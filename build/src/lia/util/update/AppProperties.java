/*
 * Created on Sep 29, 2010
 */
package lia.util.update;

import java.io.File;
import java.util.Properties;

/**
 * 
 *
 * @author ramiro
 */
public class AppProperties {

    public final String appName;
    public final String appVersion;
    public final String appBuildID;
    public final long timestamp;
    
    public final String digestAlgo;
    public final String manifestDigest;

    public final File cacheDestinationDir;
    public final File appDestinationDir;
    
    public final boolean stableBuild;
    
    public AppProperties(Properties p, final File cacheDestinationDir, final File appDestinationDir) {
        final String sappName = (String) p.get("appname");
        if(sappName == null || sappName.trim().length() == 0) {
            throw new IllegalArgumentException("appname not well defined in the properties");
        }
        this.appName = sappName.trim();

        final String sappVersion = (String) p.get("appversion");
        if(sappVersion == null || sappVersion.trim().length() == 0) {
            throw new IllegalArgumentException("appversion not well defined in the properties");
        }
        this.appVersion = sappVersion.trim();
        
        final String sappBuildID = (String) p.get("appbuildid");
        if(sappBuildID == null || sappBuildID.trim().length() == 0) {
            throw new IllegalArgumentException("appbuildid not well defined in the properties");
        }
        this.appBuildID = sappBuildID.trim();
        
        this.timestamp = Long.valueOf((String)p.get("timestamp"));

        final String sdigestAlgo = (String) p.get("digestalgo");
        if(sdigestAlgo == null || sdigestAlgo.trim().length() == 0) {
            throw new IllegalArgumentException("digestalgo not well defined in the properties");
        }
        this.digestAlgo = sdigestAlgo.trim();
        
        final String smanifestDigest = (String) p.get("digest");
        if(smanifestDigest == null || smanifestDigest.trim().length() == 0) {
            throw new IllegalArgumentException("digest not well defined in the properties");
        }
        this.manifestDigest = smanifestDigest.trim();
        final String appStableBuild = (String)p.get("stablebuild");
        final char firstChar = (appStableBuild == null || appStableBuild.trim().length() == 0)?'f':appStableBuild.trim().charAt(0);
        stableBuild = (firstChar == 't' || firstChar =='T' || firstChar == '1' || firstChar == 'y' || firstChar == 'Y');

        this.cacheDestinationDir = cacheDestinationDir;
        this.appDestinationDir = appDestinationDir;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(8192);
        builder.append("AppProperties [appName=")
               .append(appName)
               .append(", appVersion=")
               .append(appVersion)
               .append(", timestamp=")
               .append(timestamp)
               .append(", digestAlgo=")
               .append(digestAlgo)
               .append(", manifestDigest=")
               .append(manifestDigest)
               .append(", cacheDestinationDir=")
               .append(cacheDestinationDir)
               .append(", appDestinationDir=")
               .append(appDestinationDir)
               .append(", stableBuild=")
               .append(stableBuild)
               .append("]");
        return builder.toString();
    }
    
}
