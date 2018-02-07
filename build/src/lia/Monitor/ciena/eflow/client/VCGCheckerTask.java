/*
 * Created on Jul 9, 2012
 */
package lia.Monitor.ciena.eflow.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.ciena.eflow.EFlowStatsSer;
import lia.Monitor.ciena.eflow.client.VCGClientCheckerConfig.CfgEntry;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.util.Utils;
import lia.web.utils.Formatare;

/**
 * @author ramiro
 */
class VCGCheckerTask implements Runnable {

    private static final Logger logger = Logger.getLogger(VCGCheckerTask.class.getName());

    /**
     * Data from service filter for EFLOWS
     * 
     * @author ramiro
     */
    private static final class VCGFilterReceiver implements LocalDataFarmClient {

        final VCGCheckerTask master;

        final String myName;

        VCGFilterReceiver(final VCGCheckerTask master) {
            this.master = master;
            this.myName = "[VCGFilterReceiver :- " + master.filterName + " :- " + master.eflowServiceNamesSet + " ]";
        }

        /**
         * @param client
         */
        @Override
        public void newFarmResult(MLSerClient client, Object res) {
            try {
                final byte[] cObjs = byte[].class.cast(res);
                final EFlowStatsSer[] arr = Utils.readCompressedObject(cObjs, EFlowStatsSer[].class);
                System.out.println(Arrays.toString(arr));
            } catch (Throwable t) {
                logger.log(Level.WARNING, myName + " unable to decompress result. Cause:", t);
            }
        }

    }

    /**
     * Receives data from MLPing service
     * 
     * @author ramiro
     */
    private static final class MLPingResultReceiver implements LocalDataFarmClient {

        @Override
        public void newFarmResult(MLSerClient client, Object res) {

        }

    }

    final Map<String, LocalDataFarmClient> getFiltersMap(final String serviceName) {
        if (serviceName != null && serviceName.equals(serviceName)) {
            final HashMap<String, LocalDataFarmClient> retMap = new HashMap<String, LocalDataFarmClient>();
            retMap.put(filterName, filterReceiver);
            return retMap;
        }
        return Collections.emptyMap();
    }

    final Map<monPredicate, LocalDataFarmClient> getPredicatesMap(final String serviceName) {
        if (serviceName != null && serviceName.equals(serviceName)) {
            final HashMap<monPredicate, LocalDataFarmClient> retMap = new HashMap<monPredicate, LocalDataFarmClient>();
            retMap.put(predicate, filterReceiver);
            return retMap;
        }
        return Collections.emptyMap();
    }

    final String vcgName;

    final Set<String> eflowServiceNamesSet;

    final String filterName;

    final monPredicate predicate;

    final HashMap<String, VCGFilterReceiver> filterMap;

    final VCGFilterReceiver filterReceiver;

    /**
     * 
     */
    public VCGCheckerTask(final String vcgName, final CfgEntry configEntry) {
        this.vcgName = vcgName;
        this.eflowServiceNamesSet = new HashSet<String>(Arrays.asList(configEntry.eflowServiceNames));
        this.filterName = configEntry.filterName;
        this.predicate = Formatare.toPred(configEntry.pingPredSpec);
        filterReceiver = new VCGFilterReceiver(this);
        filterMap = new HashMap<String, VCGFilterReceiver>();
        filterMap.put(filterName, filterReceiver);
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub

    }

}
