<%@ page import="java.util.*,java.io.*,lia.web.utils.*"%>
<%
    final ServletContext sc = getServletContext();

    final String SITE_BASE=sc.getRealPath("/");

    final String BASE_PATH=SITE_BASE+"/doc";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
    
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
    
    final Page pMaster = new Page(baos, BASE_PATH+"/edit.res");
	
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");

    String sPage = request.getParameter("page");

    if (sPage==null)
	sPage = "index";

    if (!sPage.matches("^[\\w/-]+$"))
	return;
	
    final String sContent = request.getParameter("content");
    
    final String sFile = BASE_PATH+"/"+sPage+".html";

    if (sContent!=null){
	final PrintWriter pw = new PrintWriter(new FileWriter(sFile));
	
	pw.print(sContent.trim());
	
	pw.flush();
	
	pw.close();
	
	response.sendRedirect("index.jsp?page="+sPage);
	
	return;
    }

    pMaster.modify("page", sPage);    
    pMaster.append(new Page(sFile));
    
    pMaster.write();
    String s = new String(baos.toByteArray());
    out.println(s);

%>
