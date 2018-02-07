/**
 * 
 */
package lia.util.Pathload.server;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A Peer is the runtime information of a host.
 * A Peer is comprised by data about the host (PeerInfo)
 * and runtime data like lastRequestTime, lastTokenTime, 
 * and inactiveTime, as well as a collection that lists all
 * the peers with with my peer is interested to perform a
 * measurement. (remainingPeers).
 * 
 * - lastRequestTime: when did the Peer last request a token
 * - lastTokenTime: when did the Peer acquire the last time
 * - inactiveTime: when was the Peer last used as a sender 
 * 					or receiver 
 * 
 * @author heri
 *
 */
public class Peer {
	private PeerInfo	peerInfo;
	private long  		lastRequestTime;
	private long 		lastTokenTime;
	private long		inactiveTime;
	private Hashtable	remainingPeers;
	
	public Peer(PeerInfo peerInfo) {
		long now = System.currentTimeMillis();
		
		this.peerInfo = peerInfo;
		this.lastRequestTime = now;
		this.lastTokenTime = now;
		this.inactiveTime = now;
		this.remainingPeers = null;
	}
	
	public Peer (PeerInfo peerInfo, long lastRequestTime, long lastTokenTime,
			long inactiveTime, Hashtable remainingPeers) {
		this.peerInfo = peerInfo;
		this.lastRequestTime = lastRequestTime;
		this.lastTokenTime = lastTokenTime;
		this.inactiveTime = inactiveTime;
		this.remainingPeers = remainingPeers;
	}
		
	protected boolean removeDestPeer(PeerInfo p) {
		boolean bResult = false;
		
		if ((p == null) || (remainingPeers == null)) {
			return false;
		}
		if (remainingPeers.containsKey(p)) {
			remainingPeers.remove(p);
			bResult = true;
		}
		
		return bResult;
	}	
	
	protected boolean isAlive(long currentTime) {
		if (currentTime - lastRequestTime > PeerCache.MAX_AGING_TIME) {
			return false;
		}
		return true;
	}
	
	protected boolean waitingTimePassed(long currentTime) {
		if (currentTime - inactiveTime < PeerCache.MIN_WAITING_TIME) {
			return false;
		}
		return true;
	}
	
	protected boolean hasMoreRemainingPeers() {
		return !remainingPeers.isEmpty();
	}
	
	protected Collection getRemainingHosts() {
		return remainingPeers.values();
	}
	
	protected boolean hasRemainingHost(PeerInfo p) {
		return remainingPeers.containsKey(p);
	}
	
	public String getFarmName() {
		if (peerInfo == null) return null;
		return peerInfo.getFarmName();
	}
	
	/**
	 * Get the last time the peer contacted this service.
	 * The date is usually found out by typing 
	 * <code>new java.util.Date(peer.getLastRequestTime())</code>
	 * 
	 * @return Returns the lastRequestTime.
	 */
	public long getLastRequestTime() {
		return lastRequestTime;
	}
	
	/**
	 * Set the last request time of the peer. 
	 * The Last Request Time is when the PeerInfo last contacted this
	 * service.
	 * 
	 * @param lastRequestTime The lastRequestTime to set.
	 */
	public void setLastRequestTime(long lastRequestTime) {
		this.lastRequestTime = lastRequestTime;
	}
	
	/**
	 * Get the last time when this peer had the Token
	 * 
	 * @return Returns the lastTokenTime.
	 */
	public long getLastTokenTime() {
		return lastTokenTime;
	}
	
	/**
	 * Set the last time when this peer had the Token
	 * 
	 * @param lastTokenTime The lastTokenTime to set.
	 */
	public void setLastTokenTime(long lastTokenTime) {
		this.lastTokenTime = lastTokenTime;
	}

	/**
	 * @return Returns the inactiveTime.
	 */
	public long getInactiveTime() {
		return inactiveTime;
	}

	/**
	 * @param inactiveTime The inactiveTime to set.
	 */
	public void setInactiveTime(long inactiveTime) {
		this.inactiveTime = inactiveTime;
	}

	/**
	 * @return Returns the peer.
	 */
	public PeerInfo getPeerInfo() {
		return peerInfo;
	}

	/**
	 * @param peerInfo The peer to set.
	 */
	public void setPeerInfo(PeerInfo peerInfo) {
		this.peerInfo = peerInfo;
	}

	/**
	 * @return Returns the remainingPeers.
	 */
	public Hashtable getRemainingPeers() {
		return remainingPeers;
	}

	/**
	 * @param remainingPeers The remainingPeers to set.
	 */
	public void setRemainingPeers(Hashtable remainingPeers) {
		this.remainingPeers = remainingPeers;
	}

	/** 
	 * Two peers are equal if all the data is the same.
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object peer) {
		if ( this == peer ) return true;
		if ( !(peer instanceof Peer))
			return false;
				
		Peer p = (Peer) peer;
		return this.inactiveTime == p.getInactiveTime() &&
			this.lastRequestTime == p.getLastRequestTime() &&
			this.lastTokenTime == p.getLastTokenTime() &&
			this.peerInfo.equals(p.getPeerInfo());
	}

	/** 
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {		
		return this.toString().hashCode();
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuilder sb  = new StringBuilder();
		sb.append("Peer: ");
		sb.append(peerInfo.toString());
		sb.append(" lastRequestTime: " + lastRequestTime);
		sb.append(" lastTokenTime: "   + lastTokenTime);
		sb.append(" inactiveTime: "    + inactiveTime);
		
		return sb.toString();
	}
	
	public Element getXML(Document document) {
		Element peerElement = document.createElement("peer");
		Element temp = peerInfo.getXML(document);
		peerElement.appendChild(temp);
		long now = System.currentTimeMillis();
		
		temp = document.createElement("reamainigPeers");
		if (remainingPeers != null) {
			for (Enumeration e = remainingPeers.elements(); e.hasMoreElements(); ) {
				Peer p = (Peer) e.nextElement();
				Element shortPeerInfo = document.createElement("peerName");
				shortPeerInfo.appendChild(document.createTextNode(
						p.getFarmName()));
				temp.appendChild(shortPeerInfo);
			}
		}
		peerElement.appendChild(temp);
		
		temp = document.createElement("lastRequestTime");
		temp.appendChild(document.createTextNode(
				"" + ((now - lastRequestTime)/1000)));
		peerElement.appendChild(temp);
		
		temp = document.createElement("lastTokenTime");
		temp.appendChild(document.createTextNode(
				"" + ((now - lastTokenTime)/1000)));
		peerElement.appendChild(temp);
		
		temp = document.createElement("inactiveTime");
		temp.appendChild(document.createTextNode(
				"" + ((now - inactiveTime)/1000)));
		peerElement.appendChild(temp);
		
		return peerElement;
	}
	
}
