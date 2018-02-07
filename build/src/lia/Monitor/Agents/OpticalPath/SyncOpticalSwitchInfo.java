package lia.Monitor.Agents.OpticalPath;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  MDA .... Bad design ... or not ... if we'll use 1.5 there will be a change
 *  ... Cannot put that in OSI
 *  ask me why  
 */
class SyncOpticalSwitchInfo {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(SyncOpticalSwitchInfo.class.getName());

    private class Transaction {
        Long id;
        HashMap<OSPort, Integer> portsMap;

        Transaction(Long id, OSPort[] ports) {
            this.id = id;
            portsMap = new HashMap<OSPort, Integer>(ports.length);
            for (OSPort port : ports) {
                portsMap.put(port, backEndPortMap.get(port));
            }
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Transaction [id=").append(id).append(", portsMap=").append(portsMap).append("]");
            return builder.toString();
        }
    }

    public static final short UNKNOWN = 0;
    public static final short CALIENT = 1;
    public static final short GLIMMERGLASS = 2;

    private final HashMap<Long, Transaction> transactions;

    /**
     * Switch Name
     */
    public String name;
    /** Calient or Glimmerglass */
    public Short type;

    /**
     * Connection map
     * 
     * key - OSPort
     * value - OpticalLink
     * 
     */
    public ConcurrentHashMap<OSPort, OpticalLink> map;

    /**
     * Optical CrossConnects inside a switch
     * key - sourcePort
     */
    public ConcurrentHashMap<OSPort, OpticalCrossConnectLink> crossConnects;

    private ConcurrentHashMap<OSPort, Integer> backEndPortMap;

    /**
     * whether the OS is responding or not to TL1 commands
     */
    public AtomicBoolean isAlive;
    private static AtomicLong transactionSequencer = new AtomicLong(0);

    private SyncOpticalSwitchInfo() {
        name = null;
        map = new ConcurrentHashMap<OSPort, OpticalLink>();
        type = new Short(CALIENT);//default
        crossConnects = new ConcurrentHashMap<OSPort, OpticalCrossConnectLink>();
        isAlive = new AtomicBoolean(false);
        transactions = new HashMap<Long, Transaction>();
    }

    private synchronized Long beginTransaction(OSPort[] ports) {
        //for sure all the transaction will be sequenced in time!!!
        Long transactionID = Long.valueOf(transactionSequencer.getAndIncrement());
        transactions.put(transactionID, new Transaction(transactionID, ports));
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " beginTransaction - still have " + transactions.size()
                    + " in my transaction cache");
        }
        return transactionID;
    }

    public synchronized boolean rollback(Long transactionID) {
        if (transactionID == null) {
            logger.log(Level.WARNING, " Got a null transactionID");
            return false;
        }
        Transaction t = transactions.remove(transactionID);
        if (t == null) {
            logger.log(Level.WARNING, " No such transaction ID in the transaction map ... " + transactionID);
            return false;
        }
        for (Entry<OSPort, Integer> entry : t.portsMap.entrySet()) {
            OSPort port = entry.getKey();
            Integer state = entry.getValue();

            backEndPortMap.put(port, state);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " rollback - still have " + transactions.size() + " in my transaction cache");
        }
        return true;
    }

    //it is already commited :) - you just did not noticed in the osi.map ;) 
    public synchronized boolean commit(Long transactionID) {
        Transaction t = transactions.remove(transactionID);
        if (t == null) {
            logger.log(Level.WARNING, " No such transaction ID in the transaction map ... " + transactionID);
            return false;
        }

        for (OSPort port : t.portsMap.keySet()) {
            Integer state = backEndPortMap.get(port);

            OpticalLink ol = map.get(port);
            ol.state = state;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " commit - still have " + transactions.size() + " in my transaction cache");
        }

        return true;
    }//commit()

    /**
     * If it succeds it reurns a transactionID
     * @param port
     * @param oldStates
     * @param newState
     * @return
     */
    public Long beginTransaction(OSPort[] ports, Integer[][] oldStates, Integer[] newStates) {
        Long transactionID = null;
        try {
            transactionID = beginTransaction(ports);

            for (int i = 0; i < ports.length; i++) {
                boolean succed = false;
                for (int j = 0; j < oldStates[i].length; j++) {
                    if (backEndPortMap.replace(ports[i], oldStates[i][j], newStates[i])) {
                        succed = true;
                        break;
                    }
                }//for j

                if (!succed) {
                    rollback(transactionID);
                    return null;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc in beginTransaction", t);
            return null;
        }
        return transactionID;
    }

    public void changePortState(OSPort port, Integer newState) {
        backEndPortMap.put(port, newState);
        OpticalLink ol = map.get(port);
        ol.state = newState;
    }

    public static final SyncOpticalSwitchInfo fromOpticalSwitchInfo(OpticalSwitchInfo osi) {
        SyncOpticalSwitchInfo sosi = new SyncOpticalSwitchInfo();
        sosi.name = osi.name;
        sosi.type = osi.type;
        sosi.isAlive.set(osi.isAlive);

        if (osi.map != null) {
            sosi.map = new ConcurrentHashMap<OSPort, OpticalLink>(osi.map);
            sosi.backEndPortMap = new ConcurrentHashMap<OSPort, Integer>();
            for (Entry<OSPort, OpticalLink> entry : osi.map.entrySet()) {
                OpticalLink ol = entry.getValue();
                sosi.backEndPortMap.put(entry.getKey(), ol.state);
            }
        }

        if (osi.crossConnects != null) {
            sosi.crossConnects = new ConcurrentHashMap<OSPort, OpticalCrossConnectLink>(osi.crossConnects);
        }

        return sosi;
    }

    public static final OpticalSwitchInfo toOpticalSwitchInfo(SyncOpticalSwitchInfo sosi) {
        OpticalSwitchInfo osi = new OpticalSwitchInfo();
        osi.name = sosi.name;
        osi.type = sosi.type;
        osi.isAlive = sosi.isAlive.get();

        if (sosi.map != null) {
            osi.map = new HashMap<OSPort, OpticalLink>(sosi.map);
        }

        if (sosi.crossConnects != null) {
            osi.crossConnects = new HashMap<OSPort, OpticalCrossConnectLink>(sosi.crossConnects);
        }
        return osi;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[OpticalSwitchInfo] Name = ");
        sb.append(name);
        sb.append("\n--->ConnMap: \n");
        if ((map == null) || (map.size() == 0)) {
            sb.append("No links defined...\n");
        } else {
            for (OSPort portNo : map.keySet()) {
                sb.append(" [ ").append(portNo).append(" ---> ").append(map.get(portNo)).append(" ]\n");
            }
        }
        sb.append("\n--->END ConnMap\n");
        sb.append("\n--->Cross-Connect Links: \n");
        if ((crossConnects == null) || (crossConnects.size() == 0)) {
            sb.append("No Cross-Connect Links defined... (yet)\n");
        } else {
            for (Object name2 : crossConnects.entrySet()) {
                sb.append(name2.toString()).append("\n");
            }
        }
        sb.append("\n--->END Cross-Connect Links\n\n");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (o instanceof SyncOpticalSwitchInfo) {
            SyncOpticalSwitchInfo osi = (SyncOpticalSwitchInfo) o;
            if ((this.name == null) || (osi.name == null)) {
                return false;
            }
            if (this.name.equals(osi.name)) {
                if (this.map == null) {
                    if (osi.map == null) {
                        return true;
                    }
                    return false;
                }
                if (osi.map == null) {
                    return false;
                }
                if (osi.map.equals(this.map)) {
                    if (this.crossConnects == null) {
                        if (osi.crossConnects == null) {
                            return true;
                        }
                        return false;
                    }
                    if (osi.crossConnects == null) {
                        return false;
                    }
                    return osi.crossConnects.equals(this.crossConnects);
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }

}
