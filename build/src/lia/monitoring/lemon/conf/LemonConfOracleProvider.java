package lia.monitoring.lemon.conf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.ntp.NTPDate;

public class LemonConfOracleProvider extends LemonConfProvider {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LemonConfOracleProvider.class.getName());

    private String jdbcURL;
    private String user;
    private String passwd;

    private long nextCheck;
    private long checkInterval;
    private long errorCheckInterval;

    private final boolean hasToRun;

    public LemonConfOracleProvider(String jdbcURL, String user, String passwd, long checkInterval,
            long errorCheckInterval) {
        setNewParams(jdbcURL, user, passwd, checkInterval, errorCheckInterval);
        hasToRun = true;
    }

    public void setNewParams(String jdbcURL, String user, String passwd, long checkInterval, long errorCheckInterval) {
        logger.log(Level.INFO, "LemonConfOracleProvider using [ " + jdbcURL + ", " + user + ", " + passwd
                + " ] checkInt = " + checkInterval + " errCheck = " + errorCheckInterval);
        this.jdbcURL = jdbcURL;
        this.user = user;
        this.passwd = passwd;
        this.checkInterval = checkInterval;
        this.errorCheckInterval = errorCheckInterval;
        nextCheck = 0;
    }

    private Connection getConnection() throws Exception {
        DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
        return DriverManager.getConnection(jdbcURL, user, passwd);
    }

    private Hashtable getHostCluster(Connection conn) {
        Hashtable hc = new Hashtable();
        try {
            Statement stmt = conn.createStatement();
            stmt.execute("select hostname, clustername from cdb_clustermap");
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String hostname = null;
                String clustername = null;
                try {
                    hostname = rs.getString(1);
                    clustername = rs.getString(2);

                    if ((hostname != null) && (hostname.length() > 0)) {
                        if ((clustername == null) || (clustername.length() == 0)) {
                            logger.log(Level.WARNING, "LemonConfOracleProvider using [ " + jdbcURL + ", " + user + ", "
                                    + passwd + " ] checkInt = " + checkInterval + " errCheck = " + errorCheckInterval);
                            clustername = "N/A";
                        }

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "H: " + hostname + " C: " + clustername + " ... adding to conf");
                        }
                        hc.put(hostname, clustername);
                    } else {
                        logger.log(Level.WARNING, "Got a null or 0 size() length hostname .... ignoring it!");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error getHostCluster():", t);
                }
            }

            try {
                stmt.close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error getHostCluster() trying to close() statement ... ignoring it", t);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error getHostCluster(): --- table cdb_clustermap", t);
            conn = null;
            hc = null;
        }

        if ((hc != null) && (hc.size() == 0)) {
            return null;
        }

        return hc;
    }

    private Hashtable getMetricID_Class(Connection conn) {
        Hashtable midc = new Hashtable();
        try {
            Statement stmt = conn.createStatement();
            stmt.execute("select metric_id, metric_class from metric_instances");
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                Integer metric_id = null;
                String metric_class = null;
                try {
                    metric_id = Integer.valueOf(rs.getInt(1));
                    metric_class = rs.getString(2);

                    if (metric_id != null) {
                        if ((metric_class == null) || (metric_class.length() == 0)) {
                            logger.log(Level.WARNING, "Got a null or 0 size() length metric_class for metric_id "
                                    + metric_id + " ... ignoring it");
                            continue;
                        }
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, "metric_id: " + metric_id + " metric_class: " + metric_class
                                    + " ... adding to conf");
                        }

                        midc.put(metric_id, metric_class);
                    } else {
                        logger.log(Level.WARNING, "Got a null or 0 size() length metric_id .... ignoring it!");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error getMetricID_Class():", t);
                }
            }

            try {
                stmt.close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error getMetricID_Class() trying to close() statement ... ignoring it", t);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error getMetricID_Class(): --- table metric_instances", t);
            conn = null;
            midc = null;
        }

        if ((midc != null) && (midc.size() == 0)) {
            return null;
        }
        return midc;
    }

    private Hashtable getMetricFields(Connection conn) {
        Hashtable mfh = new Hashtable();
        try {
            Statement stmt = conn.createStatement();
            stmt.execute("select metric_class, field_index, field_name, field_type from metric_fields");
            ResultSet rs = stmt.getResultSet();

            while (rs.next()) {
                String metric_class = null;
                Integer field_index = null;
                String field_name = null;
                String field_type = null;

                try {

                    metric_class = rs.getString(1);
                    field_index = Integer.valueOf(rs.getInt(2));
                    field_name = rs.getString(3);
                    field_type = rs.getString(4);

                    if (metric_class != null) {
                        if ((field_name == null) || (field_name.length() == 0)) {
                            logger.log(Level.WARNING, "Got a null or 0 size() length field_name for metric_class "
                                    + metric_class + " ... ignoring it");
                            continue;
                        }
                        if ((field_type == null) || (field_type.length() == 0)) {
                            logger.log(Level.WARNING, "Got a null or 0 size() length field_type for metric_class "
                                    + metric_class + " field_name " + field_name + " ... ignoring it");
                            continue;
                        }

                        if (mfh.get(metric_class) == null) {
                            mfh.put(metric_class, new LemonMetricFields(metric_class));
                        }
                        LemonMetricFields lmf = (LemonMetricFields) mfh.get(metric_class);
                        lmf.addField(field_index.shortValue(), field_name, field_type);

                    } else {
                        logger.log(Level.WARNING, "Got a null or 0 size() length metric_class .... ignoring it!");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error getMetricFields():", t);
                }
            }

            try {
                stmt.close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error getMetricFields() trying to close() statement ... ignoring it", t);
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error getMetricFields(): --- table metric_fields", t);
            conn = null;
            mfh = null;
        }

        if ((mfh != null) && (mfh.size() == 0)) {
            return null;
        }
        return mfh;
    }

    @Override
    public void run() {
        while (hasToRun) {
            try {
                Thread.sleep(5 * 1000);
            } catch (Throwable t) {
            }
            try {
                long now = NTPDate.currentTimeMillis();
                if (nextCheck < now) {
                    try {
                        Connection conn = getConnection();

                        Hashtable hch = getHostCluster(conn);
                        Hashtable mich = getMetricID_Class(conn);
                        Hashtable mfh = getMetricFields(conn);

                        LemonConf clc = getCurrentLemonConf();

                        try {
                            conn.close();
                        } catch (Throwable t1) {
                            logger.log(Level.WARNING, " Got Exc closing conn", t1);
                        }

                        if ((hch == null) || (mich == null) || (mfh == null)) {
                            nextCheck = now + errorCheckInterval;
                            logger.log(Level.WARNING, "One of the hashes is null [ hch (cdb_clustermap) "
                                    + ((hch == null) ? "==" : "!=") + " null, mich (metric_instances) "
                                    + ((mich == null) ? "==" : "!=") + " null, mfh (metric_fields)] "
                                    + ((mfh == null) ? "==" : "!=") + " null ]");
                            setNewConf(hch, mich, mfh);
                            nextCheck = now + errorCheckInterval;
                            return;
                        }

                        if ((clc == null)
                                || ((clc.hostsToClusterMap == null) && (hch != null))
                                || ((clc.metricsIDToNameMap == null) && (mich != null))
                                || ((clc.metricsNameToFieldsMap == null) && (mfh != null))
                                || ((clc.hostsToClusterMap != null) && (hch != null) && !clc.hostsToClusterMap
                                        .equals(hch))
                                || ((clc.metricsIDToNameMap != null) && (mich != null) && !clc.metricsIDToNameMap
                                        .equals(mich))
                                || ((clc.metricsNameToFieldsMap != null) && (mfh != null) && !clc.metricsNameToFieldsMap
                                        .equals(mfh))) {
                            logger.log(Level.INFO, "\n\n\nThe conf has been changed .... ");
                            setNewConf(hch, mich, mfh);
                        } else {
                            logger.log(Level.INFO, "\n\n\nSAME conf .... ");
                        }
                        nextCheck = now + checkInterval;

                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Huston we've got a problem ... ", t);
                        setNewConf(null, null, null);
                        nextCheck = now + errorCheckInterval;
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Huston we've got a problem ... ", t);
            }
        }
    }
}
