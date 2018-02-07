/*
 * $Id: StdoutReaderTask.java 6880 2010-10-13 06:34:23Z ramiro $
 *
 * Created on Oct 11, 2010
 *
 */
package lia.util.process;

import java.util.Arrays;



/**
 *
 * @author ramiro
 *
 */
public class StdoutReaderTask extends InputStreamReaderTask {

    final boolean hasNotifier;
    final ProcessNotifier notifier;
    final ExternalProcess externalProcess;
    /**
     * @param isr
     * @param saveLog
     */
    public StdoutReaderTask(ExternalProcess externalProcess) {
        super(externalProcess.p.getInputStream(), externalProcess.saveOutput, externalProcess);
        this.hasNotifier = (externalProcess.notifier != null);
        this.notifier = externalProcess.notifier;
        this.externalProcess = externalProcess;
    }

    /* (non-Javadoc)
     * @see lia.util.process.InputStreamReaderTask#newLine(java.lang.String)
     */
    @Override
    void newLines(String... lines) {
        if(hasNotifier) {
            notifier.notifyStdOut(externalProcess, Arrays.asList(lines));
        }
    }

    /* (non-Javadoc)
     * @see lia.util.process.InputStreamReaderTask#streamClosed()
     */
    @Override
    void streamClosed() {
    }

}
