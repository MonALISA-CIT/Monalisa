package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.util.ArrayList;
import java.util.Vector;

import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.tcpClient.MLSerClient;

public class OpticalTooltipResult implements LocalDataFarmClient {

	private ArrayList portList = null;
	
	public OpticalTooltipResult() {
		
		portList = new ArrayList();
	}
	
	public synchronized ArrayList getPortList() {
		
		return portList;
	}
	
	public void newFarmResult(MLSerClient client, Object res) {
		if (res instanceof Result) {
			processResult((Result)res);
		} else if (res instanceof Vector) {
			Vector  v = (Vector)res;
			for (int i=0; i<v.size(); i++) {
				Object o = v.get(i);
				if (o instanceof Result) {
					processResult((Result)o);
				} else if (o instanceof eResult) {
					processResult((eResult)o);
				}
			}
		} else if (res instanceof eResult) {
			processResult((eResult)res);
		}
	}

	private synchronized void processResult(Result res) {
		
		if (res == null || res.param_name == null || res.param == null) return;
		for (int i=0; i<res.param_name.length; i++) {
			if (res.param_name[i].equals("Port-Power")) {
				String portName = res.NodeName;
				if (portName == null) continue;
				if (portName.endsWith("_In"))
					portName = portName.substring(0, portName.length()-3);
				if (portName.endsWith("_Out"))
					portName = portName.substring(0, portName.length()-4);
				if (!portList.contains(portName))
					portList.add(portName);
			}
		}
	}
	
	private synchronized void processResult(eResult res) {
		
		if (res.param == null || res.param.length == 0) return;
		try {
			ArrayList list = (ArrayList)res.param[0];
			portList = list;
		} catch (Exception ex) { }
	}
	
} // end of class OpticalTooltipResult

