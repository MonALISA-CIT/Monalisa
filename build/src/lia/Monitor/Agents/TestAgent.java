package lia.Monitor.Agents;

import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.monitor.AgentInfo;
import lia.util.ntp.NTPDate;

public class TestAgent extends AbstractAgent {

	int rec; // for debugging
	int i = 0;
	int j = 0;
	Vector listIDs ;
	Object sync = new Object () ;
	
	Vector addresses = new Vector () ;

	public TestAgent(String agentName, String agentGroup, String farmID) {
		super (agentName, agentGroup, farmID);
		agentInfo = new AgentInfo(agentName, agentGroup, farmID);
		listIDs = new Vector ();
	} // TestAgent

	public synchronized void processErrorMsg (Object msg) {
		System.out.println ("ERROR MESSAGE  ID: "+((AgentMessage)msg).messageID);
	} // processErrorMsg	
	

	public  void doWork() {
		
		try {
			Thread.sleep (20000);
		} catch (Exception e) {}
					
			if (agentComm == null) {
				System.out.println("AgentsComm e null :((");
				
			} else {
	
/*			while (true) {
						AgentMessage amB = createCtrlMsg (j++,1, 1, 5, null, null, "TestGroup");	
						System.out.println ("\n\n\nList message source group: ====> "+amB.agentGroupS+"\n\n\n");				
						agentComm.sendCtrlMsg(amB,"list");
						try {
							Thread.sleep (2000);
						} catch (Throwable t) {
						}				
			} //while
*/			
			} // if - else 
	
			synchronized (sync) {
				try {
					sync.wait();
				} catch (Exception ex) {}
			} // synchronized
			
/*			while (true) {
				for (int k=0;k<addresses.size();k++) {
					String dest = (String)addresses.elementAt(k);
					AgentMessage am = createMsg (i,1, 1, 5, dest, null, null);
//					System.out.println (am.messageID);				
					agentComm.sendMsg(am);
					i++;
				} // for
			} // while
*/			
	} // doWork

	public String getAddress() {
		return agentInfo.agentAddr;
	} // getAddress

	public AgentInfo getAgentInfo() {
		return agentInfo;
	} // getAgentInfo

	public String getGroup() {
		return agentInfo.agentGroup;
	} // getGroup

	public String getName() {
		return agentInfo.agentName;
	} // getName

	

	public void processMsg(Object msg) {

		String agentS = ((AgentMessage)msg).agentAddrS;
		if (agentS.equals ("proxy")) {
			System.out.println ("Received proxy message ====> "+(String)(((AgentMessage)msg).message));			
			StringTokenizer st = new StringTokenizer ((String)(((AgentMessage)msg).message),":");
System.out.println ("Sending a bcast message !!!!! ---- waiting for a respomse .... ");			
			AgentMessage amB = createMsg(j++, 1, 1, 5, "bcast:" + agentInfo.agentGroup, agentInfo.agentGroup, "hahaha");
	        agentComm.sendMsg(amB);
		} else {
			System.out.println (((AgentMessage)msg).message);
			System.out.println ("\t\t"+((AgentMessage)msg).messageID+"\t"+(NTPDate.currentTimeMillis()-((AgentMessage)msg).timeStamp.longValue()));	
			System.out.println ("Sending a bcast message !!!!! ---- waiting for a respomse .... ");			
			AgentMessage amB = createMsg(j++, 1, 1, 5, "bcast:" + agentInfo.agentGroup, agentInfo.agentGroup, "hahaha");
	        agentComm.sendMsg(amB);
		} // if - else
		
	} // processMsg

	public void addNewResult(Object r) {	
	} // addNewResult

} // class TestAgent
