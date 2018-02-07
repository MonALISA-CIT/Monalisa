package lia.web.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.modules.monProcIO;
import lia.Monitor.modules.monProcLoad;
import lia.Monitor.modules.monProcStat;
import lia.Monitor.modules.monSiteStats;
import lia.Monitor.monitor.MonitoringModule;

/**
 * @author costing
 *
 */
public class ExportSiteStatistics extends ExportStatistics {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(ExportSiteStatistics.class.getName());

    /**
     * @param repositoryPort
     */
    public ExportSiteStatistics(int repositoryPort) {
        System.err.println("ExportSiteStatistics(" + repositoryPort + ")");

        init("MLSite", repositoryPort);

        MonitoringModule mm = null;
        try {
            mm = new monProcLoad();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportSiteStatistics ] [ HANDLED ]Unable to instanitate monProcLoad()", t);
            mm = null;
        }

        if (mm != null) {
            addMonitoringModule(mm);
        }

        try {
            mm = new monProcStat();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportSiteStatistics ] [ HANDLED ]Unable to instanitate monProcStat()", t);
            mm = null;
        }
        if (mm != null) {
            addMonitoringModule(mm);
        }

        try {
            mm = new monProcIO();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportSiteStatistics ] [ HANDLED ]Unable to instanitate monProcIO()", t);
            mm = null;
        }
        if (mm != null) {
            addMonitoringModule(mm);
        }

        try {
            mm = new monSiteStats();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ ExportSiteStatistics ] [ HANDLED ]Unable to instanitate monSiteStats()", t);
            mm = null;
        }
        if (mm != null) {
            addMonitoringModule(mm);
        }
    }

}
