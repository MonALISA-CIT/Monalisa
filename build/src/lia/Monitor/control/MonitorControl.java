package lia.Monitor.control;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.Remote;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonitorUnit;

/**
 * Monitor Control is the main class which in turn contains references to all other panels
 */
public class MonitorControl extends JFrame implements ControlI {
    /**
     * 
     */
    private static final long serialVersionUID = -4344621888242727337L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MonitorControl.class.getName());

    //    private static boolean debug = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false")).booleanValue();
    // reference to FarmMonitor
    // for the time being it is only one but it may be a list
    MonitorUnit monitor = null;
    private static boolean isVRVSFarm = false;

    HashMap monitors = new HashMap();

    public static Hashtable mc = new Hashtable();

    // a reference to own self , can be used by inner classes
    MonitorControl control;
    // used to hold the config and control panel

    private JPanel centerPanel;

    private TreePanel treePanel;
    private ConfigPanel configPanel;
    private ControlPanel controlPanel;
    private MessagePanel messagePanel;
    private JPopupMenu treePopup, farmPopup, serviceAdminMenu;
    private JMenuItem addModule, addElement, removeElement, expand, showLog, stopServiceMenuItem,
            restartServiceMenuItem;
    private final String addModuleString = " Add Module ";

    // these strings get their values as per the selection in the tree

    private String addElementString = "  ";
    private String removeElementString = "  ";
    private final String expandString = "  ";

    private final String showLogString = " Show Log ";

    private JTree tree;

    private AddDialog addDialog;
    private InputDialog inputDialog;

    // selected object instance is used to track the
    // object which is selected in the tree
    private Object selectedObject;

    public MonitorControl() {
        // set security manager
        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }
        control = this;

    }

    public void init() {
        getContentPane().setLayout(new BorderLayout());
        messagePanel = new MessagePanel();
        treePanel = new TreePanel("Farm", this);

        configPanel = new ConfigPanel(this);
        controlPanel = new ControlPanel(this);

        addDialog = new AddDialog(this);

        // panel for layout
        centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout());
        centerPanel.add(configPanel, BorderLayout.CENTER);
        centerPanel.add(controlPanel, BorderLayout.SOUTH);

        getContentPane().add(messagePanel, BorderLayout.SOUTH);
        getContentPane().add(treePanel, BorderLayout.WEST);

        getContentPane().add(centerPanel, BorderLayout.CENTER);

        tree = treePanel.getTree();
        tree.addMouseListener(new TreeListener());

        makePopupMenu();

        setTitle("Monitor");
        pack();
        //        setSize(640,480);
    }

    public final void showWindow() {

        setVisible(true);
        //setResizable(false);
    }

    // implementing methods from ControlI interface
    @Override
    public void addFarm(MFarm farm) {
        treePanel.addFarm(farm);

        configPanel.clearDisplay();
    }

    @Override
    public void updateConfig(MFarm farm, String name) {
        treePanel.updateFarm(farm, false);
        //updateMonitorList(farm , false);
        configPanel.clearDisplay();

    }

    @Override
    public void removeFarm(MFarm farm) {
        treePanel.updateFarm(farm, true);
        updateMonitorList(farm, true);
        configPanel.clearDisplay();
    }

    public void updateMonitorList(MFarm farm, boolean remove) {
        //MFarm f =(MFarm)monitors.get(farm.toString());
        //if(f != null){
        Object flag = monitors.remove(farm.toString());
        //	if(!remove)
        //	monitors.put(farm.toString() , farm);
        //}
        //else
    }

    // this method is called by tree panel to indicate a change in the
    // tree selection and correcpondingly showing the new values for the
    // selected object
    public void setModuleValues(Vector modules) {
        controlPanel.disableControls();
        configPanel.addModules(modules);
    }

    public void setParameterValues(Vector parameters) {
        controlPanel.disableControls();
        configPanel.addParameters(parameters);
    }

    // used to clear the selection in the configPanel lists
    public void clear() {
        controlPanel.disableControls();
        configPanel.clear();
    }

    // calling methods to enable and disable the
    // buttons in the control panel
    // which are controlled by the list selections in the
    // config panel
    public void disableControls() {
        controlPanel.disableControls();
    }

    public void enableModuleControls() {
        controlPanel.enableModuleControls();
    }

    public void enableParameterControls() {
        controlPanel.enableParameterControls();
    }

    // this method is used to get reference to the Farm Monitor we want to talk to

    public MonitorUnit getMonitorUnit(Object object) {
        String name = null;
        if (object instanceof MFarm) {
            name = object.toString();
        } else if (object instanceof MCluster) {
            name = ((MCluster) object).getFarm().toString();
        } else if (object instanceof MNode) {
            name = ((MNode) object).getFarm().toString();
        }
        if (name == null) {
            return null;
        }

        MonitorUnit mon = (MonitorUnit) monitors.get(name);
        if (mon == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Not able to find the Monitoring unit");
            }
        }
        return mon;
    }

    // methods called by the Control panel

    public void stopModule() {

        String result = "";
        String command = "";
        String module = configPanel.getSelectedModule();
        selectedObject = treePanel.getSelectedComponent();
        MonitorUnit monitor = getMonitorUnit(selectedObject);
        if (monitor == null) {
            return;
        }

        if (selectedObject instanceof MNode) {
            //JOptionPane.showMessageDialog(this ,"stop module "+module+"  on  "+selectedObject.toString());
            MNode node = (MNode) selectedObject;

            try {
                messagePanel.addMessage(" Removing Module " + module + "  From Node " + node.toString(), false);
                result = monitor.ConfigRemove(node.getCluster().toString(), node.toString(), module);
            } catch (Exception ee) {
            }
        } else if (selectedObject instanceof MCluster) {
            JOptionPane.showMessageDialog(this, "stop module only works for Nodes");
        } else if (selectedObject instanceof MFarm) {
            JOptionPane.showMessageDialog(this, "stop module only works for nodes");
        }
        messagePanel.addMessage(result, isErrorMessage(result));
    }

    public void updateReflector() {
        String messagePanelString;
        if (monitor == null) {
            messagePanel.addMessage("monitor == null Please send an Email with this error at: ramiro@roedu.net", true);
        }

        try {
            messagePanelString = monitor.updateReflector();
            messagePanel.addMessage(messagePanelString, isErrorMessage(messagePanelString));
            return;
        } catch (Throwable t) {
            messagePanel.addMessage("Cannot UPDATE REFLECTOR. Cause: " + t.getMessage(), true);
            return;
        }

    }

    /**
     * This is stupid ... but we have to use the same interface ... that's the problem (ask Ramiro)
     */
    public void sendCMDToReflector(String cmd) {
        String messagePanelString;

        if (monitor == null) {
            messagePanel.addMessage("monitor == null Please send an Email with this error at: Ramiro.Voicu@cern.ch",
                    true);
            return;
        }

        try {
            messagePanelString = monitor.ConfigRemove("VRVS_REMOVE_TOKEN_MDA", cmd, "VRVS_REMOVE_TOKEN_MDA");
            messagePanel.addMessage(messagePanelString, isErrorMessage(messagePanelString));
            return;
        } catch (Throwable t) {
            messagePanel.addMessage("Cannot perform [ " + cmd + " ] on REFLECTOR. Cause: " + t.getMessage(), true);
            return;
        }

    }

    public void changeTime(int time) {
        //stem.out.println("inside control panel chenge time ");

        String result = "";
        String moduleName = configPanel.getSelectedModule();
        selectedObject = treePanel.getSelectedComponent();
        MonitorUnit monitor = getMonitorUnit(selectedObject);
        if (monitor == null) {
            return;
        }
        if (selectedObject instanceof MNode) {
            //JOptionPane.showMessageDialog(this ,"change  "+moduleName+"  on  "+selectedObject.toString()+" to "+time);
            MNode node = (MNode) selectedObject;

            try {
                //messagePanel.addMessage(" Removing Node  "+node.toString()+"  From Cluster "+node.getCluster().toString());
                messagePanel.addMessage("Changing time for Module " + moduleName + " for Node " + node.toString(),
                        false);
                result = monitor.ConfigAdd(node.getCluster().toString(), node.toString(), moduleName, time);
            } catch (Exception e) {
            }
        } else if (selectedObject instanceof MCluster) {
            //JOptionPane.showMessageDialog(this ,"change  "+moduleName+"  on  "+selectedObject.toString()+" to "+time);
            MCluster c = (MCluster) selectedObject;

            try {
                messagePanel.addMessage(
                        "Changing time for Module " + moduleName + " for Cluster " + selectedObject.toString(), false);
                result = monitor.ConfigAdd(((MCluster) selectedObject).toString(), "*", moduleName, time);
            } catch (Exception e) {
            }
        } else if (selectedObject instanceof MFarm) {

            messagePanel.addMessage("Changing time for Module " + moduleName + " for Farm", false);
            //ptionPane.showMessageDialog(this ,"change  "+moduleName+"  on  "+selectedObject.toString()+" to "+time);
            try {
                result = monitor.ConfigAdd("*", "*", moduleName, time);
            } catch (Exception e) {
            }
        }
        messagePanel.addMessage(result, isErrorMessage(result));

    }

    // method to remove elements

    public void removeElement(Object object) {

        MonitorUnit monitor = getMonitorUnit(object);
        if (monitor == null) {
            return;
        }
        String result = "";
        if (object instanceof MNode) {
            //,"Deleting  element  ::: "+selectedObject.toString());
            MNode node = (MNode) object;

            try {
                messagePanel.addMessage(" Removing Node " + node.toString() + "  From Cluster "
                        + node.getCluster().toString(), false);
                result = monitor.ConfigRemove(node.getCluster().toString(), node.toString(), null);
            } catch (Exception ee) {
            }
        } else if (object instanceof MCluster) {
            //JOptionPane.showMessageDialog(this ,"Deleting  element  ::: "+selectedObject.toString());
            MCluster cluster = (MCluster) object;

            try {
                messagePanel.addMessage(" Removing Cluster  " + cluster.toString(), false);
                result = monitor.ConfigRemove(cluster.toString(), null, null);
            } catch (Exception ee) {
            }
        } else if (object instanceof MFarm) {
            //JOptionPane.showMessageDialog(this ,"Deleting  element  ::: "+selectedObject.toString());
            MFarm farm = (MFarm) object;

            try {
                messagePanel.addMessage(" Removing Farm ", false);
                result = monitor.ConfigRemove(null, null, null);
                //monitor =null;
            } catch (Exception ee) {
            }
        }
        messagePanel.addMessage(result, isErrorMessage(result));

    }

    // method to add elements
    public void addElement(String name) {

        selectedObject = treePanel.getSelectedComponent();
        MonitorUnit monitor = getMonitorUnit(selectedObject);
        if (monitor == null) {
            return;
        }

        String result = "";
        //if(selectedObject instanceof MNode){
        //	JOptionPane.showMessageDialog(this ,"change  "+moduleName+"  on  "+selectedObject.toString()+" to "+time);
        //}
        if (selectedObject instanceof MCluster) {
            //	JOptionPane.showMessageDialog(this ,"adding   "+name+"  to  "+selectedObject.toString());
            MCluster c = (MCluster) selectedObject;

            try {
                messagePanel.addMessage(" Adding Node " + name + " to Cluster " + selectedObject.toString(), false);
                result = monitor.ConfigAdd(((MCluster) selectedObject).toString(), name, null, 0);
            } catch (Exception e) {
            }

        } else if (selectedObject instanceof MFarm) {

            //JOptionPane.showMessageDialog(this ,"adding   "+name+"  to  "+selectedObject.toString());
            try {
                messagePanel.addMessage(" Adding Cluster " + name + " to Farm " + selectedObject.toString(), false);
                result = monitor.ConfigAdd(name, null, null, 0);
            } catch (Throwable t) {
                //                e.printStackTrace();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Got Exception", t);
                }
            }
        }
        messagePanel.addMessage(result, isErrorMessage(result));

    }

    // method to start a monitoring module
    public void addModule(String name) {

        Object object = treePanel.getSelectedComponent();
        MonitorUnit monitor = getMonitorUnit(object);
        if (monitor == null) {
            return;
        }
        String result = "";
        if (object instanceof MNode) {

            MNode node = (MNode) object;

            try {
                messagePanel.addMessage(" Adding Module " + name + " to  Node " + node.toString(), false);
                result = monitor.ConfigAdd(node.getCluster().toString(), node.toString(), name, 0);
            } catch (Exception ee) {
            }
        } else if (object instanceof MCluster) {
            MCluster cluster = (MCluster) object;

            try {
                messagePanel.addMessage(" Adding Module " + name + " to  Cluster " + cluster.toString(), false);
                result = monitor.ConfigAdd(cluster.toString(), "*", name, 0);
            } catch (Exception ee) {
            }
        } else if (object instanceof MFarm) {
            MFarm farm = (MFarm) object;

            try {
                messagePanel.addMessage(" Adding Module " + name + " to  Farm ", false);
                result = monitor.ConfigAdd("*", null, name, 0);
            } catch (Exception ee) {
            }

        }
        messagePanel.addMessage(result, isErrorMessage(result));
    }

    public boolean isErrorMessage(String msg) {
        if (msg.toLowerCase().indexOf("error") >= 0) {
            return true;
        }
        return false;
    }

    public void makeServiceAdminMenu() {
        serviceAdminMenu = new JPopupMenu();
        serviceAdminMenu.setBackground(Color.white);

        stopServiceMenuItem = new JMenuItem("Stop Service");
        stopServiceMenuItem.setBackground(Color.white);
        stopServiceMenuItem.setForeground(new Color(0x00, 0x66, 0x99));
        stopServiceMenuItem.setFont(new Font("Tahoma", Font.BOLD, 11));
        stopServiceMenuItem.addActionListener(new PopupListener());

        restartServiceMenuItem = new JMenuItem("Restart Service");
        restartServiceMenuItem.setBackground(Color.white);
        restartServiceMenuItem.setForeground(new Color(0x00, 0x66, 0x99));
        restartServiceMenuItem.setFont(new Font("Tahoma", Font.BOLD, 11));
        restartServiceMenuItem.addActionListener(new PopupListener());

        serviceAdminMenu.addSeparator();
        serviceAdminMenu.add(stopServiceMenuItem);
        serviceAdminMenu.addSeparator();
        serviceAdminMenu.add(restartServiceMenuItem);

        tree.add(serviceAdminMenu);
    }

    public void makePopupMenu() {
        treePopup = new JPopupMenu("Select Function");
        treePopup.setBackground(Color.white);
        //treePopup.addMouseListener(new PopupListener());

        addModule = new JMenuItem(addModuleString);
        addModule.setBackground(Color.white);
        addModule.setForeground(new Color(0x00, 0x66, 0x99));
        addModule.setFont(new Font("Tahoma", Font.BOLD, 11));
        addModule.addActionListener(new PopupListener());

        addElement = new JMenuItem(addElementString);
        addElement.setBackground(Color.white);
        addElement.setForeground(new Color(0x00, 0x66, 0x99));
        addElement.setFont(new Font("Tahoma", Font.BOLD, 11));
        addElement.addActionListener(new PopupListener());

        removeElement = new JMenuItem(removeElementString);
        removeElement.setBackground(Color.white);
        removeElement.setForeground(new Color(0x00, 0x66, 0x99));
        removeElement.setFont(new Font("Tahoma", Font.BOLD, 11));
        removeElement.addActionListener(new PopupListener());

        expand = new JMenuItem(expandString);

        showLog = new JMenuItem(showLogString);
        showLog.setBackground(Color.white);
        showLog.setForeground(new Color(0x00, 0x66, 0x99));
        showLog.setFont(new Font("Tahoma", Font.BOLD, 11));
        showLog.addActionListener(new PopupListener());

        //treePopup.addSeparator();
        //treePopup.add(expand);
        treePopup.addSeparator();
        treePopup.add(addModule);
        treePopup.addSeparator();
        treePopup.add(addElement);
        treePopup.addSeparator();
        treePopup.add(removeElement);
        treePopup.addSeparator();
        treePopup.add(showLog);
        treePopup.addSeparator();

        tree.add(treePopup);
    }

    public boolean initPopupDisplay() {
        Object obj = treePanel.getSelectedComponent();
        addElement.setEnabled(true);

        // to see if nothing was selected in the tree

        if (obj == null) {
            // if we are at the root of the tree
            return false;
        } else if (obj instanceof MFarm) {
            addElementString = " Add Cluster   ";
            removeElementString = " Remove Farm   ";
            makePopupMenu();
            //treePopup.revalidate();
            return true;
        } else if (obj instanceof MCluster) {
            addElementString = " Add Node   ";
            removeElementString = " Remove Cluster ";
            makePopupMenu();
            //			if(((((MCluster)obj).getNodes()).size()) ==0)
            //				addModule.setEnabled(false);

            //treePopup.revalidate();
            return true;
        } else if (obj instanceof MNode) {
            addElementString = " Add Node ";

            removeElementString = " Remove Node ";
            makePopupMenu();
            //treePopup.revalidate();
            addElement.setEnabled(false);
            return true;
        }

        return false;
    }

    public boolean isVRVSFarm() {
        return isVRVSFarm;
    }

    public static final MonitorControl connectTo(String host, int port) {

        String key = host + ":" + port;
        if (mc.containsKey(key)) {
            return (MonitorControl) mc.get(key);
        }

        try {
            MonitorControl control = new MonitorControl();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ Admin/SSL ] Looking up : " + host + ":" + port);
            }
            try {
                Remote r = Naming.lookup("rmi://" + host + ":" + port + "/Farm_Monitor");
                control.monitor = (MonitorUnit) r;
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[ Admin/SSL ] Looking up : " + host + ":" + port + " OK!!!");
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[ Admin/SSL ] Looking up : " + host + ":" + port + " FAILED!!!", t);
                }
                return null;
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "initializing");
            }
            MFarm f = control.monitor.init(/*control*/);

            if ((control != null) && (control.monitor != null)) {
                isVRVSFarm = control.monitor.isVRVSFarm();
                if (logger.isLoggable(Level.FINE) && isVRVSFarm) {
                    logger.log(Level.FINER, " isVRVSFarm " + isVRVSFarm);
                }
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINER, " monitor || control null ");
                }
            }

            if (control != null) {
                control.init();
            }

            if (f != null) {
                control.addFarm(f);
            }

            mc.put(key, control);
            try {
                control.monitors.put(control.monitor.getUnitName(), control.monitor);
            } catch (Exception ex) {
            }
            //        JOptionPane.showMessageDialog(null, "Welcome!  SSL enabled! ", "Success", JOptionPane.INFORMATION_MESSAGE);

            return control;
        } catch (Throwable e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Unable to get The Admin privileges ( RMI over SSL FAILED !) \n", e);
            }
            if ((AppConfig.getProperty("lia.Monitor.KeyStore") != null)
                    && (AppConfig.getProperty("lia.Monitor.KeyStore").length() > 0)
                    && (AppConfig.getProperty("lia.Monitor.KeyStorePass") != null)
                    && (AppConfig.getProperty("lia.Monitor.KeyStorePass").length() > 0)) {
                //             JOptionPane.showMessageDialog(null, "Unable to Administer ( RMI over SSL )\n Cause: " +e.getCause(), "Error", JOptionPane.ERROR_MESSAGE);
                //            e.printStackTrace() ;
            }
            //	     if ( debug )   {
            //             e.printStackTrace();
            //         }
            return null;
        }
    }

    private static final void sleep() {
        try {
            Thread.sleep(1000 * 3);
        } catch (Exception e) {
        }
    }

    public void showSpecialPopup() {

        farmPopup = new JPopupMenu("Select Function");
        farmPopup.setBackground(Color.white);
        //treePopup.addMouseListener(new PopupListener());

        JMenuItem addModule = new JMenuItem("Add Farm Monitor");
        addModule.setBackground(Color.white);
        addModule.setForeground(new Color(0x00, 0x66, 0x99));
        addModule.setFont(new Font("Tahoma", Font.BOLD, 11));
        addModule.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String host = JOptionPane.showInputDialog(control, " Please input host name for Farm Monitor",
                        "Subscribing to a Farm Monitor ", JOptionPane.QUESTION_MESSAGE);
                if (host != null) {
                    subscribe(host);
                }
            }

        });
        farmPopup.add(addModule);
        tree.add(farmPopup);
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "inside special pop " + farmPopup);
        }
    }

    public void subscribe(String host) {
        messagePanel.addMessage(" Subscribing to Farm Monitor at " + host, false);
        try {
            monitor = (MonitorUnit) Naming.lookup("rmi://" + host + "/Farm_Monitor");
            monitor.init(/*control*/);
            monitors.put(monitor.getUnitName(), monitor);
            messagePanel.addMessage(" >>>> Successful in subscribing to host " + host, false);
        } catch (Exception e) {
            JOptionPane.showConfirmDialog(control, " Unable to get Farm Monitor at  " + host, " Connect Failure ",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
            messagePanel.addMessage(" Error >>> Failed to subscribe to host " + host, true);
            return;
            //System.exit(0);
        }

    }

    private void restartService() {
        if (monitor != null) {
            try {
                monitor.restartML();
            } catch (Throwable t) {
                messagePanel.addMessage(" Restarting ML. GOT ERRORS " + t.getMessage(), false);
                return;
            }
            messagePanel.addMessage(" Restarting ML. Hope it worked!", false);
        }
    }

    private void stopService() {
        if (monitor != null) {
            try {
                monitor.stopML();
            } catch (Throwable t) {
                messagePanel.addMessage(" Stopping ML. GOT ERRORS " + t.getMessage(), false);
                return;
            }
            messagePanel.addMessage(" Stopping ML. Hope it worked!", false);
        }
    }

    //*******inner classes ********************

    // Inner class for listening to the tree events related to
    //popup menu

    class TreeListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent me) {
            int onmask = InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                    | InputEvent.BUTTON2_MASK;
            String modifText = InputEvent.getModifiersExText(me.getModifiersEx());

            //TODO - NOT TEXT ... BUT IT DOES NOT WORKED !!!
            if ((modifText != null) && (modifText.indexOf("Ctrl") != -1) && (modifText.indexOf("Alt") != -1)
                    && (modifText.indexOf("Shift") != -1)) {
                makeServiceAdminMenu();
                serviceAdminMenu.show(tree, me.getX() + 19, me.getY() + 3);
            } else if (me.getModifiers() == InputEvent.BUTTON3_MASK) {
                boolean show = initPopupDisplay();
                if (show) {
                    treePopup.show(tree, me.getX() + 19, me.getY() + 3);
                } else if (!show) {
                    showSpecialPopup();
                    farmPopup.show(tree, me.getX() + 19, me.getY() + 3);
                } else {
                    return;
                }

            }

        }

    }

    class PopupListener implements ActionListener {
        Object source = null;

        @Override
        public void actionPerformed(ActionEvent we) {
            source = we.getSource();

            //stop - restart Service
            if (source == stopServiceMenuItem) {
                stopService();
            } else if (source == restartServiceMenuItem) {
                restartService();
            } else if (source == addModule) {
                selectedObject = treePanel.getSelectedComponent();
                if (selectedObject instanceof MFarm) {
                    addDialog.setValues(((MFarm) selectedObject).getAvModules());
                } else if (selectedObject instanceof MCluster) {
                    addDialog.setValues(((MCluster) selectedObject).getFarm().getAvModules());
                } else if (selectedObject instanceof MNode) {
                    addDialog.setValues(((MNode) selectedObject).getFarm().getAvModules());
                } else {

                }
                addDialog.show();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINE, "addModule");
                }
            } else if (source == addElement) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINE, "add element");
                }
                selectedObject = treePanel.getSelectedComponent();
                if (selectedObject instanceof MFarm) {
                    inputDialog = new InputDialog(control, " Adding Cluster to " + selectedObject.toString(),
                            " Enter the name of Cluster ");
                    inputDialog.show();

                } else if (selectedObject instanceof MCluster) {
                    inputDialog = new InputDialog(control, " Adding Node to " + selectedObject.toString(),
                            " Enter the name of Node ");
                    inputDialog.show();

                } else if (selectedObject instanceof MNode) {

                } else {

                }
            } else if (source == removeElement) {
                selectedObject = treePanel.getSelectedComponent();
                int answer = JOptionPane.showConfirmDialog(control,
                        " Are you sure to delete  " + selectedObject.toString(), " Delete Confirmation ",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

                if (answer == JOptionPane.OK_OPTION) {
                    removeElement(selectedObject);
                } else if (answer == JOptionPane.CANCEL_OPTION) {
                    ;
                }

            } else if (source == showLog) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "show log");
                }
            }

        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.out.println(" MC Main function ");
            String host = "localhost";
            if (args.length > 0) {
                host = args[0];
            }

            System.setSecurityManager(new RMISecurityManager());

            JFrame justToBeOwner = new JFrame();
            justToBeOwner.setVisible(true);
            //                UserPasswordGather auth = new UserPasswordGather();
            //                auth.doAuth();
            if (System.getProperty("lia.Monitor.KeyStore", null) == null) {
                System.setProperty("lia.Monitor.KeyStore", "");
            }
            if (System.getProperty("lia.Monitor.KeyStorePass", null) == null) {
                System.setProperty("lia.Monitor.KeyStorePass", "");
            }

            MonitorControl control = connectTo(host, 9000);

            if (control != null) {
                control.showWindow();
            } else {
                System.exit(0);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}