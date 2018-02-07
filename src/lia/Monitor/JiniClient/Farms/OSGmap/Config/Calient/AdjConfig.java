package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * The GUI for configuring the GMPLS adjacencies.
 */
public class AdjConfig extends JPanel implements ListSelectionListener, ActionListener {

	protected static final String[] adjTypes = new String[] { "CALIENTGMPLSPEER", "GMPLSOVERLAY", "GMPLSPEER", "GMPLSWDM" };
	
	protected JList adjList;
	protected JButton addAdj, delAdj;
	protected JList ctrlchList;
	protected JComboBox selectedCtrlCh;
	protected JButton addCtrl, delCtrl;
	protected JTextField currentCtrlCh;
	protected JTextField localRid;
	protected JTextField remoteRid;
	protected JTextField metric;
	protected JTextField ospfArea;
	protected JComboBox ospfAdj;
	protected JComboBox adjType;
	protected JTextField adjIndex;
	protected JComboBox rsvpRRFlag;
	protected JComboBox rsvpGRFlag;
	protected JComboBox ntfProc;
	protected JButton modify;
	
	protected Hashtable adjancencies; // name -> Adjancency
	protected Vector ctrlCh; // current ctrl ch list
	
	protected GMPLSConfig owner;
	
	public AdjConfig(GMPLSConfig owner) {
		
		super();
		setToolTipText(GMPLSHelper.adjHelp);
		this.owner = owner;
		adjancencies = new Hashtable();
		ctrlCh = new Vector();
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder("Adjacencies"));
		DefaultListModel model = new DefaultListModel();
		adjList = new JList(model);
		adjList.setToolTipText(GMPLSHelper.adjList);
		adjList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		adjList.addListSelectionListener(this);
		add(new JScrollPane(adjList));
		add(Box.createVerticalStrut(5));
		JPanel bPanel = new JPanel();
		bPanel.setOpaque(false);
		bPanel.setLayout(new BoxLayout(bPanel, BoxLayout.X_AXIS));
		addAdj = new JButton("Add adj");
		addAdj.setToolTipText(GMPLSHelper.adjAdd);
		addAdj.addActionListener(this);
		addAdj.setEnabled(false);
		delAdj = new JButton("Del adj");
		delAdj.setToolTipText(GMPLSHelper.adjDel);
		delAdj.addActionListener(this);
		delAdj.setEnabled(false);
		bPanel.add(Box.createHorizontalStrut(5));
		bPanel.add(addAdj);
		bPanel.add(Box.createHorizontalStrut(5));
		bPanel.add(delAdj);
		bPanel.add(Box.createHorizontalStrut(5));
		add(bPanel);
		add(Box.createVerticalStrut(5));
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createTitledBorder("Parameters"));
		model = new DefaultListModel();
		ctrlchList = new JList(model);
		ctrlchList.setToolTipText(GMPLSHelper.adjCtrls);
		ctrlchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ctrlchList.addListSelectionListener(this);
		p.add(new JScrollPane(ctrlchList));
		p.add(Box.createVerticalStrut(5));
		bPanel = new JPanel();
		bPanel.setOpaque(false);
		bPanel.setLayout(new BoxLayout(bPanel, BoxLayout.X_AXIS));
		selectedCtrlCh = new JComboBox();
		selectedCtrlCh.setEnabled(false);
		addCtrl = new JButton("Add ctrlch");
		addCtrl.setToolTipText(GMPLSHelper.adjAddCtrl);
		addCtrl.addActionListener(this);
		addCtrl.setEnabled(false);
		delCtrl = new JButton("Del ctrlch");
		delCtrl.setToolTipText(GMPLSHelper.adjDelCtrl);
		delCtrl.addActionListener(this);
		delCtrl.setEnabled(false);
		bPanel.add(Box.createHorizontalStrut(5));
		bPanel.add(selectedCtrlCh);
		bPanel.add(Box.createHorizontalStrut(5));
		bPanel.add(addCtrl);
		bPanel.add(Box.createHorizontalStrut(5));
		bPanel.add(delCtrl);
		bPanel.add(Box.createHorizontalStrut(5));
		p.add(bPanel);
		p.add(Box.createVerticalStrut(5));
		JPanel cctrlChPanel = new JPanel();
		cctrlChPanel.setOpaque(false);
		cctrlChPanel.setLayout(new BoxLayout(cctrlChPanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("Current Ctrl Channel: ");
		l.setToolTipText(GMPLSHelper.adjCtrlCh);
		currentCtrlCh = new JTextField();
		currentCtrlCh.setToolTipText(GMPLSHelper.adjCtrlCh);
		currentCtrlCh.setBackground(GMPLSConfig.color);
		currentCtrlCh.setEditable(false);
		currentCtrlCh.setEnabled(false);
		cctrlChPanel.add(Box.createHorizontalStrut(5));
		cctrlChPanel.add(l);
		cctrlChPanel.add(Box.createHorizontalStrut(5));
		cctrlChPanel.add(currentCtrlCh);
		cctrlChPanel.add(Box.createHorizontalStrut(5));
		p.add(cctrlChPanel);
		JPanel lridPanel = new JPanel();
		lridPanel.setOpaque(false);
		lridPanel.setLayout(new BoxLayout(lridPanel, BoxLayout.X_AXIS));
		l = new JLabel("Local Rid: ");
		l.setToolTipText(GMPLSHelper.adjLocalRid);
		localRid = new JTextField();
		localRid.setBackground(GMPLSConfig.color);
		localRid.setEnabled(false);
		localRid.setEditable(false);
		localRid.setToolTipText(GMPLSHelper.adjLocalRid);
		lridPanel.add(Box.createHorizontalStrut(5));
		lridPanel.add(l);
		lridPanel.add(Box.createHorizontalStrut(5));
		lridPanel.add(localRid);
		lridPanel.add(Box.createHorizontalStrut(5));
		p.add(lridPanel);
		JPanel rridPanel = new JPanel();
		rridPanel.setOpaque(false);
		rridPanel.setLayout(new BoxLayout(rridPanel, BoxLayout.X_AXIS));
		l = new JLabel("Remote Rid: ");
		l.setToolTipText(GMPLSHelper.adjRemoteRid);
		remoteRid = new JTextField();
		remoteRid.setToolTipText(GMPLSHelper.adjRemoteRid);
		remoteRid.setBackground(GMPLSConfig.color);
		remoteRid.setEnabled(false);
		rridPanel.add(Box.createHorizontalStrut(5));
		rridPanel.add(l);
		rridPanel.add(Box.createHorizontalStrut(5));
		rridPanel.add(remoteRid);
		rridPanel.add(Box.createHorizontalStrut(5));
		p.add(rridPanel);
		JPanel ospfAreaPanel = new JPanel();
		ospfAreaPanel.setOpaque(false);
		ospfAreaPanel.setLayout(new BoxLayout(ospfAreaPanel, BoxLayout.X_AXIS));
		l = new JLabel("OSPF Area: ");
		l.setToolTipText(GMPLSHelper.adjOspfArea);
		ospfArea = new JTextField();
		ospfArea.setToolTipText(GMPLSHelper.adjOspfArea);
		ospfArea.setBackground(GMPLSConfig.color);
		ospfArea.setEnabled(false);
		ospfAreaPanel.add(Box.createHorizontalStrut(5));
		ospfAreaPanel.add(l);
		ospfAreaPanel.add(Box.createHorizontalStrut(5));
		ospfAreaPanel.add(ospfArea);
		ospfAreaPanel.add(Box.createHorizontalStrut(5));
		p.add(ospfAreaPanel);
		JPanel metricPanel = new JPanel();
		metricPanel.setOpaque(false);
		metricPanel.setLayout(new BoxLayout(metricPanel, BoxLayout.X_AXIS));
		l = new JLabel("Metric: ");
		l.setToolTipText(GMPLSHelper.adjMetric);
		metric = new JTextField();
		metric.setToolTipText(GMPLSHelper.adjMetric);
		metric.setBackground(GMPLSConfig.color);
		metric.setEnabled(false);
		metricPanel.add(Box.createHorizontalStrut(5));
		metricPanel.add(l);
		metricPanel.add(Box.createHorizontalStrut(5));
		metricPanel.add(metric);
		metricPanel.add(Box.createHorizontalStrut(5));
		p.add(metricPanel);
		JPanel ospfPanel = new JPanel();
		ospfPanel.setOpaque(false);
		ospfPanel.setLayout(new BoxLayout(ospfPanel, BoxLayout.X_AXIS));
		l = new JLabel("OSPFAdj: ");
		l.setToolTipText(GMPLSHelper.adjOspfAdj);
		ospfAdj = new JComboBox(new String[] { "Enabled", "Disabled" });
		ospfAdj.setToolTipText(GMPLSHelper.adjOspfAdj);
		ospfAdj.setEnabled(false);
		ospfPanel.add(Box.createHorizontalStrut(5));
		ospfPanel.add(l);
		ospfPanel.add(Box.createHorizontalStrut(5));
		ospfPanel.add(ospfAdj);
		ospfPanel.add(Box.createHorizontalStrut(5));
		p.add(ospfPanel);
		JPanel adjTypePanel = new JPanel();
		adjTypePanel.setOpaque(false);
		adjTypePanel.setLayout(new BoxLayout(adjTypePanel, BoxLayout.X_AXIS));
		l = new JLabel("Adj Type: ");
		l.setToolTipText(GMPLSHelper.adjType);
		adjType = new JComboBox(adjTypes);
		adjType.setToolTipText(GMPLSHelper.adjType);
		adjType.setEnabled(false);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		adjTypePanel.add(l);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		adjTypePanel.add(adjType);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		p.add(adjTypePanel);
		JPanel adjIndexPanel = new JPanel();
		adjIndexPanel.setOpaque(false);
		adjIndexPanel.setLayout(new BoxLayout(adjIndexPanel, BoxLayout.X_AXIS));
		l = new JLabel("Adj Index: ");
		l.setToolTipText(GMPLSHelper.adjIndex);
		adjIndex = new JTextField();
		adjIndex.setToolTipText(GMPLSHelper.adjIndex);
		adjIndex.setBackground(GMPLSConfig.color);
		adjIndex.setEnabled(false);
		adjIndex.setEditable(false);
		adjIndexPanel.add(Box.createHorizontalStrut(5));
		adjIndexPanel.add(l);
		adjIndexPanel.add(Box.createHorizontalStrut(5));
		adjIndexPanel.add(adjIndex);
		adjIndexPanel.add(Box.createHorizontalStrut(5));
		p.add(adjIndexPanel);
		JPanel rsvprPanel = new JPanel();
		rsvprPanel.setOpaque(false);
		rsvprPanel.setLayout(new BoxLayout(rsvprPanel, BoxLayout.X_AXIS));
		l = new JLabel("RSVPRFlag: ");
		l.setToolTipText(GMPLSHelper.adjRsvpRR);
		rsvpRRFlag = new JComboBox(new String[] { "Enabled", "Disabled" });
		rsvpRRFlag.setToolTipText(GMPLSHelper.adjRsvpRR);
		rsvpRRFlag.setEnabled(false);
		rsvprPanel.add(Box.createHorizontalStrut(5));
		rsvprPanel.add(l);
		rsvprPanel.add(Box.createHorizontalStrut(5));
		rsvprPanel.add(rsvpRRFlag);
		rsvprPanel.add(Box.createHorizontalStrut(5));
		p.add(rsvprPanel);
		JPanel rsvpgPanel = new JPanel();
		rsvpgPanel.setOpaque(false);
		rsvpgPanel.setLayout(new BoxLayout(rsvpgPanel, BoxLayout.X_AXIS));
		l = new JLabel("RSVPGrFlag: ");
		l.setToolTipText(GMPLSHelper.adjRsvpGR);
		rsvpGRFlag = new JComboBox(new String[] { "Enabled", "Disabled" });
		rsvpGRFlag.setToolTipText(GMPLSHelper.adjRsvpGR);
		rsvpGRFlag.setEnabled(false);
		rsvpgPanel.add(Box.createHorizontalStrut(5));
		rsvpgPanel.add(l);
		rsvpgPanel.add(Box.createHorizontalStrut(5));
		rsvpgPanel.add(rsvpGRFlag);
		rsvpgPanel.add(Box.createHorizontalStrut(5));
		p.add(rsvpgPanel);
		JPanel ntfPanel = new JPanel();
		ntfPanel.setOpaque(false);
		ntfPanel.setLayout(new BoxLayout(ntfPanel, BoxLayout.X_AXIS));
		l = new JLabel("NTFProc: ");
		l.setToolTipText(GMPLSHelper.adjNtfProc);
		ntfProc = new JComboBox(new String[] { "Enabled", "Disabled" });
		ntfProc.setToolTipText(GMPLSHelper.adjNtfProc);
		ntfProc.setEnabled(false);
		ntfPanel.add(Box.createHorizontalStrut(5));
		ntfPanel.add(l);
		ntfPanel.add(Box.createHorizontalStrut(5));
		ntfPanel.add(ntfProc);
		ntfPanel.add(Box.createHorizontalStrut(5));
		p.add(ntfPanel);
		add(p);
		add(Box.createVerticalStrut(5));
		JPanel p1 = new JPanel();
		p1.setOpaque(false);
		p1.setLayout(new BorderLayout());
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.modify);
		modify.addActionListener(this);
		modify.setEnabled(false);
		p1.add(modify, BorderLayout.CENTER);
		add(p1);
		add(Box.createVerticalStrut(5));
	}

	public void addAdj(String name, String localRid, String remoteRid, Vector ctrlCh, String currentCtrlCh, String adjIndex, String ospfAdj, String ospfArea,
			String metric, String adjType, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) {
		
		if (name == null) return;
		if (localRid == null) localRid = "";
		if (remoteRid == null) remoteRid = "";
		if (ctrlCh == null) ctrlCh = new Vector();
		if (currentCtrlCh == null) currentCtrlCh = "";
		if (adjIndex == null) adjIndex = "";
		if (ospfAdj == null) ospfAdj = "";
		if (ospfArea == null) ospfArea = "";
		if (metric == null) metric = "";
		if (adjType == null) adjType = "";
		if (rsvpRRFlag == null) rsvpRRFlag = "";
		if (rsvpGRFlag == null) rsvpGRFlag = "";
		if (ntfProc == null) ntfProc = "";
		Adjancency adj = null;
		if (adjancencies.containsKey(name)) 
			adj = (Adjancency)adjancencies.get(name);
		else {
			adj = new Adjancency(name, this);
			adjancencies.put(name, adj);
			owner.ctrlCh.addAdj(name);
			DefaultListModel model = (DefaultListModel)adjList.getModel();
			model.add(0, name);
		}
		adj.set(currentCtrlCh, ctrlCh, localRid, remoteRid, ospfArea, metric, ospfAdj, adjType, adjIndex, rsvpRRFlag, rsvpGRFlag, ntfProc);
		if (adjList.getSelectedIndex() == -1) {
			adjList.setSelectedIndex(0);
			setCurrentAdj(name);
		}
	}
	
	public void addCtrlCh(String name) {
		
		if (name == null || name.length() == 0 || ctrlCh.contains(name)) return;
		ctrlCh.add(name);
		selectedCtrlCh.setEnabled(true);
		selectedCtrlCh.addItem(name);
		addCtrl.setEnabled(true);
		addAdj.setEnabled(true);
	}
	
	public void delCtrlCh(String name) {
		
		if (name == null || name.length() == 0 || !ctrlCh.contains(name)) return;
		ctrlCh.remove(name);
		selectedCtrlCh.removeItem(name);
		if (selectedCtrlCh.getItemCount() == 0) {
			selectedCtrlCh.setEnabled(false);
			addCtrl.setEnabled(false);
			DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
			for (int i=0; i<model.getSize(); i++) {
				if (name.equals(model.get(i).toString())) {
					model.remove(i);
					i--;
				}
			}
		}
	}
	
	protected void setCurrentAdj(String name) {
		
		Adjancency adj = null;
		
		if (name != null)
			adj = (Adjancency)adjancencies.get(name);
		if (adj == null || name == null) {
			currentCtrlCh.setText("");
			currentCtrlCh.setEnabled(false);
			DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
			model.removeAllElements();
			localRid.setText("");
			localRid.setEnabled(false);
			remoteRid.setText("");
			remoteRid.setEnabled(false);
			ospfArea.setText("");
			ospfArea.setEnabled(false);
			metric.setText("");
			metric.setEnabled(false);
			ospfAdj.setEnabled(false);
			adjType.setEnabled(false);
			adjIndex.setText("");
			adjIndex.setEnabled(false);
			rsvpRRFlag.setEnabled(false);
			rsvpGRFlag.setEnabled(false);
			ntfProc.setEnabled(false);
			modify.setEnabled(false);
			return;
		}
		DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
		model.removeAllElements();
		Vector v = adj.ctrlChList;
		for (int i=0; i<v.size(); i++)
			model.add(0, v.get(i));
		ctrlchList.setEnabled(true);
		currentCtrlCh.setText(adj.currentCtrlCh);
		currentCtrlCh.setEnabled(true);
		localRid.setText(adj.localRid);
		localRid.setEnabled(true);
		remoteRid.setText(adj.remoteRid);
		remoteRid.setEnabled(true);
		ospfArea.setText(adj.ospfArea);
		ospfArea.setEnabled(true);
		metric.setText(adj.metric);
		metric.setEnabled(true);
		if (adj.ospfAdj.length() != 0)
			ospfAdj.setSelectedItem(adj.ospfAdj);
		else
			ospfAdj.setSelectedIndex(0);
		ospfAdj.setEnabled(true);
		if (adj.adjType.length() != 0)
			adjType.setSelectedItem(adj.adjType);
		else
			adjType.setSelectedIndex(0);
		adjType.setEnabled(true);
		adjIndex.setText(adj.adjIndex);
		adjIndex.setEnabled(true);
		if (adj.rsvpRRFlag.length() != 0)
			rsvpRRFlag.setSelectedItem(adj.rsvpRRFlag);
		else
			rsvpRRFlag.setSelectedIndex(0);
		rsvpRRFlag.setEnabled(true);
		if (adj.rsvpGRFlag.length() != 0)
			rsvpGRFlag.setSelectedItem(adj.rsvpGRFlag);
		else
			rsvpGRFlag.setSelectedIndex(0);
		rsvpGRFlag.setEnabled(true);
		if (adj.ntfProc.length()  != 0)
			ntfProc.setSelectedItem(adj.ntfProc);
		else
			ntfProc.setSelectedIndex(0);
		ntfProc.setEnabled(true);
		modify.setEnabled(true);
	}
	
	public void valueChanged(ListSelectionEvent e) {
		
		if (e.getSource().equals(adjList)) {
			if (adjList.getSelectedIndex() == -1) {
				delAdj.setEnabled(false);
			} else {
				delAdj.setEnabled(true);
				setCurrentAdj((String)adjList.getSelectedValue());
			}
			return;
		}
		if (e.getSource().equals(ctrlchList)) {
			String ctrl = (String)ctrlchList.getSelectedValue();
			if (ctrl == null) delCtrl.setEnabled(false);
			else delCtrl.setEnabled(true);
		}
	}

	public void actionPerformed(ActionEvent e) {
		
		Object source = e.getSource();
		if (source.equals(addAdj)) {
			if (ctrlCh.size() == 0) return;
			String ctrl[] = new String[ctrlCh.size()];
			for (int i=0; i<ctrl.length; i++) ctrl[i] = (String)ctrlCh.get(i);
			AdjDialog dialog = new AdjDialog(owner, ctrl);
			dialog.setLocation((int)(owner.getLocation().getX() + owner.getWidth()/2-dialog.getWidth()/2), (int)(owner.getLocation().getY()+owner.getHeight()/2-dialog.getHeight()/2));
			dialog.setVisible(true);
			if (dialog.ret == AdjDialog.OK) {
				if (adjancencies.containsKey(dialog.name)) {
					GMPLSAdmin.showError(owner, "There is already an adjancency named "+dialog.name);
					return;
				}
				addAdj(dialog.name, "", dialog.rRid, dialog.ctrlCh, "", "", "", "", dialog.metric, dialog.adjType, "", "", "");
				owner.link.addAdj(dialog.name);
				executeAdd(dialog.name);
			}
			return;
		}
		if (source.equals(delAdj)) {
			String name = (String)adjList.getSelectedValue();
			if (!adjancencies.containsKey(name)) return;
			adjancencies.remove(name);
			DefaultListModel model = (DefaultListModel)adjList.getModel();
			for (int i=0; i<model.size(); i++) {
				if (name.equals(model.get(i).toString())) { model.remove(i); i--; }
			}
			if (model.size() == 0) {
				delAdj.setEnabled(false);
				setCurrentAdj(null);
			} else {
				adjList.setSelectedIndex(0);
				setCurrentAdj((String)adjList.getSelectedValue());
			}
			owner.link.delAdj(name);
			executeDel(name);
			return;
		}
		if (source.equals(addCtrl)) {
			String name = (String)selectedCtrlCh.getSelectedItem();
			if (name == null) return;
			DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
			for (int i=0; i<model.size(); i++) 
				if (name.equals(model.get(i).toString())) return; 
			model.add(0, name);
			return;
		}
		if (source.equals(delCtrl)) {
			String name = (String)ctrlchList.getSelectedValue();
			DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
			for (int i=0; i<model.size(); i++)
				if (name.equals(model.get(i).toString())) { model.remove(i); i--; }
			if (model.size() == 0) delCtrl.setEnabled(false);
			return;
		}
		if (source.equals(modify)) {
			String name = (String)adjList.getSelectedValue();
			if (name == null) return;
			String currentCtrlCh = this.currentCtrlCh.getText();
			Vector ctrl =  new Vector();
			DefaultListModel model = (DefaultListModel)ctrlchList.getModel();
			for (int i=0; i<model.size(); i++)
				ctrl.add(model.get(i));
			String remoteRid = this.remoteRid.getText();
			String ospfArea = this.ospfArea.getText();
			String metric = this.metric.getText();
			String ospfAdj = (String)this.ospfAdj.getSelectedItem();
			String adjType = (String)this.adjType.getSelectedItem();
			String rsvpRRFlag = (String)this.rsvpRRFlag.getSelectedItem();
			String rsvpGRFlag = (String)this.rsvpGRFlag.getSelectedItem();
			String ntfProc = (String)this.ntfProc.getSelectedItem();
			owner.admin.changeAdj(name, currentCtrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag, rsvpGRFlag, ntfProc);
			return;
		}
	}
	
	protected void executeAdd(String name) {

		if (name == null) return;
		Adjancency adj = (Adjancency)adjancencies.get(name);
		if (adj == null) return;
		StringBuilder buf = new StringBuilder();
		for (int i=0; i<adj.ctrlChList.size(); i++) {
			if (i != 0) buf.append("&");
			buf.append(adj.ctrlChList.get(i).toString());
		}
		String ospfAdj;
		if (adj.ospfAdj.equals("Disabled")) ospfAdj = "N";
		else ospfAdj = "Y";
		String adjType;
		if (adj.adjType.length() == 0) adjType = adjTypes[0];
		else adjType = adj.adjType;
		String rsvpRRFlag;
		if (adj.rsvpRRFlag.equals("Disabled")) rsvpRRFlag = "N";
		else rsvpRRFlag = "Y";
		String rsvpGRFlag;
		if (adj.rsvpGRFlag.equals("Disabled")) rsvpGRFlag = "N";
		else rsvpGRFlag = "Y";
		String ntfProc;
		if (adj.ntfProc.equals("Disabled"))  ntfProc = "N";
		else ntfProc = "Y";
//		System.out.println("Execute add "+adj.name+" "+buf.toString()+" "+adj.remoteRid+" "+adj.ospfArea+" "+adj.metric+" "+
//				ospfAdj+" "+adjType+" "+rsvpRRFlag+" "+rsvpGRFlag+" "+ntfProc+" ");
		try {
			owner.admin.addAdj(adj.name, buf.toString(), adj.remoteRid, adj.ospfArea, adj.metric, ospfAdj, adjType,  rsvpRRFlag, rsvpGRFlag, ntfProc);
		} catch (Exception ex) { GMPLSAdmin.showError(this, "When trying to add adjancency "+name+" got error "+ex); }
	}
	
	protected void executeDel(String name) {
		
		if (name == null) return;
		try {
			owner.admin.deleteAdj(name);
		} catch (Exception ex) { GMPLSAdmin.showError(this, "When trying to delete adjancency "+name+" got error "+ex); };
	}
	
} // end of class AdjConfig


