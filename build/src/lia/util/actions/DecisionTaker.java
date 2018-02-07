/**
 * 
 */
package lia.util.actions;

import lia.util.MLProperties;
import lia.util.actions.Action.SeriesState;

/**
 * @author costing
 * @since May 22, 2007
 */
public interface DecisionTaker {

	/**
	 * Initialize internal variables based on the configuration file
	 * 
	 * @param mlp
	 * @throws Exception 
	 */
	public void init(MLProperties mlp) throws Exception;
	
	/**
	 * Find out the current value for a given series
	 * 
	 * @param ss
	 * @return the result
	 */
	public DecisionResult getValue(SeriesState ss);
}
