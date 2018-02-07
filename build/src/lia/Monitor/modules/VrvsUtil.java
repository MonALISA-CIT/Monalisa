package lia.Monitor.modules;

import java.io.BufferedReader;

public final class VrvsUtil {

    private final static Object syncConnLocker = new Object();

    /**
     * 
     * Synchronized version of TcpCmd. 
     *  
     * @param host
     * @param port
     * @param cmd
     * @return
     */
    public static BufferedReader syncTcpCmd(String host, int port, String cmd) {

        synchronized (syncConnLocker) {

            return MLModulesUtils.TcpCmd(host, port, cmd);

        }

    }//end syncTcpCmd

    public static Object getReflLock() {
        return syncConnLocker;
    }
}