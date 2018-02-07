package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;


public class ShowAttribute extends JPanel  implements SelectionNotifier {
	
	Tree tree = null;
	JTextArea textField ;
	JButton but ;
	
	public ShowAttribute () {
		super();
		
		//aici trebuie toate caracteristicile pt. asta
		
		BorderLayout jPanel1Layout = new BorderLayout();
		setLayout(jPanel1Layout);
		setVisible(true);
		
		textField = new JTextArea ();
		textField.setEditable (false);
		
		JSeparator jSeparator1 = new JSeparator() ;
		JSeparator jSeparator2  = new JSeparator();
		
		JScrollPane scroll = new JScrollPane (textField) ;
		
	//	textField.setText("");
		textField.setVisible(true);
			
		add(scroll);
	jSeparator1.setLayout(null);
		jSeparator1.setVisible(true);
		jSeparator1.setPreferredSize(new java.awt.Dimension(219, 21));
		jSeparator1.setBounds(new java.awt.Rectangle(0, 0, 219, 21));
		add(jSeparator1, BorderLayout.NORTH);
		jSeparator2.setLayout(null);
		jSeparator2.setVisible(true);
		jSeparator2.setPreferredSize(new java.awt.Dimension(219, 20));
		jSeparator2.setBounds(new java.awt.Rectangle(0, 90, 219, 20));
		add(jSeparator2, BorderLayout.SOUTH);
		
	} //constructor

	public String getText() {
	    return textField.getText();
	}
	
	public void notifySelection (Object o) {
		
		//textField.setEditable (false);
		if (o instanceof Tree) {
			
			tree = (Tree) o ;
			textField.setText ("");
			for (int i=0 ; i<tree.attNames.size() ; i++) {
				String attribute = (String ) (tree.attNames.elementAt (i));
				if (attribute.startsWith ("value")) {
					textField.setText (lia.app.AppUtils.dec((String) (tree.attValues.elementAt(i)))) ;
					textField.setEditable (true);
					break ;
				} else {
					textField.setEditable (false); 
				}
			} //for
			//setText ("") ;
		} else {
			tree = null ;
			textField.setEditable (false);
		} //if - else
		
	} //notifySelection
	
} //class
