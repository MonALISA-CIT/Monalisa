/**
 * 
 */
package lia.util.Pathload.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xbill.DNS.utils.base64;

/**
 * Because pathload is a very I/O intensive operation, 
 * only one measurement is allowed at a single time.
 * Each host requires a Token to get permission to perform
 * a pathload measurement.
 * 
 * The token is uniquely described by his ID.
 * A token may not be requested twice by the same owner.
 * 
 * @author heri
 *
 */
public class Token {
	
	/**
	 * Aging time in miliseconds before the 
	 * token dies
	 */
	public static long MAX_TOKEN_AGING_TIME = 300 * 1000; 
	
	private PeerInfo owner;
	private PeerInfo srcHost;
	private PeerInfo destHost;
	private String   ID;
	private boolean	 taken;
	private long	 lastAccessTime;
	
	/**
	 * Create a new Token, generate a new TokenID and set the lastAccessTime.
	 * 
	 * @param owner		Set the Owner of the token. Only the owner may aquire the
	 * 					token.
	 * @param srcHost	The host that will perform the measurement. This is usually
	 * 					the owner.
	 * @param destHost	The destination host with wich the srcHost will perform the
	 * 					measurement.
	 */
	public Token(PeerInfo owner, PeerInfo srcHost, PeerInfo destHost) {
		this.owner 	  = owner;
		this.srcHost  = srcHost;
		this.destHost = destHost;
		this.taken    = false;
		
		Date date = new Date();
		this.lastAccessTime = date.getTime();
		
		try {
			String input = destHost.toString() + date.toString();
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			byte[] result = md5.digest(input.getBytes());
			this.ID = base64.toString(result); 
		} catch (NullPointerException e) {
			this.ID = "" + lastAccessTime;
		} catch (NoSuchAlgorithmException e) {
			this.ID = "" + lastAccessTime;
		}		
	}
	
	public Token(String ID) {
		this.ID = ID;
		this.owner 	  = null;
		this.srcHost  = null;
		this.destHost = null;
		this.taken    = true;
	}
	
	/**
	 * Check if the token is Alive. A token is alive if 
	 * lastAccessTime + MAX_TOKEN_AGING_TIME > date
	 * LastAceessTime is set at the creation time of the 
	 * token and when the token is requested by its owner.
	 * 
	 * @param date		Current time as long.
	 * @return			True if token is Alive, false otherwise.
	 */
	public boolean isAlive(long date) {
		return lastAccessTime + MAX_TOKEN_AGING_TIME > date;
	}
	
	/**
	 * Check if requestor of token is it's owner. If true return
	 * token and update its lastAccessTime, otherwise return null.
	 * A token may not be requested twice.
	 * 
	 * @param p		PeerInfo of the requesting host
	 * @return		Token if requestor is owner, null otherwise
	 * 				or token has already been requested.
	 */
	public Token getToken(PeerInfo p) {
		Token token = null;
		
		if ((p == null) || 
				(taken == true)) {
			return null;
		}
		
		if (owner.equals(p)) {
			Date date = new Date();
			taken = true;
			lastAccessTime = date.getTime();
			token = this;
		}
		
		return token;
	}

	/**
	 * Get an XML Representation of the token
	 * 
	 * @param document	The main XML Document to attach itself to.
	 * @return			XML Token Element.
	 */
	public Element getXML(Document document) {		
		Element tokenElement = document.createElement("token");
		Element temp = document.createElement("owner");
		if (owner != null) {
			temp.appendChild(owner.getXML(document));
		} else {
			temp.appendChild(document.createTextNode(""));
		}
		tokenElement.appendChild(temp);
		
		temp = document.createElement("srcHost");
		if (srcHost != null) {
			temp.appendChild(srcHost.getXML(document));
		} else {
			temp.appendChild(document.createTextNode(""));
		}		
		tokenElement.appendChild(temp);
		
		temp = document.createElement("destHost");
		if (destHost != null) {
			temp.appendChild(destHost.getXML(document));
		} else {
			temp.appendChild(document.createTextNode(""));
		}		
		tokenElement.appendChild(temp);
		
		temp = document.createElement("ID");
		temp.appendChild(document.createTextNode(ID));
		tokenElement.appendChild(temp);

		temp = document.createElement("taken");
		temp.appendChild(document.createTextNode("" + taken));
		tokenElement.appendChild(temp);		
		
		temp = document.createElement("lastAccessTime");
		temp.appendChild(document.createTextNode("" + lastAccessTime));
		tokenElement.appendChild(temp);		
		
		return tokenElement;
	}	
	
	/**
	 * Two tokens are equal if their ID attribute is the same.
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj == null) return false;
		if (this == obj) return true;
		if ( !(obj instanceof Token)) {
			return false;
		}
		
		Token t = (Token) obj;				
		return ID.equals(t.getID());
	}

	/**
	 * Equals method has been overidden so we must also override
	 * hashCode()
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {		
		return ID.hashCode();
	}

	/**
	 * Get the Destination Host
	 * 
	 * @return Returns the destHost.
	 */
	public PeerInfo getDestHost() {
		return destHost;
	}

	/**
	 * Get the unique TokenID
	 * 
	 * @return Returns the iD.
	 */
	public String getID() {
		return ID;
	}

	/**
	 * Get the last Access Time.
	 * 
	 * @return Returns the lastAccessTime.
	 */
	public long getLastAccessTime() {
		return lastAccessTime;
	}

	/**
	 * Get the token owner.
	 * 
	 * @return Returns the owner.
	 */
	public PeerInfo getOwner() {
		return owner;
	}

	/**
	 * Get the source host.
	 * 
	 * @return Returns the srcHost.
	 */
	public PeerInfo getSrcHost() {
		return srcHost;
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {		
		return "Token " + ID + " from " 
			+ srcHost.toString() + " to " + destHost.toString(); 
	}

	/**
	 * @param id The iD to set.
	 */
	public void setID(String id) {
		ID = id;
	}	
}
