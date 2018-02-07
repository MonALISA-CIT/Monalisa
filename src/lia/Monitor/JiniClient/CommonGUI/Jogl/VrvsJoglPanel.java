/*
 * Created on Nov 7, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;

/**
 *
 * Nov 7, 2004 - 3:51:18 PM
 */
public class VrvsJoglPanel extends JoglPanel implements ActionListener {
    private static final long serialVersionUID = 3924041196337163895L;
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(VrvsJoglPanel.class.getName());

    public JoptPan optPan;

    public VrvsJoglPanel() {
        super();
    }

    @Override
    public void init() {
        super.init();
        //set renderer for nodes
        renderer.addNodesRenderer(new VrvsNodesRenderer(3, 32), "Normal View");
        //renderer.addNodesRenderer( new VrvsOnTopNodesRenderer(3, 32), "OnTop View");
        renderer.setActiveNodesRenderer(0);

        checkToolbar();
    }

    @Override
    protected void buildOptPan() {
        optPan = new JoptPan(this);
        optPan.setMaximumSize(new Dimension(1000, 80));

        optPan.csMST.setColors(Color.MAGENTA, Color.MAGENTA);
        optPan.csMST.setValues(0, 0);
        optPan.csMST.setLabelFormat("", "");

        optPan.csPeers.setColors(Color.RED, Color.GREEN);
        optPan.csPeers.setLabelFormat("###.##", "%");
        optPan.csPeers.setValues(0, 100);

        optPan.csPing.setColors(Color.RED, Color.RED);
        optPan.csPing.setValues(0, 0);
        optPan.csPing.setLabelFormat("###.##", "");

        optPan.csNodes.setColors(Color.CYAN, Color.CYAN);
        optPan.csNodes.setValues(0, 0);
        optPan.cbNodeOpts.setActionCommand("cbNodeOpts");
        optPan.cbNodeOpts.addActionListener(this);
        //		optPan.cbNodeOpts.setLightWeightPopupEnabled(false);

        optPan.cbPeerOpts.setActionCommand("cbPeerOpts");
        optPan.cbPeerOpts.addActionListener(this);
        //optPan.cbPeerOpts.setLightWeightPopupEnabled(false);

        optPan.kbShowMST.setActionCommand("kbShowMST");
        optPan.kbShowMST.addActionListener(this);

        optPan.kbShowPeers.setActionCommand("kbShowPeers");
        optPan.kbShowPeers.addActionListener(this);

        optPan.kbShowPing.setActionCommand("kbShowPing");
        optPan.kbShowPing.addActionListener(this);

        toolbarsPanel.add(optPan);

        //update colors and values
        //for options panel
        //also update list of available meetings
        BackgroundWorker.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(
                        " ( ML ) - VrvsJoglPanel - update colors and values and list of available meetings Thread");
                AbstractNodesRenderer anr = (AbstractNodesRenderer) renderer.getActiveNodesRenderer();
                if (anr == null) {
                    return;
                }
                try {
                    Hashtable hPingOptions = (Hashtable) anr.OptionsPanelValues.get("ping");
                    optPan.csPing.setValues(((Double) hPingOptions.get("MinValue")).doubleValue(),
                            ((Double) hPingOptions.get("MaxValue")).doubleValue());
                    optPan.csPing.setColors(((Color) hPingOptions.get("MinColor")),
                            ((Color) hPingOptions.get("MaxColor"))); // red(min) --> yellow(max)
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "exception computing Ping links color: no options values hastable????");
                    ex.printStackTrace();
                }
                optPan.csNodes.setValues(anr.NodeMinValue, anr.NodeMaxValue);
                optPan.csNodes.setColors(anr.NodeMinColor, anr.NodeMaxColor);
                //				optPan.csPing.setValues( ((Double)anr.LinkMinValue.get("ping")).doubleValue(), ((Double)anr.LinkMaxValue.get("ping")).doubleValue());

                //				optPan.csPing.setColors( ((Color)anr.LinkMinColor.get("ping")), ((Color)anr.LinkMaxColor.get("ping")));
                try {
                    //					VrvsNodesRenderer vnr = (VrvsNodesRenderer)renderer.getActiveNodesRenderer();
                    optPan.meetingsUpdate.updateMeetingsMenu(dglobals.snodes, optPan.communityMenuList,
                            optPan.meetingsMenuList);
                    JoglPanel.globals.canvas.repaint();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Error updating menu list of meetings");
                    ex.printStackTrace();
                }
            }

        }, 1000, 2000);

    }

    /**
     * actions performed by accessing the top option panel
     *      
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("cbNodeOpts")) {
            //			if(nodesMode != 1 + optPan.cbNodeOpts.getSelectedIndex()) {
            //			nodesMode = 1 + optPan.cbNodeOpts.getSelectedIndex();
            //			nodesGroup.mode = nodesMode;
            //			nodesGroup.refresh();
            //			optPan.csNodes.setColors(nodesGroup.minValueColor.get(), nodesGroup.maxValueColor.get());
            //			optPan.csNodes.setValues(nodesGroup.minValue, nodesGroup.maxValue);
            //			}
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
        } else if (cmd.equals("cbPeerOpts")) {
            //			if(peersMode != 11 + optPan.cbPeerOpts.getSelectedIndex()) {
            //			peersMode = 11 + optPan.cbPeerOpts.getSelectedIndex();
            //			peerLinksGroup.mode = peersMode;
            //			if(peerLinksGroup.isLive())
            //			peerLinksGroup.refresh();
            //			}
            //TODO: should be optimized for each tipe of links
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbShowMST")) {
            //TODO: should be optimized for each tipe of links
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbShowPeers")) {
            //TODO: should be optimized for each tipe of links
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("kbShowPing")) {
            //TODO: should be optimized for each tipe of links
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_LINKS_CHANGED));
        } else if (cmd.equals("MeetingsMenuItemChanged")) {
            DataRenderer.sendGlobeEvent(DataRenderer.GLOBE_OPTION_PANEL_CHANGE,
                    Integer.valueOf(DataRenderer.GLOBE_EVENT_PARAM_NODES_CHANGED));
        }
    }
}
