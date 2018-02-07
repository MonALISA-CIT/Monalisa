package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import javax.media.j3d.Appearance;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TriangleArray;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import lia.Monitor.monitor.ILink;

/** A directed arc connecting two points along a great circle. */
@SuppressWarnings("restriction")
public class DirectedArc3D extends Arc3D {

  public static double ARROW_LENGTH = 0.025;
  public static double ARROW_WIDTH = 0.01;

  Shape3D arrow;
  Appearance arrowAppear;
  TransformGroup arrowGroup;
  
  private Tooltip3D tooltip;
  private double currentScale = 1.0;
  private Vector3d arrowPos;
  private ILink link;

  public DirectedArc3D(WorldCoordinates start, WorldCoordinates end, Color3f color, ILink link) {
    super(start, end, color, link);

    buildArrow(link);
    addChild(arrowGroup);
  }

  public DirectedArc3D(WorldCoordinates start, WorldCoordinates end, Color3f color, double bow, ILink link) {
    super(start, end, color, bow, link);

    buildArrow(link);
	this.link = link;
    
    // compute a transformation to get to arrowPos for the tooltip
    Transform3D tt = new Transform3D();
    float x = (float) arrowPos.x;
	float y = (float) arrowPos.y;
	float z = (float) arrowPos.z;
	Transform3D t1 = new Transform3D();
	Vector3d Z = new Vector3d(0, 0, 1);
	Vector3d X = new Vector3d(1, 0, 0);
	Vector3d Y = new Vector3d(0, 1, 0);
	
	double ux = - Math.atan2(y, Math.sqrt(x*x+z*z));
	double uy = Math.atan2(x, z);
	double uz = 0;
	t1.set(arrowPos); tt.mul(t1);
	t1.rotY(uy); tt.mul(t1);
	t1.rotX(ux); tt.mul(t1);
	t1.rotZ(uz); tt.mul(t1);
	tooltip = new Tooltip3D(tt);
  }

  public void setScale(double scale) {
    super.setScale(scale);
    Transform3D t = new Transform3D();
    arrowGroup.getTransform(t);
    t.setScale(scale);
    arrowGroup.setTransform(t);
	if(tooltip != null)
		tooltip.setScale(scale);
	currentScale = scale;
  }

  public void setTooltipText(String text){
	  tooltip.setLabel(text);
	  tooltip.setScale(currentScale);
//	  System.out.println("setting tooltip for "+link);
  }

  public void showTooltip(){
  	  if(!tooltip.isLive()){
		  addChild(tooltip);
//		  System.out.println("showing tooltip for "+link);
	  }
  }
	
  public void hideTooltip(){
	  if( tooltip.isLive()){
		  tooltip.detach();
		  removeChild(tooltip);
//		  System.out.println("hiding tooltip for "+link);
	  }
  }

  /** Set the color of the arc. */
  public void setColor(Color3f color) {
    super.setColor(color);

    ColoringAttributes colorAttr = arrowAppear.getColoringAttributes();
    Color3f tc = new Color3f();
    colorAttr.getColor(tc);
    
    if ( tc != null && !tc.equals(color)){
        this.color = color;
        colorAttr.setColor(color);
    }
  }

  /** Build the small arrow indicating the direction of the arc.
    * The arrow will be located a third of the way along the arc.  */
  void buildArrow(ILink link) {
    double c = Math.cos(a/3);
    double s = Math.sin(a/3);
    double db = bow/3;
    double b = bow*2/9;
    double r = start.radius*2/3 + end.radius/3;

    Vector3d forward = new Vector3d(-s*X.x + c*Y.x + db*Z.x, -s*X.y + c*Y.y + db*Z.y, -s*X.z + c*Y.z + db*Z.z);
    Vector3d up = new Vector3d(c*X.x + s*Y.x + b*Z.x, c*X.y + s*Y.y + b*Z.y, c*X.z + s*Y.z + b*Z.z);
    Vector3d left = new Vector3d(Z);

    forward.normalize();
    up.normalize();
//    left.normalize();

    Vector3d loc = new Vector3d(up);
    loc.scale(r);
	arrowPos = loc;
	
    forward.scale(ARROW_LENGTH/2);
    up.scale(ARROW_WIDTH);
    left.scale(ARROW_WIDTH);

    Point3d tip = new Point3d(forward);
    Point3d ubase = new Point3d(up);
    ubase.sub(forward);
    Point3d lbase = new Point3d(left);
    lbase.sub(forward);
    Point3d bbase = new Point3d(up);
    bbase.negate();
    bbase.sub(forward);
    Point3d rbase = new Point3d(left);
    rbase.negate();
    rbase.sub(forward);

    Point3d[] triangles = new Point3d[] {
      tip, ubase, rbase,
      tip, rbase, bbase,
      tip, bbase, lbase,
      tip, lbase, ubase,
      ubase, lbase, bbase,
      ubase, bbase, rbase
    };

    TriangleArray arrowGeom = new TriangleArray(18, GeometryArray.COORDINATES);
    arrowGeom.setCoordinates(0, triangles);

    ColoringAttributes colorAttr = new ColoringAttributes();
    colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
    colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
    colorAttr.setColor(color);

    arrowAppear = new Appearance();
    arrowAppear.setColoringAttributes(colorAttr);
    arrowAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
    arrowAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);

    Shape3D arrow = new Shape3D(arrowGeom, arrowAppear);
	arrow.setPickable(true);
	arrow.setUserData(link);
	//System.out.println("creating link: "+link.name+": "+link.fromIP+"->"+link.toIP);

    Transform3D arrowTransform = new Transform3D();
    arrowTransform.set(loc);

    arrowGroup = new TransformGroup();
    arrowGroup.setTransform(arrowTransform);
    arrowGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
    arrowGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
    arrowGroup.addChild(arrow);
    addChild(arrowGroup);
  }

}
