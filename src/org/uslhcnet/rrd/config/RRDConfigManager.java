/*
 * Created on Aug 23, 2010
 */
package org.uslhcnet.rrd.config;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.DateFileWatchdog;

/**
 * @author ramiro
 */
public class RRDConfigManager implements Observer {
    private static final Logger logger = Logger.getLogger(RRDConfigManager.class.getName());

    private static final class InstanceHolder {
        private static final RRDConfigManager theInstance;

        static {
            final String cfgFile = AppConfig.getProperty("RRDConfigManager.config");
            RRDConfigManager cfg = null;
            if (cfgFile == null) {
                logger.log(Level.WARNING,
                        " [ RRDConfigManager ] Unable to instantiate RRDConfigManager. Configuration file RRDConfigManager.config not defined");
            }

            try {
                cfg = new RRDConfigManager(cfgFile.trim());
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ RRDConfigManager ] Unable to instantiate RRDConfigManager. Cause:", t);
                cfg = null;
            }
            theInstance = cfg;
        }
    }

    private final File configFile;

    private final AtomicReference<String> rrdDirectory = new AtomicReference<String>();
    private final AtomicReference<String> rrdFileExtension = new AtomicReference<String>();
    private final AtomicReference<String> rrdToolCmd = new AtomicReference<String>();

    private final AtomicReference<RRATemplates> rraTemplatesRef = new AtomicReference<RRATemplates>();
    private final AtomicReference<DSTemplates> dsTemplatesRef = new AtomicReference<DSTemplates>();
    private final AtomicReference<RRDTemplates> rrdTemplatesRef = new AtomicReference<RRDTemplates>();

    private RRDConfigManager(final String configFileName) throws Exception {
        final File tmpConfigFile = new File(configFileName);

        if (configFileName == null) {
            throw new NullPointerException(" [ RRDConfigManager ] Null config file name");
        }

        if (!tmpConfigFile.exists()) {
            throw new IOException(" [ RRDConfigManager ] Config file does not exist: " + configFileName);
        }

        if (!tmpConfigFile.canRead()) {
            throw new IOException(" [ RRDConfigManager ] Config file is not readable: " + configFileName);
        }

        this.configFile = tmpConfigFile;
        reloadConfig();
        final DateFileWatchdog dfw = DateFileWatchdog.getInstance(configFile, 5 * 1000);
        dfw.addObserver(this);
        logger.log(Level.INFO, " RRDConfigManager instantiated !");
    }

    public static final RRDConfigManager getInstance() {
        if (InstanceHolder.theInstance == null) {
            throw new IllegalStateException("Config manager is not instantiated.");
        }
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
            final String destDirProp = p.getProperty("rrd.directory");

            if ((destDirProp == null) || (destDirProp.trim().length() == 0)) {
                throw new IllegalArgumentException("rrd.directory not (correclty) defined");
            }
            rrdDirectory.set(destDirProp.trim());

            final String rrdExtProp = p.getProperty("rrd.file.extension", ".rrd");

            if (rrdExtProp == null) {
                throw new IllegalArgumentException("rrd.file.extension not (correclty) defined");
            }
            rrdFileExtension.set(rrdExtProp.trim());

            final String rrdToolProp = p.getProperty("rrd.tool.cmd", "/usr/bin/rrdtool");

            if (rrdToolProp == null) {
                throw new IllegalArgumentException("rrd.tool.cmd not (correclty) defined");
            }
            rrdToolCmd.set(rrdToolProp.trim());

            rraTemplatesRef.set(new RRATemplates(p));
            dsTemplatesRef.set(new DSTemplates(p));
            rrdTemplatesRef.set(new RRDTemplates(p, rraTemplatesRef.get(), dsTemplatesRef.get()));
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

    public String rrdDirectory() {
        return rrdDirectory.get();
    }

    public String rrdFileExtension() {
        return rrdFileExtension.get();
    }

    public String rrdToolCmd() {
        return rrdToolCmd.get();
    }

    public RRATemplates getRRATemplates() {
        return rraTemplatesRef.get();
    }

    public DSTemplates getDSTemplates() {
        return dsTemplatesRef.get();
    }

    public RRDTemplates getRRDTemplates() {
        return rrdTemplatesRef.get();
    }

    @Override
    public void update(Observable o, Object arg) {
        reloadConfig();
    }
}
