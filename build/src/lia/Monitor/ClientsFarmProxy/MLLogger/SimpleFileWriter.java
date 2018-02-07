/*
 * $Id: SimpleFileWriter.java 7419 2013-10-16 12:56:15Z ramiro $
 */

package lia.Monitor.ClientsFarmProxy.MLLogger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.logging.comm.MLLogMsg;

/**
 * TODO !!! This should smth more or less like a java.util.handler!
 */
public class SimpleFileWriter {

    private static final Logger logger = Logger.getLogger(SimpleFileWriter.class.getName());

    private static final SimpleFileWriter _theInstance = new SimpleFileWriter();

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread("(ML) SimpeFileWriter logger thread");
        }
    });

    String cDate;

    final Calendar calendar = Calendar.getInstance();

    BufferedWriter br;

    String defaultPath = ".";

    final AtomicBoolean hasToRun = new AtomicBoolean(true);

    int iDay;

    private static final class SimpleTaskPublisher implements Runnable {

        final BufferedWriter writer;
        final MLLogMsg msg;

        private SimpleTaskPublisher(BufferedWriter br, MLLogMsg msg) {
            this.writer = br;
            this.msg = msg;
        }

        @Override
        public void run() {
            try {
                StringBuilder recordToWrite = new StringBuilder(16384);
                recordToWrite.setLength(0);
                recordToWrite.append("\n============> Rlog @ ").append(new Date()).append(" <================\n");
                recordToWrite.append(msg);
                recordToWrite.append("\n============> END Rlog @ ").append(new Date()).append(" <================\n");
                writer.write(recordToWrite.toString());
                writer.flush();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ SimpleTaskPublisher ] got exception publishing: " + msg + ". Cause: ", t);
            }
        }
    }

    SimpleFileWriter() {
        iDay = 0;
        // initialize tha path
        defaultPath = AppConfig.getProperty(SimpleFileWriter.class.getName() + ".defaultPath", ".");
        checkDate();
    }

    public static final SimpleFileWriter getInstance() {
        return _theInstance;
    }

    private boolean checkDate() {
        calendar.setTimeInMillis(System.currentTimeMillis());

        int cDay = calendar.get(Calendar.DAY_OF_MONTH);
        if (iDay == cDay) {
            return false;
        }

        iDay = cDay;
        StringBuilder sb = new StringBuilder();
        sb.append(calendar.get(Calendar.YEAR)).append("_");
        int iMonth = (calendar.get(Calendar.MONTH) + 1);
        sb.append(((iMonth <= 9) ? "0" : "") + iMonth).append("_");
        sb.append(((iDay <= 9) ? "0" : "") + iDay);

        if (cDate == null) {
            cDate = sb.toString();
            return true;
        }

        if (cDate.equals(sb.toString())) {
            return false;
        }

        cDate = sb.toString();
        return true;
    }

    private void rotate() {
        try {
            if (br != null) {
                try {
                    br.close();// includes also a flush()
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got ex closing streams", t);
                    br = null;
                }
            }
            br = new BufferedWriter(new FileWriter(defaultPath + "/" + cDate, true));
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception ", t);
            br = null;
        }
    }

    public void publishRecord(final MLLogMsg m) {
        try {
            if ((br == null) || checkDate()) {
                rotate();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ SimpleFileWriter ] Exception checking BufferedReader. Cause: ", t);
        }

        if (br != null) {
            executor.submit(new SimpleTaskPublisher(br, m));
            return;
        }
    }
}
