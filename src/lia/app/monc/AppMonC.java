package lia.app.monc;

import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.app.AppUtils;
import lia.util.Utils;
import lia.web.utils.Formatare;

/**
 * @author costing
 * @since forever
 */
public class AppMonC implements lia.app.AppInt {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppMonC.class.getName());

    /**
     * Configuration file
     */
    String sFile = null;

    /**
     * Configuration options
     */
    Properties prop = new Properties();

    /**
     * Configuration options description
     */
    public static final String sConfigOptions = "########### Required parameters : ########\n"
            + "#bash=/path/to/bash (default is /bin/bash)\n" + "##########################################\n\n";

    /**
     * Path to bash
     */
    String sBash = "/bin/bash";

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public boolean restart() {
        return true;
    }

    @Override
    public int status() {
        String s = exec(sBash + " --version");

        if ((s == null) || (s.length() <= 0)) {
            return AppUtils.APP_STATUS_STOPPED;
        }

        s = s.replace('\n', ' ');
        s = s.replace('\r', ' ');

        if (s.matches("^GNU bash, version.*$")) {
            return AppUtils.APP_STATUS_RUNNING;
        }
        return AppUtils.APP_STATUS_UNKNOWN;
    }

    @Override
    public String info() {
        // xml with the version & stuff
        StringBuilder sb = new StringBuilder();
        sb.append("<config app=\"Bash\">\n");
        sb.append("<file name=\"info\">\n");

        try {
            String s = exec(sBash + " --version");
            if (s.indexOf("version") > 0) {
                s = s.substring(s.indexOf("version") + "version".length()).trim();
                s = s.substring(0, s.indexOf(" ")).trim();

                sb.append("<key name=\"version\" value=\"" + AppUtils.enc(s)
                        + "\" line=\"1\" read=\"true\" write=\"false\"/>\n");
            }
        } catch (Exception e) {
            // ignore
        }

        sb.append("</file>");
        sb.append("</config>");

        return sb.toString();
    }

    @Override
    public String exec(final String sCmd) {

        Throwable exc = null;
        try {
            final String s = Formatare.replace(sCmd, "\"", "\\\"");

            return AppUtils.getOutput(new String[] { sBash, "-c", s });
        } catch (Throwable t) {
            exc = t;
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Got exception in AppMonC", t);
            }
        }

        return "Got exception. Cause:\n" + Utils.getStackTrace(exc);
    }

    /**
     * @param sUpdate
     */
    @Override
    public boolean update(String sUpdate) {
        return true;
    }

    /**
     * @param sUpdate
     */
    @Override
    public boolean update(String sUpdate[]) {
        return true;
    }

    @Override
    public String getConfiguration() {
        StringBuilder sb = new StringBuilder();

        sb.append(sConfigOptions);

        Enumeration<?> e = prop.propertyNames();

        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();

            sb.append(s + "=" + prop.getProperty(s) + "\n");
        }

        return sb.toString();
    }

    @Override
    public boolean updateConfiguration(String s) {
        return AppUtils.updateConfig(sFile, s) && init(sFile);
    }

    @Override
    public boolean init(String sPropFile) {
        sFile = sPropFile;
        AppUtils.getConfig(prop, sFile);

        if ((prop.getProperty("bash") != null) && (prop.getProperty("bash").length() > 0)) {
            sBash = prop.getProperty("bash");
        }
        return true;
    }

    @Override
    public String getName() {
        return "lia.app.monc.AppMonC";
    }

    @Override
    public String getConfigFile() {
        return sFile;
    }

}
