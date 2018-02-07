package lia.Monitor.JiniClient.Farms.CienaMap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import net.jini.core.lookup.ServiceID;

/**
 * Panel used to represent the CIENA switches, acording to lia.Monitor.ciena.osrp
 * @author cipsm
 *
 */
public class CienaMapPan extends JPanel implements graphical, ActionListener, ChangeListener, ComponentListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(CienaMapPan.class.getName());

    JPanel cmdPan;
    public CienaGraphPan grapPan;
    SerMonitorBase monitor;

    public JCheckBox kbShowOS;
    public JCheckBox kbMakeNice;
    public JCheckBox kbShowShadow;

    // layout stuff
    JComboBox cbLayout;
    JSlider sldStiffness;
    JSlider sldRepulsion;
    JCheckBox ckShowOCN; // show only connected nodes
    JCheckBox ckShowDCN; // show only DCN circuits

    JCheckBox ckShowOSRP; // show or not the OSRP links...

    CheckCombo swDropDown;
    CheckCombo sncDropDown;

    public CienaMapPan() {
        ginit();
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

        grapPan = CienaGraphPan.getInstance(this);
        cmdPan.setLayout(new BoxLayout(cmdPan, BoxLayout.X_AXIS));

        JPanel p1 = new JPanel();
        p1.setLayout(new BoxLayout(p1, BoxLayout.Y_AXIS));
        p1.add(kbShowShadow);
        p1.add(kbShowOS);
        cmdPan.add(p1);

        JPanel p2 = new JPanel();
        p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));
        p2.add(kbMakeNice);
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

        Dimension dimLayoutPan = new Dimension(450, 30);
        Dimension dimSingleLayoutPan = new Dimension(450, 14);

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

        JPanel pCircuitSelect = new JPanel() {
            Insets insets = new Insets(12, 3, 3, 3);

            @Override
            public Insets getInsets() {
                return insets;
            }
        };
        TitledBorder b = BorderFactory.createTitledBorder("Circuits");
        b.setTitleFont(f);
        pCircuitSelect.setBorder(b);

        Dimension dpan = new Dimension(420, 42);

        pCircuitSelect.setLayout(new GridLayout(1, 0));
        setProp(pCircuitSelect, dpan, f);

        swDropDown = new CheckCombo(this, 70);
        pCircuitSelect.add(swDropDown.getContent());

        sncDropDown = new CheckCombo(this, 110);
        pCircuitSelect.add(sncDropDown.getContent());

        ckShowDCN = new JCheckBox("Only DCN", false);
        ckShowDCN.setActionCommand("showDCN");
        ckShowDCN.setFont(f);
        ckShowDCN.addActionListener(this);
        pCircuitSelect.add(ckShowDCN);

        JLabel lblLayout = new JLabel(" Select layout: ");
        lblLayout.setFont(f);
        layoutCbPan.add(lblLayout);
        cbLayout = new JComboBox(new String[] { "None", "Random", "Grid", "Radial", "Layered", "Elastic" });
        cbLayout.setFont(new Font("Arial", Font.BOLD, 9));
        cbLayout.setPreferredSize(dimSlider);
        cbLayout.addActionListener(this);
        layoutCbPan.add(cbLayout);
        ckShowOCN = new JCheckBox("Only connected nodes", false);
        ckShowOCN.setActionCommand("showOCN");
        ckShowOCN.setFont(f);
        ckShowOCN.addActionListener(this);
        layoutCbPan.add(ckShowOCN);

        ckShowOSRP = new JCheckBox("Show OSRP", true);
        ckShowOSRP.setActionCommand("showOSRP");
        ckShowOSRP.setFont(f);
        ckShowOSRP.addActionListener(this);
        layoutCbPan.add(ckShowOSRP);

        cmdPan.add(layoutPan);

        cmdPan.add(new JLabel("  ")); //"    Show : "));

        cmdPan.add(pCircuitSelect);

        //		ButtonGroup bg = new ButtonGroup();
        //		JPanel pTooltipModeSelect = new JPanel();
        //		
        ////		Dimension dpan= new Dimension(140, 30);
        ////		Dimension drb = new Dimension(140, 14);
        //		
        //		pTooltipModeSelect.setLayout(new GridLayout(0, 1));
        //		setProp( pTooltipModeSelect, dpan, f);

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

        //		createSelectPan();
        //		createLinkSelectPan();
        //cbLayout.setSelectedItem("Map");        
    }

    public void comboChanged(CheckCombo instance) {
        if (instance.equals(swDropDown)) {
            grapPan.redoSNCs();
        }
        grapPan.repaint();
    }

    private void setProp(JComponent c, Dimension d, Font f) {
        c.setFont(f);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
        c.setMinimumSize(d);
    }

    @Override
    public void updateNode(rcNode node) {
    }

    @Override
    public void gupdate() {
        this.grapPan.repaint();
    }

    @Override
    public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
    }

    @Override
    public void setSerMonitor(SerMonitorBase ms) {
        this.monitor = ms;
        grapPan.setSerMonitor(ms);
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
    }

    @Override
    public void new_global_param(String name) {
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
        } else if (cmd.equals("showOSRP")) {
            if (grapPan != null) {
                boolean selected = ckShowOSRP.isSelected();
                swDropDown.setEnabled(!selected);
                sncDropDown.setEnabled(!selected);
                grapPan.setDrawOSRP(ckShowOSRP.isSelected());
            }
        } else if (cmd.equals("showDCN")) {
            grapPan.onlyDCN = ckShowDCN.isSelected();
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
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {
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

} // end of class CienaMapPan

