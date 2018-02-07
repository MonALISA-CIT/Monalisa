/*
 * $Id: EPGProcessCMDExecutor.java 7419 2013-10-16 12:56:15Z ramiro $
 * Created on Oct 12, 2010
 */
package lia.Monitor.Store.EPGUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.os.OSType.OSArch;
import lia.util.process.ExternalProcess.ExecutorFinishStatus;
import lia.util.process.ExternalProcess.ExitStatus;
import lia.util.process.ExternalProcessBuilder;
import lia.util.process.ProcessNotifier;
import netx.jnlp.Version;

/**
 * 
 * @author ramiro
 * 
 */
public class EPGProcessCMDExecutor {

    private static final Logger logger = Logger.getLogger(EPGProcessCMDExecutor.class.getName());

    final File pgDirectory;
    final String pgDirAbsPath;
    final String fullPGExecPath;

    final ExternalProcessBuilder procBuilder;

    final String logPrefix;

    final Version version;
    final OSArch osArch;

    EPGProcessCMDExecutor(final String pgDirectoryPath) {
        this(new File(pgDirectoryPath));
    }

    /**
     * 
     * @param pgDirectory
     * @throws IllegalArgumentException if it cannot find the pgDirectory or cannot determine the pgSql file type
     */
    EPGProcessCMDExecutor(final File pgDirectory) {
        // TODO directory checks
        this.pgDirectory = pgDirectory;
        procBuilder = new ExternalProcessBuilder();
        procBuilder.directory(pgDirectory);
        this.logPrefix = "EPGProcExec [" + pgDirectory + "] ";
        pgDirAbsPath = pgDirectory.getAbsolutePath();
        fullPGExecPath = pgDirAbsPath + "/bin/postgres";

        initProcEnv();

        lia.util.os.OSType.OSArch tosArch = null;

        //get arch
        try {
            //the output for this command is similar with
            //postgres: ELF 32-bit LSB executable 80386 Version 1 [FPU], dynamically linked, not stripped
            final ExitStatus exitStatus = execWithOutput(Arrays
                    .asList(new String[] { "/usr/bin/file", fullPGExecPath }));
            if ((exitStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                    && (exitStatus.getExtProcExitStatus() == 0)) {
                final String fOut = exitStatus.getStdOut();

                if (fOut.indexOf("32-bit") >= 0) {
                    tosArch = OSArch.i86;
                } else if (fOut.indexOf("64-bit") >= 0) {
                    tosArch = OSArch.amd64;
                }
                if (tosArch == null) {
                    throw new IllegalArgumentException("Unable to determine file type for: " + fullPGExecPath
                            + " file exit status: " + exitStatus);
                }
            } else {
                throw new IllegalArgumentException("Unable to determine file type for: " + fullPGExecPath
                        + " file exit status: " + exitStatus);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to determine file type for: " + fullPGExecPath, t);
        }

        this.osArch = tosArch;
        if (osArch == null) {
            throw new IllegalArgumentException("Unable to determine file type for: " + fullPGExecPath);
        }

        //get version
        try {
            //the output for this command is similar with
            //postgres (PostgreSQL) 8.3.11
            final ExitStatus exitStatus = execWithOutput(Arrays.asList(new String[] { fullPGExecPath, "--version" }));
            if ((exitStatus.getExecutorFinishStatus() == ExecutorFinishStatus.NORMAL)
                    && (exitStatus.getExtProcExitStatus() == 0)) {
                final String fOut = exitStatus.getStdOut();
                final String[] split = fOut.split("(\\s)+");
                if ((split == null) || (split.length == 0)) {
                    throw new IllegalArgumentException("Unable to determine PG version for: " + fullPGExecPath
                            + "; exit status: " + exitStatus);
                }

                this.version = new Version(split[split.length - 1].trim());
            } else {
                throw new IllegalArgumentException("Unable to determine PG version for: " + fullPGExecPath
                        + "; exit status: " + exitStatus);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Unable to determine PG version for: " + fullPGExecPath, t);
        }

        if (this.version == null) {
            throw new IllegalStateException("Unable to determine PG version for: " + fullPGExecPath);
        }
    }

    public static EPGProcessCMDExecutor newInstance(final File pgDirectory) {
        return new EPGProcessCMDExecutor(pgDirectory);
    }

    public static EPGProcessCMDExecutor newInstance(final String pgDirectoryPath) {
        return newInstance(new File(pgDirectoryPath));
    }

    private void initProcEnv() {
        final Map<String, String> env = procBuilder.environment();

        final String existingPath = env.get("PATH");
        final String newPath = pgDirAbsPath + ":" + pgDirAbsPath + "/bin:/bin:/usr/bin:/sbin:/usr/sbin:"
                + ((existingPath == null) ? "" : existingPath);
        env.put("PATH", newPath);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Existing $PATH=" + existingPath + "\nNew $PATH=" + newPath);
        }

        final String existingLdLib = env.get("LD_LIBRARY_PATH");
        final String newLdLinb = pgDirAbsPath + "/lib:" + existingLdLib;
        env.put("LD_LIBRARY_PATH", newLdLinb);

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Existing $LD_LIBRARY_PATH=" + existingLdLib + "\nNew $LD_LIBRARY_PATH="
                    + newLdLinb);
        }
        procBuilder.redirectErrorStream(true);
    }

    public ExternalProcessBuilder notifier(ProcessNotifier notifier) {
        return procBuilder.notifier(notifier);
    }

    public ProcessNotifier notifier() {
        return procBuilder.notifier();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("EPGProcessCMDExecutor [pgDirectory=").append(pgDirectory).append("]");
        return builder.toString();
    }

    public ExitStatus execWithOutput(List<String> cmd) throws IOException, InterruptedException {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, " executing " + cmd);
        }
        return procBuilder.command(cmd).returnOutputOnExit(true).start().waitFor();
    }

    public ExitStatus execWithOutput(List<String> cmd, ProcessNotifier notifier) throws IOException,
            InterruptedException {
        return procBuilder.command(cmd).returnOutputOnExit(true).start().waitFor();
    }

}
