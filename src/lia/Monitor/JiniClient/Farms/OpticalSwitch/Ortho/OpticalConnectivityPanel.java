package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JPanel;

import lia.net.topology.Link;

import org.jfree.ui.RefineryUtilities;
import org.jfree.ui.TextAnchor;

public class OpticalConnectivityPanel extends JPanel implements MouseMotionListener {

	protected OrthogonalLayout layout = null;
	protected Dimension panelDimension = null;
	protected double textRotationAngle = Math.toRadians(10);
	protected Font axisFont = new Font("Arial", Font.BOLD, 10);
	protected AffineTransform transform = null;
	protected Color connectedColor = new Color(0, 0, 255, 160);
	protected Color selectedConnectedColor = new Color(0, 0, 255, 255);
	protected Color unconnectedColor = new Color(255, 0, 0, 160);
	protected Color portsColor = new Color(125, 125, 125, 125);
	protected Color lineColor = new Color(0, 0, 100, 50);
	
	protected Hashtable lines = null;
	protected Hashtable names = null;
	protected OrthogonalPath selectedPath = null;
	
	protected boolean updating = true;
	
	public OpticalConnectivityPanel() {
		
		lines = new Hashtable();
		names = new Hashtable();
		layout = new OrthogonalLayout(this);
		addMouseMotionListener(this);
	}
	
	public void update(int nrPorts, HashMap links) {
		synchronized (this) {
			updating = true;
			repaint();
		}
		layout.update(nrPorts, links);
		synchronized (this) {
			updating = false;
			repaint();
		}
	}
	
	public void update(int nrPorts, Link links[]) {
		synchronized (this) {
			updating = true;
			repaint();
		}
		layout.update(nrPorts, links);
		synchronized (this) {
			updating = false;
			repaint();
		}
	}
	
	public void update(int nrPorts, ArrayList links) {
		synchronized (this) {
			updating = true;
			repaint();
		}
		layout.update(nrPorts, links);
		synchronized (this) {
			updating = false;
			repaint();
		}
	}
	
	public OrthogonalLayout getOrthogonalLayout() {
		
		return layout;
	}
	
	protected void paintComponent(Graphics g) {
		
		synchronized (this) {
			if (updating) {
				drawUpdating((Graphics2D)g);
				return;
			}
		}
		if (!layout.isReady()) return;
		Graphics2D g2 = (Graphics2D)g;
		g.setFont(axisFont);
		panelDimension = getSize();
		int numberOfPorts = layout.getNumberOfPorts();
		Hashtable no2names = layout.getNo2Ports();
		int p = numberOfPorts >> 2; // nrConnections / 4
		double max = 0.0;		
		
		g2.setPaint(Color.white);
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setPaint(Color.black);
		
		double startx = 0.0, starty = 0.0, leftx = 0.0, lefty = 0.0;
		String[] up = new String[p];
		String[] down = new String[p];
		String[] left = new String[p];
		String[] right = new String[p];
		
		FontMetrics fm = g2.getFontMetrics(axisFont);
		for (int i=0; i<p; i++) {
			String portName = "";
			if (!no2names.containsKey(Integer.valueOf(i))) portName = ""+i;
			else portName = (String)no2names.get(Integer.valueOf(i));
			Rectangle2D portNameBounds = fm.getStringBounds(portName, g2);
			AffineTransform at = AffineTransform.getRotateInstance(textRotationAngle);
			Shape rotatedLabelBounds = at.createTransformedShape(portNameBounds);
			Rectangle2D portBounds = rotatedLabelBounds.getBounds2D();
			double h = portBounds.getHeight();
			if (h > max) max = h;
			up[i] = portName;
		}
		starty = max + 4.0;
		max = 0.0;
		for (int i=0; i<p; i++) {
			String portName = "";
			if (!no2names.containsKey(Integer.valueOf(p+i))) portName = ""+(p+i);
			else portName = (String)no2names.get(Integer.valueOf(p+i));
			Rectangle2D portNameBounds = fm.getStringBounds(portName, g2);
			double w = portNameBounds.getWidth();
			if (w > max) max = w;
			right[i] = portName;
		}
		leftx = panelDimension.getWidth() - max - 4.0;
		max = 0.0;
		for (int i=0; i<p; i++) {
			String portName = "";
			if (!no2names.containsKey(Integer.valueOf(2*p+i))) portName = ""+(2*p+i);
			else portName = (String)no2names.get(Integer.valueOf(2*p+i));
			Rectangle2D portNameBounds = fm.getStringBounds(portName, g2);
			AffineTransform at = AffineTransform.getRotateInstance(textRotationAngle);
			Shape rotatedLabelBounds = at.createTransformedShape(portNameBounds);
			Rectangle2D portBounds = rotatedLabelBounds.getBounds2D();
			double h = portBounds.getHeight();
			if (h > max) max = h;
			down[i] = portName;
		}
		lefty = panelDimension.getHeight() - max - 4.0;
		max = 0.0;
		for (int i=0; i<p; i++) {
			String portName = "";
			if (!no2names.containsKey(Integer.valueOf(3*p+i))) portName = ""+(3*p+i);
			else portName = (String)no2names.get(Integer.valueOf(3*p+i));
			Rectangle2D portNameBounds = fm.getStringBounds(portName, g2);
			double w = portNameBounds.getWidth();
			if (w > max) max = w;
			left[i] = portName;
		}
		startx = max + 4.0;
		double newWidth = leftx - startx;
		double newHeight = lefty - starty;
		transform = AffineTransform.getTranslateInstance(startx, starty);
		transform.scale(newWidth / layout.getSize(), newHeight / layout.getSize());
		
		lines.clear();
		names.clear();
		
		drawUpperLabels(g2, up, startx, leftx, fm);
		drawRightLabels(g2, right, starty, lefty, fm);
		drawDownLabels(g2, down, startx, leftx, fm);
		drawLeftLabels(g2, left, starty, lefty, fm);
		
		drawSwitch(g2);
		drawPaths(g2);
	}
	
	protected boolean portSelected(Point2D.Double p) {
		
		if (selectedPath == null) return false;
		return selectedPath.getFirstPoint().equals(p) || selectedPath.getLastPoint().equals(p);
	}
	
	protected void drawUpperLabels(Graphics2D g2, String[] labels, double start, double end, FontMetrics fm) {
		
		Hashtable points = layout.getPorts();
		Hashtable no2names = layout.getNo2Ports();
		Rectangle2D lastBounds = null;
		for (int i=0; i<labels.length; i++) {
			Point2D.Double p = (Point2D.Double)points.get(Integer.valueOf(i));
			Point2D.Double portPoint = (Point2D.Double)transform.transform(p, null);
			g2.setPaint(portsColor);
			g2.fillRoundRect((int)portPoint.getX()-1, (int)portPoint.getY()-4, 3, 8, 3, 3);
			double downY = portPoint.getY() - 2.0;
			double downX = portPoint.getX();
			Shape shape = RefineryUtilities.calculateRotatedStringBounds(labels[i], g2, (float)downX, (float)downY, TextAnchor.BOTTOM_CENTER, TextAnchor.BOTTOM_CENTER,
						textRotationAngle);
			names.put(p, shape);
			Rectangle2D portBounds = shape.getBounds2D();
			if (no2names.containsKey(Integer.valueOf(i))) g2.setPaint(connectedColor);
			else g2.setPaint(unconnectedColor);
			if (portSelected(p)) g2.setPaint(selectedConnectedColor);
			if (lastBounds == null) {
				RefineryUtilities.drawRotatedString(labels[i], g2,
						(float) downX, (float) downY,
						TextAnchor.BOTTOM_CENTER, TextAnchor.BOTTOM_CENTER,
						textRotationAngle);
				lastBounds = portBounds;
			} else
				if (!lastBounds.intersects(portBounds)) {
					RefineryUtilities.drawRotatedString(labels[i], g2,
							(float) downX, (float) downY,
							TextAnchor.BOTTOM_CENTER, TextAnchor.BOTTOM_CENTER,
							textRotationAngle);
					lastBounds = portBounds;
				}
		}
	}
	
	protected void drawRightLabels(Graphics2D g2, String[] labels, double start, double end, FontMetrics fm) {
		
		Hashtable points = layout.getPorts();
		Hashtable no2names = layout.getNo2Ports();
		Rectangle2D lastBounds = null;
		for (int i=0; i<labels.length; i++) {
			Point2D.Double p = (Point2D.Double)points.get(Integer.valueOf(labels.length+i));
			Point2D.Double portPoint = (Point2D.Double)transform.transform(p, null);
			g2.setPaint(portsColor);
			g2.fillRoundRect((int)portPoint.getX()-4, (int)portPoint.getY()-1, 8, 3, 3, 3);
			double x = portPoint.getX() + 2.0;
			double y = portPoint.getY();
			Shape shape = RefineryUtilities.calculateRotatedStringBounds(labels[i], g2, (float)x, (float)y, TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT,
					0.0);
			names.put(p, shape);
			Rectangle2D portBounds = shape.getBounds2D(); 
			if (no2names.containsKey(Integer.valueOf(labels.length+i))) g2.setPaint(connectedColor);
			else g2.setPaint(unconnectedColor);
			if (portSelected(p)) g2.setPaint(selectedConnectedColor);
			if (lastBounds == null) {
				RefineryUtilities.drawRotatedString(labels[i], g2,
						(float) x, (float) y,
						TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT,
						0.0);
				lastBounds = portBounds;
			} else
				if (!lastBounds.intersects(portBounds)) {
					RefineryUtilities.drawRotatedString(labels[i], g2,
							(float) x, (float) y,
							TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT,
							0.0);
					lastBounds = portBounds;
				}		
		}
	}
	
	protected void drawDownLabels(Graphics2D g2, String[] labels, double start, double end, FontMetrics fm) {
		
		Hashtable points = layout.getPorts();
		Hashtable no2names = layout.getNo2Ports();
		Rectangle2D lastBounds = null;
		for (int i=0; i<labels.length; i++) {
			Point2D.Double p = (Point2D.Double)points.get(Integer.valueOf(2*labels.length+i));
			Point2D.Double portPoint = (Point2D.Double)transform.transform(p, null);
			g2.setPaint(portsColor);
			g2.fillRoundRect((int)portPoint.getX()-1, (int)portPoint.getY()-4, 3, 8, 3, 3);
			double downY = portPoint.getY() + 3.0;
			double downX = portPoint.getX();
			Shape shape = RefineryUtilities.calculateRotatedStringBounds(labels[i], g2, (float)downX, (float)downY, TextAnchor.TOP_CENTER, TextAnchor.TOP_CENTER,
					textRotationAngle);
			names.put(p, shape);
			Rectangle2D portBounds = shape.getBounds2D(); 
			if (no2names.containsKey(Integer.valueOf(2*labels.length+i))) g2.setPaint(connectedColor);
			else g2.setPaint(unconnectedColor);
			if (portSelected(p)) g2.setPaint(selectedConnectedColor);
			if (lastBounds == null) {
				RefineryUtilities.drawRotatedString(labels[i], g2,
						(float) downX, (float) downY,
						TextAnchor.TOP_CENTER, TextAnchor.TOP_CENTER,
						textRotationAngle);
				lastBounds = portBounds;
			} else
				if (!lastBounds.intersects(portBounds)) {
					RefineryUtilities.drawRotatedString(labels[i], g2,
							(float) downX, (float) downY,
							TextAnchor.TOP_CENTER, TextAnchor.TOP_CENTER,
							textRotationAngle);
					lastBounds = portBounds;
				}
		}
	}
	
	protected void drawLeftLabels(Graphics2D g2, String[] labels, double start, double end, FontMetrics fm) {
		
		Hashtable points = layout.getPorts();
		Hashtable no2names = layout.getNo2Ports();
		Rectangle2D lastBounds = null;
		for (int i=0; i<labels.length; i++) {
			Point2D.Double p = (Point2D.Double)points.get(Integer.valueOf(3*labels.length+i));
			Point2D.Double portPoint = (Point2D.Double)transform.transform(p, null);
			g2.setPaint(portsColor);
			g2.fillRoundRect((int)portPoint.getX()-4, (int)portPoint.getY()-1, 8, 3, 3, 3);
			double x = 2.0;
			double y = portPoint.getY();
			Shape shape = RefineryUtilities.calculateRotatedStringBounds(labels[i], g2, (float)x, (float)y, TextAnchor.CENTER_RIGHT, TextAnchor.CENTER_RIGHT,
					0.0);
			names.put(p, shape);
			Rectangle2D portBounds = shape.getBounds2D(); 
			if (no2names.containsKey(Integer.valueOf(3*labels.length+i))) g2.setPaint(connectedColor);
			else g2.setPaint(unconnectedColor);
			if (portSelected(p)) g2.setPaint(selectedConnectedColor);
			if (lastBounds == null) {
				RefineryUtilities.drawRotatedString(labels[i], g2,
						(float) x, (float) y,
						TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT,
						0.0);
				lastBounds = portBounds;
			} else
				if (!lastBounds.intersects(portBounds)) {
					RefineryUtilities.drawRotatedString(labels[i], g2,
							(float) x, (float) y,
							TextAnchor.CENTER_LEFT, TextAnchor.CENTER_LEFT,
							0.0);
					lastBounds = portBounds;
				}		
		}
	}
	
	protected void drawSwitch(Graphics2D g2) {
		
		double max = layout.getSize();
		Point2D.Double upperLeftCorner = (Point2D.Double)transform.transform(new Point2D.Double(0, 0), null);
		Point2D.Double downRightCorner = (Point2D.Double)transform.transform(new Point2D.Double(max, max), null);
		int w = (int)(downRightCorner.getX()-upperLeftCorner.getX());
		int h = (int)(downRightCorner.getY()-upperLeftCorner.getY());
		g2.setPaint(new Color(0, 0, 0, 50));
		Stroke stroke = g2.getStroke();
		g2.setStroke(new BasicStroke(3.5f));
		g2.drawRoundRect((int)upperLeftCorner.getX(), (int)upperLeftCorner.getY(), w, h, 4, 4);
		g2.setStroke(stroke);
	}
	
	protected synchronized void drawPaths(Graphics2D g2) {
		
		Vector v = layout.getOrthogonalPaths();
		for (int i=0; i<v.size(); i++) {
			OrthogonalPath p = (OrthogonalPath)v.get(i);
			if (names.containsKey(p.getFirstPoint())) {
				names.put(names.get(p.getFirstPoint()), p);
			}
			if (names.containsKey(p.getLastPoint())) {
				names.put(names.get(p.getLastPoint()), p);
			}
			if (selectedPath != null && p.equals(selectedPath)) g2.setPaint(selectedConnectedColor);
			else g2.setPaint(lineColor);
			Vector path = p.getPath();
			Point2D.Double last = null;
			for (Iterator it = path.iterator(); it.hasNext(); ) {
				Point2D.Double point1 = (Point2D.Double)it.next();
				Point2D.Double point = (Point2D.Double)transform.transform(point1, null);
				if (last != null) {
					Stroke stroke = g2.getStroke();
					g2.setStroke(new BasicStroke(1.5f));
					g2.drawLine((int)last.getX(), (int)last.getY(), (int)point.getX(), (int)point.getY());
					Line2D.Double line = new Line2D.Double(last, point);
					lines.put(line, p);
					g2.setStroke(stroke);
				}
				last = point;
			}
		}
	}
	
	protected Shape getTransformedShaped(Shape original) {
	
		return transform.createTransformedShape(original);
	}

	public void mouseDragged(MouseEvent e) {
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
		panelDimension = getSize();
		int x = (int)(panelDimension.getWidth()/2-rect.getWidth()/2);
		int y = (int)(panelDimension.getHeight()/2+rect.getHeight()/2);
		g2.drawString(str, x, y);
		g2.setFont(oldFont);
	}

	public synchronized void mouseMoved(MouseEvent e) {
				
		if (transform == null) return;
		boolean update = true;
		for (Enumeration en = lines.keys(); en.hasMoreElements(); ) {
			Line2D.Double line = (Line2D.Double)en.nextElement();
			Rectangle2D.Double rect = new Rectangle2D.Double(e.getPoint().getX()-3, e.getPoint().getY()-3, 6, 6);
			if (rect.intersectsLine(line)) {
				if (selectedPath != null && selectedPath.equals(lines.get(line))) update = false;
				selectedPath = (OrthogonalPath)lines.get(line);
				if (update)
					repaint();
				return;
			}
		}
		for (Enumeration en = names.keys(); en.hasMoreElements(); ) {
			Object obj = en.nextElement();
			if (obj instanceof Shape) {
				Shape shape = (Shape)obj;
				Rectangle2D.Double rect = new Rectangle2D.Double(e.getPoint().getX()-3, e.getPoint().getY()-3, 6, 6);
				if (shape.intersects(rect)) {
					if (selectedPath != null && selectedPath.equals(names.get(shape))) update = false;
					selectedPath = (OrthogonalPath)names.get(shape);
					if (update)
						repaint();
					return;
				}
			}
		}
		if (selectedPath == null) update = false;
		selectedPath = null;
		if (update) repaint();
	}
	
} // end of class OpticalConnectivityPanel

