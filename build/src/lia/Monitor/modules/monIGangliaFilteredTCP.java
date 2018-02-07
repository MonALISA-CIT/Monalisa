package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

/**
 * Interface to Ganglia using gmon
 */

public class monIGangliaFilteredTCP extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 5104474804936044552L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monIGangliaFilteredTCP.class.getName());

    TcpCmd tc = null;

    /**
     * The name of the monitoring parameters to be "extracted" from the Ganglia
     * report
     */

    static String[] metric = { "cpu_num", "cpu_user", "cpu_system", "cpu_nice", "bytes_in", "bytes_out", "load_five",
            "load_one", "load_fifteen", "proc_run", "mem_free", "mem_shared", "mem_cached", "mem_buffers", "mem_total",
            "disk_free", "disk_total" };

    static public String ModuleName = "monIGangliaFilteredTCP";
    public static final int CONNECT_TIMEOUT = Integer.valueOf(
            AppConfig.getProperty("lia.Monitor.modules.monIGangliaFilteredTCP.CONNECT_TIMEOUT", "5")).intValue() * 1000;
    /**
     * Rename them into :
     */

    static String[] tmetric = { "NoCPUs", "CPU_usr", "CPU_sys", "CPU_nice", "TotalIO_Rate_IN", "TotalIO_Rate_OUT",
            "Load5", "Load1", "Load15", "proc_run", "MEM_free", "MEM_shared", "MEM_cached", "MEM_buffers", "MEM_total",
            "DISK_free", "DISK_total" };

    String cmd;
    Vector filteredHosts = new Vector();
    int port = 8649;

    String host = "127.0.0.1";

    public monIGangliaFilteredTCP() {
        super("monIGangliaFilteredTCP");
        info.ResTypes = tmetric;
        isRepetitive = true;
        canSuspend = false;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg1) {
        this.Node = Node;
        info.ResTypes = tmetric;
        String sport = "8649"; // default Ganglia port

        if ((arg1 != null) && (arg1.length() > 0)) {
            int i1Port = arg1.indexOf("gPort");
            if (i1Port != -1) {
                arg1 = arg1.substring(i1Port + "gPort".length()).trim();
                int iComma = arg1.indexOf(",");
                if (iComma != -1) {
                    sport = arg1.substring(arg1.indexOf("=") + 1, iComma).trim();
                } else {
                    sport = arg1.substring(arg1.indexOf("=") + 1);
                }
            }

            int iHosts = arg1.indexOf("Hosts");
            if (iHosts != -1) {
                arg1 = arg1.substring(arg1.indexOf("Hosts") + "Hosts".length()).trim();
                arg1 = arg1.substring(arg1.indexOf("=") + 1).trim();
                StringTokenizer st = new StringTokenizer(arg1, ",");
                while (st.hasMoreTokens()) {
                    filteredHosts.add(st.nextToken().trim().toLowerCase());
                }
            }

            try {
                port = Integer.parseInt(sport);
            } catch (Exception e) {
                port = 8649;
            }
            cmd = "telnet " + Node.getIPaddress() + " " + port;

        } else {
            port = 8649;
        }
        host = Node.getIPaddress();
        info.name = ModuleName;
        StringBuilder sb = new StringBuilder();
        sb.append(" monIGangliaFilteredTCP port = " + port + " Hosts [ " + filteredHosts.size() + " ]= \n");
        for (int i = 0; i < filteredHosts.size(); i++) {
            sb.append(filteredHosts.elementAt(i) + "\n");
        }
        logger.log(Level.INFO, sb.toString());
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        tc = new TcpCmd(host, port, "");
        BufferedReader buff1 = tc.execute();

        if (buff1 == null) {
            tc.cleanup();
            tc = null;
            if (pro != null) {
                pro.destroy();
                pro = null;
            }
            throw new Exception(" Ganglia output  is null for " + Node.name);
        }

        return Parse(buff1);

    }

    public Vector Parse(BufferedReader buff) throws Exception {
        int i1, i2;
        Result rr = null;
        Vector results = new Vector();

        try {
            for (;;) {
                try {
                    String lin = buff.readLine();
                    if (lin == null) {
                        break;
                    }

                    if (lin.indexOf("<HOST") != -1) {
                        i1 = lin.indexOf("=");
                        i2 = lin.indexOf("\"", i1 + 2);
                        String host = lin.substring(i1 + 2, i2).toLowerCase();
                        if (!filteredHosts.contains(host)) {
                            String lin1 = null;
                            for (lin1 = buff.readLine(); (lin1 != null) && (lin1.indexOf("/HOST>") == -1); lin1 = buff
                                    .readLine()) {
                                ;
                            }
                            if (lin1 == null) {
                                break;
                            }

                            continue;
                        }
                        rr = new Result();
                        rr.NodeName = host;
                        rr.ClusterName = Node.getClusterName();
                        rr.FarmName = Node.getFarmName();
                        rr.Module = ModuleName;
                        i1 = lin.indexOf("ED=");
                        i2 = lin.indexOf("\"", i1 + 4);
                        long time = (Long.valueOf(lin.substring(i1 + 4, i2))).longValue();
                        rr.time = time * 1000;

                    } else {
                        if (lin.indexOf("/HOST>") != -1) {
                            //the output from gmond can be broken...
                            //got that in CMS-PIC 
                            if ((rr != null) && (rr.param != null) && (rr.param_name != null) && (rr.param.length > 0)
                                    && (rr.param_name.length > 0) && (rr.param.length == rr.param_name.length)) {
                                results.add(rr);
                            }
                        } else {

                            for (int l = 0; l < metric.length; l++) {

                                if (lin.indexOf(metric[l]) != -1) {
                                    i1 = lin.indexOf("VAL=");
                                    i2 = lin.indexOf("\"", i1 + 5);
                                    String sval = lin.substring(i1 + 5, i2);
                                    double val = (Double.valueOf(lin.substring(i1 + 5, i2))).doubleValue();
                                    //               trasform IO measurments in mb/s !
                                    if (metric[l].indexOf("bytes") != -1) {
                                        val = (val * 8) / 1000000.0;
                                    }
                                    //               converet memory units from KB in MB
                                    if (metric[l].indexOf("mem") != -1) {
                                        val = val / 1000.0;
                                    }

                                    rr.addSet(tmetric[l], val);
                                }
                            }

                        }
                    }
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Got I/O Exception parsing output", ioe);
                    throw ioe;
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got Exception parsing output", t);
                }

            }//for 
        } catch (Throwable t) {
            throw new Exception(t);
        } finally {
            try {
                if (pro != null) {
                    pro.destroy();
                    pro = null;
                }
            } catch (Throwable ignoreException) {
            }
            try {
                if (tc != null) {
                    tc.cleanup();
                    tc = null;
                }
            } catch (Throwable ignoreException) {
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            if ((results != null) && (results.size() > 0)) {
                sb.append(" monIGangliaTCP:- Returning [").append(results.size()).append("] results\n");
                for (int i = 0; i < results.size(); i++) {
                    Object o = results.elementAt(i);
                    sb.append(" [").append(i).append("] =>");
                    if (o == null) {
                        sb.append(" null \n");
                    } else {
                        sb.append(o.toString()).append("\n");
                    }
                }
            } else {
                sb.append("monIGangliaTCP returning no Results back to ML\n");
            }
            logger.log(Level.FINEST, "\n\n ==== Ganglia Results ====\n\n" + sb.toString()
                    + "\n\n ==== END Ganglia Results ====\n\n");
        }
        return results;

    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return tmetric;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    static public void main(String[] args) {

        String host = "localhost";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        monIGangliaFilteredTCP aa = new monIGangliaFilteredTCP();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            Object bb = aa.doProcess();

            if (bb instanceof Vector) {
                System.out.println(" Received a Vector having " + ((Vector) bb).size() + " results");
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }

    }

}
