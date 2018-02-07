package lia.Monitor.Agents.OpticalPath.comm;

import java.net.ServerSocket;
import java.net.Socket;



public class XDRTcpServer extends Thread {

    ServerSocket ss = null;
    private boolean hasToRun;
    private int port;
    XDRMessageNotifier notifier;
    
    public XDRTcpServer(int port, XDRMessageNotifier notifier) throws Exception {
        super("( ML ) XDRTcpServer :- Listening on port [ " + port + " ] ");
        this.port = port;
        ss = new ServerSocket(port);
        this.notifier = notifier;
        hasToRun = true;
    }
    
    public XDRTcpServer(ServerSocket ss, XDRMessageNotifier notifier) throws Exception {
        this(null, ss, notifier);
    }

    public XDRTcpServer(String name, ServerSocket ss, XDRMessageNotifier notifier) throws Exception {
        super((name==null)?"( ML ) XDRTcpServer :- Listening on port [ " + ss.getLocalPort() + " ] ":name);
        this.ss = ss;
        this.notifier = notifier;
        hasToRun = true;
    }

    public void run() {
        System.out.println("XDRTcpServerSocket entering main loop ... listening on port " + port);
        while(hasToRun) {
            try {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                new XDRTcpSocket(s, notifier).start();
            }catch(Throwable t){
                t.printStackTrace();
            }
        }
    }
    
}
