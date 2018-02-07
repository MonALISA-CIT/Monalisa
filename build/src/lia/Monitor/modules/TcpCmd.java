package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TcpCmd {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TcpCmd.class.getName());

    private Socket socket = null;
    private OutputStreamWriter out = null;
    private BufferedReader buffer = null;
    private String host = null;
    private int port = 0;
    private int localPort = -1;
    private String cmd = null;
    private final AtomicBoolean cleaned;

    public TcpCmd(String host, int port, String cmd) {
        this.cleaned = new AtomicBoolean(false);
        this.host = host;
        this.port = port;
        this.cmd = cmd;
    }

    public BufferedReader execute(int timeout) {
        //Create the sock
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            socket.setSoLinger(true, 1);
            socket.setTcpNoDelay(true);
            localPort = socket.getLocalPort();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception creating socket", t);
            cleanup();
            return null;
        }

        try {

            out = new OutputStreamWriter(socket.getOutputStream(), "8859_1");
            buffer = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception creating Streams", t);
            cleanup();
            return null;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " [ TcpCmd ] Sending remote CMD [ " + host + ":" + port + ":-" + localPort
                    + " Cmd = " + cmd + " ]");
        }

        try {
            out.write(cmd);
            out.flush();
            return buffer;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "FAILED to execute cmd = " + cmd, t);
            cleanup();
            return null;
        }
    }

    public BufferedReader execute() {
        return execute(10000);
    }

    public void cleanup() {

        //allow multiple calls
        if (!cleaned.getAndSet(true)) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ TcpCmd ] cleanup() for [ " + host + ":" + port + ":-" + localPort
                        + " Cmd = " + cmd + " ]");
            }

            try {
                if (out != null) {
                    out.close();
                }
                if (buffer != null) {
                    buffer.close();
                }

            } catch (Throwable ignore) {
            }

            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Throwable ignore) {
            }
        }

    }
}
