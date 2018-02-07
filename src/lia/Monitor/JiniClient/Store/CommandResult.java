/**
 * 
 */
package lia.Monitor.JiniClient.Store;

/**
 * @author costing
 * @since Mar 22, 2007
 */
public class CommandResult {
	
	/**
	 * Command that was executed
	 */
	public final String command;
	
	/**
	 * Command output
	 */
	public String output;
	
	
	/**
	 * Initialize all fields
	 * @param command 
	 */
	public CommandResult(final String command){
		this.command = command;
	}
	
	@Override
	public String toString(){
		return "CommandResult["+command+"]="+output;
	}
}
