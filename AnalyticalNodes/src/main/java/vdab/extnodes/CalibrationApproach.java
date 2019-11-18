package vdab.extnodes.analytical;

import com.lcrc.af.datatypes.AFEnum;

public class CalibrationApproach {
	public static final int SLOPEINTERCEPT = 1;
	public static final int TWOPOINT = 2;
	public static final int MULTIPOINT= 3;

	private static AFEnum s_EnumCalibrationApproach = new AFEnum("CalibrationApproach")
	.addEntry(SLOPEINTERCEPT , "Enter Slope Intercept")
	.addEntry(TWOPOINT, "Two Point Calibration")
	.addEntry(MULTIPOINT, "Multi Point Calibration");

	public static AFEnum getEnum(){
		return s_EnumCalibrationApproach;
	}
}
