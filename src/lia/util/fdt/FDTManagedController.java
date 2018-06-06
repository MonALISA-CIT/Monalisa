package lia.util.fdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Format;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.util.MLProcess;
import lia.util.Utils;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.fdt.xdr.LisaXDRModule;

/**
 * <ul>
 * This class encapsulates the behavior for :
 * <li>FDTListener capable to receive commands/information from a running FDT client/server.
 * <li>URL Manageable interface able to poll periodically for a remote configuration
 * </ul>
 * 
 * @author adim
 * @since 2006-11-22
 */
public abstract class FDTManagedController extends SchJob implements LisaXDRModule, MonitoringModule, ShutdownReceiver {

    /**
     * 
     */
    private static final long serialVersionUID = -3640241005236258502L;

    /** Logger used by this class */
    protected static final Logger logger = Logger.getLogger(FDTManagedController.class.getName());

    static public String OsName = "*";

    protected MNode Node;

    protected MonModuleInfo info;

    private final Properties pModuleConfiguration = new Properties();

    protected static final String DEFAULT_FDT_CONF_URL = AppConfig.getProperty("fdt.controlURL",
            "http://monalisa1.cern.ch:8000");

    protected static final String SERVLET_KEY = AppConfig.getProperty("fdt.urlkey", "5s54g^fX");

    protected static final String FDT_CLIENTS_CONF = "/Client?key=" + Format.encode(SERVLET_KEY);

    protected static final String FDT_SERVERS_CONF = "/Server?key=" + Format.encode(SERVLET_KEY);

    protected static final String FDT_TRACE_CONF = "/Trace";

    protected static final String FDT_CLIENT_CMD_PREFIX = AppConfig.getProperty("fdt.clientCmdPrefix",
            AppConfig.getProperty("java.home") + "/bin/java -jar %JAR% -c %IP% ");

    protected static final String FDT_CLIENT_CMD_SUFFIX = AppConfig.getProperty("fdt.clientCmdSuffix",
            " -lisafdtclient  &>" + AppConfig.getProperty("lia.Monitor.Farm.HOME") + "/fdtclient.log");

    // configuration read from control servlet
    protected Properties pRemoteConfig = new Properties();

    // internal state for FDT
    protected String prvCommand = "";

    protected boolean bActive = false;

    protected String linkName = "";

    final BlockingQueue<Result> bufferedResults = new LinkedBlockingQueue<Result>();

    public FDTManagedController() {

    }

    /**
     * Init the module info and parse the arguments
     * 
     * @see lia.Monitor.monitor.MonitoringModule#init(lia.Monitor.monitor.MNode, java.lang.String)
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        this.Node = node;
        this.info = new MonModuleInfo();
        try {
            init_args(args);
            this.info.setState(0);
        } catch (Exception e) {
            this.info.setState(1);// error
        }
        return info;
    }

    /**
     * Parse module configuration : monduleName{params}%30 params: params: semicolon separated list of key=value
     * 
     * @return Properties object
     */
    protected void init_args(String list) throws Exception {
        String[] splittedArgs = list.split("(\\s)*(;|,)+(\\s)*");
        for (String splittedArg : splittedArgs) {
            String[] aKeyValue = splittedArg.split("(\\s)*=(\\s)*");
            if (aKeyValue.length != 2) {
                continue;
            }
            pModuleConfiguration.put(aKeyValue[0], aKeyValue[1]);
        }
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#isRepetitive()
     */
    @Override
    public boolean isRepetitive() {
        return true;
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getNode()
     */
    @Override
    public MNode getNode() {
        return Node;
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getClusterName()
     */
    @Override
    public String getClusterName() {
        return Node.getClusterName();
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getFarmName()
     */
    @Override
    public String getFarmName() {
        return Node.getFarmName();
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getInfo()
     */
    @Override
    public MonModuleInfo getInfo() {
        return this.info;
    }

    /**
     * @see lia.Monitor.monitor.MonitoringModule#getTaskName()
     */
    @Override
    public String getTaskName() {
        return info.getName();
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    /**
     * Get the buffered Results and clear the internal buffer for the next round
     * 
     * @return
     */
    public Object getBufferedResults() {
        final int size = bufferedResults.size();
        if (size == 0) {
            return null;
        }
        Vector<Result> rV = new Vector<Result>(size);
        bufferedResults.drainTo(rV);
        return rV;
    }

    /**
     * Add a result to the internal buffer
     */
    public void addToBufferedResults(Result r) {
        bufferedResults.add(r);
    }

    @Override
    public List<String> getCommandSet() {
        List<String> lCommandSet = new LinkedList<String>();
        lCommandSet.add("monitorTransfer transfer_id parameter value\n");
        return lCommandSet;
    }

    @Override
    public String getCommandUsage(String command) {
        if (command.equalsIgnoreCase("startTransfer")) {
            return "startTransfer IPsrc IPdst filesz \n\t IPsrc: source address \n\t IPdst: dest address\n\t filesz: size of file to transfer \n\t Request performance improvements for data transfer";
        } else if (command.equalsIgnoreCase("endTransfer")) {
            return "endTransfer: \n\t Request reverting the settings";
        } else {
            return "Unknown command";
        }
    }

    protected static boolean isNull(String prv) {
        return (prv == null) || (prv.trim().length() == 0);
    }

    /** Internals * */
    protected ProcessRunner prBackgroundProcess = new ProcessRunner();

    abstract protected void startFDT(Properties config) throws IOException;

    abstract protected void stopFDT() throws IOException;

    /**
     * @see lia.util.DynamicThreadPoll.SchJob#stop()
     */
    @Override
    public boolean stop() {
        try {
            stopFDT();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error while stopFDT.", e);
        }
        return true;
    }

    /** Internals * */
    public String getFDTJar() {
        return AppConfig.getProperty("MonaLisa_HOME") + "/Service/lib/fdt.jar";
    }

    protected void reportTrace(String ip, String trace) {
        final Properties prop = getModuleConfiguration();
        final String sFDTTraceURL = prop.getProperty("controlURL",
                AppConfig.getProperty("fdt.controlURL", DEFAULT_FDT_CONF_URL))
                + FDT_TRACE_CONF;
        try {
            // Construct data
            String data = URLEncoder.encode("key", "UTF-8") + "=" + URLEncoder.encode(SERVLET_KEY, "UTF-8");
            final String myName = getName();
            data += "&" + URLEncoder.encode("name", "UTF-8") + "="
                    + URLEncoder.encode(myName == null ? getMyDetails() : myName, "UTF-8");
            data += "&" + URLEncoder.encode("target", "UTF-8") + "=" + URLEncoder.encode(ip, "UTF-8");
            data += "&" + URLEncoder.encode("trace", "UTF-8") + "=" + URLEncoder.encode(trace, "UTF-8");
            // Send data
            URL url = new URL(sFDTTraceURL);
            URLConnection conn = getURLConnection(url);
            conn.setDoOutput(true);
            conn.connect();
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();
            // Get the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while (rd.readLine() != null) {
                ;
            }
            rd.close();
            wr.close();
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "[FDTClient] Tracepath to " + ip + " (" + data + ") sent.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Cannot report trace to URL: " + sFDTTraceURL, e);
        }
    }

    protected void reportStatus(String param, String value) throws IOException {
        String sURL = null;

        URLConnection conn = null;
        BufferedReader rd = null;
        InputStreamReader isr = null;
        InputStream is = null;

        try {
            final Properties prop = getModuleConfiguration();
            final String myName = getName();
            final String sFDTClientsURL = prop.getProperty("controlURL",
                    AppConfig.getProperty("fdt.controlURL", DEFAULT_FDT_CONF_URL))
                    + FDT_CLIENTS_CONF;
            sURL = (sFDTClientsURL + "&machine.name=" + encode(myName == null ? getMyDetails() : myName)
                    + "&machine.hostname=" + encode(getMyDetails()) + "&" + param + "=" + encode(value));
            URL url = new URL(sURL);
            conn = getURLConnection(url);
            conn.connect();
            is = conn.getInputStream();
            isr = new InputStreamReader(is);
            rd = new BufferedReader(isr);
            while (rd.readLine() != null) {
                ;
            }
            rd.close();
        } finally {
            Utils.closeIgnoringException(rd);
            Utils.closeIgnoringException(isr);
            Utils.closeIgnoringException(is);
        }
    }

    /**
     * @return Properties object containing the configuration parameters for the FDT module
     */
    protected Properties getModuleConfiguration() {
        return pModuleConfiguration;
    }

    protected static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Throwable e) {
            return value;
        }
    }

    /**
     * Returns a 'unconnected' URLConnection with the cache flags set to false
     * 
     * @param url
     * @return
     * @throws IOException
     */
    protected URLConnection getURLConnection(URL url) throws IOException {
        URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);
        return conn;
    }

    private String cachedMyHostName = null; 
    
	protected String getMyHostname() {
		if (cachedMyHostName != null)
			return cachedMyHostName;

		String myHostname = null;
		try {
			myHostname = InetAddress.getLocalHost().toString();

			cachedMyHostName = myHostname;
		} catch (Throwable e) {
			myHostname = "localhost";
		}

		return myHostname;
	}

    protected String getMyDetails() {
        String myDetails = getMyHostname();
        myDetails += "<br>" + System.getProperty("java.version") + " / " + System.getProperty("os.version") + " / "
                + System.getProperty("os.arch") + " <br> FDTVersion:" + getMyVersion();
        return myDetails;
    }

    protected String getMyVersion() {
        try {
            return this.getClass().getPackage().getImplementationVersion();
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * Return the name used by FDTClient/Server used when publishing in control servlet
     * 
     * @return
     */
    protected String getName() {
        return AppConfig.getProperty("MonALISA_ServiceName") == null ? getMyHostname() : AppConfig
                .getProperty("MonALISA_ServiceName");
    }

    private static final String[] SYS_EXTENDED_PATH = new String[] { "PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin" };

    public StringBuilder getStdoutFirstLines(String cmd, int nLines) throws IOException {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Executing: [" + cmd + "]");
        }
        final Process process = MLProcess.exec(cmd, SYS_EXTENDED_PATH, -1);
        BufferedReader buff = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder ret = null;
        try {
            String crtLine = null;
            int lineCnt = 0;
            if (buff != null) {
                ret = new StringBuilder();
                while (((crtLine = buff.readLine()) != null) && (lineCnt < nLines)) {
                    ret.append(crtLine + "\n");
                    lineCnt++;
                }
                if (ret.length() == 0) {
                    ret = null;
                }
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Error while getting output for [" + cmd + "]", e);
            }
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
            if (buff != null) {
                buff.close();
            }
        }
        return ret;
    }

    /**
     * @see lia.Monitor.monitor.ShutdownReceiver#Shutdown()
     */
    @Override
    public void Shutdown() {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "Stopping module...");
        }
        stop();
    }

    // DEBUG
    /*
     * public static void main(String[] args) { FDTClient module = new FDTClient(); module.start(); int i = 0; while (i
     * < 40) { System.out.println("Getting data...");
     * module.getData(); try { Thread.sleep(10 * 1000); } catch (InterruptedException e) { e.printStackTrace(); } i++; }
     * System.exit(0); }
     */

}
