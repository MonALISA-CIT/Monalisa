package lia.Monitor.Filters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.DBFunctions;
import lazyj.ExtProperties;
import lazyj.cache.ExpirationCache;
import lazyj.cache.GenericLastValuesCache;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.Histogram;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.DataArray;
import lia.util.MLProcess;
import lia.util.StringFactory;
import lia.util.net.NetMatcher;
import lia.util.ntp.NTPDate;

/**
 * Provide site local and centralized aggregations and summaries for all data AliEn sends to ML.
 *
 * @author catac
 */
public class AliEnFilter extends GenericMLFilter implements AppConfigChangeListener {

	/**
	 * Some serial number
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Whether or not to use the AliEn LDAP to find the best ML service name matching a given queue name
	 */
	private static final boolean MATCH_ML_FROM_LDAP = true;

	/** Logger used by this class */
	static final Logger logger = Logger.getLogger(AliEnFilter.class.getName());

	/**
	 * How often I should send summarized data (given in ml.properties in seconds) Default value: 2 minutes
	 */
	public static final long AF_SLEEP_TIME = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.SLEEP_TIME", 120) * 1000;

	/**
	 * Consider a param no longer available after PARAM_EXPIRE time (given in ml.properties in seconds) Default value: 15 minutes
	 */
	static long PARAM_EXPIRE = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.PARAM_EXPIRE", 900) * 1000;

	/**
	 * Consider a job from the central services expired after this amount of time (given in ml.prop in seconds) Default value: 1 week
	 */
	static final long JOB_STATUS_CS_EXPIRE = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.JOB_STATUS_CS_EXPIRE", 604800) * 1000;

	/**
	 * Consider a FTD transfer from the central services expired after this amount of time (given in ml.prop in seconds) Default value: 1 week
	 */
	static final long TRANSFER_STATUS_CS_EXPIRE = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.TRANSFER_STATUS_CS_EXPIRE", 604800) * 1000;

	/**
	 * Remove a job in zombie state after ZOMBIE_EXPIRE time (given in ml.properties in seconds) Default value: 2 hours
	 */
	static final long ZOMBIE_EXPIRE = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.ZOMBIE_EXPIRE", 7200) * 1000;

	/**
	 * get the Sites and SEs from LDAP each LDAP_QUERY_INTERVAL (given in ml.properties seconds) Default value: 2 hours
	 */
	static final long LDAP_QUERY_INTERVAL = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.LDAP_QUERY_INTERVAL", 7200) * 1000;

	/** the ALIEN_ROOT path is taken from the environment of the script starting ML */
	private static final String ALIEN_ROOT = AppConfig.getGlobalEnvProperty("ALIEN_ROOT");

	/** the alien command */
	static final String ALIEN = ALIEN_ROOT + "/bin/alien";

	/** the perl used in AliEn */
	static final String ALIEN_PERL = ALIEN_ROOT + "/bin/alien-perl";

	/** path to getAliEnFilterConf.pl script; this will be run using the alien-perl from $ALIEN_ROOT/bin */
	static final String LDAP_QUERY_SCRIPT = ALIEN_ROOT + "/java/MonaLisa/AliEn/getAliEnFilterConf.pl";

	/** path to the vobox_mon.pl script that monitors the VO Box */
	static final String VOBOX_MON_SCRIPT = ALIEN_ROOT + "/java/MonaLisa/AliEn/vobox_mon.pl";

	/** path to the ML Farm HOME */
	static final String FarmHOME = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);

	/** path to getJobs.pl script; this will fetch the currently active jobs in AliEn queue. */
	static final String JOB_SYNC_SCRIPT = ALIEN_ROOT + "/java/MonaLisa/AliEn/getJobs.pl";

	/** path to getTransfers.pl script; this will fetch the currently active transfers in AliEn queue. */
	static final String TRANSFER_SYNC_SCRIPT = ALIEN_ROOT + "/java/MonaLisa/AliEn/getTransfers.pl";

	/**
	 *
	 */
	static final int CPUsPerNode = AppConfig.geti("lia.Monitor.Filters.AliEnFilter.ROOT_CPUs_PER_NODE", 1);

	/**
	 * Timeout for getJobs.pl script Default value: 20 minutes
	 */
	static final long JOB_SYNC_SCRIPT_TIMEOUT = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.JOB_SYNC_SCRIPT_TIMEOUT", 20 * 60) * 1000;

	/**
	 * tells if the JOB_SYNC_SCRIPT and TRASNFER_SYNC_SCRIPT should be run when AliEn filter starts Default value: false
	 */
	static final boolean RUN_JOB_SYNC_SCRIPT = AppConfig.getb("lia.Monitor.Filters.AliEnFilter.RUN_JOB_SYNC_SCRIPT", false);

	/**
	 * if RUN_JOB_SYNC_SCRIPT is true, how often we should run the sync scripts Default value: 2 hours
	 */
	static long JOB_SYNC_RUN_INTERVAL = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.JOB_SYNC_RUN_INTERVAL", 2 * 3600) * 1000;

	private static boolean ROOT_JOB_TOTALS = AppConfig.getb("lia.Monitor.Filters.AliEnFilter.ROOT_JOBS_TOTALS", false);

	private static boolean ROOT_SUMMARY_PER_SUBJOB = AppConfig.getb("lia.Monitor.Filters.AliEnFilter.ROOT_SUMMARY_PER_SUBJOB", false);

	/** The name of this filter */
	public static final String Name = "AliEnFilter";

	/** contains hashes with job data for each CE (JobInfo's) */
	final Hashtable<String, Hashtable<String, JobInfo>> htComputingElements = new Hashtable<>();

	/** contains hashes with job data for each CE (JobInfo's) */
	private final Hashtable<String, Hashtable<String, NodeInfo>> htSites = new Hashtable<>();

	/** known job states for all CEs */
	final DataArray knownJobStates = new DataArray();

	/** known job params for all CEs */
	final DataArray knownJobParams = new DataArray();

	/** known node params for all Sites */
	final DataArray knownNodeParams = new DataArray();

	/** known job status for jobs in Central Services */
	final DataArray knownJobStatusCS = new DataArray();

	/** known job status for jobs on Sites */
	final DataArray knownJobStatusSite = new DataArray();

	/** known transfer status for FTD transfers */
	final DataArray knownTransferStatusCS = new DataArray();

	/** known transfer parameters */
	final DataArray knownTransferParams = new DataArray();

	/** known params for root jobs host statistics */
	final DataArray knownRootJobsHostParams = new DataArray();

	/** known params for root jobs destination statistics */
	final DataArray knownRootJobsDestParams = new DataArray();

	/** known params for root jobs destToHost statistics */
	final DataArray knownRootJobsDestToHostParams = new DataArray();

	/** known params for user jobs statistics */
	final DataArray knownUserJobsParams = new DataArray();

	/** known params for job agents statistics */
	final DataArray knownJobAgentParams = new DataArray();

	/** contains NetMatcher's with sites domains */
	final Hashtable<String, NetMatcher> htSitesNetMatchers = new Hashtable<>();

	/**
	 * Reentrant lock for the above NetMatcher
	 */
	final ReentrantReadWriteLock sitesNetMatchersLock = new ReentrantReadWriteLock();

	/** contains NatMatcher's with SEs hosts */
	final Hashtable<String, NetMatcher> htSEsNetMatchers = new Hashtable<>();

	/**
	 *
	 */
	final ReentrantReadWriteLock sesNetMatchers = new ReentrantReadWriteLock();

	/** holds data about sites traffic (XrdInfo's) */
	final Hashtable<String, XrdInfo> htSitesTraffic = new Hashtable<>();

	/** holds data about SEs traffic (XrdInfo's) */
	final Hashtable<String, XrdInfo> htSEsTraffic = new Hashtable<>();

	/** how many jobs can run on this site (from LDAP) */
	final Hashtable<String, Integer> htCEsMaxJobs = new Hashtable<>();

	/** holds data about Castorgrid servers */
	final Hashtable<String, RfcpInfo> htCastorgridServers = new Hashtable<>();

	/** contains CE hosts (NetMatcher-s) for each site */
	final Hashtable<String, NetMatcher> htSitesCEhosts = new Hashtable<>();

	/**
	 *
	 */
	final ReentrantReadWriteLock sitesCEhostsLock = new ReentrantReadWriteLock();

	/** contains the jobs in each Organisation (Hash of JobStatusCSs) */
	final Hashtable<String, Hashtable<String, JobStatusCS>> htOrgCSJobStats = new Hashtable<>();

	/** contains the FTD transfers in each organisation (Hash of TransferStatusCSs) */
	final Hashtable<String, Hashtable<String, TransferStatusCS>> htOrgCSTransferStats = new Hashtable<>();

	/** contains FTD transfers summaries */
	final Hashtable<String, DataArray> htOrgTransfersSESummary = new Hashtable<>();

	/** contains FTD transfers summaries */
	final Hashtable<String, DataArray> htOrgTransfersStatusSummary = new Hashtable<>();

	/** contains hashes with ROOT job data for each Site (RootJobInfo's) */
	final Hashtable<String, Hashtable<String, RootJobInfo>> htSitesRootJobs = new Hashtable<>();

	/** contains hashes with API Services for each Site (APIServiceInfo's) */
	final Hashtable<String, Hashtable<String, APIServiceInfo>> htSitesAPIServices = new Hashtable<>();

	/** contains hashes with JobAgents for each Site (JobAgentInfo's) */
	final Hashtable<String, Hashtable<String, JobAgentInfo>> htSitesJobAgents = new Hashtable<>();

	/**
	 * histogram of number of jobs executed by the job agents on each site
	 */
	final Hashtable<String, Histogram> htSitesJobAgentsNumJobs = new Hashtable<>();

	/** contains hashes with CEs for each Site (CEInfo's) -> summarize jobAgents_(running|slots|queued) */
	final Hashtable<String, Hashtable<String, CEInfo>> htSitesCEs = new Hashtable<>();

	/** contains hashes with DAQ hosts for each site (DAQInfo's) */
	final Hashtable<String, Hashtable<String, DAQInfo>> htSitesDAQ = new Hashtable<>();

	/** contains FTSTransferInfo's with key=FTD transferID */
	final Hashtable<String, Hashtable<String, FTSTransferInfo>> htFTSTransfers = new Hashtable<>();

	/** contains FDTTestTransferInfo's with key=CrtMLName_destIP:port */
	final Hashtable<String, FDTTestTransferInfo> htFDTTestTransfers = new Hashtable<>();

	/** contains QuotaInfo's with key=node (i.e. user_requestType) */
	final Hashtable<String, QuotaInfo> htQuotaInfos = new Hashtable<>();

	/** contains hashes like [CAF instance (string) => hash [ unique CAF job-like ID => RootCafUsageInfo] */
	final Hashtable<String, Hashtable<String, RootCafUsageInfo>> htRootCafUsageInfos = new Hashtable<>();

	/** contains summaries for XrdServers data produced by the monXrootd module */
	final Hashtable<String, XrdServerInfo> htXrdServers = new Hashtable<>();

	/** contains hashes with SEs */
	final Hashtable<String, SETransferInfo> htAliEnSEs = new Hashtable<>();

	/** contains results produced for debug purposes */
	// final Vector vDebugResults = new Vector();

	/** Record the time when entering these CS Job statuses. */
	final static int[] timeWatchedCSJobStatuses = { JobUtil.JS_INSERTING, JobUtil.JS_ASSIGNED, JobUtil.JS_STARTED, JobUtil.JS_RUNNING, JobUtil.JS_SAVING, JobUtil.JS_SAVED, JobUtil.JS_DONE,
			JobUtil.JS_ANY_ERROR };

	/**
	 * Produce summaries for time deltas from the first to the second CS Job status in this list. Whatever status appears here MUST be also put in the list above (timeWatchedCSJobStatuses).
	 */
	final static int[][] timeWatchedCSJobTransitions = { { JobUtil.JS_INSERTING, JobUtil.JS_ASSIGNED }, { JobUtil.JS_ASSIGNED, JobUtil.JS_RUNNING }, { JobUtil.JS_RUNNING, JobUtil.JS_SAVING },
			{ JobUtil.JS_SAVING, JobUtil.JS_DONE }, { JobUtil.JS_ASSIGNED, JobUtil.JS_DONE }, { JobUtil.JS_INSERTING, JobUtil.JS_ANY_ERROR }, { JobUtil.JS_ASSIGNED, JobUtil.JS_ANY_ERROR },
			{ JobUtil.JS_RUNNING, JobUtil.JS_ANY_ERROR }, { JobUtil.JS_ASSIGNED, JobUtil.JS_STARTED }, { JobUtil.JS_STARTED, JobUtil.JS_RUNNING }, { JobUtil.JS_SAVING, JobUtil.JS_SAVED },
			{ JobUtil.JS_SAVED, JobUtil.JS_DONE }, };

	/** Helper class to identify AliEn job's status */
	public static final class JobUtil {
		// All AliEn job's possible states
		/**
		 * inserting
		 */
		public static final int JS_INSERTING = 1;

		/**
		 *
		 */
		public static final int JS_SPLITTING = 2;
		/**
		 *
		 */
		public static final int JS_SPLIT = 3;
		/**
		 *
		 */
		public static final int JS_QUEUED = 4;
		/**
		 *
		 */
		public static final int JS_WAITING = 5;
		/**
		 *
		 */
		public static final int JS_ASSIGNED = 6;
		/**
		 *
		 */
		public static final int JS_STARTED = 7;
		/**
		 *
		 */
		public static final int JS_INTERACTIV = 8;
		/**
		 *
		 */
		public static final int JS_IDLE = 9;
		/**
		 *
		 */
		public static final int JS_RUNNING = 10;
		/**
		 *
		 */
		public static final int JS_SAVING = 11;
		/**
		 *
		 */
		public static final int JS_SAVED = 12;
		/**
		 *
		 */
		public static final int JS_MERGING = 13;
		/**
		 *
		 */
		public static final int JS_FORCEMERGE = 14;
		/**
		 *
		 */
		public static final int JS_DONE = 15;
		/**
		 *
		 */
		public static final int JS_DONE_WARNING = 16;
		/**
		 *
		 */
		public static final int JS_TO_STAGE = 17;
		/**
		 *
		 */
		public static final int JS_A_STAGED = 18;
		/**
		 *
		 */
		public static final int JS_STAGING = 19;
		/**
		 *
		 */
		public static final int JS_OVER_WAITING = 21;
		/**
		 * Saved with warning
		 */
		public static final int JS_SAVED_WARNING = 22;
		/**
		 *
		 */
		public static final int JS_UPDATING = 23;
		/**
		 *
		 */
		public static final int JS_FAULTY = 24;
		/**
		 *
		 */
		public static final int JS_INCORRECT = 25;
		/**
		 *
		 */
		public static final int JS_ERROR_A = -1;
		/**
		 *
		 */
		public static final int JS_ERROR_I = -2;
		/**
		 *
		 */
		public static final int JS_ERROR_E = -3;
		/**
		 *
		 */
		public static final int JS_ERROR_IB = -4;
		/**
		 *
		 */
		public static final int JS_ERROR_M = -5;
		/**
		 *
		 */
		public static final int JS_ERROR_R = -6;
		/**
		 *
		 */
		public static final int JS_ERROR_S = -7;
		/**
		 *
		 */
		public static final int JS_ERROR_SPLIT = -8;
		/**
		 *
		 */
		public static final int JS_ERROR_SV = -9;
		/**
		 *
		 */
		public static final int JS_ERROR_V = -10;
		/**
		 *
		 */
		public static final int JS_ERROR_VN = -11;
		/**
		 *
		 */
		public static final int JS_EXPIRED = -12;
		/**
		 *
		 */
		public static final int JS_FAILED = -13;
		/**
		 *
		 */
		public static final int JS_KILLED = -14;
		/**
		 *
		 */
		public static final int JS_ZOMBIE = -15;
		/**
		 *
		 */
		public static final int JS_ERROR_VT = -16;
		/**
		 *
		 */
		public static final int JS_ERROR_RE = -17;
		/**
		 *
		 */
		public static final int JS_ERROR_EW = -18;
		/**
		 *
		 */
		public static final int JS_ERROR_W = -19;

		/**
		 *
		 */
		public static final int JS_ANY_ERROR = -100; // this includes any of the errors, except for zombie
		// and a constant to define unknown status from my point of view
		/**
		 *
		 */
		public static final int JS_UNKNOWN = 0;

		/**
		 * convert the job status from int to string, as defined in AliEn::Util.pm {'INSERTING' => 1, 'SPLITTING' => 2, 'SPLIT' => 3, 'QUEUED' => 4, 'WAITING' => 5, 'ASSIGNED' => 6, 'STARTED' => 7,
		 * 'INTERACTIV' => 8, 'IDLE' => 9, 'RUNNING' => 10, 'SAVING' => 11, 'SAVED' => 12, 'MERGING' => 13, 'FORCEMERGE' => 14, 'DONE' => 15, 'DONE_WARNING' => 16, 'TO_STAGE'=>17,
		 * 'A_STAGED'=>18,'STAGING'=>19, 'ERROR_A' => -1, 'ERROR_I' => -2, 'ERROR_E' => -3, 'ERROR_IB' => -4, 'ERROR_M' => -5, 'ERROR_R' => -6, 'ERROR_S' => -7, 'ERROR_SPLT' => -8, 'ERROR_SV' => -9,
		 * 'ERROR_V' => -10, 'ERROR_VN' => -11, 'EXPIRED' => -12, 'FAILED' => -13, 'KILLED' => -14, 'ZOMBIE' => -15, 'ERROR_VT' => -16, 'ERROR_RE' => -17};
		 *
		 * @param status
		 * @return String
		 */
		public static String jobStatusToText(final int status) {
			switch (status) {
				case JS_INSERTING:
					return "INSERTING";
				case JS_SPLITTING:
					return "SPLITTING";
				case JS_SPLIT:
					return "SPLIT";
				case JS_QUEUED:
					return "QUEUED";
				case JS_WAITING:
					return "WAITING";
				case JS_ASSIGNED:
					return "ASSIGNED";
				case JS_STARTED:
					return "STARTED";
				case JS_INTERACTIV:
					return "INTERACTIV";
				case JS_IDLE:
					return "IDLE";
				case JS_RUNNING:
					return "RUNNING";
				case JS_SAVING:
					return "SAVING";
				case JS_SAVED:
					return "SAVED";
				case JS_SAVED_WARNING:
					return "SAVED_WARNING";
				case JS_MERGING:
					return "MERGING";
				case JS_FORCEMERGE:
					return "FORCEMERGE";
				case JS_DONE:
					return "DONE";
				case JS_DONE_WARNING:
					return "DONE_WARNING";
				case JS_TO_STAGE:
					return "TO_STAGE";
				case JS_A_STAGED:
					return "A_STAGED";
				case JS_STAGING:
					return "STAGING";
				case JS_ERROR_A:
					return "ERROR_A";
				case JS_ERROR_I:
					return "ERROR_I";
				case JS_ERROR_E:
					return "ERROR_E";
				case JS_ERROR_IB:
					return "ERROR_IB";
				case JS_ERROR_M:
					return "ERROR_M";
				case JS_ERROR_R:
					return "ERROR_R";
				case JS_ERROR_S:
					return "ERROR_S";
				case JS_ERROR_SPLIT:
					return "ERROR_SPLT";
				case JS_ERROR_SV:
					return "ERROR_SV";
				case JS_ERROR_V:
					return "ERROR_V";
				case JS_ERROR_VN:
					return "ERROR_VN";
				case JS_EXPIRED:
					return "EXPIRED";
				case JS_FAILED:
					return "FAILED";
				case JS_KILLED:
					return "KILLED";
				case JS_ZOMBIE:
					return "ZOMBIE";
				case JS_ERROR_VT:
					return "ERROR_VT";
				case JS_ERROR_RE:
					return "ERROR_RE";
				case JS_ERROR_W:
					return "ERROR_W";
				case JS_ERROR_EW:
					return "ERROR_EW";
				case JS_OVER_WAITING:
					return "OVER_WAITING";
				case JS_UPDATING:
					return "UPDATING";
				case JS_FAULTY:
					return "FAULTY";
				case JS_INCORRECT:
					return "INCORRECT";
				case JS_ANY_ERROR:
					return "ERROR";
				default:
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Unknown job status for " + status);
					return "UNKNOWN";
			}
		}

		/**
		 * returns true if the given status is cummulative
		 *
		 * @param status
		 * @return boolean
		 */
		static final boolean isCummulativeStatus(final int status) {
			return (status == JS_STARTED) || (status == JS_SAVED) || (status == JS_SAVED_WARNING) || (status == JS_ASSIGNED) || isFinalStatus(status);
		}

		/**
		 * returns true if the given status is final, i.e. the job can be removed if expired in this state
		 *
		 * @param status
		 * @return boolean
		 */
		static final boolean isFinalStatus(final int status) {
			return (status == JS_DONE) || (status == JS_DONE_WARNING) || (status == JS_KILLED) || (status == JS_FAILED);
		}

		/**
		 * returns true if it's an error status, but not zombie
		 *
		 * @param status
		 * @return boolean
		 */
		@SuppressWarnings("unused")
		static boolean isError(final int status) {
			return (status < 0) && (status != JS_ZOMBIE);
		}

		/**
		 * Some error states should not be counted in order to avoid double-counting at resubmission and so on
		 *
		 * @param status
		 * @return true if the error should be ignored when summarizing
		 */
		static final boolean isIgnoredError(final int status) {
			return (status == JobUtil.JS_ZOMBIE) || (status == JobUtil.JS_EXPIRED) || (status == JobUtil.JS_KILLED);
		}

		/**
		 * returns true if the job is active
		 *
		 * @param status
		 * @return boolean
		 */
		static final boolean isActiveJob(final int status) {
			return (status == JS_ASSIGNED) || (status == JS_STARTED) || (status == JS_RUNNING) || (status == JS_SAVING);
		}

		/**
		 * get the position in the status time array for the given status, or -1 if the given's status time should not be stored.
		 *
		 * status time = time when CS Job enters that status
		 *
		 * @param status
		 * @return boolean
		 */
		public static int getStatusTimeIdx(final int status) {
			for (int i = timeWatchedCSJobStatuses.length - 1; i >= 0; i--)
				if (isEquivalentStatus(status, timeWatchedCSJobStatuses[i]))
					return i;

			return -1;
		}

		/**
		 * compares the two given statuses and returns true if they are the same or equivalent. For example, JS_ANY_ERROR is equivalent to any error status, except for zombie.
		 *
		 * @param status1
		 * @param status2
		 * @return boolean
		 */
		private static boolean isEquivalentStatus(final int status1, final int status2) {
			if (status1 == status2)
				return true;

			if ((status1 >= 0) && (status2 >= 0))
				return false;

			if (status1 == JS_ANY_ERROR)
				return (status2 < 0) && (status2 != JS_ZOMBIE);

			if (status2 == JS_ANY_ERROR)
				return (status1 < 0) && (status1 != JS_ZOMBIE);

			return false;
		}

		private static final int[] csSid = new int[] { JS_INSERTING, JS_SPLITTING, JS_SPLIT, JS_WAITING, // NO QUEUED anymore
				JS_TO_STAGE, JS_A_STAGED, JS_STAGING, JS_ASSIGNED, JS_MERGING, JS_FORCEMERGE, JS_DONE, JS_DONE_WARNING, JS_ERROR_A, // DONE for splitted jobs
				JS_ERROR_I, JS_ERROR_M, JS_ERROR_SPLIT, JS_KILLED, JS_ERROR_W, JS_ERROR_EW, JS_OVER_WAITING, JS_UPDATING, JS_FAULTY, JS_INCORRECT };

		private static final int[] siteSid = new int[] { JS_ASSIGNED, JS_STARTED, JS_RUNNING, JS_SAVING, JS_SAVED_WARNING, JS_SAVED, JS_DONE, JS_DONE_WARNING, // DONE for simple jobs
				JS_ERROR_E, JS_ERROR_IB, JS_ERROR_R, JS_ERROR_S, // NO INTERACTIV and IDLE
				JS_ERROR_SV, JS_ERROR_V, JS_ERROR_VN, JS_EXPIRED, JS_FAILED, JS_KILLED, JS_ZOMBIE, JS_ERROR_VT, JS_ERROR_RE };

		/**
		 * Fills the given arrays with the known jobs statuses both with CS and Site -related statuses.
		 *
		 * @param daCS
		 * @param daSite
		 * @param daUser
		 */
		public static void fillKnownJobStatusCS(final DataArray daCS, final DataArray daSite, final DataArray daUser) {
			for (int i = csSid.length - 1; i >= 0; i--) {
				daCS.setParam(jobStatusToText(csSid[i]) + "_jobs", 0);

				if (isCummulativeStatus(csSid[i]) || (csSid[i] < 0))
					daCS.setParam(jobStatusToText(csSid[i]) + "_jobs_R", 0);
			}

			daCS.setParam("ERR_jobs", 0);
			daCS.setParam("ERR_jobs_R", 0);
			daCS.setParam("TOTAL_jobs", 0);

			for (int i = siteSid.length - 1; i >= 0; i--) {
				daSite.setParam(jobStatusToText(siteSid[i]) + "_jobs", 0);
				if (isCummulativeStatus(siteSid[i]) || (siteSid[i] < 0))
					daSite.setParam(jobStatusToText(siteSid[i]) + "_jobs_R", 0);
			}

			daSite.setParam("ERR_jobs", 0);
			daSite.setParam("ERR_jobs_R", 0);
			daSite.setParam("active_jobs", 0);

			daCS.addToDataArray(daUser);
			daSite.addToDataArray(daUser);
		}

		/**
		 * Fills the given DAs with known stats for hostStats and destStats for ROOT jobs
		 *
		 * @param hostStats
		 * @param destStats
		 * @param destToHostStats
		 */
		public static void fillKnownRootJobsStats(final DataArray hostStats, final DataArray destStats, final DataArray destToHostStats) {
			// common summaries
			hostStats.setParam("read_mbytes_R", 0);
			hostStats.setParam("read_files_R", 0);

			destToHostStats.setAsDataArray(hostStats);

			// summaries for hosts and dests
			hostStats.setParam("local_read_mbytes_R", 0);
			hostStats.setParam("local_read_files_R", 0);
			hostStats.setParam("external_read_mbytes_R", 0);
			hostStats.setParam("external_read_files_R", 0);

			destStats.setAsDataArray(hostStats);

			// host-only summaries
			hostStats.setParam("STARTED_jobs", 0);
			hostStats.setParam("PROCESSING_jobs", 0);
			hostStats.setParam("DONE_jobs", 0);
			hostStats.setParam("DONE_jobs_R", 0);

			hostStats.setParam("events", 0);
			hostStats.setParam("events_R", 0);
			hostStats.setParam("cputime", 0);
			hostStats.setParam("cputime_R", 0);
			hostStats.setParam("cpu_usage", 0);
			hostStats.setParam("jobs_count", 0);
			hostStats.setParam("processedbytes", 0);
			hostStats.setParam("processedbytes_R", 0);
			hostStats.setParam("shdmem", 0);
			hostStats.setParam("rssmem", 0);
			hostStats.setParam("totmem", 0);
		}
	}

	/**
	 * Helper class to identify AliEn transfers status. The status information is taken from AliEn Central services.
	 */
	private static class TransferUtil {
		// All transfers status
		/**
		 *
		 */
		public static final int TS_INSERTING = 1;
		/**
		 *
		 */
		public static final int TS_WAITING = 2;
		/**
		 *
		 */
		public static final int TS_ASSIGNED = 3;
		/**
		 *
		 */
		public static final int TS_LOCAL_COPY = 4;
		/**
		 *
		 */
		public static final int TS_TRANSFERRING = 5;
		/**
		 *
		 */
		public static final int TS_CLEANING = 6;
		/**
		 *
		 */
		public static final int TS_DONE = 7;
		/**
		 *
		 */
		public static final int TS_FAILED = -1;
		/**
		 *
		 */
		public static final int TS_KILLED = -2;
		/**
		 *
		 */
		public static final int TS_EXPIRED = -3;
		// and a constant to define unknown status from my point of view
		/**
		 *
		 */
		public static final int TS_UNKNOWN = 0;

		/**
		 * convert the transfer status from int to string, as defined in AliEn::Util.pm 'INSERTING' => 1, 'WAITING' => 2, 'ASSIGNED' => 3, 'LOCAL COPY' => 4, 'TRANSFERRING' => 5, 'CLEANING' => 6,
		 * 'DONE' => 7, 'FAILED' => -1, 'KILLED' => -2, 'EXPIRED' => -3,
		 *
		 * @param status
		 * @return boolean
		 */
		static String transferStatusToText(final int status) {
			switch (status) {
				case TS_INSERTING:
					return "INSERTING";
				case TS_WAITING:
					return "WAITING";
				case TS_ASSIGNED:
					return "ASSIGNED";
				case TS_LOCAL_COPY:
					return "LOCAL_COPY";
				case TS_TRANSFERRING:
					return "TRANSFERRING";
				case TS_CLEANING:
					return "CLEANING";
				case TS_DONE:
					return "DONE";
				case TS_FAILED:
					return "FAILED";
				case TS_KILLED:
					return "KILLED";
				case TS_EXPIRED:
					return "EXPIRED";
				default:
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Unknown transfer status for " + status);
					return "UNKNOWN";
			}
		}

		/**
		 * returns true if the given status is cummulative
		 *
		 * @param status
		 * @return boolean
		 */
		static boolean isCummulativeStatus(final int status) {
			return ((status == TS_DONE) || (status == TS_KILLED) || (status == TS_FAILED) || (status == TS_EXPIRED));
		}

		private static final int[] csSid = new int[] { TS_INSERTING, TS_WAITING, TS_ASSIGNED, TS_LOCAL_COPY, TS_TRANSFERRING, TS_CLEANING, TS_DONE, TS_FAILED, TS_KILLED };

		/**
		 * Fills the given array with the known transfer statuses
		 *
		 * @param daTransfStats
		 * @param daTransfParams
		 */
		public static void fillKnownTransferStatusCS(final DataArray daTransfStats, final DataArray daTransfParams) {
			for (int i = csSid.length - 1; i >= 0; i--) {
				final String sStatus = transferStatusToText(csSid[i]);

				daTransfStats.setParam(sStatus + "_transfers", 0);

				if (isCummulativeStatus(csSid[i]))
					daTransfStats.setParam(sStatus + "_transfers_R", 0);
			}

			daTransfParams.setParam("transf_mbytes", 0);
			daTransfParams.setParam("transf_mbytes_R", 0);
			daTransfParams.setParam("transf_speed_mbs", 0);
		}

		/**
		 * Fills the given array with the known parameters for FDT Test transfers
		 *
		 * @param daFDTLinkParams
		 */
		public static void fillKnownFDTTestTransferParams(final DataArray daFDTLinkParams) {
			daFDTLinkParams.setParam("DISK_READ", 0);
			daFDTLinkParams.setParam("NET_OUT", 0);
		}
	}

	/** Helper class to identify the status of a JobAgent */
	private static final class JobAgentUtil {
		// Statuses for Job Agents, as defined in AliEn::Util.pm
		/**
		 *
		 */
		public static final int AS_REQUESTING_JOB = 1;
		/**
		 *
		 */
		public static final int AS_INSTALLING_PKGS = 2;
		/**
		 *
		 */
		public static final int AS_JOB_STARTED = 3;
		/**
		 *
		 */
		public static final int AS_RUNNING_JOB = 4;
		/**
		 *
		 */
		public static final int AS_DONE = 5;
		/**
		 *
		 */
		public static final int AS_ERROR_HC = -1; // error in getting host classad
		/**
		 *
		 */
		public static final int AS_ERROR_IP = -2; // error installing packages
		/**
		 *
		 */
		public static final int AS_ERROR_GET_JDL = -3; // error getting jdl
		/**
		 *
		 */
		public static final int AS_ERROR_JDL = -4; // incorrect jdl
		/**
		 *
		 */
		public static final int AS_ERROR_DIRS = -5; // error creating directories, not enough free space in workdir
		/**
		 *
		 */
		public static final int AS_ERROR_START = -6; // error forking to start job
		// and a constant to define unknown status from my point of view
		/**
		 *
		 */
		@SuppressWarnings("unused")
		public static final int AS_UNKNOWN = 0;

		/**
		 * convert the status from number to string
		 *
		 * @param status
		 * @return String
		 */
		static String jaStatusToText(final int status) {
			switch (status) {
				case AS_REQUESTING_JOB:
					return "REQUESTING_JOB";
				case AS_INSTALLING_PKGS:
					return "INSTALLING_PKGS";
				case AS_JOB_STARTED:
					return "JOB_STARTED";
				case AS_RUNNING_JOB:
					return "RUNNING_JOB";
				case AS_DONE:
					return "DONE";
				case AS_ERROR_HC:
					return "ERROR_HC";
				case AS_ERROR_IP:
					return "ERROR_IP";
				case AS_ERROR_GET_JDL:
					return "ERROR_GET_JDL";
				case AS_ERROR_JDL:
					return "ERROR_JDL";
				case AS_ERROR_DIRS:
					return "ERROR_DIRS";
				case AS_ERROR_START:
					return "ERROR_START";
				default:
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Unknown JobAgent status for " + status);
					return "UNKNOWN";
			}
		}

		private static final int[] jaSid = new int[] { AS_REQUESTING_JOB, AS_INSTALLING_PKGS, AS_JOB_STARTED, AS_RUNNING_JOB, AS_DONE, AS_ERROR_HC, AS_ERROR_IP, AS_ERROR_GET_JDL, AS_ERROR_JDL,
				AS_ERROR_DIRS, AS_ERROR_START };

		/**
		 * Fills the given arrays with the known job agents statuses
		 *
		 * @param daJAStats
		 */
		public static void fillKnownJobAgentsStatus(final DataArray daJAStats) {
			for (int i = jaSid.length - 1; i >= 0; i--) {
				final int s = jaSid[i];

				final String sTextStatus = jaStatusToText(s);

				daJAStats.setParam(sTextStatus + "_ja", 0);

				if (isCummulativeStatus(s))
					daJAStats.setParam(sTextStatus + "_ja_R", 0);
			}
		}

		/**
		 * return true if this is a cummulative status
		 *
		 * @param status
		 * @return boolean
		 */
		public static boolean isCummulativeStatus(final int status) {
			return ((status < 0) || (status == AS_DONE));
		}
	}

	/**
	 * convert a values from bytes to MBytes
	 *
	 * @param bytes
	 * @return MBytes
	 */
	static double convertBtoMB(final double bytes) {
		return bytes / (1024.0 * 1024.0);
	}

	/**
	 * @param d
	 * @return true if d~=0
	 */
	static final boolean isZero(final double d) {
		return Math.abs(d) < 1e-10;
	}

	/** Class used to store information about a job */
	private final class JobInfo {
		/**
		 * in what CE this job is run
		 */
		final String ceName;

		/**
		 * the job id
		 */
		final String jobID;

		// absolute values:
		// status
		// host
		// disk_usage workdir_size disk_total disk_free disk_used virtualmem mem_usage rss cpu_usage

		// XRD_Transfers
		// speed
		// UNUSED YET: total_size elapsed_time percent src_IP dst_IP

		// rates:
		// cpu_time run_time
		// moved_bytes read_bytes written_bytes

		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * job's status
		 */
		int status;

		/**
		 * host where this job runs
		 */
		String host;
		/**
		 * alien user who started this job
		 */
		String jobUser;

		/**
		 * absolute values
		 */
		final DataArray values = new DataArray();

		/**
		 * for rates, last set of values
		 */
		final DataArray lastValues = new DataArray();

		/**
		 * for rates, current set of values
		 */
		final DataArray crtValues = new DataArray();

		/**
		 * rates=(crtValues-lastValues)/deltaT; lastValues = crtValues
		 */
		final DataArray rates = new DataArray();

		/**
		 * record stats through which the job has been
		 */
		final DataArray stats = new DataArray();

		/**
		 * remember previously reported stats for job stats reported as deltas and rates
		 */
		final DataArray prevStats = new DataArray();

		/**
		 * data about previous external transfer
		 */
		final DataArray prevTransfer = new DataArray();

		/**
		 * data about previous internal transfer
		 */
		final DataArray prevRootTransf = new DataArray();

		/**
		 * @param sCEName
		 * @param sJobID
		 */
		public JobInfo(final String sCEName, final String sJobID) {
			this.ceName = sCEName;
			this.jobID = sJobID;
			this.values.setParam("count", 1); // this is to count how many JobInfo instances give a certain value.
		}

		@Override
		public String toString() {
			// use the unused field
			return this.ceName;
		}

		/**
		 * return true if the current result refers to the same transfer it's the same transfer if total_size=prev, percent>prev, src_IP=prev, dst_IP=prev
		 */
		private boolean isPrevTransfer(final Result r) {
			if (this.prevTransfer.size() != r.param_name.length)
				return false;

			double preVal;
			int idx;
			preVal = this.prevTransfer.getParam("total_size");
			idx = r.getIndex("total_size");
			if ((idx < 0) || isZero(preVal - r.param[idx]))
				return false;

			preVal = this.prevTransfer.getParam("src_IP");
			idx = r.getIndex("src_IP");
			if ((idx < 0) || isZero(preVal - r.param[idx]))
				return false;

			preVal = this.prevTransfer.getParam("dst_IP");
			idx = r.getIndex("dst_IP");
			if ((idx < 0) || isZero(preVal - r.param[idx]))
				return false;

			preVal = this.prevTransfer.getParam("percent");
			idx = r.getIndex("percent");
			if ((idx < 0) || (preVal > r.param[idx]))
				return false;

			return true;
		}

		/**
		 * update current parameters with the ones in the received Result or eResult
		 *
		 * @param o
		 */
		public void updateData(final Object o) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "JobInfo[" + this.jobID + "@" + this.host + "] updateData called with " + o);
			if (o instanceof Result) {
				final Result r = (Result) o;

				if (r.ClusterName.equals("Job_XRD_Transfers"))
					handleXrdTransfer(r);
				else
					// it's a result from a job
					for (int i = r.param_name.length - 1; i >= 0; i--) {
						final String name = r.param_name[i];

						if (name.equals("status")) {
							this.status = (int) r.param[i];
							// we want to consider the job as being alive only while
							// it reports its status (form JobAgent)
							this.stats.setParam(JobUtil.jobStatusToText(this.status), this.status);
							this.lastUpdateTime = NTPDate.currentTimeMillis();
						}
						else if (name.equals("cpu_time") || name.equals("cpu_ksi2k") || name.equals("cpu_hep06") || name.equals("run_time") || name.equals("run_ksi2k") || name.equals("run_hep06"))
							// append _R because will be reported as rates
							this.crtValues.setParam(name + "_R", r.param[i]);
						else
							// disk_usage workdir_size disk_total disk_free disk_used
							// virtualmem mem_usage rss cpu_usage
							this.values.setParam(name, r.param[i]);
					}
			}
			else if (o instanceof eResult) {
				final eResult er = (eResult) o;
				if (er.ClusterName.equals("ROOT_AliEnJob_Info"))
					handleRootJobInfo(er);
				else
					for (int i = er.param_name.length - 1; i >= 0; i--) {
						final String name = er.param_name[i];

						if (name.equals("host"))
							this.host = (String) er.param[i];
						else if (name.equals("job_user"))
							this.jobUser = (String) er.param[i];
					}
			}
		}

		// it's a xrootd transfer; check if it's the same as the one for the previous result
		// if this result refers to the same transfer as the previous result,
		// add only the difference to the crtValues array
		private void handleXrdTransfer(final Result r) {
			final boolean prevT = isPrevTransfer(r);
			double transf_mbytes = 0;
			double transf_files = 0;

			if (!prevT) {
				this.prevTransfer.clear();
				transf_files = 1;
			}

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String name = r.param_name[i];

				final double deltaMB = convertBtoMB(r.param[i] - this.prevTransfer.getParam(name));

				if (name.equals("moved_bytes")) {
					this.crtValues.addToParam("transf_moved_mbytes_R", deltaMB);
					transf_mbytes = deltaMB;
					if (!prevT)
						this.crtValues.addToParam("transf_moved_files_R", 1);
				}
				else if (name.equals("read_bytes")) {
					this.crtValues.addToParam("transf_read_mbytes_R", deltaMB);
					if (!prevT)
						this.crtValues.addToParam("transf_read_files_R", 1);
				}
				else if (name.equals("written_bytes")) {
					this.crtValues.addToParam("transf_written_mbytes_R", deltaMB);
					if (!prevT)
						this.crtValues.addToParam("transf_written_files_R", 1);
				}
				else if (name.equals("speed"))
					this.values.setParam("transf_speed", r.param[i]);
				// else ignore it, i.e. we don't care about:
				// elapsed_time, src_IP, dst_IP
			}

			this.prevTransfer.setAsResult(r);

			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "XRD_crtValues: " + this.crtValues);

			// identify source and dest IPs and forward the data
			// to the corresponding SITE and SE nodes
			final int i_src = r.getIndex("src_IP");
			final int i_dst = r.getIndex("dst_IP");

			if ((i_src != -1) && (i_dst != -1))
				addJobExternalXrdData(r.param[i_src], r.param[i_dst], transf_mbytes, transf_files);
		}

		/**
		 * Handle data about a root job - we are interested in the number of bytes read and their source. We account only bytes read from remote sources.
		 */
		private void handleRootJobInfo(final eResult er) {
			String srcHost = null;
			String fileId = null;
			double readBytes = 0;

			for (int i = er.param_name.length - 1; i >= 0; i--) {
				final String paramName = er.param_name[i];
				final String paramValue = (String) er.param[i];

				if (paramName.equals("destname"))
					// data are read from this SE
					// this can be empty if data is read locally (assumed)
					srcHost = paramValue;
				else if (paramName.equals("fileid_str"))
					// this number is always in (not strict) but ascending order
					// if this is not true anymore, all the following assumptions will fail!
					fileId = paramValue;
				else if (paramName.equals("readbytes_str"))
					try {
						readBytes = Double.parseDouble(paramValue);
					}
					catch (final NumberFormatException nfe) {
						logger.log(Level.WARNING, "Got invalid readbytes_str in: " + er, nfe);
						// don't add this to monitoring
						return;
					}
			}
			// skip bad results and don't account locally read data
			if ((srcHost == null) || (srcHost.length() == 0) || (fileId == null) || (fileId.length() == 0))
				return;

			// check if this result is part of a previous transfer
			final String key = srcHost + fileId;
			double readFiles = 0;
			double readMBytes;

			if (!this.prevRootTransf.containsKey(key)) {
				// job is reading a new file; account all the the readBytes
				readFiles++;
				readMBytes = convertBtoMB(readBytes);
			}
			else
				// job reading the same file; account only the difference
				readMBytes = convertBtoMB(readBytes - this.prevRootTransf.getParam(key));

			this.prevRootTransf.setParam(key, readBytes);

			// addROOTJobXrdData(srcHost, convertBtoMB(readBytes), readFiles);
			addROOTJobXrdData(srcHost, readMBytes, readFiles);
		}

		/**
		 * add summary data about this job; returns false if this job should be removed from the hashes
		 *
		 * @param sum
		 * @param min
		 * @param max
		 * @param jobsInState
		 * @param htUserSums
		 * @return boolean
		 */
		public boolean summarize(final DataArray sum, final DataArray min, final DataArray max, final DataArray jobsInState, final Hashtable<String, DataArray> htUserSums) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing job " + this.jobID + "@" + this.host);

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE) {
				// this job has expired
				if ((this.status > 0) && (this.status != JobUtil.JS_SAVED) && (this.status != JobUtil.JS_SAVED_WARNING)) { // saved is the last state for a job on WN
					// the job isn't in error state, and not saved; consider it zombie
					jobsInState.addToParam("ZOMBIE_jobs", 1);
					jobsInState.addToParam("ZOMBIE_" + JobUtil.jobStatusToText(this.status) + "_jobs", 1);
					// if it's in zombie state for more than ZOMBIE_EXPIRE time, then remove it
					if ((now - this.lastUpdateTime) > ZOMBIE_EXPIRE) {
						// add it to the FAILED_jobs fist
						jobsInState.addToParam("FAILED_jobs", 1);
						jobsInState.addToParam("FAILED_jobs_R", 1.0d / timeInterval);
						// and delete this job info from the hashes
						logger.log(Level.INFO, "ZOMBIE_EXPIRED-job: " + dumpJobStatus());
						return false;
					}

					return true;
				}

				// return false to signal the caller to delete it from the hashes
				logger.log(Level.INFO, "FINISHED-job: " + dumpJobStatus());
				return false;
			}
			// report rates
			this.crtValues.subDataArrayTo(this.lastValues, this.rates);
			this.lastValues.setAsDataArray(this.crtValues);
			// convert read_bytes, written_bytes and moved_bytes in MB/s
			final Iterator<String> itParameters = this.rates.parameterSet().iterator();

			while (itParameters.hasNext()) {
				final String name = itParameters.next();

				double value = this.rates.getParam(name);

				if (value < 0) {
					value = 0; // TODO: maybe there's a better solution than ignoring overflows
					this.rates.setParam(name, value);
				}

				// also send this as differences, not only as rates
				this.values.setParam(StringFactory.get(name.substring(0, name.length() - 2)), value);
			}

			this.rates.divParams(timeInterval);
			this.rates.addToDataArray(sum);
			this.rates.minToDataArray(min);
			this.rates.maxToDataArray(max);
			// absolute values
			this.values.addToDataArray(sum);
			this.values.minToDataArray(min);
			this.values.maxToDataArray(max);

			if (this.values.getParam("transf_speed") != 0)
				this.values.setParam("transf_speed", 0); // we have to reset the speed after reading it once

			// summaries for each user
			if (this.jobUser != null) {
				DataArray daUserSum = htUserSums.get(this.jobUser);
				if (daUserSum == null) {
					daUserSum = new DataArray(this.rates.size() + this.values.size());
					htUserSums.put(this.jobUser, daUserSum);
				}
				this.rates.addToDataArray(daUserSum);
				this.values.addToDataArray(daUserSum);
			}
			// and jobs
			if (this.status != JobUtil.JS_UNKNOWN) {
				final String statusText = JobUtil.jobStatusToText(this.status);

				if (this.stats.size() > this.prevStats.size()) {
					// add to summary the new stats
					boolean addedInErr = false;
					boolean addedInActive = false;

					final Iterator<String> itMissing = this.stats.diffParameters(this.prevStats).iterator();

					while (itMissing.hasNext()) {
						final String statName = itMissing.next();

						final int statValue = (int) this.stats.getParam(statName);

						jobsInState.addToParam(statName + "_jobs", 1);
						NodeInfo ni = null;
						if ((statValue == JobUtil.JS_SAVED) || (statValue == JobUtil.JS_SAVED_WARNING) || (statValue < 0)) {
							// we also have to add the rate
							ni = findNodeInfo(null, this.host, false);
							if (ni != null) {
								ni.jobStats.addToParam(statName + "_jobs", 1);
								ni.jobStats.addToParam(statName + "_jobs_R", 1.0d / timeInterval);
							}
							jobsInState.addToParam(statName + "_jobs_R", 1.0d / timeInterval);
						}
						// if it's an error state, add, but only once, to the generic error state (ERR_jobs)
						if ((statValue < 0) && !addedInErr && !JobUtil.isIgnoredError(statValue)) {
							if (ni != null) {
								ni.jobStats.addToParam("ERR_jobs", 1);
								ni.jobStats.addToParam("ERR_jobs_R", 1.0d / timeInterval);
							}
							jobsInState.addToParam("ERR_jobs", 1);
							jobsInState.addToParam("ERR_jobs_R", 1.0d / timeInterval);
							addedInErr = true;
						}
						// if job is active, count it, but only once, in case job goes fast through states
						if (!(addedInActive || (statValue == JobUtil.JS_SAVED) || (statValue == JobUtil.JS_SAVED_WARNING) || (statValue < 0))) {
							jobsInState.addToParam("active_jobs", 1);
							addedInActive = true;
						}
					}
					this.prevStats.setAsDataArray(this.stats);
				}
				else // we are in the same state as before
				if (!((this.status == JobUtil.JS_SAVED) || (this.status == JobUtil.JS_SAVED_WARNING) || (this.status < 0))) {
					// we don't want to report these cumulative parameters several times
					jobsInState.addToParam(statusText + "_jobs", 1);
					// count the active jobs (STARTED, RUNNING, SAVING)
					jobsInState.addToParam("active_jobs", 1);
				}
			}
			return true;
		}

		/**
		 * Return a string-description of the job's status & resource usage.
		 *
		 * @return String
		 */
		public String dumpJobStatus() {
			final StringBuilder sb = new StringBuilder();
			sb.append("JobID=" + this.jobID);
			sb.append(", status=" + JobUtil.jobStatusToText(this.status));
			sb.append(", host=" + this.host);
			sb.append(", cpu_time=" + (this.crtValues.getParam("cpu_time_R") / 3600) + " hours");
			sb.append(", cpu_ksi2k=" + (this.crtValues.getParam("cpu_ksi2k_R") / 3600) + " hours");
			sb.append(", cpu_hep06=" + (this.crtValues.getParam("cpu_hep06_R") / 3600) + " hours");
			sb.append(", run_time=" + (this.crtValues.getParam("run_time_R") / 3600) + " hours");
			sb.append(", run_ksi2k=" + (this.crtValues.getParam("run_ksi2k_R") / 3600) + " hours");
			sb.append(", run_hep06=" + (this.crtValues.getParam("run_hep06_R") / 3600) + " hours");
			return sb.toString();
		}

		// /** add to stdev the difSq of my values+rates and med */
		// public void difSqSum(DataArray stdev, DataArray med){
		// values.sqDifToDataArray(med, stdev);
		// rates.sqDifToDataArray(med, stdev);
		// }
	}

	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		final AliEnFilter filter = new AliEnFilter("CentralTest");

		try (DBFunctions db = getQueueDB()) {
			db.setReadOnly(true);
			db.setQueryTimeout(60);

			for (String id : args) {
				db.query("SELECT queueId, statusId, userId, submitHostId, execHostId, received, started, finished, siteId FROM QUEUE WHERE queueId=?;", false, Long.valueOf(id));

				while (db.moveNext()) {
					JobStatusCS js = filter.jsStatusfromDB(db, "ALICE");
					Result r = new Result();
					js.setExecHost("NO_SITE", r, 0);
					js.setStatus(-12);

					System.err.println(js.jobID + " : " + js.execSite);
				}
			}
		}
	}

	JobStatusCS jsStatusfromDB(final DBFunctions db, final String orgName) {
		final String queueId = db.gets(1);
		final int statusId = db.geti(2);
		final String userName = getUserName(db.geti(3));

		final int submitHostId = db.geti(4);
		final String submitHostName = submitHostId > 0 ? getHost(submitHostId) : "NO_SITE";

		final int execHostId = db.geti(5);
		final String execHostName = execHostId > 0 ? getHost(execHostId) : "NO_SITE";

		final long received = db.getl(6) * 1000;
		final long started = db.getl(7) * 1000;
		final long finished = db.getl(8) * 1000;

		final int siteId = db.geti(9);

		return new JobStatusCS(queueId, orgName, statusId, userName + "@" + submitHostName, execHostName, received, started, finished, siteId);
	}

	/** Class used to store job status. It is used only in the AliEn Central Services ML. */
	private final class JobStatusCS {
		/**
		 * unique job ID
		 */
		final String jobID;

		/**
		 * name of the organisation
		 */
		final String orgName;

		/**
		 * alien-user@host - from which host the job was submitter, and by which alien user
		 */
		String submitHost;

		/**
		 * site from where the job is submitted (a ML-name from LDAP)
		 */
		String submitSite;

		/**
		 * alien user that submitted this job
		 */
		String submitUser;

		/**
		 * user@host - the Computing Element on that site, running in that account
		 */
		String execHost;

		int siteId;

		/**
		 * site where the job is executed (a ML-name from LDAP)
		 */
		String execSite;

		/**
		 * time when entering the corresponding status (see timeWatchedCSJobStatuses)
		 */
		long[] statusTime;

		/**
		 * this transition time delta was reported already (see timeWatchedCSJobTransitions)
		 */
		boolean[] reportedTransitionsTime;

		// long submitTime; // when the job was submitted
		// long assinedTime; // when the job was assigned
		// long runTime; // when it started running on a worker node
		// long saveTime; // when it started saving
		// long doneTime; // when it finished
		// long errTime; // when it went into an error state (but NOT ZOMBIE)

		/**
		 * after the last sync, the job was not found in AliEn.
		 */
		boolean notInAliEn;

		/**
		 * current status of the job
		 */
		int status;
		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * if job was created after running the sync with alien script, don't report cumulative changed statuses the first time summarize runs
		 */
		boolean firstSummarizeAfterSync;

		/**
		 * record stats through which the job has been
		 */
		final DataArray stats = new DataArray();

		/**
		 * remember previously reported stats for job stats reported as deltas and rates
		 */
		final DataArray prevStats = new DataArray();

		/**
		 * @param sJobID
		 * @param sOrgName
		 */
		public JobStatusCS(final String sJobID, final String sOrgName) {
			this.jobID = sJobID;
			this.orgName = sOrgName;
			this.execSite = "CentralServices";
			this.lastUpdateTime = NTPDate.currentTimeMillis();
			this.statusTime = new long[timeWatchedCSJobStatuses.length];
			this.reportedTransitionsTime = new boolean[timeWatchedCSJobTransitions.length];
		}

		/**
		 * Allows initialization of all fields. In this case of JobStatusCS construction, cumulative parameters aren't summarized at the first summarize()'s run to avoid double-reporting them.
		 *
		 * @param sJobID
		 * @param sOrgName
		 * @param iStatus
		 * @param sSubmitHost
		 * @param sExecHost
		 * @param lReceiveTime
		 * @param lStartTime
		 * @param lFinishTime
		 * @param siteId
		 */
		public JobStatusCS(final String sJobID, final String sOrgName, final int iStatus, final String sSubmitHost, final String sExecHost, final long lReceiveTime, final long lStartTime,
				final long lFinishTime, final int siteId) {
			this(sJobID, sOrgName);
			setStatus(iStatus);
			setSubmitHost(sSubmitHost);
			setExecHost(sExecHost, null, siteId);
			setStatusTime(JobUtil.JS_INSERTING, lReceiveTime);
			setStatusTime(JobUtil.JS_RUNNING, lStartTime);
			setStatusTime(JobUtil.JS_DONE, lFinishTime);
			this.firstSummarizeAfterSync = true;
		}

		/**
		 * If the case (we want to keep time for this status, and not already set), do it
		 *
		 * @param iStatus
		 * @param time
		 */
		public void setStatusTime(final int iStatus, final long time) {
			final int idx = JobUtil.getStatusTimeIdx(iStatus);

			if (idx >= 0) {
				this.statusTime[idx] = time;

				// clear all subsequent times, in case the job was resubmitted
				for (int i = idx + 1; i < this.statusTime.length; i++)
					this.statusTime[i] = 0;
			}
		}

		/**
		 * Get the time when entering the given status, or 0 if that time is not stored, or job has not reached that status yet.
		 *
		 * @param iStatus
		 * @return long
		 */
		public long getStatusTime(final int iStatus) {
			final int idx = JobUtil.getStatusTimeIdx(iStatus);

			return idx >= 0 ? this.statusTime[idx] : 0;
		}

		/**
		 * update the status of this job - also sets the submit/start/finish/Time if needed
		 *
		 * @param iStatus
		 */
		public void setStatus(final int iStatus) {
			final boolean changedStatus = (this.status != iStatus);

			this.status = iStatus;

			this.stats.setParam(JobUtil.jobStatusToText(iStatus), iStatus);
			this.lastUpdateTime = NTPDate.currentTimeMillis();
			this.notInAliEn = false;

			if (changedStatus)
				setStatusTime(iStatus, this.lastUpdateTime);
		}

		/**
		 * The received execHost is of the form user@host where user is the account where ClusterMonitor is running on that site, and the host is the machine there. This also tries to determine the
		 * exec site.
		 *
		 * The oRes is either a Result or a eResult and is only for debug purposes.
		 *
		 * @param sExecHost
		 * @param oRes
		 * @param siteId
		 */
		public void setExecHost(final String sExecHost, final Object oRes, final int siteId) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();
			this.notInAliEn = false;

			if (sExecHost.equals(this.execHost))
				// nothing to do, we got twice the same value, skip host matching part
				return;

			this.execHost = sExecHost;

			if (sExecHost.equals("NO_SITE")) {
				if (siteId > 0) {
					this.execSite = siteCache.get(Integer.valueOf(siteId));
					this.siteId = siteId;
				}
				else
					this.execSite = getSite(jobID);

				if (this.execSite == null)
					this.execSite = "CentralServices";
			}
			else {
				String nExecSite = null;

				int iIdx = sExecHost.indexOf('@');

				String host = sExecHost;

				if (iIdx >= 0)
					host = sExecHost.substring(iIdx + 1);

				iIdx = host.indexOf("::");

				if (iIdx >= 0) {
					final int iIdx2 = host.indexOf("::", iIdx + 2);

					if (iIdx2 > 0)
						nExecSite = host.substring(iIdx + 2, iIdx2);
				}
				else
					nExecSite = getNetMatch(AliEnFilter.this.htSitesCEhosts, AliEnFilter.this.sitesCEhostsLock, host);

				if (nExecSite == null)
					logger.log(Level.WARNING,
							"Received a bad execHost=" + sExecHost + ", host=" + host + ". Keeping previous execSite=" + this.execSite + "\norig. rez=" + oRes + "\nCEhosts = "
									+ AliEnFilter.this.htSitesCEhosts);
				else
					this.execSite = nExecSite;
			}
		}

		/**
		 * Sets the submit host and alien user for this job. It also tries to determine the submit site.
		 *
		 * @param sSubmitHost
		 */
		public void setSubmitHost(final String sSubmitHost) {
			this.submitHost = sSubmitHost;

			final int iIdx = sSubmitHost.indexOf('@');

			if ((iIdx < 0) || (iIdx == (sSubmitHost.length() - 1))) {
				logger.log(Level.WARNING, "Received a bad submitHost=" + sSubmitHost);

				this.submitUser = this.submitHost = null;
			}

			final int iFinalIdx = sSubmitHost.lastIndexOf('@');

			if (iFinalIdx != iIdx) {
				logger.log(Level.WARNING, "Received a bad submitHost=" + sSubmitHost);

				this.submitUser = this.submitHost = null;
			}

			this.submitUser = StringFactory.get(sSubmitHost.substring(0, iIdx));
			this.submitSite = getNetMatch(AliEnFilter.this.htSitesNetMatchers, AliEnFilter.this.sitesNetMatchersLock, sSubmitHost.substring(iIdx + 1));
		}

		/** Select the data array to write the summary for the current state */
		private DataArray selectDA(final DataArray csJobs, final DataArray siteJobs) {
			if (siteJobs == null)
				return csJobs;

			if (this.execSite.equals("CentralServices")) {
				final String temp = getSite(jobID);

				if (temp != null)
					this.execSite = temp;
			}

			if (this.execSite.equals("CentralServices"))
				return csJobs;

			return siteJobs;
		}

		/**
		 * Add summary data about this job. Returns false if this job should be removed from the hashes.
		 *
		 * @param htJobsInOrg
		 * @param jobsInCS
		 * @param htUsersJobs
		 * @param htOrgSitesStatusTimeDeltas
		 * @param htUsersStatusTimeDeltas
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htJobsInOrg, final DataArray jobsInCS, final Hashtable<String, DataArray> htUsersJobs,
				final Hashtable<String, DataArray> htOrgSitesStatusTimeDeltas, final Hashtable<String, DataArray> htUsersStatusTimeDeltas) {

			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing jobStatusCS " + this.jobID + "@" + this.orgName + " submitSite=" + this.submitSite + " execSite=" + this.execSite + " status=" + this.status);

			DataArray jobsInSite = null;
			DataArray timeDeltasInSite = null; // can be null if job is still in central services

			if (!this.execSite.equals("CentralServices")) {
				// jobs in site
				jobsInSite = htJobsInOrg.get(this.execSite);
				if (jobsInSite == null) {
					jobsInSite = new DataArray(AliEnFilter.this.knownJobStatusSite);
					htJobsInOrg.put(this.execSite, jobsInSite);
				}
				// job status transitions, in each site
				timeDeltasInSite = htOrgSitesStatusTimeDeltas.get(this.execSite);
				if (timeDeltasInSite == null) {
					timeDeltasInSite = new DataArray();
					htOrgSitesStatusTimeDeltas.put(this.execSite, timeDeltasInSite);
				}
			}

			DataArray timeDeltasSitesTotal = htOrgSitesStatusTimeDeltas.get("_TOTALS_");
			if (timeDeltasSitesTotal == null) {
				// for status transitions of jobs not yet assigned to a site
				// this also includes jobs already assigned to a site
				timeDeltasSitesTotal = new DataArray();
				htOrgSitesStatusTimeDeltas.put("_TOTALS_", timeDeltasSitesTotal);
			}

			if (this.submitUser == null)
				this.submitUser = getUserName(this.jobID);

			final String user = (this.submitUser == null ? "unknown" : this.submitUser);
			DataArray ujda = htUsersJobs.get(user);
			if (ujda == null) {
				// jobs for this user
				ujda = new DataArray(AliEnFilter.this.knownUserJobsParams);
				htUsersJobs.put(user, ujda);
			}

			DataArray timeDeltasForUser = htUsersStatusTimeDeltas.get(user);
			if (timeDeltasForUser == null) {
				// jobs status transition, for this user
				timeDeltasForUser = new DataArray();
				htUsersStatusTimeDeltas.put(user, timeDeltasForUser);
			}
			DataArray timeDeltasUsersTotal = htUsersStatusTimeDeltas.get("_TOTALS_");
			if (timeDeltasUsersTotal == null) {
				// jobs status transition, for all users
				timeDeltasUsersTotal = new DataArray();
				htUsersStatusTimeDeltas.put("_TOTALS_", timeDeltasUsersTotal);
			}

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE) {
				final long runTime = getStatusTime(JobUtil.JS_RUNNING);

				if (JobUtil.isFinalStatus(this.status))
					// job is finished - either done or in error state. we can remove it from the hashes
					return false;

				if (((this.lastUpdateTime != 0) && ((now - this.lastUpdateTime) > JOB_STATUS_CS_EXPIRE)) || ((runTime != 0) && ((now - runTime) > JOB_STATUS_CS_EXPIRE)) || this.notInAliEn) {
					// the job was lost for ~ one week ...
					// add it to the LOST_jobs fist
					final DataArray da = selectDA(jobsInCS, jobsInSite);
					da.addToParam("LOST_jobs", 1);
					da.addToParam("LOST_jobs_R", 1.0d / timeInterval);

					ujda.addToParam("LOST_jobs", 1);
					ujda.addToParam("LOST_jobs_R", 1.0d / timeInterval);

					logger.log(Level.WARNING,
							"LOST JOB: jobID=" + this.jobID + "@" + this.orgName + " status=" + JobUtil.jobStatusToText(this.status) + " submitHost=" + this.submitHost + " execHost=" + this.execHost
									+ " lastUpdateTime=" + new Date(this.lastUpdateTime) + " submitTime=" + new Date(getStatusTime(JobUtil.JS_INSERTING)) + " startTime=" + new Date(runTime)
									+ " finishTime=" + new Date(getStatusTime(JobUtil.JS_DONE)) + " notInAliEn=" + this.notInAliEn);
					// and delete this job info from the hashes
					return false;
				}
				// else we still wait for some signs from this job; and do the statistics
			}

			if (this.status != JobUtil.JS_UNKNOWN) {
				final String statusText = JobUtil.jobStatusToText(this.status);
				if (this.stats.size() > this.prevStats.size()) {
					// add to summary the new stats
					boolean addedInErr = this.prevStats.hasNegatives();

					final Iterator<String> itMissing = this.stats.diffParameters(this.prevStats).iterator();

					while (itMissing.hasNext()) {
						final String statName = itMissing.next();
						final int statValue = (int) this.stats.getParam(statName);

						final DataArray da = selectDA(jobsInCS, jobsInSite);
						final String statNameJobs = statName + "_jobs";
						final String statNameJobsR = statNameJobs + "_R";

						da.addToParam(statNameJobs, 1);
						ujda.addToParam(statNameJobs, 1);

						if ((JobUtil.isCummulativeStatus(statValue) || (statValue < 0)) && (!this.firstSummarizeAfterSync)) {
							// we also have to add the rate
							da.addToParam(statNameJobsR, 1.0d / timeInterval);
							ujda.addToParam(statNameJobsR, 1.0d / timeInterval);
						}
						// if it's an error state, add, but only once, to the generic error state (ERR_jobs)
						if ((statValue < 0) && !JobUtil.isIgnoredError(statValue) && !addedInErr) {
							da.addToParam("ERR_jobs", 1);
							ujda.addToParam("ERR_jobs", 1);

							if (!this.firstSummarizeAfterSync) {
								// we also have to add the rates
								da.addToParam("ERR_jobs_R", 1.0d / timeInterval);
								ujda.addToParam("ERR_jobs_R", 1.0d / timeInterval);
							}
							addedInErr = true;
						}
					}
					this.prevStats.setAsDataArray(this.stats);
					// new status added, check if we can report any time delta as well
					for (int i = this.reportedTransitionsTime.length - 1; i >= 0; i--)
						if (!this.reportedTransitionsTime[i]) {
							final int firstStatus = timeWatchedCSJobTransitions[i][0];
							final int secondStatus = timeWatchedCSJobTransitions[i][1];

							final long firstTime = this.statusTime[JobUtil.getStatusTimeIdx(firstStatus)];
							final long secondTime = this.statusTime[JobUtil.getStatusTimeIdx(secondStatus)];

							if ((firstTime > 0) && (secondTime > 0)) {
								final String firstName = JobUtil.jobStatusToText(firstStatus);
								final String secondName = JobUtil.jobStatusToText(secondStatus);

								if (secondTime >= firstTime) {
									final String paramName = firstName + "-" + secondName;
									final double deltaSeconds = (secondTime - firstTime) / 1000.0;

									final String paramNameAVG = paramName + "_avg";
									final String paramNameCNT = paramName + "_cnt";

									// put the sites statistics
									if (timeDeltasInSite != null) {
										timeDeltasInSite.addToParam(paramNameAVG, deltaSeconds);
										timeDeltasInSite.addToParam(paramNameCNT, 1);
									}

									timeDeltasSitesTotal.addToParam(paramNameAVG, deltaSeconds);
									timeDeltasSitesTotal.addToParam(paramNameCNT, 1);

									// put the users statistics
									// this cannot be null - user would be "unknown"
									timeDeltasForUser.addToParam(paramNameAVG, deltaSeconds);
									timeDeltasForUser.addToParam(paramNameCNT, 1);
									timeDeltasUsersTotal.addToParam(paramNameAVG, deltaSeconds);
									timeDeltasUsersTotal.addToParam(paramNameCNT, 1);
								}
								else
									logger.warning(
											"Invalid times for job " + this.jobID + " status transitions: " + firstName + "=" + new Date(firstTime) + " " + secondName + "=" + new Date(secondTime));

								this.reportedTransitionsTime[i] = true;
							}
						}
					// produce also an active_jobs number
					if (JobUtil.isActiveJob(this.status)) {
						final DataArray da = selectDA(jobsInCS, jobsInSite);
						da.addToParam("active_jobs", 1);
						ujda.addToParam("active_jobs", 1);
					}
					if (JobUtil.isFinalStatus(this.status))
						jobsInCS.addToParam("TOTAL_jobs", 1);
				}
				else // we are in the same state as before
				if (!JobUtil.isFinalStatus(this.status)) {
					// we don't want to report these final statuses several times
					final DataArray da = selectDA(jobsInCS, jobsInSite);
					final String statusTextJobs = statusText + "_jobs";

					da.addToParam(statusTextJobs, 1);
					ujda.addToParam(statusTextJobs, 1);

					jobsInCS.addToParam("TOTAL_jobs", 1);

					if (JobUtil.isActiveJob(this.status)) {
						da.addToParam("active_jobs", 1);
						ujda.addToParam("active_jobs", 1);
					}

					// count the error jobs
					if ((this.status < 0) && !JobUtil.isIgnoredError(this.status)) {
						da.addToParam("ERR_jobs", 1);
						ujda.addToParam("ERR_jobs", 1);
					}
				}
			}

			this.firstSummarizeAfterSync = false;
			return true; // keep the job
		}
	}

	/**
	 * Class used to store FTD transfer status. It is used only in the AliEn Central Services ML. It aggregates data from AliEn TransferManager.
	 */
	private final static class TransferStatusCS {
		/**
		 * id of this transfer
		 */
		final String transferID;

		/**
		 * the organisation name to which this transfer belongs
		 */
		final String orgName;

		/**
		 * alien user who initiated the transfer
		 */
		String user = "unknown";

		/**
		 * source SE
		 */
		String srcSE = "NO_SE";

		/**
		 * destination SE
		 */
		String dstSE = "NO_SE";

		/**
		 * current status of the transfer
		 */
		int status;

		/**
		 * size of this transfer
		 */
		long size;

		/**
		 * record of the stats through the transfer has been
		 */
		final DataArray stats = new DataArray();

		/**
		 * previous stats of the job (from last summary)
		 */
		final DataArray prevStats = new DataArray();

		/**
		 * when this transfer was received by FTD
		 */
		long receiveTime;

		/**
		 * when the transfer has effectively started
		 */
		long startTime;

		/**
		 * and when it finished
		 */
		long finishTime;

		/**
		 * after the last sync with AliEn, this transfer was not found anymore.
		 */
		boolean notInAliEn;

		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * @param sTransferID
		 * @param sOrgName
		 */
		public TransferStatusCS(final String sTransferID, final String sOrgName) {
			this.transferID = sTransferID;
			this.orgName = sOrgName;
			this.lastUpdateTime = NTPDate.currentTimeMillis();
		}

		/**
		 * allows initialization of all fields
		 *
		 * @param sTransferID
		 * @param sOrgName
		 * @param iStatus
		 * @param lSize
		 * @param sUser
		 * @param sSrcSE
		 * @param sDstSE
		 * @param lReceiveTime
		 * @param lStartTime
		 * @param lFinishTime
		 */
		public TransferStatusCS(final String sTransferID, final String sOrgName, final int iStatus, final long lSize, final String sUser, final String sSrcSE, final String sDstSE,
				final long lReceiveTime, final long lStartTime, final long lFinishTime) {

			this(sTransferID, sOrgName);

			this.user = sUser;

			setSrcSE(sSrcSE);
			setDstSE(sDstSE);

			this.status = iStatus;
			this.size = lSize;

			this.receiveTime = lReceiveTime;
			this.startTime = lStartTime;
			this.finishTime = lFinishTime;
		}

		private void setSrcSE(final String sSE) {
			this.srcSE = sSE.toUpperCase();

			if (this.srcSE.startsWith("ALICE::") || this.srcSE.startsWith("PANDA::"))
				this.srcSE = this.srcSE.substring(7); // strip ALICE:: from the beginning

			this.srcSE = StringFactory.get(this.srcSE);
		}

		private void setDstSE(final String sSE) {
			this.dstSE = sSE.toUpperCase();

			if (this.dstSE.startsWith("ALICE::") || this.dstSE.startsWith("PANDA::"))
				this.dstSE = this.dstSE.substring(7); // strip ALICE:: from the beginning

			this.dstSE = StringFactory.get(this.dstSE);
		}

		/**
		 * update the parameters with what is in the given result
		 *
		 * @param o
		 */
		public void updateData(final Object o) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();
			this.notInAliEn = false;
			if (o instanceof Result) {
				final Result r = (Result) o;

				for (int i = r.param_name.length - 1; i >= 0; i--) {
					final String name = r.param_name[i];

					if (name.equals("statusID")) {
						this.status = (int) r.param[i];
						this.stats.setParam(TransferUtil.transferStatusToText(this.status), this.status);
					}
					else if (name.equals("size"))
						this.size = (long) r.param[i];
					else if (name.equals("received"))
						this.receiveTime = 1000 * (long) r.param[i];
					else if (name.equals("started"))
						this.startTime = 1000 * (long) r.param[i];
					else if (name.equals("finished"))
						this.finishTime = 1000 * (long) r.param[i];
					else
						logger.log(Level.WARNING, "Received unknown transfer parameter `" + name + "` in R=" + r);
				}
			}
			else if (o instanceof eResult) {
				final eResult er = (eResult) o;

				for (int i = er.param_name.length - 1; i >= 0; i--) {
					final String name = er.param_name[i];

					if (name.equals("user"))
						this.user = (String) er.param[i];
					else if (name.equals("SE"))
						setSrcSE((String) er.param[i]);
					else if (name.equals("destination"))
						setDstSE((String) er.param[i]);
					else if (name.equals("Protocol")) {
						// TODO: aggregate per protocol
					}
					else
						logger.log(Level.WARNING, "Received unknown transfer parameter `" + name + "` in eR=" + er);
				}
			}
		}

		/**
		 * merge the parameters of the given 'nTS' TransferStatusCS with the ones from the current TransferStatusCS. This is called when the ML synchronizes its view about the current active transfers
		 * by querying the AliEn Central Services. The returned list contains all active transfers. If ML receives data about a transfer before this synchronization, the status will be updated only if
		 * it's older than the one from AliEn.
		 *
		 * @param nTS
		 */
		public void mergeParams(final TransferStatusCS nTS) {
			if (nTS.lastUpdateTime > this.lastUpdateTime) {
				this.status = nTS.status;
				this.stats.setParam(TransferUtil.transferStatusToText(this.status), this.status);
				this.lastUpdateTime = nTS.lastUpdateTime;
			}

			if (this.size == 0)
				this.size = nTS.size;

			if (this.user.equals("unknown"))
				this.user = nTS.user;

			if (this.srcSE.equals("NO_SE"))
				setSrcSE(nTS.srcSE);

			if (this.dstSE.equals("NO_SE"))
				setDstSE(nTS.dstSE);

			if (this.receiveTime == 0)
				this.receiveTime = nTS.receiveTime;

			if (this.startTime == 0)
				this.startTime = nTS.startTime;

			if (this.finishTime == 0)
				this.finishTime = nTS.finishTime;
		}

		/**
		 * add my data to the summaries regarding: - the number of transfers in each state - rates for completed transfers since last report it returns false if this transfer has reached a final point
		 * and should be deleted form the hashes
		 *
		 * @param transfStats
		 * @param transfSEs
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> transfStats, final Hashtable<String, DataArray> transfSEs) {
			String tsKey = "GLOBAL";
			if (!this.dstSE.equals("NO_SE"))
				tsKey = this.dstSE;
			// TODO: uncomment this when srcSE will be also consistently reported by the getTransfers.pl script
			// if((! srcSE.equals("NO_SE")) && (! dstSE.equals("NO_SE")))
			// tsKey = srcSE+"-"+dstSE;

			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing transferStatusCS " + this.transferID + "@" + this.orgName + " srcSE=" + this.srcSE + " dstSE=" + this.dstSE + " state=" + this.status + " ("
						+ TransferUtil.transferStatusToText(this.status) + ")");

			DataArray sda = transfStats.get(tsKey);
			if (sda == null) {
				sda = new DataArray();
				transfStats.put(tsKey, sda);
			}

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE) {
				if (TransferUtil.isCummulativeStatus(this.status))
					// transfer is finished - done or failed or killed. We can forget about it
					return false;

				if (((this.lastUpdateTime != 0) && ((now - this.lastUpdateTime) > TRANSFER_STATUS_CS_EXPIRE)) || ((this.startTime != 0) && ((now - this.startTime) > TRANSFER_STATUS_CS_EXPIRE))
						|| this.notInAliEn) {
					// the transfer was lost for ~ one week...
					// add it to the LOST_transfers first
					sda.addToParam("LOST_transfers", 1);
					sda.addToParam("LOST_transfers_R", 1.0d / timeInterval);
					// and delete this transfer from the hashses
					logger.log(Level.WARNING,
							"LOST TRANSFER: transferID=" + this.transferID + "@" + this.orgName + " status=" + TransferUtil.transferStatusToText(this.status) + " srcSE=" + this.srcSE + " dstSE="
									+ this.dstSE + " size=" + this.size + " user=" + this.user + " lastUpdateTime=" + new Date(this.lastUpdateTime) + " receiveTime=" + new Date(this.receiveTime)
									+ " startTime=" + new Date(this.startTime) + " finishTime=" + new Date(this.finishTime) + " notInAliEn=" + this.notInAliEn);
					return false;
				}
			}

			if (this.status != TransferUtil.TS_UNKNOWN) {
				final String statusText = TransferUtil.transferStatusToText(this.status);

				DataArray tda = transfSEs.get(tsKey);
				if (tda == null) {
					tda = new DataArray();
					transfSEs.put(tsKey, tda);
				}

				if (this.stats.size() > this.prevStats.size()) {
					// consider the new states

					final Iterator<String> itMissing = this.stats.diffParameters(this.prevStats).iterator();

					while (itMissing.hasNext()) {
						final String statName = itMissing.next();
						final int statValue = (int) this.stats.getParam(statName);

						sda.addToParam(statName + "_transfers", 1);

						if (TransferUtil.isCummulativeStatus(statValue))
							sda.addToParam(statName + "_transfers_R", 1.0d / timeInterval);
					}

					this.prevStats.setAsDataArray(this.stats);
					// if the new state is DONE, also report the speed and rate
					if (this.status == TransferUtil.TS_DONE) {
						final double sizeMB = convertBtoMB(this.size);
						final double transfTime = (this.finishTime - this.startTime) / 1000.0;

						tda.addToParam("transf_mbytes", sizeMB);
						tda.addToParam("transf_mbytes_R", sizeMB / timeInterval);

						if (transfTime > 0)
							tda.addToParam("transf_speed_mbs", sizeMB / transfTime);
					}
				}
				else {
					// we are in the same state as before
					tda.addToParam("transf_mbytes", 0);
					tda.addToParam("transf_mbytes_R", 0);
					tda.addToParam("transf_speed_mbs", 0);

					if (!TransferUtil.isCummulativeStatus(this.status))
						// we dont' report these cumultative params several times
						sda.addToParam(statusText + "_transfers", 1);
				}
			}
			return true;
		}
	}

	/**
	 * Class used to store data about ROOT jobs. Currently it suppports the transfers between jobs.
	 */
	private final class RootJobInfo {
		/**
		 *
		 */
		final String sFarmName;

		/**
		 * this should be the AliEn JobID...
		 */
		final String jobID;

		/**
		 * this is an ID produced by PROOF (?)
		 */
		final String subJobID;

		/**
		 * where this job is running
		 */
		String hostName = "unknown";

		/** job's data transfers */
		final DataArray transf = new DataArray();

		/**
		 * previous run transfers
		 */
		final DataArray prevTransf = new DataArray();

		/** job's parameters */
		final DataArray jobParams = new DataArray();
		/**
		 * parameters at the previous run
		 */
		final DataArray prevJobParams = new DataArray();

		/** job's status */
		String status;

		/**
		 * status history
		 */
		final DataArray stats = new DataArray();

		/**
		 * history at the previous run
		 */
		final DataArray prevStats = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * @param sFarmNameParam
		 * @param sJobID
		 * @param sSubJobID
		 */
		public RootJobInfo(final String sFarmNameParam, final String sJobID, final String sSubJobID) {
			this.sFarmName = sFarmNameParam;
			this.jobID = sJobID;
			this.subJobID = sSubJobID;
		}

		/**
		 * add a result with information about this job
		 *
		 * @param er
		 */
		public void addResult(final eResult er) {
			// for file transfers
			String fileID = null;
			String destName = null;
			double readBytes = 0;

			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = er.param.length - 1; i >= 0; i--) {
				final String sParam = er.param_name[i];
				final String sValue = (String) er.param[i];

				if (sParam.equals("fileid_str"))
					fileID = sValue;
				else if (sParam.equals("hostname"))
					this.hostName = sValue;
				else if (sParam.equals("destname"))
					destName = sValue;
				else if (sParam.equals("status")) {
					this.status = sValue;
					this.stats.setParam(this.status, 1);
				}
				else if (sParam.equals("readbytes_str"))
					try {
						readBytes = Double.parseDouble(sValue);
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring readbytes_str = " + sValue, ex);
					}
				else if (sParam.equals("processedbytes_str"))
					try {
						this.jobParams.setParam("processedbytes", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring processedbytes_str = " + sValue, ex);
					}
				else if (er.param_name[i].equals("events_str"))
					try {
						this.jobParams.setParam("events", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring events_str = " + sValue, ex);
					}
				// }else if(er.param_name[i].equals("realtime_str")){
				// try{
				// jobParams.setParam("realtime", Double.parseDouble((String) er.param[i]));
				// }catch(Exception ex){
				// logger.log(Level.WARNING, "Ignoring realtime_str = "+er.param[i], ex);
				// }
				else if (sParam.equals("cputime_str"))
					try {
						this.jobParams.setParam("cputime", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring cputime_str = " + sValue, ex);
					}
				else if (sParam.equals("shdmem_str"))
					try {
						this.jobParams.setParam("shdmem", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring shdmem_str = " + sValue, ex);
					}
				else if (sParam.equals("rssmem_str"))
					try {
						this.jobParams.setParam("rssmem", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring rssmem_str = " + sValue, ex);
					}
				else if (sParam.equals("totmem_str"))
					try {
						this.jobParams.setParam("totmem", Double.parseDouble(sValue));
					}
					catch (final Exception ex) {
						logger.log(Level.WARNING, "Ignoring totmem_str = " + sValue, ex);
					}
			}

			if ((destName == null) || (destName.length() == 0))
				destName = this.hostName; // if destName is empty, it's a local transfer

			if ((fileID != null) && (destName != null)) {
				// we have a file transfer
				final String key = fileID + "|" + destName;
				this.transf.setParam(key, readBytes);
				// at Jan's request, I'll keep the very first value for a file transfer as reference
				// The reason is that ROOT keeps the files open and the offset is not reset
				// for each subjob.
				if (!this.prevTransf.containsKey(key))
					this.prevTransf.setParam(key, readBytes);
				// addRezFromDA(vDebugResults, "ROOT_TransferDebug_"+jobID, subJobID, transf);
			}
		}

		/**
		 * add summary data about this ROOT job; returns false if this job should be removed from the hashes
		 *
		 * @param hostStats
		 * @param destStats
		 * @param destToHostsStats
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> hostStats, final Hashtable<String, DataArray> destStats, final Hashtable<String, DataArray> destToHostsStats) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing job " + this.jobID + "/" + this.subJobID + "@" + this.sFarmName);

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this job has expired - we can remove it from the hashes
				return false;

			summarizeTransfers(hostStats, destStats, destToHostsStats, timeInterval);
			summarizeJobParams(hostStats, timeInterval);
			return true;
		}

		/**
		 * based on the new data in transf compared to prevTransf, add the moved bytes to all statistics: host, dest, dest2host
		 */
		private void summarizeTransfers(final Hashtable<String, DataArray> hostStats, final Hashtable<String, DataArray> destStats, final Hashtable<String, DataArray> destToHostsStats,
				final double timeInterval) {
			final Iterator<String> it = this.transf.parameterSet().iterator();

			while (it.hasNext()) {
				final String key = it.next();

				final int iIdx = key.indexOf('|');

				if (iIdx < 0) {
					logger.log(Level.WARNING, "Invalid key for job stransfer to split: " + key);
					continue;
				}

				final int iLastIdx = key.lastIndexOf('|');

				if ((iIdx != iLastIdx) || (iIdx == (key.length() - 1))) {
					logger.log(Level.WARNING, "Invalid key for job stransfer to split: " + key);
					continue;
				}

				double movedMBytes = this.transf.getParam(key) - this.prevTransf.getParam(key);

				if (movedMBytes < 0)
					movedMBytes = 0;

				movedMBytes /= 1024 * 1024;

				final String transfDest = StringFactory.get(key.substring(iIdx + 1));
				final String transfDH = StringFactory.get(transfDest + "|" + this.hostName);

				// update host-based statistics
				updateTransfStats(hostStats, this.hostName, transfDest, movedMBytes, timeInterval, AliEnFilter.this.knownRootJobsHostParams);
				// update the dest-based statistics
				updateTransfStats(destStats, transfDest, this.hostName, movedMBytes, timeInterval, AliEnFilter.this.knownRootJobsDestParams);
				// update the dest-to-host statistics
				updateTransfStats(destToHostsStats, transfDH, null, movedMBytes, timeInterval, AliEnFilter.this.knownRootJobsDestToHostParams);
			}

			this.prevTransf.setAsDataArray(this.transf);
		}

		/**
		 * update a DataArray in hash->{key} by adding to the given param the given value. If timeInterval is != 0, it also adds a rate for that, for that time interval
		 */
		private void updateTransfStats(final Hashtable<String, DataArray> hash, final String key, final String other, final double movedMBytes, final double timeInterval, final DataArray daTemplate) {
			DataArray sda = hash.get(key);
			if (sda == null) {
				sda = new DataArray(daTemplate);
				hash.put(key, sda);
			}

			if (timeInterval != 0) {
				sda.addToParam("read_mbytes_R", movedMBytes / timeInterval);
				sda.addToParam("read_files_R", (movedMBytes > 0 ? 1.0 / timeInterval : 0));

				if (other != null) {
					final String loc_ext = (key.equals(other) ? "local" : "external");
					sda.addToParam(loc_ext + "_read_mbytes_R", movedMBytes / timeInterval);
					sda.addToParam(loc_ext + "_read_files_R", (movedMBytes > 0 ? 1.0 / timeInterval : 0));
				}
			}
		}

		/**
		 * based on the new params and stats that were accumulated from previous report update the statistics for the host where this job runs (events, cputime, runtime, job states)
		 */
		private void summarizeJobParams(final Hashtable<String, DataArray> hostStats, final double timeInterval) {
			DataArray sda = hostStats.get(this.hostName);
			if (sda == null) {
				sda = new DataArray(AliEnFilter.this.knownRootJobsHostParams);
				hostStats.put(this.hostName, sda);
			}

			final Iterator<String> it = this.jobParams.parameterSet().iterator();

			while (it.hasNext()) {
				final String par = it.next();
				final double crtVal = this.jobParams.getParam(par);

				double delta = crtVal;
				final double prevValue = this.prevJobParams.getParam(par);
				if (prevValue <= crtVal)
					delta -= prevValue;

				if (par.equals("events") || par.equals("cputime") || par.equals("processedbytes")) {
					// sum-up cummulative parameters
					sda.addToParam(par, crtVal);
					sda.addToParam(par + "_R", delta / timeInterval);
				}
				else if (par.equals("shdmem") || par.equals("rssmem") || par.equals("totmem"))
					// sum-up running parameters
					sda.addToParam(par, crtVal);
			}
			this.prevJobParams.setAsDataArray(this.jobParams);

			// compute derivate values

			// the cpu_usage for the last period, i.e. since last report
			sda.setParam("cpu_usage", (sda.getParam("cputime_R") * 100) / CPUsPerNode);
			// number of jobs running on this host
			sda.addToParam("jobs_count", 1);

			// now add the new (from previous report) stats
			final Iterator<String> itMissing = this.stats.diffParameters(this.prevStats).iterator();

			while (itMissing.hasNext()) {
				final String s = itMissing.next();

				sda.addToParam(s + "_jobs", 1);

				if (s.equals("DONE"))
					sda.addToParam(s + "_jobs_R", 1.0 / timeInterval);
			}

			this.prevStats.setAsDataArray(this.stats);
			// finally, add the current status, if not cummulative
			if ((this.status != null) && (!this.status.equals("DONE")))
				sda.addToParam(this.status + "_jobs", 1);
		}
	}

	/** Class used to store information about a node */
	private final class NodeInfo {
		/**
		 *
		 */
		final String site;
		/**
		 *
		 */
		final String host;

		/**
		 * absolute values
		 */
		final DataArray values = new DataArray();

		/**
		 * for rates, last set of values
		 */
		final DataArray lastValues = new DataArray();

		/**
		 * for rates, current set of values
		 */
		final DataArray crtValues = new DataArray();

		/**
		 * rates=(crtValues-lastValues)/deltaT; lastValues = crtValues
		 */
		final DataArray rates = new DataArray();

		/**
		 * a few of the job stats - the cummulative ones (saved and all errors) note that jobStats is updated by the summarize in JobInfo and it's reset in the NodeInfo's addJobRez
		 */
		final DataArray jobStats = new DataArray();

		/**
		 * previously reported jobStats
		 */
		final DataArray prevJobStats = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * @param sSite
		 * @param sHost
		 */
		public NodeInfo(final String sSite, final String sHost) {
			this.site = sSite;
			this.host = sHost;
			this.values.setParam("count", 1); // this is to count how many NodeInfo instances give a certain value.
		}

		/**
		 * update current parameters with the ones in the received Result or eResult
		 *
		 * @param o
		 */
		public void updateData(final Object o) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "NodeInfo[" + this.host + "@" + this.site + "] updateData called with " + o);
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			if (o instanceof Result) {
				final Result r = (Result) o;

				for (int i = r.param_name.length - 1; i >= 0; i--) {
					final String name = r.param_name[i];

					if (name.startsWith("eth") && name.endsWith("_errs"))
						this.crtValues.setParam(name + "_R", r.param[i]);
					else
						// total_swap swap_in swap_out swap_used swap_free swap_usage
						// total_mem pages_in pages_out mem_usage mem_free mem_used
						// cpu_idle cpu_usr cpu_nice cpu_sys cpu_usage load1 load5 load15
						// processes no_CPUs cpu_MHz uptime
						// ethX_[in,out]
						this.values.setParam(name, r.param[i]);
				}
			}
			// we don't care for eResults, now
			// that would be 'ethX_ip' and 'hostname' parameters.
		}

		/**
		 * add summary data about this node; returns false if this node should be removed from hashes
		 *
		 * @param sum
		 * @param min
		 * @param max
		 * @return boolean
		 */
		public boolean summarize(final DataArray sum, final DataArray min, final DataArray max) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing node " + this.host + "@" + this.site);
			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;
			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this node has expired
				return false;

			this.crtValues.subDataArrayTo(this.lastValues, this.rates);
			this.lastValues.setAsDataArray(this.crtValues);
			this.rates.divParams(timeInterval);

			// make rates from the raw counters (ethX_errs)
			final Iterator<String> itRates = this.rates.parameterSet().iterator();

			while (itRates.hasNext()) {
				final String name = itRates.next();
				double value = this.rates.getParam(name);

				if (value < 0) {
					value = 0; // TODO: maybe there's a better solution than ignoring overflows
					this.rates.setParam(name, value);
				}

				// also send this as differences, not only as rates
				this.values.setParam(StringFactory.get(name.substring(0, name.length() - 2)), value);
			}

			this.rates.addToDataArray(sum);
			this.rates.minToDataArray(min);
			this.rates.maxToDataArray(max);

			this.values.addToDataArray(sum);
			this.values.minToDataArray(min);
			this.values.maxToDataArray(max);

			return true;
		}

		// /** add to stdev the difSq of my values+rates and med */
		// public void difSqSum(DataArray stdev, DataArray med){
		// values.sqDifToDataArray(med, stdev);
		// rates.sqDifToDataArray(med, stdev);
		// }

		/**
		 * Add to the given vector information about the cummulative stats of the jobs currently running on this node. This method sends a 0 status before and after any activity to ease the
		 * integration of these values.
		 *
		 * @param vrez
		 */
		public void addJobRez(final List<Serializable> vrez) {
			if ((this.jobStats.size() == 0) && (this.prevJobStats.size() == 0))
				return;

			if ((this.jobStats.size() != 0) && (this.prevJobStats.size() == 0)) {
				// we have new activity
				this.prevJobStats.setAsDataArray(this.jobStats);
				this.prevJobStats.setToZero();
				// send a zero first for the previous period, to be easyer to integrate the values
				final Result r = addRezFromDA(vrez, this.site + "_Nodes", this.host, this.prevJobStats);
				r.time -= AF_SLEEP_TIME;
			}

			if ((this.jobStats.size() != 0) && (this.prevJobStats.size() != 0)) {
				// add the missing fileds to the jobStats with zero
				// we do this because we have to zero a previously non-zero value
				// that wasn't updated anymore on this summarize operation

				final Iterator<String> it = this.prevJobStats.diffParameters(this.jobStats).iterator();

				while (it.hasNext()) {
					final String name = it.next();
					this.jobStats.setParam(name, 0);
				}
			}

			// send the current activity
			addRezFromDA(vrez, this.site + "_Nodes", this.host, this.jobStats);

			if ((this.jobStats.size() == 0) && (this.prevJobStats.size() != 0))
				// end of the activity - last addResFromDA didn't produce any Results
				addRezFromDA(vrez, this.site + "_Nodes", this.host, this.prevJobStats);
			// prevJobStats is <- jobStats except it's filled with zeros
			this.prevJobStats.setAsDataArray(this.jobStats);
			this.prevJobStats.setToZero();
			// prepare jobStats for accumulating other data
			this.jobStats.clear();
		}
	}

	/**
	 * Class used to store information about the RFCP transfers from a single castorgrid server to castor2 using 'rfcp'. The purpose is to do an aggregation of the raw data (each transfer) and provide
	 * summaries with transfer size, average speed and total errors.
	 */
	private static final class RfcpInfo {
		/**
		 * hostname of the castorgrid server
		 */
		final String name;

		/**
		 * when some info about this was last updated
		 */
		long lastUpdateTime;

		/**
		 * is this node allowed to expire?
		 */
		final boolean canExpire;

		/**
		 * values that have to be added
		 */
		final DataArray sumValues = new DataArray();

		/**
		 * values for which summarize will compute maximum
		 */
		final DataArray maxValues = new DataArray();

		/**
		 * @param sName
		 * @param bCanExpire
		 */
		public RfcpInfo(final String sName, final boolean bCanExpire) {
			this.name = sName;
			this.canExpire = bCanExpire;
		}

		/**
		 * Add another traffic data about this castorgrid host. The information can be of 4 types: "staging", "migrating", "gc" and "fsfill".
		 *
		 * @param da
		 * @param type
		 */
		public void addData(final DataArray da, final String type) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "RfcpInfo[" + this.name + "] addData called with type=" + type + " " + da);

			if (type.equals("fsfill"))
				da.maxToDataArray(this.maxValues);
			else if (type.equals("staging") || type.equals("migrating")) {
				da.addToDataArray(this.sumValues);
				this.sumValues.addToParam(type + "_count", 1); // we'll need how many of each type we have
			}
			else if (type.equals("gc")) {
				final Iterator<String> it = da.parameterSet().iterator();
				while (it.hasNext()) {
					final String paramName = it.next();

					final double value = da.getParam(paramName);

					if (paramName.equals("gc_cache_usage"))
						this.maxValues.setParam(paramName, value);
					else
						this.sumValues.setParam(paramName, value);
				}
			}

			this.lastUpdateTime = NTPDate.currentTimeMillis();
		}

		/**
		 * Summarize data for this node (for last 2 minutes): - adds the total sum and rates for migrating and staging transfers - adds the average speed of the transfers - adds the total number of
		 * errors - adds the values from the garbage collector - sets the maximum value for fsFill (between my and existing (if any)) value - sets the max value for gc cache usage returns false if
		 * this node should be removed from hashes
		 *
		 * @param sumSummary
		 * @param maxSummary
		 * @return boolean
		 */
		public boolean summarize(final DataArray sumSummary, final DataArray maxSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing traffic for " + this.name);
			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if (this.canExpire && ((now - this.lastUpdateTime) > PARAM_EXPIRE))
				// this node has expired
				return false;

			final int stagingCount = (int) this.sumValues.getParam("staging_count");
			final int migratingCount = (int) this.sumValues.getParam("migrating_count");

			Iterator<String> it = this.sumValues.parameterSet().iterator();
			while (it.hasNext()) {
				final String paramName = it.next();
				final double paramValue = this.sumValues.getParam(paramName);

				if (paramName.endsWith("_mbytes"))
					this.sumValues.setParam(paramName + "_R", paramValue / timeInterval);
				else if (paramName.endsWith("_speed") && (paramValue > 0))
					this.sumValues.setParam(paramName, paramValue / (paramName.startsWith("staging") ? stagingCount : migratingCount));
			}

			this.sumValues.addToDataArray(sumSummary);
			this.maxValues.maxToDataArray(maxSummary);
			// maxValues will remain unmodified
			// but the sumValues will be reset except for the gc_ values
			it = this.sumValues.parameterSet().iterator();
			while (it.hasNext()) {
				final String paramName = it.next();

				if (!paramName.startsWith("gc_"))
					this.sumValues.setParam(paramName, 0);
			}
			return true;
		}

	}

	/** Class used to store information about a SITE and SE traffic */
	private static final class XrdInfo {
		/**
		 * can be: SITE1-SITE2, Incomming_SITE, Outgoing_SITE, Internal_SITE, SE::Name
		 */
		final String name;

		/**
		 *
		 */
		long lastUpdateTime;

		/**
		 * is this node allowed to expire?
		 */
		final boolean canExpire;

		/**
		 * last reported values for transfers
		 */
		final DataArray lastValues = new DataArray();

		/**
		 * current values for transfers
		 */
		final DataArray crtValues = new DataArray();

		/**
		 * non rates
		 */
		final DataArray values = new DataArray();

		/**
		 * @param sName
		 * @param bCanExpire
		 */
		public XrdInfo(final String sName, final boolean bCanExpire) {
			this.name = sName;
			this.canExpire = bCanExpire;
		}

		/**
		 * add another traffic data about this node
		 *
		 * @param da
		 */
		public void addData(final DataArray da) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "XrdSiteInfo[" + this.name + "] addData called with " + da);

			da.addToDataArray(this.crtValues);
			this.lastUpdateTime = NTPDate.currentTimeMillis();
		}

		// /** add another traffic data about this node */
		// public void setData(DataArray da){
		// if(logger.isLoggable(Level.FINEST)){
		// logger.log(Level.FINEST, "XrdSiteInfo["+name+"] setData called with "+da);
		// }
		// da.addToDataArray(values); // add or set?
		// lastUpdateTime = NTPDate.currentTimeMillis();
		// }

		/**
		 * set the summary data about this node; returns false if this node should be removed from hashes
		 *
		 * @param summary
		 * @return boolean
		 */
		public boolean summarize(final DataArray summary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing traffic for " + this.name);

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if (this.canExpire && ((now - this.lastUpdateTime) > PARAM_EXPIRE))
				// this node has expired
				return false;

			summary.clear();
			this.crtValues.subDataArrayTo(this.lastValues, summary);
			this.lastValues.setAsDataArray(this.crtValues);
			summary.divParams(timeInterval);
			this.values.addToDataArray(summary);
			return true;
		}
	}

	/**
	 * Class used to summarize the parameters of API services on site. It generates a sum for each parameter for all the services. Computed data is published in node _TOTALS_, same cluster
	 */
	private static final class APIServiceInfo {
		/**
		 * farm name
		 */
		final String farm;

		/**
		 * hostname:port of the api service
		 */
		final String name;

		/**
		 *
		 */
		final DataArray crtValues = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime = 0;

		/**
		 * @param sFarm
		 * @param sName
		 */
		public APIServiceInfo(final String sFarm, final String sName) {
			this.farm = sFarm;
			this.name = sName;
		}

		@Override
		public String toString() {
			// use the unused field
			return this.farm;
		}

		/**
		 * add another traffic data about this node
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "APIServiceInfo[" + this.name + "] setData called with " + r);

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String pName = r.param_name[i];
				final double pValue = r.param[i];

				if (pName.endsWith("_R"))
					this.crtValues.addToParam(pName, pValue);
				else
					this.crtValues.setParam(pName, pValue);
			}

			this.lastUpdateTime = NTPDate.currentTimeMillis();
		}

		/**
		 * add to the sum this api service's parameters. Returns false if
		 *
		 * @param sum
		 * @return boolean
		 */
		public boolean summarize(final DataArray sum) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing APIService info for " + this.name);

			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this node has expired
				return false;

			// add my contribution
			this.crtValues.addToDataArray(sum);

			// and reset the rates
			final Iterator<String> it = this.crtValues.parameterSet().iterator();
			while (it.hasNext()) {
				final String paramName = it.next();

				if (paramName.endsWith("_R"))
					this.crtValues.setParam(paramName, 0);
			}

			return true;
		}
	}

	/** Helper class that holds data about a Job Agent */
	private static final class JobAgentInfo {
		/**
		 * farm name
		 */
		final String farm;

		/**
		 * hostname:port of the JobAgent service
		 */
		final String name;

		/**
		 * current status of the JobAgent
		 */
		int status;

		/**
		 * job agent ID (ce's PID _DOT_ ce's counter)
		 */
		String jaID;

		/**
		 * the job ID of the job picked by this agent
		 */
		String jobID;

		/**
		 * time to live for this job agent (or -1 if
		 */
		int ttl;

		/**
		 *
		 */
		final DataArray stats = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime = 0;

		/**
		 * @param sFarm
		 * @param sName
		 */
		public JobAgentInfo(final String sFarm, final String sName) {
			this.farm = sFarm;
			this.name = sName;
			this.ttl = -1;
		}

		/**
		 * add another traffic data about this node
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			String sjaID = null;
			String sjobID = null;
			int newStat = 0;

			this.lastUpdateTime = NTPDate.currentTimeMillis();
			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String pName = r.param_name[i];
				final double pValue = r.param[i];

				if (pName.equals("ja_status"))
					newStat = (int) pValue;
				else if (pName.equals("ja_id_maj"))
					sjaID = "" + (int) pValue + (sjaID == null ? "" : sjaID);
				else if (pName.equals("ja_id_min"))
					sjaID = (sjaID == null ? "" : sjaID) + "." + (int) pValue;
				else if (pName.equals("job_id"))
					sjobID = String.valueOf((long) pValue);
				else if (pName.equals("TTL"))
					this.ttl = (int) pValue;
			}
			if (sjaID != null)
				this.jaID = sjaID;

			if (sjobID != null)
				this.jobID = sjobID;

			if (newStat != 0) {
				final boolean differs = newStat != this.status;
				this.status = newStat;
				final String statName = JobAgentUtil.jaStatusToText(this.status);
				final double timeInterval = AF_SLEEP_TIME / 1000.0d;

				if (JobAgentUtil.isCummulativeStatus(this.status)) {
					if (differs) {
						this.stats.addToParam(statName + "_ja", 1);
						this.stats.addToParam(statName + "_ja_R", 1 / timeInterval);
					}
				}
				else
					this.stats.setParam(statName + "_ja", 1);
			}
		}

		@Override
		public String toString() {
			// something to make use of the unused fields in this class :)
			return this.farm + " - " + this.jaID + " - " + this.jobID;
		}

		/**
		 * Add data about this JobAgent to the given summary. Returns false if this JA should be removed from hashes.
		 *
		 * @param sum
		 * @param ttlSum
		 * @return boolean
		 */
		public boolean summarize(final DataArray sum, final DataArray ttlSum) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing JobAgent info for " + this.name);

			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this node has expired
				return false;
			this.stats.addToDataArray(sum);
			this.stats.setToZero();

			if (this.ttl != -1) {
				ttlSum.addToParam("ttl", this.ttl);
				ttlSum.addToParam("ttl_count", 1);
				this.ttl = -1;
			}

			return true;
		}
	}

	/**
	 * Helper class to hold information about CEs. Currently this is used to aggregate the number of slots/running/queued jobAgents, as the AliEn CE knows.
	 */
	private static final class CEInfo {

		/**
		 * farm name
		 */
		final String farm;

		/**
		 * hostname:port of the CE service
		 */
		final String name;

		/**
		 * jobAgents_(running|queued|slots)
		 */
		final DataArray statsJA = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime = 0;

		/**
		 * @param sFarm
		 * @param sName
		 */
		public CEInfo(final String sFarm, final String sName) {
			this.farm = sFarm;
			this.name = sName;
		}

		@Override
		public String toString() {
			// something to use the unused variable
			return this.farm;
		}

		/**
		 * Add more information for this CE
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String pName = r.param_name[i];
				final double pValue = r.param[i];

				if (pName.startsWith("jobAgents_"))
					this.statsJA.setParam(pName, pValue);
			}
		}

		/**
		 * Add data about this CE to the given summary. Returns false if this CE should be removed from hashes.
		 *
		 * @param sumJA
		 * @return boolean
		 */
		public boolean summarize(final DataArray sumJA) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing CE info for " + this.name);

			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this CE has expired
				return false;

			this.statsJA.addToDataArray(sumJA);
			this.statsJA.clear();
			return true;
		}
	}

	/**
	 * Helper class to hold information about DAQs. Currently this is used to aggregate the transferred amount (size and files).
	 */
	private static final class DAQInfo {

		/**
		 * farm name
		 */
		final String farm;

		/**
		 * hostname of the DAQ service
		 */
		final String name;

		/**
		 * size_R, count_R
		 */
		final DataArray rates = new DataArray(2);

		/**
		 * delay_avg
		 */
		final DataArray avgs = new DataArray(1);

		/**
		 *
		 */
		long lastUpdateTime = 0;

		/**
		 * @param sFarm
		 * @param sName
		 */
		public DAQInfo(final String sFarm, final String sName) {
			this.farm = sFarm;
			this.name = sName;
			this.rates.setParam("size_R", 0);
			this.rates.setParam("count_R", 0);
			this.avgs.setParam("delay_avg", 0);
		}

		@Override
		public String toString() {
			// use the unused variable
			return this.farm;
		}

		/**
		 * Add more information for this DAQ
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "I am " + this.farm + " / " + this.name + " and I have received a DAQ update:\n    " + r);

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String pName = r.param_name[i];
				final double pValue = r.param[i];

				if (pName.equals("size")) {
					this.rates.addToParam("size_R", pValue / (1024.0 * 1024.0));
					this.rates.addToParam("count_R", 1.0);

					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "DAQ Value " + pValue + " added to the rates, which are now:\n   " + this.rates);
				}
				else if (pName.equals("delay"))
					this.avgs.addToParam("delay_avg", pValue);
			}
		}

		/**
		 * Summarize data about this DAQ Returns false if this DAQ should be removed from hashes.
		 *
		 * @param daqSummary
		 * @return boolean
		 */
		public boolean summarize(final DataArray daqSummary) {
			final boolean bDebug = logger.isLoggable(Level.FINE);

			if (bDebug)
				logger.log(Level.FINE, "Summarizing DAQ info for " + this.name);

			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE) {
				if (bDebug)
					logger.fine("Exiting quickly because the values have expired");

				// this DAQ has expired
				return false;
			}

			final double timeInterval = AF_SLEEP_TIME / 1000.0d;
			final double count = this.rates.getParam("count_R");

			if (count >= 1.5) {
				if (bDebug)
					logger.log(Level.FINE, "Dividing averages by " + count + ":\n  " + avgs);

				this.avgs.divParams(count);

				if (bDebug)
					logger.log(Level.FINE, "After dividing the averages are:\n  " + avgs);
			}

			if (bDebug)
				logger.fine("Dividing the rates by time (" + timeInterval + "):\n  " + rates);

			this.rates.divParams(timeInterval);

			if (bDebug)
				logger.fine("After dividing rates are:\n  " + rates);

			this.rates.addToDataArray(daqSummary);

			if (bDebug)
				logger.fine("After adding rates to the summary, the summary is:\n  " + daqSummary);

			this.avgs.addToDataArray(daqSummary);

			if (bDebug)
				logger.fine("After adding averages to the summary, the summary is:\n  " + daqSummary);

			this.rates.setToZero();
			this.avgs.setToZero();

			return true;
		}
	}

	/**
	 * Helper class to store the information and generate summaries about the FTS transfers.
	 */
	private static final class FTSTransferInfo {
		/**
		 * full name of the FTD that manages this transfer
		 */
		final String ftdFullName;

		/**
		 * the FTD ID of the current transfer
		 */
		final String transferID;

		/**
		 * FTS ID of the current transfer
		 */
		String ftsID;

		/**
		 * the FTS channel for this transfer
		 */
		String channel = "unknown";

		/**
		 * the current status of this transfer
		 */
		String status;

		/**
		 * last time when I received something about this transfer
		 */
		long lastUpdateTime;

		/**
		 * statuses that this transfer passed through; may have duplicates;
		 */
		final ArrayList<String> vStatus;

		/**
		 * times when transfer entered each of those statuses
		 */
		final ArrayList<Long> vTimes;

		/**
		 * @param sFtdFullName
		 * @param sTransferID
		 */
		public FTSTransferInfo(final String sFtdFullName, final String sTransferID) {
			this.ftdFullName = sFtdFullName;
			this.transferID = sTransferID;
			this.vStatus = new ArrayList<>();
			this.vTimes = new ArrayList<>();
			// this will not be reported; it's only used to have a time basis for the 'real' statuses
			this.vStatus.add("dummy_invalid_status");
			this.vTimes.add(Long.valueOf(NTPDate.currentTimeMillis()));
		}

		@Override
		public String toString() {
			// use the unused variable
			return this.transferID;
		}

		/**
		 * Set the last data from FTS
		 *
		 * @param er
		 */
		public void setData(final eResult er) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = er.param_name.length - 1; i >= 0; i--) {
				final String paramName = er.param_name[i];
				final String paramValue = (String) er.param[i];

				if ("ftsID".equals(paramName))
					this.ftsID = paramValue;
				else if ("ftsChannel".equals(paramName))
					this.channel = paramValue;
				else if ("ftsStatus".equals(paramName)) {
					this.status = paramValue;
					if (this.vStatus.get(this.vStatus.size() - 1).equals(this.status))
						this.vTimes.set(this.vTimes.size() - 1, Long.valueOf(this.lastUpdateTime));
					else {
						this.vStatus.add(this.status);
						this.vTimes.add(Long.valueOf(this.lastUpdateTime));
					}
				}
			}
		}

		/**
		 * Summarize the data about this transfer by adding all useful data to the given hash of (key=channel name; value = DataArray).
		 *
		 * @param htChannelsSummary
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htChannelsSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing FTS Transfers Info for " + this.ftsID + "@" + this.ftdFullName);

			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this FTS Transfer has expired
				return false;

			DataArray daCh = htChannelsSummary.get(this.channel);
			if (daCh == null) {
				daCh = new DataArray(1 + this.vStatus.size());
				htChannelsSummary.put(this.channel, daCh);
			}

			daCh.addToParam(this.status + "_transfers", 1);
			daCh.addToParam("_TOTALS__transfers", 1);
			for (int i = 1; i < this.vStatus.size(); i++)
				daCh.addToParam(this.vStatus.get(i) + "_avg_time", (this.vTimes.get(i).longValue() - this.vTimes.get(i - 1).longValue()) / 1000.0);
			return true;
		}
	}

	/**
	 * Helper class to store the information and generate summaries about the test FDT transfers. These transfers are always from the current ML service to a given destination. They can be summarized
	 * for the "CRT_ML-DEST_ML" or given "linkName" link. The DEST_ML is extracted from the IP address of the destination.
	 */
	private static final class FDTTestTransferInfo {
		/**
		 * destination ML Service
		 */
		final String dest;

		/**
		 * source site - resolves to the main ML of that site
		 */
		final String src;

		/**
		 * user given name for the link - used for summaries, if non-empty
		 */
		final String linkName;

		/**
		 * name of the link (either linkName or CRT_ML-DEST_ML)
		 */
		final String fdtLink;

		/**
		 * name of the link (either linkName or CRT_ML-DEST_ML)
		 */
		long lastUpdateTime;

		/**
		 * current rates received from the monFDTClient module (NET_OUT and DISK_READ)
		 */
		final DataArray rates = new DataArray();

		/**
		 * current measurement number for this transfer
		 */
		int iMeasurementID;

		/**
		 * number of samples
		 */
		int samples = 0;

		/**
		 * @param sLinkName
		 * @param destIP
		 * @param htSitesNetMatchers
		 * @param rwLock
		 * @param farmName
		 */
		public FDTTestTransferInfo(final String sLinkName, final String destIP, final Hashtable<String, NetMatcher> htSitesNetMatchers, final ReentrantReadWriteLock rwLock, final String farmName) {
			this.linkName = sLinkName;

			final String sSrc = getNetMatch(htSitesNetMatchers, rwLock, "127.0.0.1");
			final String sDest = getNetMatch(htSitesNetMatchers, rwLock, destIP);

			TransferUtil.fillKnownFDTTestTransferParams(this.rates);

			this.src = sSrc == null ? farmName : sSrc;
			this.dest = sDest == null ? destIP : sDest;

			this.fdtLink = sLinkName.length() > 0 ? sLinkName : this.src + ">" + this.dest;
		}

		/**
		 * Add the last parameters for this FDT Transfer
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String paramName = r.param_name[i];
				final double paramValue = r.param[i];

				if (paramName.equals("MeasurementID"))
					this.iMeasurementID = (int) paramValue;
				else
					this.rates.addToParam(paramName, paramValue);
			}

			this.samples++;
		}

		/**
		 * Summarize the data about the test FDT transfer on this FDTLink.
		 *
		 * @param htFDTLinksSummary
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htFDTLinksSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing FDT Test Transfer Info for fdtLink='" + this.fdtLink + "', linkName='" + this.linkName + "', dest='" + this.dest + "'");
			final long now = NTPDate.currentTimeMillis();

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this FDT Transfer has expired
				return false;

			DataArray daLink = htFDTLinksSummary.get(this.fdtLink);
			if (daLink == null) {
				daLink = new DataArray();
				htFDTLinksSummary.put(this.fdtLink, daLink);
			}

			this.rates.divParams(this.samples);
			this.rates.addToDataArray(daLink);
			this.rates.setToZero();
			this.samples = 0;

			daLink.setParam("MeasurementID", this.iMeasurementID);

			return true;
		}
	}

	/**
	 * Provide user statistics on the requests to the storage elements.
	 */
	private static final class QuotaInfo {
		/**
		 * number of samples
		 */
		final String name;

		/**
		 * when this QuotaInfo was last updated
		 */
		long lastUpdateTime;

		/**
		 * rates, used in summary
		 */
		final DataArray rates = new DataArray();

		/**
		 * @param sName
		 */
		public QuotaInfo(final String sName) {
			this.name = sName;
		}

		/**
		 * Add the last parameters for this QuotaInfo
		 *
		 * @param r
		 */
		public void setData(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String sSE = r.param_name[i].toUpperCase();

				if (sSE.indexOf("::") > 0)
					this.rates.addToParam(sSE + "_R", r.param[i]);
			}
		}

		/**
		 * Summarize the rates for this user_requestType
		 *
		 * @param htQuotaSummary
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htQuotaSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing Quota Info for user_requestType='" + this.name + "'");

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this QuotaInfo has expired
				return false;

			DataArray daUserReq = htQuotaSummary.get(this.name);
			if (daUserReq == null) {
				daUserReq = new DataArray(this.rates.size());
				htQuotaSummary.put(this.name, daUserReq);
			}

			this.rates.divParams(timeInterval);
			this.rates.addToDataArray(daUserReq);
			this.rates.setToZero();
			return true;
		}
	}

	/**
	 * Provide statistics on ROOT CAF usage, summarized per user and per group.
	 */
	private static final class RootCafUsageInfo {
		/**
		 * id of this job (node's name)
		 */
		final String jobID;

		/**
		 *
		 */
		String user = null;

		/**
		 *
		 */
		String group = null;

		/**
		 * when was last updated
		 */
		long lastUpdateTime;

		/**
		 * numeric values
		 */
		final DataArray values = new DataArray();

		/**
		 * @param sJobID
		 */
		public RootCafUsageInfo(final String sJobID) {
			this.jobID = sJobID;
		}

		@Override
		public String toString() {
			// use the unused field
			return this.jobID;
		}

		/**
		 * Add job's string values (user, group)
		 *
		 * @param er
		 */
		public void addDataStrings(final eResult er) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = er.param_name.length - 1; i >= 0; i--) {
				final String paramName = er.param_name[i];
				final String paramValue = (String) er.param[i];

				if ("user".equals(paramName))
					this.user = paramValue;
				else if ("group".equals(paramName) || "proofgroup".equals(paramName))
					this.group = paramValue;
			}
		}

		/**
		 * Add job's numeric values (cputime, walltime, events, bytesread)
		 *
		 * @param r
		 */
		public void addDataValues(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param_name.length - 1; i >= 0; i--) {
				final String paramName = r.param_name[i];
				final double paramValue = r.param[i];

				this.values.addToParam(paramName, paramValue);
			}
		}

		/**
		 * Add the current values to the user&group summaries
		 *
		 * @param htUserSummary
		 * @param htGroupSummary
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htUserSummary, final Hashtable<String, DataArray> htGroupSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing RootCafUsage Info for user='" + this.user + "', group='" + this.group + "'");

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this 'node' has expired
				return false;

			if ((this.user == null) || (this.group == null) || (this.values.size() == 0))
				return true; // didn't receive all data; wait a bit more

			final DataArray rates = new DataArray(this.values, "_R");

			rates.divParams(timeInterval);

			DataArray daUserSummary = htUserSummary.get(this.user);
			if (daUserSummary == null) {
				daUserSummary = new DataArray(this.values.size() + rates.size());
				htUserSummary.put(this.user, daUserSummary);
			}
			this.values.addToDataArray(daUserSummary);
			rates.addToDataArray(daUserSummary);

			DataArray daGroupSummary = htGroupSummary.get(this.group);
			if (daGroupSummary == null) {
				daGroupSummary = new DataArray(this.values.size() + rates.size());
				htGroupSummary.put(this.group, daGroupSummary);
			}
			this.values.addToDataArray(daGroupSummary);
			rates.addToDataArray(daGroupSummary);

			this.values.setToZero();
			return false;
		}
	}

	/**
	 * Provide summaries for XrdServers
	 */
	private static final class XrdServerInfo {
		/**
		 *
		 */
		final String host;

		/**
		 * when was last updated
		 */
		long lastUpdateTime;

		/**
		 * numeric values
		 */
		final DataArray srvStats = new DataArray();

		/**
		 * @param sHost
		 */
		public XrdServerInfo(final String sHost) {
			this.host = sHost;
		}

		/**
		 * @param r
		 */
		public void addData(final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param.length - 1; i >= 0; i--) {
				final String paramName = r.param_name[i];
				final double paramValue = r.param[i];

				if (paramName.startsWith("srv_rd_") || paramName.startsWith("srv_wr_"))
					this.srvStats.addToParam(paramName + "_R", paramValue);
			}
		}

		/**
		 * @param htXrdNodes
		 * @return boolean
		 */
		public boolean summarize(final Hashtable<String, DataArray> htXrdNodes) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing XrdServer Info for host='" + this.host + "'");

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > PARAM_EXPIRE)
				// this XrdServer has expired
				return false;

			this.srvStats.divParams(timeInterval);
			DataArray daNode = htXrdNodes.get(this.host);
			if (daNode == null) {
				daNode = new DataArray(this.srvStats.size());
				htXrdNodes.put(this.host, daNode);
			}

			this.srvStats.addToDataArray(daNode);
			this.srvStats.setToZero();

			return true;
		}
	}

	/**
	 * Helper class to hold information about AliEn SE transfers. Currently this is used to aggregate the number of transferred files and the amount of traffic performed by alien (both prompt and
	 * jobs).
	 *
	 * For now we don't care if it's traffic from jobs or from alien prompt.
	 */
	private static final class SETransferInfo {

		/**
		 * ORG::SITE::SEName
		 */
		final String name;

		/**
		 *
		 */
		final DataArray rates = new DataArray();

		/**
		 *
		 */
		final DataArray params = new DataArray();

		/**
		 *
		 */
		long lastUpdateTime = 0;

		/**
		 * @param sName
		 */
		public SETransferInfo(final String sName) {
			this.name = sName;
		}

		/**
		 * @param operation
		 * @param r
		 */
		public void addData(final String operation, final Result r) {
			this.lastUpdateTime = NTPDate.currentTimeMillis();

			for (int i = r.param.length - 1; i >= 0; i--) {
				final String paramName = r.param_name[i];
				final double paramValue = r.param[i];

				if (paramName.equals("size"))
					this.rates.addToParam(operation + "_mbytes_R", convertBtoMB(paramValue));
				else if (paramName.equals("speed"))
					this.params.addToParam(operation + "_speed_mbps", convertBtoMB(paramValue));
				else if (paramName.equals("status")) {
					final boolean success = paramValue > 0.5;
					this.params.addToParam(operation + "_successes", success ? 1 : 0);
					this.params.addToParam(operation + "_failures", success ? 0 : 1);
				}
			}
		}

		/**
		 * @param daSummary
		 * @return boolean
		 */
		public boolean summarize(final DataArray daSummary) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "Summarizing SETransfer Info for SE='" + this.name + "'");

			final long now = NTPDate.currentTimeMillis();
			final double timeInterval = AF_SLEEP_TIME / 1000.0d;

			if ((now - this.lastUpdateTime) > (2 * PARAM_EXPIRE))
				// this SE has expired
				// keep this twice the normal param expire time since if there's no activity,
				// the local SE-tests will be run once each 15 minutes, same as the default
				// param expire interval
				return false;

			this.rates.divParams(timeInterval);
			this.rates.addToDataArray(daSummary);
			this.rates.setToZero();
			this.params.addToDataArray(daSummary);
			this.params.clear();
			return true;
		}
	}

	/**
	 * Helper class that allows re-reading the configuration by running the LDAP_QUERY_SCRIPT at some time interval.
	 */
	final class ConfigLoader extends TimerTask {

		/** for getAliEnFilterConf.pl */
		boolean confFirstSuccess = false;

		/**
		 *
		 */
		boolean confWasSuccessful = false;

		/**
		 *
		 */
		long confLastRead = 0;

		/** for getJobs.pl */
		long jobsLastAttemptTime = 0;

		/**
		 *
		 */
		long jobAttemptInterval = 1 * 60 * 1000;

		/** for getTransfers.pl */
		long transfersLastAttemptTime = 0;

		/**
		 *
		 */
		long transferAttemptInterval = 1 * 60 * 1000;

		/** helper method to run a command and return a BufferedReader from its output */
		private BufferedReader procOutput(final String cmd, final long timeout) {
			Process pro = null;
			InputStream out = null;
			try {
				pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd }, timeout);
				out = pro.getInputStream();
				return new BufferedReader(new InputStreamReader(out));
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "FAILED to execute cmd = " + cmd, t);
			}
			if (pro != null)
				pro.destroy();
			if (out != null)
				try {
					out.close();
				}
				catch (final IOException ex) {
					logger.log(Level.WARNING, "FAILED to close the output stream for cmd = " + cmd, ex);
				}
			return null;
		}

		/**
		 * Synchronize ML's image with AliEn's image about the currently active jobs. This is executed until it succeeds, but no more than 3 attempts are made in a row. Then, it is repeated with a
		 * period of JOB_SYNC_RUN_INTERVAL.
		 *
		 * @return true if it was successful.
		 */
		private boolean getAliEnJobs() {
			final String orgName = AppConfig.getGlobalEnvProperty("ALIEN_ORGANISATION", "Alice").toUpperCase();

			boolean ok = true;

			try (DBFunctions db = getQueueDB()) {

				if (db != null) {
					db.setReadOnly(true);
					db.setQueryTimeout(60);

					final long lStart = System.currentTimeMillis();

					if (!db.query("SELECT queueId, statusId, userId, submitHostId, execHostId, received, started, finished, siteId FROM QUEUE WHERE statusId in (10,5,21,6,1,7,11,17,18,19,12,-15);")) {
						logger.log(Level.WARNING, "Direct DB query failed, falling back to querying AliEn");
						ok = false;
					}
					else {
						final long lProcess = System.currentTimeMillis();

						logger.log(Level.INFO, "Active jobs query returned in " + (lProcess - lStart) + " ms");

						final ArrayList<JobStatusCS> crtAliEnJobs = new ArrayList<>(10240);

						while (db.moveNext()) {
							crtAliEnJobs.add(jsStatusfromDB(db, orgName));
						}

						final long lEnd = System.currentTimeMillis();

						logger.log(Level.INFO, "Loaded status for " + crtAliEnJobs.size() + " in " + (lEnd - lProcess) + " ms");
						// status of AliEn jobs loaded ok; populate hashes...
						addJobStatusCSBulk(crtAliEnJobs);

						logger.log(Level.INFO, "Updated in-memory status " + crtAliEnJobs.size() + " jobs via direct query in " + (System.currentTimeMillis() - lEnd) + " ms");
						return true;
					}
				}
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "Caught exception while loading job status from query", t);
				ok = false;
			}

			if (ok)
				return true;

			String cmd = ALIEN + " proxy-init";

			logger.log(Level.INFO, "Trying to load status for current AliEn jobs...");

			try (BufferedReader buff = procOutput(cmd, 30 * 1000)) { // this should be really fast

				if (buff == null) {
					logger.log(Level.WARNING, "Got null OutputStream from " + cmd);
					return false;
				}

				String line;

				boolean proxyOK = false;
				while ((line = buff.readLine()) != null) {
					line = line.trim();
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "proxy-init: " + line);
					if (line.indexOf("valid") >= 0) {
						proxyOK = true;
						break;
					}
				}
				if (!proxyOK) {
					logger.log(Level.WARNING, "Failed to create proxy with " + cmd);
					return false;
				}
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "FAILED to read output from " + cmd, t);
				return false;
			}

			// proxy was created ok. now sync the jobs
			cmd = ALIEN + " -x " + JOB_SYNC_SCRIPT;

			try (BufferedReader buff = procOutput(cmd, JOB_SYNC_SCRIPT_TIMEOUT)) {
				if (buff == null) {
					logger.log(Level.WARNING, "Got null OutputStream from " + cmd);
					return false;
				}

				boolean finishedOK = false;

				final ArrayList<JobStatusCS> crtAliEnJobs = new ArrayList<>(10240);

				final boolean bLog = logger.isLoggable(Level.FINEST);

				String line;

				while ((line = buff.readLine()) != null) {
					line = line.trim();

					if (bLog)
						logger.log(Level.FINEST, "getJobs.pl: " + line);

					if (line.equals("DONE")) {
						finishedOK = true;
						break;
					}

					final StringTokenizer stk = new StringTokenizer(line, "\t");

					// job data
					String jobID = null, execHost = null, submitHost = null;
					int status = 0;
					long receiveTime = 0, startTime = 0, finishTime = 0;

					try {
						if (stk.hasMoreTokens())
							jobID = stk.nextToken();
						if (stk.hasMoreTokens())
							status = Integer.parseInt(stk.nextToken());
						if (stk.hasMoreTokens())
							submitHost = StringFactory.get(stk.nextToken());
						if (stk.hasMoreTokens())
							execHost = StringFactory.get(stk.nextToken());
						if (stk.hasMoreTokens())
							receiveTime = 1000 * Long.parseLong(stk.nextToken());
						if (stk.hasMoreTokens())
							startTime = 1000 * Long.parseLong(stk.nextToken());
						if (stk.hasMoreTokens())
							finishTime = 1000 * Long.parseLong(stk.nextToken());

						if ((jobID != null) && (status != 0) && (execHost != null) && (submitHost != null))
							crtAliEnJobs.add(new JobStatusCS(jobID, orgName, status, submitHost, execHost, receiveTime, startTime, finishTime, 0));
					}
					catch (@SuppressWarnings("unused") final Exception e) {
						continue;
					}
				}

				if (!finishedOK) {
					logger.log(Level.WARNING, "Failed to correctly read all AliEn jobs  " + cmd);
					return false;
				}

				logger.log(Level.INFO, "Loaded status for " + crtAliEnJobs.size() + " jobs.");
				// status of AliEn jobs loaded ok; populate hashes...
				addJobStatusCSBulk(crtAliEnJobs);
				return true;
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "FAILED to read output from " + cmd, t);
				return false;
			}
		}

		/**
		 * Synchronize ML's image with AliEn's image about the currently active transfers. This is executed until it succeeds, but no more than 3 attempts are made in a row. Then, it is repeated with
		 * a period of JOB_SYNC_RUN_INTERVAL.
		 *
		 * @return true if it was successful.
		 */
		private boolean getAliEnTransfers() {
			logger.log(Level.INFO, "Trying to load status for current AliEn transfers...");
			final String cmd = ALIEN + " -x " + TRANSFER_SYNC_SCRIPT;
			final BufferedReader buff = procOutput(cmd, JOB_SYNC_SCRIPT_TIMEOUT);
			if (buff == null) {
				logger.log(Level.WARNING, "Got null OutputStream from " + cmd);
				return false;
			}
			try {
				boolean finishedOK = false;
				final String orgName = StringFactory.get(AppConfig.getGlobalEnvProperty("ALIEN_ORGANISATION", "ALICE").toUpperCase());
				final ArrayList<TransferStatusCS> crtAliEnTransfers = new ArrayList<>(1024);
				String line;
				while ((line = buff.readLine()) != null) {
					line = line.trim();

					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "getTransfers.pl: " + line);

					if (line.equals("DONE")) {
						finishedOK = true;
						break;
					}

					final StringTokenizer stk = new StringTokenizer(line, "\t");
					// the line looks like this:
					// $transferId $status $size $user $srcSE $dstSE $received $started $finished

					// transfer data
					String transferID = null, srcSE = null, dstSE = null, user = null;
					int status = 0;
					long receiveTime = 0, startTime = 0, finishTime = 0, size = 0;

					try {
						if (stk.hasMoreTokens())
							transferID = stk.nextToken();
						if (stk.hasMoreTokens())
							status = Integer.parseInt(stk.nextToken());
						if (stk.hasMoreTokens())
							size = Long.parseLong(stk.nextToken());
						if (stk.hasMoreTokens())
							user = StringFactory.get(stk.nextToken());
						if (stk.hasMoreTokens())
							srcSE = StringFactory.get(stk.nextToken());
						if (stk.hasMoreTokens())
							dstSE = StringFactory.get(stk.nextToken());
						if (stk.hasMoreTokens())
							receiveTime = 1000 * Long.parseLong(stk.nextToken());
						if (stk.hasMoreTokens())
							startTime = 1000 * Long.parseLong(stk.nextToken());
						if (stk.hasMoreTokens())
							finishTime = 1000 * Long.parseLong(stk.nextToken());
						if ((transferID != null) && (status != 0))
							crtAliEnTransfers.add(new TransferStatusCS(transferID, orgName, status, size, user, srcSE, dstSE, receiveTime, startTime, finishTime));
					}
					catch (@SuppressWarnings("unused") final Exception e) {
						continue;
					}
				}
				if (!finishedOK) {
					logger.log(Level.WARNING, "Failed to correctly read all AliEn transfers  " + cmd);
					return false;
				}
				logger.log(Level.INFO, "Loaded status for " + crtAliEnTransfers.size() + " transfers.");
				// status of AliEn jobs loaded ok; populate hashes...
				addTransferStatusCSBulk(crtAliEnTransfers);
				return true;
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "FAILED to read output from " + cmd, t);
				return false;
			}
			finally {
				try {
					buff.close();
				}
				catch (@SuppressWarnings("unused") final Throwable t) {
					// ignore
				}
			}
		}

		/**
		 * Extract useful info from ALiEn LDAP: Sites domains, SE's hosts, Max nr. of jobs in each CE, Sites CEs' hosts.
		 *
		 * @return true if it was successful.
		 */
		private boolean getAliEnConf() {
			final String cmd = ALIEN_PERL + " " + LDAP_QUERY_SCRIPT;
			logger.log(Level.INFO, "Getting AliEn config with " + cmd);
			final BufferedReader buff = procOutput(cmd, 15 * 60 * 1000); // it can take a long time to resolve the DNS names
			if (buff != null) {
				String line;
				Hashtable<String, NetMatcher> crt = null;
				final Hashtable<String, NetMatcher> sites_domains = new Hashtable<>();
				final Hashtable<String, NetMatcher> ses_hosts = new Hashtable<>();
				final Hashtable<String, Integer> ces_max_jobs = new Hashtable<>();
				final Hashtable<String, NetMatcher> sites_ce_hosts = new Hashtable<>();

				boolean bJobs = false;

				try {
					while ((line = buff.readLine()) != null) {
						line = line.trim();
						if (line.length() == 0)
							continue;
						else if (line.startsWith("#"))
							continue;
						else if (line.equals("[Sites_domains]")) {
							crt = sites_domains;
							bJobs = false;
							continue;
						}
						else if (line.equals("[SEs_hosts]")) {
							bJobs = false;
							crt = ses_hosts;
							continue;
						}
						else if (line.equals("[CEs_max_jobs]")) {
							bJobs = true;
							crt = null;
							continue;
						}
						else if (line.equals("[Sites_CE_hosts]")) {
							bJobs = false;
							crt = sites_ce_hosts;
							continue;
						}
						final int eq = line.indexOf('=');
						if (eq == -1)
							continue; // invalid line

						final String key = StringFactory.get(line.substring(0, eq).trim());
						final String value = line.substring(eq + 1).trim();

						if ((crt != null) && ((crt == sites_domains) || (crt == ses_hosts) || (crt == sites_ce_hosts))) {
							final ArrayList<String> nets = new ArrayList<>();
							String redirect = null;
							if (value.startsWith(">"))
								redirect = value.substring(1);
							else
								for (final StringTokenizer stk = new StringTokenizer(value, " "); stk.hasMoreTokens();) {
									final String val = stk.nextToken();

									if (val.length() == 0)
										continue;
									// if we are in sites section, we'll have domain names as values
									// and the convention in NetMatcher is that domains start with ".".
									if (crt == sites_domains) {
										// this can be a domain name like cern.ch => we'll add ".cern.ch"
										// or can be a net/mask => we'll add it just as it is
										if (val.indexOf('/') != -1)
											nets.add(val);
										else
											nets.add("." + val);
									}
									else
										// this is for CEs, SEs...
										nets.add(val);
								}
							// we have to add local addresses as taking part of this farm:
							// 127.0.0.0/24, 192.168.0.0/16, 172.16.0.0/12, 10.0.0.0/8
							if (key.equals(AliEnFilter.this.farmName) && (crt == sites_domains)) {
								nets.add("127.0.0.0/24");
								nets.add("192.168.0.0/16");
								nets.add("172.16.0.0/12");
								nets.add("10.0.0.0/8");
							}
							if (nets.size() > 0)
								if (redirect != null) {
									final NetMatcher nm = crt.get(redirect); // this HAS to be there already
									if (nm != null)
										nm.addInetNetworks(nets);
									else
										logger.log(Level.WARNING, "Not found NetMatchers for " + AliEnFilter.this.farmName + " to redirected farm " + redirect);
								}
								else {
									logger.log(Level.FINE, "Resolving nets : " + nets);

									final NetMatcher nm = new NetMatcher(nets);
									crt.put(key, nm);
								}
						}
						else if (bJobs)
							try {
								ces_max_jobs.put(key, Integer.valueOf(value));
							}
							catch (final NumberFormatException ex) {
								logger.log(Level.WARNING, "Got invalid maxJobs number " + key + " = " + value, ex);
							}
					}
					// if we made it so far, we can use this as the current configuration
					sitesNetMatchersLock.writeLock().lock();

					try {
						AliEnFilter.this.htSitesNetMatchers.clear();
						AliEnFilter.this.htSitesNetMatchers.putAll(sites_domains);
					}
					finally {
						sitesNetMatchersLock.writeLock().unlock();
					}

					sesNetMatchers.writeLock().lock();

					try {
						// ses.put("Alice::CERN::MonaLisa", new NetMatcher(new String[] {"monalisa.cern.ch"}));
						AliEnFilter.this.htSEsNetMatchers.clear();
						AliEnFilter.this.htSEsNetMatchers.putAll(ses_hosts);
					}
					finally {
						sesNetMatchers.writeLock().unlock();
					}

					synchronized (AliEnFilter.this.htCEsMaxJobs) {
						AliEnFilter.this.htCEsMaxJobs.clear();
						AliEnFilter.this.htCEsMaxJobs.putAll(ces_max_jobs);
					}

					sitesCEhostsLock.writeLock().lock();
					try {
						AliEnFilter.this.htSitesCEhosts.clear();
						AliEnFilter.this.htSitesCEhosts.putAll(sites_ce_hosts);
					}
					finally {
						sitesCEhostsLock.writeLock().unlock();
					}

					netMatchCache.refresh();
					netMatchIPCache.refresh();

					if (!this.confFirstSuccess) {
						// print this only once, at the first successsful read
						logger.log(Level.INFO, "AliEn config from LDAP:" + "\nSites Matchers:\n" + AliEnFilter.this.htSitesNetMatchers + "\nSEs Matchers:\n" + AliEnFilter.this.htSEsNetMatchers
								+ "\nCEs MaxJobs:\n" + AliEnFilter.this.htCEsMaxJobs + "\nSites CE hosts:\n" + AliEnFilter.this.htSitesCEhosts);
						this.confFirstSuccess = true;
					}
					return true;
				}
				catch (final Throwable t) {
					logger.log(Level.WARNING, "FAILED to read output from " + LDAP_QUERY_SCRIPT, t);
				}
				finally {
					try {
						buff.close();
					}
					catch (@SuppressWarnings("unused") final Throwable t) {
						// ignore
					}
				}
			}
			else
				logger.log(Level.WARNING, "Got null OutputStream from " + cmd);

			return false;
		}

		/** check that the vobox_mon.pl script is still running */
		private void checkVoBoxMon() {
			// run this command in background. If the script is not started, it will start and
			// this command won't return
			final String cmd = ALIEN_PERL + " " + VOBOX_MON_SCRIPT + " >>" + FarmHOME + "/vobox_mon.log" + " 2>>" + FarmHOME + "/vobox_mon.log &";
			try (BufferedReader buff = procOutput(cmd, 5 * 1000)) { // a minute should be enough to read ldap
				if (buff == null)
					logger.log(Level.WARNING, "Got null OutputStream from " + cmd);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Exception executing " + cmd, e);
			}
		}

		@Override
		public void run() {
			Thread.currentThread().setName("AliEnFilter ConfigLoader");
			final long now = NTPDate.currentTimeMillis();

			// check the vobox_mon script
			checkVoBoxMon();

			// check LDAP configuration
			if ((!this.confWasSuccessful) || ((now - this.confLastRead) > LDAP_QUERY_INTERVAL)) {
				this.confLastRead = now;
				this.confWasSuccessful = getAliEnConf();
			}

			// check jobs
			if (((now - this.jobsLastAttemptTime) >= this.jobAttemptInterval) && (RUN_JOB_SYNC_SCRIPT)) {
				this.jobsLastAttemptTime = now;
				final boolean jobsWasSuccessful = getAliEnJobs();
				if (!jobsWasSuccessful) {
					if (this.jobAttemptInterval <= (60 * 60 * 1000))
						this.jobAttemptInterval *= 2;
				}
				else {
					JOB_SYNC_RUN_INTERVAL = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.JOB_SYNC_RUN_INTERVAL", JOB_SYNC_RUN_INTERVAL / 1000) * 1000;
					this.jobAttemptInterval = JOB_SYNC_RUN_INTERVAL;
				}
				logger.log(Level.INFO, "Next attempt to sync jobs will be over " + (this.jobAttemptInterval / 1000 / 60) + " min.");
			}

			// check transfers
			if (((now - this.transfersLastAttemptTime) >= this.transferAttemptInterval) && (RUN_JOB_SYNC_SCRIPT)) { // I will use the same trigger
				this.transfersLastAttemptTime = now;
				final boolean transfersWasSuccessful = getAliEnTransfers();
				if (!transfersWasSuccessful) {
					if (this.transferAttemptInterval <= (60 * 60 * 1000))
						this.transferAttemptInterval *= 2;
				}
				else {
					JOB_SYNC_RUN_INTERVAL = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.JOB_SYNC_RUN_INTERVAL", JOB_SYNC_RUN_INTERVAL / 1000) * 1000;
					this.transferAttemptInterval = JOB_SYNC_RUN_INTERVAL;
				}
				logger.log(Level.INFO, "Next attempt to sync transfers will be over " + (this.transferAttemptInterval / 1000 / 60) + " min.");
			}
		}
	}

	/**
	 * @param sFarmName
	 */
	public AliEnFilter(final String sFarmName) {
		super(sFarmName);

		JobUtil.fillKnownJobStatusCS(this.knownJobStatusCS, this.knownJobStatusSite, this.knownUserJobsParams);
		JobUtil.fillKnownRootJobsStats(this.knownRootJobsHostParams, this.knownRootJobsDestParams, this.knownRootJobsDestToHostParams);
		TransferUtil.fillKnownTransferStatusCS(this.knownTransferStatusCS, this.knownTransferParams);
		JobAgentUtil.fillKnownJobAgentsStatus(this.knownJobAgentParams);

		// htSitesMaxJobs = new Hashtable();
		// htSitesJobsStats = new Hashtable();
		if ((ALIEN_ROOT != null) && (ALIEN_ROOT.length() > 0)) {
			logger.log(Level.INFO, "Starting with ALIEN_ROOT=" + ALIEN_ROOT);
			final Timer tmConf = new Timer(true);
			final ConfigLoader configLoader = new ConfigLoader();
			configLoader.run();
			tmConf.schedule(configLoader, 0, 2 * 60 * 1000);
		}
		else
			logger.log(Level.SEVERE, "Not starting ConfigLoader thread since ALIEN_ROOT is '" + ALIEN_ROOT + "'");
	}

	@Override
	public String getName() {
		return Name;
	}

	@Override
	public long getSleepTime() {
		return AF_SLEEP_TIME;
	}

	/** Receive all results that are produced by the modules */
	@Override
	public monPredicate[] getFilterPred() {
		return null;
	}

	/** This is called when I receive a Result or an eResult */
	@Override
	synchronized public void notifyResult(final Object o) {
		if (o == null)
			return;

		if (o instanceof Result) {
			final Result r = (Result) o;

			if ("AliEnFilter".equals(r.Module))
				return;

			final String Cluster = r.ClusterName;
			final String Node = r.NodeName;

			if (Cluster.endsWith("_Jobs") || Cluster.equals("Job_XRD_Transfers"))
				addJobData(Cluster, Node, r);
			else if (Cluster.endsWith("_Nodes"))
				addNodeData(Cluster, Node, r);
			else if (Cluster.equals("CastorGrid_scripts"))
				addRfcpData(r);
			else if (Cluster.startsWith("TaskQueue_Jobs_"))
				addJobStatusCSData(r);
			else if (Cluster.startsWith("TransferQueue_Transfers_"))
				addTransferStatusCSData(Cluster, Node, r);
			else if (Cluster.endsWith("_ApiService"))
				addAPIServiceData(r);
			else if (Cluster.endsWith("_JobAgent"))
				addJobAgentData(r);
			else if (Cluster.indexOf("_CE_") > 0)
				addCEData(r);
			else if (Cluster.endsWith("_DAQ"))
				addDAQData(r);
			else if (Cluster.startsWith("FDT_Link_"))
				addFDTTestTransferData(r);
			else if (Cluster.equals("CERN_QUOTA"))
				addQuotaData(r);
			else if (Cluster.startsWith("ROOT_CAF") || Cluster.startsWith("ROOT_PROOF::"))
				addRootCafUsage(r);
			else if (Cluster.equals("XrdServers"))
				addSrvXrdData(r);
			else if (Cluster.startsWith("SE_READ_") || Cluster.startsWith("SE_WRITE_"))
				addSEfileTransfer(r);
		}
		else if (o instanceof eResult) {
			final eResult er = (eResult) o;
			if ("AliEnFilter".equals(er.Module))
				return;

			final String Cluster = er.ClusterName;
			final String Node = er.NodeName;

			if (Cluster.endsWith("_Jobs") || Cluster.equals("Job_XRD_Transfers"))
				addJobData(Cluster, Node, er);
			else if (Cluster.endsWith("_Nodes"))
				addNodeData(Cluster, Node, er);
			if (Cluster.startsWith("TaskQueue_Jobs_"))
				addJobStatusCSData(er);
			else if (Cluster.startsWith("TransferQueue_Transfers_"))
				addTransferStatusCSData(Cluster, Node, er);
			else if (Cluster.startsWith("ROOT_CAF") || Cluster.startsWith("ROOT_PROOF::"))
				addRootCafUsage(er);
			else if (Cluster.equals("ROOT_AliEnJob_Info"))
				addJobData(Cluster, Node, er);
			else if (Cluster.startsWith("ROOT_")) {
				if (!Cluster.startsWith("ROOT_IGN_"))
					addRootJobData(er);
			}
			else if (Cluster.indexOf("_FTS_") > 0)
				addFTSTransferData(er);
		}
		else if (o instanceof Collection<?>) {
			final Iterator<?> it = ((Collection<?>) o).iterator();

			while (it.hasNext())
				notifyResult(it.next());
		}
	}

	/// SITE JOB DATA ///////////////////////////////////////////////////////////////////////////////////////

	/** add information about a job */
	private void addJobData(final String clusterName, final String nodeName, final Object result) {
		final String jobID = nodeName;
		String ceName = null;

		if (clusterName.endsWith("_Jobs"))
			ceName = StringFactory.get(clusterName.substring(0, clusterName.length() - 5));

		final JobInfo ji = findJobInfo(ceName, jobID, true);
		if (ji == null) {
			logger.log(Level.INFO, "CE not found for job " + jobID + ". Ignoring result:" + result);
			return;
		}

		ji.updateData(result);
	}

	/**
	 * Search for a job info object, given the jobID (and ceName for faster access). If create is true and ceName is given, the JobInfo object will be created if not found.
	 */
	private JobInfo findJobInfo(final String ceNameParam, final String jobID, final boolean create) {
		String ceName = ceNameParam;

		if (ceName == null) {
			// we have to search the Computing Element based on job ID
			for (final Entry<String, Hashtable<String, JobInfo>> meCE : this.htComputingElements.entrySet()) {
				final String ce = meCE.getKey();
				final Hashtable<String, JobInfo> htJobsInCE = meCE.getValue();

				if (htJobsInCE.containsKey(jobID)) {
					ceName = ce;
					break;
				}
			}

			if (ceName == null)
				// not found; we cannot create it since we don't know the CE name
				ceName = "UnknownCE";
			// return null;
		}

		Hashtable<String, JobInfo> htJobsInCE = this.htComputingElements.get(ceName);
		if (htJobsInCE == null)
			if (create) {
				// first appearance of this CE. create the CE hash
				htJobsInCE = new Hashtable<>();
				this.htComputingElements.put(ceName, htJobsInCE);
			}
			else
				return null;

		JobInfo ji = htJobsInCE.get(jobID);
		if (ji == null)
			if (create) {
				// first time we know about this job. Create the JobInfo structure.
				ji = new JobInfo(ceName, jobID);
				htJobsInCE.put(jobID, ji);
			}
			else
				return null;

		return ji;
	}

	/// JOB STATUS CS DATA ///////////////////////////////////////////////////////////////////////////////////////

	/** add info about the status of a job from AliEn Central Services */
	private void addJobStatusCSData(final Object o) {
		if (o instanceof Result) {
			final Result r = (Result) o;

			final int jidid;
			final int stid;

			if (((jidid = r.getIndex("jobID")) >= 0) && ((stid = r.getIndex("statusID")) >= 0)) {
				final String execHost = r.NodeName;

				String orgName = r.ClusterName.substring("TaskQueue_Jobs_".length()).toUpperCase();
				if (orgName.length() == 0)
					orgName = AppConfig.getGlobalEnvProperty("ALIEN_ORGANISATION", "Alice").toUpperCase();

				orgName = StringFactory.get(orgName);

				final String jobID = String.valueOf((long) r.param[jidid]);
				final int status = (int) r.param[stid];
				final JobStatusCS jscs = findJobStatusCS(orgName, jobID, true);

				jscs.setExecHost(execHost, o, 0);
				jscs.setStatus(status);
			}
		}
		else if (o instanceof eResult) {
			final eResult er = (eResult) o;

			final String execHost = er.NodeName;
			final int shid = er.getIndex("submitHost");
			String submitHost = (shid >= 0 ? (String) er.param[shid] : null);

			if (submitHost != null) {
				final int iIdx = submitHost.indexOf('/');

				if ((iIdx > 0) && (iIdx < (submitHost.length() - 1)) && (iIdx == submitHost.lastIndexOf('/'))) {
					final String jobID = StringFactory.get(submitHost.substring(0, iIdx));
					submitHost = StringFactory.get(submitHost.substring(iIdx + 1));

					// this can only be a submitHost result
					String orgName = er.ClusterName.substring("TaskQueue_Jobs_".length()).toUpperCase();

					if (orgName.length() == 0)
						orgName = AppConfig.getGlobalEnvProperty("ALIEN_ORGANISATION", "ALICE").toUpperCase();

					orgName = StringFactory.get(orgName);

					final JobStatusCS jscs = findJobStatusCS(orgName, jobID, true);
					jscs.setExecHost(execHost, o, 0);
					jscs.setSubmitHost(submitHost);
				}
				else
					submitHost = null;
			}

			if (submitHost == null)
				logger.log(Level.WARNING, "Received invalid submitHost with eR=" + er);
		}
	}

	/**
	 * Search for a job status from CS object, given the jobID and the Organisation Name. If create is true, the JobStatusCS object will be created if not found.
	 */
	private JobStatusCS findJobStatusCS(final String orgName, final String jobID, final boolean create) {
		Hashtable<String, JobStatusCS> htJobStats = this.htOrgCSJobStats.get(orgName);
		if (htJobStats == null) {
			htJobStats = new Hashtable<>();
			this.htOrgCSJobStats.put(orgName, htJobStats);
		}

		JobStatusCS jscs = htJobStats.get(jobID);
		if ((jscs == null) && create) {
			try (DBFunctions db = getQueueDB()) {
				if (db != null) {
					db.setQueryTimeout(15);

					if (db.query("SELECT queueId, statusId, userId, submitHostId, execHostId, received, started, finished, siteId FROM QUEUE WHERE queueId=?", false, Long.valueOf(jobID))) {
						if (db.moveNext())
							jscs = jsStatusfromDB(db, orgName);
						else
							logger.log(Level.WARNING, jobID + " doesn't seem to exist in the queue any more, falling back to the legacy method of waiting for the fields to be updated");
					}
					else
						logger.log(Level.WARNING, "Direct DB query failed for " + jobID + ", falling back to the legacy method of waiting for the fields to be updated");
				}

				if (jscs == null)
					jscs = new JobStatusCS(jobID, orgName);
			}

			htJobStats.put(jobID, jscs);
		}

		return jscs;
	}

	/**
	 * This is called when this filter starts, and then on a regular basis, with a vector of JobStatusCS objects that describe the jobs currently running in AliEn. This is done to synchronize the
	 * filter's hashes with the image of the jobs as AliEn sees it. This is needed because some UDPs might get lost and therefore ML might report different data from AliEn.
	 *
	 * @param crtAliEnJobs
	 */
	synchronized void addJobStatusCSBulk(final List<JobStatusCS> crtAliEnJobs) {
		int addedJobs = 0, updatedJobs = 0, markedForDeletion = 0, ignoredJobs = 0;
		// adding / updating jobs
		final long now = NTPDate.currentTimeMillis();

		final HashMap<String, Set<String>> hmCrtAliEnJobsPerOrg = new HashMap<>();

		String sOldOrg = null;

		Set<String> sCrtAliEnJobIDs = null;

		for (final Iterator<JobStatusCS> jit = crtAliEnJobs.iterator(); jit.hasNext();) {
			final JobStatusCS nJS = jit.next();
			final long njRunTime = nJS.getStatusTime(JobUtil.JS_RUNNING);

			if ((njRunTime != 0) && ((now - njRunTime) > JOB_STATUS_CS_EXPIRE)) {
				// this job is too old to be added - ignoring it
				jit.remove();
				ignoredJobs++;
				continue;
			}

			final JobStatusCS oJS = findJobStatusCS(nJS.orgName, nJS.jobID, false);
			if (oJS == null) {
				// nJS wasn't already present. Just add it to the hash.
				final Hashtable<String, JobStatusCS> htJobStats = this.htOrgCSJobStats.get(nJS.orgName);
				htJobStats.put(nJS.jobID, nJS);
				addedJobs++;
			}
			else {
				// this job was already discovered. We have to 'merge' the
				// details from nJS into oJS where they don't exist
				// status, submitHost and execHost are updated only if they weren't updated lately.
				if ((now - oJS.lastUpdateTime) > PARAM_EXPIRE) {
					oJS.setStatus(nJS.status);
					oJS.setSubmitHost(nJS.submitHost);
					oJS.setExecHost(nJS.execHost, null, nJS.siteId);
				}
				if (oJS.submitHost == null)
					oJS.setSubmitHost(nJS.submitHost);
				oJS.setStatusTime(JobUtil.JS_INSERTING, nJS.getStatusTime(JobUtil.JS_INSERTING));
				oJS.setStatusTime(JobUtil.JS_RUNNING, nJS.getStatusTime(JobUtil.JS_RUNNING));
				oJS.setStatusTime(JobUtil.JS_DONE, nJS.getStatusTime(JobUtil.JS_DONE));
				updatedJobs++;
			}

			if ((sOldOrg == null) || !nJS.orgName.equals(sOldOrg)) {
				sCrtAliEnJobIDs = hmCrtAliEnJobsPerOrg.get(nJS.orgName);

				if (sCrtAliEnJobIDs == null) {
					// most probably we have only one organization so all the jobs will go in this structure
					sCrtAliEnJobIDs = new HashSet<>(crtAliEnJobs.size());
					hmCrtAliEnJobsPerOrg.put(nJS.orgName, sCrtAliEnJobIDs);
				}

				sOldOrg = nJS.orgName;
			}

			// cannot be null because of the above
			if (sCrtAliEnJobIDs != null)
				sCrtAliEnJobIDs.add(nJS.jobID);
		}

		// removing jobs
		final Iterator<Map.Entry<String, Hashtable<String, JobStatusCS>>> orgIterator = this.htOrgCSJobStats.entrySet().iterator();

		while (orgIterator.hasNext()) {
			final Map.Entry<String, Hashtable<String, JobStatusCS>> meIterator = orgIterator.next();

			final Hashtable<String, JobStatusCS> htJobStats = meIterator.getValue();
			final String orgName = meIterator.getKey();

			final Set<String> sCrtAliEnJobIDsSet = hmCrtAliEnJobsPerOrg.get(orgName);

			if (sCrtAliEnJobIDsSet == null)
				logger.log(Level.INFO, "Set of known jobs is null for " + orgName);
			else
				logger.log(Level.INFO, "Set of known jobs has " + sCrtAliEnJobIDsSet.size() + " elements for " + orgName);

			for (final JobStatusCS oldJS : htJobStats.values()) {
				boolean found = false;

				if (sCrtAliEnJobIDsSet != null)
					found = sCrtAliEnJobIDsSet.contains(oldJS.jobID);

				if (!found) {
					oldJS.notInAliEn = true;
					markedForDeletion++;
				}
			}
		}
		logger.log(Level.INFO,
				"Of the loaded jobs " + ignoredJobs + " were ignored, " + addedJobs + " were added, " + updatedJobs + " were updated and " + markedForDeletion + " were marked for deletion.");
	}

	/// TRANSFER STATUS CS DATA (FTD transfers) /////////////////////////////////////////////////////////

	/** add data about a FTD transfer performed by the AliEn CentralServices */
	private void addTransferStatusCSData(final String cluster, final String node, final Object o) {
		String orgName = cluster.substring("TransferQueue_Transfers_".length()).toUpperCase();
		if (orgName.length() == 0)
			orgName = AppConfig.getGlobalEnvProperty("ALIEN_ORGANISATION", "ALICE").toUpperCase();

		orgName = StringFactory.get(orgName);

		final String transferID = node;
		final TransferStatusCS tscs = findTransferStatusCS(orgName, transferID, true);

		if (o instanceof Result) {
			final Result r = (Result) o;
			tscs.updateData(r);
		}
		else if (o instanceof eResult) {
			final eResult er = (eResult) o;
			tscs.updateData(er);
		}
	}

	/**
	 * Search for a transfer status from CS object, given the transferID and the Organisation Name. If create is true, the TransferStatusCS object will be created if not found.
	 */
	private TransferStatusCS findTransferStatusCS(final String orgName, final String transferID, final boolean create) {
		Hashtable<String, TransferStatusCS> htTransferStats = this.htOrgCSTransferStats.get(orgName);
		if (htTransferStats == null) {
			htTransferStats = new Hashtable<>();
			this.htOrgCSTransferStats.put(orgName, htTransferStats);
		}

		TransferStatusCS tscs = htTransferStats.get(transferID);
		if ((tscs == null) && create) {
			tscs = new TransferStatusCS(transferID, orgName);
			htTransferStats.put(transferID, tscs);
		}

		return tscs;
	}

	/**
	 * This is called when this filter starts, or as soon as possible, only once, with a vector of TransferStatusCS objects that describe the transfers currently running in AliEn. It will add them (or
	 * unknown details) to filter's hashes.
	 *
	 * @param crtAliEnTransfers
	 */
	synchronized void addTransferStatusCSBulk(final List<TransferStatusCS> crtAliEnTransfers) {
		int addedTransfers = 0, updatedTransfers = 0, markedForDeletion = 0, ignoredTransfers = 0;
		// adding / updating transfers
		final long now = NTPDate.currentTimeMillis();

		final Map<String, Set<String>> hmTransfersPerOrg = new HashMap<>();

		String sOldOrg = null;

		Set<String> sTransferIDs = null;

		for (final Iterator<TransferStatusCS> tit = crtAliEnTransfers.iterator(); tit.hasNext();) {
			final TransferStatusCS nTS = tit.next();
			if ((nTS.startTime != 0) && ((now - nTS.startTime) > JOB_STATUS_CS_EXPIRE)) {
				// this transfer is too old to be added - ignoring it
				tit.remove();
				ignoredTransfers++;
				continue;
			}

			final TransferStatusCS oTS = findTransferStatusCS(nTS.orgName, nTS.transferID, false);
			if (oTS == null) {
				// nTS wasn't already present. Just add it to the hash.
				final Hashtable<String, TransferStatusCS> htTransferStats = this.htOrgCSTransferStats.get(nTS.orgName);
				htTransferStats.put(nTS.transferID, nTS);
				addedTransfers++;
			}
			else {
				// this transfer was already discrovered. We have to 'merge' the
				// details from nTS into oTS where they don't exist
				// transferID and status FOR SURE have the latest value, so we don't check/change them
				oTS.mergeParams(nTS);
				updatedTransfers++;
			}

			if ((sOldOrg == null) || !nTS.orgName.equals(sOldOrg)) {
				sTransferIDs = hmTransfersPerOrg.get(nTS.orgName);

				if (sTransferIDs == null) {
					sTransferIDs = new HashSet<>(crtAliEnTransfers.size());
					hmTransfersPerOrg.put(nTS.orgName, sTransferIDs);
				}

				sOldOrg = nTS.orgName;
			}

			// cannot be null because of the above, but Java complains, so why not
			if (sTransferIDs != null)
				sTransferIDs.add(nTS.transferID);
		}

		// removing transfers

		final Iterator<Map.Entry<String, Hashtable<String, TransferStatusCS>>> oit = this.htOrgCSTransferStats.entrySet().iterator();

		while (oit.hasNext()) {
			final Map.Entry<String, Hashtable<String, TransferStatusCS>> me = oit.next();

			final String sOrg = me.getKey();
			final Hashtable<String, TransferStatusCS> htTransferStats = me.getValue();

			sTransferIDs = hmTransfersPerOrg.get(sOrg);

			final Iterator<TransferStatusCS> otit = htTransferStats.values().iterator();

			while (otit.hasNext()) {
				final TransferStatusCS oldTS = otit.next();

				if ((sTransferIDs == null) || !sTransferIDs.contains(oldTS.transferID)) {
					oldTS.notInAliEn = true;
					markedForDeletion++;
				}
			}
		}

		logger.log(Level.INFO, "Of the loaded transfers " + ignoredTransfers + " were ignored, " + addedTransfers + " were added, " + updatedTransfers + " were updated and " + markedForDeletion
				+ " were marked for deletion.");
	}

	/// NODE DATA ///////////////////////////////////////////////////////////////////////////////////////

	/** add information about a node */
	private void addNodeData(final String clusterName, final String nodeName, final Object result) {
		final String host = nodeName;
		String siteName = null;

		if (clusterName.endsWith("_Nodes"))
			siteName = StringFactory.get(clusterName.substring(0, clusterName.length() - 6));

		final NodeInfo ni = findNodeInfo(siteName, host, true);
		if (ni == null) {
			logger.log(Level.INFO, "Site not found for host " + host + ". Ignoring result.");
			return;
		}

		ni.updateData(result);
	}

	/**
	 * Search for a NodeInfo object for the given host (and site, for faster access). If create is true, and site given, it will also create it.
	 *
	 * @param siteParam
	 * @param host
	 * @param create
	 * @return node info
	 */
	NodeInfo findNodeInfo(final String siteParam, final String host, final boolean create) {
		if (host == null)
			return null;

		String site = siteParam;

		if (site == null) {
			// we have to search for this host in all known sites...
			for (final Entry<String, Hashtable<String, NodeInfo>> meS : this.htSites.entrySet()) {
				final String s = meS.getKey();
				final Hashtable<String, NodeInfo> htNodesInSite = meS.getValue();

				if (htNodesInSite.containsKey(host)) {
					site = s;
					break;
				}
			}
			if (site == null)
				// not found; since we don't know the site, we won't create it
				return null;
		}

		Hashtable<String, NodeInfo> htNodesInSite = this.htSites.get(site);
		if (htNodesInSite == null) {
			if (!create)
				return null;

			htNodesInSite = new Hashtable<>();
			this.htSites.put(site, htNodesInSite);
		}

		NodeInfo ni = htNodesInSite.get(host);
		if (ni == null) {
			if (!create)
				return null;

			ni = new NodeInfo(site, host);
			htNodesInSite.put(host, ni);
		}

		return ni;
	}

	/// NETWORK MATCHING functions to identify Sites, SEs ... //////////////////////////////////////
	/**
	 * convert an IP from a double value to a InetAddress. I don't know for sure if the byte order is good; it might have problems on other machines.
	 */
	private static InetAddress getIPfromDouble(final double dIP) {
		final long lIP = (long) dIP;
		final byte[] bIP = new byte[4];

		bIP[3] = (byte) ((lIP >> 24) & 0xffL);
		bIP[2] = (byte) ((lIP >> 16) & 0xffL);
		bIP[1] = (byte) ((lIP >> 8) & 0xffL);
		bIP[0] = (byte) (lIP & 0xffL);

		try {
			return InetAddress.getByAddress(bIP);
		}
		catch (@SuppressWarnings("unused") final UnknownHostException ex) {
			return null;
		}
	}

	// set some reasonable limits to how many entries can be cached
	static final ExpirationCache<InetAddress, String> netMatchIPCache = new ExpirationCache<>(10000);

	/** return the key of the entry in the given hashtable that matched the given IP */
	private static String getNetMatch(final Hashtable<String, NetMatcher> matchers, final ReentrantReadWriteLock rwLock, final InetAddress ip) {
		if (ip == null)
			return null;

		String cachedValue = netMatchIPCache.get(ip);

		if (cachedValue != null)
			return cachedValue;

		if (rwLock != null) {
			rwLock.readLock().lock();

			try {
				cachedValue = getNetMatchUnsync(matchers, ip);
			}
			finally {
				rwLock.readLock().unlock();
			}
		}
		else
			synchronized (matchers) {
				cachedValue = getNetMatchUnsync(matchers, ip);
			}

		netMatchIPCache.put(ip, cachedValue, LDAP_QUERY_INTERVAL);
		return cachedValue;
	}

	private static String getNetMatchUnsync(final Hashtable<String, NetMatcher> matchers, final InetAddress ip) {
		for (final Map.Entry<String, NetMatcher> meM : matchers.entrySet())
			if (meM.getValue().matchInetNetwork(ip))
				return meM.getKey();

		return null;
	}

	// set some reasonable limits to how many entries can be cached
	static final ExpirationCache<String, String> netMatchCache = new ExpirationCache<>(10000);

	/**
	 * return the key of the entry in the given hashtable that matched the given hostname
	 *
	 * @param matchers
	 * @param rwLock
	 * @param hostName
	 * @return net match
	 */
	static String getNetMatch(final Hashtable<String, NetMatcher> matchers, final ReentrantReadWriteLock rwLock, final String hostName) {
		if (hostName == null)
			return null;

		String cachedValue = netMatchCache.get(hostName);

		if (cachedValue != null)
			return cachedValue;

		if (rwLock != null) {
			rwLock.readLock().lock();

			try {
				cachedValue = getNetMatchUnsync(matchers, hostName);
			}
			finally {
				rwLock.readLock().unlock();
			}
		}
		else
			synchronized (matchers) {
				cachedValue = getNetMatchUnsync(matchers, hostName);
			}

		netMatchCache.put(hostName, cachedValue, LDAP_QUERY_INTERVAL);
		return cachedValue;
	}

	private static String getNetMatchUnsync(final Hashtable<String, NetMatcher> matchers, final String hostName) {
		String host = hostName;

		try {
			final int hostId = Integer.parseInt(hostName);

			host = getHost(hostId);

			if (host == null)
				return null;
		}
		catch (@SuppressWarnings("unused") final NumberFormatException nfe) {
			// ignore hosts that are actually a host ID in AliEn
		}

		for (final Map.Entry<String, NetMatcher> meM : matchers.entrySet())
			if (meM.getValue().matchInetNetwork(host))
				return meM.getKey();

		return null;
	}

	/// XROOTD job data /////////////////////////////////////////////////////////////////////////////

	/**
	 * finds in the given hash the requested XrdInfo container; it adds the passed data array.
	 */
	private static void findXrdAndAdd(final Hashtable<String, XrdInfo> htInfos, final String name, final DataArray da, final boolean canExpire) {
		final String sName = StringFactory.get(name);

		XrdInfo xi = htInfos.get(sName);
		if (xi == null) {
			xi = new XrdInfo(sName, canExpire);
			htInfos.put(sName, xi);
		}

		xi.addData(da);
	}

	// /**
	// * finds in the given hash the requested XrdInfo container; it sets the passed data array.
	// */
	// private void findXrdAndSet(Hashtable htInfos, String name, DataArray da, boolean canExpire){
	// XrdInfo xi = (XrdInfo) htInfos.get(name);
	// if(xi == null){
	// xi = new XrdInfo(name, canExpire);
	// htInfos.put(name, xi);
	// }
	// xi.setData(da);
	// }

	/**
	 * Add external xrd data regarding a job. By external data we mean job traffic performed while preparing job's files and while saving the produced files.
	 *
	 * @param srcIP
	 * @param dstIP
	 * @param transfMBytes
	 * @param transfFiles
	 */
	void addJobExternalXrdData(final double srcIP, final double dstIP, final double transfMBytes, final double transfFiles) {
		final InetAddress ina_src = getIPfromDouble(srcIP);
		final InetAddress ina_dst = getIPfromDouble(dstIP);

		// compute Sites summary first
		final String srcFarm = getNetMatch(this.htSitesNetMatchers, this.sitesNetMatchersLock, ina_src);
		final String dstFarm = getNetMatch(this.htSitesNetMatchers, this.sitesNetMatchersLock, ina_dst);

		final String srcSE = getNetMatch(this.htSEsNetMatchers, this.sesNetMatchers, ina_src);
		final String dstSE = getNetMatch(this.htSEsNetMatchers, this.sesNetMatchers, ina_dst);

		if (logger.isLoggable(Level.FINER))
			logger.finer("Adding JobExternalXrdData farms=" + srcFarm + "->" + dstFarm + " SEs=" + srcSE + "->" + dstSE + " MB=" + transfMBytes + " files=" + transfFiles);

		addJobXrdData(srcFarm, dstFarm, srcSE, dstSE, transfMBytes, transfFiles);
	}

	/**
	 * Add traffic information for a ROOT job. A root job only reads data from a SE.
	 *
	 * @param srcHost
	 * @param transfMBytes
	 * @param transfFiles
	 */
	void addROOTJobXrdData(final String srcHost, final double transfMBytes, final double transfFiles) {
		final String srcFarm = getNetMatch(this.htSitesNetMatchers, this.sitesNetMatchersLock, srcHost);
		final String srcSE = getNetMatch(this.htSEsNetMatchers, this.sesNetMatchers, srcHost);

		if (logger.isLoggable(Level.FINER))
			logger.finer("Adding ROOTJobXrdData from farm=" + srcFarm + " SE=" + srcSE + " MB=" + transfMBytes + " files=" + transfFiles);

		addJobXrdData(srcFarm, null, srcSE, null, transfMBytes, transfFiles);
	}

	private static final String UNKNOWN = "UNKNOWN"; // use a variable to avoid spell errors

	/**
	 * Add traffic data from a job. This produces the following clusters/nodes/params: + Site_Traffic_Summary + SITE1-SITE2 + Internal_SITE + Incoming_SITE + Outgoing_SITE + transf_mbytes_R +
	 * transf_files_R + SE_Traffic_Summary + Alice::SITE::SE_Name + transf_rd_mbytes_R + transf_rd_files_R + transf_wr_mbytes_R + transf_wr_files_R
	 *
	 * Note that srcFarm/SE, dstFarm/SE can be null if the source/destination was not identified.
	 */
	private void addJobXrdData(final String srcFarmParam, final String dstFarmParam, final String srcSE, final String dstSE, final double transf_mbytes, final double transf_files) {
		final DataArray da = new DataArray();
		da.setParam("transf_mbytes_R", transf_mbytes);
		da.setParam("transf_files_R", transf_files);

		String srcFarm = (srcFarmParam == null ? UNKNOWN : srcFarmParam);

		String dstFarm = (dstFarmParam == null ? UNKNOWN : dstFarmParam);

		// if source or destination farms cannot be identified,
		// there are still some things we can do
		final String siteName = StringFactory.get(this.farmName.endsWith("-L") ? this.farmName.substring(0, this.farmName.length() - 2) : this.farmName);

		if (srcFarm.equals(UNKNOWN)) {
			if (dstFarm.equals(UNKNOWN))
				srcFarm = dstFarm = siteName; // consider this as internal traffic
			else if (!dstFarm.equals(siteName))
				srcFarm = siteName;
			// else there's nothing we can do; it will be UNK -> this farm
		}
		else // srcFarm is known
		if (dstFarm.equals(UNKNOWN))
			if (!srcFarm.equals(siteName))
				dstFarm = siteName;
		// else there's notging we can do; it will be this farm -> UNK

		final boolean unknownTransfer = (srcFarm.equals(UNKNOWN)) || (dstFarm.equals(UNKNOWN));

		if (srcFarm.equals(dstFarm))
			findXrdAndAdd(this.htSitesTraffic, "Internal_" + srcFarm, da, false);
		else {
			findXrdAndAdd(this.htSitesTraffic, srcFarm + "-" + dstFarm, da, false); // cannot expire anymore
			findXrdAndAdd(this.htSitesTraffic, "Outgoing_" + srcFarm, da, false);
			findXrdAndAdd(this.htSitesTraffic, "Incoming_" + dstFarm, da, false);
		}

		// compute the SE summary
		da.clear();

		// since we get data from a job, only one of these can be a SE
		if (srcSE != null) {
			da.setParam("transf_rd_mbytes_R", transf_mbytes);
			da.setParam("transf_rd_files_R", transf_files);
			findXrdAndAdd(this.htSEsTraffic, srcSE, da, false);
		}
		else if (dstSE != null) {
			da.setParam("transf_wr_mbytes_R", transf_mbytes);
			da.setParam("transf_wr_files_R", transf_files);
			findXrdAndAdd(this.htSEsTraffic, dstSE, da, false);
		}

		if (logger.isLoggable(Level.FINER))
			logger.log(Level.FINER, "Got mbytes=" + transf_mbytes + " files=" + transf_files + " from srcFarm=" + srcFarm + " dstFarm=" + dstFarm + " srcSE=" + srcSE + " dstSE=" + dstSE);
		else if (unknownTransfer || ((srcSE == null) && (dstSE == null)))
			logger.log(Level.INFO,
					"Got unknown transfer mbytes=" + transf_mbytes + " files=" + transf_files + " from srcFarm=" + srcFarm + " dstFarm=" + dstFarm + " srcSE=" + srcSE + " dstSE=" + dstSE);
	}

	/// CASTORGRID statistics ///////////////////////////////////////////////////////////////

	/**
	 * Add data about a castorgrid Xrootd 'rfcp' transfer. The results should have these 3 kinds of parameters:<br>
	 * - "migrating_mbytes" & "migrating_speed" & "migrating_errors"<br>
	 * - "staging_mbytes" & "staging_speed" & "staging_errors"<br>
	 * - "migrating_fsfill"
	 */
	private void addRfcpData(final Result r) {
		final String name = r.NodeName; // hostname of the castorgrid server

		RfcpInfo xi = this.htCastorgridServers.get(name);
		if (xi == null) {
			xi = new RfcpInfo(name, false);
			this.htCastorgridServers.put(name, xi);
		}

		String type = null;
		if ((r.param_name.length == 1) && r.param_name[0].equals("migrating_fsfill"))
			type = "fsfill";
		else if (r.param_name[0].startsWith("staging_"))
			type = "staging";
		else if (r.param_name[0].startsWith("migrating_"))
			type = "migrating";
		else if (r.param_name[0].startsWith("gc_"))
			type = "gc";

		if (type != null) {
			final DataArray da = new DataArray(r);
			xi.addData(da, type);
		}
		else
			logger.log(Level.INFO, "Ignoring non-standard 'CastorGrid_scripts' result: " + r);
	}

	/// XROOTD Server monitoring. Commented since it is not used anymore //////////////////////////

	// /**
	// * Add traffic data from a xrd server.
	// *
	// * Currently this accounts only for the number of connected clients and open files
	// * for each SE, since the transfer information is not reliable enough
	// */
	// private void addSrvXrdData(String cluster, String node, Result r){
	//
	// int i_ncl = r.getIndex("srv_conn_clients");
	// int i_nof = r.getIndex("srv_open_files");
	//
	// if(i_ncl == -1 || i_nof == -1)
	// return;
	// InetAddress ina_srv = null;
	// try{
	// ina_srv = InetAddress.getByName(node);
	// }catch(UnknownHostException ex){
	// return; // don't know what to do with this, either
	// }
	// String srvSE = getNetMatch(htSEsNetMatchers, ina_srv);
	// if(srvSE == null)
	// return;
	// DataArray da = new DataArray();
	// if(i_ncl != -1)
	// da.setParam("srv_conn_clients", r.param[i_ncl]);
	// if(i_nof != -1)
	// da.setParam("srv_open_files", r.param[i_nof]);
	// findXrdAndSet(htSEsTraffic, srvSE, da, false);
	//
	// if(logger.isLoggable(Level.FINER))
	// logger.log(Level.FINER, "Got ncl="+(i_ncl != -1 ? r.param[i_ncl] : -1)
	// +" #nof="+(i_nof != -1 ? r.param[i_nof] : -1));
	//
	//// int i_cli = r.getIndex("transf_client_ip");
	//// int i_rd = r.getIndex("transf_rd_mbytes");
	//// int i_wr = r.getIndex("transf_wr_mbytes");
	//// if(i_cli == -1 || i_rd == -1 || i_wr == -1)
	//// return; // don't know what to do with this; skip it
	//// InetAddress ina_cli = getIPfromDouble(r.param[i_cli]);
	//// InetAddress ina_srv = null;
	//// try{
	//// ina_srv = InetAddress.getByName(node);
	//// }catch(UnknownHostException ex){
	//// return; // don't know what to do with this, either
	//// }
	////
	//// String srvSE = getNetMatch(htSEsNetMatchers, ina_srv);
	//// String farmSE = getNetMatch(htSitesNetMatchers, ina_srv);
	//// String farmCli = getNetMatch(htSitesNetMatchers, ina_cli);
	////
	//// double transf_rd_mbytes = r.param[i_rd];
	//// double transf_wr_mbytes = r.param[i_wr];
	//// double transf_rd_files = (transf_rd_mbytes > 0 ? 1 : 0);
	//// double transf_wr_files = (transf_wr_mbytes > 0 ? 1 : 0);
	////
	//// // compute Sites summary first
	////
	//// DataArray dar = new DataArray();
	//// dar.setParam("transf_mbytes_R", transf_rd_mbytes);
	//// dar.setParam("transf_files_R", transf_rd_files);
	////
	//// DataArray daw = new DataArray();
	//// daw.setParam("transf_mbytes_R", transf_wr_mbytes);
	//// daw.setParam("transf_files_R", transf_wr_files);
	////
	//// if(farmSE != null && farmCli != null && (! farmSE.equals(farmCli)) && (transf_rd_mbytes > 0))
	//// findXrdAndAdd(htSitesTraffic, farmSE+"-"+farmCli, dar, true);
	////
	//// if(farmSE != null && (! farmSE.equals(farmCli)) && (transf_rd_mbytes > 0))
	//// findXrdAndAdd(htSitesTraffic, "Outgoing_"+farmSE, dar, false);
	////
	//// if(farmSE != null && (! farmSE.equals(farmCli)) && (transf_wr_mbytes > 0))
	//// findXrdAndAdd(htSitesTraffic, "Incoming_"+farmSE, daw, false);
	////
	//// if(farmSE != null && farmSE.equals(farmCli)){
	//// if(transf_rd_mbytes > 0)
	//// findXrdAndAdd(htSitesTraffic, "Internal_"+farmSE, dar, false);
	//// else
	//// findXrdAndAdd(htSitesTraffic, "Internal_"+farmSE, daw, false);
	//// }
	////
	//// // compute SEs summary
	////
	//// DataArray da = new DataArray();
	//// da.setParam("transf_rd_mbytes_R", transf_rd_mbytes);
	//// da.setParam("transf_rd_files_R", transf_rd_files);
	//// da.setParam("transf_wr_mbytes_R", transf_wr_mbytes);
	//// da.setParam("transf_wr_files_R", transf_wr_files);
	////
	//// if(srvSE != null)
	//// findXrdAndAdd(htSEsTraffic, srvSE, da, false);
	////
	//// if(logger.isLoggable(Level.FINER))
	//// logger.log(Level.FINER, "Got rd_mbytes="+transf_rd_mbytes+" rd_files="+transf_rd_files
	//// +" wr_mbytes="+transf_wr_mbytes+" wr_files="+transf_wr_files
	//// +" from srvSE="+srvSE+" farmSE="+farmSE+" farmCli="+farmCli);
	// }

	/// ROOT job data /////////////////////////////////////////////////////////////////////////////

	/**
	 * Add data about a ROOT job
	 */
	private void addRootJobData(final eResult er) {

		// parse the cluster name
		if (er.ClusterName.length() <= 5) {
			logger.log(Level.WARNING, "Cannot identify farm for ROOT result " + er);
			return;
		}

		final String fName = StringFactory.get(er.ClusterName.substring(5)); // what comes after ROOT_: ROOT_farmName_queryType
		final String jobID = er.NodeName;
		final int sjidx = er.getIndex("subid");
		final String subJobID = (sjidx >= 0 ? (String) er.param[sjidx] : "");
		final RootJobInfo rji = findRootJobInfo(ROOT_SUMMARY_PER_SUBJOB ? fName + "~" + jobID : fName, jobID, subJobID, true);
		rji.addResult(er);

		if (ROOT_JOB_TOTALS) {
			final RootJobInfo rjiSUM = findRootJobInfo(fName + "~_TOTALS_", jobID, subJobID, true);
			rjiSUM.addResult(er);
		}
	}

	/**
	 * Search for a ROOT job info object, given the farmName, jobID and subJobID. If create is true, the RootJobInfo object will be created if not found.
	 */
	private RootJobInfo findRootJobInfo(final String _farmName, final String jobID, final String subJobID, final boolean create) {

		final String sFarmName = StringFactory.get(_farmName);

		Hashtable<String, RootJobInfo> htRootJobsInFarm = this.htSitesRootJobs.get(sFarmName);
		if (htRootJobsInFarm == null)
			if (create) {
				// first appearance of this farm
				htRootJobsInFarm = new Hashtable<>();
				this.htSitesRootJobs.put(sFarmName, htRootJobsInFarm);
			}
			else
				return null;

		final String key = jobID + "|" + subJobID;
		RootJobInfo rji = htRootJobsInFarm.get(key);
		if (rji == null)
			if (create) {
				// first time we know about this ROOT job. Create the RootJobInfo structure.
				rji = new RootJobInfo(sFarmName, jobID, subJobID);
				htRootJobsInFarm.put(key, rji);
			}
			else
				return null;

		return rji;
	}

	// API Service data /////////////////////////////////////////////////////////////////////////////

	/**
	 * Add data about a API Service
	 */
	private void addAPIServiceData(final Result r) {
		final String cluster = r.ClusterName;
		final String sFarm = StringFactory.get(cluster.substring(0, cluster.indexOf("_ApiService")));
		final String apiService = r.NodeName;

		// ignore previously generated totals
		if (apiService.equals("_TOTALS_"))
			return;

		final APIServiceInfo api = findAPIServiceInfo(sFarm, apiService, true);
		api.setData(r);
	}

	/**
	 * Search for a APIService info object, given the farmName, apiService. If create is true, the APIServiceInfo object will be created if not found.
	 */
	private APIServiceInfo findAPIServiceInfo(final String sFarmName, final String apiService, final boolean create) {
		Hashtable<String, APIServiceInfo> htAPIServicesInFarm = this.htSitesAPIServices.get(sFarmName);
		if (htAPIServicesInFarm == null)
			if (create) {
				// first appearance of this farm
				htAPIServicesInFarm = new Hashtable<>();
				this.htSitesAPIServices.put(sFarmName, htAPIServicesInFarm);
			}
			else
				return null;

		APIServiceInfo api = htAPIServicesInFarm.get(apiService);
		if (api == null)
			if (create) {
				// first time we know about this ROOT job. Create the RootJobInfo structure.
				api = new APIServiceInfo(sFarmName, apiService);
				htAPIServicesInFarm.put(apiService, api);
			}
			else
				return null;

		return api;
	}

	// JobAgent data /////////////////////////////////////////////////////////////////////////////

	/** Add data about a JobAgent */
	private void addJobAgentData(final Result r) {
		final String cluster = r.ClusterName;
		final String sFarm = StringFactory.get(cluster.substring(0, cluster.indexOf("_JobAgent")));
		final String jobAgent = r.NodeName;
		final JobAgentInfo ja = findJobAgentInfo(sFarm, jobAgent, true);
		ja.setData(r);

		final int idxNumJobs = r.getIndex("numjobs");

		if (idxNumJobs >= 0) {
			Histogram h = htSitesJobAgentsNumJobs.get(sFarm);

			if (h == null) {
				h = new Histogram();
				h.setName("numjobs");
				h.setMaxLimit(50);
				h.add(r, idxNumJobs);
				htSitesJobAgentsNumJobs.put(sFarm, h);
			}
			else
				synchronized (h) {
					h.add(r, idxNumJobs);
				}
		}
	}

	/**
	 * Search for a JobAgent info object, given the farmName, jobAgent. If create is true, the JobAgentInfo object will be created if not found.
	 */
	private JobAgentInfo findJobAgentInfo(final String sFarmName, final String jobAgent, final boolean create) {
		Hashtable<String, JobAgentInfo> htJobAgentsInFarm = this.htSitesJobAgents.get(sFarmName);
		if (htJobAgentsInFarm == null)
			if (create) {
				// first appearance of this farm
				htJobAgentsInFarm = new Hashtable<>();
				this.htSitesJobAgents.put(sFarmName, htJobAgentsInFarm);
			}
			else
				return null;

		JobAgentInfo ja = htJobAgentsInFarm.get(jobAgent);
		if (ja == null)
			if (create) {
				// first time we know about this JobAgent. Create the JobAgentInfo structure.
				ja = new JobAgentInfo(sFarmName, jobAgent);
				htJobAgentsInFarm.put(jobAgent, ja);
			}
			else
				return null;

		return ja;
	}

	// CE data /////////////////////////////////////////////////////////////////

	/** Add data about a CE */
	private void addCEData(final Result r) {
		final String cluster = r.ClusterName;
		final String sFarm = StringFactory.get(cluster.substring(0, cluster.indexOf("_CE_")));
		final String ceName = r.NodeName;
		final CEInfo ce = findCEInfo(sFarm, ceName, true);

		ce.setData(r);
	}

	/**
	 * Search for a CE info object, given the farmName, ceName. If create is true, the CEInfo object will be created if not found.
	 */
	private CEInfo findCEInfo(final String sFarmName, final String ceName, final boolean create) {
		Hashtable<String, CEInfo> htCEsInFarm = this.htSitesCEs.get(sFarmName);
		if (htCEsInFarm == null)
			if (create) {
				// first appearance of this farm
				htCEsInFarm = new Hashtable<>();
				this.htSitesCEs.put(sFarmName, htCEsInFarm);
			}
			else
				return null;

		CEInfo ce = htCEsInFarm.get(ceName);
		if (ce == null)
			if (create) {
				// first time we know about this JobAgent. Create the JobAgentInfo structure.
				ce = new CEInfo(sFarmName, ceName);
				htCEsInFarm.put(ceName, ce);
			}
			else
				return null;

		return ce;
	}

	// DAQ data /////////////////////////////////////////////////////////////////

	private void addDAQData(final Result r) {
		final String cluster = r.ClusterName;
		final String sFarm = StringFactory.get(cluster.substring(0, cluster.indexOf("_DAQ")));
		final String daqHost = r.NodeName;
		final DAQInfo daq = findDAQInfo(sFarm, daqHost, true);

		daq.setData(r);
	}

	/**
	 * Search for a DAQ info object, given the farmName, daqHost. If create is true, the DAQInfo object will be created if not found.
	 */
	private DAQInfo findDAQInfo(final String sFarmName, final String daqHost, final boolean create) {
		Hashtable<String, DAQInfo> htDAQInFarm = this.htSitesDAQ.get(sFarmName);
		if (htDAQInFarm == null)
			if (create) {
				// first appearance of this farm
				htDAQInFarm = new Hashtable<>();
				this.htSitesDAQ.put(sFarmName, htDAQInFarm);
			}
			else
				return null;

		DAQInfo daq = htDAQInFarm.get(daqHost);
		if (daq == null)
			if (create) {
				// first time we know about this DAQ. Create the DAQInfo structure.
				daq = new DAQInfo(sFarmName, daqHost);
				htDAQInFarm.put(daqHost, daq);
			}
			else
				return null;

		return daq;
	}

	/// FTS Transfers data ////////////////////////////////////////////////////////////////////////

	private void addFTSTransferData(final eResult er) {
		final String cluster = er.ClusterName;
		final String ftdFullName = StringFactory.get(cluster.substring(cluster.indexOf("_FTS_")));
		final String transferID = er.NodeName;
		final FTSTransferInfo ftsTrInfo = findFTSTranferInfo(ftdFullName, transferID, true);

		ftsTrInfo.setData(er);
	}

	private FTSTransferInfo findFTSTranferInfo(final String ftdFullName, final String transferID, final boolean create) {
		Hashtable<String, FTSTransferInfo> htFTD = this.htFTSTransfers.get(ftdFullName);
		if (htFTD == null)
			if (create) {
				htFTD = new Hashtable<>();
				this.htFTSTransfers.put(ftdFullName, htFTD);
			}
			else
				return null;

		FTSTransferInfo ftsTrInfo = htFTD.get(transferID);
		if (ftsTrInfo == null)
			if (create) {
				// first time we know about this FTS Transfer. Create the FTSTransferInfo structure.
				ftsTrInfo = new FTSTransferInfo(ftdFullName, transferID);
				htFTD.put(transferID, ftsTrInfo);
			}
			else
				return null;

		return ftsTrInfo;
	}

	/// FDT Test Transfers data ////////////////////////////////////////////////////////////////////////

	private void addFDTTestTransferData(final Result r) {
		final FDTTestTransferInfo fdtt = findFDTTestTransferInfo(r.ClusterName, r.NodeName, true);

		if (fdtt != null)
			fdtt.setData(r);
		else
			logger.log(Level.WARNING, "Cannot get the destIP for this result: " + r);
	}

	private FDTTestTransferInfo findFDTTestTransferInfo(final String cluster, final String node, final boolean create) {
		FDTTestTransferInfo fdtTT = this.htFDTTestTransfers.get(cluster + node);

		if (fdtTT == null)
			if (create) {
				final String linkName = StringFactory.get(cluster.substring("FDT_Link_".length()));
				final int idxSIP = node.lastIndexOf('_');
				final int idxEIP = node.lastIndexOf(':');
				if ((idxSIP >= 0) && (idxEIP > idxSIP)) {
					final String destIP = StringFactory.get(node.substring(idxSIP + 1, idxEIP));
					fdtTT = new FDTTestTransferInfo(linkName, destIP, this.htSitesNetMatchers, this.sitesNetMatchersLock, this.farmName);
					this.htFDTTestTransfers.put(node, fdtTT);
				}
			}

		return fdtTT;
	}

	/// QUOTA data ///////////////////////////////////////////////////////////////////////////

	private void addQuotaData(final Result r) {
		final QuotaInfo quotaInfo = findQuotaInfo(r.NodeName, true);

		if (quotaInfo != null)
			quotaInfo.setData(r);
		else
			logger.log(Level.WARNING, "Cannot get the QuotaInfo for this result: " + r);
	}

	private QuotaInfo findQuotaInfo(final String node, final boolean create) {
		QuotaInfo quotaInfo = this.htQuotaInfos.get(node);

		if (quotaInfo == null)
			if (create) {
				quotaInfo = new QuotaInfo(node);
				this.htQuotaInfos.put(node, quotaInfo);
			}

		return quotaInfo;
	}

	/// ROOT CAF Usage ////////////////////////////////////////////////////////////////////////////

	private void addRootCafUsage(final Result r) {
		if (r.NodeName.startsWith("Progress-"))
			return;

		final RootCafUsageInfo cafUsageInfo = findRootCafUsageInfo(r.ClusterName, r.NodeName, true);

		if (cafUsageInfo != null)
			cafUsageInfo.addDataValues(r);
		else
			logger.log(Level.WARNING, "Cannot get the RootCafUsageInfo for Result: " + r);
	}

	private void addRootCafUsage(final eResult er) {
		if (er.NodeName.startsWith("Progress-"))
			return;

		final RootCafUsageInfo cafUsageInfo = findRootCafUsageInfo(er.ClusterName, er.NodeName, true);

		if (cafUsageInfo != null)
			cafUsageInfo.addDataStrings(er);
		else
			logger.log(Level.WARNING, "Cannot get the RootCafUsageInfo for eResult: " + er);
	}

	private RootCafUsageInfo findRootCafUsageInfo(final String cluster, final String node, final boolean create) {
		Hashtable<String, RootCafUsageInfo> htInfosHash = this.htRootCafUsageInfos.get(cluster);
		if (htInfosHash == null)
			if (create) {
				htInfosHash = new Hashtable<>();
				this.htRootCafUsageInfos.put(cluster, htInfosHash);
			}
			else
				return null;

		RootCafUsageInfo info = htInfosHash.get(node);
		if (info == null)
			if (create) {
				info = new RootCafUsageInfo(node);
				htInfosHash.put(node, info);
			}

		return info;
	}

	/// XrdServers ////////////////////////////////////////////////////////////////////////////

	private void addSrvXrdData(final Result r) {
		XrdServerInfo xsi = this.htXrdServers.get(r.NodeName);

		if (xsi == null) {
			xsi = new XrdServerInfo(r.NodeName);
			this.htXrdServers.put(r.NodeName, xsi);
		}

		xsi.addData(r);
	}

	/// SE_READ_${SE::Name} and SE_WRITE_${SE::Name} data /////////////////////////////////////

	private void addSEfileTransfer(final Result r) {
		final String clusterName = r.ClusterName;
		String name = null;
		String operation = null;

		if (clusterName.startsWith("SE_READ_")) {
			name = StringFactory.get(clusterName.substring("SE_READ_".length()));
			operation = "read";
		}
		else if (clusterName.startsWith("SE_WRITE_")) {
			name = StringFactory.get(clusterName.substring("SE_WRITE_".length()));
			operation = "write";
		}
		else {
			logger.warning("Cannot be! SE transfer result clusterName is not SE_READ_* or SE_WRITE_*: " + clusterName);
			return;
		}

		SETransferInfo sei = this.htAliEnSEs.get(name);
		if (sei == null) {
			sei = new SETransferInfo(name);
			this.htAliEnSEs.put(name, sei);
		}

		sei.addData(operation, r);
	}

	/// SUMMARIZE functions ///////////////////////////////////////////////////////////////////////

	private static class JobComparator implements Comparator<JobInfo>, Serializable {
		private static final long serialVersionUID = 1L;

		public final String key;

		/**
		 * DataArray field of the job, to sort by
		 *
		 * @param field
		 */
		public JobComparator(final String field) {
			key = field;
		}

		@Override
		public int compare(final JobInfo ji1, final JobInfo ji2) {
			final double diff = ji1.values.getParam(key) - ji2.values.getParam(key);

			if (diff < 0)
				return 1;
			if (diff > 0)
				return -1;
			return 0;
		}
	}

	private static final JobComparator jobComparatorVirtualMem = new JobComparator("virtualmem");
	private static final JobComparator jobComparatorRSS = new JobComparator("rss");

	private static void addToSortedArray(final ArrayList<JobInfo> array, final JobInfo ji, final JobComparator comparator, final int maxSize) {
		if (maxSize <= 0)
			return;

		if (ji.values.getParam(comparator.key) <= 0)
			return; // sanity check, jobs that have 0 memory usage are bogus :)

		if (array.size() < maxSize) {
			array.add(ji);

			Collections.sort(array, comparator);

			return;
		}

		final JobInfo jiOld = array.get(maxSize - 1);

		if (jiOld.values.getParam(comparator.key) < ji.values.getParam(comparator.key)) {
			array.remove(maxSize - 1);
			array.add(ji);
			Collections.sort(array, jobComparatorVirtualMem);
		}
	}

	private void addRezFromSortedArray(final List<Serializable> rez, final String sCluster, final ArrayList<JobInfo> array, final JobComparator comparator, final String extra) {
		for (int i = 0; i < array.size(); i++) {
			final JobInfo ji = array.get(i);

			final DataArray da = new DataArray(ji.values.size() + 3);

			da.copyValues(ji.values);

			try {
				da.setParam("jobid", Double.parseDouble(ji.jobID));
			}
			catch (@SuppressWarnings("unused") final Throwable t) {
				// ignore
			}

			da.setParam("cpu_time_abs", ji.crtValues.getParam("cpu_time_R"));
			da.setParam("run_time_abs", ji.crtValues.getParam("run_time_R"));

			final String sNode = "top" + comparator.key + (extra != null ? extra : "") + "_" + (i + 1);

			addRezFromDA(rez, sCluster, sNode, da);

			if ((ji.jobUser != null) && (ji.host != null)) {
				final eResult er = new eResult(this.farm.name, sCluster, sNode, "AliEnFilter", null);
				er.time = NTPDate.currentTimeMillis();
				er.addSet("job_user", ji.jobUser);
				er.addSet("host", ji.host);

				rez.add(er);
			}
		}
	}

	/** summarize data from all jobs in each CE and produce a vector with Results */
	private void summarizeJobs(final List<Serializable> rez) {
		// statistics for all CEs that report here
		final DataArray allMin = new DataArray();
		final DataArray allMax = new DataArray(this.knownJobParams);
		final DataArray allSum = new DataArray(this.knownJobParams);
		final DataArray allMed = new DataArray(this.knownJobParams);
		final DataArray allJobs = new DataArray(this.knownJobStates);
		final Hashtable<String, DataArray> htUserSum = new Hashtable<>();
		int jobsCount = 0;

		final int iTopJobsCount = AppConfig.geti("lia.Monitor.Filters.AliEnFilter.topJobs", 3);

		final ArrayList<JobInfo> topJobsByVirtualMem = new ArrayList<>(iTopJobsCount);
		final ArrayList<JobInfo> topJobsByRSS = new ArrayList<>(iTopJobsCount);

		final Map<String, ArrayList<JobInfo>> topJobsByVirtualMemPerUser = new HashMap<>();
		final Map<String, ArrayList<JobInfo>> topJobsByRSSPerUser = new HashMap<>();

		// go and analyze jobs in all CEs
		for (final Entry<String, Hashtable<String, JobInfo>> cme : this.htComputingElements.entrySet()) {
			final String ceName = cme.getKey();
			final Hashtable<String, JobInfo> htJobsInCE = cme.getValue();

			// initialize the names for the parameters, but with zero values
			final DataArray min = new DataArray();
			final DataArray max = new DataArray(this.knownJobParams);
			final DataArray sum = new DataArray(this.knownJobParams);
			final DataArray med = new DataArray(this.knownJobParams);
			final DataArray jobs = new DataArray(this.knownJobStates);

			final ArrayList<JobInfo> topCEJobsByVirtualMem = new ArrayList<>(iTopJobsCount);
			final ArrayList<JobInfo> topCEJobsByRSS = new ArrayList<>(iTopJobsCount);

			// compute min, max, sum and med from all jobs in this CE
			for (final Iterator<Map.Entry<String, JobInfo>> jit = htJobsInCE.entrySet().iterator(); jit.hasNext();) {
				final Map.Entry<String, JobInfo> jme = jit.next();
				final JobInfo ji = jme.getValue();
				if (!ji.summarize(sum, min, max, jobs, htUserSum)) {
					// this job has expired and therefore this JobInfo must be deleted
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Removing jobInfo " + ji.jobID + "@" + ceName);
					jit.remove();
				}
				else {
					if (ji.values.containsKey("virtualmem")) {
						addToSortedArray(topCEJobsByVirtualMem, ji, jobComparatorVirtualMem, iTopJobsCount);

						if (ji.jobUser != null) {
							ArrayList<JobInfo> topVM = topJobsByVirtualMemPerUser.get(ji.jobUser);

							if (topVM == null) {
								topVM = new ArrayList<>();
								topJobsByVirtualMemPerUser.put(ji.jobUser, topVM);
							}

							addToSortedArray(topVM, ji, jobComparatorVirtualMem, iTopJobsCount);
						}
					}

					if (ji.values.containsKey("rss")) {
						addToSortedArray(topCEJobsByRSS, ji, jobComparatorRSS, iTopJobsCount);

						if (ji.jobUser != null) {
							ArrayList<JobInfo> topRSS = topJobsByRSSPerUser.get(ji.jobUser);

							if (topRSS == null) {
								topRSS = new ArrayList<>();
								topJobsByRSSPerUser.put(ji.jobUser, topRSS);
							}

							addToSortedArray(topRSS, ji, jobComparatorRSS, iTopJobsCount);
						}
					}
				}
			}

			med.setAsDataArray(sum);
			med.divParams(htJobsInCE.size()); // we have med

			// compute the ce statistics, if possible
			final Integer max_jobs = this.htCEsMaxJobs.get(ceName);
			if ((max_jobs != null) && (max_jobs.intValue() > 0)) {
				final double active_jobs = jobs.getParam("active_jobs");
				final double dMaxJobs = max_jobs.doubleValue();

				jobs.setParam("max_jobs", dMaxJobs);
				jobs.setParam("ce_efficiency", (active_jobs * 100.0) / dMaxJobs);
			}

			// update the allMin, allMax and allSum summaries
			min.minToDataArray(allMin);
			max.maxToDataArray(allMax);
			sum.addToDataArray(allSum);
			jobs.addToDataArray(allJobs);
			jobsCount += htJobsInCE.size();

			final Iterator<String> itMissing = sum.diffParameters(min).iterator();

			// add the missing fields to min
			while (itMissing.hasNext()) {
				final String name = itMissing.next();
				min.setParam(name, 0);
			}

			final String sClusterSummary = StringFactory.get(ceName + "_Jobs_Summary");

			// now we have to build Result-s from min,max,sum and med
			addRezFromDA(rez, sClusterSummary, "min", min);
			addRezFromDA(rez, sClusterSummary, "max", max);
			addRezFromDA(rez, sClusterSummary, "sum", sum);
			addRezFromDA(rez, sClusterSummary, "med", med);
			addRezFromDA(rez, sClusterSummary, "jobs", jobs);

			addRezFromSortedArray(rez, sClusterSummary, topCEJobsByVirtualMem, jobComparatorVirtualMem, null);
			addRezFromSortedArray(rez, sClusterSummary, topCEJobsByRSS, jobComparatorRSS, null);

			// update the list of known job states
			this.knownJobStates.setAsDataArray(jobs);
			this.knownJobStates.setToZero();
			this.knownJobParams.setAsDataArray(sum);
			this.knownJobParams.setToZero();

			for (int i = 0; i < topCEJobsByVirtualMem.size(); i++) {
				final JobInfo ji = topCEJobsByVirtualMem.get(i);

				addToSortedArray(topJobsByVirtualMem, ji, jobComparatorVirtualMem, iTopJobsCount);
			}

			for (int i = 0; i < topCEJobsByRSS.size(); i++) {
				final JobInfo ji = topCEJobsByRSS.get(i);

				addToSortedArray(topJobsByRSS, ji, jobComparatorRSS, iTopJobsCount);
			}
		}

		// prepare site statistics
		allMed.setAsDataArray(allSum);
		allMed.divParams(jobsCount);

		allJobs.removeParam("ce_efficiency");

		final double max_jobs = allJobs.getParam("max_jobs");
		if (max_jobs > 0) {
			final double active_jobs = allJobs.getParam("active_jobs");
			allJobs.setParam("site_efficiency", (active_jobs * 100.0) / max_jobs);
		}

		final String sSiteSummary = "Site_Jobs_Summary";

		// build the corresponding Results
		addRezFromDA(rez, sSiteSummary, "min", allMin);
		addRezFromDA(rez, sSiteSummary, "max", allMax);
		addRezFromDA(rez, sSiteSummary, "sum", allSum);
		addRezFromDA(rez, sSiteSummary, "med", allMed);
		addRezFromDA(rez, sSiteSummary, "jobs", allJobs);

		addRezFromSortedArray(rez, sSiteSummary, topJobsByVirtualMem, jobComparatorVirtualMem, null);
		addRezFromSortedArray(rez, sSiteSummary, topJobsByRSS, jobComparatorRSS, null);

		Iterator<Map.Entry<String, ArrayList<AliEnFilter.JobInfo>>> it = topJobsByVirtualMemPerUser.entrySet().iterator();

		final String OFFENDERS = "Jobs_Memory_Offenders";

		while (it.hasNext()) {
			final Map.Entry<String, ArrayList<AliEnFilter.JobInfo>> me = it.next();

			final String sUser = me.getKey();
			final ArrayList<JobInfo> jobs = me.getValue();

			addRezFromSortedArray(rez, OFFENDERS, jobs, jobComparatorVirtualMem, "_" + sUser);
		}

		it = topJobsByRSSPerUser.entrySet().iterator();

		while (it.hasNext()) {
			final Map.Entry<String, ArrayList<AliEnFilter.JobInfo>> me = it.next();

			final String sUser = me.getKey();
			final ArrayList<JobInfo> jobs = me.getValue();

			addRezFromSortedArray(rez, OFFENDERS, jobs, jobComparatorRSS, "_" + sUser);
		}

		// and the user jobs' summaries
		for (final Entry<String, DataArray> usme : htUserSum.entrySet()) {
			final String user = usme.getKey();
			final DataArray sum = usme.getValue();
			addRezFromDA(rez, "Site_UserJobs_Summary", user, sum);
		}
	}

	/**
	 * summarize data from all jobs in each ORG and produce a vector with Results. $ORG_CS_Jobs_Summary + jobs -> states of jobs waiting in central services $ORG_Sites_Jobs_Summary + Site1..N -> for
	 * each site, states of jobs running .. saving in that site $ORG_Users_Jobs_Summary + User1..N -> for each user, states of submitted jobs (how many jobs has the user in each state)
	 */
	private void summarizeJobStatusCS(final List<Serializable> rez) {
		// statistics for all Organisations that report here
		final DataArray daOrgSitesJobs = new DataArray(this.knownJobStatusSite);
		final DataArray daOrgCSJobs = new DataArray(this.knownJobStatusCS);
		final Hashtable<String, DataArray> htOrgJobsSummary = new Hashtable<>();
		final DataArray daOrgUsersJobs = new DataArray(this.knownUserJobsParams);
		final Hashtable<String, DataArray> htUsersJobsSummary = new Hashtable<>();
		final Hashtable<String, DataArray> htOrgSitesStatusTimeDeltas = new Hashtable<>();
		final Hashtable<String, DataArray> htUsersStatusTimeDeltas = new Hashtable<>();

		// go and analyze jobs in all ORGs
		for (final Entry<String, Hashtable<String, JobStatusCS>> ome : this.htOrgCSJobStats.entrySet()) {
			final String orgName = ome.getKey();
			final Hashtable<String, JobStatusCS> htJobsInOrg = ome.getValue();

			// we will build statistics per site and a for all jobs
			htOrgJobsSummary.clear();
			daOrgSitesJobs.setToZero();
			daOrgCSJobs.setToZero();
			daOrgUsersJobs.setToZero();
			htUsersJobsSummary.clear();
			htOrgSitesStatusTimeDeltas.clear();
			htUsersStatusTimeDeltas.clear();

			// summarize jobs
			for (final Iterator<Entry<String, JobStatusCS>> jit = htJobsInOrg.entrySet().iterator(); jit.hasNext();) {
				final Entry<String, JobStatusCS> jme = jit.next();
				final JobStatusCS jscs = jme.getValue();

				if (!jscs.summarize(htOrgJobsSummary, daOrgCSJobs, htUsersJobsSummary, htOrgSitesStatusTimeDeltas, htUsersStatusTimeDeltas)) {
					// this job has expired and therefore this JobStatusCS must be deleted
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Removing JobStatusCS " + jscs.jobID + "@" + orgName);
					jit.remove();
				}
			}

			// build results
			addRezFromDA(rez, orgName + "_CS_Jobs_Summary", "jobs", daOrgCSJobs);

			for (final Entry<String, DataArray> ssme : htOrgJobsSummary.entrySet()) {
				final String site = ssme.getKey();
				final DataArray jobStats = ssme.getValue();
				addRezFromDA(rez, orgName + "_Sites_Jobs_Summary", site, jobStats);
				jobStats.addToDataArray(daOrgSitesJobs);
			}
			addRezFromDA(rez, orgName + "_Sites_Jobs_Summary", "_TOTALS_", daOrgSitesJobs);

			for (final Entry<String, DataArray> ume : htUsersJobsSummary.entrySet()) {
				final String user = ume.getKey();
				final DataArray userStat = ume.getValue();
				addRezFromDA(rez, orgName + "_Users_Jobs_Summary", user, userStat);
				userStat.addToDataArray(daOrgUsersJobs);
			}
			addRezFromDA(rez, orgName + "_Users_Jobs_Summary", "_TOTALS_", daOrgUsersJobs);

			for (final Entry<String, DataArray> ssme : htOrgSitesStatusTimeDeltas.entrySet()) {
				final String site = ssme.getKey();
				final DataArray daTimeDeltas = ssme.getValue();

				final String[] vsParams = daTimeDeltas.getSortedParameters();

				for (int i = 0; i < (vsParams.length - 1); i += 2) {
					final String sAVG = vsParams[i];
					final String sCNT = vsParams[i + 1];

					final double dAVG = daTimeDeltas.getParam(sAVG);
					final double dCNT = daTimeDeltas.getParam(sCNT);

					if (dCNT > 0)
						daTimeDeltas.setParam(sAVG, dAVG / dCNT);
					else {
						logger.warning("Found timeDelta with zero cnt for site=" + site + " with " + sAVG + " = " + dAVG + " and " + sCNT + " = " + dCNT);
						// this should not happen!
						daTimeDeltas.setParam(sAVG, 0);
					}
				}

				if (daTimeDeltas.size() > 0)
					// particularity of these results: They should be published
					// only if they contain meaningful data, because they will be
					// used in histograms
					addRezFromDA(rez, orgName + "_Sites_StatusTransit_Summary", site, daTimeDeltas);
			}

			for (final Entry<String, DataArray> usme : htUsersStatusTimeDeltas.entrySet()) {
				final String user = usme.getKey();
				final DataArray daTimeDeltas = usme.getValue();

				final String[] vsParams = daTimeDeltas.getSortedParameters();

				for (int i = 0; i < (vsParams.length - 1); i += 2) {
					final String sAVG = vsParams[i];
					final String sCNT = vsParams[i + 1];

					final double dAVG = daTimeDeltas.getParam(sAVG);
					final double dCNT = daTimeDeltas.getParam(sCNT);

					if (dCNT > 0)
						daTimeDeltas.setParam(sAVG, dAVG / dCNT);
					else {
						logger.warning("Found timeDelta with zero cnt for user=" + user + " with " + sAVG + " = " + dAVG + " and " + sCNT + " = " + dCNT);
						// this should not happen!
						daTimeDeltas.setParam(sAVG, 0);
					}

				}

				if (daTimeDeltas.size() > 0)
					// particularity of these results: They should be published
					// only if they contain meaningful data, because they will be
					// used in histograms
					addRezFromDA(rez, orgName + "_Users_StatusTransit_Summary", user, daTimeDeltas);
			}

			// update the list of known job states
			this.knownJobStatusCS.setAsDataArray(daOrgCSJobs);
			this.knownJobStatusCS.setToZero();
			this.knownJobStatusSite.setAsDataArray(daOrgSitesJobs);
			this.knownJobStatusSite.setToZero();
		}
	}

	/**
	 * summarize data from all transfers in each ORG and produce a vector with Results. $ORG_CS_Transfers_Summary + transfers -> states of transfers as in central services $ORG_SEs_Transfers_Summary +
	 * SE1-SE2... -> for each pair SE1-SE2, rates and absolute values of the transfers from SE1 to SE2
	 */
	private void summarizeTransferStatusCS(final List<Serializable> rez) {
		// statistics for all Organisations that report here
		final DataArray daOrgTotalTransfers = new DataArray(this.knownTransferParams);
		final DataArray daOrgTotalTransfersStatus = new DataArray(this.knownTransferStatusCS);

		// go and analyze transfers in all ORGs
		for (final Entry<String, Hashtable<String, TransferStatusCS>> ome : this.htOrgCSTransferStats.entrySet()) {
			final String orgName = ome.getKey();
			final Hashtable<String, TransferStatusCS> htTransfersInOrg = ome.getValue();

			// we will build the two statistics (status and rates) for all transfers
			// htOrgTransfersStatusSummary.clear();
			// htOrgTransfersSESummary.clear();
			daOrgTotalTransfersStatus.setToZero();
			daOrgTotalTransfers.setToZero();

			// summarize transfers
			for (final Iterator<Entry<String, TransferStatusCS>> tit = htTransfersInOrg.entrySet().iterator(); tit.hasNext();) {
				final Entry<String, TransferStatusCS> jme = tit.next();
				final TransferStatusCS tscs = jme.getValue();
				if (!tscs.summarize(this.htOrgTransfersStatusSummary, this.htOrgTransfersSESummary)) {
					// this transfer has expired and therefore this TransferStatusCS must be deleted
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Removing TransferStatusCS " + tscs.transferID + "@" + orgName);
					tit.remove();
				}
			}

			// build results
			for (final Entry<String, DataArray> ssme : this.htOrgTransfersStatusSummary.entrySet()) {
				final String SE1_SE2 = ssme.getKey();
				final DataArray transfStats = ssme.getValue();
				addRezFromDA(rez, orgName + "_CS_Transfers_Summary", SE1_SE2, transfStats);
				transfStats.addToDataArray(daOrgTotalTransfersStatus);
				if (!transfStats.isZero())
					transfStats.setToZero();
				// else
				// sit.remove(); // we already set it once to zero; if still not updated, we can remove it
			}
			addRezFromDA(rez, orgName + "_CS_Transfers_Summary", "_TOTALS_", daOrgTotalTransfersStatus);

			for (final Entry<String, DataArray> ssme : this.htOrgTransfersSESummary.entrySet()) {
				final String SE1_SE2 = ssme.getKey();
				final DataArray transfStats = ssme.getValue();
				addRezFromDA(rez, orgName + "_SEs_Transfers_Summary", SE1_SE2, transfStats);
				transfStats.addToDataArray(daOrgTotalTransfers);
				if (!transfStats.isZero())
					transfStats.setToZero();
				// else
				// sit.remove(); // we already set it once to zero; if still not updated, we can remove it
			}
			addRezFromDA(rez, orgName + "_SEs_Transfers_Summary", "_TOTALS_", daOrgTotalTransfers);
		}

		// update the list of known job states
		this.knownTransferStatusCS.setAsDataArray(daOrgTotalTransfersStatus);
		this.knownTransferStatusCS.setToZero();
		this.knownTransferParams.setAsDataArray(daOrgTotalTransfers);
		this.knownTransferParams.setToZero();
	}

	/** summarize all data from all nodes in each Site and produce a vector with Results */
	private void summarizeNodes(final List<Serializable> rez) {
		// statistics for all Sites that report here
		final DataArray allMin = new DataArray();
		final DataArray allMax = new DataArray(this.knownNodeParams);
		final DataArray allSum = new DataArray(this.knownNodeParams);
		final DataArray allMed = new DataArray(this.knownNodeParams);
		int nodesCount = 0;

		// go and summarize all known sites
		for (final Entry<String, Hashtable<String, NodeInfo>> sme : this.htSites.entrySet()) {
			final String siteName = sme.getKey();
			final Hashtable<String, NodeInfo> htNodesInSite = sme.getValue();

			final DataArray min = new DataArray();
			final DataArray max = new DataArray();
			final DataArray sum = new DataArray();
			final DataArray med = new DataArray();

			for (final Iterator<Entry<String, NodeInfo>> nit = htNodesInSite.entrySet().iterator(); nit.hasNext();) {
				final Entry<String, NodeInfo> nme = nit.next();
				final NodeInfo ni = nme.getValue();
				if (!ni.summarize(sum, min, max)) {
					// this node has expired and therefore this NodeInfo must be deleted
					if (logger.isLoggable(Level.FINEST))
						logger.log(Level.FINEST, "Removing nodeInfo " + ni.host + "@" + ni.site);
					nit.remove();
				}
				else
					// add the computed job-related results for this node
					ni.addJobRez(rez);
			}

			med.setAsDataArray(sum);
			med.divParams(htNodesInSite.size()); // we have med

			final Iterator<String> itMissing = sum.diffParameters(min).iterator();

			// add the missing fields to min
			while (itMissing.hasNext()) {
				final String name = itMissing.next();
				min.setParam(name, 0);
			}

			min.minToDataArray(allMin);
			max.maxToDataArray(allMax);
			sum.addToDataArray(allSum);
			nodesCount += htNodesInSite.size();

			// now we have to build Result-s from min,max,sum and med
			addRezFromDA(rez, siteName + "_Nodes_Summary", "min", min);
			addRezFromDA(rez, siteName + "_Nodes_Summary", "max", max);
			addRezFromDA(rez, siteName + "_Nodes_Summary", "sum", sum);
			addRezFromDA(rez, siteName + "_Nodes_Summary", "med", med);

			this.knownNodeParams.setAsDataArray(sum);
			this.knownNodeParams.setToZero();
		}

		// prepare site statistics
		allMed.setAsDataArray(allSum);
		allMed.divParams(nodesCount);

		// build the corresponding Results
		addRezFromDA(rez, "Site_Nodes_Summary", "min", allMin);
		addRezFromDA(rez, "Site_Nodes_Summary", "max", allMax);
		addRezFromDA(rez, "Site_Nodes_Summary", "sum", allSum);
		addRezFromDA(rez, "Site_Nodes_Summary", "med", allMed);
	}

	/** convert to results all data for Sites traffic */
	private void summarizeSitesTraffic(final List<Serializable> rez) {
		final DataArray da = new DataArray();

		for (final Iterator<Entry<String, XrdInfo>> sit = this.htSitesTraffic.entrySet().iterator(); sit.hasNext();) {
			final Entry<String, XrdInfo> sme = sit.next();
			final String siteName = sme.getKey();
			final XrdInfo xi = sme.getValue();

			if (!xi.summarize(da)) {
				sit.remove(); // node expired
				addRezFromDA(rez, "Site_Traffic_Summary", siteName, null);
			}
			else
				addRezFromDA(rez, "Site_Traffic_Summary", siteName, da);
		}
	}

	/** convert to results all data for SEs traffic */
	private void summarizeSEsTraffic(final List<Serializable> rez) {
		final DataArray da = new DataArray();

		for (final Iterator<Entry<String, XrdInfo>> seit = this.htSEsTraffic.entrySet().iterator(); seit.hasNext();) {
			final Entry<String, XrdInfo> seme = seit.next();
			final String seName = seme.getKey();
			final XrdInfo xi = seme.getValue();

			if (!xi.summarize(da)) {
				seit.remove(); // node expired
				addRezFromDA(rez, "SE_Traffic_Summary", seName, null);
			}
			else
				addRezFromDA(rez, "SE_Traffic_Summary", seName, da);
		}
	}

	/** Summarizes Castorgrid servers traffic providing per node and totals summaries */
	private void summarizeCastorgridTraffic(final List<Serializable> rez) {
		final DataArray sumTotals = new DataArray(); // totals for all castorgrid servers
		final DataArray maxTotals = new DataArray();
		final DataArray nodeDA = new DataArray(); // summarized values for a single node
		final DataArray maxDA = new DataArray();

		for (final Iterator<Entry<String, RfcpInfo>> csit = this.htCastorgridServers.entrySet().iterator(); csit.hasNext();) {
			final Entry<String, RfcpInfo> csme = csit.next();
			final String hostName = csme.getKey();
			final RfcpInfo ri = csme.getValue();

			nodeDA.clear();
			maxDA.clear();
			if (!ri.summarize(nodeDA, maxDA)) {
				csit.remove(); // node expired; remove it
				addRezFromDA(rez, "CastorGrid_scripts_Summary", hostName, null);
			}
			else {
				maxDA.maxToDataArray(maxTotals); // set the maximum in totals
				nodeDA.addToDataArray(sumTotals); // add the other values in totals
				// now we can report for this node
				maxDA.addToDataArray(nodeDA);
				addRezFromDA(rez, "CastorGrid_scripts_Summary", hostName, nodeDA);
			}
		}
		maxTotals.addToDataArray(sumTotals);
		addRezFromDA(rez, "CastorGrid_scripts_Summary", "_TOTALS_", sumTotals);
	}

	/**
	 * Summarizes the Root Jobs, providing summaries per host, per read-destination and per dest-host read details.
	 */
	private void summarizeRootJobs(final List<Serializable> rez) {
		final DataArray daHostTotals = new DataArray(this.knownRootJobsHostParams);
		final DataArray daDestTotals = new DataArray(this.knownRootJobsDestParams);
		final Hashtable<String, DataArray> hostStats = new Hashtable<>();
		final Hashtable<String, DataArray> destStats = new Hashtable<>();
		final Hashtable<String, DataArray> destToHostsStats = new Hashtable<>();

		for (final Iterator<Entry<String, Hashtable<String, RootJobInfo>>> sit = this.htSitesRootJobs.entrySet().iterator(); sit.hasNext();) {
			final Entry<String, Hashtable<String, RootJobInfo>> sme = sit.next();
			final String fName = sme.getKey();
			final Hashtable<String, RootJobInfo> htRootJobsInFarm = sme.getValue();

			daHostTotals.setToZero();
			daDestTotals.setToZero();
			hostStats.clear();
			destStats.clear();
			destToHostsStats.clear();

			// compute the summary for this farm
			int aliveJobs = 0;
			for (final Iterator<RootJobInfo> rjit = htRootJobsInFarm.values().iterator(); rjit.hasNext();) {
				final RootJobInfo rj = rjit.next();

				if (!rj.summarize(hostStats, destStats, destToHostsStats))
					rjit.remove();
				else
					aliveJobs++;
			}
			if (aliveJobs == 0)
				sit.remove(); // don't keep reporting the totals for dead things.

			// report hostStats and compute the total
			int aliveHosts = 0;
			for (final Entry<String, DataArray> hsme : hostStats.entrySet()) {
				final String host = hsme.getKey();
				final DataArray params = hsme.getValue();
				params.addToDataArray(daHostTotals);
				addRezFromDA(rez, "ROOT_" + fName + "_hosts_Summary", host, params);
				aliveHosts++;
			}

			final double cpuUsage = daHostTotals.getParam("cpu_usage");

			if (aliveHosts > 0)
				daHostTotals.setParam("cpu_usage", cpuUsage / aliveHosts);

			final int clusterSize = AppConfig.geti("lia.Monitor.Filters.AliEnFilter.ROOT_CLUSTER_SIZE", aliveHosts);

			daHostTotals.setParam("alive_hosts", aliveHosts);
			daHostTotals.setParam("cluster_size", clusterSize);
			daHostTotals.setParam("cluster_usage", cpuUsage / clusterSize);
			addRezFromDA(rez, "ROOT_" + fName + "_hosts_Summary", "_TOTALS_", daHostTotals);

			// report destStats and compute the total
			for (final Entry<String, DataArray> dsme : destStats.entrySet()) {
				final String dest = dsme.getKey();
				final DataArray params = dsme.getValue();
				params.addToDataArray(daDestTotals);
				addRezFromDA(rez, "ROOT_" + fName + "_dests_Summary", dest, params);
			}
			addRezFromDA(rez, "ROOT_" + fName + "_dests_Summary", "_TOTALS_", daDestTotals);

			// report destToHostsStats; no total is needed
			for (final Entry<String, DataArray> dhsme : destToHostsStats.entrySet()) {
				final String dest2host = dhsme.getKey();
				final DataArray params = dhsme.getValue();
				addRezFromDA(rez, "ROOT_" + fName + "_destToHost_Summary", dest2host, params);
			}
		}
	}

	/**
	 * Summarize the api services, by adding for each farm a _TOTALS_ node with the sum for its parameters
	 */
	private void summarizeAPIServices(final List<Serializable> rez) {
		final DataArray sum = new DataArray();

		for (final Iterator<Entry<String, Hashtable<String, APIServiceInfo>>> sit = this.htSitesAPIServices.entrySet().iterator(); sit.hasNext();) {
			final Entry<String, Hashtable<String, APIServiceInfo>> sme = sit.next();
			final String fName = sme.getKey();
			final Hashtable<String, APIServiceInfo> htAPIServicesInFarm = sme.getValue();

			sum.clear();
			boolean noApiService = true;
			// compute the summary for this farm
			for (final Iterator<APIServiceInfo> asit = htAPIServicesInFarm.values().iterator(); asit.hasNext();) {
				final APIServiceInfo api = asit.next();
				noApiService = false;
				if (!api.summarize(sum))
					asit.remove();
			}
			// if no api service in this farm, don't report anything
			if (noApiService)
				sit.remove();
			else
				addRezFromDA(rez, fName + "_ApiService", "_TOTALS_", sum);
		}
	}

	/** Summarize the status for all job agents on all sites that report here */
	private void summarizeJobAgents(final List<Serializable> rez) {
		final DataArray stats = new DataArray(this.knownJobAgentParams);
		final DataArray ttlSum = new DataArray();

		for (final Entry<String, Hashtable<String, JobAgentInfo>> sme : this.htSitesJobAgents.entrySet()) {
			final String fName = sme.getKey();
			final Hashtable<String, JobAgentInfo> htJobAgentsInFarm = sme.getValue();

			stats.setToZero();
			ttlSum.clear();
			// compute the summary for this farm
			for (final Iterator<JobAgentInfo> jait = htJobAgentsInFarm.values().iterator(); jait.hasNext();) {
				final JobAgentInfo ja = jait.next();
				if (!ja.summarize(stats, ttlSum))
					jait.remove();
			}

			addRezFromDA(rez, fName + "_JobAgent_Summary", "status", stats);
			final int ttlCount = (int) ttlSum.getParam("ttl_count");
			if (ttlCount > 0) {
				ttlSum.removeParam("ttl_count");
				ttlSum.divParams(ttlCount);
				addRezFromDA(rez, fName + "_JobAgent_Summary", "avg", ttlSum);
			}
		}

		final long lNow = NTPDate.currentTimeMillis();

		for (final Entry<String, Histogram> sit : this.htSitesJobAgentsNumJobs.entrySet()) {
			final Histogram h = sit.getValue();

			synchronized (h) {
				final int size = h.size();

				final Result r = new Result(this.farm.name, sit.getKey() + "_JobAgent_Summary", "histograms", "monXDRUDP");

				r.time = lNow;

				if (size > 0) {
					h.fill(r);

					final int count = h.count();

					final double timeInterval = AF_SLEEP_TIME / 1000.0d;

					r.addSet(h.getName() + "_ja_end_R", count / timeInterval);

					final long volume = h.volume();

					r.addSet(h.getName() + "_jobs_end_R", volume / timeInterval);

					h.clearValues();
				}
				else {
					r.addSet(h.getName() + "_ja_end_R", 0);
					r.addSet(h.getName() + "_jobs_end_R", 0);
				}

				rez.add(r);
			}
		}
	}

	/** Summarize the number of JobAgents as seen by the AliEN CEs on each site */
	private void summarizeCEs(final List<Serializable> rez) {
		final DataArray sumJA = new DataArray();
		for (final Entry<String, Hashtable<String, CEInfo>> sme : this.htSitesCEs.entrySet()) {
			final String fName = sme.getKey();
			final Hashtable<String, CEInfo> htCEsInFarm = sme.getValue();
			sumJA.clear();
			// compute the summary for this farm
			for (final Iterator<CEInfo> ceit = htCEsInFarm.values().iterator(); ceit.hasNext();) {
				final CEInfo ce = ceit.next();
				if (!ce.summarize(sumJA))
					ceit.remove();
			}

			if (sumJA.size() > 0)
				addRezFromDA(rez, fName + "_JobAgent_Summary", "ce", sumJA);
		}
	}

	/** Summarize the DAQ statistics on each site */
	private void summarizeDAQ(final List<Serializable> rez) {
		final DataArray daqDA = new DataArray();
		for (final Iterator<Entry<String, Hashtable<String, DAQInfo>>> sit = this.htSitesDAQ.entrySet().iterator(); sit.hasNext();) {
			final Entry<String, Hashtable<String, DAQInfo>> sme = sit.next();
			final String fName = sme.getKey();
			final Hashtable<String, DAQInfo> htDAQInFarm = sme.getValue();
			// compute the summary for each daq in this farm
			for (final Iterator<DAQInfo> daqit = htDAQInFarm.values().iterator(); daqit.hasNext();) {
				final DAQInfo daq = daqit.next();
				daqDA.clear();
				if (!daq.summarize(daqDA))
					daqit.remove();
				else
					addRezFromDA(rez, fName + "_DAQ", daq.name, daqDA);
			}
			if (htDAQInFarm.size() == 0)
				sit.remove();
		}
	}

	/** Summarize the DAQ statistics on each site */
	private void summarizeFTSTransfers(final List<Serializable> rez) {
		final Hashtable<String, DataArray> channelsSummary = new Hashtable<>();

		// summarize the data about all channels
		for (final Iterator<Entry<String, Hashtable<String, FTSTransferInfo>>> ftit = this.htFTSTransfers.entrySet().iterator(); ftit.hasNext();) {
			final Entry<String, Hashtable<String, FTSTransferInfo>> sme = ftit.next();
			final Hashtable<String, FTSTransferInfo> htFTD = sme.getValue();
			// compute the summary for each transfer in this FTD/FTS
			for (final Iterator<FTSTransferInfo> ftsTrIt = htFTD.values().iterator(); ftsTrIt.hasNext();) {
				final FTSTransferInfo ftsTrInfo = ftsTrIt.next();
				if (!ftsTrInfo.summarize(channelsSummary))
					ftsTrIt.remove();
			}

			if (htFTD.size() == 0)
				ftit.remove();
		}
		// prepare and report summary for each channel
		for (final Entry<String, DataArray> cme : channelsSummary.entrySet()) {
			final String channelName = cme.getKey();
			final DataArray daChSumm = cme.getValue();

			final double count = daChSumm.getParam("_TOTALS__transfers");

			if (count > 0.5) {
				final Iterator<String> it = daChSumm.parameterSet().iterator();

				while (it.hasNext()) {
					final String name = it.next();

					if (name.endsWith("_avg_time"))
						daChSumm.setParam(name, daChSumm.getParam(name) / count);
				}
			}

			addRezFromDA(rez, "FTS_Channels_Summary", channelName, daChSumm);
		}
	}

	/** Summarize FDT Test Transfers */
	private void summarizeFDTTestTransfers(final List<Serializable> rez) {
		final Hashtable<String, DataArray> htFDTLinksSummary = new Hashtable<>();
		// summarize the info for all FDT Test Transfers
		for (final Iterator<FDTTestTransferInfo> ftit = this.htFDTTestTransfers.values().iterator(); ftit.hasNext();) {
			final FDTTestTransferInfo fdtTr = ftit.next();
			if (!fdtTr.summarize(htFDTLinksSummary))
				ftit.remove();
		}
		for (final Entry<String, DataArray> lme : htFDTLinksSummary.entrySet()) {
			final String linkName = lme.getKey();
			final DataArray params = lme.getValue();
			addRezFromDA(rez, "FDTTestTransfers_Summary", linkName, params);
		}
	}

	/** Summarize QuotaInfos */
	private void summarizeQuotaInfos(final List<Serializable> rez) {
		final Hashtable<String, DataArray> htQuotaSummary = new Hashtable<>();
		// summarize the info for all QuotaInfos
		for (final Iterator<QuotaInfo> qit = this.htQuotaInfos.values().iterator(); qit.hasNext();) {
			final QuotaInfo quotaInfo = qit.next();
			if (!quotaInfo.summarize(htQuotaSummary))
				qit.remove();
		}

		final DataArray daQuotaSum = new DataArray();
		for (final Entry<String, DataArray> lme : htQuotaSummary.entrySet()) {
			final String user_reqType = lme.getKey();
			final DataArray params = lme.getValue();
			addRezFromDA(rez, "CERN_QUOTA_Summary", user_reqType, params);
			params.addToDataArray(daQuotaSum);
		}
		if (htQuotaSummary.size() > 0)
			addRezFromDA(rez, "CERN_QUOTA_Summary", "_TOTALS_", daQuotaSum);
	}

	/** Summarize RootCafUsageInfos */
	private void summarizeRootCafUsageInfos(final List<Serializable> rez) {
		final Hashtable<String, DataArray> htUserSummaries = new Hashtable<>();
		final Hashtable<String, DataArray> htGroupSummaries = new Hashtable<>();
		for (final Iterator<Entry<String, Hashtable<String, RootCafUsageInfo>>> cit = this.htRootCafUsageInfos.entrySet().iterator(); cit.hasNext();) {
			final Entry<String, Hashtable<String, RootCafUsageInfo>> cme = cit.next();
			final String cluster = cme.getKey();
			final Hashtable<String, RootCafUsageInfo> htInfos = cme.getValue();
			if (htInfos.size() == 0) {
				cit.remove();
				continue;
			}
			htUserSummaries.clear();
			htGroupSummaries.clear();
			for (final Iterator<RootCafUsageInfo> iit = htInfos.values().iterator(); iit.hasNext();) {
				final RootCafUsageInfo info = iit.next();
				if (!info.summarize(htUserSummaries, htGroupSummaries))
					iit.remove();
			}

			final DataArray daInfoSum = new DataArray();
			for (final Entry<String, DataArray> sme : htUserSummaries.entrySet()) {
				final String user = sme.getKey();
				final DataArray values = sme.getValue();
				addRezFromDA(rez, cluster + "_Summary_user", user, values);
				values.addToDataArray(daInfoSum);
			}

			if (htUserSummaries.size() > 0)
				addRezFromDA(rez, cluster + "_Summary_user", "_TOTALS_", daInfoSum);

			daInfoSum.clear();

			for (final Entry<String, DataArray> sme : htGroupSummaries.entrySet()) {
				final String group = sme.getKey();
				final DataArray values = sme.getValue();
				addRezFromDA(rez, cluster + "_Summary_group", group, values);
				values.addToDataArray(daInfoSum);
			}

			if (htGroupSummaries.size() > 0)
				addRezFromDA(rez, cluster + "_Summary_group", "_TOTALS_", daInfoSum);
		}
	}

	/** Summarize XrdServerInfos */
	private void summarizeXrdServerInfos(final List<Serializable> rez) {
		final Hashtable<String, DataArray> htSummaries = new Hashtable<>();
		for (final Iterator<XrdServerInfo> sit = this.htXrdServers.values().iterator(); sit.hasNext();) {
			final XrdServerInfo xrd = sit.next();
			if (!xrd.summarize(htSummaries))
				sit.remove();
		}

		final DataArray total = new DataArray();
		for (final Entry<String, DataArray> sme : htSummaries.entrySet()) {
			final String host = sme.getKey();
			final DataArray values = sme.getValue();
			addRezFromDA(rez, "XrdServers_Summary", host, values);
			values.addToDataArray(total);
		}

		if (htSummaries.size() > 0)
			addRezFromDA(rez, "XrdServers_Summary", "_TOTALS_", total);
	}

	/** convert to results all data for SEs traffic */
	private void summarizeSEsAliEnTraffic(final List<Serializable> rez) {
		final DataArray da = new DataArray();

		for (final Iterator<Entry<String, SETransferInfo>> seit = this.htAliEnSEs.entrySet().iterator(); seit.hasNext();) {
			final Entry<String, SETransferInfo> seme = seit.next();
			final String seName = seme.getKey();
			final SETransferInfo sei = seme.getValue();

			if (!sei.summarize(da)) {
				seit.remove(); // node expired
				addRezFromDA(rez, "SE_AliEnTraffic_Summary", seName, null);
				addRezFromDA(rez, "Site_SE_AliEnTraffic_Summary", this.farmName + "-" + seName, null);
			}
			else {
				addRezFromDA(rez, "SE_AliEnTraffic_Summary", seName, da);
				addRezFromDA(rez, "Site_SE_AliEnTraffic_Summary", this.farmName + "-" + seName, da);
			}
		}
	}

	/**
	 * Build a Result from the given DataArray into the appropriate cluster/node; also returns the result. If the given DataArray is null, it returns a expire result for that node If nodeName is null,
	 * it returns the expire result for that cluster
	 *
	 * @param vrez
	 * @param clusterName
	 * @param nodeName
	 * @param da
	 * @return result
	 */
	Result addRezFromDA(final List<Serializable> vrez, final String clusterName, final String nodeName, final DataArray da) {
		if (nodeName == null) {
			final eResult er = new eResult(this.farm.name, clusterName, null, "AliEnFilter", null);
			er.time = NTPDate.currentTimeMillis();
			vrez.add(er);
			return null;
		}

		if (da == null) {
			final eResult er = new eResult(this.farm.name, clusterName, nodeName, "AliEnFilter", null);
			er.time = NTPDate.currentTimeMillis();
			vrez.add(er);
			return null;
		}

		if (da.size() != 0) {
			final Result rez = new Result(this.farm.name, clusterName, nodeName, "monXDRUDP", da.getParameters());

			for (int i = rez.param_name.length - 1; i >= 0; i--)
				rez.param[i] = da.getParam(rez.param_name[i]);

			rez.time = NTPDate.currentTimeMillis();
			vrez.add(rez);
			return rez;
		}

		return null;
	}

	private static void reloadConfigParameters() {
		PARAM_EXPIRE = AppConfig.getl("lia.Monitor.Filters.AliEnFilter.PARAM_EXPIRE", 900) * 1000;

		ROOT_JOB_TOTALS = AppConfig.getb("lia.Monitor.Filters.AliEnFilter.ROOT_JOBS_TOTALS", false);

		ROOT_SUMMARY_PER_SUBJOB = AppConfig.getb("lia.Monitor.Filters.AliEnFilter.ROOT_SUMMARY_PER_SUBJOB", false);
	}

	/** Dump the accumulated results */
	@Override
	synchronized public Object expressResults() {
		logger.log(Level.FINE, "expressResults was called");

		reloadConfigParameters();

		final Vector<Serializable> rez = new Vector<>();
		try {
			summarizeJobs(rez); // this must be called before summarizeNodes !
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeJobs()", t);
		}
		try {
			summarizeJobStatusCS(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeJobStatusCS()", t);
		}
		try {
			summarizeTransferStatusCS(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeTransferStatusCS()", t);
		}
		try {
			summarizeNodes(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeNodes()", t);
		}
		try {
			summarizeSitesTraffic(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeSitesTraffic()", t);
		}
		try {
			summarizeSEsTraffic(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeSEsTraffic()", t);
		}
		try {
			summarizeCastorgridTraffic(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeCastorgridTraffic()", t);
		}
		try {
			summarizeRootJobs(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeRootJobs()", t);
		}
		try {
			summarizeAPIServices(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeAPIServices()", t);
		}
		try {
			summarizeJobAgents(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeJobAgents()", t);
		}
		try {
			summarizeCEs(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeCEs()", t);
		}
		try {
			summarizeDAQ(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeDAQ()", t);
		}
		try {
			summarizeFTSTransfers(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeFTSTransfers()", t);
		}
		try {
			summarizeFDTTestTransfers(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeFDTTestTransfers()", t);
		}
		try {
			summarizeQuotaInfos(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeQuotaInfos()", t);
		}
		try {
			summarizeRootCafUsageInfos(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeRootCafUsageInfos()", t);
		}
		try {
			summarizeXrdServerInfos(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeXrdServerInfos()", t);
		}
		try {
			summarizeSEsAliEnTraffic(rez);
		}
		catch (final Throwable t) {
			logger.log(Level.WARNING, "Error doing summarizeSEsAliEnTraffic()", t);
		}

		// rez.addAll(vDebugResults);

		// vDebugResults.clear();

		if (logger.isLoggable(Level.FINEST)) {
			final StringBuilder sb = new StringBuilder();
			for (final Serializable serializable : rez)
				sb.append(serializable.toString() + "\n");

			logger.log(Level.FINEST, "Summarised results are: " + sb.toString());
		}

		if (logger.isLoggable(Level.FINE)) {
			DataArray.setCollectStatistics(true);

			final Map<Integer, AtomicInteger> m = DataArray.getStats();

			final StringBuilder sb = new StringBuilder();

			long lTotalCount = 0;

			for (final AtomicInteger ai : m.values())
				lTotalCount += ai.get();

			long lSum = 0;

			for (final Entry<Integer, AtomicInteger> me : m.entrySet()) {
				final int value = me.getValue().get();

				lSum += value;

				sb.append(me.getKey()).append("\t->\t").append(value).append("\t(").append((lSum * 100) / lTotalCount).append("%)\n");
			}

			logger.log(Level.FINE, "DataArray size distribution:\n" + sb);

			if (AppConfig.getb("lia.Monitor.Filters.AliEnFilter.resetDataArrayStats", true))
				DataArray.resetStats();
		}
		else
			DataArray.setCollectStatistics(false);

		return rez;
	}

	@Override
	public void notifyAppConfigChanged() {
		reloadConfigParameters();
	}

	private static boolean configured = false;

	private static ExtProperties dbProp = null;

	/**
	 * @return connection to the database, if any
	 */
	static DBFunctions getQueueDB() {
		if (configured) {
			if (dbProp != null)
				return new DBFunctions(dbProp);

			return null;
		}

		configured = true;

		final String pass = AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_PASSWORD");

		if ((pass == null) || (pass.length() == 0)) {
			logger.log(Level.WARNING, "No database password for direct connection");

			return null;
		}

		dbProp = new ExtProperties();

		dbProp.set("password", pass);
		dbProp.set("driver", AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_DRIVER", "com.mysql.jdbc.Driver"));
		dbProp.set("host", AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_PROCESSES_HOST", "alice-taskqueuedb.cern.ch"));
		dbProp.set("port", AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_PROCESSES_PORT", "3308"));
		dbProp.set("database", AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_PROCESSES_DBNAME", "processes"));
		dbProp.set("user", AppConfig.getGlobalEnvProperty("ALIEN_DATABASE_PROCESSES_USER", "root"));
		dbProp.set("autoReconnect", "true");
		dbProp.set("useServerPrepStmts", "true");

		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "Database details for direct connection are:\n" + dbProp.toString());

		return new DBFunctions(dbProp);
	}

	private static final GenericLastValuesCache<Integer, String> userCache = new GenericLastValuesCache<Integer, String>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected int getMaximumSize() {
			// active users are usually < 100, this size is enough to cache the collaboration
			return 2000;
		}

		@Override
		protected String resolve(final Integer key) {
			try (DBFunctions db = getQueueDB()) {
				if (db == null)
					return null;

				db.setReadOnly(true);

				db.setQueryTimeout(15);
				
				db.query("SELECT user FROM QUEUE_USER where userId=?;", false, key);

				if (db.moveNext()) {
					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "Resolved user ID " + key + " to " + db.gets(1));

					return StringFactory.get(db.gets(1));
				}
			}

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Could not resolve host id " + key);

			return null;
		}
	};

	private static final GenericLastValuesCache<Integer, String> hostCache = new GenericLastValuesCache<Integer, String>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected int getMaximumSize() {
			// the hosts (submit and exec) are very few (central services + voboxes) so even ~100 would be enough but let's make it large enough to cache WN names as well, if needed
			return 10000;
		}

		@Override
		protected String resolve(final Integer key) {
			try (DBFunctions db = getQueueDB()) {
				if (db == null)
					return null;

				db.setReadOnly(true);
				
				db.setQueryTimeout(15);

				db.query("SELECT host FROM QUEUE_HOST where hostId=?;", false, key);

				if (db.moveNext()) {
					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "Resolved host ID " + key + " to " + db.gets(1));

					return StringFactory.get(db.gets(1));
				}
			}

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Could not resolve host id " + key);

			return null;
		}
	};

	/**
	 * @param userId
	 * @return user name for the respective userId
	 */
	public static String getUserName(final int userId) {
		if (userId <= 0)
			return null;

		return userCache.get(Integer.valueOf(userId));
	}

	/**
	 * @param hostId
	 * @return host name for the respective hostId
	 */
	public static String getHost(final int hostId) {
		if (hostId <= 0)
			return null;

		return hostCache.get(Integer.valueOf(hostId));
	}

	/**
	 * @param jobId
	 * @return user name, if known
	 */
	static final String getUserName(final String jobId) {
		final Long iJobID;

		try {
			iJobID = Long.valueOf(jobId);
		}
		catch (@SuppressWarnings("unused") final NumberFormatException nfe) {
			return null;
		}

		try (DBFunctions db = getQueueDB()) {
			if (db == null)
				return null;

			db.setReadOnly(true);

			db.setQueryTimeout(15);
			
			db.query("SELECT userId FROM QUEUE WHERE queueId=?;", false, iJobID);

			if (db.moveNext())
				return userCache.get(Integer.valueOf(db.geti(1)));
		}

		return null;
	}

	static final GenericLastValuesCache<Integer, String> siteCache = new GenericLastValuesCache<Integer, String>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected int getMaximumSize() {
			// active users are usually < 100, this size is enough to cache the collaboration
			return 2000;
		}

		@Override
		protected String resolve(final Integer key) {
			try (DBFunctions db = getQueueDB()) {

				if (db == null)
					return null;

				db.setReadOnly(true);

				db.setQueryTimeout(15);
				
				db.query("select site from SITEQUEUES where siteId=?;", false, key);

				if (db.moveNext()) {
					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "Resolved site ID " + key + " to " + db.gets(1));

					return StringFactory.get(db.gets(1));
				}

				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Could not resolve site id " + key);

				return null;
			}
		}
	};

	static final GenericLastValuesCache<String, String> queueToMLCache = new GenericLastValuesCache<String, String>() {
		private static final long serialVersionUID = 1L;

		@Override
		protected int getMaximumSize() {
			return 1000;
		}

		@Override
		protected String resolve(final String queue) {
			if (queue.lastIndexOf("::") == queue.indexOf("::"))
				return "NO_SITE";

			final String site = queue.substring(queue.indexOf("::") + 2, queue.lastIndexOf("::"));
			final String qname = queue.substring(queue.lastIndexOf("::") + 2);

			String query;

			if (qname.startsWith(site))
				query = "(|(ce=" + qname + ")(ce=LCG" + (qname.substring(site.length())) + "))";
			else
				query = "(ce=" + qname + ")";

			Set<String> mlNames = LDAPHelper.checkLdapInformation(query, "ou=Config,ou=" + site + ",ou=Sites,", "monalisa");

			if (mlNames == null || mlNames.isEmpty()) {
				if (qname.startsWith(site))
					query = "(|(name=" + qname + ")(name=LCG" + (qname.substring(site.length())) + "))";
				else
					query = "(name=" + qname + ")";

				mlNames = LDAPHelper.checkLdapInformation(query, "ou=MonaLisa,ou=Services,ou=" + site + ",ou=Sites,", "name");
			}

			String mlName = site;

			if (mlNames != null && mlNames.size() > 0) {
				mlName = mlNames.iterator().next();
				if (mlName.startsWith("LCG"))
					mlName = site + mlName.substring(3);
			}

			return mlName;
		}
	};

	/**
	 * Get execution site for this job
	 *
	 * @param queueId
	 * @return site name, if known
	 */
	String getSite(final String queueId) {
		try (DBFunctions db = getQueueDB()) {
			if (db == null)
				return null;

			db.setReadOnly(true);

			db.setQueryTimeout(15);

			db.query("SELECT siteId FROM QUEUE WHERE queueId=?", false, Long.valueOf(queueId));

			if (db.moveNext()) {
				final String queueName = siteCache.get(Integer.valueOf(db.geti(1)));

				if (queueName == null)
					return null;

				if (MATCH_ML_FROM_LDAP)
					return queueToMLCache.get(queueName);

				try {
					final String site = queueName.substring(queueName.indexOf("::") + 2, queueName.lastIndexOf("::"));

					final String siteLower = site.toLowerCase();

					AliEnFilter.this.sitesCEhostsLock.readLock().lock();

					try {
						String bestClosest = null;

						for (final String candidate : AliEnFilter.this.htSitesCEhosts.keySet()) {
							if (site.equalsIgnoreCase(candidate))
								return candidate;

							if ((bestClosest == null) && candidate.toLowerCase().startsWith(siteLower))
								bestClosest = candidate;
						}

						return bestClosest;
					}
					finally {
						AliEnFilter.this.sitesCEhostsLock.readLock().unlock();
					}
				}
				catch (@SuppressWarnings("unused") final Exception e) {
					// ignore
				}
			}
		}

		return null;
	}
}
