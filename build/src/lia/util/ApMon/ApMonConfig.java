package lia.util.ApMon;

/** This is a servlet for generating ApMon configuration. Used without package. */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lia.Monitor.monitor.AppConfig;
import lia.util.DateFileWatchdog;
import lia.util.net.NetMatcher;

/**
 * The purpose of this class is to generate an apmon configuration
 * based on the ip address from where the request comes and the application type. 
 * 
 * It supports several configuration files, one per application. Application name
 * is given in the http request in the form http://urlApMonConfig?app=appName.
 * If appName is missing, "default" will be taken instead. For each appName there must
 * be a corresponding config file appName.conf. Configuration files in the configurations
 * directory will be reread from time to time to be kept update with the changes on disk.
 * 
 * The configuration file for one application looks like this:
 * 
 * ---------------------------------
 * #[destination description; this can be anything]
 * #a line with a list of space-sepparated network addresses with netmasks
 * #from where if the request comes, the following lines will be given as apmon config 
 * #until the next [...] all the lines will be considered
 * #as being the ApMon config for this description
 * 
 * [caltech+fermilab]
 * 131.215.0.0/16 131.225.0.0/16
 * monalisa.cacr.caltech.edu:8884
 * monalisa2.cern.ch:8884
 * 
 * [cern]
 * 137.138.0.0/16 128.141.0.0/16
 * monalisa2.cern.ch:8884
 * 
 * [default]
 * 0.0.0.0/0
 * pcardaab.cern.ch:8884
 * ----------------------------------
 * 
 * The configurations will be analyzed in the order of appearance. The first 
 * list of addresses that matches will produce the corresponding lines of apmon
 * configuration and the search will end.
 * 
 * If no address matches, no configuration will be generated. Therefore, it is 
 * wise to have a default entry to catch all other requests 
 * 
 * @author catac
 */
public class ApMonConfig extends HttpServlet {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(ApMonConfig.class.getName());
    
	/** All configurations are kept in this Hash: key=appName; value=ApMonAppConfig */
	static HashMap hmConfig = new HashMap();

	/** Global config guard for reads and updates */
	static ReentrantReadWriteLock rwlAllConfig = new ReentrantReadWriteLock();
	
	/** Update apps configuration */
	static {
		new ConfigLoader().start();
	}

	/**
	 * Do the HTTP GET request  
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");
		String requestIP = request.getRemoteAddr();
		PrintWriter out = response.getWriter();
		String appName = request.getParameter("app");
		if(appName == null || appName.length() == 0){
			appName = "default";
		}
		if(logger.isLoggable(Level.FINER)){
			logger.finer("query from "+requestIP+" app="+appName);
		}
		rwlAllConfig.readLock().lock();
		AppConfiguration appConfig = (AppConfiguration) hmConfig.get(appName);
		if(appConfig == null){
			try{
				out.println("# No ApMon configuration defined for this application.");
				out.print("# Currently valid applications:");
				for(Iterator acit = hmConfig.values().iterator(); acit.hasNext(); ){
					out.print(" ");
					out.print(((AppConfiguration) acit.next()).appName);
				}
				out.println(".");
				out.flush();
				return;
			}finally{
				rwlAllConfig.readLock().unlock();
			}
		}
		appConfig.rwlAppConfig.readLock().lock();
		rwlAllConfig.readLock().unlock();
		try{
			boolean found = false;
			for(Iterator cpit = appConfig.lAppConfig.iterator(); cpit.hasNext(); ){
				AddrConfigPair cp = (AddrConfigPair) cpit.next();
				if(logger.isLoggable(Level.FINEST)){
					logger.finest("trying to match with "+cp.addresses.toString()+" ... ");
				}
				if(cp.addresses.matchInetNetwork(requestIP)){
					out.println(cp.config);
					if(logger.isLoggable(Level.FINEST)){
						logger.finest("matched!. Sending config:\n"+cp.config);
					}
					found = true;
					break;
				}
				if(logger.isLoggable(Level.FINEST)){
					logger.finest("nope.");
				}
			}
			if(! found){
				out.println("# Your address doesn't match any config.");
				if(logger.isLoggable(Level.INFO)){
					logger.info("No address from config matches "+requestIP);
				}
			}
			out.flush();
			return;
		}finally{
			appConfig.rwlAppConfig.readLock().unlock();
		}
	}
}

/** whenever the configuration file changes, this class will reload it */
class ConfigLoader extends Thread {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());

	/** Directory with configuration files for ApMon */
	static String CONF_DIR = AppConfig.getProperty("CONF_DIR", "");

	FilenameFilter confFilter;
	
	public ConfigLoader(){
		
		confFilter = new FilenameFilter(){
			public boolean accept(File dir, String name) {
				return name.endsWith(".conf");
			}
		};
		logger.info("Watching config dir: "+CONF_DIR);
	}
	
	public void run(){
		setName("ConfigLoader");
		Set oldApps = new HashSet();
		for(;;){
			try{
				File dir = new File(CONF_DIR);
				String [] confFiles = dir.list(confFilter);
				oldApps.clear();
				oldApps.addAll(ApMonConfig.hmConfig.keySet());
				// add new apps
				for(int i=0; i<confFiles.length; i++){
					String file = confFiles[i];
					String appName = file.substring(0, file.length() - ".conf".length());
					oldApps.remove(appName);
					if(ApMonConfig.hmConfig.get(appName) == null){
						ApMonConfig.rwlAllConfig.writeLock().lock();
						try{
							logger.info("Loading new app config: "+appName);
							ApMonConfig.hmConfig.put(appName, new AppConfiguration(appName));
						}finally{
							ApMonConfig.rwlAllConfig.writeLock().unlock();
						}
					}
				}
				// remove old apps
				for(Iterator oait = oldApps.iterator(); oait.hasNext(); ){
					String oldApp = (String) oait.next();
					ApMonConfig.rwlAllConfig.writeLock().lock();
					try{
						logger.info("Removing old app config: "+oldApp);
						AppConfiguration appConf = (AppConfiguration) ApMonConfig.hmConfig.remove(oldApp);
						appConf.stopIt();
					}finally{
						ApMonConfig.rwlAllConfig.writeLock().unlock();
					}
				}
			}catch(Throwable t){
				logger.log(Level.WARNING, "Error checking config:", t);
			}
			try{
				Thread.sleep(5 * 1000);
			}catch(InterruptedException ie){
				// ignore
			}
		}
	}
}

/** Holds configuration for an application */
class AppConfiguration implements Observer {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());
    
	ReentrantReadWriteLock rwlAppConfig = new ReentrantReadWriteLock(); // guard configuration reads and updates
	String appName; // name of the application
	String confFile; // corresponding config file
	List lAppConfig = new ArrayList(); // ordered list with configurations for this app; contains AddrConfigPairs
	DateFileWatchdog dfw; // the conf file watchdog
	
	public AppConfiguration(String appName){
		this.appName = appName;
		confFile = ConfigLoader.CONF_DIR+"/"+appName+".conf";
		loadConfig();
		// and start a whatchdog on the conf file
		try {
            dfw = DateFileWatchdog.getInstance(confFile, 5 * 1000);
            dfw.addObserver(this);
        } catch (Throwable t) {
            System.err.println("Cannot instantiate DateFileWatchdog for: " + confFile);
            t.printStackTrace();
        }
	}
	
	public void stopIt(){
		dfw.deleteObserver(this);
		dfw.stopIt();
	}
	
	// this will be called whenever the conf file changes
	public void update(Observable o, Object arg) {
		logger.info("Config changed for "+appName);
		loadConfig();
	}

	// load the configuration
	void loadConfig(){
		try{
			logger.info("Loading config for "+appName+" from "+confFile);
			BufferedReader in = new BufferedReader(new FileReader(confFile));
			String line;
			StringBuilder contents = new StringBuilder();
			StringBuilder logMsg = new StringBuilder();
			Vector crtConfig = new Vector();
			NetMatcher addresses = null;
			while((line = in.readLine()) != null){
				line = line.trim();
				if(line.length() == 0 || line.startsWith("#"))
					continue;
//				System.out.println("config: "+line);
				if(line.startsWith("[") && line.endsWith("]")){
					if(addresses != null){
						logMsg.append("got ApMonConfig:\n").append(contents);
						logger.info(logMsg.toString());
						logMsg.setLength(0);
						crtConfig.add(new AddrConfigPair(addresses, contents.toString()));
						addresses = null;
						contents.setLength(0);
					}
					continue; // skip this line
				}
				if(addresses == null){
					// first line after [...] lists the addresses
					addresses = new NetMatcher(line.split("\\s+"));
					logMsg.append("For app "+appName);
					logMsg.append(", for Addresses: ").append(addresses.toString()).append("\n");
				}else{
					// then comes the apmon config
					contents.append(line);
					contents.append("\n");
				}
			}
			// and the last entry
			if(addresses != null){
				logMsg.append("got ApMonConfig:\n").append(contents);
				logger.info(logMsg.toString());
				crtConfig.add(new AddrConfigPair(addresses, contents.toString()));
			}
			rwlAppConfig.writeLock().lock();
			try{
				lAppConfig.clear();
				lAppConfig.addAll(crtConfig);
				logger.info("Config updated successfully for "+appName);
			}finally{
				rwlAppConfig.writeLock().unlock();
			}
			
		}catch(Throwable t){
			logger.log(Level.WARNING, "Error fetching file "+confFile+"; keeping previous config.", t);
		}
	}
}


/** Holds a config entry */
class AddrConfigPair {
	NetMatcher addresses;
	String config;
	
	AddrConfigPair(NetMatcher addresses, String config){
		this.addresses = addresses;
		this.config = config;
	}
}
