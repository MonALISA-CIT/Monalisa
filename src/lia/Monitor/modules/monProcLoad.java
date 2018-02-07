/*
 * $Id: monProcLoad.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.monitor.AttributePublisher;
import lia.Monitor.monitor.MLAttributePublishers;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 */
public class monProcLoad extends monProcReader {

    /**
     * @since ML 1.5.4
     */
    private static final long serialVersionUID = -3195248260332505852L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monProcLoad.class.getCanonicalName());

    static private final String ModuleName = "monProcLoad";

    static private final String[] ResTypes = { "Load1", "Load5", "Load15", "Tasks_running", "Tasks_total" };

    static private final String OsName = "linux";

    /**
     * 
     */
    double load5;

    private static final AtomicBoolean alreadyStarted = new AtomicBoolean(false);
    /**
     * 
     */
    static final AttributePublisher publisher = MLAttributePublishers.getInstance();

    /**
     * @throws Exception
     */
    public monProcLoad() throws Exception {
        super(ModuleName);

        final File f = new File("/proc/loadavg");
        if (!f.exists() || !f.canRead()) {
            throw new MLModuleInstantiationException("Cannot read /proc/loadavg");
        }

        PROC_FILE_NAMES = new String[] { "/proc/loadavg" };
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;

        if (alreadyStarted.compareAndSet(false, true)) {
            initPublishTimer();
        }
    }

    private void initPublishTimer() {
        final Runnable ttAttribUpdate = new Runnable() {
            @Override
            public void run() {
                try {
                    publisher.publish("Load5", Double.valueOf(load5));
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "monProcLoad LUS Publisher: Got Exception", t);
                }
            }
        };

        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(ttAttribUpdate, 40, 2 * 60, TimeUnit.SECONDS);
        logger.log(Level.INFO, "[ monProcLoad ] Attributes Update thread shcheduled");
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    protected Object processProcModule() throws Exception {
        return process_load(bufferedReaders[0]);
    }

    /**
     * @param br
     * @return the new Result
     * @throws Exception
     */
    private Result process_load(BufferedReader br) throws Exception {
        final Result res = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);

        res.time = NTPDate.currentTimeMillis();
        final String lin = br.readLine();

        if (lin != null) {
            final StringTokenizer tz = new StringTokenizer(lin, " \t/");

            res.param[0] = load5 = Double.parseDouble(tz.nextToken());
            res.param[1] = Double.parseDouble(tz.nextToken());
            res.param[2] = Double.parseDouble(tz.nextToken());
            res.param[3] = Double.parseDouble(tz.nextToken());
            res.param[4] = Double.parseDouble(tz.nextToken());
        }

        br.close();
        cleanup();

        return lin != null ? res : null;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    /**
     * @param args
     * @throws Exception
     */
    static public void main(String[] args) throws Exception {
        String host = "localhost"; // args[0] ;
        monProcLoad aa = new monProcLoad();
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));
        String ad = null;
        logger.setLevel(Level.ALL);
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        aa.init(new MNode(host, ad, null, null), null, null);

        try {
            for (;;) {
                Thread.sleep(5000);
                logger.log(Level.INFO, ":- bb :-\n" + aa.doProcess());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
