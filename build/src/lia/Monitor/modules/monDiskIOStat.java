/*
 * Created on May 9, 2010
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * Disk stats for Linux and Solaris. For Linux the
 * 
 * @author ramiro
 */
public class monDiskIOStat extends AbstractSchJobMonitoring implements Observer {

    /**
     * 
     */
    private static final long serialVersionUID = 8079426069042234980L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monDiskIOStat.class.getName());

    /**
     * Proc entry where the disk IO statistics are published
     */
    public static final String LINUX_PROC_DISK_STATS = AppConfig
            .getProperty("LINUX_PROC_DISK_STATS", "/proc/diskstats");

    /**
     * Sysfs virtual entry for block devices
     */
    public static final String LINUX_SYSFS_BLOCK = AppConfig.getProperty("BLOCK_SYSFS_MOUNT_POINT", "/sys/block");

    /**
     * Default block size, in bytes
     */
    public static final long DEFAULT_SECTOR_SIZE = AppConfig.getl("DEFAULT_SECTOR_SIZE", 512L);

    private static final String OS_NAME = System.getProperty("os.name");

    private static final String OS_VER = System.getProperty("os.version");

    private static final String OS_ARCH = System.getProperty("os.arch");

    private final AtomicReference<Config> configReference = new AtomicReference<Config>();

    private final Map<DevKey, BlockDevice> devicesMap = new HashMap<DevKey, BlockDevice>();

    private File configFile;

    /**
     * Nanoseconds in a second
     */
    static final long NANOS_IN_A_SECOND = TimeUnit.SECONDS.toNanos(1);

    private static final class DevKey implements Comparable<DevKey> {

        private static final Map<Long, DevKey> devCache = new HashMap<Long, DevKey>();

        private final int major;

        private final int minor;

        final String name;

        DevKey(final int major, final int minor, final String name) {
            this.major = major;
            this.minor = minor;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return (17 * major) ^ (33 * minor);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof DevKey) {
                final DevKey oKey = (DevKey) obj;
                return ((this.major == oKey.major) && (this.minor == oKey.minor));
            }
            return false;
        }

        @Override
        public int compareTo(DevKey o) {
            if (this == o) {
                return 0;
            }
            final int majDiff = major - o.major;
            return (majDiff != 0) ? majDiff : (minor - o.minor);
        }

        @Override
        public String toString() {
            return "DevKey [major=" + major + ", minor=" + minor + ", name=" + name + ", hash=" + hashCode() + "]";
        }

        public static DevKey newInstance(int major, int minor, String devPartName) {
            final Long key = Long.valueOf(((long) major << 32) ^ minor);
            DevKey rVal = devCache.get(key);
            if (rVal == null) {
                rVal = new DevKey(major, minor, devPartName);
                devCache.put(key, rVal);
            }
            return rVal;
        }

    }

    /**
     * @author ramiro
     */
    private static final class RawIOStats {

        final long nanoTimeStamp;

        /**
         * Field 1 -- # of reads completed This is the total number of reads completed successfully.
         */
        final BigInteger readReqs;

        /**
         * Field 2 -- # of reads merged. Reads and writes which are adjacent to each other may be merged for efficiency.
         * Thus two 4K reads may become one 8K read before it is ultimately handed to the disk, and so it will be
         * counted (and queued) as only one I/O. This field lets you know how often this was done.
         */
        final BigInteger mergedReadReq;

        /**
         * Field 3 -- # of sectors read This is the total number of sectors read successfully.
         */
        final BigInteger readSectors;

        /**
         * Field 4 -- # of milliseconds spent reading This is the total number of milliseconds spent by all reads (as
         * measured from __make_request() to end_that_request_last()).
         */
        final BigInteger millisRead;

        /**
         * Field 5 -- # of writes completed This is the total number of writes completed successfully.
         */
        final BigInteger writeReqs;

        /**
         * Field 6 -- # of writes merged. Same as {@link #mergedReadReq}
         */
        final BigInteger mergedWriteReq;

        /**
         * Field 7 -- # of sectors written This is the total number of sectors written successfully.
         */
        final BigInteger writeSectors;

        /**
         * Field 8 -- # of milliseconds spent writing This is the total number of milliseconds spent by all writes (as
         * measured from __make_request() to end_that_request_last()).
         */
        final BigInteger millisWrite;

        /**
         * Field 9 -- # of I/Os currently in progress The only field that should go to zero. Incremented as requests are
         * given to appropriate struct request_queue and decremented as they finish.
         */
        // highly unlikely to have so many concurrent I/Os in progress
        final long concurrentIO;

        /**
         * * Field 10 -- # of milliseconds spent doing I/Os This field is increases so long as field 9 is nonzero.
         */
        final BigInteger millisIO;

        /**
         * Field 11 -- weighted # of milliseconds spent doing I/Os This field is incremented at each I/O start, I/O
         * completion, I/O merge, or read of these stats by the number of I/Os in progress (field 9) times the number of
         * milliseconds spent doing I/O since the last update of this field. This can provide an easy measure of both
         * I/O completion time and the backlog that may be accumulating.
         */
        final BigInteger weightedIO;

        RawIOStats(final BigInteger readReqs, final BigInteger mergedReadReq, final BigInteger readSectors,
                final BigInteger millisRead, final BigInteger writeReqs, final BigInteger mergedWriteReq,
                final BigInteger writeSectors, final BigInteger millisWrite, final long concurrentIO,
                final BigInteger millisIO, final BigInteger weightedIO) {
            nanoTimeStamp = Utils.nanoNow();
            this.readReqs = readReqs;
            this.mergedReadReq = mergedReadReq;
            this.readSectors = readSectors;
            this.millisRead = millisRead;
            this.writeReqs = writeReqs;
            this.mergedWriteReq = mergedWriteReq;
            this.writeSectors = writeSectors;
            this.millisWrite = millisWrite;
            this.concurrentIO = concurrentIO;
            this.millisIO = millisIO;
            this.weightedIO = weightedIO;
        }

        @Override
        public String toString() {
            return "RawIOStats [nanoTimeStamp=" + nanoTimeStamp + ", readReqs=" + readReqs + ", mergedReadReq="
                    + mergedReadReq + ", readSectors=" + readSectors + ", millisRead=" + millisRead + ", writeReqs="
                    + writeReqs + ", mergedWriteReq=" + mergedWriteReq + ", writeSectors=" + writeSectors
                    + ", millisWrite=" + millisWrite + ", concurrentIO=" + concurrentIO + ", millisIO=" + millisIO
                    + ", weightedIO=" + weightedIO + "]";
        }

    }

    private static final class DerivedIOStats {

        /**
         * number of read sectors per second
         */
        final double readSpeed;

        /**
         * number of write sectors per second
         */
        final double writeSpeed;

        /**
         * bandwidth utilization for the device in percentage.
         */
        final double utilization;

        /**
         * read IOPS
         */
        final double readIOPS;

        /**
         * merged read IOPS
         */
        final double mergedReadIOPS;

        /**
         * write IOPS
         */
        final double writeIOPS;

        /**
         * merged write IOPS
         */
        final double mergedWriteIOPS;

        /**
         * @param oldStats
         * @param newStats
         * @param sectorSize 
         * @throws IllegalArgumentException
         *             if we go back in time.
         */
        DerivedIOStats(final RawIOStats oldStats, final RawIOStats newStats, final long sectorSize) {
            final long dtNanos = newStats.nanoTimeStamp - oldStats.nanoTimeStamp;
            if (dtNanos <= 0) {
                throw new IllegalArgumentException(
                        "[ monDiskIOStat ] [ DerivedIOStats ] time is going back(issue with nano time?) or too fast polling"
                                + dtNanos);
            }

            readSpeed = computeSpeed(oldStats.readSectors, newStats.readSectors, dtNanos, NANOS_IN_A_SECOND);
            writeSpeed = computeSpeed(oldStats.writeSectors, newStats.writeSectors, dtNanos, NANOS_IN_A_SECOND);

            readIOPS = computeSpeed(oldStats.readReqs, newStats.readReqs, dtNanos, NANOS_IN_A_SECOND);
            mergedReadIOPS = computeSpeed(oldStats.mergedReadReq, newStats.mergedReadReq, dtNanos, NANOS_IN_A_SECOND);

            writeIOPS = computeSpeed(oldStats.writeReqs, newStats.writeReqs, dtNanos, NANOS_IN_A_SECOND);
            mergedWriteIOPS = computeSpeed(oldStats.mergedWriteReq, newStats.mergedWriteReq, dtNanos, NANOS_IN_A_SECOND);

            utilization = (newStats.millisIO.subtract(oldStats.millisIO).longValue() * 100d)
                    / TimeUnit.NANOSECONDS.toMillis(newStats.nanoTimeStamp - oldStats.nanoTimeStamp);
        }

        /**
         * @param oldValue
         * @param newValue
         * @param delay
         *            - in nano seconds
         * @return
         */
        private static final double computeSpeed(final BigInteger oldValue, final BigInteger newValue,
                final long delay, final double factor) {
            final double diff = newValue.subtract(oldValue).doubleValue();
            return (diff * factor) / delay;
        }

        @Override
        public String toString() {
            return "DerivedIOStats [readSpeed=" + readSpeed + " sect/s, writeSpeed=" + writeSpeed
                    + " sect/s, utilization=" + utilization + "]";
        }

    }

    // used by isDevice()
    private static final class DevNameCache {

        private static final Set<DevKey> devCache = new TreeSet<DevKey>();

        private static final Set<DevKey> noDevCache = new TreeSet<DevKey>();

        static final boolean isDevice(DevKey devKey) {
            if (devCache.contains(devKey)) {
                return true;
            }

            if (noDevCache.contains(devKey)) {
                return false;
            }
            // not in cache
            boolean isDevice = false;
            if (devKey.name.indexOf("/") >= 0) {
                final String sysBName = LINUX_SYSFS_BLOCK + "/" + devKey.name.replace("/", "!");
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ monDiskIOStat ] Strange devName: " + devKey + " will check for: "
                            + sysBName);
                }
                isDevice = (new File(sysBName).exists());
            } else {
                isDevice = (new File(LINUX_SYSFS_BLOCK + "/" + devKey.name).exists());
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, " Adding device: " + devKey.name + " to DevNameCache: " + isDevice);
            }

            if (isDevice) {
                devCache.add(devKey);
            } else {
                noDevCache.add(devKey);
            }

            return isDevice;
        }

        final static void clearCache() {
            noDevCache.clear();
            devCache.clear();
            logger.log(Level.INFO, " DevNameCache cleared");
        }
    }

    private static final class BlockDevice {

        final DevKey deviceKey;

        RawIOStats rawIOStats;

        final long sectorSize;

        BlockDevice(DevKey deviceKey, RawIOStats rawIOStats) {
            this.deviceKey = deviceKey;
            this.rawIOStats = rawIOStats;
            this.sectorSize = DEFAULT_SECTOR_SIZE;
        }

        DerivedIOStats updateIOStats(RawIOStats newIOStats) {
            try {
                return (this.rawIOStats != null) ? new DerivedIOStats(this.rawIOStats, newIOStats, sectorSize) : null;
            } finally {
                this.rawIOStats = newIOStats;
            }
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("BlockDevice [deviceKey=").append(deviceKey).append(", rawIOStats=").append(rawIOStats)
                    .append(", sectorSize=").append(sectorSize).append("]");
            return builder.toString();
        }

    }

    private static final class Config {

        final Pattern[] allowedDevices;

        final Pattern[] deniedDevices;

        private final boolean hasAllowed;

        private final boolean hasDenied;

        final Set<DevKey> allowedDevicesCache = new HashSet<DevKey>();

        final Set<DevKey> deniedDevicesCache = new HashSet<DevKey>();

        /**
         * When no configuration file is specified 
         */
        Config() {
            hasAllowed = false;
            hasDenied = true;

            allowedDevices = null;
            deniedDevices = fromProperty("dm-\\d+;md\\d+");

            return;
        }

        /**
         * @param configFile 
         * @throws IOException
         *             in case of I/O errors
         * @throws PatternSyntaxException
         */
        Config(final File configFile) throws IOException {
            FileInputStream fis = null;
            Pattern[] tAllowedDevices = null;
            Pattern[] tDeniedDevices = null;
            try {
                // it's no need for a BufferedReader; Properties is using a LineReader which buffers the input
                fis = new FileInputStream(configFile);
                final Properties p = new Properties();
                p.load(fis);
                tAllowedDevices = fromProperty(p.getProperty("allowedDevices"));
                tDeniedDevices = fromProperty(p.getProperty("deniedDevices", "dm-\\d+;md\\d+"));
            } finally {
                hasAllowed = ((tAllowedDevices != null) && (tAllowedDevices.length > 0));
                hasDenied = ((tDeniedDevices != null) && (tDeniedDevices.length > 0));
                allowedDevices = tAllowedDevices;
                deniedDevices = tDeniedDevices;
                Utils.closeIgnoringException(fis);
            }
        }

        private static Pattern[] fromProperty(final String property) {
            if (property == null) {
                return null;
            }

            final String[] propPatternsSplit = property.split("(\\s)*;(\\s)*");
            if ((propPatternsSplit == null) || (propPatternsSplit.length <= 0)) {
                return null;
            }

            final List<Pattern> pList = new ArrayList<Pattern>(propPatternsSplit.length);
            for (final String s : propPatternsSplit) {
                try {
                    pList.add(Pattern.compile(s));
                } catch (PatternSyntaxException pse) {
                    logger.log(Level.WARNING, "Pattern syntax error for `" + s + "`: " + pse);
                }
            }

            return pList.toArray(new Pattern[0]);
        }

        private static final boolean match(final String searchString, final Pattern[] searchPatterns) {
            for (final Pattern p : searchPatterns) {
                if (p.matcher(searchString).matches()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks against {@code allowedDevices} and {@code deniedDevices}
         * @param deviceKey 
         * @return true if allowed
         */
        boolean isAllowed(DevKey deviceKey) {
            if (hasAllowed) {
                if (allowedDevicesCache.contains(deviceKey)) {
                    return true;
                }
                if (match(deviceKey.name, allowedDevices)) {
                    allowedDevicesCache.add(deviceKey);
                    return false;
                }
            }

            if (hasDenied) {
                if (deniedDevicesCache.contains(deviceKey)) {
                    return false;
                }

                if (match(deviceKey.name, deniedDevices)) {
                    deniedDevicesCache.add(deviceKey);
                    return false;
                }
            }

            allowedDevicesCache.add(deviceKey);
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Config [allowedDevices=").append(Arrays.toString(allowedDevices))
                    .append(", deniedDevices=").append(Arrays.toString(deniedDevices)).append(", hasAllowed=")
                    .append(hasAllowed).append(", hasDenied=").append(hasDenied).append(", allowedDevicesCache=")
                    .append(allowedDevicesCache).append(", deniedDevicesCache=").append(deniedDevicesCache).append("]");
            return builder.toString();
        }

    }

    @Override
    public MonModuleInfo initArgs(final String argStr) {
        if (argStr != null) {
            final String[] args = Utils.getSplittedListFields(argStr);
            for (String arg : args) {
                if (arg.indexOf("=") >= 0) {
                    final String[] sArgs = arg.split("(\\s)*=(\\s)*");
                    if ((sArgs[0].compareToIgnoreCase("ConfFile") == 0)
                            || (sArgs[0].compareToIgnoreCase("ConfigFile") == 0)) {
                        configFile = new File(sArgs[1].trim());
                    }
                }
            }

            if (configFile == null) {
                final String appConfConfigFile = AppConfig.getProperty("monDiskIOStat.configFile", null);
                if (appConfConfigFile != null) {
                    configFile = new File(appConfConfigFile);
                }
            }

            if (configFile != null) {
                DateFileWatchdog dfw = null;
                try {
                    dfw = DateFileWatchdog.getInstance(configFile, 5 * 1000);
                    dfw.addObserver(this);
                    configReference.set(new Config(configFile));
                    logger.log(Level.INFO, "[ monDiskIOStat ] loading conf: " + configReference.get() + " from: "
                            + configFile);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[ monDiskIOStat ]  Unable to parse or load the config. Cause: ", t);
                    if (dfw != null) {
                        logger.log(Level.INFO, "[ monDiskIOStat ] The file: " + configFile
                                + " will not be monitored for future changes", t);
                        dfw.stopIt();
                    }
                    throw new InstantiationError(t.getMessage());
                }
            } else {
                configReference.set(new Config());
                logger.log(Level.INFO, "[ monDiskIOStat ] loading default conf: " + configReference.get());
            }
        } else {
            configReference.set(new Config());
            logger.log(Level.INFO, "[ monDiskIOStat ] loading default conf: " + configReference.get());
        }
        return new MonModuleInfo();
    }

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return "monDiskIOStat";
    }

    /**
     * Computes the IO params based on /proc/diskstat and sysfs (by default assumes it's mounted in /sys). For more
     * details about diskstats file check the Linux kernel <a
     * href="http://lxr.linux.no/linux/Documentation/iostats.txt"><i>Documentation/iostats.txt</i></a>
     */
    private final Object processLinuxProcFS(Config config) throws IOException {
        final boolean isFINEST = logger.isLoggable(Level.FINEST);
        final boolean isFINER = isFINEST || logger.isLoggable(Level.FINER);
        final boolean isFINE = isFINER || logger.isLoggable(Level.FINE);

        final long rTime = NTPDate.currentTimeMillis();

        FileReader fr = null;
        BufferedReader br = null;
        final List<Object> lRet = new LinkedList<Object>();
        try {
            fr = new FileReader(LINUX_PROC_DISK_STATS);
            br = new BufferedReader(fr, 2048);
            int lineNo = 0;
            double totalReadSpeed = 0;
            double totalReadIOPS = 0;
            double totalMergedReadIOPS = 0;
            double totalWriteIOPS = 0;
            double totalMergedWriteIOPS = 0;

            double totalWriteSpeed = 0;
            double totalIOUtil = 0;
            int totalDevices = 0;

            double maxIOUsage = -1;
            double maxIOUsageRead = -1;
            double maxIOUsageWrite = -1;
            String maxIOUsageDevice = null;
            double maxIOUsageReadIOPS = -1;
            double maxIOUsageReadIOPSMerged = -1;
            double maxIOUsageWriteIOPS = -1;
            double maxIOUsageWriteIOPSMerged = -1;

            final boolean sumIOPSPerDevice = AppConfig.getb("lia.Monitor.modules.monDiskIOStat.sum_iops_per_device",
                    false);
            final boolean sumTrafficPerDevice = AppConfig.getb(
                    "lia.Monitor.modules.monDiskIOStat.sum_traffic_per_device", false);
            if (isFINEST) {
                if (config == null) {
                    logger.log(Level.FINEST, "[ monDiskIOStat ] The conf is null ... ");
                } else {
                    logger.log(Level.FINEST, "[ monDiskIOStat ] The conf is ... \n" + config);
                }
            }

            for (;;) {
                final String line = br.readLine();
                if (line == null) {
                    break;
                }
                lineNo++;
                try {
                    // final Scanner sc = new Scanner(line);
                    StringTokenizer st = new StringTokenizer(line);
                    int iTmp;
                    // device major number
                    try {
                        iTmp = Integer.parseInt(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "[ monDiskIOStat ] Major number not found. Ignoring line [ " + lineNo
                                + " ]:\n" + line);
                        continue;
                    }
                    final int major = iTmp;

                    // device minor number
                    try {
                        iTmp = Integer.parseInt(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "[ monDiskIOStat ] Minor number not found. Ignoring line [ " + lineNo
                                + " ]:\n" + line);
                        continue;
                    }
                    final int minor = iTmp;

                    // device or partition name
                    if (!st.hasMoreTokens()) {
                        logger.log(Level.WARNING,
                                "[ monDiskIOStat ] Device or partition name not found. Ignoring line [ " + lineNo
                                        + " ]:\n" + line);
                        continue;
                    }
                    final String devPartName = StringFactory.get(st.nextToken());
                    final DevKey devKey = DevKey.newInstance(major, minor, devPartName);
                    // check if is allowed or not to be monitored
                    if (config != null) {
                        if (!config.isAllowed(devKey)) {
                            if (isFINE) {
                                logger.log(Level.FINE, "[ monDiskIOStat ] The device: " + devPartName
                                        + " is filtered in the config");
                            }
                            continue;
                        }
                    }

                    // check if is really a device
                    if (!isDevice(devKey)) {
                        if (isFINEST) {
                            logger.log(Level.FINEST, "[ monDiskIOStat ] Ignoring device/partition " + devPartName
                                    + ". Not a device.");
                        }
                        continue;
                    }

                    // Field 1 -- total number of completed read requests
                    BigInteger bi;
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unable to parse field 1 - # of reads completed");
                        continue;
                    }
                    final BigInteger readReqs = bi;

                    // Field 2 -- merged read req
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unable to parse field 2 - # of merged read req");
                        continue;
                    }
                    final BigInteger mergedReadReq = bi;

                    // Field 3 # of sectors read successfully
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 3 - # of sectors read successfully");
                        continue;
                    }
                    final BigInteger readSectors = bi;

                    // Field 4 -- # of milliseconds spent reading
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 4 - # of milliseconds spent reading");
                        continue;
                    }
                    final BigInteger millisRead = bi;

                    // Field 5 -- # of writes completed
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 5 - # of writes completed");
                        continue;
                    }
                    final BigInteger writeReqs = bi;

                    // Field 6 -- merged write req
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 6 - # of merged write req");
                        continue;
                    }
                    final BigInteger mergedWriteReq = bi;

                    // Field 7 # of sectors written successfully
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 7 - # of sectors written successfully");
                        continue;
                    }
                    final BigInteger writeSectors = bi;

                    // Field 8 -- # of milliseconds spent reading
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 8 - # of milliseconds spent writing");
                        continue;
                    }
                    final BigInteger millisWrite = bi;

                    // Field 9 -- # of I/Os currently in progress
                    long tlong = 0L;
                    try {
                        tlong = Long.parseLong(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 9 - # of I/Os currently in progress");
                        continue;
                    }
                    final long concurrentIO = tlong;

                    // Field 10 -- # of milliseconds spent doing I/Os
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 10 - # of milliseconds spent doing I/Os");
                        continue;
                    }
                    final BigInteger millisIO = bi;

                    // Field 11 -- weighted # of milliseconds spent doing I/Os
                    try {
                        bi = new BigInteger(st.nextToken());
                    } catch (Throwable t) {
                        logger.log(Level.FINE, "[ monDiskIOStat ] Ignoring device " + devPartName
                                + " Unalbe to parse field 11 - weighted # of milliseconds spent doing I/Os");
                        continue;
                    }
                    final BigInteger weightedIO = bi;

                    final RawIOStats cStats = new RawIOStats(readReqs, mergedReadReq, readSectors, millisRead,
                            writeReqs, mergedWriteReq, writeSectors, millisWrite, concurrentIO, millisIO, weightedIO);

                    final DerivedIOStats dio = processAndUpdateDevice(devKey, cStats);
                    if (dio == null) {
                        continue;
                    }
                    final BlockDevice bdev = devicesMap.get(devKey);

                    if (readSectors.equals(BigInteger.ZERO) && writeSectors.equals(BigInteger.ZERO)) {
                        continue;
                    }
                    final Result r = new Result(node.getFarmName(), node.getClusterName(), node.getName(),
                            getTaskName());
                    r.time = rTime;

                    double cDevRead = (dio.readSpeed * bdev.sectorSize) / 1024 / 1024;
                    double cDevWrite = (dio.writeSpeed * bdev.sectorSize) / 1024 / 1024;
                    double cUtil = (dio.utilization <= 100) ? dio.utilization : 100;

                    String newPName = devPartName;
                    if (devPartName.indexOf("/") >= 0) {
                        newPName = devPartName.replace("/", "!");
                    }

                    if (cUtil > maxIOUsage) {
                        maxIOUsage = cUtil;
                        maxIOUsageDevice = newPName;
                        maxIOUsageRead = cDevRead;
                        maxIOUsageWrite = cDevWrite;

                        maxIOUsageReadIOPS = dio.readIOPS;
                        maxIOUsageReadIOPSMerged = dio.mergedReadIOPS;
                        maxIOUsageWriteIOPS = dio.writeIOPS;
                        maxIOUsageWriteIOPSMerged = dio.mergedWriteIOPS;
                    }

                    r.addSet(newPName + "_ReadMBps", cDevRead);
                    r.addSet(newPName + "_ReadIOPS", dio.readIOPS);
                    r.addSet(newPName + "_ReadIOPSMerged", dio.mergedReadIOPS);

                    r.addSet(newPName + "_WriteMBps", cDevWrite);
                    r.addSet(newPName + "_WriteIOPS", dio.writeIOPS);
                    r.addSet(newPName + "_WriteIOPSMerged", dio.mergedWriteIOPS);

                    if (sumTrafficPerDevice) {
                        r.addSet(newPName + "_TotalMBps", cDevRead + cDevWrite);
                    }

                    if (sumIOPSPerDevice) {
                        r.addSet(newPName + "_IOPS", dio.readIOPS + dio.writeIOPS);
                        r.addSet(newPName + "_IOPSMerged", dio.mergedReadIOPS + dio.mergedWriteIOPS);
                    }

                    r.addSet(newPName + "_IOUtil", cUtil);
                    r.Module = getTaskName();

                    totalDevices++;
                    totalIOUtil += cUtil;

                    totalReadSpeed += cDevRead;
                    totalReadIOPS += dio.readIOPS;
                    totalMergedReadIOPS += dio.mergedReadIOPS;

                    totalWriteSpeed += cDevWrite;
                    totalWriteIOPS += dio.writeIOPS;
                    totalMergedWriteIOPS += dio.mergedWriteIOPS;

                    lRet.add(r);

                } catch (Throwable t) {
                    logger.log(Level.WARNING, " [ monDiskIOStat ] exception parsing " + LINUX_PROC_DISK_STATS
                            + " file at line [ " + lineNo + " ]:\n" + line + "\n", t);
                }

            }

            final Result r = new Result(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName());
            final eResult er = new eResult(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName(),
                    null);

            er.time = r.time = rTime;

            r.addSet("TOTAL_devices", totalDevices);

            if (totalDevices > 0) {
                r.addSet("TOTAL_ReadMBps", totalReadSpeed);
                r.addSet("TOTAL_ReadIOPS", totalReadIOPS);
                r.addSet("TOTAL_ReadIOPSMerged", totalMergedReadIOPS);

                r.addSet("TOTAL_WriteMBps", totalWriteSpeed);
                r.addSet("TOTAL_WriteIOPS", totalWriteIOPS);
                r.addSet("TOTAL_WriteIOPSMerged", totalMergedWriteIOPS);

                r.addSet("TOTAL_TotalMBps", totalReadSpeed + totalWriteSpeed);
                r.addSet("TOTAL_IOPS", totalReadIOPS + totalWriteIOPS);
                r.addSet("TOTAL_IOPSMerged", totalMergedReadIOPS + totalMergedWriteIOPS);

                r.addSet("AVG_IOUtil", totalIOUtil / totalDevices);
            }

            if (maxIOUsage >= 0) {
                r.addSet("MAXIOUTIL_ReadMBps", maxIOUsageRead);
                r.addSet("MAXIOUTIL_ReadIOPS", maxIOUsageReadIOPS);
                r.addSet("MAXIOUTIL_ReadIOPSMerged", maxIOUsageReadIOPSMerged);

                r.addSet("MAXIOUTIL_WriteMBps", maxIOUsageWrite);
                r.addSet("MAXIOUTIL_WriteIOPS", maxIOUsageWriteIOPS);
                r.addSet("MAXIOUTIL_WriteIOPSMerged", maxIOUsageWriteIOPSMerged);

                r.addSet("MAXIOUTIL_TotalMBps", maxIOUsageRead + maxIOUsageWrite);
                r.addSet("MAXIOUTIL_IOPS", maxIOUsageReadIOPS + maxIOUsageWriteIOPS);
                r.addSet("MAXIOUTIL_IOPSMerged", maxIOUsageReadIOPSMerged + maxIOUsageWriteIOPSMerged);

                r.addSet("MAXIOUTIL_IOUtil", maxIOUsage);

                er.addSet("MAXIOUTIL_Device", maxIOUsageDevice);
            }

            if ((r.param != null) && (r.param.length > 0)) {
                lRet.add(r);
            }

            if ((er.param != null) && (er.param.length > 0)) {
                lRet.add(er);
            }
        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(br);
        }

        return lRet;
    }

    private DerivedIOStats processAndUpdateDevice(DevKey devKey, RawIOStats cStats) {
        final BlockDevice bDev = devicesMap.get(devKey);
        if (bDev == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " Adding " + devKey + " for monitoring");
            }
            devicesMap.put(devKey, new BlockDevice(devKey, cStats));
            return null;
        }

        return bDev.updateIOStats(cStats);
    }

    private boolean isDevice(DevKey devKey) {
        return DevNameCache.isDevice(devKey);
    }

    @Override
    public Object doProcess() throws Exception {
        if (isLinuxOS()) {
            return processLinuxProcFS(configReference.get());
        }

        return null;
    }

    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));
        logger.setLevel(Level.INFO);
        logger.log(Level.INFO, " Running on OS: " + OS_NAME + "; VERSION: " + OS_VER + "; ARCH: " + OS_ARCH);

        monDiskIOStat mdIOStat = new monDiskIOStat();
        mdIOStat.init(new MNode(), "");

        for (;;) {

            Thread.sleep(5 * 1000);
            final long sTime = Utils.nanoNow();
            Collection<?> cr = (Collection<?>) mdIOStat.doProcess();
            final long endTime = Utils.nanoNow();
            StringBuilder sb = new StringBuilder();
            for (final Object r : cr) {
                sb.append(r).append("\n");
            }
            logger.log(Level.INFO, " DT " + TimeUnit.NANOSECONDS.toMillis(endTime - sTime) + " ms. \n\n Returning \n"
                    + sb.toString());
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        try {
            DevNameCache.clearCache();
            configReference.set(configFile != null ? new Config(configFile) : new Config());
            logger.log(Level.INFO, "[ monDiskIOStat ] loading conf: " + configReference.get()
                    + ((configFile != null) ? (" from: " + configFile) : ""));
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ monDiskIOStat ] Unable to reload new config", t);
        }
    }

}
