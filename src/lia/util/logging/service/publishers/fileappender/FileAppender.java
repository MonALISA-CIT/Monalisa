package lia.util.logging.service.publishers.fileappender;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.logging.comm.MLLogMsg;
import lia.util.logging.service.MLLoggerPublisher;

public class FileAppender implements MLLoggerPublisher {

    private static final Logger logger = Logger.getLogger(FileAppender.class.getName());

    String defaultPath = ".";

    private BufferedWriter bw;
    private final Object buffLock;

    private String cDate;
    private final Calendar calendar;
    private int iDay;

    public FileAppender() {
        //initialize tha path
        calendar = Calendar.getInstance();
        buffLock = new Object();
        iDay = 0;
        defaultPath = AppConfig.getProperty(FileAppender.class.getName() + ".logPath", ".");
        checkDate();
    }

    /**
     * 
     * @return true if the current day is different from iDay
     */
    private boolean checkDate() {

        calendar.setTimeInMillis(System.currentTimeMillis());

        int cDay = calendar.get(Calendar.DAY_OF_MONTH);
        //should be enough...cannot change month/year without changing the day
        if (cDay == iDay) {
            return false;
        }

        iDay = cDay;
        StringBuilder sb = new StringBuilder();
        sb.append(calendar.get(Calendar.YEAR)).append("_");
        int cMonth = (calendar.get(Calendar.MONTH) + 1);
        sb.append(((cMonth <= 9) ? "0" : "") + cMonth).append("_");
        sb.append(((iDay <= 9) ? "0" : "") + iDay);

        cDate = sb.toString();
        return true;
    }

    private void closeBuffer() {
        synchronized (buffLock) {
            if (bw != null) {
                try {
                    bw.close();//includes also a flush()
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got ex closing the buffered stream", t);
                } finally {
                    bw = null;
                }
            }
        }
    }

    private void rotate() {
        synchronized (buffLock) {
            closeBuffer();
            try {
                bw = new BufferedWriter(new FileWriter(defaultPath + "/" + cDate, true));
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception creating buffered stream ", t);
                closeBuffer();
            }
        }
    }

    @Override
    public void finish() {

    }

    private char[] formatRecord(MLLogMsg m) {
        try {
            StringBuilder recordToWrite = new StringBuilder(16384);
            recordToWrite.setLength(0);
            recordToWrite.append("\n============> Rlog @ ").append(new Date()).append(" <================\n");
            recordToWrite.append(m);
            recordToWrite.append("\n============> END Rlog @ ").append(new Date()).append(" <================\n");
            char[] buff = new char[recordToWrite.length()];
            recordToWrite.getChars(0, recordToWrite.length(), buff, 0);
            return buff;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception formating MLLogMsg", t);
            return null;
        }
    }

    @Override
    public void publish(MLLogMsg m) {
        try {

            char[] buffToWrite = formatRecord(m);
            if (buffToWrite == null) {
                return;
            }

            boolean shouldRotate = checkDate();

            synchronized (buffLock) {
                if ((bw == null) || shouldRotate) {
                    rotate();
                }

                try {
                    bw.write(buffToWrite);
                    bw.flush();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, " Got IOException writting to stream. Will close buffer.", ioe);
                    closeBuffer();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " Got General Exception writting to stream", t);
                }
            }//sync

        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception publishing MLLogMsg", t);
        }
    }
}
