import lia.Monitor.monitor.AppConfig;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Hashtable;

public class MGangliaMetrics {
 /** Logger Name */
 private static final transient String COMPONENT = "lia.Monitor.JiniClient.Store";
 /** The Logger */ 
 private static final transient Logger logger = Logger.getLogger(COMPONENT);

 public static final int XDR_STRING     = 0;
 public static final int XDR_INT16      = 1;
 public static final int XDR_INT32      = 2;
 public static final int XDR_INT64      = 3;
 public static final int XDR_REAL32     = 4;
 public static final int XDR_REAL64     = 5;
 
 //ganglia specific
 public static final int MIN_RESERVED_KEY   = 25;
 public static final int MAX_RESERVED_KEY   = 28;

 private static final String gDefaultMetrics[] = 
  {
   "user_defined",
   "cpu_num",
   "cpu_speed",
   "mem_total",
   "swap_total",
   "boottime",
   "sys_clock",
   "machine_type",
   "os_name",
   "os_release",

   "cpu_user",
   "cpu_nice",
   "cpu_system",
   "cpu_idle",
   "cpu_aidle",
   "load_one",
   "load_five",
   "load_fifteen",
   "proc_run",
   "proc_total",
   "mem_free",
   "mem_shared",
   "mem_buffers",
   "mem_cached",
   "swap_free",
   /* internal.. ignore ... just for index!*/
   "gexec",
   "heartbeat",
   "mtu",
   "location"
  };
 private static final String gSolarisMetrics[] = 
  {
   "cpu_wio",
   "bread_sec",
   "bwrite_sec",
   "lread_sec",
   "lwrite_sec",
   "phread_sec",
   "phwrite_sec",
   "rcache",
   "wcache"
  };

 private static final String gLinuxMetrics[] = 
  {
   "bytes_in",
   "bytes_out",
   "pkts_in",
   "pkts_out",
   "disk_total",
   "disk_free",
   "part_max_used"
  };

 private static final String gHPUXMetrics[] = 
  {
   "cpu_intr",
   "cpu_ssys",
   "cpu_wait",
   "cpu_arm",
   "cpu_rm",
   "cpu_avm",
   "cpu_vm"
  };
 public static String[] gMetrics = null;
 static {
    boolean solaris = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.Ganglia.SOLARIS", "false")).booleanValue();
    boolean hpux = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.Ganglia.SOLARIS", "false")).booleanValue();
    boolean linux = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.Ganglia.SOLARIS", "true")).booleanValue();
    int gMetricsLength = gDefaultMetrics.length;
    if( solaris ) {
        gMetricsLength += gSolarisMetrics.length;
    }
    if( linux ) {
        gMetricsLength += gLinuxMetrics.length;
    }
    if( hpux ) {
        gMetricsLength += gHPUXMetrics.length;
    }
    
    gMetrics = new String[gMetricsLength];
    
    //default metrics
    System.arraycopy(gDefaultMetrics, 0, gMetrics, 0, gDefaultMetrics.length);

    int cPos = gDefaultMetrics.length;

    //solaris specific
    if ( solaris ) {
        System.arraycopy(gSolarisMetrics, 0, gMetrics, cPos, gSolarisMetrics.length);
        cPos += gSolarisMetrics.length;
    }

    if ( linux ) {
        System.arraycopy(gLinuxMetrics, 0, gMetrics, cPos, gLinuxMetrics.length);
        cPos += gLinuxMetrics.length;
    }

    if ( hpux ) {
        System.arraycopy(gHPUXMetrics, 0, gMetrics, cPos, gHPUXMetrics.length);
        cPos += gHPUXMetrics.length;
    }
    
    if ( cPos != gMetrics.length ) {
        logger.log(Level.SEVERE, "Something was changed ... in the metrics table ?! ");
    } else {
        logger.log(Level.INFO, "Ganglia Metrics no: " + gMetrics.length);
    }
 }

 public Hashtable g2mlh = new Hashtable();
 MGangliaMetrics() {
     g2mlh.put( "cpu_num", new Ganglia2MLMetric( "cpu_num", "NoCPUs", XDR_INT16 ) );
     g2mlh.put( "cpu_user", new Ganglia2MLMetric( "cpu_user", "CPU_usr", XDR_REAL32 ) );
     g2mlh.put( "cpu_nice", new Ganglia2MLMetric( "cpu_nice", "CPU_nice", XDR_REAL32 ) );
     g2mlh.put( "cpu_system", new Ganglia2MLMetric( "cpu_system", "CPU_sys", XDR_REAL32 ) );
     g2mlh.put( "cpu_aidle", new Ganglia2MLMetric( "cpu_aidle", "CPU_idle", XDR_REAL32 ) );
     g2mlh.put( "load_one", new Ganglia2MLMetric( "load_one", "Load1", XDR_REAL32 ) );
     g2mlh.put( "load_five", new Ganglia2MLMetric( "load_five", "Load5", XDR_REAL32 ) );
     g2mlh.put( "load_fifteen", new Ganglia2MLMetric( "load_fifteen", "Load15", XDR_REAL32 ) );
     g2mlh.put( "mem_free", new Ganglia2MLMetric( "mem_free", "MEM_Free", XDR_INT32 ) );
     g2mlh.put( "proc_run", new Ganglia2MLMetric( "proc_run", "PROC_Run", XDR_INT32 ) );
     g2mlh.put( "proc_total", new Ganglia2MLMetric( "proc_total", "Proc_Total", XDR_INT32 ) );
     g2mlh.put( "bytes_in", new Ganglia2MLMetric( "bytes_in", "TotalIO_Rate_IN", XDR_REAL32 ) );
     g2mlh.put( "bytes_out", new Ganglia2MLMetric( "bytes_out", "TotalIO_Rate_OUT", XDR_REAL32 ) );
 }
}
