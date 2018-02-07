package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.util.Vector;

import javax.media.j3d.Alpha;
import javax.media.j3d.Appearance;
import javax.media.j3d.BoundingSphere;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.LineStripArray;
import javax.media.j3d.PositionPathInterpolator;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3d;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import lia.util.ntp.NTPDate;

import com.sun.j3d.utils.geometry.Sphere;

/** An undirected arc connecting two points along a great circle. */
@SuppressWarnings("restriction")
public class Arc3D extends BranchGroup { //implements Runnable {

  Shape3D arc;
  Appearance arcAppear;
  BranchGroup animGroup;
  Vector alphas;
  Vector transfGroups;
  Color3f color;
  double bow;
  Point3d[] points;						// points on the arc
  ILink link;
  Thread colorThread;
  int TYPE_CYLINDERS = 1;				// the arc is made of cylinders
  int TYPE_SIMPLE = 2;					// the arc is a simple set of lines
  int linkType = TYPE_SIMPLE;
  double CYLINDER_RADIUS = 0.008;		// how thick the cylinders should be 
  private double cylDensity = Double.valueOf(AppConfig.getProperty("lia.Monitor.Arc3D.cylDensity","0.6")).doubleValue(); // should be a divisor of divisions !!!, 2^q

  /* Here is how the local coordinate system is set up.
   *
   *        Z
   *        |
   *        |_____ Y
   *       / \ 
   *      /   \
   *     X     E
   *
   * X is the location of the first node.
   * E is the location of the second node.
   * Z is the normalized cross product of X and E.
   * Y is the normalized cross product of Z and X.
   * a is the angle between X and E. */
  WorldCoordinates start;
  WorldCoordinates end;
  Vector3d X;
  Vector3d Y;
  Vector3d Z;
  Vector3d E;
  double a;

  public Arc3D(WorldCoordinates start, WorldCoordinates end, Color3f color, ILink link) {
    this.start = start;
    this.end = end;
    this.color = color;
    bow = 0;

    setupCoordinateSystem();
    buildArc();
  }

  public Arc3D(WorldCoordinates start, WorldCoordinates end, Color3f color) {
	this.start = start;
	this.end = end;
	this.color = color;
	bow = 0;
	setCapability(ALLOW_DETACH);

	setupCoordinateSystem();
	buildArc();
  }

  
  /** Constructor.
    * @param bow how far the arc should "bow", i.e. deviate from a great circle */
  public Arc3D(WorldCoordinates start, WorldCoordinates end, Color3f color, double bow, ILink link) {
    this.start = start;
    this.end = end;
    this.color = color;
    this.bow = bow;
	this.link = link;
	alphas = new Vector();
	transfGroups = new Vector();
	setCapability(ALLOW_DETACH);
	setCapability(ALLOW_CHILDREN_EXTEND);
	setCapability(ALLOW_CHILDREN_READ);
	setCapability(ALLOW_CHILDREN_WRITE);

//	if(link.name.indexOf("->") >=0)
//		linkType = TYPE_SIMPLE;			// for abpings
//	else
//		linkType = TYPE_CYLINDERS;		// for wan
	
	linkType = TYPE_SIMPLE;
    setupCoordinateSystem();
    buildArc();
  }

public void setScale(double scale) {
	Transform3D transf = new Transform3D();
	for(int i=0; i<transfGroups.size(); i++){
		TransformGroup tg = (TransformGroup)transfGroups.get(i);
		tg.getTransform(transf);
		transf.setScale(scale);
		tg.setTransform(transf);
	}
}

  /** Set the color of the arc. */
  public void setColor(Color3f color) {
    if ( this.color == null ) this.color = color;
    if ( color == null ) return;
    
    ColoringAttributes colorAttr = arcAppear.getColoringAttributes();
    Color3f tc = new Color3f();
    colorAttr.getColor(tc);
    
    if ( tc != null && !tc.equals(color)) {
        this.color = color;
        colorAttr.setColor(color);
    }
    
	if(linkType == TYPE_CYLINDERS) {
		int n = alphas.size();
		double spd = getSpeed();
		long duration = (long) ((400.0+8000.0*(1.0-spd))*n);
//		if(link.name.indexOf("Geant") >=0 || link.name.indexOf("Prod-US") >=0)
//			System.out.println("setting lc for "+link.name+" to "+duration+" n="+n+" spd="+spd);
	    for(int i=0; i<n; i++){
	    	Alpha a = (Alpha) alphas.get(i);
	    	long oldDuration = a.getIncreasingAlphaDuration();
	  		if(oldDuration == duration)
	  			continue;
			long diff = (long)((NTPDate.currentTimeMillis() - a.getStartTime()) *
								(1.0f - (float)duration / (float)oldDuration));
			long oldST = a.getStartTime();
			float oldVal = a.value();
			a.setIncreasingAlphaDuration(duration);
			a.setStartTime(a.getStartTime() + diff);
//	    	long ST = a.getStartTime();
//			float val = a.value();
//	    	if(i == 0)
//	    		System.out.println(link.name+" oST="+oldST+" ST="+ST
//	    				+" oD="+oldDuration+" d="+duration	    		
//	    				+ "oV="+oldVal +" V="+val);
	    }
	}
  }
	
	/** change animation status (on/off), if needed */
	public void setAnimationStatus(boolean animated){
		boolean oldAnimStat = (linkType == TYPE_CYLINDERS);
		if(oldAnimStat == animated)
			return;
		// changed
		linkType = (animated ? TYPE_CYLINDERS :TYPE_SIMPLE);
		if(animated){
			if(animGroup == null)
				buildCylindersGeometry();
			else{
//				System.out.println("resuming anim");
				addChild(animGroup);
				long now = NTPDate.currentTimeMillis();
				int n = alphas.size();
				for(int i=0; i<n; i++){
					Alpha a = (Alpha) alphas.get(i);
					a.resume(now);
				}
			}
		}else{
			if(animGroup != null){
//				System.out.println("pausing anim");
				animGroup.detach();
				removeChild(animGroup);
				long now = NTPDate.currentTimeMillis();
				int n = alphas.size();
				for(int i=0; i<n; i++){
					Alpha a = (Alpha) alphas.get(i);
					a.pause(now);
				}
			}
		}
	}

  void setupCoordinateSystem() {
    X = start.toUnitVector();
    E = end.toUnitVector();
    Z = new Vector3d();
    Z.cross(X, E);
    Z.normalize();
    Y = new Vector3d();
    Y.cross(Z, X);
    Y.normalize();
    a = X.angle(E);
  }

  	/** Initialize the arc shape */
	void buildArc() {
	    // There should be at least five line segments on the curve.
	    // After that, one for every degree on the globe sounds about right.
	    double degree = 10*Math.PI/180.0;
	    int n = (a < degree) ? 5 : (int) (5 + a/degree);
	
//		if(linkType == TYPE_CYLINDERS)
//			n = (int)(n * 1.5);
	
	    double da = a/n;
	
	    points = new Point3d[n+1];
	    for(int i = 0; i <= n; i++) {
	      // TODO: document this
	      double c = Math.cos(i*da);
	      double s = Math.sin(i*da);
	      double r = (1-i/n)*start.radius + (i/n)*end.radius;
	      double b = bow*i*(n-i)/(n*n);
	      Vector3d point = new Vector3d(c*X.x + s*Y.x + b*Z.x,
	                                    c*X.y + s*Y.y + b*Z.y,
	                                    c*X.z + s*Y.z + b*Z.z);
	      point.normalize();
	      point.scale(r);
	      points[i] = new Point3d(point);
	    }
	    
		ColoringAttributes colorAttr = new ColoringAttributes();
		colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
		colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
		colorAttr.setColor(color);
		
		arcAppear = new Appearance();
		arcAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
		arcAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);
		arcAppear.setColoringAttributes(colorAttr);
		
		buildSimpleGeometry();
		if(linkType == TYPE_CYLINDERS)
			buildCylindersGeometry();
	}

	void buildSimpleGeometry(){		
		LineStripArray lineStrip =
		  new LineStripArray(points.length, GeometryArray.COORDINATES, new int[] {points.length});
		lineStrip.setCoordinates(0, points);
		arc = new Shape3D(lineStrip, arcAppear);
		arc.setPickable(false);
	    addChild(arc);
	}
	
	/** get the rigt color for a cylinder at a certain moment */
	void setCylinderArcColor(int pos, int moment, Appearance appear){
		int howOften = 4;
		int col = Math.abs((pos + moment) % howOften);
		if(col < howOften-1)
			appear.getColoringAttributes().setColor(color);
		else
			appear.getColoringAttributes().setColor(1f, 1f, 1f);
	}
	
//	Appearance buildAppearance(){
//
//		Color3f white = new Color3f(0f, 0f, 0f);
//		Material mat = new Material(color, white, color, white, 90.0f);
//		mat.setCapability(Material.ALLOW_COMPONENT_WRITE);
//		mat.setCapability(Material.ALLOW_COMPONENT_READ);
//
//		Appearance appear = new Appearance();
//		appear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
//		appear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);
//		appear.setMaterial(mat);
//		
//		return appear;
//	}
	
	void buildCylindersGeometry() {
//		System.out.println("building anim");
		alphas.clear();
		animGroup = new BranchGroup();
		animGroup.setCapability(BranchGroup.ALLOW_DETACH);
		animGroup.setPickable(false);
		int n = 1 + (int)(points.length * cylDensity);				// number of cylinders
		double radius = CYLINDER_RADIUS;
		//double height = 4 * CYLINDER_RADIUS; 
		for(int i=0; i<n; i++){
//			Cylinder cyl = new Cylinder((float)radius, (float)height, 
//				Cylinder.GENERATE_NORMALS | Cylinder.ENABLE_APPEARANCE_MODIFY,
//				6, 1, buildAppearance());
//			cyl.setPickable(false);
//			cyl.setUserData(link);
//			cylVector.add(cyl);

			Sphere sphere = new Sphere((float)radius, Sphere.ENABLE_APPEARANCE_MODIFY 
									| Sphere.GENERATE_NORMALS, 5, arcAppear);

			// moving cylinder along the arc
			double spd = getSpeed();
			Alpha alpha = new Alpha(-1, (long)(400+8000*(1-spd))*n);		// default duration
			long startTime = NTPDate.currentTimeMillis();		// startTime
			long deltat = alpha.getIncreasingAlphaDuration() / n;
			if(i > 0)
				startTime = ((Alpha)alphas.get(i-1)).getStartTime() - deltat;
			alpha.setStartTime(startTime);
			alphas.add(alpha);
			
			TransformGroup target = new TransformGroup();
			target.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
			float [] knots = new float [points.length];
			Point3f[] positions = new Point3f[points.length];
			for(int j=0; j<points.length; j++){
				knots[j] = (float)j/(float)(points.length-1);
				positions[j] = new Point3f(points[j]);
			}
			Transform3D posTransf = new Transform3D();
			TransformGroup scaleGroup = new TransformGroup();
			Transform3D scaleTransf = new Transform3D();
			scaleGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
			scaleGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
			PositionPathInterpolator posPath = new PositionPathInterpolator(
					alpha, target, posTransf, knots, positions);
			posPath.setSchedulingBounds(new BoundingSphere());
			scaleGroup.addChild(sphere);
			target.addChild(scaleGroup);
			animGroup.addChild(target);
			animGroup.addChild(posPath);
			transfGroups.add(scaleGroup);
		}
		addChild(animGroup);
	}
	

	/** returns the speed of the link from 0 to 1 */
	double getSpeed(){
		// and this should be BLUE!
		double val = 1.0 - color.y; //((Double)link.data).doubleValue() / link.speed ; 
		val = Math.sqrt(val);
//		if(link.name.indexOf("Geant") >=0 || link.name.indexOf("Prod-US") >=0)
//			System.out.println("link "+link.name+" val="+((Double)link.data).toString()+" spd="+val);
		if(val < 0)	val = 0;
		if(val > 1) val = 1;
		return val;
	}
}
