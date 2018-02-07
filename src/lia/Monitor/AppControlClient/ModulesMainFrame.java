package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.tree.DefaultMutableTreeNode;

import lia.app.AppUtils;

public class ModulesMainFrame extends javax.swing.JFrame {

	final Color color = new Color(227, 233, 255);

	private static final int CONFIG = 0;
	private static final int CONFIG_APP=1;
	private static final int EXEC=2;
	
	
	JPanel configureModule;
	JPanel configureApplication;
	JPanel buttons;

	JSeparator jSeparator1;
	JSeparator jSeparator2;
	JSeparator jSeparator3;
	JSeparator jSeparator4;
	JSeparator jSeparator5;

	JPanel configureButtons;
	JPanel ssrButtons;

	JButton ConfigureModule;
	JButton ConfigureApplication;
	JButton createModuleButton;
	JButton deleteModuleButton;

	JButton Restart;
	JButton Stop;
	JButton Start;
	JButton Execute;
	JPanel jPanel3;

	ModulesTree modulesArea;
	CommunicateMsg communicator;
	
	ModuleInformation mi ;
	
	BusyDialog busy ;

	public Object lock = new Object();

	String response;
	String response2;
	String confFile = "";

	JPanel jPanel1;
	JPanel executePanel;

	//JPanel test;

	// optionPane.createInternalFrame(desktop, "Modal");

	public ModulesMainFrame(CommunicateMsg communicator) {
		super("Control Application");

		this.communicator = communicator;
		modulesArea = new ModulesTree();
		initGUI();
		pack();
		setVisible(false);
		
		busy = new BusyDialog(this);
	}

	public void addModule(String str) {

		String moduleName = null;
		String configurationFile = null;
		String modulesStatus = null;
		StringTokenizer st = new StringTokenizer(str, ": ");
		ModuleInformation module;

		if (st.countTokens() == 3) {
			moduleName = st.nextToken();
			configurationFile = st.nextToken();
			modulesStatus = st.nextToken();
			module =
				new ModuleInformation(
					moduleName,
					configurationFile,
					(Integer.valueOf(modulesStatus)).intValue());
			modulesArea.addModule(module);
		}

	} //addModule

	public Object getSelectedModule() {

		return modulesArea.getSelectedModule();

	} //getSelectedModule

	public void initGUI() {
		try {

			URL file0 = TestBorderFill.class.getResource("border0.png");
			Border border0 =
				new BorderFill(ImageIO.read(file0), new Rectangle[] {
				/* 0 */
				new Rectangle(8, 0, 2, 5),
				/* 1 */
				new Rectangle(125, 0, 5, 5),
				/* 2 */
				new Rectangle(125, 8, 5, 2),
				/* 3 */
				new Rectangle(125, 50, 5, 5),
				/* 4 */
				new Rectangle(8, 50, 2, 5),
				/* 5 */
				new Rectangle(0, 50, 5, 5),
				/* 6 */
				new Rectangle(0, 8, 5, 2),
				/* 7 */
				new Rectangle(0, 0, 5, 5)},
					new boolean[] { true, true, true, true });
			Color background0 = color;

			//	test = new TestBorderFill(border0,background0);

			jPanel1 = new TestBorderFill(border0, background0);

			jPanel3 = new JPanel();
			executePanel = new JPanel();
			executePanel.setLayout(new FlowLayout());
			executePanel.setBackground(color);
			ssrButtons = new TestBorderFill(border0, background0);
			jSeparator1 = new JSeparator();
			buttons = new JPanel();

			Action actionDeleteModule = new AbstractAction("Delete Module") {
				public void actionPerformed(ActionEvent evt) {
					deleteModuleButtonAction();
				} //actionPerformed
			};

			deleteModuleButton = new RoundButton(actionDeleteModule);

			Action actionCreateModule = new AbstractAction("Create Module") {
				public void actionPerformed(ActionEvent evt) {

	createModuleButtonAction();
				} //actionPerformed
			};

			createModuleButton = new RoundButton(actionCreateModule);
			//	createModuleButton.setBackground (colorButtons);

			Action actionExecute = new AbstractAction("Execute Command") {
				public void actionPerformed(ActionEvent evt) {

	executeButtonAction();
				} //actionPerformed
			};
			Execute = new RoundButton(actionExecute);

			executePanel.add(Execute);

			Action actionStart = new AbstractAction("Start") {
				public void actionPerformed(ActionEvent evt) {

	startButtonAction();
				} //actionPerformed
			};
			Start = new RoundButton(actionStart);

			Action actionRestart = new AbstractAction("Restart") {
				public void actionPerformed(ActionEvent evt) {

	restartButtonAction();
				} //actionPerformed
			};
			Restart = new RoundButton(actionRestart);
			//	Restart.setBackground(colorButtons);
			//			Restart.setIcon(new
			// ImageIcon("lia/images/appControl/restart.gif"));

			Action actionStop = new AbstractAction("Stop") {
				public void actionPerformed(ActionEvent evt) {

	stopButtonAction();
				} //actionPerformed
			};
			Stop = new RoundButton(actionStop);
			//Stop.setBackground(colorButtons);
			//			Stop.setIcon(new ImageIcon("lia/images/appControl/stop.gif"));

			configureButtons = new TestBorderFill(border0, background0);
			jSeparator2 = new JSeparator();
			configureApplication = new JPanel();

			Action actionConfigureApplication =
				new AbstractAction("ConfigureApplication") {
				public void actionPerformed(ActionEvent evt) {
						//								System.out.println("CONFIGURE APPLICATION ACTION");
	configureApplicationButtonAction();
				} //actionPerformed
			};
			ConfigureApplication = new RoundButton(actionConfigureApplication);
			//		ConfigureApplication.setBackground(colorButtons);
			//ConfigureApplication.setIcon (new
			// ImageIcon("configureApplication.gif"));

			//			System.out.println(
			//				"Here trying to create the panel in the new mode ..... ");

			configureModule = new JPanel();

			//			System.out.println("Panel Created ....");

			Action actionConfigureModule =
				new AbstractAction("ConfigureModule") {
				public void actionPerformed(ActionEvent evt) {
						//					System.out.println("CONFIGURE APPLICATION ACTION");
	configureModuleButtonAction();
				} //actionPerformed
			};

			ConfigureModule = new RoundButton(actionConfigureModule);
			//		ConfigureModule.setBackground(colorButtons);
			GridLayout thisLayout = new GridLayout(0, 2);
			this.getContentPane().setLayout(thisLayout);
			thisLayout.setHgap(5);
			thisLayout.setVgap(5);
			thisLayout.setColumns(2);
			thisLayout.setRows(0);
			this.setResizable(true);
			this.getContentPane().setSize(new java.awt.Dimension(542, 358));
			BorderLayout jPanel1Layout = new BorderLayout();
			jPanel1.setLayout(jPanel1Layout);
			jPanel1.setVisible(true);
			this.getContentPane().add(jPanel1);

			JScrollPane jsp = new JScrollPane(modulesArea.getTree());
			//			jsp.getViewport().add(modulesArea.getTree());

			jPanel1.add(jsp);
			GridLayout jPanel3Layout = new GridLayout(2, 1);
			jPanel3.setLayout(jPanel3Layout);
			jPanel3Layout.setRows(2);
			jPanel3.setVisible(true);
			jPanel3.setMinimumSize(new java.awt.Dimension(400, 350));
			jPanel3.setPreferredSize(new java.awt.Dimension(400, 350));
			jPanel3.setBorder(new BevelBorder(1, null, null, null, null));
			jPanel3.setMaximumSize(new java.awt.Dimension(400, 350));
			this.getContentPane().add(jPanel3);
			GridLayout ssrButtonsLayout = new GridLayout(3, 3);
			ssrButtons.setLayout(ssrButtonsLayout);
			ssrButtonsLayout.setColumns(3);
			ssrButtonsLayout.setRows(3);
			ssrButtons.setVisible(true);
			jPanel3.add(ssrButtons);
			jSeparator1.setLayout(null);
			jSeparator1.setVisible(true);
			ssrButtons.add(jSeparator1);

			buttons.setLayout(new FlowLayout());
			buttons.setBackground(color);
			Start.setVisible(true);
			buttons.add(Start);

			Stop.setVisible(true);
			buttons.add(Stop);

			Restart.setVisible(true);
			buttons.add(Restart);

			ssrButtons.add(buttons);
			//ssrButtons.add(restartPanel);
			ssrButtons.add(executePanel);

			GridLayout configureButtonsLayout = new GridLayout(4, 4);
			configureButtons.setLayout(configureButtonsLayout);
			configureButtonsLayout.setColumns(4);
			configureButtonsLayout.setRows(4);
			configureButtons.setVisible(true);
			jPanel3.add(configureButtons);
			jSeparator2.setLayout(null);
			jSeparator2.setVisible(true);

			configureButtons.add(jSeparator2);

			JPanel createModulePanel = new JPanel();
			createModulePanel.setBackground(color);
			createModulePanel.setLayout(new FlowLayout());
			createModulePanel.add(createModuleButton);
			createModulePanel.add(deleteModuleButton);
			configureButtons.add(createModulePanel);
			FlowLayout configureApplicationLayout = new FlowLayout();
			configureApplication.setLayout(configureApplicationLayout);
			configureApplication.setBackground(color);
			configureApplication.setVisible(true);
			configureButtons.add(configureApplication);
			ConfigureApplication.setText("ConfigureApplication");
			ConfigureApplication.setVisible(true);
			ConfigureApplication.setPreferredSize(
				new java.awt.Dimension(172, 29));
			ConfigureApplication.setBounds(
				new java.awt.Rectangle(44, 5, 172, 29));
			configureApplication.add(ConfigureApplication);
			FlowLayout configureModuleLayout = new FlowLayout();
			configureModule.setLayout(configureModuleLayout);
			configureModule.setBackground(color);
			configureModule.setVisible(true);
			configureButtons.add(configureModule);
			ConfigureModule.setText("ConfigureModule");
			ConfigureModule.setVisible(true);
			ConfigureModule.setPreferredSize(new java.awt.Dimension(172, 29));
			ConfigureModule.setBounds(new java.awt.Rectangle(44, 5, 172, 29));
			configureModule.add(ConfigureModule);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void connectionClosed() {
		this.setVisible(false);
		JOptionPane.showMessageDialog(this, "Connection down");
	}

	public void refreshModulesTree() throws Exception {

		//System.out.println (" =======> refreshModulesTree .... ");

		modulesArea.clearTree();

		communicator.sendCommand("loadedmodules");
		String raspuns = communicator.receiveResponseLine();

		if (raspuns == null) {
			connectionClosed();
			return;
		}

		if (raspuns.startsWith("+OK")) {

			raspuns = communicator.receiveResponseLine();

			while (true) {
				if (!raspuns.equals(".")) {
					addModule(raspuns);
				} else {
					break;
				}
				raspuns = communicator.receiveResponseLine();
			} //while
			modulesArea.setExpanded();
		} else {
			System.out.println("Error trying to read from socket .... ");
		} //if - else

	} //refreshModulesTree

	public synchronized void deleteModuleButtonAction() {
		//System.out.println ("deleteModuleButtonAction .... ");
		confFile = "";

		Object selectedModule = getSelectedModule();
		try {
			if (selectedModule != null) { //e un modul selectat
				 mi = null;
				try {
					mi = (ModuleInformation) selectedModule;
				} catch (Exception e) {
					JOptionPane.showMessageDialog(this, "No module selected");
					return;
				}
				String command =
					"deletemodule "
						+ lia.app.AppUtils.enc(mi.moduleName)
						+ " "
						+ lia.app.AppUtils.enc(mi.configurationFile);
				communicator.sendCommand(command);
				String raspuns = communicator.receiveResponseLine();

				if (raspuns == null) { // broken connection ....
					connectionClosed();
					return;
				} // if

				if (raspuns.startsWith("+OK")) {
					raspuns = communicator.receiveResponseLine();

					while (true) {

						if (raspuns == null) { // broken connection
							// ....
							connectionClosed();
							return;
						} // if

						if (raspuns.equals(".")) {
							break;
						}
						raspuns = communicator.receiveResponseLine();
					} // while
					refreshModulesTree();
				} else {
					JOptionPane.showMessageDialog(
						this,
						"Error trying to delete module ....");
					System.out.println(raspuns);
				}

			} //if
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"Exception trying to delete module ....");
			e.printStackTrace();
		}
	} //deleteModuleButtonAction

	public synchronized void createModuleButtonAction() {

		String command = "availablemodules";
		communicator.sendCommand(command);

		CreateModuleDialog cmd =
			new CreateModuleDialog("CreateModule", this, communicator);
		try {
			String availableResponse = communicator.receiveResponseLine();

			if (availableResponse == null) { // broken
				// connection
				// ....
				connectionClosed();
				return;
			} // if

			if (availableResponse.startsWith("+OK")) {
				availableResponse = communicator.receiveResponseLine();
				while (true) {
					if (availableResponse == null) {
						connectionClosed();
						return;
					}
					if (!availableResponse.equals(".")) {
						cmd.addModule(availableResponse);
					} else {
						break;
					}
					availableResponse = communicator.receiveResponseLine();
				} // while
				cmd.initGUI();
			}

			cmd = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	} //createModuleButtonAction

	public void afterConfigure() {
		
			if (response == null) {
				connectionClosed();
				return;
			}

			if (response.startsWith("+OK")) {
				String message = "Got application configuration";

				UpdateConfigurationDialog ci =
					new UpdateConfigurationDialog(
						"UpdateConfiguration",
						this,
						true,
						confFile,
						communicator,
						mi.moduleName,
						mi.configurationFile);
				//				ci.appendText (confFile);
			} else {
				JOptionPane.showMessageDialog(
					this,
					"Error while trying to start module");
			} //if - else

	
	} // afterConfigure
	
	public synchronized void configureModuleButtonAction() {

		confFile = "";

		Object selectedModule = getSelectedModule();
		if (selectedModule != null) { //e un modul selectat
			mi = null;
			try {
				mi = (ModuleInformation) selectedModule;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "No module selected");
				return;
			}
			String command =
				"getconfig "
					+ lia.app.AppUtils.enc(
						mi.moduleName + ":" + mi.configurationFile);

			dezactivate();
			SendCommandThread waitingWindow = new SendCommandThread(CONFIG,command);
			Thread runn = new Thread(waitingWindow);
			(runn).start();

	} else {
			JOptionPane.showMessageDialog(this, "No module selected ...");

		}

	} //configureModuleButtonAction

	public void afterConfigureApp() {
		if (response == null) {
			
			connectionClosed();
			return;
		}

		if (!confFile.equals("")) {
			String message = "Got application configuration";

			//								while (((response =
			// communicator.receiveResponseLine())!= null) &&
			// !response.equals(".")) {
			//									confFile += response;
			//								} //while

			ModuleConfigurationDialog ci =
			new ModuleConfigurationDialog(
					this,
					"Configure application",
					confFile,
					mi.moduleName,
					mi.configurationFile,
					communicator);
			//		ci.showGUI();
		} else {
			JOptionPane.showMessageDialog(
					this,
					"Error while trying to start module");
		} //if - else
		
	} // afterConfigureApp
	
	public synchronized void configureApplicationButtonAction() {

		confFile = "";

		Object selectedModule = getSelectedModule();
		try {
			mi = null;
			if (selectedModule != null) { //e un modul selectat
				 mi = (ModuleInformation) selectedModule;
				String command =
					"info "
						+ lia.app.AppUtils.enc(
							mi.moduleName + ":" + mi.configurationFile);

				try {

					dezactivate();
					SendCommandThread waitingWindow = new SendCommandThread(CONFIG_APP,command);
					(new Thread(waitingWindow)).start();
				
				} catch (Exception e) {
					JOptionPane.showMessageDialog(
							this,
							"Exception while trying to start module");

				}
				

			} else {
				JOptionPane.showMessageDialog(
					this,
					"No module selected ... please select one");
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"No module selected ... please select one");
		}
	} //configureApplicationButtonAction

	public void afterExecute() {
		if (response == null) { // broken connection ....
			connectionClosed();
			return;
		} // if

		if (response.startsWith("+OK")) {
			String message = response2;
			new ExecuteCommandDialog(
					"Command response",
					this,
					message);
		} else {
			JOptionPane.showMessageDialog(
					this,
					"Error executing command");
		} //if - else
		
	} // afterExecute
	
	public synchronized void executeButtonAction() {
		Object selectedModule = getSelectedModule();
		try {
			mi=null;
			if (selectedModule != null) { //e un modul selectat
				 mi = (ModuleInformation) selectedModule;
				String command =
					"exec "
						+ lia.app.AppUtils.enc(
							mi.moduleName + ":" + mi.configurationFile)
						+ " ";
				String commandToExecute =
					JOptionPane.showInputDialog(this, "Insert command");
				if (commandToExecute == null)
					return;
				command += AppUtils.enc(commandToExecute);
				try {

					dezactivate();
					SendCommandThread waitingWindow = new SendCommandThread(EXEC,command);
					(new Thread(waitingWindow)).start();

				} catch (Exception e) {
					JOptionPane.showMessageDialog(
						this,
						"Exception while command execution");

				}

			} else {
				JOptionPane.showMessageDialog(
					this,
					"No module selected ... please select one");
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"No module selected ... please select one");
		}

		//		String commandToExecute = JOptionPane.showInputDialog(this,"Insert
		// command");
	} //executeButtonAction

	public synchronized void startButtonAction() {

		DefaultMutableTreeNode mutableTreeNode =
			modulesArea.getSelectedDefaultMutableTree();
		Object selectedModule = getSelectedModule();
		try {
			mi = null;
			if (selectedModule != null) { //e un modul selectat
				 mi = (ModuleInformation) selectedModule;
				String command =
					"start "
						+ lia.app.AppUtils.enc(
							mi.moduleName + ":" + mi.configurationFile);

				try {

					communicator.sendCommand(command);
					response = communicator.receiveResponseLine();
					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {
						while (true) {
							response = communicator.receiveResponseLine();
							if (response == null) {
								connectionClosed();
								return;
							} // if

							if (response.equals(".")) {
								JOptionPane.showMessageDialog(
									this,
									"Module started");
								break;
							} // if
						} // while

					} else {

						JOptionPane.showMessageDialog(
							this,
							"Error while trying to start module");
					} //if - else

					command =
						"status "
							+ lia.app.AppUtils.enc(
								mi.moduleName + ":" + mi.configurationFile);

					communicator.sendCommand(command);
					response = communicator.receiveResponseLine();

					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {
						response = communicator.receiveResponseLine();

						while (true) {

							if (response == null) { // broken
								// connection
								// ....
								connectionClosed();
								return;
							} // if

							if (!response.equals(".")) {
								//				    	System.out.println (response);
								if (response.equals("0")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											0);
									mutableTreeNode.setUserObject(modInf);

								}
								if (response.equals("1")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											1);
									mutableTreeNode.setUserObject(modInf);

								}
								if (response.equals("2")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											2);
									mutableTreeNode.setUserObject(modInf);

								}
								modulesArea.refresh();
							} else {
								break;
							} // if - else

							response = communicator.receiveResponseLine();
						} //while
					}

				} catch (Exception e) {
					JOptionPane.showMessageDialog(
						this,
						"Exception while trying to start module");

				}

			} else {
				JOptionPane.showMessageDialog(
					this,
					"No module selected ... please select one");
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"No module selected ... please select one");
		}

	} //startButtonAction

	public synchronized void stopButtonAction() {

		DefaultMutableTreeNode mutableTreeNode =
			modulesArea.getSelectedDefaultMutableTree();

		Object selectedModule = getSelectedModule();
		try {
			mi = null;
			if (selectedModule != null) { //e un modul selectat
				 mi = (ModuleInformation) selectedModule;
				String command =
					"stop "
						+ lia.app.AppUtils.enc(
							mi.moduleName + ":" + mi.configurationFile);

				try {
					/*
					 * WaitingDialog waitingWindow = new WaitingDialog( this,
					 * "Wait", "Waiting for a server response", command); (new
					 * Thread(waitingWindow)).start(); waitingWindow.dispose();
					 */

					communicator.sendCommand(command);

					response = communicator.receiveResponseLine();

					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {
						while (true) {
							response = communicator.receiveResponseLine();

							if (response == null) {
								connectionClosed();
								return;
							} // if

							if (response.equals(".")) {
								JOptionPane.showMessageDialog(
									this,
									"Module stoped");
								break;
							} //if

						} // while
						//JOptionPane.showMessageDialog(this, message);
					} else {
						JOptionPane.showMessageDialog(
							this,
							"Error while trying to stop module");
					} //if - else

					command =
						"status "
							+ lia.app.AppUtils.enc(
								mi.moduleName + ":" + mi.configurationFile);

					communicator.sendCommand(command);
					response = communicator.receiveResponseLine();

					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {
						response = communicator.receiveResponseLine();
						while (true) {
							if (response == null) { // broken
								// connection
								// ....
								connectionClosed();
								return;
							} // if

							if (!response.equals(".")) {

								//				    	System.out.println (response);
								if (response.equals("0")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											0);
									mutableTreeNode.setUserObject(modInf);
								}
								if (response.equals("1")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											1);
									mutableTreeNode.setUserObject(modInf);

								}
								if (response.equals("2")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											2);
									mutableTreeNode.setUserObject(modInf);

								}
								modulesArea.refresh();
							} else {
								break;
							} // if - else
							response = communicator.receiveResponseLine();
						} //while
					}

				} catch (Exception e) {
					JOptionPane.showMessageDialog(
						this,
						"Exception while trying to stop module");
					//catch exception
				}

			} else {
				JOptionPane.showMessageDialog(
					this,
					"No module selected ... please select one");
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"No module selected ... please select one");
		}

	}

	public synchronized void restartButtonAction() {

		DefaultMutableTreeNode mutableTreeNode =
			modulesArea.getSelectedDefaultMutableTree();

		Object selectedModule = getSelectedModule();
		try {
			mi = null;
			if (selectedModule != null) { //e un modul selectat
				 mi = (ModuleInformation) selectedModule;
				String command =
					"restart "
						+ lia.app.AppUtils.enc(
							mi.moduleName + ":" + mi.configurationFile);

				try {

					communicator.sendCommand(command);
					response = communicator.receiveResponseLine();

					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {
						while (true) {
							response = communicator.receiveResponseLine();

							if (response == null) { // broken
															  // connection
															  // ....
								connectionClosed();
								return;
							} // if

							if (response.equals(".")) {
								JOptionPane.showMessageDialog(
									this,
									"Module restarted");
								break;
							} // if

						} // while

					} else {
						JOptionPane.showMessageDialog(
							this,
							"Error while trying to restart module " + response);
					} //if - else

					command =
						"status "
							+ lia.app.AppUtils.enc(
								mi.moduleName + ":" + mi.configurationFile);

					communicator.sendCommand(command);
					response = communicator.receiveResponseLine();

					if (response == null) { // broken connection ....
						connectionClosed();
						return;
					} // if

					if (response.startsWith("+OK")) {

						response = communicator.receiveResponseLine();

						while (true) {

							if (response == null) { // broken
								// connection
								// ....
								connectionClosed();
								return;
							} // if

							if (!response.equals(".")) {
								//				    	System.out.println (response);
								if (response.equals("0")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											0);
									mutableTreeNode.setUserObject(modInf);
								}
								if (response.equals("1")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											1);
									mutableTreeNode.setUserObject(modInf);

								}
								if (response.equals("2")) {
									ModuleInformation modInf =
										new ModuleInformation(
											mi.moduleName,
											mi.configurationFile,
											2);
									mutableTreeNode.setUserObject(modInf);

								}
								modulesArea.refresh();
							} else {
								break;
							}

							response = communicator.receiveResponseLine();

						} //while
					}

				} catch (Exception e) {
					JOptionPane.showMessageDialog(
						this,
						"Exception while trying to restart module");
					//catch exception
				}

			} else {
				JOptionPane.showMessageDialog(
					this,
					"No module selected ... please select one");
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				this,
				"No module selected ... please select one");
		}
	}

	public void showGUI() {
		try {
			initGUI();
			pack();
			setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void dezactivate() {

		
		ConfigureModule.setEnabled(false);
		ConfigureApplication.setEnabled(false);
		createModuleButton.setEnabled(false);
		deleteModuleButton.setEnabled(false);
		Start.setEnabled(false);
		Stop.setEnabled(false);
		Restart.setEnabled(false);
		Execute.setEnabled(false);
		busy.setVisible (true);
	} // dezactivate

	public void activate() {
		ConfigureModule.setEnabled(true);
		ConfigureApplication.setEnabled(true);
		createModuleButton.setEnabled(true);
		deleteModuleButton.setEnabled(true);
		//Start.setEnabled(true);
		Stop.setEnabled(true);
		Restart.setEnabled(true);
		Execute.setEnabled(true);
		Start.setEnabled(true);
	
	} // dezactivate

	class SendCommandThread implements Runnable {

		private String command;
		private int type ;

		SendCommandThread(int type,String command) {
			//make modal dialog
			//super(f, title, true);
			this.type = type;
			this.command = command;
		} // WaitingDialog

		public void run() {
			response = null;
			String response1 = null;
			response2 = null;
			try {
				
				communicator.sendCommand(command);
				
				response = communicator.receiveResponseLine();

				if (response == null) {

					//setModal(false);
					//			setVisible(false);
					//					dispose();
					return;
				}

				if (response.startsWith("+OK")) {

					while (true) {
						response1 = communicator.receiveResponseLine();

						//error on the network . break
						if (response1 == null) {
							
							response = null;
							break;
						} //if

						if (!response1.equals(".")) {
							if (response2 == null)
								response2 = "";
							response2 += response1 + "\n";
							confFile += response1 + "\n";
						} else {
							break;
						}
					} //while

				} else {
					confFile = "";
				} //if - else

				busy.setVisible(false);
				
				if (type==CONFIG) {
					afterConfigure();
				} // if
				
				if (type==CONFIG_APP) {
					afterConfigureApp();
				} // if
				
				if (type==EXEC) {
					afterExecute();
				} // if
				
				activate();

			} catch (Exception e) {
				e.printStackTrace();


				activate();
				// TODO - aici trebuie ceva sa afiseze asta ......
			}

		}
	} //

}
