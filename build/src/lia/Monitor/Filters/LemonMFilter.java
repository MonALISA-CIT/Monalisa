/*
 * $Id: LemonMFilter.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.Filters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.DateFileWatchdog;

/**
 * 
 * Compacter for "Lemon" monitoring tool
 * 
 * @author ramiro
 * 
 */
public class LemonMFilter extends GenericMLFilter implements Observer {

    /**
     * 
     */
    private static final long serialVersionUID = 114661413193989891L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(LemonMFilter.class.getName());

    public static final String Name = "LemonMFilter";
    public static long MF2_SLEEP_TIME;

    //consider a param no longer aveilable after PARAM_EXPIRE time
    private static long PARAM_EXPIRE;

    private DateFileWatchdog dfw;
    private File confFile;
    private boolean gotFirstRez;

    private final static String defaultInterestedParams[] = new String[] { "Load", "ReadRate", "WriteRate" };
    private long filterStime;
    private long startDelay;
    private boolean simulate = false;
    private boolean dumpResults = false;

    String interestedParams[] = null;
    HashMap remapParams;

    static {
        MF2_SLEEP_TIME = 30 * 1000;
        try {
            MF2_SLEEP_TIME = Long.valueOf(
                    AppConfig.getProperty("lia.Monitor.Filters.LemonMFilter.SLEEP_TIME", "60000").trim()).longValue();
        } catch (Throwable t1) {
            MF2_SLEEP_TIME = 30 * 1000;
        }

        PARAM_EXPIRE = 2 * 600000;

        try {
            PARAM_EXPIRE = Long.valueOf(AppConfig.getProperty("lia.Monitor.Filters.PARAM_EXPIRE", "1200")).longValue() * 1000;
        } catch (Throwable t) {
            PARAM_EXPIRE = 2 * 600000;
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

    public LemonMFilter(String farmName) {
        super(farmName);
        buff = new Vector();
        filterStime = 0;
        startDelay = 2 * 60 * 1000;
        buff1 = new Vector();
        hs = new Hashtable();
        gotFirstRez = false;
        clust_ref = new Hashtable();
        interestedParams = defaultInterestedParams;
        remapParams = new HashMap();

        try {
            String sConfFile = AppConfig.getProperty("lia.Monitor.Filters.LemonMFilter.CONF_FILE", null);
            if (sConfFile != null) {
                confFile = new File(sConfFile.trim());
                dfw = DateFileWatchdog.getInstance(confFile, 5 * 1000);
                dfw.addObserver(this);
            } else {
                confFile = null;
                dfw = null;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exc", t);
            confFile = null;
            dfw = null;
        }

        if (confFile != null) {
            reloadFilterConf();
        } else {
            logger.log(Level.WARNING, Name + " ... No conf file for filter ... using defaultInterestedParams");
        }
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
                            if (endLease < System.currentTimeMillis()) {
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, Name + " Removing node [ " + key + " ] for [ " + clus + ", "
                                            + para + " ] ");
                                }
                                hnodes.remove(key);
                            }
                        }
                    }
                    if ((hnodes != null) && (hnodes.size() == 0)) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, Name + " Removing param [ " + para + " ] for all nodes! ");
                        }
                        hparam.remove(para);
                    }
                }
                if ((hparam != null) && (hparam.size() == 0)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, Name + " Removing cluster [ " + clus + " ] for all params! ");
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

        if ((r.ClusterName.indexOf("MonaLisa") != -1) || (r.ClusterName.indexOf("Master") != -1)) {
            return;
        }
        if (!hs.containsKey(r.ClusterName)) {
            hs.put(r.ClusterName, new Hashtable());
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, Name + " Adding Cluster: [ " + r.ClusterName + "] r = " + r);
            }
            clust_ref.put(r.ClusterName, farm.getCluster(r.ClusterName));
        }
        Hashtable parameters = (Hashtable) hs.get(r.ClusterName);

        for (int l = 0; l < r.param_name.length; l++) {
            for (String para : interestedParams) {
                if (r.param_name[l].indexOf(para) != -1) {
                    if (!parameters.containsKey(r.param_name[l])) {
                        parameters.put(r.param_name[l], new Hashtable());
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, Name + " Adding Param: " + r.param_name[l]);
                        }
                    }
                    Hashtable nodes = (Hashtable) parameters.get(r.param_name[l]);
                    nodes.put(r.NodeName, new ExpireResult(r, System.currentTimeMillis() + PARAM_EXPIRE));
                }
            }//kk
        } //l
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

        if ((v != null) && (v.size() > 0)) {
            if (dumpResults) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n").append("dumpRez: [ " + v.size() + " ] GResults").append("\n");
                for (int i = 0; i < v.size(); i++) {
                    sb.append("\n").append("GR [ ").append(i).append("] = ")
                            .append(GResult2String((Gresult) v.elementAt(i)));
                }
                logger.log(Level.INFO, sb.toString());
            }
            if ((filterStime > 0) && (filterStime < System.currentTimeMillis())) {
                if (!simulate) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER,
                                " Returning [ " + ((v == null) ? "v == null" : "v.size() == " + v.size()) + " ]");
                    }
                    return v;
                }
                logger.log(Level.INFO, "\n\n SIMULATE == true Not sending THE results ... ");
                return null;
            }
        }
        logger.log(Level.INFO, "\n\n Not sending YET the results .... Waiting to fill the hs to grow [ "
                + ((v == null) ? "v == null" : "v.size() == " + v.size()) + " ]");
        return null;
    }

    private static String GResult2String(Gresult gr) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("@ " + new Date(gr.time) + " C: " + gr.ClusterName + " P: " + gr.Module);
        return sb.toString();
    }

    public Vector myExpressResults() {
        Vector ans = new Vector();
        double[] values = null;
        int ynd = 0;

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " myExpressResults hs.size() [ " + hs.size() + " ] ! ");
        }
        for (Enumeration e = hs.keys(); e.hasMoreElements();) {
            String clus = (String) e.nextElement();
            int NoOfNodes = ((MCluster) clust_ref.get(clus)).getNodes().size();
            Hashtable hparam = (Hashtable) hs.get(clus);
            if ((hparam != null) && (hparam.size() > 0)) {
                for (Enumeration e1 = hparam.keys(); e1.hasMoreElements();) {

                    String para = (String) e1.nextElement();

                    Gresult xr = new Gresult(farmName, clus, para);
                    xr.TotalNodes = NoOfNodes;
                    xr.time = System.currentTimeMillis();
                    Hashtable hnodes = (Hashtable) hparam.get(para);

                    if ((hnodes != null) && (hnodes.size() > 0)) {
                        xr.Nodes = hnodes.size();

                        if ((values == null) || (values.length < hnodes.size())) {
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
                        if (remapParams != null) {
                            String newPara = (String) remapParams.get(para);
                            if ((newPara != null) && (newPara.trim().length() > 0)) {
                                xr.Module = newPara.trim();
                            }
                        }
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
            if (!gotFirstRez) {
                gotFirstRez = true;
                filterStime = System.currentTimeMillis() + startDelay;
                logger.log(Level.INFO, Name + " Setting Stime = " + new Date(filterStime));
            }
            buff.add(o);
        }
    }

    private void reloadFilterConf() {
        try {
            Properties p = new Properties();
            FileInputStream fis = new FileInputStream(confFile);
            p.load(fis);
            fis.close();

            try {
                startDelay = Long.valueOf(p.getProperty("lia.Monitor.Filters.LemonMFilter.DelayStart", "120"))
                        .longValue() * 1000;
            } catch (Throwable t1) {
                startDelay = 120 * 1000;
            }
            try {
                simulate = Boolean.valueOf(p.getProperty("lia.Monitor.Filters.LemonMFilter.simulate", "false"))
                        .booleanValue();
            } catch (Throwable t1) {
                simulate = false;
            }

            try {
                dumpResults = Boolean.valueOf(p.getProperty("lia.Monitor.Filters.LemonMFilter.dumpResults", "false"))
                        .booleanValue();
            } catch (Throwable t1) {
                dumpResults = false;
            }

            logger.log(Level.INFO, " simulate == " + simulate + " dumpResults == " + dumpResults);

            String newIParam = p.getProperty("lia.Monitor.Filters.LemonMFilter.PARAMS", null);
            if (newIParam == null) {
                interestedParams = defaultInterestedParams;
                logger.log(Level.WARNING, Name
                        + " reloadFilterConf newIParam == null Setting defaultInterestedParams!!!!!!!!");
                return;
            }

            newIParam = newIParam.trim();
            if (newIParam.length() == 0) {
                interestedParams = defaultInterestedParams;
                logger.log(Level.WARNING, Name
                        + " reloadFilterConf newIParam.size() == 0 Setting defaultInterestedParams!!!!!!!!");
                return;
            }

            String tokens[] = newIParam.split("(\\s)*,(\\s)*");
            if ((tokens == null) || (tokens.length == 0)) {
                interestedParams = defaultInterestedParams;
                logger.log(Level.WARNING, Name
                        + " reloadFilterConf tokens.size() == 0 Setting defaultInterestedParams!!!!!!!!");
                return;
            }

            Vector np = new Vector(tokens.length);
            for (String token : tokens) {
                if (token != null) {
                    String pp = token.trim();
                    if (pp.length() > 0) {
                        np.add(pp);
                    }
                }
            }

            if (np.size() == 0) {
                interestedParams = defaultInterestedParams;
                logger.log(Level.WARNING, Name
                        + " reloadFilterConf newTokens.size() == 0 Setting defaultInterestedParams!!!!!!!!");
                return;
            }

            interestedParams = (String[]) np.toArray(new String[np.size()]);

            if (logger.isLoggable(Level.FINE)) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n\n" + Name + " reloadFilterConf(): Setting new interestedParams: ");
                for (int i = 0; i < interestedParams.length; i++) {
                    sb.append(interestedParams[i]);
                    if (i < (interestedParams.length - 1)) {
                        sb.append(",");
                    } else {
                        sb.append("\n\n");
                    }
                }
                logger.log(Level.FINE, sb.toString());
            }

            BufferedReader br = null;

            HashMap newHmp = new HashMap();
            try {
                br = new BufferedReader(new FileReader(confFile));
                String line = br.readLine();
                boolean remapStarted = false;
                for (; line != null; line = br.readLine()) {
                    try {
                        line = line.trim();
                        if (line.length() == 0) {
                            continue;
                        }
                        if (line.startsWith("#") && !line.startsWith("#RemapParam")) {
                            continue;
                        }

                        if (line.startsWith("#RemapParam")) {
                            remapStarted = true;
                            continue;
                        }

                        if (!remapStarted) {
                            continue;
                        }

                        String ltokens[] = line.split("((\\s)*(=)(\\s)*|(\\s)+)");

                        if ((ltokens == null) || (ltokens.length < 2)) {
                            continue;
                        }

                        if ((ltokens[0] == null) || (ltokens[1] == null)) {
                            continue;
                        }

                        String key = ltokens[0].trim();
                        String value = ltokens[1].trim();

                        if ((key.length() == 0) || (value.length() == 0)) {
                            continue;
                        }

                        logger.log(Level.INFO, Name + "reloadFilterConf(): Adding remap key ( " + key + "," + value
                                + " )");
                        newHmp.put(key, value);

                    } catch (Throwable t) {
                        logger.log(Level.WARNING, Name + "Ignoring line [" + line + "]", t);
                    }
                }
                br.close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, Name + "Got Exc parsing conf", t);
            }
            remapParams = newHmp;
        } catch (Throwable t) {
            logger.log(Level.WARNING, Name + " got exc", t);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((o != null) && o.equals(dfw)) {
            reloadFilterConf();
        }
    }

}
