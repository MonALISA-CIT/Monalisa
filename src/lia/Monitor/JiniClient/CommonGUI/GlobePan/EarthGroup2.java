package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.util.TimerTask;
import java.util.Vector;

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
import javax.media.j3d.Shape3D;
import javax.media.j3d.Texture;
import javax.media.j3d.Texture2D;
import javax.media.j3d.TextureAttributes;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.TexCoord2f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.GlobeTextureLoader;
import lia.Monitor.monitor.AppConfig;

import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.Stripifier;
import com.sun.j3d.utils.universe.SimpleUniverse;

@SuppressWarnings("restriction")
public class EarthGroup2 extends BranchGroup {
    private final BufferedImage buffBigImg;
    private BufferedImage buffSliceImg;
    private int[] buffImgLine;

    private final int divisions = 64; // how many points are /x and /y and /z, 2^k
    private Point3d points[][]; // divisions x divisions
    private Vector3f normals[][]; // divisions x divisions
    private final int shapesXcount = AppConfig.geti("lia.Monitor.globeTexture.slices", 2); // should be a divisor of divisions !!!, 2^q
    private final int shapesYcount = AppConfig.geti("lia.Monitor.globeTexture.slices", 2); // should be a divisor of divisions !!!, 2^r
    private final int linesPerShape = divisions / shapesYcount;
    private final int colsPerShape = divisions / shapesXcount;

    private int bigHeight;
    private int bigWidth;
    private int subWidth;
    private int subHeight;

    private Shape3D shapes[][]; // shapesXcount x shapesYcount
    private final double radius = 1.0; // shpere's radius

    private final String textureScaleFilter;

    public EarthGroup2() {
        this(null);
    }

    public EarthGroup2(BufferedImage image) {
        buffBigImg = image;
        textureScaleFilter = AppConfig.getProperty("lia.Monitor.globeTexture.scaleFilter", "nice"); // or "fast"
        if (buffBigImg != null) {
            bigHeight = buffBigImg.getHeight();
            bigWidth = buffBigImg.getWidth();
            subWidth = (colsPerShape * bigWidth) / divisions;
            subHeight = (linesPerShape * bigHeight) / divisions;
            buffSliceImg = new BufferedImage(subWidth, subHeight, buffBigImg.getType());
            buffImgLine = new int[subWidth];
        }
        setCapability(ALLOW_DETACH);
        createSphere();

        refreshTexture();
        // update the texture in background
        TimerTask ttask = new TimerTask() {
            public void run() {
                try {
                    refreshTexture();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        BackgroundWorker.schedule(ttask, 10 * 60 * 1000, 10 * 60 * 1000);

        compile();
        // cleanup
        //buffBigImg.flush();
        //		buffBigImg = null;
        //		buffSliceImg = null;
        //		buffImgLine = null;
    }

    private void createSphere() {
        createSpherePoints();
        createSphereShapes();
        for (int i = 0; i < shapesYcount; i++) {
            for (int j = 0; j < shapesXcount; j++) {
                //addChild(create3DShape(i, j));
                addChild(shapes[i][j]);
            }
        }
    }

    private void createSpherePoints() {
        // first generate points of a single meridian, in the xy plane
        Point3d meridian[] = new Point3d[divisions + 1];
        Transform3D iniTr = new Transform3D();
        iniTr.rotY(Math.PI / 2);
        for (int i = 0; i <= divisions; i++) {
            double alpha = (Math.PI / 2) - ((Math.PI / divisions) * i);
            double x = radius * Math.cos(alpha);
            double y = radius * Math.sin(alpha);
            meridian[i] = new Point3d(x, y, 0);
            iniTr.transform(meridian[i]);
        }

        // then rotate this points around the y axis, generating
        // 'divisions' meridianes
        points = new Point3d[divisions + 1][divisions];
        Transform3D transf = new Transform3D();
        transf.rotY((2.0 * Math.PI) / divisions);
        for (int j = 0; j < divisions; j++) {
            for (int i = 0; i <= divisions; i++) {
                // first copy old point
                points[i][j] = new Point3d(meridian[i]);
                // then generate the next position by rotating around Y axis
                transf.transform(meridian[i]);
            }
        }
        // generate normal for each point as a median value of normals between
        // normals obtained for a node with all 4 its neighbors
        normals = new Vector3f[divisions + 1][divisions];
        for (int j = 0; j < divisions; j++) {
            for (int i = 0; i <= divisions; i++) {
                Point3d p = points[i][j];
                normals[i][j] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                normals[i][j].normalize();
            }
        }
    }

    private void createSphereShapes() {
        shapes = new Shape3D[shapesYcount][shapesXcount];
        for (int i = 0; i < shapesYcount; i++) {
            for (int j = 0; j < shapesXcount; j++) {
                shapes[i][j] = create3DShape(i, j);
            }
        }
    }

    private Shape3D create3DShape(int lin, int col) {
        Vector triPoints = new Vector();
        Vector normVects = new Vector();
        int svcCrt = 0;
        int oldVertCount = 0;
        int[] stripVertCount = new int[colsPerShape];
        Vector texCoords = new Vector();
        float jTex = 0.0f;
        float iTex = 0.0f;
        for (int j = colsPerShape * col; j < (colsPerShape * (1 + col)); j++) {
            jTex = (j - (colsPerShape * col)) / (float) colsPerShape; // logical coords
            for (int i = linesPerShape * lin; i <= (linesPerShape * (1 + lin)); i++) {
                iTex = (i - (linesPerShape * lin)) / (float) linesPerShape; // logical (0<->1)
                //if(iTex > 1.0f) iTex = 1.0f;

                Point3d a = points[i][j];
                Point3d b = points[i][(j + 1) % divisions];

                float jTexB = jTex + (1.0f / linesPerShape);
                TexCoord2f tca = new TexCoord2f(jTex, 1.0f - iTex);
                TexCoord2f tcb = new TexCoord2f(jTexB, 1.0f - iTex);

                Vector3f na = normals[i][j];
                Vector3f nb = normals[i][(j + 1) % divisions];

                //System.out.println("lin="+lin+" col="+col+" jTex="+jTex+" iTex="+iTex);
                triPoints.add(b);
                texCoords.add(tcb);
                normVects.add(nb);
                triPoints.add(a);
                texCoords.add(tca);
                normVects.add(na);
            }
            // just finished a strip; fill the stripVertCount too
            stripVertCount[svcCrt++] = triPoints.size() - oldVertCount;
            oldVertCount = triPoints.size();
        }

        GeometryInfo gi = new GeometryInfo(GeometryInfo.TRIANGLE_STRIP_ARRAY);
        // initialize the geometry info here
        gi.setStripCounts(stripVertCount);
        gi.setCoordinates((Point3d[]) (triPoints.toArray(new Point3d[triPoints.size()])));

        gi.setTextureCoordinateParams(1, 2);
        gi.setTextureCoordinates(0, (TexCoord2f[]) texCoords.toArray(new TexCoord2f[texCoords.size()]));

        gi.setNormals((Vector3f[]) normVects.toArray(new Vector3f[normVects.size()]));

        // generate normals
        //NormalGenerator ng = new NormalGenerator();
        //ng.generateNormals(gi);

        // stripify
        Stripifier st = new Stripifier();
        st.stripify(gi);
        GeometryArray ga = gi.getGeometryArray();

        // create appearance for this shape
        Appearance appear = createSphereAppearance(lin, col);
        Shape3D shape = new Shape3D(ga, appear);
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        return shape;
    }

    private Appearance createSphereAppearance(int lin, int col) {
        Appearance appear = new Appearance();
        //System.out.println("createsSpehereAppearance "+lin+", "+col);
        if (buffBigImg == null) { // || (lin + col) % 2 == 0 ){
            Material mat = new Material();
            mat.setAmbientColor(new Color3f(0, 0.2f, 0.3f));
            mat.setDiffuseColor(new Color3f(0, 0.3f, 0.4f));
            mat.setSpecularColor(new Color3f(0, 0, 0));
            appear.setMaterial(mat);

            //			appear.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
            //			float r = (float) Math.random();
            //			float g = (float) Math.random();
            //			float b = (float) Math.random();
            //			Color3f c = new Color3f(r, g, b);
            //			Color3f black = new Color3f(0.0f, 0.0f, 0.0f);
            //			Material mat = new Material(c, black, c, black, 90.0f);
            //			appear.setMaterial(mat);
        } else {
            TextureAttributes attr = new TextureAttributes();
            attr.setTextureMode(TextureAttributes.MODULATE);
            appear.setTextureAttributes(attr);
            appear.setCapability(Appearance.ALLOW_TEXTURE_READ);

            // set material properties
            Color3f white = new Color3f(1f, 1f, 1f);
            Color3f black = new Color3f(0, 0, 0);
            appear.setMaterial(new Material(white, black, white, black, 1.0f));

            int subX = getSubX(col);
            int subY = getSubY(lin);
            //System.out.println("subX="+subX+" subY="+subY+" subW="+subWidth+" subH="+subHeight);

            getSubImage(buffBigImg, subX, subY, subWidth, subHeight, buffSliceImg);
            ImageComponent2D icloc = new ImageComponent2D(ImageComponent2D.FORMAT_RGB, buffSliceImg);
            icloc.setCapability(ImageComponent2D.ALLOW_IMAGE_READ);
            icloc.setCapability(ImageComponent2D.ALLOW_IMAGE_WRITE);
            buffSliceImg.flush();
            Texture2D texture = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, subWidth, subHeight);
            texture.setImage(0, icloc);
            texture.setCapability(Texture.ALLOW_IMAGE_READ);
            texture.setCapability(Texture.ALLOW_IMAGE_WRITE);

            int scaleFilter = Texture.FASTEST;
            if (textureScaleFilter.equals("nice")) {
                scaleFilter = Texture.NICEST;
            }
            texture.setMagFilter(scaleFilter); // FASTEST
            texture.setMinFilter(scaleFilter); // FASTEST
            texture.setBoundaryModeS(Texture2D.CLAMP_TO_EDGE);
            texture.setBoundaryModeT(Texture2D.CLAMP_TO_EDGE);
            texture.setEnable(true);

            appear.setTexture(texture);
        }
        return appear;
    }

    private void refreshTexture() {
        //	    System.out.println("starting sliced texture refresh @ "+ new Date());
        for (int i = 0; i < shapesYcount; i++) {
            for (int j = 0; j < shapesXcount; j++) {
                refreshSubTexture(i, j);
            }
        }
        //	    System.out.println("sliced texture refreshed");
    }

    private void refreshSubTexture(int lin, int col) {
        int subX = getSubX(col);
        int subY = getSubY(lin);
        //System.out.println("subX="+subX+" subY="+subY+" subW="+subWidth+" subH="+subHeight);

        getSubImage(buffBigImg, subX, subY, subWidth, subHeight, buffSliceImg);
        Appearance appear = shapes[lin][col].getAppearance();
        Texture2D texture = (Texture2D) appear.getTexture();
        ImageComponent2D icloc = (ImageComponent2D) texture.getImage(0);
        icloc.set(buffSliceImg);
        buffSliceImg.flush();
    }

    private int getSubX(int col) {
        return (col * colsPerShape * bigWidth) / divisions;
    }

    private int getSubY(int lin) {
        return (lin * linesPerShape * bigHeight) / divisions;
    }

    private void getSubImage(BufferedImage src, int x, int y, int w, int h, BufferedImage dest) {
        for (int i = y; i < (y + h); i++) {
            src.getRGB(x, i, w, 1, buffImgLine, 0, w);
            dest.setRGB(0, i - y, w, 1, buffImgLine, 0, w);
        }
    }

    public static void main(String args[]) {

        JFrame frame = new JFrame("Partial Globe test");

        JPanel globe = new Pane4();

        frame.getContentPane().add(globe);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}

@SuppressWarnings("restriction")
class Pane4 extends JPanel {
    private SimpleUniverse u = null;
    private final Canvas3D canvas;

    Pane4() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(800, 400));

        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();

        canvas = new Canvas3D(config);
        add("Center", canvas);

        // Create a simple scene and attach it to the virtual universe
        BranchGroup scene = createSceneGraph();
        u = new SimpleUniverse(canvas);

        // This will move the ViewPlatform back a bit so the
        // objects in the scene can be viewed.
        u.getViewingPlatform().setNominalViewingTransform();

        u.addBranchGraph(scene);
        //		JOptionPane.showMessageDialog(this, "done.");
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
        objRoot.addChild(objTrans);

        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3d(0, 0, -2));
        objTrans.setTransform(t);

        Rotator rotator = new Rotator(objTrans);
        canvas.addMouseListener(rotator);
        canvas.addMouseMotionListener(rotator);

        Translator translator = new Translator(objTrans);
        canvas.addMouseListener(translator);
        canvas.addMouseMotionListener(translator);

        Zoomer zoomer = new Zoomer(objTrans);
        canvas.addMouseListener(zoomer);
        canvas.addMouseMotionListener(zoomer);
        canvas.addMouseWheelListener(zoomer);

        //		// Create a bounds for the background and lights
        //		BoundingSphere NO_BOUNDS =
        //			new BoundingSphere(new Point3d(0.0,0.0,0.0), 100.0);

        BoundingBox NO_BOUNDS = new BoundingBox(new Point3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY), new Point3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY));
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1.0f, -1.0f, -1.0f));
        sun.setInfluencingBounds(NO_BOUNDS);
        objTrans.addChild(sun);

        AmbientLight ambient = new AmbientLight(new Color3f(0.5f, 0.5f, 0.5f));
        ambient.setInfluencingBounds(NO_BOUNDS);
        objRoot.addChild(ambient);

        Background background = new Background(0, 0, 0);
        background.setApplicationBounds(NO_BOUNDS);
        objRoot.addChild(background);

        // Build the Earth
        ClassLoader classLoader = getClass().getClassLoader();
        BranchGroup globe = null;
        BufferedImage buffImg = null;
        try {
            //URL textureURL = classLoader.getResource("lia/images/earth_texture512x512.jpg");
            //			JOptionPane.showMessageDialog(this, "Loading texture...");
            System.out.println("Loading texture...");
            buffImg = GlobeTextureLoader.getBufferedImage();
            //		  	System.out.println("texture loaded; creating image component2d");
            //		  	image = new ImageComponent2D(ImageComponent2D.FORMAT_RGB, bi);
            //			JOptionPane.showMessageDialog(this, "texture loaded; building globe");
            System.out.println("texture loaded; building globe");
            //		  	URL textureURL = new URL("http://monalisa.cern.ch/MONALISA/DEMO/jClient/images/earth_texture2048x1024.jpg");
            //		  	TextureLoader textureLoader = new TextureLoader(textureURL, this);
            //		  	image = textureLoader.getImage();
            //		  	System.out.println("Texture successfully loaded!");
        } catch (Exception e) {
            System.out.println("Could not load texture.");
        }
        globe = new EarthGroup2(buffImg);
        objTrans.addChild(globe);
        //		JOptionPane.showMessageDialog(this, "scene finished");
        System.out.println("scene finished");

        // Have Java 3D perform optimizations on this scene graph.
        objRoot.compile();

        return objRoot;
    }

}
