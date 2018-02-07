package lia.web.servlets.map2d;


import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.Vector;

/*
 * Created on May 13, 2004
 *
 */
/**
 * @author mluc
 *
 * defines variables and constants for use in Texture class
 */
public class TextureParams 
{
	public Map2D map2D;
	//I don't need its index because I know the index in the parent
	//I don't need world position and dimensions because will be computed on tree traversal
	//but it's better to compute them only once
	//I don't need zoom because it is stored in main program
	//protected int texture_id;//opengl texture id
	protected BufferedImage biTexture = null;
	protected Texture[] children = null;
	protected Texture parent = null;
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
	protected float [][][] grid = null;
	//what grid should be used to draw texture: static one or parent dinamic one
	protected boolean bDynamicGrid = false;
	//position of texture on the map
	protected int nWorldX=0, nWorldY=0;//indices of position in parent grid
	protected int nWidth = Globals.nrDivisionsX, nHeight = Globals.nrDivisionsY;
	//protected float worldX=-Globals.MAP_WIDTH*.5f, worldY=Globals.MAP_HEIGHT*.5f, width=Globals.MAP_WIDTH, height=Globals.MAP_HEIGHT;
	//position of texture in the bigger texture if any ( full texture: 0-1 )
	protected float leftT=0f, bottomT=0f, rightT=1f, topT=1f;
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
	public boolean checkStatus( int flag) {	return ( (status&flag)>0 ); }
	public void setStatus( int flag) { status|=flag; }
	public void clearStatus( int flag) { status^=(status&flag); }//status|=flag; status^=flag; }
	/**
	 * reset status loses S_SHOULD_LOAD, S_ALWAYS, S_FILE_NOT_FOUND, S_LOADED, S_SET
	 * but keeps S_NONE, S_MAX_LEVEL, S_DEREFERENCED
	 */
	public void resetStatus() { status=(S_NONE|(status&S_MAX_LEVEL)|(status&S_DEREFERENCED)); }
	
	/**
	 * arrays that defines the resolution of full picture on each level
	 * neccessary to compute the resolution per slice
	 */
//	public static int[] resolutionX = { 1024, 2048, 4096, 8192};
//	public static int[] resolutionY = { 512, 1024, 2048, 4096};
	public static int[] resolutionX = { 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288};
	public static int[] resolutionY = { 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144};
	//arrays that contain for each level of detail info like:
	//number of pieces on x, on y and the depth the next level should apply
	//probably the last value in depth array must be eliminated, is redundant
//	public static int[] texturesX = {8,2,2,2};
//	public static int[] texturesY = {4,2,2,2};
//	public static float[] depthZ = {4f, 1f, .5f};//{2f, .5f, .125f};
	public static int[] texturesX = {8,2,2,2,2,2,2,2,2,2};
	public static int[] texturesY = {4,2,2,2,2,2,2,2,2,2};
	public static float[] depthZ = {4f, 1f, .5f, .25f, .125f, .0625f, .03125f, .015625f, .0078125f/*, .00390625f*/};
	//object used for synchronisation between threads that use common variables
	protected static Object syncObject_Texture = new Object();
	
	//queue containing the textures to load fifo
	public static Vector texturesToLoad= new Vector();
	//vector containing the textures to unload/unset
	public static Vector texturesToUnSet= new Vector();
	//public static Object waitForTexturesToLoad = new Object();
	//queue containing the textures with data array to be loaded into opengl
	public static Vector texturesToSet= new Vector();
	
	/**
	 * hashtable of weak references to images data<br>
	 * key = slice identifier<br>
	 * value = weak reference to a slice's byte array<br>
	 */
	public static Hashtable texturesData = new Hashtable();
	
	public static final String pathSeparator = "/"/*System.getProperty("file.separator")*/;
	//public static String pathToTextures ="/home/osg/MLrepository/tomcat/webapps/ROOT/WEB-INF/classes/lia/web/servlets/map2d/images"+pathSeparator;/*System.getProperty("user.dir")+System.getProperty("file.separator")+"bin"+System.getProperty("file.separator")+*/
	public static String pathToTextures =FarmMap.sClassesDir+"lia/web/servlets/map2d/images"+pathSeparator;/*System.getProperty("user.dir")+System.getProperty("file.separator")+"bin"+System.getProperty("file.separator")+*/
	

	/**
	 * @return Returns the texture_id.
	 */
	public BufferedImage getTextureImage() {	return biTexture; }
	
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
		this.map2D = parent.map2D;
	}
	
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
	public void drawMapSlice( Graphics2D g, BufferedImage bi,float[][][] grid_pointer, int startX, int startY, int nx, int ny, float lT, float bT, float rT, float tT)
	{
		synchronized( map2D.globals.syncGrid) {
			int left = (int)(bi.getWidth()*lT);
			int right = (int)(bi.getWidth()*rT);
			int top = (int)((float)bi.getHeight()*(1f-tT));
			int bottom = (int)((float)bi.getHeight()*(1f-bT)); 

			if ( grid_pointer == map2D.globals.points ) {//use static grid
				
					//gl.glBegin(GL.GL_TRIANGLE_STRIP); 
					//System.out.println("p left,top=("+grid_pointer[0][startY][startX]+","+grid_pointer[1][startY][startX]+","+grid_pointer[2][startY][startX]+")");
				//System.out.println("drawMapSlice:      left top: "+startX+" , "+startY+" width:"+bi.getWidth()+" height: "+bi.getHeight()+ " nx:"+nx+" ny: "+ny+" x="+grid_pointer[0][startY][startX]+" y="+ grid_pointer[1][startY][startX]);
					
					//gl.glTexCoord2f( lT, tT);gl.glVertex3f( grid_pointer[0][startY][startX], grid_pointer[1][startY][startX], grid_pointer[2][startY][startX]);
					//g.drawOval(10,10,6,9);
					
					//g.drawImage(bi,(int)grid_pointer[0][startY+ny][startX], (int)grid_pointer[1][startY+ny][startX], 128, 128/*(int)grid_pointer[0][startY][startX+nx], (int)grid_pointer[1][startY][startX+nx]*/ , left, top, right, bottom, null);
					//g.drawImage(bi,(int)grid_pointer[0][startY][startX]+16, (int)grid_pointer[1][startY][startX]+8, (int)grid_pointer[0][startY+ny][startX+nx]+16, (int)grid_pointer[1][startY+ny][startX+nx]+8, left, top, right, bottom, null);
					
					int X;
					X=(int)(map2D.globals.DISPLAY_W/map2D.globals.width2D * (grid_pointer[0][startY][startX]-map2D.globals.x2D));
					int Y;
					Y=(int)(map2D.globals.DISPLAY_H/map2D.globals.height2D * (map2D.globals.y2D-grid_pointer[1][startY][startX]));
					int X2;
					X2=(int)(map2D.globals.DISPLAY_W/map2D.globals.width2D * (grid_pointer[0][startY+ny][startX+nx]-map2D.globals.x2D));
					int Y2;
					Y2=(int)(map2D.globals.DISPLAY_H/map2D.globals.height2D * (map2D.globals.y2D-grid_pointer[1][startY+ny][startX+nx]));
					
					//System.out.println("static X= "+X+" Y= "+Y+" X2= "+X2+" Y2= "+Y2);
					g.drawImage(bi,X,Y,X2,Y2, left, top, right, bottom, null);
					//g.drawImage(bi,0,0,512,512,0,0,128,128,null);
					
					//g.drawImage(bi,0,0,128,128,0,0,128,128,null);
					/*
					Component c = new JLabel();
					while ( (c.checkImage( (Image)bi, null)&ImageObserver.HEIGHT)==0 )
					{
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
					*/
					//g.drawImage(bi,((int)grid_pointer[0][startY][startX]+16)*4,((int)grid_pointer[1][startY][startX]-8)*2,Color.WHITE,null);
				
				
					//System.out.println("	p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
					//gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
					
					
					//System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
					//gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
					
					//System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
					//gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
					//System.out.println("p right,bottom=("+grid_pointer[startY+ny][startX+nx][0]+","+grid_pointer[startY+ny][startX+nx][1]+","+grid_pointer[startY+ny][startX+nx][2]+")");
					//gl.glTexCoord2f( rT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX+nx], grid_pointer[1][startY+ny][startX+nx], grid_pointer[2][startY+ny][startX+nx]);
						
					//System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
					//gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
					//gl.glEnd();

			} else {//draw from dynamic grid
				//two triangles: l-t > l-b > r-t and l-b > r-b > r-t
				//derived from a l-t > l-b > r-t > r-b
				//gl.glBegin(GL.GL_TRIANGLE_STRIP); 
				//System.out.println("p left,top=("+grid_pointer[startY][startX][0]+","+grid_pointer[startY][startX][1]+","+grid_pointer[startY][startX][2]+")");
				//gl.glTexCoord2f( lT, tT);gl.glVertex3f( grid_pointer[0][startY][startX], grid_pointer[1][startY][startX], grid_pointer[2][startY][startX]);
				
				//g.drawImage(bi,(int)grid_pointer[0][startY+ny][startX], (int)grid_pointer[1][startY+ny][startX],(int)grid_pointer[0][startY][startX+nx], (int)grid_pointer[1][startY][startX+nx],left, bottom, right, top, null);
				//g.drawImage(bi,startX*nx*2,startY*ny*2,Color.WHITE,null);
				int X;
				X=(int)(map2D.globals.DISPLAY_W/map2D.globals.width2D * (grid_pointer[0][startY][startX]-map2D.globals.x2D));
				int Y;
				Y=(int)(map2D.globals.DISPLAY_H/map2D.globals.height2D * (map2D.globals.y2D-grid_pointer[1][startY][startX]));
				int X2;
				X2=(int)(map2D.globals.DISPLAY_W/map2D.globals.width2D * (grid_pointer[0][startY+ny][startX+nx]-map2D.globals.x2D));
				int Y2;
				Y2=(int)(map2D.globals.DISPLAY_H/map2D.globals.height2D * (map2D.globals.y2D-grid_pointer[1][startY+ny][startX+nx]));
				
				
				//System.out.println("dinamic X= "+X+" Y= "+Y+" X2= "+X2+" Y2= "+Y2);
				
				g.drawImage(bi,X,Y,X2,Y2, left, top, right, bottom,null);
				//g.drawImage(bi,0,0,128,128,0,0,128,128,null);
				/*
				Component c = new JLabel();
				while ( (c.checkImage( (Image)bi, null)&ImageObserver.HEIGHT)==0 )
				{
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				*/
				//g.drawImage(bi,X,Y,Color.WHITE,null);
				
				
				//System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
				//gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
				
				
				//System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
				//gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
				
				//System.out.println("p left,bottom=("+grid_pointer[startY+ny][startX][0]+","+grid_pointer[startY+ny][startX][1]+","+grid_pointer[startY+ny][startX][2]+")");
				//gl.glTexCoord2f( lT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX], grid_pointer[1][startY+ny][startX], grid_pointer[2][startY+ny][startX]);
				//System.out.println("p right,bottom=("+grid_pointer[startY+ny][startX+nx][0]+","+grid_pointer[startY+ny][startX+nx][1]+","+grid_pointer[startY+ny][startX+nx][2]+")");
				//gl.glTexCoord2f( rT, bT);gl.glVertex3f( grid_pointer[0][startY+ny][startX+nx], grid_pointer[1][startY+ny][startX+nx], grid_pointer[2][startY+ny][startX+nx]);
				
				
				//System.out.println("p right,top=("+grid_pointer[startY][startX+nx][0]+","+grid_pointer[startY][startX+nx][1]+","+grid_pointer[startY][startX+nx][2]+")");
				//gl.glTexCoord2f( rT, tT);gl.glVertex3f( grid_pointer[0][startY][startX+nx], grid_pointer[1][startY][startX+nx], grid_pointer[2][startY][startX+nx]);
				//gl.glEnd();
			}
		}
	}
	
	/**
	 * recomputes the dynamic grills points positions after the positions in 
	 * static grill have been modified
	 */
	public void computeDynGrid()
	{
		if ( grid!=null ) {
			float[][][] grid_pointer = map2D.globals.points;
			if ( bDynamicGrid )
				grid_pointer = parent.grid;
			lt_grid[0] = grid_pointer[0][nWorldY][nWorldX];
			lt_grid[1] = grid_pointer[1][nWorldY][nWorldX];
			lt_grid[2] = grid_pointer[2][nWorldY][nWorldX];
			lb_grid[0] = grid_pointer[0][nWorldY+nHeight][nWorldX];
			lb_grid[1] = grid_pointer[1][nWorldY+nHeight][nWorldX];
			lb_grid[2] = grid_pointer[2][nWorldY+nHeight][nWorldX];
			rt_grid[0] = grid_pointer[0][nWorldY][nWorldX+nWidth];
			rt_grid[1] = grid_pointer[1][nWorldY][nWorldX+nWidth];
			rt_grid[2] = grid_pointer[2][nWorldY][nWorldX+nWidth];
			rb_grid[0] = grid_pointer[0][nWorldY+nHeight][nWorldX+nWidth];
			rb_grid[1] = grid_pointer[1][nWorldY+nHeight][nWorldX+nWidth];
			rb_grid[2] = grid_pointer[2][nWorldY+nHeight][nWorldX+nWidth];
			 
			initDynamicGrid( grid);
		}
			
		
		if ( children!=null ) {
			for( int i=0; i<children.length; i++)
				if ( children[i]!=null )
					children[i].computeDynGrid();
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
	protected static void initDynamicGrid( float[][][] grid)
	{
		/**
		 * deduce nx and ny = the number of points for x and y so called axis
		 */
		int nx = grid[0][0].length-1;
		int ny = grid[0].length-1;
		/**
		 * another computing algorithm:
		 * each corner has a weight:
		 * lt -> (nx-i)/nx * (ny-j)/ny
		 * rt -> i/nx * (ny-j)/ny
		 * lb -> (nx-i)/nx * j/ny
		 * rb -> i/nx * j/ny
		 */
		for ( int j=0; j<=ny; j++) {
			for ( int i=0; i<=nx; i++ ) {
				grid[0][j][i] = lt_grid[0] * (nx-i)/nx * (ny-j)/ny + rt_grid[0] * i/nx * (ny-j)/ny + lb_grid[0] * (nx-i)/nx * j/ny + rb_grid[0] * i/nx * j/ny;
				grid[1][j][i] = lt_grid[1] * (nx-i)/nx * (ny-j)/ny + rt_grid[1] * i/nx * (ny-j)/ny + lb_grid[1] * (nx-i)/nx * j/ny + rb_grid[1] * i/nx * j/ny;
				grid[2][j][i] = lt_grid[2] * (nx-i)/nx * (ny-j)/ny + rt_grid[2] * i/nx * (ny-j)/ny + lb_grid[2] * (nx-i)/nx * j/ny + rb_grid[2] * i/nx * j/ny;
			}
		};
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
	public static String[] createTexturePath( int currentLevel, int nwX, int nwY)
	{
		String[] ret = new String[2];
		String path = "map0";
		String prefix = "";
		float widthLOD, heightLOD;
		int x, y;
		widthLOD = Globals.nrDivisionsX;
		heightLOD = Globals.nrDivisionsY;
		int x_map = 0;
		int y_map = 0;
		//for each level idetify the slice number
		for( int l = 0; l<=currentLevel; l++ ) {
			x = 0;
			widthLOD /= texturesX[l];
			while ( x_map+widthLOD <= nwX ) {
				x++;
				x_map += widthLOD;
			};
			y = 0;
			heightLOD /= texturesY[l];
			while ( y_map+heightLOD <= nwY ) {
				y++;
				y_map += heightLOD;
			};
			prefix+="_"+(y+1)+"."+(x+1);
			path += pathSeparator+"map"+(l+1)+prefix;
		};
		ret[0] = path;
		ret[1] = prefix;
		return ret;
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
