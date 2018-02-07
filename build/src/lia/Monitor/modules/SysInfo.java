package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.process.ExternalProcesses;

/**
 * @author costing
 * @since Oct 29, 2010
 */
public class SysInfo extends AbstractSchJobMonitoring {

    /**
     * stop complaining :)
     */
    private static final long serialVersionUID = 1L;

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return "SysInfo";
    }

    private Map<String, Object> cache = null;

    private long lastCacheUpdate = System.currentTimeMillis();

    private void updateCache() throws Exception {
        final Map<String, Object> newValues = new HashMap<String, Object>();

        if (isLinuxOS()) {
            // /proc/cpuinfo -> no_CPUs / cpu_MHz / cpu_cache / cpu_family / cpu_model / cpu_model_name / cpu_vendor_id

            BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"));

            String sLine;

            int no_cpus = 0;

            while ((sLine = br.readLine()) != null) {
                int idx = sLine.indexOf(':');

                if (idx <= 0) {
                    continue;
                }

                final String sKey = sLine.substring(0, idx).trim();
                final String sValue = sLine.substring(idx + 1).trim();

                if (sKey.equals("processor")) {
                    no_cpus++;
                } else if (sKey.equals("vendor_id")) {
                    newValues.put("cpu_vendor_id", sValue);
                } else if (sKey.equals("cpu family")) {
                    newValues.put("cpu_family", Double.valueOf(sValue));
                } else if (sKey.equals("model")) {
                    newValues.put("cpu_model", Double.valueOf(sValue));
                } else if (sKey.equals("model name")) {
                    newValues.put("cpu_model_name", sValue);
                } else if (sKey.equals("stepping")) {
                    newValues.put("cpu_stepping", Double.valueOf(sValue));
                } else if (sKey.equals("cpu MHz")) {
                    final double value = Double.parseDouble(sValue);

                    final Double old = (Double) newValues.get("cpu_MHz");

                    if ((old == null) || (value > old.doubleValue())) {
                        newValues.put("cpu_MHz", Double.valueOf(value));
                    }
                } else if (sKey.equals("cache size")) {
                    newValues.put("cpu_cache", Double.valueOf(parseSize(sValue) / 1024));
                } else if (sKey.equals("bogomips")) {
                    newValues.put("bogomips", Double.valueOf(sValue));
                } else if (sKey.equals("flags")) {
                    newValues.put("cpu_flags", sValue);
                }
            }

            newValues.put("no_CPUs", Double.valueOf(no_cpus));

            Utils.closeIgnoringException(br);

            String lsb_release = null;

            try {
                lsb_release = ExternalProcesses.getCmdOutput(Arrays.asList("lsb_release", "-d"), false, 30,
                        TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }

            if (lsb_release != null) {
                if (lsb_release.startsWith("Description:")) {
                    lsb_release = lsb_release.substring("Description:".length()).trim();
                }

                newValues.put("os_type", lsb_release);
            } else {
                // try various /etc/* files if lsb_release is not available

                String sVersion = null;

                for (final String fileName : Arrays.asList("/etc/redhat-release", "/etc/debian_version",
                        "/etc/SuSE-release", "/etc/slackware-version", "/etc/gentoo-release", "/etc/mandrake-release",
                        "/etc/mandriva-release", "/etc/issue")) {

                    final File f = new File(fileName);

                    if (f.exists() && f.isFile() && f.canRead()) {
                        try {
                            br = new BufferedReader(new FileReader(f));

                            sLine = br.readLine();

                            if (sLine == null) {
                                continue;
                            }

                            sVersion = sLine.trim();

                            if (sVersion.length() > 0) {
                                break;
                            }
                        } catch (IOException ioe) {
                            // ignore
                        } finally {
                            Utils.closeIgnoringException(br);
                        }
                    }
                }

                if ((sVersion != null) && (sVersion.length() > 0)) {
                    newValues.put("os_type", sVersion);
                }
            }
        } else if (isSolarisOS()) {
            // nothing specific yet
        } else if (isMacOS()) {
            // nothing specific yet
        }

        newValues.put("kernel_version", System.getProperty("os.version"));
        newValues.put("platform", System.getProperty("os.arch"));
        newValues.put("os_name", System.getProperty("os.name"));

        if (!newValues.containsKey("os_type")) {
            newValues.put("os_type", System.getProperty("os.name"));
        }

        newValues.put("java_version", System.getProperty("java.version"));

        try {
            InetAddress addr = InetAddress.getLocalHost(); // Get IP Address 
            newValues.put("hostname", addr.getCanonicalHostName());
        } catch (UnknownHostException e) {
            // ignore
        }

        cache = newValues;

        lastCacheUpdate = System.currentTimeMillis();
    }

    /**
     * Parse a size, default in bytes
     * 
     * @param s
     * @return size in bytes
     */
    public static double parseSize(final String s) {
        final StringTokenizer st = new StringTokenizer(s);

        double d = Double.parseDouble(st.nextToken());

        if (st.hasMoreTokens()) {
            String t = st.nextToken().toLowerCase();

            switch (t.charAt(0)) {
            case 'k':
                d *= 1024;
                break;
            case 'm':
                d *= 1024 * 1024;
                break;
            case 'g':
                d *= 1024 * 1024 * 1024;
                break;
            case 't':
                d *= 1024 * 1024 * 1024 * 1024L;
                break;
            case 'p':
                d *= 1024 * 1024 * 1024 * 1024 * 1024L;
                break;
            }
        }

        return d;
    }

    private static long systemStarted = 0;

    /**
     * @return System uptime, in milliseconds since last boot
     * @throws Exception 
     */
    public static long getUptime() throws Exception {
        // linux -> /proc/uptime
        // or command
        // linux -> uptime ->  19:07:16 up 35 min,  2 users,  load average: 0.81, 0.65, 0.65
        // solaris -> uptime -> 5:50pm  up   7:14,  2 users,   load average: 0.02, 0.02, 0.02
        // mac -> uptime ->   5:43pm  up 39 days  5:35,  4 users,  load average: 0.07, 0.02, 0.01

        if (systemStarted == 0) {
            if (isLinuxOS()) {
                final BufferedReader br = new BufferedReader(new FileReader("/proc/uptime"));

                final String sLine = br.readLine();

                if (sLine != null) {
                    final StringTokenizer st = new StringTokenizer(sLine);

                    systemStarted = System.currentTimeMillis() - (long) (Double.parseDouble(st.nextToken()) * 1000);
                }

                br.close();
            } else {
                final String uptime = ExternalProcesses.getCmdOutput("uptime", false, 30, TimeUnit.SECONDS);

                if (uptime != null) {
                    int idx = uptime.indexOf("up");

                    int idx2 = uptime.indexOf("user");

                    idx2 = uptime.lastIndexOf(',', idx2);

                    long lUptime = parseUptime(uptime.substring(idx + 3, idx2).trim());

                    if (lUptime > 0) {
                        systemStarted = System.currentTimeMillis() - lUptime;
                    }
                }
            }
        }

        if (systemStarted == 0) {
            return 0;
        }

        return System.currentTimeMillis() - systemStarted;
    }

    /**
     * 11 days, 18:57
     * 87 days, 18:10
     * 7 days  3:08
     * 33 day(s),  1:54
     * 7 days,  3:45
     * 7 days,  4:35
     * 8 days, 12 mins 
     * 2:21
     * 58 min
     * 
     * @param s
     * @return
     */
    private static long parseUptime(final String s) {
        long lTime = 0;

        final StringTokenizer st = new StringTokenizer(s, " \t(),");

        int days = -1;
        int hours = -1;
        int mins = -1;
        int value = -1;

        while (st.hasMoreTokens()) {
            final String tok = st.nextToken();

            try {
                value = Integer.parseInt(tok);
                continue;
            } catch (Exception e) {
                if (tok.startsWith("day") && (value >= 0)) {
                    days = value;
                    value = -1;
                } else if (tok.startsWith("min") && (value >= 0)) {
                    mins = value;
                    value = -1;
                } else {
                    final int idx = tok.indexOf(':');
                    if (idx > 0) {
                        hours = Integer.parseInt(tok.substring(0, idx));
                        mins = Integer.parseInt(tok.substring(idx + 1));
                        value = -1;
                    }
                }
            }
        }

        if ((value >= 0) && (mins < 0)) {
            mins = value;
        }

        if (days > 0) {
            lTime = days;
        }

        lTime *= 24;

        if (hours > 0) {
            lTime += hours;
        }

        lTime *= 60;

        if (mins > 0) {
            lTime += mins;
        }

        lTime *= 60 * 1000;

        return lTime;
    }

    @Override
    public Object doProcess() throws Exception {
        final Result r = getResult();
        final eResult er = geteResult();

        if ((cache == null) || ((System.currentTimeMillis() - lastCacheUpdate) > (1000 * 60 * 60))) {
            updateCache();
        }

        if (cache == null) {
            //System.err.println("Null cache");
            return null;
        }

        for (final Map.Entry<String, Object> me : cache.entrySet()) {
            final Object o = me.getValue();

            if (o instanceof Number) {
                r.addSet(me.getKey(), ((Number) o).doubleValue());
            } else {
                er.addSet(me.getKey(), o);
            }
        }

        try {
            final long uptime = getUptime();

            if (uptime > 0) {
                r.addSet("uptime", uptime / (1000d * 3600 * 24));
            }
        } catch (Exception e) {
            // ignore
        }

        final Vector<Object> ret = new Vector<Object>(2);

        if ((r.param != null) && (r.param.length > 0)) {
            ret.add(r);
        }

        if ((er.param != null) && (er.param.length > 0)) {
            ret.add(er);
        }

        return ret;
    }

    @Override
    protected MonModuleInfo initArgs(String args) {
        return null;
    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        final SysInfo si = new SysInfo();
        si.init(new MNode("localhost", null, null), "");

        Utils.dumpResults(si.doProcess());
    }
}
