package lia.web.servlets.map2d;

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
public class Globals 
{
	public Map2D map2d;
	
	//Map2D parameters
	public float width2D = 0;
	public float height2D = 0;
	public float x2D = 0;
	public float y2D = 0;
	public int zoom2D = 0;
	
	public int show_shadow = -1;
	public int show_lights = -1;
	
	
	public int DISPLAY_W = 800;
	public int DISPLAY_H = 400;
	
	
//	 Perspective & Window defines
	public static final  float FOV_ANGLE     =45.0f;
	public static final  float NEAR_CLIP     =0.01f;
	public static final  float FAR_CLIP      =2000.0f;

	//window aspect
	public float fAspect=1;
	public int width, height;//window in frame width and height ( canvas system dimension and ratio )
	
	//used to set shadow on map
	//should be changed at each 5 minutes, and then all textures reloaded...
	//Date currentTime = new Date();
	
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
	 * globe radius when changing from plane projection to sphere projection
	 * related to MAP_WIDTH
	 * maximal value is 90*MAP_WIDTH/PI -> this value aproximates the infinit to an angle of 1 degree
	 * minimal value for radius is: MAP_WIDHT/2/PI
	 * a -1 value for the globe radius means that it is used a plane projection
	 */
	public float globeRadius = -1;//MAP_WIDTH*45f/(float)Math.PI/mapAngle;
	/**
	 * virtual radius of the globe: means distance from nearest point on sphere to virtual center<br>
	 * usefull for some computations<br>
	 * There is a direct connection between virtual radius and radius.
	 */
	public float globeVirtualRadius = globeRadius;//0;
	
	public int nScaleFactor = 50;//scale factor for nodes on map, updated by scale slider in second toolbar and used for nodes radius
	
	//max depth the center of the map can be from the eye
	public static final float MAX_DEPTH = MAP_WIDTH*.5f/(float)Math.tan(FOV_ANGLE*Math.PI/360.0f);
	//minimal depth the center of the map can be from the eye
	public static final float MIN_DEPTH = 0.02f; //usefull for displaying text in left free space
	
	//number of divisions for sphere map on x and y
	public static final int nrDivisionsX = 64;
	public static final int nrDivisionsY = 32;
	//properties for a division of the map
	public static final float divisionWidth = (float)MAP_WIDTH/nrDivisionsX;
	public static final float divisionHeight = (float)MAP_HEIGHT/nrDivisionsY;
	//points that reprezents the map projection on a sphere
	//TODO: remove comment after all references have been corrected
	public final float[][][] points = new float[3][nrDivisionsY+1][nrDivisionsX+1];
	
	//root of textures tree
	public Texture root;
	
	//used to synchromize access to points array
	public Object syncGrid = new Object();

	public void computeVirtualSphereCenterZ()
	{
			map2d.globals.globeVirtualRadius = 0f;
	}
	
	/**
	 * computes the 3D coordinates of a point that is on the grid at (x,y) position
	 * @param x position on grid on x axis
	 * @param y position on grid on y axis
	 * @return an float array with 3 coordinates for 3D
	 */
	public float[] point2Dto3D(int x, int y, float[] coord)
	{
		if ( coord == null )
			coord = new float[3];
		float px, py, pz;
		float aux;
		px = -(float)MAP_WIDTH/2f + divisionWidth*x;
		py = (float)MAP_HEIGHT/2f - divisionHeight*y;
		pz = map2d.globals.globeVirtualRadius;
		coord[0] = px;
		coord[1] = py;
		coord[2] = pz;
		if ( map2d.globals.globeRadius != -1 ) {
			aux = py/map2d.globals.globeRadius;
			coord[1] = map2d.globals.globeRadius*(float)Math.sin( aux);
			aux =map2d.globals.globeRadius*(float)Math.cos( aux);
			coord[0] = aux*(float)Math.sin( px/map2d.globals.globeRadius);
			coord[2] = pz - map2d.globals.globeRadius + aux*(float)Math.cos( px/map2d.globals.globeRadius);
		};
		return coord;
	}
	
	/**
	 * computes the 3D coordinates of a point that is on the globe map at (lat,long) position,<br>
	 * where lat is in [-90,90] and long in [-180;180]
	 * @param lat position on plane map on y axis
	 * @param long position on plane map on x axis
	 * @return an float array with 3 coordinates for 3D
	 */
	public float[] point2Dto3D(float latitude, float longitude, float[] coord)
	{
		if ( coord == null )
			coord = new float[3];
		float px, py, pz;
//		float aux;
		//correct latitude if outside the interval
		if ( latitude > 90f )
			latitude = 89.90f;
		else if ( latitude < -90f )
			latitude = -89.90f;
		//correct longitude if outside the interval
		if ( longitude > 180f )
			longitude = 179.90f;
		else if ( longitude < -180f )
			longitude = -179.90f;
		py = latitude/180f*MAP_HEIGHT;
		px = longitude/360f*(float)MAP_WIDTH;
		pz =map2d.globals.globeVirtualRadius;
		coord[0] = px;
		coord[1] = py;
		coord[2] = pz;
		if (map2d.globals.globeRadius != -1 ) {
		    //version 1.
//			aux = py/JoglPanel.globals.globeRadius;
//			coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( aux);
//			aux = JoglPanel.globals.globeRadius*(float)Math.cos( aux);
//			coord[0] = aux*(float)Math.sin( px/JoglPanel.globals.globeRadius);
//			coord[2] = pz - JoglPanel.globals.globeRadius + aux*(float)Math.cos( px/JoglPanel.globals.globeRadius);
			//version 2. (only for complete sphere projection)
//		    coord[1] = JoglPanel.globals.globeRadius*(float)Math.sin( latitude/180*Math.PI);
//		    coord[0] = JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.sin( longitude/180*Math.PI);
//		    coord[2] = JoglPanel.globals.globeVirtualRadius - JoglPanel.globals.globeRadius + JoglPanel.globals.globeRadius*(float)Math.cos( latitude/180*Math.PI)*(float)Math.cos( longitude/180*Math.PI);
			//version 3. equivalent to v1.
			float alpha_lat, alpha_long, r;
			r = map2d.globals.globeRadius;
			alpha_lat = py/r;
			alpha_long = px/r;
			coord[1] = (float)(r*Math.sin(alpha_lat));
			coord[0] = (float)(r*Math.cos(alpha_lat)*Math.sin(alpha_long));
			coord[2] = pz - r + (float)(r*Math.cos(alpha_lat)*Math.cos(alpha_long));
		};
		return coord;
	}
	
	/**
	 * returns a vector with longitude on position 0 and latitude on position 1, to equivalete
	 * with x and y axis
	 * @param x
	 * @param y
	 * @param z
	 * @param coord if initialized, it fills this vector
	 * @return a new vector or updatede coord vector
	 */
	public float[] point3Dto2D( float x, float y, float z, float coord[])
	{
		if ( coord == null )
			coord = new float[2];
		if ( map2d.globals.globeRadius==-1 ) {
		    coord[0] = x*360/(float)MAP_WIDTH;
		    coord[1] = y*180f/(float)MAP_HEIGHT;
		} else {
		    //version 1.
//		    coord[0] = (float)(360*JoglPanel.globals.globeRadius/(float)MAP_WIDTH*Math.asin(x/JoglPanel.globals.globeRadius/Math.cos(y/JoglPanel.globals.globeRadius)));
//		    coord[1] = (float)(180*JoglPanel.globals.globeRadius/(float)MAP_HEIGHT*Math.asin(y/JoglPanel.globals.globeRadius));
		    //version 2.
//		    float r = JoglPanel.globals.globeRadius;
//		    coord[1] = (float)(180/Math.PI*Math.asin(y/r));
//		    coord[0] = (float)(180/Math.PI*Math.asin(x/Math.sqrt(r*r-y*y)));
		    //version 3.
		    float r = map2d.globals.globeRadius;
		    coord[1] = (float)(180*r/MAP_HEIGHT*Math.asin(y/r));
		    //coord[0] = (float)(180*r/MAP_HEIGHT*Math.atan(x/z));
		    if ( x > 0)
		        if ( z > 0 )
		            coord[0] = (float)(180*r/MAP_HEIGHT*Math.atan(x/z));
		        else if ( z == 0 )
		            coord[0] = 90;
		        else
		            coord[0] = (float)(180 + 180*r/MAP_HEIGHT*Math.atan(x/z));
		    else if ( x == 0 )
		        if ( z >= 0 )
		            coord[0] = 0;
		        else
		            coord[0] = 180;
		    else
		        if ( z > 0)
		            coord[0] = (float)(180*r/MAP_HEIGHT*Math.atan(x/z));
		        else if ( z == 0 )
		            coord[0] = -90;
		        else
		            coord[0] = (float)(-180 + 180*r/MAP_HEIGHT*Math.atan(x/z));
		}
		return coord;
	}
	
	/**
	 * recomputes the grid of points based on the globe radius<br>
	 * It first considers the plane coordinates that it then transforms to 3d coordinates on a sphere
	 * of globeRadius radius/<br>
	 * If radius == -1 then the coordinates should remain plane so no trasformations is applied.
	 */
	public void computeGrid()
	{
//		float px, py, pz;
		//float step_x, step_y;
//		float aux;
		computeVirtualSphereCenterZ();
		//step_x = MAP_WIDTH/nrDivisionsX;
		//step_y = MAP_HEIGHT/nrDivisionsY;
		//for each point on the grid
		int x, y;
		float[] coord = new float[3];
		synchronized( Texture.syncObject_Texture ) {
			synchronized( syncGrid) {
			for ( y=0; y<=nrDivisionsY; y++)
				for ( x=0; x<=nrDivisionsX; x++) {
					//compute the plane coordinates x in (-Width/2;Width/2), y in (Height/2;-Height/2), and z=0
					point2Dto3D( x, y, coord);
					points[0][y][x] = coord[0];
					points[1][y][x] = coord[1];
					points[2][y][x] = coord[2];
					//System.out.println("POINTS x= " +coord[0]+" y= "+coord[1]);
				}
			};
		//System.out.println("p(x,y,z)=("+points[y][x][0]+","+points[y][x][1]+","+points[y][x][2]+")");

		//todo: also recompute all dynamic grids in tree
			root.computeDynGrid();
		}
	}
	
	
	public double limitValue( double val, double minVal, double maxVal)
	{
	    return val>maxVal?maxVal:val<minVal?minVal:val;
	}
}
