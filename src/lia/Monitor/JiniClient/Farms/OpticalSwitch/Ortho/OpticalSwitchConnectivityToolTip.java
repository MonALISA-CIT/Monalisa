package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.monPredicate;
import lia.net.topology.GenericEntity;
import lia.net.topology.opticalswitch.OpticalSwitch;

public class OpticalSwitchConnectivityToolTip extends JToolTip {

	protected OpticalConnectivityPanel connectivityPanel = null;
	protected LargeFDXOpticalPanel panelIn = null;
	protected LargeFDXOpticalPanel panelOut = null;
	protected JTabbedPane tabPane = null;
	private rcNode node = null;
	
	public Dimension opticalConnectivityPanelSize = new Dimension(300, 300);
	public Dimension opticalPanelSize = new Dimension(190, 190);
	boolean currentPanel = true;
	
	Popup popup = null;
	
	private Hashtable results = new Hashtable();
	
	public OpticalSwitchConnectivityToolTip() {
		
		super();
		connectivityPanel = new OpticalConnectivityPanel();
		tabPane = new JTabbedPane();
		panelIn = new LargeFDXOpticalPanel(this, tabPane, true);
		panelOut = new LargeFDXOpticalPanel(this, tabPane, false);
		tabPane.addTab("In", panelIn);
		tabPane.addTab("Out", panelOut);
		setLayout(new BorderLayout());
		add(tabPane, BorderLayout.CENTER);
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
	
    public String getToolTipText() {
    	return "test";
    }
	
	public void setNode(rcNode node) {
		
		hidePopup();
		panelIn.newNode();
		panelOut.newNode();
		
		this.node = node;
		if (node == null) {
			setBorder(BorderFactory.createTitledBorder(""));
			connectivityPanel.update(16, new HashMap());
			return;
		}
		
		if (node.getOpticalSwitch() != null && (node.getOpticalSwitch() instanceof OpticalSwitch)) {
//			panel.update((OpticalSwitch)node.getOpticalSwitch());
			panelIn.update((OpticalSwitch)node.getOpticalSwitch());
			panelOut.update((OpticalSwitch)node.getOpticalSwitch());
		}
		
//		if (results.containsKey(node)) {
//			OpticalTooltipResult res = (OpticalTooltipResult)results.get(node);
//			panel.setPortNames(res.getPortList());
//		}
		GenericEntity osi = node.getOpticalSwitch();
		if (osi != null) {
			updateResult(osi);
			return;
		}
//		if (node.client != null && node.client.portMap != null) {
//			updateResult(node.client.portMap);
//			return;
//		}
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
		
		if (panelIn == null || panelOut == null || connectivityPanel == null) return;
		
		if (isConnectivityPanel && currentPanel) {
//			remove(panel);
			remove(tabPane);
			add(connectivityPanel, BorderLayout.CENTER);
			currentPanel = false;
			setPreferredSize(opticalConnectivityPanelSize);
			revalidate();
			return;
		}
		if (!isConnectivityPanel && !currentPanel) {
			remove(connectivityPanel);
//			add(panel, BorderLayout.CENTER);
			add(tabPane, BorderLayout.CENTER);
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
	public void updateResult(GenericEntity ge) {
		
		if (node == null) return;
		
		if (!node.getOpticalSwitch().equals(ge))
			return;
		
		if (ge == null || !(ge instanceof OpticalSwitch)) {
			if (popup != null)
				hidePopup();
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
		if (panelIn != null)
			panelIn.update((OpticalSwitch)ge);
		if (panelOut != null)
			panelOut.update((OpticalSwitch)ge);
		if (connectivityPanel != null)
		connectivityPanel.update(16, ((OpticalSwitch)ge).getCrossConnects());
	}
	
	/**
	 * Method that updates the tooltip panel based on the port map list
	 * @param osi
	 */
//	public void updateResult(ArrayList portList) {
//		if (portList == null)
//			return;
//		if (node == null)
//			return;
//		String szNodeName = node.szOpticalSwitch_Name;
//		if ( szNodeName == null )
//		    szNodeName = node.shortName;
//		if ( szNodeName == null )
//		    szNodeName = node.UnitName;
//		if ( szNodeName == null )
//		    return;
//		setBorder(BorderFactory.createTitledBorder(szNodeName));
//		if (panel != null)
//			panel.update(portList);
//		if (connectivityPanel != null)
//		connectivityPanel.update(portList.size(), portList);
//	}
	
	public rcNode getNode() {
		
		return node;
	}
	
	public boolean hasMouse() {
		
		if (popup == null) return false; // not showing...
		if (!currentPanel) return false;
		if (tabPane.getSelectedIndex() == 0)
			return panelIn.hasMouse();
		return panelOut.hasMouse();
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
		node.setOpticalSwitchConnectivityToolTip(this);
		int width = tabPane.getWidth();
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
			node.setOpticalSwitchConnectivityToolTip(null);
//		if (panel != null && panel.popup != null) {
//			panel.popup.hide(); panel.popup = null;
//		}
		if (panelIn != null && panelIn.popup != null) {
			panelIn.popup.hide(); panelIn.popup = null;
		}
		if (panelOut != null && panelOut.popup != null) {
			panelOut.popup.hide(); panelOut.popup = null;
		}
		if (popup != null) {
			popup.hide();
			popup = null;
		}
	}
		
	
} // end of class OpticalConnectivityToolTip

