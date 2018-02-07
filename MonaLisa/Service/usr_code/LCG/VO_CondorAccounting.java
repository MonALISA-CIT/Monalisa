
//package lia.Monitor.modules;

import lia.util.LogWatcher;

import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.net.InetAddress;


/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information 
 * from Condor. It parses the output of the condor_q command (to obtain information  
 * for currently running jobs) and the Condor history file (to obtain information
 * about the finished jobs). 
 */
public class VO_CondorAccounting {
	private static final transient Logger logger = 
		Logger.getLogger("lia.Monitor.modules.VO_CondorAccounting");
	
	// Mapping between the integer values of the job statuses and the corresponding
	// Strings:
	//   5 - H (on hold), 
	//   2 - R (running), 
	//   1 - I (idle, waiting for a machine to execute on), 
	//  --------------------
	//  These states are not inlcuded in any metric:
	//   4 - C (completed), 
	//   ? - U (unexpanded - never been run), 
	//   3 - R (removed).
	private String[] jobStatusNames = {"X", "I", "R", "R", "C", "H"};
	
	/* Buffer used to keep the lines from the history file that contain
	 * information for a job. */ 
	StringBuffer logBuffer = null;
	/* Used to obtain only the lines that were added to the history file
	 * since the previous read operation.
	 */ 
	LogWatcher watcher = null;
	
	/* Indicates whether a warning should be logged if the output of the
	 * condor_q command is empty or if there was an error. A warning will only be 
	 * logged the first time in a series of consecutive errors.
	 */
	boolean logWarning = true;
	
	private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");
	
	private static final long SEC_MILLIS      =   1000;
	private static final long MIN_MILLIS      =   60*SEC_MILLIS;
	private static final long HOUR_MILLIS     =   60*MIN_MILLIS;
	private static final long DAY_MILLIS      =   24*HOUR_MILLIS;
	
	/**
	 * Constructor for the VO_CondorAccounting class.
	 * @param histFile the complete path of the Condor history file.
	 */	
	public VO_CondorAccounting(String histFile) {
	    logger.log(Level.INFO, "[VO_CondorAccounting] Initalizing...");
	    if (histFile != null)
		watcher = new LogWatcher(histFile);
	    else {
		logger.log(Level.INFO, "[VO_CondorAccounting] No Condor history information will be provided (either the feature was disabled or the history file does not exist)");
		watcher = null;
	    }
		logBuffer = new StringBuffer("");
	}
	
	/**
	 * Collect the new information added to the history file since the last
	 * time we checked it.
	 * @return A Vector with Hashtables, each hashtable containing information
	 * about a job.
	 */
	public Vector getHistoryInfo() {
		Vector histInfo = new Vector();
		String line;
		logger.log(Level.FINEST, "[VO_CondorAccounting] Checking Condor history file...");
		if (watcher == null)
		    return histInfo;

		BufferedReader br = watcher.getNewChunk();

		if (br == null) {
			logger.log(Level.FINEST, "[VO_CondorAccounting] Error getting buffer");
			return  histInfo;
		}
		
		try {
		    while ((line = br.readLine()) != null) {				
			    logBuffer.append(line);
				logBuffer.append("\n");
				if (line.startsWith("***")) { // we have a new record
					// parse the record that has just finished
					Hashtable histJobInfo = parseHistoryRecord(logBuffer);
					histInfo.add(histJobInfo);
					// reset the buffer
					logBuffer = new StringBuffer("");
				}
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "[VO_CondorAccounting] Error reading Condor history file", e);
		}
		
		return histInfo;
	}
	
	/**
	 * Obtains information about a job by parsing a record from the history
	 * file (it can also be used to parse the output of the condor_q -l command).
	 * @param rec Record extracted from the history file (contains multiple
	 * lines).
	 * @return Hashtable in which the keys are parameter names and the elements
	 * are the values of the parameters. The first letter of the parameter
	 * names corresponds with their type ('s' for String, 'd' for Double).
	 */
	Hashtable parseHistoryRecord(StringBuffer rec) {
		Hashtable jobInfo = new Hashtable();
		String line;
		String sRec = new String(rec);
		StringTokenizer st = new StringTokenizer(sRec, "\n");
		// some parameters appear twice in a record. This variable can br used
		// to make the difference between the first occurence and the
		// second one (if necessary):
		boolean finalRecords = false;
		double d;
		int idx;
		
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
			
			if (paramName.equals("JobStartDate")) {
				finalRecords = true; continue;
			}
			if (paramName.equals("ClusterId")) {
				jobInfo.put("sId", paramValue); continue;
			}
			if (paramName.equals("ProcId")) {
				jobInfo.put("procId", paramValue); continue;
			}
			if (paramName.equals("Owner")) {
				String nameWithoutQuotes = paramValue.replaceAll("\"", ""); 
				jobInfo.put("sOwner", nameWithoutQuotes); continue;
			}
			if (paramName.equals("Cmd")) {
				jobInfo.put("sCmd", paramValue); continue;
			}
			/* if these parameters appear twice in the record, the last value
			 * (which is the good one) will be kept
			 */
			if (paramName.equals("LocalUserCpu")) { // && finalRecords) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dLocalUserCpu", new Double(d)); continue;
			}
			if (paramName.equals("LocalSysCpu")) { // && finalRecords) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dLocalSysCpu", new Double(d)); continue;
			}
			if (paramName.equals("RemoteUserCpu")) { //&& finalRecords) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dRemoteUserCpu", new Double(d)); continue;
			}
			if (paramName.equals("RemoteSysCpu")) { //&& finalRecords) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dRemoteSysCpu", new Double(d)); continue;
			}
			if (paramName.equals("RemoteWallClockTime")) { //&& finalRecords) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dRemoteWallClockTime", new Double(d)); 
			}
			if (paramName.equals("ImageSize")) { //&& finalRecords) {
				d = Double.parseDouble(paramValue) / 1024; // measure in MB
				jobInfo.put("dImageSize", new Double(d)); continue;
			} 
			if (paramName.equals("BytesSent")) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dBytesSent", new Double(d)); continue;
			}
			if (paramName.equals("BytesRecvd")) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dBytesRecvd", new Double(d)); continue;
			}
			if (paramName.equals("FileReadBytes")) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dCondorFileReadBytes", new Double(d)); continue;
			}
			if (paramName.equals("FileWriteBytes")) {
				d = Double.parseDouble(paramValue);
				jobInfo.put("dFileWriteBytes", new Double(d)); continue;
			}
			if (paramName.equals("DiskUsage")) {
				d = Double.parseDouble(paramValue) / 1024;
				jobInfo.put("dDiskUsage", new Double(d)); continue;
			}
			
			if (paramName.equals("JobStatus")) {
				idx = Integer.parseInt(paramValue);
				if (idx >= 0 && idx <= 5)
					jobInfo.put("sStatus", jobStatusNames[idx]);
				else
					jobInfo.put("sStatus", "X"); // unknown status
			}
		}
		
		return jobInfo;
	}
	
	/**
	 * Parses the output of the condor_q -l command, which displays information
	 * in the same format as the history file.
	 * @param buff The output of the condor_q -l command.
	 * @return A Vector with JobInfoExt objects correspunding to the current jobs 
	 * @throws Exception
	 */
	Vector parseCondorQLongOutput (BufferedReader buff) throws Exception {
		Vector ret = new Vector();
		StringBuffer sb = new StringBuffer();
		
		boolean haveNewRecord = false;
		boolean haveErrorOutput = false; // shows if the command executed successfully
		boolean haveEmptyOutput = true;
		
		String scheddName = InetAddress.getLocalHost().getHostName();
		
		try {
			for (String lin = buff.readLine(); lin != null; lin=buff.readLine() ) {
				if (lin.length() > 0)
					haveEmptyOutput = false;				
								
				if (lin.indexOf("-- Schedd") >= 0) {
					StringTokenizer st = new StringTokenizer(lin, ": ");
					st.nextToken(); st.nextToken();
					scheddName = st.nextToken();
				}
				
				if (lin.indexOf("-- Submitter") >= 0 || lin.indexOf("Machine") >= 0
						|| lin.indexOf("-- Schedd") >= 0) {
					continue;
				}
				
				if (lin.length() != 0) { // && !lin.startsWith("-- Submitter") 
						//&& !lin.startsWith("-- Schedd")) {
					if (lin.indexOf("=") < 0 && lin.indexOf("All queues are empty") < 0) {
						// this is an error message
						haveErrorOutput = true;
						break;
					}
					sb.append(lin + "\n");
					haveNewRecord = true;
				} else  if (lin.length() == 0 && haveNewRecord && !haveErrorOutput) { 
					// the record for a job is finished, parse it
					haveNewRecord = false;
					JobInfoExt jobInfo = new JobInfoExt();
					jobInfo.jobManager = "CONDOR";
					Hashtable qInfo = parseHistoryRecord(sb);
										
					jobInfo.serverName = scheddName;
					jobInfo.user = (String)qInfo.get("sOwner");
					String clusterId = (String)qInfo.get("sId");
					String procId = (String)qInfo.get("procId");
					jobInfo.id = jobInfo.jobManager + "_" + 
						clusterId + "." + procId;
					if (scheddName != null)
						jobInfo.id += ("_" + scheddName);
					
					Double runtime = (Double)qInfo.get("dRemoteWallClockTime");
					if (runtime != null) {
						jobInfo.run_time = runtime.longValue();
						//jobInfo.run_time = System.currentTimeMillis() % 10000;
						//logger.finest("run_time: " + jobInfo.run_time);
					}
					
					jobInfo.status = (String)qInfo.get("sStatus");
					Double imsize = (Double)qInfo.get("dImageSize");
					if (imsize != null)
						jobInfo.size = imsize.doubleValue();
					
					Double dusage = (Double)qInfo.get("dDiskUsage");
					if (dusage != null)
						jobInfo.disk_usage = dusage.doubleValue();	
					
					Double cpuUsr = (Double)qInfo.get("dRemoteUserCpu");
					Double cpuSys = (Double)qInfo.get("dRemoteSysCpu");
					if (cpuUsr != null && cpuSys != null)
						jobInfo.cpu_time = cpuUsr.longValue() + cpuSys.longValue();
					
					ret.add(jobInfo);
					sb = new StringBuffer("");
				} // end of if
				
			} // end of for  
			
			if (logWarning) {
				if (haveErrorOutput && !haveEmptyOutput)
					logger.log(Level.INFO, "The condor_q command returned error or there are no jobs in the queues.");
			
				if (haveEmptyOutput) 
					logger.log(Level.INFO, "The condor_q command has an empty output.");
				logWarning = false;
			}
			
			if (!haveErrorOutput && !haveEmptyOutput)
				logWarning = true;
			
			if (haveErrorOutput)
				throw new Exception("condor_q -l returned error!");
			
		} catch (Throwable t) {
			logger.log(Level.WARNING, "ParseCondorQLongOutput got exception: ", t);
			throw new Exception("ParseCondorQLongOutput - " + t.getMessage()) ;
		} // end try/catch
		
		return ret;
	} // end method
	
	/**
	 * Parses the output of the condor_q command.
	 * @param buff The output of the condor_q command.
	 * @return A Vector with JobInfoExt objects correspunding to the 
	 * @throws Exception
	 */
	
	Vector parseCondorQOutput (BufferedReader buff) throws Exception {
		//--------------------------------------------------------------------
		// The integer values of the job statuses:
		//   5 - H (on hold), 
		//   2 - R (running), 
		//   1 - I (idle, waiting for a machine to execute on), 
		//  --------------------
		//  These states are not inlcuded in any metric:
		//   4 - C (completed), 
		//   ? - U (unexpanded - never been run), 
		//   3 - R (removed).
		
		Vector ret = new Vector();
		String scheddName = InetAddress.getLocalHost().getHostName();
		
		try {
			boolean canProcess = false;
			for (String lin = buff.readLine(); lin != null; lin=buff.readLine() ) {
				
				if (lin.equals("")) 
					continue;				
				
				//Find the specific fields so we can substring the line
				// ID      OWNER            SUBMITTED     RUN_TIME ST PRI SIZE CMD 
				// 185.0   sdss           10/8  20:09   0+00:00:00 R  0   0.0  data
				//
				if(!canProcess) {
					if(lin.indexOf("ID") != -1 && lin.indexOf("OWNER") != -1) {							
						canProcess = true;
					}
					continue;
				}
				
				// if we have a line like:
				// 35 jobs; 2 idle, 33 running, 0 held
				if(lin.indexOf("jobs;") != -1) {						
					canProcess = false;
					continue;
				}
				
				// if we have a line like:
				// -- Schedd: tier2b.cacr.caltech.edu : <192.168.0.254:33273>
				if (lin.indexOf("-- Schedd") >= 0) {
					StringTokenizer st = new StringTokenizer(lin, ": ");
					st.nextToken(); st.nextToken();
					scheddName = st.nextToken();	
					continue;
				}
				
				if (canProcess) {
					String[] columns = SPACE_PATTERN.split(lin.trim());
					
					if ( columns.length > 6 )  {
						JobInfoExt jobInfo = new JobInfoExt();
						jobInfo.jobManager = "CONDOR";
						
						String condorid = columns[0]; //.replaceFirst(".0", "");
						jobInfo.id = jobInfo.jobManager + "_" + condorid;
						if (scheddName != null)
							jobInfo.id += ("_" + scheddName);
						
						jobInfo.user      = columns[1];
						jobInfo.date      = columns[2];
						jobInfo.time      = columns[3];
						jobInfo.run_time   = parseCondorTime(columns[4]) / SEC_MILLIS;
						jobInfo.status    = columns[5];
						jobInfo.priority  = columns[6];
						jobInfo.size      = Double.parseDouble(columns[7]);
						
						ret.add(jobInfo);
					} // end of if
				} else { // canProcess == false
					throw new Exception("condor_q returned error");
				}
			} // end of for      
			
		} catch (Throwable t) {
			logger.log(Level.WARNING, "ParseCondorQOutput got exception: ", t);
			throw new Exception("ParseCondorQOutput - " + t.getMessage()) ;
		} // end try/catch
		
		return ret;
	} // end method
	
	/**
	 * Parses the "RUN_TIME" field from the output of the condor_q command.
	 * Time is given as dd+hh:mm:ss and the function returns the equivalent
	 * number of milliseconds. 
	 */
	private long parseCondorTime(String cpuTime){
		long sum = 0;
		
		String[] dh = cpuTime.split("\\+");
		
		sum += Long.parseLong(dh[0])*DAY_MILLIS;
		
		String[] hms = dh[1].split(":");
		
		sum +=  Long.valueOf(hms[0]).longValue()*HOUR_MILLIS;
		sum +=  Long.valueOf(hms[1]).longValue()*MIN_MILLIS;
		sum +=  Long.valueOf(hms[2]).longValue()*SEC_MILLIS;
		
		return sum;
	}
}
