/*
 * $Id: MFilter2.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.Filters;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author Iosif Legrand
 * 
 */
public class MFilter2 extends GenericMLFilter {

    /**
     * 
     */
    private static final long serialVersionUID = -1768041657354379587L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MFilter2.class.getName());

    public static final String Name = "MFilter2";
    public static long MF2_SLEEP_TIME;

    //consider a param no longer aveilable after PARAM_EXPIRE time
    private static long PARAM_EXPIRE;

    String[][] interest = { { "PN", "Load5", "Load1", "Load", "CPU_usr", "CPU_sys", "CPU_nice", "TotalIO_Rate_IN",
            "TotalIO_Rate_OUT", "FreeDsk", "UsedDsk", "NoCPUs", "MEM_free", "MEM_total", "MEM_shared", "MEM_cached",
            "MEM_buffers" } };

    static {
        MF2_SLEEP_TIME = 20 * 1000;
        try {
            MF2_SLEEP_TIME = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.Filters.MFilter2.SLEEP_TIME", "20000").trim()).longValue();
        } catch (Throwable t1) {
            MF2_SLEEP_TIME = 20 * 1000;
        }

        PARAM_EXPIRE = 600000;

        try {
            PARAM_EXPIRE = Long.valueOf(AppConfig.getProperty("lia.Monitor.Filters.PARAM_EXPIRE", "600")).longValue() * 1000;
        } catch (Throwable t) {
            PARAM_EXPIRE = 600000;
        }
    }

    Vector old_ans;

    int tot;

    int free;

    Hashtable hs;

    Hashtable clust_ref;

    Vector buff;

    Vector buff1;

    class ExpireResult {

        Result r;

        long endLease;

        ExpireResult(Result r, long endLease) {
            this.r = r;
            this.endLease = endLease;
        }
    }

    public MFilter2(String farmName) {
        super(farmName);
        buff = new Vector();
        buff1 = new Vector();
        hs = new Hashtable();
        clust_ref = new Hashtable();
    }

    @Override
    public String getName() {
        return Name;
    }

    public void removeOldParams() {
        for (Enumeration e = hs.keys(); e.hasMoreElements();) {
            String clus = (String) e.nextElement();
            Hashtable hparam = (Hashtable) hs.get(clus);
            if (hparam != null) {
                for (Enumeration e1 = hparam.keys(); e1.hasMoreElements();) {
                    String para = (String) e1.nextElement();
                    Hashtable hnodes = (Hashtable) hparam.get(para);
                    if ((hnodes != null) && (hnodes.size() > 0)) {
                        for (Enumeration e2 = hnodes.keys(); e2.hasMoreElements();) {
                            Object key = e2.nextElement();
                            long endLease = ((ExpireResult) hnodes.get(key)).endLease;
                            if (endLease < NTPDate.currentTimeMillis()) {
                                if (logger.isLoggable(Level.FINEST)) {
                                    logger.log(Level.FINEST, " Removing node [ " + key + " ] for [ " + clus + ", "
                                            + para + " ] ");
                                }
                                hnodes.remove(key);
                            }
                        }
                    }
                    if ((hnodes != null) && (hnodes.size() == 0)) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Removing param [ " + para + " ] for all nodes! ");
                        }
                        hparam.remove(para);
                    }
                }
                if ((hparam != null) && (hparam.size() == 0)) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Removing cluster [ " + clus + " ] for all params! ");
                    }
                    hs.remove(clus);
                }
            }
        }
    }

    void updateResults() {
        if (buff.size() == 0) {
            return;
        }
        synchronized (buff) {
            buff1.clear();
            buff1.addAll(buff);
            buff.clear();
        }

        synchronized (hs) {
            for (int i = 0; i < buff1.size(); i++) {
                update((Result) buff1.elementAt(i));
            }
        }
    }

    void update(Result r) {
        if ((r == null) || (r.param_name == null) || (r.param_name.length == 0) || (r.param == null)
                || (r.param.length == 0) || (r.param.length != r.param_name.length)) {
            return;
        }

        /* maybe we should make a copy of r here, before modifying it */

        /* if the result comes fromm the PN_Condor or PN_PBS modules, send
         * Load1 as Load5 because Load5 is not available
         */
        if ((r.ClusterName.indexOf("PN_Condor") != -1) || (r.ClusterName.indexOf("PN_PBS") != -1)
                || (r.ClusterName.indexOf("PN_LSF") != -1)) {
            int idx = r.getIndex("Load1");
            if (idx >= 0) {
                r.addSet("Load5", r.param[idx]);
            }
        }

        /* add the "Load" parameter (which is either Load1 or Load5) */
        int idx = r.getIndex("Load5");
        if (idx >= 0) {
            r.addSet("Load", r.param[idx]);
        }

        for (String[] element : interest) {
            if (r.ClusterName.indexOf(element[0]) != -1) {
                if (!hs.containsKey(r.ClusterName)) {
                    hs.put(r.ClusterName, new Hashtable());

                    MCluster cluster = farm.getCluster(r.ClusterName);

                    if (cluster == null) {
                        cluster = new MCluster(r.ClusterName, farm);
                        farm.addClusterIfAbsent(cluster);
                    }

                    clust_ref.put(r.ClusterName, cluster);
                }

                Hashtable parameters = (Hashtable) hs.get(r.ClusterName);

                for (String element2 : r.param_name) {
                    for (int kk = 1; kk < element.length; kk++) {
                        String para = element[kk];
                        if (para.equals(element2)) {
                            if (!parameters.containsKey(para)) {
                                parameters.put(para, new Hashtable());
                            }
                            Hashtable nodes = (Hashtable) parameters.get(para);
                            nodes.put(r.NodeName, new ExpireResult(r, NTPDate.currentTimeMillis() + PARAM_EXPIRE));
                        }
                    }
                } // kk
            }
        }

    }

    @Override
    public Object expressResults() {
        updateResults();

        synchronized (hs) {
            removeOldParams();
        }
        Vector v = null;

        synchronized (hs) {
            v = myExpressResults();
        }
        return v;
    }

    public Vector myExpressResults() {
        Vector ans = new Vector();
        double[] values = new double[500];
        int ynd = 0;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " myExpressResults hs.size() [ " + hs.size() + " ] ! ");
        }
        for (Enumeration e = hs.keys(); e.hasMoreElements();) {
            String clus = (String) e.nextElement();
            int NoOfNodes = ((MCluster) clust_ref.get(clus)).getNodes().size();
            Hashtable hparam = (Hashtable) hs.get(clus);
            if ((hparam != null) && (hparam.size() > 0)) {
                /*
                if (hparam.containsKey("Load5"))
                	logger.info("### Have load5! " + clus);
                if (hparam.containsKey("Load1"))
                	logger.info("### Have load1!" + clus);
                	*/
                for (Enumeration e1 = hparam.keys(); e1.hasMoreElements();) {

                    String para = (String) e1.nextElement();

                    if ((para != null) && (para.indexOf("MEM_free") != -1)) {
                        if (hparam.containsKey("MEM_total")) {
                            Hashtable hn_free = (Hashtable) hparam.get("MEM_free");
                            Hashtable hn_total = (Hashtable) hparam.get("MEM_total");

                            if ((hn_free == null) || (hn_total == null) || (hn_free.size() == 0)
                                    || (hn_total.size() == 0)) {
                                continue;
                            }

                            Vector memr = new Vector();
                            for (Enumeration ef = hn_free.keys(); ef.hasMoreElements();) {
                                String nname = (String) ef.nextElement();
                                if (nname != null) {
                                    ExpireResult er = (ExpireResult) hn_free.get(nname);
                                    Result rmf = null;
                                    Result rmt = null;
                                    if (er != null) {
                                        rmf = er.r;
                                    }
                                    er = ((ExpireResult) hn_total.get(nname));
                                    if (er != null) {
                                        rmt = er.r;
                                    }
                                    if ((rmf == null) || (rmt == null)) {
                                        continue;
                                    }
                                    int idx_f = rmf.getIndex("MEM_free");
                                    int idx_t = rmf.getIndex("MEM_total");
                                    if ((idx_f == -1) || (idx_t == -1)) {
                                        continue;
                                    }
                                    double mf = rmf.param[idx_f];
                                    double mt = rmf.param[idx_t];
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "For [ " + nname + " ] mf = " + mf + " mt= " + mt);
                                    }
                                    memr.add(Double.valueOf(mf / mt));
                                }
                            }

                            if (memr.size() != 0) {
                                double[] vals = new double[memr.size()];

                                for (int vint = 0; vint < memr.size(); vint++) {
                                    vals[vint] = ((Double) memr.elementAt(vint)).doubleValue();
                                }

                                Gresult xr = new Gresult(farmName, clus, "MEM_free_p");
                                fill_data("MEM_free_p", xr, memr.size(), 4, vals);
                                ans.add(xr);
                            }
                        }
                    }

                    Gresult xr = new Gresult(farmName, clus, para);
                    xr.TotalNodes = NoOfNodes;

                    Hashtable hnodes = (Hashtable) hparam.get(para);

                    if ((hnodes != null) && (hnodes.size() > 0)) {
                        xr.Nodes = hnodes.size();

                        if (values.length < hnodes.size()) {
                            values = new double[hnodes.size()];
                        }
                        ynd = 0;

                        for (Enumeration e2 = hnodes.elements(); e2.hasMoreElements();) {
                            ExpireResult er = ((ExpireResult) e2.nextElement());
                            Result rr = null;
                            if (er != null) {
                                rr = er.r;
                            }
                            if (rr != null) {
                                int indx = rr.getIndex(para);
                                values[ynd++] = rr.param[indx];
                            }
                        }

                        fill_data(para, xr, ynd, 8, values);
                        ans.add(xr);
                    }

                }
            }
        }
        old_ans = ans;
        return ans;

    }

    void fill_data(String para, Gresult xr, int ynd, int nbin, double[] values) {

        if (ynd <= 0) {
            xr.Nodes = 0;
            xr.mean = 0;
            xr.max = 0;
            xr.min = 0;
            xr.nbin = 0;
            return;
        }

        double sum = 0;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < ynd; i++) {
            final double val = values[i];
            sum += val;
            min = Math.min(min, val);
            max = Math.max(max, val);
        }

        xr.mean = sum / ynd;
        xr.sum = sum;
        xr.max = max;
        xr.min = min;

        double pas = (max - min) / nbin;
        if (para.indexOf("Load") != -1) {
            nbin = 5;
            pas = 0.25;
            min = 0.0;
        }

        xr.hist = new int[nbin];

        for (int i = 0; i < ynd; i++) {

            int k = 0;
            double x1 = min;
            double x2 = min + pas;
            for (int j = 0; j < nbin; j++) {
                k = j;
                if ((values[i] >= x1) && (values[i] < x2)) {
                    break;
                }
                x1 = x2;
                x2 += pas;
            }
            xr.hist[k]++;
        }

    }

    /** (non-Javadoc)
     * @see lia.Monitor.Filters.GenericMLFilter#getSleepTime()
     */
    @Override
    public long getSleepTime() {
        return MF2_SLEEP_TIME;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#getFilterPred()
     */
    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    /**
     * @see lia.Monitor.Filters.GenericMLFilter#notifyResult(java.lang.Object)
     */
    @Override
    public void notifyResult(Object o) {
        if ((o != null) && (o instanceof Result)) {
            buff.add(o);
        }
    }

}
