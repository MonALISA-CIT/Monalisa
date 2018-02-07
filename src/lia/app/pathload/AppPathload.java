/**
 * 
 */
package lia.app.pathload;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.app.AppInt;
import lia.app.AppUtils;
import lia.util.logging.MLLogEvent;

/**
 * Starts and stops the pathload_snd instance of the system. 
 * @author heri
 *
 */
public class AppPathload implements AppInt {

    /**
     * Pathload Module Name used in log entries as well in the modules.properties
     * file from $MONALISA_HOME/Control/etc which ties the module name with the
     * configuration filename together.
     * Example:
     * 		lia.app.abping.pathload=pathload_snd.config
     */
    public final static String MODULE_NAME = "lia.app.abping.pathload";

    /**
     * Get reference to MONALISA_HOME directory. This is used in case other 
     * properties like MONALISA_BIN_DIR are relative paths to the ML home
     * directory. 
     */
    public static String MONALISA_HOME = AppConfig.getProperty("MonaLisa_HOME", "../..");

    /**
     * Name of the update command issued by the GUI. This could change in the future.
     * Other commands would be insert and delete. 
     */
    public final static String UPDATE_COMMAND = "update";

    /**
     * Pathload_snd configuration file. This file resides in $MONALISA_HOME/Control/etc
     * Modification through the update command are made here. Pathload keeps only
     * one line here: parameters=
     */
    public final static String PATHLOAD_CONFIG_FILE = "pathload_snd.config";

    /**
     * Name of the jar file distribuited by the MonAlisa Development team for
     * pathload. This is used to extract the neccesary files for running the 
     * pathload module.
     * 
     * NOTE: Because 
     * 
     * @since 1.5.12
     */
    public static String PATHLOAD_JAR = "pathload";

    /**
     * The place where pathload_snd, pathload_rcv and pgrep should reside.
     * This is $MONALISA_HOME/Control/bin 
     */
    public final static String MONALISA_BIN_DIR;

    /**
     * The place where pathload.jar should reside.
     * This is $MONALISA_HOME/Control/lib
     */
    public final static String MONALISA_LIB_DIR;

    /**
     * If set, then pathload_snd will run with nohup and the output
     * will be logged in MONALISA_SENDER_LOG_FILE 
     */
    public final static String MONALISA_SENDER_LOG_FILE;

    /**
     * If set, pathload_rcv will and log all its output to 
     * MONALISA_RECEIVER_LOG_FILE 
     */
    public final static String MONALISA_RECEIVER_LOG_FILE;

    /**
     * Directory where pathload binaries will be extracted.
     * If not specified, pathload_snd and pathload_rcv will be
     * extracted in $MONALISA_HOME/Control/bin
     */
    public final static String PATHLOAD_BIN_DIR;

    /**
     * Tell the pathload_rcv client to be verbose in ints output.
     * This will append a -v in the client pathload_rcv command.
     * The switch will only work in conjunction with specifing 
     * an output log file.
     */
    public static String MONALISA_RECEIVER_VERBOSE = AppConfig.getProperty("lia.util.Pathload.client.receiverVerbose");

    /**
     * The user can disable the logging of pathload_rcv output by modifing 
     * ml.properties file.
     * This property is read each time pathload_rcv runs.
     * 
     * @since ML v.1.5.14
     */
    public static boolean MONALISA_RECEIVER_DISABLE_LOG = Boolean.valueOf(
            AppConfig.getProperty("lia.util.Pathload.client.disableRecvLog", "false")).booleanValue();

    /**
     * Tell pathload_rcv to enable aditional debugging on the code
     * inserted by the monalisa developers.
     * Useless without MONALISA_RECEIVER_VERBOSE and !MONALISA_RECEIVER_DISABLE_LOG
     * 
     * @since ML v.1.5.16
     */
    public static String MONALISA_RECEIVER_DEBUG = AppConfig.getProperty("lia.util.Pathload.client.receiverDebug");

    /**
     * Tells pathload_rcv to die when the ADR algorithm fails.
     * On some links, this causes high load and failure of the measurement.
     * 
     * @since ML v.1.5.16
     */
    public static String MONALISA_RECEIVER_ADRFAIL = AppConfig
            .getProperty("lia.util.Pathload.client.receiverFailOnADRFail");

    /**
     * Convert relative paths into absolute paths.
     */
    static {
        MONALISA_HOME = cutEndingSlashes(MONALISA_HOME);

        String temp = AppConfig.getProperty("lia.util.Pathload.client.binDir", "Control/bin");
        MONALISA_BIN_DIR = getAbsolutePath(temp);

        temp = AppConfig.getProperty("lia.util.Pathload.client.libDir", "Control/lib");
        MONALISA_LIB_DIR = getAbsolutePath(temp);

        temp = AppConfig.getProperty("lia.util.Pathload.client.senderLogFile");
        MONALISA_SENDER_LOG_FILE = getAbsolutePath(temp);

        temp = AppConfig.getProperty("lia.util.Pathload.client.receiverLogFile");
        MONALISA_RECEIVER_LOG_FILE = getAbsolutePath(temp);

        temp = AppConfig.getProperty("lia.util.Pathload.client.pathoadBinDir", "Control/bin");
        PATHLOAD_BIN_DIR = getAbsolutePath(temp);
    }

    /**
     * Our logging component 
     */
    private static final Logger logger = Logger.getLogger(MODULE_NAME);

    private StringBuilder startCmd = null;
    private StringBuilder stopCmd = null;
    private StringBuilder statusCmd = null;
    private StringBuilder checkCmd = null;
    private StringBuilder pathloadRcvCmd = null;

    private static boolean bFirstRun = true;
    private String sFile = PATHLOAD_CONFIG_FILE;
    private final Properties prop = new Properties();
    public static final String sConfigOptions = "########### Required parameters : ############\n"
            + "#parameters=parameters to be passed to pathload_snd\n"
            + "##############################################\n\n";

    /**
     * Parameters used to run pathload_snd. They are read from AppPathload.PATHLOAD_CONFIG_FILE
     * Access permissions are read/write.
     */
    private String sParameters;

    /** 
     * Starts the pathload_snd instance used for sending packets.
     * There are two ways of running pathload_snd. If
     * lia.util.Pathload.client.logDir is defined in ml.properties, then
     * all output from the server will be redirected there.
     *  
     * @see 	lia.app.AppInt#start()
     * @return 	Returns true if successfull false otherwise.
     */
    @Override
    public boolean start() {
        String vsCommand[];
        String sResult;
        boolean bResult;

        if (startCmd == null) {
            startCmd = new StringBuilder();
            startCmd.append("PATH=" + MONALISA_BIN_DIR + ":" + PATHLOAD_BIN_DIR + ":$PATH ; ");
            startCmd.append("PATH=$PATH:" + MONALISA_HOME + "/bin ; ");
            startCmd.append("RET_VAL='ok' ; PATHLOAD_PID=`pgrep -x pathload_snd` ;  ");
            startCmd.append("if [[ $? -eq 1 ]] ;  then ");
            if (AppPathload.MONALISA_SENDER_LOG_FILE != null) {
                startCmd.append(PATHLOAD_BIN_DIR + "/pathload_snd ");
                startCmd.append((sParameters != null ? sParameters : ""));
                startCmd.append(" >" + MONALISA_SENDER_LOG_FILE + " & 2>&1 ; sleep 5 ; else RET_VAL=0 ; fi ; ");
            } else {
                startCmd.append(PATHLOAD_BIN_DIR + "/pathload_snd ");
                startCmd.append((sParameters != null ? sParameters : ""));
                startCmd.append(" >/dev/null & 2>&1 ; sleep 5 ; else RET_VAL=0 ; fi ; ");
            }
            startCmd.append("PATHLOAD_PID=`pgrep -x pathload_snd` ;if [[ $? -eq 1 ]] ; ");
            startCmd.append("then RET_VAL=0 ; fi ; echo $RET_VAL");
        }

        vsCommand = new String[] { "/bin/bash", "-c", startCmd.toString() };

        sResult = procOutput(vsCommand);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "Executing command: " + startCmd.toString());
            logger.log(Level.FINE, "Starting pathload: " + sResult);
        }

        if ((sResult != null) && (sResult.indexOf("ok") != -1)) {
            bResult = true;
        } else {
            bResult = false;
            logger.log(Level.WARNING, MODULE_NAME + " : pathload_snd failed to start either because it "
                    + "was already or because of an internal error.");
        }

        return bResult;
    }

    /** 
     * Stops any pathload_snd instance in the system.
     * @see 	lia.app.AppInt#stop()
     * @return	Return true if successfull false otherwise
     */
    @Override
    public boolean stop() {
        String vsCommand[];
        String sResult;
        boolean bResult;

        if (stopCmd == null) {
            stopCmd = new StringBuilder();
            stopCmd.append("PATH=" + MONALISA_BIN_DIR + ":" + PATHLOAD_BIN_DIR + ":$PATH ; ");
            stopCmd.append("PATH=$PATH:" + MONALISA_HOME + "/bin ; ");
            stopCmd.append("RET_VAL='ok' ; PATHLOAD_PID=`pgrep -x pathload_snd` ;  ");
            stopCmd.append("if [[ $? -eq 0 ]] ;  then ");
            stopCmd.append("kill $PATHLOAD_PID ; else RET_VAL=0 ; fi ; ");
            stopCmd.append("PATHLOAD_PID=`pgrep -x pathload_snd` ; ");
            stopCmd.append("if [[ $? -eq 0 ]] ;  then ");
            stopCmd.append("killall -9 pathload_snd ; fi ; echo $RET_VAL");
        }

        vsCommand = new String[] { "/bin/bash", "-c", stopCmd.toString() };

        sResult = procOutput(vsCommand);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "Executing command: " + stopCmd.toString());
            logger.log(Level.FINE, "Stopping pathload: " + sResult);
        }

        if ((sResult != null) && (sResult.indexOf("ok") != -1)) {
            bResult = true;
        } else {
            bResult = false;
            logger.log(Level.WARNING, MODULE_NAME + " : pathload_snd failed to stop because it " + "is not running.");
        }

        return bResult;
    }

    /** 
     * Restarts the pathload_snd instance.
     * @see 	lia.app.AppInt#restart()
     * @return	Return true if successfull false otherwise
     */
    @Override
    public boolean restart() {
        boolean bResult;

        bResult = stop();
        if (!bResult) {
            logger.log(Level.WARNING, MODULE_NAME + " : Restarting pathload_snd. Pathload instance " + "not running");
        }
        bResult = start();
        return bResult;
    }

    /** 
     * Checkes the status of the pathload_snd instance.
     * @see 	lia.app.AppInt#status()
     * @return	Returns <b>AppUtil.APP_STATUS_STOPPED</b> if stopped and </br>
     * 			<b>AppUtil.APP_STATUS_RUNNING</b> if running.
     */
    @Override
    public int status() {
        String vsCommand[];
        String sResult;
        int iResult;

        if (statusCmd == null) {
            statusCmd = new StringBuilder();
            statusCmd.append("PATH=" + MONALISA_BIN_DIR + ":" + PATHLOAD_BIN_DIR + ":$PATH ; ");
            statusCmd.append("PATH=$PATH:" + MONALISA_HOME + "/bin ; ");
            statusCmd.append("RET_VAL=1 ; PATHLOAD_PID=`pgrep -x pathload_snd` ;  ");
            statusCmd.append("if [[ $? -eq 1 ]] ;  then ");
            statusCmd.append("RET_VAL=0 ; fi ; echo $RET_VAL");
        }

        vsCommand = new String[] { "/bin/bash", "-c", statusCmd.toString() };

        sResult = AppUtils.getOutput(vsCommand);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "Executing command: " + statusCmd.toString());
            logger.log(Level.FINE, "Checking status of pathload: " + sResult);
        }

        if ((sResult != null) && ((sResult.length() - 2) >= 0) && (sResult.charAt(sResult.length() - 2) == '1')) {
            iResult = AppUtils.APP_STATUS_RUNNING;
        } else {
            iResult = AppUtils.APP_STATUS_STOPPED;
        }

        return iResult;
    }

    /** 
     * Returns an XML with the parameters used to run pathload_snd.
     * There is currently only one key involved in running pathload_snd
     * that is the paramerter string used to run pathload_snd.
     * 
     * 		<pre>
     * 		// xml with the version & stuff
     *		StringBuilder sb = new StringBuilder();
     *		sb.append("<config app=\"Iperf\">\n");
     *		sb.append("<file name=\"Iperf\">\n");
     *
     *		sb.append(	"<key name=\"parameters\" value=\""
     *			+ AppUtils.enc(sParameters)
     *			+ "\" line=\"1\" read=\"true\" write=\"true\"/>");
     *
     *		sb.append("</file>\n");
     *		sb.append("</config>\n");
     *
     *		return sb.toString();
     *		</pre>
     * 
     * @link	http://pccil.cern.ch:8887/monalisa__Documentation__Service_Administration_Guide.html#writing-app-modules
     * @see 	lia.app.AppInt#info()
     * @return	XML formatted String
     */
    @Override
    public String info() {
        StringBuilder sb = new StringBuilder();

        sb.append("<config app = \"Pathload\">\n");
        sb.append("\t<file name = \"" + sFile + "\">\n");
        sb.append("\t\t<key name = \"parameters\" value = \"" + AppUtils.enc(sParameters)
                + "\" line = \"1\" read = \"true\" write = \"true\" />");
        sb.append("\t</file>\n");
        sb.append("</config>\n");

        return sb.toString();
    }

    /** 
     * This command has no meaning for pathload_snd
     * @see lia.app.AppInt#exec(java.lang.String)
     */
    @Override
    public String exec(String arg0) {
        return null;
    }

    /** 
     * Updates the command line parameters used to run pathload_snd.
     * @see 	lia.app.AppInt#update(java.lang.String)
     * @param arg0 Update command issued by the Configuration App.
     * @return 	Returns true if successfull false otherwise.
     */
    @Override
    public boolean update(String arg0) {
        return update(new String[] { arg0 });
    }

    /**
     * Parses update commands issued by the Configuration App.
     * @see lia.app.AppInt#update(java.lang.String[])
     * @param arg0	Array of update commands issued by the Configuration App.
     * @return 	Returns true if successfull false otherwise.
     */
    @Override
    public boolean update(String[] arg0) {
        boolean bErrorFlag = false;

        if (arg0 == null) {
            return false;
        }
        try {
            for (String sUpdateCmd : arg0) {
                StringTokenizer st = new StringTokenizer(sUpdateCmd, " ");
                String sConfigFile = AppUtils.dec(st.nextToken());

                /**
                 * Normally there would be a
                 * int iLine 	 	= Integer.parseInt(st.nextToken());
                 * but it's not used in this config. file. It's allways line 1. 
                 */
                st.nextToken();
                String sPrev = st.nextToken();
                String sCommand = st.nextToken();
                String sParams = null;
                sPrev = AppUtils.dec(sPrev);

                if (!sConfigFile.equals(sFile)) {
                    logger.log(Level.WARNING, MODULE_NAME + " : Update command issued to "
                            + "wrong config file. Used: " + sConfigFile + " Required: " + sFile);
                    bErrorFlag = true;
                }

                if (!sCommand.equals(UPDATE_COMMAND)) {
                    logger.log(Level.WARNING, MODULE_NAME + " : Invalid update command " + sCommand + " Required: "
                            + UPDATE_COMMAND);
                    bErrorFlag = true;
                    break;
                }

                while (st.hasMoreTokens()) {
                    sParams = (sParams == null ? "" : sParams + " ");
                    sParams += AppUtils.dec(st.nextToken());
                }

                if (sParams != null) {
                    updateConfiguration("parameters=" + sParams);
                }
            }
        } catch (NullPointerException e) {
            logger.log(Level.WARNING, MODULE_NAME + " : Invalid update command. Command is null.");
            bErrorFlag = true;
        } catch (NoSuchElementException e) {
            logger.log(Level.WARNING, MODULE_NAME + " : Invalid update command. Not enough parameters.");
            bErrorFlag = true;
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    MODULE_NAME + " : Catch-all Exception triggered. Please revise code.\n" + e.getMessage());
            bErrorFlag = true;
        }

        return (bErrorFlag == false ? true : false);
    }

    /**
     * Returns pathload_snd's configuration file.
     * The only property used for now is parameters
     * @see 	lia.app.AppInt#getConfiguration()
     * @return	The configuration file in Key = Value format
     */
    @Override
    public String getConfiguration() {
        StringBuilder sb = new StringBuilder();

        sb.append(sConfigOptions);
        Enumeration e = prop.propertyNames();
        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();
            sb.append(s + "=" + prop.getProperty(s) + "\n");
        }

        return sb.toString();
    }

    /**
     * This command has no meaning for pathload_snd
     * @see lia.app.AppInt#updateConfiguration(java.lang.String)
     * @return 	Returns true if successfull false otherwise.
     */
    @Override
    public boolean updateConfiguration(String arg0) {
        boolean bErrorFlag = false;

        bErrorFlag = AppUtils.updateConfig(sFile, arg0) && init(sFile);

        if (!bErrorFlag && (status() == AppUtils.APP_STATUS_RUNNING)) {
            restart();
        }

        return (bErrorFlag == false ? true : false);
    }

    /**
     * This is the very first thing AppControl runs when loading modules.
     * It first checks if MONALISA_HOME is defined correctly and then reads
     * the arg0 file, in this case pathloads config. file.
     * The only interesting thing for our module is the parameters key and 
     * its value.
     * On the first run, it extracts and installs pathload_snd from 
     * MonAlisa/Control/lib/pathload.jar. Any files will be overwritten.
     * 
     * @see 	lia.app.AppInt#init(java.lang.String)
     * @param arg0	AppControl reads the file Config/modules.properties,
     * 			which is a KEY = VALUE property file. From here, it gets
     * 			the name of the configuration file of the current module
     * 			(MODULE_NAME)
     * 			This files lives in MonAlisa/Control/etc/{sFile}
     * @return 	Returns true if successfull false otherwise.
     */
    @Override
    public boolean init(String arg0) {
        while (MONALISA_HOME.endsWith("/")) {
            MONALISA_HOME = MONALISA_HOME.substring(0, MONALISA_HOME.length() - 1);
        }

        sFile = arg0;
        AppUtils.getConfig(prop, sFile);

        if ((prop.getProperty("parameters") != null) && (prop.getProperty("parameters").length() > 0)) {
            sParameters = prop.getProperty("parameters");
        } else {
            sParameters = "";
            logger.log(Level.WARNING, MODULE_NAME + " : Warnig. No parameters specified "
                    + "in init. By default pathload_snd will run only once.");
        }

        if (bFirstRun) {
            extractFiles();
            bFirstRun = false;
        }

        return true;
    }

    /**
     * My module name.
     * @see 	lia.app.AppInt#getName()
     * @return	MODULE_NAME
     */
    @Override
    public String getName() {
        return MODULE_NAME;
    }

    /**
     * Get configuration File's name
     * @see 	lia.app.AppInt#getConfigFile()
     * @return 	Configuration Filename
     */
    @Override
    public String getConfigFile() {
        return sFile;
    }

    /**
     * Installs the pathload module and overwites existing files.
     * This is a two stage extraction. First of all the Manifest file
     * is checked for it's version number. If the version String does
     * not contain the arhitecture type, the files will not get extracted. 
     *  
     *  Exceptions like permission denied or file not found will be sent remote.
     *  
     * @return	Returns true if successfull false otherwise.
     */
    private boolean extractFiles() {
        String[] files = { "pathload_snd", "pathload_rcv" };
        byte[] buff = new byte[1024];
        boolean bErrorFlag = false;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;

        try {
            File destdir = new File(PATHLOAD_BIN_DIR);
            if (!destdir.exists()) {
                destdir.mkdirs();
            }

            String jarFilename = MONALISA_LIB_DIR + "/" + PATHLOAD_JAR + getArch() + ".jar";
            JarFile jf = new JarFile(jarFilename);
            Manifest mf = jf.getManifest();
            Attributes attr = mf.getMainAttributes();
            String jarVersion = attr.getValue("Implementation-Version");

            if (jarVersion.indexOf(getArch()) != -1) {
                for (String file : files) {
                    File destfile = new File(PATHLOAD_BIN_DIR + "/" + file);
                    if (destfile.exists()) {
                        destfile.delete();
                    }
                    JarEntry je = new JarEntry(file);
                    bis = new BufferedInputStream(jf.getInputStream(je));
                    fos = new FileOutputStream(PATHLOAD_BIN_DIR + "/" + file);
                    int bNO;
                    while ((bNO = bis.read(buff)) != -1) {
                        fos.write(buff, 0, bNO);
                    }
                    bis.close();
                    fos.close();
                    AppUtils.getOutput(new String[] { "/bin/chmod", "a+x", PATHLOAD_BIN_DIR + "/" + file });
                }
            } else {
                String message = "Pathload version in jar file is: " + jarVersion + " while my requirements are: "
                        + getArch();

                MLLogEvent logEv = new MLLogEvent();
                logEv.put("ModuleName", "AppPathload");
                logEv.put("ErrorName", "PathloadArchMismatch");
                logEv.put("CheckCmd", message);
                logEv.put("CheckResult", "false");

                logger.log(Level.WARNING, message, new Object[] { logEv });
                bErrorFlag = true;
            }
        } catch (SecurityException e) {
            String message = MODULE_NAME + " : SecurityException. I am not permitted " + "to write to "
                    + PATHLOAD_BIN_DIR;

            MLLogEvent logEv = new MLLogEvent();
            logEv.put("ModuleName", "AppPathload");
            logEv.put("ErrorName", "Permission denied");
            logEv.put("CheckCmd", message);
            logEv.put("CheckResult", "false");

            logger.log(Level.WARNING, e.getMessage(), new Object[] { logEv });
            bErrorFlag = true;
        } catch (IOException e) {
            String message = MODULE_NAME + " : IOException, while extracting from " + PATHLOAD_JAR + getArch() + "\n"
                    + e.getMessage();

            MLLogEvent logEv = new MLLogEvent();
            logEv.put("ModuleName", "AppPathload");
            logEv.put("ErrorName", "IO Exception");
            logEv.put("CheckCmd", message);
            logEv.put("CheckResult", "false");

            logger.log(Level.WARNING, e.getMessage(), new Object[] { logEv });
            bErrorFlag = true;
        } catch (IllegalArgumentException e) {

        } catch (NullPointerException e) {
            logger.log(Level.WARNING, "Pathload Jar " + PATHLOAD_JAR + getArch() + " seems to be invalid.");
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, MODULE_NAME + " : IOException, while " + "extracting from " + PATHLOAD_JAR
                        + " Unable to release resources.");
            }
        }

        return (bErrorFlag == false ? true : false);
    }

    /**
     * This command is not part of the AppInt Contract.
     * It is used by the monPathload module to make some 
     * preliminary checks if it's ok to run or not.
     *  
     * @return	True if succeded, false otherwise 
     */
    public boolean check() {
        String vsCommand[];
        String sResult;
        boolean bResult = false;

        if (bFirstRun) {
            extractFiles();
            bFirstRun = false;
        }
        if (checkCmd == null) {
            checkCmd = new StringBuilder();
            checkCmd.append("PATH=$PATH:" + MONALISA_HOME + "/bin ; ");
            checkCmd.append("echo monPathload sanity check ; ");
            checkCmd.append("echo MonAlisa_Home: 	 " + MONALISA_HOME + " ; ");
            checkCmd.append("echo Pathload Bin Dir:  " + MONALISA_BIN_DIR + " ; ");
            if ((MONALISA_SENDER_LOG_FILE != null) && (MONALISA_RECEIVER_LOG_FILE != null)) {
                if (!MONALISA_SENDER_LOG_FILE.equals(MONALISA_RECEIVER_LOG_FILE)) {
                    checkCmd.append("echo Pathload ReceiveLog and SendLog are different: true ; ");
                } else {
                    checkCmd.append("echo Pathload ReceiveLog and SendLog are different: false ; ");
                }
            } else {
                checkCmd.append("echo Pathload ReceiveLog and SendLog are different: skipped ; ");
            }
            if (MONALISA_SENDER_LOG_FILE == null) {
                checkCmd.append("echo Pathload Sender LogFile: not set ; ");
                checkCmd.append("echo Able to write to Pathload Sender Log: skipped ; ");
            } else {
                checkCmd.append("echo Pathload Sender LogFile: " + MONALISA_SENDER_LOG_FILE + " ; ");
                checkCmd.append("OUTPUT=`touch " + MONALISA_SENDER_LOG_FILE + " >/dev/null 2>&1 ; ` ; ");
                checkCmd.append("if [[ $? -eq 0 ]] ;  then ");
                checkCmd.append("echo Able to write to Pathload Sender Log: true ; ");
                checkCmd.append("else echo Able to write to Pathload Sender Log: false ; fi ; ");
            }
            if (MONALISA_RECEIVER_LOG_FILE == null) {
                checkCmd.append("echo Pathload Receiver LogFile: not set ; ");
                checkCmd.append("echo Able to write to Pathload Receiver Log: skipped ; ");
                checkCmd.append("echo Receiver LogFileName is less than 200 chars: skipped ; ");
            } else {
                checkCmd.append("echo Pathload Receiver LogFile: " + MONALISA_RECEIVER_LOG_FILE + " ; ");
                checkCmd.append("OUTPUT=`touch " + MONALISA_RECEIVER_LOG_FILE + " >/dev/null 2>&1 ; ` ; ");
                checkCmd.append("if [[ $? -eq 0 ]] ;  then ");
                checkCmd.append("echo Able to write to Pathload Receiver Log: true ; ");
                checkCmd.append("else echo Able to write to Pathload Receiver Log: false ; fi ; ");
                if (MONALISA_RECEIVER_LOG_FILE.length() < 200) {
                    checkCmd.append("echo Receiver LogFileName is less than 200 chars: true ; ");
                } else {
                    checkCmd.append("echo Receiver LogFileName is less than 200 chars: false ; ");
                }
            }
            checkCmd.append("if [[ `uname` == 'Linux' ]] ; then ");
            checkCmd.append("echo Distribution is Linux: true ; ");
            checkCmd.append("else echo Distribution is Linux: false ; fi ; ");
            checkCmd.append("OUTPUT=`test -x " + PATHLOAD_BIN_DIR + "/" + "pathload_snd` ; ");
            checkCmd.append("if [[ $? -eq 0 ]] ;  then ");
            checkCmd.append("echo pathload_snd exists and is executable: true ; ");
            checkCmd.append("else pathload_snd exists and is executable: false ; fi ; ");
            checkCmd.append("OUTPUT=`test -x " + PATHLOAD_BIN_DIR + "/" + "pathload_rcv` ; ");
            checkCmd.append("if [[ $? -eq 0 ]] ;  then ");
            checkCmd.append("echo pathload_rcv exists and is executable: true ; ");
            checkCmd.append("else echo pathload_rcv exists and is executable: false ; fi ; ");
            checkCmd.append("OUTPUT=`pgrep >/dev/null 2>&1` ; ");
            checkCmd.append("if [[ $? -eq 2 ]] ;  then ");
            checkCmd.append("echo pgrep is usable: true ; ");
            checkCmd.append("else echo pgrep is usable: false ; fi ; ");
            checkCmd.append("OUTPUT=`killall >/dev/null 2>&1` ; ");
            checkCmd.append("if [[ $? -eq 1 ]] ;  then ");
            checkCmd.append("echo killall is usable: true ; ");
            checkCmd.append("else echo killall is usable: false ; fi ; ");
            checkCmd.append("echo System. arch is " + getArch() + " ; ");

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, checkCmd.toString());
            }
        }

        vsCommand = new String[] { "/bin/bash", "-c", checkCmd.toString() };

        sResult = procOutput(vsCommand);
        if (sResult != null) {
            if (sResult.indexOf("false") == -1) {
                bResult = true;
            }

            if (!bResult) {
                try {
                    MLLogEvent logEv = new MLLogEvent();
                    logEv.put("ModuleName", "AppPathload");
                    logEv.put("ErrorName", "Failed pathload check");
                    logEv.put("CheckCmd", checkCmd.toString());
                    logEv.put("CheckResult", sResult.toString());
                    logger.log(Level.SEVERE, "Pathload checks failed.", new Object[] { logEv });
                } catch (Throwable t) {
                    logger.log(Level.FINE, "Error while logging. " + t.getMessage());
                }
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, checkCmd.toString());
                logger.log(Level.FINE, sResult.toString());
            }
        }

        return bResult;
    }

    /**
     * Stop the current client connections (pathload_rcv)
     * 
     * @return	True if successfull, false otherwise
     */
    public boolean stopPathloadClient() {
        String vsCommand[];

        vsCommand = new String[] { "/bin/bash", "-c", "killall -9 pathload_rcv" };
        AppUtils.getOutput(vsCommand);

        logger.log(Level.FINEST, "Pathload_rcv client has been killed.");

        return true;
    }

    /**
     * Get Execution Command of the pathload Client;
     * The disableRecvLog and Verbose logging property
     * are read each time pathload_snd runs.
     * 
     * @param myIpAddress MyIp if i realy want to connect to
     * 			this interface.
     * @return	ExecCommand for pathload
     */
    public String getPathloadClientExecCmd(String myIpAddress) {
        if (pathloadRcvCmd == null) {
            pathloadRcvCmd = new StringBuilder();
            pathloadRcvCmd.append(PATHLOAD_BIN_DIR + "/pathload_rcv -q -N stdout ");

            MONALISA_RECEIVER_VERBOSE = AppConfig.getProperty("lia.util.Pathload.client.receiverVerbose");
            MONALISA_RECEIVER_DISABLE_LOG = Boolean.valueOf(
                    AppConfig.getProperty("lia.util.Pathload.client.disableRecvLog", "false")).booleanValue();

            if (!MONALISA_RECEIVER_DISABLE_LOG) {
                if (MONALISA_RECEIVER_LOG_FILE != null) {
                    pathloadRcvCmd.append("-o " + MONALISA_RECEIVER_LOG_FILE + " ");
                    if (MONALISA_RECEIVER_VERBOSE != null) {
                        pathloadRcvCmd.append("-v ");
                        if (MONALISA_RECEIVER_DEBUG != null) {
                            pathloadRcvCmd.append("-d ");
                        }
                    }
                }
            }
            if (MONALISA_RECEIVER_ADRFAIL != null) {
                pathloadRcvCmd.append("-x ");
            }
            if (myIpAddress != null) {
                pathloadRcvCmd.append("-i " + myIpAddress + " ");
            }
            pathloadRcvCmd.append("-s");
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, pathloadRcvCmd.toString());
            }
        }
        return pathloadRcvCmd.toString();
    }

    /**
     * Filter out trailing slashes, convert relative paths
     * to absolute paths.
     * 
     * @param path	Absolute or relative pathname
     * @return		Absolute path or null in case of error.
     */
    private static String getAbsolutePath(String path) {
        String sResult = null;
        if (path == null) {
            return null;
        }

        path = cutEndingSlashes(path);
        sResult = path;

        if ((path.length() > 1) && (path.charAt(0) != '/')) {
            sResult = MONALISA_HOME + "/" + path;
        }

        return sResult;
    }

    /**
     * In case the given string has trailing slashes, 
     * cut the ending.
     * 
     * @param path	Path ending or not with a / 
     * @return		Path without the ending /
     */
    private static String cutEndingSlashes(String path) {
        if (path == null) {
            return null;
        }

        while ((path.endsWith("/")) && (path.length() > 1)) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    /**
     * Return architecture type. This will be appended to the jar 
     * filename.
     * 
     * @return The architecture type
     */
    private String getArch() {
        return "-" + System.getProperty("os.arch");
    }

    /**
     * Create a MLProcess and run my command. MLProcess uses a watchdog to control
     * my app.
     * 
     * @param command	Command to execute
     * @return			Command output
     */
    private String procOutput(String[] command) {
        String sResult = null;

        if (command == null) {
            return null;
        }

        try {
            // Process p = MLProcess.exec(command);
            Process p = Runtime.getRuntime().exec(command);
            InputStream is = p.getInputStream();
            OutputStream os = p.getOutputStream();
            os.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int count = 0;

            while ((count = is.read(buff)) > 0) {
                baos.write(buff, 0, count);
            }
            p.waitFor();
            baos.close();
            sResult = baos.toString();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "[appPathload] RunClient IOException while running " + command);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "[appPathload] RunClient caught interruptedException while waiting for " + command
                    + "to finish.");
        }

        return sResult;
    }
}
