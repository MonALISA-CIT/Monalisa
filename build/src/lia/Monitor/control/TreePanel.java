package lia.Monitor.control;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;


/**
 * @author muhammad
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class TreePanel extends JPanel{
	JTree configTree ;
	DefaultMutableTreeNode root ;
	DefaultTreeModel model;
	DefaultTreeCellRenderer rend;
	DefaultMutableTreeNode groupRoot;
	String name="";
	JScrollPane scrollPane ;
	
	// a reference to parent used to indicate new values for the
	// modules and parameters
	
	MonitorControl parent;
	
	// selected object points out to the current selection in the tree
	
	Object selectedObject =null;
	
	public TreePanel(){
		this("TREE" ,null);
	}
	public TreePanel(String name , MonitorControl parent){
		this.parent=parent;
		this.name=name;
		this.setBackground(Color.white);
		this.setLayout(new BorderLayout());
		this.setPreferredSize(new Dimension(175,400));
		//this.setBorder(new TitledBorder("  Structure  " ));
		scrollPane = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS ,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.getHorizontalScrollBar().setBackground(new Color(166,210,255));
		scrollPane.getVerticalScrollBar().setBackground(new Color(0x00,0x66,0x99));
		
		JPanel cornerPanel = new JPanel();
		cornerPanel.setPreferredSize(new Dimension(4,4));
		cornerPanel.setBackground(new Color(166,210,255));
		scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER ,cornerPanel);
		//scrollPane.getHorizontalScrollBar().setForeground(Color.black);
		
		makeBasicTree();
		//makeTestFarm();
		this.add(scrollPane , BorderLayout.CENTER);
		
	}
	
	
	
	public void makeBasicTree(){
		//Color c = new Color(205,226,247);
		
		Color c = new Color(166,210,255);
        root = new DefaultMutableTreeNode();
        root.add(groupRoot = new DefaultMutableTreeNode(name));
        model = new DefaultTreeModel(root);
        configTree = new JTree(model);
        //rend = (DefaultTreeCellRenderer) configTree.getCellRenderer();
        rend = new DefaultTreeCellRenderer();

        rend.setLeafIcon( new ImageIcon (TreeIcons.createLeafImage()));
        rend.setOpenIcon( new ImageIcon (TreeIcons.createOpenFolderImage()));
        rend.setClosedIcon( new ImageIcon (TreeIcons.createClosedFolderImage()));

        //rend.setBackgroundSelectionColor(new Color(228,219,165));
        rend.setBackgroundSelectionColor(Color.black);
        rend.setBackgroundNonSelectionColor(c);
        rend.setTextSelectionColor(Color.white);
        rend.setTextNonSelectionColor(new Color(0x00,0x66,0x99));
        rend.setTextNonSelectionColor(Color.black);
        rend.setFont(new Font("Tohoma" ,Font.BOLD , 11));
        //rend.setForeground(new Color(0x00,0x66,0x99));
        //rend.setBackground(new Color(0x00,0x66,0x99));
        configTree.setCellRenderer(rend);
        //configTree.addTreeSelectionListener(new TreeListener());
        
        configTree.addMouseListener(new TreeListener());

        // added code **************************
        //configTree.addTreeSelectionListener
        configTree.setBackground(c);
        configTree.setForeground(new Color(0x00,0x66,0x99));
        //configTree.setBackground(Color.white);
        configTree.setRowHeight(17);
        configTree.putClientProperty("JTree.lineStyle", "Angled");
        //configTree.putClientProperty("JTree.lineColor", "white");
        configTree.setRootVisible(false);
        configTree.setShowsRootHandles(true);
        //configTree.setForeground(Color.white);
        scrollPane.getViewport().add(configTree, null);
        configTree.validate();
        scrollPane.validate();
        configTree.expandPath(new TreePath(groupRoot.getPath()));
	}
	
	public boolean addFarm ( MFarm fa ) {
        DefaultMutableTreeNode fan = new DefaultMutableTreeNode( fa );
        groupRoot.add ( fan ) ;
	    Vector clusters =fa.getClusters();
        for ( Enumeration ec = clusters.elements(); ec.hasMoreElements(); ) {

        	MCluster cc = (MCluster) ec.nextElement() ;
        	DefaultMutableTreeNode ccn = new DefaultMutableTreeNode( cc );
        	fan.add( ccn);
        	//for ( int j=0; j < cc.nodes.size(); j ++ ) {
        	// BNode bn = (BNode) cc.nodes.elementAt(j);
        	// code changed to cater for hashtable nodes in Cluster class
            Vector nodes=cc.getNodes();
			Enumeration nodeList =nodes.elements();
            while(nodeList.hasMoreElements()){
                MNode bn = (MNode)nodeList.nextElement();
                DefaultMutableTreeNode non = new DefaultMutableTreeNode( bn );
                ccn.add( non);
            }
        }
        //model.reload(root);
        model.reload(groupRoot);
		return true;
	}
	
	public void reset(){
		model.reload(root);
	}
	public void updateFarm(MFarm farm , boolean remove){
		int count = model.getChildCount(groupRoot);
        
	// this variable tells us where we have to reinsert the new node
		int index =-1;
        MFarm f =null;
        
	loop:	for(int i=0 ;i< count ;i++){
           		DefaultMutableTreeNode node =(DefaultMutableTreeNode)model.getChild(groupRoot,i);
           		Object userObject = node.getUserObject();
             	if(userObject instanceof MFarm){
                	f = (MFarm)userObject;
                	if(f.toString().equals(farm.toString())){
          	     		index = i;
                    //newNode = new DefaultMutableTreeNode(f);
                   		break loop;
                	}
				else{
					continue loop;
				}
                //else{
                //JOptionPane.showMessageDialog(null ,"usr obj is not farm");
                //addFarm(farm);
                //}

                }
            }
         if(index >= 0){
        	DefaultMutableTreeNode nn =(DefaultMutableTreeNode)model.getChild(groupRoot ,index);
            model.removeNodeFromParent(nn);
                /**
                * if we want to remove the farm instead of updating it then only remove it
                * and refresh the tree and exit
                */
             if(remove){
             	// something to clear the panels
				//panel.clear();
				model.reload(groupRoot);
				
                //valPanel.refreshDisplay();
                //modPanel.refreshDisplay();
                return;
              }
              //model.reload(groupRoot);
              makeUpdate(farm, groupRoot , index);
              //model.reload(groupRoot);
           }

 	}
 	
 	public JTree getTree(){
 		return this.configTree;
 	}
 	
 	// this method is used to make a test Farm
 	// for testing only
 	public void makeTestFarm(){
/*
 		
*/
 	}
 	
 	
 	public void makeUpdate(MFarm fa ,DefaultMutableTreeNode parent  , int index){
       	DefaultMutableTreeNode fan =null;
       	if(fa != null){
           fan = new DefaultMutableTreeNode( fa);
		   for ( Enumeration ec = fa.getClusters().elements(); ec.hasMoreElements(); ) {
		        MCluster cc = (MCluster) ec.nextElement() ;
        		DefaultMutableTreeNode ccn = new DefaultMutableTreeNode( cc );
        		fan.add( ccn);
        		//for ( int j=0; j < cc.nodes.size(); j ++ ) {
        		// BNode bn = (BNode) cc.nodes.elementAt(j);
        		// code changed to cater for hashtable nodes in Cluster class
                Enumeration nodes = cc.getNodes().elements();
                   while(nodes.hasMoreElements()){
                       MNode bn = (MNode)nodes.nextElement();
                       DefaultMutableTreeNode non = new DefaultMutableTreeNode( bn );
                       ccn.add( non);
                   }
	        }

        }
        
		//panel.clear();
		model.insertNodeInto(fan , parent , index);
        model.reload(fan);
 	}
 	
 	public Object getSelectedComponent(){
		return selectedObject;
	}
	
	
		
	
	class TreeListener extends MouseAdapter{
		 public void mousePressed(MouseEvent mouseevent){
             selectedObject = null;
				int i = configTree.getRowForLocation(mouseevent.getX(), mouseevent.getY());
                TreePath treepath = configTree.getPathForLocation(mouseevent.getX(), mouseevent.getY());
                if( treepath == null )
                	return;
                DefaultMutableTreeNode defaultmutabletreenode = (DefaultMutableTreeNode)treepath.getLastPathComponent();
                selectedObject = defaultmutabletreenode.getUserObject();
			    if(i != -1 && mouseevent.getClickCount() == 1 ) {
                	Vector parameters = null ;
                	Vector modules = null;
					//Hashtable modules =null;
                	if ( selectedObject instanceof MNode)  {
						
                        parameters = ( (MNode) selectedObject).getParameterList() ;
                        modules = ( (MNode) selectedObject).getModuleList();
						
					}
					else if ( selectedObject instanceof MCluster)  {
						
                       parameters= ( (MCluster) selectedObject).getParameterList() ;
                        modules = ( (MCluster) selectedObject).getModuleList();
					}
					else if ( selectedObject instanceof MFarm)  {
						
                        parameters = ( (MFarm) selectedObject).getParameterList() ;
                        modules = ( (MFarm) selectedObject).getModuleList();
					}
					else if (selectedObject == null){
						
						// to clear the module and parameter list values
						parent.clear();
						//panel.clear();
						return;
					}
					parent.setModuleValues(modules);
					parent.setParameterValues(parameters);
					//panel.clear();
					//panel.setModules(modules);
					//panel.setParameters(func);
				}
		  }
	  } // inner class ends here*/

}
