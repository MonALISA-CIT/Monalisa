package lia.Monitor.JiniClient.Farms;

import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import net.jini.core.lookup.ServiceID;

public class Dijkstra {


 private Vector Remaining;
 private Vector Done;
// rcNode snode ;
 Map<ServiceID, rcNode> allNodes ;
 Vector results;


public Dijkstra(Map<ServiceID, rcNode> allNodes) {
	this.allNodes = allNodes;
	Done = new Vector();
	Remaining = new Vector();

}

synchronized public Vector Compute(rcNode snode) {
//	this.snode = snode;
	rcNode u, v;

	results = new Vector();
	Done.clear();
	Remaining.clear();

	if (allNodes.size() < 2)
		return results;

	for (final rcNode node : allNodes.values()) {
		Remaining.add(node);
		node.BestEstimate = Double.MAX_VALUE;
	}

	snode.BestEstimate = 0;

	while (!Remaining.isEmpty()) {

		u = ExtractClosestNode();
		for (Enumeration e = u.conn.keys(); e.hasMoreElements();) {
			rcNode nxx = (rcNode) e.nextElement();
			if(u.connLP(nxx) == 1.00)
				continue;
			v = (rcNode) allNodes.get(nxx.sid);
			if (v != null)
				relax(u, v);
		}

		Done.add(u);

	}

	for (int l = 1; l < Done.size(); l++) {
		rcNode rn = (rcNode) Done.elementAt(l);
		if ((rn.Predecessor != null) && (rn.BestEstimate < Double.MAX_VALUE)) {
			DLink dl =new DLink(rn.Predecessor.sid,	rn.sid,	rn.BestEstimate);
			results.add(dl);
		}
	}

	return results;

}

public Map<ServiceID, rcNode> getAllNodes() {
	return allNodes;
}

private void relax(rcNode u, rcNode v) {
	double testDistance = u.BestEstimate + getPerformance(u, v);

	if (v.BestEstimate > testDistance) {
		v.BestEstimate = testDistance;
		v.Predecessor = u;
	}
}

double getPerformance(rcNode n1, rcNode n2) {
	double val = n1.connPerformance(n2);
	return val;
	//if (val <= 0)
	//	return Double.MAX_VALUE;
	//return 1000.0 / val;

}

private rcNode ExtractClosestNode() {
	int closestNode = 0;
	double dist = Double.MAX_VALUE;

	for (int i = 0; i < Remaining.size(); i++) {
		if (((rcNode) Remaining.get(i)).BestEstimate < dist) {
			dist = ((rcNode) Remaining.get(i)).BestEstimate;
			closestNode = i;
		}
	}

	return (rcNode) Remaining.remove(closestNode);
}

}  
