package vdab.extnodes.ndbc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

import com.lcrc.af.AnalysisCompoundData;
import com.lcrc.af.AnalysisEvent;

import com.lcrc.af.util.IconUtility;

import vdab.api.node.HTTPService_A;

public class NDBCBuoyDataService  extends HTTPService_A{
	private static String PROPER_HEADER="#YY  MM DD hh mm WDIR WSPD GST  WVHT   DPD   APD MWD   PRES  ATMP  WTMP  DEWP  VIS PTDY  TIDE";
	private static int EXPECTED_DATA_PARTS= 19;
	private static String[] DATA_LABELS = new String[]{"","","","","","WindDirection","WindSpeed","GustSpeed","WaveHeight","DominantWavePeriod","AverageWavePeriod","MedianWaveDirection","AtmosphericPressure","AirTemperature","WaterTemperature","DewPoint","Visibility","PressureTendency","Tide"};
	private static String DATA_LABEL= "NDBCData";
	private static String API_ENDPOINT= "https://www.ndbc.noaa.gov/data/realtime2/";
	private static String PATH_PREFIX = "NDBC_";
	private static long MIN_TIMECHANGE = 100L;
	private static Double[] c_LastValues = new Double[19];
	private static long[] c_LastValueTime = new long[19];
	private static SimpleDateFormat SDF_NDBC = new SimpleDateFormat("yyyyMMddHHmm");
	private String c_BuoyID;
	private long c_LastEventTimestamp = 0L;
	public Integer get_IconCode(){
		return  IconUtility.getIconHashCode("node_noaa");
	}
	public String get_BuoyID() { 
		return c_BuoyID;
	}
	public void set_BuoyID(String code){
		c_BuoyID = code;
	}
	public String get_LatestReceived(){
		if (c_LastEventTimestamp > 0L){
			return new Date(c_LastEventTimestamp).toString();	
			
		}
		return null;
	}
	public void _init(){
		
		super._init();
		SDF_NDBC.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	public String buildCompleteURL(AnalysisEvent ev) {
		StringBuilder sb = new StringBuilder();
		sb.append(API_ENDPOINT);
		sb.append(c_BuoyID);
		sb.append(".txt");
		return sb.toString();
	}

	public void processReturnStream(AnalysisEvent inEvent, int retCode, InputStream is) {
		BufferedReader in = new BufferedReader(new InputStreamReader(is));
		String line;
		String lineHeader1 = null;
		String lineHeader2 = null;
		String dataLine = null;
		try {
			while ((line = in.readLine()) != null)	{
				if (lineHeader1 == null){
					lineHeader1 = line;
				}
				else if (lineHeader2 == null){
					lineHeader2 = line;
				}
				else {
					dataLine = line;
					break;
				}
			}

			if (!lineHeader1.equals(PROPER_HEADER)){
				setWarning("NDBC data header not in expected format, not reading data");
				serviceFailed(inEvent, 4);
				return;
			}
	

			StringTokenizer st = new StringTokenizer(dataLine);
			int noDataParts = st.countTokens();
			if (noDataParts != EXPECTED_DATA_PARTS){
				setWarning("NDBC data values not in expected format, not reading data");
				serviceFailed(inEvent, 5);
				return;
			}	
			
			ArrayList<String> dataList = new ArrayList<String>();
			while (st.hasMoreTokens()){
				String token = st.nextToken();
				dataList.add(token);
			}
			String[] dataParts = dataList.toArray(new String[dataList.size()]);
			long ts = getEventTS(dataParts);
			if (isTraceLogging())
				logTrace("Received data from BUOY="+c_BuoyID+" DATE="+new Date(ts).toString());

			long timeNow = System.currentTimeMillis();
			if (ts > timeNow) {
				setError("Time calculation ERROR, time is in the future DIFF="+(ts-timeNow)+" data dropped");
				serviceFailed(inEvent, 6);
				return;
			}
			if (ts > c_LastEventTimestamp) { // This is a new value.
				c_LastEventTimestamp = ts+MIN_TIMECHANGE;
				AnalysisCompoundData acd = new AnalysisCompoundData(DATA_LABEL);
				getAllData(ts, acd, dataParts);
				AnalysisEvent ev = new AnalysisEvent(ts, this, acd);
				serviceResponse(inEvent, ev);
			}
			else { // HACKALERT - Same value just complete.
				return;
			}
				
		}
		catch (Exception e){
			serviceFailed(inEvent, 3);
		}

	}
	public String getDataPath(){ // Overrides data path;
		return PATH_PREFIX+c_BuoyID;
	}
	private long getEventTS(String[] dataParts){
		long tsNow = System.currentTimeMillis();
		
		StringBuilder sb = new StringBuilder();
		for (int n=0; n < 5; n++)
			sb.append(dataParts[n]);
		String dateStr = sb.toString();
		try {
			Date evDate = SDF_NDBC.parse(dateStr);
			return evDate.getTime();
		} 
		catch (ParseException e) {
			setWarning("Failed parsing NDBC Data DATE="+dateStr+" Using current time.");
			return tsNow;
		}
	}
	private void getAllData(long ts, AnalysisCompoundData acd, String[] dataParts){
		for (int n=0; n < DATA_LABELS.length; n++){
			if (DATA_LABELS[n].length() > 0 ){ // If there is a label, should look for that value.
				if (!dataParts[n].equals("MM")) { // If value is available, use it
					try { 
						c_LastValues[n] = Double.valueOf(dataParts[n]);
						acd.addAnalysisData(DATA_LABELS[n], c_LastValues[n]);
						c_LastValueTime[n] = ts;
					}
					catch (Exception e){
						setError("Unable to add NDBC data point LABEL="+DATA_LABELS[n]+" DATA="+dataParts[n]);
					}
				}
				else {  // If there is not a value but there was a recent one (< 1 1/2 hours) use that one
					if (c_LastValues[n] != null && (ts - c_LastValueTime[n]) < 4400000L){
						if (isTraceLogging())
							logTrace("No current value for LABEL="+DATA_LABELS[n]+" using previously received data, VALUE="+c_LastValues[n]);
						acd.addAnalysisData(DATA_LABELS[n], c_LastValues[n]);
					}
				}
			}
			
		}
		
	}
}
