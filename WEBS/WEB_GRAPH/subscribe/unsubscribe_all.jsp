<%@ page import="alimonitor.*,java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.Formatare,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /subscribe/unsubscribe_all.jsp", 0, request);

    String sMethod = request.getMethod();

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Subscribe to events");
    
    pMaster.modify("bookmark", "/unsubscribe_all.jsp");

    String sEmail = request.getParameter("email") == null ? "" : request.getParameter("email").toLowerCase();

    //errors
    String sError = request.getParameter("error") == null ? "" : request.getParameter("error");
    int iError = -1;

    try{
        iError = Integer.parseInt(sError);
    }catch(Exception e){
        iError = -1;
    }
    

    ArrayList<String> alErrors = new ArrayList<String>();
    alErrors.add("Please insert your email adress!");//0
    alErrors.add("Please insert your name!");//1
    alErrors.add("Please select a service!");//2
    alErrors.add("You are not subscribed to this event!");//3
    //end errors
    
    String sQuery;
    DB db = new DB();    

    //POST
    if(sMethod.toLowerCase().equals("post")){
	if(sEmail.length() == 0){
	    response.sendRedirect("/subscribe/unsubscribe_all.jsp?error=0&email="+response.encodeRedirectUrl(sEmail));
	    return;    
	}
	
	sQuery = "SELECT * FROM subscribers INNER JOIN annotation_groups ON ag_id=s_agid WHERE s_email='"+Formatare.mySQLEscape(sEmail)+"'";
	db.query(sQuery);
	
	if(!db.moveNext()){
	    response.sendRedirect("/subscribe/unsubscribe_all.jsp?error=3&email="+response.encodeRedirectUrl(sEmail));
	    return;
	}

        Page pInfo = new Page("subscribe/infou_all_email.res");
    
        pInfo.modify("email", sEmail);
	
	pMaster.append(pInfo);
	
	Page pEmail = new Page("subscribe/unsubscribe_email_all.res");
	Page pEmailEl = new Page("subscribe/unsubscribe_email_all_el.res");
	
	do{	
	    pEmailEl.modify("event", db.gets("ag_name")+" "+db.gets("s_site"));
	    pEmailEl.modify("code", db.gets("s_code"));
	    pEmailEl.modify("email", sEmail);
	
	    pEmail.append(pEmailEl);
	}while(db.moveNext());
	
	lia.util.mail.MailSender ms = lia.util.mail.DirectMailSender.getInstance();
	ms.sendMessage("monalisa@alimonitor.cern.ch", new String[] {sEmail}, "MonAlisa unsubscription confirmation", pEmail.toString());
    
    }
    //GET
    else{
    
	Page pSubscribe = new Page("subscribe/unsubscribe_all.res");

	if(iError >= 0 && iError < alErrors.size())
    	    pSubscribe.modify("error", alErrors.get(iError));
    
    	pSubscribe.modify("email", sEmail);
    
	pMaster.append(pSubscribe);
    }
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/subscribe/unsubscribe_all.jsp", baos.size(), request);
%>