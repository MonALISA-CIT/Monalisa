package lia.Monitor.JiniClient.VRVS3D;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MediaTracker;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import lia.Monitor.monitor.AppConfig;


/**
 * 
 * @author catac
 *
 * Used to display the chooser and color range for the links and bubbles
 */
public class JoptPan extends JPanel implements ActionListener {

	public String [] nodeOpts = {
		"No. of audio clients",
		"No. of video clients",
		"Total Traffic",
		"System Load",
		"No. of virtual rooms",
		"Meetings",
        "No. of users"
		};
//		"CPU Pie (user/sys/idle)"};             // in the future, maybe

	public String [] peerOpts = {
		"Last Peer quality",
		"Peer quality /15 sec",
		"Peer quality /30 sec" };
//		"Traffic" };							// in the future ...
	public JComboBox cbNodeOpts;
	public JComboBox cbPeerOpts;
	public JColorScale csNodes;
	public JColorScale csPeers;
	public JColorScale csPing;
	public JColorScale csMST;
	public JPanel parent;
	
	public JCheckBox kbShowPing;
	public JCheckBox kbShowPeers;
	public JCheckBox kbShowMST;
	
	public JPanel meetingsPanel, nodesScalePanel;//two panels that will overlap, only one being visible at a given moment of time

	public JScrollMenu communityMenuList; // menu with with current available communities for meetings...
	public JScrollMenu meetingsMenuList;//menu with meetings available each one having an asociated checkbox and color
	
	public JButton communityMenuButton;
	public JButton meetingsMenuButton;
	public MeetingsLayer meetingsUpdate;//manipulation tool for options in menu

	// what we show on the map
	public final static int NODE_AUDIO = 1;    // bubbles with nr. of audio clients
	public final static int NODE_VIDEO = 2;    // bubbles with nr. of audio clients
	public final static int NODE_TRAFFIC = 3;   // bubbles with current traffic
	public final static int NODE_LOAD = 4;     // bubbles with current load (load5)
	public final static int NODE_VIRTROOMS = 5;// bubbles with nr. of virtual rooms
	public final static int NODE_CPUPIE = 8;   // bubbles with cpu pie (user/sys/idle)
	public final static int NODE_MEETINGS = 6;   // bubbles with meetings (each with a different color)
    public final static int NODE_USERS = 7;   // bubbles with nr. of users on a node
	public final static int PEERS_QUAL2H = 11;  // peers link with quality last 2 h
	public final static int PEERS_QUAL12H = 12; // peers link with quality last 12 h
	public final static int PEERS_QUAL24H = 13; // peers link with quality last 24 h
	public final static int PEERS_TRAFFIC = 14; // peers link with current traffic

	public int nodesShow = 1;         // what do the bubbles are = one of NODE_xxx constants
	public int peersShow = 11;         // what shows the color of links = one of the LINK_xxx constants

	public boolean gmapNotif = false;
	public boolean wmapNotif = false;
	
	public final static boolean showMST = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.showMST", "true")).booleanValue();

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
	
	// constructor
	public JoptPan(JPanel parent){
		this.parent = parent;
		
		if ( parent instanceof ActionListener )
		    meetingsUpdate = new MeetingsLayer((ActionListener)parent);
		else 
		    meetingsUpdate = new MeetingsLayer(null);
		
		Font f = new Font("Arial", Font.PLAIN, 10);
		this.setFont(f);

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		cbNodeOpts = new JComboBox(nodeOpts);
		cbPeerOpts = new JComboBox(peerOpts);
		csNodes = new JColorScale();
		csPeers = new JColorScale();
		csPing = new JColorScale();
		csMST = new JColorScale();
		kbShowPing = new JCheckBox("Show", false);
		kbShowPeers = new JCheckBox("Show", true);
		kbShowMST = new JCheckBox("Show MST", false);
        Icon dnArrow = loadImage("blue_arraw_down.png"); 
        communityMenuButton = new JButton("Community", dnArrow);
        communityMenuButton.setHorizontalTextPosition(JButton.LEFT);
        communityMenuButton.setIconTextGap(10);
        communityMenuButton.setMargin(new Insets(1,1,1,1));
        communityMenuButton.setFont(communityMenuButton.getFont().deriveFont(8.0f));
        communityMenuButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
            	communityMenuList.show(meetingsPanel, e.getX()-5, e.getY()-5);			}
        });
//        communityMenuButton.addActionListener(this);
        
        meetingsMenuButton = new JButton("Meetings", dnArrow);
        meetingsMenuButton.setHorizontalTextPosition(JButton.LEFT);
        meetingsMenuButton.setIconTextGap(10);
        meetingsMenuButton.setMargin(new Insets(1,1,1,1));
        meetingsMenuButton.setFont(meetingsMenuButton.getFont().deriveFont(8.0f));
        meetingsMenuButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
            	meetingsMenuList.show(meetingsPanel, e.getX()-5, e.getY()-5);			}
        });
//        meetingsMenuButton.addActionListener(this);
        
        //meetingsMenuButton.setFont(btnFont);
        
       	communityMenuList = new JScrollMenu();
       	meetingsMenuList = new JScrollMenu();
		//add some virtual items
/*		JCheckBoxMenuItem cbmi;
		BufferedImage biColor;
		Graphics g;
		biColor = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		g = biColor.getGraphics();
		//add first menu item
		g.setColor(Color.RED);
		g.fillRoundRect(0,0, 16, 16, 10, 10);
		cbmi = new JCheckBoxMenuItem( "Team Meeting", new ImageIcon(biColor));
		meetingsMenuList.add(cbmi);
		//add second menu item
		biColor = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		g = biColor.getGraphics();
		g.setColor(Color.GREEN);
		g.fillRoundRect(0,0, 16, 16, 10, 10);
		cbmi = new JCheckBoxMenuItem( "VRVS", new ImageIcon(biColor));
		meetingsMenuList.add(cbmi);
*/
		Font f1 = new Font("Arial", Font.BOLD, 12);
		Font f2 = new Font("Arial", Font.PLAIN, 12);
		Dimension dkb = new Dimension(100, 14);
		Dimension dcb = new Dimension(150, 16);
		Dimension dpan= new Dimension(120, 30);
		Dimension dmb = new Dimension(80, 24);

		JLabel nLabel = new JLabel(" Nodes: ");
		JLabel lLabel = new JLabel("   Links: ");

		add(nLabel);
		setProp(cbNodeOpts, dpan, f2);
		add(cbNodeOpts);
		//	add the two panels that will overlap
		nodesScalePanel = new JPanel();
		meetingsPanel = new JPanel();
		nodesScalePanel.setLayout(new BorderLayout());
		meetingsPanel.setLayout(new GridLayout(2, 1));
		nodesScalePanel.add(csNodes);
		//setProp(meetingsMenuButton, dmb, f1);
		meetingsPanel.add(communityMenuButton);
		meetingsPanel.add(meetingsMenuButton);
		setProp(meetingsPanel, dmb, f1);
		//setProp(meetingsMenuList, dpan, f2);
		add(nodesScalePanel);
		add(meetingsPanel);
		meetingsPanel.hide();
		//add(Box.createGlue());
		add(lLabel);

		JPanel lPanel1 = new JPanel();
		lPanel1.setLayout(new BorderLayout());
		JLabel iLabel = new JLabel("Internet RTTime Quality");
		iLabel.setFont(f);
		lPanel1.add(iLabel, BorderLayout.CENTER);
		setProp(cbPeerOpts, dcb, f);
		lPanel1.add(cbPeerOpts, BorderLayout.SOUTH);
		setProp(lPanel1, dpan, f);
		add(lPanel1);
		
		JPanel lPanel2 = new JPanel();
		lPanel2.setLayout(new BoxLayout(lPanel2, BoxLayout.Y_AXIS));
		csPing.setPreferredSize(dkb);
		csPing.setFont1(f);
		lPanel2.add(csPing);
		csPeers.setPreferredSize(dkb);
		csPeers.setFont1(f);
		lPanel2.add(csPeers);
		add(lPanel2);
		
		JPanel lPanel3 = new JPanel();
		lPanel3.setLayout(new BoxLayout(lPanel3, BoxLayout.Y_AXIS));
		kbShowPing.setFont(f);
		kbShowPing.setPreferredSize(dkb);
		lPanel3.add(kbShowPing);
		kbShowPeers.setFont(f);
		kbShowPeers.setPreferredSize(dkb);
		lPanel3.add(kbShowPeers);
		add(lPanel3);
		
		JPanel lPanel4 = new JPanel();
		lPanel4.setLayout(new BoxLayout(lPanel4, BoxLayout.Y_AXIS));
		kbShowMST.setPreferredSize(dkb);
		kbShowPeers.setFont(f);
		kbShowMST.setAlignmentX(Component.CENTER_ALIGNMENT);
		kbShowMST.setVisible(showMST);
		lPanel4.add(kbShowMST);
		csMST.setFont1(f);
		csMST.setPreferredSize(dkb);
		csMST.setVisible(showMST);
		lPanel4.add(csMST);
		add(lPanel4);

		cbNodeOpts.addActionListener(this);
		cbPeerOpts.addActionListener(this);

		kbShowMST.addActionListener(this);
		kbShowPeers.addActionListener(this);
		kbShowPing.addActionListener(this);

		//add(new BoxLayout(this, BoxLayout.Y_AXIS));

/*		add(csLinks, BoxLayout.X_AXIS);
		add(cbLinkOpts, BoxLayout.X_AXIS);

		JLabel ll = new JLabel("       Links: ");
		//ll.setFont(f);
		add(ll, BoxLayout.X_AXIS);
		add(csNodes, BoxLayout.X_AXIS);
		add(cbNodeOpts, BoxLayout.X_AXIS);
		
		JLabel nl = new JLabel(" Nodes: ");
		//nl.setFont(f);
		add(nl, BoxLayout.X_AXIS);

		cbNodeOpts.addActionListener(this);
		cbLinkOpts.addActionListener(this);
		
		cbNodeOpts.setPreferredSize(dcb);
		cbLinkOpts.setPreferredSize(dcb);
		cbLinkOpts.setFont(f);
		cbNodeOpts.setFont(f);
*/
	}

	private void setProp(JComponent c, Dimension d, Font f){
		c.setFont(f);
		c.setPreferredSize(d);
		c.setMaximumSize(d);
		c.setMinimumSize(d);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(communityMenuButton)) {
			communityMenuList.show(meetingsPanel, 0, meetingsPanel.getHeight());
			return;
		}
		if( e.getSource().equals(meetingsMenuButton) ){
//			lastSummaryBtn = src;
			//summaryMenu.setPreferredSize(new Dimension(src.getWidth(), 130));
			meetingsMenuList.show(meetingsPanel, 0, meetingsPanel.getHeight());//meetingsPanel.getX(), this.getHeight());
			return;
		}
		gmapNotif = true;
		wmapNotif = true;
		if (e.getSource().equals(cbNodeOpts)) {
			nodesShow = 1 + cbNodeOpts.getSelectedIndex();
			if ( nodesShow == NODE_MEETINGS ) {
			    //see which panel is visible
			    if ( !meetingsPanel.isVisible() ) {
			        nodesScalePanel.hide();
			        meetingsPanel.show();
			        //meetingsPanel.repaint();
			        repaint();
			    };
			} else {
			    //check to see which panel should be visible
			    if ( meetingsPanel.isVisible() ) {
			        meetingsPanel.hide();
			        nodesScalePanel.show();
			        //nodesScalePanel.repaint();
			        repaint();
			    };
			}
			// it would be interesting to change the color palette
			// in order to have a different scale for every menu item
			//System.out.println("Nodes event");
			switch (nodesShow) {
				case NODE_AUDIO:
					//csNodes.setColors(Color.YELLOW, Color.RED);
					csNodes.setLabelFormat("###,###", "");
					break;
				case NODE_VIDEO:
					//csNodes.setColors(Color.YELLOW, Color.RED);
					csNodes.setLabelFormat("###,###", "");
					break;
				case NODE_TRAFFIC:
					//csNodes.setColors(Color.CYAN, Color.BLUE);
					csNodes.setLabelFormat("###,###.##", "Mb/s");
					break;
				case NODE_LOAD:
					//csNodes.setColors(Color.YELLOW, Color.RED);
					csNodes.setLabelFormat("###,###.##", "");
					break;
				case NODE_VIRTROOMS:
					//csNodes.setColors(Color.YELLOW, Color.RED);
					csNodes.setLabelFormat("###,###", "");
                    break;
                case NODE_USERS:
                    //csNodes.setColors(Color.YELLOW, Color.RED);
                    csNodes.setLabelFormat("###,###", "");
                    break;
			}
			parent.repaint();
		} else if (e.getSource().equals( cbPeerOpts)) {
			peersShow = 11 + cbPeerOpts.getSelectedIndex();
			//System.out.println("Peer event");
			switch (peersShow) {
				case PEERS_QUAL2H:
					//csLinks.setColors(Color.RED, Color.GREEN);
					csPeers.setLabelFormat("###.##", "%");
					break;
				case PEERS_QUAL12H:
					//csLinks.setColors(Color.RED, Color.GREEN);
					csPeers.setLabelFormat("###.##", "%");
					break;
				case PEERS_QUAL24H:
					//csLinks.setColors(Color.RED, Color.GREEN);
					csPeers.setLabelFormat("###.##", "%");
					break;
			}
			parent.repaint();
		}else if(e.getSource().equals(kbShowPing) || e.getSource().equals(kbShowPeers) || e.getSource().equals(kbShowMST)){
			//System.out.println("checkbox event");
			parent.repaint();
		}
        repaint();
	}

}
