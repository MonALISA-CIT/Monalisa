package lia.Monitor.JiniClient.Farms.OSGmap.Config;

import java.rmi.Naming;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;
import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwCrossConn;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tClient;

/** clasa care se modifica */

public class OSMonitorControl implements PortAdminListener, LocalDataFarmClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSMonitorControl.class.getName());

    ConfigFrame frame;
    OSAdminInterface remoteCtrl;
    public OpticalSwitchInfo info;
    public OSwConfig newInfo; // the new type of config
    private static Hashtable opticalSwInfos = new Hashtable();
    private final Vector<String> alreadyAddedPorts;

    LocalDataFarmProvider provider;

    private final String address;
    private final int remoteRegistryPort;

    private OSMonitorControl(LocalDataFarmProvider provider, String address, int remoteRegistryPort) throws Exception {

        this.address = address;
        this.remoteRegistryPort = remoteRegistryPort;
        remoteCtrl = null;
        alreadyAddedPorts = new Vector<String>();
        Remote r = Naming.lookup("rmi://" + address + ":" + remoteRegistryPort + "/OS_Admin");
        remoteCtrl = (OSAdminInterface) r;
        this.provider = provider;
    }

    public static OSMonitorControl connectTo(LocalDataFarmProvider provider, String address, int remoteRegistryPort) {

        //		System.out.println("rmi port = "+remoteRegistryPort);

        String keyStore = System.getProperty("lia.Monitor.KeyStore", null);
        if ((keyStore == null) || (keyStore.length() == 0)) {
            return null;
        }
        try {
            OSMonitorControl control = new OSMonitorControl(provider, address, remoteRegistryPort);
            control.remoteCtrl.connectPorts("", "", null, true); // must test if the line is really secure
            return control;
        } catch (Throwable t) {
            logger.finer("Got exception for " + address + ": " + t);
            return null;
        }
    }

    public void setName(String name) {

        if (frame != null) {
            frame.setVisible(false);
            if (frame.gmplsAdmin != null) {
                frame.gmplsAdmin.getWindow().setVisible(false);
            }
            frame.dispose();
            frame = null;
        }
        frame = new ConfigFrame(name, this);
        if (info != null) {
            setOpticalSwitchInfo(info);
        }
        frame.addPortAdminListener(this);
    }

    public ConfigFrame getFrame() {
        return frame;
    }

    public void showWindow() {

        if (frame == null) {
            return;
        }
        frame.setVisible(true);
    }

    public void windowClosed() {

        if (frame != null) {
            frame.setVisible(false);
            if (frame.gmplsAdmin != null) {
                frame.gmplsAdmin.getWindow().setVisible(false);
            }
            frame.stopIt();
        }
        if (remoteCtrl != null) {
            try {
                Naming.unbind("rmi://" + address + ":" + remoteRegistryPort + "/OS_Admin");
            } catch (Exception ex) {
            }
        }
    }

    @Override
    public void changePortState(String portName, String newSignalType) {
        try {
            if (remoteCtrl != null) {
                String remoteCode = remoteCtrl.changePortState(portName, newSignalType);
                logger.log(Level.INFO, "Remote cmd CHANGE_PORT_STATE[" + portName + "," + newSignalType
                        + "] returned code " + remoteCode);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Could not send remote cmd CHANGE_PORT_STATE[" + portName + "," + newSignalType
                    + "]");
        }
    }

    /**
     * //TODO 
     *  Is it ok to return void ??
     */
    @Override
    public void disconnectPorts(String inputPort, String outputPort, boolean fullDuplex) {

        try {
            if (remoteCtrl != null) {
                String remoteCode = remoteCtrl.disconnectPorts(inputPort, outputPort, null, fullDuplex);
                //TODO - Change the log level
                logger.log(Level.INFO, " Remote cmd DISCONNECT_PORTS[" + inputPort + "," + outputPort + "] returned: "
                        + remoteCode);
                System.out.println(" Remote cmd DISCONNECT_PORTS[" + inputPort + "," + outputPort + "] returned: "
                        + remoteCode);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Could not send remote cmd disconnectPorts [ " + inputPort + " - " + outputPort
                    + " ]");
        }
    }

    /**
     * //TODO 
     *  Is it ok to return void ??
     */
    @Override
    public void connectPorts(String inputPort, String outputPort, boolean fullDuplex) {

        try {
            if (remoteCtrl != null) {
                String remoteCode = remoteCtrl.connectPorts(inputPort, outputPort, null, fullDuplex);
                //TODO - Change the log level
                logger.log(Level.INFO, " Remote cmd CONNECT_PORTS[" + inputPort + "," + outputPort + "] returned: "
                        + remoteCode);
                System.out.println(" Remote cmd CONNECT_PORTS[" + inputPort + "," + outputPort + "] returned: "
                        + remoteCode);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Could not send remote cmd connectPorts [ " + inputPort + " - " + outputPort
                    + " ]");
        }
    }

    @Override
    public void newFarmResult(MLSerClient client, Object res) {

        if (res instanceof Result) {
            processResult((Result) res);
        } else if (res instanceof Vector) {
            Vector v = (Vector) res;
            for (int i = 0; i < v.size(); i++) {
                Object o = v.get(i);
                if (o instanceof Result) {
                    processResult((Result) o);
                } else if (o instanceof eResult) {
                    processResult((eResult) o);
                }
            }
        } else if (res instanceof eResult) {
            processResult((eResult) res);
        }
    }

    public static void setOpticalSwitchInfo(String device, OpticalSwitchInfo info) {
        opticalSwInfos.put(device, info);
    }

    public static void setOpticalSwitchInfo(String device, OSwConfig info) {
        opticalSwInfos.put(device, info);
    }

    public void setOpticalSwitchInfo(OpticalSwitchInfo info) {

        if (frame == null) {
            return;
        }

        synchronized (opticalSwInfos) {
            this.info = info;
            if ((info != null) && (frame != null)) {
                StringBuilder buf = new StringBuilder();
                if (info.type.shortValue() == OpticalSwitchInfo.CALIENT) {
                    buf.append("CALIENT_");
                    frame.enableGMPLS();
                } else if (info.type.shortValue() == OpticalSwitchInfo.GLIMMERGLASS) {
                    buf.append("GLIMMERGLASS_");
                }
                buf.append(info.name);

                frame.setDeviceParams(buf.toString(), "", ""); // TODO check how to get all the ports

                Hashtable h = frame.getCurrentConns();
                Vector<String> v = new Vector<String>();
                for (Enumeration en = h.keys(); en.hasMoreElements();) {
                    String p = (String) en.nextElement();
                    //					System.out.println(p);
                    if (p.startsWith("in_")) {
                        v.add(p.substring(3));
                    }
                }

                // first check the ports withing the cross connections
                if ((info.crossConnects != null) && (info.crossConnects.size() != 0)) {
                    for (OSPort osPort : info.crossConnects.keySet()) {
                        OpticalCrossConnectLink link = info.crossConnects.get(osPort);
                        String sPort = link.sPort.name;
                        String dPort = link.dPort.name;
                        if (v.contains(sPort)) {
                            v.remove(sPort);
                        }
                        if (!alreadyAddedPorts.contains("in_" + sPort)) {
                            alreadyAddedPorts.add("in_" + sPort);
                            alreadyAddedPorts.add("out_" + dPort);
                            frame.setInputPortObj(sPort, link.sPort);
                            frame.setOutputPortsObj(dPort, link.dPort);
                            try {
                                frame.setInputPortState(sPort, "GOOD", link.sPort.power.doubleValue() + " dBm", 2);
                                frame.setInputPortPower(sPort, link.sPort.power.doubleValue());
                            } catch (Exception ex) {
                            }
                            try {
                                frame.setOutputPortState(dPort, "GOOD", link.dPort.power.doubleValue() + " dBm");
                                frame.setOutputPortPower(dPort, link.dPort.power.doubleValue());
                            } catch (Exception ex) {
                            }
                            // find out if we have connection....
                            //							if (link.status.intValue() == OpticalCrossConnectLink.OK) {
                            frame.addPort(sPort, PortsPanel.connected, dPort);
                            frame.setConnState(sPort, dPort, "STEADY", "unknown", "unknown", "unknown");
                            //							} else {
                            //								frame.addPort(sPort, PortsPanel.connected_nolight, dPort);
                            //								frame.setConnState(sPort, dPort, "NO LIGHT", "unknown", "unknown", "unknown");
                            //							} 
                        } else {
                            //							if (link.status.intValue() == OpticalCrossConnectLink.OK) {
                            frame.setPortState(sPort, PortsPanel.connected, dPort);
                            frame.setConnState(sPort, dPort, "STEADY", "unknown", "unknown", "unknown");
                            //							} else {
                            //								frame.setPortState(sPort, PortsPanel.connected_nolight, dPort);
                            //								frame.setConnState(sPort, dPort, "NO LIGHT", "unknown", "unknown", "unknown");
                            //							} 
                            try {
                                frame.setInputPortPower(sPort, link.sPort.power.doubleValue());
                                frame.setInputPortState(sPort, "GOOD", link.sPort.power.doubleValue() + " dBm", 2);
                            } catch (Exception ex) {
                            }
                            try {
                                frame.setOutputPortPower(dPort, link.dPort.power.doubleValue());
                                frame.setOutputPortState(dPort, "GOOD", link.dPort.power.doubleValue() + " dBm");
                            } catch (Exception ex) {
                            }
                        }
                    }
                }
                if (v.size() != 0) { // we must delete some old connections...
                    for (int i = 0; i < v.size(); i++) {
                        String p = v.get(i);
                        frame.setPortState(p, PortsPanel.unconnected, null);
                    }
                }
                for (Object element : info.map.keySet()) {
                    OSPort port = (OSPort) element;
                    if (port.type.shortValue() == OSPort.INPUT_PORT) {
                        frame.setInputPortObj(port.name, port);
                        try {
                            frame.setInputPortPower(port.name, port.power.doubleValue());
                            frame.setInputPortState(port.name, "GOOD", port.power.doubleValue() + " dBm", 2);
                        } catch (Exception ex) {
                        }
                    } else {
                        frame.setOutputPortsObj(port.name, port);
                        try {
                            frame.setOutputPortPower(port.name, port.power.doubleValue());
                            frame.setOutputPortState(port.name, "GOOD", port.power.doubleValue() + " dBm");
                        } catch (Exception ex) {
                        }
                    }
                }
                frame.update();
            }
        }
    }

    public void setOpticalSwitchInfo(OSwConfig info) {

        if (frame == null) {
            return;
        }

        synchronized (opticalSwInfos) {
            this.newInfo = info;
            if ((info != null) && (frame != null)) {
                StringBuilder buf = new StringBuilder();
                if (info.type == OpticalSwitchInfo.CALIENT) {
                    buf.append("CALIENT_");
                    frame.enableGMPLS();
                } else if (info.type == OpticalSwitchInfo.GLIMMERGLASS) {
                    buf.append("GLIMMERGLASS_");
                }
                buf.append(info.name);

                frame.setDeviceParams(buf.toString(), "", ""); // TODO check how to get all the ports

                Hashtable h = frame.getCurrentConns();
                Vector v = new Vector();
                for (Enumeration en = h.keys(); en.hasMoreElements();) {
                    String p = (String) en.nextElement();
                    if (p.startsWith("in_")) {
                        v.add(p.substring(3));
                    }
                }

                // first check the ports withing the cross connections
                if ((info.crossConnects != null) && (info.crossConnects.length != 0)) {
                    for (final OSwCrossConn link : info.crossConnects) {
                        if ((link.sPort.type == OSwPort.MULTICAST_PORT) || (link.dPort.type == OSwPort.MULTICAST_PORT)) {
                            continue;
                        }
                        String sPort = link.sPort.name;
                        String dPort = link.dPort.name;
                        if (v.contains(sPort)) {
                            v.remove(sPort);
                        }
                        if (!alreadyAddedPorts.contains("in_" + sPort)) {
                            alreadyAddedPorts.add("in_" + sPort);
                            alreadyAddedPorts.add("out_" + dPort);
                            frame.setInputPortObj(sPort, link.sPort);
                            frame.setOutputPortsObj(dPort, link.dPort);
                            try {
                                frame.setInputPortState(sPort, "GOOD", link.sPort.power + " dBm", 2);
                                frame.setInputPortPower(sPort, link.sPort.power);
                            } catch (Exception ex) {
                            }
                            try {
                                frame.setOutputPortState(dPort, "GOOD", link.dPort.power + " dBm");
                                frame.setOutputPortPower(dPort, link.dPort.power);
                            } catch (Exception ex) {
                            }
                            // find out if we have connection....
                            frame.addPort(sPort, PortsPanel.connected, dPort);
                            frame.setConnState(sPort, dPort, "STEADY", "unknown", "unknown", "unknown");
                        } else {
                            frame.setPortState(sPort, PortsPanel.connected, dPort);
                            frame.setConnState(sPort, dPort, "STEADY", "unknown", "unknown", "unknown");
                            try {
                                frame.setInputPortPower(sPort, link.sPort.power);
                                frame.setInputPortState(sPort, "GOOD", link.sPort.power + " dBm", 2);
                            } catch (Exception ex) {
                            }
                            try {
                                frame.setOutputPortPower(dPort, link.dPort.power);
                                frame.setOutputPortState(dPort, "GOOD", link.dPort.power + " dBm");
                            } catch (Exception ex) {
                            }
                        }
                    }
                }
                if ((v != null) && (v.size() != 0)) { // we must delete some old connections...
                    for (int i = 0; i < v.size(); i++) {
                        String p = (String) v.get(i);
                        frame.setPortState(p, PortsPanel.unconnected, null);
                    }
                }
                if (info.osPorts != null) {
                    for (OSwPort port : info.osPorts) {
                        if (port.type == OSwPort.MULTICAST_PORT) {
                            continue;
                        }
                        if (port.type == OSPort.INPUT_PORT) {
                            frame.setInputPortObj(port.name, port);
                            try {
                                frame.setInputPortPower(port.name, port.power);
                                frame.setInputPortState(port.name, "GOOD", port.power + " dBm", 2);
                            } catch (Exception ex) {
                            }
                        } else {
                            frame.setOutputPortsObj(port.name, port);
                            try {
                                frame.setOutputPortPower(port.name, port.power);
                                frame.setOutputPortState(port.name, "GOOD", port.power + " dBm");
                            } catch (Exception ex) {
                            }
                        }
                    }
                }
                frame.update();
            }
        }
    }

    public void setPortList(ArrayList<String> portList) {

        synchronized (opticalSwInfos) {

            if (frame == null) {
                return;
            }

            HashSet<String> set = new HashSet<String>();

            for (String portName : portList) {
                set.add(portName);
                if (!alreadyAddedPorts.contains("in_" + portName)) {
                    alreadyAddedPorts.add("in_" + portName);
                    frame.addPort(portName, PortsPanel.unconnected, null);
                }
                if (!alreadyAddedPorts.contains("out_" + portName)) {
                    alreadyAddedPorts.add("out_" + portName);
                    frame.addPort(portName, PortsPanel.unconnected, null);
                }
            }
            for (int i = 0; i < alreadyAddedPorts.size(); i++) {
                String p = alreadyAddedPorts.get(i);
                String portName = null;
                if (p.startsWith("in_")) {
                    portName = p.substring(3);
                } else {
                    portName = p.substring(4);
                }
                if (!portList.contains(portName)) { // must delete port
                    frame.delPort(p);
                }
            }
            frame.update();
        }
    }

    private void processResult(Result res) {

        if ((frame == null) || (info == null) || (res == null) || (res.param_name == null) || (res.param == null)) {
            return;
        }

        synchronized (opticalSwInfos) {
            for (int i = 0; i < res.param_name.length; i++) {

                if (res.param_name[i].equals("Port-Power")) {
                    String portName = res.NodeName;
                    if (info.type.byteValue() == OpticalSwitchInfo.CALIENT) {
                        if (portName.startsWith("In_")) {
                            portName = portName.substring(3);
                            if (!alreadyAddedPorts.contains("in_" + portName)) {
                                alreadyAddedPorts.add("in_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setInputPortPower(portName, res.param[i]);
                            frame.setInputPortState(portName, "GOOD", res.param[i] + " dBm", 2);
                        } else if (portName.startsWith("Out_")) {
                            portName = portName.substring(4);
                            if (!alreadyAddedPorts.contains("out_" + portName)) {
                                alreadyAddedPorts.add("out_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setOutputPortPower(portName, res.param[i]);
                            frame.setOutputPortState(portName, "GOOD", res.param[i] + " dBm");
                        } else if (portName.endsWith("_In")) {
                            portName = portName.substring(0, portName.length() - 3);
                            if (!alreadyAddedPorts.contains("in_" + portName)) {
                                alreadyAddedPorts.add("in_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setInputPortPower(portName, res.param[i]);
                            frame.setInputPortState(portName, "GOOD", res.param[i] + " dBm", 2);
                        } else if (portName.endsWith("_Out")) {
                            portName = portName.substring(0, portName.length() - 4);
                            if (!alreadyAddedPorts.contains("out_" + portName)) {
                                alreadyAddedPorts.add("out_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setOutputPortPower(portName, res.param[i]);
                            frame.setOutputPortState(portName, "GOOD", res.param[i] + " dBm");
                        }
                    } else if (info.type.byteValue() == OpticalSwitchInfo.GLIMMERGLASS) {
                        if (portName.endsWith("_In")) {
                            portName = portName.substring(0, portName.length() - 3);
                            if (!alreadyAddedPorts.contains("in_" + portName)) {
                                alreadyAddedPorts.add("in_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setInputPortPower(portName, res.param[i]);
                            frame.setInputPortState(portName, "GOOD", res.param[i] + " dBm", 2);
                        } else if (portName.endsWith("_Out")) {
                            portName = portName.substring(0, portName.length() - 4);
                            if (!alreadyAddedPorts.contains("out_" + portName)) {
                                alreadyAddedPorts.add("out_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setOutputPortPower(portName, res.param[i]);
                            frame.setOutputPortState(portName, "GOOD", res.param[i] + " dBm");
                        } else if (portName.startsWith("In_")) {
                            portName = portName.substring(3);
                            if (!alreadyAddedPorts.contains("in_" + portName)) {
                                alreadyAddedPorts.add("in_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setInputPortPower(portName, res.param[i]);
                            frame.setInputPortState(portName, "GOOD", res.param[i] + " dBm", 2);
                        } else if (portName.startsWith("Out_")) {
                            portName = portName.substring(4);
                            if (!alreadyAddedPorts.contains("out_" + portName)) {
                                alreadyAddedPorts.add("out_" + portName);
                                frame.addPort(portName, PortsPanel.unconnected, null);
                            }
                            frame.setOutputPortPower(portName, res.param[i]);
                            frame.setOutputPortState(portName, "GOOD", res.param[i] + " dBm");
                        }
                    }
                }
            }
            frame.update();
        }
    }

    private void processResult(eResult res) {

        if ((res.param == null) || (res.param.length == 0)) {
            return;
        }
        if (res.ClusterName.startsWith("OS_GMPLS")) {
            frame.gmplsAdmin.processeResult(res);
            return;
        }
    }

    public void stopIt() {
        frame.stopIt();
        frame.dispose();
        remoteCtrl = null;
    }

    /** see monOSGMPLS for details */
    public void registerForGMPLS() {

        monPredicate p = new monPredicate(((tClient) provider).getFarmName(), "OS_GMPLS*", "*", -60000, -1, null, null);
        provider.addLocalClient(this, p);
    }

    public void createPath(boolean allPathMode, boolean singleEPMode, boolean multipleEPMode, Vector path, boolean fdx) {

        if (singleEPMode) {
            if ((path == null) || (path.size() < 2)) {
                return;
            }
            rcNode src = (rcNode) path.get(0);
            String srcName = src.szOpticalSwitch_Name == null ? src.shortName : src.szOpticalSwitch_Name;
            rcNode dst = (rcNode) path.get(1);
            String dstName = dst.szOpticalSwitch_Name == null ? dst.shortName : dst.szOpticalSwitch_Name;
            try {
                String ret = remoteCtrl.makeMLPathConn(srcName, dstName, fdx);
                if ((ret == null) || !ret.equals("OK")) {
                    logger.warning("Got " + ret + " when trying to create path");
                } else {
                    logger.info("Path created");
                }
            } catch (Exception ex) {
                logger.warning("When trying to make ml path got exception " + ex);
            }
            return;
        }
        if (allPathMode) {
            logger.warning("Command not implemented yet");
            return;
        }
        if (multipleEPMode) {
            logger.warning("Command not implemented yet");
            return;
        }
    }

    public boolean deletePath(String id) {

        try {
            String ret = remoteCtrl.deleteMLPathConn(id);
            if ((ret != null) && ret.equals("OK")) {
                return true;
                //			System.out.println("deletePath returned code "+ret);
            }
        } catch (Exception ex) {
            logger.warning("When trying to delete ml path got exception " + ex);
        }
        return false;
    }

} // end of class OSMonitorControl

