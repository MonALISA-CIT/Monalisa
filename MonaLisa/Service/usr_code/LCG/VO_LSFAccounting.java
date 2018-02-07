//package lia.Monitor.modules;

import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;
import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;
import java.text.SimpleDateFormat; 
import java.text.ParsePosition; 

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information 
 * from LSF. It parses the output of the bjobs command, to obtain information  
 * for currently running jobs. 
 */
public class VO_LSFAccounting {
	private static final transient Logger logger = 
		Logger.getLogger("lia.Monitor.modules.VO_LSFAccounting");
	
	/**
	 * Parses the output of the bjobs -l command.
	 * @param buff The output of the bjobs command.
	 * @return A Vector with JobInfoExt objects correspunding to the current jobs 
	 * @throws Exception
	 */
	public static Vector parseLSFOutput (BufferedReader buff) throws Exception {
		Vector ret = new Vector();
		String line;
		Date startDate = null;
		
		logger.log(Level.FINEST, "[VO_LSFAccounting] Parsing LSF output... " );
		JobInfoExt jobInfo = null; 
		while ((line = buff.readLine()) != null) {
			/* if we have a new job record */
			if (jobInfo == null || line.startsWith("----------")) {
				if (jobInfo != null)
					ret.add(jobInfo);
				//logger.finest("[VO_LSFAccounting] added job: " + jobInfo);
				jobInfo = new JobInfoExt();
				jobInfo.jobManager = "LSF";
				jobInfo.status = "X"; // unknown status for the moment
			}
			
			
			/* parse the first lines to get the job ID, the user and the status */
			if (line.startsWith("Job")) {
				/* concatentate with the following lines, until the line with
				   "Submitted from host..." */
				StringBuffer sb = new StringBuffer(line.trim());
				String line1 = null;
				while ((line1 = buff.readLine()) != null) {
					if (line1.indexOf("Submitted from host") >= 0)
						break;
					else
						sb.append(line1.trim());
				}
				StringTokenizer st = new StringTokenizer(sb.toString(), ", ");
				
				/* job ID: "Job <12345>" */
				String tok = st.nextToken(); 
				tok = st.nextToken();
				jobInfo.id = "LSF_" + tok.substring(1, tok.length() - 1);
							
				while (st.hasMoreTokens()) {
					tok = st.nextToken();
					
					/* user: "User <dteam001>" */
					if (tok.equals("User")) {
						tok = st.nextToken();
						jobInfo.user = tok.substring(1, tok.length() - 1);
						continue;
					}
					
					if (tok.equals("Status")) {
						/* 
						Possible values for job status:
						PEND, PSUSP, USUSP, SSUSP, WAIT => idle job
						RUN => running job
						DONE, EXIT => finished job
						UNKWN, ZOMBI => unknown status
						*/
						if (st.hasMoreTokens())
							tok = st.nextToken();
						else
							tok = "<UNKWN>";
						if (!tok.endsWith(">"))
							tok = "<UNKWN>";
						
						String status = tok.substring(1, tok.length() - 1);
						if (status.equals("RUN")) {
							jobInfo.status = "R";
						} else { 
							if (status.equals("DONE") || status.equals("EXIT")) {
								jobInfo.status = "F";
							} else {
								if (status.equals("UNKWN") || status.equals("ZOMBI"))
									jobInfo.status = "U";
								else
									jobInfo.status = "I";
							}
						}
					}
				} 
				continue;
			} // first line parsed	
			
			/* parse the line which contains the start date */
			if (line.indexOf("Started on") > 0) {
				SimpleDateFormat dateFmt = new SimpleDateFormat( 
				"EEE MMM dd HH:mm:ss");                 
				ParsePosition ppos = new ParsePosition(0); 	
				startDate = dateFmt.parse(line, ppos);
				
				// can't compute run_time like this because we don't know if the 
				// job has been running without interruption
				/*
				if (jobInfo.status != "F") {
					Date crtDate = new Date();
					jobInfo.run_time = (crtDate.getTime() - startDate.getTime()) /
						1000;
				} else
				*/
					jobInfo.run_time = 0;
				continue;
			}
			
			/* line which contains the CPU time, looking like this: 
			 * The CPU time used is 30 seconds.
			 */
			if (line.indexOf("The CPU time used is") > 0) {
				String tline = line.replaceFirst("The CPU time used is", "");
				StringTokenizer st = new StringTokenizer(tline, " \t");
				jobInfo.cpu_time = Long.parseLong(st.nextToken());
				continue;
			}
			
			/* line which gives the memory & swap usage, looking like this:
			 *  MEM: 8 Mbytes;  SWAP: 80 Mbytes
			 */  
			if (line.indexOf("MEM:") > 0) {
				String tline = line.replaceFirst("MEM:", "");
				StringTokenizer st = new StringTokenizer(tline, " \t");
				jobInfo.size = Double.parseDouble(st.nextToken());
				
				/* in the "size" field we store the total amount of virtual
				   memory */
				st.nextToken(); st.nextToken();
				jobInfo.size += Double.parseDouble(st.nextToken());
				continue;
			}
			
		}
		return ret;
	}
}
