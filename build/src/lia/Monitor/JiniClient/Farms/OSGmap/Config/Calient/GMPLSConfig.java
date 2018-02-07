package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/**
 * 	A class that helps configuring the GMPLS params for the Calient sw.
 */
public class GMPLSConfig extends JFrame implements ActionListener {

	/** For IP */
	protected GMPLSNPPorts npPorts;
	
	/** For OSPF */
	protected JTextField routerID;
	protected boolean routerIDFlag = false;
	protected JTextField areaID;
	protected boolean areaIDFlag = false;
	protected JButton ospfModify;
	
	/** For RSVP */
	protected JTextField msgRetryInvl;
	protected boolean msgRetryInvlFlag = false;
	protected JTextField ntfRetryInvl;
	protected boolean ntfRetryInvlFlag = false;
	protected JTextField grInvl;
	protected boolean grInvlFlag = false;
	protected JTextField grcvInvl;
	protected boolean grcvInvlFlag = false;
	protected JButton rsvpModify;
	
	/** For CtrlCH */
	protected CtrlCHConfig ctrlCh;
	
	/** For Adj */
	protected AdjConfig adj;
	
	/** For TE Links */
	protected LinkConfig link;
	
	static final Color color = new Color(255, 255, 150);
	
	GMPLSAdmin admin;
	
	public GMPLSConfig(String swName, GMPLSAdmin admin) {

		super(swName);
		this.admin = admin;
		initGUI();
	}
	
	private void initGUI() {
		
		getContentPane().setLayout(new BorderLayout());
		
		JPanel p = new EnhancedJPanel();
		p.setLayout(new GridLayout(0, 4));
		
		JPanel p1 = new JPanel();
		p1.setOpaque(false);
		p1.setLayout(new BoxLayout(p1, BoxLayout.Y_AXIS));
		JPanel p2 = new JPanel();
		p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));
		p2.setOpaque(false);
		JPanel p3 = new JPanel();
		p3.setLayout(new BoxLayout(p3, BoxLayout.Y_AXIS));
		p3.setOpaque(false);
		JPanel p4 = new JPanel();
		p4.setLayout(new BoxLayout(p4, BoxLayout.Y_AXIS));
		p4.setOpaque(false);
		
		p.add(p1);
		p.add(p2);
		p.add(p3);
		p.add(p4);
		
		/** lay out the np ports params */
		npPorts = new GMPLSNPPorts(admin);
		p1.add(npPorts);
		
		// lay out the ospf components
		JPanel ospfPanel = new JPanel();
		ospfPanel.setToolTipText(GMPLSHelper.ospfHelp);
		ospfPanel.setOpaque(false);
		ospfPanel.setBorder(BorderFactory.createTitledBorder("OSPF"));
		ospfPanel.setLayout(new BoxLayout(ospfPanel, BoxLayout.Y_AXIS));
		JPanel routerPanel = new JPanel();
		routerPanel.setOpaque(false);
		routerPanel.setLayout(new BoxLayout(routerPanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("RouterID: ");
		l.setToolTipText(GMPLSHelper.routerIDTT);
		routerID = new JTextField(12);
		routerID.setToolTipText(GMPLSHelper.routerIDTT);
		routerID.setBackground(color);
		routerID.setEnabled(false);
		routerPanel.add(Box.createHorizontalStrut(5));
		routerPanel.add(l);
		routerPanel.add(Box.createHorizontalStrut(5));
		routerPanel.add(routerID);
		routerPanel.add(Box.createHorizontalStrut(5));
		ospfPanel.add(routerPanel);
		JPanel areaPanel = new JPanel();
		areaPanel.setOpaque(false);
		areaPanel.setLayout(new BoxLayout(areaPanel, BoxLayout.X_AXIS));
		l = new JLabel("AreaID: ");
		l.setToolTipText(GMPLSHelper.areaIDTT);
		areaID = new JTextField();
		areaID.setToolTipText(GMPLSHelper.areaIDTT);
		areaID.setBackground(color);
		areaID.setEnabled(false);
		areaPanel.add(Box.createHorizontalStrut(5));
		areaPanel.add(l);
		areaPanel.add(Box.createHorizontalStrut(5));
		areaPanel.add(areaID);
		areaPanel.add(Box.createHorizontalStrut(5));
		ospfPanel.add(areaPanel);
		JPanel mp = new JPanel();
		mp.setOpaque(false);
		mp.setLayout(new BorderLayout());
		ospfModify = new JButton("Modify");
		ospfModify.setToolTipText(GMPLSHelper.modify);
		ospfModify.addActionListener(this);
		ospfModify.setEnabled(false);
		mp.add(ospfModify);
		ospfPanel.add(Box.createVerticalStrut(5));
		ospfPanel.add(mp);
		
		p1.add(ospfPanel);
		
		// lay out the rsvp components
		JPanel rsvpPanel = new JPanel();
		rsvpPanel.setToolTipText(GMPLSHelper.rsvpHelp);
		rsvpPanel.setOpaque(false);
		rsvpPanel.setBorder(BorderFactory.createTitledBorder("RSVP"));
		rsvpPanel.setLayout(new BoxLayout(rsvpPanel, BoxLayout.Y_AXIS));
		JPanel msgRetryPanel = new JPanel();
		msgRetryPanel.setOpaque(false);
		msgRetryPanel.setLayout(new BoxLayout(msgRetryPanel, BoxLayout.X_AXIS));
		l = new JLabel("MsgRetryInvl: ");
		l.setToolTipText(GMPLSHelper.msgRetryInvlTT);
		msgRetryInvl = new JTextField(10);
		msgRetryInvl.setToolTipText(GMPLSHelper.msgRetryInvlTT);
		msgRetryInvl.setBackground(color);
		msgRetryInvl.setEnabled(false);
		msgRetryPanel.add(Box.createHorizontalStrut(5));
		msgRetryPanel.add(l);
		msgRetryPanel.add(Box.createHorizontalStrut(5));
		msgRetryPanel.add(msgRetryInvl);
		msgRetryPanel.add(Box.createHorizontalStrut(5));
		rsvpPanel.add(msgRetryPanel);
		JPanel ntfRetryPanel = new JPanel();
		ntfRetryPanel.setOpaque(false);
		ntfRetryPanel.setLayout(new BoxLayout(ntfRetryPanel, BoxLayout.X_AXIS));
		l = new JLabel("NtfRetryInvl: ");
		l.setToolTipText(GMPLSHelper.ntfRetryInvlTT);
		ntfRetryInvl = new JTextField();
		ntfRetryInvl.setToolTipText(GMPLSHelper.ntfRetryInvlTT);
		ntfRetryInvl.setBackground(color);
		ntfRetryInvl.setEnabled(false);
		ntfRetryPanel.add(Box.createHorizontalStrut(5));
		ntfRetryPanel.add(l);
		ntfRetryPanel.add(Box.createHorizontalStrut(5));
		ntfRetryPanel.add(ntfRetryInvl);
		ntfRetryPanel.add(Box.createHorizontalStrut(5));
		rsvpPanel.add(ntfRetryPanel);
		JPanel grPanel = new JPanel();
		grPanel.setOpaque(false);
		grPanel.setLayout(new BoxLayout(grPanel, BoxLayout.X_AXIS));
		l = new JLabel("GRInvl: ");
		l.setToolTipText(GMPLSHelper.grInvlTT);
		grInvl = new JTextField();
		grInvl.setToolTipText(GMPLSHelper.grInvlTT);
		grInvl.setEnabled(false);
		grInvl.setBackground(color);
		grPanel.add(Box.createHorizontalStrut(5));
		grPanel.add(l);
		grPanel.add(Box.createHorizontalStrut(5));
		grPanel.add(grInvl);
		grPanel.add(Box.createHorizontalStrut(5));
		rsvpPanel.add(grPanel);
		JPanel grcPanel = new JPanel();
		grcPanel.setOpaque(false);
		grcPanel.setLayout(new BoxLayout(grcPanel, BoxLayout.X_AXIS));
		l = new JLabel("GRCVInvl: ");
		l.setToolTipText(GMPLSHelper.grcvInvlTT);
		grcvInvl = new JTextField();
		grcvInvl.setToolTipText(GMPLSHelper.grcvInvlTT);
		grcvInvl.setBackground(color);
		grcvInvl.setEnabled(false);
		grcPanel.add(Box.createHorizontalStrut(5));
		grcPanel.add(l);
		grcPanel.add(Box.createHorizontalStrut(5));
		grcPanel.add(grcvInvl);
		grcPanel.add(Box.createHorizontalStrut(5));
		rsvpPanel.add(grcPanel);
		mp = new JPanel();
		mp.setOpaque(false);
		mp.setLayout(new BorderLayout());
		rsvpModify = new JButton("Modify");
		rsvpModify.setToolTipText(GMPLSHelper.modify);
		rsvpModify.addActionListener(this);
		rsvpModify.setEnabled(false);
		mp.add(rsvpModify);
		rsvpPanel.add(Box.createVerticalStrut(5));
		rsvpPanel.add(mp);
		
		p1.add(rsvpPanel);
		
		/** lay out the ctrl ch params */
		ctrlCh = new CtrlCHConfig(this);
		p2.add(ctrlCh);
		
		/** lay out the adj params */
		adj = new AdjConfig(this);
		p3.add(adj);
		
		/** lay out the link params */
		link = new LinkConfig(this);
		p4.add(link);
		
		p1.add(Box.createVerticalGlue());
		p2.add(Box.createVerticalGlue());
		p3.add(Box.createVerticalGlue());
		p4.add(Box.createVerticalGlue());
		
		getContentPane().add(p, BorderLayout.CENTER);
		
		setSize(900, 500);
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
	}

	private void changeOSPF() {
		
		String r = routerID.getText();
		String a = areaID.getText();
		admin.changeOSPF(r, a);
	}
	
	private void changeRSVP() {
		
		String m = msgRetryInvl.getText();
		String n = ntfRetryInvl.getText();
		String gr = grInvl.getText();
		String grc = grcvInvl.getText();
		admin.changeRSVP(m, n, gr, grc);
	}
	
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource().equals(ospfModify)) {
			changeOSPF();
			return;
		}
		if (e.getSource().equals(rsvpModify)) {
			changeRSVP();
			return;
		}
	}

	/** for testing */
	public static void main(String args[]) {
		
		GMPLSConfig c = new GMPLSConfig("Calient1", null);
		c.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		c.setVisible(true);
		c.link.setFreePorts(new String[] { "10.0.1", "10.0.2" });
		c.link.addLink("link0", "192.156.0.2", "192.156.0.3", "126", "128", "WDM", "adj0", "", "0", "1", "", "detect", false, "WDM", "10");
		Vector ctrl = new Vector();
		ctrl.add("ctrl0"); ctrl.add("ctrl1");
		c.adj.addAdj("adj0", "0", "1", ctrl, "ctrl0", "0", "", "", "WDM", "Adj", "19", "1", "1");
		c.ctrlCh.addCtrlCh("ctrl0", "100.100.100.100", "200.200.200.200", "10", "20", "port1", null, "10", "5", "15", "20", "19", "21");
		c.ctrlCh.addCtrlCh("ctrl1", "100.100.100.101", "200.200.200.201", "11", "21", "port2", "adj1", "11", "6", "16", "21", "18", "22");
		c.npPorts.setEqpt("10.0.1", "192.156.0.2", "255.255.255.192", "192.156.0.1");
		c.ctrlCh.addNPPort("10.0.1");
		c.routerIDFlag = true;
		c.routerID.setText("0");
		c.routerID.setEnabled(true);
		c.ospfModify.setEnabled(true);
		c.areaIDFlag = true;
		c.areaID.setText("19");
		c.areaID.setEnabled(true);
		c.ospfModify.setEnabled(true);
		c.msgRetryInvlFlag = true;
		c.msgRetryInvl.setText("30");
		c.msgRetryInvl.setEnabled(true);
		c.rsvpModify.setEnabled(true);
		c.ntfRetryInvlFlag = true;
		c.ntfRetryInvl.setText("100");
		c.ntfRetryInvl.setEnabled(true);
		c.rsvpModify.setEnabled(true);
		c.grInvlFlag = true;
		c.grInvl.setText("30");
		c.grInvl.setEnabled(true);
		c.rsvpModify.setEnabled(true);
		c.grcvInvlFlag = true;
		c.grcvInvl.setText("30");
		c.grcvInvl.setEnabled(true);
		c.rsvpModify.setEnabled(true);
	}
	
} // end of class GMPLSConfig

