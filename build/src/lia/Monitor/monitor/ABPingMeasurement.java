
package lia.Monitor.monitor;


public class ABPingMeasurement {

	public double RTime;
	public double RTT;
	public double Jitter ;
	public double PacketLoss ;
	
	public ABPingMeasurement (double RTime, double RTT, double Jitter, double PacketLoss) {
		this.RTime = RTime ;
		this.RTT = RTT;
		this.Jitter = Jitter ;
		this.PacketLoss = PacketLoss ;
	} // ABPingMeasurement
	
} // ABPingMeasurement
