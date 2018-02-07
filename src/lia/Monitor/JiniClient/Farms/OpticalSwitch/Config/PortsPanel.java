package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JPanel;

import lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho.LargeFDXOpticalPanel;
import lia.net.topology.Link;
import lia.net.topology.LinkState;
import lia.net.topology.Port.PortType;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.net.topology.opticalswitch.OpticalSwitchType;

/**
 * The panel consisting of all the ports (in or out) of an optical switch...
 */
public class PortsPanel extends JPanel implements MouseListener, MouseMotionListener {

	// constants for the different states of any port
	public static final int inactive = 0;
	public static final int unconnected = 1;
	public static final int connected = 2;
	public static final int fault = 4;

	public static final int unconnected_light = 5;
	public static final int unconnected_innolight = 6;
	public static final int unconnected_outnolight = 7;

	public static final int connected_nolight = 3;

	private Font font = new Font("Arial", Font.BOLD, 12);

	// constants for the different states colors
	public static final Color inactiveColor = Color.gray;
	public static final Color unconnectedLight = Color.white;
	public static final Color unconnectedInNoLight = Color.black;
	public static final Color unconnectedOutNoLight = Color.gray;
	public static final Color connectedColor = new Color(0, 177, 41);
	public static final Color connectedNoLightColor = new Color(255, 201, 151);
	public static final Color faultColor = new Color(255, 18, 18);

	public static final Color upperSelectionColor = new Color(70, 30, 30);
	public static final Color lowerSelectionColor = new Color(250, 60, 60);

	public static final Color upperClickedColor = new Color(70, 30, 70);
	public static final Color lowerClickedColor = new Color(250, 60, 250);

	// the font used to draw the port names...
	public static final Font portFont = new Font("Arial", Font.BOLD, 12);
	public static final Font titleFont = new Font("Arial", Font.BOLD, 14);

	public static final Stroke rectStroke = new BasicStroke(1.2f);

	public static final Color pending1 = new Color(99, 130, 191);

	final int rW = 12;

	private Font nrFont = new Font("SansSerif", Font.BOLD, rW - 3);

	TreeSet<OSPort> oPorts;
	Hashtable<OSPort, Color>  connectionsIn;
	Hashtable<OSPort, Color> connectionsOut;
	Hashtable<OSPort, Boolean> portInLight;
	Hashtable<OSPort, Boolean> portOutLight;

	Hashtable<OSPort, Rectangle> portInForms = new Hashtable<OSPort, Rectangle>();
	Hashtable<OSPort, Rectangle> portOutForms = new Hashtable<OSPort, Rectangle>();

	Hashtable portInVsLink;
	Hashtable portOutVsLink;

	boolean updating = true;

	public OSPort portInMouseOver = null;
	public OSPort portOutMouseOver = null;

	public OSPort portInClicked = null;
	public OSPort portOutClicked = null;

	private Vector assignedColors;

	private ConfigFrame parent;

	private String tooltipText = null;

	public PortsPanel(ConfigFrame parent) {

		super();
		this.parent = parent;
		addMouseListener(this);
		addMouseMotionListener(this);
		oPorts = new TreeSet<OSPort>();
		connectionsIn = new Hashtable<OSPort, Color>();
		connectionsOut = new Hashtable<OSPort, Color>();
		portInVsLink = new Hashtable();
		portOutVsLink = new Hashtable();
		portInLight = new Hashtable<OSPort, Boolean>();
		portOutLight = new Hashtable<OSPort, Boolean>();
		assignedColors = new Vector<Color>();
		setToolTipText("test");
	}

	public synchronized Color getUniqueColor() {
		for (int i=0; i<LargeFDXOpticalPanel.colorSupply.length; i++) {
			if (!assignedColors.contains(LargeFDXOpticalPanel.colorSupply[i])) {
				assignedColors.add(LargeFDXOpticalPanel.colorSupply[i]);
				return LargeFDXOpticalPanel.colorSupply[i];
			}
		}
		return LargeFDXOpticalPanel.colorSupply[0];
	}

	/**
	 * Method that updates the panel based on the old OpticalSwitchInfo type of result
	 * @param info
	 */
	public void update(OpticalSwitch info) {
		synchronized(this) {
			updating = true;
			repaint();
		}
		Hashtable<OSPort, Color> tmpConnectionsIn = new Hashtable<OSPort, Color>();
		Hashtable<OSPort, Color> tmpConnectionsOut = new Hashtable<OSPort, Color>();
		Hashtable<OSPort, Link> tmpPortIn = new Hashtable<OSPort, Link>();
		Hashtable<OSPort, Link> tmpPortOut = new Hashtable<OSPort, Link>();
		Hashtable<OSPort, Boolean> tmpPortInLight = new Hashtable<OSPort, Boolean>();
		Hashtable<OSPort, Boolean> tmpPortOutLight = new Hashtable<OSPort, Boolean>();
		Vector<OSPort> tmpPorts = new Vector<OSPort>();
		assignedColors.clear();
		// first check the ports...
		Set<OSPort> p = info.getPortSet();
		if (p != null) {
			synchronized (oPorts) {
				oPorts.addAll(p);
			}
		}
		// next check the cross connects...
		Link l[] = info.getCrossConnects();
		if (l != null) {

			for (int i=0; i<l.length; i++) {
				Link link = l[i];
				Color c = getUniqueColor();
				OSPort inPort = null;
				OSPort outPort = null;
				if (link.sourcePort().type() == PortType.INPUT_PORT) {
					inPort = (OSPort)link.sourcePort();
					outPort = (OSPort)link.destinationPort();
				} else {
					inPort = (OSPort)link.destinationPort();
					outPort = (OSPort)link.sourcePort();
				}
				tmpConnectionsIn.put(inPort, c);
				tmpConnectionsOut.put(outPort, c);
				tmpPortIn.put(inPort, link);
				tmpPortOut.put(outPort, link);
				if (!tmpPorts.contains(inPort))
					tmpPorts.add(inPort);
				if (!tmpPorts.contains(outPort))
					tmpPorts.add(outPort);

				LinkState state = link.getStates().iterator().next();
				tmpPortInLight.put((OSPort)link.sourcePort(), state.equals(LinkState.CONNECTED) || state.equals(LinkState.ML_CONN));
				tmpPortInLight.put((OSPort)link.destinationPort(), state.equals(LinkState.CONNECTED) || state.equals(LinkState.ML_CONN));
			}
		}

		synchronized(this) {
			connectionsIn = null;
			connectionsIn = tmpConnectionsIn;
			connectionsOut = null;
			connectionsOut = tmpConnectionsOut;
			portInVsLink = null;
			portInVsLink = tmpPortIn;
			portOutVsLink = null;
			portOutVsLink = tmpPortOut;
			portInLight = null;
			portInLight = tmpPortInLight;
			portOutLight = null;
			portOutLight = tmpPortOutLight;
			synchronized (oPorts) {
				for (int i=0; i<tmpPorts.size(); i++) {
					OSPort port = (OSPort)tmpPorts.get(i);
					if (!oPorts.contains(port))
						oPorts.add(port);
				}
			}
			
			updating = false;
			repaint();
		}
	}

	public void checkDimension(Graphics2D g2) {
		if (parent == null || oPorts.size() == 0) return;

		int maxW = 0, maxH = 0;

		// first check how many...
		synchronized (oPorts) {
			TreeMap<Integer, TreeMap<Integer, OSPort>> ports = new TreeMap<Integer, TreeMap<Integer, OSPort>>();
			for (Iterator<OSPort> it = oPorts.iterator(); it.hasNext(); ) {
				OSPort p = it.next();
				if (p.type() == PortType.INPUT_PORT) {
					AFOXOSPort a = (AFOXOSPort)p;
					int c = a.getColumn();
					int r = a.getRow();
					TreeMap<Integer, OSPort> cols = null;
					if (ports.containsKey(r))
						cols = ports.get(r);
					else {
						cols = new TreeMap<Integer, OSPort>();
						ports.put(r, cols);
					}
					cols.put(c, a);
					if (maxW < c) maxW = c;
					if (maxH < r) maxH = r;
				}
			}
		}

		//			System.out.println(maxW+":"+maxH);

		maxW = 10+ maxW * (2 + rW);
		maxH = 15+ maxH * (4 + rW);
		
		maxH *= 2;

		maxW += rW + 20;
		maxH += rW + 170;

		int w = maxW;
		int h = maxH;
		if (w != parent.getWidth() || h != parent.getHeight()) {
			setSize(w, h);
			setPreferredSize(new Dimension(w, h));
			parent.setSize(w, h);
			parent.setPreferredSize(new Dimension(w, h));
			revalidate();
		}
	}

	private int drawPortsIn(Graphics2D g2, int startY) {

		if (oPorts == null || oPorts.size() == 0) return startY;

		portInForms.clear();

		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();

		int x = 2;
		int initialX = x;

		int y = 2 + startY;

		g2.setFont(portFont);
		fm = g2.getFontMetrics();

		int maxW = 0, maxH = 0;
		int minW = 5000, minH = 5000;

		synchronized (oPorts) {
		if ((oPorts.first().device() instanceof OpticalSwitch) && (((OpticalSwitch)oPorts.first().device()).switchType() == OpticalSwitchType.AFOX)) {

			int i =0;

			TreeMap<Integer, TreeMap<Integer, OSPort>> arrange = new TreeMap<Integer, TreeMap<Integer,OSPort>>();
			for (Iterator<OSPort> it = oPorts.iterator(); it.hasNext(); ) {
				AFOXOSPort p = (AFOXOSPort)it.next();
				if (p.type() != PortType.INPUT_PORT) continue;
				int row = p.getRow();
				int col = p.getColumn();
				if (maxW < col) maxW = col;
				if (maxH < row) maxH = row;
				if (minW > col) minW = col;
				if (minH > row) minH = row;
				TreeMap<Integer, OSPort> c = null;
				if (arrange.containsKey(row)) {
					c = arrange.get(row);
				} else {
					c = new TreeMap<Integer, OSPort>();
					arrange.put(row, c);
				}
				c.put(col, p);
			}

			// draw first row as numbers...

			x += rW + 2;
			g2.setFont(nrFont);
			FontMetrics fmm = g2.getFontMetrics();
			for (i=minW; i<=maxW; i++) {
				int fw = fmm.stringWidth(""+i);
				if (portInMouseOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portInMouseOver;
					if (aop.getColumn() == i) {
						g2.setColor(Color.red);
					}
				}
				g2.drawString(""+i, x + rW /2 - fw/2 + 1 , y);
				g2.setColor(Color.black);
				g2.drawLine(x, y - fm.getHeight() / 2, x, y+ - fm.getHeight()/2 + rW);
				x += rW + 2;
			}
			y -= rW/2 + fm.getHeight()/2;
			g2.setFont(font);
			x = initialX;
			for (Iterator<Integer> rows = arrange.keySet().iterator(); rows.hasNext(); ) {
				int r = rows.next();
				TreeMap<Integer, OSPort> co = arrange.get(r);
				g2.setFont(nrFont);
				g2.setColor(Color.black);
				int fw = fmm.stringWidth(""+r);
				if (portInMouseOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portInMouseOver;
					if (aop.getRow() == r)
						g2.setColor(Color.red);
				}
				g2.drawString(""+r, x + rW /2 - fw/2 + 1 , y+fm.getHeight() + rW);
				g2.setColor(Color.black);
				g2.drawLine(x + 1, y + fm.getHeight() / 2 + rW / 2 + 1, x + rW + 1, y + fm.getHeight() / 2 + rW/2 + 1);
				x += rW + 2;
				g2.setFont(font);
				for (Iterator<Integer> cols = co.keySet().iterator(); cols.hasNext(); ) {
					int c = cols.next();
					AFOXOSPort port = (AFOXOSPort)co.get(c);
					if (port.type() != PortType.INPUT_PORT) continue;
					Color cc = null;
					if (connectionsIn.containsKey(port)) {
						cc = connectionsIn.get(port);
					}
					if (port.pendingExists()) {
						cc = pending1;
					}
					boolean isLight = false;
					if (portInLight.containsKey(port))
						isLight = ((Boolean)portInLight.get(port)).booleanValue();
					if (portInMouseOver != null && portInMouseOver.equals(port))
						drawPortRect(g2, x, y+fm.getHeight(), cc, true, isLight, portInClicked!=null && portInClicked.equals(port));
					else
						drawPortRect(g2, x, y+fm.getHeight(), cc, false, isLight, portInClicked!=null && portInClicked.equals(port));
					portInForms.put(port, new Rectangle(x, y+fm.getHeight(), rW, rW));
					x += rW + 2;
				}
				x = initialX;
				y += rW + 2;
			}
		}
		}
		return y;
	}

	private int drawPortsOut(Graphics2D g2, int startY) {

		if (oPorts == null || oPorts.size() == 0) return startY;

		portOutForms.clear();

		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();

		int x = 2;
		int initialX = x;

		g2.setFont(portFont);
		fm = g2.getFontMetrics();

		int maxW = 0, maxH = 0;
		int minW = 5000, minH = 5000;

		synchronized (oPorts) {
		if ((oPorts.first().device() instanceof OpticalSwitch) && (((OpticalSwitch)oPorts.first().device()).switchType() == OpticalSwitchType.AFOX)) {

			// first check how many...
			int maxRow = 0;
			int maxCol = 0;

			for (Iterator<OSPort> it = oPorts.iterator(); it.hasNext(); ) {
				OSPort p = it.next();
				if (p.type() == PortType.OUTPUT_PORT) {
					AFOXOSPort a = (AFOXOSPort)p;
					if (maxRow < a.getRow()) maxRow = a.getRow();
					if (maxCol < a.getColumn()) maxCol = a.getColumn();
				}
			}

			TreeMap<Integer, TreeMap<Integer, OSPort>> arrange = new TreeMap<Integer, TreeMap<Integer,OSPort>>();
			for (Iterator<OSPort> it = oPorts.iterator(); it.hasNext(); ) {
				AFOXOSPort p = (AFOXOSPort)it.next();
				if (p.type() != PortType.OUTPUT_PORT) continue;
				int row = p.getRow();
				int col = p.getColumn();
				if (maxW < col) maxW = col;
				if (maxH < row) maxH = row;
				if (minW > col) minW = col;
				if (minH > row) minH = row;
				TreeMap<Integer, OSPort> c = null;
				if (arrange.containsKey(row)) {
					c = arrange.get(row);
				} else {
					c = new TreeMap<Integer, OSPort>();
					arrange.put(row, c);
				}
				c.put(col, p);
			}

			int h = arrange.keySet().size() * (fm.getHeight() + 12) + 4;

			int from = 2 + startY;

			// draw first row as numbers...

			x += rW + 2;
			g2.setFont(nrFont);
			FontMetrics fmm = g2.getFontMetrics();
			for (int i=minW; i<=maxW; i++) {
				int fw = fmm.stringWidth(""+i);
				if (portOutMouseOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portOutMouseOver;
					if (aop.getColumn() == i) 
						g2.setColor(Color.red);
				}
				g2.drawString(""+i, x + rW /2 - fw/2 + 1 , from);
				g2.setColor(Color.black);
				g2.drawLine(x, from - fm.getHeight() / 2, x, from+ - fm.getHeight()/2 + rW);
				x += rW + 2;
			}
			from -= rW/2 + fm.getHeight()/2;
			g2.setFont(font);
			x = initialX;

			for (Iterator<Integer> rows = arrange.keySet().iterator(); rows.hasNext(); ) {
				int r = rows.next();
				TreeMap<Integer, OSPort> co = arrange.get(r);
				g2.setFont(nrFont);
				g2.setColor(Color.black);
				int fw = fmm.stringWidth(""+r);
				if (portOutMouseOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portOutMouseOver;
					if (aop.getRow() == r)
						g2.setColor(Color.red);
				}
				g2.drawString(""+r, x + rW /2 - fw/2 + 1 , from+fm.getHeight() + rW);
				g2.setColor(Color.black);
				g2.drawLine(x + 1, from + fm.getHeight() / 2 + rW / 2 + 1, x + rW + 1, from + fm.getHeight() / 2 + rW/2 + 1);

				x += rW + 2;
				g2.setFont(font);
				for (Iterator<Integer> cols = co.keySet().iterator(); cols.hasNext(); ) {
					int c = cols.next();
					AFOXOSPort port = (AFOXOSPort)co.get(c);
					if (port.type() != PortType.OUTPUT_PORT) continue;
					Color cc = null;
					if (connectionsOut.containsKey(port)) 
						cc = connectionsOut.get(port);
					if (port.pendingExists()) {
						cc = pending1;
					}
					boolean isLight = false;
					if (portOutLight.containsKey(port))
						isLight = ((Boolean)portOutLight.get(port)).booleanValue();
					if (portOutMouseOver != null && portOutMouseOver.equals(port))
						drawPortRect(g2, x, from+fm.getHeight(), cc, true, isLight, portOutClicked!=null && portOutClicked.equals(port));
					else
						drawPortRect(g2, x, from+fm.getHeight(), cc, false, isLight, portOutClicked!=null && portOutClicked.equals(port));
					portOutForms.put(port, new Rectangle(x, from+fm.getHeight(), rW, rW));
					x += rW + 2;
				}
				x = initialX;
				from += rW + 2;
			}
			return from;
		}
		}
		return startY;
	}

	private void drawPortRect(Graphics2D g2, int x, int y, Color color, boolean mouseOver, boolean isLight, boolean isClicked) {

		if (color != null) {
			g2.setColor(color);
			g2.fillRect(x+1, y+1, rW, rW);
		}
		if (isClicked) {
			g2.setColor(upperSelectionColor);
			g2.drawLine(x, y, x+rW, y);
			g2.drawLine(x, y+1, x+rW-1, y+1);
			g2.drawLine(x, y+2, x+rW-2, y+2);
			g2.drawLine(x, y, x, y+rW);
			g2.drawLine(x+1, y, x+1, y+rW-1);
			g2.drawLine(x+2, y, x+2, y+rW-2);
			g2.setColor(lowerSelectionColor);
			g2.drawLine(x+rW-2, y+2, x+rW-2, y+rW);
			g2.drawLine(x+rW-1, y+1, x+rW-1, y+rW);
			g2.drawLine(x+rW, y, x+rW, y+rW);
			g2.drawLine(x+2, y+rW-2, x+rW, y+rW-2);
			g2.drawLine(x+1, y+rW-1, x+rW, y+rW-1);
			g2.drawLine(x, y+rW, x+rW, y+rW);
			return;
		}
		if (mouseOver) {
			if (color == null)
				g2.setColor(Color.lightGray.darker());
			else
				if (isLight)
					g2.setColor(LargeFDXOpticalPanel.VERY_LIGHT_GREEN);
				else
					g2.setColor(LargeFDXOpticalPanel.VERY_LIGHT_RED);
			g2.drawLine(x, y, x+rW, y);
			g2.drawLine(x, y+1, x+rW, y+1);
			g2.drawLine(x, y, x, y+rW);
			g2.drawLine(x+1, y, x+1, y+rW-1);
			if (color == null)
				g2.setColor(Color.gray.darker());
			else
				if (isLight)
					g2.setColor(LargeFDXOpticalPanel.VERY_DARK_GREEN);
				else
					g2.setColor(LargeFDXOpticalPanel.VERY_DARK_RED);
			g2.drawLine(x+rW, y+2, x+rW, y+rW-1);
			g2.drawLine(x+rW+1, y+1, x+rW+1, y+rW-1);
			g2.drawLine(x+1, y+rW-1, x+rW+1, y+rW-1);
			g2.drawLine(x, y+rW, x+rW+1, y+rW);
		} else{
			if (color == null)
				g2.setColor(Color.lightGray);
			else
				if (isLight)
					g2.setColor(LargeFDXOpticalPanel.LIGHT_GREEN);
				else
					g2.setColor(LargeFDXOpticalPanel.LIGHT_RED);
			g2.drawLine(x, y, x+rW, y);
			g2.drawLine(x, y+1, x+rW, y+1);
			g2.drawLine(x, y, x, y+rW-1);
			g2.drawLine(x+1, y, x+1, y+rW-1);
			if (color == null)
				g2.setColor(Color.gray);
			else
				if (isLight)
					g2.setColor(LargeFDXOpticalPanel.DARK_GREEN);
				else
					g2.setColor(LargeFDXOpticalPanel.DARK_RED);
			g2.drawLine(x+rW, y+2, x+rW, y+rW-1);
			g2.drawLine(x+rW+1, y+1, x+rW+1, y+rW-1);
			g2.drawLine(x+2, y+rW-1, x+rW, y+rW-1);
			g2.drawLine(x+1, y+rW, x+rW, y+rW);
		}
	}

	public void paintComponent(Graphics g) {

		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g;
		g2.setColor(Color.WHITE);
		g2.fillRect(10, 10, getWidth()-20, getHeight()-20);
		g2.setColor(Color.BLACK);
		g2.setFont(font);

		checkDimension(g2);
		// draw the in title
		g2.setFont(titleFont);
		FontMetrics fm = g2.getFontMetrics();
		int titleW = fm.stringWidth("Input");
		g2.drawString("Input", (getWidth() -titleW)/2, 20);
		int startY = fm.getHeight() + 20;
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		int startRect = 20;
		// draw the ports...
		int h = drawPortsIn(g2, startY);
		startY += h;
		g2.setFont(titleFont);
		g2.setColor(Color.black);
		fm = g2.getFontMetrics();
		titleW = fm.stringWidth("Output");
		g2.drawString("Output", (getWidth()-titleW)/2, startY);
		startY +=  fm.getHeight() ;
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		startRect = 20;
		// draw the ports...
		drawPortsOut(g2, startY);
	}

	public void mouseClicked(MouseEvent e) {

		Point p = e.getPoint();
		final int modifier = InputEvent.CTRL_DOWN_MASK; //  | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
		boolean mod = (e.getModifiersEx() & modifier) != 0;
		if (mod)
			parent.mouseClicked(e);
		if (e.isConsumed()) return; 
		
		for (Enumeration<OSPort> en = portInForms.keys(); en.hasMoreElements(); ) {
			OSPort port = en.nextElement();
			Rectangle rect = (Rectangle)portInForms.get(port);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(port)) return;
				portInClicked = port;
				parent.setCurrentClickedPort(port);
				if (portInVsLink.containsKey(port)) {
					Link link = (Link)portInVsLink.get(port);
					portOutClicked = (OSPort)link.destinationPort();
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portOutClicked != null && portOutVsLink.containsKey(portOutClicked)) {
						OSPort pp = (OSPort)((Link)portOutVsLink.get(portOutClicked)).sourcePort();
						if (!pp.equals(portInClicked)) portOutClicked = null;
					}
					if (portOutClicked != null ) {
						parent.setConnectButtonState(true);
					} else {
						parent.setConnectButtonState(false);
					}
					parent.setDisconnectButtonState(false);
				}
				portInMouseOver = null;
				portOutMouseOver = null;
				parent.setCurrentConnPorts(portInClicked, portOutClicked);
				parent.setCurrentInputPort(portInClicked);
				parent.setCurrentOutputPort(portOutClicked);
				repaint();
				showPortLabels();
				return;
			}
		}
		for (Enumeration<OSPort> en = portOutForms.keys(); en.hasMoreElements(); ) {
			OSPort port = en.nextElement();
			Rectangle rect = (Rectangle)portOutForms.get(port);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(port)) return;
				portOutClicked = port;
				parent.setCurrentClickedPort(port);
				if (portOutVsLink.containsKey(port)) {
					Link link = (Link)portOutVsLink.get(port);
					portInClicked = (OSPort)link.sourcePort();
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portInClicked != null && portInVsLink.containsKey(portInClicked)) {
						OSPort pp = (OSPort)((Link)portInVsLink.get(portInClicked)).destinationPort();
						if (!pp.equals(portOutClicked)) portInClicked = null;
					}
					if (portInClicked != null ) {
						parent.setConnectButtonState(true);
					} else {
						parent.setConnectButtonState(false);
					}
					parent.setDisconnectButtonState(false);
				}
				portInMouseOver = null;
				portOutMouseOver = null;
				parent.setCurrentConnPorts(portInClicked, portOutClicked);
				parent.setCurrentOutputPort(portOutClicked);
				parent.setCurrentInputPort(portInClicked);
				repaint();
				showPortLabels();
				return;
			}
		}
		if (portInClicked != null) {
			portInClicked= null;
			parent.setConnectButtonState(false);
			parent.setDisconnectButtonState(false);
			repaint();
		}
		if (portOutClicked != null) {
			portOutClicked = null;
			parent.setConnectButtonState(false);
			parent.setDisconnectButtonState(false);
			repaint();
		}
		parent.setCurrentClickedPort(null);
		parent.setCurrentConnPorts(portInClicked, portOutClicked);
		parent.setCurrentInputPort(null);
		parent.setCurrentOutputPort(null);
		showPortLabels();
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mouseDragged(MouseEvent e) {
	}

	private final String getOSPortLabel(OSPort p) {
		StringBuilder b = new StringBuilder();
		if (p instanceof AFOXOSPort) {
			final AFOXOSPort a = (AFOXOSPort)p;
			if (a.getSerialNumber() != null) {
				b.append("RFID: ").append(a.getSerialNumber()).append("<br\\>");
			}
		} 
		b.append(p.toString());
		return b.toString();
	}

	public void mouseMoved(MouseEvent e) {

		Point p = e.getPoint();
		for (Enumeration<OSPort> en = portInForms.keys(); en.hasMoreElements(); ) {
			OSPort port = en.nextElement();
			Rectangle rect = (Rectangle)portInForms.get(port);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(port)) return;
				if (portInMouseOver != null && portInMouseOver.equals(port)) return;
				portInMouseOver = port;
				if (portInVsLink.containsKey(port)) {
					Link link = (Link)portInVsLink.get(port);
					portOutMouseOver = (OSPort)link.destinationPort();
				} else {
					portOutMouseOver = null;
				}
				
				StringBuilder buf = new StringBuilder();
				buf.append("<html><table BORDER=0 CELLSPACING=2 cellpadding=0>");
				buf.append("<tr><td bgcolor=#deeaf6>In: ").append(getOSPortLabel(portInMouseOver)).append("</td>");
				if (portInVsLink.containsKey(portInMouseOver)) {
					Link link = (Link)portInVsLink.get(portInMouseOver);
					OSPort portOutOver = (OSPort)link.destinationPort();
					buf.append("<td bgcolor=#f896f2>Out: ").append(getOSPortLabel(portOutOver)).append("</td>");
				} 
				buf.append("</tr></table></html>");
				tooltipText = buf.toString();
				
				repaint();
				showPortLabels();
				return;
			}
		}
		for (Enumeration<OSPort> en = portOutForms.keys(); en.hasMoreElements(); ) {
			OSPort port = en.nextElement();
			Rectangle rect = (Rectangle)portOutForms.get(port);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(port)) return;
				if (portOutMouseOver != null && portOutMouseOver.equals(port)) return;
				portOutMouseOver = port;
				if (portOutVsLink.containsKey(port)) {
					Link link = (Link)portOutVsLink.get(port);
					portInMouseOver = (OSPort)link.sourcePort();
				} else {
					portInMouseOver = null;
				}
				
				StringBuilder buf = new StringBuilder();
				buf.append("<html><table BORDER=0 CELLSPACING=2 cellpadding=0>");
				buf.append("<tr><td bgcolor=#deeaf6>Out: ").append(getOSPortLabel(portOutMouseOver)).append("</td>");
				if (portOutVsLink.containsKey(portOutMouseOver)) {
					Link link = (Link)portOutVsLink.get(portOutMouseOver);
					OSPort portInOver = (OSPort)link.sourcePort();
					buf.append("<td bgcolor=#f896f2>In: ").append(getOSPortLabel(portInOver)).append("</td>");
				} 
				buf.append("</tr></table></html>");
				tooltipText = buf.toString();
				
				repaint();
				showPortLabels();
				return;
			}
		}
		tooltipText = null;
		if (portInMouseOver != null) {
			portInMouseOver = null;
			repaint();
			showPortLabels();
			return;
		}
		if (portOutMouseOver != null) {
			portOutMouseOver = null;
			showPortLabels();
			repaint();
		}
	}
	
	 /**
     * when user over a node, return node configuration for real node ignore tooltip text
     */
    public String getToolTipText() {
    	return tooltipText;
    }
    
    public String getToolTipText(MouseEvent e) {
    	return tooltipText;
    }

	/** display the port labels on the config frame */
	private void showPortLabels(){
		OSPort in = portInClicked;
		if(portInMouseOver != null)
			in = portInMouseOver;
		OSPort out = portOutClicked;
		if(portOutMouseOver != null)
			out = portOutMouseOver;
		//System.out.println("inMO = "+in+" outMO = "+out);
		parent.setDeviceParams(null, getINPortLabel(in), getOUTPortLabel(out));
	}

	/** return the label for a IN port, if available */
	public String getINPortLabel(OSPort port){
		if (port == null) return "";
		return port.name() != null ? port.name() : "";
	}

	/** return the label for a OUT port, if available */
	public String getOUTPortLabel(OSPort port){
		if (port == null) return "";
		return port.name() != null ? port.name() : "";
	}
}
