package lia.Monitor.modules;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.ShutdownManager;
import lia.util.fdt.FDTListener;
import lia.util.fdt.FDTManagedController;
import lia.util.fdt.xdr.XDRMessage;
import lia.util.net.LocalHost;
import lia.util.ntp.NTPDate;

/**
 * Module used to control/report information about a FDT Server
 * 
 * @author adim
 */
public class monFDTServer extends FDTManagedController {

	private static final long serialVersionUID = 6983573643467873331L;

	private String[] sResTypes = new String[0]; // dynamic

	static public String ModuleName = "FDTServer";
	static public String resultsModName = "monXDRUDP";	// report the results as coming from this module

	// number of clients connected to FDT server monitored by this module
	private int iConnectedClients;

	public MonModuleInfo init(MNode node, String args) {
		// update the module info
		MonModuleInfo info = super.init(node, args);
		info.ResTypes = sResTypes;
		info.setName(ModuleName);
		try {
			// KILL prv Server
			super.getStdoutFirstLines("pkill -9 -f lisafdtserver ", 0);
		} catch (Throwable t) {
			if (logger.isLoggable(Level.WARNING)) {
				logger.log(Level.WARNING, "Cannot cleanup the previous FDT Server", t);
			}
		}
		// start (if is not started already) an FDT monitoring server and register ourserlves as listner for FDTServer commands
		FDTListener.getInstance().instanceStarted(ModuleName, this);
		
		ShutdownManager.getInstance().addModule(this);
		
		return info;
	}

	public Object doProcess() throws Exception {

		if (info.getState() != 0) {
			throw new IOException("[FDTServer: " + this.Node.getName() + "]  Module could not be initialized");
		}

		Properties newConfig = new Properties();
		newConfig.clear();
		newConfig.put("server.start", "0");
		
		// check the URL and perform start/restart actions of fdt clients
		URLConnection conn;
		String status = (prBackgroundProcess.isRunning()) ? "0" : "1";
		String sURL = null;
		try {
			final String myName = getName();
			final String sFDTServersURL = getModuleConfiguration().getProperty("controlURL", AppConfig.getProperty("fdt.controlURL",DEFAULT_FDT_CONF_URL)) + FDT_SERVERS_CONF;
			sURL = sFDTServersURL + "&user.name=" + encode(System.getProperty("user.name")) + "&machine.name=" + encode(myName == null ? getMyHostname() : myName)
					+ "&machine.hostname=" + encode(getMyHostname()) + "&server.clients=" + encode(String.valueOf(getConnectedClients())) + "&machine.interfaces="
					+ encode(LocalHost.getPublicInterfacesIPs4()) + "&server.status=" + encode(status);
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "[FDTServer] Announcement " + sURL);
			URL url = new URL(sURL);
			conn = getURLConnection(url);
			conn.connect();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "[FDTServer] Cannot open Server URL: " + sURL, e);
			conn = null;
		} catch (Throwable t) {
			logger.log(Level.SEVERE, "[FDTServer] Internal error:", t);
			conn = null;
		}
		
		if(conn != null){
			try {
				final InputStream is;
				is = conn.getInputStream();
				newConfig.load(is);
				if (logger.isLoggable(Level.FINEST))
					logger.log(Level.FINEST, "[FDTServer] READING CONFIG:" + newConfig);
				is.close();
			} catch (IOException e) {
				logger.log(Level.WARNING, "[FDTServer] Error while reading config from URL "+conn, e);
			}
		}
		
		synchronized (pRemoteConfig) {
			pRemoteConfig.clear();
			pRemoteConfig.putAll(newConfig);
		}
		applyConfiguration(pRemoteConfig);
	
		// return the cache monitoring information received from FDT
		return getBufferedResults();
	}

	/**
	 * @see lia.Monitor.monitor.MonitoringModule#ResTypes()
	 */
	public String[] ResTypes() {
		return sResTypes;
	}

	// module is ignored since here we know for sure that we are in Server.
	public XDRMessage execCommand(String module, String command, List args) {
		final long rightNow = NTPDate.currentTimeMillis();
		if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, "[FDTServer] New XDR command received. Cmd: "+command + " Args:" + args);
		}
		
		try {
			XDRMessage xdrOutput = new XDRMessage();
			final String myName = getName();
			if (command.equalsIgnoreCase("monitorTransfer")) {
				if (args.size() < 3) {
					logger.log(Level.WARNING, "[FDTServer] Invalid monitorTransfer parameters");
					xdrOutput.payload = "Invalid monitorTransfer parameters";
					xdrOutput.status = XDRMessage.ERROR;
				} else {
					try {
						Vector paramNames = new Vector();
						Vector paramValues = new Vector();
						String paramName;
						Double paramValue;
						for (int j = 1; j < args.size() - 1; j += 2) {
							try {
								paramName = ((String) args.get(j)).trim();
								paramValue = Double.valueOf((String) args.get(j + 1));
								// update the connected clients to FDT server
								if ("CLIENTS_NO".equalsIgnoreCase(paramName))
									setConnectedClients(paramValue.intValue());
								paramNames.addElement(paramName);
								paramValues.addElement(paramValue);
							} catch (Exception e) {
								logger.log(Level.WARNING, "[FDTServer-MON] [skipped] Cannot understand value for  " + args.get(j) + ": " + args.get(j + 1)+" "+e.getMessage());
							}
						}

						String[] aParamNames = (String[]) paramNames.toArray(new String[paramNames.size()]);
						double[] aParamValues = new double[paramValues.size()];
						int i = 0;
						for (Iterator iterator = paramValues.iterator(); iterator.hasNext(); i++) {
							aParamValues[i] = ((Double) iterator.next()).doubleValue();
						}

						final String key = ((String) args.get(0)).trim();
						String clusterName = null;
						String nodeName = null;
						if ("FDT_PARAMS".equalsIgnoreCase(key)) {
							// apMon.sendParameters("Servers", myName, paramNames.size(), paramNames, paramValues);
							clusterName = "FDT_Servers";
							nodeName = myName;
						} else if (key.startsWith("FDT_MON")) {
							final String sKey = key.substring("FDT_MON".length());
							// apMon.sendParameters("FDT_MON", myName + sKey, paramNames.size(), paramNames, paramValues);
							clusterName = "FDT_MON";
							nodeName= myName+sKey;
						} else {
							// apMon.sendParameters("Clients", key, paramNames.size(), paramNames, paramValues);
							clusterName = "FDT_Clients";
							nodeName = key;
						}

						if (clusterName != null && nodeName != null) {
							Result rResult = new Result(Node.getFarmName(), clusterName, nodeName, resultsModName, aParamNames, aParamValues);
							rResult.time = rightNow;
							addToBufferedResults(rResult);
							xdrOutput.payload = "OK";
							xdrOutput.status = XDRMessage.SUCCESS;
							if(logger.isLoggable(Level.FINE))
								logger.log(Level.FINE, "Monitoring values published: Servers {0} {1} {2}", new Object[] { myName + "_" + args.get(0), paramNames, paramValues });
						}
					} catch (NumberFormatException e) {
						logger.warning("Wrong parameter:" + args.get(2) + ".  integer/float/double values required");
						xdrOutput.payload = "Wrong parameter.  integer/float/double values required";
						xdrOutput.status = XDRMessage.ERROR;
					}
				}
			}
			return xdrOutput;
		} catch (Throwable t) {
			logger.log(Level.WARNING, "Cannot execute command " + command + "\n Cause: ", t);
			return XDRMessage.getErrorMessage("Cannot execute command " + command + "\n Cause: " + t.getMessage());
		}
	}

	protected void startFDT(Properties config) throws IOException {
		String fdtServerCmd = AppConfig.getProperty("java.home") + "/bin/java -jar %JAR% -bs 8M -bio 0<&- &>" + AppConfig.getProperty("lia.Monitor.Farm.HOME") + "/fdtserver.log";
		if (getModuleConfiguration() != null) {
			fdtServerCmd = getModuleConfiguration().getProperty("server_command", AppConfig.getProperty("fdt.server_command", fdtServerCmd));
		}
		fdtServerCmd = fdtServerCmd.replaceAll("%JAR%", getFDTJar() + " -lisafdtserver");
		if (logger.isLoggable(Level.FINEST))
			logger.log(Level.FINEST, "[FDTServer] Using cmd for server:" + fdtServerCmd);
		prBackgroundProcess.startProcess(fdtServerCmd);
	}

	protected void stopFDT() throws IOException {
		if (logger.isLoggable(Level.INFO))
			logger.log(Level.INFO, "[FDTServer] Stopping FDT Server" + prBackgroundProcess.getSCmd());
		prBackgroundProcess.stopProcess();
	}

	/**
	 * Apply the remote configuration read from the control servlet
	 * 
	 * @param config
	 */
	private void applyConfiguration(Properties config) {
		boolean bNewState = false;
		try{
			synchronized (config) {
				if (Integer.parseInt(config.getProperty("server.start")) > 0)
					bNewState = true;
			}
		}catch(Exception ex){
			logger.log(Level.WARNING, "Error while taking config parameters", ex);
		}
		bActive = prBackgroundProcess.isRunning();

		if (!bActive && bNewState) {
			try {
				stopFDT();
				startFDT(config);
				bActive = true;
				reportStatus("server.status", "0");
			} catch (IOException e) {
				logger.log(Level.WARNING, "[FDTServer] Error while restartFDT and reportStatus.", e);
			}
			return;
		}

		if (bActive && !bNewState) {
			try {
				stopFDT();
				bActive = false;
				reportStatus("server.status", "1");
			} catch (IOException e) {
				logger.log(Level.WARNING, "[FDTServer] Error while stopFDT and reportStatus.", e);
			}
			return;
		}
		if(logger.isLoggable(Level.FINE))
			logger.fine("[FDTServer] No change in server status [running:"+bActive+"]");
	}

	private void setConnectedClients(int iNewValue) {
		this.iConnectedClients = iNewValue;
	}

	private int getConnectedClients() {
		return iConnectedClients;
	}

}
