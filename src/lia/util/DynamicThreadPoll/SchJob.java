/**
 *  Generic class used to define  a scheduled job
 * 
 */

package lia.util.DynamicThreadPoll;

import java.util.concurrent.atomic.AtomicInteger;

abstract public class SchJob implements java.io.Serializable, SchJobInt {
    /**
     * 
     */
    private static final long serialVersionUID = 3713877051346990592L;
    
    private static final AtomicInteger ID = new AtomicInteger(0);
    public final int myID;
    protected volatile long exec_time;
    protected volatile long repet_time;
    protected volatile long max_time;
    protected volatile long done;
    protected volatile long eff_time;
    protected volatile boolean canSuspend;

    public SchJob() {
        canSuspend = true;
        myID = ID.getAndIncrement();
        exec_time = System.currentTimeMillis();
        max_time = -1;
        eff_time = -1;
    }

    public long getExeTime() {
        return eff_time;
    }
    public void set_exec_time(long t) {
        exec_time = t;
    }
    public long get_exec_time() {
        return exec_time;
    }

    public void set_repet_time(long t) {
        repet_time = t;
    }
    public long get_repet_time() {
        return repet_time;
    }

    public void set_max_time(long t) {
        max_time = t;
    }
    public long get_max_time() {
        return max_time;
    }

    public void set_last_time_done(long t) {
        done = t;
    }

    public abstract Object doProcess() throws Exception;

    public String toString() {
        return " Job id= " + myID ;
    }

    //abstract public SchJob duplicate();

    public int compareTo(SchJobInt c) {
        if(this == c) {
            return 0;
        }
        
        if(c instanceof SchJob) {
            final SchJob rmc = (SchJob)c;
            final long d = exec_time - rmc.exec_time; 
            if (d > 0)
                return 1;
            if (d < 0)
                return -1;
            
            //it's almost impossible to have a such a tight
            return (myID - rmc.myID);
        }
        
        return -1;
    }

    public boolean canSuspend() { return canSuspend; }
    
    public boolean stop() {
        return true;
    }

}
