package lia.Monitor.Store;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.Formatare;

/**
 */
public class PersistentStoreFast extends AbstractPersistentStore {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(PersistentStoreFast.class.getName());

    private String sTableNames[] = null;

    /**
     * @param constraintParams
     */
    public PersistentStoreFast(String[] constraintParams) {
        super(constraintParams);

        // sTableNames will be a vector of only one element : the table corresponding to the minimum interval length
        try {
            int nr = Integer.parseInt(AppConfig.getProperty("lia.Monitor.Store.TransparentStoreFast.web_writes", "0"));

            sTableNames = new String[1];
            sTableNames[0] = null;

            long lMinTime = 0;

            for (int i = 0; i < nr; i++) {
                long lTotalTime = Long.parseLong(AppConfig.getProperty("lia.Monitor.Store.TransparentStoreFast.writer_"
                        + i + ".total_time", "0")) * 1000;

                if ((lMinTime == 0) || (lMinTime > lTotalTime)) {
                    lMinTime = lTotalTime;
                    sTableNames[0] = AppConfig.getProperty("lia.Monitor.Store.TransparentStoreFast.writer_" + i
                            + ".table_name", "writer_" + i);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "PersistentStoreFast : could not determine the table names. Check your App.properties file!");
        }
    }

    @Override
    public Vector<Object> getResults(final monPredicate p) {
        if ((sTableNames == null) || (sTableNames.length <= 0) || (sTableNames[0] == null)) {
            logger.log(Level.WARNING, "hmmm ... no tables defined ?!");
            return null;
        }

        Vector<Object> res = new Vector<Object>();

        for (int indent = sTableNames.length - 1; indent >= 0; indent--) {
            Vector<Object> vToAdd = getResults(p, indent);
            if (vToAdd.size() > 0) {
                res.addAll(vToAdd);
            }
        }
        return res;
    }

    @Override
    public Vector<Object> getResults(final monPredicate p, final int indent) {
        final Vector<Object> results = new Vector<Object>();
        String query = DataSelect.rquery(p, sTableNames[indent]);

        try {
            DB db = new DB();
            
            db.setReadOnly(true);
            
            db.query(query);

            while (db.moveNext()) {
                Result rez = new Result();
                rez.time = db.getl("rectime");
                rez.FarmName = db.gets("mfarm");
                rez.ClusterName = db.gets("mcluster");
                rez.NodeName = db.gets("mnode");
                rez.addSet(db.gets("mfunction"), db.getd("mval"));

                results.add(rez);
            }
        } catch (Exception ee) {
            logger.log(Level.WARNING, " Failed to execute the query ! ", ee);
        }

        return results;
    }

    /*
     These methods are not required since they are not used in the HistogramBean, so we'll just skip this part for now
     */

    @Override
    public void makePersistent(lia.Monitor.monitor.Result[] values) throws StoreException {
        // ignore
    }

    @Override
    public void makePersistent(lia.Monitor.monitor.eResult[] values) throws StoreException {
        // ignore
    }

    @Override
    public void deleteOld(long time, int indent, long avgTime) throws StoreException {
        // ignore
    }

    @Override
    public void updateConfig(lia.Monitor.monitor.MFarm mfarm) throws StoreException {
        // ignore
    }

    private static Vector<Object> getConfigByQuery(final String query) {
        Vector<Object> retV = null;
        DB db = new DB();

        if (!db.query(query)) {
            return null;
        }

        retV = new Vector<Object>();

        try {
            while (db.moveNext()) {
                ByteArrayInputStream bais = new ByteArrayInputStream(db.getBytes(1));
                ObjectInputStream ois = new ObjectInputStream(bais);
                Object conf = ois.readObject();
                retV.add(conf);
            }
        } catch (Throwable t) {
            // ignore
        }

        return retV;

    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public java.util.Vector getConfig(long fromTime, long toTime) throws StoreException {
        return getConfigByQuery("SELECT conf FROM monitor_conf WHERE conftime > " + fromTime + " AND conftime < "
                + toTime + ";");
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public java.util.Vector getConfig(String FarmName, long fromTime, long toTime) {
        return getConfigByQuery("SELECT conf FROM monitor_conf WHERE mfarm = " + Formatare.mySQLEscape(FarmName)
                + " AND conftime > " + fromTime + " AND conftime < " + toTime + ";");
    }

    @Override
    public java.lang.Object getConfig(final String FarmName) {
        return getConfigByQuery("SELECT conf FROM monitor_conf  WHERE  mfarm=" + Formatare.mySQLEscape(FarmName)
                + " ORDER BY conftime LIMIT 1");
    }

}