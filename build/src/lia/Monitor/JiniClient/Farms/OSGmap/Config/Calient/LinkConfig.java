package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * GUI for configuring the parameters regarding the GMPLS TE links. 
 */
public class LinkConfig extends JPanel implements ActionListener, ListSelectionListener {

	private static final String[] types = { "Numbered", "Unnumbered" };
	private static final String[] flts = { "True", "False" };
	
	protected JList linkList;
	
	protected JButton add;
	protected JButton delete;
	
	protected JComboBox localIP;
	protected JTextField remoteIP;
	protected JTextField localRid;
	protected JTextField remoteRid;
	protected JComboBox linkType;
	protected JComboBox adj;
	protected JComboBox wdmAdj;
	protected JTextField localIF;
	protected JTextField remoteIF;
	protected JTextField wdmRemoteIF;
	protected JComboBox fltDetect;
	protected JTextField lmpVerify;
	protected JTextField metric;
	protected JComboBox port;
	
	protected JButton modify;
	
	public Hashtable links;
	public Vector addresses;
	public Vector adjs;
	public Vector freePorts;
	public Link currentLink;
	
	private GMPLSConfig owner;

	public LinkConfig(GMPLSConfig owner) {
		
		super();
		this.setToolTipText(GMPLSHelper.linkHelp);
		this.owner = owner;
		links = new Hashtable();
		addresses = new Vector();
		adjs = new Vector();
		freePorts = new Vector();
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("TE Links"));
		DefaultListModel model = new DefaultListModel();
		linkList = new JList(model);
		linkList.setToolTipText(GMPLSHelper.linkList);
		linkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		linkList.addListSelectionListener(this);
		add(new JScrollPane(linkList));
		add(Box.createVerticalStrut(5));
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setOpaque(false);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		add = new JButton("Add link");
		add.setToolTipText(GMPLSHelper.linkAdd);
		add.addActionListener(this);
		add.setBackground(GMPLSConfig.color);
		add.setEnabled(false);
		delete = new JButton("Delete link");
		delete.setToolTipText(GMPLSHelper.linkDel);
		delete.addActionListener(this);
		delete.setBackground(GMPLSConfig.color);
		delete.setEnabled(false);
		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(add);
		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(delete);
		buttonPanel.add(Box.createHorizontalStrut(5));
		add(buttonPanel);
		add(Box.createVerticalStrut(5));
		
		JPanel params = new JPanel();
		params.setOpaque(false);
		params.setLayout(new BoxLayout(params, BoxLayout.Y_AXIS));
		params.setBorder(BorderFactory.createTitledBorder("Parameters"));
		add(params);
		add(Box.createVerticalStrut(5));
		
		JPanel lip = new JPanel();
		lip.setOpaque(false);
		lip.setLayout(new BoxLayout(lip, BoxLayout.X_AXIS));
		JLabel l = new JLabel("LocalIP: ");
		l.setToolTipText(GMPLSHelper.linkLocalIP);
		localIP = new JComboBox(new String[] { "" });
		localIP.setToolTipText(GMPLSHelper.linkLocalIP);
		localIP.setEnabled(false);
		lip.add(Box.createHorizontalStrut(5));
		lip.add(l);
		lip.add(Box.createHorizontalStrut(5));
		lip.add(localIP);
		lip.add(Box.createHorizontalStrut(5));
		params.add(lip);
		JPanel rip = new JPanel();
		rip.setOpaque(false);
		rip.setLayout(new BoxLayout(rip, BoxLayout.X_AXIS));
		l = new JLabel("RemoteIP: ");
		l.setToolTipText(GMPLSHelper.linkRemoteIP);
		remoteIP = new JTextField();
		remoteIP.setToolTipText(GMPLSHelper.linkRemoteIP);
		remoteIP.setBackground(GMPLSConfig.color);
		remoteIP.setEnabled(false);
		rip.add(Box.createHorizontalStrut(5));
		rip.add(l);
		rip.add(Box.createHorizontalStrut(5));
		rip.add(remoteIP);
		rip.add(Box.createHorizontalStrut(5));
		params.add(rip);
		JPanel lid = new JPanel();
		lid.setOpaque(false);
		lid.setLayout(new BoxLayout(lid, BoxLayout.X_AXIS));
		l = new JLabel("LocalRID: ");
		l.setToolTipText(GMPLSHelper.linkLocalRid);
		localRid = new JTextField();
		localRid.setToolTipText(GMPLSHelper.linkLocalRid);
		localRid.setBackground(GMPLSConfig.color);
		localRid.setEnabled(false);
		localRid.setEditable(false);
		lid.add(Box.createHorizontalStrut(5));
		lid.add(l);
		lid.add(Box.createHorizontalStrut(5));
		lid.add(localRid);
		lid.add(Box.createHorizontalStrut(5));
		params.add(lid);
		JPanel rid = new JPanel();
		rid.setOpaque(false);
		rid.setLayout(new BoxLayout(rid, BoxLayout.X_AXIS));
		l = new JLabel("RemoteRID: ");
		l.setToolTipText(GMPLSHelper.linkRemoteRid);
		remoteRid = new JTextField();
		remoteRid.setToolTipText(GMPLSHelper.linkRemoteRid);
		remoteRid.setBackground(GMPLSConfig.color);
		remoteRid.setEnabled(false);
		remoteRid.setEditable(false);
		rid.add(Box.createHorizontalStrut(5));
		rid.add(l);
		rid.add(Box.createHorizontalStrut(5));
		rid.add(remoteRid);
		rid.add(Box.createHorizontalStrut(5));
		params.add(rid);
		JPanel type = new JPanel();
		type.setOpaque(false);
		type.setLayout(new BoxLayout(type, BoxLayout.X_AXIS));
		l = new JLabel("LinkType: ");
		l.setToolTipText(GMPLSHelper.linkType);
		linkType = new JComboBox(types);
		linkType.setToolTipText(GMPLSHelper.linkType);
		linkType.setEnabled(false);
		type.add(Box.createHorizontalStrut(5));
		type.add(l);
		type.add(Box.createHorizontalStrut(5));
		type.add(linkType);
		type.add(Box.createHorizontalStrut(5));
		params.add(type);
		JPanel adjp = new JPanel();
		adjp.setOpaque(false);
		adjp.setLayout(new BoxLayout(adjp, BoxLayout.X_AXIS));
		l = new JLabel("Adj: ");
		l.setToolTipText(GMPLSHelper.linkAdj);
		adj = new JComboBox(new String[] { "" });
		adj.setToolTipText(GMPLSHelper.linkAdj);
		adj.setEnabled(false);
		adjp.add(Box.createHorizontalStrut(5));
		adjp.add(l);
		adjp.add(Box.createHorizontalStrut(5));
		adjp.add(adj);
		adjp.add(Box.createHorizontalStrut(5));
		params.add(adjp);
		JPanel wdmAdjPanel = new JPanel();
		wdmAdjPanel.setOpaque(false);
		wdmAdjPanel.setLayout(new BoxLayout(wdmAdjPanel, BoxLayout.X_AXIS));
		l = new JLabel("WdmAdj: ");
		l.setToolTipText(GMPLSHelper.linkWdmAdj);
		wdmAdj = new JComboBox(new String[] {""});
		wdmAdj.setToolTipText(GMPLSHelper.linkWdmAdj);
		wdmAdj.setEnabled(false);
		wdmAdjPanel.add(Box.createHorizontalStrut(5));
		wdmAdjPanel.add(l);
		wdmAdjPanel.add(Box.createHorizontalStrut(5));
		wdmAdjPanel.add(wdmAdj);
		wdmAdjPanel.add(Box.createHorizontalStrut(5));
		params.add(wdmAdjPanel);
		JPanel lif = new JPanel();
		lif.setOpaque(false);
		lif.setLayout(new BoxLayout(lif, BoxLayout.X_AXIS));
		l = new JLabel("LocalInterfaceIndex: ");
		l.setToolTipText(GMPLSHelper.linkLocalIf);
		localIF = new JTextField();
		localIF.setToolTipText(GMPLSHelper.linkLocalIf);
		localIF.setBackground(GMPLSConfig.color);
		localIF.setEnabled(false);
		localIF.setEditable(false);
		lif.add(Box.createHorizontalStrut(5));
		lif.add(l);
		lif.add(Box.createHorizontalStrut(5));
		lif.add(localIF);
		lif.add(Box.createHorizontalStrut(5));
		params.add(lif);
		JPanel rif = new JPanel();
		rif.setOpaque(false);
		rif.setLayout(new BoxLayout(rif, BoxLayout.X_AXIS));
		l = new JLabel("RemoteInterfaceIndex: ");
		l.setToolTipText(GMPLSHelper.linkRemoteIf);
		remoteIF = new JTextField();
		remoteIF.setToolTipText(GMPLSHelper.linkRemoteIf);
		remoteIF.setBackground(GMPLSConfig.color);
		remoteIF.setEnabled(false);
		rif.add(Box.createHorizontalStrut(5));
		rif.add(l);
		rif.add(Box.createHorizontalStrut(5));
		rif.add(remoteIF);
		rif.add(Box.createHorizontalStrut(5));
		params.add(rif);
		JPanel wdmp = new JPanel();
		wdmp.setOpaque(false);
		wdmp.setLayout(new BoxLayout(wdmp, BoxLayout.X_AXIS));
		l = new JLabel("WDMRemoteIF: ");
		l.setToolTipText(GMPLSHelper.linkWDMRemoteIf);
		wdmRemoteIF = new JTextField();
		wdmRemoteIF.setToolTipText(GMPLSHelper.linkWDMRemoteIf);
		wdmRemoteIF.setBackground(GMPLSConfig.color);
		wdmRemoteIF.setEnabled(false);
		wdmp.add(Box.createHorizontalStrut(5));
		wdmp.add(l);
		wdmp.add(Box.createHorizontalStrut(5));
		wdmp.add(wdmRemoteIF);
		wdmp.add(Box.createHorizontalStrut(5));
		params.add(wdmp);
		JPanel fltp = new JPanel();
		fltp.setOpaque(false);
		fltp.setLayout(new BoxLayout(fltp, BoxLayout.X_AXIS));
		l = new JLabel("FLTDetect: ");
		l.setToolTipText(GMPLSHelper.linkFltDetect);
		fltDetect = new JComboBox(flts);
		fltDetect.setToolTipText(GMPLSHelper.linkFltDetect);
		fltDetect.setEnabled(false);
		fltp.add(Box.createHorizontalStrut(5));
		fltp.add(l);
		fltp.add(Box.createHorizontalStrut(5));
		fltp.add(fltDetect);
		fltp.add(Box.createHorizontalStrut(5));
		params.add(fltp);
		JPanel metricp = new JPanel();
		metricp.setOpaque(false);
		metricp.setLayout(new BoxLayout(metricp, BoxLayout.X_AXIS));
		l = new JLabel("Metric: ");
		l.setToolTipText(GMPLSHelper.linkMetric);
		metric = new JTextField();
		metric.setToolTipText(GMPLSHelper.linkMetric);
		metric.setBackground(GMPLSConfig.color);
		metric.setEnabled(false);
		metricp.add(Box.createHorizontalStrut(5));
		metricp.add(l);
		metricp.add(Box.createHorizontalStrut(5));
		metricp.add(metric);
		metricp.add(Box.createHorizontalStrut(5));
		params.add(metricp);
		JPanel lmpp = new JPanel();
		lmpp.setOpaque(false);
		lmpp.setLayout(new BoxLayout(lmpp, BoxLayout.X_AXIS));
		l = new JLabel("LMPVerify: ");
		l.setToolTipText(GMPLSHelper.linkLMPVerify);
		lmpVerify = new JTextField();
		lmpVerify.setToolTipText(GMPLSHelper.linkLMPVerify);
		lmpVerify.setBackground(GMPLSConfig.color);
		lmpVerify.setEnabled(false);
		lmpp.add(Box.createHorizontalStrut(5));
		lmpp.add(l);
		lmpp.add(Box.createHorizontalStrut(5));
		lmpp.add(lmpVerify);
		lmpp.add(Box.createHorizontalStrut(5));
		params.add(lmpp);
		JPanel portPanel = new JPanel();
		portPanel.setOpaque(false);
		portPanel.setLayout(new BoxLayout(portPanel, BoxLayout.X_AXIS));
		l = new JLabel("Port: ");
		l.setToolTipText(GMPLSHelper.linkPort);
		port = new JComboBox(new String[] { "" });
		port.setToolTipText(GMPLSHelper.linkPort);
		port.setEnabled(false);
		portPanel.add(Box.createHorizontalStrut(5));
		portPanel.add(l);
		portPanel.add(Box.createHorizontalStrut(5));
		portPanel.add(port);
		portPanel.add(Box.createHorizontalStrut(5));
		params.add(portPanel);
		
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BorderLayout());
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.modify);
		modify.addActionListener(this);
		modify.setEnabled(false);
		p.add(modify, BorderLayout.CENTER);
		add(p);
	}
	
	public void changeIP(String oldIP, String newIP) {
		
		if (oldIP != null && oldIP.length() != 0) delAddress(oldIP);
		if (newIP != null && newIP.length() != 0) addAddress(newIP);
	}
	
	public void addAddress(String address) {
		
		if (address == null || addresses.contains(address)) return;
		addresses.add(address);
		if (adjs.size() != 0) add.setEnabled(true);
	}
	
	public void delAddress(String address) {
		
		if (address == null) return;
		addresses.remove(address);
		if (addresses.size() == 0) add.setEnabled(false);
	}
	
	public void addAdj(String name) {
		
		if (name == null || adjs.contains(name) || name.length() == 0) return;
		adjs.add(name);
		this.adj.addItem(name);
		this.wdmAdj.addItem(name);
		if (addresses.size() != 0) add.setEnabled(true);
	}
	
	public void delAdj(String name) {
		
		if (name == null || !adjs.contains(name) || name.length() == 0) return;
		adjs.remove(name);
		this.adj.removeItem(name);
		this.wdmAdj.removeItem(name);
		if (adjs.size() == 0) add.setEnabled(false);
	}
	
	private void redoFreePorts() {
		
		if (currentLink == null || currentLink.port == null || currentLink.port.length() == 0) {
			port.removeAllItems();
			if (freePorts.size() == 0) port.setEnabled(false);
			else {
				port.setEnabled(true);
				for (int i=0; i<freePorts.size(); i++) port.addItem(freePorts.get(i));
			}
		}
	}
	
	public void setFreePorts(String[] ports) {
		
		if (ports == null) return;
		freePorts.clear();
		for (int i=0; i<ports.length; i++) {
			freePorts.add(ports[i]);
		}
		redoFreePorts();
	}
	
	public void addFreePort(String port) {
		
		freePorts.add(port);
		redoFreePorts();
	}
	
	public void delFreePort(String port) {
		
		freePorts.remove(port);
		redoFreePorts();
	}
	
	public void addLink(String name, String localIP, String remoteIP, String localRid, String remoteRid, String linkType, String adj, String wdmAdj, String localIf, 
			String remoteIf, String wdmRemoteIf, String lmpVerify, boolean fltDetect, String metric, String port) {
		
		if (name == null) return;
		if (localIP == null) localIP = "";
		if (remoteIP == null) remoteIP = "";
		if (localRid == null) localRid = "";
		if (remoteRid == null) remoteRid = "";
		if (linkType == null) linkType = "";
		if (adj == null) adj = "";
		if (wdmAdj == null) wdmAdj = "";
		if (localIf == null) localIf = "";
		if (remoteIf == null) remoteIf = "";
		if (wdmRemoteIf == null) wdmRemoteIf  = "";
		if (lmpVerify == null) lmpVerify = "";
		if (metric == null) metric = "";
		if (port == null) port = "";
		Link link = null;
		if (links.containsKey(name))
			link = (Link)links.get(name);
		else {
			link = new Link(name);
			links.put(name, link);
			((DefaultListModel)linkList.getModel()).addElement(name);
			linkList.setSelectedIndex(((DefaultListModel)linkList.getModel()).getSize()-1);
		}
		link.set(localIP, remoteIP, localRid, remoteRid, linkType, adj, wdmAdj, localIf, remoteIf, wdmRemoteIf, fltDetect, metric, lmpVerify, port);
		if (port != null && port.length() != 0) freePorts.remove(port);
		modify.setEnabled(true);
		setCurrentLink(name);
	}
	
	private void deleteLink(String name) {
		
		if (!links.containsKey(name)) return;
		Link link = (Link)links.remove(name);
		((DefaultListModel)linkList.getModel()).removeElement(name);
		int remaining = ((DefaultListModel)linkList.getModel()).getSize();
		if (remaining == 0) {
			delete.setEnabled(false);
			localIP.setEnabled(false);
			remoteIP.setEnabled(false);
			remoteIP.setText("");
			localRid.setEnabled(false);
			localRid.setText("");
			remoteRid.setEnabled(false);
			remoteRid.setText("");
			linkType.setEnabled(false);
			adj.setEnabled(false);
			wdmAdj.setEnabled(false);
			localIF.setEnabled(false);
			localIF.setText("");
			remoteIF.setEnabled(false);
			remoteIF.setText("");
			wdmRemoteIF.setEnabled(false);
			wdmRemoteIF.setText("");
			fltDetect.setEnabled(false);
			lmpVerify.setEnabled(false);
			lmpVerify.setText("");
			metric.setEnabled(false);
			metric.setText("");
			modify.setEnabled(false);
			port.setEnabled(false);
			return;
		}
		if (link.port != null && link.port.length() != 0) {
			addFreePort(link.port);
		}
		linkList.setSelectedIndex(0);
		setCurrentLink((String)linkList.getSelectedValue());
	}	
	
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource().equals(add)) {
			if  (addresses.size() == 0) {
				GMPLSAdmin.showError(owner, "No IP address to use");
				return;
			}
			if (adjs.size() == 0) {
				GMPLSAdmin.showError(owner, "No adjancency declared that we can use");
				return;
			}
			LinkDialog dialog = new LinkDialog(owner, addresses, adjs);
			dialog.setLocation((int)(owner.getLocation().getX() + owner.getWidth()/2-dialog.getWidth()/2), (int)(owner.getLocation().getY()+owner.getHeight()/2-dialog.getHeight()/2));
			dialog.setVisible(true);
			if (dialog.ret == LinkDialog.OK) {
				if (links.containsKey(dialog.name)) {
					GMPLSAdmin.showError(owner, "There is already a TE link named "+dialog.name);
					return;
				}
				addLink(dialog.name, dialog.localIP, dialog.remoteIP, "", "", types[0], dialog.adj, "", "", "", "", "", false, "", "");
				owner.admin.addLink(dialog.name, dialog.localIP, dialog.remoteIP, dialog.adj);
			}
			return;
		}
		
		if (e.getSource().equals(delete)) {
			String name = (String)linkList.getSelectedValue();
			int ret = JOptionPane.showConfirmDialog(owner, "Are you sure you want to delete the TE link "+name+"?", "Delete", JOptionPane.YES_NO_OPTION);
			if (ret == JOptionPane.YES_OPTION) {
				deleteLink(name);
			}
			owner.admin.delLink(name);
			return;
		}
		
		if (e.getSource().equals(modify)) {
			String name = (String)linkList.getSelectedValue();
			String localIP = (String)this.localIP.getSelectedItem();
			String remoteIP = this.remoteIP.getText();
			String linkType = (String)this.linkType.getSelectedItem();
			String adj = (String)this.adj.getSelectedItem();
			String wdmAdj = (String)this.wdmAdj.getSelectedItem();
			String remoteIf = this.remoteIF.getText();
			String wdmRemoteIf = this.wdmRemoteIF.getText();
			String lmpVerify = this.lmpVerify.getText();
			String fltDetect = (String)this.fltDetect.getSelectedItem();
			if (fltDetect.equals("True")) fltDetect = "Y";
			else fltDetect = "N";
			String metric = this.metric.getText();
			String port = (String)this.port.getSelectedItem();
			owner.admin.changeLink(name, localIP, remoteIP, linkType, adj, wdmAdj, remoteIf, wdmRemoteIf, lmpVerify, fltDetect, metric, port);
			return;
		}
	}

	private void setCurrentLink(String name) {
		
		if (name == null || !links.containsKey(name)) return;
		currentLink = (Link)links.get(name);
		delete.setEnabled(true);
		localIP.setEnabled(true);
		remoteIP.setEnabled(true);
		localRid.setEnabled(true);
		remoteRid.setEnabled(true);
		linkType.setEnabled(true);
		adj.setEnabled(true);
		wdmAdj.setEnabled(true);
		localIF.setEnabled(true);
		remoteIF.setEnabled(true);
		wdmRemoteIF.setEnabled(true);
		fltDetect.setEnabled(true);
		lmpVerify.setEnabled(true);
		metric.setEnabled(true);
		port.setEnabled(true);
		boolean found = false;
		localIP.removeAllItems();
		for (int i=0; i<addresses.size(); i++) {
			localIP.addItem(addresses.get(i));
		}
		for (int i=0; i<localIP.getItemCount(); i++) {
			String item = (String)localIP.getItemAt(i);
			if (item.equals(currentLink.localIP)) {
				found = true;
				localIP.setSelectedIndex(i);
				break;
			}
		}
		if (!found) {
			currentLink.localIP = "";
			localIP.removeAllItems();
			localIP.addItem("");
			for (int i=0; i<addresses.size(); i++) {
				localIP.addItem(addresses.get(i));
			}
			localIP.setSelectedIndex(0);
		}
		remoteIP.setText(currentLink.remoteIP);
		localRid.setText(currentLink.localRID);
		remoteRid.setText(currentLink.remoteRID);
		for (int i=0; i<linkType.getItemCount(); i++) {
			String item = (String)linkType.getItemAt(i);
			if (item.equals(currentLink.linkType)) {
				linkType.setSelectedIndex(i);
				break;
			}
		}
		adj.removeAllItems();
		for (int i=0; i<adjs.size(); i++)
			adj.addItem(adjs.get(i));
		found = false;
		for (int i=0; i<adj.getItemCount(); i++) {
			String item = (String)adj.getItemAt(i);
			if (item.equals(currentLink.adj)) {
				found = true;
				adj.setSelectedIndex(i);
				break;
			}
		}
		if (!found) {
			currentLink.adj = "";
			adj.removeAllItems();
			adj.addItem("");
			for (int i=0; i<adjs.size(); i++)
				adj.addItem(adjs.get(i));
			adj.setSelectedIndex(0);
		}
		wdmAdj.removeAllItems();
		for (int i=0; i<adjs.size(); i++)
			wdmAdj.addItem(adjs.get(i));
		found = false;
		for (int i=0; i<wdmAdj.getItemCount(); i++) {
			String item = (String)wdmAdj.getItemAt(i);
			if (item.equals(currentLink.wdmAdj)) {
				found = true;
				wdmAdj.setSelectedIndex(i);
				break;
			}
		}
		if (!found) {
			currentLink.wdmAdj = "";
			wdmAdj.removeAllItems();
			wdmAdj.addItem("");
			for (int i=0; i<adjs.size(); i++)
				wdmAdj.addItem(adjs.get(i));
			wdmAdj.setSelectedIndex(0);
		}
		localIF.setText(currentLink.localIFIndex);
		remoteIF.setText(currentLink.remoteIF);
		wdmRemoteIF.setText(currentLink.wdmRemoteIF);
		for (int i=0; i<fltDetect.getItemCount(); i++) {
			String item = (String)fltDetect.getItemAt(i);
			if (currentLink.fltDetect && item.equals("True")) {
				fltDetect.setSelectedIndex(i);
				break;
			}
			else if (!currentLink.fltDetect && item.equals("False")) {
				fltDetect.setSelectedIndex(i);
				break;
			}
		}
		lmpVerify.setText(currentLink.lmpVerify);
		metric.setText(currentLink.metric);
		port.removeAllItems();
		if (currentLink.port == null || currentLink.port.length() == 0) {
			port.addItem("");
			for (int i=0; i<freePorts.size(); i++) 
				port.addItem(freePorts.get(i));
			port.setSelectedIndex(0);
		} else {
			port.addItem(currentLink.port);
			for (int i=0; i<freePorts.size(); i++) 
				port.addItem(freePorts.get(i));
			port.setSelectedIndex(0);
		}
	}
	
	public void valueChanged(ListSelectionEvent e) {
		
		if (e.getValueIsAdjusting()) {
			setCurrentLink((String)linkList.getSelectedValue());
		}
	}
	
} // end of class LinkConfig


