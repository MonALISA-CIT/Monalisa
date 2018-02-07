package lia.Monitor.modules;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon2;
import lia.util.ntp.NTPDate;
import snmp.SNMPCounter32;

/**
 * Ver2 of snmp CPU module Note that this module needs SNMP ver.2, so, be kind and
 * set lia.Monitor.SNMP_version="2c" in your configuration
 * 
 * @author Adrian Muraru
 * @version Jun 28, 2005 4:10:41 PM
 * @since ML 2.0.0
 */
public class snmp_CPU_v2 extends snmpMon2 implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -7501839404260208908L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_CPU_v2.class.getName());

    static public String ModuleName = "snmp_CPU_v2";
    static public String[] ResTypes = { "CPU_usr", "CPU_nice", "CPU_sys", "CPU_idle" };

    /*
     * UCD-SNMP-MIB::ssCpuRawUser.0 = Counter32 (1.3.6.1.4.1.2021.11.50)
     * UCD-SNMP-MIB::ssCpuRawNice.0 = Counter32 (1.3.6.1.4.1.2021.11.51)
     * UCD-SNMP-MIB::ssCpuRawSystem.0 = Counter32 (1.3.6.1.4.1.2021.13.52)
     * UCD-SNMP-MIB::ssCpuRawIdle.0 = Counter32 (1.3.6.1.4.1.2021.14.53)
     */
    static String[] sOids = { "1.3.6.1.4.1.2021.11.50.0", "1.3.6.1.4.1.2021.11.51.0", "1.3.6.1.4.1.2021.11.52.0",
            "1.3.6.1.4.1.2021.11.53.0" };
    static public String OsName = "*";

    long[] old_count = null;
    long[] cur = new long[ResTypes.length];
    long[] xtmp = new long[ResTypes.length];
    long last_measured;
    long[] diff = new long[ResTypes.length];

    private static final long MAX_COUNTER32 = (2 << (32 - 1));

    public snmp_CPU_v2() {
        super(sOids);
    }

    @Override
    public MonModuleInfo init(MNode node, String args) {
        try {
            init(node);
        } catch (SocketException e) {
            // severe init error, cannot continue..
            logger.log(Level.SEVERE, "[SNMP] CommInterface could not be initialized", e);
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("CommInterface could not be initialized");
        }

        info.ResTypes = ResTypes;
        info.name = ModuleName;
        return info;
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
    public Object doProcess() throws Exception {

        if (info.getState() != 0) {
            throw new IOException("[snmp_CPU_v2 ERR] Module could not be initialized");
        }

        Map res = null;
        try {
            res = snmpBulkGet();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Got exc for Node [ " + Node.getName() + " ] :", t);
            } else if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ] :" + t.getMessage());
            }

            res = null;
        }

        if (res == null) {
            old_count = null;
            return null;
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
        result.time = NTPDate.currentTimeMillis();

        if (res.size() < 4) {
            return null;
        }

        for (int i = 0; i < sOids.length; i++) {
            Object oValue = res.get(sOids[i]);
            if ((oValue != null) && (oValue instanceof SNMPCounter32)) {
                SNMPCounter32 oc32Value = (SNMPCounter32) oValue;
                cur[i] = ((BigInteger) oc32Value.getValue()).longValue();
            }
        }
        if (old_count == null) {
            old_count = cur;
            cur = xtmp;
            last_measured = NTPDate.currentTimeMillis();
            return null;
        }
        long sum = 0;
        for (int i = 0; i < cur.length; i++) {
            diff[i] = cur[i] - old_count[i];

            if (diff[i] < 0) {
                diff[i] = (MAX_COUNTER32 - old_count[i]) + cur[i];
            }

            if (diff[i] < 0) {// should not get here
                diff[i] = 0;
            }

            sum += diff[i];
        }

        for (int i = 0; i < ResTypes.length; i++) {
            if (sum > 0) {
                result.param[i] = 100.0 * ((double) diff[i] / (double) sum);
            }
        }

        xtmp = old_count;
        old_count = cur;
        cur = xtmp;

        last_measured = result.time;

        return result;
    }

    static public void main(String[] args) {

        String host = "rb.rogrid.pub.ro";// args[0] ;
        snmp_CPU_v2 aa = new snmp_CPU_v2();
        System.setProperty("lia.Monitor.SNMP_version", "2c");
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);
        try {
            Object bb = aa.doProcess();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
            }
            Result cc = null;
            try {
                cc = (Result) aa.doProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("[SIM]  Result" + cc);
        }
    }
}
