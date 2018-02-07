/*
 * Created on Oct 4, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

package lia.Monitor.Agents.SchedAgents;


public class DummyManager implements ResourceManager{
	public void reserveResources (UserRequest userReq) {
		System.out.println("Reserve resources for " + userReq.getID());
	}
	
	public void  scheduleJob(UserRequest userReq) {
		System.out.println("schedule job for " + userReq.getID());
	}
	
}
