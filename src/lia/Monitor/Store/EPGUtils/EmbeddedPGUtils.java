/*
 * $Id: EmbeddedPGUtils.java 7541 2014-11-18 15:24:12Z costing $
 * Created on Oct 7, 2010
 */
package lia.Monitor.Store.EPGUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;
import lia.util.os.OSType;
import lia.util.os.OSType.OSArch;
import lia.util.os.OSType.OSName;
import lia.util.process.ExternalProcess.ExecutorFinishStatus;
import lia.util.process.ExternalProcess.ExitStatus;
import lia.util.process.ExternalProcessBuilder;
import netx.jnlp.Version;

/**
 * @author ramiro
 */
public class EmbeddedPGUtils {

    private static final Logger logger = Logger.getLogger(EmbeddedPGUtils.class.getName());

    private static EmbeddedPGUtils runningInstance;

    private boolean started = false;

    private final List<File> tempFiles = new LinkedList<File>();

    private String sPG_PATH;

    private EmbeddedPGUtils() {

    }

    private static synchronized boolean stopAndStartPG(final File ePGDir) throws IOException, InterruptedException {
        stopPG(ePGDir);
        final EPGProcessCMDExecutor executor = new EPGProcessCMDExecutor(ePGDir);
        executor.procBuilder.timeout(2, TimeUnit.MINUTES);
        final ExitStatus eStatus = executor.execWithOutput(Arrays.asList(new String[] { ePGDir.getAbsolutePath()
                + "/start.sh" }));
        return (eStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                && (eStatus.getExtProcExitStatus() == 0);
    }

    private static synchronized boolean stopPG(final File ePGDir) throws IOException, InterruptedException {
        final EPGProcessCMDExecutor executor = new EPGProcessCMDExecutor(ePGDir);
        executor.procBuilder.timeout(2, TimeUnit.MINUTES);
        final ExitStatus eStatus = executor.execWithOutput(Arrays.asList(new String[] { ePGDir.getAbsolutePath()
                + "/stop.sh" }));
        return (eStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                && (eStatus.getExtProcExitStatus() == 0);
    }

    private static final synchronized List<String> unpackPGFromJar(final JarFile jarFile, final String sPG_PATH) {
        final Enumeration<JarEntry> e = jarFile.entries();
        List<String> fileNames = new LinkedList<String>();
        boolean unpackPGFromJar = false;
        while (e.hasMoreElements()) {
            final JarEntry je = e.nextElement();
            final String s = je.toString();
            if (s.endsWith(".tgz") || s.endsWith("tar.gz") || s.endsWith("install.sh")) {
                final byte[] buff = new byte[1024 * 1024];

                BufferedInputStream bis = null;
                FileOutputStream fos = null;
                InputStream is = null;
                BufferedOutputStream bos = null;

                try {
                    is = jarFile.getInputStream(je);
                    bis = new BufferedInputStream(is);
                    fos = new FileOutputStream(sPG_PATH + "/" + s);
                    bos = new BufferedOutputStream(fos);
                    fileNames.add(sPG_PATH + "/" + s);

                    long totalWrite = 0;
                    for (;;) {
                        final int bNO = bis.read(buff);
                        if (bNO == -1) {
                            break;
                        }
                        totalWrite += bNO;
                        bos.write(buff, 0, bNO);
                    }

                    logger.log(Level.INFO, " Written " + totalWrite + " to " + sPG_PATH + "/" + s);
                    bos.flush();
                    bos.close();
                    fos.flush();
                    fos.close();
                    if (s.endsWith("install.sh")) {
                        if (!new File(sPG_PATH + "/" + s).setExecutable(true, false)) {
                            unpackPGFromJar = false;
                            break;
                        }
                    }
                    unpackPGFromJar = true;
                } catch (Throwable ioe) {
                    unpackPGFromJar = false;
                    logger.log(Level.WARNING, "[ unpackPGFromJar ] Exception while unpacking PG from " + jarFile
                            + " to " + sPG_PATH);
                    break;
                } finally {
                    Utils.closeIgnoringException(bis);
                    Utils.closeIgnoringException(fos);
                    Utils.closeIgnoringException(is);
                    Utils.closeIgnoringException(bos);
                }
            }
        }

        if (unpackPGFromJar) {
            return fileNames;
        }

        for (final String fName : fileNames) {
            try {
                final File fToDelete = new File(fName);
                fToDelete.deleteOnExit();
                if (!fToDelete.delete()) {
                    logger.log(Level.WARNING, "[ unpackPGFromJar ] Exception while unpacking PG from " + jarFile
                            + " to " + sPG_PATH);
                }
            } catch (Throwable t1) {
                logger.log(Level.WARNING, "[ unpackPGFromJar ] Exception while unpacking PG from " + jarFile + " to "
                        + sPG_PATH, t1);
            }
        }

        return Collections.emptyList();
    }

    private static final synchronized boolean unpackAndInstallPGFromJar(final JarFile jarFile, final File ePGDir,
            final String sPG_PATH, boolean fullInit) {
        try {
            final List<String> retFiles = unpackPGFromJar(jarFile, sPG_PATH);
            if ((retFiles != null) && (retFiles.size() > 0)) {
                // just run install.sh
                ExternalProcessBuilder untarPB = new ExternalProcessBuilder(sPG_PATH + "/install.sh");
                untarPB.directory(new File(sPG_PATH));
                untarPB.redirectErrorStream(true);
                // smth is completly messed up otherwise
                untarPB.timeout(5, TimeUnit.MINUTES);
                ExitStatus eStatus = untarPB.start().waitFor();
                if ((eStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                        && (eStatus.getExtProcExitStatus() == 0)) {
                    logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] extracting new PG went fine");
                    return true;
                }

                logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] Unable to extract pgsql.tgz. ExitStatus = "
                        + eStatus);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to unpack the  new pgSQL DB. Cause: " + t);
        } finally {
            try {
                if (!new File(sPG_PATH + "/install.sh").delete()) {
                    logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to delete: " + sPG_PATH
                            + "/install.sh");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to delete: " + sPG_PATH
                        + "/install.sh Cause: " + t);
            }
            try {
                if (!new File(sPG_PATH + "/pgsql.tgz").delete()) {
                    logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to delete: " + sPG_PATH
                            + "/pgsql.tgz");
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to delete: " + sPG_PATH
                        + "/pgsql.tgz Cause: " + t);
            }
        }
        return false;
    }

    private void upgradeAndStart() throws IOException, InterruptedException {
        final OSArch jvmOSArch = OSType.getJVMOsArch();
        final OSName jvmOSName = OSType.getJVMOsName();

        if ((jvmOSArch == null) || (jvmOSName == null)) {
            logger.log(Level.WARNING,
                    "Embedded PG was not started because we were unable to determine current JVM OS Name: " + jvmOSName
                            + " or JVM OS Arch: " + jvmOSArch);
            return;
        }

        logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] JVM Details: We're running on OS Name=" + jvmOSName
                + " OS Arch=" + jvmOSArch);

        final String MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME");
        // relative to this path we get the jar file containing the database
        if (MonaLisa_home == null) {
            throw new IllegalArgumentException("Unable to determine MonaLisa_HOME");
        }
        String sPG_PATH = AppConfig.getGlobalEnvProperty("PGSQL_PATH", null);
        final String FARM_home = AppConfig.getProperty("lia.Monitor.Farm.HOME");

        // if this variable is not set fallback to the FARM_home folder, if possible
        if ((sPG_PATH == null) || (sPG_PATH.length() == 0)) {
            if ((FARM_home == null) || (FARM_home.length() == 0)) {
                throw new IllegalArgumentException("Unable to determine lia.Monitor.Farm.HOME");
            }

            sPG_PATH = FARM_home;
        }

        this.sPG_PATH = sPG_PATH.trim();

        logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] Embedded PGdb location will be : " + sPG_PATH);

        final File ePGDir = new File(sPG_PATH + File.separator + "pgsql");

        final boolean pgSQLExists = ePGDir.exists();
        EPGProcessCMDExecutor ePGExecutor = null;
        if (pgSQLExists) {
            try {
                ePGExecutor = EPGProcessCMDExecutor.newInstance(ePGDir);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to determine version and OSArch from current PG instance", t);
                started = stopAndStartPG(ePGDir);
                return;
            }
        }

        final OSArch currentPGArch = ((ePGExecutor == null) || (ePGExecutor.osArch == null)) ? jvmOSArch
                : ePGExecutor.osArch;
        logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] current PG OS arch: " + currentPGArch);
        // Get OS stuff
        String sJarFile = null;

        if ((jvmOSName == OSName.SUNOS) && (currentPGArch == OSArch.sparc32)) {
            sJarFile = "epgsqldb-solaris_sparc32.jar";
        } else if (jvmOSName == OSName.LINUX) {
            if (currentPGArch == OSArch.amd64) {
                sJarFile = "epgsqldb-linux_amd64.jar";
            } else if (currentPGArch == OSArch.i86) {
                sJarFile = "epgsqldb-linux_i386.jar";
            }
        }

        if (sJarFile == null) {
            logger.log(Level.WARNING, "No available versions of embedded PGdb for OS: " + jvmOSName + " arch: "
                    + currentPGArch + ". Will use memory store only");
            started = false;
            return;
        }

        if (jvmOSArch != currentPGArch) {
            logger.log(Level.WARNING, "\n\n[ upgradeAndStartEmbeddedPG ] JVM OS Arch: " + jvmOSArch
                    + " != PG OS Arch: " + currentPGArch + " will use PG OS Arch: " + currentPGArch);
        } else {
            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] JVM OS Arch: " + jvmOSArch + " == PG OS Arch: "
                    + currentPGArch);
        }

        final File fullJarFilePath = new File(MonaLisa_home + "/Service/lib/" + sJarFile);
        JarFile pgJarFile = null;
        if (!fullJarFilePath.exists() || !fullJarFilePath.canRead()) {
            logger.log(Level.WARNING,
                    "[ upgradeAndStartEmbeddedPG ] Unable to stat ePG jar file: " + fullJarFilePath.getAbsolutePath());
            if (pgSQLExists) {
                started = stopAndStartPG(ePGDir);
                return;
            }

            started = false;
            return;
        }

        try {
            pgJarFile = new JarFile(fullJarFilePath);
        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "[ upgradeAndStartEmbeddedPG ] Unable to stat ePG jar file: " + fullJarFilePath.getAbsolutePath()
                            + " Cause: ", t);
            started = stopAndStartPG(ePGDir);
            return;
        }

        //clean install
        if (!pgSQLExists) {
            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] Fresh install for EPG database");
            unpackAndInstallPGFromJar(pgJarFile, ePGDir, sPG_PATH, false);
            try {
                ePGExecutor = EPGProcessCMDExecutor.newInstance(ePGDir);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to determine version and OSArch from current PG instance", t);
                deleteDirectory(ePGDir);
                return;
            }

            ExitStatus eStatus = ePGExecutor.execWithOutput(Arrays.asList(new String[] { ePGDir + "/init_nomig.sh" }));
            if ((eStatus.getExecutorFinishStatus() != ExecutorFinishStatus.NORMAL)
                    && (eStatus.getExtProcExitStatus() != 0)) {
                logger.log(Level.WARNING,
                        "[ upgradeAndStartEmbeddedPG ] [ new install ] unable to init the DB. Cause:\n" + eStatus);
                started = false;
                stopPG(ePGDir);
                deleteDirectory(ePGDir);
                return;
            }

            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] [ new install ] DB inited. stop and start the DB ");
            started = stopAndStartPG(ePGDir);
            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] [ new install ] returning = " + started);
            return;
        }

        String jarEPGVersionMF = null;
        String minUpgradeMF = null;
        String noUpgradeMF = null;

        try {
            final Manifest mf = pgJarFile.getManifest();
            if (mf == null) {
                logger.log(Level.WARNING,
                        "[ upgradeAndStartEmbeddedPG ] Unable to stat the manifest for ePG jar file: "
                                + fullJarFilePath.getAbsolutePath() + " Cause: Null manifest");
                started = stopAndStartPG(ePGDir);
                return;
            }

            Attributes attrs = mf.getMainAttributes();
            jarEPGVersionMF = attrs.getValue("EPGVersion");
            minUpgradeMF = attrs.getValue("EPGMinUpgrade-Version");
            noUpgradeMF = attrs.getValue("EPGNoUpgrade-Version");

        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] Unable to stat the manifest for ePG jar file: "
                    + fullJarFilePath.getAbsolutePath() + " Cause: ", t);
            started = stopAndStartPG(ePGDir);
            return;
        }

        if ((jarEPGVersionMF == null) || (minUpgradeMF == null) || (noUpgradeMF == null)) {
            logger.log(Level.WARNING,
                    "[ upgradeAndStartEmbeddedPG ] Unable to stat attributes from the manifest for ePG jar file: "
                            + fullJarFilePath.getAbsolutePath() + "jarEPGVersion: " + jarEPGVersionMF + "minUpgrade: "
                            + minUpgradeMF + "noUpgrade: " + noUpgradeMF);
            started = stopAndStartPG(ePGDir);
            return;
        }

        jarEPGVersionMF = jarEPGVersionMF.trim();
        minUpgradeMF = minUpgradeMF.trim();
        noUpgradeMF = noUpgradeMF.trim();
        if (jarEPGVersionMF.isEmpty() || minUpgradeMF.isEmpty() || noUpgradeMF.isEmpty()) {
            logger.log(Level.WARNING,
                    "[ upgradeAndStartEmbeddedPG ] Unable to stat attributes from the manifest for ePG jar file: "
                            + fullJarFilePath.getAbsolutePath() + "jarEPGVersion: " + jarEPGVersionMF + "minUpgrade: "
                            + minUpgradeMF + "noUpgrade: " + noUpgradeMF);
            started = stopAndStartPG(ePGDir);
            return;
        }

        logger.log(Level.INFO, " Manifest attributes for: " + fullJarFilePath + "jarEPGVersion: " + jarEPGVersionMF
                + "minUpgrade: " + minUpgradeMF + "noUpgrade: " + noUpgradeMF);

        final Version jarEPGVersion = new Version(jarEPGVersionMF);
        final Version minUpgradeVersion = new Version(minUpgradeMF);
        final Version noUpgradeVersion = new Version(noUpgradeMF);

        final Version currentPGVersion = ePGExecutor.version;

        if (currentPGVersion.matches(jarEPGVersion)) {
            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] no need to upgrade current pgVersion: "
                    + currentPGVersion + " matches the jarEPGVersion version: " + jarEPGVersion);
            started = stopAndStartPG(ePGDir);
            return;
        }

        if (currentPGVersion.matches(noUpgradeVersion)) {
            logger.log(Level.INFO, "[ upgradeAndStartEmbeddedPG ] no need to migrate current pgVersion: "
                    + currentPGVersion + " matches the noUpgrade version: " + noUpgradeMF + " jarEPGVersion: "
                    + jarEPGVersion);
            try {
                unpackAndInstallPGFromJar(pgJarFile, ePGDir, sPG_PATH, true);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] unable to unpack the  new pgSQL DB. Cause: "
                        + t);
            }

            // in both cases start whatever works ....
            started = stopAndStartPG(ePGDir);
            return;
        }

        if (!minUpgradeVersion.matches(currentPGVersion)) {
            logger.log(Level.INFO,
                    "[ upgradeAndStartEmbeddedPG ] Current PG upgrade does not work for old versions of PGSql pgVersion: "
                            + currentPGVersion + ", jarEPGVersion version: " + jarEPGVersion + ", minUpgradeVersion "
                            + minUpgradeVersion + " ... Will use exising DB.");
            started = stopAndStartPG(ePGDir);
            return;
        }

        logger.log(Level.INFO, " Trying to migrate from " + currentPGVersion + " to " + jarEPGVersion);

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        final String timeStamp = sdf.format(new Date());
        final File ePGDirOld = new File(sPG_PATH + File.separator + "pgsql.old." + timeStamp);

        boolean bErr = true;
        if (pgSQLExists) {
            // move current installation to pgsql.old.timestamp
            if (!ePGDir.renameTo(ePGDirOld)) {
                logger.log(Level.WARNING, " Unable to move the old db from: '" + ePGDir + "' to '" + ePGDirOld
                        + "' Will try to use old DB");
                bErr = false;
                started = stopAndStartPG(ePGDir);
                return;
            }
            // TODO change to FINER
            logger.log(Level.INFO, "Old pg instance moved to: '" + ePGDirOld + "' ... cleaning up old instance.");

            try {
                final EPGProcessCMDExecutor oldPGExec = EPGProcessCMDExecutor.newInstance(ePGDirOld);
                if (!stopAndStartPG(ePGDirOld)) {
                    stopPG(ePGDirOld);
                    deleteDirectory(ePGDir);
                    ePGDirOld.renameTo(ePGDir);
                    started = stopAndStartPG(ePGDir);
                    return;
                }

                unpackAndInstallPGFromJar(pgJarFile, ePGDir, sPG_PATH, false);
                final File oldAdminConsole = new File(ePGDirOld + "/admin_console.sh");
                final File newAdminConsole = new File(ePGDir + "/admin_console.sh");

                if (!oldAdminConsole.exists()) {
                    if (newAdminConsole.exists()) {
                        newAdminConsole.renameTo(oldAdminConsole);
                    }
                }
                ExitStatus e = oldPGExec
                        .execWithOutput(Arrays
                                .asList(new String[] { "/bin/echo \"delete from pg_proc where (proname, probin) in (select proname, probin from pg_proc where probin not in (select probin from pg_proc where probin like '\\$libdir%'));\" | "
                                        + ePGDirOld + "/admin_console.sh" }));
                System.out.println(e);
                ;
                oldAdminConsole.renameTo(newAdminConsole);
                // TODO check status
                stopPG(ePGDirOld);
                ePGExecutor.execWithOutput(Arrays.asList(new String[] { ePGDir + "/init.sh" }));
                // ///////////////////
                // here comes the sun
                //
                // pg_upgrade \
                // --link \
                // --old-bindir ${OLD_PGDIR_BIN} --new-bindir ${NEW_PGDIR_BIN} \
                // --old-datadir ${OLD_PGDIR_DATA} --new-datadir ${NEW_PGDIR_DATA}
                // //////////////////////

                final List<String> cmdList = new LinkedList<String>();
                cmdList.add(ePGDir.getAbsolutePath() + "/bin/pg_upgrade");
                if (AppConfig.getb("lia.Monitor.Store.EPGUtils.useLinks", false)) {
                    cmdList.add("--link");
                }
                cmdList.add("--old-bindir");
                cmdList.add(ePGDirOld.getAbsolutePath() + "/bin");
                cmdList.add("--new-bindir");
                cmdList.add(ePGDir.getAbsolutePath() + "/bin");
                cmdList.add("--old-datadir");
                cmdList.add(ePGDirOld.getAbsolutePath() + "/data");
                cmdList.add("--new-datadir");
                cmdList.add(ePGDir.getAbsolutePath() + "/data");
                ePGExecutor.procBuilder.timeout(60, TimeUnit.MINUTES);
                logger.log(Level.INFO, " Start importing from: " + ePGDirOld + " to " + ePGDir
                        + ". Will wait for max 60 minutes");
                ExitStatus importExitStatus = ePGExecutor.execWithOutput(cmdList);
                if ((importExitStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                        && (importExitStatus.getExtProcExitStatus() == 0)) {
                    bErr = false;
                } else {
                    logger.log(Level.WARNING, " Error importing data. Import Exit status: " + importExitStatus);
                    bErr = true;
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Unable to import. Cause", t);
            } finally {
                if (bErr) {
                    logger.log(Level.INFO, " Roll-back to previous PG version...");
                    //delete current PG
                    deleteDirectory(ePGDir);

                    //move old dit to new dir
                    if (!ePGDirOld.renameTo(ePGDir)) {
                        logger.log(Level.WARNING, " Unable to restore old PG dir " + ePGDirOld + " to new dir: "
                                + ePGDir);
                        started = false;
                        return;
                    }
                    logger.log(Level.INFO, "Previous PG dir moved back to old location. Trying to start old PG...");
                }

            }

            stopAndStartPG(ePGDir);
            Thread.sleep(5 * 1000);
        }

        started = true;
    }

    public static synchronized boolean upgradeAndStartEmbeddedPG() {

        try {
            try {
                runningInstance = new EmbeddedPGUtils();
                runningInstance.upgradeAndStart();
                // setting params for epgsqldb
                System.setProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver");
                System.setProperty("lia.Monitor.ServerName", "127.0.0.1");
                System.setProperty("lia.Monitor.DatabaseName", "mon_data");
                System.setProperty("lia.Monitor.UserName", "mon_user");
                System.setProperty("lia.Monitor.Pass", "mon_pass");

                BufferedReader br = null;
                String portS = null;

                try {
                    br = new BufferedReader(new FileReader(runningInstance.sPG_PATH + "/pgsql/pgsql.port"));
                    portS = br.readLine();
                } catch (IOException ioe) {
                    // ignore
                } finally {
                    if (br != null) {
                        try {
                            br.close();
                            br = null;
                        } catch (IOException ioe2) {
                            // ignore
                        }
                    }
                }

                if (portS == null) {
                    return false;
                }

                System.setProperty("lia.Monitor.DatabasePort", portS.trim());

                DB db = new DB();
                
                db.setReadOnly(true);

                if (db.query("SELECT 1015 AS omiecinshpe;", false) && db.moveNext() && (db.geti("omiecinshpe") == 1015)) {
                    logger.log(Level.INFO, " [ TransparentStoreFactory ] Emebdded PG Started successfully ... ");
                    return true;
                }

                logger.log(Level.INFO, " upgradeAndStartEmbeddedPG returning: " + runningInstance.started);
                return runningInstance.started;
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ upgradeAndStartEmbeddedPG ] Exception starting DB. Cause: ", t);
            } finally {
                if ((runningInstance != null) && (runningInstance.tempFiles.size() > 0)) {
                    for (final File f : runningInstance.tempFiles) {
                        deleteDirectory(f);
                    }
                }
                runningInstance = null;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exception running final clean-up for ugpradeAndStartEPG. Cause: ", t);
        }

        logger.log(Level.INFO, " upgradeAndStartEmbeddedPG returning false ");
        return false;
    }

    static public boolean deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return (path.delete());
    }
}
