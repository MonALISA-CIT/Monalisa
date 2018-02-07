/*
 * Created on Jul 18, 2012
 */
package lia.Monitor.ciena.eflow.client;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.eflow.client.VCGClientCheckerConfig.CfgEntry;
import lia.util.Utils;

/**
 * @author ramiro
 */
public class MainVCGMLClient {

    private static final Logger logger = Logger.getLogger(MainVCGMLClient.class.getName());

    /**
     * 
     */
    public MainVCGMLClient() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            final VCGClientCheckerConfig config = VCGClientCheckerConfig.newInstance();
            final SimpleMLClient<BasicServiceNode> simpleMLClient = new SimpleMLClient<BasicServiceNode>(BasicServiceNodeFactory.newInstance());
            final Map<String, CfgEntry> configMap = config.getConfigMap();
            for (Iterator<Map.Entry<String, CfgEntry>> iterator = configMap.entrySet().iterator(); iterator.hasNext();) {
                final Map.Entry<String, CfgEntry> entry = iterator.next();
                final String vcgName = entry.getKey();
                final CfgEntry cfg = entry.getValue();

            }
            simpleMLClient.init();
            if (Utils.waitUntilInterruptedAndSwallowException()) {
                logger.log(Level.INFO, "[SimpleMainClient] [MAIN] Main thread interrupted. Will exit");
                System.exit(0);
            } else {
                logger.log(Level.INFO, "[SimpleMainClient] [MAIN] Main thread NOT interrupted but returned from wait(). Will exit");
                System.exit(-254);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[SimpleMainClient] [MAIN] Got Exception. Will exit. Cause:", t);
            System.exit(-253);
        }
    }

}
