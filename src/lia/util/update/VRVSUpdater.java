package lia.util.update;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Farm.FarmMonitor;
import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;
import lia.util.Utils;
import lia.util.mail.MailFactory;
import netx.jnlp.JARDesc;
import netx.jnlp.JNLPFile;
import netx.jnlp.ResourcesDesc;
import netx.jnlp.cache.ResourceTracker;
import netx.jnlp.runtime.JNLPRuntime;
import netx.jnlp.services.ServiceUtil;

public class VRVSUpdater {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VRVSUpdater.class.getName());

    /** loads the resources */
    private final ResourceTracker tracker;

    private static String cacheDIR;

    private static String destDIR;

    private static String[] URLs;

    private static JNLPFile jnlpf;

    private File vrvsJar = null;

    private static String username = "username";

    private static String hostname = "localhost";

    private static String realFromAddress = username + "@" + hostname;

    private static String[] defaultMailAddresses = new String[] { "ramiro@roedu.net" };

    private static StringBuilder statusBuffer;

    static {
        try {
            username = System.getProperty("user.name");
        } catch (Throwable t) {
        }
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {

        }
        realFromAddress = username + "@" + hostname;
    }

    VRVSUpdater(String[] args) throws Exception {
        cacheDIR = getOption(args, "-cachedir");
        destDIR = getOption(args, "-destdir");
        init();
        tracker = new ResourceTracker(false);
    }

    private void init() throws Exception {
        try {
            if (!JNLPRuntime.isInitialized()) {
                JNLPRuntime.setBaseDir(checkForDir(cacheDIR));
                JNLPRuntime.setDebug(true);
                JNLPRuntime.setSecurityEnabled(false);
                JNLPRuntime.setHeadless(true);
                JNLPRuntime.initialize();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got Exception ", t);
            throw new Exception(t);
        }
    }

    /**
     * 
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

    private static String[] getMailAddresses() {

        boolean shouldUseVRVS_MAIL = false;

        try {
            shouldUseVRVS_MAIL = Boolean.valueOf(
                    AppConfig.getProperty("lia.Monitor.vrvs.SHOULD_USE_VRVS_MAIL", "false")).booleanValue();
        } catch (Throwable t) {
            shouldUseVRVS_MAIL = false;
        }

        if (shouldUseVRVS_MAIL) {
            final String mailaddress = AppConfig.getProperty("lia.Monitor.vrvs.MAIL", null);
            try {
                if (mailaddress != null) {
                    final String[] splittedMailAddresses = Utils.getSplittedListFields(mailaddress);
                    if ((splittedMailAddresses != null) && (splittedMailAddresses.length > 0)) {
                        return splittedMailAddresses;
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " Got Exc ", t);
                }
            }
        }
        return null;
    }

    /**
     * 
     * @param dir - local dir in FileSystem
     * @return The directory where to store temporary jar(s)
     */
    private static File checkForDir(String dir) throws Exception {
        return checkForDir(dir, true);
    }

    /**
     * 
     * @param dir - local dir in FileSystem
     * @param create - if true create the dir if not exists
     * @return The directory where to store temporary jar(s)
     */
    private static File checkForDir(String dir, boolean create) throws Exception {

        File fdir = new File(dir);
        if (!fdir.exists()) {
            if (create) {
                if (!fdir.mkdirs()) {
                    throw new Exception(" Cannot create one or more directories from the specified path:  " + dir);
                }
                logger.log(Level.CONFIG, " Dir " + dir + " was successfully created");
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
            url = new File(location).toURL();
        } else {
            url = new URL(ServiceUtil.getBasicService().getCodeBase(), location);
        }

        JNLPFile file = new JNLPFile(url, false);

        return file;
    }

    private void waitForJars() throws Exception {

        ResourcesDesc rd = jnlpf.getResources();

        JARDesc[] jars = rd.getJARs();
        URL urls[] = new URL[jars.length];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jars.length; i++) {
            sb.append("[ " + i + " ] " + jars[i].getLocation() + "\n");

            tracker.addResource(jars[i].getLocation(), jars[i].getVersion(), JNLPRuntime.getDefaultUpdatePolicy());
            urls[i] = jars[i].getLocation();
        }

        logger.log(Level.CONFIG, sb.toString());

        tracker.waitForResources(urls, 0);

    }

    private void copyFile2File(File s, File d) throws Exception {

        // Create channel on the source
        FileChannel srcChannel = new FileInputStream(s).getChannel();

        // Create channel on the destination
        FileChannel dstChannel = new FileOutputStream(d).getChannel();

        // Copy file contents from source to destination
        dstChannel.transferFrom(srcChannel, 0, srcChannel.size());

        // Close the channels
        srcChannel.close();
        dstChannel.close();
    }

    private void updateVRVSFromJar() throws Exception {
        StringBuilder sb = new StringBuilder();
        String MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME", null);
        if (MonaLisa_HOME == null) {
            throw new Exception("MonaLisa_HOME == null ");
        }
        String whereToUnjar = AppConfig.getProperty("user.home", "MonaLisa_HOME/../../");
        String cmd = "cd " + whereToUnjar + "; jar -xvf " + vrvsJar + "; tar -xvf VRVS.tar";
        Process pro = MLProcess.exec(new String[] { "/bin/sh", "-c", cmd });

        InputStream is = pro.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        String line = null;
        while ((br != null) && ((line = br.readLine()) != null)) {
            sb.append(line).append("\n");
        }

        pro.waitFor();
        logger.log(Level.INFO, "Update VRVS Reflector [ cmd: " + cmd + " ] ");
    }

    private void copyLocal() throws Exception {
        waitForJars();

        ResourcesDesc rd = jnlpf.getResources();

        JARDesc[] jars = rd.getJARs();
        for (JARDesc jar : jars) {
            File s = tracker.getCacheFile(jar.getLocation());
            //          File d = new File(checkForDir(getOption(args, "-destdir")).getAbsolutePath()  + "/" + s.getName());

            File d = new File(checkForDir(destDIR).getAbsolutePath()
                    + "/"
                    + jar.getLocation().toString()
                            .substring(jnlpf.getCodeBase().toString().length(), jar.getLocation().toString().length()));
            String dir = d.getParent();
            File fdir = new File(dir);
            if (!fdir.exists() && !fdir.mkdirs()) {
                throw new Exception(Updater.class + " Cannot create dirs to file: " + d);
            }

            copyFile2File(s, d);
            if (d != null) {
                vrvsJar = d;
            }
        }

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
        for (int i = 0; i <= st.countTokens(); i++) {
            ret[i] = st.nextToken() + "/ML.jnlp";
        }
        return ret;
    }

    private static void sendMail(String subj, String body) {

        if ((defaultMailAddresses != null) && (defaultMailAddresses.length > 0)) {
            try {
                MailFactory.getMailSender().sendMessage(realFromAddress, "mlvrvs@monalisa-chi.uslhcnet.org",
                        defaultMailAddresses, subj, body);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Got Exception", t);
                }
            }
        }

        final String[] sendAlsoTo = getMailAddresses();
        if ((sendAlsoTo != null) && (sendAlsoTo.length > 0)) {
            try {
                MailFactory.getMailSender().sendMessage(realFromAddress, "mlvrvs@monalisa-chi.uslhcnet.org",
                        sendAlsoTo, subj, body);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Got Exception", t);
                }
            }
        }

    }

    private static final void appendVRVSHOMEContentToStatusBuffer() {
        String vrvsHome = "~/VRVS";
        try {
            vrvsHome = AppConfig.getProperty("VRVS_HOME", "~/VRVS");
        } catch (Throwable t) {
            vrvsHome = "~/VRVS";
        }
        statusBuffer.append("\n Content for VRVS_HOME [ ").append(vrvsHome).append(" ]");
        BufferedReader br = null;
        try {
            Process p = MLProcess.exec(new String[] { "ls", "-ltR", vrvsHome });
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            while ((line = br.readLine()) != null) {
                statusBuffer.append(line).append("\n");
            }
            p.waitFor();
        } catch (Throwable t) {
            statusBuffer.append("\n Got exc looking for content ").append(Utils.getStackTrace(t));
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Throwable ignore) {
                }
                ;
            }
        }
        statusBuffer.append("\n END Content for VRVS_HOME [ ").append(vrvsHome).append(" ]");
    }

    public static String updateVRVS(String args[]) {
        statusBuffer = new StringBuilder(8192);

        long initialStartTime = System.currentTimeMillis();

        statusBuffer.append("Reflector update for ").append(FarmMonitor.FarmName).append(" started at localtime: ")
                .append(new Date());
        if ((getOption(args, "-jnlps") == null) || (getOption(args, "-cachedir") == null)
                || (getOption(args, "-destdir") == null)) {
            return "Error: \nUsage: " + args[0] + " -cachedir <path_to_your_cache_dir> "
                    + " -destdir <path_to_your_cache_dir>" + " -jnlps <URL_to_jnlp_file>";
        }

        URLs = getURLs(getOption(args, "-jnlps"));
        if (URLs == null) {
            return " Error: URLs cannot be null!";
        }

        try {

            //
            //CHECK FOR FIREWALL OR NETWORK PROBLEMS
            //

            boolean statusOK = false;
            for (String url : URLs) {

                InputStream is = null;
                InputStreamReader isr = null;
                BufferedReader br = null;
                URLConnection conn = null;

                try {
                    long sTime = System.currentTimeMillis();
                    statusBuffer.append("\nChecking ").append(url).append(" for firewall or network problems ... ");
                    conn = new URL(url).openConnection();
                    conn.setDefaultUseCaches(false);
                    conn.setUseCaches(false);

                    is = conn.getInputStream();
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);

                    while (br.readLine() != null) {
                        ;
                    }
                    statusBuffer.append(" OK [ ").append((System.currentTimeMillis() - sTime)).append(" ] ms");
                    statusOK = true;
                } catch (Throwable t) {
                    statusBuffer.append(" NOT OK!\nGot exc reading URL \n").append(url).append("\n The Exc: \n")
                            .append(Utils.getStackTrace(t));
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable t) {
                        }
                    }

                    if (isr != null) {
                        try {
                            isr.close();
                        } catch (Throwable t) {
                        }
                    }

                    if (br != null) {
                        try {
                            br.close();
                        } catch (Throwable t) {
                        }
                    }
                }

                if (statusOK) {
                    break;
                }
            }

            if (!statusOK) {
                logger.log(Level.WARNING, " Cannot reach none of the URLs ... maybe a firewall issue ?!?!\n"
                        + statusBuffer.toString());
                sendMail(" Reflector UPDATE [ ERROR ] @ " + FarmMonitor.FarmName, statusBuffer.toString());
                return statusBuffer.toString();
            }

            appendVRVSHOMEContentToStatusBuffer();

            VRVSUpdater updater = new VRVSUpdater(args);
            boolean error = true;
            int idx = 0;

            for (int i = 0; (i < URLs.length) && error; i++) {
                if ((URLs[i] != null) && (URLs[i].length() > 0)) {
                    try {
                        jnlpf = getJNLPFile(URLs[i]);
                        statusBuffer.append("\n Real update started @ localTime ").append(new Date());

                        statusBuffer.append("\n Waiting dor download [ ").append(URLs[i]).append(" ] to finish ... ");
                        long dsTime = System.currentTimeMillis();
                        updater.waitForJars();
                        statusBuffer.append(" [ ").append((System.currentTimeMillis() - dsTime)).append(" ] ms");

                        statusBuffer.append("\n Updating local cache ... ");
                        dsTime = System.currentTimeMillis();
                        updater.copyLocal();
                        statusBuffer.append(" [ ").append((System.currentTimeMillis() - dsTime)).append(" ] ms");

                        statusBuffer.append("\n Updating VRVS System from jar ... ");
                        dsTime = System.currentTimeMillis();
                        updater.updateVRVSFromJar();
                        statusBuffer.append(" [ ").append((System.currentTimeMillis() - dsTime)).append(" ] ms");

                        statusBuffer.append("\n Real update FINISHED @ localTime ").append(new Date());
                        error = false;
                        idx = i;
                        break;
                    } catch (Throwable t) {
                        statusBuffer.append("\nUPDATE at ").append(FarmMonitor.FarmName).append(" failed for URL: ")
                                .append(URLs[i]).append(" Exc: \n").append(Utils.getStackTrace(t));
                        logger.log(Level.WARNING, " Error got for URL " + URLs[i], t);
                    }
                }
            }//for

            appendVRVSHOMEContentToStatusBuffer();
            statusBuffer.append("\n Entire update took [ ").append((System.currentTimeMillis() - initialStartTime))
                    .append(" ] ms");
            if (error) {
                sendMail("Reflector UPDATE [ ERROR ] @ " + FarmMonitor.FarmName, statusBuffer.toString());
                return " Error updateing reflector ... Update Log: \n" + statusBuffer.toString();
            }
            sendMail("Reflector UPDATE [ SUCCESS ] @ " + FarmMonitor.FarmName, statusBuffer.toString());
            return " Update OK! ... Update Log: \n" + statusBuffer.toString();
        } catch (Throwable t) {
            appendVRVSHOMEContentToStatusBuffer();
            statusBuffer.append("\n\n Error: UPDATE FAILED! Cause: " + Utils.getStackTrace(t));
            sendMail("Reflector UPDATE [ ERROR ] @ " + FarmMonitor.FarmName, statusBuffer.toString());
            return " Error updateing reflector ... Update Log: \n" + statusBuffer.toString();
        }
    }
}
