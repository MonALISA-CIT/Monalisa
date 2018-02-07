package lia.Monitor.control;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.io.File;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import lia.Monitor.monitor.AppConfig;

/**
 * @author 
 *
 */
public class UserPasswordGather {

	public static short ID_OK = 1;
	public static short ID_CANCEL = 2;

	private JFileChooser fc = new JFileChooser();
	private JPasswordField passwordField;
	private JTextField userField;
	private JTextField aliasField;
	private JButton openButton = new JButton("KeyStore...");
	private JDialog theDialog;

	private String _theUser;
	private String _thePassword;
	private String _jdbcURL;
	private String _theAlias;
	
	private short _returnCode = ID_OK;

	public final static String KS_EXTENSION = "ks";

	/*
	 * Get the extension of a file.
	 */  
	private static String getExtension(File f) {
		String ext = null;
		String s = f.getName();
		int i = s.lastIndexOf('.');

		if (i > 0 &&  i < s.length() - 1) {
			ext = s.substring(i+1).toLowerCase();
		}
		return ext;
	}


	private class KeystoreFilter extends FileFilter {

		public boolean accept(File f) {
			if (f.isDirectory()) {
				return true;
			}

			String extension = getExtension(f);
			if (extension != null) {
				if (extension.equals(UserPasswordGather.KS_EXTENSION)) {
						return true;
				} else {
					return false;
				}
			}

			return false;
		}

		//The description of this filter
		public String getDescription() {
			return "Just Keystores *.ks";
		}
	}

	private class ActionListenerImpl implements ActionListener {

		private UserPasswordGather _userPasswordGather;

		public ActionListenerImpl(UserPasswordGather _userPasswordGather) {
			this._userPasswordGather = _userPasswordGather;
		}

		public void actionPerformed(ActionEvent e) {

			if (e.getSource() == openButton) {
				int returnVal = fc.showOpenDialog(theDialog);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File file = fc.getSelectedFile();
					_userPasswordGather.userField.setText(file.getAbsolutePath());
				}
				return;
			}

			if (e.getActionCommand().equals("buttonOk")) {
				_userPasswordGather._theUser =
					_userPasswordGather.userField.getText();
				_userPasswordGather._thePassword =
					String.copyValueOf(
							_userPasswordGather.passwordField.getPassword());
				_userPasswordGather._theAlias =
					_userPasswordGather.aliasField.getText();
				_returnCode = ID_OK;
				_userPasswordGather.theDialog.dispose();
				return;
			} 
			
			if (e.getActionCommand().equals("buttonCancel")) {
				_userPasswordGather._theUser = null;
				_userPasswordGather._thePassword = null;
				_userPasswordGather._theAlias = null;
				_returnCode = ID_CANCEL;
				_userPasswordGather.theDialog.dispose();
				return;
			}
			
			if (e.getActionCommand().equals("userField"))
				_userPasswordGather.passwordField.grabFocus();
		}
	}
		
	private ActionListenerImpl actionListener;
	
	public UserPasswordGather() {
		_thePassword = null;
		_theUser = null;
		_jdbcURL = null;
		fc.addChoosableFileFilter(new KeystoreFilter());
		_theAlias = null;
	}

	public short doAuth() {
		theDialog = new JDialog(new JFrame(), "Key Store & Password", true);

		actionListener = new ActionListenerImpl(this);

		JPanel leftPanel = new JPanel();
		JPanel rightPanel = new JPanel();
		JPanel upperPanel = new JPanel();

		JPanel buttonPanel = new JPanel();
		final JButton buttonCancel = new JButton("Cancel");
		final JButton buttonOk = new JButton("Ok");

		KeyListener enterAdapter = new KeyListener() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					buttonOk.doClick();
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					buttonCancel.doClick();
				}
			}
			public void keyReleased(KeyEvent e) {
			}
			public void keyTyped(KeyEvent e) {
			}
		};
		
		JPanel content = new JPanel();
		content.addKeyListener(enterAdapter);

		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

//		JLabel userLabel = new JLabel("KeyStore:");
		openButton.setAlignmentY(Component.CENTER_ALIGNMENT);
		openButton.addKeyListener(enterAdapter);
//		userLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

		JLabel passwordLabel = new JLabel("Password:");
		passwordLabel.addKeyListener(enterAdapter);
		passwordLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
		
		JLabel aliasLabel = new JLabel("Alias:");
		aliasLabel.addKeyListener(enterAdapter);
		aliasLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

		leftPanel.add(Box.createRigidArea(new Dimension(0, 5)));
//		leftPanel.add(userLabel);
		leftPanel.add(openButton);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		leftPanel.add(aliasLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		leftPanel.add(passwordLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		leftPanel.add(Box.createVerticalGlue());

		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        String store = AppConfig.getProperty("lia.Monitor.KeyStore");
        if(store == null)
        	store = AppConfig.getProperty("keystore");
		String alias = AppConfig.getProperty("keystore_alias");
		userField = new JTextField(store);
		userField.addKeyListener(enterAdapter);
		userField.setActionCommand("userField");
		userField.addActionListener(actionListener);
		openButton.addActionListener(actionListener);

		userField.setPreferredSize(
			new Dimension(200, userField.getPreferredSize().height));
		userField.setMaximumSize(
			new Dimension(
				userField.getMaximumSize().width,
				userField.getPreferredSize().height));
		userField.setMinimumSize(
			new Dimension(200, userField.getPreferredSize().height));

		passwordField = new JPasswordField();
		passwordField.addKeyListener(enterAdapter);
		passwordField.setActionCommand("passwordField");
		passwordField.addActionListener(actionListener);

		passwordField.setPreferredSize(
			new Dimension(200, userField.getMinimumSize().height));
		passwordField.setMaximumSize(
			new Dimension(
				passwordField.getMaximumSize().width,
				passwordField.getPreferredSize().height));
		passwordField.setMinimumSize(
			new Dimension(200, passwordField.getPreferredSize().height));

		aliasField = new JTextField(alias);
		aliasField.setActionCommand("aliasField");
		aliasField.addActionListener(actionListener);

		aliasField.setPreferredSize(
			new Dimension(200, aliasField.getMinimumSize().height));
		aliasField.setMaximumSize(
			new Dimension(
				aliasField.getMaximumSize().width,
				aliasField.getPreferredSize().height));
		aliasField.setMinimumSize(
			new Dimension(200, aliasField.getPreferredSize().height));

		rightPanel.addKeyListener(enterAdapter);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		rightPanel.add(userField);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		rightPanel.add(aliasField);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		rightPanel.add(passwordField);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		rightPanel.add(Box.createVerticalGlue());

		buttonPanel.addKeyListener(enterAdapter);
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonOk.setActionCommand("buttonOk");
		buttonCancel.setActionCommand("buttonCancel");
		buttonPanel.add(buttonOk);
		buttonPanel.add(buttonCancel);

		buttonOk.addActionListener(actionListener);
		buttonCancel.addActionListener(actionListener);

		upperPanel.addKeyListener(enterAdapter);
		upperPanel.setLayout(new BoxLayout(upperPanel, BoxLayout.X_AXIS));
		upperPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		upperPanel.add(leftPanel);
		upperPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		upperPanel.add(rightPanel);
		upperPanel.add(Box.createRigidArea(new Dimension(5, 0)));

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(Box.createRigidArea(new Dimension(0, 5)));
		content.add(upperPanel);
		content.add(Box.createRigidArea(new Dimension(0, 5)));
		content.add(buttonPanel);
		content.add(Box.createRigidArea(new Dimension(0, 5)));
		content.add(Box.createVerticalGlue());

		theDialog.addWindowListener(new WindowAdapter() {
			//public void windowClosing(WindowEvent e){System.exit(0); }
		});
		theDialog.addKeyListener(enterAdapter);

		theDialog.setContentPane(content);
		theDialog.setResizable(false);

		theDialog.pack();
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension dialogSize = theDialog.getSize();
		
		theDialog.setLocation((screenSize.width - dialogSize.width)/2, (screenSize.height - dialogSize.height)/2);
		
		theDialog.setVisible(true);
		
		return _returnCode;
	}

	public String getUser() {
		if (_theUser == null) return "";
		return _theUser;
	}

	public String getPassword() {
		if (_thePassword == null) return "";
		return _thePassword;
	}
	
	public String getAlias() {
		if(_theAlias == null) return "";
		return _theAlias;
	}

	public static void main(String args[]) {
		JFrame justToBeOwner = new JFrame();
		UserPasswordGather auth = new UserPasswordGather();

		auth.doAuth();

		System.out.println(
			"User: " + auth.getUser() + "Password: " + auth.getPassword());

		System.out.println("Out FROM main!!!!");
		
		System.exit(0);
	}

}
