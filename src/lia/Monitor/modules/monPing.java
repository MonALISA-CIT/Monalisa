package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

public class monPing extends cmdExec implements MonitoringModule {

    /**
     * @since ML 1.5.4
     */
    private static final long serialVersionUID = 2969789031029788445L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monPing.class.getName());

    static public String ModuleName = "monPing";

    static public String[] ResTypes = {"LostPackages", "RTT"};

    static public final String PING_CMD = AppConfig.getProperty("lia.Monitor.modules.monPing.PING_CMD", "ping -q -c 1 -s 1400 -W 4");

    static public final String OsName = "linux";
    
    private static final Pattern COMMA_PATTERN = Pattern.compile("(\\s)*,(\\s)*");
    // boolean debug;

    private volatile String cmd;
    
    public monPing() {
        super(ModuleName);
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
        canSuspend = false;
    }

    public String[] ResTypes() {
        return ResTypes;
    }

    public String getOsName() {
        return OsName;
    }

    public Object doProcess() throws Exception {

        
        try {
            
            if(cmd == null) {
                cmd = PING_CMD + " " + Node.getIPaddress();
            }
            
            if(logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ monPing ] Executing cmd: " + cmd);
            }
            
            final BufferedReader buff = procOutput(cmd);

            if (buff == null) {
                throw new Exception(" ping output2 is null for " + Node.name);
            }
            
            double[] rez = ParsePingOut(buff);

            Result result = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName);
            result.time = NTPDate.currentTimeMillis();
            
            if(rez[0] >= 0) {
                result.addSet("LostPackages", rez[0]);
            }
            
            if(rez[1] >= 0) {
                result.addSet("RTT", rez[1]);
            }

            return result;
        } catch(Throwable t) {
            logger.log(Level.WARNING, " [ monPing ] Exception processing command output", t);
            throw new Exception(t);
        } finally {
            cleanup();
        }
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    double[] ParsePingOut(BufferedReader buff) throws Exception {
        String lost = null;
        String mean = null;
        double[] rez = new double[2];

        final StringBuilder sb = new StringBuilder(8192);
        
        try {
            for (;;) {
                String lin = buff.readLine();
                
                if (lin == null) break;
                sb.append(lin).append("\n");
                
                if (lin.indexOf("min/avg") != -1) {
                    int i1 = lin.indexOf("=");
                    int i2 = lin.indexOf("/", i1);
                    int i3 = lin.indexOf("/", i2 + 1);
                    mean = lin.substring(i2 + 1, i3);
                    continue;
                }
                if (lin.indexOf("packet loss") != -1) {
                    String[] tks = COMMA_PATTERN.split(lin);
                    if(tks != null) {
                        for(int i=0; i<tks.length; i++) {
                            int idx = tks[i].indexOf("%");
                            if(idx > 0) {
                                lost = tks[i].substring(0, idx);
                                break;
                            }
                        }//for
                    }//if(tks != null)
                }
            }
        } catch (Throwable t) {
            throw new Exception("Exception reading / parsing ping output. Cause: " + t.getCause() + "\n Output:\n" + sb.toString());
        }

        if (lost != null) {
            rez[0] = Double.parseDouble(lost);
        } else {
            rez[0] = -1;
        }
        
        if (mean != null) {
            rez[1] = Double.parseDouble(mean);
        } else {
            rez[1] = -1;
        }


        if(rez[0] > 0) {
            if(rez[0] < 100) {
                rez[0] = 100;
            }
            logger.log(Level.INFO, " monPing. cmd: " + cmd + ", lost pkts: " + rez[0] + ", Output: \n " + sb.toString());
        }
        
        return rez;
    }

    static public void main(String[] args) throws Exception {
        
        if(args == null || args.length == 0) {
            System.err.println(" Please specify a hostname as parameter");
            System.exit(1);
        }

        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream((
                "handlers= java.util.logging.ConsoleHandler\n" + 
                "java.util.logging.ConsoleHandler.level = FINEST\n" + 
                "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + 
                ".level = INFO\n" 
                ).getBytes()));
        logger.setLevel(Level.FINEST);

        String host = args[0];
        monPing aa = new monPing();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        aa.init(new MNode(args[0], ad, null, null), null, null);

        for(;;) {
            try {
                Thread.sleep(5 * 1000);
                System.out.println(aa.doProcess());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Got exception in monPing", t);
            }
        }
    }
}