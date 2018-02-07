<%@ page import="alimonitor.*,java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.Formatare,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /subscribe/subscribe.jsp", 0, request);

    String server = 
	request.getScheme()+"://"+
	request.getServerName()+":"+
	request.getServerPort()+"/";
	
    String sMethod = request.getMethod();
    ServletContext sc = getServletContext();
    
    final String SITE_BASE = sc.getRealPath("/");

    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Subscribe to events");
    
    pMaster.modify("bookmark", "/subscribe.jsp");

    String sName = request.getParameter("name") == null ? "" : request.getParameter("name");
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
    alErrors.add("You are already subscribed to this event!");//3
    //end errors
    
    String sQuery;
    DB db = new DB();    

    sQuery = "SELECT * FROM annotation_groups WHERE ag_id = "+iSet;

    db.query(sQuery);
        
    String sEvent = db.gets("ag_name")+" "+sSite;
    
    //POST
    if(sMethod.toLowerCase().equals("post")){
	if(sName.length() == 0){
	    response.sendRedirect("/subscribe/subscribe.jsp?error=1&set="+iSet+"&site="+response.encodeRedirectUrl(sSite)+"&name="+response.encodeRedirectUrl(sName)+"&email="+response.encodeRedirectUrl(sEmail));
	    return;
	}
	
	if(sEmail.length() == 0){
	    response.sendRedirect("/subscribe/subscribe.jsp?error=0&set="+iSet+"&site="+response.encodeRedirectUrl(sSite)+"&name="+response.encodeRedirectUrl(sName)+"&email="+response.encodeRedirectUrl(sEmail));
	    return;    
	}
	
	sQuery = "SELECT count(1)  AS cnt FROM subscribers WHERE s_agid="+iSet+" AND s_site='"+Formatare.mySQLEscape(sSite)+"' AND s_email='"+Formatare.mySQLEscape(sEmail)+"'";
	db.query(sQuery);
	
	if(db.geti("cnt") > 0){
	    response.sendRedirect("/subscribe/subscribe.jsp?error=3&set="+iSet+"&site="+response.encodeRedirectUrl(sSite)+"&name="+response.encodeRedirectUrl(sName)+"&email="+response.encodeRedirectUrl(sEmail));
	    return;
	}
		
	sQuery = "SELECT nextval('subscribers_s_id_seq') AS id";
	db.query(sQuery);
	
	int iSid = db.geti("id");
	
	sQuery = "INSERT INTO subscribers (s_id, s_email, s_name, s_agid, s_site, s_code, s_date) VALUES "+
		"("+iSid+", '"+Formatare.mySQLEscape(sEmail)+"', '"+Formatare.mySQLEscape(sName)+"', "+iSet+", '"+Formatare.mySQLEscape(sSite)+"', md5('"+iSid+"_"+sEmail+"'), now())";
    
	if(db.syncUpdateQuery(sQuery)){
	    //send the email
	    Page pInfo = new Page("subscribe/infos_email.res");
	    
	    pInfo.modify("event", sEvent);
	    pInfo.modify("name", sName);
	    pInfo.modify("email", sEmail);
	    
	    pMaster.append(pInfo);
	    
	    //send the email
	    Page pEmail = new Page("subscribe/validation_email.res");
	    pEmail.modify("event", sEvent);
	    pEmail.modify("name", sName);
	    pEmail.modify("email", sEmail);

	    sQuery = "SELECT s_code FROM subscribers  WHERE s_id="+iSid;
	    db.query(sQuery);
	    
	    pEmail.modify("code", db.gets("s_code"));
	
	    lia.util.mail.MailSender ms = lia.util.mail.DirectMailSender.getInstance();
	    ms.sendMessage("monalisa@alimonitor.cern.ch", new String[] {sEmail}, "MonAlisa Subscription confirmation", pEmail.toString());
	}
	else{
	    pMaster.append("<div align=center class=error>Error inserting into the database! Please try again!</div>");
	}
    
    }
    //GET
    else{
    
	Page pSubscribe = new Page("subscribe/subscribe.res");

	if(iError >= 0 && iError < alErrors.size())
    	    pSubscribe.modify("error", alErrors.get(iError));
    
	pSubscribe.modify("name", sName);
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