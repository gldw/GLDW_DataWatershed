package vdab.extnodes.usgs;

import com.lcrc.af.datatypes.AFEnum;

public class USGSMetricLabel {
	public static final int AIRTEMP = 10;
	public static final int FLOW = 60;
	public static final int STAGE = 65;
	public static final int BATTERY = 70969;
	private static AFEnum s_EnumUSGSMetricLabel = new AFEnum("USGSMetricLabel")
	.addEntry(AIRTEMP,"air_temp")
	.addEntry(FLOW,"flow")
	.addEntry(STAGE, "stage")
	.addEntry(BATTERY, "battery")
	;
	public static AFEnum getEnum(){
		return s_EnumUSGSMetricLabel ;
	}
}
