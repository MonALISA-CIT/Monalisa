package lia.Monitor.monitor;

import java.util.Vector;

public class AccountingResult implements java.io.Serializable, Comparable<AccountingResult> {

    /**
     * 
     */
    private static final long serialVersionUID = -1974965247808084271L;

    public String sGroup;

    public String sUser;

    public String sJobID;

    public final Vector<String> vsParams;

    public final Vector<Double> vnValues;

    public final long lStartTime;

    public final long lEndTime;

    public AccountingResult(String _sGroup, String _sUser, String _sJobID, long _lStartTime, long _lEndTime) {
        vsParams = new Vector<String>();
        vnValues = new Vector<Double>();

        sGroup = _sGroup;
        sUser = _sUser;
        sJobID = _sJobID;

        lStartTime = _lStartTime;
        lEndTime = _lEndTime;
    }

    public void addParam(String sParam, double d) {
        addParam(sParam, Double.valueOf(d));
    }

    public void addParam(String sParam, Number o) {
        if (o != null)
            addParam(sParam, Double.valueOf(o.doubleValue()));
    }

    public void addParam(String sParam, Double value) {
        if (sParam == null || sParam.length() <= 0 || value == null)
            return;

        if (!vsParams.contains(sParam)) {
            vsParams.add(sParam);
            vnValues.add(value);
        }
    }

    public String toString() {
        return sGroup + "/" + sUser + ": " + sJobID + " (" + lStartTime + " ... " + lEndTime + ") : " + vsParams + " -> " + vnValues;
    }

    public int compareTo(AccountingResult ar) {
        long lDiff = lStartTime - ar.lStartTime;

        if (lDiff < 0)
            return -1;
        if (lDiff > 0)
            return 1;
        return 0;
    }

}
