package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

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
 * Dialog GUI class for adding a new ctrl channel.
 */
public class CtrlCHDialog extends JDialog implements ActionListener, WindowListener {

	public static final byte OK = 0;
	public static final byte CANCEL = 1;
	
	private JTextField nameField;
	private JComboBox eqptField;
	private JTextField remoteIPField;
	private JTextField remoteRidField;
	
	public String name;
	public String eqptID;
	public String remoteIP;
	public String remoteRid;
	
	private JButton modify;
	private JButton cancel;
	
	public byte ret;
	
	public CtrlCHDialog(JFrame owner, String[] eqpts) {
		
		super(owner, "Add control channel", true);
		getContentPane().setLayout(new BorderLayout());
		JPanel p = new EnhancedJPanel();
		p.setToolTipText(GMPLSHelper.ctrlHelp);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(Box.createVerticalStrut(5));
		JPanel namePanel = new JPanel();
		namePanel.setOpaque(false);
		namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("Name: ");
		l.setToolTipText(GMPLSHelper.ctrlName);
		nameField = new JTextField();
		nameField.setToolTipText(GMPLSHelper.ctrlName);
		nameField.setBackground(GMPLSConfig.color);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(l);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(nameField);
		namePanel.add(Box.createHorizontalStrut(5));
		p.add(namePanel);
		p.add(Box.createVerticalStrut(5));
		JPanel eqptPanel = new JPanel();
		eqptPanel.setOpaque(false);
		eqptPanel.setLayout(new BoxLayout(eqptPanel, BoxLayout.X_AXIS));
		l = new JLabel("EqptID: ");
		l.setToolTipText(GMPLSHelper.ctrlEqpt);
		eqptField = new JComboBox(eqpts);
		eqptField.setToolTipText(GMPLSHelper.ctrlEqpt);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(l);
		eqptPanel.add(Box.createHorizontalStrut(5));
		eqptPanel.add(eqptField);
		eqptPanel.add(Box.createHorizontalStrut(5));
		p.add(eqptPanel);
		p.add(Box.createVerticalStrut(5));
		JPanel ipPanel = new JPanel();
		ipPanel.setOpaque(false);
		ipPanel.setLayout(new BoxLayout(ipPanel, BoxLayout.X_AXIS));
		l = new JLabel("Remote IP: ");
		l.setToolTipText(GMPLSHelper.ctrlRemoteIP);
		remoteIPField = new JTextField();
		remoteIPField.setToolTipText(GMPLSHelper.ctrlRemoteIP);
		remoteIPField.setBackground(GMPLSConfig.color);
		ipPanel.add(Box.createHorizontalStrut(5));
		ipPanel.add(l);
		ipPanel.add(Box.createHorizontalStrut(5));
		ipPanel.add(remoteIPField);
		ipPanel.add(Box.createHorizontalStrut(5));
		p.add(ipPanel);
		p.add(Box.createVerticalStrut(5));
		JPanel ridPanel = new JPanel();
		ridPanel.setOpaque(false);
		ridPanel.setLayout(new BoxLayout(ridPanel, BoxLayout.X_AXIS));
		l = new JLabel("Remote Rid: ");
		l.setToolTipText(GMPLSHelper.ctrlRemoteRid);
		remoteRidField = new JTextField();
		remoteRidField.setToolTipText(GMPLSHelper.ctrlRemoteRid);
		remoteRidField.setBackground(GMPLSConfig.color);
		ridPanel.add(Box.createHorizontalStrut(5));
		ridPanel.add(l);
		ridPanel.add(Box.createHorizontalStrut(5));
		ridPanel.add(remoteRidField);
		ridPanel.add(Box.createHorizontalStrut(5));
		p.add(ridPanel);
		
		JPanel mPanel = new JPanel();
		mPanel.setOpaque(false);
		mPanel.setLayout(new BoxLayout(mPanel, BoxLayout.X_AXIS));
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.ctrlAdd);
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
		setSize(200, 200);
		setResizable(false);
	}

	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource().equals(modify)) {
			name = nameField.getText();
			if (name == null || name.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the name of the control channel");
				return;
			}
			remoteIP = remoteIPField.getText();
			if (remoteIP == null || remoteIP.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the remote ip");
				return;
			}
			remoteRid = remoteRidField.getText();
			if (remoteRid == null || remoteRid.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the remote rid");
				return;
			}
			eqptID = (String)eqptField.getSelectedItem();
			ret = OK;
			setVisible(false);
			return;
		}
		if(e.getSource().equals(cancel)) {
			name = null;
			eqptID = null;
			remoteIP = null;
			remoteRid = null;
			ret = CANCEL;
			setVisible(false);
			return;
		}
	}

	/** Just for testing */
	public static void main(String args[]) {
		JFrame f = new JFrame();
		f.setSize(200, 200);
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
		CtrlCHDialog d = new CtrlCHDialog(f, new String[] { "e1", "e2"} );
		d.setVisible(true);
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		name = null;
		eqptID = null;
		remoteIP = null;
		remoteRid = null;
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

}
