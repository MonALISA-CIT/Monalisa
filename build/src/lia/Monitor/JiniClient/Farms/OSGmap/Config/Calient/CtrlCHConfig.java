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
 * Handles the GUI for configuring the ctrl channels (GMPLS).
 */
public class CtrlCHConfig extends JPanel implements ActionListener, ListSelectionListener {

	protected JList ctrlChList;
	
	protected JButton add;
	protected JButton delete;
	
	protected JTextField remoteIP;
	protected JTextField remoteRid;
	protected JComboBox port;
	protected JComboBox adj;
	protected JTextField helloInvl;
	protected JTextField helloInvlMin;
	protected JTextField helloInvlMax;
	protected JTextField deadInvl;
	protected JTextField deadInvlMin;
	protected JTextField deadInvlMax;
	
	protected JButton modify;
	
	public Hashtable ctrlch;
	public Vector npPorts;
	public Vector adjs;
	
	private GMPLSConfig owner;
	
	public CtrlCHConfig(GMPLSConfig owner) {
		
		super();
		this.setToolTipText(GMPLSHelper.ctrlHelp);
		this.owner = owner;
		ctrlch = new Hashtable();
		npPorts = new Vector();
		adjs = new Vector();
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Ctrl Channels"));
		DefaultListModel model = new DefaultListModel();
		ctrlChList = new JList(model);
		ctrlChList.setToolTipText(GMPLSHelper.ctrlList);
		ctrlChList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ctrlChList.addListSelectionListener(this);
		add(new JScrollPane(ctrlChList));
		add(Box.createVerticalStrut(5));
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setOpaque(false);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		add = new JButton("Add ctrlch");
		add.setToolTipText(GMPLSHelper.ctrlAdd);
		add.addActionListener(this);
		add.setBackground(GMPLSConfig.color);
		add.setEnabled(false);
		delete = new JButton("Delete ctrlch");
		delete.setToolTipText(GMPLSHelper.ctrlDel);
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
		
		JPanel rip = new JPanel();
		rip.setOpaque(false);
		rip.setLayout(new BoxLayout(rip, BoxLayout.X_AXIS));
		JLabel l = new JLabel("RemoteIP: ");
		l.setToolTipText(GMPLSHelper.ctrlRemoteIP);
		remoteIP = new JTextField();
		remoteIP.setBackground(GMPLSConfig.color);
		remoteIP.setEnabled(false);
		remoteIP.setToolTipText(GMPLSHelper.ctrlRemoteIP);
		rip.add(Box.createHorizontalStrut(5));
		rip.add(l);
		rip.add(Box.createHorizontalStrut(5));
		rip.add(remoteIP);
		rip.add(Box.createHorizontalStrut(5));
		params.add(rip);
		JPanel rid = new JPanel();
		rid.setOpaque(false);
		rid.setLayout(new BoxLayout(rid, BoxLayout.X_AXIS));
		l = new JLabel("RemoteRID: ");
		l.setToolTipText(GMPLSHelper.ctrlRemoteRid);
		remoteRid = new JTextField();
		remoteRid.setBackground(GMPLSConfig.color);
		remoteRid.setEnabled(false);
		remoteRid.setToolTipText(GMPLSHelper.ctrlRemoteRid);
		rid.add(Box.createHorizontalStrut(5));
		rid.add(l);
		rid.add(Box.createHorizontalStrut(5));
		rid.add(remoteRid);
		rid.add(Box.createHorizontalStrut(5));
		params.add(rid);
		JPanel eqptPanel = new JPanel();
		eqptPanel.setOpaque(false);
		eqptPanel.setLayout(new BoxLayout(eqptPanel, BoxLayout.X_AXIS));
		l = new JLabel("Eqpt ID: ");
		l.setToolTipText(GMPLSHelper.ctrlEqpt);
		port = new JComboBox();
		port.setEnabled(false);
		port.setToolTipText(GMPLSHelper.ctrlEqpt);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(l);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(port);
		eqptPanel.add(Box.createHorizontalStrut(5));
		params.add(eqptPanel);
		JPanel adjPanel = new JPanel();
		adjPanel.setOpaque(false);
		adjPanel.setLayout(new BoxLayout(adjPanel, BoxLayout.X_AXIS));
		l = new JLabel("Adj: ");
		l.setToolTipText(GMPLSHelper.ctrlAdj);
		adj = new JComboBox(new String[] { "" });
		adj.setToolTipText(GMPLSHelper.ctrlAdj);
		adj.setEnabled(false);
		adjPanel.add(Box.createHorizontalStrut(5));
		adjPanel.add(l);
		adjPanel.add(Box.createHorizontalStrut(5));
		adjPanel.add(adj);
		adjPanel.add(Box.createHorizontalStrut(5));
		params.add(adjPanel);
		JPanel hPanel = new JPanel();
		hPanel.setOpaque(false);
		hPanel.setLayout(new BoxLayout(hPanel, BoxLayout.X_AXIS));
		l = new JLabel("HelloInvl (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlHello);
		helloInvl = new JTextField();
		helloInvl.setToolTipText(GMPLSHelper.ctrlHello);
		helloInvl.setEnabled(false);
		helloInvl.setBackground(GMPLSConfig.color);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(l);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(helloInvl);
		hPanel.add(Box.createHorizontalStrut(5));
		params.add(hPanel);
		hPanel = new JPanel();
		hPanel.setOpaque(false);
		hPanel.setLayout(new BoxLayout(hPanel, BoxLayout.X_AXIS));
		l = new JLabel("HelloInvlMin (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlHelloMin);
		helloInvlMin = new JTextField();
		helloInvlMin.setToolTipText(GMPLSHelper.ctrlHelloMin);
		helloInvlMin.setEnabled(false);
		helloInvlMin.setBackground(GMPLSConfig.color);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(l);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(helloInvlMin);
		hPanel.add(Box.createHorizontalStrut(5));
		params.add(hPanel);
		hPanel = new JPanel();
		hPanel.setOpaque(false);
		hPanel.setLayout(new BoxLayout(hPanel, BoxLayout.X_AXIS));
		l = new JLabel("HelloInvlMax (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlHelloMax);
		helloInvlMax = new JTextField();
		helloInvlMax.setToolTipText(GMPLSHelper.ctrlHelloMax);
		helloInvlMax.setEnabled(false);
		helloInvlMax.setBackground(GMPLSConfig.color);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(l);
		hPanel.add(Box.createHorizontalStrut(5));
		hPanel.add(helloInvlMax);
		hPanel.add(Box.createHorizontalStrut(5));
		params.add(hPanel);
		JPanel dPanel = new JPanel();
		dPanel.setOpaque(false);
		dPanel.setLayout(new BoxLayout(dPanel, BoxLayout.X_AXIS));
		l = new JLabel("DeadInvl (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlDeadInvl);
		deadInvl = new JTextField();
		deadInvl.setToolTipText(GMPLSHelper.ctrlDeadInvl);
		deadInvl.setEnabled(false);
		deadInvl.setBackground(GMPLSConfig.color);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(l);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(deadInvl);
		dPanel.add(Box.createHorizontalStrut(5));
		params.add(dPanel);
		dPanel = new JPanel();
		dPanel.setOpaque(false);
		dPanel.setLayout(new BoxLayout(dPanel, BoxLayout.X_AXIS));
		l = new JLabel("DeadInvlMin (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlDeadInvlMin);
		deadInvlMin = new JTextField();
		deadInvlMin.setToolTipText(GMPLSHelper.ctrlDeadInvlMin);
		deadInvlMin.setEnabled(false);
		deadInvlMin.setBackground(GMPLSConfig.color);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(l);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(deadInvlMin);
		dPanel.add(Box.createHorizontalStrut(5));
		params.add(dPanel);
		dPanel = new JPanel();
		dPanel.setOpaque(false);
		dPanel.setLayout(new BoxLayout(dPanel, BoxLayout.X_AXIS));
		l = new JLabel("DeadInvlMax (ms): ");
		l.setToolTipText(GMPLSHelper.ctrlDeadInvlMax);
		deadInvlMax = new JTextField();
		deadInvlMax.setToolTipText(GMPLSHelper.ctrlDeadInvlMax);
		deadInvlMax.setEnabled(false);
		deadInvlMax.setBackground(GMPLSConfig.color);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(l);
		dPanel.add(Box.createHorizontalStrut(5));
		dPanel.add(deadInvlMax);
		dPanel.add(Box.createHorizontalStrut(5));
		params.add(dPanel);
		
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
	
	public void addNPPort(String port) {
		
		if (npPorts.contains(port)) return;
		add.setEnabled(true);
		npPorts.add(port);
		this.port.addItem(port);
	}
	
	public void addAdj(String name) {
		
		if (name == null || adjs.contains(name) || name.length() == 0) return;
		adjs.add(name);
		this.adj.addItem(name);
	}
	
	public void delAdj(String name) {
		
		if (name == null || !adjs.contains(name) || name.length() == 0) return;
		adjs.remove(name);
		this.adj.removeItem(name);
	}
	
	public void addCtrlCh(String name, String localIP, String remoteIP, String localRid, String remoteRid, String port, String adj, String helloInvl,
			String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax) {
		
		if (name == null) return;
		if (localIP == null) localIP = "";
		if (remoteIP == null) remoteIP = "";
		if (localRid == null) localRid = "";
		if (remoteRid == null) remoteRid = "";
		if (port == null) port = "";
		if (adj == null) adj = "";
		if (helloInvl == null) helloInvl = "";
		if (helloInvlMin == null) helloInvlMin = "";
		if (helloInvlMax == null) helloInvlMax = "";
		if (deadInvl == null) deadInvl = "";
		if (deadInvlMin == null) deadInvlMin = "";
		if (deadInvlMax == null) deadInvlMax = "";
		CtrlCH ch = null;
		if (ctrlch.containsKey(name)) 
			ch = (CtrlCH)ctrlch.get(name);
		else {
			ch = new CtrlCH(name, this);
			ctrlch.put(name, ch);
			owner.adj.addCtrlCh(name);
			((DefaultListModel)ctrlChList.getModel()).addElement(name);
			ctrlChList.setSelectedIndex(((DefaultListModel)ctrlChList.getModel()).getSize()-1);
			setCurrentCtrlCh(name);
		}
		ch.set(localIP, remoteIP, localRid, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax, deadInvl, deadInvlMin, deadInvlMax);
		addNPPort(port);
		modify.setEnabled(true);
		addAdj(adj);
	}
	
	private void deleteCtrlCh(String name) {
		
		if (!ctrlch.containsKey(name)) return;
		ctrlch.remove(name);
		owner.adj.delCtrlCh(name);
		((DefaultListModel)ctrlChList.getModel()).removeElement(name);
		int remaining = ((DefaultListModel)ctrlChList.getModel()).getSize();
		if (remaining == 0) {
			delete.setEnabled(false);
			remoteIP.setEnabled(false);
			remoteIP.setText("");
			remoteRid.setEnabled(false);
			remoteRid.setText("");
			port.setEnabled(false);
			adj.setEnabled(false);
			adj.setSelectedIndex(0);
			helloInvl.setEnabled(false);
			helloInvl.setText("");
			helloInvlMin.setEnabled(false);
			helloInvlMin.setText("");
			helloInvlMax.setEnabled(false);
			helloInvlMax.setText("");
			deadInvl.setEnabled(false);
			deadInvl.setText("");
			deadInvlMin.setEnabled(false);
			deadInvlMin.setText("");
			deadInvlMax.setEnabled(false);
			deadInvlMax.setText("");
			modify.setEnabled(false);
			return;
		}
		ctrlChList.setSelectedIndex(0);
		setCurrentCtrlCh((String)ctrlChList.getSelectedValue());
	}

	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource().equals(add)) {
			if (npPorts.size() == 0) return;
			String ports[] = new String[npPorts.size()];
			for (int i=0; i<ports.length; i++) ports[i] = (String)npPorts.get(i);
			CtrlCHDialog dialog = new CtrlCHDialog(owner, ports);
			dialog.setLocation((int)(owner.getLocation().getX() + owner.getWidth()/2-dialog.getWidth()/2), (int)(owner.getLocation().getY()+owner.getHeight()/2-dialog.getHeight()/2));
			dialog.setVisible(true);
			if (dialog.ret == CtrlCHDialog.OK) {
				if (ctrlch.containsKey(dialog.name)) {
					GMPLSAdmin.showError(owner, "There is already a control channel named "+dialog.name);
					return;
				}
				addCtrlCh(dialog.name, "", dialog.remoteIP, "", dialog.remoteRid, dialog.eqptID, "", "", "", "", "", "", "");
				owner.admin.addCtrlCh(dialog.name, dialog.remoteIP,  dialog.remoteRid, dialog.eqptID, "", "", "", "", "", "", "");
			}
			return;
		}
		
		if (e.getSource().equals(delete)) {
			String name = (String)ctrlChList.getSelectedValue();
			int ret = JOptionPane.showConfirmDialog(owner, "Are you sure you want to delete the control channel "+name+"?", "Delete", JOptionPane.YES_NO_OPTION);
			if (ret == JOptionPane.YES_OPTION) {
				deleteCtrlCh(name);
				owner.admin.deleteCtrlCh(name);
			}
			return;
		}
		
		if (e.getSource().equals(modify)) {
			String name =  (String)ctrlChList.getSelectedValue();
			String remoteIP = this.remoteIP.getText();
			String remoteRid = this.remoteRid.getText();
			String port = (String)this.port.getSelectedItem();
			String adj = (String)this.adj.getSelectedItem();
			String helloInvl = this.helloInvl.getText();
			String helloInvlMin = this.helloInvlMin.getText();
			String helloInvlMax = this.helloInvlMax.getText();
			String deadInvl = this.deadInvl.getText();
			String deadInvlMin = this.deadInvlMin.getText();
			String deadInvlMax = this.deadInvlMax.getText();
			owner.admin.changeCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax, deadInvl, deadInvlMin, deadInvlMax);
		}
	}

	private void setCurrentCtrlCh(String name) {
		
		if (!ctrlch.containsKey(name)) return;
		delete.setEnabled(true);
		remoteIP.setEnabled(true);
		remoteRid.setEnabled(true);
		port.setEnabled(true);
		adj.setEnabled(true);
		helloInvl.setEnabled(true);
		helloInvlMin.setEnabled(true);
		helloInvlMax.setEnabled(true);
		deadInvl.setEnabled(true);
		deadInvlMin.setEnabled(true);
		deadInvlMax.setEnabled(true);
		CtrlCH ch = (CtrlCH)ctrlch.get(name);
		remoteIP.setText(ch.remoteIP);
		remoteRid.setText(ch.remoteRid);
		if (ch.port != null && ch.port.length() != 0) {
			boolean found = false;
			for (int i=0; i<port.getItemCount(); i++) {
				String item = (String)port.getItemAt(i);
				if (item.equals(ch.port)) {
					found = true;
					port.setSelectedIndex(i);
					break;
				}
			}
			if (!found) {
				addNPPort(ch.port);
				port.setSelectedIndex(port.getItemCount()-1);
			}
		}
		if (ch.adj != null && ch.adj.length() != 0) {
			boolean found = false;
			if (ch.adj.length() == 0) {
				adj.setSelectedIndex(0);
			} else {
				found = false;
				for (int i=0; i<adj.getItemCount(); i++) {
					String item = (String)adj.getItemAt(i);
					if (item.equals(ch.adj)) {
						found = true;
						adj.setSelectedIndex(i);
						break;
					}
				}
				if (!found) {
					addAdj(ch.adj);
					adj.setSelectedIndex(adj.getItemCount()-1);
				}
			}
		}
		helloInvl.setText(ch.helloInvl);
		helloInvlMin.setText(ch.helloInvlMin);
		helloInvlMax.setText(ch.helloInvlMax);
		deadInvl.setText(ch.deadInvl);
		deadInvlMin.setText(ch.deadInvlMin);
		deadInvlMax.setText(ch.deadInvlMax);
	}
	
	public void valueChanged(ListSelectionEvent e) {
		
		if (e.getValueIsAdjusting()) {
			setCurrentCtrlCh((String)ctrlChList.getSelectedValue());
		}
	}
	
} // end of class CtrlCHConfig

