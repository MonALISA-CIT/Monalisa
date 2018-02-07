package lia.Monitor.Agents.OpticalPath.comm;

import lia.util.security.RSSF;


public class XDRSSLTcpServer extends XDRTcpServer {

    public XDRSSLTcpServer(int port, XDRMessageNotifier notifier) throws Exception {
        super("( ML ) XDRSSLTcpServer :- Listening on port [ " + port + " ] ", new RSSF().createServerSocket(port), notifier);
        //super ("( ML ) XDRSSLTcpServer :- Listening on port [ " + port + " ] ", new RSSF().createServerSocket(port,AppConfig.getProperty("lia.Monitor.SKeyStore"),RSSF.DEFAULT_TM), notifier);
    }
    
}
