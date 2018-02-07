/*
 * Created on Apr 8, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package lia.web.servlets.map2d;

/**
 * @author alexc
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
import java.util.Hashtable;

public class WLink1 {
	WNode1 src;
	WNode1 dest;
	Hashtable data;	// key = string ; value = Double
	
	public java.util.Vector vMap = null;
	
	public WLink1(WNode1 src, WNode1 dest, Hashtable data){
		this.src = src;
		this.dest = dest;
		this.data = data;
	}
}