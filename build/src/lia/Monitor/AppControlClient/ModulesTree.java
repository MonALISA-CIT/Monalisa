 package lia.Monitor.AppControlClient;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class ModulesTree implements TreeWillExpandListener, TreeExpansionListener   {

	final Color color = new Color (227,233,255);//255,247,216);
	
	private JTree tree ;
	private DefaultMutableTreeNode root ;
	private DefaultTreeModel treeModel ;
	
	public ModulesTree () {
		root = new DefaultMutableTreeNode ("Modules")  ;
		treeModel = new DefaultTreeModel (root);
		tree = new JTree (treeModel) ;
		tree.expandRow(0);
		tree.expandRow(1);
		
		//Enable tool tips.
		ToolTipManager.sharedInstance().registerComponent(tree);
    
		
		BasicTreeUI ui = (BasicTreeUI) tree.getUI();
//		ui.setExpandedIcon (null);
//		ui.setCollapsedIcon (null);


//		tree.addTreeWillExpandListener ( this);
	//	tree.addTreeExpansionListener(this) ;
		tree.putClientProperty ("JTree.lineStyle", "Horizontal");

		tree.getSelectionModel().setSelectionMode
					   (TreeSelectionModel.SINGLE_TREE_SELECTION);
		MyRenderer myRenderer = new MyRenderer ();
		tree.setCellRenderer (myRenderer) ;

		tree.setShowsRootHandles(true);
		tree.setBackground (color) ;
		tree.setForeground (color);
					   
	} //TreeExtended

	public void refresh () {
		tree.repaint();
	} // refresh
	
	public void setExpanded () {
		tree.expandRow (0);
		tree.expandRow (1) ;
	} //setExpanded
	
	public Object getSelectedModule () {
		
		TreePath path = tree.getSelectionPath() ;
		Object node = null ;
		if (path!=null) {
			DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) (path.getLastPathComponent()) ;
			node = dmtn.getUserObject();
		} //if - else
		
		return node ;
		
	} //getSelectedModule

	public DefaultMutableTreeNode getSelectedDefaultMutableTree () {
	    	TreePath path = tree.getSelectionPath() ;
		DefaultMutableTreeNode node = null ;
		if (path!=null) {
			node = (DefaultMutableTreeNode) (path.getLastPathComponent()) ;
		} //if - else
		
		return node ;
	} //getSelectedDefaultMutableTree
	
	public void treeExpanded(TreeExpansionEvent e) {
//		System.out.println("Extended") ;
	}

	public void treeCollapsed(TreeExpansionEvent e) {
//		System.out.println("Collapsed") ;
	//	throw new ExpandVetoException (e);
	}

	public void treeWillCollapse (TreeExpansionEvent e) throws ExpandVetoException {
		   TreePath treePath = e.getPath();
		   DefaultMutableTreeNode o =(DefaultMutableTreeNode) treePath.getLastPathComponent();
		   if (o.getParent()==null)
		   		throw new ExpandVetoException (e);
	   } //end of method treeWillCollapse

	   public void treeWillExpand (TreeExpansionEvent e)  {
	   } //end of method treeWillExpand


	public void addModule (Object module) {		
		DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode (module) ;
		root.add (moduleNode) ;
//		tree.expandRow(0);
//		tree.expandRow(1);
		
	} //addModule

	public void clearTree () {
	    root.removeAllChildren();
	    treeModel.reload();
	} //clearTree
	
	public JTree getTree () {
		return this.tree;
	} //getTree

	public ImageIcon loadImage (String resource) {

			   ImageIcon ico = null;
			   ClassLoader myClassLoader = getClass().getClassLoader();
			   try {
				   URL resLoc = myClassLoader.getResource (resource);
				   ico = new ImageIcon (resLoc);
			   } catch (Exception e) {
				   System.out.println ("Failed to get image ...");
			   }
			   return ico;

	} //loadImage
	
	private class MyRenderer extends DefaultTreeCellRenderer {

			   ImageIcon icon ;
			   ImageIcon rootIcon ;

			   public MyRenderer () {
		   	
				   icon =  loadImage ("lia/images/appControl/module.gif");
				   rootIcon =  loadImage ("lia/images/appControl/root.gif");
				   setBackground( new Color (255,247,216) );
				   setForeground( new Color (255,247,216));
			   
			   } //end of constructor MyRenderer

			   public Component getTreeCellRendererComponent (
							   JTree tree,
							   Object value,
							   boolean sel,
							   boolean expanded,
							   boolean leaf,
							   int row,
							   boolean hasFocus) {
						   	
				   super.getTreeCellRendererComponent (tree, value, sel, expanded, leaf, row, hasFocus);
				   
				   setBackground( color);
					Object o = ((DefaultMutableTreeNode)value).getUserObject() ;
					try {
						ModuleInformation mi = (ModuleInformation) o ;
						if (mi.configurationFile!=null)
    						    setToolTipText(mi.configurationFile); //set tool tip
						if (mi.status == 0)
						     setForeground (Color.red);
						if (mi.status == 1 )
						     setForeground (new Color (38,118,19));
						if (mi.status == 2)
						     setForeground (Color.black);            
						setIcon (icon); 	
		//				    
					} catch (Exception e) {
						setToolTipText(null); //no tool tip
				   		setIcon (null) ;
						
					}
				   
				   Font font = getFont ();
				   //Font newFont1 = font.deriveFont (50);
				   Font newFont2 = font.deriveFont (Font.BOLD ,14);
				   
				   setFont (newFont2);
				   
				   setBackgroundNonSelectionColor( color );
				   setBackground( color);
				   return this;
			   
			   } //end of method getTreeCellRendererComponent

		} //class MyRenderer


}
