/*
 * $Id: monOSPortsPower.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AttributePublisher;
import lia.Monitor.monitor.MLAttributePublishers;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.ntp.NTPDate;
import lia.util.telnet.OSTelnet;
import lia.util.telnet.OSTelnetFactory;

/**
 * 
 * @author ramiro
 * @author Ciprian Dobre
 * 
 */
public class monOSPortsPower extends cmdExec implements MonitoringModule, Observer {

    /**
     * 
     */
    private static final long serialVersionUID = 5283336158754448886L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monOSPortsPower.class.getName());

    public MNode Node;

    static String[] ResTypes = { "Port-Power" };

    public MonModuleInfo info;
    short switchType;

    String moduleName;

    DateFileWatchdog dfw;
    File moduleConfFile;

    ArrayList portsList;
    Object portsSync;

    ArrayList removePortsResults;

    OSTelnet osConn;
    int portsSendCounter;

    public static Hashtable simulatedPortsHash = new Hashtable();
    public static final String GLIMMER_TL1_CMD_PORT_POWER = "rtrv-port-power::all:" + OSTelnet.PPOWER_CTAG
            + OSTelnet.TL1_FINISH_CMD;
    private static final AttributePublisher publisher = MLAttributePublishers.getInstance();

    public monOSPortsPower() {
        portsSync = new Object();
        portsList = null; //should be set only by (re)loadConf() method ;)
        moduleName = "monOSPortsPower";
        isRepetitive = true;
        portsSendCounter = 0;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.INFO,
                "monOSPortsPower: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                        + " nodeName=" + Node.getName());

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                String argTemp = arg2.trim();
                if (argTemp.startsWith("SwitchType")) {
                    String switchName = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, "monOSPortsPower: swType = " + switchName);
                    switchType = OSTelnet.getType(switchName);
                } else if (argTemp.startsWith("PortMap")) {
                    try {
                        File f = new File(argTemp.split("(\\s)*=(\\s)*")[1].trim());
                        if (f.exists() && f.canRead()) {
                            dfw = DateFileWatchdog.getInstance(f, 2 * 1000);
                            dfw.addObserver(this);
                            moduleConfFile = f;
                        } else {
                            logger.log(Level.WARNING, " File for PortMap = " + f.toString() + " cannot be read!");
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " Got exc in init", t);
                        dfw = null;
                        moduleConfFile = null;
                    }
                }
            }
        }

        info = new MonModuleInfo();
        info.name = moduleName;
        info.ResTypes = ResTypes;
        reloadConf();
        return info;
    }

    private void reloadConf() {
        ArrayList newPortsList = new ArrayList();

        if (moduleConfFile == null) {
            return;
        }

        try {
            int lineCount = 0;//just for debugging...
            BufferedReader br = new BufferedReader(new FileReader(moduleConfFile));
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (line.trim().startsWith("#")) {
                    continue; //ignore possible comments
                }
                lineCount++;
                try {
                    String[] linePorts = line.split("(\\s)*,(\\s)*");
                    if ((linePorts == null) || (linePorts.length == 0)) {
                        continue;
                    }
                    for (String linePort : linePorts) {
                        String port = linePort.trim();
                        if (port.length() > 0) {
                            newPortsList.add(port);
                        }
                    }//end for - linePorts
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line " + lineCount + ": " + line, t);
                }
            }//end for - line
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception parsing the conf file", t);
        }

        synchronized (portsSync) {
            //Are there any ports which needs to be removed?
            if (portsList != null) {//first time it will be null
                for (Iterator it = portsList.iterator(); it.hasNext();) {
                    String oldPort = (String) it.next();
                    if (!newPortsList.contains(oldPort)) {
                        eResult er = new eResult();
                        er.FarmName = Node.getFarmName();
                        er.ClusterName = Node.getClusterName();
                        er.Module = moduleName;
                        er.NodeName = oldPort + "_In";
                        er.param = null;
                        er.param_name = null;
                        if (removePortsResults == null) {
                            removePortsResults = new ArrayList();
                        }
                        removePortsResults.add(er);
                        er = new eResult();
                        er.FarmName = Node.getFarmName();
                        er.ClusterName = Node.getClusterName();
                        er.Module = moduleName;
                        er.NodeName = oldPort + "_Out";
                        er.param = null;
                        er.param_name = null;
                        removePortsResults.add(er);
                    }//if
                }//for
            }//if
            portsList = newPortsList;
        }
    }

    private Vector getPortsPower() {
        switch (switchType) {
        case (OSTelnet.CALIENT): {
            return getPortsPowerCalient();
        }
        case (OSTelnet.GLIMMERGLASS): {
            return getPortsPowerGlimmer();
        }
        }
        return null;
    }

    private Result getResultForPort(String port, double power, long time) {
        Result rez = new Result();
        rez.FarmName = Node.getFarmName();
        rez.ClusterName = Node.getClusterName();
        rez.Module = moduleName;
        rez.NodeName = port;

        //Just a hack ... should be removed
        Double simulatedVal = (Double) simulatedPortsHash.get(port);
        if (simulatedVal != null) {
            power = simulatedVal.doubleValue();
        }

        rez.addSet("Port-Power", power);
        rez.time = time;
        return rez;
    }

    private Vector getPortsPowerCalient() {
        if (portsList == null) {
            return null;
        }
        Vector vec = new Vector();
        long time = NTPDate.currentTimeMillis();

        String inPatternStr1 = "InOpticalPower";
        String inPatternStr2 = "INOPTICALPOWER";
        String outPatternStr1 = "OutOpticalPowerWorking";
        String outPatternStr2 = "OUTOPTICALPOWERWORKING";

        HashMap input = new HashMap();
        HashMap output = new HashMap();
        Vector newPortsList = null;
        synchronized (portsList) {
            newPortsList = new Vector(portsList);
        }
        for (Iterator it = newPortsList.iterator(); it.hasNext();) {
            String portName = (String) it.next();

            // RTRV-DET-EQPT:[TID]:<eqptId>:[CTAG];
            // SID DATE TIME
            // M CTAG COMPLD
            // AID:[SWVERSION=<SwVersion>],....,[INOPTICALPOWER=<InOpticalPower>],...,[OUTOPTICALPOWERWORKING=<OutOpticalPower>]

            try {
                String[] lines = osConn.execCmdAndGet("rtrv-det-eqpt::" + portName + ":" + OSTelnet.PPOWER_CTAG
                        + OSTelnet.TL1_FINISH_CMD, OSTelnet.PPOWER_CTAG);
                String line;
                for (String line2 : lines) {
                    line = line2;
                    try {
                        String trimmedLine = line.trim();
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Parsing (trimmed)line " + trimmedLine);
                        }
                        String[] arguments = trimmedLine.split(",");
                        if ((arguments == null) || (arguments.length == 0)) {
                            continue;
                        }
                        for (String argument : arguments) {
                            if ((argument.indexOf(inPatternStr1) == 0) || (argument.indexOf(inPatternStr2) == 0)) {
                                String power = argument.substring(inPatternStr1.length() + 1);
                                if (power == null) {
                                    continue;
                                }
                                power = power.trim();
                                if (power.indexOf("\"") != -1) {
                                    power = power.substring(0, power.length() - 1);
                                }
                                input.put(portName, power);
                                continue;
                            }
                            if ((argument.indexOf(outPatternStr1) == 0) || (argument.indexOf(outPatternStr2) == 0)) {
                                String power = argument.substring(outPatternStr1.length() + 1);
                                if (power == null) {
                                    continue;
                                }
                                power = power.trim();
                                if (power.indexOf("\"") != -1) {
                                    power = power.substring(0, power.length() - 1);
                                }
                                output.put(portName, power);
                            }
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
            }
        }
        synchronized (portsSync) {
            for (Iterator it = portsList.iterator(); it.hasNext();) {
                String portName = (String) it.next();

                String sInP = (String) input.get(portName);
                String sOutP = (String) output.get(portName);

                double dip = Double.MAX_VALUE;
                double dop = Double.MAX_VALUE;

                try {
                    dip = Double.valueOf(sInP).doubleValue();
                    dop = Double.valueOf(sOutP).doubleValue();
                } catch (Throwable t) {
                    dip = Double.MAX_VALUE;
                    dop = Double.MAX_VALUE;
                }

                if ((dip == Double.MAX_VALUE) || (dop == Double.MAX_VALUE)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " Wrong Power for port " + portName + " ---> InPower = " + sInP
                                + " OutPower = " + sOutP);
                    }
                    continue;
                }

                vec.add(getResultForPort(portName + "_In", dip, time));
                vec.add(getResultForPort(portName + "_Out", dop, time));

            }// end for()

            if (removePortsResults != null) {
                vec.addAll(removePortsResults);
                removePortsResults = null;
            }
        }//end sync

        return vec;
    }

    private void fillPortsListGlimmer() {
        ArrayList newPortList = new ArrayList();

        try {

            String[] lines = osConn.execCmdAndGet(GLIMMER_TL1_CMD_PORT_POWER, OSTelnet.PPOWER_CTAG);
            String line;
            for (String line2 : lines) {
                line = line2;
                try {
                    String trimmedLine = line.trim();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Parsing (trimmed)line " + trimmedLine);
                    }
                    if (trimmedLine.indexOf("PORTPOWER=") != -1) {
                        String[] tk = trimmedLine.substring(1, trimmedLine.length() - 1).split("PORTPOWER="); // also remove "" from the beginning and the end of line
                        String strPort = tk[0].split("PORTID=")[1].split(",")[0].substring(3);
                        newPortList.add(strPort);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Adding to newPortList [ " + strPort + " ]");
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }
        if (newPortList.size() > 0) {
            synchronized (portsSync) {
                portsList = newPortList;
            }
        }

        if (portsList == null) {
            logger.log(Level.INFO, " Set initial failed");
        } else {
            logger.log(Level.INFO, " Set initial PortList.size(" + portsList.size() + ") := " + portsList);
        }
    }

    private Vector getPortsPowerGlimmer() {
        if (portsList == null) {
            fillPortsListGlimmer();
            if (portsList == null) {
                return null;
            }
        }
        Vector vec = new Vector();
        long time = NTPDate.currentTimeMillis();

        HashMap hm = new HashMap();
        //        <rtrv-port-power::all:1;
        //        rtrv-port-power::all:1;
        //
        //        o01gva.datatag.org 05-06-24 19:56:23
        //     M  1 COMPLD
        //        "GGN:PORTID=10001,PORTPOWER=-47.922"
        //        "GGN:PORTID=10002,PORTPOWER=-48.613"
        //        "GGN:PORTID=10003,PORTPOWER=-0.951"
        //        "GGN:PORTID=10004,PORTPOWER=-1.578"
        //        "GGN:PORTID=10005,PORTPOWER=-1.732"
        //        "GGN:PORTID=10006,PORTPOWER=-49.775"
        try {
            String[] lines = osConn.execCmdAndGet(GLIMMER_TL1_CMD_PORT_POWER, OSTelnet.PPOWER_CTAG);
            String line;
            for (String line2 : lines) {
                line = line2;
                try {
                    String trimmedLine = line.trim();
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, " Parsing (trimmed)line " + trimmedLine);
                    }
                    if (trimmedLine.indexOf("PORTPOWER=") != -1) {
                        String[] tk = trimmedLine.substring(1, trimmedLine.length() - 1).split("PORTPOWER="); // also remove "" from the beginning and the end of line
                        String strPort = tk[0].split("PORTID=")[1].split(",")[0];
                        String strPower = tk[1];
                        hm.put(strPort, strPower);
                        if (logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, " Adding to hmap [ " + strPort + " -> " + strPower + " ]");
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got exception parsing line [" + line + "]", t);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exception reading from command's buffered stream", t);
        }

        synchronized (portsSync) {
            for (Iterator it = portsList.iterator(); it.hasNext();) {
                String portName = (String) it.next();

                String inP = "100" + portName;
                String outP = "200" + portName;

                String sInP = (String) hm.get(inP);
                String sOutP = (String) hm.get(outP);

                double dip = Double.MAX_VALUE;
                double dop = Double.MAX_VALUE;

                try {
                    dip = Double.valueOf(sInP).doubleValue();
                    dop = Double.valueOf(sOutP).doubleValue();
                } catch (Throwable t) {
                    dip = Double.MAX_VALUE;
                    dop = Double.MAX_VALUE;
                }

                if ((dip == Double.MAX_VALUE) || (dop == Double.MAX_VALUE)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " Wrong Power for port " + portName + " ---> InPower = " + sInP
                                + " OutPower = " + sOutP);
                    }
                    continue;
                }

                vec.add(getResultForPort(portName + "_In", dip, time));
                vec.add(getResultForPort(portName + "_Out", dop, time));

            }// end for()

            if (removePortsResults != null) {
                vec.addAll(removePortsResults);
                removePortsResults = null;
            }
        }//end sync

        return vec;
    }

    @Override
    public Object doProcess() throws Exception {
        long sTime = System.currentTimeMillis();
        Vector v = null;
        try {
            try {
                publisher.publish("OS_PortMap", portsList);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Could not export ports list");
            }

            osConn = OSTelnetFactory.getMonitorInstance(switchType);

            v = getPortsPower();

            if ((portsSendCounter++ % 10) == 0) {
                portsSendCounter = 0;
                if (v == null) {
                    v = new Vector();
                }
                synchronized (portsSync) {
                    if ((portsList != null) && (portsList.size() > 0)) {
                        eResult er = new eResult();
                        er.FarmName = Node.getFarmName();
                        er.ClusterName = Node.getClusterName();
                        er.Module = moduleName;
                        er.NodeName = (String) portsList.get(0) + "_In";
                        er.addSet("Port-Power", portsList);
                        v.add(er);
                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER, " Sending eResult with port list" + er.toString());
                        }
                    }
                }
            }
            return v;
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                StringBuilder sb = new StringBuilder(8192);
                sb.append(" [ monOSPortPower ] dt= [ ").append(System.currentTimeMillis() - sTime).append(" ] ms \n");
                if (logger.isLoggable(Level.FINEST)) {
                    sb.append(" returning ").append(v).append("\n");
                }
                logger.log(Level.FINE, sb.toString());
            }
        }
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return ResTypes;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    @Override
    public MNode getNode() {
        return Node;
    }

    @Override
    public String getClusterName() {
        return Node.getClusterName();
    }

    @Override
    public String getFarmName() {
        return Node.getFarmName();
    }

    @Override
    public String getTaskName() {
        return moduleName;
    }

    @Override
    public boolean isRepetitive() {
        return isRepetitive;
    }

    @Override
    public boolean stop() {
        logger.log(Level.INFO, " monOSPortsPower stop() Request . SHOULD NOT!!!");
        return true;
    }

    static public void main(String[] args) {

        monOSPortsPower aa = new monOSPortsPower();
        String ad = null;
        String host = null;
        if ((args == null) || (args.length < 1) || (args[0] == null) || (args[0].trim().length() == 0)) {
            System.out.println("First argument must be the optical switch type ! [ Calient | Glimmerglass ]");
            System.exit(1);
        }

        String sType = args[0].trim().toLowerCase();
        boolean isCalient = false;

        if (sType.indexOf("calient") != -1) {
            isCalient = true;
        } else if (sType.indexOf("glimmer") == -1) {
            System.out.println("First argument must be the optical switch type ! [ Calient | Glimmerglass ]");
            System.exit(1);
        }

        try {
            host = (InetAddress.getLocalHost()).getHostName();
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }
        System.out.println("Using hostname= " + host + " IPaddress=" + ad);
        if (isCalient) {
            aa.init(new MNode(host, ad, null, null), "SwitchType = Calient; PortMap = /home/ramiro/PortMap_Calient");
            System.out.println("\n\n SwitchType = Calient");
        } else {
            aa.init(new MNode(host, ad, null, null),
                    "SwitchType = Glimmerglass; PortMap = /home/ramiro/PortMap_Glimmer");
            System.out.println("\n\n SwitchType = Glimmerglass");
        }

        try {
            for (int k = 0; k < 10000; k++) {
                Vector bb = (Vector) aa.doProcess();
                for (int q = 0; q < bb.size(); q++) {
                    System.out.println(bb.get(q).toString());
                }
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            System.out.println(" failed to process !!!");
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((o != null) && o.equals(dfw)) {//just extra check
            reloadConf();
        }
    }

}
