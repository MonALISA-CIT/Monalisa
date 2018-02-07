//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Hashtable;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

//import lia.util.ntp.NTPDate;

public class monPN_LCG_PBS extends cmdExec implements MonitoringModule {
   
	/** serial version number */
    static final long serialVersionUID = 1706200525091981L;
	
	protected final double EXP = Math.exp(-(5.0/60.0));
	
    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monPN_LCG_PBS");
	
    protected String   OsName            = "linux";
    protected String   ModuleName        = "monPN_LCG_PBS";
	protected String   clusterModuleName = "PN_LCG_PBS";
    protected String[] ResTypes          = null;
	
	/** The name of the monitoring parameters to be "extracted" from the PBS report */
	static String[] pbsMetric = { "ncpus", "availmem", "physmem", "loadave" };
	/** Rename them into : */
    static String[] myResTypes = { "NoCPUs", "VIRT_MEM_free", "MEM_total", "Load1"};
	
	protected int downNodeNo;
	protected int totalNodeNo;
	protected int freeNodesNo;
	
	protected Hashtable pbsNodeData = new Hashtable();

	protected String	cmd				= null;
	protected String	PBSHome			= null;
	protected Vector	remoteHostNames	= new Vector();
	protected Vector	serverNames		= new Vector();
	protected int		nrCall			= 0;
	protected boolean	UseLocal		= false;
	protected boolean	UseRemote		= false;
	protected boolean	debugmode 		= false;
	protected boolean	statisticsmode	= false;
	protected boolean	environmentSet	= false;
	protected long		DELAY_IF_ERROR	= 20000;

	public monPN_LCG_PBS(String TaskName) {
		super(TaskName);
        info.ResTypes = myResTypes;
        isRepetitive = true;
	}

	public monPN_LCG_PBS() {
		super("monPN_LCG_PBS");
        info.ResTypes = myResTypes;
        isRepetitive = true;
	}
	
    public MonModuleInfo init(MNode inNode, String args) {
		/** the method name */
        String methodName = "init";
        /** the arguments list from configuration file entry */
        String[] argList = null;
		
		String	serverName		= null;
		String	remoteHostName	= null;
		
		isRepetitive = true;
		Node = inNode;
		clusterModuleName = Node.getClusterName() + "-" + ModuleName;
        info.ResTypes = myResTypes;
		
        try {
			/** Check the argument list and process information */
	        if (args != null) {
	            /** check if file location or globus_location are passed */
	            argList = args.split("(\\s)*,(\\s)*");
	            
	            for (int i = 0; i < argList.length; i++) { 
	                argList[i] = argList[i].trim();
	                if (argList[i].toLowerCase().startsWith("debug")) {
	                    debugmode = true;
	                    logger.log(Level.INFO,ModuleName+": "+methodName+": overrridden Debug(" + debugmode + ")");
			   continue;
	                }
			if (argList[i].toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(argList[i].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    }catch(Throwable t){
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    continue;
                }
					
			if (argList[i].toLowerCase().startsWith("uselocal")) {
	                    UseLocal = true;
	                    logger.log(Level.INFO,ModuleName+": "+methodName+": overrridden UseLocal(" + debugmode + ")");
				continue;
	                }
		
			if (argList[i].toLowerCase().startsWith("remotehost")) {
						if(!UseRemote)
							UseRemote = true;
						try {
							remoteHostName = argList[i].split("(\\s)*=(\\s)*")[1].trim();
							remoteHostNames.add(remoteHostName);
                        }catch(Throwable t){
                            if(logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Got exception parsing server option", t);
                            }
							remoteHostName = null;
                        }
						logger.log(Level.INFO, ModuleName+": "+methodName + "overrridden RemoteHostName(" + remoteHostName + ")");
				continue;
	                }
					
			if (argList[i].toLowerCase().startsWith("delayiferror")) {
		                DELAY_IF_ERROR = Long.parseLong(argList[i].split("(\\s)*=(\\s)*")[1].trim());    
						logger.log(Level.INFO,ModuleName+": "+methodName+": overrridden DelayIfError(" + DELAY_IF_ERROR + ")");
						DELAY_IF_ERROR *= 1000;
				continue;
		            }
					
			if (argList[i].toLowerCase().indexOf("server") != -1) {
                        try {
                            serverName = argList[i].split("(\\s)*=(\\s)*")[1].trim();
							serverNames.add(serverName);
                        }catch(Throwable t){
                            if(logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, " Got exception parsing server option", t);
                            }
                            serverName = null;
                        }
						logger.log(Level.INFO, ModuleName+": "+methodName + "overrridden Server(" + serverName + ")");
				continue;
	                }
					
			if (argList[i].toLowerCase().startsWith("statistics")) {
	                    statisticsmode = true;
	                    logger.log(Level.INFO,ModuleName+": "+methodName+": overrridden Statistics(" + statisticsmode + ")");
				continue;
	                }
	            }
            }
        } catch (Exception e) {
            e.printStackTrace();
		}

        info.name = ModuleName;
        return info;
    }
	
    /**
     * Get the PBS locations and set other data structures
     * @throws Exception
     */
    protected void setEnvironment() throws Exception {
        /** the method name */
        String methodName = "setEnvironment";
        
        try {	
			StringBuffer pbsCmd;
			if(UseRemote){
				pbsCmd = new StringBuffer();
				if (remoteHostNames.size() != 0) {
					for (int ind = 0; ind < remoteHostNames.size(); ind++) { 
						if (pbsCmd.length() > 0)
							pbsCmd.append(" ; ");
						pbsCmd.append("ssh " + remoteHostNames.get(ind) + " 'pbsnodes -a'" );
					}
				}
				cmd = new String(pbsCmd);
				logger.log(Level.INFO, "Using remote PBS command");
			} else {
				/** try to get the PBS_LOCATION */
				PBSHome = AppConfig.getGlobalEnvProperty("PBS_LOCATION");
				if (PBSHome == null){
					logger.log(Level.WARNING, "The PBS_LOCATION environment variable is not set!");
					throw new Exception("PBS_LOCATION environmental variable not set.");
				} else {
					/** create command */
					cmd = PBSHome + "/bin/pbsnodes";
					/* check if the condor_status executable actually exists */
					File fd = new File(cmd);
					if (!fd.exists()) { 
						throw new Exception("[monPN_LCG_Condor] The command " + cmd + " does not exist.");
					}
					pbsCmd = new StringBuffer();
					if (UseLocal || serverNames.size() == 0) {
						pbsCmd.append(cmd + " -a");
					}
					
					if (serverNames.size() != 0) {
						for (int ind = 0; ind < serverNames.size(); ind++) { 
							if (pbsCmd.length() > 0)
								pbsCmd.append(" ; ");
							pbsCmd.append(cmd + " -s " + serverNames.get(ind) + " -a");
						}
					}
					
					cmd = new String(pbsCmd);
					logger.log(Level.INFO, "Using PBS location: " + PBSHome);
				}
			}
			
        } catch (Exception ex) {
            throw new Exception("setEnvironment() - " + ex.getMessage() + " " + ex);
        }
		environmentSet = true;
    }

	/**
	 * @see lia.util.DynamicThreadPoll.SchJob#doProcess()
	 */
	public Object doProcess() throws Exception {
		/** the method name */
        String methodName = "doProcess";
		
		long start = NTPDate.currentTimeMillis();
		nrCall++;
		Vector results = new Vector();
		
		if (!environmentSet)
			setEnvironment();
		
		downNodeNo = 0;
		totalNodeNo = 0;
		freeNodesNo = 0;
		
		try {
			getCommandOutput();
		} catch (Exception e) {
			logger.log(Level.WARNING, "monPN_LCG_PBS got exception: ", e);
			logger.log(Level.INFO, "Failed getting nodes status, retrying after " + DELAY_IF_ERROR/1000 +"s...");
			try {
				Thread.sleep(DELAY_IF_ERROR);
			} catch (Throwable t) {}
			
			try {
				getCommandOutput();
			} catch (Exception e2) {
				if (pro != null) {
					pro.destroy();
					pro = null;
				}
				logger.log(Level.INFO, "Second attempt to get nodes status failed, no results were sent");
				throw e2;
			}
		}

		results = createResults();
		
		long stop = NTPDate.currentTimeMillis();
		if(debugmode && nrCall%20 == 0)
			logger.log(Level.INFO, ModuleName+": "+methodName+": "+nrCall+" > time to run: "+(stop-start)+"ms");
		
		return results;
	}
	
	void getCommandOutput() throws Exception {
		/** the method name */
        String methodName = "getCommandOutput";

		/** execute the condor_status command */
		BufferedReader buffer = procOutput (cmd);
		
		if (buffer == null)
			throw new Exception ("No output for the  command");
		
		parsePBSOutput(buffer); 
	}
	
	public void parsePBSOutput(BufferedReader buffer) throws Exception {
		/** the method name */
        String methodName = "parsePBSOutput";
		String line;
		String node_name = "";
		Hashtable vals = new Hashtable();
		
		boolean haveErrorOutput = true; // shows if the command executed successfully
		
		try {
			while((line = buffer.readLine())!=null){
				haveErrorOutput = false;
				if(!line.startsWith(" ") && line.length()!=0){
					vals = new Hashtable();
					node_name = line;
				}
				if(line.trim().startsWith("state =")){
					String state = line.split(" = ")[1];
					
					if(state.indexOf("free")>=0)
						freeNodesNo++;
					if(state.indexOf("down")>=0)
						downNodeNo++;
				}
				if(line.trim().startsWith("status = ")){
					line = (line.trim()).substring("status = ".length());
					String[] lineElements = new String[] {};
					lineElements = line.split(",");
					for(int i=0;i<lineElements.length;i++){
						String key = lineElements[i].split("=")[0].trim();
				        String value = lineElements[i].split("=")[1].trim();
						if(isGoodParameter(key)>=0)
							vals.put(key,value);
					}
				}

				if(line.length()==0)
					if(vals.size()!=0){
						pbsNodeData.put(node_name,vals);
						totalNodeNo++;
					}
			}
			
			if (haveErrorOutput) {
				//logger.log(Level.WARNING, "The condor_status command returned error!");
				throw new Exception("Error executing the pbsnodes command");
			}
			
		} catch (Throwable t) {
			//t.printStackTrace();
			throw new Exception("ParsePBSOutput - " + t.getMessage()) ;
		} // end try/catch
	}
	
	int isGoodParameter(String param_name){
		for(int i=0;i<pbsMetric.length;i++)
			if(param_name.equals(pbsMetric[i]))
				return i;
		return -1;
	}
	
	public Vector createResults(){
		/** the method name */
        String methodName = "createResults";
		
		Vector results = new Vector();
		Result result;
		double factor = 1024.0;
		long resultTime = NTPDate.currentTimeMillis();
		boolean haveResults = false;
		
		 /** then, create result for each user */
        Enumeration nodes = pbsNodeData.keys();
		while (nodes.hasMoreElements()) {
			/** get the node host name */
			String nodeName = (String) nodes.nextElement();
            /** get the node values */
			Hashtable values = (Hashtable) pbsNodeData.get(nodeName);
            
			/** create the totals Result for this user */
			result = new Result(Node.getFarmName(), Node.getClusterName(), nodeName, ModuleName, myResTypes);
            result.time = resultTime;
			double load1 = 0D;
			for(int i=0;i<pbsMetric.length;i++){
				String value = (String)values.get(pbsMetric[i]);
				if(value.indexOf("kb")>0){
					value=value.substring(0,value.length()-2);
					result.param[i] = Double.parseDouble(value) / factor;
				}
				else{
					result.param[i] = Double.parseDouble(value);
					/*if(pbsMetric[i].equals("loadave")){
						 load1 = Double.parseDouble(value);
						 Random r = new Random();
						 result.addSet("Load5",load(5,1,load1));
						 result.addSet("Load15",load(15,1,load1));
					}*/
				}
			}
			results.addElement(result);
			haveResults = true;
        }
		/** create statistical cluster 	*/
		if(statisticsmode && haveResults){
			Result statisticalResult = new Result();
			statisticalResult.time = resultTime;
			statisticalResult.FarmName = Node.getFarmName();
			statisticalResult.ClusterName = "PN_LCG_PBS_Statistics";
			statisticalResult.NodeName = "Statistics";
			statisticalResult.addSet("Total Nodes",totalNodeNo + downNodeNo);
			statisticalResult.addSet("Total Available Nodes", totalNodeNo);
			statisticalResult.addSet("Total Free Nodes", freeNodesNo);
			statisticalResult.addSet("Total Down Nodes", downNodeNo);
			results.add(statisticalResult);
		}
		return results;
	}
	
	double load(long t, long n, double Load1){
		if(t == 1) return Load1;
		return load(t-1, n, Load1)*EXP + n*(1.0 - EXP);
	}
	
	/**
	 * @see lia.Monitor.monitor.MonitoringModule#ResTypes()
	 * @see lia.Monitor.monitor.MonitoringModule#getInfo()
	 * @see lia.Monitor.monitor.MonitoringModule#getOsName()
	 */
    public String[]      ResTypes()  { return ResTypes; }
    public String        getOsName() { return OsName; }
    public MonModuleInfo getInfo()   { return info; }

	/**
	 * Main method for testing module
	 * @param args
	 */
	public static void main(String[] args) {
        String host = "localhost";
        String ad = null;
        
		try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

		monPN_LCG_PBS aa = new monPN_LCG_PBS();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            Object bb = aa.doProcess();
            if (bb instanceof Vector) {
				Vector results = (Vector) bb;
				int dim = results.size();
                System.out.println(" Received a Vector having " + dim + " results...");
				for(int i=0; i<dim; i++){
					System.out.println(((Result) results.elementAt(i)).toString());
				}
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }
	}

}
