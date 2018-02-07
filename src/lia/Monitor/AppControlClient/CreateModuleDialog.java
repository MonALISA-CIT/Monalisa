package lia.Monitor.AppControlClient;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

public class CreateModuleDialog extends JDialog {

	private ModulesMainFrame parent;
	private ModulesTree modulesTree;
	private JButton uploadButton;
	private JButton createModuleButton;
	private CommunicateMsg communicator;
	private String response = null;
	
	BusyDialog busy ;
	
	
	public static final int UPLOAD=0;

	public CreateModuleDialog(
		String name,
		ModulesMainFrame parent,
		CommunicateMsg communicator) {
		super(parent, name, true);

		this.communicator = communicator;
		this.parent = parent;
		getContentPane().setLayout(new GridLayout(0, 2));

		modulesTree = new ModulesTree();

		
		
	} //CreateModuleDialog

	public void dezactivate () {
		uploadButton.setEnabled(false);
		createModuleButton.setEnabled (false);
	
		busy.setVisible (true);
	} // dezactivate
	
	public void activate () {
		uploadButton.setEnabled(true);
		createModuleButton.setEnabled (true);
	} // activate
	
	public void addModule(String str) {

		String moduleName = null;
		String configurationFile = null;
		String modulesStatus = null;
		StringTokenizer st = new StringTokenizer(str, ": ");
		ModuleInformation module;

		if (st.countTokens() == 1) {
			moduleName = st.nextToken();
			//	    configurationFile = st.nextToken();
			//	    modulesStatus = st.nextToken();
			module = new ModuleInformation(moduleName, "", 2);
			modulesTree.addModule(module);
		} // if

	} //addModule

	public void initGUI() {

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.getViewport().add(modulesTree.getTree());

		Action uploadAction = new AbstractAction(" Upload ") {
			public void actionPerformed(ActionEvent evt) {
					//                System.out.println ("Upload action");
	uploadAction();
			} //actionPerformed
		}; //modifyAction
		uploadButton = new JButton(uploadAction);

		Action createModuleAction = new AbstractAction(" Create module ") {
			public void actionPerformed(ActionEvent evt) {
					//		System.out.println (" Create module ");
					createModule();
			} //actionPerformed
		}; //createModuleAction
		createModuleButton = new JButton(createModuleAction);

		JPanel uploadButtonPanel = new JPanel();
		uploadButtonPanel.setLayout(new GridLayout(3, 0));
		uploadButtonPanel.add(new JSeparator());

		JPanel butPanel = new JPanel();
		butPanel.setLayout(new FlowLayout());
		butPanel.add(uploadButton);
		uploadButtonPanel.add(butPanel);

		JPanel createModulePanel = new JPanel();
		createModulePanel.setLayout(new FlowLayout());
		createModulePanel.add(createModuleButton);
		uploadButtonPanel.add(createModulePanel);

		getContentPane().add(scrollPane);
		getContentPane().add(uploadButtonPanel);

		pack();

		if (parent != null) {
			Rectangle r = parent.getBounds();
			setLocation(
				(int) (r.getX() + r.getWidth() / 2 - 250),
				(int) (r.getY() + r.getHeight() / 2 - 100));
		} //if

		setSize(500, 200);
		busy = new BusyDialog(this);
		setVisible(true);
		
		//busy = new BusyDialog(this);

	} // initGUI

	public void createModule() {

		String command = "createmodule ";

		String fileName = null;
		File file = null;

		Object selected = modulesTree.getSelectedModule();
		try {

			ModuleInformation selectedModule = (ModuleInformation) selected;

			if (selectedModule != null) {

				String moduleName = selectedModule.moduleName;

				fileName =
					JOptionPane.showInputDialog(
						this,
						"Insert conf file name : ");

				if (fileName != null && !fileName.equals("")) {
					command =
						command
							+ lia.app.AppUtils.enc(moduleName)
							+ " "
							+ lia.app.AppUtils.enc(fileName);
					communicator.sendCommand(command);
					String response = communicator.receiveResponseLine();

					if (response == null) {
						setModal(false);
						setVisible(false);
						dispose();
						return;
					}

					if (response.startsWith("+OK")) {
						response = communicator.receiveResponseLine();
						while (true) {

							if (response == null) {
								setModal(false);
								setVisible(false);
								dispose();
								return;
							}

							if (response.equals(".")) {
								break;
							}
							response = communicator.receiveResponseLine();
						}

					} //if

					parent.refreshModulesTree();

					this.setModal(false);
					this.setVisible(false);
					dispose();
				} //if
			} else {
				JOptionPane.showMessageDialog(this, "No module selected .... ");
			}

		} catch (ClassCastException e) {
			JOptionPane.showMessageDialog(this, "No module selected .... ");

			//        e.printStackTrace ();
		} catch (Exception e) {
			this.setModal(false);
			this.setVisible(false);
			dispose();
		}

	} //createModule

	public void afterUpload () {
		if (response == null) {
			setModal(false);
			setVisible(false);
			dispose();
			return;
		}
		
		if (response != null && response.startsWith("+OK")) {
			JOptionPane.showMessageDialog(this, "Module uploaded");
		} else {
			JOptionPane.showMessageDialog(this, response);
			//	    System.out.println (response);
		} //if - else
		
		response =  null;
		String command = "availablemodules";
		communicator.sendCommand(command);
		System.out.println ("CreateModule ====> send availablemodule command .... ");
		try {
		response = communicator.receiveResponseLine();
		
		if (response == null) {
			setModal(false);
			setVisible(false);
			dispose();
			return;
		} // if

		if (response.startsWith("+OK")) {
			System.out.println ("Am facut cleartree");					
			modulesTree.clearTree();
			response = communicator.receiveResponseLine();

			while (true) {

				if (response == null) {
					System.out.println ("Response e null;") ;							
					setModal(false);
					setVisible(false);
					dispose();
					return;
				}

				if (response.equals(".")) {
					System.out.println ("Response e . ;") ;							
					break;
				}

				System.out.println ("CreateModule ==> add a module ====> "+response);						
				addModule(response);
				response = communicator.receiveResponseLine();
			} // while
			modulesTree.setExpanded();
		} //if
		} catch (Exception e) {
			e.printStackTrace ();
			JOptionPane.showMessageDialog(
					this,
					"Error during tree refresh operation");
			//		this.setVisible (false);
			this.setModal(false);
			this.setVisible(false);
			dispose();
			
		} // try - catch 
		
	} // afterUpload
	
	public void uploadAction() {
		String fileName = null;
		File file = null;

		JFileChooser fileChooser = new JFileChooser();
		int returnValue = fileChooser.showOpenDialog(this);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			fileName = fileChooser.getSelectedFile().getName();
			//System.out.println (" ====>FileName ====> :"+fileName);
			file = fileChooser.getSelectedFile();
			try {
				FileInputStream fis = new FileInputStream(file);
				StringBuilder sb = new StringBuilder();

				int r = 0;
				byte[] vb = new byte[1024];
				char[] vc = new char[1024];

				do {
					r = fis.read(vb, 0, vb.length);

					for (int i = 0; i < r; i++)
						vc[i] = (char) vb[i];

					if (r > 0)
						sb.append(vc, 0, r);
				} while (r != -1);

				String fileContent = sb.toString();
				//		System.out.println ("File content .... "+fileContent);

				String command =
					"upload "
						+ lia.app.AppUtils.enc(fileName)
						+ " "
						+ lia.app.AppUtils.enc(fileContent);
		
				dezactivate() ;
				WaitingDialog waitingDialog =
				new WaitingDialog(
						UPLOAD,
						command);
				(new Thread(waitingDialog)).start();

	
			} catch (Exception e) {
				JOptionPane.showMessageDialog(
					this,
					"Error during upload operation");
				//		this.setVisible (false);
				this.setModal(false);
				this.setVisible(false);
				dispose();

				e.printStackTrace();
			}

		} else {
			System.out.println("not approve action .... ");
		}

	} //uploadAction

	
	class WaitingDialog  implements Runnable {

		private String command;
		String titleN = "Waiting for a server response ....";
		int type;

		WaitingDialog(int type, String command) {
			//make modal dialog
			//super(f, title, true);
			this.command = command;
			this.type = type ;

		}

		public void run() {
			response = null;
			String response1 = null;
		
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
							
							break;
						} // if
					    
						if (!response1.equals(".")) {
						} else {
							break;
						}
						response1 = communicator.receiveResponseLine();
					} // while
				}

				busy.setVisible (false);
				
				if (type == UPLOAD ) {
					afterUpload();
				} // IF
				
				activate();
				
			} catch (Exception e) {
				response = null;
				e.printStackTrace();
				activate();
				// TODO - aici trebuie ceva sa afiseze asta ......
			} // try - catch

			
		}
	} //WaitingDialog
	
	
} //CreateModuleDialog class
