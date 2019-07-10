package vdab.extnodes.usgs;

import com.lcrc.af.datatypes.AFEnum;

public class USGSDataNamingType {
	public final static int ORIGINAL = 0;
	public final static int ENHANCED = 1;

	
	private static AFEnum s_EnumUSGSDataNamingType = new AFEnum("USGSDataNamingType")
	.addEntry(USGSDataNamingType.ORIGINAL,  "Original")
	.addEntry(USGSDataNamingType.ENHANCED, "Enhanced")

	;
	public static AFEnum getEnum(){
		return s_EnumUSGSDataNamingType ;
	}
}
