package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import lia.Monitor.monitor.AppConfig;
import lia.util.geo.iNetGeoManager;

public class iNetGeoClient extends JFrame implements ActionListener,  ListSelectionListener {

	protected iNetGeoManager owner;
	
	public iNetGeoClientNodeTable nodeTable;
	public iNetGeoClientLinkTable linkTable;
	protected JList urlList;
	protected JButton addLink;
	protected JButton removeLink;
	protected JButton refreshLinks;
	
	public iNetGeoClient(iNetGeoManager owner) {
		
		super("GeoParameters");
		this.owner = owner;
		getContentPane().setLayout(new BorderLayout());
		
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		
		JPanel upPanel = new JPanel();
		upPanel.setOpaque(false);
		upPanel.setLayout(new GridLayout(0, 2));
		mainPanel.add(upPanel);
		
		DefaultListModel model = new DefaultListModel();
		urlList = new JList(model);
		urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		urlList.addListSelectionListener(this);
		upPanel.add(new JScrollPane(urlList));
		String configFileAddr = AppConfig.getProperty("lia.util.geo.iNetGeoConfig", "");
		String addresses[] = configFileAddr.split(",");
		for (int i=0; i<addresses.length; i++) model.addElement(addresses[i]);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setOpaque(false);
		buttonPanel.setLayout(new GridLayout(0, 1));
		upPanel.add(buttonPanel);
		
		addLink = new JButton("Add url");
		addLink.addActionListener(this);
		removeLink = new JButton("Remove url");
		removeLink.setEnabled(false);
		removeLink.addActionListener(this);
		refreshLinks = new JButton("Refresh parameters");
		refreshLinks.addActionListener(this);
		buttonPanel.add(addLink);
		buttonPanel.add(removeLink);
		buttonPanel.add(refreshLinks);
		
		JPanel downPanel = new JPanel();
		downPanel.setOpaque(false);
		downPanel.setLayout(new GridLayout(0, 2));
		mainPanel.add(downPanel);
		
		JPanel nodePanel = new JPanel();
		nodePanel.setOpaque(false);
		nodePanel.setLayout(new BoxLayout(nodePanel, BoxLayout.Y_AXIS));
		JPanel l = new JPanel();
		l.setOpaque(false);
		l.setLayout(new FlowLayout());
		l.add(new JLabel("City Parameters"));
		nodePanel.add(l);
		nodeTable = new iNetGeoClientNodeTable();
		nodePanel.add(nodeTable);
		JPanel linkPanel = new JPanel();
		linkPanel.setOpaque(false);
		linkPanel.setLayout(new BoxLayout(linkPanel, BoxLayout.Y_AXIS));
		l = new JPanel();
		l.setOpaque(false);
		l.setLayout(new FlowLayout());
		l.add(new JLabel("Link Parameters"));
		linkPanel.add(l);
		linkTable = new iNetGeoClientLinkTable();
		linkPanel.add(linkTable);
		downPanel.add(nodePanel);
		downPanel.add(linkPanel);
		
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		setSize(400, 200);
	}
	
	public void refreshURLs(String[] url) {
		
		DefaultListModel model = (DefaultListModel)urlList.getModel();
		model.clear();
		if (url == null) return;
		for (int i=0; i<url.length; i++) model.addElement(url[i]);
	}
	
	public void actionPerformed(ActionEvent e) {
		
		if (owner == null) return;
		Object src = e.getSource();
		if (src.equals(addLink)) {
			String name = JOptionPane.showInputDialog(this, "Enter the URL to check...");
			if (name != null) {
				if (!owner.addURL(name)) return;
				((DefaultListModel)urlList.getModel()).addElement(name);
			}
			return;
		}
		if (src.equals(removeLink)) {
			String url = (String)urlList.getSelectedValue();
			if (url == null) return;
			((DefaultListModel)urlList.getModel()).removeElement(url);
			owner.removeURL(url);
			return;
		}
		if (src.equals(refreshLinks)) {
			owner.refresh();
			return;
		}
	}

	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting()) {
			removeLink.setEnabled(true);
		}
	}
	
} // end of class iNetGeoClient


