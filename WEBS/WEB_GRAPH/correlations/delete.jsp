<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /correlations/correlations_delete.jsp", 0, request);

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
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("title", "Delete");


    int iStep = 1;
    
    try{
	iStep = Integer.parseInt(request.getParameter("step") == null ? "1" : request.getParameter("step"));    
    }
    catch(Exception e){
	e.printStackTrace();
    }
    
    String sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop"); 
    String sChart = request.getParameter("chart") == null ? "" : request.getParameter("chart");
    String sSeries = request.getParameter("serie") == null ? "" : request.getParameter("serie");    
    
    Properties p = new Properties();
    p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));
    
    switch(iStep){
	//chart
	case 2:
	    
	    String sNames = p.getProperty("charts");
	    
	    if(sNames == null)
		sNames = "";
	    
	    StringTokenizer st = new StringTokenizer(sNames, ",");
	    String sNewCharts = "";
	    
	    while(st.hasMoreTokens()){
		String sToken = st.nextToken();
	    
	    
		if(!sChart.equals(sToken)){
		    sNewCharts += (sNewCharts.length() == 0 ? "" : ",")+sToken;
		}
	    }
	    
	    p.setProperty("charts", sNewCharts);
    	
    	    p.remove(sChart+".alias");
	    p.remove(sChart+".xlabel");
	    p.remove(sChart+".ylabel");
	    p.remove(sChart+".type");
	    p.remove(sChart+".histogramtype");
	    p.remove(sChart+".autorange");
	    p.remove(sChart+".minx");		
	    p.remove(sChart+".maxx");
	    p.remove(sChart+".miny");
	    p.remove(sChart+".maxy");		
	    p.remove(sChart+".names");		
	    
    	    break;
    	//series
    	case 3:
    	    sNames = p.getProperty(sChart+".names");
    	    
    	    st = new StringTokenizer(sNames, ",");
    	    
    	    String sNewSeries = "";
    	    
    	    while(st.hasMoreTokens()){
    		String sToken = st.nextToken();
   
                if(!sSeries.equals(sToken)){
		    sNewSeries += (sNewSeries.length() == 0 ? "" : ",")+sToken;
                }
    	    }
	    
	    p.setProperty(sChart+".names", sNewSeries);    	
    	    
    	    break;
        default:
    	    break;
    }

    p.store(new FileOutputStream(CONF_PATH+sProp+".properties"), "");
    response.sendRedirect("/correlations/correlations.jsp?step="+iStep+"&prop="+sProp+(sSeries.length() > 0 ? "&chart="+sChart : ""));

    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/correlations/correlations_delete.jsp", baos.size(), request);
%>