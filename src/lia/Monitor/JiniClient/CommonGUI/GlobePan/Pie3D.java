package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.util.Hashtable;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.ColoringAttributes;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.LineStripArray;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TriangleArray;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;

import com.sun.j3d.utils.universe.SimpleUniverse;

@SuppressWarnings("restriction")
public class Pie3D extends BranchGroup {
	
	static int BASE_POINT_COUNT = 20;			// number of points at the
	static double RADIUS = 0.03;
	static double HEIGHT = 0.03;
	rcNode n;
	TransformGroup transfGroup;
    private static final Color3f black = new Color3f(0.0f, 0.0f, 0.0f);

	private Point3d [] base;			// base points
	private Point3d [] top;				// top points
	private Point3d middleTop;
	private Shape3D [] pieFaces = new Shape3D[BASE_POINT_COUNT];		// pie faces
	private long lastTimeUpdate;
	
	String LAT, LONG;//geographical coordinates for 3d representation of rcNode; these may differ from those in rcNode

	private Tooltip3D tooltip;
	private double currentScale = 1.0;
//    private static final long updateDelata = 5 * 1000;//5s

	public Pie3D(rcNode n){
		this.n = n;
		setCapability(ALLOW_DETACH);
		setCapability(ALLOW_CHILDREN_EXTEND);
		setCapability(ALLOW_CHILDREN_READ);
		setCapability(ALLOW_CHILDREN_WRITE);
		lastTimeUpdate = 0;
		transfGroup = new TransformGroup();
		WorldCoordinates loc = new WorldCoordinates(n.LAT, n.LONG);
		LAT = n.LAT;
		LONG = n.LONG;
		
		transfGroup.setTransform(loc.toTransform());
		transfGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		transfGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		addChild(transfGroup);
		
		buildPoints();
		
//		reBuild3DPie(null);
//		reBuild3DPie((pie) n.haux.get("LoadPie"));
//		
//		Transform3D t3d = new Transform3D();
//		transfGroup.getTransform(t3d);
//		t3d.rotX(Math.PI/3);
//		transfGroup.setTransform(t3d);
		//refresh("LoadPie");
		
		//compile();
		
		tooltip = new Tooltip3D(loc.toTransform());
	}

	public void setScale(double scale){
		Transform3D t = new Transform3D();
		transfGroup.getTransform(t);
		t.setScale(scale);
		transfGroup.setTransform(t);
		if(tooltip != null)
			tooltip.setScale(scale);
		currentScale = scale;
	}

	public void refresh(String pieKey){
		//System.out.println("trying to refresh pie");
		pie crtPie = (pie) n.haux.get(pieKey);
		pie tmpPie = null;
		if(crtPie != null){
			synchronized(crtPie){
				tmpPie = new pie(crtPie);
			}
		}
		reBuild3DPie(tmpPie);
		if ( n.LAT.compareTo(LAT)!=0 || n.LONG.compareTo(LONG)==0 )
		    updatePosition();
	}
	
	public void updatePosition()
	{
	    LAT = n.LAT;
	    LONG = n.LONG;
		WorldCoordinates loc = new WorldCoordinates(LAT, LONG);
		transfGroup.setTransform(loc.toTransform());
		Transform3D t = new Transform3D();
		transfGroup.getTransform(t);
		t.setScale(currentScale);
		transfGroup.setTransform(t);
		tooltip = new Tooltip3D(loc.toTransform());
	}

	void buildPoints(){
		base = new Point3d[BASE_POINT_COUNT];
		top = new Point3d[BASE_POINT_COUNT];
		middleTop = new Point3d(0, 0, HEIGHT);
			
		for(int i=0; i<BASE_POINT_COUNT; i++){
			double val = 2.*Math.PI*i/(double)BASE_POINT_COUNT;
			double x = RADIUS * Math.cos(val);
			double y = RADIUS * Math.sin(val);
			base[i] = new Point3d(x, y, 0.0);
			top[i] = new Point3d(x, y, HEIGHT);
		}
		// build the shape in the default color
		// subsequent calls to reBuild3DPie will only change shapes' color
		for(int i=0; i<pieFaces.length; i++){
			TriangleArray ta = new TriangleArray(9, GeometryArray.COORDINATES | GeometryArray.NORMALS);

			Point3d [] facesPoints = new Point3d[9];
			
			// setup a triangle on lateral face
			facesPoints[0] = base[i];
			facesPoints[1] = base[(i+1) % BASE_POINT_COUNT];
			facesPoints[2] = top[i];
			// setup the other triangle on lateral face
			facesPoints[3] = base[(i+1) % BASE_POINT_COUNT];
			facesPoints[4] = top[(i+1) % BASE_POINT_COUNT];
			facesPoints[5] = top[i];
			// setup top face triangle
			facesPoints[6] = top[i];
			facesPoints[7] = top[(i+1) % BASE_POINT_COUNT];
			facesPoints[8] = middleTop;

			ta.setCoordinates(0, facesPoints);
			
			// setup normals to surfaces
			int face;
			Vector3f normal = new Vector3f();
			Vector3f v1 = new Vector3f();
			Vector3f v2 = new Vector3f();
			Point3f [] pts = new Point3f[3];
			for (int ii = 0; ii < 3; ii++) pts[ii] = new Point3f();

			for (face = 0; face < 3; face++) {
				ta.getCoordinates(face*3, pts);
				v1.sub(pts[1], pts[0]);
				v2.sub(pts[2], pts[0]);
				normal.cross(v1, v2);
				normal.normalize();
				for (int ii = 0; ii < 3; ii++) {
					Point3d p = facesPoints[face*3+ii];
					normal.set((float)p.x, (float)p.y, (float)p.z);  
					ta.setNormal((face * 3 + ii), normal);
				}
			}
			
			// setup appearance
			Appearance pieAppear = new Appearance();
            pieAppear.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
            pieAppear.setCapability(Appearance.ALLOW_MATERIAL_READ);
            
			// Set up the coloring properties
			Color3f col = new Color3f(1f, 1f, 0f);
            Material mat = new Material(col, black, col, black, 90.0f);
            mat.setCapability(Material.ALLOW_COMPONENT_READ);
            mat.setCapability(Material.ALLOW_COMPONENT_WRITE);
			//mat.setShininess(1);

			pieAppear.setMaterial(mat);

			pieFaces[i] = new Shape3D(ta, pieAppear);
			pieFaces[i].setUserData(n);
            pieFaces[i].setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
            pieFaces[i].setCapability(Shape3D.ALLOW_APPEARANCE_READ);
		}
		
		// build a circle on top of the pie
		LineStripArray lineStrip = 
			new LineStripArray(top.length+1, GeometryArray.COORDINATES, 
			new int[] {top.length + 1});
		lineStrip.setCoordinates(0, top);
		lineStrip.setCoordinate(top.length, top[0]);
		
		ColoringAttributes colorAttr = new ColoringAttributes();
		colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_READ);
		colorAttr.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);
		colorAttr.setColor(0, 0, 0);
		
		Appearance arcAppear = new Appearance();
		arcAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
		arcAppear.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_WRITE);
		arcAppear.setColoringAttributes(colorAttr);		
		Shape3D arc = new Shape3D(lineStrip, arcAppear);

		// add pie faces to scene
		for(int i=0; i<pieFaces.length; i++){
			if(pieFaces[i] != null)
				transfGroup.addChild(pieFaces[i]);
		}
		transfGroup.addChild(arc);
	}

	public void reBuild3DPie(pie p){
//        if ( NTPDate.currentTimeMillis() - lastTimeUpdate < updateDelata ) return; 
//        lastTimeUpdate = NTPDate.currentTimeMillis();
		int sumFaces = 0;
		double sumSubunit = 0;
		// Globally used colors
//		Color3f black = new Color3f(0.0f, 0.0f, 0.0f);
//		Color3f white = new Color3f(1.0f, 1.0f, 1.0f);
		
//		if( n.UnitName.equals("ucsd")|| n.UnitName.equals("caltech") )
//			System.out.println( n.UnitName + " rebuilding 3DPie");
		
		// default pie
		if(p == null || p.len == 0){
			p = new pie(1);
			p.rpie[0] = 1.0;
			p.cpie[0] = new Color(1f, 1f, 0f);
		}

		for(int i=0; i<p.len; i++){
			sumSubunit += p.rpie[i];
			int nFaces = (int) Math.round(sumSubunit  * (double)BASE_POINT_COUNT) - sumFaces;
			if(nFaces <= 0){
				continue;
			}
			sumFaces += nFaces;
			if(sumFaces > BASE_POINT_COUNT){
				sumFaces = BASE_POINT_COUNT;
			}
			Color3f col = new Color3f();
			col.set(p.cpie[i]);
			Color3f tc = new Color3f();
			if(sumFaces-nFaces < 0)
				continue;
			for(int j=sumFaces-nFaces; j < sumFaces; j++) {
                Appearance pieAppear = pieFaces[j].getAppearance();
               if ( pieAppear == null ) {
                 pieAppear = new Appearance();
                 pieAppear.setCapability(Appearance.ALLOW_MATERIAL_READ);
                 pieAppear.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
                 pieFaces[j].setAppearance(pieAppear);
               }

				// Set up the coloring properties
                Material mat = pieAppear.getMaterial();
                if ( mat == null ) {
                    mat = new Material(col, black, col, black, 90.0f);
                    mat.setCapability(Material.ALLOW_COMPONENT_WRITE);
                    mat.setCapability(Material.ALLOW_COMPONENT_READ);
                    pieAppear.setMaterial(mat);
                } else {
//					mat.getAmbientColor(tc);
					mat.getDiffuseColor(tc);
                      
                        if (! tc.equals(col) ){
//					if(n.UnitName.equals("ucsd") || n.UnitName.equals("caltech"))
//					   System.out.println(n.UnitName + " ... setting col face "+j+" "+col.toString()+ " crt col="+tc.toString());
                            mat.setAmbientColor(col);
                            mat.setDiffuseColor(col);
                            mat.setShininess(1);
							pieAppear.setMaterial(mat);
                        }
                        else{
//							if(n.UnitName.equals("ucsd") || n.UnitName.equals("caltech"))
//                        		System.out.println(n.UnitName + " #### face "+j+" tc == col "+ tc.toString()+ " == "+col.toString());
                       }
                }
			}
		}
//		if( n.UnitName.equals("ucsd")|| n.UnitName.equals("caltech") )
//			System.out.println( n.UnitName + " FINISHED rebuilding 3DPie");
	}
	
	public void setTooltipText(String text){
		tooltip.setLabel(text);
		tooltip.setScale(currentScale);
	}

	public void showTooltip(){
		if(!tooltip.isLive()){
			addChild(tooltip);
			//System.out.println("Showing tooltip for "+n.UnitName);
		}
	}
	
	public void hideTooltip(){
		if( tooltip.isLive()){
			tooltip.detach();
			removeChild(tooltip);
			//System.out.println("Hiding tooltip for "+n.UnitName);
		}
	}
	
	public boolean tooltipVisible(){
		return tooltip.isLive();
	}

	public static void main(String args[]){
		rcNode cern = new rcNode();
		cern.UnitName = "CERN";
		cern.CITY = "Geneva";
		cern.LAT = "46.22";
		cern.LONG = "6.15";
		cern.haux = new Hashtable();
		
		pie p1 = new pie(3);
		p1.cpie[0] = Color.pink; p1.rpie[0] = 0.4;
		p1.cpie[1] = Color.blue; p1.rpie[1] = 0.2;
		p1.cpie[2] = Color.green; p1.rpie[2] = 0.4;
		cern.haux.put("LoadPie", p1);
		
		JFrame frame = new JFrame("Pie3D test");
		
		JPanel globe = new Pane2(cern, p1);
		
		frame.getContentPane().add(globe);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
		((Pane2)globe).p3d.reBuild3DPie(p1);
	}

}
@SuppressWarnings("restriction")
class Pane2 extends JPanel {
	private SimpleUniverse u = null;
	public Pie3D p3d;
	
	Pane2(rcNode n, pie p){
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(800, 400));

		GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();

		Canvas3D c = new Canvas3D(config);
		add("Center", c);

		// Create a simple scene and attach it to the virtual universe
		BranchGroup scene = createSceneGraph(n, p);
		u = new SimpleUniverse(c);

		// This will move the ViewPlatform back a bit so the
		// objects in the scene can be viewed.
		u.getViewingPlatform().setNominalViewingTransform();

		u.addBranchGraph(scene);
		System.out.println("done.");
	}
	
	public BranchGroup createSceneGraph(rcNode n, pie p) {
		// Create the root of the branch graph
		BranchGroup objRoot = new BranchGroup();

		// Create the TransformGroup node and initialize it to the
		// identity. Enable the TRANSFORM_WRITE capability so that
		// our behavior code can modify it at run time. Add it to
		// the root of the subgraph.
		TransformGroup objTrans = new TransformGroup();
        objTrans.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        objTrans.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		objRoot.addChild(objTrans);		

//		// Create a bounds for the background and lights
//		BoundingSphere NO_BOUNDS =
//			new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);

		BoundingBox NO_BOUNDS = new BoundingBox(new Point3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
																new  Point3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
		DirectionalLight sun = new DirectionalLight();
		sun.setDirection(new Vector3f(0f, -1.0f, 0f));
		sun.setInfluencingBounds(NO_BOUNDS);
		objTrans.addChild(sun);

		AmbientLight ambient = new AmbientLight(new Color3f(0.5f, 0.5f, 0.5f));
		ambient.setInfluencingBounds(NO_BOUNDS);
		objRoot.addChild(ambient);

		Background background = new Background(0, 0, 0);
		background.setApplicationBounds(NO_BOUNDS);
		objRoot.addChild(background);


//		// Set up the global lights
//		 Color3f lColor1 = new Color3f(0.7f, 0.7f, 0.7f);
//		 Vector3f lDir1  = new Vector3f(-1.0f, -1.0f, -1.0f);
//		 Color3f alColor = new Color3f(0.2f, 0.2f, 0.2f);
//
//		 AmbientLight aLgt = new AmbientLight(alColor);
//		 aLgt.setInfluencingBounds(bounds);
//		 DirectionalLight lgt1 = new DirectionalLight(lColor1, lDir1);
//		 lgt1.setInfluencingBounds(bounds);
//		 objRoot.addChild(aLgt);
//		 objRoot.addChild(lgt1);


		p3d = new Pie3D(n);
		p3d.setScale(4.0);
		
		objTrans.addChild(p3d);
		
		System.out.println("scene finished");
		
		// Have Java 3D perform optimizations on this scene graph.
		objRoot.compile();

		return objRoot;
	}

	
}
