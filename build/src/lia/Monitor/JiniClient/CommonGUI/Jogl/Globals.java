package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.swing.ImageIcon;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.CapitalsGISInfo;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.NetResourceClassLoader;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/*
 * Created on 03.05.2004 23:38:35
 * Filename: Globals.java
 *
 */
/**
 * @author Luc
 *
 * Globals
 */
public class Globals {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Globals.class.getName());

    /**
     * initialize some more variables that can't be only set a value
     */
    public Globals() {

        //get number of threads
        int nThreads = 3;
        try {
            nThreads = Integer.parseInt(AppConfig.getProperty("jogl.Texture.nDownloadThreads", "3"));
        } catch (Exception ex) {
            nThreads = 3;
        }
        logger.fine("Number of download threads: " + nThreads);
        tpOctDownload = Executors.newFixedThreadPool(nThreads);

    }

    //thread pool for downloading
    public ExecutorService tpOctDownload;

    //class loader for downloadable octs
    public NetResourceClassLoader myMapsClassLoader;

    /**
     * only when invalid GL, or changing GL, this should be false
     */
    //public static boolean bValidGL = true;

    //	 Perspective & Window defines
    public final static float FOV_ANGLE = 45.0f;
    public final static float NEAR_CLIP = 0.01f;
    public final static float FAR_CLIP = 2000.0f;

    //window aspect
    public float fAspect = 1;
    public int width, height;//window in frame width and height ( canvas system dimension and ratio )

    /** eye position, situated for 0,0 in center of map */
    public VectorO EyePosition = new VectorO(0f, 0f, 32f);
    /** eye direction - always normalized */
    public VectorO EyeDirection = new VectorO(0f, 0f, -1f);//eye direction - always normalized
    /** eye normal - always normalized */
    public VectorO EyeNormal = new VectorO(0f, 1f, 0f);//eye normal - always normalized

    public int CHANGE_PROJECTION_TIME = 40;//0;//time till next change in radius
    public int GO_TO_NODE_TIME = 100;//time to iterate the 10 steps to get over the node

    //used to set shadow on map
    //should be changed at each 5 minutes, and then all textures reloaded...
    Date currentTime = new Date(NTPDate.currentTimeMillis());

    public static boolean bUpdateShadow = true;//boolean variable to force updating of shadow for earth map; if it is false, no update timer will be created
    private static Object objSync_US = new Object();//sync object for update timer
    private static TimerTask ttUpdateShadow = null;//indicates that there is already an update timer running, so another one musnt be started

    /** sync object for eye vectors update and sky vectors */
    private static Object objSync_UpdateEyeVectors = new Object();

    public static void setUpdateShadow(boolean updateShadow) {
        bUpdateShadow = updateShadow;
        if (!updateShadow) {
            stopUpdateShadowTimer();
        } else {
            doUpdateShadowTimer();
        }
    }

    /**
     * starts an timer for shadow update if this is allowed from menu options and no other is already running<br>
     * The time period for update is set according to current startup texture level.
     *
     */
    public static void doUpdateShadowTimer() {
        if (!bUpdateShadow) {
            logger.warning("Creating new update shadow timer... Not allowed from menu.");
            return;
        }
        ;
        synchronized (objSync_US) {
            if (ttUpdateShadow != null) {
                logger.warning("Creating new update shadow timer... Already another update shadow timer running.");
                return;//already another thread is doing shadow update
            }
            ;
            //this timer will do shadow update

            //depending on nInitialLevel set an update time so that this opperation would not disturb the user
            int nUpdateTime = 5;//for 1024 have 5 minutes, for 8k have one hour, so...
            nUpdateTime += Texture.nInitialLevel * 20;//add 20 minutes for each bigger startup resolution

            logger.fine("Creating new update shadow timer... Set update interval: " + nUpdateTime + " minutes.");
            //start the shadow update thread also
            ttUpdateShadow = new TimerTask() {
                @Override
                public void run() {
                    Thread.currentThread().setName("JoGL Globals - UpdateShadowTimer");
                    /**
                     * if textures are still to be loaded, then wait for a while and try again.
                     * 20 tries before giving up
                     */
                    int nTries = 20;
                    while (JoglPanel.globals.mainPanel.isVisible() && (Texture.texturesToLoad.size() > 0)
                            && (nTries > 0)) {
                        nTries--;
                        try {
                            logger.warning("update shadow thread going to sleep for 5 seconds now.");
                            //wait 5 seconds and then try again
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            //ignore exception and wait some more
                        }
                    }
                    ;
                    if ((nTries > 0) && JoglPanel.globals.mainPanel.isVisible()) {
                        ChangeRootTexture.init(Texture.nInitialLevel, ChangeRootTexture.CRT_KEY_MODE_UPDATE,
                                JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                                JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        //                        ChangeRootTexture crtMonitor = new ChangeRootTexture(Texture.nInitialLevel, ChangeRootTexture.CRT_KEY_MODE_UPDATE);
                        //                        crtMonitor.setProgressBar(JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress, JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        //                        //change global date
                        //                        if ( !crtMonitor.init() )
                        //                            logger.warning("Resolution changing in progress.... Please wait");
                    }
                    ;
                }
            };
            BackgroundWorker.schedule(ttUpdateShadow, nUpdateTime * 60 * 1000, nUpdateTime * 60 * 1000);
        }
    }

    /**
     * sets update shadow timer on cancel mode
     *
     */
    public static void stopUpdateShadowTimer() {
        //        System.out.println("Stopping current update shadow timer...");
        synchronized (objSync_US) {
            if (ttUpdateShadow != null) {
                ttUpdateShadow.cancel();
                ttUpdateShadow = null;//if, by chance, not null, make it now null
                //                System.out.println("Set for canceling.");
            } else {
                ;//System.out.println("Nothing to be stopped.");
            }
        }
    }

    /** function display in DataRenderer starts */
    public long lStartCanvasRepaint = -1;
    /** function display in DataRenderer ends */
    public long lEndCanvasRepaint = -1;
    //display rate
    public long lLastRefreshTime = -1;
    public static final long REFRESH_TIME = 10; //time to redraw gl window
    public static final long RECOMPUTE_NODES_TIME = 4000; //time till next recomputation of rendered nodes

    public Component canvas = null;//the drawing on component
    public JoglPanel mainPanel = null;//the window of application

    long startIdleTime = -1;//time since the map is in the same position from the eye, reference for when the texture will be set to change
    public static final long IDLE_TIME = 10;//the interval to idle before trying to change to a different texture resolution
    boolean bIsIdle = false;//set means that the idle is on, false means that the idle was treated, so the textures are at correct resolutions

    public static final int MAP_WIDTH = 32;
    public static final int MAP_HEIGHT = 16;
    /**
     * sets of variables that establish the current projection mode: sphere or plane.<br>
     * <p>
     * For plane projection, the values are:<br>
     * globeVirtualRadius = 0
     * globeRadius = -1<br>
     * mapAngle = 0<br>
     * bMapTransition2Sphere = true.<br>
     * </p><p>
     * For sphere projection, the values are:<br>
     * mapAngle = 90<br>
     * globeRadius = MAP_WIDTH*45f/(float)Math.PI/mapAngle;
     * globeVirtualRadius = globeRadius
     * bMapTransition2Sphere = false<br>
     */
    //
    public int mapAngle = 90;//0;//angle between the center of map and one extremity on x axis
    /**
     * indicates the next possible transition, if true means that a transition from plane projection
     * to sphere is possible, that means that now is active a plane projection.<br>
     * map becoms a shpere by decreasing the radius and increasing the mapAngle.
     */
    public boolean bMapTransition2Sphere = false;//true;
    /**
     * globe radius when changing from plane projection to sphere projection
     * related to MAP_WIDTH
     * maximal value is 90*MAP_WIDTH/PI -> this value aproximates the infinit to an angle of 1 degree
     * minimal value for radius is: MAP_WIDHT/2/PI
     * a -1 value for the globe radius means that it is used a plane projection
     */
    public float globeRadius = (MAP_WIDTH * 45f) / (float) Math.PI / mapAngle;//-1;
    /**
     * virtual radius of the globe: means distance from nearest point on sphere to virtual center<br>
     * usefull for some computations<br>
     * There is a direct connection between virtual radius and radius.
     */
    public float globeVirtualRadius = globeRadius;//0;

    /** global sphere mode desired level for all visible textures, used for change in zooming */
    public int globalSphereDesiredLevel = Texture.nInitialLevel;

    public static final int BUTTON_SELECT = 1;
    public static final int BUTTON_ROTATE = 2;
    public static final int BUTTON_TRANSLATE = 3;
    public static final int BUTTON_ZOOM = 4;
    public static final int BUTTON_RADIO_ROTATE = 8;
    public static final int BUTTON_RADIO_ZOOM = 16;
    public int nButtonType = /*BUTTON_SELECT*/BUTTON_RADIO_ZOOM;//left button action type set when clicking on a button in second toolbar

    /** scale factor for nodes on map, updated by scale slider in second toolbar and used for nodes radius */
    public int nScaleFactor = 50;//scale factor for nodes on map, updated by scale slider in second toolbar and used for nodes radius

    //max depth the center of the map can be from the eye
    public final static float MAX_DEPTH = (MAP_WIDTH * .5f) / (float) Math.tan((Globals.FOV_ANGLE * Math.PI) / 360.0f);
    //minimal depth the center of the map can be from the eye
    public final static float MIN_DEPTH = 0.02f; //usefull for displaying text in left free space

    public char charPressed = '#'; //key pressed
    public boolean bShowChartesianSystem = false;//left-bottom corner shows a chartesian system: yes/no

    //number of divisions for sphere map on x and y
    public final static int nrDivisionsX = 64;
    public final static int nrDivisionsY = 32;
    //properties for a division of the map
    public final static float divisionWidth = (float) MAP_WIDTH / nrDivisionsX;
    public final static float divisionHeight = (float) MAP_HEIGHT / nrDivisionsY;
    //points that reprezents the map projection on a sphere
    public static final float[][][] points = new float[3][nrDivisionsY + 1][nrDivisionsX + 1];

    /** gis information for most important cities in the world */
    public CapitalsGISInfo wcityGIS = null;
    /** gl id for all cities for plane projection */
    public int wcityGisID0 = -1;
    /** gl id for all cities for sphere projection */
    public int wcityGisID90 = -1;
    /** gis information for most important cities in the US */
    public CapitalsGISInfo uscityGIS = null;
    /** gl id for all us cities for plane projection */
    public int uscityGisID0 = -1;
    /** gl id for all us cities for sphere projection */
    public int uscityGisID90 = -1;
    /** gl id for vectorial countries map for plane projection */
    public int countriesBordersID0 = -1;
    /** gl id for vectorial countries map for sphere projection */
    public int countriesBordersID90 = -1;
    /** gl id for vectorial US states map for plane projection */
    public int usStatesBordersID0 = -1;
    /** gl id for vectorial US state map for sphere projection */
    public int usStatesBordersID90 = -1;

    /** the id of the tree of textures, to diferentiate between same path, but different textures */
    public int nTreeID = 0;
    /** root of textures tree */
    public Texture root;

    //moon parameters
    public float moon_radius = .2f;
    public VectorO vMoonPosition;
    public VectorO vMoonRotationAxis;
    public float moon_speed = 0f;
    public String sMoonTexture = "lia/images/moon_texture.jpg";
    public String sCloudsTexture = "lia/images/clouds_texture.png";
    public int moon_tid = 0;//moon texture id
    public int moon_opengl_id = 0;//moon opengl object's id
    public float moon_cycle = 29.5f;//numbers of rotation of earth around itself for a full moon rotation round earth
    public static boolean bHideMoon = false;//show or hide moon
    static {
        String val = AppConfig.getProperty("jogl.hide_moon", "false");
        if ((val != null) && (val.equals("1") || val.toLowerCase().equals("true"))) {
            Globals.bHideMoon = true;
        }
        ;
    }
    public int clouds_tid = 0;//moon texture id
    public int clouds_opengl_id = 0;//moon opengl object's id
    public float fCloudsRotationAngle = 0f;
    /** antialised links */
    public static boolean bNiceLinks = true;
    static {
        String val = AppConfig.getProperty("jogl.nice_links", "true");
        if ((val != null) && (val.equals("0") || val.toLowerCase().equals("false"))) {
            Globals.bNiceLinks = false;
        }
        ;
    }
    /** show clouds on mouse or kyeboard idle and panel visible */
    public static boolean bShowClouds = true;
    static {
        String val = AppConfig.getProperty("jogl.show_clouds", "true");
        if ((val != null) && (val.equals("0") || val.toLowerCase().equals("false"))) {
            Globals.bShowClouds = false;
        }
        ;
    }

    //used to synchromize access to points array
    public Object syncGrid = new Object();

    //sky texture id
    public int sky_texture_id = 0;
    //sky corners, depending on eye position and direction
    public VectorO VskyLT = new VectorO(0, 0, 0);
    public VectorO VskyLB = new VectorO(0, 0, 0);
    public VectorO VskyRT = new VectorO(0, 0, 0);
    public VectorO VskyRB = new VectorO(0, 0, 0);
    //no texture id
    public int no_texture_id = 0;

    /**
     * loads a image and returns a reference to the loaded object
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = Globals.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);//imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
                private static final long serialVersionUID = 1L;
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            //System.out.println("\nFailed loading image from "+imageFileName);
            //e.printStackTrace();
        }
        return res;
    }

    /**
     * loads an image with transparency and returns a reference to the loaded object
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadBuffImageAlpha(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ClassLoader myClassLoader = Globals.class.getClassLoader();
            URL imageURL;
            if (imageFileName.indexOf("://") >= 0) {
                imageURL = new URL(imageFileName);
            } else {
                imageURL = myClassLoader.getResource(imageFileName);
            }
            ImageIcon icon = new ImageIcon(imageURL);//imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
                private static final long serialVersionUID = 1L;
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            //System.out.println("\nFailed loading image from "+imageFileName);
            //e.printStackTrace();
        }
        return res;
    }

    public static void computeVirtualSphereCenterZ() {
        if (JoglPanel.globals.globeRadius != -1f) {
            float Rmin = Globals.MAP_WIDTH / 2f / (float) Math.PI;
            JoglPanel.globals.globeVirtualRadius = (Rmin * JoglPanel.globals.mapAngle) / 90f;
        } else {
            JoglPanel.globals.globeVirtualRadius = 0f;
        }
    }

    /**
     * computes the 3D coordinates of a point that is on the grid at (x,y) position
     * @param x position on grid on x axis
     * @param y position on grid on y axis
     * @return an float array with 3 coordinates for 3D
     */
    public static float[] point2Dto3D(int x, int y, float[] coord) {
        if (coord == null) {
            coord = new float[3];
        }
        float px, py, pz;
        float aux;
        px = (-(float) MAP_WIDTH / 2f) + (divisionWidth * x);
        py = (MAP_HEIGHT / 2f) - (divisionHeight * y);
        pz = JoglPanel.globals.globeVirtualRadius;
        coord[0] = px;
        coord[1] = py;
        coord[2] = pz;
        if (JoglPanel.globals.globeRadius != -1) {
            aux = py / JoglPanel.globals.globeRadius;
            coord[1] = JoglPanel.globals.globeRadius * (float) Math.sin(aux);
            aux = JoglPanel.globals.globeRadius * (float) Math.cos(aux);
            coord[0] = aux * (float) Math.sin(px / JoglPanel.globals.globeRadius);
            coord[2] = (pz - JoglPanel.globals.globeRadius)
                    + (aux * (float) Math.cos(px / JoglPanel.globals.globeRadius));
        }
        ;
        return coord;
    }

    /**
     * computes the 3D coordinates of a point that is on the globe map at (lat,long) position,<br>
     * where lat is in [-90,90] and long in [-180;180]
     * @param lat position on plane map on y axis
     * @param long position on plane map on x axis
     * @return an float array with 3 coordinates for 3D
     */
    public static float[] point2Dto3D(float latitude, float longitude, float[] coord) {
        //		if ( coord == null )
        //			coord = new float[3];
        //		float px, py, pz;
        //		float aux;
        //		//correct latitude if outside the interval
        //		if ( latitude > 90f )
        //			latitude = 89.90f;
        //		else if ( latitude < -90f )
        //			latitude = -89.90f;
        //		//correct longitude if outside the interval
        //		if ( longitude > 180f )
        //			longitude = 179.90f;
        //		else if ( longitude < -180f )
        //			longitude = -179.90f;
        //		py = latitude/180f*MAP_HEIGHT;
        //		px = longitude/360f*(float)MAP_WIDTH;
        //		pz = JoglPanel.globals.globeVirtualRadius;
        //		coord[0] = px;
        //		coord[1] = py;
        //		coord[2] = pz;
        //		if ( JoglPanel.globals.globeRadius != -1 ) {
        //		    //version 1.
        ////			aux = py/JoglPanel.globals.globeRadius;
        ////			coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( aux);
        ////			aux = JoglPanel.globals.globeRadius*(float)Math.cos( aux);
        ////			coord[0] = aux*(float)Math.sin( px/JoglPanel.globals.globeRadius);
        ////			coord[2] = pz - JoglPanel.globals.globeRadius + aux*(float)Math.cos( px/JoglPanel.globals.globeRadius);
        //			//version 2. (only for complete sphere projection)
        ////		    coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( latitude/180*Math.PI);
        ////		    coord[0] = JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.sin( longitude/180*Math.PI);
        ////		    coord[2] = JoglPanel.globals.globeVirtualRadius - JoglPanel.globals.globeRadius + JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.cos( longitude/180*Math.PI);
        //			//version 3. equivalent to v1.
        //			float alpha_lat, alpha_long, r;
        //			r = JoglPanel.globals.globeRadius;
        //			alpha_lat = py/r;
        //			alpha_long = px/r;
        //			coord[1] = (float)(r*Math.sin(alpha_lat));
        //			coord[0] = (float)(r*Math.cos(alpha_lat)*Math.sin(alpha_long));
        //			coord[2] = pz - r + (float)(r*Math.cos(alpha_lat)*Math.cos(alpha_long));
        //		};
        //		return coord;
        return point2Dto3D(latitude, longitude, coord, 0);
    }

    public static float[] point2Dto3D(float latitude, float longitude, float[] coord, float increment) {
        if (coord == null) {
            coord = new float[3];
        }
        float px, py, pz;
        //        float aux;
        //correct latitude if outside the interval
        if (latitude > 90f) {
            latitude = 89.90f;
        } else if (latitude < -90f) {
            latitude = -89.90f;
        }
        //correct longitude if outside the interval
        if (longitude > 180f) {
            longitude = 179.90f;
        } else if (longitude < -180f) {
            longitude = -179.90f;
        }
        py = (latitude / 180f) * MAP_HEIGHT;
        px = (longitude / 360f) * MAP_WIDTH;
        pz = JoglPanel.globals.globeVirtualRadius;
        coord[0] = px;
        coord[1] = py;
        coord[2] = pz + increment;
        if (JoglPanel.globals.globeRadius != -1) {
            //version 1.
            //          aux = py/JoglPanel.globals.globeRadius;
            //          coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( aux);
            //          aux = JoglPanel.globals.globeRadius*(float)Math.cos( aux);
            //          coord[0] = aux*(float)Math.sin( px/JoglPanel.globals.globeRadius);
            //          coord[2] = pz - JoglPanel.globals.globeRadius + aux*(float)Math.cos( px/JoglPanel.globals.globeRadius);
            //version 2. (only for complete sphere projection)
            //          coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( latitude/180*Math.PI);
            //          coord[0] = JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.sin( longitude/180*Math.PI);
            //          coord[2] = JoglPanel.globals.globeVirtualRadius - JoglPanel.globals.globeRadius + JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.cos( longitude/180*Math.PI);
            //version 3. equivalent to v1.
            float alpha_lat, alpha_long, r;
            r = JoglPanel.globals.globeRadius + increment;
            alpha_lat = py / r;
            alpha_long = px / r;
            coord[1] = (float) (r * Math.sin(alpha_lat));
            coord[0] = (float) (r * Math.cos(alpha_lat) * Math.sin(alpha_long));
            coord[2] = (pz - r) + (float) (r * Math.cos(alpha_lat) * Math.cos(alpha_long));
        }
        ;
        return coord;
    }

    /**
     * 2D point to 3D point for plane projection
     * @param latitude
     * @param longitude
     * @param coord reuse this vector if not null
     * @param increment
     * @return an array of three float values that represent the (long,lat) point in plane projection
     */
    public static float[] point2Dto3D0(float latitude, float longitude, float[] coord, float increment) {
        if (coord == null) {
            coord = new float[3];
        }
        float px, py, pz;
        //        float aux;
        //correct latitude if outside the interval
        if (latitude > 90f) {
            latitude = 89.90f;
        } else if (latitude < -90f) {
            latitude = -89.90f;
        }
        //correct longitude if outside the interval
        if (longitude > 180f) {
            longitude = 179.90f;
        } else if (longitude < -180f) {
            longitude = -179.90f;
        }
        py = (latitude / 180f) * MAP_HEIGHT;
        px = (longitude / 360f) * MAP_WIDTH;
        pz = increment;
        coord[0] = px;
        coord[1] = py;
        coord[2] = pz;
        return coord;
    }

    /**
     * 2D point to 3D point for sphere projection
     * @param latitude
     * @param longitude
     * @param coord reuse this vector if not null
     * @param increment
     * @return an array of three float values that represent the (long,lat) point in sphere projection
     */
    public static float[] point2Dto3D90(float latitude, float longitude, float[] coord, float increment) {
        if (coord == null) {
            coord = new float[3];
        }
        float px, py, pz;
        //        float aux;
        //correct latitude if outside the interval
        if (latitude > 90f) {
            latitude = 89.90f;
        } else if (latitude < -90f) {
            latitude = -89.90f;
        }
        //correct longitude if outside the interval
        if (longitude > 180f) {
            longitude = 179.90f;
        } else if (longitude < -180f) {
            longitude = -179.90f;
        }
        float full_radius = (MAP_WIDTH * .5f) / (float) Math.PI;
        py = (latitude / 180f) * MAP_HEIGHT;
        px = (longitude / 360f) * MAP_WIDTH;
        pz = full_radius + increment;
        float alpha_lat, alpha_long, r;
        r = full_radius + increment;
        alpha_lat = py / r;
        alpha_long = px / r;
        coord[1] = (float) (r * Math.sin(alpha_lat));
        coord[0] = (float) (r * Math.cos(alpha_lat) * Math.sin(alpha_long));
        coord[2] = (pz - r) + (float) (r * Math.cos(alpha_lat) * Math.cos(alpha_long));
        return coord;
    }

    /**
     * returns a vector with longitude on position 0 and latitude on position 1, to equalise
     * with x and y axis<br>
     * Point must be situated on globe, that means: x^2+y^2+z^2=globeRadius^2.
     * @param x
     * @param y
     * @param z
     * @param coord if initialized, it fills this vector
     * @return a new vector or updatede coord vector
     */
    public static float[] point3Dto2D(float x, float y, float z, float coord[]) {
        if (coord == null) {
            coord = new float[2];
        }
        if (JoglPanel.globals.globeRadius == -1) {
            coord[0] = (x * 360) / MAP_WIDTH;
            coord[1] = (y * 180f) / MAP_HEIGHT;
        } else {
            //version 1.
            //		    coord[0] = (float)(360*JoglPanel.globals.globeRadius/(float)MAP_WIDTH*Math.asin(x/JoglPanel.globals.globeRadius/Math.cos(y/JoglPanel.globals.globeRadius)));
            //		    coord[1] = (float)(180*JoglPanel.globals.globeRadius/(float)MAP_HEIGHT*Math.asin(y/JoglPanel.globals.globeRadius));
            //version 2.
            //		    float r = JoglPanel.globals.globeRadius;
            //		    coord[1] = (float)(180/Math.PI*Math.asin(y/r));
            //		    coord[0] = (float)(180/Math.PI*Math.asin(x/Math.sqrt(r*r-y*y)));
            //version 3.
            float r = JoglPanel.globals.globeRadius;
            coord[1] = (float) (((180 * r) / MAP_HEIGHT) * Math.asin(y / r));
            //coord[0] = (float)(180*r/MAP_HEIGHT*Math.atan(x/z));
            if (x > 0) {
                if (z > 0) {
                    coord[0] = (float) (((180 * r) / MAP_HEIGHT) * Math.atan(x / z));
                } else if (z == 0) {
                    coord[0] = 90;
                } else {
                    coord[0] = (float) (180 + (((180 * r) / MAP_HEIGHT) * Math.atan(x / z)));
                }
            } else if (x == 0) {
                if (z >= 0) {
                    coord[0] = 0;
                } else {
                    coord[0] = 180;
                }
            } else if (z > 0) {
                coord[0] = (float) (((180 * r) / MAP_HEIGHT) * Math.atan(x / z));
            } else if (z == 0) {
                coord[0] = -90;
            } else {
                coord[0] = (float) (-180 + (((180 * r) / MAP_HEIGHT) * Math.atan(x / z)));
            }
        }
        return coord;
    }

    /**
     * computes geografical coordinates raported to a specific axis,
     * and two aditional random chosen axis in the plane perpendicular on first
     * axis.<br>
     * Latitude is measured on axis, while longitude is computed on the
     * circle inside the plane.
     * uses global defined vAxis axis to which to report latitude and longitude, much like the
     * rotation axis of the earth
     * @param vP chartezian location of point on the sphere
     * @param coord if not null, it will be used to store geographical coordinates
     * and it will be returned
     * @return coords as (long,lat)
     */
    /*    public static float[] point3Dto2Daxis( float x, float y, float z, float coord[])
        {
            if ( coord == null || coord.length<2 )
                coord = new float[2];
            float r = JoglPanel.globals.globeRadius;
            if ( r==-1 ) {
                coord[0] = x*360/(float)MAP_WIDTH;
                coord[1] = y*180f/(float)MAP_HEIGHT;
            } else {
                VectorO vP = new VectorO(x, y, z);
                vP.Normalize();
                //compute scalar product between vAxis and vP to find out the angle
                //between them.
                //there is no need to divide the value to the module of each vector,
                //because both are presumed to be normalized (module is 1).
                double cu = (float)JoglPanel.globals.vEyeAxis.DotProduct(vP);
                double u = Math.acos(cu);
    //            System.out.println("u=arccos(cu)="+u);
                coord[1] = (float)(180*r/MAP_HEIGHT*(Math.PI*.5-u));
                VectorO vAux;
                //find component of vector vP in plane perpendicular on vAxis
                vAux = new VectorO(JoglPanel.globals.vEyeAxis);
                vAux.MultiplyScalar(-cu);
                vAux.AddVector(vP);
                vAux.Normalize();
                //compute projection of vector on vEyeAxisPP to find out direction
                double pr = JoglPanel.globals.vEyeAxisPP.DotProduct(vAux);
                //compute longitude
                //as value of arccos of projection of vAux on vEyeAxisP
                coord[0] = (float)(360*r/MAP_WIDTH*Math.acos(JoglPanel.globals.vEyeAxisP.DotProduct(vAux)));
                //if projection is negative, then the angle should also be negative
                if ( pr<0 )
                    coord[0] = -coord[0];
            }
            return coord;
        }
     */
    /**
     * recomputes the grid of points based on the globe radius<br>
     * It first considers the plane coordinates that it then transforms to 3d coordinates on a sphere
     * of globeRadius radius/<br>
     * If radius == -1 then the coordinates should remain plane so no trasformations is applied.
     */
    public void computeGrid(Texture root) {
        /*        synchronized( Texture.syncObject_Texture ) {
                    Texture.bInComputeFunction=true;
                    if ( Texture.bInDrawFunction ) {
                        try {
                            Texture.syncObject_Texture.wait();
                        } catch (InterruptedException e) {
                        }
                    };
                }
         */
        //		float px, py, pz;
        //float step_x, step_y;
        //		float aux;
        computeVirtualSphereCenterZ();
        //step_x = MAP_WIDTH/nrDivisionsX;
        //step_y = MAP_HEIGHT/nrDivisionsY;
        //for each point on the grid
        int x, y;
        float[] coord = new float[3];
        synchronized (Texture.syncObject_Texture) {
            synchronized (syncGrid) {
                for (y = 0; y <= nrDivisionsY; y++) {
                    for (x = 0; x <= nrDivisionsX; x++) {
                        //compute the plane coordinates x in (-Width/2;Width/2), y in (Height/2;-Height/2), and z=0
                        point2Dto3D(x, y, coord);
                        points[0][y][x] = coord[0];
                        points[1][y][x] = coord[1];
                        points[2][y][x] = coord[2];
                    }
                }
            }
            ;
            //System.out.println("p(x,y,z)=("+points[y][x][0]+","+points[y][x][1]+","+points[y][x][2]+")");
            //System.out.println("points computed");
            //todo: also recompute all dynamic grids in tree
            root.computeDynGrid();
        }
        /*synchronized( Texture.syncObject_Texture ) {
            Texture.bInComputeFunction=false;
            Texture.syncObject_Texture.notifyAll();
        };*/
    }

    /**
     * checks new possible values for eye vectors to conform with some constraints:<br>
     * - if plane projection, eyePositionZ must be always greater than 0 and eyeDirectionZ must be always negative<br>
     * - if sphere, radius of vector formed from real sphere center to eyePosition must be greater than sphere's real radius<br>
     * - for any projection, there must be a small MAX_DEPTH space between eyePosition and map, and eyePosition doesn't resolve it completelly<br>
     * - for any projection, limit eyeDirection inside the map, that means getGeographicalPointOnMap(width/2,height/2)!=null<br>
     * @param newEyePosition
     * @param newEyeDirection
     * @param newEyeNormal
     */
    public static void falisafeUpdateEyeVectors(VectorO newEyePosition, VectorO newEyeDirection, VectorO newEyeNormal) {
        float globeRadius = JoglPanel.globals.globeRadius;
        if (globeRadius == -1) { //plane projection
            //System.out.println("EyePositionZ:"+newEyePosition.getZ());
            if (newEyePosition != null) {
                VectorO vCheck;
                if (newEyeDirection != null) {
                    vCheck = new VectorO(newEyeDirection);
                } else {
                    vCheck = new VectorO(JoglPanel.globals.EyeDirection);
                }
                vCheck.MultiplyScalar(Globals.MIN_DEPTH + Globals.NEAR_CLIP);
                vCheck.AddVector(newEyePosition);
                if (vCheck.getZ() <= 0) {
                    return;//not valid position for z
                }
            }
            if ((newEyeDirection != null) && (newEyeDirection.getZ() >= 0)) {
                return;
            }
        }
        ;
        //update variables and do a refresh
        VectorO vSky[] = JoglPanel.globals.correctSkyPosition(newEyePosition, newEyeDirection, newEyeNormal);
        //set the new values (they are correct if this code is reached)
        synchronized (objSync_UpdateEyeVectors) {
            if (newEyePosition != null) {
                JoglPanel.globals.EyePosition.duplicate(newEyePosition);
            }
            if (newEyeDirection != null) {
                JoglPanel.globals.EyeDirection.duplicate(newEyeDirection);
            }
            if (newEyeNormal != null) {
                JoglPanel.globals.EyeNormal.duplicate(newEyeNormal);
            }
            JoglPanel.globals.VskyLT = vSky[0];
            JoglPanel.globals.VskyLB = vSky[1];
            JoglPanel.globals.VskyRT = vSky[2];
            JoglPanel.globals.VskyRB = vSky[3];
        }
        //reset start idle time
        JoglPanel.globals.resetIdleTime();
    }

    /**
     * as the name suggests, has the role to recompute sky corners
     * to make the view frustum base at 2*max_depth distance from the eye<br>
     * the map is always above the sky
     */
    public VectorO[] correctSkyPosition(VectorO newEyePosition, VectorO newEyeDirection, VectorO newEyeNormal) {
        VectorO vSky[] = new VectorO[4];
        VectorO vAux;
        if (newEyePosition == null) {
            newEyePosition = JoglPanel.globals.EyePosition;
        }
        if (newEyeDirection == null) {
            newEyeDirection = JoglPanel.globals.EyeDirection;
        }
        if (newEyeNormal == null) {
            newEyeNormal = JoglPanel.globals.EyeNormal;
        }
        VectorO Vm = newEyeDirection.CrossProduct(newEyeNormal);
        float local_MAX_DEPTH = 4f * Globals.MAX_DEPTH;//the distance will be 4 time the maximal depth
        float angle_m, angle_n;
        float tan = (float) Math.tan(((Globals.FOV_ANGLE / 2f) * Math.PI) / 180.0f);
        float fx = 2f * local_MAX_DEPTH * tan;
        float rapXY = JoglPanel.globals.fAspect;
        float fy = fx / rapXY;
        float d = (float) Math.sqrt((fx * fx) + (fy * fy) + (4f * local_MAX_DEPTH * local_MAX_DEPTH));
        /**
         * the sky is draw at double maximal distance the eye can reach = 32
         * it is always facing the eye, so is the base of the view frustum
         *
         */
        //left top corner
        vAux = new VectorO(newEyeDirection);
        int nWidth = JoglPanel.globals.width;
        angle_m = (float) Math.atan((((2f * 0) - nWidth) / nWidth) * tan);
        int nHeight = JoglPanel.globals.height;
        angle_n = (float) Math.atan(((((2f * 0) - nHeight) / nHeight) * tan) / rapXY);
        vAux.Rotate(newEyeNormal, (-angle_m * 180f) / (float) Math.PI);
        vAux.Rotate(Vm, (-angle_n * 180f) / (float) Math.PI);
        vAux.MultiplyScalar(d);
        vAux.AddVector(newEyePosition);
        vSky[0] = vAux; //JoglPanel.globals.VskyLT
        //i found the first corner

        //left bottom corner
        vAux = new VectorO(newEyeDirection);
        angle_m = (float) Math.atan((((2f * 0) - nWidth) / nWidth) * tan);
        angle_n = (float) Math.atan(((((2f * nHeight) - nHeight) / nHeight) * tan) / rapXY);
        vAux.Rotate(newEyeNormal, (-angle_m * 180f) / (float) Math.PI);
        vAux.Rotate(Vm, (-angle_n * 180f) / (float) Math.PI);
        vAux.MultiplyScalar(d);
        vAux.AddVector(newEyePosition);
        vSky[1] = vAux; //JoglPanel.globals.VskyLB
        //i found the second corner

        //right top corner
        vAux = new VectorO(newEyeDirection);
        angle_m = (float) Math.atan((((2f * nWidth) - nWidth) / nWidth) * tan);
        angle_n = (float) Math.atan(((((2f * 0) - nHeight) / nHeight) * tan) / rapXY);
        vAux.Rotate(newEyeNormal, (-angle_m * 180f) / (float) Math.PI);
        vAux.Rotate(Vm, (-angle_n * 180f) / (float) Math.PI);
        vAux.MultiplyScalar(d);
        vAux.AddVector(newEyePosition);
        vSky[2] = vAux; //JoglPanel.globals.VskyRT
        //i found the third corner

        //right bottom corner
        vAux = new VectorO(newEyeDirection);
        angle_m = (float) Math.atan((((2f * nWidth) - nWidth) / nWidth) * tan);
        angle_n = (float) Math.atan(((((2f * nHeight) - nHeight) / nHeight) * tan) / rapXY);
        vAux.Rotate(newEyeNormal, (-angle_m * 180f) / (float) Math.PI);
        vAux.Rotate(Vm, (-angle_n * 180f) / (float) Math.PI);
        vAux.MultiplyScalar(d);
        vAux.AddVector(newEyePosition);
        vSky[3] = vAux; //JoglPanel.globals.VskyRB
        //i found the fourth corner

        return vSky;
    }

    /**
     * restarts idle timer as a change in zoom has been made<br>
     */
    public void resetIdleTime() {
        //reset start idle time
        /*JoglPanel.globals.*/startIdleTime = NTPDate.currentTimeMillis();
        /*JoglPanel.globals.*/bIsIdle = true;
    }

    /**
     * prints statistics about memory
     */
    public static void printMemory() {
        char tc, fc, mc;//chars to represent values
        long tmem = Runtime.getRuntime().totalMemory();
        tc = 'b';
        if (tmem > 1024) {
            tmem /= 1024;
            tc = 'k';
        }
        if (tmem > 1024) {
            tmem /= 1024;
            tc = 'm';
        }
        if (tmem > 1024) {
            tmem /= 1024;
            tc = 'g';
        }
        long fmem = Runtime.getRuntime().freeMemory();
        fc = 'b';
        if (fmem > 1024) {
            fmem /= 1024;
            fc = 'k';
        }
        if (fmem > 1024) {
            fmem /= 1024;
            fc = 'm';
        }
        if (fmem > 1024) {
            fmem /= 1024;
            fc = 'g';
        }
        long mmem = Runtime.getRuntime().maxMemory();
        mc = 'b';
        if (mmem > 1024) {
            mmem /= 1024;
            mc = 'k';
        }
        if (mmem > 1024) {
            mmem /= 1024;
            mc = 'm';
        }
        if (mmem > 1024) {
            mmem /= 1024;
            mc = 'g';
        }
        System.out.println("Total memory: " + tmem + " " + tc + " Free memory: " + fmem + " " + fc
                + " Maximal memory: " + mmem + " " + mc);
    }

    public static double limitValue(double val, double minVal, double maxVal) {
        return val > maxVal ? maxVal : val < minVal ? minVal : val;
    }

    /**
     * rotates the eye from current position to end position with only the fraction mentioned.<br>
     * Unfortunately, this function fails to show a nice rotation because the rotation of
     * direction and normal vectors is not related to the rotation of position vector. Up to this moment,
     * after long thinking I haven't found a way to modify position based on direction, or the other way
     * around. <br>
     *  TODO: change the algorithm
     * @param vEnds triplet of vectors: position, direction, normal
     * @param fraction number from 0 to 1 to indicate how the eye vectors will modify from current
     * position to end position, if fraction is not 0 or 1, an intermediate position will be generated.
     */
    public void rotate(VectorO[] vEnds, double fraction) {
        VectorO vStart = new VectorO(JoglPanel.globals.EyePosition);
        VectorO vStartDir = new VectorO(JoglPanel.globals.EyeDirection);
        VectorO vStartNormal = new VectorO(JoglPanel.globals.EyeNormal);
        VectorO vEnd, vEndDir, vEndNormal;
        vEnd = vEnds[0];//new VectorO(0,0,32);
        vEndDir = vEnds[1];//new VectorO(0,0,-1);
        vEndNormal = vEnds[2];//new VectorO(0,1,0);
        //do translation
        VectorO vPct;
        //	    vPct = VectorO.SubstractVector(vEnd, vStart);
        //	    vPct.MultiplyScalar( fraction);
        //	    vPct.AddVector(vStart);
        VectorO positionStart, positionEnd, positionInter, positionAxis;
        positionStart = new VectorO(vStart);
        positionStart.setZ((positionStart.getZprojection() + JoglPanel.globals.globeRadius)
                - JoglPanel.globals.globeVirtualRadius);
        positionEnd = new VectorO(vEnd);
        positionEnd.setZ((positionEnd.getZprojection() + JoglPanel.globals.globeRadius)
                - JoglPanel.globals.globeVirtualRadius);
        positionAxis = positionStart.CrossProduct(positionEnd);
        double alphaPos;
        alphaPos = (Math.acos(Globals.limitValue(positionStart.DotProduct(positionEnd) / positionStart.getRadius()
                / positionEnd.getRadius(), -1, 1)) * 180)
                / Math.PI;//in degree
        //check to see if positional angle is the correct one:
        VectorO vDupl = new VectorO(positionStart);
        vDupl.RotateDegree(positionAxis, alphaPos);
        //first check to see if angle1 is correctly computed
        if (!vDupl.equals(positionEnd)) {
            alphaPos = -alphaPos;
            vDupl.duplicate(positionStart);
            vDupl.RotateDegree(positionAxis, alphaPos);
            if (!vDupl.equals(positionEnd)) {
                alphaPos = -alphaPos;
            }
        }
        positionInter = new VectorO(positionStart);
        positionInter.Normalize();
        positionInter.RotateDegree(positionAxis, fraction * alphaPos);
        double interRadius;
        interRadius = (positionStart.getRadius() * (1 - fraction)) + (positionEnd.getRadius() * fraction);
        if (interRadius < JoglPanel.globals.globeRadius) {
            interRadius = ((positionEnd.getRadius() - JoglPanel.globals.globeRadius) * fraction)
                    + JoglPanel.globals.globeRadius;
        }
        positionInter.MultiplyScalar(interRadius);
        vPct = new VectorO(positionInter);
        vPct.setZ((vPct.getZprojection() - JoglPanel.globals.globeRadius) + JoglPanel.globals.globeVirtualRadius);
        /**
         * <u>ROTATION</u>
         * 1. compute cross product between dStart and nStart to obtain pStart
         *     compute cross product between dEnd and nEnd to obtain pEnd
         *     compute alpha1 = arccos( p1.p2 ) (dot product)
         * 2. compute cross product between pStart and pEnd to obtain v - rotation axis
         *     compute dStartSecund = dStart.rotate( v, alpha1)
         * 3. compute alpha2 = arccos( dStartSecund.dEnd ) (also dot product)
         * 4. rotate d and n with f.alpha1 around v and
         *     rotate d and n with f.alpha2 around p
         */
        VectorO pStart = vStartDir.CrossProduct(vStartNormal);
        VectorO pEnd = vEndDir.CrossProduct(vEndNormal);
        VectorO vRotationAxis = pStart.CrossProduct(pEnd);
        double alpha1 = (Math.acos(Globals.limitValue(pStart.DotProduct(pEnd), -1, 1)) * 180) / Math.PI;//in degree
        //check to see if angle 1 is the correct one:
        VectorO pStartDupl = new VectorO(pStart);
        pStartDupl.RotateDegree(vRotationAxis, alpha1);
        //first check to see if angle1 is correctly computed
        if (!pStartDupl.equals(pEnd)) {
            //debug testing and also to correct invalid angle1
            //	        System.out.println("[angle1] pEndToBe!=pEnd, alpha1="+alpha1+"\n[angle1] pEndToBe: "+pStartDupl+"\n[angle1] pEnd: "+pEnd);
            //	        System.out.println("[angle1] pEndToBe!=pEnd at start plane rotation to end plane");
            alpha1 = -alpha1;
            //	        System.out.println("[angle1] Changing to -angle...");
            pStartDupl.duplicate(pStart);
            pStartDupl.RotateDegree(vRotationAxis, alpha1);
            //second check to see if angle1 is correctly computed. if not, ERROR!!!
            if (!pStartDupl.equals(pEnd)) {
                //		        System.out.println("[angle1] invalid end also. Wrong equations?");
                //		        System.out.println("[angle1] pEndToBe!=pEnd, alpha1="+alpha1+"\n[angle1] pEndToBe: "+pStartDupl+"\n[angle1] pEnd: "+pEnd);
                //revert to first computed angle
                alpha1 = -alpha1;
            } else {
                ;
                //		        System.out.println("[angle1] successfull done");
            }
        }
        ;
        //suppose pStartDupl is the correct one...? => is equal to pEnd
        VectorO vStartDirSec = new VectorO(vStartDir);
        vStartDirSec.RotateDegree(vRotationAxis, alpha1);
        //check rotated direction start vector to see if in correct plane:
        //VectorO pEndToBe =
        double alpha2 = (Math.acos(Globals.limitValue(vStartDirSec.DotProduct(vEndDir), -1, 1)) * 180) / Math.PI;//in degree
        //check to see if angle 2 is the correct one:
        VectorO vStartDirSecDupl = new VectorO(vStartDirSec);
        //rotate start dir vector around end axis to see if it gets over the end's directional vector
        vStartDirSecDupl.RotateDegree(pEnd, alpha2);
        if (!vStartDirSecDupl.equals(vEndDir)) {
            //	        System.out.println("[angle2] pEndToBe!=pEnd, alpha2="+alpha2+"\n[angle2] pEndDirToBe: "+vStartDirSecDupl+"\n[angle2] pEndDir: "+vEndDir);
            alpha2 = -alpha2;
            //	        System.out.println("[angle2] Changing to -angle...");
            vStartDirSecDupl.duplicate(vStartDirSec);
            vStartDirSecDupl.RotateDegree(pEnd, alpha2);
            //second check to see if angle2 is correctly computed. if not, ERROR!!!
            if (!vStartDirSecDupl.equals(vEndDir)) {
                //		        System.out.println("[angle2] invalid end also. Wrong equations?");
                //		        System.out.println("[angle2] pEndToBe!=pEnd, alpha2="+alpha2+"\n[angle2] pEndDirToBe: "+vStartDirSecDupl+"\n[angle2] pEndDir: "+vEndDir);
                alpha2 = -alpha2;
            } else {
                ;
                //		        System.out.println("[angle2] successfull done");
            }
        }
        VectorO vPctDir = new VectorO(vStartDir);
        VectorO vPctNormal = new VectorO(vStartNormal);
        //first rotate around start plane axis
        vPctDir.RotateDegree(pStart, fraction * alpha2);
        vPctNormal.RotateDegree(pStart, fraction * alpha2);
        //then rotate around global rotation axis
        vPctDir.RotateDegree(vRotationAxis, fraction * alpha1);
        vPctNormal.RotateDegree(vRotationAxis, fraction * alpha1);

        falisafeUpdateEyeVectors(vPct, vPctDir, vPctNormal);
        //recheck visibility
        Texture.checkVisibility();
        //      JoglPanel.globals.startIdleTime = System.currentTimeMillis();
        //      JoglPanel.globals.bIsIdle = true;
        JoglPanel.globals.canvas.repaint();
        //	    JoglPanel.globals.EyePosition.duplicate(vPct);
        //	    JoglPanel.globals.EyeDirection.duplicate(vPctDir);
        //	    JoglPanel.globals.EyeNormal.duplicate(vPctNormal);
        //	    JoglPanel.globals.correctSkyPosition(null, null, null);
    }

    /**
     * check to see if current mouse position is intersecting the sphere centered in vPoint,
     * and with radius r
     * @param mouse_x
     * @param mouse_y
     * @param vPoint
     * @return
     */
    static boolean sphereIntersection(int mouse_x, int mouse_y, VectorO vPoint, float r) {
        //0. startup vectors
        //		float ex, ey, ez;
        //		ex = (float)JoglPanel.globals.EyePosition.getX();
        //		ey = (float)JoglPanel.globals.EyePosition.getY();
        //		ez = (float)JoglPanel.globals.EyePosition.getZ();
        //		float R, Rv;
        //		R = JoglPanel.globals.globeRadius;
        //		Rv = JoglPanel.globals.globeVirtualRadius;
        VectorO Vn = JoglPanel.globals.EyeNormal;
        //1. first line
        VectorO Vd1 = new VectorO(JoglPanel.globals.EyeDirection);
        float angle_m, angle_n;
        float tan = (float) Math.tan(((Globals.FOV_ANGLE / 2f) * Math.PI) / 180.0f);
        float tan_m = (((2f * mouse_x) - JoglPanel.globals.width) / JoglPanel.globals.width) * tan;
        angle_m = (float) Math.atan(tan_m);
        angle_n = (float) Math.atan(((((2f * mouse_y) - JoglPanel.globals.height) / JoglPanel.globals.height) * tan)
                / JoglPanel.globals.fAspect / Math.sqrt(1 + (tan_m * tan_m)));
        Vd1.Rotate(Vn, (-angle_m * 180f) / (float) Math.PI);
        VectorO Vm = Vd1.CrossProduct(Vn);
        Vd1.Rotate(Vm, (-angle_n * 180f) / (float) Math.PI);
        VectorO vDiff = VectorO.SubstractVector(vPoint, JoglPanel.globals.EyePosition);
        //Vd1.Normalize();//should not be neccessary
        float pr = (float) Vd1.DotProduct(vDiff);
        double dd;
        dd = (r * r) - ((vDiff.getRadius() * vDiff.getRadius()) - (pr * pr));
        //System.out.println("dd="+dd);
        if (dd >= 0) {
            return true;
        }
        return false;
    }

    /**
     * checks if arrow starting from vEye, going in vDirection intersects a sphere centered in vPoint
     * and having a radius of r
     * @param vEye starting position for arrow
     * @param vDirection direction of propagation for arrow
     * @param vPoint position of center of sphere
     * @param r shpere's radius
     * @return true if the arrow intersects the sphere
     */
    static boolean sphereIntersection(VectorO vEye, VectorO vDirection, VectorO vPoint, float r) {
        VectorO vDiff = VectorO.SubstractVector(vPoint, vEye);
        float pr = (float) vDirection.DotProduct(vDiff);
        double dd;
        dd = (r * r) - ((vDiff.getRadius() * vDiff.getRadius()) - (pr * pr));
        if (dd >= 0) {
            return true;
        }
        return false;
    }

    /**
     * checks to see if point is on visible side of globe, with radius fGlobeRadius, considering
     * eye situated at vEye<br>
     * <b>ATTENTION:</b> works only for full sphere!!!<br>
     * @param vEye position of eye, considering center of sphere as (0,0,0)
     * @param vPoint point on globe or somewhere in space
     * @param fGlobeRadius globe's radius
     * @return true if point is on visible part of globe, false otherwise
     */
    static boolean isVisible(VectorO vEye, VectorO vPoint, float fGlobeRadius) {
        //check first to see if globe projection
        if (fGlobeRadius > 0) {
            VectorO vDiff = VectorO.SubstractVector(vPoint, vEye);
            float c1 = (float) vDiff.getRadius();
            float ip = (float) vEye.getRadius();
            return ((c1 * c1) <= ((ip * ip) - (fGlobeRadius * fGlobeRadius)));
        }

        return true;
    }

    /**
     * draws a circle for the current transformation matrix in (x,y) plane
     * @param gl
     * @param slices - number of slices, >0
     * @param fRadius - radius of circle
     */
    public static void drawCircle(GL2 gl, int slices, float fRadius) {
        if (slices == 0) {
            return;
        }
        float faDelta = (2 * (float) Math.PI) / slices, faAlfa;
        faAlfa = 0.0f;
        float fX, fY;
        gl.glBegin(GL.GL_LINE_LOOP);
        for (int slice = 0; slice < slices; slice++) {
            fX = fRadius * (float) Math.cos(faAlfa);
            fY = fRadius * (float) Math.sin(faAlfa);
            gl.glVertex2f(fX, fY);
            faAlfa += faDelta;
        }
        gl.glEnd();
    }

    /**
     * draws a circle for the current transformation matrix in (x,y) plane
     * @param gl
     * @param slices - number of slices, >0
     * @param fRadius - radius of circle
     */
    public static void drawHashedHexagon(GL2 gl) {
        //draw hexagon
        int i;
        float sqrt2 = (float) Math.sqrt(2);
        float vx[] = { 1f, sqrt2 - 1f, 1 - sqrt2, -1f, 1 - sqrt2, sqrt2 - 1f };
        float vy[] = { 0f, 2 - sqrt2, 2 - sqrt2, 0, sqrt2 - 2f, sqrt2 - 2f };
        gl.glBegin(GL.GL_LINE_LOOP);
        for (i = 0; i < vx.length; i++) {
            gl.glVertex2f(vx[i], vy[i]);
        }
        gl.glEnd();
        //draw vertical lines
        float dmd = 2f - sqrt2;
        float vxl[] = { (1 + sqrt2) * .33f, ((2 * sqrt2) - 1f) * .33f, ((2 * sqrt2) - 2f) * .33f, 0 };
        float vyl[] = { dmd * .33f, dmd * .66f, dmd, dmd };
        gl.glBegin(GL.GL_LINES);
        for (i = 0; i < vxl.length; i++) {
            gl.glVertex2f(vxl[i], vyl[i]);
            gl.glVertex2f(vxl[i], -vyl[i]);
            if (i < (vxl.length - 1)) {//draw symetrical vertical lines to y axis, skip the middle one
                gl.glVertex2f(-vxl[i], vyl[i]);
                gl.glVertex2f(-vxl[i], -vyl[i]);
            }
        }
        ;
        //draw horizontal lines
        //middle horizontal line
        gl.glVertex2f(1f, 0f);
        gl.glVertex2f(-1f, 0f);
        for (i = 0; i < 2; i++) {
            gl.glVertex2f(vxl[i], vyl[i]);
            gl.glVertex2f(-vxl[i], vyl[i]);
            gl.glVertex2f(vxl[i], -vyl[i]);
            gl.glVertex2f(-vxl[i], -vyl[i]);
        }
        ;
        gl.glEnd();
    }

    /**
     * variables and constants for rotation bar in 3d Map
     */
    public static final int ROTATION_BAR_WIDTH = 250;
    public static int ROTATION_BAR_HEIGHT = 11;//20;
    public static int ROTATION_BAR_BORDER = 1;//2;
    public int nRBHotSpot = -1;//rotation bar hot spot detector: 0 is left, 1 is top, 2 is right, 3 is bottom
    /** percentual position for hot spot: from -100 to 0 to 100 */
    public int nRBHotSpotPos = 0;//
    /** becomes true first time the JoGL is initialized, DataRenderer.init is called */
    public boolean bJoglInitialized = false;
    public static boolean bShowRotationBar = true;
    public static boolean bAutoHideRotationBar = true;//auto hides rotation bar if mouse not around it
    public static boolean bShowIfAutoHide = false;//if set as auto hide, show only if mouse on rotation bar
    public static boolean bShowRotationBarTooltip = false;//show tooltip for rotation bar
    public static boolean bShowCountriesBorders = true;//show countries borders
    /** indentify selected bars */
    public static int nShowRBs = 0;//selected bars

    /** showing of cities is activated */
    public static boolean bShowCities = true;

    public static final NumberFormat nf = NumberFormat.getInstance();
    static {
        nf.setMinimumIntegerDigits(2);
    }

    public static String getUTCHour(String delayUTC) {
        //remove " UTC" part from string
        String sDelay = delayUTC.substring(0, delayUTC.length() - 4);
        //conrvert to number
        float fDelay = Float.parseFloat(sDelay);
        long lDelay = (long) (fDelay * 60 * 60 * 1000);//transform to miliseconds
        long localDate = new Date().getTime();
        TimeZone tz = TimeZone.getDefault();
        long delayFromUTC = tz.getRawOffset();
        Date newDate = new Date((localDate - delayFromUTC) + lDelay);
        Calendar c = Calendar.getInstance();
        c.setTime(newDate);

        return nf.format(c.get(Calendar.HOUR_OF_DAY)) + ":" + nf.format(c.get(Calendar.MINUTE)) + ":"
                + nf.format(c.get(Calendar.SECOND));
        //return newDate;//JoglPanel.globals.currentTime;
    }

    /**
     * tries to deletes the provided directory, and all in it
     * @param dirFile
     * @return true if action is successfull, false otherwise
     */
    public static boolean emptyDirectory(File dirFile) {
        boolean bDeleted = true;
        if (dirFile == null) {
            return true;
        }
        try {
            if (dirFile.isDirectory()) {
                File[] files = dirFile.listFiles();
                boolean bEmpty = true;
                for (int i = 0; i < files.length; i++) {
                    if (!emptyDirectory(files[i])) {
                        bEmpty = false;
                    }
                }
                if (!bEmpty) {
                    return false;
                }
            }
            if (dirFile.exists()) {
                bDeleted = dirFile.delete();
            }
        } catch (Exception ex) {
            bDeleted = false;
        }
        return bDeleted;
    }

    /**
     * if sphere projection, computes intersection parameters:<br>
     * fLongIntersection and fLatIntersection for geografical coordinates of
     * point that is on segment from eye to center of sphere (0,0,0),<br>
     * fRadiusIntersection - the radius of the viewable area from eye position
     * of the sphere shaped earth.<br>
     * It doesn't return anything, it only modifies the static variables.
     */
    //    public static void computeIntersectionParams()
    //    {
    //        if ( JoglPanel.globals.globeRadius==-1 ) {
    //            //plane projection, reset to cartezian system
    //            JoglPanel.globals.vEyeAxis.setXYZ( 0, 1, 0);
    //            JoglPanel.globals.vEyeAxisP.setXYZ( 0, 0, 1);
    //            JoglPanel.globals.vEyeAxisPP.setXYZ( 1, 0, 0);
    //        } else {
    //            /** compute eye to sphere center vector's projection on map in geographical coordinates */
    //            JoglPanel.globals.vEyeAxis.setXYZ( JoglPanel.globals.EyePosition.getX(),
    //                    JoglPanel.globals.EyePosition.getY(),
    //                    JoglPanel.globals.EyePosition.getZ()-JoglPanel.globals.globeRadius+JoglPanel.globals.globeVirtualRadius);
    //            JoglPanel.globals.vEyeAxis.Normalize();
    //            VectorO vB = new VectorO( JoglPanel.globals.vEyeAxis.getX()*2+.3, JoglPanel.globals.vEyeAxis.getY(), JoglPanel.globals.vEyeAxis.getZ());
    //            vB.Normalize();
    //            JoglPanel.globals.vEyeAxisP.duplicate(JoglPanel.globals.vEyeAxis);
    //            JoglPanel.globals.vEyeAxisP.CrossProduct(vB);
    //            JoglPanel.globals.vEyeAxisP.Normalize();
    //            JoglPanel.globals.vEyeAxisPP.duplicate(JoglPanel.globals.vEyeAxis);
    //            JoglPanel.globals.vEyeAxisPP.CrossProduct(JoglPanel.globals.vEyeAxisP);
    //            JoglPanel.globals.vEyeAxisPP.Normalize();
    //        }
    /*        if ( JoglPanel.globals.globeRadius!=-1 && JoglPanel.globals.mapAngle==90 ) {
                float globeR = JoglPanel.globals.globeRadius;
                float d1 = (float)vIntersection.getRadius();//d1 MUST be greater than r
                vIntersection.Normalize();
                vIntersection.MultiplyScalar(globeR);
                float []coords = Globals.point3Dto2D( vIntersection.getX(), vIntersection.getY(), vIntersection.getZ(), null);
                if ( coords!=null ) {
                    JoglPanel.globals.fLongIntersection = coords[0];
                    JoglPanel.globals.fLatIntersection = coords[1];
                    JoglPanel.globals.fLongAltIntersection[0] = JoglPanel.globals.fLongIntersection;
                    JoglPanel.globals.fLatAltIntersection[1] = JoglPanel.globals.fLatIntersection;
                    if ( JoglPanel.globals.fLongIntersection <= 0 ) {
                        JoglPanel.globals.fLongAltIntersection[1] = JoglPanel.globals.fLongIntersection + 360;
                        if ( JoglPanel.globals.fLatIntersection <= 0 )
                            JoglPanel.globals.fLatAltIntersection[0] = JoglPanel.globals.fLatIntersection + 180;
                        else
                            JoglPanel.globals.fLatAltIntersection[0] = JoglPanel.globals.fLatIntersection - 180;
                    } else {
                        JoglPanel.globals.fLongAltIntersection[1] = JoglPanel.globals.fLongIntersection - 360;
                        if ( JoglPanel.globals.fLatIntersection <= 0 )
                            JoglPanel.globals.fLatAltIntersection[0] = JoglPanel.globals.fLatIntersection + 180;
                        else
                            JoglPanel.globals.fLatAltIntersection[0] = JoglPanel.globals.fLatIntersection - 180;
                    }
                    JoglPanel.globals.fLongAltIntersection[2] = JoglPanel.globals.fLongAltIntersection[1];
                    JoglPanel.globals.fLatAltIntersection[2] = JoglPanel.globals.fLatAltIntersection[0];
                    System.out.println("intersection: (long,lat)=("+coords[0]+","+coords[1]+")");
                    for( int i=0; i<3; i++ )
                        System.out.println("intersection alternate "+i+": (long,lat)=("+JoglPanel.globals.fLongAltIntersection[i]+","+JoglPanel.globals.fLatAltIntersection[i]+")");
                } else
                    logger.log(Level.WARNING, "Invalid intersection between eye and sphere?!!! Vector is not on globe?");
     *//** compute radius in degrees */
    /*
    if ( d1>=globeR )
    JoglPanel.globals.fRadiusIntersection = (float)Math.acos(globeR/d1)*180f/(float)Math.PI;
    else
    logger.log(Level.WARNING, "Invalid intersection area between eye and sphere?!!! Eye inside the sphere or not sphere?");
    System.out.println("intersection radius (in degrees): "+JoglPanel.globals.fRadiusIntersection);
    };
     */
    //    }

    /**
     * check any intersection between circle and rectangle
     * @param cx
     * @param cy
     * @param r
     * @param r1x
     * @param r1y
     * @param r2x
     * @param r2y
     * @return true if intersection, circle inside rectangle or rectangle inside circle
     */
    /*    public static boolean checkIntersectionCircleRectangle( float cx, float cy, float r, float r1x, float r1y, float r2x, float r2y)
        {
            if ( containCircleRectangle( cx, cy, r, r1x, r1y, r2x, r2y) )
                return true;
            if ( intersectCircleRectangle( cx, cy, r, r1x, r1y, r2x, r2y) )
                return true;
            return false;
        }
     */
    /**
     * checks to see if circle inside rectangle or other way around
     * @param cx
     * @param cy
     * @param r
     * @param r1x
     * @param r1y
     * @param r2x
     * @param r2y
     * @return true if true, false otherwise
     */
    /*    public static boolean containCircleRectangle( float cx, float cy, float r, float r1x, float r1y, float r2x, float r2y)
        {
            //check first if center inside the rectangle
            float minx = r1x;
            float maxx = r2x;
            if ( r2x < minx ) {
                minx = r2x;
                maxx = r1x;
            };
            float miny = r1y;
            float maxy = r2y;
            if ( r2y < miny ) {
                miny = r2y;
                maxy = r1y;
            };
            if ( cx>=minx && cx<=maxx && cy>=miny && cy<=maxy )
                return true;//circle inside rectangle
            //next, for rectangle inside circle
            //if at least one point is inside, then return true
            float r2 = r*r;
            float aux;
            aux = (r1x-cx)*(r1x-cx)+(r1y-cy)*(r1y-cy) - r2;
            if ( aux<=0 )
                return true;
            aux = (r1x-cx)*(r1x-cx)+(r2y-cy)*(r2y-cy) - r2;
            if ( aux<=0 )
                return true;
            aux = (r2x-cx)*(r2x-cx)+(r1y-cy)*(r1y-cy) - r2;
            if ( aux<=0 )
                return true;
            aux = (r2x-cx)*(r2x-cx)+(r2y-cy)*(r2y-cy) - r2;
            if ( aux<=0 )
                return true;
            //return false, check if intersection between circle and rectangle,
            //for cases when points on lines are inside circle.
            return false;
        }
     */
    /**
     * checks intersection of circle with an rectangle by checking the intersection
     * of circle with each line in rectangle
     * @param cx
     * @param cy
     * @param r
     * @param r1x
     * @param r1y
     * @param r2x
     * @param r2y
     * @return
     */
    /*    public static boolean intersectCircleRectangle ( float cx, float cy, float r, float r1x, float r1y, float r2x, float r2y)
        {
            if ( intersectCircleLine( cx, cy, r, r1x, r1y, r2x, r1y) )
                return true;
            if ( intersectCircleLine( cx, cy, r, r2x, r1y, r2x, r2y) )
                return true;
            if ( intersectCircleLine( cx, cy, r, r2x, r2y, r1x, r2y) )
                return true;
            if ( intersectCircleLine( cx, cy, r, r1x, r2y, r1x, r1y) )
                return true;
            return false;
        }
     */
    /**
     * intersectCircleLine<br>
     * Detects only if line intersects circle or not.<br>
     * Sep 13, 2005 12:23:07 PM - mluc<br>
     * @param cx
     * @param cy
     * @param r
     * @param a1x
     * @param a1y
     * @param a2x
     * @param a2y
     */
    /*    public static boolean intersectCircleLine( float cx, float cy, float r, float a1x, float a1y, float a2x, float a2y)
        {
            float a, b, cc, deter;
            a  = (a2x-a1x)*(a2x-a1x)+(a2y-a1y)*(a2y-a1y);
            b  = 2*((a2x-a1x)*(a1x-cx)+(a2y-a1y)*(a1y-cy));
            cc = cx*cx+cy*cy+a1x*a1x+a1y*a1y - 2*(cx*a1x+cy*a1y) - r*r;
            deter = b*b - 4*a*cc;
            if ( deter > 0) {
                float e  = (float)Math.sqrt(deter);
                float u1 = ( -b + e ) / ( 2*a );
                float u2 = ( -b - e ) / ( 2*a );

                if ( (u1 >= 0 && u1 <= 1) || (u2 >= 0 && u2 <= 1) )
                    return true;
            };
            return false;
        }
     */
    /** print sync variable */
    //    public static final Object syncPrintObject = new Object();
}
