import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.*;
import java.net.InetAddress;


public class VOgsiftpIO extends cmdExec implements MonitoringModule  {

		static final public String OsName = "linux";
		static final public String ModuleName="VOgsiftpIO";
		static public String[] ResTypes = {"ftpInput","ftpOutput"};
		//static final public String  CmdPath = "$MonaLisa_HOME/Service/usr_code/VOModules/";
		static final public String  CmdPath = "$MonaLisa_HOME/Service/usr_code/VOgsiftpIO/";

		public boolean testMode  = false;
		public boolean debugMode = true;
		public Date currDate     =  new Date();
		
		//public String vo         =  null;
		//public Hashtable jobCmd = new Hashtable();
		//public Hashtable unixAccounts  = new Hashtable();
		public String monalisaHome = null;
		//private String[] nodes = null;
		

		private String[] voNames = null;
		private String[] argList = null;
		private static final String GRIDFTP_LOG = "/var/gridftp.log";
		private static final String GRIDFTP_PARSER = "/libexec/grid-info-grid3-ftp";
		private static final String GRIDFTP_STAT = "/monitoring/info/gridftp-stats.txt";
		private String filename = null; //VODescriptor.getVdtLocation()+GRIDFTP_STAT;
		private String execcmd = null; //VODescriptor.getVdtLocation()+GRIDFTP_PARSER;

		private HashMap mapParam = VODescriptor.getDefaultParameters();


		//------------------------------------------
		public VOgsiftpIO () {
				super(ModuleName);
				info.ResTypes = ResTypes;
				isRepetitive = true;
				
				voNames = VODescriptor.getVOD().getVOListCN();
				logit("Constructor of "+ModuleName+" at "+currDate );
				logit("Info content: name "+info.name+" id "+info.id+" type "+info.type+" state "+info.state+" err "+info.error_count+".");
				for (int i=0; i<voNames.length; i++)
						logit("VO "+i+":"+voNames[i]);
		}


		//==============================================================
		// configuration file entry: *Node{VOgsiftpIO, localhost, <arguments>}%30
		// <arguments> is a comme separated list (no quoted)
		// ftplog=/path-to-ftplog
		// globus_location=/globus-location
		public  MonModuleInfo init( MNode Node , String args ) {
				String[] argList = null;

				// -----------------------
				this.Node = Node;
				//vo = Node.getClusterName();

				logit("Instantiating instance for Cluster (node in cf)"+Node.getClusterName()+" at "+currDate );
				logit("Arguments: "+args );
				//if (Node==null)
				//		logit("No node defined");
				//else
				logit("Node Info: name "+(Node.name==null?"null":Node.name)+" short_name "+(Node.name_short==null?"null":Node.name_short)+
							" cl.name "+(Node.cluster==null?"null":Node.cluster.name)+" fa.name "+(Node.farm==null?"null":Node.farm.name)+".");

				// -----------------------
				// Check the argument list
				if ( args != null ) {
						//check if file location or  globus_location
						//are passed
						//NOW IGNORED
						argList = args.split(","); //requires java 1.4
						for ( int j=0; j < argList.length; j ++ ) {
								argList[j]=argList[j].trim();
								logit("Argument "+j+":"+argList[j]+".");
						}
						VODescriptor.setParameters(argList, mapParam);
						/*
								if (argList[j].equals("par_alt")) {
								    ResTypes = new String[] {"ftpInput22w","ftpOutput22w"};
										info.ResTypes = ResTypes;
								}
						*/
				
				} else {
						logit("No arguments passed to the module");
				}

				// -------------------------------------
				// Determine the MonaALisa_HOME e file location
				// -------------------------------------
				filename = VODescriptor.getVdtLocation(mapParam)+GRIDFTP_STAT;
				execcmd = VODescriptor.getGlobusLocation(mapParam)+GRIDFTP_PARSER;

				monalisaHome = System.getProperty("MonaLisa_HOME");
				if ( monalisaHome == null ) {
						logit("MonaLisa_HOME environmental variable not set.");
				}
				else {
						logit("MonaLisa_HOME="+monalisaHome);
						File probe = new File(filename);
						if (!probe.isFile())
								logit("Gridftp stats ("+filename+"="+probe.getAbsolutePath()+") not found.");
						else
								logit("Gridftp stats ("+filename+"="+probe.getAbsolutePath()+
											") last modified "+(new Date(probe.lastModified())).toString()+".");
						probe = new File(VODescriptor.getGlobusLocation(mapParam)+GRIDFTP_LOG);
						if (!probe.isFile())
								logit("Gridftp log ("+VODescriptor.getGlobusLocation(mapParam)+GRIDFTP_LOG+"="+probe.getAbsolutePath()+") not found.");
						else
								logit("Gridftp log ("+VODescriptor.getGlobusLocation(mapParam)+GRIDFTP_LOG+"="+probe.getAbsolutePath()+
											") last modified "+(new Date(probe.lastModified())).toString()+".");
						probe = new File(execcmd);
						if (!probe.isFile())
								logit("Gridftp log parser ("+execcmd+"="+probe.getAbsolutePath()+") not found.");
						else
								logit("Gridftp log parser ("+execcmd+"="+probe.getAbsolutePath()+
											") last modified "+(new Date(probe.lastModified())).toString()+".");
				}
    
				info.ResTypes = ResTypes;
				return info;
		}

		
		//------------------------------------------
		public String[] ResTypes () {
				return ResTypes;
		}

		//------------------------------------------
		public String getOsName() { return OsName; }
		
		//=======================================================================
		//-- doProcess ----------------
		private final static String COMMENT_LINE_START = "#";
		private final static String TOTAL_LINE_START = "Totals";

		public double[][]  parseFtpStat (BufferedReader buff)   throws Exception  {
				String record = null;
				String[] tok = null;
				double[][] res = new double[voNames.length][2];
				for ( int i = 0; i < voNames.length; i++) 
						res[i][0] = -1.0;
				while ( (record = buff.readLine()) != null) {
						if (record.length() == 0 )   //check for empty line
								continue;
						if (record.startsWith(COMMENT_LINE_START))  //check for comment
								continue;
						tok = record.split(" ");
						if (tok.length<3) {
								; //logit("Irregular ftp stat file, line len. "+tok.length+" ("+record+"), skipping line.");
						} else {
								int vid = VODescriptor.getVOD().getVOid(tok[0].trim());
								if (vid>=0)
										if (res[vid][0]>0) {
												logit("Irreguler ftp stat file, twice the same VO, skipping line.");
										} else {
												/*Double dd = new Double("5.0");
												Double dq = new Double("-5.0");
												String ss = "6.3";
												Double dp = new Double(ss);
												Double pp =  Double.valueOf(tok[1]);
												*/
												res[vid][0] = Double.valueOf(tok[1]).doubleValue();
												res[vid][1] = Double.valueOf(tok[2]).doubleValue();
										}

						}
						//if (record.startsWith(TOTAL_LINE_START))  //check for totals
						//	continue;
				}
				return res;
		}

		public Object   doProcess() throws Exception  {
				Vector results = new Vector();
				Result result  = null;
				double[][] numRes = null;

				currDate = new Date();

				//ExecuteScript
				try { 
						logit("Gsiftp log parser starting, cmd:"+execcmd);
						//switch the commented lines to read from the file
						//FileReader fr     = new FileReader( filename ); 
						//BufferedReader br = new BufferedReader(fr);   
						BufferedReader br = procOutput ( execcmd );

						if ( br  == null ) {
								throw new Exception ("Command line process failed unexpectedly");
						}
						
						numRes = parseFtpStat(br);
						
						//logit("-- process VO("++") -- "+currDate+" --");
						for ( int i = 0; i < voNames.length; i++) {
								String NodeName = voNames[i];
								
								if (numRes[i][0]<0)
										continue;
								result = new Result();
								result.NodeName = NodeName;
								result.ClusterName = Node.getClusterName(); 
								result.FarmName = Node.getFarmName(); 
								//result.Module = "IOgridftpVO";
								result.time =  currDate.getTime();

								//Node.getFarmName(), NodeName, ModuleName ResTypes;
								//result.param_name = ResTypes;
								//result.param = numRes[i];
								result.addSet(ResTypes[0], numRes[i][0]);
								result.addSet(ResTypes[1], numRes[i][1]);
								results.add(result);
								//logit("Adding result "+Node.getFarmName()+","+Node.getClusterName()+","+NodeName+" - "+ResTypes[0]+ResTypes[1]+".");
						}
						/* Remove logging
						for (int i=0; i<results.size(); i++) {
								Result r = (Result) results.elementAt(i);
								logit("Reporting "+i+":"+r.NodeName+" "+r.param[0]+","+r.param[1]+".");
						}
						*/
				} catch (Exception e) {
						logit("FATAL ERROR -"+e);
						throw e;
				}
				return results;
		
		}


		// --------------------------------------------------
    /*
    public class BasicLogging {
        public static void main(String[] args) {
            // Get a logger; the logger is automatically created if
            // it doesn't already exist
            Logger logger = Logger.getLogger("com.mycompany.BasicLogging");
    
            // Log a few message at different severity levels
            logger.severe("my severe message");
            logger.warning("my warning message");
            logger.info("my info message");
            logger.config("my config message");
            logger.fine("my fine message");
            logger.finer("my finer message");
            logger.finest("my finest message");
         }
    }
		*/

		private static Logger myLogger = null;
		private static Logger getLogger() {
				if (myLogger!=null) {
						return myLogger;
				}
				else {
						try {
								// Create an appending file handler
								boolean append = true;
								FileHandler handler = new FileHandler("my_module.log", append);
    
								// Add to the desired logger
								myLogger = Logger.getLogger("com.mycompany");
								myLogger.addHandler(handler);
								return myLogger;
						} catch (IOException e) {
								e.printStackTrace();
								System.err.println ( "Failed to open logger" );
						}
						return null;
				}
		}
		
		void logit(String msg) {
        /*
					logger.severe("my severe message");
					logger.warning("my warning message");
					logger.info("my info message");
					logger.config("my config message");
					logger.fine("my fine message");
					logger.finer("my finer message");
					logger.finest("my finest message");
				*/
				//VOgsiftpIO.getLogger().info(ModuleName+": "+msg);
				System.out.println ( "VOIO::"+ModuleName+": "+msg );
		}
		/*
			void logit(String msg) {
			System.out.println ( ModuleName+": " + msg );
			}
		*/

		// --------------------------------------------------
		void debug(String msg){
				if (debugMode) {
						logit(msg);
				}
		}
		// --------------------------------------------------
		public MonModuleInfo getInfo(){
				return info;
		}

		// --------------------------------------------------
		static public void main ( String [] args ) {
				String host = args[0] ;
				VOgsiftpIO aa = new VOgsiftpIO();
				String ad = null ;
				
				try {
						ad = InetAddress.getByName( host ).getHostAddress();
				} catch ( Exception e ) {
						System.out.println ( " Cannot get ip for node " + e );
						System.exit(-1);
				}
				
				//MonModuleInfo info = aa.init( new MNode (args[0] ,ad,  null, null), "debug");
				String arg = "stat-file";
				MonModuleInfo info = aa.init( new MNode (args[0], ad, null, null), arg);
				for (;;) {
						try {
								Thread.sleep(10*1000);
								Object bb = aa.doProcess();
						} catch (Exception e) {
								System.out.println ( "ERROR: "+e );
								
						}
				}
				
		}
 
}


