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
import snmp.SNMPInteger;

public class snmp_MEM_v2 extends snmpMon2 implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -8431030196784344871L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(snmp_MEM_v2.class.getName());

    static public String ModuleName = "snmp_MEM";

    static public String[] ResTypes = { "MEM_Free", "Swap_MEM_Free" };
    /*
     * UCD-SNMP-MIB::memAvailReal.0   1.3.6.1.4.1.2021.4.6.0  Available Real/Physical Memory Space on the host.
     * UCD-SNMP-MIB::memAvailSwap.0 .1.3.6.1.4.1.2021.4.4.0  Available Swap Space on the host.
     */
    static String[] sOids = { "1.3.6.1.4.1.2021.4.6.0", "1.3.6.1.4.1.2021.4.4.0" };
    static public String OsName = "*";

    long[] cur = new long[ResTypes.length];

    public snmp_MEM_v2() {
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
            throw new IOException("[snmp_MEM_v2 ERR] Module could not be initialized");
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

        if ((res == null) || (res.size() < 2)) {
            return null;
        }

        for (int i = 0; i < sOids.length; i++) {
            Object oValue = res.get(sOids[i]);
            if ((oValue != null) && (oValue instanceof SNMPInteger)) {
                SNMPInteger iValue = (SNMPInteger) oValue;
                cur[i] = ((BigInteger) iValue.getValue()).longValue();
            }
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
        result.time = NTPDate.currentTimeMillis();

        for (int i = 0; i < ResTypes.length; i++) {
            result.param[i] = cur[i] / 1024.0;
        }

        return result;
    }

    static public void main(String[] args) {

        String host = "rb.rogrid.pub.ro";//args[0] ;
        snmp_MEM_v2 aa = new snmp_MEM_v2();
        System.setProperty("lia.Monitor.SNMP_version", "2c");
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);
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
