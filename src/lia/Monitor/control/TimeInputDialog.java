package lia.Monitor.control;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;


/**
 * @author muhammad
 *
 
 */
public class TimeInputDialog extends JDialog {
	
	private String title ="";
	private String option ="";
	private JPanel top ,bottom;
	private JTextField field ;
	private JButton ok , cancel;
	
	private MonitorControl parent;
	
	public TimeInputDialog(MonitorControl parent ,String title ,String option){
		
		this.parent=parent;
		
		setTitle(title);
		setOption(option);
		
		//
		top = new JPanel();
		field = new JTextField(20);
		field.setFont(new Font("Tahoma" ,Font.BOLD,12));
		field.setForeground(new Color(0x00,0x66,0x99));
		TitledBorder border1 =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2),option ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		top.setBackground(Color.white);
		top.setBorder(border1);
		top.add(field);
		
		//
		bottom = new JPanel();
		bottom.setBackground(Color.white);
		//bottom.setBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2));
		//bottom.setBorder(new LineBorder(Color.black ,2));
		//bottom.setLayout(new GridLayout(1,2));
		
		DialogListener dialogList = new DialogListener();
		
		ok = new JButton("      OK      ");
		cancel = new JButton("  CANCEL  ");
		ok.setBackground(new Color(0x00,0x66,0x99));
		ok.setForeground(Color.white);
		ok.addActionListener(dialogList);
		cancel.setBackground(new Color(0x00,0x66,0x99));
		cancel.setForeground(Color.white);
		cancel.addActionListener(dialogList);
		
		bottom.add(ok);
		bottom.add(cancel);
		
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(top ,BorderLayout.NORTH);
		this.getContentPane().add(bottom ,BorderLayout.SOUTH);
		
		
		this.getContentPane().setBackground(Color.white);
		//this.setForeground(Color.white);
		this.setSize(300,130);
		this.setLocationRelativeTo(parent);
		this.setResizable(false);
		this.setModal(true);
		
		
	
	}
	
	public void setTitle(String title){
		this.title=title;
		super.setTitle(title);
	
	}
	public void setOption(String optio){
		//this.option=option;
	}
	
	
	class DialogListener implements ActionListener{
		public void actionPerformed(ActionEvent e){
			if(e.getSource() == cancel){
				dispose();
			}
			else if(e.getSource() == ok){
				try{
					int value =Integer.parseInt(field.getText());
					field.setText("");
					dispose();
					parent.changeTime(value);
				}
				catch(Exception ee){
					JOptionPane.showMessageDialog(null, "Wrong Number Format Try again");
					field.setText("");
				}
				
			}
		}
	}
	

	public static void main(String[] args) {
		new InputDialog(null ,"Add module" ,"Enter Module Name").show();
	}
}
