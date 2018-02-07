package lia.util.net;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

import lia.Monitor.monitor.AppConfig;

public class TimeoutServerSocketFactory implements RMIServerSocketFactory, Serializable{
    private static final long serialVersionUID = 7787746168561037530L;

    public ServerSocket createServerSocket(int port)   throws IOException  { 
        ServerSocket ss = new ServerSocket();
        //MUST USE ss.bind() ... otherwise will not bound
        final String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");
        if(forceIP != null) {
            ss.bind(new InetSocketAddress(InetAddress.getByName(forceIP), port));
        } else {
            ss.bind(new InetSocketAddress(port));
        }
        return ss;
    }
}
