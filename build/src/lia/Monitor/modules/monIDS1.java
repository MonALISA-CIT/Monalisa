package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

public class monIDS1 extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 7688347007276621253L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monIDS1.class.getName());

    private static final String LOW_ATTACKS = "LowLevelAttacks";
    private static final String MEDIUM_ATACKS = "MediumLevelAtacks";
    private static final String HIGH_ATTACKS = "HighLevelAttacks";

    static public String ModuleName = "monIDS1";

    static public String[] ResTypes = null;

    static public String OsName = "linux";

    private String cmd;

    //Snort stats stuff
    private String pgrepCmd;
    private String psSnortCmd;
    private String snortPID;
    private static final int dtSnortPIDUpdate = 1 * 60 * 60 * 1000;//3h
    private long lastSnortPIDUpdate;

    //last time this module was called
    private long lastCall;

    private String alertfile = null;

    private static String localAddress;
    private long logOffset;

    static {
        localAddress = AppConfig.getProperty("lia.Monitor.useIPaddress", "127.0.0.1");

        if ((localAddress == null) || (localAddress.length() == 0)) {
            try {
                localAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {
            }
        }
    }

    public monIDS1() {
        super(ModuleName);
        //info is initialized at this point
        ResTypes = info.ResTypes;
        info.name = ModuleName;
        info.setState(0);
        lastCall = NTPDate.currentTimeMillis();
        info.lastMeasurement = lastCall;
        this.lastSnortPIDUpdate = lastCall;
        isRepetitive = true;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        //init module arguments 
        init_args(arg);
        //get snort PID
        init_snortstats();
        return info;
    }

    /**
     * Try to find the snort porcess PID in order to report memory/cpu usage data
     */
    private void init_snortstats() {

        //init to null, in case the PID is to be re-read
        this.snortPID = null;

        String mlHome = null;
        StringBuilder pgrepCmd = new StringBuilder();
        //FIXME - o more reliable rule for detecting snort process PID
        pgrepCmd.append("pgrep -x -f \".*snort.*-A fast.*\"");

        mlHome = AppConfig.getProperty("MonaLisa_HOME", null);
        if ((mlHome == null) || (mlHome.length() == 0)) {
            logger.log(Level.WARNING, "monIDS Could not determine MonaLisa_HOME.Try to use *pgrep* from $PATH");
        } else {
            pgrepCmd.insert(0, mlHome + "/bin/");
        }

        this.pgrepCmd = pgrepCmd.toString();
        BufferedReader buff = procOutput(this.pgrepCmd);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Execute *pgrep* cmd: " + this.pgrepCmd);
        }

        if (buff != null) {
            try {
                //just  first PID is of interest
                snortPID = buff.readLine();
                //anything else on output?
                while (buff.readLine() != null) {
                    ;
                }
                //be gentle and wait for process to finish
                int exitValue = pro.waitFor();
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " pgrep exitvalue: " + exitValue);
                }
                if (exitValue != 0) {
                    throw new Exception("pgrep non-zero exitValue=" + exitValue);
                }
            } catch (Throwable t) {
                //Oh,no.. WTF
            }
        }
        cleanup();

        if (snortPID == null) {
            //severe init error, cannot continue..
            psSnortCmd = null;
            logger.log(Level.SEVERE, "Cannot find the snort PID. Is it running?");
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("Cannot find the snort PID. Is it running? ...");
        } else {
            psSnortCmd = "ps --no-headers -H -p" + snortPID + " -o etime,time,pmem";
            info.setState(0); // success
        }

    }

    private void init_args(String list) {
        if ((list == null) || (list.length() == 0)) {
            return;
        }
        String params[] = list.split("(\\s)*,(\\s)*");
        if ((params == null) || (params.length == 0)) {
            return;
        }

        for (String param : params) {
            int itmp = param.indexOf("SnortAlerts");
            if (itmp != -1) {
                String tmp = param.substring(itmp + "SnortAlerts".length()).trim();
                int iq = tmp.indexOf("=");
                alertfile = tmp.substring(iq + 1).trim();
            }
        }
        if (alertfile == null) {
            //severe init error, cannot continue..
            logger.log(Level.SEVERE, "alert file parameter not set ...");
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("alert file parameter not set ...");
            return;
        }

        File f = new File(alertfile);
        if (!f.isFile() || !f.canRead()) {
            //severe init error, cannot continue..
            logger.log(Level.SEVERE, "Snort alerts log-file could not be found or is not readable by monalisa user ...");
            cmd = null;
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("logtail could not be found ...");
            return;

        } else {
            logOffset = f.length();
        }

    }

    @Override
    public String[] ResTypes() {
        return info.ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        // can't run this module, init failed
        if (info.getState() != 0) {
            throw new Exception("There was some exception during init ...");
        }

        long ls = NTPDate.currentTimeMillis();
        if (ls <= lastCall) {
            return null;
        }

        /*
         * report Snort alerts
         */
        Object o = null;
        try {
            o = getLastAlerts();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Cannot get last alerts:" + t.getMessage());
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("Cannot get last alerts" + t);
        }

        if ((o == null) || !(o instanceof Hashtable)) {
            return null;
        }
        Hashtable allAlerts = (Hashtable) o;
        if (allAlerts.size() == 0) {
            return null;
        }

        Vector retV = new Vector();
        //Iterate over the priorities
        Iterator it = allAlerts.keySet().iterator();
        while (it.hasNext()) {
            // Get priority
            String priority = (String) it.next();
            o = allAlerts.get(priority);
            if ((o == null) || !(o instanceof Hashtable)) {
                return null;
            }

            Hashtable alerts = (Hashtable) o;
            if (alerts.size() == 0) {
                continue;
            }

            //Iterate over the values in the map
            Iterator iter = alerts.keySet().iterator();
            while (iter.hasNext()) {
                //          Get key
                String srcIP = (String) iter.next();

                //we don't report local sourced attacks :-)
                if (localAddress.compareToIgnoreCase(srcIP) == 0) {
                    continue;
                }
                int count = ((Integer) alerts.get(srcIP)).intValue();
                Result r = new Result();
                r.FarmName = getFarmName();
                r.ClusterName = priority;
                r.NodeName = srcIP;
                r.Module = ModuleName;
                r.time = ls;
                double dt = (ls - lastCall) / 1000;
                //attacks/s
                r.addSet("Rate", count / dt);
                retV.add(r);
            }
        }
        /*
         * report Snort process stats
         *          
         */
        //snort cpu,mem stats
        double[] snortPMstats;

        // if alert file is log-rotated, the snort process changes its PID, so
        // we need to grep it again
        if ((ls - lastSnortPIDUpdate) > dtSnortPIDUpdate) {
            init_snortstats();
            lastSnortPIDUpdate = ls;
        }

        if (psSnortCmd == null) {
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("Cannot find the snort PID. Is it running? ...");
        } else {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Executing cmd = " + psSnortCmd);
            }
            try {
                BufferedReader buff1 = procOutput(psSnortCmd);

                if (buff1 == null) {
                    if (pro != null) {
                        pro.destroy();
                    }
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "monIDS Exception while processing the buffer! Buffer is NULL!");
                    }
                } else { // ok,we have the goods
                    snortPMstats = parseSnortStats(buff1);
                    Result r = new Result();
                    r.FarmName = getFarmName();
                    r.ClusterName = "Snort";
                    r.NodeName = "Stats";
                    r.Module = ModuleName;
                    r.time = ls;
                    r.addSet("pcpu", snortPMstats[0]);
                    r.addSet("pmem", snortPMstats[1]);
                    retV.add(r);
                }
                // cleanup native process
                cleanup();
            } catch (Throwable t1) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "monIDS1. Exception while processing the buffer", t1);
                }
            }
        }
        //update last sampling time
        lastCall = ls;
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, retV.toString());
        }
        if ((retV != null) && (retV.size() > 0)) {
            return retV;
        }
        return null;
    }

    /**scan the snort alert file for last attacks    
     * @throws Exception
     */
    private Hashtable getLastAlerts() throws Exception {
        Hashtable pr = new Hashtable();

        pr.put(HIGH_ATTACKS, new Hashtable());
        pr.put(MEDIUM_ATACKS, new Hashtable());
        pr.put(LOW_ATTACKS, new Hashtable());
        // pr.put("UnknownPriority", new Hashtable());

        // snapshot length
        File f = new File(alertfile);
        if (!f.exists() || !f.isFile()) {
            throw new IOException("Invalid alert file. It does not exist or it is not a file");
        }
        long length = f.length();
        //have some new records?                     
        if (length > logOffset) {
            // Open the file to get the new contents.
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            raf.seek(logOffset);
            String line = null;
            //grab each line
            while ((line = raf.readLine()) != null) {
                int priority = Integer.MAX_VALUE;
                String srcIP = null;
                Pattern pattern = Pattern.compile("\\s+([\\d\\.]+)\\:?(\\d+)?\\s[\\-\\>]+\\s([\\d\\.]+)\\:?(\\d+)?");
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    srcIP = matcher.group(1);
                }
                pattern = null;
                matcher = null;
                pattern = Pattern.compile("\\s+\\[Priority\\:\\s+(\\d)]\\s+");
                matcher = pattern.matcher(line);
                if (matcher.find()) {
                    try {
                        priority = Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException e) {
                    }
                }
                String level;
                switch (priority) {
                case 1:
                    level = HIGH_ATTACKS;
                    break;
                case 2:
                    level = MEDIUM_ATACKS;
                    break;
                case 3:
                    level = LOW_ATTACKS;
                    break;
                default:
                    //XXX - UnknownAttacks is considered  to be low level attacks
                    level = LOW_ATTACKS;
                    break;
                }
                Hashtable prH = (Hashtable) pr.get(level);
                if (prH.containsKey(srcIP)) {
                    int count = ((Integer) prH.get(srcIP)).intValue() + 1;
                    prH.put(srcIP, Integer.valueOf(count));
                } else {
                    prH.put(srcIP, Integer.valueOf(1));
                }
            }//while readline()s

            //get the actual fp, the point where readline has stopped
            logOffset = raf.getFilePointer();
            raf.close();
        } else if (length < logOffset) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "[Log file truncated or log-rotated. Restarting ]");
            }
            logOffset = 0;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "LogOffset:" + logOffset);
        }
        //else  logOffset = length;                
        return pr;
    }

    private static String getLocalIP() {
        String localAddress = AppConfig.getProperty("lia.Monitor.useIPaddress", null);

        if ((localAddress == null) || (localAddress.length() == 0)) {
            try {
                localAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Throwable t) {

            }
        }
        return localAddress;
    }

    private double[] parseSnortStats(BufferedReader buff) {
        double retETime = 0;
        double timeSum = 0;
        double pMem = -1;
        if (buff == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "monIDS Exception while processing the buffer! Buffer is NULL!");
            }
            return null;
        }

        for (;;) {
            String line = null;
            try {
                line = buff.readLine();
            } catch (Throwable t1) {
                // not a normal EOS
                return null;
            }
            if (line == null) {
                break;
            }
            StringTokenizer st = new StringTokenizer(line, " \t");

            // time
            if (!st.hasMoreTokens()) {
                continue;
            }
            String ETIME = st.nextToken();
            if ((ETIME == null) || (ETIME.length() == 0)) {
                continue;
            }
            long crtETime = ParseTime(ETIME);
            if (retETime < crtETime) {
                retETime = crtETime;
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "MAX ETime " + retETime);
            }

            // time
            if (!st.hasMoreTokens()) {
                continue;
            }
            String TIME = st.nextToken();
            if ((TIME == null) || (TIME.length() == 0)) {
                continue;
            }
            timeSum += ParseTime(TIME);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Sum CPU_Time " + timeSum);
            }

            //          pmem, read it only once
            if (!st.hasMoreTokens() || (pMem > 0)) {
                continue;
            }
            String sPMEM = st.nextToken();
            if ((sPMEM == null) || (sPMEM.length() == 0)) {
                continue;
            }
            pMem = Double.valueOf(sPMEM).doubleValue();
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "%MEM " + pMem);
            }

        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "TOTAL SumCPU_Time:ETime:%MEM " + timeSum + ":" + retETime + ":" + pMem);
        }
        //return pcpu,pmem
        return new double[] { (timeSum / retETime) * 100, pMem };

    }

    //days-hh:mm:ss
    private long ParseTime(String timeS) {
        if ((timeS == null) || (timeS.length() == 0)) {
            return 0;
        }

        long sum = 0;

        Pattern pattern = Pattern.compile("((\\d{1,2})-){0,1}(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");
        Matcher matcher = pattern.matcher(timeS);
        if (matcher.find()) {
            String sDays, sHours, sMinutes, sSeconds;
            long lDays, lHours, lMinutes, lSeconds;
            sDays = matcher.group(2);
            lDays = (sDays == null ? 0 : Long.valueOf(sDays).longValue());

            sHours = matcher.group(3);
            lHours = (sHours == null ? 0 : Long.valueOf(sHours).longValue());

            sMinutes = matcher.group(4);
            lMinutes = (sMinutes == null ? 0 : Long.valueOf(sMinutes).longValue());

            sSeconds = matcher.group(5);
            lSeconds = (sSeconds == null ? 0L : Long.valueOf(sSeconds).longValue());
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "monIDS1 ParseTime = [ " + sDays + " - " + sHours + ":" + sMinutes + ":"
                        + sSeconds + " ]");
            }
            sum = (lDays * 24 * 60 * 60 * 1000) + (lHours * 60 * 60 * 1000) + (lMinutes * 60 * 1000)
                    + (lSeconds * 1000);

        }
        return sum;
    }

    static public void main(String[] args) {
        System.out.println("START;");
        String host = "localhost"; //args[0] ;
        monIDS1 aa = new monIDS1();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), "SnortAlerts=/var/log/snort/alert");
        while (true) {
            try {
                Thread.sleep(1000 * 10);
                aa.doProcess();
                //System.out.println((Vector) cb);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("DONE.");
        }
        // System.exit(0);
    }

}
