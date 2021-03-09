package vdab.extnodes.usgs;

import com.lcrc.af.datatypes.AFEnum;

public class USGSMetricLabel {
	public static final int AIRTEMP = 10;
	public static final int FLOW = 60;
	public static final int STAGE = 65;
	public static final int CONDUCTIVITY = 95;
	public static final int PH = 400;
	public static final int BATTERY = 70969;
	private static AFEnum s_EnumUSGSMetricLabel = new AFEnum("USGSMetricLabel")
	.addEntry(AIRTEMP,"water_temp")
	.addEntry(FLOW,"flow")
	.addEntry(STAGE, "stage")
	.addEntry(CONDUCTIVITY, "sp_cond")
	.addEntry(PH, "ph")
	.addEntry(BATTERY, "battery")
	;
	public static AFEnum getEnum(){
		return s_EnumUSGSMetricLabel ;
	}
}
