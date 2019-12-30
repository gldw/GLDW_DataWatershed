package vdab.extnodes.analytical.hab;

import vdab.api.event.VDABData;
import vdab.api.node.JavaFunctionSet_A;

public class HABCalculations extends JavaFunctionSet_A{
	public VDABData func_BGAChlorRatio(VDABData vData, String selectedPath){
		
		try {
			Double bga  = vData.getDataAsDouble(selectedPath+".BGA-PC (ug/L)");
			Double chlor =  vData.getDataAsDouble(selectedPath+".Chlorophyll (ug/L)");
		    if (bga != null && chlor != null && chlor.doubleValue() > 0.1D){
		    	Double ratio = Double.valueOf(bga.doubleValue()/chlor.doubleValue());
		    	Double roundedRatio = roundDouble(ratio, 6);
		    	vData.addDataObject("BGA/Chlorophyll", roundedRatio);
		    }
		}
		catch (Exception e) {
			setError("Could not find BGA or CHLOR in data AD="+vData+" e>"+e);
		}
		return vData;
	}

	private Double roundDouble(Double val, int numberOfDigits) {
		
		String strVal = val.toString();

		
		// Concatenate if it happens to be too long.
		int decPos = strVal.indexOf(".");
		int len = Math.min(strVal.length(), numberOfDigits);
		if (decPos > len)
			len = decPos;
		return Double.valueOf(strVal.substring(0, len));

	}
}
