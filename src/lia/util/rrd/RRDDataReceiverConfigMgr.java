/*
 * Created on Aug 19, 2010 
 */
package lia.util.rrd;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.monPredicate;
import lia.util.DateFileWatchdog;

import org.uslhcnet.rrd.config.DSConfig;
import org.uslhcnet.rrd.config.RRDConfig;
import org.uslhcnet.rrd.config.RRDConfigManager;

/**
 * @author ramiro
 */
public class RRDDataReceiverConfigMgr implements Observer {

    private static final Logger logger = Logger.getLogger(RRDDataReceiverConfigMgr.class.getName());

    private static final class InstanceHolder {
        private static final RRDDataReceiverConfigMgr theInstance;

        static {
            final String cfgFile = AppConfig.getProperty("RRDConfigManager.config");
            RRDDataReceiverConfigMgr cfg = null;
            if (cfgFile == null) {
                logger.log(
                        Level.WARNING,
                        " [ RRDDataReceiverConfigMgr ] Unable to instantiate RRDDataReceiverConfigMgr. Configuration file RRDConfigManager.config not defined");
            }

            try {
                cfg = new RRDDataReceiverConfigMgr(cfgFile.trim());
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                        " [ RRDDataReceiverConfigMgr ] Unable to instantiate RRDDataReceiverConfigMgr. Cause:", t);
                cfg = null;
            }
            theInstance = cfg;
        }
    }

    private final File configFile;

    private final AtomicReference<List<MLRRDConfigEntry>> mlMappingsConfigs = new AtomicReference<List<MLRRDConfigEntry>>();

    private RRDDataReceiverConfigMgr(final String configFileName) throws Exception {
        final File tmpConfigFile = new File(configFileName);

        if (configFileName == null) {
            throw new NullPointerException(" [ RRDDataReceiverConfigMgr ] Null config file name");
        }

        if (!tmpConfigFile.exists()) {
            throw new IOException(" [ RRDDataReceiverConfigMgr ] Config file does not exist: " + configFileName);
        }

        if (!tmpConfigFile.canRead()) {
            throw new IOException(" [ RRDDataReceiverConfigMgr ] Config file is not readable: " + configFileName);
        }

        this.configFile = tmpConfigFile;
        reloadConfig();
        final DateFileWatchdog dfw = DateFileWatchdog.getInstance(configFile, 5 * 1000);
        dfw.addObserver(this);
    }

    public static RRDDataReceiverConfigMgr getInstance() {
        return InstanceHolder.theInstance;
    }

    private final void reloadConfig() {
        boolean bReload = true;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            Properties p = new Properties();
            fis = new FileInputStream(configFile);
            bis = new BufferedInputStream(fis);
            p.load(bis);

            final String mlRRDCountProp = p.getProperty("mlrrd.mappings");
            if ((mlRRDCountProp == null) || (mlRRDCountProp.trim().length() == 0)) {
                throw new IllegalArgumentException("mlrrd.mappings not (correclty) defined");
            }

            int mlRRDMappingsCount = 0;
            try {
                mlRRDMappingsCount = Integer.valueOf(mlRRDCountProp);
            } catch (Throwable t) {
                throw new IllegalArgumentException("mlrrd.mappings not (correclty) defined. Not a number? Cause: ", t);
            }

            if (mlRRDMappingsCount <= 0) {
                throw new IllegalArgumentException("mlrrd.mappings should be a positive integer");
            }

            List<MLRRDConfigEntry> mlRRDMappings = new ArrayList<MLRRDConfigEntry>(mlRRDMappingsCount);
            for (int i = 1; i <= mlRRDMappingsCount; i++) {
                final String mlRRDPreds[] = getPropChecked("mlrrd." + i + ".predicates", p).split("(\\s)*,(\\s)*");
                if ((mlRRDPreds == null) || (mlRRDPreds.length == 0)) {
                    throw new IllegalArgumentException("mlrrd." + i
                            + ".predicates not correctly defined. Should have at least one predicate");
                }

                List<monPredicate> predicatesList = new LinkedList<monPredicate>();
                for (final String predS : mlRRDPreds) {
                    monPredicate mp = lia.web.utils.Formatare.toPred(predS);
                    if (mp == null) {
                        throw new IllegalArgumentException("Cannot understand predicate: " + predS);
                    }
                    predicatesList.add(mp);
                }

                if (predicatesList.size() == 0) {
                    throw new IllegalArgumentException("the property mlrrd." + i
                            + ".predicates is not well defined. No predicates parsed");
                }

                final String rrdTemplate = getPropChecked("mlrrd." + i + ".rrd.template", p);
                RRDConfig rrdConfig = null;
                try {
                    final int rrdTemplateIdx = Integer.valueOf(rrdTemplate);
                    rrdConfig = RRDConfigManager.getInstance().getRRDTemplates().getRRDConfig(rrdTemplateIdx);
                } catch (Throwable t) {
                    throw new IllegalArgumentException("Unable to parse mlrrd." + i + ".rrd.template property. Cause:",
                            t);
                }

                List<DSConfig> dsConfigs = rrdConfig.getDataSources();
                Map<String, DSConfig> functionDSMap = new HashMap<String, DSConfig>(dsConfigs.size());
                for (DSConfig dsConfig : dsConfigs) {
                    final String mlFunctionProp = getPropChecked("mlrrd." + i + ".ds." + dsConfig.getIndex()
                            + ".mlfunction.suffix", p);
                    functionDSMap.put(mlFunctionProp, dsConfig);
                }

                mlRRDMappings.add(new MLRRDConfigEntry(rrdConfig, predicatesList, functionDSMap));
            }

            mlMappingsConfigs.set(Collections.unmodifiableList(mlRRDMappings));
        } catch (Throwable t) {
            bReload = false;
            logger.log(Level.WARNING, " [ RRDConfigManager ] exception reloading the config. Cause: ", t);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Throwable ignore) {
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (Throwable ignore) {
                }
            }
            logger.log(Level.INFO, " [ RRDConfigManager ] reloaded config. Success: " + bReload);
        }
    }

    private static final String getPropChecked(final String propertyName, Properties p) {
        final String propValue = p.getProperty(propertyName);
        if (propValue == null) {
            throw new IllegalArgumentException(propertyName + " not defined");
        }
        final String propTrimmed = propValue.trim();
        if (propTrimmed.length() <= 0) {
            throw new IllegalArgumentException(propertyName + " illegal value=" + propValue + "; trimmed="
                    + propTrimmed);
        }

        return propTrimmed;
    }

    public List<MLRRDConfigEntry> getMLMappingsConfigs() {
        return mlMappingsConfigs.get();
    }

    public File getConfigFile() {
        return configFile;
    }

    @Override
    public void update(Observable o, Object arg) {
        reloadConfig();
    }
}
