<%@ page import="alimonitor.*,java.util.*,java.io.*,lia.web.utils.Formatare,java.text.SimpleDateFormat,java.awt.Color,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /subscribe/subscribe.jsp", 0, request);

    String sMethod = request.getMethod();

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Subscribe to events");
    
    pMaster.modify("bookmark", "/unsubscribe.jsp");

    String sEmail = request.getParameter("email") == null ? "" : request.getParameter("email").toLowerCase();

    String sSet = request.getParameter("set") == null ? "0" : request.getParameter("set");
    int iSet = -1;
    
    try{
        iSet = Integer.parseInt(sSet);
    }catch(Exception e){
        iSet = -1;
    }
    
    String sSite = request.getParameter("site") == null ? "" : request.getParameter("site");
    
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

    sQuery = "SELECT * FROM annotation_groups WHERE ag_id = "+iSet;

    db.query(sQuery);
        
    String sEvent = db.gets("ag_name")+" "+sSite;
    
    //POST
    if(sMethod.toLowerCase().equals("post")){
	if(sEmail.length() == 0){
	    response.sendRedirect("/subscribe/unsubscribe.jsp?error=0&set="+iSet+"&site="+response.encodeRedirectUrl(sSite)+"&email="+response.encodeRedirectUrl(sEmail));
	    return;    
	}
	
	sQuery = "SELECT * FROM subscribers WHERE s_agid="+iSet+" AND s_site='"+Formatare.mySQLEscape(sSite)+"' AND s_email='"+Formatare.mySQLEscape(sEmail)+"'";
	db.query(sQuery);
	
	if(!db.moveNext()){
	    response.sendRedirect("/subscribe/unsubscribe.jsp?error=3&set="+iSet+"&site="+response.encodeRedirectUrl(sSite)+"&email="+response.encodeRedirectUrl(sEmail));
	    return;
	}
		
		
        Page pInfo = new Page("subscribe/infou_email.res");
    
        pInfo.modify("event", sEvent);
        pInfo.modify("name", db.gets("s_name"));
        pInfo.modify("email", sEmail);
	    
        pMaster.append(pInfo);
	    
        //send the email
        Page pEmail = new Page("subscribe/unsubscribe_email.res");
        pEmail.modify("event", sEvent);
        pEmail.modify("name", db.gets("s_name"));
        pEmail.modify("email", sEmail);
	pEmail.modify("code", db.gets("s_code"));
	
	lia.util.mail.MailSender ms = lia.util.mail.DirectMailSender.getInstance();
	ms.sendMessage("monalisa@alimonitor.cern.ch", new String[] {sEmail}, "MonAlisa unsubscription confirmation", pEmail.toString());
    
    }
    //GET
    else{
    
	Page pSubscribe = new Page("subscribe/unsubscribe.res");

	if(iError >= 0 && iError < alErrors.size())
    	    pSubscribe.modify("error", alErrors.get(iError));
    
    	pSubscribe.modify("email", sEmail);
    	pSubscribe.modify("site", sSite);
    	pSubscribe.modify("set", iSet);
    
	sQuery = "SELECT * FROM annotation_groups WHERE ag_id = "+iSet;

	db.query(sQuery);
    
        pSubscribe.modify("event", sEvent);
    
	pMaster.append(pSubscribe);
    }
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/subscribe/subscribe.jsp", baos.size(), request);
%>