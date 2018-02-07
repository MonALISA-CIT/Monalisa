package lia.util.ABPing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;

/**
 * This class will hold the internals of ABPing Config Generator
 */
public class ConfigManager extends Thread {

	// after how many minutes of inactivity remove a peer from this cache; by default, 10. 
	private final long PEER_TIMEOUT = Long.parseLong(AppConfig.getProperty("lia.util.ABPing.autoConfigTimeout", "10")) * 60 * 1000;
	
	// comma-sepparated list of URLs (or files) pointing to user given configurations   
	private final String configURLs = AppConfig.getProperty("lia.util.ABPing.configURLs", "http://monalisa.cern.ch/ABPingOldFarmConfig");
	
	// after how many minutes try to refresh configuration
	private final long configRefreshInterval = Long.parseLong(AppConfig.getProperty("lia.util.ABPing.configRefreshInterval", "5")) * 60 * 1000;
	
	// when was the config read last time
	private long lastConfigRefreshTime = -1;
	
	// minimum number of peers that a peer should have
	private int MIN_PEERS = Integer.parseInt(AppConfig.getProperty("lia.util.ABPing.minNrOfPeers", "2")); 

	// comma-sepparated list of URLs pointing to other ABPingAutoConfig_OLD servlets. KeepAliver will inform them of my alive peers from peersCache
	private final String peerServlets = AppConfig.getProperty("lia.util.ABPing.peerServlets", "");
	
	// at what interval announce the other servlets
	private final long keepAliveInterval = 20 * 1000; 
	
	// when was the last keep alive performed
	private long lastKeepAliveTime = -1;

	// we store here the peers that have to ping each other 
	private Hashtable peersCache;	
	
	// this keeps the ABPing properties from the last URL that was read and is
	// sent to the clients when they ask for their configuration
	private Hashtable configProperties;
	
	// ABPing config properties' names
	private String CONFIG_PROPERTIES [] = { "OVERALL_COEF", "RTT_COEF", "PKT_LOSS_COEF", "JITTER_COEF", 
		"RTT_SAMPLES", "PKT_LOSS_MEM", "PACKET_SIZE", "PING_INTERVAL" };

	/** only one instance of this class */
	private static ConfigManager configManager = new ConfigManager();

	/**
	 * private constructor; use the getInstance() method to get this
	 */
	private ConfigManager(){
		peersCache = new Hashtable();
		configProperties = new Hashtable();
		start();
	}
	
	public static ConfigManager getInstance(){
		return configManager;
	}

	/**
	 * get the peer for the given host or ip. If it doesn't exist, it's created.  
	 */
	private PeerInfo getPeer(String hostOrIP) {
		try{
			InetAddress ina = InetAddress.getByName(hostOrIP);
			String ipAddr = ina.getHostAddress();
			PeerInfo pi = (PeerInfo) peersCache.get(ipAddr);
			if(pi == null){
				pi = new PeerInfo(ipAddr);
				peersCache.put(ipAddr, pi);
			}
			return pi;
		}catch(Exception ex){
			System.err.println("Cannot create peer for hostOrIP = "+hostOrIP+": "+ex.toString());
			return null;
		}
	}

	/**
	 * Dump given peer's peers 
	 */
	private void dumpPeer(PeerInfo pi, PrintWriter pwOut){
		if(pi.getHostName() != null){
			pwOut.print(pi.getHostName()+" ");
			pwOut.println(pi.getPeers());
		}
		pwOut.print(pi.getIpAddress()+" ");
		pwOut.println(pi.getPeers());
		pwOut.println();
	}

	/**
	 * Dump config properties
	 */
	private void dumpConfig(PrintWriter pwOut){
		for(Enumeration enp = configProperties.keys(); enp.hasMoreElements(); ){
			String prop = (String) enp.nextElement();
			pwOut.println(prop + " " + configProperties.get(prop));
		}
	}
	
	/**
	 * Dump all alive peers 
	 */
	synchronized public void dumpAlivePeers(PrintWriter pwOut){
		for(Enumeration enp=peersCache.elements(); enp.hasMoreElements(); ){
			PeerInfo pi = (PeerInfo) enp.nextElement();
			if(pi.isAlive())
				dumpPeer(pi, pwOut);
		}
		dumpConfig(pwOut);
		pwOut.println();
	}
	
	/**
	 * Dump current status
	 */
	synchronized public void dumpStatus(PrintWriter pwOut){
		pwOut.println("configURLs = " + configURLs);
		pwOut.println("configRefreshInterval = " + (configRefreshInterval/1000/60) + " min.");
		pwOut.println("minNrOfPeers = " + MIN_PEERS);
		pwOut.println("autoConfigTimeout = " + (PEER_TIMEOUT/1000/60) + " min.");
		pwOut.println("peerServlets = " + peerServlets);
		for(Enumeration pen = peersCache.elements(); pen.hasMoreElements(); ){
			pwOut.print(((PeerInfo) pen.nextElement()).getStatus());
		}
		pwOut.println("\nconfigProperties:");
		dumpConfig(pwOut);
	}
	
	/**
	 * Refresh multiple peers 
	 */
	synchronized public void multiRefresh(String peerList){
		StringTokenizer stk = new StringTokenizer(peerList, ",");
		while(stk.hasMoreTokens()){
			String requestIP = stk.nextToken();
			PeerInfo pi = getPeer(requestIP);
			if(pi != null)
				pi.refresh();
		}
	}
	
	/**
	 * refresh the given peer and dump its configuration 
	 */
	synchronized public void dumpPeerConfig(String requestIP, String farmName, String farmGroups, PrintWriter pwOut){
		PeerInfo pi = getPeer(requestIP);
		if(pi != null){
			pi.setDetails(farmName, farmGroups);
			pi.refresh();
			dumpPeer(pi,pwOut);
			dumpConfig(pwOut);
		}else{
			pwOut.println("#ERROR!");
		}
	}
	
	// keep data about peers here
	class PeerInfo {
		
		private String hostName = null; // peer's hostname, if it can be resolved, or null otherwize
		private String ipAddress = null; // peer's ip
		private Vector farmNames = null;	// farm's name(s) ?
		private Vector farmsGroups = null; // farm(s)'s groups

		private long lastRequestTime = -1; // when was the last request for config from this peer
		
		private Vector wantedPeers = null; // list of peers for me, from user's config files.
		private Vector myPeers = null; // my current list of peers
		private Vector alivePeers = null; // temporary list with currently alive peers.

		/** 
		 * Initialize a peer, based on its hostname or ip address 
		 * Note that this mai fail, if the given hostName cannot be resolved.
		 */  
		public PeerInfo(String host) throws IOException{
			InetAddress ina = InetAddress.getByName(host);
			ipAddress = ina.getHostAddress();
			hostName = ina.getCanonicalHostName();
			if(hostName.equals(ipAddress))
				hostName = null;
			farmNames = new Vector();
			farmsGroups = new Vector();
			wantedPeers = new Vector();
			myPeers = new Vector();
			alivePeers = new Vector();
		}
		
		/**
		 * Sets the list of wanted peers for this peer, i.e. what user specifies in the
		 * configuration files. 
		 */
		public void setWantedPeers(Vector peers){
			synchronized(wantedPeers){
				wantedPeers.clear();
				wantedPeers.addAll(peers);
			}
		}
		
		/**
		 * add/set details for this peer, like farm's name, or group, if available 
		 */
		public void setDetails(String sFarmName, String sFarmGroups){
			if(sFarmName != null && (! farmNames.contains(sFarmName)))
				farmNames.add(sFarmName);
			if(sFarmGroups != null && (! farmsGroups.contains(sFarmGroups)))
				farmsGroups.add(sFarmGroups);
		}
		
		/**
		 * This should be called when a request for this Peer is received. 
		 * It will refresh the lastRequestTime and also, based on the wantedPeers
		 * and their isAlive status it will adjust the myPeers list with alive peers.   
		 */
		public void refresh(){
			lastRequestTime = System.currentTimeMillis();
			// get the list with alive peers from the wanted ones.
//			System.err.println("refresh: "+this);
			alivePeers.clear();
			synchronized(wantedPeers){
				for(Iterator pit = wantedPeers.iterator(); pit.hasNext(); ){
					PeerInfo p = (PeerInfo) pit.next();
					if(p.isAlive() && (! alivePeers.contains(p)) && (p != this))
						alivePeers.add(p);
				}
			}
//			System.err.print("alivewp:");
//			for(Iterator pit=alivePeers.iterator(); pit.hasNext(); ){
//				System.err.print(" "+(PeerInfo)pit.next());
//			}
			// now check that my current peers are alive and while alivePeers.size() < MIN_PEERS
			// add them to the livePeers if not already there. This way make sure that if
			// previously we randomly selected a peer (see below), we will still use it, 
			// if it's still alive. This should prevent oscillations.
			for(int i=0; (i < myPeers.size()) && (alivePeers.size() < MIN_PEERS); i++){
				PeerInfo p = (PeerInfo) myPeers.get(i);
				if(p.isAlive() && (! alivePeers.contains(p)))
					alivePeers.add(p);
			}
//			System.err.print("\nawp+mp:");
//			for(Iterator pit=alivePeers.iterator(); pit.hasNext(); ){
//				System.err.print(" "+(PeerInfo)pit.next());
//			}

			// we still have to check if we have MIN_PEERS peers, and if not, select
			// some other random peers, until this condition is met.
			// TODO: improve this by using some groups, geographic etc. data.
			if(alivePeers.size() < MIN_PEERS){
				Vector otherAlive = new Vector();
				// first get a list with all alive peers, not already added to alivePeers
				for(Enumeration enp = peersCache.elements(); enp.hasMoreElements(); ){
					PeerInfo p = (PeerInfo) enp.nextElement();
					// I don't want to add the peers directly to alivePeers to enhance
					// as much as possible the randomness
					if(p.isAlive() && (! alivePeers.contains(p)) && (p != this))
						otherAlive.add(p);
				}
				// then add random peers until one of the conditions breaks
				while((alivePeers.size() < MIN_PEERS) && (otherAlive.size() != 0)){
					int pos = (int)(otherAlive.size() * Math.random());
					alivePeers.add(otherAlive.remove(pos));
				}
			}
//			System.err.print("\nw_o_np:");
//			for(Iterator pit=alivePeers.iterator(); pit.hasNext(); ){
//				System.err.print(" "+(PeerInfo)pit.next());
//			}
			// now we have to transfer alivePeers to myPeers and make sure that each 
			// of my peers has me as it's peer also - to avoid asymmetric links.
			// add the new peers
			for(Iterator pit = alivePeers.iterator(); pit.hasNext(); ){
				PeerInfo p = (PeerInfo) pit.next();
				addPeer(p); // it won't add it twice
				p.addPeer(this);
			}
			// remove unwanted peers (if possible)
			for(Iterator pit = myPeers.iterator(); pit.hasNext(); ){
				PeerInfo p = (PeerInfo) pit.next();
				if(! alivePeers.contains(p)){
					if((! p.isAlive()) || p.canRemovePeer(this)){
						pit.remove();
						p.removePeer(this);
//						System.err.println("removing peer: "+p);
					}else{
//						System.err.println("cannot remove peer: "+p);
					}
				}
			}
//			System.err.print("\nnew_mp:");
//			for(Iterator pit=myPeers.iterator(); pit.hasNext(); ){
//				System.err.print(" "+(PeerInfo)pit.next());
//			}
//			System.err.println();
		}
		
		/**
		 * Adds the given peer to the list with my peers. 
		 */
		public void addPeer(PeerInfo p){
			if(! myPeers.contains(p))
				myPeers.add(p);
		}
		
		/**
		 * Remove from the list with my peers  
		 */
		public void removePeer(PeerInfo p){
			myPeers.remove(p);
		}
		
		/**
		 * Test if this peer can be removed 
		 */
		public boolean canRemovePeer(PeerInfo p){
			return (! isAlive()) || (! p.isAlive()) || ((! wantedPeers.contains(p)) && (myPeers.size() > MIN_PEERS));
		}
		
		/**
		 * Checks if this peer is still alive, i.e. refresh() was called for it sooner
		 * than PEER_TIMEOUT. 
		 */
		public boolean isAlive(){
			//TODO: improve this by checking if the peer returns valid data after
			// a certain amount of time from selecting it. This will help removing
			// nodes that are behind firewalls
			return (System.currentTimeMillis() - lastRequestTime) < PEER_TIMEOUT; 
		}

		/**
		 * Generates a set of lines containg this peer's status, for servlet info 
		 */
		public String getStatus(){
			StringBuilder ssb = new StringBuilder();
			if(farmNames.size() > 0){
				ssb.append("\nfarmName");
				for(Iterator nit = farmNames.iterator(); nit.hasNext(); )
					ssb.append(" = "+nit.next());
			}
			if(farmsGroups.size() > 0){
				ssb.append("\nfarmGrps");
				for(Iterator git = farmsGroups.iterator(); git.hasNext(); )
					ssb.append(" = "+git.next());
			}
			ssb.append("\nip addr  = "); ssb.append(ipAddress);
			ssb.append(" ["); ssb.append(hostName); ssb.append("]");
			ssb.append("\nis alive = "); ssb.append(isAlive());
			ssb.append("\nwantpeers=");
			for(Iterator wit = wantedPeers.iterator(); wit.hasNext(); ){
				ssb.append(" "+wit.next());
			}
			ssb.append("\nmyPeers  = "); ssb.append(getPeers());
			ssb.append("\nlastReqOn= "); 
			ssb.append(lastRequestTime == -1 ? "Never" : ""+new Date(lastRequestTime));
			ssb.append("\n");
			return ssb.toString();
		}
		
		/**
		 * Return a string with this peer's peers. Note that refresh should be 
		 * called before this to get fresh data.  
		 */
		public String getPeers(){
			StringBuilder psb = new StringBuilder();
			for(Iterator pit=myPeers.iterator(); pit.hasNext(); ){
				if(psb.length() > 0)
					psb.append(" ");
				psb.append(pit.next());
			}
			return psb.toString();
		}
		
		public String getHostName(){
			return hostName;
		}
		
		public String getIpAddress(){
			return ipAddress;
		}
		
		/**
		 * Return a textual representation of this.
		 */
		public String toString(){
			if(hostName != null)
				return hostName;
			return ipAddress;
		}
	}

	/**
	 * for a peer, adjust it's wanted peers list 
	 */
	private void setWantedPeers(String peer, StringTokenizer st){
		PeerInfo pi = getPeer(peer);
		if(pi == null)
			return;
		Vector wp = new Vector();
		while(st.hasMoreTokens()){
			PeerInfo p = getPeer(st.nextToken());
			if(p != null)
				wp.add(p);
		}
		pi.setWantedPeers(wp);
	}
	
	/**
	 * load the config from this url 
	 */
	private void loadConfig(String url){
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
	
	/**
	 * refresh config from all sources
	 */
	private void doConfigRefresh(){
		try{
			for(StringTokenizer stk=new StringTokenizer(configURLs, ","); stk.hasMoreTokens(); ){
				String url = stk.nextToken();
				loadConfig(url);
			}
		}catch(Exception ex){
			System.err.println("Error while loading configuration:");
			ex.printStackTrace();
		}
	}

	/**
	 * just do a url connection and read the response
	 */
	private void keepAlive(String url){
		try{
		    URLConnection urlc = new URL(url).openConnection();
//		    urlc.setConnectTimeout(5 * 1000);
		    urlc.setDefaultUseCaches(false);
		    urlc.setUseCaches(false);

		    BufferedReader in = new BufferedReader(new InputStreamReader(urlc.getInputStream()));
			String line = in.readLine();
			if((line == null) || (! line.equals("OK"))){
				System.err.println("Got response "+line+" while keepaliving\n"+url);
			}
		}catch(Exception me){
			System.err.println("Error keepaliving: "+url);
			me.printStackTrace();
		}
	}
	
	/**
	 * propagate my knowledge to all the other servlets
	 */
	private void doKeepAlive(){
		try{
			StringBuilder sbp = new StringBuilder();
			for(Enumeration enp = peersCache.elements(); enp.hasMoreElements(); ){
				PeerInfo pi = (PeerInfo) enp.nextElement();
				if(pi.lastRequestTime > lastKeepAliveTime){
					if(sbp.length() > 0)
						sbp.append(",");
					sbp.append(pi.ipAddress);
				}
			}
			if(sbp.length() > 0){
				for(StringTokenizer stk = new StringTokenizer(peerServlets, ","); stk.hasMoreTokens(); ){
					keepAlive(stk.nextToken() + "?keepAlive=" + sbp.toString());
				}
			}
		}catch(Exception ex){
			System.err.println("Error while keepAliving:");
			ex.printStackTrace();
		}
	}

	/**
	 * Main thread of ConfigManager
	 */
	public void run(){
		setName("(ML) - ConfigManager");
		while(true){
			try{
				Thread.sleep(5000);
			}catch(InterruptedException ex) { }
			try{
				long now = System.currentTimeMillis();
				if(lastConfigRefreshTime + configRefreshInterval < now){
					doConfigRefresh();
					lastConfigRefreshTime = now;
				}
				if((peerServlets != null) && (peerServlets.length() > 0) && 
						(lastKeepAliveTime + keepAliveInterval < now)){
					doKeepAlive();
					lastKeepAliveTime = now;
				}
			}catch(Exception ex){
				System.err.println("Error in cofigManager thread");
				ex.printStackTrace();
			}
		}
	}
}
