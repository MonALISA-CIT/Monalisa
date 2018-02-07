package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;

/**
 * The class that draws the upper menu of the panel.
 */
public class OSGMenu extends JPanel implements ActionListener, ChangeListener, ItemListener {

	static final long serialVersionUID = 181020051L;
	// type of ftp values shown
	JComboBox ftpValues;
	DefaultComboBoxModel combo;
	
	// layout stuff
	public JSlider sldStiffness;
	public JSlider sldRepulsion;
	JCheckBox ckShowOCN; // show only connected nodes
	JCheckBox ckShowToolTip;
	JCheckBox ckShowOnlyModule;
	JComboBox cbLayout;
	
	public JColorScale csFTP;
	
	// param shown
	public JCheckBox nodesShown;
	public JCheckBox cpuShown;
	public JCheckBox ioShown;
	public JCheckBox cpuTimeShown;
	public JCheckBox jobsShown;
	public JCheckBox fJobsShown;

	public boolean onlyToolTip = false;
	public boolean onlyModule = false;
	
	public JCheckBox kbMakeNice;
	public JCheckBox kbShadow;
	
	public JComboBox options;
	public HashMap activeItems = new HashMap();
	
	private OSGPanel owner;

	//public String inputHtm		= 	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.IN_COLOR)+"\">INPUT</font></html>";
	//public String outputHtm		= 	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.OUT_COLOR)+"\">OUTPUT</font></html>";
	public String inoutHtm 		= 	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.IN_COLOR)+"\">INPUT</font>/"+"<font color=\"" + OSGColor.decodeColor(OSGConstants.OUT_COLOR)+"\">OUTPUT</font></html>";
	//public String inputRateHtm 	=	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.IN_COLOR)+"\">INPUT</font> RATE</html>";
	//public String outputRateHtm =	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.OUT_COLOR)+"\">OUTPUT</font> RATE</html>";
	public String inoutRateHtm	=	"<html>FTP <font color=\""+OSGColor.decodeColor(OSGConstants.IN_COLOR)+"\">INPUT</font>/"+"<font color=\"" + OSGColor.decodeColor(OSGConstants.OUT_COLOR)+"\">OUTPUT</font> RATE</html>";
	
	public String nodesHtm		=	"<html>Nodes <font color=\""+OSGColor.decodeColor(OSGConstants.FREE_NODES.color)+"\">free</font>/<font color=\"" + OSGColor.decodeColor(OSGConstants.BUSY_NODES.color)+"\">busy</font></html>";
	public String cpuHtm		=	"<html>CPU <font color=\""+OSGColor.decodeColor(OSGConstants.CPU_USR.color)+"\">usr</font>/<font color=\""+ OSGColor.decodeColor(OSGConstants.CPU_SYS.color)+"\">sys</font>/<font color=\""+OSGColor.decodeColor(OSGConstants.CPU_IDLE.color)+"\">idl</font>/<font color=\""+ OSGColor.decodeColor(OSGConstants.CPU_ERR.color)+"\">err</font></html>";
	public String ioHtm			=	"<html>IO ratio <font color=\""+OSGColor.decodeColor(OSGConstants.IO_IN.color)+"\">in</font>/<font color=\""+ OSGColor.decodeColor(OSGConstants.IO_OUT.color)+"\">out</font></html>";
	public String jobsStateHtm	=	"<html>Jobs <font color=\""+OSGColor.decodeColor(OSGConstants.RUNNING_JOBS.color)+"\">Running</font>/"+"<font color=\""+ OSGColor.decodeColor(OSGConstants.IDLE_JOBS.color)+"\">Idle</font>";
	public String fJobsStateHtm	=	"<html>Finished Jobs (<font color=\""+OSGColor.decodeColor(OSGConstants.FINISHED_S_JOBS.color)+"\">Succes</font>/" + "<font color=\""+ OSGColor.decodeColor(OSGConstants.FINISHED_E_JOBS.color)+"\">Error</font>)";
	public String cpuTimeHtm	=	"<html>VO <font color=\""+OSGColor.decodeColor(OSGConstants.CPU_TIME.color)+"\">CPU time</font> consumed";

	public OSGMenu(OSGPanel owner) {
		
		super();
		this.owner = owner;
		init();
	}
	
	public void init() {

		Font f = new Font("Arial", Font.BOLD, 10);
		ComboBoxRenderer renderer = new ComboBoxRenderer();
		Dimension dpan = new Dimension(200, 35);
		
		Dimension dimLayoutPan = new Dimension(350, 35);
		Dimension dimSingleLayoutPan = new Dimension(350, 17);
		Dimension dimSlider = new Dimension(40, 15);
		
		/** panel for FTP parameters */
		JPanel ftpPanel = new JPanel();
		ftpValues = new JComboBox(new DefaultComboBoxModel());
		ftpValues.setRenderer(renderer);
		ftpValues.addActionListener(this);
		ftpValues.setFont(f);
		
		//ftpValues.addItem(inputHtm);
		//ftpValues.addItem(outputHtm);
		ftpValues.addItem(inoutHtm);
		//ftpValues.addItem(inputRateHtm);
		//ftpValues.addItem(outputRateHtm);
		ftpValues.addItem(inoutRateHtm);
		ftpValues.setSelectedIndex(0);
		
		csFTP = new JColorScale();
		csFTP.setValues(0, 0);
		csFTP.setColors(Color.GREEN, Color.GREEN);
		
		ftpPanel.setLayout(new BorderLayout());
		ftpPanel.setPreferredSize(dpan);
		//ftpPanel.setMaximumSize(dpan);
		//ftpPanel.setMinimumSize(dpan);
		
		ftpPanel.add(ftpValues, BorderLayout.CENTER);
		ftpPanel.add(csFTP, BorderLayout.SOUTH);
		
		
		/** panel for collected Parameters */
		JPanel paramPanel = new JPanel();
		
		JPanel pPanel1 = new JPanel();

		ckShowOCN = new JCheckBox("Only Connected nodes ", false);
	    ckShowOCN.setActionCommand("showOCN");
	    ckShowOCN.setPreferredSize(new Dimension(170, 15));
	    ckShowOCN.setFont(f);
	    ckShowOCN.addActionListener(this);

		ckShowOnlyModule = new JCheckBox("Only OSG Modules ", false);
	    ckShowOnlyModule.setActionCommand("showOM");
		ckShowOnlyModule.setPreferredSize(new Dimension(170, 15));
		ckShowOnlyModule.setFont(f);
	    ckShowOnlyModule.addActionListener(this);

	    //pPanel1.setLayout(new BoxLayout(pPanel1, BoxLayout.X_AXIS));
	    pPanel1.setLayout(new BorderLayout());
		setProp(pPanel1, new Dimension(330, 15), f);
		pPanel1.add(ckShowOCN, BorderLayout.EAST);
		pPanel1.add(ckShowOnlyModule, BorderLayout.WEST);
		
		JPanel pPanel2 = new JPanel();
		
		ckShowToolTip = new JCheckBox("Only ToolTip ", false);
	    ckShowToolTip.setActionCommand("showTT");
		//ckShowToolTip.setPreferredSize(new Dimension(150, 15));
	    ckShowToolTip.setFont(f);
	    ckShowToolTip.addActionListener(this);
		
		options = new JComboBox();
		options.setRenderer(new SpecialComboBoxRenderer());
		options.addActionListener(this);
		options.setPreferredSize(dimSlider);
		
		activeItems.putAll(OSGConstants.UPARAMS);
			
		nodesShown = new JCheckBox(nodesHtm);
		nodesShown.setSelected(true);
		nodesShown.setEnabled(false);
		nodesShown.setFont(f);
		nodesShown.addItemListener(this);
		
		cpuShown = new JCheckBox(cpuHtm);
		cpuShown.setSelected(((Integer)activeItems.get("cpu")).intValue()==1);
		cpuShown.setFont(f);
		cpuShown.addItemListener(this);
		
		ioShown = new JCheckBox(ioHtm);
		ioShown.setSelected(((Integer)activeItems.get("io")).intValue()==1);
		ioShown.setFont(f);
		ioShown.addItemListener(this);

		cpuTimeShown = new JCheckBox(cpuTimeHtm);
		cpuTimeShown.setSelected(((Integer)activeItems.get("cputime")).intValue()==1);
		cpuTimeShown.setFont(f);
		cpuTimeShown.addItemListener(this);
		
		jobsShown = new JCheckBox(jobsStateHtm);
		jobsShown.setSelected(((Integer)activeItems.get("jobs")).intValue()==1);
		jobsShown.setFont(f);
		jobsShown.addItemListener(this);
		
		fJobsShown = new JCheckBox(fJobsStateHtm);
		fJobsShown.setSelected(((Integer)activeItems.get("fjobs")).intValue()==1);
		fJobsShown.setFont(f);
		fJobsShown.addItemListener(this);
		
		options.addItem(nodesShown);
		options.addItem(cpuShown);
		options.addItem(ioShown);
		options.addItem(cpuTimeShown);
		options.addItem(jobsShown);
		options.addItem(fJobsShown);
		
	    pPanel2.setLayout(new BoxLayout(pPanel2, BoxLayout.X_AXIS));
		setProp(pPanel2, new Dimension(330, 20), f);
		pPanel2.add(ckShowToolTip);
		pPanel2.add(options);
		
		paramPanel.setLayout(new BoxLayout(paramPanel, BoxLayout.Y_AXIS));
		setProp(paramPanel, new Dimension(340, 35), f);
		paramPanel.add(pPanel1, BorderLayout.SOUTH);
		paramPanel.add(pPanel2, BorderLayout.SOUTH);
		
		
		/** node properties */
		JPanel nodePanel = new JPanel();
		
		JPanel slidersPan = new JPanel();
		JPanel layoutCbPan = new JPanel();
		
		JLabel lblStiff = new JLabel(" Stiffness: ");
		lblStiff.setFont(f);
		sldStiffness = new JSlider(1, 100, 10);
	    sldStiffness.addChangeListener(this);
	    sldStiffness.setPreferredSize(new Dimension(80, 15));
	    //sldStiffness.setMaximumSize(dimSlider);
		sldStiffness.setEnabled(false);
	    lblStiff.setLabelFor(sldStiffness);
	    
	    JLabel lblRepulsion = new JLabel(" Repulsion: ");
		lblRepulsion.setFont(f);
		sldRepulsion = new JSlider(1, 100, 40);
	    sldRepulsion.addChangeListener(this);
	    sldRepulsion.setPreferredSize(new Dimension(80, 15));
	    //sldRepulsion.setMaximumSize(dimSlider);
		sldRepulsion.setEnabled(false);
	    lblRepulsion.setLabelFor(sldRepulsion);
	    
	    slidersPan.setLayout(new BoxLayout(slidersPan, BoxLayout.X_AXIS));
		setProp(slidersPan, dimSingleLayoutPan, f);
		slidersPan.add(lblStiff);
		slidersPan.add(sldStiffness);
		slidersPan.add(lblRepulsion);
		slidersPan.add(sldRepulsion);
		
		JLabel lblLayout = new JLabel(" Select layout: ");
		lblLayout.setFont(f);
		layoutCbPan.add(lblLayout);
		cbLayout = new JComboBox(new String [] { "None", "Random", "Grid", "Radial", "Layered", "Map", "Elastic" });
		cbLayout.setFont(new Font("Arial", Font.BOLD, 9));
		cbLayout.setPreferredSize(dimSlider);
		cbLayout.addActionListener(this);
		
		kbMakeNice = new JCheckBox("Nicer", false);
		kbMakeNice.setFont(f);
		kbMakeNice.addActionListener(this);
		
		kbShadow = new JCheckBox("Shadow", false);
		kbShadow.setFont(f);
		kbShadow.addActionListener(this);
		
		layoutCbPan.setLayout(new BoxLayout(layoutCbPan, BoxLayout.X_AXIS));
		setProp(layoutCbPan, dimSingleLayoutPan, f);
		layoutCbPan.add(cbLayout);
		layoutCbPan.add(kbShadow);
		layoutCbPan.add(kbMakeNice);
		
		nodePanel.setLayout(new BoxLayout(nodePanel, BoxLayout.Y_AXIS));
		setProp(nodePanel, dimLayoutPan, f);
		nodePanel.add(slidersPan);
		nodePanel.add(layoutCbPan);
						
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(ftpPanel);
		add(paramPanel);
		add(nodePanel);		
	}
	
	static public void setProp(JComponent c, Dimension d, Font f){
		c.setFont(f);
		c.setPreferredSize(d);
		//c.setMaximumSize(d);
		//c.setMinimumSize(d);
	}

	public void actionPerformed(ActionEvent e) {
		
		Object src = e.getSource();
		String cmd = (src == cbLayout ? (String) cbLayout.getSelectedItem() : e.getActionCommand());
		if(cmd.equals("Elastic") || cmd.equals("Random") || cmd.equals("Grid") 
		        || cmd.equals("Layered") || cmd.equals("Radial") || cmd.equals("Map")) {
		    // enable/disable sliders
		    if(cmd.equals("Elastic")){
		        sldStiffness.setEnabled(true);
		        sldRepulsion.setEnabled(true);
		    }else{
		        sldStiffness.setEnabled(false);
		        sldRepulsion.setEnabled(false);
				owner.stopElastic();
		    }
		    owner.currentTransformCancelled = true;
			owner.setLayoutType(cmd);
		}else if(cmd.equals("None")){
		    owner.stopElastic();
		    owner.currentTransformCancelled = true;
		    owner.setLayoutType(cmd);
		} else if(src.equals(ftpValues)){
			String paramName = (String) ftpValues.getSelectedItem();
            if ( paramName == null ) 
                return;
			int index = ftpValues.getSelectedIndex();
			switch (index) {
				//case 0 : owner.ftpTransferType = OSGFTPHelper.FTP_INPUT; break;
				//case 1 : owner.ftpTransferType = OSGFTPHelper.FTP_OUTPUT; break;
				case 0 : owner.ftpTransferType = OSGFTPHelper.FTP_INOUT; break;
				//case 3 : owner.ftpTransferType = OSGFTPHelper.FTP_INPUT_RATE; break;
				//case 4 : owner.ftpTransferType = OSGFTPHelper.FTP_OUTPUT_RATE; break;
				case 1 : owner.ftpTransferType = OSGFTPHelper.FTP_INOUT_RATE; break;
			}
			try {
				owner.setLayoutType(owner.currentLayout);
			} catch (Exception ex) { }
		} if(e.getSource() == kbMakeNice){
			owner.setShowSphere(kbMakeNice.isSelected());
		} else if (e.getSource().equals(kbShadow)) {
			owner.setShowShadow(kbShadow.isSelected());
		} else if(cmd.equals("showOCN")){
		    owner.showOnlyConnectedNodes = ((JCheckBox)src).isSelected();
		    owner.currentTransformCancelled = true;
//		    owner.setLayoutType((String)cbLayout.getSelectedItem());
		} else if(cmd.equals("showTT")){
		    if(owner.onlyToolTip == true)
				owner.onlyToolTip = false;
		    else owner.onlyToolTip = true;
		} else if(cmd.equals("showOM")){
		    if(owner.onlyModule == true)
				owner.onlyModule = false;
		    else owner.onlyModule = true;
		} else if(src.equals(options)){
			int index = options.getSelectedIndex();
			boolean flag;
			switch (index) {
				/*case 0 :
				    flag = nodesShown.isSelected();
					nodesShown.setSelected(!flag);
					owner.monitor.updateUserParamPreferences("nodes",!flag);
					break;*/
				case 1 :
					flag = cpuShown.isSelected();
					cpuShown.setSelected(!flag);
					//owner.monitor.updateUserParamPreferences("cpu",!flag);
					break;	
				case 2 : 
					flag = ioShown.isSelected();
					ioShown.setSelected(!flag);
					//owner.monitor.updateUserParamPreferences("io",!flag);
					break;
				case 3 : 
					flag = cpuTimeShown.isSelected();
					cpuTimeShown.setSelected(!flag);
					//owner.monitor.updateUserParamPreferences("cputime",!flag);
					break;
				case 4 : 
					flag = jobsShown.isSelected();
					jobsShown.setSelected(!flag);
					//owner.monitor.updateUserParamPreferences("jobs",!flag);
					break;
				case 5 : 
					flag = fJobsShown.isSelected();
					fJobsShown.setSelected(!flag);
					//owner.monitor.updateUserParamPreferences("finishedjobs",!flag);
					break;
			}
			try {
				owner.setLayoutType(owner.currentLayout);
			} catch (Exception ex) { }
		}
		owner.repaint();
	}

	public void stateChanged(ChangeEvent e) {
        if(e.getSource() == sldStiffness){
            if(owner.layout instanceof SpringLayoutAlgorithm)
                ((SpringLayoutAlgorithm)owner.layout).setStiffness(sldStiffness.getValue());
        }else if(e.getSource() == sldRepulsion){
            if(owner.layout instanceof SpringLayoutAlgorithm)
                ((SpringLayoutAlgorithm)owner.layout).setRespRange(sldRepulsion.getValue());
        }
	}

	
	public void itemStateChanged(ItemEvent e) {
        
		Object source = e.getItemSelectable();
		boolean selected = (e.getStateChange() == ItemEvent.SELECTED);
		if (source.equals(nodesShown)) {
			if (!(owner.getParamState("nodes") == selected)) {
				owner.putInUserParams("nodes", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
	    } else if (source.equals(cpuShown)) {
			if (!(owner.getParamState("cpu") == selected)) {
				owner.putInUserParams("cpu", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
        } else if (source.equals(ioShown)) {
			if (!(owner.getParamState("io") == selected)) {
				owner.putInUserParams("io", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
        } else if (source.equals(cpuTimeShown)) {
			if (!(owner.getParamState("cputime") == selected)) {
				owner.putInUserParams("cputime", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
		} else if (source.equals(jobsShown)) {
			if (!(owner.getParamState("jobs") == selected)) {
				owner.putInUserParams("jobs", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
        } else if (source.equals(fJobsShown)) {
			if (!(owner.getParamState("fjobs") == selected)) {
				owner.putInUserParams("fjobs", selected);
				owner.redoParams(owner.canvasPane.currentNodes);
			}
        }
	}

	static class ComboBoxRenderer extends JLabel implements ListCellRenderer {
		static final long serialVersionUID = 181020052;
		public ComboBoxRenderer() {
			setOpaque(true);
			setHorizontalAlignment(CENTER);
			setVerticalAlignment(CENTER);
		}
		public Component getListCellRendererComponent(
			JList list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus) {
			if (isSelected) {
				this.setBackground(list.getSelectionBackground());
				this.setForeground(list.getSelectionForeground());
			} else {
				this.setBackground(list.getBackground());
				this.setForeground(list.getForeground());
			}

			//ImageIcon icon = (ImageIcon)value;
			this.setText((String) value);
			// setIcon(icon);
			return this;
		}
	}

	static class SpecialComboBoxRenderer extends JCheckBox implements ListCellRenderer {
		
		static final long serialVersionUID = 181020055;
		
		public SpecialComboBoxRenderer() {
			this.setOpaque(true);
			this.setHorizontalAlignment(LEFT);
			this.setVerticalAlignment(CENTER);
		}

		public Component getListCellRendererComponent(
					JList list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {
			//Get the selected index. (The index param isn't
			//always valid, so just use the value.)
			JCheckBox element = (JCheckBox)value;

			if (isSelected) {
				this.setBackground(list.getSelectionBackground());
				this.setForeground(list.getSelectionForeground());
			} else {
				this.setBackground(list.getBackground());
				this.setForeground(list.getForeground());
			}
			// Set the icon and text.  If icon was null, say so.
			this.setText(element.getText());
			this.setSelected(element.isSelected());
			this.setEnabled(element.isEnabled());
			this.setBackground(element.getBackground());
			return this;
		}
	}
	
} // end of class OSGMenu

