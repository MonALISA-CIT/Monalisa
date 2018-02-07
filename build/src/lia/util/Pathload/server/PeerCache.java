/**
 * Pathload Configuration Helper Classes
 */
package lia.util.Pathload.server;

import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This container holds data about active peers.
 * This algorithm works in rounds. A new host is not permitted to
 * be imediatly selected to be a next token host.
 * A <i>Token Host</i> is the farm that currently is permitted
 * to initiate a measurement. This token Host can be a forced
 * host, somebody may want to trigger a measurement or, it is
 * the oldest host that received the token.
 * The selection of the next <i>Destination Host</i> is made by
 * selecting the oldest unused farm. A farm is used if it is 
 * either a pathload sender or a receiver.
 * 
 *  A host may not be a receiver until MIN_WAITING_TIME milliseconds
 *  pass.
 *  
 *  A host is kicked out either when it's aging time surpasses
 *  MAX_AGING_TIME, either if MAX_DEAD_PEER_COUNT farms report
 *  this farm as inactive. 
 * 
 * @author heri
 *
 */

public class PeerCache implements XMLWritable {
	/**
	 * How much time in milliseconds must pass, before a host is
	 * allowed to receive the token again.
	 */
	public static long MIN_WAITING_TIME = 180 * 1000;
	
	/**
	 * How much time in milliseconds before the host will be
	 * kicked out of the cache. Each host must contact the
	 * cache in at least MAX_AGING_TIME / 1000 seconds.
	 */
	public static long MAX_AGING_TIME = 300 * 1000;
	
	/**
	 * How many dead peer reports to receive until a host is
	 * kicked out of the cache. 
	 */
	public static int MAX_DEAD_PEER_COUNT = 3;
	
	private Hashtable currentPeers;	
	private Vector queuedPeers;
	
	private ForcedPeerContainer forcedPeers;	
	
	private int count;
	
	private PathloadLogger log;
	
	/**
	 * Construct a new PeerCache. This structure is NOT synchronized,
	 * it must be synchronized externally.
	 *
	 */
	public PeerCache() {
		currentPeers = new Hashtable();
		queuedPeers = new Vector();
		forcedPeers = new ForcedPeerContainer();
		log = PathloadLogger.getInstance();
		count = 0;
	}
	
	/**
	 * Add a new peer to the cache.
	 * The host is added to a waiting queue until the cache is ready to
	 * insert it into the main cache.  lastRequestTime
	 * 
	 * @param p		PeerInfo fully describes the new host.
	 * @return		True if the operation succeded, false otherwise.
	 * 				A host will not be added twice.
	 */
	public boolean add(PeerInfo p) {
		if (p == null) return false;
		if ((queuedPeers.contains(p)) ||
				(currentPeers.containsKey(p))) {
			return false; 
		}
		
		queuedPeers.add(p);
		
		return true;
	}
	
	/**
	 * Remove a peer from the cache.
	 * 
	 * @param p		The Host to be removed
	 * @return		True if the operation succeded, false otherwise.
	 */
	public boolean remove(PeerInfo p) {				
		if (p == null) return false;
		
		log.log(Level.FINE, "Removing peer " + p.toString());		
		
		forcedPeers.remove(p);
		if (queuedPeers.contains(p)) {
			queuedPeers.remove(p);			
		} 
		if (currentPeers.containsKey(p)) {
			currentPeers.remove(p);
			count--;			
			for (Enumeration e = currentPeers.elements() ; e.hasMoreElements() ; ) {
				Peer peer = (Peer) e.nextElement();
				peer.removeDestPeer(p);
				if (!peer.hasMoreRemainingPeers()) {
					count--;
				}				
			}
		}		
		return true;
	}
	
	/**
	 * Check if the peer is already in the cache.
	 * 
	 * @param p		PeerInfo to check
	 * @return		True if it's in the cache, false otherwise.
	 */
	public boolean contains(PeerInfo p) {		
		if (p == null) return false;
		if ((queuedPeers.contains(p)) ||
				(currentPeers.containsKey(p))) {
			return true; 
		}		
		return false;
	}
	
	/**
	 * Refresh lastRequestTime attribute of the Peer
	 * with PeerInfo p from the cache.
	 * 
	 * @param p		PeerInfo of the accesed Peer
	 * @return		True if operation succeded, false otherwise.
	 */
	public boolean refresh(PeerInfo p) {
		boolean bResult = false;
		if (p == null) return false;
				
		Peer peer = (Peer) currentPeers.get(p);
		if (peer != null) {
			peer.setLastRequestTime(System.currentTimeMillis());
			bResult = true;
		}
		
		return bResult;
	}
	
	/**
	 * Get the next host that will hold the token.Src: [Farm3/10.0.0.3] Dst: [Farm2/10.0.0.2]
	 * If no hosts exist or if a measuring round has been
	 * finished, add new pending hosts and begin a new measuring round.
	 * 
	 * A measuring round is a time in which new hosts are added to
	 * a waiting queue while the others perfom measurements with each
	 * other in all possible combinations. A measuring round is over
	 * when all possible combinations are done.
	 *  
	 * The selection follows the next rules:
	 * - if <i>Forced peers</i> exists they will be the next
	 * - else select the oldest host, the host that didn't have the
	 * token. This host must meet the following reuirements: must be
	 * alive and must have not had the token from MIN_WAITING_TIME ago.
	 * 
	 * @return	The next Peer that will take the token
	 */
	public PeerInfo getNextSrcHost() {
		PeerInfo nextSrcHost = null;
		Peer p = null;
		Date date = new Date();
		long crtTime = date.getTime();
	
		if (!forcedPeers.isEmpty()) {			
			nextSrcHost = forcedPeers.getNextSrcHost();
		} else {
			if ((currentPeers.size() < 2) ||
					(count == 0)) {
				addNewHostsAndResetCounters();
			} else {
				Vector v = new Vector(currentPeers.values());
				Collections.sort(v, new AgingPolicy());	

				for (Iterator it = v.iterator(); it.hasNext() ; ) {
					p = (Peer) it.next();
					if (p.isAlive(crtTime)) {
						if ((p.hasMoreRemainingPeers()) && 
								(p.waitingTimePassed(crtTime))) {
							nextSrcHost = p.getPeerInfo();
							break ;
						}
					} else {
						log.log(Level.FINE, "Removing peer because of old age or unresponsive behavior.");
						remove(p.getPeerInfo());
					}					
				}
			}
		}
		
		return nextSrcHost;
	}
	
	/**
	 * Get the destination Host for my host.
	 * The rule of selecting the destination host is:
	 * - if there is a forcedPeer for nextSrcHost, select it
	 * - else select the most unused Peer with which my peer did
	 * not have a measurement yet.
	 * 
	 * @param nextSrcHost	The host that will run the measurement.
	 * @return				The host with with the measurement will be run.
	 * 						NULL in case of error.
	 */
	public PeerInfo getNextDestHost(PeerInfo nextSrcHost) {
		PeerInfo destPeerInfo;
		long now = System.currentTimeMillis();
		
		if (nextSrcHost == null) return null;		
		
		Peer destPeer = null; 
		Peer srcPeer = (Peer) currentPeers.get(nextSrcHost);
		if (!srcPeer.hasMoreRemainingPeers()) {
			return null;
		}
		if (!forcedPeers.isEmpty()) {
			destPeerInfo = forcedPeers.getNextDestHost(nextSrcHost);
			destPeer = (Peer) currentPeers.get(destPeerInfo);
		} else {						
			Vector v = new Vector(srcPeer.getRemainingHosts());
			Collections.sort(v, new InactivityPolicy());
			destPeer = (Peer) v.get(0);
			destPeerInfo = destPeer.getPeerInfo();
		}
		
		PeerInfo srcPeerInfo = srcPeer.getPeerInfo();			
		srcPeer.removeDestPeer(destPeerInfo);
		srcPeer.setLastTokenTime(now);
		srcPeer.setInactiveTime(now);
		destPeer.setInactiveTime(now);
		
		if (destPeer.hasRemainingHost(srcPeerInfo)) {
			forcedPeers.add(new PeerGroup(destPeerInfo,
					srcPeerInfo,
					srcPeerInfo.getFarmName()));
		}
		
		if (!srcPeer.hasMoreRemainingPeers()) {
			count --;
		}		
		
		return destPeerInfo;
	}
	
	/**
	 * This is called when a measurement round finished.
	 * New hosts are added from the waiting queue.
	 *
	 */	
	private void addNewHostsAndResetCounters() {
		String logMessage = "Participating peers: ";
		log.createRound();
		log.log(Level.INFO, "New round started at " + (new Date().toString()));
		
		for (Iterator it = queuedPeers.iterator(); it.hasNext(); ) {
			PeerInfo pi = (PeerInfo) it.next();
			currentPeers.put(pi, new Peer(pi));
		}
		queuedPeers.clear();
		count = currentPeers.size();
		if (count != 1) {
			for (Enumeration e = currentPeers.elements(); e.hasMoreElements() ;) {
				Peer peer = (Peer) e.nextElement();
				Hashtable remainingPeers = new Hashtable(currentPeers);			
				remainingPeers.remove(peer.getPeerInfo());
				peer.setRemainingPeers(remainingPeers);
				logMessage += peer.getFarmName() + " ";
			}
			
			log.log(Level.INFO, logMessage);			
		} else {
			count = 0;
		}
	}
	
	/**
	 * Cleans up peers that are reported dead or inactive
	 * If current peers list becomes empty or there is only one
	 * peer left and the waiting queue isn't empty,
	 * it triggeres the addition of queued peers and the start 
	 * of a new round.
	 *
	 * @return Vector with the Peers being removed
	 */
	public Vector cleanUpDeadPeers() {
		long now = System.currentTimeMillis();
		Vector removedPeers = new Vector();
		
		if (currentPeers != null) {
			for (Enumeration e = currentPeers.elements(); e.hasMoreElements(); ) {
				Peer p = (Peer) e.nextElement();
				if (!p.isAlive(now)) {
					removedPeers.add(p.getPeerInfo());
					log.log(Level.FINE, "Cleaning up peer " + p.toString());
				}
			}
			
			for (Iterator it = removedPeers.iterator(); it.hasNext() ; ) {
				PeerInfo p = (PeerInfo) it.next();
				remove(p);
			}
			
			if ((currentPeers.isEmpty() || currentPeers.size() == 1) 
					&& ((queuedPeers != null) && (!queuedPeers.isEmpty())) ) {
				addNewHostsAndResetCounters();
			}
		}
		
		return removedPeers;
	}
	
	
	/**
	 * Get current keySet
	 * 
	 * @return	current KeySet of the current peers
	 */
	public Set getCurrentPeersKeySet() {
		return currentPeers.keySet();
	}
	
	/** 
	 * Get a minimalistic string representation of the cache.
	 * 
	 * @see java.lang.Object#toString()
	 * @return	Cache to string.
	 */
	public String toString() {
		StringBuilder sb  = new StringBuilder();
		sb.append("PeerCache:\n");
		sb.append("Nr. of forced Peers:  " + forcedPeers.size() + "\n");
		sb.append("Nr. of queued Peers:  " + queuedPeers.size() + "\n");
		sb.append("Nr. of current Peers: " + currentPeers.size() + "\n");
		
		return sb.toString();
	}
	
	/**
	 * Get a full XML representation of the cache
	 * 
	 * @param document	The XML Document to with the element will be added.
	 * @return			XML element representing the cache
	 */
	public Element getXML(Document document) {
		Element peerCacheElement = document.createElement("peerCache");
		Element temp = document.createElement("count");
		temp.appendChild(document.createTextNode(
				"" + count));
		peerCacheElement.appendChild(temp);					
		
		temp = forcedPeers.getXML(document); 
		peerCacheElement.appendChild(temp);

		Element peers =  document.createElement("queuedPeers");
		for (Iterator it = queuedPeers.iterator(); it.hasNext(); ) {
			PeerInfo pi = (PeerInfo) it.next();
			temp = pi.getXML(document);
			peers.appendChild(temp);
		}
		peerCacheElement.appendChild(peers);
		
		peers = document.createElement("currentPeers");
		for (Enumeration e = currentPeers.elements();  e.hasMoreElements(); ) {
			Peer p = (Peer) e.nextElement();
			temp = p.getXML(document);
			peers.appendChild(temp);
		}
		peerCacheElement.appendChild(peers);

		return peerCacheElement;
	}	
}
