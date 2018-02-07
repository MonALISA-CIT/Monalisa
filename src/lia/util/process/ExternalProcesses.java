/*
 * $Id: ExternalProcesses.java 7186 2011-06-21 20:09:09Z ramiro $
 * 
 * Created on Oct 26, 2010
 */
package lia.util.process;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lia.util.process.ExternalProcess.ExecutorFinishStatus;
import lia.util.process.ExternalProcess.ExitStatus;


/**
 * 
 *
 * @author ramiro
 */
public class ExternalProcesses {
    
    public static String getCmdOutput(String command, boolean redirectOutput, long timeout, TimeUnit unit) throws InterruptedException, IOException {
        return getCmdOutput(Arrays.asList(command), redirectOutput, timeout, unit);
    }

    public static String getCmdOutput(List<String> command, boolean redirectOutput, long timeout, TimeUnit unit) throws InterruptedException, IOException {
        final ExternalProcessBuilder pBuilder = new ExternalProcessBuilder(command);
        pBuilder.returnOutputOnExit(true);
        pBuilder.timeout(timeout, unit);
        pBuilder.redirectErrorStream(redirectOutput);
        ExitStatus exitStatus = pBuilder.start().waitFor();
        if(exitStatus.executorFinishStatus != ExecutorFinishStatus.NORMAL) {
            throw new IOException("Process executor finished with errors: " + exitStatus.executorFinishStatus + " for command: " + command.toString() + " | ExitStatus from process: " + exitStatus);
        }
        return exitStatus.stdOut;
    }
}
