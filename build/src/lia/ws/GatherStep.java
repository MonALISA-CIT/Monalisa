package lia.ws;

import java.util.Vector;

/**
 * 
 * @author mickyt
 *
 */
public class GatherStep {

    public long initTime ;
    public long lastTime ;

    public String farmName ;
    public String clusterName ;
    public String nodeName ;
    
    public Vector paramNames ;
    public Vector paramValues ;
    
    public GatherStep (String farmName, String clusterName, String nodeName) {
	this.farmName = farmName ;
	this.clusterName = clusterName ;
	this.nodeName = nodeName ;
	paramNames = new Vector ();
	paramValues = new Vector ();
    }
    
    public void setInitTime (long initTime ) {
	this.initTime = initTime;
    }
    
    public void setLastTime (long lastTime ) {
	this.lastTime = lastTime;
    }
    
    public void addParamName (String paramName) {
	paramNames.add (paramName);
    }
    
    public void addParamValue (Object paramValue) {
//	Double value = Double.valueOf(paramValue);
	paramValues.add (paramValue);
    }

} //GatherStep