package lia.Monitor.monitor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DynamicThreadPoll.SchJob;

public abstract class vrvsTcpCmd extends SchJob implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1306866542811118573L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(vrvsTcpCmd.class.getName());

    //TODO - when the requests to the reflector will be no more sync 
    //This is a workaround because all the requests to the Reflector must be sync! 
    //Performance issue?!? 
    private static Object serialRequestSync = new Object();

    public MNode Node;
    public String TaskName;
    public MonModuleInfo info;
    public String cmd;
    public boolean isRepetitive = false;

    //	private boolean debug ;

    private Socket socket = null;
    BufferedInputStream buffer = null;
    InetAddress remote = null;
    int port = -1;
    OutputStreamWriter out = null;
    InputStreamReader in = null;

    public vrvsTcpCmd(String TaskName) {
        this.TaskName = TaskName;
        port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011")).intValue();
        info = new MonModuleInfo();
        //		debug = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false")).booleanValue();
    }

    public vrvsTcpCmd() {
    }

    public boolean isRepetitive() {
        return isRepetitive;
    }

    public MonModuleInfo init(MNode Node, String param) {
        this.Node = Node;
        port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011")).intValue();
        return info;
    }

    public MonModuleInfo init(MNode Node, String rem_cmd, String cmd) {

        this.Node = Node;

        port = Integer.valueOf(AppConfig.getProperty("lia.Monitor.VRVS_port", "46011")).intValue();

        return info;
    }

    public void setCmd(String cmd) {
        if ((Node == null) || (cmd == null)) {
            return;
        }
        this.cmd = cmd;
    }

    public MNode getNode() {
        return Node;
    }

    public String getClusterName() {
        return Node.getClusterName();
    }

    public String getFarmName() {
        return Node.getFarmName();
    }

    public String getTaskName() {
        return TaskName;
    }

    public BufferedReader procOutput(String cmd) {
        synchronized (serialRequestSync) {
            try {

                remote = InetAddress.getByName(Node.getIPaddress());
                socket = new Socket(remote, port);

                try {
                    socket.setSoTimeout(2000);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Cannot set SO_TIMEOUT for Reflector. Very BAD!", t);
                }

                try {
                    socket.setSoLinger(true, 1);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot set SO_LINGER for Reflector. Not so BAD!", t);
                }

                try {
                    socket.setTcpNoDelay(true);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot set TCP_NODELAY for Reflector. Not so BAD!", t);
                }

                out = new OutputStreamWriter(socket.getOutputStream(), "8859_1");
                buffer = new BufferedInputStream(socket.getInputStream());
                in = new InputStreamReader(buffer, "8859_1");

            } catch (Throwable t) {
                logger.log(Level.SEVERE, "General Exception creating socket", t);
                cleanup();
                return null;
            }

            cmd += "/end";

            try {

                out.write(cmd);
                out.flush();

                // read the result the return from the reflector
                String answer = "";
                int c = in.read();
                int nb = 0;
                while ((c > -1) && (nb < 300)) {
                    nb++;
                    //filter non-printable and non-ASCII
                    if (((c >= 32) && (c < 127)) || (c == '\t') || (c == '\r') || (c == '\n')) {
                        answer += (char) c;
                    }
                    c = in.read();
                }

                cleanup();

                return new BufferedReader(new StringReader(answer));

            } catch (Throwable t) {
                logger.log(Level.WARNING, "FAILED to execute cmd = " + cmd, t);
                cleanup();
                return null;
            }
        } //end serial request
    }

    private void cleanup() {

        try {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            if (buffer != null) {
                buffer.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Failed to clean-up streams ", t);
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to close socket! ", t);
        }

        out = null;
        in = null;
        buffer = null;
        socket = null;
    }

    @Override
    public boolean stop() {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " Stoping vrvsTcpCmd . ... destoy process..");
        }
        return true;
    }

}
