package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.awt.geom.Point2D;
import java.util.Vector;

/**
 * This class represents an orthogonal path between two nodes.
 */
public class OrthogonalPath {

	private Point2D.Double A = null; // start point
	private Point2D.Double B = null; // end point
	private Vector innerPoints = null;
	
	public OrthogonalPath(Point2D.Double A, Point2D.Double B) {
		
		this.A = A;
		this.B = B;
		innerPoints = new Vector();
	}
	
	public void addPoint(Point2D.Double p) {
		
		innerPoints.add(p);
	}
	
	public Vector getPath() {
		
		Vector v = (Vector)innerPoints.clone();
		v.add(0, A);
		v.add(B);
		return v;
	}
	
	public Point2D.Double getFirstPoint() {
		
		return A;
	}
	
	public Point2D.Double getLastPoint() {
		
		return B;
	}
	
	public String toString() {
		
		String str = "";
		str += A+" --> ";
		for (int i=0; i<innerPoints.size(); i++) 
			str += innerPoints.get(i)+" --> ";
		str += B+"\n";
		return str;
	}
	
} // end of class OrthogonalPath

