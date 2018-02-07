package lia.Monitor.modules;

import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.vrvsTcpCmd;
import lia.util.ntp.NTPDate;

public class vrvsClientsT extends vrvsTcpCmd implements MonitoringModule {
    /**
     * 
     */
    private static final long serialVersionUID = -5731919529989597840L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(vrvsClientsT.class.getName());

    static String[] tmetric = { "VirtualRooms", "Audio", "Video" };
    //,  "MBONE","H323" };

    String cmd;
    String Args;
    String base;
    //String xarg = "vrvs/admin_checkhost/all";
    String xarg = "vrvs/admin_checkhost/all";

    int no_vrvsRoom = 0;
    int no_video = 0;
    int no_audio = 0;
    int no_mbone = 0;
    int no_h323 = 0;

    public vrvsClientsT() {
        super("vrvsClients");
        info.ResTypes = tmetric;
        isRepetitive = true;
        canSuspend = false;
        base = AppConfig.getProperty("MonaLisa.VRVS_HOME", "../../");
        cmd = base + "/bin/vrvs_cmd_LE -m " + xarg;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        super.init(Node, arg);
        this.Node = Node;
        info.ResTypes = tmetric;
        base = AppConfig.getProperty("MonaLisa_HOME", "../../");
        cmd = base + "/bin/vrvs_cmd_LE -m " + xarg;

        try {
            BufferedReader buff2 = procOutput(cmd);
            buff2.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, " Failed  to get perform the init cmd");
        }

        return info;
    }

    @Override
    public Object doProcess() throws Exception {
        //    BufferedReader buff1 = procOutput ( cmd );
        BufferedReader buff1 = procOutput(xarg);

        if (buff1 == null) {
            logger.log(Level.WARNING, " Failed  to get the vrvsClient  output ");
            throw new Exception(" vrvsClient output  is null for " + Node.name);
        }

        Result r = Parse(buff1);

        return r;
    }

    public Result Parse(BufferedReader buff) throws Exception {

        no_vrvsRoom = 0;
        no_video = 0;
        no_audio = 0;
        no_mbone = 0;
        no_h323 = 0;
        String lin;

        Result rr = null;

        for (;;) {
            lin = buff.readLine();
            if (lin == null) {
                break;
            }
            if (lin.length() > 4) {
                if (lin.startsWith("video/")) {
                    no_video++;
                } else if (lin.startsWith("audio/")) {
                    no_audio++;
                } else {
                    if (lin.indexOf("/") != -1) {
                        no_vrvsRoom++;
                    }
                }
            }
        }

        rr = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), "vrvsClient", tmetric);

        rr.param[0] = no_vrvsRoom;
        rr.param[1] = no_audio;
        rr.param[2] = no_video;
        rr.time = NTPDate.currentTimeMillis();

        buff.close();
        return rr;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return tmetric;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    static public void main(String[] args) {

        vrvsClients aa = new vrvsClients();
        String ad = null;
        String host = "localhost";
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        try {

            Object bb = aa.doProcess();

        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }

    }

}
