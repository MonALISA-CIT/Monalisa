import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import lia.util.net.NetMatcher;

/**
 * 
 * Class used to read and parse the NetFlow configuration files.
 * The configuration file format:
 * 
 * ---------------------------
 * 	Cern
 *		type: M
 *		ipClass: 137.138.*.*
 *		ipClass: 192.91.*.*
 *	
 *	Caltech 
 *		type: N
 *		ipClass: 131.215.*.*
 *	
 *	FNAL 
 *		type: N
 *		ipClass: 131.225.*.*
 *
 *  Total_IN: Cern
 *  Total_OUT: Starlight
 *
 *  --------------------------
 *  
 *  The calculated traffic (in and out) will be between Cern (Master entry) and all nodes (Caltec, FNAL). 
 *  There also will be a total traffic (sum) between the Master node (Cern) and all the other nodes.
 *  The total traffic in and out will be parameters for nodes Cern_Starlight, Starlight_Cern respectivly,
 *  as mentioned in the Total_IN and Total_OUT configuration entry.
 *  
 */
public class NetFlowConfig implements Observer {

	/**
	 * ConfigHost for every IP class specified (as IpMatch)
	 */
	private Hashtable hosts;

	private Hashtable vHostsTmp;

	/**
	 * NetFlow configuration file
	 */
	private File confFile;

	private String cannonicalFilePath;

	/**
	 * watchdog for file changes
	 */
	private DateFileWatchdog dfw;

	/**
	 * Total_IN from the conf file
	 */
	private String total_IN;

	/**
	 * Total_OUT from the conf file
	 */
	private String total_OUT;
	
	private NetMatcher nm = new NetMatcher ();
    private Vector networks = new Vector();
	
	
	public NetFlowConfig(String fileName) throws Exception {
		this(new File(fileName));
	}

	
	public NetFlowConfig(File confFile) throws Exception {
		this.confFile = confFile;

		if (confFile == null) {
			throw new Exception("Cannot monitor a null File...");
		}
		cannonicalFilePath = confFile.getCanonicalPath();
		if (!confFile.exists()) {
			throw new Exception("The file [ " + cannonicalFilePath
					+ " ] does not exist!");
		}
		if (!confFile.canRead()) {
			throw new Exception("The file [ " + cannonicalFilePath
					+ " ] has now Read acces!");
		}

		hosts = new Hashtable();
		vHostsTmp = new Hashtable();
		parseConfFile();
		try {
			// watch for changes every 30s!
			dfw = new DateFileWatchdog(confFile, 5 * 1000);
		} catch (Throwable t) {
			System.out.println("Cannot monitor the file [ "
					+ cannonicalFilePath + " ] for changes");
			t.printStackTrace();
		}
		if (dfw != null) {
			dfw.addObserver(this);
		}
	}

	/**
	 * parse the NetFlow module configuration file.
	 * @throws Exception
	 */
	public void parseConfFile() throws Exception {

		FileReader fr = new FileReader(confFile);
		BufferedReader br = new BufferedReader(fr);
		String line = br.readLine();
		vHostsTmp.clear();

		ConfigHost ch = null;

		while (line != null) {

			String trimLine = line.trim();
			System.out.println("line: " + trimLine);
			// no blank lines or comments
			if (trimLine.length() > 0 && !trimLine.startsWith("#")) {

				if (trimLine.length() > 0 && !trimLine.startsWith("#")) {

					if (trimLine.startsWith("type:")) {
						System.out.println("type: " + trimLine);
						int index = trimLine.indexOf(":");
						String type = null;
						if (ch != null) {
							try {
								type = trimLine.substring(index + 1).trim();
								if (type.length() == 0)
									type = null;
							} catch (Throwable t) {
								type = null;
							} // try - catch
							if (type != null) {
								if (type.equals("M")) {
									ch.setIsMaster(true);
								} else {
									ch.setIsMaster(false);
								} // if - else
							} // if
						} // if

					} else if (trimLine.startsWith("ipClass:")) {
						System.out.println("ipClass: " + trimLine);
						if (ch != null) {
							int index = trimLine.indexOf(":");
							String ipAddress = null;
							try {
								ipAddress = trimLine.substring(index + 1)
										.trim();
								
								if (ipAddress.length() == 0) {
									ipAddress = null;
								}
							} catch (Throwable t) {
								ipAddress = null;
							} // try - catch

							if (ipAddress != null) {
								int indx = ipAddress.indexOf("/");
								String addr = ipAddress.substring( 0, indx);
								if (addr!=null && addr.length()>0) {
									networks.add (ipAddress);
									IPMatch ipM = new IPMatch(addr);
									vHostsTmp.put(ipM, ch);
								}
							} // if

						} // if ch

					} else if (trimLine.startsWith("Total_IN:")) {
						try {
							int index = trimLine.indexOf(":");
							total_IN = trimLine.substring(index + 1,
									trimLine.length()).trim();
							if (total_IN.length() == 0)
								total_IN = null;
						} catch (Exception exp) {
							total_IN = null;
						} // try - catch
					} else if (trimLine.startsWith("Total_OUT")) {
						try {
							int index = trimLine.indexOf(":");
							total_OUT = trimLine.substring(index + 1,
									trimLine.length()).trim();
							if (total_OUT.length() == 0)
								total_OUT = null;
						} catch (Exception exp) {
							total_IN = null;
						} // try - catch
					} else { // it's all about a name. make a new ConfigHost
								// and get rid of the last one.
						System.out.println("name: " + trimLine);
						ch = new ConfigHost(trimLine, false);
					} // if - else if - else

				} // if

			} // if

			line = br.readLine();
		}

		synchronized (hosts) {
			hosts.clear();
			hosts.putAll(vHostsTmp);
		} // synchronized


		System.out.println("Parsed configuration: " + hosts);
		nm.initInetNetworks (networks);
		fr.close();
		br.close();
	}

	public String getTotal_IN() {
		return total_IN;
	} // getTotal_IN

	public String getTotal_OUT() {
		return total_OUT;
	} // getTotal_OUT

	public Hashtable getHosts() {
		return hosts;
	}

	/**
	 * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
	 */
	public void update(Observable o, Object arg) {
		System.out.println(new Date() + " ConfFile [" + cannonicalFilePath
				+ "] has changed!");
		try {
			parseConfFile();
			System.out.println(new Date() + " Conf from [" + cannonicalFilePath
					+ "] reloaded!");
		} catch (Throwable t) {
			System.out.println(new Date()
					+ " Got exception while reading conf from ["
					+ cannonicalFilePath + "]");
			t.printStackTrace();
		} // try- catch
	}

	public boolean matchIP (String ip) {
		return nm.matchInetNetwork(ip) ;
	} // matchIP
	
	/**
	 * just for test !
	 */
	public static void main(String args[]) {
		try {
			NetFlowConfig nfc = new NetFlowConfig("NetFlow.config");

			Object obj = new Object();

			try {
				Thread.sleep(5000);
			} catch (Throwable t) {
			}

			IPMatch ip = new IPMatch("137.138.42.56");

			System.out.println("Get IP " + nfc.getHosts().get(ip));

			synchronized (obj) {
				try {
					obj.wait();
				} catch (Throwable t) {
				}
			} // synchronized

		} catch (Throwable t) {
			t.printStackTrace();
		}

	} // main

}
