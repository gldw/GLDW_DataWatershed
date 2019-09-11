package vdab.extnodes.wqdata;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;

import com.lcrc.af.AnalysisData;
import com.lcrc.af.AnalysisDataDef;
import com.lcrc.af.AnalysisEvent;
import com.lcrc.af.constants.HTTPMethodType;
import com.lcrc.af.constants.SpecialText;
import com.lcrc.af.util.AnalysisDataUtility;
import com.lcrc.af.util.ControlDataBuffer;
import com.lcrc.af.util.IconUtility;
import com.lcrc.af.util.StringUtility;

import vdab.api.node.HTTPService_A;
import vdab.core.nodes.http.ServiceHandler_HTTP;

public class WQDataService extends HTTPService_A{
	private static String API_ENDPOINT= "https://v2.wqdatalive.com/public/";
	private  DateFormat c_WQDateFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");	
	private Integer c_ProjectCode;
	private String c_TimeZone ;

	private ControlDataBuffer c_cdb_ParamIDs = new ControlDataBuffer("WQDataService_ParameterIDs");;
	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_wqdata");
	}
	public String get_TimeZone(){
		return c_TimeZone;
	}
	public Integer get_ProjectCode() { 
		return c_ProjectCode;	
	}
	public void set_ProjectCode(Integer code){
		c_ProjectCode = code;
	}
	public String get_ParamIDs() {
		if(c_cdb_ParamIDs.isEmpty())
			return null;
		return c_cdb_ParamIDs.getAllSet(","); 
	}	
	public void set_ParamIDs(String ids){	
		// Multiple attributes, probably read from xml
		if (ids.contains(",")){
			c_cdb_ParamIDs.setAll(ids,","); 			
		} 
		// Clear command from option picker
		else if (ids.equals(SpecialText.CLEAR)){
			c_cdb_ParamIDs.clear();
			return;
		}
		else {
		// One value to add.
			if (!c_cdb_ParamIDs.isSet(ids))
				c_cdb_ParamIDs.set(ids);
		}
	}
	public AnalysisDataDef def_ParamIDs(AnalysisDataDef theDataDef){
		ArrayList<String> l = new ArrayList<String>();
		if (!c_cdb_ParamIDs.isEmpty())
			l.add(SpecialText.CLEAR);
		theDataDef.setAllPickValues(l.toArray(new String[l.size()]));
		return theDataDef;
	}
	@Override
	public void _init(){
		super._init();
		set_HTTPMethod(Integer.valueOf(HTTPMethodType.POST));
	}
	@Override
	public void _start(){
		super._start();
	}
	@Override
	public String buildCompleteURL(AnalysisEvent ev) {
		if (c_ProjectCode == null)
			return null;	
		StringBuilder sb = new StringBuilder();
		sb.append(API_ENDPOINT);
		sb.append(c_ProjectCode);
		sb.append("/data");
		return sb.toString();
	}
	@Override
	public synchronized void processEvent(AnalysisEvent ev){
		
		if (ev.isTriggerEvent()){
			for (String paramID: c_cdb_ParamIDs.getAllSet()){
				new ServiceHandler_HTTP(this, new AnalysisEvent(this, new AnalysisData("paramID",paramID)));	
				break;
			}
		}
		else { // Not trigger event
			AnalysisData ad = getSelectedData(ev);
			if (ad == null  || !ad.isSimple()){
				setWarning("Selected Simple data not available EVENT="+ev+" NEEDED="+get_SelectedElement());
				return;
			}
			if (ad.getLabel().equalsIgnoreCase("PARAMID")){
				new ServiceHandler_HTTP(this, new AnalysisEvent(this, new AnalysisData("paramID",ad.getDataAsString())));
			}
			else {
				for (String paramID: c_cdb_ParamIDs.getAllSet()){
					new ServiceHandler_HTTP(this, new AnalysisEvent(this, new AnalysisData("paramID",paramID)));	
					break;
				}
		
			}
		}
	}
	@Override
	public String buildPost(AnalysisEvent ev){
		StringBuilder sb = new StringBuilder();
		// For get methods, params are part of the Query URL.
		if (!ev.isTriggerEvent() ){
			sb.append(AnalysisDataUtility.buildPostParams(ev.getAnalysisData()));
			return sb.toString();
		}
		return null;
	}
	public void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuilder sb = new StringBuilder();
		try {
			while ((line = in.readLine()) != null)
				sb.append(line);	
			String rawData = sb.toString();
			// Find timezone first time.
			if (c_TimeZone == null){
				String tzone = StringUtility.locateBetween(rawData,"\"timezone\":\"", "\"");
				c_TimeZone = tzone.replace("\\", "");
				if (isTraceLogging())
					logTrace("Incoming data included the timezone TZ="+c_TimeZone);

			}
			if (c_TimeZone == null){
				setError("Timezone not defined, unable to process data");
				serviceFailed(inEvent, 6);
				return;
			}
			String tableData = StringUtility.locateBetween(rawData,"\"aaData\":[[\"", "\"]");
			String[] dataTableParts = tableData.split("\",\"");
			c_WQDateFormat.setTimeZone(TimeZone.getTimeZone(c_TimeZone));
			long ts = c_WQDateFormat.parse(dataTableParts[0]).getTime();	
			long t0 = System.currentTimeMillis();
			if (isTraceLogging())
				logTrace("Received data time information: DATE="+dataTableParts[0]+" OFFSET="+(t0-ts)/60000L+" minutes");
			
			ArrayList<String> titles = StringUtility.locateAllBetween(rawData,"\"title\":\"", "\",");
			String label = null;
			if (titles.size() > 1)
				label = titles.get(1);
	
			String value = null;
			if (dataTableParts.length > 1)
				value = dataTableParts[1];
			if (value == null || label == null){
				setError("Unable to interpret returned data DATA="+tableData);
				serviceFailed(inEvent, 4);
				return;	
			}
			AnalysisEvent ev = new AnalysisEvent(ts, this, new AnalysisData(label, Double.valueOf(value)));
			serviceResponse(inEvent, ev);
		}
		catch (Exception e){
			setError("Failed processing return data e>"+e);
			serviceFailed(inEvent, 3);
		}

	}
}
