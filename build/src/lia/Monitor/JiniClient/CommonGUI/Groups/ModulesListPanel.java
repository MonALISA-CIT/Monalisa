package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.TextField;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;

public class ModulesListPanel extends JPanel{

	public JList modList ;
	protected DefaultListModel lmodel;
	public TextField addMod;
	
	int XW = 150;
	int YW = 60;

	public ModulesListPanel() {
		super();
		setLayout ( new BorderLayout());
		Color c = new Color(205,226,247);
		
		lmodel = new DefaultListModel();
		modList = new JList(lmodel);
		modList.setBackground(c);
		modList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );

		modList.setCellRenderer(new DefaultListCellRenderer());
		modList.setSelectionBackground(new Color(228,219,165));
		modList.setSelectionForeground(Color.blue);

		JScrollPane scp1 = new JScrollPane();
		scp1.getViewport().setView(modList);
		scp1.setPreferredSize(new Dimension(XW, YW/2));
		scp1.setBackground(c);
		scp1.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED ,Color.white,Color.white,new Color(0,98,137) , new Color(0,59,95)));
		
		add("Center", scp1 );

		setPreferredSize(new Dimension(XW,YW));
		setBorder(BorderFactory.createTitledBorder("Modules"));
	}
	public void refreshDisplay(){
		lmodel.clear();
	}

//	private void addBorder(JComponent c){
//		LineBorder border = (LineBorder) BorderFactory.createLineBorder(Color.black , 1 );
//		c.setBorder(border);
//	}
	
	public void updateList ( Vector  list ) {
		lmodel.clear();
		if ( list == null ) return;
		for(int i=0;i<list.size();i++)
			lmodel.addElement(list.get(i));
		modList.repaint();
	}
	
	public JList getModulesList() {
		return modList;
	}
}
