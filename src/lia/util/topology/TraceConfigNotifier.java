package lia.util.topology;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;

public class TraceConfigNotifier extends Thread {
	// any received request will be also forwarded to these traceAutoConfig services to keep sync 
	private static final String otherTraceConfigServ = AppConfig.getProperty("lia.util.topology.otherTraceConfigServ", "");
	// here we store the list of notifications for other trace config services 
	private LinkedList otherNotifQueue;
	private final int MAX_NOTIFICATIONS = 20;
	boolean hasToRun = true;

	public TraceConfigNotifier (){
		otherNotifQueue = new LinkedList();
		this.start();
	}
	
	/** 
	 * notify other trace config generators about this peer's request
	 * in an asynchronous manner. 
	 * */
	public void addNotification(TracePeer tp){
		synchronized (this) {
			int count = 0;
			while(otherNotifQueue.size() > MAX_NOTIFICATIONS){
				otherNotifQueue.removeFirst();
				count++;
			}
			if(count > 0){
				System.err.println("Notification queue too big! Removed "+count+" notifications.");
			}
			otherNotifQueue.addLast(tp);
			this.notify();
		}
	}
	
	/** notify all trace config generator about the query regarding this peer */
	private void notifyConfGen(TracePeer tp){
		if(otherTraceConfigServ == null || otherTraceConfigServ.trim().length() == 0)
			return;

		StringBuilder query = new StringBuilder();
		query.append("keepAlive=true");
		if(tp.requestIP.trim().length() > 0)
			query.append("&requestIP="+tp.requestIP);
		if(tp.farmIP.trim().length() > 0)
			query.append("&farmIP="+tp.farmIP);
		if(tp.hostName.trim().length() > 0)
			query.append("&hostName="+tp.hostName);
		if(tp.farmName.trim().length() > 0)
			query.append("&farmName="+tp.farmName);
		if(tp.groups.size() > 0)
			query.append("&groups="+TracePeer.vectorToStr(tp.groups));
		if(tp.mlVersion.trim().length() > 0)
			query.append("&mlVersion="+tp.mlVersion);
		if(tp.mlDate.trim().length() > 0)
			query.append("&mlDate="+tp.mlDate);
		if(tp.LONG.trim().length() > 0)
			query.append("&LONG="+tp.LONG);
		if(tp.LAT.trim().length() > 0)
			query.append("&LAT="+tp.LAT);
		if(tp.traceOpts.trim().length() > 0)
			query.append("&traceOpts="+tp.traceOpts);
		
		String strQuery = query.toString();
		
		// notify all other TraceAutoConfig about this
		StringTokenizer stk = new StringTokenizer(otherTraceConfigServ, ",");
		while(stk.hasMoreTokens()){
			String confGenPeer = stk.nextToken();
			postMessage(strQuery, confGenPeer);
		}
	}

	/** http-POST the given message to given servlet url */
	private void postMessage(String message, String confGenPeer){
		URLConnection urlc = null;
		InputStream is = null;
		DataOutputStream os = null;
		BufferedReader in = null;
		try{
			urlc = new URL(confGenPeer).openConnection();
			// We are going to do a POST
		    urlc.setDoInput(true);
		    urlc.setDoOutput(true);
	        // Quick fix to bypass http proxies 
		    urlc.setDefaultUseCaches(false);
		    urlc.setUseCaches(false);
		    // We are going to send URLEncoed data
		    urlc.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		    os = new DataOutputStream(urlc.getOutputStream());
		    os.writeBytes(message);
		    os.flush();
		    os.close();
		    os = null;
		    // Ok, now let's see what we got
		    is = urlc.getInputStream();
			in = new BufferedReader(new InputStreamReader(is));
			// in fact... for now I don't really care.
			//TODO: do smth. with the answer
		}catch(Throwable t){
			System.err.println("Error notifying conf gen peer "+confGenPeer);
			t.printStackTrace();
		}finally {
		    try {
		        if (in != null)
		            in.close();
		    }catch(Throwable tf){  }
		    try {
		        if (is != null)
		            is.close();
		    }catch(Throwable tf){  }
		    try {
		        if (os != null)
		            os.close();
		    }catch(Throwable tf){  }
		    in = null;
		    is = null;
		    os = null;
		    urlc = null;
		}
	}
	
	public void run() {
		while(hasToRun){
			try{
				TracePeer tp = null;
				synchronized(this) {
					while(otherNotifQueue.size() == 0)
						this.wait();
					tp = (TracePeer) otherNotifQueue.removeFirst();
				}
				if(tp != null){
					notifyConfGen(tp);
				}
			}catch(Throwable t){
				t.printStackTrace();
			}
		}
	}
}
