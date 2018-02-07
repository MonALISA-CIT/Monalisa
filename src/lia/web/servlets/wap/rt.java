package lia.web.servlets.wap;

import java.util.Properties;

import lia.Monitor.Store.Cache;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.servlets.web.Utils;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Page;

/**
 */
public class rt extends WAPPage {
	private static final long	serialVersionUID	= 1L;


	@Override
	@SuppressWarnings("null")
	public void execGet(){
	pMaster.modify("id", "realtime");
	
	Page p = new Page(sResDir+"rt/rt.res");
	
	p.modify("page", gets("page"));
	p.modify("rand", nextRand());
	
	String vsDescr[];
	monPredicate[] preds;
	String[] series = null;
	boolean bSize = false;
	
	try{
	    Properties prop = Utils.getProperties(sConfDir, gets("page"));
	    
	    pMaster.modify("title", pgets(prop, "title"));

	    String[] vsFarms        = Utils.getValues(prop, "Farms");
	    String[] vsClusters     = Utils.getValues(prop, "Clusters");
	    String[] vsNodes        = Utils.getValues(prop, "Nodes");
	    String[] vsFunctions    = Utils.getValues(prop, "Functions");
	    String[] vsFunctionSuff = Utils.getValues(prop, "FuncSuff");
	    
	    bSize = pgetb(prop, "size", false);
	    
	    if (vsFunctionSuff==null || vsFunctionSuff.length<=0)
		vsFunctionSuff = new String[] {""};
    
	    String[] vsWildcards    = Utils.getValues(prop, "Wildcards");
    
	    int len = 0;
	    int i;
    
	    int w = -1;
    
	    for (i=0; vsWildcards!=null && i<vsWildcards.length; i++){
		int newlen = 0;
    
		String[] newseries = null;
	
		if (vsWildcards[i].equals("F")) {newlen = vsFarms!=null?vsFarms.length:0; newseries=vsFarms;}
		if (vsWildcards[i].equals("C")) {newlen = vsClusters!=null?vsClusters.length:0; newseries=vsClusters;}
		if (vsWildcards[i].equals("N")) {newlen = vsNodes!=null?vsNodes.length:0; newseries=vsNodes;}
		if (vsWildcards[i].equals("f")) {newlen = vsFunctions!=null?vsFunctions.length:0; newseries=vsFunctions;}
	
		if (newlen>0){
		    w = i;
		    len = newlen;
		    series = newseries;
		}
	    }
    
	    if (len<=0 || w<0){
		return;
	    }
    
	    preds = new monPredicate[len];
    
	    for (i=0; i<len; i++){
		preds[i] = new monPredicate();
		
		if (vsWildcards[w].equals("F")) preds[i].Farm=vsFarms[i];
		else preds[i].Farm = (vsFarms!=null&&vsFarms.length>0)?vsFarms[0]:"*";
		
		if (vsWildcards[w].equals("C")) preds[i].Cluster=vsClusters[i];
		else preds[i].Cluster = (vsClusters!=null&&vsClusters.length>0)?vsClusters[0]:"*";
	
		if (vsWildcards[w].equals("N")) preds[i].Node=vsNodes[i];
		else preds[i].Node = (vsNodes!=null&&vsNodes.length>0)?vsNodes[0]:"*";
	
		String[] param = null;
	
		if (vsWildcards[w].equals("f")) param=new String[]{vsFunctions[i]};
		else {
		    param = vsFunctions!=null&&vsFunctions.length>0 ? vsFunctions : new String[]{"*"};
		}
	
		preds[i].parameters = new String[ param.length * vsFunctionSuff.length ];
	
		for (int j=0; j<param.length; j++)
		    for (int k=0; k<vsFunctionSuff.length; k++)
			preds[i].parameters[j*vsFunctionSuff.length+k] = param[j]+vsFunctionSuff[k];
    
	        preds[i].tmin = - 5*60*1000;
	        preds[i].tmax = -1;
	    }
    
	    vsDescr = Utils.getValues(prop, "descr");
    
	    int descrLen = vsWildcards[w].equals("f") ? vsFunctionSuff.length : vsFunctions.length;
    
	    if (vsDescr==null || vsDescr.length!=descrLen ){
		vsDescr = new String[descrLen];
		    for (i=0; i<descrLen; i++)
		        vsDescr[i] = "";
	    }
	}
	catch (Throwable e){
	    p.append("Exception was : "+e.getMessage());
	    return;
	}
	
	int i;
	
	String vsTables[] = new String[vsDescr.length];
	
	for (i=0; i<vsDescr.length; i++){
	    vsTables[i] = "</p><p align=\"center\"><big><b><u>"+escHtml(vsDescr[i])+"</u></b></big><br/>\n<table columns=\"2\">\n";
	}
	
	for (i=0; i<series.length; i++){
	    for (int j=0; j<vsDescr.length; j++){
		vsTables[j] += "<tr><td>"+series[i]+"</td><td>";
		
		monPredicate pred  = preds[i];
		String sParameter  = pred.parameters[j];

		monPredicate pTemp = new monPredicate();
		pTemp.Farm         = pred.Farm;
		pTemp.Cluster      = pred.Cluster;
		pTemp.Node         = pred.Node;
		pTemp.parameters   = new String[] {sParameter};

		Object o           = Cache.getLastValue(pTemp);
		
		String sValue      = "-";
		
		Result r = (o!=null && (o instanceof Result)) ? (Result)o : null;
			    
		if (r!=null){
		    if (bSize){
			sValue = DoubleFormat.size( r.param[0]>0.01 ? r.param[0] : 0, "M" ) + "bps";
		    }
		    else{
			sValue = ""+((long)(r.param[0]*100))/100.0;
			
			if (sValue.endsWith(".0") || sValue.endsWith(".00"))
			    sValue = sValue.substring(0, sValue.lastIndexOf("."));
		    }
		}
		
		vsTables[j] += sValue + "</td></tr>\n";
	    }
	}
	
	for (i=0; i<vsDescr.length; i++){
	    vsTables[i] += "</table><br/>\n";
	    
	    p.append(vsTables[i]);
	}
	
	p.append("</p><p>");
	
	pMaster.append(p);
	
	pMaster.write();
    }

}
