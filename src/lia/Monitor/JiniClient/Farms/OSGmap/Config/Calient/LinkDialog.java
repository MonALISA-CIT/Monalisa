package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/**
 * Dialog GUI class for adding a new TE Link.
 */
public class LinkDialog extends JDialog implements ActionListener {

	public static final byte OK = 0;
	public static final byte CANCEL = 1;

	public static String linkTypes[] = { "Numbered", "Unnumbered" };

	private JTextField nameField;
	private JComboBox linkType;
	private JComboBox localIPField;
	private JTextField remoteIPField;
	private JComboBox adjField;
	
	public String name;
	public String localIP;
	public String remoteIP;
	public String adj;
	
	private JButton modify;
	private JButton cancel;
	
	public byte ret;

	public LinkDialog(JFrame owner, Vector localIPs, Vector adjs) {

		super(owner, "Add TE Link", true);
		getContentPane().setLayout(new BorderLayout());
		JPanel p = new EnhancedJPanel();
		p.setToolTipText(GMPLSHelper.linkHelp);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(Box.createVerticalStrut(5));
		JPanel namePanel = new JPanel();
		namePanel.setOpaque(false);
		namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("Name: ");
		l.setToolTipText(GMPLSHelper.linkName);
		nameField = new JTextField();
		nameField.setToolTipText(GMPLSHelper.linkName);
		nameField.setBackground(GMPLSConfig.color);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(l);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(nameField);
		namePanel.add(Box.createHorizontalStrut(5));
		p.add(namePanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel typesP = new JPanel();
		typesP.setOpaque(false);
		typesP.setLayout(new BoxLayout(typesP, BoxLayout.X_AXIS));
		l = new JLabel("LinkType: ");
		l.setToolTipText(GMPLSHelper.linkType);
		linkType = new JComboBox(linkTypes);
		linkType.setToolTipText(GMPLSHelper.linkType);
		linkType.setEnabled(false);
		typesP.add(Box.createHorizontalStrut(5));
		typesP.add(l);
		typesP.add(Box.createHorizontalStrut(5));
		typesP.add(linkType);
		typesP.add(Box.createHorizontalStrut(5));
		p.add(typesP);
		p.add(Box.createVerticalStrut(5));
		
		JPanel localIPPanel = new JPanel();
		localIPPanel.setOpaque(false);
		localIPPanel.setLayout(new BoxLayout(localIPPanel, BoxLayout.X_AXIS));
		l = new JLabel("LocalIP: ");
		l.setToolTipText(GMPLSHelper.linkLocalIP);
		String[] ips = new String[localIPs.size()];
		for (int i=0; i<ips.length; i++) ips[i]= (String)localIPs.get(i);
		localIPField = new JComboBox(ips);
		localIPField.setToolTipText(GMPLSHelper.linkLocalIP);
		localIPField.setBackground(GMPLSConfig.color);
		localIPPanel.add(Box.createHorizontalStrut(5));
		localIPPanel.add(l);
		localIPPanel.add(Box.createHorizontalStrut(5));
		localIPPanel.add(localIPField);
		localIPPanel.add(Box.createHorizontalStrut(5));
		p.add(localIPPanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel remoteIPPanel = new JPanel();
		remoteIPPanel.setOpaque(false);
		remoteIPPanel.setLayout(new BoxLayout(remoteIPPanel, BoxLayout.X_AXIS));
		l = new JLabel("RemoteIP: ");
		l.setToolTipText(GMPLSHelper.linkRemoteIP);
		remoteIPField = new JTextField();
		remoteIPField.setToolTipText(GMPLSHelper.linkRemoteIP);
		remoteIPField.setBackground(GMPLSConfig.color);
		remoteIPPanel.add(Box.createHorizontalStrut(5));
		remoteIPPanel.add(l);
		remoteIPPanel.add(Box.createHorizontalStrut(5));
		remoteIPPanel.add(remoteIPField);
		remoteIPPanel.add(Box.createHorizontalStrut(5));
		p.add(remoteIPPanel);
		p.add(Box.createVerticalStrut(5));

		JPanel adjPanel = new JPanel();
		adjPanel.setOpaque(false);
		adjPanel.setLayout(new BoxLayout(adjPanel, BoxLayout.X_AXIS));
		l = new JLabel("Adj: ");
		l.setToolTipText(GMPLSHelper.linkAdj);
		String[] adjss = new String[adjs.size()];
		for (int i=0; i<adjss.length; i++) adjss[i] = (String)adjs.get(i);
		adjField = new JComboBox(adjss);
		adjField.setToolTipText(GMPLSHelper.linkAdj);
		adjPanel.add(Box.createHorizontalStrut(5));
		adjPanel.add(l);
		adjPanel.add(Box.createHorizontalStrut(5));
		adjPanel.add(adjField);
		adjPanel.add(Box.createHorizontalStrut(5));
		p.add(adjPanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel mPanel = new JPanel();
		mPanel.setOpaque(false);
		mPanel.setLayout(new BoxLayout(mPanel, BoxLayout.X_AXIS));
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.linkAdd);
		modify.addActionListener(this);
		cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		mPanel.add(Box.createHorizontalStrut(5));
		mPanel.add(cancel);
		mPanel.add(Box.createHorizontalStrut(5));
		mPanel.add(modify);
		mPanel.add(Box.createHorizontalStrut(5));
		p.add(Box.createVerticalStrut(5));
		p.add(mPanel);
		p.add(Box.createVerticalStrut(5));
		
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		getContentPane().add(p, BorderLayout.CENTER);
		setSize(400, 230);
		setResizable(false);
	}
	
	public void actionPerformed(ActionEvent e) {
		
		Object source = e.getSource();
		if (source.equals(modify)) {
			name = nameField.getText();
			if (name == null || name.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the name of the TE link");
				return;
			}
			remoteIP = remoteIPField.getText();
			if (remoteIP == null || remoteIP.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the remote ip");
				return;
			}
			localIP = (String)localIPField.getSelectedItem();
			adj = (String)adjField.getSelectedItem();
			ret = OK;
			setVisible(false);
			return;
		}
		if(source.equals(cancel)) {
			name = null;
			localIP = null;
			remoteIP = null;
			adj = null;
			ret = CANCEL;
			setVisible(false);
			return;
		}
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		name = null;
		localIP = null;
		remoteIP = null;
		adj = null;
		ret = CANCEL;
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	/** Just for testing */
	public static void main(String args[]) {
		JFrame f = new JFrame();
		f.setSize(200, 200);
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
		Vector ips = new Vector(); ips.add("10.1.1.1"); ips.add("10.2.1.2");
		Vector adj = new Vector(); adj.add("t1"); adj.add("t2");
		LinkDialog d = new LinkDialog(f, ips, adj);
		d.setVisible(true);
	}
	
} // end of class LinkDialog

