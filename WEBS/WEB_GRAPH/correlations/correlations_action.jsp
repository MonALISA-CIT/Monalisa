<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /correlations/correlations_action.jsp", 0, request);

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
    
    pMaster.modify("title", "Correlations");


    int iStep = 1;
    
    try{
	iStep = Integer.parseInt(request.getParameter("step") == null ? "1" : request.getParameter("step"));    
    }
    catch(Exception e){
	e.printStackTrace();
    }
    
    switch(iStep){
	case 1:
	    String sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop"); 
    	    
    	    Properties p = new Properties();
    	    
    	    if(sProp.length() == 0){
    		Date now = new Date();
    		sProp = now.getTime()+"";
	    }
	    else{
		if (!sProp.matches("^\\d+$"))
		    return;
	    
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));
	    }
	    
	    String sTitle = request.getParameter("title") == null ? "" : request.getParameter("title");
	    String sWidth = request.getParameter("width") == null ? "" : request.getParameter("width");
	    String sHeight = request.getParameter("height") == null ? "" : request.getParameter("height");	    
	    String sSamerange = request.getParameter("samerange") == null ? "" : request.getParameter("samerange");	    
	    String sDropYAbove = request.getParameter("dropy_above") == null ? "" : request.getParameter("dropy_above");
	    String sDropYBelow = request.getParameter("dropy_below") == null ? "" : request.getParameter("dropy_below");

	    p.setProperty("title", sTitle);
	    p.setProperty("width", sWidth);
	    p.setProperty("height", sHeight);
	    p.setProperty("samerange", sSamerange);
	    p.setProperty("dropy_below", sDropYBelow);
	    p.setProperty("dropy_above", sDropYAbove);
	    
    	    p.store(new FileOutputStream(CONF_PATH+sProp+".properties"), "");
    	    response.sendRedirect("/correlations/correlations.jsp?step=2&prop="+sProp);

    	    break;
    	case 2:
    	    sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop"); 
    	    
    	    if(sProp.length() > 0){
    		if (!sProp.matches("^\\d+$"))
    		    return;
    	    
		p = new Properties();
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));
    		
    		//set the "charts" property
    		sTitle = ""; 
    		
    		//daca e edit sau nu
    		String sChartEl = request.getParameter("chart") == null ? "" : request.getParameter("chart"); 
    		
		
		// daca e edit nu trebuie modificat titlul
		if(sChartEl.length() == 0){
		    String sCharts = p.getProperty("charts");
		
		    Date now = new Date();
		    sTitle = now.getTime()+"";
		    
		    if(sCharts == null){
			p.setProperty("charts", sTitle);

		    }
		    else{
	    		StringTokenizer st = new StringTokenizer(sCharts, ",");
			p.setProperty("charts", sCharts+","+sTitle);
		    }
		}
		else{
		    sTitle = sChartEl;
		}
    		
    		
    		String sAlias = request.getParameter("alias") == null ? "" : request.getParameter("alias");
    		p.setProperty(sTitle+".alias", sAlias);
    		
    		String sXLabel = request.getParameter("xlabel") == null ? "" : request.getParameter("xlabel");    		
    		p.setProperty(sTitle+".xlabel", sXLabel);
    		
    		String sYLabel = request.getParameter("ylabel") == null ? "" : request.getParameter("ylabel");
    		p.setProperty(sTitle+".ylabel", sYLabel);
    		
    		String sType = request.getParameter("type") == null ? "" : request.getParameter("type");    		    		
    		p.setProperty(sTitle+".type", sType);
    		
    		if(sType.equals("histogram")){
    		    String sHistogram = request.getParameter("histogram_type") == null ? "" : request.getParameter("histogram_type");
    		    p.setProperty(sTitle+".histogramtype", sHistogram);
    		}

    		String sAutorange = request.getParameter("autorange") == null ? "false" : request.getParameter("autorange");    		
    		p.setProperty(sTitle+".autorange", sAutorange);    		
    		
    		if(sAutorange.equals("false")){
    		    String sXMin = request.getParameter("minx") == null ? "" : request.getParameter("minx");    		
    		    p.setProperty(sTitle+".minx", sXMin);    		    		    
    		    
    		    String sXMax = request.getParameter("maxx") == null ? "" : request.getParameter("maxx");    		    
    		    p.setProperty(sTitle+".maxx", sXMax);    		    		    
    		    
    		    String sYMin = request.getParameter("miny") == null ? "" : request.getParameter("miny");    		    
    		    p.setProperty(sTitle+".miny", sYMin);    		    		    
    		    
    		    String sYMax = request.getParameter("maxy") == null ? "" : request.getParameter("maxy");    		    
    		    p.setProperty(sTitle+".maxy", sYMax);    		    		    
    		}
    		
                p.store(new FileOutputStream(CONF_PATH+sProp+".properties"), "");                                                                                     
    		response.sendRedirect("/correlations/correlations.jsp?step=3&prop="+sProp+"&chart="+sTitle);    
    	    }
    	    
    	    break;
    	case 3:
    	    sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop");     	    
    	    String sChart = request.getParameter("chart") == null ? "" : request.getParameter("chart"); 
    	    
    	    String sSerieEl = request.getParameter("serie") == null ? "" : request.getParameter("serie"); 
    	    
    	    if(sProp.length() > 0){
    	    	if (!sProp.matches("^\\d+$"))
		    return;
    	    
		p = new Properties();
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));
    		
    		//set the "charts" property
    		sTitle = ""; 
		String sSeries = p.getProperty(sChart+".names");
		
		if(sSerieEl.length() == 0){
		    Date now = new Date();
		    
		    sTitle = now.getTime()+"";
		    
		    if(sSeries == null || sSeries.length() == 0){
			p.setProperty(sChart+".names", sTitle);
		    }
		    else{
			StringTokenizer st = new StringTokenizer(sSeries, ",");
			p.setProperty(sChart+".names", sSeries+","+sTitle);
		    }
		}
		else{
		    sTitle = sSerieEl;
		}
    		
    		String sAlias = request.getParameter("alias") == null ? "" : request.getParameter("alias");
    		p.setProperty(sChart+"."+sTitle+".alias", sAlias);

    		String sFunction = request.getParameter("function") == null ? "" : request.getParameter("function");
    		p.setProperty(sChart+"."+sTitle+".function", sFunction);

		String sPredicates = p.getProperty(sChart+"."+sTitle+".pred") == null ? "" : p.getProperty(sChart+"."+sTitle+".pred");


		String sPredicate = request.getParameter("predicates") == null ? "" : request.getParameter("predicates");
		
    		p.setProperty(sChart+"."+sTitle+".pred", sPredicate);		
        	
        	p.store(new FileOutputStream(CONF_PATH+sProp+".properties"), "");                                                                                     
    		response.sendRedirect("/correlations/correlations.jsp?step=3&prop="+sProp+"&chart="+sChart);    

	    }    	    

    	    
    	    break;
        default:
    	    break;
    }

    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/correlations/correlations_action.jsp", baos.size(), request);
%>
