/**
 * This module obtains information about the status of the nodes from a Condor pool,
 * with the aid of the condor_status command. If "statistics" is given as argument,
 * the module provides statistics about the number of up/down nodes (the number of 
 * down nodes may be inaccurate).
 * If there is an error executing the condor_status command, the module waits for
 * a number of seconds and retries to execute it. The number of seconds to wait can
 * be specified with the "delayIfError" argument (by default it is 20).
 */
//package lia.Monitor.modules;

import java.io.File;
import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

public class monPN_LCG_Condor extends cmdExec implements MonitoringModule {
	
	/** serial version number */
    static final long serialVersionUID = 17062005L;
	
	/** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monPN_LCG_Condor");
    
	public MNode Node;
	public MonModuleInfo info ;
	
	static public String ModuleName = "monPN_LCG_Condor";
	static public String OsName = "*";
	public boolean isRepetitive = false;
	
	/** The names of the parameters reported by Condor */
	static String[] condorMetrics = {
		"Cpus",
		"TotalLoadAvg",
		"VirtualMemory",
        "Memory"
	};
	
	/** The names above will be "translated" to the Ganglia names: */
    static String[] myResTypes = {
		"NoCPUs",
		"Load1",
		"VIRT_MEM_free",
		"MEM_total"
	};
	
    /** The path to the Condor directory. */ 
    String condorLocation = null;
    String cmd = null;
    boolean environmentSet = false;
    
    /** Keeps monitoring information for the Condor nodes. The keys are the names
     * of the nodes and the elements are hashtables containing (parameter name,
     * parameter value) pairs. For SMP machines, there are separate nodes in 
     * Condor for each CPU.
     */
    Hashtable condorNodesData = new Hashtable();
    
    /**
     * Keeps the evidence of the active nodes from the pool. The keys are the 
     * nodes' hostnames and the values are Boolean (true if the node is active,
     * false otherwise).
     */
    Hashtable activeNodes = new Hashtable();
    
    /** Determines is we send results with the number of up/down nodes */
    boolean showStatistics = false;
    
    /** Number of seconds to wait if there was an error executing the Condor
     * command. After waiting, the module retries to execute the command.
     */
    int delayIfError = 20;
    
    /** Specifies whether the local central manager will be queried. */
    boolean useLocal = false;
    
    /** The names of the central manager daemos that we will query. */
    Vector serverNames = new Vector();
	
	protected boolean	UseRemote		= false;
	protected Vector	remoteHostNames	= new Vector();
    
	public monPN_LCG_Condor() {
		isRepetitive = true;
		canSuspend = false;
	}
	
	public MonModuleInfo init(MNode Node , String args) {
		String argList[] = new String[]{};
		String serverName = null;
		String remoteHostName = null;
		this.Node = Node;
		info = new MonModuleInfo();
		isRepetitive = true;
		
		/* check the argument lists */
		if ( args != null ) {
			argList = args.split(","); //requires java 1.4
			for ( int j=0; j < argList.length; j++ ) {
				if (argList[j].toLowerCase().indexOf("statistics") != -1) {
					showStatistics = true;
					logger.info("Statistics option enabled.");
				} 
				if (argList[j].toLowerCase().startsWith("remotehost")) {
					if(!UseRemote)
						UseRemote = true;
					try {
						remoteHostName = argList[j].split("(\\s)*=(\\s)*")[1].trim();
						remoteHostNames.add(remoteHostName);
                    }catch(Throwable t){
                        if(logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " Got exception parsing server option", t);
                        }
						remoteHostName = null;
                    }
					logger.log(Level.INFO, ModuleName+": overrridden RemoteHostName(" + remoteHostName + ")");
                }

				if (argList[j].toLowerCase().indexOf("cansuspend") != -1) {
				    boolean cSusp = false;
				    try {
					cSusp = Boolean.valueOf(argList[j].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
				    }catch(Throwable t){
					cSusp = false;
				    }
				    canSuspend = cSusp;
				    continue;
				}

				if (argList[j].toLowerCase().indexOf("uselocal") != -1) {
					useLocal = true;
					logger.info("UseLocal option enabled");
				} 
				if (argList[j].toLowerCase().indexOf("server") != -1) {
					try {
						serverName = argList[j].split("(\\s)*=(\\s)*")[1].trim();
						serverNames.add(serverName);
					}catch(Throwable t){                      
						logger.log(Level.INFO, " Got exception parsing server option", t);
						serverName = null;
					}
					logger.log(Level.INFO, "Added schedd to query: " + serverName);
				}
				
				if (argList[j].indexOf("DelayIfError") != -1) {
					String argval[] = argList[j].split("=");
					String sDelay = argval[1].trim();
					try {
						delayIfError = Integer.parseInt(sDelay);
						logger.finest("Value for DelayIfError: " + delayIfError);
					} catch (Exception e) {
						logger.warning("Invalid parameter value for DelayIfError");
						delayIfError = 20;
					}
				}
			} // end for 
		} // end if args
		
		
		info.ResTypes = myResTypes;
		info.name = ModuleName;	
		return info;
	}
	
	/**
	 * Initializes the module taking into account the environment variables.
	 * @throws Exception
	 */
	void setEnvironment() throws Exception {
		StringBuffer condorCmd;
		if(UseRemote){
			condorCmd = new StringBuffer();
			if (remoteHostNames.size() != 0) {
				for (int ind = 0; ind < remoteHostNames.size(); ind++) { 
					if (condorCmd.length() > 0)
						condorCmd.append(" ; ");
					condorCmd.append("ssh " + remoteHostNames.get(ind) + " 'condor_status -l'" );
				}
			}
			cmd = new String(condorCmd);
			logger.log(Level.INFO, "Using remote Condor command");
		} else {
			try {
				condorLocation = AppConfig.getGlobalEnvProperty("CONDOR_LOCATION");
			} catch (Exception e) {
				logger.log(Level.WARNING, "Got exception when obtaining env variable: ", e);	    	 
			}
			if (condorLocation == null) {
				logger.log(Level.WARNING, "The CONDOR_LOCATION environment variable is not set!");
				throw new Exception("The CONDOR_LOCATION environment variable is not set!");
			} else {
				/* check if the condor_status executable actually exists */
				cmd = condorLocation + "/bin/condor_status";
				File fd = new File(cmd);
				if (!fd.exists()) { 
					throw new Exception("The command " + cmd + " does not exist.");
				}
		
				condorCmd = new StringBuffer();
				if (useLocal || serverNames.size() == 0) {
					condorCmd.append(condorLocation + "/bin/condor_status -l");
				}
				if (serverNames.size() != 0) {
					for (int ind = 0; ind < serverNames.size(); ind++) { 
						if (condorCmd.length() > 0)
							condorCmd.append(" ; ");
						condorCmd.append(condorLocation + "/bin/condor_status -l -pool " +
								serverNames.get(ind));
					}
				}
			
				cmd = new String(condorCmd);
				logger.log(Level.INFO, "Using Condor location: " + condorLocation);
			}
		}
	}
	
	public String[] ResTypes () {
		return myResTypes;
	}
	public String getOsName() { return OsName; }
	
	
	public MNode getNode () { return Node ; }
	public String getClusterName () { return Node.getClusterName() ; }
	public String getFarmName () {    return Node.getFarmName() ; }
	public String getTaskName () {    return ModuleName ; }
	public boolean isRepetitive() { return isRepetitive; }
	public MonModuleInfo getInfo() { return info; }
	
	/**
	 * The main function of the module.
	 */
	public Object doProcess() throws Exception {
		
		if (!environmentSet) {
			setEnvironment();
			environmentSet = true;
		}
		
		long t1 = System.currentTimeMillis();
		Vector ret = null;
		
		try {
			getCommandOutput();
		} catch (Exception e) {
			// if the command fails the first time, wait for a while and try again 
			logger.log(Level.WARNING, "monPN_LCG_Condor got exception: ", e);
			logger.log(Level.INFO, "Failed getting nodes status, retrying after "
					+ delayIfError +"s...");
			try {
				Thread.sleep(delayIfError * 1000);
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
		
		/* generate the results based on the condorNodesData hashtable */
		ret = createResults();
		
		long t2 = System.currentTimeMillis();
		logger.log(Level.INFO, "[monPN_LCG_Condor] execution time for doProcess(): "
				  + (t2 - t1) + " ms");
		  
		return ret;
	}
	
	/**
	 * Executes the condor_status command and parses its output. The information 
	 * obtained is stored in the condorNodesData hashtable. 
	 * @throws Exception If there was an error executing the condor_status command 
	 */
	void getCommandOutput() throws Exception {
		/* execute the condor_status command */
		BufferedReader buffer = procOutput (cmd);
		
		if (buffer == null)
			throw new Exception ("No output for the condor_status command");
		
		/* reset the information about the active nodes */
		Enumeration akeys = activeNodes.keys();
		while (akeys.hasMoreElements()) {
			String hostname = (String)akeys.nextElement();
			activeNodes.put(hostname, new Boolean(false));
		}
		
		/* clear the old data */
		condorNodesData = new Hashtable();
		/* fill condorNodesData with information obtained from Condor */
		parseCondorOutput(buffer); 
	}
	
	/**
	 * Parses the output of the "condor_status -l" command and fills the
	 * condorNodesData hashtable with the results.
	 * @param buff Buffer containing the output of the command.
	 * @throws Exception
	 */
	public void parseCondorOutput(BufferedReader buff) throws Exception {
		/* buffer in which we gather the information about the current job */
		StringBuffer sb = new StringBuffer();
			
		boolean haveNewRecord = false;
		boolean haveErrorOutput = true; // shows if the command executed successfully
		try {
			/* the information for a node is on multiple lines */
			for (String lin = buff.readLine(); lin != null; lin=buff.readLine() ) {
				if (lin.startsWith("MyType") || lin.indexOf("Machine") >= 0) {
					/* this is a "normal" output, not an error message */
					haveErrorOutput = false;
				}
				if (lin.length() != 0 && !lin.startsWith("MyType")) {
					/* we are in the middle of a condor_status record */
					sb.append(lin + "\n");
					haveNewRecord = true;
				} else  if (lin.length() == 0 && haveNewRecord && !haveErrorOutput) { 
					/* the record for a node is finished, parse it */
					haveNewRecord = false;
					Hashtable nodeData = parseStatusRecord(sb);
					
					/* mark the machine as active (the "Machine" attribute is
					 * the node's hostname) */
					String hostname = (String)nodeData.get("Machine");
					activeNodes.put(hostname, new Boolean(true));
					
					/* the "Name" attribute is the name of the virtual machine
					 * (on a SMP machine there can be multiple virtual machines)
					 */
					String vmName = (String)nodeData.get("Name");
					condorNodesData.put(vmName, nodeData);
					
					/* clear the buffer to prepare it for the next record */
					sb = new StringBuffer("");
				} // end of if
				
			} // end of for      
			
			if (haveErrorOutput) {
				//logger.log(Level.WARNING, "The condor_status command returned error!");
				throw new Exception("Error executing the condor_status command");
			}
			
		} catch (Throwable t) {
			//t.printStackTrace();
			throw new Exception("ParseCondorOutput - " + t.getMessage()) ;
		} // end try/catch
	}
	
	/**
	 * Parses a record from the output of the "condor_status -l" command, containing
	 * information about a single node.
	 * @param rec String that contains the lines of the record.
	 * @return A hashtable containing parameter names as keys and parameter values as
	 * elements.
	 */
	Hashtable parseStatusRecord(StringBuffer rec) {
		Hashtable nodeInfo = new Hashtable();
		String line;
		String sRec = new String(rec);
		StringTokenizer st = new StringTokenizer(sRec, "\n");
		
		/* process each line from the record */
		while (st.hasMoreTokens()) {
			line = st.nextToken();
			
			StringTokenizer lst = new StringTokenizer(line, " =");
			if (!lst.hasMoreTokens())
				continue;
			String paramName = lst.nextToken();
			if (!lst.hasMoreTokens())
				continue;
			String paramValue = lst.nextToken();
			
			if (paramName.equals("Name") || paramName.equals("Machine")) {
				String nameWithoutQuotes = paramValue.replaceAll("\"", ""); 
				nodeInfo.put(paramName, nameWithoutQuotes); continue;
			}
			
			if (paramName.equals("Cpus")) {
				nodeInfo.put(paramName, paramValue); continue;
			}
			
			if (paramName.equals("TotalLoadAvg")) {
				nodeInfo.put(paramName, paramValue); continue;
			}
			
			if (paramName.equals("VirtualMemory")) {
				nodeInfo.put(paramName, paramValue); continue;
			}
			
			if (paramName.equals("Memory")) {
				nodeInfo.put(paramName, paramValue); continue;
			}
		}
		
		return nodeInfo;
	}
	
	/**
	 * Creates a Vector with Results from the contents of the condorNodesData
	 * hashtable. A result will be created for each node.
	 */
	public Vector createResults() {
        long   cTime = NTPDate.currentTimeMillis();
        
		Vector results = new Vector();
		
		Enumeration ckeys = condorNodesData.keys();
		/* for each node */
		while (ckeys.hasMoreElements()) {
			String vmName = (String)ckeys.nextElement();
			
			/* initialize a Result */
			Result r = new Result();
			r.ClusterName = getClusterName();
			r.FarmName = getFarmName();
			r.NodeName = vmName;
			r.time = cTime;
			r.Module = ModuleName;
			
			/* add the parameter values to the result */
			Hashtable nodeData = (Hashtable)condorNodesData.get(vmName);
			Enumeration nkeys = nodeData.keys();
			while(nkeys.hasMoreElements()) {
				String paramName = (String)nkeys.nextElement();
				if (!paramName.equals("Name") && !paramName.equals("Machine")) {
					String paramValue = (String)nodeData.get(paramName);
					double d = Double.parseDouble(paramValue);
					int idx = getIndex(condorMetrics, paramName);
					if (idx >= 0)
						r.addSet(myResTypes[idx], d);
					else
						logger.log(Level.FINEST, "Unsupported Condor parameter: " + paramName);
				}
			}
			results.add(r);
		}
		
		if (showStatistics) {
			/* construct the Result with statistics about the active nodes */
			Result rs = new Result();
			rs.ClusterName = "PN_LCG_Condor_Statistics";
			rs.FarmName = getFarmName();
			rs.NodeName = "Statistics";
			rs.time = cTime;
			rs.Module = ModuleName;
			
			double nTotalNodes = activeNodes.size();
			rs.addSet("Total Nodes", nTotalNodes);
			/* count the active nodes */
			Enumeration aelems = activeNodes.elements();
			double nActiveNodes = 0;
			while(aelems.hasMoreElements()) {
				Boolean b = (Boolean)aelems.nextElement();
				if (b.booleanValue() == true)
					nActiveNodes++;
			}
			rs.addSet("Total Available Nodes", nActiveNodes);
			rs.addSet("Total Down Nodes", nTotalNodes - nActiveNodes);
			results.add(rs);
		}
		
		StringBuffer sb = new StringBuffer();
		for(int vi = 0; vi < results.size(); vi++) {
			sb.append(" [ " + vi + " ] = " + results.elementAt(vi) + "\n");
		}
		logger.log(Level.FINEST, "Got Results: " + sb);
		
		return results;
	}
	
	/**
	 * Finds the index of an element in an array of Strings. 
	 * @param tab The array of Strings.
	 * @param elem The element to be found.
	 * @return The index of the element, or -1 if it is not found.
	 */
	int getIndex(String[] tab, String elem) {
		int ret = -1;
		
		for (int i = 0; i < tab.length; i++) {
			if (tab[i].equals(elem)) {
				ret = i;
				break;
			}
		}
		return ret;
	}
	
	/**
	 * Used to test the module outside MonALISA.
	 */
	static public void main ( String [] args ) {
	  System.out.println ( "args[0]: " + args[0] );
	  String host = args[0] ;
	  monPN_LCG_Condor aa = null;
	  String ad = null ;

	  try {
	    System.out.println ( "...instantiating PN_LCG_Condor");
	    aa = new monPN_LCG_Condor();
	  } catch ( Exception e ) {
	    System.out.println ( " Cannot instantiate PN_LCG_Condor:" + e );
	    System.exit(-1);
	  } // end try/catch

	  try {
	    ad = InetAddress.getByName( host ).getHostAddress();
	  } catch ( Exception e ) {
	    System.out.println ( " Cannot get ip for node " + e );
	    System.exit(-1);
	  } // end try/catch

	  System.out.println ( "...running init method ");
	  //MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), arg);
	  MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), "");

	  int sec = 20; // number of seconds to sleep before processing again
	  for ( int i=0; i<(int) 500; i++) {
	    try {
	      System.out.println ( "...sleeping "+sec+" seconds");
	      Thread.sleep(sec*1000);
	      System.out.println ( "...running doProcess");
	      Object bb = aa.doProcess();
	      if ( bb != null && bb instanceof Vector ) {
	          Vector v = (Vector) bb;
	          System.out.println ( " Received a Vector having " + v.size() + " results" );
	          for(int vi = 0; vi < v.size(); vi++) {
	              System.out.println(" [ " + vi + " ] = " + v.elementAt(vi));
	          }
	        }

	    } catch (Exception e) {
	      logger.log(Level.WARNING, "ERROR: ", e );
	    } // end try/catch
	  } // end for

	  System.out.println ( "PN_LCG_Condor Testing Complete" );
	  System.exit(0);
	} // end main
}

