<%
    lia.web.servlets.web.Utils.logRequest("START /gc.jsp", 0, request);

    System.gc();
    
    lia.web.servlets.web.Utils.logRequest("/gc.jsp", 0, request);
%>
