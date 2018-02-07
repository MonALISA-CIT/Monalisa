package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.TempMemWriterInterface;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since 18.12.2006 (the comment)
 */
public class monRepositoryStats extends SchJob implements MonitoringModule {

	/**
	 * Please Eclipse, stop complaining :)
	 */
	private static final long	serialVersionUID	= -7929523928017419987L;

	private MonModuleInfo		mmi					= null;

	private MNode				mn					= null;

	private long				lLastCall			= 0;

	private double				vdData[]			= new double[20];

	/**
	 * memory store
	 */
	transient TempMemWriterInterface		tmw					= null;

	@Override
	public MonModuleInfo init(MNode node, String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("RepositoryStatsModule");
		mmi.setState(0);

		lLastCall = NTPDate.currentTimeMillis();
		mmi.lastMeasurement = lLastCall;

		try {
			final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();
			tmw = store.getTempMemWriter();
		} catch (Throwable t) {
			// ignore any exception here, tmw will be null if something is wrong in the storage engine
		}

		try {
			vdData[0] = lia.web.utils.ThreadedPage.getRequestCount();
			vdData[1] = lia.web.utils.ThreadedPage.getIPv4Requests();
			vdData[2] = lia.web.utils.ThreadedPage.getIPv6Requests();
		} catch (Throwable t) {
			// if the class cannot be found then this is not a repository distribution, ignore the error
		}

		try {
			for (int i = 0; i < lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames.length; i++)
				vdData[3 + i] = lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i];
		} catch (Throwable t) {
			// ignore the error caused by missing WS classes
		}

		// make sure the counters are reset at start so we don't get spikes at the first run
		lia.Monitor.JiniClient.Store.Main.getAndFlushSavedValues();
		lia.Monitor.JiniClient.Store.Main.getAndFlushSavedValues();
		
		return mmi;
	}

	// MonitoringModule

	@Override
	public String[] ResTypes() {
		return mmi.getResType();
	}

	@Override
	public String getOsName() {
		return "Linux";
	}
	
	private long lLastDatabaseSizeCheck = 0;
	private double dLastDatabaseSize = 0;
	private double dLastDatabasePartitionSize = 0;
	private double dLastDatabasePartitionFree = 0;
	
	@Override
	public Object doProcess() throws Exception {
		final long ls = NTPDate.currentTimeMillis();

		if (ls <= lLastCall)
			return null;

		final Result er = new Result();
		er.FarmName = getFarmName();
		er.ClusterName = getClusterName();
		er.NodeName = mn.getName();
		er.Module = mmi.getName();
		er.time = ls;

		final Runtime rt = Runtime.getRuntime();

		er.addSet("FreeMemory", rt.freeMemory());
		er.addSet("TotalMemory", rt.totalMemory());
		er.addSet("MaxMemory", rt.maxMemory());

		if (tmw != null) {
			er.addSet("CacheSize", tmw.getSize());
			er.addSet("CacheGuideline", tmw.getLimit());
			//er.addSet("CacheLimit", tmw.getHardLimit());
			er.addSet("CacheLength_hours", (double) tmw.getTotalTime() / (double) 3600000);
		}

		try {
			er.addSet("Requests_permin", (lia.web.utils.ThreadedPage.getRequestCount() - vdData[0]) * 60000d / (ls - lLastCall));
			
			final double dIPv4Delta = lia.web.utils.ThreadedPage.getIPv4Requests() - vdData[1];
			final double dIPv6Delta = lia.web.utils.ThreadedPage.getIPv6Requests() - vdData[2];
			
			if (dIPv4Delta>=0)
				er.addSet("Requestsv4_permin", dIPv4Delta * 60000d / (ls - lLastCall));
			
			if (dIPv6Delta>=0)
				er.addSet("Requestsv6_permin", dIPv6Delta * 60000d / (ls - lLastCall));

			vdData[0] = lia.web.utils.ThreadedPage.getRequestCount();
			vdData[1] = lia.web.utils.ThreadedPage.getIPv4Requests();
			vdData[2] = lia.web.utils.ThreadedPage.getIPv6Requests();
			
			if (dIPv4Delta>=0 && dIPv6Delta>=0 && (dIPv4Delta + dIPv6Delta>0)){
				er.addSet("Requests_ipv6_percent", dIPv6Delta*100 / (dIPv6Delta + dIPv4Delta));
			}
		} catch (final Throwable t) {
			// ignore the error caused by missing base Web classes
		}

		try {
			for (int i = 0; i < lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames.length; i++) {
				er.addSet("WS_" + lia.ws.MLWebServiceSoapBindingImpl.vsCounterNames[i], (lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i] - vdData[3 + i]) * 60000d / (ls - lLastCall));

				vdData[3 + i] = lia.ws.MLWebServiceSoapBindingImpl.vsCounterValues[i];
			}
		} catch (Throwable t) {
			// ignore a possible error caused by missing WS classes
		}

		try {
			er.addSet("UniqueIDs", lia.Monitor.Store.Fast.IDGenerator.size());
		} catch (Throwable t) {
			// ignore a possible the error caused by missing Fast storage engine classes  
		}

		try {
			er.addSet("ReceivedResults", lia.Monitor.JiniClient.Store.Main.getAndFlushSavedValues());
		} catch (Throwable t) {
			// ignore a possible error if this is not a repository so the JiniClient.Store.Main is missing
		}

		try {
			er.addSet("RecentDataCache_size", lia.Monitor.Store.Cache.size());
		} catch (Throwable t) {
			// ignore a possible error if the storage engine is missing
		}

		try {
			final lia.web.utils.CacheServlet.CacheStatistics cs = lia.web.utils.CacheServlet.getCacheStatistics();
			//er.addSet("WebCacheSize", cs.lCacheCount);
			//er.addSet("WebCacheMem", cs.lTotalKeyLength + cs.lTotalCacheSize);
			//er.addSet("WebCacheHits", cs.lCacheHits);

			final long lCount = lia.web.utils.ThreadedPage.getRequestCount();

			er.addSet("WebCacheHitsPercent", lCount > 0 ? cs.lCacheHits * 100d / lCount : 0);
		} catch (Throwable t) {
			// ignore a possible error if this is not a repository and base Web classes are missing
		}

		try {
			er.addSet("Uptime", (NTPDate.currentTimeMillis() - lia.web.utils.ThreadedPage.lRepositoryStarted) / 1000d);
		} catch (Throwable t) {
			// ignore a possible error if this is not a repository and base Web classes are missing
		}

		/*
		 *  0 = database is not ok, the repository is using only the in-memory storage
		 *	1 = database is ok  
		 */
		er.addSet("DatabaseStatus", TransparentStoreFactory.isMemoryStoreOnly() ? 0 : 1);

		try {
			//er.addSet("BatchProcessor_size", lia.Monitor.Store.Fast.BatchProcessor.getBufferSize());
			
			double dQueue = lia.Monitor.Store.Fast.BatchProcessor.getBatchV1Size(); 
			
			er.addSet("BatchProcessor_v1", dQueue);
			
			double d = lia.Monitor.Store.Fast.BatchProcessor.getBatchV2Size();
			
			dQueue += d;
			
			er.addSet("BatchProcessor_v2", d);
			
			d = lia.Monitor.Store.Fast.BatchProcessor.getBatchV3Size();
			
			dQueue += d;
			
			er.addSet("BatchProcessor_v3", d);
			
			d = lia.Monitor.Store.Fast.BatchProcessor.getBatchQuerySize();
			
			dQueue += d;
			
			er.addSet("BatchProcessor_q", d);
			
			er.addSet("BatchProcessor_queue", dQueue);
			
			// average time to insert one value in the database, in milliseconds
			final double dAvgTime = lia.Monitor.Store.Fast.BatchProcessor.getTimePerValue();
			
			if (dAvgTime>0)
				er.addSet("BatchProcessor_time_per_val", dAvgTime);
			
			final double dInsertCount = lia.Monitor.Store.Fast.BatchProcessor.getInsertCount();
			
			er.addSet("BatchProcessor_insert_cnt", dInsertCount);
			
			er.addSet("BatchProcessor_insert_R", dInsertCount*1000/(ls - lLastCall));
			
		}
		catch (Throwable t) {
			// ignore a possible error if BatchProcessor class is missing
		}
		
		final String sDatabaseLocation = AppConfig.getProperty("lia.Repository.selfMonitoring.database_location", "../../pgsql_store/data/");
		final String sDirSizeCommand = AppConfig.getProperty("lia.Repository.selfMonitoring.dirSizeCommand", "/usr/bin/du -bs");
		final String sDirFreeCommand = AppConfig.getProperty("lia.Repository.selfMonitoring.dirFreeCommand", "/bin/df -P -B1 --no-sync");
		
		// try to get the database size at 6 hours, it doesn't make much sense to execute the query more often
		if (sDatabaseLocation!=null && sDatabaseLocation.length()>0 && ls>lLastDatabaseSizeCheck+1000*60*60*6){
			lLastDatabaseSizeCheck = ls;
			
			BufferedReader br = null;
			
			try{
				Runtime runtime = Runtime.getRuntime();
				
				Process p = runtime.exec(sDirSizeCommand+" "+sDatabaseLocation);
				
				br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				
				String sSize = br.readLine();
				
				StringTokenizer st = new StringTokenizer(sSize);
				
				dLastDatabaseSize = Double.parseDouble(st.nextToken());
				
				br.close();
			
				br = null;
				
				p.waitFor();				
			}
			catch (Throwable t){
				dLastDatabaseSize = 0;
				
				System.err.println("monRepositoryStats: cannot get the size of '"+sDatabaseLocation+"' with '"+sDirSizeCommand+"' command: "+t+" ("+t.getMessage()+")");
				t.printStackTrace();
			}
			finally{
				if (br!=null){
					try{
						br.close();
					}
					catch (Exception e){
						// ignore
					}
				}
			}
			
			try{
				Runtime runtime = Runtime.getRuntime();
				
				Process p = runtime.exec(sDirFreeCommand+" "+sDatabaseLocation);
				
				br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				
				br.readLine();
				
				String sSize = br.readLine();
				
				StringTokenizer st = new StringTokenizer(sSize);
				
				st.nextToken();
				
				dLastDatabasePartitionSize = Double.parseDouble(st.nextToken());
				
				st.nextToken();
				
				dLastDatabasePartitionFree = Double.parseDouble(st.nextToken()); 
				
				br.close();
			
				br = null;
				
				p.waitFor();				
			}
			catch (Throwable t){
				dLastDatabasePartitionFree = dLastDatabasePartitionSize = 0;
				
				System.err.println("monRepositoryStats: cannot get the free space of '"+sDatabaseLocation+"' with '"+sDirFreeCommand+"' command: "+t+" ("+t.getMessage()+")");
				t.printStackTrace();
			}
			finally{
				if (br!=null){
					try{
						br.close();
					}
					catch (Exception e){
						// ignore
					}
				}
			}
		}
		
		if (dLastDatabaseSize>0)
			er.addSet("DatabaseSize", dLastDatabaseSize);
		
		if (dLastDatabasePartitionFree>0)
			er.addSet("PartitionFree", dLastDatabasePartitionFree);
		
		if (dLastDatabasePartitionSize>0)
			er.addSet("PartitionSize", dLastDatabasePartitionSize);

		lLastCall = ls;

		final Vector<Object> vReturn = new Vector<Object>();
		vReturn.add(er);

		final eResult erVers = new eResult();

		erVers.FarmName = er.FarmName;
		erVers.ClusterName = er.ClusterName;
		erVers.NodeName = er.NodeName;
		erVers.Module = er.Module;
		erVers.time = er.time;

		try {
			erVers.addSet("RepositoryVersion", lia.web.servlets.web.display.sRepositoryVersion);
			erVers.addSet("RepositoryDate", lia.web.servlets.web.display.sRepositoryDate);
			erVers.addSet("MonitoredGroup", AppConfig.getProperty("lia.Monitor.group", "(undefined)"));
			
			erVers.addSet("JavaVersion", System.getProperty("java.version"));
			erVers.addSet("JavaVendor", System.getProperty("java.vendor"));
			
			erVers.addSet("OsName", System.getProperty("os.name"));
			erVers.addSet("OsArch", System.getProperty("os.arch"));
			erVers.addSet("OsVersion", System.getProperty("os.version"));
			
			final String sProxyIP =  lia.Monitor.JiniClient.CommonJini.JiniClient.getProxyIP();
			erVers.addSet("UsingProxy", sProxyIP != null ? sProxyIP : "<unknown>");
			
			if (!TransparentStoreFactory.isMemoryStoreOnly()){
				final DB db = new DB();
				
				db.setReadOnly(true);
				
				db.query("SELECT version();");
				
				final String sVers = db.gets(1);
				
				erVers.addSet("PgSQLFullVersion", sVers);
				
				final StringTokenizer st = new StringTokenizer(sVers);
				if (st.countTokens()>=2 && st.nextToken().toLowerCase().equals("postgresql")){
					erVers.addSet("PgSQLVersion", st.nextToken());
				}
			}
			
			vReturn.add(erVers);
		} catch (Throwable ignore) {
			// ignore a possible error if this is not a repository and base Web classes are missing
		}

		final ExtendedResult erStats = lia.web.utils.ThreadedPage.getStats();
		
		if (erStats!=null){
			erStats.FarmName = getFarmName();
			erStats.ClusterName = getClusterName();
			erStats.NodeName = mn.getName();
			erStats.Module = mmi.getName();
			erStats.time = ls;
			vReturn.add(erStats);
		}
		
		return vReturn;
	}

	@Override
	public MNode getNode() {
		return mn;
	}

	@Override
	public String getClusterName() {
		return mn.getClusterName();
	}

	@Override
	public String getFarmName() {
		return mn.getFarmName();
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return mmi.getName();
	}

	@Override
	public MonModuleInfo getInfo() {
		return mmi;
	}

}
