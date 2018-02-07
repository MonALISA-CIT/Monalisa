/*
 * Created on Sep 24, 2010
 */
package lia.util.update.downloader;

import java.io.File;
import java.net.URL;

/**
 * 
 * @author ramiro
 */
public class RemoteResource {

    public final URL url;
    public final File destinationFile;
    public final String digestAlgorithm;
    
    public RemoteResource(URL url, File destinationFile) {
        this(url, destinationFile, null);
    }
    
    public RemoteResource(URL url, File destinationFile, String digestAlgorithm) {
        this.url = url;
        this.destinationFile = destinationFile;
        this.digestAlgorithm = digestAlgorithm;
    }

    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(128);
        builder.append("RemoteResource [url=").append(url).append(", destinationFile=").append(destinationFile).append(", digestAlgorithm=").append(digestAlgorithm).append("]");
        return builder.toString();
    }
    
}
