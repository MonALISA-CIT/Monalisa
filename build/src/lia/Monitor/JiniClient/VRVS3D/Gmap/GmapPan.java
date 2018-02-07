package lia.Monitor.JiniClient.VRVS3D.Gmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;
import net.jini.core.lookup.ServiceID;

public class GmapPan extends JPanel implements graphical, ActionListener, ChangeListener {

    /**
     * 
     */
    private static final long serialVersionUID = -1968234630010143714L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GmapPan.class.getName());

    JPanel cmdPan;
    JoptPan optPan;
    JPanel layoutPan;
    GraphPan grapPan;

    public SerMonitorBase monitor;
    Map<ServiceID, rcNode> nodes;
    Vector<rcNode> vnodes;

    JComboBox cbLayout;
    JCheckBox ckUsers;
    JSlider sldStiffness;
    JSlider sldRepulsion;
    JCheckBox ckShowOHN; // show only layout handled nodes
    JCheckBox ckShowORN; // show only reflector nodes

    JButton searchUserButton;

    //	JButton btnLayTree;
    //	JButton btnRadTree;
    //	JButton btnGrid;
    //	JButton btnRandom;
    //	JToggleButton btnElastic;
    //	JToggleButton btnElaTree;
    //	JRadioButton btnUsePeers;
    //	JRadioButton btnUseMST;

    //	Timer timer;
    //	int paintCount;

    public GmapPan() {
        cmdPan = new JPanel();

        optPan = new JoptPan(this);//cmdPan);
        optPan.csNodes.setColors(Color.CYAN, Color.CYAN); // cyan(min) --> blue(max)
        optPan.csNodes.setValues(0, 0);
        optPan.csPeers.setColors(Color.RED, Color.GREEN); // red(min) --> green(max)
        optPan.csPeers.setLabelFormat("###.##", "%");
        optPan.csPeers.setValues(0, 100);
        optPan.csPing.setColors(Color.RED, Color.RED); // red(min) --> yellow(max)
        optPan.csPing.setLabelFormat("###.##", "");
        optPan.csPing.setValues(0, 0);
        optPan.csMST.setColors(Color.BLUE, Color.BLUE); // blue
        optPan.csMST.setValues(0, 0);
        optPan.csMST.setLabelFormat("", "");
        optPan.cbNodeOpts.addActionListener(this);
        optPan.cbPeerOpts.addActionListener(this);
        optPan.kbShowMST.addActionListener(this);
        optPan.kbShowPeers.addActionListener(this);
        optPan.kbShowPing.addActionListener(this);

        grapPan = new GraphPan(this);
        cmdPan.setLayout(new BoxLayout(cmdPan, BoxLayout.Y_AXIS));
        cmdPan.add(optPan);

        buildLayoutPan();
        cmdPan.add(layoutPan);

        setLayout(new BorderLayout());

        add(cmdPan, BorderLayout.NORTH);
        add(grapPan, BorderLayout.CENTER);
        setBackground(Color.white);

        //		Timer timer = new Timer();
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - VRVS3D Gmap - GmapPan setElastic layout Timer Thread");
                try {
                    if (grapPan.currentLayout.equals("Elastic")) {
                        grapPan.setLayoutType(grapPan.currentLayout);
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Error executing", t);
                }
                //				dumpThreads();
                //				System.out.println("Threads active="+Thread.activeCount() 
                //					+" Free mem="+Runtime.getRuntime().freeMemory()
                //					+" Total mem="+Runtime.getRuntime().totalMemory());
                repaint();
            }
        };
        BackgroundWorker.schedule(ttask, 2 * 1000, 2 * 1000);

    }

    private void buildLayoutPan() {
        Font f = new Font("Arial", Font.BOLD, 10);
        Dimension dimLayoutPan = new Dimension(870, 14);
        Dimension dimSlider = new Dimension(20, 14);

        layoutPan = new JPanel();
        layoutPan.setLayout(new BoxLayout(layoutPan, BoxLayout.X_AXIS));
        JLabel lblLayout = new JLabel(" Select layout: ");
        lblLayout.setFont(f);
        layoutPan.add(lblLayout);
        cbLayout = new JComboBox(new String[] { "None", "Random", "Grid", "Radial", "Layered", "Elastic" });
        cbLayout.setFont(f);
        cbLayout.setPreferredSize(dimSlider);
        cbLayout.addActionListener(this);
        layoutPan.add(cbLayout);

        ckUsers = new JCheckBox("Users", true);
        ckUsers.setActionCommand("showUsers");
        ckUsers.setFont(f);
        //		ckUsers.setPreferredSize(ckSlider);
        ckUsers.addActionListener(this);
        layoutPan.add(ckUsers);

        JLabel lblStiff = new JLabel(" Stiffness:");
        lblStiff.setFont(f);
        layoutPan.add(lblStiff);
        sldStiffness = new JSlider(1, 100, 10);
        sldStiffness.addChangeListener(this);
        sldStiffness.setMaximumSize(dimSlider);
        sldStiffness.setEnabled(false);
        lblStiff.setLabelFor(sldStiffness);
        layoutPan.add(sldStiffness);
        JLabel lblRepulsion = new JLabel(" Repulsion:");
        lblRepulsion.setFont(f);
        layoutPan.add(lblRepulsion);
        sldRepulsion = new JSlider(1, 100, 40);
        sldRepulsion.addChangeListener(this);
        sldRepulsion.setMaximumSize(dimSlider);
        sldRepulsion.setEnabled(false);
        lblRepulsion.setLabelFor(sldRepulsion);
        layoutPan.add(sldRepulsion);

        searchUserButton = new JButton("Search");
        searchUserButton.setHorizontalTextPosition(JButton.LEFT);
        searchUserButton.setIconTextGap(10);
        searchUserButton.setMargin(new Insets(1, 1, 1, 1));
        searchUserButton.setFont(searchUserButton.getFont().deriveFont(8.0f));
        searchUserButton.setActionCommand("searchUser");
        searchUserButton.addActionListener(this);

        layoutPan.add(searchUserButton);

        Dimension dkb = new Dimension(170, 14);
        JPanel layoutPan2 = new JPanel();
        layoutPan2.setLayout(new BoxLayout(layoutPan2, BoxLayout.Y_AXIS));
        ckShowOHN = new JCheckBox("Only Layout-handled nodes", false);
        ckShowOHN.setActionCommand("showOHN");
        ckShowOHN.setFont(f);
        ckShowOHN.setPreferredSize(dkb);
        ckShowOHN.addActionListener(this);
        layoutPan2.add(ckShowOHN);
        ckShowORN = new JCheckBox("Only reflector nodes", false);
        ckShowORN.setActionCommand("showORN");
        ckShowORN.setFont(f);
        ckShowORN.setPreferredSize(dkb);
        ckShowORN.addActionListener(this);
        layoutPan2.add(ckShowORN);
        layoutPan.add(layoutPan2);

        //set current mode <> none
        cbLayout.setSelectedItem("Elastic");
    };

    private void setProp(JComponent c, Dimension d, Font f) {
        c.setFont(f);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
        c.setMinimumSize(d);
    }

    @Override
    public void new_global_param(String mod) {
        // empty
    }

    @Override
    public void updateNode(rcNode node) {
        // empty 
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
        this.nodes = nodes;
        this.vnodes = vnodes;
        //grapPan.setNodes(nodes, vnodes);
        grapPan.setNodesTab(nodes);
    }

    @Override
    public void gupdate() {
        // empty
    }

    @Override
    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
        grapPan.setSerMonitor(monitor);
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
        // empty
    }

    private void stopElastic() {
        if (cbLayout.getSelectedItem().equals("Elastic")) {
            cbLayout.setSelectedIndex(0);
            grapPan.currentLayout = "None";
            if (grapPan.layout != null) {
                grapPan.layout.finish();
            }
            grapPan.vGraph = null;
            grapPan.layout = null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        String cmd = (src.equals(cbLayout) ? (String) cbLayout.getSelectedItem() : e.getActionCommand());
        //System.out.println("Command -> "+ cmd);
        if (cmd.equals("Elastic") || cmd.equals("Random") || cmd.equals("Grid") || cmd.equals("Layered")
                || cmd.equals("Radial")) {
            // enable/disable sliders
            if (cmd.equals("Elastic")) {
                sldStiffness.setEnabled(true);
                sldRepulsion.setEnabled(true);
            } else {
                sldStiffness.setEnabled(false);
                sldRepulsion.setEnabled(false);
                stopElastic();
            }
            grapPan.setLayoutType(cmd);
            return;
        } else if (cmd.equals("None")) {
            grapPan.setLayoutType(cmd);
            for (final rcNode n : nodes.values()) {
                n.isLayoutHandled = true;
            }
            return;
        } else if (cmd.equals("showOHN")) {
            grapPan.showOnlyHandledNodes = ((JCheckBox) src).isSelected();
            grapPan.repaint();
            return;
        } else if (cmd.equals("showORN")) {
            grapPan.showOnlyReflectorNodes = ((JCheckBox) src).isSelected();
            grapPan.repaint();
            return;
        } else if (src.equals(optPan.kbShowMST) || src.equals(optPan.kbShowPeers) || src.equals(optPan.kbShowPing)
                || src.equals(optPan.cbNodeOpts) || src.equals(optPan.cbPeerOpts)) {
            grapPan.setLayoutType((String) cbLayout.getSelectedItem());
            grapPan.repaint();
            return;
        } else if (cmd.equals("MeetingsMenuItemChanged")) {
            grapPan.repaint();
            return;
        }
        if (cmd.equals("showUsers")) {
            grapPan.showFullUserInfo = ((JCheckBox) src).isSelected();
            grapPan.repaint();
            return;
        }
        if (cmd.equals("searchUser")) {
            grapPan.showSearchPopup();
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
}
