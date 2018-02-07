package lia.util.ABPing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

/**
 * This class is a servlet that replaces the static ABPingFarmConfig configuration file 
 * for monABPing module. It will generate automatically the configuration for each of the
 * interested callers based on the following idea: when a request to this servlet is made,
 * the originating IP and the time are recorded in a hash. All IPs that didn't 
 * do any request for more than x minutes are automatically removed from the list.   
 */
public class ABPingAutoConfig extends ThreadedPage {

	// we store here the peers that have to abping each other
	private static Hashtable abpingPeers = new Hashtable();
	
	// vector with the banned groups; only these will get config
	static Vector bannedGroups = new Vector();
	
	//vector with the farms that are not allowed to get config
	static Vector bannedFarms = new Vector();
	
	// vector with the IPs that are not allowed to get config
	static Vector bannedIPs = new Vector();
	
	// this keeps the ABPing properties from the last URL that was read and is
	// sent to the clients when they ask for their configuration
	private static Hashtable configProperties = new Hashtable();
	
	// ABPing config properties' names
	private static String CONFIG_PROPERTIES [] = { "OVERALL_COEF", "RTT_COEF", "PKT_LOSS_COEF", "JITTER_COEF", 
		"RTT_SAMPLES", "PKT_LOSS_MEM", "PACKET_SIZE", "PING_INTERVAL" };
	
	// comma-sepparated list of URLs (or files) pointing to user given configurations   
	private static String configURLs = ""; // "http://monalisa.cern.ch/ABPingOldFarmConfig");
	
	// after how much inactivity remove a peer from this cache; by default, 600 sec. 
	static long PEER_TIMEOUT = 600 * 1000;

	// min nr. of ABPing peers for each node; by default, 2.
	static int MIN_PEERS_NR = 2;
	
	// last config refresh
	private static long lastConfigRefresh;
	// how often to refresh the config
	private static long CONFIG_REFRESH_INTERVAL = 10 * 1000; // 10 sec by default
	
	static {
		// do this only once!
		try {
			System.setProperty("networkaddress.cache.ttl","21600");//6h
		} catch (Throwable t){
			System.out.println("Error setting IP Cache TTL");
			t.printStackTrace();
		}
		System.out.println("going to reinitGlobalParams...");
		reinitGlobalParams();
		System.out.println("static init completed...");
	}
	
	public ABPingAutoConfig() {
		super();
		System.out.println("constructor...ABPingAutoConfig");
	}
	
	public void doInit() {
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("text/plain");
	}
	
	/** reinitalize global parameters */
	private static void reinitGlobalParams(){
		synchronized (ABPingAutoConfig.class) {
			long now = System.currentTimeMillis();
			if(now - lastConfigRefresh < CONFIG_REFRESH_INTERVAL)
				return;
			lastConfigRefresh = now;
			
			synchronized (abpingPeers) {
				bannedGroups.clear();
				bannedGroups.addAll(ABPingPeer.splitString(AppConfig.getProperty("lia.util.ABPing.bannedGroups", "")));

				bannedFarms.clear();
				bannedFarms.addAll(ABPingPeer.splitString(AppConfig.getProperty("lia.util.ABPing.bannedFarms", "")));

				bannedIPs.clear();
				bannedIPs.addAll(ABPingPeer.splitString(AppConfig.getProperty("lia.util.ABPing.bannedIPs", "")));
				
				PEER_TIMEOUT = Long.parseLong(AppConfig.getProperty("lia.util.ABPing.autoConfigTimeout", "600")) * 1000;
				
				MIN_PEERS_NR = Integer.parseInt(AppConfig.getProperty("lia.util.ABPing.minNrOfPeers", "2"));
				
				CONFIG_REFRESH_INTERVAL = Long.parseLong(AppConfig.getProperty("lia.util.ABPing.configRefreshInterval", "10")) * 1000;
				
				configURLs = AppConfig.getProperty("lia.util.ABPing.configURLs", "");
				if(configURLs == null)
					configURLs = "";
			}
			System.out.println("loading conf from urls... "+configURLs);
			for(StringTokenizer stk=new StringTokenizer(configURLs, ","); stk.hasMoreTokens(); ){
				String url = stk.nextToken();
				loadConfig(url);
			}
		}
	}
	
	/**
	 * load the config from this url 
	 */
	private static void loadConfig(String url){
		try{
		    URLConnection urlc = new URL(url).openConnection();
//		    urlc.setConnectTimeout(5 * 1000);
		    urlc.setDefaultUseCaches(false);
		    urlc.setUseCaches(false);

		    BufferedReader in = new BufferedReader(new InputStreamReader(urlc.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if(line.startsWith("#") || line.length() == 0)
					continue;
				StringTokenizer st = new StringTokenizer(line, " ");
				// get first word on the line
				if(st.hasMoreTokens()){
					String id = st.nextToken();
					// check if it's a property
					boolean isConf = false;
					for(int i = 0; i < CONFIG_PROPERTIES.length; i++){
						if(CONFIG_PROPERTIES[i].equals(id)){
							isConf = true;
							if(st.hasMoreTokens())
								configProperties.put(id, st.nextToken());
						}
					}
					// if not, maybe it's a peer with its peers
					if(! isConf)
						setWantedPeers(id, st);
				}
			}
		}catch(Exception me){
			System.err.println("Error loading config from "+url);
			me.printStackTrace();
		}
	}
	
	/** set the wanted peers for a farm - used when loading user config */
	private static void setWantedPeers(String requestIP, StringTokenizer stkPeers){
		if(! stkPeers.hasMoreTokens())
			return;
		ABPingPeer ap = findABPingPeer(requestIP, true);
		synchronized (abpingPeers) {
			ap.wantPeers.clear();
			while(stkPeers.hasMoreTokens()){
				ABPingPeer wp = findABPingPeer(stkPeers.nextToken(), true);
				ap.wantPeers.add(wp);
			}
		}
	}

	/** dump the current status of the servlet */
	private void dumpCurrentStatus(){
		pwOut.println("ABPingConfig status:");
		pwOut.println("BannedFarms: "+bannedFarms);
		pwOut.println("BannedIPs: "+bannedIPs);
		pwOut.println("BannedGroups: "+bannedGroups);
		
		TreeSet ts = new TreeSet();
		synchronized (abpingPeers) {
			for(Enumeration enp = abpingPeers.elements(); enp.hasMoreElements(); ){
				ABPingPeer ap = (ABPingPeer) enp.nextElement();
				ts.add(ap.getStatus());
			}
		}
		// sort the output
		for(Iterator tsit=ts.iterator(); tsit.hasNext(); )
			pwOut.println(tsit.next());
		ts.clear();
		pwOut.flush();
		bAuthOK = true;
	}
	
	/** search the current peers and return (or create) the one for the given IP */
	private static ABPingPeer findABPingPeer(String requestIP, boolean create){
		String key = requestIP;
		synchronized (abpingPeers) {
			ABPingPeer ap = (ABPingPeer) abpingPeers.get(key);
			if(ap == null && create){
				ap = new ABPingPeer(requestIP);
				abpingPeers.put(key, ap);
			}
			return ap;
		}
	}
	
	/** return true if banned */
	public static boolean isBanned(ABPingPeer p){
		return p.matchesGroups(bannedGroups) ||
		(bannedFarms.contains(p.farmName)) || 
		(bannedIPs.contains(p.requestIP)) ||
		(bannedIPs.contains(p.farmIP));
	}
	
	/** return a list of peers preferred to ping by the given node */
	public static Vector getPreferredPeers(ABPingPeer a){
		Vector vr = new Vector();
		TreeSet ts = new TreeSet();
		for(Iterator pit = abpingPeers.values().iterator(); pit.hasNext(); ){
			ABPingPeer b = (ABPingPeer) pit.next();
			if(b == a)
				continue;
			if((! b.isAlive()) || isBanned(b))
				continue;

			int score = a.distanceTo(b);
			if(! a.matchesGroups(b.groups))
				score += 500 + Math.random() * 500;
			ts.add(new myMap(b, score));
		}
		for(Iterator tsit = ts.iterator(); tsit.hasNext(); ){
			myMap mm = (myMap) tsit.next();
			vr.add(mm.p);
		}
		return vr;
	}
	
	/** Dump config properties */
	private void dumpConfig(PrintWriter pwOut){
		for(Enumeration enp = configProperties.keys(); enp.hasMoreElements(); ){
			String prop = (String) enp.nextElement();
			pwOut.println(prop + " " + configProperties.get(prop));
		}
	}
	/** process the received http request */
	public void execGet() {
		if(request.getParameter("dumpStatus") != null){
			dumpCurrentStatus();
			return;
		}
		boolean keepAlive = (request.getParameter("keepAlive") != null);
		
		// request IP
		String requestIP = request.getParameter("requestIP");
		if(requestIP == null)
			requestIP = request.getParameter("RequestIP");
		if(requestIP == null)
			requestIP = request.getRemoteAddr();
		
		// ml farm IP
		String farmIP = request.getParameter("farmIP");
		if(farmIP == null)
			farmIP = request.getParameter("FarmIP");
		if(farmIP == null)
			farmIP = requestIP;
		
		reinitGlobalParams();
		
		ABPingPeer ap = null;
		synchronized (abpingPeers) {
			ap = findABPingPeer(requestIP, true);
			ap.updateDetails(request);
		}
		
		// if this was a notification from other ABPingAutoConfig service, give only a short response
		if(keepAlive){
			pwOut.println("OK");
			pwOut.flush();
			bAuthOK = true;
			return;
		}else{
			// TODO: notify all other ABPingAutoConfig's about this
		}
		
		// start building the response
		if(ap.farmIP.trim().length() > 0){
			pwOut.print(ap.farmIP);
		}else{
			pwOut.print(ap.requestIP);
		}
		
		// I will generate configuration for this peer only if it's NOT in the bannedGroups
		if(isBanned(ap)){
			synchronized (abpingPeers) {
				ap.configPeers.clear();
				pwOut.print(" none");
				System.out.println("{"+ap.requestIP+"} is BANNED!");
			}
		}else{
			synchronized (abpingPeers) {
				for(Iterator apit = abpingPeers.values().iterator(); apit.hasNext(); ){
					ABPingPeer abp = (ABPingPeer) apit.next();
					if(ap.equals(abp))
						continue;
					if(abp.isExpired()){
						apit.remove();
						System.out.println("ABPingAutoConfig: Peer "+abp.farmName+"/"+abp.farmIP
								+" expired and was removed from list.");
						continue;
					}
				}
				ap.refreshConfigPeers();
				for(Iterator pit = ap.configPeers.iterator(); pit.hasNext(); ){
					ABPingPeer p = (ABPingPeer) pit.next();
					pwOut.print(" "+p.hostName);
				}
				System.out.println("{"+ap.requestIP+"} ConfPeers: "+ap.configPeers);
			}
		}
		pwOut.println();
		dumpConfig(pwOut);
		pwOut.flush();
		bAuthOK = true;
	}
}

class myMap implements Comparable {
	ABPingPeer p;
	int score;
	
	myMap(ABPingPeer p, int score){
		this.p = p;
		this.score = score;
	}
	
	public int compareTo(Object o) {
		myMap om = (myMap) o;
		return score < om.score ? -1 : (score > om.score ? 1 : 0);
	}
}