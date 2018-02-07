package lia.Monitor.Agents.SchedAgents;

import java.util.Hashtable;
import java.util.Random;

public class DummyPredictor implements GridPredictor{

	ResourceAgent resourceAgent;
	
	public DummyPredictor(ResourceAgent resourceAgent) {
		this.resourceAgent = resourceAgent;
	}
	
	public ClusterOffer evaluateRequest(UserRequest userReq) {
		Random rgen = new Random();
		
		double execTime = rgen.nextDouble() * 10;
		Hashtable offer = new Hashtable();
		offer.put("request_id", Integer.valueOf(userReq.getID()));
		offer.put("time", Double.valueOf(execTime));
		offer.put("load", Double.valueOf(17.0));
		offer.put("cost", Integer.valueOf(0));
		offer.put("cluster_address", resourceAgent.getAddress());
		return new ClusterOffer(offer);
	}
}
