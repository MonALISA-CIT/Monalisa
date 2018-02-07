package lia.Monitor.control;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
public class MessagePanel extends JPanel{
	
	private JScrollPane scroll ;
	private JTextArea msgArea ;
	
	private JButton clear ;
	
	public MessagePanel(){
		TitledBorder border =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,2),"  Messages  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		msgArea = new JTextArea(20, 80);
		
		//msgArea.setPreferredSize(new Dimension(200,100));
		msgArea.setSelectedTextColor(Color.blue);
		msgArea.setEnabled(true);
		msgArea.setEditable(false);
		Color c = new Color(230,242,255);
		msgArea.setBackground(c);
		msgArea.setForeground(Color.black);
		scroll = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS ,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		scroll.setBackground(Color.white);
		scroll.getViewport().add(msgArea);
		//scroll.getVerticalScrollBar().setBackground(new Color(0x00,0x66,0x99));
		scroll.getVerticalScrollBar().setBackground(new Color(166,210,255));
		this.setBorder(border);
		this.setLayout(new BorderLayout());
		this.add(scroll, BorderLayout.NORTH);
		clear = new JButton("CLEAR");
		clear.setFont(new Font("Tahoma" ,Font.BOLD,12));
		clear.setForeground(Color.white);
		clear.setFocusPainted(false);
		//clear.setForeground(new Color(0x00,0x66,0x99));
		clear.setBackground(new Color(0x00,0x66,0x99));
		
		clear.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				clear();
			}
		});
		
		this.add(clear , BorderLayout.EAST);
		//this.setBackground( new Color(0x00,0x66,0x99));
		this.setBackground( new Color(166,210,255));
		this.setForeground(Color.white);
	}
	
	public void clear(){
		msgArea.setText("");
	}
	
	public void addMessage(String message , boolean error){
		//if(error)
		msgArea.append("\n========================================\n");
        msgArea.append(message);
        msgArea.append("\n========================================\n");
	}
	public void addError(String error){
        msgArea.append("\n========================================\n");
        msgArea.append(error);
        msgArea.append("\n========================================\n");
	}

}
