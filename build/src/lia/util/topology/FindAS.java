package lia.util.topology;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;
import lia.web.utils.MailDate;
import lia.web.utils.ThreadedPage;

public class FindAS extends ThreadedPage {

	private final String topoPath = AppConfig.getProperty("lia.util.topology.path", "/usr/lib/cgi-bin"); 
	private final String asLatLongFile = topoPath + "/aslatlong.txt";

	Hashtable asCache;
	Hashtable asIntervalCache;
	
	public FindAS(){
		super();
		asCache = new Hashtable();
		asIntervalCache = new Hashtable();
		loadCache();
	}
	
	/** loads the cache from file */
	private void loadCache() {
		try{
			BufferedReader br = new BufferedReader(new FileReader(asLatLongFile));
			String line = null;
			while((line = br.readLine()) != null){
				line = line.trim();
				if(line.startsWith("#"))
					continue;
				int d = line.indexOf("\t");
				if(d > 0){
					String as = line.substring(0, d);
					if(as.indexOf(" - ") > 0)
						asIntervalCache.put(as, line.substring(d));
					else
						asCache.put(as, line.substring(d));
				}
			}
			br.close();
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
		if(enp.hasMoreElements()){
			String p = (String) enp.nextElement();
			
			StringTokenizer stk = new StringTokenizer(p, " ");
			while(stk.hasMoreTokens()){
				String as = stk.nextToken();
				String rez = resolveAS(as);
				if(rez != null)
					pwOut.println(rez);
			}
		}
		pwOut.flush();
		bAuthOK = true;
	}

	/** return from aslatlong.txt the line corresponding to the given as */
	private String resolveAS(String as) {
		String rez = (String) asCache.get(as);
		if(rez == null){
			int asn = Integer.parseInt(as);
			for(Iterator asIt = asIntervalCache.keySet().iterator(); asIt.hasNext(); ){
				String asRange = (String) asIt.next();
				int min = Integer.parseInt(asRange.substring(0, asRange.indexOf(" - ")));
				int max = Integer.parseInt(asRange.substring(asRange.indexOf(" - ")+3));
				if(min <= asn && asn <= max){
					rez = (String) asIntervalCache.get(asRange);
					break;
				}
			}
		}
		if(rez != null)
			return as + rez;
		return null;
	}
}
