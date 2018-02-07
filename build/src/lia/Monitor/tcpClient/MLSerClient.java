package lia.Monitor.tcpClient;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Inflater;

import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppControlClient;
import lia.Monitor.monitor.AppControlMessage;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.cmonMessage;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;
import lia.util.UUID;
import lia.util.Utils;
import lia.util.threads.MLExecutorsFactory;
import monalisa.security.gridforum.gss.ExtendedGSSContext;
import monalisa.security.util.GSSInitData;
import monalisa.security.util.InitatorGSSContext;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;

import org.ietf.jgss.GSSException;

/**
 * 
 */
public abstract class MLSerClient implements LocalDataFarmProvider {

	/** The Logger */
	protected static final Logger logger = Logger.getLogger(MLSerClient.class.getName());

	/** NOT only debugging */
	protected final String myName;

	/** */
	public volatile MFarm farm = null;

	/**
	 * Message multiplexer
	 */
	public ConnMessageMux msgMux;

	/** object used for sync on moddifications opperation done for localClients Hashtable: put and iterator with remove */
	private final Object syncLocalClients_Modif = new Object();

	/**
	 * Clients subscribed to data produced by this service
	 */
	protected Hashtable<Integer, LocalDataFarmClient> localClients;

	/**
	 * Clients registered with filters to this service
	 */
	protected Hashtable<String, LocalDataFarmClient> filterClients;

	/**
	 * Predicate ID generator
	 */
	static int PRED_IDS = 0;

	/**
	 * synchronized access to ID generator
	 */
	static final Object predIdsSync = new Object();

	/**
	 * Service local time
	 */
	public volatile String localTime = "???";

	/**
	 * Service uptime
	 */
	public volatile String uptime = "N/A";

	/**
	 * MonALISA Service version
	 */
	public volatile String mlVersion = "???";

	/**
	 * MonALISA Service build number
	 */
	public volatile String buildNr = "";

	/**
	 * Whether or not this service is active
	 */
	public volatile boolean active = true;

	/**
	 * For full/incremental configurations
	 */
	protected boolean conf_added;

	/**
	 * Service name
	 */
	protected final String FarmName;

	/**
	 * Hostname of the machine on which the service runs
	 */
	public final String hostName;

	/**
	 * IP address of the machine on which the service runs
	 */
	public final InetAddress address;

	/**
	 * SSID
	 */
	public final ServiceID tClientID;

	/**
	 * 
	 */
	volatile ServiceItem si = null;

	/** the security context mantained by initiator (client in this case) */
	private volatile InitatorGSSContext initSecCtx = null;

	/** previous user keystore */
	private static String prevKeystoreParams = null;

	/**
	 * previous GSSInitData. This is reused unless the keystore params change (checked using prevKeystoreParams)
	 */
	private static GSSInitData prevGSSinitData = null;

	private static Object syncGSSinitObj = new Object();

	/**
	 * APP Control status - not yet initialized
	 */
	public static final int APP_CONTROL_NONE = 0;

	/**
	 * APP Control status - starting
	 */
	public static final int APP_CONTROL_STARTING = 1;

	/**
	 * APP Control status - started
	 */
	public static final int APP_CONTROL_STARTED = 2;

	/**
	 * APP Control status - timeout starting
	 */
	public static final int APP_CONTROL_TIMEOUT = -1;

	/**
	 * APP Control status - could not initialize
	 */
	public static final int APP_CONTROL_FAILED = -2;

	/** the startAppControl was called */
	private volatile int appCtrlStatus = APP_CONTROL_NONE;

	private final Object syncAppCtrl = new Object();

	private final Object syncAppCtrlInit = new Object();

	/** the session UID which identifies the communication channel between this tClient and the associated ML Service */
	private volatile UUID uidAppCtrlSessionID;

	/** timeout for starting AppControl */
	public final long lAppCtrlStartTimeout = AppConfig.getl("lia.Monitor.tcpClient.MLSerClient.AppControlStartTimeout", 60) * 1000;

	/** how many times it should retry establishing the AppControl connection, in case of timeout */
	final int APP_CTRL_TIMEOUT_RETRIES = AppConfig.geti("lia.Monitor.tcpClient.MLSerClient.AppControlTimeoutRetries", 3);

	/** Set to true when AppControl initialization is either successful or failed */
	private volatile boolean bAppCtrlInitFinished = false;

	/** list of the AppControlClients that want to send AppControl commands */
	protected final Vector<AppControlClient> vAppControlClients;

	/** mapping between the commandID and the AppControlClient for which this command was sent */
	protected final Hashtable<Long, AppControlClient> htAppControlCommands;

	/**
	 * List of commands that were not sent because AUTH didn't complete yet. They will be sent automatically when the
	 * security ctx is established.
	 */
	protected final Vector<AppControlMessage> vUnsentAppMsgs;

	/** generate unique AppControl command IDs */
	private static AtomicLong appCtrlCmdID = new AtomicLong();

	/**
	 * Total number of parameters
	 */
	public volatile int total_params;

	/**
	 * Execute several actions in parallel
	 */
	protected static final ExecutorService executor;

	static {
		try {
			executor = MLExecutorsFactory.getCachedThreadPool("lia.Monitor.tcpClient.MLSerClient", 0, 256, 1);
		} catch (Throwable e) {
			throw new RuntimeException("Unable to create the executor service lia.Monitor.tcpClient.MLSerClient ", e);
		}
	}

	/**
	 * @param name
	 * @param address
	 * @param hostName
	 * @param msgMux
	 * @param tClientID
	 * @throws Exception
	 */
	public MLSerClient(String name, InetAddress address, String hostName, ConnMessageMux msgMux, ServiceID tClientID) throws Exception {

		conf_added = false;
		localClients = new Hashtable<Integer, LocalDataFarmClient>();
		filterClients = new Hashtable<String, LocalDataFarmClient>();
		vAppControlClients = new Vector<AppControlClient>();
		htAppControlCommands = new Hashtable<Long, AppControlClient>();
		vUnsentAppMsgs = new Vector<AppControlMessage>();

		myName = "MLSerClient for " + name;
		this.FarmName = name;
		this.tClientID = tClientID;
		this.msgMux = msgMux;
		this.hostName = hostName;
		this.address = address;
	}

	/**
	 * @return Farm name
	 */
	public String getFarmName() {
		return FarmName;
	}

	/**
	 * Callback function
	 * 
	 * @param o
	 */
	public void notifyMessage(Object o) {
		process(o);
	}

	/**
	 * @param si
	 */
	public void setServiceEntry(ServiceItem si) {
		this.si = si;
	}

	/**
	 * @return the entry
	 */
	public ServiceItem getServiceEntry() {
		return this.si;
	}

	/**
	 * @return the GenericMLEntry structure
	 */
	public GenericMLEntry getGMLEntry() {

		try {
			final GenericMLEntry gmle = JiniClient.getEntry(si, GenericMLEntry.class);
			return gmle;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * wrapper for addFarmClient from proxy registers a new farm, by id, to the proxy
	 * 
	 * @param farmID
	 */
	public void addFarmClient(ServiceID farmID) {
		if ((msgMux != null) && msgMux.isActive()) {
			msgMux.addFarmClient(farmID, this);
		}
		else {
			throw new RuntimeException("No active connection to proxy for client " + FarmName);
		}
	}

	@Override
	public void addLocalClient(LocalDataFarmClient client, monPredicate pred) {
		if ((msgMux != null) && msgMux.isActive()) {
			synchronized (predIdsSync) {
				PRED_IDS++;
				pred.id = (PRED_IDS) % Integer.MAX_VALUE;
			}
			Integer ikey = Integer.valueOf(pred.id);
			synchronized (syncLocalClients_Modif) {
				localClients.put(ikey, client);
			}
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Registering for predicate " + pred);
			}
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Registering for predicate " + pred);
			}
			MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.PREDICATE_REGISTER_TAG, ikey, pred, tClientID);
			msgMux.sendMsg(mess);
		}
		else {
			throw new RuntimeException("No active connection to proxy for client " + FarmName + " when sending predicat " + pred);
		}
	}

	@Override
	public void addLocalClient(LocalDataFarmClient client, String FilterName) {
		if ((msgMux != null) && msgMux.isActive()) {
			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, "Registering for filter " + FilterName);
			}
			MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.FILTER_REGISTER_TAG, FilterName, null, tClientID);
			msgMux.sendMsg(mess);
			filterClients.put(FilterName, client);
		}
		else {
			throw new RuntimeException("No active connection to proxy for client " + FarmName + " when sending filter " + FilterName);
		}
	}

	/**
	 * @param predId
	 * @return true if this ID is registered
	 */
	public boolean containsPredicate(final Integer predId) {
		try {
			if (localClients.containsKey(predId)) {
				return true;
			}
		} catch (Exception ex) {
			// ignore exception
		}
		return false;
	}

	/**
	 * Callback function
	 * 
	 * @param time
	 */
	public void setLocalTime(final String time) {
		localTime = time;
		postSetLocalTime(time);
	}

	/**
	 * extend this to do something when receiving new local time
	 * 
	 * @param time
	 */
	public void postSetLocalTime(String time) {
		// empty default implementation
	}

	/**
	 * Callback function
	 * 
	 * @param uptime
	 */
	public void setUptime(String uptime) {
		this.uptime = uptime;
		postSetUptime(uptime);
	}

	/**
	 * extend this to do something when receiving new uptime
	 * 
	 * @param newUptime
	 */
	public void postSetUptime(String newUptime) {
		// empty default implementation
	}

	/**
	 * Callback function
	 * 
	 * @param version
	 */
	public void setMLVersion(String version) {
		int buildNrIdx = version.indexOf("-");
		if (buildNrIdx >= 0) {
			mlVersion = version.substring(0, buildNrIdx);
			buildNr = version.substring(buildNrIdx + 1);
		}
		else {
			mlVersion = version;
			buildNr = "";
		}
		postSetMLVersion(version);
	}

	/**
	 * extend this to do something when receiving new version
	 * 
	 * @param version
	 */
	public void postSetMLVersion(String version) {
		// empty default implementation
	}

	/**
	 * unregisters the specified predicate from the provided local client based on predicate's id
	 * 
	 * @author mluc
	 * @since Jul 28, 2006
	 * @param client
	 * @param pred
	 */
	public void deleteLocalClient(LocalDataFarmClient client, monPredicate pred) {
		if (client == null) {
			return;
		}
		boolean bPredFound = false;
		// unregister predicate
		synchronized (syncLocalClients_Modif) {
			for (Iterator<Integer> it = localClients.keySet().iterator(); it.hasNext();) {// Enumeration e = localClients.keys();
				// e.hasMoreElements();) {
				Integer ikey = it.next();// e.nextElement();
				LocalDataFarmClient lc = localClients.get(ikey);
				if (lc.equals(client) && (ikey.intValue() == pred.id)) {
					it.remove();
					bPredFound = true;
					break;
				} // if
			} // for
		}
		if (bPredFound && (msgMux != null) && msgMux.isActive()) {
			MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG, Integer.valueOf(pred.id), null, tClientID);
			msgMux.sendMsg(mess);
		}
	}

	@Override
	public void deleteLocalClient(LocalDataFarmClient client) {

		// unregister predicates
		ArrayList<Integer> alUnregister = new ArrayList<Integer>(); // list of predicates to unregister sorted to avoid duplicates
		synchronized (syncLocalClients_Modif) {
			for (Iterator<Integer> it = localClients.keySet().iterator(); it.hasNext();) {// Enumeration e = localClients.keys();
				// e.hasMoreElements();) {
				Integer ikey = it.next();// e.nextElement();
				LocalDataFarmClient lc = localClients.get(ikey);

				if (client == null) { // remove all clients
					// logger.log(Level.INFO, myName + ": unregister " + ikey);
					alUnregister.add(ikey);
					// if(tmclient != null && tmclient.isActive()) {
					// MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG,
					// ikey, null);
					// mess.farmID = tClientID;
					// tmclient.sendMsg (mess);
					// };
					it.remove(); // localClients.remove(ikey);
				}
				else { // remove only specified clients ....
					if (lc.equals(client)) {
						// logger.log(Level.INFO, myName + ": unregister " + ikey);
						alUnregister.add(ikey);
						// if(tmclient != null && tmclient.isActive()) {
						// MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG,
						// ikey, null);
						// mess.farmID = tClientID;
						// tmclient.sendMsg (mess);
						// };
						it.remove(); // localClients.remove(ikey);
					} // if
				} // else
			} // for
		}
		// int nCountDupl = 0;
		// for ( int i=0; i<alUnregister.size(); i++ ) {//remove duplicates
		// Integer ikey = (Integer)alUnregister.get(i);
		// for ( int j=i+1; j<alUnregister.size(); j++) {
		// if ( ikey.equals(alUnregister.get(j)) ) {
		// alUnregister.remove(j);
		// j--;
		// nCountDupl++;
		// }
		// }
		// }
		// System.out.println("<mluc> <tClient delLocalClient func> counted "+nCountDupl+" duplicates");
		for (int i = 0; i < alUnregister.size(); i++) {
			if ((msgMux != null) && msgMux.isActive()) {
				MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.PREDICATE_UNREGISTER_TAG, alUnregister.get(i), null, tClientID);
				msgMux.sendMsg(mess);
			}
		}

		// uregister filters
		for (Iterator<String> it = filterClients.keySet().iterator(); it.hasNext();) {// Enumeration
			// eFilters=filterClients.keys();
			// eFilters.hasMoreElements(); ){
			String filter = it.next();// eFilters.nextElement();
			LocalDataFarmClient lc = filterClients.get(filter);

			if (client == null) {
				logger.log(Level.FINE, myName + ": funregister " + filter);
				if ((msgMux != null) && msgMux.isActive()) {
					MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.FILTER_UNREGISTER_TAG, filter, null, tClientID);
					msgMux.sendMsg(mess);
				}
				it.remove(); // filterClients.remove(filter);
			}
			else {
				if (lc.equals(client)) {
					logger.log(Level.FINE, myName + ": funregister " + filter);
					if ((msgMux != null) && msgMux.isActive()) {
						MonMessageClientsProxy mess = new MonMessageClientsProxy(monMessage.FILTER_UNREGISTER_TAG, filter, null, tClientID);
						msgMux.sendMsg(mess);
					}
					it.remove();// filterClients.remove(filter);
				} // if
			} // else
		}

		// unregister app ctrl clients
		if (client == null) {
			while (vAppControlClients.size() > 0) {
				AppControlClient appCli = vAppControlClients.get(0);
				deleteAppControlClient(appCli);
			}
		}
	}

	/**
	 * @param nfarm
	 */
	protected abstract void newConfig(MFarm nfarm);

	private String calculateElapsedTime(String elapsedMillis) {
		String retv = null;
		long millis = Long.valueOf(elapsedMillis).longValue();

		long seconds = millis / 1000;
		long days = seconds / (3600 * 24);
		seconds -= days * 3600 * 24;
		long hours = seconds / 3600;
		seconds -= hours * 3600;
		long mins = seconds / 60;
		seconds -= mins * 60;

		if (days != 0) {
			retv = ((days < 10) ? "0" : "") + days + " day(s)  ";
		}
		else {
			retv = "";
		}

		retv += ((hours < 10) ? "0" : "") + hours + ":" + ((mins < 10) ? "0" : "") + mins + ":" + ((seconds < 10) ? "0" : "") + seconds;
		return retv;
	}

	/**
	 * If the given message.result is a cmonMessage, this method replaces it with the uncompressed monMessage.
	 * Otherwise, nothing happens.
	 */
	private final MonMessageClientsProxy decompressResultIfNeeded(MonMessageClientsProxy msg) {
		if (msg.result instanceof cmonMessage) {
			final Inflater decompresser = new Inflater();
			cmonMessage cm = (cmonMessage) msg.result;
			// System.out.println("Uncompressing "+cm.cbuff.length+" => "+cm.dSize+" bytes");

			int initSize = cm.cbuff.length;
			// Decompress the bytes
			decompresser.reset();
			decompresser.setInput(cm.cbuff);
			byte[] result = new byte[cm.dSize];
			int resultLength = 0;
			try {
				resultLength = decompresser.inflate(result);
			} catch (Throwable t) {
				logger.log(Level.WARNING, myName + ": Got Exception while decompressing", t);
			}

			ByteArrayInputStream bais = null;
			ObjectInputStream ois = null;
			try {
				bais = new ByteArrayInputStream(result);
				ois = new ObjectInputStream(bais);
				return new MonMessageClientsProxy(msg.tag, msg.ident, ois.readObject(), msg.farmID);
			} catch (Throwable t) {
				logger.log(Level.WARNING, myName + ": Got Exception while restoring decompressed result", t);
			} finally {
				if (ois != null) {
					try {
						ois.close();
					} catch (Throwable ignore) {
						// ignore
					}
				}
				if (bais != null) {
					try {
						bais.close();
					} catch (Throwable ignore) {
						// ignore
					}
				}
			}

			if (logger.isLoggable(Level.FINER)) {
				logger.log(Level.FINER, myName + ": Recv c: " + initSize + " uc: " + resultLength);
			}
		}

		return msg;
	}

	/**
	 * @param obj
	 */
	public void process(Object obj) {
		// if not a MonMessageClientsProxy, a strange message came; ignore it
		if (!(obj instanceof MonMessageClientsProxy)) {
			logger.log(Level.WARNING, myName + ":  Received an unknown object! Ignore it!\nObj = " + obj);
			return;
		}

		// a MonMessageClientsProxy came
		MonMessageClientsProxy msg = (MonMessageClientsProxy) obj;

		if (msg.tag.startsWith(monMessage.ML_TIME_TAG)) {
			localTime = (String) msg.result;
			String newUptime = "N/A";
			int ui = localTime.indexOf("Uptime:");
			if (ui != -1) {
				newUptime = localTime.substring((ui + "&Uptime:".length()) - 1);
				try {
					newUptime = calculateElapsedTime(newUptime);
					if (newUptime == null) {
						newUptime = "N/A";
					}
				} catch (Throwable t) {
					t.printStackTrace();
					newUptime = "N/A";
				}
				localTime = localTime.substring(0, ui - 1);
			}
			setUptime(newUptime);
			setLocalTime(localTime);

			msg = null;
			return;
		}
		else
			if (msg.tag.startsWith("uptime")) {

				msg = null;
				return;
			}
			else
				if (msg.tag.startsWith(monMessage.ML_VERSION_TAG)) {
					setMLVersion((String) msg.result);
					msg = null;
					return;
				}
				else
					if (msg.tag.startsWith(monMessage.ML_CONFIG_TAG)) {
						MFarm mfarm = (MFarm) msg.result;
						newConfig(mfarm);
						setTotalParams();
						msg = null;
						return;
					}
					else
						if (msg.tag.equals(monMessage.ML_RESULT_TAG)) {
							final MonMessageClientsProxy newMsg = decompressResultIfNeeded(msg);
							processPredicateOrFilterResult(newMsg);
						}
						else
							if (msg.tag.startsWith(monMessage.ML_APP_CTRL_TAG)) {
								if (logger.isLoggable(Level.FINER)) {
									logger.log(Level.FINEST, "Got app_ctrl message from " + FarmName + ": " + msg);
								}
								final MonMessageClientsProxy newMsg = decompressResultIfNeeded(msg);
								processAppControlResult(newMsg);
							}
	}

	/**
	 * Process the given message as a result following a predicate or filter request.
	 * 
	 * @param msg
	 */
	protected void processPredicateOrFilterResult(MonMessageClientsProxy msg) {
		if (msg.ident instanceof Integer) {
			LocalDataFarmClient cl = localClients.get(msg.ident);

			if (logger.isLoggable(Level.FINEST)) {
				logger.log(Level.FINEST, "new result: " + msg.result);
			}

			if (cl != null) {
				StringFactory.convert(msg.result);
				cl.newFarmResult(this, msg.result);
			}
		}
		else
			if (msg.ident instanceof String) {
				LocalDataFarmClient cl = filterClients.get(msg.ident);

				if (logger.isLoggable(Level.FINEST)) {
					logger.log(Level.FINEST, "new filter result: " + msg.result);
				}

				if (cl != null) {
					StringFactory.convert(msg.result);
					cl.newFarmResult(this, msg.result);
				}
			}
	}

	/**
	 * @return GSSInitData
	 */
	public GSSInitData loadGSSData() {
		synchronized (syncGSSinitObj) {
			try {
				String userKeystore = AppConfig.getProperty("keystore", null);
				String userKeystorePwd = AppConfig.getProperty("keystore_pwd", null);
				String userKeystoreAlias = AppConfig.getProperty("keystore_alias", null);
				logger.fine("<" + FarmName + "> [AUTH] Loading credentials:" + userKeystore + " " + userKeystoreAlias + " " + userKeystorePwd);
				if ((userKeystore == null) || (userKeystorePwd == null) || (userKeystoreAlias == null)) {
					logger.fine("[AUTH] Cannot find user credentials. Please set the <keystore, keystore_pwd, keystore_alias> properties");
					return null;
				}
				String keystoreParams = "" + userKeystore + "/" + userKeystorePwd + "/" + userKeystoreAlias;
				if ((prevKeystoreParams != null) && prevKeystoreParams.equals(keystoreParams)) {
					if (logger.isLoggable(Level.FINE)) {
						logger.fine("Reusing gssinit data...");
					}
					return prevGSSinitData;
				}
				prevKeystoreParams = keystoreParams;
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Loading gssinit data");
				}
				prevGSSinitData = new GSSInitData(userKeystore, userKeystorePwd, userKeystoreAlias, null);
				return prevGSSinitData;
			} catch (Throwable t) {
				logger.log(Level.SEVERE, "<" + FarmName + "> [AUTH] Error loading user credentials.", t);
				return null;
			}
		}
	}

	/**
	 * Initialize a secure context and communication channel with the ML service represented by this tClient.
	 * 
	 * @return Status of the attempt. One of the APP_CONTROL_... statueses.
	 */
	int startAppControlHandshake() {
		initSecCtx = null;
		bAppCtrlInitFinished = false;
		int secStatus = APP_CONTROL_NONE;
		GSSInitData gssData = loadGSSData();
		if (gssData != null) {
			try {
				uidAppCtrlSessionID = UUID.randomUUID();
				initSecCtx = new InitatorGSSContext(gssData);
				initSecCtx.setUpInitiatorContext(hostName); // ML service hostname
				byte[] outToken = initSecCtx.produceInitSecContextMsg();
				if (outToken != null) {
					if (!active) {
						throw new RuntimeException("<" + FarmName + "> not active anymore! Aborting AppControl start phase.");
					}
					MonMessageClientsProxy mess = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_AUTH_START, uidAppCtrlSessionID, outToken, tClientID);
					msgMux.sendMsg(mess);
				}
				logger.fine("<" + FarmName + "> [AUTH] GSS Handshake started");
			} catch (Throwable t) {
				logger.log(Level.WARNING, "<" + FarmName + "> [AUTH] GSS Context failed...", t);
				try {
					initSecCtx.dispose();
				} catch (Exception ex) {
					// ignore
				}
				initSecCtx = null;
			}
			final long lTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(lAppCtrlStartTimeout);
			final long lStartHandshakeNanos = System.nanoTime();
			synchronized (syncAppCtrlInit) {
				// if we managed to initiate the handshake, wait for it to complete
				while ((initSecCtx != null) && (!bAppCtrlInitFinished) && ((System.nanoTime() - lStartHandshakeNanos) < lTimeoutNanos)) {
					try {
						syncAppCtrlInit.wait(2 * 1000);
					} catch (InterruptedException ie) {
						// ignore
					}
				}
			}
			if (initSecCtx == null) {
				// message should have been provided
				secStatus = APP_CONTROL_FAILED;
			}
			else {
				if (initSecCtx.isEstabllished()) {
					secStatus = APP_CONTROL_STARTED;
					logger.fine("<" + FarmName + "> [AUTH] Context ESTABLISHED!");
				}
				else {
					try {
						initSecCtx.dispose();
					} catch (Exception ex) {
						// ignore
					}
					initSecCtx = null;
					if (logger.isLoggable(Level.FINE)) {
						logger.fine("<" + FarmName + "> [AUTH] Timeout while establishing GSS context...");
					}
					secStatus = APP_CONTROL_TIMEOUT;
				}
			}
		}
		else {
			// message should have been provided
			secStatus = APP_CONTROL_FAILED;
		}
		return secStatus;
	}

	/**
	 * Notify the pending app control clients
	 * 
	 * @param secStatus
	 *            the final status (after timeout retries)
	 */
	void notifyAppCtrlPendingClients(int secStatus) {
		// decide the status -> appCtrlStatus
		synchronized (syncAppCtrl) {
			appCtrlStatus = secStatus;
			for (AppControlClient appControlClient : vAppControlClients) {
				appControlClient.appControlStatus(this, (appCtrlStatus == APP_CONTROL_STARTED));
			}
		}
		// send the unsent messsages
		synchronized (vUnsentAppMsgs) {
			for (AppControlMessage appControlMessage : vUnsentAppMsgs) {
				directSendAppControlMsg(appControlMessage);
			}
			vUnsentAppMsgs.clear();
		}

	}

	/**
	 * This should be called first, to initialize AppControl. The given client will be notified by the status of this
	 * request when it is known. This will be called with a null client afterwards to renew the security context since
	 * it will expire after some time...
	 * 
	 * @param client
	 * @see lia.Monitor.monitor.AppControlClient#appControlStatus(MLSerClient, boolean)
	 */
	public void addAppControlClient(AppControlClient client) {
		synchronized (syncAppCtrl) {
			if ((client != null) && !vAppControlClients.contains(client)) {
				vAppControlClients.add(client);
			}
			switch (appCtrlStatus) {
			case APP_CONTROL_NONE:
				// not yet started, so start it now
				appCtrlStatus = APP_CONTROL_STARTING;
				executor.execute(new Runnable() {

					@Override
					public void run() {
						int timeoutRetries = APP_CTRL_TIMEOUT_RETRIES;
						int secStatus = APP_CONTROL_NONE;
						do {
							secStatus = startAppControlHandshake();
							if (secStatus == APP_CONTROL_TIMEOUT) {
								terminateAppControlSession();
								try {
									long wait = (long) (1000 * (1 + (5 * Math.random())));
									logger.warning("<" + FarmName + "> Don't panic! Will retry " + timeoutRetries + " more times, with first in " + (wait / 1000) + " seconds.");
									Thread.sleep(wait);
								} catch (Exception e) {
									// ignore
								}
								if (!active) {
									logger.info("<" + FarmName + "> Giving up, farm instance not active anymore.");
									break;
								}
							}
							else {
								break;
							}
						} while (timeoutRetries-- > 0);
						notifyAppCtrlPendingClients(secStatus);
					}
				});
				break;
			case APP_CONTROL_STARTED:
			case APP_CONTROL_TIMEOUT:
			case APP_CONTROL_FAILED:
				// notify client of an already known final status
				if (client != null) {
					client.appControlStatus(this, (appCtrlStatus == APP_CONTROL_STARTED));
				}
				break;
			default:
				// otherwise it's still trying... the client will be notified
				// at the end of startAppControlHandshake()
				break;
			}
		}
	}

	/**
	 * delete the client from the internal AppControl hashes
	 * 
	 * @param client
	 */
	public void deleteAppControlClient(AppControlClient client) {
		synchronized (syncAppCtrl) {
			vAppControlClients.remove(client);
			client.appControlStatus(this, false);
		}
		synchronized (htAppControlCommands) {
			for (Iterator<AppControlClient> cit = htAppControlCommands.values().iterator(); cit.hasNext();) {
				if (client.equals(cit.next())) {
					cit.remove();
				}
			}
		}
	}

	/**
	 * After initializing AppControl (see initAppControl(..)) use this method to send a command to the ML service
	 * represented by this tClient. When the response is received, the given client will be notified with the cmdResult
	 * callback. If sending several commands to the same tClient, the result of this method can be used to identify the
	 * command.
	 * 
	 * @param client
	 *            - the client which will be notified by the result
	 * @param command
	 *            - the command to be executed by the ML service
	 * @param params
	 *            - optional params. If none needed, just pass 'null' here
	 * @return - the command ID for this message, or null if the message was not sent
	 */
	public Long sendAppControlCmd(AppControlClient client, String command, Object params) {
		if ((msgMux != null) && msgMux.isActive()) {
			Long cmdID = Long.valueOf(appCtrlCmdID.incrementAndGet());
			AppControlMessage appMsg = new AppControlMessage(cmdID, command, params, -1);
			synchronized (syncAppCtrl) {
				if (appCtrlStatus == APP_CONTROL_STARTED) {
					if (initSecCtx.context.getLifetime() < (60 * 60)) {
						logger.info("<" + FarmName + "> Security context lifetime is too low: " + initSecCtx.context.getLifetime());
						restartAppControlAuth();
					}
				}
				if (appCtrlStatus == APP_CONTROL_STARTING) {
					htAppControlCommands.put(cmdID, client);
					vUnsentAppMsgs.add(appMsg);
					// TODO: what if it will not be sent, eventually?
					return cmdID;
				}
				if (appCtrlStatus != APP_CONTROL_STARTED) {
					return null;
				}
			}
			htAppControlCommands.put(cmdID, client);
			return directSendAppControlMsg(appMsg);
		}
		throw new RuntimeException("No active connection to proxy for client " + FarmName + " when sending AppControlCmd " + command);
	}

	/** Using the current security context, send immediately the prepared message */
	private Long directSendAppControlMsg(AppControlMessage appMsg) {
		try {
			return sendAppControlMsg(appMsg);
		} catch (Exception ex) {
			logger.log(Level.WARNING, "<" + FarmName + "> Failed to prepare, encrypt or send the AppControl message. Will try to redo authentication.", ex);

			restartAppControlAuth();
		}

		return null;
	}

	private Long sendAppControlMsg(AppControlMessage appMsg) throws Exception {
		if (!active) {
			logger.info("<" + FarmName + "> Not active anymore. Aborting AppControl send message: " + appMsg);
			return null;
		}
		if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER, "<" + FarmName + "> Sending AppControlMessage: " + appMsg);
		}
		byte[] messageBytes = Utils.writeObject(appMsg);
		messageBytes = encryptMsg(initSecCtx.context, messageBytes);
		MonMessageClientsProxy mess = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_CMD, uidAppCtrlSessionID, messageBytes, tClientID);
		msgMux.sendMsg(mess);
		return appMsg.cmdID;
	}

	/** Terminate the existing AppControl session */
	void terminateAppControlSession() {
		if (uidAppCtrlSessionID != null) {
			if (!active) {
				logger.info("<" + FarmName + "> Not active anymore. Aborting AppControl termination.");
				return;
			}
			logger.fine("<" + FarmName + "> Terminating AppControlSession: " + uidAppCtrlSessionID);
			MonMessageClientsProxy mess = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_END_SESSION, uidAppCtrlSessionID, AppControlMessage.EMPTY_PAYLOAD, tClientID);
			msgMux.sendMsg(mess);
		}
	}

	/**
	 * Rstart Authentication procedure. This is called in case of security context expiration and on proxy errors.
	 */
	private void restartAppControlAuth() {
		terminateAppControlSession();
		synchronized (syncAppCtrl) {
			// we have to fully restart AppControl, so we set the status to NONE
			appCtrlStatus = APP_CONTROL_NONE;
		}
		logger.info("<" + FarmName + "> Redoing AppControl authentication");
		addAppControlClient(null);
	}

	/** Process the given message as a result following an app_ctrl_{admin|cmd} request. */
	private void processAppControlResult(MonMessageClientsProxy msg) {
		if ((msg.ident == null) || (!msg.ident.equals(uidAppCtrlSessionID))) {
			logger.log(Level.WARNING, "<" + FarmName + "> AppControl expecting msg for session " + uidAppCtrlSessionID + " but received " + msg);
			return;
		}
		if (initSecCtx == null) {
			logger.log(Level.WARNING, "<" + FarmName + "> AppControl initSecCtx is null, but received " + msg);
			return;
		}
		if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER, "<" + FarmName + "> Received AppControl message " + msg);
		}

		if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_AUTH)) {
			// negociating the setup of the session
			try {
				final byte[] outToken = initSecCtx.consumeInitSecContextMsg((byte[]) msg.result);
				if (outToken != null) {
					MonMessageClientsProxy reply = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_AUTH, uidAppCtrlSessionID, outToken, tClientID);
					msgMux.sendMsg(reply);
				}
				else {
					final String sErrMsg = "Null AUTH msg";
					MonMessageClientsProxy reply = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_ERR, uidAppCtrlSessionID, sErrMsg, tClientID);
					msgMux.sendMsg(reply);
					try {
						initSecCtx.dispose();
					} catch (Exception ex) {
						// ignore
					}
					logger.log(Level.WARNING, "<" + FarmName + "> [AUTH] GSS Context failed... Null CTX.");
					// wake-up the waiting startAppControlHandshake
					synchronized (syncAppCtrlInit) {
						initSecCtx = null;
						bAppCtrlInitFinished = true;
						syncAppCtrlInit.notify();
					}
				}
			} catch (Throwable t) {
				final String sErrMsg = "Invalid AUTH msg" + t.getMessage();
				MonMessageClientsProxy reply = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_ERR, uidAppCtrlSessionID, sErrMsg, tClientID);
				msgMux.sendMsg(reply);
				try {
					initSecCtx.dispose();
				} catch (Exception ex) {
					// ignore
				}
				logger.log(Level.WARNING, "<" + FarmName + "> [AUTH] GSS Context failed... ", t);
				// wake-up the waiting startAppControlHandshake
				synchronized (syncAppCtrlInit) {
					initSecCtx = null;
					bAppCtrlInitFinished = true;
					syncAppCtrlInit.notify();
				}
			}
		}
		else
			if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_AUTH_FINISHED)) {
				try {
					initSecCtx.consumeInitSecContextMsg((byte[]) msg.result);
				} catch (GSSException gssE) {
					logger.log(Level.WARNING, "<" + FarmName + "> [AUTH] GSS Context failed for last message", gssE);
				}
				if (!initSecCtx.isEstabllished()) {
					final String errMsg = "Received " + AppControlMessage.APP_CONTROL_MSG_AUTH_FINISHED + " but GSSContext not established yet!";
					MonMessageClientsProxy reply = new MonMessageClientsProxy(AppControlMessage.APP_CONTROL_MSG_ERR, uidAppCtrlSessionID, errMsg, tClientID);
					logger.log(Level.WARNING, "<" + FarmName + "> [AUTH] " + reply.result);
					msgMux.sendMsg(reply);
					try {
						initSecCtx.dispose();
					} catch (Exception ex) {
						// ignore
					}
					initSecCtx = null;
				}
				// wake-up the waiting startAppControlHandshake
				synchronized (syncAppCtrlInit) {
					bAppCtrlInitFinished = true;
					syncAppCtrlInit.notify();
				}
			}
			else
				if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_ERR)) {
					logger.warning("<" + FarmName + "> AppControl ERR message received from peer: " + msg.result);
					synchronized (syncAppCtrl) {
						try {
							initSecCtx.dispose();
						} catch (Exception ex) {
							// ignore
						}
						initSecCtx = null;
						appCtrlStatus = APP_CONTROL_FAILED;
					}
				}
				else
					if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_AUTH_RETRY)) {
						// we have to refresh the security context since it has expired
						logger.info("<" + FarmName + "> The service asked to restart authentication..");
						restartAppControlAuth();
					}
					else
						if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_PROXY_ERR)) {
							// proxy error regarding this security context retry
							logger.warning("<" + FarmName + "> AppControl PROXY ERR message received: " + msg.result + "\nWill restart AppControl authentication");
							restartAppControlAuth();
						}
						else
							if (msg.tag.equals(AppControlMessage.APP_CONTROL_MSG_CMD)) {
								if (initSecCtx != null) {
									try {
										byte[] decryptedBytes = decryptMsg(initSecCtx.context, (byte[]) msg.result);
										AppControlMessage appMsg = (AppControlMessage) Utils.readObject(decryptedBytes);
										AppControlClient client = htAppControlCommands.get(appMsg.cmdID);
										if (logger.isLoggable(Level.FINER)) {
											logger.log(Level.FINER, "<" + FarmName + "> Received AppControlMessage: " + appMsg);
										}
										if (appMsg.seqNr < 0) {
											htAppControlCommands.remove(appMsg.cmdID);
										}
										if (client != null) {
											client.cmdResult(this, appMsg.cmdID, appMsg.msg, appMsg.params);
										}
										else
											if (logger.isLoggable(Level.INFO)) {
												logger.log(Level.INFO, "<" + FarmName + "> Discarding AppControlMessage " + appMsg + " since client was deleted");
											}
									} catch (Exception ex) {
										logger.log(Level.WARNING, "<" + FarmName + "> Failed to reconstruct AppControl message " + msg, ex);
									}
								}
								else {
									logger.log(Level.WARNING, "<" + FarmName + "> Discarding AppControlMessage since the security context is NULL!!");
								}
							}
	}

	/**
	 * Get the status of the AppControl procedure. Compare the result with the APP_CONTROL_* constants.
	 * 
	 * @return the status
	 */
	public int getAppControlStatus() {
		synchronized (syncAppCtrl) {
			return appCtrlStatus;
		}
	}

	/** Decrypt the given message bytes with the provided security context */
	private byte[] decryptMsg(ExtendedGSSContext gssContext, byte[] msg) throws GSSException {
		if ((gssContext == null) || !gssContext.isEstablished()) {
			throw new GSSException(GSSException.CONTEXT_EXPIRED);
		}
		synchronized (gssContext) {
			return gssContext.unwrap(msg, 0, msg.length, null);
		}
	}

	/** Ecrypt the given message bytes with the provided security context */
	private byte[] encryptMsg(ExtendedGSSContext gssContext, byte[] msg) throws GSSException {
		if ((gssContext == null) || !gssContext.isEstablished()) {
			throw new GSSException(GSSException.NO_CONTEXT);
		}
		synchronized (gssContext) {
			return gssContext.wrap(msg, 0, msg.length, null);
		}
	}

	/**
	 * computes the total number of parameters for easy retrieval.<br>
	 * It is updated when a new configuration for this farm is received.<br>
	 * Modifies field total_params.
	 */
	public void setTotalParams() {
		if (farm == null) {
			return;
		}

		// int nrClusters = farm.getClusters().size();
		// int nrNodes = farm.getNodes().size();
		// int nrParams = farm.getParameterList().size();
		final Vector<MCluster> v = farm.getClusters();

		if (v == null) {
			return;
		}

		int tparams = 0;

		for (int i = 0; i < v.size(); i++) {
			final Vector<MNode> v1 = v.get(i).getNodes();

			if (v1 != null) {
				for (final MNode node : v1) {
					tparams += node.getParameterList().size();
				}
			}
		}

		// return "<html>"+nrClusters+" cluster"+(nrClusters != 1 ? "s" : "")+"<br>"
		// +nrNodes+" node"+(nrNodes != 1 ? "s" : "")+"<br>"
		// +nrParams+" unique param"+(nrParams != 1 ? "s" : "")+"<br>"
		// +tparams+" total param"+(tparams != 1 ? "s" : "")+"</html>";

		total_params = tparams;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("MLSerClient [").append("ServiceName=").append(FarmName).append(", mlVersion=").append(mlVersion).append(", buildNr=").append(buildNr).append("]");
		return builder.toString();
	}

}
