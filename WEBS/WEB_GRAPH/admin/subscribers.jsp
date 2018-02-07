<%@ page import="lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.Monitor.Store.FarmBan,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.net.URLEncoder,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.Result,lia.Monitor.monitor.eResult,lia.Monitor.JiniClient.Store.Main" %><%
    lia.web.servlets.web.Utils.logRequest("START /admin/subscribers.jsp", 0, request);

    ServletContext sc = getServletContext();
    final String SITE_BASE = sc.getRealPath("/");
 
    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
        
    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            
    Page pMaster = new Page(baos, RES_PATH+"/masterpage/masterpage_admin.res");
    
    pMaster.modify("title", "Site Administration");
    pMaster.modify("class_administration", "_active");

    Page pSubscribers = new Page(BASE_PATH+"/admin/subscribers.res");
    Page pSubscribersEl = new Page(BASE_PATH+"/admin/subscribers_el.res");

    String sAction = request.getParameter("action") == null ? "" : request.getParameter("action");
    
    DB db = new DB();
    String sQuery;
    
    if(sAction.equals("1")){
	String sId =  request.getParameter("sid") == null ? "" : request.getParameter("sid");
	
	sQuery = "DELETE FROM subscribers WHERE s_id="+sId;
	
	db.syncUpdateQuery(sQuery);	    
    }

    if(sAction.equals("2")){
	String sId =  request.getParameter("sid") == null ? "" : request.getParameter("sid");
	
	sQuery = "UPDATE subscribers SET s_valid = (s_valid + 1) % 2 WHERE s_id="+sId;
	
	db.syncUpdateQuery(sQuery);	    
    }
    
    String sSet = request.getParameter("set") == null ? "" : request.getParameter("set");
    String sSite = request.getParameter("site") == null ? "" : request.getParameter("site");    
    String sEmail = request.getParameter("email") == null ? "" : request.getParameter("email").toLowerCase();        
    
    sQuery = "SELECT * FROM subscribers INNER JOIN annotation_groups ON ag_id=s_agid "; 
    
    boolean bAnd = true;
    
    if(sSet.length() > 0){
	sQuery += " WHERE ag_id = "+sSet;
	bAnd = true;
	
	DB db1 = new DB();
	db1.query("SELECT * FROM annotation_groups WHERE ag_id="+sSet);
	
	pSubscribers.modify("path", "&raquo; "+db1.gets("ag_name"));
    }
    
    if(sSite.length() > 0){
	sQuery += ((bAnd) ? " AND " : " WHERE " ) + " s_site='"+Formatare.mySQLEscape(sSite)+"' ";
	bAnd = true;
	
	 pSubscribers.modify("path", "&raquo; "+sSite);
    }

    if(sEmail.length() > 0){
	sQuery += ((bAnd) ? " AND " : " WHERE " ) + " s_email='"+Formatare.mySQLEscape(sEmail)+"' ";
	bAnd = true;

	pSubscribers.modify("path", "&raquo; "+sEmail);
    }
    
    sQuery += " ORDER BY s_email ASC, s_site ASC, s_id ASC";

    db.query(sQuery);

    while(db.moveNext()){
	pSubscribersEl.modify("sid", db.geti("s_id"));
	pSubscribersEl.modify("set", db.gets("ag_name"));
	pSubscribersEl.modify("set_id", db.gets("ag_id"));
	pSubscribersEl.modify("site", db.gets("s_site").length() == 0 ? "" : db.gets("s_site"));
	pSubscribersEl.modify("email", db.gets("s_email"));
	pSubscribersEl.modify("name", db.gets("s_name"));	
	
	pSubscribersEl.modify("validated", db.geti("s_valid") == 1 ? "YES" : "NO");	
	
	pSubscribers.append(pSubscribersEl);
    }

    
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    response.setContentType("text/html");
    response.setHeader("Admin", "Costin Grigoras <costin.grigoras@cern.ch>");

    pMaster.append(pSubscribers);

    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
            
    lia.web.servlets.web.Utils.logRequest("/admin/subscribers.jsp", s.length(), request);
%>