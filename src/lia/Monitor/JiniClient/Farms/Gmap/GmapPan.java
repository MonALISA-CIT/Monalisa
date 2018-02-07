package lia.Monitor.JiniClient.Farms.Gmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.MFilter2Constants;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;
import net.jini.core.lookup.ServiceID;

public class GmapPan extends JPanel implements graphical, ActionListener, ChangeListener {

    /**
     * 
     */
    private static final long serialVersionUID = -625050137963471811L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(GmapPan.class.getName());

    JPanel cmdPan;
    JoptPan optPan;
    GraphPan grapPan;
    SerMonitorBase monitor;
    JComboBox gparam;
    DefaultComboBoxModel combo;
    volatile Map<ServiceID, rcNode> nodes;

    static String[] farmPies = new String[] { "CPUPie", "IOPie", "LoadPie", "DiskPie" };

    // layout stuff
    JComboBox cbLayout;
    JSlider sldStiffness;
    JSlider sldRepulsion;
    JCheckBox ckShowOCN; // show only connected nodes

    private final Object syncGparamCombobox = new Object();

    public GmapPan() {
        ginit();
    }

    void ginit() {

        cmdPan = new JPanel();
        combo = new DefaultComboBoxModel();
        gparam = new JComboBox(combo);
        ComboBoxRenderer renderer = new ComboBoxRenderer();
        gparam.setRenderer(renderer);
        gparam.addActionListener(this);

        optPan = new JoptPan(cmdPan, this);
        optPan.csPing.setValues(0, 0);
        optPan.csPing.setColors(Color.GREEN, Color.GREEN);
        optPan.kbShowPing.addActionListener(this);
        //		optPan.kbShowOS.addActionListener(this);

        grapPan = new GraphPan(this);
        optPan.setGraphPan(grapPan);
        cmdPan.setLayout(new BoxLayout(cmdPan, BoxLayout.X_AXIS));
        cmdPan.add(optPan);

        Font f = new Font("Arial", Font.BOLD, 10);
        Dimension dimLayoutPan = new Dimension(350, 30);
        Dimension dimSingleLayoutPan = new Dimension(350, 14);
        //		Dimension dimLabel = new Dimension(80, 14);
        //		Dimension dimLayoutButton = new Dimension(90, 14);
        Dimension dimSlider = new Dimension(40, 14);

        JPanel slidersPan = new JPanel();
        slidersPan.setLayout(new BoxLayout(slidersPan, BoxLayout.X_AXIS));

        JPanel layoutCbPan = new JPanel();
        layoutCbPan.setLayout(new BoxLayout(layoutCbPan, BoxLayout.X_AXIS));

        JPanel layoutPan = new JPanel();
        layoutPan.setLayout(new BoxLayout(layoutPan, BoxLayout.Y_AXIS));

        JoptPan.setProp(slidersPan, dimSingleLayoutPan, f);
        JoptPan.setProp(layoutCbPan, dimSingleLayoutPan, f);
        JoptPan.setProp(layoutPan, dimLayoutPan, f);

        layoutPan.add(slidersPan);
        layoutPan.add(layoutCbPan);

        JLabel lblStiff = new JLabel(" Stiffness:");
        lblStiff.setFont(f);
        slidersPan.add(lblStiff);
        sldStiffness = new JSlider(1, 100, 10);
        sldStiffness.addChangeListener(this);
        sldStiffness.setMaximumSize(dimSlider);
        sldStiffness.setEnabled(false);
        lblStiff.setLabelFor(sldStiffness);
        slidersPan.add(sldStiffness);
        JLabel lblRepulsion = new JLabel(" Repulsion:");
        lblRepulsion.setFont(f);
        slidersPan.add(lblRepulsion);
        sldRepulsion = new JSlider(1, 100, 40);
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
        cmdPan.add(gparam);

        setLayout(new BorderLayout());

        add(cmdPan, BorderLayout.NORTH);
        add(grapPan, BorderLayout.CENTER);
        setBackground(Color.white);
    }

    @Override
    public void new_global_param(String mod) {
        int k = -1;

        for (int i = 0; i < MFilter2Constants.acceptg.length; i++) {
            if (mod.equals(MFilter2Constants.acceptg[i])) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            return;
        }

        synchronized (syncGparamCombobox) {
            int f = 0;
            for (int i = 0; i < gparam.getItemCount(); i++) {
                String pa2 = (String) gparam.getItemAt(i);
                if (pa2.equals(MFilter2Constants.MenuAccept[k])) {
                    f = 1;
                }
            }

            if (f == 0) {
                logger.log(Level.FINE, "Adding in GmapPan gparam combobox: " + MFilter2Constants.MenuAccept[k]);
                gparam.addItem(MFilter2Constants.MenuAccept[k]);
                gparam.repaint();
            }
        }
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

    static class ComboBoxRenderer extends JLabel implements ListCellRenderer {
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
        } else if (src == optPan.kbShowPing) {
            grapPan.currentTransformCancelled = true;
            grapPan.setLayoutType((String) cbLayout.getSelectedItem());
            //		}else if(src == optPan.kbShowOS){
            //		    grapPan.currentTransformCancelled = true;
            //		    grapPan.setLayoutType((String)cbLayout.getSelectedItem());
        } else if (src == gparam) {
            String paramName = (String) gparam.getSelectedItem();
            if (paramName == null) {
                return;
            }
            for (int k = 0; k < MFilter2Constants.MenuAccept.length; k++) {
                if (paramName.equals(MFilter2Constants.MenuAccept[k])) {
                    grapPan.setPie(farmPies[k]);
                    break;
                }
            }
            repaint();
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
