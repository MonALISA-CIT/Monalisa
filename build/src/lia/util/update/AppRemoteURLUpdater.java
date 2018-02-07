/*
 * Created on Sep 18, 2010
 */
package lia.util.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;
import lia.util.net.Util;
import lia.util.update.digest.DigestManager;
import lia.util.update.digest.DigestResult;
import lia.util.update.downloader.DownloadEvent;
import lia.util.update.downloader.DownloadManager;
import lia.util.update.downloader.DownloadNotifier;
import lia.util.update.downloader.DownloadResult;
import lia.util.update.downloader.RemoteResource;

/**
 * Replaces the old JNLP updater. </br> Supports multiple streams, configurable signature algorithms, application
 * versioning, etc</br>
 * <p>
 * The layout looks similar to this:</br> The URL passed to the updater:
 * <b><i>http://monalisa.cern.ch/~mlupdate/FARM_ML</i></b></br> All <b>root</b> for the application resources, including
 * the updater manifest and its properties will be located under:
 * <b><i>http://monalisa.cern.ch/~mlupdate/FARM_ML/appName</i></b></br> This URL have to always point to the desired
 * application version which will reside under:</br>
 * <b><i>http://monalisa.cern.ch/~mlupdate/FARM_ML/appName_appVersion</i></b></br> <b>Note:</b> This updater
 * <b><i>always</i></b> assumes that the application and version are separated by a dash "_"</br> </br>How it
 * works:</br> If no specific version is desired the updater will first get the properties for the updater
 * manifest:</br> <i>http://monalisa.cern.ch/~mlupdate/FARM_ML/appName/app.upmf.properties</i></br> Based on the version
 * found in this <code>.properties</code> file the updater will determine the specific version to use and will download
 * the updater manifest and all its entries from:</br>
 * <i>http://monalisa.cern.ch/~mlupdate/FARM_ML/appName_appVersion/</i></br> </br><b>A note about the cache:</b></br>
 * The cache is version and URL agnostic in the sense that all application resources, including its manifest, will be
 * cached in <b><code>cacheDirectory/appName</code></b></br>
 * 
 * @since ML 1.9.0
 * @author ramiro
 */
public class AppRemoteURLUpdater implements DownloadNotifier {

    /**
     * Prints statistics during download
     */
    private static final class DownloadReportingTask implements Runnable {

        long toDownload = 0;

        private final Map<RemoteResource, ManifestEntry> resourcesMap = new ConcurrentHashMap<RemoteResource, ManifestEntry>();

        private final Map<RemoteResource, Long> downloadedMap = new ConcurrentHashMap<RemoteResource, Long>();

        final AppRemoteURLUpdater updater;

        DownloadReportingTask(AppRemoteURLUpdater updater) {
            this.updater = updater;
        }

        void addResource(RemoteResource rr, ManifestEntry manifestEntry) {
            resourcesMap.put(rr, manifestEntry);
            toDownload += manifestEntry.size;
            downloadedMap.put(rr, Long.valueOf(0L));
        }

        void notifyDownloadEvent(DownloadEvent event) {
            final RemoteResource rr = event.resource;
            downloadedMap.put(rr, Long.valueOf(event.downloadedSize));
        }

        final long computeRemaining() {
            long remaining = 0;
            for (final Map.Entry<RemoteResource, ManifestEntry> entry : resourcesMap.entrySet()) {
                final RemoteResource rr = entry.getKey();
                final long tSize = entry.getValue().size;
                final long dSize = downloadedMap.get(rr).longValue();
                remaining += (tSize - dSize);
            }
            return remaining;
        }

        @Override
        public void run() {
            if (updater.updateStarted) {
                updater.logOut("Download stats: remaining "
                        + Util.valToString(computeRemaining(), Util.VALUE_2_STRING_SHORT_UNIT) + "Bytes out of "
                        + Util.valToString(toDownload, Util.VALUE_2_STRING_SHORT_UNIT) + "Bytes");
            }
        }

    }

    private static final Logger logger = Logger.getLogger(AppRemoteURLUpdater.class.getName());

    private static final AtomicInteger CONNECT_TIMEOUT = new AtomicInteger((int) TimeUnit.SECONDS.toMillis(30));

    private static final AtomicInteger READ_TIMEOUT = new AtomicInteger((int) TimeUnit.SECONDS.toMillis(60));

    private static volatile boolean bLogFine;

    private static volatile boolean bLogFiner;

    private static volatile boolean bLogFinest;

    private volatile DownloadManager downloader;

    private final boolean standAlone;

    final UpdaterArgs upArgs;

    final DownloadReportingTask reportingTask;

    Future<?> reportingTaskFuture;

    private volatile boolean updateStarted;

    public AppRemoteURLUpdater(final String[] args, boolean standAlone) {
        this.standAlone = standAlone;
        this.upArgs = UpdaterArgs.newInstance(args);
        this.reportingTask = (standAlone) ? new DownloadReportingTask(this) : null;
    }

    private static final void initConf() {
        final long cTimeout = AppConfig.getl("lia.util.update.CONNECT_TIMEOUT", TimeUnit.SECONDS.toMillis(30));
        if (cTimeout < 0) {
            CONNECT_TIMEOUT.set((int) TimeUnit.SECONDS.toMillis(20));
        } else {
            CONNECT_TIMEOUT.set((int) TimeUnit.SECONDS.toMillis(cTimeout));
        }

        final long rTimeout = AppConfig.getl("lia.util.update.READ_TIMEOUT", TimeUnit.SECONDS.toMillis(60));
        if (rTimeout < 0) {
            READ_TIMEOUT.set((int) TimeUnit.SECONDS.toMillis(20));
        } else {
            READ_TIMEOUT.set((int) TimeUnit.SECONDS.toMillis(rTimeout));
        }
    }

    private final void initLogging(boolean noUpdateLog) {
        try {
            // add update.log
            if (!noUpdateLog) {
                try {
                    FileHandler fh = null;
                    Level ll = Level.INFO;

                    final String farmHome = AppConfig.getProperty("FARM_HOME");
                    if (farmHome != null) {
                        fh = new FileHandler(farmHome + File.separator + "update%g.log", 10 * 1024, 2, true);
                        fh.setFormatter(new SimpleFormatter());
                        fh.setLevel(Level.ALL);
                        logger.setUseParentHandlers(false);
                        logger.addHandler(fh);
                        // try check the logging level
                        final String possibleLevel = AppConfig.getProperty("lia.util.update.level");
                        if (possibleLevel != null) {

                            try {
                                ll = Level.parse(possibleLevel);
                            } catch (Throwable t) {
                                ll = Level.INFO;
                            }

                        }

                        logger.setLevel(ll);
                        logger.log(Level.INFO, "Logging properties set. file logger: " + fh.getLevel() + " my logger: "
                                + ll);
                    } else {
                        logger.log(Level.INFO, "Null FARM_HOME env var ... will log only @ console");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Unable to set logging properties. Cause:", t);
                }
            }

        } catch (Throwable t) {
            System.err
                    .println("[ AppRemoteURLUpdater ] [ static init ] [ logging ] Unable to check/load default logger props. Cause:");
            t.printStackTrace();
        }

        bLogFinest = logger.isLoggable(Level.FINEST);
        bLogFiner = bLogFinest || logger.isLoggable(Level.FINER);
        bLogFine = bLogFiner || logger.isLoggable(Level.FINE);

    }

    private static URL getAppURL(String url, final String fullAppName) throws MalformedURLException {
        final String uSuffix = (fullAppName == null) ? "" : fullAppName;

        String tURL = url.trim();
        while (tURL.endsWith("/")) {
            tURL = tURL.substring(0, tURL.length() - 1);
        }

        return new URL(tURL + '/' + uSuffix + "/");
    }

    /**
     * @param URLList
     *            comma separated list
     * @param version
     *            if no specific version needed use blank string ""
     * @return
     * @throws MalformedURLException
     */
    private static List<URL> getAppURLs(final String[] URLList, final String fullAppName) throws MalformedURLException {
        if ((URLList == null) || (URLList.length == 0)) {
            return Collections.emptyList();
        }

        List<URL> urlList = new ArrayList<URL>();
        for (final String sURL : URLList) {
            urlList.add(getAppURL(sURL, fullAppName));
        }

        return urlList;
    }

    private static final void checkDirectories(File cacheDir, File destDir) throws Exception {

        if (cacheDir.exists()) {
            if (!cacheDir.isDirectory()) {
                throw new Exception("AppRemoteURLUpdater: The destination cache dir: " + cacheDir
                        + " is not a directory");
            }

            if (destDir.exists()) {
                if (!destDir.isDirectory()) {
                    throw new Exception("AppRemoteURLUpdater: The destination dir: " + destDir + " is not a directory");
                }
            } else {
                if (destDir.mkdirs()) {
                    // we're good
                } else {
                    throw new Exception("AppRemoteURLUpdater: Unable to create destination directory: " + destDir);
                }
            }
        } else {
            if (cacheDir.mkdirs()) {
                // we're good
            } else {
                throw new Exception("AppRemoteURLUpdater: Unable to create cache directory: " + cacheDir);
            }
        }
    }

    /**
     * 
     * @param umf
     * @param codeBaseURL
     * @param cacheDir
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    private Map<RemoteResource, ManifestEntry> synchronizeCache(UpdaterManifest umf, URL codeBaseURL,
            final File cacheDir) throws InterruptedException, ExecutionException, IOException, NoSuchAlgorithmException {
        final Map<RemoteResource, ManifestEntry> retMap = new HashMap<RemoteResource, ManifestEntry>();
        final Map<RemoteResource, Future<DigestResult>> cacheDigest = new HashMap<RemoteResource, Future<DigestResult>>();

        final List<Future<DownloadResult>> resourcesFutures = new ArrayList<Future<DownloadResult>>(umf.entries.size());

        final String codebasePath = umf.file.getParent();
        for (final ManifestEntry manifestEntry : umf.entries) {
            final URL url = new URL(codeBaseURL.toString() + '/' + manifestEntry.name);
            final File destFile = new File(codebasePath + File.separatorChar + manifestEntry.name);
            final File pDir = destFile.getParentFile();
            if (!pDir.exists()) {
                if (!pDir.mkdirs()) {
                    // double check locking idiom; some other thread created the directory just after checking
                    if (!pDir.exists()) {
                        throw new IOException("Unable to create destination directory for cache:" + pDir);
                    }
                }
            }

            RemoteResource rr = new RemoteResource(url, destFile, umf.appProperties.digestAlgo);
            retMap.put(rr, manifestEntry);
            if (destFile.exists()) {
                // compute the digest
                cacheDigest.put(rr, DigestManager.getInstance().asyncDigest(destFile, rr.digestAlgorithm));
            } else {
                if (bLogFine) {
                    logger.log(Level.FINE, "[ syncCache ] first time download for " + rr);
                }

                if (standAlone) {
                    reportingTask.addResource(rr, manifestEntry);
                }

                resourcesFutures.add(downloader.asyncDownload(this, rr));
            }
        }

        for (final Map.Entry<RemoteResource, Future<DigestResult>> entry : cacheDigest.entrySet()) {
            final RemoteResource rr = entry.getKey();
            final ManifestEntry me = retMap.get(rr);
            final DigestResult dr = entry.getValue().get();

            final boolean bTimeOK = upArgs.checkTimestamp() ? dr.file.lastModified() == me.lastModified : true;
            final boolean bSizeOK = upArgs.checkSize() ? dr.file.length() == me.size : true;
            final boolean bCryptoHashOK = upArgs.ignoreCryptoHash() ? true : me.digest.equals(UpdaterUtils
                    .toHexString(dr.digest));

            if (!upArgs.isFullUpdateSet()) {
                if (bTimeOK && bSizeOK && bCryptoHashOK) {
                    continue;
                }
            }

            if (bLogFine) {
                logger.log(Level.FINE, "[ syncCache ] download for " + rr + " timeStampCheck=" + bTimeOK
                        + ", sizeCheck=" + bSizeOK + ", cryptoHashCheck=" + bCryptoHashOK + ", isFullUpdateSet="
                        + upArgs.isFullUpdateSet());
            }
            if (standAlone) {
                reportingTask.addResource(rr, me);
            }
            resourcesFutures.add(downloader.asyncDownload(this, rr));
        }
        // start the reporting task
        if (standAlone) {
            if (reportingTask.toDownload > 0) {
                logOut("Need to download " + Util.valToString(reportingTask.toDownload, Util.VALUE_2_STRING_SHORT_UNIT)
                        + "Bytes");
                reportingTaskFuture = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

                    @Override
                    public Thread newThread(Runnable r) {
                        final Thread thread = new Thread(r);
                        thread.setName("ConsoleReporting task worker");
                        thread.setDaemon(true);
                        return thread;
                    }
                }).scheduleWithFixedDelay(reportingTask, 1, 2, TimeUnit.SECONDS);
            } else {
                logOut("Nothing to download.");
            }
        }

        for (final Future<DownloadResult> future : resourcesFutures) {
            DownloadResult dwldRes = future.get();
            RemoteResource rr = dwldRes.getRemoteResource();
            ManifestEntry me = retMap.get(rr);
            rr.destinationFile.setLastModified(me.lastModified);
            if (rr.destinationFile.length() != me.size) {
                throw new IllegalStateException("Error for " + me + " different length");
            }

            final String dwldDigest = UpdaterUtils.toHexString(dwldRes.getCheckSum());
            final String remoteDigest = me.digest;

            if (!dwldDigest.equals(remoteDigest)) {
                throw new IllegalStateException("Error for " + me.name + " different checksums: expected="
                        + remoteDigest + "; dwld: " + dwldDigest);
            }

            UpdaterUtils.setRWOwnerOnly(rr.destinationFile);
        }

        // cancel the reporting
        if (standAlone && (reportingTaskFuture != null)) {
            reportingTaskFuture.cancel(false);
            logOut("Download finished");
        }

        // cancel downloader
        if ((downloader != null) && !downloader.cancelAndShutdown()) {
            logger.log(Level.WARNING, "Unable to cancel downloader");
        }

        return retMap;
    }

    private final UpdaterManifest getRemoteUpdaterManifest(final URL url, final AppProperties appProperties)
            throws IOException, NoSuchAlgorithmException, InterruptedException, ExecutionException {
        final File parentDir = appProperties.cacheDestinationDir;
        final String sURL = stripURL(url) + "/app.upmf";

        DownloadResult dr = downloader.asyncDownload(
                new RemoteResource(new URL(sURL), new File(parentDir.getAbsolutePath() + File.separator + "app.upmf"),
                        appProperties.digestAlgo)).get();
        return new UpdaterManifest(dr.getRemoteResource().destinationFile, appProperties);
    }

    private static final String stripURL(URL url) {
        String strippedURL = url.toString();
        while (strippedURL.endsWith("/")) {
            strippedURL = strippedURL.substring(0, strippedURL.length() - 1);
        }
        return strippedURL;
    }

    public AppProperties getRemoteAppProperties(String[] remoteURLs, final File cacheDestinationDir,
            final String appName) throws IOException {
        final List<URL> updateURLList = getAppURLs(remoteURLs, appName);
        Throwable lastException = null;
        downloader = DownloadManager.newInstance("lia.util.update", 5, CONNECT_TIMEOUT.get(), READ_TIMEOUT.get());
        for (final URL url : updateURLList) {
            lastException = null;
            try {
                // get first URL which do not fail
                return downloadAndGetAppProperties(url, cacheDestinationDir, null, true);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " Exception getting app properties for URL: " + url, t);
                }
                lastException = t;
            }
        }

        if (lastException != null) {
            // it should not get here but maybe we'll get lucky
            throw new IOException("Unable to get the app properties", lastException);
        }

        throw new IOException("Unable to get the app properties. errors privided.");
    }

    private final AppProperties syncAndGetAppProperties(final List<URL> updateURLList, final File cacheDestinationDir,
            final File appDestDir) throws IOException {

        Throwable lastException = null;
        for (final URL url : updateURLList) {
            try {
                // get first URL which do not fail
                return downloadAndGetAppProperties(url, cacheDestinationDir, appDestDir, false);
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Exception getting app properties for URL: " + url, t);
                lastException = t;
            }
        }

        if (lastException != null) {
            // it should not get here but maybe we'll get lucky
            throw new IOException("Unable to get the app properties", lastException);
        }

        throw new IOException("Unable to get the app properties. errors privided.");
    }

    private final AppProperties downloadAndGetAppProperties(final URL url, final File destCacheDir,
            final File destAppDir, boolean tmpFile) throws IOException, InterruptedException, ExecutionException {
        File dstFile = null;
        try {
            final URL manifestPropertiesURL = new URL(stripURL(url) + "/app.upmf.properties");
            if (tmpFile) {
                dstFile = File.createTempFile("app.upmf.properties", ".TMP", destCacheDir);
                dstFile.deleteOnExit();
            } else {
                dstFile = new File(destCacheDir.getAbsolutePath() + File.separator + "app.upmf.properties");
            }

            DownloadResult dr = downloader.asyncDownload(new RemoteResource(manifestPropertiesURL, dstFile)).get();
            final File propertiesFile = dr.getRemoteResource().destinationFile;
            final Properties p = new Properties();

            FileReader fr = null;
            BufferedReader br = null;
            try {
                fr = new FileReader(propertiesFile);
                br = new BufferedReader(fr);
                p.load(br);
            } finally {
                UpdaterUtils.closeIgnoringException(fr);
                UpdaterUtils.closeIgnoringException(br);
            }
            return new AppProperties(p, destCacheDir, destAppDir);
        } finally {
            if (tmpFile && (dstFile != null)) {
                dstFile.delete();
            }
        }
    }

    private static final String toSecondsAndMillis(final long delayNanos) {
        final long seconds = TimeUnit.NANOSECONDS.toSeconds(delayNanos);
        final long remMillis = TimeUnit.NANOSECONDS.toMillis(delayNanos - TimeUnit.SECONDS.toNanos(seconds));

        return seconds + "." + remMillis;
    }

    public final boolean doUpdate(boolean onlyCacheSync) throws Exception {
        final long sTime = Utils.nanoNow();

        boolean bError = true;
        try {
            if (standAlone) {
                initLogging(upArgs.isNoupdatelog());
                initConf();
                try {
                    System.setProperty("sun.net.client.defaultConnectTimeout", "" + CONNECT_TIMEOUT.get());
                    System.setProperty("sun.net.client.defaultReadTimeout", "" + READ_TIMEOUT.get());
                } catch (Throwable t) {
                    logger.log(Level.INFO, "Cannot set timeouts for URLConnection", t);
                }
            }

            bLogFinest = logger.isLoggable(Level.FINEST);
            bLogFiner = bLogFinest || logger.isLoggable(Level.FINER);
            bLogFine = bLogFiner || logger.isLoggable(Level.FINE);

            logOut("** AppRemoteURLUpdater started **");
            // TODO check for maximum; leave 1 only for debugging
            downloader = DownloadManager.newInstance("lia.util.update", 5, CONNECT_TIMEOUT.get(), READ_TIMEOUT.get());

            logger.log(Level.INFO, "[ AppRemoteURLUpdater ] started update with args: " + upArgs);
            // check the cache and destination directories
            final File cacheDir = new File(upArgs.cacheDirPath());
            final File appDestDir = new File(upArgs.destDirPath());
            checkDirectories(cacheDir, appDestDir);

            final String appName = upArgs.getAppName();
            final String appPropAppName = upArgs.getFullAppName();

            final String[] splittedURLs = Utils.getSplittedListFields(upArgs.getJnlps());
            final List<URL> updateURLList = getAppURLs(splittedURLs, appPropAppName);

            String tmpCacheAbsPath = cacheDir.getAbsolutePath();
            while (tmpCacheAbsPath.endsWith(File.separator)) {
                tmpCacheAbsPath.substring(0, tmpCacheAbsPath.length() - 1);
            }

            final String cacheRootPath = tmpCacheAbsPath + File.separator + appName;
            final File destinationCacheDir = new File(cacheRootPath);

            final AppProperties appProperties = syncAndGetAppProperties(updateURLList, destinationCacheDir, appDestDir);
            if (!appProperties.stableBuild) {
                if (AppConfig.getb("lia.util.update.stableBuildOnly", true)) {
                    logger.log(
                            Level.INFO,
                            "Remote version of "
                                    + appName
                                    + " is a development build. Current configuration accepts only stable builds. No changes performed.");
                    bError = false;
                    return false;
                }
                logger.log(
                        Level.WARNING,
                        "Remote version of "
                                + appName
                                + " is a development build and current configuration accepts such snapshots. Updater will continue.");
            } else {
                logger.log(Level.INFO, "Remote version of " + appName + " is a stable build. ");
            }

            final String fullAppName = appName + '_' + appProperties.appVersion;
            // for now I know the application and the version
            final List<URL> appURLList = (upArgs.hasAppVersion()) ? updateURLList : getAppURLs(splittedURLs,
                    fullAppName);

            final Map<File, File> appBkpFiles = new HashMap<File, File>();
            final Map<File, File> appNewFiles = new HashMap<File, File>();
            for (final URL url : appURLList) {
                try {
                    appBkpFiles.clear();
                    appNewFiles.clear();
                    logOut("Trying to synchronize local cache from: " + url);

                    final UpdaterManifest umf = getRemoteUpdaterManifest(url, appProperties);
                    logOut("UpdaterManifest loaded successfully ... synchronizing local cache");

                    final long nanoStart = Utils.nanoNow();
                    final Map<RemoteResource, ManifestEntry> cacheMap = synchronizeCache(umf, url, cacheDir);

                    // local cache in sync
                    logOut("Local cache synchronized in " + toSecondsAndMillis(Utils.nanoNow() - nanoStart)
                            + " seconds");

                    if (onlyCacheSync) {
                        bError = false;
                        return true;
                    }

                    logOut("Start commiting changes to local installation");

                    final long nanoStartLocal = Utils.nanoNow();

                    final Map<RemoteResource, Future<DigestResult>> appDigest = new HashMap<RemoteResource, Future<DigestResult>>();
                    final Map<RemoteResource, File> copyMap = new HashMap<RemoteResource, File>();
                    for (final Map.Entry<RemoteResource, ManifestEntry> entry : cacheMap.entrySet()) {
                        final RemoteResource rr = entry.getKey();
                        final ManifestEntry me = entry.getValue();

                        final File appFile = new File(appDestDir.getAbsolutePath() + File.separator + me.name);
                        if (appFile.exists()) {
                            appDigest.put(rr, DigestManager.getInstance().asyncDigest(appFile, rr.digestAlgorithm));
                        } else {
                            copyMap.put(rr, appFile);
                        }
                    }

                    // check the new files
                    for (final Map.Entry<RemoteResource, Future<DigestResult>> entry : appDigest.entrySet()) {
                        final RemoteResource rr = entry.getKey();
                        final ManifestEntry me = cacheMap.get(rr);
                        final DigestResult dr = entry.getValue().get();

                        final boolean bTimeOK = upArgs.checkTimestamp() ? dr.file.lastModified() == me.lastModified
                                : true;
                        final boolean bSizeOK = upArgs.checkSize() ? dr.file.length() == me.size : true;
                        final boolean bCryptoHashOK = upArgs.ignoreCryptoHash() ? true : me.digest.equals(UpdaterUtils
                                .toHexString(dr.digest));

                        if (!upArgs.isFullUpdateSet()) {

                            if (bTimeOK && bSizeOK && bCryptoHashOK) {
                                continue;
                            }
                        }

                        if (bLogFine) {
                            logger.log(Level.FINE, "[ doUpdate ] Resource " + rr + " added to copyMap timeStampCheck="
                                    + bTimeOK + ", sizeCheck=" + bSizeOK + ", cryptoHashCheck=" + bCryptoHashOK
                                    + ", isFullUpdateSet=" + upArgs.isFullUpdateSet());
                        }

                        copyMap.put(rr, dr.file);
                    }

                    // copy new files
                    for (final Map.Entry<RemoteResource, File> entry : copyMap.entrySet()) {
                        final File appFile = entry.getValue();
                        final File cacheFile = entry.getKey().destinationFile;

                        final File pDir = appFile.getParentFile();
                        if (!pDir.exists()) {
                            pDir.mkdirs();
                        }

                        final File newFileUpdate = File.createTempFile(appFile.getName(), ".UPDATE", pDir);
                        UpdaterUtils.copyFile2File(cacheFile, newFileUpdate);
                        appNewFiles.put(appFile, newFileUpdate);

                        if (appFile.exists()) {
                            final File bkpFile = File.createTempFile(appFile.getName(), ".BKP", pDir);
                            if (appFile.renameTo(bkpFile)) {
                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER, "[ Updater ] [ COMMIT CHANGES ] moved " + appFile
                                            + " to bkp file: " + bkpFile);
                                }
                            } else {
                                logger.log(Level.SEVERE,
                                        "[ Updater ] [ COMMIT CHANGES ] unable to backup the appFile '" + appFile
                                                + "' to temp file: '" + bkpFile + "'");
                                throw new Exception("[ Updater ] [ COMMIT CHANGES ] unable to backup the appFile '"
                                        + appFile + "' to temp file: '" + bkpFile + "'");
                            }
                            appBkpFiles.put(appFile, bkpFile);
                        }

                        UpdaterUtils.setRWOwnerOnly(appFile);
                        if (newFileUpdate.renameTo(appFile)) {
                            logger.log(Level.FINE, " [ OK ] TMP update file " + newFileUpdate
                                    + " moved to application file: " + appFile);
                        } else {
                            // shit happens
                            throw new Exception(" Unable to move TMP update file " + newFileUpdate
                                    + " to application file: " + appFile);
                        }
                    }

                    // we're ok; delete the bkps
                    deleteBkps(appBkpFiles);
                    appBkpFiles.clear();
                    deleteBkps(appNewFiles);
                    appNewFiles.clear();

                    logOut("Finished commiting changes in " + toSecondsAndMillis(Utils.nanoNow() - nanoStartLocal)
                            + " seconds");

                    // we are ok!
                    bError = false;

                    return true;
                } catch (Throwable t) {
                    if (standAlone) {
                        logOut("[" + new Date() + "] unable to update from: " + url + " check update.log for details");
                    }
                    logger.log(Level.WARNING, "Got exception for URL: " + url + ". Cause: ", t);
                    if (appBkpFiles.size() > 0) {
                        if (standAlone) {
                            logOut("[" + new Date() + "] restoring bkp files ");
                        }
                        logger.log(Level.WARNING, "restoring bkp files " + appBkpFiles, t);
                        appBkpRollback(appBkpFiles);
                        appBkpFiles.clear();
                    }

                    if (appNewFiles.size() > 0) {
                        deleteBkps(appNewFiles);
                        appNewFiles.clear();
                    }
                }
            }

        } finally {
            logOut("Update finished " + (bError ? "NOT" : "") + " OK in " + toSecondsAndMillis(Utils.nanoNow() - sTime)
                    + " seconds");
            logOut("** AppRemoteURLUpdater finished **");
        }
        return bError;
    }

    private static void deleteBkps(Map<File, File> appBkpFiles) {
        for (final File bkpFile : appBkpFiles.values()) {
            bkpFile.deleteOnExit();
            // not so much to check; I may throw some errors here
            bkpFile.delete();
        }
    }

    private static void appBkpRollback(Map<File, File> appBkpFiles) throws IOException {
        for (final Map.Entry<File, File> entry : appBkpFiles.entrySet()) {
            final File bkpFile = entry.getValue();
            final File appFile = entry.getKey();
            try {
                UpdaterUtils.copyFile2File(bkpFile, appFile);
                bkpFile.deleteOnExit();
                bkpFile.delete();
            } catch (Throwable t) {
                // because shit happens; cosmic ray hit lucky ones
                logger.log(Level.SEVERE,
                        "[ SEVERE ERROR ] Unable to restore app jars from a failed download. Please check your FS for I/O errors: "
                                + appFile, t);
                throw new IOException(
                        "[ SEVERE ERROR ] Unable to restore app jars from a failed download. Please your FS for I/O errors. Full log in Service/myFarm/update*.log. File with errors: "
                                + appFile, t);
            }
        }
    }

    public static final void main(String[] args) throws Exception {
        new AppRemoteURLUpdater(args, true).doUpdate(false);
    }

    private final void logOut(String msg) {
        if (standAlone) {
            System.out.println("[" + new Date() + "] " + msg);
        }
        logger.log(Level.INFO, "[ AppRemoteURLUpdater ] " + msg);
    }

    @Override
    public void notifyDownloadEvent(DownloadEvent event) {
        if (!updateStarted && (event.eventType == DownloadEvent.Type.DOWNLOAD_STARTED)) {
            updateStarted = true;
        }

        if (standAlone) {
            reportingTask.notifyDownloadEvent(event);
        }
    }

    @Override
    public long getProgressNotifierDelay(TimeUnit unit) {
        return unit.convert(2, TimeUnit.SECONDS);
    }
}
