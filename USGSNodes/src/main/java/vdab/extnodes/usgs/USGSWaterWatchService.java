package vdab.extnodes.usgs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import com.lcrc.af.AnalysisCompoundData;
import com.lcrc.af.AnalysisData;
import com.lcrc.af.AnalysisDataDef;
import com.lcrc.af.AnalysisEvent;
import com.lcrc.af.constants.GeoUnits;
import com.lcrc.af.constants.SpecialText;
import com.lcrc.af.util.ControlDataBuffer;
import com.lcrc.af.util.IconUtility;
import com.lcrc.af.util.StringUtility;

import vdab.api.node.HTTPService_A;

import vdab.core.dataencode.JsonUtility;


public class USGSWaterWatchService  extends HTTPService_A{
	// https://waterwatch.usgs.gov/webservices/realtime?region=oh&format=json
	private static String API_ENDPOINT= "https://waterwatch.usgs.gov/webservices/realtime";
	private String c_HUCodeMatch;
	private String c_RegionCode;
	private Integer c_DataNamingApproach;
	DateFormat c_WWDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");	
	String c_WWLastTZ = "";
	private ControlDataBuffer c_cdb_AvailableOriginalFields = new ControlDataBuffer("AvailableOriginalFields",new String[]{"tz_cd","dec_lat_va","station_nm","stage_dt","flow_unit","class","dec_long_va","url","site_no","percentile","stage","percent_median","flow_dt","stage_unit","huc_cd","percent_mean","flow"});
	private ControlDataBuffer c_cdb_AvailableEnhancedFields= new ControlDataBuffer("AvailableEnhancedFields");
	private ControlDataBuffer c_cdb_AvailableFields = c_cdb_AvailableOriginalFields;
	private ControlDataBuffer c_cdb_SelectedFields = new ControlDataBuffer("USGSSelectedFields",new String[]{"stage","flow","percentile"});
	private static String SITES_START= "{\"sites\":[";
	private static String SITENO_START= "{\"site_no\":";
	private static String HUC_CODE_START= ",\"huc_cd\":\"";
	

	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_usgs");
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
	public Integer get_DataNamingApproach() { 
		return c_DataNamingApproach;	
	}
	public void set_DataNamingApproach(Integer type){
		c_DataNamingApproach = type;
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
	
	public String buildCompleteURL(AnalysisEvent ev) {
		if (c_RegionCode == null)
			return null;	
		StringBuilder sb = new StringBuilder();
		sb.append(API_ENDPOINT);
		sb.append("?region=").append(c_RegionCode);
		sb.append("&format=json");
		return sb.toString();
	}
	public void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuilder sb = new StringBuilder();
		try {
			while ((line = in.readLine()) != null)
				sb.append(line);	

			String json = sb.toString();
			int i = json.indexOf(SITES_START);
			json = json.substring(SITES_START.length()+i);
			AnalysisEvent[] evs = convertSelectedToEvents(json);
			serviceResponse(inEvent, evs);
		}
		catch (Exception e){
			serviceFailed(inEvent, 3);
		}

	}
	private HashMap<String,String> c_LastRecord_map = new HashMap<String, String>();
	private String getDataPath(String siteno){
		StringBuilder sb = new StringBuilder();
		sb.append(c_RegionCode.toUpperCase());
		sb.append(siteno);
		return sb.toString();
	}
	private boolean hasChanged(String record){
		String site = StringUtility.digitsOnly(record.substring(0,16));
		String last = c_LastRecord_map.get(site);
		if (last == null || !last.equals(record)){
			c_LastRecord_map.put(site, record);
			return true;
		}
		return false;
	}
	private AnalysisEvent convertOneEvent(String huc, String json) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(SITENO_START);
		sb.append(json);
		sb.append("}");
		try{
		String fixedJson = sb.toString();
		fixedJson = fixedJson.replace("class", "XXXclassXXX"); // Replace "class" with token, JSON java library doesn't like it
		fixedJson = fixedJson.replace("null", "\"\"");

	
			AnalysisData ad = JsonUtility.convertJsonToAnalysisData("SiteUSGS", fixedJson);
			String siteno = ad.getElementByLabel("site_no").getDataAsString();	
			String flowTimeStr = ad.getElementByLabel("flow_dt").getDataAsString();
			String stageTimeStr = ad.getElementByLabel("stage_dt").getDataAsString();
			String timeZone = ad.getElementByLabel("tz_cd").getDataAsString();
		
			Double latitude = ad.getElementByLabel("dec_lat_va").getDataAsDouble();
			Double longitude = ad.getElementByLabel("dec_long_va").getDataAsDouble();
	
			boolean foundStageUnit = (ad.getElementByLabel("stage_unit").getDataAsString().length() > 0	);
			boolean foundFlowUnit = (ad.getElementByLabel("flow_unit").getDataAsString().length() > 0);	

			AnalysisCompoundData acd = new AnalysisCompoundData("USGSData");
			AnalysisData[] ads = ad.getAllSimpleSubData();
			
			for (AnalysisData adSub: ads){
				if (adSub.getLabel().equals("XXXclassXXX")) // replace token "class"
					adSub.setLabel("class");
				String label = adSub.getLabel();		
				if (c_cdb_SelectedFields.isSet(label)){
					if (label.equals("station_nm")){ // remove commas in station name.
						adSub.setData(adSub.getDataAsString().replace(","," "));
					}
					/*
					if (label.equals("flow") && !foundFlowUnit) // Don't get flow if not defined
						continue;
					if (label.equals("stage") && !foundStageUnit)  // Don't get stage if not defined
						continue;
					*/
					acd.addAnalysisData(adSub);
				}
			}

			// Get Date for event - set timzone if needed.
			if (!c_WWLastTZ.equals(timeZone)){
				c_WWDateFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
				c_WWLastTZ = timeZone;
			}
			
			// Use the latest of the available times
			long ts = 0L;
			long flowTime = c_WWDateFormat.parse(flowTimeStr).getTime();
			long stageTime = c_WWDateFormat.parse(stageTimeStr).getTime();
			if (flowTime == stageTime){
				ts = flowTime;
			}
			else {
				ts = (flowTime > stageTime)? flowTime: stageTime;
//				setWarning(">>>> THE times are not the same STAGE="+stageTimeStr+" FLOW="+flowTimeStr+" SITE="+siteno);
			}

			AnalysisEvent ev = new AnalysisEvent(ts,getDataPath(siteno),acd);
			ev.setLocation(GeoUnits.DEGREES_NE_METERS, latitude, longitude, null);
			return ev;
		}
		catch(Exception e){
			setWarning("Unable to convert record with HUC="+huc+" e>"+e);
		}
		return null;
	}
	private AnalysisEvent[] convertSelectedToEvents(String json){
		ArrayList<AnalysisEvent> l = new ArrayList<AnalysisEvent>();	
		ArrayList<String> locJsonList = StringUtility.locateAllBetween(json, SITENO_START ,"}");

		int failedCount = 0;
		for(String locJson: locJsonList){
			if (!hasChanged(locJson))
				continue;
			String huc = StringUtility.locateBetween(locJson, HUC_CODE_START ,"\",");
			if (huc == null){
				if (isTraceLogging())
					logTrace("HU Code not found for WaterWatch RECORD="+locJson);
				failedCount++;	
				continue;
			}
			if ( c_HUCodeMatch == null || c_HUCodeMatch.length() == 0  || huc.startsWith(c_HUCodeMatch)) {
				AnalysisEvent ev = convertOneEvent(huc, locJson);
				if (ev != null)
					l.add(ev);
				else
					failedCount++;
			}
		}
		if (failedCount > 0){
			setWarning("Some records were not parsable NOFAILED="+failedCount+" NORECORDS="+locJsonList.size());
		}
		else {
			if (isTraceLogging())
				logTrace("Received records from USGS NORECORDS="+locJsonList.size());	
		}
		return l.toArray(new AnalysisEvent[l.size()]);
	}
}
