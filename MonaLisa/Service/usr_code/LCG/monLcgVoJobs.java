/**
 * Module used to collect accounting statistics for the VOs. It obtains
 * information Condor (condor_q and the Condor history file), PBS, SGE and LSF.
 * The location of the job managers must be set in environment variables like 
 * CONDOR_LOCATION, PBS_LOCATION etc. If the Condor local directory (i.e., the
 * directory which contains spool/, log/ etc.) is in other location than 
 * CONDOR_LOCATION, the environment variable CONDOR_LOCAL_DIR must also be set.
 */
//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

/*
 * Accounting information about a job. 
 */
class JobInfoExt {
	public String jobManager; // the name of the job manager (CONDOR, PBS etc.)
    public String id; // String of the following form: <job_manager>_<job_id>
    public String user;
    public String date;  // the date when the job was submitted
    public String time;  // the time when the job was submitted
    public long run_time;  // wall clock time in seconds
    public long cpu_time;  // CPU time in seconds
    public String status;
    public String priority;
    public double size;  // the total virtual size of the job in MB
    public double disk_usage;  // disk usage in MB
    public String VO; // the VO that owns this job
    public String serverName; // the host on which the job was submitted
    
    public JobInfoExt() {
    	jobManager = id = VO = null;
    	user = date = time = null;
    	status = priority = null;
    	run_time = cpu_time = 0;
    	size = disk_usage = 0.0;
    	serverName = null;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("jobManager=").append(jobManager);
        sb.append("\tid=").append(id);
        sb.append("\tuser=").append(user);
        sb.append("\tdate=").append(date);
        sb.append("\trun_time=").append(run_time);
        sb.append("\tcpu_time=").append(cpu_time);
        sb.append("\tstatus=").append(status);
        sb.append("\tpriority=").append(priority);
        sb.append("\tsize=").append(size);
        sb.append("\tdisk_usage=").append(disk_usage);
        sb.append("\tVO=").append(VO);
        if (serverName != null)
        	sb.append("\tserverName").append(serverName);
        return sb.toString();
    }
}

/**
 * Class that holds statistical information about the jobs belonging to a VO. The
 * parameters ending with "_t" hold total (cumulated) values since the service
 * started; the others hold only the value for the last time interval.
 */
class VOSummaryExt implements Cloneable {
	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monLcgVoJobs");
	  
    /* parameters obtained from a queue manager: */
	long run_time_t; // total wall clock time for the jobs, in seconds
	long cpu_time_t; // total CPU time, in seconds
    double size; // total size of the jobs, in MB
    double disk_usage; // disk usage in MB
    
    long runningjobs; // total number of currently runnifng jobs 
    long idlejobs;
    long heldjobs;
    long finishedjobs;
    long unknownjobs;
    
    long submittedJobs;
    long finishedJobs;
    long finishedJobs_t;
    long submittedJobs_t;
    
    /* parameters obtained from the Condor history file: */
    Hashtable paramsCondorH;
    
    /* parameters obtained from LSF (bjobs command) */
    Hashtable paramsLSF;
    
    public VOSummaryExt() {
        run_time_t = 0; cpu_time_t = 0;
        size = 0.0; disk_usage = 0.0;
        
        runningjobs   = 0;
        idlejobs      = 0;
        heldjobs      = 0;
        finishedjobs  = 0;
        unknownjobs   = 0;
        
        submittedJobs = 0; finishedJobs = 0;
        finishedJobs_t = 0; submittedJobs_t = 0;
        
        paramsCondorH = new Hashtable();
        paramsCondorH.put("CondorFinishedJobs_t", new Double(0));
        paramsCondorH.put("CondorRunTime_t", new Double(0));
        paramsCondorH.put("CondorCpuUsr_t", new Double(0));
        paramsCondorH.put("CondorCpuSys_t", new Double(0));
        paramsCondorH.put("CondorBytesSent_t", new Double(0));
        paramsCondorH.put("CondorBytesRecvd_t", new Double(0));
        paramsCondorH.put("CondorFileReadBytes_t", new Double(0));
        paramsCondorH.put("CondorFileWriteBytes_t", new Double(0));
        paramsCondorH.put("CondorMemoryUsage", new Double(0));
        paramsCondorH.put("CondorDiskUsage", new Double(0));
        
        paramsLSF = new Hashtable();
        paramsLSF.put("MemoryUsageLSF", new Double(0));
        paramsLSF.put("SwapUsageLSF", new Double(0));
        paramsLSF.put("CPUTimeLSF", new Double(0));
        paramsLSF.put("RunTimeLSF", new Double(0));
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("run_time_t=").append(run_time_t);
        sb.append("cpu_time_t=").append(cpu_time_t);
        sb.append("\tsize=").append(size);
        sb.append("\tdiskUsage=").append(disk_usage);
        sb.append("\trunningjobs=").append(runningjobs);
        sb.append("\tidlejobs=").append(idlejobs);
        sb.append("\theldjobs=").append(heldjobs);
        sb.append("\tfinishedjobs=").append(finishedjobs);
        sb.append("\tunknownjobs=").append(unknownjobs);
        
        sb.append("\tsubmittedJobs=").append(submittedJobs);
        sb.append("\tfinishedJobs=").append(submittedJobs);
        sb.append("\tfinishedJobs_t=").append(finishedJobs_t);
        sb.append("\tsubmittedJobs_t=").append(submittedJobs_t);
        
        Enumeration condorE = paramsCondorH.keys();
        while(condorE.hasMoreElements()) {
        	String paramName = (String)condorE.nextElement();
        	Double paramValue = (Double)paramsCondorH.get(paramName);
        	sb.append("\t" + paramName + "=").append(paramValue);
        }
        
        Enumeration lsfE = paramsLSF.keys();
        while(lsfE.hasMoreElements()) {
        	String paramName = (String)condorE.nextElement();
        	Double paramValue = (Double)paramsLSF.get(paramName);
        	sb.append("\t" + paramName + "=").append(paramValue);
        }
        
        return sb.toString();
    }
    
    public Object clone() {
    	VOSummaryExt newInstance = null; 
    	try {
    		newInstance = (VOSummaryExt)super.clone();
    	} catch (CloneNotSupportedException e) {
    		logger.log(Level.WARNING, "[monLcgVoJobs] Got exception:", e);
    	}   
    	
    	/* copy the two hashtables to the new instance */
    	newInstance.paramsCondorH = new Hashtable();
    	Enumeration condorE = paramsCondorH.keys();
    	while(condorE.hasMoreElements()) {
    		String key = (String)condorE.nextElement();
    		Double val = (Double)paramsCondorH.get(key);
    		newInstance.paramsCondorH.put(new String(key), new Double(val.doubleValue()));
    	}
    	
    	newInstance.paramsLSF = new Hashtable();
    	Enumeration lsfe = paramsLSF.keys();
    	while(lsfe.hasMoreElements()) {
    		String key = (String)lsfe.nextElement();
    		Double val = (Double)paramsLSF.get(key);
    		newInstance.paramsLSF.put(new String(key), new Double(val.doubleValue()));
    	}
    	return newInstance;
    }
}

/**
 * Module that collects accounting information (for the moment, from 
 * Condor and PBS).
 */
public class monLcgVoJobs extends monExtVoModules implements MonitoringModule {
	
	static final long serialVersionUID = 1407200518051980L;
	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monLcgVoJobs");
	protected String    monalisaHome  = null;
	
	/** The name of the farm */
	protected String farmName = null;
	
	/** This should be set to false if the module is run from usr_code, so that
	 * status emails will not be sent.
	 */ 
	private static boolean isInternalModule = true;
	
      //save some GC-s! and some speed at .equals()
    private static final String   CONDOR      =   "CONDOR"; 
    private static final String   CONDOR2     =   "CONDOR2"; 
    private static final String   PBS         =   "PBS"; 
    private static final String   LSF         =   "LSF";
    private static final String   FBS         =   "FBS";
    private static final String   SGE         =   "SGE";

	private static final long SEC_MILLIS      =   1000;
	private static final long MIN_MILLIS      =   60*SEC_MILLIS;
	private static final long MIN_SEC      =   60;
	private static final long HOUR_MILLIS     =   60*MIN_MILLIS;
	private static final long DAY_MILLIS      =   24*HOUR_MILLIS;
	
	static public final String VoModulesDir = "@ML_VoModulesDir@";
	protected static String   OsName            = "linux";
	protected        String   ModuleName        = null;
	protected        String[] ResTypes          = null;
	protected static String MyModuleName ="monLcgVoJobs";
	
	//Ramiro  
	//protected static final String VoModulesDir = "VoModules-v0.9";
	boolean testmode = false;
	
	private static boolean firstInit = true;
	private long lastRun = 0;
	
	/** Contains the names of the available job managers as keys and their 
	 * locations as values. */
	protected HashMap jobMgr       = new HashMap();
	/** Contains the names of the available job managers as keys and their 
	 * locations as values. */
	protected HashMap jobMgrLocations = new HashMap();
	
	/** Contains the default locations of the job managers. */
	protected Hashtable defaultLocations = new Hashtable();
	
	/** The location of the Condor history file. */
	protected String condorHistFile = null;
	
	/** This flag disables the use of the "plain" condor_q command in 
	 * addition to condor_q -l
	 */
	protected boolean condorQuickMode = false;
    /** This flag enables the parsing of the Condor history file. */
	protected boolean checkCondorHist = true;

	protected VO_CondorAccounting condorAcc = null;
	
	static public String testpath = "/home/weigand/MonALISA/MonaLisa.v098/Service/usr_code/VoModules/testdata";
	
	/** The results that this module provides. */
	protected static String[] MyResTypes = {"RunningJobs", "IdleJobs", "HeldJobs",
		"SubmittedJobs", "RunTime", "DiskUsage", "JobsSize"};
	
	protected static String emailNotifyProperty = "lia.Monitor.notifyLcgVoJobs";
	
	/** The number of Condor commands that will be issued. */
	protected int nCondorCommands = 0;
	
	boolean haveError = false;
	
	/* Indicates whether a warning should be logged if there was an error when
	 * executing a command. A warning will only be 
	 * logged the first time in a series of consecutive errors.
	 */
	//boolean cmdErrorWarning = true;
	
	/**** information from the previous doProcess():  ******/
	/* information for each job (the keys are the job IDs, the 
	 elements are JobInfoExt objects) */
	private Hashtable jobsInfo = new Hashtable();
	/* summary information for each VO (the keys are VO names, the elements
	 are VoSummaryExt objects) */
	private Hashtable VOjobsInfo = new Hashtable();
	/* total for all the VOs */
	VOSummaryExt oTotalVOS = new VOSummaryExt();
	
	/**** information from the current doProcess():  ******/
	/* information for each job (the keys are the job IDs, the 
	 elements are JobInfoExt objects) */
	private Hashtable currentJobsInfo = new Hashtable();
	/* summary information for each VO */
	private Hashtable currentVOjobsInfo = new Hashtable();
	/* total for all the VOs */
	VOSummaryExt cTotalVOS = new VOSummaryExt();
  
	/*
	 static {
      try {
          String sMAP_FILE_WATCHDOG_CHECK_RATE = AppConfig.getProperty("lia.Monitor.modules.monVoModules", "20");
          MAP_FILE_WATCHDOG_CHECK_RATE = Long.valueOf(sMAP_FILE_WATCHDOG_CHECK_RATE).longValue()*1000;
      }catch(Throwable t) {
          MAP_FILE_WATCHDOG_CHECK_RATE = 30 * 1000;
      }
      }
      */
  
	private boolean shouldPublishJobInfo = true;
	
//	/** Specifies whether the local Condor schedd daemon will be queried. */
//    boolean condorUseLocal = false;
    
    /** Specifies whether all Condor the schedd daemons will be queried. */
    boolean condorUseGlobal = false;
    
    /** The names of the Condor schedd daemos that we will query. */
    Vector condorServers = new Vector();
    
    /** Specifies whether some remote hosts should be used to execute the commands */
    protected boolean	UseRemote = false;
    
    /** The name of the remote host used to execute the commands. */
    protected String remoteHostName = null;
    
    /** This is set to true if we find decreasing CPU time or run time for a job. */
    boolean inconsistentJobInfo = false;
    
    /** If this flag is true, the names of VOs will be sent wiith mixed cases
     * in the result.
     */
    boolean mixedCaseVOs = false;
    
    /** If this flag is true, the total results (sum for all the VOs) will be
     * in separate clusters for each module instance.
     */ 
    boolean singleTotals = false;
    
	public monLcgVoJobs () { 
		super(MyModuleName, MyResTypes, emailNotifyProperty);
        ModuleName = MyModuleName;
		isRepetitive = true;
		canSuspend = false;
		info.ResTypes = ResTypes();
		info.setName("monLcgVoJobs");
	} 
	
	public MNode         getNode()   { return this.Node ; }
	public String[]      ResTypes()  { return MyResTypes; }
	public String        getOsName() { return OsName; }
	public MonModuleInfo getInfo()   { return info; }
  
  /**
   * Initialization of this module.
   * configuration file entry: monVoJobs{test,debug,location=}%30
   */
  public  MonModuleInfo init( MNode inNode , String args ) {
    String methodName = "init";
    String serverName = null;
    String argList[] = new String[]{};
    Node = inNode;

    logger.info("Processing arguments for the module monLcgVoJobs...");
    // ------------------------
    // Check the argument list
    // ------------------------
    if ( args != null ) {
      //check if file location or globus_location are passed
      argList = args.split("(\\s)*,(\\s)*"); //requires java 1.4
      testmode = false;
      
      for ( int j=0; j < argList.length; j ++ ) {
    	  
    	  if (argList[j].toLowerCase().startsWith("mapfile")) {
    		  try {
    			  mapfile = argList[j].split("(\\s)*=(\\s)*")[1].trim();
    			  logger.log(Level.INFO, "Using map file: " + 
    					  mapfile);
    		  } catch(Throwable t) {			
    			  logger.log(Level.INFO, " Got exception parsing mapfile option", t);
    		  }	                  
    		  continue;
    	  } 
    	  
    	  if (argList[j].toLowerCase().startsWith("siteinfofile")) {
    		  try {
    			  lcgInfoFile = argList[j].split("(\\s)*=(\\s)*")[1].trim();
    			  logger.log(Level.INFO, "Using LCG site info file: " + 
    					  lcgInfoFile);
    		  } catch(Throwable t) {			
    			  logger.log(Level.INFO, " Got exception parsing mapfile option", t);
    		  }	                  
    		  continue;
    	  }

        if (argList[j].startsWith("test")) {
          testmode = true;
          continue;
        } 
        
    	/* the Grid distribution (OSG, LCG2.4) */
        if (argList[j].toLowerCase().startsWith("griddistribution")) {
        	try {
				gridDistribution = argList[j].split("(\\s)*=(\\s)*")[1].trim();
			}catch(Throwable t){                      
				logger.log(Level.INFO, " Got exception parsing GridDistribution option", t);
				gridDistribution = "OSG";
			}
        }
        
        if(argList[j].toLowerCase().startsWith("donotpublishjobinfo")) {
            shouldPublishJobInfo = false;
            continue;
        }
        
        if(argList[j].toLowerCase().startsWith("singletotals")) {
            singleTotals = true;
        }
        
        /* flag to enable the use of condor_q -global */
        if (argList[j].toLowerCase().startsWith("condoruseglobal")) {
			condorUseGlobal = true;
			logger.info("CondorUseGlobal option enabled");
            continue;
		} 

		/* flag to disable the use of the "plain" condor_q command */
		if (argList[j].toLowerCase().startsWith("condorquickmode")) {
			condorQuickMode = true;
			logger.info("CondorQuickMode option enabled");
            continue;
		} 
		
		/* flag to enable the parsing of the Condor history file */
		if (argList[j].toLowerCase().startsWith("condorhistorycheck")) {
			checkCondorHist = true;
			logger.info("CheckCondorHistory option enabled");
		} 
		
		/* flag to disable the parsing of the Condor history file */
		if (argList[j].toLowerCase().startsWith("nocondorhistorycheck")) {
			checkCondorHist = false;
			logger.info("CheckCondorHistory option disabled");
		} 
		
		/* flag to enable the use of mixed cases in VOs names */
		if (argList[j].toLowerCase().indexOf("mixedcasevos") != -1) {
			mixedCaseVOs = true;
			logger.info("MixedCaseVOs option enabled");
            continue;
		}
 
		if (argList[j].toLowerCase().indexOf("cansuspend") != -1) {
		    boolean cSusp = false;
		    try {
			cSusp = Boolean.valueOf(argList[j].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
		    }catch(Throwable t){
			cSusp = false;
		    }
		    canSuspend = cSusp;
		    continue;
		}

		if (argList[j].toLowerCase().startsWith("server")) {
			try {
				serverName = argList[j].split("(\\s)*=(\\s)*")[1].trim();
				condorServers.add(serverName);
			}catch(Throwable t){                      
				logger.log(Level.INFO, " Got exception parsing CondorSchedd option", t);
				serverName = null;
			}
			logger.log(Level.INFO, "Added Condor Server to query: " + serverName);
		}
        
		/* the name of a remote host on which we'll issue the commands */
		if (argList[j].toLowerCase().startsWith("remotehost")) {
			if(!UseRemote)
				UseRemote = true;
			try {
				remoteHostName = argList[j].split("(\\s)*=(\\s)*")[1].trim();
				logger.log(Level.INFO, "Using remote host: " + remoteHostName);
			} catch(Throwable t){
				logger.log(Level.INFO, " Got exception parsing RemoteHost option", t);
				remoteHostName = null;
			}
        }

		/* the location of the Condor history file */
		if (argList[j].toLowerCase().startsWith("condorhistoryfile")) {
			try {
				condorHistFile = argList[j].split("(\\s)*=(\\s)*")[1].trim();
				logger.log(Level.INFO, "Added remote host: " + remoteHostName);
			} catch(Throwable t){			
				logger.log(Level.INFO, " Got exception parsing CondorHistoryFile option", t);
			}
		}
		
		/* the location of a job manager, e.g. CONDOR_LOCATION=/usr/local/condor */
		if (argList[j].indexOf("_LOCATION") != -1) {
			try {
				String[] sa = argList[j].split("(\\s)*=(\\s)*");
				String manager = sa[0].trim().replaceFirst("_LOCATION", "");
				String location = sa[1].trim();
				jobMgrLocations.put(manager.toUpperCase(), location);
				if (manager.equals("CONDOR"))
					jobMgrLocations.put("CONODR2", location);
				
				logger.log(Level.INFO, "Using " + manager + " location: " + 
						location);
			} catch(Throwable t){              
				logger.log(Level.INFO, " Got exception parsing LOCATION option", t);               				
			}
        }
		
		/* request to use a job manager, e.g. JobManager = PBS */
		if (argList[j].toLowerCase().startsWith("jobmanager")) {
			try {
				String[] sa = argList[j].split("(\\s)*=(\\s)*");
				String manager = sa[1].trim().toUpperCase();
				/* if the user does not provide a location we'll use a 
				   default one */
				if (jobMgrLocations.get(manager) == null)
					jobMgrLocations.put(manager.toUpperCase(), "");
				logger.log(Level.INFO, "Using job manager " + manager);
			} catch(Throwable t){              
				logger.log(Level.INFO, " Got exception parsing LOCATION option", t);               				
			}
        }
      } // end for 
    } // end if args

    info.ResTypes = ResTypes();
    farmName = inNode.farm.name;
    
    
    return info;
  } // end method
  
  protected void cleanupEnv() {
      //now we can clear ...
      voAccts.clear();
      voMixedCase.clear();
  }
  
  /**
   * (Re-)Initializes some of the data structures.
   * @param firstTime Specifies whether this is the first time we call this function.  
   */
  protected void initializeEnv(boolean firstTime) throws Exception {
	  logger.log(Level.INFO, "[monLcgVoJobs] Initializing monLcgVoJobs module...");
	  if (isInternalModule)
		  setupEmailNotification();
	  loadUserVoMapTable();
	  //logger.finest("### after loading VoMapTable: " + voMixedCase.size() +
		//	  " voAccts: " + voAccts.size());
	  validateMappings();
	  //logger.finest("### after validating mappings: " + voMixedCase.size() +
		//	  " voAccts: " + voAccts.size());
	  printVoTable();
	  
	  /* initialize the summaries for each VO */
	  if (firstTime) {
		  VOjobsInfo = new Hashtable();
		  oTotalVOS = new VOSummaryExt();
	  }
      
      //check for new VOs
      Enumeration voe = voMixedCase.elements();
      while(voe.hasMoreElements()) {
          String voMixedName = (String)voe.nextElement();
          VOSummaryExt vos = (VOSummaryExt)VOjobsInfo.get(voMixedName);
          if(vos == null) {
              VOjobsInfo.put(voMixedName, new VOSummaryExt());
          }
      }
      
      //check for removed VOs
      Enumeration voi = VOjobsInfo.keys();
      while(voi.hasMoreElements()) {
          Object key = voi.nextElement();
          if(!voMixedCase.contains(key)) {
              VOjobsInfo.remove(key);
          }
      }
	  
	  
  } // end method
  

  /**
   *  (Re)sets the environment for this module at the beginning and when the 
   *  configuration file changes.
   */
  protected void setEnvironment() throws Exception {
	  String methodName = "setEnvironment";
	  
	  // -- Establish map table ---
	  //What happens if the map table file is partialy good ?!?! Only a few accounts!
	  
	  //save latest known state
	  tmpVoAccts.clear();
	  tmpVoMixedCase.clear();
	  tmpVoAccts.putAll(voAccts);
	  tmpVoMixedCase.putAll(voMixedCase);
	  
	  cleanupEnv();
	  try {
		  initializeEnv(firstInit);
	  }catch(Exception e1) {
		  throw e1;
	  } 
	  
	  computeVoAcctsDiff();
	  
	  
	  // -- Determine the MonaALisa_HOME ---
	  monalisaHome = AppConfig.getGlobalEnvProperty("MonaLisa_HOME");
	  if ( monalisaHome == null ) {
		  throw new Exception("MonaLisa_HOME environmental variable not set.");
	  } //end if monalisaHome
	  
	  // -------------------------------------------------------
	  // Determine the job managers being used and the location
	  // of their processes for querying the queues.
	  // -------------------------------------------------------
	  if (firstInit) {
		  firstInit = false;    
		  try {
			  getJobManagers();
			  if (jobMgr.get(CONDOR) != null)
				  condorAcc = new VO_CondorAccounting(condorHistFile);
		  } catch(Exception e1){
			  throw e1;
		  }
	  }
	  environmentSet = true;
  } // end method

  /**
   * Determines the job manages available on this system.
   * @throws Exception
   */
  public void getJobManagers() throws Exception {
	  String methodName = "getJobManagers";
	  String location = null;
	  String var      = null;
	  
	  /* fill the table with default locations for the job managers */
	  defaultLocations.put("CONDOR", "/usr/local/condor");
	  defaultLocations.put("PBS", "/usr");
	  defaultLocations.put("LSF", "/usr");
   
    // -------------------------------------------------------
    // Initialize the command for the job manager types 
    // -------------------------------------------------------
    if (testmode) { 
      jobMgr.put(CONDOR,"CONDOR_LOCATION/cat "+monalisaHome+"/Service/usr_code/"+VoModulesDir+"/testdata/condor");
      jobMgr.put(PBS,   "PBS_LOCATION/cat "+monalisaHome+"/Service/usr_code/"+VoModulesDir+"/testdata/pbs"); 
      jobMgr.put(LSF,   "LSF_LOCATION/cat "+monalisaHome+"/Service/usr_code/"+VoModulesDir+"/testdata/lsf");
      jobMgr.put(FBS,   "FBS_LOCATION/cat "+monalisaHome+"/Service/usr_code/"+VoModulesDir+"/testdata/fbs");
      jobMgr.put(SGE,   "SGE_LOCATION/cat "+monalisaHome+"/Service/usr_code/"+VoModulesDir+"/testdata/sge");
    } else {      
    	String command, command2;
    	StringBuffer pbsCmd, lsfCmd, fbsCmd, sgeCmd;
	StringBuffer condorCmd = new StringBuffer();
	StringBuffer condorCmd2 = new StringBuffer();
    	
    	/* make up the Condor commands */ 
    	if (condorUseGlobal) {
    		command = "CONDOR_LOCATION/bin/condor_q -l -global 2>&1";
    		command2 = "CONDOR_LOCATION/bin/condor_q -global 2>&1";
    		updateCondorCommands(condorCmd, condorCmd2, command, command2);
    	} else {
    		if (condorServers.size() == 0) {
    			command = "CONDOR_LOCATION/bin/condor_q -l 2>&1";
    			command2 = "CONDOR_LOCATION/bin/condor_q 2>&1";
    			updateCondorCommands(condorCmd, condorCmd2, command, command2);
    			/*
    			condorCmd.append("CONDOR_LOCATION/bin/condor_q -l 2>&1 && echo OK > " +
    					checkFiles.lastElement());
    					*/
    		} else {
                for (int ind = 0; ind < condorServers.size(); ind++) {                                              
                    command = "CONDOR_LOCATION/bin/condor_q -l -name " +
                    condorServers.get(ind) + " 2>&1 ";
                    command2 = "CONDOR_LOCATION/bin/condor_q -name " +
                    condorServers.get(ind) + " 2>&1 ";
                    updateCondorCommands(condorCmd, condorCmd2, command, command2);
                }
            }
    	}
    	
    	pbsCmd = new StringBuffer("PBS_LOCATION/bin/qstat 2>&1 && echo ML_OSGVOJOBS_OK");     			
    	lsfCmd = new StringBuffer("LSF_LOCATION/bin/bjobs -l 2>&1 && echo ML_OSGVOJOBS_OK");    			
    	fbsCmd = new StringBuffer("FBS_LOCATION/bin/fbs lj 2>&1 && echo ML_OSGVOJOBS_OK");    			
    	sgeCmd = new StringBuffer("SGE_LOCATION/bin/SGE_ARCH/qstat -ext 2>&1 && echo ML_OSGVOJOBS_OK");
    	
    	if(UseRemote) {
    		/* the commands will be issued on a remote host via ssh */
    		    		    		
    		condorCmd.insert(0, "ssh " + remoteHostName + " '");
    		condorCmd.append("'");    		
    		condorCmd2.insert(0, "ssh " + remoteHostName + " '");
    		condorCmd2.append("'");
    		pbsCmd.insert(0, "ssh " + remoteHostName + " '");
    		pbsCmd.append("'");    		
    		lsfCmd.insert(0, "ssh " + remoteHostName + " '");
    		lsfCmd.append("'");
    		fbsCmd.insert(0, "ssh " + remoteHostName + " '");
    		fbsCmd.append("'");    		    		
    		sgeCmd.insert(0, "ssh " + remoteHostName + " '");
    		sgeCmd.append("'");
    	}
    	
    	jobMgr.put(CONDOR, new String(condorCmd));
    	jobMgr.put(CONDOR2, new String(condorCmd2));
    	jobMgr.put(PBS, new String(pbsCmd));  
		jobMgr.put(LSF, new String(lsfCmd));     
		jobMgr.put(FBS, new String(fbsCmd));     
		jobMgr.put(SGE,  new String(sgeCmd));	    	  			
    }

    //-- ----------------------------------------------------------------
    // Check the environmental variables for the job manager location
    // If found, replace the environmental value in the command string.
    //------------------------------------------------------------------
    for (Iterator jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
        Map.Entry entry = (Map.Entry)jmi.next();
        
    	String jobMgrKey = (String)entry.getKey();
        String newloc = (String) entry.getValue();
    	location = (String)jobMgrLocations.get(jobMgrKey);
    	String location2 = null;
    	
    	String jobManager = jobMgrKey;
    	if (jobMgrKey.equals(CONDOR2))
    		jobManager = CONDOR;
    	
    	var  = jobManager+"_LOCATION";
    	
	//logger.log(Level.INFO, "### " + var + ": " + location);
    	if (location == null || location.equals("")) {
	    /* try to get the location from an environment variable */ 
    		try {    			
    			if (UseRemote) {
    				location2 = getRemoteVariable(remoteHostName, var);
    			} else {
    				location2 = AppConfig.getGlobalEnvProperty((String) var);
    			}
    		} catch (Exception e) {;}
		
    		if (location2 == null) {
    			logger.log(Level.INFO, "The value of the variable " +
    					var + " could not be obtained.");
    			if (location == null) {
    				jmi.remove();
    				continue;
    			} else {
    				/* use a default location */
    				location2 = (String)defaultLocations.get(jobManager);
    				if (location2 == null)
    					continue;
    			}
    		}
    		jobMgrLocations.put(jobMgrKey, location2);
    		location = location2;
    	}
    	
	logger.log(Level.INFO, "Determined " + jobMgrKey + 
    			" location: " + location);

    	String value = newloc.replaceAll(var,location);
    	logger.info("Using command for " + jobManager + ": " + value);
    	jobMgr.put(jobMgrKey, (String) value);
    	    
    	
    	/* find the location of the Condor history file */  	
    	if (jobManager.equals(CONDOR) && checkCondorHist) {
    		if (condorHistFile == null) {
    			String localDir;
    			if (UseRemote) {
    				localDir = getRemoteVariable(remoteHostName, "CONDOR_LOCAL_DIR");
    			} else {
    				localDir = AppConfig.getGlobalEnvProperty("CONDOR_LOCAL_DIR");
    			}
    			
    			if (localDir != null) {
    				condorHistFile = localDir + "/spool/history";
    			} else {
    				// the path should be ${CONDOR_LOCATION}/local.<hostname>/spool/history
    				File condorDir = new File (location);
    				String[] dirContent = condorDir.list();
    				if (dirContent == null)
    					condorHistFile = null;
    				else {
    					for (int ci = 0; ci < dirContent.length; ci++) {
    						if (dirContent[ci].startsWith("local.")) {
    							condorHistFile = location + "/" + dirContent[ci] + "/spool/history";
    							break;
    						}
    					}
    				}
    			}
    		}
    		logger.log(Level.INFO, "[monLcgVoJobs] Using Condor history file: " + condorHistFile);
    	}//if - condor
    	
	//TODO we should check for errors here...
    	if(jobManager.equals(SGE)) {
    		String SGE_ARCH = "UNKOWN";
    		try {
		    String archCmd = location + "/util/arch";
		    if (UseRemote)
			archCmd = "ssh " + remoteHostName + " '" + archCmd + "'";

		    BufferedReader br = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(location + "/util/arch").getInputStream()));
		    String line = br.readLine();
		    if ( line == null ) break;
		    SGE_ARCH = line.trim();
    		}catch(Throwable t) {
    			SGE_ARCH = "UNKOWN";
    		}
    		
    		addToMsg(methodName, "SGE_ARCH = " + SGE_ARCH);
    		if(SGE_ARCH.equals("UNKNOWN") || SGE_ARCH.length() == 0) {
    			addToMsg(methodName, " Ignoring SGE !");
    			jmi.remove();
    			continue;
    		}
    		
    		value = value.replaceFirst("SGE_ARCH", SGE_ARCH);
    	}//if SGE

    } // end of for (Iterator...

    //-----------------------------------------------------
    // Verify the remaining job managers' executable exists.
    // if not, remove it and throw an exception or not.
    //----------------------------------------------------- 
    if (!UseRemote) {
    	for (Iterator jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
    		Map.Entry entry = (Map.Entry)jmi.next();
    		
    		String jobManager = (String)entry.getKey();    	
    		if (jobManager.equals("CONDOR2"))
    			continue;
    		String cmd1 = (String) jobMgr.get(jobManager); 
    		StringTokenizer tz = new StringTokenizer ( cmd1 ) ;
    		int ni = tz.countTokens();
    		if ( ni > 0 )  {
    			location = tz.nextToken().trim();
    			File fd = new File(location);
    			if ( !fd.exists() ) { 
    				throw new Exception("Job Manager ("+jobManager+") Command ("+location+") does not exist.)");
    			} // end if/else         
    			
    		} // end if ni>0
    		logger.info("Checked " + jobManager + " command: " + cmd1);
    	} // end of for (Enum...
    }

  } // end method
  
  
  /**
   * Adds a Condor command to the buffers that contains the existing commands.
   * @param buff Buffer that contains the "condor_q -l" commands. 
   * @param buff2 Buffer that contains the plain "condor_q" commands.
   * @param cmd The "condor_q -l" command.
   * @param cmd2 The plain "condor_q" command.
   */
  void updateCondorCommands(StringBuffer buff, StringBuffer buff2, String cmd,
		  String cmd2) {
	  if (buff.length() > 0)
		  buff.append(" ; ");
	  if (buff2.length() > 0)
		  buff2.append(" ; ");
	  
	  buff.append(cmd);
	  buff2.append(cmd2);
	  
	  this.nCondorCommands++;
	  buff.append(" && echo ML_OSGVOJOBS_OK");
	  
	  if (!condorQuickMode) {
		  //checkFiles.add(new String("/tmp/osgVoJobs" + farmName + "_" 
			//	  + Node.getClusterName() + "_" + (checkFiles.size() - 1)));
		  buff2.append(" && echo ML_OSGVOJOBS_OK");
	  }
  }
  
  
  /**
   *  The "main" function of the module.  
   */
  public Object doProcess() throws Exception {
	  long t1 = System.currentTimeMillis();
	  String methodName = "doProcess";
	 
	  Vector v = null;
	  try { 		  
		  //-- set environment (only once we hope --
		  synchronized(jobMgr) {
			  if ( !environmentSet ) {
				  setEnvironment();
			  } // if environmentSet
		  }
		  currentJobsInfo      = new Hashtable();
		  
		  /* copy the old VOjobsInfo data into the current one, which 
		   * will further be updated; the fields that do not represent
		   * total values are initialised to 0
		   */
		  currentVOjobsInfo    = new Hashtable();
		  Enumeration voe = VOjobsInfo.keys();
		  while (voe.hasMoreElements()) {
			  String voName = (String) voe.nextElement();
			  VOSummaryExt vos = (VOSummaryExt)VOjobsInfo.get(voName);
			  VOSummaryExt vosc = (VOSummaryExt)vos.clone();
			  vosc.runningjobs = vosc.idlejobs = vosc.heldjobs = 0;
			  vosc.finishedjobs = vosc.unknownjobs = vosc.submittedJobs = 0;
			  vosc.size = 0.0; vosc.disk_usage = 0.0; 
			  vosc.paramsCondorH.put("CondorMemoryUsage", new Double(0));
			  vosc.paramsCondorH.put("CondorDiskUsage", new Double(0));
			  currentVOjobsInfo.put(voName, vosc);
		  }
		  
		  // -- Start the Job Queue Manager collectors ---
		  boolean bret = collectJobMgrData();
		  if (bret == false) {
			  logger.log(Level.INFO, "Failed getting jobs status, retrying after 20s...");
			  try {
				  Thread.sleep(20000);
			  } catch (Throwable t) {}
			  bret = collectJobMgrData();
		  }
		  
		  if (inconsistentJobInfo) {
			  logger.info("Inconsistent job information, not sending any result");
			  return null;
		  }
		  		  
		  if (bret == true) {
			  v = getResults();			 
		  } else {			  
			  throw new Exception("Second attempt to get jobs status failed, no results were sent");			  
		  }
		  
	  } catch (Throwable e) {
		  //e.printStackTrace();
		  logger.log(Level.WARNING, "monLcgVoJobs.doProcess() got exception:", e);
		  if (isInternalModule)
			  sendExceptionEmail(methodName + " FATAL ERROR - " + e.getMessage());
		  
		  //e.printStackTrace();
		  throw new Exception(e) ;
	  } // end try/catch
	  
	  long t2 = System.currentTimeMillis();
	  String infomsg =  "[monLcgVoJobs] execution time for doProcess(): "
			  + (t2 - t1) + " ms";
	  
	  // -- save the latest results (to be included in the status email) ----
	  if (v != null) {
		  //logger.finest("results not null!!!");
		  lastResults = new StringBuffer();
		  for ( int i = 0; i < v.size(); i++ ) {
			  lastResults.append("\n["+i+"]"+ v.elementAt(i));
		  }
	  }
	  
	  if(getShouldNotifyConfig()) {
          for(int i=0; i<removedVOseResults.size(); i++) {
              eResult er = (eResult) removedVOseResults.get(i);
                           
              if(er != null) {
            	  if (!mixedCaseVOs)
                	  er.NodeName = er.NodeName.toUpperCase();
                  v.add(er);
                  v.add(new eResult(er.FarmName, er.ClusterName+"_Rates", er.NodeName, ModuleName, er.param_name));
              }
          }
      }

      if (v == null) {
          infomsg = infomsg + "; Returning null result!";
      } else {
          infomsg = infomsg + "; Number of results returned: " + v.size();
      }
      logger.info(infomsg);

      if (isInternalModule)
          sendStatusEmail();

      if(getShouldNotifyConfig()) {
          logit(" [ monLcgVoJobs ] - Notified Config changed");
          setShouldNotifyConfig(false);
      }
      
	  return v;
  } //end method

  /**
   * Constructs a Result with the information regarding a singular job, held
   * in a JobInfoExt object.
   */
  private Object jobInfoToResult(JobInfoExt jobInfo) {
	  Result r = new Result();
	  
	  if (mixedCaseVOs) {
		  //r.ClusterName = jobInfo.VO + "_JOBS_LCG";
		  r.ClusterName = jobInfo.VO + "_" + Node.getClusterName();
	  } else {
		  //r.ClusterName = jobInfo.VO.toUpperCase() + "_JOBS_LCG";
		  r.ClusterName = jobInfo.VO.toUpperCase() + "_"  + 
		      Node.getClusterName();
	  }
	  
	  r.NodeName        = jobInfo.id;
	  r.time            = NTPDate.currentTimeMillis();
	  r.addSet("CPUTime",   jobInfo.cpu_time);
	  //r.addSet("RunTime", jobInfo.run_time/(double)MIN_SEC);
	  if (jobInfo.jobManager.equals(CONDOR) || 
	      jobInfo.jobManager.equals(LSF))
	      r.addSet("RunTime", (double)jobInfo.run_time);
	   if (jobInfo.jobManager.equals(CONDOR) || 
	       jobInfo.jobManager.equals(LSF) || jobInfo.jobManager.equals(SGE))
	       r.addSet("Size", jobInfo.size);
	   if (jobInfo.jobManager.equals(CONDOR))
	       r.addSet("DiskUsage", jobInfo.disk_usage);

	   r.Module = ModuleName;
	  
	  return r;
  }
  
  /**
   * Gathers the current Results both for individual jobs and for VOs. 
   */
  private Vector getResults() {
      Vector v = new Vector();
     
      /* get the rate statistics for jobs */
      Vector jd = getJobDiff();
      if(jd != null && jd.size() > 0) {
          v.addAll(jd);
      }
      
      //publish all the current jobs
      if(shouldPublishJobInfo) {
      	logger.log(Level.FINEST, "[monLcgVoJobs] found " + currentJobsInfo.size() + " current jobs");
          for(Enumeration it = currentJobsInfo.keys();it.hasMoreElements();) {
              String jobId = (String)it.nextElement();
              try {
                  Object o = jobInfoToResult((JobInfoExt)currentJobsInfo.get(jobId));
                  if(o != null) {
                      v.add(o);
                  }
              }catch(Throwable t){
                  logger.log(Level.WARNING, "Got exception adding JobID :- " + jobId, t);
              }
          }
      }
      
      
      
      /* get the rate statistics for VOs */
      Vector vd = null;
      if(lastRun != 0) {
          vd = getVODiff();
      }
      
      if(vd != null && vd.size() > 0) {
          v.addAll(vd);
      }
      
      jobsInfo = currentJobsInfo;
      VOjobsInfo = currentVOjobsInfo;
      oTotalVOS = cTotalVOS;
      
      lastRun = NTPDate.currentTimeMillis();
      
      return v;
  }
  
  private double getBin(double newValue, double oldValue) {
      return newValue - oldValue;
  }
  
  private double getRate(double newValue, double oldValue, double dt) {
      return getBin(newValue, oldValue)/dt;
  }
  
  /**
   * Calculates rate statistics for VOs.
   * @return Vector with Results containing the rates.
   */
  private Vector getVODiff() {
      
      Vector retV = new Vector();
      long   cTime = NTPDate.currentTimeMillis();
      double dTime = (cTime  - lastRun)/SEC_MILLIS; //in seconds
      
      // in this result we put the total values for all the VOs, for the 
      // values of the parameters in the last time interval
      Result rt = new Result();
      
      if (singleTotals) {
    	  rt.ClusterName = Node.getClusterName()+"_Totals";
    	  rt.NodeName = "Totals";
      } else {
    	  rt.ClusterName = "LcgVO_JOBS_Totals";
    	  rt.NodeName = Node.getClusterName() + "_Totals"; 
      }
      
      rt.NodeName = "Totals";
      rt.time = cTime;
      rt.Module = ModuleName;
      
      // in this result we put the total values for all the VOs, for the rates
      Result rt1 = new Result();
      if (singleTotals) {
    	  rt1.ClusterName = Node.getClusterName()+"_Totals";
    	  rt1.NodeName = "Total_Rates";
      } else {
    	  rt1.ClusterName = "LcgVO_JOBS_Totals";
    	  rt1.NodeName = Node.getClusterName() + "_Total_Rates";
      }
      rt1.NodeName = "Total_Rates";
      rt1.time = cTime;
      rt1.Module = ModuleName;
      
      // used to compute the totals for all the VOs
      cTotalVOS = new VOSummaryExt();
      
      for(Enumeration it = VOjobsInfo.keys(); it.hasMoreElements();) {
      	String VO = (String)it.nextElement();
      	
      	VOSummaryExt cVOSummary = (VOSummaryExt)currentVOjobsInfo.get(VO); 
      	if(cVOSummary != null) {
      		VOSummaryExt oVOSummary = (VOSummaryExt)VOjobsInfo.get(VO);
      		
      		// in this result we put the cumulated values of the parameters
      		Result r = new Result();
      		r.ClusterName = Node.getClusterName();
      		r.NodeName = VO;
      		if (!mixedCaseVOs)
      			r.NodeName = r.NodeName.toUpperCase();
      		r.time = cTime;
      		r.Module = ModuleName;
      		
      		// in this result we put the rates
      		Result r1 = new Result();
      		r1.ClusterName = Node.getClusterName() + "_Rates";
      		r1.NodeName = VO;
      		if (!mixedCaseVOs)
      			r1.NodeName = r1.NodeName.toUpperCase();
      		r1.time = cTime;
      		r1.Module = ModuleName;
      		
      		/* add the values of this VO to the total values */
      		cTotalVOS.runningjobs += cVOSummary.runningjobs; 
      		cTotalVOS.idlejobs += cVOSummary.idlejobs;
      		cTotalVOS.heldjobs += cVOSummary.heldjobs;
      		
      		cTotalVOS.submittedJobs += cVOSummary.submittedJobs;
      		cTotalVOS.submittedJobs_t += cVOSummary.submittedJobs_t;           
      		
      		cTotalVOS.finishedjobs += cVOSummary.finishedjobs;              
      		cTotalVOS.finishedJobs_t += cVOSummary.finishedJobs_t;
      		
      		cTotalVOS.size += cVOSummary.size;
      		cTotalVOS.run_time_t += cVOSummary.run_time_t;
      		cTotalVOS.cpu_time_t += cVOSummary.cpu_time_t;
      		cTotalVOS.disk_usage += cVOSummary.disk_usage;
      		
      		if(cVOSummary.unknownjobs != 0) {
      			cTotalVOS.unknownjobs +=  cVOSummary.unknownjobs;
      		}	
      		
      		Enumeration condorE = cVOSummary.paramsCondorH.keys();
      		while (condorE.hasMoreElements()) {
      			String key = (String)condorE.nextElement();
      			Double cval = (Double)cVOSummary.paramsCondorH.get(key);
      			
      			double total_val = ((Double)cTotalVOS.paramsCondorH.get(key)).doubleValue();
      			total_val += cval.doubleValue();
      			cTotalVOS.paramsCondorH.put(key, new Double(total_val));
      		}
      		
      		Enumeration lsfe = cVOSummary.paramsLSF.keys();
      		while (lsfe.hasMoreElements()) {
      			String key = (String)lsfe.nextElement();
      			Double cval = (Double)cVOSummary.paramsLSF.get(key);
      			
      			double total_val = ((Double)cTotalVOS.paramsLSF.get(key)).doubleValue();
      			total_val += cval.doubleValue();
      			cTotalVOS.paramsLSF.put(key, new Double(total_val));
      		}
      		
      		/* fill the results with values for this VO */
      		fillVOSResults(cVOSummary, oVOSummary, r, r1, dTime);
      		retV.add(r); 
      		retV.add(r1);
      	} else {  
      	}
      } 
      
      /* fill the results with the total values */
      fillVOSResults(cTotalVOS, oTotalVOS, rt, rt1, dTime);
      retV.add(rt);
      retV.add(rt1);
      return retV;
  }
  
  
  /**
   * Fills the fields of two Results with VO summary information.
   * @param cVOSummary Information from the current doProcesss().
   * @param oVOSummary Information from the previous doProcess().
   * @param r Is filled with current or cumulated values for the parameters.
   * @param r1 Is filled with the values of the rates.
   * @param dtime The time interval between the previous doProcess() and the
   * current one.
   */
  private void fillVOSResults(VOSummaryExt cVOSummary, VOSummaryExt oVOSummary,
  		Result r, Result r1, double dTime) {
  	boolean haveCondor = false, havePBS = false, haveSGE = false, haveLSF = false;
  	if (jobMgr.get("CONDOR") != null)
  		haveCondor = true;
	if (jobMgr.get("PBS") != null)
  		havePBS = true;
	if (jobMgr.get("SGE") != null)
  		haveSGE = true;
	if (jobMgr.get("LSF") != null)
  		haveSGE = true;
	
  	r.addSet("RunningJobs", cVOSummary.runningjobs);
  	r.addSet("IdleJobs", cVOSummary.idlejobs);
  	r.addSet("HeldJobs", cVOSummary.heldjobs);
  	r.addSet("SubmittedJobs", cVOSummary.submittedJobs);
  	//r.addSet("TotalSubmittedJobs", cVOSummary.totalSubmittedJobs);
  	r1.addSet("SubmittedJobs_R", getRate(cVOSummary.submittedJobs_t, oVOSummary.submittedJobs_t, dTime));
  	r.addSet("FinishedJobs", cVOSummary.finishedjobs);
  	//r.addSet("TotalFinishedJobs", cVOSummary.totalFinishedJobs);
  	r1.addSet("FinishedJobs_R", getRate(cVOSummary.finishedJobs_t, oVOSummary.finishedJobs_t, dTime));
  	
  	/* parameters reported by Condor */
  	if (haveCondor && !haveLSF && !havePBS && !haveSGE) {		
  		r.addSet("DiskUsage", cVOSummary.disk_usage);
  		
  		/* parameters from the history file... most of them are disabled 
  		   for the moment */
  		try {
  			Double cpu_usr = (Double)cVOSummary.paramsCondorH.get("CondorCpuUsr_t");
  			Double cpu_sys = (Double)cVOSummary.paramsCondorH.get("CondorCpuSys_t");
  			Double ocpu_usr = (Double)oVOSummary.paramsCondorH.get("CondorCpuUsr_t");
  			Double ocpu_sys = (Double)oVOSummary.paramsCondorH.get("CondorCpuSys_t");
  			double CondorCPU = cpu_usr.doubleValue() + cpu_sys.doubleValue();
  			double oCondorCPU = ocpu_usr.doubleValue() + ocpu_sys.doubleValue();
  			double cpuDiff = CondorCPU - oCondorCPU;
  			if (cpuDiff < 0) cpuDiff = 0;
			if (checkCondorHist)
			    r.addSet("CPUTimeCondorHist", cpuDiff);
  		} catch (Exception e) {
  			logger.log(Level.FINEST, "Got exception when getting Condor parameters", e);
  		}
  		
  		//r.addSet("FinishedJobsCondor", cVOSummary.CondorFinishedJobs);
  		//r1.addSet("FinishedJobsCondor_R", getRate(cVOSummary.CondorFinishedJobs, oVOSummary.CondorFinishedJobs, dTime));
  		//r.addSet("BytesSent", cVOSummary.CondorBytesSent);
  	  	//r1.addSet("BytesSent_R", getRate(cVOSummary.CondorBytesSent, oVOSummary.CondorBytesSent, dTime));
  	  	//r.addSet("BytesRecvd", cVOSummary.CondorBytesRecvd);
  	  	//r1.addSet("BytesRecvd_R", getRate(cVOSummary.CondorBytesRecvd, oVOSummary.CondorBytesRecvd, dTime));
  	  	//r.addSet("FileReadBytes", cVOSummary.CondorFileReadBytes);
  	  	//r1.addSet("FileReadBytes_R", getRate(cVOSummary.CondorFileReadBytes, oVOSummary.CondorFileReadBytes, dTime));
  	  	//r.addSet("FileWriteBytes", cVOSummary.CondorFileWriteBytes);
  	  	//r1.addSet("FileWriteBytes_R", getRate(cVOSummary.CondorFileWriteBytes, oVOSummary.CondorFileWriteBytes, dTime));	  	
  	  	//r1.addSet("DiskUsageCondor_R", getRate(cVOSummary.CondorDiskUsage, oVOSummary.CondorDiskUsage, dTime));
  	  	//r.addSet("MemoryUsage", cVOSummary.CondorMemoryUsage);
  	  	//r1.addSet("MemoryUsageCondor_R", getRate(cVOSummary.CondorMemoryUsage, oVOSummary.CondorMemoryUsage, dTime));
  	}
  	
  	/* parameters reported by Condor and LSF */
	if ((haveCondor || haveLSF) && !havePBS && !haveSGE) {
  		r.addSet("RunTime", (cVOSummary.run_time_t - oVOSummary.run_time_t)/(double)MIN_SEC);
  		r1.addSet("RunTime_R", getRate(cVOSummary.run_time_t, oVOSummary.run_time_t, dTime)/(double)MIN_SEC);
	}
	
  	/* parameters reported by Condor, LSF and SGE */
	if ((haveCondor || haveSGE || haveLSF) && !havePBS) {
  		r.addSet("JobsSize", cVOSummary.size);
	}
	
	/* parameters reported by Condor, PBS, LSF and SGE */
  	if (haveCondor || havePBS || haveSGE || haveLSF) {
  		r.addSet("CPUTime", cVOSummary.cpu_time_t - oVOSummary.cpu_time_t);
  		r1.addSet("CPUTime_R", getRate(cVOSummary.cpu_time_t, 
  				oVOSummary.cpu_time_t, dTime));
  	}
  	
  	if(cVOSummary.unknownjobs != 0) {
  		r.addSet("UnkownJobs", cVOSummary.unknownjobs);
  	}
  	
  	// in case we want to report the LSF parameters separately uncomment this
  	/*
	Enumeration lsfe = cVOSummary.paramsLSF.keys();
	while (lsfe.hasMoreElements()) {
		String key = (String)lsfe.nextElement();
		Double cval = (Double)cVOSummary.paramsLSF.get(key);
		Double oval = (Double)cVOSummary.paramsLSF.get(key);
		r.addSet(key, cval.doubleValue());
		r1.addSet(key, getRate(cval.doubleValue(), oval.doubleValue(), dTime));
	} 
	*/
		
  	double cTotalJobs = cVOSummary.heldjobs + cVOSummary.idlejobs + cVOSummary.runningjobs + cVOSummary.unknownjobs;
  	double oTotalJobs = oVOSummary.heldjobs + oVOSummary.idlejobs + oVOSummary.runningjobs + oVOSummary.unknownjobs;
  	r.addSet("TotalJobs", cTotalJobs);
  }
  
  /**
   * Calculates rate statistics for jobs.
   * @return Vector with Results containing the rates.
   */
  private Vector getJobDiff() {
      Vector retV = new Vector();
      for(Enumeration it=jobsInfo.keys(); it.hasMoreElements();) {
          String oldJobID = (String)it.nextElement();
          JobInfoExt oldJobInfo = (JobInfoExt)jobsInfo.get(oldJobID);
          if(!currentJobsInfo.containsKey(oldJobID)) {//a finished job
              eResult r = new eResult();
              if (mixedCaseVOs)
            	  r.ClusterName = oldJobInfo.VO + "_" + Node.getClusterName();
              else
            	  r.ClusterName = oldJobInfo.VO.toUpperCase() + "_" + Node.getClusterName();
              
              /*
              StringTokenizer st = new StringTokenizer(oldJobInfo.id, "_");
        	  r.NodeName = st.nextToken();
        	  if (st.hasMoreTokens())
        		  r.NodeName = st.nextToken();
        	  else
        		  logger.finest("[monLcgVoJobs] Incorrect job ID: " + oldJobInfo.id);
        		  */
              r.NodeName        = oldJobInfo.id;
              r.time            = NTPDate.currentTimeMillis();
              r.param           = null;
              r.param_name      = null;
              r.Module = ModuleName;
          
              VOSummaryExt vos = (VOSummaryExt)currentVOjobsInfo.get(oldJobInfo.VO);
              if(vos == null) {
                  vos = new VOSummaryExt();
              }
              vos.finishedjobs += 1;
              vos.finishedJobs_t += 1;
              currentVOjobsInfo.put(oldJobInfo.VO, vos);

              // TODO: see if this is really needed
              
              VOSummaryExt oldvos = (VOSummaryExt)VOjobsInfo.get(oldJobInfo.VO);
              if(oldvos == null) {
                  logger.log(Level.WARNING, " JOB FINISHED !!! NO VO ?!?!??!");
              } else {
                  oldvos.run_time_t -= oldJobInfo.run_time;
                  oldvos.size -= oldJobInfo.size; 
              } 
              
              retV.add(r);
              
              /* see if this VO has other jobs running */
              boolean jobFound = false;
              for (Enumeration jenum = currentJobsInfo.elements(); 
              	jenum.hasMoreElements();) {
            	  JobInfoExt jinfo = (JobInfoExt)jenum.nextElement();
            	  if (jinfo.VO.equals(oldJobInfo.VO)) {
            		  jobFound = true;
            		  break;
            	  }
              }
              if (!jobFound) {
            	  logger.log(Level.FINEST, "[monLcgVoJobs] VO " + oldJobInfo.VO +
            	  " has no more jobs");
            	  // this VO has no more jobs, delete the jobs cluster 
            	  eResult er1 = new eResult();
            	  if (mixedCaseVOs)
            		  er1.ClusterName = oldJobInfo.VO + "_" + Node.getClusterName();
            	  else
            		  er1.ClusterName = oldJobInfo.VO.toUpperCase() + "_" + Node.getClusterName();
            	  er1.NodeName = null;
            	  er1.param = null;
            	  er1.param_name = null;
            	  r.Module = ModuleName;
            	  retV.add(er1);
              } 
          } else {
        	  /* the job is not finished; make sure we don't report a 
        	   * decreasing run_time / cpu_time (Condor may do such a
        	   * thing ?) */
        	  /*
        	  JobInfoExt crtJobInfo = (JobInfoExt)currentJobsInfo.get(oldJobID);
        	  if (crtJobInfo.run_time < oldJobInfo.run_time)
        		  crtJobInfo.run_time = oldJobInfo.run_time;
        	  if (crtJobInfo.cpu_time < oldJobInfo.cpu_time)
        		  crtJobInfo.cpu_time = oldJobInfo.cpu_time;
        	  currentJobsInfo.put(oldJobID, crtJobInfo);
        	  */
          }
      }
      
      return retV;
  }
  
private boolean collectJobMgrData() throws Exception {
    //Vector results = new Vector();
    Hashtable outputBuffers = new Hashtable();
 
    inconsistentJobInfo = false;
    
    try{ 
        // ---- check the sanity of the environment ----
        if ( jobMgr.isEmpty() ) {
            throw new Exception ("There are no valid job queue managers to use.");
        }
        
        // --- query the queue managers and get the output of the commands ------
        this.haveError = false;        
        for (Iterator jmi = jobMgr.entrySet().iterator(); jmi.hasNext();) {
            
            Map.Entry entry = (Map.Entry)jmi.next();
            String jobManager = (String)entry.getKey();
            
            if (jobManager.equals("CONDOR2") && condorQuickMode)
            	continue;
            
            int nCommandsToCheck;
            if (jobManager.equals(CONDOR) || jobManager.equals(CONDOR2))
            	nCommandsToCheck = nCondorCommands;
            else
            	nCommandsToCheck = 1;
            
            String cmd1 = (String) entry.getValue();            
            StringBuffer sb1 = sbProcOutput (cmd1, -1, nCommandsToCheck);
                                        
            if (this.haveError) {
            	if (sb1 != null) {
            		String content = null;
            		if (sb1.length() > 1000)
            			content = sb1.substring(0, 1000);
            		else
            			content = sb1.toString();
            		logger.log(Level.WARNING, "Error getting job information from " +
            			jobManager + ": \n" + content);
            	}
            	
            	break;
            }
                        
            outputBuffers.put(jobManager, sb1);
        }
        
        // don't return any result if there was an error executing one of the
        // commands
        if (this.haveError)
        	return false;
        
        // only if all the commands executed successfully construct the results
        for (Iterator jmi = jobMgr.keySet().iterator(); jmi.hasNext();) {
        	String jobManager = (String)jmi.next();
        	
        	if (jobManager.equals(CONDOR2))
        		continue; //CONDOR2 will be processed simultaneously with CONDOR
        	
        	StringBuffer sb = (StringBuffer)outputBuffers.get(jobManager);
        	BufferedReader buff1 = new BufferedReader(new StringReader(sb.toString()));
        	
        	if (buff1 == null) {
        		logger.log(Level.WARNING, "Unexpected null output from " + jobManager);
        		continue;
        	}
        	
            if (jobManager.equals(CONDOR)) {
            	logger.log(Level.FINEST, "Collecting Condor data...");
            	// get information from the condor_q command
                Vector qInfo = condorAcc.parseCondorQLongOutput(buff1);
                
                if (!condorQuickMode) {
                	/* get the additional information from plain condor_q */
                	StringBuffer sb2 = (StringBuffer)outputBuffers.get(CONDOR2);          
                	BufferedReader buff2 = new BufferedReader(new StringReader(sb2.toString()));
                	Vector q2Info = condorAcc.parseCondorQOutput(buff2);
                	
                	/* replace the run time obtained from condor_q -l with the run time 
                	 * obtained from plain condor_q
                	 */
                	logger.log(Level.FINEST, "Getting additional info from condor_q...");
                	if (q2Info != null) {
                		for (int qi = 0; qi < qInfo.size(); qi++) {
                			JobInfoExt jInfo = (JobInfoExt)(qInfo.get(qi));                		
                			for (int q2i = 0; q2i < q2Info.size(); q2i++) {
                				JobInfoExt j2Info = (JobInfoExt)(q2Info.get(q2i));                			
                				if (jInfo.id.equals(j2Info.id)) {                				
                					jInfo.run_time = j2Info.run_time;
                				}
                			}
                		}
                	}
                }
                processJobsInfo(qInfo, jobManager);
                              
                // get information from the Condor history file
		if (checkCondorHist) {
		    Vector histInfo = condorAcc.getHistoryInfo();
		    for (int hi = 0; hi < histInfo.size(); hi++) {
                	Hashtable hJobInfo = (Hashtable)histInfo.get(hi);
                	updateVoSummaryCondorHist(hJobInfo);                	
		    }
		}
                             
            } else if ( jobManager.equals(PBS))  {
            	logger.log(Level.FINEST, "[monLcgVoJobs] Collecting PBS data...");
            	// get information from the qstat command
                Vector pbsInfo = VO_PBSAccounting.parsePBSOutput(buff1);
                processJobsInfo(pbsInfo, jobManager);
            } else if (jobManager.equals(LSF)) {            	
            	logger.log(Level.FINEST, "[monLcgVoJobs] Collecting LSF data...");
            	// get information from the condor_q command
                Vector lsfInfo = VO_LSFAccounting.parseLSFOutput(buff1);
                processJobsInfo(lsfInfo, jobManager);
            } else if (jobManager.equals(FBS)) {
                ParseFBSOutput( buff1 );
            } else if (jobManager.equals(SGE)) {
            	logger.log(Level.FINEST, "[monLcgVoJobs] Collecting SGE data...");
            	// get information from the qstat command
                Vector sgeInfo = VO_SGEAccounting.parseSGEOutput(buff1);
                processJobsInfo(sgeInfo, jobManager);
            }
        } // end of for
        
    } catch (Throwable t) {
    	//t.printStackTrace();
    	logger.log(Level.WARNING, "[monLcgVoJobs] collectJobMgrData got exception", t);
        throw new Exception("collectJobMgrData - " + t.getMessage()) ;
    } // end try/catch
    
    return true;
} // end method

/**
 * Wrapper that takes the job information obtained from a queue manager and updates the
 * VO statistics. 
 * @param qInfo Holds information for the jobs that are currently in the queue. 
 * @param jobManager The name of the job manager.
 */
void processJobsInfo(Vector qInfo, String jobManager) {
	for (int qi = 0; qi < qInfo.size(); qi++) {
		JobInfoExt jobInfo = (JobInfoExt)qInfo.get(qi);
		jobInfo.VO = getVo(jobInfo.user);
		if (jobInfo.VO == null) // this is not a user we are interested in
			continue;
		
		/* the queue manager may report a non-zero job size even if the
		 * job has not started yet
		 */
		if (jobInfo.cpu_time == 0 && jobInfo.run_time == 0)
			jobInfo.size = 0;
		
		/* if we have a new job we must update the number of submitted
		 * jobs
		 */
		boolean haveNewJob = false;
		if (jobsInfo.get(jobInfo.id) == null)
			haveNewJob = true;
		currentJobsInfo.put(jobInfo.id, jobInfo);
		try {
			updateVoSummary(jobInfo, haveNewJob);
		} catch (Exception ex) {
			logger.log(Level.WARNING, "updateVoSummary() got exception: ", ex);
			inconsistentJobInfo = true;
		}
		logger.finest("[monLcgVoJobs] Got " + jobManager + " job: " + jobInfo);
	}
}

/**
 * Updates the current VO statistics with information about a job.
 * @param jobInfo Job information that will be added to the VO statistics.
 */
private void updateVoSummary(JobInfoExt jobInfo, boolean haveNewJob) 
	throws Exception {
	VOSummaryExt vos = (VOSummaryExt)currentVOjobsInfo.get(jobInfo.VO);
	if(vos == null) {        	
		vos = new VOSummaryExt();
	}
	
	// F - finished
	if(jobInfo.status.equals("H")) {
		vos.heldjobs += 1;
	} else if (jobInfo.status.equals("R")) {
		vos.runningjobs += 1;
	} else if (jobInfo.status.equals("I")) {
		vos.idlejobs += 1;
	} else {
		vos.unknownjobs += 1;
	}
	
	if (haveNewJob) {
		vos.submittedJobs_t++;
		vos.submittedJobs++;
	}
	
	JobInfoExt oldInfo = (JobInfoExt)jobsInfo.get(jobInfo.id);
	if (oldInfo == null) { // new job, we add all the run/cpu time
		vos.run_time_t += jobInfo.run_time;
		vos.cpu_time_t += jobInfo.cpu_time;
	} else {
		vos.run_time_t += (jobInfo.run_time - oldInfo.run_time);
		vos.cpu_time_t += (jobInfo.cpu_time - oldInfo.cpu_time);
		if (jobInfo.run_time < oldInfo.run_time || jobInfo.cpu_time <
				oldInfo.cpu_time) {
			logger.log(Level.WARNING, "Decreasing run time / CPU time for job " + 
					jobInfo.id + "( cpu time: "+ jobInfo.cpu_time + ", old cpu time: "
					+ oldInfo.cpu_time + " ; run time:  " + jobInfo.run_time + 
					", old run time: " + oldInfo.run_time + ". The counter will be reset!");
			
			/* this time we won't send any result, but we replace the old record
			   we had about the job with a new one */
			jobsInfo.put(jobInfo.id, jobInfo);
		
			throw new Exception ("Decreasing run time / CPU time");
		}
	}
	
	vos.size += jobInfo.size;
	vos.disk_usage += jobInfo.disk_usage;
	
	// in case we want separate statistics for LSF, uncomment this
	/*	
	 if (jobInfo.jobManager.equals("LSF")) {
	 Double mem = (Double)vos.paramsLSF.get("MemoryUsageLSF");
	 vos.paramsLSF.put("MemoryUsageLSF", new Double(mem.doubleValue() +
	 jobInfo.size));
	 
	 Double swap = (Double)vos.paramsLSF.get("SwapUsageLSF");
	 vos.paramsLSF.put("SwapUsageLSF", new Double(swap.doubleValue() +
	 jobInfo.swap));
	 
	 Double cpu = (Double)vos.paramsLSF.get("CPUUsageLSF");
	 vos.paramsLSF.put("CPUUsageLSF", new Double(mem.doubleValue() +
	 jobInfo.cpu_time));
	 
	 Double runtime = (Double)vos.paramsLSF.get("CPUTimeLSF");
	 vos.paramsLSF.put("CPUTimeLSF", new Double(mem.doubleValue() +
	 jobInfo.size));
	 }
	 */
	currentVOjobsInfo.put(jobInfo.VO, vos);
}

/**
 * Updates the current VO statistics with information about a finished job
 * obtained from the Condor history file.
 * @param hJobInfo Job information that will be added to the VO statistics.
 */
private void updateVoSummaryCondorHist(Hashtable hJobInfo) {
	String user = (String)hJobInfo.get("sOwner");
	String jobVo = getVo(user);

	if (jobVo == null) {
		logger.finest("[monLcgVoJobs] no VO for user " + user);
        currentVOjobsInfo.remove(jobVo);
		return;
	}
	
	try {
		VOSummaryExt vos = (VOSummaryExt)currentVOjobsInfo.get(jobVo);
		if(vos == null) {
			vos = new VOSummaryExt();
		}
			
		Double cpuUsr = (Double)hJobInfo.get("dRemoteUserCpu");
		Double oCpuUsr = (Double)vos.paramsCondorH.get("CondorCpuUsr_t");
		if (cpuUsr != null) {
			vos.paramsCondorH.put("CondorCpuUsr_t", new Double(cpuUsr.doubleValue() +
					oCpuUsr.doubleValue()));
		}
		
		Double cpuSys = (Double)hJobInfo.get("dRemoteSysCpu");
		Double oCpuSys = (Double)vos.paramsCondorH.get("CondorCpuSys_t");
		if (cpuSys != null) {
			vos.paramsCondorH.put("CondorCpuSys_t", new Double(cpuSys.doubleValue() +
					oCpuSys.doubleValue()));
		}
		
		/*
		 vos.CondorFinishedJobs++;
		Double remoteWallTime = (Double)hJobInfo.get("dRemoteWallClockTime");
		if (remoteWallTime != null) {
			vos.CondorRunTime += remoteWallTime.doubleValue();
		}		
		
		Double bytesSent = (Double)hJobInfo.get("dBytesSent");
		if (bytesSent != null)
			vos.CondorBytesSent += bytesSent.doubleValue();
		
		Double bytesRecvd = (Double)hJobInfo.get("dBytesRecvd");
		if (bytesRecvd != null)
			vos.CondorBytesRecvd += bytesRecvd.doubleValue();
		
		Double fileReadBytes = (Double)hJobInfo.get("dFileReadBytes");
		if (fileReadBytes != null)
			vos.CondorFileReadBytes += fileReadBytes.doubleValue();
		
		Double fileWriteBytes = (Double)hJobInfo.get("dFileWriteBytes");
		if (fileWriteBytes != null)
			vos.CondorFileWriteBytes += fileWriteBytes.doubleValue();
		
		Double diskUsage = (Double)hJobInfo.get("dDiskUsage");
		if (diskUsage != null) {
			vos.CondorDiskUsage += diskUsage.doubleValue();
			//logger.log(Level.INFO, "[monLcgVoJobs] got disk usage " + vos.CondorDiskUsage);
		}
		
		Double memUsage = (Double)hJobInfo.get("dImageSize");
		if (memUsage != null)
			vos.CondorMemoryUsage += memUsage.doubleValue();
		*/
	} catch(Throwable t) {
		logger.log(Level.WARNING, "updateVoSummaryCondor got exc", t);
	}
}


//====== Parse LSF Ouput ==========================
void  ParseLSFOutput (  BufferedReader buff ) throws Exception {
    //TO BE DONE
} // end method

//====== Parse FBS Ouput ==========================
void  ParseFBSOutput (  BufferedReader buff ) throws Exception {
    //TO BE DONE
} // end method



//===============================================================
// The Vo map file has changed
/*
public void update(Observable o, Object arg) {
    if(o != null && mapFileWatchdog != null && o.equals(mapFileWatchdog)) {//just extra check
        environmentSet = false;
    }
}
*/

public StringBuffer sbProcOutput (String cmd, long delay, int nCommandsToCheck) {
	StringBuffer ret = new StringBuffer();
	BufferedReader br1 = procOutput(cmd, delay);
	if (br1 == null)
		return null;
	
	String line;
	int nSuccessfulCommands = 0;
	try {
		while ((line = br1.readLine()) != null) {
			if (line.indexOf("ML_OSGVOJOBS_OK") >= 0)
				nSuccessfulCommands++;
			else
				ret.append(line + "\n");
			
		}
	} catch (Exception e) {
		return null;
	}
	    	
	if (nSuccessfulCommands < nCommandsToCheck || ret == null)
		this.haveError = true;
	else
		this.haveError = false;
	
	return ret;        
}

public String getRemoteVariable(String remoteHost, String var) {
	String cmd = "ssh " + remoteHost + " 'echo $" + var + 
		" && echo ML_OSGVOJOBS_OK'";
	StringBuffer sb1 = sbProcOutput (cmd, -1, 1);
	if (sb1 == null || this.haveError) {
		this.haveError = false;
		return null;
	} else {
		String ret = (new String(sb1)).trim();
		if (ret.length() == 0)
		    return null;
		return ret;
	}
}

//--------------------------------------------------
static public void main ( String [] args ) {
  System.out.println ( "args[0]: " + args[0] );
  String host = args[0] ;
  monLcgVoJobs aa = null;
  String ad = null ;

  try {
    System.out.println ( "...instantiating LcgVoJobs");
    aa = new monLcgVoJobs();
  } catch ( Exception e ) {
    System.out.println ( " Cannot instantiate LcgVoJobs:" + e );
    System.exit(-1);
  } // end try/catch

  try {
    ad = InetAddress.getByName( host ).getHostAddress();
  } catch ( Exception e ) {
    System.out.println ( " Cannot get ip for node " + e );
    System.exit(-1);
  } // end try/catch

  System.out.println ( "...running init method ");
  String mapFile = "grid3-user-vo-map.txt";
  String arg = "mapfile="+mapFile;
  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), arg);
  //MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), "");

  int sec = 20; // number of seconds to sleep before processing again
  for ( int i=0; i<(int) 500; i++) {
    try {
      System.out.println ( "...sleeping "+sec+" seconds");
      Thread.sleep(sec*1000);
      System.out.println ( "...running doProcess");
      Object bb = aa.doProcess();
      if ( bb != null && bb instanceof Vector ) {
          Vector v = (Vector) bb;
          System.out.println ( " Received a Vector having " + v.size() + " results" );
          for(int vi = 0; vi < v.size(); vi++) {
              System.out.println(" [ " + vi + " ] = " + v.elementAt(vi));
          }
        }

      // -- after the 5th time, touch the map file and sleep --
      // -- to test the re-reading of the map file     --
      /*
      if ( i == (int) 4 ) {
        System.out.println ( "...touching map file: "+mapFile);
        Runtime.getRuntime().exec("touch "+mapFile);
        System.out.println ( "...sleeping "+sec+" seconds");
        Thread.sleep((int) 25000); // 25 secs
      }
      */
    } catch (Exception e) {
      logger.log(Level.WARNING, "ERROR: ", e );
    } // end try/catch
  } // end for

  System.out.println ( "LcgVoJobs Testing Complete" );
  System.exit(0);
} // end main
} // end class


