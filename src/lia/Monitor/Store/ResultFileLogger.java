/*
 * Created on Nov 20, 2003
 */
package lia.Monitor.Store;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.DataReceiver;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.BoundedDropVector;
import lia.util.DropEvent;
import lia.util.ntp.NTPDate;
import lia.util.threads.MonALISAExecutors;

/**
 * Log results in a file
 */
public class ResultFileLogger extends Thread implements DataReceiver, DropEvent {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ResultFileLogger.class.getName());

    private static Calendar cal = Calendar.getInstance();

    private final BoundedDropVector buff;

    private final Vector<Object> tmpbuff;

    /**
     * 
     */
    public boolean hasToRun;

    private static final SimpleDateFormat dateform;

    private static NumberFormat nf = NumberFormat.getInstance();

    private static String dirPath = AppConfig.getProperty("lia.Monitor.JStore.DirLogger", ".");

    private String cfn = null;

    private int iMaxDays = -1;

    /**
     * the instance
     */
    static ResultFileLogger rflInstance = null;

    static {
        dateform = new SimpleDateFormat(AppConfig.getProperty("lia.file_logger.time_format", " HH:mm:ss"));
    }

    /**
     * @return the instance
     */
    public static final synchronized ResultFileLogger getLoggerInstance() {
        if (rflInstance == null) {
            try {
                rflInstance = new ResultFileLogger();
                rflInstance.start();
            } catch (Exception e) {
                rflInstance = null;
            }
        }

        return rflInstance;
    }

    /**
     * @return true if running
     */
    public boolean isActive() {
        return hasToRun;
    }

    private ResultFileLogger() {
        super("( ML ) JStore Results File Logger");

        this.iMaxDays = AppConfig.geti("lia.Monitor.Store.FileLogger.maxDays", 2);

        if (iMaxDays <= 0) {
            throw new IndexOutOfBoundsException("lia.Monitor.Store.FileLogger.maxDays is <=0, file logging is disabled");
        }

        hasToRun = true;
        buff = new BoundedDropVector(AppConfig.geti("lia.Monitor.JStore.BufferSize", 10000), this);
        tmpbuff = new Vector<Object>();
        nf.setMaximumFractionDigits(3);
        nf.setMinimumFractionDigits(1);
        nf.setGroupingUsed(false);

        cleanupDir(iMaxDays);
    }

    private int iDropEvents = 0;

    @Override
    public void notifyDrop() {
        synchronized (buff) {
            iDropEvents++;

            if (iDropEvents > 100) {
                logger.log(Level.WARNING, "ResultFileLogger is stoping now because there are too many drop events");

                hasToRun = false;
            } else {
                logger.log(Level.WARNING, "ResultFileLogger received notification no. " + iDropEvents
                        + " that the buffer is full");
            }

            buff.clear();
        }
    }

    /**
     * @param o
     */
    public void addResult(Object o) {
        if (o instanceof Result) {
            addResult((Result) o);
        } else if (o instanceof eResult) {
            addResult((eResult) o);
        } else if (o instanceof Collection<?>) {
            addResult((Collection<?>) o);
        }
    }

    /**
     * @param c
     */
    public void addResult(Collection<?> c) {
        if ((c == null) || (c.size() <= 0)) {
            return;
        }

        if (hasToRun) {
            Iterator<?> it = c.iterator();

            while (it.hasNext()) {
                addResult(it.next());
            }
        }
    }

    @Override
    public void addResult(Result a) {
        if (hasToRun) {
            buff.add(a);
        }
    }

    @Override
    public void addResult(eResult a) {
        if (hasToRun) {
            buff.add(a);
        }
    }

    @Override
    public void addResult(ExtResult a) {
        // don't know how
    }

    @Override
    public void addResult(AccountingResult a) {
        // don't know how also
    }

    @Override
    public void updateConfig(lia.Monitor.monitor.MFarm f) {
        // and these ... we don't care :)
    }

    private static String formatResult(Result r) {
        if (r == null) {
            return null;
        }

        if ((r.param == null) || (r.param.length == 0) || (r.param_name == null) || (r.param_name.length == 0)) {
            return null;
        }

        final StringBuilder sb = new StringBuilder(100);

        final long time = r.time;
        final Date da = new Date(time);
        final String date = dateform.format(da);

        for (int i = 0; i < r.param_name.length; i++) {
            //ans += date + "  " + r.ClusterName + "  "+ r.NodeName+ "  " + r.param_name[i] + " "+ nf.format(r.param[i]) + "\n"; 
            sb.append(date).append('\t');
            sb.append(r.FarmName).append('\t');
            sb.append(r.ClusterName).append('\t');
            sb.append(r.NodeName).append('\t');
            sb.append(r.param_name[i]).append('\t');
            sb.append(nf.format(r.param[i])).append('\n');
        }

        return sb.toString();
    }

    private static String formatResult(eResult r) {
        if (r == null) {
            return null;
        }

        if ((r.param == null) || (r.param.length == 0) || (r.param_name == null) || (r.param_name.length == 0)) {
            return null;
        }

        final StringBuilder sb = new StringBuilder(100);

        final long time = r.time;
        final Date da = new Date(time);
        final String date = dateform.format(da);

        for (int i = 0; i < r.param_name.length; i++) {
            //ans += date + "  " + r.ClusterName + "  "+ r.NodeName+ "  " + r.param_name[i] + " "+ nf.format(r.param[i]) + "\n"; 
            sb.append(date).append('\t');
            sb.append(r.FarmName).append('\t');
            sb.append(r.ClusterName).append('\t');
            sb.append(r.NodeName).append('\t');
            sb.append(r.param_name[i]).append('\t');
            if (r.param[i] == null) {
                sb.append("NULL");
            } else {
                sb.append(r.param[i].getClass().getName()).append(':').append(r.param[i].toString());
            }

            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * @param dir
     * @param fileName
     * @throws Exception
     */
    static final void zipFile(String dir, String fileName) throws Exception {
        ZipOutputStream zos = null;
        FileInputStream fis = null;

        try {
            logger.info("Starting zipping previous logfile: " + fileName);
            zos = new ZipOutputStream(new FileOutputStream(dir + "/" + fileName + ".zip"));
            fis = new FileInputStream(dir + "/" + fileName);
            byte[] buff = new byte[1024];

            zos.putNextEntry(new ZipEntry(fileName));

            boolean hasToRead = true;
            while (hasToRead) {
                int n = fis.read(buff, 0, buff.length);
                if (n > 0) {
                    zos.write(buff, 0, n);
                } else {
                    hasToRead = false;
                }
            }

            zos.flush();

            fis.close();
            zos.close();

            fis = null;
            zos = null;

            logger.info("\n[ ZIP ]\nFrom: " + fileName + " [ " + new File(dir + "/" + fileName).length() + " ]\nTo: "
                    + fileName + ".zip [ " + new File(dir + "/" + fileName + ".zip").length() + " ]\n");
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    // ignore
                }
            }

            if (zos != null) {
                try {
                    zos.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private long lLastSaved = NTPDate.currentTimeMillis();

    private static final Pattern LOGFILE_PATTERN = Pattern.compile(".*JStore_([0-9]+)_([0-9]+)_([0-9]+)\\.log.*");

    /**
     * @param iMaxDays
     */
    public static final void cleanupDir(int iMaxDays) { // delete old log files
        //logger.log(Level.INFO, "CleaupDir : "+iMaxDays);

        if (iMaxDays <= 0) {
            return;
        }

        try {
            File f = new File(dirPath);

            String[] fileList = f.list();

            if ((fileList == null) || (fileList.length == 0)) {
                return;
            }

            Matcher m;

            long now = NTPDate.currentTimeMillis();

            for (String sFN : fileList) {

                //System.err.println("FN : "+sFN);

                m = LOGFILE_PATTERN.matcher(sFN);

                if (m.matches()) {
                    //System.err.println("  Matches");
                    String sYear = m.group(1);
                    String sMonth = m.group(2);
                    String sDay = m.group(3);

                    Date d = new GregorianCalendar(Integer.parseInt(sYear), Integer.parseInt(sMonth) - 1,
                            Integer.parseInt(sDay)).getTime();

                    //System.err.println(now+" : "+d+" : "+(now - d.getTime()));

                    if ((now - d.getTime()) > ((iMaxDays + 1) * 24L * 60L * 60L * 1000L)) {
                        //System.err.println("  DELETE");
                        try {
                            (new File(dirPath + "/" + sFN)).delete();
                        } catch (Exception ee) {
                            logger.log(Level.WARNING, "Cannot delete : " + sFN, ee);
                        }
                    } else {
                        //System.err.println("  KEEP");
                    }
                } else {
                    //System.err.println("  Doesn't match");
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "ResultFileLogger : exception during cleanup", e);
        }

    }

    /** Perform the zipping in a separate task. The task is scheduled to run immediately.*/
    private void scheduleLogZip(final String file, final String path) {
        MonALISAExecutors.getMLHelperExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                boolean bDeleteAfterZip = true;
                try {
                    zipFile(path, file);
                } catch (Throwable t) {
                    bDeleteAfterZip = false;
                    logger.log(Level.WARNING, " Got error trying to zip file: " + file + " to directory: " + path, t);
                }
                if (bDeleteAfterZip) {
                    scheduleLastLogDelete(path + "/" + file);
                }
            }
        }, 0, TimeUnit.SECONDS);
    }

    /** Perform the deletion of last log file with a configurable delay.
     * By default, last log is deleted immediately after successful zipping. 
     * @param fileToDelete */
    void scheduleLastLogDelete(final String fileToDelete) {
        long delInterval = AppConfig.getl("lia.Monitor.Store.ResultFileLogger.del_old_files_delay", 0);
        if (delInterval > 0) {
            logger.info("Old logfile will be deleted in " + delInterval + " seconds.");
        }
        MonALISAExecutors.getMLHelperExecutor().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    new File(fileToDelete).delete();
                    logger.info("Old logfile " + fileToDelete + " deleted.");
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, " Exception Got Trying to delete the file: " + fileToDelete, t1);
                }
            }
        }, delInterval, TimeUnit.SECONDS);
    }

    private void writeResults() {
        if (buff.size() == 0) {
            return;
        }

        lLastSaved = NTPDate.currentTimeMillis();

        synchronized (buff) {
            tmpbuff.clear();
            tmpbuff.addAll(buff);
            buff.clear();

            // each successfull write decreases the stop counter
            if (iDropEvents > 0) {
                iDropEvents--;
            }
        }

        BufferedWriter bw = null;

        try {
            cal.setTimeInMillis(NTPDate.currentTimeMillis());
            int iyear = cal.get(Calendar.YEAR);
            int imonth = cal.get(Calendar.MONTH) + 1;
            String month = (imonth < 10) ? ("0" + imonth) : "" + imonth;
            int iday = cal.get(Calendar.DAY_OF_MONTH);
            String day = (iday < 10) ? ("0" + iday) : "" + iday;
            String ccfn = "JStore_" + iyear + "_" + month + "_" + day + ".log";
            if (cfn == null) {
                cfn = ccfn;
            } else {
                if (cfn.compareTo(ccfn) != 0) {//should "zip" the older file
                    logger.log(Level.INFO, "START logrotate");
                    if (AppConfig.getb("lia.Monitor.Store.ResultFileLogger.zip_old_files", true)) {
                        scheduleLogZip(cfn, dirPath);
                    }
                    cfn = ccfn;

                    cleanupDir(iMaxDays);

                    logger.log(Level.INFO, "END logrotate");
                }
            }

            final File f = new File(dirPath + "/" + cfn);
            bw = new BufferedWriter(new FileWriter(f, true));

            for (int i = 0; i < tmpbuff.size(); i++) {
                Object o = tmpbuff.elementAt(i);
                if (o != null) {
                    String toWrite = null;

                    if (o instanceof Result) {
                        toWrite = formatResult((Result) o);
                    } else if (o instanceof eResult) {
                        toWrite = formatResult((eResult) o);
                    }

                    if ((toWrite != null) && (toWrite.length() > 0)) {
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Writing " + toWrite);
                        }
                        bw.write(toWrite);
                    }
                }
            }
            bw.flush();
            bw.close();
            bw = null;
        } catch (Throwable t) {
            if (bw != null) {
                try {
                    bw.close();
                } catch (Throwable t2) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void run() {
        logger.log(Level.INFO, "ResultFileLogger STARTED. Logging dir = " + dirPath);

        try {
            // try to create the destination folder
            (new File(dirPath)).mkdirs();
        } catch (Exception e) {
            // ignore
        }

        while (hasToRun) {
            try {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    // ignore
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "\n\n Got " + buff.size());
                }

                if ((buff.size() > 100)
                        || ((buff.size() > 1) && ((NTPDate.currentTimeMillis() - lLastSaved) > (30 * 1000)))) {
                    writeResults();
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "ResultFileLogger -- Exception in main loop ", t);
            }
        }

    }

    /**
     * @param args
     */
    public static final void main(String[] args) {
        try {
            ResultFileLogger.zipFile("/home/ramiro/JStoreLogger", "JStore_2003_11_20.log");

            cleanupDir(2);
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Exc ", t);
        }
    }
}
