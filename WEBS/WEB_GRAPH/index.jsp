<%@ page import="lia.Monitor.Store.Fast.DB,lazyj.*"%><%
    final RequestWrapper rw = new RequestWrapper(request);

    lia.web.servlets.web.Utils.logRequest("START /index.jsp", 0, request);

    final Cookie[] cookies = request.getCookies();

    String sPage = "/map.jsp";

    if (cookies != null){
	for (int i = 0; i < cookies.length; i++) {
    	    if (cookies[i].getName().equals("lastval_div_indexmap")){
    		sPage = cookies[i].getValue();
    	    
    		sPage = java.net.URLDecoder.decode(sPage);
    		
    		sPage = lia.web.utils.Formatare.replace(sPage, "&amp;", "&");
    	    }
    	}
    }
    
    try{
	final int id = Integer.parseInt(request.getQueryString());
	
	if (id>0){
	    final DB db = new DB("SELECT path FROM shorturl WHERE id="+id);
	    
	    if (db.moveNext()){
		sPage = db.gets(1);
		
		db.asyncUpdate("UPDATE shorturl SET lastaccessed=extract(epoch from now())::int, accesscount=accesscount+1 WHERE id="+id);
	    }
	}
    }
    catch (Exception e){
    }

    response.sendRedirect(sPage);
    
    lia.web.servlets.web.Utils.logRequest("/index.jsp?path="+sPage, 0, request);
%>