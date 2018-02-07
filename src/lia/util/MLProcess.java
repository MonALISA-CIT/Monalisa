/*
 * Created on Jun 28, 2004
 */
package lia.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

public class MLProcess {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLProcess.class.getName());

    private static MLProcessWatchdog mlpw = null;
    private static long DEFAULT_WAIT_TIME;
    private static final String CMD_WRAPPER;
    
    static {
        mlpw = MLProcessWatchdog.getInstance();
        //in seconds
        try {
            DEFAULT_WAIT_TIME = Long.valueOf(AppConfig.getProperty("lia.util.MLProcess.DEFAULT_WAIT_TIME").trim()).longValue()*1000;
        }catch(Throwable t){
            DEFAULT_WAIT_TIME = 3*60*1000;
        }
        
        String cWrapper = null;
        
        try {
        	String mlHOME = null;
            try {
            	 mlHOME = AppConfig.getProperty("MonaLisa_HOME", "");
            } catch (Throwable t) {
                mlHOME = "";
            }
            try {
            	cWrapper = AppConfig.getProperty("lia.util.MLProcess.CMD_WRAPPER", mlHOME + "/Service/CMD/cmd_run.sh");
            } catch (Throwable t) {
            	cWrapper = mlHOME + "/Service/CMD/cmd_run.sh";
            }
            File cwf = new File(cWrapper); 
            if(!cwf.exists() || !cwf.canRead()) {
            	cWrapper = null;
            }
        } catch (Throwable t) {
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ MLProcess ] Cannot use command wrapper", t);
            }
            cWrapper = null;
        }
        
       CMD_WRAPPER = cWrapper;
        
       logger.log(Level.INFO, " [ MLProcess ] using CMD_WRAPPER [" + CMD_WRAPPER + "]");
    }

    /**
     * @see Runtime.exec(String) 
     */
    public static Process exec(String cmd) throws IOException {
        return exec(cmd, DEFAULT_WAIT_TIME, true);
    }

    public static Process exec(String cmd, boolean shouldSleep) throws IOException {
        return exec(cmd, DEFAULT_WAIT_TIME, shouldSleep);
    }

    /**
     * @see exec(String cmd)
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmd, long maxDelay) throws IOException {
        return exec(cmd, null, maxDelay, true);
    }
    
    public static Process exec(String cmd, long maxDelay, boolean shouldSleep) throws IOException {
        return exec(cmd, null, maxDelay, shouldSleep);
    }

    /**
     * @see Runtime.exec(String, String[]) 
     */
    public static Process exec(String cmd, String envp[]) throws IOException {
        return exec(cmd, envp, DEFAULT_WAIT_TIME, true);
    }

    public static Process exec(String cmd, String envp[], boolean shouldSleep) throws IOException {
        return exec(cmd, envp, DEFAULT_WAIT_TIME, shouldSleep);
    }

    /**
     * @see exec(String cmd, String[] envp)
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmd, String envp[], long maxDelay) throws IOException {
        return exec(cmd, envp, null, maxDelay, true);
    }

    public static Process exec(String cmd, String envp[], long maxDelay, boolean shouldSleep) throws IOException {
        return exec(cmd, envp, null, maxDelay, shouldSleep);
    }

    /**
     * @see Runtime.exec(String, String[], File)
     */
    public static Process exec(String cmd, String envp[], File dir) throws IOException {
        return exec(cmd, envp, dir, DEFAULT_WAIT_TIME, true);
    }

    public static Process exec(String cmd, String envp[], File dir, boolean shouldSleep) throws IOException {
        return exec(cmd, envp, dir, DEFAULT_WAIT_TIME, shouldSleep);
    }
    /**
     * @see exec(String cmd, String[] envp, File dir)
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmd, String envp[], File dir, long maxDelay) throws IOException {
        //ensure that no process left STDOUT or STDERR OPEN if the command starts
        //a daemon which lefts the STDOUT or STDERR opened
        return exec(new String[]{"/bin/sh", "-c", cmd}, envp, dir, maxDelay, true);
//        return exec(new String[]{cmd}, envp, dir, maxDelay, true);
    }

    public static Process exec(String cmd, String envp[], File dir, long maxDelay, boolean shouldSleep) throws IOException {
        return exec(new String[]{"/bin/sh", "-c", cmd}, envp, dir, maxDelay, shouldSleep);
//        return exec(new String[]{cmd}, envp, dir, maxDelay, shouldSleep);
    }
    
    /**
     * @see Runtime.exec(String[]) 
     */
    public static Process exec(String cmdarray[]) throws IOException {
        return exec(cmdarray, DEFAULT_WAIT_TIME, true);
    }

    public static Process exec(String cmdarray[], boolean shouldSleep) throws IOException {
        return exec(cmdarray, DEFAULT_WAIT_TIME, shouldSleep);
    }
    /**
     * @see exec(String cmdarray[])
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmdarray[], long maxDelay) throws IOException {
        return exec(cmdarray, null, maxDelay, true);
    }
    
    public static Process exec(String cmdarray[], long maxDelay, boolean shouldSleep) throws IOException {
        return exec(cmdarray, null, maxDelay, shouldSleep);
    }
    
    /**
     * @see Runtime.exec(String[], String[]) 
     */
    public static Process exec(String cmdarray[], String envp[]) throws IOException {
        return exec(cmdarray, envp, DEFAULT_WAIT_TIME, true);
    }

    public static Process exec(String cmdarray[], String envp[], boolean shouldSleep) throws IOException {
        return exec(cmdarray, envp, DEFAULT_WAIT_TIME, shouldSleep);
    }
    
    /**
     * @see exec(String cmdarray[], String envp[])
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmdarray[], String envp[], long maxDelay) throws IOException {
        return exec(cmdarray, envp, null, maxDelay, true);
    }

    public static Process exec(String cmdarray[], String envp[], long maxDelay, boolean shouldSleep) throws IOException {
        return exec(cmdarray, envp, null, maxDelay, shouldSleep);
    }
    
    /**
     * @see  Runtime.exec(String cmdarray[], String envp[], File dir) 
     */
    public static Process exec(String cmdarray[], String envp[], File dir) throws IOException {
        return exec(cmdarray, envp, dir, DEFAULT_WAIT_TIME, true);
    }
        
    public static Process exec(String cmdarray[], String envp[], File dir, boolean shouldSleep) throws IOException {
        return exec(cmdarray, envp, dir, DEFAULT_WAIT_TIME, shouldSleep);
    }
    
    /**
     * @see exec(String cmdarray[], String envp[], File dir)
     * @param maxDelay - delay in milliseconds to wait for the Process to finish
     */
    public static Process exec(String cmdarray[], String envp[], File dir, long maxDelay) throws IOException {
        return exec(cmdarray, envp, dir, maxDelay, true);
    }
    
    public static Process exec(String cmdarray[], String envp[], File dir, long maxDelay, boolean shouldSleep) throws IOException {
        if (cmdarray == null || cmdarray.length == 0) throw new IOException("Command cannot be null");
        
        StringBuilder newCmd = new StringBuilder();
        for(int i=0; i<cmdarray.length; i++) {
            newCmd.append("\"").append(cmdarray[i]).append("\" ");
        }
        
        newCmd.append("; retValue=$? ; echo OK &>/dev/null ; echo OK >&2 &>/dev/null");
        newCmd.append(shouldSleep ? " ; sleep 1 &>/dev/null":"");
        newCmd.append("; exit $retValue");

        if(logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            sb.append(" [ MLPROCESS ] Executing CMD[]: {").append(newCmd.toString() );
            sb.append("} WAIT [ ");
            
            if(maxDelay < 0) {
                sb.append("FOREVER");
            } else if (maxDelay == 0) {
                sb.append(DEFAULT_WAIT_TIME);
            } else {
                sb.append(maxDelay);
            }
            sb.append(" ] ms");
            
            logger.log(Level.FINEST, sb.toString());
        }
        
        boolean doNotUseWrapper = AppConfig.getb("lia.util.MLProcess.doNotUseWrapper", false);
        String[] newCmdArray;
        
        if(logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "CMD_WRAPPER [" + CMD_WRAPPER + "] doNotUseWrapper[" + doNotUseWrapper + "]");
        }
        
        if(CMD_WRAPPER != null && !doNotUseWrapper){
            newCmdArray = new String[]{CMD_WRAPPER, newCmd.toString()};
        }else{
            newCmdArray = new String[]{"/bin/sh", "-c", newCmd.toString()};
        }
        
        Process p = Runtime.getRuntime().exec(newCmdArray, envp, dir);
        
        if(mlpw == null) {
            mlpw = MLProcessWatchdog.getInstance();
        }

        if(maxDelay < 0) {
            return p;
        }
        
        if ( p != null && mlpw != null) {
            if (maxDelay == 0) {
                mlpw.add(p, NTPDate.currentTimeMillis() + DEFAULT_WAIT_TIME);
            } else {
                mlpw.add(p, NTPDate.currentTimeMillis() + maxDelay);
            }
        }

        return p;
    }

    
    public static final void main(String[] args) throws Exception {
        
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(
                ("handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = FINEST\n" +
                "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n" +
                ".level = FINEST" +
                "").getBytes()
                ));
        
        String[] cmds = {
                "echo mimi", 
                "echo traalallala ; echo asa", 
                "echo traalallala ; echo sasda &> ~/myfis_sleep &; echo asa"};
        
        for(int i=0; i<cmds.length; i++) {
            System.out.println("--> Executing " + cmds[i]);
            printProcOutput(MLProcess.exec(cmds[i]));
//            System.out.println("\n" + AppUtils.getOutput(cmds[i]) + "\n");
            System.out.println("--> END Executing " + cmds[i] + " \n\n");
            System.out.flush();
        }
    }
    
    private static final void printProcOutput(final Process process) throws Exception {
       BufferedReader br = null;
       InputStreamReader isr = null;
       
       try {
           isr = new InputStreamReader(process.getInputStream());
           br = new BufferedReader(isr);
           String line = br.readLine();
           process.waitFor();
           
           int i = 1;
           System.out.println(" Line: " + line);
           while(line != null) {
               System.out.println(" [ " + i++ + "] > " + line);
               System.out.flush();
               line = br.readLine();
           }
           
       } finally {
           if(isr != null){
               try {
                   isr.close();
               }catch(Throwable ignore){};
           }
           
           if(br != null){
               try {
                   br.close();
               }catch(Throwable ignore){};
           }
       }
    }
}
