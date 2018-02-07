
package lia.Monitor.ClientsFarmProxy.AgentsPlatform ;

import java.io.Serializable;

import lia.util.ntp.NTPDate;

 public class AMsgCache implements Serializable {
 
    private Long timeMem;
    private AgentMessage msg;
 
    public AMsgCache (AgentMessage msg) {
    	// aici tre sa iau timpul loca si sa-l setez .....
    	this.msg = msg;
    	timeMem = new Long ( NTPDate.currentTimeMillis()); 
    } // constr. AMsgCache

    public String toString () {
    	return msg.toString();
    } // toString 
    
    public AgentMessage getMsg () {
    	return this.msg;
    } // getAgentMessage
    
    public Long getTimeMem () {
    	return this.timeMem;
    } // getTimeMem
 
 } // AMsgCache
 