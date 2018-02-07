package lia.util.topology;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

public class FindIP extends ThreadedPage {

	private static final String topoPath = AppConfig.getProperty("lia.util.topology.path", "/usr/lib/cgi-bin"); 
	private final static String cacheFile = topoPath + "/whoisCache.txt";
	
	private static final String cmdWrapper = AppConfig.getProperty("lia.util.MLProcess.CMD_WRAPPER", null);
	
	private static Hashtable ipCache;
	
	static {
		// do this only once
		synchronized (FindIP.class) {
			ipCache = new Hashtable();
			loadCache();
		}
	}
	
	public FindIP(){
		super();
	}
	
	/** load whois cache from the saved file */
	private static void loadCache() {
		try{
			BufferedReader br = new BufferedReader(new FileReader(cacheFile));
			String line = null;
			while((line = br.readLine()) != null){
				line = line.trim();
				int d = line.indexOf(":");
				if(d > 0){
					String ip = line.substring(0, d);
					StringBuilder crtDescr = (StringBuilder) ipCache.get(ip);
					if(crtDescr == null){
						crtDescr = new StringBuilder();
						ipCache.put(ip, crtDescr);
					}
					crtDescr.append(line.substring(d+1));
					crtDescr.append("\n");
				}
			}
			br.close();
		}catch(IOException ex){
			ex.printStackTrace();
		}
	}

	/** saves the cache to the known file */
	private void saveCache() {
		try{
			PrintWriter pw = new PrintWriter(new FileOutputStream(cacheFile));
			synchronized (ipCache) {
				for(Enumeration enh = ipCache.keys(); enh.hasMoreElements(); ){
					String ip = (String) enh.nextElement();
					StringBuilder descr = (StringBuilder) ipCache.get(ip);
					StringTokenizer stk = new StringTokenizer(descr.toString(), "\n");
					while(stk.hasMoreTokens()){
						String line = stk.nextToken();
						if(line.trim().length() > 0)
							pw.println(ip+":"+line);
					}
				}
			}
			pw.close();
		}catch(IOException ex){
			ex.printStackTrace();
		}
	}
	
	public void doInit() {
		response.setHeader("Expires", "0");
		response.setHeader("Last-Modified", (new MailDate(new Date())).toMailString());
		response.setHeader("Cache-Control", "no-cache, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setContentType("text/plain");
	}

	public void execGet() {
		Enumeration enp = request.getParameterNames(); 
		String remoteIP = request.getRemoteAddr();
		if(enp.hasMoreElements()){
			String p = (String) enp.nextElement();
			
			StringTokenizer stk = new StringTokenizer(p, " ");
			while(stk.hasMoreTokens()){
				String ip = stk.nextToken();
				if(ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.") 
				        	|| (ip.compareTo("172.16.0.0") >= 0 && ip.compareTo("172.32.0.0") < 0)){
				    ip = remoteIP;
				    pwOut.println("publicIP:   "+ip);
				}
				pwOut.println(resolveIP(ip));
			}
		}
		pwOut.flush();
		bAuthOK = true;
	}

	/** search this ip in cache and if not found, use whois and update cache */
	private String resolveIP(String ip) {
		StringBuilder rez = (StringBuilder)ipCache.get(ip);
		if(rez == null){
			rez = resolveWhois(ip);
			try{
				InetAddress addr = InetAddress.getByName(ip);
				rez.append("hostName:     "+addr.getCanonicalHostName());
				rez.append("\n");
			}catch(IOException ex){
				System.err.println("Error resolving "+ip);
				ex.printStackTrace();
			}
			ipCache.put(ip, rez);
			saveCache();
		}
		return rez.toString();
	}

	/** use whois to get info about this IP */
	private StringBuilder resolveWhois(String ip) {
		String whoisCmd = cmdWrapper != null ? cmdWrapper +" -t 20 whois " : " whois ";
		String whoisSuffix = cmdWrapper != null ? " 2>/dev/null " : "";
		String cmd = "( "+whoisCmd + ip + whoisSuffix + " | grep -E -i '^(descr|netname):' ;" +
                	 whoisCmd +" -h whois.radb.net " + ip + whoisSuffix +
					 " | grep -E -i '^origin:' ) | sort -f | uniq -i";
		StringBuilder rez = new StringBuilder();
		try {
			BufferedReader inBuf = IpClassifier.runCommand(cmd);
			String line = null;
			while((line = inBuf.readLine()) != null){
				line = line.trim();
				rez.append(line);
				rez.append("\n");
			}
		}catch(IOException ex){
			ex.printStackTrace();
		}
		return rez;
	}

}
