package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.util.concurrent.LinkedBlockingQueue;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.monitor.AppConfig;

/*
 * Created on May 13, 2004
 *
 */
/**
 * @author mluc
 *
 * defines variables and constants for use in Texture class
 */
public class TextureParams {
    //I don't need its index because I know the index in the parent
    //I don't need world position and dimensions because will be computed on tree traversal
    //but it's better to compute them only once
    //I don't need zoom because it is stored in main program
    protected int texture_id;//opengl texture id
    protected Texture[] children = null;
    protected Texture parent = null;

    protected String resultID; // treeID + path to this texture; set after texture is loaded

    public static String sMapExt = ".oct";
    /** map file extension */

    //    protected static boolean bInDrawFunction=false;
    //    public static boolean bInComputeFunction=false;
    //    protected Date lastAccessTime = null;
    /** texture is visible or not, set as the result of textVisible() */
    protected boolean bIsVisible = false;
    /** texture should be checked for visibility?, if true, run textVisible, else, use bIsVisible flag */
    protected boolean bCheckVisibility = true;
    /**
     * contains the points for position child textures on world map,
     * for detail levels that exceed the capacity of global static grid =>
     * create a dinamic grid that is used only for child textures, and only
     * if there is at least one child
     * <br>
     * punctele dinamice de pozitionare pentru copiii care detaliaza peste
     * capacitatea grilei statice
     * <br>
     * first indice goes from 0 to 2 -> gives spatial coordinates (x,y,z)<br>
     * second indice is for y axis<br>
     * and third is for x axis<br>
     * so, for x spatial coordinate there are all y rows, and for each row, there
     * are all x columns
     */
    protected float[][][] grid = null;
    //what grid should be used to draw texture: static one or parent dinamic one
    protected boolean bDynamicGrid = false;
    //position of texture on the map
    protected int nWorldX = 0, nWorldY = 0;//indices of position in parent grid
    protected int nWidth = Globals.nrDivisionsX, nHeight = Globals.nrDivisionsY;
    //protected float worldX=-Globals.MAP_WIDTH*.5f, worldY=Globals.MAP_HEIGHT*.5f, width=Globals.MAP_WIDTH, height=Globals.MAP_HEIGHT;
    //position of texture in the bigger texture if any ( full texture: 0-1 )
    protected float leftT = 0f, bottomT = 0f, rightT = 1f, topT = 1f;
    /** geographical coordinates for texture on a 2d map or 3d map relative to earth rotation axis*/
    //    protected float fLongStart, fLongEnd, fLatStart, fLatEnd;//replaced by vectors
    /** the vectors that establish the surface of the texture */
    //    protected VectorO vTopLeft, vTopRight, vBottomLeft, vBottomRight;
    //special flags to set options for loaded texture
    public static final int S_SPECIAL_NONE = 0;
    public static final int S_SPECIAL_NO_LIGHTS = 1;//picture will not have lights on it
    public static final int S_SPECIAL_NO_NIGHT = 2;//picture will not have night on it
    protected int special_flags = S_SPECIAL_NONE;
    //constants to state types of loading status
    public static final int S_NONE = 0;
    public static final int S_SHOULD_LOAD = 1;
    public static final int S_ALWAYS = 2;//must not be dealocated or changed
    public static final int S_FILE_NOT_FOUND = 4;//file not on disk
    public static final int S_LOADED = 8;//texture already loaded
    public static final int S_DEREFERENCED = 16;//sliced has been removed, reference is invalid
    public static final int S_SET = 32;
    public static final int S_MAX_LEVEL = 64;//this is max level, no children
    //field used to identify the loading status of a slice
    protected int status = S_NONE;

    public boolean checkStatus(int flag) {
        return ((status & flag) > 0);
    }

    /**
     * sets the specified set of ORed flags to status
     * @param flag
     */
    public void setStatus(int flag) {
        status |= flag;
    }

    /**
     * clears the specified set of ORed flags from status
     * @param flag
     */
    public void clearStatus(int flag) {
        status ^= (status & flag);
    }//status|=flag; status^=flag; }

    /**
     * reset status loses S_SHOULD_LOAD, S_ALWAYS, S_FILE_NOT_FOUND, S_LOADED, S_SET
     * but keeps S_NONE, S_MAX_LEVEL, S_DEREFERENCED
     */
    public void resetStatus() {
        status = (S_NONE | (status & S_MAX_LEVEL) | (status & S_DEREFERENCED));
    }

    public void setSpecialFlags(int flags) {
        special_flags |= flags;
    }

    public int getSpecialFlags() {
        return special_flags;
    }

    public boolean checkSpecialFlag(int flag) {
        return ((special_flags & flag) > 0);
    }

    public static boolean checkFlagIsSet(int flags, int one_flag) {
        return ((flags & one_flag) > 0);
    }

    /**
     * arrays that defines the resolution of full picture on each level
     * neccessary to compute the resolution per slice
     */
    public static int nInitialLevel = 0;//Integer.parseInt(AppConfig.getProperty("jogl.Texture.nInitialLevel","0"));//for usual client is 0
    /**
     * maximum level allowed for textures. Any texture at this level that wants
     * to load new better looking textures, it can't.
     */
    public static int nMaximumLevel = -1;
    //	public static int[] resolutionX = { 1024, 2048, 4096, 8192};
    //	public static int[] resolutionY = { 512, 1024, 2048, 4096};
    //	public static int[] resolutionX = { 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288};
    //	public static int[] resolutionY = { 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144};
    public static int resolutionX0 = 1024;
    public static int resolutionY0 = 512;
    //arrays that contain for each level of detail info like:
    //number of pieces on x, on y and the depth the next level should apply
    //probably the last value in depth array must be eliminated, is redundant
    //	public static int[] texturesX = {8,2,2,2};
    //	public static int[] texturesY = {4,2,2,2};
    //	public static float[] depthZ = {4f, 1f, .5f};//{2f, .5f, .125f};
    //for 512 textures settings
    //    public static int[] texturesX = {2,2,2,2,2,2,2,2,2,2};
    //    public static int[] texturesY = {1,2,2,2,2,2,2,2,2,2};
    //for 256 textures settings
    //    public static int[] texturesX = {4,2,2,2,2,2,2,2,2,2};
    //    public static int[] texturesY = {2,2,2,2,2,2,2,2,2,2};
    public static final int texturesX0;
    public static final int texturesY0;

    public static final int OCT_WIDTH;
    public static final int OCT_HEIGHT;
    /** any texture file length */
    public static final int nTextureFileLength;
    static {
        texturesX0 = AppConfig.geti("nrXtiles0", 4);
        texturesY0 = AppConfig.geti("nrYtiles0", 2);
        OCT_WIDTH = resolutionX0 / texturesX0;
        OCT_HEIGHT = resolutionY0 / texturesY0;
        nTextureFileLength = 3 * OCT_WIDTH * OCT_HEIGHT;
    }
    //	public static int[] texturesX = {8,2,2,2,2,2,2,2,2,2};
    //	public static int[] texturesY = {4,2,2,2,2,2,2,2,2,2};
    //public static float[] depthZ = {16f, 4f, 2f, 1f, .5f, .25f, .125f, .0625f, .03125f/*, .015625f, .0078125f/*, .00390625f*/};
    //    public static float[] depthZ = {4f, 1f, .5f, .25f, .125f, .0625f, .03125f, .015625f, .0078125f/*, .00390625f*/};
    public static float[] depthZ = { 14f, 7f, 2f, 1f, .5f, .25f, .125f, .0625f, .03125f /*, .015625f/*, .0078125f/*, .00390625f*/};
    //object used for synchronisation between threads that use common variables
    protected static Object syncObject_Texture = new Object();

    //queue containing the textures to load fifo
    public static LinkedBlockingQueue<TextureLoadJobResult> texturesToLoad = new LinkedBlockingQueue<TextureLoadJobResult>();
    //vector containing the textures to unload/unset
    public static LinkedBlockingQueue<JoGLCachedTexture> texturesToUnSet = new LinkedBlockingQueue<JoGLCachedTexture>();
    //public static Object waitForTexturesToLoad = new Object();
    //queue containing the textures with data array to be loaded into opengl
    public static LinkedBlockingQueue<TextureLoadJobResult> texturesToSet = new LinkedBlockingQueue<TextureLoadJobResult>();

    // holds space allocated for the textures that are being loaded
    public static TextureDataSpace textureDataSpace = new TextureDataSpace(nTextureFileLength);

    public static final String pathSeparator = System.getProperty("file.separator");
    public static String pathToTextures = "";//"lia"+pathSeparator+"images"+pathSeparator;/*System.getProperty("user.dir")+System.getProperty("file.separator")+"bin"+System.getProperty("file.separator")+*/

    /** sets special parameters for creating a map texture that looks nicer */
    public static boolean bNiceRendering = true;

    /**
     * @return Returns the texture_id.
     */
    public int getTextureId() {
        return texture_id;
    }

    /**
     * @return Returns the parent.
     */
    public Texture getParent() {
        return parent;
    }

    /**
     * @param parent The parent to set.
     */
    protected void setParent(Texture parent) {
        this.parent = parent;
        if (parent != null) {
            this.special_flags = parent.special_flags;
        }
    }

    /**
     * Sets this texture's geographical coordinates.<br>
     * longitude goes from negative value to positive (-180 to 180)<br>
     * latitude goes from positive to negative value (90 to -90)<br>
     * Sep 13, 2005 11:42:09 AM - mluc<br>
     * @param longS - start coordinate (left)
     * @param longE - end longitude
     * @param latS - start latitude (up)
     * @param latE - end latitude (down)
     */
    //    protected void setCoordinates( float longS, float longE, float latS, float latE )
    //    {
    //        fLongStart = longS;
    //        fLatStart = latS;
    //        fLongEnd = longE;
    //        fLatEnd = latE;
    //    }
    //
    //    protected void setVectors( VectorO vtl, VectorO vtr, VectorO vbl, VectorO vbr)
    //    {
    //        vTopLeft = vtl;
    //        vTopRight = vtr;
    //        vBottomLeft = vbl;
    //        vBottomRight = vbr;
    //    }

    //    public static VectorO getVector( float[][][] grid_pointer, int startX, int startY, int nx, int ny, VectorO vReusable)
    //    {
    //        if ( vReusable == null )
    //            vReusable = new VectorO();
    //        vReusable.setXYZ(grid_pointer[0][startY][startX], grid_pointer[1][startY][startX], grid_pointer[2][startY][startX]);
    //        return vReusable;
    //    }

    /**
     * draws a part of the map from given coordinates with given dimensions, according to the current
     * map form (plane or on sphere)
     * @param gl
     * @param startX
     * @param startY
     * @param nx
     * @param ny
     * @param lT
     * @param bT
     * @param rT
     * @param tT
     */
    public static void drawMapSlice(GL2 gl, float[][][] grid_pointer, int startX, int startY, int nx, int ny, float lT,
            float bT, float rT, float tT) {
        if (grid_pointer == Globals.points) {//use static grid
            synchronized (JoglPanel.globals.syncGrid) {
                if (JoglPanel.globals.globeRadius == -1) {
                    gl.glBegin(GL.GL_TRIANGLE_STRIP);
                    //System.out.println("p left,top=("+grid_pointer[startY][startX][0]+","+grid_pointer[startY][startX][1]+","+grid_pointer[startY][startX][2]+")");
                    gl.glTexCoord2f(lT, tT);
                    gl.glVertex3f(grid_pointer[0][startY][startX], grid_pointer[1][startY][startX],
                            grid_pointer[2][startY][startX]);
                    //System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
                    gl.glTexCoord2f(lT, bT);
                    gl.glVertex3f(grid_pointer[0][startY + ny][startX], grid_pointer[1][startY + ny][startX],
                            grid_pointer[2][startY + ny][startX]);
                    //System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
                    gl.glTexCoord2f(rT, tT);
                    gl.glVertex3f(grid_pointer[0][startY][startX + nx], grid_pointer[1][startY][startX + nx],
                            grid_pointer[2][startY][startX + nx]);
                    //System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
                    //gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
                    //System.out.println("p right,bottom=("+grid_pointer[startY+ny][startX+nx][0]+","+grid_pointer[startY+ny][startX+nx][1]+","+grid_pointer[startY+ny][startX+nx][2]+")");
                    gl.glTexCoord2f(rT, bT);
                    gl.glVertex3f(grid_pointer[0][startY + ny][startX + nx], grid_pointer[1][startY + ny][startX + nx],
                            grid_pointer[2][startY + ny][startX + nx]);
                    //System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
                    //gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
                    gl.glEnd();
                } else {
                    float stepxt = (rT - lT) / nx;
                    float stepyt = (tT - bT) / ny;
                    for (int y = 0; y < ny; y++) {
                        for (int x = 0; x < nx; x++) {
                            gl.glBegin(GL.GL_TRIANGLE_STRIP);
                            gl.glTexCoord2f(lT + (x * stepxt), tT - (y * stepyt));
                            gl.glVertex3f(grid_pointer[0][startY + y][startX + x], grid_pointer[1][startY + y][startX
                                    + x], grid_pointer[2][startY + y][startX + x]);
                            gl.glTexCoord2f(lT + (x * stepxt), tT - ((y + 1) * stepyt));
                            gl.glVertex3f(grid_pointer[0][startY + y + 1][startX + x],
                                    grid_pointer[1][startY + y + 1][startX + x], grid_pointer[2][startY + y + 1][startX
                                            + x]);
                            gl.glTexCoord2f(lT + ((x + 1) * stepxt), tT - (y * stepyt));
                            gl.glVertex3f(grid_pointer[0][startY + y][startX + x + 1],
                                    grid_pointer[1][startY + y][startX + x + 1], grid_pointer[2][startY + y][startX + x
                                            + 1]);
                            //gl.glTexCoord2f( lT+x*stepxt, tT-(y+1)*stepyt);gl.glVertex3f( grid_pointer[0][startY+y+1][startX+x], grid_pointer[1][startY+y+1][startX+x], grid_pointer[2][startY+y+1][startX+x]);
                            gl.glTexCoord2f(lT + ((x + 1) * stepxt), tT - ((y + 1) * stepyt));
                            gl.glVertex3f(grid_pointer[0][startY + y + 1][startX + x + 1], grid_pointer[1][startY + y
                                    + 1][startX + x + 1], grid_pointer[2][startY + y + 1][startX + x + 1]);
                            //gl.glTexCoord2f( lT+(x+1)*stepxt, tT-y*stepyt);gl.glVertex3f( grid_pointer[0][startY+y][startX+x+1], grid_pointer[1][startY+y][startX+x+1], grid_pointer[2][startY+y][startX+x+1]);
                            gl.glEnd();
                        }
                    }
                }
            }
        } else {//draw from dynamic grid
            //two triangles: l-t > l-b > r-t and l-b > r-b > r-t
            //derived from a l-t > l-b > r-t > r-b
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            //System.out.println("p left,top=("+grid_pointer[startY][startX][0]+","+grid_pointer[startY][startX][1]+","+grid_pointer[startY][startX][2]+")");
            gl.glTexCoord2f(lT, tT);
            gl.glVertex3f(grid_pointer[0][startY][startX], grid_pointer[1][startY][startX],
                    grid_pointer[2][startY][startX]);
            //System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
            gl.glTexCoord2f(lT, bT);
            gl.glVertex3f(grid_pointer[0][startY + ny][startX], grid_pointer[1][startY + ny][startX],
                    grid_pointer[2][startY + ny][startX]);
            //System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
            gl.glTexCoord2f(rT, tT);
            gl.glVertex3f(grid_pointer[0][startY][startX + nx], grid_pointer[1][startY][startX + nx],
                    grid_pointer[2][startY][startX + nx]);
            //System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
            //gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
            //System.out.println("p right,bottom=("+grid_pointer[startY+ny][startX+nx][0]+","+grid_pointer[startY+ny][startX+nx][1]+","+grid_pointer[startY+ny][startX+nx][2]+")");
            gl.glTexCoord2f(rT, bT);
            gl.glVertex3f(grid_pointer[0][startY + ny][startX + nx], grid_pointer[1][startY + ny][startX + nx],
                    grid_pointer[2][startY + ny][startX + nx]);
            //System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
            //gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
            gl.glEnd();
        }
    }

    /**
     * recomputes the dynamic grills points positions after the positions in
     * static grill have been modified
     */
    public void computeDynGrid() {
        if (grid != null) {
            float[][][] grid_pointer = Globals.points;
            if (bDynamicGrid) {
                grid_pointer = parent.grid;
            }
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

        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    children[i].computeDynGrid();
                }
            }
        }
    }

    /**
     * variables used to set starting and ending coordinates for initDynamicGrid function
     */
    protected static float[] lt_grid = new float[3];
    protected static float[] rt_grid = new float[3];
    protected static float[] lb_grid = new float[3];
    protected static float[] rb_grid = new float[3];

    /**
     * computes points for dynamic grid based on four starting points: left-bottom, right-bottom, right-top, left-top.<br>
     * The algorithm is as follows:<br>
     * 1. consider the 2 triangles that can be created with 4 points: lt-lb-rt and lb-rb-rt<br>
     * 2. for each triangle the points are generated on triangle plane, on lines parallel with lt-lb and lt-rt, and
     * lb-rb and rb-rt.<br>
     * 3. for first triangle, the directions are t=(rt-lt) and l=(lb-lt).<br>
     * 	- for i = 0 to nx<br>
     * 		- consider ti = |t|*i/nx<br>
     * 		- on the other direction, lj, the triangle base is the limit to where it can go, so<br>
     * 		- for j = 0 to (nx-i)*ny/nx<br>
     * 			- compute point(i,j)<br>
     * How that the limit is (nx-i)*ny/nx? Because, for a ti, the limit li is lli = |l|*(|t|-ti)/|t|, as stated by
     * similarity theorem for triangles.<br>
     * 4. the same for lower triangle<br>
     * <br>
     * To compute a point's coordinates, for first triangle use:
     * 	p[x] = lt[x]+dtx*i+dlx*j, where dtx is t/nx for x axis, and dlx is l/nt for y axis
     * Problem: correct algorithm for sphere computing of points!!!!!!!!!!!!!!
     * Solution: the alghorithm changed to:
     * 	-> create a equation for each axis ( x, y, z) using the four corners values and indexes i and j,
     * so that 	for i=0 and j=0 value of point is upper left corner,
     * 			for i=nx, j=0 value of point is upper right corner,
     * 			for i=0, j=ny value of point is lower left corner,
     * 			for i=nx, j=ny value of point is lower right corner.
     *
     * The equation is, for each axis:
     * 	lt*(nx-i)/nx*(ny-j)/ny + rt*i/nx*(ny-j)/ny + lb*(nx-i)/nx*j/ny + rb*i/nx*j/ny
     * @param grid already allocated grid to fill with new values
     */
    protected static void initDynamicGrid(float[][][] grid) {
        /**
         * deduce nx and ny = the number of points for x and y so called axis
         */
        int nx = grid[0][0].length - 1;
        int ny = grid[0].length - 1;
        /**
         * another computing algorithm:
         * each corner has a weight:
         * lt -> (nx-i)/nx * (ny-j)/ny
         * rt -> i/nx * (ny-j)/ny
         * lb -> (nx-i)/nx * j/ny
         * rb -> i/nx * j/ny
         */
        for (int j = 0; j <= ny; j++) {
            for (int i = 0; i <= nx; i++) {
                grid[0][j][i] = ((((lt_grid[0] * (nx - i)) / nx) * (ny - j)) / ny)
                        + ((((rt_grid[0] * i) / nx) * (ny - j)) / ny) + ((((lb_grid[0] * (nx - i)) / nx) * j) / ny)
                        + ((((rb_grid[0] * i) / nx) * j) / ny);
                grid[1][j][i] = ((((lt_grid[1] * (nx - i)) / nx) * (ny - j)) / ny)
                        + ((((rt_grid[1] * i) / nx) * (ny - j)) / ny) + ((((lb_grid[1] * (nx - i)) / nx) * j) / ny)
                        + ((((rb_grid[1] * i) / nx) * j) / ny);
                grid[2][j][i] = ((((lt_grid[2] * (nx - i)) / nx) * (ny - j)) / ny)
                        + ((((rt_grid[2] * i) / nx) * (ny - j)) / ny) + ((((lb_grid[2] * (nx - i)) / nx) * j) / ny)
                        + ((((rb_grid[2] * i) / nx) * j) / ny);
            }
        }
        ;
        /**
         * set the x and y direction unitar vectors, x is equivalent with m, and y with n
         * for first triangle, top is m and left is n
         */
        /*		float dmx, dmy, dmz, dnx, dny, dnz;
        		//computation for upper triangle
        		//first compute delta deplacement on the 3 axis for each vector: left and top
        		dmx = (rt_grid[0]-lt_grid[0])/nx;
        		dmy = (rt_grid[1]-lt_grid[1])/nx;
        		dmz = (rt_grid[2]-lt_grid[2])/nx;
        		dnx = (lb_grid[0]-lt_grid[0])/ny;
        		dny = (lb_grid[1]-lt_grid[1])/ny;
        		dnz = (lb_grid[2]-lt_grid[2])/ny;
        		for ( int i=0; i<=nx; i++ ) {
        			//ti is now dm_*i
        			for ( int j=0; j<=(nx-i)*ny/nx; j++) {//the equal sign is not considered because it is computed on next step, for lower triangle
        				grid[0][j][i] = lt_grid[0]+dmx*i+dnx*j;
        				grid[1][j][i] = lt_grid[1]+dmy*i+dny*j;
        				grid[2][j][i] = lt_grid[2]+dmz*i+dnz*j;
        			}
        		}
        		//computation for lower triangle
        		//recompute delta deplacement on the 3 axis for each vector: bottom and right
        		dmx = (rb_grid[0]-lb_grid[0])/nx;
        		dmy = (rb_grid[1]-lb_grid[1])/nx;
        		dmz = (rb_grid[2]-lb_grid[2])/nx;
        		dnx = (rb_grid[0]-rt_grid[0])/ny;
        		dny = (rb_grid[1]-rt_grid[1])/ny;
        		dnz = (rb_grid[2]-rt_grid[2])/ny;
        		for ( int i=0; i<=nx; i++ ) {
        			//ti is now dm_*i
        			for ( int j=(nx-i)*ny/nx; j<=ny; j++) {
        				grid[0][j][i] = lt_grid[0]+dmx*i+dnx*j;
        				grid[1][j][i] = lt_grid[1]+dmy*i+dny*j;
        				grid[2][j][i] = lt_grid[2]+dmz*i+dnz*j;
        			}
        		}
         */
        //print the grid
        //		System.out.println("bounding coordinates:");
        //		System.out.println("left-top corner: ("+lt_grid[0]+","+lt_grid[1]+","+lt_grid[2]+")");
        //		System.out.println("right-top corner: ("+rt_grid[0]+","+rt_grid[1]+","+rt_grid[2]+")");
        //		System.out.println("left-bottom corner: ("+lb_grid[0]+","+lb_grid[1]+","+lb_grid[2]+")");
        //		System.out.println("right-bottom corner: ("+rb_grid[0]+","+rb_grid[1]+","+rb_grid[2]+")");
        //		System.out.println("generated grid:");
        //		for ( int j=0; j<=ny; j++) {
        //			for ( int i=0; i<=nx; i++ ) {
        //				System.out.print("("+grid[0][j][i]+","+grid[1][j][i]+","+grid[2][j][i]+") ");
        //			}
        //			System.out.println("");
        //		};
    }

    /**
     * Recontstructs the path for the texture based on world coordinates
     * and number of texture on x and y for each level
     * @param currentLevel the level on which this slice is
     * @param nwX position on static grid or dynamic one on x axis
     * @param nwY position on static grid or dynamic one on y axis
     * @param bDynamic flag that states to use static grid or dynamic one
     * @param parent used only for dynamic grid, to compute world positions
     * @return the computed path on position 0 and the prefix for last level on position 1<br>
     * The full path can be obtained by concatenating this path with pathToTextures
     */
    public static StringBuilder createTexturePath(int currentLevel, int nwX, int nwY) {
        //String[] ret = new String[2];
        StringBuilder path = new StringBuilder();//"map0";
        //		String prefix = "";
        float widthLOD, heightLOD;
        int x, y;
        widthLOD = Globals.nrDivisionsX;
        heightLOD = Globals.nrDivisionsY;
        int x_map = 0;
        int y_map = 0;
        //for each level idetify the slice number
        for (int l = 0; l <= currentLevel; l++) {
            x = 0;
            if (l == 0) {
                widthLOD /= texturesX0;
            } else {
                widthLOD /= 2;
            }
            while ((x_map + widthLOD) <= nwX) {
                x++;
                x_map += widthLOD;
            }
            ;
            y = 0;
            if (l == 0) {
                heightLOD /= texturesY0;
            } else {
                heightLOD /= 2;
            }
            while ((y_map + heightLOD) <= nwY) {
                y++;
                y_map += heightLOD;
            }
            ;
            //old notation
            //			prefix+="_"+(y+1)+"."+(x+1);
            //			path += pathSeparator+"map"+(l+1)+prefix;
            //new computation
            if (path.length() > 0) {
                path.append(pathSeparator);
            }
            path.append((y + 1) + "." + (x + 1));
        }
        ;
        //		ret[0] = path.toString();
        //		ret[1] = prefix;
        return path;
    }

    /**
     * @return Returns the bDynamicGrid.
     */
    public boolean isBDynamicGrid() {
        return bDynamicGrid;
    }

    /**
     * @param dynamicGrid The bDynamicGrid to set.
     */
    public void setBDynamicGrid(boolean dynamicGrid) {
        bDynamicGrid = dynamicGrid;
    }
}
