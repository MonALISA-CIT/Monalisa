package lia.Monitor.ClientsFarmProxy.AgentsPlatform;

import java.util.Date;
import java.util.StringTokenizer;

public class AgentMessage implements java.io.Serializable {
    
    private static final long serialVersionUID = -3260432584012082139L;

    public Integer messageID; // message ID ???? asta nu stiu daca mai trebuie, dar .... ; 
			      // poate pt. fragmentare .... 
    
	public Long timeStamp;

    public Integer messageType; // eveniment // !!! aici tre' sa vad cum sa tin minte evenimentele, 
					     // dupa ce adica
						// de stare
						// comunicatie intre agenti
					// eroare - la un mesaj trimis anterior				
				
    public Integer priority; // message priority
    
    public String agentAddrS; // source agent address
    
    public String agentAddrD; // destination agent address 
				    // - unicast
				    // - multicast
				    // - broadcast
				    
    public String agentGroupS; // grup agent sursa
    
    public String agentGroupD; // grup agent destinatie				    
				    
    public Integer messageTypeAck ; 
    
    public Object message ; // - serializable Object;				     				
        
    
    public AgentMessage () {
    } // AgentMessage constructor

    public AgentMessage (String adrNameS) {
    	agentAddrS = adrNameS;
    } // AgentMessage constructor

    public int compareTo (Object object) {
    
		AgentMessage e = (AgentMessage) object;
		
		if  (agentAddrS!=null && e.agentAddrS!=null && agentAddrS.compareTo (e.agentAddrS)!=0) {
		    return agentAddrS.compareTo (e.agentAddrS);
		}
		
		if (timeStamp!=null && e.timeStamp!=null && timeStamp.compareTo(e.timeStamp)!=0) {
			return timeStamp.compareTo(e.timeStamp);
		} // if
		
		if (messageID!=null && e.messageID!=null && messageID.compareTo (e.messageID)!=0) {
		    return messageID.compareTo (e.messageID);
		} // if
		
		if (agentAddrD!=null && e.agentAddrD!=null && agentAddrD.compareTo (e.agentAddrD)!=0) {
		    return agentAddrS.compareTo (e.agentAddrS);
		} // if
				
		try {
		    if (message!=null && e.message!=null && ((Comparable)message).compareTo(e.message)!=0) {
			return ((Comparable)message).compareTo(e.message);
		    }
		} catch (Throwable t) {}
		
		return 0;
    } // compareTo
    
    public String getAgentDName () {
    	
        if (agentAddrD !=null) {
    		StringTokenizer st = new StringTokenizer (agentAddrD,"@");
    		return st.nextToken();
    	}
    	
   		return null;
    } // getAgentDName
    
    public String toString () {	
        StringBuilder sb = new StringBuilder(1024);
        sb.append(messageID).append(": ").append(messageType).append(" [ ");
        sb.append(agentAddrS).append("/").append(agentGroupS).append(" --> ");
        sb.append(agentAddrD).append("/").append(agentGroupD).append(" ] ");
        sb.append(" Priority: ").append(priority).append(" Time: ").append(new Date(timeStamp.longValue()));
        sb.append(" [ ").append(timeStamp).append(" ] ");
    	return sb.toString();
    } // toString

} // AgentMessage class
