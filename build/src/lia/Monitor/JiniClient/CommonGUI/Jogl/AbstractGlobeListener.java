/*
 * Created on Jan 6, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

/**
 * offers a void implementation for each function/event of globe listener interface
 * so that an extension of this class could implement only the needed functions
 * Jan 6, 2005 - 2:09:09 PM
 */
public abstract class AbstractGlobeListener implements GlobeListener {

    public void radiusChanged() {}

    public void radiusChangeStart() {}

    public void radiusChangeFinish() {}

    public void mouseClick(float LONG, float LAT) {}

    public void mouseDblClick(float LONG, float LAT) {}

    public void mouseMove(int mouse_x, int mouse_y) {}

    public void optionPanelChanged(int event) {}

}
