package lia.web.servlets.map2d;

import javax.servlet.ServletContext;

import lia.web.servlets.web.Utils;
import lia.web.servlets.web.display;
import lia.web.utils.CacheServlet;
import lia.web.utils.Page;

/**
 * Servlet to display a static html
 * 
 * @author alexc
 * @since forever
 */
public class show extends CacheServlet {
	private static final long	serialVersionUID	= 1L;
	
	private String sResDir = "";
    private String sConfDir = "";

    private transient Page pMaster = null;

	public void doInit() {
		ServletContext sc = getServletContext();

        sResDir = sc.getRealPath("/");
        if (!sResDir.endsWith("/"))
            sResDir += "/";

        sConfDir = sResDir + "WEB-INF/conf/";

        sResDir += "WEB-INF/res/";
	
		String sExtraResPath = gets("res_path");
	
		if (
	    sExtraResPath.length()>0 && 
	    !sExtraResPath.startsWith(".") && 
	    !sExtraResPath.startsWith("/") && 
	    !sExtraResPath.endsWith("/") &&
	    sExtraResPath.indexOf("..")<0 
		)
	    sResDir += sExtraResPath+"/";
		
		//response.setContentType("text/html");
        pMaster = new Page(osOut, sResDir + "masterpage/masterpage.res");
	}

	public void execGet() {
		String pageName = null;
		
		try {
            pageName=request.getParameter("page");
        }
		catch (Exception ex) { 
			pageName="no_frame.html"; 
		}
		
		if (pageName==null || pageName.length()==0)
			pageName="no_frame.html";
		
		if (pageName.indexOf("..")>=0)
			return;
		
		String fileExtension = pageName.substring(pageName.indexOf(".")+1,pageName.length());
		if(fileExtension.compareTo("jsp")==0)
			response.setContentType("application/jsp");
		else
			response.setContentType("text/html");
		
		pMaster.append(new Page(sResDir+"pages/"+pageName));
				
		display.initMasterPage(pMaster, Utils.getProperties(sConfDir, "global", null, false), sResDir);
		
		String sPath = gets("res_path");
		
		pMaster.modify("bookmark", "/show?page="+encode(pageName)+(sPath.length()>0 ? "&res_path="+encode(sPath):""));
		
		pMaster.write();
		
		bAuthOK = true;
	}

}
