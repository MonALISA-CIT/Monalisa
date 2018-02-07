/**
 * 
 */
package lia.util.Pathload.server.manager;

import java.util.Iterator;
import java.util.Vector;

import lia.util.Pathload.server.PathloadLogger;
import lia.util.Pathload.server.PeerCache;
import lia.util.Pathload.server.Token;
import lia.util.Pathload.server.XMLWritable;
import lia.util.Pathload.server.servlet.PathloadVersionFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The SetupManager is allowed to make changes to
 * the configuration variables of PathloadStatus.
 * Theese include, token age, token holding time,
 * minimum time to wait before a host is allowed to 
 * perform another measurement.
 * 
 * SetupManager is not necessarily a single instance 
 * class. (Problem?)
 * 
 * @author heri
 *
 */
public class SetupManager implements XMLWritable {	
	
	private Vector messages;
	
	/**
	 * This class will probably disappear in later releases.
	 * SetupManager is currently responsible for changing the
	 * default parameters of the main servlet. In case of
	 * changes, it must report either success or error.
	 * The Message class is used for passing status messages
	 * back to the PathloadSetup servlet.
	 * (It usually does not belong here)
	 * 
	 * @author heri
	 *
	 */
	private class Message implements XMLWritable {
		public final static int MESSAGE_OK = 0;
		public final static int MESSAGE_ERROR = 1;
		
		private String type;
		private String message;
		
		/**
		 * Default constructor
		 * 
		 * @param type		Type may be MESSAGE_OK or MESSAGE_ERROR
		 * @param message	Reason of failure or success message
		 */
		protected Message(int type, String message) {
			switch (type) {
				case MESSAGE_OK: this.type = "ok"; break;
				case MESSAGE_ERROR: this.type = "error"; break;
				default: this.type = "error";
						
			}
			this.message = message;
		}

		/**
		 * XML Representation of the Message
		 */
		public Element getXML(Document document) {
			Element messageElement = document.createElement("Message");
			
			Element temp = document.createElement("type");
			temp.appendChild(document.createTextNode(type));
			messageElement.appendChild(temp);
			
			temp = document.createElement("message");
			temp.appendChild(document.createTextNode(message));
			messageElement.appendChild(temp);
			
			return messageElement;
		}
	}
	
	/**
	 * Default Constructor
	 *
	 */
	public SetupManager() {
		messages = new Vector();
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MinWaitingTime of peers before they are allowed to
	 * be active again.
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @return	The minimum time in milliseconds a host has
	 * 			to wait before he is able to be active again.
	 * 			(before he is allowed to aquire the token) 
	 */
	public long getMinWaitingTime() {
		return PeerCache.MIN_WAITING_TIME;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MinWaitingTime of peers before they are allowed to
	 * be active again.
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @param minWaitingTime	The minimum time in milliseconds 
	 * 			a host has to wait before he is able to be active again.
	 * 			(before he is allowed to aquire the token)
	 * @return	True if successful, false otherwise
	 */
	public boolean setMinWaitingTime(long minWaitingTime) {
		if (minWaitingTime < 0) return false;
		PeerCache.MIN_WAITING_TIME = minWaitingTime;
		return true;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxAgingTime of peers before they are booted out of the
	 * PeerCache. 
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @return	If a peer fails to refresh its status durig
	 * 			this time, it will kicked out.
	 */
	public long getMaxAgingTime() {
		return PeerCache.MAX_AGING_TIME;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxAgingTime of peers before they are booted out of the
	 * PeerCache. 
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @param maxAgingTime	The new maxAgingTime.
	 * @return	True if successful, false otherwise
	 */
	public boolean setMaxAgingTime(long maxAgingTime) {
		if (maxAgingTime < 0) return false;
		PeerCache.MAX_AGING_TIME = maxAgingTime;
		return true;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxDeadPeerCount of peers before they are booted out of the
	 * PeerCache. 
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @return	How many times a host may be reported dead by
	 * 			other peers before he is booted out.
	 */
	public int getMaxDeadPeerCount() {
		return PeerCache.MAX_DEAD_PEER_COUNT;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxDeadPeerCount of peers before they are booted out of the
	 * PeerCache. 
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @param 	maxDeadPeerCount
	 * @return	True if successful, false otherwise
	 */
	public boolean setMaxDeadPeerCount(int maxDeadPeerCount) {
		if (maxDeadPeerCount < 0) return false;
		PeerCache.MAX_DEAD_PEER_COUNT = maxDeadPeerCount;
		return true; 
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxTokenAgingTime, the time after which a token is
	 * declared as lost.
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @return	The time after which a token is
	 * 			declared as lost
	 */
	public long getMaxTokenAgingTime() {
		return Token.MAX_TOKEN_AGING_TIME;		
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * MaxTokenAgingTime, the time after which a token is
	 * declared as lost.int messageType, 
	 * This variable is a property of the PeerCache of the
	 * ConfigurationManager
	 * 
	 * @param maxTokenAgingTime	The time after which a token is
	 * 							declared as lost
	 * @return	True if successful, false otherwise
	 */
	public boolean setMaxTokenAgingTime(long maxTokenAgingTime) {
		if (maxTokenAgingTime < 0) return false;
		Token.MAX_TOKEN_AGING_TIME = maxTokenAgingTime;
		return true;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * LoggerCapacity, the amount of Message Rounds remembered 
	 * by the Logger. 
	 * This variable is a property of PathloadLogger class
	 *
	 * @return	How many rounds are held in memory before being deleted.
	 */
	public int  getLoggerCapacity() {
		return PathloadLogger.PATHLOADLOGGER_CAPACITY;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * LoggerCapacity, the amount of Message Rounds remembered 
	 * by the Logger. 
	 * This variable is a property of PathloadLogger class
	 *  
	 * @param loggerCapacity		The new loggerCapacity. Must be > 10.
	 * @return		True if successful, false otherwise
	 */
	public boolean setLoggerCapacity(int loggerCapacity) {
		if (loggerCapacity < 10) return false;
		PathloadLogger.PATHLOADLOGGER_CAPACITY = loggerCapacity; 
		return true;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * LoggerLevel, the amount of messages being displayed.
	 * Messages with a priority higher or equal to the 
	 * priority level will be displayed.
	 * See java.util.logging.Level
	 * 
	 * This variable is a property of PathloadLogger class
	 *  
	 * @return	The priorityLevel associeted with the logger.
	 */
	public int getLoggerLevel() {
		return PathloadLogger.PRIORITY_LEVEL;
	}
	
	/**
	 * SetupWrapper for the PeerCache. This sets the 
	 * LoggerLevel, the amount of messages being displayed.
	 * Messages with a priority higher or equal to the 
	 * priority level will be displayed.
	 * See java.util.logging.Level
	 *  
	 * @param loggerLevel		The new logging Level. See
	 * 							java.util.loggging.Level
	 * @return					Method always returns true.
	 */
	public boolean setLoggerLevel(int loggerLevel) {
		PathloadLogger.PRIORITY_LEVEL = loggerLevel;
		return true;
	}
	
	/**
	 * Set the minimum Pathload Communication message version
	 * acceptable. The Messages below this level will be 
	 * silently dropped.
	 * 
	 * @param minVersion	The minimum Version. Must be >=1
	 * @return				True if succeded, false otherwise.
	 */
	public boolean setPathloadMinVersion(int minVersion) {
		if (minVersion < 1) return false;
		
		PathloadVersionFilter.MINIMUM_VERSION = minVersion;
		return true;
	}
	
	/**
	 * Get the minimum accepted version of Pathload messages.
	 * 
	 * @return The minimum accepted version of Pathload messages.
	 */
	public int getPathloadMinVersion() {
		return PathloadVersionFilter.MINIMUM_VERSION;
	}
	
	/**
	 * This method will disappear from the 
	 * @param message
	 */
	public void addErrorMessage(String message) {
		if (message == null) return;
		Message m = new Message(Message.MESSAGE_ERROR, message);
		messages.add(m);
	}
	
	/**
	 * See addErrorMessage(String)
	 * 
	 * @param message
	 */
	public void addOkMessage(String message) {
		if (message == null) return;
		Message m = new Message(Message.MESSAGE_OK, message);
		messages.add(m);
	}

	/**
	 * XML representation of the configuration manager setup.
	 */
	public Element getXML(Document document) {
		Element setupManagerElement = document.createElement("SetupManager");
		
		Element temp = document.createElement("minWaitingTime");
		temp.appendChild(document.createTextNode("" + getMinWaitingTime()));
		setupManagerElement.appendChild(temp);

		temp = document.createElement("maxAgingTime");
		temp.appendChild(document.createTextNode("" + getMaxAgingTime()));
		setupManagerElement.appendChild(temp);
		
		temp = document.createElement("maxDeadPeerCount");
		temp.appendChild(document.createTextNode("" + getMaxDeadPeerCount()));
		setupManagerElement.appendChild(temp);
		
		temp = document.createElement("maxTokenAgingTime");
		temp.appendChild(document.createTextNode("" + getMaxTokenAgingTime()));
		setupManagerElement.appendChild(temp);
		
		temp = document.createElement("loggerCapacity");
		temp.appendChild(document.createTextNode("" + getLoggerCapacity()));
		setupManagerElement.appendChild(temp);
		
		temp = document.createElement("loggerLevel");
		temp.appendChild(document.createTextNode("" + getLoggerLevel()));
		setupManagerElement.appendChild(temp);
		
		for (Iterator it = messages.iterator(); it.hasNext(); ) {
			Message m = (Message) it.next();
			temp = m.getXML(document);
			setupManagerElement.appendChild(temp);
		}
		
		return setupManagerElement;
	}
}
