/*
 * Created on May 27, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimerTask;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Globals;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Texture;
import lia.util.ntp.NTPDate;

/**
 * takes care of changing the root texture so that the map can be changed from one startup resolution level 
 * to another<br>
 * Monitors the process of creating the new set of textures for selected resolution and then swaps the
 * new root texture with the old one.
 *
 * May 27, 2005 - 4:59:18 PM
 */
public class ChangeRootTexture extends Thread
{
    private JProgressBar jpbDownloadBar = null; //progress bar to show statu of download
    private JComponent jpDownloadPanel = null; //panel that includes progress bar and label
    private int new_level=0;//the level that indicates the resolution map to be loaded
    private Texture new_root;//new root texture
    /** id of new tree */
    private int new_treeid=0;
    /** last used id of tree, 0 means no last tree */
    private static int nTreeID=0;
    private int nTotal=0;//total textures to be loaded
    private int nLoadedSoFar=0;//number of textures loaded so far
    /** available resolutions */
    public static String sResolutions[] = new String[] { "1024x512", "2048x1024", "4096x2048", "8192x4096", "16384x8192"};
    /** stores the previous state of progress bar's panel */
    private boolean bHiddenBefore = false;//
    
    /** only one instance allowed to load a new texture */
    private static ChangeRootTexture thisInstance = null;
    /** sync object to set that only one instance */
    private static Object sync_crtInstance = new Object();
    
    private static class ChangeRequest {
    	int nl;
    	int nKeyMode;
    	JProgressBar jProgress;
    	JPanel jPanel;
    }
    /** queue that contains all requests made while resolving another one */
    private static ArrayList qRequests = new ArrayList();
    
    /** sync object to notify when new textures were set */
    private Object newTexturesToLoad = new Object();
    
    private static boolean bDebug = false;//debug mode
    public static int CRT_KEY_MODE_LOAD = 0;
    public static int CRT_KEY_MODE_UPDATE = 1;
    public static int CRT_KEY_MODE_DO_SHADOW = 2;
    public static int CRT_KEY_MODE_REMOVE_SHADOW = 3;
    public static int CRT_KEY_MODE_DO_LIGHTS = 4;
    public static int CRT_KEY_MODE_REMOVE_LIGHTS = 5;
    private int nKeyMode = CRT_KEY_MODE_LOAD;
    private String sKeyMode = "";//text to specify why this changer is called
//    private boolean bLoopMode = false;//after loading a texture, set it for update, more like an infinite loop
    //actualy, a real infinite recursive loop if called from another update 
    
    /**
     * sets debug level for this class
     * @param bIsDebug set debug mode if true, else do not show debug messages
     */
    public static void setDebugLevel(boolean bIsDebug)
    {
        bDebug = bIsDebug;
//        if ( bDebug )
//            logLevel = Level.INFO;
//        else
//            logLevel = Level.FINEST;
    }
    
    private Date currentTime;//date to update shadow
    
    /**
     * dummy constructor only to be able to make it private and to not be
     * created from another class
     */
    private ChangeRootTexture() {}
    
    /**
     * sets the new level in range of available resolutions
     * @param nl
     */
    private void setNewLevel(int nl) {
        this.new_level=nl;
        if ( new_level<0 )
            new_level = 0;
        if ( new_level>=sResolutions.length )
            new_level=sResolutions.length-1;
    }

    private void setLevelAndKeyMode(int nl, int nKeyMode) {
        setNewLevel(nl);
        this.nKeyMode=nKeyMode;
        if ( nKeyMode == CRT_KEY_MODE_LOAD )
            sKeyMode = "Loading";
        else if ( nKeyMode == CRT_KEY_MODE_UPDATE )
            sKeyMode = "Updating";
        else if ( nKeyMode == CRT_KEY_MODE_DO_SHADOW )
            sKeyMode = "Do shadow";
        else if ( nKeyMode == CRT_KEY_MODE_REMOVE_SHADOW )
            sKeyMode = "Removing shadow";
        else if ( nKeyMode == CRT_KEY_MODE_DO_LIGHTS )
            sKeyMode = "Do lights";
        else if ( nKeyMode == CRT_KEY_MODE_REMOVE_LIGHTS )
            sKeyMode = "Removing lights";
    }

//    public ChangeRootTexture(int nl, String sKeyMode, boolean bLoopMode) {
//        this(nl, sKeyMode);
////        this.bLoopMode = bLoopMode;
//    }
    
    /**
     * retrieves next available tree id
     */
    public static synchronized int getNextTreeID() 
    {
        return ++nTreeID;
    }
    
    /**
     * creates a new chage root texture object and queues it if another change is in progress
     * @author mluc
     * @since Jul 13, 2006
     * @param nl
     * @param nKeyMode
     * @param jPB
     * @param jP
     * @return true if it is going to be run now, false if it is queued for later run
     */
    public static boolean init(int nl, int nKeyMode, JProgressBar jPB, JPanel jP) {
    	ChangeRequest cr = new ChangeRequest(), auxcr;
    	cr.jPanel=jP;
    	cr.jProgress = jPB;
    	cr.nKeyMode = nKeyMode;
    	cr.nl = nl;
    	boolean bAdd = true;
        synchronized (sync_crtInstance) {
        	//TODO: before adding, check if already in queue, or if not neccessary
        	//if load, then discard all waiting transformation, because some will be
        	//also automatically applied (update, shadow, etc) and others are not neccessary (another load)
        	if ( cr.nKeyMode == CRT_KEY_MODE_LOAD )
        		qRequests.clear();
        	else if ( cr.nKeyMode == CRT_KEY_MODE_DO_LIGHTS || cr.nKeyMode == CRT_KEY_MODE_REMOVE_LIGHTS
        			|| cr.nKeyMode == CRT_KEY_MODE_DO_SHADOW || cr.nKeyMode == CRT_KEY_MODE_REMOVE_SHADOW ) {
        		for ( int i=0; i<qRequests.size(); i++) {
        			auxcr=(ChangeRequest)qRequests.get(i);
        			if ( auxcr.nKeyMode == CRT_KEY_MODE_DO_LIGHTS || auxcr.nKeyMode == CRT_KEY_MODE_REMOVE_LIGHTS
                			|| auxcr.nKeyMode == CRT_KEY_MODE_DO_SHADOW || auxcr.nKeyMode == CRT_KEY_MODE_REMOVE_SHADOW 
                			|| auxcr.nKeyMode == CRT_KEY_MODE_UPDATE ) {
        				qRequests.remove(i);
        				i--;
        			} else if ( auxcr.nKeyMode == CRT_KEY_MODE_LOAD ) {
        				bAdd = false;
        			}
        		}
        	} else {//CRT_KEY_MODE_UPDATE
        		if ( qRequests.size()>0 )
        			bAdd = false;//already another request in queue, so not neccessary to set this update, because
        		//the map will automatically be updated 
        	}
        	if ( bAdd )
        		qRequests.add(cr);
            if ( thisInstance != null ) {
            	sync_crtInstance.notify();
                return false;
            };
            thisInstance = new ChangeRootTexture();
            thisInstance.start();
            return true;
        }
    }
    
    /**
     * initializes the new texture loading mechanism<br>
     * constructs initial tree with a new root<br>
     * shows the progress bar<br>
     * start a thread to monitor the loading of textures
     * @return true if this is a valid instance, or false if there is already an instance running
     */
    public void run()
    {
    	ChangeRequest cr;
    	while( true ) {
            synchronized (sync_crtInstance) {
		        try {
		        	cr = (ChangeRequest)qRequests.remove(0);
		        } catch (Exception ex) {
		        	cr = null;
		        }
		        if ( cr==null ) {
		        	try {
						sync_crtInstance.wait();
					} catch (InterruptedException ex) {
					}
		        	continue;//get a new change request
		        };
            };
            try {
		        this.setLevelAndKeyMode(cr.nl, cr.nKeyMode);
		        this.setProgressBar(cr.jProgress, cr.jPanel);
		        this.currentTime = new Date(NTPDate.currentTimeMillis());//date to update shadow
	            //increment new tree texture id
	            new_treeid = getNextTreeID();
	            //reset all values
	            this.nLoadedSoFar = 0;
	            this.nTotal = 0;
	            //construct initial tree, but it does not use actual coordinates in the grid
	            //so it does not need the initialized coordinates yet
	            if ( bDebug )
	                System.out.println("creating new root texture");
	            new_root = Texture.constructInitialTree(this, new_level);
	            if ( bDebug )
	                System.out.println("initial tree done!");
	            //compute the points for the current globe radius
	            JoglPanel.globals.computeGrid( new_root);
	            //load also the textures to get to correct level of detail
	            int nRestOfTextures = Texture.loadRestOfTextures( this, new_root, new_level);
	            setNoTotalTextures( getNoTotalTextures()+nRestOfTextures);
	            if ( bDebug )
	                System.out.println("Total number of textures to load: "+getNoTotalTextures());
	            //I should wait for all textures to load
	            //for that, I shoul be notified
	            
	            //rehide panel, if hidden from start
	            if ( jpDownloadPanel!=null ) {
	                if ( jpDownloadPanel.isVisible()==false ) {
	                    bHiddenBefore = true;
	                    jpDownloadPanel.setVisible( true);
	                }
	            }
	            if ( jpbDownloadBar!=null ) {
	                jpbDownloadBar.setStringPainted(true);
	                jpbDownloadBar.setString(sKeyMode+" "+sResolutions[new_level]+" map");
	                jpbDownloadBar.setMinimum(0);
	                jpbDownloadBar.setMaximum(100);
	                jpbDownloadBar.setValue(0);
	            };
                if ( bDebug )
                    System.out.println("run thread JoGL - ChangeRootTexture");
                runMe();
                if ( bDebug )
                    System.out.println("end thread JoGL - ChangeRootTexture");
	        } catch ( Exception ex ) {
	            //who knows
	            System.out.println("Exception during run for ChangeRootTexture");
	            ex.printStackTrace();
	        }
    	}
    }
    
    /**
     * run in a new thread because of problems with deadlock in AWT<br>
     * waits as long as there are still textures to be loaded to be notified <br>
     * when notified about a new texture loaded, it updates the progress bar<br>
     * cycles indefinitelly until all textures are loaded<br>
     * if all textures were loaded, hide progress bar, if visible,<br>
     * swap root textures,<br>
     * and remove instance of this class, because as last texture was loaded, last reference to it
     * is lost.
     */
    private void runMe()
    {
        int percent=0;
        while (true) {
            synchronized ( newTexturesToLoad ) {
                if ( bDebug )
                    System.out.println("loaded so far: "+nLoadedSoFar+"/"+nTotal);
                if ( nLoadedSoFar<nTotal ) {
                    try {
                        newTexturesToLoad.wait();
                    } catch (InterruptedException ex) {
                    }
                    percent=(int)(100*nLoadedSoFar/nTotal);
                } else
                    break;
            };
            //update progress bar
            if ( jpbDownloadBar!=null )
                jpbDownloadBar.setValue(percent);
        };
        if ( bDebug )
            System.out.println("set new root texture");
        //first hide progressbar
        if ( jpDownloadPanel!=null ) {
            if ( bHiddenBefore )
                jpDownloadPanel.setVisible( false);
        }
        if ( jpbDownloadBar!=null ) {
            jpbDownloadBar.setStringPainted(false);
            jpbDownloadBar.setString("");
            jpbDownloadBar.setValue(0);
        };
        //change manually the zoom
        //Texture.zoomChanged(new_root, new_level);
        //then set new textures
        Texture.swapRoots(new_root, new_level, currentTime, new_treeid);
        Texture.checkVisibility();
        Texture.zoomChanged();
        JoglPanel.globals.canvas.repaint();
//        //this thread ends, the object vanishes, so remove reference to instance
//        synchronized (sync_crtInstance) {
//            thisInstance = null;
//        };
        //if loop mode is on, then create the update mechanism
        //only after this loading is done!
        //loose this reference and create a new one
        BackgroundWorker.schedule(new TimerTask() {
            public void run() {
                Thread.currentThread().setName("JoGL Loading new resolution level thread - set this level for shadow updating");
                //first stop the update timer, then start it again.
                Globals.stopUpdateShadowTimer();
                Globals.doUpdateShadowTimer();
            }
            
        }, 1000);//arbitrary time, has no relevance, it could have been now
    }
    
    /**
     * notifies that a new texture was set, so the progress bar should be updated
     *
     */
    public void notifyNewTextureSet()
    {
        synchronized ( newTexturesToLoad ) {
            nLoadedSoFar++;//increment number of loaded textures
            newTexturesToLoad.notify();
        };
    }
    
    /**
     * updates the progress bar if any to show that a new texture was loaded<br>
     * and, if all textures were loaded, swaps the global root with this root<br>
     * an so, it silently disappears...
     *
     */
//    private void newTextureSet()
//    {
//        //if last texture, set new root
//        if ( nLoadedSoFar>=nTotal ) {
//        }
//    }
    
    /**
     * sets usable GUI interface to show download statistics
     * @param jpb
     * @param jlb
     */
    public void setProgressBar( JProgressBar jpb, JComponent jmp)
    {
        jpbDownloadBar = jpb;
        jpDownloadPanel = jmp;
    }
    
    public void clearProgressBar() {
        jpbDownloadBar = null;
        jpDownloadPanel = null;
    }

    public int getNoTotalTextures() {
        return nTotal;
    }

    public void setNoTotalTextures(int totalTextures) {
        nTotal = totalTextures;
    }

    public int getKeyMode() {
        return nKeyMode;
    }

    public int getTreeID() {
        return new_treeid;
    }
}
