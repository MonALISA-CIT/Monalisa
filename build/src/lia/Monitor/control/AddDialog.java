package lia.Monitor.control;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * @author muhammad
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class AddDialog extends JDialog {
	private String title ="Add the Farm Configuration";
	private JComboBox combo;
	private JLabel avail ,url;
	private JTextField field;
	private JButton ok ,cancel;
	
	// place holder panels  :)
	private JPanel availP ,urlP ,actionP ;
	
	private MonitorControl parent;
	
	public AddDialog(MonitorControl parent ){
		this.parent=parent;
		// adding first group of componenets
		
		//avail = new JLabel("Available Modules");
		//avail.setHorizontalTextPosition(JLabel.CENTER);
		//avail.setHorizontalAlignment(SwingConstants.CENTER);
		
		
		InputListener listener = new InputListener();
		
		
		combo = new JComboBox();
		combo.setPreferredSize(new Dimension(150,27));
		combo.setFont(new Font ("Tahoma" ,Font.BOLD ,11));
		combo.setBackground(new Color(0x00,0x66,0x99));
		combo.setForeground(Color.white);
		combo.addActionListener(listener);
		
		TitledBorder border2 =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2),"  Available Modules  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		availP = new JPanel();
		availP.setBackground(Color.white);
		availP.setBorder(border2);
		//availP.setLayout(new GridLayout(1,2));
		//availP.add(avail);
		availP.add(combo);
		
		// adding the second components
		urlP = new JPanel();
		field = new JTextField(20);
		field.setFont(new Font("Tahoma" ,Font.BOLD,12));
		field.setForeground(new Color(0x00,0x66,0x99));
		TitledBorder border1 =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2),"  Enter URL of the module  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		urlP.setBackground(Color.white);
		urlP.setBorder(border1);
		urlP.add(field);
		
		// adding the button controls
		// which need a place holder for proper placement
		JPanel placeHolder = new JPanel();
		placeHolder.setBackground(Color.white);
		placeHolder.setLayout(new BorderLayout());
		
		JPanel placeHolder2 = new JPanel();
		placeHolder2.setBackground(Color.white);
		placeHolder2.setPreferredSize(new Dimension(350,50));
		
		placeHolder.add(placeHolder2 ,BorderLayout.NORTH);
		
		
		
		
		actionP = new JPanel();
		actionP.setBackground(Color.white);
		//actionP.setBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2));
		//actionP.setBorder(new LineBorder(Color.black ,2));
		//actionP.setLayout(new GridLayout(1,2));
		
		ok = new JButton("      OK      ");
		cancel = new JButton("  CANCEL  ");
		ok.setBackground(new Color(0x00,0x66,0x99));
		ok.setForeground(Color.white);
		ok.addActionListener(listener);
		
		cancel.setBackground(new Color(0x00,0x66,0x99));
		cancel.setForeground(Color.white);
		cancel.addActionListener(listener);
		
		actionP.add(ok);
		actionP.add(cancel);
		
		// use the placeholder
		placeHolder.add(actionP ,BorderLayout.CENTER);
		
		
		this.setTitle(title);
		this.setBackground(Color.white);
		this.setSize(350,250);
		this.setLocationRelativeTo(parent);
		this.setResizable(false);
		this.setModal(true);
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(availP ,BorderLayout.NORTH);
		this.getContentPane().add(urlP ,BorderLayout.CENTER);
		this.getContentPane().add(placeHolder ,BorderLayout.SOUTH);
		this.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent we){
				System.exit(0);
			}
		});
	}
	
	public void setTitle(String title){
		this.title=title;
		super.setTitle(title);
	}
	
	
	
	// set the available module values in the combo box
	public void setValues(Vector values){
		combo.removeAllItems();
		Iterator iterator = values.iterator();
		while(iterator.hasNext()){
			combo.addItem(iterator.next());
		}
	}
	
	
	
	public static void main(String[] args){
		AddDialog add = new AddDialog(null);
		add.setTitle(" Add Cluster to Farm ");
		add.show();
	}
	
	
	class InputListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			if(e.getSource() == cancel){
				dispose();
			}
		//	else if(e.getSource() == combo){
			//	String module =(String)combo.getSelectedItem();
			//	parent.addModule(module);
		//	}
			else if(e.getSource() == ok){
				String url =field.getText();
				if(url.equals("")){
					url=(String)combo.getSelectedItem();
					dispose();
					parent.addModule(url);
				}
				else{
					parent.addModule(url);
					field.setText("");
					dispose();
				}
				
			}
		}
	}
	
	

}
