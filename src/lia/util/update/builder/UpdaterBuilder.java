/*
 * Created on Sep 18, 2010
 */
package lia.util.update.builder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import lia.Monitor.monitor.AppConfig;
import lia.util.Utils;
import lia.util.update.ManifestEntry;
import lia.util.update.UpdaterUtils;
import lia.util.update.digest.DigestManager;
import lia.util.update.digest.DigestResult;

/**
 * @author ramiro
 */
public class UpdaterBuilder {

    public String codebaseDir;

    public String directories;

    public String digestAlgo;

    public String updaterManifest;

    final static DigestManager digestManager = DigestManager.newInstance(4);

    final static String MD5 = "MD5";

    private final static boolean endsWith(final String fName, String[] suffixes) {
        for(final String suffix: suffixes) {
            if(fName.endsWith(suffix)) {
                return true;
            }
        }
        
        return false;
    }
    
    public static final void getRecursiveFiles(String codebase, String fileName, Map<File, Future<DigestResult>> allMLDigests, Map<File, Future<DigestResult>> allMD5Digests, final String mlDigestAlgo, final String[] fileNameSuffixes) throws IOException, NoSuchAlgorithmException {

        File file = new File(fileName);

        if (file.exists() && file.canRead()) {
            if (file.isFile()) {
                if (file.getName().startsWith(".") || !endsWith(file.getName(), fileNameSuffixes)) {
                    return;
                }
                allMLDigests.put(file, digestManager.asyncDigest(file, mlDigestAlgo));
                allMD5Digests.put(file, digestManager.asyncDigest(file, MD5));
            } else if (file.isDirectory()) {
                if (file.getName().equalsIgnoreCase("CVS")) {
                    return;
                }
                String[] listContents = file.list();
                if (listContents != null && listContents.length > 0) {
                    for (String subFile : listContents) {
                        getRecursiveFiles(codebase, fileName + File.separator + subFile, allMLDigests, allMD5Digests, mlDigestAlgo, fileNameSuffixes);
                    }
                }
            } else {
                if (file.getName().startsWith(".") || !endsWith(file.getName(), fileNameSuffixes)) {
                    return;
                }
                allMLDigests.put(file, digestManager.asyncDigest(file, mlDigestAlgo));
                allMD5Digests.put(file, digestManager.asyncDigest(file, MD5));
            }
        }
    }

    /**
     * @param args
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InterruptedException, ExecutionException {
        final long start = Utils.nanoNow();
        final String[] fileNameSuffixes = AppConfig.getVectorProperty("suffixes", new String[] {".jar"});
        if (fileNameSuffixes == null || fileNameSuffixes.length == 0) {
            throw new IllegalArgumentException(" The property updater.suffixes is not correctly defined! ");
        } 

        System.out.println(" Suffixes: " + Arrays.toString(fileNameSuffixes));

        final String[] dirs = AppConfig.getVectorProperty("dirs");
        if (dirs == null || dirs.length == 0) {
            throw new IllegalArgumentException(" The property updater.directories is not correctly defined! ");
        }

        System.out.println(" Dirs: " + Arrays.toString(dirs));

        final String updaterCodeBase = AppConfig.getProperty("codebase", ".");
        final File codebaseDirectory = new File(updaterCodeBase);
        if (!codebaseDirectory.exists() || !codebaseDirectory.isDirectory()) {
            throw new IllegalArgumentException(" The property updater.codebase.directory=" + codebaseDirectory.getAbsolutePath() + " does not point to a directory ");
        }
        
        System.out.println(" updaterCodeBase: " + updaterCodeBase);

        final String manifestPath = AppConfig.getProperty("manifest");
        final String manifestPropertiesPath = manifestPath + ".properties";

        if (manifestPath == null || manifestPath.trim().length() == 0) {
            throw new IllegalArgumentException(" The property manifestfile is not correctly defined! ");
        }
        final String digestAlgo = AppConfig.getProperty("digestalgo", "SHA1");
        if (digestAlgo == null) {
            throw new IllegalArgumentException(" The property digestalgo is not correctly defined! ");
        }

        final String md5File = AppConfig.getProperty("md5file");
        if (md5File == null || md5File.trim().length() == 0) {
            throw new IllegalArgumentException(" The property md5file is not correctly defined! ");
        }

        final MessageDigest mlDigest = MessageDigest.getInstance(digestAlgo);

        Map<File, Future<DigestResult>> allMLDigests = new HashMap<File, Future<DigestResult>>();
        Map<File, Future<DigestResult>> allMD5Digests = new HashMap<File, Future<DigestResult>>();

        final String codeBaseAbsPath = (codebaseDirectory.getAbsolutePath().endsWith(File.separator)) ? codebaseDirectory.getAbsolutePath() : codebaseDirectory.getAbsolutePath() + File.separator;

        for (final String dir : dirs) {
            getRecursiveFiles(codeBaseAbsPath, codeBaseAbsPath + ((dir.startsWith(File.separator)) ? dir.substring(0, dir.length() - 1) : dir), allMLDigests, allMD5Digests, digestAlgo, fileNameSuffixes);
        }

        //K - manifest entry details, including desired digest, V - the MD5 digest 
        final Map<ManifestEntry, DigestResult> allFiles = new HashMap<ManifestEntry, DigestResult>();
        for (final Map.Entry<File, Future<DigestResult>> entry : allMLDigests.entrySet()) {
            final File file = entry.getKey();
            final DigestResult mlDigestResult = entry.getValue().get();
            final DigestResult md5DigestResult = allMD5Digests.get(file).get();
            allFiles.put(new ManifestEntry(file.getAbsolutePath().substring(codeBaseAbsPath.length()), mlDigestResult.size, mlDigestResult.timestamp, UpdaterUtils.toHexString(mlDigestResult.digest)), md5DigestResult);
        }
        final BufferedWriter md5fWriter = new BufferedWriter(new FileWriter(md5File));
        final BufferedWriter manifestWriter = new BufferedWriter(new FileWriter(manifestPath));
        final BufferedWriter manifestPropertiesWriter = new BufferedWriter(new FileWriter(manifestPropertiesPath));

        final String appJEnvName = AppConfig.getProperty("APP_NAME");
        final String appName = (appJEnvName != null)?appJEnvName:AppConfig.getGlobalEnvProperty("APP_NAME");
        final String appJEnvVersion = AppConfig.getProperty("APP_VERSION");
        final String appVersion = (appJEnvVersion != null)?appJEnvVersion:AppConfig.getGlobalEnvProperty("APP_VERSION");
        final String appJEnvBuildID = AppConfig.getProperty("APP_BUILD_ID");
        final String appBuildID = (appJEnvBuildID != null)?appJEnvBuildID:AppConfig.getGlobalEnvProperty("APP_BUILD_ID");
        final String appJStableBuild = AppConfig.getProperty("APP_STABLE_BUILD");
        final String appStableBuild = (appJStableBuild != null)?appJStableBuild:AppConfig.getGlobalEnvProperty("APP_STABLE_BUILD");
        final char firstChar = (appStableBuild == null || appStableBuild.trim().length() == 0)?'f':appStableBuild.trim().charAt(0);
        final boolean stableBuild = (firstChar == 't' || firstChar =='T' || firstChar == '1' || firstChar == 'y' || firstChar == 'Y');
        
        StringBuilder sb = new StringBuilder(1 * 1024 * 1024);
        StringBuilder sbProperties = new StringBuilder(2048);
        
        //application name and version
        if(appName == null || appName.trim().length() == 0) {
            throw new IllegalArgumentException("Unable to determine appname");
        }
        sb.append("appname=").append(appName).append("\n");
        if(appVersion == null || appVersion.trim().length() == 0) {
            throw new IllegalArgumentException("Unable to determine appversion");
        }
        sb.append("appversion=").append(appVersion).append("\n");
        sb.append("appbuildid=").append(appBuildID).append("\n");
        sb.append("stablebuild=").append(stableBuild).append("\n");
        sb.append("digestalgo=").append(digestAlgo).append("\n");
        sb.append("timestamp=").append(System.currentTimeMillis()).append("\n");
        sbProperties.append(sb.toString());
        StringBuilder sbMD5 = new StringBuilder(1 * 1024 * 1024);
        for (final Map.Entry<ManifestEntry, DigestResult> entry : allFiles.entrySet()) {
            final ManifestEntry me = entry.getKey();
            final DigestResult md5Digest = entry.getValue();
            sbMD5.append(UpdaterUtils.toHexString(md5Digest.digest)).append("  ").append(me.name).append("\n");
            sb.append(me).append("\n");
        }
        
        final String iCheck = UpdaterUtils.toHexString(mlDigest.digest(sb.toString().getBytes("UTF-8")));
        sbProperties.append("digest=").append(iCheck).append("\n");
        md5fWriter.write(sbMD5.toString());
        manifestWriter.write(sb.toString());
        mlDigest.reset();
        manifestWriter.flush();
        manifestWriter.close();
        md5fWriter.flush();
        md5fWriter.close();
        manifestPropertiesWriter.write(sbProperties.toString());
        manifestPropertiesWriter.flush();
        manifestPropertiesWriter.close();
        
        System.out.println("UpdateFileBuilder finshed building updater files in: " + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - start) + " millis. Will now check the reverse reading.");
        // checking the ml.prop
        final BufferedReader br = new BufferedReader(new FileReader(manifestPath));
        for (;;) {
            final String line = br.readLine();
            if (line == null) {
                break;
            }
            if (line.indexOf('[') < 0) {
                System.out.println("prop line: " + line);
                continue;
            }
            System.out.println(ManifestEntry.newInstance(line));
        }
        System.out.println("UpdateFileBuilder finshed checking. Manifest: " + new File(manifestPath).getAbsolutePath() + " and properties " + new File(manifestPropertiesPath).getAbsolutePath() +" ready. Total DT= " + TimeUnit.NANOSECONDS.toMillis(Utils.nanoNow() - start) + " millis");
    }

}
