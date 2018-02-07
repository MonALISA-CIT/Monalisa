<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.*,lia.Monitor.Store.*"%><%
    lia.web.servlets.web.Utils.logRequest("START /correlations/pick_pred.jsp", 0, request);

    String server = 
	request.getScheme()+"://"+
	request.getServerName()+":"+
	request.getServerPort()+"/";
	
    ServletContext sc = getServletContext();
    
    final String SITE_BASE = sc.getRealPath("/");

    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/correlations";

    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);

    Page pMaster = new Page(baos, BASE_PATH+"/WEB-INF/res/masterpage/masterpage_empty.res");

    pMaster.modify("title", "Predicate picker");
    
    String sPredStart = request.getParameter("pred_start")==null ? "" : request.getParameter("pred_start");

    monPredicate pred = Formatare.toPred(sPredStart);    
    
    //
    
    String sPredicate = request.getParameter("pred")==null ? "" : request.getParameter("pred");
    String sPredId = request.getParameter("pred_id")==null ? "" : request.getParameter("pred_id");
    
    if (sPredicate.length()==0){
	// first step : select predicate from the list of available series
	
	Page p = new Page(RES_PATH+"/pick_pred.res");
	p.modify("pred_id", sPredId);

	String stmin = request.getParameter("tmin")==null ? (pred!=null ? ""+pred.tmin : "") : request.getParameter("tmin");
	String stmax = request.getParameter("tmax")==null ? (pred!=null ? ""+pred.tmax : "") : request.getParameter("tmax");
        
	p.modify("tmin", stmin);
	p.modify("tmax", stmax);
    
        String sFilterFarm = request.getParameter("filter_farm")==null ? (pred!=null && pred.Farm!=null ? pred.Farm : "") : request.getParameter("filter_farm");
        String sFilterCluster = request.getParameter("filter_cluster")==null ? (pred!=null && pred.Cluster!=null ? pred.Cluster : "") : request.getParameter("filter_cluster");
        String sFilterNode = request.getParameter("filter_node")==null ? (pred!=null && pred.Node!=null ? pred.Node : "") : request.getParameter("filter_node");
        String sFilterParameter = request.getParameter("filter_parameter")==null ? (pred!=null && pred.parameters!=null && pred.parameters.length>=1 ? pred.parameters[0] : "") : request.getParameter("filter_parameter");
        
        if ("*".equals(sFilterCluster))
    	    sFilterCluster="";
    	    
        if ("*".equals(sFilterNode))
    	    sFilterNode="";
        
        String sWhere = "";
        String sWhereFarm = "";
        String sWhereCluster = "";
        String sWhereNode = "";
        String sWhereParameter = "";
        
        if (sFilterFarm.length()>0){
    	    sWhere = sWhereCluster = sWhereNode = sWhereParameter = "split_part(mi_key, '/', 1)='"+Formatare.mySQLEscape(sFilterFarm)+"'";
        }
    
        if (sFilterCluster.length()>0){
    	    String s = "split_part(mi_key, '/', 2)='"+Formatare.mySQLEscape(sFilterCluster)+"'";
        
    	    sWhere += (sWhere.length()>0 ? " AND " : "") + s;

    	    sWhereFarm = s;
    	    sWhereNode += (sWhereCluster.length()>0 ? " AND " : "") + s;
    	    sWhereParameter += (sWhereParameter.length()>0 ? " AND " : "") + s;
        }

        if (sFilterNode.length()>0){
    	    String s = "split_part(mi_key, '/', 3)='"+Formatare.mySQLEscape(sFilterNode)+"'";
        
    	    sWhere += (sWhere.length()>0 ? " AND " : "") + s;
    	    
    	    sWhereFarm += (sWhereFarm.length()>0 ? " AND " : "") + s;
    	    sWhereCluster += (sWhereCluster.length()>0 ? " AND " : "") + s;
    	    sWhereParameter += (sWhereParameter.length()>0 ? " AND " : "") + s;
        }

        if (sFilterParameter.length()>0){
    	    String s = "split_part(mi_key, '/', 4)='"+Formatare.mySQLEscape(sFilterParameter)+"'";
        
    	    sWhere += (sWhere.length()>0 ? " AND " : "") + s;

    	    sWhereFarm += (sWhereFarm.length()>0 ? " AND " : "") + s;
    	    sWhereCluster += (sWhereCluster.length()>0 ? " AND " : "") + s;
    	    sWhereNode += (sWhereNode.length()>0 ? " AND " : "") + s;
        }
        
        if (sWhere.length()>0)
    	    sWhere = " WHERE "+sWhere+" ";
    	    
    	if (sWhereFarm.length()>0)
    	    sWhereFarm = " WHERE "+sWhereFarm + " ";

    	if (sWhereCluster.length()>0)
    	    sWhereCluster = " WHERE "+sWhereCluster + " ";

    	if (sWhereNode.length()>0)
    	    sWhereNode = " WHERE "+sWhereNode + " ";

    	if (sWhereParameter.length()>0)
    	    sWhereParameter = " WHERE "+sWhereParameter+ " ";
        
	DB db = new DB();
	
	db.query("SELECT name FROM (SELECT distinct split_part(mi_key,'/',1) AS name FROM monitor_ids"+sWhereFarm+") AS x ORDER BY lower(name) ASC;");
	while (db.moveNext()){
	    String s = db.gets(1);
	
	    p.append("opt_farm", "<option value='"+s+"' "+(s.equals(sFilterFarm) ? "selected" : "")+">"+s+"</option>");
	}

	db.query("SELECT distinct split_part(mi_key,'/',2) FROM monitor_ids"+sWhereCluster+";");
	while (db.moveNext()){
	    String s = db.gets(1);
	
	    p.append("opt_cluster", "<option value='"+s+"' "+(s.equals(sFilterCluster) ? "selected" : "")+">"+s+"</option>");
	}
	
	db.query("SELECT distinct split_part(mi_key,'/',3) FROM monitor_ids"+sWhereNode+";");
	while (db.moveNext()){
	    String s = db.gets(1);
	
	    p.append("opt_node", "<option value='"+s+"' "+(s.equals(sFilterNode) ? "selected" : "")+">"+s+"</option>");
	}

	db.query("SELECT distinct split_part(mi_key,'/',4) FROM monitor_ids"+sWhereParameter+";");
	while (db.moveNext()){
	    String s = db.gets(1);
	
	    p.append("opt_parameter", "<option value='"+s+"' "+(s.equals(sFilterParameter) ? "selected" : "")+">"+s+"</option>");
	}

	db.query("select count(1) from monitor_ids "+sWhere+";");
	
	int iTotal = db.geti(1);
	
	p.modify("totalmatch", iTotal);
	
	db.query("select mi_key from monitor_ids "+sWhere+" order by lower(mi_key) asc LIMIT 500;");
	
	Page pPred = new Page(RES_PATH+"/pick_pred_line.res");
	
	while (db.moveNext()){
	    String sKey = db.gets(1);
	    
	    Object o = Cache.getLastValue(Formatare.toPred(sKey));
	    
	    if (o!=null && (o instanceof Result)){
		Result r = (Result) o;
		
		pPred.modify("value", DoubleFormat.point(r.param[0]));
	    }
	    else{
		pPred.modify("value", "-");
	    }
	    
	    pPred.modify("pred", sKey);
	    pPred.modify("pred_id", sPredId);
	    pPred.modify("tmin", stmin);
	    pPred.modify("tmax", stmax);
	    
	    p.append(pPred);
	}
	
	pMaster.append(p);
    }
    else{
	int tmin=0;
	int tmax=0;
	
	boolean bTimeSel = true;
	
	try{
	    tmin = Integer.parseInt(request.getParameter("tmin_other"));
	}
	catch (Exception e){
	    try{
		tmin = Integer.parseInt(request.getParameter("tmin"));
	    }
	    catch (Exception e2){
		bTimeSel = false;
	    }
	}

	try{
	    tmax = Integer.parseInt(request.getParameter("tmax_other"));
	}
	catch (Exception e){
	    try{
		tmax = Integer.parseInt(request.getParameter("tmax"));
	    }
	    catch (Exception e2){
		bTimeSel = false;
	    }
	}
	
	if (bTimeSel) {
	    // last step : display the selected predicate
	    
	    Page p = new Page(RES_PATH+"/pick_pred_final.res");

	    String sFCN = sPredicate.substring(0, sPredicate.lastIndexOf("/"));
	    String sP = sPredicate.substring(sPredicate.lastIndexOf("/")+1);
	    
	    sPredicate = sFCN+"/"+tmin+"/"+tmax+"/"+sP;
	    
	    p.modify("pred", sPredicate);
	    p.modify("pred_id", sPredId);	    
	    
	    pMaster.append(p);
	}
	else{
	    // next step : select time constraints
	
	    Page p = new Page(RES_PATH+"/pick_pred_time.res");
	
	    p.modify("pred", sPredicate);
	    p.modify("pred_id", sPredId);
	    
	    p.modify("tmin", request.getParameter("tmin_start"));
	    p.modify("tmax", request.getParameter("tmax_start"));
	
	    pMaster.append(p);
	}
    }
    
    //
    
    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
    
    lia.web.servlets.web.Utils.logRequest("/correlations/pick_pred.jsp", baos.size(), request);
%>