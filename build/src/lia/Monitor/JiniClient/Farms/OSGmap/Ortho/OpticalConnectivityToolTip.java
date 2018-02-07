package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;

import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.monPredicate;

public class OpticalConnectivityToolTip extends JToolTip {

	protected OpticalConnectivityPanel connectivityPanel = null;
	protected NewFDXOpticalPanel panel = null;
	private rcNode node = null;
	
	public Dimension opticalConnectivityPanelSize = new Dimension(300, 300);
	public Dimension opticalPanelSize = new Dimension(190, 190);
	boolean currentPanel = true;
	
	Popup popup = null;
	
	private Hashtable results = new Hashtable();
	
	public OpticalConnectivityToolTip() {
		
		super();
		connectivityPanel = new OpticalConnectivityPanel();
		panel = new NewFDXOpticalPanel(this);
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);
		currentPanel = true;
		setPreferredSize(opticalPanelSize);
	}
	
	public synchronized void checkNode(rcNode node) {
		
		if (node == null) return;
		
		if (!results.containsKey(node)) {
			OpticalTooltipResult res = new OpticalTooltipResult();
			results.put(node, res);
			monPredicate p = new monPredicate(node.client.getFarmName(), "OS_Ports", "*", -60000,-1, new String[] { "Port-Power" }, null);
			node.client.addLocalClient(res, p);
		}
	}
	
	public synchronized void removeCheck(rcNode node) {
		
		if (node == null) return;
		
		if (results.containsKey(node)) {
			OpticalTooltipResult res = (OpticalTooltipResult)results.remove(node);
			node.client.deleteLocalClient(res);
		}
	}
	
	public void setNode(rcNode node) {
		
		hidePopup();
		panel.newNode();
		
		this.node = node;
		if (node == null) {
			setBorder(BorderFactory.createTitledBorder(""));
			connectivityPanel.update(16, new HashMap());
			return;
		}
		if (results.containsKey(node)) {
			OpticalTooltipResult res = (OpticalTooltipResult)results.get(node);
			panel.setPortNames(res.getPortList());
		}
		OpticalSwitchInfo osi = node.getOpticalSwitchInfo();
		if (osi != null) {
			updateResult(osi);
			return;
		}
		OSwConfig newOsi = node.getOSwConfig();
		if (newOsi != null) {
			updateResult(newOsi);
			return;
		}
		osi = OSFrontImage.getOpticalSwitchInfo(node);
		if (osi != null) {
			updateResult(osi);
			return;
		}
		newOsi = OSFrontImage.getNewOpticalSwitchInfo(node);
		if (newOsi != null) {
			updateResult(newOsi);
			return;
		}
		if (node.client != null && node.client.portMap != null) {
			updateResult(node.client.portMap);
			return;
		}
		String szNodeName = node.szOpticalSwitch_Name;
		if ( szNodeName == null )
			szNodeName = node.shortName;
		if ( szNodeName == null )
			szNodeName = node.UnitName;
		if ( szNodeName == null )
			return;
		setBorder(BorderFactory.createTitledBorder(szNodeName));
		connectivityPanel.update(16, new HashMap());
		return;
	}
	
	public void switchPanel(boolean isConnectivityPanel) {
		
		if (panel == null || connectivityPanel == null) return;
		
		if (isConnectivityPanel && currentPanel) {
			remove(panel);
			add(connectivityPanel, BorderLayout.CENTER);
			currentPanel = false;
			setPreferredSize(opticalConnectivityPanelSize);
			revalidate();
			return;
		}
		if (!isConnectivityPanel && !currentPanel) {
			remove(connectivityPanel);
			add(panel, BorderLayout.CENTER);
			currentPanel = true;
			setPreferredSize(opticalPanelSize);
			revalidate();
			return;
		}
	}

	/**
	 * Method that updates the tooltip panel based on the old type of opticalswitchinfo result
	 * @param osi
	 */
	public void updateResult(OpticalSwitchInfo osi) {
		if (osi == null) {
			return;
		}
		if ( node == null )
		    return;
		String szNodeName = node.szOpticalSwitch_Name;
		if ( szNodeName == null )
		    szNodeName = node.shortName;
		if ( szNodeName == null )
		    szNodeName = node.UnitName;
		if ( szNodeName == null )
		    return;
		setBorder(BorderFactory.createTitledBorder(szNodeName));
		if (panel != null)
			panel.update(osi);
		if (connectivityPanel != null)
		connectivityPanel.update(16, osi.crossConnects);
	}
	
	/**
	 * Method that updates the tooltip panel based on the new type of OSwConfig result
	 * @param osi
	 */
	public void updateResult(OSwConfig osi) {
		if (osi == null)
			return;
		if (node == null)
			return;
		String szNodeName = node.szOpticalSwitch_Name;
		if ( szNodeName == null )
		    szNodeName = node.shortName;
		if ( szNodeName == null )
		    szNodeName = node.UnitName;
		if ( szNodeName == null )
		    return;
		setBorder(BorderFactory.createTitledBorder(szNodeName));
		if (panel != null)
			panel.update(osi);
		if (connectivityPanel != null)
		connectivityPanel.update(16, osi.crossConnects);
	}

	/**
	 * Method that updates the tooltip panel based on the port map list
	 * @param osi
	 */
	public void updateResult(ArrayList portList) {
		if (portList == null)
			return;
		if (node == null)
			return;
		String szNodeName = node.szOpticalSwitch_Name;
		if ( szNodeName == null )
		    szNodeName = node.shortName;
		if ( szNodeName == null )
		    szNodeName = node.UnitName;
		if ( szNodeName == null )
		    return;
		setBorder(BorderFactory.createTitledBorder(szNodeName));
		if (panel != null)
			panel.update(portList);
		if (connectivityPanel != null)
		connectivityPanel.update(portList.size(), portList);
	}
	
	public rcNode getNode() {
		
		return node;
	}
	
	public boolean hasMouse() {
		
		if (popup == null) return false; // not showing...
		if (!currentPanel) return false;
		return panel.hasMouse();
	}
	
	public void reshow() {

		hidePopup();
		showPopup(lastOwner, lastX, lastY);
	}
	
	private Component lastOwner = null;
	private int lastX = 0;
	private int lastY = 0;
	
	public void showPopup(Component owner, int x, int y) {

		if ( node == null || owner == null)
		    return;
		
		this.lastOwner = owner;
		this.lastX = x;
		this.lastY = y;
		
		node.setOpticalConnectivityToolTip(this);

		int width = panel.getWidth();
		if (!currentPanel) width = (int)opticalConnectivityPanelSize.getWidth();
		
		if (popup != null) {
			popup.hide();
			popup = null;
		}
		popup = PopupFactory.getSharedInstance().getPopup(owner, this, x - width / 2, y);
		popup.show();
	}
	
	public void hidePopup() {
		
		if (node != null)
			node.setOpticalConnectivityToolTip(null);

		if (popup != null) {
			popup.hide();
			popup = null;
		}
	}
		
	
} // end of class OpticalConnectivityToolTip

