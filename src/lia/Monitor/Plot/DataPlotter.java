package lia.Monitor.Plot;

public interface DataPlotter {
	
	/** called from parent to dispose all allocated resources */
	public void stopIt();
	
	/** called to set local time for this plot. the timeZone and local time are of particular interest */
	public void setLocalTime( String dd );
	
	/** called to set the country code */
	public void setCountryCode(String cc);
	
	/** called to set the farm name */
	public void setFarmName(String farmName);
}
