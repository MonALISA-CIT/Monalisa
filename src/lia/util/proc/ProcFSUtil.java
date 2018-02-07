/*
 * $Id: ProcFSUtil.java 6921 2010-10-29 09:34:33Z costing $
 */
package lia.util.proc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * 
 * Various utility functions for /proc file system on Linux
 * 
 * @author ramiro
 */
public class ProcFSUtil {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(ProcFSUtil.class.getName());

    private static final String PROC_UPTIME_FILE = "/proc/uptime";

    private static final String RO_FLAG = "r";

    private static RandomAccessFile fr = null;

    private static final Object PROC_UPTIME_FILE_LOCK = new Object();

    static final char SPACE_CHAR = ' ';

    private static final char DOT_CHAR = '.';

    static final char ONE_CHAR = '1';
    static final char ZERO_CHAR = '0';

    static final char NINE_CHAR = '9';

    public static final int JIFFIES_IN_A_SECOND;

    static final String PROC_FILE_NAME = "/proc/";
    static final String STAT_FILE_NAME = "/stat";
    static final String STATUS_FILE_NAME = "/status";
    
    private static final File PROC_FILE = new File(PROC_FILE_NAME);

    // determine the arch ... on IA64
    static {

        int jifInS = 100;
        try {
            String osArch = System.getProperty("os.arch");
            if (osArch == null) {
                jifInS = 100;
            } else {
                if (osArch.equalsIgnoreCase("ia64") || osArch.equalsIgnoreCase("alpha")) {
                    jifInS = 1024;
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ ProcFSUtil ] Got exception trying to determine arch jiffies", t);
            }
            jifInS = 100;
        }

        JIFFIES_IN_A_SECOND = jifInS;
        logger.log(Level.INFO, " [ ProcFSUtil ] Jiffies in a second: " + JIFFIES_IN_A_SECOND);
    }

    private static final void initProcUptimeFile() throws Exception {

        synchronized (PROC_UPTIME_FILE_LOCK) {
            if (fr == null || !fr.getFD().valid()) {
                fr = new RandomAccessFile(PROC_UPTIME_FILE, RO_FLAG);
            }
        }
    }

    private static final FilenameFilter PROC_PID_FILTER = new FilenameFilter() {

        public boolean accept(File file, String name) {
            final char fc1 = name.charAt(0);

            // TODO
            // both File.isDirectory() and File.list() seem slow ... doing some
            // Gugl stuff
            // some suggest that JNDI implementation for FS can be faster,
            // but ... hopefully small speed up will be to check for the file
            // name first
            if (fc1 >= ONE_CHAR && fc1 <= NINE_CHAR && file.isDirectory()) { return true; }

            // Supid HACK for /proc/.pid - stupid SLC kernel ... or should I
            // blame RedHat ?!
            char fc2 = SPACE_CHAR;
            if (name.length() > 1) {
                fc2 = name.charAt(1);
            }
            if (fc1 == DOT_CHAR && fc2 >= ONE_CHAR && fc2 < NINE_CHAR && file.isDirectory()) { return true; }

            return false;
        }
    };

    public static final String[] getProcPIDs() {
        return PROC_FILE.list(PROC_PID_FILTER);
    }

    public static final String[] getPIDStatFile(final String PID) throws Exception {
        return getPIDStatFile(PID, new byte[2048]);
    }

    private static final String[] getPIDStatFile(final String PID, final byte[] buff) throws Exception {
//        RandomAccessFile r = null;
        RandomAccessFile r = null;
        try {
//            r = new RandomAccessFile(new StringBuilder(20).append(PROC_FILE_NAME).append(PID).append(STAT_FILE_NAME).toString(), RO_FLAG);
            r = new RandomAccessFile(new File(new StringBuilder(20).append(PROC_FILE_NAME).append(PID).append(STAT_FILE_NAME).toString()), RO_FLAG);
            int l = r.read(buff);
            if (l > 2048) {// smth is wrong with the /proc FS !!
                throw new Exception("/proc/" + PID + "/stat is too big " + l + " ! The max is 2048 ...");
            }

            final StringBuilder sb = new StringBuilder(l);
            ArrayList<String> al = new ArrayList<String>();
            for (int i = 0; i < l; i++) {
                char c = (char) buff[i];
                if (c != SPACE_CHAR) {
                    sb.append(c);
                } else {
                    al.add(sb.toString());
                    sb.setLength(0);
                }
            }// for
            al.add(sb.toString());
            return al.toArray(new String[al.size()]);
        } catch (Exception e) {
            throw e;
        } finally {
            try {
            	if (r!=null)
            		r.close();
            } catch (Throwable t1) {
            	// ignore
            }
        }
    }

    public static final String[] getPIDStatFile(int PID) throws Exception {
        return getPIDStatFile(String.valueOf(PID));
    }

    public static final OSProccessStatWrapper[] getCurrentProcs() throws Exception {
        Map<Integer, OSProccessStatWrapper> hm = getCurrentProcsHash();
        return hm.values().toArray(new OSProccessStatWrapper[hm.size()]);
    }

    /**
     * Returns the local system uptime based on information from /proc/uptime
     * 
     * @return The system uptime in jiffies since the reboot
     * @throws Exception
     */
    public static final long getSystemUptime() throws Exception {
        initProcUptimeFile();

        byte[] buff_proc_uptime = new byte[100];

        double jiffiesSinceBoot = 0;
        int l = 0;

        synchronized (PROC_UPTIME_FILE_LOCK) {
            fr.seek(0);
            l = fr.read(buff_proc_uptime);
        }

        StringBuilder sb_proc_uptime = new StringBuilder(l);

        for (int i = 0; i < l; i++) {
            char c = (char) buff_proc_uptime[i];
            if (c != SPACE_CHAR) {
                sb_proc_uptime.append(c);
            } else {
                break;
            }
        }

        jiffiesSinceBoot = Double.parseDouble(sb_proc_uptime.toString()) * JIFFIES_IN_A_SECOND;

        return (long) jiffiesSinceBoot;
    }

    public static final Map<Integer, OSProccessStatWrapper> getCurrentProcsHash() throws Exception {
        final long sTime = System.currentTimeMillis();

        final String PIDS[] = getProcPIDs();

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINE, " [ ProcFSUtil ] getProcPIDs took: " + (System.currentTimeMillis() - sTime) + " ms");
        }
        if (PIDS == null || PIDS.length == 0) 
        	return null;
        
        Map<Integer, OSProccessStatWrapper> hm = new TreeMap<Integer, OSProccessStatWrapper>();

        final byte[] buff = new byte[2048];
        for (int i = 0; i < PIDS.length; i++) {
            try {
                OSProccessStatWrapper ospw = new OSProccessStatWrapper(getPIDStatFile(PIDS[i], buff));
                hm.put(Integer.valueOf(ospw.pid), ospw);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, " [ ProcFSUtil ] Got exception for PID [ " + PIDS[i] + " ]", t);
                }
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ ProcFSUtil ] getCurrentProccessesStat() Returning: \n" + hm + "\n");
        }

        return hm;
    }

    public static final void main(String[] argd) throws Exception {
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers=java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));
        
        logger.setLevel(Level.FINER);
        
        StringBuilder sb = new StringBuilder(2048);
        for (;;) {
            sb.setLength(0);
            final long sTime = System.currentTimeMillis();
            final Map<Integer, OSProccessStatWrapper> procMap = getCurrentProcsHash();
            final long fTime = System.currentTimeMillis();
            sb.append("\n Date: ").append(new Date()).append("Current /proc PIDs [ ").append(procMap.size()).append(" ] : total parsing Dt= ").append(fTime - sTime).append(" ms\n");
            sb.append(procMap);
            sb.append("\n Date: ").append(new Date()).append("FINISHED Current /proc PIDs: \n");
            System.out.println(sb.toString());
            Thread.sleep(2 * 1000);
        }
    }

}
