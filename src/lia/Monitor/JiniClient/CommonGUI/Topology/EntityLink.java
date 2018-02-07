package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.awt.Color;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/**
 * Connection betweek two rcNode entities 
 */
public class EntityLink {
	public static int FIELD_DELAY = 1;
	public static int FIELD_AV_BW = 2;
	public static int FIELD_L_CAP = 4;
	
	public rcNode n1;		// source node
	public rcNode n2;		// destination node
	public int usageCount;	// how many times is this link used
	public int fields;		// map with filled fields

	public double delay;	// delay on this link
	public double avBw;		// available Bandwidth
	public double lCap;		// link capacity
	
	public Color color;		// what color to use to draw this link
	public boolean hotLink;	// is this link highlighted
	public boolean inDijkstra; // in Dijkstra-computed tree
	
	public EntityLink(){
		// empty
	}
	
	/** copy constructor */
	public EntityLink(EntityLink other){
		n1 = other.n1;
		n2 = other.n2;
		fields = other.fields;
		usageCount = other.usageCount;
		delay = other.delay;
		avBw = other.avBw;
		lCap = other.lCap;
		color = other.color;
		hotLink = other.hotLink;
		inDijkstra = other.inDijkstra;
	}
	
	public EntityLink(rcNode n1, rcNode n2){
		this.n1 = n1;
		this.n2 = n2;
		this.usageCount = 0;
	}
	
	public String toString(){
		return n1.UnitName+"->"+n2.UnitName;
	}
}
