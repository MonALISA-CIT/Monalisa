package lia.util.topology;

import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import lia.Monitor.monitor.AppConfig;

/**
 * Describes a Tracepath Peer.  
 * @author catac
 */
public class TracePeer {

	// after how many minutes of inactivity remove a peer from this cache; by default, 10. 
	private static final long PEER_TIMEOUT = Long.parseLong(AppConfig.getProperty("lia.util.topology.autoConfigTimeout", "10")) * 60 * 1000;

	String farmName = "";
	Vector groups = new Vector();
	String farmIP = "";
	String requestIP;
	String hostName = "";
	String mlVersion = "";
	String mlDate = "";
	String LONG = "";
	String LAT = "";
	String traceOpts = "";
	boolean keptAlive = false;
	Vector peersStat = new Vector();	// status of its tracing peers, as reported by the module 
	Vector configPeers = new Vector();	// given tracing peers by the config servlet 
	long lastRefresh;					// last refresh time
	
	public TracePeer(String farmIP, String requestIP){
		this.farmIP = farmIP;
		this.requestIP = requestIP;
	}
	
	/** return true if the peer is still alive */
	public boolean isAlive(){
		long now = System.currentTimeMillis();
		return now - lastRefresh < PEER_TIMEOUT;
	}
	
	/** return true if the peer can be removed from hashes */
	public boolean isExpired(){
		long now = System.currentTimeMillis();
		return now - lastRefresh > 20 * PEER_TIMEOUT;
	}
	
	/** return true if this.groups has a group contained in the given toMatch Vector */
	public boolean matchesGroups(Vector toMatch){
		for(Iterator git = this.groups.iterator(); git.hasNext(); )
			if(toMatch.contains(git.next()))
				return true;
		return false;
	}
	
	public boolean equals(Object other){
		return (other instanceof TracePeer)
			&& (requestIP.equals(((TracePeer) other).requestIP))
			&& (farmIP.equals(((TracePeer) other).farmIP));
	}
	
	public String toString(){
		long now = System.currentTimeMillis();
		String time = isAlive() ? 
				"" + (now - lastRefresh) / 1000 / 60 + " min ago" :
				"died on "+new Date(lastRefresh);
		return farmName+" ["+hostName+"/"+farmIP+" requestIP="+requestIP+"] ML: "+mlVersion+"/"+mlDate+" Location: "+LONG+"/"+LAT+"\n"
			+"Kept Alive : "+keptAlive+"\n"
			+"Groups: ["+vectorToStr(groups)+"]\n"
			+"Trace Opts : ["+traceOpts+"]\n"
			+"Last Update: "+time+"\n"
			+"Given peers: "+configPeers+"\n"
			+"Peers stats: "+peersStat+"\n";
	}
	
	/** create a Vector from the given array */
	private Vector vectFromStrArray(String [] values){
		Vector rez = new Vector();
		if(values != null){
			for(int i=0; i<values.length; i++)
				rez.add(values[i]);
		}
		return rez;
	}

	/** return a string with the elements in the given vector joined by a comma */
	public static String vectorToStr(Vector v){
		if(v.size() == 0)
			return "";
		StringBuilder sb = new StringBuilder((String) v.get(0));
		for(int i=1; i<v.size(); i++){
			sb.append(",");
			sb.append((String) v.get(i));
		}
		return sb.toString();
	}
	
	/** split the given comma sepparated groups string in single group strings */
	public static Vector splitString(String params){
		Vector v = new Vector();
		if(params != null){
			StringTokenizer stk = new StringTokenizer(params, ",");
			while(stk.hasMoreTokens()){
				String g = stk.nextToken().trim();
				if(g.length() > 0)
					v.add(g);
			}
		}
		return v;
	}
	
	/** 
	 * Check if the given hostName, if exists, points to the same IP as requestIP.
	 * This is done since determining the hostName based only on the requestIP 
	 * might fail (reverse dns), but direct dns might success.  
	 */
	private String getRealHostName(String hostName, String requestIP){
		try{
			if(hostName != null){
				if(hostName.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
					hostName = null;
			}
			InetAddress addr = InetAddress.getByName(requestIP);
			if(hostName != null){
				// if given, verify it
				try{
					InetAddress givenAddr = InetAddress.getByName(hostName);
					if(! givenAddr.getHostAddress().equals(addr.getHostAddress())){
						// they don't match; we have to determine it
						hostName = addr.getCanonicalHostName();
					}
				}catch(Exception ex){
					// the hostname given by module is a mess; we have to determine it
					hostName = addr.getCanonicalHostName();
				}
			}else{
				// we have to determine it
				hostName = addr.getCanonicalHostName();
			}
			return hostName;
		}catch(Exception ex){
			// something is really bad here
			return requestIP;	
		}
	}
	
	/** 
	 * update peer's parameters based on the received http request that looks like this:
	 * LONG=-87.60 Groups=gloriad LAT=41.88 MLversion=1.4.8-200512132130 MLdate=2005-12-13 TraceOpts= HostName=monalisa-chi.uslhcnet.org FarmIP=192.65.196.67 FarmName=GLORIAD
	 */ 
	synchronized public void updateDetails(HttpServletRequest request){
		lastRefresh = System.currentTimeMillis();
		keptAlive = false;
		StringBuilder sb = new StringBuilder("["+request.getRemoteAddr()+"] updateDetails:");
		for(Enumeration enp = request.getParameterNames(); enp.hasMoreElements(); ) {
			String param = (String) enp.nextElement();
			String [] values = request.getParameterValues(param);
			Vector vValues = null;
			String value = "!NULL!";
			if(values != null){
				if(values.length == 1)
					value = values[0];
				else if(values.length > 1){
					vValues = vectFromStrArray(values);
					value = vectorToStr(vValues);
				}
			}
			sb.append(" "+param+"="+value);
			
			if(param.toLowerCase().equals("farmip")){
				farmIP = value;
			}else if(param.toLowerCase().equals("farmname")){
				farmName = value;
			}else if(param.toLowerCase().equals("groups")){
				groups = splitString(value);
			}else if(param.toLowerCase().equals("traceopts")){
				traceOpts = value;
			}else if(param.toLowerCase().equals("requestip")){
				requestIP = value;
			}else if(param.toLowerCase().equals("hostname")){
				hostName = value;
			}else if(param.toLowerCase().equals("mlversion")){
				mlVersion = value;
			}else if(param.toLowerCase().equals("mldate")){
				mlDate = value;
			}else if(param.toLowerCase().equals("long")){
				LONG = value;
			}else if(param.toLowerCase().equals("lat")){
				LAT = value;
			}else if(param.toLowerCase().equals("peerstat")){
				peersStat = vValues;
			}else if(param.toLowerCase().equals("keepalive")){
				keptAlive = true;
			}
		}
		hostName = getRealHostName(hostName, requestIP);
		System.out.println(sb.toString());
	}

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return requestIP.hashCode();
    }
}
