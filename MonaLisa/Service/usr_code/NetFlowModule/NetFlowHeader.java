/**
 * 
 * NetFlow V5 message header format. Wrapper class.
 * 
 */

public class NetFlowHeader {

	/**
	 * The version of NetFlow records exported in this packet;
	 */
	private short version;

	/**
	 * Number of FlowSet records (both template and data) contained within this
	 * packet;
	 */
	private short count;

	/**
	 * Time in milliseconds since this device was first booted
	 */
	private int SysUptime;

	/**
	 * Seconds since 0000 Coordinated Universal Time (UTC) 1970
	 */
	private int unix_secs;

	/**
	 * Residual nanoseconds since 0000 UTC 1970
	 */
	private int unix_nsecs;

	/**
	 * Sequence number of total flows seen
	 */
	private int flow_sequence;

	/**
	 * Type of flow switching engine, 0 for RP, 1 for VIP/LC
	 */
	private short engine_type;

	/**
	 * VIP or LC slot number of the flow switching engine
	 */
	private short engine_id;

	public short getCount() {
		return count;
	}

	public void setCount(short count) {
		this.count = count;
	}

	public short getEngine_id() {
		return engine_id;
	}

	public void setEngine_id(short engine_id) {
		this.engine_id = engine_id;
	}

	public short getEngine_type() {
		return engine_type;
	}

	public void setEngine_type(short engine_type) {
		this.engine_type = engine_type;
	}

	public int getFlow_sequence() {
		return flow_sequence;
	}

	public void setFlow_sequence(int flow_sequence) {
		this.flow_sequence = flow_sequence;
	}

	public int getSysUptime() {
		return SysUptime;
	}

	public void setSysUptime(int sysUptime) {
		SysUptime = sysUptime;
	}

	public int getUnix_nsecs() {
		return unix_nsecs;
	}

	public void setUnix_nsecs(int unix_nsecs) {
		this.unix_nsecs = unix_nsecs;
	}

	public int getUnix_secs() {
		return unix_secs;
	}

	public void setUnix_secs(int unix_secs) {
		this.unix_secs = unix_secs;
	}

	public short getVersion() {
		return version;
	}

	public void setVersion(short version) {
		this.version = version;
	}

	public String toString() {
		String s = "Header:";

		s = s + "\n\tVersion: " + version;
		s = s + "\n\tcount: " + count;
		s = s
				+ "\n\tSysUptime: "
				+ ((SysUptime < 0) ? ((long) SysUptime + 2147483648l)
						: (long) SysUptime);
		s = s
				+ "\n\tunix_secs: "
				+ ((unix_secs < 0) ? ((long) unix_secs + 2147483648l)
						: (long) unix_secs);
		s = s
				+ "\n\tunix_nsecs: "
				+ ((unix_nsecs < 0) ? ((long) unix_nsecs + 2147483648l)
						: (long) unix_nsecs);
		s = s + "\n\tflow_sequence: " + flow_sequence;
		s = s + "\n\tengine_type: " + engine_type;
		s = s + "\n\tengine_id: " + engine_id;
		s = s + "\n";

		return s;
	} // toString

} // NetFlowHeader class
