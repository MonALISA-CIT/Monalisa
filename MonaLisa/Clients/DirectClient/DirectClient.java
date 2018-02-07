import java.net.Socket;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Vector;
import java.util.StringTokenizer;

import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.Result;

public class DirectClient {

    public static Vector query(String sServer, int iPort, Vector preds, boolean bLastValues){
	Vector vRez = new Vector();
	
	try{
	    Socket s = new Socket(sServer, iPort);

	    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
	    PrintWriter    pw = new PrintWriter(s.getOutputStream());
	    
	    pw.println(bLastValues ? "L" : "H");
	    
	    for (int i=0; i<preds.size(); i++){
		monPredicate pred = (monPredicate) preds.get(i);
	    
		StringBuffer sb = new StringBuffer();
		
		sb.append(pred.Farm!=null    ? pred.Farm    : "*");
		sb.append("/");
		sb.append(pred.Cluster!=null ? pred.Cluster : "*");
		sb.append("/");
		sb.append(pred.Node!=null    ? pred.Node    : "*");
		sb.append("/");
		
		if (bLastValues)
		    sb.append("-1/-1/");
		else
		    sb.append(pred.tmin+"/"+pred.tmax+"/");
		
		for (int j=0; pred.parameters!=null && j<pred.parameters.length; j++)
		    sb.append( (j>0?"|":"") + pred.parameters[j] );
		
		pw.println(sb.toString());
	    }
	    
	    pw.println();
	    
	    pw.flush();
	    
	    String sLine;
	    
	    while ( (sLine=br.readLine())!=null ){
		StringTokenizer st = new StringTokenizer(sLine, "/");
		
		Result r = new Result();
		r.time        = Long.parseLong(st.nextToken());
		r.FarmName    = st.nextToken();
		r.ClusterName = st.nextToken();
		r.NodeName    = st.nextToken();
		
		r.addSet(st.nextToken(), Double.parseDouble(st.nextToken()));
		
		vRez.add(r);
	    }
	    
	    pw.close();
	    br.close();
	}
	catch (Exception e){
	    System.err.println("Exception communicating: "+e+" ("+e.getMessage()+")");
	    e.printStackTrace();
	}
	
	
	return vRez;
    }
    
    public static void main(String args[]){
    
	Vector vPreds = new Vector();
	

	vPreds.add(new monPredicate("*", "Master", "*", -1, -1, new String[] {"Load5"}, null));

//	Other sample predicates
//	vPreds.add(new monPredicate("*", "PN%", "*", -1, -1, new String[] {"Load_05", "Load_51"}, null));
//	vPreds.add(new monPredicate("*", "*", "*", -1, -1, null, null));
    
	long lStart = System.currentTimeMillis();
	Vector vResults = query("localhost", 9100, vPreds, true);
	System.err.println("  Query took : "+(System.currentTimeMillis() - lStart)+" millis, "+vResults.size()+" results");
	
	for (int i=0; i<vResults.size(); i++){
	    Result r = (Result) vResults.get(i);
	
	    System.out.println(r.time+"/"+r.FarmName+"/"+r.ClusterName+"/"+r.NodeName+"/"+r.param_name[0]+"/"+r.param[0]);
	}
    
    }

}