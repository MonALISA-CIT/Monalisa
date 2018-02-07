/**
 * 
 */
package lia.util.actions;

/**
 * @author costing
 * @since May 22, 2007
 */
public class DecisionResult {

	/**
	 * What is the status for this series.
	 */
	public boolean bOk = false;
	
	/**
	 * Flag indicating the presence of data
	 */
	public boolean bData = false;
	
	/**
	 * Value that was at the base of the decision
	 */
	public String sValue = "";
	
	@Override
	public String toString(){
		return "OK:"+this.bOk+", has data:"+this.bData+", message:'"+this.sValue+"'";
	}
	
}
