<%@ page import="lazyj.*,lia.Monitor.Store.Fast.DB,alimonitor.*,java.io.ByteArrayOutputStream,lia.Monitor.Store.*,lia.Monitor.monitor.monPredicate,lia.web.utils.Annotation,lia.web.utils.Annotations,lia.web.servlets.web.Utils,lia.Monitor.monitor.monPredicate,java.io.*,java.util.*,java.text.SimpleDateFormat,java.awt.Color"%><%
    if (!lia.web.utils.ThreadedPage.acceptRequest(request, response))
	return;

    lia.web.servlets.web.Utils.logRequest("START /annotations.jsp", 0, request);

    ByteArrayOutputStream baos = new ByteArrayOutputStream(10000);
            
    Page pMaster = new Page(baos, "annotations.res");
    
    Page pFilter = new Page("annotations_filter.res");
    
    Page pAnnotationsEl = new Page("annotations_el.res"); 

    pMaster.modify("title", "Annotations List");
    
    String JSP = "annotations.jsp";
    pMaster.modify("jsp", JSP);
    pFilter.modify("jsp", JSP);

    response.setHeader("Pragma", "no-cache");
    response.setHeader("Expires", "-1");
    response.setContentType("text/html");
    response.setHeader("Admin", "Costin Grigoras <costin.grigoras@cern.ch>");


    //listing params
    //sorting params
    String sSort = (request.getParameter("sort") == null || request.getParameter("sort").length() == 0) ? "4" : request.getParameter("sort");
    int iSort = 0;
    
    try{
	iSort = Integer.parseInt(sSort);
    }
    catch(Exception e){
	iSort = 1;
    }

    String sSortValue = (request.getParameter("sortvalue") == null || request.getParameter("sortvalue").length() == 0) ? "asc" : request.getParameter("sortvalue");

    pFilter.modify("img_"+iSort, "<img src=\"/img/"+sSortValue.toLowerCase()+"_trend.png\" border=\"0\">");
    pFilter.modify("sort_"+iSort, sSortValue.toLowerCase().equals("asc") ? "desc" : "asc");
    pFilter.modify("sort", iSort);
    pFilter.modify("sortvalue", sSortValue);
    
    Comparator<Annotation> comp = null;
    final int iSortValue = sSortValue.toLowerCase().equals("asc") ? -1 : 1;
    
    final long lNow = System.currentTimeMillis();    

    String sSeriesNames = request.getParameter("series_names");
    
    if (sSeriesNames==null)
	sSeriesNames="";

    String sGroups = request.getParameter("groups");
    
    if (sGroups==null)
	sGroups = "";

    pMaster.modify("sSeriesNames", sSeriesNames);
    pMaster.modify("sGroups", sGroups);

    final TreeSet tsGroups = new TreeSet();
	
    if (sGroups.length()>0){
	String vs[] = sGroups.split(",");
		
	for (int i=0; i<vs.length; i++){
	    try{
		tsGroups.add(Integer.parseInt(vs[i]));
	    }
	    catch (Exception e){
	    }
	}
    }


    DB db = new DB("select ag_id,ag_name from annotation_groups;");
    
    final TreeMap<Integer,String> tmGroups = new TreeMap<Integer,String>();
    
    while (db.moveNext()){
	pFilter.append("groups_options", "<option value="+db.geti(1)+(tsGroups.contains(db.geti(1)) ? " selected" :"")+">"+db.gets(2)+"</option>");
	
	tmGroups.put(db.geti(1), db.gets(2));
    }

    
    switch(iSort){
	case 1: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){
		    return iSortValue * a1.text.toLowerCase().compareTo(a2.text.toLowerCase());
		}    	    
    	    };
    	    
    	    break;
	case 2: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){
		    return iSortValue * a1.services.toString().toLowerCase().compareTo(a2.services.toString().toLowerCase());
		}    	    
    	    };

	    break;
	case 3: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){
		    long lDiff1 = (a1.to > lNow ? lNow : a1.to) - a1.from;
		    long lDiff2 = (a2.to > lNow ? lNow : a2.to) - a2.from;
		    
		    return iSortValue * (lDiff1 > lDiff2 ? 1 : -1);
		}    	    
    	    };

	    break;
	case 4: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){
		    return iSortValue * (a1.from > a2.from ? 1 : -1);
		}    	    
    	    };

	    break;
	case 5: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){
		    return iSortValue * (a1.to > a2.to ? 1 : -1);
		}    	    
    	    };

	    break;
	case 6: 
    	    comp = new Comparator<Annotation>(){
		public int compare(Annotation a1, Annotation a2){

		    Iterator it2 = a1.groups.iterator();
	
		    String sGroupNames1 = "";
	
		    while (it2.hasNext()){
			String s = tmGroups.get( (Integer) it2.next() );
	    
			if (s!=null)
			    sGroupNames1 += (sGroupNames1.length()>0 ? "," : "") + s;
		    }
		    
		    it2 = a2.groups.iterator();
		    
		    String sGroupNames2 = "";
	
		    while (it2.hasNext()){
			String s = tmGroups.get( (Integer) it2.next() );
	    
			if (s!=null)
			    sGroupNames2 += (sGroupNames2.length()>0 ? "," : "") + s;
		    }
		    
		    return iSortValue * (sGroupNames1.toLowerCase().compareTo(sGroupNames2.toLowerCase()));
		}    	    
    	    };

	    break;
	default:
	    break;
    }

    //filter params
    String sFilter1 = request.getParameter("filter_1") == null ? "" : request.getParameter("filter_1");
    String sFilter2 = request.getParameter("filter_2") == null ? "" : request.getParameter("filter_2");
    String sFilter4 = request.getParameter("filter_4") == null ? "-1" : request.getParameter("filter_4");
    String sFilter5 = request.getParameter("filter_5") == null ? "" : request.getParameter("filter_5");
    
    int iFilter4 = -1;
    
    try{
	iFilter4 = Integer.parseInt(sFilter4);	
    }
    catch(Exception e){
	iFilter4 = -1;
    }
    
    pFilter.modify("filter_1", sFilter1);
    pFilter.modify("filter_2", sFilter2);
    pFilter.modify("filter_4_"+sFilter4, "selected");
    
    pFilter.modify("filter_5_"+sFilter5, "selected");
    
    final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
    
    pMaster.modify("filter", pFilter);
    
    final List lAnnotations = Annotations.getAnnotations(0, System.currentTimeMillis(), tsGroups, 10000, 0, true);

    Collections.reverse(lAnnotations);
    
    if (comp != null)
        Collections.sort(lAnnotations, comp);	    
	    
    final Iterator it = lAnnotations.iterator();
	    
    int i=0;
    
    

    while (it.hasNext()){
	Annotation a = (Annotation) it.next();
		
	if (a.bValue)
	    continue;
	
	//aplying filters
	if(sFilter1.length() > 0){
	    if(a.text.toLowerCase().indexOf(sFilter1.toLowerCase()) < 0)
		continue;
	}

	if(sFilter2.length() > 0){
	    if(a.services.toString().toLowerCase().indexOf(sFilter2.toLowerCase()) < 0)
		continue;
	}
	
	if(iFilter4 > 0){
			      //nr of days	
	    if(a.from < (lNow - iFilter4 * (1000L * 60 * 60 * 24))){
		continue;
	    }
	}
	
	if(sFilter5.equals("1")){
	    if(a.to < lNow)
		continue;
	}
	
	i++;
	
	pAnnotationsEl.modify("color", i%2==0 ? "#FFFFFF" : "#F0F0F0");
	pAnnotationsEl.modify("a_color", Utils.toHex(a.color));
	pAnnotationsEl.modify("a_text", a.text);
	pAnnotationsEl.modify("a_id", a.id);
	
	Iterator it2 = a.groups.iterator();
	
	String sGroupNames = "";
	
	while (it2.hasNext()){
	    String s = tmGroups.get( (Integer) it2.next() );
	    
	    if (s!=null)
		sGroupNames += (sGroupNames.length()>0 ? "," : "") + s;
	}
	
	pAnnotationsEl.modify("a_groupnames", sGroupNames.length()>0 ? sGroupNames : "<b><i>System-wide</i></b>");
	
	if (a.getDescription() == null || a.getDescription().length()==0){
	    pAnnotationsEl.comment("com_image", false);
	}
	else{
	    pAnnotationsEl.modify("a_description", Format.escHtml(Format.escJS(a.getDescription())));
	}
	
	pAnnotationsEl.modify("a_service", a.services.size() > 0 ? a.services.toString() : "<i><b>chart-wide</i>");

        long lDiff = (a.to > lNow ? lNow : a.to) - a.from;

	lDiff /= 60*1000;
			    
	if (lDiff < 60)
	    pAnnotationsEl.append("diff", lDiff+" min");
	else{
	    long h = lDiff/60;
	
	if (h>24){
	    pAnnotationsEl.append("diff", h/24+"d ");
	    h = h%24;
	}
	
	pAnnotationsEl.append("diff",h+"h");
	
	if (lDiff%60>0)
	    pAnnotationsEl.append("diff"," "+lDiff%60+"m");
	}
			
	if (a.to > lNow)
	    pAnnotationsEl.append("diff", " until now");

	 pAnnotationsEl.modify("a_from", sdf.format(new Date(a.from)));
	 pAnnotationsEl.modify("a_to", a.to > lNow ? "<b><i>continues</i></b>" : sdf.format(new Date(a.to)));
	 
	 pMaster.append(pAnnotationsEl);
    }
    
    pMaster.modify("totalmatch", i==0 ? "No event" : i+" event"+(i>1?"s":""));
    
    pMaster.write();
        
    String s = new String(baos.toByteArray());
    out.println(s);

    lia.web.servlets.web.Utils.logRequest("/annotations.jsp", s.length(), request);
%>