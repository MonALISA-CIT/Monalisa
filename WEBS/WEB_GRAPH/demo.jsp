<%@ page import="alimonitor.*,java.util.*,java.io.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /demo.jsp", 0, request);

    ByteArrayOutputStream baos = new ByteArrayOutputStream(15000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("bookmark", "/demo.jsp");
    
    pMaster.modify("title", "Demo mode");
    
    Page p = new Page("demo.res");
    
    pMaster.append(p);
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/demo.jsp", baos.size(), request);
%>