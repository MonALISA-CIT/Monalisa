package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Color;
import java.awt.Component;
import java.util.Hashtable;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

public class EnhancedTreeCellRenderer extends JLabel implements TreeCellRenderer {

	private static final Color blueUnselected = new Color(15, 70, 180);
	private static final Color blueSelected = new Color(130, 200, 250);
	private static final Color redUnselected = new Color(180, 15, 70);
	private static final Color redSelected = new Color(250, 130, 200);
	public Hashtable nodes = null;
	
	public EnhancedTreeCellRenderer(Hashtable hash) {
		
		this.nodes = hash;
	}

	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
		
		DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
		String	labelText = (String)node.getUserObject();
		
		boolean running = false;
		try {
			String key = ((DefaultMutableTreeNode)node.getParent()).getUserObject()+":"+labelText;
			if (nodes.containsKey(key))
				running = ((Boolean)nodes.get(key)).booleanValue();
		} catch (Exception ex) { running = false; }
		
		if (!leaf)
			setForeground(Color.black);
		else
		if (selected)
			if (running)
				setForeground(blueSelected);
			else
				setForeground(redSelected);
		else
			if (running)
				setForeground(blueUnselected);
			else
				setForeground(redUnselected);

		setText(labelText);
		return this;
	}

}
