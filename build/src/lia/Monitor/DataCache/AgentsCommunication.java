package lia.Monitor.DataCache;

public interface AgentsCommunication {

    public void  sendMsg (Object o);
    public void  sendCtrlMsg (Object o, String cmd);
	public void sendToAllMsg (Object o);
    
} // AgentsCommunication