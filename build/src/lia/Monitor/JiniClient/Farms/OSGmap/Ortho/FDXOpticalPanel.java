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

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;

/**
 * A new class to be used to represent one's optical switch ports on two rows, one for in and one for out
 */
public class FDXOpticalPanel extends JPanel implements MouseMotionListener, MouseListener {

	TreeSet opticalPorts;
	Hashtable  connectionsIn;
	Hashtable connectionsOut;
	
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
            Color.green,
            Color.yellow,
            Color.orange,
            Color.magenta,
            Color.cyan,
            Color.pink,
            Color.gray,
            DARK_RED,
            DARK_BLUE,
            DARK_GREEN,
            DARK_YELLOW,
            DARK_MAGENTA,
            DARK_CYAN,
            Color.darkGray,
            LIGHT_RED,
            LIGHT_BLUE,
            LIGHT_GREEN,
            LIGHT_YELLOW,
            LIGHT_MAGENTA,
            LIGHT_CYAN,
            Color.lightGray,
            VERY_DARK_RED,
            VERY_DARK_BLUE,
            VERY_DARK_GREEN,
            VERY_DARK_YELLOW,
            VERY_DARK_MAGENTA,
            VERY_DARK_CYAN,
            VERY_LIGHT_RED,
            VERY_LIGHT_BLUE,
            VERY_LIGHT_GREEN,
            VERY_LIGHT_YELLOW,
            VERY_LIGHT_MAGENTA,
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
	
	public FDXOpticalPanel() {
		
		opticalPorts = new TreeSet();
		connectionsIn = new Hashtable();
		connectionsOut = new Hashtable();
		portInVsLink = new Hashtable();
		portOutVsLink = new Hashtable();
		assignedColors = new Vector();
		addMouseMotionListener(this);
		addMouseListener(this);
	}
	
	public void setPortNames(ArrayList portNames) {
		
		if (portNames == null) return;
		
		System.out.println("Setting port names");
		
		synchronized (this) {
			updating = true;
			repaint();
		}

		TreeSet tmpOpticalPorts = new TreeSet();
		
		for (Iterator it = portNames.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			tmpOpticalPorts.add(portName);
		}
		
		synchronized (this) {
			opticalPorts = null;
			opticalPorts = tmpOpticalPorts;
			updating = false;
			repaint();
		}
	}
	
	public void update(Vector links) {
		
		System.out.println("Updating");
		
		synchronized(this) {
			updating = true;
			repaint();
		}

		Hashtable tmpConnectionsIn = new Hashtable();
		Hashtable tmpConnectionsOut = new Hashtable();
		
		Hashtable tmpPortIn = new Hashtable();
		Hashtable tmpPortOut = new Hashtable();
		
		Vector tmpPorts = new Vector();
		assignedColors.clear();
		
		for (int i=0; i<links.size(); i++) {
			OpticalCrossConnectLink link = (OpticalCrossConnectLink)links.get(i);
			Color c = getUniqueColor();
			tmpConnectionsIn.put(link.sPort, c);
			tmpConnectionsOut.put(link.dPort, c);
			tmpPortIn.put(link.sPort, link);
			tmpPortOut.put(link.dPort, link);
			if (!tmpPorts.contains(link.sPort))
				tmpPorts.add(link.sPort);
			if (!tmpPorts.contains(link.dPort))
				tmpPorts.add(link.dPort);
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
			for (int i=0; i<tmpPorts.size(); i++) {
				String portName = ((OSPort)tmpPorts.get(i)).name;
				if (!opticalPorts.contains(portName))
					opticalPorts.add(portName);
			}
			updating = false;
			repaint();
		}
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

		drawPortsIn(g2);
		drawPortsOut(g2);
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
		
		int d = (getWidth() - 6 * (opticalPorts.size()+1)) / opticalPorts.size();
		int between = (getWidth() - d*opticalPorts.size()) / (opticalPorts.size()+1); 
		int x = between;
		
		for (Iterator it = opticalPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Color c = unconnectedColor;
			if (connectionsIn.containsKey(portName)) 
				c = (Color)connectionsIn.get(portName);
			if (portInOver != null && portInOver.equals(portName))
				drawPortRect(g2, portName, x, 4+fm.getHeight(), d-10, h-8-fm.getHeight(), c, true);
			else
				drawPortRect(g2, portName, x, 4+fm.getHeight(), d-10, h-8-fm.getHeight(), c, false);
			portInForms.put(portName, new Rectangle(x, 4+fm.getHeight(), d, h-8-fm.getHeight()));
			x += d+between;
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
		
		int d = (getWidth() - 6 * (opticalPorts.size()+1)) / opticalPorts.size(); 
		int between = (getWidth() - d*opticalPorts.size()) / (opticalPorts.size()+1); 
		int x = between;
		
		for (Iterator it = opticalPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Color c = unconnectedColor;
			if (connectionsOut.containsKey(portName)) 
				c = (Color)connectionsOut.get(portName);
			if (portOutOver != null && portOutOver.equals(portName))
				drawPortRect(g2, portName, x, from+4+fm.getHeight(), d-10, h-8-fm.getHeight(), c, true);
			else
				drawPortRect(g2, portName, x, from+4+fm.getHeight(), d-10, h-8-fm.getHeight(), c, false);
			portOutForms.put(portName, new Rectangle(x, from+4+fm.getHeight(), d, h-8-fm.getHeight()));
			x += d+between;
		}
	}
	
	private void drawPortRect(Graphics2D g2, String portName, int x, int y, int w, int h,  Color color, boolean mouseOver) {
		
		g2.setColor(color);
		g2.fillRect(x+1, y+1, w+8, h-2);
		if (mouseOver) {
			g2.setColor(color.brighter());
			g2.drawLine(x, y, x+w+10, y);
			g2.drawLine(x, y+1, x+w+9, y+1);
			g2.drawLine(x, y+2, x+w+8, y+2);
			g2.drawLine(x, y, x, y+h);
			g2.drawLine(x+1, y, x+1, y+h-1);
			g2.drawLine(x+2, y, x+2, y+h-2);
			g2.setColor(color.darker());
			g2.drawLine(x+w+8, y+2, x+w+8, y+h);
			g2.drawLine(x+w+9, y+1, x+w+9, y+h);
			g2.drawLine(x+w+10, y, x+w+10, y+h);
			g2.drawLine(x+2, y+h-2, x+w+10, y+h-2);
			g2.drawLine(x+1, y+h-1, x+w+10, y+h-1);
			g2.drawLine(x, y+h, x+w+10, y+h);
		} else{
			g2.setColor(Color.lightGray);
			g2.drawRect(x, y, w+10, h);
			g2.drawRect(x+1, y+1, w+8, h-2);
			g2.drawRect(x+2, y+2, w+6, h-4);
		}
		
		if (mouseOver) {
			g2.setColor(Color.black);
			g2.setFont(portFont);
			int ww = g2.getFontMetrics().stringWidth(portName);
			g2.drawString(portName, x+(w+10)/2-ww/2, y + h/2);
		}
		
//		int w1 = g2.getFontMetrics(portFont.deriveFont(10.0f)).stringWidth(portName);
//		int w2 = g2.getFontMetrics(portFont.deriveFont(14.0f)).stringWidth(portName);
//		int ww = 0;
//		if (w1 > (w+10)) {
//			g2.setFont(portFont.deriveFont(10.0f));
//			ww = w1;
//		} else if (w2 < (w+10)) {
//			g2.setFont(portFont.deriveFont(14.0f));
//			ww = w2;
//		} else {
//			double f = 10.0 + 6 * (w+10-w1) / (w2 - w1);
//			g2.setFont(portFont.deriveFont((float)f));
//			ww = g2.getFontMetrics().stringWidth(portName);
//		}
//		g2.drawString(portName, x+(w+10)/2-ww/2, y + (w+10)/2);
		
//		double fontScale =  w / 3;
//		Font f = portFont.deriveFont((float)fontScale);
//		
//		if (!mouseOver)
//			g2.setFont(f);
//		else
//			g2.setFont(f.deriveFont(1.2f * (float)fontScale));
//		Shape shape = RefineryUtilities.calculateRotatedStringBounds(portName, g2, x+w/2, y+7+w/2, TextAnchor.CENTER, TextAnchor.CENTER,
//				/*3 * Math.PI/2 + Math.PI/10*/0.0);
//		int xx = (int)(x+w/2+5 - shape.getBounds().getCenterX());
//		RefineryUtilities.drawRotatedString(portName, g2, x+w/2+5+xx, y+7+w/2, TextAnchor.CENTER, TextAnchor.CENTER,
//				/*3 * Math.PI/2 + Math.PI/10*/0.0);
//		g2.drawString(portName, x+5, y+7+w/2);
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
		// TODO Auto-generated method stub
		
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
	
//	public static void main(String args[]) {
//		
//		JFrame test = new JFrame();
//		FDXOpticalPanel p = new FDXOpticalPanel();
//		test.setSize(400, 400);
//		test.getContentPane().setLayout(new BorderLayout());
//		test.getContentPane().add(p, BorderLayout.CENTER);
//		test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		
//		ArrayList l = new ArrayList();
//		for (int i=0; i<10; i++)
//			l.add("tasfasdfsa_"+i);
//		p.setPortNames(l);
//		Vector v = new Vector();
//		OpticalCrossConnectLink link = new OpticalCrossConnectLink("tasfasdfsa_4", "tasfasdfsa_6", null);
//		v.add(link);
//		link = new OpticalCrossConnectLink("tasfasdfsa_2", "tasfasdfsa_3", null);
//		v.add(link);
//		p.update(v);
//		test.setVisible(true);
//	}

	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
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

} // end of class FDXOpticalPanel


