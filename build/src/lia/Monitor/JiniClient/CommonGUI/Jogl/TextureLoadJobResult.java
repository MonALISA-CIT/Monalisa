/*
 * Created on Sep 20, 2005 by Lucian Musat
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;
import lia.util.dataStruct.DoubleLinkedListNode;

/**
 * returns results and input parameters for submited job<br>
 * Sep 20, 2005 - 6:28:31 PM
 */
public class TextureLoadJobResult extends DoubleLinkedListNode {
    /**
     * variables needed for first part of loading
     */
    /** texture for whom the loading is done */
    public Texture texture;
    /** is relative, has no extension (.oct should be added); used in textureData hash */
    public String path;
    /** level the texture is drawn at, for backward compatibility in the case that different levels have different dimmensions... */
    public int level;
    /** change root texture monitoring object that can be null if the loading is done for a texture in the current active tree */
    public ChangeRootTexture crtMonitor;
    /** tree id that this texture belong to */
    public int treeID;
    /**
     * variables set after loading
     */
    /** bytes array read from the image */
    public byte [] data = null;
    /** indicates if this texture is at max level, so there are no child images left */
    boolean isMaxLevel = false;
    /** value that indicates where from the texture was loaded: disc (val=0) or memory (val=1)*/
    public int loadedFrom;
    /** error reason of result for loading a map */
    public int errReason=0;
    public static final int ERR_ISDOWNLOADING=1;
    
    /**
     * variables set after passing the texture to jogl.
     * after that operation, data is null again.
     */
    /** texture id as jogl has assigned it */
    public int joglID;
    
    public String getResultID()
    {
        String id;
        id = path;
        if ( path.endsWith(".oct") )
            id = path.substring(0, path.length()-4);
        return treeID+"#"+id;
    }
}
