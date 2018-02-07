<%@ page import="lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.Monitor.Store.FarmBan,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.net.URLEncoder,lia.web.servlets.web.*,lia.web.utils.*,auth.*,java.security.cert.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /admin/map_admin.jsp", 0, request);

    ServletContext sc = getServletContext();
    final String SITE_BASE = sc.getRealPath("/");
 
    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
        
    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            
    Page pMaster = new Page(baos, RES_PATH+"/masterpage/masterpage_admin.res");

    pMaster.modify("title", "Site administration");
    
    //menu
    pMaster.modify("class_administration", "_active");

    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    response.setContentType("text/html");
    response.setHeader("Admin", "Costin Grigoras <costing@cs.pub.ro>");

    Page pAdmin = new Page(BASE_PATH+"/admin/map_admin.res");                 

    String sQuery = "SELECT * FROM abping_aliases WHERE name NOT IN (SELECT name FROM hidden_sites) ORDER BY name ASC";
    DB db = new DB();
    
    db.query(sQuery);
    
    Page pMapAdminEl = new Page(BASE_PATH+"/admin/map_admin_el.res");
    
    int i = 0;
    
    while(db.moveNext()){
	pMapAdminEl.modify("name", db.gets("name"));
	pMapAdminEl.modify("labelx", db.gets("labelx"));
	pMapAdminEl.modify("labely", db.gets("labely"));
	pMapAdminEl.modify("iconx", db.gets("iconx"));
	pMapAdminEl.modify("icony", db.gets("icony"));
	
	pMapAdminEl.modify("color", (i%2 == 0 ? "#FFFFFF" : "#F0F0F0"));	
	
	pAdmin.append(pMapAdminEl);
	
	i++;
    }
    
    pMaster.append(pAdmin);
    
    pMaster.write();
        
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/admin/map_admin.jsp", baos.size(), request);
%>
