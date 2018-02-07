/*
 * $Id: DateFileWatchdog.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.util;
import java.io.File;
import java.util.Observable;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.threads.MonALISAExecutors;


/**
 * 
 * This class monitors a specified File for a change ... It is based on the
 * last modification time of the file
 *  
 * @author ramiro
 *  
 */
public final class DateFileWatchdog extends Observable {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(DateFileWatchdog.class.getName());

    private final File fileToWatch;
    private final AtomicLong lastModifiedTime;
    private volatile ScheduledFuture sf;

    private static class DateFileWatchdogCheckTask implements Runnable {

        private final DateFileWatchdog dfw;
        DateFileWatchdogCheckTask(final DateFileWatchdog dfw) {
            this.dfw = dfw;
        }

        private void checkEntry() {
            try {
                final long lmt = dfw.fileToWatch.lastModified();
                if (dfw.lastModifiedTime.get() != lmt) {
                    dfw.lastModifiedTime.set(lmt);
                    dfw.setChanged();
                    dfw.notifyObservers();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " DateFileWatchdog got exception monitoring file: ( " + dfw.fileToWatch + " ) for changes", t);
            }
        }

        public void run() {
            checkEntry();
        }
        
        public String toString() {
            return "DateFileWatchdogCheckTask for: " + dfw;
        }
    }

    /**
     * @param fileName
     * @param howOften -
     *            How often to verify for a change ( in millis )
     * @throws Exception
     */
    private DateFileWatchdog(String fileName, long howOften) throws Exception {
        this(new File(fileName), howOften);
    }

    public File getFile() {
        return fileToWatch;
    }

    /**
     * @param f
     * @param howOften -
     *            How often to verify for a change ( in millis )
     * @throws Exception
     */
    private DateFileWatchdog(File f, long howOften) throws Exception {

        if (f == null) { throw new NullPointerException("Cannot monitor a null File..."); }
        if (!f.exists()) { throw new Exception("The file [ " + f + " ] does not exist!"); }
        if (!f.canRead()) { throw new Exception("The file [ " + f + " ] has now Read acces!"); }

        fileToWatch = f;
        lastModifiedTime = new AtomicLong(fileToWatch.lastModified());
    }

    public static final DateFileWatchdog getInstance(File file, long howOften) throws Exception {
        final DateFileWatchdog dfw = new DateFileWatchdog(file, howOften);
        
        if(howOften <= 500) {
            howOften = 20 * 1000; 
        }
        
        final long initDelay = howOften + new Random(System.currentTimeMillis()).nextInt(20 * 1000);
        
        dfw.sf = MonALISAExecutors.getMLHelperExecutor().scheduleWithFixedDelay(new DateFileWatchdogCheckTask(dfw), initDelay, howOften, TimeUnit.MILLISECONDS);
        return dfw;
    }
    
    public static final DateFileWatchdog getInstance(String fileName, long howOften) throws Exception {
        return getInstance(new File(fileName), howOften);
    }
    
    public void stopIt() {
        sf.cancel(false);
    }
    
    public String toString() {
        return "DateFileWatchdog for: " + fileToWatch;
    }
}
