import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Filters.GenericMLFilter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.DataArray;
import lia.util.ntp.NTPDate;

/**
 * Filter for CMS Tier0 monitoring data.
 * 
 * It computes summaries for data of this kind
 * - Something_$AppName[_SomethingElse]
 *    - host-something-something-something_ipAddress
 *      - cpu_time,rss,virtualmem,workdir_size,NEvents,RecoSize
 *      
 * into something like: 
 * - Something_$AppName_Summary
 *    - host_ipAddress           <- unique hosts/IP addresses
 *      - SUM of each of the parameters
 *      - activeJobs = number of nodes (jobs) in for each host_ipAddress
 * 
 * $AppName should be one of the names specified in the APP_NAMES_FILE,
 * otherwise it won't be taken into account.
 * 
 * @author catac
 */
public class Tier0Filter extends GenericMLFilter {
	
	private static Logger logger = Logger.getLogger("Tier0Filter");
	
	/** The name of this filter */
    public static final String Name = "AliEnFilter";
    
    /** How often we should produce data. At these intervals, expressResults is called */
    private long T0F_SLEEP_TIME;
    
    /** How soon we should drop data about the received parameters */
    private long PARAM_EXPIRE;
    
    /** Holds the (,-sepparated) names of the applications for which we do the summary */ 
    private String APP_NAMES;
    
    Vector vDebugResults;   // keep debugging results, produced by filter
    Hashtable htApps;       // key=appName; val=Hashtable[key=jobID; val=T0JobInfo]
    TreeSet sAppNames;		// list of appNames to look for

////////////////////////////////////////////////////////////////
//  Internal data structures for each summarized thing
////////////////////////////////////////////////////////////////

    class Tier0JobInfo {
    	String appName;
    	String host;
    	String ip;
    	
    	DataArray params = getTier0JobsEmptySummary();
    	
    	long lastUpdateTime;
    	
    	Tier0JobInfo(String appName, String host, String ip){
    		this.appName = appName;
    		this.host = host;
    		this.ip = ip;
    		this.lastUpdateTime = NTPDate.currentTimeMillis();
    	}
    	
    	/** Add a result with updates of the parameters of this job */
    	void addResult(Result r){
    		lastUpdateTime = NTPDate.currentTimeMillis();
    		
    		for(int i=0; i<r.param_name.length; i++){
    			String paramName = r.param_name[i];
    			double paramValue = r.param[i];
    			for(int j=0; j<params.params.length; j++){
    				if(paramName.equals(params.params[j]))
    					params.values[j] = paramValue;
    			}
    		}
    	}
    	
    	/** Summarize the parameters of this job for the nodes */
    	boolean summarize(Hashtable htNodes){
    		long now = NTPDate.currentTimeMillis();
//    		double timeInterval = T0F_SLEEP_TIME / 1000.0d;
    		if(now - lastUpdateTime > PARAM_EXPIRE){
    			return false;
    		}
    		DataArray nodeParams = (DataArray) htNodes.get(host+"_"+ip);
    		if(nodeParams == null){
    			nodeParams = getTier0JobsEmptySummary();
    			htNodes.put(host+"_"+ip, nodeParams);
    		}
    		params.addToDataArray(nodeParams);
    		nodeParams.addToParam("ActiveJobs", 1);
    		return true;
    	}
    }
    
////////////////////////////////////////////////////////////////
//  Default initializations
////////////////////////////////////////////////////////////////
    
    public Tier0Filter(String farmName) {
        super(farmName);
        
        vDebugResults = new Vector();
        sAppNames = new TreeSet();
        htApps = new Hashtable();
        
        reloadConfParams();
    }

	public monPredicate[] getFilterPred() {
		return null;
	}

	public String getName() {
		return Name;
	}

	public long getSleepTime() {
		return T0F_SLEEP_TIME;
	}
	
	/** Reload parameters */
	private void reloadConfParams(){
        PARAM_EXPIRE = AppConfig.getl("Tier0Filter.PARAM_EXPIRE", 900) * 1000;
        T0F_SLEEP_TIME = AppConfig.getl("Tier0Filter.SLEEP_TIME", 120) * 1000;		
        APP_NAMES = AppConfig.getProperty("Tier0Filter.APP_NAMES", "PromptReco");
        if(APP_NAMES != null){
        	sAppNames.clear();
        	StringTokenizer stk = new StringTokenizer(APP_NAMES, ",");
        	while(stk.hasMoreTokens()){
        		sAppNames.add(stk.nextToken().trim());
        	}
        	if(logger.isLoggable(Level.FINE))
        		logger.fine("Summarizing Tier0 apps: "+sAppNames);
        }else{
        	logger.warning("Not summarizing any Tier0 apps!!");
        }
	}
    
////////////////////////////////////////////////////////////////
//  Prepare received data for our structures
////////////////////////////////////////////////////////////////
    
	/** This is called when I receive a Result or an eResult */
	synchronized public void notifyResult(Object o) {
		if(o == null)
			return;
		if(o instanceof Result){
			Result r = (Result) o;
			if(logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Got result:"+r);
			String [] cn = r.ClusterName.split("_");
			if(cn.length < 2 || cn.length > 3)
				return;
			String something = cn[0];
			String appName = cn[1];
			String somethingElse = null;
			if(cn.length == 3)
				somethingElse = cn[2];
			if(! sAppNames.contains(appName))
				return;
			String [] nn = r.NodeName.split("-");
			if(nn.length != 3)
				return;
			String host = nn[0];
			String [] nnLast = nn[2].split("_");
			if(nnLast.length != 4)
				return;
			String ip = nnLast[3];
			addTier0JobData(something+"_"+appName, host, ip, somethingElse+"_"+r.NodeName, r);
		}
		//else if(o instanceof eResult){
			//eResult er = (eResult) o;
			// don't care for now
		//}
	}
	
	/** Add data for Tier0 jobs */
	private void addTier0JobData(String fullAppName, String host, String ip, String fullJobID, Result r){
		if(logger.isLoggable(Level.FINER))
			logger.log(Level.FINER, "Adding Tier0JobData app="+fullAppName+" node="+host+"_"+ip+" jobID="+fullJobID);
		Hashtable htJobsInApp = (Hashtable) htApps.get(fullAppName);
		if(htJobsInApp == null){
			htJobsInApp = new Hashtable();
			htApps.put(fullAppName, htJobsInApp);
		}
		Tier0JobInfo job = (Tier0JobInfo) htJobsInApp.get(fullJobID);
		if(job == null){
			job = new Tier0JobInfo(fullAppName, host, ip);
			htJobsInApp.put(fullJobID, job);
		}
		job.addResult(r);
	}

	private DataArray getTier0JobsEmptySummary(){
		return new DataArray(new String[] {"cpu_time", "rss", "virtualmem", "workdir_size",
				"NEvents", "RecoSize", "ActiveJobs"});
	}
	
////////////////////////////////////////////////////////////////
// Summarize all received data
////////////////////////////////////////////////////////////////

	/** Summarize all Tier0Jobs and produce results for each node, per application */ 
	private void summarizeTier0Jobs(Vector rez){
		Hashtable htNodes = new Hashtable();
		for(Iterator ait = htApps.entrySet().iterator(); ait.hasNext(); ){
			htNodes.clear();
			Map.Entry ame = (Map.Entry) ait.next();
			String appName = (String) ame.getKey();
			Hashtable htJobsInApp = (Hashtable) ame.getValue();
			for(Iterator jit = htJobsInApp.entrySet().iterator(); jit.hasNext(); ){
				Map.Entry jme = (Map.Entry) jit.next();
				String jobID = (String ) jme.getKey();
				Tier0JobInfo job = (Tier0JobInfo) jme.getValue();
				if(! job.summarize(htNodes)){
					jit.remove();
					if(logger.isLoggable(Level.FINEST)){
						logger.log(Level.FINEST, "Removing Tier0JobInfo "+jobID+"@"+appName);
					}
				}
			}
			if(htJobsInApp.size() == 0)
				ait.remove();
			for(Iterator nit = htNodes.entrySet().iterator(); nit.hasNext(); ){
				Map.Entry nme = (Map.Entry) nit.next();
				String nodeName = (String) nme.getKey();
				DataArray nodeDA = (DataArray) nme.getValue();
				DataArray.addRezFromDA(rez, farm.name, appName+"_Summary", nodeName, nodeDA);
			}
		}
	}
	
	synchronized public Object expressResults() {
		logger.log(Level.FINE, "expressResults was called");
		reloadConfParams();
		Vector rez=new Vector();
		
		summarizeTier0Jobs(rez);
		
		rez.addAll(vDebugResults);
		vDebugResults.clear();
		if(logger.isLoggable(Level.FINEST)){
			StringBuffer sb = new StringBuffer();
			for(Iterator rit = rez.iterator(); rit.hasNext(); ){
				Result r = (Result) rit.next();
				sb.append(r.toString()+"\n");
			}
			logger.log(Level.FINEST, "Summarised results are: "+sb.toString());
		}
		return rez;
	}
}
