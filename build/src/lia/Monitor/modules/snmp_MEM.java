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

import org.opennms.protocols.snmp.SnmpInt32;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_MEM extends snmpMon implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -6895308062514153410L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_MEM.class.getName());

    static public String ModuleName = "snmp_MEM";

    static public String[] ResTypes = { "MEM_Free", "Swap_MEM_Free" };
    static String sOid = ".1.3.6.1.4.1.2021.4";
    static public String OsName = "*";

    long[] cur = new long[ResTypes.length];

    public snmp_MEM() {
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

        if ((res == null) || (res.size() < 13)) {
            return null;
        }

        SnmpVarBind vb = (SnmpVarBind) res.elementAt(5);
        SnmpSyntax ss1 = vb.getValue();
        if (ss1 instanceof SnmpInt32) {
            SnmpInt32 aa = (SnmpInt32) ss1;
            cur[0] = aa.getValue();
        }

        vb = (SnmpVarBind) res.elementAt(3);
        ss1 = vb.getValue();
        if (ss1 instanceof SnmpInt32) {
            SnmpInt32 aa = (SnmpInt32) ss1;
            cur[1] = aa.getValue();
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
        result.time = NTPDate.currentTimeMillis();

        for (int i = 0; i < ResTypes.length; i++) {
            result.param[i] = cur[i] / 1024.0;
        }

        return result;
    }

    static public void main(String[] args) {
        String host = args[0];
        snmp_MEM aa = new snmp_MEM();
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
            Thread.sleep(10000);
        } catch (Exception e) {
        }

        Result cc = (Result) aa.doProcess();
        System.out.println(cc);
        System.out.println(cc);

    }

}
