package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.ForceDirectedLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import net.jini.core.lookup.ServiceID;

public class GNetTopoPan extends JPanel 
						 implements graphical, 
						 			ActionListener,
									ChangeListener {

    Map<ServiceID, rcNode> nodes;
	Vector<rcNode> vnodes;
	SerMonitorBase monitor;
	
	GTopoArea gtArea;
	JPanel cmdPan;
	JLabel lblCrtOn;
	JComboBox cbShow;
	JComboBox cbLayout;
	JCheckBox cCompact;
	JButton bEndPoints;
	JColorScale colorScale;
	JCheckBox cDijkstra;
	JCheckBox cJoinAliases;
	JComboBox cbFilter;
	JCheckBox cValuesShown;
	
	JSlider respfSlider;
	JSlider stiffSlider;
	
	String [] showElements = { "Routers", "Networks", "AS" };
	final static int SHOW_ROUTERS = 0;
	final static int SHOW_NETS = 1;
	final static int SHOW_AS = 2; 
	Hashtable showPrefs;

static class EndPointsSelector extends JDialog 
						implements ActionListener, 
									WindowListener {
	JButton bSave;
	JButton bDiscard;
	JButton bCheckAll;
	JButton bUncheckAll;
	JPanel epPan;
	Hashtable cboxes2farms;
	boolean resultsSaved = false;
	
	public EndPointsSelector(Vector farms){
		setTitle("End-Points Selector");
		
		addWindowListener(this);
		
		JPanel allPan = new JPanel();
		allPan.setLayout(new BorderLayout());
		
		JPanel btnsCheckUncheck = new JPanel();
		bCheckAll = new JButton("Check all");
		bCheckAll.addActionListener(this);
		bUncheckAll = new JButton("Uncheck all");
		bUncheckAll.addActionListener(this);
		btnsCheckUncheck.setLayout(new GridLayout(2, 1));
		btnsCheckUncheck.add(bCheckAll);
		btnsCheckUncheck.add(bUncheckAll);
		btnsCheckUncheck.setPreferredSize(new Dimension(110, 32));
		
		JPanel btnsPan = new JPanel();
		bSave = new JButton("Save");
		bSave.addActionListener(this);
		bDiscard = new JButton("Discard");
		bDiscard.addActionListener(this);
		btnsPan.add(btnsCheckUncheck);
		btnsPan.add(Box.createRigidArea(new Dimension(30, 0)));
		btnsPan.add(bSave);
		btnsPan.add(bDiscard);
		
		cboxes2farms = new Hashtable();
		
		epPan = new JPanel();
		epPan.setLayout(new GridLayout(0, 3));
		fillEpPan(farms);
		
		JScrollPane epScrollPane = new JScrollPane(epPan, 
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		allPan.add(epScrollPane, BorderLayout.CENTER);
		allPan.add(btnsPan, BorderLayout.SOUTH);
		getContentPane().add(allPan);
		pack();
		setLocationRelativeTo(null); // this should center it
		setModal(true);
		setVisible(true);
	}

	private void fillEpPan(Vector farms){
		for(int i=0; i<farms.size(); i++){
			rcNode n = (rcNode) farms.get(i);
			JCheckBox jcb = new JCheckBox(n.UnitName, !n.selected);
			int j=0;
			for(; j<epPan.getComponentCount(); j++){
				JCheckBox c = (JCheckBox) epPan.getComponent(j);
				if(c.getText().compareTo(n.UnitName) > 0)
					break;
			}
			if(j == epPan.getComponentCount())
				j = -1;
			epPan.add(jcb, j);
			cboxes2farms.put(jcb, n);
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == bSave){
			//System.out.println("save");
			for(Iterator it=cboxes2farms.keySet().iterator(); it.hasNext(); ){
				JCheckBox jcb = (JCheckBox) it.next();
				rcNode farm = (rcNode) cboxes2farms.get(jcb);
				farm.selected = ! jcb.isSelected();
				//System.out.println("Show "+farm.UnitName+" : "+farm.selected);
			}
			cboxes2farms.clear();
			dispose();
			resultsSaved = true;
		}else if(e.getSource() == bDiscard){
			//System.out.println("discard");
			cboxes2farms.clear();
			dispose();
		}else if(e.getSource() == bCheckAll){
			for(Iterator it=cboxes2farms.keySet().iterator(); it.hasNext(); ){
				JCheckBox jcb = (JCheckBox) it.next();
				jcb.setSelected(true);
			}
		}else if(e.getSource() == bUncheckAll){
			for(Iterator it=cboxes2farms.keySet().iterator(); it.hasNext(); ){
				JCheckBox jcb = (JCheckBox) it.next();
				jcb.setSelected(false);
			}
		}

	}

	public void windowActivated(WindowEvent e) {
	}
	public void windowClosed(WindowEvent e) {
	}
	public void windowClosing(WindowEvent e) {
		//System.out.println("discard from close");
		cboxes2farms.clear();
		dispose();
	}
	public void windowDeactivated(WindowEvent e) {
	}
	public void windowDeiconified(WindowEvent e) {
	}
	public void windowIconified(WindowEvent e) {
	}
	public void windowOpened(WindowEvent e) {
	}
}
	
	
public GNetTopoPan(){

	showPrefs = new Hashtable();
	showPrefs.put("Routers/con", "15");
	showPrefs.put("Routers/stiff", "70");
	showPrefs.put("Networks/con", "36");
	showPrefs.put("Networks/stiff", "17");
	showPrefs.put("AS/con", "40");
	showPrefs.put("AS/stiff", "20");

	gtArea = new GTopoArea(this);
	cmdPan = buildCmdPanel();
	JPanel wrCmdPan = new JPanel();
	wrCmdPan.setLayout(new BorderLayout());
	wrCmdPan.add(cmdPan, BorderLayout.WEST);
	setLayout(new BorderLayout());
	add(wrCmdPan, BorderLayout.NORTH);
	add(gtArea, BorderLayout.CENTER);
}

JPanel buildCmdPanel(){
	JPanel pan = new JPanel();
	pan.setAlignmentY(Component.LEFT_ALIGNMENT);
//	JLabel lblShow = new JLabel("Show: ");
		
	cbShow = new JComboBox(showElements);
	cbShow.setSelectedIndex(2);
	cbShow.addActionListener(this);
	
	bEndPoints = new JButton("End-Points");
	bEndPoints.addActionListener(this);
	
	JPanel pShow = new JPanel();
	pShow.setLayout(new GridLayout(2, 1));
	pShow.add(cbShow);
	pShow.add(bEndPoints);
	pShow.setPreferredSize(new Dimension(110, 32));
	
	cbLayout = new JComboBox(new String [] { "No Layout", "Radial", "Layered", "Spring" });
	cbLayout.addActionListener(this);

	cbFilter = new JComboBox(new String [] {"All"});
	cbFilter.addActionListener(this);
	
    JPanel cboxes = new JPanel();
    cboxes.setPreferredSize(new Dimension(100, 32));
    cboxes.setLayout(new GridLayout(2, 1));
    cboxes.add(cbLayout);
    cboxes.add(cbFilter);

    cJoinAliases = new JCheckBox("Join aliases", false);
	cJoinAliases.setPreferredSize(new Dimension(100, 16));
	cJoinAliases.addActionListener(this);

	cCompact = new JCheckBox("Compact", false);
	cCompact.addActionListener(this);
	
	JPanel filters = new JPanel();
	filters.setLayout(new GridLayout(2, 1));
	filters.setPreferredSize(new Dimension(110, 32));
	filters.add(cJoinAliases);
	filters.add(cCompact);
	
	JLabel respfLabel = new JLabel("Con");
	respfLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
	respfLabel.setPreferredSize(new Dimension(30, 15));
    respfSlider = new JSlider(1, 100, Integer.parseInt((String)showPrefs.get("AS/con")));
    respfSlider.addChangeListener(this);
    respfSlider.setPreferredSize(new Dimension(120, 15));
    respfLabel.setLabelFor(respfSlider);
	respfSlider.setEnabled(false);
    
	JLabel stiffLabel = new JLabel("Stiff");
	stiffLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
	stiffLabel.setPreferredSize(new Dimension(30, 15));
    stiffSlider = new JSlider(1, 100, Integer.parseInt((String)showPrefs.get("AS/stiff")));
    stiffSlider.setPreferredSize(new Dimension(120, 15));
    stiffSlider.addChangeListener(this);
    stiffLabel.setLabelFor(respfSlider);
	stiffSlider.setEnabled(false);
    
    JPanel sliders = new JPanel();
    sliders.setLayout(new BoxLayout(sliders, BoxLayout.Y_AXIS));
    JPanel respfSliderPan = new JPanel();
    respfSliderPan.setLayout(new BoxLayout(respfSliderPan, BoxLayout.X_AXIS));
    respfSliderPan.add(respfLabel);
    respfSliderPan.add(respfSlider);
    sliders.add(respfSliderPan);
    JPanel stiffSliderPan = new JPanel();
    stiffSliderPan.setLayout(new BoxLayout(stiffSliderPan, BoxLayout.X_AXIS));
    stiffSliderPan.add(stiffLabel);
    stiffSliderPan.add(stiffSlider);
    sliders.add(stiffSliderPan);
    sliders.setPreferredSize(new Dimension(150, 32));
    
	JLabel lblCrtLabel = new JLabel("Node: ");
	lblCrtOn = new JLabel();
	JPanel labelPan = new JPanel();
	labelPan.setLayout(new BoxLayout(labelPan, BoxLayout.X_AXIS));
	labelPan.add(lblCrtLabel); //, BorderLayout.WEST);
	labelPan.add(lblCrtOn); //, BorderLayout.CENTER);
	labelPan.add(Box.createHorizontalGlue());//, BorderLayout.EAST);
	
	JLabel lblLinksLabel = new JLabel("Links: ");
	colorScale = new JColorScale();
	colorScale.setPreferredSize(new Dimension(200, 16));
	colorScale.setMaximumSize(new Dimension(200, 16));
	colorScale.setColors(Color.GREEN, Color.RED);

	cDijkstra = new JCheckBox("Dijkstra", false);
	cDijkstra.setPreferredSize(new Dimension(80, 16));
	cDijkstra.addActionListener(this);
	
	cValuesShown = new JCheckBox("Delays", false);
	cValuesShown.setPreferredSize(new Dimension(110, 16));
	cValuesShown.addActionListener(this);
	
	JPanel csPan = new JPanel();
	csPan.setLayout(new BoxLayout(csPan, BoxLayout.X_AXIS));
	csPan.add(lblLinksLabel); //, BorderLayout.WEST);
	csPan.add(colorScale); //, BorderLayout.CENTER);
	csPan.add(Box.createHorizontalStrut(10));
	csPan.add(cDijkstra);
	csPan.add(cValuesShown);
	csPan.add(Box.createHorizontalGlue()); //, BorderLayout.EAST);
	
	JPanel crtPan = new JPanel();
	crtPan.setLayout(new GridLayout(2, 1));
	crtPan.add(labelPan);
	crtPan.add(csPan);
	
	pan.add(pShow);
	pan.add(cboxes);
	pan.add(filters);
	pan.add(sliders);
	pan.add(crtPan);
	return pan;
}

public void updateNode(rcNode node) {
	// empty 
}

public void gupdate() {
	// empty 
}

public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
	this.nodes = nodes;
	this.vnodes = vnodes;
}

public void setSerMonitor(SerMonitorBase ms) {
	this.monitor = ms;
	gtArea.setSerMonitor(ms);
}

public void setMaxFlowData(rcNode n, Vector v) {
	// empty 
}

public void new_global_param(String name) {
	// empty 
}

public void actionPerformed(ActionEvent e) {
	if(e.getSource() == cbShow){
		int idx = cbShow.getSelectedIndex();
		//cElastic.setSelected(false);
		//gtArea.setLayout("none");
		respfSlider.setValue(Integer.parseInt((String)showPrefs.get(showElements[idx]+"/con")));
		stiffSlider.setValue(Integer.parseInt((String)showPrefs.get(showElements[idx]+"/stiff")));

		gtArea.setShow(idx);
		//respfSlider.setEnabled(false);
		//stiffSlider.setEnabled(false);
	}else if(e.getSource() == cbLayout){
	    int id = ((JComboBox)e.getSource()).getSelectedIndex();
		boolean isSel = id == 3;		
		gtArea.setLayout((String)((JComboBox)e.getSource()).getSelectedItem());
		respfSlider.setEnabled(isSel);
		stiffSlider.setEnabled(isSel);
	}else if(e.getSource() == cCompact){
		boolean isSel = ((JCheckBox)e.getSource()).isSelected();
		gtArea.setCompact(isSel);
	}else if(e.getSource() == bEndPoints){
		//System.out.println("eps->start");
		/*EndPointsSelector eps = */new EndPointsSelector(gtArea.farms);
		//System.out.println("eps->finished");
		gtArea.postRefreshLinksMsg(GTopoArea.DO_ALL);
	}else if(e.getSource() == cDijkstra){
		gtArea.showDijkstra = cDijkstra.isSelected();
		gtArea.postRefreshLinksMsg(GTopoArea.DO_ALL);
	}else if(e.getSource() == cValuesShown){
		gtArea.valuesShown = cValuesShown.isSelected();
		gtArea.postRefreshLinksMsg(GTopoArea.DO_ALL);
	}else if(e.getSource() == cJoinAliases){
		gtArea.joinAliases = cJoinAliases.isSelected();
		gtArea.postRefreshLinksMsg(GTopoArea.DO_ALL);
	}else if(e.getSource() == cbFilter){
		gtArea.postRefreshLinksMsg(GTopoArea.DO_ALL);
	}
}

public void stateChanged(ChangeEvent e) {
	if(e.getSource() == respfSlider){
		int idShow = cbShow.getSelectedIndex();
		showPrefs.put(showElements[idShow]+"/con", ""+respfSlider.getValue());
	    if(gtArea.layout instanceof ForceDirectedLayoutAlgorithm)
	        ((ForceDirectedLayoutAlgorithm)gtArea.layout).setRespF(respfSlider.getValue());
	    if(gtArea.layout instanceof SpringLayoutAlgorithm)
	        ((SpringLayoutAlgorithm)gtArea.layout).setRespRange(respfSlider.getValue());
	}else if(e.getSource() == stiffSlider){
		int idShow = cbShow.getSelectedIndex();
		showPrefs.put(showElements[idShow]+"/stiff", ""+stiffSlider.getValue());
	    if(gtArea.layout instanceof ForceDirectedLayoutAlgorithm)
	        ((ForceDirectedLayoutAlgorithm)gtArea.layout).setStiffness(stiffSlider.getValue());
	    if(gtArea.layout instanceof SpringLayoutAlgorithm)
	        ((SpringLayoutAlgorithm)gtArea.layout).setStiffness(stiffSlider.getValue());
	}
}

public void setVisible(boolean isVisible){
	super.setVisible(isVisible);
	if(! isVisible){
		cbLayout.setSelectedIndex(0);
	}
}

}
