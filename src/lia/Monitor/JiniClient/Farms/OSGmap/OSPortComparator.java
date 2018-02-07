package lia.Monitor.JiniClient.Farms.OSGmap;

import java.util.Comparator;

import lia.Monitor.Agents.OpticalPath.OSPort;

public class OSPortComparator implements Comparator {

	public int compare(Object arg0, Object arg1) {
		
		if (!(arg0 instanceof OSPort)) return -1;
		if (!(arg1 instanceof OSPort)) return 1;
		OSPort p1  = (OSPort)arg0;
		OSPort p2 = (OSPort)arg1;
		if (!p1.type.equals(p2.type)) return -1;
		if (!p1.name.equals(p2.name)) return -1;
		return 0;
	}

}
