
/**
 * 
 * Class that wraps a  flow. 
 *
 */
public class NetFlowRecord {
	
	/**
	 * Source IP address
	 */
	private int srcaddr;
	/**
	 * Destination IP address
	 */
	private int dstaddr;
	/**
	 * Next hop router's IP address
	 */
	private int nexthop;
	/**
	 * Ingress interface SNMP ifIndex
	 */
	private short input;
	/**
	 * Egress interface SNMP ifIndex
	 */
	private short output;
	/**
	 * Packets in the flow
	 */
	private int dPkts;
	/**
	 * Octets (bytes) in the flow
	 */
	private int dOctets;
	/**
	 * SysUptime at start of the flow
	 */
	private int first;
	/**
	 * SysUptime at the time the last packet of the flow was received
	 */
	private int last;
	/**
	 * Layer 4 source port number or equivalent
	 */
	private short srcport;
	/**
	 * Layer 4 destination port number or equivalent
	 */
	private short dstport;
	/**
	 * Unused (zero) byte
	 */
	private byte pad1;
	/**
	 * Cumulative OR of TCP flags
	 */
	private byte tcp_flags;
	/**
	 * Layer 4 protocol (for example, 6=TCP, 17=UDP)
	 */
	private byte prot;
	/**
	 * IP type-of-service byte
	 */
	private byte tos;
	/**
	 * Autonomous system number of the source, either origin or peer
	 */
	private short src_as;
	/**
	 * Autonomous system number of the destination, either origin or peer
	 */
	private short dst_as;
	/**
	 * Source address prefix mask bits
	 */
	private short src_mask;
	/**
	 * Destination address prefix mask bits
	 */
	private short dst_mask;
	/**
	 * Pad 2 is unused (zero) bytes
	 */
	private byte pad2;
	
	public int getDOctets() {
		return dOctets;
	}
	
	public void setDOctets(int octets) {
		dOctets = octets;
	}
	
	public int getDPkts() {
		return dPkts;
	}
	
	public void setDPkts(int pkts) {
		dPkts = pkts;
	}
	
	public short getDst_as() {
		return dst_as;
	}
	
	public void setDst_as(short dst_as) {
		this.dst_as = dst_as;
	}
	
	public short getDst_mask() {
		return dst_mask;
	}
	
	public void setDst_mask(short dst_mask) {
		this.dst_mask = dst_mask;
	}
	
	public int getDstaddr() {
		return dstaddr;
	}
	
	public void setDstaddr(int dstaddr) {
		this.dstaddr = dstaddr;
	}
	
	public short getDstport() {
		return dstport;
	}
	
	public void setDstport(short dstport) {
		this.dstport = dstport;
	}
	
	public int getFirst() {
		return first;
	}
	
	public void setFirst(int first) {
		this.first = first;
	}
	
	public short getInput() {
		return input;
	}
	
	public void setInput(short input) {
		this.input = input;
	}
	
	public int getLast() {
		return last;
	}
	
	public void setLast(int last) {
		this.last = last;
	}
	
	public int getNexthop() {
		return nexthop;
	}
	
	public void setNexthop(int nexthop) {
		this.nexthop = nexthop;
	}
	
	public short getOutput() {
		return output;
	}
	
	public void setOutput(short output) {
		this.output = output;
	}
	
	public byte getPad1() {
		return pad1;
	}
	
	public void setPad1(byte pad1) {
		this.pad1 = pad1;
	}
	
	public byte getPad2() {
		return pad2;
	}
	
	public void setPad2(byte pad2) {
		this.pad2 = pad2;
	}
	
	public byte getProt() {
		return prot;
	}
	
	public void setProt(byte prot) {
		this.prot = prot;
	}
	
	public short getSrc_as() {
		return src_as;
	}
	
	public void setSrc_as(short src_as) {
		this.src_as = src_as;
	}
	
	public short getSrc_mask() {
		return src_mask;
	}
	
	public void setSrc_mask(short src_mask) {
		this.src_mask = src_mask;
	}
	
	public int getSrcaddr() {
		return srcaddr;
	}
	
	public void setSrcaddr(int srcaddr) {
		this.srcaddr = srcaddr;
	}
	
	public short getSrcport() {
		return srcport;
	}
	
	public void setSrcport(short srcport) {
		this.srcport = srcport;
	}
	
	public byte getTcp_flags() {
		return tcp_flags;
	}
	
	public void setTcp_flags(byte tcp_flags) {
		this.tcp_flags = tcp_flags;
	}
	
	public byte getTos() {
		return tos;
	}
	
	public void setTos(byte tos) {
		this.tos = tos;
	}
		
	public static String ipAddress (int addr){
		String s = "";
		int x = 0;
		s = s+((addr>>24) & 0xff)+"."+((addr>>16) & 0xff)+"."+((addr>>8) & 0xff)+"."+((addr) & 0xff);
		
		return s;
	} // ipAddress
	
	public String toString(){
		
		String s = "\tFlowInformation:";
		s = s+ "\n\t\tSourceIPAddress: "+NetFlowRecord.ipAddress(srcaddr)+" Mask: "+NetFlowRecord.ipAddress(src_mask)+" SrcPort: "+((int)srcport & 0xffff)+" As: "+((int)src_as & 0xffff);
		s = s+ "\n\t\tDestinationIPAddress: "+NetFlowRecord.ipAddress(dstaddr)+" Mask: "+NetFlowRecord.ipAddress(dst_mask)+" DstPort: "+((int)dstport & 0xffff)+" As: "+((int)dst_as & 0xffff);
		s = s+ "\n\t\tNr. of packages in flow: "+(((long)dPkts) & 0xffffffffL);
		s = s+ "\n\t\tNr. of octets in flow: "+(((long)dOctets) & 0xffffffffL);
		s = s+ "\n\t\tLayer 4 protocol: "+((short)prot & 0xff);
		s = s+ "\n\t\tFirstTime: "+(((long)first) & 0xffffffffL);
		s = s+ "\n\t\tLastTime: "+(((long)last) & 0xffffffffL);
		s = s+ "\n\t\tCurrentTime: "+(System.currentTimeMillis());
		s = s+ "\n";
		
		return s;
		
	} // toString
	
	public boolean equals (Object cmpO) {
		
		NetFlowRecord cmp = (NetFlowRecord) cmpO;
	
		if (cmp!=null && srcaddr == cmp.getSrcaddr() && dstaddr == cmp.getDstaddr() && srcport == cmp.getSrcport() && dstport == cmp.getDstport())
			return true;
		
		return false;
	} // equals
	
	public int hashCode () {
		return ipAddress(srcaddr).length() + ipAddress(dstaddr).length();
	} // hashCode 
	
} // NetFlowRecord class
