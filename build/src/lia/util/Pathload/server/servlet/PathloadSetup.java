/**
 * 
 */
package lia.util.Pathload.server.servlet;

import java.util.Date;
import java.util.logging.Level;

import lia.util.Pathload.server.XMLWriter;
import lia.util.Pathload.server.manager.SetupManager;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

/**
 * This servlet handles the configuration of the ConfigurationManager :)
 * General setup variables are set here. Checking of the required 
 * parameters is done here, the actual change and error reporting is
 * done by the SetupManager Class.
 * (CODE REVIEW NEEDED)
 * 
 * @author heri
 *
 */
public class PathloadSetup extends ThreadedPage {

	/**
	 * Needed for serialization
	 */
	private static final long serialVersionUID = 468167048717018035L;

	/** 
	 * @see lia.web.utils.ThreadedPage#doInit()
	 */
	public void doInit() {
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setContentType("text/html");
	}

	/** 
	 * Parse parameters if available, make changes and print out results.
	 * 
	 * @see lia.web.utils.ThreadedPage#execGet()
	 */
	public void execGet() {
		SetupManager sm = new SetupManager();
		if (haveParametersToProcess()) {
			String minWaitingTime = request.getParameter("minWaitingTime");
			String maxAgingTime = request.getParameter("maxAgingTime");
			String maxDeadPeerCount = request.getParameter("maxDeadPeerCount");
			String maxTokenAgingTime = request.getParameter("maxTokenAgingTime");
			String loggerCapacity = request.getParameter("loggerCapacity");
			String loggerLevel = request.getParameter("loggerLevel");
			
			setMinWaitingTime(sm, minWaitingTime);
			setMaxAgingTime(sm, maxAgingTime);
			setMaxDeadPeerCount(sm, maxDeadPeerCount);
			setMaxTokenAgingTime(sm, maxTokenAgingTime);
			setLoggerCapacity(sm, loggerCapacity);
			setLoggerLevel(sm, loggerLevel);
		}		
		
		pwOut.println(XMLWriter.getHTMLStringFromXMLDocument("MonalisaConfig.xsl", sm));
		
		bAuthOK = true;
	}
	
	/**
	 * Check if request has parameters to commit. (IsPostBack)
	 * 
	 * @return	True if parameters are available
	 */
	private boolean haveParametersToProcess(){
		return !request.getParameterMap().isEmpty(); 
	}
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param minWaitingTime		Spending time before a peer is allowed to be
	 * 								active again. (To be able to aquire a token)
	 */
	private void setMinWaitingTime(SetupManager sm, String minWaitingTime) {
		try {
			if (minWaitingTime == null) throw new NullPointerException();
			long lminWaitingTime = Long.parseLong(minWaitingTime);
			lminWaitingTime = lminWaitingTime * 1000;
			if (lminWaitingTime < 0) throw new IllegalArgumentException();
			sm.setMinWaitingTime(lminWaitingTime);
			sm.addOkMessage("minWaitingTime set successfully.");
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter minWaitingTime in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter minWaitingTime in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter minWaitingTime in a negative number and is not accepted.");
		}		
	}
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param maxAgingTime			Maximum time a peer is allowed in the cache 
	 * 								without refreshing its status before he is 
	 * 								kicked out
	 */
	private void setMaxAgingTime(SetupManager sm, String maxAgingTime) {
		try {
			if (maxAgingTime == null) throw new NullPointerException();
			long lmaxAgingTime = Long.parseLong(maxAgingTime);
			lmaxAgingTime = lmaxAgingTime * 1000;
			if (lmaxAgingTime < 0) throw new IllegalArgumentException();
			sm.setMaxAgingTime(lmaxAgingTime);
			sm.addOkMessage("maxAgingTime set successfully.");
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter maxAgingTime in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter maxAgingTime in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter maxAgingTime in a negative number and is not accepted.");
		}		
	}
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param maxDeadPeerCount		Maximum number of dead peer reports before a peer
	 * 								is kicked out of the cache.
	 */
	private void setMaxDeadPeerCount(SetupManager sm, String maxDeadPeerCount) {
		try {
			if (maxDeadPeerCount == null) throw new NullPointerException();
			int imaxDeadPeerCount = Integer.parseInt(maxDeadPeerCount);
			if (imaxDeadPeerCount < 0) throw new IllegalArgumentException();
			sm.setMaxDeadPeerCount(imaxDeadPeerCount);
			sm.addOkMessage("maxDeadPeerCount set successfully.");
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter maxDeadPeerCount in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter maxDeadPeerCount in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter maxDeadPeerCount in a negative number and is not accepted.");
		}		
	}
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param maxTokenAgingTime		Maximum Time allowed for a token to live
	 */
	private void setMaxTokenAgingTime(SetupManager sm, String maxTokenAgingTime) {
		try {
			if (maxTokenAgingTime == null) throw new NullPointerException();
			long lmaxTokenAgingTime = Long.parseLong(maxTokenAgingTime);
			lmaxTokenAgingTime = lmaxTokenAgingTime * 1000;
			if (lmaxTokenAgingTime < 0) throw new IllegalArgumentException();
			sm.setMaxTokenAgingTime(lmaxTokenAgingTime);
			sm.addOkMessage("maxTokenAgingTime set successfully.");
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter maxTokenAgingTime in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter maxTokenAgingTime in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter maxTokenAgingTime in a negative number and is not accepted.");
		}		
	}
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param loggerCapacity		Maximum number of rounds remembered by the logger.
	 */
	private void setLoggerCapacity(SetupManager sm, String loggerCapacity) {
		try {
			if (loggerCapacity == null) throw new NullPointerException();
			int iloggerCapacity = Integer.parseInt(loggerCapacity);
			if (iloggerCapacity < 10) throw new IllegalArgumentException();
			sm.setLoggerCapacity(iloggerCapacity);
			sm.addOkMessage("loggerCapacity set successfully.");
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter loggerCapacity in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter loggerCapacity in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter loggerCapacity in less than 10.");
		}		
	}		
	
	/**
	 * (Will be deprecated) Check parameters and make changes.
	 * Report error and success message. 
	 * 
	 * @param sm					SetupManager that will actually perform the
	 * 								parameter conversion and change the needed value
	 * @param loggerLevel			PathloadLogger logging level.
	 */
	private void setLoggerLevel(SetupManager sm, String loggerLevel) {
		try {
			if (loggerLevel == null) throw new NullPointerException();
			Level level = Level.parse(loggerLevel);
			int ilevel = level.intValue();
			sm.setLoggerLevel(ilevel);
			sm.addOkMessage("loggerLevel set successfully.");			
		} catch (NullPointerException e) {
			sm.addErrorMessage("Could not parse parameter loggerLevel in POST Message response.");
		} catch (NumberFormatException e) {
			sm.addErrorMessage("Parameter loggerLevel in not a number.");
		} catch (IllegalArgumentException e) {
			sm.addErrorMessage("Parameter loggerLevel in not recognized");
		}		
	}	
}
