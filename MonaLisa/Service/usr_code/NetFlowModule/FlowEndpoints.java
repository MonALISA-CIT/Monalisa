
/**
 * 
 * Internal helper class. Defines endpoints (source and destination address)
 * for communication flows.
 *
 */
public class FlowEndpoints {
	
	/**
	 * source address
	 */
	public String srcAddr;
	
	/**
	 * destination address
	 */
	public String dstAddr;

	public FlowEndpoints(String srcAddr, String dstAddr) {
		this.srcAddr = srcAddr;
		this.dstAddr = dstAddr;
	}

	public boolean equals(Object obj) {

		if (obj == null || !(obj instanceof FlowEndpoints)) {
			return false;
		} // if

		return (srcAddr.equals(((FlowEndpoints) obj).srcAddr) && dstAddr
				.equals(((FlowEndpoints) obj).dstAddr));
	}

	public int hashCode() {
		return 0;
	}

} // FlowEndpoints
