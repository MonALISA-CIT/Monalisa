/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import lia.Monitor.monitor.Result;


/**
 * This holder class fully describes a pathload result.
 * Given the input values, it can fill a result with
 * theese 
 * 
 * @author heri
 *
 */
public class PathloadResult {
	
	/**
	 * This is a warning message. Pathload reports 0 low, 0 high but 
	 * still it has transferred more than one Megabyte of data and
	 * received more than two fleets of data.
	 * 
	 * The result will be modified in 0 low and 0.2 high. Pathload
	 * precision is 100k at low bandwidth. 
	 */
	public final static double MEASUREMENT_STATUS_LOW_BANDWITH_WARNING = 9;
	/**
	 * The measurement failed from different other reasons
	 */
	public final static double MEASUREMENT_STATUS_FAILED = -10;
	/**
	 * The Pathload controller has shut me off
	 */
	public final static double MEASUREMENT_STATUS_OUT_OF_SYNC = -11;
	/**
	 * Is Sender firewalled?
	 */
	public final static double MEASUREMENT_STATUS_NO_SENDER = -12;	
	/**
	 * The results are way beoynd the established margin of error
	 */
	public final static double MEASUREMENT_STATUS_ERRORNOUS = -13;
	/**
	 * There are too few fleets
	 */
	public final static double MEASUREMENT_STATUS_FEW_FLEETS = -14;
	/**
	 * Pathload measurement error.
	 */
	public final static double MEASUREMENT_STATUS_RESULT_MAX_TIME_INTERVAL = -15;
	/**
	 * Pathoad measurement error.
	 */
	public final static double MEASUREMENT_STATUS_CS_WITHOUT_RESULT = -16;
	
	/**
	 * Receiver is behind a firewall that drops all packages. 
	 */
	public final static double MEASUREMENT_STATUS_FIREWALLED_RECEIVER = -17;
	
	/**
	 * Resource Types published by this module. 
	 * <b>AwBandwidth_Low</b> - lower margin of the aw. bw from client to server
	 * <b>AwBandwidth_High</b> - upper margin of available bandwidth 
	 */
	public static String[] ResTypes = { "AwBandwidth_Low", "AwBandwidth_High", 
		"MeasurementDuration" , "FleetsSent", "MegaBytesReceived", "MeasurementStatus" };
	
	private String destIp;
	private String destFarmName;
	private double fleets_value;
	private double bytesRecv_value;
	private double awbwLow_value;
	private double awbwHigh_value;
	private double measurementDuration_value;
	private double exitStat_value;
	
	private boolean fleets;
	private boolean bytesRecv;
	private boolean awbwLow;
	private boolean awbwHigh;
	private boolean measurementDuration;
	private boolean exitStat;
	
	public PathloadResult(String destIpOrFarmName, String destFarmName) {
		this.destIp = destIpOrFarmName;
		this.destFarmName = destFarmName;
		this.fleets = false;
		this.bytesRecv = false;
		this.awbwLow = false;
		this.awbwHigh = false;
		setExitStat_value(PathloadResult.MEASUREMENT_STATUS_FAILED);
	}
	
	public synchronized String getNodeName() {
		StringBuilder sb = new StringBuilder();
		if (destFarmName != null) {
			sb.append(destFarmName);
		} else {
			sb.append("??");
		}
		sb.append("@");
		if (destIp != null) {
			sb.append(destIp);
		} else {
			sb.append("??");
		}
		
		return sb.toString();
	}
	
	/**
	 * public static String[] ResTypes = { "AwBandwidth_Low" 0 , "AwBandwidth_High" 1, 
		"MeasurementDuration" 2 , "FleetsSent 3", "MegaBytesReceived" 4, "MeasurementStatus" 5};
		
	 * @param rez
	 * @return
	 */
	public synchronized Result fillResult(Result rez) {
		if (rez == null) return null;
		
		int count = 0;
		if (fleets) 	{ count++; }
		if (bytesRecv) 	{ count++; }
		if (awbwLow) 	{ count++; }
		if (awbwHigh) 	{ count++; }
		if (measurementDuration) { count++; }
		if (exitStat)	{ count++; }
		
		String[] resTypes = new String[count];
		double[] values = new double[count];                               
		
		int crt = 0;
		if (fleets) 	{ resTypes[crt] = ResTypes[3]; values[crt] = fleets_value; crt++; }
		if (bytesRecv) 	{ resTypes[crt] = ResTypes[4]; values[crt] = bytesRecv_value; crt++; }
		if (awbwLow) 	{ resTypes[crt] = ResTypes[0]; values[crt] = awbwLow_value; crt++; }
		if (awbwHigh) 	{ resTypes[crt] = ResTypes[1]; values[crt] = awbwHigh_value; crt++; }
		if (measurementDuration) { resTypes[crt] = ResTypes[2]; values[crt] = measurementDuration_value; crt++; }
		if (exitStat)	{ resTypes[crt] = ResTypes[5]; values[crt] = exitStat_value; crt++; }
		
		rez.param_name = resTypes;
		rez.param = values;
		
		return rez;
	}
	
	/**
	 * @return Returns the awbwHigh_value.
	 */
	public synchronized double getAwbwHigh_value() {
		return awbwHigh_value;
	}
	
	/**
	 * @param awbwHigh_value The awbwHigh_value to set.
	 */
	public synchronized void setAwbwHigh_value(double awbwHigh_value) {
		this.awbwHigh = true;
		this.awbwHigh_value = awbwHigh_value;
	}
	
	/**
	 * 
	 * @param awbwHigh_strValue
	 */
	public synchronized void setAwbwHigh_value(String awbwHigh_strValue) {
		int index = -1;
		if ((awbwHigh_strValue == null) || (awbwHigh_strValue.length() < 5)) return ;
		if ((index = awbwHigh_strValue.indexOf("Mbps"))  > 0) {
			try {
				awbwHigh_value = Double.parseDouble(
						awbwHigh_strValue.substring(0, index));
				awbwHigh = true;
			} catch (NumberFormatException e) {
			} catch (NullPointerException e) {
			} catch (IndexOutOfBoundsException e) {
			}
		}
	}
	
	/**
	 * @return Returns the awbwLow_value.
	 */
	public synchronized double getAwbwLow_value() {
		return awbwLow_value;
	}
	
	/**
	 * @param awbwLow_value The awbwLow_value to set.
	 */
	public synchronized void setAwbwLow_value(double awbwLow_value) {
		this.awbwLow = true;
		this.awbwLow_value = awbwLow_value;
	}
	
	/**
	 * 
	 * @param awbwLow_strValue
	 */
	public synchronized void setAwbwLow_value(String awbwLow_strValue) {
		int index = -1;
		if ((awbwLow_strValue == null) || (awbwLow_strValue.length() < 5)) return ;
		if ((index = awbwLow_strValue.indexOf("Mbps"))  > 0) {
			try {
				awbwLow_value = Double.parseDouble(
						awbwLow_strValue.substring(0, index));
				awbwLow = true;
			} catch (NumberFormatException e) {
			} catch (NullPointerException e) {
			} catch (IndexOutOfBoundsException e) {
			}
		}
	}	
	
	/**
	 * @return Returns the bytesRecv_value.
	 */
	public synchronized double getBytesRecv_value() {
		return bytesRecv_value;
	}
	
	/**
	 * @param bytesRecv_value The bytesRecv_value to set.
	 */	
	public synchronized void setBytesRecv_value(double bytesRecv_value) {
		this.bytesRecv = true;
		this.bytesRecv_value = bytesRecv_value;
	}
	
	public synchronized void setBytesRecv_value(String bytesRecv_strValue) {
		if (bytesRecv_strValue == null) return ;
		
		try {
			bytesRecv_value = Double.parseDouble(bytesRecv_strValue);
			bytesRecv_value = bytesRecv_value / 1000000;
			bytesRecv = true;
		} catch (NumberFormatException e) {
		}
	}
	
	/**
	 * @return Returns the exitStat_value.
	 */
	public synchronized double getExitStat_value() {
		return exitStat_value;
	}
	/**
	 * @param exitStat_value The exitStat_value to set.
	 */
	public synchronized void setExitStat_value(double exitStat_value) {
		this.exitStat_value = exitStat_value;
		this.exitStat = true;
	}
	
	/**
	 * 
	 * @param exitStat_strValue
	 */
	public synchronized void setExitStat_value(String exitStat_strValue) {
		if (exitStat_strValue == null) return ;
		
		try {
			exitStat_value = Double.parseDouble(exitStat_strValue);
			exitStat = true;
		} catch (NumberFormatException e) {
		}
	}
	
	/**
	 * @return Returns the fleets_value.
	 */
	public synchronized double getFleets_value() {
		return fleets_value;
	}
	/**
	 * @param fleets_value The fleets_value to set.
	 */
	public synchronized void setFleets_value(double fleets_value) {
		this.fleets_value = fleets_value;
		this.fleets = true;
	}
	
	public synchronized void setFleets_value(String fleets_strValue) {
		if (fleets_strValue == null) return ;
		
		try {
			fleets_value = Double.parseDouble(fleets_strValue);
			fleets = true;
		} catch (NumberFormatException e) {
		}
	}
	
	/**
	 * @return Returns the measurementDuration_value.
	 */
	public synchronized double getMeasurementDuration_value() {
		return measurementDuration_value;
	}
	/**
	 * @param measurementDuration_value The measurementDuration_value to set.
	 */
	public synchronized void setMeasurementDuration_value(
			double measurementDuration_value) {
		this.measurementDuration_value = measurementDuration_value;
		this.measurementDuration = true;
	}
	/**
	 * @return Returns the destFarmName.
	 */
	public synchronized String getDestFarmName() {
		return destFarmName;
	}
	/**
	 * @return Returns the destIp.
	 */
	public synchronized String getDestIp() {
		return destIp;
	}
	
	/**
	 * @return Returns the awbwHigh.
	 */
	public synchronized boolean isAwbwHigh() {
		return awbwHigh;
	}
	/**
	 * @param awbwHigh The awbwHigh to set.
	 */
	public synchronized void setAwbwHigh(boolean awbwHigh) { 
		this.awbwHigh = awbwHigh;
	}
	/**
	 * @return Returns the awbwLow.
	 */
	public synchronized boolean isAwbwLow() {
		return awbwLow;
	}
	/**
	 * @param awbwLow The awbwLow to set.
	 */
	public synchronized void setAwbwLow(boolean awbwLow) {
		this.awbwLow = awbwLow;
	}
	/**
	 * @return Returns the bytesRecv.
	 */
	public synchronized boolean isBytesRecv() {
		return bytesRecv;
	}
	/**
	 * @param bytesRecv The bytesRecv to set.
	 */
	public synchronized void setBytesRecv(boolean bytesRecv) {
		this.bytesRecv = bytesRecv;
	}
	
	/**
	 * @return Returns the exitStat.
	 */
	public synchronized boolean isExitStat() {
		return exitStat;
	}
	/**
	 * @param exitStat The exitStat to set.
	 */
	public synchronized void setExitStat(boolean exitStat) {
		this.exitStat = exitStat;
	}
	/**
	 * @return Returns the fleets.
	 */
	public synchronized boolean isFleets() {
		return fleets;
	}
	/**
	 * @param fleets The fleets to set.
	 */
	public synchronized void setFleets(boolean fleets) {
		this.fleets = fleets;
	}
	/**
	 * @return Returns the measurementDuration.
	 */
	public synchronized boolean isMeasurementDuration() {
		return measurementDuration;
	}
	/**
	 * @param measurementDuration The measurementDuration to set.
	 */
	public synchronized void setMeasurementDuration(boolean measurementDuration) {
		this.measurementDuration = measurementDuration;
	}
}
