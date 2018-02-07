package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.DLink;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.Arc3D;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.WorldCoordinates;
import lia.Monitor.JiniClient.VRVS3D.VrvsSerMonitor;

@SuppressWarnings("restriction")
public class MSTLinksGroup extends BranchGroup {

  public static Color3f mstColor = new Color3f(1, 0, 1);

  Hashtable hnodes;
  VrvsSerMonitor monitor;
  Vector arcs;       // list of Triple(rcNode, rcNode, Arc3D)

  public MSTLinksGroup() {
    setCapability(ALLOW_DETACH);
    setCapability(ALLOW_CHILDREN_EXTEND);
    setCapability(ALLOW_CHILDREN_WRITE);
    setCapability(ALLOW_CHILDREN_READ);
    arcs = new Vector();
  }

  public void setNodes(Hashtable hnodes, Vector vnodes) {
    this.hnodes = hnodes;
  }

  public void setSerMonitor(VrvsSerMonitor monitor) {
    this.monitor = monitor;
  }

  double failsafeParseDouble(String value, double failsafe){
	  try {
		  return Double.parseDouble(value);
	  } catch ( Throwable t  ){  
		  return failsafe;
	  }
  }
  
  public void refresh() {
    if(hnodes == null || monitor == null)
      return;

    try{
        Vector mstData = monitor.getMST();
        if(mstData == null)
        	return;

        // Add links that are missing
    	for(int i=0; i<mstData.size(); i++) {
    		DLink link = (DLink) mstData.get(i);
    		rcNode n1 = (rcNode) hnodes.get(link.fromSid);
    		rcNode n2 = (rcNode) hnodes.get(link.toSid);
    		if(n1 != null && n2 != null && !hasLink(n1, n2))
    			addLink(n1, n2);
    	}

    	// Remove links that are no longer there
      	for(int i=0; i<arcs.size(); i++){
      		Triple triple = (Triple)arcs.get(i);
      		rcNode n1 = (rcNode)triple.first;
      		rcNode n2 = (rcNode)triple.second;
      		
      		boolean keep = false;
      		for(int j=0; j<mstData.size(); j++){
      			DLink link = (DLink)mstData.get(j);
      			if(link.cequals(n1.sid, n2.sid) 
      					|| link.cequals(n2.sid, n1.sid)) {
      				keep = true;
      				break;
      			}
      		}
			int nr = 0;
			for (Enumeration en = hnodes.keys(); en.hasMoreElements(); ) {
				rcNode nn = (rcNode)hnodes.get(en.nextElement());
				if (n1.equals(nn)) nr++;
				if (n2.equals(nn)) nr++;
			}
      		if(nr < 2)
      			keep = false;
      		
      		if(!keep) {
      			Arc3D arc = (Arc3D)triple.third;
      			removeChild(arc);
      			arcs.remove(i);
      		}
      	}
    }catch(ConcurrentModificationException ex){
    	// ignore
    }
  }

  public boolean hasLink(rcNode n1, rcNode n2) {
    for(int i=0; i<arcs.size(); i++){
    	Triple triple = (Triple) arcs.get(i);
    	rcNode first = (rcNode) triple.first;
    	rcNode second = (rcNode) triple.second;
    	if((first == n1 && second == n2) 
    			|| (first == n2 && second == n1))
    		return true;
    }
    return false;
  }

  public void addLink(rcNode n1, rcNode n2) {
    if(n1 == null || n2 == null || hasLink(n1, n2))
      return;

    WorldCoordinates loc1 = 
    	new WorldCoordinates(failsafeParseDouble(n1.LAT, -21.22D), 
    		failsafeParseDouble(n1.LONG, -111.15D), 1.008);
    WorldCoordinates loc2 = 
    	new WorldCoordinates(failsafeParseDouble(n2.LAT, -21.22D),
    		failsafeParseDouble(n2.LONG, -111.15D), 1.008);
    Arc3D arc = new Arc3D(loc1, loc2, mstColor);
    arcs.add(new Triple(n1, n2, arc));
    addChild(arc);
  }

  public void removeLink(rcNode n1, rcNode n2) {
    for(int i=0; i<arcs.size(); i++){
      Triple triple = (Triple)arcs.get(i);
      rcNode first = (rcNode)triple.first;
      rcNode second = (rcNode)triple.second;
      if((first == n1 && second == n2) || (first == n2 && second == n1)) {
        Arc3D arc = (Arc3D)triple.third;
        removeChild(arc);
        arcs.remove(i);
        i--;
      }
    }
  }

}
