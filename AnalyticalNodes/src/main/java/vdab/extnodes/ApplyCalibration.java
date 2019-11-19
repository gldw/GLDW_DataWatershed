package vdab.extnodes.analytical;

import java.util.ArrayList;

import com.lcrc.af.AnalysisCompoundData;
import com.lcrc.af.AnalysisData;
import com.lcrc.af.AnalysisDataDef;
import com.lcrc.af.AnalysisEvent;
import com.lcrc.af.function.AnalysisFunction;

public class ApplyCalibration extends AnalysisFunction {
	static {
		CalibrationStep.getEnum();
		CalibrationApproach.getEnum();
		CalibrationFunction.getEnum();
	}
	private ArrayList<Double> c_RawValues = new ArrayList<Double>();
	private ArrayList<Double> c_CalibratedValues = new ArrayList<Double>();
	// Expected Relationship.
	private Boolean c_RetainBoth = Boolean.TRUE;
	private Integer c_CalibrationStep = CalibrationStep.ADDPOINT; 
	private Integer c_CalibrationApproach = CalibrationApproach.SLOPEINTERCEPT;
	private String c_MetricLabel ;
	private Integer c_MetricUnit ;
	private Double c_Slope;
	private Double c_Intercept;
	public Integer get_CalibrationStep(){
		return c_CalibrationStep;
	}
	public void set_CalibrationStep(Integer step){
		c_CalibrationStep = step;
		if (c_CalibrationStep.intValue() == CalibrationStep.CLEARPOINTS){
			c_RawValues.clear();
			c_CalibratedValues.clear();
		}
	}
	public AnalysisDataDef def_CalibrationStep(AnalysisDataDef theDataDef){
		
		if ((c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT))
			theDataDef.disable();
		
		return theDataDef;
	}
	public Boolean get_RetainRawValue(){
		return c_RetainBoth;
	}
	public void set_RetainRawValue(Boolean retain){
		c_RetainBoth = retain;
	}
	public void set_AddRawValue(Double value){
		c_RawValues.add(value);
	}
	public Double get_AddRawValue(){
		return c_RawValues.get(c_RawValues.size());
	}
	public AnalysisDataDef def_AddRawValue(AnalysisDataDef theDataDef){
	
		if ((c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT)){
			theDataDef.disable();
			return theDataDef;
		}
		switch (c_CalibrationStep.intValue()){

		case CalibrationStep.ADDPOINT:		
			break;	
		case CalibrationStep.CLEARPOINTS:
			theDataDef.disable();
			break;
		}
		return theDataDef;
	}
	public void set_AddCalibratedValue(Double value){
		c_CalibratedValues.add(value);
	}
	public Double get_AddCalibratedValue(){
		return c_CalibratedValues.get(c_CalibratedValues.size());
	}
	public AnalysisDataDef def_AddCalibratedValue(AnalysisDataDef theDataDef){
	
		if ((c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT)){
			theDataDef.disable();
			return theDataDef;
		}

		switch (c_CalibrationStep.intValue()){		
		case CalibrationStep.ADDPOINT:
			break;
			
		case CalibrationStep.CLEARPOINTS:
			theDataDef.disable();
			break;

		}
		return theDataDef;
	}
	public void set_MetricLabel(String label){
		c_MetricLabel = label;
	}
	
	public String get_MetricLabel(){
		return c_MetricLabel;
	}

	public AnalysisDataDef def_MetricLabel(AnalysisDataDef theDataDef){
		return theDataDef;
	}

	public void set_MetricUnit(Integer unit){
		c_MetricUnit = unit;
	}
	
	public Integer get_MetricUnit(){
		return c_MetricUnit;
	}
	public void set_CalibrationApproach(Integer approach){
		c_CalibrationApproach = approach;
	}
	
	public Integer get_CalibrationApproach(){
		return c_CalibrationApproach;
	}
	public void set_Slope(Double slope){
		c_Slope = slope;
	}
	public Double get_Slope(){
		return c_Slope;
	}

	public AnalysisDataDef def_Slope(AnalysisDataDef theDataDef){
		if (!(c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT))
			theDataDef.disable();
		
		return theDataDef;
	}

	public void set_Intercept(Double intercept){
		c_Intercept = intercept;
	}
	public Double get_Intercept(){
		return c_Intercept;
	}
	public AnalysisDataDef def_Intercept(AnalysisDataDef theDataDef){
		if (!(c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT))
			theDataDef.disable();
		
		return theDataDef;
	}
	public void set_CalibrationPoints(String points){
		loadCalibrationPoints(points);
	}
	public String get_CalibrationPoints(){
		return getCalibrationPoints();
	}
	public AnalysisDataDef def_CalibrationPoints(AnalysisDataDef theDataDef){
		if ((c_CalibrationApproach.intValue() == CalibrationApproach.SLOPEINTERCEPT))
			theDataDef.disable();
		
		return theDataDef;
	}

	private void loadCalibrationPoints(String points){
		String[] calibPoints = points.split("\\],") ;
		for (String calibPoint :calibPoints){
			String[] parts = calibPoint.split(",");
			try {
				Double calibValue = Double.valueOf(parts[0].substring(1));
				Double rawValue = Double.valueOf(parts[1]);
				c_CalibratedValues.add(calibValue);
				c_RawValues.add(rawValue);
			}
			catch (Exception e){
				setWarning("Failed reading calibration value POINT="+calibPoint);
			}			
		}	
	}
	private String getCalibrationPoints(){
		StringBuilder sb = new StringBuilder();
		for (int n=0; n < c_CalibratedValues.size(); n++){
			sb.append("[");
			sb.append(c_CalibratedValues.get(n));
			if (n < c_RawValues.size()){
				sb.append(",");
				sb.append(c_RawValues.get(n));
			}
			sb.append("],");
		}		
		return sb.toString();
	}
	public synchronized void processEvent(AnalysisEvent ev){
		if (ev.isTriggerEvent())
			return;
		
		AnalysisData adIn = ev.getAnalysisData();
		AnalysisData adSel =  getSelectedData(adIn);
		
		// If selected data is not available, just exit
		if (adSel == null){
			return;
		}

		AnalysisData adInClone;
		try {
			adInClone = (AnalysisData) adIn.clone();
		} 
		catch (Exception e) {
			setError("Unable to clone incoming event data EVENT="+ev);
			return;
		}
		adSel =   getSelectedData(adInClone);
		// --- SIMPLE INPUT ----- 
		processCalibration(adSel);
		publishDerivedEvent(ev, adInClone );
	}
	private void processCalibration(AnalysisData ad){

		Double calibVal = calibrateIt(ad.getDataAsDouble());
		if (calibVal == null){
			setError("Unable to perform data conversion, event dropped for DATA="+ad.getLabel());
			return;
		}
		
		AnalysisData adParent = ad.getParent();
		if (!c_RetainBoth.booleanValue() || adParent == null || adParent.isSimple()){ // If not retaining the old just overwrite
			ad.setData(calibVal);
			ad.setLabel(c_MetricLabel);
			ad.setUnits(c_MetricUnit);
		}
		else {							// If retaining the add new value ad this one with suffix of units.
			AnalysisData adNew = new AnalysisData(c_MetricLabel, calibVal);
			adNew.setUnits(c_MetricUnit);
			((AnalysisCompoundData) adParent).addAnalysisData(adNew);
		}
	}
	private Double calibrateIt(Double val){
		
		switch (c_CalibrationApproach.intValue()){
		case CalibrationApproach.SLOPEINTERCEPT:
			Double retVal = (val * c_Slope) - c_Intercept;
			return retVal;
		default:
			setError("Currently only slope intercept calibration is supported");
			return null;
		
		}
		
	}

}
