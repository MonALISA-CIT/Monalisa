package lia.Monitor.monitor;

import lia.Monitor.DataCache.AgentsCommunication;

public interface AgentI {
	
    public void init(AgentsCommunication comm);
    public void doWork();
    public String getName();
    public String getGroup();
    public String getAddress();
    public AgentInfo getAgentInfo ();
    public void processMsg(Object msg);
    public void processErrorMsg (Object msg);
	public void newProxyConnection ();
    
} // AgentI