package lia.Monitor.JiniClient.Farms.OSGmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.Farms.OSGmap.Config.OSMonitorControl;
import lia.Monitor.JiniClient.Farms.OSGmap.Config.RemoveLinksDialog;
import lia.Monitor.monitor.OSLinkLegend;
import net.jini.core.lookup.ServiceID;

public class OSGmapPan extends JPanel implements graphical, ActionListener, ChangeListener, ComponentListener {

    /**
     * 
     */
    private static final long serialVersionUID = -8273192645608839338L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSGmapPan.class.getName());

    JPanel cmdPan;
    public OSGraphPan grapPan;
    SerMonitorBase monitor;
    volatile Map<ServiceID, rcNode> nodes;

    public JCheckBox kbShowOS;
    public JCheckBox kbMakeNice;
    public JCheckBox kbShowShadow;

    public JButton createPathButton;
    public JButton destroyPathButton;
    public RemoveLinksDialog removeLinksDialog;
    public OSMonitorControl currentControl = null;

    public JPanel selectPan;
    public JPanel linkSelectPan;
    public JButton removeLink;
    public JButton detailsLink;

    // layout stuff
    JComboBox cbLayout;
    JSlider sldStiffness;
    JSlider sldRepulsion;
    JCheckBox ckShowOCN; // show only connected nodes

    public JLabel currentPath = null;

    private static OSGmapPan _instance = null;

    private OSGmapPan() {
        ginit();
    }

    public static synchronized OSGmapPan getInstance() {
        if (_instance == null) {
            _instance = new OSGmapPan();
        }
        return _instance;
    }

    void ginit() {

        cmdPan = new JPanel();

        Font f = new Font("Arial", Font.BOLD, 10);

        kbShowOS = new JCheckBox("OS show links", true);
        kbShowOS.setToolTipText("OS show links");
        kbMakeNice = new JCheckBox("Nicer", true);
        kbMakeNice.setToolTipText("Nicer");
        kbShowShadow = new JCheckBox("Shadow", false);
        kbShowShadow.setToolTipText("Shadow");

        kbShowOS.setFont(f);
        kbMakeNice.setFont(f);
        kbShowShadow.setFont(f);

        kbShowOS.addActionListener(this);
        kbMakeNice.addActionListener(this);
        kbShowShadow.addActionListener(this);

        grapPan = OSGraphPan.getInstance(this);
        cmdPan.setLayout(new BoxLayout(cmdPan, BoxLayout.X_AXIS));

        JPanel p1 = new JPanel();
        p1.setLayout(new BoxLayout(p1, BoxLayout.Y_AXIS));
        p1.add(kbShowShadow);
        p1.add(kbShowOS);
        cmdPan.add(p1);

        JButton but = new JButton("Legend");
        but.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (monitor != null) {
                    OSLinkLegend.show(monitor.main);
                }
            }
        });
        setProp(but, new Dimension(80, 16), f);

        JPanel p2 = new JPanel();
        p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));
        p2.add(kbMakeNice);
        p2.add(but);
        cmdPan.add(p2);
        cmdPan.add(Box.createHorizontalStrut(3));

        JPanel slidersPan = new JPanel();
        slidersPan.setLayout(new BoxLayout(slidersPan, BoxLayout.X_AXIS));

        JPanel layoutCbPan = new JPanel();
        layoutCbPan.setLayout(new BoxLayout(layoutCbPan, BoxLayout.X_AXIS));

        JPanel layoutPan = new JPanel();
        layoutPan.setLayout(new BoxLayout(layoutPan, BoxLayout.Y_AXIS));

        layoutPan.add(slidersPan);
        layoutPan.add(layoutCbPan);

        Dimension dimLayoutPan = new Dimension(350, 30);
        Dimension dimSingleLayoutPan = new Dimension(350, 14);

        setProp(slidersPan, dimSingleLayoutPan, f);
        setProp(layoutCbPan, dimSingleLayoutPan, f);
        setProp(layoutPan, dimLayoutPan, f);

        Dimension dimSlider = new Dimension(40, 14);
        JLabel lblStiff = new JLabel(" Stiffness:");
        lblStiff.setFont(f);
        slidersPan.add(lblStiff);
        sldStiffness = new JSlider(1, 20, 10);//100, 10);
        sldStiffness.addChangeListener(this);
        sldStiffness.setMaximumSize(dimSlider);
        sldStiffness.setEnabled(false);
        lblStiff.setLabelFor(sldStiffness);
        slidersPan.add(sldStiffness);
        JLabel lblRepulsion = new JLabel(" Repulsion:");
        lblRepulsion.setFont(f);
        slidersPan.add(lblRepulsion);
        sldRepulsion = new JSlider(1, 200, 100);//100, 40);
        sldRepulsion.addChangeListener(this);
        sldRepulsion.setMaximumSize(dimSlider);
        sldRepulsion.setEnabled(false);
        lblRepulsion.setLabelFor(sldRepulsion);
        slidersPan.add(sldRepulsion);

        JLabel lblLayout = new JLabel(" Select layout: ");
        lblLayout.setFont(f);
        layoutCbPan.add(lblLayout);
        cbLayout = new JComboBox(new String[] { "None", "Random", "Grid", "Radial", "Layered", "Map", "Elastic" });
        cbLayout.setFont(new Font("Arial", Font.BOLD, 9));
        cbLayout.setPreferredSize(dimSlider);
        cbLayout.addActionListener(this);
        layoutCbPan.add(cbLayout);
        ckShowOCN = new JCheckBox("Only connected nodes", false);
        ckShowOCN.setActionCommand("showOCN");
        ckShowOCN.setFont(f);
        ckShowOCN.addActionListener(this);
        layoutCbPan.add(ckShowOCN);

        cmdPan.add(layoutPan);

        cmdPan.add(new JLabel("  ")); //"    Show : "));

        ButtonGroup bg = new ButtonGroup();
        JPanel pTooltipModeSelect = new JPanel();

        Dimension dpan = new Dimension(140, 30);
        Dimension drb = new Dimension(140, 14);

        pTooltipModeSelect.setLayout(new GridLayout(0, 1));
        setProp(pTooltipModeSelect, dpan, f);

        /**
         * creates two radio buttons to change the way port connections 
         * are shown for optical switch's tooltip<br>
         * select wired connections as fist way of showing tooltip
         */
        JRadioButton radioBut;
        radioBut = new JRadioButton("Wired connections");
        radioBut.setActionCommand("changeTooltipMode2Wired");
        radioBut.addActionListener(grapPan);
        setProp(radioBut, drb, f);
        bg.add(radioBut);
        pTooltipModeSelect.add(radioBut);
        radioBut = new JRadioButton("Port connections");
        radioBut.setActionCommand("changeTooltipMode2Port");
        radioBut.addActionListener(grapPan);
        setProp(radioBut, drb, f);
        radioBut.setSelected(true);
        grapPan.OSConToolTip.switchPanel(false);
        bg.add(radioBut);
        pTooltipModeSelect.add(radioBut);

        cmdPan.add(pTooltipModeSelect);

        JPanel pathPanel = new JPanel();
        pathPanel.setOpaque(false);
        pathPanel.setLayout(new BoxLayout(pathPanel, BoxLayout.Y_AXIS));
        createPathButton = new JButton("Create optical path");
        createPathButton.addActionListener(this);
        createPathButton.setEnabled(false);
        setProp(createPathButton, new Dimension(200, 16), f);
        pathPanel.add(createPathButton);
        pathPanel.add(Box.createVerticalStrut(2));
        destroyPathButton = new JButton("Remove optical path");
        destroyPathButton.addActionListener(this);
        destroyPathButton.setEnabled(false);
        setProp(destroyPathButton, new Dimension(200, 16), f);
        pathPanel.add(destroyPathButton);

        cmdPan.add(pathPanel);

        setLayout(new BorderLayout());

        add(cmdPan, BorderLayout.NORTH);
        add(grapPan, BorderLayout.CENTER);
        setBackground(Color.white);

        addComponentListener(this);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("ML (OSGmapPan) TimerTask");
                grapPan.syncNodes();
            }
        };
        BackgroundWorker.schedule(task, 10000, 4000);

        createSelectPan();
        createLinkSelectPan();
        //cbLayout.setSelectedItem("Map");        
    }

    private void createSelectPan() {

        JPanel buttPan = new JPanel();
        buttPan.setLayout(new FlowLayout());
        Font f = new Font("Arial", Font.BOLD, 10);
        JButton cancel = new JButton("Cancel");
        cancel.setFont(f);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enterCmd();
                grapPan.selectedNodes.clear();
            }
        });
        JButton ok = new JButton("Done");
        ok.setFont(f);
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        enterCmd();
                        if (currentControl != null) {
                            OSMonitorControl c = grapPan.getAddFirstMonitor();
                            if (c != null) {
                                currentControl = c;
                            }
                            currentControl.createPath(grapPan.allPathMode, grapPan.singleEndPointsMode,
                                    grapPan.multipleEndPointsMode, grapPan.selectedNodes, grapPan.fdxPath);
                        }
                        grapPan.selectedNodes.clear();
                    }
                }).start();
            }
        });
        final String[] selectionModes = { "Select all path", "Select single end-points", "Select multiple end-points" };
        final JComboBox selectionMode = new JComboBox(selectionModes);
        selectionMode.setFont(f);
        selectionMode.setSelectedIndex(1);
        selectionMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String item = (String) selectionMode.getSelectedItem();
                if (item.equals(selectionModes[0])) {
                    grapPan.setAllPathMode();
                } else if (item.equals(selectionModes[1])) {
                    grapPan.setSingleEndPointsMode();
                } else if (item.equals(selectionModes[2])) {
                    grapPan.setMultipleEndPointsMode();
                }
            }
        });
        selectionMode.setEnabled(false);

        JPanel fdxPan = new JPanel();
        fdxPan.setLayout(new FlowLayout());
        JCheckBox fdxCheck = new JCheckBox("FDX");
        fdxCheck.setFont(f);
        fdxCheck.setMnemonic(KeyEvent.VK_F);
        fdxCheck.setSelected(true);
        fdxCheck.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.DESELECTED) {
                    grapPan.fdxPath = false;
                } else {
                    grapPan.fdxPath = true;
                }
                grapPan.selectedNodes.clear();
                grapPan.redoSelectedPath();
                grapPan.repaint();
            }
        });

        buttPan.add(fdxCheck);
        buttPan.add(selectionMode);
        buttPan.add(cancel);
        buttPan.add(ok);

        JPanel sellPan = new JPanel();
        sellPan.setLayout(new FlowLayout());
        currentPath = new JLabel("");
        currentPath.setFont(f);
        sellPan.add(currentPath);

        selectPan = new JPanel();
        Dimension d = new Dimension(350, 60);
        selectPan.setFont(f);
        selectPan.setPreferredSize(d);
        selectPan.setMaximumSize(d);
        selectPan.setMinimumSize(d);
        selectPan.setLayout(new BoxLayout(selectPan, BoxLayout.Y_AXIS));
        selectPan.add(buttPan);
        selectPan.add(sellPan);
    }

    public void createLinkSelectPan() {

        JPanel butPan = new JPanel();
        butPan.setLayout(new FlowLayout());
        Font f = new Font("Arial", Font.BOLD, 10);
        JButton ok = new JButton("Done");
        ok.setFont(f);
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enterCmd();
                grapPan.linkSelected = null;
            }
        });
        removeLink = new JButton("Remove");
        removeLink.setFont(f);
        removeLink.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if ((currentControl != null) && (grapPan.linkSelected != null)) {
                            OSMonitorControl c = grapPan.getDelFirstMonitor();
                            if (c != null) {
                                currentControl = c;
                            }
                            if (!currentControl.deletePath(grapPan.linkSelected)) {
                                JOptionPane.showMessageDialog(grapPan, "The link was not deleted");
                            } else {
                                removeLink.setEnabled(false);
                                detailsLink.setEnabled(false);
                                grapPan.linkSelected = null;
                                grapPan.linkSelectedDetails = null;
                                grapPan.participatingRCNodes.clear();
                                grapPan.repaint();
                            }
                        }
                    }
                }).start();
            }
        });
        removeLink.setEnabled(false);
        detailsLink = new JButton("Details for the selected link");
        detailsLink.setFont(f);
        detailsLink.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (grapPan.linkSelectedDetails != null) {
                    JOptionPane.showMessageDialog(grapPan, grapPan.linkSelectedDetails);
                }
            }
        });
        detailsLink.setEnabled(false);
        butPan.add(ok);
        butPan.add(removeLink);
        butPan.add(detailsLink);

        linkSelectPan = new JPanel();
        Dimension d = new Dimension(350, 30);
        linkSelectPan.setFont(f);
        linkSelectPan.setPreferredSize(d);
        linkSelectPan.setMaximumSize(d);
        linkSelectPan.setMinimumSize(d);
        linkSelectPan.setLayout(new BorderLayout());
        linkSelectPan.add(butPan, BorderLayout.CENTER);
    }

    public void enterSelect() {

        grapPan.OSConToolTip.hidePopup();
        grapPan.OSConToolTip.setNode(null);
        if (grapPan.linkSelectionMode) {
            remove(linkSelectPan);
            add(selectPan, BorderLayout.NORTH);
        } else if (!grapPan.selectionMode) {
            remove(cmdPan);
            add(selectPan, BorderLayout.NORTH);
        }
        grapPan.linkSelectionMode = false;
        grapPan.selectionMode = true;
        grapPan.participatingRCNodes.clear();
        revalidate();
        repaint();
    }

    public void enterLinkSelect() {

        grapPan.OSConToolTip.hidePopup();
        grapPan.OSConToolTip.setNode(null);
        if (grapPan.selectionMode) {
            remove(selectPan);
            add(linkSelectPan, BorderLayout.NORTH);
        } else if (!grapPan.linkSelectionMode) {
            remove(cmdPan);
            add(linkSelectPan, BorderLayout.NORTH);
        }
        grapPan.selectionMode = false;
        grapPan.linkSelectionMode = true;
        grapPan.participatingRCNodes.clear();
        revalidate();
        repaint();
    }

    public void enterCmd() {

        if (grapPan.selectionMode) {
            remove(selectPan);
            add(cmdPan, BorderLayout.NORTH);
        } else if (grapPan.linkSelectionMode) {
            remove(linkSelectPan);
            add(cmdPan, BorderLayout.NORTH);
        }
        grapPan.selectionMode = false;
        grapPan.linkSelectionMode = false;
        grapPan.participatingRCNodes.clear();
        revalidate();
        repaint();
    }

    public Hashtable getLinks() {
        return grapPan.getLinks();
    }

    private void setProp(JComponent c, Dimension d, Font f) {
        c.setFont(f);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
        c.setMinimumSize(d);
    }

    @Override
    public void new_global_param(String mod) {
    }

    @Override
    public void updateNode(rcNode node) {
        //   grapPan.updateNode( node ) ; 
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
        this.nodes = nodes;
        grapPan.setNodesTab(nodes);
    }

    @Override
    public void gupdate() {
        // empty
        this.grapPan.repaint();
    }

    @Override
    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
        grapPan.setSerMonitor(monitor);
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
        grapPan.setMaxFlowData(n, v);
    }

    class ComboBoxRenderer extends JLabel implements ListCellRenderer {
        public ComboBoxRenderer() {
            setOpaque(true);
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            //ImageIcon icon = (ImageIcon)value;
            setText((String) value);
            // setIcon(icon);
            return this;
        }
    }

    private void stopElastic() {
        if (cbLayout.getSelectedItem().equals("Elastic")) {
            synchronized (grapPan.syncGraphObj) {
                cbLayout.setSelectedIndex(0);
                grapPan.currentLayout = "None";
                if (grapPan.layout != null) {
                    grapPan.layout.finish();
                }
                grapPan.vGraph = null;
                grapPan.layout = null;
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        String cmd = (src == cbLayout ? (String) cbLayout.getSelectedItem() : e.getActionCommand());
        //System.out.println("Command -> "+ cmd);
        if (cmd.equals("Elastic") || cmd.equals("Random") || cmd.equals("Grid") || cmd.equals("Layered")
                || cmd.equals("Radial") || cmd.equals("Map")) {
            // enable/disable sliders
            if (cmd.equals("Elastic")) {
                sldStiffness.setEnabled(true);
                sldRepulsion.setEnabled(true);
            } else {
                sldStiffness.setEnabled(false);
                sldRepulsion.setEnabled(false);
                stopElastic();
            }
            grapPan.currentTransformCancelled = true;
            grapPan.setLayoutType(cmd);
        } else if (cmd.equals("None")) {
            stopElastic();
            grapPan.currentTransformCancelled = true;
            grapPan.setLayoutType(cmd);
        } else if (cmd.equals("showOCN")) {
            grapPan.showOnlyConnectedNodes = ((JCheckBox) src).isSelected();
            grapPan.currentTransformCancelled = true;
            grapPan.setLayoutType((String) cbLayout.getSelectedItem());
        }

        if (src.equals(createPathButton)) {
            enterSelect();
            return;
        }

        if (src.equals(destroyPathButton)) {
            Hashtable links = getLinks();
            if ((links == null) || (links.size() == 0)) {
                logger.warning("Got no ml link to delete");
                return;
            }
            if ((monitor != null) && (monitor.main != null)) {
                removeLinksDialog = new RemoveLinksDialog(monitor.main, links, this);
            }
            removeLinksDialog = null;
            return;
        }

        if (e.getSource().equals(kbShowOS)) {
            grapPan.currentTransformCancelled = true;
            grapPan.setLayoutType((String) cbLayout.getSelectedItem());
            grapPan.repaint();
            return;
        }
        if (e.getSource().equals(kbMakeNice)) {
            grapPan.repaint();
            return;
        }
        if (e.getSource().equals(kbShowShadow)) {
            grapPan.repaint();
            return;
        }
    }

    /** slider values have changed -> update */
    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == sldStiffness) {
            if (grapPan.layout instanceof SpringLayoutAlgorithm) {
                ((SpringLayoutAlgorithm) grapPan.layout).setStiffness(sldStiffness.getValue());
            }
        } else if (e.getSource() == sldRepulsion) {
            if (grapPan.layout instanceof SpringLayoutAlgorithm) {
                ((SpringLayoutAlgorithm) grapPan.layout).setRespRange(sldRepulsion.getValue());
            }
        }

    }

    @Override
    public void setVisible(boolean bVisible) {
        if (bVisible) {
            if (!grapPan.currentLayout.equals("SomeLayout")) {
                grapPan.setLayoutType(grapPan.currentLayout);
                //			    System.out.println("set layout parent");
            }
            //			System.out.println("repaint parent");
            repaint();
        } else {
            if (grapPan.OSConToolTip.isShowing()) {
                grapPan.OSConToolTip.hidePopup();
                grapPan.OSConToolTip.setNode(null);
            }
        }
        super.setVisible(bVisible);
    }

    public void enterGUIRemoveLinks() {
        enterLinkSelect();
        return;
    }

    @Override
    public void componentResized(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {

        // make tooltips to appear faster and last longer
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        // default as 100 ms
        // set to 500 ms for optical switches consideration in osgmap
        ttm.setInitialDelay(100);
        ttm.setDismissDelay(30 * 1000);
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

}
