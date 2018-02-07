package lia.web.utils.gmap;

import java.awt.Rectangle;
import java.util.Hashtable;

/** Class that handles a node... */
public class Node {

	/**
	 * 
	 */
	public String name;
	/**
	 * 
	 */
	public double x;
	/**
	 * 
	 */
	public double y;
    /**
     * 
     */
    public Rectangle limits ;
	/**
	 * 
	 */
	public Hashtable<Node, Double> connQuality;
	/**
	 * 
	 */
	public Hashtable<Node, Double> connLosts;
	/**
	 * 
	 */
	public String LONG;
	/**
	 * 
	 */
	public String LAT;
    /**
     * 
     */
    public boolean fixed;
	
	/**
	 * @param w
	 * @param h
	 * @param _name
	 */
	public Node(int w, int h, String _name) {
		 x = w * Math.random();
		 y = h * Math.random();
		 this.name = _name;
		 connQuality = new Hashtable<Node, Double>();
		 connLosts = new Hashtable<Node, Double>();
		 fixed = false;
	}
	
	/**
	 * @param to
	 * @param linkQuality
	 * @param losts
	 */
	public void addLink(Node to, Double linkQuality, Double losts) {
		
		connQuality.put(to, linkQuality);
		connLosts.put(to, losts);
	}
	
	/**
	 * @param to
	 * @return perf
	 */
	public double connPerformance(Node to) {
		
		if (!connQuality.containsKey(to)) return -1;
		return (connQuality.get(to)).doubleValue();
	}
	
	/**
	 * @param to
	 * @return lost
	 */
	public double connLP(Node to) {
		
		if (!connLosts.containsKey(to)) return -1;
		return connLosts.get(to).doubleValue();
	}
	
} // end of class Node
