<%@ page import="lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.Monitor.Store.FarmBan,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.net.URLEncoder,lia.web.servlets.web.*,lia.web.utils.*,auth.*,java.security.cert.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /admin/map_admin_action.jsp", 0, request);

    ServletContext sc = getServletContext();
    final String SITE_BASE = sc.getRealPath("/");
 
    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
        
    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            
    Page pMaster = new Page(baos, RES_PATH+"/masterpage/empty_page.res");

    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    response.setContentType("text/html");
    response.setHeader("Admin", "Costin Grigoras <costing@cs.pub.ro>");

    String sName = request.getParameter("name") == null ? "" : request.getParameter("name");    
    String sLabelX = request.getParameter("labelx") == null ? "" : request.getParameter("labelx");    
    String sLabelY = request.getParameter("labely") == null ? "" : request.getParameter("labely");    
    String sIconX = request.getParameter("iconx") == null ? "" : request.getParameter("iconx");    
    String sIconY = request.getParameter("icony") == null ? "" : request.getParameter("icony");        

    DB db = new DB();

    if(sName.length() == 0){
	pMaster.append("Error: No farm");
    }
    else{
	try{
    
	    int iLabelX = Integer.parseInt(sLabelX);
	    int iLabelY = Integer.parseInt(sLabelY);	
	    int iIconX = Integer.parseInt(sIconX);
	    int iIconY = Integer.parseInt(sIconY);	
    
            String sQuery = "UPDATE abping_aliases SET "+
    		"labelx = "+Formatare.mySQLEscape(sLabelX)+", "+
    		"labely="+Formatare.mySQLEscape(sLabelY)+", "+
    		"iconx="+Formatare.mySQLEscape(sIconX)+", "+
    		"icony="+Formatare.mySQLEscape(sIconY)+" "+
    		"WHERE name='"+Formatare.mySQLEscape(sName)+"'";
	
	    if(db.query(sQuery))
    		pMaster.append("OK");
    	    else
    		pMaster.append("Error: database update error");
	
	
	}
	catch(Exception e){
	    pMaster.append("Error: not an integer");
	}
    }
        
    pMaster.write();    
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/admin/map_admin_action.jsp", baos.size(), request);
%>
