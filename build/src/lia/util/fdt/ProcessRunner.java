package lia.util.fdt;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.MLProcess;

/**
 * Helper class to run external process in background and get a notification when the process exits
 */
public class ProcessRunner {

    BackgroundProcess process = null;
    Thread tBackroundRunner = null; // on demand creation
    String sCmd = null;

    public void startProcess(String javaCmd) throws IOException {
        stopProcess();
        // TODO: use FutureTask from 1.5 instead of creating new Thread each time
        this.process = new BackgroundProcess(javaCmd);
        tBackroundRunner = new Thread(this.process);
        this.sCmd = javaCmd;
        tBackroundRunner.start();
    }

    public void stopProcess() throws IOException {
        if ((tBackroundRunner != null) && tBackroundRunner.isAlive()) {
            tBackroundRunner.interrupt();
            try {
                // wait for the sub-process to be destroyed
                tBackroundRunner.join();
            } catch (InterruptedException ie) {
                // ignore
            }
        }
        tBackroundRunner = null;
    }

    /**
     * @return the exit code the the bg'ed process -1: mean still running
     */
    public int exitCode() {
        return process == null ? 0 : this.process.exitCode;
    }

    public boolean isRunning() {
        if (this.tBackroundRunner == null) {
            return false;
        }
        return this.exitCode() < 0;
    }

    /**
     * Background thread used to run FDT Client
     */
    static class BackgroundProcess implements Runnable {
        /** Logger used by this class */
        private static final Logger logger = Logger.getLogger(BackgroundProcess.class.getName());
        private final String cmd;
        private Process process;
        private int exitCode = -1;

        private static final String[] SYS_EXTENDED_BIN_PATH = new String[] { "PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin" };

        public BackgroundProcess(String cmd) {
            this.cmd = cmd;
        }

        @Override
        public void run() {
            try {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Starting process: " + cmd);
                }
                process = MLProcess.exec(cmd, -1);
                // DON'T consume the streams (we redirect them in files)
                exitCode = process.waitFor();
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "CMD (" + cmd + ") terminated normally.  RC: " + exitCode);
                }
            } catch (Exception e) {
                try {
                    exitCode = process.exitValue();
                } catch (Throwable t) {
                    exitCode = 99;// INTERRUPTED by Application
                    process.destroy();
                }
            }
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Background process [" + cmd + "] terminated with rc:" + exitCode);
            }
            this.process = null;
        }

        /*public boolean isRunning() {
        	return this.exitCode > 0;
        }*/
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ProcessRunner pr = new ProcessRunner();
        System.out.println(System.getenv("PATH"));
        Long.getLong("a");
        pr.startProcess("echo 'Hello World' &>/tmp/log.out ");
        Thread.sleep(1000);
        System.out.println(pr.isRunning());

        Process process = MLProcess.exec("java -version", -1);
        // DON'T consume the streams (we redirect them in files)
        System.out.println("CMD RC:" + process.waitFor());

    }

    public String getSCmd() {
        return this.sCmd;
    }

    public void setSCmd(String cmd) {
        this.sCmd = cmd;
    }
}
