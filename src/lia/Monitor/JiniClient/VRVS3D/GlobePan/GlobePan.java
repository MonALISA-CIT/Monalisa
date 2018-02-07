package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.event.ChangeEvent;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.GlobePan.GlobePanBase;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import lia.Monitor.JiniClient.VRVS3D.VrvsSerMonitor;
import lia.Monitor.monitor.ILink;
import net.jini.core.lookup.ServiceID;

@SuppressWarnings("restriction")
public class GlobePan extends GlobePanBase {
	
	public JoptPan optPan;
	public NodesGroup nodesGroup;
	public PingLinksGroup pingLinksGroup;
	public PeerLinksGroup peerLinksGroup;
	public MSTLinksGroup mstLinksGroup;

	int nodesMode = 1;
	int peersMode = 11;
	int MAX_INV = 4;
	int invisibleUpdates = MAX_INV;

	public GlobePan(){
		super();
		TimerTask ttask = new TimerTask() {
			public void run() {
		        Thread.currentThread().setName(" ( ML ) - VRVS3D - GlobePan Timer Thread");
				try{
					if(! isVisible()){
						invisibleUpdates--;
						if(invisibleUpdates>0)
							return;
						invisibleUpdates = MAX_INV;
					}
					refresh();
				}catch(Throwable t){
					t.printStackTrace();
				}
			}
		};
		BackgroundWorker.schedule(ttask, 10000, 4000);
		//System.out.println("GlobePan Finished");
	}
	
	protected void buildOptPan() {
	  optPan = new JoptPan(this);
	  optPan.setMaximumSize(new Dimension(1000, 80));

	  optPan.csMST.setColors(Color.MAGENTA, Color.MAGENTA);
	  optPan.csMST.setValues(0, 0);
	  optPan.csMST.setLabelFormat("", "");

	  optPan.csPeers.setColors(Color.RED, Color.GREEN);
	  optPan.csPeers.setLabelFormat("###.##", "%");
	  optPan.csPeers.setValues(0, 100);

	  optPan.csPing.setColors(Color.RED, Color.RED);
	  optPan.csPing.setValues(0, 0);
	  optPan.csPing.setLabelFormat("###.##", "");

	  optPan.csNodes.setColors(Color.CYAN, Color.CYAN);
	  optPan.csNodes.setValues(0, 0);

	  optPan.cbNodeOpts.setActionCommand("cbNodeOpts");
	  optPan.cbNodeOpts.addActionListener(this);
	  optPan.cbNodeOpts.setLightWeightPopupEnabled(false);

	  optPan.cbPeerOpts.setActionCommand("cbPeerOpts");
	  optPan.cbPeerOpts.addActionListener(this);
	  optPan.cbPeerOpts.setLightWeightPopupEnabled(false);

	  optPan.kbShowMST.setActionCommand("kbShowMST");
	  optPan.kbShowMST.addActionListener(this);

	  optPan.kbShowPeers.setActionCommand("kbShowPeers");
	  optPan.kbShowPeers.addActionListener(this);

	  optPan.kbShowPing.setActionCommand("kbShowPing");
	  optPan.kbShowPing.addActionListener(this);

	  toolbarsPanel.add(optPan);
	}

	protected void buildGroups() {
		super.buildGroups();
		pingLinksGroup = new PingLinksGroup();
		nodesGroup = new NodesGroup();
		peerLinksGroup = new PeerLinksGroup();
		mstLinksGroup = new MSTLinksGroup();
		//System.out.println("builtGroups");
	}
	
	protected void buildSelector() {
		selector = new Selector(canvas, nodesGroup, pingLinksGroup, peerLinksGroup);
		canvas.addMouseListener(selector);
		canvas.addMouseMotionListener(selector);
		selector.addNodeSelectionListener(this);
		selector.addLinkHighlightedListener(this);
		//System.out.println("BuiltSelector");
	}
	
	protected void addGroups() {
		spin.addChild(nodesGroup);
		if(optPan.kbShowMST.isSelected())
		  spin.addChild(mstLinksGroup);
		if(optPan.kbShowPeers.isSelected())
		  spin.addChild(peerLinksGroup);
		if(optPan.kbShowPing.isSelected())
		  spin.addChild(pingLinksGroup);

		((Selector)selector).setNodesBranch(nodesGroup);
		((Selector)selector).setPingBranch(pingLinksGroup);
		pingLinksGroup.setScale(1.01 + 0.01*scaleSlider.getValue());
		nodesGroup.setScale(1.01 + 0.01*scaleSlider.getValue());
		//System.out.println("addedGroups");
	}
	
	protected void otherActionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if(cmd.equals("cbNodeOpts")) {
		  if(nodesMode != 1 + optPan.cbNodeOpts.getSelectedIndex()) {
			nodesMode = 1 + optPan.cbNodeOpts.getSelectedIndex();
			nodesGroup.mode = nodesMode;
			nodesGroup.refresh();
			optPan.csNodes.setColors(nodesGroup.minValueColor.get(), nodesGroup.maxValueColor.get());
			optPan.csNodes.setValues(nodesGroup.minValue, nodesGroup.maxValue);
		  }
		}
		else if(cmd.equals("cbPeerOpts")) {
		  if(peersMode != 11 + optPan.cbPeerOpts.getSelectedIndex()) {
			peersMode = 11 + optPan.cbPeerOpts.getSelectedIndex();
			peerLinksGroup.mode = peersMode;
			if(peerLinksGroup.isLive())
			  peerLinksGroup.refresh();
		  }
		}
		else if(cmd.equals("kbShowMST"))
		  toggleMSTLinksGroup();
		else if(cmd.equals("kbShowPeers"))
		  togglePeerLinksGroup();
		else if(cmd.equals("kbShowPing"))
		  togglePingLinksGroup();
	}
	
	protected void otherStateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if(src == scaleSlider) {
			int v = scaleSlider.getValue();
			nodesGroup.setScale(1.01 + 0.01*v);
			peerLinksGroup.setScale(1.01 + 0.01*v);
			pingLinksGroup.setScale(1.01 + 0.01*v);
		    zoomer.resetPosSlider();
		}
	}
	
	void toggleMSTLinksGroup() {
		if (optPan.kbShowMST.isSelected()) {
			if (!mstLinksGroup.isLive()) {
				mstLinksGroup.refresh();
				spin.addChild(mstLinksGroup);
			}
		} else {
			if (mstLinksGroup.isLive()){
				mstLinksGroup.detach();
				spin.removeChild(mstLinksGroup);
			}
		}
	}

	void togglePeerLinksGroup() {
		if (optPan.kbShowPeers.isSelected()) {
			if (!peerLinksGroup.isLive()) {
				peerLinksGroup.refresh();
				spin.addChild(peerLinksGroup);
			}
		} else {
			if (peerLinksGroup.isLive()){
				peerLinksGroup.detach();
				spin.removeChild(peerLinksGroup);
			}
		}
	}

	void togglePingLinksGroup() {
		if (optPan.kbShowPing.isSelected()) {
			if (!pingLinksGroup.isLive()) {
				pingLinksGroup.refresh();
				spin.addChild(pingLinksGroup);
			}
		} else {
			if (pingLinksGroup.isLive()){
				pingLinksGroup.detach();
				spin.removeChild(pingLinksGroup);
			}
		}
	}

	public void refresh() {
		super.refresh();
		try {
			// There's no sense in refreshing if there's nothing to refresh.
			if(hnodes == null || vnodes == null)
			  return;
	
			nodesGroup.refresh();
			if(mstLinksGroup.isLive())
				mstLinksGroup.refresh();
			if(peerLinksGroup.isLive())
				peerLinksGroup.refresh();
			if(pingLinksGroup.isLive())
				pingLinksGroup.refresh();

			optPan.csNodes.setColors(nodesGroup.minValueColor.get(), nodesGroup.maxValueColor.get());
			optPan.csNodes.setValues(nodesGroup.minValue, nodesGroup.maxValue);
			optPan.csPing.setColors(pingLinksGroup.maxRTTColor.get(), pingLinksGroup.minRTTColor.get());
			optPan.csPing.setValues(pingLinksGroup.maxRTT, pingLinksGroup.minRTT);
		}catch(Exception e) {
	  		System.out.println("jc: Exception caught in GlobePan.refresh()");
	  		e.printStackTrace();
		}
	}

	public void nodeHighlighted(rcNode node) {
		if (node == null) {
			if (!status.getText().equals(" ")) {
				status.setText(" ");
				nodesGroup.hideAllNodeTooltips();
				peerLinksGroup.hideAllLinkTooltips();
				pingLinksGroup.hideAllLinkTooltips();
			}
		} else {
			String text = VrvsNodeToolTip.getToolTip(node, optPan, nodesGroup);
			if (!status.getText().equals(text)) {
				nodesGroup.hideAllNodeTooltips();
				status.setIcon(node.icon);
				status.setText(text);
				nodesGroup.setNodeTooltip(node, 
						VrvsNodeToolTip.getShortToolTip(node, optPan, nodesGroup));
				nodesGroup.showNodeTooltip(node);
			}
		}
	}

	public void linkHighlighted(Object olink) {
		ILink link = (ILink) olink;
	  if(link == null){
	  	  if(!status.getText().equals(" ")) {
			  status.setText(" ");
			  peerLinksGroup.hideAllLinkTooltips();
			  pingLinksGroup.hideAllLinkTooltips();
		  }
	  }else{
		  String text = " ";
		  String shortText = " ";
		  rcNode from = null;
		  rcNode to = null;
		  for(int i=0; i<vnodes.size(); i++){
		  	rcNode n = (rcNode) vnodes.get(i);
			boolean contains = false;
			for (Enumeration en = n.wconn.elements(); en.hasMoreElements(); ) {
				if (link.equals(en.nextElement())) { contains = true; break; }
			}
		  	if(contains){
		  		from = n;
		  		for(Enumeration en=n.wconn.keys(); en.hasMoreElements(); ){
		  			ServiceID sid = (ServiceID) en.nextElement();
		  			 if(link == (ILink) n.wconn.get(sid)){
		  			 	to = (rcNode) hnodes.get(sid);
		  			 	break;
		  			 }
		  		}
		  		if(to != null) break;
		  	}
		  }
		  if((from == null) || (to==null))
		  	  return;
		  if(link.peersQuality != null && peerLinksGroup.isLive()){
		  	  text = "Peer: " + from.UnitName + " -> " + to.UnitName
		  	  	  + " [ " + optPan.peerOpts[peersMode - 11] +" = " 
		  	  	  + optPan.csPeers.formatter.format(peerLinksGroup.getLinkValue(link)) + " % ]";
		  	  shortText = "Peer link: " + from.UnitName + " -> " + to.UnitName
		  	  	  + ": " + optPan.csPeers.formatter.format(peerLinksGroup.getLinkValue(link)) + " %";
		  }else if(link.inetQuality != null && pingLinksGroup.isLive()){
		  	  text = "ABPing: " + from.UnitName + " -> " + to.UnitName
		  	  	  + " [ RTime = " + optPan.csPing.formatter.format(link.inetQuality[0])
		  	  	  + ", RTT = " + optPan.csPing.formatter.format(link.inetQuality[1]) 
		  	  	  + "ms, Lost Packages= " + optPan.csPing.formatter.format(link.inetQuality[3] * 100.0) + " % ]";
		  	  shortText = "ABPing: " +  from.UnitName +"-> " + to.UnitName
				  + ": RTT=" + optPan.csPing.formatter.format(link.inetQuality[1])
				  + " (Lost=" + optPan.csPing.formatter.format(link.inetQuality[3] * 100.0) + " %)";
		  }
		  if(! status.getText().equals(text)){
		  	  peerLinksGroup.hideAllLinkTooltips();
			  pingLinksGroup.hideAllLinkTooltips();
			  if(link.peersQuality != null && peerLinksGroup.isLive()){
			  	peerLinksGroup.setLinkTooltip(link, shortText);
				peerLinksGroup.showLinkTooltip(link);
			  }else if(link.inetQuality != null && pingLinksGroup.isLive()){
			  	pingLinksGroup.setLinkTooltip(link, shortText);
			  	pingLinksGroup.showLinkTooltip(link);
			  }
			  status.setText(text);
		  }
	  }		
	}

	public void setNodes(Hashtable hnodes, Vector vnodes) {
		super.setNodes(hnodes, vnodes);
		nodesGroup.setNodes(vnodes);
		pingLinksGroup.setNodes(vnodes);
		peerLinksGroup.setNodes(hnodes, vnodes);
		mstLinksGroup.setNodes(hnodes, vnodes);
	}
	
	public void setSerMonitor(SerMonitorBase monitor) {
		super.setSerMonitor(monitor);
		mstLinksGroup.setSerMonitor((VrvsSerMonitor)monitor);
	}
	
}
