package lia.util.security;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import lia.Monitor.monitor.AppConfig;


public class OSRCSF
	implements RMIClientSocketFactory, Serializable {

        /** maximum time to connect with the other endPoint */ 
         private static final int CONNECT_TIMEOUT = 15 * 1000; //15s

        /** maximum time to wait for data */
        private static final int SO_TIMEOUT = 10 * 1000; //20s

        String store=null;
        String passwd ="monalisa";

        public OSRCSF(){}

        public OSRCSF(String keyStoreName )  {
        if (  keyStoreName == null ) {
          store=AppConfig.getProperty("lia.Monitor.KeyStore"); 
        } else {
           store=keyStoreName;
        }
}
        public int hashCode() {
            int retHash = 0;
            
            try {
                
                String sstore=AppConfig.getProperty("lia.Monitor.KeyStore");
                String spasswd = AppConfig.getProperty("lia.Monitor.KeyStorePass");
                
                if(sstore != null) {
                    retHash += sstore.hashCode();
                }
                
                if(spasswd != null) {
                    retHash += spasswd.hashCode();
                }
                
                return retHash;
            }catch(Throwable t) {
                
            }
            
            return super.hashCode();
        }
        
        public boolean equals(Object o) {
            return this.getClass() == o.getClass();
        }

        public Socket createSocket(String host, int port) throws IOException {
        KeyManagerFactory kmf;
        KeyStore ks;
//        TrustManagerFactory tmf ;
        SSLSocketFactory factory = null;
        SSLContext ctx;

         store=AppConfig.getProperty("lia.Monitor.KeyStore");
         passwd = AppConfig.getProperty("lia.Monitor.KeyStorePass");
            try {
               kmf = KeyManagerFactory.getInstance("SunX509");
//               tmf = TrustManagerFactory.getInstance("SunX509");
               ks = KeyStore.getInstance("JKS");

               ks.load(new FileInputStream(store), passwd.toCharArray());

               kmf.init(ks ,passwd.toCharArray());
//               tmf.init(ks);
	 		 TrustManager[] tms = {new FarmMonitorTrustManager()};
              ctx = SSLContext.getInstance("TLS");
              ctx.init(kmf.getKeyManagers(), tms, null);
              factory = ctx.getSocketFactory();
            } catch (Exception e) {
              throw new IOException(e.getMessage());
            }

 	      SSLSocket socket = (SSLSocket)factory.createSocket();
          socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
//          socket.setSoTimeout(SO_TIMEOUT);
		return socket;
	}

}
