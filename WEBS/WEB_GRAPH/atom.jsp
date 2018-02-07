<%@ page import="alimonitor.*,lia.Monitor.Store.Fast.DB,lia.Monitor.Store.Cache,lia.web.utils.Formatare,lia.Monitor.monitor.monPredicate,java.util.*,java.io.*,java.text.DateFormat,java.text.SimpleDateFormat,lia.web.servlets.web.*,lazyj.*,lia.web.utils.Annotations,lia.web.utils.Annotation" %><%
    if (!lia.web.utils.ThreadedPage.acceptRequest(request, response))
	return;

    lia.web.servlets.web.Utils.logRequest("START /atom.jsp", 0, request);
    
    final RequestWrapper rw = new RequestWrapper(request);
    
    final String sGroups = rw.gets("set");
    final String sSite = rw.gets("site");
    final int iPage = rw.geti("page");

    CachingStructure cs = PageCache.get(request, null);
    
    String sPage="atom.jsp";
    boolean bQM = false;
    
    if (sGroups!=null && sGroups.length()>0){
	sPage += "?set="+sGroups;
	bQM = true;
    }
	
    if (sSite!=null && sSite.length()>0){
	sPage += (bQM ? "&" : "?") + "site="+sSite;
	bQM = true;
    }
    
    if (cs!=null){
	out.write(cs.getContentAsString());
	out.flush();
	
	lia.web.servlets.web.Utils.logRequest(sPage+(bQM ? "&" : "?")+"cache=true", cs.length(), request);
	
	return;
    }

    response.setContentType("application/atom+xml");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    
    DateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    f.setTimeZone(TimeZone.getTimeZone("UTC"));

    final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    ServletContext sc = getServletContext();
    final String SITE_BASE = sc.getRealPath("/");
 
    final String BASE_PATH=SITE_BASE+"/";
    
    final String RES_PATH=SITE_BASE+"/WEB-INF/res";
        
    ByteArrayOutputStream baos = new ByteArrayOutputStream(30000);
            
    Page pMaster = new Page(baos, "atom.res");

    pMaster.modify("subtitle", "All events recorded by MonALISA");

    Page p = new Page("atom_entry.res");

    DB db = new DB("SELECT * FROM annotation_groups;");
    
    TreeMap<Integer,String> tmGroups = new TreeMap<Integer,String>();
    
    while (db.moveNext()){
	tmGroups.put(db.geti("ag_id"), db.gets("ag_name"));
    }
    
    final TreeMap<Integer,String> tmGroupLinks = new TreeMap<Integer, String>();

    tmGroupLinks.put(1, "display?page=jobStatusSites_RUNNING");
    tmGroupLinks.put(2, "display?page=jobStatusCS_run_params");
    tmGroupLinks.put(3, "display?page=FTD/SE");
    tmGroupLinks.put(6, "stats?page=services_status");
    tmGroupLinks.put(7, "stats?page=machines/machines");
    tmGroupLinks.put(8, "stats?page=proxies");
    tmGroupLinks.put(9, "stats?page=ups/ups");
    tmGroupLinks.put(10, "stats?page=SE/table");
    
    final TreeSet tsGroups = new TreeSet();
    
    long lMaxTime = 0;
	
    final int iPageSize = 100;
	
    int iOffset = iPage * iPageSize;
    
    if (sGroups!=null && sGroups.length()>0){
	String vs[] = sGroups.split(",");
		
	for (int i=0; i<vs.length; i++){
	    try{
		Integer grp = Integer.valueOf(vs[i]);
	    
		tsGroups.add(grp);
		
		if (tmGroupLinks.containsKey(grp))
		    pMaster.modify("link_extra", tmGroupLinks.get(grp));
		    
		if (tmGroups.containsKey(grp))
		    pMaster.modify("title", tmGroups.get(grp)+(sSite!=null && sSite.length()>0 ? " - "+sSite : ""));
	    }
	    catch (Exception e){
	    }
	}
    }
	
    final List lAnnotations = Annotations.getAnnotations(0, System.currentTimeMillis(), tsGroups, iPageSize, iOffset, true);

    Collections.reverse(lAnnotations);
    
    final Iterator it = lAnnotations.iterator();
	    
    final long lNow = System.currentTimeMillis();

	    
    while (it.hasNext()){
	Annotation a = (Annotation) it.next();
		
	if (a.bValue)
	    continue;
	    
	if (sSite!=null && sSite.length()>0){
	    if (a.groups.contains(10) && a.groups.size()==1){
		boolean bFound = false;
	    
		final String sSearch = "alice::"+sSite.toLowerCase()+"::";
	    
		for (String s: (Set<String>)a.services){
		    if (s.toLowerCase().startsWith(sSearch)){
			bFound = true;
			break;
		    }
		}
		
		if (!bFound)
		    continue;
	    }
	    else
	    if (!a.services.contains(sSite))
	        continue;
	}
	    
	String gr = "";
	
	Iterator itgr = a.groups.iterator();
	
	while (itgr.hasNext()){
	    Integer group = (Integer) itgr.next();
	
	    String s = tmGroups.get( group );
	    
	    if (s!=null){
		if (gr.length()>0) gr += ", ";
		
		gr += s;
		
		if (tmGroupLinks.get(group)!=null)
		    p.modify("link_extra", tmGroupLinks.get(group));
	    }
	}
	
	if (gr.length()==0)
	    gr = "General announcement";
	
	String sg = a.services.size() > 0 ? a.services.toString() : "";

	String duration = sdf.format(new Date(a.from))+" - "+(a.to > lNow ? "<b><font color=red>continues</font></b>" : sdf.format(new Date(a.to))) + " (";
	
	long lDiff = (a.to > lNow ? lNow : a.to) - a.from;

        lDiff /= 60*1000;
			    
	if (lDiff < 60)
	    duration += lDiff+" min";
	else{
	    long h = lDiff/60;
			    
	    if (h>24){
	        duration += h/24+"d ";
	        h = h%24;
	    }
				
	    duration += h+"h";
				
	    if (lDiff%60>0)
	        duration += " "+lDiff%60+"m";
	}
				
	if (a.to > lNow)
	    duration += " until now";
	
	duration += ")";
	
	p.modify("id", a.id);
	    
	p.modify("title", gr+" : " + (sg.length()>0 ? sg+" - " : "") + a.text);
	p.modify("summary", "<b><i><font size=+1>"+gr+"</font></i></b><br>" + (sg.length()>0 ? "<b>"+sg+"</b><br>" : "") + a.text + "<br><br>Duration: "+duration);
	
	String sDescription = a.getDescription();
	if (sDescription==null)
	    sDescription = "";
	
	p.modify("content", "<b><i><font size=+1>"+gr+"</font></i></b><br>" + (sg.length()>0 ? "<b>"+sg+"</b><br>" : "") + (sDescription.length()>0 ? sDescription : a.text) + 
				"<br><br><b>Duration</b>: "+duration);
	
	p.modify("publish_time", f.format(new Date(a.from)));
	p.modify("update_time", f.format(new Date(a.to > lNow ? a.from : a.to)));
	
	if (a.from > lMaxTime)
	    lMaxTime = a.from;
	    
	if (a.to < lNow && a.to > lMaxTime)
	    lMaxTime = a.to;
	
	p.modify("category", gr);
	    
	pMaster.append(p);
    }
    

    int iLastPage = (lAnnotations.size()-1)/iPageSize;

    String sFirstPage = sPage;
    String sLastPage  = sPage+(lAnnotations.size()>iPageSize ? (bQM ? "&" : "?") + "page="+iLastPage : "");
    String sNextPage  = sPage+ (bQM ? "&" : "?") + "page="+(iPage < iLastPage ? iPage+1 : iLastPage);
    String sPrevPage  = sPage+(iPage > 1 ? (bQM ? "&" : "?") + "page="+(iPage-1) : "");

    pMaster.append("navlinks", "<link rel=\"first\" href=\"http://alimonitor.cern.ch/"+sFirstPage+"\"/>\n");
    pMaster.append("navlinks", "<link rel=\"last\" href=\"http://alimonitor.cern.ch/"+sLastPage+"\"/>\n");
    pMaster.append("navlinks", "<link rel=\"next\" href=\"http://alimonitor.cern.ch/"+sNextPage+"\"/>\n");
    pMaster.append("navlinks", "<link rel=\"prev\" href=\"http://alimonitor.cern.ch/"+sPrevPage+"\"/>\n");

    // fallback, in case no group parameter was specified
    pMaster.modify("title", "All events");
    
    pMaster.modify("update_time", f.format(new Date(lMaxTime)));

    pMaster.write();
            
    cs = PageCache.put(request, null, baos.toByteArray(), 600*1000, "text/html");
    
    out.write(cs.getContentAsString());
    
    lia.web.servlets.web.Utils.logRequest(sPage+(bQM ? "&" : "?")+"cache=false", cs.length(), request);
%>