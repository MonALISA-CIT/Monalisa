package lia.Monitor.GUIs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.TextField;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class ModulesPanel extends JPanel  {
	
	public JList modList ;
	DefaultListModel lmodel;
	public TextField addMod;
	
	private ModListener listener;
	//***************
	
	
	int XW = 150;
	int YW = 60;
	
	public ModulesPanel() {
		super();
		setLayout ( new BorderLayout());
		Color c = new Color(205,226,247);
		setBackground(c);
//		String ss ="<html><p><font color =\"#141843\" "+
//		"size = \"2\" face =\"Serif\"><b> "+" Modules "+
//		"</b></font></p></html>";
		String ss = " Modules ";
		add( "North", new JLabel ( ss ) );
		
		listener = new ModListener();
		
		lmodel = new DefaultListModel();
		modList = new JList(lmodel);
		modList.setBackground(c);
		modList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		
		modList.setCellRenderer(new rclistCellRenderer(modList));
		modList.setSelectionBackground(new Color(228,219,165));
		//modList.setSelectionForeground(new Color(223,242,229));
		modList.setSelectionForeground(Color.blue);
		
		modList.addListSelectionListener(listener);
		
		JScrollPane scp1 = new JScrollPane();
		scp1.getViewport().setView(modList);
		scp1.setPreferredSize(new Dimension(XW, YW/2));
		scp1.setBackground(c);
		scp1.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED ,Color.white,Color.white,new Color(0,98,137) , new Color(0,59,95)));
		
		add("Center", scp1 );
		
		setPreferredSize(new Dimension(XW,YW));
	}

	public void refreshDisplay(){
		lmodel.clear();
	}
	
	public JList getModulesList() {
		return modList;
	}
	
	private void addBorder(JComponent c){
		LineBorder border = (LineBorder) BorderFactory.createLineBorder(Color.black , 1 );
		c.setBorder(border);
	}
	
	private class ModListener implements ListSelectionListener{
		
		public void valueChanged(ListSelectionEvent e){
		}
	}
	
	
	public void updateList ( Vector  list ) {
		lmodel.clear();
		if ( list == null ) return;
		for(int i=0;i<list.size();i++)
			lmodel.addElement(list.get(i));
		
	}
	
	class rclistCellRenderer extends DefaultListCellRenderer
	{
		
		rclistCellRenderer(JList rclist) {
			super();
		}
	};
	
	
}
