package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that containts some useful functions and error codes definitions
 * for the VO modules.
 * 
 * @author corina
 */
public final class VO_Utils {

    public static final String okString = "ML_OSGVOJOBS_OK";

    /* ===================== Error codes for monOsgVoJobs ======================== */

    /* internal error codes for monOsgVoJobs */
    public static final int INTERNAL_JMGR_GEN = 1;

    public static final int INTERNAL_JMGR_COLLECT = 11;

    public static final int INTERNAL_JMGR_INCONSIST = 112;

    public static final int INTERNAL_JMGR_TIMEOUT = 13;

    public static final int INTERNAL_JMGR_VOSUMCONDOR = 114;

    public static final int INTERNAL_JMGR_VOSUMPBS = 115;

    public static final int INTERNAL_JMGR_SETENV = 16;

    public static final int INTERNAL_FIRST_EXEC = 117;

    public static final int INTERNAL_JMGR_VERSION = 118;

    /* Condor error codes */
    public static final int CONDOR_NO_HIST = 130;

    /* ===================== Error codes for monPN_* ============================= */

    public static final int PN_SUCCESS = 0;

    public static final int PN_NON_TORQUE = 1;

    public static final int PN_PARSE_ERROR = 2;

    public static final int PN_NULL_OUTPUT = 3;

    public static final int PN_SECOND_NULL_OUTPUT = 4;

    public static final int PN_CMD_NOT_EXIST = 5;

    public static final int PN_COMMAND_TIMEOUT = 6;

    public static final int PN_RET_ERROR = 7;

    /**
     * Contains mappings between error codes and error messages for
     * the monOsgVoJobs module.
     */
    public static final HashMap<Integer, String> voJobsErrCodes = new HashMap<Integer, String>();

    /**
     * Contains mappings between error codes and error messages for
     * the monOsgVO_IO module.
     */
    public static final HashMap<Integer, String> voIOErrCodes = new HashMap<Integer, String>();

    /**
     * Contains mappings between error codes and error messages for
     * the monVoModules; these are used by all the VO modules.
     */
    public static final HashMap<Integer, String> voErrCodes = new HashMap<Integer, String>();

    /**
     * Contains mappings between error codes and error messages for
     * the monPN_PBS and monPN_Condor modules; these are used by all the pn modules.
     */
    public static final HashMap<Integer, String> pnErrCodes = new HashMap<Integer, String>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error codes returned when there was an error executing the
     * job manager command.
     */
    public static final HashMap<String, Integer> jobMgrCmdErrors = new HashMap<String, Integer>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error codes returned when a null output is obtained when executing the
     * job manager command.
     */
    public static final HashMap<String, Integer> jobMgrNullErrors = new HashMap<String, Integer>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error codes returned when there was a problem when reading the logs.
     */
    public static final HashMap<String, Integer> jobMgrLogErrors = new HashMap<String, Integer>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error codes returned when there was an error parsing the output
     * of the command.
     */
    public static final HashMap<String, Integer> jobMgrParseErrors = new HashMap<String, Integer>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error coed returned when there was an error parsing a job record
     * contained in the command's output.
     */
    public static final HashMap<String, Integer> jobMgrRecordErrors = new HashMap<String, Integer>();

    /**
     * Contains mappings between job manager names and the corresponding
     * error codes returned when duplicate job IDs are detected.
     */
    public static final HashMap<String, Integer> jobMgrDuplicateIds = new HashMap<String, Integer>();

    // save some GC-s! and some speed at .equals()
    public static final String CONDOR = "CONDOR";

    public static final String CONDOR2 = "CONDOR2";

    public static final String CONDOR3 = "CONDOR3";

    public static final String PBS = "PBS";

    public static final String PBS2 = "PBS2";

    public static final String LSF = "LSF";

    public static final String FBS = "FBS";

    public static final String SGE = "SGE";

    static {
        /* internal errors for monOsgVoJobs */
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_COLLECT), "Internal error when collecting job manager data.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_INCONSIST), "Inconsistent job information.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_TIMEOUT), "Timeout at command execution.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_VOSUMCONDOR), "Error updating VO summary from Condor history file.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_VOSUMPBS), "Error updating VO summary from PBS accounting log.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_SETENV), "Error setting the module's environment.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_FIRST_EXEC), "Error at the first command execution.");
        voJobsErrCodes.put(Integer.valueOf(INTERNAL_JMGR_VERSION), "Could not obtain job manager verison.");

        /* errors when executing the commands */
        jobMgrCmdErrors.put(CONDOR, Integer.valueOf(21));
        voJobsErrCodes.put(Integer.valueOf(21), "The condor_q -l command returned error.");
        jobMgrCmdErrors.put(CONDOR2, Integer.valueOf(22));
        voJobsErrCodes.put(Integer.valueOf(22), "The condor_q command returned error.");
        jobMgrCmdErrors.put(CONDOR3, Integer.valueOf(23));
        voJobsErrCodes.put(Integer.valueOf(23), "The condor_q -format command returned error.");
        jobMgrCmdErrors.put(PBS, Integer.valueOf(41));
        voJobsErrCodes.put(Integer.valueOf(41), "The PBS qsat command returned error.");
        jobMgrCmdErrors.put(PBS2, Integer.valueOf(42));
        voJobsErrCodes.put(Integer.valueOf(42), "The PBS qsat -a command returned error.");
        jobMgrCmdErrors.put(LSF, Integer.valueOf(61));
        voJobsErrCodes.put(Integer.valueOf(61), "The LSF bjobs command returned error.");
        jobMgrCmdErrors.put(SGE, Integer.valueOf(81));
        voJobsErrCodes.put(Integer.valueOf(81), "The SGE qstat command returned error.");

        /* null output when executing the commands */
        jobMgrNullErrors.put(CONDOR, Integer.valueOf(24));
        voJobsErrCodes.put(Integer.valueOf(24), "Null output for the condor_q -l command.");
        jobMgrNullErrors.put(CONDOR2, Integer.valueOf(25));
        voJobsErrCodes.put(Integer.valueOf(25), "Null output for the condor_q command.");
        jobMgrNullErrors.put(CONDOR3, Integer.valueOf(26));
        voJobsErrCodes.put(Integer.valueOf(26), "Null output for the condor_q -format command.");
        jobMgrNullErrors.put(PBS, Integer.valueOf(44));
        voJobsErrCodes.put(Integer.valueOf(44), "Null output for the PBS qsat command.");
        jobMgrNullErrors.put(PBS2, Integer.valueOf(45));
        voJobsErrCodes.put(Integer.valueOf(45), "Null output for the PBS qsat -a command.");
        jobMgrNullErrors.put(LSF, Integer.valueOf(64));
        voJobsErrCodes.put(Integer.valueOf(64), "Null output for the LSF bjobs command.");
        jobMgrNullErrors.put(SGE, Integer.valueOf(84));
        voJobsErrCodes.put(Integer.valueOf(84), "Null output for the SGE qstat command.");

        /* Condor errors */
        voJobsErrCodes.put(Integer.valueOf(CONDOR_NO_HIST),
                           "No Condor history information will be provided (either the feature was disabled or the history file does not exist).");

        /* Log errors */
        jobMgrLogErrors.put(CONDOR, Integer.valueOf(131));
        voJobsErrCodes.put(Integer.valueOf(131), "Error reading Condor history log.");
        jobMgrLogErrors.put(PBS, Integer.valueOf(151));
        voJobsErrCodes.put(Integer.valueOf(151), "Error reading PBS history log.");

        /* Parse errors */
        jobMgrParseErrors.put(CONDOR, Integer.valueOf(33));
        voJobsErrCodes.put(Integer.valueOf(33), "Error parsing the output of the condor_q -long command.");
        jobMgrParseErrors.put(CONDOR3, Integer.valueOf(34));
        voJobsErrCodes.put(Integer.valueOf(34), "Error parsing the output of the condor_q -format command.");
        jobMgrParseErrors.put(CONDOR2, Integer.valueOf(35));
        voJobsErrCodes.put(Integer.valueOf(35), "Error parsing the output of the condor_q command.");
        jobMgrParseErrors.put(PBS, Integer.valueOf(53));
        voJobsErrCodes.put(Integer.valueOf(53), "Error parsing the output of the PBS qstat command.");
        jobMgrParseErrors.put(PBS2, Integer.valueOf(54));
        voJobsErrCodes.put(Integer.valueOf(54), "Error parsing the output of the PBS qstat -a command.");
        jobMgrParseErrors.put(LSF, Integer.valueOf(73));
        voJobsErrCodes.put(Integer.valueOf(73), "Error parsing the output of the LSF bjobs command.");
        jobMgrParseErrors.put(SGE, Integer.valueOf(93));
        voJobsErrCodes.put(Integer.valueOf(93), "Error parsing the output of the SGE qstat command.");

        /* Record errors */
        jobMgrRecordErrors.put(CONDOR, Integer.valueOf(132));
        voJobsErrCodes.put(Integer.valueOf(132), "Error parsing Condor job record");
        jobMgrRecordErrors.put(PBS, Integer.valueOf(143));
        voJobsErrCodes.put(Integer.valueOf(143), "Error parsing PBS job record");

        /* Duplicate IDs errors */
        jobMgrDuplicateIds.put(CONDOR, Integer.valueOf(136));
        jobMgrDuplicateIds.put(CONDOR2, Integer.valueOf(136));
        jobMgrDuplicateIds.put(CONDOR3, Integer.valueOf(136));
        voJobsErrCodes.put(Integer.valueOf(136), "Duplicate job IDs reported by Condor");
        jobMgrDuplicateIds.put(PBS, Integer.valueOf(155));
        jobMgrDuplicateIds.put(PBS2, Integer.valueOf(155));
        voJobsErrCodes.put(Integer.valueOf(155), "Duplicate job IDs reported by PBS");
        jobMgrDuplicateIds.put(LSF, Integer.valueOf(174));
        voJobsErrCodes.put(Integer.valueOf(174), "Duplicate job IDs reported by LSF");
        jobMgrDuplicateIds.put(SGE, Integer.valueOf(194));
        voJobsErrCodes.put(Integer.valueOf(194), "Duplicate job IDs reported by SGE");

        /* VO_IO errors */
        voIOErrCodes.put(Integer.valueOf(0), "Success");
        voIOErrCodes.put(Integer.valueOf(1), "Error in processing value for gridftp log parameters.");
        voIOErrCodes.put(Integer.valueOf(2), "Transfer CODE not 226 (success).");
        voIOErrCodes.put(Integer.valueOf(3), "Error in parsing line from gridftp logfile.");
        voIOErrCodes.put(Integer.valueOf(4), "Error reading the gridftp logfile.");
        voIOErrCodes.put(Integer.valueOf(5), "Gridftp log not found. No GridFTP transfer information will be provide.");

        /* PN errors */
        pnErrCodes.put(Integer.valueOf(PN_SUCCESS), "Success");
        pnErrCodes.put(Integer.valueOf(PN_NON_TORQUE), "non-Torque PBS version.");
        pnErrCodes.put(Integer.valueOf(PN_PARSE_ERROR), "Error parsing the output of command.");
        pnErrCodes.put(Integer.valueOf(PN_NULL_OUTPUT), "Null output for the command.");
        pnErrCodes.put(Integer.valueOf(PN_SECOND_NULL_OUTPUT), "Second attempt: Null output for the command.");
        pnErrCodes.put(Integer.valueOf(PN_CMD_NOT_EXIST), "The command does not exist.");
        pnErrCodes.put(Integer.valueOf(PN_COMMAND_TIMEOUT), "Timeout at command execution.");
        pnErrCodes.put(Integer.valueOf(PN_RET_ERROR), "The command returned error.");

        /* general VO errors */
        voErrCodes.put(Integer.valueOf(1), "ERROR in VO mapping file.");
        voErrCodes.put(Integer.valueOf(2), "VO map file is not readable.");
        voErrCodes.put(Integer.valueOf(3), "VO map file not found.");
    }

    /**
     * Parses an entry of the form "parameter = value" from the argument list
     * of a module, for string values.
     */
    public static String getSArgumentValue(String listEntry, String argName, Logger logger) {
        String retVal = null;

        if (!listEntry.toLowerCase().startsWith(argName))
            return null;

        try {
            retVal = listEntry.split("(\\s)*=(\\s)*")[1].trim();
        } catch (Throwable t) {
            logger.log(Level.INFO, " Got exception parsing module argument " + listEntry + ": ", t);
            return null;
        }

        return retVal;
    }

    /**
     * Parses an entry of the form "parameter = value" from the argument list,
     * for boolean values (on/off).
     */
    public static Boolean getBArgumentValue(String listEntry, String argName, Logger logger) {
        Boolean retVal = null;
        String sVal;

        if (!listEntry.toLowerCase().startsWith(argName))
            return null;

        if (argName.equals((listEntry.trim()).toLowerCase())) {
            retVal = new Boolean(true);
        } else {
            try {
                sVal = listEntry.split("(\\s)*=(\\s)*")[1].trim();
                if (sVal.toLowerCase().equals("on"))
                    retVal = new Boolean(true);
                else
                    retVal = new Boolean(false);
            } catch (Throwable t) {
                logger.log(Level.INFO, " Got exception parsing module argument " + listEntry + ": ", t);
                return null;
            }
        }
        logger.info("Set module parameter " + argName + "to " + retVal);
        return retVal;

    }

    /**
     * Returns the first nLines lines from a buffer.
     */
    public static StringBuilder getFirstLines(BufferedReader buff, int nLines) {
        StringBuilder ret = null;
        try {
            String crtLine = null;
            int lineCnt = 0;
            if (buff != null) {
                ret = new StringBuilder();
                while ((crtLine = buff.readLine()) != null && lineCnt < nLines) {
                    ret.append(crtLine + "\n");
                    lineCnt++;
                }
                if (ret.length() == 0)
                    ret = null;
            }
        } catch (Exception e) {
            return null;
        }
        return ret;
    }

    /**
     * Obtains the positions in which the parameter names are placed in the
     * first line of a command's output.
     * 
     * @param line
     *            The line that we need to process, containing the parameter
     *            names.
     * @return Hashtable having the parameter names as keys and their positions
     *         as values.
     */
    public static Hashtable<String, Integer> getParamIndex(String line) {
        Hashtable<String, Integer> ret = new Hashtable<String, Integer>();

        String[] tokens = line.split("(\\s)+");
        for (int i = 0; i < tokens.length; i++) {
            ret.put(tokens[i], Integer.valueOf(i));
        }

        return ret;
    }

    /**
     * Helper function that searches for a parameter value in a parameter list.
     * 
     * @param paramName
     * @param tokens
     * @param paramIndex
     * @return
     */
    public static String getSParameter(String paramName, String[] tokens, Hashtable<String, Integer> paramIndex) {
        if (paramName == null || tokens == null || paramIndex == null)
            return null;

        Integer iIndex = paramIndex.get(paramName);
        if (iIndex != null)
            return tokens[iIndex.intValue()];
        
        return null;
    }
}
