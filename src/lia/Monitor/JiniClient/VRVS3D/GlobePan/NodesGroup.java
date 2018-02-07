package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import java.awt.Color;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.DoubleContainer;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.Node3D;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;

/* TODO
 *   o Documentation
 */
@SuppressWarnings("restriction")
public class NodesGroup extends BranchGroup {

  public double minValue = 0;
  public double maxValue = 0;
  public Color3f minValueColor = new Color3f(0, 1, 1);
  public Color3f maxValueColor = new Color3f(0, 0, 1);

  Vector vnodes;        // list of rcnode
  Hashtable nodes3d;    // table of rcNode -> Node3D
  int mode = JoptPan.NODE_AUDIO;
  double scale = 1.0;

  public NodesGroup() {
    setCapability(ALLOW_DETACH);
    setCapability(ALLOW_CHILDREN_EXTEND);
    setCapability(ALLOW_CHILDREN_WRITE);

    nodes3d = new Hashtable();
  }

  public void setNodes(Vector vnodes) {
    this.vnodes = vnodes;
  }

  public void setScale(double scale) {
    this.scale = scale;
    for(Enumeration e = nodes3d.elements(); e.hasMoreElements(); )
    	((Node3D)e.nextElement()).setScale(scale);
  }

  public void setNodeTooltip(rcNode node, String text){
	  if (nodes3d == null)
		  return;
	  Node3D p3d = (Node3D) nodes3d.get(node);
	  if(p3d != null)
	  	  p3d.setTooltipText(text);
  }
	
  public void showNodeTooltip(rcNode node){
	  if (nodes3d == null)
		  return;
	  Node3D p3d = (Node3D) nodes3d.get(node);
	  if(p3d != null)
	  	  p3d.showTooltip();	
  }
	
  public void hideAllNodeTooltips(){
	  if (nodes3d == null)
		  return;
	  for (Enumeration e = nodes3d.elements(); e.hasMoreElements();)
	  	  ((Node3D) e.nextElement()).hideTooltip();
  }

  
  public void refresh() {
    if(vnodes == null)
      return;

    checkNodes();
    updateValueRange();
      
    for(Enumeration e = nodes3d.keys(); e.hasMoreElements(); ) {
    	rcNode n = (rcNode)e.nextElement();
        ((Node3D)nodes3d.get(n)).setColor(getNodeColor(n));
    }
  }

  void checkNodes(){
  	// check for new nodes
  	for(int i=0; i<vnodes.size(); i++){
  		rcNode node = (rcNode) vnodes.get(i);
  		if(! nodes3d.containsKey(node))
  			addNode(node);
  	}
  	// check for deleted nodes
  	for(Enumeration en=nodes3d.keys(); en.hasMoreElements(); ){
  		rcNode node = (rcNode) en.nextElement();
  		if(! vnodes.contains(node))
  			removeNode(node);
  	}
  }
  
  void addNode(rcNode n) {
  	Node3D n3d = new Node3D(n);
  	n3d.setScale(scale);
  	nodes3d.put(n, n3d);
  	addChild(n3d);
  }

  public void removeNode(rcNode n) {
  	Node3D n3d = (Node3D)nodes3d.get(n);
  	if(n3d != null) {
  		removeChild(n3d);
  		nodes3d.remove(n);
  	}
  }
  
  public double getNodeValue(rcNode node) {
    try {
      switch(mode) {
      case JoptPan.NODE_AUDIO:
      	return DoubleContainer.getHashValue(node.haux, "Audio");
//        return ((Double) node.haux.get("Audio")).doubleValue();
      case JoptPan.NODE_VIDEO:
      	return DoubleContainer.getHashValue(node.haux, "Video");
//        return ((Double) node.haux.get("Video")).doubleValue();
      case JoptPan.NODE_TRAFFIC:
          double in = -1.0, out = -1.0;
          synchronized ( node.haux ) {//do not modify the hash
               for (Enumeration en = node.haux.keys(); en.hasMoreElements();) {
                   String key = (String) en.nextElement();
                   if ( key.indexOf("_IN") != -1 ) {
                   	   double tin = DoubleContainer.getHashValue(node.haux, key);
                   	   if(tin >= 0){
                   	   		if(in < 0)
                   	   			in = tin;
                   	   		else
                   	   			in += tin;
                   	   }
                   } else if ( key.indexOf("_OUT") != -1 ) {
                   	   double tout = DoubleContainer.getHashValue(node.haux, key);
                   	   if(tout >= 0){
                           if ( out < 0)
                               out = tout;
                           else
                               out += tout;
                   	   }
                   }
               }//end for
           }//end sync
           if ( in + out < 0 ) return -1;
           return in + out;
      case JoptPan.NODE_LOAD:
      	return DoubleContainer.getHashValue(node.haux, "Load");
//        return ((Double) node.haux.get("Load")).doubleValue();
      case JoptPan.NODE_VIRTROOMS:
      	return DoubleContainer.getHashValue(node.haux, "VirtualRooms");
//        return ((Double) node.haux.get("VirtualRooms")).doubleValue();
      default:
        return -1;
      }
    }
    catch(NullPointerException e) {
      return -1;
    }
  }

  Color3f getNodeColor(rcNode node) {
  	Color c = null;
  	if(node.haux.get("ErrorKey") != null && node.haux.get("ErrorKey").equals("1") ){
//				System.out.println(n.UnitName+"-ErrorKey>"+n.haux.get("ErrorKey"));
  		c = Color.PINK;
  	}
  	if ( node.haux.get("lostConn") != null && node.haux.get("lostConn").equals("1") ){
//				System.out.println(n.UnitName+"-lostConn>"+n.haux.get("lostConn"));
  		c = Color.RED;
  	}
  	double val = getNodeValue(node);
  	Color3f color = null;
  	if(val == -1)
  		color = (c == null ? new Color3f(Color.PINK): new Color3f(c));
  	else{
  		color = new Color3f();
  		color.interpolate(maxValueColor, minValueColor, (float) ((val - maxValue)/(minValue - maxValue)));
  	}
    return color;
  }

  void updateValueRange() {
    maxValue = Double.MIN_VALUE;
    minValue = Double.MAX_VALUE;
    for(Enumeration e = nodes3d.keys(); e.hasMoreElements(); ) {
    	rcNode n = (rcNode)e.nextElement();
    	double val = getNodeValue(n);
    	if(val == -1) continue;
    	if(val > maxValue) maxValue = val;
    	if(val < minValue) minValue = val;
    }

    if(maxValue < minValue)
    	maxValue = minValue = 0;
  }

}
