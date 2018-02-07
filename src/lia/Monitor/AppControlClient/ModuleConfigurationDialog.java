package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.border.CompoundBorder;

public class ModuleConfigurationDialog extends javax.swing.JDialog {

	JButton Modify;
	JSeparator jSeparator2;
	JSeparator jSeparator1;
	JPanel jPanel2;
	ShowAttribute jPanel1;
	JTextField modifyField;
	//JTree configurationTree;
	JPanel modifyPanel;
	JPanel treePanel;

	ConfigurationTree graphicalTree;
	JTree confTree;

	String stringToParse;
	String moduleName;
	String configurationFile;
	String confFile;
	CommunicateMsg communicator;

	public ModuleConfigurationDialog(
		JFrame f,
		String title,
		String stringToParse,
		String moduleName,
		String configurationFile,
		CommunicateMsg communicator) {
		super(f, title, true);
		this.stringToParse = stringToParse;
		this.moduleName = moduleName;
		this.configurationFile = configurationFile;
		this.communicator = communicator;

		initGUI(f);
	}

	public void dezactivate() {
		Modify.setEnabled(false);
	}

	public void activate() {
		Modify.setEnabled(true);
	}

	public void initGUI(JFrame f) {
		try {
			treePanel = new JPanel();

			modifyPanel = new JPanel();
			jPanel1 = new ShowAttribute();

			graphicalTree =
				new ConfigurationTree(
					jPanel1,
					stringToParse,
					moduleName,
					configurationFile,
					this,
					communicator,
					this);

			modifyField = new JTextField();
			jSeparator1 = new JSeparator();
			jSeparator2 = new JSeparator();
			jPanel2 = new JPanel();

			Action actionModify = new AbstractAction("Modify") {
				public void actionPerformed(ActionEvent ae) {
					modifyButtonAction();
				} //actionPerformed
			};

			Modify = new JButton(actionModify);
			GridLayout thisLayout = new GridLayout(0, 2);
			this.getContentPane().setLayout(thisLayout);
			thisLayout.setColumns(2);
			thisLayout.setRows(0);
			this.setResizable(true);
			this.setTitle("Configuration");
			this.setModal(true);
			this.setName("Configuration");
			this.getContentPane().setSize(new java.awt.Dimension(600, 600));
			BorderLayout treePanelLayout = new BorderLayout();
			treePanel.setLayout(treePanelLayout);
			treePanel.setVisible(true);
			treePanel.setBorder(new CompoundBorder(null, null));
			this.getContentPane().add(treePanel);

			confTree = null;
			confTree = graphicalTree.createGraphicalTree();

			JScrollPane scrollPane = new JScrollPane();
			scrollPane.getViewport().add(confTree);
			treePanel.add(scrollPane);

			GridLayout modifyPanelLayout = new GridLayout(2, 1);
			modifyPanel.setLayout(modifyPanelLayout);
			modifyPanelLayout.setRows(2);
			modifyPanel.setVisible(true);
			modifyPanel.setBorder(new CompoundBorder(null, null));
			this.getContentPane().add(modifyPanel);

			modifyPanel.add(jPanel1);
			FlowLayout jPanel2Layout = new FlowLayout();

			jPanel2.setLayout(jPanel2Layout);
			jPanel2.setVisible(true);
			modifyPanel.add(jPanel2);
			Modify.setText("Modify");
			Modify.setVisible(true);
			Modify.setPreferredSize(new java.awt.Dimension(116, 29));
			Modify.setBounds(new java.awt.Rectangle(51, 5, 116, 29));
			jPanel2.add(Modify);

			pack();

			Rectangle r = f.getBounds();

			setLocation(
				(int) (r.getX() + r.getWidth() / 2 - 300),
				(int) (r.getY() + r.getHeight() / 2 - 200));
			setSize(600, 400);
			setVisible(true);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void modifyButtonAction() {
		

		String command =
			"update "
				+ lia.app.AppUtils.enc(moduleName + ":" + configurationFile)
				+ " ";
		String name = "";
		String value = "";
		String line = "";
		String fileName = "";
		String lastValue = "";

		Object nodeObject = graphicalTree.getSelectedNode();
		if (nodeObject != null) {
			Tree o = (Tree) nodeObject;
			if (o.attNames != null) {
				for (int i = 0; i < o.attNames.size(); i++) {
					String att = (String) (o.attNames.elementAt(i));
					if (att.equals("name"))
						name =
							lia.app.AppUtils.dec(
								(String) o.attValues.elementAt(i));
					if (att.equals("value"))
						value =
							lia.app.AppUtils.dec(
								(String) o.attValues.elementAt(i));
					if (att.equals("line"))
						line =
							lia.app.AppUtils.dec(
								(String) o.attValues.elementAt(i));

				} //for
			} //if

			fileName = graphicalTree.getFileName();
			lastValue = jPanel1.getText();
			if (lastValue == null || lastValue.equals("")) {
				lastValue = " ";
			}

			command
				+= lia.app.AppUtils.enc(
					lia.app.AppUtils.enc(fileName)
						+ " "
						+ line
						+ " "
						+ lia.app.AppUtils.enc(name)
						+ " "
						+ "update"
						+ " "
						+ lia.app.AppUtils.enc(lastValue));
			try {
				communicator.sendCommand(command);

				String raspuns = communicator.receiveResponseLine();
				if (raspuns == null) {
					setModal(false);
					setVisible(false);
					dispose();
					return;
				}

				if (raspuns.startsWith("+OK")) {
					String message = "";
					raspuns = communicator.receiveResponseLine();
					while (true) {
						if (raspuns == null) {
							setModal(false);
							setVisible(false);
							dispose();
							return;
						}
						if (raspuns.equals(".")) {
							break;
						}
						message = message + raspuns;
						raspuns = communicator.receiveResponseLine();
					}

					confFile = graphicalTree.getConfFile();
					//confFile = graphicalTree.
					if (confFile != null) {
						graphicalTree.parser.setStringToParse(confFile);
						graphicalTree.clearTree();
						graphicalTree.reconstructTree();
					}
				}
			} catch (Exception e) {
				this.setModal(false);
				this.setVisible(false);
				dispose();
				e.printStackTrace();
			}
		} else {
			System.out.println("No tree node selected ....");
		}

	} //modifyButtonAction

	public void connectionClosed() {
		setModal(false);
		setVisible(false);
		dispose();
	} // connectionClosed

}
