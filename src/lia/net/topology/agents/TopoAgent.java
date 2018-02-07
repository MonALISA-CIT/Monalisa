/*
 * Created on Feb 21, 2011
 */
package lia.net.topology.agents;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lia.Monitor.Agents.AbstractAgent;
import lia.Monitor.monitor.AppConfig;
import lia.net.topology.agents.conf.RawConfigInterface;
import lia.net.topology.agents.conf.RawConfigNotifier;



/**
 *
 * @author ramiro
 */
public abstract class TopoAgent<C extends RawConfigInterface<P>, P> extends AbstractAgent implements RawConfigNotifier<P>{

    /**
     * 
     */
    private static final long serialVersionUID = -167700179155304046L;
    protected static final String TOPO_CONFIG_CLUSTER = AppConfig.getProperty("lia.Monitor.Farm.TopoConfigHiddenCluster", "TopoConfigHCluster");
    protected static final transient boolean STANDALONE = AppConfig.getb("lia.net.topology.agents.TopoAgent.STANDALONE", false);
    protected static final transient boolean SIMULATED = AppConfig.getb("lia.net.topology.agents.TopoAgent.SIMULATED", false);

    protected final C config;

    //TODO - check if we have to run multiple things in parallel
    protected final transient ScheduledExecutorService monitorExec = Executors.newSingleThreadScheduledExecutor();

    //does not have to volatile; worst case scenario we precompute a few times
    private String farmName;
    
    public TopoAgent(String agentName, String agentGroup, String farmID, C config){
        super(agentName, agentGroup, farmID);
        this.config = config;
        this.config.addNotifier(this);
    }

    @Override
    public abstract void addNewResult(Object r);

    @Override
    public abstract void doWork();

    @Override
    public abstract void processMsg(Object msg);
    
    public String getFarmName() {
        final String fFname = this.farmName;
        if(fFname != null) {
            return fFname;
        }
        
        if (!STANDALONE && (farm == null || farm.name == null)) {
            while (farm == null || farm.name == null) {
                try {
                    Thread.sleep(500);
                } catch (Throwable t) {
                    // ignore InterruptedException
                }
            }
        }
        final String FarmName = (STANDALONE) ? "AFOX_TEST" : farm.name;
        this.farmName = FarmName;
        
        return FarmName;
    }
}
