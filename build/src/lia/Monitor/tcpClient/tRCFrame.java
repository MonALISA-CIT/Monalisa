package lia.Monitor.tcpClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import lia.Monitor.AppControlClient.Main;
import lia.Monitor.GUIs.RCMonPanel;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.control.MonitorControl;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.LocalDataFarmProvider;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.net.topology.opticalswitch.OpticalSwitch;

public class tRCFrame extends JFrame /* implements Runnable */{

    /**
     * 
     */
    private static final long serialVersionUID = 2299263458343263617L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(tRCFrame.class.getName());

    //    Hashtable config;
    LocalDataFarmProvider dataprovider;
    JLabel timeTF;
    JLabel mlVersion;
    JLabel unl;
    JProgressBar progress;
    volatile boolean done = false;
    RCMonPanel rcpanel;
    public int remoteRegistryPort = 1099;
    JPanel totul;
    JFrame me = null;
    String uptime = "N/A";
    String buildNr = "";

    //   SimpleDateFormat dateform = new SimpleDateFormat( "MM-dd-yy 'at' HH:mm" );
    SimpleDateFormat dateform = new SimpleDateFormat(" HH:mm");

    public MonitorControl control = null;
    Main appControl = null;

    private int ControlPort = -1;

    Object controlLock;
    Object appControlLock;

    JButton jbAdmin = null;
    JButton acAdmin = null;

    // new button for the optical switch administration
    JButton osAdmin = null;

    MonaLisaEntry mle = null;
    private SiteInfoEntry sie = null;
    private ExtendedSiteInfoEntry esie = null;
    String siteInfoStr = "Site Info\n";

    public InetAddress address;
    public SerMonitorBase serMonitorBase = null;

    //    public tRCFrame(String name, Hashtable config, LocalDataProvider dataprovider){
    //	   this(name, config, dataprovider, null, 1099, 9005, name, null, null, null);
    //    }

    public tRCFrame(String name, Hashtable config, final LocalDataFarmProvider dataprovider, InetAddress _address,
            int remoteRegistryPort, int ControlPort, String Title, MonaLisaEntry mle, SiteInfoEntry sie,
            ExtendedSiteInfoEntry esie) {
        super(Title);

        me = this;
        this.mle = mle;
        this.sie = sie;
        this.esie = esie;
        this.ControlPort = ControlPort;
        //        this.config = config;
        this.remoteRegistryPort = remoteRegistryPort;
        this.dataprovider = dataprovider;
        this.address = _address;
        //    Color backGr =new Color(205,226,247);
        Color backGr = new Color(166, 210, 255);

        controlLock = "controlLock_" + name;
        appControlLock = "appControlLock_" + name;

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                endIt();
            }
        });

        totul = new JPanel();
        //        totul.setBackground(backGr);
        totul.setLayout(new BorderLayout());

        JPanel panel1 = new JPanel();
        //        panel1.setBackground(backGr);
        panel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 0));
        JButton siteInfoButton = new JButton("Site info");
        siteInfoButton.setMaximumSize(new java.awt.Dimension(120, 20));
        siteInfoButton.setVisible(true);
        siteInfoButton.setBounds(380, 0, 120, 20);
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nIp Address: " + _address);
        sb.append("\nGroup: " + mle.Group);
        sb.append("\nLocation: " + mle.Location + ", Country: " + mle.Country + ", LAT: " + mle.LAT + ", LONG: "
                + mle.LONG);
        sb.append("\nContact: " + esie.localContactName + " email: " + esie.localContactEMail);
        sb.append("\nJVM: " + esie.JVM_VERSION + "\nLIBC: " + esie.LIBC_VERSION);
        sb.append("\n\n");
        siteInfoStr += sb.toString();

        siteInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int modifiers = e.getModifiers();
                if (((modifiers & InputEvent.CTRL_MASK) != 0) && ((modifiers & InputEvent.ALT_MASK) != 0)
                        && ((modifiers & InputEvent.SHIFT_MASK) != 0) && checkVersion()) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINE, "Sending PMS_ML_STATUS predicate");
                    }
                    MLSerClient client = (MLSerClient) dataprovider;
                    String ret = JOptionPane.showInputDialog(me, "Enter time for ml_status predicate");
                    int time = 5;
                    try {
                        time = Integer.parseInt(ret);
                        if (time <= 0) {
                            time = 5;
                        }
                    } catch (Exception ex) {
                        time = 5;
                    }
                    // TODO: why?!
                    //					monPredicate pred = new monPredicate(client.farm.getName(), "PMS_ML_STATUS", "PMS_ML_STATUS", time, time, null, null);
                    //					client.addLocalClient(new LocalDataFarmClient() {
                    //						public void newFarmResult(tClient client, Object res) {
                    //						}
                    //					}, pred);
                }
                // stand by while the lookup is done
                JOptionPane.showMessageDialog(me, siteInfoStr + (buildNr.length() > 0 ? "\nBuildID: " + buildNr : "")
                        + "\nUptime: " + uptime + "\n\n\n", getTitle(), JOptionPane.INFORMATION_MESSAGE);
            }
        });
        panel1.add(siteInfoButton);

        jbAdmin = new JButton("ML Admin");
        jbAdmin.setBackground(backGr);
        jbAdmin.setMaximumSize(new java.awt.Dimension(120, 20));
        jbAdmin.setVisible(false);
        jbAdmin.setBounds(380, 0, 120, 20);

        jbAdmin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // stand by while the lookup is done
                synchronized (controlLock) {
                    if (control != null) {
                        // show administrative window
                        control.showWindow();
                    }
                }
            }
        });

        panel1.add(jbAdmin);

        acAdmin = new JButton("App Control");
        acAdmin.setBackground(backGr);
        acAdmin.setMaximumSize(new java.awt.Dimension(120, 20));
        acAdmin.setVisible(false);
        acAdmin.setBounds(380, 0, 120, 20);

        acAdmin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // stand by while the lookup is done
                synchronized (appControlLock) {
                    if (appControl != null) {
                        // show administrative window
                        appControl.showWindow();
                    }
                }
            }
        });

        panel1.add(acAdmin);

        osAdmin = new JButton("OS Admin");
        osAdmin.setBackground(backGr);
        osAdmin.setMaximumSize(new java.awt.Dimension(120, 20));
        osAdmin.setVisible(false);
        osAdmin.setBounds(380, 0, 120, 20);

        osAdmin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // stand by while the lookup is done
                tClient client = (tClient) dataprovider;
                if (client == null) {
                    return;
                }
                synchronized (client) {
                    if (client.opticalSwitchControl != null) {
                        if ((client.opticalSwitchInfo != null) && (client.opticalSwitchInfo instanceof OpticalSwitch)
                                && (client.opticalSwitchControl.frame != null)) {
                            client.opticalSwitchControl.frame.update((OpticalSwitch) client.opticalSwitchInfo);
                        }
                        client.opticalSwitchControl.showWindow();
                    } else if (client.osControl != null) {
                        // show administrative window
                        client.osControl.showWindow();
                    }
                }
            }
        });

        panel1.add(osAdmin);

        totul.add(panel1, BorderLayout.SOUTH);

        JPanel panel2 = new JPanel();
        panel2.setLayout(new BoxLayout(panel2, BoxLayout.X_AXIS));
        timeTF = new JLabel("Local Time :          "); //"unknown" );
        panel2.add(timeTF);
        panel2.add(Box.createHorizontalStrut(10));
        mlVersion = new JLabel("MonALISA Version:          "); //"unknown" );
        panel2.add(mlVersion);

        totul.add(panel2, BorderLayout.NORTH);

        rcpanel = new RCMonPanel(dataprovider, "RC", config, this);
        rcpanel.setFarmName(mle.Name);
        rcpanel.setCountryCode(mle.Country);

        totul.add("Center", rcpanel);
        totul.setPreferredSize(new Dimension(500, 400));

        getContentPane().add(totul);

        pack();
        setVisible(false);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent evt) {
                (new AdminTestThread()).start();
                (new AppControlThread()).start();
                tClient client = (tClient) dataprovider;
                if (client != null) {
                    if (client.osControl != null) {
                        osAdmin.setVisible(true);
                    } else {
                        client.redoOS(client.OSInfo, false);
                    }
                }
            }
        });

        setFocusable(true);
        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setVisible(false);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        });

        //        (new Thread (this, "( ML ) tRCFrame for " + getTitle())).start();
        ImageIcon logo = loadIcon("lia/images/joconda.jpg");
        Image img = logo.getImage();
        Image iconLogo = img.getScaledInstance(-160, -160, java.awt.Image.SCALE_DEFAULT);
        setIconImage(iconLogo);
    }

    public void setSerMonitorBase(SerMonitorBase serMonitorBase) {
        this.serMonitorBase = serMonitorBase;
        if (rcpanel != null) {
            rcpanel.setSerMonitorBase(serMonitorBase);
        }
    }

    public boolean checkVersion() {

        MLSerClient client = (MLSerClient) dataprovider;
        if (client.mlVersion == null) {
            return false;
        }
        String v[] = client.mlVersion.replace('.', ',').split(",");
        if ((v == null) || (v.length == 0)) {
            return false;
        }
        try {
            int f = Integer.parseInt(getNumberFromVersion(v[0]));
            if (f < 1) {
                return false;
            }
            if (f > 1) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
        // so it's 1...
        if (v.length == 1) {
            return false;
        }
        try {
            int f = Integer.parseInt(getNumberFromVersion(v[1]));
            if (f < 4) {
                return false;
            }
            if (f > 4) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
        // so it's 1.4.....
        if (v.length == 2) {
            return false;
        }
        try {
            int f = Integer.parseInt(getNumberFromVersion(v[2]));
            if (f < 4) {
                return false;
            }
            if (f > 4) {
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
        // so it's 1.4.4....
        return true;
    }

    private String getNumberFromVersion(String v) {

        char[] array = v.toCharArray();
        String ret = "";
        int i = 0;
        while ((i < array.length) && Character.isDigit(array[i])) {
            ret += array[i];
            i++;
        }
        return ret;
    }

    @Override
    public void setVisible(boolean b) {

        //System.out.println("set visible called");
        super.setVisible(b);
        if (!b) {
            tClient client = (tClient) dataprovider;
            if ((client != null) && (client.osControl != null)) {
                client.osControl.windowClosed();
            }
            if ((client != null) && (client.opticalSwitchControl != null)) {
                client.opticalSwitchControl.windowClosed();
            }
        }
    }

    private class AdminTestThread extends Thread {

        public AdminTestThread() {
            super("(ML) AdminTestThread @" + getTitle());
        }

        @Override
        public void run() {
            synchronized (controlLock) {
                control = null;
                try {
                    if (address != null) {
                        control = MonitorControl.connectTo(address.getHostAddress(), remoteRegistryPort);
                        if (control != null) {
                            SerMonitorBase.controlModules.put(address.getHostAddress() + ":" + remoteRegistryPort,
                                    control);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                jbAdmin.setVisible(control != null);
            }
        }

    }

    private class AppControlThread extends Thread {

        public AppControlThread() {
            super("(ML) AppControlThread @" + getTitle());
        }

        @Override
        public void run() {
            synchronized (appControlLock) {
                appControl = null;

                logger.log(Level.INFO, " address = " + address + " PORT = " + ControlPort);

                try {
                    if (address != null) {
                        appControl = Main
                                .getInstance((MLSerClient) dataprovider, address.getHostAddress(), ControlPort);
                    }
                } catch (Throwable e) {
                    appControl = null;
                    e.printStackTrace();
                }

                logger.log(Level.INFO, " appcontrol = " + appControl);

                acAdmin.setVisible(appControl != null);
            }
        }

    }

    //    public void run() {
    //
    //        if ( logger.isLoggable(Level.FINEST) ) {
    //            logger.log(Level.INFO, " tRCFrame for " + getTitle() + " in main loop " );
    //        }
    //
    //        while (!done) {
    //            try {
    //                Thread.sleep(10000);
    //            } catch (Exception e) {
    //                ;
    //            }
    //        }
    //        
    //        if ( logger.isLoggable(Level.FINER) ) {
    //            logger.log(Level.FINER, "OUT FROM while() tRCFrame");
    //        }
    //        dispose();
    //    }

    public void addFarm(MFarm f) {
        rcpanel.addFarm(f);
    }

    public void updateFarm(MFarm f, boolean r) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "UPDATE FARM " + f.name);
        }

        rcpanel.updateFarm(f, r);
        if (control != null) {
            control.updateConfig(f, f.name);
        }
    }

    void endIt() {
        setVisible(false); //

        if (control != null) {
            //            control.dispose();
            String key = address.getHostAddress() + ":" + remoteRegistryPort;
            if (SerMonitorBase.controlModules.containsKey(key)) {
                SerMonitorBase.controlModules.remove(key);
            }
            control = null;
        }

        if (jbAdmin != null) {
            jbAdmin.setVisible(false);
        }

        if (acAdmin != null) {
            acAdmin.setVisible(false);
        }

        //		if (osAdmin != null)
        //			osAdmin.setVisible(false);

        if (appControl != null) {
            appControl.windowClosed(null);
        }

        tClient client = (tClient) dataprovider;
        if ((client != null) && (client.osControl != null)) {
            client.osControl.windowClosed();
            //			osControl = null;
        }
    }

    public void stopIt() {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "STOPPING tRCFrame for " + getTitle());
        }
        this.done = true;
        if (appControl != null) {
            appControl.windowClosed(null);
        }
        if (control != null) {
            control.setVisible(false);
            control.dispose();
            control = null;
        }
        rcpanel.stopIt();
        dispose();
    }

    public void setLocalTime(String dd) {
        timeTF.setText("Local Time :" + dd);
        //System.out.println("rcframe: local time "+ dd);
        if (rcpanel != null) {
            rcpanel.setLocalTime(dd);
        }
    }

    public void setMLVersion(String dd) {
        mlVersion.setText("MonALISA Version: " + dd);
    }

    public void setUptime(String dd) {
        uptime = dd;
    }

    public void setBuildNr(String dd) {
        buildNr = dd;
    }

    /**
     * @return Returns the MonaLIsaEntry.
     */
    public MonaLisaEntry getMle() {
        return mle;
    }

    public ImageIcon loadIcon(String resource) {
        ImageIcon ico = null;
        ClassLoader myClassLoader = getClass().getClassLoader();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " myClassLoader = " + myClassLoader);
        }

        try {
            URL resLoc = myClassLoader.getResource(resource);
            ico = new ImageIcon(resLoc);
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.WARNING, "Failed to get icon " + resource, t);
            }
        }
        return ico;
    }

}
