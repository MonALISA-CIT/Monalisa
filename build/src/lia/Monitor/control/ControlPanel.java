package lia.Monitor.control;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JPanel;
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
public class ControlPanel extends JPanel{
	JButton stopModule ,showBar , showGraph ,changeTime, updateVRVS, restartVRVS, stopVRVS, startVRVS, statusVRVS;
	JPanel  placeHolder;
	MonitorControl parent;
	TimeInputDialog input ;
	
	
	public ControlPanel(MonitorControl parent){
		this.parent=parent;
		TitledBorder border =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99)),"  Control Panel  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		//TitledBorder border =new TitledBorder(new LineBorder(Color.white ,2),"  Control Panel  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),Color.white);
		placeHolder = new JPanel();
		//placeHolder.setPreferredSize(new Dimension(70,415));
		
		//placeHolder.setBackground(new Color(205,226,247));		
		placeHolder.setBackground(new Color(0x00,0x66,0x99));
		placeHolder.setLayout(new GridLayout(4,2));
		
		ControlListner controlList = new ControlListner();
		
		stopModule = new JButton(" Stop Module ");
		stopModule.addActionListener(controlList);

        updateVRVS = new JButton(" UPDATE VRVS ");
        updateVRVS.addActionListener(controlList);
        
        restartVRVS = new JButton(" RESTART VRVS ");
        restartVRVS.addActionListener(controlList);
        
        stopVRVS = new JButton(" STOP VRVS ");
        stopVRVS.addActionListener(controlList);

        startVRVS = new JButton(" START VRVS ");
        startVRVS.addActionListener(controlList);

        statusVRVS = new JButton(" VRVS STATUS ");
        statusVRVS.addActionListener(controlList);
        
		//stopModule.setMaximumSize(new Dimension(6 ,20));
		showBar  = new JButton(" Plot Bar ");
		showBar.addActionListener(controlList);
		showGraph = new JButton(" Plot Graph ");
		showGraph.addActionListener(controlList);
		changeTime = new JButton(" Change Time ");
		changeTime.addActionListener(controlList);
		
		//LineBorder buttonBor = new LineBorder(Color.white ,0);
		
		//Color backGr = new Color(0x33,0x66,0x99);
		Color backGr =new Color(205,226,247);
		Color c = new Color(0,0,0);
		
		
		stopModule.setBackground(backGr);
		showBar.setBackground(backGr);
		showGraph.setBackground(backGr);
		changeTime.setBackground(backGr);

        restartVRVS.setBackground(backGr);
        updateVRVS.setBackground(backGr);
        startVRVS.setBackground(backGr);
        stopVRVS.setBackground(backGr);
        statusVRVS.setBackground(backGr);

		stopModule.setForeground(c);
		showBar.setForeground(c);
		showGraph.setForeground(c);
		changeTime.setForeground(c);
        
        restartVRVS.setForeground(c);
        updateVRVS.setForeground(c);
        startVRVS.setForeground(c);
        stopVRVS.setForeground(c);
        statusVRVS.setForeground(c);
		
		// disable all the buttons to start with
		disableControls();
		
		
		
		placeHolder.add(stopModule);
		placeHolder.add(changeTime);
        
        if ( parent.isVRVSFarm() ) {
            placeHolder.add(updateVRVS);
            placeHolder.add(restartVRVS);
            placeHolder.add(stopVRVS);
            placeHolder.add(startVRVS);
            placeHolder.add(statusVRVS);
        }
//		placeHolder.add(showBar);
//		placeHolder.add(showGraph);
		
		this.add(placeHolder ,BorderLayout.CENTER);
		
		
		this.setBorder(border);
		//this.setPreferredSize(new Dimension(200,400));
		//this.setBackground(new Color(205,226,247));
		this.setBackground(new Color(205,226,247));
	}
	
	public void disableControls(){
		stopModule.setEnabled(false);
		changeTime.setEnabled(false);
		showBar.setEnabled(false);
		showGraph.setEnabled(false);
	}
	public void enableModuleControls(){
		stopModule.setEnabled(true);
		changeTime.setEnabled(true);
		showBar.setEnabled(true);
		showGraph.setEnabled(true);
	}
	public void enableParameterControls(){
		stopModule.setEnabled(false);
		changeTime.setEnabled(false);
		showBar.setEnabled(true);
		showGraph.setEnabled(true);
	}
	
	//**************************inner class
	class ControlListner implements ActionListener {
		public void actionPerformed(ActionEvent e){
			if(e.getSource() == stopModule && stopModule.isEnabled()){
				// call the parent method which will alo know 
				// about the selected Module and the object
				parent.stopModule();
			}
			else if (e.getSource()==changeTime && changeTime.isEnabled()){
				input = new TimeInputDialog(parent ," Changing Repetation Time " ," Input the new Repetation Time ");
				
				
				input.setVisible(true);
			
			} else if ( e.getSource() == updateVRVS ) {
                updateVRVS.setEnabled ( false );
                parent.updateReflector();
                updateVRVS.setEnabled ( true );
            } else if ( e.getSource() == stopVRVS ) {
                stopVRVS.setEnabled ( false );
                parent.sendCMDToReflector("stop");
                stopVRVS.setEnabled ( true );
            } else if ( e.getSource() == startVRVS ) {
                startVRVS.setEnabled ( false );
                parent.sendCMDToReflector("start");
                startVRVS.setEnabled ( true );
            } else if ( e.getSource() == statusVRVS ) {
                statusVRVS.setEnabled ( false );
                parent.sendCMDToReflector("status");
                statusVRVS.setEnabled ( true );
            } else if ( e.getSource() == restartVRVS ) {
                restartVRVS.setEnabled( false );
                parent.sendCMDToReflector("restart");
                restartVRVS.setEnabled( true );
            } else if (e.getSource() == showBar && showBar.isEnabled()){}
			else if (e.getSource() == showGraph && showGraph.isEnabled()){}
		}
	}
	
	
	
	
	
	
	

}
