package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.Component;
import java.util.Vector;

/**
 * Inner representation of the parameters regarding an adjancency.
 */
public class Adjancency {

	public String name;
	public String currentCtrlCh;
	public Vector ctrlChList;
	public String localRid;
	public String remoteRid;
	public String ospfArea;
	public String metric;
	public String ospfAdj;
	public String adjType;
	public String adjIndex;
	public String rsvpRRFlag;
	public String rsvpGRFlag;
	public String ntfProc;
	
	private Component owner;

	public Adjancency(String name, Component owner) {
		this.name = name;
		this.owner = owner;
	}

	public void set(String currentCtrlCh, Vector ctrlChList, String localRid, String remoteRid, String ospfArea, String metric, String ospfAdj, String adjType,
			String adjIndex, String rsvpRRFlag, String rsvpGRFlag, String ntfProc) {
		
		this.currentCtrlCh = currentCtrlCh;
		this.ctrlChList = ctrlChList;
		this.localRid = localRid;
		this.remoteRid = remoteRid;
		this.ospfArea = ospfArea;
		this.metric = metric;
		if (ospfAdj.compareToIgnoreCase("Y") == 0 || ospfAdj.equals("Enabled")) ospfAdj = "Enabled";
		else if (ospfAdj.compareToIgnoreCase("N") == 0 || ospfAdj.equals("Disabled")) ospfAdj = "Disabled";
		else ospfAdj = "Enabled";
		this.ospfAdj = ospfAdj;
		this.adjIndex = adjIndex;
		this.adjType = adjType;
		if (rsvpRRFlag.compareToIgnoreCase("Y") == 0 || rsvpRRFlag.equals("Enabled")) rsvpRRFlag = "Enabled";
		else if (rsvpRRFlag.compareToIgnoreCase("N") == 0 || rsvpRRFlag.equals("Disabled")) rsvpRRFlag = "Disabled";
		else rsvpRRFlag = "Enabled";
		this.rsvpRRFlag = rsvpRRFlag;
		if (rsvpGRFlag.compareToIgnoreCase("Y") == 0 || rsvpGRFlag.equals("Enabled")) rsvpGRFlag = "Enabled";
		else if (rsvpGRFlag.compareToIgnoreCase("N") == 0 || rsvpGRFlag.equals("Disabled")) rsvpGRFlag = "Disabled";
		else rsvpGRFlag = "Enabled";
		this.rsvpGRFlag = rsvpGRFlag;
		if (ntfProc.compareToIgnoreCase("Y") == 0 || ntfProc.equals("Enabled")) ntfProc = "Enabled";
		else if (ntfProc.compareToIgnoreCase("N") == 0 || ntfProc.equals("Disabled")) ntfProc = "Disabled";
		else ntfProc = "Enabled";
		this.ntfProc = ntfProc;
	}
	
} // end of class Adjancency

