package lia.util.topology;

import java.util.Date;
import java.util.Enumeration;

import javax.servlet.ServletException;

import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

public class GetIPids extends ThreadedPage {
    
    public void init() throws ServletException {
        super.init();
    }

	public void doInit() {
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("text/plain");
	}

	public void execGet() {
		Enumeration enp = request.getParameterNames();
		IpClassifier ipClassifier = IpClassifier.getInstance();
		if(enp.hasMoreElements()){
			String p = (String) enp.nextElement();
		    //System.out.println("GetIPids:"+p);
			pwOut.println(ipClassifier.getIPidsResponse(p));
		}
		pwOut.flush();
		bAuthOK = true;
	}
}
