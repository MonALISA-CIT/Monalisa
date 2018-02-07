package lia.Monitor.Agents.SchedAgents;

import java.io.Serializable;
import java.util.Hashtable;

public class ClusterOffer implements Serializable {
	
	private static final long serialVersionUID = 1699028120361910782L;

	protected Hashtable offer = new Hashtable();;
		
	public ClusterOffer(Hashtable _offer) {
		this.offer = _offer;
	}
		
	public Object getParamValue(Object key) {
		return offer.get(key);
	}
}
