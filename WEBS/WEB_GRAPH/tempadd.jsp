<%@ page import="lia.web.servlets.web.display,lia.web.utils.Formatare,java.util.*,java.io.*,lia.Monitor.monitor.*,lia.Monitor.Store.Cache,lia.Monitor.Store.Fast.DB,lia.web.utils.Page" %><%
    lia.web.servlets.web.Utils.logRequest("START /tempadd.jsp", 0, request);    

    String sSite = request.getParameter("site");
    String sChart = request.getParameter("chart");
    String sSensor = request.getParameter("sensor");
    
    if (sSite!=null && sChart!=null && sSensor!=null){
	DB db = new DB();
	
	if (db.query("DELETE FROM temp_assoc WHERE site='"+Formatare.mySQLEscape(sSite)+"' AND chart='"+Formatare.mySQLEscape(sChart)+"';"))
	    db.query("INSERT INTO temp_assoc (site, chart, sensor) VALUES ('"+Formatare.mySQLEscape(sSite)+"', '"+Formatare.mySQLEscape(sChart)+"', '"+Formatare.mySQLEscape(sSensor)+"');");
    }

    response.sendRedirect("/temp.jsp?site="+sSite);
    
    lia.web.servlets.web.Utils.logRequest("/tempadd.jsp", 0, request);
%>