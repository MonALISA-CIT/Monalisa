package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * This module tries to use apcaccess to extract monitoring information from an APC UPS.<br>
 * <br>
 * The values are taken from the output of <code>apcaccess</code> tool, part of the <code>acpupsd</code>
 * package. See <a target="_blank" href="http://www.apcupsd.org/">http://www.apcupsd.org/</a> for more info
 * on this tool. By default the module will call <code>/sbin/apcaccess</code> without any arguments but you 
 * can give a full command as argument to the module.<br>
 * <br>
 * Example (myFarm.conf):<br>
 * <code>*UPS{monAPCUPS, localhost, "/sbin/apcaccess status 127.0.0.1:1234"}%60</code>
 * 
 * @author costing
 * @since 2007-02-22
 */
public class monAPCUPS extends cmdExec implements MonitoringModule {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monAPCUPS.class.getName());

    private static final long serialVersionUID = 1;

    private static final String[] vsPossiblePaths = { "/sbin/apcaccess" };

    private MonModuleInfo mmi = null;

    private MNode mn = null;

    private final long lLastCall = 0;

    private final String[] resTypes = new String[0];

    private String sCommand = "apcaccess";

    /**
     * default constructor for the module
     */
    public monAPCUPS() {
        super("monAPCUPS");
        info.ResTypes = resTypes;
        isRepetitive = true;
    }

    /**
     * Initialize data structures
     * @param node ML node
     * @param args arguments
     * @return module informations
     */
    @Override
    public MonModuleInfo init(final MNode node, final String args) {
        mn = node;

        mmi = new MonModuleInfo();
        mmi.setName("monAPCUPS");
        mmi.setState(0);

        mmi.lastMeasurement = lLastCall;

        // try to see where the "sensors" executable is	
        int iFoundCount = 0;

        for (String vsPossiblePath : vsPossiblePaths) {
            final File f = new File(vsPossiblePath);

            if (f.exists() && f.isFile()) {
                sCommand = vsPossiblePath;
                iFoundCount++;
            }
        }

        // not found at all or found several times => let PATH decide where to look
        if (iFoundCount != 1) {
            sCommand = "apcaccess";
        }

        // a module argument will override any auto discovered, if the file exists
        if ((args != null) && (args.length() > 0)) {
            sCommand = args;
        }

        return info;
    }

    /**
     * This is a dynamic module so this will return an empty array
     * @return empty array
     */
    @Override
    public String[] ResTypes() {
        return resTypes;
    }

    /**
     * Operating system on which this module can run.
     * 
     * @return Obviously "Linux"
     */
    @Override
    public String getOsName() {
        return "Linux";
    }

    /**
     * Called periodically to get data from the sensors.
     * 
     * @return a Vector with the results of the processing
     * @throws Exception if there was an error processing the output of the sensors command
     */
    @Override
    public Object doProcess() throws Exception {
        final long ls = NTPDate.currentTimeMillis();

        final Result r = new Result();
        final eResult er = new eResult();

        er.FarmName = r.FarmName = getFarmName();
        er.ClusterName = r.ClusterName = getClusterName();
        er.NodeName = r.NodeName = mn.getName();
        er.Module = r.Module = mmi.getName();
        er.time = r.time = ls;

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Running cmd='" + sCommand + "'");
        }

        final BufferedReader br = procOutput(sCommand, 5000);

        if (br == null) {
            logger.log(Level.WARNING, "Cannot run '" + sCommand
                    + "'.\nFor details increase debug level of lia.Monitor.monitor.cmdExec to FINER");
        } else {
            try {
                String line = null;
                while ((line = br.readLine()) != null) {
                    final int idx = line.indexOf(":");

                    if (idx < 0) {
                        continue;
                    }

                    final String sKey = line.substring(0, idx).trim();
                    final String sValue = line.substring(idx + 1).trim();

                    if (sKey.startsWith("END ")) {
                        continue;
                    }

                    if (sKey.equals("HOSTNAME") || sKey.equals("RELEASE") || sKey.equals("CABLE")
                            || sKey.equals("DATE") || sKey.equals("VERSION") || sKey.equals("UPSNAME")) {
                        er.addSet("daemon_" + sKey, sValue);
                    } else {
                        try {
                            final StringTokenizer st2 = new StringTokenizer(sValue);
                            final double v = Double.parseDouble(st2.nextToken());
                            r.addSet("ups_" + sKey, v);
                        } catch (Exception e) {
                            // the value is not a number, or a number followed by a space and then some other text
                            er.addSet("ups_" + sKey, sValue);
                        }
                    }
                }

                br.close();
            } finally {
                cleanup();
            }
        }

        final Vector<Object> vReturn = new Vector<Object>();

        if (((r.param == null) || (r.param.length == 0)) && ((er.param == null) || (er.param.length == 0))) {
            throw new IOException(
                    "No APC UPS detected. If apcupsd is correctly installed, please give the exact command to execute (path included) as an argument to the module");
        }

        if ((r.param != null) && (r.param.length > 0)) {
            vReturn.add(r);
        }

        if ((er.param != null) && (er.param.length > 0)) {
            vReturn.add(er);
        }

        return vReturn;
    }

    /**
     * Node name
     * 
     * @return node name
     */
    @Override
    public MNode getNode() {
        return mn;
    }

    /**
     * Cluster name
     * 
     * @return cluster name
     */
    @Override
    public String getClusterName() {
        return mn.getClusterName();
    }

    /**
     * Farm name
     * 
     * @return farm name
     */
    @Override
    public String getFarmName() {
        return mn.getFarmName();
    }

    /**
     * Of course this module is repetitive :)
     * 
     * @return true
     */
    @Override
    public boolean isRepetitive() {
        return true;
    }

    /**
     * Task name
     * 
     * @return task name
     */
    @Override
    public String getTaskName() {
        return mmi.getName();
    }

    /**
     * Module info
     * 
     * @return info
     */
    @Override
    public MonModuleInfo getInfo() {
        return mmi;
    }

    /**
     * Debug method
     * 
     * @param args command line arguments
     * @throws Exception 
     */
    public static void main(final String args[]) throws Exception {
        MFarm f = new MFarm("myFarm");
        MCluster c = new MCluster("myCluster", f);
        MNode n = new MNode("testUPS", c, f);

        monAPCUPS m = new monAPCUPS();
        m.init(n, (args != null) && (args.length > 0) ? args[0] : null);

        Utils.dumpResults(m.doProcess());
    }

}
