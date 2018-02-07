package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.JPanel;

import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;

import org.jfree.ui.RefineryUtilities;
import org.jfree.ui.TextAnchor;

public class OpticalPanel extends JPanel implements MouseMotionListener {

	private Hashtable portPozitions = null;
	private Vector links = null;
	private int size = 0;
	private Hashtable port2link = null;
	private Hashtable colors = null;
	
	private Hashtable shapes = null;
	
	private OpticalCrossConnectLink currentLink = null;
	
	protected Color unconnectedColor = new Color(255, 0, 0, 160);
	protected BasicStroke stroke = new BasicStroke(2.0f);
	
	private Font font = new Font("Arial", Font.BOLD, 10);
	
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
	
	private static Paint colorSupply[] = new Paint[] {
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
	
	int colorIndex = 0;
	boolean updating = true;
	
	public OpticalPanel() {
		
		super();
		portPozitions = new Hashtable();
		port2link = new Hashtable();
		colors = new Hashtable();
		shapes = new Hashtable();
		addMouseMotionListener(this);
	}
	
	public void update(int nrPorts, Vector links) {
		
		synchronized (this) {
			updating = true;
			repaint();
		}
		if (links == null || nrPorts < 0) return;
		TreeMap portNames = new TreeMap();
		TreeMap portSet = new TreeMap();
		Hashtable portPozitions = new Hashtable();
		Hashtable port2link = new Hashtable();
		int size = (int)Math.sqrt(nrPorts);
		int x = 0;
		int y = 0;
		int nr = 0;
		for (int i=0; i<links.size(); i++) {
			OpticalCrossConnectLink link = (OpticalCrossConnectLink)links.get(i);
			if (link == null || link.sPort == null || link.dPort == null) continue;
			if (!portSet.containsKey(link.dPort))
				portSet.put(link.dPort, link);
			if (!portSet.containsKey(link.sPort))
				portSet.put(link.sPort, link);
		}
		for (Iterator it = portSet.keySet().iterator(); it.hasNext(); ) {
			String port = (String)it.next();
			OpticalCrossConnectLink  l = (OpticalCrossConnectLink)portSet.get(port);
			if (!portNames.containsKey(port)) {
				portNames.put(port, new Point(x, y));
				portPozitions.put(new Point(x, y), port);
				port2link.put(new Point(x, y), l);
				x++;
				if (x == size) { x = 0; y++; }
				nr++;
			}
		}
		for (int i=nr; i<nrPorts; i++) {
			portNames.put(""+i, new Point(x,y));
			portPozitions.put(new Point(x, y), ""+i);
			x++;
			if (x == size) { x = 0; y++; }
		}
		synchronized (this) {
			updating = false;
			this.links = links;
			this.portPozitions = portPozitions;
			this.port2link = port2link;
			this.size = size;
			repaint();
		}
	}
	
	protected synchronized void paintComponent(Graphics g) {

		if (updating) {
			drawUpdating((Graphics2D)g);
			return;
		}
		
		if (links == null) return;
		Graphics2D g2 = (Graphics2D)g;
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		int width = 0;
		int height = 0;
		int[] rowHeights = new int[size];
		int[] columnWidths = new int[size];
		for (int i=0; i<columnWidths.length; i++) columnWidths[i] = 0;
		for (int i=0; i<size; i++) {
			int w = 0;
			int h = 0;
			for (int j=0; j<size; j++) {
				String str = (String)portPozitions.get(new Point(j, i));
				Rectangle2D rect = fm.getStringBounds(str, g2);
				w += rect.getWidth()+10;
				if (w > columnWidths[j]) columnWidths[j] = w;
				else w = columnWidths[j];
				if (h < rect.getHeight()) h = (int)rect.getHeight();
			}
			if (width < w) width = w;
			rowHeights[i] = height+h+5;
			height += h+10+10;
		}
		int wDiff = getWidth() - width;
		int hDiff = getHeight() - height;
		if (wDiff < 0) wDiff = 0;
		if (hDiff < 0) hDiff = 0;
		wDiff = wDiff / (size + 1);
		hDiff = hDiff / (size + 1);
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(Color.BLACK);
		colors.clear();
		shapes.clear();
		colorIndex = 0;
		for (int i=0; i<size; i++) {
			int x = 5;
			for (int j=0; j<size; j++) {
				Point p = new Point(j, i);
				Color c = null;
				OpticalCrossConnectLink link = null;
				if (port2link.containsKey(p)) {
					link = (OpticalCrossConnectLink)port2link.get(p);
					if (colors.containsKey(link)) {
						c = (Color)colors.get(link);
					} else {
						c = generateUniqueColor();
						colors.put(link, c);
					}
					if (currentLink != null && currentLink.equals(link)) {
						c = Color.blue;
					}
				} else
					c = unconnectedColor;
				String port = (String)portPozitions.get(p);
				Rectangle2D rect = fm.getStringBounds(port, g2);
				g2.setColor(c);
				int x1 = (int)((x+columnWidths[j])/2-rect.getWidth()/2) + wDiff * (j+1);
				g2.drawString(port, x1, rowHeights[i]+hDiff * (i+1));
				Shape shape = RefineryUtilities.calculateRotatedStringBounds(port, g2, x1, rowHeights[i], TextAnchor.BOTTOM_CENTER, TextAnchor.BOTTOM_CENTER,
						0.0);
				if (link != null && shape != null)
					shapes.put(shape, link);
				int startPortX = (x + columnWidths[j])/2-5+wDiff * (j+1);
				int startPortY = rowHeights[i]+5+hDiff * (i+1);
				x = columnWidths[j];
				g2.setColor(c);
				g2.fillRect(startPortX, startPortY, 10, 10);
				if (link != null)
					shapes.put(new Rectangle(startPortX, startPortY, 10, 10), link);
				g2.setColor(Color.black);
				g2.setStroke(stroke);
				g2.drawRect(startPortX, startPortY, 10, 10);
			}
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
	
	protected Color generateUniqueColor() {
		
		if (colorIndex == colorSupply.length) colorIndex=0;
		return (Color)colorSupply[colorIndex++];
	}
	
	public void mouseDragged(MouseEvent e) {
	}

	public synchronized void mouseMoved(MouseEvent e) {
		
		for (Enumeration en = shapes.keys(); en.hasMoreElements(); ) {
			Shape shape = (Shape)en.nextElement();
			if (shape.contains(e.getPoint())) {
				OpticalCrossConnectLink link = (OpticalCrossConnectLink)shapes.get(shape);
				if (link != currentLink) {
					currentLink = link;
					repaint();
				}
				return;
			}
		}
		if (currentLink != null) {
			currentLink = null;
			repaint();
		}
	}

} // end of class OpticalPanel
