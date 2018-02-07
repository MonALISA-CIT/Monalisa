<%@ page import="alimonitor.*,java.util.*,java.io.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /links.jsp", 0, request);

    ByteArrayOutputStream baos = new ByteArrayOutputStream(5000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    pMaster.modify("title", "Useful links");
    
    pMaster.modify("bookmark", "/links.jsp");
    
    Page p = new Page("links.res");
    
    pMaster.append(p);
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/links.jsp", baos.size(), request);
%>
