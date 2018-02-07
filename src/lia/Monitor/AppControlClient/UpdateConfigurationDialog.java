package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;

public class UpdateConfigurationDialog extends JDialog {

	final Color color = new Color(227, 233, 255);

	private JTextArea textArea = null;

	private JButton modifyButton = null;

	private String confFile = null;

	private CommunicateMsg communicator = null;

	private String moduleName = null;
	private String configurationFile = null;

	private String response = null;

	public UpdateConfigurationDialog(
		String name,
		JFrame parent,
		boolean modal,
		String confFile,
		CommunicateMsg communicator,
		String moduleName,
		String configurationFile) {

		super(parent, name, modal);

		this.confFile = confFile;
		this.communicator = communicator;
		this.moduleName = moduleName;
		this.configurationFile = configurationFile;

		getContentPane().setLayout(new BorderLayout());

		textArea = new JTextArea();
		JScrollPane scroll = new JScrollPane(textArea);
		getContentPane().add(scroll);

		Action modifyAction = new AbstractAction("Update configuration") {
			public void actionPerformed(ActionEvent evt) {
				updateConfigurationAction();
			} //actionPerformed
		}; //modifyAction
		modifyButton = new RoundButton(modifyAction);

		JPanel downPanel = new JPanel();
		downPanel.setLayout(new BorderLayout());
		downPanel.setBackground(color);
		JSeparator separator = new JSeparator();
		downPanel.add(separator, BorderLayout.NORTH);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setBackground(color);
		buttonPanel.add(modifyButton);
		downPanel.add(buttonPanel);
		getContentPane().add(downPanel, BorderLayout.SOUTH);

		textArea.setText(lia.app.AppUtils.dec(confFile));

		pack();

		if (parent != null) {
			Rectangle r = parent.getBounds();
			setLocation(
				(int) (r.getX() + r.getWidth() / 2 - 300),
				(int) (r.getY() + r.getHeight() / 2 - 200));
		} //if

		setSize(600, 400);

		setVisible(true);
	}

	public void dezactivate () {
		modifyButton.setEnabled(false);
	}
	
	public void activate () {
		modifyButton.setEnabled (true);
	}
	
	public void setText(String text) {

		if (textArea == null)
			return;
		textArea.setText(text);
	}

	public void appendText(String text) {

		if (textArea == null)
			return;
		textArea.append(text);
	}

	public String getText() {

		if (textArea == null)
			return "";
		return textArea.getText();
	}

	public void afterUpdate () {
		if (response == null) {
			setModal(false);
			setVisible(false);
			dispose();
			return;
		}

		if (response != null && response.startsWith("+OK")) {
			JOptionPane.showMessageDialog(this, "Configuration updated");
		} else {
			if (response !=null)
				JOptionPane.showMessageDialog(this, response);
			else  {
				setModal(false);
				setVisible(false);
				dispose();
				JOptionPane.showMessageDialog(this, "Connection down");
			}	
			//	    System.out.println (response);
		} //if - else

		setModal(false);
		setVisible(false);
		dispose();
	}
	
	public void updateConfigurationAction() {

		String text = textArea.getText();
		if (text==null) {
//System.out.println("UpdateConfiguration =====> null text .... ") ;			
			text="";
		} // if
		
		String command =
			"updateconfig "
				+ lia.app.AppUtils.enc(moduleName + " : " + configurationFile)
				+ " "
				+ lia.app.AppUtils.enc(text);

		WaitingDialog waitingDialog =
			new WaitingDialog(
				command);
		(new Thread(waitingDialog)).start();
		

	} //updateConfigurationAction

	class WaitingDialog  implements Runnable {

		private String command;
		String titleN = "Waiting for a server response ....";

		WaitingDialog(String command) {
			//make modal dialog
			
			this.command = command;

		}

		public void run() {
			response = null;
			String response1 = null;
			//                    response2=null ;
			try {
				communicator.sendCommand(command);
				response = communicator.receiveResponseLine();
				if (response == null) {
					return ;
				}
				if (response.startsWith("+OK")) {
					response1 = communicator.receiveResponseLine();
					while (true) {
						if (response1 == null) {
							response = null;
							
						}

						if (!response1.equals(".")) {
						} else {
							break;
						}
						response1 = communicator.receiveResponseLine();
					} // while
				}
				
				afterUpdate();
				
				activate ();
			} catch (Exception e) {
				e.printStackTrace();
				activate();
				// TODO - aici trebuie ceva sa afiseze asta ......
			} // try - catch

			
		}
	} //WaitingDialog

} // end of class NewDialog
