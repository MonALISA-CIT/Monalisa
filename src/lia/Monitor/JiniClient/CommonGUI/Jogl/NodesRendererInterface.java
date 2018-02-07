/*
 * Created on 12.06.2004 20:04:22
 * Filename: NodesRendererInterface.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/**
 * @author Luc
 *
 * NodesRendererInterface
 */
public interface NodesRendererInterface {
    /**
     * this function only draws the snapshot representation it has access to through snodes
     * @param gl
     * @param snodes
     * @param graphicalAttrs
     */
    public void drawNodes(GL2 gl, Object[] graphicalAttrs);

    /**
     * actualizes nodes representation in memory, and draws them by calling <code>drawNodes</code>
     * @param gl graphical context
     * @param snodes represents snapshot of nodes collection 
     * @param graphicalAttrs general graphical attributes for all nodes
     */
    public void computeNodes(GL2 gl, Object[] graphicalAttrs);

    /**
     * initalizes opengl structures used by this renderer
     * @param gl
     */
    public void initNodes(GL2 gl, Object[] graphicalAttrs);

    /**
     * draws all available links that are already computed
     * @param gl
     * @param graphicalAttrs
     */
    public void drawLinks(GL2 gl, Object[] graphicalAttrs);

    /**
     * compute graphical attributes for links.<br>
     * That means color, and, if object not initialized, the display list associated with the link
     * @param gl
     * @param graphicalAttrs
     */
    public void computeLinks(GL2 gl, Object[] graphicalAttrs);

    /**
     * gets a node's hashtable with its links<br>
     * the format of hashtable is a mistery, and depends on client
     * @param node
     * @return hashtable containing as key something, and as value something else, from which a link should
     * be obtained
     */
    public Hashtable getLinks(rcNode node);

    /**
     * interogates the renderer if a specific link is valid, given only his internal properties...
     * @param obj a type of link
     * @return boolean value to show the validity
     */
    public boolean isValidLink(Object link);

    /**
     * should a link be removed?
     * @param obj
     * @param hLinkAttrs link's attributes, not general, but for current renderer
     * @param localNodes nodes
     * @return boolean value to show the deadness...
     */
    public boolean isDeadLink(Object link, HashMap hLinkAttrs, HashMap localNodes);

    /**
     * gathers a list of objects that are positioned in a <i>radius</i> distance from vector starting at <i>vEyePosition</i>
     * with direction <i>vDirection</i>
     * @param vEyePosition
     * @param vDirection
     * @param alSelectedObjects
     * @return
     */
    public ArrayList getOtherSelectedObjects(VectorO vEyePosition, VectorO vDirection, float radius,
            ArrayList alSelectedObjects);

    /**
     * add name and description attribute for node, and also any neccessary attrs
     * @param n node
     * @param objAttrs hashmap with key as string, and value as text
     */
    public void fillSelectedNodeInfo(rcNode n, HashMap objAttrs);

    /**
     * add name and description attribute for link, and also any neccessary attrs
     * @param link
     * @param objAttrs hashmap with key as string, and value as text
     * @return returns false in case no attribute is provided, and some defaults should be provided by
     * framework, or true if other attributes are provided and should be used
     */
    public boolean fillSelectedLinkInfo(Object link, HashMap hLinkAttrs, HashMap objAttrs);
}
