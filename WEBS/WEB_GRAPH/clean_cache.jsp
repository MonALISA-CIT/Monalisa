<%@ page import="lia.web.utils.CacheServlet" %><%
    lia.web.servlets.web.Utils.logRequest("START /clean_cache.jsp", 0, request);

    CacheServlet.clearCache();

    response.sendRedirect("cache.jsp");
    
    lia.web.servlets.web.Utils.logRequest("/clean_cache.jsp", 0, request);
%>