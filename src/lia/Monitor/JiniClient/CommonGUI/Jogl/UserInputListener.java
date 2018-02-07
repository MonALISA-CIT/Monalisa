package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.util.ntp.NTPDate;

/*
 * Created on May 3, 2004
 *
 */
/**
 * @author mluc
 *
 *	Class UserInputListener
 *	implements mouse actions for jogl renderer
 */
public class UserInputListener implements MouseMotionListener, MouseListener, MouseWheelListener, KeyListener, ActionListener, ChangeListener
{
	private int pMouseAntX=0, pMouseAntY = 0;
    public static int BUTTON_PRESSED_LEFT = 1;
    public static int BUTTON_PRESSED_RIGHT = 3;
    public static int BUTTON_PRESSED_MIDDLE = 2;
    public static int BUTTON_PRESSED_NONE = 0;
    public static int BUTTON_PRESSED_SPOT = 4;
	public int button_pressed = BUTTON_PRESSED_NONE;//1 = left, 3 = right, 2 = middle, 0 = none
//	private int nMousePositionX = 0, nMousePositionY = 0;
    private int pStartSpotX, pStartSpotY;
	
	/**
	 * takes mouse position on map window and returns longitude and latitude of hit point
	 * @param mouse_x
	 * @param mouse_y
	 * @return array with [long,lat] or null
	 */
	public static float[] getPointOnMap( int mouse_x, int mouse_y, float[] coord)
	{
	    return getGeographicalPointOnMap( mouse_x, mouse_y, coord);
	}

	/**
	 * 
	 * @param mouse_x
	 * @param mouse_y
	 * @param coords array of 2 elements or null
	 * @return return (long, lat) location of mouse on map
	 */
	public static float[] getGeographicalPointOnMap( int mouse_x, int mouse_y, float[] coords)
	{
	    float []ccoords = getCarthezianPointOnMap( mouse_x, mouse_y, null);
		//System.out.println("xp="+xp+" yp="+yp+" zp="+zp);
	    if ( ccoords == null )
	        return null;
		return Globals.point3Dto2D( ccoords[0], ccoords[1], ccoords[2], coords);
	}
	
	/**
	 * 
	 * @param mouse_x
	 * @param mouse_y
	 * @param coords array of 3 elements or null
	 * @return return (x,y,z) location of mouse on map
	 */
	public static float[] getCarthezianPointOnMap( int mouse_x, int mouse_y, float[] coords)
	{
	    if ( coords == null || coords.length!=3 )
	        coords = new float[3];
		//0. startup vectors
		float ex, ey, ez;
		ex = (float)JoglPanel.globals.EyePosition.getX();
		ey = (float)JoglPanel.globals.EyePosition.getY();
		ez = (float)JoglPanel.globals.EyePosition.getZ();
		float R, Rv;
		R = JoglPanel.globals.globeRadius;
		Rv = JoglPanel.globals.globeVirtualRadius;
		VectorO Vn = JoglPanel.globals.EyeNormal;
		//1. first line
		VectorO Vd1 = new VectorO(JoglPanel.globals.EyeDirection);
		float angle_m, angle_n;
		float tan = (float)Math.tan(Globals.FOV_ANGLE/2f * Math.PI/180.0f);
		float tan_m = (2f*mouse_x-JoglPanel.globals.width)/JoglPanel.globals.width*tan;
		angle_m = (float)Math.atan( tan_m);
		angle_n = (float)Math.atan( (2f*mouse_y-JoglPanel.globals.height)/JoglPanel.globals.height*tan/JoglPanel.globals.fAspect/Math.sqrt(1+tan_m*tan_m));
		Vd1.Rotate( Vn, -angle_m*180f/(float)Math.PI);
		VectorO Vm = Vd1.CrossProduct(Vn);
		Vd1.Rotate( Vm, -angle_n*180f/(float)Math.PI);
		//2. intersection point
		float xp, yp, zp;
		float t_sol;
		if ( R!=-1 ) {
			//construct the 2nd grade equation
			float a, b, c;
			c = ex*ex+ey*ey+(ez+R-Rv)*(ez+R-Rv)-R*R;
			b = (float)(ex*Vd1.getX()+ey*Vd1.getY()+(ez+R-Rv)*Vd1.getZ());
			a = (float)(Vd1.getX()*Vd1.getX()+Vd1.getY()*Vd1.getY()+Vd1.getZ()*Vd1.getZ());
			/**
			 * solution of this ( at^2+2bt+c=0 ) equation:
			 * t1,2 = (-b+/-sqrt(b^2-a*c))/a
			 */ 
			float delta;
			delta = b*b-a*c;
			if ( delta<0 )
				return null;//no translation is done
			delta = (float)Math.sqrt(delta);
			float t1, t2;
			t1 = (-b+delta)/a;
			t2 = (-b-delta)/a;
			if ( t1>=0 ) //t1 positive
				if ( t1 <= t2 ) //t1,t2 positive, t1 smaller than t2
					t_sol = t1;
				else if ( t2 >= 0 ) //t1,t2 positive, t2 smaller than t1
					t_sol = t2;
				else if ( t1 <= -t2 ) //t1 positive, t2 negative, abs(t1)<abs(t2)
					t_sol = t1;
				else //t1 positive, t2 negative, abs(t1)>abs(t2)
					t_sol = t2;
			else //t1 negative
				if ( t1 >= t2 ) //t1,t2 negative, abs(t1)<abs(t2)
					t_sol = t1;
				else if ( t2<=0 ) //t1,t2 negative, abs(t1)>abs(t2)
					t_sol = t2;
				else if ( t2<=-t1 ) //t1 negative, t2 positive, abs(t1)>abs(t2)
					t_sol = t2;
				else
					t_sol = t1;
		} else {
			t_sol = -ez/(float)Vd1.getZ();
			//System.out.println("plane t_sol="+t_sol);
			//TODO: check solution
		}
		xp = ex + (float)Vd1.getX()*t_sol;
		yp = ey + (float)Vd1.getY()*t_sol;
		zp = ez + (float)Vd1.getZ()*t_sol;
		coords[0] = xp;
		coords[1] = yp;
		coords[2] = zp;
		return coords;
	}

	/**
	 * computes 3d direction from eye position based on mouse coordinates in the viewable frustrum
	 * considering a rectangle as 2d area for mouse to move over
	 * @param mouse_x
	 * @param mouse_y
	 * @return vector that has a direction that coresponds to 3d coordinates for the mouse...
	 */
	public static VectorO getVectorToPointOnMap( int mouse_x, int mouse_y)
	{
		//0. startup vectors
//		float ex, ey, ez;
//		ex = (float)JoglPanel.globals.EyePosition.getX();
//		ey = (float)JoglPanel.globals.EyePosition.getY();s
//		ez = (float)JoglPanel.globals.EyePosition.getZ();
//		float R, Rv;
//		R = JoglPanel.globals.globeRadius;
//		Rv = JoglPanel.globals.globeVirtualRadius;
		VectorO Vn = JoglPanel.globals.EyeNormal;
		//1. first line
		VectorO Vd1 = new VectorO(JoglPanel.globals.EyeDirection);
		float angle_m, angle_n;
		float tan = (float)Math.tan(Globals.FOV_ANGLE/2f * Math.PI/180.0f);
		float tan_m = (2f*mouse_x-JoglPanel.globals.width)/JoglPanel.globals.width*tan;
		angle_m = (float)Math.atan( tan_m);
		angle_n = (float)Math.atan( (2f*mouse_y-JoglPanel.globals.height)/JoglPanel.globals.height*tan/JoglPanel.globals.fAspect/Math.sqrt(1+tan_m*tan_m));
		Vd1.Rotate( Vn, -angle_m*180f/(float)Math.PI);
		VectorO Vm = Vd1.CrossProduct(Vn);
		Vd1.Rotate( Vm, -angle_n*180f/(float)Math.PI);
		return Vd1;
	}
	
	/**
	 * traslates the object in view according to mouse movement on window.<br>
	 * uses eye position, eye normal, eye direction, field of view angle, pMouseAntX, pMouseAntY
	 * @param x new mouse position on x axis<br>varies from 0 to w
	 * @param y new mouse position on y axis<br>varies from 0 to h
	 */
	private void translate( int x, int y)
	{
		/**
		 * algorithm:
		 * 0. starup variables: d, n, m, x, y, xant, yant, FOVA
		 * 1. based on xant, yant make a line in space.
		 * - this line contains eye point
		 * - this line is constructed by rotating the direction vector around n axis and m axis ( m = d x n )
		 * - the rotation angles are determined by using xant, yant, w, h, fov angle, with the formula:
		 * 		angle_m = atan( (2*xant-w)/w*tan(FOVA/2)),
		 * 		angle_n = atan( (2*yant-h)/h*tan(FOVA/2)/fAspect/sqrt(1+tan^2(FOVA/2))),
		 * where m and n corespond to x and y axis
		 * and the m axis follows the direction axis as both rotate around n with angle_m
		 * Line equation is, based on one point coordinates and equation of a vector:
		 * 	x = ex + dx*t
		 * 	y = ey + dy*t
		 * 	z = ez + dz*t
		 * where dx, dy, dz are projections of the direction vector rotated with angle_m around n axis and
		 * angle_n around m axis
		 * 2. determin the intersection point between the line and the sphere:
		 * - sphere equation is:
		 * 	x^2 + y^2 + (z+R-Rv)^2 = R^2,
		 * where Rv is the virtual globe radius
		 * - this equation is only applied when R!=-1, otherwise the equation is one of a plane:
		 * 	z = 0 (!!!)
		 * - the intersection means:
		 * -for sphere:
		 * 	(ex+dx*t)^2+(ey+dy*t)^2+(ez+dz*t+R-Rv)^2=R^2
		 * 	This equation has 2, 1 or 0 solutions. For 2 solutions, the chosen one is the one that is closest to eye:
		 * 	(xsol-ex)^2+(ysol-ey)^2+(zsol-ez)^2=min
		 * but this becomes:
		 * 	(d1x^2+d1y^2+d1z^2)*t_sol^2 = min
		 * and, so, this is minim when abs(t_sol) is minim,
		 * that is because t is a multiplying  factor for vector starting in eye position, so, distance is smaller as
		 * vector is smaller, as factor is smaller.
		 * 	For 0 solutions, there will be no translation.
		 * - for plane:
		 * 	ez+dz*t=0, and also some constraints that can be activated:
		 * 	x in (-map_width/2,map_width/2) and y in (-map_height/2,map_heght/2)
		 * 	If constraints are not fulfilled, there will be no translation
		 * The intersection point is: xp, yp, zp.
		 * 3. determin the line equation that goes through (xp,yp,zp) and is paralel with the eye direction vector,
		 * after it has been rotated with angles computed from x and y ( hope this make sense :D )
		 * using step one, line equation looks like this:
		 * 	x = xp + dx2*t
		 * 	y = yp + dy2*t
		 * 	z = zp + dz2*t,
		 * where step 1 uses d1 as the rotated direction vector, while step 3 uses d2
		 * 4. the intersection point between the second line and the eye translation plane ( perpendicular on eye direction)
		 * is the new eye position.
		 * 	plane equation: d.e = d.e_new
		 *  	line equation: e_new = p + t*d2
		 * 	results that
		 * 		t = d.(e-p)/d.d2
		 * where d is eye direction vector,
		 * 		e is eye position vector
		 * 		p is the point determined in step 3
		 * 		d2 is the second line direction vector
		 * 		e_new is the new eye position
		 * 		t is a scalar parameter
		 * So, first find the parameter, and then the new eye position.
		 */
		//0. startup vectors
		float ex, ey, ez;
		ex = (float)JoglPanel.globals.EyePosition.getX();
		ey = (float)JoglPanel.globals.EyePosition.getY();
		ez = (float)JoglPanel.globals.EyePosition.getZ();
		float R, Rv;
		R = JoglPanel.globals.globeRadius;
		Rv = JoglPanel.globals.globeVirtualRadius;
		VectorO Vn = JoglPanel.globals.EyeNormal;
		//1. first line
		VectorO Vd1 = new VectorO(JoglPanel.globals.EyeDirection);
		float angle_m, angle_n;
		float tan = (float)Math.tan(Globals.FOV_ANGLE/2f * Math.PI/180.0f);
		float tan_m = (2f*pMouseAntX-JoglPanel.globals.width)/JoglPanel.globals.width*tan;
		angle_m = (float)Math.atan( tan_m);
		angle_n = (float)Math.atan( (2f*pMouseAntY-JoglPanel.globals.height)/JoglPanel.globals.height*tan/JoglPanel.globals.fAspect/Math.sqrt(1+tan_m*tan_m));
		Vd1.Rotate( Vn, -angle_m*180f/(float)Math.PI);
		VectorO Vm = Vd1.CrossProduct(Vn);
		Vd1.Rotate( Vm, -angle_n*180f/(float)Math.PI);
		//2. intersection point
		float xp, yp, zp;
		float t_sol;
		if ( R!=-1 ) {
			//construct the 2nd grade equation
			float a, b, c;
			c = ex*ex+ey*ey+(ez+R-Rv)*(ez+R-Rv)-R*R;
			b = (float)(ex*Vd1.getX()+ey*Vd1.getY()+(ez+R-Rv)*Vd1.getZ());
			a = (float)(Vd1.getX()*Vd1.getX()+Vd1.getY()*Vd1.getY()+Vd1.getZ()*Vd1.getZ());
			/**
			 * solution of this ( at^2+2bt+c=0 ) equation:
			 * t1,2 = (-b+/-sqrt(b^2-a*c))/a
			 */ 
			float delta;
			delta = b*b-a*c;
			if ( delta<0 )
				return;//no translation is done
			delta = (float)Math.sqrt(delta);
			float t1, t2;
			//System.out.println("a="+a+" b="+b+" c="+c);
			t1 = (-b+delta)/a;
			t2 = (-b-delta)/a;
			if ( t1>=0 ) //t1 positive
				if ( t1 <= t2 ) //t1,t2 positive, t1 smaller than t2
					t_sol = t1;
				else if ( t2 >= 0 ) //t1,t2 positive, t2 smaller than t1
					t_sol = t2;
				else if ( t1 <= -t2 ) //t1 positive, t2 negative, abs(t1)<abs(t2)
					t_sol = t1;
				else //t1 positive, t2 negative, abs(t1)>abs(t2)
					t_sol = t2;
			else //t1 negative
				if ( t1 >= t2 ) //t1,t2 negative, abs(t1)<abs(t2)
					t_sol = t1;
				else if ( t2<=0 ) //t1,t2 negative, abs(t1)>abs(t2)
					t_sol = t2;
				else if ( t2<=-t1 ) //t1 negative, t2 positive, abs(t1)>abs(t2)
					t_sol = t2;
				else
					t_sol = t1;
			//System.out.println("sphere t1="+t1+" t2="+t2);
		} else {
			t_sol = -ez/(float)Vd1.getZ();
			//System.out.println("plane t_sol="+t_sol);
			//TODO: check solution
		}
		xp = ex + (float)Vd1.getX()*t_sol;
		yp = ey + (float)Vd1.getY()*t_sol;
		zp = ez + (float)Vd1.getZ()*t_sol;
		//System.out.println("xp="+xp+" yp="+yp+" zp="+zp);
		//3. second line direction vector
		VectorO Vd2= new VectorO(JoglPanel.globals.EyeDirection);
		tan_m = (2f*x-JoglPanel.globals.width)/JoglPanel.globals.width*tan;
		angle_m = (float)Math.atan( tan_m);
		angle_n = (float)Math.atan( (2f*y-JoglPanel.globals.height)/JoglPanel.globals.height*tan/JoglPanel.globals.fAspect/Math.sqrt(1+tan_m*tan_m));
		Vd2.Rotate( JoglPanel.globals.EyeNormal, -angle_m*180f/(float)Math.PI);
		Vm = Vd2.CrossProduct(JoglPanel.globals.EyeNormal);
		Vd2.Rotate( Vm, -angle_n*180f/(float)Math.PI);
		//4. determin new eye position
		float t;
		VectorO Vp = new VectorO( ex-xp, ey-yp, ez-zp);
		t = (float)JoglPanel.globals.EyeDirection.DotProduct(Vp)/(float)JoglPanel.globals.EyeDirection.DotProduct(Vd2);
		VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
		newEyePosition.setX( xp+t*Vd2.getX());
		newEyePosition.setY( yp+t*Vd2.getY());
		newEyePosition.setZ( zp+t*Vd2.getZ());
		Globals.falisafeUpdateEyeVectors( newEyePosition, null, null);
        //recheck visibility
        Texture.checkVisibility();
//      JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//      JoglPanel.globals.bIsIdle = true;
        JoglPanel.globals.canvas.repaint();
	}

	/**
	 * rotates the map with respect to the hit points only for sphere projection
	 * @param x - final mouse position on x
	 * @param y - final mouse position on y
	 */
	private void rotate( int x, int y)
	{
		float delta_rot;
		float eyeRadius = (float)JoglPanel.globals.EyePosition.getRadius(); 
		VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
		VectorO newEyeDirection = new VectorO(JoglPanel.globals.EyeDirection);
		VectorO newEyeNormal = new VectorO(JoglPanel.globals.EyeNormal);
	    //if plane projection, permit only rotation around x axis
		if ( JoglPanel.globals.globeRadius==-1 ) {
		    //TODO: make a mathematical formula this rotation should respect
		    //rotation of eye around the z axis based on mouse movement on x axis
//		    float[] coordsRotAxis = getCarthezianPointOnMap( pMouseAntX, pMouseAntY, null);
		    VectorO vZrotAxis;
	        vZrotAxis = new VectorO(0,0,1);
	        //TODO: rotate round mouse point
//		    if ( coordsRotAxis == null )
//		        vZrotAxis = new VectorO(0,0,1);
//		    else
//		        vZrotAxis = new VectorO( coordsRotAxis);
			delta_rot = 10*(-x+pMouseAntX)*eyeRadius/JoglPanel.globals.width;
			newEyePosition.Rotate( vZrotAxis, delta_rot);
			newEyeDirection.Rotate( vZrotAxis, delta_rot);
			newEyeNormal.Rotate( vZrotAxis, delta_rot);
			//rotation of eye around the x axis
			VectorO vXRelrotAxis;
			vXRelrotAxis = newEyeDirection.CrossProduct(newEyeNormal);
			delta_rot = 10*(-y+pMouseAntY)*eyeRadius/JoglPanel.globals.height;//40;
			//TODO: fully understand that the conditions are only particular, and should be rechecked again
			//pp. EyeDirection a vector in yO-z plane
			//if the angle between this vector and -z axis is larger than 60 degrees
			//stay at 60
			VectorO vAux = new VectorO(newEyeDirection);
			vAux.Rotate( vXRelrotAxis, delta_rot);
			if ( Math.abs(Math.acos(vAux.DotProduct(new VectorO(0,0,-1)))) < Math.PI/3 ) {
				newEyePosition.Rotate( vXRelrotAxis, delta_rot);
				newEyeDirection.Rotate( vXRelrotAxis, delta_rot);
				newEyeNormal.Rotate( vXRelrotAxis, delta_rot);
			} else
			    return;
		} else {//globe rotation
		    /**
		     * to have a nice rotation round the globe's main axis, this is a small trick algorithm:<br>
		     * - if there is movement on x axis for mouse, and
		     * - if movement on y axis is reduced comparing with movement on x axis, that meaning that
		     * 				dy < dx && dy < Max_MinY then
		     * - use (0,1,0) as rotation axis and agle is given by movement on x axis, so, 
		     * - use case for when mouse is outside the globe.
		     */
		    //compute geographical and carthezian coordinates
		    //first point
		    float[] coordsStart = getCarthezianPointOnMap( pMouseAntX, pMouseAntY, null);
		    //second point
		    float[] coordsEnd = getCarthezianPointOnMap( x, y, null);
		    //int Max_MinY = 10;//maximal deviation for y axis when we're interested in movement on x axis
		    //minimal distance from eye position to a point on globe is
		    //float min_depth = (float)(eyeRadius-JoglPanel.globals.globeRadius);
		    VectorO vRotAxis;
		    VectorO vStart, vEnd;
		    if ( coordsStart!=null && coordsEnd != null ) {
			    vStart = new VectorO(coordsStart);
			    vEnd = new VectorO(coordsEnd);
			    //compute rotation axis
			    vRotAxis = vStart.CrossProduct( vEnd);
			    //compute angle between the 2 vectors
			    delta_rot =- (float)Math.acos( Globals.limitValue(vStart.DotProduct( vEnd)/vStart.getRadius()/vEnd.getRadius(), -1, 1) );//in radians
			    //TODO: is this ok???
//			    if ( 		            /** condition for trick **/
//		            Math.abs(y-pMouseAntY) > Math.abs(x-pMouseAntX) &&
//		            Math.abs(y-pMouseAntY) > Max_MinY &&
//		            min_depth > 20f /*????*/
//			    )
//			        vRotAxis = new VectorO(0,1,0);
			    //rotate eye vectors with - angle around rotation axis
				newEyePosition.RotateRadian( vRotAxis, delta_rot);
				newEyeDirection.RotateRadian( vRotAxis, delta_rot);
				newEyeNormal.RotateRadian( vRotAxis, delta_rot);
		    } else {
		        //compute rotation with no respect to mouse position...?
				vRotAxis = new VectorO(0,1,0);//JoglPanel.globals.EyeNormal);
				delta_rot = (-x+pMouseAntX)*eyeRadius/40;
				newEyePosition.Rotate( vRotAxis, delta_rot);
				newEyeDirection.Rotate( vRotAxis, delta_rot);
				newEyeNormal.Rotate( vRotAxis, delta_rot);
		    }
		    //rotate moon, only if perfect sphere
//		    if ( JoglPanel.globals.mapAngle==90 ) {
//		        float moon_rot = -delta_rot/JoglPanel.globals.moon_cycle;
//                JoglPanel.globals.vMoonPosition.Rotate( JoglPanel.globals.vMoonRotationAxis, moon_rot);
//		    }
		}
		Globals.falisafeUpdateEyeVectors( newEyePosition, newEyeDirection, newEyeNormal);
        //recheck visibility
        Texture.checkVisibility();
//      JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//      JoglPanel.globals.bIsIdle = true;
        JoglPanel.globals.canvas.repaint();
	}

	/**
	 * changes the zoom or makes a translation to the new position
	 * @param x
	 * @param y
	 */
	private void dragMouse( int x, int y)
	{
		if ( button_pressed == BUTTON_PRESSED_RIGHT || (button_pressed==BUTTON_PRESSED_LEFT && JoglPanel.globals.nButtonType==Globals.BUTTON_ROTATE) ) {//rotate
			//rotate(x,y);
			rotate(x,y);
		} else if ( button_pressed == BUTTON_PRESSED_LEFT )
		    if ( JoglPanel.globals.nButtonType==Globals.BUTTON_ZOOM ) {
				zoom( (pMouseAntY-y)/10, x, y);
		    } else if ( JoglPanel.globals.nButtonType==Globals.BUTTON_RADIO_ROTATE ) {
                rotate(x,y);
            } else {//translate
				translate( x, y);
			}
        else if ( button_pressed == BUTTON_PRESSED_SPOT ) {
            //update spot position
            boolean bVerticalBar = (JoglPanel.globals.nRBHotSpot==InfoPlane.POSITION_LEFT||JoglPanel.globals.nRBHotSpot==InfoPlane.POSITION_RIGHT);
            int nRBnewHotSpotPos = 0;
            int deplRB = (bVerticalBar?y-pStartSpotY:x-pStartSpotX);
            nRBnewHotSpotPos = InfoPlane.doMouse2RotBarDepl(deplRB);
            if ( nRBnewHotSpotPos > 100 )
                nRBnewHotSpotPos = 100;
            else if ( nRBnewHotSpotPos<-100 ) 
                nRBnewHotSpotPos = -100;
            JoglPanel.globals.nRBHotSpotPos = nRBnewHotSpotPos;
            //rotate globe
            float depl = (bVerticalBar?y-pMouseAntY:x-pMouseAntX);
            boolean bInverseDir = (JoglPanel.globals.nRBHotSpot==InfoPlane.POSITION_TOP||JoglPanel.globals.nRBHotSpot==InfoPlane.POSITION_RIGHT);
            if ( bInverseDir )
                depl = -depl;
            //compute rotation with no respect to mouse position...?
            VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
            VectorO newEyeDirection = new VectorO(JoglPanel.globals.EyeDirection);
            VectorO newEyeNormal = new VectorO(JoglPanel.globals.EyeNormal);
            float eyeRadius = (float)JoglPanel.globals.EyePosition.getRadius(); 
            float delta_rot = depl*(eyeRadius-JoglPanel.globals.globeRadius)/5f;
            float MAX_ABS_ROT = 10;
            if ( delta_rot>MAX_ABS_ROT )
                delta_rot=MAX_ABS_ROT;
            if ( delta_rot<-MAX_ABS_ROT )
                delta_rot=-MAX_ABS_ROT;
            VectorO vRotAxis;
            if ( bVerticalBar ) {
                vRotAxis = JoglPanel.globals.EyeDirection;
                newEyeNormal.Rotate( vRotAxis, delta_rot);
            } else {
                vRotAxis = new VectorO(0,1,0);//JoglPanel.globals.EyeNormal);
                newEyePosition.Rotate( vRotAxis, delta_rot);
                newEyeDirection.Rotate( vRotAxis, delta_rot);
                newEyeNormal.Rotate( vRotAxis, delta_rot);
            }
            Globals.falisafeUpdateEyeVectors( newEyePosition, newEyeDirection, newEyeNormal);
            //recheck visibility
            Texture.checkVisibility();
//          JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//          JoglPanel.globals.bIsIdle = true;
            JoglPanel.globals.canvas.repaint();
            //JoglPanel.globals.canvas.repaint();
        }
            ;//do something
		pMouseAntX = x;
		pMouseAntY = y;
//		JoglPanel.globals.correctSkyPosition();
//		//reset start idle time
//		JoglPanel.globals.resetIdleTime();
////		JoglPanel.globals.startIdleTime = System.currentTimeMillis();
////		JoglPanel.globals.bIsIdle = true;
//		JoglPanel.globals.canvas.repaint();
	}
	
	private void zoom( int zoom, int mouse_x, int mouse_y)
	{
		int MAX_ZOOM = 20; //mouse sensitivity
		if ( zoom>MAX_ZOOM )
			zoom = MAX_ZOOM-1;
		else if ( zoom < -MAX_ZOOM )
		    zoom = -MAX_ZOOM+1;
		else if ( zoom == 0 )
		    return;//no zoom to do
	    float globeRadius = JoglPanel.globals.globeRadius;
		VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
		VectorO newEyeDirection = new VectorO(JoglPanel.globals.EyeDirection);
		VectorO newEyeNormal = new VectorO(JoglPanel.globals.EyeNormal);
        //check if map is at maximum distance
        //distance to map
        float dist;
        if ( globeRadius!=-1 ) {
            dist = (float)newEyePosition.getRadius();
        } else {
            dist = (float)JoglPanel.globals.EyePosition.DotProduct(JoglPanel.globals.EyeDirection);
            if ( dist < 0 )
                dist = -dist;
        };
        if ( dist > 100f && zoom < 0 )
            return;//distance is too great and zoom out, do not zoom
		//apply an algorithm that takes in consideration mouse position
		float []coords = getCarthezianPointOnMap( mouse_x, mouse_y, null);
		if ( coords!=null ) {
//			System.out.println("mouse_x="+mouse_x+" mouse_y="+mouse_y);
//		    System.out.println("mouse pos(x,y,z)=("+coords[0]+","+coords[1]+","+coords[2]+")");
		    //compute difference vector from EyePosition to MousePosition
		    VectorO vMousePos = new VectorO(coords);
		    VectorO vDiff = VectorO.SubstractVector( vMousePos, newEyePosition);
		    vDiff.MultiplyScalar( (double)zoom/(double)MAX_ZOOM);
			if ( globeRadius!=-1 ) {
				//TODO: correct zoom, this is only an approximative algorithm
				VectorO vEyePosFuture = new VectorO(newEyeDirection);
				vEyePosFuture.MultiplyScalar(Globals.MIN_DEPTH+Globals.NEAR_CLIP);
				vEyePosFuture.AddVector( newEyePosition);
			    vEyePosFuture.AddVector( vDiff);
				if ( vEyePosFuture.getRadius() > globeRadius+Globals.MIN_DEPTH || vEyePosFuture.getRadius() > newEyePosition.getRadius() )
				    newEyePosition.AddVector( vDiff);
				else
				    return;
			} else
			    newEyePosition.AddVector( vDiff);
		} else {
			float depl;
			if ( globeRadius!=-1 ) {
				float eyeRadius = (float)newEyePosition.getRadius();
				depl = (eyeRadius-JoglPanel.globals.globeVirtualRadius)*zoom/MAX_ZOOM;
				//TODO: correct zoom
				if ( eyeRadius-depl>JoglPanel.globals.globeVirtualRadius && eyeRadius-depl<Globals.MAX_DEPTH )
					newEyePosition.MultiplyScalar((eyeRadius-depl)/eyeRadius);
				else
				    return;
			} else {
				//for plane projection, zoom is made along view direction
				VectorO eye_dir = new VectorO(newEyeDirection);
				depl = newEyePosition.getZ()*zoom/MAX_ZOOM;
				eye_dir.MultiplyScalar(depl);
				newEyePosition.AddVector(eye_dir);
				//TODO: correct position
			}
		};
        //System.out.println("eye radius: "+newEyePosition.getRadius()+" sphere radius: "+globeRadius+" min depth: "+Globals.MIN_DEPTH);
        
		Globals.falisafeUpdateEyeVectors( newEyePosition, newEyeDirection, newEyeNormal);
        //recheck visibility
        Texture.checkVisibility();
//      JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//      JoglPanel.globals.bIsIdle = true;
        JoglPanel.globals.canvas.repaint();
	}
	
	/**
	 * moves the cursor at the right position to corespond to mouse pointer<br>
	 * takes in consideration z distance between cursor and map
	 * @param x new position of mouse pointer on x axis
	 * @param y new position of mouse pointer on y axis
	 */
/*	private void moveCursor( int x, int y)
	{
		//this fx differs from fx computed bellow
		float fx = 2*((float)Main.globals.EyePosition.getZprojection()-Main.globals.CursorPosition[2])*(float)Math.tan(Globals.FOV_ANGLE*Math.PI/360.0f);
		float fy = fx/Main.globals.fAspect;
		Main.globals.CursorPosition[0]=(float)Main.globals.EyePosition.getXprojection()+((float)x/(float)Main.globals.width-0.5f)*fx;
		Main.globals.CursorPosition[1]=(float)Main.globals.EyePosition.getYprojection()+(-(float)y/(float)Main.globals.height+0.5f)*fy;
	}
*/	
	/**
	 * corects the view position so that there is no black space that can be occupied
	 * by visible map
	 * @param fx view frustum on x
	 * @param fy view frustum on y
	 * @param dx new computed displacement of the view position on x axis that has to be corrected
	 * @param dy new computed displacement of the view position on y axis that has to be corrected
	 */
/*	public void correctViewPosition( float fx, float fy, float dx, float dy)
	{
		//this fx differs from fx computed above
//		float fx = 2*Main.globals.EyePosition[2]*(float)Math.tan(Globals.FOV_ANGLE*Math.PI/360.0f);
//		float fy = fx/Main.globals.fAspect;
		//if view frustum tries to go beyond right margin, stop it
		if ( (float)Main.globals.EyePosition.getXprojection()+dx+fx*.5f > .5f*Globals.MAP_WIDTH )
			dx = .5f*Globals.MAP_WIDTH-(float)Main.globals.EyePosition.getXprojection()-fx*.5f;
		//if view frustum tries to go beyond left margin, stop it
		if ( (float)Main.globals.EyePosition.getXprojection()+dx-fx*.5f < -.5f*Globals.MAP_WIDTH )
			dx = -.5f*Globals.MAP_WIDTH-(float)Main.globals.EyePosition.getXprojection()+fx*.5f;
		Main.globals.EyePosition.setX(Main.globals.EyePosition.getX()+dx);
		//Main.globals.EyeDirection[0]+=dx;
		//for y there are 2 cases: when the map height is smaller than the view frustum's height
		//or when is it greater, so:
		//1. map_height < fy
		if ( Globals.MAP_HEIGHT < fy ) {
			//if the eye tries to go up and hide bottom part of the map...
			if ( (float)Main.globals.EyePosition.getYprojection()+dy-fy*.5f > -.5f*Globals.MAP_HEIGHT )
				dy = -.5f*Globals.MAP_HEIGHT+fy*.5f-(float)Main.globals.EyePosition.getYprojection();
			//else, if the eye tries to go too down, stop
			if ( (float)Main.globals.EyePosition.getYprojection()+dy+fy*.5f < .5f*Globals.MAP_HEIGHT )
				dy = .5f*Globals.MAP_HEIGHT-fy*.5f-(float)Main.globals.EyePosition.getYprojection();
		} else {
		//2. map_height >= fy
			//if view frustum tries to go beyond top margin, stop it
			if ( (float)Main.globals.EyePosition.getYprojection()+dy+fy*.5f > .5f*Globals.MAP_HEIGHT )
				dy = .5f*Globals.MAP_HEIGHT-(float)Main.globals.EyePosition.getYprojection()-fy*.5f;
			//if view frustum tries to go beyond bottom margin, stop it
			if ( (float)Main.globals.EyePosition.getYprojection()+dy-fy*.5f < -.5f*Globals.MAP_HEIGHT )
				dy = -.5f*Globals.MAP_HEIGHT-(float)Main.globals.EyePosition.getYprojection()+fy*.5f;
		};
		Main.globals.EyePosition.setY((float)Main.globals.EyePosition.getYprojection()+dy);
		//Main.globals.EyeDirection[1]+=dy;
	}
*/	
	
	/**
	 * save current mouse position, maybe I'll need it some times
	 */
//	private void saveMousePosition( MouseEvent e)
//	{
//	    nMousePositionX = e.getX();
//	    nMousePositionY = e.getY();
//	}

	public void mouseDragged(MouseEvent e) 
	{
		mouseEvent( e);
//		saveMousePosition(e);
		dragMouse( e.getX(), e.getY());
	}

	public void mouseMoved(MouseEvent e) 
	{
		mouseEvent( e);
//        saveMousePosition(e);
        //check to see if mouse on rotation bar
        //save previous value
        if ( Globals.bShowRotationBar ) {
            int old_nShowRBs = Globals.nShowRBs;
            Globals.nShowRBs = InfoPlane.checkPointOnRotBar( e.getX(), e.getY());
            //System.out.println("nShowRBs="+Globals.nShowRBs);
            if ( old_nShowRBs!=Globals.nShowRBs )
                //if a change in showing, redraw
                //only if not rotation
                if ( !JoglPanel.globals.mainPanel.renderer.sr.IsInRotation() )
                    JoglPanel.globals.canvas.repaint();
        };
        if ( JoglPanel.globals.mainPanel.renderer.sr.IsInRotation() ) {
            return;//if in rotation, don't show tooltips, so, don't send mouse move event on globe
        }
        //System.out.println("selected bars: "+Globals.nShowRBs);
	    int []mouse_position = new int[] {e.getX(), e.getY()};
	    //float []map_coordinates = getPointOnMap( e.getX(), e.getY(), null);
	    DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_MOUSE_MOVE, mouse_position);//map_coordinates);
	}

	public void mouseClicked(MouseEvent e) 
	{
		mouseEvent( e);
//		saveMousePosition(e);
	    float []map_coordinates = getPointOnMap( e.getX(), e.getY(), null);
	    if ( e.getButton()==MouseEvent.BUTTON1 )
	        DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_MOUSE_CLICK, map_coordinates);
	    else if ( e.getButton() == MouseEvent.BUTTON3 )
	        DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_MOUSE_DBLCLICK, map_coordinates);
	}

	public void mouseEntered(MouseEvent e) 
	{
		mouseEvent( e);
//		saveMousePosition(e);
		pMouseAntX = e.getX();
		pMouseAntY = e.getY();
	}

	public void mouseExited(MouseEvent e) 
	{
		mouseEvent( e);
//		saveMousePosition(e);
	}

	public void mousePressed(MouseEvent e) 
	{
		mouseEvent( e);
//		saveMousePosition(e);
		button_pressed = e.getButton();
		pMouseAntX = e.getX();
		pMouseAntY = e.getY();
        //check to see if on spot
        if ( (JoglPanel.globals.nRBHotSpot=InfoPlane.checkPointOnSpot( pMouseAntX, pMouseAntY, JoglPanel.globals.nRBHotSpotPos))!=0 ) {
            button_pressed = BUTTON_PRESSED_SPOT;
            pStartSpotX = pMouseAntX;
            pStartSpotY = pMouseAntY;
            JoglPanel.globals.canvas.repaint();
        };
	}

	public void mouseReleased(MouseEvent e) {
		mouseEvent( e);
//		saveMousePosition(e);
		button_pressed = BUTTON_PRESSED_NONE;
        JoglPanel.globals.nRBHotSpot = 0;//works only if previously it was true
        JoglPanel.globals.nRBHotSpotPos = 0;
        pStartSpotX = 0;
        pStartSpotY = 0;
        JoglPanel.globals.canvas.repaint();
	}
/*	
	public void computeViewFrustum()
	{
		float dx = (float)Main.globals.EyePosition.getZprojection()*(float)Math.tan(Globals.FOV_ANGLE*Math.PI/360.0f);
		float dy = dx/Main.globals.fAspect;
		Main.globals.startFx = (float)Main.globals.EyePosition.getXprojection()-dx;
		Main.globals.endFx = (float)Main.globals.EyePosition.getXprojection()+dx;
		Main.globals.startFy = (float)Main.globals.EyePosition.getYprojection()+dy;
		Main.globals.endFy = (float)Main.globals.EyePosition.getYprojection()-dy;
	}
*/
	/**
	 * zooms in or out of the map<br>
	 * wheel down means zoom out<br>
	 * wheel up means zoom in
	 */
	public void mouseWheelMoved(MouseWheelEvent e) 
	{
		mouseEvent( e);
		zoom( -e.getWheelRotation(), e.getX(), e.getY());
	}

	/* (non-Javadoc)
	 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
	 */
	public void keyPressed(KeyEvent e) {
		keyEvent(e);
	}

	/* (non-Javadoc)
	 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
	 */
	public void keyReleased(KeyEvent e) {
		keyEvent(e);
	}

	/* (non-Javadoc)
	 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
	 */
	public void keyTyped(KeyEvent e) {
		keyEvent(e);
		if ( e.getKeyChar()=='a' || e.getKeyChar()=='A' ) {
			System.out.println("map angle: "+JoglPanel.globals.mapAngle);
		} else if ( e.getKeyChar()=='f' || e.getKeyChar()=='F' ) {
			JoglPanel.globals.charPressed = 'F';
			JoglPanel.globals.canvas.repaint();
		} else if ( e.getKeyChar()=='b' || e.getKeyChar()=='B' ) {
			JoglPanel.globals.charPressed = 'B';
			JoglPanel.globals.canvas.repaint();
        } else if ( e.getKeyChar()=='j' || e.getKeyChar()=='J' ) {
            JoglPanel.globals.charPressed = 'J';
		} else if ( e.getKeyChar()=='l' || e.getKeyChar()=='L' ) {
	        JoglPanel.globals.charPressed = 'L';
			//JoglPanel.globals.canvas.repaint();
        } else if ( e.getKeyChar()=='d' || e.getKeyChar()=='D' ) {
            JoglPanel.globals.charPressed = 'D';
		} else if ( e.getKeyChar()=='k' || e.getKeyChar()=='K' ) {
		    JoglPanel.globals.charPressed = '#';
		} else if ( e.getKeyChar()=='s' || e.getKeyChar()=='S' ) {
			JoglPanel.globals.bShowChartesianSystem = !JoglPanel.globals.bShowChartesianSystem;
			JoglPanel.globals.canvas.repaint();
		} else if ( e.getKeyChar()=='m' || e.getKeyChar()=='M' ) {
			Globals.printMemory();
		} else if ( e.getKeyChar()=='t' || e.getKeyChar()=='T' ) {
			changeProjection();
		} else if ( e.getKeyChar()=='e' || e.getKeyChar()=='E' ) {
		    System.out.println("EyePosition: "+JoglPanel.globals.EyePosition);
		    System.out.println("EyeDirection: "+JoglPanel.globals.EyeDirection);
		    System.out.println("EyeNormal: "+JoglPanel.globals.EyeNormal);
		}
/*		if ( e.getKeyChar()=='d' || e.getKeyChar()=='D' ) {
			//start physicaly deleting the texture files
			Texture.removeTextFiles();
		} else if ( e.getKeyChar()=='c' || e.getKeyChar()=='C' ) {
			//reset deletion flag
			Main.globals.root.resetDel();
			Main.globals.canvas.repaint();
		}
*/	}
	
	/**
	 * changes the map from plane projection to sphere projection, or other way
	 * around<br>
	 * does that in several steps so that the user can enjoy the transition
	 *
	 */
	private void changeProjection()
	{
	    if ( JoglPanel.globals.mainPanel.renderer.projectionStatus.getState() == ChangeProjectionStatus.PROJECTION_CHANGING )
	        return;
		DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_RADIUS_CHANGE_START, null);
        //to be sure that is executed without delay
        Timer t = new Timer();
		/*BackgroundWorker*/t.schedule(new TimerTask() {
		    public void run()
			{
		    	//first change the button's icon
		    	if ( JoglPanel.globals.bMapTransition2Sphere )
		    		JoglPanel.globals.mainPanel.proj_button.setIcon( JoglPanel.dglobals.iconPlaneProj);
		    	else
		    		JoglPanel.globals.mainPanel.proj_button.setIcon( JoglPanel.dglobals.iconSphereProj);
				if ( JoglPanel.globals.bMapTransition2Sphere ) {
					if ( JoglPanel.globals.mapAngle>= 90 ) {
						JoglPanel.globals.bMapTransition2Sphere = false;
						JoglPanel.globals.mapAngle = 90;
						this.cancel();
						DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_RADIUS_CHANGE_FINISH, null);
						//reset to recompute
						JoglPanel.globals.resetIdleTime();
						return;
					}
					JoglPanel.globals.mapAngle += 2;
					JoglPanel.globals.globeRadius = Globals.MAP_WIDTH*45f/(float)Math.PI/JoglPanel.globals.mapAngle;
				} else {
					if ( JoglPanel.globals.mapAngle <= 0 ) {
						JoglPanel.globals.bMapTransition2Sphere = true;
						JoglPanel.globals.mapAngle = 0;
						this.cancel();
						
						VectorO[] vEnds = { new VectorO(0,0,32), new VectorO(0,0,-1), new VectorO(0,1,0)};
					    double fraction = (90-JoglPanel.globals.mapAngle)/(double)90;
					    JoglPanel.globals.rotate( vEnds, fraction);
						
						DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_RADIUS_CHANGE_FINISH, null);
						//reset idle time to recompute textures
						JoglPanel.globals.resetIdleTime();
						return;
					}
					JoglPanel.globals.mapAngle -= 2;
					if ( JoglPanel.globals.mapAngle <= 0 ) {
						JoglPanel.globals.globeRadius = -1f;
					} else {
						JoglPanel.globals.globeRadius = Globals.MAP_WIDTH*45f/(float)Math.PI/JoglPanel.globals.mapAngle;
						
						//set eye direction and normal to point perpendicular on plane map
						VectorO[] vEnds = { new VectorO(0,0,32), new VectorO(0,0,-1), new VectorO(0,1,0)};
					    double fraction = (90-JoglPanel.globals.mapAngle)/(double)90;
					    //System.out.println( "fraction="+fraction);
					    JoglPanel.globals.rotate( vEnds, fraction);
					};
				}
				JoglPanel.globals.computeGrid(JoglPanel.globals.root);
				//correct so that eye not behind the sphere
				if ( JoglPanel.globals.EyePosition.getRadius() < JoglPanel.globals.globeVirtualRadius ) {
                    VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
                    newEyePosition.MultiplyScalar( (JoglPanel.globals.globeVirtualRadius+1)/newEyePosition.getRadius());
                    Globals.falisafeUpdateEyeVectors(newEyePosition, null, null);
                    //recheck visibility
//                    Texture.checkVisibility();
//                  JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//                  JoglPanel.globals.bIsIdle = true;
                    //JoglPanel.globals.canvas.repaint();
				    //JoglPanel.globals.correctSkyPosition(null, null, null);
				};
				DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_RADIUS_CHANGED, null);
				//the repaint is automatically done when this event is treated
				//JoglPanel.globals.canvas.repaint();
			}
		}, 0, JoglPanel.globals.CHANGE_PROJECTION_TIME);
		
	}

	/**
	 * receives actions from buttons, etc on toolbar panel
	 */
	public void actionPerformed(ActionEvent e) {
	    String cmd = e.getActionCommand();
	    if(cmd.equals("changeProjection")) {
	    	changeProjection();
	    } else if(cmd.equals("changeNodesRenderer")) {
	    	JRadioButton rb = (JRadioButton)e.getSource();
	        String rendererName = (String)rb.getText();
	        JoglPanel.globals.mainPanel.renderer.setActiveNodesRenderer(rendererName);
	        //invalidate links and nodes
	        DataRenderer.sendGlobeEvent( DataRenderer.GLOBE_OPTION_PANEL_CHANGE, Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_RENDERER_CHANGED));
	    } else if ( cmd.equals("reset")) {
	        //resets to initial position
			new Timer().schedule(new TimerTask() {
			    int nStep=0;
			    int nMaxSteps = 10;
			    public void run()
				{
                    Thread.currentThread().setName("Jogl - Change projection timer, step "+nStep+" of "+nMaxSteps);
					VectorO[] vEnds = { new VectorO(0,0,32), new VectorO(0,0,-1), new VectorO(0,1,0)};
				    JoglPanel.globals.rotate( vEnds, nStep/(double)nMaxSteps);
				    //JoglPanel.globals.correctSkyPosition(null, null, null);
                    Texture.checkVisibility();
					JoglPanel.globals.canvas.repaint();
			        //check textures
					JoglPanel.globals.resetIdleTime();

					//at the end, increment step
				    nStep++;
				    if ( nStep > nMaxSteps ) {
				        this.cancel();
				    };
				}
			}, 0, JoglPanel.globals.CHANGE_PROJECTION_TIME);
	    } else if ( cmd.equals("select")) {
	        JoglPanel.globals.nButtonType = Globals.BUTTON_SELECT;
	    } else if ( cmd.equals("rotate")) {
	        JoglPanel.globals.nButtonType = Globals.BUTTON_ROTATE;
        } else if ( cmd.equals("rotate_radio")) {
            JoglPanel.globals.nButtonType = Globals.BUTTON_RADIO_ROTATE;
	    } else if ( cmd.equals("translate")) {
	        JoglPanel.globals.nButtonType = Globals.BUTTON_TRANSLATE;
	    } else if ( cmd.equals("zoom")) {
	        JoglPanel.globals.nButtonType = Globals.BUTTON_ZOOM;
        } else if ( cmd.equals("zoom_radio")) {
            JoglPanel.globals.nButtonType = Globals.BUTTON_RADIO_ZOOM;
	    }
/*
	    synchronized(syncObject) {
		    //do not call organize because we don't search for new nodes at this stage
			computeVectors();
//			if ( graphicalAttributes[0].get( "DetailLevel_ShowLinksOnChangeProjection")!=null ) {
//			    bInvalidateLinks = true;
//			};
	    };
	    //if ( bInvalidateLinks )
	    JoglPanel.globals.canvas.repaint();
//	    System.out.println("radius changed");
	}
 */		
	}

    static private boolean bResetSlider = false;
    private int nAntSliderValue = 0;
	/**
	 * used for change in toolbar sliders
	 */
    public void stateChanged(ChangeEvent e) {
        Object src = e.getSource();
        if ( src == JoglPanel.globals.mainPanel.scaleSlider ) {
            JoglPanel.globals.nScaleFactor = JoglPanel.globals.mainPanel.scaleSlider.getValue();
    	    JoglPanel.globals.canvas.repaint();
        } else if ( src == JoglPanel.globals.mainPanel.sliderSlider ) {
            //check to see if zoom or rotation
            //use value of slider, only if not reseting
            if ( JoglPanel.globals.mainPanel.sliderSlider.getValueIsAdjusting() && !bResetSlider ) {
                //do something
                //System.out.println("current slider value: "+JoglPanel.globals.mainPanel.sliderSlider.getValue());
                int nCurValue = JoglPanel.globals.mainPanel.sliderSlider.getValue();
                if ( JoglPanel.globals.nButtonType == Globals.BUTTON_RADIO_ROTATE ) {
                    //do rotation
                    //compute rotation with no respect to mouse position...?
                    VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
                    VectorO newEyeDirection = new VectorO(JoglPanel.globals.EyeDirection);
                    VectorO newEyeNormal = new VectorO(JoglPanel.globals.EyeNormal);
                    float eyeRadius = (float)JoglPanel.globals.EyePosition.getRadius(); 
                    float delta_rot = (nCurValue-nAntSliderValue)*(eyeRadius-JoglPanel.globals.globeRadius)/5f;
//                    float MAX_ABS_ROT = 10;
//                    if ( delta_rot>MAX_ABS_ROT )
//                        delta_rot=MAX_ABS_ROT;
//                    if ( delta_rot<-MAX_ABS_ROT )
//                        delta_rot=-MAX_ABS_ROT;
                    VectorO vRotAxis;
                    vRotAxis = new VectorO(0,1,0);//JoglPanel.globals.EyeNormal);
                    newEyePosition.Rotate( vRotAxis, delta_rot);
                    newEyeDirection.Rotate( vRotAxis, delta_rot);
                    newEyeNormal.Rotate( vRotAxis, delta_rot);
                    Globals.falisafeUpdateEyeVectors( newEyePosition, newEyeDirection, newEyeNormal);
                    //recheck visibility
                    Texture.checkVisibility();
//                  JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//                  JoglPanel.globals.bIsIdle = true;
                    JoglPanel.globals.canvas.repaint();
                    //JoglPanel.globals.canvas.repaint();
                } else if ( JoglPanel.globals.nButtonType == Globals.BUTTON_RADIO_ZOOM ) {
                    zoom( (nCurValue-nAntSliderValue), JoglPanel.globals.width/2, JoglPanel.globals.height/2);
                }
                //set old value
                nAntSliderValue = nCurValue;
            };
            if ( !JoglPanel.globals.mainPanel.sliderSlider.getValueIsAdjusting() && !bResetSlider) {
                bResetSlider = true;
                nAntSliderValue = 0;
                new Timer().schedule(new TimerTask() {
                    public void run() {
                        JoglPanel.globals.mainPanel.sliderSlider.setValue(0);
                        bResetSlider = false;
                    }
                }, 10);
                //System.out.println("slider is not adjusting, new value = "+JoglPanel.globals.mainPanel.sliderSlider.getValue());
            };
//            if ( JoglPanel.globals.nButtonType == Globals.BUTTON_ROTATE )
        } else if ( src == JoglPanel.globals.mainPanel.timeSlider ) {
            int sleeptime;
            double newangle;
            sleeptime=SphereRotator.MIN_SLEEP_TIME;
            //val between 0 and 100
            int val = JoglPanel.globals.mainPanel.timeSlider.getValue();
//            System.out.println("new rotation val="+val);
            //compute rotation angle between 0.01 and 1.34
            newangle = 0.01+(double)val*.0133;
            //stop rotator if slider below 1
            if ( val < 1 ) {
                sleeptime=-1;
                newangle=0;
            };
            JoglPanel.globals.mainPanel.renderer.sr.setSleepTime( sleeptime, newangle);
        }
    }

    /** last time in miliseconds when mouse or key event was generated */
    public long lastMovementTime = -1;
	private void mouseEvent(MouseEvent e) {
		lastMovementTime = NTPDate.currentTimeMillis();
	}
	private void keyEvent( KeyEvent e) {
		lastMovementTime = NTPDate.currentTimeMillis();
	}
}
