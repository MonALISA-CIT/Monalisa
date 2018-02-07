package lia.Monitor.modules;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon2;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;
import snmp.SNMPOctetString;

/**
 * Load{1,5,15} monitoring module - version 2  uses JSNMP library 
 * @author Adrian Muraru
 * @version Jun 28, 2005 5:16:30 PM 
 */
public class snmp_Load_v2 extends snmpMon2 implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 9152140985983034559L;

    private static final Logger logger = Logger.getLogger(snmp_Load_v2.class.getName());

    static public String ModuleName = "snmp_Load_v2";

    static public String[] ResTypes = { "Load1", "Load5", "Load15" };
    static String[] sOids = { "1.3.6.1.4.1.2021.10.1.3.1", "1.3.6.1.4.1.2021.10.1.3.2", "1.3.6.1.4.1.2021.10.1.3.3" };
    static public String OsName = "*";

    public snmp_Load_v2() {
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
        // this.mode = MODE64;
        // init_args(args);        
        info.ResTypes = ResTypes;
        info.setName(ModuleName);
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

    // Default canSuspend, but there are cases ( DC04 Filter )
    @Override
    public boolean canSuspend() {
        boolean canS = true;
        try {
            canS = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.modules.snmp_Load_v2.canSuspend", "true"))
                    .booleanValue();
        } catch (Throwable t1) {
            canS = true;
        }
        return canS;
    }

    @Override
    public Object doProcess() throws Exception {
        if (info.getState() != 0) {
            throw new IOException("[snmp_Load_v2 ERR] Module could not be initialized");
        }
        Map res = super.snmpBulkGet();

        if (res.size() != 3) {
            throw new Exception(" snmp Load failed ");
        }

        Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);
        result.time = NTPDate.currentTimeMillis();
        // Load1
        for (int i = 0; i < sOids.length; i++) {
            Object oValue = res.get(sOids[i]);
            if ((oValue != null) && (oValue instanceof SNMPOctetString)) {
                SNMPOctetString osValue = (SNMPOctetString) oValue;
                String value = StringFactory.get((byte[]) osValue.getValue());
                double dvalue = Double.valueOf(value).doubleValue();
                result.param[i] = dvalue;
            }
        }
        return result;
    }

    static public void main(String[] args) throws Exception {
        String host = "rb.rogrid.pub.ro";//args[0] ;
        snmp_Load_v2 aa = new snmp_Load_v2();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);
        Object bb = aa.doProcess();
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
            }
            Result cc = (Result) aa.doProcess();
            System.out.println("[SIM]  Result" + cc);
        }
    }
}
