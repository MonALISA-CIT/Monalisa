/*
 * Created on Sep 23, 2010
 */
package lia.util.update;

/**
 * Helper class for parsing Updater args param from static main(String[] args) call
 * 
 * @author ramiro
 */
public class UpdaterArgs {

    private final String jnlps;

    private final String appname;

    private final String appversion;

    private final String cacheDirPath;

    private final String destDirPath;

    private final boolean noupdatelog;

    private final boolean hasAppVersion;

    private final boolean checkTimestamp;

    private final boolean checkSize;

    private final boolean ignoreCryptoHash;
    
    private final boolean isFullUpdate;

    /**
     * @param args
     * @throws IllegalArgumentException
     *             if the following params are not set: appname, jnlps, cachedir, destdir
     */
    private UpdaterArgs(final String[] args) {
        final String tAppname = UpdaterUtils.getOption(args, "-appname", "MLService");
        if (tAppname == null || tAppname.trim().length() == 0) {
            throw new IllegalArgumentException("UpdaterArgs: appname param is not set");
        }
        this.appname = tAppname.trim();

        final String tjnlps = UpdaterUtils.getOption(args, "-jnlps");
        if (tjnlps == null || tjnlps.trim().length() == 0) {
            throw new IllegalArgumentException("UpdaterArgs: jnlps param is not set");
        }
        this.jnlps = tjnlps.trim();

        final String tappversion = UpdaterUtils.getOption(args, "-useVersion");
        appversion = (tappversion == null || tappversion.trim().length() == 0) ? "" : tappversion.trim();
        hasAppVersion = appversion.length() > 0;

        final String tcacheDir = UpdaterUtils.getOption(args, "-cachedir");
        if (tcacheDir == null || tcacheDir.trim().length() == 0) {
            throw new IllegalArgumentException("UpdaterArgs: cachedir param is not set");
        }
        cacheDirPath = tcacheDir.trim();

        final String tdestDir = UpdaterUtils.getOption(args, "-destdir");
        if (tdestDir == null || tdestDir.trim().length() == 0) {
            throw new IllegalArgumentException("UpdaterArgs: destdir param is not set");
        }
        this.destDirPath = tdestDir.trim();

        noupdatelog = UpdaterUtils.getOption(args, "-noupdatelog") != null;
        checkTimestamp = UpdaterUtils.getOption(args, "-checkTimestamp") != null;
        checkSize = UpdaterUtils.getOption(args, "-checkSize") != null;
        ignoreCryptoHash = UpdaterUtils.getOption(args, "-ignoreCryptoHash") != null;
        isFullUpdate = UpdaterUtils.getOption(args, "-fullUpdate") != null;
    }

    public static UpdaterArgs newInstance(String[] args) {
        return new UpdaterArgs(args);
    }

    public String getJnlps() {
        return jnlps;
    }

    public String getAppName() {
        return appname;
    }

    public String getAppVersion() {
        return appversion;
    }

    /**
     * @return getAppName() if not version defined, or getAppName() + "_" + getAppVersion() otherwise
     */
    public String getFullAppName() {
        return appname + ((!hasAppVersion) ? "" : "_" + appversion);
    }

    public boolean hasAppVersion() {
        return hasAppVersion;
    }

    public String cacheDirPath() {
        return cacheDirPath;
    }

    public String destDirPath() {
        return destDirPath;
    }

    public boolean isNoupdatelog() {
        return noupdatelog;
    }

    public boolean checkTimestamp() {
        return checkTimestamp;
    }

    public boolean checkSize() {
        return checkSize;
    }

    public boolean ignoreCryptoHash() {
        return ignoreCryptoHash;
    }

    public boolean isFullUpdateSet() {
        return isFullUpdate;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("UpdaterArgs [jnlps=")
               .append(jnlps)
               .append(", appname=")
               .append(appname)
               .append(", appversion=")
               .append(appversion)
               .append(", cacheDirPath=")
               .append(cacheDirPath)
               .append(", destDirPath=")
               .append(destDirPath)
               .append(", noupdatelog=")
               .append(noupdatelog)
               .append("]");
        return builder.toString();
    }

}
