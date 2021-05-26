package vdab.extnodes.hyfi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import com.lcrc.af.AnalysisCompoundData;
import com.lcrc.af.AnalysisData;
import com.lcrc.af.AnalysisDataDef;
import com.lcrc.af.AnalysisEvent;
import com.lcrc.af.constants.EventRetrieveType;
import com.lcrc.af.constants.GeoUnits;
import com.lcrc.af.constants.MeasurementUnit;
import com.lcrc.af.constants.SpecialText;
import com.lcrc.af.util.ControlDataBuffer;
import com.lcrc.af.util.IconUtility;
import vdab.api.node.HTTPService_A;

import vdab.core.nodes.http.HTTPRequestRunner;
import vdab.core.nodes.http.HTTPResponseHandler_I;
import vdab.core.nodes.http.ServiceHandler_HTTP;
import vdab.core.nodes.units.UnitAdder;


public class HyfiService  extends HTTPService_A{
	// https://waterwatch.usgs.gov/webservices/realtime?region=oh&format=json
	private final static String API_BASE_ENDPOINT= "https://api.hyfi.io";
	private static DateFormat SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // ISO 8601 time
	private static DateFormat START_SDF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); // ISO 8601 time
	private final static long START_OFFSET = 7200000L;
	private final static String ALLSITES = "*ALL*";
	private LinkedList<String> c_ActiveSiteQueue= new LinkedList<String>();

	private static UnitAdder s_UnitAdder = new UnitAdder()
	.addUnit("depth",MeasurementUnit.DISTANCE_FEET)
	.addUnit("stage",MeasurementUnit.DISTANCE_FEET)
	;
	private String c_DataLabel = "HyfiData";
	private String c_APIKey;
	private String c_SiteCode ;

	private Integer c_RetrieveType = Integer.valueOf(EventRetrieveType.SERIES); // Not exactly correct.
	
	private ControlDataBuffer c_cdb_AvailableFields = new ControlDataBuffer("HyfiAvailableFields",new String[]{"depth","stage"});
	private ControlDataBuffer c_cdb_SelectedFields = new ControlDataBuffer("HyfiSelectedFields",new String[]{"depth","stage"});
	private ConcurrentHashMap<String,Hyfi_Site> c_HyfiSites ;
	private static ConcurrentHashMap<String, ConcurrentHashMap<String,Hyfi_Site>> s_map_HyfiSiteGroupsByKey = new ConcurrentHashMap<String, ConcurrentHashMap<String,Hyfi_Site>>();

	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_hyfi");
	}
	public String get_APIKey() { // If NULL return all sites.
		return c_APIKey;
	}
	public void set_APIKey(String key){
		c_APIKey = key;
		c_HyfiSites  = s_map_HyfiSiteGroupsByKey.get(c_APIKey); 
	}

	public AnalysisDataDef def_APIKey(AnalysisDataDef theDataDef){
		// If there are existing keys, put them in the picklist
		if (s_map_HyfiSiteGroupsByKey.size() > 0){
			ArrayList<String> l = new ArrayList<String>();
			for (String site: s_map_HyfiSiteGroupsByKey.keySet())
				l.add(site);
			theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		}
		return theDataDef;
	}
	public Integer get_RetrieveType(){
		return c_RetrieveType;
	}
	public void set_RetrieveType(Integer type){
		c_RetrieveType = type;
	}
	public String get_DataLabel() { 
		return c_DataLabel;
	}
	public void set_DataLabel(String label){
		c_DataLabel = label;
	}
	public String get_SelectedFields() {
		if(c_cdb_SelectedFields.isEmpty())
			return null;
		return c_cdb_SelectedFields.getAllSet(","); 
	}	
	public void set_SelectedFields(String fields){
		// Multitple attributes, probably read from xml
		if (fields.contains(",")){
			c_cdb_SelectedFields.setAll(fields,","); 			
		} 
		// Clear command from option picker
		else if (fields.equals(SpecialText.CLEAR)){
			c_cdb_SelectedFields.clear();
			return;
		}
		else {
			// One value to add.
			c_cdb_SelectedFields.set(fields);
		}
	}
	public AnalysisDataDef def_SelectedFields(AnalysisDataDef theDataDef){
		ArrayList<String> l = new ArrayList<String>();
		if (!c_cdb_SelectedFields.isEmpty())
			l.add(SpecialText.CLEAR);
		for (String label: c_cdb_AvailableFields.getAllSet()){
			if  (!c_cdb_SelectedFields.isSet(label))
				l.add(label);
		}
		theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		return theDataDef;
	}
	public String get_SiteCode() { // If NULL return all sites.
		return c_SiteCode;
	}
	public void set_SiteCode(String site){
		c_SiteCode = site;
	}
	private String c_NextSiteCode;
	public String getNextSiteCode(){	
		return c_NextSiteCode;
	}
	public AnalysisDataDef def_SiteCode(AnalysisDataDef theDataDef){
		ArrayList<String> l = new ArrayList<String>();
		l.add(ALLSITES);
		for (String site: c_HyfiSites.keySet())
			 l.add(site);
		 
		theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		return theDataDef;
	}
	// DEBUG Attributes
	public Integer get_NoAPIKeys(){
		return Integer.valueOf(s_map_HyfiSiteGroupsByKey.size()); 		
	}
	public Integer get_NoSites(){
		if (c_HyfiSites == null)
			return null;
		return Integer.valueOf(c_HyfiSites.size()); 		
	}
	public void _start(){
		SDF.setTimeZone(TimeZone.getTimeZone("UTC"));
		c_HyfiSites  = s_map_HyfiSiteGroupsByKey.get(c_APIKey); 
		
		// If restart
		if (c_HyfiSites != null){
			for (Hyfi_Site site: c_HyfiSites.values())
				site.setLatestReport(0L);
		}
		if (c_HyfiSites == null) { // Fetch sites if first time for this APIKey.
			c_HyfiSites = new ConcurrentHashMap<String, Hyfi_Site>();
			s_map_HyfiSiteGroupsByKey.put(c_APIKey, c_HyfiSites);
			Runner_GetSites runner = new Runner_GetSites();
			new HTTPRequestRunner(this, 1, runner.buildUrl(), runner );
		}
		super._start();
	}
	public synchronized void processEvent(AnalysisEvent ev){
		if (c_HyfiSites.size() <= 0){
			return;
		}
		if ( c_ActiveSiteQueue.size() > 0){
			setError("Processing new event before CLEARING, would have failed to process entire last event.");
			c_ActiveSiteQueue.clear();
		}
		if (c_SiteCode.equals(ALLSITES))
			c_ActiveSiteQueue.addAll(c_HyfiSites.keySet());
		else
			c_ActiveSiteQueue.add(c_SiteCode);

		processNextSite();
	}
	private synchronized void processNextSite() {
		String nextSite = c_ActiveSiteQueue.pop();
		if (nextSite == null){
			setError("Could not find next event");
			return;
		}
		new ServiceHandler_HTTP(this, new AnalysisEvent(this, new AnalysisData("NEXTSITE", nextSite)));
	}

	public String buildCompleteURL(AnalysisEvent ev) {
		long ts = System.currentTimeMillis();
		String siteCode = getSiteCodeFromEvent(ev);
		if (siteCode == null)
			return null;
			
		String start = START_SDF.format(new Date(ts));

		StringBuilder sb = new StringBuilder();
		sb.append(API_BASE_ENDPOINT);
		String fields = c_cdb_SelectedFields.getAllSet(",");
		sb.append("/getData?q=").append(fields.substring(0,fields.length()-1));
		sb.append("&site_code=").append(siteCode);
		sb.append("&start=").append(start);
		String url = sb.toString();
		return url;
	}
public void processReturnStreamForErrors(AnalysisEvent inEvent, int retCode, String msg, InputStream is){
		
		if (retCode == 400){ // Bad site data - continue with others.
			String siteNo = getSiteCodeFromEvent(inEvent);
			if (siteNo != null)
				setWarning("HTTP 400 Return Code: Site could not be processed SITECODE="+siteNo);
			if (!c_ActiveSiteQueue.isEmpty()) {
				processNextSite();
			}
		}
		else { // Other codes just use standard handling.
			super.processReturnStreamForErrors(inEvent, retCode, msg, is);
		}

	}
	// TRYIT - Try this to prevent crosstalk on Latitude and Longitude
	public synchronized void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		AnalysisData adSite = inEvent.getAnalysisData();
		if (!adSite.getLabel().equals("NEXTSITE")){
			serviceFailed(inEvent, 5);
			return;
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
		ArrayList<AnalysisEvent> l = new ArrayList<AnalysisEvent>();
		try {
			String[] headerFields = null;
			while ((line = in.readLine()) != null){		
				if (headerFields == null) {
					if (line.startsWith("_time")){
						headerFields = line.split(",");
					}
				}
				else {
					AnalysisEvent ev = getSiteDataEvent(headerFields, line);
					if (ev != null)
						l.add(ev);
				}
	// DEBUG			logInfo("HyfiService.processReturnStream() RECEIVED="+line);
			}
			if (l.size() > 0)
				serviceResponse(inEvent, l.toArray(new AnalysisEvent[l.size()]));	
		}
		catch (Exception e){
			serviceFailed(inEvent, 3);
		}
		if (!c_ActiveSiteQueue.isEmpty()) {
			processNextSite();
		}
	}
	private String getSiteCodeFromEvent(AnalysisEvent ev){
		AnalysisData adSite = ev.getAnalysisData();
		if (!adSite.getLabel().equals("NEXTSITE"))
			return null;
		return adSite.getDataAsString();
	}

	private AnalysisEvent getSiteDataEvent(String[] headerFields, String line){

		String[] fields = line.split(",");
		Hyfi_Site site = c_HyfiSites.get(fields[1]);

		if (site == null){		
			setWarning("Unable to find the site SITE="+fields[1]);
			return null;
		}
		AnalysisCompoundData acd = new AnalysisCompoundData(c_DataLabel);

		for (int n=2; n< headerFields.length; n++){
			Double val = new Double(fields[n]);
			AnalysisData ad = new AnalysisData(headerFields[n],val);
			s_UnitAdder.addUnitForData(ad.getLabel(), ad); // Add unit info if available
			acd.addAnalysisData(ad);		
		}
		long ts = System.currentTimeMillis();
		Date date;
		try {
			date = SDF.parse(fields[0]);
			ts = date.getTime();
		} 		
		catch (ParseException e) {
			setError("Unable to parse data time. DATE="+fields[0]);
		}
		// Drop already reported data.
		if (!site.isNewerData(ts)){
			if (isTraceLogging())
				logTrace("Dropping already reported data for SITE="+site);
			return null;
		}		
//		logInfo("HyfiService.GetSites.processRunnerResponse() RECEIVED="+line);
		site.setLatestReport(ts);
		AnalysisEvent ev = new AnalysisEvent(ts, this,acd);	
		ev.setPath(site.getPath());
		ev.setLocation(GeoUnits.DEGREES_NE_METERS, site.getLatitude(), site.getLongitude(), site.getAltitude());
		return ev;
	}
		

	public void setupHTTPConnection(HttpURLConnection con)  {
		try {
			con.setRequestMethod("GET");
			con.setRequestProperty("X-API-KEY", c_APIKey);
		}
		catch (Exception e) {} // Will never happen
		
	}
	// SITE DATA CLASS
	private class Hyfi_Site {
		private String c_StationName;

		private String c_SiteType;
		private Double c_Latitude;
		private Double c_Longitude;
		private Double c_Altitude;
		private String c_SiteCode;
		private long c_LatestReport;
	
		public Hyfi_Site(String apiKey, String[] fields){
			c_SiteCode = fields[0];
			c_StationName = fields[1];

			c_Latitude = Double.valueOf(fields[5]);
			c_Longitude = Double.valueOf(fields[6]);
			try {
				c_Altitude = Double.valueOf(fields[9]);
			}
			catch (Exception e){}
			ConcurrentHashMap<String,Hyfi_Site> hyfiSites = s_map_HyfiSiteGroupsByKey.get(apiKey);
			hyfiSites.put(c_SiteCode, this);				
		}
		public Double getLatitude(){
			return c_Latitude;
		}
		public Double getLongitude(){
			return c_Longitude;
		}
		public Double getAltitude(){
			return c_Altitude;
		}
		public String getStationName(){
			return c_StationName.replace(","," ").replace("\"", ""); // Remove , to make JSON friendly
		}
		public String getPath(){
			return getStationName().replace(" ","").replace("\t","");
		}
		public String getSiteType(){
			return c_SiteType;
		}
		public boolean isNewerData(long ts){
			return ts > c_LatestReport;
		}
		public void setLatestReport(long ts){
			c_LatestReport = ts;
		}
		public String toString(){
			return c_SiteCode;
		}
	}
	// == SETUP QUERY CLASSES ==============================
	private class Runner_GetSites implements HTTPResponseHandler_I {

		public String buildUrl(){
			StringBuilder sb = new StringBuilder();
			sb.append(API_BASE_ENDPOINT);	
			sb.append("/getSites");
			String url = sb.toString();
			return url;
		}

		@Override
		public void processRunnerResponse(int reqCode, String retMsg, InputStream inS) {
			BufferedReader in = new BufferedReader(new InputStreamReader(inS));
			String line = null;
			try {
				while ((line = in.readLine()) != null){
					if (line.startsWith("site"))
						continue;
					String fields[] = line.split(",");
					if (isTraceLogging())
						logTrace("HyfiService.GetSites.processRunnerResponse() RECEIVED="+line+" NOFIELDS="+fields.length);
					if (fields.length == 12)
						new Hyfi_Site(c_APIKey, fields);			
				}
			}
			catch (IOException e) {
				setError("Hyfi.GetSites.processRunnerResponse() Failed to receive response e>"+e);
			}
		}
		@Override
		public void setHTTPRequestRunnerConnection(int reqCode, HttpURLConnection con)  {
			try {
				con.setRequestMethod("GET");
				con.setRequestProperty("X-API-KEY", c_APIKey);
			} catch (ProtocolException e) {}
		
		} 	
	}
}
