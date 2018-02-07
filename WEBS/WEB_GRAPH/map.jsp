<%@ page import="alimonitor.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.Formatare,java.util.Date,java.util.StringTokenizer,java.io.*,lia.web.utils.DoubleFormat,lia.Monitor.Store.Cache,lia.Monitor.monitor.*,lazyj.*" %><%
    lia.web.servlets.web.Utils.logRequest("START /map.jsp", 0, request);

    lia.Monitor.Store.Fast.TempMemWriterInterface tmw = null;
    
    RequestWrapper rw = new RequestWrapper(request);
    
    String sError = "";

    try{
        lia.Monitor.Store.TransparentStoreFast store = (lia.Monitor.Store.TransparentStoreFast) lia.Monitor.Store.TransparentStoreFactory.getStore();
	tmw = store.getTempMemWriter();
    }
    catch (Throwable t){
	sError = t.getMessage();
    }
    
    String sExtraPath = rw.gets("res_path");

    if (sExtraPath.length()>0)
	sExtraPath="/"+sExtraPath;
    
    final String RES_PATH="WEB-INF/res"+sExtraPath;

    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);

    Page pMaster = new Page(baos, RES_PATH+"/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "//");
    
    pMaster.modify("bookmark", "/map.jsp");

    pMaster.modify("title", "Grid sites map");
    
    pMaster.append("extrastyle", "<link href='/js/map/style.css' rel='stylesheet' type='text/css' />");
    
    boolean bLines = rw.getb("lines", false);
    boolean bRelations = rw.getb("relations", false);
    
    String sContinent = ""+rw.geti("continent");
    
    Page pMap = new Page(baos, "map.res");
    
    //check for the port
    int iPort = request.getServerPort();
        
    // default for 8889
    String sKey = "ABQIAAAAe-d2_jCXwlI-BLssWvahAhS4-P5_JKa8FJRjGfg_1cX76Csh0hR58jYUu91d4xY3f-z3rB3_l3-Eiw";
        
    if (request.getServerName().toLowerCase().startsWith("pc")){
	if (iPort == 80){
	    // new port
	    sKey = "ABQIAAAAe-d2_jCXwlI-BLssWvahAhSd8AOFs0Gsm6MWTlWRfqGbyBTlFBTMkoKHgMgwxHC4cx9XgUWcFCB2_w";
	}
	else
	if (iPort == 8443){
	    // ssl, old
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRRgUZa3JNsqa6eg5zl2ejpt7mYHcBSQHph51_nCkUAdhcj2tKg3R2z4cw";
	}
	else
	if (iPort == 443){
	    // ssl, new
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRTgHe3oHRaa_kAlFYrhy7bU6KLYgRSWlXmeVPuDqeauILWn7dAvObuDWA";
	}
    }
    else{
	if (iPort == 80){
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRRL7Fmk6FaYZspD2D8W7nUW2HI2WRQ0doxQ7KF7VJlTTSNNkEh1ZKLmog";
	}
	else
	if (iPort == 8443){
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRS361Cd4Up2LIqdCoOJ3ST4wkrS1xQFx8w__a1DWcgMzaZebrQECyF0qg";
	}
	else
	if (iPort == 8889){
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRSxTLN3zHhwX4Kpppo7HE0qnnVomxQLMG9moM0NUsFw2A7NOo3Z2tE_Xw";
	}
	else
	if (iPort == 443){
	    sKey = "ABQIAAAAYq5eZGR45iursVuiWp-gKRSfNUikpT2tSqs8WcKetSP_Ms-iwRQvaXL6Rdqo7MA87K3mSAOA2EJ89A";
	}
    }
    
    pMap.modify("map_key", sKey);
                                        
    pMap.modify("bLines", ""+bLines);
    pMap.modify("lines_checked", bLines ? "checked" : "");
    
    pMap.modify("bRelations", ""+bRelations);
    pMap.modify("relations_checked", bRelations ? "checked" : "");    
    
    pMap.modify("continent", sContinent);
    pMap.modify("continent_"+sContinent, "selected");

    pMap.modify("hoffset", rw.geti("hoffset"));
    pMap.modify("woffset", rw.geti("woffset"));
    
    pMap.modify("hsub", rw.geti("hsub", 270));
    pMap.modify("wsub", rw.geti("wsub", 240));
    
    pMap.modify("mapflags", rw.gets("flags"));
    
    pMaster.append(pMap);

    //comment on basepage
    pMaster.comment("com_basepage", false);

    pMaster.write();
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/map.jsp", baos.size(), request);
%>