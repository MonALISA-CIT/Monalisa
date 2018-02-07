package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.text.StyledDocument;

import lia.net.topology.Link;
import lia.net.topology.LinkState;
import lia.net.topology.Port.PortType;
import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import lia.net.topology.opticalswitch.OpticalSwitchType;

public class LargeFDXOpticalPanel extends JPanel implements MouseMotionListener, MouseListener {

	private JToolTip parent1;
	private JTabbedPane pparent;
	
	TreeSet<OSPort> oPorts;
	Hashtable<OSPort, Color>  connectionsIn;
	Hashtable<OSPort, Color> connectionsOut;
	Hashtable<OSPort, Boolean> portInLight;
	Hashtable<OSPort, Boolean> portOutLight;
	
	boolean updating = true;
	
	public Popup popup = null;
	private PopupPanel popupPanel = null;
	
	private String tooltipText = null;
	
	protected Color unconnectedColor = new Color(255, 0, 0, 160);
	protected BasicStroke stroke = new BasicStroke(2.0f);
	
	private Font font = new Font("Arial", Font.BOLD, 12);

	final int rW = 12;

	private Font nrFont = new Font("SansSerif", Font.BOLD, rW - 3);
	private Font portFont = new Font("Arial", Font.BOLD, 10);
	
	/** A very dark red color. */
	public static final Color VERY_DARK_RED = new Color(0x80, 0x00, 0x00);

	/** A dark red color. */
	public static final Color DARK_RED = new Color(0xc0, 0x00, 0x00);

	/** A light red color. */
	public static final Color LIGHT_RED = new Color(0xFF, 0x40, 0x40);

	/** A very light red color. */
	public static final Color VERY_LIGHT_RED = new Color(0xFF, 0x80, 0x80);

	/** A very dark yellow color. */
	public static final Color VERY_DARK_YELLOW = new Color(0x80, 0x80, 0x00);

	/** A dark yellow color. */
	public static final Color DARK_YELLOW = new Color(0xC0, 0xC0, 0x00);

	/** A light yellow color. */
	public static final Color LIGHT_YELLOW = new Color(0xFF, 0xFF, 0x40);

	/** A very light yellow color. */
	public static final Color VERY_LIGHT_YELLOW = new Color(0xFF, 0xFF, 0x80);

	/** A very dark green color. */
	public static final Color VERY_DARK_GREEN = new Color(0x00, 0x80, 0x00);

	/** A dark green color. */
	public static final Color DARK_GREEN = new Color(0x00, 0xC0, 0x00);

	/** A light green color. */
	public static final Color LIGHT_GREEN = new Color(0x40, 0xFF, 0x40);

	/** A very light green color. */
	public static final Color VERY_LIGHT_GREEN = new Color(0x80, 0xFF, 0x80);

	/** A very dark cyan color. */
	public static final Color VERY_DARK_CYAN = new Color(0x00, 0x80, 0x80);

	/** A dark cyan color. */
	public static final Color DARK_CYAN = new Color(0x00, 0xC0, 0xC0);

	/** A light cyan color. */
	public static final Color LIGHT_CYAN = new Color(0x40, 0xFF, 0xFF);

	/** Aa very light cyan color. */
	public static final Color VERY_LIGHT_CYAN = new Color(0x80, 0xFF, 0xFF);

	/** A very dark blue color. */
	public static final Color VERY_DARK_BLUE = new Color(0x00, 0x00, 0x80);

	/** A dark blue color. */
	public static final Color DARK_BLUE = new Color(0x00, 0x00, 0xC0);

	/** A light blue color. */
	public static final Color LIGHT_BLUE = new Color(0x40, 0x40, 0xFF);

	/** A very light blue color. */
	public static final Color VERY_LIGHT_BLUE = new Color(0x80, 0x80, 0xFF);

	/** A very dark magenta/purple color. */
	public static final Color VERY_DARK_MAGENTA = new Color(0x80, 0x00, 0x80);

	/** A dark magenta color. */
	public static final Color DARK_MAGENTA = new Color(0xC0, 0x00, 0xC0);

	/** A light magenta color. */
	public static final Color LIGHT_MAGENTA = new Color(0xFF, 0x40, 0xFF);

	/** A very light magenta color. */
	public static final Color VERY_LIGHT_MAGENTA = new Color(0xFF, 0x80, 0xFF);
	
	public static final Color pending1 = new Color(99, 130, 191);
	public static final Color pending2 = new Color(239, 207, 79);
	
	int step = 1;
	
	public static Color  colorSupply[] = new Color[] {
            Color.yellow,
            Color.cyan,
            Color.pink,
            Color.gray,
            new Color(154, 211, 223),
            DARK_YELLOW,
            DARK_MAGENTA,
            DARK_CYAN,
            Color.darkGray,
            LIGHT_BLUE,
            LIGHT_YELLOW,
            Color.orange,
            LIGHT_MAGENTA,
            LIGHT_CYAN,
            Color.lightGray,
            VERY_DARK_BLUE,
            VERY_DARK_YELLOW,
            VERY_DARK_MAGENTA,
            VERY_DARK_CYAN,
            VERY_LIGHT_BLUE,
            VERY_LIGHT_YELLOW,
            VERY_LIGHT_MAGENTA,
            Color.magenta,
            VERY_LIGHT_CYAN
        };
	
	Vector<Color> assignedColors; 
	
	Hashtable<OSPort, Rectangle> portInForms = new Hashtable<OSPort, Rectangle>();
	Hashtable<OSPort, Rectangle> portOutForms = new Hashtable<OSPort, Rectangle>();
	
	Hashtable portInVsLink;
	Hashtable portOutVsLink;
	
	OSPort portInOver = null;
	OSPort portOutOver = null;
	
	private boolean hasMouse = false; 
	
	private final boolean isIn;
	
	public LargeFDXOpticalPanel(JToolTip parent, JTabbedPane pparent, boolean isIn) {
		this.parent1 = parent;
		this.pparent = pparent;
		this.isIn = isIn;
		oPorts = new TreeSet<OSPort>();
		connectionsIn = new Hashtable<OSPort, Color>();
		connectionsOut = new Hashtable<OSPort, Color>();
		portInVsLink = new Hashtable();
		portOutVsLink = new Hashtable();
		portInLight = new Hashtable<OSPort, Boolean>();
		portOutLight = new Hashtable<OSPort, Boolean>();
		assignedColors = new Vector<Color>();
		addMouseMotionListener(this);
		addMouseListener(this);
		popupPanel = new PopupPanel();
	}
	
	public void newNode() {
		oPorts.clear();
		connectionsIn.clear();
		connectionsOut.clear();
		portInForms.clear();
		portOutForms.clear();
		portInVsLink.clear();
		portOutVsLink.clear();
		portInLight.clear();
		portOutLight.clear();
		assignedColors.clear();
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
		    oPorts.addAll(p);
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
			for (int i=0; i<tmpPorts.size(); i++) {
				OSPort port = (OSPort)tmpPorts.get(i);
				if (!oPorts.contains(port))
					oPorts.add(port);
			}
			updating = false;
			repaint();
		}
	}
	
	public synchronized Color getUniqueColor() {
		for (int i=0; i<colorSupply.length; i++) {
			if (!assignedColors.contains(colorSupply[i])) {
				assignedColors.add(colorSupply[i]);
				return colorSupply[i];
			}
		}
		return colorSupply[0];
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

	public synchronized void mouseMoved(MouseEvent e) {
		if (isIn) {
			for (Enumeration<OSPort> en = portInForms.keys(); en.hasMoreElements(); ) {
				OSPort portIn = en.nextElement();
				Rectangle rect = (Rectangle)portInForms.get(portIn);
				if (rect.contains(e.getPoint())) {
					StringBuilder buf = new StringBuilder();
					buf.append("<html><table BORDER=0 CELLSPACING=2 cellpadding=0>");
					buf.append("<tr><td bgcolor=#deeaf6>In: ").append(getOSPortLabel(portIn)).append("</td>");
					portInOver = portIn;
					if (portInVsLink.containsKey(portIn)) {
						Link link = (Link)portInVsLink.get(portIn);
						portOutOver = (OSPort)link.destinationPort();
						buf.append("<td bgcolor=#f896f2>Out: ").append(getOSPortLabel(portOutOver)).append("</td>");
					} else
						portOutOver = null;
					buf.append("</tr></table></html>");
					tooltipText = buf.toString();
					repaint();
					if (popup != null) {
						popup.hide(); popup = null;
					}
					popupPanel.setTooltip(tooltipText);
					popup = PopupFactory.getSharedInstance().getPopup(this, popupPanel, e.getXOnScreen(), e.getYOnScreen());
					popup.show();
					return;
				}
			}
		} else {
			for (Enumeration<OSPort> en = portOutForms.keys(); en.hasMoreElements(); ) {
				OSPort portOut = en.nextElement();
				Rectangle rect = (Rectangle)portOutForms.get(portOut);
				if (rect.contains(e.getPoint())) {
					StringBuilder buf = new StringBuilder();
					buf.append("<html><table BORDER=0 CELLSPACING=2 cellpadding=0>");
					buf.append("<tr><td bgcolor=#deeaf6>Out: ").append(getOSPortLabel(portOut)).append("</td>");
					portOutOver = portOut;
					if (portOutVsLink.containsKey(portOut)) {
						Link link = (Link)portOutVsLink.get(portOut);
						portInOver = (OSPort)link.sourcePort();
						buf.append("<td bgcolor=#f896f2>In: ").append(getOSPortLabel(portInOver)).append("</td>");
					} else {
						portInOver = null;
					}
					buf.append("</tr></table></html>");
					tooltipText = buf.toString();
					repaint();

					if (popup != null) {
						popup.hide(); popup = null;
					}
					popupPanel.setTooltip(tooltipText);
					popup = PopupFactory.getSharedInstance().getPopup(this, popupPanel, e.getXOnScreen(), e.getYOnScreen());
					popup.show();
					return;
				}
			}
		}
		if (popup != null) {
			popup.hide();
			popup = null;
		}
		tooltipText = null;
		if (portInOver != null || portOutOver != null) {
			portInOver = portOutOver = null;
			repaint();
		}
		portInOver = portOutOver = null;
	}
	
	public void mouseClicked(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
		hasMouse = true;
	}

	public void mouseExited(MouseEvent e) {
		hasMouse = false;
//		if (popup != null) {
//			popup.hide();
//			popup = null;
//		}
	}
	
	public boolean hasMouse() {
		return hasMouse;
	}
	
	public void drawUpdating(Graphics2D g2) {
		
		g2.setPaint(Color.white);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setPaint(new GradientPaint(0, 0, Color.red.darker(), 10, 0, Color.red.brighter(), true));
		
		String str = "Updating...";
		
		Font oldFont = g2.getFont();
		g2.setFont(new Font("Arial", Font.BOLD, 18));
		FontMetrics fm = g2.getFontMetrics(g2.getFont());
		Rectangle2D rect = fm.getStringBounds(str, g2);
		Dimension panelDimension = getSize();
		int x = (int)(panelDimension.getWidth()/2-rect.getWidth()/2);
		int y = (int)(panelDimension.getHeight()/2+rect.getHeight()/2);
		g2.drawString(str, x, y);
		g2.setFont(oldFont);
		
		if ( parent1.getWidth() != 190 || parent1.getHeight() != 190) {
			setSize(190, 190);
			setPreferredSize(new Dimension(190, 190));
			parent1.setSize(190, 190);
			parent1.setPreferredSize(new Dimension(190, 190));
			parent1.revalidate();
			if (parent1 instanceof OpticalSwitchConnectivityToolTip) {
				((OpticalSwitchConnectivityToolTip)parent1).reshow();
			}
		}
	}
	
	public synchronized void paintComponent(Graphics g) {
		if (updating || oPorts == null || (oPorts.size() == 0)) {
			drawUpdating((Graphics2D)g);
			return;
		}
		Graphics2D g2 = (Graphics2D)g;
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(Color.BLACK);
		g2.setFont(font);

		checkDimension(g2);
		if (isIn)
			drawPortsIn(g2);
		else
			drawPortsOut(g2);
//		drawPortsIn(g2);
//		drawPortsOut(g2);
	}
	
	public void setNextStep() {
		step = (step+1)%2;
//		System.out.println("paint "+step);
	}
	
	private final String getInfo(AFOXOSPort a) {
//		if (a.getSerialNumber() != null && a.getSerialNumber().length() != 0) 
//			return a.getSerialNumber();
		return a.getRow()+":"+a.getColumn();
	}
	
	private void checkDimension(Graphics2D g2) {
		
		if (parent1 == null || oPorts.size() == 0) return;
		
		int maxW = 0, maxH = 0;
		
		if ((oPorts.first().device() instanceof OpticalSwitch) && (((OpticalSwitch)oPorts.first().device()).switchType() == OpticalSwitchType.AFOX)) {
			
			// first check how many...
			
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

//			System.out.println(maxW+":"+maxH);
			
			maxW = 10+ maxW * (2 + rW);
			maxH = 15+ maxH * (4 + rW);
			
			maxW += rW + 2;
			maxH += rW + 2;
			
			if (pparent.getBorder() != null) {
				maxW += 20;
				maxH += 30;
			}
			
			if (parent1.getBorder() != null) {
				maxW += 20;
				maxH += 30;
			}
			
			int w = maxW;
			int h = maxH;
			if (w != parent1.getWidth() || h != parent1.getHeight()) {
				setSize(w, h);
				setPreferredSize(new Dimension(w, h));
				parent1.setSize(w, h);
				parent1.setPreferredSize(new Dimension(w, h));
				parent1.revalidate();
				if (parent1 instanceof OpticalSwitchConnectivityToolTip) {
					((OpticalSwitchConnectivityToolTip)parent1).reshow();
				}
			}
			
		}
	}
	
	private void drawPortsIn(Graphics2D g2) {
		
		if (oPorts == null || oPorts.size() == 0) return;
		
		portInForms.clear();

		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		String str = "In";

		int x = 2;
		if (parent1.getBorder() != null) 
			x += 6;
		int initialX = x;
		
		int y = 2;
		
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		
		int maxW = 0, maxH = 0;
		int minW = 5000, minH = 5000;
		
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
			
			int h = arrange.keySet().size() * (fm.getHeight() + 12) + 8;
			
//			System.out.println("In: "+arrange.keySet().size());
			
			if (parent1.getBorder() != null) {
				h += 15;
			}
			
			g2.drawString(str, getWidth()/2 - fm.stringWidth(str)/2, fm.getHeight());
			g2.drawLine(2, fm.getHeight()/2+2, getWidth()/2 - fm.stringWidth(str)/2 -4, fm.getHeight()/2+2);
			g2.drawLine(getWidth()/2+fm.stringWidth(str)/2+4, fm.getHeight()/2+2, getWidth()-3, fm.getHeight()/2+2);
			g2.drawLine(2, fm.getHeight()/2+2, 2, h);
			g2.drawLine(getWidth()-3, fm.getHeight()/2+2, getWidth()-3, h);
			g2.drawLine(2, h, getWidth()-3, h);
			
			// draw first row as numbers...
			
			y += fm.getHeight()+rW/2 + 2;
			x += rW + 2;
			g2.setFont(nrFont);
			FontMetrics fmm = g2.getFontMetrics();
			for (i=minW; i<=maxW; i++) {
				int fw = fmm.stringWidth(""+i);
				if (portInOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portInOver;
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
			boolean foundOne = false;
			for (Iterator<Integer> rows = arrange.keySet().iterator(); rows.hasNext(); ) {
				int r = rows.next();
				TreeMap<Integer, OSPort> co = arrange.get(r);
				g2.setFont(nrFont);
				g2.setColor(Color.black);
				int fw = fmm.stringWidth(""+r);
				if (portInOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portInOver;
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
						foundOne = true;
					}
					if (port.pendingExists()) {
						if (step == 0) 
							cc = pending1;
						else 
							cc = pending2;
					}
					boolean isLight = false;
					if (portInLight.containsKey(port))
						isLight = ((Boolean)portInLight.get(port)).booleanValue();
					if (portInOver != null && portInOver.equals(port))
						drawPortRect(g2, x, y+fm.getHeight(), cc, true, isLight);
					else
						drawPortRect(g2, x, y+fm.getHeight(), cc, false, isLight);
					portInForms.put(port, new Rectangle(x, y+fm.getHeight(), rW, rW));
					x += rW + 2;
				}
				x = initialX;
				y += rW + 2;
			}
		}
	}
	
	private void drawPortsOut(Graphics2D g2) {
		
		if (oPorts == null || oPorts.size() == 0) return;

		portOutForms.clear();
		
		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		String str = "Out";
		
		int x = 2;
		if (parent1.getBorder() != null) 
			x += 6;
		int initialX = x;
		
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		
		int maxW = 0, maxH = 0;
		int minW = 5000, minH = 5000;

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
			
			if (parent1.getBorder() != null) {
				h += 15;
			}
			
			int from = 2;
			
			g2.drawString(str, getWidth()/2 - fm.stringWidth(str)/2, fm.getHeight());
			g2.drawLine(2, fm.getHeight()/2+2, getWidth()/2 - fm.stringWidth(str)/2 -4, fm.getHeight()/2+2);
			g2.drawLine(getWidth()/2+fm.stringWidth(str)/2+4, fm.getHeight()/2+2, getWidth()-3, fm.getHeight()/2+2);
			g2.drawLine(2, fm.getHeight()/2+2, 2, h);
			g2.drawLine(getWidth()-3, fm.getHeight()/2+2, getWidth()-3, h);
			g2.drawLine(2, h, getWidth()-3, h);
			
			// draw first row as numbers...
			
			from += fm.getHeight()+rW/2 + 2;
			x += rW + 2;
			g2.setFont(nrFont);
			FontMetrics fmm = g2.getFontMetrics();
			for (int i=minW; i<=maxW; i++) {
				int fw = fmm.stringWidth(""+i);
				if (portOutOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portOutOver;
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
				if (portOutOver != null) {
					AFOXOSPort aop = (AFOXOSPort)portOutOver;
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
						if (step == 0) 
							cc = pending1;
						else
							cc = pending2;
					}
					boolean isLight = false;
					if (portOutLight.containsKey(port))
						isLight = ((Boolean)portOutLight.get(port)).booleanValue();
					if (portOutOver != null && portOutOver.equals(port))
						drawPortRect(g2, x, from+fm.getHeight(), cc, true, isLight);
					else
						drawPortRect(g2, x, from+fm.getHeight(), cc, false, isLight);
					portOutForms.put(port, new Rectangle(x, from+fm.getHeight(), rW, rW));
					x += rW + 2;
				}
				x = initialX;
				from += rW + 2;
			}
		}
	}
	
	private void drawPortRect(Graphics2D g2, int x, int y, Color color, boolean mouseOver, boolean isLight) {
		
		if (color != null) {
			g2.setColor(color);
			g2.fillRect(x+1, y+1, rW, rW);
		}
		if (mouseOver) {
			if (color == null)
				g2.setColor(Color.lightGray.darker());
			else
				if (isLight)
					g2.setColor(VERY_LIGHT_GREEN);
				else
					g2.setColor(VERY_LIGHT_RED);
			g2.drawLine(x, y, x+rW, y);
			g2.drawLine(x, y+1, x+rW, y+1);
			g2.drawLine(x, y, x, y+rW);
			g2.drawLine(x+1, y, x+1, y+rW-1);
			if (color == null)
				g2.setColor(Color.gray.darker());
			else
				if (isLight)
					g2.setColor(VERY_DARK_GREEN);
				else
					g2.setColor(VERY_DARK_RED);
			g2.drawLine(x+rW, y+2, x+rW, y+rW-1);
			g2.drawLine(x+rW+1, y+1, x+rW+1, y+rW-1);
			g2.drawLine(x+1, y+rW-1, x+rW+1, y+rW-1);
			g2.drawLine(x, y+rW, x+rW+1, y+rW);
		} else{
			if (color == null)
				g2.setColor(Color.lightGray);
			else
				if (isLight)
					g2.setColor(LIGHT_GREEN);
				else
					g2.setColor(LIGHT_RED);
			g2.drawLine(x, y, x+rW, y);
			g2.drawLine(x, y+1, x+rW, y+1);
			g2.drawLine(x, y, x, y+rW-1);
			g2.drawLine(x+1, y, x+1, y+rW-1);
			if (color == null)
				g2.setColor(Color.gray);
			else
				if (isLight)
					g2.setColor(DARK_GREEN);
				else
					g2.setColor(DARK_RED);
			g2.drawLine(x+rW, y+2, x+rW, y+rW-1);
			g2.drawLine(x+rW+1, y+1, x+rW+1, y+rW-1);
			g2.drawLine(x+2, y+rW-1, x+rW, y+rW-1);
			g2.drawLine(x+1, y+rW, x+rW, y+rW);
		}
	}
	
	 /**
     * when user over a node, return node configuration for real node ignore tooltip text
     */
    public String getToolTipText() {
    	return tooltipText;
    }

    public class PopupPanel extends JPanel {
    	
    	private JEditorPane area;
    	private String popupText = "";
    	
    	public PopupPanel() {
    		super();
    		area = new JEditorPane() {
    			public void paintComponent(Graphics g) {
    				if (System.getProperty("os.name").contains("nix")) {
    					StyledDocument doc = (StyledDocument)getDocument();
    					String text = null;
    					try {
    						text = doc.getText(0, doc.getLength());
    					} catch (Exception e) {
    						text = popupText;
    					}
    					checkDimension(text, (Graphics2D)g);
    				}
    	    		super.paintComponent(g);
    	    	}
    		};
    		area.setContentType( "text/html" );  
    		area.setEditable(false);
    		area.setFont(portFont);
    		setLayout(new BorderLayout());
    		setSize(300, 200);
    		add(area, BorderLayout.CENTER);
    	}

    	private void checkDimension(String text, Graphics2D g2) {
    		
    		FontMetrics fm = g2.getFontMetrics(portFont);
    		g2.setFont(portFont);
    		
//    		System.out.println(text);
    		
    		int w = ((int)(1.3 * fm.stringWidth(text)) - 8);
    		int h = fm.getHeight() + 16;
    		
//    		System.out.println(w+":"+h);
    		
    		if (w != getWidth() || h != getHeight()) {
    			setSize(w, h);
    			setPreferredSize(new Dimension(w, h));
    			revalidate();
    		}
    	}
    	
    	public void setTooltip(String tooltip) {
    		this.popupText = tooltip;
    		area.setText(tooltip);
    		area.revalidate();
    		area.repaint();
    	}
    }

} // end of class NewFDXOpticalPanel

