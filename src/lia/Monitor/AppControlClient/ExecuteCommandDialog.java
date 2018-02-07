package lia.Monitor.AppControlClient;

import java.awt.Rectangle;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ExecuteCommandDialog extends JDialog {

    private JTextArea textArea = null;
    
    public ExecuteCommandDialog (String name, JFrame parent, String commandResponse) {
	
	super (parent, name, true);
    
	textArea = new JTextArea();
	textArea.setText (commandResponse);
	textArea.setEditable (false);  
    
	JScrollPane scrollPane = new JScrollPane (textArea) ;
	getContentPane().add (scrollPane);
	
	pack();
	if (parent!=null) {
	    Rectangle r = parent.getBounds();
            setLocation ((int)(r.getX()+r.getWidth()/2-300), (int)(r.getY()+r.getHeight()/2-200));
	} //if

	 setSize(600, 400);
 
         setVisible(true);	
    
    } //ExecuteCommandDialog
        
    
} //ExecuteCommandDialog
