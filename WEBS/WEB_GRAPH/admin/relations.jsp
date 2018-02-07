<%@ page import="lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.Monitor.Store.FarmBan,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.net.URLEncoder,lia.web.servlets.web.*,lia.web.utils.*,lia.Monitor.monitor.Result,lia.Monitor.monitor.eResult,lia.Monitor.JiniClient.Store.Main" %><%
    lia.web.servlets.web.Utils.logRequest("START /admin/relations.jsp", 0, request);

    ServletContext sc = getServletContext();
    final String SITE_BASE = sc.getRealPath("/");
 
    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
        
    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            
    Page pMaster = new Page(baos, RES_PATH+"/masterpage/masterpage_admin.res");
    
    pMaster.modify("title", "Site Administration");
    pMaster.modify("class_administration", "_active");

    Page pRelations = new Page(BASE_PATH+"/admin/relations.res");
    Page pRelationsEl = new Page(BASE_PATH+"/admin/relations_el.res");

    /** Parameters parsing*/
    Enumeration e = request.getParameterNames();
    

    DB db = new DB();

//    String sQuery = "SELECT name FROM abping_aliases WHERE geo_lat IS NOT NULL AND geo_long IS NOT NULL AND geo_lat!='N/A' AND geo_long!='N/A' " +
//		"AND name NOT IN (SELECT name FROM hidden_sites) ORDER BY name!='CERN' DESC, name;";

    String sQuery = "SELECT site FROM site_relations WHERE centertype <= 1 ORDER BY lower(site)";

    db.query(sQuery);

    ArrayList<String> al = new ArrayList();
    
    while(db.moveNext()){
	al.add(db.gets("site"));
    }
    
    //sQuery = "SELECT site_relations.*, abping_aliases.name FROM abping_aliases LEFT OUTER JOIN site_relations ON name=site "+
    //		    " WHERE geo_lat IS NOT NULL AND geo_long IS NOT NULL AND geo_lat!='N/A' AND geo_long!='N/A' AND "+
    //		    " name NOT IN (SELECT name FROM hidden_sites) AND name != 'CERN' ORDER BY name;";
    
    sQuery = "SELECT site_relations.*, googlemap.name FROM googlemap  LEFT OUTER JOIN site_relations ON name=site "+
	" WHERE geo_lat IS NOT NULL AND geo_long IS NOT NULL AND geo_lat!='N/A' AND geo_long!='N/A' AND "+
	" name != 'CERN' ORDER BY lower(name);";
		    
    db.query(sQuery);
    
    String sDestination = "";
    
    while(db.moveNext()){
	pRelationsEl.modify("source", db.gets("name"));
	
	pRelationsEl.modify("color", db.gets("color").length() > 0 ? db.gets("color") : "FFFFFF");
	pRelationsEl.modify("color_value", db.gets("color").length()==0 ? "" : db.gets("color"));	

	pRelationsEl.modify("centertype", db.geti("centertype"));
	
	for(int i=0; i<al.size(); i++){
	    if(!db.gets("name").equals(al.get(i)))
		sDestination += "<option value=\""+al.get(i)+"\" "+(db.gets("connectedto").equals(al.get(i)) ? "selected" : "") +">"+al.get(i)+"</option>";
	}    	    
    	    
    	pRelationsEl.modify("destination", sDestination);
		
	sDestination = "";
	
	pRelations.append(pRelationsEl);
    }
    
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    response.setContentType("text/html");
    response.setHeader("Admin", "Costin Grigoras <costin.grigoras@cern.ch>");

    pMaster.append(pRelations);

    pMaster.write();
    
    String s = new String(baos.toByteArray());
    out.println(s);
            
    lia.web.servlets.web.Utils.logRequest("/admin/relations.jsp", s.length(), request);
%>
