package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.process.ExternalProcesses;

/**
 * Get the TCP stack parameters that influence the transfer speeds
 * 
 * @author costing
 * @since 2010-11-02
 */
public class NetworkConfiguration extends AbstractSchJobMonitoring {
    private static final long serialVersionUID = 1L;

    /**
     * Message logger
     */
    private static final Logger logger = Logger.getLogger(NetworkConfiguration.class.getName());

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return "NetworkConfiguration";
    }

    @Override
    protected MonModuleInfo initArgs(String args) {
        return null;
    }

    @Override
    public Object doProcess() throws Exception {
        final Result r = getResult();
        final eResult er = geteResult();

        // http://monalisa.cern.ch/FDT/documentation_syssettings.html

        // sizes are exported in KB

        int iStatus = 0;

        String sMessage = null;

        if (isLinuxOS()) {
            /*
             * net.core.rmem_max = 8388608
             * net.core.wmem_max = 8388608
             * net.ipv4.tcp_rmem = 4096 87380 8388608
             * net.ipv4.tcp_wmem = 4096 65536 8388608
             * net.core.netdev_max_backlog = 250000
             * net.ipv4.tcp_congestion_control = cubic
             * net.ipv4.tcp_available_congestion_control = cubic reno
             */

            for (final String sParam : Arrays.asList("rmem_max", "wmem_max", "netdev_max_backlog")) {
                final String s = readProcFileLine("/proc/sys/net/core/" + sParam);

                if (s == null) {
                    continue;
                }

                try {
                    double value = Double.parseDouble(s);

                    if (sParam.endsWith("_max")) {
                        value /= 1024;
                    }

                    r.addSet(sParam, value);
                } catch (NumberFormatException nfe) {
                    System.err.println("Cannot parse " + s);
                }
            }

            for (final String sParam : Arrays.asList("tcp_rmem", "tcp_wmem")) {
                final String s = readProcFileLine("/proc/sys/net/ipv4/" + sParam);

                if (s == null) {
                    continue;
                }

                final StringTokenizer st = new StringTokenizer(s);

                double dMin = Double.parseDouble(st.nextToken()) / 1024;
                double dInit = Double.parseDouble(st.nextToken()) / 1024;
                double dMax = Double.parseDouble(st.nextToken()) / 1024;

                r.addSet(sParam + "_min", dMin);
                r.addSet(sParam + "_init", dInit);
                r.addSet(sParam + "_max", dMax);

                if (dMax < 1024) {
                    sMessage = addToMessage(
                            sMessage,
                            "You should increase net.ipv4."
                                    + sParam
                                    + " (max) to at least 1MB! The recommended setting is 8MB, you can set it from 4MB to 16MB depending on how many sockets you expect to keep opened on the system");
                    iStatus = 1;
                } else if (dMax < 4096) {
                    sMessage = addToMessage(
                            sMessage,
                            "A setting of 8MB for net.ipv4."
                                    + sParam
                                    + " (max) is recommended. Please keep this parameter between 4MB and 16MB, depending on how many sockets you expect to keep opened on the system.");
                    if (iStatus != 1) {
                        iStatus = 2;
                    }
                } else if (dMax > (20 * 1024)) {
                    sMessage = addToMessage(sMessage, "Careful with too large values of net.ipv4." + sParam
                            + ", too many opened sockets might exhaust your system memory.");
                    if (iStatus != 1) {
                        iStatus = 2;
                    }
                }

                if (dInit > (dMax / 4)) {
                    sMessage = addToMessage(
                            sMessage,
                            "The initial (middle) value of net.ipv4."
                                    + sParam
                                    + " (the middle value) should be way below the max (last value). 64KB is a good starting point.");

                    if (iStatus != 1) {
                        iStatus = 2;
                    }
                }

                if ((dMin >= dInit) || (dInit >= dMax)) {
                    sMessage = addToMessage(sMessage, "Please set the min/initial/max values of net.ipv4." + sParam
                            + " to progressive values, something like (4KB, 64KB, 8MB)");

                    iStatus = 1;
                }
            }

            for (final String sParam : Arrays.asList("tcp_congestion_control", "tcp_available_congestion_control")) {
                final String s = readProcFileLine("/proc/sys/net/ipv4/" + sParam);

                if (s == null) {
                    continue;
                }

                er.addSet(sParam, s);
            }
        } else if (isSolarisOS()) {
            /*
             * ndd -set /dev/tcp tcp_max_buf 8388608
             * ndd -set /dev/tcp tcp_cwnd_max 4194304
             * ndd -set /dev/tcp tcp_xmit_hiwat 524288
             * ndd -set /dev/tcp tcp_recv_hiwat 524288
             */

            try {
                final String s = ExternalProcesses.getCmdOutput(Arrays.asList("ndd", "-get", "/dev/tcp", "tcp_max_buf",
                        "tcp_cwnd_max", "tcp_xmit_hiwat", "tcp_recv_hiwat"), false, 30, TimeUnit.SECONDS);

                final StringTokenizer st = new StringTokenizer(s);

                for (String sParam : Arrays.asList("tcp_max_buf", "tcp_cwnd_max", "tcp_xmit_hiwat", "tcp_recv_hiwat")) {
                    final double value = Double.parseDouble(st.nextToken()) / 1024;

                    r.addSet(sParam, value);

                    if (sParam.equals("tcp_max_buf") || sParam.equals("tcp_cwnd_max")) {
                        if (value < 1024) {
                            sMessage = addToMessage(
                                    sMessage,
                                    "You should increase /dev/tcp "
                                            + sParam
                                            + " to at least 1MB! 8MB is a good starting point, but please keep it between 4MB and 16MB function of how much memory you have on the system and how many sockets you expect to have opened");
                            iStatus = 1;
                        } else if (value < 4096) {
                            sMessage = addToMessage(
                                    sMessage,
                                    "You should increase /dev/tcp "
                                            + sParam
                                            + " to at least 4MB! 8MB is a good starting point, but please keep it between 4MB and 16MB function of how much memory you have on the system and how many sockets you expect to have opened");
                            if (iStatus != 1) {
                                iStatus = 2;
                            }
                        } else if (value > (20 * 1024)) {
                            sMessage = addToMessage(sMessage, "Careful with too large values of net.ipv4." + sParam
                                    + ", too many opened sockets might exhaust your system memory.");
                            if (iStatus != 1) {
                                iStatus = 2;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error parsing Solaris commands : " + t);
            }
        } else {
            return null;
        }

        final Vector<Object> ret = new Vector<Object>(2);

        if ((r.param != null) && (r.param.length > 0)) {
            r.addSet("netconfig_Status", iStatus);

            if ((iStatus > 0) && (sMessage != null)) {
                er.addSet("netconfig_Message", sMessage);
            }

            ret.add(r);
        }

        if ((er.param != null) && (er.param.length > 0)) {
            ret.add(er);
        }

        return ret;
    }

    private static final String addToMessage(final String sOldMessage, final String newLine) {
        if (sOldMessage == null) {
            return newLine;
        }

        return sOldMessage + "\n" + newLine;
    }

    private static final String readProcFileLine(final String file) {
        final File f = new File(file);

        if (!f.exists() || !f.canRead()) {
            return null;
        }

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));

            final String sLine = br.readLine();

            br.close();

            return sLine;
        } catch (IOException ioe) {
            // ignore
        } finally {
            Utils.closeIgnoringException(br);
        }

        return null;
    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        NetworkConfiguration nc = new NetworkConfiguration();
        nc.init(new MNode("localhost", null, null), "");

        String s = "1\n\n2\n\n3\n\n4\n\n";

        StringTokenizer st = new StringTokenizer(s);

        for (int i = 0; i < 4; i++) {
            double d = Double.parseDouble(st.nextToken());
            System.err.println(d);
        }

        Utils.dumpResults(nc.doProcess());
    }
}
