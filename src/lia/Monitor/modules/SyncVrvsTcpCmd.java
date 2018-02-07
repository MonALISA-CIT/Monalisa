package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.util.DynamicThreadPoll.SchJob;

public abstract class SyncVrvsTcpCmd extends SchJob implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -6453707175069289239L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(SyncVrvsTcpCmd.class.getName());

    public MNode Node;
    public String TaskName;
    public MonModuleInfo info;
    public static boolean isRepetitive = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false"))
            .booleanValue();

    private boolean debug;

    public SyncVrvsTcpCmd(String TaskName) {
        this.TaskName = TaskName;
        info = new MonModuleInfo();
        canSuspend = false;
    }

    public SyncVrvsTcpCmd() {
        isRepetitive = true;
        canSuspend = false;
    }

    public boolean isRepetitive() {
        isRepetitive = true;
        return isRepetitive;
    }

    public MonModuleInfo init(MNode Node, String param) {
        this.Node = Node;
        return info;
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

    public BufferedReader procOutput(int port, String cmd) {
        return VrvsUtil.syncTcpCmd(Node.getName(), port, cmd);
    }

    @Override
    public boolean stop() {
        logger.log(Level.WARNING, " Stoping SyncVrvsTcpCmd. ");
        return true;
    }

}