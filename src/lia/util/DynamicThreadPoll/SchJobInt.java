package lia.util.DynamicThreadPoll;

public interface  SchJobInt   extends   Comparable<SchJobInt>, java.io.Serializable {   

public void set_exec_time ( long t );
public long get_exec_time();
public void set_repet_time ( long t );
public long get_repet_time();

public void set_max_time ( long t );
public long get_max_time();

public long getExeTime() ;
public void  set_last_time_done( long t );
public abstract Object doProcess() throws Exception ;
public boolean stop();

public boolean canSuspend();

}
