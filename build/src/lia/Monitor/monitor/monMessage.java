/*
 * $Id: monMessage.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.monitor;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 *
 * @since FOREVER
 * 
 */
public class monMessage implements java.io.Serializable {

    /**
     * it should be the same since first release of MonALISA ( FLAMES ) :) 
     * added @ ML 1.2.54
     */
    private static final long serialVersionUID = -8144568954323050463L;

    public final String tag;

    public final Object ident;

    public final Object result;

    public static final String PREDICATE_REGISTER_TAG           =   "register";
    public static final String PREDICATE_UNREGISTER_TAG         =   "unregister";
    public static final String FILTER_REGISTER_TAG              =   "fregister";
    public static final String FILTER_UNREGISTER_TAG            =   "funregister";
    public static final String ML_TIME_TAG                      =   "time";
    public static final String ML_VERSION_TAG                   =   "MLVersion";
    public static final String ML_CONFIG_TAG                    =   "config";
    public static final String ML_APP_CTRL_TAG                  =   "app_ctrl";
    public static final String ML_AGENT_TAG                     =   "agent";
    public static final String ML_AGENT_CTRL_TAG                =   "agentCtrl";
    public static final String ML_AGENT_ERR_TAG                 =   "agents:error";
    public static final String ML_RESULT_TAG                    =   "rez";
    public static final String ML_SID_TAG                       =   "FARM_SID";
    
    public static final String PROXY_MLSERVICES_TAG             =   "farms";
    
    public monMessage(final String tag, Object ident, Object result) {
        this.tag = tag;
        this.ident = ident;
        this.result = result;
    }
    
     public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(" tag=").append(this.tag); 
        sb.append("; ident=").append(this.ident);
        sb.append("; result=").append(this.result);
        
        return sb.toString();
    }
}
