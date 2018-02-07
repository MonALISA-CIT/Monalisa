package lia.Monitor.modules;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

import org.opennms.protocols.snmp.SnmpCounter64;
import org.opennms.protocols.snmp.SnmpSyntax;
import org.opennms.protocols.snmp.SnmpVarBind;

public class snmp_IO extends snmpMon implements MonitoringModule {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(snmp_IO.class.getName());

    /**
     * 
     */
    private static final long serialVersionUID = 3897034659528826714L;

    static public String ModuleName = "snmp_IO";

    String[] types = { "TotalIO_Rate_IN", "TotalIO_Rate_OUT" };

    // private static final String[] sOidv1 = {
    // ".1.3.6.1.2.1.2.2.1.10", ".1.3.6.1.2.1.2.2.1.16"
    // };

    private static final String[] sOidv2 = { ".1.3.6.1.2.1.31.1.1.1.6", ".1.3.6.1.2.1.31.1.1.1.10" };

    static public String OsName = "*";

    long last_measured = -1;

    BigInteger[][] contrs;

    BigInteger[][] old_contrs;

    BigInteger[][] tmp;

    int ports = 0;

    boolean debug = false;

    public snmp_IO() {
        super(sOidv2, ModuleName);
        info.ResTypes = types;
        info.setName(ModuleName);
    }

    @Override
    public String[] ResTypes() {
        return types;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public boolean canSuspend() {
        return false;
    }

    @Override
    public Object doProcess() throws Exception {
        synchronized (this) {
            Vector[] res = mresults();

            if (wasError) {
                logger.log(Level.WARNING, " [ snmp_IO ] Got error: (" + errorDescription + ") for node: " + getNode());
                return null;
            }

            if ((last_measured > 0) && (res.length != 2)) {
                return null;
            }

            if ((res[0] == null) || (res[1] == null)) {
                throw new Exception(" got null result in snmp_IO");
            }

            if ((res[0].size() == 0) && (res[1].size() == 0)) {
                throw new Exception(" got 0 size result in snmp_IO");
            }

            if (contrs == null) {
                contrs = new BigInteger[2][];
            }

            for (int m = 0; m < 2; m++) {
                for (int i = 0; (res[m] != null) && (i < res[m].size()); i++) {
                    if (contrs[m] == null) {
                        contrs[m] = new BigInteger[res[m].size()];
                    }
                    SnmpVarBind vb = (SnmpVarBind) res[m].elementAt(i);
                    SnmpSyntax ss1 = vb.getValue();
                    if (ss1 instanceof SnmpCounter64) {
                        SnmpCounter64 ssc = (SnmpCounter64) ss1;
                        BigInteger lc = ssc.getValue();
                        contrs[m][i] = lc;
                    }
                }
            }

            final long nanoNow = Utils.nanoNow();

            if (old_contrs == null) {
                old_contrs = contrs;
                contrs = null;
                last_measured = nanoNow;
                return null;
            }

            Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, types);

            result.time = NTPDate.currentTimeMillis();

            final long dt = nanoNow - last_measured;
            last_measured = nanoNow;
            final double factor = dt / (8D * 1000D);

            for (int m = 0; m < 2; m++) {
                double tot = 0.0;
                int i = contrs[m].length;

                for (int j = 0; j < i; j++) {
                    long diff = contrs[m][j].subtract(old_contrs[m][j]).longValue();

                    if (diff < 0) {// ignore this result...perhaps the macine was restarted, and the contors reseted!
                        diff = 0;
                    }

                    tot += diff;
                }

                result.param[m] = tot / factor;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(
                        Level.FINEST,
                        " [ snmp_IO ] Node: " + getNode() + " DT since last measure :"
                                + TimeUnit.NANOSECONDS.toMillis(dt) + " ms;  returning " + result + " current in: "
                                + res[0].toString() + " current out: " + res[1].toString());
            }
            // setting last measured field for MonModuleInfo
            info.setLastMeasurement(result.time);
            tmp = old_contrs;
            old_contrs = contrs;
            contrs = tmp;
            return result;
        }
    }

    static public void main(String[] args) throws Exception {
        String host = args[0];
        snmp_IO aa = new snmp_IO();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), null);
        Object bb = aa.doProcess();
        Thread th = new Thread();
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
        }

        Result cc = (Result) aa.doProcess();
        System.out.println(cc);

    }

}
