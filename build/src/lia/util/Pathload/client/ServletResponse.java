/**
 * 
 */
package lia.util.Pathload.client;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the interpreted response of the pathload servlet.
 * 
 * @author heri
 *
 */
public class ServletResponse {
	
	private boolean status;
	private String version;
	private String ID;	
	private String destFarmName;
	private String destIp;
	private Vector myGroup;
	private boolean outOfSync;
	
	/**
	 * Default constructor
	 * 
	 * @param version
	 * @param status
	 * @param ID
	 * @param destFarmName
	 * @param destIp
	 * @param myGroup
	 */
	public ServletResponse(String version, String status, 
			String ID, String destFarmName, 
			String destIp, Vector myGroup) {
		this.version = version;
		if ((status == null) || (status.equals("0"))) {
			this.status = false;
		} else {
			this.status = true;
		}
		this.ID = ID;
		this.destFarmName = destFarmName;
		this.destIp = destIp;
		this.myGroup = myGroup;
		this.outOfSync = false;
	}
	
	/**
	 * Reads a ServletResponse from a input String.
	 * 
	 * @param inputStr	Input String from witch to decode the Token
	 * @return		The new Client Token or null if an error occured
	 */
	public static ServletResponse fromString(String inputStr) {
		if (inputStr == null) return null;
		
		ServletResponse ct = null;
		
		String sPattern = "Ver: \\[((.)*)\\] " +
			"Status: \\[((.)*)\\] " +
			"Token: \\[(((.)*)\\Q|\\E((.)*)\\Q|\\E((.)*))?\\] " +
			"Group: \\[((.)*)\\] " +
			"Msg: \\[((.)*)\\]";
		Pattern pattern = Pattern.compile(sPattern,
				Pattern.UNIX_LINES | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(inputStr);
		
		if (matcher.find()) {
			String version = matcher.group(2);			
			String status = matcher.group(4);			
			String destFarmName = matcher.group(6);
			String destIp = matcher.group(8);
            String ID = matcher.group(10);
            String groupStr = matcher.group(12);
            /**
             * String message = matcher.group(14);
             */
            String[] myGroups = null;
            Vector vec = new Vector();
            if (groupStr != null) {            	
            	myGroups = groupStr.split(",");
            	if (myGroups!=null) {
            		for (int i=0; i< myGroups.length; i++) {
            			vec.add(myGroups[i]);
            		}
            	}
            }
            
            ct = new ServletResponse(version, status, ID, destFarmName, destIp, vec);
		}
		
		return ct;
	}
	
	/**
	 * Does the response from the servlet say, i have the token?
	 * 
	 * @return	True if the token is in my posession, false otherwise
	 */
	public boolean hasToken() {
		return (ID != null)? true : false;
	}

	/**
	 * Does the reponse from the servlet tell me the other peers with
	 * who i measure.
	 * 
	 * @return	True if myGroup is present, false otherwise
	 */
	public boolean hasMyGroup() {
		return (myGroup != null)? true : false;
	}
	
	/**
	 * Return the status of the servlet request
	 * 
	 * @return	True for an ok response, false otherwise
	 */
	public boolean getStatus() {
		return status;
	}
	
	/**
	 * @return Returns the destFarmName.
	 */
	public String getDestFarmName() {
		return destFarmName;
	}

	/**
	 * @return Returns the destIp.
	 */
	public String getDestIp() {
		return destIp;
	}

	/**
	 * @return Returns the iD.
	 */
	public String getID() {
		return ID;
	}

	/**
	 * 
	 * @return Returns myGroup
	 */
	public Vector getMyGroup() {
		return myGroup;
	}

	/**
	 * 
	 * @return Returns the Version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @return Returns the outOfSync.
	 */
	public boolean isOutOfSync() {
		return outOfSync;
	}

	/**
	 * @param outOfSync The outOfSync to set.
	 */
	public void setOutOfSync(boolean outOfSync) {
		this.outOfSync = outOfSync;
	}
}
