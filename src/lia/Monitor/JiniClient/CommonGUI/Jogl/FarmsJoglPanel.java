/*
 * Created on Nov 7, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComboBox;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.MFilter2Constants;
import lia.Monitor.JiniClient.Farms.JoptPan;

/**
 *
 * Nov 7, 2004 - 3:51:18 PM
 * 
 * @author mluc
 */
public class FarmsJoglPanel extends JoglPanel implements ActionListener {

    private static final long serialVersionUID = -3246699653643938275L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(FarmsJoglPanel.class.getName());

    private final Object syncGparamCombobox = new Object();
    public String pieKey = "noLoadPie";

    public JoptPan optPan;

    public static Color minNetFlowColor = new Color(0xfd9a52);
    public static Color maxNetFlowColor = new Color(0xcb521d);

    public FarmsJoglPanel() {
        super();
    }

    @Override
    public void init() {
        super.init();
        //set renderer for nodes
        FarmsNodesRenderer fnr = new FarmsNodesRenderer(3, 32);
        renderer.addNodesRenderer(fnr, "Normal view");
        //renderer.addNodesRenderer( new FarmsOnTopNodesRenderer(3, 32), "OnTop view");
        renderer.setActiveNodesRenderer(0);
        DataRenderer.addGlobeListener(fnr);

        checkToolbar();
    }

    @Override
    protected void buildOptPan() {
        optPan = new JoptPan(this);
        //		  optPan.setMaximumSize(new Dimension(1000, 80));

        optPan.csWAN.setColors(Color.CYAN, Color.CYAN);
        optPan.csWAN.setLabelFormat("###.##", "Mbps");
        optPan.csWAN.setValues(0, 0);

        optPan.csPing.setColors(Color.GREEN, Color.GREEN);
        optPan.csPing.setValues(0, 0);
        optPan.csPing.setLabelFormat("###.##", "");

        optPan.csNetFlow.setColors(minNetFlowColor, minNetFlowColor);
        optPan.csNetFlow.setLabelFormat("###.##", "Mbps");
        optPan.csNetFlow.setValues(0, 0);

        optPan.kbShowWAN.setActionCommand("kbShowWAN");
        optPan.kbShowWAN.addActionListener(this);

        optPan.kbShowPing.setActionCommand("kbShowPing");
        optPan.kbShowPing.addActionListener(this);

        optPan.kbShowOS.setActionCommand("kbShowOS");
        optPan.kbShowOS.addActionListener(this);

        optPan.kbShowNF.setActionCommand("kbShowNetFlow");
        optPan.kbShowNF.addActionListener(this);

        optPan.kbAnimateWAN.setActionCommand("kbAnimateWAN");
        optPan.kbAnimateWAN.addActionListener(this);
        optPan.kbAnimateWAN.setVisible(true);
        optPan.kbShowOS.setVisible(true);

        optPan.gparam.setActionCommand("comboBox");
        optPan.gparam.addActionListener(this);

        toolbarsPanel.add(optPan);

        //update colors and values
        //for options panel
        BackgroundWorker.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(
                        " ( ML ) - JOGL - FarmsJoglPanel Timer Thread: update colors and values for options panel");
                AbstractNodesRenderer anr = (AbstractNodesRenderer) renderer.getActiveNodesRenderer();
                if (anr == null) {
                    return;
                }
                if (anr.OptionsPanelValues != null) {
                    try {
                        Hashtable hWANOptions = (Hashtable) anr.OptionsPanelValues.get("wan");
                        if (hWANOptions != null) {
                            optPan.csWAN.setValues(((Double) hWANOptions.get("MinValue")).doubleValue(),
                                    ((Double) hWANOptions.get("MaxValue")).doubleValue());
                            optPan.csWAN.setLimitValues(((Double) hWANOptions.get("LimitMinValue")).doubleValue(),
                                    ((Double) hWANOptions.get("LimitMaxValue")).doubleValue());
                            optPan.csWAN.setColors(((Color) hWANOptions.get("MinColor")),
                                    ((Color) hWANOptions.get("MaxColor"))); // red(min) --> yellow(max)
                        }
                        ;
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "exception computing WAN links color: no options values hastable????");
                        ex.printStackTrace();
                    }
                    try {
                        Hashtable hNFOptions = (Hashtable) anr.OptionsPanelValues.get("netflow");
                        if (hNFOptions != null) {
                            optPan.csNetFlow.setValues(((Double) hNFOptions.get("MinValue")).doubleValue(),
                                    ((Double) hNFOptions.get("MaxValue")).doubleValue());
                            optPan.csNetFlow.setLimitValues(((Double) hNFOptions.get("LimitMinValue")).doubleValue(),
                                    ((Double) hNFOptions.get("LimitMaxValue")).doubleValue());
                            optPan.csNetFlow.setColors(((Color) hNFOptions.get("MinColor")),
                                    ((Color) hNFOptions.get("MaxColor"))); // red(min) --> yellow(max)
                        }
                        ;
                    } catch (Exception ex) {
                        logger.log(Level.WARNING,
                                "exception computing NetFlow links color: no options values hastable????");
                        ex.printStackTrace();
                    }
                    try {
                        Hashtable hPingOptions = (Hashtable) anr.OptionsPanelValues.get("ping");
                        if (hPingOptions != null) {
                            optPan.csPing.setValues(((Double) hPingOptions.get("MinValue")).doubleValue(),
                                    ((Double) hPingOptions.get("MaxValue")).doubleValue());
                            optPan.csPing.setColors(((Color) hPingOptions.get("MinColor")),
                                    ((Color) hPingOptions.get("MaxColor"))); // red(min) --> yellow(max)
                        }
                        ;
                    } catch (Exception ex) {
                        logger.log(Level.WARNING,
                                "exception computing Ping links color: no options values hastable????");
                        ex.printStackTrace();
                    }
                }
                ;
                //		          optPan.csNodes.setValues(anr.NodeMinValue, anr.NodeMaxValue);
                //		          optPan.csNodes.setColors( anr.NodeMinColor, anr.NodeMaxColor);
                //		          optPan.csPing.setValues( ((Double)anr.LinkMinValue.get("ping")).doubleValue(), ((Double)anr.LinkMaxValue.get("ping")).doubleValue());
                //		          
                //		          optPan.csPing.setColors( ((Color)anr.LinkMinColor.get("ping")), ((Color)anr.LinkMaxColor.get("ping")));
            }

        }, 1000, 4000);

    }

    /**
     * actions performed by accessing the top option panel
     *      
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("kbShowWAN")) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbShowOS")) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbShowPing")) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
            //	            togglePingLinksGroup();
        } else if (cmd.equals("kbShowNetFlow")) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbAnimateWAN")) {
            ((FarmsNodesRenderer) renderer.getActiveNodesRenderer()).changeLinkAnimationStatus(optPan.kbAnimateWAN
                    .isSelected());
        } else if (cmd.equals("comboBox")) {
            JComboBox cb = (JComboBox) e.getSource();
            String paramName = (String) cb.getSelectedItem();
            if (paramName == null) {
                return;
            }

            if (paramName.equals(MFilter2Constants.MenuAccept[0])) {
                setPie("CPUPie");
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[1])) {
                setPie("IOPie");
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[2])) {
                setPie("LoadPie");
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
                return;
            }
            if (paramName.equals(MFilter2Constants.MenuAccept[3])) {
                setPie("DiskPie");
                DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                        Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
                return;
            }
        }
    }

    @Override
    public void gupdate() {
        //        
        //        String paramName = (String) optPan.gparam.getSelectedItem();
        //        if (paramName == null) {
        //            repaint();
        //            return;
        //        }
        //        
        //        if (paramName.equals(MFilter2Constants.MenuAccept[0]))
        //            setPie("CPUPie");
        //        if (paramName.equals(MFilter2Constants.MenuAccept[1]))
        //            setPie("IOPie");
        //        if (paramName.equals(MFilter2Constants.MenuAccept[2]))
        //            setPie("LoadPie");
        //        if (paramName.equals(MFilter2Constants.MenuAccept[3]))
        //            setPie("DiskPie");
        //        
        //        repaint();
    }

    void setPie(String key) {
        pieKey = key;
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
            for (int i = 0; i < optPan.gparam.getItemCount(); i++) {
                String pa2 = (String) optPan.gparam.getItemAt(i);
                if (pa2.equals(MFilter2Constants.MenuAccept[k])) {
                    f = 1;
                }
            }

            if (f == 0) {
                logger.log(Level.FINE, "Adding in JoglPan gparam combobox: " + MFilter2Constants.MenuAccept[k]);
                optPan.gparam.addItem(MFilter2Constants.MenuAccept[k]);
                optPan.gparam.repaint();
            }
        }
    }
}
