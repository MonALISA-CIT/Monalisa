package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

/**
  Interface to Ganglia using gmon 
*/

public class monIGanglia extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 2086087959306398390L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monIGanglia.class.getName());

    /** 
      The name of the monitoring parameters to be "extracted" from the Ganglia report 
    */

    static String[] metric = { "cpu_num", "cpu_user", "cpu_system", "cpu_nice", "bytes_in", "bytes_out", "load_five",
            "load_one", "load_fifteen", "proc_run", "mem_free", "mem_shared", "mem_cached", "mem_buffers", "mem_total",
            "disk_free", "disk_total" };
    static public String ModuleName = "monIGanglia";

    /**
      Rename them into : 
    */

    static String[] tmetric = { "NoCPUs", "CPU_usr", "CPU_sys", "CPU_nice", "TotalIO_Rate_IN", "TotalIO_Rate_OUT",
            "Load5", "Load1", "Load15", "proc_run", "MEM_free", "MEM_shared", "MEM_cached", "MEM_buffers", "MEM_total",
            "DISK_free", "DISK_total" };

    String cmd;

    public monIGanglia() {
        super("monIGanglia");
        info.ResTypes = tmetric;
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg1) {
        this.Node = Node;
        info.ResTypes = tmetric;
        String port = "8649"; // default Ganglia port 
        if (arg1 != null) {
            port = arg1;
        }

        cmd = "telnet " + Node.getIPaddress() + " " + port;

        info.name = ModuleName;
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        BufferedReader buff1 = procOutput(cmd);

        if (buff1 == null) {
            logger.log(Level.WARNING, " Failed  to get the the Ganglia output ");
            if (pro != null) {
                pro.destroy();
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
                String lin = buff.readLine();
                if (lin == null) {
                    break;
                    // System.out.println(" " + lin );
                }

                if (lin.indexOf("<HOST") != -1) {
                    i1 = lin.indexOf("=");
                    i2 = lin.indexOf("\"", i1 + 2);

                    rr = new Result();
                    rr.NodeName = lin.substring(i1 + 2, i2);
                    rr.ClusterName = Node.getClusterName();
                    rr.FarmName = Node.getFarmName();
                    rr.Module = ModuleName;
                    i1 = lin.indexOf("ED=");
                    i2 = lin.indexOf("\"", i1 + 4);
                    long time = (new Long(lin.substring(i1 + 4, i2))).longValue();
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
                                double val = Double.parseDouble(lin.substring(i1 + 5, i2));
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

            }
            buff.close();
            if (pro != null) {
                pro.destroy();
            }
            cleanup();
        } catch (Throwable t) {
            cleanup();
            logger.log(Level.WARNING, "Exeption in Get Ganglia  Ex=", t);
            buff.close();
            if (pro != null) {
                pro.destroy();
            }
            throw new Exception(t);
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

        monIGanglia aa = new monIGanglia();
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
