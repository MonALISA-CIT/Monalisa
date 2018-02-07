package lia.Monitor.modules;

import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.Transfer.ProtocolManager;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.util.DynamicThreadPoll.SchJob;

/**
 * Simple module to get monitoring information from the AppTransfer protocols.
 * 
 * Usage:
 * ^monAppTransfer{ParamTimeout=300,NodeTimeout=300,ClusterTimeout=300}%5
 * 
 * @author catac
 */
public class monAppTransfer extends SchJob implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -3045900020765669528L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monAppTransfer.class.getName());

    static protected String OsName = "*";
    protected MNode Node;
    protected MonModuleInfo info;
    private final String[] sResTypes = new String[0]; // dynamic
    static public String ModuleName = "monAppTransfer";
    private final Vector vResults;

    public monAppTransfer() {
        vResults = new Vector();
    }

    /**
     * Init the module info and parse the arguments
     */
    @Override
    public MonModuleInfo init(MNode node, String args) {
        this.Node = node;
        info = new MonModuleInfo();
        try {
            // don't care about arguments
            info.setState(0);
        } catch (Exception e) {
            info.setState(1);// error
        }
        info.ResTypes = sResTypes;
        info.setName(ModuleName);
        return info;
    }

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String[] ResTypes() {
        return sResTypes;
    }

    @Override
    public MNode getNode() {
        return Node;
    }

    @Override
    public String getClusterName() {
        return Node.getClusterName();
    }

    @Override
    public String getFarmName() {
        return Node.getFarmName();
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public String getTaskName() {
        return info.getName();
    }

    @Override
    public Object doProcess() throws Exception {
        // return the cache monitoring information received from FDT
        vResults.clear();
        try {
            ProtocolManager.getInstance().getMonitorInfo(vResults);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed getting monitor info", t);
        }
        Level logLevel = vResults.size() > 0 ? Level.FINE : Level.FINER;
        if (logger.isLoggable(logLevel)) {
            StringBuilder sbRes = new StringBuilder("Publishing " + vResults.size()
                    + " results from AppTransfer protocols:");
            for (Iterator rit = vResults.iterator(); rit.hasNext();) {
                sbRes.append("\n").append(rit.next());
            }
            logger.log(logLevel, sbRes.toString());
        }
        return vResults;
    }
}
