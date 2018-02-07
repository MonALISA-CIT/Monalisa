package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class OSGHistoryMenu extends JPanel {

	static final long serialVersionUID = 2030012005L;
	
	private OSGPanel owner;
	JPanel radioPanel;
	ButtonGroup group;
	RadioListener myListener;
	
	String []buttonLabel = { "Last hour", "Last 3 hours" };
	long []historyTime = { 60 * 60 * 1000, 180 * 60 * 1000 };
	JRadioButton []historyButton = new JRadioButton[2];
	
	public OSGHistoryMenu(OSGPanel owner){
		super();
		this.owner = owner;
		init();
	}
	
	public void init() {
		// Group the radio buttons.
        group = new ButtonGroup();
	    // Register a listener for the radio buttons.
	    myListener = new RadioListener();
	    // Put the radio buttons in a column in a panel.
        radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));
        
        for(int i = 0; i < historyButton.length; i++){
			historyButton[i] = new JRadioButton(buttonLabel[i]);
			historyButton[i].setActionCommand(buttonLabel[i]);
			historyButton[i].setSelected(i == 0 ? true : false);
			historyButton[i].addActionListener(myListener);
		
			group.add(historyButton[i]);
			
			radioPanel.add(historyButton[i]);
		}
        
        add(radioPanel);
	}
	
	class RadioListener implements ActionListener{
		
		public void actionPerformed(ActionEvent e) {
			System.out.print("ActionEvent received: ");
			for(int i = 0; i < buttonLabel.length; i++){
				if (e.getActionCommand() == buttonLabel[i]) {
					System.out.println(buttonLabel[i] + " pressed. Setting historyTime to " + historyTime[i]);
					owner.historyTime = historyTime[i];
				}
			}
		}
	}

}