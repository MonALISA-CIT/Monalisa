package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.Component;

/**
 * A class that represent a control channel.
 */
public class CtrlCH {

	public String name;
	public String localIP;
	public String remoteIP;
	public String localRid;
	public String remoteRid;
	public String port;
	public String adj;
	public String helloInvl;
	public String helloInvlMin;
	public String helloInvlMax;
	public String deadInvl;
	public String deadInvlMin;
	public String deadInvlMax;
	
	private Component owner;
	
	public CtrlCH(String name, Component owner) {
		this.name = name;
		this.owner = owner;
	}
	
	
	public void set(String localIP, String remoteIP, String localRid, String remoteRid, String port, String adj, String hInvl, String hInvlMin, String hInvlMax,
			String dInvl, String dInvlMin, String dInvlMax) {

		this.localIP = localIP;
		this.remoteIP = remoteIP;
		this.localRid = localRid;
		this.remoteRid = remoteRid;
		this.port = port;
		this.adj = adj;
		this.helloInvl = hInvl;
		this.helloInvlMin = hInvlMin;
		this.helloInvlMax = hInvlMax;
		this.deadInvl = dInvl;
		this.deadInvlMin = dInvlMin;
		this.deadInvlMax = dInvlMax;
	}
} // end of class CtrlCH 


