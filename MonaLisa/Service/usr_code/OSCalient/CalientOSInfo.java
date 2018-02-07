import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/** 
 * This is an object carried the current monitored configuration of a Calient OS.
 */
public class CalientOSInfo implements Serializable {

	public static byte unk = -1; // unknown value for all below
	
	// administrative state values
	public static byte as_is = 0; // eqpt in service (can provide service and is monitored for alarms and faults)
	public static byte as_uma = 1; // eqpt under maintanance (can provide service , but is not monitored for alarms)
	public static byte as_oos = 2; // out of service (the eqpt is disabled)
	public static byte as_oos_np = 3; // out of service - not provisioned (the eqpt has not been added to the system)
	
	// alarm status values
	public static byte as_cr = 0; // critical alarms
	public static byte as_mj = 1; // major alarms
	public static byte as_mn = 2; // minor alarms
	public static byte as_cl = 3; // cleared for alarms
	
	// operational states of an eqpt
	public static byte os_is = 0; // is rendering service
	public static byte os_oos = 1; // not rendering service
	public static byte os_ready = 2; // is rendering service, but not monitored for alarms
	public static byte os_init = 3; // in the process of initializing
	public static byte os_degraded = 4; // is rendering limited service 
	public static byte os_nohw = 5; // no hardware
	
	// operational capacities
	public static byte oc_ok = 0; // healthy
	public static byte oc_degraded = 1; // is rendering limited service 
	public static byte oc_init = 2; // in the process of initialization
	public static byte oc_failed = 3; // in a failed state
	public static byte oc_nohw = 4; // no eqpt
	
	// redundancy states
	public static byte rs_act = 0; // primary role
	public static byte rs_stbyh = 1; // secondary role
	public static byte rs_nred = 2; // not a redundant eqpt
	
	// alarm efect type for the service that generated it
	public static byte srveff_nsa = 0; // non-service-affecting condition
	public static byte srveff_sa = 1; // service-affecting condition
	
	// connection types
	public static byte conn_1way = 0; // 1 way conn
	public static byte conn_2way = 1; // duplex conn
	
	// waveband constraints
	public static byte wb_WBand = 0; // wavelengths between 1260 and 1625 nm
	public static byte wb_CBand = 1; // wavelengths between 1530 and 1565 nm
	public static byte wb_LBand = 2; // wavelengths between 1565 and 1610 nm
	public static byte wb_XLBand = 3; // wavelengths between 1610 and 1625 nm
	public static byte wb_OBand = 4; // wavelengths between 1260 and 1360 nm
	
	// protected states values
	public static byte ps_PRT = 0; // has a functioning peer
	public static byte ps_UPR = 1;  // has a non-functioning peer
	
	// status values
	public static byte stat_INIT = 0; // system boot up
	public static byte stat_DEGR = 1; // missing hardware
	public static byte stat_FAIL = 2; // failed hardware
	public static byte stat_OK = 3; // all ok
	
	// adjancecy types
	public static byte adj_CALIENTGMPLSPEER = 0; 
	public static byte adj_GMPLSOVERLAY = 1;
	public static byte adj_GMPLSPEER = 2;
	public static byte adj_GMPLSWDM = 3;
	
	// link types
	public static byte te_NUMBERED = 0; // numbered link -> use ip 
	public static byte te_UNNUMBERED = 1; // unnumbered link -> use te if index
	
	// service classes for connections
	public static byte srv_UPR = 0; // connection is unprotected
	public static byte srv_SM = 1; // connection is protected
	
	// equipment list
	public Hashtable shelves = null; // shelved eqptID -> CalientShelfEqpt
	public Hashtable switchingMatrix = null; // switching matrix parameters swID -> CalientSwitchingMatrix
	public Hashtable crossConnects = null; // cross connects aid -> CalientCrossConnect
	public Vector groupCrossConnects = null; // the names of the groups Cross Connection names
	public Vector alarms = null; // the list of all the current alarms
	public String serialNumber = "N/A"; // the serial number of the eqpt
	public String softwareVersion = "N/A"; // the version of the software running
	public byte status = unk; // the global status of the eqpt
	
	// NetworkConfiguration parameters
	public CalientOSPF ospf = null; // the parameters for OSPF protocol
	public CalientRSVP rsvp = null; // the parameters for RSVP protocol
	public Hashtable ctrlchs = null; // the control channels (aid -> CalientCtrlCh)
	public Hashtable adjacencies = null; // the adjacencies (aid -> CalientAdjacency)
	public Hashtable links = null; // the te links (aid -> CalientLink)
	public Hashtable connections = null; // current connection (name -> CalientConn)
	
	// global network configuration parameters
	public String bpInvl = "N/A"; // connection bid period timer
	public String initConnSetupTmoutl = "N/A";  // Initiator Connection Setup Timeout
	public String minRetryInvl = "N/A"; // minimum number of connection retries
	public String maxNumConnRetryl = "N/A"; // maximum number of connection retries
	public String slowInvl = "N/A"; // connection slow timer
	public String initRespTmout = "N/A"; // initiator response timeout
	public String maxConnPb = "N/A"; // maximum number of connections to be tried in each bin

	public void addShelf(String eqptID, String shelfKind, String status, String freePorts, String connectedPorts) {
		
		CalientShelfEqpt shelf = new CalientShelfEqpt(eqptID, shelfKind, status, freePorts, connectedPorts);
		if (shelves == null) shelves = new Hashtable();
		shelves.put(eqptID, shelf);
	}
	
	
	public void addCard(String shelfEqptID, String eqptID, String cardType, String as, String os, String oc, String rs, String al) {
		
		if (!shelves.containsKey(shelfEqptID)) return;
		CalientShelfEqpt shelf = (CalientShelfEqpt)shelves.get(shelfEqptID);
		shelf.addCard(eqptID, cardType, as, os, oc, rs, al);
	}
	
	
	public void addCard(String shelfEqptID, String eqptID, String cardType, String as, String os, String oc, String rs, String al, String preferredMatrix) {
		
		if (!shelves.containsKey(shelfEqptID)) return;
		CalientShelfEqpt shelf = (CalientShelfEqpt)shelves.get(shelfEqptID);
		shelf.addCard(eqptID, cardType, as, os, oc, rs, al, preferredMatrix);
	}
	
	
	public void addCPPort(String shelfEqptID, String cardEqptID, String eqptID, String ip, String mask, String gw, String as, String os, String oc, String al) {
		
		if (!shelves.containsKey(shelfEqptID)) return;
		CalientShelfEqpt shelf = (CalientShelfEqpt)shelves.get(shelfEqptID);
		shelf.addCPPort(cardEqptID, eqptID, ip, mask, gw, as, os, oc, al);
	}
	
	
	public void addIOPort(String shelfEqptID, String cardEqptID, String eqptID, String ias, String ios, String ioc, String oas, String oos, String ooc, String al) {
		
		if (!shelves.containsKey(shelfEqptID)) return;
		CalientShelfEqpt shelf = (CalientShelfEqpt)shelves.get(shelfEqptID);
		shelf.addIOPort(cardEqptID, eqptID, ias, ios, ioc, oas, oos, ooc, al);
	}
	

	public void addPort(String shelfEqptID, String cardEqptID, String eqptID, String as, String os, String oc, String al) {
		
		if (!shelves.containsKey(shelfEqptID)) return;
		CalientShelfEqpt shelf = (CalientShelfEqpt)shelves.get(shelfEqptID);
		shelf.addPort(cardEqptID, eqptID, as, os, oc, al);
	}

	
	public void addAlarm(String aid, String ntfcncde, String condType, String srveff, String ocrdat, String ocrtm, String condDescr) {
		
		CalientAlarm alarm = new CalientAlarm(aid, ntfcncde, condType, srveff, ocrdat, ocrtm, condDescr);
		if (alarms == null) alarms = new Vector();
		alarms.add(alarm);
	}
	
	
	public void addSwitchingMatrix(String aid, String degradeThreshold, String criticalThreshold) {
		
		CalientSwitchMatrix sm = new CalientSwitchMatrix(aid, degradeThreshold, criticalThreshold);
		if (switchingMatrix == null) switchingMatrix = new Hashtable();
		switchingMatrix.put(aid, sm);
	}
	
	
	public void addCrossConnect(String aid, String srcPort, String dstPort, String groupName, String connName, String connType, String status, String as, 
				String os, String oc, String ps, String al, String matrixUsed) {
		
		CalientCrossConnect cc = new CalientCrossConnect(srcPort, dstPort, groupName, connName, connType, status, as, os, oc, ps, al, matrixUsed);
		if (crossConnects == null) crossConnects = new Hashtable();
		crossConnects.put(aid, cc);
	}
	
	
	public void addGroupCrossConnect(String groupName) {
		
		if (groupCrossConnects == null) groupCrossConnects = new Vector();
		groupCrossConnects.add(groupName);
	}
	
	
	public void addNE(String serialNumber, String swpackage, String status) {
		
		this.serialNumber = serialNumber;
		this.softwareVersion = swpackage;
		if (status.equals("INIT"))
			this.status = stat_INIT;
		else if (status.equals("DEGR"))
			this.status = stat_DEGR;
		else if (status.equals("FAIL"))
			this.status = stat_FAIL;
		else if (status.equals("OK"))
			this.status = stat_OK;
		else 
			this.status = unk;
	}
	
	
	public void addDetailsEqpt(String aid, String SwVersion, String DSPSwVersion, String PartNum, String RevNum, String SerialNum, String BoardID, String FpgaRevNum,
				String DiagCodeRevNum, String BootCodeRevNum, String Date, String Time, String InConnected, String OutConnected, String Hardware, 
				String FullDuplex, String InConnectionID, String InConnectionGroupID, String InCircuitID, String InActive, String InOpticalPower, String InOpticalPowerCrossing,
				String InDegradedOpticalPowerCrossing, String OutConnectionID, String OutConnectionGroupID, String OutCircuitID, String OutActive, String OutOpticalPowerWorking, 
				String OutOpticalPowerCrossingCritWorking,  String OutOpticalPowerCrossingDegWorking) {
		
		if (shelves == null) return;
		for (Enumeration en = shelves.elements(); en.hasMoreElements(); ) {
			CalientShelfEqpt shelf = (CalientShelfEqpt)en.nextElement();
			if (shelf.children == null) continue;
			for (Enumeration en1 = shelf.children.elements(); en1.hasMoreElements(); ) {
				CalientCardEqpt card = (CalientCardEqpt)en1.nextElement();
				if (card.eqptID.equals(aid)) {
					card.details = new CalientDetailsEqpt(SwVersion, DSPSwVersion, PartNum, RevNum, SerialNum, BoardID, FpgaRevNum,
							DiagCodeRevNum, BootCodeRevNum, Date, Time, InConnected, OutConnected, Hardware, 
							FullDuplex, InConnectionID, InConnectionGroupID, InCircuitID, InActive, InOpticalPower, InOpticalPowerCrossing,
							InDegradedOpticalPowerCrossing, OutConnectionID, OutConnectionGroupID, OutCircuitID, OutActive, OutOpticalPowerWorking, 
							OutOpticalPowerCrossingCritWorking,  OutOpticalPowerCrossingDegWorking);
					return;
				}
				if (card.children == null) continue;
				for (Enumeration en2 = card.children.elements(); en2.hasMoreElements(); ) {
					CalientPortEqpt port = (CalientPortEqpt)en2.nextElement();
					if (port.eqptID.equals(aid)) {
						port.details = new CalientDetailsEqpt(SwVersion, DSPSwVersion, PartNum, RevNum, SerialNum, BoardID, FpgaRevNum,
								DiagCodeRevNum, BootCodeRevNum, Date, Time, InConnected, OutConnected, Hardware, 
								FullDuplex, InConnectionID, InConnectionGroupID, InCircuitID, InActive, InOpticalPower, InOpticalPowerCrossing,
								InDegradedOpticalPowerCrossing, OutConnectionID, OutConnectionGroupID, OutCircuitID, OutActive, OutOpticalPowerWorking, 
								OutOpticalPowerCrossingCritWorking,  OutOpticalPowerCrossingDegWorking);
						return;
					}
				}
			}
		}
	}
	
	
	public void addEnvEqpt(String aid, String EqptType, String Temperature, String AdcBus, String Memory, String CPU, String AtaDisk, String FlashDisk, String Fan,
				String Volt160, String Volt5, String Voltminus5, String TempMPC, String TempLowVoltPS, String TempOptSwPln2, String Time,
				String TempOptSwPln1, String DSP0Status, String DSP1Status) {
		
		if (shelves == null) return;
		for (Enumeration en = shelves.elements(); en.hasMoreElements(); ) {
			CalientShelfEqpt shelf = (CalientShelfEqpt)en.nextElement();
			if (shelf.children == null) continue;
			for (Enumeration en1 = shelf.children.elements(); en1.hasMoreElements(); ) {
				CalientCardEqpt card = (CalientCardEqpt)en1.nextElement();
				if (card.eqptID.equals(aid)) {
					card.env = new CalientEnvEqpt(EqptType, Temperature, AdcBus, Memory, CPU, AtaDisk, FlashDisk, Fan, Volt160, Volt5, Voltminus5, TempMPC, 
							TempLowVoltPS, TempOptSwPln2, Time, TempOptSwPln1, DSP0Status, DSP1Status);
					return;
				}
				if (card.children == null) continue;
				for (Enumeration en2 = card.children.elements(); en2.hasMoreElements(); ) {
					CalientPortEqpt port = (CalientPortEqpt)en2.nextElement();
					if (port.eqptID.equals(aid)) {
						port.env = new CalientEnvEqpt(EqptType, Temperature, AdcBus, Memory, CPU, AtaDisk, FlashDisk, Fan, Volt160, Volt5, Voltminus5, TempMPC, 
								TempLowVoltPS, TempOptSwPln2, Time, TempOptSwPln1, DSP0Status, DSP1Status);
						return;
					}
				}
			}
		}
	}
	
	
	public void addMemEqpt(String aid, String EqptType, String FreeMemory) {
		
		if (shelves == null) return;
		for (Enumeration en = shelves.elements(); en.hasMoreElements(); ) {
			CalientShelfEqpt shelf = (CalientShelfEqpt)en.nextElement();
			if (shelf.children == null) continue;
			for (Enumeration en1 = shelf.children.elements(); en1.hasMoreElements(); ) {
				CalientCardEqpt card = (CalientCardEqpt)en1.nextElement();
				if (card.eqptID.equals(aid)) {
					card.mem = new CalientMemEqpt(FreeMemory, EqptType);
					return;
				}
				if (card.children == null) continue;
				for (Enumeration en2 = card.children.elements(); en2.hasMoreElements(); ) {
					CalientPortEqpt port = (CalientPortEqpt)en2.nextElement();
					if (port.eqptID.equals(aid)) {
						port.mem = new CalientMemEqpt(FreeMemory, EqptType);
						return;
					}
				}
			}
		}
	}
	
	
	public void addOSPF(String routerID, String areaID) {
		
		this.ospf = new CalientOSPF(routerID, areaID);
	}
	
	
	public void addRSVP(String msgRetryInvl, String ntfRertyInvl, String grInvl, String grcvInvl) {
		
		this.rsvp = new CalientRSVP(msgRetryInvl, ntfRertyInvl, grInvl, grcvInvl);
	}
	
	
	public void addCtrlCh(String aid, String localIP, String remoteIP, String localRid, String remoteRid, String port, String localIfIndex, String remoteIfIndex, 
			String adjancecy, String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax, String helloInvlNegotiated,
			String deadInvlNegotiated, String as, String os, String oc, String al) {
		
		CalientCtrlCh  ctrlch = new CalientCtrlCh(localIP, remoteIP, localRid, remoteRid, port, localIfIndex, remoteIfIndex, adjancecy, helloInvl, 
				helloInvlMin, helloInvlMax, deadInvl, deadInvlMin, deadInvlMax, helloInvlNegotiated, deadInvlNegotiated, as, os, oc, al);
		if (ctrlchs == null) ctrlchs = new Hashtable();
		ctrlchs.put(aid, ctrlch);
	}
	
	
	public void addAdjancecy(String aid, String localRid, String remoteRid, String currentCtrlChannel, String adjIndex, String ospfArea, String metric, String ospfAdy,
				String adjType, String rsvpRRFlag, String rsvpRGFlag, String ntfProc, String as, String os, String oc, String ps, String al, Vector ctrlChannels) {
		
		CalientAdjacency adj = new CalientAdjacency(localRid, remoteRid, currentCtrlChannel, adjIndex, ospfArea, metric, ospfAdy,
				adjType, rsvpRRFlag, rsvpRGFlag, ntfProc, as, os, oc, ps, al);
		if (adjacencies == null) adjacencies = new Hashtable();
		adjacencies.put(aid, adj);
		if (ctrlChannels != null && ctrlChannels.size()  != 0)
			for (int i=0; i<ctrlChannels.size(); i++) {
				String ctrlCh = (String)ctrlChannels.elementAt(i);
				if (ctrlchs.containsKey(ctrlCh)) {
					CalientCtrlCh ch = (CalientCtrlCh)ctrlchs.get(ctrlCh);
					adj.addCtrlChannel(ch);
				}
			}
	}	

	public void addTELink(String aid, String linkType, String localRid, String remoteRid, String localIP, String remoteIP, String adjName, String localIfIndex, 
				String remoteIf, String wdmRemoteIf, String fptDetect, String metric, String lmpVerify, String adjType, String admin, 
				String[]totalBandwidth, String[]availableBandwidth, String port, String lspEncoding, String bandwidth, String portMinPriority, String portRemPortLabel,
				String portLolState, String swCap, String as, String os, String oc, String al, String color) {
		
		CalientLink link = new CalientLink(aid, linkType, localRid, remoteRid, localIP, remoteIP, adjName, localIfIndex, 
				remoteIf, wdmRemoteIf, fptDetect, metric, lmpVerify, adjType, admin, 
				totalBandwidth, availableBandwidth, port, lspEncoding, bandwidth, portMinPriority, portRemPortLabel,
				portLolState, swCap, as, os, oc, al, color);
		if (links == null) links = new Hashtable();
		links.put(aid, link);
	}
	
	
	public void addConn(String aid, String groupName, String connType, String srcIP, String ingressPortIn, String egressPortOut, String ingressPortOut, String egressPortIn,
			String dstIP, String dstPortIn, String dstPortOut, String nt, String srvClass, String upnt, String dnnt, String outTELink, String adjID, String extAdj,
			String lspEncoding, String lspPayload, String bandwidth, String setPrio, String holdPrio, String maxHop, String recroute, String proIP, 
			String proIfIf, String proHt, String as, String os, String oc, String al, String color) {
		
		CalientConn conn = new CalientConn(groupName, connType, srcIP, ingressPortIn, egressPortOut, ingressPortOut, egressPortIn,
				dstIP, dstPortIn, dstPortOut, nt, srvClass, upnt, dnnt, outTELink, adjID, extAdj,
				lspEncoding, lspPayload, bandwidth, setPrio, holdPrio, maxHop, recroute, proIP, 
				proIfIf, proHt, as, os, oc, al, color);
		if (connections == null) connections = new Hashtable();
		connections.put(aid, conn);
	}
	
	public void addCfgConn(String bpInvl, String initConnSetupTmout, String minRetryInvl, String maxNumConnRetry, String slowInvl, String initRespTmout, String maxConnPb) {

		this.bpInvl = bpInvl;
		this.initConnSetupTmoutl = initConnSetupTmout;
		this.minRetryInvl = minRetryInvl;
		this.maxNumConnRetryl = maxNumConnRetry;
		this.slowInvl = slowInvl;
		this.initRespTmout = initRespTmout;
		this.maxConnPb = maxConnPb;
	}
	
	
	public class CalientShelfEqpt implements Serializable {
		
		public String eqptID; // the id of the equipment 
		public String shelfKind; // the kind of shelf
		public byte alarmStatus; // the status of the shelf;
		public int freePorts; // the number of free ports 
		public int connectedPorts; // the number of connected ports in this shelf
		public Hashtable children = null; // the list of sub-equiments (cards) eqptID -> CalientCardEqpt
		
		public CalientShelfEqpt(String eqptID, String shelfKind, String status, String freePorts, String connectedPorts) {
			
			this.eqptID = eqptID;
			this.shelfKind = shelfKind;
			if (status.equals("CR"))
				alarmStatus = as_cr;
			else if (status.equals("MJ"))
				alarmStatus = as_mj;
			else if (status.equals("MN"))
				alarmStatus = as_mn;
			else if (status.equals("CL"))
				alarmStatus = as_cl;
			else
				alarmStatus = unk;
			int d = -1;
			try {
				d = Integer.parseInt(freePorts);
			} catch (Throwable t) {
				d = -1;
			}
			if (d > 0)
				this.freePorts = d;
			else
				this.freePorts = 0;
			d = -1;
			try {
				d = Integer.parseInt(connectedPorts);
			} catch (Throwable t) {
				d = -1;
			}
			if (d > 0)
				this.connectedPorts = d;
			else
				this.connectedPorts = 0;
			children = new Hashtable();
		}
		
		public void addCard(String eqptID, String cardType, String as, String os, String oc, String rs, String al) { 
			
			CalientCardEqpt card = new CalientCardEqpt(eqptID, cardType, as, os, oc, rs, al);
			children.put(eqptID, card);
		}
		
		public void addCard(String eqptID, String cardType, String as, String os, String oc, String rs, String al, String preferredMatrix) {
			
			CalientCardSwitchMatrixEqpt card = new CalientCardSwitchMatrixEqpt(eqptID, cardType, as, os, oc, rs, al, preferredMatrix);
			children.put(eqptID, card);
		}
		
		public void addCPPort(String cardEqptID, String eqptID, String ip, String mask, String gateway, String as, String os, String oc, String al) {
			
			if (!children.containsKey(cardEqptID)) return;
			CalientCardEqpt card = (CalientCardEqpt)children.get(cardEqptID);
			card.addCPPort(eqptID, ip, mask, gateway, as, os, oc, al);
		}
		
		public void addIOPort(String cardEqptID, String eqptID, String ias, String ios, String ioc, String oas, String oos, String ooc, String al) {
			
			if (!children.containsKey(cardEqptID)) return;
			CalientCardEqpt card = (CalientCardEqpt)children.get(cardEqptID);
			card.addIOPort(eqptID, ias, ios, ioc, oas, oos, ooc, al);
		}
		
		public void addPort(String cardEqptID, String eqptID, String as, String os, String oc, String al) {
			
			if (!children.containsKey(cardEqptID)) return;
			CalientCardEqpt card = (CalientCardEqpt)children.get(cardEqptID);
			card.addPort(eqptID, as, os, oc, al);
		}
	} // end of class CalientOSInfo.CalientShelfEqpt
	
	public class CalientCardEqpt implements Serializable {
		
		public String eqptID; // the id of the card (s.c[a|b])
		public String cardType;
		public byte administrativeState; // administrative state of the card
		public byte operationalState; // the operational state of the card
		public byte operationalCapacity; // Operational Capacity
		public byte redundancyState; // redundancy state
		public byte alarmStatus; // the alarm status
		public Hashtable children = null; // the list of subequipments (ports)
		public CalientDetailsEqpt details = null;
		public CalientEnvEqpt env = null;
		public CalientMemEqpt mem = null;
		
		public CalientCardEqpt(String eqptID, String cardType, String as, String os, String oc, String rs, String al) {
			
			this.eqptID = eqptID;
			this.cardType = cardType;
			if (as.equals("IS")) 
				administrativeState = as_is;
			else if (as.equals("UMA"))
				administrativeState = as_uma;
			else if (as.equals("OOS"))
				administrativeState = as_oos;
			else if (as.equals("OOS-NP"))
				administrativeState = as_oos_np;
			else 
				administrativeState = unk;
			if (os.equals("IS"))
				operationalState = os_is;
			else if (os.equals("OOS"))
				operationalState = os_oos;
			else if (os.equals("Ready"))
				operationalState = os_ready;
			else if (os.equals("Init"))
				operationalState = os_init;
			else if (os.equals("Degraded"))
				operationalState = os_degraded;
			else if (os.equals("NoHW"))
				operationalState = os_nohw;
			else 
				operationalState = unk;
			if (oc.equals("OK"))
				operationalCapacity = oc_ok;
			else if (oc.equals("Degraded"))
				operationalCapacity = oc_degraded;
			else if (oc.equals("Init"))
				operationalCapacity = oc_init;
			else if (oc.equals("Failed"))
				operationalCapacity = oc_failed;
			else if (oc.equals("NoHW"))
				operationalCapacity = oc_nohw;
			else
				operationalCapacity = unk;
			if (rs.equals("ACT"))
				redundancyState = rs_act;
			else if (rs.equals("STBYH"))
				redundancyState = rs_stbyh;
			else if (rs.equals("NRED"))
				redundancyState = rs_nred;
			else
				redundancyState = unk;
			if (al.equals("CR"))
				alarmStatus = as_cr;
			else if (al.equals("MJ"))
				alarmStatus = as_mj;
			else if (al.equals("MN"))
				alarmStatus = as_mn;
			else if (al.equals("CL"))
				alarmStatus = as_cl;
			else
				alarmStatus = unk;
			children = new Hashtable();
		}

		public void addPort(String eqptID, String as, String os, String oc, String al) {
			
			CalientPortEqpt card = new CalientPortEqpt(eqptID, as, os, oc, al);
			children.put(eqptID, card);
		}
		
		public void addIOPort(String eqptID, String ias, String ios, String ioc, String oas, String oos, String ooc, String al) {
			
			CalientPortIOEqpt card = new CalientPortIOEqpt(eqptID, ias, ios, ioc, oas, oos, ooc, al);
			children.put(eqptID, card);
		}
		
		public void addCPPort(String eqptID, String ip, String mask, String gateway, String as, String os, String oc, String al) { 
			
			CalientPortCPEqpt card = new CalientPortCPEqpt(eqptID, ip, mask, gateway, as, os, oc, al);
			children.put(eqptID, card);
		}
	} // end of class CalientOSInfo.CalientCardEqpt
	
	public class CalientCardSwitchMatrixEqpt extends CalientOSInfo.CalientCardEqpt {

		public String preferredMatrix; // the preferred matrix for this card
		
		public CalientCardSwitchMatrixEqpt(String eqptID, String cardType, String as, String os, String oc, String rs, String al, String preferredMatrix) {
			
			super(eqptID, cardType, as, os, oc, rs, al);
			this.preferredMatrix = preferredMatrix;
		}
	} // end of class CalientOSInfo.CalientCardSwitchMatrixEqpt
	
	public class CalientPortEqpt implements Serializable {
		
		public String eqptID; // the id of the port (s.c[a|b].p)
		public byte administrativeState; // administrative state of the card
		public byte operationalState; // the operational state of the card
		public byte operationalCapacity; // Operational Capacity
		public byte alarmStatus; // the alarm status
		public CalientDetailsEqpt details = null;
		public CalientEnvEqpt env = null;
		public CalientMemEqpt mem = null;
		
		public CalientPortEqpt(String eqptID, String as, String os, String oc, String al) {
			
			this.eqptID = eqptID;
			if (as.equals("IS")) 
				administrativeState = as_is;
			else if (as.equals("UMA"))
				administrativeState = as_uma;
			else if (as.equals("OOS"))
				administrativeState = as_oos;
			else if (as.equals("OOS-NP"))
				administrativeState = as_oos_np;
			else 
				administrativeState = unk;
			if (os.equals("IS"))
				operationalState = os_is;
			else if (os.equals("OOS"))
				operationalState = os_oos;
			else if (os.equals("Ready"))
				operationalState = os_ready;
			else if (os.equals("Init"))
				operationalState = os_init;
			else if (os.equals("Degraded"))
				operationalState = os_degraded;
			else if (os.equals("NoHW"))
				operationalState = os_nohw;
			else 
				operationalState = unk;
			if (oc.equals("OK"))
				operationalCapacity = oc_ok;
			else if (oc.equals("Degraded"))
				operationalCapacity = oc_degraded;
			else if (oc.equals("Init"))
				operationalCapacity = oc_init;
			else if (oc.equals("Failed"))
				operationalCapacity = oc_failed;
			else if (oc.equals("NoHW"))
				operationalCapacity = oc_nohw;
			else
				operationalCapacity = unk;
			if (al.equals("CR"))
				alarmStatus = as_cr;
			else if (al.equals("MJ"))
				alarmStatus = as_mj;
			else if (al.equals("MN"))
				alarmStatus = as_mn;
			else if (al.equals("CL"))
				alarmStatus = as_cl;
			else
				alarmStatus = unk;
		}
	} // end of class CalientOSInfo.CalientPortEqpt
	
	public class CalientPortCPEqpt extends CalientOSInfo.CalientPortEqpt {
		
		public String ip; // the ip -> only for NP
		public String mask; // the netmask
		public String gateway; // the gateway IP address

		public CalientPortCPEqpt(String eqptID, String ip, String mask, String gateway, String as, String os, String oc, String al) {
			
			super(eqptID, as, os, oc, al);
			this.ip = ip;
			this.mask = mask;
			this.gateway = gateway;
		}
	} // end of class CalientOSInfo.CalientPortCPEqpt
	
	public class CalientPortIOEqpt extends CalientOSInfo.CalientPortEqpt {
		
		public byte ias; // the input administrative state
		public byte ios; // the input operational state
		public byte ioc; // the input operational capacity
		public byte oas; // the output administrative state
		public byte oos; // the output operational state
		public byte ooc; // the output operarional capacity
		
		public CalientPortIOEqpt(String eqptID, String ias, String ios, String ioc, String oas, String oos, String ooc, String al) {
			
			super(eqptID, "N/A", "N/A", "N/A", al);
			if (ias.equals("IS")) 
				this.ias = as_is;
			else if (ias.equals("UMA"))
				this.ias = as_uma;
			else if (ias.equals("OOS"))
				this.ias = as_oos;
			else if (ias.equals("OOS-NP"))
				this.ias = as_oos_np;
			else 
				this.ias = unk;
			if (ios.equals("IS"))
				this.ios = os_is;
			else if (ios.equals("OOS"))
				this.ios = os_oos;
			else if (ios.equals("Ready"))
				this.ios = os_ready;
			else if (ios.equals("Init"))
				this.ios = os_init;
			else if (ios.equals("Degraded"))
				this.ios = os_degraded;
			else if (ios.equals("NoHW"))
				this.ios = os_nohw;
			else 
				this.ios = unk;
			if (ioc.equals("OK"))
				this.ioc = oc_ok;
			else if (ioc.equals("Degraded"))
				this.ioc = oc_degraded;
			else if (ioc.equals("Init"))
				this.ioc = oc_init;
			else if (ioc.equals("Failed"))
				this.ioc = oc_failed;
			else if (ioc.equals("NoHW"))
				this.ioc = oc_nohw;
			else
				this.ioc = unk;

			if (oas.equals("IS")) 
				this.oas = as_is;
			else if (oas.equals("UMA"))
				this.oas = as_uma;
			else if (oas.equals("OOS"))
				this.oas = as_oos;
			else if (oas.equals("OOS-NP"))
				this.oas = as_oos_np;
			else 
				this.oas = unk;
			if (oos.equals("IS"))
				this.oos = os_is;
			else if (oos.equals("OOS"))
				this.oos = os_oos;
			else if (oos.equals("Ready"))
				this.oos = os_ready;
			else if (oos.equals("Init"))
				this.oos = os_init;
			else if (oos.equals("Degraded"))
				this.oos = os_degraded;
			else if (oos.equals("NoHW"))
				this.oos = os_nohw;
			else 
				this.oos = unk;
			if (ooc.equals("OK"))
				this.ooc = oc_ok;
			else if (ooc.equals("Degraded"))
				this.ooc = oc_degraded;
			else if (ooc.equals("Init"))
				this.ooc = oc_init;
			else if (ooc.equals("Failed"))
				this.ooc = oc_failed;
			else if (ooc.equals("NoHW"))
				this.ooc = oc_nohw;
			else
				this.ooc = unk;
		}
	}
	
	public class CalientAlarm implements Serializable {
		
		public String aid; // the equiment component for which the alarm was generated
		public byte severity; // the severity of the alarm
		public String condType; // the condition type;
		public byte srveff; // effect of the alarm on the service
		public String ocrdat; // the date the alarm occured
		public String ocrtm; // the time the alarm occured;
		public String condDesc; // the desciption of the alarm;
		
		public CalientAlarm(String aid, String ntfcncde, String condType, String srveff, String ocrdat, String ocrtm, String condDesc) {
			
			this.aid = aid;
			if (ntfcncde.equals("CR"))
				severity = as_cr;
			else if (ntfcncde.equals("MJ"))
				severity = as_mj;
			else if (ntfcncde.equals("MN"))
				severity = as_mn;
			else if (ntfcncde.equals("CL"))
				severity = as_cl;
			else
				severity = unk;
			this.condType = condType;
			if (srveff.equals("NSA"))
				this.srveff = srveff_nsa;
			else if (srveff.equals("SA"))
				this.srveff = srveff_sa;
			else
				this.srveff = unk;
			this.ocrdat = ocrdat;
			this.ocrtm = ocrtm;
			this.condDesc = condDesc;
		}
	} // end of class CalientOSInfo.CalientAlarm
	
	public class CalientSwitchMatrix implements Serializable{
		
		public String switchingMatrixID; // the id of the switching matrix
		public String degradeThreshold; // the degraded optical signal loss threshold through the system
		public String criticalThreshold; // the critical optcal signal loss threshold through the system
		
		public CalientSwitchMatrix(String aid, String degradeThreshold, String criticalThreshold) {
			
			switchingMatrixID = aid;
			this.degradeThreshold = degradeThreshold;
			this.criticalThreshold = criticalThreshold;
		}
	} // end of class CalientOSInfo.CalientSwitchMatrix
	
	public class CalientDetailsEqpt implements Serializable {
		
		public String SwVersion; // software version
		public String DSPSwVersion; // software version of the Digital Signal Processor 
		public String PartNum; // part number of the eqpt 
		public String RevNum; // revision number of the eqpt
		public String SerialNum; // serial number of the eqpt
		public String BoardID; // type of the card
		public String FpgaRevNum; // Fpga number
		public String DiagCodeRevNum; // Diagnostic code number
		public String BootCodeRevNum; // boot code number
		public String Date; // the manufacturation date
		public String Time; // time when eqpt passed the quality assurance check
		public boolean InConnected; // presence of an input connection
		public boolean OutConnected; // presence of an output connection
		public boolean Hardware; // presence of a hardware 
		public boolean FullDuplex; // is full duplex ?
		public String InConnectionID; // connection id (sourcePortID-destPortID or sourcePortID>destinationPortID
		public String InConnectionGroupID; // name of the customer that is served by the connection
		public String InCircuitID; // connection id (sourcePortID-destPortID or sourcePortID>destinationPortID
		public boolean InActive; // presence of an input connection
		public String InOpticalPower; // current input optical power (dBm)
		public String InOpticalPowerCrossing; // number of times the input power level crossed the critical threshold
		public String InDegradedOpticalPowerCrossing; // number of times the input power level crossed the degraded threshold
		public String OutConnectionID; // connection id (sourcePortID-destPortID or sourcePortID>destinationPortID
		public String OutConnectionGroupID; // name of the customer that is served by the connection
		public String OutCircuitID; // connection id (sourcePortID-destPortID or sourcePortID>destinationPortID
		public boolean OutActive; // presence of an output connection
		public String OutOpticalPowerWorking; // current output optical power (dBm) 
		public String OutOpticalPowerCrossingCritWorking; // number of times the output power level crossed the critical threshold
		public String OutOpticalPowerCrossingDegWorking; // number of times the output power level crossed the degraded threshold

		public CalientDetailsEqpt(String SwVersion, String DSPSwVersion, String PartNum, String RevNum, String SerialNum, String BoardID, String FpgaRevNum,
				String DiagCodeRevNum, String BootCodeRevNum, String Date, String Time, String InConnected, String OutConnected, String Hardware, 
				String FullDuplex, String InConnectionID, String InConnectionGroupID, String InCircuitID, String InActive, String InOpticalPower, String InOpticalPowerCrossing,
				String InDegradedOpticalPowerCrossing, String OutConnectionID, String OutConnectionGroupID, String OutCircuitID, String OutActive, String OutOpticalPowerWorking, 
				String OutOpticalPowerCrossingCritWorking,  String OutOpticalPowerCrossingDegWorking) {
			
			this.SwVersion = SwVersion;
			this.DSPSwVersion = DSPSwVersion;
			this.PartNum = PartNum;
			this.RevNum = RevNum;
			this.SerialNum = SerialNum;
			this.BoardID = BoardID;
			this.FpgaRevNum = FpgaRevNum;
			this.DiagCodeRevNum = DiagCodeRevNum;
			this.BootCodeRevNum = BootCodeRevNum;
			this.Date = Date;
			this.Time = Time;
			if (InConnected.equals("True"))
				this.InConnected = true;
			else
				this.InConnected = false;
			if (OutConnected.equals("True"))
				this.OutConnected = true;
			else
				this.OutConnected = false;
			if (Hardware.equals("True"))
				this.Hardware = true;
			else
				this.Hardware = false;
			if (FullDuplex.equals("True"))
				this.FullDuplex = true;
			else
				this.FullDuplex = false;
			this.InConnectionID = InConnectionID;
			this.InConnectionGroupID = InConnectionGroupID;
			this.InCircuitID = InCircuitID;
			if (InActive.equals("True"))
				this.InActive = true;
			else
				this.InActive = false;
			this.InOpticalPower = InOpticalPower;
			this.InOpticalPowerCrossing = InOpticalPowerCrossing;
			this.InDegradedOpticalPowerCrossing = InDegradedOpticalPowerCrossing;
			this.OutConnectionID = OutConnectionID;
			this.OutConnectionGroupID = OutConnectionGroupID;
			this.OutCircuitID = OutCircuitID;
			if (OutActive.equals("True"))
				this.OutActive = true;
			else
				this.OutActive = false;
			this.OutOpticalPowerWorking = OutOpticalPowerWorking;
			this.OutOpticalPowerCrossingCritWorking = OutOpticalPowerCrossingCritWorking;
			this.OutOpticalPowerCrossingDegWorking = OutOpticalPowerCrossingDegWorking;
		}
	} // end of class CalientOSInfo.CalientDetailsEqpt
	
	public class CalientEnvEqpt implements Serializable {
		
		public String EqptType; // the type of the equipment
		public String Temperature; // the temperatute of the eqpt
		public String AdcBus; // number of the ADC bus error seen
		public String Memory; // memory utilization
		public String Cpu; // cpu utilization
		public String AtaDisk; // ATA disk utilization
		public String FlashDisk; // FlashDisk utilization
		public String Fan; // status of the fan
		public String Volt160; // 160 volt power supply status
		public String Volt5; // 5 volt power supply status
		public String Voltminus5; // -5 volt power supply status
		public String TempMPC; // temp of the MPC board
		public String TempLowVoltPS; // temp of the low voltage power supply
		public String TempOptSwPln2; // temp of the switch matrix plane 2
		public String Time; 
		public String TempOptSwPln1; // temp of the switch matrix plane 1
		public String DSP0Status; // status of the DSP0
		public String DSP1Status; // status of the DSP1
		
		public CalientEnvEqpt(String EqptType, String Temperature, String AdcBus, String Memory, String CPU, String AtaDisk, String FlashDisk, String Fan,
				String Volt160, String Volt5, String Voltminus5, String TempMPC, String TempLowVoltPS, String TempOptSwPln2, String Time,
				String TempOptSwPln1, String DSP0Status, String DSP1Status) {
			
			this.EqptType = EqptType;
			this.Temperature = Temperature;
			this.AdcBus = AdcBus;
			this.Memory = Memory;
			this.Cpu = CPU;
			this.AtaDisk = AtaDisk;
			this.FlashDisk = FlashDisk;
			this.Fan = Fan;
			this.Volt160 = Volt160;
			this.Volt5 = Volt5;
			this.Voltminus5 = Voltminus5;
			this.TempMPC = TempMPC;
			this.TempLowVoltPS = TempLowVoltPS;
			this.TempOptSwPln2 = TempOptSwPln2;
			this.TempOptSwPln1 = TempOptSwPln1;
			this.Time = Time;
			this.DSP0Status = DSP0Status;
			this.DSP1Status = DSP1Status;
		}
	} // end of class CalientOSInfo.CalientEnvEqpt
	
	public class CalientMemEqpt implements Serializable {
		
		public String FreeMemory; // the amount of free memory
		public String EqptType; // the type of the equiment
		
		public CalientMemEqpt(String FreeMemory, String EqptType) {
			
			this.FreeMemory = FreeMemory;
			this.EqptType = EqptType;
		}
	} // end of class CalientOSInfo.CalientMemEqpt
	
	public class CalientCrossConnect implements Serializable {
		
		public String srcPort; // the source port of the cross-connect
		public String dstPort; // the dst port of the cross-connect
		public String groupName; // the name of the group to which the connName belongs
		public String connName; // the name of the connection 
		public byte connType; // the type of the connection
		public byte status; // the status of the cross-conn
		public byte as; // administrative state 
		public byte os; // operational state
		public byte oc; // operational capacity
		public byte ps; // protective state
		public byte al; // alarm state
		public String matrixUsed; // the used switch matrix
		
		public CalientCrossConnect(String srcPort, String dstPort, String groupName, String connName, String connType, String status, String as, 
				String os, String oc, String ps, String al, String matrixUsed) {
			
			this.srcPort = srcPort;
			this.dstPort = dstPort;
			this.groupName = groupName;
			this.connName = connName;
			if (connType.equals("1way"))
				this.connType = conn_1way;
			else if (connType.equals("2way"))
				this.connType = conn_2way;
			else
				this.connType = unk;
			if (status.equals("INIT"))
				this.status = stat_INIT;
			else if (status.equals("DEGR"))
				this.status = stat_DEGR;
			else if (status.equals("FAIL"))
				this.status = stat_FAIL;
			else if (status.equals("OK"))
				this.status = stat_OK;
			else 
				this.status = unk;
			if (as.equals("IS")) 
				this.as = as_is;
			else if (as.equals("UMA"))
				this.as = as_uma;
			else if (as.equals("OOS"))
				this.as = as_oos;
			else if (as.equals("OOS-NP"))
				this.as = as_oos_np;
			else 
				this.as = unk;
			if (os.equals("IS"))
				this.os = os_is;
			else if (os.equals("OOS"))
				this.os = os_oos;
			else if (os.equals("Ready"))
				this.os = os_ready;
			else if (os.equals("Init"))
				this.os = os_init;
			else if (os.equals("Degraded"))
				this.os = os_degraded;
			else if (os.equals("NoHW"))
				this.os = os_nohw;
			else 
				this.os = unk;
			if (oc.equals("OK"))
				this.oc = oc_ok;
			else if (oc.equals("Degraded"))
				this.oc = oc_degraded;
			else if (oc.equals("Init"))
				this.oc = oc_init;
			else if (oc.equals("Failed"))
				this.oc = oc_failed;
			else if (oc.equals("NoHW"))
				this.oc = oc_nohw;
			else
				this.oc = unk;
			if (ps.equals("PRT"))
				this.ps = ps_PRT;
			else if (ps.equals("UPR"))
				this.ps = ps_UPR;
			else
				this.ps = unk;
			if (al.equals("CR"))
				this.al = as_cr;
			else if (al.equals("MJ"))
				this.al = as_mj;
			else if (al.equals("MN"))
				this.al = as_mn;
			else if (al.equals("CL"))
				this.al = as_cl;
			else
				this.al = unk;
			this.matrixUsed = matrixUsed;
		}
	}
	
	public class CalientOSPF implements Serializable {
		
		public String routerID; // the router ID with which the switch associates
		public String areaID;  // the OSPF area to which the router belongs
		
		public CalientOSPF(String routerID, String areaID) {
			
			this.routerID = routerID;
			this.areaID = areaID;
		}
	}
	
	public class CalientRSVP implements Serializable {
		
		public String mgsRetryInvl; // message retry interval attempts of the same RSVP message
		public String ntfRetryInvl; // notification retry interval 
		public String grInvl; // gracefull interval to refresh the state of the neighbor
		public String grcvInvl; // gracefull recovery interval
		
		public CalientRSVP(String msgRetryInvl, String ntfRertyInvl, String grInvl, String grcvInvl) {
			
			this.mgsRetryInvl = msgRetryInvl;
			this.ntfRetryInvl = ntfRertyInvl;
			this.grInvl = grInvl;
			this.grcvInvl = grcvInvl;
		}
	} // end of class CalientRSVP
	
	public class CalientCtrlCh implements Serializable {
		
		public String localIP; // local ip of the control channel
		public String remoteIP; // remote ip of the control channel
		public String localRid; // local router address
		public String remoteRid; // remote router address
		public String port; // the port associated with the control channel
		public String localIfIndex; // local interface index
		public String remoteIfIndex; // remote interface index
		public String adjanceny; // the name of the associated adjacency
		public String helloInvl; // time interval for hello packets (0 means LMP is disabled)
		public String helloInvlMin; // minimum hello interval
		public String helloInvlMax; // max hello interval
		public String deadInvl; // interval before declaring the link down
		public String deadInvlMin; // min dead interval
		public String deadInvlMax; // max dead interval
		public String helloInvlNegotiated; // the negotiated hello interval
		public String deadInvlNegotiated; // the negotiated dead interval
		public byte as; // administrative state
		public byte os; // operational state
		public byte oc; // operational capacity
		public byte al; // alarm state
		
		public CalientCtrlCh(String localIP, String remoteIP, String localRid, String remoteRid, String port, String localIfIndex, String remoteIfIndex, String adjancecy,
				String helloInvl, String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax, String helloInvlNegotiated,
				String deadInvlNegotiated, String as, String os, String oc, String al) {
			
			this.localIP = localIP;
			this.remoteIP = remoteIP;
			this.localRid = localRid;
			this.remoteRid = remoteRid;
			this.port = port;
			this.localIfIndex = localIfIndex;
			this.remoteIfIndex = remoteIfIndex;
			this.adjanceny = adjancecy;
			this.helloInvl = helloInvl;
			this.helloInvlMin = helloInvlMin;
			this.helloInvlMax = helloInvlMax;
			this.deadInvl = deadInvl;
			this.deadInvlMin = deadInvlMin;
			this.helloInvlNegotiated = helloInvlNegotiated;
			this.deadInvlNegotiated = deadInvlNegotiated;
			if (as.equals("IS")) 
				this.as = as_is;
			else if (as.equals("UMA"))
				this.as = as_uma;
			else if (as.equals("OOS"))
				this.as = as_oos;
			else if (as.equals("OOS-NP"))
				this.as = as_oos_np;
			else 
				this.as = unk;
			if (os.equals("IS"))
				this.os = os_is;
			else if (os.equals("OOS"))
				this.os = os_oos;
			else if (os.equals("Ready"))
				this.os = os_ready;
			else if (os.equals("Init"))
				this.os = os_init;
			else if (os.equals("Degraded"))
				this.os = os_degraded;
			else if (os.equals("NoHW"))
				this.os = os_nohw;
			else 
				this.os = unk;
			if (oc.equals("OK"))
				this.oc = oc_ok;
			else if (oc.equals("Degraded"))
				this.oc = oc_degraded;
			else if (oc.equals("Init"))
				this.oc = oc_init;
			else if (oc.equals("Failed"))
				this.oc = oc_failed;
			else if (oc.equals("NoHW"))
				this.oc = oc_nohw;
			else
				this.oc = unk;
			if (al.equals("CR"))
				this.al = as_cr;
			else if (al.equals("MJ"))
				this.al = as_mj;
			else if (al.equals("MN"))
				this.al = as_mn;
			else if (al.equals("CL"))
				this.al = as_cl;
			else
				this.al = unk;
		}
	} // end of class CalientCtrlCh
	
	public class CalientAdjacency implements Serializable {
		
		public String localRid;
		public String remoteRid;
		public Vector ctrlChannels; // the associated control channels (CalientCtrlCh objects)
		public String currentCtrlChannel; // the name of the current ctrl channel
		public String adjIndex; // the index number of the current neighbor adjancecy
		public String ospfArea; // the ospf area 
		public String metric; // the administrative cost (number of hops) associated with the adjancecy
		public String ospfAdy; // the flag of an OSPF adjancecy
		public byte adjType; // the adjancecy type
		public String rsvpRRFlag; // refresh reduction flag
		public String rsvpRGFlag; // gracefull restart flag
		public String ntfProc; // processing notification
		public byte as; // administrative cost
		public byte os; // operational state
		public byte oc; // operational capacity
		public byte ps; // protection state
		public byte al; // alarm status
		
		public CalientAdjacency(String localRid, String remoteRid, String currentCtrlChannel, String adjIndex, String ospfArea, String metric, String ospfAdy,
				String adjType, String rsvpRRFlag, String rsvpRGFlag, String ntfProc, String as, String os, String oc, String ps, String al) {
			
			this.localRid = localRid;
			this.remoteRid = remoteRid;
			this.currentCtrlChannel = currentCtrlChannel;
			this.adjIndex = adjIndex;
			this.ospfArea = ospfArea;
			this.metric = metric;
			this.ospfAdy = ospfAdy;
			if (adjType.equals("CALIENTGMPLSPEER"))
				this.adjType = adj_CALIENTGMPLSPEER;
			else if (adjType.equals("GMPLSOVERLAY"))
				this.adjType = adj_GMPLSOVERLAY;
			else if (adjType.equals("GMPLSPEER"))
				this.adjType = adj_GMPLSPEER;
			else if (adjType.equals("GMPLSWDM"))
				this.adjType = adj_GMPLSWDM;
			else
				this.adjType = unk;
			this.rsvpRRFlag = rsvpRRFlag;
			this.rsvpRGFlag = rsvpRGFlag;
			this.ntfProc = ntfProc;
			if (as.equals("IS")) 
				this.as = as_is;
			else if (as.equals("UMA"))
				this.as = as_uma;
			else if (as.equals("OOS"))
				this.as = as_oos;
			else if (as.equals("OOS-NP"))
				this.as = as_oos_np;
			else 
				this.as = unk;
			if (os.equals("IS"))
				this.os = os_is;
			else if (os.equals("OOS"))
				this.os = os_oos;
			else if (os.equals("Ready"))
				this.os = os_ready;
			else if (os.equals("Init"))
				this.os = os_init;
			else if (os.equals("Degraded"))
				this.os = os_degraded;
			else if (os.equals("NoHW"))
				this.os = os_nohw;
			else 
				this.os = unk;
			if (oc.equals("OK"))
				this.oc = oc_ok;
			else if (oc.equals("Degraded"))
				this.oc = oc_degraded;
			else if (oc.equals("Init"))
				this.oc = oc_init;
			else if (oc.equals("Failed"))
				this.oc = oc_failed;
			else if (oc.equals("NoHW"))
				this.oc = oc_nohw;
			else
				this.oc = unk;
			if (ps.equals("PRT"))
				this.ps = ps_PRT;
			else if (ps.equals("UPR"))
				this.ps = ps_UPR;
			else
				this.ps = unk;
			if (al.equals("CR"))
				this.al = as_cr;
			else if (al.equals("MJ"))
				this.al = as_mj;
			else if (al.equals("MN"))
				this.al = as_mn;
			else if (al.equals("CL"))
				this.al = as_cl;
			else
				this.al = unk;
			this.ctrlChannels = new Vector();
		}
		
		public void addCtrlChannel(CalientOSInfo.CalientCtrlCh ctrlChannel) {
			
			if (ctrlChannels == null) ctrlChannels = new Vector();
			ctrlChannels.add(ctrlChannel);
		}
	} // end of class CalientAdjancecy
	
	public class CalientLink implements Serializable {
		
		public String name; // the name of the link
		public byte linkType; // the type of the link
		public String localRid; // local router ip address
		public String remoteRid; // remote router ip address
		public String localIP; // the local ip address with which the te associates 
		public String remoteIP; // the remote ip address
		public String adjName; // the name of the adjacency associated with this link
		public String localIfIndex; // the local te if index (the local te link ip address) 
		public String remoteIf; // remote te if index (the remote te link ip address)
		public String wdmRemoteIf; // wdm remote te interface index
		public String fptDetect; // lmp fault detection
		public String metric; // administrative cost
		public String lmpVerify; // lmp verify interval
		public byte adjType; // the type of the associated adjancency
		public String admin; // name of the associated administrative group
		public String totalBandwidth[] ; // the total bandwidth
		public String availableBandwidth[] ; // the available bandwidth
		public String port; // the port associated with this link
		public String lspEncoding; // lsp encoding scheme for the port 
		public String bandwidth; // the connection bandwidth
		public String portMinPriority;  // minimum setup priority
		public String portRemPortLabel; // the connected port at the other node (signaling)
		public String portLolState; // loss of light state
		public String swCap; // switching capabilities
		public byte as; // administrative state
		public byte os; // operational state
		public byte oc; // operational capacity
		public byte al; // alarm status
		public String color; // port color
		
		public CalientLink(String name, String linkType, String localRid, String remoteRid, String localIP, String remoteIP, String adjName, String localIfIndex, 
				String remoteIf, String wdmRemoteIf, String fptDetect, String metric, String lmpVerify, String adjType, String admin, 
				String[]totalBandwidth, String[]availableBandwidth, String port, String lspEncoding, String bandwidth, String portMinPriority, String portRemPortLabel,
				String portLolState, String swCap, String as, String os, String oc, String al, String color) {
			
			this.name = name;
			if (linkType.equals("NUMBERED"))
				this.linkType = te_NUMBERED;
			else if (linkType.equals("UNNUMBERED"))
				this.linkType = te_UNNUMBERED;
			else
				this.linkType = unk;
			this.localRid = localRid;
			this.remoteRid = remoteRid;
			this.localIP = localIP;
			this.remoteIP = remoteIP;
			this.adjName = adjName;
			this.localIfIndex = localIfIndex;
			this.remoteIf = remoteIf;
			this.wdmRemoteIf = wdmRemoteIf;
			this.fptDetect = fptDetect;
			this.metric = metric;
			this.lmpVerify = lmpVerify;
			if (adjType.equals("CALIENTGMPLSPEER"))
				this.adjType = adj_CALIENTGMPLSPEER;
			else if (adjType.equals("GMPLSOVERLAY"))
				this.adjType = adj_GMPLSOVERLAY;
			else if (adjType.equals("GMPLSPEER"))
				this.adjType = adj_GMPLSPEER;
			else if (adjType.equals("GMPLSWDM"))
				this.adjType = adj_GMPLSWDM;
			else
				this.adjType = unk;
			this.admin = admin;
			this.totalBandwidth = totalBandwidth;
			this.availableBandwidth = availableBandwidth;
			this.port = port;
			this.lspEncoding = lspEncoding;
			this.bandwidth = bandwidth;
			this.portMinPriority = portMinPriority;
			this.portRemPortLabel = portRemPortLabel;
			this.portLolState = portLolState;
			this.swCap = swCap;
			if (as.equals("IS")) 
				this.as = as_is;
			else if (as.equals("UMA"))
				this.as = as_uma;
			else if (as.equals("OOS"))
				this.as = as_oos;
			else if (as.equals("OOS-NP"))
				this.as = as_oos_np;
			else 
				this.as = unk;
			if (os.equals("IS"))
				this.os = os_is;
			else if (os.equals("OOS"))
				this.os = os_oos;
			else if (os.equals("Ready"))
				this.os = os_ready;
			else if (os.equals("Init"))
				this.os = os_init;
			else if (os.equals("Degraded"))
				this.os = os_degraded;
			else if (os.equals("NoHW"))
				this.os = os_nohw;
			else 
				this.os = unk;
			if (oc.equals("OK"))
				this.oc = oc_ok;
			else if (oc.equals("Degraded"))
				this.oc = oc_degraded;
			else if (oc.equals("Init"))
				this.oc = oc_init;
			else if (oc.equals("Failed"))
				this.oc = oc_failed;
			else if (oc.equals("NoHW"))
				this.oc = oc_nohw;
			else
				this.oc = unk;
			if (al.equals("CR"))
				this.al = as_cr;
			else if (al.equals("MJ"))
				this.al = as_mj;
			else if (al.equals("MN"))
				this.al = as_mn;
			else if (al.equals("CL"))
				this.al = as_cl;
			else
				this.al = unk;
			this.color = color;
		}
		
		public String toString() {
			
			String ret = "";
			ret += "Name="+name+" ";
			ret += "LT="+linkType+" ";
			ret += "LRid="+localRid+" ";
			ret += "RRid="+remoteRid+" ";
			ret += "LIP="+localIP+" "; 
			ret += "RIP="+remoteIP+" "; 
			ret += "Adj="+adjName+" "; 
			ret += "LIf="+localIfIndex+" "; 
			ret += "RIf="+remoteIf+" "; 
			ret += "WRIf="+wdmRemoteIf+" "; 
			ret += "FPT="+fptDetect+" "; 
			ret += "M="+metric+" "; 
			ret += "LMP="+lmpVerify+" "; 
			ret += "ADJ="+adjType+" "; 
			ret += "Admin="+admin+" "; 
			ret += "Port="+port+" "; 
			ret += "LSPE="+lspEncoding+" ";  
			ret += "BWDT="+bandwidth+" "; 
			ret += "PortMinP="+portMinPriority+" ";  
			ret += "PortRLabel="+portRemPortLabel+" "; 
			ret += "portLol="+portLolState+" "; 
			ret += "swCap="+swCap+" "; 
			ret += "Color="+color; 
			return ret;
		}
		
	} // end of class CalientLink
	
	public class CalientConn implements Serializable {
		
		public String groupName; // the name of the group
		public byte connType; // the type of the connection
		public String srcIP; // the source IP address
		public String ingressPortIn; // source port
		public String egressPortOut; // destination port
		public String ingressPortOut; 
		public String egressPortIn;
		public String dstIP; // destination ip address of the connection
		public String dstPortIn; // destination input port
		public String dstPortOut; // destination output port
		public String nt; // network 
		public byte srvClass; // service class of connection
		public byte upnt; // uplink adjacency type
		public byte dnnt; // downlink adjancency type
		public String outTELink; // output te link
		public String adjID; // adjancecy
		public String extAdj; // external adjancency
		public String lspEncoding; // lsp encoding scheme
		public String lspPayload; // lsp payload type
		public String bandwidth; // connection bandwidth
		public String setPrio; // connection setup priority
		public String holdPrio; // connection holding priority
		public String maxHop; // maximum number of hops
		public String recroute; // the actual route is recorded or not
		public String proIP; // ip of the preferred route
		public String proIfIf; // interface id of the preferred route
		public String proHt; // type of hops used
		public byte as; // administrative state
		public byte os; // operational state
		public byte oc; // operational capacity
		public byte al; // alarm status
		public String color; // port color
		
		public CalientConn(String groupName, String connType, String srcIP, String ingressPortIn, String egressPortOut, String ingressPortOut, String egressPortIn,
				String dstIP, String dstPortIn, String dstPortOut, String nt, String srvClass, String upnt, String dnnt, String outTELink, String adjID, String extAdj,
				String lspEncoding, String lspPayload, String bandwidth, String setPrio, String holdPrio, String maxHop, String recroute, String proIP, 
				String proIfIf, String proHt, String as, String os, String oc, String al, String color) {
			
			this.groupName = groupName;
			if (connType.equals("1WAY"))
				this.connType = conn_1way;
			else if (connType.equals("2WAY"))
				this.connType = conn_2way;
			else 
				this.connType = unk;
			this.srcIP = srcIP;
			this.ingressPortIn = ingressPortIn;
			this.egressPortOut = egressPortOut;
			this.ingressPortOut = ingressPortOut;
			this.egressPortIn = egressPortIn;
			this.dstIP = dstIP;
			this.dstPortIn = dstPortIn;
			this.dstPortOut = dstPortOut;
			this.nt = nt;
			if (srvClass.equals("UPR"))
				this.srvClass = srv_UPR;
			else if (srvClass.equals("SM"))
				this.srvClass = srv_SM;
			else
				this.srvClass = unk;
			if (upnt.equals("CALIENTGMPLSPEER"))
				this.upnt =adj_CALIENTGMPLSPEER;
			else if (upnt.equals("GMPLSOVERLAY"))
				this.upnt = adj_GMPLSOVERLAY;
			else if (upnt.equals("GMPLSPEER"))
				this.upnt = adj_GMPLSPEER;
			else if (upnt.equals("GMPLSWDM"))
				this.upnt = adj_GMPLSWDM;
			else 
				this.upnt = unk;
			if (dnnt.equals("CALIENTGMPLSPEER"))
				this.dnnt =adj_CALIENTGMPLSPEER;
			else if (dnnt.equals("GMPLSOVERLAY"))
				this.dnnt = adj_GMPLSOVERLAY;
			else if (dnnt.equals("GMPLSPEER"))
				this.dnnt = adj_GMPLSPEER;
			else if (dnnt.equals("GMPLSWDM"))
				this.dnnt = adj_GMPLSWDM;
			else 
				this.dnnt = unk;
			this.outTELink = outTELink;
			this.adjID = adjID;
			this.extAdj = extAdj;
			this.lspEncoding = lspEncoding;
			this.lspPayload = lspPayload;
			this.bandwidth = bandwidth;
			this.setPrio = setPrio;
			this.holdPrio = holdPrio;
			this.maxHop = maxHop;
			this.recroute = recroute;
			this.proIP = proIP;
			this.proIfIf = proIfIf;
			this.proHt = proHt;
			if (as.equals("IS")) 
				this.as = as_is;
			else if (as.equals("UMA"))
				this.as = as_uma;
			else if (as.equals("OOS"))
				this.as = as_oos;
			else if (as.equals("OOS-NP"))
				this.as = as_oos_np;
			else 
				this.as = unk;
			if (os.equals("IS"))
				this.os = os_is;
			else if (os.equals("OOS"))
				this.os = os_oos;
			else if (os.equals("Ready"))
				this.os = os_ready;
			else if (os.equals("Init"))
				this.os = os_init;
			else if (os.equals("Degraded"))
				this.os = os_degraded;
			else if (os.equals("NoHW"))
				this.os = os_nohw;
			else 
				this.os = unk;
			if (oc.equals("OK"))
				this.oc = oc_ok;
			else if (oc.equals("Degraded"))
				this.oc = oc_degraded;
			else if (oc.equals("Init"))
				this.oc = oc_init;
			else if (oc.equals("Failed"))
				this.oc = oc_failed;
			else if (oc.equals("NoHW"))
				this.oc = oc_nohw;
			else
				this.oc = unk;
			if (al.equals("CR"))
				this.al = as_cr;
			else if (al.equals("MJ"))
				this.al = as_mj;
			else if (al.equals("MN"))
				this.al = as_mn;
			else if (al.equals("CL"))
				this.al = as_cl;
			else
				this.al = unk;
			this.color = color;
		}
		
	} // end of class CalientConn
	
	public String toString() {
		
		if (shelves == null || shelves.size() == 0) return "";
		String ret = "|\n";
		for (Enumeration en1 = shelves.elements(); en1.hasMoreElements(); ) {
			CalientShelfEqpt eqpt = (CalientShelfEqpt)en1.nextElement();
			ret += "|\n";
			ret += "|="+eqpt.eqptID+": ShelfKind="+eqpt.shelfKind+" AS="+eqpt.alarmStatus+" FreePorts="+eqpt.freePorts+" ConnectedPorts="+eqpt.connectedPorts+"\n";
			if (eqpt.children != null && eqpt.children.size() != 0)
				for (Enumeration en2 = eqpt.children.elements(); en2.hasMoreElements();  ) {
					CalientCardEqpt card = (CalientCardEqpt)en2.nextElement();
					ret += "|\n";
					ret += "|=="+card.eqptID+": CardType="+card.cardType+" AS="+card.administrativeState+" OS="+card.operationalState+" OC="+card.operationalCapacity+" RS="+card.redundancyState+" AL="+card.alarmStatus+"\n";
					if (card.children != null && card.children.size() != 0)
						for (Enumeration en3=card.children.elements(); en3.hasMoreElements(); ) {
							CalientPortEqpt port = (CalientPortEqpt)en3.nextElement();
							ret += "|\n";
							ret += "|==="+port.eqptID+": AS="+port.administrativeState+" OS="+port.operationalState+" OC="+port.operationalCapacity+" AL="+port.alarmStatus+"\n";
						}
				}
		}
//		if (alarms != null) {
//			ret += "\nAlarms:\n";
//			for (int i=0; i<alarms.size(); i++) {
//				CalientAlarm alarm = (CalientAlarm)alarms.elementAt(i);
//				ret += ""+alarm.aid+": Sev="+alarm.severity+" CondType="+alarm.condType+" SRVEFF="+alarm.srveff+" OCRDAT="+alarm.ocrdat+" OCRTM="+alarm.ocrtm+" Desc="+alarm.condDesc+"\n";
//			}
//		}
//		if (switchingMatrix != null) {
//			ret += "\nSwitching Matrix:\n";
//			for (Enumeration en = switchingMatrix.elements(); en.hasMoreElements(); ) {
//				CalientSwitchMatrix sm = (CalientSwitchMatrix)en.nextElement();
//				ret += ""+sm.switchingMatrixID+": DegradeThreshold="+sm.degradeThreshold+" CriticalThreshold="+sm.criticalThreshold+"\n";
//			}
//		}
		if (links != null) {
			for (Enumeration en = links.keys(); en.hasMoreElements(); ) {
				String aid = (String)en.nextElement();
				CalientLink link = (CalientLink)links.get(aid);
				System.out.println(aid+" - ("+link+")");
			}
		}
		return ret;
	}
	
} // end of class CalientOSInfo

