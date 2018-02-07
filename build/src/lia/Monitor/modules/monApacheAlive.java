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

public class monApacheAlive extends SchJob implements MonitoringModule, Runnable {

    private MonModuleInfo mmi = null;
    private MNode mn = null;
    
    private static class Host {
	public String sHost = "localhost";
	public int    iPort = 80;
	public String sURL  = "/";
	
	public String toString(){
	    return sHost+":"+iPort+sURL;
	}
    }
    
    Vector vHosts;
    Vector vr;
    
    public  MonModuleInfo init( MNode node, String args ){
	mn = node;
	
	//System.err.println("Init monApacheAlive ("+args+")");
	
	mmi = new MonModuleInfo();
	mmi.setName("ApacheAliveModule");
	mmi.setResType(new String[]{"ConnectTime_sec", "RequestTime_sec", "Throughput_bps"});
	mmi.setState(0);
	
	vHosts = new Vector();
	vr     = new Vector();
	
	StringTokenizer st = new StringTokenizer(args, ", ");
	
	String sError = null;

	while (st.hasMoreTokens()){
    	    try{
		Host host = new Host();
	    
		host.sHost = st.nextToken();
	    
	        if (host.sHost.indexOf("://") >0 && host.sHost.indexOf("://") < 10)
		    host.sHost = host.sHost.substring(host.sHost.indexOf("://")+3);
	    
	        if (host.sHost.indexOf("/")>0){
	    	    host.sURL  = host.sHost.substring(host.sHost.indexOf("/"));
		    host.sHost = host.sHost.substring(0, host.sHost.indexOf("/"));
		}
	    
		if (host.sHost.indexOf(":")>0){
		    host.iPort = Integer.parseInt(host.sHost.substring(host.sHost.indexOf(":")+1));
		    host.sHost = host.sHost.substring(0, host.sHost.indexOf(":"));
		}
		
		vHosts.add(host);
	    }
	    catch (Exception e){	
		sError = e.getMessage();
	    }
	}
	
	if ( sError != null ){
	    mmi.addErrorCount();
	    mmi.setState(1);	// error
	    mmi.setErrorDesc(sError);
	    return mmi;
	}
	
	mmi.lastMeasurement = NTPDate.currentTimeMillis();
	
	Thread t = new Thread(this, "(ML) monApacheAlive");
	t.setDaemon(true);
	t.start();
		
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
        if (mmi.getState()!=0){
	    throw new IOException("there was some exception during init ...");
	}
	
	Vector v = new Vector();
	
	synchronized (vr){
	    v.addAll(vr);
	    vr.clear();
	}
	
	//System.err.println("monApacheAlive.doProcess() returns : "+v);
	
	return v;
    }
    
    public void run(){
	while (true){
	    //System.err.println("monApacheAlive.run("+vHosts+")");
    
	    long ls = NTPDate.currentTimeMillis();
	
	    for (int i=0; i<vHosts.size(); i++){
		try{
	    	    Host host = (Host) vHosts.get(i);
	
		    long lStart    = NTPDate.currentTimeMillis();
		
	            Result er      = new Result();
		    er.FarmName    = getFarmName();
	            er.ClusterName = getClusterName();
		    er.NodeName    = host.sHost+":"+host.iPort;
	            er.Module      = mmi.getName();
	    	    er.time        = lStart;

	            Socket s = new Socket(host.sHost, host.iPort);
	    
		    long lConnect  = NTPDate.currentTimeMillis();
	    
		    s.setSoTimeout(15 * 1000);
	
		    PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
	
		    pw.println("GET "+host.sURL+" HTTP/1.0\r");
		    pw.println("Host: "+host.sHost+"\r");
		    pw.println("\r");
	            pw.flush();
	
		    String sLine = null;
		    int count = 0;
	
		    while ( (sLine=br.readLine()) != null )
	    		count += sLine.length()+1;
	
		    long lEnd = NTPDate.currentTimeMillis();
	
		    s.close();
	
		    if (lEnd==lConnect) lEnd++;
	
		    er.addSet("ConnectTime_sec", (double) (lConnect-lStart) / 1000.0);
		    er.addSet("RequestTime_sec", (double) (lEnd-lConnect) / 1000.0);
		    er.addSet("Throughput_bps", ((double) count * 1000.0)/ (double) (lEnd-lConnect));
	
		    vr.addElement(er);
		}
	        catch (Exception e){
		    e.printStackTrace();
		}
	    }
	    
	    ls = 1000*2*60 - (NTPDate.currentTimeMillis() - ls);	// every two minutes at most
	    
	    if (ls<=0)
		ls = 1;
	    
	    try{
	        Thread.sleep(ls);
	    }
	    catch (Exception e){
	    }
	}
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
