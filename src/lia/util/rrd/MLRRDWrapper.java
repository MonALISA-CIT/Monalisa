/*
 * Created on Aug 23, 2010
 */
package lia.util.rrd;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.UnsignedLong;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;

import org.uslhcnet.rrd.DSValue;
import org.uslhcnet.rrd.RRDFile;
import org.uslhcnet.rrd.config.DSConfig;

/**
 * 
 * @author ramiro
 */
public class MLRRDWrapper {
    private static final Logger logger = Logger.getLogger(MLRRDWrapper.class.getName());
    private static final char SEPARATOR = '_';

    private static final RRDDataReceiverConfigMgr cfgMgr = RRDDataReceiverConfigMgr.getInstance();

    private static final ConcurrentMap<MLRRDKey<String>, MLRRDWrapper> rrdWrapperMap = new ConcurrentHashMap<MLRRDKey<String>, MLRRDWrapper>();
    private final MLRRDKey<String> key;
    private final RRDFile rrdFile;
    private final MLRRDConfigEntry mlRRDConfig;

    private final ConcurrentMap<String, DSConfig> mlParamRRDDSMap = new ConcurrentHashMap<String, DSConfig>();

    //    private final MLRRDConfigEntry mlRRDConfigEntry;

    private MLRRDWrapper(MLRRDKey<String> key, Result r) throws IOException {
        this.key = key;
        final String rrdFileName = key.getKey();
        final List<MLRRDConfigEntry> mlRRDConfs = cfgMgr.getMLMappingsConfigs();
        for (MLRRDConfigEntry mlRRDCfgEntry : mlRRDConfs) {
            final List<monPredicate> predList = mlRRDCfgEntry.getPredicatesList();
            for (final monPredicate p : predList) {
                if (DataSelect.matchResult(r, p) != null) {
                    //we got a winner
                    this.rrdFile = new RRDFile(rrdFileName, mlRRDCfgEntry.getRrdConfig());
                    this.mlRRDConfig = mlRRDCfgEntry;
                    return;
                }
            }
        }

        throw new IllegalArgumentException("Unable to match any RRD confs for Result: " + r);
    }

    private static final MLRRDWrapper getInstance(Result r) throws IOException {
        MLRRDKey<String> key = generateKey(r);
        if (key == null) {
            logger.log(Level.WARNING, " [ RRDDataReceiver ] Null key for result:\n\t" + r);
            return null;
        }
        final MLRRDWrapper cachedRRDWrapper = rrdWrapperMap.get(key);
        if (cachedRRDWrapper == null) {
            rrdWrapperMap.putIfAbsent(key, new MLRRDWrapper(key, r));
        }

        return rrdWrapperMap.get(key);
    }

    public static void updateResult(Result r) throws IOException {
        MLRRDWrapper mlRRDWrapper = MLRRDWrapper.getInstance(r);
        if (mlRRDWrapper != null) {
            mlRRDWrapper.internalUpdate(r);
        }
    }

    private void internalUpdate(Result r) throws IOException {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ MLRRDWrapper ] " + key + " [ internalUpdate ] " + r);
        }

        final List<DSValue<UnsignedLong>> valuesToAdd = new LinkedList<DSValue<UnsignedLong>>();
        final long timestamp = (r.time + 500L) / 1000L;
        int it = 0;
        for (final String paramName : r.param_name) {
            DSConfig ds = null;
            final DSConfig dsCache = mlParamRRDDSMap.get(paramName);
            if (dsCache == null) {
                //get the DS
                for (Map.Entry<String, DSConfig> entry : mlRRDConfig.dsMap().entrySet()) {
                    if (paramName.endsWith(entry.getKey())) {
                        mlParamRRDDSMap.putIfAbsent(paramName, entry.getValue());
                        break;
                    }
                }
                ds = mlParamRRDDSMap.get(paramName);
            } else {
                ds = dsCache;
            }

            if (ds == null) {
                logger.log(Level.WARNING, " [ MLRRDWrapper ] [ internalUpdate ] ignoring param " + paramName
                        + " from result: " + r);
            } else {
                final DSValue<UnsignedLong> dsValAdd = new DSValue<UnsignedLong>(timestamp, UnsignedLong.valueOf(Double
                        .doubleToRawLongBits(r.param[it])), ds);
                valuesToAdd.add(dsValAdd);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ MLRRDWrapper ] mapping param " + paramName + " -> " + dsValAdd
                            + " from ML Result " + r);
                }
            }

            it++;
        }

        rrdFile.addValues(timestamp, valuesToAdd);
    }

    private static final MLRRDKey<String> generateKey(Result r) {

        StringBuilder sb = new StringBuilder();
        if ((r.FarmName == null) || (r.ClusterName == null) || (r.NodeName == null)) {
            return null;
        }
        sb.append(r.FarmName).append(SEPARATOR).append(r.ClusterName).append(SEPARATOR).append(r.NodeName);
        final String[] params = Arrays.copyOf(r.param_name, r.param_name.length);
        Arrays.sort(params);
        for (final String paramName : params) {
            if (paramName == null) {
                return null;
            }
            sb.append(SEPARATOR).append(paramName);
        }

        return new MLRRDKey<String>(StringFactory.get(sb.toString()));
    }

}
