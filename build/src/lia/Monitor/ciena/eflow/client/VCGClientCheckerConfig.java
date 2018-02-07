/*
 * Created on Jul 9, 2012
 */
package lia.Monitor.ciena.eflow.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * @author ramiro
 */
public class VCGClientCheckerConfig {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VCGClientCheckerConfig.class.getName());

    private static final String CFG_PREFIX = "vcgcheckcfg";

    static final class CfgEntry {

        final String vcgName;

        final String[] eflowServiceNames;

        final String pingPredSpec;

        final String filterName;

        final String eflowName;

        final int pingLossThresholdCount;

        final double speedThreshold;

        final int speedThresholdCount;

        /**
         * @param vcgName
         * @param eflowServiceName
         * @param filterName
         * @param eflowName
         */
        CfgEntry(String vcgName,
                final String defaultEflowSeviceNames,
                String defaultFilterName,
                String defaultPingLossThresholdCount,
                String defaultSpeedThreshold,
                String defaultSpeedThresholdCount) {
            if (vcgName == null || vcgName.trim().isEmpty()) {
                throw new IllegalArgumentException("Null/Empty vcgName");
            }

            this.vcgName = vcgName.trim();

            final String cfgPrefix = CFG_PREFIX + "." + vcgName + ".";
            this.eflowServiceNames = AppConfig.getVectorProperty(cfgPrefix + "eflowServiceNames", defaultEflowSeviceNames);
            if(this.eflowServiceNames == null || eflowServiceNames.length == 0) {
                throw new IllegalArgumentException("Null/Empty property value: " + cfgPrefix + "eflowServiceNames");
            }
            
            this.pingPredSpec = getProperty(cfgPrefix + "pingPredSpec", null);
            this.filterName = getProperty(cfgPrefix + "filterName", defaultFilterName);
            this.eflowName = getProperty(cfgPrefix + "eflowName", null);

            this.pingLossThresholdCount = Integer.parseInt(getProperty(cfgPrefix + "pingLossThresholdCount", defaultPingLossThresholdCount));
            this.speedThreshold = Double.parseDouble(getProperty(cfgPrefix + "speedThreshold", defaultSpeedThreshold));
            this.speedThresholdCount = Integer.parseInt(getProperty(cfgPrefix + "speedThresholdCount", defaultSpeedThresholdCount));
        }

        private static final String getProperty(final String propertyName, final String defaultPropertyVaule) {
            final String valueProp = AppConfig.getProperty(propertyName, defaultPropertyVaule);

            if (valueProp == null || valueProp.trim().isEmpty()) {
                throw new IllegalArgumentException("Null/Empty property value: " + propertyName);
            }

            return valueProp.trim();
        }

        /*
         * (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Entry [vcgName=")
                   .append(vcgName)
                   .append(", eflowServiceNames=")
                   .append(Arrays.toString(eflowServiceNames))
                   .append(", pingPredSpec=")
                   .append(pingPredSpec)
                   .append(", filterName=")
                   .append(filterName)
                   .append(", eflowName=")
                   .append(eflowName)
                   .append(", pingLossThresholdCount=")
                   .append(pingLossThresholdCount)
                   .append(", speedThreshold=")
                   .append(speedThreshold)
                   .append(", speedThresholdCount=")
                   .append(speedThresholdCount)
                   .append("]");
            return builder.toString();
        }

    }

    /**
     * K - vcgName, value - config for the entry
     */
    private final HashMap<String, CfgEntry> configMap = new HashMap<String, CfgEntry>();
    private final String mailFrom;
    private final String[] mailRCPTs;
    private final long lockUnlockDelayNanos;
    
    /**
     * 
     */
    private VCGClientCheckerConfig() {
        try {
            final StringBuilder sb = new StringBuilder();
            sb.append("\nVCGClientCheckerConfig - Default values for all VCGs:\n");

            final String mailFrom = getProp(CFG_PREFIX + ".mailFrom");
            if(mailFrom == null) {
            	throw new IllegalArgumentException(CFG_PREFIX + ".mailFrom property undefined" );
            }
            this.mailFrom = mailFrom;
            sb.append("\nmailFrom=").append(this.mailFrom);
            
            final String[] rcpts = AppConfig.getVectorProperty(CFG_PREFIX + ".mailRCPTs");
            if(rcpts == null || rcpts.length == 0) {
            	throw new IllegalArgumentException(CFG_PREFIX + ".mailRCPTs property undefined" );
            }
            this.mailRCPTs = rcpts;
            sb.append("\nmailRCPTs=").append(Arrays.toString(this.mailRCPTs));

            this.lockUnlockDelayNanos = TimeUnit.SECONDS.toNanos(Long.parseLong(getProp(CFG_PREFIX + ".lockUnlockDelaySeconds")));
            sb.append("\nlockUnlockDelaySeconds=").append(TimeUnit.NANOSECONDS.toSeconds(lockUnlockDelayNanos));
            
            final String defaultEflowServiceNames = getProp(CFG_PREFIX + ".eflowServiceNames");
            sb.append("\ndefaultEflowServiceNames=").append(defaultEflowServiceNames);

            final String defaultFilterName = getProp(CFG_PREFIX + ".filterName");
            sb.append("\ndefaultFilterName=").append(defaultFilterName);

            final String defaultPingLossThresholdCount = getProp(CFG_PREFIX + ".pingLossThresholdCount");
            sb.append("\ndefaultPingLossThresholdCount=").append(defaultPingLossThresholdCount);

            final String defaultSpeedThresholdCount = getProp(CFG_PREFIX + ".speedThresholdCount");
            sb.append("\ndefaultSpeedThresholdCount=").append(defaultSpeedThresholdCount);

            final String defaultSpeedThreshold = getProp(CFG_PREFIX + ".speedThreshold");
            sb.append("\ndefaultSpeedThreshold=").append(defaultSpeedThreshold);

            final String[] vcgNames = AppConfig.getVectorProperty(CFG_PREFIX + ".VCGS");
            if (vcgNames == null || vcgNames.length == 0) {
                throw new IllegalArgumentException("No VCGs defined");
            }

            sb.append("\nVCGClientCheckerConfig - VCGs Configs:\n");

            for (final String vcgName : vcgNames) {
                final CfgEntry entry = new CfgEntry(vcgName,
                                              defaultEflowServiceNames,
                                              defaultFilterName,
                                              defaultPingLossThresholdCount,
                                              defaultSpeedThreshold,
                                              defaultSpeedThresholdCount);
                configMap.put(vcgName, entry);
                sb.append("\nvcgName=").append(vcgName).append(" - ").append(entry);
            }

            logger.log(Level.INFO, "reloadConf() " + sb.toString());

            if (configMap.isEmpty()) {
                throw new IllegalArgumentException("No VCGs defined");
            }
        } catch (Throwable t) {
            throw new IllegalArgumentException("Unable to load VCGClientCheckerConfig. Cause:", t);
        }
    }

    public static VCGClientCheckerConfig newInstance() {
        final VCGClientCheckerConfig ret = new VCGClientCheckerConfig();
        //TODO - Listen for AppConfig changes ?
        return ret;
    }

    private final String getProp(final String propName) {
        final String propVal = AppConfig.getProperty(propName);
        return (propVal != null && !propVal.trim().isEmpty()) ? propVal.trim() : null;
    }

    /**
     * @return a read-only view of the VCG config
     */
    public Map<String, CfgEntry> getConfigMap() {
        return Collections.unmodifiableMap(configMap); 
    }
}
