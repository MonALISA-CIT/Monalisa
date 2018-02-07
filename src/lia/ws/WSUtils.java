package lia.ws;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;

/**
 * 
 * @author mickyt
 *
 */
public class WSUtils {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(WSUtils.class.getName());

    public static final WSFarm getWSFarmInstance(MFarm mFarm) {
        if (mFarm == null) {
            logger.log(Level.WARNING, " mfarm == null !!!!?");
            return null;
        }

        WSFarm wsf = new WSFarm();

        wsf.setFarmName(mFarm.name);
        WSCluster[] clusterList = new WSCluster[mFarm.getClusters().size()];

        for (int i = 0; i < mFarm.getClusters().size(); i++) {
            MCluster mc = mFarm.getClusters().elementAt(i);
            WSCluster wsc = new WSCluster();
            if (mc != null) {
                wsc.setClusterName(mc.name);
                WSNode[] nodeList = new WSNode[mc.getNodes().size()];
                for (int j = 0; j < mc.getNodes().size(); j++) {
                    MNode mn = mc.getNodes().elementAt(j);
                    WSNode wsn = new WSNode();
                    if (mn != null) {
                        wsn.setNodeName(mn.name);
                        String[] paramList = new String[mn.getParameterList().size()];
                        for (int k = 0; k < mn.getParameterList().size(); k++) {
                            String paramName = mn.getParameterList().elementAt(k);
                            paramList[k] = paramName;
                        }//for - params
                        wsn.setParamList(paramList);
                    }
                    nodeList[j] = wsn;
                }//for - nodes
                wsc.setNodeList(nodeList);
            }//if

            clusterList[i] = wsc;
        }//for clusters
        wsf.setClusterList(clusterList);
        return wsf;
    }
}
