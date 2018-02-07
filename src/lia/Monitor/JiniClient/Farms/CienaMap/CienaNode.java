package lia.Monitor.JiniClient.Farms.CienaMap;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/**
 * A class used to graphical represent a Ciena OSRP Node
 * @author cipsm
 *
 */
public class CienaNode {

	/** Whether the node is movable or not */
	public boolean fixed;
	
	public boolean selected = false;

	public boolean isLayoutHandled = false;

	/** The graphical position of the node */
	public int x, y;
	
	public Rectangle limits ;
	
	/** The name of the unit */
	public String UnitName;
	
	public String osrpID;
	
	public String ipAddress;
	
	/** The connected predecessor */
	public CienaNode Predecessor;
	
	/** The LPTs map */
	public Map osrpLtpsMap;
	
	public boolean dirty = false;
	
	public CienaNode(String name) {
		this.UnitName = name;
		osrpLtpsMap = new HashMap();
	}
}
