package lia.web.servlets.map2d;



/*
 * Created on Apr 5, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author alexc
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class NodeUtilities {
	
	//just for test
	
	
	
	public static float[] transform2Screen( float real_long, float real_lat, float []new_coords){
		if ( new_coords==null )
			new_coords=new float[2];
		new_coords[0]=real_long*Globals.MAP_WIDTH/360;
		new_coords[1]=real_lat*Globals.MAP_HEIGHT/180;
		return new_coords;
	}
	
	public static float[] transform2Coordinates( float real_x, float real_y, float []new_coords){
		if ( new_coords==null )
			new_coords=new float[2];
		new_coords[0]=real_x*360/Globals.MAP_WIDTH;
		new_coords[1]=real_y*180/Globals.MAP_HEIGHT;
		return new_coords;
	}
	
	public static boolean nodeVisible( Map2D map2D, float real_long, float real_lat, float x, float w){
		float []screen_coord = null;
		screen_coord = transform2Screen(real_long, real_lat, screen_coord);
		
		if((x<=screen_coord[0] && screen_coord[0]<=x+w)&&(map2D.globals.y2D>=screen_coord[1] && screen_coord[1]>=map2D.globals.y2D-map2D.globals.height2D))
			return true;
		return false;
		
		/*
		if( screen_coord[1]>Globals.y2D && screen_coord[1]>Globals.y2D ){
			//System.out.println("Texture is up");
			return false;
		}
		if( screen_coord[1]<Globals.y2D-Globals.height2D && screen_coord[1]<Globals.y2D-Globals.height2D){
			//System.out.println("Texture is down");
			return false;
		}
		if( screen_coord[0]<Globals.x2D && screen_coord[0]<Globals.x2D){
			//System.out.println("Texture is left");
			return false;
		}
		if( screen_coord[0]>Globals.x2D+Globals.width2D && screen_coord[0]>Globals.x2D+Globals.width2D ){
			//System.out.println("Texture is right");
			return false;
		}
		//System.out.println("Texture is visible");
		return true;
		*/
	}
	/*
	public static void drawNode(Graphics g, float real_long, float real_lat){
		
		if(nodeVisible(real_long, real_lat)){
			float []screen_coord = transform2Screen(real_long, real_lat);
			float x = (screen_coord[0]-FarmMap.x2D)*FarmMap.DISPLAY_W/FarmMap.width2D;
			float y = (FarmMap.y2D-screen_coord[1])*FarmMap.DISPLAY_H/FarmMap.height2D;
			System.out.println("x= "+x+"y= "+y);
			g.drawOval((int)x, (int) y, 10, 10);
		}
		else
			System.out.println("node not visible");
	}
	
	*/

}
