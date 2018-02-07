package lia.util.fdt.xdr;

import hep.io.xdr.XDRInputStream;
import hep.io.xdr.XDROutputStream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public abstract class XDRTcpSocket extends XDRGenericComm {

    private final Socket rawSocket;
    private boolean closed;

    /*	public static final int SERVER_MODE = 0;
    	public static final int CLIENT_MODE = 1;
    	private int mode = CLIENT_MODE;
    */

    public XDRTcpSocket(Socket s) throws IOException {
        super("XDRTcpSocket for [ " + s.getInetAddress() + ":" + s.getPort() + " ] ", new XDROutputStream(
                s.getOutputStream()), new XDRInputStream(s.getInputStream()));
        this.rawSocket = s;
        closed = false;
        /*this.mode = mode;
        this.auth = auth;*/
    }

    /*@Override
    protected void initSession() throws Exception {
    	if (auth == null ) {
    		if (logger.isLoggable(Level.WARNING))
    			logger.log(Level.WARNING, " No Authenticator in place.... We'll not perform authentication phase");
    		return;
    	}
    	if (mode == SERVER_MODE)
    		auth.acceptSession(rawSocket);
    	else
    		auth.initSession(rawSocket);
    }*/

    public int getPort() {
        return rawSocket.getPort();
    }

    public int getLocalPort() {
        return rawSocket.getLocalPort();
    }

    public InetAddress getInetAddress() {
        return rawSocket.getInetAddress();
    }

    public InetAddress getLocalAddress() {
        return rawSocket.getLocalAddress();
    }

    @Override
    public void close() {
        try {
            super.close();
            if (!closed) {// allow multiple invocation for close()
                closed = true;
                if (rawSocket != null) {
                    rawSocket.close();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
