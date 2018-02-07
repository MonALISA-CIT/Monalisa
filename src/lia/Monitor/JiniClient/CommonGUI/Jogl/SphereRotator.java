/*
 * Created on Dec 20, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;


/**
 * rotates the sphere projection with a small angle
 * Dec 20, 2004 - 10:58:57 AM
 */
public class SphereRotator extends Thread implements GlobeListener {
    
    private double nRotationAngle = 0;//angle for one rotation
    private boolean bFrozen = true;//rotator is frozen, one reason can be that projection is not sphere
    private int nSleepTime = 0;//sleep time from one rotation to the next, in miliseconds
    private String sThreadName = "";
    public static final int MIN_SLEEP_TIME=30;
    
    /** sync drawing of current rotation object so that no rotation will be skipped */
    private Object syncDrawCurrentRotation = new Object();
    /** draw current rotation pending, a wait is neccessary */
    private boolean bDrawCurrentRotation = false;
    
    /**
     * give name to this sphere rotator thread
     * @param name
     */
    public SphereRotator(String name)
    {
        sThreadName=name;
    }
    
    /**
     * sets new values for rotator: sleep time and rotation angle
     * @param newVal sleep time
     * @param newAngle new degree angle
     */
    public void setSleepTime( int newVal, double newAngle)
    {
    	if ( newVal>=0 && newVal<MIN_SLEEP_TIME )
    		newVal=MIN_SLEEP_TIME;
        nSleepTime = newVal;
        if ( newAngle < .01 )
            nRotationAngle = 0;
        else
            nRotationAngle = newAngle;
    }
    
    public int getSleepTime()
    {
        if ( bFrozen )
            return -1;
        return nSleepTime;
    }
    
    public int getPositiveSleepTime()
    {
        int nST = getSleepTime();
        if ( nST == -1 || nST == 0 )
            nST = 1000;
        return nST;
    }
    
    public void setFrozen( boolean bVal)
    {
        bFrozen = bVal;
    }
    
    private void rotate()
    {
        if ( nRotationAngle < .01 )
            return;
        VectorO Vn = new VectorO(0,-1,0);//JoglPanel.globals.EyeNormal);
        VectorO newEyePosition = new VectorO(JoglPanel.globals.EyePosition);
        VectorO newEyeDirection = new VectorO(JoglPanel.globals.EyeDirection);
        VectorO newEyeNormal = new VectorO(JoglPanel.globals.EyeNormal);
        newEyePosition.Rotate( Vn, nRotationAngle);
        newEyeDirection.Rotate( Vn, nRotationAngle);
        newEyeNormal.Rotate( Vn, nRotationAngle);
        if ( !Globals.bHideMoon ) {
            double moon_rot = -nRotationAngle+nRotationAngle/JoglPanel.globals.moon_cycle;
            JoglPanel.globals.vMoonPosition.Rotate( JoglPanel.globals.vMoonRotationAxis, moon_rot);
        };
        Globals.falisafeUpdateEyeVectors( newEyePosition, newEyeDirection, newEyeNormal);
        
        //recheck visibility
        Texture.checkVisibility();
//      JoglPanel.globals.startIdleTime = System.currentTimeMillis();
//      JoglPanel.globals.bIsIdle = true;
    }
    
    public void run()
    {
        setName(sThreadName);
        while (true) {
            int val = getSleepTime();
            try {
                if ( val == -1 || val == 0 || !JoglPanel.globals.bJoglInitialized 
                		|| !JoglPanel.globals.mainPanel.monitor.main.isVisible() 
                		|| !JoglPanel.globals.mainPanel.monitor.main.isShowing() 
                		|| !JoglPanel.globals.canvas.isVisible()
                		|| !JoglPanel.globals.canvas.isShowing() )
                    sleep( 1000 );
                else {
                	//System.out.println("is visible!!!!!!!");
                    JoglPanel.globals.canvas.repaint();
                    sleep( val);
//                    synchronized (Globals.syncPrintObject) {
//                        System.out.println("start wait");
//                    }
                    waitForRotationDrawn( val);
//                    synchronized (Globals.syncPrintObject) {
//                        System.out.println("end wait");
//                    }
                    rotate();
                    rotationDrawPending();
//                    synchronized (Globals.syncPrintObject) {
//                        System.out.println("next rotation");
//                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void radiusChanged() {    }
    
    public void radiusChangeStart() {
        setFrozen( true);
    }
    
    public void radiusChangeFinish() {
        if ( JoglPanel.globals.globeRadius==-1 )
            setFrozen( true);
        else
            setFrozen( false);
    }
    
    public boolean IsInRotation() {
        return ( !bFrozen && getSleepTime()>0 );
    }
    
    public void mouseClick(float LONG, float LAT) {    }
    
    public void mouseDblClick(float LONG, float LAT) {    }
    
    public void mouseMove(int mouse_x, int mouse_y) {    }
    
    public void optionPanelChanged(int event) {    }
    
    /**
     * sets that rotation is drawn
     */
    public void rotationDrawn() {
//        synchronized (Globals.syncPrintObject) {
//            System.out.println("before notification");
//        }
        synchronized ( syncDrawCurrentRotation ) {
            bDrawCurrentRotation = false;
            syncDrawCurrentRotation.notify/*All*/();
        }
//        synchronized (Globals.syncPrintObject) {
//            System.out.println("after notification");
//        }
    }
    
    /**
     * waiting for a rotation drawing
     */
    public void rotationDrawPending() {
        synchronized ( syncDrawCurrentRotation ) {
            bDrawCurrentRotation = true;
        }
    }
    
    /**
     * waits in a sync zone until the variable becomes true
     * @param rotationSleepTime sleep time between rotations
     */
    public void waitForRotationDrawn( int rotationSleepTime) {
        boolean bWait = true;
        do {
            synchronized (syncDrawCurrentRotation) {
                if ( bDrawCurrentRotation ) {
                    //wait until becomes false
//                    System.out.println("inainte");
                    try {
                        syncDrawCurrentRotation.wait(getPositiveSleepTime()/*1000*/);
                    } catch (InterruptedException e) {
                    }
//                    System.out.println("dupa");
                    //if timeout, draw next...
//                    bDrawCurrentRotation = false;
                } else
                    bWait = false;
            }
            JoglPanel.globals.canvas.repaint();
        } while ( bWait);
    }
}
