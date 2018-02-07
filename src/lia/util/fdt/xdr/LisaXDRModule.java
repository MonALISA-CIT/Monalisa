package lia.util.fdt.xdr;

import java.util.List;



/**
 * Interface that must be implemented by modules which export XDR command
 * dispatching interface to remote clients The modules implementing this
 * interface acts as XDR command interpreters
 * 
 * @author adim
 */
public interface LisaXDRModule {
	
	public List getCommandSet();
	
	public String getCommandUsage(String command);
	
	/**
	 * Called when a new command is received for this module
	 * 
	 * @param module the module to receive this command
	 * @param command command received from client
	 * @param args command arguments
	 * @return an <code>XDRMessage</code> filled with command result and exit code
	 * 
	 */
	public XDRMessage execCommand(String module, String command, List args);

}

