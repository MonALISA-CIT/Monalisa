<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /correlations/correlations.jsp", 0, request);

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
	    //tries to create the properties file
	    Page pStep1 = new Page(RES_PATH+"/step1.res");
	    
	    String sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop");	    
	    
	    if(sProp.length() > 0){
		Properties p = new Properties();
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));
		
		pStep1.modify("title", p.getProperty("title"));
		pStep1.modify("width", p.getProperty("width"));
		pStep1.modify("height", p.getProperty("height"));
		pStep1.modify("dropyabove", p.getProperty("dropy_above"));
		pStep1.modify("dropybelow", p.getProperty("dropy_below"));
		pStep1.modify("samerange", "true".equals(p.getProperty("samerange")) ? "checked" : "");
		
		pStep1.modify("prop", sProp);
	    }
	    //default values
	    else{
		pStep1.modify("title", "Default Chart Title");
		pStep1.modify("width", "800");		
		pStep1.modify("height", "600");				
		
		pStep1.comment("com_back", false);
	    }
	    
	    pMaster.append(pStep1);
	    
	    break;
	case 2:
	    sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop");
	    String sChartEl = request.getParameter("chart") == null ? "" : request.getParameter("chart");
	    
	    if(sProp.length() > 0){
		Properties p = new Properties();
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));

		Page pStep2 = new Page(RES_PATH+"/step2.res");
		
		pStep2.modify("prop", sProp);
		
		//list the charts
		String sCharts = p.getProperty("charts");
		
		if(sCharts != null){
		    Page pCharts = new Page(RES_PATH+"/charts.res");
		    Page pChartsEl = new Page(RES_PATH+"/charts_el.res");
		    
		    pCharts.modify("prop", sProp);
		    
		    StringTokenizer st = new StringTokenizer(sCharts, ",");
		    
		    while(st.hasMoreElements()){
			String sChart = (String) st.nextElement();
			
			if(!sChart.equals(sChartEl)){
			    pChartsEl.modify("prop", sProp);
			    pChartsEl.modify("title", sChart);
			    pChartsEl.modify("alias", p.getProperty(sChart+".alias"));			

			    pChartsEl.modify("xlabel", p.getProperty(sChart+".xlabel"));		    
			    pChartsEl.modify("ylabel", p.getProperty(sChart+".ylabel"));		    
		    
			    String sChartType = p.getProperty(sChart+".type");
		
			    if("histogram".equals(sChartType)){
				sChartType += " : "+ p.getProperty(sChart+".histogramtype");
			    }
		
    			    pChartsEl.modify("type", sChartType);
		
			    String sChartAutorange = p.getProperty(sChart+".autorange");
		
			    if("true".equals(sChartAutorange)){
				sChartAutorange = "YES";
			    }
			    else{
				sChartAutorange = "NO<br>X Min:"+p.getProperty(sChart+".minx")+" X Max:"+p.getProperty(sChart+".maxx")+"<br> Y Min:"+p.getProperty(sChart+".miny")+" Y Max:"+p.getProperty(sChart+".maxy");
				
			    }
			    
			    pChartsEl.modify("autorange", sChartAutorange);
			
			    pCharts.append(pChartsEl);
			}
		    }
		    
		    if(sChartEl.length() == 0)
			pCharts.comment("com_view", false);
		    
		    pStep2.modify("charts", pCharts);
		    
		}
		
		//info chart
		if(sChartEl.length() != 0){
		    pStep2.modify("action", "Modify");
		    pStep2.modify("disabled", "disabled");
		
		    pStep2.modify("title", sChartEl);
		    pStep2.modify("alias", p.getProperty(sChartEl+".alias"));
		    pStep2.modify("xlabel", p.getProperty(sChartEl+".xlabel"));		    
		    pStep2.modify("ylabel", p.getProperty(sChartEl+".ylabel"));		    
		    
		    String sChartType = p.getProperty(sChartEl+".type");
		    pStep2.modify(p.getProperty(sChartEl+".type"), "selected");
		    
		    if("histogram".equals(sChartType))
			pStep2.modify("histogram_initial_value", p.getProperty(sChartEl+".histogramtype"));
		    
		    pStep2.modify("autorange", "true".equals(p.getProperty(sChartEl+".autorange")) ? "checked" : "");		    
		    pStep2.modify("minx", p.getProperty(sChartEl+".minx"));		    
		    pStep2.modify("maxx", p.getProperty(sChartEl+".maxx"));		    		    
		    pStep2.modify("miny", p.getProperty(sChartEl+".miny"));		    		    
		    pStep2.modify("maxy", p.getProperty(sChartEl+".maxy"));		    		    
		}
		else{
		    //autorange default
		    pStep2.modify("autorange", "checked");
		    pStep2.modify("alias", "Default Chart Title");
		    pStep2.modify("xlabel", "Time");
		    pStep2.modify("ylabel", "Y Label");
		    
		    pStep2.comment("com_back", false);
		}
		
		pStep2.modify("action", "Add");
		pStep2.modify("histogram_initial_value", "Choose Type");
		
		
		//info grafic
		Page pInfo = new Page(RES_PATH+"/graphic.res");
		
		pInfo.modify("title", p.getProperty("title"));
		pInfo.modify("width", p.getProperty("width"));
		pInfo.modify("height", p.getProperty("height"));
		pInfo.modify("samerange", "true".equals(p.getProperty("samerange")) ? "YES" : "NO");
		pInfo.modify("dropybelow", p.getProperty("dropy_below"));
		pInfo.modify("dropyabove", p.getProperty("dropy_above"));
		
		pInfo.modify("prop", sProp);
		
		pStep2.modify("info", pInfo);
		
		pMaster.append(pStep2);
	    }
	    
	    break;
	case 3:
	    sProp = request.getParameter("prop") == null ? "" : request.getParameter("prop");
	    String sChart = request.getParameter("chart") == null ? "" : request.getParameter("chart");

	    if(sProp.length() > 0 && sChart.length() > 0){
		Properties p = new Properties();
		p.load(new FileInputStream(new File(CONF_PATH+sProp+".properties")));

		Page pStep3 = new Page(RES_PATH+"/step3.res");
		
		//correlations
		Page pInfo = new Page(RES_PATH+"/graphic.res");
		
		pInfo.modify("title", p.getProperty("title"));
		pInfo.modify("width", p.getProperty("width"));
		pInfo.modify("height", p.getProperty("height"));
		pInfo.modify("samerange", "true".equals(p.getProperty("samerange")) ? "YES" : "NO");
		pInfo.modify("dropybelow", p.getProperty("dropy_below"));
		pInfo.modify("dropyabove", p.getProperty("dropy_above"));
		
		pInfo.modify("prop", sProp);
		
		pStep3.modify("info", pInfo);
		
		//chart
		Page pChart = new Page(RES_PATH+"/chart.res");
		
		pChart.modify("prop", sProp);
		
		pChart.modify("title", sChart);
		pChart.modify("alias", p.getProperty(sChart+".alias"));
		pChart.modify("xlabel", p.getProperty(sChart+".xlabel"));		    
		pChart.modify("ylabel", p.getProperty(sChart+".ylabel"));		    
		    
		String sChartType = p.getProperty(sChart+".type");
		
		if("histogram".equals(sChartType)){
		    sChartType += " : "+ p.getProperty(sChart+".histogramtype");
		}
		
    		pChart.modify("type", sChartType);
		
		String sChartAutorange = p.getProperty(sChart+".autorange");
		
		if("true".equals(sChartAutorange)){
		    sChartAutorange = "YES";
		}
		else{
		    sChartAutorange = "NO<br>X Min:"+p.getProperty(sChart+".minx")+" X Max:"+p.getProperty(sChart+".maxx")+"<br> Y Min:"+p.getProperty(sChart+".miny")+" Y Max:"+p.getProperty(sChart+".maxy");		

		}
		
		pChart.modify("autorange", sChartAutorange);
		
		pStep3.modify("charts", pChart);
		//end chart
		
		
		//list Series
		String sSeries = p.getProperty(sChart+".names");
		String sSerieEl = request.getParameter("serie") == null ? "" : request.getParameter("serie");

		if(sSeries != null){
		    Page pSeries = new Page(RES_PATH+"/series.res");
		    Page pSeriesEl = new Page(RES_PATH+"/series_el.res");
		    
		    StringTokenizer st = new StringTokenizer(sSeries, ",");

		    while(st.hasMoreElements()){
			String sSerie = (String) st.nextElement();
			
			if(!sSerie.equals(sSerieEl)){
			
			    pSeriesEl.modify("title", sSerie);
			    pSeriesEl.modify("alias", p.getProperty(sChart+"."+sSerie+".alias"));			
			    
			    String sPredicates = p.getProperty(sChart+"."+sSerie+".pred") == null ? "" : p.getProperty(sChart+"."+sSerie+".pred");
			    
			    StringTokenizer st_pred = new StringTokenizer(sPredicates, ",");
			    
			    while(st_pred.hasMoreTokens()){
				String sPredicateEl = st_pred.nextToken();
				pSeriesEl.append("predicate", sPredicateEl+"<br>");
			    }
			    
			    
			    pSeriesEl.modify("prop", sProp);
			    pSeriesEl.modify("chart", sChart);
			
			    pSeries.append(pSeriesEl);
			}
		    }
		    
		    pSeries.modify("prop", sProp);
		    pSeries.modify("chart", sChart);
		    pSeries.modify("alias",  p.getProperty(sChart+".alias"));
		    
		    if(sSerieEl.length() == 0)
			pSeries.comment("com_view", false);	
		    
		    pStep3.modify("series", pSeries);
		}
		
		if(sSerieEl.length() > 0){
		    pStep3.modify("action", "Modify");
		
		    pStep3.modify("disabled", "disabled");
		    
		    pStep3.modify("title", sSerieEl);
		    pStep3.modify("alias", p.getProperty(sChart+"."+sSerieEl+".alias"));
		    
		    
		    String sPredicates = p.getProperty(sChart+"."+sSerieEl+".pred") == null ? "" : p.getProperty(sChart+"."+sSerieEl+".pred");
			    
		    StringTokenizer st_pred = new StringTokenizer(sPredicates, ",");
		
		    int iCnt = 1;
		     
		    while(st_pred.hasMoreTokens()){
			String sPredicateEl = st_pred.nextToken();
			
			if(iCnt == 1){
			    pStep3.modify("predicate_1", sPredicateEl);
			}
			else{
			    Page pPredicatePage = new Page(RES_PATH+"/predicate_el.res");
			    pPredicatePage.modify("cnt", iCnt);
			    pPredicatePage.modify("value", sPredicateEl);
			    
			    pStep3.append("predicates_list", pPredicatePage);
			}
			
			iCnt++;
		    }
		    
		    if(iCnt > 2)
			pStep3.modify("predicate_display", "inline");
		    else
			pStep3.modify("predicate_display", "none");
		    
		    String sFunction = p.getProperty(sChart+"."+sSerieEl+".function") == null ? "" : p.getProperty(sChart+"."+sSerieEl+".function");
		    pStep3.modify(sFunction, "selected");
		}
		else{
		    pStep3.comment("com_back", false);
		    
		    pStep3.modify("alias", "Default Series Title");
		}
		
		pStep3.modify("action", "Add");
		pStep3.modify("predicate_display", "none");
		
		pStep3.modify("prop", sProp);
		pStep3.modify("title_chart", p.getProperty(sChart+".alias"));
		pStep3.modify("chart", sChart);

		pMaster.append(pStep3);
	    }
	    
	    break;
	default:
	    break;
    }

    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/correlations/correlations.jsp", baos.size(), request);
%>
