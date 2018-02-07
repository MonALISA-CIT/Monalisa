<%@ page import="lia.web.utils.CacheServlet" %><%
lia.web.servlets.web.Utils.logRequest("START /cache.jsp", 0, request);

final String clear = request.getParameter("clear");

if (clear!=null && clear.trim().length()>0)
    CacheServlet.clearCache();
%>

<html>
    <head>
        <title>Cache contents</title>
    </head>
    <body bgcolor=white>
        <%=CacheServlet.getCacheContent()%>
    </body>
</html><%
    lia.web.servlets.web.Utils.logRequest("/cache.jsp", 0, request);
%>