package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class used by the monOsgVoJobs module to obtain accounting information
 * from LSF. It parses the output of the bjobs command, to obtain information
 * for currently running jobs.
 */
public class VO_LSFAccounting {

    private static final Logger logger = Logger.getLogger(VO_LSFAccounting.class.getName());

    /**
     * Parses the output of the bjobs -l command.
     * 
     * @param buff
     *            The output of the bjobs command.
     * @param nCommandsToCheck
     *            The number of commands that were executed.
     * @param checkExitStatus
     *            Specifies whether the command's exit status
     *            should be verified.
     * @return A Vector with JobInfoExt objects correspunding to the current jobs
     * @throws Exception
     */
    public static Vector<JobInfoExt> parseLSFOutput(BufferedReader buff, int nCommandsToCheck, boolean checkExitStatus)
            throws Exception {
        Vector<JobInfoExt> ret = new Vector<JobInfoExt>();
        String line;
        Integer parseErrCode = VO_Utils.jobMgrParseErrors.get(VO_Utils.LSF);
        int tmpIndex;
        int okCnt = 0; // the number of commands executed successfully.

        // Store the first 10 lines from the command's output so that
        // we can display them if there is an error
        StringBuilder firstLines = new StringBuilder();
        int lineCnt = 0;

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[VO_LSFAccounting] Parsing LSF output... ");
        }
        JobInfoExt jobInfo = null;
        try {
            while ((line = buff.readLine()) != null) {
                if (lineCnt < 10) {
                    firstLines.append(line + "\n");
                }
                lineCnt++;

                logger.finest("Parsing line from LSF bjobs output: " + line);

                if (line.startsWith(VO_Utils.okString)) {
                    okCnt++;
                    continue;
                }
                /* if we have a new job record */
                if ((jobInfo == null) || line.startsWith("----------")) {
                    if (jobInfo != null) {
                        ret.add(jobInfo);
                    }
                    // logger.finest("[VO_LSFAccounting] added job: " + jobInfo);
                    jobInfo = new JobInfoExt();
                    jobInfo.jobManager = "LSF";
                    jobInfo.status = "X"; // unknown status for the moment
                }

                /* parse the first lines to get the job ID, the user and the status */
                if (line.startsWith("Job")) {
                    /*
                     * concatentate with the following lines, until the line with
                     * "Submitted from host..."
                     */
                    StringBuilder sb = new StringBuilder(line.trim());
                    String line1 = null;
                    while ((line1 = buff.readLine()) != null) {
                        if (line1.indexOf("Submitted from host") >= 0) {
                            break;
                        }
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
                             * Possible values for job status:
                             * PEND, PSUSP, USUSP, SSUSP, WAIT => idle job
                             * RUN => running job
                             * DONE, EXIT => finished job
                             * UNKWN, ZOMBI => unknown status
                             */
                            if (st.hasMoreTokens()) {
                                tok = st.nextToken();
                            } else {
                                tok = "<UNKWN>";
                            }
                            if (!tok.endsWith(">")) {
                                tok = "<UNKWN>";
                            }

                            String status = tok.substring(1, tok.length() - 1);
                            if (status.equals("RUN")) {
                                jobInfo.status = "R";
                                continue;
                            }
                            if (status.equals("DONE")) {
                                jobInfo.status = "F";
                                jobInfo.exit_status = 0;
                                continue;
                            }
                            if (status.equals("EXIT")) {
                                jobInfo.status = "F";
                                /*
                                 * LSF does not give the exit code of the job, so just
                                 * put a non-zero value here...
                                 */
                                jobInfo.exit_status = 1;
                                continue;
                            }
                            if (status.equals("UNKWN") || status.equals("ZOMBI")) {
                                jobInfo.status = "U";
                                continue;
                            }
                            /* otherwise */
                            jobInfo.status = "I";
                        }
                    }
                    continue;
                } // first line parsed

                /* parse the line which contains the start date */
                if (line.indexOf("Started on") > 0) {
                    // can't compute run_time like this because we don't know if the
                    // job has been running without interruption
                    /*
                     * if (jobInfo.status != "F") {
                     * Date crtDate = new Date();
                     * jobInfo.run_time = (crtDate.getTime() - startDate.getTime()) /
                     * 1000;
                     * } else
                     */
                    jobInfo.run_time = 0;
                    continue;
                }

                /*
                 * line which contains the CPU time, looking like this:
                 * The CPU time used is 30 seconds.
                 */
                if ((tmpIndex = line.indexOf("The CPU time used is")) > 0) {
                    try {
                        String tline = line.substring(tmpIndex).replaceFirst("The CPU time used is", "");
                        StringTokenizer st = new StringTokenizer(tline, " \t");
                        jobInfo.cpu_time = Long.parseLong(st.nextToken());
                    } catch (Exception e) {
                        logger.fine("Unable to parse CPU time: " + line);
                    }
                    continue;
                }

                /*
                 * line which gives the memory & swap usage, looking like this:
                 * MEM: 8 Mbytes; SWAP: 80 Mbytes
                 */
                if ((tmpIndex = line.indexOf("MEM:")) > 0) {
                    try {
                        String tline = line.substring(tmpIndex).replaceFirst("MEM:", "");
                        StringTokenizer st = new StringTokenizer(tline, " \t");
                        jobInfo.size = Double.parseDouble(st.nextToken()) * valMultiplier(st.nextToken());

                        /*
                         * in the "size" field we store the total amount of virtual
                         * memory
                         */
                        if (st.hasMoreTokens() && st.nextToken().equals("SWAP")) {
                            jobInfo.size += Double.parseDouble(st.nextToken()) * valMultiplier(st.nextToken());
                        }
                    } catch (Exception e) {
                        logger.info("Error parsing line: " + line
                                + "\n Memory information will not be reported for the job.");
                    }
                    continue;
                }

            } // while
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Error parsing the output of bjobs - sample from command output:\n " + firstLines.toString());
            throw e;
        }
        if ((okCnt < nCommandsToCheck) && checkExitStatus) {
            // logger.warning("Error output from bjobs: " + firstLines.toString());
            throw new ModuleException("The bjobs command returned with error - command output:\n"
                    + firstLines.toString(), parseErrCode.intValue());
        }
        return ret;
    }

    public static double valMultiplier(String unit) {
        if (unit.startsWith("M")) {
            return 1;
        }
        if (unit.startsWith("G")) {
            return 1024;
        }
        if (unit.startsWith("K")) {
            return 1.0 / 1024;
        }
        return 1;
    }
}
