<%
    lia.web.servlets.web.Utils.logRequest("START /dologin.jsp", 0, request);

    String sPage = request.getParameter("page");

    response.sendRedirect(sPage!=null ? sPage : "/admin.jsp");

    lia.web.servlets.web.Utils.logRequest("/dologin.jsp", 0, request);
%>