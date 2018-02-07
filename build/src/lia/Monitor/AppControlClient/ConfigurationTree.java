package lia.Monitor.AppControlClient;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class ConfigurationTree implements ActionListener {

	public XMLFileParse parser;
	private JTree graphicalTree;

	private SelectionNotifier selectionNotifier;
	final JPopupMenu menu = new JPopupMenu();

	private int Xmenu = 0;
	private int Ymenu = 0;

	private String moduleName;
	private String configurationFile;
	private JDialog parentFrame;
	private CommunicateMsg communicator;

	protected DefaultTreeModel treeModel;
	private DefaultMutableTreeNode root;

	private String response;
	String confFile = "";
	String fileName = null;

	ModuleConfigurationDialog mcd;

	JMenuItem renameItem;
	JMenuItem deleteItem;
	JMenuItem insertItem;
	JMenuItem insertSectionItem;

	final Color color = new Color(227, 233, 255); //255,247,216);

	public ConfigurationTree(
		SelectionNotifier selectionNotifier,
		String stringToParse,
		String moduleName,
		String configurationFile,
		JDialog parentFrame,
		CommunicateMsg communicator,
		ModuleConfigurationDialog mcd) {

		this.mcd = mcd;
		this.selectionNotifier = selectionNotifier;
		this.moduleName = moduleName;
		this.configurationFile = configurationFile;
		this.parentFrame = parentFrame;
		this.communicator = communicator;

		parser = new XMLFileParse(stringToParse);

		// Create and add a menu item

		renameItem = new JMenuItem("Rename");
		renameItem.addActionListener(this);
		deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(this);
		insertItem = new JMenuItem("Insert");
		insertItem.addActionListener(this);
		insertSectionItem = new JMenuItem("Insert Section ");
		insertSectionItem.addActionListener(this);

		menu.add(renameItem);
		menu.add(deleteItem);
		menu.add(insertItem);
		menu.add(insertSectionItem);

	} //constructor

	public void dezactivateModify() {
		mcd.dezactivate();
	}
	
	public void activateModify() {
		mcd.activate();
	}
	
	public void dezactivate() {
		renameItem.setEnabled(false);
		deleteItem.setEnabled(false);
		insertItem.setEnabled(false);
		insertSectionItem.setEnabled(false);
		mcd.dezactivate();
	}

	public void activate() {
		renameItem.setEnabled(true);
		deleteItem.setEnabled(true);
		insertItem.setEnabled(true);
		insertSectionItem.setEnabled(true);
		mcd.activate();
	}

	public void clearTree() {
		root.removeAllChildren();
		treeModel.reload();
	} //clearTree

	public void afterConf() {
		if (response.startsWith("+OK")) {
			String message = "Got application configuration";

			//turn confFile ;
		} else {
			JOptionPane.showMessageDialog(
				parentFrame,
				"Error while trying to get module configuration");
			confFile = null;
		} //if - else

	}

	public String getConfFile() {

		String command =
			"info "
				+ lia.app.AppUtils.enc(moduleName + ":" + configurationFile);
		confFile = "";
		try {

			response = null;
			String response1 = null;

			communicator.sendCommand(command);
			response = communicator.receiveResponseLine();

			if (response == null) {
				//				setModal (false);
				//				setVisible (false);
				return null;
			}

			if (response.startsWith("+OK")) {
				//						String message = "Got application configuration";

				response1 = communicator.receiveResponseLine();
				while (true) {
					if (response1 == null) {
						response = null;
						//								setModal(false);
						//								setVisible(false);
						return null;
					}

					if (!response1.equals(".")) {

						confFile += response1;
					} else {
						break;
					}
					response1 = communicator.receiveResponseLine();
				}

			} else {
				confFile = "";
			} //if - else

			afterConf();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(
				parentFrame,
				"Exception while trying to get module configuration");
			confFile = null;
		}
		return confFile;
	} //getConfFile

	public void actionPerformed(ActionEvent ae) {

		if (ae.getActionCommand().equals("Rename")) {
			menuOperation("rename");
		}

		if (ae.getActionCommand().equals("Delete")) {
			menuOperation("delete");
		}

		if (ae.getActionCommand().equals("Insert")) {
			menuOperation("insert");
		}

		if (ae.getActionCommand().startsWith("Insert Section")) {
			menuOperation("insertsection");
		}

	} //actionPerformed

	public void menuOperation(String operation) {

		String command =
			"update "
				+ lia.app.AppUtils.enc(moduleName + ":" + configurationFile)
				+ " ";
		String name = "";
		String value = "";
		String line = "";
		String lastValue = "";

		TreePath path = graphicalTree.getPathForLocation(Xmenu, Ymenu);
		Object[] objectPath = path.getPath();
		DefaultMutableTreeNode selectedNode =
			(DefaultMutableTreeNode) path.getLastPathComponent();

		Tree o = (Tree) selectedNode.getUserObject();

		if (o.attNames != null) {
			for (int i = 0; i < o.attNames.size(); i++) {
				String att = (String) (o.attNames.elementAt(i));
				if (att.equals("name"))
					name =
						lia.app.AppUtils.dec((String) o.attValues.elementAt(i));
				if (att.equals("value"))
					value =
						lia.app.AppUtils.dec((String) o.attValues.elementAt(i));
				if (att.equals("line"))
					line =
						lia.app.AppUtils.dec((String) o.attValues.elementAt(i));
			}
		}

		selectedNode = (DefaultMutableTreeNode) objectPath[1];

		Tree file = (Tree) selectedNode.getUserObject();
		String fileName = "";
		for (int i = 0; i < file.attNames.size(); i++) {
			if ((((String) file.attNames.elementAt(i)).equals("name"))) {
				fileName = (String) file.attValues.elementAt(i);
				break;
			}
		} //for

		if (!operation.equals("delete")) {
			lastValue =
				JOptionPane.showInputDialog(parentFrame, operation + "value");
			if (lastValue == null || lastValue.length() <= 0)
				return;
		} // if

		command
			+= lia.app.AppUtils.enc(
				lia.app.AppUtils.enc(fileName)
					+ " "
					+ line
					+ " "
					+ lia.app.AppUtils.enc(name)
					+ " "
					+ operation
					+ " "
					+ lia.app.AppUtils.enc(lastValue));
		communicator.sendCommand(command);
		try {
			String raspuns = communicator.receiveResponseLine();

			if (raspuns == null) {
				mcd.connectionClosed();
			}

			if (raspuns.startsWith("+OK")) {
				String message = "";
				raspuns = communicator.receiveResponseLine();

				while (true) {

					if (raspuns == null) {
						mcd.connectionClosed();
					}

					if (!raspuns.equals(".")) {

						message = message + raspuns;
					} else {
						break;
					}

					raspuns = communicator.receiveResponseLine();
				} // while

				confFile = getConfFile();
				if (confFile != null) {
					parser.setStringToParse(confFile);
					clearTree();
					reconstructTree();
				} //if
			} //if
		} catch (Exception e) {
			e.printStackTrace();
		}
	} //menuOperation

	public Object getSelectedNode() {
		TreePath path = graphicalTree.getSelectionPath();
		Object node = null;

		if (path != null) {
			DefaultMutableTreeNode dmtn =
				(DefaultMutableTreeNode) (path.getLastPathComponent());
			node = dmtn.getUserObject();
		}

		if (fileName == null) {
			Object[] objectPath = path.getPath();
			DefaultMutableTreeNode selectedNode =
				(DefaultMutableTreeNode) objectPath[1];

			Tree file = (Tree) selectedNode.getUserObject();
			for (int i = 0; i < file.attNames.size(); i++) {
				if ((((String) file.attNames.elementAt(i)).equals("name"))) {
					fileName = (String) file.attValues.elementAt(i);
					break;
				}
			} //for
		} //if

		return node;
	} // getSelectedNode

	public String getFileName() {
		return fileName;
	} //getFileName

	private void createTree(DefaultMutableTreeNode dmt) {

		if (dmt == null)
			return;

		Tree tree = (Tree) dmt.getUserObject();

		for (int i = 0;
			tree.children != null && i < tree.children.size();
			i++) {
			DefaultMutableTreeNode dmtn =
				new DefaultMutableTreeNode(tree.children.elementAt(i));
			dmt.add(dmtn);
			createTree(dmtn);
		} //for

	} //createGraphicalTree

	public void reconstructTree() {
		Tree newTree = parser.parse();
		root.setUserObject(null);
		root.setUserObject(newTree);

		createTree(root);

		graphicalTree.expandRow(0);
		graphicalTree.expandRow(1);

	}

	public JTree createGraphicalTree() {

		Tree tree = parser.parse();
		if (tree != null)
			root = new DefaultMutableTreeNode(tree);

		if (root != null) {
			createTree(root);
			treeModel = new DefaultTreeModel(root);
			treeModel.addTreeModelListener(new MyTreeModelListener());

			graphicalTree = new JTree(treeModel);

			graphicalTree.expandRow(0);
			graphicalTree.expandRow(1);

			BasicTreeUI ui = (BasicTreeUI) graphicalTree.getUI();
			//ui.setExpandedIcon (null);
			//ui.setCollapsedIcon (null);

			graphicalTree.putClientProperty("JTree.lineStyle", "Horizontal");

			graphicalTree.getSelectionModel().setSelectionMode(
				TreeSelectionModel.SINGLE_TREE_SELECTION);
			MyRenderer myRenderer = new MyRenderer();
			graphicalTree.setCellRenderer(myRenderer);

			graphicalTree.setBackground(color);
			graphicalTree.setForeground(color);

		} else {

			return null;
		} //if - else

		MouseListener ml = new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				int selRow =
					graphicalTree.getRowForLocation(e.getX(), e.getY());
				TreePath selPath =
					graphicalTree.getPathForLocation(e.getX(), e.getY());
				boolean write = false;
				
				if (selRow != -1) {
					if (e.getButton() == MouseEvent.BUTTON3
						&& e.getClickCount() == 1) {
						
						try {
							Tree t =
							(Tree) (((DefaultMutableTreeNode) (selPath
									.getLastPathComponent()))
									.getUserObject());
							
							String attr = t.getAttribute("write");
							if (attr==null || attr.equals("false")) {
								write=false;
							}
							if (attr!=null && attr.equals ("true")) {
								write=true;
							}
							
						} catch (Exception ex) {
							write = false;
						}
						
						//mySingleClick(selRow, selPath);
						mySingleClick(e,write);
					} else if (e.getClickCount() == 2) {
						//myDoubleClick(selRow, selPath);
					}
				}
			}
		};

		graphicalTree.addMouseListener(ml);
		return graphicalTree;
	} //createGraphicalTree

	public void mySingleClick(MouseEvent evt, boolean write) {
		//menu.
		Xmenu = evt.getX();
		Ymenu = evt.getY();
		if (write) {
			activate();
		}else{ 
			dezactivate();
		}	
		menu.show(evt.getComponent(), Xmenu, Ymenu);
	} //mySingleClick

	public ImageIcon loadImage(String resource) {

		ImageIcon ico = null;
		ClassLoader myClassLoader = getClass().getClassLoader();
		try {
			URL resLoc = myClassLoader.getResource(resource);
			ico = new ImageIcon(resLoc);
		} catch (Exception e) {
			System.out.println("Failed to get image ...");
		}
		return ico;

	} //loadImage

	public synchronized void removeNodeXY(int xpos, int ypos) {

		TreePath currentSelection =
			graphicalTree.getPathForLocation(xpos, ypos);
		if (currentSelection != null) {
			DefaultMutableTreeNode currentNode =
				(DefaultMutableTreeNode) (currentSelection
					.getLastPathComponent());
			MutableTreeNode parent =
				(MutableTreeNode) (currentNode.getParent());
			if (parent != null) {
				treeModel.removeNodeFromParent(currentNode);
				return;
			} //if
		} //if

	} //end of method removeCurrentNoded

	private class MyRenderer extends DefaultTreeCellRenderer {

		ImageIcon rootIcon;
		ImageIcon keyIcon;
		ImageIcon key1Icon;
		ImageIcon sectionIcon;

		public MyRenderer() {

			rootIcon = loadImage("lia/images/appControl/root.gif");
			sectionIcon = loadImage("lia/images/appControl/section.gif");

			keyIcon = loadImage("lia/images/appControl/key.gif");
			key1Icon = loadImage("lia/images/appControl/key1.gif");
			setBackground(Color.lightGray);
			setForeground(Color.lightGray);

		} //end of constructor MyRenderer

		public Component getTreeCellRendererComponent(
			JTree tree,
			Object value,
			boolean sel,
			boolean expanded,
			boolean leaf,
			int row,
			boolean hasFocus) {

			super.getTreeCellRendererComponent(
				tree,
				value,
				sel,
				expanded,
				leaf,
				row,
				hasFocus);
			Object o = ((DefaultMutableTreeNode) value).getUserObject();

			Tree t = (Tree) o;
			String s = t.getAttribute("write");
			if (s != null) {
				if (s.equals("false")) {
					
					 		if (sel)
								   			dezactivateModify();
					setForeground(Color.red);
				}
				if (s.equals("true")) {
							if (sel)
					   			activateModify();
					setForeground(new Color(38, 118, 19));
					
				}

			} // if

			if (sel) {
				//Object o = ((DefaultMutableTreeNode)value).getUserObject() ;
				selectionNotifier.notifySelection(o);
			}

			if (leaf) {
				if (isSection(value)) {
					if (!expanded)
						setIcon(sectionIcon);
					else
						setIcon(key1Icon);
				} else {
					if (isFile(value))
						setIcon(rootIcon);
					else
						setIcon(keyIcon);
				}
			} else {
				if (isSection(value)) {
					if (!expanded)
						setIcon(sectionIcon);
					else
						setIcon(key1Icon);
				} else
					setIcon(rootIcon);
			}

			setBackgroundNonSelectionColor(color);
			return this;

		} //end of method getTreeCellRendererComponent

		private boolean isSection(Object value) {
			Tree tree = null;
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			try {
				tree = (Tree) node.getUserObject();
			} catch (Exception e) {
				return false;
			}
			if (tree != null && tree.name.startsWith("section"))
				return true;
			return false;
		} //isSection function

		private boolean isFile(Object value) {
			Tree tree = null;
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			try {
				tree = (Tree) node.getUserObject();
			} catch (Exception e) {
				return false;
			}
			if (tree != null && tree.name.startsWith("file"))
				return true;
			return false;
		} //isSection function

	} //class MyRenderer

	class MyTreeModelListener implements TreeModelListener {
		public void treeNodesChanged(TreeModelEvent e) {
			DefaultMutableTreeNode node;
			node =
				(DefaultMutableTreeNode) (e
					.getTreePath()
					.getLastPathComponent());

			try {
				int index = e.getChildIndices()[0];
				node = (DefaultMutableTreeNode) (node.getChildAt(index));
			} catch (NullPointerException exc) {
			}

		}
		public void treeNodesInserted(TreeModelEvent e) {
		}
		public void treeNodesRemoved(TreeModelEvent e) {
		}
		public void treeStructureChanged(TreeModelEvent e) {
		}
	} //end of class MyTreeModelListener

	class WaitingDialog implements Runnable {

		private String command;
		String titleN = "Waiting for application configuration .... ";

		WaitingDialog(String command) {
			//make modal dialog

			this.command = command;

		}

		public void run() {
			response = null;
			String response1 = null;
			try {
				communicator.sendCommand(command);
				response = communicator.receiveResponseLine();

				if (response == null) {
					//						setModal (false);
					//						setVisible (false);
					return;
				}

				if (response.startsWith("+OK")) {
					//						String message = "Got application configuration";

					response1 = communicator.receiveResponseLine();
					while (true) {
						if (response1 == null) {
							response = null;
							//								setModal(false);
							//								setVisible(false);
							return;
						}

						if (!response1.equals(".")) {

							confFile += response1;
						} else {
							break;
						}
						response1 = communicator.receiveResponseLine();
					}

				} else {
					confFile = "";
				} //if - else

				afterConf();
				activate();
				//					setModal(false);
				//					setVisible(false);
			} catch (Exception e) {
				e.printStackTrace();
				activate();
				//					setVisible(false);
				// TODO - aici trebuie ceva sa afiseze asta ......
			}

		}
	} //WaitingDialog

} //class GraphicalTree
