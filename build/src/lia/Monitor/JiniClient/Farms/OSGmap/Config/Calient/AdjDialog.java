package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Dialog GUI class for adding a new adjacency.
 */
public class AdjDialog extends JDialog implements ActionListener, WindowListener, ListSelectionListener {

	public static final byte OK = 0;
	public static final byte CANCEL = 1;

	public static String adjTypes[] = { "CALIENTGMPLSPEER", "GMPLSOVERLAY", "GMPLSPEER", "GMPLSWDM" };
	
	private JTextField nameField;
	private JComboBox ctrlField;
	private JList selectedCtrlList;
	DefaultListModel selectedCtrlListModel;
	private JTextField rRidField;
	private JTextField metricField;
	private JComboBox adjTypeField;
	
	private JButton addCtrl;
	private JButton delCtrl;
	
	public String name;
	public Vector ctrlCh;
	public String rRid;
	public String metric;
	public String adjType;

	private JButton modify;
	private JButton cancel;
	
	public byte ret;

	public AdjDialog(JFrame owner, String[] ctrlCh) {

		super(owner, "Add adjacency", true);
		this.ctrlCh = new Vector();
		getContentPane().setLayout(new BorderLayout());
		JPanel p = new EnhancedJPanel();
		p.setToolTipText(GMPLSHelper.adjHelp);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(Box.createVerticalStrut(5));
		JPanel namePanel = new JPanel();
		namePanel.setOpaque(false);
		namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.X_AXIS));
		JLabel l = new JLabel("Name: ");
		l.setToolTipText(GMPLSHelper.adjName);
		nameField = new JTextField();
		nameField.setToolTipText(GMPLSHelper.adjName);
		nameField.setBackground(GMPLSConfig.color);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(l);
		namePanel.add(Box.createHorizontalStrut(5));
		namePanel.add(nameField);
		namePanel.add(Box.createHorizontalStrut(5));
		p.add(namePanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel p1 = new JPanel();
		p1.setOpaque(false);
		p1.setLayout(new BoxLayout(p1, BoxLayout.Y_AXIS));
		p1.setBorder(BorderFactory.createTitledBorder("Ctrl channels"));
		
		JPanel ctrlPanel = new JPanel();
		ctrlPanel.setOpaque(false);
		ctrlPanel.setLayout(new BoxLayout(ctrlPanel, BoxLayout.X_AXIS));
		ctrlField = new JComboBox(ctrlCh);
		addCtrl = new JButton("Add");
		addCtrl.setToolTipText(GMPLSHelper.adjAddCtrl);
		addCtrl.addActionListener(this);
		addCtrl.setEnabled(true);
		delCtrl = new JButton("Del");
		delCtrl.setToolTipText(GMPLSHelper.adjDelCtrl);
		delCtrl.addActionListener(this);
		delCtrl.setEnabled(false);
		ctrlPanel.add(Box.createHorizontalStrut(5));
		ctrlPanel.add(ctrlField);
		ctrlPanel.add(Box.createHorizontalStrut(5));
		ctrlPanel.add(addCtrl);
		ctrlPanel.add(Box.createHorizontalStrut(5));
		ctrlPanel.add(delCtrl);
		ctrlPanel.add(Box.createHorizontalStrut(5));
		
		p1.add(Box.createVerticalStrut(5));
		p1.add(ctrlPanel);
		p1.add(Box.createVerticalStrut(5));
		
		ctrlPanel = new JPanel();
		ctrlPanel.setOpaque(false);
		ctrlPanel.setLayout(new BoxLayout(ctrlPanel, BoxLayout.Y_AXIS));
		l = new JLabel("List of currently selected ctrl channels:");
		JPanel p2 = new JPanel();
		p2.setOpaque(false);
		p2.setLayout(new FlowLayout());
		p2.add(l);
		ctrlPanel.add(p2);
		selectedCtrlListModel = new DefaultListModel();
		selectedCtrlList = new JList(selectedCtrlListModel);
		selectedCtrlList.addListSelectionListener(this);
		p2 = new JPanel();
		p2.setOpaque(false);
		p2.setLayout(new BoxLayout(p2, BoxLayout.X_AXIS));
		p2.add(Box.createHorizontalStrut(5));
		p2.add(new JScrollPane(selectedCtrlList));
		p2.add(Box.createHorizontalStrut(5));
		ctrlPanel.add(p2);
		p1.add(ctrlPanel);

		p.add(p1);
		p.add(Box.createVerticalStrut(5));
		
		JPanel rridPanel = new JPanel();
		rridPanel.setOpaque(false);
		rridPanel.setLayout(new BoxLayout(rridPanel, BoxLayout.X_AXIS));
		l = new JLabel("Remote RID: ");
		l.setToolTipText(GMPLSHelper.adjRemoteRid);
		rRidField = new JTextField();
		rRidField.setToolTipText(GMPLSHelper.adjRemoteRid);
		rRidField.setBackground(GMPLSConfig.color);
		rridPanel.add(Box.createHorizontalStrut(5));
		rridPanel.add(l);
		rridPanel.add(Box.createHorizontalStrut(5));
		rridPanel.add(rRidField);
		rridPanel.add(Box.createHorizontalStrut(5));
		p.add(rridPanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel metricPanel = new JPanel();
		metricPanel.setOpaque(false);
		metricPanel.setLayout(new BoxLayout(metricPanel, BoxLayout.X_AXIS));
		l = new JLabel("Metric: ");
		l.setToolTipText(GMPLSHelper.adjMetric);
		metricField = new JTextField();
		metricField.setToolTipText(GMPLSHelper.adjMetric);
		metricField.setBackground(GMPLSConfig.color);
		metricPanel.add(Box.createHorizontalStrut(5));
		metricPanel.add(l);
		metricPanel.add(Box.createHorizontalStrut(5));
		metricPanel.add(metricField);
		metricPanel.add(Box.createHorizontalStrut(5));
		p.add(metricPanel);
		p.add(Box.createVerticalStrut(5));
		
		JPanel adjTypePanel = new JPanel();
		adjTypePanel.setOpaque(false);
		adjTypePanel.setLayout(new BoxLayout(adjTypePanel, BoxLayout.X_AXIS));
		l = new JLabel("Adjacency Type: ");
		l.setToolTipText(GMPLSHelper.adjType);
		adjTypeField = new JComboBox(adjTypes);
		adjTypeField.setToolTipText(GMPLSHelper.adjType);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		adjTypePanel.add(l);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		adjTypePanel.add(adjTypeField);
		adjTypePanel.add(Box.createHorizontalStrut(5));
		p.add(adjTypePanel);
		p.add(Box.createVerticalStrut(5));

		JPanel mPanel = new JPanel();
		mPanel.setOpaque(false);
		mPanel.setLayout(new BoxLayout(mPanel, BoxLayout.X_AXIS));
		modify = new JButton("Modify");
		modify.setToolTipText(GMPLSHelper.adjAdd);
		modify.addActionListener(this);
		cancel = new JButton("Cancel");
		cancel.addActionListener(this);
		mPanel.add(Box.createHorizontalStrut(5));
		mPanel.add(cancel);
		mPanel.add(Box.createHorizontalStrut(5));
		mPanel.add(modify);
		mPanel.add(Box.createHorizontalStrut(5));
		p.add(Box.createVerticalStrut(5));
		p.add(mPanel);
		p.add(Box.createVerticalStrut(5));
		
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		getContentPane().add(p, BorderLayout.CENTER);
		setSize(500, 400);
		setResizable(false);
	}
	
	public void actionPerformed(ActionEvent e) {
		
		Object source = e.getSource();
		if (source.equals(addCtrl)) {
			String ctrl = (String)ctrlField.getSelectedItem();
			for (int i=0; i<selectedCtrlListModel.getSize(); i++) {
				if (ctrl.equals(selectedCtrlListModel.get(i).toString())) return;
			}
			selectedCtrlListModel.add(0, ctrl);
			return;
		}
		if (source.equals(delCtrl)) {
			String ctrl = (String)selectedCtrlList.getSelectedValue();
			if (ctrl == null) return;
			for (int i=0; i<selectedCtrlListModel.getSize(); i++) {
				if (ctrl.equals(selectedCtrlListModel.get(i).toString())) {
					selectedCtrlListModel.remove(i);
					return;
				}
			}
			return;
		}
		if (source.equals(modify)) {
			name = nameField.getText();
			if (name == null || name.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the name of the adjancency");
				return;
			}
			ctrlCh.clear();
			for (int i=0; i<selectedCtrlListModel.getSize(); i++)
				ctrlCh.add(selectedCtrlListModel.get(i));
			if (ctrlCh.size() == 0) {
				GMPLSAdmin.showError(this, "Please enter at least one channel");
				return;
			}
			rRid = rRidField.getText();
			if (rRid == null || rRid.length() == 0) {
				GMPLSAdmin.showError(this, "Please enter the remote rid");
				return;
			}
			metric = metricField.getText();
			adjType = (String)adjTypeField.getSelectedItem();
			ret = OK;
			setVisible(false);
			return;
		}
		if(source.equals(cancel)) {
			name = null;
			ctrlCh = null;
			rRid = null;
			metric = null;
			adjType = null;
			ret = CANCEL;
			setVisible(false);
			return;
		}
	}

	public void windowOpened(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		name = null;
		ctrlCh = null;
		rRid = null;
		metric = null;
		adjType = null;
		ret = CANCEL;
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting() == false) {
	        if (selectedCtrlList.getSelectedIndex() == -1) {
				delCtrl.setEnabled(false);
	        } else {
				delCtrl.setEnabled(true);
	        }
		}
	}

	/** Just for testing */
	public static void main(String args[]) {
		JFrame f = new JFrame();
		f.setSize(200, 200);
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setVisible(true);
		AdjDialog d = new AdjDialog(f, new String[] { "e1", "e2"} );
		d.setVisible(true);
	}

} // end of class AdjDialog

