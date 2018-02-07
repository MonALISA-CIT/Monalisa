package lia.Monitor.JiniClient.CommonGUI;

import net.jini.core.lookup.ServiceID;

public class DLink implements java.io.Serializable {
	public ServiceID fromSid;
	public ServiceID toSid;
	public double dist;

	public DLink(ServiceID fromSid, ServiceID toSid, double dist) {
		this.dist = dist;
		this.fromSid = fromSid;
		this.toSid = toSid;
	}
	
	public DLink() {
	}
	
	public boolean cequals(ServiceID from, ServiceID to) {
		if ((fromSid.equals(from)) && (toSid.equals(to)))
			return true;
		return false;
	}
	
	public String toString() {
		return " DL  ==  " + fromSid + " ---> " + toSid + "[" + dist + "]";
	}
}
