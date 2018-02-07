/*
 * $Id: ProcessTest.java 6906 2010-10-26 13:38:44Z ramiro $
 * Created on Oct 11, 2010
 */
package lia.util.process.test;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.process.ExternalProcess;
import lia.util.process.ExternalProcesses;
import lia.util.process.ProcessNotifier;
import lia.util.process.ExternalProcess.ExitStatus;
import lia.util.process.ExternalProcessBuilder;

/**
 * @author ramiro
 */
public class ProcessTest {

    final static ExecutorService processExecutor = Executors.newCachedThreadPool();

    final static ScheduledExecutorService monitoringExecutor = Executors.newSingleThreadScheduledExecutor();

    final static AtomicLong totalExecutions = new AtomicLong(0L);

    private final static class TestTask implements Runnable {

        final List<String> cmd;
        final int id;
        
        TestTask(int id, final String... cmd) {
            if(id % 4 == 0) {
                this.cmd = new LinkedList<String>();
                this.cmd.add("/bin/bash");
                this.cmd.add("-c");
                String newCmd = "";
                for(int xx = 0; xx < 10; xx++) {
                    for(final String s : cmd) {
                        newCmd += s + " ";
                    }
                    newCmd += "; sleep 1; ";
                }
                this.cmd.add(newCmd);
            } else {
                this.cmd = Arrays.asList(cmd);
            }
            this.id = id;
        }

        @Override
        public void run() {
            ExternalProcessBuilder epb = new ExternalProcessBuilder(this.cmd);
            epb.redirectErrorStream(true);
            epb.returnOutputOnExit(true);
            if(id % 4 == 0) {
                epb.timeout(2, TimeUnit.SECONDS);
            }

            epb.notifier(new ProcessNotifier() {
                
                @Override
                public void notifyStdOut(ExternalProcess p, List<String> line) {
                    //p.renewTimeout(4, TimeUnit.SECONDS);
                }
                
                @Override
                public void notifyStdErr(ExternalProcess p, List<String> line) {
                    // TODO Auto-generated method stub
                    
                }
                
                @Override
                public void notifyProcessFinished(ExternalProcess p) {
                    // TODO Auto-generated method stub
                    
                }
            });
            
            final long printInterval = TimeUnit.SECONDS.toNanos(10);
            long lastPrint = System.nanoTime();
            long iExecCount = 0L;
            // TODO Auto-generated method stub
            for (;;) {
                try {
                    ExternalProcess eproc = epb.start();
                    ExitStatus exitStat = eproc.waitFor();
                    totalExecutions.incrementAndGet();
                    System.out.println(" Process ID: " + id + " exit status; execution count: " + iExecCount + " ExecStatus: \n" + exitStat);
                    iExecCount++;
                    final long now = System.nanoTime();
                    final long dt = now - lastPrint;
                    if(dt > printInterval) {
                        lastPrint = now;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

        }

    }

    private final static class MonitoringTask implements Runnable {

        long lastStat = System.nanoTime();
        long lastSave = 0L;
        
        @Override
        public void run() {
            final long cExec = totalExecutions.get();
            // TODO Auto-generated method stub
            final long now = System.nanoTime();
            final long dtNanos = now - lastStat;
            final long seconds = TimeUnit.NANOSECONDS.toSeconds(dtNanos);
            System.out.println("\n\n [ " + new Date() + " ] " + (cExec - lastSave) / seconds + " procs/sec counter: " + cExec + "\n\n");
            lastSave = cExec;
            lastStat = now;
        }

    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        try {
            System.out.println(ExternalProcesses.getCmdOutput(Arrays.asList(new String[] {"sleep", "20"}), true, 5, TimeUnit.SECONDS));
        }catch(Throwable t) {
            t.printStackTrace();
        }
//        final int howMany = 1;
//        for (int i = 0; i < howMany; i++) {
//            processExecutor.execute(new TestTask(i, "echo", "" + i));
//        }
//        monitoringExecutor.scheduleWithFixedDelay(new MonitoringTask(), 20, 20, TimeUnit.SECONDS);
//
//        synchronized(ProcessTest.class) {
//            ProcessTest.class.wait();
//        }
    }

}
