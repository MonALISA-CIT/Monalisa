package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

public class monBwctl extends cmdExec implements MonitoringModule {

    /** serial version number */
    static final long serialVersionUID = 1108200525091981L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monBwctl.class.getName());

    static public String ModuleName = "monBwctl";

    static public String[] ResTypes = { "BwctlTCPBandwidth" };

    static public final String bwCtlCmd = AppConfig.getGlobalEnvProperty("MonaLisa_HOME") + "/Control/bin/bwctl -c ";

    static public final String OsName = "linux";

    //  boolean debug;

    public monBwctl() {
        super(ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
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

        String cmd = bwCtlCmd + " " + Node.getIPaddress();

        logger.log(Level.INFO, "monBwctl using cmd = " + cmd);

        BufferedReader buff = procOutput(cmd);

        if (buff == null) {
            throw new Exception(" ping output is null for " + Node.name);
        }

        Result result = ParseOutput(buff);
        cleanup();
        return result;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    Result ParseOutput(BufferedReader buff) throws Exception {
        Result retv = null;
        try {
            for (;;) {
                String lin = buff.readLine();

                if (lin == null) {
                    break;
                }
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "lin Out = " + lin);
                }
                if ((lin.indexOf("Unable to connect") != -1) || (lin.indexOf("Server denied") != -1)) {
                    return null;
                }

                //[ ID] Interval       Transfer     Bandwidth
                if ((lin.indexOf("Interval") != -1) && (lin.indexOf("Transfer") != -1)
                        && (lin.indexOf("Bandwidth") != -1)) {
                    lin = buff.readLine();
                    if (lin == null) {
                        break;
                    }
                    try {
                        String lastToken = null;
                        String cToken = null;
                        for (StringTokenizer st = new StringTokenizer(lin); st.hasMoreTokens();) {
                            cToken = st.nextToken();
                            if ((cToken != null) && (cToken.indexOf("bits/sec") != -1)) {
                                try {
                                    retv = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(),
                                            ModuleName, ResTypes);
                                    retv.time = NTPDate.currentTimeMillis();
                                    int index = retv.getIndex("BwctlTCPBandwidth");
                                    double lbw = Long.valueOf(lastToken).doubleValue();
                                    retv.param[index] = lbw / 1000000D;
                                    break;
                                } catch (Throwable t1) {
                                    logger.log(Level.WARNING, "monBwctl got Exc", t1);
                                    retv = null;
                                    break;
                                }
                            }
                            lastToken = cToken;
                        }
                    } catch (Throwable rr) {
                        logger.log(Level.WARNING, "monBwctl got Exception ", rr);
                        retv = null;
                        break;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            retv = null;
        }

        logger.log(Level.INFO, "monBwctl Returning result = " + retv);
        return retv;
    }

    static public void main(String[] args) {
        String host = "localhost";
        monBwctl aa = new monBwctl();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null, null);

        try {
            Object bb = aa.doProcess();
            System.out.println(bb);
        } catch (Exception e) {
            ;
        }
    }
}