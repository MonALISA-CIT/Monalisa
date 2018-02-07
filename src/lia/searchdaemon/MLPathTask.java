package lia.searchdaemon;

import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.searchdaemon.comm.XDRAbstractComm;
import lia.searchdaemon.comm.XDRMessage;

public class MLPathTask implements Runnable {

    private static final Logger logger = Logger.getLogger(MLPathTask.class.getName());

    /**
     * messages to be sent to agents.
     */
    private final ConcurrentHashMap toAgent;

    /**
     * messages to be sent to client shell.
     */
    private final ConcurrentHashMap toCMD;

    private boolean taskHasToRun;

    String key;

    /**
     * communication with the client from shell.
     */
    XDRAbstractComm clientComm;

    /**
     * communication with agent on a tcp connection.
     */
    XDRAbstractComm agentComm;

    private final Object messagesInQ = new Object();

    MLPathTask(String key, XDRAbstractComm comm, XDRAbstractComm agentComm) {
        this.key = key;
        taskHasToRun = true;
        this.agentComm = agentComm;
        toAgent = new ConcurrentHashMap();
        toCMD = new ConcurrentHashMap();
        clientComm = comm;
    }

    public void stopIt() {
        taskHasToRun = false;
    }

    @Override
    public void run() {
        while (taskHasToRun) {
            synchronized (messagesInQ) {
                while (taskHasToRun && (toAgent.size() == 0) && (toCMD.size() == 0)) {
                    try {
                        messagesInQ.wait();
                    } catch (Exception ex) {

                    }
                }
            }//synch

            if (toAgent.size() > 0) {
                for (Enumeration en = toAgent.keys(); en.hasMoreElements();) {
                    Object keyA = en.nextElement();
                    XDRMessage xdrMsg = (XDRMessage) toAgent.get(keyA);
                    xdrMsg.id = this.key;
                    try {
                        agentComm.write(xdrMsg);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception sending to Agent", t);
                    }
                    toAgent.remove(keyA);
                }
            }

            if (toCMD.size() > 0) {
                for (Enumeration en = toCMD.keys(); en.hasMoreElements();) {
                    Object keyA = en.nextElement();
                    XDRMessage xdrMsg = (XDRMessage) toCMD.get(keyA);
                    try {
                        clientComm.write(xdrMsg);
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception sending to Agent", t);
                    }
                    toCMD.remove(keyA);
                }
            }
        }
        System.out.println(" MLPathTask exits run() " + key);
    }

    public void notifyClosed() {
        synchronized (messagesInQ) {
            this.taskHasToRun = false;
            messagesInQ.notify();
        }
    }

    public void notify(XDRMessage xdrMessage, XDRAbstractComm comm) {
        synchronized (messagesInQ) {
            if (comm == agentComm) {
                toCMD.put(comm, xdrMessage);
            } else {
                toAgent.put(comm, xdrMessage);
            }
            messagesInQ.notify();
        }
    }
}
