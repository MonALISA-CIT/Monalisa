package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.DirectedArc3D;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.WorldCoordinates;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import lia.Monitor.monitor.ILink;
import net.jini.core.lookup.ServiceID;

/* TODO
 *   o Documentation
 */

@SuppressWarnings("restriction")
public class PeerLinksGroup extends BranchGroup {

  public double minQuality = 0;
  public double maxQuality = 100;
  public Color3f minQualityColor = new Color3f(1, 0, 0);
  public Color3f maxQualityColor = new Color3f(0, 1, 0);
  public int mode = JoptPan.PEERS_QUAL2H;

  Vector links;      // set of ILink
  Hashtable arcs;  	 // table of ILink -> DirectedArc3D
  Vector vnodes;     // list of rcNode
  Hashtable hnodes;	 // UnitName -> rcNode
  private double currentScale = 1.0;
  private boolean animated;

  public PeerLinksGroup() {
    setCapability(ALLOW_DETACH);
    setCapability(ALLOW_CHILDREN_EXTEND);
    setCapability(ALLOW_CHILDREN_WRITE);

    links = new Vector();
    arcs = new Hashtable();
  }

  public void setNodes(Hashtable hnodes, Vector vnodes) {
    this.hnodes = hnodes;
  	this.vnodes = vnodes;
  }

  void addPeerLink(ILink link) {
	  WorldCoordinates start =
		  new WorldCoordinates(link.fromLAT, link.fromLONG, 1.006);
	  WorldCoordinates end =
		  new WorldCoordinates(link.toLAT, link.toLONG, 1.006);
	  Color3f color = getLinkColor(link);
	  if(color == null)
	  	return;
	  DirectedArc3D arc =
		  new DirectedArc3D(start, end, color, 0.05, link);
	  arcs.put(link, arc);
	  links.add(link);
	  arc.compile();
	  addChild(arc);
	  arc.setScale(currentScale);
	  //System.out.println("peerLink "+link.name+" added.");
  }

  void removePeerLink(ILink link) {
	  DirectedArc3D arc = (DirectedArc3D) arcs.remove(link);
	  links.remove(link);
	  arc.detach();
	  removeChild(arc);
	  //System.out.println("peerLink "+link.name+" removed.");
  }
 
  public void setScale(double scale) {
	currentScale = scale;
    for(Enumeration e = arcs.elements(); e.hasMoreElements(); )
      ((DirectedArc3D) e.nextElement()).setScale(scale);
  }

  public void setLinkTooltip(ILink link, String text){
	  if (arcs == null)
		  return;
	  DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
	  if(arc != null)
		  arc.setTooltipText(text);
  }
	
  public void showLinkTooltip(ILink link){
	  if (arcs == null)
		  return;
	  DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
	  if(arc != null)
		  arc.showTooltip();
  }
	
  public void hideAllLinkTooltips(){
	  if (arcs == null)
		  return;
	  for (Enumeration e = arcs.elements(); e.hasMoreElements();)
		  ((DirectedArc3D) e.nextElement()).hideTooltip();
  }

  public void changeAnimationStatus(boolean animateWANLinks){
	  animated = animateWANLinks;
	  for (Iterator i = links.iterator(); i.hasNext();) {
		  ILink link = (ILink) i.next();
		  DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
		  arc.setAnimationStatus(animateWANLinks);
	  }
	  setScale(currentScale);
  }
  
  void checkPeerLinks() {
		for (int i = 0; i < vnodes.size(); i++) {
			rcNode ns = (rcNode) vnodes.get(i);
			// skip nodes that have the reflector down
//			String ekns = (String) ns.haux.get("ErrorKey");
//			if(ekns != null && ekns.equals("1"))
//				continue;
			// any new peer links ?
			for(Enumeration el = ns.wconn.keys(); el.hasMoreElements(); ) {
				ServiceID nwsid = (ServiceID) el.nextElement();
				ILink link = (ILink) ns.wconn.get(nwsid);
				rcNode nw = (rcNode) hnodes.get(nwsid);
				if(nw == null)
					continue;
				// skip links that connect to a reflector that is down
				if((! links.contains(link)) && (link.peersQuality != null)){
					addPeerLink(link);
				}
			}
		}
		// any removed peer links
		for(int i=0; i<links.size(); i++){
			ILink link = (ILink) links.get(i);
			boolean keep = false;
			for(int j=0; j<vnodes.size(); j++){
				rcNode ns = (rcNode) vnodes.get(j);
				boolean contains = false;
				for (Enumeration en = ns.wconn.elements(); en.hasMoreElements(); ) {
					if (link.equals(en.nextElement())) { contains = true; break; }
				}
				if(contains){
					keep = true;
					break;
				}
			}
			if(!keep){
				removePeerLink(link);
				i--;
			}
		}
	}
  
  public void refresh() {
	if(vnodes == null)
		return;
	checkPeerLinks();

	for (Enumeration e = arcs.keys(); e.hasMoreElements();){
		ILink link = (ILink) e.nextElement();
		DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
		Color3f color = getLinkColor(link);
		if(color != null)
			arc.setColor(color);
		else
			removePeerLink(link);
	}
  }

  public double getLinkValue(ILink link){
  	return link.peersQuality[mode - 11];
  }
  
  Color3f getLinkColor(ILink link) {
    try {
	  	double q = link.peersQuality[mode - 11];
	    Color3f color = new Color3f();
	    color.interpolate(minQualityColor, maxQualityColor, (float) ((q - minQuality)/(maxQuality - minQuality)));
	    return color;
    }catch(Exception e){
    	return null;
    }
  }
}
