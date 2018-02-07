package lia.web.servlets.map2d;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;

/*
 * Created on May 7, 2004
 *
 */
/**
 * @author mluc
 *
 * class containing the definition of texture tree
 */
public class Texture extends TextureParams {

    //public static Hashtable ht=new Hashtable();
    /**
     * implicit constructor that does nothing, because the data is already initialized
     */
    public Texture() {

    }

    /**
     * constuctor to initialize a node in the tree
     * @param tex_id opengl id for texture
     */
    public Texture(BufferedImage bi) {
        this();
        biTexture = bi;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions
     * @param tex_id opengl id for texture
     * @param wX world position on x axis -> indice on x for array
     * @param wY world position on y axis
     * @param w width on map ( world map ) -> number of divisions covered by this texture
     * @param h height on map
     */
    public Texture(BufferedImage bi, int wX, int wY, int w, int h) {
        this(bi);
        nWorldX = wX;
        nWorldY = wY;
        nWidth = w;
        nHeight = h;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions
     * and a given status
     * @param tex_id opengl id for texture
     * @param wX world position on x axis
     * @param wY world position on y axis
     * @param w width on map ( world map )
     * @param h height on map
     * @param status texture loading indicator
     */
    public Texture(BufferedImage bi, int wX, int wY, int w, int h, int status) {
        this(bi);
        nWorldX = wX;
        nWorldY = wY;
        nWidth = w;
        nHeight = h;
        this.status = status;
    }

    /**
     * constuctor to initialize a node in the tree
     * having associated the world positions and dimensions<br>
     * and the slice coordinates in texture
     * @param tex_id opengl id for texture
     * @param wX world position on x axis
     * @param wY world position on y axis
     * @param w width on map ( world map )
     * @param h height on map
     * @param lT left position in texture
     * @param bT bottom position in texture 
     * @param rT right position in texture
     * @param tT top position in texture
     */
    public Texture(BufferedImage bi, int wX, int wY, int w, int h, float lT, float bT, float rT, float tT) {
        this(bi, wX, wY, w, h);
        leftT = lT;
        bottomT = bT;
        rightT = rT;
        topT = tT;
    }

    /**
     * Loads a texture from file into an byte array<br>
     * first check if an weak reference to data exists and if it is valid<br>
     * if not, loads from file
     * @param fileName the full path to file
     * @param data represents an array that will be used if not null, instead of creating a new array based on 
     * file length
     * @param vbType is a vector with an element to remember the place the texture was loaded from
     * @return the byte array containing the 3 byte pixels of texture
     */
    public static BufferedImage loadTextureFromFile(String fileName, byte[] data, int level, int[] vbType) {

        BufferedImage bi = null;
        int loadedFrom;//stores place where from last image was loaded
        //range: 0 - disc, 1 - memory -> must be less than 256 
        //		long startTime = System.currentTimeMillis();
        //System.out.print("Loading file "+fileName+" ... ");
        //trim the filename to get the hashtable identifier
        int startF = fileName.lastIndexOf('/');
        String id;
        //get only the file name, remove the path
        id = fileName.substring(startF + 1);
        //remove extension: ".oct"
        id = id.substring(0, id.length() - 4);
        //remove "mapX_" prefix
        startF = id.indexOf('_');
        id = id.substring(startF + 1);
        //"id" now is the hashtable identifier
        //because it becomes a strong reference, remove the weak one
        //		WeakReference wr;
        //		wr = (WeakReference)texturesData.remove(id);
        SoftReference sr;
        sr = (SoftReference) texturesData.remove(id);
        //check to see if weak reference still points to something
        //		Object obj;

        int width, height;
        width = resolutionX[level];
        height = resolutionY[level];
        int file_length = 0;
        //				file_length = resolutionX[level]*resolutionY[level];
        for (int i = 0; i <= level; i++) {
            //					file_length/=(texturesX[i]*texturesY[i]);
            width /= texturesX[i];
            height /= texturesY[i];
        }
        ;
        file_length = width * height * 3;
        //if ( wr!= null && (obj=wr.get())!=null ) {
        /*
        if ( sr!= null && (obj=sr.get())!=null ) {
        	System.out.print(" from memory ");
        	loadedFrom = 1;
        	data = null;//free previous allocated array
        	data = (byte[])obj;//return the referent of weak reference
        } else {//else (re)load the image data from file
        	*/
        //System.out.print(" from disc ");
        loadedFrom = 0;
        try {
            if (data == null) {
                data = new byte[file_length];
                //				ClassLoader myClassLoader = Globals.class.getClassLoader();
            }

            //InputStream in = ClassLoader.getSystemResourceAsStream(fileName);	
            InputStream in = new FileInputStream(fileName);

            //if(in==null)
            //System.out.println("ERROR: in is null");
            /*
            if(in!=null)
            	ht.put(fileName,in);
            else
            	in=(InputStream)ht.get(fileName);
            */
            //FileInputStream in = new FileInputStream("/home/alexc/test/MSRC/MonaLisa/Repository/MLrepository/tomcat/webapps/ROOT/WEB-INF/classes/"+fileName);
            BufferedInputStream bis = new BufferedInputStream(in);
            /*
            if(in==null){
            	System.out.println("ERROR: in is null ");
            	bis = new BufferedInputStream( myClassLoader.getResourceAsStream(fileName));
            }else
            	bis = new BufferedInputStream(in) ;
            if(bis==null)
            	System.out.println("bis is null :((");
            */
            bis.read(data);
            in.close();
            bis.close();
            //in.close();

        } catch (MalformedURLException e2) {
            data = null;
            e2.printStackTrace();
        } catch (FileNotFoundException e) {
            data = null;
            e.printStackTrace();
        } catch (IOException e1) {
            data = null;
            e1.printStackTrace();
        }
        if (data != null) {//if there is something to refer to
        //				wr = new WeakReference(data);
        //				texturesData.put( id, wr);
            //System.out.println("data is not null");
            sr = new SoftReference(data);
            texturesData.put(id, sr);

            int[] data_int = new int[file_length];
            for (int i = 0; i < file_length; i++) {
                data_int[i] = data[i];
            }

            try {
                bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int i = 0; i < width; i++) {
                    for (int j = 0; j < height; j++) {
                        bi.setRGB(i, height - 1 - j, ((data[3 * (i + (j * width))]) << 16)
                                + ((data[(3 * (i + (j * width))) + 1]) << 8) + (data[(3 * (i + (j * width))) + 2]));
                    }
                }

                //bi.setRGB(0,0,width,height,data_int,0,width);
            } catch (Exception e) {
                e.printStackTrace();
            }

            //data will not be null'ed now because it will be few moments later
        } else {
            //System.out.println("data is null");
        }

        //};
        //		long endTime = System.currentTimeMillis();
        //logger.log( Level.INFO, "Loading file "+fileName+" ...  "+(loadedFrom==1?"from memory":"from disc")+"  in "+(endTime-startTime)+" ms... "+(bi!=null?"success":"failed"));
        //System.out.println(" in "+(endTime-startTime)+" ms.");
        if (vbType != null) {
            vbType[0] = loadedFrom;
        }
        //return data;
        return bi;
    }

    /**
     * loads initial textures, no optimisation required
     * @param gl opengl graphical context
     * @return array of textures id that were loaded into opengl memory
     */
    public static BufferedImage[] loadInitialTextures(Map2D map2D) {
        BufferedImage[] textures = new BufferedImage[texturesX[0] * texturesY[0]];
        //gl.glGenTextures( textures.length, textures);// Create Small Textures
        //load in textures array the lowest resolution image available
        String sliceStartName = pathToTextures + "map0" + pathSeparator + "map1_";
        String sliceExt = ".oct";
        String fileName = "";
        byte[] data = null;
        int width, height;
        width = resolutionX[0] / texturesX[0];
        height = resolutionY[0] / texturesY[0];
        for (int y = 0; y < texturesY[0]; y++) {
            for (int x = 0; x < texturesX[0]; x++) {
                fileName = sliceStartName + (y + 1) + "." + (x + 1) + sliceExt;
                //gl.glBindTexture( GL.GL_TEXTURE_2D, textures[y*texturesX[0]+x]);
                //System.out.println("texture id: "+textures[y*texturesX[0]+x]);
                //data = loadTextureFromJar( "zoom.jar", fileName, data);
                textures[(y * texturesX[0]) + x] = loadTextureFromFile(fileName, data, 0, null);
                //cast shadow on image
                if (map2D.globals.show_shadow == 1) {
                    Shadow.setShadow(textures[(y * texturesX[0]) + x], width, height, x * width, y * height,
                            resolutionX[0], resolutionY[0], map2D.globals.show_lights);
                    //gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST);
                    //gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST);
                    //gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, 3, resolutionX[0]/texturesX[0], resolutionY[0]/texturesY[0], 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, data);
                }
            }
        }
        ;
        return textures;
    }

    /**
     * constructs an opengl texture from an byte array, with given width and height,
     * the byte array represent the 3-byte color array for an image
     * @param gl
     * @param data 3-byte array
     * @param width width of image
     * @param height height of image
     * @return Returns 
     */
    public static BufferedImage setTexture(byte[] data, int width, int height) {
        BufferedImage[] textures = new BufferedImage[1];
        int[] data_int = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            data_int[i] = data[i];
        }

        textures[0] = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        textures[0].setRGB(0, 0, width, height, data_int, 0, width);

        /*
        gl.glGenTextures( textures.length, textures);// Create Textures Id
        gl.glBindTexture( GL.GL_TEXTURE_2D, textures[0]);
        gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, 3, width, height, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, data);
        */
        data = null;//free the image data, because it is now in opengl memory
        return textures[0];
    }

    /**
     * constructs the initial tree of textures
     * @param gl opengl graphical context
     * @return updates the global root variable with the tree's root
     */
    public static void constructInitialTree(Map2D map2D) {
        synchronized (syncObject_Texture) {
            map2D.globals.root = new Texture();
            map2D.globals.root.map2D = map2D;
            map2D.globals.root.biTexture = null;
            BufferedImage[] textures = loadInitialTextures(map2D);
            int stepX, stepY;
            stepX = Globals.nrDivisionsX / texturesX[0];
            stepY = Globals.nrDivisionsY / texturesY[0];
            map2D.globals.root.children = new Texture[texturesX[0] * texturesY[0]];
            for (int y = 0; y < texturesY[0]; y++) {
                for (int x = 0; x < texturesX[0]; x++) {
                    //set world position and texture id for each children
                    //for first children in tree set always flag to stay forever
                    map2D.globals.root.children[(y * texturesX[0]) + x] = new Texture(textures[(y * texturesX[0]) + x],
                            map2D.globals.root.nWorldX + (stepX * x), map2D.globals.root.nWorldY + (stepY * y), stepX,
                            stepY, S_NONE | S_SHOULD_LOAD | S_LOADED | S_SET | S_ALWAYS);
                    map2D.globals.root.children[(y * texturesX[0]) + x].setParent(map2D.globals.root);
                }
            }
            ;
        }
        ;
    }

    /**
     * draws the tree of textures to cover the map
     * @param gl opengl graphical context
     */
    public void drawTree(Graphics2D g) {
        //gl.glColor3f( 1.0f, 1.0f, 1.0f);
        g.setColor(Color.WHITE);
        //starts the drawing from the root
        //System.out.println("startx="+startx+" endx="+endx+" starty="+starty+" endy="+endy);
        synchronized (syncObject_Texture) {
            //		    System.out.println("syncObject_Texture begin");
            //Main.globals.root.draw( gl, 0);
            for (Texture element : map2D.globals.root.children) {
                element.textDraw(g, 0);
                //		    System.out.println("syncObject_Texture end");
            }
        }
        ;
    }

    /*
    public void childrenNumber(Texture root){
    	System.out.println("len: "+root.children.length);
    	for(int i=0;i<root.children.length;i++){
    		
    	}
    }
    */

    /**
     * computes the view area and starts the recursive process of setting
     * slices for texture loading 
     */
    public void zoomChanged() {
        /** this should be computed in UserInput class */
        /** end should section */
        synchronized (syncObject_Texture) {
            for (Texture element : map2D.globals.root.children) {
                element.textZoomChanged(0);
            }
        }
        ;
        //call to redraw scene for maybe there where textures unloaded
        //JoglPanel.globals.canvas.repaint();
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
             * 		cell width and height are divided to number of children on x and y,
             * 		and the position of child is computed based on that of parent
             * and on position inside parent grid
             */

            //System.out.println("worldX: "+this.nWorldX+" worldY: "+this.nWorldY+" parent's children: "+parent.children+" parent grid: "+(parent.grid==null?"null":"not null")+" parent grid[0]: "+(parent.grid==null?"null":(parent.grid[0]==null?"null":"not null"))+" parent grid[0][0]: "+(parent.grid==null?"null":(parent.grid[0]==null?"null":(parent.grid[0][0]==null?"null":"not null"))));
            values[1] /= parent.grid[0][0].length;//child width is smaller than parent's
            values[0] += nWorldX * values[1];//get from parent to child on x
            values[3] /= parent.grid[0].length;//get number of rows (children on y axis)
            values[2] += nWorldY * values[3];//on y
        } else {
            values = new float[4];
            values[0] = nWorldX * Globals.divisionWidth;
            values[1] = Globals.divisionWidth;
            values[2] = nWorldY * Globals.divisionHeight;
            values[3] = Globals.divisionHeight;
        }
        return values;
    }

    /**
     * checks for new textures to load that, at the moment of checking
     * are still set for loading, and tries to load them
     */

    public static void loadNewTextures(Map2D map2d) {
        //		boolean bTexturesLoaded;
        while (true) {
            //wait for new textures
            synchronized (texturesToLoad) {
                if (texturesToLoad.isEmpty()) {
                    return;
                }
                ;
            }
            ;
            //			bTexturesLoaded = false;

            //vector with source of texture
            int vbType[] = new int[1];
            int loadedFrom;
            //if there is no texture, catch the exception
            try {
                while (true) {//check all available in the vector
                    Object[] tuplu = (Object[]) texturesToLoad.get(0);
                    Texture t = (Texture) tuplu[0];
                    if (t == null) {
                        break;
                    }
                    //System.out.println("texture to load: "+path);
                    boolean bLoad = false;
                    synchronized (syncObject_Texture) {
                        //check to see if the texture still should be loaded
                        //but first check to see if texture still exists
                        if (t.checkStatus(S_DEREFERENCED)) {
                            tuplu[0] = null;
                            t = null;
                        } else if (t.checkStatus(S_SHOULD_LOAD)) {
                            bLoad = true;
                        }
                    }
                    if (bLoad) {
                        byte[] data = null;
                        int level = ((Integer) tuplu[2]).intValue();
                        //data = loadTextureFromJar( "zoom.jar", pathToTextures+path+".oct", data);
                        BufferedImage bi = null;
                        if (tuplu[1] == null) {
                            t.setStatus(S_FILE_NOT_FOUND);
                        } else {
                            if (tuplu[1] instanceof String) {
                                String path = (String) tuplu[1];
                                //System.out.println( "try to load file: "+pathToTextures+path+".oct");
                                bi = loadTextureFromFile(pathToTextures + path + ".oct", data, level, vbType);
                            } else {
                                bi = (BufferedImage) tuplu[1];
                            }
                            //System.out.println("loaded from: "+vbType[0]);
                            loadedFrom = vbType[0];
                            //set the status for slice
                            synchronized (syncObject_Texture) {
                                //System.out.println("status: "+t.status+" texture to load2: "+path);
                                //it could have been modified, so check again
                                if (t.checkStatus(S_DEREFERENCED)) {
                                    tuplu[0] = null;
                                    t = null;
                                } else if (t.checkStatus(S_SHOULD_LOAD)) {
                                    //System.out.println("data: "+data);
                                    if (bi == null) {
                                        //t.resetStatus();
                                        t.setStatus(S_FILE_NOT_FOUND);
                                    } else {
                                        //t.resetStatus();
                                        t.setStatus(S_LOADED);
                                        //codification: tuplu[2] contains on first 8 bits the level, and on following 8 bits the storage
                                        // from where the image was loaded
                                        tuplu[2] = Integer.valueOf(level);//+((0xFF&loadedFrom)<<8));
                                        //check to see if parent still has children before trying to take grid from it
                                        //parent can have children==null although this is it's children because
                                        //this child should not exist
                                        //cast shadow on image
                                        if ((t.parent.children != null) && (loadedFrom == 0)) {
                                            int width, height;
                                            width = resolutionX[level];
                                            height = resolutionY[level];
                                            for (int i = 0; i <= level; i++) {
                                                width /= texturesX[i];
                                                height /= texturesY[i];
                                            }
                                            ;
                                            int mapx, mapy;
                                            float worldX, worldY;
                                            /** compute worldX and worldY **/
                                            float[] values = t.findDinamicPosition();
                                            worldX = values[0];
                                            worldY = values[2];
                                            //										worldX = t.nWorldX*Globals.divisionWidth;
                                            //										worldY = t.nWorldY*Globals.divisionHeight;

                                            mapx = (int) ((worldX * resolutionX[level]) / Globals.MAP_WIDTH);
                                            mapy = (int) ((worldY * resolutionY[level]) / Globals.MAP_HEIGHT);
                                            //System.out.println("set shadow for texture: width="+width+" height="+height+" mapx="+mapx+" mapy="+mapy+" resX="+resolutionX[level]+" resY="+resolutionY[level]);
                                            //System.out.println("set shadow for texture@ "+data);
                                            //System.out.println("show shadow="+map2d.globals.show_shadow);
                                            if (map2d.globals.show_shadow == 1) {
                                                Shadow.setShadow(bi, width, height, mapx, mapy, resolutionX[level],
                                                        resolutionY[level], map2d.globals.show_lights);
                                            }
                                        }
                                        //set for loading into opengl
                                        tuplu[1] = bi;
                                        texturesToSet.add(tuplu);
                                    }
                                    ;
                                }
                            }
                        }
                        ;
                        //System.out.println("tuplu[1] instanceof "+(tuplu[1]));
                    }
                    ;
                    //remove the tuplu either way, so that the thread could be correctly notified 
                    texturesToLoad.remove(tuplu);
                    tuplu = null;
                    //					bTexturesLoaded = true;
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
            }

            //if ( bTexturesLoaded )//new textures were loaded, so show them
            //JoglPanel.globals.canvas.repaint();
        }
    }

    /**
     * checks for loaded from file textures and updates the tree to show them, 
     * instead of the old ones<br>
     * if there are sufficient child textures, parent texture is unloaded
     * @param gl
     */
    public static void setTreeTextures() {
        try {
            //check for new textures loaded till the array is empty
            //when we'll have an exception
            while (true) {
                Object[] tuplu = (Object[]) texturesToSet.remove(0);
                //int level_and_loadedFrom = ((Integer)tuplu[2]).intValue();
                int level = ((Integer) tuplu[2]).intValue();//(level_and_loadedFrom&0xff);
                //int lFrom = ((level_and_loadedFrom>>8)&0xff);
                int width, height;
                width = resolutionX[level];
                height = resolutionY[level];
                for (int i = 0; i <= level; i++) {
                    width /= texturesX[i];
                    height /= texturesY[i];
                }
                ;
                //generate opengl texture from byte array
                //int t_id = setTexture( gl, (byte[])tuplu[1], width, height);
                BufferedImage bi = (BufferedImage) tuplu[1];//setTexture((byte[])tuplu[1], width, height);
                if (bi != null) {
                    synchronized (syncObject_Texture) {//update the tree synchronized
                    //					    System.out.println("syncObject_Texture begin");
                        //System.out.println("set texture id: "+t_id);
                        Texture t = (Texture) tuplu[0];
                        if (t.checkStatus(S_DEREFERENCED)) {
                            t = null;
                        } else {
                            t.biTexture = bi;
                            t.leftT = 0f;
                            t.rightT = 1.0f;
                            t.bottomT = 0f;
                            t.topT = 1f;
                            //t.resetStatus();
                            //System.out.println("Texture SET");
                            t.setStatus(S_SET);
                            //set all children to the new parent texture, if the case
                            if (t.children != null) {
                                //set for existing children that don't have their own texture this node's parent texture
                                for (Texture element : t.children) {
                                    if (element != null /*&& !children[i].checkStatus(S_SET)*/) {
                                        element.textSetParentTexture();
                                    }
                                }
                            }
                            //check to see if parent texture should be cleared
                            //recompute child textures parent id
                            t.getParent().textUnsetParentTexture();

                        }
                        ;
                        //					    System.out.println("syncObject_Texture end");
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
        }
    }

    /**
     * deletes from memory invisible textures ( not in the view frustum )
     * and not on the correct depth<br>
     * <b>Updated to:<b>This textures have already been put into an array texturesToUnSet, and
     * the only thing to be done is to delete them from opengl one by one.
     * @param gl
     */
    public static void unsetTreeTextures() {
        try {
            //			int[] textures = new int[1];
            //check for new textures loaded till the array is empty
            //when we'll have an exception
            while (true) {
                //				BufferedImage bi = (BufferedImage)
                texturesToUnSet.remove(0);
                //System.out.println(">>>>> unsetTreetextures: texture remove");
                /*
                if ( gl.glIsTexture(tid) ) {//this check also includes  (children[i].texture_id!= tex_id && children[i].texture_id>0)
                	textures[0] = tid;
                	gl.glDeleteTextures( 1, textures);
                	logger.log(Level.INFO, "unsetTreeTextures -> Delete texture with id: "+tid);
                	//System.out.println("unsetTreeTextures -> Delete texture with id: "+tid);
                };*/
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
        }
        ;
        //		synchronized ( syncObject_Texture ) {
        //			for ( int i=0; i<Main.globals.root.children.length; i++)
        //				Main.globals.root.children[i].unsetTexture2( gl, 0);
        //		}
    }

    /**
     * draws this texture or its children<br>
     * first check to see if any part of texture is visible<br>
     * then check to see if this node has children<br>
     * then check to see if has an valid id...  ( optional, if second is false )<br>
     * each condition stops the traversal<br>
     * @param gl opengl rendering context
     * @param currentLevel indicates the current level reached in the tree, neccessary
     * to compute position of child node if it is not defined
     */
    private void textDraw(Graphics2D g, int currentLevel) {
        /**
         * 0. a small visibility test to reduce algorithm complexity, and to avoid
         * duplicates (one on each side of the sphere) is:
         * 		if ( globeRadius!=-1 ) then
         * 			dist(p,e) must be less or equal to sqrt( d(e,C)^2+gR^2 )
         * 
         * the visible textures computation can be done for any situation with a
         * general algorithm, but is complicated.
         * What the algorithm does: determines if a point is in the visible frustum
         * (that is, in the piramid formed by the near and far plane).
         * The steps to be taken are:
         * 1. construct the eye direction vector: d and normalize it
         * 2. construct the eye normal vector: n and normalize it; 
         * 		- reprezents the y axis of the screen
         * 3. construct the second axis of the projection plane ( screen):
         * 		m = d x n  =>  m has the direction to the right ( x axis ),
         * 		- m is normalized as cross product of two normalized vectors
         * 4. construct the vector to the studied point, from eye: p
         * 5. compute p projections on the n,m axis:
         * 		pn = p.n (dot product between the two vectors)
         * 		pm = p.m (dot product between the two vectors)
         * 6. compute dimensions of visible frustum for the distance to the point:
         * 6.1 compute the distance from point to the plane:
         * 		dp = p.d (dot product)
         * 6.2 compute fx=fm, fy=fn, the limits of the frustum:
         * 		fx = dp*tg(alpha/2)
         * 		fy = fx/aspect
         * 		where alpha is the view frustum angle
         * 		and aspect is the raport between width and height of the window
         * 7. check if pn is contained in (-fy,fy) and pm in (-fx,fx)
         * => transformed in: consider the four points that make the texture
         * if at least 1 point's pm is smaller than its fm and at least
         * 1 point's pm is greater than its -fm and same condition for n axis,
         * then this texture is visible
         */
        //frustum checking only for plane projection

        if (textVisible(nWorldX, nWorldY, nWidth, nHeight, bDynamicGrid, parent)) {
            //		if ( worldX<Main.globals.endFx && worldX+nWidth*Globals.divisionWidth>Main.globals.startFx && 
            //				worldY>Main.globals.endFy && worldY-nHeight*Globals.divisionHeight<Main.globals.startFy ) {//texture is visible
            //		System.out.println("nWidth="+nWidth+" nHeight="+nHeight+ "divisionWidth="+Globals.divisionWidth);
            //		System.out.println("worldX="+worldX+" worldY="+worldY+ " worldXend="+(worldX+nWidth*Globals.divisionWidth)+" worldYend="+(worldY-nHeight*Globals.divisionHeight));
            //		System.out.println("startFx="+Main.globals.startFx+" endFx="+Main.globals.endFx+" startFy="+Main.globals.startFy+ " endFy="+Main.globals.endFy);
            //System.out.println("textVisible");
            if (children != null) {//has children
                currentLevel++;
                //System.out.println("has children");
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        //System.out.println("children["+i+"] autodraw for texture ["+nWorldX+","+nWorldY+"]");
                        children[i].textDraw(g, currentLevel);
                    } else if (biTexture != null) {//I should draw the parent texture and texture is valid
                        //System.out.println("parent texture ["+nWorldX+","+nWorldY+"] draw children["+i+"]");
                        //totally ineficient, must be put some where else
                        int wX, wY, w, h;
                        int nx = texturesX[currentLevel];
                        int ny = texturesY[currentLevel];
                        float lT, rT, tT, bT;
                        float tx = (rightT - leftT) / nx;
                        float ty = (topT - bottomT) / ny;
                        lT = leftT + (tx * (i % nx));
                        rT = lT + tx;
                        tT = topT - (ty * (i / ny));
                        bT = tT - ty;
                        /*						if ( !gl.glIsTexture( texture_id) ) {
                        							System.out.println("ERROR! children draw: not a valid texture id1 ("+texture_id+")");
                        							gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.no_texture_id);
                        							lT = 0;
                        							rT = 1;
                        							bT = 0;
                        							tT = 1;
                        						} else
                        							gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
                        *///gl.glBegin(GL.GL_TRIANGLES);
                        if ((nWidth == 1) || (nHeight == 1)) {
                            if (grid != null) {
                                //the second checking is futile because if
                                //nWidth is one, or nHeight is one and this node
                                //has children, it must also have the grid initialized
                                w = 1;
                                h = 1;
                                wX = (i % nx);
                                wY = (i / nx);
                                //System.out.println("draw missing dynamic child["+i+"]");
                                drawMapSlice(g, biTexture, grid, wX, wY, w, h, lT, bT, rT, tT);
                            } else {
                                System.out.println("ERROR! children draw: No position available to draw at.");//I should draw the parent texture
                            }
                        } else {//dynamic node, so use own grid to draw children
                            w = nWidth / nx;
                            h = nHeight / ny;
                            wX = nWorldX + (w * (i % nx));
                            wY = nWorldY + (h * (i / nx));
                            //System.out.println("draw missing static child["+i+"]");
                            drawMapSlice(g, biTexture, map2D.globals.points, wX, wY, w, h, lT, bT, rT, tT);
                        }
                        //gl.glEnd();
                    } else {
                        System.out.println("ERROR! children draw: No texture available to draw.");//I should draw the parent texture
                    }
                }
            } else if (biTexture != null) {//texture is valid
                float lT, rT, tT, bT;
                lT = leftT;
                bT = bottomT;
                rT = rightT;
                tT = topT;
                /*
                if ( !gl.glIsTexture( texture_id) ) {
                	System.out.println("ERROR! children draw: not a valid texture id2 ("+texture_id+")");
                	gl.glBindTexture(GL.GL_TEXTURE_2D, JoglPanel.globals.no_texture_id);
                	lT = 0;
                	rT = 1;
                	bT = 0;
                	tT = 1;
                } else
                    gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
                */
                //gl.glBegin(GL.GL_TRIANGLES);
                if (!bDynamicGrid) {//if node is on static grid
                    //System.out.println("draw static node-  x= "+Globals.points[0][nWorldX+nWidth][nWorldX]+" y="+Globals.points[1][nWorldY+nHeight][nWorldX]);
                    try {
                        drawMapSlice(g, biTexture, map2D.globals.points, nWorldX, nWorldY, nWidth, nHeight, lT, bT, rT,
                                tT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {//node is on parent grid
                        //System.out.println("draw dynamic node");
                    drawMapSlice(g, biTexture, parent.grid, nWorldX, nWorldY, nWidth, nHeight, lT, bT, rT, tT);
                }
                //gl.glEnd();
                //System.out.println("Draw texture with id: "+texture_id);
            } else {
                System.out.println("ERROR! parent draw: No texture available to draw.");
            }
        } else {
            //System.out.println("not draw texture  worldx="+nWorldX+" worldy="+nWorldY);
        }
    }

    /**
     * tests to see if a texture starting in grid at (x,y) and having nx units on x axis
     * and ny units on y axis, is visible or not (that is in the view frustum === the pyramid)
     * @param x
     * @param y
     * @param nx
     * @param ny
     * @param bDynamic indicates if the test is done for a static grid texture, or a dynamic one
     * @return boolean value to indicate visibility
     */
    private boolean textVisible(int x, int y, int nx, int ny, boolean bDynamic, Texture parent) {
        //return true;

        if (bDynamic && (parent == null)) {
            return false;//if this is a dynamic texture, but there is no parent, it must be an error, so
        }
        //return false
        //set the grid where from points are taken: static or dynamic
        float[][][] grid_pointer = map2D.globals.points;
        if (bDynamic) {
            grid_pointer = parent.grid;
            //System.out.println("x="+x+" y="+y+" nx="+nx+" ny="+ny);
        }

        /**
         * 0. a small visibility test to reduce algorithm complexity, and to avoid
         * duplicates (one on each side of the sphere) is:
         * 		if ( globeRadius!=-1 ) then
         * 			dist(p,e) must be less or equal to sqrt( d(e,C)^2-gR^2 )
         * where gR is the virtual globe radius
         * 
         * the visible textures computation can be done for any situation with a
         * general algorithm.
         * What the algorithm does: determines if a point is in the visible frustum
         * (that is, in the piramid formed by the near and far plane).
         * The steps to be taken are:
         * 1. construct the eye direction vector: d and normalize it
         * 2. construct the eye normal vector: n and normalize it; 
         * 		- reprezents the y axis of the screen
         * 3. construct the second axis of the projection plane ( screen):
         * 		m = d x n  =>  m has the direction to the right ( x axis ),
         * 		- m is normalized as cross product of two normalized vectors
         * 4. construct the vector to the studied point, from eye: p
         * 5. compute p projections on the n,m axis:
         * 		pn = n.p (dot product between the two vectors)
         * 		pm = m.p (dot product between the two vectors)
         * 6. compute dimensions of visible frustum for the distance to the point:
         * 6.1 compute the distance from point to the plane:
         * 		dp = d.p (dot product)
         * 6.1.0 if all 4 points(corners) have projection <=0 then texture is invisible
         * 6.2 compute fx=fm, fy=fn, the limits of the frustum:
         * 		fx = dp*tg(alpha/2)
         * 		fy = fx/aspect
         * 		where alpha is the view frustum angle
         * 		and aspect is the raport between width and height of the window
         * 7. check if pn is contained in (-fy,fy) and pm in (-fx,fx)
         * => transformed in: consider the four points that make the texture
         * if at least 1 point's pm is smaller than its fm and at least
         * 1 point's pm is greater than its -fm and same condition for n axis,
         * then this texture is visible
         */

        //for each point repeat:
        //4. vector from eye to point
        //float []coord;
        //		float coordX, coordY, coordZ;
        //		coordX = grid_pointer[0][y][x];
        //		coordY = grid_pointer[1][y][x];
        //		coordZ = grid_pointer[2][y][x];
        //System.out.println("textVisible x= "+coordX+" y= "+ coordY+" x+nx= "+grid_pointer[0][y+ny][x+nx]+" y+ny="+grid_pointer[1][y+ny][x+nx]+" width="+Globals.width2D+" height= "+Globals.height2D+" x="+Globals.x2D+" y="+Globals.y2D);

        //coord = Globals.points[y][x];
        //		VectorO Vp1 = new VectorO(coordX, coordY, coordZ);
        //		Vp1.SubstractVector(map2D.globals.EyePosition);
        ////		coord = Globals.points[y+ny][x];
        //		coordX = grid_pointer[0][y+ny][x];
        //		coordY = grid_pointer[1][y+ny][x];
        //		coordZ = grid_pointer[2][y+ny][x];
        //		VectorO Vp2 = new VectorO(coordX, coordY, coordZ);
        //		Vp2.SubstractVector(map2D.globals.EyePosition);
        ////		coord = Globals.points[y][x+nx];
        //		coordX = grid_pointer[0][y][x+nx];
        //		coordY = grid_pointer[1][y][x+nx];
        //		coordZ = grid_pointer[2][y][x+nx];
        //		VectorO Vp3 = new VectorO(coordX, coordY, coordZ);
        //		Vp3.SubstractVector( map2D.globals.EyePosition);
        ////		coord = Globals.points[y+ny][x+nx];
        //		coordX = grid_pointer[0][y+ny][x+nx];
        //		coordY = grid_pointer[1][y+ny][x+nx];
        //		coordZ = grid_pointer[2][y+ny][x+nx];
        //		VectorO Vp4 = new VectorO(coordX, coordY, coordZ);
        //		Vp4.SubstractVector(map2D.globals.EyePosition);
        ////		coord = Globals.points[y+ny/2][x+nx/2];
        //		VectorO VpC = null;
        //		if ( ny>1 && nx>1 ) {
        //				coordX = grid_pointer[0][y+ny/2][x+nx/2];
        //				coordY = grid_pointer[1][y+ny/2][x+nx/2];
        //				coordZ = grid_pointer[2][y+ny/2][x+nx/2];
        //			VpC = new VectorO(coordX, coordY, coordZ);
        //			VpC.SubstractVector( map2D.globals.EyePosition);
        //		};
        if ((grid_pointer[1][y][x] > map2D.globals.y2D) && (grid_pointer[1][y + ny][x] > map2D.globals.y2D)) {
            //System.out.println("Texture is up");
            return false;
        }
        if ((grid_pointer[1][y][x] < (map2D.globals.y2D - map2D.globals.height2D))
                && (grid_pointer[1][y + nx][x] < (map2D.globals.y2D - map2D.globals.height2D))) {
            //System.out.println("Texture is down");
            return false;
        }
        if ((grid_pointer[0][y][x] < map2D.globals.x2D) && (grid_pointer[0][y][x + nx] < map2D.globals.x2D)) {
            //System.out.println("Texture is left");
            return false;
        }
        if ((grid_pointer[0][y][x] > (map2D.globals.x2D + map2D.globals.width2D))
                && (grid_pointer[0][y][x + nx] > (map2D.globals.x2D + map2D.globals.width2D))) {
            //System.out.println("Texture is right");
            return false;
        }
        //System.out.println("Texture is visible");
        return true;

        /*
        
        //0. small visibility test
        if ( Globals.globals.globeRadius!=-1f ) {
        	float vgR = Globals.globals.globeVirtualRadius;
        	float dmin, dc;
        	VectorO eye_center = new VectorO(0f,0f,-Globals.globals.globeRadius+vgR);
        	eye_center.SubstractVector(Globals.globals.EyePosition);//new VectorO(-Main.globals.EyePosition[0], -Main.globals.EyePosition[1], -Main.globals.EyePosition[2]-Main.globals.globeRadius+vgR);
        	dc = (float)eye_center.getRadius();
        	//the small visibility test functions only for outside of sphere view
        	if ( dc>Globals.globals.globeRadius ) {
        		//System.out.println("eye_center="+dc);
        		dmin = (float)Math.sqrt(dc*dc-Globals.globals.globeRadius*Globals.globals.globeRadius);
        		//System.out.println("dmin="+dmin+" radius="+Main.globals.globeRadius+" dc="+dc+" vp1="+Vp1.getRadius());
        		if ( (VpC!=null && VpC.getRadius()>dmin) && Vp1.getRadius()>dmin && Vp2.getRadius()>dmin && Vp3.getRadius()>dmin && Vp4.getRadius()>dmin ) {
        			//System.out.println("hide texture");
        			return false;
        		}
        	};
        	//0. test points
        	//System.out.println("mapAngle: "+Main.globals.mapAngle);
        }
        //TODO: recheck visibility test using latitude and longitude
        //1. eye direction vector
        VectorO Vd = Globals.globals.EyeDirection;//new VectorO(Main.globals.EyeDirection[0], Main.globals.EyeDirection[1], Main.globals.EyeDirection[2]);
        //Vd.Normalize();
        //2. eye normal vector
        VectorO Vn = Globals.globals.EyeNormal;//new VectorO(Main.globals.EyeNormal[0], Main.globals.EyeNormal[1], Main.globals.EyeNormal[2]);
        //Vd.Normalize();
        //3. second axis
        VectorO Vm = Vd.CrossProduct(Vn);
        //for each point repeat:
        //5. projections on axis
        float pn1, pm1;
        pn1 = (float)Vn.DotProduct(Vp1);
        pm1 = (float)Vm.DotProduct(Vp1);
        float pn2, pm2;
        pn2 = (float)Vn.DotProduct(Vp2);
        pm2 = (float)Vm.DotProduct(Vp2);
        float pn3, pm3;
        pn3 = (float)Vn.DotProduct(Vp3);
        pm3 = (float)Vm.DotProduct(Vp3);
        float pn4, pm4;
        pn4 = (float)Vn.DotProduct(Vp4);
        pm4 = (float)Vm.DotProduct(Vp4);
        //6. view frustum
        //6.1 dp
        float dp1;
        dp1 = (float)Vd.DotProduct(Vp1);// if (dp1<0) dp1=-dp1;
        float dp2;
        dp2 = (float)Vd.DotProduct(Vp2);// if (dp2<0) dp2=-dp2;
        float dp3;
        dp3 = (float)Vd.DotProduct(Vp3);// if (dp3<0) dp3=-dp3;
        float dp4;
        dp4 = (float)Vd.DotProduct(Vp4);// if (dp4<0) dp4=-dp4;
        //6.1.0 negative projections
        if ( dp1<=0 && dp2<=0 && dp3<=0 && dp4<=0 ) {
        	//System.out.println("hide texture");
        	return false;
        }
        //6.2 frustum
        float fm1, fn1;
        fm1 = dp1*(float)Math.tan(Globals.FOV_ANGLE/2f*Math.PI/180f);
        fn1 = fm1/Globals.globals.fAspect;
        float fm2, fn2;
        fm2 = dp2*(float)Math.tan(Globals.FOV_ANGLE/2f*Math.PI/180f);
        fn2 = fm2/Globals.globals.fAspect;
        float fm3, fn3;
        fm3 = dp3*(float)Math.tan(Globals.FOV_ANGLE/2f*Math.PI/180f);
        fn3 = fm3/Globals.globals.fAspect;
        float fm4, fn4;
        fm4 = dp4*(float)Math.tan(Globals.FOV_ANGLE/2f*Math.PI/180f);
        fn4 = fm4/Globals.globals.fAspect;
        //7. check if at least 1 point is well positioned
        if ( pm1>fm1 && pm2>fm2 && pm3>fm3 && pm4>fm4 )
        	return false;
        if ( pm1<-fm1 && pm2<-fm2 && pm3<-fm3 && pm4<-fm4 )
        	return false;
        if ( pn1>fn1 && pn2>fn2 && pn3>fn3 && pn4>fn4 )
        	return false;
        if ( pn1<-fn1 && pn2<-fn2 && pn3<-fn3 && pn4<-fn4 )
        	return false;
        return true;
        */
    }

    /**
     * deletes children and sets their valid textures for unload from memory
     * @return a boolean value to indicate if there are children left ( the ones with ALWAYS flag set)<br>
     * The function will probably return always true because the only textures with this flag set are the ones on
     * first level
     */
    private boolean textDeleteChildren() {
        boolean bNoChildLeft = true;
        boolean bNoChildChildLeft;
        if (children != null) {
            //todo: set valid child textures for unset in gl drawing thread
            //delete children
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    //if a descendant of this child has an ALWAYS texture
                    //then this child cannot be removed
                    bNoChildChildLeft = children[i].textDeleteChildren();
                    if (children[i].checkStatus(S_SET)) {
                        if (children[i].checkStatus(S_ALWAYS)) {
                            bNoChildLeft = false;
                            continue;
                        } else {
                            children[i].resetStatus();// = S_NONE;
                            texturesToUnSet.add(children[i].biTexture);
                        }
                        ;
                    }
                    ;
                    //neccessary code to set a new texture id for this child that any way seems to be nulled
                    children[i].textSetParentTexture();
                    if (!bNoChildChildLeft) {
                        bNoChildLeft = false;
                        continue;
                    }
                    //unreachable code
                    //section reached only by children textures that are 
                    // not any more set, 
                    // or they are in texturesToLoad and texturesToSet arrays
                    //so, in this case,
                    //set status as dereferenced and null the reference to the child
                    //so that, if in other array, when it is removed from that
                    //array, it should be nulled also
                    children[i].setStatus(S_DEREFERENCED);
                    //children[i].grid = null;//?
                    children[i] = null;
                }
                ;
            }
            if (bNoChildLeft) {
                children = null;
                //grid = null;
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
            biTexture = parent.biTexture;
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
            //set for existing children that don't have their own texture this node's parent texture
            for (Texture element : children) {
                if (element != null /*&& !children[i].checkStatus(S_SET)*/) {
                    element.textSetParentTexture();
                }
            }
        }
    }

    /**
     * checks a slice to see if needs texture load, based on current level of detail,
     * texture visibility in frustrum and file availability<br>
     * TODO : should be more inteligent in that should set for load first the full
     * viewable textures and then the half viewable, and in the future, then the near
     * ones
     * @param currentLevel level for this node
     * @return boolean value to indicate validity
     */
    private boolean textZoomChanged(int currentLevel) {
        if (currentLevel >= texturesX.length) {
            return false;
        }
        //System.out.println("text zoom changed");
        /**
         * Algorithm for setting the, hopefully, correct LOD(level of detail) for a texture.
         * This algorithm uses an aproximate visibility test, as being the best I could find.
         * Algorithm:
         * 1. traverse the tree
         * 2. for a visible node compute depth (depth is: "projection of vector starting from
         * eye to center of texture, on vector eye direction starting at eye and ending at direction")
         * 3. compare current LOD (given by level in tree) with the desired LOD (computed
         * based on depth and global depths vector)
         * 4.	- if greater, decrease LOD by deleting node and its children
         * 5.	- if lower, then
         * 5.1		- create/set visible children, delete the rest
         * 5.2		- set current texture as their base texture, if they have none
         * 5.3		- test algorithm for each child
         * 5.4*		- if there is no valid child (left), set texture for load
         * 6.	- if equal, then
         * 6.1		- if has children, delete them, 'cause it doesn't need a more detailed LOD
         * 6.2*		- it texture is not set, set texture for load
         * 7. end
         * 7+ unload not visible textures. 
         * 
         * What "*" means is that only on those branches a texture is set for load, that is
         * a new file is read into memory.
         * Problems for this algorithm:
         * 		- delete a node
         * 		- use base texture... => a node must not have a 0 id for texture or an invalid one
         * 		- algorithm may behave badly for sphere shape not centered
         * 		- not used distance, but projection so that the algorithm behave the same as the one
         * for plane projection
         */
        if (textVisible(nWorldX, nWorldY, nWidth, nHeight, bDynamicGrid, parent)) {
            //System.out.println("text visible");
            //2. compute depth
            /*			float depth;
            			float centerX, centerY, centerZ;
            */float[][][] grid_pointer = map2D.globals.points;
            if (bDynamicGrid) {
                grid_pointer = parent.grid;
            }
            /**
             * if the center is not on the static grid (h or w==1), or
             * it is a dynamic grid, that has no point for center on it,
             * compute the center as center of polygon formed by the four
             * points that limit the texture.
             */
            /*			if ( nHeight==1 || nWidth==1 ) {//compute the center of this node
            				//based on its dynamic grid position properties
            				centerX = (grid_pointer[0][nWorldY][nWorldX]+grid_pointer[0][nWorldY+nHeight][nWorldX]+grid_pointer[0][nWorldY][nWorldX+nWidth]+grid_pointer[0][nWorldY+nHeight][nWorldX+nWidth])/4f;
            				centerY = (grid_pointer[1][nWorldY][nWorldX]+grid_pointer[1][nWorldY+nHeight][nWorldX]+grid_pointer[1][nWorldY][nWorldX+nWidth]+grid_pointer[1][nWorldY+nHeight][nWorldX+nWidth])/4f;
            				centerZ = (grid_pointer[2][nWorldY][nWorldX]+grid_pointer[2][nWorldY+nHeight][nWorldX]+grid_pointer[2][nWorldY][nWorldX+nWidth]+grid_pointer[2][nWorldY+nHeight][nWorldX+nWidth])/4f;
            			} else {
            				centerX = Globals.points[0][nWorldY+nHeight/2][nWorldX+nWidth/2];
            				centerY = Globals.points[1][nWorldY+nHeight/2][nWorldX+nWidth/2];
            				centerZ = Globals.points[2][nWorldY+nHeight/2][nWorldX+nWidth/2];
            			};
            			VectorO Vp = new VectorO(centerX, centerY, centerZ);
            			Vp.SubstractVector(Globals.globals.EyePosition);
            			VectorO Vd = Globals.globals.EyeDirection;//new VectorO(Main.globals.EyeDirection[0]-Main.globals.EyePosition[0], Main.globals.EyeDirection[1]-Main.globals.EyePosition[1], Main.globals.EyeDirection[2]-Main.globals.EyePosition[2]);
            			//Vd.Normalize();
            			depth = (float)Vd.DotProduct( Vp);
            			if ( depth<0f ) {
            				depth = - depth;
            				System.out.println("negative depth!!! Alert!");
            				//return true;
            			};
            *///3. compare currentLevel with desiredLevel
            int desiredLevel = 0;
            //System.out.println("depthZ.length: "+depthZ.length+" depth: "+depth);
            /*			for( int i=0; i<depthZ.length; i++)
            				if ( depth > depthZ[i] )
            					break;
            				else 
            					desiredLevel = i+1;
            */
            float R;
            for (int i = 0; i < resolutionX.length; i++) {
                R = map2D.globals.DISPLAY_W / ((map2D.globals.width2D * resolutionX[i]) / Globals.MAP_WIDTH);
                if (R < 2) {
                    break;
                } else {
                    desiredLevel = i + 1;
                }
            }
            //desiredLevel=Globals.zoom2D;
            //desiredLevel=1;
            //System.out.println("texture at ["+nWorldX+","+nWorldY+"] has desired level: "+ desiredLevel);
            //4. LODc > LODd
            if (currentLevel > desiredLevel) {
                //level of detail too high for the current depth, so decrease it by deleting child nodes
                //and, eventually, this node
                boolean bNoChildLeft = false;
                bNoChildLeft = textDeleteChildren();
                //set for unload this texture if is SET and is not ALWAYS
                //and set this node for deletion in parent
                if (checkStatus(S_SET)) {
                    if (checkStatus(S_ALWAYS)) {
                        return true;
                    } else {
                        //set current texture for unload and revert to parent texture
                        texturesToUnSet.add(biTexture);
                        resetStatus();
                        textSetParentTexture();
                    }
                }
                if (bNoChildLeft) {
                    return false;
                }
            } else if (currentLevel < desiredLevel) {
                boolean bShouldLoad = true;
                //5. LODc < LODd
                if (!checkStatus(S_MAX_LEVEL)) {//not max level
                    //System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!not max level");
                    //5.1 create/set children
                    //compute the slice path
                    //					ClassLoader myClassLoader = Globals.class.getClassLoader();
                    String path = pathToTextures + textCreatePathDyn(currentLevel)[0];
                    //System.out.print("path to current slice: "+path);
                    //if ( myClassLoader.getResource(path) ==null ) {
                    //					int fileExists=0;
                    File file_in = null;
                    try {
                        file_in = new File(path);
                    } catch (Exception e) {/*fileExists =0;*/
                    }
                    if (!file_in.isDirectory()) {

                        setStatus(S_MAX_LEVEL);
                    } else {
                        //System.out.println("parent visible (x,y,width,height)=("+(nWorldX)+","+(nWorldY)+","+nWidth+","+nHeight+")");
                        //currentLevel++;//go to children
                        float stepTX, stepTY;
                        int nx = texturesX[currentLevel + 1];
                        int ny = texturesY[currentLevel + 1];
                        stepTX = (rightT - leftT) / nx;
                        stepTY = (topT - bottomT) / ny;
                        int stepX, stepY, startX, startY;
                        boolean bDynamicChildren = false;
                        //test to see if children will be created based on the static grid for this node, or the parent
                        //has to create a dynamic grid for them
                        if (((nWidth == 1) || (nHeight == 1)) || bDynamicGrid) {
                            bDynamicChildren = true;
                            //the conditions to create a dynamic grid are:
                            //there are no points left to divide the static grid on x or on y, or
                            //this node is already a dynamic grid based, so, its children must also be dynamic
                            //the second condition also implies that nWidth =1 and nHeight =1, but it is there for
                            //clarity reasons
                            stepX = 1;
                            stepY = 1;
                            startX = 0;
                            startY = 0;
                            //create dynamic grid
                            if (grid == null) {
                                //create with one aditional point because 1 texture is guarded by 2 points,
                                //and nx is the number of textures...
                                grid = new float[3][ny + 1][nx + 1];
                                //instantiate the grid, based on parent coordinates
                                //prepare parent coordinate for init grid function
                                //								float[][][] grid_pointer = Globals.points;
                                //								if ( bDynamicGrid )
                                //									grid_pointer = parent.grid;

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
                        //System.out.println("nWidth="+nWidth+" nx="+nx+" stepX="+stepX);
                        //System.out.println("nHeight="+nHeight+" ny="+ny+" stepY="+stepY);
                        if (children == null) {
                            children = new Texture[nx * ny];
                        }
                        int k;
                        for (int y = 0; y < ny; y++) {
                            for (int x = 0; x < nx; x++) {
                                k = (y * nx) + x;
                                //set world position and texture id for each visible children
                                if (textVisible(startX + (stepX * x), startY + (stepY * y), stepX, stepY,
                                        bDynamicChildren, this)) {
                                    //System.out.println("child visible (x,y,width,height)=("+(nWorldX+stepX*x)+","+(nWorldY+stepY*y)+","+stepX+","+stepY+")");
                                    //5.2 set base texture
                                    if (children[k] == null) {//if visible child is null, create it
                                        //System.out.println("texture_id = "+texture_id);
                                        children[k] = new Texture(biTexture,//parent texture for the moment
                                                startX + (stepX * x), startY + (stepY * y), stepX, stepY, leftT
                                                        + (x * stepTX), topT - ((y + 1) * stepTY), leftT
                                                        + ((x + 1) * stepTX), topT - (y * stepTY));//set texture coordonates
                                        children[k].setParent(this);
                                        children[k].setBDynamicGrid(bDynamicChildren);
                                    }
                                    //because child is using parent texture
                                    //5.3 test algorithm for each visible child
                                    if (children[k].textZoomChanged(currentLevel + 1) == false) {
                                        //invalid child, so, delete it, texture_id already set for dealocation
                                        children[k].setStatus(S_DEREFERENCED);
                                        //children[k].grid = null;
                                        children[k] = null;
                                    } else {
                                        bShouldLoad = true;//? false;//5.4 if one child is valid, this should not load
                                    }
                                } else {
                                    //System.out.println("child not visible (x,y,width,height)=("+(nWorldX+stepX*x)+","+(nWorldY+stepY*y)+","+stepX+","+stepY+")");
                                    //5.1 delete the rest
                                    //delete children if not set as ALWAYS
                                    if (children[k] != null) {
                                        boolean bNoChildLeft = children[k].textDeleteChildren();
                                        //set for unload this texture if is its texture and is not set as ALWAYS
                                        if (children[k].checkStatus(S_SET) && !children[k].checkStatus(S_ALWAYS)) {
                                            texturesToUnSet.add(children[k].biTexture);
                                            children[k].resetStatus();// = S_NONE;
                                            children[k].textSetParentTexture();//although there is no need for this line, as the node is deleted anyway
                                        }
                                        ;
                                        if (bNoChildLeft && !children[k].checkStatus(S_ALWAYS)) {
                                            children[k].setStatus(S_DEREFERENCED);
                                            //children[k].grid = null;
                                            children[k] = null;
                                        }
                                        ;
                                    }
                                }//endif ( textVisible(nWorldX+stepX*x, nWorldY+stepY*y, stepX, stepY) )
                            }
                        }
                        ;//endfor ( int x=0; x<nx; x++ )
                    }//endif ( myClassLoader.getResource(path) ==null )
                }//endif ( !checkStatus(S_MAX_LEVEL) ) {
                 //5.4 no valid child and hasn't been already set for loading
                if (bShouldLoad && !checkStatus(S_SHOULD_LOAD)) {
                    //resetStatus();//has nothing to reset cause this is the first flag to be set, except for S_NONE
                    setStatus(S_SHOULD_LOAD);
                    String path = textCreatePathDyn(currentLevel)[0];
                    //System.out.println("SHOULD_LOAD: "+path);
                    Object[] tuplu = { this, path, Integer.valueOf(currentLevel) };
                    //add this slice to slices that should have texture loaded
                    texturesToLoad.add(tuplu);
                    //notify the sleeping thread that new textures are available to load
                    synchronized (texturesToLoad) {
                        texturesToLoad.notify();
                    }
                    ;
                    //if the thread is not sleeping, the notification is lost
                }
                ;

            } else { //6. LODc = LODd
                //6.1 delete children
                //moved in set function, when there is a correct texture available for this level
                //and the betters ones (childs ) can be unloaded
                //delete child textures only if parent has one
                if (checkStatus(S_SET)) {
                    //			        boolean bNoChildLeft = 
                    textDeleteChildren();
                }
                ;
                //6.2 set for load, if the case
                if (!checkStatus(S_SHOULD_LOAD)) {
                    //resetStatus();//has nothing to reset cause this is the first flag to be set, except for S_NONE
                    setStatus(S_SHOULD_LOAD);
                    String path = textCreatePathDyn(currentLevel)[0];
                    //System.out.println("SHOULD_LOAD: "+path);
                    Object[] tuplu = { this, path, Integer.valueOf(currentLevel) };
                    //add this slice to slices that should have texture loaded
                    texturesToLoad.add(tuplu);
                    //notify the sleeping thread that new textures are available to load
                    synchronized (texturesToLoad) {
                        texturesToLoad.notify();
                    }
                    ;
                    //if the thread is not sleeping, the notification is lost
                }
                ;
            }//end compare current LOD with desired LOD
        } else {//texture not visible
            //System.out.println("text not visible");

            //level of detail too high for the current depth, so decrease it by deleting child nodes
            //and, eventually, this node
            boolean bNoChildLeft = textDeleteChildren();
            //System.out.println("bNoChildLeft: "+bNoChildLeft);
            //set for unload this texture if is its texture and is not set as ALWAYS
            //and set this node for deletion in parent
            if (checkStatus(S_SET)) {
                if (checkStatus(S_ALWAYS)) {
                    return true;
                } else {
                    texturesToUnSet.add(biTexture);
                    resetStatus();// = S_NONE;
                    textSetParentTexture();
                }
            }
            if (bNoChildLeft) {
                return false;
            }
        }
        return true;
    }

    /*	private void printChildTid( int level)
    	{
    		if ( children!=null ) {
    			//set for existing children that don't have their own texture this node's parent texture
    			for( int i=0; i<children.length; i++)
    				if ( children[i]!=null ) {
    					//logger.log( Level.INFO, "level "+level+" -> Child texture id : "+children[i].texture_id);
    					children[i].printChildTid( level+1);
    				};
    		}
    	}
    */
    /**
     * check the node to see if all children have their own textures so that
     * the parent can unload its<br>
     * sets a texture to parent texture if all possible children have their own
     * texture
     * @param gl
     */
    private void textUnsetParentTexture() {
        //if there is no parent, no unset neccessary
        if (parent == null) {
            return;//keep the texture if last texture...
        }
        if (biTexture == null) {
            return;//somehow similar to first conditions as both apply to root texture
        }
        //if this texture is set forever, no unset to do for this or parents
        if (checkStatus(S_ALWAYS)) {
            return;
        }
        //if this texture is using it's parent texture, then has nothing to be
        //unset, and the parent cannot unload it's texture
        if (!checkStatus(S_SET)) {
            return;
        }
        //if no children, no need to unset, to lose its texture
        if ((children == null) || (children.length == 0)) {
            return;
        }
        //check to see if all children are set
        //if one is not set then nothing to do for this texture
        for (int i = 0; i < children.length; i++) {
            if ((children[i] == null) || !children[i].checkStatus(S_SET)/*children[i].texture_id==texture_id*/) {
                return;
            }
        }
        //unload texture from memory, because ABSOLUTELY no one else is using IT!
        /*
        if ( gl.glIsTexture(texture_id) ) {
        	int[] textures = {texture_id};
        	gl.glDeleteTextures( 1, textures);
        	logger.log( Level.INFO, "unsetTexture -> Delete texture with id: "+textures[0]);
        };*/
        //texture not set, so reset status also
        resetStatus();// = S_NONE; keeps S_DEREFERENCED and S_MAX_LEVEL
        /**
         * set the parent id for this texture
         * so set the texture coordinates based on those from parent
         * if parent has dynamic grid, then nWidth=1 or nHeight=1, so get the correct number
         * of child textures from dynamic grid
         */
        biTexture = parent.biTexture;
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
        //		logger.log( Level.INFO, "unsetTexture -> New texture id from parent: "+texture_id);
    }

    private String[] textCreatePathDyn(int currentLevel) {
        if (bDynamicGrid) {
            String[] ret = parent.textCreatePathDyn(currentLevel - 1);
            ret[1] += "_" + (nWorldY + 1) + "." + (nWorldX + 1);
            ret[0] += pathSeparator + "map" + (currentLevel + 1) + ret[1];
            return ret;
        } else {
            return createTexturePath(currentLevel, nWorldX, nWorldY);
        }
    }

    /**
     * another unset method that tranverses the tree from root to leaves
     * and unloads textures that are not visible or not on the correct depth
     * @param gl
     */
    /*	private void unsetTexture2( GL gl, int currentLevel)
    	{
    //		System.out.println("unset texture 2 level:"+currentLevel);
    		//check to see if texture is not visible
    //		if ( (worldX>=Main.globals.endFx || worldX+width<=Main.globals.startFx) && 
    //				(worldY<=Main.globals.endFy || worldY-height>=Main.globals.startFy) ) {//texture is not visible
    		float worldX, worldY;
    		*//** compute worldX and worldY **/
    /*
    worldX = -Globals.MAP_WIDTH/2f + nWorldX*Globals.divisionWidth;
    worldY = Globals.MAP_HEIGHT/2f - nWorldY*Globals.divisionHeight;

    //frustum checking only for plane projection
    if ( !(worldX<Main.globals.endFx && worldX+nWidth*Globals.divisionWidth>Main.globals.startFx && 
    worldY>Main.globals.endFy && worldY-nHeight*Globals.divisionHeight<Main.globals.startFy ) ||
    //		if ( !(worldX<Main.globals.endFx && worldX+width>Main.globals.startFx && 
    //				worldY>Main.globals.endFy && worldY-height<Main.globals.startFy) ||
    currentLevel > Main.globals.currentLevel ) {		
    //for texture_id, equal ( "=" ) means the same as greater or equal ( ">=" ) to zero
    if ( !checkStatus(S_ALWAYS) && checkStatus(S_SET)(status&SM_LOW)!=S_ALWAYS && texture_id>=0 && (status&SM_LOW)==S_SET ) {
    System.out.println("Unloading file "+pathToTextures+createTexturePath( currentLevel, nWorldX, nWorldY)+".oct");
    unsetDirect( gl);
    };
    }
    if ( children!=null ){//part of texture is visible, so check the children
    boolean bRemoveChildren = true;
    //for each children, clear the textures that are not visible
    for ( int i=0; i<children.length; i++)
    if ( children[i]!= null ) {
    children[i].unsetTexture2( gl, currentLevel+1);
    if ( children[i].texture_id>0 && ( children[i].texture_id!=texture_id || children[i].checkStatus(S_NONE) ) )
    bRemoveChildren = false;
    else {
    children[i].status = S_DEREFERENCED;
    children[i] = null;
    }
    }
    //if any of the children are not visible, remove the children
    if ( bRemoveChildren ) {
    //System.out.println("unsetTexture2 remove children for texture id: "+texture_id);
    children = null;
    }
    }
    }
    */
    /**
     * direct unset of loaded texture, without any aditional visibility checking
     * @param gl
     */
    /*	private void unsetDirect( GL gl)
    	{
    		//System.out.println("unsetDirect texture_id: "+texture_id);
    		//int tex_id = texture_id;
    		if ( gl.glIsTexture(texture_id) ) {//this check also includes  (children[i].texture_id!= tex_id && children[i].texture_id>0)
    			int[] textures = {texture_id};
    			gl.glDeleteTextures( 1, textures);
    			System.out.println("unsetDirect -> Delete texture with id: "+textures[0]);
    		};
    		texture_id = 0;
    		status = S_NONE;
    		//free the invisible children
    		if ( children != null ) {
    			for ( int i=0; i<children.length; i++)
    				if ( children[i]!=null ) {
    					children[i].unsetDirect( gl);
    					children[i].status = S_DEREFERENCED;
    					children[i] = null;
    				};
    			children = null;
    		}
    	}
    */
    /**
     * finds and returns the texture under the cursor<br>
     * The texture is the smallest one, and the most detailed
     * @return the pointer to texture
     */
    /*	public Texture findUnderCursor()
    	{
    		Texture ret=null;
    		float worldX, worldY;
    		// compute worldX and worldY
    		worldX = -Globals.MAP_WIDTH/2f + nWorldX*Globals.divisionWidth;
    		worldY = Globals.MAP_HEIGHT/2f - nWorldY*Globals.divisionHeight;

    		//frustum checking only for plane projection
    		if ( worldX<=JoglPanel.globals.CursorPosition[0] && worldX+nWidth*Globals.divisionWidth>JoglPanel.globals.CursorPosition[0] && 
    				worldY>=JoglPanel.globals.CursorPosition[1] && worldY-nHeight*Globals.divisionHeight<JoglPanel.globals.CursorPosition[1] ) {//texture is under cursor
    			//System.out.println("worldX="+worldX+" worldY="+worldY+" widht="+width+" height="+height);
    			if ( children!=null ) {
    				for( int i=0; i<children.length; i++) {
    					if ( children[i]!=null && children[i].checkStatus(S_SET) )
    						ret = children[i].findUnderCursor();
    					if ( ret!=null )
    						return ret;
    				};
    			}
    			if ( ret ==null )
    				ret = this;
    		}
    		return ret;
    	}
    */
}
