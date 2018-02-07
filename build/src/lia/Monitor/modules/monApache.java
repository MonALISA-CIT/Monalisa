package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

public class monApache extends SchJob implements MonitoringModule {

    private MonModuleInfo mmi = null;
    private MNode mn = null;
    
    private String sHost = "localhost";
    private int    iPort = 80;
    private String sURL  = "/server-status/";
    
    public  MonModuleInfo init( MNode node, String args ){
	mn = node;
	
	mmi = new MonModuleInfo();
	mmi.setName("ApacheModule");
	mmi.setState(0);

	String sError = null;
	try{
	    sHost = args;
	    
	    if (sHost.indexOf("://") >0 && sHost.indexOf("://") < 10){
		sHost = sHost.substring(sHost.indexOf("://")+3);
	    }
	    
	    if (sHost.indexOf("/")>0){
		sURL  = sHost.substring(sHost.indexOf("/"));
		sHost = sHost.substring(0, sHost.indexOf("/"));
	    }
	    
	    if (sHost.indexOf(":")>0){	
		iPort = Integer.parseInt(sHost.substring(sHost.indexOf(":")+1));
		sHost = sHost.substring(0, sHost.indexOf(":"));
	    }
	}
	catch (Exception e){
	    sError = e.getMessage();
	}
	
	if ( sError != null ){
	    mmi.addErrorCount();
	    mmi.setState(1);	// error
	    mmi.setErrorDesc(sError);
	    return mmi;
	}
	
	mmi.lastMeasurement = NTPDate.currentTimeMillis();
		
	return mmi;
    }
    
    // MonitoringModule
    
    public String[] ResTypes() {
	return mmi.getResType();
    }
    
    public String   getOsName(){
	return "Linux";
    }
    
    private long   lLastProcess  = 0;
    private double dLastAccesses = 0;
    private double dLastTraffic  = 0;
    
    public Object   doProcess() throws Exception {
	if (mmi.getState()!=0){
	    throw new IOException("there was some exception during init ...");
	}
	
	Vector vr = new Vector();
	
	long ls        = NTPDate.currentTimeMillis();
	
	Result er      = new Result();
	er.FarmName    = getFarmName();
	er.ClusterName = getClusterName();
	er.NodeName    = mn.getName();
	er.Module      = mmi.getName();
	er.time        = ls;
	
	// patch here
	Socket s = new Socket(sHost, iPort);
	
	s.setSoTimeout(15 * 1000);
	
	PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
	BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
	
	pw.println("GET "+sURL+" HTTP/1.0\r");
	pw.println("Host: "+sHost+"\r");
	pw.println("\r");
	pw.flush();
	
	String sLine = null;
	
	while ( (sLine=br.readLine()).length() > 0 ){
	}
	
	try{
	    while ( (sLine=br.readLine()) != null ){
		if (sLine.startsWith("<dt>Total accesses: ")){
		    StringTokenizer st = new StringTokenizer(sLine, " ");
		
		    st.nextToken(); st.nextToken();
		
		    double d = Double.parseDouble(st.nextToken());
		
		    er.addSet("TotalRequests", d);
		    
		    if ( (lLastProcess > 0) && (ls - lLastProcess > 0) ){
			er.addSet("RequestsPerSec", ((d-dLastAccesses)/(double)(ls-lLastProcess))*(double)1000.0);
		    }
		    else{
			er.addSet("RequestsPerSec", (double)0.0);
		    }
		    dLastAccesses= d;
		    
		    st.nextToken(); st.nextToken(); st.nextToken();
		    
		    d = Double.parseDouble(st.nextToken());
		    String str  = st.nextToken().toLowerCase();
		    if (str.startsWith("k")) d*=1024;
		    if (str.startsWith("m")) d*=1024 * 1024;
		    if (str.startsWith("g")) d*=1024 * 1024 * 1024;
	    	    er.addSet("TotalTraffic", d);
		    
		    if ( (lLastProcess > 0) && (ls - lLastProcess > 0) ){
			er.addSet("CurrentTraffic", ((d-dLastTraffic)/(double)(ls-lLastProcess))*(double)1000.0);
		    }
		    else{
			er.addSet("CurrentTraffic", (double)0.0);
		    }
		    dLastTraffic = d;
		    
		    lLastProcess = ls;
		}
	        else
		if (sLine.startsWith("<dt>CPU Usage: ")){
		    StringTokenizer st = new StringTokenizer(sLine, " %");
		
		    for (int i=0; i<7; i++)
			st.nextToken();
		
		    er.addSet("CPUUsage", Double.parseDouble(st.nextToken()));
	        }
		else
		if (sLine.indexOf(" requests/sec - ")>0){
		    StringTokenizer st = new StringTokenizer(sLine, "> ");
		
		    st.nextToken();
		
		    double nr = Double.parseDouble(st.nextToken());
		    String str  = st.nextToken().toLowerCase();
		    if (str.startsWith("k")) nr*=1024;
		    if (str.startsWith("m")) nr*=1024 * 1024;
		    if (str.startsWith("g")) nr*=1024 * 1024 * 1024;
	    	    er.addSet("AvgReqPerSec", nr);
		
		    st.nextToken(); 
		
		    nr = Double.parseDouble(st.nextToken());
		    str  = st.nextToken().toLowerCase();
		    if (str.startsWith("k")) nr*=1024;
		    if (str.startsWith("m")) nr*=1024 * 1024;
		    if (str.startsWith("g")) nr*=1024 * 1024 * 1024;
		    er.addSet("AvgBytesPerSec", nr);
		
		    st.nextToken(); 
		
		    nr = Double.parseDouble(st.nextToken());
		    str  = st.nextToken().toLowerCase();
		    if (str.startsWith("k")) nr*=1024;
		    if (str.startsWith("m")) nr*=1024 * 1024;
		    if (str.startsWith("g")) nr*=1024 * 1024 * 1024;
		    er.addSet("AvgBytesPerReq", nr);
		}
		else
		if (sLine.indexOf(" requests currently being processed,")>0){
		    StringTokenizer st = new StringTokenizer(sLine, "> ");
		
		    st.nextToken();
		
		    er.addSet("RunningWorkers", Double.parseDouble(st.nextToken()));
		
		    st.nextToken(); st.nextToken(); st.nextToken(); st.nextToken(); 
		
	    	    er.addSet("IdleWorkers", Double.parseDouble(st.nextToken()));
		}
	    }
	}
	catch (Exception e){
	    System.err.println("Exception while parsing : "+e+" ( "+e.getMessage()+" )");
	    e.printStackTrace();
	}
	
	if (er.param_name!=null && er.param_name.length>0){
	    vr.addElement(er);
	    return vr;
	}
		
	return null;
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
