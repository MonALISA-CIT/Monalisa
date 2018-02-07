/*
 * Created on Aug 20, 2010
 */
package org.uslhcnet.rrd;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.MLProcess;

import org.uslhcnet.rrd.config.RRDConfig;
import org.uslhcnet.rrd.config.RRDConfigManager;

/**
 * @author ramiro
 */
public class RRDFile {

    private static final Logger logger = Logger.getLogger(RRDFile.class.getName());

    private final File rrdFile;

    private final RRDConfig template;

    /**
     * @param rrdDirectory
     * @param rrdFileKey
     * @param dataSources
     * @param rraConfig
     * @throws IOException
     */
    public RRDFile(final String rrdFileName, RRDConfig template) throws IOException {
        final RRDConfigManager rrdCfgMgr = RRDConfigManager.getInstance();
        final String rrdDir = rrdCfgMgr.rrdDirectory();
        final String rrdExt = rrdCfgMgr.rrdFileExtension();

        this.rrdFile = new File(rrdDir + File.separatorChar + rrdFileName + rrdExt);
        this.template = template;
    }

    /**
     * @throws IOException
     */
    public synchronized void createRRDFile() throws IOException {
        if (!rrdFile.exists()) {
            final String rrdCreateCMD = template.getRRDCreateCommand(rrdFile.toString());
            logger.log(Level.INFO, " RRDFile: " + rrdFile + " does not exist. Creating it using cmd: " + rrdCreateCMD);
            try {
                Process p = MLProcess.exec(RRDConfigManager.getInstance().rrdToolCmd() + " " + rrdCreateCMD);
                int exitStatus = p.waitFor();
                if (exitStatus != 0) {
                    logger.log(Level.WARNING, " exist status for: " + rrdCreateCMD + " was " + exitStatus);
                    throw new IOException("Unable to rrdcreate " + rrdFile + ". Exit status = " + exitStatus);
                }
            } catch (Throwable t) {
                throw new IOException("Unable to rrdcreate " + rrdFile + ". Cause: ", t);
            }
        }
    }

    public <T> void addValues(final long timestamp, Collection<DSValue<T>> values) throws IOException {
        if (!rrdFile.exists()) {
            createRRDFile();
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ RRDFile ] " + rrdFile + " add multiple values: " + values);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" update ").append(rrdFile.toString()).append(" --template ");
        StringBuilder sbValTS = new StringBuilder(" ").append(timestamp).append(':');
        // build cmd line
        for (final Iterator<DSValue<T>> it = values.iterator(); it.hasNext();) {
            final DSValue<T> dsVal = it.next();
            //
            sb.append(dsVal.ds().name());
            sbValTS.append(dsVal.value());
            if (it.hasNext()) {
                sb.append(':');
                sbValTS.append(':');
            }
        }

        final String updateCmd = sb.toString() + sbValTS.toString();
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ RRDFile ] update cmd: " + updateCmd);
        }

        try {
            Process p = MLProcess.exec(RRDConfigManager.getInstance().rrdToolCmd() + " " + updateCmd);
            int exitStatus = p.waitFor();
            if (exitStatus != 0) {
                logger.log(Level.SEVERE, " [ RRDFile ] [ rrdupdate ERROR ] exist status for: " + updateCmd + " was "
                        + exitStatus);
            }
        } catch (Throwable t) {
            throw new IOException("Unable to rrdupdate " + rrdFile + ". Cause: ", t);
        }

    }

}
