package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import lia.Monitor.ClientsFarmProxy.ProxyServiceEntry;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Globals;
import lia.Monitor.JiniClient.CommonGUI.Jogl.InfoPlane;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Shadow;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Texture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.TextureDataSpace;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.NetResourceClassLoader;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TabPanBase;
import lia.Monitor.JiniClient.Farms.FarmsSerMonitor;
import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.control.MonitorControl;
import lia.Monitor.control.UserPasswordGather;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;

abstract public class MainBase extends JFrame implements ItemListener {

    /**
     * 
     */
    private static final long serialVersionUID = -5856894978496565740L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MainBase.class.getName());

    public long nStartCreateMonitorTime = -1;

    public long nStopCreateMonitorTime = -1;

    public SerMonitorBase monitor;

    private static ImageIcon logo;

    private static Image iconLogo;

    private static String szMenuImagesPath = "lia/images/menu/small/";

    HelpDialog help;

    AboutWindow abw;

    Hashtable htGraphicalsIcons;// icons names for graphicals (name, icon base

    // name)

    Hashtable htGraphicals;

    Hashtable htOpenedFrames;

    Hashtable htCheckboxes;

    Hashtable htRadiobuttons;

    Vector vVisiblePanels;

    /** identify this client as an grids client */
    public boolean bGridsClient = false;

    public JPanel jJarDownBar;// status bar for downloading jars

    public JProgressBar jpbJarDownProgress;// progress bar in status bar

    public JPanel jTextLoadBar;// status bar for loading new resolution textures

    public JProgressBar jpbTextLoadProgress;// progress bar in new resolution status bar

    JPanel jpRadioButtons;// panel that contains the icons for panels as a

    // radio buttons list

    JPanel jpCheckboxes;// panel that contains the icons for panels as a check

    // box buttons list

    JPanel jpAbsolutePos;// used to move on the y axis the menu icons panels

    /**
     * => jpMenu contains jpAbsolutePos which can contain jpRadioButtons or jpCheckboxes
     */
    int nAbsPosY = 0;// absolute position for the inner panel on the y axis

    // inside the jpAbsolutePos panel ( inner panel ===
    // radio or check box panel )

    protected int nOptionsMenuHeight = 0;// integral height of options menu

    // including multi-view checkbox

    JButton jbArrowUp, jbArrowDown;

    // JPanel jpMultiview;
    JCheckBox jcbMultiView;

    // label with the caltech logo inside
    JLabel jlbCaltechLogo;

    public JPanel pCaltechLogo = null;

    JPanel jpSouthMenuPanel;// insert MultiView and down arrow if there is the

    // case

    public JPanel jpMenu;// contains all items for menu: arrow up and down,

    // multi view image, and panel's icons

    public JPanel jpMain;

    /** menu to select columns in table */
    public JMenu panelTablMenu;

    /** reference to table panel */
    public TabPanBase panelTabl = null;

    final JMenu proxyMenu = new JMenu("Proxy");

    final JMenu removedNodesMenu = new JMenu("RemNodes");

    JDialog removedNodesListDialog;

    JPanel jpAll;

    /** statistics windows */
    volatile public ClientTransferStatsFrame frmStatistics = null;

    ButtonGroup rbButtonGroup;

    WindowListener childFramesListener;

    public StatisticsMonitor sMon;

    JLabel jlbStatistics;

    BoxLayout layJPRB;

    BoxLayout layJPCB;

    JMoreMenu groupMenu;

    private volatile int nGroupMenuStartPos = 0;// start position from where group names are inserted

    JMenu positionMenu;

    String clientName;

    public volatile boolean bAutomaticPosition = true;// set that farms use automatic

    // positioning, not based on own
    // configuration file

    public volatile boolean bSmallMenuIcons = true;// set menu icons size: small or not

    // small (normal)

    public volatile boolean topologyShown = false;

    public volatile boolean proxyMenuShown = false;

    public volatile boolean removedNodesShown = false;

    public volatile boolean nodesMenuShown = false; // indicates if nodes removal menu

    // option is visible or not

    public configNodesFrame cnFrame; // config nodes frame

    public ChangeResFrame crFrame;// change resolution frame

    protected HashSet rangeOfGroups = new HashSet();

    // private Object objGroupMenu = new Object();

    private StatsUpdateThread threadUpStats;

    protected void locateOnScreen(Component component) {
        Dimension paneSize = component.getSize();
        Dimension screenSize = component.getToolkit().getScreenSize();
        component.setLocation((screenSize.width - paneSize.width) / 2, (screenSize.height - paneSize.height) / 2);
    }

    public MainBase(String client) {

        super("MonALISA");
        // is this a grids client?
        bGridsClient = AppConfig.getProperty("ml_client.mainWindow.gridsClient", "false").equals("true");
        if (bGridsClient) {
            setTitle("MonALISA LHC Computing GridMap");
        }
        setSize(new Dimension(1000, 650));
        locateOnScreen(this);
        this.clientName = client;
        help = new HelpDialog(this);
        initStart();
    }

    /**
     * This method should be called with the appropriate SerMonitor for the client
     * 
     * @param sm
     *            The desired SerMonitor
     */
    public void setSerMonitor(SerMonitorBase sm) {
        monitor = sm;
        monitor.init2();
    }

    public boolean hasGraphical(String name) {
        return (htGraphicals.get(name) == null ? false : true);
    }

    /**
     * removes panel icon from left menu list
     * 
     * @param name
     */
    public void removeGraphical(String name) {
        // System.out.println("Trying to remove panel... "+name);
        boolean bNewPanelVisible = false;
        // remove the panel from the frame and close the window
        JFrame f = (JFrame) htOpenedFrames.remove(name);
        JPanel pan = (JPanel) htGraphicals.remove(name);
        JCheckBox objCheck = (JCheckBox) htCheckboxes.remove(name);
        if (objCheck != null) {
            // System.out.println("check box found... ");
            jpCheckboxes.remove(objCheck);
            jpCheckboxes.repaint();
        }
        JRadioButton objRadio = (JRadioButton) htRadiobuttons.remove(name);
        if (objRadio != null) {
            // System.out.println("radio button found... ");
            jpRadioButtons.remove(objRadio);
            rbButtonGroup.remove(objRadio);
            jpRadioButtons.repaint();
        }
        ;
        htGraphicalsIcons.remove(name);
        if (f != null) {
            // System.out.println("frame found... ");
            if (f.isVisible()) {
                f.setVisible(false);
            }
            ;
            f.getContentPane().removeAll();
        }

        if (pan != null) {
            // System.out.println("graphical found... ");
            if (pan.isVisible()) {
                bNewPanelVisible = true;
            }
            jpMain.remove(pan);
            jpMain.repaint();
            vVisiblePanels.remove(pan);
            // CardLayout cl = (CardLayout)(jpMain.getLayout());
            // if(isSelected){
            // cl.show(jpMain, name);
            // vVisiblePanels.add(jpG);
            // }
            // tPane.addTab(name, yellowball, (JPanel)g, description);
            monitor.removeGraph((graphical) pan);
        }
        ;
        // pan.setVisible(false);

        if (jpMenu != null) {
            // compute maximal menu height
            nOptionsMenuHeight = getOptionsMenuHeight();
            // System.out.println("removeGraphical: new options menu
            // height="+nOptionsMenuHeight);
            onMenuResized();
        }

        // set another panel as visible pane
        if (bNewPanelVisible) {
            name = null;
            try {
                name = (String) htGraphicals.keys().nextElement();
            } catch (Exception ex) {

            }
            if (name != null) {
                // System.out.println("new panel visible: "+name);
                objCheck = (JCheckBox) htCheckboxes.get(name);
                if (objCheck != null) {
                    objCheck.setSelected(true);
                    // System.out.println("select new check box");
                    objCheck.repaint();
                }
                objRadio = (JRadioButton) htRadiobuttons.get(name);
                if (objRadio != null) {
                    objRadio.setSelected(true);
                    // System.out.println("select new radio button");
                    objRadio.repaint();
                }
                ;
                Dimension dim = pan.getSize();
                pan.setPreferredSize(dim);
                CardLayout cl = (CardLayout) (jpMain.getLayout());
                cl.show(jpMain, name);
                vVisiblePanels.add(pan);
                pan.setVisible(true);
            }
            ;
        }
        ;
    }

    public graphical getGraphical(String name) {
        return (graphical) htGraphicals.get(name);
    }

    public void addGraphical(graphical g, String name, String description, String iconBaseName) {
        addGraphical(g, name, description, iconBaseName, "gif");
    }

    /**
     * Adds a tab with a graphical component on the main panel
     * 
     * @param g
     *            The JPanel that implements graphical
     * @param name
     *            The name on the tab
     * @param description
     *            The description shwon when mouse is over the tab
     */
    public void addGraphical(graphical g, String name, String description, String iconBaseName, String extension) {
        JPanel jpG = (JPanel) g;
        // jpG.setPreferredSize(new Dimension(800, 650));
        // jpG.setVisible(false);
        boolean isSelected = (htGraphicals.size() == 0);

        // setup hashtables
        htGraphicals.put(name, g);
        // htGraphicalsIcons.put(name, iconBaseName);
        htGraphicalsIcons.put(name + "_unselIcon", iconBaseName + "_0." + extension);
        htGraphicalsIcons.put(name + "_selIcon", iconBaseName + "_1." + extension);
        htGraphicalsIcons.put(name + "_disabledIcon", iconBaseName + "_2." + extension);
        ImageIcon unselIcon = loadIcon(szMenuImagesPath + iconBaseName + "_0." + extension);
        ImageIcon selIcon = loadIcon(szMenuImagesPath + iconBaseName + "_1." + extension);
        ImageIcon disabledIcon = loadIcon(szMenuImagesPath + iconBaseName + "_2." + extension);
        // System.out.println("unselIcon="+szMenuImagesPath + iconBaseName
        // + "_0."+extension);
        // if ( unselIcon==null )
        // System.out.println("Could not load unselected icon for "+iconBaseName);
        // if ( selIcon==null )
        // System.out.println("Could not load selected icon for "+iconBaseName);
        // if ( disabledIcon==null )
        // System.out.println("Could not load disabled icon for "+iconBaseName);

        // setup corresponding checkbox
        JCheckBox jcb = new JCheckBox(name, isSelected);
        jcb.setToolTipText(description);
        jcb.setIcon(unselIcon);
        jcb.setDisabledIcon(disabledIcon);
        jcb.setSelectedIcon(selIcon);
        jcb.setVerticalTextPosition(SwingConstants.BOTTOM);
        jcb.setHorizontalTextPosition(SwingConstants.CENTER);
        jcb.setIconTextGap(1);

        jpCheckboxes.add(jcb);
        htCheckboxes.put(name, jcb);

        jcb.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox cb = (JCheckBox) e.getSource();
                JPanel pan = (JPanel) htGraphicals.get(cb.getText());
                if (cb.isSelected()) {
                    // open this view in a new frame
                    JFrame f = (JFrame) htOpenedFrames.get(cb.getText());
                    if (f == null) {
                        f = new JFrame(cb.getText());
                        htOpenedFrames.put(cb.getText(), f);
                        f.addWindowListener(childFramesListener);
                    }// else{
                    Dimension dim = pan.getSize();
                    pan.setPreferredSize(dim);
                    // }
                    vVisiblePanels.add(pan);
                    pan.requestFocusInWindow();
                    pan.setVisible(true);
                    f.getContentPane().add(pan);
                    f.pack();
                    f.setVisible(true);
                } else {
                    // remove the panel from the frame and close the window
                    JFrame f = (JFrame) htOpenedFrames.get(cb.getText());
                    pan = (JPanel) htGraphicals.get(cb.getText());
                    f.setVisible(false);
                    // pan.setVisible(false);
                    vVisiblePanels.remove(pan);
                    f.getContentPane().removeAll();
                }
            }
        });

        // setup corresponding radio button
        JRadioButton jrb = new JRadioButton(name, isSelected);
        jrb.setIcon(unselIcon);
        jrb.setDisabledIcon(disabledIcon);
        jrb.setSelectedIcon(selIcon);
        jrb.setVerticalTextPosition(SwingConstants.BOTTOM);
        jrb.setHorizontalTextPosition(SwingConstants.CENTER);
        jrb.setToolTipText(description);
        jrb.setIconTextGap(1);

        jpRadioButtons.add(jrb);
        rbButtonGroup.add(jrb);
        htRadiobuttons.put(name, jrb);
        jrb.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                JRadioButton rb = (JRadioButton) e.getSource();
                if (rb.isSelected()) {
                    // remove old panel from jpMain and add this one
                    // System.out.println("selected "+rb.getText());
                    JPanel pan = (JPanel) htGraphicals.get(rb.getText());
                    if (vVisiblePanels.contains(pan)) {
                        // System.out.println("crt");
                        return; // this panel is already selected
                    }
                    String name = rb.getText();
                    CardLayout cl = (CardLayout) (jpMain.getLayout());
                    cl.show(jpMain, name);
                    // System.out.println("Show "+name);
                    vVisiblePanels.clear();
                    vVisiblePanels.add(pan);
                    // System.out.println("finished switching");
                }
            }
        });

        jpMain.add(jpG, name);
        CardLayout cl = (CardLayout) (jpMain.getLayout());
        if (isSelected) {
            cl.show(jpMain, name);
            vVisiblePanels.add(jpG);
            jpG.setVisible(true);
        }
        // tPane.addTab(name, yellowball, (JPanel)g, description);
        monitor.addGraph(g);

        if (jpMenu != null) {
            // compute maximal menu height
            nOptionsMenuHeight = getOptionsMenuHeight();
            // System.out.println("addGraphical: new options menu
            // height="+nOptionsMenuHeight);
            onMenuResized();
        }
        ;
    }

    /**
     * Initialize jar download bar
     */
    private void buildJarDownloadBar() {
        jJarDownBar = new JPanel();
        jJarDownBar.setVisible(false);
        jJarDownBar.setOpaque(false);
        jJarDownBar.setLayout(new BorderLayout(10, 0));// new FlowLayout(FlowLayout.RIGHT, 10, 0));
        // jJarDownBar.setPreferredSize(new Dimension(200, 16));

        jpbJarDownProgress = new JProgressBar();
        jpbJarDownProgress.setPreferredSize(new Dimension(200, 16));
        jpbJarDownProgress.setFont(new Font("Arial", Font.PLAIN, 9));
        jJarDownBar.add(jpbJarDownProgress);
    }

    /**
     * Initialize texture load bar
     */
    private void buildTextureLoadBar() {
        jTextLoadBar = new JPanel();
        jTextLoadBar.setVisible(false);
        jTextLoadBar.setOpaque(false);
        jTextLoadBar.setLayout(new BorderLayout(10, 0));// new FlowLayout(FlowLayout.RIGHT, 10, 0));
        // jTextLoadBar.setPreferredSize(new Dimension(140, 16));

        jpbTextLoadProgress = new JProgressBar();
        jpbTextLoadProgress.setPreferredSize(new Dimension(140, 16));
        jpbTextLoadProgress.setFont(new Font("Arial", Font.PLAIN, 9));
        jTextLoadBar.add(jpbTextLoadProgress);
    }

    private void initStart() {
        try {
            System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
            System.setProperty("sun.net.client.defaultReadTimeout", "10000");
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Error setting socket connect and read timeouts", t);
        }
        setSize(new Dimension(1000, 650));
        WindowListener wl = new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        };
        addWindowListener(wl);
        this.setBackground(Color.white);

        htGraphicals = new Hashtable();
        htGraphicalsIcons = new Hashtable();
        htOpenedFrames = new Hashtable();
        htCheckboxes = new Hashtable();
        htRadiobuttons = new Hashtable();
        vVisiblePanels = new Vector();
        rbButtonGroup = new ButtonGroup();

        jpRadioButtons = new JPanel();
        layJPRB = new BoxLayout(jpRadioButtons, BoxLayout.Y_AXIS);
        jpRadioButtons.setLayout(layJPRB);
        jpRadioButtons.setSize(new Dimension(200, 1000));

        jpCheckboxes = new JPanel();
        layJPCB = new BoxLayout(jpCheckboxes, BoxLayout.Y_AXIS);
        jpCheckboxes.setLayout(layJPCB);
        jpCheckboxes.setSize(new Dimension(200, 1000));

        jpMain = new JPanel();
        jpMain.setLayout(new CardLayout(0, 0));
        jpMain.setSize(new Dimension(800, 650));

        buildJarDownloadBar();
        buildTextureLoadBar();
        createCaltechLogoPanel();

        // if its a grid client, then there is no reason to create the left menu bar
        if (!bGridsClient) {
            jpMenu = new JPanel();
            jpMenu.setLayout(new BorderLayout());
            jpMenu.setSize(new Dimension(200, 650));
            jpMenu.addMouseWheelListener(new MouseWheelListener() {

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    // System.out.println("mouse wheel moved for menu panel");
                    onSetNewAbsPos(-e.getWheelRotation() * 10);
                }
            });
            jpAbsolutePos = new JPanel();
            jpAbsolutePos.setLayout(null);
            jpMenu.add(jpAbsolutePos, BorderLayout.CENTER);
            nAbsPosY = 0;
            jpMenu.addComponentListener(new ComponentListener() {

                @Override
                public void componentHidden(ComponentEvent e) {
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                }

                @Override
                public void componentShown(ComponentEvent e) {
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    onMenuResized();
                }
            });
            jpSouthMenuPanel = new JPanel();
            jpSouthMenuPanel.setLayout(new BorderLayout());
            jbArrowUp = new JButton();
            jbArrowUp.setMargin(new Insets(0, 0, 0, 0));
            jpMenu.add(jbArrowUp, BorderLayout.NORTH);
            jbArrowUp.addMouseListener(new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    onSetNewAbsPos(20);
                }
            });
            jbArrowDown = new JButton( /* imgArrowDown */); // new
            jbArrowDown.setMargin(new Insets(0, 0, 0, 0));
            jpSouthMenuPanel.add(jbArrowDown, BorderLayout.NORTH);
            // if menu options height is bigger than the height of jpMenu, insert
            // buttons
            jbArrowDown.addMouseListener(new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    onSetNewAbsPos(-20);
                }
            });
            JPanel logo = new JPanel();
            // logo.setOpaque(false);
            logo.setLayout(new BorderLayout());
            jcbMultiView = new JCheckBox("Multi-view", false);
            jcbMultiView.setVerticalTextPosition(SwingConstants.BOTTOM);
            jcbMultiView.setHorizontalTextPosition(SwingConstants.CENTER);
            jcbMultiView.setIconTextGap(1);
            logo.add(jcbMultiView, BorderLayout.NORTH);
            logo.add(pCaltechLogo, BorderLayout.CENTER);
            jpSouthMenuPanel.add(logo, BorderLayout.CENTER);
            jpMenu.add(jpSouthMenuPanel, BorderLayout.SOUTH);

            jcbMultiView.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (((JCheckBox) (e.getSource())).isSelected()) {
                        // we are switching from radiobuttons to checkboxes
                        // select and disable the checkbox corresponding to the
                        // currently selected radio button; clear all other
                        // checkboxes
                        for (Enumeration enR = htRadiobuttons.keys(); enR.hasMoreElements();) {
                            String name = (String) enR.nextElement();
                            JRadioButton jrb = (JRadioButton) htRadiobuttons.get(name);
                            JCheckBox jcb = (JCheckBox) htCheckboxes.get(name);
                            jcb.setSelected(false);
                            if (jrb.isSelected()) {
                                jcb.setEnabled(false);
                            } else {
                                jcb.setEnabled(true);
                                JPanel pan = (JPanel) htGraphicals.get(name);
                                jpMain.remove(pan);
                            }
                        }
                        // show checkboxes
                        Dimension dim = getContentPane().getSize();
                        jpAbsolutePos.remove(jpRadioButtons);
                        jpAbsolutePos.add(jpCheckboxes);
                        // jpMenu.remove(jpRadioButtons);
                        // jpMenu.add(jpCheckboxes, BorderLayout.CENTER);
                        jpAll.setPreferredSize(dim);
                        pack();
                        if (jpMenu != null) {
                            jpMenu.repaint();
                            nOptionsMenuHeight = getOptionsMenuHeight();
                            onMenuResized();
                        }
                        ;
                        jpCheckboxes.setLocation(0, nAbsPosY);
                    } else {
                        // switching from checkboxes; close all opened windows and
                        // select
                        // the radiobutton corresponding to the currently disabled
                        // checkbox
                        String selPanName = "";
                        for (Enumeration enC = htCheckboxes.keys(); enC.hasMoreElements();) {
                            String name = (String) enC.nextElement();
                            JCheckBox jcb = (JCheckBox) htCheckboxes.get(name);
                            if (!jcb.isEnabled()) {
                                JRadioButton jrb = (JRadioButton) htRadiobuttons.get(name);
                                jrb.setSelected(true);
                                selPanName = name;
                                continue;
                            }
                            if (jcb.isSelected()) {
                                JFrame f = (JFrame) htOpenedFrames.get(name);
                                f.setVisible(false);
                                f.getContentPane().removeAll();
                                jcb.setSelected(false);
                            }
                            JPanel pan = (JPanel) htGraphicals.get(name);
                            pan.setVisible(true);
                            jpMain.add(pan, name);
                            vVisiblePanels.remove(pan);
                        }
                        // show radio buttons
                        Dimension dim = getContentPane().getSize();
                        jpAbsolutePos.remove(jpCheckboxes);
                        jpAbsolutePos.add(jpRadioButtons);
                        // jpMenu.remove(jpCheckboxes);
                        // jpMenu.add(jpRadioButtons, BorderLayout.CENTER);
                        jpAll.setPreferredSize(dim);
                        pack();
                        if (jpMenu != null) {
                            jpMenu.repaint();
                            nOptionsMenuHeight = getOptionsMenuHeight();
                            onMenuResized();
                        }
                        jpRadioButtons.setLocation(0, nAbsPosY);
                        CardLayout cl = (CardLayout) (jpMain.getLayout());
                        cl.show(jpMain, selPanName);
                    }
                }
            });

            // when user closes a detached panel using window's close button
            childFramesListener = new WindowListener() {

                @Override
                public void windowClosing(WindowEvent e) {
                    String name = ((JFrame) e.getSource()).getTitle();
                    JFrame f = (JFrame) htOpenedFrames.get(name);
                    // f.setVisible(false);
                    f.getContentPane().removeAll();
                    JPanel pan = (JPanel) htGraphicals.get(name);
                    vVisiblePanels.remove(pan);
                    // pan.setVisible(false);
                    JCheckBox cb = (JCheckBox) htCheckboxes.get(name);
                    cb.setSelected(false);
                }

                @Override
                public void windowActivated(WindowEvent e) {
                }

                @Override
                public void windowClosed(WindowEvent e) {
                }

                @Override
                public void windowDeactivated(WindowEvent e) {
                }

                @Override
                public void windowDeiconified(WindowEvent e) {
                }

                @Override
                public void windowIconified(WindowEvent e) {
                }

                @Override
                public void windowOpened(WindowEvent e) {
                }
            };
        }// end if grid client

        setFocusable(true);
        // hot key for topology panel or proxy menu
        addKeyListener(new KeyListener() {

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // if(e.getKeyChar() == 'T' || e.getKeyChar() == 'n' ) {
                // if(! topologyShown){
                // topologyShown = true;
                // addGraphical(new GNetTopoPan(), "Topology", "Network
                // Topology", "topology");
                // }
                // }
                if ((e.getKeyChar() == 'P') || (e.getKeyChar() == 'p')) {
                    if (!proxyMenuShown) {
                        proxyMenuShown = true;
                        addProxyMenu();
                    }
                } else if ((e.getKeyChar() == 'R') || (e.getKeyChar() == 'r')) {
                    if (!removedNodesShown) {
                        removedNodesShown = true;
                        addRemovedNodesMenu();
                    }
                } else if ((e.getKeyChar() == 'N') || (e.getKeyChar() == 'n')) {
                    if (!nodesMenuShown) {
                        nodesMenuShown = true;
                        addNodesMenu();
                    }
                } else if ((e.getKeyChar() == 'd') || (e.getKeyChar() == 'D')) {
                    BackgroundWorker.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            StringBuilder sDumpList = new StringBuilder();
                            sDumpList.append("Dumping list of available nodes...\r\n");
                            // dumping to log the list of farms
                            for (final rcNode node : monitor.snodes.values()) {
                                sDumpList
                                        .append(node.UnitName)
                                        .append(" - ")
                                        .append(node.IPaddress)
                                        .append(" (")
                                        .append((((node.client != null) && (node.client.mle != null)) ? node.client.mle.Group
                                                : "???")).append(")\r\n");
                            }
                            sDumpList.append("List ended, counted " + monitor.snodes.size() + " elements.");
                            logger.log(Level.INFO, sDumpList.toString());
                        }
                    }, 100);
                }

            }
        });
    }

    /**
     * @author mluc
     * @since Aug 15, 2006
     * @return
     */
    public JPanel createCaltechLogoPanel() {
        if (pCaltechLogo == null) {
            pCaltechLogo = new JPanel();
            jlbCaltechLogo = new JLabel("");
            jlbCaltechLogo.setOpaque(false);
            try {
                jlbCaltechLogo.setIcon(loadIcon("lia/images/CaltechLogo.png"));
            } catch (Exception ex) {
            }
            jlbCaltechLogo.addMouseListener(new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        if (MouseEvent.getMouseModifiersText(e.getModifiers()).toLowerCase().indexOf("shift") != -1) {
                            if (!nodesMenuShown) {
                                nodesMenuShown = true;
                                addNodesMenu();
                            }
                        } else if (!proxyMenuShown) {
                            proxyMenuShown = true;
                            addProxyMenu();
                        }
                        if (!removedNodesShown) {
                            removedNodesShown = true;
                            addRemovedNodesMenu();
                        }
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }
            });
            pCaltechLogo.setLayout(new BoxLayout(pCaltechLogo, BoxLayout.X_AXIS));
            pCaltechLogo.add(jlbCaltechLogo);
            pCaltechLogo.setPreferredSize(jlbCaltechLogo.getPreferredSize());
        }
        return pCaltechLogo;
    }

    /**
     * @author mluc
     * @since Jun 19, 2006
     */
    protected void addNodesMenu() {
        JMenuBar menuBar = getJMenuBar();
        final JMenu nodesMenu = new JMenu("Nodes");
        JMenuItem mi = new JMenuItem("Config");
        nodesMenu.add(mi);
        mi.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("show config nodes frame");
                cnFrame = new configNodesFrame(monitor);
                cnFrame.setVisible(true);
            }

        });
        final JCheckBoxMenuItem mi2 = new JCheckBoxMenuItem("Debug Mode");
        nodesMenu.add(mi2);
        mi2.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("set debug mode to " + mi2.isSelected());
                ChangeRootTexture.setDebugLevel(mi2.isSelected());
                NetResourceClassLoader.setDebugLevel(mi2.isSelected());
                InfoPlane.setDebugLevel(mi2.isSelected());
                Texture.logLevel = (mi2.isSelected() ? Level.INFO : Level.FINEST);
                TextureDataSpace.bDebug = mi2.isSelected();
            }
        });
        final JCheckBoxMenuItem mi3 = new JCheckBoxMenuItem("Test proxy msg buffer mechanism");
        nodesMenu.add(mi3);
        mi3.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("set testing proxy messages buffer mechanism to " + mi3.isSelected());
                monitor.setTestProxyBuf(mi3.isSelected());
            }
        });
        if (clientName.equals("Farms Client")) {
            final JMenuItem urlMenu = new JMenuItem("URL infos");
            nodesMenu.add(urlMenu);
            urlMenu.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    ((FarmsSerMonitor) monitor).iNetGeo.clientFrame.setVisible(true);
                    ((FarmsSerMonitor) monitor).iNetGeo.clientFrame.toFront();
                }
            });
        }
        menuBar.add(nodesMenu, 4);
        menuBar.revalidate();
    }

    /**
     * sets new absolute position for options menu given an delta positive or negative for up or down movement
     * 
     * @param step
     */
    public void onSetNewAbsPos(int step) {
        int newAbsPosY = nAbsPosY + step;
        Component jpMenuOptions = jpAbsolutePos.getComponent(0);
        if ((step < 0) && (nOptionsMenuHeight > jpAbsolutePos.getHeight())) {
            // check if I can move them any higher
            if ((newAbsPosY + nOptionsMenuHeight) < jpAbsolutePos.getHeight()) {
                newAbsPosY = jpAbsolutePos.getHeight() - nOptionsMenuHeight;
            }
            if ((nAbsPosY != newAbsPosY) && (jpMenuOptions != null)) {
                nAbsPosY = newAbsPosY;
                jpMenuOptions.setLocation(0, nAbsPosY);
            }
            ;
        } else if (step > 0) {
            if (newAbsPosY > 0) {
                newAbsPosY = 0;
            }
            // check if I can move them any lower
            if ((nAbsPosY != newAbsPosY) && (jpMenuOptions != null)) {
                nAbsPosY = newAbsPosY;
                jpMenuOptions.setLocation(0, nAbsPosY);
            }
            ;
        }
    }

    /**
     * when user resizes the items menu, this function is called or when the content of the menu is changed
     */
    public void onMenuResized() {
        int nMenuHeight = jpMenu.getHeight() - jcbMultiView.getHeight() - pCaltechLogo.getHeight();// +
        // jbArrowDown.getHeight()
        // +
        // jbArrowUp.getHeight();
        // System.out.println("full items menu height: "+nOptionsMenuHeight+ "
        // current items menu height: "+nMenuHeight);
        if (nOptionsMenuHeight > nMenuHeight) {// integral height of menu is
            // bigger than the one visible
            // show arrow buttons
            if (!jbArrowDown.isVisible()) {
                jbArrowDown.setVisible(true);
            }
            if (!jbArrowUp.isVisible()) {
                jbArrowUp.setVisible(true);
            }
            // if the user has resized the menu so that there is some free space
            // between the menu items and the bottom of menu
            // push the items down by correcting nAbsPosY
            if ((nAbsPosY + nOptionsMenuHeight) < jpAbsolutePos.getHeight() /*
                                                                            * nMenuHeight - jbArrowDown.getHeight() -
                                                                            * jbArrowUp.getHeight()
                                                                            */) {
                nAbsPosY = jpAbsolutePos.getHeight() - nOptionsMenuHeight;
                Component jpMenuOptions = jpAbsolutePos.getComponent(0);
                if (jpMenuOptions != null) {
                    jpMenuOptions.setLocation(0, nAbsPosY);
                }
            }
            ;
        } else {// all items in menu are visible
            // hide arrow buttons
            if (jbArrowUp.isVisible()) {
                jbArrowUp.setVisible(false);
            }
            if (jbArrowDown.isVisible()) {
                jbArrowDown.setVisible(false);
            }
            if (nAbsPosY != 0) {
                nAbsPosY = 0;
                /**
                 * get whichever component is currently on menu: the panel with checkboxes or the panel with radio
                 * buttons
                 */
                Component jpMenuOptions = jpAbsolutePos.getComponent(0);
                if (jpMenuOptions != null) {
                    jpMenuOptions.setLocation(0, nAbsPosY);
                }
            }
            ;
        }
        ;
    }

    /**
     * computes the maximal height of items menu, without the multiview checkbox ATTENTION: should be used only if the
     * menu is visible, else it returns 0
     */
    public int getOptionsMenuHeight() {
        int total_height = 0;
        JPanel jpItems;
        if (jcbMultiView.isSelected()) {
            jpItems = jpCheckboxes;
        } else {
            jpItems = jpRadioButtons;
        }
        // compute check-boxes height
        for (int i = 0; i < jpItems.getComponentCount(); i++) {
            total_height += jpItems.getComponent(i).getHeight();
        }
        // total_height += jcbMultiView.getHeight();
        return total_height;
    }

    /**
     * Finish the initialization. This method should be called after setSerMonitor and addGraphical in order to show the
     * main window.
     */
    public void init() {
        // usual client
        setJMenuBar(createMenu());
        jpAll = new JPanel();
        jpAll.setLayout(new BorderLayout());

        if (!bGridsClient) {
            // establish the logo icon
            logo = loadIcon("lia/images/joconda.jpg");
            java.awt.Image img = logo.getImage();
            iconLogo = img.getScaledInstance(-160, -160, java.awt.Image.SCALE_DEFAULT);
            setIconImage(iconLogo);

            // jpRadioButtons.setLayout(null);
            jpAbsolutePos.add(jpRadioButtons);
            jpRadioButtons.setLocation(0, nAbsPosY);
            // jpMenu.add(jpRadioButtons, BorderLayout.CENTER);

            jpAll.add(jpMenu, BorderLayout.WEST);
        }
        jpAll.add(jpMain, BorderLayout.CENTER);
        jpAll.setPreferredSize(new Dimension(1000, 650));

        getContentPane().add(jpAll);
        if (AppConfig.getProperty("lia.Monitor.mainWindow.maximized", "false").equals("true")) {
            try {
                setFullScreenMode();

                setUndecorated(true);
                setResizable(false);

                // no need to pack because we don't set a preffered size for internal components
                // pack();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error maximizing main window.", ex);
                pack();
            }
        } else {
            pack();
        }
        setVisible(true);
        // CardLayout cl = (CardLayout)(jpMain.getLayout());
        // cl.first(jpMain);
        // first time when compute maximal menu height
        if (jpMenu != null) {
            nOptionsMenuHeight = getOptionsMenuHeight();
            onMenuResized();
        }
        // make tooltips to appear faster and last longer
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        // default as 100 ms
        // set to 500 ms for optical switches consideration in osgmap
        ttm.setInitialDelay(500);
        ttm.setDismissDelay(5 * 1000);
        // moved the updateGroups a bit down to give it time to initialize...
        monitor.populateSGroups();
        // dump the list of groups
        // boolean bFirst=true;
        // for ( Enumeration en = monitor.SGroups.keys(); en.hasMoreElements();bFirst=false) {
        // System.out.print((bFirst?"":",")+en.nextElement());
        // }
        // System.out.println("");

        if (!bGridsClient) {
            updateGroups();
            // } else {
            // //grids client
            // // moved the updateGroups a bit down to give it time to initialize...
            // monitor.populateSGroups();
            // updateGroups() ;
            // }
        }
    }

    public void loadMenuIcons(boolean bSmallSize) {
        if (bSmallSize) {
            szMenuImagesPath = "lia/images/menu/small/";
        } else {
            szMenuImagesPath = "lia/images/menu/";
        }
        ImageIcon imgArrowUp = loadIcon(szMenuImagesPath + "up_1.gif");
        jbArrowUp.setIcon(imgArrowUp);
        ImageIcon imgArrowDown = loadIcon(szMenuImagesPath + "down_1.gif");
        jbArrowDown.setIcon(imgArrowDown);
        jcbMultiView.setIcon(loadIcon(szMenuImagesPath + "multiview_0.gif"));
        jcbMultiView.setSelectedIcon(loadIcon(szMenuImagesPath + "multiview_1.gif"));
        JCheckBox jcb;
        JRadioButton jrb;
        String /* item_path, */path_unsel, path_sel, path_dis;
        String item_name;
        ImageIcon unselIcon, selIcon, disabledIcon;
        for (Iterator it = htGraphicals.keySet().iterator(); it.hasNext();) {
            item_name = (String) it.next();
            jcb = (JCheckBox) htCheckboxes.get(item_name);
            jrb = (JRadioButton) htRadiobuttons.get(item_name);
            // item_path = (String) htGraphicalsIcons.get(item_name);
            path_unsel = (String) htGraphicalsIcons.get(item_name + "_unselIcon");
            path_sel = (String) htGraphicalsIcons.get(item_name + "_selIcon");
            path_dis = (String) htGraphicalsIcons.get(item_name + "_disabledIcon");

            unselIcon = loadIcon(szMenuImagesPath + path_unsel);
            selIcon = loadIcon(szMenuImagesPath + path_sel);
            disabledIcon = loadIcon(szMenuImagesPath + path_dis);

            jcb.setIcon(unselIcon);
            jcb.setDisabledIcon(disabledIcon);
            jcb.setSelectedIcon(selIcon);

            jrb.setIcon(unselIcon);
            jrb.setDisabledIcon(disabledIcon);
            jrb.setSelectedIcon(selIcon);
        }
        ;

        if (jpMenu != null) {
            // compute maximal menu height
            nOptionsMenuHeight = getOptionsMenuHeight();
            // System.out.println("removeGraphical: new options menu
            // height="+nOptionsMenuHeight);
            onMenuResized();
        }
    }

    public class FullScreenFrame extends JFrame {

        private static final long serialVersionUID = 1L;

        public FullScreenFrame(final MainBase mb, final JPanel jp, final String jpname) {
            super(clientName + " - full screen mode");
            JMenuBar menuBar = new JMenuBar();
            menuBar.setLayout(new BoxLayout(menuBar, 0));
            JMenu fileMenu = new JMenu("File");
            fileMenu.setMnemonic('F');
            menuBar.add(fileMenu);
            setJMenuBar(menuBar);
            getContentPane().add(jp);
            JMenuItem exitMenuItem = fileMenu.add(new JMenuItem("Exit full screen"));
            // exitMenuItem.setMnemonic('x');
            exitMenuItem.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    BackgroundWorker.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            try {
                                Thread.currentThread().setName(
                                        "MAINBase - FullScreenFrame Timer - change to window mode");
                                setVisible(false);
                                // if ( !(jp instanceof JoglPanel) ) {
                                remove(jp);
                                // jp.setVisible(true);
                                dispose();
                                // };
                                // Thread.sleep(5000);
                                mb.setVisible(true);
                                mb.jpMain.add(jp, jpname);
                                // CardLayout cl = (CardLayout) (mb.jpMain.getLayout());
                                // cl.show(mb.jpMain, jpname);
                                // mb.setSize(800, 600);
                                // jp.setVisible(true);
                                // select the readded panel
                                // only if not joglpanel
                                if (!(jp instanceof JoglPanel)) {
                                    CardLayout cl = (CardLayout) (mb.jpMain.getLayout());
                                    cl.show(mb.jpMain, jpname);
                                    // select radio button
                                    JRadioButton jrb = (JRadioButton) mb.htRadiobuttons.get(jpname);
                                    jrb.setSelected(true);
                                    vVisiblePanels.clear();
                                    vVisiblePanels.add(jp);
                                }
                                ;
                                // System.out.println("card layout: "+jpname);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }, 100);
                }
            });
            // if ( jp instanceof JoglPanel ) {
            // //if 3d map, create a new panel
            // JoglPanel jogl;
            // if ( jp instanceof FarmsJoglPanel )
            // jogl = new FarmsJoglPanel();
            // else
            // jogl = new VrvsJoglPanel();
            // getContentPane().add(jogl);
            // } else
        }

        public void setFullScreen() {
            setUndecorated(true);
            setResizable(false);
            setSize(getToolkit().getScreenSize());
        }
    }

    // private class FSItemListener implements ActionListener {

    // protected Dimension restoreSize = new Dimension();
    // protected Dimension restorePos = new Dimension();
    // public MainBase mb;
    // public FSItemListener(MainBase m)
    // {
    // mb =m;
    // }
    // /////////////////////////////////////////////////////////
    // public void setFullScreen() {
    // //check first to see if radio buttons mode
    // if ( !mb.jcbMultiView.isSelected() ) {
    // //hide main window
    // //create new window with only one panel, undecorated
    // mb.setVisible(false);
    // JPanel pan = null;
    // String name = "";
    // for (Enumeration enR = htRadiobuttons.keys(); enR.hasMoreElements();) {
    // name = (String) enR.nextElement();
    // JRadioButton jrb = (JRadioButton) htRadiobuttons.get(name);
    // if ( jrb.isSelected() ) {
    // pan = (JPanel) mb.htGraphicals.get(name);
    // //remove panel only if not 3d map
    // // if ( !(pan instanceof JoglPanel) ) {
    // // mb.jpMain.remove(pan);
    // // };
    // break;
    // }
    // }
    // //get next panel to show, the first unselected one
    // String nextName=null;
    // JRadioButton nextRB=null;
    // for (Enumeration enR = htRadiobuttons.keys(); enR.hasMoreElements();) {
    // nextName = (String) enR.nextElement();
    // nextRB = (JRadioButton) htRadiobuttons.get(nextName);
    // if ( !nextRB.isSelected() ) {
    // break;
    // }
    // }
    // if ( pan!=null && nextRB!=null ) {
    // //remove panel from main window
    // CardLayout cl = (CardLayout) (mb.jpMain.getLayout());
    // cl.show(mb.jpMain, nextName);
    // //select next radio button
    // nextRB.setSelected(true);
    // JPanel nextPanel = (JPanel) htGraphicals.get(nextName);
    // vVisiblePanels.clear();
    // vVisiblePanels.add(nextPanel);
    // // // System.out.println("Show "+name);
    // mb.jpMain.remove(pan);
    // FullScreenFrame fsframe = new FullScreenFrame(mb, pan, name);
    // //add info to frame
    // fsframe.setSize(640, 480); // initial size
    // fsframe.setFullScreen();
    // //fsframe.setEnabled(true);
    // //fsframe.pack();
    // fsframe.setVisible(true);
    // pan.setVisible(true);
    // } else
    // mb.setVisible(true);
    // }
    // }

    // public void actionPerformed(ActionEvent e) {
    // setFullScreen();
    // }
    // }
    public JMenuBar createMenu() {

        // set menu weight to avoid drawing menus under 3D scenes
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        JMenuBar menuBar = new JMenuBar();
        menuBar.setLayout(new BoxLayout(menuBar, 0));
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');
        JMenuItem exitMenuItem = fileMenu.add(new JMenuItem("Exit"));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        menuBar.add(fileMenu);

        String sTrue = "true";
        Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
        bSmallMenuIcons = sTrue.equals(prefs.get("ToolbarMenu.ShowSmallIcons", sTrue));
        String sRotationBarSize = "thin";
        // first read preferences for options in menu
        // TODO: should check for 3dmap using another method
        try {
            if (hasGraphical("3D Map")) { // we have the 3D panel
                Globals.bShowCities = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.ShowCities", sTrue));
                Globals.bShowCountriesBorders = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.ShowCountriesBorders",
                        sTrue));
                if (AppConfig.getProperty("jogl.rederer.map.noBorder", "false").equals(sTrue)) {
                    Globals.bShowCountriesBorders = false;
                }
                Globals.bUpdateShadow = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.UpdateShadow", sTrue));
                Globals.bShowRotationBar = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.ShowRotationBar",
                        AppConfig.getProperty("ToolbarMenu.3DSubMenu.ShowRotationBar", sTrue)));
                Globals.bShowRotationBarTooltip = sTrue.equals(prefs.get(
                        "ToolbarMenu.3DSubMenu.ShowRotationBarTooltip", sTrue));
                JoglPanel.globals.myMapsClassLoader.bDownloadNewFiles = sTrue.equals(prefs.get(
                        "ToolbarMenu.3DSubMenu.DownloadNewTextures", sTrue));
                Shadow.bShowNight = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.ShowNight", sTrue));
                Shadow.bHideLights = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.HideLights", sTrue));
                Globals.bHideMoon = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.HideMoon", sTrue));
                sRotationBarSize = prefs.get("ToolbarMenu.3DSubMenu.RotationBarSize", "thin");
                Texture.bNiceRendering = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.NiceMapsRendering", sTrue));
                Globals.bNiceLinks = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.NiceLinks", sTrue));
                Globals.bShowClouds = sTrue.equals(prefs.get("ToolbarMenu.3DSubMenu.ShowClouds", sTrue));
                // read starting rotation value
                String sRotSpeed = AppConfig.getProperty("jogl.globe_rotation_speed", null);
                int nRotSpeed = 0;
                try {
                    nRotSpeed = Integer.parseInt(sRotSpeed);
                } catch (Exception ex) {
                    nRotSpeed = 0;
                }
                if (nRotSpeed > 100) {
                    nRotSpeed = 100;
                }
                if (nRotSpeed < 0) {
                    nRotSpeed = 0;
                }
                if (nRotSpeed > 0) {
                    JoglPanel.globals.mainPanel.timeSlider.setValue(nRotSpeed);
                }
            }
        } catch (Exception ex) {
            System.out.println("[MainBase] 3D Map not available, will not be shown in menu, error: " + ex.getMessage());
        }
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic('V');
        if (!bGridsClient) {
            JCheckBoxMenuItem miIconSize = new JCheckBoxMenuItem("small icons", bSmallMenuIcons);
            miIconSize.addItemListener(new ItemListener() {

                @Override
                public void itemStateChanged(ItemEvent e) {
                    int nPositionState = e.getStateChange();
                    if (nPositionState == ItemEvent.SELECTED) {
                        // set small size for icons
                        bSmallMenuIcons = true;
                    } else { // nPositionState == ItemEvent.DESELECTED
                        bSmallMenuIcons = false;
                    }
                    try {
                        Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                        prefs.put("ToolbarMenu.ShowSmallIcons", Boolean.toString(bSmallMenuIcons));
                    } catch (Exception ex) {
                        System.out
                                .println("[MainBase] Could not save preference for toolbar menu option Small Icons, error: "
                                        + ex.getMessage());
                    }
                    loadMenuIcons(bSmallMenuIcons);
                }
            });
            // set icons for all available resurses: 2 arrows (up and down) and one
            // multiview button
            // for the moment no panel icons
            loadMenuIcons(bSmallMenuIcons);
            viewMenu.add(miIconSize);
        }
        // set fullscreen option
        if (AppConfig.getProperty("lia.Monitor.mainWindow.maximized", "false").equals(sTrue)) {
            boolean bTrueFullscreen = sTrue.equals(prefs.get("ToolbarMenu.ShowTrueFullScreen", sTrue));
            JCheckBoxMenuItem miFullscreen = new JCheckBoxMenuItem("set fullscreen", bTrueFullscreen);
            miFullscreen.addItemListener(new ItemListener() {

                @Override
                public void itemStateChanged(ItemEvent e) {
                    int nPositionState = e.getStateChange();
                    boolean bFS = (nPositionState == ItemEvent.SELECTED);
                    try {
                        Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                        prefs.put("ToolbarMenu.ShowTrueFullScreen", Boolean.toString(bFS));
                    } catch (Exception ex) {
                        System.out
                                .println("[MainBase] Could not save preference for toolbar menu option True Full Screen, error: "
                                        + ex.getMessage());
                    }
                    setFullScreenMode();
                }
            });
            viewMenu.add(miFullscreen);
        }
        JMenuItem miStats = new JMenuItem("Statistics");
        viewMenu.add(miStats);
        miStats.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                // System.out.println("show statistics frame");
                if (frmStatistics == null) {
                    frmStatistics = new ClientTransferStatsFrame(sMon);
                    if (threadUpStats != null) {
                        threadUpStats.updateNow();
                    }
                    frmStatistics.setVisible(true);
                } else {
                    frmStatistics.setVisible(true);
                    frmStatistics.toFront();
                    frmStatistics.requestFocusInWindow();
                }
            }
        });
        menuBar.add(viewMenu);

        try {
            if (hasGraphical("3D Map")) { // we have the 3D panel
                JMenu panel3DMenu = new JMenu("3D Map Options");
                viewMenu.add(panel3DMenu);
                // JMenuItem miFullScreen = new JMenuItem( "Change to full screen");
                // miFullScreen.addActionListener(new FSItemListener(this));
                // fileMenu.add(miFullScreen);
                JMenuItem resMenuItem = panel3DMenu.add(new JMenuItem("Map options"));
                JCheckBoxMenuItem miShowCities = new JCheckBoxMenuItem("show cities", Globals.bShowCities);
                JCheckBoxMenuItem miCBorders = new JCheckBoxMenuItem("show countries borders",
                        Globals.bShowCountriesBorders);
                if (AppConfig.getProperty("jogl.rederer.map.noBorder", "false").equals("true")) {
                    miCBorders.setEnabled(false);
                }
                JCheckBoxMenuItem miUpdateShadow = new JCheckBoxMenuItem("update shadow", Globals.bUpdateShadow);
                JCheckBoxMenuItem miRotationBar = new JCheckBoxMenuItem("show rotation bar", Globals.bShowRotationBar);
                final JCheckBoxMenuItem miRotationBarTT = new JCheckBoxMenuItem("show rotation bar tooltip",
                        Globals.bShowRotationBarTooltip);
                if (Globals.bShowRotationBar == false) {
                    miRotationBarTT.setEnabled(false);
                }
                JCheckBoxMenuItem miTextDown = new JCheckBoxMenuItem("download new images",
                        JoglPanel.globals.myMapsClassLoader.bDownloadNewFiles);
                JCheckBoxMenuItem miNightShadow = new JCheckBoxMenuItem("show night shadow", Shadow.bShowNight);
                final JCheckBoxMenuItem miNightLights = new JCheckBoxMenuItem("show night lights", !Shadow.bHideLights);
                if (Shadow.bShowNight == false) {
                    miNightLights.setEnabled(false);
                }
                JCheckBoxMenuItem miHideMoon = new JCheckBoxMenuItem("show moon", !Globals.bHideMoon);
                JCheckBoxMenuItem miNiceRendering = new JCheckBoxMenuItem("nice maps rendering", Texture.bNiceRendering);
                JCheckBoxMenuItem miNiceLinks = new JCheckBoxMenuItem("antialiased links", Globals.bNiceLinks);
                JCheckBoxMenuItem miShowClouds = new JCheckBoxMenuItem("show clouds", Globals.bShowClouds);
                panel3DMenu.add(miShowCities);
                panel3DMenu.add(miCBorders);
                panel3DMenu.add(miUpdateShadow);
                panel3DMenu.add(miRotationBar);
                panel3DMenu.add(miRotationBarTT);
                panel3DMenu.add(miTextDown);
                panel3DMenu.add(miNightShadow);
                panel3DMenu.add(miNightLights);
                panel3DMenu.add(miHideMoon);
                panel3DMenu.add(miNiceRendering);
                panel3DMenu.add(miNiceLinks);
                panel3DMenu.add(miShowClouds);
                JMenu rbsMenu = new JMenu("Rotation Bars Size");
                panel3DMenu.add(rbsMenu);
                JRadioButtonMenuItem smallSizeMI = new JRadioButtonMenuItem("thin", true);
                JRadioButtonMenuItem normalSizeMI = new JRadioButtonMenuItem("normal", false);
                JRadioButtonMenuItem bigSizeMI = new JRadioButtonMenuItem("thick", false);
                rbsMenu.add(smallSizeMI);
                rbsMenu.add(normalSizeMI);
                rbsMenu.add(bigSizeMI);
                ButtonGroup bg = new ButtonGroup();
                bg.add(smallSizeMI);
                bg.add(normalSizeMI);
                bg.add(bigSizeMI);
                if ("thin".equals(sRotationBarSize)) {
                    smallSizeMI.setSelected(true);
                    normalSizeMI.setSelected(false);
                    bigSizeMI.setSelected(false);
                    Globals.ROTATION_BAR_HEIGHT = 11;
                    Globals.ROTATION_BAR_BORDER = 1;
                } else if ("normal".equals(sRotationBarSize)) {
                    smallSizeMI.setSelected(false);
                    normalSizeMI.setSelected(true);
                    bigSizeMI.setSelected(false);
                    Globals.ROTATION_BAR_HEIGHT = 20;
                    Globals.ROTATION_BAR_BORDER = 2;
                } else if ("thick".equals(sRotationBarSize)) {
                    smallSizeMI.setSelected(false);
                    normalSizeMI.setSelected(false);
                    bigSizeMI.setSelected(true);
                    Globals.ROTATION_BAR_HEIGHT = 30;
                    Globals.ROTATION_BAR_BORDER = 2;
                }

                resMenuItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (crFrame == null) {
                            crFrame = new ChangeResFrame(monitor);
                            crFrame.setVisible(true);
                        }
                        ;
                    }
                });
                miShowCities.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bShowCities = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bShowCities = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowCities", Boolean.toString(Globals.bShowCities));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Cities, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miCBorders.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bShowCountriesBorders = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bShowCountriesBorders = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowCountriesBorders",
                                    Boolean.toString(Globals.bShowCountriesBorders));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Countries Borders, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miUpdateShadow.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.setUpdateShadow(true);
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.setUpdateShadow(false);
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.UpdateShadow", Boolean.toString(Globals.bUpdateShadow));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Update Shadow, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miRotationBar.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bShowRotationBar = true;
                            miRotationBarTT.setEnabled(true);
                            // miRotationBarTT.setSelected(Globals.bShowRotationBarTooltip);
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bShowRotationBar = false;
                            miRotationBarTT.setEnabled(false);
                            // miRotationBarTT.setSelected(false);
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowRotationBar",
                                    Boolean.toString(Globals.bShowRotationBar));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Rotation Bar, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miRotationBarTT.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bShowRotationBarTooltip = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bShowRotationBarTooltip = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowRotationBarTooltip",
                                    Boolean.toString(Globals.bShowRotationBarTooltip));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Rotation Bar Tooltip, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miTextDown.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            JoglPanel.globals.myMapsClassLoader.bDownloadNewFiles = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            JoglPanel.globals.myMapsClassLoader.bDownloadNewFiles = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.DownloadNewTextures",
                                    Boolean.toString(JoglPanel.globals.myMapsClassLoader.bDownloadNewFiles));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Download New Texture Files, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miNightShadow.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Shadow.bShowNight = true;
                            miNightLights.setEnabled(true);
                            // miNightLights.setSelected(!Shadow.bHideLights);
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Shadow.bShowNight = false;
                            miNightLights.setEnabled(false);
                            // miNightLights.setSelected(false);
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowNight", Boolean.toString(Shadow.bShowNight));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Night, error: "
                                            + ex.getMessage());
                        }
                        ChangeRootTexture.init(Texture.nInitialLevel,
                                (Shadow.bShowNight ? ChangeRootTexture.CRT_KEY_MODE_DO_SHADOW
                                        : ChangeRootTexture.CRT_KEY_MODE_REMOVE_SHADOW),
                                JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                                JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        // ChangeRootTexture crtMonitor = new ChangeRootTexture(TextureParams.nInitialLevel,
                        // (Shadow.bShowNight?ChangeRootTexture.CRT_KEY_MODE_DO_SHADOW:ChangeRootTexture.CRT_KEY_MODE_REMOVE_SHADOW));
                        // crtMonitor.setProgressBar(JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                        // JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        // //change global date
                        // if ( !crtMonitor.init() )
                        // System.out.println("Failed changing shadow. Will happen at next update.");
                        Globals.setUpdateShadow(Shadow.bShowNight);
                    }
                });
                miNightLights.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Shadow.bHideLights = false;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Shadow.bHideLights = true;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.HideLights", Boolean.toString(Shadow.bHideLights));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Hide Lights, error: "
                                            + ex.getMessage());
                        }
                        ChangeRootTexture.init(Texture.nInitialLevel,
                                (Shadow.bHideLights ? ChangeRootTexture.CRT_KEY_MODE_REMOVE_LIGHTS
                                        : ChangeRootTexture.CRT_KEY_MODE_DO_LIGHTS),
                                JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                                JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        // ChangeRootTexture crtMonitor = new ChangeRootTexture(TextureParams.nInitialLevel,
                        // (Shadow.bHideLights?ChangeRootTexture.CRT_KEY_MODE_REMOVE_LIGHTS:ChangeRootTexture.CRT_KEY_MODE_DO_LIGHTS));
                        // crtMonitor.setProgressBar(JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress,
                        // JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
                        // //change global date
                        // if ( !crtMonitor.init() )
                        // System.out.println("Failed changing lights. Will happen at next update.");
                    }
                });
                miHideMoon.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bHideMoon = false;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bHideMoon = true;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.HideMoon", Boolean.toString(Globals.bHideMoon));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Hide Moon, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miNiceRendering.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Texture.bNiceRendering = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Texture.bNiceRendering = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.NiceMapsRendering",
                                    Boolean.toString(Texture.bNiceRendering));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Nice Rendering, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                miNiceLinks.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bNiceLinks = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bNiceLinks = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.NiceLinks", Boolean.toString(Globals.bNiceLinks));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Nice Links, error: "
                                            + ex.getMessage());
                        }
                    }
                });

                miShowClouds.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            Globals.bShowClouds = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            Globals.bShowClouds = false;
                        }
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.ShowClouds", Boolean.toString(Globals.bShowClouds));
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Show Clouds, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                smallSizeMI.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Globals.ROTATION_BAR_HEIGHT = 11;// 20;
                        Globals.ROTATION_BAR_BORDER = 1;// 2;
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.RotationBarSize", "thin");
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Rotation Bar Size Thin, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                normalSizeMI.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Globals.ROTATION_BAR_HEIGHT = 20;
                        Globals.ROTATION_BAR_BORDER = 2;
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.RotationBarSize", "normal");
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Rotation Bar Size Normal, error: "
                                            + ex.getMessage());
                        }
                    }
                });
                bigSizeMI.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        Globals.ROTATION_BAR_HEIGHT = 30;
                        Globals.ROTATION_BAR_BORDER = 2;
                        try {
                            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                            prefs.put("ToolbarMenu.3DSubMenu.RotationBarSize", "thick");
                        } catch (Exception ex) {
                            System.out
                                    .println("[MainBase] Could not save preference for toolbar menu, 3D Map submenu, option Rotation Bar Size Thick, error: "
                                            + ex.getMessage());
                        }
                    }
                });
            }
            ;
        } catch (Exception ex) {
            System.out
                    .println("[MainBase] 3D Map not available, will not be shown in menu, error2: " + ex.getMessage());
        }

        // check if table exists
        if (hasGraphical("TabPan")) { // we have the table panel
            panelTablMenu = new JMenu("Table Panel Info");
            viewMenu.add(panelTablMenu);
            // put existing columns
            panelTabl.addColumnsToMenu();
        }
        ;

        if (!bGridsClient) {

            JMenu discoMenu = new JMenu("Discovery");

            JMenuItem addLocator = discoMenu.add(new JMenuItem("Add Locator..."));
            addLocator.setMnemonic('L');
            addLocator.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent ae) {
                    addLocator();
                }

            });

            JMenuItem remLocator = discoMenu.add(new JMenuItem("Remove Locator..."));
            remLocator.setMnemonic('O');
            remLocator.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    removeLocator();
                }

            });
            discoMenu.addSeparator();

            menuBar.add(discoMenu);

            JMenuItem addGroup = discoMenu.add(new JMenuItem("Add Group..."));
            addGroup.setMnemonic('G');
            addGroup.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent ae) {
                    addGroup();
                }

            });

            JMenuItem remGroup = discoMenu.add(new JMenuItem("Remove Group..."));
            remGroup.setMnemonic('R');
            remGroup.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    removeGroup();
                }

            });

            // groupMenu = new JMenu("Groups");
            JMoreMenu gMenu = new JMoreMenu("Groups");
            JMenuItem miSelectAllGroups = new JMenuItem("Select All");
            gMenu.add(miSelectAllGroups);
            nGroupMenuStartPos++;
            JMenuItem miUnSelectAllGroups = new JMenuItem("Unselect All");
            gMenu.add(miUnSelectAllGroups);
            nGroupMenuStartPos++;
            gMenu.add(new Separator());
            nGroupMenuStartPos++;
            miSelectAllGroups.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (groupMenu == null) {
                        return;
                    }
                    try {
                        StringBuilder buf = new StringBuilder();
                        Object obj;
                        boolean bSetSelected = false;
                        for (int i = nGroupMenuStartPos; i < groupMenu.getItemCount(); i++) {
                            obj = groupMenu.getItem(i);
                            if ((obj != null) && (obj instanceof JCheckBoxMenuItem)) {
                                JCheckBoxMenuItem groupMI = (JCheckBoxMenuItem) obj;
                                if (!groupMI.isSelected()) {
                                    synchronized (rangeOfGroups) {
                                        rangeOfGroups.add(groupMI);
                                    }
                                    bSetSelected = true;
                                    groupMI.setSelected(true);
                                    if (buf.length() != 0) {
                                        buf.append(",");
                                    }
                                    String mitx = groupMI.getText();
                                    buf.append(mitx);
                                    monitor.SGroups.put(mitx, Integer.valueOf(1));
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "Change " + mitx + " 1");
                                    }
                                }
                            }
                        }
                        if (bSetSelected) {
                            monitor.updateUserGroupPreferences(buf.toString(), true);
                            monitor.GroupViewUpdate();
                        }
                        ;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            miUnSelectAllGroups.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (groupMenu == null) {
                        return;
                    }
                    try {
                        StringBuilder buf = new StringBuilder();
                        Object obj;
                        boolean bSetUnSelected = false;
                        for (int i = nGroupMenuStartPos; i < groupMenu.getItemCount(); i++) {
                            obj = groupMenu.getItem(i);
                            if ((obj != null) && (obj instanceof JCheckBoxMenuItem)) {
                                JCheckBoxMenuItem groupMI = (JCheckBoxMenuItem) obj;
                                if (groupMI.isSelected()) {
                                    synchronized (rangeOfGroups) {
                                        rangeOfGroups.add(groupMI);
                                    }
                                    bSetUnSelected = true;
                                    groupMI.setSelected(false);
                                    if (buf.length() != 0) {
                                        buf.append(",");
                                    }
                                    String mitx = groupMI.getText();
                                    buf.append(mitx);
                                    monitor.SGroups.put(mitx, Integer.valueOf(0));
                                    if (logger.isLoggable(Level.FINEST)) {
                                        logger.log(Level.FINEST, "Change " + mitx + " 0");
                                    }
                                }
                            }
                        }
                        if (bSetUnSelected) {
                            monitor.updateUserGroupPreferences(buf.toString(), false);
                            monitor.GroupViewUpdate();
                        }
                        ;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
            groupMenu = gMenu;
            menuBar.add(groupMenu);
        }
        ;

        // add security menu
        if (!bGridsClient) {
            JMenu securityMenu = new JMenu("Security");
            securityMenu.setMnemonic('S');
            JMenuItem keystore = securityMenu.add(new JMenuItem("Keystore"));
            keystore.setMnemonic('K');
            keystore.addActionListener(new ActionListener() {

                // long lastCall = 0;
                // long expire = 15 * 1000;

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        // if (System.currentTimeMillis() - lastCall < expire) return;
                        // load key store
                        UserPasswordGather auth = new UserPasswordGather();
                        int ret = auth.doAuth();
                        if ((monitor != null) && (ret == UserPasswordGather.ID_OK)) {
                            System.setProperty("lia.Monitor.KeyStore", auth.getUser());
                            System.setProperty("keystore", auth.getUser());
                            System.setProperty("lia.Monitor.KeyStorePass", auth.getPassword());
                            System.setProperty("keystore_pwd", auth.getPassword());
                            System.setProperty("keystore_alias", auth.getAlias());
                            if (monitor.basicPanel != null) {
                                monitor.basicPanel.refreshKeyStore();
                            }
                            MonitorControl.mc.clear();
                            if (SerMonitorBase.controlModules != null) {
                                SerMonitorBase.controlModules.clear();
                            }
                            if (SerMonitorBase.osControlModules != null) {
                                SerMonitorBase.osControlModules.clear();
                            }
                            for (final rcNode n : monitor.snodes.values()) {
                                if (n == null) {
                                    continue;
                                }
                                n.client.redoOS(n.client.OSInfo, false);
                            }
                            // lastCall = System.currentTimeMillis();
                        }
                        // System.setProperty("lia.Monitor.AdminUser", auth.getAlias() );
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    repaint();
                }

            });
            menuBar.add(securityMenu);
        }

        // add position menu
        if (!bGridsClient) {
            // insert position option only for farm client
            if (clientName.compareTo("Farms Client") == 0) {
                positionMenu = new JMenu("Position");
                JCheckBoxMenuItem newMI = new JCheckBoxMenuItem("automatic", bAutomaticPosition = getBoolUserPref(
                        "farms.automatic.position", bAutomaticPosition));
                newMI.addItemListener(new ItemListener() {

                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        int nPositionState = e.getStateChange();
                        if (nPositionState == ItemEvent.SELECTED) {
                            // set automatic position for farms
                            bAutomaticPosition = true;
                        } else { // nPositionState == ItemEvent.DESELECTED
                            // set manual position for farms
                            bAutomaticPosition = false;
                        }
                        setBoolUserPref("farms.automatic.position", bAutomaticPosition);
                        // to reset position use RemoveNode from JiniClient...
                        monitor.reconsiderPositionForNodes();
                        // for ( Enumeration en=monitor.snodes.keys(); en.hasMoreElements();) {
                        // ServiceID sid = (ServiceID)en.nextElement();
                        // monitor.removeNode(sid);
                        // monitor.addNode()
                        // }
                    }
                });
                positionMenu.add(newMI);
                menuBar.add(positionMenu);
            }
            ;
        }

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');
        JMenuItem examples = helpMenu.add(new JMenuItem("Examples"));
        examples.setMnemonic('E');
        examples.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (!bGridsClient) {
                        help.showOnlineHelp("http://monalisa.caltech.edu/ml_client/monalisa__Documentation__User_Interface_Guide.html");
                    } else {
                        help.showOnlineHelp("http://monalisa.caltech.edu/ml_client/monalisa__Documentation__Grids_Client.html");
                    }
                } catch (Exception ex) {
                    logger.warning("Got exception " + ex.getLocalizedMessage());
                }
            }
        });
        helpMenu.addSeparator();
        JMenuItem about = helpMenu.add(new JMenuItem("About"));
        about.setMnemonic('A');
        about.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if ((abw != null) && abw.isShowing()) {
                    abw.toFront();
                } else {
                    abw = AboutWindow.display(clientName);
                }
            }
        });

        menuBar.add(helpMenu);
        // get to the right part of the bar
        menuBar.add(jTextLoadBar);
        menuBar.add(jJarDownBar);
        menuBar.add(Box.createHorizontalGlue());

        sMon = new StatisticsMonitor(monitor, clientName);
        JPanel jpStatisticsBar = new JPanel();
        jpStatisticsBar.setLayout(new BoxLayout(jpStatisticsBar, BoxLayout.X_AXIS));
        jpStatisticsBar.setOpaque(false);

        // jpStatisticsBar.add(Box.createHorizontalGlue());
        // jpStatisticsBar.add(jTextLoadBar);
        // jpStatisticsBar.add(jJarDownBar);
        // jpStatisticsBar.add(Box.createHorizontalGlue());
        // jpStatisticsBar.add(Box.createHorizontalGlue());

        // jpStatisticsBar.setPreferredSize(new Dimension(140, 16));
        jlbStatistics = new JLabel("? ", SwingConstants.RIGHT);
        jlbStatistics.setFont(new Font("Arial", Font.PLAIN, 9));
        jpStatisticsBar.add(jlbStatistics);
        JLabel jlbChoiceArrow = new JLabel(loadIcon("lia/images/arrows.png"));
        jlbChoiceArrow.setToolTipText("Left or Right Click to change type of statistics shown");
        jlbChoiceArrow.addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent e) {
                // change choice that will be visible at next timer execution
                if (e.getButton() == MouseEvent.BUTTON3) {
                    sMon.changeType(true);
                } else {
                    sMon.changeType(false);
                }
                jlbStatistics.setText(sMon.getStatistics());
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }
        });
        jpStatisticsBar.add(jlbChoiceArrow);
        // jpStatisticsBar.add(Box.createHorizontalStrut(4));
        // TODO should add a reset counters button
        // set an timer task to run at every 4 seconds that updates the label
        // with the number of clients viewable
        threadUpStats = new StatsUpdateThread();
        threadUpStats.start();
        // BackgroundWorker.schedule( new TimerTask() {
        // public void run() {

        // }
        // }, 0, StatisticsMonitor.TIME_UNIT);
        menuBar.add(jpStatisticsBar);
        // menuBar.add(jlbChoiceArrow);
        return menuBar;
    }

    /**
     * @author mluc
     * @since May 29, 2006
     * @param bfs
     */
    protected void setFullScreenMode() {
        boolean bfs = true;
        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            bfs = "true".equals(prefs.get("ToolbarMenu.ShowTrueFullScreen", "true"));
        } catch (Exception ex) {
            bfs = true;
        }

        // this is an alternate solution to maximize
        /*
         * Rectangle maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
         * setMaximizedBounds(maxBounds); setExtendedState(getExtendedState()|Frame.MAXIMIZED_BOTH); Dimension dim = new
         * Dimension(maxBounds.width, maxBounds.height); setSize(dim); setLocation(0,0);
         */
        // second solution, perhaps much more viable
        Dimension dim = getToolkit().getScreenSize();
        Insets ins = getToolkit().getScreenInsets(getGraphicsConfiguration());
        if (!bfs) {
            // respect the insets and move the window
            setBounds(ins.left, ins.top, dim.width - ins.left - ins.right, dim.height - ins.top - ins.bottom);
        } else {
            if (System.getProperty("os.name").equals("Mac OS X")) {
                System.out.println("Applying Mac OS X workaround to display under menu bar.");
                setBounds(0, ins.top, dim.width, dim.height - ins.top);
            } else {
                setBounds(0, 0, dim.width, dim.height);
            }
        }
        toFront();
    }

    class StatsUpdateThread extends Thread {

        private final boolean should_run = true;

        private volatile boolean update_now = false;

        public void updateNow() {
            update_now = true;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(" ( ML ) - MainBase - show statistics Thread");
            long lastRun = NTPDate.currentTimeMillis();
            long curRun;
            while (should_run) {
                curRun = NTPDate.currentTimeMillis();
                if ((curRun < lastRun) || ((curRun - lastRun) >= StatisticsMonitor.TIME_UNIT) || update_now) {
                    sMon.updateDataAndWindow();
                    update_now = false;
                    lastRun = curRun;
                }
                ;
                try {
                    Thread.sleep(StatisticsMonitor.TIME_UNIT);
                } catch (Exception ex) {
                    // ignore exception, as it will sleep again
                }
            }
        }
    };

    void addProxyMenu() {
        JMenuBar menuBar = getJMenuBar();

        proxyMenu.addMenuListener(new MenuListener() {

            @Override
            public void menuSelected(MenuEvent arg0) {
                // System.out.println("something happened!!!");
                bgRefreshProxyMenu();
            }

            @Override
            public void menuDeselected(MenuEvent arg0) {
            }

            @Override
            public void menuCanceled(MenuEvent arg0) {
            }
        });

        menuBar.add(proxyMenu, 3);
        menuBar.revalidate();

        // if the list of proxies is available, the prepare the menu
        bgRefreshProxyMenu();

        // BackgroundWorker.schedule(new TimerTask() {
        // public void run() {
        // Thread.currentThread().setName(
        // " ( ML ) - MainBase - refreshProxyMenu Timer Thread");
        // System.out.println("should refreshing proxy menu");
        // refreshProxyMenu(proxyMenu);
        // }
        // }, 0, 10000);
    }

    void addRemovedNodesMenu() {
        JMenuBar menuBar = getJMenuBar();
        removedNodesMenu.addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent arg0) {
                //                refreshRemovedNodesMenu();
                //                removedNodesListDialog.setVisible(true);
                //                removedNodesMenu.setArmed(false);
                //                removedNodesMenu.setSelected(false);
                //
                //                removedNodesMenu.menuSelectionChanged(true);
                //
                //                removedNodesMenu.setSelected(false);
                //
                //                removedNodesMenu.transferFocus();
                //                removedNodesMenu.transferFocusBackward();
                //
                //                arg0.consume();
            }

            @Override
            public void mouseEntered(MouseEvent arg0) {
            }

            @Override
            public void mouseExited(MouseEvent arg0) {
            }

            @Override
            public void mousePressed(MouseEvent arg0) {
                refreshRemovedNodesMenu();
                removedNodesListDialog.setVisible(true);
                removedNodesMenu.setArmed(false);
                removedNodesMenu.setSelected(false);

                removedNodesMenu.menuSelectionChanged(true);

                removedNodesMenu.setSelected(false);

                removedNodesMenu.transferFocus();
                removedNodesMenu.transferFocusBackward();

                arg0.consume();
            }

            @Override
            public void mouseReleased(MouseEvent arg0) {
            }
        });

        menuBar.add(removedNodesMenu, 3);
        menuBar.revalidate();

    }

    private void bgRefreshProxyMenu() {
        if (proxyMenu != null) {
            Timer t = new Timer();
            t.schedule(new TimerTask() {

                @Override
                public void run() {
                    Thread.currentThread().setName(" ( ML ) - Client - Proxy Menu refresh action Thread");
                    refreshProxyMenu();
                    // proxyMenu.revalidate();
                }
            }, 0);
        }
        ;
    }

    String getProxyMenuItem(ServiceItem si, String[] proxyAddress) {
        if (proxyAddress != null) {
            proxyAddress[0] = null;
            proxyAddress[1] = null;
        }
        if (si == null) {
            return null;
        }
        Entry[] proxyEntry = si.attributeSets;
        if (proxyEntry == null) {
            return null;
        }
        if (proxyEntry.length > 0) {
            int crtProxyPort = ((ProxyServiceEntry) proxyEntry[0]).proxyPort.intValue();
            String crtProxyIP = ((ProxyServiceEntry) proxyEntry[0]).ipAddress;
            String crtProxyName = ((ProxyServiceEntry) proxyEntry[0]).proxyName;
            if (crtProxyName == null) {
                crtProxyName = IpAddrCache.getHostName(crtProxyIP, true);
            } else {
                IpAddrCache.putIPandHostInCache(crtProxyIP, crtProxyName);
            }
            if (crtProxyName == null) {
                crtProxyName = crtProxyIP;
            }
            String crtProxy = crtProxyName + ":" + crtProxyPort;
            if (proxyAddress != null) {
                proxyAddress[0] = crtProxyIP + ":" + crtProxyPort;
                proxyAddress[1] = crtProxy;
            }
            return crtProxy;
        }
        return null;
    }

    void refreshRemovedNodesMenu() {

        Set<String> list = new TreeSet<String>();
        if (monitor.removedNodes != null) {
            for (final String rNode : monitor.removedNodes.values()) {
                list.add(rNode);
            }
        }
        String l[] = list.toArray(new String[0]);
        removedNodesListDialog = ListDialog.initialize(this, l, "Removed nodes", list.size() + " removed nodes");
        removedNodesListDialog.setLocationRelativeTo(this);
        removedNodesListDialog.setLocation(100, 100);
    }

    private final static class ListDialog extends JDialog {

        private static ListDialog dialog = null;

        private static String value = "";

        private final JList list;

        private static JLabel label;

        /**
         * Set up the dialog. The first argument can be null, but it really should be a component in the dialog's
         * controlling frame.
         */
        public static JDialog initialize(Component comp, String[] possibleValues, String title, String labelText) {
            Frame frame = JOptionPane.getFrameForComponent(comp);
            if (dialog == null) {
                dialog = new ListDialog(frame, possibleValues, title, labelText);
            } else {
                dialog.redoList(possibleValues);
            }
            label.setText(labelText);
            return dialog;
        }

        private ListDialog(Frame frame, Object[] data, String title, String labelText) {
            super(frame, title, true);
            // buttons
            JButton okButton = new JButton("OK");
            okButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    ListDialog.dialog.setVisible(false);
                }
            });
            // main part of the dialog
            list = new JList(new DefaultListModel());
            redoList(data);
            list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            JScrollPane listScroller = new JScrollPane(list);
            listScroller.setPreferredSize(new Dimension(250, 80));
            // XXX: Must do the following, too, or else the scroller thinks
            // XXX: it's taller than it is:
            listScroller.setMinimumSize(new Dimension(250, 80));
            listScroller.setAlignmentX(LEFT_ALIGNMENT);
            // Create a container so that we can add a title around
            // the scroll pane. Can't add a title directly to the
            // scroll pane because its background would be white.
            // Lay out the label and scroll pane from top to button.
            JPanel listPane = new JPanel();
            listPane.setLayout(new BoxLayout(listPane, BoxLayout.Y_AXIS));
            label = new JLabel(labelText);
            label.setLabelFor(list);
            listPane.add(label);
            listPane.add(Box.createRigidArea(new Dimension(0, 5)));
            listPane.add(listScroller);
            listPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            // Lay out the buttons from left to right.
            JPanel buttonPane = new JPanel();
            buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.X_AXIS));
            buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            buttonPane.add(Box.createHorizontalGlue());
            buttonPane.add(okButton);
            // Put everything together, using the content pane's BorderLayout.
            Container contentPane = getContentPane();
            contentPane.add(listPane, BorderLayout.CENTER);
            contentPane.add(buttonPane, BorderLayout.SOUTH);
            pack();
        }

        public void redoList(Object possibleValues[]) {
            if (list == null) {
                return;
            }
            ((DefaultListModel) list.getModel()).clear();
            if ((possibleValues == null) || (possibleValues.length == 0)) {
                return;
            }
            for (Object possibleValue : possibleValues) {
                ((DefaultListModel) list.getModel()).addElement(possibleValue);
            }
        }

    }

    void refreshProxyMenu() {

        Vector existingProxies = new Vector();
        Vector oldProxies = new Vector();
        for (int i = 0; i < proxyMenu.getMenuComponentCount(); i++) {
            JRadioButtonMenuItem mi = (JRadioButtonMenuItem) proxyMenu.getMenuComponent(i);
            existingProxies.add(mi);
            oldProxies.add(mi);
        }

        String[] proxyAddress = new String[2];
        getProxyMenuItem(monitor.proxyService, proxyAddress);
        String crtProxyIp = proxyAddress[0];
        // String crtProxyName = proxyAddress[1];
        getProxyMenuItem(monitor.getNextProxy(), proxyAddress);
        String nxtProxyIp = proxyAddress[0];
        // String nxtProxyName = proxyAddress[1];
        // String nextProxy = getProxyMenuItem(monitor.getNextProxy());

        ServiceItem[] sip = MLLUSHelper.getInstance().getProxies();

        if ((sip != null) && (sip.length > 0)) {
            for (ServiceItem si : sip) {
                if (si == null) {
                    continue;
                }
                Entry[] proxyEntry = si.attributeSets;
                if (proxyEntry == null) {
                    continue;
                }
                if (proxyEntry.length > 0) {
                    getProxyMenuItem(si, proxyAddress);
                    final String proxyItemIp = proxyAddress[0];
                    String proxyItemName = proxyAddress[1];
                    if (proxyItemIp == null) {
                        continue;
                    }
                    boolean selected = (crtProxyIp != null ? proxyItemIp.equals(crtProxyIp) : false);
                    boolean bNextProxy = ((nxtProxyIp != null) && proxyItemIp.equals(nxtProxyIp)) ? true : false;
                    // System.out.println("proxy @ "+proxyItem+" "+selected);
                    // ensure that ip:port menuItem exists in proxyMenu and has
                    // the appropriate selection
                    boolean found = false;
                    for (int j = 0; j < existingProxies.size(); j++) {
                        JRadioButtonMenuItem mi = (JRadioButtonMenuItem) existingProxies.get(j);
                        if (proxyItemIp.equals(mi.getText()) || proxyItemName.equals(mi.getText())) {
                            mi.setText(proxyItemName);
                            mi.setSelected(selected);
                            if (bNextProxy) {
                                mi.setBackground(Color.LIGHT_GRAY);
                            } else {
                                mi.setBackground(getBackground());
                            }
                            for (int k = 0; k < oldProxies.size(); k++) {
                                JRadioButtonMenuItem old = (JRadioButtonMenuItem) existingProxies.get(k);
                                if (proxyItemIp.equals(old.getText()) || proxyItemName.equals(old.getText())) {
                                    oldProxies.remove(k);
                                    k--;
                                }
                            }
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        final JRadioButtonMenuItem mi = new JRadioButtonMenuItem((proxyItemName == null ? proxyItemIp
                                : proxyItemName), selected);
                        proxyMenu.add(mi);
                        existingProxies.add(mi);
                        // System.out.println("Adding it");
                        mi.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent ae) {
                                try {
                                    String[] proxyAddress = new String[2];
                                    getProxyMenuItem(monitor.proxyService, proxyAddress);
                                    if (!proxyItemIp.equals(proxyAddress[0])) {
                                        ServiceItem[] sip = MLLUSHelper.getInstance().getProxies();
                                        if ((sip != null) && (sip.length > 0)) {
                                            for (final ServiceItem si : sip) {
                                                getProxyMenuItem(si, proxyAddress);
                                                if (proxyItemIp.equals(proxyAddress[0])) {
                                                    // Component[] items = proxyMenu.getMenuComponents();
                                                    // for (int j = 0; j < items.length; j++) {
                                                    // JRadioButtonMenuItem mi = (JRadioButtonMenuItem) items[j];
                                                    // if ( mi.isSelected() ) {
                                                    // mi.setSelected(false);
                                                    // break;
                                                    // };
                                                    // }
                                                    Timer t = new Timer();
                                                    t.schedule(new TimerTask() {

                                                        @Override
                                                        public void run() {
                                                            Thread.currentThread()
                                                                    .setName(
                                                                            " ( ML ) - Client - Proxy Menu change proxy thread");
                                                            monitor.addProxyToGet(si);
                                                            refreshProxyMenu();
                                                            proxyMenu.revalidate();
                                                        }
                                                    }, 0);
                                                    break;
                                                }
                                                ;
                                            }
                                        }
                                    } else {
                                        // System.out.println("Not changing proxy");
                                        mi.setSelected(true);
                                    }
                                } catch (Exception ex) {
                                    logger.log(Level.WARNING, "Error changing proxy ", ex);
                                }
                            }
                        });// end action listener
                    }
                }
            }
        }
        // delete proxies that don't exist anymore
        while (oldProxies.size() > 0) {
            proxyMenu.remove((JRadioButtonMenuItem) oldProxies.remove(0));
        }
    }

    /**
     * updates the menu list of groups<br>
     * it uses the monitor.SGroups array.<br>
     * It inserts the new groups in case insensitive order
     */
    public void updateGroups() {
        if (groupMenu == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : monitor.SGroups.entrySet()) {
            final String group = entry.getKey();
            boolean selected = entry.getValue().intValue() != 0;
            boolean appendIt = true;
            int n = groupMenu.getItemCount();
            int i = nGroupMenuStartPos;

            for (; i < n; i++) {
                JMenuItem groupMI = groupMenu.getItem(i);
                if (groupMI != null) {
                    String miGroup = groupMI.getText();
                    if (miGroup.equals(group)) {
                        groupMI.setSelected(selected);
                        appendIt = false;
                        break;
                    } else if (miGroup.compareToIgnoreCase(group) > 0) {
                        // JCheckBoxMenuItem newMI = new JCheckBoxMenuItem(group, !selected);
                        // newMI.addItemListener(this);
                        // groupMenu.add(newMI, i);
                        // newMI.doClick();
                        // appendIt = false;
                        break;
                    }
                }
            }

            if (appendIt) {
                JCheckBoxMenuItem newMI = new JCheckBoxMenuItem(group, selected);
                newMI.addItemListener(this);
                if (i < n) {
                    groupMenu.add(newMI, i);
                } else {
                    groupMenu.add(newMI);
                }
            }
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object source = e.getItemSelectable();

        if (source instanceof JCheckBoxMenuItem) {
            JCheckBoxMenuItem cb = (JCheckBoxMenuItem) source;
            synchronized (rangeOfGroups) {
                if (rangeOfGroups.contains(cb)) {
                    rangeOfGroups.remove(cb);
                    return;
                }
            }
            String mitx = cb.getText();
            Integer s;
            // System.out.println("checkbox clicked: "+mitx);
            if (cb.isSelected()) {
                s = Integer.valueOf(1);
                monitor.updateUserGroupPreferences(mitx, true);
            } else {
                s = Integer.valueOf(0);
                monitor.updateUserGroupPreferences(mitx, false);
            }
            monitor.SGroups.put(mitx, s);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Change " + mitx + " " + s);
            }
            monitor.GroupViewUpdate();
        }
    }

    public void showLocator() {

        ServiceRegistrar[] regs = monitor.lookupDiscoveryManager.getRegistrars();
        if ((regs == null) || (regs.length == 0)) {
            JOptionPane.showMessageDialog(this, "There are no Reggie services ", "Locator Error", 0);
            return;
        }

        StringBuilder sb = null;
        if (logger.isLoggable(Level.FINE)) {
            sb = new StringBuilder();
        }
        for (int i = 0; i < regs.length; i++) {
            if (logger.isLoggable(Level.FINE)) {
                sb.append(" i=" + i + "  " + regs[i] + "\n");
            }
        }

        if (logger.isLoggable(Level.FINE) && (sb != null)) {
            logger.log(Level.FINE, sb.toString());
        }

    }

    public void addLocator() {
        int port = 0;
        String input = JOptionPane.showInputDialog("Enter a Locator to discover host[:port]");
        if (input != null) {
            int portIndex = input.indexOf(":");
            if (portIndex == -1) {
                port = 4160;
            }
            if (input.startsWith("http://") || input.startsWith("jini://")) {
                String s = input.substring(0, 5);
                JOptionPane.showMessageDialog(this, "Remove the [" + s + "] and resubmit", "Locator Format Error", 0);
                return;
            }
            try {
                String host = portIndex != -1 ? input.substring(0, portIndex) : input;
                if (portIndex != -1) {
                    boolean portError = false;
                    String errorReason = null;
                    String p = input.substring(portIndex + 1, input.length());
                    try {
                        port = (Integer.valueOf(p)).intValue();
                    } catch (Throwable t) {
                        portError = true;
                        errorReason = "Not a valid number";
                    }
                    if ((port <= 0) || (port >= 0x10000)) {
                        portError = true;
                        errorReason = "port number out of range";
                    }
                    if (portError) {
                        JOptionPane.showMessageDialog(this, "The provided port is invalid : " + errorReason,
                                "Locator Port Error", 0);
                        return;
                    }
                }
                monitor.addLUS(host, port);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Exception trying to add Locator [" + e.getClass().getName() + "]",
                        "Locator Addition Error", 0);

            }
        }
    }

    public void removeLocator() {
        LookupLocator locators[] = monitor.lookupDiscoveryManager.getLocators();
        if ((locators == null) || (locators.length == 0)) {
            JOptionPane.showMessageDialog(this, "There are no locators to remove", "Zero Locator Error", 0);
            return;
        }
        LookupLocator selected = (LookupLocator) JOptionPane.showInputDialog(null, "Select a Locator to Remove",
                "Locator Removal Selector", 1, null, locators, locators[0]);
        if (selected == null) {
            return;
        }
        monitor.lookupDiscoveryManager.removeLocators(new LookupLocator[] { selected });
        return;
    }

    public void addGroup() {

        String group = JOptionPane.showInputDialog("Enter a Group to Discover");
        if (group != null) {
            // String groups[] = monitor.lookupDiscoveryManager.getGroups();
            // if(groups != null && groups.length > 0)
            // {
            // for(int i = 0; i < groups.length; i++)
            // if(groups[i].equals(group))
            // {
            // JOptionPane.showMessageDialog(this, "The [" +
            // group + "] group is already part of the discovery listener",
            // "Group Addition Error", 0);
            // return;
            // }
            //
            // }
            try {
                if (monitor.SGroups.containsKey(group)) {
                    JOptionPane.showMessageDialog(this, "The [" + group
                            + "] group is already part of the discovery listener", "Group Addition Error", 0);
                } else {
                    monitor.SGroups.put(group, Integer.valueOf(1));
                    // monitor.lookupDiscoveryManager.addGroups(new String[] {
                    // group});
                    updateGroups();
                    monitor.updateUserGroupPreferences(group, true);
                    // groups menu changed its selections, so the list of configurations for farms should be updated
                    // also
                    monitor.GroupViewUpdate();
                }
            } catch (Exception e) {
            }
        }
    }

    /**
     * adds a set of groups in Groups menu as unselected if they were not already there
     * 
     * @param groups
     */
    public void addGroups(String[] groups) {
        if ((groups == null) || (groups.length == 0)) {
            return;
        }
        try {
            int nAdded = 0;
            String bannedGroups = AppConfig.getProperty("lia.Monitor.group.ban", null);
            if (bannedGroups != null) {
                bannedGroups = "," + bannedGroups + ",";
            }
            for (int i = 0; i < groups.length; i++) {
                // if this group is banned, continue
                if ((bannedGroups != null) && (bannedGroups.indexOf("," + groups[i].trim() + ",") != -1)) {
                    continue;
                }
                if (!monitor.SGroups.containsKey(groups[i])) {
                    monitor.SGroups.put(groups[i], Integer.valueOf(0));
                    nAdded++;
                }
            }
            if (nAdded > 0) {
                updateGroups();
                // logger.info("MainBase > Service Ids vector received > "+nAdded+" groups added to menu.");
            }
        } catch (Exception e) {
        }
    }

    public void removeGroup() {
        // String groups[] = monitor.lookupDiscoveryManager.getGroups();
        // if(groups == null || groups.length == 0)
        final String groups[] = monitor.SGroups.keySet().toArray(new String[0]);
        if ((groups == null) || (groups.length == 0)) {
            JOptionPane.showMessageDialog(this, "There are no groups to remove", "Zero Group Error", 0);
            updateGroups();
            return;
        }
        String selected = (String) JOptionPane.showInputDialog(null, "Select a Group to Remove",
                "Group Removal Selector", 1, null, groups, groups[0]);
        if (selected == null) {
            updateGroups();
            return;
        }
        monitor.SGroups.remove(selected);
        // monitor.lookupDiscoveryManager.removeGroups(new String[] {
        // selected
        // });
        monitor.updateUserGroupPreferences(selected, false);

        boolean bWasSelected = false;
        for (int i = nGroupMenuStartPos; i < groupMenu.getItemCount(); i++) {
            JMenuItem mi = groupMenu.getItem(i);
            if ((mi != null) && (mi instanceof JCheckBoxMenuItem)) {
                // JCheckBoxMenuItem cmi = (JCheckBoxMenuItem) mi;
                String mitx = mi.getText();
                if (!monitor.SGroups.containsKey(mitx)) {
                    groupMenu.remove(mi);
                    // if this group that is selected is removed, then the proxy should know about it
                    if (mi.isSelected()) {
                        bWasSelected = true;
                    }
                }
            }
        }
        updateGroups();
        // groups menu changed its selections, so the list of configurations for farms should be updated also
        if (bWasSelected) {
            monitor.GroupViewUpdate();
        }

        return;
    }

    // protected Component makeTextPanel(String text) {
    // JPanel panel = new JPanel(false);
    // JLabel filler = new JLabel(text);
    // filler.setHorizontalAlignment(JLabel.CENTER);
    // panel.setLayout(new GridLayout(1, 1));
    // panel.add(filler);
    // return panel;
    // }

    public static ImageIcon loadIcon(String resource) {
        ImageIcon ico = null;
        ClassLoader myClassLoader = MainBase.class.getClassLoader();
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

    /** helper function to get the 3D View menu options */
    private boolean getBoolUserPref(String userPref, boolean def) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            def = prefs.getBoolean(userPref, def);
        } catch (Exception ex) {
            // ????
        }
        return def;
    }

    /** helper function to set the 3D View menu options */
    private void setBoolUserPref(String userPref, boolean value) {
        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            prefs.putBoolean(userPref, value);
        } catch (Exception ex) {
            // ????
        }
    }

    // public static void main(String s[]) {
    // MainBase res = new MainBase( );
    // }

}
