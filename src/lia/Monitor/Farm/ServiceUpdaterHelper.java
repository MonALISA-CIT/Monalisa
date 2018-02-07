/*
 * $Id: ServiceUpdaterHelper.java 7171 2011-05-11 06:04:02Z ramiro $
 * Created on Oct 12, 2010
 */
package lia.Monitor.Farm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;
import lia.util.process.ExternalProcess.ExecutorFinishStatus;
import lia.util.process.ExternalProcess.ExitStatus;
import lia.util.process.ExternalProcessBuilder;

/**
 * @author ramiro
 */
class ServiceUpdaterHelper {

    private static final Logger logger = Logger.getLogger(ServiceUpdaterHelper.class.getName());

    private static final String CMD_WRAPPER_SCRIPT_NAME = "cmd_run.sh";

    private static final String ML_ENV_EVO_SCRIPT = "MLEVO_ENV";

    static String getVersionFromBuffer(BufferedReader buff) {
        try {
            for (;;) {
                String lin = buff.readLine();
                if (lin == null) {
                    break;
                }
                if (lin.indexOf("ML Script Version:") != -1) {
                    return lin.substring(lin.indexOf("ML Script Version:")).trim();
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static boolean updateScripts() {
        // String ML_SER_SCRIPT_NAME = isVRVSFarm ? "ML_SER.VRVS" : "ML_SER";
        // String CHECK_UPDATE_SCRIPT_NAME = isVRVSFarm ? "CHECK_UPDATE.VRVS" : "CHECK_UPDATE";
        // String COMMON_SH_SCRIPT_NAME = isVRVSFarm ? "common.sh.VRVS" : "common.sh";

        String ML_SER_SCRIPT_NAME = "ML_SER";
        String CHECK_UPDATE_SCRIPT_NAME = "CHECK_UPDATE";
        String COMMON_SH_SCRIPT_NAME = "common.sh";
        final boolean isVRVSFarm = FarmMonitor.isVRVSFarm;
        final String MonaLisa_home = FarmMonitor.MonaLisa_home;

        if (isVRVSFarm) {
            if (!compareScriptVersion(ML_ENV_EVO_SCRIPT)) {
                logger.log(Level.INFO, "[ FarmMonitor ] Updating script: " + ML_ENV_EVO_SCRIPT);

                if (!updateScript(ML_ENV_EVO_SCRIPT)) {
                    logger.log(Level.WARNING, "\n\n [ FarmMonitor ] The script: " + ML_ENV_EVO_SCRIPT + " was not updated!! \n\n");
                } else {
                    boolean canDelete = false;
                    try {
                        File mlEnvFile = new File(MonaLisa_home + File.separator + "Service" + File.separator + "CMD" + File.separator + "ml_env.VRVS");
                        if (mlEnvFile.exists()) {
                            if (mlEnvFile.canRead()) {
                                if (mlEnvFile.renameTo(new File(MonaLisa_home + File.separator + "Service" + File.separator + "CMD" + File.separator + "ml_env"))) {
                                    canDelete = true;
                                    logger.log(Level.INFO, " [ FarmMonitor ] Succesfully moved ml_env.VRVS to ml_env file");
                                }
                            }
                        } else {
                            canDelete = true;
                        }
                    } catch (Throwable t) {
                        logger.log(Level.INFO, " [ FarmMonitor ] Got exception moving ml_env.VRVS to ml_env file", t);
                    }

                    if (canDelete) {
                        if (!deleteMLScript("ML_SER.VRVS")) {
                            logger.log(Level.WARNING, " [ FarmMonitor ] Unable to delete ML_SER.VRVS");
                        }

                        if (!deleteMLScript("CHECK_UPDATE.VRVS")) {
                            logger.log(Level.WARNING, " [ FarmMonitor ] Unable to delete ML_SER.VRVS");
                        }

                        if (!deleteMLScript("common.sh.VRVS")) {
                            logger.log(Level.WARNING, " [ FarmMonitor ] Unable to delete ML_SER.VRVS");
                        }
                    }
                }
            }
        }

        String[] scripts = new String[] {
                ML_SER_SCRIPT_NAME, CHECK_UPDATE_SCRIPT_NAME, COMMON_SH_SCRIPT_NAME, CMD_WRAPPER_SCRIPT_NAME
        };

        boolean bUpdate = false;
        for (int i = 0; i < scripts.length; i++) {
            try {
                if (!compareScriptVersion(scripts[i])) {
                    logger.log(Level.INFO, "Updating Script: " + scripts[i]);
                    final boolean success = updateScript(scripts[i]);
                    bUpdate = (success || bUpdate);// it's
                    if (success) {
                        logger.log(Level.INFO, "[ FarmMonitor ] the script " + scripts[i] + " updated successfully");
                    } else {
                        logger.log(Level.WARNING, "[ FarmMonitor ] Unable to update the script: '" + scripts[i] + "' error should have been provided.");
                    }
                } else {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Not Updating " + scripts[i]);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ HANDLED ] Got exception updating script " + scripts[i], t);
            }
        }// for

        return bUpdate;
    }

    static boolean compareScriptVersion(String scriptName) {

        final String MonaLisa_home = FarmMonitor.MonaLisa_home;

        FileReader fr = null;
        BufferedReader localbr = null;
        BufferedReader jarbr = null;
        InputStreamReader isr = null;
        InputStream is = null;

        try {
            ClassLoader classLoader = ServiceUpdaterHelper.class.getClassLoader();
            URL jarURL = null;

            jarURL = classLoader.getResource("lia/Monitor/Farm/" + scriptName);

            fr = new FileReader(MonaLisa_home + "/Service/CMD/" + scriptName);
            localbr = new BufferedReader(fr);

            is = jarURL.openStream();
            isr = new InputStreamReader(is);
            jarbr = new BufferedReader(isr);

            final String localVersion = getVersionFromBuffer(localbr);
            final String jarVersion = getVersionFromBuffer(jarbr);

            if (jarVersion == null) {
                return true;
            }
            if (localVersion == null) {
                return false;
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, scriptName + ": localVersion: " + localVersion + " && jarVersion: " + jarVersion + " [ " + jarVersion.compareToIgnoreCase(localVersion) + " ]");
            }

            return (jarVersion.compareToIgnoreCase(localVersion) == 0);

        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ] checkScriptVersion " + scriptName + " got exception. Cause ", t);
        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(localbr);
            Utils.closeIgnoringException(jarbr);
            Utils.closeIgnoringException(isr);
            Utils.closeIgnoringException(is);
        }

        return true;
    }

    static final boolean deleteMLScript(String scriptName) {
        try {
            final String MonaLisa_home = FarmMonitor.MonaLisa_home;

            final File scriptFile = new File(MonaLisa_home + File.separator + "Service" + File.separator + "CMD" + File.separator + scriptName);
            if (scriptFile.exists()) {
                return scriptFile.delete();
            }
            return true;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ FarmMonitor ] Unable to delete script: " + scriptName, t);
        }

        return false;
    }

    static boolean updateScript(String scriptName) {

        final String logPrefix = "[ updateScript ] '" + scriptName + "' ";
        final String MonaLisa_home = FarmMonitor.MonaLisa_home;

        final boolean finest = logger.isLoggable(Level.FINEST);
        final boolean finer = finest || logger.isLoggable(Level.FINER);
        final boolean fine = finer || logger.isLoggable(Level.FINER);

        final String scriptPath = MonaLisa_home + File.separator + "Service" + File.separator + "CMD" + File.separator + scriptName;

        final File scriptFile = new File(scriptPath);
        if (!chmod(scriptPath, "755")) {
            return false;
        }

        BufferedInputStream src = null;
        FileOutputStream dst = null;

        File dstTmp = null;
        File dstBkp = null;

        boolean bError = true;
        try {

            ClassLoader classLoader = ServiceUpdaterHelper.class.getClassLoader();
            URL jarURL = null;

            byte[] buff = new byte[4096];

            jarURL = classLoader.getResource("lia/Monitor/Farm/" + scriptName);

            dstTmp = File.createTempFile(scriptFile.getName(), ".UPDATE", scriptFile.getParentFile());
            dstTmp.deleteOnExit();
            if (finest) {
                logger.log(Level.FINEST, logPrefix + "Temporary dstTmp file: " + dstTmp + " created");
            }

            dstBkp = File.createTempFile(scriptFile.getName(), ".BKP", scriptFile.getParentFile());
            if (finest) {
                logger.log(Level.FINEST, logPrefix + "Temporary dstBkp file: " + dstTmp + " created");
            }

            if (scriptFile.renameTo(dstBkp)) {
                if (finer) {
                    logger.log(Level.FINEST, logPrefix + "The script " + scriptFile + " moved to temp dstBkp file: " + dstTmp);
                }
            } else {
                logger.log(Level.WARNING, logPrefix + " Unable to make a bakup for " + scriptFile + " to temp file: " + dstBkp + " update will not continue");
                dstBkp.delete();
                dstBkp = null;
                return false;
            }

            dst = new FileOutputStream(dstTmp, false);
            src = new BufferedInputStream(jarURL.openStream());

            for (;;) {
                int bNO = src.read(buff);
                if (bNO == -1) {
                    break;
                }
                dst.write(buff, 0, bNO);
            }

            dst.flush();
            dst.close();

            if (!dstTmp.renameTo(scriptFile)) {
                logger.log(Level.WARNING, logPrefix + "Unable to move the tmp script: " + dstTmp + " to final destination: " + scriptFile);
                return false;
            }

            // every thing went fine
            if (dstTmp != null) {
                if (dstTmp.delete()) {
                    if (finest) {
                        logger.log(Level.FINEST, " dstTmp file " + dstTmp + " deleted");
                    }
                } else {
                    logger.log(Level.WARNING, " Unable to delete dstTmp " + dstTmp);
                }
            }
            if (dstBkp != null) {
                if (dstBkp.delete()) {
                    if (finest) {
                        logger.log(Level.FINEST, " dstBkp file " + dstBkp + " deleted");
                    }
                } else {
                    logger.log(Level.WARNING, " Unable to delete dstBkp " + dstBkp);
                }
            }

            if (fine) {
                logger.log(Level.FINE, " Everything hopefully went fine ... will chmod 755 for the new script");
            }
            bError = false;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "\n\n[ FarmMonitor ] Got Exception updating " + scriptPath, t);
            return false;
        } finally {
            Utils.closeIgnoringException(src);
            Utils.closeIgnoringException(dst);
            if (bError) {
                if (dstBkp != null) {
                    if (!dstBkp.renameTo(scriptFile)) {
                        logger.log(Level.SEVERE, "\n\n" + logPrefix + "Please check your FS. Impossible to move bkp script " + dstBkp + " to real script " + scriptFile + "\n\n");
                    } else {
                        dstBkp.delete();
                    }
                }
                dstTmp.delete();
            }
        }

        final boolean finalChmod = chmod(scriptPath, "755") && !bError;

        return finalChmod;
    }

    private static final boolean chmod(final String path, final String mode) {
        final File scriptFile = new File(path);
        if (scriptFile.exists()) {
            try {
                ExternalProcessBuilder epb = new ExternalProcessBuilder("/bin/chmod", mode, path);
                epb.timeout(20, TimeUnit.SECONDS);
                epb.redirectErrorStream(true);
                epb.returnOutputOnExit(true);
                ExitStatus exitStatus = epb.start().waitFor();
                if (exitStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL && exitStatus.getExtProcExitStatus() == 0) {
                    // we went well
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Change mode for " + path + " to 755 ... Success");
                    }
                } else {
                    logger.log(Level.WARNING, " Unable to change mode for " + path + " to 755. ExitStatus: " + exitStatus);
                    return false;
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING, " Unable to change mode for " + path + " to 755. Cause: ", e);
                return false;
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "The script " + path + " does not exist");
            }
        }

        return true;
    }

}
