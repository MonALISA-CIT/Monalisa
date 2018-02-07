package lia.Monitor.AppControlClient;

public class ModuleInformation  {

	public String moduleName ;
	public String configurationFile ;
	public int status ;
	
	public ModuleInformation (String moduleName, String configurationFile) {
		this.moduleName = moduleName ;
		this.configurationFile = configurationFile ; 
	} //ModuleInformation
	
	public ModuleInformation (String moduleName, String configurationFile, int status) {
		
			this.moduleName = moduleName ;
			this.configurationFile = configurationFile ;
			this.status = status ;
			
	} //ModuleInformation
	
	public String toString() {
		return moduleName ;
	} //toString

}
