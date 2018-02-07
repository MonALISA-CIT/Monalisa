package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

/**
 * this class implements the Fruchterman-Rheingold layout algorithm
 */
public class TemplateIterativeLayoutAlgorithm extends GraphLayoutAlgorithm implements Runnable {
	private LayoutChangedListener lListener;
	private Thread runner;
	private Object syncObj = new Object();
	private Vector crtNodes = null;
	private double MIN = 5; // these 2 restrict the size of a
	private double MAX = 100; // link between two nodes
//	private final double EPS = 1e-4D; 
	
    public TemplateIterativeLayoutAlgorithm(GraphTopology gt, LayoutChangedListener lListener) {
        super(gt);
        this.lListener = lListener;
        crtNodes = new Vector();
    }

    /** all links lengths will be adjusted to this interval */
	public void setLinksSizeRange(double min, double max){
		MIN = min;
		MAX = max;
	}
    
    /** the graph has changed; update it at runtime */
	public void updateGT(GraphTopology gtNew){
		synchronized(syncObj){
			gt = gtNew;
			selectHandledNodes();
			//System.out.println("updateGT");
			
			// compute min/max
			double min=Double.MAX_VALUE, max=Double.MIN_VALUE;
			for(Iterator nit=gt.gnodes.iterator(); nit.hasNext(); ){
				GraphNode gn = (GraphNode) nit.next();
				for(Enumeration en=gn.neighbors.elements(); en.hasMoreElements(); ){
					Double d = (Double) en.nextElement();
					if(d != null){
						double v = d.doubleValue();
						min = (min > v ? v : min);
						max = (max < v ? v : max);
					}
				}
			}
			// adjust all link values to be in a certain interval
			if(max > min){
				for(Iterator nit=gt.gnodes.iterator(); nit.hasNext(); ){
					GraphNode n1 = (GraphNode) nit.next();
					for(Enumeration en=n1.neighbors.keys(); en.hasMoreElements(); ){
						GraphNode n2 = (GraphNode) en.nextElement();
						Double d = (Double) n1.neighbors.get(n2);
						if(d != null){
							double v = d.doubleValue();
							double V = (MAX - MIN) / (max - min) * (v - min) + MIN;
							n1.neighbors.put(n2, Double.valueOf(V));
						}
					}
				}
			}
			crtNodes.clear();
			crtNodes.addAll(gt.gnodes);
		}
	}

	/** from the newly added graph, select the nodes that are handled by this algorithm */
	private void selectHandledNodes(){
		handled.clear();
		for(Iterator it=gt.gnodes.iterator(); it.hasNext(); ){
			GraphNode gn = (GraphNode) it.next();
			if(gn.neighbors.size() != 0){
				if(! handled.contains(gn)){
					handled.add(gn);
				}
				for(Enumeration en=gn.neighbors.keys(); en.hasMoreElements(); ){
					GraphNode peer = (GraphNode) en.nextElement();
					if(! handled.contains(peer)){
						handled.add(peer);
					}
				}
			}
		}
	}

	/** get the length for a link or -1 if it doesn't exist */ 
	private double getConnPerf(GraphNode n1, GraphNode n2){
		double val = -1;
		double v1, v2;
		Double d = (Double)(n1.neighbors.get(n2));
		v1 = (d == null ? -1 : d.doubleValue());
		d = (Double)(n2.neighbors.get(n1));
		v2 = (d == null ? -1 : d.doubleValue());
		if(v1 < 0 && v2 < 0)
			return -1;
		else if(v1 < 0 || v2 < 0) // but not both
			val = 1 + v1 + v2;
		else // both v1 and v2 are > 0
			val = (v1 + v2) / 2;
		return val;
	}
	
	private void algorithmStep(){
	    
        // calculate repulsion forces
	    for(Iterator itv=handled.iterator(); itv.hasNext(); ){
			GraphNode v = (GraphNode) itv.next();
			for(Iterator itu=handled.iterator(); itu.hasNext(); ){
			    GraphNode u = (GraphNode) itu.next();
			    if(v != u){
			        double deltaX = v.pos.x - u.pos.x;
			        double deltaY = v.pos.y - u.pos.y;
			    }
			}
	    }
	    
	    // calculate attraction forces
	    for(Iterator itv=handled.iterator(); itv.hasNext(); ){
			GraphNode v = (GraphNode) itv.next();
			for(Iterator itu=v.neighbors.keySet().iterator(); itu.hasNext(); ){
			    GraphNode u = (GraphNode) itu.next();
			    double deltaX = v.pos.x - u.pos.x;
		        double deltaY = v.pos.y - u.pos.y;
		        double realForce = getConnPerf(v, u);
			}
	    }
	}
	
	private void assignPositions(){
		for(int i=0; i<handled.size(); i++){
			GraphNode gn = (GraphNode) handled.get(i);
			if(gn.rcnode.fixed)
				continue;
		}
	}

	private void cool() {
	}

	private void relax(){
		synchronized(syncObj){
			if(runner == null || runner != Thread.currentThread())
				return;
			
			for(int i=0; i<1; i++){
				algorithmStep();
				assignPositions();
				cool();
			}
			directLayUnhandledNodes();
			lListener.setElasticLayout();
		}
	}

	/** star computing this layout */
    public void layOut() {
		updateGT(gt);
		runner = new Thread(this, "SpringLayout");
		runner.start();
    }

    /** finish computing this layout */
    public void finish() {
		synchronized(syncObj){
			runner = null;
		}			
    }

    public void run() {
		Thread me = Thread.currentThread();
		while(runner == me){
			//long t1 = NTPDate.currentTimeMillis();
			relax();
			//long t2 = NTPDate.currentTimeMillis();
			//System.out.println("relax in "+(t2-t1)+" ms");
			try{
				Thread.sleep(200);	
			}catch(InterruptedException e){
				break;
			}
		}
    }
}
