/**
 * 
 */
package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.awt.Color;
import java.util.HashMap;

/**
 * @author florinpop
 *
 */
public final class OSGConstants {
	
    /** User Params */
	public static HashMap UPARAMS = new HashMap();
	static{
		UPARAMS.put("nodes", Integer.valueOf(1));
		UPARAMS.put("cpu", Integer.valueOf(0));
		UPARAMS.put("io", Integer.valueOf(0));
		UPARAMS.put("cputime", Integer.valueOf(0));
		UPARAMS.put("jobs", Integer.valueOf(0));
		UPARAMS.put("fjobs", Integer.valueOf(0));
	}
	
	/** Nodes constants */
	public static final Long LTOTAL_NODES = Long.valueOf(0x1L);
	public static final ParamProperties TOTAL_NODES	= 
		new ParamProperties("Nodes", new Color(0, 0, 51));
	
    public static final Long LFREE_NODES = Long.valueOf(0x2L);
	public static final ParamProperties FREE_NODES	= 
		new ParamProperties("Free", Color.yellow);
	
    public static final Long LBUSY_NODES = Long.valueOf(0x4L);
	public static final ParamProperties BUSY_NODES	= 
		new ParamProperties("Busy", Color.green);
	
	public static HashMap HNODES = new HashMap();
	static{
		HNODES.put(LTOTAL_NODES, TOTAL_NODES);
		HNODES.put(LFREE_NODES, FREE_NODES);
		HNODES.put(LBUSY_NODES, BUSY_NODES);
	}
	
	/** Cpu constants */
    public static final Long LCPU_SYS = Long.valueOf(0x20L);
	public static final ParamProperties CPU_SYS = 
		new ParamProperties("CPU Sys", new Color(255, 179, 0));
	
    public static final Long LCPU_USR = Long.valueOf(0x40L);
	public static final ParamProperties CPU_USR = 
		new ParamProperties("CPU Usr", new Color(161, 37, 255));
	
    public static final Long LCPU_IDLE = Long.valueOf(0x80L);
	public static final ParamProperties CPU_IDLE = 
		new ParamProperties("CPU Idle", new Color(0, 255, 251));
	
    public static final Long LCPU_ERR = Long.valueOf(0x100L);
	public static final ParamProperties CPU_ERR = 
		new ParamProperties("CPU Err", new Color(255, 20, 20));
	
	public static HashMap HCPU = new HashMap();
	static{
		HCPU.put(LCPU_SYS, CPU_SYS);
		HCPU.put(LCPU_USR, CPU_USR);
		HCPU.put(LCPU_IDLE, CPU_IDLE);
		HCPU.put(LCPU_ERR, CPU_ERR);
	}
	
	/** IO constants */
    public static final Long LIO_IN = Long.valueOf(0x800L);
	public static final ParamProperties IO_IN = 
		new ParamProperties("TotalIO Rate In", new Color(200, 200,  10));
	
    public static final Long LIO_OUT = Long.valueOf(0x1000L);
	public static final ParamProperties IO_OUT = 
		new ParamProperties("TotalIO Rate Out", new Color( 10, 200, 200));

	public static HashMap HIO = new HashMap();
	static{
		HIO.put(LIO_IN, IO_IN);
		HIO.put(LIO_OUT, IO_OUT);
	}
	
	/** JOBS constants */
    public static final Long LRUNNING_JOBS = Long.valueOf(0x2000L);
	public static final ParamProperties RUNNING_JOBS = 
		new ParamProperties("Running Jobs", new Color(  0, 205,   0));
	
    public static final Long LIDLE_JOBS	= Long.valueOf(0x4000L);
	public static final ParamProperties IDLE_JOBS = 
		new ParamProperties("Idle Jobs", new Color(251, 236,  93));
	
    public static final Long LFINISHED_JOBS = Long.valueOf(0x8000L);
	public static final ParamProperties FINISHED_JOBS = 
		new ParamProperties("Finished Jobs", new Color(200, 200, 200));
	
	public static final Long LFINISHED_S_JOBS = Long.valueOf(0x10000L);
	public static final ParamProperties FINISHED_S_JOBS = 
		new ParamProperties("Succes", new Color( 69, 139, 0));
	
    public static final Long LFINISHED_E_JOBS = Long.valueOf(0x20000L);
	public static final ParamProperties FINISHED_E_JOBS = 
		new ParamProperties("Error", new Color(205, 85, 85));
	
	public static HashMap HJOBS = new HashMap();
	static{
		HJOBS.put(LRUNNING_JOBS, RUNNING_JOBS);
		HJOBS.put(LIDLE_JOBS, IDLE_JOBS);
		HJOBS.put(LFINISHED_JOBS, FINISHED_JOBS);
		HJOBS.put(LFINISHED_S_JOBS, FINISHED_S_JOBS);
		HJOBS.put(LFINISHED_E_JOBS, FINISHED_E_JOBS);
	}
	
	/** CPU_TIME constants */
	public static final Long LVOS_NUMBER = Long.valueOf(0x20000L);
	public static final ParamProperties VOS_NUMBER	= 
		new ParamProperties("Number of VOs", new Color(0, 0, 51));
	
    public static final Long LCPU_TIME = Long.valueOf(0x40000L);
	public static final ParamProperties CPU_TIME = 
		new ParamProperties("CPU Time consumed by VOs", new Color(50, 150, 250));

	public static HashMap HVOS = new HashMap();
	static{
		HVOS.put(LVOS_NUMBER, VOS_NUMBER);
		HVOS.put(LCPU_TIME, CPU_TIME);
	}
	
	/** CPUs number constants */
	public static final Long LCPU_NO = Long.valueOf(0x80000L);
	public static final ParamProperties CPU_NO = 
		new ParamProperties("Number of CPUs", new Color(211, 211, 211));
	
	public static HashMap HCPUNO = new HashMap();
	static{
		HCPUNO.put(LCPU_NO, CPU_NO);
	}
	
	public static Color IN_COLOR = Color.blue;
	public static Color OUT_COLOR = Color.red;
	 
	public static final String[] jobs_name = {"Running Jobs", "Idle Jobs", "Held Jobs", "Unknown Jobs"};
	public static final Color[] jobs_color = {RUNNING_JOBS.color, IDLE_JOBS.color};//, HELD_JOBS.color, UNKNOWN_JOBS.color};

}

