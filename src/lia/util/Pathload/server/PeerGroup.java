/**
 * 
 */
package lia.util.Pathload.server;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A peer group is two PeerInfo instances: srcHost and destHost
 * 
 * @author heri
 *
 */
public class PeerGroup {
	
	private PeerInfo src;
	private PeerInfo dest;
	private String forcedBy;
	
	public PeerGroup(PeerInfo src, PeerInfo dest, String forcedBy) {
		this.src = src;
		this.dest = dest;
		this.forcedBy = forcedBy;
	}

	/**
	 * @return Returns the dest.
	 */
	public PeerInfo getDest() {
		return dest;
	}

	/**
	 * @param dest The dest to set.
	 */
	public void setDest(PeerInfo dest) {
		this.dest = dest;
	}

	/**
	 * @return Returns the src.
	 */
	public PeerInfo getSrc() {
		return src;
	}

	/**
	 * @param src The src to set.
	 */
	public void setSrc(PeerInfo src) {
		this.src = src;
	}

	/**
	 * @return Returns the forcedBy.
	 */
	public String getForcedBy() {
		return forcedBy;
	}

	/**
	 * @param forcedBy The forcedBy to set.
	 */
	public void setForcedBy(String forcedBy) {
		this.forcedBy = forcedBy;
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		
		PeerGroup p = (PeerGroup) obj;
		
		return (src.equals(p.getSrc()) &&
			   dest.equals(p.getDest()));
	}

	/** 
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return toString().hashCode();
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {		
		return "Force from: " + src.toString() + " to " + dest.toString();
	}	
	
	public Element getXML(Document document) {
		Element peerGroupElement = document.createElement("peerGroup");
		Element temp = document.createElement("src");
		temp.appendChild(src.getXML(document));
		peerGroupElement.appendChild(temp);

		temp = document.createElement("dest");
		temp.appendChild(dest.getXML(document));
		peerGroupElement.appendChild(temp);		
		
		temp = document.createElement("forcedBy");
		temp.appendChild(document.createTextNode(
				"" + forcedBy));
		peerGroupElement.appendChild(temp);		
		
		return peerGroupElement;
	}
	
}
