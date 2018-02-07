/*
 * Created on Sep 20, 2010
 */
package lia.util.update.old;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import netx.jnlp.Version;

/**
 * A stripped-down version of AppConfig. Just for Java4 compatibility.</br>
 * Does not depend on any lia.* packages or Java5+.
 * 
 * @since ML 1.9.0
 * @author ramiro
 */
public class UpdaterConfig {

    private static final Properties mlConfigPoperties = new Properties();

    private static final String JAVA_VERSION;

    static {
        String javaVersion = "1.4";

        try {
            String tmpCfgURL = null;
            boolean bErr = false;
            try {
                tmpCfgURL = System.getProperty("lia.Monitor.ConfigURL");
            } catch (Throwable t) {
                System.err.println(" [ UpdaterConfig ] Unable to determine lia.Monitor.ConfigURL system property. Cause: " + t.getMessage());
                bErr = true;
            }

            if(!bErr) {
                try {
                    javaVersion = getProperty("java.version");
                } catch (Throwable ignore) {
                    javaVersion = "1.4";
                }

                InputStream is = null;
                BufferedInputStream bis = null;
                try {
                    is = new URL(tmpCfgURL).openStream();
                    bis = new BufferedInputStream(is);
                    mlConfigPoperties.load(bis);
                } catch (Throwable t) {
                    System.err.println(" [ UpdaterConfig ] unable to load config from lia.Monitor.ConfigURL: " + tmpCfgURL + ". Cause: " + t.getMessage());
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable ignore) {
                        }
                    }

                    if (bis != null) {
                        try {
                            is.close();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        } finally {
            JAVA_VERSION = javaVersion;
        }
    }// end static code

    public static final String getProperty(String key) {
        return getProperty(key, null);
    }

    public static final String getProperty(String key, String defaultValue) {

        String rv = System.getProperty(key);
        if (rv == null) {
            rv = mlConfigPoperties.getProperty(key, defaultValue);
        }

        return (rv != null) ? rv.trim() : defaultValue;
    }

    public static final String getJavaVersion() {
        return JAVA_VERSION;
    }

    /**
     * 
     * @param desiredVersion
     * @return
     */
    public static boolean checkJavaVersion(final String desiredVersion) {
        final Version javaVersion = new Version(getJavaVersion());
        final Version patternVersion = new Version(desiredVersion);
        return patternVersion.matches(javaVersion);
    }

    public static boolean getb(final String sParam, final boolean bDefault) {
        String s = getProperty(sParam);

        if (s != null && s.length() > 0) {
            final char c = s.charAt(0);

            if (c == 't' || c == 'T' || c == 'y' || c == 'Y' || c == '1')
                return true;

            if (c == 'f' || c == 'F' || c == 'n' || c == 'N' || c == '0')
                return false;
        }

        return bDefault;
    }

    public static final String getGlobalEnvProperty(String key) {
        return getGlobalEnvProperty(key, null);
    }

    public static final String getGlobalEnvProperty(String key, String defaultValue) {
        final String envVar = System.getenv(key);
        return (envVar == null) ? defaultValue : envVar.trim();
    }

    public static final void main(String[] args) {
        final String[] versionsToCheck = {
                "1.6+", "1.4+"
        };
        final String jVersion = getJavaVersion();
        for (int i = 0; i < versionsToCheck.length; i++) {
            final String pattern = versionsToCheck[i];
            System.out.println("Version: " + jVersion + " pattern: " + pattern + " matches: " + UpdaterConfig.checkJavaVersion(pattern));
        }

    }
}
