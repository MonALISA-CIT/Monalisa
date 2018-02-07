package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
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
 * This module tries to parse the output of <a href="http://www.lm-sensors.org/" target="_blank">LM Sensors</a> hardware monitors and ACPI thermal zone ones.<br>
 * To acomplish this it tries first to determine the path to <code>sensors</code> command by the following algorithm:<br>
 * <ul>
 *   <li>first it looks in well-known folders (<code>/bin, /usr/bin, /usr/local/bin, /opt/bin</code>). 
 *   If it finds the executable in one and only one of these it will use that one as the default.</li>
 *   <li>then it evaluates the module argument. If the argument is a file then it will use that path for calling the program</li>
 *   <li>if the configuration parameter <b>lia.Monitor.modules.monLMSensors.path</b> exists and denotes a file then this
 *   path will override any previously set value</li>
 *   <li>if none of the above was true, the module will try to call <code>sensors</code> using the inherited <code>PATH</code> system variable.</li>
 * </ul>
 * <br>
 * For each line of the output the module will create three entries:<br><ul>
 *   <li>sensor value : <code>lm_NAME_value</code> (double)</li>
 *   <li>measurement unit : <code>lm_NAME_unit</code> (string)</li>
 *   <li>sensor state, as reported by the <code>sensors</code> command : <code>lm_NAME_status</code> (string) [<i>OK</i>, <i>DISABLED</i> or <i>ALARM</i>]</li>
 * </ul>
 * where NAME is the first column of the output returned by the <code>sensors</code> command. 
 * <br>
 * <br>
 * The second part of this module tries to find thermal sensors exported through the ACPI interface of the kernel.
 * For this the module iterates through all folders within <code>/proc/acpi/thermal_zone</code> and for each file
 * named <code>temperature</code> it will read the contents and create two series:<br>
 * <ul>
 *   <li>sensor value : <code>acpi_NAME_value</code> (double)</li>
 *   <li>measurement unit : <code>acpi_NAME_unit</code> (string)</li>
 * </ul>
 * where NAME is the name of the folder that contains the <code>temperature</code> file (usually named <i>THRM</i> or <i>THM0</i>). 
 * 
 * @author costing
 * @since 2006-11-17
 */
public class monLMSensors extends cmdExec implements MonitoringModule {
    private static final long serialVersionUID = 1;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monLMSensors.class.getName());

    private MonModuleInfo mmi = null;

    private MNode mn = null;

    private final long lLastCall = 0;

    private final String[] resTypes = new String[0];

    private final static String[] sPossiblePaths = new String[] { "/bin/sensors", "/usr/bin/sensors",
            "/usr/local/bin/sensors", "/opt/bin/sensors" };

    private String sCommand = "sensors";

    /**
     * default constructor for the module
     */
    public monLMSensors() {
        super("monLMSensors");
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
    public MonModuleInfo init(MNode node, String args) {
        mn = node;

        mmi = new MonModuleInfo();
        mmi.setName("monLMSensors");
        mmi.setState(0);

        mmi.lastMeasurement = lLastCall;

        // try to see where the "sensors" executable is	
        int iFoundCount = 0;

        for (String sPossiblePath : sPossiblePaths) {
            File f = new File(sPossiblePath);

            if (f.exists() && f.isFile()) {
                sCommand = sPossiblePath;
                iFoundCount++;
            }
        }

        // not found at all or found several times => let PATH decide where to look
        if (iFoundCount != 1) {
            sCommand = "sensors";
        }

        // a module argument will override any auto discovered, if the file exists
        if ((args != null) && (args.length() > 0)) {
            File f = new File(args);

            if (f.exists() && f.isFile()) {
                sCommand = args;
            }
        }

        // a configuration parameter will override any previously set path, if the file exists
        String sConfigPath = AppConfig.getProperty("lia.Monitor.modules.monLMSensors.path");

        if ((sConfigPath != null) && (sConfigPath.length() > 0)) {
            File f = new File(sConfigPath);

            if (f.exists() && f.isFile()) {
                sCommand = sConfigPath;
            }
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
                jump_while: while ((line = br.readLine()) != null) {
                    StringTokenizer st = new StringTokenizer(line, ":");

                    if (st.countTokens() >= 2) {
                        final String sName = st.nextToken();
                        String sValue = st.nextToken();

                        st = new StringTokenizer(sValue, " \t()");

                        if (st.countTokens() < 1) {
                            continue;
                        }

                        sValue = st.nextToken();

                        String sUnit = st.hasMoreTokens() ? st.nextToken() : "";

                        if (sValue.startsWith("+")) {
                            sValue = sValue.substring(1);
                        }

                        final StringBuilder sbRealValue = new StringBuilder();

                        for (int i = 0; i < sValue.length(); i++) {
                            char c = sValue.charAt(i);

                            if ((c == '-') || (c == '.') || ((c >= '0') && (c <= '9'))) {
                                sbRealValue.append(c);
                            } else {
                                if (i < (sValue.length() - 1)) {
                                    sUnit = sValue.substring(i).trim();
                                }

                                break;
                            }
                        }

                        final double value;

                        try {
                            value = Double.parseDouble(sbRealValue.toString());
                        } catch (NumberFormatException nfe) {
                            continue;
                        }

                        final StringBuilder sbRealName = new StringBuilder();

                        for (int i = 0; i < sName.length(); i++) {
                            final char c = sName.charAt(i);

                            if (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) || ((c >= '0') && (c <= '9'))
                                    || (c == '-') || (c == '.')) {
                                sbRealName.append(c);
                            }
                        }

                        if (sbRealName.length() == 0) {
                            continue;
                        }

                        final String sParamName = "lm_" + sbRealName.toString() + "_value";

                        // do not add the same parameter twice. there are cases when the sensors command give the
                        // same name to several parameters. to be consistent we will take the first one always
                        for (int i = 0; (r.param_name != null) && (i < r.param_name.length); i++) {
                            if (r.param_name[i].equals(sParamName)) {
                                continue jump_while;
                            }
                        }

                        r.addSet(sParamName, value);
                        er.addSet("lm_" + sbRealName.toString() + "_unit", sUnit);

                        String sStat = "OK";

                        if (line.indexOf("disabled") >= 0) {
                            sStat = "DISABLED";
                        } else if (line.indexOf("ALARM") >= 0) {
                            sStat = "ALARM";
                        }

                        er.addSet("lm_" + sbRealName.toString() + "_status", sStat);
                    }
                }

                br.close();
            } finally {
                cleanup();
            }
        }

        // now let's process /proc/acpi		
        try {
            final File fProc = new File("/proc/acpi/thermal_zone");

            final File[] fDirs = fProc.listFiles();

            if ((fDirs != null) && (fDirs.length > 0)) {
                for (final File f : fDirs) {
                    if (f.isDirectory()) {
                        BufferedReader br2 = null;

                        try {
                            final String sName = f.getName();

                            final File fTemp = new File(f, "temperature");

                            if (fTemp.exists() && fTemp.isFile() && fTemp.canRead()) {
                                br2 = new BufferedReader(new FileReader(fTemp));

                                final String sLine = br2.readLine();

                                if (sLine != null) {
                                    final StringTokenizer st = new StringTokenizer(sLine, ": \t");

                                    if (st.countTokens() == 3) {
                                        st.nextToken();

                                        final String sValue = st.nextToken().trim();
                                        final String sUnit = st.nextToken().trim();

                                        r.addSet("acpi_" + sName + "_value", Double.parseDouble(sValue));
                                        er.addSet("acpi_" + sName + "_unit", sUnit);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            // ignore
                        } finally {
                            if (br2 != null) {
                                try {
                                    br2.close();
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // ignore
        }

        final Vector<Object> vReturn = new Vector<Object>();

        if ((r.param != null) && (r.param.length > 0)) {
            vReturn.add(r);
        } else {
            throw new IOException(
                    "No sensors detected. If the machine is correctly configured, try setting configuration parameter 'lia.Monitor.modules.monLMSensors.path' to the full path to the 'sensors' executable.");
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
    public static void main(String args[]) throws Exception {
        MFarm f = new MFarm("myFarm");
        MCluster c = new MCluster("myCluster", f);
        MNode n = new MNode("sensors", c, f);

        monLMSensors m = new monLMSensors();
        m.init(n, args.length > 0 ? args[0] : null);

        Utils.dumpResults(m.doProcess());
    }

}
