package vdab.extnodes.analytical;

import com.lcrc.af.datatypes.AFEnum;

public class CalibrationStep {
	public static final int ADDPOINT	= 1;
	public static final int READPOINT	= 2;
	public static final int CLEARPOINTS	= 3;

	private static AFEnum s_EnumCalibrationStep= new AFEnum("CalibrationStep")
//	.addEntry(EDIT, "Edit Calibration Details")
//	.addEntry(READPOINT, "Read Calibration Point")
	.addEntry(ADDPOINT, "Add Calibration Point")
	.addEntry(CLEARPOINTS, "Clear Calibration Points");

	public static AFEnum getEnum(){
		return s_EnumCalibrationStep;
	}
}
