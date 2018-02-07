<%@ page import="alimonitor.*,lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.util.*,java.io.*,java.text.DateFormat,java.text.SimpleDateFormat,lia.web.servlets.web.*" %><%
    if (!lia.web.utils.ThreadedPage.acceptRequest(request, response))
	return;

    Utils.logRequest("START /xml.jsp", 0, request);
    
    response.setContentType("text/html");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);
            
    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    
    pMaster.modify("title", "ATOM feeds of monitoring-triggered events");
    
    pMaster.modify("bookmark", "/xml.jsp");

    Page p = new Page("xml.res");
    
    Page pSite = new Page("xml_site.res");
    Page pLine1 = new Page("xml_line.res");
    Page pLine2 = new Page("xml_line.res");
    Page pLine3 = new Page("xml_line.res");
    Page pLine4 = new Page("xml_line.res");

    DB db = new DB("select name from abping_aliases where name in (select name from alien_sites) order by lower(name) asc;");
    
    int iCol = -1;
    
    char cPrev = ' ';
    
    final int COUNT=6;
    
    while (db.moveNext()){
	char c = db.gets(1).toUpperCase().charAt(0);
    
	if (c!=cPrev){
	    iCol++;
	    
	    cPrev = c;
	    
	    if (iCol==COUNT){
		p.append("services", pLine1);
		p.append("proxies", pLine2);
		p.append("samtests", pLine3);
		p.append("storages", pLine4);
		iCol = 0;
	    }
	    
	    String sText = "<b><font color=#FF6600>"+c+"</font></b>";
	    
	    pLine1.append("col"+iCol, sText);
	    pLine2.append("col"+iCol, sText);
	    pLine3.append("col"+iCol, sText);
	    pLine4.append("col"+iCol, sText);
	}
    
	pSite.modify("site", db.gets(1));
	pSite.modify("set", 6);
	
	pLine1.append("col"+iCol, pSite);
	
	pSite.modify("site", db.gets(1));
	pSite.modify("set", 8);
	
	pLine2.append("col"+iCol, pSite);

	pSite.modify("site", db.gets(1));
	pSite.modify("set", 12);
	
	pLine3.append("col"+iCol, pSite);

	pSite.modify("site", db.gets(1));
	pSite.modify("set", 10);
	
	pLine4.append("col"+iCol, pSite);
    }
    
    if (iCol<COUNT){
	p.append("services", pLine1);
	p.append("proxies", pLine2);
	p.append("samtests", pLine2);
    }

    pMaster.append(p);
    
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");

    pMaster.write();
            
    String s = new String(baos.toByteArray());
    out.println(s);
    
    Utils.logRequest("/xml.jsp", baos.size(), request);
%>