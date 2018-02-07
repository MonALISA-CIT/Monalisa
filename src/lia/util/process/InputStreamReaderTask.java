/*
 * $Id: InputStreamReaderTask.java 7419 2013-10-16 12:56:15Z ramiro $
 * Created on Oct 10, 2010
 */
package lia.util.process;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.Utils;

/**
 * 
 * @author ramiro
 */
abstract class InputStreamReaderTask implements Callable<String> {

    private static final Logger logger = Logger.getLogger(InputStreamReaderTask.class.getName());

    final BufferedReader reader;

    final boolean saveLog;

    protected ExternalProcess procWrapper;

    InputStreamReaderTask(InputStreamReader isr, boolean saveLog, ExternalProcess procWrapper) {
        this.reader = new BufferedReader(isr);
        this.saveLog = saveLog;
        this.procWrapper = procWrapper;
    }

    InputStreamReaderTask(InputStream in, boolean saveLog, ExternalProcess procWrapper) {
        this(new InputStreamReader(in), saveLog, procWrapper);
    }

    @Override
    public String call() throws Exception {
        try {

            //            if(logger.isLoggable(Level.FINER)) {
            //                logger.log(Level.FINER, "ISR started. entering barrier.");
            //            }

            //            if(barrier != null) {
            //                for(;;) {
            //                    if(procWrapper.timedOut.get() || procWrapper.finished.get()) {
            //                        break;
            //                    }
            //                    
            //                    try {
            //                        barrier.await(10, TimeUnit.SECONDS);
            //                        break;
            //                    } catch (InterruptedException e) {
            //                        if(procWrapper.timedOut.get()) {
            //                            //normal signaling
            //                            if(logger.isLoggable(Level.FINE)) {
            //                                logger.log(Level.FINE, " My process " + procWrapper + " timed out. ");
            //                            }
            //                        } else {
            //                            logger.log(Level.WARNING, " Got interrupted exception though process did not timed out ");
            //                        }
            //                        break;
            //                    } catch (BrokenBarrierException e) {
            //                        logger.log(Level.WARNING, " Got BrokenBarrierException though process did not timed out ");
            //                        break;
            //                    } catch (TimeoutException e) {
            //                        if(logger.isLoggable(Level.FINE)) {
            //                            logger.log(Level.FINE, " My process " + procWrapper + " timed out. ");
            //                        }
            //                    }
            //                }
            //            }
            //            
            //            if(logger.isLoggable(Level.FINER)) {
            //                logger.log(Level.FINER, " ISR started. exiting barrier.");
            //            }

            StringBuilder sb = new StringBuilder();
            for (;;) {
                final String line = reader.readLine();
                if (line == null) {
                    try {
                        streamClosed();
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Exception while notify EOF. Cause:", t);
                    }
                    return (sb.length() == 0) ? "" : sb.toString();
                }

                if (saveLog) {
                    sb.append(line).append("\n");
                }
                try {
                    newLines(line);
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Exception while notify line: '" + line + "'. Cause:", t);
                }
            }
        } finally {
            Utils.closeIgnoringException(reader);
        }
    }

    abstract void newLines(String... lines);

    abstract void streamClosed();
}
