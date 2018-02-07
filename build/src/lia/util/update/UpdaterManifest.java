/*
 * Created on Sep 23, 2010
 */
package lia.util.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import lia.util.Utils;
import lia.util.update.digest.DigestManager;

/**
 * @author ramiro
 */
public class UpdaterManifest {

    final File file;

    final AppProperties appProperties;

    final List<ManifestEntry> entries;

    final String digest;

    /**
     * @param url
     * @param file
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws NoSuchAlgorithmException
     */
    public UpdaterManifest(final File file, final AppProperties appProperties) throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {

        if(!file.exists()) {
            throw new IOException("File not found: " + file);
        }
        
        final List<ManifestEntry> parsedEntries = parseManifestFile(file);
        if(parsedEntries != null) {
            this.entries = parsedEntries;
        } else {
            this.entries = Collections.emptyList();
        }
        
        this.file = file;
        this.digest = UpdaterUtils.toHexString(DigestManager.getInstance().asyncDigest(file, appProperties.digestAlgo).get().digest);
        if (!this.digest.equals(appProperties.manifestDigest)) {
            throw new IllegalStateException("Different digests appProps: " + appProperties.manifestDigest + " my: " + this.digest);
        }

        this.appProperties = appProperties;
    }

    /**
     * @param file
     * @return
     * @throws IOException
     */
    private static final List<ManifestEntry> parseManifestFile(File file) throws IOException {
        BufferedReader br = null;
        FileReader fr = null;

        try {
            fr = new FileReader(file);
            br = new BufferedReader(fr);
            final List<ManifestEntry> entries = new LinkedList<ManifestEntry>();
            StringBuilder sbCheck = new StringBuilder();
            String prevLine = null;
            for (;;) {
                final String line = br.readLine();
                if (line == null) {
                    break;
                }

                if (prevLine != null) {
                    sbCheck.append(prevLine);
                }
                prevLine = line;

                if (line.trim().startsWith("ManifestEntry")) {
                    entries.add(ManifestEntry.newInstance(line));
                    continue;
                }
            }

            return entries;
        } finally {
            Utils.closeIgnoringException(br);
            Utils.closeIgnoringException(fr);
        }
    }

    @Override
    public String toString() {
        return "UpdaterManifest [file=" + file + ", entries=" + entries + ", properties=" + appProperties + "]";
    }

}
