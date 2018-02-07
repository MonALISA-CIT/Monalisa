package lia.util.ABPing;

import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

/**
 * Describes a ABPing Peer.  
 * @author catac
 */
public class ABPingPeer {

	String farmName = "";
	Vector groups = new Vector();
	String farmIP = "";
	String requestIP;
	String hostName = "";
	String mlVersion = "";
	String mlDate = "";
	double LONG;
	double LAT;
	boolean keptAlive = false;
	Vector peersStat = new Vector();	// status of its peers, as reported by the module
	Vector wantPeers = new Vector();	// list of peers, wanted by the user (specified in the orig conf file)
	Vector configPeers = new Vector();	// given peers by the config servlet; we remember them to give the same peers if nothing bad happens 
	long lastRefresh;					// last refresh time
	
	public ABPingPeer(String requestIP){
		this.requestIP = requestIP;
		this.hostName = getRealHostName(null, requestIP);
	}
	
	/** return true if the peer is still alive */
	public boolean isAlive(){
		long now = System.currentTimeMillis();
		return now - lastRefresh < ABPingAutoConfig.PEER_TIMEOUT;
	}
	
	/** return true if the peer can be removed from hashes */
	public boolean isExpired(){
		long now = System.currentTimeMillis();
		return now - lastRefresh > 20 * ABPingAutoConfig.PEER_TIMEOUT;
	}
	
	/** return true if this.groups has a group contained in the given toMatch Vector */
	public boolean matchesGroups(Vector toMatch){
		for(Iterator git = this.groups.iterator(); git.hasNext(); )
			if(toMatch.contains(git.next()))
				return true;
		return false;
	}
	
	public boolean equals(Object other){
		return (other instanceof ABPingPeer)
			&& (requestIP.equals(((ABPingPeer) other).requestIP))
			&& (farmIP.equals(((ABPingPeer) other).farmIP));
	}
	
	public String toString(){
		return (farmName.length() > 0 ? farmName+"@" : "") + hostName;
	}
	
	public String getStatus(){
		long now = System.currentTimeMillis();
		String time = isAlive() ? 
				"" + (now - lastRefresh) / 1000 / 60 + " min ago" :
				"died on "+new Date(lastRefresh);
		return farmName+" ["+hostName+"/"+farmIP+" requestIP="+requestIP+"] ML: "+mlVersion+"/"+mlDate+" Location: "+LONG+"/"+LAT+"\n"
			+"Kept Alive : "+keptAlive+"\n"
			+"is banned  : "+ABPingAutoConfig.isBanned(this)+"\n"
			+"Groups: ["+vectorToStr(groups)+"]\n"
			+"Last Update: "+time+"\n"
			+"Given peers: "+configPeers+"\n"
			+"Peers stats: "+peersStat+"\n"
			+"Want peers : "+wantPeers+"\n";
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
	public void updateDetails(HttpServletRequest request){
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
			}else if(param.toLowerCase().equals("requestip")){
				requestIP = value;
			}else if(param.toLowerCase().equals("hostname")){
				hostName = value;
			}else if(param.toLowerCase().equals("mlversion")){
				mlVersion = value;
			}else if(param.toLowerCase().equals("mldate")){
				mlDate = value;
			}else if(param.toLowerCase().equals("long")){
				try{
					LONG = Double.parseDouble(value);
				}catch(NumberFormatException ex){
					LONG = 0;
				}
			}else if(param.toLowerCase().equals("lat")){
				try{
					LAT = Double.parseDouble(value);
				}catch(NumberFormatException ex){
					LAT = 0;
				}
			}else if(param.toLowerCase().equals("peerstat")){
				peersStat = vValues;
			}else if(param.toLowerCase().equals("keepalive")){
				keptAlive = true;
			}
		}
		hostName = getRealHostName(hostName, requestIP);
		System.out.println(sb.toString());
	}
	
	/** return the distance to this peer, considering the LONG/LAT values */
	public int distanceTo(ABPingPeer ap){
		double LONGdiff = Math.abs(LONG - ap.LONG) > 180 ? 360 - LONG + ap.LONG : LONG - ap.LONG;
		double LATdiff = Math.abs(LAT - ap.LAT) > 90 ? 180 - LAT + ap.LAT : LAT - ap.LAT;
		return (int) Math.sqrt(LONGdiff*LONGdiff +LATdiff*LATdiff);
	}
	
	/** refresh the configured peers of this node */
	public void refreshConfigPeers(){
		Vector alivePeers = new Vector();
		for(Iterator pit = wantPeers.iterator(); pit.hasNext(); ){
			ABPingPeer ap = (ABPingPeer) pit.next();
			if((ap != this) && ap.isAlive() && (! alivePeers.contains(ap))
					&& (! ABPingAutoConfig.isBanned(ap)))
				alivePeers.add(ap);
		}
		// now check that my current peers are alive and while alivePeers.size() < MIN_PEERS
		// add them to the livePeers if not already there. This way make sure that if
		// previously we randomly selected a peer (see below), we will still use it, 
		// if it's still alive. This should prevent oscillations.
		for(int i=0; (i < configPeers.size()) && (alivePeers.size() < ABPingAutoConfig.MIN_PEERS_NR); i++){
			ABPingPeer p = (ABPingPeer) configPeers.get(i);
			if(p.isAlive() && (! alivePeers.contains(p))
					&& (! ABPingAutoConfig.isBanned(p)))
				alivePeers.add(p);
		}
		// we still have to check if we have MIN_PEERS_NR peers, and if not, select
		// some other peers, until this condition is met.
		if(alivePeers.size() < ABPingAutoConfig.MIN_PEERS_NR){
			Vector pPeers = ABPingAutoConfig.getPreferredPeers(this);
			Iterator tmpit = pPeers.iterator();
			// then add random peers until one of the conditions breaks
			while((alivePeers.size() < ABPingAutoConfig.MIN_PEERS_NR) && (tmpit.hasNext())){
				alivePeers.add(tmpit.next());
			}
		}
		// now we have to transfer alivePeers to myPeers and make sure that each 
		// of my peers has me as its peer also - to avoid asymmetric links.
		// add the new peers
		for(Iterator pit = alivePeers.iterator(); pit.hasNext(); ){
			ABPingPeer p = (ABPingPeer) pit.next();
			addPeer(p); // it won't add it twice
			p.addPeer(this);
		}
		// remove unwanted peers (if possible)
		for(Iterator pit = configPeers.iterator(); pit.hasNext(); ){
			ABPingPeer p = (ABPingPeer) pit.next();
			if(! alivePeers.contains(p)){
				if(p.canRemovePeer(this)){
					pit.remove();
					p.removePeer(this);
//					System.err.println("removing peer: "+p);
				}else{
//					System.err.println("cannot remove peer: "+p);
				}
			}
		}
	}
	
	/** Adds the given peer to the list with my peers. */
	private void addPeer(ABPingPeer p){
		if(! configPeers.contains(p))
			configPeers.add(p);
	}
	
	/** Remove from the list with my peers */
	private void removePeer(ABPingPeer p){
		configPeers.remove(p);
	}
	
	/** Test if this peer can be removed */
	private boolean canRemovePeer(ABPingPeer p){
		return (! isAlive()) || (! p.isAlive()) || 
			((! wantPeers.contains(p)) && (configPeers.size() > ABPingAutoConfig.MIN_PEERS_NR) &&
			 (! p.wantPeers.contains(this)) && (p.configPeers.size() > ABPingAutoConfig.MIN_PEERS_NR));
	}

    @Override
    public int hashCode() {
        return requestIP.hashCode();
    }

}
