<%@ page import="lia.Monitor.monitor.*,java.io.*,java.util.*,lazyj.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /getValue.jsp", 0, request);

    response.setContentType("text/plain");

    final RequestWrapper rw = new RequestWrapper(request);

    final String sPredicate = rw.gets("p");
    
    if (sPredicate.length()==0)
	return;
    
    monPredicate pred = lia.web.utils.Formatare.toPred(sPredicate);
	
    Object o = lia.Monitor.Store.Cache.getLastValue(pred);

    String sValue = "";

    if (o!=null){
	if (o instanceof Result){
	    sValue = ""+((Result)o).param[0];
	}
	else
	if (o instanceof eResult){
	    sValue = ((eResult)o).param[0].toString();
	}
    }
    
    if (sValue.matches("^[0-9]+\\.0$"))
	sValue = sValue.substring(0, sValue.length()-2);
	
    out.println(sValue);
    
    lia.web.servlets.web.Utils.logRequest("/getValue.jsp?p="+sPredicate, 0, request);
%>