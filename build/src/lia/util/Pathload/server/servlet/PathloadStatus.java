/**
 *  Pathload Configurations Servlets reside here.
 */
package lia.util.Pathload.server.servlet;

import java.util.Date;

import lia.util.Pathload.server.XMLWriter;
import lia.util.Pathload.server.manager.ConfigurationManager;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

/**
 * Transforms internal XML Status Data
 * into HTML
 *  
 * @author heri
 *
 */
public class PathloadStatus extends ThreadedPage {

	/**
	 * Needed for serialization
	 */
	private static final long serialVersionUID = 468167048717018035L;

	/** 
	 * @see lia.web.utils.ThreadedPage#doInit()
	 */
	public void doInit() {
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Refresh", "60");
		response.setContentType("text/html");
	}

	/** 
	 * @see lia.web.utils.ThreadedPage#execGet()
	 */
	public void execGet() {
		ConfigurationManager cm = ConfigurationManager.getInstance();
		pwOut.println(XMLWriter.getHTMLStringFromXMLDocument("MonalisaConfig.xsl", cm));
		
		bAuthOK = true;
	}

}
