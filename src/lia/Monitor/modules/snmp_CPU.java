package lia.Monitor.modules;

import java.net.InetAddress;
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
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_CPU extends snmpMon implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 915771639464924907L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_CPU.class.getName());

    static public String ModuleName = "snmp_CPU";
    static public String[] ResTypes = { "CPU_usr", "CPU_nice", "CPU_sys", "CPU_idle" };
    static String sOid = ".1.3.6.1.4.1.2021.11";
    static public String OsName = "*";

    long[] old_count = null;
    long[] cur = new long[ResTypes.length];
    long[] xtmp = new long[ResTypes.length];
    long last_measured;
    long[] diff = new long[ResTypes.length];
    long sum;

    public snmp_CPU() {
        super(sOid, ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
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
    public Object doProcess() {
        Vector res = null;
        try {
            res = results();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc for Node [ " + Node.getName() + " ]", t);
            res = null;
        }

        if (res == null) {
            old_count = null;
            return null;
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
        result.time = NTPDate.currentTimeMillis();

        if (res.size() < 14) {
            return null;
        }

        for (int i = 11; i <= 14; i++) {
            SnmpVarBind vb = (SnmpVarBind) res.elementAt(i);
            SnmpSyntax ss1 = vb.getValue();
            if (ss1 instanceof SnmpCounter32) {
                SnmpCounter32 aa = (SnmpCounter32) ss1;
                cur[i - 11] = aa.getValue();
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
                diff[i] = ((2 * (long) Integer.MAX_VALUE) - old_count[i]) + cur[i];
            }

            if (diff[i] < 0) {//should not get here
                diff[i] = 0;
            }

            sum += diff[i];
        }

        for (int i = 0; i < ResTypes.length; i++) {
            result.param[i] = 100.0 * ((double) diff[i] / (double) sum);
        }

        xtmp = old_count;
        old_count = cur;
        cur = xtmp;

        last_measured = result.time;

        return result;
    }

    static public void main(String[] args) {
        String host = args[0];
        snmp_CPU aa = new snmp_CPU();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), null);

        Result vv = (Result) aa.doProcess();

        Thread th = new Thread();
        try {
            //th.sleep( 10000 ) ; ??????!?????
            Thread.sleep(10000);
        } catch (Exception e) {
        }

        Result cc = (Result) aa.doProcess();
        System.out.println(cc);
        System.out.println(cc);

    }

}
