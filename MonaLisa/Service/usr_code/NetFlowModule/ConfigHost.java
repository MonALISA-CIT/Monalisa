
/**
 * This class specifies a network address class (read from 
 * the NetFlow Module configuration file) and the type (Master 
 * or Node), also read from the configuration file of this module,
 * that will matter for calculating traffic in and out.
 * 
 * Master is only one in the configuration file:
 *      ------------
 * 		Cern
 *   	type: M
 *   	ipClass: 137.138.*.*
 *      ipClass: 192.91.*.*
 *      ------------
 * and means that trafficIn and trafficOut will be calculated for these address class. 
 * Note that here are two ip classes for the same entry, so two ConfigHost objects will
 * be crated based on this.
 * 
 * Nodes are the rest of the network address classes specified:
 *      ------------- 
 *  	Caltech 
 *		type: N
 *		ipClass: 131.215.*.*
 *      -------------
 * and means that the traffic in and out between the master node and this one will count
 * for calculating the total traffic.     
 * 
 */
public class ConfigHost {

	/**
	 * Ip address class.
	 * For example (an IP class of type B):
	 *   137.138.*.* 
	 */
	private String name; 
	
	/**
	 * type:
	 *  M - Master
	 *  N - Node
	 */
	private boolean isMaster;

	public ConfigHost(String name, boolean isMaster) {
		this.name = name;
		this.isMaster = isMaster;
	} // ConfigHost

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean getIsMaster() {
		return isMaster;
	}

	public void setIsMaster(boolean isMaster) {
		this.isMaster = isMaster;
	}

	public String toString() {
		return "" + name + " " + isMaster;
	} // toString

} // ConfigHost

