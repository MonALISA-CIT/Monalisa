<%@ page import="lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.Monitor.Store.FarmBan,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.net.URLEncoder,lia.web.servlets.web.*,lia.web.utils.*,auth.*,java.security.cert.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /admin/relations_action.jsp", 0, request);

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

    String sSource = request.getParameter("source") == null ? "" : request.getParameter("source");    
    String sDest = request.getParameter("dest") == null ? "" : request.getParameter("dest");        
    String sTier = request.getParameter("tier") ==  null ? "" : request.getParameter("tier");        
    String sColor = request.getParameter("color") == null ? "" : request.getParameter("color");        

    sColor = sColor.substring(1);

    DB db = new DB();

    if(sSource.length() == 0){
	pMaster.append("Error: No farm");
    }
    else{
	String sQuery = "DELETE FROM site_relations WHERE site = '"+Formatare.mySQLEscape(sSource)+"';";
	db.query(sQuery);
	
	sQuery = "INSERT INTO site_relations (site, connectedto, centertype, color) VALUES "+
		    "('"+Formatare.mySQLEscape(sSource)+"', '"+Formatare.mySQLEscape(sDest)+"', '"+Formatare.mySQLEscape(sTier)+"', '"+Formatare.mySQLEscape(sColor)+"');";
    
	if(db.query(sQuery)){
	    pMaster.append("UPDATE SUCCESSFUL");
	}
	else{
	    pMaster.append("Error: Could not update the database");
	}
    }
        
    pMaster.write();    
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/admin/relations_action.jsp", baos.size(), request);
%>