<%@ page import="alimonitor.*,java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.Formatare,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /subscribe/unvalidate.jsp", 0, request);

    String sMethod = request.getMethod();

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Unsubscribe to MonALISA alerts");
    
    String sCode = request.getParameter("code") == null ? "" : request.getParameter("code");
    String sEmail = request.getParameter("email") == null ? "" : request.getParameter("email");    
    
    String sQuery;
    DB db = new DB();    

    sQuery = "SELECT * FROM subscribers INNER JOIN annotation_groups ON ag_id=s_agid WHERE s_code='"+Formatare.mySQLEscape(sCode)+"' AND s_email='"+Formatare.mySQLEscape(sEmail)+"'";
    db.query(sQuery);
    
    if(!db.moveNext()){
	pMaster.append("<div class=error><br>Invalid code or email adress!</div>");    
    }
    else{
        String sEvent = db.gets("ag_name")+" "+db.gets("s_site");
        String sName = db.gets("s_name");
	
	sQuery = "DELETE FROM subscribers WHERE s_code='"+Formatare.mySQLEscape(sCode)+"' AND s_email='"+Formatare.mySQLEscape(sEmail)+"';";

	if(db.query(sQuery)){
	    Page pInfo = new Page("subscribe/unsubscribe_ok.res");
	
	    pInfo.modify("event", sEvent);
	    pInfo.modify("name", sName);
	    pInfo.modify("email", sEmail);
		
	    pMaster.append(pInfo);
	}
	else{
	    pMaster.append("<div class=error>Error updating the database!</div>");
	}
    }
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/subscribe/unvalidate.jsp", baos.size(), request);
%>