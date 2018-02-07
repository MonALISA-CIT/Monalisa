import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

public class Bwctl extends cmdExec implements MonitoringModule {

    static public String ModuleName = "Bwctl";

    static public String[] ResTypes = { "BwctlTCPBandwidth"};

    static public final String bwCtlCmd = AppConfig.getProperty("MonaLisa_HOME","../..")+"/Control/bin/bwctl -c ";

    static public final String OsName = "linux";

    //  boolean debug;

    public Bwctl() {
        super(ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
    }

    public String[] ResTypes() {
        return ResTypes;
    }

    public String getOsName() {
        return OsName;
    }

    public Object doProcess() throws Exception {

        String cmd = bwCtlCmd + " " + Node.getIPaddress();
        
        System.out.println("Bwctl using cmd = " + cmd);
        
        BufferedReader buff = procOutput(cmd);

        if (buff == null) { throw new Exception(" ping output is null for " + Node.name); }

        Result result = ParseOutput(buff);
        cleanup();
        return result;
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    Result ParseOutput(BufferedReader buff) throws Exception {
        Result retv = null;
        try {
            for (;;) {
                String lin = buff.readLine();

                if (lin == null) break;
                if (lin.indexOf("Unable to connect") != -1 || lin.indexOf("Server denied") != -1 ) {
                    return null;
                }
                
                //[ ID] Interval       Transfer     Bandwidth
                if (lin.indexOf("Interval") != -1 && lin.indexOf("Transfer") != -1 && lin.indexOf("Bandwidth") != -1){
                    lin = buff.readLine();
                    if (lin == null) break;
                    try {
                        String lastToken = null;
                        String cToken = null;
                        for (StringTokenizer st = new StringTokenizer(lin);st.hasMoreTokens();) {
                            cToken=st.nextToken();
                            if ( cToken != null && cToken.indexOf("bits/sec") != -1) {
                                try {
                                    retv = new Result ( Node.getFarmName(),Node.getClusterName(),Node.getName(), ModuleName, ResTypes );
                                    retv.time = System.currentTimeMillis();
                                    int index = retv.getIndex("BwctlTCPBandwidth");
                                    double lbw = Long.valueOf(lastToken).doubleValue();
                                    retv.param[index] = lbw/1000000D;
                                    break;
                                }catch(Throwable t1) {
                                    System.out.println("Bwctl got Exc");
				    t1.printStackTrace();
                                    retv = null;
                                    break;
                                }
                            }
                            lastToken = cToken;
                        }
                    } catch(Throwable rr){
                        System.out.println("monBwctl got Exception ");
			rr.printStackTrace();
                        retv = null;
                        break;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            retv = null;
        }
        
        System.out.println("Bwctl Returning result = " + retv);
        return retv;
    }

    static public void main(String[] args) {
        String host = args[0];
        Bwctl aa = new Bwctl();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), null, null);

        try {
            Object bb = aa.doProcess();
            System.out.println((Result) bb);
        } catch (Exception e) {
            ;
        }
    }
}
