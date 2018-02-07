/*
 * Created on Apr 7, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package lia.web.servlets.map2d;

import java.util.List;

import lia.web.servlets.web.WNode;

/**
 * @author alexc
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Node2D extends WNode{

	public List data;
	public java.awt.Color colors[];
	double LONG = -10;
	double LAT = -10;
	
	public Node2D(String name, String LONG, String LAT, List data){
		super(name, LONG, LAT, data);
		this.name = name;
		this.data = data;
		try{
			this.LONG = Double.parseDouble(LONG);
		}catch(NumberFormatException ex){
			// skip
		}
		try{
			this.LAT = Double.parseDouble(LAT);
		}catch(NumberFormatException ex){
			// skip
		}		
		
		alternate = null;
		
	}
}
