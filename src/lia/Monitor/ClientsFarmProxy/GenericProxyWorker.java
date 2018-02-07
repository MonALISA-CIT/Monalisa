/*
 * $Id: GenericProxyWorker.java 7372 2013-04-04 15:38:50Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import lia.Monitor.ClientsFarmProxy.Monitor.InfoToFile;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.monMessage;
import lia.Monitor.monitor.tcpConnNotifier;
import lia.util.UUID;

/**
 * Simple Interface to unify Farm and Client Message processing
 * 
 * @author ramiro
 */
public abstract class GenericProxyWorker implements tcpConnNotifier {

    protected final ProxyTCPWorker ptw;

    protected AtomicLong msgsLastHourContor = new AtomicLong(0L);

    protected long lastUpdateFileInfo = 0;

    protected final ConcurrentSkipListSet<UUID> appCtrlSessionSet = new ConcurrentSkipListSet<UUID>();

    protected final InfoToFile infoToFile;

    protected GenericProxyWorker(ProxyTCPWorker ptw) { // ClientWorker
        lastUpdateFileInfo = System.currentTimeMillis();
        infoToFile = InfoToFile.getInstance();
        this.ptw = ptw;
    }

    public long getSentBytes() {
        if ((ptw != null) && (ptw.conn != null)) {
            return ptw.conn.getSentBytes();
        }
        return 0;
    } // getSentBytes

    public long getConfSentBytes() {
        if ((ptw != null) && (ptw.conn != null)) {
            return ptw.conn.getConfSentBytes();
        }
        return 0;
    } // getConfSentBytes

    public void sendMsg(monMessage msg) {
        msgsLastHourContor.getAndIncrement();
        processMsg(msg);
    } // sendMsg

    public void sendMsg(MonMessageClientsProxy msg) {
        msgsLastHourContor.getAndIncrement();
        processMsg(msg);
    } // sendMsg

    public abstract boolean commInit();

    public abstract void processMsg(monMessage o);

    public abstract void processMsg(MonMessageClientsProxy o);

    public ConcurrentSkipListSet<UUID> getAppCtrlSessionSet() {
        return appCtrlSessionSet;
    }

    public void addAppCtrlSession(final UUID sessionID) {
        appCtrlSessionSet.add(sessionID);
    }

    public boolean removeAppCtrlSession(final UUID sessionID) {
        return appCtrlSessionSet.remove(sessionID);
    }

    public void stopIt() {
        if (ptw != null) {
            ptw.stopIT();
        }
    }
}
