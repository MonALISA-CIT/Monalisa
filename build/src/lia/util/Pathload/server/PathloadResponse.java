/**
 * 
 */
package lia.util.Pathload.server;

import lia.util.Pathload.util.PathloadClient;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This is the response a client app will get if using
 * Pathload Connector Servlet.
 * 
 * Response is evidently in form of a String.
 * 
 * @author heri
 *
 */
public class PathloadResponse implements XMLWritable {	
	
	public final static String PATHLOAD_MESSAGE_VERSION = "2";
	
	private int status;
	private String version  = PathloadResponse.PATHLOAD_MESSAGE_VERSION;
	private String message;
	private String[] group;
	private Token token;
	
	/**
	 * Default Constructor
	 *
	 */
	public PathloadResponse() {
		this.status = PathloadClient.STATUS_FAILED;
		this.message = null;
		this.token = null;
		this.group = null;
	}
	
	/**
	 * Set response type: failed or success
	 * @param status	Response Flag
	 * 					True if successful, false otherwise
	 */
	public void addStatusMessage(boolean status) {
		if (!status) {
			this.status = PathloadClient.STATUS_FAILED; 
		} else {
			this.status = PathloadClient.STATUS_SUCCESS;
		}
	}
	
	/**
	 * Add a token information to the response
	 * @param t	The token to be attached
	 * @return	True, if added Token is valid (not null),
	 * 			False otherwise
	 */
	public boolean addToken(Token t) {
		if (t == null) return false;
		this.token = t;
		return true;
	}
	
	/**
	 * Ads an error message to the response
	 * 
	 * @param message	The reason of error.
	 */
	public void addError(String message) {
		this.message = message;
		this.status = PathloadClient.STATUS_FAILED;
	}
	
	/**
	 * Always send the group with wich the peer is making
	 * all the measurements
	 *
	 */
	public void addGroup(String[] group) {
		this.group = group;
	}
	
	/** 
	 * @see lia.util.Pathload.server.XMLWritable#getXML(org.w3c.dom.Document)
	 */
	public Element getXML(Document document) {		
		Element responseElement = document.createElement("PathloadResponse");
		responseElement.setAttribute("status", "" + status);		
		
		Element temp = null;
		if (token != null) {
			temp = token.getXML(document);
			responseElement.appendChild(temp);
		}
		
		if (message != null) {
			temp = document.createElement("message");
			temp.appendChild(document.createTextNode(
					"" + message));	
			responseElement.appendChild(temp);
		}
		
		return responseElement;
	}

	/** 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Ver: [" + version + "] ");
		sb.append("Status: [" + status + "] ");
		sb.append("Token: [");
		if (token != null) {
			String destFarm = token.getDestHost().getFarmName();
			if (destFarm.indexOf("|")>=0) {
				destFarm = "InvalidName";
			}
			String destIp = token.getDestHost().getIpAddress();
			String ID = token.getID();
			sb.append(destFarm + "|" + destIp + "|" + ID);
		}
		sb.append("] ");
		sb.append("Group: [");
		if ((group != null) && (group.length > 0)) {
			sb.append(group[0]);
			for (int i=1; i<group.length; i++) {
				sb.append(",");
				sb.append(group[i]);
			}			
		}
		sb.append("] ");
		sb.append("Msg: [");
		if (message != null) {
			sb.append(message);
		}
		sb.append("]");
		return sb.toString();
	}

	/**
	 * @return Returns the message.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message The message to set.
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * @return Returns the status.
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * @param status The status to set.
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * @return Returns the token.
	 */
	public Token getToken() {
		return token;
	}

	/**
	 * @param token The token to set.
	 */
	public void setToken(Token token) {
		this.token = token;
	}

}
