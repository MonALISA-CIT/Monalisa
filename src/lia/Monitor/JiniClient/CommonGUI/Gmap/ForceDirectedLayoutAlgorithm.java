package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.geom.Point2D;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

public class ForceDirectedLayoutAlgorithm extends GraphLayoutAlgorithm implements Runnable {
	private LayoutChangedListener lListener;
	private Thread runner;
	private Object syncObj = new Object();
	
	private double MIN = 100; // these 2 restrict the size of a
	private double MAX = 200; // link between two nodes
	
	private int respf = 3000; // should be in [1000 - 6000] 
	private double stiffness = 0.5; // should be in [0.1 - 0.9]
	private Vector crtNodes = null;
	
	public ForceDirectedLayoutAlgorithm(GraphTopology gt, LayoutChangedListener lListener) {
		super(gt);
		this.lListener = lListener;
		crtNodes = new Vector();
	}
	
	public void setLinksSizeRange(double min, double max){
		MIN = min;
		MAX = max;
	}
	
	/** respf received in [1 .. 100] */
	public void setRespF(int respf){
		this.respf =(int)( (6000.0-1000)/(100-1) * (respf-1) + 1000);
	}
	
	/** stiffness received in [1 .. 100] */
	public void setStiffness(double stiffness){
		this.stiffness = (.9-.1)/(100-1) * (stiffness-1) + 0.1;
	}

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
						if (min > v) min = v;
						if (max < v) max = v;
					}
				}
			}
			// adjust all link values to be in a certain interval
			if(max > min){
				for(Iterator nit=gt.gnodes.iterator(); nit.hasNext(); ){
					GraphNode n1 = (GraphNode) nit.next();
//					System.out.println(n1.rcnode.UnitName+" @ "+n1.pos.x+", "
//							+n1.pos.y+" =@ "+n1.rcnode.x+", "+n1.rcnode.y);
					for(Enumeration en=n1.neighbors.keys(); en.hasMoreElements(); ){
						GraphNode n2 = (GraphNode) en.nextElement();
						Double d = (Double) n1.neighbors.get(n2);
						if(d != null){
							double v = d.doubleValue();
							double V = (MAX - MIN) / (max - min) * (v - min) + MIN;
							n1.neighbors.put(n2, Double.valueOf(V));
//							System.out.println(n1.rcnode.UnitName+"->"+
//								n2.rcnode.UnitName+" v="+v+" V="+V);
						}
					}
				}
			}
			crtNodes.clear();
			crtNodes.addAll(gt.gnodes);
		}
	}

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

	private void reduceOldForces(){
		for(Iterator it=gt.gnodes.iterator(); it.hasNext(); ){
			GraphNode gn = (GraphNode) it.next();
			gn.force.x /= 2;
			gn.force.y /= 2;
		}
	}
	
	private double getConnPerf(GraphNode n1, GraphNode n2){
		double val = -1;
		double v1, v2;
		Double d = (Double)(n1.neighbors.get(n2));
		v1 = (d == null ? -1 : d.doubleValue());
		d = (Double)(n2.neighbors.get(n1));
		v2 = (d == null ? -1 : d.doubleValue());
//		v1 = lListener.getLinkValue(n1.rcnode, n2.rcnode);
//		v2 = lListener.getLinkValue(n2.rcnode, n1.rcnode);
//		if(! n1.neighbors.contains(n2))
//			v1 = -1;
//		else
//			v1 = n1.rcnode.connPerformance(n2.rcnode);
//		if(! n2.neighbors.contains(n1))
//			v2 = -1;
//		else
//			v2 = n2.rcnode.connPerformance(n1.rcnode);
		if(v1 < 0 && v2 < 0)
			return -1;
		else if(v1 < 0 || v2 < 0) // but not both
			val = 1 + v1 + v2;
		else // both v1 and v2 are > 0
			val = (v1 + v2) / 2;
		// if exists, adjust val to a reasonable length
		return val; //Math.sqrt(val * 10);
	}
	
	private void computeNewForces2(){
		for(int i=0; i<crtNodes.size(); i++){
			GraphNode n1 = (GraphNode) crtNodes.get(i);
			Point2D.Double f1 = n1.force;
			for(int j=i+1; j<crtNodes.size(); j++){
				GraphNode n2 = (GraphNode) crtNodes.get(j);
				Point2D.Double f2 = n2.force;
				double rd = n1.pos.distance(n2.pos);
				double id = getConnPerf(n1, n2);
				double fresp = respf/(rd*rd);
				double fatr = 0;
				if(id >= 0)
					fatr = Math.pow(Math.abs(rd-id), stiffness) * sign(id-rd);
				double force = fresp + fatr;
				if(Double.isNaN(force) || Double.isInfinite(force))
					continue;
				double alpha = 0;
				alpha = Math.atan2(n2.pos.y-n1.pos.y, n2.pos.x-n1.pos.x);
				if(alpha > 10 || alpha < -10)
					alpha = Math.random() * Math.PI * 2;
				
				double cosAlpha = Math.cos(alpha);
				double sinAlpha = Math.sin(alpha);
				f1.x -= force * cosAlpha;
				f1.y -= force * sinAlpha;
				f2.x += force * cosAlpha;
				f2.y += force * sinAlpha;
			}
		}
	}

//	private void computeNewForces(){
//		int i = 0;
//		for(Iterator it=gt.gnodes.iterator(); it.hasNext(); i++ ){
//			GraphNode n1 = (GraphNode) it.next();
////			if(! handled.contains(n1))
////				continue;
//			Point2D.Double f1 = (Point2D.Double) forces.get(n1);
//			Iterator jt=gt.gnodes.iterator();
//			for(int j=0; j<=i; j++)
//				jt.next();
//			for(;jt.hasNext(); ){
//				GraphNode n2 = (GraphNode) jt.next();
////				if(! handled.contains(n2))
////					continue;
//				Point2D.Double f2 = (Point2D.Double) forces.get(n2);
//				double rd = n1.pos.distance(n2.pos);
//				double id = getConnPerf(n1, n2);
//				double fresp = respf/(rd*rd);
//				double fatr = 0;
//				if(id >= 0)
//					fatr = Math.pow(Math.abs(rd-id), stiffness) * sign(id-rd);
//				double force = fresp + fatr;
//				if(Double.isNaN(force) || Double.isInfinite(force))
//					continue;
////				if(n1.rcnode.UnitName.equals("test-caltech") || n2.rcnode.UnitName.equals("test-wn1-ro")){
////					System.out.println("cnf: "+n1.rcnode.UnitName+"->"+n2.rcnode.UnitName
////						+" rd="+rd+" id="+id+" force="+force);
////				}
//				double alpha = 0;
////				if(n1.pos.equals(n2.pos))
////					alpha = Math.random() * Math.PI * 2;
////				else
//				alpha = Math.atan2(n2.pos.y-n1.pos.y, n2.pos.x-n1.pos.x);
//				if(alpha > 10 || alpha < -10)
//					alpha = Math.random() * Math.PI * 2;
//				
//				double cosAlpha = Math.cos(alpha);
//				double sinAlpha = Math.sin(alpha);
//				f1.x -= force * cosAlpha;
//				f1.y -= force * sinAlpha;
//				f2.x += force * cosAlpha;
//				f2.y += force * sinAlpha;
//			}		
//		}
////		double maxfx = 0, maxfy = 0;
////		for(Iterator it=gt.gnodes.iterator(); it.hasNext(); i++ ){
////			GraphNode n1 = (GraphNode) it.next();
////			if(! handled.contains(n1))
////				continue;
////			Point2D.Double f1 = (Point2D.Double) forces.get(n1);
////			if(Math.abs(f1.x) > maxfx)
////				maxfx = Math.abs(f1.x);
////			if(Math.abs(f1.y) > maxfy)
////				maxfy = Math.abs(f1.y);
////		}
////		System.out.println("mfx="+maxfx+" mfy="+maxfy);
//		//if(maxfx > 3 || maxfy > 3){
////			for(Iterator it=gt.gnodes.iterator(); it.hasNext(); i++ ){
////				GraphNode n1 = (GraphNode) it.next();
////				if(! handled.contains(n1))
////					continue;
////				Point2D.Double f1 = (Point2D.Double) forces.get(n1);
////				double ff = 0.5;
////				double os = sign(f1.x);
////				f1.x -= sign(f1.x) * ff;
////				if(sign(f1.x) != os)
////					f1.x = 0;
////				os = sign(f1.y);
////				f1.y -= sign(f1.y) * ff;
////				if(sign(f1.y) != os)
////					f1.y = 0;
//////				if(Math.sqrt(f1.x*f1.x + f1.y*f1.y) < 3){
//////					f1.x = 0; f1.y = 0;
//////				}
//////				f1.x *= 3/maxfx;
//////				f1.y *= 3/maxfy;
////			}
//		//}
//	}
	
	private double sign(double val){
		return (val>0 ? 1.0 : (val<0 ? -1.0: 0));
	}
	
//	private void computeForces(){
//		for(Iterator it=gt.gnodes.iterator(); it.hasNext(); ){
//			GraphNode n1 = (GraphNode) it.next();
//			Point2D.Double f1 = (Point2D.Double) forces.get(n1);
//			for(Iterator jt=gt.gnodes.iterator(); jt.hasNext();){
//				GraphNode n2 = (GraphNode) jt.next();
//				if(n1 == n2)
//					continue;
//				Point2D.Double f2 = (Point2D.Double) forces.get(n2);
//
////				double resp = 400 / (1 + n1.pos.distanceSq(n2.pos));
////				double attr = 4 / getConnPerf(n1, n2);
////				double force = (resp - attr) * 200;
//				double force = 0;
//				double actualDist = n1.pos.distance(n2.pos);
//				// repulsion force
////				force = Math.pow(50000.0 / ( 5.0 + actualDist*actualDist), 2);
//				force = Math.pow(1500.0 / ( 5.0 + actualDist), 2);
//				if(! handled.contains(n1) || ! handled.contains(n2))
//					force /= 15;
////				force = Math.pow(1000.0 / ( 5.0 + actualDist), 2);
//				double idealDist = getConnPerf(n1, n2);
//				if(idealDist >= 0){
//					force += Math.pow((idealDist - actualDist)/10, 3);
//				}
//				force /= 100;
////				if(n1.rcnode.UnitName.equals("upb") && n2.rcnode.UnitName.equals("cern"))
////					System.out.println("upb<->cern: force="+force);
//				// forces angle
//				double alpha = 0;
//				if(n1.pos.equals(n2.pos))
//					alpha = Math.random() * Math.PI * 2;
//				else
//					alpha = Math.atan2(n2.pos.y-n1.pos.y, n2.pos.x-n1.pos.x);
//				double cosAlpha = Math.cos(alpha);
//				double sinAlpha = Math.sin(alpha);
//				f1.x -= force * cosAlpha;
//				f1.y -= force * sinAlpha;
//				f2.x += force * cosAlpha;
//				f2.y += force * sinAlpha;
////				// adjustment to avoid letting nodes on the same line or column
////				if(n1.pos.x == n2.pos.x){
////					f1.y += 2*Math.random(); f2.y -= 2*Math.random();
////				}
////				if(n1.pos.y == n2.pos.y){
////					f1.x += 2*Math.random(); f2.x -= 2*Math.random();
////				}
//			}
//		}
//	}

	private void assignPositions(){
		for(int i=0; i<handled.size(); i++){
			GraphNode gn = (GraphNode) handled.get(i);
			if(gn.rcnode.fixed)
				continue;

			double maxSpeed = 4;
			gn.pos.x += gn.force.x / maxSpeed;
			gn.pos.y += gn.force.y / maxSpeed;
		}
	}

	/** relax nodes */
	private void relax(){
		synchronized(syncObj){
			if(runner == null || runner != Thread.currentThread())
				return;
			for(int i=0; i<3; i++){
				reduceOldForces();
				computeNewForces2();
				assignPositions();
			}
			directLayUnhandledNodes();
			lListener.setElasticLayout();
//			System.out.println("action = "+action);
		}
	}

	public void run() {
		Thread me = Thread.currentThread();
		while(runner == me){
//			long t1 = NTPDate.currentTimeMillis();
			relax();		
//			long t2 = NTPDate.currentTimeMillis();
//			System.out.println("relax in "+(t2-t1)+"ms");
			try{
				Thread.sleep(20);	
			}catch(InterruptedException e){
				break;
			}
		}
		//System.out.println("runner thread finished");		
	}

	public void layOut() {
//		selectHandledNodes();
//		directLayUnhandledNodes();
		updateGT(gt);
		runner = new Thread(this, "Force Directed layout");
		runner.start();
	}

	public void finish() {
		synchronized(syncObj){
			runner = null;
		}			
	}
}
