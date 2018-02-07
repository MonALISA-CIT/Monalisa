//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Date;
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
import lia.util.LogWatcher;

/**
 * This module compute the value for this tree structure from globus log file
 * Tree result:
 *     \_user*
 *          |__ftpInput
 *          |__ftpOutput
 *          |__ftpRateIn
 *          |__ftpRateOut
 *          |__(src,dest)*
 *                |__ftpInput
 *                |__ftpOutput
 *                |__ftpRateIn
 *                |__ftpRateOut
 * The data structure is voTotals from the base class. and voAccts for VO users
 */

public class monLcgVO_IO extends monExtVoModules implements MonitoringModule {
    
    /** serial version number */
    static final long serialVersionUID = 1706200525091981L;
    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monOsgVO_IO");
    /** information for email notifyer */
    protected static String emailNotifyProperty = "lia.Monitor.notifyVOFTP";
    /** Module name */
    protected static String MyModuleName = "monLcgVO_IO";
    
    /** strings that represent the MonALISA, vdt and globus location */
    protected String vdtLocation = null;
    
    protected String globusLocation = null;
    
    protected static final String GRIDFTP_LOG = "/var/gridftp.log";
    
    protected String ftplog = null; // GLOBUS_LOCATION + GRIDFTP_LOG;
    
    /** set's of constants */
    protected final int LINE_ELEMENTS_NO = 16;
    
    /**
     * Name of parameters calculated with this module. To add a parameter, just
     * add his name in the list below.
     */
    protected static String[] MyResTypes = {
        /* 0 */"ftpInput", /* 1 */"ftpOutput",
        /* 2 */"ftpRateIn", /* 3 */"ftpRateOut"
        /** other name for result parameter */
    };
    
    /** watcher to log file */
    protected LogWatcher lw = null;
    
    /** if it is the fisrt time when we run the doProcess */
	protected boolean firstTime = true;
	protected boolean singleTotals = false;
	protected long lastTime = 0;
	protected long doProcessInterval;
	protected static int nrCall = 0;
	protected boolean MixedCaseVOs = false;
	protected boolean haveError = false;
	
	protected String remoteHostName = null;
	protected String remoteFile = null;
	
	/** Total result for all IO */
	UserFTPData monTotals;
	boolean useRemote = false;
	//String gridDistribution = null ;
	
	/**
	 * {(user): (site1), (site2), ..., (siten)}
	 * ........................................
	 * {(user): (site1), (site2), ..., (siten)}
	 */
	Hashtable siteTable = new Hashtable();
    
    /** Module constructor */
    public monLcgVO_IO() {
        /** the base class constructor */
        super(MyModuleName, MyResTypes, emailNotifyProperty);
        
        String methodName = "constructor";
        addToMsg(methodName, "Constructor of " + ModuleName + " at " + currDate);
        addToMsg(methodName, "Info content: name " + info.name + " id " + info.id + " type " + info.type + " state " + info.state + " err " + info.error_count + ".");
        
        isRepetitive = true;
        info.ResTypes = ResTypes();
        /** write the last messges in the ML log file */
        logit(sb.toString());
        /** prepare the new string buffer for new log messages */
        sb = new StringBuffer();
    }
    
    /**
     * init module with node and arguments configuration file entry:
     * *Node{monLcgVO_IO, localhost, <arguments>}%30 <arguments> is a comme
     * separated list (no quoted) ftplog=/path-to-ftplog
     * mapfile=/globus-location
     */
    public MonModuleInfo init(MNode inNode, String args) {
        /** the method name */
        String methodName = "init";
        /** the arguments list from configuration file entry */
        String[] argList = null;
        
        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;
        
        addToMsg(methodName, "Instantiating instance for Cluster (node in cf) " + clusterModuleName + " at " + currDate);
        addToMsg(methodName, "arguments: " + ((args == null) ? "NO ARGUMENTS" : args));
        addToMsg(methodName, "Node Info: name " + (Node.name == null ? "null" : Node.name) + " short_name " + (Node.name_short == null ? "null" : Node.name_short) + " cl.name " + (Node.cluster == null ? "null" : Node.cluster.name) + " fa.name " + (Node.farm == null ? "null" : Node.farm.name) + ".");
        
        /** Check the argument list and process information */
        if (args != null) {
            /** check if file location or globus_location are passed */
            argList = args.split("(\\s)*,(\\s)*");
            
            for (int i = 0; i < argList.length; i++) {
                
                argList[i] = argList[i].trim();
                logit("Argument " + i + ":" + argList[i] + ".");
		if (argList[i].toLowerCase().startsWith("remotehost")) {
		    useRemote = true;
		    remoteHostName = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                    addToMsg(methodName, "overrridden RemoteHost(" + remoteHostName + ")");
		    continue;
                }
		if (argList[i].toLowerCase().startsWith("remotefile")) {
		    remoteFile = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                    addToMsg(methodName, "overrridden RemoteFile(" + remoteFile + ")");
		    continue;
                }
		if (argList[i].toLowerCase().startsWith("ftplog")) {
                    ftplog = argList[i].split("(\\s)*=(\\s)*")[1].trim();
                    addToMsg(methodName, "overrridden ftplog(" + ftplog + ")");
		    continue;
                }
		
		if (argList[i].toLowerCase().indexOf("mapfile") != -1) {
                    try {
		        mapfile = argList[i].split("(\\s)*=(\\s)*")[1].trim();
			addToMsg(methodName, "overrridden MapFile(" + mapfile + ")");
                    }catch(Throwable t){
                        logit("Got exception parsing server option: " + t);
                    }
                    continue;
		}

		if (argList[i].toLowerCase().indexOf("siteinfofile") != -1) {
                    try {
		        lcgInfoFile = argList[i].split("(\\s)*=(\\s)*")[1].trim();
			addToMsg(methodName, "overrridden SiteInfoFile(" + lcgInfoFile + ")");
                    }catch(Throwable t){
                        logit("Got exception parsing SiteInfoFile file option: " + t);
                    }
                    continue;
		}		

		
                if (argList[i].toLowerCase().startsWith("mixedcasevos")) {
		    MixedCaseVOs = true;
                    logit(ModuleName+": "+methodName+": overrridden MixedCaseVOs(" + MixedCaseVOs + ")");
                    continue;
                }
                if (argList[i].toLowerCase().startsWith("debug")) {
                    debugmode = true;
                    addToMsg(methodName, "overrridden Debug(" + debugmode + ")");
		    continue;
                }
		if (argList[i].toLowerCase().startsWith("singletotals")) {
                    singleTotals = true;
                    addToMsg(methodName, "overrridden SingleTotals(" + singleTotals + ")");
		    continue;
                }
		if (argList[i].toLowerCase().indexOf("griddistribution") != -1) {
	            try {
			gridDistribution = argList[i].split("(\\s)*=(\\s)*")[1].trim();
		    }catch(Throwable t){                      
			addToMsg(methodName, " Got exception parsing GridDistribution option " + t);
			gridDistribution = "OSG";
		    }
		}
                if (argList[i].toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(argList[i].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    }catch(Throwable t){
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    continue;
                }
            }
        }
        info.ResTypes = ResTypes();
		
        return info;
    }
    
    /**
     * Set the MonALISA, VDT, Globus locations and other data structures
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {
        /** the method name */
        String methodName = "setEnvironment";

	tmpVoAccts.clear();
	tmpVoMixedCase.clear();
	tmpVoAccts.putAll(voAccts);
	tmpVoMixedCase.putAll(voMixedCase);
	  
	cleanupEnv();
	
	try {
		initializeEnv();
		environmentSet = true;
	}catch(Exception e1) {
		throw e1;
	} 
	
	computeVoAcctsDiff();

        try {
            /** Determine the MonaALisa_HOME */
            monalisaHome = getEnvValue("MonaLisa_HOME");
            if (monalisaHome == null) throw new Exception("MonaLisa_HOME environmental variable not set.");
            addToMsg(methodName, "MonaLisa_HOME=" + monalisaHome);

            /** Determine the VDT_LOCATION */
            if(useRemote){
		vdtLocation = getRemoteVariable(remoteHostName,"VDT_LOCATION");
            } else{
	    	vdtLocation = getEnvValue("VDT_LOCATION");
            }
            if (vdtLocation == null) vdtLocation = monalisaHome + "/..";
                addToMsg(methodName, "VDT_LOCATION=" + vdtLocation);

            /** Determine the GLOBUS_LOCATION */
            if (ftplog == null) {
		if(useRemote){
		    globusLocation = getRemoteVariable(remoteHostName,"GLOBUS_LOCATION");
            	} else{
		    globusLocation = getEnvValue("GLOBUS_LOCATION");
		}
                if (globusLocation == null) throw new Exception("GLOBUS_LOCATION environmental variable not set.");
                	addToMsg(methodName, "GLOBUS_LOCATION=" + globusLocation);
            }

	    if(useRemote){
		ftplog = "/tmp/gridftp_" + Node.getFarmName() + "_" + Node.getClusterName() + "_log";
		String cmd = "ssh " + remoteHostName + " cat " + remoteFile +  " > " + ftplog;
		BufferedReader b = procOutput (cmd);
			
	    } else {
		/** set the ftplog values */
	        if (ftplog == null) ftplog = globusLocation + GRIDFTP_LOG;
	            addToMsg(methodName, "ftplog...." + ftplog);

                /** check to see if GRIDFTP_LOG exists and readable */
                File probe = new File(ftplog);
	        if (!probe.isFile()) throw new Exception("Gridftp log (" + ftplog + ") not found.");
	        if (!probe.canRead()) throw new Exception("Gridftp log (" + ftplog + ") is not readable.");
	        addToMsg(methodName, "Gridftp log (" + ftplog + ")\n  last modified " + (new Date(probe.lastModified())).toString() + " - size(" + probe.length() + " Bytes)");
	    }

        } catch (Exception ex) {
			ex.printStackTrace();
            throw new Exception("setEnvironment() - " + ex.getMessage() + " " + ex);
        }
    }

    public Object doProcess() throws Exception {
        /** the method name */
        String methodName = "doProcess";
	/** result of the doProcess method */
        Vector result = new Vector();
		
        try {
	    /** start of doProcess */
	    long start = NTPDate.currentTimeMillis();
	    /** call rank */
	    nrCall++;
            /** set the environment (only once we hope) */
            if (!environmentSet)
		setEnvironment();
			
	    /** VO data */
	    voTotals = new Hashtable();

	    Enumeration VOUsers = VoList();
	    String user;
			
            /** add all users and set totals and sites */
            while (VOUsers.hasMoreElements()) {
                user = (String) VOUsers.nextElement();
                UserFTPData ufd = new UserFTPData(user, "totals");
                Vector v = new Vector();
                v.add(ufd);
		voTotals.put(user, v);
            }
			
	    if(singleTotals)
		monTotals = new UserFTPData(Node.getClusterName()+"_Totals", "Total_Trafic");
	    else
		monTotals = new UserFTPData("LcgVO_IO_Totals", Node.getClusterName()+"_Totals");

            /** set the status to good */
            statusGood = true;

            /** fist time when I run this method */
            if (firstTime == true) {
                /** record the start moment (time in miliseconds) */
                setStartTime();
                /** for second time :) */
                firstTime = false;
				
		if(useRemote){
			ftplog = "/tmp/gridftp_" + Node.getFarmName() + "_" + Node.getClusterName() + "_log";
			new File(ftplog);
		}

		/** but i put a watche to a log file */
                lw = new LogWatcher(ftplog);
				
		/** first time - the users for sites table */
		VOUsers = VoList();
		while(VOUsers.hasMoreElements()){
			user = (String) VOUsers.nextElement();
			Vector sites = new Vector();
			siteTable.put(user, sites);
		}
		/** i will return zero results */
                result = createResultsVector();
				
		/** confirm by Email */
                sendStatusEmail();
                /** record the finish moment (date and time in miliseconds) */
                setFinishTime();
                /** last time when doProcess was called */
                lastTime = NTPDate.currentTimeMillis();
                /** message to ML log file */
                addToMsg(methodName, " first time when i was called: " + (new Date(NTPDate.currentTimeMillis())));
            } else {
                /** interval between two call of this method (in seconds) */
                long currentTime = NTPDate.currentTimeMillis();
                doProcessInterval = (currentTime - lastTime) / 1000;
				
				/** record the start moment (time in miliseconds) */
                setStartTime();

		VOUsers = VoList();
		while(VOUsers.hasMoreElements()){
		    user = (String) VOUsers.nextElement();
		    if(!siteTable.containsKey(user)){
			Vector sites = new Vector();
			siteTable.put(user, sites);
		    }
		}

                /** take the par of log file */
                BufferedReader logBuffer = lw.getNewChunk();
                if (logBuffer == null)
			throw new Exception("Buffered Reader is null for [ " + ftplog + " ]");

                /** parse and buid the result */
                try {
                    parseFtpLog(logBuffer);
                } catch (Throwable t) {
                   if (logBuffer != null) logBuffer.close();
                    throw t;
                }
                /** close the buffer */
                if (logBuffer != null)
			logBuffer.close();

                /** after the date was cumulated in the hashtabe, compute the rates */
                computeFTPRate(doProcessInterval);
                monTotals.computeRate(doProcessInterval);

                /** create the results for update to ML (results in KBytes) */
                result = createResultsVector();

                /** confirm by Email */
                sendStatusEmail();
		
		if(getShouldNotifyConfig())
                    setShouldNotifyConfig(false);

                /** record the finish moment (date and time in miliseconds) */
                setFinishTime();
		/** last time when doProcess was called */
                lastTime = currentTime;				
            }
            long stop = NTPDate.currentTimeMillis();
		addToMsg(methodName," "+nrCall+" : "+(stop-start)+" milli-seconds");
        } catch (Throwable ex) {
            statusGood = false;
	    ex.printStackTrace();
            sendExceptionEmail(methodName + " FATAL ERROR - " + ex.getMessage());
            throw new Exception(ex);
        }
        return result;
    }
    
    /**
     * Parse the all buffer from log file and add data
     * 
     * @param buffer
     * @throws Exception
     */
    protected void parseFtpLog(BufferedReader buffer) throws Exception {
        /** the method name */
        String methodName = "parseFtpLog";
        /** a line from buffer */
        String line;
        /** elements of a line */
        Hashtable lineResult = new Hashtable();
        /** value of lines number for printing process in ML logfile */
        int maxlinesNo = 7;
        /** numer of lines of buffer */
        int linesNo = 0;

        /** parse the buffer (part of a logfile) */
        try {
            while ((line = buffer.readLine()) != null) {
                linesNo++;
                /** special cases */
                if (line.length() == 0) continue; // empty line
                if (line.startsWith(COMMENT)) continue; // comment

                /** process the line from log file */
                lineResult = parseLine(line);
                /** some debug information for first 5 lines */
                if (linesNo < 5) {
                    debug(line);
                    if (lineResult != null)
						debug("Length: " + lineResult.size());
                }
                /** process the line result */
                if (lineResult != null)
					updateVOData(lineResult);
            }
            debug(linesNo + " lines read from gridftp.log file");
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(methodName + "() Exception: " + e);
        }
    }

    /**
     * Parse a line with some specific structure. If the line in the log file
     * changes the structure, you must modify just the constants from the begin
     * of this module.
     * 
     * Structure of a line in the log file (an example) is: 
     * [ 0] DATE=20050616052722.475032
     * [ 1] HOST=osg.rcac.purdue.edu
     * [ 2] PROG=wuftpd
     * [ 3] NL.EVNT=FTP_INFO
     * [ 4] START=20050616052722.410910
     * [ 5] USER=ivdgl
     * [ 6] FILE=/tmp/gridcat-_-test.gridcat.20252.remote
     * [ 7] BUFFER=87380
     * [ 8] BLOCK=65536
     * [ 9] NBYTES=28
     * [10] VOLUME=/tmp
     * [11] STREAMS=1
     * [12] STRIPES=1
     * [13] DEST=1[129.79.4.64]
     * [14] TYPE=STOR
     * [15] CODE=226
     */
    protected Hashtable parseLine(String line) {
        /** the method name */
        String methodName = "parseLine";
        
        if (line == null) return null;
        
        /** result of parse */
        Hashtable result = new Hashtable();
        /** elements of a line */
        String[] lineElements = new String[] {};
        
        lineElements = line.split("\\s+");
        
        /** test if the line is complete */
        if (lineElements.length == LINE_ELEMENTS_NO) {
            for (int i = 0; i < LINE_ELEMENTS_NO; i++) {
                String key = lineElements[i].split("=")[0].trim();
                String value = lineElements[i].split("=")[1].trim();
                result.put(key, value);
            }
            return result;
        }
        return null;
    }
    
    /**
     * Add data from info to a tree structure of information
     * @param info - information from line result
     */
    protected void updateVOData(Hashtable info) throws Exception {
        /** the method name */
        String methodName = "updateVOData";
		
        String user = getVo((String) info.get("USER"));
        if (user == null) return;
        String src, dest, sd;
        double newFtpIn, newFtpOut;

	try {
	    /** find a new user (i hope not) */
            if (!voTotals.containsKey(user)) {
                Vector userData = new Vector();
                UserFTPData ufd = new UserFTPData(user, "totals");
                userData.add(ufd);
                voTotals.put(user, userData);
		siteTable.put(user,userData);
            }

            /** get the result's vector for user */
            Vector userData = (Vector) voTotals.get(user);
            UserFTPData ufdTotals = (UserFTPData) userData.get(0);

            /** get the destination */
            dest = (String) info.get("DEST");
            int p1 = dest.indexOf('[');
            int p2 = dest.lastIndexOf(']');
            if (p1 + 1 <= p2 - 1) {
                dest = dest.substring(p1 + 1, p2);
                dest = getMachineName(dest);
                dest = getMachineDomain(dest);
            }

            /** get the source */
            src = (String) info.get("HOST");
            if(Character.isDigit(src.charAt(0)))
		src = getMachineName(src);
            src = getMachineDomain(src);
			
	    /** add the site in sites Table */
	    FTPSite ftpsrc = new FTPSite(src);
	    FTPSite ftpdest = new FTPSite(dest);
		
	    Vector sl = (Vector)siteTable.get(user);
	    if(sl==null){
		sl = new Vector();
		sl.add(ftpsrc);
		sl.add(ftpdest);
		}
		else{
		   boolean isSrc = false, isDest = false;
		   for(int i = 0; i<sl.size(); i++){
			FTPSite aux = (FTPSite)sl.get(i);
			if((aux.siteName).equals(src)) isSrc = true;
			if((aux.siteName).equals(dest)) isDest = true;
			if(isSrc == true && isDest == true) break;
		    }
		    if(isSrc == false)  sl.add(ftpsrc);
		    if(isDest == false) sl.add(ftpdest);
	    }
	    siteTable.put(user,sl);

	    /** input */
            if (info.get("TYPE").equals("STOR")) {
		sd = src;
                newFtpIn = (new Double(((String) info.get("NBYTES")))).doubleValue();
                ufdTotals.ftpInput += newFtpIn;
                userData.setElementAt(ufdTotals, 0);
		monTotals.ftpInput += newFtpIn;

                int goodPosition = getGoodResult(userData, sd);
                UserFTPData ufdSD;
                if (goodPosition == -1) {
                    ufdSD = new UserFTPData(user, sd);
                    ufdSD.ftpInput = ufdSD.ftpInput + newFtpIn;
                    userData.add(ufdSD);
                } else {
                    ufdSD = (UserFTPData) userData.elementAt(goodPosition);
                    ufdSD.ftpInput = ufdSD.ftpInput + newFtpIn;
                    userData.setElementAt(ufdSD, goodPosition);
                }	
            }

	    /** output */
            if (info.get("TYPE").equals("RETR")) {
		sd = dest;
                newFtpOut = (new Double(((String) info.get("NBYTES")))).doubleValue();
                ufdTotals.ftpOutput = ufdTotals.ftpOutput + newFtpOut;
                userData.setElementAt(ufdTotals, 0);
		monTotals.ftpOutput += newFtpOut;

                int goodPosition = getGoodResult(userData, sd);
                UserFTPData ufdSD;
                if (goodPosition == -1) {
                    ufdSD = new UserFTPData(user, sd);
                    ufdSD.ftpOutput = ufdSD.ftpOutput + newFtpOut;
                    userData.add(ufdSD);
                } else {
                    ufdSD = (UserFTPData) userData.elementAt(goodPosition);
                    ufdSD.ftpOutput = ufdSD.ftpOutput + newFtpOut;
                    userData.setElementAt(ufdSD, goodPosition);
                }
				
			}
            /** put the new values */
            voTotals.put(user, userData);
			
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(methodName + "() Exception: " + e);
        }
    }
    
    protected int getGoodResult(Vector v, String s) {
        /** the method name */
        String methodName = "getGoodResult";
        for (int i = 0; i < v.size(); i++) {
            UserFTPData aux = (UserFTPData) v.get(i);
            if (s.equals(aux.node_name)) return i;
        }
        return -1;
    }
    
    protected String getMachineName(String ip) {
        String methodName = "getMachineName";
		String dom = null;
		try {
            dom = InetAddress.getByName(ip).getCanonicalHostName();
        } catch (Exception e) {
            addToMsg(methodName, " Cannot get ip for node " + e);
            dom = ip;
        }
        return dom;
    }
    
    protected String getMachineDomain(String name) {
		if(Character.isDigit(name.charAt(0)))
			return name;
        String[] domElements = new String[] {};
        String domain = null;
        domElements = name.split("\\.");
        int n = domElements.length;
        if (n >= 2)
            domain = domElements[n - 2] + "." + domElements[n - 1];
        else
            domain = name;
        return domain;
    }
    
    /** Compute the rate of input and output */
    protected void computeFTPRate(long interval) {
        /** the method name */
        String methodName = "computeFTPRate";
        Enumeration e = voTotals.keys();
        while (e.hasMoreElements()) {
            String user = (String) e.nextElement();
            Vector ud = (Vector) voTotals.get(user);
            for (int i = 0; i < ud.size(); i++) {
                UserFTPData ufd = (UserFTPData) ud.get(i);
                ufd.computeRate(interval);
                ud.setElementAt(ufd, i);
            }
            voTotals.put(user, ud);
        }
    }
	
	FTPSite getSite(Vector ftps, String name){
		FTPSite result = null;
		for(int i = 0; i<ftps.size(); i++)
			if(((FTPSite)ftps.elementAt(i)).siteName.equals(name))
				result = (FTPSite)ftps.get(i);
		return result;
	}
    
    /**
     * Create a vector of Result - output of this Module
     * @return vector results
     */
    protected Vector createResultsVector() {
	String methodName = "createResultsVector";
        try{
		Vector results = new Vector();
		double factor = 1024.0;
		Result result;
		long resultTime = NTPDate.currentTimeMillis();

        	/** first, create general totals Result */
        	result = new Result(Node.getFarmName(), monTotals.user_name, monTotals.node_name, ModuleName, MyResTypes);
        	result.time = resultTime;
        	result.param[0] = monTotals.ftpInput / factor;
        	result.param[1] = monTotals.ftpOutput / factor;
        	result.param[2] = monTotals.ftpRateIn / factor;
        	result.param[3] = monTotals.ftpRateOut / factor;
		results.addElement(result);
			
	        /** then, create VO_user totals Result */
	        Enumeration e = voTotals.keys();
	        while (e.hasMoreElements()) {
			String user = (String) e.nextElement();
			Vector values = (Vector) voTotals.get(user);
			UserFTPData aux = (UserFTPData) values.get(0);
			if(MixedCaseVOs)
				result = new Result(Node.getFarmName(), Node.getClusterName(), aux.user_name, ModuleName, MyResTypes);
			else
				result = new Result(Node.getFarmName(), Node.getClusterName(), (aux.user_name).toUpperCase(), ModuleName, MyResTypes);
			result.time = resultTime;
			result.param[0] = aux.ftpInput / factor;
			result.param[1] = aux.ftpOutput / factor;
			result.param[2] = aux.ftpRateIn / factor;
			result.param[3] = aux.ftpRateOut / factor;
				
			Vector userSites = (Vector)siteTable.get(user);
			for (int i = 0; i < userSites.size(); i++) {
				FTPSite site = (FTPSite)userSites.elementAt(i);
				int ufdPos = getGoodResult(values,site.siteName);
				if(ufdPos>0){
					if(site.isFirst == true){
						Result r0 = new Result();
						r0.FarmName = Node.getFarmName();
						r0.ClusterName = Node.getClusterName();
						if(MixedCaseVOs)
						    r0.NodeName = aux.user_name;
						else
						    r0.NodeName = (aux.user_name).toUpperCase();    
						r0.time = resultTime - doProcessInterval;
						r0.addSet("ftpInput_" + site.siteName, 0D);
						r0.addSet("ftpRateIn_" + site.siteName, 0D);
						r0.addSet("ftpOutput_" + site.siteName, 0D);
						r0.addSet("ftpRateOut_" + site.siteName, 0D);
						site.isFirst = false;
						userSites.setElementAt(site,i);
						results.add(r0);
					}
					aux = (UserFTPData) values.get(ufdPos);
					result.addSet("ftpInput_" + aux.node_name, aux.ftpInput / factor);
					result.addSet("ftpRateIn_" + aux.node_name, aux.ftpRateIn / factor);
					result.addSet("ftpOutput_" + aux.node_name, aux.ftpOutput / factor);
					result.addSet("ftpRateOut_" + aux.node_name, aux.ftpRateOut / factor);
	                	} else{
					result.addSet("ftpInput_" + site.siteName, 0D);
					result.addSet("ftpRateIn_" + site.siteName, 0D);
					result.addSet("ftpOutput_" + site.siteName, 0D);
					result.addSet("ftpRateOut_" + site.siteName, 0D);
					userSites.remove(i);
					i--;
				}
			}
			results.addElement(result);
			siteTable.put(user,userSites);
	        }
		if(getShouldNotifyConfig()) {
			logit("\n\nWILL RETURN NEW CONFIG!");
			for(int i=0; i<removedVOseResults.size(); i++) {
				eResult er = (eResult) removedVOseResults.get(i);
				if(er != null) {
					if(MixedCaseVOs==false)
						er.NodeName = er.NodeName.toUpperCase(); 
					results.add(er);
					siteTable.remove(er.NodeName);
				}
			}
		}
		addToMsg(methodName, "sent "+results.size() + " results.");
        	return results;
        }catch (Exception e){
            logger.log(Level.WARNING,"Got Exc:",e);
			return null;
        }
    }

	public StringBuffer sbProcOutput (String cmd, long delay, int nCommandsToCheck) {
		StringBuffer ret = new StringBuffer();
		BufferedReader br1 = procOutput(cmd, delay);
		if (br1 == null)
			return null;
		
		String line;
		int nSuccessfulCommands = 0;
		try {
			while ((line = br1.readLine()) != null) {
				if (line.indexOf("ML_OSGVOIO_OK") >= 0)
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
		String cmd = "ssh " + remoteHost + " 'echo $" + var + " && echo ML_OSGVOIO_OK'";
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
    
    public class UserFTPData {
        public String user_name = new String("user");
        public String node_name = new String("node");
        public double ftpInput, ftpOutput;
        public double ftpRateIn, ftpRateOut;
        
        UserFTPData(String clusterName, String nodeName) {
            user_name = clusterName;
            node_name = nodeName;
            ftpInput = 0.0;
            ftpOutput = 0.0;
            ftpRateIn = 0.0;
            ftpRateOut = 0.0;
        }
        
        void computeRate(long interval) {
            ftpRateIn = ftpInput / interval;
            ftpRateOut = ftpOutput / interval;
        }
    };
	
	public class FTPSite{
		public String siteName;
		public boolean isFirst;
		
		public FTPSite(String name){
			siteName = name;
			isFirst = true;
		}
	}
    
    /** main function - a test function */
    static public void main(String[] args) {
        /** write this function if you want to test the module without MonALISA
         * started. Do you want ?
         */
    }
    
}// end class
