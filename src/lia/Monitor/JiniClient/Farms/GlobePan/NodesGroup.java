package lia.Monitor.JiniClient.Farms.GlobePan;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.Pie3D;

/* TODO
 *   o Documentation
 */
@SuppressWarnings("restriction")
public class NodesGroup extends BranchGroup {

	public double minValue = 0;
	public double maxValue = 0;
	public Color3f minValueColor = new Color3f(1, 1, 0);
	//new Color3f(0, 1, 1);
	public Color3f maxValueColor = new Color3f(1, 1, 0);
	private double currentScale = 1.0;
	HashSet nodes;
	Hashtable nodes3d; // table of rcNode -> Node3D
	//int mode = JoptPan.NODE_AUDIO;
	String pieKey = "LoadPie";

	public NodesGroup() {
		nodes = new HashSet();
		nodes3d = new Hashtable();
		setCapability(ALLOW_DETACH);
		setCapability(Group.ALLOW_CHILDREN_EXTEND);
		setCapability(Group.ALLOW_CHILDREN_WRITE);
	}

	/*  public NodesGroup(HashSet nodes) {
	    this.nodes = nodes;
	    setCapability(ALLOW_DETACH);
	
	    build();
	  }
	
		public void setNodes(HashSet nodes){
			this.nodes = nodes;
			build();
		}
	*/
	public void addNode(rcNode n) {
		synchronized (nodes3d) {
			nodes.add(n);
			//Node3D n3d = new Node3D(n);
			Pie3D n3d = new Pie3D(n);
			nodes3d.put(n, n3d);
			addChild(n3d);
			n3d.setScale(currentScale);
			//System.out.println("Node "+n.UnitName+" added.");
		}
	}

	public void removeNode(rcNode n, Iterator nodeIter) {
		synchronized (nodes3d) {
			//Node3D n3d = (Node3D) nodes3d.remove(n);
			Pie3D n3d = (Pie3D) nodes3d.remove(n);
			n3d.hideTooltip();
			nodeIter.remove();  // remove the current element from nodes hashset
			n3d.detach();
			removeChild(n3d);
			//System.out.println("Node "+n.UnitName+" removed.");
		}
	}

	public void setScale(double scale) {
		currentScale = scale;
		if (nodes3d == null)
			return;
		synchronized (nodes3d) {
			for (Enumeration e = nodes3d.elements(); e.hasMoreElements();)
				 //((Node3D) e.nextElement()).setScale(scale);
				((Pie3D) e.nextElement()).setScale(scale);
		}
	}
	
	public void setNodeTooltip(rcNode node, String text){
		if (nodes3d == null)
			return;
		synchronized (nodes3d) {
			Pie3D p3d = (Pie3D) nodes3d.get(node);
			if(p3d != null)
				p3d.setTooltipText(text);
		}					
	}
	
	public void showNodeTooltip(rcNode node){
		if (nodes3d == null)
			return;
		synchronized (nodes3d) {
			hideAllNodeTooltips();
			Pie3D p3d = (Pie3D) nodes3d.get(node);
			if(p3d != null)
				p3d.showTooltip();
		}		
	}
	
	public void hideAllNodeTooltips(){
		if (nodes3d == null)
			return;
		synchronized (nodes3d) {
			for (Enumeration e = nodes3d.elements(); e.hasMoreElements();)
				((Pie3D) e.nextElement()).hideTooltip();
		}
	}
	
	public void refresh() {
		if (nodes3d.size() == 0)
			return;

		//updateValueRange();

		synchronized (nodes3d) {
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				rcNode n = (rcNode) i.next();
				//((Node3D) nodes3d.get(n)).setColor(getNodeColor(n));
				Pie3D p3d = ((Pie3D) nodes3d.get(n));
				// p3d.detach(); // it will detach itself, but it must be added from here
				if(! p3d.tooltipVisible())
					p3d.refresh(pieKey);
				//addChild(p3d);	 // i.e. here
			}
		}
	}

	public double getNodeValue(rcNode node) {
		double val = 0; //-1;
		/*    try {
		      switch(mode) {
		      case JoptPan.NODE_AUDIO:
		        val = ((Double) node.haux.get("Audio")).doubleValue();
		        break;
		      case JoptPan.NODE_VIDEO:
		        val = ((Double) node.haux.get("Video")).doubleValue();
		        break;
		      case JoptPan.NODE_TRAFFIC:
		          double in = -1.0, out = -1.0;
		          synchronized ( node.haux ) {//do not modify the hash
		               for (Enumeration en = node.haux.keys(); en.hasMoreElements();) {
		                   String key = (String) en.nextElement();
		                   if ( key.indexOf("_IN") != -1 ) {
		                       Double tin = (Double)node.haux.get(key);
		                       if ( tin != null )
		                           if ( in < 0) 
		                               in = tin.doubleValue();
		                           else
		                               in += tin.doubleValue();
		                   } else if ( key.indexOf("_OUT") != -1 ) {
		                       Double tout = (Double)node.haux.get(key);
		                       if ( tout != null )
		                           if ( out < 0)
		                               out = tout.doubleValue();
		                           else
		                               out += tout.doubleValue();
		                   }
		               }//end for
		           }//end sync
		           if ( in + out < 0 ) return -1;
		           return in + out;
		      case JoptPan.NODE_LOAD:
		        val = ((Double) node.haux.get("Load")).doubleValue();
		        break;
		      case JoptPan.NODE_VIRTROOMS:
		        val = ((Double) node.haux.get("VirtualRooms")).doubleValue();
		        break;
		      }
		    }
		    catch(NullPointerException e) {
		    }
		*/
		return val;
	}

	Color3f getNodeColor(rcNode node) {
		double val = getNodeValue(node);
		if (val == -1)
			return new Color3f(1, 0, 0);

		Color3f color = new Color3f();
		color.interpolate(
			maxValueColor,
			minValueColor,
			(float) ((val - maxValue) / (minValue - maxValue)));
		return color;
	}

	void updateValueRange() {
		if (nodes.size() == 0)
			return;

		maxValue = Double.MIN_VALUE;
		minValue = Double.MAX_VALUE;
		synchronized (nodes) {
			for (Iterator i = nodes.iterator(); i.hasNext();) {
				rcNode n = (rcNode) i.next();
				double val = getNodeValue(n);
				if (val == -1)
					continue;
				if (val > maxValue)
					maxValue = val;
				if (val < minValue)
					minValue = val;
			}

			if (maxValue < minValue)
				maxValue = minValue = 0;
		}
	}

}
