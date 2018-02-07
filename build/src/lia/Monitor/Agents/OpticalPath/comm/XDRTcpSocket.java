package lia.Monitor.Agents.OpticalPath.comm;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;


public class XDRTcpSocket extends XDRGenericComm {
    
    private Socket socket;
    private boolean closed;
    
    public XDRTcpSocket(Socket s, XDRMessageNotifier notifier) throws IOException{
        super("XDRTcpSocket for [ " + s.getInetAddress() + ":" +s.getPort() + " ] ", 
                new XDROutputStream(s.getOutputStream()),
                new XDRInputStream(s.getInputStream()), 
                notifier);
        this.socket = s;
        closed = false;
    }
    
    public int getPort() {
        return socket.getPort();
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return socket.getLocalAddress();
    }

    public void close() {
        try {
            super.close();
            if(!closed) {//allow multiple invocation for close()
                closed = true;
                if(socket != null) {
                    socket.close();
                }
            }
        }catch(Throwable t){
            t.printStackTrace();
        }
    }

}

