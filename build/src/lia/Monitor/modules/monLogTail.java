/**
 * 
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.DynamicModule;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ShutdownReceiver;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.MLProcess;
import lia.util.ShutdownManager;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since 2007-03-07
 */
public class monLogTail extends DynamicModule implements ShutdownReceiver {
	private static final Logger logger = Logger.getLogger(monLogTail.class.getName());
	private static final String[] SYS_EXTENDED_BIN_PATH = new String[] { "PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin" };
	
	private static long statusReportInterval = AppConfig.getl("lia.Monitor.monLogTail.statusReportInterval", 60) * 1000;
	private long lastStatusReport;
	
	private static final long serialVersionUID = 1L;
	
	private MNode node = null;
	private MonModuleInfo info = null;
	
	/**
	 * Configuration options
	 */
	Properties prop = null;

	private MonitorOutput t = null;
	private Object syncT = new Object();
	
	protected void disable() {
		if(logger.isLoggable(Level.FINE))
			logger.fine("Disabling logtail for command="+prop.getProperty("command"));
		synchronized (syncT) {
			if (t!=null){
				t.signalStop();
				t = null;
			}
		}
	}

	protected void enable() {
		if(logger.isLoggable(Level.FINE))
			logger.fine("Enabling logtail for command="+prop.getProperty("command"));
		synchronized (syncT) {
			if (t==null){
				t = new MonitorOutput(this);
				t.start();
			}
		}
	}
	
	/**
	 * Thread that monitors the output of a command and publishes the results 
	 */
	private static final class MonitorOutput extends Thread {
		
		private final monLogTail module;
		
		private AtomicBoolean bShouldStop = new AtomicBoolean(false);
		
		private Process p = null;
		
		/**
		 * @param module
		 */
		public MonitorOutput(final monLogTail module){
			this.module = module;
			
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Module created the process monitoring thread");
		}
		
		public void run(){
			BufferedReader br = null;
			
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Module started the process monitoring thread");
			
			try{
				final String sCommand = module.prop.getProperty("command"); 
				
				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Command to execute is : '"+sCommand+"'");
				
				
				p = MLProcess.exec(sCommand, SYS_EXTENDED_BIN_PATH, -1);

				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Process created ok");
				
				br = new BufferedReader(new InputStreamReader(p.getInputStream()));

				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Got the IS, waiting");
				
				while (!bShouldStop.get()){
					final String sLine = br.readLine();
					
					if (logger.isLoggable(Level.FINE))
						logger.log(Level.FINE, "Got line : '"+sLine+"'");
					
					if (sLine==null)
						break;
						
					module.newLine(sLine);
				}
				
				br.close();
				br = null;
			}
			catch (Exception e){
				if (logger.isLoggable(Level.FINE))
					logger.log(Level.FINE, "Exception reading program output", e);
				// ignore
			}
			cleanup();
		}
		
		private void cleanup(){
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Closing input stream");
			
			try{
				p.getInputStream().close();
			}
			catch (Throwable t){
				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "Exception closing input stream", t);
			}

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Closing error stream");
			
			try{
				p.getErrorStream().close();
			}
			catch (Throwable t){
				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "Exception closing error stream", t);
			}
			
			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Closing output stream");

			try{
				p.getOutputStream().close();
			}
			catch (Throwable t){
				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "Exception closing output stream", t);
			}

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Destroying");

			try{
				p.destroy();
			}
			catch (Throwable t){
				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "Exception destroying", t);
			}

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Waiting for");

			try{
				p.waitFor();
			}
			catch (Throwable t){
				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "Exception waiting for", t);
			}
			
			p = null;

			if (logger.isLoggable(Level.FINE))
				logger.log(Level.FINE, "Done, process ended");
		}
		
		/**
		 * Tell the thread to stop.
		 */
		public void signalStop(){
			bShouldStop.set(true);
			p.destroy();
		}
		
	}

	private final Vector buffer = new Vector();
	
	/**
	 * Called from the monitoring thread whenever some new line is produced
	 * 
	 * @param sLine
	 */
	void newLine(final String sLine){
		final eResult r = new eResult(sFarm, sCluster, sNode, "monLogTail", new String[]{"line"});
		
		r.param[0] = sLine;
		
		r.time = NTPDate.currentTimeMillis();
		
		buffer.add(r);
	}
	
	public boolean matches(final monPredicate pred) {
		if(logger.isLoggable(Level.FINEST))
			logger.finest("Matching predicate "+pred+" with my cluster="+sCluster+" and node="+sNode);
		if (
			pred.Cluster!=null && pred.Cluster.equals(sCluster) && 
			pred.Node!=null && (pred.Node.equals(sNode) || pred.Node.equals("*") || pred.Node.equals("%"))
		)
			return true;
		if(logger.isLoggable(Level.FINEST))
			logger.finest("Predicate "+pred+" didn't match!");
		return false;
	}

	public Object doProcess() throws Exception {
		final Vector vRet;
		
		synchronized (buffer){
			vRet = new Vector(buffer.size() + 1);
			
			vRet.addAll(buffer);
			
			buffer.clear();
			
			if (buffer.size()>1000)
				buffer.trimToSize();
		}
		
		statusReportInterval = AppConfig.getl("lia.Monitor.monLogTail.statusReportInterval", 60) * 1000;
		if(System.currentTimeMillis() - lastStatusReport > statusReportInterval){
			lastStatusReport = System.currentTimeMillis();
			int status = 0;
			synchronized (syncT) {
				status = t==null ? 0 : (t.isAlive() ? 1 : 2);
			}
			
			final Result r = new Result(sFarm, sCluster, sNode, "monLogTail", new String[]{"Status"}, new double[]{status});
			r.time = NTPDate.currentTimeMillis();
			
			if (status==1)
				r.addSet("Subscribers", getCurrentUseCount());
			
			vRet.add(r);
		}		
		if (logger.isLoggable(Level.FINEST))
			logger.log(Level.FINEST, "In logtail for "+sCluster+"/"+sNode+" with "+getCurrentUseCount()+" subscribers and returning: "+vRet.toString());
		
		return vRet;
	}

	public String[] ResTypes() {
		return new String[0];
	}

	public String getClusterName() {
		return node.getClusterName();
	}

	public String getFarmName() {
		return node.getFarmName();
	}

	public MonModuleInfo getInfo() {
		return info;
	}

	public MNode getNode() {
		return node;
	}

	public String getOsName() {
		return "Linux";
	}

	public String getTaskName() {
		return info.getName();
	}

	private String sFarm;
	private String sCluster;
	private String sNode;
	private String sCommand;
	
	public MonModuleInfo init(final MNode node, final String args) {
		this.node = node;
		this.info = new MonModuleInfo();
		
		try {
			prop = parseArguments(args);
			this.info.setState(0);
			
			sFarm = prop.getProperty("Farm", getFarmName());
			sCluster = prop.getProperty("Cluster", getClusterName());
			sNode = prop.getProperty("Node", node.name);
			sCommand = prop.getProperty("command"); 
			register();
			ShutdownManager.getInstance().addModule(this);
		}
		catch (Exception e) {
			this.info.setState(1);// error
			
			logger.log(Level.SEVERE, "Cannot initialize because", e);
		}
		
		if (logger.isLoggable(Level.FINE))
			logger.log(Level.FINE, "After init I have these values: Farm="+sFarm+", Cluster="+sCluster+", Node="+sNode+", Command="+sCommand);
		
		return info;
	}

	public boolean isRepetitive() {
		return true;
	}
	
	public boolean stop() {
		disable();
		unregister();
		return true;
	}

	// kill the started process, if the case
	public void Shutdown() {
		disable();
	}
}
