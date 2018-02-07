package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Material;
import javax.media.j3d.Texture;
import javax.media.j3d.TextureAttributes;
import javax.vecmath.Color3f;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;

@SuppressWarnings("restriction")
public class MoonGroup extends BranchGroup {

	Texture image = null;
	int divisions = 32;
	Sphere moon;

	public MoonGroup(Texture image) {
		this.image = image;
		build();
		setCapability(BranchGroup.ALLOW_DETACH);
	  }

	private void build(){
		Appearance appear = new Appearance();

		 // If an image was given, display the Earth with a texture.
		 if(image != null) {
		   Material mat = new Material();
		   mat.setAmbientColor(new Color3f(0.4f, 0.4f, 0.4f));
		   mat.setSpecularColor(new Color3f(0, 0, 0));
		   appear.setMaterial(mat);

		   TextureAttributes attr = new TextureAttributes();
		   attr.setTextureMode(TextureAttributes.MODULATE);
		   appear.setTextureAttributes(attr);

//		   Texture2D texture = new Texture2D(Texture.BASE_LEVEL, Texture.RGBA, image.getWidth(), image.getHeight());
//		   texture.setImage(0, image);
//		   texture.setMagFilter(Texture.BASE_LEVEL_LINEAR);
//		   texture.setMinFilter(Texture.BASE_LEVEL_LINEAR);
//		   texture.setEnable(true);
		   appear.setTexture(image);
		 }
		 // Otherwise just display a solid blue sphere.
		 else {
		   Material mat = new Material();
		   mat.setAmbientColor(new Color3f(0.8f, 0.6f, 0.0f));
		   mat.setDiffuseColor(new Color3f(0.8f, 0.5f, 0.0f));
		   mat.setSpecularColor(new Color3f(0, 0, 0));
		   appear.setMaterial(mat);
		 }
		moon = new Sphere(0.27f, Primitive.GENERATE_NORMALS | Primitive.GENERATE_TEXTURE_COORDS, divisions);
		moon.setAppearance(appear);
		addChild(moon);
	}

//	public static void main(String args[]){
//		
//		
//		JFrame frame = new JFrame("MoonTexture test");
//		
//		JPanel globe = new Pane3(frame);
//		
//		frame.getContentPane().add(globe);
//		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//		frame.pack();
//		frame.setVisible(true);
//	}

}

/*
class Pane3 extends JPanel {
	private SimpleUniverse u = null;
	public MoonGroup p3d;
	JFrame parent;

	Pane3(JFrame parent){
		this.parent = parent;
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

//			// Create a bounds for the background and lights
//			BoundingSphere NO_BOUNDS =
//				new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);

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


//			// Set up the global lights
//			 Color3f lColor1 = new Color3f(0.7f, 0.7f, 0.7f);
//			 Vector3f lDir1  = new Vector3f(-1.0f, -1.0f, -1.0f);
//			 Color3f alColor = new Color3f(0.2f, 0.2f, 0.2f);
//
//			 AmbientLight aLgt = new AmbientLight(alColor);
//			 aLgt.setInfluencingBounds(bounds);
//			 DirectionalLight lgt1 = new DirectionalLight(lColor1, lDir1);
//			 lgt1.setInfluencingBounds(bounds);
//			 objRoot.addChild(aLgt);
//			 objRoot.addChild(lgt1);


		//ClassLoader classLoader = getClass().getClassLoader();
		URL url = null;
		try {
			//url = classLoader.getResource("lia/images/earth_texture.jpg");
			url = new URL("file:///home/catac/workspace/MSRC/lia/images/earth_texture3.jpg");
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("URL="+url.toString());
		TextureLoader tex = new TextureLoader(url, parent);

		p3d = new MoonGroup(tex.getImage());
	
		Transform3D yAxis = new Transform3D();
		
		Alpha rotationAlpha = new Alpha(-1, Alpha.INCREASING_ENABLE,
											0, 0,
											1000, 0, 0,
											0, 0, 0);
		RotationInterpolator rotator =
			new RotationInterpolator(rotationAlpha, p3d, yAxis,
									 0.0f, (float) Math.PI*2.0f);
		BoundingSphere bounds =
			new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);

		rotator.setSchedulingBounds(bounds);

		objTrans.addChild(p3d);
		objTrans.addChild(rotator);
	
		System.out.println("scene finished");
	
		// Have Java 3D perform optimizations on this scene graph.
		objRoot.addChild(objTrans);		
		objRoot.compile();

		rotator.getAlpha().setIncreasingAlphaDuration(1000);

		rotator.setEnable(false);
		
		rotator.setEnable(true);

		return objRoot;
	}
}
*/