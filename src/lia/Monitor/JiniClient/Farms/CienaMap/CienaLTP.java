package lia.Monitor.JiniClient.Farms.CienaMap;

import java.awt.Color;

/**
 * Represent the graphical properties of an OsrpLtp object 
 * @author cipsm
 *
 */
public class CienaLTP {

	public String id;
	
    /**
     * The OSRP node parameter specifies the node name or OSRP node
     * identifier of the OSRP node
     */
    public String osrpNode;
    
    /**
     * The OSRP CAC termination points parameter is a list of CAC termination points
     * that are aggregated to make up this OSRP link termination point. This list can
     * have zero to 20 local supporting TTPs (physical port) of OSRP CAC termination
     * points
     */
    public String[] osrpCtps;

    /**
     * The remote node parameter indicates the node name of the remote link (that is,
     * the corresponding link coming from the other node). This is valid only when the
     * hello state has reached the two way inside state.
     */
    public String rmtName;
    
    /**
     * The maximum bandwidth parameter indicates the maximum bandwidth available
     * on this OSRP link for the specific service class
     */
    public long maxBW;
    

    /**
     * The available bandwidth remaining parameter indicates the available bandwidth
     * on the interface for this specific service class
     */
    public long avlBW;
    
    /**
     * The priority parameter indicates the service priority level of the link
     */
    public long prio;

    /**
     * The hello state parameter indicates the state of Hello protocol
     */
    public String hState;

    
    public boolean dirty = false;
    
	public static final Color COLOR_IDLE = new Color(53, 73, 255);
	public static final Color COLOR_TRANSFERING = Color.RED;
    
	public CienaLTP(String id) {
		this.id = id;
	}
	
    public Color getStateColor() {
    	int r1 = COLOR_TRANSFERING.getRed();
    	int g1 = COLOR_TRANSFERING.getGreen();
    	int b1 = COLOR_TRANSFERING.getBlue();
    	int r2 = COLOR_IDLE.getRed();
    	int g2 = COLOR_IDLE.getGreen();
    	int b2 = COLOR_IDLE.getBlue();
    	int r = (int)(r1 + avlBW * (r2 - r1) / maxBW);
    	int g = (int)(g1 + avlBW * (g2 - g1) / maxBW);
    	int b = (int)(b1 + avlBW * (b2 - b1) / maxBW);
    	return new Color(r, g, b);
    }
    
} // end of class CienaLTP
