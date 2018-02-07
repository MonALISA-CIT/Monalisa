package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import java.util.Vector;

public abstract class GraphLayoutAlgorithm {

	/** the graph that is used to compute the layout. */
    public GraphTopology gt;
    
    /** List of gnodes that are handled by the algorithm. Usually, the nodes
     *  left unconnected are not present in this vector. */
    public Vector handled;
    
    public GraphLayoutAlgorithm(GraphTopology gt) {
        this.gt = gt;
		handled = new Vector();
    }

    /** Lay out the graph, constained to the unit square. */
    public abstract void layOut();

	/** stop if in elastic mode. */
	public abstract void finish();

	/** decide if we should show the unhandled nodes */
	public void setHandledFlag(){
	    for(Iterator gnit = gt.gnodes.iterator(); gnit.hasNext(); ){
	        GraphNode gn = (GraphNode) gnit.next();
	        gn.rcnode.isLayoutHandled = handled.contains(gn);
	    }
	}
	
	/** set positions for nodes left unhandled by the algorithm. */
	protected void layUnhandledNodes(){
		// assign positions for nodes that are not connected to the main tree
//		Vector unassigned = new Vector();
//		for(Iterator it=gt.gnodes.iterator(); it.hasNext();){
//			GraphNode gn = (GraphNode) it.next();
//			if(! handled.contains(gn))
//				unassigned.add(gn);
//		}
		// put all of them in the top left corner
//		for(int i=0; i<unassigned.size(); i++){
//		GraphNode gn = (GraphNode) unassigned.get(i);
		int i = 0;
		for(Iterator it=gt.gnodes.iterator(); it.hasNext();){
			GraphNode gn = (GraphNode) it.next();
			if(handled.contains(gn)) 
				continue;
			gn.pos.y = -1.0 + 2.0 * (double) (i++) / (double) (gt.gnodes.size() - handled.size());
			gn.pos.x = -1.0; //-1.0 + 2.0 * (double) i / (double) (unassigned.size() - 1);
//			System.out.println("Unandled "+gn.rcnode.UnitName+" x="+gn.pos.x+" y="+gn.pos.y);
		}
	}

	/** set positions for unhandled nodes using direct screen coords 
	 *  This is used in elastic layouts. */
	protected void directLayUnhandledNodes(){
		int i = 0;
//		String uh = "";
		for(Iterator it=gt.gnodes.iterator(); it.hasNext();){
			GraphNode gn = (GraphNode) it.next();
			if(handled.contains(gn)) 
				continue;
			if(gn.rcnode.fixed)
				continue;
			gn.pos.y = 20 + (i%13) * 30;
			gn.pos.x = 42 + (i/13) * 100; //-1.0 + 2.0 * (double) i / (double) (unassigned.size() - 1);
			i++;
			//System.out.println("direct lay: "+gn.rcnode.UnitName+" x="+gn.pos.x+" y="+gn.pos.y);
//			uh = uh.concat(gn.rcnode.UnitName+" ");
		}
//		System.out.println("unhandled: "+uh);
	}
	
	/** we should expand the nodes to occupy more space on screen */
	protected void zoomHandledNodes(){
		 Rectangle2D.Double area = null;
		 if(handled.size() == 0)
		     return;
		 for(Iterator it=handled.iterator(); it.hasNext();){
			 GraphNode gn = (GraphNode) it.next();
			 if(area == null) 
				 area = new Rectangle2D.Double(gn.pos.x, gn.pos.y, 0, 0);
			 else
				 area.add(gn.pos.x, gn.pos.y);
		 }
//		 double zx = area.width / 1.6;
//		 double zy = area.height / 1.8;
//		 double dx = -(area.width/2.0);
//		 double dy = -(area.height/2.0);
//		 double z = Math.max(zx, zy);
//		 for(Iterator it=handled.iterator(); it.hasNext();){
//			 GraphNode gn = (GraphNode) it.next();
//			 gn.pos.x = dx + (gn.pos.x - area.x)/z;
//			 gn.pos.y = dy + (gn.pos.y - area.y)/z;
//		 }
//		 System.out.println("aX="+area.x+" aY="+area.y+" areaW="+area.width+" areaH="+area.height);
		 double zx = (area.width > 0 ? 1.8 / area.width : 1);
		 double zy = (area.height > 0 ? 2 / area.height : 1);
//		 System.out.println("area.x="+area.x+" area.y="+area.y+" area.w="+area.width+" area.h="+area.height);
		 double dx = (area.width > 0 ? -0.8 : 0.0);
		 double dy = (area.height > 0 ? -1.0 : 0.0);
//		 double zx = area.width / 1.8;
//		 double zy = area.height / 1.99;
//		 double dx = -(area.width/2.0);
//		 double dy = -(area.height/2.0);
//		 double z = Math.max(zx, zy);
		 for(Iterator it=handled.iterator(); it.hasNext();){
		 	GraphNode gn = (GraphNode) it.next();
		 	gn.pos.x = dx + (gn.pos.x - area.x)*zx;
			gn.pos.y = dy + (gn.pos.y - area.y)*zy;
//			System.out.println("HANDLED "+gn.rcnode.UnitName+" x="+gn.pos.x+" y="+gn.pos.y);
		 }
	}
}
