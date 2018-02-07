import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

/**
 * 
 * MonaLisa Netflow module. Receives UDP NetFlow v5 datagrams on a given port
 * and, based on the configuration file, is able to raport traffic in and out
 * between networks (a Master network and Nodes networks - see the module's
 * configuration file format). It also reports total traffic in and out for the Master 
 * entry.
 * 
 */
public class NetFlowModule extends cmdExec implements MonitoringModule {

	static public String ModuleName = "NetFlow";

	static private long Measure_Time_Rate = 60000; // in millisec.

	static String[] tmetric = { "" };

	String cmd;

	String host = "127.0.0.1";

	Vector results;

	long startMeasure = -1;

	long stopMeasure = -1;

	long raportMeasureTime = 0;

	long routerSysUptime = 0;

	private Hashtable trace;

	private long totalin = 0;

	private long totalout = 0;

	// module configurable parameters; default values;
	int port = 2055;

	String confFile = "NetFlow.conf";

	int sampleRate = 100;

	public NetFlowModule() {
		super("NetFlowModule");
		info.ResTypes = tmetric;
		isRepetitive = true;
		results = new Vector();
		trace = new Hashtable();
	} // NetFlowModule

	/**
	 * 
	 * Internal thread that analizes coming NetFlow datagrams, calculates
	 * partial and total traffic between given nodes and reports them as
	 * lia.Monitor.monitor.Result objects.
	 * 
	 */
	class NetFlowThread extends Thread {

		private boolean hasToRun = true;

		private DatagramSocket receiveSocket;

		NetFlowConfig nfc;

		int nrOfMeasurements = 0; // just for testing to see the min number

		// necessary for measurements

		public NetFlowThread(int port) {
			super("( ML ) NetFlow Module QueryThread");
			hasToRun = true;
			raportMeasureTime = 0;
			try {
				receiveSocket = new DatagramSocket(port);
			} catch (Throwable t) {
				t.printStackTrace();
				receiveSocket = null;
				hasToRun = false;
			} // try - catch
			try {
				nfc = new NetFlowConfig(confFile);
			} catch (Exception t) {
				t.printStackTrace();
			}
		} // NetFlowThread

		public void run() {

			// alloc for NetFlow datagram data.
			byte[] buf = new byte[8000];

			long totalBytesIn = 0;
			long totalBytesOut = 0;

			while (hasToRun && receiveSocket != null) {

				try {
					DatagramPacket dp = new DatagramPacket(buf, buf.length);

					/**
					 * receive the NetFlow Datagram
					 */
					receiveSocket.receive(dp);

					System.out.println(new java.util.Date()
							+ " NetFlowModule ==> receiveDatgram [ "
							+ System.currentTimeMillis() + " ] ");

					ByteBuffer bb = ByteBuffer.wrap(dp.getData());

					NetFlowHeader header = new NetFlowHeader();

					/**
					 * first read the NetFlow header
					 */
					header.setVersion(bb.getShort());
					header.setCount(bb.getShort());
					header.setSysUptime(bb.getInt());
					header.setUnix_secs(bb.getInt());
					header.setUnix_nsecs(bb.getInt());
					header.setFlow_sequence(bb.getInt());

					header.setEngine_type(bb.getShort());
					header.setEngine_id(bb.getShort());

					// update routerSysUptime

					long routerSysUptimeNew = ((long) header.getSysUptime()) & 0xffffffffL;
					if (routerSysUptime > 0
							&& routerSysUptimeNew < routerSysUptime) {
						routerSysUptime = routerSysUptimeNew;
						System.out.println("Router restarted");
						// reset couters
						trace.clear();
						startMeasure = -1;
						stopMeasure = -1;
						nrOfMeasurements = 0;
						raportMeasureTime = 0;
						continue;
					} else {
						routerSysUptime = ((long) header.getSysUptime()) & 0xffffffffL;
					} // if - else

					NetFlowRecord[] netFlowRecords = new NetFlowRecord[header
							.getCount()];
					/**
					 * read flows specified in this datagram
					 */
					for (int i = 0; i < netFlowRecords.length; i++) {
						netFlowRecords[i] = new NetFlowRecord();
						netFlowRecords[i].setSrcaddr(bb.getInt());
						netFlowRecords[i].setDstaddr(bb.getInt());
						netFlowRecords[i].setNexthop(bb.getInt());
						netFlowRecords[i].setInput(bb.getShort());
						netFlowRecords[i].setOutput(bb.getShort());
						netFlowRecords[i].setDPkts(bb.getInt());
						netFlowRecords[i].setDOctets(bb.getInt());
						netFlowRecords[i].setFirst(bb.getInt());
						netFlowRecords[i].setLast(bb.getInt());
						netFlowRecords[i].setSrcport(bb.getShort());
						netFlowRecords[i].setDstport(bb.getShort());
						netFlowRecords[i].setPad1(bb.get());
						netFlowRecords[i].setTcp_flags(bb.get());
						netFlowRecords[i].setProt(bb.get());
						netFlowRecords[i].setTos(bb.get());
						netFlowRecords[i].setSrc_as(bb.getShort());
						netFlowRecords[i].setDst_as(bb.getShort());
						netFlowRecords[i].setSrc_mask(bb.getShort());
						netFlowRecords[i].setDst_mask(bb.getShort());

						String srcAddr = NetFlowRecord
								.ipAddress(netFlowRecords[i].getSrcaddr());
						String dstAddr = NetFlowRecord
								.ipAddress(netFlowRecords[i].getDstaddr());

						ConfigHost srcHost = (ConfigHost) nfc.getHosts().get(
								new IPMatch(srcAddr));
						ConfigHost dstHost = (ConfigHost) nfc.getHosts().get(
								new IPMatch(dstAddr));

						if (srcAddr != null && nfc.matchIP(srcAddr)
								&& srcHost != null && srcHost.getIsMaster()) {
							// add tot total out
							totalBytesOut = totalBytesOut
									+ (((long) (netFlowRecords[i].getDOctets())) & 0xffffffffL);
						} // if

						if (dstAddr != null && nfc.matchIP(dstAddr)
								&& dstHost != null && dstHost.getIsMaster()) {
							// add tot total in
							totalBytesIn = totalBytesIn
									+ (((long) (netFlowRecords[i].getDOctets())) & 0xffffffffL);
						}

						if (srcAddr != null
								&& nfc.matchIP(srcAddr)
								&& dstAddr != null
								&& nfc.matchIP(dstAddr)
								&& srcHost != null
								&& dstHost != null
								&& (srcHost.getIsMaster() || dstHost
										.getIsMaster())) {
							nrOfMeasurements++;

							Measure m = (Measure) trace.get(netFlowRecords[i]);
							if (m == null)
								m = new Measure();

							/**
							 * calculate nr. of packages and nr. of octets for
							 * each flow.
							 */
							m.packages = m.packages
									+ (((long) (netFlowRecords[i].getDPkts())) & 0xffffffffL);
							m.bytes = m.bytes
									+ (((long) (netFlowRecords[i].getDOctets())) & 0xffffffffL);

							trace.put(netFlowRecords[i], m);

						} // if

						if (startMeasure == -1
								|| (((long) netFlowRecords[i].getFirst()) & 0xffffffffL) < startMeasure) {
							startMeasure = (((long) netFlowRecords[i]
									.getFirst()) & 0xffffffffL);
						} // if

						if (stopMeasure == -1
								|| (((long) netFlowRecords[i].getLast()) & 0xffffffffL) > stopMeasure) {
							stopMeasure = (((long) netFlowRecords[i].getLast()) & 0xffffffffL);
						} // if

						long newRaportMeasureTime = ((long) (System
								.currentTimeMillis()
								- routerSysUptime + (startMeasure + stopMeasure) / 2));

						if (raportMeasureTime == 0) {
							raportMeasureTime = newRaportMeasureTime;
						} // if - initialize raportMeasureTime

						/**
						 * at least 200 measurements every 1 min for the
						 * statistic measurement to be real.
						 */
						if (nrOfMeasurements >= 200
								&& (newRaportMeasureTime - raportMeasureTime >= 60000)) {

							Hashtable measurements = new Hashtable();
							String total_IN = nfc.getTotal_IN();
							String total_OUT = nfc.getTotal_OUT();
							double time = ((double) (stopMeasure - startMeasure)) / 1000;

							for (Enumeration en = trace.keys(); en
									.hasMoreElements();) {

								NetFlowRecord nfr = (NetFlowRecord) en
										.nextElement();
								ConfigHost srcH = (ConfigHost) nfc
										.getHosts()
										.get(
												new IPMatch(NetFlowRecord
														.ipAddress(nfr
																.getSrcaddr())));
								ConfigHost dstH = (ConfigHost) nfc
										.getHosts()
										.get(
												new IPMatch(NetFlowRecord
														.ipAddress(nfr
																.getDstaddr())));

								if (srcH != null && dstH != null) {
									FlowEndpoints fw = new FlowEndpoints(srcH
											.getName(), dstH.getName());
									Measure m = (Measure) measurements.get(fw);
									if (m == null)
										m = new Measure();

									m.bytes = m.bytes
											+ (((double) (((Measure) trace
													.get(nfr)).bytes * sampleRate) * 8) / ((1000 * 1000 * time)));
									m.packages = m.packages
											+ ((double) ((Measure) trace
													.get(nfr)).packages * sampleRate)
											/ time;
									measurements.put(fw, m);
								} // if

							} // for

							for (Enumeration en = measurements.keys(); en
									.hasMoreElements();) {
								try {
									FlowEndpoints fe = (FlowEndpoints) en
											.nextElement();
									Measure m = (Measure) measurements.get(fe);
									Result r = new Result();
									r.NodeName = fe.srcAddr + "_" + fe.dstAddr;
									r.addSet("packages", m.packages);
									r.addSet("throughput", m.bytes);
									r.time = newRaportMeasureTime;

									results.add(r);
								} catch (Throwable t) {
								}
							} // for

							if (total_IN != null && total_OUT != null) {
								try {
									if (totalBytesOut != 0) {
										Result r = new Result();
										r.NodeName = total_IN + "_" + total_OUT;
										r
												.addSet(
														"throughput",
														(((double) (totalBytesOut * sampleRate)) * 8 / ((1000 * 1000 * time))));
										r.time = newRaportMeasureTime;
										results.add(r);
									} // if
								} catch (Throwable t) {
								}

								try {
									if (totalBytesIn != 0) {
										Result r = new Result();
										r.NodeName = total_OUT + "_" + total_IN;
										r
												.addSet(
														"throughput",
														(((double) (totalBytesIn * sampleRate)) * 8 / ((1000 * 1000 * time))));
										r.time = newRaportMeasureTime;
										results.add(r);
									} // if
								} catch (Throwable t) {
								}

								totalBytesIn = 0;
								totalBytesOut = 0;

							} // if

							trace.clear();

							System.out.println("\n\nNrOfMeasurements =======> "
									+ nrOfMeasurements + " [" + startMeasure
									+ "-" + stopMeasure + "]" + "\n");

							startMeasure = -1;
							stopMeasure = -1;
							nrOfMeasurements = 0;

							// update reportMeasureTime
							raportMeasureTime = newRaportMeasureTime;
						} // if

					} // for

				} catch (Throwable t) {
					t.printStackTrace();
				} // try - catch

			} // while

		} // run

	} // NetFlowThread

	public MonModuleInfo init(MNode Node, String args1) {

		System.out.println("NetFlowModule started ... :). initi called");

		this.Node = Node;
		info.ResTypes = tmetric;

		if (args1 != null && args1.length() > 0) {
			StringTokenizer st = new StringTokenizer(args1, ",");

			while (st.hasMoreTokens()) {
				String nextToken = st.nextToken();
				if (nextToken != null && nextToken.length() > 0) {

					if (nextToken.indexOf("port") != -1) {
						int ieq = nextToken.indexOf("=");
						if (ieq != -1) {
							String portString = nextToken.substring(ieq + 1)
									.trim();
							if (portString != null && portString.length() > 0) {
								port = new Integer(portString).intValue();
								System.out.println("Using port : " + port);
							} else {
								port = 2055; // default port
							} // if - else

						} // if
					} // if - port

					if (nextToken.indexOf("confFile") != -1) {
						int ieq = nextToken.indexOf("=");
						if (ieq != -1) {
							confFile = nextToken.substring(ieq + 1).trim();
							System.out.println("Using conf file: " + confFile);
							if (confFile == null || confFile.length() == 0) {
								confFile = "NetFlow.conf"; // default
								// configuration
								// file ....
							} // if
						} // if
					} // if

					if (nextToken.indexOf("sampleRate") != -1) {

						int ieq = nextToken.indexOf("=");

						if (ieq != -1) {

							String sampleRateString = nextToken
									.substring(ieq + 1);
							if (sampleRateString != null
									&& sampleRateString.length() > 0) {
								sampleRate = new Integer(sampleRateString)
										.intValue();
								System.out.println("Using sample rate: "
										+ sampleRate);
							} else {
								sampleRate = 100; // default rate
							} // if - else

						} // if

					} // if

				} // if
			} // while

		} // if - citesc parametrii

		(new NetFlowThread(port)).start();

		return info;
	} // init

	public Object doProcess() throws Exception {

		if (results == null || results.size() == 0) {
			return null;
		} // if

		Vector retV = new Vector();

		synchronized (results) {
			retV.addAll(results);
			results.clear();
		} // synchronized

		if (retV != null && retV.size() > 0) {
			for (int i = 0; i < retV.size(); i++) {
				Result rr = (Result) retV.elementAt(i);
				rr.ClusterName = Node.getClusterName();
				rr.FarmName = Node.getFarmName();
			} // for
		} // if

		if (retV.size() == 0)
			return null;

		return retV;
	} // doProcess

	public MonModuleInfo getInfo() {
		return info;
	} // getInfo

	public String[] ResTypes() {
		return tmetric;
	} // ResTypes

	public String getOsName() {
		return "linux";
	} // getOsName

}
