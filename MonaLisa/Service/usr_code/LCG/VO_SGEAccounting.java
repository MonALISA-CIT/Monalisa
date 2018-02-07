
//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.Vector;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information 
 * from SGE. It parses the output of the qstat command (to obtain information  
 * for currently running jobs).
 */
public class VO_SGEAccounting {
	/** Logger used by this class */
	private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.VO_SGEAccounting");
	private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");
	private static final long SEC_MILLIS      =   1000;
	private static final long MIN_MILLIS      =   60*SEC_MILLIS;
	private static final long HOUR_MILLIS     =   60*MIN_MILLIS;
	private static final long DAY_MILLIS      =   24*HOUR_MILLIS;
	
	/**
	 * Parses the output of the qstat -ext command.
	 * @param buff The output of the qstat command.
	 * @return A Vector with JobInfoExt objects correspunding to the current jobs 
	 * @throws Exception
	 */
	public static Vector parseSGEOutput (BufferedReader buff) throws Exception {
		
		Vector ret = new Vector();
		
		try {
			boolean canProcess = false;
			for (String lin = buff.readLine(); lin != null; lin=buff.readLine() ) {
				try {					
					if(!canProcess) {
						if(lin.indexOf("----------") != -1) {
							canProcess = true;
						}
						continue;
					}
					
					String[] columns = SPACE_PATTERN.split(lin.trim());
					
					if ( columns.length > 12 )  {
						JobInfoExt jobInfo = new JobInfoExt();
						jobInfo.jobManager = "SGE";
						jobInfo.id        = jobInfo.jobManager + "_" + columns[0];
						jobInfo.user      = columns[3];
						jobInfo.date      = columns[7];
						jobInfo.time      = columns[8];
						
						try {
							jobInfo.cpu_time   = parseSGETime(columns[9]) / SEC_MILLIS;
							jobInfo.size      = Double.parseDouble(columns[10]) / 1024;
						} catch (Exception e) {
							logger.log(Level.FINEST, "[VO_SGEAccounting] CPU Time or size not available");
							jobInfo.cpu_time = 0;
							jobInfo.size = 0;
						}
						
						// Job statuses:
						//   t = transferring, 
						//   r = running,
						//   R = restarted
						//   s = suspended
						//   T = threshold
						//   w = waiting
						//   h = hold						
						
						String status  = columns[6];
						jobInfo.status = "U"; // unknown for the moment
						if ( status.equals("r") || status.equals("t") || 
								status.equals("R"))  { 
							jobInfo.status = "R";					
						}										
						else if ( status.equals("h"))  { 
							jobInfo.status = "H";	
						}
						else if ( status.equals("s") || status.equals("T") || 
								status.equals("w"))  { 
							jobInfo.status = "I";	
						}
						logger.log(Level.FINEST, "[VO_SGEAccounting] Got jobInfo:" +
								jobInfo);						
						ret.add(jobInfo);
					} // end of if
				} catch (Throwable t) {
					logger.log(Level.WARNING, "[VO_SGEAccounting] Got exc parsing qstat output at line [" + lin + "]", t);
				}
			} // end of for      
			
		} catch (Throwable t) {
			throw new Exception("parseSGEOutput - " + t.getMessage()) ;
		} // end try/catch
		
		return ret;
	} // end method
	
	/**
	 * Parses the "cpu" field from the output of the qstat command.
	 * Time is given as dd:hh:mm:ss and the function returns the equivalent
	 * number of milliseconds. 
	 */
	private static long parseSGETime(String cpuTime){
		long sum = 0;
		
		String[] hms = cpuTime.split(":");
		if (hms.length == 4) {
			sum +=  (Long.valueOf(hms[0]).longValue()*DAY_MILLIS +
					Long.valueOf(hms[1]).longValue()*HOUR_MILLIS +
					Long.valueOf(hms[2]).longValue()*MIN_MILLIS +
					Long.valueOf(hms[3]).longValue()*SEC_MILLIS);
		} else if (hms.length == 3) {
				sum +=  (Long.valueOf(hms[0]).longValue()*HOUR_MILLIS +
						Long.valueOf(hms[1]).longValue()*MIN_MILLIS +
						Long.valueOf(hms[2]).longValue()*SEC_MILLIS);
		} else if (hms.length == 2) {
			sum +=  (Long.valueOf(hms[0]).longValue()*MIN_MILLIS +
					Long.valueOf(hms[1]).longValue()*SEC_MILLIS);
		} else if (hms.length == 1) {
			sum +=  Long.valueOf(hms[0]).longValue()*SEC_MILLIS;
		}		
		return sum;
	}
}
