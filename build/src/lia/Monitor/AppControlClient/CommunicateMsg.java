package lia.Monitor.AppControlClient;

public interface CommunicateMsg{

	public void sendCommand (String command) ;
	
	public String receiveResponseLine () throws Exception ;

}
