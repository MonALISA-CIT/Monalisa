package lia.util.logging.comm;

import java.util.Properties;

import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;


public class SerMLLogMsg extends MLLogMsg {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = -1677445033192013092L;

    //this will be filled from the environment
    public String ServiceName;
   
    //has a meaning only for ML Services and ML Proxies
    public String ServiceID;

    public Properties localAppProps;

    public SerMLLogMsg(){
        super();
        localAppProps = AppConfig.getPropertiesConfigApp();
        ServiceName = System.getProperty("MonALISA_ServiceName");
        ServiceID = System.getProperty("MonALISA_ServiceID");
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(8192);
        try {
            sb.append(" ServiceName: ").append(ServiceName);
            sb.append(" ServiceID: ").append(ServiceID).append("\n");
            sb.append("localAppProps:\n").append(localAppProps).append("\n\n");
            sb.append(super.toString());
        } catch(Throwable t) {
            sb.append("Got exc in SerMLLogMsg.toString()").append(Utils.getStackTrace(t)).append("\n");
        }
        return sb.toString();
    }
}
