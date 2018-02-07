package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Class that handled the configuration of ip addresses for the NP ports of a calient sw
 */
public class GMPLSNPPorts extends JPanel implements ActionListener, ItemListener {

	protected JComboBox eqptID;
	protected boolean eqptIDFlag = false;
	protected JTextField ip;
	protected JTextField mask;
	protected JTextField gw;
	protected JButton modify; 
	
	protected Hashtable h = new Hashtable();
	protected GMPLSAdmin admin;
	
	public  GMPLSNPPorts(GMPLSAdmin admin) {
		
		super();
		setToolTipText(GMPLSHelper.npHelp);
		this.admin = admin;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("NP Ports"));
		JPanel eqptPanel = new JPanel();
		eqptPanel.setOpaque(false);
		eqptPanel.setLayout(new BoxLayout(eqptPanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("Eqpt: ");
		l.setToolTipText(GMPLSHelper.eqptIDTT);
		eqptID = new JComboBox();
		eqptID.setToolTipText(GMPLSHelper.eqptIDTT);
		eqptID.addItemListener(this);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(l);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(eqptID);
		eqptPanel.add(Box.createHorizontalStrut(5));
		add(eqptPanel);
		JPanel ipPanel = new JPanel();
		ipPanel.setOpaque(false);
		ipPanel.setLayout(new BoxLayout(ipPanel, BoxLayout.X_AXIS));
		l = new JLabel("IP: ");
		l.setToolTipText(GMPLSHelper.ipTT);
		ip = new JTextField();
		ip.setToolTipText(GMPLSHelper.ipTT);
		ip.setBackground(GMPLSConfig.color);
		ip.setEnabled(false);
		ipPanel.add(Box.createHorizontalStrut(5));
		ipPanel.add(l);
		ipPanel.add(Box.createHorizontalStrut(5));
		ipPanel.add(ip);
		ipPanel.add(Box.createHorizontalStrut(5));
		add(ipPanel);
		JPanel maskPanel = new JPanel();
		maskPanel.setOpaque(false);
		maskPanel.setLayout(new BoxLayout(maskPanel, BoxLayout.X_AXIS));
		l = new JLabel("Mask: ");
		l.setToolTipText(GMPLSHelper.maskTT);
		mask = new JTextField();
		mask.setToolTipText(GMPLSHelper.maskTT);
		mask.setBackground(GMPLSConfig.color);
		mask.setEnabled(false);
		maskPanel.add(Box.createHorizontalStrut(5));
		maskPanel.add(l);
		maskPanel.add(Box.createHorizontalStrut(5));
		maskPanel.add(mask);
		maskPanel.add(Box.createHorizontalStrut(5));
		add(maskPanel);
		JPanel gwPanel = new JPanel();
		gwPanel.setOpaque(false);
		gwPanel.setLayout(new BoxLayout(gwPanel, BoxLayout.X_AXIS));
		l = new JLabel("Gateway: ");
		l.setToolTipText(GMPLSHelper.gwTT);
		gw = new JTextField();
		gw.setToolTipText(GMPLSHelper.gwTT);
		gw.setBackground(GMPLSConfig.color);
		gw.setEnabled(false);
		gwPanel.add(Box.createHorizontalStrut(5));
		gwPanel.add(l);
		gwPanel.add(Box.createHorizontalStrut(5));
		gwPanel.add(gw);
		gwPanel.add(Box.createHorizontalStrut(5));
		add(gwPanel);
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.modify);
		modify.setEnabled(false);
		modify.addActionListener(this);
		JPanel mPanel = new JPanel();
		mPanel.setOpaque(false);
		mPanel.setLayout(new BorderLayout());
		mPanel.add(modify, BorderLayout.CENTER);
		add(Box.createVerticalStrut(5));
		add(mPanel);
	}
	
	private void addItem(String item) {
		
		for (int i=0; i<eqptID.getItemCount(); i++) {
			String s = (String)eqptID.getItemAt(i);
			if (s.equals(item)) return;
		}
		eqptID.addItem(item);
	}
	
	public void setEqpt(String port, String ip, String mask, String gw) {

		addItem(port);
		if (h.containsKey(port)) {
			String[] str = (String[])h.get(port);
			String oldIP = str[0];
			if (ip != null) str[0] = ip;
			if (mask != null) str[1] = mask;
			if (gw != null) str[2] = gw;
			h.put(port, str);
			if (oldIP != null && oldIP.length() != 0) {
				admin.config.link.changeIP(oldIP, ip);
			} else {
				admin.config.link.addAddress(ip);
			}
		} else {
			h.put(port, new String[] { ip, mask, gw } );
		}
		this.ip.setEnabled(true);
		this.mask.setEnabled(true);
		this.gw.setEnabled(true);
		modify.setEnabled(true);
		check();
	}

	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource().equals(modify)) {
			String port = (String)eqptID.getSelectedItem();
			if (port == null) return;
			String sip = ip.getText();
			String smask = mask.getText();
			String sgw = gw.getText();
			admin.changeNPPort(port, sip, smask, sgw);
		}
	}
	
	private void check() {
		String port = (String)eqptID.getSelectedItem();
		if (port == null) return;
		String[] str = (String[])h.get(port);
		if (str == null) return;
		if (str[0] != null)
			ip.setText(str[0]);
		else
			ip.setText("");
		if (str[1] != null)
			mask.setText(str[1]);
		else
			mask.setText("");
		if (str[2] != null)
			gw.setText(str[2]);
		else
			gw.setText("");
	}

	public void itemStateChanged(ItemEvent e) {
		
		if (e.getSource().equals(eqptID)) {
			String port = (String)eqptID.getSelectedItem();
			if (port == null) return;
			String[] str = (String[])h.get(port);
			if (str == null) return;
			if (str[0] != null)
				ip.setText(str[0]);
			else
				ip.setText("");
			if (str[1] != null)
				mask.setText(str[1]);
			else
				mask.setText("");
			if (str[2] != null)
				gw.setText(str[2]);
			else
				gw.setText("");
			return;
		}
	}
	
} // end of class GMPLSNPPorts

