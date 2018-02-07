/**
 * 
 */
package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.Color;
import java.util.HashMap;

/**
 * @author florinpop
 *
 */
public class OSGColor {

	public static HashMap VO_COLOR = new HashMap();
	static{
		VO_COLOR.put( "ACCELERATOR",	new Color( 255, 153,  51 ) );
		VO_COLOR.put( "ASTRO",			new Color( 205, 204, 204 ) );
		VO_COLOR.put( "ATLAS",			new Color( 105,   0, 178 ) );
		VO_COLOR.put( "AUGER",			new Color( 204, 255, 153 ) );
		VO_COLOR.put( "BABR",			new Color( 204, 255, 204 ) );
		VO_COLOR.put( "CDF",			new Color( 134, 255, 171 ) );
		VO_COLOR.put( "CDMS",			new Color(  51,  51,   0 ) );
		VO_COLOR.put( "CMS",			new Color( 204,   0,   0 ) );
		VO_COLOR.put( "DAGS",			new Color( 104,   0,   0 ) );
		VO_COLOR.put( "DES",			new Color(   0, 205, 204 ) );
		VO_COLOR.put( "DOSAR",			new Color(   0, 153, 255 ) );
		VO_COLOR.put( "DTEAM",			new Color(   0, 102, 255 ) );
		VO_COLOR.put( "DZERO",			new Color(  153, 51,   0 ) );
		VO_COLOR.put( "FERMILAB",		new Color(   0,  91, 127 ) );
		VO_COLOR.put( "FERMILAB-TEST",	new Color(   0,  91, 120 ) );
		VO_COLOR.put( "FMRI",			new Color( 255, 196, 255 ) );
		VO_COLOR.put( "GADU",			new Color( 255, 218,  72 ) );
		VO_COLOR.put( "GEANT4",			new Color(   0,  51, 255 ) );
		VO_COLOR.put( "GLOW",			new Color(  51,   0, 204 ) );
		VO_COLOR.put( "GRASE",			new Color( 233, 255,  51 ) );
		VO_COLOR.put( "GRIDEX",			new Color( 255, 102,   0 ) );
		VO_COLOR.put( "GROW",			new Color( 255, 102, 255 ) );
		VO_COLOR.put( "HYPERCP",		new Color(   0,  51,   0 ) );
		VO_COLOR.put( "I2U2",			new Color(   0, 204,  51 ) );
		VO_COLOR.put( "ILC",			new Color(   0, 102,  51 ) );
		VO_COLOR.put( "IVDGL",			new Color( 153, 153,   0 ) );
		VO_COLOR.put( "KTEV",			new Color(  51, 153, 102 ) );
		VO_COLOR.put( "LIGO",			new Color(   0, 204,   0 ) );
		VO_COLOR.put( "LCGMS",			new Color( 102, 204, 153 ) );
		VO_COLOR.put( "LQCD",			new Color( 153, 255, 204 ) );
		VO_COLOR.put( "MINIBOONE",		new Color( 204, 255, 255 ) );
		VO_COLOR.put( "MINOS",			new Color(  51, 153, 255 ) );
		VO_COLOR.put( "MIPP",			new Color( 204, 204, 255 ) );
		VO_COLOR.put( "MIS",			new Color( 255, 255,   0 ) );
		VO_COLOR.put( "NANOHUB",		new Color( 204, 204, 204 ) );
		VO_COLOR.put( "N0VA",			new Color( 153, 200, 153 ) );
		VO_COLOR.put( "NUMI",			new Color( 153, 153, 153 ) );
		VO_COLOR.put( "OSG",			new Color(  51,   0,  51 ) );
		VO_COLOR.put( "PATRIOT",		new Color(   0, 255,  51 ) );
		VO_COLOR.put( "SDSS",			new Color(   0, 178, 178 ) );
		VO_COLOR.put( "STAR",			new Color( 204, 153,  51 ) );
		VO_COLOR.put( "THEORY",			new Color(  51,  51,  51 ) );
		VO_COLOR.put( "USATLAS",		new Color( 102, 102, 102 ) );
		VO_COLOR.put( "USCMS",			new Color( 102, 204, 102 ) );
		VO_COLOR.put( "VDT",			new Color( 204, 102, 204 ) );
	}
	
	public static HashMap PARAM_COLOR = new HashMap();
	static{
	
	}
	
	public static Color getVoColor(String voName){
		if (VO_COLOR.containsKey(voName))
			return (Color) VO_COLOR.get(voName.toUpperCase());
		else
			return new Color( 0, 0, 0);
	}
	
	public static Color getParamColor(String paramName){
		if (PARAM_COLOR.containsKey(paramName))
			return (Color) PARAM_COLOR.get(paramName.toUpperCase());
		else
			return new Color( 0, 0, 0);
	}
	
	public static String decodeColor(Color color) {
		
		StringBuilder buf = new StringBuilder();
		buf.append("#");
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		String s = Integer.toHexString(r);
		if (s.length() == 1) buf.append("0");
		buf.append(s);
		s = Integer.toHexString(g);
		if (s.length() == 1) buf.append("0");
		buf.append(s);
		s = Integer.toHexString(b);
		if (s.length() == 1) buf.append("0");
		buf.append(s);
		return buf.toString();
	}
	
}
