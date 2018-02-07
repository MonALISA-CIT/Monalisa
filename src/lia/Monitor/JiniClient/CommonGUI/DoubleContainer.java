package lia.Monitor.JiniClient.CommonGUI;

import java.util.Hashtable;

public class DoubleContainer {
	double val;
	
	public DoubleContainer(){
		val = 0;
	}
	
	public DoubleContainer(double value){
		this.val = value;
	}
	
	public void setValue(double value){
		val = value;
	}
	
	public double getValue(){
		return val;
	}
	
	/** add/update the double value of a key in a hash */
	static public void setHashValue(Hashtable hash, Object key, double value){
		DoubleContainer dc = (DoubleContainer) hash.get(key);
		if(dc == null)
			dc = new DoubleContainer(value);
		else
			dc.setValue(value);
		hash.put(key, dc);
	}
	
	/** get a double value corresponding to a key in the given hash. 
	 * If key doesn't exist, or hasn't a DoubleContainer elem, return -1 */
	static public double getHashValue(Hashtable hash, Object key){
		DoubleContainer dc = null;
		try {
			dc = (DoubleContainer) hash.get(key);			
		}catch(ClassCastException ex){
			return -1;
		}
		if(dc == null)
			return -1;
		return dc.getValue();
	}
}
