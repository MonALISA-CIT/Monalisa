package lia.Monitor.modules;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

public class monSiteStats extends SchJob implements MonitoringModule {

    private MonModuleInfo mmi = null;
    private MNode mn = null;
    
    private long   lLastCall = 0;
    private double vdData[]  = new double[20];
    
    public  MonModuleInfo init( MNode node, String args ){
	mn = node;
	
	mmi = new MonModuleInfo();
	mmi.setName("SiteStatsModule");
	mmi.setState(0);

	lLastCall = NTPDate.currentTimeMillis();
	mmi.lastMeasurement = lLastCall;
	
	try{
	    vdData[0] = lia.web.utils.ThreadedPage.getRequestCount();
	}
	catch (Throwable t){
	}
	
	try{
	    for (int i=0; i<lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames.length; i++)
		vdData[1+i] = lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i];
	}
	catch (Throwable t){
	}
	
	return mmi;
    }
    
    // MonitoringModule
    
    public String[] ResTypes() {
	return mmi.getResType();
    }
    
    public String   getOsName(){
	return "Linux";
    }
    
    public Object   doProcess() throws Exception {
	long ls        = NTPDate.currentTimeMillis();
	
	if (ls <= lLastCall)
	    return null;
	
	Result er      = new Result();
	er.FarmName    = getFarmName();
	er.ClusterName = getClusterName();
	er.NodeName    = mn.getName();
	er.Module      = mmi.getName();
	er.time        = ls;

	er.addSet("FreeMemory", Runtime.getRuntime().freeMemory());
	er.addSet("TotalMemory", Runtime.getRuntime().totalMemory());
	
	try{
	    er.addSet("Requests_permin", (lia.web.utils.ThreadedPage.getRequestCount() - vdData[0]) * 60000 / (double) (ls - lLastCall));
	    
	    vdData[0] = lia.web.utils.ThreadedPage.getRequestCount();
	}
	catch (Throwable t){
	}
	
	try{
	    er.addSet("Uptime", (NTPDate.currentTimeMillis()-lia.web.utils.ThreadedPage.lRepositoryStarted)/1000);
	}
	catch (Throwable t){
	}

	lLastCall = ls;
	
	return er;
    }

    public MNode getNode(){
	return mn;
    }
 
    public String getClusterName(){
	return mn.getClusterName();
    }
    
    public String getFarmName(){
	return mn.getFarmName();
    }

    public boolean isRepetitive(){
	return true;
    }

    public String getTaskName(){
	return mmi.getName();
    }
    
    public MonModuleInfo getInfo(){
	return mmi;
    }
    
}
