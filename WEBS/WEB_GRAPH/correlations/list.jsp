<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /correlations/list.jsp", 0, request);    

    String server = 
	request.getScheme()+"://"+
	request.getServerName()+":"+
	request.getServerPort()+"/";
	
    ServletContext sc = getServletContext();
    
    final String SITE_BASE = sc.getRealPath("/");

    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/correlations";
    
    final String CONF_PATH=BASE_PATH+"/WEB-INF/conf/temp/";

    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);

    Page pMaster = new Page(baos, BASE_PATH+"/WEB-INF/res/masterpage/masterpage.res");

    pMaster.modify("title", "Predicate picker");
    
    Page p = new Page(RES_PATH+"/list.res");
    Page pLine = new Page(RES_PATH+"/list_line.res");

    // work
    
    File f = new File(CONF_PATH);
    
    String[] list = f.list();
    
    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    
    ArrayList<String> al = new ArrayList<String>(list.length);
    
    for (String s: list){
	al.add(s);
    }
    
    Collections.sort(al);
    Collections.reverse(al);
    
    for (String s: al){
	if (!s.matches("[0-9]+\\.properties"))
	    continue;
	
	s = s.substring(0, s.indexOf("."));
	
	Date d = new Date(Long.parseLong(s));
	
	pLine.modify("creationdate", sdf.format(d));
	pLine.modify("filename", s);
	
	Properties prop = new Properties();
	prop.load(new FileInputStream(new File(f, s+".properties")));

	pLine.modify("title", ServletExtension.pgets(prop, "title"));
	
	p.append(pLine);
    }
    
    // finish

    pMaster.append(p);

    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/correlations/list.jsp", baos.size(), request);    
%>