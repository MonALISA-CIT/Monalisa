package lia.Monitor.JiniClient.Farms.CienaMap.layout;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;

/**
 * this class implements the Fruchterman-Rheingold layout algorithm
 */
public class SpringLayoutAlgorithm extends lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm {
	private LayoutChangedListener lListener;
	private Thread runner;
	private Object syncObj = new Object();
	private Vector crtNodes = null;
	private double MIN = 5; // these 2 restrict the size of a
	private double MAX = 100; // link between two nodes
	private double EPS = 1e-2;
	private double rMin = 200;  // minimum distance for repulsion force
	private double stiffness = .5; // the coefficient for F = stiffness * (actualDistance - desiredDistance)
	private double springQualityFactor = 0.9; // 1 for perfect spring; actual quality varies with desiredDistance
	private double frictionFactor = 0.3; // should slow down the movement
	private double totalMovement = 0;
	
    public SpringLayoutAlgorithm(GraphTopology gt, LayoutChangedListener lListener) {
        super(gt, lListener);
        this.lListener = lListener;
        crtNodes = new Vector();
    }

    /** new stiffness in range [1..100] */
	public void setStiffness(double stiffness){
//	    this.stiffness = (1.1-0.1)/(100.0-1) * (stiffness-1) + .1;
        this.stiffness = (.6-0.1)/(100.0-1) * (stiffness-1) + .1;
	    notifyRunnerThread();
	}

    /** new repulsion force range [1..100] */
	public void setRespRange(double respRange){
	    this.rMin = (int)( (1600.0-10)/(100-1) * (respRange-1) + 10);
	    notifyRunnerThread();
	}
	
    /** the graph has changed; update it at runtime */
	public void updateGT(GraphTopology gtNew){
		synchronized(syncObj){
			gt = gtNew;
			selectHandledNodes();
			//System.out.println("updateGT");
			
			// compute min/max
	        double min = Double.POSITIVE_INFINITY;
	        double max = Double.NEGATIVE_INFINITY;
			for(Iterator nit=gt.gnodes.iterator(); nit.hasNext(); ){
				GraphNode gn = (GraphNode) nit.next();
				for(Enumeration en=gn.neighbors.elements(); en.hasMoreElements(); ){
					Double d = (Double) en.nextElement();
					if(d != null){
						final double v = d.doubleValue();
			            min = Math.min(min, v);
			            max = Math.max(max, v);
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
		setHandledFlag();
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
	
	private double repulsionForce(double dist){
	    if(dist > rMin)
	        return 0;
	    if(dist < EPS)
	        dist = EPS;
	    return rMin/dist - 1;
	}
	
	private double sign(double value){
	    return value < 0 ? -1 : (value > 0 ? 1 : 0);
	}
	
	private double springForce(double dist, double desiredDist){
	    if(desiredDist < 0)
	        return 0;
	    double zeroDelta = desiredDist * (1 - springQualityFactor);
	    double delta = dist - desiredDist;
	    if(Math.abs(delta) < zeroDelta)
	        return 0;
        double rez = stiffness * (delta + sign(- delta) * zeroDelta);
        double maxSpringForce = 50;
        return Math.max(-maxSpringForce, Math.min(maxSpringForce, rez));
	}
	
	private void algorithmStep(){
	    int n = handled.size();
	    
	    for(int i=0; i<n; i++){
	        GraphNode v = (GraphNode) handled.get(i);
	        v.force.x = 0;
	        v.force.y = 0;
	    }
	    
	    for(int i=0; i<n; i++){
	        GraphNode v = (GraphNode) handled.get(i);
	        for(int j=i+1; j<n; j++){
	            GraphNode u = (GraphNode) handled.get(j);
		        double deltaX = v.pos.x - u.pos.x;
		        double deltaY = v.pos.y - u.pos.y;
		        double dist = Math.max(EPS, v.pos.distance(u.pos));
		        double desiredDist = getConnPerf(v, u);
	            
		        double forceRez = springForce(dist, desiredDist) - repulsionForce(dist) ;//+ springForce(dist, desiredDist);
		        
		        if(deltaX == 0 && deltaY == 0){
		            deltaX = Math.random() - 0.5;
		            deltaY = Math.random() - 0.5;
		        }
		        
	            double forceX = deltaX/dist * forceRez;
	            double forceY = deltaY/dist * forceRez;
	            
		        v.force.x -= forceX;
		        v.force.y -= forceY;
		        u.force.x += forceX;
		        u.force.y += forceY;
	        }
	    }
	}
	
	private void assignPositions(){
		for(int i=0; i<handled.size(); i++){
			GraphNode gn = (GraphNode) handled.get(i);
			if(gn.rcnode.fixed)
				continue;
			
			//double forceRez = Math.max(EPS, frictionFactor * Math.sqrt(gn.force.x * gn.force.x + gn.force.y * gn.force.y));
			double dx = gn.force.x * frictionFactor;
			double dy = gn.force.y * frictionFactor; 
			dx = Math.max(-100, Math.min(100, dx));
			dy = Math.max(-100, Math.min(100, dy));
			gn.pos.x += dx;
			gn.pos.y += dy;
		}
	}

	private void cool() {
	    // empty
}

	private void relax(){
		synchronized(syncObj){
			if(runner == null || runner != Thread.currentThread())
				return;
			
			for(int i=0; i<2; i++){
				algorithmStep();
				assignPositions();
				cool();
			}
			directLayUnhandledNodes();
			totalMovement = lListener.setElasticLayout();
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
    
    /** this is used to notify the thread that something has changed and
     * it should recalculate faster the layout
     */
    public Thread getRunnerThread(){
        return runner;
    }
    
    public void notifyRunnerThread(){
        if(runner != null){
	        synchronized(runner){
	            runner.notify();
	        }
	    }
    }

    public void run() {
		Thread me = Thread.currentThread();
		while(runner == me){
//			long t1 = NTPDate.currentTimeMillis();
			relax();
//			long t2 = NTPDate.currentTimeMillis();
//			System.out.println("relax in "+(t2-t1)+" ms");
			double dCoef = (3 - totalMovement)/2; //Math.abs(totalMovement) / handled.size();
			if(dCoef < 0)
			    dCoef = 0;
			double delay = 900 * dCoef + 20;
			//System.out.println("delay1= "+delay+" totMvmt="+totalMovement+" tmvrel="+totalMovement / handled.size());
			//delay = 20;
			totalMovement = 0;
			try{
			    synchronized(me){
			        me.wait((long) delay);
			    }
				//Thread.sleep((long)delay);	
			}catch(InterruptedException e){
				break;
			}
		}
    }
    
	/** decide if we should show the unhandled nodes */
	public void setHandledFlag(){
	    for(Iterator gnit = gt.gnodes.iterator(); gnit.hasNext(); ){
	        GraphNode gn = (GraphNode) gnit.next();
	        gn.rcnode.isLayoutHandled = handled.contains(gn);
	    }
	}

}
