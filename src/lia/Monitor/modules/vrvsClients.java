package lia.Monitor.modules;
import java.io.BufferedReader;
import java.net.InetAddress;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

public class vrvsClients extends cmdExec implements MonitoringModule {

    static String[] tmetric = { "VirtualRooms", "Audio", "Video" };
    //,  "MBONE","H323" };

    String cmd;
    String Args;
    String base;
    String xarg = " vrvs/admin_checkhost/all ";

    int no_vrvsRoom = 0;
    int no_video = 0;
    int no_audio = 0;
    int no_mbone = 0;
    int no_h323 = 0;

    public vrvsClients() {
        super("vrvsClients");
        info.ResTypes = tmetric;
        isRepetitive = true;
        base = AppConfig.getProperty("MonaLisa.VRVS_HOME", "../..");
        cmd = base + "/bin/vrvs_cmd_LE -m " + xarg;
    }

    public Object doProcess() throws Exception {
        BufferedReader buff1 = procOutput(cmd);

        if (buff1 == null) {
            cleanup();
            throw new Exception(" vrvsClient output  is null for " + Node.name);
        }

        Result r = Parse(buff1);
        cleanup();

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
            if (lin == null)
                break;
            if (lin.length() > 4) {
                if (lin.startsWith("video/")) {
                    no_video++;
                } else if (lin.startsWith("audio/")) {
                    no_audio++;
                } else {
                    if (lin.indexOf("/") != -1)
                        no_vrvsRoom++;
                }
            }
        }

        rr =
            new Result(
                Node.getFarmName(),
                Node.getClusterName(),
                Node.getName(),
                "vrvsClient",
                tmetric);

        rr.param[0] = (double)no_vrvsRoom;
        rr.param[1] = (double)no_audio;
        rr.param[2] = (double)no_video;
        rr.time = NTPDate.currentTimeMillis();


        buff.close();
        return rr;
    }

    public MonModuleInfo getInfo() {
        return info;
    }
    public String[] ResTypes() {
        return tmetric;
    }
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
