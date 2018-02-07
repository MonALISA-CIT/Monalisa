package lia.Monitor.Agents.SchedAgents;


public interface ResourceManager {
	
	public void reserveResources (UserRequest userReq);
	public void  scheduleJob(UserRequest userReq);
	
}
