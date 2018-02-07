<%@ page import="alimonitor.*,java.util.*,java.io.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /contact.jsp", 0, request);

    ByteArrayOutputStream baos = new ByteArrayOutputStream(15000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    pMaster.modify("title", "Grid development and deployment team");
    
    pMaster.modify("bookmark", "/contact.jsp");
    
    Page p = new Page("contact.res");
    
    pMaster.append(p);
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/contact.jsp", baos.size(), request);
%>
