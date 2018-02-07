package lia.Monitor.Filters;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.DataArray;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

/**
 * Aggregate FDT transfers. 
 * Data is produced by the monFDTClient and monFDTServer modules
 * which run FDT and generate the monitoring results.
 * 
 * If you use LISA to run FDT, then use LisaFDTFilter instead!
 * 
 * @author catac
 */
public class FDTFilter extends GenericMLFilter {
    private static final long serialVersionUID = 1L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FDTFilter.class.getName());

    /** 
     * How often I should send summarized data (given in ml.properties in seconds) 
     * Default: 1 minute
     */
    public static final long SLEEP_TIME = AppConfig.getl("lia.Monitor.Filters.FDTFilter.SLEEP_TIME", 60) * 1000;

    /** 
     * Consider a param no longer available after PARAM_EXPIRE time (given in ml.properties in seconds) 
     * Default: 35 seconds
     */
    private static long PARAM_EXPIRE = AppConfig.getl("lia.Monitor.Filters.FDTFilter.PARAM_EXPIRE", 35) * 1000;

    /** The name of this filter */
    public static final String Name = "FDTFilter";

    private final Hashtable htLinksWithTransfers; // key=linkName(w/o dir), value=hash: key=transfName; value=FDTTransfer
    private final Hashtable htFDTServers; // key=serverName, value=FDTServer

    /**
     * @param farmName
     */
    public FDTFilter(String farmName) {
        super(farmName);

        htLinksWithTransfers = new Hashtable();
        htFDTServers = new Hashtable();
    }

    @Override
    public monPredicate[] getFilterPred() {
        return null;
    }

    @Override
    public String getName() {
        return Name;
    }

    @Override
    public long getSleepTime() {
        return SLEEP_TIME;
    }

    @Override
    synchronized public void notifyResult(Object o) {
        if (o == null) {
            return;
        }
        if (o instanceof Result) {
            Result r = (Result) o;
            if (r.Module.equals("FDTFilter")) {
                return;
            }
            if (r.ClusterName.startsWith("FDT_Link_")) {
                addFDTClientData(r);
            }
            if (r.ClusterName.equals("FDT_Servers") && (!r.NodeName.equals("_TOTALS_"))) {
                addServerData(r);
            }

        } else if (o instanceof eResult) {
            eResult er = (eResult) o;
            if (er.Module.equals("FDTFilter")) {
                return;
            }
        }
    }

    // FDTClients & Transfers //////////////////////////////////////////////////////////////

    /**
     * This class identifies a FDT transfer performed by this client  
     */
    private static class FDTTransfer {

        /**
         * clientName_serverIP:port
         */
        String transfName;

        /**
         * just the clientName portion of the transfName
         */
        String clientName;

        /**
         * Name of the link
         */
        String linkName;

        /**
         * _IN or _OUT
         */
        String netDir;

        /**
         * _READ or _WRITE
         */
        String diskDir;

        /**
         * the rates received for this transfer
         */
        DataArray rates;

        /**
         * ID of this measurement
         */
        int iMeasurementID;

        /**
         * number of samples received since last summary
         */
        int iSamples;

        /**
         * 
         */
        long lastUpdateTime;

        /**
         * @param transfName
         * @param clientName
         * @param linkName
         * @param direction
         */
        public FDTTransfer(String transfName, String clientName, String linkName, String direction) {
            this.transfName = transfName;
            this.clientName = clientName;
            this.linkName = linkName;
            this.netDir = direction;
            this.diskDir = direction.equals("_IN") ? "_WRITE" : "_READ";
            rates = new DataArray();
            iSamples = 0;
            String[] params = { "NET_IN", "NET_OUT", "DISK_READ", "DISK_WRITE" };
            for (String param : params) {
                rates.setParam(param, 0);
            }
        }

        /**
         * @param r
         */
        public void addData(Result r) {
            lastUpdateTime = NTPDate.currentTimeMillis();
            for (int i = 0; i < r.param.length; i++) {
                String pName = r.param_name[i];
                double pValue = r.param[i];
                if (pName.equals("DISK_READ")) {
                    rates.addToParam("DISK" + diskDir, pValue);
                } else if (pName.equals("NET_OUT")) {
                    rates.addToParam("NET" + netDir, pValue);
                } else if (pName.equals("MeasurementID")) {
                    iMeasurementID = (int) pValue;
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Received unknown transfer parameter " + r);
                    }
                }
            }
            iSamples++;
        }

        /**
         * @return measurement ID
         */
        public int getMeasurementID() {
            return iMeasurementID;
        }

        /**
         * @param linkParams
         * @param htLinkDetails
         * @return boolean
         */
        public boolean summarize(DataArray linkParams, Hashtable htLinkDetails) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Summarizing " + toString());
            }
            long now = NTPDate.currentTimeMillis();
            // double timeInterval = SLEEP_TIME / 1000.0d;
            if ((now - lastUpdateTime) > PARAM_EXPIRE) {
                logger.log(Level.INFO, "Removing expired " + toString());
                return false;
            }
            // add the summary for this client
            DataArray clientParams = (DataArray) htLinkDetails.get(clientName);
            if (clientParams == null) {
                clientParams = new DataArray();
                htLinkDetails.put(clientName, clientParams);
            }
            rates.divParams(iSamples);
            rates.addToDataArray(clientParams);
            // add the summary for this link
            rates.addToDataArray(linkParams);
            rates.setToZero();
            iSamples = 0;

            linkParams.addToParam("active_transfers", 1);
            return true;
        }

        @Override
        public String toString() {
            return "FDTTransfer " + transfName + " on " + linkName + netDir;
        }
    }

    private void addFDTClientData(Result r) {
        String linkAndDir = r.ClusterName.substring("FDT_Link_".length()).toUpperCase();
        if (!linkAndDir.endsWith("_IN") && !linkAndDir.endsWith("_OUT")) {
            logger.log(Level.WARNING, "The name of the link should end with '_IN' or '_OUT':\n" + r);
            return;
        }
        int idx = linkAndDir.lastIndexOf('_');
        String link = StringFactory.get(linkAndDir.substring(0, idx));
        String direction = StringFactory.get(linkAndDir.substring(idx));
        idx = r.NodeName.lastIndexOf('_');
        if (idx == -1) {
            logger.log(Level.WARNING, "transfer node name should be client_ipServer:port\n" + r);
            return;
        }
        String client = StringFactory.get(r.NodeName.substring(0, idx));
        FDTTransfer transfer = findFDTTransfer(link, direction, r.NodeName, client, true);
        transfer.addData(r);
    }

    private FDTTransfer findFDTTransfer(String linkName, String direction, String transfName, String clientName,
            boolean create) {
        Hashtable htTransfers = (Hashtable) htLinksWithTransfers.get(linkName);
        if (htTransfers == null) {
            htTransfers = new Hashtable();
            htLinksWithTransfers.put(linkName, htTransfers);
        }
        FDTTransfer transfer = (FDTTransfer) htTransfers.get(transfName + direction);
        if (transfer == null) {
            transfer = new FDTTransfer(transfName, clientName, linkName, direction);
            htTransfers.put(transfName + direction, transfer);
        }
        return transfer;
    }

    private void summarizeLinks(Vector rez) {
        Hashtable htLinkDetails = new Hashtable(); // key=LisaName; value=DataArray with its params
        DataArray linkSummary = new DataArray();
        DataArray globalSummary = new DataArray();
        String[] params = { "NET_IN", "NET_OUT", "DISK_READ", "DISK_WRITE", "active_transfers" };
        for (String param : params) {
            linkSummary.setParam(param, 0);
        }
        globalSummary.setAsDataArray(linkSummary);
        globalSummary.setParam("active_links", 0);
        for (Iterator lit = htLinksWithTransfers.entrySet().iterator(); lit.hasNext();) {
            Map.Entry lme = (Map.Entry) lit.next();
            String linkName = (String) lme.getKey();
            Hashtable htTransfers = (Hashtable) lme.getValue();
            if (htTransfers.size() == 0) {
                lit.remove();
                continue;
            }
            htLinkDetails.clear();
            linkSummary.setToZero();
            for (Iterator tit = htTransfers.values().iterator(); tit.hasNext();) {
                FDTTransfer transfer = (FDTTransfer) tit.next();
                if (!transfer.summarize(linkSummary, htLinkDetails)) {
                    tit.remove();
                }
            }
            for (Iterator cit = htLinkDetails.entrySet().iterator(); cit.hasNext();) {
                Map.Entry cme = (Map.Entry) cit.next();
                String clientName = (String) cme.getKey();
                DataArray clientParams = (DataArray) cme.getValue();
                addRezFromDA(rez, "FDT_LinkDetails_" + linkName, clientName, clientParams);
            }
            addRezFromDA(rez, "FDT_LinkDetails_" + linkName, "_TOTALS_", linkSummary);
            linkSummary.addToDataArray(globalSummary);
            addRezFromDA(rez, "FDT_LinksSummary", linkName, linkSummary);
            globalSummary.addToParam("active_links", 1);
        }
        addRezFromDA(rez, "FDT_LinksSummary", "_TOTALS_", globalSummary);
    }

    // FTDServers //////////////////////////////////////////////////////////////////////////

    private static final class FDTServer {
        /**
         * 
         */
        String serverName;

        /**
         * the rates received for this server
         */
        DataArray rates;

        /**
         * the running params for this server
         */
        DataArray params;

        /**
         * 
         */
        int iSamples;

        /**
         * 
         */
        long lastUpdateTime;

        /**
         * @param serverName
         */
        public FDTServer(String serverName) {
            this.serverName = serverName;
            rates = new DataArray();
            params = new DataArray();
            iSamples = 0;
        }

        /**
         * @param r
         */
        public void addData(Result r) {
            lastUpdateTime = NTPDate.currentTimeMillis();
            for (int i = 0; i < r.param.length; i++) {
                String pName = r.param_name[i];
                double pValue = r.param[i];
                if (pName.equals("DISK_WRITE") || pName.equals("NET_IN")) {
                    rates.addToParam(pName, pValue);
                } else if (pName.equals("CLIENTS_NO")) {
                    params.setParam(pName, pValue);
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Received unknown server parameter " + r);
                    }
                }
            }
            iSamples++;
        }

        /**
         * @param globalParams
         * @return boolean
         */
        public boolean summarize(DataArray globalParams) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Summarizing " + toString());
            }
            long now = NTPDate.currentTimeMillis();
            // double timeInterval = SLEEP_TIME / 1000.0d;
            if ((now - lastUpdateTime) > PARAM_EXPIRE) {
                logger.log(Level.INFO, "Removing expired " + toString());
                return false;
            }
            // add the summary for this link
            rates.divParams(iSamples);
            rates.addToDataArray(globalParams);
            rates.setToZero();
            iSamples = 0;

            params.addToDataArray(globalParams);
            globalParams.addToParam("active_servers", 1);
            return true;
        }

        @Override
        public String toString() {
            return "FDTServer " + serverName;
        }
    }

    private void addServerData(Result r) {
        FDTServer server = (FDTServer) htFDTServers.get(r.NodeName);
        if (server == null) {
            server = new FDTServer(r.NodeName);
            htFDTServers.put(r.NodeName, server);
        }
        server.addData(r);
    }

    private void summarizeServers(Vector rez) {
        DataArray globalSummary = new DataArray();
        String[] params = { "NET_IN", "DISK_WRITE", "CLIENTS_NO", "active_servers" };
        for (String param : params) {
            globalSummary.setParam(param, 0);
        }
        for (Iterator sit = htFDTServers.values().iterator(); sit.hasNext();) {
            FDTServer server = (FDTServer) sit.next();
            if (!server.summarize(globalSummary)) {
                sit.remove();
            }
        }
        addRezFromDA(rez, "FDT_Servers", "_TOTALS_", globalSummary);
    }

    ////////////////////////////////////////////////////////////////////////////////////////

    /** 
     * Build a Result from the given DataArray into the appropriate cluster/node; 
     * also returns the result.
     * If the given DataArray is null, it returns a expire result for that node
     * If nodeName is null, it returns the expire result for that cluster
     */
    private Result addRezFromDA(Vector vrez, String clusterName, String nodeName, DataArray da) {
        if (nodeName == null) {
            eResult er = new eResult(farm.name, clusterName, null, "FDTFilter", null);
            er.time = NTPDate.currentTimeMillis();
            vrez.add(er);
            return null;
        }
        if (da == null) {
            eResult er = new eResult(farm.name, clusterName, nodeName, "FDTFilter", null);
            er.time = NTPDate.currentTimeMillis();
            vrez.add(er);
            return null;
        }
        if (da.size() != 0) {
            Result rez = new Result(farm.name, clusterName, nodeName, "monXDRUDP", da.getParameters());
            for (int i = 0; i < rez.param_name.length; i++) {
                rez.param[i] = da.getParam(rez.param_name[i]);
            }

            rez.time = NTPDate.currentTimeMillis();
            vrez.add(rez);
            return rez;
        } else {
            return null;
        }
    }

    private static void reloadConfig() {
        PARAM_EXPIRE = AppConfig.getl("lia.Monitor.Filters.FDTFilter.PARAM_EXPIRE", PARAM_EXPIRE / 1000) * 1000;
    }

    @Override
    synchronized public Object expressResults() {
        logger.log(Level.FINE, "expressResults was called");

        reloadConfig();

        Vector rez = new Vector();

        try {
            summarizeLinks(rez);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error doing summarizeLinks()", t);
        }

        try {
            summarizeServers(rez);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error doing summarizeServers()", t);
        }

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            for (Iterator rit = rez.iterator(); rit.hasNext();) {
                Result r = (Result) rit.next();
                sb.append(r.toString() + "\n");
            }
            logger.log(Level.FINEST, "Summarised results are: " + sb.toString());
        }
        return rez;
    }

}
