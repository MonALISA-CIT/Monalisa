/*
 * Created on Nov 6, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

/**
 *
 * Nov 6, 2004 - 2:39:10 PM
 */
public abstract class AbstractNodesRenderer implements NodesRendererInterface {

    public double NodeMinValue;
    public double NodeMaxValue;
    public Color NodeMinColor;
    public Color NodeMaxColor;

    public Hashtable OptionsPanelValues; //hashtable with values for each option in the panel
    //organized as key -> hashtable, where key is a property of an option, like "ping", "wan", "node"
    //and value is a hashtable containing type and object, where type is LimitValue, Color, Value..., and Object is Double, Color...

    public String subViewCapabilities[];

    public void changeSubView(int subView) {
    }

    public void drawNodes(GL2 gl, Object[] graphicalAttrs) {
    }

    public void computeNodes(GL2 gl, Object[] graphicalAttrs) {
    }

    public void initNodes(GL2 gl, Object[] graphicalAttrs) {
    }

    public void drawLinks(GL2 gl, Object[] graphicalAttrs) {
    }

    public void computeLinks(GL2 gl, Object[] graphicalAttrs) {
    }

    public Hashtable getLinks(rcNode node) {
        return null;
    }

    public boolean isValidLink(Object link) {
        return true;
    }

    public boolean isDeadLink(Object link, HashMap hLinkAttrs, HashMap localNodes) {
        return false;
    }

    public ArrayList getOtherSelectedObjects(VectorO vEyePosition, VectorO vDirection, float radius,
            ArrayList alSelectedObjects) {
        return alSelectedObjects;
    }

    public void fillSelectedNodeInfo(rcNode n, HashMap objAttrs) {
    }

    public boolean fillSelectedLinkInfo(Object link, HashMap hLinkAttrs, HashMap objAttrs) {
        return false;
    }
}
