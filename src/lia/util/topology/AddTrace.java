package lia.util.topology;

import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;

import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

public class AddTrace extends ThreadedPage {

	public AddTrace(){
		super();
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
		if(enp.hasMoreElements()){
			String p = (String) enp.nextElement();
		    //System.out.println("AddTrace:"+p);
			//pwOut.println("params="+p);
			IpClassifier ipClassifier = IpClassifier.getInstance();
			StringTokenizer stk = new StringTokenizer(p, " ");
			while(stk.hasMoreTokens()){
				String ip = stk.nextToken();
				//pwOut.println(ip);
				ipClassifier.addIP(ip);
			}
		}
		pwOut.println("OK");
		pwOut.flush();
		bAuthOK = true;
	}
}
