package lia.Monitor.modules;

import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpCounter32;
import org.opennms.protocols.snmp.SnmpObjectId;
import org.opennms.protocols.snmp.SnmpTimeTicks;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_IOpp extends snmpMon implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -4666804863285327452L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_IOpp.class.getName());

    static public String ModuleName = "snmp_IOpp";

    static String timeTicksOID = ".1.3.6.1.2.1.1.3";
    static String oidin = ".1.3.6.1.2.1.2.2.1.10";
    static String oidout = ".1.3.6.1.2.1.2.2.1.16";
    static String[] sOid = { timeTicksOID, oidin, oidout };

    static public String OsName = "*";

    protected boolean canSuspend = false;

    String[] types;
    SnmpObjectId[] voidin;
    SnmpObjectId[] voidout;
    long[][] counters;
    long[][] old_counters;
    long[][] keep;
    int[] sports;

    long last_measured = -1;
    int ports = 0;
    boolean debug = false;
    int NPORTS;

    public snmp_IOpp() {
        super(sOid, ModuleName);
        info.setName(ModuleName);
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        super.init(Node, null);
        init_args(arg);
        info.ResTypes = types;
        return info;

    }

    void init_args(String list) {
        StringTokenizer tz = new StringTokenizer(list, ";");
        int Nargs = tz.countTokens();

        NPORTS = Nargs;
        counters = new long[2][NPORTS];
        keep = new long[2][NPORTS];
        types = new String[2 * Nargs];
        sports = new int[Nargs];
        voidin = new SnmpObjectId[Nargs];
        voidout = new SnmpObjectId[Nargs];

        int k = 0;
        while (tz.hasMoreTokens()) {
            String ss = tz.nextToken();
            int i1 = ss.indexOf("=");
            int nport = (Integer.valueOf(ss.substring(0, i1).trim())).intValue();
            String pname = ss.substring(i1 + 1).trim();
            SnmpObjectId aain = new SnmpObjectId(oidin + "." + nport);
            SnmpObjectId aaout = new SnmpObjectId(oidout + "." + nport);
            voidin[k] = aain;
            voidout[k] = aaout;

            types[2 * k] = pname + "_IN";
            types[(2 * k) + 1] = pname + "_OUT";
            sports[k] = nport;
            k++;
        }

    }

    @Override
    public String[] ResTypes() {
        return types;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    private void resetOldCounters() {
        if (old_counters != null) {
            keep = old_counters;
            old_counters = null;
        }
    }

    @Override
    public Object doProcess() throws Exception {

        Vector[] res = null;
        try {
            res = mresults();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ]", t);
            res = null;
        }

        if (wasError) {
            logger.log(Level.WARNING, "Received error for " + Node.getName() + " Error description : "
                    + errorDescription);
            resetOldCounters();
            return null;
        }

        if (res == null) {//Just in case of SNMP Errors ...
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Got null vector of results ... from mresults()");
            }
            resetOldCounters();
            return null;
        }

        if (res.length != 3) {//SNMP Errors ... should not happen
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "\n\nGot res.length == " + res.length + " ... should get length == 3");
            }
            return null;
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, types);
        result.time = NTPDate.currentTimeMillis();

        long snmpTime = -1;

        try {
            if (res[0] != null) {
                SnmpVarBind vb = (SnmpVarBind) res[0].elementAt(0);
                snmpTime = ((SnmpTimeTicks) vb.getValue()).getValue();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception trying to read the time from SNMP", t);
            return null;
        }

        if (snmpTime == -1) {
            logger.log(Level.WARNING, "SNMP time still not set ??!");
            return null;
        }

        long dt = 0;

        if (snmpTime > 0) {
            if (last_measured >= snmpTime) {//restarted ?
                logger.log(Level.WARNING, "last_measured [" + last_measured + " ] >= snmpTime [ " + snmpTime
                        + " ] Was the device [ " + Node.getName() + " ] restarted ?");
                last_measured = snmpTime;
                resetOldCounters();
                return null;
            }

            dt = snmpTime - last_measured;
            last_measured = snmpTime;
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " dt = " + dt);
            }
        } else {
            logger.log(Level.INFO, "Negative SNMP Time ?! " + snmpTime);
            return null;
        }

        if (dt == 0) {
            logger.log(
                    Level.INFO,
                    " diff == 0 for SNMPtime for "
                            + Node.getName()
                            + " ... probably the counters still not updated (... SNMP has high Load or SNMP queries are too often )");
            return null;
        }

        for (int i = 0; i < res[1].size(); i++) {
            SnmpVarBind vb = (SnmpVarBind) res[1].elementAt(i);
            SnmpObjectId idx = vb.getName();
            for (int ci = 0; ci < NPORTS; ci++) {
                if (idx.equals(voidin[ci])) {
                    SnmpCounter32 ssc = (SnmpCounter32) vb.getValue();
                    counters[0][ci] = ssc.getValue();
                }
            }
        }

        for (int i = 0; i < res[2].size(); i++) {
            SnmpVarBind vb = (SnmpVarBind) res[2].elementAt(i);
            SnmpObjectId idx = vb.getName();
            for (int ci = 0; ci < NPORTS; ci++) {
                if (idx.equals(voidout[ci])) {
                    SnmpCounter32 ssc = (SnmpCounter32) vb.getValue();
                    counters[1][ci] = ssc.getValue();
                }
            }
        }

        if (old_counters == null) {
            old_counters = counters;
            counters = keep;
            return null;
        }

        boolean negativeDiff = false;

        for (int m = 0; m < 2; m++) {
            for (int ci = 0; ci < NPORTS; ci++) {
                double diff = counters[m][ci] - old_counters[m][ci];
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ " + Node.getName() + " ---> " + result.param_name[(2 * ci) + m]
                            + " ] New: " + counters[m][ci] + " Old: " + old_counters[m][ci] + " diff: " + diff);
                }
                if (diff < 0) {
                    negativeDiff = true;
                    logger.log(Level.WARNING, " Diff neg [ " + Node.getName() + " ---> "
                            + result.param_name[(2 * ci) + m] + " ] ... New: " + counters[m][ci] + " Old: "
                            + old_counters[m][ci] + " diff: " + diff);
                    continue;
                }
                //time from SNMP is in hundredths of a second
                result.param[(2 * ci) + m] = (diff / (10000.0D * dt)) * 8.0D;
            }
        }

        info.setLastMeasurement(result.time);
        keep = old_counters;
        old_counters = counters;
        counters = keep;
        if (negativeDiff) {
            return null;
        }
        return result;
    }

    static public void main(String[] args) throws Exception {
        String host = args[0];
        snmp_IOpp aa = new snmp_IOpp();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        String arg = args[1];
        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), arg);
        Object bb = aa.doProcess();
        while (true) {
            try {
                //th.sleep( 30000 ) ;
                Thread.sleep(20000);
            } catch (Exception e) {
            }

            Result cc = (Result) aa.doProcess();
            System.out.println(cc);
        }

    }

}