package vdab.extnodes.usgs;

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
import java.util.HashMap;
import java.util.TimeZone;

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
import com.lcrc.af.util.StringUtility;

import vdab.api.node.HTTPService_A;

import vdab.core.nodes.http.HTTPRequestRunner;
import vdab.core.nodes.http.HTTPResponseHandler_I;
import vdab.core.nodes.units.UnitAdder;

public class USGSWaterService  extends HTTPService_A{
	// https://waterwatch.usgs.gov/webservices/realtime?region=oh&format=json
	private static String API_ENDPOINT= "https://waterservices.usgs.gov/nwis/iv/?format=rdb&siteStatus=active";
	private String c_HUCodeMatch;
	private String c_RegionCode;
	private String c_DataLabel = "USGSData";
	private Integer c_RetrieveType = EventRetrieveType.LATESTONLY;

	/*
		00010     Temperature, water, degrees Celsius
		00011     Temperature, water, degrees Fahrenheit
		00020     Temperature, air, degrees Celsius
		00035     Wind speed, miles per hour
		00036     Wind direction, degrees clockwise from true nor
		00045     Precipitation, total, inches
		00060     Discharge, cubic feet per second
		00065     Gage height, feet 
		00095     Specific conductance, water, unfiltered, microsiemens per centimeter at 25 degrees Celsius
		00201     Incident light, daily total, 400-700 nanometers, microeinsteins per square meter
		00300     Dissolved oxygen, water, unfiltered, milligrams per liter
		00400     pH, water, unfiltered, field, standard units
		32315     Chlorophyll relative fluorescence (fChl), water, in situ, relative fluorescence units (RFU)
  		32316     Chlorophyll fluorescence (fChl), water, in situ, concentration estimated from reference material, micrograms per liter as chlorophyll
   		32319     Phycocyanin fluorescence (fPC), water, in situ, concentration estimated from reference material, micrograms per liter as phycocyanin
   		32321     Phycocyanin relative fluorescence (fPC), water, in situ, relative fluorescence units (RFU)
   		62609     Net solar radiation, watts per square meter
		62614     Lake or reservoir water surface elevation above NGVD 1929, feet
		62615     Lake or reservoir water surface elevation above NAVD 1988, feet
		63680     Turbidity, water, unfiltered, monochrome near infra-red LED light, 780-900 nm, detection angle 90 +-2.5 degrees, formazin nephelometric units (FNU)
		70969     DCP battery voltage, volts
		72019     Depth to water level, feet below land surface
 		72254     Water velocity reading from field sensor, feet per second
 		72255     Mean water velocity for discharge computation, feet per second
   		99133     Nitrate plus nitrite, water, in situ, milligrams per liter as nitrogen
   		99234     Count of samples collected by autosampler, number
	 */

	private ControlDataBuffer c_cdb_AvailableOriginalFields = new ControlDataBuffer("AvailableOriginalFields",new String[]{"00010","00011","00045","00060","00065","00095","00300","00400","62609","62614","62615","63680","70969","72019","72255","70969"});
	private ControlDataBuffer c_cdb_AvailableEnhancedFields = new ControlDataBuffer("AvailableAvailableEnhancedFieldsFields",new String[]{"site_no","station_nm","url","huc_cd","site_tp_cd"});
	private ControlDataBuffer c_cdb_AvailableFields = c_cdb_AvailableOriginalFields;
	private ControlDataBuffer c_cdb_SelectedFields = new ControlDataBuffer("USGSSelectedFields",new String[]{"00060","00065","00010"});
	private ControlDataBuffer c_cdb_EnhancedFields = new ControlDataBuffer("USGSEnhancedFields",new String[]{"site_no","station_nm","url","huc_cd","site_tp_cd"});
	private static ArrayList<StringBuilder> c_SiteRawDataList = new ArrayList<StringBuilder>();

	private static String SITES_START= "# Data for the following";
	private static String SITES_END= "# -----------------------";
	private static String SITEDATAKEY_START= "# Data provided for site ";

	private static UnitAdder s_UnitAdder = new UnitAdder()
	.addUnit("00010",MeasurementUnit.TEMP_DEG_CENTIGRADE)
	.addUnit("00011",MeasurementUnit.TEMP_DEG_FAHRENHEIT)
	.addUnit("00065",MeasurementUnit.DISTANCE_FEET)
	.addUnit("00060",MeasurementUnit.FLOW_FEET3_SEC)

	;
	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_usgs");
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
	public String get_HUCodeMatch() { 
		return c_HUCodeMatch;
	}
	public void set_HUCodeMatch(String code){
		c_HUCodeMatch = code;
	}
	public String get_RegionCode() { 
		return c_RegionCode;	
	}
	public void set_RegionCode(String code){
		c_RegionCode = code;
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
	public String get_EnhancedFields() {
		if(c_cdb_EnhancedFields.isEmpty())
			return null;
		return c_cdb_EnhancedFields.getAllSet(","); 
	}	
	public void set_EnhancedFields(String fields){

		// Multitple attributes, probably read from xml
		if (fields.contains(",")){
			c_cdb_EnhancedFields.setAll(fields,","); 			
		} 
		// Clear command from option picker
		else if (fields.equals(SpecialText.CLEAR)){
			c_cdb_EnhancedFields.clear();
			return;
		}
		else {
			// One value to add.
			c_cdb_EnhancedFields.set(fields);
		}
	}
	public AnalysisDataDef def_EnhancedFields(AnalysisDataDef theDataDef){
		ArrayList<String> l = new ArrayList<String>();
		if (!c_cdb_EnhancedFields.isEmpty())
			l.add(SpecialText.CLEAR);
		for (String label: c_cdb_AvailableEnhancedFields.getAllSet()){
			if  (!c_cdb_EnhancedFields.isSet(label))
				l.add(label);
		}
		theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		return theDataDef;
	}
	public void _start(){
		s_map_USGSSites.clear(); // Clear old sites.
		Runner_GetSites runner = new Runner_GetSites();
		new HTTPRequestRunner(this, 1, runner.buildUrl(), runner );
		super._start();
	}
	
	public String buildCompleteURL(AnalysisEvent ev) {
		if (c_RegionCode == null)
			return null;	
		StringBuilder sb = new StringBuilder();
		sb.append(API_ENDPOINT);
		sb.append("&stateCd=").append(c_RegionCode);
		String paramCodes = c_cdb_SelectedFields.getAllSet(",");
		sb.append("&parameterCd=").append(paramCodes.substring(0,paramCodes.length()-1));
		return sb.toString();
	}
	// TRYIT - Try this to prevent crosstalk on Latitude and Longitude
	public synchronized void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		int stationCount = 0;
		String line;
		try {

			// READ SITE LIST
			StringBuilder sbSites= new StringBuilder();
			boolean in_Sites = false;
			while ((line = in.readLine()) != null){
				if (in_Sites && line.startsWith(SITES_END)){
					break;
				}
				if (in_Sites){
					sbSites.append("\n");
					sbSites.append(line);
				}
				if (line.startsWith(SITES_START))
					in_Sites = true;
			}
			String siteKey = sbSites.toString();

			// SEPARATE INDIVIDUAL SITE DATA
			StringBuilder sb = null;
			while ((line = in.readLine()) != null){
				if (line.startsWith(SITEDATAKEY_START)){
					if (sb != null && sb.length() > 0)
						c_SiteRawDataList.add(sb); 	
					sb = new StringBuilder();
					sb.append(line);
					continue;
				}
				if (sb != null){
					sb.append("\n");
					sb.append(line);
				}
			}
			if (sb != null && sb.length() > 0)
				c_SiteRawDataList.add(sb);

	//		logInfo(">>>>>>>>>>>> Found SITES="+c_SiteRawDataList.size());
			ArrayList<AnalysisEvent> evList = new  ArrayList<AnalysisEvent>();

			for (StringBuilder sbNext: c_SiteRawDataList){
				AnalysisEvent ev = processSiteData(sbNext.toString());
				if (ev != null)
					evList.add(ev);
			}
			c_SiteRawDataList.clear();
			serviceResponse(inEvent, evList.toArray(new AnalysisEvent[evList.size()]));
		}
		catch (Exception e){
			serviceFailed(inEvent, 3);
		}
	}
	private AnalysisEvent processSiteData(String siteRawData){
		USGS_SiteData siteData = new USGS_SiteData(siteRawData);
		USGS_Site site =  s_map_USGSSites.get(siteData.getSiteNo());
		// Skip sites not matching HUCode
		if (c_HUCodeMatch != null && !site.getHUCode().startsWith(c_HUCodeMatch))
			return null;
	//	logInfo(">>>>>>>>> SITE NO="+site.getSiteNo()+" NOLINES="+site.getNoLines()+" NOCOLS="+site.getNoColumns());
		AnalysisEvent ev = siteData.getSiteDataEvent();
		return ev ;
	}

	// == DATA HOLDING CLASSES
	private class USGS_SiteData {
		private String c_SiteNo;
		private String c_SiteRawData;
		private String[] c_SiteLines;
		private String[] c_DataLines;
		private String[] c_HeaderFields;
		DateFormat c_DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");	
		
		public USGS_SiteData(String siteRawData){
			c_SiteRawData = siteRawData;
			c_SiteLines = c_SiteRawData.split("\n");
			String[] site_fields = c_SiteLines[0].split("\\s+");
			c_SiteNo = site_fields[site_fields.length -1];
			int n = 0;
			for (; n < c_SiteLines.length; n++){
				if (c_SiteLines[n].startsWith("agency_cd"))	{
					c_HeaderFields = c_SiteLines[n].split("\t",-1);
					n++;
					break;
				}
			}
			n++; // skip 1 more line;
			ArrayList<String> l = new ArrayList<String>();
			for (; n < c_SiteLines.length; n++){
				String[] dataFields = c_SiteLines[n].split("\t",-1);
				if (dataFields.length == c_HeaderFields.length)
					l.add(c_SiteLines[n]);
			}
			c_DataLines = l.toArray(new String[l.size()]);	
		}
		public String getSiteNo(){
			return c_SiteNo;
		}
		public Long getLatestTimestamp(){
			long maxTime = Long.MIN_VALUE;
			for (String dataLine: c_DataLines){
				String[] dataFields = dataLine.split("\t",-1);
				try {
					c_DateFormat.setTimeZone(TimeZone.getTimeZone(dataFields[3]));
					long metricTime= c_DateFormat.parse(dataFields[2]).getTime();
					if (metricTime > maxTime)
						maxTime = metricTime;
				} catch (ParseException e) {
					setWarning("Unable to parse data DATE="+dataFields[2]+" e>"+e);
				}

			}
			if (maxTime > Long.MIN_VALUE)
				return Long.valueOf(maxTime);
			else
				return null;
		}
		public Double getMetricByCode(String code) {
			int atPos = -1;
			for (int n= 4 ; n < c_HeaderFields.length ; n++){
				if (c_HeaderFields[n].endsWith("_"+code)){
					atPos = n;
					break;
				}
			}
			if (atPos == -1)
				return null;
			
			for (String dataLine: c_DataLines){
				String[] dataFields = dataLine.split("\t",-1);
				if (dataFields[atPos] != null  && dataFields[atPos].length() > 0){
					try {
						String data = dataFields[atPos];
						return Double.valueOf(data);
					}
					catch (Exception e){}
				}
			}
			return null;
		}
		public int getNoLines(){
			if (c_DataLines == null)
				return 0;	
			return c_DataLines.length;
		}
		public int getNoColumns(){
			if (c_HeaderFields == null)
				return 0;
			else
				return c_HeaderFields.length;
		}
		public String getDataPath(){
			StringBuilder sb = new StringBuilder();
			sb.append(c_RegionCode.toUpperCase());
			sb.append(getSiteNo());
			return sb.toString();
		}
		public AnalysisEvent getSiteDataEvent(){
			
			USGS_Site site =  s_map_USGSSites.get(getSiteNo());
			if (site == null){
				setError("Unable to find site information SITENO="+getSiteNo());
				return null;
			}
			
			long ts = System.currentTimeMillis();
			Long latestTS = getLatestTimestamp(); // Try to get it from the data.
			if (latestTS != null)
				ts = latestTS.longValue();

			if (!site.isNewerData(ts)){
				if (isTraceLogging())
					logTrace("Dropping already reported data for SITENO="+site);
				return null;
			}

			AnalysisCompoundData acd = new AnalysisCompoundData(c_DataLabel);		
			//Add enhancement fields.
			if (c_cdb_EnhancedFields.isSet("site_no"))
				acd.addAnalysisData(new AnalysisData("site_no", site.getSiteNo()));
				
			if (c_cdb_EnhancedFields.isSet("station_nm"))
				acd.addAnalysisData(new AnalysisData("station_nm", site.getStationName()));
			
			if (c_cdb_EnhancedFields.isSet("huc_cd"))
				acd.addAnalysisData(new AnalysisData("huc_cd", site.getHUCode()));
			
			if (c_cdb_EnhancedFields.isSet("site_tp_cd"))
				acd.addAnalysisData(new AnalysisData("site_tp_cd", site.getSiteType()));
			
			if (c_cdb_EnhancedFields.isSet("url"))
				acd.addAnalysisData(new AnalysisData("url", "https://waterdata.usgs.gov/monitoring-location/"+site.getSiteNo()));
	
			// Add all the selected fields
			for (String code:  USGSWaterService.this.c_cdb_SelectedFields.getAllSet()){
				Double val = getMetricByCode(code);
				if (val != null){
					int codeNo = -1;
					String label = code;
					try {
						codeNo = Integer.parseInt(code);
						String labelForCode = USGSMetricLabel.getEnum().getLabel(codeNo);
						if (labelForCode != null)
							label = labelForCode;
					}
					catch  (Exception e){}		
					AnalysisData ad = new AnalysisData(label, val);
					s_UnitAdder.addUnitForData(code, ad); // Add unit info if available
					acd.addAnalysisData(ad);
				}
			}
			AnalysisEvent ev = new AnalysisEvent(ts,getDataPath(),acd);			
			ev.setLocation(GeoUnits.DEGREES_NE_METERS, site.getLatitude(), site.getLongitude(), site.getAltitude());
			site.setLatestReport(ts);
			return ev;
		}
	}

	private static HashMap<String, USGS_Site> s_map_USGSSites = new HashMap<String, USGS_Site>();
	private class USGS_Site {
		private String c_StationName;
		private String c_SiteNo;
		private String c_SiteType;
		private String c_HUCode;
		private Double c_Latitude;
		private Double c_Longitude;
		private Double c_Altitude;
		private long c_LatestReport;
	
		public USGS_Site(String[] fields){
			c_StationName = fields[2];
			c_SiteNo = fields[1];
			c_SiteType = fields[3];
			c_HUCode  = fields[11];
			c_Latitude = Double.valueOf(fields[4]);
			c_Longitude = Double.valueOf(fields[5]);
			try {
				c_Altitude = Double.valueOf(fields[8]);
			}
			catch (Exception e){}
			s_map_USGSSites.put(c_SiteNo, this);				
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
			return c_StationName.replace(","," "); // Remove , to make JSON friendly
		}
		public String getSiteType(){
			return c_SiteType;
		}
		public String getSiteNo(){
			return c_SiteNo;
		}
		public String getHUCode(){
			return c_HUCode;
		}
		public boolean isNewerData(long ts){
			return ts > c_LatestReport;
		}
		public void setLatestReport(long ts){
			c_LatestReport = ts;
		}
		public String toString(){
			return c_SiteNo;
		}
	}
	// == SETUP QUERY CLASSES ==============================
	private class Runner_GetSites implements HTTPResponseHandler_I {
		public String buildUrl(){
			StringBuilder sb = new StringBuilder();
			sb.append("https://waterservices.usgs.gov/nwis/site/?format=rdb&siteStatus=active");	
			sb.append("&stateCd=").append(c_RegionCode);
			String url = sb.toString();
			return url;
		}

		@Override
		public void processRunnerResponse(int reqCode, String retMsg, InputStream inS) {
			BufferedReader in = new BufferedReader(new InputStreamReader(inS));
			StringBuilder sbSites= new StringBuilder();
			String line = null;
			try {
				while ((line = in.readLine()) != null){
					if (line.startsWith("#"))
						continue;
					if (line.startsWith("agency_cd")){
						line = in.readLine(); // read extra line.
						continue;
					}
					// These should be valid sites.
					String[] fields = line.split("\t", -1);
					sbSites.append("\n");
					USGS_Site site = null;
					if (fields.length == 12)
						site = new USGS_Site(fields);
					sbSites.append("FIELDS="+fields.length+" LAT="+site.getLatitude()+" TYPE="+site.getSiteType()+" ALT="+site.getAltitude());
				}
				if (isTraceLogging())
					logTrace("USGSWaterService.GetSites.processRunnerResponse() RECEIVED="+sbSites.toString());			
			}
			catch (IOException e) {
				setError("USGSWaterService.GetSites.processRunnerResponse() Failed to receive response e>"+e);
			}
		}
		@Override
		public void setHTTPRequestRunnerConnection(int reqCode, HttpURLConnection con)  {
			try {
				con.setRequestMethod("GET");
			} catch (ProtocolException e) {}
			con.setRequestProperty("Content-Type","application/json");
		} 	
	}
}


