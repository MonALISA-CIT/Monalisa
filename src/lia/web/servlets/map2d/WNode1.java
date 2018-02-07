/*
 * Created on Apr 8, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package lia.web.servlets.map2d;

/**
 * @author alexc
 */


import java.awt.Color;
import java.util.List;

public class WNode1 implements Comparable {
	public String name;
	public String realname;
	List data;
	// if data == null => drawn as router
	// if data.size == 0 => drawn with my colour
	// if data.size == 1 => drawn cu degradeu
	// if data.size > 1 => drawn as pie
	public List alternate;

	double LONG = -10;
	double LAT = -10;

	public int x, y;
	
	public int x_alt, y_alt;
	public int r = 8;
	
	public int 
	    xLabelOffset = -32,
	    yLabelOffset = -12,
	    fontsize     = 14;
	    
	Color colors[];
    
    public WNode1(WNode1 original) {
        this.name = original.name;
        this.realname = original.realname;
        this.data = original.data;
        this.LONG = original.LONG;
        this.LAT = original.LAT;
        alternate = original.alternate;
        this.x = original.x;
        this.y = original.y;
        this.x_alt = original.x_alt;
        this.y_alt = original.y_alt;
        r = original.r;
        xLabelOffset = original.xLabelOffset;
        yLabelOffset = original.yLabelOffset;
        fontsize = original.fontsize;
        colors = new Color[original.colors.length];
        System.arraycopy( original.colors, 0, colors, 0, original.colors.length);
    }

	public WNode1(String name, String LONG, String LAT, List data){
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
	
	public int compareTo(Object o) {
		if(o instanceof WNode1)
			return name.compareTo(((WNode1)o).name);
		return 0;
	}
	
	public boolean equals(Object o){
	    if (o!=null && o instanceof WNode1)
	    	return name.equals(((WNode1)o).name);
	    else
	    	return false;
	}
	
	public int hashCode(){
		return name.hashCode();
	}
}
