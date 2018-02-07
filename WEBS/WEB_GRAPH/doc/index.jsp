<%@ page import="java.util.*,java.io.*,lia.web.utils.*"%>
<%
    final ServletContext sc = getServletContext();

    final String SITE_BASE=sc.getRealPath("/");

    final String BASE_PATH=SITE_BASE+"/doc";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
    
    String sPage = request.getParameter("page");

    if (sPage==null)
	sPage = "index";
    
    boolean bFull = sPage.equals("index") || sPage.startsWith("full/");
    
    final String sOriginalPage = sPage;
    
    Page pMaster = new Page(baos, bFull ? RES_PATH+"/masterpage/masterpage.res" : BASE_PATH+"/index.res");
	
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");

    String sFile = BASE_PATH+"/"+sPage+".html";
    
    if (!(new File(sFile)).exists()){
	sPage = "missing";
	sFile = BASE_PATH+"/"+sPage+".html";
    }

    Page pFile = new Page(sFile);
    pFile.modify("page", sOriginalPage);

    pMaster.append(pFile);
    
    pMaster.modify("page", sOriginalPage);

    pMaster.write();
    String s = new String(baos.toByteArray());
    out.println(s);
%>
