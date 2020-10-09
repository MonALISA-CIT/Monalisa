package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 *
 */
public class monProcStat extends monProcReader {

    /**
     * @since ML 1.5.4
     */
    private static final long serialVersionUID = -4166827396826748236L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monProcStat.class.getName());

    static private final String ModuleName = "monProcStat";

    /**
     * return names
     */
    static String[] ResTypes;

    static private final String OsName = "linux";

    /**
     * old values
     */
    long[] old; // = new long[8];

    /**
     * current values
     */
    private long[] cur = new long[ResTypes.length];

    /**
     * temp
     */
    private long[] xtmp = new long[ResTypes.length];

    /**
     * counter diffs
     */
    private final long[] diff = new long[ResTypes.length];

    private static boolean hasCommonCPUStats;
    private static boolean hasCPUIOWaitStats;
    private static boolean hasCPUIntStats;
    private static boolean hasCPUStealStats;
    private static boolean hasPageProcStat;
    private static boolean hasSwapProcStat;
    private static boolean hasPageProcVmStat;
    private static boolean hasSwapProcVmStat;
    private static boolean hasCPUGuest;

    private long last_time = 0;

    private long lastProcesses = -1;

    static {
        synchronized (monProcStat.class) {
            try {
                ArrayList<String> al = new ArrayList<String>(16);//at least 12
                FileReader fr = null;
                BufferedReader br = null;
                File procFile = null;
                //check for info that can be processed from /proc/stat
                try {
                    procFile = new File("/proc/stat");
                    if (procFile.exists() && procFile.canRead()) {
                        fr = new FileReader("/proc/stat");
                        br = new BufferedReader(fr);
                        String line = br.readLine();
                        boolean parsedCPU = false;
                        for (; line != null; line = br.readLine()) {
                            line = line.trim();
                            if (!parsedCPU && line.startsWith("cpu")) {
                                parsedCPU = true;
                                String[] tokens = line.split("(\\s)+");
                                int len = tokens.length;
                                if (len >= 5) {
                                    al.add("CPU_usr");
                                    al.add("CPU_nice");
                                    al.add("CPU_sys");
                                    hasCommonCPUStats = true;
                                    if (len >= 6) {
                                        al.add("CPU_iowait");
                                        hasCPUIOWaitStats = true;
                                        if (len >= 8) {
                                            al.add("CPU_int");
                                            al.add("CPU_softint");
                                            hasCPUIntStats = true;
                                            if (len >= 9) {
                                                al.add("CPU_steal");
                                                hasCPUStealStats = true;

                                                if (len >= 10) {
                                                    al.add("CPU_guest");
                                                    hasCPUGuest = true;
                                                }
                                            }
                                        }
                                    }
                                    al.add("CPU_idle");
                                }//if (len >= 5 )
                            } else {// if ( "cpu" )
                                if (line.startsWith("page")) {
                                    hasPageProcStat = true;
                                } else if (line.startsWith("swap")) {
                                    hasSwapProcStat = true;
                                }
                            }
                        }//for
                    }//if ( procStatF.exists() )
                } catch (Throwable pft) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                " [ monProcStat ] [ HANDLED ] Checking for /proc/stat yield a caught exception", pft);
                    } else {
                        logger.log(Level.INFO,
                                " [ monProcStat ] [ HANDLED ] Cannot use /proc/stat for local monitoring");
                    }
                } finally {
                    Utils.closeIgnoringException(fr);
                    Utils.closeIgnoringException(br);
                }

                //check for info that can be processed from /proc/vmstat
                procFile = new File("/proc/vmstat");
                try {
                    if (procFile.exists() && procFile.canRead()) {
                        fr = new FileReader("/proc/vmstat");
                        br = new BufferedReader(fr);

                        String line = br.readLine();
                        for (; line != null; line = br.readLine()) {
                            line = line.trim();
                            if (line.startsWith("pgpgin")) {
                                hasPageProcVmStat = true;
                                continue;
                            }
                            if (line.startsWith("pswpin")) {
                                hasSwapProcVmStat = true;
                                continue;
                            }
                        }//for
                    }//if - exists && canRead
                } catch (Throwable pft) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                " [ monProcStat ] [ HANDLED ] Checking for /proc/vmstat yield a caught exception", pft);
                    } else {
                        logger.log(Level.INFO,
                                " [ monProcStat ] [ HANDLED ] Cannot use /proc/vmstat for local monitoring");
                    }
                } finally {
                    Utils.closeIgnoringException(fr);
                    Utils.closeIgnoringException(br);
                }

                if (hasPageProcStat || hasPageProcVmStat) {
                    al.add("Page_in");
                    al.add("Page_out");
                }

                if (hasSwapProcStat || hasSwapProcVmStat) {
                    al.add("Swap_in");
                    al.add("Swap_out");
                }

                ResTypes = al.toArray(new String[al.size()]);
            } catch (Throwable t) {
                logger.log(
                        Level.WARNING,
                        "[ monProcStat ] [ HANDLED ] Got exception in init. The module will not be used for local monitoring",
                        t);
            }
        }//synchronized
    }//static

    /**
     * @throws Exception
     */
    public monProcStat() throws Exception {
        super(ModuleName);
        PROC_FILE_NAMES = new String[] { "/proc/stat" };
        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
        if (hasSwapProcVmStat || hasPageProcVmStat) {
            PROC_FILE_NAMES = new String[] { "/proc/stat", "/proc/vmstat" };
        } else if (hasCommonCPUStats || hasSwapProcStat || hasPageProcStat) {
            PROC_FILE_NAMES = new String[] { "/proc/stat" };
        } else {
            throw new MLModuleInstantiationException("Cannot read or parse /proc/stat and /proc/vmstat");
        }

        logger.log(Level.INFO, " [ monProcStat ] Using : " + Arrays.toString(PROC_FILE_NAMES));
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    protected Object processProcModule() throws Exception {
        Result res = processProcStat(bufferedReaders);
        return res;
    }

    private Result processProcStat(BufferedReader[] brs) throws Exception {
        Result res = new Result();

        res.FarmName = Node.getFarmName();
        res.ClusterName = Node.getClusterName();
        res.NodeName = Node.getName();
        res.Module = ModuleName;

        long sTime = System.currentTimeMillis();
        final int len = ResTypes.length;
        BufferedReader br = brs[0];
        int index = 0;
        boolean parsedCPU = !hasCommonCPUStats;

        long processes = -1;

        for (;;) {
            String lin = br.readLine();
            if (lin == null) {
                break;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Processing line: [" + lin + "]");
            }

            lin = lin.trim();
            if (lin.length() == 0) {
                continue;
            }

            StringTokenizer tz = new StringTokenizer(lin);

            String item = tz.nextToken().trim();
            if (!parsedCPU && hasCommonCPUStats && item.equals("cpu")) {
                parsedCPU = true;
                cur[index++] = Long.parseLong(tz.nextToken());
                cur[index++] = Long.parseLong(tz.nextToken());
                cur[index++] = Long.parseLong(tz.nextToken());
                cur[index++] = Long.parseLong(tz.nextToken());
                if (hasCPUIOWaitStats) {
                    cur[index++] = Long.parseLong(tz.nextToken());
                    if (hasCPUIntStats) {
                        cur[index++] = Long.parseLong(tz.nextToken());
                        cur[index++] = Long.parseLong(tz.nextToken());
                        if (hasCPUStealStats) {
                            cur[index++] = Long.parseLong(tz.nextToken());

                            if (hasCPUGuest) {
                                cur[index++] = Long.parseLong(tz.nextToken());
                            }
                        }
                    }
                }
            } else if (item.equals("page")) {
                cur[index++] = Long.parseLong(tz.nextToken());
                cur[index++] = Long.parseLong(tz.nextToken());
            } else if (item.equals("swap")) {
                cur[index++] = Long.parseLong(tz.nextToken());
                cur[index++] = Long.parseLong(tz.nextToken());
            } else if (item.equals("processes")) {
                processes = Long.parseLong(tz.nextToken());
            }
        }

        if (hasSwapProcVmStat || hasPageProcVmStat) {
            br = brs[1];
            int cCount = 0;
            String lin = br.readLine();
            for (; (cCount < 4) && (lin != null); lin = br.readLine()) {
                lin = lin.trim();
                if (lin.startsWith("pgpgin")) {
                    cur[len - 4] = Long.parseLong(lin.substring(7));
                    cCount++;
                    continue;
                }

                if (lin.startsWith("pgpgout")) {
                    cur[len - 3] = Long.parseLong(lin.substring(8));
                    cCount++;
                    continue;
                }

                if (lin.startsWith("pswpin")) {
                    cur[len - 2] = Long.parseLong(lin.substring(7));
                    cCount++;
                    continue;
                }

                if (lin.startsWith("pswpout")) {
                    cur[len - 1] = Long.parseLong(lin.substring(8));
                    cCount++;
                    continue;
                }
            }
        }
        if (old == null) {
            old = cur;
            cur = xtmp;
            last_time = NTPDate.currentTimeMillis();
            return null;
        }

        for (int i = 0; i < diff.length; i++) {
            diff[i] = cur[i] - old[i];
        }

        boolean bCPU = true;

        for (int i = 0; i <= 2; i++) {
            if (diff[i] < 0) {
                bCPU = false;
                logger.log(Level.WARNING, " Decreasing " + ResTypes[i] + " old: " + old[i] + " new: " + cur[i]);
                break;
            }
        }

        res.time = NTPDate.currentTimeMillis();

        long sum = 0;
        if (bCPU) {
            if (diff[3] < 0) {
                bCPU = false;
                logger.log(Level.WARNING, " Decreasing CPU_idle old: " + old[3] + " new: " + cur[3]);
            } else {
                sum = diff[0] + diff[1] + diff[2] + diff[3];
                if (hasCPUIOWaitStats) {
                    if (diff[4] < 0) {
                        bCPU = false;
                        logger.log(Level.WARNING, " Decreasing CPU_iowait old: " + old[4] + " new: " + cur[4]);
                    } else {
                        sum += diff[4];
                        if (hasCPUIntStats) {
                            if (diff[5] < 0) {
                                bCPU = false;
                                logger.log(Level.WARNING, " Decreasing CPU_int old: " + old[5] + " new: " + cur[5]);
                            } else {
                                if (diff[6] < 0) {
                                    bCPU = false;
                                    logger.log(Level.WARNING, " Decreasing CPU_softint old: " + old[6] + " new: "
                                            + cur[6]);
                                } else {
                                    sum += diff[5] + diff[6];
                                    if (hasCPUStealStats) {
                                        if (diff[7] < 0) {
                                            bCPU = false;
                                            logger.log(Level.WARNING, " Decreasing CPU_steal old: " + old[7] + " new: "
                                                    + cur[7]);
                                        } else {
                                            sum += diff[7];

                                            if (hasCPUGuest) {
                                                sum += diff[8];
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bCPU) {
            index = 0;
            res.addSet(ResTypes[index++], (100.0 * diff[0]) / sum);
            res.addSet(ResTypes[index++], (100.0 * diff[1]) / sum);
            res.addSet(ResTypes[index++], (100.0 * diff[2]) / sum);

            if (hasCPUIOWaitStats) {
                res.addSet(ResTypes[index++], (100.0 * diff[4]) / sum);
                if (hasCPUIntStats) {
                    res.addSet(ResTypes[index++], (100.0 * diff[5]) / sum);
                    res.addSet(ResTypes[index++], (100.0 * diff[6]) / sum);
                    if (hasCPUStealStats) {
                        res.addSet(ResTypes[index++], (100.0 * diff[7]) / sum);

                        if (hasCPUGuest) {
                            res.addSet(ResTypes[index++], (100.0 * diff[8]) / sum);
                        }
                    }
                }
            }
            
			final double idleCPU = (100.0 * diff[3]) / sum;

			res.addSet(ResTypes[index++], idleCPU);
			res.addSet("cpu_usage", 100 - idleCPU);
        }

        double imp = ((res.time - last_time) / 1000D) * 1024D;

        /*
         * Little comment here ( hopefully both page|swap _in|out will be in MB/s 
         * Check this first: 
         *   - http://lkml.org/lkml/2002/4/12/6
         *   - http://marc.theaimsgroup.com/?l=linux-kernel&m=101770318012189&w=2
         * 
         * the page_[ in | out ] represents in 1KB values
         * the swap_[ in | out ] represents in PAGE_SIZE, usually 4KB values
         */
        if (diff[len - 4] < 0) {
            logger.log(Level.WARNING, " Decreasing page_in old: " + old[len - 4] + " new: " + cur[len - 4]);
        } else {
            if (diff[len - 3] < 0) {
                logger.log(Level.WARNING, " Decreasing page_out old: " + old[len - 3] + " new: " + cur[len - 3]);
            } else {
                res.addSet(ResTypes[len - 4], diff[len - 4] / imp);
                res.addSet(ResTypes[len - 3], diff[len - 3] / imp);
            }
        }

        //TODO - this can be buggy
        /*
         * To termine the PAGE_SIZE you can run this code
         * cat << EOF |
         * #include <stdio.h>
         * main() { printf ("%d bytes\n",getpagesize()); }
         * EOF
         * gcc -xc - -o /tmp/getpagesize
         * /tmp/getpagesize; rm -f /tmp/getpagesize
         * 
         * PAGE_SIZE was 4096 bytes on all these machines ( though on SunOS the module does not work ! ):
         * 
         *  Linux pccit16 2.6.17-rc2 #2 SMP Wed Apr 19 10:25:58 CEST 2006 i686 pentium4 i386 GNU/Linux ( Slack_10.2 )
         *  Linux pccit15 2.4.20-18.7.cernsmp #1 SMP Thu Jun 12 12:27:49 CEST 2003 i686 unknown ( RH_7.3 )
         *  Linux lxplus056.cern.ch 2.4.21-40.EL.cernsmp #1 SMP Fri Mar 17 00:53:42 CET 2006 i686 i686 i386 GNU/Linux ( SLC 3.0.6 )
         *  SunOS vinci 5.10 Generic_118844-26 i86pc i386 i86pc
         *  Linux pccil 2.6.5-7.252-smp #1 SMP Tue Feb 14 11:11:04 UTC 2006 i686 i686 i386 GNU/Linux ( SuSE Linux 9.1 )
         */

        if (diff[len - 2] < 0) {
            logger.log(Level.WARNING, " Decreasing swap_in old: " + old[len - 2] + " new: " + cur[len - 2]);
        } else {
            if (diff[len - 1] < 0) {
                logger.log(Level.WARNING, " Decreasing swap_out old: " + old[len - 1] + " new: " + cur[len - 1]);
            } else {
                res.addSet(ResTypes[len - 2], diff[len - 2] / imp);
                res.addSet(ResTypes[len - 1], diff[len - 1] / imp);
            }
        }

        if (processes > 0) {
            res.addSet("Forks", processes);

            if (lastProcesses > 0) {
                res.addSet("Forks_R", ((processes - lastProcesses) * 1000d) / (res.time - last_time));
            }

            lastProcesses = processes;
        }

        last_time = res.time;
        if (logger.isLoggable(Level.FINE)) {
            StringBuilder sb = new StringBuilder(50);
            if (logger.isLoggable(Level.FINER)) {
                sb.append("\no: [ ");
                for (int i = 0; i < old.length; i++) {
                    sb.append(old[i]).append((i < (old.length - 1)) ? ", " : " ]");
                }

                sb.append("\nc: [ ");
                for (int i = 0; i < cur.length; i++) {
                    sb.append(cur[i]).append((i < (cur.length - 1)) ? ", " : " ]");
                }

                sb.append("\nd: [ ");
                for (int i = 0; i < diff.length; i++) {
                    sb.append(diff[i]).append((i < (diff.length - 1)) ? ", " : " ]");
                }
            }
            sb.append("\n Result :- ").append(res);
            sb.append("\n doProcess() dT [ " + (System.currentTimeMillis() - sTime) + " ] ms");
            logger.log(Level.FINE, sb.toString());
        }
        xtmp = old;
        old = cur;
        cur = xtmp;

        return res;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    /**
     * @param args
     * @throws Exception
     */
    static public void main(String[] args) throws Exception {
        String host = "localhost"; // args[0] ;
        monProcStat aa = new monProcStat();
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(("handlers= java.util.logging.ConsoleHandler\n"
                        + "java.util.logging.ConsoleHandler.level = FINEST\n"
                        + "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" + "")
                        .getBytes()));
        String ad = null;
        //        logger.setLevel(Level.FINER);
        logger.setLevel(Level.FINE);
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        aa.init(new MNode(host, ad, null, null), null, null);

        try {
            for (;;) {
                Thread.sleep(5000);
                Utils.dumpResults(aa.doProcess());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
