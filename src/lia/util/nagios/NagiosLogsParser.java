package lia.util.nagios;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;
import java.util.Set;

/**
 * Parses nagios logs in the format:<br>
 *
 * <code>
 * [1168112623] SERVICE ALERT: e600chi;CHI NYC GC LINK STATUS;CRITICAL;SOFT;1;Link is DOWN
 * [1168112683] SERVICE ALERT: e600chi;CHI NYC GC LINK STATUS;OK;SOFT;2;Link is UP
 * </code>
 *
 * @since 17/07/2008
 * @author ramiro
 *
 */
public class NagiosLogsParser {
    //nagios log dir
    private final File nagiosLogDir;    //eventual filter the files in the nagios log dir
    private final String logFileNamePrefix;
    private final String logFileNameSuffix;
    /**
     * contains NagiosLogEntry sorted in time; per link
     *
     * Key: link name; Value: TreeSet of nagios events ( sorted in time )
     */
    private final Map<String, Set<NagiosLogEntry>> nagiosLogsMap;
    private final Map<String, Set<NagiosEventInterval>> nagiosIntervalsMap;
    private final long startDate;
    private final long endDate;
    private final long maxAllowedDownTime;

    private NagiosLogsParser(final String nagiosLogDir,
            final String logFileNamePrefix,
            final String logFileNameSuffix,
            final String sTime,
            final String eTime,
            final long maxAllowedDownTime) throws Exception {
        if (nagiosLogDir == null) {
            throw new NullPointerException("Nagios lgo dir cannot be null");
        }

        this.nagiosLogDir = new File(nagiosLogDir);
        if (this.nagiosLogDir == null || !this.nagiosLogDir.exists() || !this.nagiosLogDir.isDirectory()) {
            throw new Exception(" The Nagios logs directory ( " + nagiosLogDir + ") does not exist");
        }

        if (!this.nagiosLogDir.canRead()) {
            throw new Exception(" The Nagios logs directory ( " + nagiosLogDir + ") cannot be read");
        }

        this.logFileNamePrefix = logFileNamePrefix;
        this.logFileNameSuffix = logFileNameSuffix;

        final String[] files = this.nagiosLogDir.list(getFileNameFilter());

        if (files == null || files.length < 1) {
            throw new Exception(" No nagios log files in the specified directory " + nagiosLogDir);
        }

        startDate = NagiosUtils.getImportDate(sTime);
        endDate = NagiosUtils.getImportDate(eTime);
        this.maxAllowedDownTime = maxAllowedDownTime;

        this.nagiosLogsMap = NagiosUtils.getNagiosLogEntries(startDate, endDate, files, nagiosLogDir);
        this.nagiosIntervalsMap = NagiosUtils.getNagiosDownIntervals(startDate, endDate, maxAllowedDownTime, files, nagiosLogDir);

    }

    public Map<String, Set<NagiosEventInterval>> getDownIntervalMap() {
        return nagiosIntervalsMap;
    }

    private final FilenameFilter getFileNameFilter() {
        return new FilenameFilter() {

            public boolean accept(File dir, String name) {
                if (NagiosLogsParser.this.logFileNamePrefix != null && !name.startsWith(NagiosLogsParser.this.logFileNamePrefix)) {
                    return false;
                }

                if (NagiosLogsParser.this.logFileNameSuffix != null && !name.endsWith(NagiosLogsParser.this.logFileNameSuffix)) {
                    return false;
                }

                return true;
            }
        };
    }

    public static final NagiosLogsParser getInstance(final String nagiosLogDir,
            final String logFileNamePrefix,
            final String logFileNameSuffix,
            final String sTime,
            final String eTime,
            final long maxAllowedDownTime) throws Exception {
        return new NagiosLogsParser(nagiosLogDir, logFileNamePrefix, logFileNameSuffix, sTime, eTime, maxAllowedDownTime);
    }

}
