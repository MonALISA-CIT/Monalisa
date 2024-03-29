import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

public class monCalientPXC extends cmdExec implements MonitoringModule {

	/** Logger Name */
    private static final String COMPONENT = "lia.Monitor.modules.monCalientPXC";

    /** The Logger */
    private static final Logger logger = Logger.getLogger(COMPONENT);

    static public String ModuleName = "monIDS1";   

    static public String[] ResTypes = null;

    static public String OsName = "linux";
    
    protected OSTelnet osConn = null;
    
    protected Vector switchingMatrix = new Vector();
    
    protected static int stepsInterogateEqpt = 3;
	protected int currentStep = 0;
    
	protected boolean readDetailsEqpt = true; // should we read the details for the eqpts? default yes
	protected boolean readEnvEqpt = true; // should we read the env infos for the eqpts? default yes
	protected boolean readMemEqpt = true; // should we read the mem infos for the eqpts? default yes
	
    public MonModuleInfo init(MNode Node, String arg) {
    	
        this.Node = Node;
		info = new MonModuleInfo ();
        init_args(arg);
		logger.log(Level.INFO, "monCalientPXC: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName() + " nodeName=" + Node.getName() + " arg = " + arg);
       return info;
    } // end of constructor
    
    protected void init_args(String list) {
    	
        if (list == null || list.length() == 0) return;
        String params[] = list.split("(\\s)*,(\\s)*");
        if (params == null || params.length == 0) return;
        for (int i=0; i<params.length; i++) {
        	String cmd[] = params[i].split("=");
        	if (cmd == null || cmd.length < 2) continue;
        	cmd[0] = cmd[0].trim();
        	cmd[1] = cmd[1].trim();
        	if (cmd[0].compareToIgnoreCase("DetEqpt") == 0) {
        		if (cmd[1].compareToIgnoreCase("false") == 0)
        			readDetailsEqpt = false;
        	} else if (cmd[0].compareToIgnoreCase("EnvEqpt") == 0) {
        		if (cmd[1].compareToIgnoreCase("false") == 0)
        			readEnvEqpt = false;
        	} else if (cmd[0].compareToIgnoreCase("MemEqpt") == 0) {
        		if (cmd[1].compareToIgnoreCase("false") == 0)
        			readMemEqpt = false;
        	}
        }
    }
    
	 public boolean isRepetitive() {
		 return true;
	 }

	
	public Object doProcess() throws Exception {

		Vector ret = new Vector();
		
		if (currentStep%stepsInterogateEqpt == 0) {
			getCalientEqptConfig(ret);
		}
		
		getCalientAlarms(ret);
		getSwitchMatrix(ret);
		getCrossConnects(ret);
		getNetworkElement(ret);
		
		// network configuration
		getOSPF(ret);
		getRSVP(ret);
		getCtrlChs(ret);
		getAdjancencies(ret);
		getLinks(ret);
		getConn(ret);
		getCfgConn(ret);
		
		currentStep++;
		if (currentStep == 3000) currentStep = 0;
		
		return ret;
	} // end doProcess

	protected void addeResult(Vector v, String nodeName, String paramName, Object value) {
		
		if (nodeName == null || paramName == null || value== null)
			return;
		
		if (value instanceof String)
			if (((String)value).equals("N/A"))
				return;
		
		eResult ret = new eResult(Node.getFarmName(), Node.getClusterName(), nodeName, ModuleName, new String[0]);
		ret.time = NTPDate.currentTimeMillis();
		ret.addSet(paramName, value);
		v.add(ret);
	}
	
	protected void addResult(Vector v, String nodeName, String paramName, double value) {

		if (nodeName == null || paramName == null)
			return;
		
		Result ret = new Result(Node.getFarmName(), Node.getClusterName(), nodeName, ModuleName, new String[0]);
		ret.time = NTPDate.currentTimeMillis();
		ret.addSet(paramName, value);
		v.add(ret);
	}
	
	protected void addResult(Vector v, String nodeName, String paramName, String value) {
		
		if (nodeName == null || paramName == null)
			return;
		
		double d = -1.0;
		try {
			d = Double.parseDouble(value);
		} catch (Exception e) { d = -1.0; }
		if (d < 0)
			return;
		
		Result ret = new Result(Node.getFarmName(), Node.getClusterName(), nodeName, ModuleName, new String[0]);
		ret.time = NTPDate.currentTimeMillis();
		ret.addSet(paramName, d);
		v.add(ret);
	}
	
	public String[] ResTypes() {
        return info.ResTypes;
	} 

	public String getOsName() {
        return OsName;
	}

	public MonModuleInfo getInfo() {
        return info;
	}

	public void getCalientEqptConfig(Vector ret) {
		
		switchingMatrix.clear();
		
		// first get shelves eqpt....
		Vector v = executeCommand("rtrv-eqpt;");
		if (v!=null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String eqptID = line.substring(0, line.indexOf(":"));
				if (eqptID == null || eqptID.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				if (line == null || line.length() == 0) continue;
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String shelfKind = "N/A";
				String status = "N/A";
				String freePorts = "N/A";
				String connectedPorts = "N/A";
				for (int k=0; k<params.length; k++) {
					String parts[] = params[k].split("=");
					if (parts == null || parts.length != 2) continue;
					if (parts[0].equals("ShelfKind"))
						shelfKind = parts[1];
					else if (parts[0].equals("Status"))
						status = parts[1];
					else if (parts[0].equals("FreePorts"))
						freePorts = parts[1];
					else if (parts[0].equals("ConnectedPorts"))
						connectedPorts = parts[1];
				}
				addeResult(ret, eqptID, "ShelfKind", shelfKind);
				addeResult(ret, eqptID, "Status", status);
				addResult(ret, eqptID, "FreePorts", freePorts);
				addResult(ret, eqptID, "ConnectedPorts", connectedPorts);
				// now get the infos for the cards of this shelf
				Vector v1 = executeCommand("rtrv-eqpt::"+eqptID+";");
				if (v1 != null && v1.size() != 0) {
					for (int i1=0; i1<v1.size(); i1++) {
						line = (String)v1.elementAt(i1);
						if (line.indexOf(":")<0) continue;
						eqptID = line.substring(0, line.indexOf(":"));
						if (eqptID == null || eqptID.length() == 0) continue;
						line = line.substring(line.indexOf(":")+1);
						if (line == null || line.length() == 0) continue;
						params = line.split(",");
						if (params == null || params.length == 0) continue;
						String cardType = "N/A";
						String as = "N/A";
						String os = "N/A"; 
						String oc = "N/A";
						String rs = "N/A";
						String al= "N/A";
						String preferredMatrix = "N/A";
						for (int k=0; k<params.length; k++) {
							String parts[] = params[k].split("=");
							if (parts == null || parts.length != 2) continue;
							if (parts[0].equals("CardType"))
								cardType = parts[1];
							else if (parts[0].equals("AS"))
								as = parts[1];
							else if (parts[0].equals("OS"))
								os = parts[1];
							else if (parts[0].equals("OC"))
								oc = parts[1];
							else if (parts[0].equals("RS"))
								rs = parts[1];
							else if (parts[0].equals("AL"))
								al = parts[1];
							else if (parts[0].equals("PREFERREDMATRIX"))
								preferredMatrix = parts[1];
						}
						if (preferredMatrix.equals("N/A")) {
							addeResult(ret, eqptID, "CardType", cardType);
							addeResult(ret, eqptID, "AdminstrativeState", as);
							addeResult(ret, eqptID, "OperationalState", os);
							addeResult(ret, eqptID, "OperationalCapacity", oc);
							addeResult(ret, eqptID, "RedundancyState", rs);
							addeResult(ret, eqptID, "AlarmStatus", al);
							if (!oc.equals("NoHW") && !oc.equals("Failed") && !oc.equals("N/A")) {
								if (readDetailsEqpt)
									getCalientDetailsEqpt(ret, eqptID);
								if (readEnvEqpt)
									getCalientEnvEqpt(ret, eqptID);
								if (readMemEqpt)
									getCalientMemEqpt(ret, eqptID);
							}
						} else {
							addeResult(ret, eqptID, "CardType", cardType);
							addeResult(ret, eqptID, "AdminstrativeState", as);
							addeResult(ret, eqptID, "OperationalState", os);
							addeResult(ret, eqptID, "OperationalCapacity", oc);
							addeResult(ret, eqptID, "RedundancyState", rs);
							addeResult(ret, eqptID, "AlarmStatus", al);
							addeResult(ret, eqptID, "PreferredMatrix", preferredMatrix);
							switchingMatrix.add(eqptID);
							if (!oc.equals("NoHW") && !oc.equals("Failed") && !oc.equals("N/A")) {
								if (readDetailsEqpt)
									getCalientDetailsEqpt(ret, eqptID);
								if (readEnvEqpt)
									getCalientEnvEqpt(ret, eqptID);
								if (readMemEqpt)
									getCalientMemEqpt(ret, eqptID);
							}
						}
						Vector v2 = executeCommand("rtrv-eqpt::"+eqptID+";");
						if (v2 != null && v2.size() != 0) {
							for (int i2=0; i2<v2.size(); i2++) {
								line = (String)v2.elementAt(i2);
								if (line.indexOf(":")<0) continue;
								eqptID = line.substring(0, line.indexOf(":"));
								if (eqptID == null || eqptID.length() == 0) continue;
								line = line.substring(line.indexOf(":")+1);
								if (line == null || line.length() == 0) continue;
								params = line.split(",");
								if (params == null || params.length == 0) continue;
								String ip = "N/A";
								String mask = "N/A";
								String gw = "N/A";
								as = "N/A";
								os = "N/A"; 
								oc = "N/A";
								al= "N/A";
								boolean ioPort = false;
								if (line.indexOf("AS") != line.lastIndexOf("AS")) ioPort = true;
								if (!ioPort) {
									for (int k=0; k<params.length; k++) {
										String parts[] = params[k].split("=");
										if (parts == null || parts.length != 2) continue;
										if (parts[0].equals("IP"))
											ip = parts[1];
										else if (parts[0].equals("MASK"))
											mask = parts[1];
										else if (parts[0].equals("GATEWAY"))
											gw = parts[1];
										else if (parts[0].equals("AS"))
											as = parts[1];
										else if (parts[0].equals("OS"))
											os = parts[1];
										else if (parts[0].equals("OC"))
											oc = parts[1];
										else if (parts[0].equals("AL"))
											al = parts[1];
									}
									if (ip.equals("N/A")) {
										addeResult(ret, eqptID, "AdminstrativeState", as);
										addeResult(ret, eqptID, "OperationalState", os);
										addeResult(ret, eqptID, "OperationalCapacity", oc);
										addeResult(ret, eqptID, "RedundancyState", rs);
										addeResult(ret, eqptID, "AlarmStatus", al);
									} else {
										addeResult(ret, eqptID, "IP", ip);
										addeResult(ret, eqptID, "Mask", mask);
										addeResult(ret, eqptID, "Gateway", gw);
										addeResult(ret, eqptID, "AdminstrativeState", as);
										addeResult(ret, eqptID, "OperationalState", os);
										addeResult(ret, eqptID, "OperationalCapacity", oc);
										addeResult(ret, eqptID, "RedundancyState", rs);
										addeResult(ret, eqptID, "AlarmStatus", al);
									}
									if (!oc.equals("NoHW") && !oc.equals("Failed") && !oc.equals("N/A")) {
										if (readDetailsEqpt)
											getCalientDetailsEqpt(ret, eqptID);
										if (readEnvEqpt)
											getCalientEnvEqpt(ret, eqptID);
										if (readMemEqpt)
											getCalientMemEqpt(ret, eqptID);
									}
								} else {
									String ias = "N/A";
									String ios = "N/A";
									String ioc = "N/A";
									String oas = "N/A";
									String oos = "N/A";
									String ooc = "N/A";
									al= "N/A";
									for (int k=0; k<params.length; k++) {
										String parts[] = params[k].split("=");
										if (parts == null || parts.length != 2) continue;
										if (parts[0].equals("AS"))
											if (ias.equals("N/A")) 
												ias = parts[1];
											else
												oas = parts[1];
										else if (parts[0].equals("OS"))
											if (ios.equals("N/A"))
												ios = parts[1];
											else
												oos = parts[1];
										else if (parts[0].equals("OC"))
											if (ioc.equals("N/A"))
												ioc = parts[1];
											else
												ioc = parts[1];
										else if (parts[0].equals("AL"))
											al = parts[1];
									}
									addeResult(ret, eqptID, "InputAdminstrativeState", ias);
									addeResult(ret, eqptID, "InputOperationalState", ios);
									addeResult(ret, eqptID, "InputOperationalCapacity", ioc);
									addeResult(ret, eqptID, "OutputAdminstrativeState", oas);
									addeResult(ret, eqptID, "OutputOperationalState", oos);
									addeResult(ret, eqptID, "OutputOperationalCapacity", ooc);
									addeResult(ret, eqptID, "AlarmStatus", al);
									if (!ioc.equals("NoHW") && !ioc.equals("Failed") && !ioc.equals("N/A") && !ooc.equals("NoHW") && !ooc.equals("Failed") && !ooc.equals("N/A")) {
										if (readDetailsEqpt)
											getCalientDetailsEqpt(ret, eqptID);
										if (readEnvEqpt)
											getCalientEnvEqpt(ret, eqptID);
										if (readMemEqpt)
											getCalientMemEqpt(ret, eqptID);
									}
								}
							}
						}
					}
				}
			}
		}
	} // end getCalientEqptConfig
	
	public void getCalientDetailsEqpt(Vector ret, String eqptID) {
		
		Vector v = executeCommand("rtrv-det-eqpt::"+eqptID+";");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String SwVersion = "N/A";
				String DSPSwVersion = "N/A";
				String PartNum = "N/A";
				String RevNum = "N/A";
				String SerialNum = "N/A";
				String BoardID = "N/A";
				String FpgaRevNum = "N/A";
				String DiagCodeRevNum = "N/A";
				String BootCodeRevNum = "N/A";
				String Date = "N/A";
				String Time = "N/A";
				String InConnected = "N/A";
				String OutConnected = "N/A";
				String Hardware = "N/A";
				String FullDuplex = "N/A";
				String InConnectionID = "N/A";
				String InConnectionGroupID = "N/A";
				String InCircuitID = "N/A";
				String InActive = "N/A";
				String InOpticalPower = "N/A";
				String InOpticalPowerCrossing = "N/A";
				String InDegradedOpticalPowerCrossing = "N/A";
				String OutConnectionID = "N/A";
				String OutConnectionGroupID = "N/A";
				String OutCircuitID = "N/A";
				String OutActive = "N/A";
				String OutOpticalPowerWorking = "N/A";
				String OutOpticalPowerCrossingCritWorking = "N/A";
				String OutOpticalPowerCrossingDegWorking = "N/A";
				for (int k=0; k<params.length; k++) {
					String cmd[] = params[k].split("=");
					if (cmd == null || cmd.length < 2) continue;
					if (cmd[0].equals("SWVERSION"))
						SwVersion = cmd[1];
					else if (cmd[0].equals("DSPSWVERIONS"))
						DSPSwVersion = cmd[1];
					else if (cmd[0].equals("PARTNUM"))
						PartNum = cmd[1];
					else if (cmd[0].equals("REVNUM"))
						RevNum = cmd[1];
					else if (cmd[0].equals("SERIALNUM"))
						SerialNum = cmd[1];
					else if (cmd[0].equals("BOARDID"))
						BoardID = cmd[1];
					else if (cmd[0].equals("FPGAREVNUM"))
						FpgaRevNum = cmd[1];
					else if (cmd[0].equals("DIAGCODEREVNUM"))
						DiagCodeRevNum = cmd[1];
					else if (cmd[0].equals("BOOTCODEREVNUM"))
						BootCodeRevNum = cmd[1];
					else if (cmd[0].equals("DATE"))
						Date = cmd[1];
					else if (cmd[0].equals("TIME"))
						Time = cmd[1];
					else if (cmd[0].equals("INCONNECTED"))
						InConnected = cmd[1];
					else if (cmd[0].equals("OUTCONNECTED"))
						OutConnected = cmd[1];
					else if (cmd[0].equals("HARDWARE"))
						Hardware = cmd[1];
					else if (cmd[0].equals("FULLDUPLEX"))
						FullDuplex = cmd[1];
					else if (cmd[0].equals("INCONNECTIONID"))
						InConnectionID = cmd[1];
					else if (cmd[0].equals("INNCONNECTIONGROUPID"))
						InConnectionGroupID = cmd[1];
					else if (cmd[0].equals("INCIRCUITID"))
						InCircuitID = cmd[1];
					else if (cmd[0].equals("INACTIVE"))
						InActive = cmd[1];
					else if (cmd[0].equals("INOPTICALPOWER"))
						InOpticalPower = cmd[1];
					else if (cmd[0].equals("INOPTICALPOWERCROSSING"))
						InOpticalPowerCrossing = cmd[1];
					else if (cmd[0].equals("INDEGRADEDOPTICALPOWERCROSSING"))
						InDegradedOpticalPowerCrossing = cmd[1];
					else if (cmd[0].equals("OUTCONNECTIONID"))
						OutConnectionID = cmd[1];
					else if (cmd[0].equals("OUTCONNECTIONGROUPID"))
						OutConnectionGroupID = cmd[1];
					else if (cmd[0].equals("OUTCIRCUITID"))
						OutCircuitID = cmd[1];
					else if (cmd[0].equals("OUTACTIVE"))
						OutActive = cmd[1];
					else if (cmd[0].equals("OUTOPTICALPOWERWORKING"))
						OutOpticalPowerWorking = cmd[1];
					else if (cmd[0].equals("OUTOPTICALCROSSINGCRITWORKING"))
						OutOpticalPowerCrossingCritWorking = cmd[1];
					else if (cmd[0].equals("OUTOPTICALPOWERCROSSINGDEGWORKING"))
						OutOpticalPowerCrossingDegWorking = cmd[1];
				}
				addeResult(ret, eqptID, "SoftwareVersion", SwVersion);
				addeResult(ret, eqptID, "DSP Software Version", DSPSwVersion);
				addeResult(ret, eqptID, "PartNo", PartNum);
				addeResult(ret, eqptID, "RevisionNo", RevNum);
				addeResult(ret, eqptID, "SerialNo", SerialNum);
				addeResult(ret, eqptID, "BoardID", BoardID);
				addeResult(ret, eqptID, "FPGARevisionNo", FpgaRevNum);
				addeResult(ret, eqptID, "DiagnosticCodeRevNo", DiagCodeRevNum);
				addeResult(ret, eqptID, "BootCodeRevNo", BootCodeRevNum);
				addeResult(ret, eqptID, "ManufacturationDate", Date);
				addeResult(ret, eqptID, "LastQualityCheck", Time);
				addeResult(ret, eqptID, "InConnected", InConnected);
				addeResult(ret, eqptID, "OutConnected", OutConnected);
				addeResult(ret, eqptID, "HardwarePresence", Hardware);
				addeResult(ret, eqptID, "FullDuplex", FullDuplex);
				addeResult(ret, eqptID, "InConnectionID", InConnectionID);
				addeResult(ret, eqptID, "InConnectionGroupID", InConnectionGroupID);
				addeResult(ret, eqptID, "InCircuitID", InCircuitID);
				addeResult(ret, eqptID, "InActive", InActive);
				addResult(ret, eqptID, "InOpticalPower", InOpticalPower);
				addResult(ret, eqptID, "InOPticalPowerCrossing", InOpticalPowerCrossing);
				addResult(ret, eqptID, "InDegradedOpticalPowerCrossing", InDegradedOpticalPowerCrossing);
				addeResult(ret, eqptID, "OutConnectionID", OutConnectionID);
				addeResult(ret, eqptID, "OutConnectionGroupID", OutConnectionGroupID);
				addeResult(ret, eqptID, "OutCircuitID", OutCircuitID);
				addeResult(ret, eqptID, "OutActive", OutActive);
				addResult(ret, eqptID, "OutOpticalPowerWorking", OutOpticalPowerWorking);
				addResult(ret, eqptID, "OutOpticalPowerCrossingCritWorking", OutOpticalPowerCrossingCritWorking);
				addResult(ret, eqptID, "OutOpticalPowerCrossingDegWorking", OutOpticalPowerCrossingDegWorking);
			}
		}
	} // end getCalientDetailsEqpt
	
	public void getCalientEnvEqpt(Vector ret, String eqptID) {
		
		Vector v = executeCommand("rtrv-env-eqpt::"+eqptID+";");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String EqptType = "N/A";
				String Temperature = "N/A";
				String AdcBus = "N/A";
				String Memory = "N/A";
				String CPU = "N/A";
				String AtaDisk = "N/A";
				String FlashDisk = "N/A";
				String Fan = "N/A";
				String Volt160 = "N/A";
				String Volt5 = "N/A";
				String Voltminus5 = "N/A";
				String TempMPC = "N/A";
				String TempLowVoltPS = "N/A";
				String TempOptSwPln2 = "N/A";
				String Time = "N/A";
				String TempOptSwPln1 = "N/A";
				String DSP0Status = "N/A";
				String DSP1Status = "N/A";
				for (int k=0; k<params.length; k++) {
					String cmd[] = params[k].split("=");
					if (cmd == null || cmd.length < 2) continue;
					if (cmd[0].equals("EQUIPTYPE") || cmd[0].equals("EQPTTYPE"))
						EqptType = cmd[1];
					else if (cmd[0].equals("TEMPERATURE"))
						Temperature = cmd[1];
					else if (cmd[0].equals("ADCBUS"))
						AdcBus = cmd[1];
					else if (cmd[0].equals("MEMORY"))
						Memory = cmd[1];
					else if (cmd[0].equals("CPU"))
						CPU = cmd[1];
					else if (cmd[0].equals("ATADISK"))
						AtaDisk = cmd[1];
					else if (cmd[0].equals("FLASHDISK"))
						FlashDisk = cmd[1];
					else if (cmd[0].equals("FAN"))
						Fan = cmd[1];
					else if (cmd[0].equals("VOLT160"))
						Volt160 = cmd[1];
					else if (cmd[0].equals("VOLT5"))
						Volt5 = cmd[1];
					else if (cmd[0].equals("VOLTMINUS5"))
						Voltminus5 = cmd[1];
					else if (cmd[0].equals("TEMPMPC") || cmd[0].equals("TEMPMCP")) // it'a buggy in documentation 
						TempMPC = cmd[1];
					else if (cmd[0].equals("TEMPLOWVOLTPS"))
						TempLowVoltPS = cmd[1];
					else if (cmd[0].equals("TEMPOPTSSWPLN2"))
						TempOptSwPln2 = cmd[1];
					else if (cmd[0].equals("TEMPOPTSSWPLN1"))
						TempOptSwPln1 = cmd[1];
					else if (cmd[0].equals("TIME"))
						Time = cmd[1];
					else if (cmd[0].equals("DSP0STATUS"))
						DSP0Status = cmd[1];
					else if (cmd[0].equals("DSP1STATUS"))
						DSP1Status = cmd[1];
				}
				addeResult(ret, eqptID, "EqptType", EqptType);
				addResult(ret, eqptID, "Temperature", Temperature);
				addResult(ret, eqptID, "AdcBusErrors", AdcBus);
				addResult(ret, eqptID, "MemoryUsage", Memory);
				addResult(ret, eqptID, "CpuUsage", CPU);
				addResult(ret, eqptID, "AtaDiskUsage", AtaDisk);
				addResult(ret, eqptID, "FlashDiskUsage", FlashDisk);
				addeResult(ret, eqptID, "FanStatus", Fan);
				addeResult(ret, eqptID, "Volt160Status", Volt160);
				addeResult(ret, eqptID, "Volt5Status", Volt5);
				addeResult(ret, eqptID, "VoltMinus5Status", Voltminus5);
				addResult(ret, eqptID, "MPCTemp", TempMPC);
				addResult(ret, eqptID, "LowVoltPSTemp", TempLowVoltPS);
				addResult(ret, eqptID, "OptSwPln2Temp", TempOptSwPln2);
				addResult(ret, eqptID, "OptSwPln1Temp", TempOptSwPln1);
				addeResult(ret, eqptID, "DSP0Status", DSP0Status);
				addeResult(ret, eqptID, "DSP1Status", DSP1Status);
			}
		}
	} // end of getCalientEnvEqpt
	
	public void getCalientMemEqpt(Vector ret, String eqptID) {
		
		Vector v = executeCommand("rtrv-mem-eqpt::"+eqptID+";");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String EqptType = "N/A";
				String FreeMemory = "N/A";
				for (int k=0; k<params.length; k++) {
					String cmd[] = params[k].split("=");
					if (cmd == null || cmd.length < 2) continue;
					if (cmd[0].equals("EQUIPTYPE") || cmd[0].equals("EQPTTYPE"))
						EqptType = cmd[1];
					else if (cmd[0].equals("FREEMEMORY"))
						FreeMemory = cmd[1];
				}
				addResult(ret, eqptID, "FreeMemory", FreeMemory);
			}
		}
	} // end of getCalientMemEqpt

	public void getCalientAlarms(Vector ret) {
		
		Vector v = executeCommand("rtrv-alm-all;");
		Hashtable alarms = new Hashtable();
		if (v  != null && v.size() != 0)
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String paramLine = line.substring(0, line.indexOf(":"));
				if (paramLine == null || paramLine.length() == 0) continue;
				String condDesc = line.substring(line.indexOf(":")+1);
				if (!condDesc.startsWith("\\\"") || !condDesc.endsWith("\\\"") || !(condDesc.length() >= 4)) condDesc = "";
				else
					condDesc = condDesc.substring(2, condDesc.length()-2);
				String params[] = paramLine.split(",");
				String ntfcncde = "N/A";
				String condType = "N/A";
				String srveff = "N/A";
				String ocrdat = "N/A";
				String ocrtm = "N/A";
				if (params != null && params.length != 0) {
					ntfcncde = params[0];
					if (params.length > 1)
						condType = params[1];
					if (params.length > 2)
						srveff = params[2];
					if (params.length > 3)
						ocrdat = params[3];
					if (params.length > 4)
						ocrtm = params[4];
				}
				String alarm = "";
				if (alarms.containsKey(aid)) 
					alarm = (String)alarms.get(aid);
				alarm += ntfcncde+":"+condType+","+ocrdat+","+ocrtm+":"+condDesc+"\n";
				alarms.put(aid, alarm);
			}
		for (Enumeration en = alarms.keys(); en.hasMoreElements(); ) {
			String key = (String)en.nextElement();
			String alarm = (String)alarms.get(key);
			addeResult(ret, key, "Alarm", alarm);
		}
	} // end getCalientAlarms
	
	public void getSwitchMatrix(Vector ret) {
		
		if (switchingMatrix.size() == 0) return;
		
		for (int i=0; i<switchingMatrix.size(); i++) {
			String switchMatrix = (String)switchingMatrix.elementAt(i);
			Vector v = executeCommand("rtrv-cfg-swc::"+switchMatrix+";");
			if (v != null && v.size() != 0)
				for (int i1=0; i1<v.size(); i1++) {
					String line = (String)v.elementAt(i1);
					if (line.indexOf(":")<0) continue;
					String aid = line.substring(0, line.indexOf(":"));
					if (aid == null || aid.length() == 0) continue;
					line = line.substring(line.indexOf(":")+1);
					String[] params = line.split(",");
					if (params == null || params.length == 0) continue;
					String degradeThreshold = "N/A";
					String criticalThreshold = "N/A";
					for (int k=0; k<params.length; k++) {
						String[] pp = params[k].split("=");
						if (pp.length != 2) continue;
						if (pp[0].equals("DEGRADETHRESHOLD"))
							degradeThreshold = pp[1];
						else if (pp[0].equals("CRITICALTHRESHOLD"))
							criticalThreshold = pp[1];
					}
					addResult(ret, aid, "DegradeOpticalSignalLossThreshold", degradeThreshold);
					addResult(ret, aid, "CriticalOpticalSignalLossThreshold", criticalThreshold);
				}
		}
	} // end getSwitchMatrix
	
	public void getCrossConnects(Vector ret) {
		
		Vector v = executeCommand("rtrv-crs;");
		if (v!=null && v.size() != 0)
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid =  line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String[] params = line.split(",");
				if (params == null || params.length == 0) continue;
				String srcPort = "N/A";
				String dstPort = "N/A";
				String groupName = "N/A";
				String connName = "N/A"; 
				String connType = "N/A";
				String status = "N/A";
				String as = "N/A"; 
				String os = "N/A";
				String oc = "N/A";
				String ps = "N/A";
				String al = "N/A";
				String matrixUsed = "N/A";
				for (int k=0; k<params.length; k++) {
					String[] pp = params[k].split("=");
					if (pp.length != 2) continue;
					if (pp[0].equals("SRCPORT"))
						srcPort = pp[1];
					else if (pp[0].equals("DSTPORT"))
						dstPort = pp[1];
					else if (pp[0].equals("GRPNAME") || pp[0].equals("GROUPNAME"))
						groupName = pp[1];
					else if (pp[0].equals("CONNNAME"))
						connName = pp[1];
					else if (pp[0].equals("CONNTYPE"))
						connType = pp[1];
					else if (pp[0].equals("STATUS"))
						status = pp[1];
					else if (pp[0].equals("AS"))
						as = pp[1];
					else if (pp[0].equals("OS"))
						os = pp[1];
					else if (pp[0].equals("OC"))
						oc = pp[1];
					else if (pp[0].equals("PS"))
						ps = pp[1];
					else if (pp[0].equals("AL"))
						al = pp[1];
					else if (pp[0].equals("MATRIXUSED"))
						matrixUsed = pp[1];
				}
				addeResult(ret, aid, "SrcPort", srcPort);
				addeResult(ret, aid, "DstPort", dstPort);
				addeResult(ret, aid, "GroupName", groupName);
				addeResult(ret, aid, "ConnName", connName);
				addeResult(ret, aid, "ConnType", connType);
				addeResult(ret, aid, "Status", status);
				addeResult(ret, aid, "AdminstrativeState", as);
				addeResult(ret, aid, "OperationalState", os);
				addeResult(ret, aid, "OperationalCapacity", oc);
				addeResult(ret, aid, "ProtectiveState", ps);
				addeResult(ret, aid, "AlarmStatus", al);
				addeResult(ret, aid, "MatrixUsed", matrixUsed);
			}
	} // end getCrossConnects
	
//	public void getGroupCrossConnects(Vector ret) {
//		
//		Vector v = executeCommand("rtrv-grp-crs;");
//		if (v!=null && v.size() != 0)
//			for (int i=0; i<v.size(); i++) {
//				String line = (String)v.elementAt(i);
//				if (line.indexOf(":")<0) continue;
//				line = line.substring(line.indexOf(":")+1);
//				String[] params = line.split("&");
//				if (params == null || params.length == 0) continue;
//				for (int k=0; k<params.length; k++)
//					if (params[k] != null && params[k].length() > 0)
//						calient.addGroupCrossConnect(params[k]);
//			}
//	} // end getGroupCrossConnects
	
	public void getNetworkElement(Vector ret) {
		
		Vector v = executeCommand("rtrv-ne;");
		if (v!=null && v.size() != 0)
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				line = line.substring(line.indexOf(":")+1);
				String[] params = line.split(",");
				if (params == null || params.length == 0) continue;
				String serialNumber = "N/A";
				String swPackage = "N/A";
				String status = "N/A";
				for (int k=0; k<params.length; k++) {
					if (params[k] == null || params[k].length() == 0) continue;
					String pp[] = params[k].split("=");
					if (pp == null || pp.length != 2) continue;
					if (pp[0].equals("SERIALNUMBER"))
						serialNumber = pp[1];
					else if (pp[0].equals("SWPACKAGE"))
						swPackage = pp[1];
					else if (pp[0].equals("STATUS"))
						status = pp[1];
				}
				addeResult(ret, Node.getName(), "SerialNumber", serialNumber);
				addeResult(ret, Node.getName(), "SoftwareVersion", swPackage);
				addeResult(ret, Node.getName(), "Status", status);
			}
	} // end of class getNetworkElement
	
	public void getOSPF(Vector ret) {
		
		Vector v = executeCommand("rtrv-cfg-ospf;");
		if (v!=null && v.size() != 0)
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				line = line.substring(line.indexOf(":")+1);
				String[] params = line.split(",");
				if (params == null || params.length == 0) continue;
				String routerID = "N/A";
				String areaID = "N/A";
				for (int k=0; k<params.length; k++) {
					if (params[k] == null || params[k].length() == 0) continue;
					String pp[] = params[k].split("=");
					if (pp == null || pp.length != 2) continue;
					if (pp[0].equals("ROUTERID"))
						routerID = pp[1];
					else if (pp[0].equals("AREAID"))
						areaID = pp[1];
				}
				addeResult(ret, "OSPF", "RouterID", routerID);
				addeResult(ret, "OSPF", "AreaID", areaID);
			}
	} // end of getOSPF
	
	public void getRSVP(Vector ret) {
		
		Vector v = executeCommand("rtrv-cfg-rsvp;");
		if (v!=null && v.size() != 0)
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				line = line.substring(line.indexOf(":")+1);
				String[] params = line.split(",");
				if (params == null || params.length == 0) continue;
				String mgsRetryInvl = "N/A"; 
				String ntfRetryInvl = "N/A";  
				String grInvl = "N/A"; 
				String grcvInvl = "N/A"; 
				for (int k=0; k<params.length; k++) {
					if (params[k] == null || params[k].length() == 0) continue;
					String pp[] = params[k].split("=");
					if (pp == null || pp.length != 2) continue;
					if (pp[0].equals("MSGRETRYINVL"))
						mgsRetryInvl = pp[1];
					else if (pp[0].equals("NTFRETRYINVL"))
						ntfRetryInvl = pp[1];
					else if (pp[0].equals("GRINVL"))
						grInvl = pp[1];
					else if (pp[0].equals("GRCVINVL"))
						grcvInvl = pp[1];
				}
				if (mgsRetryInvl.indexOf("-") >= 0)
					mgsRetryInvl = mgsRetryInvl.substring(0, mgsRetryInvl.indexOf("-"));
				addResult(ret, "RSVP", "MsgRetryInterval", mgsRetryInvl);
				if (ntfRetryInvl.indexOf("-") >= 0)
					ntfRetryInvl = ntfRetryInvl.substring(0, ntfRetryInvl.indexOf("-"));
				addResult(ret, "RSVP", "NotificationRetryInterval", ntfRetryInvl);
				if (grInvl.indexOf("-") >= 0)
					grInvl = grInvl.substring(0, grInvl.indexOf("-"));
				addResult(ret, "RSVP", "GracefulInterval", grInvl);
				if (grcvInvl.indexOf("-") >= 0)
					grcvInvl = grcvInvl.substring(0, grcvInvl.indexOf("-"));
				addResult(ret, "RSVP", "GracefulRecoveryInterval", grcvInvl);
			}
	} // end of getRSVP

	public void getCtrlChs(Vector ret) {
		
		Vector v = executeCommand("rtrv-ctrlch;");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String localIP = "N/A"; 
				String remoteIP = "N/A"; 
				String localRid = "N/A"; 
				String remoteRid = "N/A"; 
				String port = "N/A"; 
				String localIfIndex = "N/A"; 
				String remoteIfIndex = "N/A"; 
				String adjanceny = "N/A"; 
				String helloInvl = "N/A"; 
				String helloInvlMin = "N/A"; 
				String helloInvlMax = "N/A"; 
				String deadInvl = "N/A"; 
				String deadInvlMin = "N/A"; 
				String deadInvlMax = "N/A"; 
				String helloInvlNegotiated = "N/A"; 
				String deadInvlNegotiated = "N/A"; 
				String as = "N/A"; 
				String os = "N/A"; 
				String oc = "N/A"; 
				String al = "N/A"; 
				for (int k=0; k<params.length; k++) {
					String cmd[] = params[k].split("=");
					if (cmd == null || cmd.length < 2) continue;
					if (cmd[0].equals("LOCALIP"))
						localIP = cmd[1];
					else if (cmd[0].equals("REMOTEIP"))
						remoteIP = cmd[1];
					else if (cmd[0].equals("LOCALRID"))
						localRid = cmd[1];
					else if (cmd[0].equals("REMOTERID"))
						remoteRid = cmd[1];
					else if (cmd[0].equals("PORT"))
						port = cmd[1];
					else if (cmd[0].equals("LOCALIFINDEX"))
						localIfIndex = cmd[1];
					else if (cmd[0].equals("REMOTEIFINDEX"))
						remoteIfIndex = cmd[1];
					else if (cmd[0].equals("ADJANCECY"))
						adjanceny = cmd[1];
					else if (cmd[0].equals("HELLOINTRVL"))
						helloInvl = cmd[1];
					else if (cmd[0].equals("HELLOINTRVLMIN"))
						helloInvlMin = cmd[1];
					else if (cmd[0].equals("HELLOINTRVLMAX"))
						helloInvlMax = cmd[1];
					else if (cmd[0].equals("DEADINTRVL"))
						deadInvl = cmd[1];
					else if (cmd[0].equals("DEADINTRVLMIN"))
						deadInvlMin = cmd[1];
					else if (cmd[0].equals("DEADINTRVLMAX"))
						deadInvlMax = cmd[1];
					else if (cmd[0].equals("HELLOINTRVLNEGOTIATED"))
						helloInvlNegotiated = cmd[1];
					else if (cmd[0].equals("DEADINTRVLNEGOTIATED"))
						deadInvlNegotiated = cmd[1];
					else if (cmd[0].equals("AS"))
						as = cmd[1];
					else if (cmd[0].equals("OS"))
						os = cmd[1];
					else if (cmd[0].equals("OC"))
						oc = cmd[1];
					else if (cmd[0].equals("AL"))
						al = cmd[1];
				}
				addeResult(ret, "CtrlCh_"+aid, "LocalIP", localIP);
				addeResult(ret, "CtrlCh_"+aid, "RemoteIP", remoteIP);
				addeResult(ret, "CtrlCh_"+aid, "LocalRid", localRid);
				addeResult(ret, "CtrlCh_"+aid, "RemoteRid", remoteRid);
				addeResult(ret, "CtrlCh_"+aid, "Port", port);
				addeResult(ret, "CtrlCh_"+aid, "LocalIfIndex", localIfIndex);
				addeResult(ret, "CtrlCh_"+aid, "RemoteIfIndex", remoteIfIndex);
				addeResult(ret, "CtrlCh_"+aid, "Adjacency", adjanceny);
				addResult(ret, "CtrlCh_"+aid, "HelloInvl", helloInvl);
				addResult(ret, "CtrlCh_"+aid, "HelloInvlMin", helloInvlMin);
				addResult(ret, "CtrlCh_"+aid, "HelloInvlMax", helloInvlMax);
				addResult(ret, "CtrlCh_"+aid, "DeadInvl", deadInvl);
				addResult(ret, "CtrlCh_"+aid, "DeadInvlMin", deadInvlMin);
				addResult(ret, "CtrlCh_"+aid, "DeadInvlMax", deadInvlMax);
				addResult(ret, "CtrlCh_"+aid, "HelloInvlNegotiated", helloInvlNegotiated);
				addResult(ret, "CtrlCh_"+aid, "DeadInvlNegotiated", deadInvlNegotiated);
				addeResult(ret, "CtrlCh_"+aid, "AdminstrativeState", as);
				addeResult(ret, "CtrlCh_"+aid, "OperationalState", os);
				addeResult(ret, "CtrlCh_"+aid, "OperationalCapacity", oc);
				addeResult(ret, "CtrlCh_"+aid, "AlarmStatus", al);
			}
		}
	} // end getCtrlChs
	
	public void getAdjancencies(Vector ret) {
		
		Vector v = executeCommand("rtrv-adj;");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				Vector v1 = executeCommand("rtrv-adj::"+aid+";");
				if (v1 != null && v1.size() != 0) {
					for (int i1=0; i1<v1.size(); i1++) {
						String line1 = (String)v1.elementAt(i1);
						if (line1.indexOf(":")<0) continue;
						aid = line1.substring(0, line.indexOf(":"));
						if (aid == null || aid.length() == 0) continue;
						line1 = line1.substring(line1.indexOf(":")+1);
						String params[] = line1.split(",");
						if (params == null || params.length == 0) continue;
						String localRid = "N/A";
						String remoteRid = "N/A";
						String currentCtrlChannel = "N/A";
						String adjIndex = "N/A";
						String ospfArea = "N/A";
						String metric = "N/A";
						String ospfAdj = "N/A";
						String adjType = "N/A";
						String rsvpRRFlag = "N/A";
						String rsvpRGFlag = "N/A";
						String ntfProc = "N/A";
						String as = "N/A";
						String os = "N/A";
						String oc = "N/A";
						String ps = "N/A";
						String al = "N/A";
						String ctrlChannels = "";
						for (int k=0; k<params.length; k++) {
							String pp[] = params[k].split("=");
							if (pp[0].equals("LOCALRID"))
								localRid = pp[1];
							else if (pp[0].equals("REMOTERID"))
								remoteRid = pp[1];
							else if (pp[0].equals("CURRENTCTRLCHNAME"))
								currentCtrlChannel = pp[1];
							else if (pp[0].startsWith("CTRLCHNAME"))
								ctrlChannels += "&"+pp[1];
							else if (pp[0].equals("ADJINDEX"))
								adjIndex = pp[1];
							else if (pp[0].equals("OSPFAREA"))
								ospfArea = pp[1];
							else if (pp[0].equals("METRIC"))
								metric = pp[1];
							else if (pp[0].equals("OSPFADJ"))
								ospfAdj = pp[1];
							else if (pp[0].equals("ADJTYPE"))
								adjType = pp[1];
							else if (pp[0].equals("RSVPRRFLAG"))
								rsvpRRFlag = pp[1];
							else if  (pp[0].equals("RSVPGRFLAG"))
								rsvpRGFlag = pp[1];
							else if (pp[0].equals("NTFPROC"))
								ntfProc = pp[1];
							else if (pp[0].equals("AS"))
								as = pp[1];
							else if (pp[0].equals("OS"))
								os = pp[1];
							else if (pp[0].equals("OC"))
								oc = pp[1];
							else if (pp[0].equals("PS"))
								ps = pp[1];
							else if (pp[0].equals("AL"))
								al = pp[1];
						}
						addeResult(ret, "Adj_"+aid, "LocalRid", localRid);
						addeResult(ret, "Adj_"+aid, "RemoteRid", remoteRid);
						addeResult(ret, "Adj_"+aid, "CurrentCtrlCh", currentCtrlChannel);
						addeResult(ret, "Adj_"+aid, "AdjIndex", adjIndex);
						addeResult(ret, "Adj_"+aid, "OSPFArea", ospfArea);
						addeResult(ret, "Adj_"+aid, "Metric", metric);
						addeResult(ret, "Adj_"+aid, "OSPFAdj", ospfAdj);
						addeResult(ret, "Adj_"+aid, "AdjType", adjType);
						addeResult(ret, "Adj_"+aid, "RSVPRefreshReductionFlag", rsvpRRFlag);
						addeResult(ret, "Adj_"+aid, "RSVPGracefulRestartFlag", rsvpRGFlag);
						addeResult(ret, "Adj_"+aid, "ProcNotification", ntfProc);
						addeResult(ret, "Adj_"+aid, "AdminstrativeState", as);
						addeResult(ret, "Adj_"+aid, "OperationalState", os);
						addeResult(ret, "Adj_"+aid, "OperationalCapacity", oc);
						addeResult(ret, "Adj_"+aid, "ProtectiveState", ps);
						addeResult(ret, "Adj_"+aid, "AlarmStatus", al);
						if (ctrlChannels.length() > 0)
							addeResult(ret, "Adj_"+aid, "CtrlChannels", ctrlChannels.substring(1));
						else
							addeResult(ret, "Adj_"+aid, "CtrlChannels", "");
					}
				}
			}
		}
	} // end getCalientAdjancencies
	
	public void getLinks(Vector ret) {
		
		Vector v = executeCommand("rtrv-link;");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				Vector v1 = executeCommand("rtrv-link::"+aid+";");
				if (v1 != null && v1.size() != 0) {
					for (int i1=0; i1<v1.size(); i1++) {
						String line1 = (String)v1.elementAt(i1);
						if (line1.indexOf(":")<0) continue;
						aid = line1.substring(0, line.indexOf(":"));
						if (aid == null || aid.length() == 0) continue;
						line1 = line1.substring(line1.indexOf(":")+1);
						String params[] = line1.split(",");
						if (params == null || params.length == 0) continue;
						String linkType = "N/A";
						String localRid = "N/A";
						String remoteRid = "N/A";
						String localIP = "N/A";
						String remoteIP = "N/A";
						String adjName = "N/A";
						String localIfIndex = "N/A"; 
						String remoteIf = "N/A";
						String wdmRemoteIf = "N/A";
						String fptDetect = "N/A";
						String metric = "N/A";
						String lmpVerify = "N/A";
						String adjType = "N/A";
						String admin = "N/A"; 
						String[]totalBandwidth = new String[] {"N/A", "N/A","N/A","N/A","N/A","N/A","N/A"};
						String[]availableBandwidth = new String[] {"N/A", "N/A","N/A","N/A","N/A","N/A","N/A"};
						String port = "N/A";
						String lspEncoding = "N/A";
						String bandwidth = "N/A";
						String portMinPriority = "N/A";
						String portRemPortLabel = "N/A";
						String portLolState = "N/A";
						String swCap = "N/A";
						String as = "N/A";
						String os = "N/A";
						String oc = "N/A";
						String al = "N/A";
						String color = "N/A";
						for (int k=0; k<params.length; k++) {
							String pp[] = params[k].split("=");
							if (pp[0].equals("LINKTYPE"))
								linkType = pp[1];
							else if (pp[0].equals("LOCALRID"))
								localRid = pp[1];
							else if (pp[0].equals("REMOTERID"))
								remoteRid = pp[1];
							else if (pp[0].equals("LOCALIP"))
								localIP = pp[1];
							else if (pp[0].equals("REMOTEIP"))
								remoteIP = pp[1];
							else if (pp[0].equals("ADJNAME"))
								adjName = pp[1];
							else if (pp[0].equals("LOCALIFINDEX"))
								localIfIndex = pp[1];
							else if (pp[0].equals("REMOTEIFINDEX"))
								remoteIf = pp[1];
							else if (pp[0].equals("WDMREMOTETEIF"))
								wdmRemoteIf = pp[1];
							else if (pp[0].equals("FLTDETECT"))
								fptDetect = pp[1];
							else if (pp[0].equals("METRIC"))
								metric = pp[1];
							else if (pp[0].equals("LMPVERIFY"))
								lmpVerify = pp[1];
							else if (pp[0].equals("ADJTYPE"))
								adjType = pp[1];
							else if (pp[0].equals("ADMIN"))
								admin = pp[1];
							else if (pp[0].startsWith("TOTALBANDWIDTH")) {
								int kk = -1;
								try {
									kk = Integer.parseInt(pp[0].substring("TOTALBANDWIDTH".length()));
								} catch (Exception ex) { kk = -1; }
								if (kk >= 0) totalBandwidth[kk] = pp[1]; 
							} else if (pp[0].startsWith("AVAILBANDWIDTH")) {
								int kk = -1;
								try {
									kk = Integer.parseInt(pp[0].substring("AVAILBANDWIDTH".length()));
								} catch (Exception ex) { kk = -1; }
								if (kk >= 0) availableBandwidth[kk] = pp[1];
							} else if (pp[0].equals("PORT"))
								port = pp[1];
							else if (pp[0].equals("LSPENCODE"))
								lspEncoding = pp[1];
							else if (pp[0].equals("BANDWIDTH"))
								bandwidth = pp[1];
							else if (pp[0].equals("PORTREMPORTLABEL"))
								portRemPortLabel = pp[1];
							else if (pp[0].equals("PORTMINPRIORITY"))
								portMinPriority = pp[1];
							else if (pp[0].equals("PORTLOLSTATE"))
								portLolState = pp[1];
							else if (pp[0].equals("SWCAP"))
								swCap = pp[1];
							else if (pp[0].equals("COLOR"))
								color = pp[1];
							else if (pp[0].equals("AS"))
								as = pp[1];
							else if (pp[0].equals("OS"))
								os = pp[1];
							else if (pp[0].equals("OC"))
								oc = pp[1];
							else if (pp[0].equals("AL"))
								al = pp[1];
						}
						addeResult(ret, "TELink_"+aid, "LinkType", linkType);
						addeResult(ret, "TELink_"+aid, "LocalRid", localRid);
						addeResult(ret, "TELink_"+aid, "RemoteRid", remoteRid);
						addeResult(ret, "TELink_"+aid, "LocalIP", localIP);
						addeResult(ret, "TELink_"+aid, "RemoteIP", remoteIP);
						addeResult(ret, "TELink_"+aid, "AdjName", adjName);
						addeResult(ret, "TELink_"+aid, "LocalIfIndex", localIfIndex);
						addeResult(ret, "TELink_"+aid, "RemoteIf", remoteIf);
						addeResult(ret, "TELink_"+aid, "RemPortLabel", portRemPortLabel);
						addeResult(ret, "TELink_"+aid, "WDMRemoteIf",  wdmRemoteIf);
						addeResult(ret, "TELink_"+aid, "FPTDetect", fptDetect);
						addeResult(ret, "TELink_"+aid, "Metric", metric);
						addeResult(ret, "TELink_"+aid, "LMPVerify", lmpVerify);
						addeResult(ret, "TELink_"+aid, "AdjType", adjType);
						addeResult(ret, "TELink_"+aid, "Admin", admin);
						addeResult(ret, "TELink_"+aid, "TotalBandwidth", totalBandwidth);
						addeResult(ret, "TELink_"+aid, "AvailableBandwidth", availableBandwidth);
						addeResult(ret, "TELink_"+aid, "Port", port);
						addeResult(ret, "TELink_"+aid, "LSPEncoding", lspEncoding);
						addeResult(ret, "TELink_"+aid, "Bandwidth", bandwidth);
						addeResult(ret, "TELink_"+aid, "PortMinPriority", portMinPriority);
						addeResult(ret, "TELink_"+aid, "PortLolState", portLolState);
						addeResult(ret, "TELink_"+aid, "SwCap", swCap);
						addeResult(ret, "TeLink_"+aid, "AdminstrativeState", as);
						addeResult(ret, "TeLink_"+aid, "OperationalState", os);
						addeResult(ret, "TeLink_"+aid, "OperationalCapacity", oc);
						addeResult(ret, "TeLink_"+aid, "AlarmStatus", al);
						addeResult(ret, "TELink_"+aid, "Color", color);
					}
				}
			}
		}
	} // end getLinks
	
	public void getConn(Vector ret) {
		
		Vector v = executeCommand("rtrv-conn:::::all;");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String groupName = "N/A";
				String connType = "N/A";
				String srcIP = "N/A";
				String ingressPortIn = "N/A";
				String egressPortOut = "N/A";
				String ingressPortOut = "N/A";
				String egressPortIn = "N/A";
				String dstIP = "N/A";
				String dstPortIn = "N/A";
				String dstPortOut = "N/A";
				String nt = "N/A";
				String srvClass = "N/A";
				String upnt = "N/A";
				String dnnt = "N/A";
				String outTELink = "N/A";
				String adjID = "N/A";
				String extAdj = "N/A";
				String lspEncoding = "N/A";
				String lspPayload = "N/A";
				String bandwidth = "N/A";
				String setPrio = "N/A";
				String holdPrio = "N/A";
				String maxHop = "N/A";
				String recroute = "N/A";
				String proIP = "N/A";
				String proIfIf = "N/A";
				String proHt = "N/A";
				String as = "N/A";
				String os = "N/A";
				String oc = "N/A";
				String al = "N/A";
				String color = "N/A";
				for (int k=0; k<params.length; k++) {
					String pp[] = params[k].split("=");
					if (pp[0].equals("CONNTYPE"))
						connType = pp[1];
					else if (pp[0].equals("SRCIP"))
						srcIP = pp[1];
					else if (pp[0].equals("INGRESSPORTIN"))
						ingressPortIn = pp[1];
					else if (pp[0].equals("EGRESSPORTOUT"))
						egressPortOut = pp[1];
					else if (pp[0].equals("INGRESSPORTOUT"))
						ingressPortOut = pp[1];
					else if (pp[0].equals("EGRESSPORTIN"))
						egressPortIn = pp[1];
					else if (pp[0].equals("DSTIP"))
						dstIP = pp[1];
					else if (pp[0].equals("DSTPORTIN"))
						dstPortIn = pp[1];
					else if (pp[0].equals("DSTPORTOUT"))
						dstPortOut = pp[1];
					else if (pp[0].equals("NT"))
						nt = pp[1];
					else if (pp[0].equals("SRVCLASS"))
						srvClass = pp[1];
					else if (pp[0].equals("UPNT"))
						upnt = pp[1];
					else if (pp[0].equals("DNNT"))
						dnnt = pp[1];
					else if (pp[0].equals("OUTTELINK"))
						outTELink = pp[1];
					else if (pp[0].equals("ADJID"))
						adjID = pp[1];
					else if (pp[0].equals("EXTADJ"))
						extAdj = pp[1];
					else if (pp[0].equals("LSPENCODING"))
						lspEncoding = pp[1];
					else if (pp[0].equals("LSPPAYLOAD"))
						lspPayload = pp[1];
					else if (pp[0].equals("BANDWIDTH"))
						bandwidth = pp[1];
					else if (pp[0].equals("SETUPPRIO"))
						setPrio = pp[1];
					else if (pp[0].equals("HOLDPRIO"))
						holdPrio = pp[1];
					else if (pp[0].equals("MAXHOP"))
						maxHop = pp[1];
					else if (pp[0].equals("RECROUTE"))
						recroute = pp[1];
					else if (pp[0].equals("PROIP"))
						proIP = pp[1];
					else if (pp[0].equals("PROIFID"))
						proIfIf = pp[1];
					else if (pp[0].equals("PROHT"))
						proHt = pp[1];
					else if (pp[0].equals("AS"))
						as = pp[1];
					else if (pp[0].equals("OS"))
						os = pp[1];
					else if (pp[0].equals("OC"))
						oc = pp[1];
					else if (pp[0].equals("AL"))
						al = pp[1];
					else if (pp[0].equals("COLOR"))
						color = pp[1];
				}
				addeResult(ret, "Conn_"+aid, "GroupName", groupName);
				addeResult(ret, "Conn_"+aid, "ConnType", connType);
				addeResult(ret, "Conn_"+aid, "SrcIP", srcIP);
				addeResult(ret, "Conn_"+aid, "IngressPortIn", ingressPortIn);
				addeResult(ret, "Conn_"+aid, "EgressPortOut", egressPortOut);
				addeResult(ret, "Conn_"+aid, "IngressPortOut", ingressPortOut);
				addeResult(ret, "Conn_"+aid, "EgressPortIn", egressPortIn);
				addeResult(ret, "Conn_"+aid, "DstIP", dstIP);
				addeResult(ret, "Conn_"+aid, "DstPortIn", dstPortIn);
				addeResult(ret, "Conn_"+aid, "DstPortOut", dstPortOut);
				addeResult(ret, "Conn_"+aid, "Network", nt);
				addeResult(ret, "Conn_"+aid, "SrvClass", srvClass);
				addeResult(ret, "Conn_"+aid, "UplinkAdjType", upnt);
				addeResult(ret, "Conn_"+aid, "DownlinkAdjType", dnnt);
				addeResult(ret, "Conn_"+aid, "OutputTELink", outTELink);
				addeResult(ret, "Conn_"+aid, "AdjID", adjID);
				addeResult(ret, "Conn_"+aid, "ExternalAdj", extAdj);
				addeResult(ret, "Conn_"+aid, "LSPEncoding", lspEncoding);
				addeResult(ret, "Conn_"+aid, "LSPPayload", lspPayload);
				addeResult(ret, "Conn_"+aid, "Bandwidth", bandwidth);
				addResult(ret, "Conn_"+aid, "SetupPrio", setPrio);
				addResult(ret, "Conn_"+aid, "HoldPrio", holdPrio);
				addResult(ret, "Conn_"+aid, "MaxHop", maxHop);
				addeResult(ret, "Conn_"+aid, "RecordRoute", recroute);
				addeResult(ret, "Conn_"+aid, "PreferredRouteIP", proIP);
				addeResult(ret, "Conn_"+aid, "PreferredRouteIfID", proIfIf);
				addeResult(ret, "Conn_"+aid, "PreferredRouteTypeOfHops", proHt);
				addeResult(ret, "Conn_"+aid, "AdminstrativeState", as);
				addeResult(ret, "Conn_"+aid, "OperationalState", os);
				addeResult(ret, "Conn_"+aid, "OperationalCapacity", oc);
				addeResult(ret, "Conn_"+aid, "AlarmStatus", al);
				addeResult(ret, "Conn_"+aid, "Color", color);
			}
		}
	} // end getConn
	
	public void getCfgConn(Vector ret) {
		
		Vector v = executeCommand("rtrv-cfg-conn;");
		if (v != null && v.size() != 0) {
			for (int i=0; i<v.size(); i++) {
				String line = (String)v.elementAt(i);
				if (line.indexOf(":")<0) continue;
				String aid = line.substring(0, line.indexOf(":"));
				if (aid == null || aid.length() == 0) continue;
				line = line.substring(line.indexOf(":")+1);
				String params[] = line.split(",");
				if (params == null || params.length == 0) continue;
				String bpInvl = "N/A";
				String initConnSetupTmout = "N/A";
				String minRetryInvl = "N/A";
				String maxNumConnRetry = "N/A";
				String slowInvl = "N/A";
				String initRespTmout = "N/A";
				String maxConnPb = "N/A"; 
				for (int k=0; k<params.length; k++) {
					String pp[] = params[k].split("=");
					if (pp[0].equals("BPINVL"))
						bpInvl = pp[1];
					else if (pp[0].equals("INITCONNSETUPTMOUT"))
						initConnSetupTmout = pp[1];
					else if (pp[0].equals("MINRETRYINVL"))
						minRetryInvl = pp[1];
					else if (pp[0].equals("MAXNUMCONNRETRY"))
						maxNumConnRetry = pp[1];
					else if (pp[0].equals("SLOWINVL"))
						slowInvl = pp[1];
					else if (pp[0].equals("INITRESPTMOUT"))
						initRespTmout = pp[1];
					else if (pp[0].equals("MAXCONNPB"))
						maxConnPb = pp[1];
				}
				if (bpInvl.indexOf("-") >= 0)
					bpInvl = bpInvl.substring(0, bpInvl.indexOf("-"));
				addResult(ret, "ConnCfg", "ConnBidPeriodTimer", bpInvl);
				if (initConnSetupTmout.indexOf("-") >= 0)
					initConnSetupTmout = initConnSetupTmout.substring(0, initConnSetupTmout.indexOf("-"));
				addResult(ret, "ConnCfg", "InitConnSetupTimeout", initConnSetupTmout); 
				if (minRetryInvl.indexOf("-") >= 0)
					minRetryInvl = minRetryInvl.substring(0, minRetryInvl.indexOf("-"));
				addResult(ret, "ConnCfg", "MinRetryInvl", minRetryInvl);
				addResult(ret, "ConnCfg", "MaxNumConnRetry", maxNumConnRetry);
				if (slowInvl.indexOf("-") >= 0)
					slowInvl = slowInvl.substring(0, slowInvl.indexOf("-"));
				addResult(ret, "ConnCfg", "ConnSlowInvl", slowInvl);
				if (initRespTmout.indexOf("-") >= 0)
					initRespTmout = initRespTmout.substring(0, initRespTmout.indexOf("-"));
				addResult(ret, "ConnCfg", "InitRespTimeout", initRespTmout);
				addResult(ret, "ConnCfg", "MaxConnNo", maxConnPb);
			}
		}
	} // getCfgConn
	
	public Vector executeCommand(String command) {
		
		if (osConn == null) {
		    osConn = OSTelnetFactory.getInstance(OSTelnet.CALIENT);
		}

		Vector v = new Vector();
		
		try {
			BufferedReader br = osConn.doCmd(command);
            for(String line = br.readLine();line != null;line = br.readLine()) {
                line=line.trim();
                if (line.startsWith("M ") || line.startsWith("M\t")) { // status of the command execution
                	if (line.indexOf("COMPLD") < 0) {
//                		logger.log(Level.WARNING, "monCalientPXC: Got DENY when executing "+command);
                		return null;
                	}
                }
                if(!line.startsWith("\"") || !line.endsWith("\"")) continue;
                String cline = line.substring(1, line.length()-1);
                if(cline == null) continue;
                cline = cline.trim();
                int len1 = cline.length();
                if(len1 > 0) {
                	v.add(cline);
                }
            }
		} catch (Throwable t) {
			logger.log(Level.WARNING, "monCalientPXC: Got exception while parsing...", t);
		}
		return v;
	} // end executeCommand
	
	static public void main(String[] args) {
		
		monCalientPXC aa = new monCalientPXC();
		String ad = null;
		String host = null;
		try {
			host = (InetAddress.getLocalHost()).getHostName();
			ad = InetAddress.getByName(host).getHostAddress();
		} catch (Exception e) {
			System.out.println(" Can not get ip for node " + e);
			System.exit(-1);
		}
		System.out.println("Using hostname= " + host + " IPaddress=" + ad);
		aa.init(new MNode(host, ad, new MCluster("CMap", null), null), null);
		
		String username = AppConfig.getProperty("lia.util.telnet.CalientUsername", null).trim();
		String passwd = AppConfig.getProperty("lia.util.telnet.CalientPasswd", null).trim();
		String hostName = AppConfig.getProperty("lia.util.telnet.CalientHostname", null).trim();
		
		System.out.println("monCalientPXC: Connecting to "+hostName+" using uid="+username+" and pid="+passwd);
		
		try {
			for (int k = 0; k < 10000; k++) {
				Vector bb = (Vector) aa.doProcess();
				for (int i=0; i<bb.size(); i++) {
					System.out.println(bb.get(i).toString());
				}
				System.out.println(bb);
				System.out.println("-------- sleeeping ----------");
				Thread.sleep(5000);
				System.out.println("-------- doProcess-ing --------- k=" + k);
			}
		} catch (Exception e) {
			System.out.println(" failed to process !!!"+e);
			e.printStackTrace();
		}
	}
	
} // end of class monCalientPXC

