package lia.Monitor.JiniClient.CommonGUI.Groups.Plot;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

public interface MultipleDataPlotter {
	
	/** called from parent to dispose all allocated resources */
	public boolean stopIt(rcNode node); // true if full close or false otherwise
	
	/** called to set local time for this plot. the timeZone and local time are of particular interest */
	public void setLocalTime( String dd );
	
	/** called to set the country code */
	public void setCountryCode(String cc);
	
	/** called to set the farm name */
	public void setFarmName(String farmName);
}
