/*
 * Created on Sep 29, 2010
 */
package lia.util.update;

import java.io.File;

/**
 * 
 *
 * @author ramiro
 */
public class AppEntry {

    final ManifestEntry manifestEntry;
    final File cacheFile;
    final File destinationFile;

    /**
     * @param manifestEntry
     * @param cacheFile
     * @param destinationFile
     */
    public AppEntry(ManifestEntry manifestEntry, File cacheFile, File destinationFile) {
        this.manifestEntry = manifestEntry;
        this.cacheFile = cacheFile;
        this.destinationFile = destinationFile;
    }
    
}
