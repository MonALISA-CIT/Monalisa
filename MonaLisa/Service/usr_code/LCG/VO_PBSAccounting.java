//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information 
 * from PBS. It parses the output of the qstat command, to obtain information  
 * for currently running jobs. 
 */
public class VO_PBSAccounting {
	private static final long SEC_MILLIS      =   1000;
	private static final long MIN_MILLIS      =   60*SEC_MILLIS;
	private static final long HOUR_MILLIS     =   60*MIN_MILLIS;
	private static final long DAY_MILLIS      =   24*HOUR_MILLIS;
	
	/**
	 * Parses the output of the qstat command.
	 * @param buff The output of the qstat command.
	 * @return A Vector with JobInfoExt objects correspunding to the current jobs 
	 * @throws Exception
	 */
	public static Vector  parsePBSOutput(BufferedReader buff) throws Exception {
		/* 
		 Job states:
		 R - job is running.
		 C - Job is completed and leaving the queue on it's own.
		 E - Job is exiting after having run.
		 H - Job is held.
		 Q - job is queued, eligable to run or routed.
		 T - job is being moved to new location.
		 I - job is idle.
		 W - job is waiting for its execution time
		 (-a option) to be reached.
		 S - (Unicos only) job is suspend.
		 ----------
		 These states are not included in any metric:
		 X - Job is removed from the queue
		 */
		Vector ret = new Vector();
		StringTokenizer tz;
		int linecnt = 0;
		int maxlinecnt = 7;
		boolean startParsing = false;
		
		try {
			for ( ; ; ) {
				String lin = buff.readLine();
				
				//--- end of file ----
				if ( lin == null )  break;
				
				linecnt++;
				
				if (lin.indexOf("----") >= 0) {
					startParsing = true;
					continue;
				}				
				if (startParsing == false)
					continue;
				
				
				//if ( lin.equals("") ) break;
				
				JobInfoExt jobInfo = new JobInfoExt();
				jobInfo.jobManager = "PBS";
				
				//Find the specific fields so we can substring the line
				//Job id       Name             User             Time Use S Queue
				//------------ ---------------- ---------------- -------- - -----
				//22930.bh1    calmob           uscms01          70:21:58 R bg
				//
				tz = new StringTokenizer ( lin ) ;
				int ni = tz.countTokens();
				if ( ni > 4 )  {
					jobInfo.id     = jobInfo.jobManager + "_" + tz.nextToken().trim();
					String jname   = tz.nextToken().trim();
					jobInfo.user    = tz.nextToken().trim();
					String cputime = tz.nextToken().trim();
					jobInfo.cpu_time = parsePBSTime(cputime) / SEC_MILLIS;
					
					String status  = tz.nextToken().trim();					
					if ( status.equals("R") || status.equals("C") || 
							status.equals("E") || status.equals("S"))  { 
						jobInfo.status = "R";					
					}										
					else if ( status.equals("H"))  { 
						jobInfo.status = "H";	
					}
					else if ( status.equals("Q") || status.equals("T") || 
							status.equals("I") || status.equals("W"))  { 
						jobInfo.status = "I";	
					}
					ret.add(jobInfo);
				} // end if > 4
				
				
			} // end of for
			
			return ret;
		} catch (Exception e) {
			throw new Exception("ParsePBSOutput - " +e.getMessage()) ;
		} // end try/catch
	} // end method
	
	/**
	 * Parses the time field from the output of the qstat command. 
	 */
	private static long parsePBSTime(String cpuTime){
		long sum = 0;
		
		String[] hms = cpuTime.split(":");
		if (hms.length == 3) {
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
