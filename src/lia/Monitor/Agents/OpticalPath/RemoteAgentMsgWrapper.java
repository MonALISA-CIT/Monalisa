package lia.Monitor.Agents.OpticalPath;

import java.util.HashMap;
import java.util.regex.Pattern;

/**
 *
 * Helper class to work only inside the agent ... it is probably faster than
 * to send this class "over the wire" as a byte array.
 *--------------------------------
 *  0   :-      1     :-     2       :- 3 :- 4  :- 5
 * MCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl:-isFdx
 * DCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl:-idFdx
 * PDCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl
 * NCONF:-REQ|ACK|NACK:-newConf:-id:-idl
 * PDOWN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl
 * ADMINDEL:-REQ:-idl
 * LRENEW:-REQ:-idl
 * 
 */
class RemoteAgentMsgWrapper {
    
    private static final Pattern PATTERN_FIELDS = Pattern.compile(":-");
    private static final Pattern PATTERN_PORTS = Pattern.compile(" - ");
    
    public static final int    MCONN       =   0;
    public static final int    DCONN       =   1;
    public static final int    PDCONN      =   2;
    public static final int    NCONF       =   3;
    public static final int    PDOWN       =   4;
    public static final int    ADMINDEL    =   5;
    public static final int    LRENEW      =   6;
    
    private static final String[] REMOTE_CMDS = new String[] {
        "MCONN",
        "DCONN",
        "PDCONN",
        "NCONF",
        "PDOWN",
        "ADMINDEL",
        "LRENEW"
    };

    private static final HashMap REMOTE_CMDS_MAP = new HashMap();
    
    
    public static final int     REQ     =   0;
    public static final int     ACK     =   1;
    public static final int     NACK    =   2;
    
    private static final String[] REMOTE_OP_STATS = new String[] {
        "REQ",
        "ACK",
        "NACK"
    };

    private static final HashMap REMOTE_OP_STATS_MAP = new HashMap();
    
    static {
        for(int i = 0; i < REMOTE_CMDS.length; i++) {
            REMOTE_CMDS_MAP.put(REMOTE_CMDS[i], Integer.valueOf(i));
        }
        
        for(int i = 0; i < REMOTE_OP_STATS.length; i++) {
            REMOTE_OP_STATS_MAP.put(REMOTE_OP_STATS[i], Integer.valueOf(i));
        }
    }
    
    
    
    int remoteCMD = -1;//MCONN | DCONN 
    int remoteOpStat = -1; //REQ | ACK | NACK
    
    String unSplittedPorts;
    String sPort;
    String dPort;
    
    Long remoteCMD_ID;
    String session;

    String conf;
    boolean isFDX;
    
    String rawMSG;
    
    /**
     * only from String should be used! 
     */
    private RemoteAgentMsgWrapper() {
        
    }
    
    static final RemoteAgentMsgWrapper fromString(final String s) throws Exception {
        RemoteAgentMsgWrapper mlca = new RemoteAgentMsgWrapper();
        mlca.rawMSG = s;
        String fields[] = PATTERN_FIELDS.split(s);
        
        int remoteCMD       = ((Integer)REMOTE_CMDS_MAP.get(fields[0])).intValue(); 
        int remoteOpStat   = ((Integer)REMOTE_OP_STATS_MAP.get(fields[1])).intValue();
        
        if(remoteOpStat < 0 || remoteOpStat >= REMOTE_OP_STATS.length) {
            throw new Exception(" [ ProtocolException ] No Such remoteOpStat ( " + remoteOpStat + " ) for String: " + s);
        }
        
        mlca.remoteOpStat = remoteOpStat;
        
        switch(remoteCMD) {
            case MCONN: {//MCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl:-isFdx
                giveMe5(mlca, fields);
                break;
            }
            case DCONN: {//DCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl:-idFdx
                giveMe5(mlca, fields);
                break;
            }
            case PDCONN: {//PDCONN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl
                giveMe5(mlca, fields);
                break;
            }
            case NCONF: {//NCONF:-REQ|ACK|NACK:-newConf:-id:-idl
                mlca.conf = fields[2];
                mlca.remoteCMD_ID = Long.valueOf(fields[3]);
                mlca.session = fields[4];
                break;
            }
            case PDOWN: {//PDOWN:-REQ|ACK|NACK:-sPort - dPort:-id:-idl
                giveMe5(mlca, fields);
                break;
            }
            case ADMINDEL: {//ADMINDEL:-REQ|ACK|NACK:-idl
                mlca.session = fields[2];
                break;
            }
            case LRENEW: {//LRENEW:-REQ:-idl
                mlca.session = fields[2];
                break;
            }
            default: {
                throw new Exception(" [ ProtocolException ] No Such OP ( " + remoteCMD + " ) for String: " + s);
            }
        }
        
        mlca.remoteCMD = remoteCMD;
        
        return mlca;
    }
    
    // :) - hmm ... so ... you think you're smart ... 
    private static void giveMe5(RemoteAgentMsgWrapper mlca, final String[] fields) {
        mlca.unSplittedPorts = fields[2];
        if(mlca.remoteOpStat == REQ) {
            String[] ports = PATTERN_PORTS.split(mlca.unSplittedPorts);
            mlca.sPort = ports[0];
            mlca.dPort = ports[1];
        }
        mlca.remoteCMD_ID = Long.valueOf(fields[3]);
        mlca.session = fields[4];
        mlca.isFDX = (fields.length > 5 && fields[5].equals("1"))?true:false;
    }
    
    public static final String getDecodedRemoteCMD(int state) {
        return REMOTE_CMDS[state];
    }

    public static final String getDecodedRemoteOPStatus(int state) {
        return REMOTE_OP_STATS[state];
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }
}
