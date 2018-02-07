package lia.Monitor.monitor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.MLProcess;
import lia.util.DynamicThreadPoll.SchJob;

/*
 Abstract class to be used as a template for
 executing a remote shell command 
 */

public abstract class cmdExec extends SchJob implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -6263440238783362125L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(cmdExec.class.getName());

    public MNode Node;
    public String TaskName;
    public MonModuleInfo info;
    public String full_cmd;
    public boolean isRepetitive = false;
    public Process pro = null;

    private InputStream out = null;
    private InputStream err = null;

    public cmdExec(String TaskName) {
        this.TaskName = TaskName;
        info = new MonModuleInfo();
        info.name = TaskName;
    }

    public cmdExec() {
    }

    public boolean isRepetitive() {
        return isRepetitive;
    }

    public MonModuleInfo init(MNode Node, String param) {
        this.Node = Node;
        return info;
    }

    public MonModuleInfo init(MNode Node, String rem_cmd, String cmd) {

        this.Node = Node;
        if (rem_cmd == null) {
            full_cmd = cmd; // local
        } else if (rem_cmd.equals("rsh")) {
            full_cmd = "rsh " + Node.getIPaddress() + " " + cmd;
        } else {
            full_cmd = "ssh " + rem_cmd + "@" + Node.getIPaddress() + " " + cmd;
        }
        return info;
    }

    public String[] ResTypes() {
        return (info == null) ? null : info.ResTypes;
    }

    public void setCmd(String rem_cmd, String cmd) {
        if (Node == null) {
            return;
        }

        if (rem_cmd == null) {
            full_cmd = cmd; // local
        } else if (rem_cmd.equals("rsh")) {
            full_cmd = "rsh " + Node.getIPaddress() + " " + cmd;
        } else {
            full_cmd = "ssh " + rem_cmd + "@" + Node.getIPaddress() + " " + cmd;
        }
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

    public void cleanup() {

        try {
            if (pro != null) {
                pro.waitFor();
            }
        } catch (Throwable ignore) {
        }

        try {
            if (pro != null) {
                pro.destroy();
            }
        } catch (Throwable ignore) {
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (Throwable t) {
        }

        try {
            if (err != null) {
                err.close();
            }
        } catch (Throwable t) {
        }

        pro = null;
        out = null;
        err = null;
    }

    public BufferedReader procOutput(String cmd) {
        return procOutput(cmd, -1);
    }

    public BufferedReader procOutput(String cmd, long delay) {
        try {
            if (delay < 0) {
                pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd });
            } else {
                pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd }, delay);
            }
            out = pro.getInputStream();
            return new BufferedReader(new InputStreamReader(out));
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " FAILED to execute cmd = " + cmd, t);
            }
        }

        cleanup();

        return null;
    }

    /**
     * Executes a command and gets the standard output and the standard error. The default
     * delay value is used to wait for the command to finish (3 min).
     * @param cmd The command to be executed.
     * @return Array containing the std output and the std error.
     */
    public BufferedReader[] procCmdStreams(String cmd) {
        return procCmdStreams(cmd, -1);
    }

    /**
     * Executes a command and gets the standard output and the standard error.
     * @param cmd The command to be executed.
     * @param delay The amount of time (in ms) we wait for the command to finish.
     * @return Array containing the std output and the std error.
     */
    public BufferedReader[] procCmdStreams(String cmd, long delay) {
        BufferedReader[] ret = new BufferedReader[2];

        try {
            if (delay < 0) {
                pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd });
            } else {
                pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd }, delay);
            }
            out = pro.getInputStream();
            ret[0] = new BufferedReader(new InputStreamReader(out));

            err = pro.getErrorStream();
            ret[1] = new BufferedReader(new InputStreamReader(err));

            return ret;
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " FAILED to execute cmd = " + cmd, t);
            }
        }

        cleanup();

        return null;
    }

    @Override
    public boolean stop() {
        cleanup();
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Stoping cmdExec . ... destroy process..");
        }

        return true;
    }

}