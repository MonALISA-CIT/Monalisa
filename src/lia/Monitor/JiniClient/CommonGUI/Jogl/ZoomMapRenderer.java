package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.image.BufferedImage;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.CapitalsGISInfo;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.MyGLU;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.MyGLUT;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.SHPInfo;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.SHPObject;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ShapeFileUtils;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/*
 * Created on 03.05.2004 23:27:37
 * Filename: ZoomMapRenderer.java
 *
 */
/**
 * @author Luc
 *
 * ZoomMapRenderer
 */
public class ZoomMapRenderer implements GLEventListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ZoomMapRenderer.class.getName());

    public UserInputListener uil = new UserInputListener();

    private final MyGLU myglu = new MyGLU();
    private final MyGLUT myglut = new MyGLUT();

    //MyGLObject cursor_hand = new MyGLObject();

    @Override
    public void init(GLAutoDrawable gLDrawable) {
        //gLDrawable.setGL(new DebugGL(gLDrawable.getGL()));
        //init opengl stuff
        //System.out.println("OpenGL Canvas initialisation");
        //load initial texture
        GL2 gl = gLDrawable.getGL().getGL2();

        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glEnable(GL.GL_DEPTH_TEST); // Hidden surface removal
        gl.glFrontFace(GL.GL_CCW); // Counter clock-wise polygons face out
        //gl.glEnable(GL.GL_CULL_FACE);		// Cull back-facing triangles
        //		gl.glPolygonMode( GL.GL_BACK, GL.GL_LINE);
        //        gl.glPolygonMode( GL.GL_BACK, GL.GL_FILL);
        gl.glPolygonMode(GL.GL_FRONT, GL2.GL_FILL);

        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClearDepth(1.0f); // Depth Buffer Setup

        //        gl.glEnable(GL.GL_FOG);
        //        {
        //            float []fogColor = {0f, .4f, .8f, 1f};
        //
        //            int fogMode = GL.GL_EXP2;
        //            gl.glFogi (GL.GL_FOG_MODE, fogMode);
        //            gl.glFogfv (GL.GL_FOG_COLOR, fogColor);
        //            gl.glFogf (GL.GL_FOG_DENSITY, 0.004f);
        //            gl.glHint (GL.GL_FOG_HINT, GL.GL_DONT_CARE);
        //        }

        //		gl.glEnable(GL.GL_LIGHTING);

        //		float ambientLight0[] = { 0.9f, 0.9f, 0.9f, 1.0f };//{ 0.3f, 0.3f, 0.3f, 1.0f };
        //		float diffuseLight0[] = { 0.7f, 0.7f, 0.7f, 1.0f };
        ////		 Setup and enable light 0
        //		gl.glLightfv(GL.GL_LIGHT0,GL.GL_AMBIENT,ambientLight0);
        //		gl.glLightfv(GL.GL_LIGHT0,GL.GL_DIFFUSE,diffuseLight0);
        //		//The light is positioned by this code:
        //		gl.glEnable(GL.GL_LIGHT0);

        //TODO: should these be enabled?
        // Enable color tracking
        //		gl.glEnable(GL.GL_COLOR_MATERIAL);
        // Set Material properties to follow glColor values
        //		gl.glColorMaterial(GL.GL_FRONT, GL.GL_AMBIENT_AND_DIFFUSE);

        long startTime;
        long endTime;
        try {
            //load star fundal
            startTime = NTPDate.currentTimeMillis();
            BufferedImage bi = Globals.loadBuffImage("lia/images/sky_texture.jpg");
            if (bi != null) {
                JoglPanel.globals.sky_texture_id = DataGlobals.makeTexture(gl, bi);
            }
            ;
            endTime = NTPDate.currentTimeMillis();
            logger.log(Level.FINEST, "Loading sky texture -> " + (bi == null ? "error! -> " : "") + " -> done in "
                    + (endTime - startTime) + " ms.");
        } catch (Exception ex) {
            logger.log(Level.WARNING, "ERROR Loading sky texture.");
            ex.printStackTrace();
        }
        try {
            //load no texture image
            startTime = NTPDate.currentTimeMillis();
            BufferedImage biNOT = Globals.loadBuffImage("lia/images/no_texture.jpg");
            if (biNOT != null) {
                JoglPanel.globals.no_texture_id = DataGlobals.makeTexture(gl, biNOT);
            }
            ;
            endTime = NTPDate.currentTimeMillis();
            logger.log(Level.FINEST, "Loading no texture" + (biNOT == null ? " error!" : "") + " -> done in "
                    + (endTime - startTime) + " ms.");
        } catch (Exception ex) {
            logger.log(Level.WARNING, "ERROR Loading no texture.");
            ex.printStackTrace();
        }

        buildClouds(gl);
        //build moon
        //        if ( !Globals.bHideMoon )
        buildMoon(gl);

        //construct initial tree, but it does not use actual coordinates in the grid
        //so it does not need the initialized coordinates yet
        //        Texture lroot = Texture.constructInitialTree();
        //compute the points for the current globe radius
        //        JoglPanel.globals.computeGrid(lroot);
        //        Texture.swapRoots( lroot, Texture.nInitialLevel, new Date());
        JoglPanel.globals.nTreeID = ChangeRootTexture.getNextTreeID();
        JoglPanel.globals.root = Texture.constructInitialTree(null, 0);
        //compute the points for the current globe radius
        JoglPanel.globals.computeGrid(JoglPanel.globals.root);

        if (Texture.nInitialLevel > 0) {
            BackgroundWorker.schedule(new TimerTask() {
                @Override
                public void run() {
                    Thread.currentThread().setName("JoGL Change Root Texture thread - changing the level of texture");
                    ChangeRootTexture.init(Texture.nInitialLevel, ChangeRootTexture.CRT_KEY_MODE_LOAD,
                            JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                            JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                    //                    ChangeRootTexture crtMonitor = new ChangeRootTexture(Texture.nInitialLevel);
                    //                    crtMonitor.setProgressBar(JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress, JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                    //                    if ( !crtMonitor.init() )
                    //                        System.out.println("Resolution changing in progress.... Please wait");
                }

            }, 1000);
        }
        ;

        setAxesID(gl);

        //        int circleID=0;
        //        circleID = gl.glGenLists(1);
        //        gl.glNewList( circleID, GL.GL_COMPILE);
        //        {
        //            //gl.glDisable( GL.GL_TEXTURE_2D);
        //            Globals.drawCircle( gl, 32, 1f);
        //            //gl.glEnable( GL.GL_TEXTURE_2D);
        //        }
        //        gl.glEndList();
        int hexagonID = 0;
        hexagonID = gl.glGenLists(1);
        gl.glNewList(hexagonID, GL2.GL_COMPILE);
        {
            //gl.glDisable( GL.GL_TEXTURE_2D);
            Globals.drawHashedHexagon(gl);
            //gl.glEnable( GL.GL_TEXTURE_2D);
        }
        gl.glEndList();
        //        float []coords=null;
        //        VectorO VzAxis = new VectorO(0,0,1);
        //        float radius = JoglPanel.globals.globeRadius/360f*.5f;
        //        float supl_radius = 0.00001f;
        //construct gis info
        if (!AppConfig.getProperty("jogl.rederer.map.noBorder", "false").equals("true")) {
            JoglPanel.globals.wcityGIS = new CapitalsGISInfo("lia/images/joglpanel/cities.csv");
            JoglPanel.globals.wcityGisID90 = doCitiesList(gl, JoglPanel.globals.wcityGIS, hexagonID, false);
            JoglPanel.globals.wcityGisID0 = doCitiesList(gl, JoglPanel.globals.wcityGIS, hexagonID, true);
            JoglPanel.globals.uscityGIS = new CapitalsGISInfo("lia/images/joglpanel/uscities.csv");
            JoglPanel.globals.uscityGisID90 = doCitiesList(gl, JoglPanel.globals.uscityGIS, hexagonID, false);
            JoglPanel.globals.uscityGisID0 = doCitiesList(gl, JoglPanel.globals.uscityGIS, hexagonID, true);

            JoglPanel.globals.countriesBordersID0 = doCoutryMap(gl, true, "lia/images/joglpanel/shapefile/cntry98.shp",
                    new int[] { 232 }, new int[][] { { 135 } }, 218f / 255f, 218f / 255f, 170f / 255f);/*144f/255f, 151f/255f, 141f/255f*///US big border, because states have their own borders
            JoglPanel.globals.countriesBordersID90 = doCoutryMap(gl, false,
                    "lia/images/joglpanel/shapefile/cntry98.shp", new int[] { 232 }, new int[][] { { 135 } },
                    218f / 255f, 218f / 255f, 170f / 255f);//US big border, because states have their own borders
            JoglPanel.globals.usStatesBordersID0 = doCoutryMap(gl, true, "lia/images/joglpanel/shapefile/usa_st.shp",
                    new int[] { 0, 49 }, new int[][] { null, null }, 223.0f / 255.0f, 224.0f / 255.0f, 134.0f / 255.0f);//alaska and small isles on far east of world map
            JoglPanel.globals.usStatesBordersID90 = doCoutryMap(gl, false, "lia/images/joglpanel/shapefile/usa_st.shp",
                    new int[] { 0, 49 }, new int[][] { null, null }, 223.0f / 255.0f, 224.0f / 255.0f, 134.0f / 255.0f);//alaska and small isles on far east of world map
        }
        //gl.glEnable(GL.GL_TEXTURE_2D);

        //compute initial view frustum
        //uil.computeViewFrustum();

        //load mouse cursor
        //cursor_hand.ImportObject("images/hand_human.obj");
        //cursor_hand.InitObject(gl, 1f/120.0f);
        //JoglPanel.globals.CursorPosition[2] = -cursor_hand.getYMin()/120.0f;

        JoglPanel.globals.bJoglInitialized = true;
    }

    private int doCitiesList(GL2 gl, CapitalsGISInfo cities, int hexagonID, boolean bPlaneProj) {
        float[] coords = null;
        VectorO VzAxis = new VectorO(0, 0, 1);
        float full_radius = (Globals.MAP_WIDTH * .5f) / (float) Math.PI;
        float radius = (full_radius / 360f) * .5f;
        float supl_radius = 0.00001f;
        int cityGisID = gl.glGenLists(1);
        //System.out.println("routerID="+ routerID);
        gl.glNewList(cityGisID, GL2.GL_COMPILE);
        {
            //draw each town at globeRadius+0.001f from center
            //with respect to its coordinates
            gl.glColor3f(.7f, .7f, .7f);
            gl.glLineWidth(2f);
            for (int i = 0; i < cities.getTotalNumber(); i++) {
                //operations are put in inverse order because last is first executed in opengl
                gl.glPushMatrix();
                //3. position object cu correct coordinates
                if (bPlaneProj) {
                    coords = Globals.point2Dto3D0(cities.fLat[i], cities.fLong[i], coords, supl_radius);
                } else {
                    coords = Globals.point2Dto3D90(cities.fLat[i], cities.fLong[i], coords, supl_radius);
                }
                gl.glTranslatef(coords[0], coords[1], coords[2]);
                //2. operation: rotate from (0,0,1) to Vdir
                //for that, first compute rotation axis as cross product between z and Vdir
                if (!bPlaneProj) {
                    VectorO vDir;
                    vDir = new VectorO(coords[0], coords[1], (coords[2] - JoglPanel.globals.globeVirtualRadius)
                            + JoglPanel.globals.globeRadius + supl_radius);
                    vDir.Normalize();
                    VectorO VRotAxis = VzAxis.CrossProduct(vDir);
                    // rotate z to Vdir around vectorial product with dot product
                    gl.glRotatef((float) ((Math.acos(VzAxis.DotProduct(vDir)) * 180) / Math.PI), VRotAxis.getX(),
                            VRotAxis.getY(), VRotAxis.getZ());
                }
                ;
                //1. operation: scale to radius dimmensions:
                gl.glScalef(radius, radius, radius);
                //use already constructed cap as display list
                //draw it at (0,0,0) in self reference system
                gl.glCallList(hexagonID);
                gl.glPopMatrix();
            }
            ;
            gl.glLineWidth(1f);
        }
        gl.glEndList();
        return cityGisID;
    }

    /**
     * generate country map for specified projection<br>
     * ignores shapes that appear in shape list and on the corresponding position in part list have null<br>
     * and ignores parts in a shape from shape list that appear in parts list for the corresponding index of the shape.
     * @param gl
     * @param bPlaneProj if true, do plane projection, else do sphere projection
     * @param ignoreShapeList shapes to ignore when drawing
     * @param ignorePartList parts from a shape to ignore when drawing
     * @return
     */
    private int doCoutryMap(GL2 gl, boolean bPlaneProj, String sShapeFileName, int[] ignoreShapeList,
            int[][] ignorePartList, float fRed, float fGreen, float fBlue) {
        long lStartTime = NTPDate.currentTimeMillis();
        int countryID = -1;
        try {
            float[] CurCoords = null;
            //            float []AntCoords=null;
            ShapeFileUtils shpUtils = new ShapeFileUtils();
            //String sShapeFileName = "lia/images/joglpanel/shapefile/cntry98.shp";//Thread.currentThread().getContextClassLoader().getResource("lia/images/joglpanel/shapefile/cntry98.shp").getFile();//"/home/mluc/Monalisa/bin/lia/images/joglpanel/shapefile/cntry98.shp";//
            SHPInfo hSHP;
            hSHP = shpUtils.SHPOpen(sShapeFileName);
            if (hSHP == null) {
                System.out.println("Unable to open: " + sShapeFileName);
                return -1;
            }
            int nEntities, i, iPart;
            int[] pnEntitiesAndShapeType = new int[2];
            double[] adfMinBound = new double[4];
            double[] adfMaxBound = new double[4];
            //        NumberFormat nf = NumberFormat.getInstance();
            //        nf.setMaximumFractionDigits(2);
            //        nf.setMinimumFractionDigits(2);
            shpUtils.SHPGetInfo(hSHP, pnEntitiesAndShapeType, adfMinBound, adfMaxBound);
            nEntities = pnEntitiesAndShapeType[0];
            float supl_radius = 0f;//(bPlaneProj?0.0001f:0.000001f);
            double min_degree = 1;//minimum long or lat a shape must have to be drawn
            countryID = gl.glGenLists(1);
            gl.glNewList(countryID, GL2.GL_COMPILE);
            {
                gl.glPushMatrix();
                gl.glColor3f(fRed, fGreen, fBlue);
                double xMin, xMax, yMin, yMax;
                boolean bNewPart;
                int j;
                SHPObject psShape;
                boolean bIgnore = false;
                for (i = 0; i < nEntities; i++) {
                    if (ignoreShapeList != null) {
                        bIgnore = false;
                        for (int k = 0; k < ignoreShapeList.length; k++) {
                            if (i == ignoreShapeList[k]) {
                                if (ignorePartList[k] == null) {
                                    bIgnore = true;
                                    //                                    System.out.println("ignoring shape "+i);
                                }
                                break;
                            }
                        }
                        ;
                        if (bIgnore) {
                            continue;
                        }
                    }
                    ;
                    psShape = shpUtils.SHPReadObject(hSHP, i);
                    //                        System.out.println( "\nShape:"+i+" ("+shpUtils.SHPTypeName(psShape.nSHPType)+")  nVertices="+psShape.nVertices+", nParts="+psShape.nParts+"\n"
                    //                                +"  Bounds:("+psShape.dfXMin+","+psShape.dfYMin+", "+psShape.dfZMin+", "+psShape.dfMMin+")\n"
                    //                                +"      to ("+psShape.dfXMax+","+psShape.dfYMax+", "+psShape.dfZMax+", "+psShape.dfMMax+")");
                    //init minim and maxim margins
                    xMin = xMax = 0;//psShape.padfX[0];
                    yMin = yMax = 0;
                    bNewPart = true;
                    for (j = 0, iPart = 1; j < psShape.nVertices; j++) {
                        if ((iPart < psShape.nParts) && (psShape.panPartStart[iPart] == j)) {
                            //if part ending, check if is to be drawn
                            if ((Math.abs(xMax - xMin) > min_degree) || (Math.abs(yMax - yMin) > min_degree)) {
                                //draw part
                                //only if not in ignore list
                                bIgnore = false;
                                if (ignoreShapeList != null) {
                                    for (int k = 0; k < ignoreShapeList.length; k++) {
                                        if (i == ignoreShapeList[k]) {
                                            if (ignorePartList[k] != null) {
                                                for (int l = 0; l < ignorePartList[k].length; l++) {
                                                    if (ignorePartList[k][l] == iPart) {
                                                        bIgnore = true;
                                                        //                                                          System.out.println("ignoring part "+i+"."+iPart);
                                                        break;
                                                    }
                                                }
                                            }
                                            ;
                                            break;
                                        }
                                    }
                                    ;
                                }
                                ;
                                if (!bIgnore) {
                                    gl.glBegin(GL.GL_LINE_LOOP);
                                    for (int k = psShape.panPartStart[iPart - 1]; k < j; k++) {
                                        if (bPlaneProj) {
                                            CurCoords = Globals.point2Dto3D0((float) psShape.padfY[k],
                                                    (float) psShape.padfX[k], CurCoords, supl_radius);
                                        } else {
                                            CurCoords = Globals.point2Dto3D90((float) psShape.padfY[k],
                                                    (float) psShape.padfX[k], CurCoords, supl_radius);
                                        }
                                        gl.glVertex3fv(CurCoords, 0);
                                    }
                                    gl.glEnd();
                                }
                                ;
                            }
                            bNewPart = true;
                            //xMin = xMax = psShape.padfX[j];
                            iPart++;
                        }
                        ;
                        if (bNewPart) {
                            xMin = xMax = psShape.padfX[j];
                            yMin = yMax = psShape.padfY[j];
                            bNewPart = false;
                        }
                        ;
                        //update margins for current part
                        if (psShape.padfX[j] < xMin) {
                            xMin = psShape.padfX[j];
                        }
                        if (psShape.padfX[j] > xMax) {
                            xMax = psShape.padfX[j];
                        }
                        if (psShape.padfY[j] < yMin) {
                            yMin = psShape.padfY[j];
                        }
                        if (psShape.padfY[j] > yMax) {
                            yMax = psShape.padfY[j];
                        }
                    }
                    //draw last part
                    if ((Math.abs(xMax - xMin) > min_degree) || (Math.abs(yMax - yMin) > min_degree)) {//has in longitude more than 2 degrees
                        bIgnore = false;
                        if (ignoreShapeList != null) {
                            for (int k = 0; k < ignoreShapeList.length; k++) {
                                if (i == ignoreShapeList[k]) {
                                    if (ignorePartList[k] != null) {
                                        for (int l = 0; l < ignorePartList[k].length; l++) {
                                            if (ignorePartList[k][l] == iPart) {
                                                bIgnore = true;
                                                //                                                  System.out.println("ignoring part "+i+"."+iPart);
                                                break;
                                            }
                                        }
                                    }
                                    ;
                                    break;
                                }
                            }
                            ;
                        }
                        ;
                        if (!bIgnore) {
                            gl.glBegin(GL.GL_LINE_LOOP);
                            for (int k = psShape.panPartStart[iPart - 1]; k < psShape.nVertices; k++) {
                                if (bPlaneProj) {
                                    CurCoords = Globals.point2Dto3D0((float) psShape.padfY[k],
                                            (float) psShape.padfX[k], CurCoords, supl_radius);
                                } else {
                                    CurCoords = Globals.point2Dto3D90((float) psShape.padfY[k],
                                            (float) psShape.padfX[k], CurCoords, supl_radius);
                                }
                                gl.glVertex3fv(CurCoords, 0);
                            }
                            gl.glEnd();
                        }
                    }
                    ;
                    shpUtils.SHPDestroyObject(psShape);
                }
                gl.glPopMatrix();
                shpUtils.SHPClose(hSHP);
            }
            gl.glEndList();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "exception reading shape file " + sShapeFileName + ": " + ex.getMessage());
            //ex.printStackTrace();
            countryID = -1;
        }
        long lEndTime = NTPDate.currentTimeMillis();
        logger.fine("doCoutryMap operation for " + sShapeFileName
                + (bPlaneProj ? " plane projection" : " sphere projection") + " took " + (lEndTime - lStartTime)
                + " miliseconds.");
        return countryID;
    }

    public void displayTest(GLAutoDrawable gLDrawable) {
        final GL2 gl = gLDrawable.getGL().getGL2();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glTranslatef(-1.5f, 0.0f, -50.0f);
        gl.glBegin(GL.GL_TRIANGLES); // Drawing Using Triangles
        gl.glColor3f(1.0f, 0.0f, 0.0f); // Set the current drawing color to red
        gl.glVertex3f(0.0f, 1.0f, 0.0f); // Top
        gl.glColor3f(0.0f, 1.0f, 0.0f); // Set the current drawing color to green
        gl.glVertex3f(-1.0f, -1.0f, 0.0f); // Bottom Left
        gl.glColor3f(0.0f, 0.0f, 1.0f); // Set the current drawing color to blue
        gl.glVertex3f(1.0f, -1.0f, 0.0f); // Bottom Right
        gl.glEnd(); // Finished Drawing The Triangle
        gl.glTranslatef(3.0f, 0.0f, 0.0f);
        gl.glBegin(GL.GL_TRIANGLE_STRIP); // Draw A Quad
        gl.glColor3f(0.5f, 0.5f, 1.0f); // Set the current drawing color to light blue
        gl.glVertex3f(-1.0f, 1.0f, 1.0f); // Top Left
        gl.glColor3f(0f, 1f, 0f); // Set the current drawing color to blue
        gl.glVertex3f(-1.0f, -1.0f, 0.0f); // Bottom Left
        gl.glColor3f(0.0f, 0.0f, 1.0f); // Set the current drawing color to blue
        gl.glVertex3f(1.0f, 1.0f, 1.0f); // Top Right
        gl.glColor3f(1.0f, 0.0f, 0f); // Set the current drawing color to blue
        gl.glVertex3f(1.0f, -1.0f, 0.0f); // Bottom Right
        gl.glEnd(); // Done Drawing The Quad
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glTranslatef(-1.5f, 0.0f, -.2f);
        gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.root.texture_id);
        gl.glColor3f(1f, 1f, 1f);
        gl.glBegin(GL.GL_TRIANGLE_STRIP);
        //System.out.println("p left,top=("+grid_pointer[startY][startX][0]+","+grid_pointer[startY][startX][1]+","+grid_pointer[startY][startX][2]+")");
        gl.glTexCoord2f(0f, 1f);
        gl.glVertex3f(-5f, 5f, 0f);
        //System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
        gl.glTexCoord2f(0f, 0f);
        gl.glVertex3f(-5f, -5f, 0f);
        //System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
        gl.glTexCoord2f(1f, 1f);
        gl.glVertex3f(5f, 5f, 0f);
        //System.out.println("p right,bottom=("+grid_pointer[startY+ny][startX+nx][0]+","+grid_pointer[startY+ny][startX+nx][1]+","+grid_pointer[startY+ny][startX+nx][2]+")");
        gl.glTexCoord2f(1f, 0f);
        gl.glVertex3f(5f, -5f, 0f);
        gl.glEnd();
        DrawFrustum(gl);
        gl.glFlush();
    }

    @Override
    public void display(GLAutoDrawable gLDrawable) {
        //gLDrawable.setGL(new DebugGL(gLDrawable.getGL()));
        displayBefore(gLDrawable);
        displayAfter(gLDrawable);
    }

    public void displayBefore(GLAutoDrawable gLDrawable) {
        //moved to DataRenderer
        //		long lCurrentTime = NTPDate.currentTimeMillis();
        //		if ( JoglPanel.globals.lLastRefreshTime != -1 &&
        //				lCurrentTime < JoglPanel.globals.lLastRefreshTime + Globals.REFRESH_TIME )
        //			return;

        //check eye position and time -> load/replace neccessary texture
        //draw map according to eye position
        //gLDrawable.setGL(new DebugGL(gLDrawable.getGL()));
        GL2 gl = gLDrawable.getGL().getGL2();

        //if the texture in a slice must be change,
        //first reverse to low resolution texture in previous high res slice
        //and then load in the new slice the high res texture
        //check first the time  and depth if everything is in place
        long currentIdleTime = NTPDate.currentTimeMillis();
        if (JoglPanel.globals.startIdleTime == -1) {
            JoglPanel.globals.startIdleTime = currentIdleTime;
        } else if ((currentIdleTime - JoglPanel.globals.startIdleTime) >= Globals.IDLE_TIME
        /*Main.globals.EyePosition[2] <= Globals.CHANGE_RES_DEPTH*/) {
            try {
                Texture.setTreeTextures(gl);
                Texture.unsetTreeTextures(gl);
            } catch (Exception ex) {
                System.out.println("Error setting or unsetting tree textures: ");
                ex.printStackTrace();
            }
        }

        //check char pressed
        int[] params = new int[2];
        gl.glGetIntegerv(GL2.GL_POLYGON_MODE, params, 0);
        if (JoglPanel.globals.charPressed == 'F') {
            JoglPanel.globals.charPressed = 'f';
            if (params[0] == GL2.GL_FILL) {
                gl.glPolygonMode(GL.GL_FRONT, GL2.GL_LINE);
            } else if (params[0] == GL2.GL_LINE) {
                gl.glPolygonMode(GL.GL_FRONT, GL2.GL_FILL);
            }
        }
        ;
        if (JoglPanel.globals.charPressed == 'B') {
            JoglPanel.globals.charPressed = 'b';
            if (params[1] == GL2.GL_FILL) {
                gl.glPolygonMode(GL.GL_BACK, GL2.GL_LINE);
            } else if (params[1] == GL2.GL_LINE) {
                gl.glPolygonMode(GL.GL_BACK, GL2.GL_FILL);
            }
        }

        // Clear the GL buffer
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        gl.glLoadIdentity();

        VectorO vEye_Dir = new VectorO(JoglPanel.globals.EyePosition);
        vEye_Dir.AddVector(JoglPanel.globals.EyeDirection);

        myglu.gluLookAt(
                gl,
                (float) JoglPanel.globals.EyePosition.getXprojection(),
                (float) JoglPanel.globals.EyePosition.getYprojection(),
                (float) JoglPanel.globals.EyePosition.getZprojection(),
                (float) JoglPanel.globals.EyePosition.getXprojection()
                        + (float) JoglPanel.globals.EyeDirection.getXprojection(),
                (float) JoglPanel.globals.EyePosition.getYprojection()
                        + (float) JoglPanel.globals.EyeDirection.getYprojection(),
                (float) JoglPanel.globals.EyePosition.getZprojection()
                        + (float) JoglPanel.globals.EyeDirection.getZprojection(),
                (float) JoglPanel.globals.EyeNormal.getXprojection(),
                (float) JoglPanel.globals.EyeNormal.getYprojection(),
                (float) JoglPanel.globals.EyeNormal.getZprojection());

        //draw the eye direction
        //as it is now is wrong, as the y axis changes with normal
        /*		gl.glColor3f( 1.0f, 0.0f, 0.0f);
        		gl.glBegin( GL.GL_LINES);
        			gl.glVertex3f(Main.globals.EyePosition[0], Main.globals.EyePosition[1]+3.0f, Main.globals.EyePosition[2] );
        			gl.glVertex3f(Main.globals.EyeDirection[0], Main.globals.EyeDirection[1], Main.globals.EyeDirection[2]);
        		gl.glEnd();
         */
        //System.out.println("draw: "+Main.globals.VskyLT);

        //        gl.glDisable(GL.GL_FOG);
        //enable texturing
        gl.glEnable(GL.GL_TEXTURE_2D);
        //draw the sky
        float lT = 0f, rT = 1f, bT = 0f, tT = 1f;
        gl.glColor3f(.7f, .7f, .7f);
        gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.sky_texture_id);
        gl.glBegin(GL.GL_TRIANGLE_STRIP);
        gl.glTexCoord2f(lT, tT);
        gl.glVertex3f(JoglPanel.globals.VskyLT.getX(), JoglPanel.globals.VskyLT.getY(), JoglPanel.globals.VskyLT.getZ());
        gl.glTexCoord2f(lT, bT);
        gl.glVertex3f(JoglPanel.globals.VskyLB.getX(), JoglPanel.globals.VskyLB.getY(), JoglPanel.globals.VskyLB.getZ());
        gl.glTexCoord2f(rT, tT);
        gl.glVertex3f(JoglPanel.globals.VskyRT.getX(), JoglPanel.globals.VskyRT.getY(), JoglPanel.globals.VskyRT.getZ());
        gl.glTexCoord2f(rT, bT);
        gl.glVertex3f(JoglPanel.globals.VskyRB.getX(), JoglPanel.globals.VskyRB.getY(), JoglPanel.globals.VskyRB.getZ());
        gl.glEnd();

        //Texture.drawTree( gl);
        gl.glDisable(GL.GL_TEXTURE_2D);

        if (JoglPanel.globals.bShowChartesianSystem) {
            drawAxes(gl);
            //DrawFrustum(gl);
        }

        //draw moon
        if (!Globals.bHideMoon) {
            drawMoon(gl);
        }

        //        gl.glEnable(GL.GL_FOG);

    }

    public void drawBordersAndCities(GL2 gl) {
        if (JoglPanel.globals.mapAngle == 90) {
            float dist = (float) JoglPanel.globals.EyePosition.getRadius();
            if (dist < 40f) {
                if (Globals.bShowCountriesBorders) {
                    if (JoglPanel.globals.countriesBordersID90 != -1) {
                        gl.glCallList(JoglPanel.globals.countriesBordersID90);
                    }
                    if (JoglPanel.globals.usStatesBordersID90 != -1) {
                        gl.glCallList(JoglPanel.globals.usStatesBordersID90);
                    }
                }
                ;
                if (Globals.bShowCities) {
                    if (JoglPanel.globals.wcityGisID90 != -1) {
                        gl.glCallList(JoglPanel.globals.wcityGisID90);
                    }
                    if (JoglPanel.globals.uscityGisID90 != -1) {
                        gl.glCallList(JoglPanel.globals.uscityGisID90);
                    }
                }
                ;
            }
            ;
        } else if (JoglPanel.globals.mapAngle == 0) {
            float dist = (float) JoglPanel.globals.EyePosition.DotProduct(JoglPanel.globals.EyeDirection);
            if (dist < 0) {
                dist = -dist;
            }
            if (dist < 70f) {
                float z_translate = dist * .001f;//(float)JoglPanel.globals.EyePosition.getRadius()*.0001f;
                if (Globals.bShowCountriesBorders) {
                    //only if distance is smaller than a certain value
                    //because of some randation problems due to very small distance between borders and map
                    //do a translation in z direction away from map, depending on zoom level
                    if ((JoglPanel.globals.countriesBordersID0 != -1) || (JoglPanel.globals.usStatesBordersID0 != -1)) {
                        gl.glPushMatrix();
                        gl.glTranslatef(0f, 0f, z_translate);
                        if (JoglPanel.globals.countriesBordersID0 != -1) {
                            gl.glCallList(JoglPanel.globals.countriesBordersID0);
                        }
                        if (JoglPanel.globals.usStatesBordersID0 != -1) {
                            gl.glCallList(JoglPanel.globals.usStatesBordersID0);
                        }
                        gl.glPopMatrix();
                    }
                    ;
                }
                ;
                if (Globals.bShowCities) {
                    //because of some randation problems due to very small distance between borders and map
                    //do a translation in z direction away from map, depending on zoom level
                    if ((JoglPanel.globals.wcityGisID0 != -1) && (JoglPanel.globals.uscityGisID0 != -1)) {
                        gl.glPushMatrix();
                        gl.glTranslatef(0f, 0f, z_translate);
                        if (JoglPanel.globals.wcityGisID0 != -1) {
                            gl.glCallList(JoglPanel.globals.wcityGisID0);
                        }
                        if (JoglPanel.globals.uscityGisID0 != -1) {
                            gl.glCallList(JoglPanel.globals.uscityGisID0);
                        }
                        gl.glPopMatrix();
                    }
                    ;
                }
                ;
            }
            ;
        }
    }

    public void displayAfter(GLAutoDrawable gLDrawable) {
        GL2 gl = gLDrawable.getGL().getGL2();

        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_DONT_CARE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        drawBordersAndCities(gl);
        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);

        drawClouds(gl);

        //draw scroll/rotation area
        if ( /*!JoglPanel.globals.mainPanel.renderer.sr.IsInRotation() && */Globals.bShowRotationBar
                && (!Globals.bAutoHideRotationBar || (Globals.bAutoHideRotationBar && (Globals.nShowRBs != 0)))) {
            gl.glEnable(GL.GL_BLEND);
            //source is what is in front, destination is what already is there
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            InfoPlane ipScroll = new InfoPlane(gl, 0.5f);
            if ((Globals.nShowRBs & InfoPlane.POSITION_LEFT) > 0) {
                ipScroll.doRotationBar(InfoPlane.POSITION_LEFT, JoglPanel.globals.nRBHotSpotPos,
                        Globals.bShowRotationBarTooltip);
            }
            if ((Globals.nShowRBs & InfoPlane.POSITION_TOP) > 0) {
                ipScroll.doRotationBar(InfoPlane.POSITION_TOP, JoglPanel.globals.nRBHotSpotPos,
                        Globals.bShowRotationBarTooltip);
            }
            if ((Globals.nShowRBs & InfoPlane.POSITION_RIGHT) > 0) {
                ipScroll.doRotationBar(InfoPlane.POSITION_RIGHT, JoglPanel.globals.nRBHotSpotPos,
                        Globals.bShowRotationBarTooltip);
            }
            if ((Globals.nShowRBs & InfoPlane.POSITION_BOTTOM) > 0) {
                ipScroll.doRotationBar(InfoPlane.POSITION_BOTTOM, JoglPanel.globals.nRBHotSpotPos,
                        Globals.bShowRotationBarTooltip);
            }
            gl.glDisable(GL.GL_BLEND);
        }
        ;

        //draw test text
        //		gl.glPushMatrix();
        //        gl.glTranslatef(0.0f, 0.0f, 1.0f);						// Move One Unit Into The Screen
        //        gl.glScalef(0.005f, 0.005f, 0.0f);
        //        gl.glColor3f( 1.0f, 1.0f, 1.0f);
        //        renderStrokeString(gl, MyGLUT.STROKE_MONO_ROMAN, "p#$~&^*QWZIljk"); // Print GL Text To The Screen
        //		gl.glPopMatrix();
        //drawText( gl, "p#$~&^*QWZIljk");

        //draw circle
        //		if ( Globals.testSpherePoints!=null ) {
        //			gl.glBegin(GL.GL_POLYGON);
        //				for( int i=0; i<Globals.testSpherePoints.length; i++)
        //					gl.glVertex3fv( Globals.testSpherePoints[i]);
        //			gl.glEnd();
        //		}

        //draw cursor
        /*		if ( JoglPanel.globals.bShowCursor ) {
        			gl.glTranslatef( JoglPanel.globals.CursorPosition[0],  JoglPanel.globals.CursorPosition[1],  JoglPanel.globals.CursorPosition[2]);
        			if ( uil.button_pressed > 0 )
        				gl.glRotatef( -30.0f, 1.0f, 0.0f, 0.0f);
        			gl.glRotatef( 90.0f, 1.0f, 0.0f, 0.0f);
        			gl.glRotatef( 180.0f, 0.0f, 1.0f, 0.0f);
        			gl.glScalef( (float)JoglPanel.globals.EyePosition.getZprojection()/20.0f, (float)JoglPanel.globals.EyePosition.getZprojection()/20.0f, (float)JoglPanel.globals.EyePosition.getZprojection()/20.0f);
        			//cursor_hand.DrawObject( gl);
        			//cursor_hand.DrawPlanes( gl, 120.0f);
        		};
         */
        //moved to DataRenderer
        //		JoglPanel.globals.lLastRefreshTime = NTPDate.currentTimeMillis();
    }

    private void renderStrokeString(GL2 gl, int font, String string) {
        // Center Our Text On The Screen
        // Render The Text
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            myglut.glutStrokeCharacter(gl, font, c);
        }
    }

    @Override
    public void reshape(GLAutoDrawable gLDrawable, int x, int y, int width, int height) {
        //set window mode
        //gLDrawable.setGL(new DebugGL(gLDrawable.getGL()));
        GL2 gl = gLDrawable.getGL().getGL2();

        //System.out.println("gl_reshape="+gl);

        float fAspect;
        // Prevent a divide by zero, when window is too short
        // (you cant make a window of zero width).
        if (height == 0) {
            height = 1;
        }

        // Set the viewport to be the entire window
        gl.glViewport(0, 0, width, height);

        JoglPanel.globals.width = width;
        JoglPanel.globals.height = height;
        fAspect = (float) width / (float) height;
        JoglPanel.globals.fAspect = fAspect;

        //compute initial sky position
        //JoglPanel.globals.correctSkyPosition(null, null, null);
        Globals.falisafeUpdateEyeVectors(null, null, null);
        //System.out.println("reshape: "+Main.globals.VskyLT);

        //compute new depth values for changing resolution
        float Xpt_max = 2;//the change of res will be when the dimension of a
        //texture pixel is greater or equal to 2 (screen pixels).
        //set each depth
        //D is an intermediary value for depths
        float D;
        D = ((width / 2f / (float) Math.tan(((Globals.FOV_ANGLE / 2.0) * Math.PI) / 180.0f)) * Globals.MAP_WIDTH)
                / Xpt_max;
        int resolutionXlevel = Texture.resolutionX0;
        for (int i = 0; i < Texture.depthZ.length; i++) {
            resolutionXlevel *= 2; //=== Texture.resolutionX[i+1]
            Texture.depthZ[i] = D / resolutionXlevel;
        }
        ;
        //after the depths have been recomputed, the textures may change, so
        //the idle timer shall be notified in short time
        //reset start idle time
        //		JoglPanel.globals.startIdleTime = NTPDate.currentTimeMillis();
        //		JoglPanel.globals.bIsIdle = true;
        JoglPanel.globals.resetIdleTime();

        // Reset coordinate system
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();

        {
            // Produce the perspective projection
            double left;
            double right;
            double bottom;
            double top;

            right = Globals.NEAR_CLIP * Math.tan(((Globals.FOV_ANGLE / 2.0) * Math.PI) / 180.0f);
            top = right / fAspect;
            bottom = -top;
            left = -right;
            gl.glFrustum(left, right, bottom, top, Globals.NEAR_CLIP, Globals.FAR_CLIP);
        }

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    private void DrawFrustum(GL2 gl) {
        //gl.glDisable(GL.GL_COLOR_MATERIAL);
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glPushMatrix();
        float fAspect = JoglPanel.globals.fAspect;
        gl.glTranslatef((float) JoglPanel.globals.EyePosition.getXprojection(),
                (float) JoglPanel.globals.EyePosition.getYprojection(),
                (float) JoglPanel.globals.EyePosition.getZprojection());
        float tg = (float) Math.tan((Globals.FOV_ANGLE * .5f * Math.PI) / 180f);
        //here I presume that fov angle is 90 degrees
        float left_near = -Globals.NEAR_CLIP * tg;
        float left_far = -Globals.FAR_CLIP * tg;//*(1-0.1f);
        float bottom_near = left_near / fAspect;
        float top_near = -left_near / fAspect;
        float bottom_far = left_far / fAspect;
        float top_far = -left_far / fAspect;
        float right_near = Globals.NEAR_CLIP * tg;
        float right_far = Globals.FAR_CLIP * tg;
        gl.glBegin(GL2.GL_QUADS);
        //left plane
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glVertex3f(left_near, bottom_near, -Globals.NEAR_CLIP);
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glVertex3f(left_far, bottom_far, -Globals.FAR_CLIP);
        gl.glColor3f(1.0f, 0f, 1.0f);
        gl.glVertex3f(left_far, top_far, -Globals.FAR_CLIP * .99f);
        //gl.glColor3f( 1.0f, 1.0f, 0.0f);
        gl.glVertex3f(left_near, top_near, -Globals.NEAR_CLIP * 1.01f);

        //right plane
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glVertex3f(right_near, bottom_near, -Globals.NEAR_CLIP);
        gl.glVertex3f(right_near, top_near, -Globals.NEAR_CLIP);
        gl.glColor3f(1.0f, 0f, 1.0f);
        gl.glVertex3f(right_far, top_far, -Globals.FAR_CLIP);
        gl.glVertex3f(right_far, bottom_far, -Globals.FAR_CLIP);

        //top
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glVertex3f(left_near, top_near, -Globals.NEAR_CLIP);
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glVertex3f(right_near, top_near, -Globals.NEAR_CLIP);
        gl.glColor3f(1.0f, 0f, 1.0f);
        gl.glVertex3f(right_far, top_far, -Globals.FAR_CLIP);
        gl.glVertex3f(left_far, top_far, -Globals.FAR_CLIP);
        //			gl.glColor3f( 1.0f, 1.0f, 0.0f);
        //			gl.glVertex3f( left_near, top_near, -Globals.NEAR_CLIP);
        //			gl.glColor3f( 1.0f, 0f, 1.0f);
        //			gl.glVertex3f( left_far, top_far, -Globals.FAR_CLIP);
        //			gl.glVertex3f( right_far, top_far, -Globals.FAR_CLIP);
        //			gl.glColor3f( 1.0f, 1.0f, 0.0f);
        //			gl.glVertex3f( right_near, top_near, -Globals.NEAR_CLIP);

        //bottom
        gl.glVertex3f(left_near, bottom_near, -Globals.NEAR_CLIP);
        gl.glVertex3f(right_near, bottom_near, -Globals.NEAR_CLIP);
        gl.glColor3f(1.0f, 0f, 1.0f);
        gl.glVertex3f(right_far, bottom_far, -Globals.FAR_CLIP);
        gl.glVertex3f(left_far, bottom_far, -Globals.FAR_CLIP);
        gl.glEnd();
        gl.glPopMatrix();
        gl.glEnable(GL.GL_TEXTURE_2D);
        //gl.glEnable(GL.GL_COLOR_MATERIAL);
    }

    public void drawCone(GL2 gl, int nMaxPoints) {
        float faDelta = (2 * (float) Math.PI) / nMaxPoints, faAlfa;
        float fX, fY;
        //draw upper cone
        gl.glBegin(GL.GL_TRIANGLE_FAN);
        //top
        gl.glVertex3f(0, 0, 2);
        //side
        faAlfa = 0.0f;
        for (int slice = 0; slice <= nMaxPoints; slice++) {
            fX = (float) Math.cos(faAlfa);
            fY = (float) Math.sin(faAlfa);
            gl.glVertex3f(fX, fY, 0);
            faAlfa += faDelta;
        }
        gl.glEnd();

        //draw base circle
        gl.glBegin(GL.GL_TRIANGLE_FAN);
        //top
        gl.glVertex3f(0, 0, 0);
        //side
        faAlfa = (float) (2 * Math.PI);
        for (int slice = 0; slice <= nMaxPoints; slice++) {
            fX = (float) Math.cos(faAlfa);
            fY = (float) Math.sin(faAlfa);
            gl.glVertex3f(fX, fY, 0);
            faAlfa -= faDelta;
        }
        gl.glEnd();
    }

    private int axesID = 0;

    public void setAxesID(GL2 gl) {
        if (axesID == 0) {
            float fLineSize = 1.0f;
            float fArrowSize = fLineSize / 10.0f;
            int font = MyGLUT.STROKE_MONO_ROMAN;
            String sAxis = "";
            float fontWidth = 0;

            axesID = gl.glGenLists(1);
            gl.glNewList(axesID, GL2.GL_COMPILE);
            {
                //draw y axis with red color
                gl.glColor3f(1.0f, 0.0f, 0.0f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex3f(0.0f, 0.0f, 0.0f);
                gl.glVertex3f(0.0f, fLineSize, 0.0f);
                gl.glEnd();
                gl.glPushMatrix();
                gl.glTranslatef(0.0f, fLineSize - (2 * fArrowSize), 0.0f);
                gl.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f);
                gl.glScalef(fArrowSize, fArrowSize, fArrowSize);
                drawCone(gl, 8);
                gl.glPopMatrix();
                sAxis = "y";
                gl.glPushMatrix();
                fontWidth = myglut.glutStrokeLength(font, sAxis);
                //translate center up the y axis
                gl.glTranslatef(0, fLineSize, 0);
                //scale object to arrow size
                gl.glScalef(2 * fArrowSize, 2 * fArrowSize, 0.0f);
                //center object in center
                gl.glTranslatef(-0.5f, 0.5f, 0);
                //scale object to unit dimmensions
                gl.glScalef(1 / fontWidth, 1 / fontWidth, 0.0f);
                renderStrokeString(gl, font, sAxis);
                gl.glPopMatrix();
                //draw z axis with blue color
                gl.glColor3f(0.0f, 0.0f, 1.0f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex3f(0.0f, 0.0f, 0.0f);
                gl.glVertex3f(0.0f, 0.0f, fLineSize);
                gl.glEnd();
                gl.glPushMatrix();
                gl.glTranslatef(0.0f, 0.0f, fLineSize - (2 * fArrowSize));
                //glu.gluCylinder( qAxeArrow, fArrowSize, 0.0f, fArrowSize, 10, 10);
                gl.glScalef(fArrowSize, fArrowSize, fArrowSize);
                drawCone(gl, 8);
                gl.glPopMatrix();
                sAxis = "z";
                gl.glPushMatrix();
                fontWidth = myglut.glutStrokeLength(font, sAxis);
                //translate center up the y axis
                gl.glTranslatef(0, 0, fLineSize);
                //rotate to z axis
                gl.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
                //scale object to arrow size
                gl.glScalef(2 * fArrowSize, 2 * fArrowSize, 0.0f);
                //center object in center
                gl.glTranslatef(-0.5f, 0.5f, 0);
                //scale object to unit dimmensions
                gl.glScalef(1 / fontWidth, 1 / fontWidth, 0.0f);
                renderStrokeString(gl, font, sAxis);
                gl.glPopMatrix();
                //draw x axis with green color
                gl.glColor3f(0.0f, 1.0f, 0.0f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex3f(0.0f, 0.0f, 0.0f);
                gl.glVertex3f(fLineSize, 0.0f, 0.0f);
                gl.glEnd();
                gl.glPushMatrix();
                gl.glTranslatef(fLineSize - (2 * fArrowSize), 0.0f, 0.0f);
                gl.glRotatef(90.0f, 0.0f, 1.0f, 0.0f);
                //glu.gluCylinder( qAxeArrow, fArrowSize, 0.0f, fArrowSize, 10, 10);
                gl.glScalef(fArrowSize, fArrowSize, fArrowSize);
                drawCone(gl, 8);
                gl.glPopMatrix();
                sAxis = "x";
                gl.glPushMatrix();
                fontWidth = myglut.glutStrokeLength(font, sAxis);
                //translate center up the y axis
                gl.glTranslatef(fLineSize, 0, 0);
                //scale object to arrow size
                gl.glScalef(2 * fArrowSize, 2 * fArrowSize, 0.0f);
                //center object in center
                gl.glTranslatef(0, -0.5f, 0);
                //scale object to unit dimmensions
                gl.glScalef(1 / fontWidth, 1 / fontWidth, 0.0f);
                renderStrokeString(gl, font, sAxis);
                gl.glPopMatrix();
            }
            gl.glEndList();
        }
    }

    public void drawAxes(GL2 gl) {
        float Fx, Fy;
        Fx = 0.167f;
        Fy = 0.2f;
        float dzmin = Globals.NEAR_CLIP;
        float alpha_x = Globals.FOV_ANGLE;
        float dz = Globals.MIN_DEPTH;
        float z = dzmin + (0.5f * dz);
        float dx = 2 * z * (float) Math.tan((0.5 * alpha_x * Math.PI) / 180);
        float dy = dx / JoglPanel.globals.fAspect;
        float fx = 0.9f * (0.5f * Fx * dx);
        float fy = 0.9f * (0.5f * Fy * dy);
        float fz = 0.9f * 0.5f * dz;
        float f = fz;
        if (fx < f) {
            f = fx;
        }
        if (fy < f) {
            f = fy;
        }
        //System.out.println("fx="+fx+" fy="+fy+" fz="+fz);
        VectorO vA = new VectorO(JoglPanel.globals.EyePosition);
        VectorO vD = new VectorO(JoglPanel.globals.EyeDirection);
        vD.MultiplyScalar(z);
        vA.AddVector(vD);
        VectorO vN = new VectorO(JoglPanel.globals.EyeNormal);
        vN.MultiplyScalar(-((0.5f * dy) - ((1.1f * f) / (float) Math.cos((0.5 * alpha_x * Math.PI) / 180))));
        vA.AddVector(vN);
        VectorO vM = JoglPanel.globals.EyeDirection.CrossProduct(JoglPanel.globals.EyeNormal);
        vM.Normalize();
        vM.MultiplyScalar(-((0.5f * dx) - ((1.1f * f) / (float) Math.cos((0.5 * alpha_x * Math.PI) / 180))));
        vA.AddVector(vM);
        float pAx, pAy, pAz;
        pAx = vA.getX();//JoglPanel.globals.EyePosition.getX()-(1.0f-Fx)*0.5f*dx;
        pAy = vA.getY();//JoglPanel.globals.EyePosition.getY()-(1.0f-Fy)*0.5f*dy;
        pAz = vA.getZ();//JoglPanel.globals.EyePosition.getZ()-z;

        if (axesID > 0) {
            gl.glPushMatrix();
            gl.glTranslatef(pAx, pAy, pAz);
            gl.glScalef(f, f, f);
            //		    gl.glPushMatrix();
            //		        float width = myglut.glutStrokeLength(MyGLUT.STROKE_MONO_ROMAN, "NeHe - Test");
            //		        gl.glTranslatef(-f*3*width / 2f, 0, 0);
            //			    gl.glScalef( 3/width, 3/width, 0.0f);
            //		        gl.glColor3f( 1.0f, 1.0f, 1.0f);
            //		        renderStrokeString(gl, MyGLUT.STROKE_MONO_ROMAN, "NeHe - Test"); // Print GL Text To The Screen
            //			gl.glPopMatrix();
            gl.glCallList(axesID);
            gl.glPopMatrix();
        }
        ;
    }

    //variables to know to draw several texts one after another
    private float start_text_x = 0f;
    private float start_text_y = 0f;
    //private float start_text_yant = 0f;
    private float start_text_max_height = 0f;
    private boolean bIncXPositive = true;
    private boolean bIncYPositive = false;
    private float dx = 0;//width of info drawing plane
    private float dy = 0;//height of info drawing plane
    private float z = 0;//position of info drawing plane from eye on axis leaving the eye and perpendicular on plane
    private float dz = Globals.MIN_DEPTH;//width of space for more planes drawing

    //	private float pixelWidth=0f;//a screen pixel's width on info plane
    //	private float pixelHeight=0f;//a screen pixel's height on info plane, should be equal with pixelWidth

    /**
     * initializes the two text related variables
     *
     */
    public void initText(boolean bLeft2Right, boolean bTop2Bottom)//int mouse_x, int mouse_y)
    {
        //text display in 3d space  related variables
        float dzmin = Globals.NEAR_CLIP;
        float alpha_x = Globals.FOV_ANGLE;
        dz = Globals.MIN_DEPTH;
        z = dzmin + (0.5f * dz);
        //width of available screen at z position
        dx = 2 * z * (float) Math.tan((0.5 * alpha_x * Math.PI) / 180);
        //height of available screen at z position
        dy = dx / JoglPanel.globals.fAspect;

        //compute screen's pixel width on info plane:
        //		pixelWidth = dx/JoglPanel.globals.width;
        //		pixelHeight = dx/JoglPanel.globals.height;//should be equal with width

        //	    int font = MyGLUT.STROKE_MONO_ROMAN;/**/
        //		float text_height = myglut.glutStrokeHeight( font);
        //	    float text_height_init = myglut.glutStrokeHeightInit( font);

        bIncXPositive = bLeft2Right;
        bIncYPositive = !bTop2Bottom;
        if (bIncXPositive) {
            start_text_x = -0.475f * dx;
        } else {
            start_text_x = 0.475f * dx;
        }
        ;
        if (!bIncYPositive) {
            start_text_y = 0.475f * dy;
            //start_text_y = start_text_yant;//!!!!! y < yant so not ok, this will be a method to know
            start_text_max_height = 0f;
        } else {
            start_text_y = -0.475f * dy;
            //			start_text_y = start_text_yant;//!!!!! y < yant so not ok, this will be a method to know
            start_text_max_height = 0f;
        }
        ;
    }

    /**
     * draws informations for an object taken from attached hashmap.<br>
     * fields expected to be found are:<br>
     * Name -> text<br>
     * Description -> text<br>
     * Position -> VectorO<br>
     * Type -> text<br>
     * Vcf -> VcfObject<br>
     * ImageId -> integer id for opengl texture<br>
     * @param gl
     * @param point
     * @param objAttrs
     */
    /*	public void drawInfoForPoint( GL gl, float[] point, HashMap objAttrs)
    	{

     * info is composed of a title, a picture and a description.
     * the box containing the info is bordered.
     * the info box has a starting x and y position on 2d plane used for drawing.
     * the x and y coordinates depend on booleans: bLeft2Right and bTop2Bottom
     * where the first means that x is incremented when boolean is true
     * and second that y is decremented when boolean is true.
     * the font used for drawing is of fixed size given in pixels a letter would cover.

    	    float border = 0f;//border width
    	    float hSpace = 0f;//horizontal space between two components
    	    float vSpace = 0f; //vertical space between two components

    	    border = pixelWidth;//border=1 px
    	    hSpace = 2*pixelWidth;//hSpace = 2 px
    	    vSpace = 2*pixelWidth;//vSpace = 2 px
    	}
     */
    /**
     * draws a text in the upper left corner showing also the starting point
     * @param gl
     * @param sText
     * @param vStartFrom
     */
    public void drawTextFrom(GL2 gl, String sText, float[] startFrom) {

        //text related variables
        int len_text = sText.length();
        int font = MyGLUT.STROKE_MONO_ROMAN;/**/
        float text_width = myglut.glutStrokeLength(font, sText);
        float text_height = myglut.glutStrokeHeight(font);
        float text_height_init = -myglut.glutStrokeHeightInit(font);

        int Nmax = 60; //maximal number of characters that can be shown on a line on the screen
        /*
         * on screen on a line, at a moment of time, there can be only Nmax chars that will occupy
         * 95% of space on x
         */
        float dcar = (0.95f * dx) / (Nmax > len_text ? Nmax : len_text); //maximal width in screen(z) constraints for a character
        float dx_text = len_text * dcar; //text width in screen(z) constraints

        float f = dx_text / text_width; //raport between screen(z) constraints and real text width => the scalling value
        float dy_text = f * text_height; //text height in screen(z) constraints
        float dy_text_init = f * text_height_init; //text height init in screen(z) constraints

        /**
         * position text among the others.<br>
         * for that, use two variables, accessible between 2 function calls, start_text_x and start_text_y,
         * to know on a grill with Nmax chars on x where the next word should start
         */

        float start_text_dx = 0;
        float start_text_dy = 0;

        if (bIncXPositive) {//texts are put from left to right
            if ((start_text_x + dx_text) < (0.475f * dx)) {
                start_text_dx = start_text_x;
            } else {//go to next line
                start_text_dx = -0.475f * dx;
                if (!bIncYPositive) {
                    start_text_y -= (start_text_max_height + (0.025f * dy));
                } else {
                    start_text_y += start_text_max_height + (0.025f * dy);
                }
                start_text_max_height = 0f;
            }
        } else {//texts are put from right to left
            if ((start_text_x - dx_text) > (-0.475f * dx)) {
                start_text_dx = start_text_x - dx_text;
            } else {//go to next line
                start_text_dx = (0.475f * dx) - dx_text;
                if (!bIncYPositive) {
                    start_text_y -= (start_text_max_height + (0.025f * dy));
                } else {
                    start_text_y += start_text_max_height + (0.025f * dy);
                }
                start_text_max_height = 0f;
            }
        }
        if (!bIncYPositive) {//text start y?
            start_text_dy = start_text_y - dy_text;// + dy_text_init;
        } else {
            start_text_dy = start_text_y;// + dy_text - dy_text_init;
        }
        if (bIncXPositive) {//next start for x
            start_text_x = start_text_dx + dx_text + (0.025f * dx);
        } else {
            start_text_x = start_text_dx - (0.025f * dx);
        }
        if (start_text_max_height < dy_text) {
            start_text_max_height = dy_text;//next start for y
            //System.out.println("start_text_x="+start_text_x+" start_text_y="+start_text_y);
        }

        //translate text to its correct position
        VectorO vA = new VectorO(JoglPanel.globals.EyePosition);
        VectorO vD = new VectorO(JoglPanel.globals.EyeDirection);
        vD.MultiplyScalar(z);
        vA.AddVector(vD);
        VectorO vN = new VectorO(JoglPanel.globals.EyeNormal);
        vN.MultiplyScalar(start_text_dy);
        vA.AddVector(vN);
        VectorO vM = JoglPanel.globals.EyeDirection.CrossProduct(JoglPanel.globals.EyeNormal);
        vM.Normalize();
        vM.MultiplyScalar(start_text_dx);
        vA.AddVector(vM);
        float pAx, pAy, pAz;
        pAx = vA.getX();
        pAy = vA.getY();
        pAz = vA.getZ();

        //save gl matrix
        gl.glPushMatrix();

        //set lines color
        gl.glColor3f(1f, 1f, 1f);

        //draw connection between the bounding rectangle and the starting point
        vN = new VectorO(JoglPanel.globals.EyeNormal);
        vM = JoglPanel.globals.EyeDirection.CrossProduct(JoglPanel.globals.EyeNormal);
        VectorO vPoint1 = new VectorO(vM);
        vPoint1.MultiplyScalar(.5f * dx_text);
        //		vPoint1.MultiplyScalar(.33f*dx_text);
        vPoint1.AddVector(vA);
        if (bIncYPositive) {//from bottom to top
            vN.MultiplyScalar(dy_text - dy_text_init);
        } else {
            vN.MultiplyScalar(-dy_text_init);
        }
        vPoint1.AddVector(vN);
        //		VectorO vPoint2 = new VectorO(vM);
        //		vPoint2.MultiplyScalar(.66f*dx_text);
        //		vPoint2.AddVector( vA);
        //		vPoint2.AddVector( vN);
        gl.glBegin(GL.GL_LINES);
        gl.glVertex3f(startFrom[0], startFrom[1], startFrom[2]);
        gl.glVertex3f(vPoint1.getX(), vPoint1.getY(), vPoint1.getZ());
        //			gl.glVertex3f( startFrom[0], startFrom[1], startFrom[2]);
        //			gl.glVertex3f( vPoint2.getX(), vPoint2.getY(), vPoint2.getZ());
        gl.glEnd();

        //translate text to draw start point
        gl.glTranslatef(pAx, pAy, pAz);

        //set text draw plane
        vD = new VectorO(JoglPanel.globals.EyeDirection);
        vN = new VectorO(JoglPanel.globals.EyeNormal);
        vM = JoglPanel.globals.EyeDirection.CrossProduct(JoglPanel.globals.EyeNormal);
        vD.MultiplyScalar(-1);
        VectorO vZ = new VectorO(0, 0, 1);
        float rot_angle1 = 0;
        float rot_angle2 = 0;
        VectorO vAxis = vZ.CrossProduct(vD);
        //obtain first rotation angle to get z vector over -vD vector in degrees
        rot_angle1 = (float) ((Math.acos(Globals.limitValue(vZ.DotProduct(vD), -1, 1)) * 180) / Math.PI);
        //check to see if angle 1 is the correct one:
        vZ.RotateDegree(vAxis, rot_angle1);
        if (!vZ.equals(vD)) {
            rot_angle1 = 360 - rot_angle1;
        }
        //update x axis to be able to rotate it also
        VectorO vX = new VectorO(1, 0, 0);
        vX.RotateDegree(vAxis, rot_angle1);
        //obtain second rotation angle to get x and y vectors over vM and vN in degrees
        rot_angle2 = (float) ((Math.acos(Globals.limitValue(vX.DotProduct(vM), -1, 1)) * 180) / Math.PI);
        //check to see if angle 2 is the correct one:
        vX.RotateDegree(vD, rot_angle2);
        if (!vX.equals(vM)) {
            rot_angle2 = 360 - rot_angle2;
        }

        //rotate text to be in screen(z) plane
        gl.glRotatef(rot_angle2, vD.getX(), vD.getY(), vD.getZ());
        gl.glRotatef(rot_angle1, vAxis.getX(), vAxis.getY(), vAxis.getZ());

        //fill rectangle
        gl.glColor3f(.6f, .6f, .6f);
        gl.glBegin(GL2.GL_POLYGON);
        gl.glVertex3f(0, -dy_text_init, -.001f * dz);
        gl.glVertex3f(dx_text, -dy_text_init, -.001f * dz);
        gl.glVertex3f(dx_text, dy_text - dy_text_init, -.001f * dz);
        gl.glVertex3f(0, dy_text - dy_text_init, -.001f * dz);
        gl.glEnd();

        //set lines color
        gl.glColor3f(1f, 1f, 1f);
        //draw bounding rectangle
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3f(0, -dy_text_init, 0);
        gl.glVertex3f(dx_text, -dy_text_init, 0);
        gl.glVertex3f(dx_text, dy_text - dy_text_init, 0);
        gl.glVertex3f(0, dy_text - dy_text_init, 0);
        gl.glEnd();

        //scale text to draw it
        gl.glScalef(f, f, f);
        //draw text
        gl.glColor3f(1f, 1f, 1.0f);
        renderStrokeString(gl, font, sText);

        //revert to initial draw matrix
        gl.glPopMatrix();
    }

    public void buildClouds(GL2 gl) {
        BufferedImage bi = Globals.loadBuffImageAlpha(JoglPanel.globals.sCloudsTexture);
        JoglPanel.globals.clouds_tid = DataGlobals.loadTextureAlpha(gl, bi, "clouds");
        if (JoglPanel.globals.clouds_tid != -1) {
            JoglPanel.globals.clouds_opengl_id = gl.glGenLists(1);
            gl.glNewList(JoglPanel.globals.clouds_opengl_id, GL2.GL_COMPILE);
            {
                drawTexturedSphere(gl, JoglPanel.globals.clouds_tid, 32, 18);
            }
            gl.glEndList();
            if (JoglPanel.globals.clouds_opengl_id != -1) {
                UpdateCloudsThread ucThread = new UpdateCloudsThread();
                new Thread(ucThread).start();
            }
            ;
        }
        ;
    }

    /**
     * updates the clouds movement as it follows:
     * - if last user input was 30 seconds ago, then increment clouds rotation angle and repaint,
     * also sleep for 100 miliseconds
     * - else, sleep for 5 seconds
     * @author mluc
     * @version Mar 10, 2006 10:23:31 AM
     * @since
     *
     */
    class UpdateCloudsThread implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("Jogl - increment clouds rotation angle");
            while (true) {
                if ((JoglPanel.globals.mapAngle == 90) && JoglPanel.globals.canvas.isVisible() && Globals.bShowClouds
                        && (uil.lastMovementTime != -1)
                        && ((NTPDate.currentTimeMillis() - uil.lastMovementTime) > 30000)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                    if (JoglPanel.globals.lEndCanvasRepaint > JoglPanel.globals.lStartCanvasRepaint) {
                        JoglPanel.globals.fCloudsRotationAngle += 0.1;
                        JoglPanel.globals.canvas.repaint();
                        //						System.out.println(new Date(NTPDate.currentTimeMillis())+" here");
                    }
                    ;
                } else {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                    }
                    //                    System.out.println(new Date(NTPDate.currentTimeMillis())+" here2");
                }
            }
        }
    }

    /**
     * creates a sphere object textured with moon's aspect
     * that will rotate around earth.<br>
     * The rotation period is 29.5 days.
     * @param gl
     */
    public static void buildMoon(GL2 gl) {
        JoglPanel.globals.vMoonPosition = new VectorO(50, 0, 0);
        JoglPanel.globals.vMoonRotationAxis = new VectorO(0, 1, 0);
        BufferedImage bi = Globals.loadBuffImage(JoglPanel.globals.sMoonTexture);
        if (bi == null) {
            return;
        }
        Shadow.setShadow(bi, 1f);
        JoglPanel.globals.moon_tid = DataGlobals.loadTexture(gl, bi, "moon");
        JoglPanel.globals.moon_opengl_id = gl.glGenLists(1);
        //System.out.println("routerID="+ routerID);
        gl.glNewList(JoglPanel.globals.moon_opengl_id, GL2.GL_COMPILE);
        {
            drawTexturedSphere(gl, JoglPanel.globals.moon_tid, 32, 18);
        }
        gl.glEndList();
    }

    public void drawClouds(GL2 gl) {
        if (Globals.bShowClouds && (JoglPanel.globals.mapAngle == 90) && (JoglPanel.globals.clouds_opengl_id != 0)) {
            if ((uil.lastMovementTime != -1) && ((NTPDate.currentTimeMillis() - uil.lastMovementTime) > 30000)) {
                if (JoglPanel.globals.fCloudsRotationAngle >= 360f) {
                    JoglPanel.globals.fCloudsRotationAngle = 0f;
                }
                gl.glEnable(GL.GL_BLEND);
                //source is what is in front, destination is what already is there
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glPushMatrix();
                float zoom = 5.4f;
                gl.glScalef(zoom, zoom, zoom);
                gl.glColor4f(1f, 1f, 1f, .3f);
                gl.glRotatef(JoglPanel.globals.fCloudsRotationAngle, 0f, 1f, 0f);
                gl.glCallList(JoglPanel.globals.clouds_opengl_id);
                gl.glColor3f(1f, 1f, 1f);
                gl.glPopMatrix();
                gl.glDisable(GL.GL_BLEND);
            }

        }

    }

    public static void drawMoon(GL2 gl) {
        if ((JoglPanel.globals.mapAngle == 90) && (JoglPanel.globals.moon_opengl_id != 0)) {
            gl.glPushMatrix();
            gl.glTranslatef(JoglPanel.globals.vMoonPosition.getX(), JoglPanel.globals.vMoonPosition.getY(),
                    JoglPanel.globals.vMoonPosition.getZ());
            gl.glRotatef(90, 0f, 1f, 0f);
            gl.glCallList(JoglPanel.globals.moon_opengl_id);
            gl.glPopMatrix();
        }

    }

    public static void drawTexturedSphere(GL2 gl, int tid, int slices_x, int slices_y) {
        gl.glEnable(GL.GL_TEXTURE_2D); // Enable Texture Mapping
        gl.glBindTexture(GL.GL_TEXTURE_2D, tid);
        float dThetaLONG, dThetaLAT; //minimal angle to get from one point to the next
        dThetaLONG = (float) ((2 * Math.PI) / slices_x);
        dThetaLAT = (float) (Math.PI / slices_y);
        float dTextX, dTextY;
        dTextX = 1f / slices_x;
        dTextY = 1f / slices_y;
        int i, j;
        float x, y, z, tx, ty1, ty2;
        float theta1, theta2, theta3;
        float PID2 = (float) Math.PI * .5f;
        for (j = 0; j < slices_y; j++) {
            theta1 = (j * dThetaLAT) - PID2;
            theta2 = ((j + 1) * dThetaLAT) - PID2;
            ty1 = j * dTextY;
            ty2 = (j + 1) * dTextY;
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            theta3 = 0;
            tx = 0;
            for (i = 0; i < slices_x; i++) {

                /**draw lower vertex*/
                x = (float) (Math.cos(theta1) * Math.cos(theta3));
                y = (float) (Math.sin(theta1));
                z = (float) (Math.cos(theta1) * Math.sin(theta3));

                gl.glTexCoord2f(tx, ty1);
                gl.glVertex3f(x, y, z);

                /**draw upper vertex*/
                x = (float) (Math.cos(theta2) * Math.cos(theta3));
                y = (float) (Math.sin(theta2));
                z = (float) (Math.cos(theta2) * Math.sin(theta3));

                gl.glTexCoord2f(tx, ty2);
                gl.glVertex3f(x, y, z);

                theta3 += dThetaLONG;
                tx += dTextX;
            }
            //draw last set of points
            {
                //when theta3 is again zero
                theta3 = 0;
                tx = 0;

                /**draw lower vertex*/
                x = (float) (Math.cos(theta1) * Math.cos(theta3));
                y = (float) (Math.sin(theta1));
                z = (float) (Math.cos(theta1) * Math.sin(theta3));

                gl.glTexCoord2f(1f, ty1);
                gl.glVertex3f(x, y, z);

                /**draw upper vertex*/
                x = (float) (Math.cos(theta2) * Math.cos(theta3));
                y = (float) (Math.sin(theta2));
                z = (float) (Math.cos(theta2) * Math.sin(theta3));

                gl.glTexCoord2f(1f, ty2);
                gl.glVertex3f(x, y, z);
            }
            gl.glEnd();
        }

        gl.glDisable(GL.GL_TEXTURE_2D); // Disable Texture Mapping
    }

    public static void drawSphere(GL2 gl, int slices_x, int slices_y) {
        float dThetaLONG, dThetaLAT; //minimal angle to get from one point to the next
        dThetaLONG = (float) ((2 * Math.PI) / slices_x);
        dThetaLAT = (float) (Math.PI / slices_y);
        int i, j;
        float x, y, z;
        float theta1, theta2, theta3;
        float PID2 = (float) Math.PI * .5f;
        for (j = 0; j < slices_y; j++) {
            theta1 = (j * dThetaLAT) - PID2;
            theta2 = ((j + 1) * dThetaLAT) - PID2;
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            theta3 = 0;
            for (i = 0; i < slices_x; i++) {

                /**draw lower vertex*/
                x = (float) (Math.cos(theta1) * Math.cos(theta3));
                y = (float) (Math.sin(theta1));
                z = (float) (Math.cos(theta1) * Math.sin(theta3));

                gl.glVertex3f(x, y, z);

                /**draw upper vertex*/
                x = (float) (Math.cos(theta2) * Math.cos(theta3));
                y = (float) (Math.sin(theta2));
                z = (float) (Math.cos(theta2) * Math.sin(theta3));

                gl.glVertex3f(x, y, z);

                theta3 += dThetaLONG;
            }
            //draw last set of points
            {
                //when theta3 is again zero
                theta3 = 0;

                /**draw lower vertex*/
                x = (float) (Math.cos(theta1) * Math.cos(theta3));
                y = (float) (Math.sin(theta1));
                z = (float) (Math.cos(theta1) * Math.sin(theta3));

                gl.glVertex3f(x, y, z);

                /**draw upper vertex*/
                x = (float) (Math.cos(theta2) * Math.cos(theta3));
                y = (float) (Math.sin(theta2));
                z = (float) (Math.cos(theta2) * Math.sin(theta3));

                gl.glVertex3f(x, y, z);
            }
            gl.glEnd();
        }
        ;
    }

    @Override
    public void dispose(GLAutoDrawable glAD) {
        // TODO Auto-generated method stub
        logger.log(Level.INFO, "ZooomMapRender dispose: " + glAD);
    }
}
