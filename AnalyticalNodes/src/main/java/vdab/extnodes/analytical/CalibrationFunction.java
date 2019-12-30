package vdab.extnodes.analytical;

import com.lcrc.af.datatypes.AFEnum;

public class CalibrationFunction {
	public static final int BESTFIT = 0;
	public static final int LINEAR = 1;
	public static final int LOG = 2;
	public static final int POLY = 3;

	private static AFEnum s_EnumCalibrationFunction = new AFEnum("CalibrationFunction")
	.addEntry(LINEAR, "Linear")
	.addEntry(LOG, "Logrithmic")
	.addEntry(POLY, "Polynomial");

	public static AFEnum getEnum(){
		return s_EnumCalibrationFunction;
	}
}
