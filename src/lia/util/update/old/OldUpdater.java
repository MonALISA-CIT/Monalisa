/*
 * Created on May 7, 2003
 */
package lia.util.update.old;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import netx.jnlp.JARDesc;
import netx.jnlp.JNLPFile;
import netx.jnlp.ResourcesDesc;
import netx.jnlp.cache.ResourceTracker;
import netx.jnlp.event.DownloadEvent;
import netx.jnlp.event.DownloadListener;
import netx.jnlp.runtime.JNLPRuntime;
import netx.jnlp.services.ServiceUtil;

public class OldUpdater implements DownloadListener {

    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger("lia.util.update.Updater");

    /** loads the resources */
    private static ResourceTracker tracker = new ResourceTracker(false);

    // prefetch
    private static String cacheDIR;

    private static String destDIR;

    private static String[] URLs;

    private static JNLPFile jnlpf;

    private static String versionNumber = null;

    private static Timer statusTimer = new Timer();

    private String[] tmpJarsNames = null;

    private static StringBuffer statusBuffer = new StringBuffer();

    // Email Stuff

    public final static String MonaLisa_version = "@version@";

    private static final boolean DEBUG_UPDATER;

    private static final String getStackTrace(Throwable t) {
        if (t == null) {
            return "Null Stacktrace??";
        }

        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static {
        boolean debugUpdater = false;

        try {
            debugUpdater = UpdaterConfig.getb("lia.util.update.Updater.DEBUG_UPDATER", false);
        } catch (Throwable t) {
            debugUpdater = false;
        }

        DEBUG_UPDATER = debugUpdater;
    }

    OldUpdater(String[] args) throws Exception {
        cacheDIR = getOption(args, "-cachedir");
        destDIR = getOption(args, "-destdir");
        statusBuffer.append("\n").append(new Date()).append(" Updater [ Constructor ] DIRS: \ncacheDir = ")
                .append(cacheDIR).append(" \ndestDir: ").append(destDIR).append("\n");
        init();
    }

    private static final boolean deleteFolder(File delFolder) throws IOException {

        if ((delFolder == null) || !delFolder.exists() || !delFolder.isDirectory()) {
            throw new IOException("Specified path is not a valid folder.");
        }
        boolean delOk = true;
        java.io.File[] files = delFolder.listFiles();

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                if (!deleteFolder(files[i])) {
                    delOk = false;
                }
            }
            if (!files[i].delete()) {
                delOk = false;
            }
        }

        if (!delFolder.delete()) {
            delOk = false;
        }

        return delOk;
    }

    private void init() throws Exception {
        statusBuffer.append("\n").append(new Date()).append(" Updater : Entering init");
        statusBuffer.append("\n").append(new Date()).append(" Updater : Checking for previous errors ");
        File updateStatus = new File(cacheDIR + File.separator + "InvalidateCache");
        if (!updateStatus.exists()) {
            statusBuffer.append("\n").append(new Date())
                    .append(" Updater : No errors in previous updates ... Cache ok! ");
        } else {
            statusBuffer.append("\n").append(new Date())
                    .append(" Updater : Errors in previous updates ... Trying to invalidate the cache! ");
            boolean deleteOk = true;
            try {
                deleteOk = deleteFolder(new File(cacheDIR));
            } catch (Throwable t) {
                statusBuffer.append("\n").append(new Date())
                        .append(" Updater : Errors while trying to delete cache folder [ ").append(cacheDIR)
                        .append(" ] ! \n");
                statusBuffer.append(getStackTrace(t));
                deleteOk = false;
            }
            statusBuffer.append("\n").append(new Date()).append(" Updater : Delete Cache Folder [ ").append(cacheDIR)
                    .append(" ] ... ").append(deleteOk);
        }

        try {
            if (!JNLPRuntime.isInitialized()) {
                JNLPRuntime.setBaseDir(checkForDir(cacheDIR));
                JNLPRuntime.setDebug(DEBUG_UPDATER);
                JNLPRuntime.setSecurityEnabled(false);
                JNLPRuntime.setHeadless(true);
                JNLPRuntime.initialize();
            } else {
                statusBuffer.append("\n").append(new Date())
                        .append(" Updater [ init ] JNLPRuntime.isInitialized() == ")
                        .append(JNLPRuntime.isInitialized());
            }
        } catch (Throwable t) {
            statusBuffer.append("\n").append(new Date()).append(" Updater [ init ] GOT EXCEPTION : ")
                    .append(getStackTrace(t));
            logger.log(Level.WARNING, " [ JNLPRuntime - initialize ] Got Exception ", t);
            throw new Exception(t);
        }

    }

    /**
     * @param args
     * @param option
     * @return An option if found in args...
     */
    private static String getOption(String[] args, String option) {

        for (int i = 0; i < args.length; i++) {
            if (option.equals(args[i]) && (args.length > (i + 1))) {
                return args[i + 1];
            }
        }

        return null;
    }

    /**
     * @param dir
     *            -
     *            local dir in FileSystem
     * @return The directory where to store temporary jar(s)
     */
    private static File checkForDir(String dir) throws Exception {
        return checkForDir(dir, true);
    }

    /**
     * @param dir
     *            -
     *            local dir in FileSystem
     * @param create
     *            -
     *            if true create the dir if not exists
     * @return The directory where to store temporary jar(s)
     */
    private static File checkForDir(String dir, boolean create) throws Exception {

        File fdir = new File(dir);
        if (!fdir.exists()) {
            if (create) {
                if (!fdir.mkdirs()) {
                    throw new Exception(" Cannot create one or more directories from the specified path:  " + dir);
                }
                logger.log(Level.INFO, " Dir " + dir + " was successfully created");
            } else {
                throw new Exception(" The directory " + dir + " does not exists");
            }
        }

        if (!fdir.isDirectory() || !fdir.canWrite()) {
            throw new Exception(" The specified path " + dir
                    + " is not a directory or there are no write permissions in it!");
        }

        return fdir;
    }

    private static JNLPFile getJNLPFile(String location) throws Exception {

        URL url = null;
        if (new File(location).exists()) {
            url = new File(location).toURI().toURL();
        } else {
            url = new URL(ServiceUtil.getBasicService().getCodeBase(), location);
        }

        InputStream is = null;
        JNLPFile file = null;
        try {
            URLConnection connection = url.openConnection();
            try {
                connection.setConnectTimeout(20 * 1000);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ Updater ] unable to set connectTimeout()", t);
                }
            }
            connection.setDefaultUseCaches(false);
            connection.setUseCaches(false);
            connection.connect();
            is = connection.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            file = new JNLPFile(bis, false);
        } finally {
            try {
                is.close();
            } catch (Throwable ignore) {
            }
        }

        return file;
    }

    private void waitForJars() throws Exception {

        ResourcesDesc rd = jnlpf.getResources();

        JARDesc[] jars = rd.getJARs();
        URL urls[] = new URL[jars.length];

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < jars.length; i++) {

            sb.append("[ " + i + " ] " + jars[i].getLocation() + "\n");
            tracker.addDownloadListener(this);
            tracker.addResource(jars[i].getLocation(), jars[i].getVersion(), JNLPRuntime.getDefaultUpdatePolicy());
            urls[i] = jars[i].getLocation();
        }

        logger.log(Level.CONFIG, sb.toString());
        tracker.waitForResources(urls, 0);
    }

    private void copyFile2File(File s, File d, File dfin) throws Exception {
        boolean sameSize = (dfin.exists() && (s.length() == dfin.length()) && (s.lastModified() == dfin.lastModified()));
        if (!sameSize) {
            statusBuffer.append("\n\n").append(new Date()).append(" START From: ").append(s.getPath()).append(" [ ")
                    .append(s.length()).append(" ]\n To: ").append(d.getPath()).append(" [ ").append(d.length())
                    .append(" ]");
        }
        // Create channel on the source
        FileChannel srcChannel = new FileInputStream(s).getChannel();

        // Create channel on the destination
        FileChannel dstChannel = new FileOutputStream(d).getChannel();

        // Copy file contents from source to destination
        long tr = dstChannel.transferFrom(srcChannel, 0, srcChannel.size());

        long ss = srcChannel.size();
        long ds = dstChannel.size();

        if ((ss != ds) || (ss != tr)) {
            statusBuffer.append("\n\nError copying from ").append(s.getPath()).append(" [ ").append(ss)
                    .append(" ] + to ").append(d.getPath()).append(" [ ").append(ds)
                    .append(" ] Total Bytes Transfered [ ").append(tr).append(" ]");
            throw new Exception("Cannot copy SourceFileSize [ " + ss + " ] DestinationFileSize [ " + ds
                    + " ] Transferred [ " + tr + " ] ");
        }

        // Close the channels
        srcChannel.close();
        dstChannel.close();
        // set the update time
        d.setLastModified(s.lastModified());
        try {
            setRWOwnerOnly(d, statusBuffer);
        } catch (Throwable ignoreInCaseOfJava5) {
            statusBuffer.append("\n\n [ Caught ignored ... ] Cannot set RW only for :").append(d)
                    .append(" only for owner. Cause: ").append(ignoreInCaseOfJava5.getMessage());
        }
        if (!sameSize) {
            statusBuffer.append("\n").append(new Date()).append(" FINISHED\nFrom: ").append(s.getPath()).append(" [ ")
                    .append(ss).append(" ]\nTo: ").append(d.getPath()).append(" [ ").append(ds)
                    .append(" ]\n Transf [ ").append(tr).append(" ]\n");
        }
    }

    private boolean moveLocal() throws Exception {
        boolean retV = true;
        if (tmpJarsNames != null) {
            for (int i = 0; i < tmpJarsNames.length; i++) {
                String dstJarName = tmpJarsNames[i].substring(0, tmpJarsNames[i].length() - 4);// remove
                // .TMP
                File f = new File(tmpJarsNames[i]);
                File df = new File(dstJarName);
                long srcLastModifiedTime = f.lastModified();
                boolean sameSize = (df.exists() && (f.length() == df.length()) && (srcLastModifiedTime == df
                        .lastModified()));

                logger.log(Level.FINEST, " Renaming " + f + " to " + df);
                if (!sameSize) {
                    retV = false;
                    statusBuffer.append("\n").append(new Date()).append(" MOVE ").append(f.getPath()).append(" to ")
                            .append(df.getPath());
                }

                if (!f.renameTo(df)) {
                    statusBuffer.append("\n\n").append(new Date()).append(" Cannot move ").append(f.getPath())
                            .append(" to ").append(df.getPath());
                    logger.log(Level.SEVERE, " Cannot rename " + f + " to " + df);
                } else {
                    if (!sameSize) {
                        statusBuffer.append("\n").append(new Date()).append(" END MOVE ").append(f.getPath())
                                .append(" to ").append(df.getPath());
                    }
                }
                // set the update time
                df.setLastModified(srcLastModifiedTime);
                // df.setReadOnly();
                try {
                    setRWOwnerOnly(df, statusBuffer);
                } catch (Throwable ignoreInCaseOfJava5) {
                    statusBuffer.append("\n\n [ Caught ignored ... ] Cannot set RW only for :").append(df)
                            .append(" only for owner. Cause: ").append(ignoreInCaseOfJava5.getMessage());
                }
            }
        }
        return retV;
    }

    private static final void setRWOwnerOnly(final File f, final StringBuffer sb) {
        try {
            // reset first
            f.setReadable(false, false);
            f.setWritable(false, false);

            // set the "right" permissions
            final boolean setRb = f.setReadable(true, false);
            final boolean setWb = f.setWritable(true, true);
            statusBuffer.append("\n Setting Read-Only for: ").append(f).append("; setReadOwnerStatus:").append(setRb)
                    .append("setWriteOwnerStatus=").append(setWb).append("\n");
        } catch (Throwable ignoreInCaseOfJava5) {
            statusBuffer.append("\n\n Cannot set RW only for :").append(f).append(" only for owner. Cause: ")
                    .append(ignoreInCaseOfJava5.getMessage());
        }
    }

    private String[] copyLocal(String[] ignorePaths) throws Exception {
        Vector retV = new Vector();

        ResourcesDesc rd = jnlpf.getResources();

        JARDesc[] jars = rd.getJARs();
        tmpJarsNames = null;
        Vector updatedJars = new Vector();
        boolean allOK = true;
        for (int i = 0; i < jars.length; i++) {
            try {
                File s = tracker.getCacheFile(jars[i].getLocation());
                File dfin = new File(checkForDir(destDIR).getAbsolutePath()
                        + "/"
                        + jars[i]
                                .getLocation()
                                .toString()
                                .substring(jnlpf.getCodeBase().toString().length(),
                                        jars[i].getLocation().toString().length()));
                if ((!dfin.exists()) || (s.length() != dfin.length()) || (s.lastModified() != dfin.lastModified())) {
                    // if(dfin.getPath().indexOf("update.jar") > -1){
                    // continue; // for test purposes - skip updating of update.jar
                    // }
                    statusBuffer.append("\n\t").append("UPDATE AVAILABLE: ").append(dfin.getPath());
                    String updatedJar = dfin.getPath() + ".TMP";
                    updatedJars.add(updatedJar);
                    File d = new File(updatedJar);
                    File fdir = new File(d.getParent());

                    if (!fdir.exists() && !fdir.mkdirs()) {
                        throw new Exception(OldUpdater.class + " Cannot create dirs to file: " + d);
                    }
                    d.createNewFile();
                    copyFile2File(s, d, dfin);
                }
                // just set the permissions
                try {
                    setRWOwnerOnly(dfin, statusBuffer);
                } catch (Throwable ignoreInCaseOfJava5) {
                    statusBuffer.append("\n\n [ Caught ignored ... ] Cannot set RW only for :").append(dfin)
                            .append(" only for owner. Cause: ").append(ignoreInCaseOfJava5.getMessage());
                }

                try {
                    setRWOwnerOnly(s, statusBuffer);
                } catch (Throwable ignoreInCaseOfJava5) {
                    statusBuffer.append("\n\n [ Caught ignored ... ] Cannot set RW only for :").append(s)
                            .append(" only for owner. Cause: ").append(ignoreInCaseOfJava5.getMessage());
                }
            } catch (Throwable t) {
                String fURL = jars[i].getLocation().getFile();
                if ((fURL == null) || (fURL.length() == 0)) {
                    throw new Exception(t);
                }
                if ((ignorePaths == null) || (ignorePaths.length == 0)) {
                    throw new Exception(t);
                }
                allOK = false;
                boolean canIgnore = false;
                for (int iIgnore = 0; iIgnore < ignorePaths.length; iIgnore++) {
                    if ((ignorePaths[iIgnore] != null) && (ignorePaths[iIgnore].length() > 0)) {
                        if (fURL.indexOf(ignorePaths[iIgnore]) != -1) {
                            canIgnore = true;
                            break;
                        }
                    }
                }// for

                if (!canIgnore) {
                    throw new Exception("Got exception at [ " + fURL + " ] Cause: ", t);
                }
                retV.add(fURL);
            }// end catch
        }// end for
        tmpJarsNames = (String[]) updatedJars.toArray(new String[updatedJars.size()]);
        if (!allOK) {
            return (String[]) retV.toArray(new String[retV.size()]);
        }
        return null;
    }

    private static String[] getURLs(String URLList) {
        String[] ret = null;
        if ((URLList == null) || (URLList.length() == 0)) {
            return null;
        }

        StringTokenizer st = new StringTokenizer(URLList, ",");
        logger.log(Level.CONFIG, "URLList " + URLList + " url no : " + st.countTokens());
        if ((st == null) || (st.countTokens() == 0)) {
            return null;
        }
        ret = new String[st.countTokens()];
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i <= st.countTokens(); i++) {
            if (versionNumber != null) {
                ret[i] = st.nextToken() + "_" + versionNumber + "/ML.jnlp";
            } else {
                ret[i] = st.nextToken() + "/ML.jnlp";
            }
            sb.append(" URL: >> " + ret[i] + "\n");
        }
        logger.log(Level.CONFIG, sb.toString());

        return ret;
    }

    private static final void writeStatusBuffer(boolean wasOK) {
        try {
            String lastUpdateLogFile = UpdaterConfig.getProperty("lia.util.update.Updater.LAST_UDPDATELOG_FILE", null);
            if (lastUpdateLogFile == null) {
                String FARM_HOME = UpdaterConfig.getGlobalEnvProperty("FARM_HOME", null);
                if (FARM_HOME == null) {
                    return;
                }
                lastUpdateLogFile = FARM_HOME + File.separator + "lastUpdate.log";
            }

            String hostname = "N/A";
            String exc = null;
            InetAddress localHostAddr = null;
            try {
                localHostAddr = InetAddress.getLocalHost();
                hostname = localHostAddr.getCanonicalHostName() + " / " + localHostAddr.getHostAddress();
            } catch (Throwable t) {
                exc = " !! Could not determine InetAddr.getLocalHost():\n" + getStackTrace(t);
                hostname = "N/A";
            }

            BufferedWriter bw = new BufferedWriter(new FileWriter(lastUpdateLogFile));
            bw.write("\n ===========  Update Status [ " + ((wasOK) ? "OK" : "NOT_OK") + " ] Updater version: "
                    + MonaLisa_version + " ===========");
            bw.write("\n <-> <-> Local Time: " + new Date());
            bw.write("\n <-> <-> Hostname: " + hostname);
            if (exc != null) {
                bw.write("\n <-> <-> Exc trying to determine Hostname: " + exc);
            }
            bw.write("\n\n");
            bw.write(statusBuffer.toString());
            bw.write("\n\n ===========  End Update Status [ " + ((wasOK) ? "OK" : "NOT_OK") + " ] ===========\n");
            bw.flush();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.WARNING, " [ Updater ] [ writeStatusBuffer ] Exc: ", t);
            }
        }
    }

    public static final void updateML(String[] args) throws Exception {
        try {
            final boolean newUpdater = UpdaterConfig.checkJavaVersion("1.6+");
            final boolean useOldUpdater = UpdaterConfig.getb("lia.util.update.USE_OLD_UPDATER", false);

            if (useOldUpdater) {
                System.out.println("Using old updater because forced in app config");
            } else {
                if (newUpdater) {
                    throw new IllegalStateException(
                            " [ OldUpdater ] Most likely a bug. We got in an illegal state. We do not support Java6+. Please contact support@monalisa.cern.ch");
                }
            }

            //too bad - cannot update to the latest and greatest
            final boolean oldUpdater = UpdaterConfig.checkJavaVersion("1.4+");
            if (oldUpdater) {
                System.err.println("\n\n********** WARNING **************\n");
                System.err.println("Please upgrade your Java installation to a newer version from http://java.com/");
                System.err.println("We no longer support old versions of Java.");
                System.err.println("Please contact support@monalisa.cern.ch for further details. Thank you.");
                System.err.println("\n***********************************\n");
                statusTimer.scheduleAtFixedRate(new TimerTask() {

                    public void run() {
                        System.out.print(".");
                    }
                }, 0, 5 * 1000);// 5s

                try {
                    System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
                    System.setProperty("sun.net.client.defaultReadTimeout", "10000");
                } catch (Throwable t) {
                    logger.log(Level.INFO, "Cannot set timeouts for URLConnection", t);
                }

                if ((getOption(args, "-jnlps") == null) || (getOption(args, "-cachedir") == null)
                        || (getOption(args, "-destdir") == null)) {
                    System.out.println("Usage: " + " -cachedir <path_to_your_cache_dir> "
                            + " -destdir <path_to_your_cache_dir>" + " -jnlps <URL_to_jnlp_file>");
                    System.exit(1);
                }

                versionNumber = getOption(args, "-useVersion");
                if ((versionNumber != null) && (versionNumber.length() == 0)) {
                    versionNumber = null;
                }

                OldUpdater updater = null;

                URLs = getURLs(getOption(args, "-jnlps"));

                if ((URLs == null) || (URLs.length == 0)) {
                    statusBuffer.append("\n").append(new Date()).append("Updater : [ main ] NO URLs defined! ");
                    writeStatusBuffer(false);
                    System.exit(1);
                }

                int i = 0;
                // Just check to see if I can reach the URLs
                boolean statusOK = false;
                for (i = 0; i < URLs.length; i++) {
                    try {
                        URLConnection conn = new URL(URLs[i]).openConnection();
                        conn.setDefaultUseCaches(false);
                        conn.setUseCaches(false);
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        while (br.readLine() != null) {
                            ;
                        }
                        statusOK = true;
                    } catch (Throwable t) {
                        statusBuffer.append(" Got exc reading URL \n").append(URLs[i]).append("\n The Exc: \n")
                                .append(getStackTrace(t));
                    }
                    if (statusOK) {
                        break;
                    }
                }

                if (!statusOK) {
                    statusBuffer.append("\nCannot reach none of the URLs ... maybe a firewall issue ?!?!");
                    logger.log(Level.WARNING, statusBuffer.toString());
                    writeStatusBuffer(false);
                    System.exit(1);
                }

                try {
                    updater = new OldUpdater(args);
                } catch (Throwable t) {
                    statusBuffer.append("\n").append(new Date())
                            .append("Updater : [ main ] Cannot instantiate updater...\nERROR: ");
                    statusBuffer.append("\n").append(getStackTrace(t));
                    writeStatusBuffer(false);
                    logger.log(Level.SEVERE, "Cannot instantiate updater...", t);
                    System.exit(1);
                }

                boolean error = true;
                boolean minorError = true;
                boolean allSameSize = false;
                statusBuffer.append("\n" + new Date() + " Updater : [ main ] Starting update ... ");
                for (i = 0; (i < URLs.length) && error; i++) {
                    error = true;
                    minorError = true;
                    allSameSize = false;
                    if ((URLs[i] != null) && (URLs[i].length() > 0)) {
                        try {
                            statusBuffer.append("\n").append(new Date())
                                    .append(" Updater : [ main ] Trying to get JNLPFile from URL: ").append(URLs[i])
                                    .append(" ... ");
                            jnlpf = getJNLPFile(URLs[i]);
                            statusBuffer.append("OK");
                            statusBuffer.append("\n").append(new Date())
                                    .append(" Updater : [ main ] Trying waitForJars() ... ");
                            updater.waitForJars();
                            statusBuffer.append("OK");
                            statusBuffer.append("\n").append(new Date())
                                    .append(" Updater : [ main ] Trying to copyLocal() ... ");
                            String[] fURLs = updater.copyLocal(new String[] { "Control/lib" });
                            if (fURLs == null) {
                                statusBuffer.append("OK");
                                statusBuffer.append("\n").append(new Date())
                                        .append(" Updater : [ main ] Trying to moveLocal() ... ");
                                allSameSize = updater.moveLocal();
                                statusBuffer.append("OK");
                                error = false;
                                minorError = false;
                            } else {
                                statusBuffer.append("\n\nThere were problems ... but the jars can be ignored ");
                                for (int k = 0; k < fURLs.length; k++) {
                                    statusBuffer.append("\n[ " + fURLs[k] + " ]");
                                }
                                statusBuffer.append("\n");
                                error = true;
                                minorError = true;
                            }
                        } catch (Throwable t) {
                            minorError = false;
                            statusBuffer.append("\n").append(new Date())
                                    .append(" Updater : [ main ] Got EXCEPTION for: ").append(URLs[i]);
                            statusBuffer.append("\n").append(getStackTrace(t));
                            logger.log(Level.WARNING, " UPDATE FROM " + URLs[i] + " FAILED! ", t);
                        }
                    }
                }// for

                new File(cacheDIR + File.separator + "InvalidateCache");

                if ((i == URLs.length) && error && !minorError) {
                    statusBuffer.append("\n\n").append(new Date())
                            .append("Updater : [ main ] Cannot update from any of the URLs");
                    writeStatusBuffer(false);
                    System.exit(1);
                }

                if (!allSameSize) {
                    statusBuffer.append("\n\n").append(new Date())
                            .append(" Updater : [ main ] SUCCESSFUL UPDATE FROM: ").append(URLs[i - 1]);
                    logger.log(Level.INFO, " UPDATE COMPLETE! ");
                } else {
                    statusBuffer.append("\n\n").append(new Date()).append(" Updater : [ main ] SUCCESSFUL RESTART ");
                    logger.log(Level.INFO, " RESTART COMPLETE! ");
                }

                writeStatusBuffer(true);
                System.exit(0);

            } else {
                System.err
                        .println(" [ Updater ] Unable to determine the updater protocol to use. ML needs at least Java 1.4+ and is highly recommended to use at least Java6! Java Version: "
                                + UpdaterConfig.getJavaVersion());
            }
        } catch (Throwable genexc) {
            statusBuffer.append("\n\n").append(new Date())
                    .append("Updater : [ main ] Cannot update from any of the URLs\n\n");
            statusBuffer.append(getStackTrace(genexc));
            writeStatusBuffer(false);
            logger.log(Level.WARNING, "General Ex", genexc);
            System.exit(1);
        }
    }

    public static final void main(String[] args) throws Exception {
        updateML(args);
    }

    public void updateStarted(DownloadEvent downloadEvent) {
        if (downloadEvent == null) {
            return;
        }
    }

    public void downloadStarted(DownloadEvent downloadEvent) {
        if (downloadEvent == null) {
            return;
        }
    }

    public void downloadCompleted(DownloadEvent downloadEvent) {
        if (downloadEvent == null) {
            return;
        }
    }
}
