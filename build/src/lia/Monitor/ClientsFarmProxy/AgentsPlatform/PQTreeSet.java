package lia.Monitor.ClientsFarmProxy.AgentsPlatform;

import java.util.TreeSet;

public class PQTreeSet {

    public static final int MAX_ELEM_NR=10000;

    // the priority queue
    private TreeSet pq = new TreeSet();

    // the Comparable class needed for TreeSet
    private class Element implements Comparable {
    
        AgentMessage key;
        int priority;

	// constructor
        Element(AgentMessage key, int priority) {
            this.key       = key;
            this.priority  = priority;
        } // Element
	
        public int compareTo(Object object) {
            Element e = (Element) object;
            if (priority != e.priority) return priority - e.priority;
            return key.compareTo(e.key);
        } // compareTo
	
        public boolean equals(Element e) {
            if (e == null) return false;
            return (priority == e.priority && key.equals(e.key));
        } // equals
	
    } // Element class


    public boolean isEmpty() { 
	return pq.isEmpty(); 
    } // isEmpty
    
    public int size() { 
	return pq.size(); 
    } // size

    public synchronized void insert(AgentMessage key, int priority) throws InterruptedException {
    	
    	while (size()==MAX_ELEM_NR) 
    		wait();
	
        pq.add(new Element(key, priority));
        
        notify();
    } // insert

    // return minimum priority
    public int min() {
        
        Element min = (Element) pq.first();
        return min.priority;
    } // min

    // return minimum priority
    public int max() {
        Element max = (Element) pq.last();
        return max.priority;
    } // max

    // delete and return the minimum value
    public synchronized AgentMessage delMin() throws InterruptedException {
        while (size() == 0) 
	    wait();
        Element min = (Element) pq.first();
        pq.remove(min);
    	notify();

    	return min.key;
    } // delMin

    // delete and return the maximum value
    public synchronized  AgentMessage delMax() throws InterruptedException {
    	
    	while (size() == 0) 
        	wait();
        
    	Element max = (Element) pq.last();
        pq.remove(max);
    	notify();

    	return max.key;
        
    } // delMax

    

} // PQTreeSet
