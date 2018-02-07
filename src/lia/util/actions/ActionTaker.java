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
public interface ActionTaker {
	
	/**
	 * Method called from Action when an action should be taken
	 * 
	 * @param ss the details of the series that needs acting upon
	 */
	public void takeAction(SeriesState ss);
	
	/**
	 * Initialize the internal variables from a configuration file for a given entry number
	 * 
	 * @param iEntry entry number
	 * @param mlp configuration file contents
	 * @throws Exception in case something goes wrong, an initialization that failed will cause 
	 * 		the action to be ignored because it is not properly specified 
	 */
	public void initAction(final int iEntry, final MLProperties mlp) throws Exception;
}
