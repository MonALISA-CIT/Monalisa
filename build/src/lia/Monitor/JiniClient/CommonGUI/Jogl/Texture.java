package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.NetResourceClassLoader;
import lia.util.ntp.NTPDate;

/*
 * Created on May 7, 2004
 */
/**
 * @author mluc
 *         class containing the definition of texture tree
 */
public class Texture extends TextureParams {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Texture.class.getName());

    public static Level logLevel = Level.FINEST;// ;//for debug use Level.INFO, for normal use Level.FINEST

    /**
     * implicit constructor that does nothing, because the data is already initialized
     */
    public Texture() { /* fLongStart=fLongEnd=fLatStart=fLatEnd=0f; *//* lastAccessTime = new Date(); */
    }

    /**
     * constuctor to initialize a node in the tree
     *
     * @param tex_id
     *            opengl id for texture
     */
    public Texture(int tex_id) {
        this();
        texture_id = tex_id;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions
     *
     * @param tex_id
     *            opengl id for texture
     * @param wX
     *            world position on x axis -> indice on x for array
     * @param wY
     *            world position on y axis
     * @param w
     *            width on map ( world map ) -> number of divisions covered by this texture
     * @param h
     *            height on map
     */
    public Texture(int tex_id, int wX, int wY, int w, int h) {
        this(tex_id);
        nWorldX = wX;
        nWorldY = wY;
        nWidth = w;
        nHeight = h;
    }

    public Texture(int tex_id, int status) {
        this(tex_id);
        this.status = status;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions
     * and a given status
     *
     * @param tex_id
     *            opengl id for texture
     * @param wX
     *            world position on x axis
     * @param wY
     *            world position on y axis
     * @param w
     *            width on map ( world map )
     * @param h
     *            height on map
     * @param status
     *            texture loading indicator
     */
    public Texture(int tex_id, int wX, int wY, int w, int h, int status) {
        this(tex_id, status);
        nWorldX = wX;
        nWorldY = wY;
        nWidth = w;
        nHeight = h;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions<br>
     * and the slice coordinates in texture
     *
     * @param tex_id
     *            opengl id for texture
     * @param wX
     *            world position on x axis
     * @param wY
     *            world position on y axis
     * @param w
     *            width on map ( world map )
     * @param h
     *            height on map
     * @param lT
     *            left position in texture
     * @param bT
     *            bottom position in texture
     * @param rT
     *            right position in texture
     * @param tT
     *            top position in texture
     */
    public Texture(int tex_id, int wX, int wY, int w, int h, float lT, float bT, float rT, float tT) {
        this(tex_id, wX, wY, w, h);
        leftT = lT;
        bottomT = bT;
        rightT = rT;
        topT = tT;
    }

    /**
     * Loads a texture from file into an byte array<br>
     * first check if an weak reference to data exists and if it is valid<br>
     * if not, loads from file
     *
     * @param fileName
     *            the full path to file
     * @param data
     *            represents an array that will be used if not null, instead of creating a new array based on
     *            file length
     * @param vbType
     *            is a vector with an element to remember the place the texture was loaded from
     */
    public static void loadTextureFromFile(TextureLoadJobResult result) {
        loadTextureFromFile(result, JoglPanel.globals.myMapsClassLoader);
    }

    public static void loadTextureFromFile(TextureLoadJobResult result, NetResourceClassLoader myMCL) { // (NetResourceClassLoader
        // myMapsClassLoader,
        // String
        // fileName,
        // byte[] data,
        // int level,
        // int[] vbType)
        int loadedFrom;// stores place where from last image was loaded
        // range: 0 - disc, 1 - memory -> must be less than 256
        long startTime = NTPDate.currentTimeMillis();
        // search for data texture in local cache
        JoGLCachedTexture jct = textureDataSpace.findTexture(result.getResultID());
        if ((jct != null) && (jct.joglID != 0)) {
            result.joglID = jct.joglID; // reuse this joglID
            loadedFrom = 1;
        } else {
            byte[] data = null;
            result.joglID = 0;
            // load it from disk / network
            loadedFrom = 0;
            try {
                Object[] objStream = myMCL.getResourceAsStream(result.path/* +".oct" */);
                BufferedInputStream bis = null;
                int retVal = 0;
                if ((objStream != null) && (objStream.length > 1) && (objStream[1] instanceof Integer)) {
                    retVal = ((Integer) objStream[1]).intValue();
                }
                if (retVal == NetResourceClassLoader.FIND_RESOURCE_MAX_LEVEL.intValue()) {
                    // System.out.println("texture "+result.path+" is at max level");
                    result.isMaxLevel = true;
                } else if (retVal == NetResourceClassLoader.FIND_RESOURCE_FILE_ISDOWNLOADING.intValue()) {
                    // data is null
                    result.errReason = TextureLoadJobResult.ERR_ISDOWNLOADING;
                }
                if ((objStream != null) && (objStream.length > 0) && (objStream[0] != null)
                        && (objStream[0] instanceof InputStream)) {
                    bis = new BufferedInputStream((InputStream) objStream[0]);
                    int file_length = nTextureFileLength;
                    // create a new memory space
                    data = textureDataSpace.getSlab();
                    int nRead = bis.read(data);
                    if (nRead != file_length) {
                        System.out.println("error, file length not right for " + result.path);
                        textureDataSpace.releaseSlab(data); // error - release invalid slab
                        data = null;
                    }
                    bis.close();
                    // result.data = data;
                    // cast shadow on image, if the case
                    if ((data != null) && (result.texture.checkSpecialFlag(S_SPECIAL_NO_NIGHT) == false)) {
                        int width, height;
                        width = OCT_WIDTH;
                        height = OCT_HEIGHT;
                        // width = resolutionX[result.level];
                        // height = resolutionY[result.level];
                        // for( int i=0; i<=result.level; i++) {
                        // width /= texturesX[i];
                        // height /= texturesY[i];
                        // };
                        int mapx, mapy;
                        float worldX, worldY;
                        /** compute worldX and worldY **/
                        float[] values = result.texture.findDinamicPosition();
                        worldX = values[0];
                        worldY = values[2];
                        int multiplier = (result.level == 0 ? 1 : (int) Math.pow(2, result.level));
                        int resolutionXlevel = resolutionX0 * multiplier;
                        int resolutionYlevel = resolutionY0 * multiplier;
                        mapx = (int) ((worldX * resolutionXlevel) / Globals.MAP_WIDTH);
                        mapy = (int) ((worldY * resolutionYlevel) / Globals.MAP_HEIGHT);
                        Shadow.setShadow(data, width, height, mapx, mapy, resolutionXlevel, resolutionYlevel,
                                result.texture.getSpecialFlags());
                    }
                }
            } catch (MalformedURLException e2) {
                if (data != null) {
                    textureDataSpace.releaseSlab(data); // error - release invalid slab
                    data = null;
                }
                System.out.println("error, MalformedURLException for " + result.path);
                // e2.printStackTrace();
            } catch (FileNotFoundException e) {
                if (data != null) {
                    textureDataSpace.releaseSlab(data); // error - release invalid slab
                    data = null;
                }
                System.out.println("error, FileNotFoundException for " + result.path);
                // e.printStackTrace();
            } catch (IOException e1) {
                if (data != null) {
                    textureDataSpace.releaseSlab(data); // error - release invalid slab
                    data = null;
                }
                // e1.printStackTrace();
                System.out.println("error, IOException for " + result.path);
            }
            result.data = data;
        }
        long endTime = NTPDate.currentTimeMillis();
        // logger.log( logLevel,
        // "Loaded file "+result.sFileName+" ...  "+(loadedFrom==1?"from memory":"from disc")+"  in "+(endTime-startTime)+" ms.");
        logger.log(logLevel, "Loaded file " + result.path + " ... " + (loadedFrom == 1 ? "from memory" : "from disc")
                + " in " + (endTime - startTime) + " ms. data=" + (result.data == null ? "null" : "ok") + " joglID="
                + result.joglID);
        result.loadedFrom = loadedFrom;
        // if ( result.data==null )
        // System.out.println("error, data not available for "+result.path);
    }

    /**
     * doesn't caches in memory
     *
     * @param myMapsClassLoader
     * @param fileName
     * @param data
     * @param level
     * @return null or byte array to file
     */
    /*
     * public static byte[] loadTextureFromFile( NetResourceClassLoader myMapsClassLoader, String fileName, byte[] data,
     * int level)
     * {
     * long startTime = NTPDate.currentTimeMillis();
     * try {
     * Object [] objStream;
     * objStream = myMapsClassLoader.getResourceAsStream(fileName);
     * BufferedInputStream bis = null;
     * if ( objStream!=null && objStream.length>0 && objStream[0]!=null && objStream[0] instanceof InputStream )
     * bis = new BufferedInputStream((InputStream)objStream[0]);
     * else
     * data = null;//file not found
     * if ( bis!=null ) {
     * int width, height;
     * width = resolutionX[level];
     * height = resolutionY[level];
     * int file_length = 0;
     * for( int i=0; i<=level; i++ ) {
     * width /= texturesX[i];
     * height /= texturesY[i];
     * };
     * file_length = width*height*3;
     * if ( data == null )
     * data = new byte[file_length];
     * int nRead = bis.read( data);
     * if ( nRead!=file_length )
     * data = null;//error
     * bis.close();
     * };
     * } catch (MalformedURLException e2) {
     * data = null;
     * } catch (FileNotFoundException e) {
     * data = null;
     * } catch (IOException e1) {
     * data = null;
     * }
     * long endTime = NTPDate.currentTimeMillis();
     * logger.log( logLevel, "Loaded file "+fileName+" ...  from disc  in "+(endTime-startTime)+" ms.");
     * return data;
     * }
     */
    /**
     * loads initial textures, no optimisation required
     *
     * @param gl
     *            opengl graphical context
     * @return array of textures id that were loaded into opengl memory
     */
    /*
     * public static int[] loadInitialTextures( GL gl, int initial_level, Texture root)
     * {
     * System.out.println("Start loading initial textures");
     * //load in textures array the lowest resolution image available
     * String sliceStartName = pathToTextures;//"map0"+pathSeparator+"map1_";
     * String sliceExt = ".oct";
     * String fileName = "";
     * byte []data=null;
     * int width, height;
     * width=resolutionX[initial_level];
     * height =resolutionY[initial_level];
     * int stepX, stepY;
     * stepX=Globals.nrDivisionsX;
     * stepY=Globals.nrDivisionsY;
     * int nx=1, ny=1;
     * for ( int i=0; i<=initial_level; i++) {
     * width /= texturesX[i];
     * height /= texturesY[i];
     * stepX /= texturesX[i];
     * stepY /= texturesY[i];
     * nx*=texturesX[i];
     * ny*=texturesY[i];
     * };
     * int []textures = new int[nx*nytexturesX[initial_level]*texturesY[initial_level]];
     * gl.glGenTextures( textures.length, textures);// Create Small Textures
     * for ( int y=0; y<ny; y++)
     * for ( int x=0; x<nx; x++ ) {
     * fileName = sliceStartName+createTexturePath(initial_level, root.nWorldX+stepX*x, root.nWorldY+stepY*y)+sliceExt;
     * //System.out.println("Try to load texture: "+fileName);
     * //fileName = sliceStartName+(y+1)+"."+(x+1)+sliceExt;
     * gl.glBindTexture( GL.GL_TEXTURE_2D, textures[y*nx+x]);
     * //System.out.println("texture id: "+textures[y*texturesX[0]+x]);
     * //data = loadTextureFromJar( "zoom.jar", fileName, data);
     * data = loadTextureFromFile( fileName, data, initial_level, null);
     * //cast shadow on image
     * //System.out.println("data.length="+data.length+" width="+width+" height="+height+" resolutionX[initial_level]="+
     * resolutionX[initial_level]+" resolutionY["+initial_level+"]="+resolutionY[initial_level]);
     * Shadow.setShadow( data, width, height, x*width, y*height, resolutionX[initial_level], resolutionY[initial_level],
     * root.getSpecialFlags());
     * gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST);
     * gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST);
     * gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, 3, width, height, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, data);
     * };
     * System.out.println("End loading initial textures");
     * return textures;
     * }
     */
    /**
     * constructs an opengl texture from an byte array, with given width and height,
     * the byte array represent the 3-byte color array for an image
     *
     * @param gl
     * @param data
     *            3-byte array
     * @param width
     *            width of image
     * @param height
     *            height of image
     * @return Returns
     */
    public static int setTexture(GL gl, byte[] data) {
        ByteBuffer dest = null;
        dest = ByteBuffer.allocateDirect(data.length);
        dest.order(ByteOrder.nativeOrder());
        dest.put(data, 0, data.length);
        dest.rewind(); // <- NEW STUFF NEEDED BY JSR231
        int[] textures = new int[1];
        int width, height;
        width = OCT_WIDTH;
        height = OCT_HEIGHT;
        // System.out.println("widht="+width+" height="+height);
        long startTime = NTPDate.currentTimeMillis();
        gl.glGenTextures(textures.length, textures, 0);// Create Textures Id
        gl.glBindTexture(GL.GL_TEXTURE_2D, textures[0]);
        if (bNiceRendering) {
            // gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP);
            // gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            if (gl.glGetError() == GL.GL_INVALID_ENUM) {
                logger.log(Level.INFO, "No nice rendering, fall back to default.");
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            } else {
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            }
        } else {
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP);
        }
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, 3, width, height, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, dest);
        data = null;// free the image data, because it is now in opengl memory
        long endTime = NTPDate.currentTimeMillis();
        logger.log(logLevel, "Generated OpenGL texture with id=" + textures[0] + " in " + (endTime - startTime)
                + " ms.");
        return textures[0];
    }

    /**
     * constructs the initial tree of textures
     *
     * @param crtMonitor
     *            monitoring unit for loading all textures
     * @return tree's root
     */
    public static Texture constructInitialTree(ChangeRootTexture crtMonitor, int initial_level) {
        // NetResourceClassLoader.setDebugLevel(true);

        Texture root = null;
        int treeid = 0;
        // get the tree id to create a unique path for textures
        if (crtMonitor != null) {
            treeid = crtMonitor.getTreeID();
        } else {
            treeid = JoglPanel.globals.nTreeID;
        }
        // int initial_level = nInitialLevel;
        // int[] textures = loadInitialTextures( gl, initial_level);
        synchronized (syncObject_Texture) {
            // textureDataSpace.invalidateAllSlabs();
            root = new Texture(0, S_NONE | S_SHOULD_LOAD | S_LOADED | S_SET | S_ALWAYS);
            // root.setCoordinates( -180, 180, 90, -90);
            root.setSpecialFlags((Shadow.bShowNight ? 0 : S_SPECIAL_NO_NIGHT)
                    | (Shadow.bHideLights ? S_SPECIAL_NO_LIGHTS : 0));
            // JoglPanel.globals.root.texture_id = 0;
            int nx = 1, ny = 1;
            int stepX, stepY;
            // int width, height;
            // width=resolutionX[initial_level];
            // height =resolutionY[initial_level];
            stepX = Globals.nrDivisionsX;
            stepY = Globals.nrDivisionsY;
            for (int i = 0; i <= initial_level; i++) {
                // width /= texturesX[i];
                // height /= texturesY[i];
                if (i == 0) {
                    stepX /= texturesX0;
                    stepY /= texturesY0;
                    nx *= texturesX0;
                    ny *= texturesY0;
                } else {
                    stepX /= 2;
                    stepY /= 2;
                    nx *= 2;
                    ny *= 2;
                }
            }
            if (crtMonitor != null) {
                crtMonitor.setNoTotalTextures(nx * ny);
            }
            root.children = new Texture[nx * ny];
            Texture child;
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    // set world position and texture id for each children
                    // for first children in tree set always flag to stay forever
                    child = new Texture(0, root.nWorldX + (stepX * x), root.nWorldY + (stepY * y), stepX, stepY, S_NONE
                            | S_ALWAYS/*
                                      * |
                                      * S_SHOULD_LOAD
                                      * |
                                      * S_LOADED
                                      * |
                                      * S_SET
                                      */);
                    root.children[(y * nx) + x] = child;
                    child.setParent(root);
                    child.setStatus(S_SHOULD_LOAD);
                    String path = child.textCreatePathDyn(initial_level).toString();
                    // for textures at level 0, load them from resources
                    if (initial_level == 0) {
                        path = "*" + path;
                    }

                    TextureLoadJobResult tres = new TextureLoadJobResult();
                    tres.texture = child;
                    tres.path = path;
                    tres.level = initial_level;
                    tres.crtMonitor = crtMonitor;
                    tres.treeID = treeid;
                    texturesToLoad.offer(tres);
                    // by adding, an automatic notification is done.
                }
            }
        }
        return root;
    }

    // private static int nCounterZoomChangedBetweenRepaints=0;
    /**
     * draws the tree of textures to cover the map
     *
     * @param gl
     *            opengl graphical context
     */
    public static void drawTree(GL2 gl) {
        /*
         * synchronized (syncObject_Texture) {
         * if ( bInDrawFunction )
         * return;
         * bInDrawFunction = true;
         * if ( bInComputeFunction ) {
         * try {
         * Texture.syncObject_Texture.wait();
         * } catch (InterruptedException e) {
         * }
         * };
         * }
         */
        gl.glColor3f(1.0f, 1.0f, 1.0f);
        // starts the drawing from the root
        // System.out.println("startx="+startx+" endx="+endx+" starty="+starty+" endy="+endy);
        synchronized (syncObject_Texture) {
            // System.out.println("\n\nzoom changed between repaints counter: "+nCounterZoomChangedBetweenRepaints+"\n\n");
            // nCounterZoomChangedBetweenRepaints = 0;
            // System.out.println("syncObject_Texture begin");
            // JoglPanel.globals.root.textDraw( gl, -1);
            for (Texture element : JoglPanel.globals.root.children) {
                if (element != null) {
                    element.textDraw(gl, nInitialLevel);
                    // System.out.println("syncObject_Texture end");
                }
            }
        }
        ;
        /*
         * synchronized (syncObject_Texture) {
         * bInDrawFunction=false;
         * syncObject_Texture.notifyAll();
         * };
         */
    }

    /*
     * public static void zoomChanged( Texture new_root, int new_level)
     * {
     * for ( int i=0; i<new_root.children.length; i++)
     * new_root.children[i].textZoomChanged( new_level);
     * }
     */
    /**
     * computes the view area and starts the recursive process of setting
     * slices for texture loading
     */
    public static void zoomChanged() {
        /** this should be computed in UserInput class */
        /** end should section */
        // compute new global desired level for sphere projection
        // to make a stronger condition, check also the angle to be
        if ((JoglPanel.globals.globeRadius != -1) && (JoglPanel.globals.mapAngle == 90)) {
            float depth = (float) JoglPanel.globals.EyePosition.getRadius() - JoglPanel.globals.globeRadius;
            if (JoglPanel.globals.charPressed == 'D') {
                System.out.println("current depth: " + depth);
            }
            int desiredLevel = nInitialLevel;
            if (depth > 0) {
                for (int i = nInitialLevel/* 0 */; i < depthZ.length; i++) {
                    if (JoglPanel.globals.charPressed == 'D') {
                        System.out.println("depthZ[" + i + "]=" + depthZ[i]);
                    }
                    if (depth > (depthZ[i] * 2)) {
                        break;
                    }
                    desiredLevel = i + 1;
                    if (JoglPanel.globals.charPressed == 'D') {
                        System.out.println("depth: " + depth + "depthZ[" + i + "]=" + depthZ[i] + " desiredLevel=" + i);
                    }
                }
            }
            if (JoglPanel.globals.charPressed == 'D') {
                System.out.println("=> desired level: " + desiredLevel);
            }
            JoglPanel.globals.globalSphereDesiredLevel = desiredLevel;
        }
        /** compute intersection params for any projection */
        // Globals.computeIntersectionParams();
        // System.out.println("eye axis: ("+JoglPanel.globals.vEyeAxis.getX()+","
        // +JoglPanel.globals.vEyeAxis.getY()+","
        // +JoglPanel.globals.vEyeAxis.getZ()+")");

        synchronized (syncObject_Texture) {
            // nCounterZoomChangedBetweenRepaints++;
            for (Texture element : JoglPanel.globals.root.children) {
                element.textZoomChanged(nInitialLevel);
            }
        }
        // call to redraw scene for maybe there where textures unloaded
        JoglPanel.globals.canvas.repaint();
    }

    private float[] findDinamicPosition() {
        /**
         * contains on first position the x world coordinate of parent,
         * on second the cell width for child
         * and on next two positions the equivalent as above for y axis
         */
        float[] values;
        if (bDynamicGrid) {
            values = parent.findDinamicPosition();
            /**
             * as the function returned values regarding the parent (position
             * and dimensions), to get to child texture some adjustments have
             * to be done:
             * cell width and height are divided to number of children on x and y,
             * and the position of child is computed based on that of parent
             * and on position inside parent grid
             */

            // System.out.println("worldX: "+this.nWorldX+" worldY: "+this.nWorldY+" parent's children: "+parent.children+" parent grid: "+(parent.grid==null?"null":"not null")+" parent grid[0]: "+(parent.grid==null?"null":(parent.grid[0]==null?"null":"not null"))+" parent grid[0][0]: "+(parent.grid==null?"null":(parent.grid[0]==null?"null":(parent.grid[0][0]==null?"null":"not null"))));
            values[1] /= (parent.grid[0][0].length - 1);// child width is smaller than parent's
            values[0] += nWorldX * values[1];// get from parent to child on x
            values[3] /= (parent.grid[0].length - 1);// get number of rows (children on y axis)
            values[2] += nWorldY * values[3];// on y
            // System.out.println("children on x="+parent.grid[0][0].length+" children on y="+parent.grid[0].length+" absWorldX="+values[0]+" absWorldY="+values[2]+" absWidth="+values[1]+" absHeight="+values[3]);
        } else {
            values = new float[4];
            values[0] = nWorldX * Globals.divisionWidth;
            values[1] = Globals.divisionWidth;
            values[2] = nWorldY * Globals.divisionHeight;
            values[3] = Globals.divisionHeight;
            // System.out.println(" absWorldX="+values[0]+" absWorldY="+values[2]+" absWidth="+values[1]+" absHeight="+values[3]);
        }
        return values;
    }

    /**
     * creates a worker for loading the image for a texture
     * Sep 29, 2005 6:08:59 PM - mluc<br>
     */
    public static void loadNewTextures1() {
        TextureLoadJobResult tres;
        while (true) {
            try {
                tres = texturesToLoad.take();
            } catch (InterruptedException iex) {
                continue;
            }
            if (tres.texture == null) {
                continue;
            }
            boolean bLoad = false;
            synchronized (syncObject_Texture) {
                // check to see if the texture still should be loaded
                // but first check to see if texture still exists
                if (tres.texture.checkStatus(S_DEREFERENCED)) {
                    tres.texture = null;
                } else if (tres.texture.checkStatus(S_SHOULD_LOAD)) {
                    bLoad = true;
                }
            }
            if (bLoad) {
                logger.log(logLevel, "try to load file: " + pathToTextures + tres.path + Texture.sMapExt);
                JoglPanel.globals.tpOctDownload.execute(new TextureLoadJob(tres));
            }
        }
    }

    private boolean textIsParentAtMaxLevel() {
        if (parent != null) {
            if (parent.checkStatus(S_MAX_LEVEL)) {
                return true;
            }
            return parent.textIsParentAtMaxLevel();
        }
        return false;
    }

    /**
     * 1) loads image into memory<br>
     * 2) set it for texture object to be transformed into opengl texture
     * Sep 29, 2005 6:07:40 PM - mluc<br>
     *
     * @param result
     */
    public static void loadNewTextures2(TextureLoadJobResult result) {
        Texture t = result.texture;
        // and then set it for tranformation into opengl texture
        boolean bAdded2Set = false;
        if (t == null) {
            if (result.crtMonitor != null) {
                // (crtMonitor=(ChangeRootTexture)result.tuplu[3])!=null )
                result.crtMonitor.notifyNewTextureSet();
            }
            return;
        }
        boolean bShouldLoad = true;
        // check if texture still valid for loading
        synchronized (syncObject_Texture) {
            // it could have been modified, so check again
            if (t.checkStatus(S_DEREFERENCED)) {
                result.texture = null;
                t = null;
                bShouldLoad = false;
            } else if (t.textIsParentAtMaxLevel()) {
                bShouldLoad = false;
            }
        }
        if (bShouldLoad) {
            // first load image into memory
            loadTextureFromFile(result);
            t.resultID = result.getResultID();
            // set the status for slice
            synchronized (syncObject_Texture) {
                // set max level if the case
                if (result.isMaxLevel) {
                    t.setStatus(S_MAX_LEVEL);
                    // System.out.println("texture "+t.resultID+" at max level");
                }
                // it could have been modified, so check again
                if (t.checkStatus(S_DEREFERENCED)) {
                    result.texture = null;
                    t = null;
                } else if (t.checkStatus(S_SHOULD_LOAD)) {
                    // System.out.println("data: "+data);
                    if ((result.data == null) && (result.errReason == TextureLoadJobResult.ERR_ISDOWNLOADING)) {
                        logger.log(logLevel, "readd job to queue: " + result.path);
                        texturesToLoad.offer(result);
                        return;
                    } else if ((result.data == null) && (result.joglID == 0)) {
                        t.setStatus(S_FILE_NOT_FOUND);
                    } else {
                        t.setStatus(S_LOADED);

                        texturesToSet.offer(result);
                        bAdded2Set = true;
                    }
                }
            }
        }
        if (!bAdded2Set) {// if texture will not get to set thread, and it will be discarded, increase the number of
            // monitorized textures
            // this texture was not set anymore, so we can release it
            if (result.data != null) {
                // textureDataSpace.releaseSlab( result.getResultID(), result.data, true);
                textureDataSpace.releaseSlab(result.data);
            }

            // inform monitoring unit that a new texture was loaded and set into gl memory
            if (result.crtMonitor != null) {
                // (crtMonitor=(ChangeRootTexture)result.tuplu[3])!=null )
                result.crtMonitor.notifyNewTextureSet();
            }
        }
        result = null;
        JoglPanel.globals.canvas.repaint();
    }

    /**
     * checks for new textures to load that, at the moment of checking
     * are still set for loading, and tries to load them
     */
    /*
     * public static void loadNewTextures()
     * {
     * boolean bTexturesLoaded;
     * while ( true ) {
     * //wait for new textures
     * synchronized (texturesToLoad) {
     * while(texturesToLoad.isEmpty()){
     * try{
     * texturesToLoad.wait();
     * } catch(InterruptedException ex) {
     * }
     * }
     * }
     * synchronized ( texturesToLoad ) {
     * if ( texturesToLoad.isEmpty() ) {
     * try {
     * texturesToLoad.wait();
     * } catch (InterruptedException ex) {
     * }
     * };
     * };
     * bTexturesLoaded = false;
     * //vector with source of texture
     * int vbType[] = new int[1];
     * // int loadedFrom;
     * //if there is no texture, catch the exception
     * try {
     * while ( true ) {//check all available in the vector
     * Object[] tuplu = (Object[])texturesToLoad.get(0);
     * Texture t = (Texture)tuplu[0];
     * if ( t==null )
     * break;
     * //System.out.println("texture to load: "+path);
     * boolean bLoad = false;
     * boolean bAdded2Set = false;
     * synchronized( syncObject_Texture ) {
     * //check to see if the texture still should be loaded
     * //but first check to see if texture still exists
     * if ( t.checkStatus(S_DEREFERENCED) ) {
     * tuplu[0] = null;
     * t = null;
     * } else if ( t.checkStatus(S_SHOULD_LOAD) )
     * bLoad = true;
     * }
     * if ( bLoad ) {
     * byte[] data = null;
     * int level = ((Integer)tuplu[2]).intValue();
     * //data = loadTextureFromJar( "zoom.jar", pathToTextures+path+".oct", data);
     * String path = (String)tuplu[1];
     * logger.log( logLevel, "try to load file: "+pathToTextures+path+".oct");
     * //System.out.println( "try to load file: "+pathToTextures+path+".oct");
     * data = loadTextureFromFile( JoglPanel.globals.myMapsClassLoader, pathToTextures+path+".oct", data, level,
     * vbType);
     * //data is clean, copy and apply shadow to it
     * byte []data2 = null;
     * //make a copy of image to have a clean copy to memory, if still there, so that... when changing
     * //time the texture would be clean. Hm. Very unprobable, but should be easier to work with
     * if ( data!=null && data.length>0 ) {
     * data2 = new byte[data.length];
     * System.arraycopy( data, 0, data2, 0, data.length);
     * };
     * //System.out.println("loaded from: "+vbType[0]);
     * // loadedFrom = vbType[0];
     * //set the status for slice
     * synchronized ( syncObject_Texture ) {
     * //set max level if the case
     * if ( (vbType[0]&2) > 0 ) {
     * t.setStatus(S_MAX_LEVEL);
     * //System.out.println("texture "+path+" at max level.");
     * };
     * //System.out.println("status: "+t.status+" texture to load2: "+path);
     * //it could have been modified, so check again
     * if ( t.checkStatus(S_DEREFERENCED) ) {
     * tuplu[0] = null;
     * t = null;
     * } else if ( t.checkStatus(S_SHOULD_LOAD) ) {
     * //System.out.println("data: "+data);
     * if ( data2 == null || data.length==0 ) {
     * //t.resetStatus();
     * t.setStatus(S_FILE_NOT_FOUND);
     * // System.out.println("file not found?!?");
     * } else {
     * //t.resetStatus();
     * t.setStatus(S_LOADED);
     * //codification: tuplu[2] contains on first 8 bits the level, and on following 8 bits the storage
     * // from where the image was loaded
     * tuplu[2] = Integer.valueOf(level);//+((0xFF&loadedFrom)<<8));
     * //check to see if parent still has children before trying to take grid from it
     * //parent can have children==null although this is it's children, so
     * //this child should not exist, and no image should be generate for it
     * //cast shadow on image
     * //if ( t.parent.children!=null && loadedFrom == 0 ) {
     * int width, height;
     * width = resolutionX[level];
     * height = resolutionY[level];
     * for( int i=0; i<=level; i++) {
     * width /= texturesX[i];
     * height /= texturesY[i];
     * };
     * int mapx, mapy;
     * float worldX, worldY;
     *//** compute worldX and worldY **/
    /*
     * float[] values = t.findDinamicPosition();
     * worldX = values[0];
     * worldY = values[2];
     * // worldX = t.nWorldX*Globals.divisionWidth;
     * // worldY = t.nWorldY*Globals.divisionHeight;
     * mapx = (int)(worldX*resolutionX[level]/Globals.MAP_WIDTH);
     * mapy = (int)(worldY*resolutionY[level]/Globals.MAP_HEIGHT);
     * //
     * System.out.println("set shadow for texture: width="+width+" height="+height+" mapx="+mapx+" mapy="+mapy+" resX="
     * +resolutionX[level]+" resY="+resolutionY[level]);
     * //System.out.println("set shadow for texture@ "+data);
     * if ( !t.checkSpecialFlag( S_SPECIAL_NO_NIGHT) )
     * Shadow.setShadow( data2, width, height, mapx, mapy, resolutionX[level], resolutionY[level], t.getSpecialFlags());
     * else
     * Shadow.setBrightness( data2, width, height, 1.7f);
     * //}
     * //
     * //set for loading into opengl
     * tuplu[1] = data2;
     * texturesToSet.add(tuplu);
     * bAdded2Set = true;
     * };
     * }
     * }
     * };
     * //remove the tuplu either way, so that the thread could be correctly notified
     * texturesToLoad.remove(tuplu);
     * if ( !bAdded2Set ) {//if texture will not get to set thread, and it will be discarded, increase the number of
     * monitorized textures
     * ChangeRootTexture crtMonitor=null;
     * //inform monitoring unit that a new texture was loaded and set into gl memory
     * if ( tuplu.length>3 && tuplu[3] instanceof ChangeRootTexture && (crtMonitor=(ChangeRootTexture)tuplu[3])!=null )
     * crtMonitor.notifyNewTextureSet();
     * }
     * tuplu = null;
     * bTexturesLoaded = true;
     * }
     * } catch (ArrayIndexOutOfBoundsException ex) {
     * }
     * if ( bTexturesLoaded )//new textures were loaded, so show them
     * JoglPanel.globals.canvas.repaint();
     * }
     * }
     */
    /**
     * checks for loaded from file textures and updates the tree to show them,
     * instead of the old ones<br>
     * if there are sufficient child textures, parent texture is unloaded
     *
     * @param gl
     */
    public static void setTreeTextures(GL gl) {
        int nCountSetTextures = 0, nCountRealSet = 0, nCountBackgroundSet = 0;
        long lStartTime, lEndTime;
        lStartTime = NTPDate.currentTimeMillis();
        TextureLoadJobResult result;
        while ((nCountRealSet < 10) && ((result = texturesToSet.poll()) != null)) {
            nCountSetTextures++;
            if (result.crtMonitor != null) {
                nCountBackgroundSet++;
            }
            // generate opengl texture from byte array if it isn't already in jogl space
            int t_id;
            if (result.joglID > 0) {
                logger.log(logLevel, "reusing texture_id " + result.joglID);
                t_id = result.joglID;
            } else {
                if (result.crtMonitor == null) {
                    nCountRealSet++;
                }
                t_id = setTexture(gl, result.data);
                logger.log(logLevel, "generating jogl texture_id " + t_id);
            }
            if (t_id > 0) {
                synchronized (syncObject_Texture) {// update the tree synchronized
                    // System.out.println("set texture id: "+t_id);
                    Texture t = result.texture;
                    if (t != null) {
                        if (t.checkStatus(S_DEREFERENCED)) {
                            t = null;
                            result.texture = null;
                        } else {
                            t.texture_id = t_id;
                            t.leftT = 0f;
                            t.rightT = 1.0f;
                            t.bottomT = 0f;
                            t.topT = 1f;
                            // t.resetStatus();
                            t.setStatus(S_SET);
                            // set all children to the new parent texture, if the case
                            if (t.children != null) {
                                // set for existing children that don't have their own texture this node's parent
                                // texture
                                for (Texture element : t.children) {
                                    if (element != null /* && !children[i].checkStatus(S_SET) */) {
                                        element.textSetParentTexture();
                                    }
                                }
                            }
                            // check to see if parent texture should be cleared
                            // recompute child textures parent id
                            if (t.getParent() != null) {
                                t.getParent().textUnsetParentTexture(gl);
                            }
                        }
                    }
                }
            }
            // textureDataSpace.releaseSlab( result.getResultID(), result, true);
            if (result.data != null) {
                textureDataSpace.releaseSlab(result.data);
            }
            // ChangeRootTexture crtMonitor=null;
            // inform monitoring unit that a new texture was loaded and set into gl memory
            if (result.crtMonitor != null) {
                // (crtMonitor=(ChangeRootTexture)tuplu[3])!=null )
                result.crtMonitor.notifyNewTextureSet();
            }
        }
        lEndTime = NTPDate.currentTimeMillis();
        logger.log(logLevel, "set textures took " + (lEndTime - lStartTime) + "ms and tested " + nCountSetTextures
                + " textures, from which " + nCountBackgroundSet + " were in background and " + nCountRealSet
                + " were really set.");
    }

    /**
     * deletes from memory invisible textures ( not in the view frustum )
     * and not on the correct depth<br>
     * <b>Updated to:</b>This textures have already been put into an array texturesToUnSet, and
     * the only thing to be done is to delete them from opengl one by one.
     *
     * @param gl
     */
    public static void unsetTreeTextures(GL gl) {
        int nCountSetTextures = 0;
        long lStartTime, lEndTime;
        lStartTime = NTPDate.currentTimeMillis();
        int[] textures = new int[1];
        // System.out.println("unsetTreeTextures...");
        JoGLCachedTexture jct;
        // get the textures to be deleted, i.e. expired from cache
        textureDataSpace.offerTexturesToDelete(texturesToUnSet);

        while ((jct = texturesToUnSet.poll()) != null) {
            nCountSetTextures++;
            if (gl.glIsTexture(jct.joglID)) {// this check also includes (children[i].texture_id!= tex_id &&
                // children[i].texture_id>0)
                textures[0] = jct.joglID;
                gl.glDeleteTextures(1, textures, 0);
                logger.log(logLevel, "unsetTreeTextures -> Delete texture with id: " + jct.joglID);
                // System.out.println("unsetTreeTextures -> Delete texture with id: "+jct.joglID);
            }
        }
        lEndTime = NTPDate.currentTimeMillis();
        logger.log(logLevel, "unset textures took " + (lEndTime - lStartTime) + "ms and deleted " + nCountSetTextures
                + " textures");
        // synchronized ( syncObject_Texture ) {
        // for ( int i=0; i<Main.globals.root.children.length; i++)
        // Main.globals.root.children[i].unsetTexture2( gl, 0);
        // }
    }

    /**
     * draws this texture or its children<br>
     * first check to see if any part of texture is visible<br>
     * then check to see if this node has children<br>
     * then check to see if has an valid id... ( optional, if second is false )<br>
     * each condition stops the traversal<br>
     *
     * @param gl
     *            opengl rendering context
     * @param currentLevel
     *            indicates the current level reached in the tree, neccessary
     *            to compute position of child node if it is not defined
     */
    private void textDraw(GL2 gl, int currentLevel) {
        /**
         * 0. a small visibility test to reduce algorithm complexity, and to avoid
         * duplicates (one on each side of the sphere) is:
         * if ( globeRadius!=-1 ) then
         * dist(p,e) must be less or equal to sqrt( d(e,C)^2+gR^2 )
         * the visible textures computation can be done for any situation with a
         * general algorithm, but is complicated.
         * What the algorithm does: determines if a point is in the visible frustum
         * (that is, in the piramid formed by the near and far plane).
         * The steps to be taken are:
         * 1. construct the eye direction vector: d and normalize it
         * 2. construct the eye normal vector: n and normalize it;
         * - reprezents the y axis of the screen
         * 3. construct the second axis of the projection plane ( screen):
         * m = d x n => m has the direction to the right ( x axis ),
         * - m is normalized as cross product of two normalized vectors
         * 4. construct the vector to the studied point, from eye: p
         * 5. compute p projections on the n,m axis:
         * pn = p.n (dot product between the two vectors)
         * pm = p.m (dot product between the two vectors)
         * 6. compute dimensions of visible frustum for the distance to the point:
         * 6.1 compute the distance from point to the plane:
         * dp = p.d (dot product)
         * 6.2 compute fx=fm, fy=fn, the limits of the frustum:
         * fx = dp*tg(alpha/2)
         * fy = fx/aspect
         * where alpha is the view frustum angle
         * and aspect is the raport between width and height of the window
         * 7. check if pn is contained in (-fy,fy) and pm in (-fx,fx)
         * => transformed in: consider the four points that make the texture
         * if at least 1 point's pm is smaller than its fm and at least
         * 1 point's pm is greater than its -fm and same condition for n axis,
         * then this texture is visible
         */
        // frustum checking only for plane projection
        if ( // bIsVisible ) { //
        textVisible(nWorldX, nWorldY, nWidth, nHeight, bDynamicGrid, parent, this)) {
            // if ( worldX<Main.globals.endFx && worldX+nWidth*Globals.divisionWidth>Main.globals.startFx &&
            // worldY>Main.globals.endFy && worldY-nHeight*Globals.divisionHeight<Main.globals.startFy ) {//texture is
            // visible
            // System.out.println("nWidth="+nWidth+" nHeight="+nHeight+ "divisionWidth="+Globals.divisionWidth);
            // System.out.println("worldX="+worldX+" worldY="+worldY+
            // " worldXend="+(worldX+nWidth*Globals.divisionWidth)+" worldYend="+(worldY-nHeight*Globals.divisionHeight));
            // System.out.println("startFx="+Main.globals.startFx+" endFx="+Main.globals.endFx+" startFy="+Main.globals.startFy+
            // " endFy="+Main.globals.endFy);
            // if texture visible, it means will be rendered, so update last Access Time
            // lastAccessTime = new Date();
            Texture[] local_children = children;
            if (local_children != null) {// has children
                currentLevel++;
                // System.out.println("has children");
                for (int i = 0; i < local_children.length; i++) {
                    if (local_children[i] != null) {
                        // System.out.println("children["+i+"] autodraw");
                        local_children[i].textDraw(gl, currentLevel);
                    } else if (texture_id != 0) {// I should draw the parent texture and texture is valid
                        // System.out.println("parent draw children["+i+"]");
                        // totally ineficient, must be put some where else
                        int wX, wY, w, h;
                        int nx/* = texturesX[currentLevel] */;
                        int ny/* = texturesY[currentLevel] */;
                        if (currentLevel == 0) {
                            nx = texturesX0;
                            ny = texturesY0;
                        } else {
                            nx = ny = 2;
                        }
                        float lT, rT, tT, bT;
                        float tx = (rightT - leftT) / nx;
                        float ty = (topT - bottomT) / ny;
                        lT = leftT + (tx * (i % nx));
                        rT = lT + tx;
                        int ipeny = (i / ny);
                        tT = topT - (ty * ipeny);
                        bT = tT - ty;
                        if (!gl.glIsTexture(texture_id)) {
                            // logger.log(logLevel, "ERROR! children draw: not a valid texture id1 ("+texture_id+")");
                            gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.no_texture_id);
                            lT = 0;
                            rT = 1;
                            bT = 0;
                            tT = 1;
                        } else {
                            gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
                        }
                        // gl.glBegin(GL.GL_TRIANGLES);
                        if ((nWidth == 1) || (nHeight == 1)) {
                            if (grid != null) {
                                // the second checking is futile because if
                                // nWidth is one, or nHeight is one and this node
                                // has children, it must also have the grid initialized
                                w = 1;
                                h = 1;
                                wX = (i % nx);
                                wY = (i / nx);
                                // logger.log(logLevel, "draw missing dynamic child["+i+"]");
                                drawMapSlice(gl, grid, wX, wY, w, h, lT, bT, rT, tT);
                            } else {
                                logger.log(logLevel, "ERROR! children draw: No position available to draw at.");// I
                                // should
                                // draw
                                // the
                                // parent
                                // texture
                            }
                        } else {// dynamic node, so use own grid to draw children
                            w = nWidth / nx;
                            h = nHeight / ny;
                            wX = nWorldX + (w * (i % nx));
                            wY = nWorldY + (h * (i / nx));
                            // logger.log(logLevel, "draw missing static child["+i+"]");
                            drawMapSlice(gl, Globals.points, wX, wY, w, h, lT, bT, rT, tT);
                        }
                        // gl.glEnd();
                    } else {
                        logger.log(logLevel, "ERROR! children draw: No parent texture available to draw.");// I should
                        // draw the
                        // parent
                        // texture
                    }
                }
            } else if (texture_id != 0) {// texture is valid
                float lT, rT, tT, bT;
                lT = leftT;
                bT = bottomT;
                rT = rightT;
                tT = topT;
                if (!gl.glIsTexture(texture_id)) {
                    // logger.log(logLevel, "ERROR! children draw: not a valid texture id2 ("+texture_id+")");
                    gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.no_texture_id);
                    lT = 0;
                    rT = 1;
                    bT = 0;
                    tT = 1;
                } else {
                    gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
                }
                // gl.glBegin(GL.GL_TRIANGLES);
                if (!bDynamicGrid) {
                    drawMapSlice(gl, Globals.points, nWorldX, nWorldY, nWidth, nHeight, lT, bT, rT, tT);
                } else {// node is on parent grid
                    // System.out.println("draw dynamic node");
                    drawMapSlice(gl, parent.grid, nWorldX, nWorldY, nWidth, nHeight, lT, bT, rT, tT);
                }
                // gl.glEnd();
                // logger.log(logLevel, "Draw texture with id: "+texture_id);
            } /*
              * else
              * logger.log(logLevel, "ERROR! parent draw: No texture available to draw.");
              */
        } else {
            // logger.log(logLevel, "not draw texture "+texture_id+" worldx="+nWorldX+" worldy="+nWorldY);
        }
    }

    /**
     * Sep 13, 2005 12:18:00 PM - mluc<br>
     * A new algorithm for checking the visibility of a texture, that is an area
     * on the map that occupies a certain amount of longitudinal degrees and
     * also latitudinal.<br>
     * it checks for intersection of rectangle given by functions' params
     * and the circle determined by global intersection point and global intersection radius.
     *
     * @param longS
     *            start longitude of texture's rectangle
     * @param longE
     *            end longitude of texture's rectangle
     * @param latS
     *            start latitude of texture's rectangle
     * @param latE
     *            end latitude of texture's rectangle
     * @param parent
     *            parent texture
     * @param text
     *            the texture that is searched for visibility
     * @return boolean value to indicate if we have intersection -> texture is visible
     */
    /*
     * private static boolean textVisible2( float longS, float longE, float latS, float latE, Texture parent, Texture
     * text, float[] coords3D, float[] coords2D)
     * {
     * if ( parent==null )
     * return false;
     * if ( text!=null && !text.bCheckVisibility )
     * return text.bIsVisible;
     * if ( text!=null ) text.bCheckVisibility = false;
     * if ( text!=null ) text.bIsVisible = true;
     * return true;
     *//**
       * transform (long,lat) from coordinates relative to earth rotation axis (y)
       * to vEyeAxis (vector from map center to eye ).
       * For that, transform to chartezian coordinates (x,y,z) and then
       * to (long,lat) relative to vEyeAxis.
       */
    /*
     * coords3D = Globals.point2Dto3D( latS, longS, coords3D);
     * System.out.println("old coords for (longS,latS)=("+longS+","+latS+")");
     * coords2D = Globals.point3Dto2Daxis( coords3D[0], coords3D[1], coords3D[2], coords2D);
     * longS = coords2D[0];
     * latS = coords2D[1];
     * System.out.println("new coords (longS,latS)=("+longS+","+latS+")");
     * System.out.println("old coords for (longE,latE)=("+longE+","+latE+")");
     * coords3D = Globals.point2Dto3D( latE, longE, coords3D);
     * coords2D = Globals.point3Dto2Daxis( coords3D[0], coords3D[1], coords3D[2], coords2D);
     * longE = coords2D[0];
     * latE = coords2D[1];
     * System.out.println("new coords (longE,latE)=("+longE+","+latE+")");
     * //check for intersection or alternate location
     * if ( Globals.checkIntersectionCircleRectangle(0,0,
     * JoglPanel.globals.fRadiusIntersection,
     * longS, latS, longE, latE)
     * ) {
     * System.out.println("texture ("+longS+","+latS+")x("+longE+","+latE+") is visible");
     * if ( text!=null ) text.bIsVisible = true;
     * return true;
     * };
     * System.out.println("texture ("+longS+","+latS+")x("+longE+","+latE+") is NOT visible");
     * if ( text!=null ) text.bIsVisible = false;
     * return false;
     * }
     */
    /**
     * tests to see if a texture starting in grid at (x,y) and having nx units on x axis
     * and ny units on y axis, is visible or not (that is in the view frustum === the pyramid)
     *
     * @param x
     * @param y
     * @param nx
     * @param ny
     * @param bDynamic
     *            indicates if the test is done for a static grid texture, or a dynamic one
     * @return boolean value to indicate visibility
     */
    private static boolean textVisible(int x, int y, int nx, int ny, boolean bDynamic, Texture parent, Texture text/*
                                                                                                                   * ,
                                                                                                                   * boolean
                                                                                                                   * bNotUsed
                                                                                                                   */) {
        if (bDynamic && (parent == null)) {
            // logger.log(logLevel, "Texture x="+x+" y="+y+" is dynamic and doesn't have a parent");
            return false;// if this is a dynamic texture, but there is no parent, it must be an error, so
            // return false
        }
        if ((text != null) && !text.bCheckVisibility) {
            // logger.log(logLevel,
            // "Texture x="+x+" y="+y+" and id="+text.texture_id+" doesn't check visibility and is "+(text.bIsVisible?"":"not")+" visible");
            return text.bIsVisible;
        }
        if (text != null) {
            text.bCheckVisibility = false;
        }

        if ((JoglPanel.globals.globeRadius != -1) && (JoglPanel.globals.mapAngle == 90)) {
            // use second type of checking
        }

        // set the grid from where points are taken: static or dynamic
        float[][][] grid_pointer = Globals.points;
        if (bDynamic) {
            grid_pointer = parent.grid;
        }
        // System.out.println("x="+x+" y="+y+" nx="+nx+" ny="+ny);
        /**
         * 0. a small visibility test to reduce algorithm complexity, and to avoid
         * duplicates (one on each side of the sphere) is:
         * if ( globeRadius!=-1 ) then
         * dist(p,e) must be less or equal to sqrt( d(e,C)^2-gR^2 )
         * where gR is the virtual globe radius;
         * the level 1 texture, that occupies each half of the globe, will always be visible.
         * this visibility test is deprecated, replace with:
         * if all points for a piece of texture are further than d(e,c)^2+gR^2,
         * then it is hidden
         * the visible textures computation can be done for any situation with a
         * general algorithm.
         * What the algorithm does: determines if a point is in the visible frustum
         * (that is, in the piramid formed by the near and far plane).
         * The steps to be taken are:
         * 1. construct the eye direction vector: d and normalize it
         * 2. construct the eye normal vector: n and normalize it;
         * - reprezents the y axis of the screen
         * 3. construct the second axis of the projection plane ( screen):
         * m = d x n => m has the direction to the right ( x axis ),
         * - m is normalized as cross product of two normalized vectors
         * 4. construct the vector to the studied point, from eye: p
         * 5. compute p projections on the n,m axis:
         * pn = n.p (dot product between the two vectors)
         * pm = m.p (dot product between the two vectors)
         * 6. compute dimensions of visible frustum for the distance to the point:
         * 6.1 compute the distance from point to the plane:
         * dp = d.p (dot product)
         * 6.1.0 if all 4 points(corners) have projection <=0 then texture is invisible
         * 6.2 compute fx=fm, fy=fn, the limits of the frustum:
         * fx = dp*tg(alpha/2)
         * fy = fx/aspect
         * where alpha is the view frustum angle
         * and aspect is the raport between width and height of the window
         * 7. check if pn is contained in (-fy,fy) and pm in (-fx,fx)
         * => transformed in: consider the four points that make the texture
         * if at least 1 point's pm is smaller than its fm and at least
         * 1 point's pm is greater than its -fm and same condition for n axis,
         * then this texture is visible
         * 24-06-05 new update: check to see if points are put so that although none of
         * them are visible, the texture would be visible
         * 19-09-05 update: the current checking is sufficient to assure that a
         * texture will be visible, even more, some invisible textures will be
         * considered visible.
         */
        int nStepX, nStepY;
        // for dynamic grid or plane projection check only heads
        if (bDynamic || (JoglPanel.globals.globeRadius == -1)) {
            nStepX = nx;
            nStepY = ny;
        } else {
            nStepX = 1;
            nStepY = 1;
        }
        // for each point repeat:
        // 4. vector from eye to point
        VectorO Vp1 = new VectorO();
        VectorO Vp2 = new VectorO();
        VectorO Vp3 = new VectorO();
        VectorO Vp4 = new VectorO();
        // float []coord;
        float coordX, coordY, coordZ;
        boolean bUseLast;// use last computed vectors for new iteration
        // recheck visibility test using latitude and longitude -> invalid test, will never work!!!!!
        // 1. eye direction vector
        VectorO Vd = JoglPanel.globals.EyeDirection;// new VectorO(Main.globals.EyeDirection[0],
        // Main.globals.EyeDirection[1], Main.globals.EyeDirection[2]);
        // Vd.Normalize();
        // 2. eye normal vector
        VectorO Vn = JoglPanel.globals.EyeNormal;// new VectorO(Main.globals.EyeNormal[0], Main.globals.EyeNormal[1],
        // Main.globals.EyeNormal[2]);
        // Vd.Normalize();
        // 3. second axis
        VectorO Vm = Vd.CrossProduct(Vn);
        // compute maximal distance
        float maxDist = 0;
        if (JoglPanel.globals.globeRadius != -1) {
            maxDist = (JoglPanel.globals.globeRadius * JoglPanel.globals.globeRadius)
                    + (JoglPanel.globals.EyePosition.getX() * JoglPanel.globals.EyePosition.getX())
                    + (JoglPanel.globals.EyePosition.getY() * JoglPanel.globals.EyePosition.getY())
                    + (((JoglPanel.globals.EyePosition.getZ() + JoglPanel.globals.globeRadius) - JoglPanel.globals.globeVirtualRadius) * ((JoglPanel.globals.EyePosition
                            .getZ() + JoglPanel.globals.globeRadius) - JoglPanel.globals.globeVirtualRadius));
            maxDist = (float) Math.sqrt(maxDist);
        }

        boolean bIsVisible = false;
        for (int j = 0; j < ny; j += nStepY) {
            bUseLast = false;
            for (int i = 0; i < nx; i += nStepX) {
                if (bUseLast) {
                    Vp1.duplicate(Vp3);
                } else {
                    coordX = grid_pointer[0][y + j][x + i];
                    coordY = grid_pointer[1][y + j][x + i];
                    coordZ = grid_pointer[2][y + j][x + i];
                    Vp1.setXYZ(coordX, coordY, coordZ);
                    Vp1.SubstractVector(JoglPanel.globals.EyePosition);
                }
                ;
                if (bUseLast) {
                    Vp2.duplicate(Vp4);
                } else {
                    coordX = grid_pointer[0][y + j + nStepY][x + i];
                    coordY = grid_pointer[1][y + j + nStepY][x + i];
                    coordZ = grid_pointer[2][y + j + nStepY][x + i];
                    Vp2.setXYZ(coordX, coordY, coordZ);
                    Vp2.SubstractVector(JoglPanel.globals.EyePosition);
                }
                ;
                coordX = grid_pointer[0][y + j][x + i + nStepX];
                coordY = grid_pointer[1][y + j][x + i + nStepX];
                coordZ = grid_pointer[2][y + j][x + i + nStepX];
                Vp3.setXYZ(coordX, coordY, coordZ);
                Vp3.SubstractVector(JoglPanel.globals.EyePosition);
                coordX = grid_pointer[0][y + j + nStepY][x + i + nStepX];
                coordY = grid_pointer[1][y + j + nStepY][x + i + nStepX];
                coordZ = grid_pointer[2][y + j + nStepY][x + i + nStepX];
                Vp4.setXYZ(coordX, coordY, coordZ);
                Vp4.SubstractVector(JoglPanel.globals.EyePosition);
                bUseLast = true;
                if (JoglPanel.globals.globeRadius != -1) {
                    if ((Vp1.getRadius() > maxDist) && (Vp2.getRadius() > maxDist) && (Vp3.getRadius() > maxDist)
                            && (Vp4.getRadius() > maxDist)) {
                        // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 1 failed");
                        continue;// texture behind
                    }
                }
                ;
                // for each point repeat:
                // 5. projections on axis
                float pn1, pm1;
                pn1 = (float) Vp1.DotProduct(Vn);
                pm1 = (float) Vp1.DotProduct(Vm);
                float pn2, pm2;
                pn2 = (float) Vp2.DotProduct(Vn);
                pm2 = (float) Vp2.DotProduct(Vm);
                float pn3, pm3;
                pn3 = (float) Vp3.DotProduct(Vn);
                pm3 = (float) Vp3.DotProduct(Vm);
                float pn4, pm4;
                pn4 = (float) Vp4.DotProduct(Vn);
                pm4 = (float) Vp4.DotProduct(Vm);
                // 6. view frustum
                // 6.1 dp
                float dp1;
                dp1 = (float) Vp1.DotProduct(Vd);// if (dp1<0) dp1=-dp1;
                float dp2;
                dp2 = (float) Vp2.DotProduct(Vd);
                float dp3;
                dp3 = (float) Vp3.DotProduct(Vd);
                float dp4;
                dp4 = (float) Vp4.DotProduct(Vd);
                // 6.1.0 negative projections
                if ((dp1 <= 0) && (dp2 <= 0) && (dp3 <= 0) && (dp4 <= 0)) {
                    // System.out.println("hide texture");
                    // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 2 failed");
                    continue;
                }
                // 6.2 frustum
                float fm1, fn1;
                float tan = (float) Math.tan(((Globals.FOV_ANGLE / 2f) * Math.PI) / 180f);
                fm1 = dp1 * tan;
                fn1 = fm1 / JoglPanel.globals.fAspect;
                float fm2, fn2;
                fm2 = dp2 * tan;
                fn2 = fm2 / JoglPanel.globals.fAspect;
                float fm3, fn3;
                fm3 = dp3 * tan;
                fn3 = fm3 / JoglPanel.globals.fAspect;
                float fm4, fn4;
                fm4 = dp4 * tan;
                fn4 = fm4 / JoglPanel.globals.fAspect;
                // before 7. do the correct checking
                // this means alot of computing...
                // first compute xc and yc:
                // xc = EyePosition.dotProduct(Vm);
                // same for yc on Vn axis
                // 7. check if at least 1 point is well positioned
                if ((pm1 > fm1) && (pm2 > fm2) && (pm3 > fm3) && (pm4 > fm4)) {
                    // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 3 failed");
                    continue;
                }
                ;
                if ((pm1 < -fm1) && (pm2 < -fm2) && (pm3 < -fm3) && (pm4 < -fm4)) {
                    // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 4 failed");
                    continue;
                }
                if ((pn1 > fn1) && (pn2 > fn2) && (pn3 > fn3) && (pn4 > fn4)) {
                    // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 5 failed");
                    continue;
                }
                ;
                if ((pn1 < -fn1) && (pn2 < -fn2) && (pn3 < -fn3) && (pn4 < -fn4)) {
                    // System.out.println("texture ["+x+","+y+"], piece ("+i+","+j+"), test 6 failed");
                    continue;
                }
                ;
                bIsVisible = true;
                break;
            }
        }
        if (!bIsVisible) {
            if (text != null) {
                text.bIsVisible = false;
            }
            return false;
        }
        if (text != null) {
            text.bIsVisible = true;
        }
        return true;
    }

    /**
     * deletes children and sets their valid textures for unload from memory
     *
     * @return a boolean value to indicate if there are children left ( the ones with ALWAYS flag set)<br>
     *         The function will probably return always true because the only textures with this flag set are the ones
     *         on
     *         first level
     */
    private boolean textDeleteChildren() {
        boolean bNoChildLeft = true;
        boolean bNoChildChildLeft;
        if (children != null) {
            // todo: set valid child textures for unset in gl drawing thread
            // delete children
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    // if a descendant of this child has an ALWAYS texture
                    // then this child cannot be removed
                    bNoChildChildLeft = children[i].textDeleteChildren();
                    if (children[i].checkStatus(S_SET)) {
                        if (children[i].checkStatus(S_ALWAYS)) {
                            bNoChildLeft = false;
                            continue;
                        } else {
                            children[i].resetStatus();// = S_NONE;
                            // texturesToUnSet.offer( Integer.valueOf(children[i].texture_id));
                            textureDataSpace.cacheTexture(new JoGLCachedTexture(children[i].resultID,
                                    children[i].texture_id));
                        }
                        ;
                    }
                    ;
                    // neccessary code to set a new texture id for this child that any way seems to be nulled
                    children[i].textSetParentTexture();
                    if (!bNoChildChildLeft) {
                        bNoChildLeft = false;
                        continue;
                    }
                    // unreachable code
                    // section reached only by children textures that are
                    // not any more set,
                    // or they are in texturesToLoad and texturesToSet arrays
                    // so, in this case,
                    // set status as dereferenced and null the reference to the child
                    // so that, if in other array, when it is removed from that
                    // array, it should be nulled also
                    children[i].setStatus(S_DEREFERENCED);
                    // children[i].grid = null;//?
                    children[i] = null;
                }
                ;
            }
            if (bNoChildLeft) {
                children = null;
                // grid = null;
            }
            ;
        }
        return bNoChildLeft;
    }

    /**
     * sets parent texture for all descendants if they don't have their own texture
     */
    private void textSetParentTexture() {
        if (!checkStatus(S_SET)) {
            /**
             * set the parent id for this texture
             * so set the texture coordinates based on those from parent
             * if parent has dynamic grid, then nWidth=1 or nHeight=1, so get the correct number
             * of child textures from dynamic grid
             */
            texture_id = parent.texture_id;
            if (!isBDynamicGrid()) {
                leftT = parent.leftT + (((nWorldX - parent.nWorldX) * (parent.rightT - parent.leftT)) / parent.nWidth);
                rightT = leftT + ((nWidth * (parent.rightT - parent.leftT)) / parent.nWidth);
                topT = parent.topT - (((nWorldY - parent.nWorldY) * (parent.topT - parent.bottomT)) / parent.nHeight);
                bottomT = topT - ((nHeight * (parent.topT - parent.bottomT)) / parent.nHeight);
            } else {
                int nx;
                int ny;
                /**
                 * deduce nx and ny = the number of points for x and y so called axis
                 */
                nx = parent.grid[0][0].length - 1;
                ny = parent.grid[0].length - 1;
                leftT = parent.leftT + ((nWorldX * (parent.rightT - parent.leftT)) / nx);
                rightT = leftT + ((parent.rightT - parent.leftT) / nx);
                topT = parent.topT - ((nWorldY * (parent.topT - parent.bottomT)) / ny);
                bottomT = topT - ((parent.topT - parent.bottomT) / ny);

            }
        }
        ;
        if (children != null) {
            // set for existing children that don't have their own texture this node's parent texture
            for (Texture element : children) {
                if (element != null /* && !children[i].checkStatus(S_SET) */) {
                    element.textSetParentTexture();
                }
            }
        }
    }

    /**
     * This one considers the image surface as composed of two triangles in space and
     * computes the minimal distance to each of them, and then selects the minimum from the
     * two values.<br>
     *
     * @see Texture#distancePoint2Triangle
     * @param vP
     * @param vP1
     * @param vP2
     * @param vP3
     * @param vP4
     * @return
     */
    /*
     * private float distancePoint2Quadrilater( VectorO vP, VectorO vP1, VectorO vP2, VectorO vP3, VectorO vP4)
     * {
     * //first triangle
     * float dist = 0;
     * dist = distancePoint2Triangle( vP, vP1, vP2, vP3);
     * //second triangle
     * float dist2;
     * dist2 = distancePoint2Triangle( vP, vP2, vP3, vP4);
     * return (dist<dist2?dist:dist2);
     * }
     */
    /**
     * Minimum distance from a point in space to a triangle is composed of two components:<br>
     * - distance from point to triangle's plane and<br>
     * - the minimum distance from point's projection on triangle's plane to triangle (that is minimum
     * distance from the point to any point that is in or on border of the triangle) if the projection is outside
     * the triangle, otherwise 0<br>
     * The minimum distance from a point to a triangle means the minimum distance from any point in
     * the triangle, or on border to this point.<br>
     * Computation:<br>
     * a. Distance from a point to a plane: the projection of a line that connects the point with a point in the
     * plane on the normal to the plane.<br>
     * Considering h this distance, the point is P, and the 3 points are P1, P2 and P3, the equations are:<br>
     * h = (vp-vp1).|vpr| (dot product between a difference vector and normal form vector.<br>
     * vpr = (vp1-vp2)x(vp2-vp3)<br>
     * Compute now the projection point P' of P on plane:<br>
     * vpp' = h.|vpr| (vector from P' to P is |vpr| multiplied by height)<br>
     * Compute vector to projection point:<br>
     * vp' = vp - vpp'<br>
     * b. Determine if a point is inside a triangle or outside<br>
     * Compute the vectors from P' to P1, P2, P3 and then see if the sum of angles between them is 180.<br>
     * For that, consider a small aproximation.<br>
     * v1 = vp1 - vp'<br>
     * v2 = vp2 - vp'<br>
     * v3 = vp3 - vp'<br>
     * total_angle = acos( |v1|.|v2| ) + acos( |v2|.|v3| ) + acos( |v3|.|v1| ) ;<br>
     * if( fabs( total_angle - 2*pi ) &lt; epsilon )<br>
     * it is inside<br>
     * else<br>
     * it is outside<br>
     * c. If P' is outside, find minimal distance:<br>
     * for each side of the 3 sides of the triangle compute minimal distance from a point to any point on the
     * segment. This is achieved by the following computation:<br>
     *
     * @see Texture#distancePoint2Triangle
     * @param vP
     * @param vP1
     * @param vP2
     * @param vP3
     * @return minimum distance
     */
    /*
     * private float distancePoint2Triangle( VectorO vP, VectorO vP1, VectorO vP2, VectorO vP3)
     * {
     * //a.
     * //vpr = (vp1-vp2)x(vp2-vp3)
     * VectorO vpr = VectorO.SubstractVector( vP1, vP2);
     * VectorO vAux = VectorO.SubstractVector( vP2, vP3);
     * vpr = vpr.CrossProduct(vAux);
     * vpr.Normalize();
     * //h = (vp-vp1).|vpr|
     * vAux = VectorO.SubstractVector( vP, vP1);
     * double h = vAux.DotProduct(vpr);
     * //vpp' = h.|vpr|
     * VectorO vPPprim = new VectorO(vpr);
     * vPPprim.MultiplyScalar(h);
     * //vp' = vp - vpp'
     * VectorO vPprim = VectorO.SubstractVector( vP, vPPprim);
     * //b.
     * double hsecond = 0;
     * //v1 = vp1 - vp'
     * VectorO v1 = VectorO.SubstractVector( vP1, vPprim);
     * v1.Normalize();
     * //v2 = vp2 - vp'
     * VectorO v2 = VectorO.SubstractVector( vP2, vPprim);
     * v2.Normalize();
     * //v3 = vp3 - vp'
     * VectorO v3 = VectorO.SubstractVector( vP3, vPprim);
     * v3.Normalize();
     * //total_angle = acos( |v1|.|v2| ) + acos( |v2|.|v3| ) + acos( |v3|.|v1| ) ;
     * double total_angle = 0;
     * total_angle += Math.acos( v1.DotProduct(v2));
     * total_angle += Math.acos( v2.DotProduct(v3));
     * total_angle += Math.acos( v3.DotProduct(v1));
     * //if( fabs( total_angle - 2*pi ) &lt; epsilon )
     * if( Math.abs( total_angle - 2*Math.PI ) > 0.001 ) {
     * //outside
     * //1st side of triangle
     * float hs1 = distancePoint2Segment( vPprim, vP1, vP2);
     * hsecond = hs1;
     * //2nd side of triangle
     * float hs2 = distancePoint2Segment( vPprim, vP2, vP3);
     * if ( hs2 < hsecond )
     * hsecond = hs2;
     * //3rd side of triangle
     * float hs3 = distancePoint2Segment( vPprim, vP3, vP1);
     * if ( hs3 < hsecond )
     * hsecond = hs3;
     * } //else inside
     * return (float)Math.sqrt( h*h+hsecond*hsecond);
     * }
     */

    /**
     * pr(P'P1) = (vp'-vp1).(vp1-vp2) (projection of vector from P1 to P' on segment P1P2)<br>
     * if ( pr(P'P1)>0 ) => projection point of P' is situated to the left of P1, where P2 is on the right => minimal
     * distance is PP1 = module(vp'-vp1)<br>
     * else {<br>
     * pr(P'P2) = (vp'-vp2).(vp2-vp1) (watch the direction of each vector)<br>
     * if ( pr(P'P2) > 0 ) => this point is on the right of P2, while P1 is on the left => minimum distance is<br>
     * PP2 = module(vp'-vp2)<br>
     * else<br>
     * the point is inside the space formed by the perpendiculares on the segment in the heads, so<br>
     * the minimal height is the perpendiculaire on the segment.<br>
     * - compute perpendicular on the plane (P',P1,P2)<br>
     * - rotate P1P2 with 90 degrees => the new vector is perpendicular on P1P2<br>
     * - project P'P1 on vector computed previously<br>
     * - use module of this value<br>
     * }<br>
     *
     * @param vPprim
     * @param vP1
     * @param vP2
     * @return distance
     */
    /*
     * private float distancePoint2Segment( VectorO vPprim, VectorO vP1, VectorO vP2)
     * {
     * double hs1 = 0;
     * VectorO vAux;
     * //pr(P'P1) = (vp'-vp1).(vp1-vp2)
     * vAux = VectorO.SubstractVector(vPprim, vP1);
     * double projPprimP1 = vAux.DotProduct( VectorO.SubstractVector(vP1, vP2));
     * //if ( pr(P'P1)>0 )
     * if ( projPprimP1 > 0 )
     * //distance is PP1 = module(vp'-vp1)
     * hs1 = vAux.getRadius();
     * //else {
     * else {
     * //pr(P'P2) = (vp'-vp2).(vp2-vp1)
     * vAux = VectorO.SubstractVector(vPprim, vP2);
     * double projPprimP2 = vAux.DotProduct( VectorO.SubstractVector(vP2, vP1));
     * //if ( pr(P'P2) > 0 )
     * if ( projPprimP2 > 0 )
     * //PP2 = module(vp'-vp2)
     * hs1 = vAux.getRadius();
     * else {
     * //compute perpendicular on the plane (P',P1,P2)
     * VectorO vP1P2 = VectorO.SubstractVector( vP1, vP2);
     * VectorO vPprimP1 = VectorO.SubstractVector( vPprim, vP1);
     * vAux = vP1.CrossProduct( vP1P2);
     * //rotate P1P2 with 90 degrees => the new vector is perpendicular on P1P2
     * VectorO vP1P2per = new VectorO(vP1P2);
     * vP1P2per.RotateDegree( vAux, 90);
     * //project P'P1 on vector computed previously
     * hs1 = vPprimP1.DotProduct(vP1P2per);
     * //use module of this value
     * if ( hs1<0 )
     * hs1 = -hs1;
     * }
     * }
     * return (float)hs1;
     * }
     */

    /** used for textVisible2 > textZoomChanged > zoomChanged */
    // private static float []coords3D = new float[3];
    /** used for textVisible2 > textZoomChanged > zoomChanged */
    // private static float []coords2D = new float[2];
    /**
     * checks a slice to see if needs texture load, based on current level of detail,
     * texture visibility in frustrum and file availability<br>
     * todo: should be more inteligent in that should set for load first the full
     * viewable textures and then the half viewable, and in the future, then the near
     * ones<br>
     * <b>Algorithm for setting the, hopefully, correct LOD(level of detail) for a texture.</b><br>
     * This algorithm uses an aproximate visibility test, as being the best I could find.<br>
     * Algorithm:<br>
     * 1. traverse the tree<br>
     * 2. for a visible node compute depth (depth is: "projection of vector starting from
     * eye to center of texture, on vector eye direction starting at eye and ending at direction")<br>
     * <b>UPDATE at 14/Jun/05:</b><br>
     * The old depth computation was aproximative, so a new one was neccessary.<br>
     * This one considers the image surface as composed of two triangles in space and
     * computes the minimal distance to each of them, and then selects the minimum from the
     * two values.<br>
     * computed by distancePoint2Quadrilater<br>
     * <b>END UPDATE</b><br>
     * 3. compare current LOD (given by level in tree) with the desired LOD (computed
     * based on depth and global depths vector)<br>
     * 4. - if greater, decrease LOD by deleting node and its children<br>
     * 5. - if lower, then<br>
     * 5.1 - create/set visible children, delete the rest<br>
     * 5.2 - set current texture as their base texture, if they have none<br>
     * 5.3 - test algorithm for each child<br>
     * 5.4* - if there is no valid child (left), set texture for load<br>
     * 6. - if equal, then<br>
     * 6.1 - if has children, delete them, 'cause it doesn't need a more detailed LOD<br>
     * 6.2* - it texture is not set, set texture for load<br>
     * 7. end<br>
     * 7+ unload not visible textures.<br>
     * <br>
     * What "*" means is that only on those branches a texture is set for load, that is
     * a new file is read into memory.<br>
     * Problems for this algorithm:<br>
     * - delete a node<br>
     * - use base texture... => a node must not have a 0 id for texture or an invalid one<br>
     * - algorithm may behave badly for sphere shape not centered<br>
     * - not used distance, but projection so that the algorithm behave the same as the one
     * for plane projection<br>
     *
     * @param currentLevel
     *            level for this node
     * @return boolean value to indicate validity
     * @see Texture#distancePoint2Quadrilater
     */
    private boolean textZoomChanged(int currentLevel) {
        if (currentLevel > depthZ.length) {
            return false;
        }
        if ((nMaximumLevel != -1) && (currentLevel > nMaximumLevel)) {
            return false;
        }
        // System.out.println("text zoom changed");
        /**
         * Algorithm for setting the, hopefully, correct LOD(level of detail) for a texture.
         * This algorithm uses an aproximate visibility test, as being the best I could find.
         * Algorithm:
         * 1. traverse the tree
         * 2. for a visible node compute depth (depth is: "projection of vector starting from
         * eye to center of texture, on vector eye direction starting at eye and ending at direction")
         * <b>UPDATE at 14/Jun/05</b>
         * 3. compare current LOD (given by level in tree) with the desired LOD (computed
         * based on depth and global depths vector)
         * 4. - if greater, decrease LOD by deleting node and its children
         * 5. - if lower, then
         * 5.1 - create/set visible children, delete the rest
         * 5.2 - set current texture as their base texture, if they have none
         * 5.3 - test algorithm for each child
         * 5.4* - if there is no valid child (left), set texture for load
         * 6. - if equal, then
         * 6.1 - if has children, delete them, 'cause it doesn't need a more detailed LOD
         * 6.2* - it texture is not set, set texture for load
         * 7. end
         * 7+ unload not visible textures.
         * What "*" means is that only on those branches a texture is set for load, that is
         * a new file is read into memory.
         * Problems for this algorithm:
         * - delete a node
         * - use base texture... => a node must not have a 0 id for texture or an invalid one
         * - algorithm may behave badly for sphere shape not centered
         * - not used distance, but projection so that the algorithm behave the same as the one
         * for plane projection
         */
        if (textVisible(nWorldX, nWorldY, nWidth, nHeight, bDynamicGrid, parent, this)) {
            // 2. compute depth
            float depth;
            float centerX, centerY, centerZ;
            float[][][] grid_pointer = Globals.points;
            if (bDynamicGrid) {
                grid_pointer = parent.grid;
            }
            /**
             * if the center is not on the static grid (h or w==1), or
             * it is a dynamic grid, that has no point for center on it,
             * compute the center as center of polygon formed by the four
             * points that limit the texture.
             */
            int desiredLevel = nInitialLevel;
            if ((JoglPanel.globals.globeRadius != -1) && (JoglPanel.globals.mapAngle == 90)) {
                desiredLevel = JoglPanel.globals.globalSphereDesiredLevel;
            } else {
                if ((nHeight == 1) || (nWidth == 1)) {// compute the center of this node
                    // based on its dynamic grid position properties
                    centerX = (grid_pointer[0][nWorldY][nWorldX] + grid_pointer[0][nWorldY + nHeight][nWorldX]
                            + grid_pointer[0][nWorldY][nWorldX + nWidth] + grid_pointer[0][nWorldY + nHeight][nWorldX
                            + nWidth]) / 4f;
                    centerY = (grid_pointer[1][nWorldY][nWorldX] + grid_pointer[1][nWorldY + nHeight][nWorldX]
                            + grid_pointer[1][nWorldY][nWorldX + nWidth] + grid_pointer[1][nWorldY + nHeight][nWorldX
                            + nWidth]) / 4f;
                    centerZ = (grid_pointer[2][nWorldY][nWorldX] + grid_pointer[2][nWorldY + nHeight][nWorldX]
                            + grid_pointer[2][nWorldY][nWorldX + nWidth] + grid_pointer[2][nWorldY + nHeight][nWorldX
                            + nWidth]) / 4f;
                } else {
                    centerX = Globals.points[0][nWorldY + (nHeight / 2)][nWorldX + (nWidth / 2)];
                    centerY = Globals.points[1][nWorldY + (nHeight / 2)][nWorldX + (nWidth / 2)];
                    centerZ = Globals.points[2][nWorldY + (nHeight / 2)][nWorldX + (nWidth / 2)];
                }
                ;
                VectorO Vp = new VectorO(centerX, centerY, centerZ);
                Vp.SubstractVector(JoglPanel.globals.EyePosition);
                depth = (float) Vp.getRadius();
                if (depth < 0f) {
                    depth = -depth;
                }
                ;

                // 3. compare currentLevel with desiredLevel
                for (int i = nInitialLevel/* 0 */; i < depthZ.length; i++) {
                    if (depth > depthZ[i]) {
                        break;
                    } else {
                        desiredLevel = i + 1;
                    }
                }
            }
            ;
            // 4. LODc > LODd
            if (currentLevel > desiredLevel) {
                // level of detail too high for the current depth, so decrease it by deleting child nodes
                // and, eventually, this node
                boolean bNoChildLeft = false;
                bNoChildLeft = textDeleteChildren();
                // set for unload this texture if is SET and is not ALWAYS
                // and set this node for deletion in parent
                if (checkStatus(S_SET)) {
                    if (checkStatus(S_ALWAYS)) {
                        return true;
                    } else {
                        // set current texture for unload and revert to parent texture
                        // texturesToUnSet.offer( Integer.valueOf(texture_id));
                        textureDataSpace.cacheTexture(new JoGLCachedTexture(resultID, texture_id));
                        resetStatus();
                        textSetParentTexture();
                    }
                }
                if (bNoChildLeft) {
                    return false;
                }
            } else if (currentLevel < desiredLevel) {
                boolean bShouldLoad = true;
                // 5. LODc < LODd
                // if still allowed to load new textures:
                // if not without child textures, or not at maximum level
                // System.out.println("texture "+resultID+" at max level? "+checkStatus(S_MAX_LEVEL));
                if (!checkStatus(S_MAX_LEVEL)
                        && ((Texture.nMaximumLevel == -1) || (currentLevel < Texture.nMaximumLevel))) {// not
                    // max
                    // level
                    // 5.1 create/set children
                    // compute the slice path
                    float stepTX, stepTY;
                    int nx/* = texturesX[currentLevel+1] */;
                    int ny/* = texturesY[currentLevel+1] */;
                    if ((currentLevel + 1) == 0) {
                        nx = texturesX0;
                        ny = texturesY0;
                    } else {
                        nx = ny = 2;
                    }
                    stepTX = (rightT - leftT) / nx;
                    stepTY = (topT - bottomT) / ny;
                    int stepX, stepY, startX, startY;
                    boolean bDynamicChildren = false;
                    // test to see if children will be created based on the static grid for this node, or the parent
                    // has to create a dynamic grid for them
                    if (((nWidth == 1) || (nHeight == 1)) || bDynamicGrid) {
                        bDynamicChildren = true;
                        // the conditions to create a dynamic grid are:
                        // there are no points left to divide the static grid on x or on y, or
                        // this node is already a dynamic grid based, so, its children must also be dynamic
                        // the second condition also implies that nWidth =1 and nHeight =1, but it is there for
                        // clarity reasons
                        stepX = 1;
                        stepY = 1;
                        startX = 0;
                        startY = 0;
                        // create dynamic grid
                        if (grid == null) {
                            // create with one aditional point because 1 texture is guarded by 2 points,
                            // and nx is the number of textures...
                            grid = new float[3][ny + 1][nx + 1];
                            // instantiate the grid, based on parent coordinates
                            // prepare parent coordinate for init grid function
                            lt_grid[0] = grid_pointer[0][nWorldY][nWorldX];
                            lt_grid[1] = grid_pointer[1][nWorldY][nWorldX];
                            lt_grid[2] = grid_pointer[2][nWorldY][nWorldX];
                            lb_grid[0] = grid_pointer[0][nWorldY + nHeight][nWorldX];
                            lb_grid[1] = grid_pointer[1][nWorldY + nHeight][nWorldX];
                            lb_grid[2] = grid_pointer[2][nWorldY + nHeight][nWorldX];
                            rt_grid[0] = grid_pointer[0][nWorldY][nWorldX + nWidth];
                            rt_grid[1] = grid_pointer[1][nWorldY][nWorldX + nWidth];
                            rt_grid[2] = grid_pointer[2][nWorldY][nWorldX + nWidth];
                            rb_grid[0] = grid_pointer[0][nWorldY + nHeight][nWorldX + nWidth];
                            rb_grid[1] = grid_pointer[1][nWorldY + nHeight][nWorldX + nWidth];
                            rb_grid[2] = grid_pointer[2][nWorldY + nHeight][nWorldX + nWidth];

                            initDynamicGrid(grid);
                        }
                    } else {
                        stepX = nWidth / nx;
                        stepY = nHeight / ny;
                        startX = nWorldX;
                        startY = nWorldY;
                    }
                    if (children == null) {
                        children = new Texture[nx * ny];
                    }
                    int k;
                    for (int y = 0; y < ny; y++) {
                        for (int x = 0; x < nx; x++) {
                            k = (y * nx) + x;
                            if (textVisible(startX + (stepX * x), startY + (stepY * y), stepX, stepY, bDynamicChildren,
                                    this, null)) {
                                // System.out.println("child visible (x,y,width,height)=("+(nWorldX+stepX*x)+","+(nWorldY+stepY*y)+","+stepX+","+stepY+")");
                                // 5.2 set base texture
                                if (children[k] == null) {// if visible child is null, create it
                                    children[k] = new Texture(texture_id,// parent texture for the moment
                                            startX + (stepX * x), startY + (stepY * y), stepX, stepY, leftT
                                                    + (x * stepTX), topT - ((y + 1) * stepTY), leftT
                                                    + ((x + 1) * stepTX), topT - (y * stepTY));// set texture coordonates
                                    children[k].setParent(this);
                                    children[k].setBDynamicGrid(bDynamicChildren);
                                }
                                // because child is using parent texture
                                // 5.3 test algorithm for each visible child
                                if (children[k].textZoomChanged(currentLevel + 1) == false) {
                                    // invalid child, so, delete it, texture_id already set for dealocation
                                    children[k].setStatus(S_DEREFERENCED);
                                    children[k] = null;
                                }
                                // todo: check if this hypothesis is correct
                                else {
                                    bShouldLoad = true;// ? false;//5.4 if one child is valid, this should not load
                                }
                            } else {
                                // System.out.println("child not visible (x,y,width,height)=("+(nWorldX+stepX*x)+","+(nWorldY+stepY*y)+","+stepX+","+stepY+")");
                                // 5.1 delete the rest
                                // delete children if not set as ALWAYS
                                if (children[k] != null) {
                                    boolean bNoChildLeft = children[k].textDeleteChildren();
                                    // set for unload this texture if is its texture and is not set as ALWAYS
                                    if (children[k].checkStatus(S_SET) && !children[k].checkStatus(S_ALWAYS)) {
                                        // texturesToUnSet.offer( Integer.valueOf(children[k].texture_id));
                                        textureDataSpace.cacheTexture(new JoGLCachedTexture(children[k].resultID,
                                                children[k].texture_id));
                                        children[k].resetStatus();// = S_NONE;
                                        children[k].textSetParentTexture();// although there is no need for this line,
                                        // as the node is deleted anyway
                                    }
                                    ;
                                    if (bNoChildLeft && !children[k].checkStatus(S_ALWAYS)) {
                                        children[k].setStatus(S_DEREFERENCED);
                                        children[k] = null;
                                    }
                                    ;
                                }
                            }// endif ( textVisible(nWorldX+stepX*x, nWorldY+stepY*y, stepX, stepY) )
                        }
                    }
                    ;// endfor ( int x=0; x<nx; x++ )
                }// endif ( !checkStatus(S_MAX_LEVEL) ) {
                 // 5.4 no valid child and hasn't been already set for loading
                if (bShouldLoad && !checkStatus(S_SHOULD_LOAD)) {
                    // resetStatus();//has nothing to reset cause this is the first flag to be set, except for S_NONE
                    setStatus(S_SHOULD_LOAD);
                    String path = textCreatePathDyn(currentLevel).toString();
                    // System.out.println("SHOULD_LOAD: "+path);

                    TextureLoadJobResult tres = new TextureLoadJobResult();
                    tres.texture = this;
                    tres.path = path;
                    tres.level = currentLevel;
                    tres.crtMonitor = null;
                    tres.treeID = JoglPanel.globals.nTreeID;
                    // add this slice to slices that should have texture loaded
                    texturesToLoad.offer(tres);
                }
            } else { // 6. LODc = LODd
                // 6.1 delete children
                // moved in set function, when there is a correct texture available for this level
                // and the betters ones (childs ) can be unloaded
                // delete child textures only if parent has one
                if (checkStatus(S_SET)) {
                    /* boolean bNoChildLeft = */textDeleteChildren();
                }
                // 6.2 set for load, if the case
                if (!checkStatus(S_SHOULD_LOAD)) {
                    // resetStatus();//has nothing to reset cause this is the first flag to be set, except for S_NONE
                    setStatus(S_SHOULD_LOAD);
                    String path = textCreatePathDyn(currentLevel).toString();
                    // System.out.println("SHOULD_LOAD: "+path);
                    TextureLoadJobResult tres = new TextureLoadJobResult();
                    tres.texture = this;
                    tres.path = path;
                    tres.level = currentLevel;
                    tres.crtMonitor = null;
                    tres.treeID = JoglPanel.globals.nTreeID;
                    // add this slice to slices that should have texture loaded
                    texturesToLoad.offer(tres);
                }
            }// end compare current LOD with desired LOD
        } else {// texture not visible
            // level of detail too high for the current depth, so decrease it by deleting child nodes
            // and, eventually, this node
            boolean bNoChildLeft = textDeleteChildren();
            // set for unload this texture if is its texture and is not set as ALWAYS
            // and set this node for deletion in parent
            if (checkStatus(S_SET)) {
                if (checkStatus(S_ALWAYS)) {
                    return true;
                }
            }
            // texturesToUnSet.offer( Integer.valueOf(texture_id));
            textureDataSpace.cacheTexture(new JoGLCachedTexture(resultID, texture_id));
            resetStatus();// = S_NONE;
            textSetParentTexture();
            if (bNoChildLeft) {
                return false;
            }
        }
        return true;
    }

    /*
     * private void printChildTid( int level)
     * {
     * if ( children!=null ) {
     * //set for existing children that don't have their own texture this node's parent texture
     * for( int i=0; i<children.length; i++)
     * if ( children[i]!=null ) {
     * logger.log( logLevel, "level "+level+" -> Child texture id : "+children[i].texture_id);
     * children[i].printChildTid( level+1);
     * };
     * }
     * }
     */
    /**
     * check the node to see if all children have their own textures so that
     * the parent can unload its<br>
     * sets a texture to parent texture if all possible children have their own
     * texture
     *
     * @param gl
     */
    private void textUnsetParentTexture(GL gl) {
        // if there is no parent, no unset neccessary
        if (parent == null) {
            return;// keep the texture if last texture...
        }
        if (texture_id == 0) {
            return;// somehow similar to first conditions as both apply to root texture
        }
        // if this texture is set forever, no unset to do for this or parents
        if (checkStatus(S_ALWAYS)) {
            return;
        }
        // if this texture is using it's parent texture, then has nothing to be
        // unset, and the parent cannot unload it's texture
        if (!checkStatus(S_SET)) {
            return;
        }
        // if no children, no need to unset, to lose its texture
        if ((children == null) || (children.length == 0)) {
            return;
        }
        // check to see if all children are set
        // if one is not set then nothing to do for this texture
        for (int i = 0; i < children.length; i++) {
            if ((children[i] == null) || !children[i].checkStatus(S_SET)/* children[i].texture_id==texture_id */) {
                return;
            }
        }
        // unload texture from memory, because ABSOLUTELY no one else is using IT!
        if (gl.glIsTexture(texture_id)) {
            // int[] textures = {texture_id};
            // gl.glDeleteTextures( 1, textures);
            textureDataSpace.cacheTexture(new JoGLCachedTexture(resultID, texture_id));
            // logger.log( logLevel, "unsetTexture -> Delete texture with id: "+textures[0]);
            // System.out.println("unsetParentTexture -> Delete texture with id: "+textures[0]);
        }
        // texture not set, so reset status also
        resetStatus();// = S_NONE; keeps S_DEREFERENCED and S_MAX_LEVEL
        /**
         * set the parent id for this texture
         * so set the texture coordinates based on those from parent
         * if parent has dynamic grid, then nWidth=1 or nHeight=1, so get the correct number
         * of child textures from dynamic grid
         */
        texture_id = parent.texture_id;
        if (!isBDynamicGrid()) {
            leftT = parent.leftT + (((nWorldX - parent.nWorldX) * (parent.rightT - parent.leftT)) / parent.nWidth);
            rightT = leftT + ((nWidth * (parent.rightT - parent.leftT)) / parent.nWidth);
            topT = parent.topT - (((nWorldY - parent.nWorldY) * (parent.topT - parent.bottomT)) / parent.nHeight);
            bottomT = topT - ((nHeight * (parent.topT - parent.bottomT)) / parent.nHeight);
        } else {
            int nx;
            int ny;
            // deduce nx and ny = the number of points for x and y so called axis
            nx = parent.grid[0][0].length - 1;
            ny = parent.grid[0].length - 1;
            leftT = parent.leftT + ((nWorldX * (parent.rightT - parent.leftT)) / nx);
            rightT = leftT + ((parent.rightT - parent.leftT) / nx);
            topT = parent.topT - ((nWorldY * (parent.topT - parent.bottomT)) / ny);
            bottomT = topT - ((parent.topT - parent.bottomT) / ny);

        }
        // logger.log( Level.FINEST, "unsetTexture -> New texture id from parent: "+texture_id);
    }

    private StringBuilder textCreatePathDyn(int currentLevel) {
        if (bDynamicGrid) {
            StringBuilder ret = parent.textCreatePathDyn(currentLevel - 1);
            // old computation
            // ret[1] += "_"+(nWorldY+1)+"."+(nWorldX+1);
            // ret[0] += pathSeparator+"map"+(currentLevel+1)+ret[1];
            // new
            if (ret.length() > 0) {
                ret.append(pathSeparator);
            }
            ret.append((nWorldY + 1) + "." + (nWorldX + 1));
            return ret;
        }
        return createTexturePath(currentLevel, nWorldX, nWorldY);
    }

    /**
     * another unset method that tranverses the tree from root to leaves
     * and unloads textures that are not visible or not on the correct depth
     *
     * @param gl
     */
    /*
     * private void unsetTexture2( GL gl, int currentLevel)
     * {
     * // System.out.println("unset texture 2 level:"+currentLevel);
     * //check to see if texture is not visible
     * // if ( (worldX>=Main.globals.endFx || worldX+width<=Main.globals.startFx) &&
     * // (worldY<=Main.globals.endFy || worldY-height>=Main.globals.startFy) ) {//texture is not visible
     * float worldX, worldY;
     *//** compute worldX and worldY **/
    /*
     * worldX = -Globals.MAP_WIDTH/2f + nWorldX*Globals.divisionWidth;
     * worldY = Globals.MAP_HEIGHT/2f - nWorldY*Globals.divisionHeight;
     * //frustum checking only for plane projection
     * if ( !(worldX<Main.globals.endFx && worldX+nWidth*Globals.divisionWidth>Main.globals.startFx &&
     * worldY>Main.globals.endFy && worldY-nHeight*Globals.divisionHeight<Main.globals.startFy ) ||
     * // if ( !(worldX<Main.globals.endFx && worldX+width>Main.globals.startFx &&
     * // worldY>Main.globals.endFy && worldY-height<Main.globals.startFy) ||
     * currentLevel > Main.globals.currentLevel ) {
     * //for texture_id, equal ( "=" ) means the same as greater or equal ( ">=" ) to zero
     * if ( !checkStatus(S_ALWAYS) && checkStatus(S_SET)(status&SM_LOW)!=S_ALWAYS && texture_id>=0 &&
     * (status&SM_LOW)==S_SET ) {
     * System.out.println("Unloading file "+pathToTextures+createTexturePath( currentLevel, nWorldX, nWorldY)+".oct");
     * unsetDirect( gl);
     * };
     * }
     * if ( children!=null ){//part of texture is visible, so check the children
     * boolean bRemoveChildren = true;
     * //for each children, clear the textures that are not visible
     * for ( int i=0; i<children.length; i++)
     * if ( children[i]!= null ) {
     * children[i].unsetTexture2( gl, currentLevel+1);
     * if ( children[i].texture_id>0 && ( children[i].texture_id!=texture_id || children[i].checkStatus(S_NONE) ) )
     * bRemoveChildren = false;
     * else {
     * children[i].status = S_DEREFERENCED;
     * children[i] = null;
     * }
     * }
     * //if any of the children are not visible, remove the children
     * if ( bRemoveChildren ) {
     * //System.out.println("unsetTexture2 remove children for texture id: "+texture_id);
     * children = null;
     * }
     * }
     * }
     */
    /**
     * direct unset of loaded texture, without any aditional visibility checking
     *
     * @param gl
     */
    /*
     * private void unsetDirect( GL gl)
     * {
     * //System.out.println("unsetDirect texture_id: "+texture_id);
     * //int tex_id = texture_id;
     * if ( gl.glIsTexture(texture_id) ) {//this check also includes (children[i].texture_id!= tex_id &&
     * children[i].texture_id>0)
     * int[] textures = {texture_id};
     * gl.glDeleteTextures( 1, textures);
     * System.out.println("unsetDirect -> Delete texture with id: "+textures[0]);
     * };
     * texture_id = 0;
     * status = S_NONE;
     * //free the invisible children
     * if ( children != null ) {
     * for ( int i=0; i<children.length; i++)
     * if ( children[i]!=null ) {
     * children[i].unsetDirect( gl);
     * children[i].status = S_DEREFERENCED;
     * children[i] = null;
     * };
     * children = null;
     * }
     * }
     */
    /**
     * finds and returns the texture under the cursor<br>
     * The texture is the smallest one, and the most detailed.<br>
     * Transforms from geographical coordinates to texture world coordinates.<br>
     * Calls private function <b>textFindUnderCursor</b> for all root's children<br>
     * that checks to see if this texture, coresponding to given level,
     * contains the point defined by x and y.<br>
     * level helps in computing the world position and widths of this
     * texture.<br>
     * If point on this texture, the see if any of the children, if any,
     * contains the point, and, if so, return the value return by the child.<br>
     * If no valid child contains this point, then, if this has it's own texture,
     * loaded and set, return name of this texture.<br>
     * After a texture name is returned, the file name is stripped from path and
     * returned.
     *
     * @param LONG
     *            longitude of mouse point
     * @param LAT
     *            latitude of mouse point
     * @return texture's name
     */
    public static String findAtCoords(float LONG, float LAT) {
        float x, y;
        x = (LONG * Globals.MAP_WIDTH) / 360f;
        y = (LAT * Globals.MAP_HEIGHT) / 180f;
        String path = null;// = JoglPanel.globals.root.textFindUnderCursor( 0, x, y);
        for (Texture element : JoglPanel.globals.root.children) {
            path = element.textFindUnderCursor(0, x, y);
            // if valid children, return it
            if (path != null) {
                break;
            }
        }
        // if ( path!=null ) {
        // int nFileStart = path.lastIndexOf('/');
        // if ( nFileStart!=-1 )
        // return path.substring(nFileStart+1);
        // };
        return path;
    }

    /**
     * checks to see if this texture, coresponding to given level,
     * contains the point defined by x and y.<br>
     * level helps in computing the world position and widths of this
     * texture.<br>
     * If point on this texture, the see if any of the children, if any,
     * contains the point, and, if so, return the value return by the child.<br>
     * If no valid child contains this point, then, if this has it's own texture,
     * loaded and set, return name of this texture.
     *
     * @param level
     *            level for texture
     * @param x
     *            mouse x in texture world coordinates
     * @param y
     *            mouse y in texture world coordinates
     * @return string value representing the name of the texture under cursor
     */
    private String textFindUnderCursor(int level, float x, float y) {
        String ret = null;
        float w, h;
        w = Globals.MAP_WIDTH;
        h = Globals.MAP_HEIGHT;
        for (int i = 0; i <= level; i++) {
            if (i == 0) {
                w /= texturesX0;
                h /= texturesY0;
            } else {
                w /= 2;
                h /= 2;
            }
            // w /= texturesX[i];
            // h /= texturesY[i];
        }
        ;
        float worldX, worldY;
        /** compute worldX and worldY **/
        float[] values = findDinamicPosition();
        // w = values[1];
        // h = values[3];
        worldX = values[0] - (Globals.MAP_WIDTH / 2f);
        worldY = -values[2] + (Globals.MAP_HEIGHT / 2f);
        // System.out.println("texture at (x,y)=("+worldX+","+worldY+") and (w,h)=("+w+","+h+")");
        if ((worldX <= x) && ((worldX + w) >= x) && (worldY >= y) && ((worldY - h) <= y)) {// cursor in this texture
            // check its children if any
            if (children != null) {
                for (Texture element : children) {
                    if (element != null) {
                        ret = element.textFindUnderCursor(level + 1, x, y);
                    }
                    // if valid children, return it
                    if (ret != null) {
                        break;
                    }
                }
            }
            // if no valid child texture, check to see if this texture is valid
            if ((ret == null) && checkStatus(S_SET)) {
                ret = textCreatePathDyn(level).toString();
                // and if so, return this one
            }
            ;
        }
        // if no mouse on this texture, return nothing
        return ret;
    }

    /*
     * public static Texture findUnderCursor(int posX, int posY)
     * {
     * Texture ret=null;
     * int worldX, worldY;
     * Texture current_texture = JoglPanel.globals.root;
     * // compute worldX and worldY
     * worldX = (int)(current_texture.nWorldX*Globals.divisionWidth);
     * worldY = current_texture.nWorldY*Globals.divisionHeight;
     * //frustum checking only for plane projection
     * if ( worldX<=posX && worldX+nWidth*Globals.divisionWidth>JoglPanel.globals.CursorPosition[0] &&
     * worldY>=JoglPanel.globals.CursorPosition[1] &&
     * worldY-nHeight*Globals.divisionHeight<JoglPanel.globals.CursorPosition[1] ) {//texture is under cursor
     * //System.out.println("worldX="+worldX+" worldY="+worldY+" widht="+width+" height="+height);
     * if ( children!=null ) {
     * for( int i=0; i<children.length; i++) {
     * if ( children[i]!=null && children[i].checkStatus(S_SET) )
     * ret = children[i].findUnderCursor();
     * if ( ret!=null )
     * return ret;
     * };
     * }
     * if ( ret ==null )
     * ret = this;
     * }
     * return ret;
     * }
     */

    /**
     * swaps the current tree of textures with a new one, that has another
     * starting resolution. Also, in the same operation, atomically, it updates
     * the initial level to be considered for the new resolution.<br>
     * <b>Old texture tree must be dealocated from open gl!!!!</b>
     *
     * @param new_root
     *            new root texture to replace JoglPanel.globals.root
     * @param new_level
     *            the new starting texture level for root's children
     * @param new_date
     *            the new currentTime to be used for drawing shaddows
     * @param new_treeid
     *            the new tree id
     */
    public static void swapRoots(Texture new_root, int new_level, Date new_date, int new_treeid) {
        Texture old_root;
        synchronized (syncObject_Texture) {
            old_root = JoglPanel.globals.root;
            textureDataSpace.invalidateTexturesInTree(JoglPanel.globals.nTreeID);
            JoglPanel.globals.nTreeID = new_treeid;
            JoglPanel.globals.root = new_root;
            Texture.nInitialLevel = new_level;
            JoglPanel.globals.currentTime = new_date;
        }
        // delete all gl textures from old root texture
        // take out the ALWAYS flag so that the child textures can be deleted
        if (old_root != null) {
            for (Texture element : old_root.children) {
                element.clearStatus(S_ALWAYS);
            }
            // the root has no texture to be deleted.
            old_root.textDeleteChildren();
        }
        ;
    }

    /**
     * sets visibility test on
     */
    private void textCheckVisible() {
        bCheckVisibility = true;
        if (children != null) {// has children
            for (Texture element : children) {
                if (element != null) {
                    element.textCheckVisible();
                }
            }
        }
    }

    public static void checkVisibility() {
        try {
            JoglPanel.globals.root.textCheckVisible();
        } catch (Exception ex) {
            // do nothing
        }
    }

    /**
     * goes into old texture to level of new texture<br>
     * creates a new reference to old one for each texture.
     *
     * @param new_root
     * @param new_level
     */
    public static int loadRestOfTextures(ChangeRootTexture crtMonitor, Texture new_root, int new_level) {
        Texture old_root = JoglPanel.globals.root;
        int old_level = Texture.nInitialLevel;
        if (old_level > new_level) {
            return 0; // nothing to be done
        }
        if (old_level < new_level) {
            return 0; // a job too complex for me
        }
        // TODO: load rest of textures for different initial levels
        int nRestTextToLoad = 0;
        try {
            // both new and old textures have the base textures
            // so their children should be synchronized
            // System.out.println("load other textures");
            for (int i = 0; i < old_root.children.length; i++) {
                // should be the same as with new_root
                nRestTextToLoad += new_root.children[i].textSync(crtMonitor, old_root.children[i], new_level + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // after setting all textures for loading, notify thread
        // synchronized ( texturesToLoad ) {
        // texturesToLoad.notify();
        // };
        return nRestTextToLoad;
    }

    /**
     * syncronizes this texture, new texture, with the old one<br>
     * That means, make the same children available and with the same kind of textures
     *
     * @param oldT
     */
    private int textSync(ChangeRootTexture crtMonitor, Texture oldT, int current_level) {
        int nTextToLoad = 0;
        try {
            Texture child;
            boolean bDynamicChildren = false;
            if (oldT.children != null) {
                children = new Texture[oldT.children.length];
                float[][][] grid_pointer = Globals.points;
                if (bDynamicGrid) {
                    grid_pointer = parent.grid;
                }
                if (((nWidth == 1) || (nHeight == 1)) || bDynamicGrid) {
                    bDynamicChildren = true;
                    // the conditions to create a dynamic grid are:
                    // there are no points left to divide the static grid on x or on y, or
                    // this node is already a dynamic grid based, so, its children must also be dynamic
                    // the second condition also implies that nWidth =1 and nHeight =1, but it is there for
                    // clarity reasons
                    // create dynamic grid
                    if (grid == null) {
                        // create with one aditional point because 1 texture is guarded by 2 points,
                        // and nx is the number of textures...
                        grid = new float[3][oldT.grid[0].length][oldT.grid[0][0].length];
                        // instantiate the grid, based on parent coordinates
                        // prepare parent coordinate for init grid function
                        lt_grid[0] = grid_pointer[0][nWorldY][nWorldX];
                        lt_grid[1] = grid_pointer[1][nWorldY][nWorldX];
                        lt_grid[2] = grid_pointer[2][nWorldY][nWorldX];
                        lb_grid[0] = grid_pointer[0][nWorldY + nHeight][nWorldX];
                        lb_grid[1] = grid_pointer[1][nWorldY + nHeight][nWorldX];
                        lb_grid[2] = grid_pointer[2][nWorldY + nHeight][nWorldX];
                        rt_grid[0] = grid_pointer[0][nWorldY][nWorldX + nWidth];
                        rt_grid[1] = grid_pointer[1][nWorldY][nWorldX + nWidth];
                        rt_grid[2] = grid_pointer[2][nWorldY][nWorldX + nWidth];
                        rb_grid[0] = grid_pointer[0][nWorldY + nHeight][nWorldX + nWidth];
                        rb_grid[1] = grid_pointer[1][nWorldY + nHeight][nWorldX + nWidth];
                        rb_grid[2] = grid_pointer[2][nWorldY + nHeight][nWorldX + nWidth];

                        initDynamicGrid(grid);
                    }
                }
                for (int i = 0; i < oldT.children.length; i++) {
                    if (oldT.children[i] != null) {
                        child = new Texture(0,
                                // set world position and texture id for each children
                                oldT.children[i].nWorldX, oldT.children[i].nWorldY, oldT.children[i].nWidth,
                                oldT.children[i].nHeight, S_NONE);
                        children[i] = child;
                        child.setParent(this);
                        child.setStatus(S_SHOULD_LOAD);
                        child.setBDynamicGrid(bDynamicChildren);
                        String path = child.textCreatePathDyn(current_level).toString();
                        // System.out.println("TextSync SHOULD_LOAD: "+path);

                        TextureLoadJobResult tres = new TextureLoadJobResult();
                        tres.texture = child;
                        tres.path = path;
                        tres.level = current_level;
                        tres.crtMonitor = crtMonitor;
                        tres.treeID = crtMonitor.getTreeID();

                        // add this slice to slices that should have texture loaded
                        texturesToLoad.offer(tres);
                        // set this texture to be monitorized for loading
                        nTextToLoad++;

                        // notify the sleeping thread that new textures are available to load
                        // set for loading the child texture also
                        nTextToLoad += child.textSync(crtMonitor, oldT.children[i], current_level + 1);
                        // add all child textures set for loading
                    }
                }
            }
        } catch (Exception ex) {
            // old texture could have become unavailable
            ex.printStackTrace();
        }
        return nTextToLoad;
    }
}
