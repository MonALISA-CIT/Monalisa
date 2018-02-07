package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Map;
import java.util.TimeZone;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.j3d.AmbientLight;
import javax.media.j3d.Background;
import javax.media.j3d.BoundingBox;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.DirectionalLight;
import javax.media.j3d.Group;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

import lia.Monitor.JiniClient.CommonGUI.GlobeTextureLoader;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ILink;
import net.jini.core.lookup.ServiceID;

import com.sun.j3d.utils.image.TextureLoader;
import com.sun.j3d.utils.universe.SimpleUniverse;

@SuppressWarnings("restriction")
public class GlobePanBase extends JPanel implements ActionListener, ChangeListener, NodeSelectionListener,
LinkHighlightedListener, graphical {

    /**
     *
     */
    private static final long serialVersionUID = -6591975936996900136L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GlobePanBase.class.getName());

    public static double STARTING_DISTANCE = 6.0;
    public BoundingBox NO_BOUNDS;
    public static String moonTextureFilename = "lia/images/moon_texture.jpg";
    public static String skyTextureFilename = "lia/images/sky_texture.jpg";

    public Canvas3D canvas;
    public SimpleUniverse universe;

    // This is how the scene graph is organized.
    public BranchGroup scene;
    public AmbientLight ambient;
    public DirectionalLight sun;
    public Background background;
    public TransformGroup sceneTransform;
    public TransformGroup spin;
    public EarthSpinBehavior spinBehavior;
    public TransformGroup moonSpin;
    public MoonGroup moonGroup;
    public MoonSpinBehavior moonSpinBehavior;
    private final Object syncRefresh = new Object();

    private final Object syncGparamCombobox = new Object();
    public JPanel toolbarsPanel;
    public JToolBar toolbar;
    public JToolBar statusbar;
    public JLabel status;
    public JSlider scaleSlider;
    public JSlider timeSlider;

    public boolean animateWANLinks;

    public Map<ServiceID, rcNode> hnodes; // map of rcNode.UnitName -> rcNode
    public Vector<rcNode> vnodes; // vector of rcNode
    public SerMonitorBase monitor;

    //	public Hashtable pingLinksCache = new Hashtable(); // cache with Ping ILinks

    protected Selector selector;
    protected Rotator rotator;
    protected Translator translator;
    protected Zoomer zoomer;

    //	int nodesMode;
    //	int peersMode;

    public GlobePanBase() {

        NO_BOUNDS = new BoundingBox(new Point3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY), new Point3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY));
        setLayout(new BorderLayout()); //new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(800, 400));

        toolbarsPanel = new JPanel();
        toolbarsPanel.setLayout(new BoxLayout(toolbarsPanel, BoxLayout.Y_AXIS));

        buildOptPan();
        buildToolbar();
        buildCanvas();
        buildStatusbar();

        add(toolbarsPanel, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusbar, BorderLayout.SOUTH);

        buildGroups(); // Build groups for nodes and links

        buildSelector();

        buildUniverse();

        rotator = new Rotator(sceneTransform);
        canvas.addMouseListener(rotator);
        canvas.addMouseMotionListener(rotator);

        translator = new Translator(sceneTransform);
        canvas.addMouseListener(translator);
        canvas.addMouseMotionListener(translator);

        zoomer = new Zoomer(sceneTransform);
        zoomer.setScaleSlider(scaleSlider);//called after scaleSlider is instantiated
        zoomer.resetPosSlider();
        canvas.addMouseListener(zoomer);
        canvas.addMouseMotionListener(zoomer);
        canvas.addMouseWheelListener(zoomer);

    }

    /** Initialize toolbar */
    protected void buildToolbar() {
        toolbar = new JToolBar(SwingConstants.HORIZONTAL);
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createRaisedBevelBorder());

        JButton reset_button = new JButton("Reset");
        reset_button.setActionCommand("reset");
        reset_button.addActionListener(this);

        JToggleButton select_button = new JToggleButton("Select");
        select_button.setActionCommand("select");
        select_button.addActionListener(this);
        select_button.setSelected(true);

        JToggleButton rotate_button = new JToggleButton("Rotate");
        rotate_button.setActionCommand("rotate");
        rotate_button.addActionListener(this);

        JToggleButton translate_button = new JToggleButton("Translate");
        translate_button.setActionCommand("translate");
        translate_button.addActionListener(this);

        JToggleButton zoom_button = new JToggleButton("Zoom");
        zoom_button.setActionCommand("zoom");
        zoom_button.addActionListener(this);

        JLabel behaviorsLabel = new JLabel("Left mouse button: ");

        ButtonGroup mouseBehaviors = new ButtonGroup();
        mouseBehaviors.add(select_button);
        mouseBehaviors.add(rotate_button);
        mouseBehaviors.add(zoom_button);
        mouseBehaviors.add(translate_button);

        JLabel scaleLabel = new JLabel("Scale nodes");
        scaleSlider = new JSlider(-100, 100, 0);
        scaleSlider.setMinorTickSpacing(50);
        scaleSlider.setPaintTicks(true);
        scaleSlider.addChangeListener(this);
        scaleSlider.setMaximumSize(new Dimension(80, 32));
        scaleLabel.setLabelFor(scaleSlider);

        JLabel timeLabel = new JLabel("Speed");
        timeSlider = new JSlider(0, 100, 0);
        timeSlider.setMinorTickSpacing(20);
        timeSlider.setPaintTicks(true);
        timeSlider.addChangeListener(this);
        timeSlider.setMaximumSize(new Dimension(80, 32));
        timeLabel.setLabelFor(timeSlider);

        toolbar.add(new JToolBar.Separator());
        toolbar.add(reset_button);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(behaviorsLabel);
        toolbar.add(select_button);
        toolbar.add(rotate_button);
        toolbar.add(translate_button);
        toolbar.add(zoom_button);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(scaleLabel);
        toolbar.add(scaleSlider);
        toolbar.add(timeLabel);
        toolbar.add(timeSlider);
        toolbar.add(Box.createHorizontalGlue());

        toolbarsPanel.add(toolbar);
    }

    /** Initialize canvas */
    protected void buildCanvas() {
        GraphicsConfiguration config = SimpleUniverse.getPreferredConfiguration();
        canvas = new Canvas3D(config);
        //add(canvas);
    }

    /** Initialize optPan */
    protected void buildOptPan() {
        // empty - to be redefined
    }

    /** Initialize statusbar */
    protected void buildStatusbar() {

        statusbar = new JToolBar(SwingConstants.HORIZONTAL);
        statusbar.setFloatable(false);
        statusbar.setLayout(new BorderLayout());
        status = new JLabel(" ");
        status.setHorizontalAlignment(JLabel.CENTER);
        statusbar.add(status, BorderLayout.CENTER);

        //add(statusbar);
    }

    /** Build Node, Ping etc. groups */
    protected void buildGroups() {
    }

    /** Build selector and attach listeners for nodes and links */
    protected void buildSelector() {
        selector = new Selector(canvas);
        canvas.addMouseListener(selector);
        canvas.addMouseMotionListener(selector);
        selector.addNodeSelectionListener(this);
        selector.addLinkHighlightedListener(this);
    }

    /** Initialize universe and scene */
    protected void buildUniverse() {
        universe = new SimpleUniverse(canvas);
        universe.getViewer().getView().setFrontClipDistance(0.01);

        buildSceneGraph();
        universe.addBranchGraph(scene);
    }

    protected void buildSceneGraph() {
        scene = new BranchGroup();

        sceneTransform = new TransformGroup();
        sceneTransform.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        sceneTransform.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        scene.addChild(sceneTransform);

        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3d(0, 0, -STARTING_DISTANCE));
        sceneTransform.setTransform(t);

        spin = new TransformGroup();
        spin.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        spin.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        spin.setCapability(Group.ALLOW_CHILDREN_WRITE);
        sceneTransform.addChild(spin);

        buildEarth();

        buildMoon();

        try {
            int speed = Integer.parseInt(AppConfig.getProperty("lia.Monitor.j3d.defaultRotationSpeed", "0"));
            timeSlider.setValue(speed);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error setting default rotation speed for j3d.", ex);
        }

        buildAmbient();

        addGroups();

        scene.compile();
    }

    /** build and add the earth to the scene */
    void buildEarth() {
        // first check if there is only one slice
        int imageSlices = AppConfig.geti("lia.Monitor.globeTexture.slices", 0);
        if (imageSlices < 2) {
            // single slice
            EarthGroup earthGroup = new EarthGroup(GlobeTextureLoader.getFinalBufferedImage());
            spin.addChild(earthGroup);
        } else {
            // multiple slices
            EarthGroup2 earthGroup = new EarthGroup2(GlobeTextureLoader.getFinalBufferedImage());
            spin.addChild(earthGroup);
        }

        spinBehavior = new EarthSpinBehavior(spin);
        spinBehavior.setSchedulingBounds(NO_BOUNDS);
        spin.addChild(spinBehavior);
    }

    /** build and add the moon to the scene */
    void buildMoon() {
        moonSpin = new TransformGroup();
        moonSpin.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        moonSpin.setCapability(Group.ALLOW_CHILDREN_EXTEND);
        moonSpin.setCapability(Group.ALLOW_CHILDREN_WRITE);
        sceneTransform.addChild(moonSpin);

        moonSpinBehavior = new MoonSpinBehavior(moonSpin);
        moonSpinBehavior.setSchedulingBounds(NO_BOUNDS);
        moonSpin.addChild(moonSpinBehavior);

        ClassLoader classLoader = getClass().getClassLoader();
        // Build Moon using the same classloader
        try {
            URL textureURL = classLoader.getResource(moonTextureFilename);
            TextureLoader textureLoader = new TextureLoader(textureURL, this);
            moonGroup = new MoonGroup(textureLoader.getTexture());
        } catch (Exception e) {
            logger.log(Level.WARNING, "buildMoon: Could not load " + moonTextureFilename + " moon texture", e);
            moonGroup = new MoonGroup(null);
        }
        moonSpin.addChild(moonGroup);
    }

    /** build ambient (light, sunlight, background texture)
     * and add it to the scene
     */
    void buildAmbient() {
        sun = new DirectionalLight(new Color3f(1f, 1f, 1f), new Vector3f(0f, -1.0f, 0f));
        sun.setDirection(calculateSunDirection());
        sun.setInfluencingBounds(NO_BOUNDS);
        sceneTransform.addChild(sun);

        ambient = new AmbientLight(new Color3f(1f, 1f, 1f)); //new Color3f(0.8f, 0.8f, 0.8f));
        ambient.setInfluencingBounds(NO_BOUNDS);
        scene.addChild(ambient);

        // Build background using the same classloader
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            URL textureURL = classLoader.getResource(skyTextureFilename);
            TextureLoader textureLoader = new TextureLoader(textureURL, this);
            background = new Background(textureLoader.getImage());
            background.setImageScaleMode(Background.SCALE_REPEAT);
        } catch (Exception e) {
            logger.log(Level.WARNING, "buildAmbient: Could not load " + skyTextureFilename + " background texture", e);
            background = new Background(0, 0, 0);
        }
        background.setApplicationBounds(NO_BOUNDS);
        scene.addChild(background);
    }

    /** add groups to scene */
    protected void addGroups() {
    }

    /** calculate sun direction */
    protected Vector3f calculateSunDirection() {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

        // Angle of Earth's rotation about the Sun
        double a = (2 * Math.PI * cal.get(Calendar.DAY_OF_YEAR)) / 365.0;
        // Angle of Earth's rotation about the Sun on June 21
        double a0 = (2 * Math.PI * 172) / 365.0;

        // Angle of Earth's tilt with respect to the Sun
        double tilt = (23.45 * Math.PI) / 180.0;

        // With respect to the Earth, the Sun's position varies sinusoidally
        // throughout the year, between +-23.45 degrees as measured from the
        // equator. The alpha value just says how far we are between these extremes.
        double alpha = Math.cos(a - a0);

        // Now we can get the Sun's direction easily
        return new Vector3f(0, (float) -Math.sin(alpha * tilt), (float) -Math.cos(alpha * tilt));
    }

    protected float calculateSensitivity() {
        Transform3D transform = new Transform3D();
        sceneTransform.getTransform(transform);
        Vector3f translation = new Vector3f();
        transform.get(translation);
        return (float) (translation.length() / STARTING_DISTANCE);
    }

    /** this should be redefinded */
    protected void refresh() {
        // Recalculate sensitivity for manipulators
        float sensitivity = calculateSensitivity();
        rotator.setSensitivity(sensitivity);
        translator.setSensitivity(sensitivity);
    }

    /**
     * ActionListener interface
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("reset")) {
            Transform3D t = new Transform3D();
            t.set(new Vector3d(0, 0, -STARTING_DISTANCE));
            sceneTransform.setTransform(t);
            //rebuildMSTLinksGroup();
            //spinBehavior.extraRotation = 0;  // set everything to current, real time
            //spinBehavior.count = -1;		   // make sure it resets _now_
            timeSlider.setValue(0);
            spinBehavior.reset();
            moonSpinBehavior.reset();
            refresh();
        } else if (cmd.equals("select")) {
            selector.setButton(1);
            rotator.setButton(-1);
            translator.setButton(-1);
            zoomer.setButton(-1);
            //scaleZoom.setButton(-1);
        } else if (cmd.equals("rotate")) {
            selector.setButton(-1);
            rotator.setButton(1);
            translator.setButton(-1);
            zoomer.setButton(-1);
            //scaleZoom.setButton(-1);
        } else if (cmd.equals("translate")) {
            selector.setButton(-1);
            rotator.setButton(-1);
            translator.setButton(1);
            zoomer.setButton(-1);
            //scaleZoom.setButton(-1);
        } else if (cmd.equals("zoom")) {
            selector.setButton(-1);
            rotator.setButton(-1);
            translator.setButton(-1);
            zoomer.setButton(1);
            //scaleZoom.setButton(1);
        } else {
            otherActionPerformed(e);
        }
    }

    /** called by actionPerformed for other events - this
     *  should be redefined
     */
    protected void otherActionPerformed(ActionEvent e) {
        // empty
    }

    /**
     * ChangeListener interface
     */
    @Override
    public void stateChanged(ChangeEvent e) {
        Object src = e.getSource();

        if (src == timeSlider) {
            //System.out.println("timeSlider");
            spinBehavior.setSpeed(timeSlider.getValue());
            moonSpinBehavior.setSpeed(timeSlider.getValue());
        } else {
            otherStateChanged(e);
        }
    }

    /** this should be redefined */
    protected void otherStateChanged(ChangeEvent e) {
    }

    /*
     * NodeSelectionListener interface
     */
    @Override
    public void nodeSelected(rcNode node) {
        if (node == null) {
            return;
        }

        node.client.setVisible(!node.client.isVisible());
    }

    /** this should be redefined - and registeder in buildSelector */
    @Override
    public void nodeHighlighted(rcNode node) {
    }

    /** this should be redefined - and registered in buildSelector */
    @Override
    public void linkHighlighted(Object link) { // should be ILink
    }

    /**
     * lia.Monitor.JiniClient.CommonGUI.graphical interface
     */
    @Override
    public void updateNode(rcNode node) {
        //refresh();
    }

    @Override
    public void gupdate() {
        //  	long now = NTPDate.currentTimeMillis();
        //  	if(now - last_update >= 4000){
        //  		last_update = now;
        //  		refresh();
        //  	}
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> hnodes, Vector<rcNode> vnodes) {
        this.hnodes = hnodes;
        this.vnodes = vnodes;
    }

    @Override
    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
    }

    @Override
    public void setMaxFlowData(rcNode node, Vector v) {
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            spinBehavior.setSpeed(timeSlider.getValue());
            moonSpinBehavior.setSpeed(timeSlider.getValue());
        } else {
            spinBehavior.setSpeed(0);
            moonSpinBehavior.setSpeed(0);
        }
        super.setVisible(visible);
        canvas.setVisible(visible);
    }

    @Override
    public void new_global_param(String mod) {
        //		int k = -1;
        //
        //		for (int i = 0; i < optPan.acceptg.length; i++) {
        //			if (mod.equals(optPan.acceptg[i])) {
        //				k = i;
        //				break;
        //			}
        //		}
        //		if (k < 0)
        //			return;
        //
        //
        //		synchronized(syncGparamCombobox) {
        //			int f = 0;
        //			for (int i = 0; i < optPan.gparam.getItemCount(); i++) {
        //				String pa2 = (String) optPan.gparam.getItemAt(i);
        //				if (pa2.equals(optPan.MenuAccept[k]))
        //					f = 1;
        //			}
        //
        //			if (f == 0) {
        //				logger.log(Level.FINE, "Adding in GlobePan gparam combobox: "+ optPan.MenuAccept[k]);
        //				optPan.gparam.addItem(optPan.MenuAccept[k]);
        //				optPan.gparam.repaint();
        //			}
        //		}
    }

    public static void main(String[] args) {
        rcNode cern = new rcNode();
        cern.UnitName = "CERN";
        cern.CITY = "Geneva";
        cern.LAT = "46.22";
        cern.LONG = "6.15";
        cern.wconn = new Hashtable();

        rcNode caltech = new rcNode();
        caltech.UnitName = "Caltech";
        caltech.CITY = "Pasadena";
        caltech.LAT = "34.1503";
        caltech.LONG = "-118.139";
        caltech.wconn = new Hashtable();

        ILink cern_caltech = new ILink("Caltech");
        cern_caltech.fromLAT = 46.22;
        cern_caltech.fromLONG = 6.15;
        cern_caltech.toLAT = 35.1503;
        cern_caltech.toLONG = -118.139;
        cern_caltech.peersQuality = new double[] { 0.5 };
        cern_caltech.inetQuality = new double[] { 1.5 };
        cern.wconn.put(caltech.UnitName, cern_caltech);

        rcNode ufl = new rcNode();
        ufl.UnitName = "University of Florida";
        ufl.CITY = "Gainesville";
        ufl.LAT = "29.64";
        ufl.LONG = "-82.35";
        ufl.wconn = new Hashtable();

        rcNode upb = new rcNode();
        upb.UnitName = "Universitatea Politehnica din Bucuresti";
        upb.CITY = "Bucharest";
        upb.LAT = "44.26";
        upb.LONG = "26.03";
        upb.wconn = new Hashtable();

        Vector vnodes = new Vector();
        vnodes.add(cern);
        vnodes.add(caltech);
        vnodes.add(ufl);
        vnodes.add(upb);

        Hashtable hnodes = new Hashtable();
        hnodes.put(cern.UnitName, cern);
        hnodes.put(caltech.UnitName, caltech);
        hnodes.put(ufl.UnitName, ufl);
        hnodes.put(upb.UnitName, upb);

        GlobePanBase globe = new GlobePanBase();
        globe.setNodes(hnodes, vnodes);

        JFrame frame = new JFrame("GlobePanel3D test");
        frame.getContentPane().add(globe);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
