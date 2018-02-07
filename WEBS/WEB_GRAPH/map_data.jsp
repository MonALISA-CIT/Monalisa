<%@ page import="alimonitor.*,lia.web.servlets.web.*,lia.web.utils.Formatare,java.util.Date,java.util.StringTokenizer,java.io.*,lia.web.utils.DoubleFormat,lia.Monitor.Store.Cache,lia.Monitor.monitor.*,lia.Monitor.Store.Fast.DB,lia.util.ntp.NTPDate,lazyj.*,java.util.*" %><%!
    final java.util.Random r = new java.util.Random(System.currentTimeMillis());
    
    boolean getGreen(final boolean bGreen, final int iPercentage){
	if (!bGreen)
	    return false;
    
	if (iPercentage >= 100)
	    return true;
	
	if (iPercentage <= 0)
	    return false;
	
	if (r.nextInt(100) < (100 - iPercentage))
	    return false;
	
	return true;
    }
%><%
    lia.web.servlets.web.Utils.logRequest("START /map_data.jsp", 0, request);

    RequestWrapper.setNotCache(response);
    response.setHeader("Connection", "close");
    response.setHeader("Content-Language", "en");
    response.setContentType("text/xml; charset=UTF-8");

    RequestWrapper rw = new RequestWrapper(request);

    CachingStructure cs = null; //PageCache.get(request, null);
    
    if (cs!=null){
	out.write(cs.getContentAsString());
	out.flush();
	
	lia.web.servlets.web.Utils.logRequest("map_data.jsp?cache=true", cs.length(), request);
	
	return;
    }

    lia.Monitor.Store.Fast.TempMemWriterInterface tmw = null;
    
    String sError = "";

    try{
        lia.Monitor.Store.TransparentStoreFast store = (lia.Monitor.Store.TransparentStoreFast) lia.Monitor.Store.TransparentStoreFactory.getStore();
	tmw = store.getTempMemWriter();
    }
    catch (Throwable t){
	sError = t.getMessage();
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);

    Page pMap = new Page(baos, "map_data.res");
    
    String sQuery = "select * from googlemap";
    DB db = new DB();
    DB db2 = new DB();

    db.query(sQuery);
    
    Page pMarker = new Page("map_data_marker.res");
    Page pLine = new Page("map_data_line.res");
    
    monPredicate predOnline  = new monPredicate("*", "MonaLisa", "localhost", -1, -1, new String[]{"CurrentParamNo"}, null);
    monPredicate predRunning = new monPredicate("CERN", "ALICE_Sites_Jobs_Summary", "*", -1, -1, new String[]{"RUNNING_jobs"}, null);
    monPredicate predCEInfo = new monPredicate("*", "AliEnServicesStatus", "CE", -1, -1, new String[]{"Info"}, null);

    boolean bGreen = false;
    
    int iPercentage = 100;
    
    StringTokenizer st = new StringTokenizer(rw.gets("flags"), ",");
    
    while (st.hasMoreTokens()){
	String sFlag = st.nextToken().trim().toLowerCase();
	
	if (sFlag.equals("green")){
	    bGreen = true;
	    continue;
	}
	
	try{
	    iPercentage = Integer.parseInt(sFlag);
	}
	catch (Exception e){
	    // ignore
	}
    }
    
    db2.query("select max(maxrunningjobs) from running_jobs_cache;");
    
    final int iAbsMaxJobs = db2.geti(1);
    
    while(db.moveNext()){
	pMarker.modify("lat", db.gets("geo_lat"));
	pMarker.modify("long", db.gets("geo_long"));
	pMarker.modify("name", db.gets("name"));
	pMarker.modify("alias", db.gets("alias"));
	pMarker.modify("iconx", db.geti("iconx", 0));
	pMarker.modify("icony", db.geti("icony", 0));
	pMarker.modify("labelx", db.geti("labelx", 5));
	pMarker.modify("labely", db.geti("labely", -25));

	final String sName = db.gets("name");

	db2.query("SELECT name FROM hidden_sites WHERE name LIKE '"+Format.escSQL(sName)+"-%' OR name LIKE '"+Format.escSQL(sName)+"\\\\_%';");
	
	Set<String> names = new HashSet<String>();
	names.add(sName);
	
	while (db2.moveNext()){
	    names.add(db2.gets(1));
	}
	
	//if (names.size()>1)
	//    System.err.println(names);
	
	final boolean bForceGreen = getGreen(bGreen, iPercentage);
	
	boolean bOnline = bForceGreen;
	
	boolean bRunning = bForceGreen || sName.equals("NDGF");
	
	int iRunningJobs = 0;
	
	String sCEInfo = null;
	
	for (String mlname: names){
	    predCEInfo.Farm = predOnline.Farm = predRunning.Node = mlname;

	    Result r = (Result) Cache.getLastValue(predOnline);
	
	    bOnline = bOnline || (r!=null && r.time > NTPDate.currentTimeMillis() - 600000);
	
	    r = (Result) Cache.getLastValue(predRunning);
	
	    bRunning = bRunning || (r!=null && r.time > NTPDate.currentTimeMillis() - 600000 && r.param[0] > 0.5);
	
	    if (r!=null)
		iRunningJobs += (int) Math.round(r.param[0]);
	
	    if (sCEInfo == null || mlname.equals(sName)){
	        final Object oCEInfo = Cache.getLastValue(predCEInfo);
		
    		if (oCEInfo!=null && (oCEInfo instanceof eResult)){
		    final eResult e = (eResult) oCEInfo;
		    
		    sCEInfo = (String) e.param[0];
		}
	    }
	}
	
	int iMaxJobs = db.geti("maxrunningjobs");
	
	if (iMaxJobs < iRunningJobs){
	    db2.syncUpdateQuery("update running_jobs_cache set maxrunningjobs="+iRunningJobs+" where name='"+Format.escSQL(predOnline.Farm)+"';");
	
	    if (db2.getUpdateCount()==0){
	        db2.syncUpdateQuery("insert into running_jobs_cache (name, maxrunningjobs) values ('"+Format.escSQL(predOnline.Farm)+"', "+iRunningJobs+");");
	    }
	
	    iMaxJobs = iRunningJobs;
	}
	
	if (bForceGreen)	
	    iRunningJobs = iMaxJobs;
	
	pMarker.modify("nr_jobs",  iRunningJobs);
	pMarker.modify("max_jobs", iMaxJobs);
	
	String sImage;
	
	if (bRunning){
	    if (!bOnline){
		sImage = "orange.png";
	    }
	    else
	    if (iRunningJobs==0){
		// fake sites
		sImage = "green.png";
	    }
	    else{
		// pie charts
	    	
	    	sImage = "pie.jsp?a="+((360*(iMaxJobs-iRunningJobs)) / iMaxJobs)+"&amp;c=";

		if (sCEInfo!=null && sCEInfo.indexOf("No job matched your")>=0)
		    sImage += "1";
		
		int iSize = (int) (Math.log(iMaxJobs)*4 / Math.log(iAbsMaxJobs))+11;
		
		sImage += "&amp;s="+iSize;
	    }
	}
	else{
	    if (!bOnline){
		sImage = "red.png";
	    }
	    else{
		sImage = "yellow.png";
	    
		if (sCEInfo!=null && sCEInfo.indexOf("No job matched your")>=0)
		    sImage = "blue.png";
	    }
	}
	
	pMarker.modify("color", sImage);

	pMap.append("markers", pMarker);
    }
    
    String sLines = request.getParameter("lines") == null ? "false" : request.getParameter("lines");
    String sRelations = request.getParameter("relations") == null ? "false" : request.getParameter("relations");    
    
    if(sLines.equals("true")){
	db.query("select src.geo_lat as src_lat,src.geo_long as src_long,dest.geo_lat as dest_lat,dest.geo_long as dest_long from (active_xrootd_transfers inner join abping_aliases src on source=src.name) inner join abping_aliases dest on destination=dest.name;");
    
	while (db.moveNext()){
	    pLine.modify("line", "line_xrootd");
	    pLine.modify("src_lat", db.gets("src_lat"));
	    pLine.modify("src_long", db.gets("src_long"));
	
	    pLine.modify("dest_lat", db.gets("dest_lat"));
	    pLine.modify("dest_long", db.gets("dest_long"));
	
	    pLine.modify("color", "00F9F9");

	    pMap.append("lines", pLine);	
	}
    }
    
    if(sRelations.equals("true")){
	db.query("select * from site_relations_googlemap;");
    
	while (db.moveNext()){
	    pLine.modify("line", "line_relations");
	    pLine.modify("src_lat", db.gets("src_lat"));
	    pLine.modify("src_long", db.gets("src_long"));
	
	    pLine.modify("dest_lat", db.gets("dest_lat"));
	    pLine.modify("dest_long", db.gets("dest_long"));
	
	    pLine.modify("color", db.gets("color"));

	    pMap.append("lines", pLine);	
	}
    }
        
    pMap.write();
    
    cs = PageCache.put(request, null, baos.toByteArray(), 120*1000, "text/xml");
    
    out.write(cs.getContentAsString());
    out.flush();
    
    lia.web.servlets.web.Utils.logRequest("/map_data.jsp", cs.length(), request);
%>