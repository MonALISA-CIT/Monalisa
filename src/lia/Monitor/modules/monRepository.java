package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

public class monRepository extends SchJob implements MonitoringModule {

    private MonModuleInfo mmi = null;
    private MNode mn = null;
    
    private String sHost = "localhost";
    private int    iPort = 8080;
    private String sURL  = "/display/?statistics=true";
    
    public  MonModuleInfo init( MNode node, String args ){
	mn = node;
	
	mmi = new MonModuleInfo();
	mmi.setName("RepositoryModule");
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
    
    public Object doProcess() throws Exception {
		if (mmi.getState() != 0) {
			throw new IOException("there was some exception during init ...");
		}

		Vector vr = new Vector();

		long ls = NTPDate.currentTimeMillis();

		Result er = new Result();
		er.FarmName = getFarmName();
		er.ClusterName = getClusterName();
		er.NodeName = mn.getName();
		er.Module = mmi.getName();
		er.time = ls;

		// patch here
		Socket s = new Socket(sHost, iPort);

		s.setSoTimeout(15 * 1000);

		PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));

		pw.println("GET " + sURL + " HTTP/1.0\r");
		pw.println("Host: " + sHost + "\r");
		pw.println("\r");
		pw.flush();

		String sLine = null;

		while ((sLine = br.readLine()) != null && sLine.length() > 0) {
			// read the header
		}

		try {
			while ((sLine = br.readLine()) != null) {
				if (sLine.indexOf("\t") > 0) {
					er.addSet(sLine.substring(0, sLine.indexOf("\t")), Double.parseDouble(sLine.substring(sLine.indexOf("\t") + 1)));
				}
			}
		}
		catch (StringIndexOutOfBoundsException e) {
			System.err.println("Exception while parsing : " + e + " ( " + e.getMessage() + " )");
			e.printStackTrace();
		}

		if (er.param_name != null && er.param_name.length > 0) {
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
