/**
 * 
 */
package lia.util.Pathload.server.servlet;

import java.util.Date;

import lia.util.Pathload.server.XMLWriter;
import lia.util.Pathload.server.manager.HistoryManager;
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
public class PathloadHistory extends ThreadedPage {

	/**
	 * Needed for serialization
	 */
	private static final long serialVersionUID = 468167048347018035L;

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
		HistoryManager hm = new HistoryManager();	
		pwOut.println(XMLWriter.getHTMLStringFromXMLDocument("MonalisaConfig.xsl", hm));
		bAuthOK = true;
	}
}
