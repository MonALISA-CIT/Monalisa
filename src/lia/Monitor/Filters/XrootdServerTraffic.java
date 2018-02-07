package lia.Monitor.Filters;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Format;
import lazyj.cache.ExpirationCache;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;
/**
 * This filter aggregates Xrootd server traffic in several categories:<br>
 * <ul>
 * <li>Total traffic per IP C-class, IN and OUT to the Xrootd cluster</li>
 * <li>Traffic aggregated per site (associating the client IP to the closest site)</li>
 * <li>Total traffic to LAN and to WAN, and overall IN and OUT</li>
 * </ul>
 */
public class XrootdServerTraffic extends GenericMLFilter {
	/**
	 * 
	 */
	private static final long serialVersionUID = 616313623299971328L;

	/** Logger used by this class */
    static final transient Logger logger = Logger.getLogger("lia.Monitor.Filters.XrootdServerTrafficAggregator");

	private static final class Client {
		public double volume_in;
		public double volume_out;
		public final AtomicLong in_count;
		public final AtomicLong out_count;

		public Client() {
			this.volume_in = 0;
			this.volume_out = 0;

			this.in_count = new AtomicLong(0);
			this.out_count = new AtomicLong(0);
		}

		public void joinClients(final Client x) {
			this.volume_in += x.volume_in;
			this.volume_out += x.volume_out;
			this.in_count.addAndGet(x.in_count.get());
			this.out_count.addAndGet(x.out_count.get());
		}
			
		public void addDataIn(final double in) {
			this.volume_in += in;
			this.in_count.getAndIncrement();
		}

		public void addDataOut(final double out) {
			this.volume_out += out;
			this.out_count.getAndIncrement();
		}
		
		public void fillResult(final Result r, final String prefix){
			r.addSet(prefix+"IN", volume_in / REPORT_INTERVAL);
			r.addSet(prefix+"OUT", volume_out / REPORT_INTERVAL);
			
			r.addSet(prefix+"IN_freq", (double) in_count.get() / REPORT_INTERVAL);
			r.addSet(prefix+"OUT_freq", (double) out_count.get() / REPORT_INTERVAL);
		}
	}

	private final static String Name = "XroodServerTraffic";

	private final Map<InetAddress, Client> clientsOnline = new HashMap<InetAddress, Client>();
	private final Map<InetAddress, Client> clientsOffline = new HashMap<InetAddress, Client>();

	@SuppressWarnings("hiding")
	String farmName = null;
	/**
	 * Predicates for filtering online data
	 */

	private monPredicate[] monPreds = null;

	/**
	 * Constructor for the ExLoadFilter object
	 * 
	 * @param farmName
	 *            Description of the Parameter
	 */
	public XrootdServerTraffic(final String farmName) {
		super(farmName);
		
		monPreds = new monPredicate[] { new monPredicate("*", "XrdServers", "*", -1, -1, new String[] { "transf_client_ip", "transf_rd_mbytes", "transf_wr_mbytes" }, null) };
	}

	/**
	 * Override from GenericMLFilter
	 * 
	 * @return The name of this Filter
	 */
	@Override
	public String getName() {
		return Name;
	}

	/**
	 * Override from GenericMLFilter Gets the filterPred attribute of the ExLoadFilter object
	 * 
	 * @return A vector of monPredicate(s) for filtering real-time data
	 */
	@Override
	public monPredicate[] getFilterPred() {
		return monPreds;
	}

	private static final long REPORT_INTERVAL = 60*2;
	
	/**
	 * Override from GenericMLFilter
	 * 
	 * @return how often should expressResults be called (in millis)
	 */
	@Override
	public long getSleepTime() {
		return REPORT_INTERVAL * 1000;
	}

	/**
	 * Override from GenericMLFilter
	 * 
	 * @param o
	 *            A Result or a Vector of Result(s)
	 */
	@Override
	public void notifyResult(final Object o) {
		if (o != null) {
			if (o instanceof Vector) {
				final Vector<?> v = (Vector<?>) o;

				for (final Object el : v) {
					notifyResult(el);
				}
			}
			else
				if (o instanceof Result) {
					final Result r = (Result) o;
					
					InetAddress ia = null;
					double in = 0;
					double out = 0;
					
					for (int i=0; i<r.param_name.length; i++){
						if (r.param_name[i].equals("transf_client_ip")){
							long ip = (long) r.param[i];
							
							final byte[] ipBuffer = new byte[4];
							
							// this is intentionally left to 3 iterations in order to have IP C-class aggregation 
							for (int j=0; j<3; j++){
								ipBuffer[j] = (byte) (ip % 256);
								ip = ip >> 8;
							}
							
							try{
								ia = InetAddress.getByAddress(ipBuffer);
							}
							catch (final UnknownHostException uhe){
								// ignore, it is built from bytes, should be ok
							}
						}
						else
						if (r.param_name[i].equals("transf_rd_mbytes")){
							out = r.param[i];
						}
						else
							in = r.param[i];
					}
					
					if (ia!=null && (in + out)>0){
						synchronized (clientsOnline){
							Client client = clientsOnline.get(ia);
							
							if (client==null){
								client = new Client();
								clientsOnline.put(ia, client);
							}
							
							if (in>0)
								client.addDataIn(in);
							
							if (out>0)
								client.addDataOut(out);
						}
					}
				}
		}
	}

	private void exchangeBuffers() {
		clientsOffline.clear();

		synchronized (clientsOnline) {
			clientsOffline.putAll(clientsOnline);
			clientsOnline.clear();
		}
	}

	@Override
	public Object expressResults() {

		exchangeBuffers();

		if (clientsOffline.size() == 0) {
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, " [ " + getName() + " ] " + new Date() + " End expressResults ... NO Results got since last iteration ... returning null!");
			
			return null;
		}

		final Vector<Result> ret = new Vector<Result>();
		
		final Map<String, Client> aggregateBySite = new HashMap<String, Client>();
		
		final Client WAN = new Client();
		final Client LAN = new Client();
		
		final String thisSite = getSite(null);
		
		// aggregation by IP C-class
		for (final Map.Entry<InetAddress, Client> entry: clientsOffline.entrySet()){
			final InetAddress addr = entry.getKey();
			final Client client = entry.getValue();
			
			String site;
			
			if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isMulticastAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()){
				site = thisSite;
			}
			else{
				String name = addr.getHostAddress();
				name = name.substring(0, name.lastIndexOf('.'));
				
				site = getSite(name);
			}
			
			if (site!=null){
				Client siteData = aggregateBySite.get(site);
				
				if (siteData==null){
					siteData = new Client();
					aggregateBySite.put(site, siteData);
				}
				
				siteData.joinClients(client);
				
				if (site.equals(farmName) || site.equals(this.farm.name) || site.equals(thisSite))
					LAN.joinClients(client);
				else
					WAN.joinClients(client);
			}
			
			final Result r = createResult("client_C_class");
			
			client.fillResult(r, addr.getHostAddress()+"_");
			
			ret.add(r);
		}
		
		// aggregation by site
		final Result bySite = createResult("site"); 
				
		for (final Map.Entry<String, Client> entry : aggregateBySite.entrySet()) {
			entry.getValue().fillResult(bySite, entry.getKey()+"_");
		}
		
		ret.add(bySite);

		final Result lanWan = createResult("lan_wan");
		
		LAN.fillResult(lanWan, "LAN_");
		WAN.fillResult(lanWan, "WAN_");

		ret.add(lanWan);
		
		LAN.joinClients(WAN);
		
		final Result total = createResult("sum");
		LAN.fillResult(total, "TOTAL_");
		
		ret.add(total);
		
		return ret;
	}
	
	private final Result createResult(final String nodeName){
		final Result ret = new Result(farmName, "XrdServers_Aggregate", nodeName, "XrootdServerTraffic");
		ret.time = NTPDate.currentTimeMillis();
		
		return ret;
	}

	private static final ExpirationCache<String, String> CCLASS_TO_SITE = new ExpirationCache<String, String>();

	private static final String REPOSITORY_URL = AppConfig.getProperty("repository.url", "http://alimonitor.cern.ch/");
	
	private static URL serviceURL = null;
	
	private static final String getSite(final String cClass) {
		String site = CCLASS_TO_SITE.get(cClass);

		if (site != null)
			return site;
		
		if (serviceURL == null){
			try{
				serviceURL = new URL(REPOSITORY_URL+"services/getClosestSite.jsp");
			}
			catch (final MalformedURLException e){
				return "Unknown";
			}
		}

		URLConnection conn = null;
		
		try {
			conn = serviceURL.openConnection();
			
			if (cClass!=null && cClass.length()>0){
				conn.setDoOutput(true);
			
				final String query = "ip="+Format.encode(cClass);

				final OutputStream output = conn.getOutputStream();
				output.write(query.getBytes());
				output.flush();
				output.close();
			}
			
			final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			
			site = br.readLine();

			br.close();
			
			if (site==null || site.length() == 0 || site.equals("null"))
				site = "Unknown";

			if (site.equals("Unknown") && logger.isLoggable(Level.WARNING))
				logger.log(Level.WARNING, "Could not get the site for "+cClass);
		}
		catch (final IOException ioe) {
			logger.log(Level.WARNING, "Exception resolving this class: "+cClass);
			site = "Unknown";
		}

		CCLASS_TO_SITE.put(cClass, site, site.equals("Unknown") ? 1000 * 60 * 5 : 1000 * 60 * 60);

		return site;
	}
	
	public static void main(String[] args) {
		System.err.println(getSite("90.147.66"));
	}
}
