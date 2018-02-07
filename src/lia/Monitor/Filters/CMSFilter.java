package lia.Monitor.Filters;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Pattern;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

public class CMSFilter extends GenericMLFilter {

    private static final long serialVersionUID = 2183687552774193366L;
    private static final String FILTER_NAME = "CMSFilter";

    public CMSFilter(String farmName) {
        super(farmName);
    }

    /** GenericMLFilter abstract method */
    public String getName() {
        return FILTER_NAME;
    }

    /** GenericMLFilter abstract method */
    public long getSleepTime() {
        return 60 * 1000;
    }
    /** notifyResult() will be called only for data matching these predicates */
    private static final monPredicate[] MONITORED_VALUES = new monPredicate[]{new monPredicate("*", // farm
        "*", // cluster
        "*", // node
        -1, // tmin
        -1, // tmax
        new String[]{"ResidentMemory", "Events/sec"}, // parameters
        null // constraints
        )};

    /** GenericMLFilter abstract method */
    public monPredicate[] getFilterPred() {
        return MONITORED_VALUES;
    }

    /** GenericMLFilter abstract method */
    public void notifyResult(Object o) {
        if (o instanceof Result) {
            notifyResults((Result) o);
        } else if (o instanceof Collection) {
            Iterator it = ((Collection) o).iterator();

            while (it.hasNext()) {
                notifyResult(it.next());
            }
        }
    }

    // group jobs by the (beautified) cluster name
    private final HashMap hmClusters = new HashMap();

    // group jobs by the domain name (first token from the node name without the begining hostname)
    private final HashMap hmDomains = new HashMap();

    // synchronized access to the internal structures
    private final Object oLock = new Object();

    // what is a valid hostname
    private static final Pattern GOOD_HOSTNAME = Pattern.compile("^[-_a-zA-Z0-9]+(\\.[-a-zA-Z0-9]+){2,}$");

    private static final String getNiceClusterName(String sClusterName) {
        if (sClusterName.indexOf("/") >= 0) {
            sClusterName = sClusterName.substring(sClusterName.lastIndexOf("/") + 1).trim();
        }
        return sClusterName;
    }
    private static final String OTHER_DOMAINS = "OTHER_DOMAINS";

    private static final String getNiceNodeName(String sNodeName) {
        final StringTokenizer st = new StringTokenizer(sNodeName.trim().toLowerCase());

        if (!st.hasMoreTokens()) {
            return OTHER_DOMAINS;
        }
        String s = st.nextToken();

        if (!GOOD_HOSTNAME.matcher(s).matches()) {
            return OTHER_DOMAINS;
        }
        s = s.substring(s.indexOf(".") + 1); // skip hostname, leave only the domanin name

        if (s.indexOf("local") >= 0 || s.indexOf(".") < 0 || s.substring(s.lastIndexOf(".") + 1).length() > 3) {
            return OTHER_DOMAINS;
        }
        return s;
    }

    // receive notifications for the new produced data
    public void notifyResults(Result r) {
        double dVal = 0;
        for (int i = 0; i < r.param_name.length; i++) {
            if (r.param_name[i].equals("Events/sec")) {
                dVal = r.param[i];
                break;
            }

        // sum per cluster
        }
        addValueToHash(hmClusters, getNiceClusterName(r.ClusterName), r.NodeName, dVal);

        // sum per domain
        addValueToHash(hmDomains, getNiceNodeName(r.NodeName), r.NodeName, dVal);
    }

    private final void addValueToHash(HashMap hm, String sKey, String sNodeName, double dVal) {
        if (sKey == null || sNodeName == null) {
            return;
        }
        synchronized (oLock) {
            HashMap hmNodes = (HashMap) hm.get(sKey);

            if (hmNodes == null) {
                hmNodes = new HashMap();
                hm.put(sKey, hmNodes);
            }

            final Object oVal = hmNodes.get(sNodeName);
            final Double dOld = (oVal != null && (oVal instanceof Double)) ? (Double) oVal : null;

            // when a value already existed for this node do an average
            hmNodes.put(sNodeName, Double.valueOf(dOld == null ? dVal : (dOld.doubleValue() + dVal) / 2));
        }
    }

    /** GenericMLFilter abstract method */
    public Object expressResults() {
        // return value
        final Vector v = new Vector();

        synchronized (oLock) {
            parseHash(hmClusters, v, "CMSTotalsPerType");

            parseHash(hmDomains, v, "CMSTotalsPerDomain");
        }

        return v;
    }

    private final void parseHash(final HashMap hm, final Vector vReturn, final String sClusterName) {
        final Iterator it = hm.entrySet().iterator();
        final long lNow = NTPDate.currentTimeMillis();
        final Long olNow = Long.valueOf(lNow);

        Map.Entry me;
        Map.Entry me2;

        String sKey;

        HashMap hmNodes;

        Result r;

        double dSum;

        final LinkedList lRemove = new LinkedList();
        final LinkedList lRemoveClusters = new LinkedList();

        Iterator it2;

        Object oVal;

        while (it.hasNext()) {
            // for each cluster name return the total number of running jobs and the total number of processed events/second
            me = (Map.Entry) it.next();

            sKey = (String) me.getKey(); // cluster name / domain name

            hmNodes = (HashMap) me.getValue(); // actual jobs

            dSum = 0d; // total events/sec in this set

            it2 = hmNodes.entrySet().iterator();

            lRemove.clear(); // jobs to remove because they are finished

            while (it2.hasNext()) {
                me2 = (Map.Entry) it2.next(); // iterate through the nodes

                oVal = me2.getValue();

                if (oVal instanceof Double) { // if they had received a value since the last iteration, add it to the sum

                    dSum += ((Double) oVal).doubleValue();

                    // set the last known value time to the current time
                    me2.setValue(olNow);
                } else if ((oVal instanceof Long) && (lNow - ((Long) oVal).longValue() > 1000 * 60 * 5)) {
                    // a job that did not report for the last 5 minutes is considered finished

                    lRemove.add(me2.getKey());
                }
            }

            it2 = lRemove.iterator();

            // remove finished jobs
            while (it2.hasNext()) {
                hmNodes.remove(it2.next());
            }
            if (hmNodes.size() > 0) {
                r = new Result();

                r.FarmName = farm.name;
                r.ClusterName = sClusterName;
                r.NodeName = sKey;
                r.time = lNow;

                r.addSet("NoJobs", hmNodes.size());
                r.addSet("EventsPerSecond", dSum);

                vReturn.add(r);
            } else {
                lRemoveClusters.add(sKey);
            }
        }

        // remove empty clusters
        it2 = lRemoveClusters.iterator();
        while (it2.hasNext()) {
            hm.remove(it2.next());
        }
    }
}
