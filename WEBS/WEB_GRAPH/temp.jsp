<%@ page import="lia.web.servlets.web.display, alimonitor.*,lia.web.utils.Formatare,java.util.*,java.io.*,lia.Monitor.monitor.*,lia.Monitor.Store.Cache,lia.Monitor.Store.Fast.DB" %><%
    lia.web.servlets.web.Utils.logRequest("START /temp.jsp", 0, request);

    lia.Monitor.Store.Fast.TempMemWriterInterface tmw = null;
    
    String sError = "";

    try{
        lia.Monitor.Store.TransparentStoreFast store = (lia.Monitor.Store.TransparentStoreFast) lia.Monitor.Store.TransparentStoreFactory.getStore();
	tmw = store.getTempMemWriter();
    }
    catch (Throwable t){
	sError = t.getMessage();
    }
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream(25000);
            
    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage.res");
    pMaster.comment("com_alternates", false);
    pMaster.modify("comment_refresh", "");
    pMaster.modify("refresh_time", "600");
    
    pMaster.modify("title", "Temperature sensors");
                
    Page pInfo = new Page("temp.res");

    // ---------------------------
    
    String sSite = request.getParameter("site");
    
    DB db = new DB("SELECT distinct split_part(mi_key,'/',1) FROM monitor_ids WHERE split_part(mi_key,'/',2)='Master' AND split_part(mi_key,'/',4) LIKE 'lm_%' AND split_part(mi_key,'/',1)!='CERN';");
    
    while (db.moveNext()){
	String s = db.gets(1);
    
	if (sSite==null || sSite.length()==0)
	    sSite = s;
	    
	pInfo.append("sitesel", "<option value='"+s+"'"+(s.equals(sSite) ? " selected" : "")+">"+s+"</option>");
    }

    monPredicate mpSensors = new monPredicate(sSite, "Master", "%", -1, -1, new String[]{"lm_%_value"}, null);
    monPredicate mpUnit    = new monPredicate(sSite, "Master", "%", -1, -1, new String[1], null);
    
    db.query("SELECT * FROM temp_assoc WHERE site='"+Formatare.mySQLEscape(sSite)+"';");
    
    String sSensorTempCPU   = "";
    String sSensorTempBoard = "";
    String sSensorFan = "";
    String sSensorVolt = "";
    
    double dValueCPU = -1;
    double dValueBoard = -1;
    double dValueFan = -1;
    double dValueVolt = 0;
    
    while (db.moveNext()){
	int iChart = db.geti("chart");
	String s2 = db.gets("sensor");
	
	switch (iChart){
	    case 1 : sSensorTempCPU = s2; break;
	    case 2 : sSensorTempBoard = s2; break;
	    case 3 : sSensorFan = s2; break;
	    case 4 : sSensorVolt = s2; break;
	}
    }

    if (sSensorTempCPU.length()==0)
	sSensorTempCPU = "temp1";

    if (sSensorTempBoard.length()==0)
	sSensorTempBoard = "temp2";
	
    if (sSensorFan.length()==0)
	sSensorFan = "fan1";

    if (sSensorVolt.length()==0)
	sSensorVolt = "12V";

    Vector v = Cache.getLastValues(mpSensors);
    
    if (v.size()==0){
	pInfo.modify("message", "Site "+sSite+" doesn't currently report any sensor data.");
    }
    else{
	TreeSet<String> tsFans = new TreeSet<String>();
	TreeSet<String> tsVolt = new TreeSet<String>();
	TreeSet<String> tsTemp = new TreeSet<String>();
	TreeSet<String> tsOth  = new TreeSet<String>();
		
	for (int i=0; i<v.size(); i++){
	    Result r = (Result) v.get(i);
		    
	    double d = r.param[0];
		    
	    String sName = r.param_name[0];
		    
	    sName = sName.substring(3, sName.lastIndexOf("_"));
		    
	    mpUnit.parameters[0] = "lm_"+sName+"_unit";
	    
	    eResult er = (eResult) Cache.getLastValue(mpUnit);
		    
	    String sUnit = er!=null ? (String) er.param[0] : "";
		    
	    if (sUnit.indexOf("RPM")>=0){
		String s = "<tr bgcolor=white><td align=left nowrap>"+sName+"</td>"+
		    "<td nowrap align=right>"+d+" RPM</td>"+
		    "<td nowrap align=left><a href=\"/display?page=sensors&Farms="+sSite+"&Functions="+r.param_name[0]+"\">hist</a>";
		
		if (sName.equals(sSensorFan)){
		    dValueFan = d;
		}
		else{
		    s += " <a href='tempadd.jsp?site="+sSite+"&chart=3&sensor="+sName+"' onMouseOver=\"overlib('Display this sensor in the monitor below');\" onMouseOut='return nd();'>F</a>";
		}
		
		s+="</td></tr>";
	    
		tsFans.add(s);
	    }
	    else
	    if (sUnit.indexOf("V")>=0){
		String s = "<tr bgcolor=white><td align=left nowrap>"+sName+"</td>"+
		    "<td nowrap align=right>"+d+" V</td>"+
		    "<td nowrap align=left><a href=\"/display?page=sensors&Farms="+sSite+"&Functions="+r.param_name[0]+"\">hist</a>";
		
		if (sName.equals(sSensorVolt)){
		    dValueVolt = d;
		}
		else{
		    s += " <a href='tempadd.jsp?site="+sSite+"&chart=4&sensor="+sName+"' onMouseOver=\"overlib('Display this sensor in the monitor below');\" onMouseOut='return nd();'>V</a>";
		}
		
		s+="</td></tr>";
	    
		tsVolt.add(s);
	    }
	    else
	    if (sUnit.indexOf("C")>=0){
		String s = "<tr bgcolor=white><td align=left nowrap>"+sName+"</td>"+
		    "<td nowrap align=right>"+d+" C</td>"+
		    "<td nowrap align=left><a href=\"/display?page=sensors&Farms="+sSite+"&Functions="+r.param_name[0]+"\">hist</a>";
		
		if (sName.equals(sSensorTempCPU)){
		    dValueCPU = d;
		}
		else{
		    s += " <a href='tempadd.jsp?site="+sSite+"&chart=1&sensor="+sName+"' onMouseOver=\"overlib('Display this sensor in the monitor below');\" onMouseOut='return nd();'>C</a>";
		}
		
		s+="</td></tr>";
	    
		tsTemp.add(s);
	    }
	    else
		tsOth.add("<tr bgcolor=white><td align=left nowrap>"+sName+"</td><td nowrap align=right>"+d+" "+sUnit+"</td><td nowrap><a href=\"/display?page=sensors&Farms="+sSite+"&Functions="+r.param_name[0]+"\">hist</a></td></tr>");
	}

        if (tsFans.size()>0){
	    pInfo.append("<td valign=top align=center><table border=0 cellspacing=1 cellpadding=3 bgcolor=#AAAAAA><tr><th colspan=3 bgcolor=#F8FFB8>FANS</th></tr><tr bgcolor=#CCCCCC><th>Name</th><th>Speed</th><th>Link</th></tr>");
	    
	    for (String s: tsFans)
		pInfo.append(s);
		    
	    pInfo.append("</table>");
		    
	    pInfo.append("<br><br><img src='/simple?page=sens/fan1&values="+dValueFan+"&title="+sSensorFan+"'>");
		    
	    pInfo.append("</td>");
	}
		
	if (tsTemp.size()>0){
	    pInfo.append("<td valign=top align=center><table border=0 cellspacing=1 cellpadding=3 bgcolor=#AAAAAA><tr><th colspan=3 bgcolor=#F8FFB8>Temperature</th></tr><tr bgcolor=#CCCCCC><th>Name</th><th>Temp</th><th>Link</th></tr>");
		    
	    for (String s: tsTemp)
		pInfo.append(s);
	    
	    pInfo.append("</table>");
		    
	    pInfo.append("<br><br><img src='/simple?page=sens/temp&values="+dValueCPU+"&title="+sSensorTempCPU+"'>");
		    
	    pInfo.append("</td>");
	}

	if (tsVolt.size()>0){
	    pInfo.append("<td valign=top align=center><table border=0 cellspacing=1 cellpadding=3 bgcolor=#AAAAAA><tr><th colspan=3 bgcolor=#F8FFB8>Voltage</th></tr><tr bgcolor=#CCCCCC><th>Name</th><th>Volts</th><th>Link</th></tr>");
	    
	    for (String s: tsVolt)
		pInfo.append(s);
	    
	    pInfo.append("</table>");
		    
	    pInfo.append("<br><br><img src='/simple?page=sens/12v&values="+dValueVolt+"&abs.min="+Math.round(dValueVolt-1)+"&abs.max="+Math.round(dValueVolt+1)+"&title="+sSensorVolt+"'>");
	    
	    pInfo.append("</td>");
	}

	if (tsOth.size()>0){
	    pInfo.append("<td valign=top align=center><table border=0 cellspacing=1 cellpadding=3 bgcolor=#AAAAAA><tr><th colspan=3 bgcolor=#F8FFB8>Other sensors</th></tr><tr bgcolor=#CCCCCC><th>Name</th><th>Value</th><th>Link</th></tr>");
	    
	    for (String s: tsOth)
		pInfo.append(s);
		    
	    pInfo.append("</table></td>");
	}
		
	//pInfo.append("<td valign=top><img src='/simple?page=sens/temp2'></td>");
    }

    // ---------------------------
    pMaster.append(pInfo);
    
    pMaster.modify("bookmark", "/temp.jsp?site="+sSite);
            
    pMaster.write();
    String s = new String(baos.toByteArray());
    out.println(s);                    
    
    lia.web.servlets.web.Utils.logRequest("/temp.jsp", baos.size(), request);
%>
