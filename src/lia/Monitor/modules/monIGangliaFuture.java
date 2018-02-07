package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

/**
 * Interface to Ganglia using gmon
 */

public class monIGangliaFuture extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -8233975180257956158L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monIGangliaFuture.class.getName());

    private static final long SECOND = 60 * 1000;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;

    private int nodeNo = 1;

    /**
     * The name of the monitoring parameters to be "extracted" from the Ganglia
     * report
     */

    static String[] metric = { "cpu_num", "cpu_user", "cpu_system", "cpu_nice", "bytes_in", "bytes_out", "load_five",
            "load_one", "load_fifteen", "proc_run", "mem_free", "mem_shared", "mem_cached", "mem_buffers", "mem_total",
            "disk_free", "disk_total" };

    static public String ModuleName = "monIGangliaFuture";
    /**
     * Rename them into :
     */

    static String[] tmetric = { "NoCPUs", "CPU_usr", "CPU_sys", "CPU_nice", "TotalIO_Rate_IN", "TotalIO_Rate_OUT",
            "Load5", "Load1", "Load15", "proc_run", "MEM_free", "MEM_shared", "MEM_cached", "MEM_buffers", "MEM_total",
            "DISK_free", "DISK_total" };

    public monIGangliaFuture() {
        super("monIGangliaFuture");
        info.ResTypes = tmetric;
        isRepetitive = true;
        nodeNo = 1;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg1) {
        this.Node = Node;
        info.ResTypes = tmetric;
        info.name = ModuleName;
        if ((arg1 != null) && (arg1.indexOf("NoOfNodes") >= 0)) {
            String args[] = arg1.split("((\\s)*,(\\s)*)|((\\s)*;(\\s)*)");
            if (args != null) {
                for (String arg : args) {
                    int index = arg.indexOf("NoOfNodes");
                    if (index >= 0) {
                        try {
                            nodeNo = Integer.valueOf(arg.split("(\\s)*=(\\s)*")[1]).intValue();
                        } catch (Throwable t) {
                            nodeNo = 1;
                        }
                    }
                }
            }
        }
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        //   BufferedReader buff1 = procOutput ( cmd );
        //   BufferedReader buff1 = TcpCmd.TcpCmd ( host, port, "" );
        return Parse();

    }

    public Vector Parse() throws Exception {
        Vector results = new Vector();

        for (int j = 0; j < nodeNo; j++) {
            try {
                Result rr = null;
                rr = new Result();
                rr.NodeName = "FutureNode" + j;
                rr.ClusterName = "PN_Future";
                rr.FarmName = Node.getFarmName();
                rr.Module = ModuleName;
                rr.time = NTPDate.currentTimeMillis() + WEEK;
                for (int i = 0; i < metric.length; i++) {
                    rr.addSet(metric[i], i);
                }
                if (rr != null) {
                    results.add(rr);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exc", t);
            }
        }
        //System.out.println ( results.size() );
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

        monIGangliaFuture aa = new monIGangliaFuture();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), "8649 ; NoOfNodes= 12, 8649");

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
