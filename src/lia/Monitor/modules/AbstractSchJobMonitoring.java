/*
 * Created on May 17, 2010
 */
package lia.Monitor.modules;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

/**
 * 
 * @author ramiro
 */
public abstract class AbstractSchJobMonitoring extends SchJob implements MonitoringModule {
	
	private static final String OS_NAME = System.getProperty("os.name");

	private static final boolean IS_LINUX_OS;
	
	private static final boolean IS_SOLARIS_OS;
	
	private static final boolean IS_MAC_OS;

	static {
		boolean isLinux = true;
		
		boolean isSolaris = false;
		
		boolean isMacOS = false;
		
		try {
			isLinux = OS_NAME.toLowerCase().indexOf("linux") >= 0;
			
			isSolaris = OS_NAME.toLowerCase().startsWith("sun") || OS_NAME.toLowerCase().startsWith("oracle");
			
			isMacOS = OS_NAME.toLowerCase().startsWith("Mac");
		}
		catch (Throwable t) {
			isLinux = false;
		}

		IS_LINUX_OS = isLinux;
		IS_SOLARIS_OS = isSolaris;
		IS_MAC_OS = isMacOS;
	}
	
	/**
	 * @return true if the host OS is Linux
	 */
	public static boolean isLinuxOS(){
		return IS_LINUX_OS;
	}
	
	/**
	 * @return true if the host OS is a Solaris
	 */
	public static boolean isSolarisOS(){
		return IS_SOLARIS_OS;
	}
	
	/**
	 * @return the exact OS name
	 */
	public static String getOSName(){
		return OS_NAME;
	}
	
	/**
	 * @return true if the host OS is a OS X
	 */
	public static boolean isMacOS(){
		return IS_MAC_OS;
	}
	
    /**
     * 
     */
    private static final long serialVersionUID = 7986848917312118903L;

    /**
     * Node to which this module is attached
     */
    protected volatile MNode node;
    
    /**
     * module info 
     */
    protected volatile MonModuleInfo monModuleInfo;
    
    public final MonModuleInfo init(MNode mnode, String args) {
        this.node = mnode;
        this.monModuleInfo = initArgs(args);
        return monModuleInfo;
    }

    /**
     * @param args
     * @return the module info
     */
    protected abstract MonModuleInfo initArgs(String args);
    
    public String[] ResTypes() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getOsName() {
        // TODO Auto-generated method stub
        return null;
    }

    public final MNode getNode() {
        return node;
    }

    public String getClusterName() {
        return node.getClusterName();
    }

    public String getFarmName() {
        return node.getFarmName();
    }

    public boolean isRepetitive() {
        return true;
    }

    public String getTaskName() {
        // TODO Auto-generated method stub
        return null;
    }

    public MonModuleInfo getInfo() {
        return monModuleInfo;
    }

    /**
     * @return a default Result for this module
     */
    protected Result getResult(){
		final Result r = new Result(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName());
		
		r.time = NTPDate.currentTimeMillis();

		return r;
    }
    
    /**
     * @return a default eResult for this module
     */
    protected eResult geteResult(){
		final eResult er = new eResult(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName(), null);

		er.time = NTPDate.currentTimeMillis();
	
		return er;
    }
    
    @Override
    public abstract Object doProcess() throws Exception;

}
