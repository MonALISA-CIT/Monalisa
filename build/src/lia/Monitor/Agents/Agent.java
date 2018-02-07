
package lia.Monitor.Agents;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.DataCache.AgentsCommunication;
import lia.Monitor.monitor.AgentI;
import lia.Monitor.monitor.AgentInfo;
import lia.util.ntp.NTPDate;


public abstract class Agent implements AgentI {

	public static final int JOINMCAST = 1;
	public static final int DELMCAST = 2;
	public static final int CONCATMCAST = 3;
	
	AgentInfo agentInfo; // agent information
	AgentsCommunication agentComm; // agent communication
	int messageID = 0;
	
	public Agent(String agentName, String agentGroup, String farmID) {
		agentInfo = new AgentInfo(agentName, agentGroup, farmID);
	} // TestAgent
	
	public String getAddress() { // returns agent address
		return agentInfo.agentAddr;
	} // getAddress

	public AgentInfo getAgentInfo() { // returns agent information
		return agentInfo;
	} // getAgentInfo

	public String getGroup() { // returns agent group
		return agentInfo.agentGroup;
	} // getGroup

	public String getName() { // returns agent name
		return agentInfo.agentName;
	} // getName
	
	public synchronized void sendMsg (AgentMessage am ) {
		
		if (agentComm == null) {
			System.out.println ("MSTAgent ===> agent communicator null ") ;
			return ;
		} // if
		
		agentComm.sendMsg (am);
		
	} // sendMsg
	
	public void sendMsgTo (String agentName, String agentGroup, AgentMessage am) { // sends message to a specified destination
		if (agentComm==null) {
			System.out.println ("MSTAgent ===> agent communicator null ") ;
			return ;
		} // if
		
		if (am == null) {
			System.out.println ("MSTAgent ===> agent to be transmitted null ");
			return;
		} // if
		
		am.agentAddrD = agentName ;
		am.agentGroupD = agentGroup ;
		agentComm.sendMsg (am);
	} // sendMsgTo
	
	
	public synchronized void sendCtrlMsg (AgentMessage msg, String cmd) {
		if (agentComm==null )
			return ;
		
		if (msg == null)
			return;
		
		agentComm.sendCtrlMsg(msg,cmd);
	} // sendCtrlMsg
	
/*	public void joinMcast (String mGroup) {
		AgentMessage am = createMsg (Agent.JOINMCAST, 1, 5, null,null, mGroup );
		sendCtrlMsg(am, "mcast") ;
	} // joinMcast
	
	public void leaveMcast (String mGroup) {
		AgentMessage am = createMsg (Agent.DELMCAST, 1, 5, null,null, mGroup );
		sendCtrlMsg(am, "mcast") ;
	} // leaveMcast
	
	public void concatMcast (String mGroup, String destGroup) {
		AgentMessage am = createMsg (Agent.CONCATMCAST, 1, 5,null ,null , mGroup+":"+destGroup);
		sendCtrlMsg (am, "mcast");
	} // concatMcast
	*/
	public AgentMessage createMsg(
			int messageType,
			int messageTypeAck,
			int priority,
			String agentAddrD,
			String agentGroupD,
			Object message) { // creates a message

		AgentMessage am = new AgentMessage();

		// set source address
		am.agentAddrS = agentInfo.agentAddr;
		am.agentGroupS = agentInfo.agentGroup;

		// set destination addr.
		am.agentAddrD = agentAddrD;
		am.agentGroupD = agentGroupD;

		//set types priority
		am.messageID = new Integer (messageID++);
		am.messageType = Integer.valueOf(messageType);
		am.messageTypeAck = Integer.valueOf(messageTypeAck);
		if (0 < priority && priority <= 10) {
			am.priority = Integer.valueOf(priority);
		} else { // wrong priority
			am.priority = Integer.valueOf(5);
		} // if - else

		//set message
		am.message = message;

		//set timeStamp
		am.timeStamp = Long.valueOf(NTPDate.currentTimeMillis());

		return am;

	} // basicMsg
	
	
}
