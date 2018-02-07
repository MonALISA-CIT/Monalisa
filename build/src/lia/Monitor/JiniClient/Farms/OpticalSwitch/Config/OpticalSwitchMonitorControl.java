package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

import java.rmi.Naming;
import java.rmi.Remote;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.Admin.OSAdminInterface;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.tClient;
import lia.net.topology.GenericEntity;
import lia.net.topology.opticalswitch.OpticalSwitch;

public class OpticalSwitchMonitorControl implements PortAdminListener, LocalDataFarmClient {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OpticalSwitchMonitorControl.class.getName());

    public ConfigFrame frame;
    OSAdminInterface remoteCtrl;
    public GenericEntity info;
    private static Hashtable opticalSwInfos = new Hashtable();
    private final Vector alreadyAddedPorts;

    LocalDataFarmProvider provider;

    private final String address;
    private final int remoteRegistryPort;

    private OpticalSwitchMonitorControl(LocalDataFarmProvider provider, String address, int remoteRegistryPort)
            throws Exception {

        this.address = address;
        this.remoteRegistryPort = remoteRegistryPort;
        remoteCtrl = null;
        alreadyAddedPorts = new Vector();
        Remote r = Naming.lookup("rmi://" + address + ":" + remoteRegistryPort + "/OS_Admin");
        remoteCtrl = (OSAdminInterface) r;
        this.provider = provider;
    }

    public static OpticalSwitchMonitorControl connectTo(LocalDataFarmProvider provider, String address,
            int remoteRegistryPort) {

        String keyStore = System.getProperty("lia.Monitor.KeyStore", null);
        if ((keyStore == null) || (keyStore.length() == 0)) {
            return null;
        }
        try {
            OpticalSwitchMonitorControl control = new OpticalSwitchMonitorControl(provider, address, remoteRegistryPort);
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
            //			if (frame.gmplsAdmin != null)
            //				frame.gmplsAdmin.getWindow().setVisible(false);
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

    public static void setOpticalSwitchInfo(String device, GenericEntity info) {
        opticalSwInfos.put(device, info);
    }

    public void setOpticalSwitchInfo(GenericEntity ge) {

        if (frame == null) {
            return;
        }

        synchronized (opticalSwInfos) {
            this.info = ge;
            if ((info != null) && (frame != null)) {
                StringBuilder buf = new StringBuilder();
                buf.append(info.name());

                frame.setDeviceParams(buf.toString(), "", ""); // TODO check how to get all the ports

                Hashtable h = frame.getCurrentConns();
                Vector v = new Vector();
                for (Enumeration en = h.keys(); en.hasMoreElements();) {
                    String p = (String) en.nextElement();
                    if (p.startsWith("in_")) {
                        v.add(p.substring(3));
                    }
                }

                if (ge instanceof OpticalSwitch) {
                    OpticalSwitch os = (OpticalSwitch) ge;
                    frame.update(os);
                }
            }
        }
    }

    //	public void setPortList(ArrayList portList) {
    //		
    //		synchronized (opticalSwInfos) {
    //			
    //			if (frame == null) return;
    //			
    //			HashSet set = new HashSet();
    //			
    //			for (Iterator it = portList.iterator(); it.hasNext(); ) {
    //				String portName = (String)it.next();
    //				set.add(portName);
    //				if (!alreadyAddedPorts.contains("in_"+portName)) {
    //					alreadyAddedPorts.add("in_"+portName);
    //					frame.addPort(portName, PortsPanel.unconnected, null);
    //				}
    //				if (!alreadyAddedPorts.contains("out_"+portName)) {
    //					alreadyAddedPorts.add("out_"+portName);
    //					frame.addPort(portName, PortsPanel.unconnected, null);
    //				}
    //			}
    //			for (int i=0; i<alreadyAddedPorts.size(); i++) {
    //				String p = (String)alreadyAddedPorts.get(i);
    //				String portName = null;
    //				if (p.startsWith("in_")) portName = p.substring(3);
    //				else portName = p.substring(4);
    //				if (!portList.contains(portName)) { // must delete port
    //					frame.delPort(p);
    //				}
    //			}
    //			frame.update();
    //		}
    //	}

    private void processResult(Result res) {

        if ((frame == null) || (info == null) || (res == null) || (res.param_name == null) || (res.param == null)) {
            return;
        }

        //		synchronized (opticalSwInfos) {
        //			for (int i=0; i<res.param_name.length; i++) {
        //				if (res.param_name[i].equals("Port-Power")) {
        //					String portName = res.NodeName;
        //					if (info.type.byteValue() == OpticalSwitchInfo.CALIENT) {
        //						if (portName.startsWith("In_")) {
        //							portName = portName.substring(3);
        //							if (!alreadyAddedPorts.contains("in_"+portName)) {
        //								alreadyAddedPorts.add("in_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setInputPortPower(portName, res.param[i]);
        //							frame.setInputPortState(portName, "GOOD", res.param[i]+" dBm", 2);
        //						} else if (portName.startsWith("Out_")) {
        //							portName = portName.substring(4);
        //							if (!alreadyAddedPorts.contains("out_"+portName)) {
        //								alreadyAddedPorts.add("out_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setOutputPortPower(portName, res.param[i]);
        //							frame.setOutputPortState(portName, "GOOD", res.param[i]+" dBm");
        //						} else if (portName.endsWith("_In")) {
        //							portName = portName.substring(0, portName.length() - 3);
        //							if (!alreadyAddedPorts.contains("in_"+portName)) {
        //								alreadyAddedPorts.add("in_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setInputPortPower(portName, res.param[i]);
        //							frame.setInputPortState(portName, "GOOD", res.param[i]+" dBm", 2);
        //						} else if (portName.endsWith("_Out")) {
        //							portName = portName.substring(0, portName.length() - 4);
        //							if (!alreadyAddedPorts.contains("out_"+portName)) {
        //								alreadyAddedPorts.add("out_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setOutputPortPower(portName, res.param[i]);
        //							frame.setOutputPortState(portName, "GOOD", res.param[i]+" dBm");
        //						}
        //					} else if (info.type.byteValue() == OpticalSwitchInfo.GLIMMERGLASS) {
        //						if (portName.endsWith("_In")) {
        //							portName = portName.substring(0, portName.length() - 3);
        //							if (!alreadyAddedPorts.contains("in_"+portName)) {
        //								alreadyAddedPorts.add("in_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setInputPortPower(portName, res.param[i]);
        //							frame.setInputPortState(portName, "GOOD", res.param[i]+" dBm", 2);
        //						} else if (portName.endsWith("_Out")){
        //							portName = portName.substring(0, portName.length() - 4);
        //							if (!alreadyAddedPorts.contains("out_"+portName)) {
        //								alreadyAddedPorts.add("out_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setOutputPortPower(portName, res.param[i]);
        //							frame.setOutputPortState(portName, "GOOD", res.param[i]+" dBm");
        //						} else if (portName.startsWith("In_")) {
        //							portName = portName.substring(3);
        //							if (!alreadyAddedPorts.contains("in_"+portName)) {
        //								alreadyAddedPorts.add("in_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setInputPortPower(portName, res.param[i]);
        //							frame.setInputPortState(portName, "GOOD", res.param[i]+" dBm", 2);
        //						} else if (portName.startsWith("Out_")) {
        //							portName = portName.substring(4);
        //							if (!alreadyAddedPorts.contains("out_"+portName)) {
        //								alreadyAddedPorts.add("out_"+portName);
        //								frame.addPort(portName, PortsPanel.unconnected, null);
        //							}
        //							frame.setOutputPortPower(portName, res.param[i]);
        //							frame.setOutputPortState(portName, "GOOD", res.param[i]+" dBm");
        //						}
        //					}
        //				}
        //			}
        //			frame.update();
        //		}
    }

    private void processResult(eResult res) {

        if ((res.param == null) || (res.param.length == 0)) {
            return;
        }
        if (res.ClusterName.startsWith("OS_GMPLS")) {
            //			frame.gmplsAdmin.processeResult(res);
            return;
        }
    }

    public void stopIt() {
        //		frame.stopIt();
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
            }
        } catch (Exception ex) {
            logger.warning("When trying to delete ml path got exception " + ex);
        }
        return false;
    }

} // end of class OSMonitorControl

