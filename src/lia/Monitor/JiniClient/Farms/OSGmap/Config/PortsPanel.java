package lia.Monitor.JiniClient.Farms.OSGmap.Config;

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
import java.util.TreeMap;

import javax.swing.JPanel;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;

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
	
	protected TreeMap inPorts;
	protected TreeMap inPortsObj;
	protected TreeMap inPortsPower;
	protected Hashtable inConnectedPorts;
	protected Hashtable inPortsRects1;
	protected Hashtable inPortsRects2;
	protected TreeMap outPorts;
	protected TreeMap outPortsObj;
	protected TreeMap outPortsPower;
	protected Hashtable outConnectedPorts;
	protected Hashtable outPortsRects1;
	protected Hashtable outPortsRects2;
	
	public String portInMouseOver = null;
	public String portOutMouseOver = null;
	
	public String portInClicked = null;
	public String portOutClicked = null;
	
	private ConfigFrame parent;
	
	public PortsPanel(ConfigFrame parent) {
		
		super();
		this.parent = parent;
		inPorts = new TreeMap();
		inConnectedPorts = new Hashtable();
		outPorts = new TreeMap();
		outConnectedPorts = new Hashtable();
		inPortsRects1 = new Hashtable();
		outPortsRects1 = new Hashtable();
		inPortsRects2 = new Hashtable();
		outPortsRects2 = new Hashtable();
		inPortsObj = new TreeMap();
		outPortsObj = new TreeMap();
		inPortsPower = new TreeMap();
		outPortsPower = new TreeMap();
		addMouseListener(this);
		addMouseMotionListener(this);
	}
	
	public void setInputPortPower(String portName, double power) {
		if (portName == null) return;
		synchronized (getTreeLock()) {
			inPortsPower.put(portName, Double.valueOf(power));
		}
	}
	
	public void setOutputPortPower(String portName, double power) {
		if (portName == null) return;
		synchronized (getTreeLock()) {
			outPortsPower.put(portName, Double.valueOf(power));
		}
	}
	
	public void setInputPortObj(String portName, OSPort port) {
		if (portName == null || port == null) return;
		synchronized (getTreeLock()) {
			inPortsObj.put(portName, port);
		}
	}
	
	public void setInputPortObj(String portName, OSwPort port) {
		if (portName == null || port == null) return;
		synchronized (getTreeLock()) {
			inPortsObj.put(portName, port);
		}
	}
	
	public void setOutputPortObj(String portName, OSPort port) {
		if (portName == null || port == null) return;
		synchronized (getTreeLock()) {
			outPortsObj.put(portName, port);
		}
	}

	public void setOutputPortObj(String portName, OSwPort port) {
		if (portName == null || port == null) return;
		synchronized (getTreeLock()) {
			outPortsObj.put(portName, port);
		}
	}

	/**
	 * @param portName The input port name
	 * @param state The status
	 * @param connectedPort The corresponding out port name (null if none connected)
	 */
	public void addPort(String portName, int state, String connectedPort) {
		
		if (portName == null) return;
		
		synchronized (getTreeLock()) {
			inPorts.put(portName, Integer.valueOf(state));
			if (connectedPort != null && connectedPort.length() != 0) {
				inConnectedPorts.put(portName, connectedPort);
				outPorts.put(connectedPort, Integer.valueOf(state));
				outConnectedPorts.put(connectedPort, portName);
			} else {
				outPorts.put(portName, Integer.valueOf(state));
			}
		}
	}
	
	public void delPort(String portName) {
		
		if (portName == null) return;
		synchronized (getTreeLock()) {
			inPorts.remove(portName);
			outPorts.remove(portName);
			inPortsObj.remove(portName);
			outPortsObj.remove(portName);
			inPortsPower.remove(portName);
			outPortsPower.remove(portName);
			String remote = (String)inConnectedPorts.remove(portName);
			if (remote != null)
				outConnectedPorts.remove(remote);
			if (portInClicked != null && portInClicked.equals(portName)) {
				portInClicked = null;
				parent.setCurrentInputPort(null);
				parent.setConnectButtonState(false);
				parent.setDisconnectButtonState(false);
				if (parent.currentClickedPort.equals(portName)) parent.setCurrentClickedPort(portOutClicked);
			}
			if (portOutClicked != null && portOutClicked.equals(portName)) {
				portOutClicked = null;
				parent.setCurrentOutputPort(null);
				parent.setConnectButtonState(false);
				parent.setDisconnectButtonState(false);
				if (parent.currentClickedPort.equals(portName)) parent.setCurrentClickedPort(portInClicked);
			}
			parent.setCurrentConnPorts(portInClicked, portOutClicked);
			repaint();
		}
	}
	
	/**
	 * 
	 * @param portName The name of the input port
	 * @param state The new state
	 */
	public void setPortState(String portName, int state, String connectedPort) {

		synchronized (getTreeLock()) {
			if (portName == null) return;
			
			if (portInClicked != null && portInClicked.equals(portName)) {
				if (inConnectedPorts.containsKey(portName) && connectedPort == null) { // was connected... now is not
					parent.setCurrentConnPorts(null, null);
					parent.setConnectButtonState(portOutClicked != null);
					parent.setDisconnectButtonState(false);
				} else
				if (!inConnectedPorts.containsKey(portName) && connectedPort != null) { // was not connected, now it is
					if (portOutClicked != null && portOutClicked.equals(connectedPort)) {
						parent.setCurrentConnPorts(portName, connectedPort);
						parent.setConnectButtonState(false);
						parent.setDisconnectButtonState(true);
					} else {
						parent.setCurrentConnPorts(null, null);
						parent.setConnectButtonState(portOutClicked != null);
						parent.setDisconnectButtonState(false);
					}
				}
			}
			
			if (inConnectedPorts.containsKey(portName)) {
				String p = (String)inConnectedPorts.remove(portName);
				outConnectedPorts.remove(p);
				inConnectedPorts.remove(portName);
				outPorts.put(p, Integer.valueOf(unconnected));
			}

//			portInMouseOver = portOutMouseOver = portInClicked = portOutClicked = null;

			inPorts.put(portName, Integer.valueOf(state));
			if (connectedPort != null && connectedPort.length() != 0) {
				inConnectedPorts.put(portName, connectedPort);
				outPorts.put(connectedPort, Integer.valueOf(state));
				outConnectedPorts.put(connectedPort, portName);
			} else {
				outPorts.put(portName, Integer.valueOf(state));
			}
		}
	}

	private Color getInColor(String portName) {
		
		if (!inPorts.containsKey(portName)) return faultColor;
		int state = ((Integer)inPorts.get(portName)).intValue();
		switch (state) {
		case inactive: return inactiveColor;
		case unconnected: {
			if (!inPortsPower.containsKey(portName) || !inPortsObj.containsKey(portName)) {
				return unconnectedInNoLight;
			}
			final Object o = inPortsObj.get(portName);
			if (o instanceof OSPort) { // old one
				OSPort p = (OSPort)o;
				double power = ((Double)inPortsPower.get(portName)).doubleValue();
				if (power < p.minPower.doubleValue() || power > p.maxPower.doubleValue()) return unconnectedInNoLight;
			} else if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				if (p.powerState != OSwPort.LIGHTOK) return unconnectedInNoLight;
			}
			return unconnectedLight;
		}
		case connected: {
			if (!inPortsPower.containsKey(portName) || !inPortsObj.containsKey(portName)) 
				return connectedNoLightColor;
			final Object o = inPortsObj.get(portName);
			if (o instanceof OSPort) {
				double power = ((Double)inPortsPower.get(portName)).doubleValue();
				OSPort p = (OSPort)o;
				if (power < p.minPower.doubleValue() || power > p.maxPower.doubleValue()) return connectedNoLightColor;
			} else if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				if (p.powerState != OSwPort.LIGHTOK)return connectedNoLightColor;
			}
			return connectedColor;
		}
		case fault: return faultColor;
		}
		return faultColor;
	}

	private Color getOutColor(String portName) {
		
		if (!outPorts.containsKey(portName)) return faultColor;
		int state = ((Integer)outPorts.get(portName)).intValue();
		switch (state) {
		case inactive: return inactiveColor;
		case unconnected: {
			if (!outPortsPower.containsKey(portName) || !outPortsObj.containsKey(portName)) return unconnectedOutNoLight;
			double power = ((Double)outPortsPower.get(portName)).doubleValue();
			final Object o = outPortsObj.get(portName);
			if (o == null) return unconnectedLight;
			if (o instanceof OSPort) {
				OSPort p = (OSPort)o;
				if (power < p.minPower.doubleValue() || power > p.maxPower.doubleValue()) return unconnectedOutNoLight;
			}
			if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				if (p.powerState != OSwPort.LIGHTOK)
					return unconnectedOutNoLight;
			}
			return unconnectedLight;
		}
		case connected: {
			if (!outPortsPower.containsKey(portName) || !outPortsObj.containsKey(portName)) return connectedNoLightColor;
			double power = ((Double)outPortsPower.get(portName)).doubleValue();
			final Object o = outPortsPower.get(portName);
			if (o == null) return connectedColor;
			if (o instanceof OSPort) {
				OSPort p = (OSPort)o;
				if (power < p.minPower.doubleValue() || power > p.maxPower.doubleValue()) return connectedNoLightColor;
			}
			if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				if (p.powerState != OSwPort.LIGHTOK)
					return connectedNoLightColor;
			}
			return connectedColor;
		}
		case fault: return faultColor;
		}
		return faultColor;
	}

	private void drawPortRect(Graphics2D g2, String portName, int x, int y, int w, int h, boolean in) {
		
		Color rectColor = null;
		if (in) {
			rectColor = getInColor(portName);
			inPortsRects1.put(portName, new Rectangle(x, y, w+10, h+10));
		} else {
			rectColor = getOutColor(portName);
			outPortsRects1.put(portName, new Rectangle(x, y, w+10, h+10));
		}
		g2.setColor(rectColor);
		g2.fillRect(x+1, y+1, w+8, h+8);
		if (in && portInMouseOver != null && portInMouseOver.equals(portName)) {
			g2.setColor(upperSelectionColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerSelectionColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (!in && portOutMouseOver != null && portOutMouseOver.equals(portName)) {
			g2.setColor(upperSelectionColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerSelectionColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (in && portInClicked != null && portInClicked.equals(portName)) {
			g2.setColor(upperClickedColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerClickedColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (!in && portOutClicked != null && portOutClicked.equals(portName)) {
			g2.setColor(upperClickedColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerClickedColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else{
			g2.setColor(Color.lightGray);
			g2.drawRect(x, y, w+10, h+10);
			g2.drawRect(x+1, y+1, w+8, h+8);
			g2.drawRect(x+2, y+2, w+6, h+6);
		}
		g2.setFont(portFont);
		if (rectColor.equals(Color.white))
			g2.setColor(Color.black);
		else
			g2.setColor(Color.white);
		g2.drawString(portName, x+5, y+7+h/2);
	}

	private void drawPortRect(Graphics2D g2, String connectedPort, String portName, int x, int y, int w, int h, boolean in) {
		
		Color rectColor = null;
		if (in) {
			rectColor = getInColor(portName);
			inPortsRects2.put(portName, new Rectangle(x, y, w+10, h+10));
		} else {
			rectColor = getOutColor(portName);
			outPortsRects2.put(portName, new Rectangle(x, y, w+10, h+10));
		}
		g2.setColor(rectColor);
		g2.fillRect(x+1, y+1, w+8, h+8);
		if (in && portInMouseOver != null && portInMouseOver.equals(portName)) {
			g2.setColor(upperSelectionColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerSelectionColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (!in && portOutMouseOver != null && portOutMouseOver.equals(portName)) {
			g2.setColor(upperSelectionColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerSelectionColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (in && portInClicked != null && portInClicked.equals(portName)) {
			g2.setColor(upperClickedColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerClickedColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else if (!in && portOutClicked != null && portOutClicked.equals(portName)) {
			g2.setColor(upperClickedColor);
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h+10);
			g2.drawLine(x+1, y, x+1, y+h+9);
			g2.drawLine(x+2, y, x+2, y+h+8);
			g2.setColor(lowerClickedColor);
			g2.drawLine(x+w+8, y+2, x+w+8, y+h+10);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h+10);
			g2.drawLine(x+w+10, y, x+w+10, y+h+10);
			g2.drawLine(x+2, y+h+8, x+w+10, y+h+8);
			g2.drawLine(x+1, y+h+9, x+w+10, y+h+9);
			g2.drawLine(x, y+h+10, x+w+10, y+h+10);
		} else{
			g2.setColor(Color.lightGray);
			g2.drawRect(x, y, w+10, h+10);
			g2.drawRect(x+1, y+1, w+8, h+8);
			g2.drawRect(x+2, y+2, w+6, h+6);
		}
		if (connectedPort != null) {
			g2.setFont(portFont);
			if (rectColor.equals(Color.white))
				g2.setColor(Color.black);
			else
				g2.setColor(Color.white);
			g2.drawString(connectedPort, x+5, y+7+h/2);
		}
	}
	
	public void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g;
		
		FontMetrics fm = g2.getFontMetrics(portFont);
		// get all the port names...
		String[] portNames = new String[inPorts.size()];
		int i=0;
		for (Iterator it = inPorts.keySet().iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			portNames[i++] = portName;
		}
		// get the max string
		int maxLen = 0; 
		for (i=0; i<portNames.length; i++) {
			int w = fm.stringWidth(portNames[i]);
			if (w > maxLen) maxLen = w;
		}
		int maxHei = fm.getHeight()+4;
		// total width
		int totalW = 20 + portNames.length * (maxLen + 10) + (portNames.length-1)*2 + 20;
		int fromLeft = (getWidth() - totalW) / 2;
		// draw the in title
		g2.setFont(titleFont);
		fm = g2.getFontMetrics();
		int titleW = fm.stringWidth("Input");
		g2.drawString("Input", fromLeft + (totalW-titleW)/2, 20);
		int startY = 20 + fm.getHeight();
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		int startRect = fromLeft + 20;
		// draw the ports...
		for (i=0; i<portNames.length; i++) {
			drawPortRect(g2, portNames[i], startRect, startY, maxLen, maxHei, true);
			startRect += maxLen + 12;
		}
		startY += maxHei + 12;
		startRect = fromLeft + 20;
		for (i=0; i<portNames.length; i++) {
			String connectedPort = null;
			if (inConnectedPorts.containsKey(portNames[i]))
				connectedPort = (String)inConnectedPorts.get(portNames[i]);
			drawPortRect(g2, connectedPort, portNames[i], startRect, startY, maxLen, maxHei, true);
			startRect += maxLen + 12;
		}
		startY += maxHei + 12+20;
		g2.setFont(titleFont);
		g2.setColor(Color.black);
		fm = g2.getFontMetrics();
		titleW = fm.stringWidth("Output");
		g2.drawString("Output", fromLeft + (totalW-titleW)/2, startY);
		startY +=  fm.getHeight();
		g2.setFont(portFont);
		fm = g2.getFontMetrics();
		startRect = fromLeft + 20;
		// draw the ports...
		for (i=0; i<portNames.length; i++) {
			drawPortRect(g2, portNames[i], startRect, startY, maxLen, maxHei, false);
			startRect += maxLen + 12;
		}
		startY += maxHei + 12;
		startRect = fromLeft + 20;
		for (i=0; i<portNames.length; i++) {
			String connectedPort = null;
			if (outConnectedPorts.containsKey(portNames[i]))
				connectedPort = (String)outConnectedPorts.get(portNames[i]);
			drawPortRect(g2, connectedPort, portNames[i], startRect, startY, maxLen, maxHei, false);
			startRect += maxLen + 12;
		}
		setPreferredSize(new Dimension(totalW, startY + maxLen + 20));
		revalidate();
	}

	public void mouseClicked(MouseEvent e) {
		
		Point p = e.getPoint();
		final int modifier = InputEvent.CTRL_DOWN_MASK; //  | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
		boolean mod = (e.getModifiersEx() & modifier) != 0;
		if (mod)
			parent.mouseClicked(e);
		if (e.isConsumed()) return; 
		for (Enumeration en = inPortsRects1.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)inPortsRects1.get(portName);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(portName)) return;
				portInClicked = portName;
				parent.setCurrentClickedPort(portName);
				int state = ((Integer)inPorts.get(portName)).intValue();
				if (state == connected) {
					portOutClicked = (String)inConnectedPorts.get(portName);
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portOutClicked != null && outConnectedPorts.containsKey(portOutClicked)) {
						String pp = (String)outConnectedPorts.get(portOutClicked);
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
		for (Enumeration en = inPortsRects2.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)inPortsRects2.get(portName);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(portName)) return;
				portInClicked = portName;
				parent.setCurrentClickedPort(portName);
				int state = ((Integer)inPorts.get(portName)).intValue();
				if (state == connected) {
					portOutClicked = (String)inConnectedPorts.get(portName);
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portOutClicked != null && outConnectedPorts.containsKey(portOutClicked)) {
						String pp = (String)outConnectedPorts.get(portOutClicked);
						if (!pp.equals(portInClicked)) portOutClicked = null;
					}
					if (portOutClicked != null) {
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
		for (Enumeration en = outPortsRects1.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)outPortsRects1.get(portName);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(portName)) return;
				portOutClicked = portName;
				parent.setCurrentClickedPort(portName);
				int state = ((Integer)outPorts.get(portName)).intValue();
				if (state == connected) {
					portInClicked = (String)outConnectedPorts.get(portName);
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portInClicked != null && inConnectedPorts.containsKey(portInClicked)) {
						String pp = (String)inConnectedPorts.get(portInClicked);
						if (!pp.equals(portOutClicked)) portInClicked = null;
					}
					if (portInClicked != null) {
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
		for (Enumeration en = outPortsRects2.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)outPortsRects2.get(portName);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(portName)) return;
				portOutClicked = portName;
				parent.setCurrentClickedPort(portName);
				int state = ((Integer)outPorts.get(portName)).intValue();
				if (state == connected) {
					portInClicked = (String)outConnectedPorts.get(portName);
					parent.setConnectButtonState(false);
					parent.setDisconnectButtonState(true);
				} else {
					if (portInClicked != null && inConnectedPorts.containsKey(portInClicked)) {
						String pp = (String)inConnectedPorts.get(portInClicked);
						if (!pp.equals(portOutClicked)) portInClicked = null;
					}
					if (portInClicked != null) {
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

	public void mouseMoved(MouseEvent e) {
		
		Point p = e.getPoint();
		for (Enumeration en = inPortsRects1.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)inPortsRects1.get(portName);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(portName)) return;
				if (portInMouseOver != null && portInMouseOver.equals(portName)) return;
				portInMouseOver = portName;
				int state = ((Integer)inPorts.get(portName)).intValue();
				if (state == connected) {
					portOutMouseOver = (String)inConnectedPorts.get(portName);
				} else
					portOutMouseOver = null;
				repaint();
				showPortLabels();
				return;
			}
		}
		for (Enumeration en = inPortsRects2.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)inPortsRects2.get(portName);
			if (rect.contains(p)) {
				if (portInClicked != null && portInClicked.equals(portName)) return;
				if (portInMouseOver != null && portInMouseOver.equals(portName)) return;
				portInMouseOver = portName;
				int state = ((Integer)inPorts.get(portName)).intValue();
				if (state == connected) {
					portOutMouseOver = (String)inConnectedPorts.get(portName);
				} else
					portOutMouseOver = null;
				repaint();
				showPortLabels();
				return;
			}
		}
		for (Enumeration en = outPortsRects1.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)outPortsRects1.get(portName);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(portName)) return;
				if (portOutMouseOver != null && portOutMouseOver.equals(portName)) return;
				portOutMouseOver = portName;
				int state = ((Integer)outPorts.get(portName)).intValue();
				if (state == connected) {
					portInMouseOver = (String)outConnectedPorts.get(portName);
				} else
					portInMouseOver = null;
				repaint();
				showPortLabels();
				return;
			}
		}
		for (Enumeration en = outPortsRects2.keys(); en.hasMoreElements(); ) {
			String portName = (String)en.nextElement();
			Rectangle rect = (Rectangle)outPortsRects2.get(portName);
			if (rect.contains(p)) {
				if (portOutClicked != null && portOutClicked.equals(portName)) return;
				if (portOutMouseOver != null && portOutMouseOver.equals(portName)) return;
				portOutMouseOver = portName;
				int state = ((Integer)outPorts.get(portName)).intValue();
				if (state == connected) {
					portInMouseOver = (String)outConnectedPorts.get(portName);
				} else
					portInMouseOver = null;
				repaint();
				showPortLabels();
				return;
			}
		}
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

	/** display the port labels on the config frame */
	private void showPortLabels(){
		String in = portInClicked;
		if(portInMouseOver != null)
			in = portInMouseOver;
		String out = portOutClicked;
		if(portOutMouseOver != null)
			out = portOutMouseOver;
		//System.out.println("inMO = "+in+" outMO = "+out);
		parent.setDeviceParams(null, getINPortLabel(in), getOUTPortLabel(out));
	}
	
	/** return the label for a IN port, if available */
	public String getINPortLabel(String portName){
		if (portName == null) return "";
		synchronized (getTreeLock()) {
			final Object o = inPortsObj.get(portName);
			if (o == null) return "";
			if (o instanceof OSPort) {
				OSPort p = (OSPort)o;
				return p.label != null ? p.label : "";
			} else if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				return p.label != null ? p.label : "";
			}
			return "";
		}
	}

	/** return the label for a OUT port, if available */
	public String getOUTPortLabel(String portName){
		if (portName == null) return "";
		synchronized (getTreeLock()) {
			final Object o = outPortsObj.get(portName);
			if (o == null) return "";
			if (o instanceof OSPort) {
				OSPort p = (OSPort)o; 
				return p.label != null ? p.label : "";
			} else if (o instanceof OSwPort) {
				OSwPort p = (OSwPort)o;
				return p.label != null ? p.label : "";
			}
			return "";
		}
	}
}
