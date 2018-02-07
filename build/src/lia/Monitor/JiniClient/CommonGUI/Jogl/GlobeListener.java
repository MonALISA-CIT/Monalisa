/*
 * Created on 13.06.2004 18:04:24
 * Filename: GlobeListener.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

/**
 * @author Luc<br>
 * <br>
 * GlobeListener<br>
 * offers a mechanism for other classes to be able to react when an action is
 * taking place on the globe due to user interaction
 */
public interface GlobeListener 
{
	public void radiusChanged();
	
	public void radiusChangeStart();
	
	public void radiusChangeFinish();
	
	/**
	 * treat mouse click on globe
	 *
	 * @param LONG longitude on globe where user clicked
	 * @param LAT latitude on globe where user clicked
	 */
	public void mouseClick(float LONG, float LAT);

	public void mouseDblClick(float LONG, float LAT);
	
	public void mouseMove( int mouse_x, int mouse_y);//float LONG, float LAT);
	
	/**
	 * an option in options panel has changed so something should be recomputed
	 *
	 */
	public void optionPanelChanged(int event);
}
