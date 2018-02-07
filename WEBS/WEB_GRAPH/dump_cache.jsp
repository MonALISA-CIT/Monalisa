<%@ page import="alimonitor.*,java.util.*,java.io.*,lia.web.utils.DoubleFormat,lia.web.servlets.web.*,lia.web.utils.ServletExtension,lia.Monitor.Store.Cache,lia.Monitor.monitor.*" %><%
    Utils.logRequest("START /dump_cache.jsp", 0, request);

    ByteArrayOutputStream baos = new ByteArrayOutputStream(20000);
            
    Page pMaster = new Page(baos, "WEB-INF/res/masterpage/masterpage_admin.res");

    pMaster.modify("title", "Last values cache inspection");
    
    //menu
    pMaster.modify("class_dump_cache", "_active");

    Page p = new Page("dump_cache.res");
    
    Page pLine = new Page("dump_cache_line.res");

    String sPred = request.getParameter("pred");
    
    if (sPred==null)
	sPred = "%/MonaLisa/localhost/Load5";
	
    p.modify("pred", sPred);
    
    //pMaster.modify("bookmark", "dump_cache.jsp?pred="+URLEncoder.encode(sPred));

    monPredicate filter = sPred==null || sPred.length()==0 ? null : ServletExtension.toPred(sPred);

    List al = filter == null ? Cache.getLastValues() : Cache.getLastValues(filter);

    Collections.sort(
            al,
            new Comparator(){
                public int compare(Object o1, Object o2){
            	    String sf1, sc1, sn1, sp1;
            	    String sf2, sc2, sn2, sp2;
            	    
            	    if (o1 instanceof Result){
            		Result r = (Result) o1;
            		
            		sf1 = r.FarmName;
            		sc1 = r.ClusterName;
            		sn1 = r.NodeName;
            		sp1 = r.param_name[0];
            	    } 
		    else
            	    if (o1 instanceof eResult){
            		eResult r = (eResult) o1;
            		
            		sf1 = r.FarmName;
            		sc1 = r.ClusterName;
            		sn1 = r.NodeName;
            		sp1 = r.param_name[0];
            	    }
            	    else
            		return 0;
            		

            	    if (o2 instanceof Result){
            		Result r = (Result) o2;
            		
            		sf2 = r.FarmName;
            		sc2 = r.ClusterName;
            		sn2 = r.NodeName;
            		sp2 = r.param_name[0];
            	    } 
		    else
            	    if (o2 instanceof eResult){
            		eResult r = (eResult) o2;
            		
            		sf2 = r.FarmName;
            		sc2 = r.ClusterName;
            		sn2 = r.NodeName;
            		sp2 = r.param_name[0];
            	    }
            	    else
            		return 0;            		
            		

                    int c = sf1.compareTo(sf2);
                    if (c!=0) return c;

                    if (sc1==null) return -1;
                    c = sc1.compareTo(sc2);
                    if (c!=0) return c;

                    if (sn1==null) return -1;
                    c = sn1.compareTo(sn2);
                    if (c!=0) return c;

                    return sp1.compareTo(sp2);
                }
            }
        );

	double total = 0;
	long cnt = 0;
	
	int iCnt = 0;
	
        for (int i=0; i<al.size(); i++){
                Object o = al.get(i);

                if (o instanceof Result){
                        Result r = (Result) o; 
                        
                        total += r.param[0];
                        
                        cnt ++;
                        
                        pLine.modify("farm", r.FarmName);
                        pLine.modify("cluster", r.ClusterName);
                        pLine.modify("node", r.NodeName);
                        pLine.modify("parameter", r.param_name[0]);
                        
                        String sValue = ""+r.param[0];
                        
	            	if (r instanceof ExtendedResult){
                    	    ExtendedResult er = (ExtendedResult) r;
                    	    sValue += " ("+er.min+", "+er.max+")";
                	}
                        
                        pLine.modify("value", sValue);
                        pLine.modify("time", (new Date(r.time)).toString());
                        
                        pLine.modify("exacttime", r.time);
                }
                
                if (o instanceof eResult){
            		eResult r = (eResult) o;
            		
            		pLine.modify("farm", r.FarmName);
                        pLine.modify("cluster", r.ClusterName);
                        pLine.modify("node", r.NodeName);
                        pLine.modify("parameter", r.param_name[0]);
                        pLine.modify("value", r.param[0].toString());
                        pLine.modify("time", (new Date(r.time)).toString());
                        
                        pLine.modify("exacttime", r.time);
                }
                
                pLine.modify("color", iCnt%2 == 0 ? "#FFFFFF" : "#F0F0F0");
                iCnt++;
                
                p.append(pLine);
        }
        
        p.modify("total", ""+total);
        
        if (cnt==0){
    	    p.comment("com_average", false);
        }
        else{
    	    p.modify("average", ""+(total/cnt));
        }
        
        p.modify("count", al.size());
    
    pMaster.append(p);
    
    pMaster.write();
        
    String s = new String(baos.toByteArray());
    out.println(s);
    
    Utils.logRequest("/dump_cache.jsp", baos.size(), request);
%>