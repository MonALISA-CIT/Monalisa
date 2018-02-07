
package lia.Monitor.monitor;


public class AgentInfo {

	public String agentName;
	public String agentGroup;
	public String farmID;
	public String agentAddr;
	
	public AgentInfo (String agentName, String agentGroup, String farmID) {
		
		this.agentName = agentName ;
		this.agentGroup = agentGroup;
		this.farmID = farmID;
		this.agentAddr = agentName+"@"+farmID;
		
	} // AgentInfo
	
} // class AgentInfo
