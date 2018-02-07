package lia.Monitor.control;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/**
 * @author muhammad
 */
public class ConfigPanel extends JPanel{
	
	private JPanel modules;
	private JPanel parameters;
	
	private DefaultListModel moduleModel = new DefaultListModel();
	private DefaultListModel parameterModel = new DefaultListModel();
	private JList moduleList = new JList(moduleModel);
	private JList parameterList = new JList(parameterModel);
	private JScrollPane modScroll ,parScroll;
	
	
	// reference to Monitor control for passing the list values
	
	MonitorControl parent;
	
	public ConfigPanel(MonitorControl parent){
		this.parent=parent;
		Color c = new Color(230,242,255);
		
		TitledBorder border2 =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,1),"  Modules  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		TitledBorder border1 =new TitledBorder(new LineBorder(new Color(0x00,0x66,0x99) ,1),"  Parameters  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),new Color(0x00,0x66,0x99));
		
		modScroll = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED ,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		modScroll.setForeground(Color.black);
		//Color c = new Color(0x99,0xcc,0xff);
		modScroll.setBackground(c);
		parScroll = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED ,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		parScroll.setForeground(Color.black);
		//Color c = new Color(0x99,0xcc,0xff);
		parScroll.setBackground(c);
		
		
		
		moduleList.setBackground(c);
		parameterList.setBackground(c);
		
		moduleList.setSelectionBackground(new Color(0x00,0x66,0x99));
		parameterList.setSelectionBackground(new Color(0x00,0x66,0x99));
		moduleList.setSelectionForeground(Color.white);
		parameterList.setSelectionForeground(Color.white);
		moduleList.setFont(new Font("Tahoma" ,Font.BOLD,11));
		parameterList.setFont(new Font("Tahoma" ,Font.BOLD,11));
		
		
		moduleList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		parameterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		moduleList.addListSelectionListener(new ModuleListListener());
		parameterList.addListSelectionListener(new ParameterListListener());
		
		modules = new JPanel();
		parameters = new JPanel();
		modules.setBorder(border2);
		parameters.setBorder(border1);
		parameters.setLayout(new BorderLayout());
		modules.setLayout(new BorderLayout());
		
		modules.add(moduleList,BorderLayout.CENTER);
		parameters.add(parameterList ,BorderLayout.CENTER);
		
		
		
		
		
		modules.setBackground(c);
		parameters.setBackground(c);
		
		
		
		modScroll.getViewport().add(modules);
		parScroll.getViewport().add(parameters);
		
		
		//this.setBackground(c);
		this.setBackground( new Color(0x00,0x66,0x99));
		this.setForeground(Color.white);
		this.setLayout(new GridLayout(1,2));
		
		//this.add(tabPane , BorderLayout.CENTER);
		//this.add(scrollPane , BorderLayout.CENTER);
		
		this.add(modScroll );
		this.add(parScroll );
		TitledBorder border =new TitledBorder(new LineBorder(Color.white ,2),"  Configuration  " ,TitledBorder.CENTER,TitledBorder.TOP,new Font("Tahoma" ,Font.BOLD,12),Color.white);
		this.setBorder(border);
		//this.setBorder(new TitledBorder(" Configuration "));
		
		
		
		
	}
	
	public void clearDisplay(){
		parameterModel.clear();
		moduleModel.clear();
	}
	
	public String getSelectedModule(){
		return (String)moduleList.getSelectedValue();
	}
	
	public void addModules(Vector modules){
        
        if ( modules == null || modules.size() == 0) return;
        
		moduleModel.clear();
		Iterator iterator=modules.iterator();
		while(iterator.hasNext()){
			moduleModel.addElement(iterator.next());
		}
	}
	public void addParameters(Vector param){
        if ( param == null || param.size() == 0 ) return;
		parameterModel.clear();
		Iterator iterator=param.iterator();
		while(iterator.hasNext()){
			parameterModel.addElement(iterator.next());
		}
	}
	
	public void clear(){
		moduleModel.clear();
		parameterModel.clear();
	}
	
	public void addModule(){
		moduleModel.addElement("ModuleOne");
		moduleModel.addElement("ModuleTwo");
		moduleModel.addElement("ModuleThree");
		moduleModel.addElement("ModuleFour");
		moduleModel.addElement("ModuleFive");
		moduleModel.addElement("ModuleSix");
		
	}
	public void addParameter(){
		parameterModel.addElement("ParamOne");
		parameterModel.addElement("ParamTwo");
		parameterModel.addElement("ParamThree");
		parameterModel.addElement("ParamFour");
		parameterModel.addElement("ParamFive");
		parameterModel.addElement("ParamSix");
		
	}
	
	class ModuleListListener implements ListSelectionListener{
		public void valueChanged(ListSelectionEvent le){
			if(moduleList.isSelectionEmpty()){
				parent.disableControls();
			}
			else{
				parameterList.clearSelection();
				parent.enableModuleControls();
			}
		}
	}
	class ParameterListListener implements ListSelectionListener{
		public void valueChanged(ListSelectionEvent le){
			
			if(parameterList.isSelectionEmpty()){
				parent.disableControls();
			}
			else{
				moduleList.clearSelection();
				parent.enableParameterControls();
			}
		
		}
	}
	
		
		

}
