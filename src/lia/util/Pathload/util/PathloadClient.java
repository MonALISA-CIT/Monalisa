/**
 * 
 */
package lia.util.Pathload.util;

import lia.util.Pathload.client.ServletResponse;



/**
 * Define actions a Pathload Client may perform
 *
 * PathloadResponse
 *	public final static int ACTION_GET_TOKEN = 0;
 *	public final static int ACTION_RELEASE_TOKEN = 1;
 *	public final static int ACTION_REFRESH = 2;
 *	public final static int ACTION_SHUTDOWN = 3;
 *	 
 * @author heri
 *
 */
public interface PathloadClient {
	
	public final static int STATUS_FAILED = 0;
	public final static int STATUS_SUCCESS = 1;	
	
	public final static int ACTION_UNKNOWN = -1;
	public final static int ACTION_GET_TOKEN = 0;
	public final static int ACTION_RELEASE_TOKEN = 1;
	public final static int ACTION_REFRESH = 2;
	public final static int ACTION_SHUTDOWN  = 3;
	public final static int ACTION_REQUESTREDO = 4;
	
	public final static String ACTION_NAME_UNKNOWN = "unknownAction";
	public final static String ACTION_NAME_GET_TOKEN = "getToken";
	public final static String ACTION_NAME_RELEASE_TOKEN = "releaseToken";
	public final static String ACTION_NAME_REFRESH = "refresh";
	public final static String ACTION_NAME_SHUTDOWN  = "shutdown";
	public final static String ACTION_NAME_REQUESTREDO = "redo";
	
	/**
	 * Try to aquire the Pathload Token.
	 * @return	The interpreted response of the servlet
	 * @throws PathloadException	If an error occurres on the servlet,
	 * 								an exception is thrown
	 */
	public ServletResponse getToken() throws PathloadException;
	
	/**
	 * Refresh your status in the PathloadConfig Servlet
	 * so u don't get booted out.
	 * @return	The interpreted response of the servlet
	 * @throws PathloadException	If an error occurres on the servlet,
	 * 								an exception is thrown 
	 */
	public ServletResponse refresh() throws PathloadException;
	
	/**
	 * Release the token after use.
	 * @param tokenID	The token to be released
	 * @return	True if succesfull, false otherwise 
	 * @throws PathloadException	If an error occurres on the servlet,
	 * 								an exception is thrown
	 */
	public boolean releaseToken(String tokenID) throws PathloadException;
	
	
	/**
	 * Shut the peer cleanly. Announce the pathload control service
	 * you will exit cleanly.
	 * @return						True if successfull, false otherwise
	 * @throws PathloadException	If an error occurres on the servlet,
	 * 								an exception is thrown
	 */
	public boolean shutdown() throws PathloadException;
	
	/**
	 * Request that the measurement should be made again.
	 * This will release the token. If accepted, a new token will be
	 * acquired with the same hosts.
	 * 
	 * @param tokenID
	 * @return
	 * @throws PathloadException
	 */
	public boolean redoMeasurement(String tokenID) throws PathloadException; 
}
