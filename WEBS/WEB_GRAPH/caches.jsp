<%@ page import="alimonitor.*,java.util.*,java.io.*,java.text.SimpleDateFormat,lia.util.StringFactory,lia.web.utils.ColorFactory"%><%
    lia.web.servlets.web.Utils.logRequest("START /caches.jsp", 0, request);
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream(4000);

    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");

    pMaster.modify("title", "Cache status");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    // -------------------
    
    Page p = new Page(null, "caches.res");

    // -------------------
    
    p.modify("strings_hit", StringFactory.getHitRatio());
    p.modify("strings_ignore", StringFactory.getIgnoreRatio());
    p.modify("strings_total", StringFactory.getAccessCount());
    p.modify("strings_size", StringFactory.getCacheSize());
    
    p.modify("colors_size", ColorFactory.size());
    p.modify("colors_hit", ColorFactory.getHitCount());
    p.modify("colors_miss", ColorFactory.getMissCount());
    
    
    // -------------------
    
    pMaster.append(p);
    
    // -------------------
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/caches.jsp", baos.size(), request);
%>