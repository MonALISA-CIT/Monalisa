<%@ page import="alimonitor.*,lia.web.servlets.web.*,lia.web.utils.Formatare,java.util.Date,java.util.StringTokenizer,java.io.*,lia.web.utils.DoubleFormat,lia.Monitor.Store.Cache,lia.Monitor.monitor.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /info.jsp", 0, request);

    String server = 
	request.getScheme()+"://"+
	request.getServerName()+(request.getServerPort()!=80 ? ":"+request.getServerPort() : "")+"/";
	
    lia.Monitor.Store.Fast.TempMemWriterInterface tmw = null;
    
    String sError = "";

    try{
        lia.Monitor.Store.TransparentStoreFast store = (lia.Monitor.Store.TransparentStoreFast) lia.Monitor.Store.TransparentStoreFactory.getStore();
	tmw = store.getTempMemWriter();
    }
    catch (Throwable t){
	sError = t.getMessage();
    }
    
    ServletContext sc = getServletContext();
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("bookmark", "/info.jsp");
    
    pMaster.modify("title", "Repository internal statistics");
    
    Page pInfo = new Page("info.res");
    
    pInfo.modify("repository_version", display.sRepositoryVersion);
    pInfo.modify("repository_date", display.sRepositoryDate);
    pInfo.modify("time_now", (new Date()).toString());
    pInfo.modify("time_start", (new Date(display.lRepositoryStarted)).toString());
    pInfo.modify("uptime", display.getUptime());
    pInfo.modify("total_requests", ""+lia.web.utils.ThreadedPage.lTotalRequests);
    pInfo.modify("first_start", (new Date(lia.web.utils.ThreadedPage.getFirstRunEpoch())).toString());
    
    Runtime rt = Runtime.getRuntime();
    
    pInfo.modify("mem_free", ""+rt.freeMemory());
    pInfo.modify("mem_total", ""+rt.totalMemory());
    
    monPredicate mpDBSize = new monPredicate("pcalimonitor.cern.ch:8889", "Repository", "pcalimonitor.cern.ch:8889", -1, -1, new String[]{"DatabaseSize"}, null);

    Result r = (Result) Cache.getLastValue(mpDBSize);
		
    pInfo.modify("db_size", r!=null ? ""+DoubleFormat.size((long)r.param[0])+"B" : "");
    
    pInfo.modify("served_pages", ""+lia.web.utils.ThreadedPage.getRequestCount());
    pInfo.modify("served_pages_avg", ""+((double)lia.web.utils.ThreadedPage.getRequestCount()*3600000d / (System.currentTimeMillis() - display.lRepositoryStarted) ));
    
    if (tmw!=null){
        pInfo.append("data_cache", "values: "+tmw.getSize()+"/"+tmw.getLimit()+" (max "+tmw.getHardLimit()+"), time frame: ");
        long l = tmw.getTotalTime() / 1000;

        long s = l%60;
        long m = (l/60)%60;
        long h = l/(60*60);

        pInfo.append("data_cache", h+":"+(m<10 ? "0" : "")+m+":"+(s<10 ? "0" : "")+s+", served requests: "+tmw.getServedRequests());
    }
    else{
	pInfo.modify("data_cache", "cannot access data store");
    }
    
    
    String sOut= "<br><table border=0 cellspacing=1 cellpadding=0 style=\"font-face:Verdana,Helvetica;font-size:12px\">"+
		 "<tr><td rowspan=100% width=100>&nbsp;</td><td align=left><b>WS Function</b></td><td align=right><b>Calls</b></td></tr>";

    long total = 0;
		
    for (int i=0; i<lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames.length; i++){
        sOut += "<tr><td align=left>"+lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames[i]+"</td><td align=right>"+lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i]+"</td></tr>\n";
        total += lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i];
    }

    sOut += "<tr><td colspan=2><hr size=1></td></tr>";
    sOut += "<tr><td align=left><b>Total</b></td><td align=right><b>"+total+"</b></td></tr>";
    sOut += "</table>";

    if (total==0){
	pInfo.modify("ws_queries", "&nbsp;&nbsp;&raquo;&nbsp;&nbsp;No WS query yet on this repository");
    }
    else{
	pInfo.modify("ws_queries", sOut);
    }
    
    pInfo.modify("server", server);
    
    pInfo.modify("lastvalues_cache", ""+Cache.size());

    pMaster.append(pInfo);

    pMaster.write();
    String s = new String(baos.toByteArray());
    out.println(s);
    
    Utils.logRequest("/info.jsp", baos.size(), request);
%>