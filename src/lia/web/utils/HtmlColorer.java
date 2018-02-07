/**
 * 
 */
package lia.web.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author costing
 * @since Mar 14, 2007
 */
public class HtmlColorer {
	
	/**
	 * Process a log text and produce the HTML version of it
	 * 
	 * @param sText
	 * @return colored version of the text
	 */
	public static final String logColorer(final String sText){
		if (sText==null || sText.length()==0)
			return sText;
		
		BufferedReader br = new BufferedReader(new StringReader(sText));
		
		StringBuilder sb = new StringBuilder(sText.length());
		
		String sLine;
		
		try{
			while ( (sLine = br.readLine()) != null){
				sb.append(logLineColorer(sLine)).append("<br>\n");
			}
		}
		catch (IOException e){
			// ignore
		}
		
		try{
			br.close();
		}
		catch (IOException e){
			// ignore
		}
		
		return sb.toString();
	}

	// Mar 14 11:08:13
	// Wed Mar 14 13:48:02 2007
	// Wed Mar 14 13:48:02 CET 2007
	private static final Pattern pDateTime1 = Pattern.compile("([A-Z][a-z]{2}\\s)?[A-Z][a-z]{2}\\s+[0-3]?[0-9]\\s[0-2]?[0-9]:[0-5][0-9](:[0-5][0-9]((\\s[A-Z]+)?\\s[1-2][0-9]{3})?)?");

	// Mar 14, 2007 11:19:32 AM
	private static final Pattern pDateTime2 = Pattern.compile("[A-Z][a-z]{2}\\s+[0-3]?[0-9],\\s+[1-2][0-9]{3}\\s+[0-2]?[0-9]:[0-5][0-9]:[0-5][0-9](\\s+(AM|PM))?");

	// 070306 15:40:12
	private static final Pattern pDateTime3 = Pattern.compile("[0-9]{6}\\s+[0-2]?[0-9]:[0-5][0-9]:[0-5][0-9]");

	// 2009-11-22 15:40:12
	private static final Pattern pDateTime4 = Pattern.compile("20[0-9]{2}-[01]?[0-9]-[012]?[0-9]\\s+[0-2]?[0-9]:[0-5][0-9]:[0-5][0-9]");

	// 22.11.2009 15:40:12
	// 11/22/2009 11:20:30
	private static final Pattern pDateTime5 = Pattern.compile("[012]?[0-9][./][012]?[0-9][./]20[0-9]{2}\\s+[0-2]?[0-9]:[0-5][0-9]:[0-5][0-9]");

	
	// 13:44:21
	private static final Pattern pTime1 = Pattern.compile("(?<![.0-9])[0-2]?[0-9]:[0-5][0-9](:[0-5][0-9])?(?![.0-9])");
		
	private static final String IP1="(25[0-5])|(2[0-4][0-9])|(1[0-9][0-9])|([1-9][0-9])|([1-9])";
	private static final String IP2="(25[0-5])|(2[0-4][0-9])|(1[0-9][0-9])|([1-9][0-9])|([0-9])";
	
	private static final String PORT="(:[1-9][0-9]{0,4}(?![.0-9]))";
	
	private static final String IP="(?<![.0-9])("+IP1+")(\\.("+IP2+")){3}(?![.0-9])"+PORT+"?";
	
	// 128.142.197.35
	// 128.142.197.35:12345
	private static final Pattern pIP = Pattern.compile(IP);
	
	private static final String HOST = "([-0-9a-zA-Z]*[a-zA-Z][-0-9a-zA-Z]*)";
	
	private static final String HOSTNAME = HOST+"(\\."+HOST+"){1,}(\\.[a-zA-Z]{2,4})(?![-.0-9a-zA-Z])"+PORT+"?";
	
	private static final Pattern pHOST_OR_IP = Pattern.compile("(?<!<a[^>]{0,1000})(("+HOSTNAME+")|("+IP+"))");

	private static final String URL = "([a-zA-Z]+://|mailto:)(\\w+(:\\w+)?@)?[-a-zA-Z0-9]+(\\.[-a-zA-Z0-9]+)*(:[0-9]{1,5})?(/[-._+%:a-zA-Z0-9]*)*(\\?([\\w%:]+=[-_\\w%+:]*(\\&amp;[\\w%:]+=[-_\\w%+:]*)*))?";
	
	private static final Pattern pURL = Pattern.compile(URL);
	
	private static final Pattern ALIROOT_ERROR = Pattern.compile("^[EF]-\\w+::\\w+: .*");
	private static final Pattern ALIROOT_WARN  = Pattern.compile("^W-\\w+::\\w+: .*");
		
	/**
	 * Process one line of log and produce the HTML version of it
	 * 
	 * @param line
	 * @return processed log line
	 */
	public static String logLineColorer(final String line) {
		// date highlighting
		String sResult = Formatare.tagProcess(line);

		sResult = findWithBoundaries(sResult, "&apos;", "&apos;", "<I>", "</I>");
		sResult = findWithBoundaries(sResult, "&quot;", "&quot;", "<I>", "</I>");
		
		sResult = highlightPattern(sResult, pDateTime1, "<font color='#2A1FAA'><b>", "</b></font>");
		sResult = highlightPattern(sResult, pDateTime2, "<font color='#2A1FAA'><b>", "</b></font>");
		sResult = highlightPattern(sResult, pDateTime3, "<font color='#2A1FAA'><b>", "</b></font>");
		sResult = highlightPattern(sResult, pDateTime4, "<font color='#2A1FAA'><b>", "</b></font>");
		sResult = highlightPattern(sResult, pDateTime5, "<font color='#2A1FAA'><b>", "</b></font>");
		
		sResult = highlightPattern(sResult, pTime1, "<font color='#631BAA'><b>", "</b></font>");

		sResult = highlightPattern(sResult, pURL, "<a target=_blank href='${MATCH}'>", "</a>");
		
		//sResult = highlightPattern(sResult, pIP, "<B>", "</B>");
		sResult = highlightHost(sResult);
		
		//sResult = highlightPattern(sResult, pHOSTNAME, "<B>", "</B>");
		
		final String sLineLower = line.toLowerCase(); 
		
		if (sLineLower.indexOf("error")>=0 || sLineLower.indexOf("fail")>=0 || ((sLineLower.startsWith("e-") || sLineLower.startsWith("f-")) && ALIROOT_ERROR.matcher(line).matches()))
			sResult = "<font color='#C31010'>"+sResult+"</font>";
		else
		if (sLineLower.indexOf("warn")>=0 || sLineLower.indexOf("cannot")>=0 || sLineLower.startsWith("!") || (sLineLower.startsWith("w-") && ALIROOT_WARN.matcher(line).matches()))
			sResult = "<font color='#C7692B'>"+sResult+"</font>";
		else
		if (sLineLower.indexOf("success")>=0 || sLineLower.indexOf("succeed")>=0)
			sResult = "<font color='#3F9840'>"+sResult+"</font>";

		if (sLineLower.startsWith("* "))
			sResult = "<B>"+sResult+"</B>";
		else
		if (sLineLower.startsWith("//") || sLineLower.startsWith("_ "))
			sResult = "<I>"+sResult+"</I>";
		else
		if (sLineLower.startsWith("*! ") && !sResult.startsWith("<"))
			sResult = "<font color='#C7692B'><b>"+sResult+"</b></font>";
		
		return sResult;
	}
	
	private static String highlightHost(final String sLine){
		final StringBuilder sb = new StringBuilder(sLine.length());
		
		final Matcher m = pHOST_OR_IP.matcher(sLine);
		
		int iLastIndex = 0;
		
		while (m.find(iLastIndex)){
			sb.append(sLine.substring(iLastIndex, m.start()));
			
			final String sDisplayName = sLine.substring(m.start(), m.end()); 

			String sHost = sDisplayName;
			
			int iPort = -1;
			
			if (sHost.indexOf(':')>0){
				try{
					iPort = Integer.parseInt(sHost.substring(sHost.indexOf(':')+1));
				}
				catch (Exception e){
					// ignore
				}

				sHost = sHost.substring(0, sHost.indexOf(':'));
			}

			final String sSuffix = sHost.substring(sHost.lastIndexOf('.')+1).toLowerCase();

			boolean bOk = true;
			
			// add here all the well-known file extensions that would otherwise mess up the display
			if (sSuffix.equals("log") || sSuffix.equals("root") || sSuffix.equals("txt") || sSuffix.equals("out") || sSuffix.equals("jdl"))
				bOk = false;
			
			String sLookup = sHost;

			if (bOk) {
				if (pIP.matcher(sLookup).matches()) {
					sLookup = ThreadedPage.getHostName(sHost);

					if (sLookup.equals(sHost))
						sLookup = "No reverse for this IP";
				} else {
					try {
						sLookup = InetAddress.getByName(sHost).getHostAddress();
					} catch (Exception e) {
						sLookup = "Cannot resolve this name";
					}
				}

				String sService = getService(iPort);

				if (sService == null && iPort!=-1) {
					if (iPort <= 0 || iPort > 65535)
						bOk = false;
					else
						sService = "unknown";
				}

				if (sService != null)
					sLookup += ":" + sService;
			}
			
			if (bOk)
				sb.append("<span title='").append(sLookup).append("'><b>");
			
			sb.append(sDisplayName);
			
			if (bOk)
				sb.append("</b></span>");

			iLastIndex = m.end();
		}
		
		sb.append(sLine.substring(iLastIndex));
		
		return sb.toString();		
	}
	
	private static HashMap<Integer, String> hmPorts = null;
	
	/**
	 * Get the service name for a given port number
	 * 
	 * @param iPort
	 * @return service name, from /etc/services + an internal list of AliEn services
	 */
	public static final String getService(final int iPort){
		if (iPort<=0 || iPort>65535)
			return null;
		
		switch (iPort){
			case 1094:
			case 1095:
				return "xrootd";
			case 3123:
				return "olbd";
			case 5001:
				return "rfiod";
			case 5015:
				return "dpm";
			case 8389:
				return "LDAP";
			case 8050:
				return "HTTP Container";
			case 8080:
				return "Authen";
			case 8081:
				return "IS";
			case 8083:
				return "JobManager";
			case 8084:
				return "ClusterMonitor";
			case 8088:
				return "ProxyServer";
			case 8089:
				return "Logger";
			case 8092:
				return "SE";
			case 8095:
				return "TransferManager";
			case 8097:
				return "TransferOptimizer";
			case 8098:
				return "JobOptimizer";
			case 8099:
				return "CatalogueOptimizer";
			case 9991:
				return "PackMan";
			case 3307:
				return "MySQL Catalogue";
			case 3308:
				return "MySQL TaskQueue";
			case 8883:
				return "FTD";
			case 8884:
				return "ApMon";
			case 4160:
			case 8765:
				return "ML LUS";
			case 6001:
				return "ML Proxy";
			case 10000:
				return "API Service";
		}
		
		if (hmPorts==null){
			hmPorts = new HashMap<Integer, String>();
			
			BufferedReader br = null;
			
			try{
				br = new BufferedReader(new FileReader("/etc/services"));
				
				String sLine;
				
				while ( (sLine = br.readLine()) != null ){
					sLine = sLine.trim();
					
					if (sLine.startsWith("#") || sLine.length()==0)
						continue;
					
					StringTokenizer st = new StringTokenizer(sLine, " \t\r\n/#");
					
					if (st.countTokens()<3)
						continue;
					
					try{
						String sName = st.nextToken();
						String sPort = st.nextToken();
						
						hmPorts.put(Integer.valueOf(sPort), sName);
					}
					catch (Exception e1){
						// ignore
					}
				}
			}
			catch (IOException e){
				// ignore
			}
			finally{
				if (br!=null){
					try{
						br.close();
					}
					catch (IOException ioe){
						// ignore
					}
				}
			}
		}
		
		return hmPorts.get(Integer.valueOf(iPort));
	}
	
	/**
	 * @param sLine
	 * @param p
	 * @param sPreffix
	 * @param sSuffix
	 * @return formatted pattern
	 */
	public static String highlightPattern(final String sLine, final Pattern p, final String sPreffix, final String sSuffix){
		final StringBuilder sb = new StringBuilder(sLine.length());
		
		final Matcher m = p.matcher(sLine);
		
		int iLastIndex = 0;
		
		while (m.find(iLastIndex)){
			final String sMatch = sLine.substring(m.start(), m.end());
	
			sb.append(sLine.substring(iLastIndex, m.start()));
			sb.append(Formatare.replace(sPreffix, "${MATCH}", sMatch));			
			sb.append(sMatch);
			sb.append(Formatare.replace(sSuffix, "${MATCH}", sMatch));
			
			iLastIndex = m.end();
		}
		
		sb.append(sLine.substring(iLastIndex));
		
		return sb.toString();
	}
	
	private static String findWithBoundaries(final String sLine, final String sStart, final String sEnd, final String sPrefix, final String sSuffix){
		final StringBuilder sb = new StringBuilder(sLine.length());
		
		int iLastIndex = 0;
		
		int idx;
		
		while ( (idx = sLine.indexOf(sStart, iLastIndex)) > 0){
			int idx2 = sLine.indexOf(sEnd, idx+sStart.length());
			
			if (idx2>0){
				idx2 += sEnd.length();
				sb.append(sLine.substring(iLastIndex, idx));
				sb.append(sPrefix);
				sb.append(sLine.substring(idx, idx2));
				sb.append(sSuffix);
				iLastIndex = idx2;
			}
			else
				break;
		}
		
		sb.append(sLine.substring(iLastIndex));
		
		return sb.toString();
	}
	
	/**
	 * debug method
	 * 
	 * @param args
	 */
	public static void main(final String args[]){
		//System.err.println(IP);
		
		System.err.println(logLineColorer("=> Trying to connect to Server [3] root://137.138.99.170:10000 as User alicesgm "));
		System.err.println(logLineColorer("Mar 14 11:22:57  info  128.142.137.123 'Updating the jobagent' : \"lastHeard\" : Wed Mar 14 13:48:02 CET 2007 seqNr: 1"));
		System.err.println(logLineColorer("x.111.2.3.4 1.2.3.4 12:33:44 1.2.3.4. 1.2.3.4"));
		System.err.println(logLineColorer("blabla 137.138.99.139 blabla 137.138.99.139:123 alien.cern.ch alien.cern.ch:1324 bubu.com a.bubu.com a.bubu.coomm"));
		System.err.println(logLineColorer("E-TObjArray::At: index 179920 out of bounds (size: 100000, this: 0x29cfa5d0)"));
		System.err.println(logLineColorer("E-AliTriggerRunScalers::CorrectScalersOverflow: Internal error: #scalers ESD != #scalers "));
		System.err.println(logLineColorer("W-AliTriggerRunScalers::ConsistencyCheck: Trigger scaler Level[i+1] > Level[i]. Diff < 16!"));
		
		System.err.println(logLineColorer("* blablabla bubu.gigi.log bubu.gigi.com asdf"));
		System.err.println(logLineColorer("! Aug 9 14:26:19 info ************Redirecting the log of the checkWakesUp to /home/alicesgm/alien-logs/FTD.wakesup.log"));
		System.err.println(logLineColorer("_ Root.Html.Search: Aug 9 14:26: http://www.google.com/search?q=%s+site%3A%u+-site%3A%u%2Fsrc%2F+-site%3A%u%2Fexamples%2F [Global]"));
		System.err.println(logLineColorer("http://savannah.cern.ch/bugs/?group=alien&func=additem http://savannah.cern.ch///bugs//?group=alien&func=additem"));
		System.err.println(logLineColorer("Error accessing path/file for root://t2-sepool-02.lnl.infn.it:1094////pnfs/lnl.infn.it/data/alice//03/21528/a497511a-0cea-11dd-98c4-00304851675c asdf"));
		System.err.println(logLineColorer("Jul 15 17:09:42  info	We got  castor://castorsrm.cr.cnaf.infn.it//castor/cnaf.infn.it/grid/lcg/alice/T1D0/14/41312/B3C55D9A-0E15-11DD-8618-00304851675C root://xrootdmgr-alice.cr.cnaf.infn.it:1094///castor/cnaf.infn.it/grid/lcg/alice/T1D0/14/41312/B3C55D9A-0E15-11DD-8618-00304851675C B3C55D9A-0E15-11DD-8618-00304851675C"));
		System.err.println(logLineColorer("Jul 15 17:09:42  info	ID 2819798 Starting a transfer to castor://castorsrm.cr.cnaf.infn.it//castor/cnaf.infn.it/grid/lcg/alice/T1D0/14/41312/B3C55D9A-0E15-11DD-8618-00304851675C"));
	}
}
