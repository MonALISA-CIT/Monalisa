package lia.Monitor.JiniClient.Store;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;

/**
 * <p>Repository filter for creating the necesarry datasets to	
 * display pathload history and realtime views. </p>
 * 
 * <p>The data exported by the pathload module from a ML Service A  
 * is the available bandwidth from B to A. To display this data, 
 * you are usually interested in the available bandwidth from A to B, 
 * so this data A/Pathload/B is inversed and inserted into the 
 * ResultSets rto and rfrom.</p>
 * 
 * <p>The history view is the available bandwidth average value. A new
 * dataset is constructed also taking in regard the exit status of
 * the pathload measurement. Exit statuses are messages that begin 
 * with MESG_STATUS. Fluctuations are also supported by using 
 * Extended Results.</p>
 * 
 * <p>The module also makes eventual error corrections and assures backward 
 * compatibility.</p>
 * 
 * <p>At best, there are four results put in the database. <b>The original
 * Result is discarded.</b> <br />
 * 		<ul>
 *			<li>rto, rfrom - used by the spiders <i>(AwBandwidth_To, AwBandwidth_From)</i> </li>
 *			<li>er - Extended result used by the history plot and matrix view. <i>(AwBandwidth_Avg)</i> </li>
 *			<li>rmatrix - used by the matrix view <i>(AwBandwidth_Status, AwBandwidth_Duration, 
 *				  AwBandwidth_FLow, AwBandwidth_FHigh)</i></li>
 * 		</ul>
 * </p>
 * 
 * @author adim
 *
 */
public class UL_Filter implements Filter {
	/**
	 * We love ok messages
	 */
	private final static int MESG_STATUS_OK = 1;
	private final static int MESG_STATUS_USER_BW_RESOLUTION_ACHIEVED = 2;
	private final static int MESG_STATUS_EXIT_DUE_GRAY_BW_RESOLUTION = 3;
	private final static int MESG_STATUS_CS_WITH_RESULT = 4;
	private final static int MESG_STATUS_OK_LWB = 5;
	private final static int MESG_STATUS_USER_BW_RESOLUTION_ACHIEVED_LWB = 6;
	private final static int MESG_STATUS_EXIT_DUE_GRAY_BW_RESOLUTION_LWB = 7;
	private final static int MESG_STATUS_RESULT_MIN_AVBW = 8;
	private final static int MESG_STATUS_LOW_BANDWITH_WARNING = 9;
	private final static int MESG_STATUS_PROBING_RATE_NOT_ACHIEVED = 11;
	/**
	 * Errors, firewall related
	 */
	private final static int MESG_STATUS_FAILED = -10;
	private final static int MESG_STATUS_OUT_OF_SYNC = -11;
	private final static int MESG_STATUS_NO_SENDER = -12;
	private final static int MESG_STATUS_ERRORNOUS = -13;
	private final static int MESG_STATUS_FEW_FLEETS = -14;
	private final static int MESG_STATUS_RESULT_MAX_TIME_INTERVAL = -15;
	private final static int MESG_STATUS_CS_WITHOUT_RESULT = -16;
	private final static int MESG_STATUS_FIREWALLED_RECEIVER = -17;
	/**
	 * Internal errors, not firewall related
	 */
	private final static int MESG_STATUS_NOSTATUS = -30;
	private final static int MESG_STATUS_DGRAM_OPEN_ERROR = -31;
	private final static int MESG_STATUS_DGRAM_BIND_ERROR = -32;
	private final static int MESG_STATUS_DGRAM_BUF_ERROR = -33;
	private final static int MESG_STATUS_SOCKET_UDP_REUSE = -34;
	private final static int MESG_STATUS_SOCKET_STREAM = -35;
	private final static int MESG_STATUS_SOCKET_TCP_REUSE = -36;
	private final static int MESG_STATUS_SENDER_IP = -37;
	private final static int MESG_STATUS_SEND_MY_IP = -38;
	private final static int MESG_STATUS_FNCTL_TCP = -39;
	private final static int MESG_STATUS_SOCK_OPT_TCP = -40;
	private final static int MESG_STATUS_PTHREAD_CREATE = -41;
	private final static int MESG_STATUS_RECV_MALLOC = -42;
	private final static int MESG_STATUS_RECV_FLEET = -43;
	private final static int MESG_STATUS_MALLOC_PKTSZ_ERROR = -44;
	private final static int MESG_STATUS_MALLOC_CURPKTSZ_ERROR = -45;
	private final static int MESG_STATUS_CONTROL_MESSAGE_FAILED = -46;
	private final static int MESG_STATUS_RECVFROM_LATENCY = -47;
	private final static int MESG_STATUS_PTHREAD_SELECT = -48;
	/**
	 * Old status codes
	 */
	private final static int MESG_STATUS_OLD_UNUSED = 10;
	private final static int MESG_STATUS_OLD_RELAY = 20;
	private final static int MESG_STATUS_OLD_OK = 30;
	/**
	 * Should we print debug information to log.out? 
	 */
	private final static boolean DEBUG = Boolean.valueOf(
			AppConfig.getProperty("lia.Monitor.JiniClient.Store.ULFilter.ULFilterDebug", "true")).booleanValue();
	
	/**
	 * The main filter method.
	 * 
	 * @return	A Vector of extended results (rto, rfrom, er)
	 */
	public Object filterData(final Object data) {
		if (data == null) {
			return null;
		}

		final Vector vResult = new Vector();

		if (data instanceof Collection) {
			Iterator it = ((Collection) data).iterator();

			while (it.hasNext()) {
				Vector vTemp = (Vector) filterData(it.next());

				vResult.addAll(vTemp);
			}
		} else if (data instanceof Result) {
			Result r = (Result) data;

			if ((r.ClusterName != null) && 
					(r.ClusterName.equals("Pathload"))) {
				double dmin = -1;
				double dmax = -1;
				int iMeasurementStatus = -1;
				int iMeasurementDuration = -1;
				double dMegaBytesReceived = -1.0d;

				for (int i = 0; i < r.param_name.length && i < r.param.length; i++) {
					if (r.param_name[i].startsWith("AwBandwidth_")) {
						if (r.param_name[i].endsWith("High"))
							dmax = r.param[i];
						if (r.param_name[i].endsWith("Low"))
							dmin = r.param[i];
					} else if (r.param_name[i].equalsIgnoreCase("MeasurementStatus")) {
						iMeasurementStatus = (int) r.param[i];
					} else if (r.param_name[i].equalsIgnoreCase("MeasurementDuration")) {
						iMeasurementDuration = (int) r.param[i];
					} else if (r.param_name[i].equalsIgnoreCase("MegaBytesReceived")) {
						dMegaBytesReceived = r.param[i];
					}
				}				
				if (DEBUG) { 
					System.err.println("[UL_Filter] >> PathloadResult. From: " + r.FarmName + 
							" To: " + r.NodeName +
							" At: " + r.time +
							" MeasurementStatus: " + iMeasurementStatus +
							" MeasurementDuration " + iMeasurementDuration + 
							" MegaBytesReceived " + dMegaBytesReceived);
				}				

				/**
				 * OLD MODULES
				 */
				// old measurement, discard
				if (iMeasurementStatus == MESG_STATUS_OLD_RELAY)
					return null;
				if (iMeasurementStatus == MESG_STATUS_OLD_OK) {
					iMeasurementStatus = MESG_STATUS_OK;
					if (dmin == 0 && dmax == 0) {
						// change High to pathload precission
						// dmax = 0.2d;
						iMeasurementStatus = MESG_STATUS_NO_SENDER;
					}
				}
				/**
				 * end OLD MODULES
				 */
				
				if ((iMeasurementStatus > 0) 
						&& (dMegaBytesReceived>=0.0 
								&& dMegaBytesReceived <= 1.0d)) {
					iMeasurementStatus = MESG_STATUS_NO_SENDER;
					if (DEBUG) {
						System.err.println("[UL_Filter] << PathloadResult Value ignored. From: " + r.FarmName +
								" To: " + r.NodeName +
								" At: " + r.time +
								" MeasurementStatus: " + iMeasurementStatus +
								" MegaBytesReceived " + dMegaBytesReceived);
					}
				}
				
				// Average bandwidth
				ExtendedResult er = new ExtendedResult();
				Result rto = new Result();
				Result rfrom = new Result();
				Result rmatrix = new Result();

				er.time = r.time;
				er.FarmName = r.FarmName;
				er.ClusterName = r.ClusterName;
				er.NodeName = trimTrailingIp(r.NodeName);

				rto.time = r.time;
				rto.FarmName = r.FarmName;
				rto.ClusterName = r.ClusterName;
				rto.NodeName = trimTrailingIp(r.NodeName);

				rfrom.time = r.time;
				rfrom.FarmName = trimTrailingIp(r.NodeName);
				rfrom.ClusterName = r.ClusterName;
				rfrom.NodeName = r.FarmName;

				rmatrix.time = r.time;
				rmatrix.FarmName = r.FarmName;
				rmatrix.ClusterName = r.ClusterName;
				rmatrix.NodeName = trimTrailingIp(r.NodeName);
				
				// no values
				switch (iMeasurementStatus) {
					case MESG_STATUS_OLD_UNUSED:
						iMeasurementStatus = MESG_STATUS_FAILED;
					case MESG_STATUS_FAILED:
					case MESG_STATUS_OUT_OF_SYNC:
					case MESG_STATUS_ERRORNOUS:
					case MESG_STATUS_FEW_FLEETS:
					case MESG_STATUS_FIREWALLED_RECEIVER:
						rto.addSet("AwBandwidth_Status", iMeasurementStatus);
						rto.addSet("AwBandwidth_Duration", iMeasurementDuration);
						vResult.add(rto);
						break;
					case MESG_STATUS_NOSTATUS:
					case MESG_STATUS_DGRAM_OPEN_ERROR:
					case MESG_STATUS_DGRAM_BIND_ERROR:
					case MESG_STATUS_DGRAM_BUF_ERROR:
					case MESG_STATUS_SOCKET_UDP_REUSE:
					case MESG_STATUS_SOCKET_STREAM:
					case MESG_STATUS_SOCKET_TCP_REUSE:
					case MESG_STATUS_SENDER_IP:
					case MESG_STATUS_NO_SENDER:
					case MESG_STATUS_SEND_MY_IP:
					case MESG_STATUS_FNCTL_TCP:
					case MESG_STATUS_SOCK_OPT_TCP:
					case MESG_STATUS_PTHREAD_CREATE:
					case MESG_STATUS_RECV_MALLOC:
					case MESG_STATUS_RECV_FLEET:
					case MESG_STATUS_MALLOC_PKTSZ_ERROR:
					case MESG_STATUS_MALLOC_CURPKTSZ_ERROR:
					case MESG_STATUS_CONTROL_MESSAGE_FAILED:
					case MESG_STATUS_RECVFROM_LATENCY:
					case MESG_STATUS_PTHREAD_SELECT:						
						rto.addSet("AwBandwidth_Status", iMeasurementStatus);
						rto.addSet("AwBandwidth_Duration", iMeasurementDuration);
						vResult.add(rto);
						break;
					case MESG_STATUS_RESULT_MAX_TIME_INTERVAL:
					case MESG_STATUS_CS_WITHOUT_RESULT:
						rto.addSet("AwBandwidth_Status", iMeasurementStatus);
						rto.addSet("AwBandwidth_Duration", iMeasurementDuration);
						vResult.add(rto);
						break;
					case MESG_STATUS_RESULT_MIN_AVBW:
					case MESG_STATUS_USER_BW_RESOLUTION_ACHIEVED_LWB:
					case MESG_STATUS_EXIT_DUE_GRAY_BW_RESOLUTION_LWB:
					case MESG_STATUS_PROBING_RATE_NOT_ACHIEVED:
					case MESG_STATUS_OK_LWB:
						if (dmin > 0) {
							er.min = dmin;
							er.max = dmin;							
							er.addSet("AwBandwidth_Avg", dmin);
							rmatrix.addSet("AwBandwidth_Status", iMeasurementStatus);
							rmatrix.addSet("AwBandwidth_Duration", iMeasurementDuration);
							rmatrix.addSet("AwBandwidth_FLow", dmin);
							rmatrix.addSet("AwBandwidth_FHigh", dmin);
							vResult.add(er);
							vResult.add(rmatrix);
						}
						rto.addSet("AwBandwidth_To", dmin);
						rfrom.addSet("AwBandwidth_From", dmin);
						vResult.add(rto);
						vResult.add(rfrom);
						break;
					case MESG_STATUS_LOW_BANDWITH_WARNING:
					case MESG_STATUS_CS_WITH_RESULT:
					case MESG_STATUS_USER_BW_RESOLUTION_ACHIEVED:
					case MESG_STATUS_EXIT_DUE_GRAY_BW_RESOLUTION:
					case MESG_STATUS_OK:
						if (dmin >= 0 && dmax >= 0) {
							if (dmin > dmax) {
								double dTemp = dmin;
								dmin = dmax;
								dmax = dTemp;
							}
							er.min = dmin;
							er.max = dmax;							
							er.addSet("AwBandwidth_Avg", (dmin + dmax) / 2);
							rmatrix.addSet("AwBandwidth_Status", iMeasurementStatus);
							rmatrix.addSet("AwBandwidth_Duration", iMeasurementDuration);
							rmatrix.addSet("AwBandwidth_FLow", dmin);
							rmatrix.addSet("AwBandwidth_FHigh", dmax);
							rto.addSet("AwBandwidth_To", (dmin + dmax) / 2);
							rfrom.addSet("AwBandwidth_From", (dmin + dmax) / 2);
							vResult.add(er);
							vResult.add(rto);
							vResult.add(rfrom);
							vResult.add(rmatrix);
						}
						break;
					default:
						System.err.println("[UL_Filter] << PathloadResult Unknown Status Code " +
								iMeasurementStatus);
						break;
				}

				if (DEBUG) {					
					if ((vResult!=null) && (!vResult.isEmpty())) {
						for (Iterator it = vResult.iterator(); it.hasNext(); ) {							
							Object o = it.next();
							if ((o != null) && (o instanceof Result)) {
								System.err.println("[UL_Filter] << PathloadResult " +
										getStringFromResult((Result) o));
							}
						}
					}
				}
			}
		}

		return vResult;
	}

	/**
	 * <p>Since v 1.4.10 the nodeName for Pathload is set to FARM_NAME@FARM_IP. </p>
	 * 
	 * <p>This method is used to trim trailing "@FARM_IP" string from NodeName 
	 * since we don't need it in repository.</p>
	 * 
	 * @param farm_at_ip	The name of the destination farm @ ip address.
	 * @return				The name of the destination farm.
	 */
	private String trimTrailingIp(String farm_at_ip) {

		if (farm_at_ip == null || farm_at_ip.length() == 0)
			return farm_at_ip;
		int iPos = farm_at_ip.lastIndexOf('@');
		if (iPos < 0)
			return farm_at_ip;
		return farm_at_ip.substring(0, iPos);
	}

	/**
	 * Get the String Representation of a Result
	 * 
	 * @param r		Result to return String data
	 * @return		Result as a String
	 */
	private String getStringFromResult(Result r) {
		if (r == null) return "";
		StringBuilder sb = new StringBuilder();
		
		
		sb.append("From: " + r.FarmName + " ");
		sb.append("To: " + r.NodeName + " ");
		sb.append("At: " + r.time + " ");
		for (int i=0; i<r.param_name.length; i++) {
			sb.append(r.param_name[i] + ": " + r.param[i] + " ");
		}
		
		if (r instanceof ExtendedResult) {
			ExtendedResult er = (ExtendedResult) r;
			sb.append("(Min: " + er.min + " Max:" + er.max + ")");
		}
		
		return sb.toString();
	}
}
