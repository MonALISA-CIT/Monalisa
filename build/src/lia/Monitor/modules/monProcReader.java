/*
 * $Id: monProcReader.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.cmdExec;

/**
 * The base class for all monProc* modules
 * 
 * @author ramiro
 */
public abstract class monProcReader extends cmdExec implements MonitoringModule {
    /**
     * @since ML1.9.0
     */
    private static final long serialVersionUID = 4270223985219638998L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monProcReader.class.getName());

    /**
     * File names from /proc
     */
    protected String PROC_FILE_NAMES[];

    /**
     * The readers that were opened
     */
    protected FileReader fileReaders[];

    /**
     * Opened buffered readers
     */
    protected BufferedReader bufferedReaders[];

    /**
     * @param TaskName
     */
    public monProcReader(String TaskName) {
        super(TaskName);
    }

    /**
     * 
     */
    public monProcReader() {
        super();
    }

    private void createReaders() throws Exception {
        cleanup();
        if (PROC_FILE_NAMES == null) {
            throw new Exception(" PROC_FILE_NAMES is null");
        }

        if ((bufferedReaders == null) || (bufferedReaders.length != PROC_FILE_NAMES.length)) {
            bufferedReaders = new BufferedReader[PROC_FILE_NAMES.length];
        }

        if ((fileReaders == null) || (fileReaders.length != PROC_FILE_NAMES.length)) {
            fileReaders = new FileReader[PROC_FILE_NAMES.length];
        }

        for (int i = 0; i < PROC_FILE_NAMES.length; i++) {
            try {
                if (PROC_FILE_NAMES[i] != null) {
                    fileReaders[i] = new FileReader(PROC_FILE_NAMES[i]);
                    bufferedReaders[i] = new BufferedReader(fileReaders[i]);
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ monProcReader ] [ HANDLED ] PROC_FILE_NAMES[" + i + "] is null");
                    }
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ monProcReader ] [ HANDLED ] Got exc creating Readers [ " + i + " ] :- "
                            + PROC_FILE_NAMES[i], t);
                }
            }
        }
    }

    @Override
    public Object doProcess() throws Exception {
        try {
            resetReaders();
            return processProcModule();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ monProcReader ] [ HANDLED ]Got exc doProcess()", t);
                throw new Exception(t);
            }
        } finally {
            cleanup();
        }

        return null;
    }

    /**
     * @return the values from /proc
     * @throws Exception
     */
    protected abstract Object processProcModule() throws Exception;

    /**
     * @throws Exception
     */
    protected void resetReaders() throws Exception {
        cleanup();
        createReaders();
    }

    @Override
    public void cleanup() {
        if (bufferedReaders != null) {
            for (int i = 0; i < bufferedReaders.length; i++) {
                try {
                    if (bufferedReaders[i] != null) {
                        bufferedReaders[i].close();
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER,
                                " [ monProcReader ] [ HANDLED ]Got exception closing buffered reader [ " + i + " ]", t);
                    }
                } finally {
                    bufferedReaders[i] = null; //let GC do the job
                }
            }
        }//if bufferedReaders
        if (fileReaders != null) {
            for (int i = 0; i < fileReaders.length; i++) {
                try {
                    if (fileReaders[i] != null) {
                        fileReaders[i].close();
                    }
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, " [ monProcReader ] [ HANDLED ]Got exception closing file reader [ "
                                + i + " ]", t);
                    }
                } finally {
                    fileReaders[i] = null; //let GC do the job
                }
            }
        }
    }
}
