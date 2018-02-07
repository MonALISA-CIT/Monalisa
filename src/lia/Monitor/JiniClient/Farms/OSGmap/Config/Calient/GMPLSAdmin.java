package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.Component;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.monitor.eResult;

/**
 * Utility class that handles the transfer of commands.
 */
public class GMPLSAdmin {

	private OSAdminInterface admin;
	public GMPLSConfig config;
	
	public GMPLSAdmin(String swName, OSAdminInterface admin) {

		this.admin = admin;
		config = new GMPLSConfig(swName, this);
	}
	
	public void processeResult(eResult r) {

		if (r.ClusterName.equals("OS_GMPLSPorts")) {
			if (r.param == null || r.param.length == 0) return;
			String sports = (String)r.param[0];
			String ports[] = sports.split(",");
			if (ports == null) return;
			config.link.setFreePorts(ports);
			return;
		}
		if (r.ClusterName.equals("OS_GMPLSLink")) {
			String linkName = r.NodeName;
			String localIP = null, remoteIP = null, linkType = null, localRid = null, remoteRid = null, localIF = null, remoteIf = null, wdmRemoteIF = null,
			metric = null, lmpVerify = null, adjName = null, port = null;
			boolean fltDetect = false;
			for (int i=0; i<r.param_name.length; i++) {
				String param = r.param_name[i];
				if (param.equals("LINK-LinkType")) {
					linkType = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-LocalRID")) {
					localRid = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-RemoteRID")) {
					remoteRid = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-LocalIP")) {
					localIP = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-RemoteIP")) {
					remoteIP = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-LocalIFIndex")) {
					localIF = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-RemoteIF")) {
					remoteIf = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-WDMRemoteTEIF")) {
					wdmRemoteIF = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-FLTDetect")) {
					fltDetect = ((Boolean)r.param[i]).booleanValue();
					continue;
				}
				if (param.equals("LINK-Metric")) {
					metric = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-LMPVerify")) {
					lmpVerify = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-AdjName")) {
					adjName = (String)r.param[i];
					continue;
				}
				if (param.equals("LINK-PortName")) {
					port = (String)r.param[i];
					continue;
				}
			}
			config.link.addLink(linkName, localIP, remoteIP, localRid, remoteRid, linkType, adjName, "", localIF, remoteIf, wdmRemoteIF, lmpVerify, fltDetect, metric, port);
			return;
		}
		if (r.ClusterName.equals("OS_GMPLSAdj")) {
			String adjName = r.NodeName;
			String localRid = null, remoteRid = null, currentCtrlCh = null, adjIndex = null, ospfArea = null, metric = null, ospfAdj = null, adjType = null, rsvpRRFlag = null, rsvpGRFlag = null, ntfProc = null;
			Vector ctrlCh = new Vector();
			for (int i=0; i<r.param_name.length; i++) {
				String param = r.param_name[i];
				if (param.equals("ADJ-LocalRID")) {
					localRid = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-RemoteRID")) {
					remoteRid = (String)r.param[i];
					continue;
				}
				if (param.startsWith("ADJ-CtrlChName")) {
					ctrlCh.add(r.param[i]);
					continue;
				}
				if (param.equals("ADJ-CurrentCtrlChName")) {
					currentCtrlCh = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-AdjIndex")) {
					adjIndex = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-OSPFArea")) {
					ospfArea = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-Metric")) {
					metric = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-OSPFAdj")) {
					ospfAdj = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-AdjType")) {
					adjType = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-RSVPRRFlag")) {
					rsvpRRFlag = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-RSVPGRFlag")) {
					rsvpGRFlag = (String)r.param[i];
					continue;
				}
				if (param.equals("ADJ-NTFProc")) {
					ntfProc = (String)r.param[i];
				}
			}
			config.adj.addAdj(adjName, localRid, remoteRid, ctrlCh, currentCtrlCh, adjIndex, ospfAdj, ospfArea, metric, adjType, rsvpRRFlag, rsvpGRFlag, ntfProc);
			return;
		}
		if (r.ClusterName.equals("OS_GMPLSNPPorts")) {
			String port = r.NodeName;
			String ip = null, mask = null, gw = null;
			for (int i=0; i<r.param_name.length; i++) {
				String param = r.param_name[i];
				if (param.equals("NP-IP")) {
					ip = (String)r.param[i];
					continue;
				}
				if (param.equals("NP-Mask")) {
					mask = (String)r.param[i];
					continue;
				}
				if (param.equals("NP-Gateway")) {
					gw = (String)r.param[i];
				}
			}
			config.npPorts.setEqpt(port, ip, mask, gw);
			config.ctrlCh.addNPPort(port);
			return;
		}
		if (r.ClusterName.equals("OS_GMPLSCtrlCh")) {
			String name = r.NodeName;
			String lip = null, rip = null, lrid = null, rrid = null, adj = null, h = null, hmin = null, hmax = null, d = null, dmin = null, dmax = null, port = null;
			for (int i=0; i<r.param_name.length; i++) {
				String param = r.param_name[i];
				if (param.equals("CTRLCH-LocalIP")) {
					lip = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-RemoteIP")) {
					rip = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-LocalRID")) {
					lrid = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-RemoteRID")) {
					rrid = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-Port")) {
					port = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-Adjacency")) {
					adj = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-HelloInterval")) {
					h = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-HelloIntervalMin")) {
					hmin = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-HelloIntervalMax")) {
					hmax = (String)r.param[i];
					continue;
				}
				if (param.equals("CTRLCH-DeadInterval")) {
					d = (String)r.param[i];
					continue;
				}
				if (param.equals( "CTRLCH-DeadIntervalMin")) {
					dmin = (String)r.param[i];
					continue;
				}
				if (param.equals( "CTRLCH-DeadIntervalMax")) {
					dmax = (String)r.param[i];
				}
			}
			config.ctrlCh.addCtrlCh(name, lip, rip, lrid, rrid, port, adj, h, hmin, hmax, d, dmin, dmax);
			return;
		}
		
		for (int i=0; i<r.param_name.length; i++) {
			String param = r.param_name[i];
			if (param.equals("OSPF-RouterID")) {
				if (!config.routerIDFlag) {
					config.routerIDFlag = true;
					config.routerID.setText((String)r.param[i]);
					config.routerID.setEnabled(true);
					config.ospfModify.setEnabled(true);
				}
				continue;
			}
			if (param.equals("OSPF-AreaID")) {
				if (!config.areaIDFlag) {
					config.areaIDFlag = true;
					config.areaID.setText((String)r.param[i]);
					config.areaID.setEnabled(true);
					config.ospfModify.setEnabled(true);
				}
				continue;
			}
			if (param.equals("RSVP-MsgRetryInvl")) {
				if (!config.msgRetryInvlFlag) {
					config.msgRetryInvlFlag = true;
					config.msgRetryInvl.setText((String)r.param[i]);
					config.msgRetryInvl.setEnabled(true);
					config.rsvpModify.setEnabled(true);
				}
				continue;
			}
			if (param.equals("RSVP-NtfRetryInvl")) {
				if (!config.ntfRetryInvlFlag) {
					config.ntfRetryInvlFlag = true;
					config.ntfRetryInvl.setText((String)r.param[i]);
					config.ntfRetryInvl.setEnabled(true);
					config.rsvpModify.setEnabled(true);
				}
				continue;
			}
			if (param.equals("RSVP-GrInvl")) {
				if (!config.grInvlFlag) {
					config.grInvlFlag = true;
					config.grInvl.setText((String)r.param[i]);
					config.grInvl.setEnabled(true);
					config.rsvpModify.setEnabled(true);
				}
				continue;
			}
			if (param.equals("RSVP-GrcvInvl")) {
				if (!config.grcvInvlFlag) {
					config.grcvInvlFlag = true;
					config.grcvInvl.setText((String)r.param[i]);
					config.grcvInvl.setEnabled(true);
					config.rsvpModify.setEnabled(true);
				}
				continue;
			}
		}
	}
	
	public JFrame getWindow() {
		return config;
	}
	
	public static void showError(Component component, String error) {
		JOptionPane.showMessageDialog(component, error, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	public static void showOk(Component component, String msg) {
		JOptionPane.showMessageDialog(component, msg, "OK", JOptionPane.INFORMATION_MESSAGE);
	}
	
	public void changeNPPort(String port, String ip, String mask, String gw) {
		try {
			String ret = admin.changeNPPort(port, ip, mask, gw);
			if (!ret.equals("OK"))
				showError(config, "Method changeNPPort did not complete correctly: "+ret);
			else
				showOk(config, "Method changeNPPort completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doiing changeNPPort: "+ex);
		}
	}
	
	public void changeOSPF(String routerID, String areaID) {
		try {
			String ret = admin.changeOSPF(routerID, areaID);
			if (!ret.equals("OK"))
				showError(config, "Method changeOSPF did not complete correctly: "+ret);
			else
				showOk(config, "Method changeOSPF completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing changeOSPF: "+ex);
		}
	}
	
	public void changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcvInvl) {
		try {
			String ret = admin.changeRSVP(msgRetryInvl, ntfRetryInvl, grInvl, grcvInvl);
			if (!ret.equals("OK"))
				showError(config, "Method changeRSVP did not complete correctly: "+ret);
			else
				showOk(config, "Method changeRSVP completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing changeRSVP: "+ex);
		}
	}
	
	public void addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl, String helloInvlMin, String helloInvlMax, 
			String deadInvl, String deadInvlMin, String deadInvlMax) {
		try {
			String ret = admin.addCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax, deadInvl, deadInvlMin, deadInvlMax);
			if (!ret.equals("OK"))
				showError(config, "Method addCtrlCh did not complete correctly: "+ret);
			else
				showOk(config, "Method addCtrlCh completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing addCtrlCh: "+ex);
		}
	}
	
	public void deleteCtrlCh(String name) {
		try {
			String ret = admin.delCtrlCh(name);
			if (!ret.equals("OK"))
				showError(config, "Method deleteCtrlCh did not complete correctly: "+ret);
			else
				showOk(config, "Method deleteCtrlCh completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing deleteCtrlCh: "+ex);
		}
	}
	
	public void changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl, String helloInvlMin, String helloInvlMax,
			String deadInvl, String deadInvlMin, String deadInvlMax) {
		try {
			String ret = admin.changeCtrlCh(name, remoteIP, remoteRid, port, adj, helloInvl, helloInvlMin, helloInvlMax, deadInvl, deadInvlMin, deadInvlMax);
			if (!ret.equals("OK"))
				showError(config, "Method changeCtrlCh did not complete correctly: "+ret);
		} catch (Exception ex) {
			showError(config, "Got error doing changeCtrlCh: "+ex);
		}
	}
	
	public void addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj, String adjType, String rsvpRRFlag, 
			String rsvpGRFlag, String ntfProc) {
		try {
			String ret = admin.addAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag, rsvpGRFlag, ntfProc);
			if (!ret.equals("OK"))
				showError(config, "Method addAdj did not complete correctly: "+ret);
			else
				showOk(config, "Method addAdj completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing addAdj: "+ex);
		}
	}
	
	public void deleteAdj(String name) {
		try {
			String ret = admin.deleteAdj(name);
			if (!ret.equals("OK"))
				showError(config, "Method delAdj did not complete correctly: "+ret);
			else
				showOk(config, "Method delAdj completed ok");
		} catch (Exception ex) {
			showError(config, "Got error doing delAdj: "+ex);
		}
	}
	
	public void changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj, String adjType, String rsvpRRFlag, 
			String rsvpGRFlag, String ntfProc) {
		try {
			String ret = admin.changeAdj(name, ctrlCh, remoteRid, ospfArea, metric, ospfAdj, adjType, rsvpRRFlag, rsvpGRFlag, ntfProc);
			if (!ret.equals("OK"))
				showError(config, "Method changeAdj did not complete correctly: "+ret);
		} catch (Exception ex) {
			showError(config, "Got error doing changeAdj: "+ex);
		}
	}
	
	public void addLink(String name, String localIP, String remoteIP, String adj) {
		
		try {
			String ret = admin.addLink(name, localIP, remoteIP, adj);
			if (!ret.equals("OK"))
				showError(config, "Method addLink did not complete correctly: "+ret);
		} catch (Exception ex) {
			showError(config, "Got error doing addLink: "+ex);
		}
	}
	
	public void delLink(String name) {
		
		try {
			String ret = admin.delLink(name);
			if (!ret.equals("OK"))
				showError(config, "Method delLink did not complete correctly: "+ret);
		} catch (Exception ex) {
			showError(config, "Got error doing delLink: "+ex);
		}
	}
	
	public void changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj, 
			String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port) {
		
		try {
			String ret = admin.changeLink(name, localIP, remoteIP, linkType, adj, wdmAdj, remoteIf, wdmRemoteIf, lmpVerify, fltDetect, 
					metric, port);
			if (!ret.equals("OK"))
				showError(config, "Method changeLink did not complete correctly: "+ret);
		} catch (Exception ex) {
			showError(config, "Got error doing changeLink: "+ex);
		}
	}
	
} // end of class GMPLSAdmin


