package lia.web.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.Store.Main;
import lia.Monitor.modules.monProcIO;
import lia.Monitor.modules.monProcLoad;
import lia.Monitor.modules.monProcStat;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.ApMon.ApMon;
import lia.util.ApMon.ApMonException;

/**
 * Send repository statistics to the central repository monitoring service
 * 
 * @author costing
 */
public class ExportStatistics extends TimerTask {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(ExportStatistics.class.getName());

    private static Timer timer = new Timer();

    private final Vector<MonitoringModule> vModules = new Vector<MonitoringModule>();

    /**
     * ApMon instance that actually sends monitoring information
     */
    ApMon apm = null;

    private MNode node = null;

    /**
     * @author costing
     *
     */
    class ApMonStarter extends Thread {

        /**
         * Configuration URL
         */
        String sConfig;

        /**
         * @param config
         */
        ApMonStarter(String config) {
            super("(ML) ApMonStarter");

            this.sConfig = config;
        }

        /**
         * Periodically read the configuration URL until and ApMon object is correctly instantiated.
         * This is to prevent temporary network problems from affecting the internal monitoring.
         */
        @Override
        public void run() {
            Thread.currentThread().setName(" ( ML ) - web utils - ExportStatistics - ApMonStarter loop");

            while (apm == null) {
                ApMon apmt = null;

                try {
                    if (sConfig.substring(0, 7).equalsIgnoreCase("http://")) {
                        Vector<String> v = new Vector<String>();
                        v.add(sConfig);
                        apmt = new ApMon(v);
                    } else {
                        apmt = new ApMon(sConfig);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                apm = apmt;

                if (apm == null) {
                    try {
                        sleep(60000);
                    } catch (InterruptedException e) {
                        // ignore this
                    }
                }
            }

            apm.setRecheckInterval(600);
        }

    }

    /**
     * Initialize the monitoring
     * 
     * @param sCluster cluster name under which to send the data (repository / proxy / ... )
     * @param repositoryPort repository public port
     */
    protected void init(String sCluster, int repositoryPort) {
        init(sCluster, repositoryPort, null);
    }

    /**
     * Initialize the monitoring
     * 
     * @param sCluster cluster name under which to send the data (repository / proxy / ... )
     * @param repositoryPort repository public port
     * @param sExtraNodeString extra string to add to the node name 
     */
    protected void init(String sCluster, int repositoryPort, String sExtraNodeString) {
        String sConfig = AppConfig.getProperty("lia.Repository.ApMonExport.ConfigFile",
                "http://monalisa.caltech.edu/repository.conf");
        if ((sConfig == null) || (sConfig.length() <= 0)) {
            System.out.println("NOT Starting ExportStatistics with ApMon! sConfig is NULL");
            return;
        }

        sConfig = sConfig.trim();

        System.out.println("Starting ExportStatistics with ApMon with sConfig=" + sConfig);
        (new ApMonStarter(sConfig)).start();

        String sHost = "";
        String sShortHost = "";
        try {
            sShortHost = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            // ignore exception if the name cannot be found
        }

        try {
            sHost = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception ex) {
            // ignore exception if the host does not have a fqdn
        }

        if (sShortHost.length() > sHost.length()) {
            sHost = sShortHost;
        }

        try {
            sHost = AppConfig.getProperty("lia.Repository.ApMonExport.HostName", sHost).trim();
        } catch (Exception e) {
            // ignore exception if the property is not set
        }

        String sRepositoryAddr = sHost + (repositoryPort > 0 ? ":" + repositoryPort : "")
                + (sExtraNodeString != null ? sExtraNodeString : "");

        MFarm farm = new MFarm(sRepositoryAddr);
        MCluster cluster = new MCluster(sCluster, farm);

        System.err.println("Export: node=" + sRepositoryAddr);

        node = new MNode(sRepositoryAddr, "127.0.0.1", cluster, farm);

        timer.scheduleAtFixedRate(this, 60000, 60000);
    }

    /**
     * simple constructor
     */
    protected ExportStatistics() {
        // just so that other classes could override this constructor
    }

    /**
     * Constructor for repository monitoring
     * 
     * @param repositoryPort public port for the web interface of the repository
     */
    public ExportStatistics(int repositoryPort) {
        System.err.println("ExportStatistics(" + repositoryPort + ")");

        init("Repository", repositoryPort);

        MonitoringModule mm = null;
        try {
            mm = new monProcLoad();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportStatistics ] [ HANDLED ]Unable to instanitate monProcLoad()", t);
        }

        if (mm != null) {
            addMonitoringModule(mm);
        }

        try {
            mm = new monProcStat();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportStatistics ] [ HANDLED ]Unable to instanitate monProcStat()", t);
            mm = null;
        }
        if (mm != null) {
            addMonitoringModule(mm);
        }

        try {
            mm = new monProcIO();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportStatistics ] [ HANDLED ]Unable to instanitate monProcIO()", t);
            mm = null;
        }
        if (mm != null) {
            addMonitoringModule(mm);
        }
        addMonitoringModule(new lia.Monitor.modules.monIPAddresses());
        addMonitoringModule(new lia.Monitor.modules.monRepositoryStats());
        addMonitoringModule(new lia.Monitor.modules.monLMSensors());
    }

    /**
     * Configure a monitoring module
     * 
     * @param module a fresh instance of a monitoring module 
     */
    public void addMonitoringModule(final MonitoringModule module) {
        if ((module == null) || (node == null)) {
            return;
        }

        final String sClass = module.getClass().getName();

        synchronized (vModules) {
            for (int i = 0; i < vModules.size(); i++) {
                String s = vModules.get(i).getClass().getName();

                if (s.equals(sClass)) {
                    return;
                }
            }

            module.init(node, null);
            vModules.add(module);
        }
    }

    /**
     * Periodically run method, calls all monitoring modules for data and sends the parameters to the 
     * central service in bunches of 10 parameters / packet 
     */
    @Override
    public void run() {
        Thread.currentThread().setName(" ( ML ) - web utils - ExportStatistics - Periodic run - active");

        try {
            final boolean bSelfMonitoring = AppConfig.getb("lia.Repository.selfMonitoring", false);

            //System.out.println("ExportStatistics... run(); apm="+apm);
            if ((apm == null) && !bSelfMonitoring) {
                return;
            }

            Vector<Object> v = new Vector<Object>();

            for (int i = 0; (vModules != null) && (i < vModules.size()); i++) {
                MonitoringModule m = vModules.get(i);

                try {
                    Object o = m.doProcess();

                    if (o == null) {
                        continue;
                    }

                    if (o instanceof Result) {
                        v.add(o);
                    } else if (o instanceof eResult) {
                        v.add(o);
                    } else if (o instanceof Collection<?>) {
                        for (Object o2 : (Collection<?>) o) {
                            v.add(o2);
                        }
                    }
                } catch (Exception e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Exception running some module: " + e + " (" + e.getMessage() + ")", e);
                    }
                }
            }
            //System.out.println("ExportStats: got "+v.size()+" results after running modules");
            if (v.size() <= 0) {
                return;
            }

            Vector<String> paramNames = new Vector<String>();
            Vector<Object> paramValues = new Vector<Object>();
            Vector<Integer> valueTypes = new Vector<Integer>();

            //System.err.println(" -> v.size = "+v.size());

            for (int i = 0; i < v.size(); i++) {
                Object o = v.get(i);

                if (o instanceof Result) {
                    Result r = (Result) o;

                    for (int j = 0; (r.param_name != null) && (j < r.param_name.length); j++) {
                        paramNames.add(r.param_name[j]);
                        paramValues.add(Double.valueOf(r.param[j]));
                        valueTypes.add(Integer.valueOf(ApMon.XDR_REAL64));
                    }
                }

                if (o instanceof eResult) {
                    eResult r = (eResult) o;

                    for (int j = 0; (r.param_name != null) && (j < r.param_name.length); j++) {
                        if (r.param[j] instanceof String) {
                            paramNames.add(r.param_name[j]);
                            paramValues.add(r.param[j]);
                            valueTypes.add(Integer.valueOf(ApMon.XDR_STRING));
                        }
                    }
                }
            }

            if (bSelfMonitoring) {
                try {
                    final Main m = Main.getInstance();

                    if (m != null) {
                        m.newFarmResult(null, v);
                    }
                } catch (Throwable t) {
                    System.err.println("ExportStatistics: self monitoring exception: " + t + " (" + t.getMessage()
                            + ")");
                    t.printStackTrace();
                }
            }

            if (apm == null) {
                return;
            }

            if (paramNames.size() <= 0) {
                return;
            }

            try {
                //System.out.println("ExportStats: Sending params "+node.name+" -> "+paramNames+" = "+paramValues);

                final Vector<String> paramNames1 = new Vector<String>();
                final Vector<Integer> valueTypes1 = new Vector<Integer>();
                final Vector<Object> values1 = new Vector<Object>();

                for (int i = 0; i < paramNames.size(); i++) {
                    if ((i % 10) == 9) {
                        apm.sendParameters(node.cluster.name, node.name, paramNames1.size(), paramNames1, valueTypes1,
                                values1);
                        paramNames1.clear();
                        valueTypes1.clear();
                        values1.clear();
                    }

                    paramNames1.add(paramNames.get(i));
                    valueTypes1.add(valueTypes.get(i));
                    values1.add(paramValues.get(i));
                }

                if (paramNames1.size() > 0) {
                    apm.sendParameters(node.cluster.name, node.name, paramNames1.size(), paramNames1, valueTypes1,
                            values1);
                }
            } catch (RuntimeException e) {
                System.err.println("Send operation failed because: ");
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (ApMonException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Throwable t) {
                System.err.println("ApMon sending: other exception: " + t + " (" + t.getMessage() + ")");
                t.printStackTrace();
            }

        } finally {
            Thread.currentThread().setName(" ( ML ) - web utils - ExportStatistics - Periodic run - idle");
        }
    }

}
