package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;

import java.awt.BasicStroke;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.OpticalLink;
import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;

public class NewFDXOpticalPanel extends JPanel implements MouseMotionListener, MouseListener {

	private JToolTip parent;
	
	TreeSet opticalPorts;
	Hashtable  connectionsIn;
	Hashtable connectionsOut;
	Hashtable portInLight;
	Hashtable portOutLight;
	
	boolean updating = true;
	
	protected Color unconnectedColor = new Color(255, 0, 0, 160);
	protected BasicStroke stroke = new BasicStroke(2.0f);
	
	private Font font = new Font("Arial", Font.BOLD, 12);
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
	
	private static Color  colorSupply[] = new Color[] {
            Color.yellow,
            Color.cyan,
            Color.pink,
            Color.gray,
            DARK_BLUE,
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
	
	Vector assignedColors; 
	
	Hashtable portInForms = new Hashtable();
	Hashtable portOutForms = new Hashtable();
	
	Hashtable portInVsLink;
	Hashtable portOutVsLink;
	
	String portInOver = null;
	String portOutOver = null;
	
	private boolean hasMouse = false; 
	
	public NewFDXOpticalPanel(JToolTip parent) {
		
		this.parent = parent;
		opticalPorts = new TreeSet();
		connectionsIn = new Hashtable();
		connectionsOut = new Hashtable();
		portInVsLink = new Hashtable();
		portOutVsLink = new Hashtable();
		portInLight = new Hashtable();
		portOutLight = new Hashtable();
		assignedColors = new Vector();
		addMouseMotionListener(this);
		addMouseListener(this);
	}
	
	public void newNode() {
		
		opticalPorts.clear();
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
	
	public void setPortNames(ArrayList portNames) {
		
		if (portNames == null) return;
		
		synchronized (this) {
			updating = true;
			repaint();
		}

//		TreeSet tmpOpticalPorts = new TreeSet();
		
		for (Iterator it = portNames.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			if (portName == null) continue;
			if (portName.endsWith("_In"))
				portName = portName.substring(0, portName.length()-3);
			if (portName.endsWith("_Out"))
				portName = portName.substring(0, portName.length()-4);
			if (!opticalPorts.contains(portName))
				opticalPorts.add(portName);
		}
		
		synchronized (this) {
//			opticalPorts = null;
//			opticalPorts = tmpOpticalPorts;
			updating = false;
			repaint();
		}
	}
	
	/**
	 * Method that updates the panel based on the old OpticalSwitchInfo type of result
	 * @param info
	 */
	public void update(OpticalSwitchInfo info) {
		
		synchronized(this) {
			updating = true;
			repaint();
		}

		Hashtable tmpConnectionsIn = new Hashtable();
		Hashtable tmpConnectionsOut = new Hashtable();
		
		Hashtable tmpPortIn = new Hashtable();
		Hashtable tmpPortOut = new Hashtable();
		
		Hashtable tmpPortInLight = new Hashtable();
		Hashtable tmpPortOutLight = new Hashtable();
		
		Vector tmpPorts = new Vector();
		assignedColors.clear();
		
		int check = OpticalLink.CONN_FAIL;
		
		if (info.map != null)
		for (Iterator it = info.map.keySet().iterator(); it.hasNext(); ) {
			OSPort osport = (OSPort)it.next();
			if (!opticalPorts.contains(osport.name))
				opticalPorts.add(osport.name);
		}
		
		if (info.crossConnects != null)
		for (Iterator it = info.crossConnects.keySet().iterator(); it.hasNext(); ) {
			OpticalCrossConnectLink link = (OpticalCrossConnectLink)info.crossConnects.get(it.next());
			Color c = getUniqueColor();
			String inPort = null;
			String outPort = null;
			if (link.sPort.type.shortValue() == OSPort.INPUT_PORT) {
				inPort = link.sPort.name;
				outPort = link.dPort.name;
			} else {
				inPort = link.dPort.name;
				outPort = link.sPort.name;
			}
			tmpConnectionsIn.put(inPort, c);
			tmpConnectionsOut.put(outPort, c);
			tmpPortIn.put(inPort, link);
			tmpPortOut.put(outPort, link);
			if (!tmpPorts.contains(inPort))
				tmpPorts.add(inPort);
			if (!tmpPorts.contains(outPort))
				tmpPorts.add(outPort);
			if (info.map.containsKey(link.sPort)) {
				OpticalLink l = (OpticalLink)info.map.get(link.sPort);
				if ((l.state.intValue() & check) == check)
					tmpPortInLight.put(link.sPort.name, Boolean.FALSE);
				else
					tmpPortInLight.put(link.sPort.name, Boolean.TRUE);
			} else
				tmpPortInLight.put(link.sPort.name, Boolean.FALSE);
			if (info.map.containsKey(link.dPort.name)) {
				OpticalLink l = (OpticalLink)info.map.get(link.dPort);
				if ((l.state.intValue() & check) == check)
					tmpPortOutLight.put(link.dPort.name, Boolean.FALSE);
				else
					tmpPortOutLight.put(link.dPort.name, Boolean.TRUE);
			} else
				tmpPortOutLight.put(link.dPort.name, Boolean.FALSE);
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
				String portName = (String)tmpPorts.get(i);
				if (!opticalPorts.contains(portName))
					opticalPorts.add(portName);
			}
			updating = false;
			repaint();
		}
	}
	
	/**
	 * Method that updates the panel based on the old OpticalSwitchInfo type of result
	 * @param info
	 */
	public void update(ArrayList portList) {
		
		synchronized(this) {
			updating = true;
			repaint();
		}

		Hashtable tmpConnectionsIn = new Hashtable();
		Hashtable tmpConnectionsOut = new Hashtable();
		
		Hashtable tmpPortIn = new Hashtable();
		Hashtable tmpPortOut = new Hashtable();
		
		Hashtable tmpPortInLight = new Hashtable();
		Hashtable tmpPortOutLight = new Hashtable();
		
		Vector tmpPorts = new Vector();
		assignedColors.clear();
		
		for (Iterator it = portList.iterator(); it.hasNext(); ) {
			String port = (String)it.next();
			if (!opticalPorts.contains(port))
				opticalPorts.add(port);
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
				String portName = (String)tmpPorts.get(i);
				if (!opticalPorts.contains(portName))
					opticalPorts.add(portName);
			}
			updating = false;
			repaint();
		}
	}
	
	/**
	 * Method that updates the panel based on the new OSWConfig type of result
	 * @param info
	 */
	public void update(OSwConfig info) {
		
		synchronized(this) {
			updating = true;
			repaint();
		}

		Hashtable tmpConnectionsIn = new Hashtable();
		Hashtable tmpConnectionsOut = new Hashtable();
		
		Hashtable tmpPortIn = new Hashtable();
		Hashtable tmpPortOut = new Hashtable();
		
		Hashtable tmpPortInLight = new Hashtable();
		Hashtable tmpPortOutLight = new Hashtable();
		
		Vector tmpPorts = new Vector();
		assignedColors.clear();
		
		if (info.osPorts != null) {
			for (int i = 0; i<info.osPorts.length; i++) {
				final OSwPort osport = info.osPorts[i]; 
				if (osport.type == OSwPort.INPUT_PORT || osport.type == OSwPort.OUTPUT_PORT)
					if (!opticalPorts.contains(osport.name))
						opticalPorts.add(osport.name);
			}
		}

		if (info.crossConnects != null) {
			for (int i=0; i<info.crossConnects.length; i++) {
				final OSwCrossConn link = info.crossConnects[i];
				if (link.sPort.type == OSwPort.MULTICAST_PORT || link.dPort.type == OSwPort.MULTICAST_PORT) 
					continue;
				Color c = getUniqueColor();
				String inPort = null;
				String outPort = null;
				if (link.sPort.type == OSPort.INPUT_PORT) {
					inPort = link.sPort.name;
					outPort = link.dPort.name;
				} else {
					inPort = link.dPort.name;
					outPort = link.sPort.name;
				}
				tmpConnectionsIn.put(inPort, c);
				tmpConnectionsOut.put(outPort, c);
				tmpPortIn.put(inPort, link);
				tmpPortOut.put(outPort, link);
				if (!tmpPorts.contains(inPort))
					tmpPorts.add(inPort);
				if (!tmpPorts.contains(outPort))
					tmpPorts.add(outPort);
				if ((link.sPort.powerState & OSwPort.LIGHTOK) == OSwPort.LIGHTOK)
					tmpPortInLight.put(link.sPort.name, Boolean.TRUE);
				else
					tmpPortInLight.put(link.sPort.name, Boolean.FALSE);
				if ((link.dPort.powerState & OSwPort.LIGHTOK) == OSwPort.LIGHTOK)
					tmpPortOutLight.put(link.dPort.name, Boolean.TRUE);
				else
					tmpPortOutLight.put(link.dPort.name, Boolean.FALSE);
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
				String portName = (String)tmpPorts.get(i);
				if (!opticalPorts.contains(portName))
					opticalPorts.add(portName);
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

	public synchronized void mouseMoved(MouseEvent e) {
		
		for (Enumeration en = portInForms.keys(); en.hasMoreElements(); ) {
			String portInName = (String)en.nextElement();
			Rectangle rect = (Rectangle)portInForms.get(portInName);
			if (rect.contains(e.getPoint())) {
				portInOver = portInName;
				if (portInVsLink.containsKey(portInName)) {
					OpticalCrossConnectLink link = (OpticalCrossConnectLink)portInVsLink.get(portInName);
					portOutOver = link.dPort.name;
				} else
					portOutOver = null;
				repaint();
				return;
			}
		}
		for (Enumeration en = portOutForms.keys(); en.hasMoreElements(); ) {
			String portOutName = (String)en.nextElement();
			Rectangle rect = (Rectangle)portOutForms.get(portOutName);
			if (rect.contains(e.getPoint())) {
				portOutOver = portOutName;
				if (portOutVsLink.containsKey(portOutName)) {
					OpticalCrossConnectLink link = (OpticalCrossConnectLink)portOutVsLink.get(portOutName);
					portInOver = link.sPort.name;
				} else {
					portInOver = null;
				}
				repaint();
				return;
			}
		}
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
		
		if ( parent.getWidth() != 190 || parent.getHeight() != 190) {
			setSize(190, 190);
			setPreferredSize(new Dimension(190, 190));
			parent.setSize(190, 190);
			parent.setPreferredSize(new Dimension(190, 190));
			parent.revalidate();
			if (parent instanceof OpticalConnectivityToolTip) {
				((OpticalConnectivityToolTip)parent).reshow();
			}
		}
	}
	
	public synchronized void paintComponent(Graphics g) {
		
		if (updating || opticalPorts == null || (opticalPorts.size() == 0)) {
			drawUpdating((Graphics2D)g);
			return;
		}

		Graphics2D g2 = (Graphics2D)g;
		
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(Color.BLACK);
		g2.setFont(font);

		checkDimension(g2);
		
		drawPortsIn(g2);
		drawPortsOut(g2);
	}
	
	private void checkDimension(Graphics2D g2) {
		
		if (parent == null) return;
		
		FontMetrics fm = g2.getFontMetrics(portFont);
		int w = 6;
		for (Iterator it = opticalPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			w += fm.stringWidth(portName)+6+8;
		}
		w += 4;
		int h = 2 * fm.getHeight() + 70;
		if (parent.getBorder() != null) {
			w += 10;
			h += 30;
		}
		if (w != parent.getWidth() || h != parent.getHeight()) {
			setSize(w, h);
			setPreferredSize(new Dimension(w, h));
			parent.setSize(w, h);
			parent.setPreferredSize(new Dimension(w, h));
			parent.revalidate();
			if (parent instanceof OpticalConnectivityToolTip) {
				((OpticalConnectivityToolTip)parent).reshow();
			}
		}
	}
	
	private void drawPortsIn(Graphics2D g2) {
		
		if (opticalPorts == null || opticalPorts.size() == 0) return;
		
		portInForms.clear();
		
		int h = getHeight() / 2 - 4;
		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		String str = "In";
		g2.drawString(str, getWidth()/2 - fm.stringWidth(str)/2, fm.getHeight());
		g2.drawLine(2, fm.getHeight()/2+2, getWidth()/2 - fm.stringWidth(str)/2 -4, fm.getHeight()/2+2);
		g2.drawLine(getWidth()/2+fm.stringWidth(str)/2+4, fm.getHeight()/2+2, getWidth()-3, fm.getHeight()/2+2);
		g2.drawLine(2, fm.getHeight()/2+2, 2, h);
		g2.drawLine(getWidth()-3, fm.getHeight()/2+2, getWidth()-3, h);
		g2.drawLine(2, h, getWidth()-3, h);

		int x = 6;
		
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		
		for (Iterator it = opticalPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Color c = null;
			if (connectionsIn.containsKey(portName)) 
				c = (Color)connectionsIn.get(portName);
			boolean isLight = false;
			if (portInLight.containsKey(portName))
				isLight = ((Boolean)portInLight.get(portName)).booleanValue();
			if (portInOver != null && portInOver.equals(portName))
				drawPortRect(g2, portName, x, 4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20, c, true, isLight);
			else
				drawPortRect(g2, portName, x, 4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20, c, false, isLight);
			portInForms.put(portName, new Rectangle(x, 4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20));
			x += fm.stringWidth(portName)+6+8;
		}
	}
	
	private void drawPortsOut(Graphics2D g2) {
		
		if (opticalPorts == null || opticalPorts.size() == 0) return;

		portOutForms.clear();
		
		int h = getHeight() / 2 - 4;
		int from = getHeight() / 2 + 2;
		g2.setColor(Color.black);
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		String str = "Out";
		g2.drawString(str, getWidth()/2 - fm.stringWidth(str)/2, fm.getHeight() + from);
		g2.drawLine(2, fm.getHeight()/2+2+from, getWidth()/2 - fm.stringWidth(str)/2 -4, fm.getHeight()/2+2+from);
		g2.drawLine(getWidth()/2+fm.stringWidth(str)/2+4, fm.getHeight()/2+2+from, getWidth()-3, fm.getHeight()/2+2+from);
		g2.drawLine(2, fm.getHeight()/2+2+from, 2, h+from);
		g2.drawLine(getWidth()-3, fm.getHeight()/2+2+from, getWidth()-3, h+from);
		g2.drawLine(2, h+from, getWidth()-3, h+from);
		
		int x = 6;
		
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		
		for (Iterator it = opticalPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Color c = null;
			if (connectionsOut.containsKey(portName)) 
				c = (Color)connectionsOut.get(portName);
			boolean isLight = false;
			if (portOutLight.containsKey(portName))
				isLight = ((Boolean)portOutLight.get(portName)).booleanValue();
			if (portOutOver != null && portOutOver.equals(portName))
				drawPortRect(g2, portName, x, from+4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20, c, true, isLight);
			else
				drawPortRect(g2, portName, x, from+4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20, c, false, isLight);
			portOutForms.put(portName, new Rectangle(x, from+4+fm.getHeight(), fm.stringWidth(portName)+8-10, 20));
			x += fm.stringWidth(portName)+6+8;
		}
	}
	
	private void drawPortRect(Graphics2D g2, String portName, int x, int y, int w, int h,  Color color, boolean mouseOver, boolean isLight) {
		
		if (color != null) {
			g2.setColor(color);
			g2.fillRect(x+1, y+1, w+8, h-2);
		}
		if (mouseOver) {
			if (color == null)
				g2.setColor(Color.lightGray.darker());
			else
				if (isLight)
					g2.setColor(VERY_LIGHT_GREEN);
				else
					g2.setColor(VERY_LIGHT_RED);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h);
			g2.drawLine(x+1, y, x+1, y+h-1);
			g2.drawLine(x+2, y, x+2, y+h-2);
			if (color == null)
				g2.setColor(Color.gray.darker());
			else
				if (isLight)
					g2.setColor(VERY_DARK_GREEN);
				else
					g2.setColor(VERY_DARK_RED);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h);
			g2.drawLine(x+w+10, y, x+w+10, y+h);
			g2.drawLine(x+2, y+h-2, x+w+10, y+h-2);
			g2.drawLine(x+1, y+h-1, x+w+10, y+h-1);
			g2.drawLine(x, y+h, x+w+10, y+h);
		} else{
			
			if (color == null)
				g2.setColor(Color.lightGray);
			else
				if (isLight)
					g2.setColor(LIGHT_GREEN);
				else
					g2.setColor(LIGHT_RED);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h);
			g2.drawLine(x+1, y, x+1, y+h-1);
			g2.drawLine(x+2, y, x+2, y+h-2);
			if (color == null)
				g2.setColor(Color.gray);
			else
				if (isLight)
					g2.setColor(DARK_GREEN);
				else
					g2.setColor(DARK_RED);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h);
			g2.drawLine(x+w+10, y, x+w+10, y+h);
			g2.drawLine(x+2, y+h-2, x+w+10, y+h-2);
			g2.drawLine(x+1, y+h-1, x+w+10, y+h-1);
			g2.drawLine(x, y+h, x+w+10, y+h);
		}
		
		g2.setColor(Color.black);
		g2.setFont(portFont);
		int ww = g2.getFontMetrics().stringWidth(portName);
		g2.drawString(portName, x+(w+10)/2-ww/2+1, y + h/2+g2.getFontMetrics().getHeight()/2);
	}
	
	public static Popup popup = null;
	public static JPanel testPanel = null;
	public static JToolTip testToolTip = null;
	public static NewFDXOpticalPanel test = null;
	
//	public static void main(String args[]) {
//		
//		JFrame test = new JFrame();
//		test.setSize(400, 400);
//		NewFDXOpticalPanel.testToolTip = new JToolTip();
//		NewFDXOpticalPanel.testToolTip.setLayout(new BorderLayout());
//		NewFDXOpticalPanel.test = new NewFDXOpticalPanel(NewFDXOpticalPanel.testToolTip);
//		NewFDXOpticalPanel.testToolTip.add(NewFDXOpticalPanel.test, BorderLayout.CENTER);
//		NewFDXOpticalPanel.testToolTip.setPreferredSize(new Dimension(190, 190));
//		NewFDXOpticalPanel.testToolTip.setBorder(BorderFactory.createTitledBorder("test"));
//		NewFDXOpticalPanel.testPanel = new JPanel();
//		NewFDXOpticalPanel.testPanel.addMouseMotionListener(new MouseMotionAdapter() {
//		    public void mouseMoved(MouseEvent e) {
//				if (NewFDXOpticalPanel.popup != null) {
//					NewFDXOpticalPanel.popup.hide();
//					NewFDXOpticalPanel.popup = null;
//				}
//				NewFDXOpticalPanel.popup = PopupFactory.getSharedInstance().getPopup(NewFDXOpticalPanel.testPanel, NewFDXOpticalPanel.testToolTip,  e.getX(), e.getY());
//				NewFDXOpticalPanel.popup.show();
//		    }
//		});
//		test.getContentPane().setLayout(new BorderLayout());
//		test.getContentPane().add(NewFDXOpticalPanel.testPanel, BorderLayout.CENTER);
//		test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		
//		ArrayList l = new ArrayList();
//		for (int i=0; i<10; i++)
//			l.add("tasfasdfsa_"+i);
//		NewFDXOpticalPanel.test.setPortNames(l);
//		Vector v = new Vector();
//		OpticalCrossConnectLink link = new OpticalCrossConnectLink("tasfasdfsa_4", "tasfasdfsa_6", null);
//		v.add(link);
//		link = new OpticalCrossConnectLink("tasfasdfsa_2", "tasfasdfsa_3", null);
//		v.add(link);
//		NewFDXOpticalPanel.test.update(null);
//		test.setVisible(true);
//	}

	
} // end of class NewFDXOpticalPanel

