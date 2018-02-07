package lia.web.servlets.web;

import java.util.Hashtable;

/**
 * 
 */
class WLink {
	/**
	 * 
	 */
	WNode src;
	/**
	 * 
	 */
	WNode dest;
	/**
	 * 
	 */
	Hashtable<String, Double> data;	// key = string ; value = Double
	
	/**
	 * 
	 */
	public java.util.Vector<Integer> vMap = null;
	
	/**
	 * @param source
	 * @param destination
	 * @param dataMap
	 */
	public WLink(WNode source, WNode destination, Hashtable<String, Double> dataMap){
		src = source;
		dest = destination;
		data = dataMap;
	}
}
