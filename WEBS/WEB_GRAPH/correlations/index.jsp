<%
    lia.web.servlets.web.Utils.logRequest("START /correlations/index.jsp", 0, request);

    response.sendRedirect("list.jsp");
    
    lia.web.servlets.web.Utils.logRequest("/correlations/index.jsp", 0, request);
%>