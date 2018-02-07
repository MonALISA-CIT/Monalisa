package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JComponent;

import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;

public class OrthogonalLayout {

	private TreeSet xaxis = null; // the current occupied points on the x axis
	private TreeSet yaxis = null; // the current occupied points on the y axis
	private Hashtable startOrthogonalPath = null; // the paths referenced by the starting points 
	private Hashtable endOrthogonalPath = null; // the same paths referenced by the ending points
	private Hashtable ports = null;
	private Hashtable namedPorts = null;
	private Hashtable names2no = null;
	private Hashtable no2names = null;
	private Vector orthogonalPaths = null;
	int currentPozition = 0;
	double max = 0;
	private boolean readyToDraw = false;
	private JComponent root = null;
	private Point2D.Double points[] = null;
	private TreeSet orderredPorts = null;
	
	public OrthogonalLayout(JComponent root) {
		
		this.root = root;
	}
	
	public synchronized boolean isReady() {
		
		return readyToDraw;
	}
	
	public synchronized int getNumberOfPorts() {
		
		if (points == null) return 0;
		return points.length;
	}
	
	public synchronized TreeSet getPortNames() {
		
		return orderredPorts;
	}
	
	public synchronized Hashtable getPorts2No() {
		
		return names2no;
	}
	
	public synchronized Hashtable getNo2Ports() {
		
		return no2names;
	}
	
	public synchronized Hashtable getPorts() {
		
		return ports;
	}
	
	public synchronized double getSize() {
		
		return max;
	}
	
	public synchronized void update(int nrPorts, OSwCrossConn[] links) {
		readyToDraw = true;
		currentPozition = 0;
		xaxis = new TreeSet();
		yaxis = new TreeSet();
		startOrthogonalPath = new Hashtable();
		endOrthogonalPath = new Hashtable();
		orthogonalPaths = new Vector();
		int p = nrPorts >> 2; // nrConnections / 4
		ports = new Hashtable();
		namedPorts = new Hashtable();
		names2no = new Hashtable();
		no2names = new Hashtable();
		// initialize the orthogonal paths
		points = new Point2D.Double[nrPorts];
		for (int i=0; i<p; i++) {
			points[i] = new Point2D.Double((i+1)*3-1, 0);
			ports.put(Integer.valueOf(i), points[i]);
		}
		for (int i=0; i<p; i++) {
			points[p+i] = new Point2D.Double(p*3+1, (i+1)*3-1);
			ports.put(Integer.valueOf(p+i), points[p+i]);
		}
		for (int i=0; i<p; i++) {
			points[2*p+i] = new Point2D.Double((p-i)*3-2, p*3+1);
			ports.put(Integer.valueOf(2*p+i), points[2*p+i]);
		}
		for (int i=0; i<p; i++) {
			points[3*p+i] = new Point2D.Double(0, (p-i)*3-2);
			ports.put(Integer.valueOf(3*p+i), points[3*p+i]);
		}
		yaxis.add(Double.valueOf(0));
		yaxis.add(Double.valueOf(p*3+1));
		xaxis.add(Double.valueOf(0));
		xaxis.add(Double.valueOf(p*3+1));
		max = p*3+1;
		initializeLinks(links);
		if (root != null) root.repaint();
	}
	
	public synchronized void update(int nrPorts, HashMap links) {
		
		readyToDraw = true;
		currentPozition = 0;
		xaxis = new TreeSet();
		yaxis = new TreeSet();
		startOrthogonalPath = new Hashtable();
		endOrthogonalPath = new Hashtable();
		orthogonalPaths = new Vector();
		int p = nrPorts >> 2; // nrConnections / 4
		ports = new Hashtable();
		namedPorts = new Hashtable();
		names2no = new Hashtable();
		no2names = new Hashtable();
		// initialize the orthogonal paths
		points = new Point2D.Double[nrPorts];
		for (int i=0; i<p; i++) {
			points[i] = new Point2D.Double((i+1)*3-1, 0);
			ports.put(Integer.valueOf(i), points[i]);
		}
		for (int i=0; i<p; i++) {
			points[p+i] = new Point2D.Double(p*3+1, (i+1)*3-1);
			ports.put(Integer.valueOf(p+i), points[p+i]);
		}
		for (int i=0; i<p; i++) {
			points[2*p+i] = new Point2D.Double((p-i)*3-2, p*3+1);
			ports.put(Integer.valueOf(2*p+i), points[2*p+i]);
		}
		for (int i=0; i<p; i++) {
			points[3*p+i] = new Point2D.Double(0, (p-i)*3-2);
			ports.put(Integer.valueOf(3*p+i), points[3*p+i]);
		}
		yaxis.add(Double.valueOf(0));
		yaxis.add(Double.valueOf(p*3+1));
		xaxis.add(Double.valueOf(0));
		xaxis.add(Double.valueOf(p*3+1));
		max = p*3+1;
		initializeLinks(links);
		if (root != null) root.repaint();
	}
	
	public synchronized void update(int nrPorts, ArrayList links) {
		
		readyToDraw = true;
		currentPozition = 0;
		xaxis = new TreeSet();
		yaxis = new TreeSet();
		startOrthogonalPath = new Hashtable();
		endOrthogonalPath = new Hashtable();
		orthogonalPaths = new Vector();
		int p = nrPorts >> 2; // nrConnections / 4
		ports = new Hashtable();
		namedPorts = new Hashtable();
		names2no = new Hashtable();
		no2names = new Hashtable();
		// initialize the orthogonal paths
		points = new Point2D.Double[nrPorts];
		for (int i=0; i<p; i++) {
			points[i] = new Point2D.Double((i+1)*3-1, 0);
			ports.put(Integer.valueOf(i), points[i]);
		}
		for (int i=0; i<p; i++) {
			points[p+i] = new Point2D.Double(p*3+1, (i+1)*3-1);
			ports.put(Integer.valueOf(p+i), points[p+i]);
		}
		for (int i=0; i<p; i++) {
			points[2*p+i] = new Point2D.Double((p-i)*3-2, p*3+1);
			ports.put(Integer.valueOf(2*p+i), points[2*p+i]);
		}
		for (int i=0; i<p; i++) {
			points[3*p+i] = new Point2D.Double(0, (p-i)*3-2);
			ports.put(Integer.valueOf(3*p+i), points[3*p+i]);
		}
		yaxis.add(Double.valueOf(0));
		yaxis.add(Double.valueOf(p*3+1));
		xaxis.add(Double.valueOf(0));
		xaxis.add(Double.valueOf(p*3+1));
		max = p*3+1;
		initializeLinks(links);
		if (root != null) root.repaint();
	}
	
	protected void initializeLinks(OSwCrossConn[] links) {
		
		if (links == null) return;
		orderredPorts = new TreeSet();
		for (int i=0; i<links.length; i++) {
			final OSwCrossConn link = links[i];
			if (link.sPort.type == OSwPort.MULTICAST_PORT || link.dPort.type == OSwPort.MULTICAST_PORT)
				continue;
			if (!orderredPorts.contains(link.sPort.name))
				orderredPorts.add(link.sPort.name);
			if (!orderredPorts.contains(link.dPort.name))
				orderredPorts.add(link.dPort.name);
		}
		for (Iterator it = orderredPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Integer in = Integer.valueOf(currentPozition);
			names2no.put(portName, in);
			no2names.put(in, portName);
			namedPorts.put(portName, ports.get(in));
			currentPozition++;
		}
		Vector v = new Vector();
		for (int i=0; i<links.length; i++) { 
			final OSwCrossConn link = links[i];
			if (link.sPort.type == OSwPort.MULTICAST_PORT || link.dPort.type == OSwPort.MULTICAST_PORT)
				continue;
			if (link.sPort.equals(link.dPort)) continue;
			if (v.contains(link.dPort) || v.contains(link.sPort)) continue;
			v.add(link.sPort);
			v.add(link.dPort);
			Point2D.Double s = (Point2D.Double)namedPorts.get(link.sPort.name);
			Point2D.Double e = (Point2D.Double)namedPorts.get(link.dPort.name);
			Point2D.Double startPoint = new Point2D.Double(s.getX(), s.getY());
			Point2D.Double endPoint = new Point2D.Double(e.getX(), e.getY());
			if (endPoint.getX() < startPoint.getX()) {
				reorder(startPoint, endPoint);
			}
			OrthogonalPath path = new OrthogonalPath(startPoint, endPoint);
			startOrthogonalPath.put(startPoint, path);
			endOrthogonalPath.put(endPoint, path);
			orthogonalPaths.add(path);
			draw(startPoint, endPoint, path, 16);
		}
	}
	
	protected void initializeLinks(HashMap links) {
		
		if (links == null) return;
		orderredPorts = new TreeSet();
		for (Iterator it = links.keySet().iterator(); it.hasNext(); ) {
			OpticalCrossConnectLink link = (OpticalCrossConnectLink)links.get(it.next());
			if (!orderredPorts.contains(link.sPort.name))
				orderredPorts.add(link.sPort.name);
			if (!orderredPorts.contains(link.dPort.name))
				orderredPorts.add(link.dPort.name);
		}
		for (Iterator it = orderredPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Integer in = Integer.valueOf(currentPozition);
			names2no.put(portName, in);
			no2names.put(in, portName);
			namedPorts.put(portName, ports.get(in));
			currentPozition++;
		}
		Vector v = new Vector();
		for (Iterator it = links.keySet().iterator(); it.hasNext(); ) {
			OpticalCrossConnectLink link = (OpticalCrossConnectLink)links.get(it.next());
			if (link.sPort.equals(link.dPort)) continue;
			if (v.contains(link.dPort) || v.contains(link.sPort)) continue;
			v.add(link.sPort);
			v.add(link.dPort);
			Point2D.Double s = (Point2D.Double)namedPorts.get(link.sPort.name);
			Point2D.Double e = (Point2D.Double)namedPorts.get(link.dPort.name);
			Point2D.Double startPoint = new Point2D.Double(s.getX(), s.getY());
			Point2D.Double endPoint = new Point2D.Double(e.getX(), e.getY());
			if (endPoint.getX() < startPoint.getX()) {
				reorder(startPoint, endPoint);
			}
			OrthogonalPath path = new OrthogonalPath(startPoint, endPoint);
			startOrthogonalPath.put(startPoint, path);
			endOrthogonalPath.put(endPoint, path);
			orthogonalPaths.add(path);
			draw(startPoint, endPoint, path, 16);
		}
	}
	
	protected void initializeLinks(ArrayList links) {
		
		if (links == null) return;
		orderredPorts = new TreeSet();
		for (Iterator it = links.iterator(); it.hasNext(); ) {
			String port = (String)it.next();
			if (!orderredPorts.contains(port))
				orderredPorts.add(port);
		}
		for (Iterator it = orderredPorts.iterator(); it.hasNext(); ) {
			String portName = (String)it.next();
			Integer in = Integer.valueOf(currentPozition);
			names2no.put(portName, in);
			no2names.put(in, portName);
			namedPorts.put(portName, ports.get(in));
			currentPozition++;
		}
	}
	
	protected void draw(Point2D.Double startPoint, Point2D.Double endPoint, OrthogonalPath path, int timed) {
		
		if (timed <= 0) return;
		
		Point2D.Double start = new Point2D.Double(startPoint.getX(), startPoint.getY());
		Point2D.Double end = new Point2D.Double(endPoint.getX(), endPoint.getY());
		
		if (startPoint.getX() == endPoint.getX() && startPoint.getY() == endPoint.getY()) return;
		
		// do we need to reorder the points on x ?
		if (start.getX() > end.getX()) {
			reorder(start, end);
		}
		
		double x1 = start.getX();
		double y1 = start.getY();
		double x2 = end.getX();
		double y2 = end.getY();
		
		if (y1 == y2) { // direct line ?
			if (!yaxis.contains(Double.valueOf(y1))) {
				yaxis.add(Double.valueOf(y1));
				return;
			}
			// else we already have a line crossing through there
			xaxis.add(Double.valueOf(x1));
			double y = 0.0;
			if (y1 < (max/2.0))
				y = getSpaceY(y1+Math.abs((y2-y1)/3.0));
			else
				y = getSpaceY(y1-Math.abs((y2-y1)/3.0));
			path.addPoint(new Point2D.Double(x1, y));
			Point2D.Double p = new Point2D.Double(x1, y);
			draw(p, end, path, timed-1);
			return;
		}
		if (x1 == x2) { // direct line ?
			if (!xaxis.contains(Double.valueOf(x1))) {
				xaxis.add(Double.valueOf(x1));
				return;
			}
			// else we already have a line crossing through there
			yaxis.add(Double.valueOf(y1));
			double x = 0.0;
			if (x1 < (max/2.0))
				x = getSpaceX(x1+Math.abs((x2-x1)/3.0));
			else
				x = getSpaceX(x1-Math.abs((x2-x1)/3.0));
			path.addPoint(new Point2D.Double(x, y1));
			Point2D.Double p = new Point2D.Double(x, y1);
			draw(p, end, path, timed-1);
			return;
		}
		// else no straigh line
		if (x2 < max && !yaxis.contains(Double.valueOf(y1)) && !xaxis.contains(Double.valueOf(x2))) {
			path.addPoint(new Point2D.Double(x2, y1));
			xaxis.add(Double.valueOf(x2));
			yaxis.add(Double.valueOf(y1));
			return;
		}
		if (x2 < max && !yaxis.contains(Double.valueOf(y1))) {
			yaxis.add(Double.valueOf(y1));
			double x = getSpaceX((x1+x2)/2.0);
			path.addPoint(new Point2D.Double(x, y1));
			Point2D.Double p = new Point2D.Double(x, y1);
			draw(p, end, path, timed-1);
			return;
		}
		if (x2 < max && !xaxis.contains(Double.valueOf(x1))) {
			xaxis.add(Double.valueOf(x1));
			double y = getSpaceY((y1+y2)/2.0);
			path.addPoint(new Point2D.Double(x1, y));
			Point2D.Double p = new Point2D.Double(x1, y);
			draw(p, end, path, timed-1);
			return;
		}
		if (x2 < max) {
			Point2D.Double p = null;
			if (x1 != max) {
				double s = 0.0;
				if (x1 < (max/2.0)) s = 1.0;
				else s = -1.0;
				path.addPoint(new Point2D.Double(x1+s * 0.5, y1));
				p = new Point2D.Double(x1+s*0.5, y1);
			} else {
				double s = 0.0;
				if (y1 < (max/2.0)) s = 1.0;
				else s = -1.0;
				path.addPoint(new Point2D.Double(x1, y1+s*0.5));
				p = new Point2D.Double(x1, y1+s*0.5);
			}
			draw(p, end, path, timed-1);
			return;
		}
		if (y2 < max && !yaxis.contains(Double.valueOf(y2)) && !xaxis.contains(Double.valueOf(x1))) {
			path.addPoint(new Point2D.Double(x1, y2));
			xaxis.add(Double.valueOf(x1));
			yaxis.add(Double.valueOf(y2)); 
			return;
		}
		if (y2 < max && !xaxis.contains(Double.valueOf(x1))) {
			xaxis.add(Double.valueOf(x1));
			double y = getSpaceY((y1+y2) / 2.0);
			path.addPoint(new Point2D.Double(x1, y));
			Point2D.Double p = new Point2D.Double(x1, y);
			draw(p, end, path, timed-1);
			return;
		}
		if (y2 < max && !yaxis.contains(Double.valueOf(y1))) {
			yaxis.add(Double.valueOf(y1));
			double x = getSpaceX((x1+x2) / 2.0);
			path.addPoint(new Point2D.Double(x, y1));
			Point2D.Double p = new Point2D.Double(x, y1);
			draw(p, end, path, timed-1);
			return;
		}
		if (y2 < max) {
			Point2D.Double p = null;
			if (x1 != max) {
				double s = 0.0;
				if (x1 < (max/2.0)) s = 1.0;
				else s = -1.0;
				path.addPoint(new Point2D.Double(x1+s * 0.5, y1));
				p = new Point2D.Double(x1+s*0.5, y1);
			} else {
				double s = 0.0;
				if (y1 < (max/2.0)) s = 1.0;
				else s = -1.0;
				path.addPoint(new Point2D.Double(x1, y1+s*0.5));
				p = new Point2D.Double(x1, y1+s*0.5);
			}
			draw(p, end, path, timed-1);
			return;
		}
		// since either x2 either y2 must be < max there is no possibility of any code reaching this point.... so stop.
	}
	
	protected double getSpaceY(double y) {
		
		if (!yaxis.contains(Double.valueOf(y))) return y;
		Double previous = null;
		for (Iterator it = yaxis.iterator(); it.hasNext(); ) {
			Double current = (Double)it.next();
			if (current.doubleValue() >= y)
				if (previous != null) return (previous.doubleValue()+current.doubleValue())/2.0;
				else if (it.hasNext())
					return (((Double)it.next()).doubleValue() + current.doubleValue()) / 2.0;
			previous = current;
		}
		return previous.doubleValue() / 2.0;
	}
	
	protected double getSpaceX(double x) {
		
		if (!xaxis.contains(Double.valueOf(x))) return x;
		Double previous = null;
		for (Iterator it = xaxis.iterator(); it.hasNext(); ) {
			Double current = (Double)it.next();
			if (current.doubleValue() >= x)
				if (previous != null) return (previous.doubleValue()+current.doubleValue()) / 2.0;
				else if (it.hasNext())
					return (((Double)it.next()).doubleValue() + current.doubleValue()) / 2.0;
			previous = current;
		}
		return previous.doubleValue() / 2.0;
	}
	
	protected void reorder(Point2D.Double p1, Point2D.Double p2) {
		
		Point2D.Double tmp = new Point2D.Double(p1.getX(), p1.getY());
		p1.setLocation(p2);
		p2.setLocation(tmp);
	}
	
	public Vector getOrthogonalPaths() {
		
		return orthogonalPaths;
	}
	
} // end of class OrthogonalLayout

