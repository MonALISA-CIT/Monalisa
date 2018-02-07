/*
 * $Id: ClientPriorMsg.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy;

/**
 * 
 * @author mickyt
 */
public class ClientPriorMsg implements Comparable<ClientPriorMsg> {

	public int level; /* priority level
						 1 - leastImpMsgs
						 2 - rtPreds
						 3 - historyPreds
						 4 - filters
						 5 - confs
					  */
	
	public Object key; /* !=null if key neccessary
						*/
	
	public Object message ; /* effective message
							*/
	public ClientPriorMsg (int level, Object key, Object message) {
		this.level = level;
		this.key = key ;
		this.message = message ;
	} // ClientPriorMsg
    
    //reverse order ... in order to be used with a PriorityQueue
    //the least important messages will be on the head of the queue
    public int compareTo(ClientPriorMsg o) {
        return o.level - level;
    }
	
} // ClientPriorMsg

