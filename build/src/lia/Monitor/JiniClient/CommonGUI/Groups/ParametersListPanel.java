package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.MediaTracker;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import lia.Monitor.GUIs.Registry;
import lia.Monitor.GUIs.Unit;

public class ParametersListPanel extends JPanel implements ActionListener, ListSelectionListener {

	protected JList modList ;
	protected DefaultListModel lmodel;
	protected JButton show, barshow;
	protected JButton clusterSummary, nodeSummary;
	protected JPopupMenu summaryMenu;
	protected JMenuItem mnBarAverage;
	protected JMenuItem mnBarSum;
	protected JMenuItem mnBarIntegral;
	protected JMenuItem mnBarMinMax;
	protected JMenuItem mnPieAverage;
	protected JMenuItem mnPieSum;
	protected JMenuItem mnPieIntegral;
	protected JMenuItem mnPieMinMax;
	protected JPopupMenu historyMenu;
	protected JMenuItem mn2D;
	protected JMenuItem mn3D;
	
	protected JComponent lastSummaryBtn;
	
	protected ModulesListPanel modulesList;
	
	protected TextField addMod;
	protected int XW = 150;
	protected int YW = 120;
	
	/* For some parameters we should be able to predict the current unit used to plot the data */
	protected HashMap currentUnit = null;

	/** This is a reference from SerMonitorBase, it's included here for faster processing */
	protected Registry registry = null;
	
	public ParametersListPanel(ModulesListPanel modulesList) {
		super();
		setLayout ( new BorderLayout());
		this.modulesList = modulesList;
		lmodel = new DefaultListModel();
		modList = new JList(lmodel);
		modList.setBackground(new Color(205,226,247));
		modList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		modList.setBorder(BorderFactory.createLineBorder(new Color(205,226,247)));

		modList.setCellRenderer(new DefaultListCellRenderer());
		modList.setSelectionBackground(new Color(228,219,165));
		modList.setSelectionForeground(Color.blue);
		modList.addListSelectionListener(this);

		JScrollPane scp1 = new JScrollPane();
		scp1.getViewport().setView(modList);
		scp1.setPreferredSize(new Dimension(XW, YW/2));
		scp1.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED ,Color.white,Color.white,new Color(0,98,137) , new Color(0,59,95)));
		add("Center", scp1 );
		Font btnFont = new Font("Arial", Font.PLAIN, 10);
		Icon dnArrow = loadImage("blue_arraw_down.png"); 
		show = new JButton ("History Plot");
		show.setIcon(getLineIcon());
		show.setFont(btnFont);
		barshow =  new JButton ("Realtime Plot");
		barshow.setIcon(getBarIcon());
		barshow.setFont(btnFont);
		clusterSummary = new JButton("Cluster Summary", dnArrow);
		clusterSummary.setIcon(getPieIcon());
		clusterSummary.setHorizontalTextPosition(SwingConstants.LEFT);
		clusterSummary.setFont(btnFont);
		nodeSummary = new JButton("Nodes Summary", dnArrow);
		nodeSummary.setIcon(getPieIcon());
		nodeSummary.setHorizontalTextPosition(SwingConstants.LEFT);
		nodeSummary.setFont(btnFont);
		JPanel jshow = new JPanel();
		jshow.setLayout(new GridLayout(2, 2));
		jshow.add (show );
		jshow.add (barshow );
		jshow.add (nodeSummary);
		jshow.add (clusterSummary);
		add("South", jshow);
		
		Icon barIcon = loadImage("small_bar.gif");
		Icon pieIcon = loadImage("small_pie.gif");
		
		historyMenu = new JPopupMenu();
		mn2D = new JMenuItem("History 2D Plot");
		mn2D.setFont(btnFont);
		mn2D.setIcon(getLineIcon2());
		mn3D = new JMenuItem("History 3D Plot");
		mn3D.setFont(btnFont);
		mn3D.setIcon(getLineIcon3d());
		historyMenu.add(mn2D);
		historyMenu.add(mn3D);
		
		summaryMenu = new JPopupMenu();
		// --- bar ---
		mnBarAverage = new JMenuItem("Average");
		mnBarAverage.setFont(btnFont);
		mnBarAverage.setIcon(barIcon);
		mnBarSum = new JMenuItem("Sum");
		mnBarSum.setFont(btnFont);
		mnBarSum.setIcon(barIcon);
		mnBarIntegral = new JMenuItem("Integral");
		mnBarIntegral.setFont(btnFont);
		mnBarIntegral.setIcon(barIcon);
		mnBarMinMax = new JMenuItem("Min/Max");
		mnBarMinMax.setFont(btnFont);
		mnBarMinMax.setIcon(barIcon);
		// --- pie ---
		mnPieAverage = new JMenuItem("Average");
		mnPieAverage.setFont(btnFont);
		mnPieAverage.setIcon(pieIcon);
		mnPieSum = new JMenuItem("Sum");
		mnPieSum.setFont(btnFont);
		mnPieSum.setIcon(pieIcon);
		mnPieIntegral = new JMenuItem("Integral");
		mnPieIntegral.setFont(btnFont);
		mnPieIntegral.setIcon(pieIcon);
		mnPieMinMax = new JMenuItem("Min/Max");
		mnPieMinMax.setFont(btnFont);
		mnPieMinMax.setIcon(pieIcon);
		// add all
		summaryMenu.add(mnBarAverage);
		summaryMenu.add(mnBarSum);
		summaryMenu.add(mnBarIntegral);
		summaryMenu.add(mnBarMinMax);
		summaryMenu.addSeparator();
		summaryMenu.add(mnPieAverage);
		summaryMenu.add(mnPieSum);
		summaryMenu.add(mnPieIntegral);
		summaryMenu.add(mnPieMinMax);
		
		show.addActionListener(this);
		clusterSummary.addActionListener(this);
		setBorder(BorderFactory.createTitledBorder("Parameters"));
		nodeSummary.addActionListener(this);
	}
	
	public void setRegistry(Registry registry) {
		this.registry = registry;
	}

	Icon loadImage(String fileName){
		Icon image = null;
		try{
			ClassLoader myClassLoader = getClass().getClassLoader();
			URL url = myClassLoader.getResource("lia/images/"+fileName);
			ImageIcon icon = new ImageIcon(url);
			if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
				throw new Exception("failed");
			}
			image = icon;
		}catch(Exception ex){
			ex.printStackTrace();
		}	
		return image;
	}

//iterates through all values
	public Object[] getSelectedValues()
	{
		synchronized(modList) {
			return modList.getSelectedValues();
		}
	}

//updates values
	public void updateValues ( Vector  list ) {
		synchronized(modList) {
			lmodel.clear();
			if ( list == null ) return;
			for ( int i=0; i < list.size(); i++ ) {
				lmodel.addElement ( list.elementAt(i) );
			}
			modList.repaint();
		}
	}

//deletes all values
	public void refreshDisplay(){
		synchronized(modList) {
			lmodel.clear();
		}
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(clusterSummary) || e.getSource().equals(nodeSummary)){
			JComponent src = (JComponent) e.getSource();
			lastSummaryBtn = src;
			summaryMenu.setPreferredSize(new Dimension(src.getWidth(), 130));
			summaryMenu.show(src, 0, src.getHeight());
			return;
		}
		if (e.getSource().equals(show)) {
			historyMenu.setPreferredSize(new Dimension(show.getWidth(), 50));
			historyMenu.show(show, 0, show.getHeight());
			return;
		}
	};
	
	protected Icon pieIcon = null;
	
	public Icon getPieIcon() {
		
		if (pieIcon != null) return pieIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/PieGraph.gif");
		pieIcon = new ImageIcon(iconURL);
		return pieIcon;
	}
	
	protected Icon barIcon = null;
	
	public Icon getBarIcon() {
		
		if (barIcon != null) return barIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/BarGraph.gif");
		barIcon = new ImageIcon(iconURL);
		return barIcon;
	}
	
	protected Icon lineIcon = null;
	
	public Icon getLineIcon() {
		
		if (lineIcon != null) return lineIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/LineGraph.gif");
		lineIcon = new ImageIcon(iconURL);
		return lineIcon;
	}

	protected Icon lineIcon2 = null;
	
	public Icon getLineIcon2() {
		
		if (lineIcon2 != null) return lineIcon2;
		URL iconURL = this.getClass().getResource("/lia/images/groups/LineGraph_2.png");
		lineIcon2 = new ImageIcon(iconURL);
		return lineIcon2;
	}
	
	protected Icon lineIcon3d = null;
	
	public Icon getLineIcon3d() {
		
		if (lineIcon3d != null) return lineIcon3d;
		URL iconURL = this.getClass().getResource("/lia/images/groups/3d_small_2.png");
		lineIcon3d = new ImageIcon(iconURL);
		return lineIcon3d;
	}

	public HashMap getCurrentUnits() {
		return currentUnit;
	}
	
	/** Hack that is used to check the selected parameters and allow only valid ones to be selected in the list */
	protected void checkSelection(Object obj[], int[] ind) {
		
		/** no point in verifying if the registry is null anyway */
		if (registry == null) return; 
		
		synchronized (modList) {
			
			currentUnit = null;
			/** construct current list of selected modules */
			if (modulesList == null || modulesList.getModulesList() == null || modulesList.getModulesList().getModel().getSize() == 0) return;
			
			int nr = modulesList.getModulesList().getModel().getSize();
			String listOfModules[] = new String[nr];
			for (int i=0; i<nr; i++) {
				try {
					listOfModules[i] = modulesList.getModulesList().getModel().getElementAt(i).toString();
				} catch (Exception ex) {
					listOfModules[i] = ""; // don;t add anything if exception
				}
			}
			
			Unit baseUnit = null;
			/** now let's try to match some units */
			for (int i=0; i<obj.length; i++) {
				String str = (String)obj[i];
				baseUnit = registry.getUnit(str, listOfModules);
				if (baseUnit != null) break; // ok, we're done, we've found a first correct unit
			}
			
			if (baseUnit == null) return; // don't know any unit in the current selection, we're done checking
			
			currentUnit = new HashMap();
			
			/** now let's remove all the selected parameters which do not match our base unit */
			for (int i=0; i<obj.length; i++) {
				String str = (String)obj[i];
				Unit unit = registry.getUnit(str, listOfModules);
				if (!baseUnit.match(unit)) {
					modList.removeSelectionInterval(ind[i], ind[i]);
				} else
					currentUnit.put(str, unit);
			}
		}			
//			boolean load = false;
//			boolean cpu = false;
//			boolean page = false;
//			boolean mbps = false; /* only values with mbps are allowed */
//			boolean seconds = false;
//			boolean secondsFromVO = false;
//			boolean minutesFromVO = false;
//			boolean KBps = false;
//			boolean KB = false;
//			boolean MB = false;
//			boolean MBdisk = false;
//			currentUnit = null;
//			
//			for (int i=0; i<obj.length; i++) {
//				String str = (String)obj[i];
//				if (str.startsWith("Load") || str.startsWith("load")) {
//					load = true;
//					break;
//				}
//				if ((str.startsWith("Cpu") || str.startsWith("CPU") || str.startsWith("cpu")) && (str.indexOf("Time") < 0) && (str.indexOf("time") < 0)) {
//					cpu = true;
//					currentUnit = "%";
//					break;
//				}
//				if (str.startsWith("Page") || str.startsWith("page")) {
//					page = true;
//					break;
//				}
//				if (modulesList != null && (str.endsWith("_IN") || str.endsWith("_OUT"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.equals("monProcIO") || name.startsWith("snmp_IOpp") || name.indexOf("monRRD") >= 0) {
//								mbps = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (mbps) { currentUnit = "Mb/s"; break; }
//				}
//				if (modulesList != null && (str.equals("uptime"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("snmp_CatSwitch") >= 0) {
//								seconds = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (seconds) { currentUnit = "seconds"; break; }
//				}
//				if (modulesList != null && (str.equals("CPUTime") || str.equals("CPUTimeCondorHist"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monOsgVoJobs") >= 0) {
//								secondsFromVO = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (secondsFromVO) { currentUnit = "seconds"; break; }
//				}
//				if (modulesList != null && (str.equals("RunTime"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monOsgVoJobs") >= 0) {
//								minutesFromVO = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (minutesFromVO) { currentUnit = "minutes"; break; }
//				}
//				if (modulesList != null && (str.startsWith("ftpInput") || str.startsWith("ftpOutput"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monOsgVO_IO") >= 0 || name.indexOf("monVO_IO") >= 0 || name.indexOf("monVOgsiftpIO") >= 0) {
//								KB = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (KB) { currentUnit = "KB"; break; }
//				}
//				if (modulesList != null && (str.startsWith("ftpRateIn") || str.startsWith("ftpRateOut"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monOsgVO_IO") >= 0 || name.indexOf("monVO_IO") >= 0 || name.indexOf("monVOgsiftpIO") >= 0) {
//								KBps = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (KBps) { currentUnit = "KB/s"; break; }
//				}
//				if (modulesList != null && (str.equals("VIRT_MEM_free") || str.equals("MEM_total") || str.equals("Size"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monPN_PBS") >= 0 || name.indexOf("monOsgVoJobs") >= 0) {
//								MB = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (MB) { currentUnit = "MB"; break; }
//				}
//				if (modulesList != null && (str.equals("DiskUsage"))) {
//					// check the module also
//					DefaultListModel model = (DefaultListModel)modulesList.getModulesList().getModel();
//					try {
//						for (Enumeration en = model.elements(); en.hasMoreElements(); ) {
//							String name = en.nextElement().toString();
//							if (name.indexOf("monOsgVoJobs") >= 0) {
//								MBdisk = true;
//								break;
//							}
//						}
//					} catch (Exception ex) { }
//					if (MBdisk) { currentUnit = "MB"; break; }
//				}
//			}
//			
//			if (load) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.startsWith("Load") && !str.startsWith("load")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (cpu) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (str.indexOf("Time") >= 0 || str.indexOf("time") >= 0 || (!str.startsWith("Cpu") && !str.startsWith("CPU") && !str.startsWith("cpu"))) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (page) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.startsWith("Page") && !str.startsWith("page")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (mbps) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.endsWith("_IN") && !str.endsWith("_OUT")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (seconds) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.equals("uptime")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (secondsFromVO) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.equals("CPUTime") && !str.equals("CPUTimeCondorHist")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (minutesFromVO) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.equals("RunTime")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (KB) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.startsWith("ftpInput") && !str.startsWith("ftpOutput")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (KBps) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.startsWith("ftpRateIn") && !str.startsWith("ftpRateOut")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (MB) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.equals("VIRT_MEM_free") && !str.equals("MEM_total") && !str.equals("Size")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//			if (MBdisk) {
//				for (int i=0; i<obj.length; i++) {
//					String str = (String)obj[i];
//					if (!str.equals("DiskUsage")) {
//						modList.removeSelectionInterval(ind[i], ind[i]);
//					}
//				}
//			}
//		}
	}

	public void valueChanged(ListSelectionEvent e) {
		synchronized(modList) {
			try {
				Object sel[] = modList.getSelectedValues();
				int ind[] = modList.getSelectedIndices();
				if (sel == null || sel.length == 0) return;
				checkSelection(sel,  ind);
			} catch (Throwable t) { }
		}
	}
	
}
