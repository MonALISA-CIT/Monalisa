package lia.Monitor.JiniClient.ReflRouter;

import java.io.Serializable;

import lia.Monitor.monitor.ReflRouterSI;

/**
 * This identifies a ReflRouter. This information will be registered in the LUSs.
 * The values are used to establish a master ReflRouter that will effectively
 * send the commands to the PandaProxies (Kangaroos). Other ReflRouters (slaves)
 * will just compute the MST but they won't send any command - untill the master
 * disappears from LUSs or doesn't see any reflector.
 *  
 * @author catac
 */
public class ReflRouterJiniProxy implements Serializable, ReflRouterSI {
    
	//keep the compatibility ....
	private static final long serialVersionUID = 6333215045663887330L;
    
    public String groups;		// followed group
    public String ip;			// the ip address
    
    public String toString(){
    	return "ReflRouter Service [ " + groups + " @ " + ip + " ]";
    }
}
