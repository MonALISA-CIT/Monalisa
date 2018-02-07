
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
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.DataArray;
import lia.util.ntp.NTPDate;

/**
 * Filter for CMS ProdAgent monitoring data.
 * 
 * 
 * It computes summaries for data of this kind
 * - ProdAgent_$Assignment_$AgentID
 *    - some_long_and_node_ID
 *      - SyncCE,NEvents
 *      
 * where:
 * - $Assignmnent can contain "_"
 * - $AgentID doesn't have "_"
 * - SyncCE is string
 * 
 * It produces three summaries, for $Assignment, $AgentID and SyncCE's values
 * - Summary_Assignment_$ShortAssignment
 *   - $Assignment
 *     - NEvents (SUM for all entries for the same assignment)
 * - Summary_ProdAgent_$ProdAgent
 *   - $Assignment
 *     - NEvents (SUM for all the Assignments with this ProdAgent)
 * - Summary_CE_$CE
 *   - $Assignment
 *     - NEvents (SUM for all the Assignments with this CE)
 *     
 * $ShortAssignment is at least 20 chars long, ending at the first _ or -
 * 
 * The number of $name-s in the summaries can be potentially large and
 * changes in time, so the storage of these values should be done in such
 * a way that they are not stored forever.
 * 
 * @author catac
 */
public class ProdAgentFilter extends GenericMLFilter {
	
	private static Logger logger = Logger.getLogger("ProdAgentFilter");
	
	/** The name of this filter */
    public static final String Name = "ProdAgentFilter";
    
    /** How often we should produce data. At these intervals, expressResults is called */
    private long PAF_SLEEP_TIME;
    
    /** How soon we should drop data about the received parameters */
    private long PARAM_EXPIRE;
    
    /** The minimum length for the Assignment's short name */
    private int MIN_SHORT_NAME_LEN;
    
    /** The maximum length for the Assignment's short name */
    private int MAX_SHORT_NAME_LEN;
    
    
    Hashtable htJobs;       // key=assignment_prodAgent; val=Hashtable[key=jobID; val=JobInstanceInfo]

////////////////////////////////////////////////////////////////
//  Internal data structures for each summarized thing
////////////////////////////////////////////////////////////////

    class JobInstanceInfo {
    	String assignment;	// assignment name
    	String shortAssignment; // short name for the assignment
    	String agentID;		// agent name
    	String syncCE;		// the computing element
    	
    	DataArray prvParams = getProdAgentEmptySummary();
    	DataArray crtParams = getProdAgentEmptySummary();
    	boolean firstSummary = true;
    	long lastUpdateTime;
    	
    	JobInstanceInfo(String assignment, String agentID){
    		this.assignment = assignment;
    		this.agentID = agentID;
    		this.syncCE = "unknown";
    		this.lastUpdateTime = NTPDate.currentTimeMillis();
    		
    		// try to guess a nice shorter name for the assignment
    		int i = assignment.indexOf("_", MIN_SHORT_NAME_LEN);
    		int j = assignment.indexOf("-", MIN_SHORT_NAME_LEN);
    		if(i == -1)
    			i = Integer.MAX_VALUE;
    		if(j == -1)
    			j = Integer.MAX_VALUE;
    		int k = Math.min(Math.min(i, j), Math.min(assignment.length(), MAX_SHORT_NAME_LEN));
    		this.shortAssignment = assignment.substring(0, k);
    	}
    	
    	/** Add a result with updates of the parameters of this job */
    	void addResult(Result r){
    		lastUpdateTime = NTPDate.currentTimeMillis();
    		
    		for(int i=0; i<r.param_name.length; i++){
    			String paramName = r.param_name[i];
    			double paramValue = r.param[i];
    			// update the values of the parameters we are interested in
    			for(int j=0; j<crtParams.params.length; j++){
    				if(paramName.equals(crtParams.params[j])){
    					// for rates, add the new value only if it's bigger than the previous one
    					if(providesRate(paramName) && (crtParams.values[j] < paramValue))
    						crtParams.values[j] = paramValue;
    				}
    			}
    		}
    	}

    	/** Add a result with updates of the parameters of this job */
    	void addEResult(eResult er){
    		lastUpdateTime = NTPDate.currentTimeMillis();

    		for(int i=0; i<er.param_name.length; i++){
    			String paramName = er.param_name[i];
    			if(paramName.equals("SyncCE"))
    				this.syncCE = (String) er.param[i];
    		}
    	}
    	
    	/** Get the summary params (or create if not existing) for a given key in a view */
    	private DataArray getViewParams(Hashtable htView, String firstKey, String secondKey){
    		Hashtable htSubView = (Hashtable) htView.get(firstKey);
    		if(htSubView == null){
    			htSubView = new Hashtable();
    			htView.put(firstKey, htSubView);
    		}
    		DataArray params = (DataArray) htSubView.get(secondKey);
    		if(params == null){
    			params = getProdAgentEmptySummary();
    			htSubView.put(secondKey, params);
    		}
    		return params;
    	}
    	
    	/** Provide the summaries for the aggregated views */
    	boolean summarize(Hashtable htAssignments, Hashtable htPAgents, Hashtable htCEs){
    		long now = NTPDate.currentTimeMillis();
    		double timeInterval = PAF_SLEEP_TIME / 1000.0d;
    		if(now - lastUpdateTime > PARAM_EXPIRE){
    			return false;
    		}
    		if(firstSummary){
    			prvParams.setAsDataArray(crtParams);
    			firstSummary = false;
    		}
//    		DataArray report = new DataArray(); 
    		DataArray rates = new DataArray();
    		crtParams.subDataArrayTo(prvParams, rates);
    		prvParams.setAsDataArray(crtParams);
    		for(int i=0; i<rates.params.length; i++)
    			if(providesRate(rates.params[i]))
    				rates.setParam(rates.params[i]+"_R", rates.values[i] / timeInterval);
    		DataArray summary;
    		summary = getViewParams(htAssignments, shortAssignment, assignment);
    		rates.addToDataArray(summary);
    		summary = getViewParams(htPAgents, agentID, assignment);
    		rates.addToDataArray(summary);
    		summary = getViewParams(htCEs, syncCE, assignment);
    		rates.addToDataArray(summary);
    		return true;
    	}
    }
    
////////////////////////////////////////////////////////////////
//  Default initializations
////////////////////////////////////////////////////////////////
    
    public ProdAgentFilter(String farmName) {
        super(farmName);
        htJobs = new Hashtable();
        reloadConfParams();
    }

	public monPredicate[] getFilterPred() {
		return null;
	}

	public String getName() {
		return Name;
	}

	public long getSleepTime() {
		return PAF_SLEEP_TIME;
	}
	
	/** Reload parameters */
	private void reloadConfParams(){
        PARAM_EXPIRE = AppConfig.getl("ProdAgentFilter.PARAM_EXPIRE", 300) * 1000;
        PAF_SLEEP_TIME = AppConfig.getl("ProdAgentFilter.SLEEP_TIME", 120) * 1000;
        MIN_SHORT_NAME_LEN = AppConfig.geti("ProdAgentFilter.MIN_SHORT_NAME_LEN", 15);
        MAX_SHORT_NAME_LEN = AppConfig.geti("ProdAgentFilter.MAX_SHORT_NAME_LEN", 30);
	}
    
////////////////////////////////////////////////////////////////
//  Prepare received data for our structures
////////////////////////////////////////////////////////////////
    
	/** This is called when I receive a Result or an eResult */
	synchronized public void notifyResult(Object o) {
		if(o == null)
			return;
		if(logger.isLoggable(Level.FINEST))
			logger.log(Level.FINEST, "Got result:"+o);
		Result r = null;
		eResult er = null;
		String clusterName = null;
		String nodeName = null;
		if(o instanceof Result){
			r = (Result) o;
			clusterName = r.ClusterName;
			nodeName = r.NodeName;
		}else if(o instanceof eResult){
			er = (eResult) o;
			clusterName = er.ClusterName;
			nodeName = er.NodeName;
		}else{
			logger.log(Level.WARNING, "Got unknown object (not Result, not eResult): "+o);
			return;
		}
		if(clusterName == null || nodeName == null)
			return;
		if(! clusterName.startsWith("ProdAgent"))
			return;
		int idxFirst_ = clusterName.indexOf("_");
		int idxLast_  = clusterName.lastIndexOf("_");
		if(idxFirst_ >=0 && idxFirst_ < idxLast_ && idxLast_ < clusterName.length()){
			String assignment = clusterName.substring(idxFirst_ + 1, idxLast_);
			String pAgentID = clusterName.substring(idxLast_+1);
			String key = nodeName+pAgentID;
			JobInstanceInfo jii = (JobInstanceInfo) htJobs.get(key);
			if(jii == null){
				jii = new JobInstanceInfo(assignment, pAgentID);
				htJobs.put(key, jii);
			}
			if(logger.isLoggable(Level.FINER))
				logger.log(Level.FINER, "Adding ProdAgentJobData assignment="+assignment+" pAgentID="+pAgentID);
			if(r != null)
				jii.addResult(r);
			else
				jii.addEResult(er);
		}
	}
	
	private DataArray getProdAgentEmptySummary(){
		return new DataArray(new String[] {"NEvents", "NEvents_R"});
	}
	
	private boolean providesRate(String param){
		if(param.equals("NEvents"))
			return true;
		return false;
	}
	
////////////////////////////////////////////////////////////////
// Summarize all received data
////////////////////////////////////////////////////////////////

	/** add a certain type of summary */
	private void addSummaryResults(Vector rez, Hashtable summary, String type){
		for(Iterator nit = summary.entrySet().iterator(); nit.hasNext(); ){
			Map.Entry nme = (Map.Entry) nit.next();
			String groupName = (String) nme.getKey();
			Hashtable subView = (Hashtable) nme.getValue();
			DataArray sum = getProdAgentEmptySummary();
			for(Iterator vit = subView.entrySet().iterator(); vit.hasNext(); ){
				Map.Entry vme = (Map.Entry) vit.next();
				String nodeName = (String) vme.getKey();
				DataArray nodeDA = (DataArray) vme.getValue();
				nodeDA.addToDataArray(sum);
				DataArray.addRezFromDA(rez, farm.name, "Summary_"+type+"_"+groupName, nodeName, nodeDA);
			}
			DataArray.addRezFromDA(rez, farm.name, "Summary_"+type+"_"+groupName, "_TOTALS_", sum);
		}
	}
	
	/** Summarize all ProdAgentJobs and produce results for each Assignment, AgentID and SyncCE */ 
	private void summarizeProdAgentJobs(Vector rez){
		Hashtable htAssignments = new Hashtable();
		Hashtable htPAgents = new Hashtable();
		Hashtable htCEs = new Hashtable();
		for(Iterator jit = htJobs.entrySet().iterator(); jit.hasNext(); ){
			Map.Entry jme = (Map.Entry) jit.next();
			String jobID = (String) jme.getKey();
			JobInstanceInfo jii = (JobInstanceInfo) jme.getValue();
			if(! jii.summarize(htAssignments, htPAgents, htCEs)){
				jit.remove();
				if(logger.isLoggable(Level.FINEST))
					logger.log(Level.FINEST, "Removing JobInstanceInfo "+jobID);
			}
		}
		addSummaryResults(rez, htAssignments, "Assignments");
		addSummaryResults(rez, htPAgents, "ProdAgents");
		addSummaryResults(rez, htCEs, "CEs");
	}
	
	synchronized public Object expressResults() {
		logger.log(Level.FINE, "expressResults was called");
		reloadConfParams();
		Vector rez=new Vector();
		
		summarizeProdAgentJobs(rez);
		
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
