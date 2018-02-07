package lia.web.servlets.web;

import java.util.List;

/**
 * 
 */
public class WNode implements Comparable<WNode> {
	/**
	 * Node name
	 */
	public String name;
	
	/**
	 * Node real name
	 */
	public String realname;
	
	/**
	 * Values
	 */
	List<Double> data;
	
	// if data == null => drawn as router
	// if data.size == 0 => drawn with my color
	// if data.size == 1 => drawn cu degradeus
	// if data.size > 1 => drawn as pie
	/**
	 * Alternate
	 */
	public List<Object> alternate;

	/**
	 * 
	 */
	double LONG = -10;
	/**
	 * 
	 */
	double LAT = -10;

	/**
	 * Coordinates
	 */
	public int x;
	
	/**
	 * Coordinates
	 */
	public int y;
	
	/**
	 * Radius
	 */
	public int r = 8;
	
	/**
	 * X offset
	 */
	public int xLabelOffset = -32;
	
	/**
	 * Y offset
	 */
	public int yLabelOffset = -12;
	
	/**
	 * Size
	 */
	public int fontsize     = 14;
	    
	/**
	 * 
	 */
	java.awt.Color colors[];

	/**
	 * @param sName
	 * @param sLONG
	 * @param sLAT
	 * @param lData
	 */
	public WNode(String sName, String sLONG, String sLAT, List<Double> lData){
		name = sName;
		data = lData;
		try{
			LONG = Double.parseDouble(sLONG);
		}catch(NumberFormatException ex){
			// skip
		}
		try{
			LAT = Double.parseDouble(sLAT);
		}catch(NumberFormatException ex){
			// skip
		}		
		
		alternate = null;
	}
	
	@Override
	public int compareTo(WNode o) {
		return name.compareTo(o.name);
	}
	
	@Override
	public boolean equals(Object o){
	    if (o instanceof WNode)
	    	return name.equals(((WNode)o).name);
	    
		return false;
	}
	
	@Override
	public int hashCode(){
		return 1;
	}
}
