package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Appearance;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.Material;
import javax.media.j3d.QuadArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Texture;
import javax.media.j3d.Texture2D;
import javax.media.j3d.TextureAttributes;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TriangleFanArray;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.TexCoord2f;
import javax.vecmath.Vector3f;

import com.sun.j3d.utils.universe.SimpleUniverse;

@SuppressWarnings("restriction")
public class TexturedPie extends BranchGroup {
	
	static int BASE_POINT_COUNT = 20;			// number of points at the
	static double RADIUS = 0.025;
	static double HEIGHT = 0.02;
	BufferedImage imgTexture;
	public String name;
	public String LONG;
	public String LAT;
	TransformGroup transfGroup;
    private static final Color3f black = new Color3f(0.0f, 0.0f, 0.0f);
    Color3f baseColor;

	private Point3d [] base;			// base points
	private Point3d [] top;				// top points
	private Point3d middleTop;
	private Shape3D [] pieFaces = new Shape3D[BASE_POINT_COUNT];		// pie faces
	private long lastTimeUpdate;

	private Tooltip3D tooltip;
	private double currentScale = 1.0;
//  private static final long updateDelata = 5 * 1000;//5s

	public TexturedPie(BufferedImage img, Color baseColor, String LONG, String LAT, String name){
		imgTexture = img;
		this.baseColor = new Color3f(baseColor);
		this.name = name;
		this.LONG = LONG;
		this.LAT = LAT;
		setCapability(ALLOW_DETACH);
		setCapability(ALLOW_CHILDREN_EXTEND);
		setCapability(ALLOW_CHILDREN_READ);
		setCapability(ALLOW_CHILDREN_WRITE);
		lastTimeUpdate = 0;
		transfGroup = new TransformGroup();
		WorldCoordinates loc = new WorldCoordinates(LAT, LONG);
		
		transfGroup.setTransform(loc.toTransform());
		transfGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		transfGroup.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		addChild(transfGroup);
		
		buildPoints();

//		Transform3D t3d = new Transform3D();
//		transfGroup.getTransform(t3d);
//		t3d.rotX(Math.PI/3);
//		transfGroup.setTransform(t3d);
		
		compile();
		tooltip = new Tooltip3D(loc.toTransform());
		setTooltipText("Router @ " + name);
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
		// build the lateral part of the pie
		QuadArray qa = new QuadArray(BASE_POINT_COUNT*4, GeometryArray.COORDINATES | GeometryArray.NORMALS);
		Vector3f [] normals = new Vector3f[4];
		for(int j=0; j<4; j++)
			normals[j] = new Vector3f();
		Point3d [] facePts = new Point3d[4];
		for(int i=0; i<BASE_POINT_COUNT; i++){
			facePts[0] = base[i];
			facePts[1] = base[(i+1)%BASE_POINT_COUNT];
			facePts[2] = top[(i+1)%BASE_POINT_COUNT];
			facePts[3] = top[i];
			qa.setCoordinates(i*4, facePts);
			for(int j=0; j<4; j++){
				normals[j].set(facePts[j]);
			}
			qa.setNormals(i*4, normals);
		}
		
		Appearance latPieAppear = new Appearance();
        latPieAppear.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
        latPieAppear.setCapability(Appearance.ALLOW_MATERIAL_READ);
		Color3f col = baseColor;
        Material mat = new Material(col, black, col, black, 90.0f);
        mat.setCapability(Material.ALLOW_COMPONENT_READ);
        mat.setCapability(Material.ALLOW_COMPONENT_WRITE);
		latPieAppear.setMaterial(mat);
		Shape3D latShape = new Shape3D(qa, latPieAppear);
		latShape.setUserData(this);
		transfGroup.addChild(latShape);
		
		int[] fanLengths = { 1 + BASE_POINT_COUNT + 1};
		TriangleFanArray tfa = new TriangleFanArray(1 + BASE_POINT_COUNT+1, 
				GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.TEXTURE_COORDINATE_2,
				fanLengths);
		TexCoord2f tc2 = new TexCoord2f();
		Vector3f normal = new Vector3f();
		normal.set(middleTop);
		tfa.setCoordinate(0, middleTop);
		tfa.setNormal(0, normal);
		tc2.set(.5f, .5f);
		tfa.setTextureCoordinate(0, 0, tc2);
		for(int i=0; i<=BASE_POINT_COUNT; i++){
			tfa.setCoordinate(i+1, top[i%BASE_POINT_COUNT]);
			tfa.setNormal(i+1, normal);
			tc2.set((float) (.5+top[i%BASE_POINT_COUNT].x/2/RADIUS*.975), 
					(float) (.5+top[i%BASE_POINT_COUNT].y/2/RADIUS*.975));
//			System.out.println("i="+i+" x="+tc2.x+" y="+tc2.y);
			tfa.setTextureCoordinate(0, i+1, tc2);
		}
		
		ImageComponent2D icloc = new ImageComponent2D(ImageComponent2D.FORMAT_RGB4, imgTexture);
		Texture texture = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, 
				imgTexture.getWidth(), imgTexture.getHeight());
//		System.out.println("w="+imgTexture.getWidth()+" h="+imgTexture.getHeight());
		texture.setImage(0, icloc);
		int scaleFilter = Texture.NICEST; //Texture.FASTEST;
		texture.setMagFilter(scaleFilter);
		texture.setMinFilter(scaleFilter);

		Appearance appear = new Appearance();
		appear.setTexture(texture);
		
		Color3f white = new Color3f(1f, 1f, 1f);
		Color3f black = new Color3f(0, 0, 0);
		appear.setMaterial(new Material(white, black, white, black, 1.0f));
		
		TextureAttributes texAttr = new TextureAttributes();
		texAttr.setTextureMode(TextureAttributes.MODULATE);
		appear.setTextureAttributes(texAttr);

		Shape3D faceShape = new Shape3D(tfa, appear);
		faceShape.setUserData(this);
		transfGroup.addChild(faceShape);
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

	public static void main(String args[]){
		JFrame frame = new JFrame("Pie3D test");
		
		JPanel globe = new Pane3(); //cern, p1);
		
		frame.getContentPane().add(globe);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

}

@SuppressWarnings("restriction")
class Pane3 extends JPanel {
	private SimpleUniverse u = null;
	public TexturedPie p3d;
	
	Pane3(){
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(800, 400));

		GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();

		Canvas3D c = new Canvas3D(config);
		add("Center", c);

		// Create a simple scene and attach it to the virtual universe
		BranchGroup scene = createSceneGraph();
		u = new SimpleUniverse(c);

		// This will move the ViewPlatform back a bit so the
		// objects in the scene can be viewed.
		u.getViewingPlatform().setNominalViewingTransform();

		u.addBranchGraph(scene);
		System.out.println("done.");
	}
	
	private BufferedImage loadImage(URL url){
		BufferedImage res = null;
		try{
			ImageIcon icon = new ImageIcon(url);
			if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
				throw new Exception("failed");
			}
			Component obs = new Component() { };
			int width = icon.getIconWidth();
			int height = icon.getIconHeight();
			res = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D big = res.createGraphics();
			icon.paintIcon(obs, big, 0, 0);
			big.dispose();
		}catch(Exception e){
			System.out.println("Failed loading image from "+url.toString());
			e.printStackTrace();
		}
		return res;
	}
	
	public BranchGroup createSceneGraph() {
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

		AmbientLight ambient = new AmbientLight(new Color3f(0.85f, 0.85f, 0.85f));
		ambient.setInfluencingBounds(NO_BOUNDS);
		objRoot.addChild(ambient);

		Background background = new Background(.2f, .4f, .5f);
		background.setApplicationBounds(NO_BOUNDS);
		objRoot.addChild(background);


//		// Set up the global lights
		 Color3f lColor1 = new Color3f(0.7f, 0.7f, 0.7f);
		 Vector3f lDir1  = new Vector3f(-1.0f, -1.0f, -1.0f);
		 Color3f alColor = new Color3f(0.4f, 0.4f, 0.4f);

		 AmbientLight aLgt = new AmbientLight(alColor);
		 aLgt.setInfluencingBounds(NO_BOUNDS);
		 DirectionalLight lgt1 = new DirectionalLight(lColor1, lDir1);
		 lgt1.setInfluencingBounds(NO_BOUNDS);
		 objRoot.addChild(aLgt);
		 objRoot.addChild(lgt1);

		 ClassLoader myClassLoader = getClass().getClassLoader();
		 URL imageURL = myClassLoader.getResource("lia/images/ml_router.png");
		 BufferedImage img = loadImage(imageURL);
		
		 Color rtrColor = new Color(0, 170, 249);
		 
		p3d = new TexturedPie(img, rtrColor, "13", "-7", "My Router");
		p3d.setScale(4.0);
		
		objTrans.addChild(p3d);
		
		System.out.println("scene finished");
		
		// Have Java 3D perform optimizations on this scene graph.
		objRoot.compile();

		return objRoot;
	}
}
