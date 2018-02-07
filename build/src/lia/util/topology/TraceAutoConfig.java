package lia.util.topology;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

/**
 * This class is a servlet that replaces the static tracepath.conf configuration file 
 * for monTracepath module. It will generate automatically the configuration for each of the
 * interested callers based on the following idea: when a request to this servlet is made,
 * the originating IP and the time are recorded in a hash. The response will contain all
 * IPs that have requested this configuration during last x minutes. All IPs that didn't 
 * do any request for more than x minutes are automatically removed from the list.   
 */
public class TraceAutoConfig extends ThreadedPage {

	// we store here the peers that have to trace each other
	private static Hashtable tracePeers;
	// vector with the allowed groups; only these will get config
	private static Vector allowedGroups;
	//vector with the farms that are not allowed to get config
	private static Vector bannedFarms;
	// use this to notify  other config generators about the requests that I receive
	private static TraceConfigNotifier othersNotifier;
	// last config refresh
	private static long lastConfigRefresh;
	// how often to refresh the config
	private static long CONFIG_REFRESH_INTERVAL = 10 * 1000; // 10 sec by default
	
	static {
		// do this only once!
		synchronized (TraceAutoConfig.class) {
			tracePeers = new Hashtable();
			refreshConfig();
			othersNotifier = new TraceConfigNotifier();
			try {
				System.setProperty("networkaddress.cache.ttl","21600");//6h
			} catch (Throwable t){
				System.out.println("Error setting IP Cache TTL");
				t.printStackTrace();
			}
		}
	}
	
	public TraceAutoConfig() {
		super();
	}
	
	public void doInit() {
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("text/plain");
	}
	
	private static void refreshConfig(){
		synchronized (TraceAutoConfig.class) {
			long now = System.currentTimeMillis();
			if(now - lastConfigRefresh < CONFIG_REFRESH_INTERVAL)
				return;
			lastConfigRefresh = now;
			
			String ALLOWED_GROUPS = AppConfig.getProperty("lia.util.topology.allowedGroups", "");
			allowedGroups = TracePeer.splitString(ALLOWED_GROUPS);
			String BANNED_FARMS = AppConfig.getProperty("lia.util.topology.bannedFarms", "");
			bannedFarms = TracePeer.splitString(BANNED_FARMS);
			String CONF_REFR_INTVL = AppConfig.getProperty("lia.util.topology.configRefreshInterval", "10");
			CONFIG_REFRESH_INTERVAL = Long.parseLong(CONF_REFR_INTVL) * 1000;
		}
	}

	/** dump the current status of the servlet */
	private void dumpCurrentStatus(){
		pwOut.println("TraceConfig status:");
		TreeSet ts = new TreeSet();
		synchronized (tracePeers) {
			for(Enumeration enp = tracePeers.elements(); enp.hasMoreElements(); ){
				TracePeer tp = (TracePeer) enp.nextElement();
				ts.add(tp.toString());
			}
		}
		// sort the output
		for(Iterator tsit=ts.iterator(); tsit.hasNext(); )
			pwOut.println(tsit.next());
		ts.clear();
		pwOut.flush();
		bAuthOK = true;
	}
	
	/**
	 * search the 
	 */
	private TracePeer findTracePeer(String farmIP, String requestIP, boolean create){
		String key = farmIP+"/"+requestIP;
		synchronized (tracePeers) {
			TracePeer tp = (TracePeer) tracePeers.get(key);
			if(tp == null && create){
				tp = new TracePeer(farmIP, requestIP);
				tracePeers.put(key, tp);
			}
			return tp;
		}
	}
	
	/** process the received http request */
	public void execGet() {
		if(request.getParameter("dumpStatus") != null){
			dumpCurrentStatus();
			return;
		}
		boolean keepAlive = (request.getParameter("keepAlive") != null);
		
		refreshConfig();
		
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
		
		TracePeer tp = findTracePeer(farmIP, requestIP, true);
		tp.updateDetails(request);
		
		// if this was a notification from other TraceAutoConfig service, give only a short response
		if(keepAlive){
			pwOut.println("OK");
			pwOut.flush();
			bAuthOK = true;
			return;
		}else{
			// notify all other TraceAutoConfig's about this
			othersNotifier.addNotification(tp);
		}
		
		// start building the response
		if(tp.traceOpts.trim().length() > 0){
			pwOut.println("TraceOpts "+tp.traceOpts);
			pwOut.println();
		}
		if(tp.farmIP.trim().length() > 0){
			pwOut.print(tp.farmIP);
		}else{
			pwOut.print(tp.requestIP);
		}
		
		// I will generate configuration for this peer only if it's in the allowedGroups
		tp.configPeers.clear();
		if(tp.matchesGroups(allowedGroups) && (! bannedFarms.contains(tp.farmName))){
			synchronized (tracePeers) {
				for(Iterator tpit = tracePeers.values().iterator(); tpit.hasNext(); ){
					TracePeer ptp = (TracePeer) tpit.next();
					if(tp.equals(ptp))
						continue;
					if(ptp.isExpired()){
						tpit.remove();
						System.out.println("TraceAutoConfig: Peer "+ptp.farmIP+"/"+ptp.requestIP
								+" expired and was removed from list.");
						continue;
					}
					if(bannedFarms.contains(ptp.farmName) || (! ptp.matchesGroups(allowedGroups)))
						continue;
					if(ptp.isAlive() && ptp.matchesGroups(tp.groups)){
						pwOut.print(" "+ptp.hostName);
						tp.configPeers.add(ptp.hostName);
					}
				}
			}
		}
		System.out.println(tp.requestIP+"@"+tp.groups+": "+tp.configPeers);
		pwOut.println();
		pwOut.flush();
		bAuthOK = true;
	}
}
