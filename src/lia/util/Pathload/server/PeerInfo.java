/**
 * 
 */
package lia.util.Pathload.server;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This fully describes a Pathload Host: hostname, ipAddress,
 * farmName, farmGroups.
 * A PeerInfo is uniquely described by ipAddress and farmName 
 * 
 * @author heri
 *
 */
public class PeerInfo {	
	private String 		hostname;
	private String 		ipAddress;
	private String 		farmName;
	private String[] 	farmGroups;	
	
	/**
	 * Default constructor
	 * 
	 * @param hostname		Peer hostname
	 * @param ipAddress		Accessible ipAddress
	 * @param farmName		Peer farm Name
	 * @param farmGroups	Peer groups
	 */
	public PeerInfo(String hostname, String ipAddress, 
			String farmName, String[] farmGroups) {
		this.hostname 	= hostname;
		this.ipAddress 	= ipAddress;
		this.farmName	= farmName;
		this.farmGroups	= farmGroups;		
	}
	
	/**
	 * Get the farm Groups of which the PeerInfo is part. A PeerInfo can be in multiple
	 * farmGroups at the same time.
	 * 
	 * @return Returns the farmGroups.
	 */	
	public String[] getFarmGroups() {
		return farmGroups;
	}
	
	/**
	 * Get the farm Groups of which the PeerInfo is part. A PeerInfo can be in multiple
	 * farmGroups at the same time.
	 * 
	 * @param farmGroups The farmGroups to set.
	 */	
	public void setFarmGroups(String[] farmGroups) {
		this.farmGroups = farmGroups;
	}
	
	/**
	 * Get the PeerInfo's Farm Name
	 * 
	 * @return Returns the farmName.
	 */	
	public String getFarmName() {
		return farmName;
	}
	
	/**
	 * Set the PeerInfo's Farm Name
	 * 
	 * @param farmName The farmName to set.
	 */	
	public void setFarmName(String farmName) {
		this.farmName = farmName;
	}
	
	/**
	 * Get the hostname (if reverse DNS is possible of the PeerInfo
	 * 
	 * @return Returns the hostname.(non-Javadoc)
	 */
	public String getHostname() {
		return hostname;
	}
	
	/**
	 * Set the hostname (if reverse DNS is possible of the PeerInfo
	 * 
	 * @param hostname The hostname to set.
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	/**
	 * Get the PeerInfo's ipAddress
	 * 
	 * @return Returns the ipAddress.
	 */
	public String getIpAddress() {
		return ipAddress;
	}
	
	/**
	 * Set the PeerInfo's ipAddress
	 * 
	 * @param ipAddress The ipAddress to set.
	 */
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	
	/** 
	 * Two are equal if they share the same ip and the same
	 * farmName
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object peerInfo) {
		if (this == peerInfo) return true;
		if ( !(peerInfo instanceof PeerInfo)) 
			return false;
		
		PeerInfo p = (PeerInfo) peerInfo;		
		return this.ipAddress.equals(p.getIpAddress()) &&
			this.farmName.equals(p.getFarmName());
	}

	/** 
	 * Overriding equals means overriding hashCode also.
	 * TODO: Verify correct hash-code creation
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {			
		return "[" + farmName + "/" + ipAddress + "]";
	}
	
	public Element getXML(Document document) {
		Element peerElement = document.createElement("peerInfo");
		
		Element temp = document.createElement("hostname");
		temp.appendChild(document.createTextNode(hostname));
		peerElement.appendChild(temp);
		
		temp = document.createElement("ipAddress");
		temp.appendChild(document.createTextNode(ipAddress));
		peerElement.appendChild(temp);
		
		temp = document.createElement("farmName");
		temp.appendChild(document.createTextNode(farmName));
		peerElement.appendChild(temp);

		Element groups = document.createElement("farmGroups");
		if (farmGroups != null) {
			for (int i=0; i<farmGroups.length; i++) {
				temp = document.createElement("group");
				temp.appendChild(document.createTextNode(farmGroups[i]));
				groups.appendChild(temp);			
			}
		}
		peerElement.appendChild(groups);
		
		return peerElement;
	}	
}
