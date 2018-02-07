package lia.searchdaemon;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.RMISecurityManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.searchdaemon.comm.XDRAbstractComm;
import lia.searchdaemon.comm.XDRAgentComm;
import lia.searchdaemon.comm.XDRClientComm;
import lia.searchdaemon.comm.XDRMessage;
import lia.searchdaemon.comm.XDRMessageNotifier;
import lia.util.MLProcess;
import lia.util.security.RCSF;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.ServiceDiscoveryManager;

/**
 * 
 * Main class for launching local OpticalSwitch daemon
 * 
 */
public class SearchDaemon extends Thread implements XDRMessageNotifier {

    private static final Logger logger = Logger.getLogger(SearchDaemon.class.getName());
    private static final String default_LUDs = "monalisa.cacr.caltech.edu,monalisa.cern.ch";

    private static final Object sync = new Object();
    private static SearchDaemon _thisInstance = null;
    private static ServiceDiscoveryManager sdm;
    private static LookupDiscoveryManager ldm;
    private final boolean hasToRun;
    private boolean shouldUseJini;

    private XDRAbstractComm agentComm;

    private static SearchDaemonConfig conf;
    private MLLUSHelper LUSHelper;
    ExecutorService pool;
    ConcurrentHashMap currentTasks;

    /**
     * Inner class used to manage Pipes
     */
    class PipesMux extends Thread implements XDRMessageNotifier {
        private final XDRMessageNotifier mainNotif;
        File is;
        File os;
        private final Object syncObj;
        private final boolean stillAlive;
        private boolean needToStart;

        PipesMux(File is, File os, XDRMessageNotifier mainNotif) {
            super(" ( ML ) PipesMux ");
            this.is = is;
            this.os = os;
            syncObj = new Object();
            needToStart = true;
            stillAlive = true;
            this.mainNotif = mainNotif;
        }

        @Override
        public void notifyXDRCommClosed(XDRAbstractComm comm) {
            synchronized (syncObj) {
                needToStart = true;
                syncObj.notifyAll();
            }
            this.mainNotif.notifyXDRCommClosed(comm);
        }

        @Override
        public void notifyXDRMessage(XDRMessage xdrMessage, XDRAbstractComm comm) {
            this.mainNotif.notifyXDRMessage(xdrMessage, comm);
        }

        @Override
        public void run() {
            while (stillAlive) {
                try {
                    synchronized (syncObj) {
                        while (!needToStart) {
                            try {
                                syncObj.wait();
                            } catch (Exception e) {
                            }
                            ;
                        }
                    }

                    // TODO - take a name more normal
                    new XDRClientComm("XDRClientComm", new XDROutputStream(new FileOutputStream(os)),
                            new XDRInputStream(new FileInputStream(is)), this).start();
                    synchronized (syncObj) {
                        needToStart = false;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    private static boolean shouldUseSSL = false;
    static {
        try {
            conf = SearchDaemonConfig.getInstance();
            String cf = conf.getProperty("lia.searchdaemon.SearchDaemon.useSSL", "true");
            if (cf != null) {
                shouldUseSSL = Boolean.valueOf(cf).booleanValue();
            }
        } catch (Throwable t) {
            shouldUseSSL = false;
        }
    }

    private SearchDaemon() {
        super("( ML ) OSDaemon");
        currentTasks = new ConcurrentHashMap();

        if (!init()) {//problems during init ... should exit
            logger.log(Level.WARNING, "Problems during init() ... will exit now");
            System.exit(1);
        }
        if (shouldUseJini) {
            LUSHelper = MLLUSHelper.getInstance();
        }
        hasToRun = true;
        agentComm = null;

        try {
            // TODO - make this implicit
            if (shouldUseSSL) {
                String IPS = conf.getProperty("lia.searchdaemon.SearchDaemon.inputPipe", "caca.in");
                if (IPS == null) {
                    logger.log(Level.WARNING, "No lia.osdaemon.SearchDaemon.inputPipe specified ... will exit now");
                    System.exit(1);
                }

                File ipf = new File(IPS);
                if (!ipf.exists()) {
                    logger.log(Level.INFO, "Input pipe [" + IPS + "] does not exist. Will try to create it.");
                    mkFifo(IPS);
                    if (!ipf.exists()) {
                        logger.log(Level.WARNING, "Cannot create pipe [" + IPS + "]");
                        System.exit(1);
                    }
                } else {
                    logger.log(Level.INFO, "Input pipe [" + IPS + "] already exists...");
                }

                if (!ipf.canRead()) {
                    logger.log(Level.WARNING, "The input pipe [" + IPS + "] does not have read permissions");
                    System.exit(1);
                }

                String OPS = conf.getProperty("lia.searchdaemon.SearchDaemon.outputPipe", "caca.out");
                if (OPS == null) {
                    logger.log(Level.WARNING, "No lia.searchdaemon.SearchDaemon.outputPipe specified ... will exit now");
                    System.exit(1);
                }

                File opf = new File(OPS);
                if (!opf.exists()) {
                    logger.log(Level.INFO, "Output pipe [" + OPS + "] does not exist. Will try to create it.");
                    mkFifo(OPS);
                    if (!opf.exists()) {
                        logger.log(Level.WARNING, "Cannot create pipe [" + OPS + "]");
                        System.exit(1);
                    }
                } else {
                    logger.log(Level.INFO, "Output pipe [" + OPS + "] already exists...");
                }

                if (!opf.canWrite()) {
                    logger.log(Level.WARNING, "The output pipe [" + OPS + "] does not have write permissions");
                    System.exit(1);
                }

                new PipesMux(ipf, opf, this).start();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot start XDRTcpServer");
            System.exit(1);
        }
        pool = Executors.newCachedThreadPool();
    }

    @Override
    public void notifyXDRCommClosed(XDRAbstractComm comm) {
        String key = comm.getKey();
        System.out.println("OSDaemon - notifyXDRCommClosed removing K = " + key);
        MLPathTask mlpt = (MLPathTask) currentTasks.remove(key);
        if (mlpt != null) {
            mlpt.notifyClosed();
        } else {
            System.out.println("OSDaemon - notifyXDRCommClosed removing K = " + key + " MLPathTask == null ");
        }
        if (comm == agentComm) {
            agentComm = null;
        }
    }

    private void mkFifo(String pipeName) {
        try {
            Process pro = null;
            InputStream out = null;
            BufferedReader br = null;
            pro = MLProcess.exec(new String[] { "mkfifo", pipeName });
            pro.waitFor();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot create named PIPE", t);
        }
    }

    @Override
    public void notifyXDRMessage(XDRMessage xdrMessage, XDRAbstractComm comm) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " notify() XDRMessage = " + xdrMessage.toString());
        }

        try {
            if (comm != agentComm) {
                System.out.println(" [ " + System.currentTimeMillis() + " ] " + " Got from client .... \n\n");
                String key = comm.getKey();
                MLPathTask mlpt = (MLPathTask) currentTasks.get(key);
                if (mlpt == null) {
                    mlpt = new MLPathTask(key, comm, agentComm);
                    currentTasks.put(key, mlpt);
                    pool.execute(mlpt);
                }
                mlpt.notify(xdrMessage, comm);
            } else {
                System.out.println(" [ " + System.currentTimeMillis() + " ] " + " Got from agent .... \n\n");
                MLPathTask mlpt = (MLPathTask) currentTasks.get(xdrMessage.id);
                if (mlpt != null) {
                    mlpt.notify(xdrMessage, comm);
                } else {
                    System.out.println(" [ " + System.currentTimeMillis() + " ] "
                            + " Got from agent .... but no task for this id [ \n\n" + xdrMessage.id + " = "
                            + xdrMessage.toString());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private boolean init() {
        shouldUseJini = true;
        try {
            shouldUseJini = Boolean.valueOf(conf.getProperty("lia.searchdaemon.SearchDaemon.shouldUseJini", "true"))
                    .booleanValue();
        } catch (Throwable t) {
            shouldUseJini = true;
        }
        if (!shouldUseJini) {
            return true;
        }

        try {
            // get specified LookupLocators[]
            LookupLocator[] lookupLocators = getLUDSs();
            if ((lookupLocators == null) || (lookupLocators.length == 0)) {
                logger.log(Level.SEVERE, "Got null/zero size length lookup locators");
                return false;
            }

            Configuration cfgLUSs = null;
            try {//this will help to allow only unicast discovery
                cfgLUSs = getBasicExportConfig();
            } catch (Throwable t1) {
                cfgLUSs = null;
            }

            ldm = new LookupDiscoveryManager(null, lookupLocators, null, cfgLUSs);
            try {
                Thread.sleep(5000); //wait to initialize lookupDiscoveryManager
            } catch (Exception e) {
            }

            LeaseRenewalManager lrm = new LeaseRenewalManager();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }

            sdm = new ServiceDiscoveryManager(ldm, lrm);
            try {
                Thread.sleep(3000); //wait to initialize sdm
            } catch (Exception e) {
            }

            MLJiniManagersProvider.setManagers(null, sdm, null);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Got exception in init()", t);
            return false;
        } // try - catch
        return true;
    } // init

    public static final SearchDaemon getInstance() {
        synchronized (sync) {
            if (_thisInstance == null) {
                _thisInstance = new SearchDaemon();
            }
        } // synchronized

        return _thisInstance;
    } // getInstance

    private LookupLocator[] getLUDSs() {
        String[] luslist = conf.getVectorProperty("lia.Monitor.LUSs", default_LUDs);
        if ((luslist == null) || (luslist.length == 0)) {
            return null;
        }
        int count = luslist.length;
        LookupLocator[] locators = new LookupLocator[count];

        int i;
        for (i = 0; i < count; i++) {
            String host = luslist[i];
            try {
                locators[i] = new LookupLocator("jini://" + host);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception trying to create LookupLocator(" + host
                        + ")... This is strange, sohuld NOT!!!", t);
            }
        }

        //return only the good ones
        if (i == count) {
            return locators;
        }

        LookupLocator[] nlocators = new LookupLocator[i];
        for (int j = 0; j < i; j++) {
            nlocators[j] = locators[j];
        }

        return nlocators;
    }

    private static final Configuration getBasicExportConfig() {
        StringBuilder config = new StringBuilder();
        String[] options = new String[] { "-" };

        config.append("import java.net.NetworkInterface;\n");
        config.append("net.jini.discovery.LookupDiscovery {\n");
        config.append("multicastInterfaces = new NetworkInterface[]{};\n");
        config.append("}\n");

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Config content:\n\n" + config.toString());
        }

        StringReader reader = new StringReader(config.toString());

        try {
            return new ConfigurationFile(reader, options);
        } catch (ConfigurationException ce) {
            logger.log(Level.SEVERE, "Cannot get config object");
        }
        return null;
    }

    public void connectToMLCopyAgent() {
        try {
            String address = conf.getProperty("lia.searchdaemon.SearchDaemon.MLAgentAddress");
            String portS = conf.getProperty("lia.osdaemon.SearchDaemon.MLAgentPort");

            if ((address == null) || (portS == null)) {
                return;
            }

            InetAddress ia = null;
            int port = -1;
            try {
                ia = InetAddress.getByName(address);
                port = Integer.valueOf(portS).intValue();
            } catch (Throwable t) {
                port = -1;
                ia = null;
            }
            if ((ia == null) || (port == -1)) {
                return;
            }

            Socket s = null;
            try {
                if (shouldUseSSL) {
                    logger.log(Level.INFO, "Using SSL to connect to [ " + address + ":" + port + " ]");
                    s = new RCSF().createSocket(address, port);
                } else {
                    s = new Socket();
                    s.connect(new InetSocketAddress(ia, port), 20 * 1000);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                s = null;
            }
            if (s != null) {
                s.setTcpNoDelay(true);
                // TODO - give a more normal name
                agentComm = XDRAgentComm.newInstance("XDRAgentComm", s, this);
                agentComm.start();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (agentComm != null) {
                try {
                    agentComm.close();
                } catch (Throwable t1) {
                    t1.printStackTrace();
                }
            }
            agentComm = null;
        }
    }

    @Override
    public void run() {
        while (hasToRun) {
            try {
                try {
                    Thread.sleep(1000);
                } catch (Throwable t) {

                }
                if (agentComm == null) {
                    connectToMLCopyAgent();
                }
            } catch (Throwable t1) {
                t1.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
        SearchDaemon searchd = getInstance();
        searchd.start();
    } // main
} // SearchDaemon
