package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information 
 * from SGE. It parses the output of the qstat command (to obtain information  
 * for currently running jobs).
 */
public class VO_SGEAccounting {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VO_SGEAccounting.class.getName());
    private static final Pattern SPACE_PATTERN = Pattern.compile("(\\s)+");
    private static final long SEC_MILLIS = 1000;
    private static final long MIN_MILLIS = 60 * SEC_MILLIS;
    private static final long HOUR_MILLIS = 60 * MIN_MILLIS;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;

    /**
     * Parses the output of the qstat -ext command.
     * @param buff The output of the qstat command.
     * @param nCommandsToCheck The number of commands that were executed.
     * @param checkExitStatus Specifies whether the commands' exit status
     * should be verified.
     * @return A Vector with JobInfoExt objects correspunding to the current jobs 
     * @throws Exception
     */
    public static Vector<JobInfoExt> parseSGEOutput(BufferedReader buff, int nCommandsToCheck, boolean checkExitStatus)
            throws Exception {

        Vector<JobInfoExt> ret = new Vector<JobInfoExt>();
        Integer retErrCode = VO_Utils.jobMgrCmdErrors.get(VO_Utils.SGE);

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        int okCnt = 0;

        int job_id = -1, user_id = -1, date_id = -1, time_id = -1, cpu_id = -1, size_id = -1, status_id = -1, ja_task_ID = -1;

        boolean canProcess = false;
        boolean isFirstLine = true;
        for (String lin = buff.readLine(); lin != null; lin = buff.readLine()) {
            if (isFirstLine) {
                isFirstLine = false;
                /*  Some example of table header in qstat -ext output
                 * 
                 *  0      1     2      3    4    5       6          7     8   9   10 11    12    13    14   15    16    17    18    19
                 *  job-ID prior ntckts name user project department state cpu mem io tckts ovrts otckt tckt stckt share queue slots ja-task-ID
                 *  
                 *  0      1     2    3    4       5          6     7               8        9   10  11 12    13    14    15    16    17    18    19    20     21
                 *  job-ID prior name user project department state submit/start at deadline cpu mem io tckts ovrts otckt dtckt ftckt stckt share queue master ja-task-ID
                 */
                String REGEX = "start at";
                String REPLACE = "start_at";
                if (lin.indexOf("submit/start at") >= 0) {
                    Pattern p = Pattern.compile(REGEX);
                    Matcher m = p.matcher(lin); // get a matcher object
                    lin = m.replaceAll(REPLACE);
                }

                String[] tableColumns = SPACE_PATTERN.split(lin.trim());

                for (int i = 0; i < tableColumns.length; i++) {
                    if (tableColumns[i].equals("job-ID")) {
                        job_id = i;
                    } else if (tableColumns[i].equals("user")) {
                        user_id = i;
                    } else if (tableColumns[i].equals("submit/start_at")) {
                        date_id = i;
                    } else if (tableColumns[i].equals("deadline")) {
                        time_id = i;
                    } else if (tableColumns[i].equals("cpu")) {
                        cpu_id = i;
                    } else if (tableColumns[i].equals("mem")) {
                        size_id = i;
                    } else if (tableColumns[i].equals("state")) {
                        status_id = i;
                    } else if (tableColumns[i].equals("ja-task-ID")) {
                        ja_task_ID = i;
                    }
                }
            }

            if (lineCnt < 10) {
                firstLines.append(lin + "\n");
            }
            lineCnt++;

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Parsing line from SGE qstat output: " + lin);
            }

            if (lin.startsWith(VO_Utils.okString)) {
                okCnt++;
                continue;
            }

            if (!canProcess) {
                if (lin.indexOf("----------") != -1) {
                    canProcess = true;
                }
                continue;
            }

            String[] columns = SPACE_PATTERN.split(lin.trim());

            if (columns.length > 12) {
                JobInfoExt jobInfo = new JobInfoExt();
                jobInfo.jobManager = "SGE";

                if (job_id >= 0) {
                    jobInfo.id = jobInfo.jobManager + "_" + columns[job_id];
                    if ((ja_task_ID >= 0) && (ja_task_ID < columns.length)) {
                        jobInfo.id += "_" + columns[ja_task_ID];
                    }
                }

                if ((user_id >= 0) && (user_id < columns.length)) {
                    jobInfo.user = columns[user_id];
                }
                if ((date_id >= 0) && (date_id < columns.length)) {
                    jobInfo.date = columns[date_id];
                }
                if ((time_id >= 0) && (time_id < columns.length)) {
                    jobInfo.time = columns[time_id];
                }

                try {
                    if ((cpu_id >= 0) && (cpu_id < columns.length)) {
                        jobInfo.cpu_time = parseSGETime(columns[cpu_id]) / SEC_MILLIS;
                    }
                    if ((size_id >= 0) && (size_id < columns.length)) {
                        jobInfo.size = Double.parseDouble(columns[size_id]) / 1024;
                    }
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

                if (status_id >= 0) {
                    String status = columns[status_id];

                    //FROM OLD monVoJobs
                    // This code is dealing with the multiple character job states
                    // shown in the example above. The last character is considered the
                    // current state.
                    if (status.length() > 1) {
                        status = status.substring(status.length() - 1);
                    }

                    jobInfo.status = "U"; // unknown for the moment
                    if (status.equals("r") || status.equals("t") || status.equals("R")) {
                        jobInfo.status = "R";
                    } else if (status.equals("h")) {
                        jobInfo.status = "H";
                    } else if (status.equals("s") || status.equals("T") || status.equals("w")) {
                        jobInfo.status = "I";
                    }
                }

                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "[VO_SGEAccounting] Got jobInfo:" + jobInfo);
                }

                ret.add(jobInfo);
            } // end of if

        } // end of for      

        if ((okCnt < nCommandsToCheck) && checkExitStatus) {
            //logger.warning("Error output from SGE qstat: " + firstLines.toString());
            throw new ModuleException("The SGE qstat command returned with error - command output:\n"
                    + firstLines.toString(), retErrCode.intValue());
        }

        return ret;
    } // end method

    /**
     * Parses the "cpu" field from the output of the qstat command.
     * Time is given as dd:hh:mm:ss and the function returns the equivalent
     * number of milliseconds. 
     */
    private static long parseSGETime(String cpuTime) {
        long sum = 0;

        String[] hms = cpuTime.split(":");
        if (hms.length == 4) {
            sum += ((Long.valueOf(hms[0]).longValue() * DAY_MILLIS) + (Long.valueOf(hms[1]).longValue() * HOUR_MILLIS)
                    + (Long.valueOf(hms[2]).longValue() * MIN_MILLIS) + (Long.valueOf(hms[3]).longValue() * SEC_MILLIS));
        } else if (hms.length == 3) {
            sum += ((Long.valueOf(hms[0]).longValue() * HOUR_MILLIS) + (Long.valueOf(hms[1]).longValue() * MIN_MILLIS) + (Long
                    .valueOf(hms[2]).longValue() * SEC_MILLIS));
        } else if (hms.length == 2) {
            sum += ((Long.valueOf(hms[0]).longValue() * MIN_MILLIS) + (Long.valueOf(hms[1]).longValue() * SEC_MILLIS));
        } else if (hms.length == 1) {
            sum += Long.valueOf(hms[0]).longValue() * SEC_MILLIS;
        }
        return sum;
    }
}
