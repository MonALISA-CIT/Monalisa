package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Group;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.DirectedArc3D;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.WorldCoordinates;
import lia.Monitor.monitor.ILink;

/* TODO
 *   o Documentation
 *   o Colors and RTT values should not be static
 */

@SuppressWarnings("restriction")
public class PingLinksGroup extends BranchGroup {

	public double minRTT = 0;
	public double maxRTT = 1000;
	public Color3f minRTTColor = new Color3f(1, 1, 0);
	public Color3f maxRTTColor = new Color3f(1, .4f, .4f);
	public Color3f errRTTColor = new Color3f(1, 0, 0);
	private double currentScale = 1.0;
	
	Vector links; 	 // vector of ILinks
	Hashtable arcs;  // table of ILink -> DirectedArc3D
	Vector vnodes;

	public PingLinksGroup() {
		links = new Vector();
		arcs = new Hashtable();
		setCapability(ALLOW_DETACH);
		setCapability(Group.ALLOW_CHILDREN_EXTEND);
		setCapability(Group.ALLOW_CHILDREN_WRITE);
	}

	void addPingLink(ILink link) {
		WorldCoordinates start =
			new WorldCoordinates(link.fromLAT, link.fromLONG, 1.006);
		WorldCoordinates end =
			new WorldCoordinates(link.toLAT, link.toLONG, 1.006);
		DirectedArc3D arc =
			new DirectedArc3D(start, end, getLinkColor(link), 0.08, link);
		arcs.put(link, arc);
		links.add(link);
		arc.compile();
		addChild(arc);
		arc.setScale(currentScale);
		//System.out.println("pingLink "+link.name+" added.");
	}

	void removePingLink(ILink link) {
		DirectedArc3D arc = (DirectedArc3D) arcs.remove(link);
		links.remove(link);
		arc.detach();
		removeChild(arc);
		//System.out.println("pingLink "+link.name+" removed.");
	}

	public void setScale(double scale) {
		currentScale = scale;
		for (Enumeration e = arcs.elements(); e.hasMoreElements();)
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

	public void refresh() {
		if(vnodes == null)
			return;
		checkPingLinks();
		updateRTTRange();

		for (Enumeration en = links.elements(); en.hasMoreElements();) {
			ILink link = (ILink) en.nextElement();
			DirectedArc3D arc = (DirectedArc3D) arcs.get(link);
			arc.setColor(getLinkColor(link));
		}
	}

	double getLinkValue(ILink link){
		return link.inetQuality[0];
	}
	
	Color3f getLinkColor(ILink link) {
		if(link.inetQuality.length < 3)
			System.out.println("iNetLink ?? "+link.name+" "+link.inetQuality.length);
		double rtt = link.inetQuality[0];
		double lp = link.inetQuality[2];
		Color3f color = new Color3f();
		if(lp == 1.0)
			return errRTTColor;
		if (maxRTT > minRTT + 1e-5)
			color.interpolate(
				minRTTColor,
				maxRTTColor,
				(float) ((rtt - minRTT) / (maxRTT - minRTT)));
		else
			return minRTTColor;
		return color;
	}

	void updateRTTRange() {
		minRTT = Double.MAX_VALUE;
		maxRTT = Double.MIN_VALUE;

		for (Enumeration en=links.elements(); en.hasMoreElements(); ) {
			ILink link = (ILink) en.nextElement();
			if(link.inetQuality == null)
				continue;
			double rtt = link.inetQuality[0];
			double lp = link.inetQuality[2];
			if(lp == 1.0)
				continue;
			if (rtt < minRTT)
				minRTT = rtt;
			if (rtt > maxRTT)
				maxRTT = rtt;
		}
		// for automatic color adjustment
		if (minRTT >= maxRTT) {
			minRTTColor = new Color3f(1, 1, 0);
			maxRTTColor = new Color3f(1, 1, 0);
			if (minRTT == Double.MAX_VALUE) {
				minRTT = 0;
				maxRTT = 0;
				return;
			}
		} else {
			minRTTColor = new Color3f(1, 1, 0);
			maxRTTColor = new Color3f(1, .4f, .4f);
		}
	}
	
	void setNodes(Vector vnodes){
		this.vnodes = vnodes;
	}

	void checkPingLinks() {
		  for (int i = 0; i < vnodes.size(); i++) {
			  rcNode node = (rcNode) vnodes.get(i);
			  // any new peer links ?
			  for(Enumeration el = node.wconn.elements(); el.hasMoreElements(); ) {
				  ILink link = (ILink) el.nextElement();
				  if((! links.contains(link)) && (link.inetQuality != null)
				  		&& (link.inetQuality.length >= 3)){
					  addPingLink(link);
				  }
			  }
		  }
		  // any removed ping links ?
		  for(int i=0; i<links.size(); i++){
			  ILink link = (ILink) links.get(i);
			  boolean keep = false;
			  if(link.inetQuality != null){
				  for(int j=0; j<vnodes.size(); j++){
					  rcNode node = (rcNode) vnodes.get(j);
					  boolean contains = false;
					  for (Enumeration en = node.wconn.elements(); en.hasMoreElements(); ) {
						  if (link.equals(en.nextElement())) { contains = true; break; }
					  }
					  if(contains){
						  keep = true;
						  break;
					  }
				  }
			  }
			  if(!keep){
				  removePingLink(link);
				  i--;
			  }
		  }
	  }
}
