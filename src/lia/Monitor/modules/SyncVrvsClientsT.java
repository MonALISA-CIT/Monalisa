/*
 * $Id: SyncVrvsClientsT.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.modules;
import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AttributePublisher;
import lia.Monitor.monitor.MLAttributePublishers;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * 
 * @author ramiro
 * 
 */
public class SyncVrvsClientsT extends SyncVrvsTcpCmd implements MonitoringModule {


    private static final long serialVersionUID = 6283496392967953130L;


    /** The Logger */ 
    private static final Logger logger = Logger.getLogger(SyncVrvsClientsT.class.getName());

    private static final AttributePublisher publisher = MLAttributePublishers.getInstance();

    static String[] tmetric = { "VirtualRooms", "Audio", "Video" };
    //,  "MBONE","H323" };

    private static String cmd_MGR = "vrvs/admin_listvr/end";

    //TODO
    //this cmd has also an interestring output
//  private static String cmd_VR = "vrvs/admin_checkhost/all";
    private static String cmd_VR = "vrvs/check_host/all";

    int mgr_port = -1;

    int no_vrvsRoom = 0;
    int no_video = 0;
    int no_audio = 0;
    int no_mbone = 0;
    int no_h323 = 0;
    int no_clients = 0;

    public SyncVrvsClientsT() {
        super("SyncVrvsClientsT");
        info.ResTypes = tmetric;
        isRepetitive = true;
        canSuspend = false;
        mgr_port =
            Integer
            .valueOf(
                     AppConfig.getProperty("lia.Monitor.VRVS_port", "46011"))
                     .intValue();
//      debug = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false")).booleanValue();
        initPublishTimer();
    }

    private void initPublishTimer(){
        final Runnable ttAttribUpdate = new Runnable() {
            public void run() {
                try {
                    publisher.publish("VRVSClients", Integer.valueOf(no_clients));
                }catch(Throwable t){
                    logger.log(Level.WARNING, "SyncVrvsClientsT LUS Publisher: Got Exception", t);
                }
            }
        };

        MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(ttAttribUpdate, 40, 2 * 60, TimeUnit.SECONDS);
        logger.log(Level.INFO, "SyncVRVSClientsT Attributes Update thread scheduled");
    }

    public MonModuleInfo init(MNode Node, String arg) {
        super.init(Node, arg);
        this.Node = Node;
        info.ResTypes = tmetric;

        try {
            BufferedReader buff2 = procOutput(mgr_port, cmd_MGR);
            buff2.close();
        } catch ( Throwable t ) {
            logger.log(Level.SEVERE, " Failed  to get perform the init cmd", t);
        }

        return info;
    }

    public Object doProcess() throws Exception {
//      long now = NTPDate.currentTimeMillis();
        BufferedReader buff= procOutput(mgr_port, cmd_MGR);

        if (buff == null) {
            throw new Exception(
                                " SyncVrvsClientsT output  is null for " + Node.name);
        }

        Result r = Parse(buff);
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

            //Looking for vr_num/port_video:port_audio/[ON|OFF]:[ON|OFF]/ 
            if (lin.indexOf("/") != -1) {
                StringTokenizer st = new StringTokenizer(lin, "/:");
                int VIDEO_PORT  = -1;
                int AUDIO_PORT  = -1;
                int ROOM_NO = -1;

                if (st.hasMoreTokens()) { //VR_NUM
                    ROOM_NO = Integer.parseInt(st.nextToken());

                    if (st.hasMoreTokens()) { //VIDEO_PORT
                        VIDEO_PORT = Integer.parseInt(st.nextToken());

                        if (st.hasMoreTokens()) { //AUDIO_PORT
                            AUDIO_PORT = Integer.parseInt(st.nextToken());
                            no_vrvsRoom++;
                        }
                    }
                }

                if ( logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "VR: " + ROOM_NO + "AudioP: " + AUDIO_PORT + "VideoP: " + VIDEO_PORT);
                }				

                //video clients
                int no_video1 = -1;
                BufferedReader buffV =  procOutput(VIDEO_PORT, cmd_VR);
                if ( buffV == null ) {
                    logger.log(Level.WARNING, " Video Buff == null for VR: " + ROOM_NO);
                } else {
                    no_video1 = ParseVideo(buffV); 
                }

                //TODO
                //throw an Exception here ???
                if ( no_video == -1 ) {
                    logger.log(Level.WARNING, " ===? Error processing no_video for VR: " + ROOM_NO);
                    //TODO
                    no_video1 = 0;
                }
                no_video += no_video1;

                //audio clients
                int no_audio1 = -1;
                BufferedReader buffA =  procOutput(AUDIO_PORT, cmd_VR);
                if ( buffA == null ) {
                    logger.log(Level.WARNING, " Audio Buff == null for VR: " + ROOM_NO);
                } else {
                    no_audio1 = ParseAudio(buffA); 
                }

                //TODO
                //throw an Exception here ???
                if ( no_audio1 == -1 ) {
                    logger.log(Level.WARNING, " ===? Error processing no_audio for VR: " + ROOM_NO);
                    //TODO
                    no_audio1 = 0;
                }
                no_audio += no_audio1;

            }
        } //for

        rr =
            new Result(
                       Node.getFarmName(),
                       Node.getClusterName(),
                       Node.getName(),
                       "vrvsClient",
                       tmetric);

        rr.param[0] = (double) no_vrvsRoom;
        rr.param[1] = (double) no_audio;
        rr.param[2] = (double) no_video;
        rr.time = NTPDate.currentTimeMillis();
        no_clients = no_audio + no_video;

        buff.close();
        return rr;
    }

    //for now same output --- but may change in the Future ?!?	
    private int ParseAudio( BufferedReader buff ) {
        return ParseAV(buff);	
    }

    private int ParseVideo( BufferedReader buff ) {
        return ParseAV(buff);	
    }

    private int ParseAV( BufferedReader buff ){
        String lin = null;
        int retV = 0;

        try {

            for (;;) {
                lin = buff.readLine();
                if ( lin == null )
                    break;
                if ( lin.indexOf("HOST") != -1 ) 
                    retV++;
            }//for(;;)

            buff.close();
        } catch ( Throwable t ) {
            logger.log(Level.WARNING, "Exception processing buffer", t);
            retV = -1;
        }

        if (logger.isLoggable(Level.FINEST) ) {
            logger.log(Level.FINEST, "retV: " + retV);
        }
        return retV;
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
