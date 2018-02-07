package lia.Monitor.monitor;

/**
 * This class encapsulates an AppControl message, both Client -> Service and Service -> Client. This message is wrapped in a monMessage (or MonMessageClientsProxy) as the result
 * field. The ident field contains the session ID (a lia.util.UUID) in the secured communication between Client <-> Service.
 */
public class AppControlMessage implements java.io.Serializable {

	private static final long serialVersionUID = 937418306427547616L;

	// types of AppControl messages
	public static final String APP_CONTROL_MSG_AUTH_START = "app_ctrl_auth_start";
	public static final String APP_CONTROL_MSG_AUTH = "app_ctrl_auth";
	public static final String APP_CONTROL_MSG_AUTH_FINISHED = "app_ctrl_auth_finished";
	public static final String APP_CONTROL_MSG_AUTH_RETRY = "app_ctrl_auth_retry";
	public static final String APP_CONTROL_MSG_CMD = "app_ctrl_cmd";
	public static final String APP_CONTROL_MSG_ERR = "app_ctrl_err";
	public static final String APP_CONTROL_MSG_END_SESSION = "app_ctrl_end_session";
	public static final String APP_CONTROL_MSG_PROXY_ERR = "app_ctrl_proxy_err";

	// empty payload
	public static final byte[] EMPTY_PAYLOAD = new byte[0];

	/**
	 * Each command has an unique ID, per client, generated on the client (initiator) side. This message refers to the command with this ID.
	 */
	public Long cmdID;

	/** The effective AppControl message. */
	public String msg;

	/** Optional user data */
	public Object params;

	/**
	 * If the command has several replies from the service, it can increase this number for each message it sends. If it's <0, it means that this is the last response for this
	 * command and the cmdID can be discarded from internal hashes that match a command with its corresponding client.
	 */
	public int seqNr;

	/** Create a new AppControl message specifying the basic parameters */
	public AppControlMessage(Long cmdID, String msg) {
		this(cmdID, msg, null, -1);
	}

	/** Create a new AppControl message specifying all parameters */
	public AppControlMessage(Long cmdID, String msg, Object params, int seqNr) {
		this.cmdID = cmdID;
		this.msg = msg;
		this.params = params;
		this.seqNr = seqNr;
	}

	public String toString() {
		return "cmdID:" + cmdID + " msg:" + msg + " params:" + params + " seqNr:" + seqNr;
	}

}
