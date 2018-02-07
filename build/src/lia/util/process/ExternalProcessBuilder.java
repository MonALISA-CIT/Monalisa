/*
 * $Id: ExternalProcessBuilder.java 6903 2010-10-26 09:49:52Z ramiro $ 
 * 
 * Created on Oct 10, 2010
 */
package lia.util.process;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lia.util.process.ExternalProcess.ExitStatus;


/**
 * 
 * Helper class around {@link ProcessBuilder} to add support for timeout, 
 * stdout/stderr notifications, extended process exit status, etc 
 * 
 * It supports all {@link ProcessBuilder} tweaks.
 * 
 * Once start is called the class will spawn at least three threads (two for reading the stdout/stderr). 
 * The third one is waiting for the process to finish.
 * 
 * @since ML 1.9.0
 * @author ramiro
 */
public class ExternalProcessBuilder {

    final ProcessBuilder processBuilder;
    private ProcessNotifier notifier;
    
    //shall we wait for a specific ammount of time
    private long timeoutNanos = 0L;
    
    private boolean returnOutputOnExit = false;
    
    public ExternalProcessBuilder(List<String> command) {
        processBuilder = new ProcessBuilder(command);
    }

    public ExternalProcessBuilder(String... command) {
        this(Arrays.asList(command));
    }

    public ExternalProcessBuilder notifier(ProcessNotifier notifier) {
        this.notifier = notifier;
        return this;
    }

    public ProcessNotifier notifier() {
        return this.notifier;
    }

    public ExternalProcessBuilder command(List<String> command) {
        processBuilder.command(command);
        return this;
    }

    /**
     * Sets desired timeout
     * 
     * @param timeout
     * @param unit
     * @return
     */
    public ExternalProcessBuilder timeout(long timeout, TimeUnit unit) {
        this.timeoutNanos = TimeUnit.NANOSECONDS.convert(timeout, unit);
        return this;
    }
    
    /**
     * Returns the current timeout in the unit passed as parameter 
     * 
     * @param unit
     * @return
     */
    public long getTimeout(TimeUnit unit) {
        return unit.convert(timeoutNanos, TimeUnit.NANOSECONDS);
    }
    
    public ExternalProcessBuilder command(String... command) {
        return this.command(Arrays.asList(command));
    }

    public List<String> command() {
        return processBuilder.command();
    }

    public Map<String, String> environment() {
        return processBuilder.environment();
    }

    public File directory() {
        return processBuilder.directory();
    }

    public ExternalProcessBuilder directory(File directory) {
        this.processBuilder.directory(directory);
        return this;
    }

    public boolean redirectErrorStream() {
        return processBuilder.redirectErrorStream();
    }

    public ExternalProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.processBuilder.redirectErrorStream(redirectErrorStream);
        return this;
    }

    /**
     * 
     * @return
     */
    public boolean returnOutputOnExit() {
        return returnOutputOnExit;
    }

    public ExternalProcessBuilder returnOutputOnExit(boolean returnOutputOnExit) {
        this.returnOutputOnExit = returnOutputOnExit;
        return this;
    }
    
    /**
     * 
     * @return
     * @throws IOException if the process cannot start
     */
    public ExternalProcess start() throws IOException {
        return ExternalProcessExecutor.start(this);
    }
    

}
