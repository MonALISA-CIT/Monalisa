<%@ page import="alimonitor.*,java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.Formatare,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /subscribe/validate.jsp", 0, request);

    String sMethod = request.getMethod();

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Validate your subscription");
    
    String sCode = request.getParameter("code") == null ? "" : request.getParameter("code");

    String sQuery;
    DB db = new DB();    

    sQuery = "SELECT * FROM subscribers INNER JOIN annotation_groups ON ag_id=s_agid WHERE s_code='"+Formatare.mySQLEscape(sCode)+"'";
    db.query(sQuery);
    
    if(!db.moveNext()){
	pMaster.append("<div class=error><br>Invalid code!</div>");    
    }
    else{
	int iValid = db.geti("s_valid");
	
	if(iValid == 1){
	    pMaster.append("<div class=error>This email was already validated!</div>");
	}
	else{
	    String sEvent = db.gets("ag_name")+" "+db.gets("s_site");
	    String sName = db.gets("s_name");
	    String sEmail = db.gets("s_email");
	
	    sQuery = "UPDATE subscribers SET s_valid = 1 WHERE s_id="+db.geti("s_id")+";";

	    if(db.query(sQuery)){
		Page pInfo = new Page("subscribe/subscribe_ok.res");
		
		pInfo.modify("event", sEvent);
		pInfo.modify("name", sName);
		pInfo.modify("email", sEmail);
		
		pMaster.append(pInfo);
	    }
	    else{
		pMaster.append("<div class=error>Error updating the database!</div>");
	    }
	}
    }
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/subscribe/validate.jsp", baos.size(), request);
%>