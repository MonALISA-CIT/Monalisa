package lia.Monitor.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MLModulesUtils {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLModulesUtils.class.getName());

    public static BufferedReader TcpCmd(String host, int port, String cmd) {

        InetAddress remote = null;
        Socket socket = null;
        OutputStreamWriter out = null;
        BufferedInputStream buffer = null;
        InputStreamReader in = null;

        //Create the sock
        try {
            remote = InetAddress.getByName(host);
            socket = new Socket(remote, port);
            socket.setSoTimeout(1000);
            socket.setSoLinger(true, 1);
            socket.setTcpNoDelay(true);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception creating socket" + " for " + host + ":" + port, t);
            cleanup(socket, in, out, buffer);
            return null;
        }

        try {

            out = new OutputStreamWriter(socket.getOutputStream(), "8859_1");
            buffer = new BufferedInputStream(socket.getInputStream());
            in = new InputStreamReader(buffer, "8859_1");

        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception creating Streams" + " for " + host + ":" + port, t);
            cleanup(socket, in, out, buffer);
            return null;
        }

        try {

            out.write(cmd);
            out.flush();

            // read the result the return from the reflector
            StringBuilder answerBuff = new StringBuilder(1024);
            int c = in.read();
            int nb = 0;
            while (c > -1) {
                nb++;
                //filter non-printable and non-ASCII
                if (((c >= 32) && (c < 127)) || (c == '\t') || (c == '\r') || (c == '\n')) {

                    answerBuff.append((char) c);
                }

                c = in.read();
            }

            cleanup(socket, in, out, buffer);

            return new BufferedReader(new StringReader(answerBuff.toString()));

        } catch (Throwable t) {
            logger.log(Level.WARNING, "FAILED to execute cmd = " + cmd + " for " + host + ":" + port);
            cleanup(socket, in, out, buffer);
            return null;
        }

    }

    private static void cleanup(Socket socket, InputStreamReader in, OutputStreamWriter out, BufferedInputStream buffer) {

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

        } catch (Throwable t) {
            logger.log(Level.WARNING, " Failed to clean-up streams ", t);
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, " Failed to close socket!!!! ", t);
        }

    }
}
