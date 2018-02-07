/*
 * $Id: OSProccessStatWrapper.java 6921 2010-10-29 09:34:33Z costing $
 */
package lia.util.proc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 *  Wrapper class for info in /proc/[PID]/stat
 *  See proc(5) and /usr/src/linux/fs/proc/array.c
 *  
 *  Note: from arp(7) and other "googled" resources
 *  
 *   [  ...snap... ] jiffies, which is architecture related.  
 *   On the Alpha a jiffy is 1/1024 of a second, on most other architectures it is 1/100s.
 *   
 *   @author ramiro
 */
public final class OSProccessStatWrapper {

    /**
     * The process ID
     */
    public int pid;

    /**
     * The command 
     */
    public String cmd;

    /**
     * UserID
     */
    public int uid;

    /**
     *  Process state
     *  
     *  One character from the string "RSDZTW" where 
     *  R is running, S is sleeping in an interruptible wait, 
     *  D is waiting in uninterruptible disk sleep, Z is zombie, 
     *  T is traced or stopped (on a signal), and W is paging.
     */
    public char state;

    /**
     *  The PID of the parent
     */
    public int ppid;

    /**
     * The Thread Group ID 
     * ( stupid cernsmp kernel & ROCKS & (Non)Scientific Linux & ALL the REDHAT's bullshit )!!!
     * I will move to Unix, or not ... ! 
     */
    public int tgid;
    /**
     *  The number of jiffies that this process has been scheduled in user mode
     */
    public long usrTimeJiffies;

    /**
     * The number of jiffies that this process has been scheduled in kernel mode.
     */
    public long sysTimeJiffies;

    /**
     *  The number of jiffies that this process's waited-for children have been scheduled in user mode.
     */
    public long cusrTimeJiffies;

    /**
     * The number of jiffies that this process's waited-for children have been scheduled in user mode.
     */
    public long csysTimeJiffies;

    /**
     * The time in jiffies the process started after system boot
     */
    public long startTimeJiffies;

    private static final String UID_TAG = "Uid";
    private static final String TGID_TAG = "Tgid";

    private static final char TAB_SEPARATOR = '\t';
    
    private static final String getFirstINumber(final String line) {
        final StringBuilder uidSB = new StringBuilder(5);
        final int len = line.length();
        boolean started = false;
        
        for(int i = 0; i < len; i++) {
            char c = line.charAt(i);
            
            if(started) {
                while(c >= ProcFSUtil.ZERO_CHAR && c <= ProcFSUtil.NINE_CHAR) {
                    uidSB.append(c);
                    if(++i < len) {
                        c = line.charAt(i);
                        continue;
                    }
                    break;
                }
                
                break;
            }
            if(c != TAB_SEPARATOR && c != ProcFSUtil.SPACE_CHAR) continue;
            c = line.charAt(++i);
            while(c == TAB_SEPARATOR || c == ProcFSUtil.SPACE_CHAR) {
                c = line.charAt(++i);
            }
            started = true;
            uidSB.append(c);
        }

        return uidSB.toString();
    }

    public OSProccessStatWrapper(String[] splits) throws Exception {

        uid = -1;
        tgid = -1;

        pid                 = Integer.parseInt(splits[0]);
        cmd                 = splits[1];
        state               = splits[2].charAt(0);
        ppid                = Integer.parseInt(splits[3]);
        usrTimeJiffies      = Long.parseLong(splits[13]);
        sysTimeJiffies      = Long.parseLong(splits[14]);
        cusrTimeJiffies     = Long.parseLong(splits[15]);
        csysTimeJiffies     = Long.parseLong(splits[16]);
        startTimeJiffies    = Long.parseLong(splits[21]);

        final BufferedReader br = new BufferedReader(new FileReader(new File(new StringBuilder(20).append(ProcFSUtil.PROC_FILE_NAME).append(pid).append(ProcFSUtil.STATUS_FILE_NAME).toString())), 2048);

        String procPIDstatusLine = null;

        while ( (procPIDstatusLine = br.readLine()) != null) {

            if (uid == -1) {
                if(procPIDstatusLine.indexOf(UID_TAG) != -1) {
                    uid = Integer.parseInt(getFirstINumber(procPIDstatusLine));
                    if(tgid != -1) {
                        break;
                    }

                    continue;
                }
            }

            if (tgid == -1 ) {
                if(procPIDstatusLine.indexOf(TGID_TAG) != -1) {
                    tgid = Integer.parseInt(getFirstINumber(procPIDstatusLine));
                    if(uid != -1) break;
                }
            }

        }

        if(uid < 0) throw new Exception("No such userID");

        br.close();
    }

    @Override
	public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("PID: ").append(pid).append(" [ ").append(uid).append(" ] ").append(cmd);
        sb.append(" '").append(state).append("' ");
        sb.append(" PPID: ").append(ppid); 
        sb.append(" TGID: ").append(tgid);
        sb.append(" usr_jiffies: ").append(usrTimeJiffies); 
        sb.append(" sys_jiffies: ").append(sysTimeJiffies); 
        sb.append(" cusr_jiffies: ").append(cusrTimeJiffies); 
        sb.append(" csys_jiffies: ").append(csysTimeJiffies); 
        sb.append(" start_jiffies: ").append(startTimeJiffies); 

        return sb.toString();
    }
}
