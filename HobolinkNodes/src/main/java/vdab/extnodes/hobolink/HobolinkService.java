package vdab.extnodes.hobolink;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import com.lcrc.af.AnalysisCompoundData;
import com.lcrc.af.AnalysisData;
import com.lcrc.af.AnalysisDataDef;
import com.lcrc.af.AnalysisEvent;
import com.lcrc.af.constants.HTTPMethodType;
import com.lcrc.af.constants.SpecialText;
import com.lcrc.af.util.ControlDataBuffer;
import com.lcrc.af.util.IconUtility;
import com.lcrc.af.util.StringUtility;

import vdab.api.node.HTTPService_A;
import vdab.core.nodes.units.MeasurementUnit;

public class HobolinkService extends HTTPService_A{
	private static String API_ENDPOINT= "https://webservice.hobolink.com/restv2";
	private static long MAX_AGEINMINUTES = 120;
	private  DateFormat SDF_HOBOLINK = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");	
	private String c_TimeZone ;
	private String c_StationName ;
	private Boolean c_AddUnitsToLabel = Boolean.FALSE;
	
	private String c_DataLabel = "HobolinkData" ;
	private String c_Token;
	private Boolean c_DropRepeatReports = Boolean.FALSE;

	private String c_LoggerID;
	private String[] c_LabelsByChannel;
	private Long  c_LastEventTime;
	private ControlDataBuffer c_cdb_DataLabels = new ControlDataBuffer("HobolinkService_DataLabels");
	private ControlDataBuffer c_cdb_ChannelsFound = new ControlDataBuffer("HobolinkService_ChannelsFound");
	private ControlDataBuffer c_cdb_LabelsFound = new ControlDataBuffer("HobolinkService_LabelsFound");
	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_hobolink");
	}
	public String get_TimeZone(){
		return c_TimeZone;
	}

	public String get_LoggerID() {
		return c_LoggerID;

	}	
	public void set_AddUnitsToLabel(Boolean add){	
		c_AddUnitsToLabel = add;
	}
	
	public Boolean get_AddUnitsToLabel() {
		return 	c_AddUnitsToLabel;

	}	
	public void set_LoggerID(String id){	
		c_LoggerID = id;
	}
	
	private String checkSensorDigits(String id){
		String digits = StringUtility.digitsOnly(id);
		if (digits.length() > 8 || digits.length() < 6){
			setError("ParamID must be a 8 digit number ID_ENTERED="+id);
			return null;
		}
		return digits;
	}

	public String get_MetricLabels() {
		if(c_cdb_DataLabels.isEmpty())
			return null;
		return c_cdb_DataLabels.getAllSet(","); 
	}	
	public void set_MetricLabels(String labels){	

		if (labels.contains(",")){
			String[] allLabels = labels.split(",");
			c_cdb_DataLabels.setAll(labels,","); 			
		} 
		// Clear command from option picker
		else if (labels.equals(SpecialText.CLEAR)){
			c_cdb_DataLabels.clear();
			return;
		}
		else {
			// One value to add.
			if (!c_cdb_DataLabels.isSet(labels)){
				c_cdb_DataLabels.set(labels);
			}
		}
		c_LabelsByChannel = c_cdb_DataLabels.getAllSet();
	}
	public AnalysisDataDef def_MetricLabels(AnalysisDataDef theDataDef){
		ArrayList<String> l = new ArrayList<String>();
		if (!c_cdb_DataLabels.isEmpty())
			l.add(SpecialText.CLEAR);
		theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		return theDataDef;
	}
	public void set_Token(String token){
		 c_Token = token;
	}
	public String get_Token(){
		return c_Token;
	}
	public void set_StationName(String name){
		 c_StationName =name;
	}
	public String get_StationName(){
		return c_StationName;
	}
	public void set_DataLabel(String label){
		 c_DataLabel = label;
	}
	public String get_DataLabel(){
		return  c_DataLabel;
	}
	public Boolean get_DropRepeatReports(){
		return c_DropRepeatReports;
	}
	public void set_DropRepeatReports(Boolean drop){
		c_DropRepeatReports = drop;
	}
	public String get_ChannelsFound(){
		return c_cdb_ChannelsFound.getAllSet(",");
	}
	public String get_ChannelLabelsFound(){
		return c_cdb_LabelsFound.getAllSet(",");
	}
	@Override
	public void _init(){
		super._init();
		set_HTTPMethod(Integer.valueOf(HTTPMethodType.POST));
	}
	@Override
	public void _start(){
		SDF_HOBOLINK.setTimeZone(TimeZone.getTimeZone("UTC"));
		c_LabelsByChannel = c_cdb_DataLabels.getAllSet();
		super._start();
	}
	@Override
	public void _reset(){
		c_LastEventTime = null;
		super._reset();
	}
	@Override
	public String buildCompleteURL(AnalysisEvent ev) {
		StringBuilder sb = new StringBuilder();
		sb.append(API_ENDPOINT);
		sb.append("/data/json");
		return sb.toString();
	}	

	@Override
	public String getDataPath(){		
		return c_StationName;
	}
	@Override
	public void setupHTTPConnection(HttpURLConnection con)  {
		con.setRequestProperty("Content-Type", "application/json");	
	}
	@Override
	public String buildPost(AnalysisEvent ev){
		
		long endTime = System.currentTimeMillis();
		long startTime = endTime - MAX_AGEINMINUTES*60000L; 
			
		StringBuilder sb = new StringBuilder();
		sb.append("\n{");
		sb.append("\n \"action\": \"\",");
		if (get_User() != null && get_User().length() > 0){
			sb.append("\n \"authentication\": {");
			sb.append("\n   \"password\":\"" ).append(get_Password()).append("\",");
			sb.append("\n   \"token\":\"" ).append(get_Token()).append("\",");
			sb.append("\n   \"user\":\"" ).append(get_User()).append("\"");	
			sb.append("\n },");
		}
		sb.append("\n \"query\": {");
//		sb.append("\n   \"end_date_time\": \"2019-12-27 21:00:00\",");
		sb.append("\n   \"end_date_time\": \"").append(SDF_HOBOLINK.format(new Date(endTime))).append("\","); 
		sb.append("\n   \"loggers\": [").append(c_LoggerID).append("],"); 
//		sb.append("\n   \"start_date_time\": \"2019-12-27 20:00:00\"");
		sb.append("\n   \"start_date_time\": \"").append(SDF_HOBOLINK.format(new Date(startTime))).append("\""); 
		sb.append("\n    }");
		sb.append("\n }");
		return sb.toString();
	}
	public void processReturnStreamForErrors(AnalysisEvent inEvent, int retCode, String msg, InputStream is){
		
		if (retCode == 400){
			BufferedReader in = new BufferedReader(new InputStreamReader(is));
			String line;
			StringBuilder sb = new StringBuilder();
			try {
				while ((line = in.readLine()) != null)
					sb.append(line);	
				serviceResponse(inEvent, new AnalysisEvent(this, "Error" ,sb.toString()));
			}
			catch (Exception e){
				setError("Failed processing return data e>"+e);
				serviceFailed(inEvent, 3);
			}
			
		}
		else { // Other codes just use standard handling.
			super.processReturnStreamForErrors(inEvent, retCode, msg, is);
		}

	}
	public void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
	
		TreeMap<Long, AnalysisCompoundData> dataMap = new TreeMap<Long, AnalysisCompoundData>();
		StringBuilder sb = new StringBuilder();
		try {
			while ((line = in.readLine()) != null)
				sb.append(line);	
			String rawData = sb.toString();
			String tableData = StringUtility.locateBetween(rawData,"{\"observationList\":[", "],\"message\"");
			ArrayList<String> rowList =  StringUtility.locateAllBetween(tableData,"{\"logger_sn\":","\"scaled_unit\":");

			for (String row : rowList){
				String timeStr = StringUtility.locateBetween(row, "\"timestamp\":\"","\",");
				AnalysisCompoundData acd = getAcdFromDataMap(timeStr, dataMap);
				if (acd == null)
					break;
				String chanStr = StringUtility.locateBetween(row, "\"channel_num\":",",\"");	
				c_cdb_ChannelsFound.set(chanStr);
				String valueStr = StringUtility.locateBetween(row, "\"si_value\":",",\"");
				String unitStr = StringUtility.locateBetween(row, "\"si_unit\":\"","\",");	
				String label = getChannelLabel(chanStr, unitStr);
				c_cdb_LabelsFound.set(label);
				// Drop labels that start with a "-"
				if (label.startsWith("-"))
					continue;
	
				AnalysisData adMetric = new AnalysisData(label, Double.valueOf(valueStr));
				setUnitsOnData(adMetric, unitStr);
				acd.addAnalysisData(adMetric);
			}
			
			ArrayList<AnalysisEvent> l = new ArrayList<AnalysisEvent>();
			for (Map.Entry<Long, AnalysisCompoundData> entry: dataMap.entrySet()){
				long ts = entry.getKey().longValue();
				if (c_LastEventTime == null || ts > c_LastEventTime.longValue()){
					l.add(new AnalysisEvent(ts, this, entry.getValue()));
					c_LastEventTime = Long.valueOf(ts);
				}
			}
			if (l.size() > 1)
				serviceResponse(inEvent, l.toArray(new AnalysisEvent[l.size()]));
			// just return if no new data.

		}
				
		catch (Exception e){
			setError("Failed processing return data e>"+e);
			serviceFailed(inEvent, 3);
		}

	}
	private  AnalysisCompoundData getAcdFromDataMap (String timeStr, TreeMap<Long, AnalysisCompoundData> dataMap){
		Long time;
		try {
			time = Long.valueOf(SDF_HOBOLINK.parse(timeStr).getTime());
		} 
		catch (Exception e) {
			setWarning("Unable to parse date="+timeStr);
			return null;
		}
		AnalysisCompoundData acd = dataMap.get(time);
		if (acd == null) {
			acd = new AnalysisCompoundData(c_DataLabel);
			dataMap.put(time, acd);
		}
		return acd;
	}
	private void setUnitsOnData(AnalysisData adMetric, String unitStr){
		Integer unitCode = MeasurementUnit.getCodeFromUnitLabel(unitStr);
		if (unitCode != null)
			adMetric.setUnits(unitCode);		
	}
	private String getChannelLabel(String chanStr, String unitStr){

		StringBuilder sb = new StringBuilder();
		String label = chanStr;
		try {
			int chanNo = Integer.valueOf(chanStr)-1;
			if (chanNo >= 0 && chanNo < c_LabelsByChannel.length){
				String newLabel = c_LabelsByChannel[chanNo];
				if (newLabel != null)
					return label = newLabel;
			}
		}
		catch(Exception e){}
		sb.append(label);
		if (c_AddUnitsToLabel.booleanValue())
			sb.append("(").append(unitStr).append(")");
		return sb.toString();
	}
}
