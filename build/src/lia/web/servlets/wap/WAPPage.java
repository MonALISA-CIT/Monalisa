package lia.web.servlets.wap;

import java.util.Date;
import java.util.Random;

import javax.servlet.ServletContext;

import lia.web.utils.MailDate;
import lia.web.utils.Page;
import lia.web.utils.ThreadedPage;

/**
 * @author costing
 * 
 */
public abstract class WAPPage extends ThreadedPage {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5378601801101357187L;

	/**
	 * Res dir
	 */
	public String sResDir = "";

	/**
	 * Config dir
	 */
	public String sConfDir = "";

	/**
	 * Master page
	 */
	public Page pMaster;

	private static final Random r = new Random();

	/**
	 * @return random number
	 */
	public static synchronized long nextRand() {
		long l = r.nextLong();

		while (l < 100000) {
			l = r.nextLong();
		}

		return l;
	}


	@Override
	public void doInit() {
		ServletContext sc = getServletContext();

		sResDir = sc.getRealPath("/");
		if (!sResDir.endsWith("/"))
			sResDir += "/";

		sConfDir = sResDir + "WEB-INF/conf/";

		sResDir += "WEB-INF/res/";

		pMaster = new Page(osOut, sResDir + "masterpage/masterpage.res");

		response.setContentType("text/vnd.wap.wml");

		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date()))
				.toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
	}

}