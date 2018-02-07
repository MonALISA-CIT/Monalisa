/*
 * Created on Oct 14, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import lia.Monitor.JiniClient.CommonGUI.plot.MultiGraphPanel;

/**
 * configuration window that permits to select nodes to be hidden in gmap and globe panels
 *
 * Oct 14, 2004 - 4:33:03 PM
 */
public class ClientTransferStatsFrame extends JFrame implements ActionListener, WindowListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ClientTransferStatsFrame.class.getName());
    public JLabel jlbAll;
    public MultiGraphPanel graph;
    private final StatisticsMonitor sMon;
    /** list of checkboxes to select current rates to show */
    private JCheckBox[] cbList = null;

    protected void locateOnScreen(Component component) {
        Dimension paneSize = component.getSize();
        Dimension screenSize = component.getToolkit().getScreenSize();
        component.setLocation((screenSize.width - paneSize.width) / 2, (screenSize.height - paneSize.height) / 2);
    }

    public ClientTransferStatsFrame(final StatisticsMonitor sMon) {
        super("Real-time statistics for ML client");
        //		System.out.println("new client transfer statistics window");
        this.sMon = sMon;

        JPanel totul = new JPanel();
        totul.setLayout(new BoxLayout(totul, BoxLayout.Y_AXIS));

        JPanel jp = new JPanel();
        jp.setLayout(new FlowLayout(FlowLayout.LEFT));
        jlbAll = new JLabel("", SwingConstants.LEFT);
        jlbAll.setFont(new Font("Arial", Font.PLAIN, 9));
        //		jlbAll.setPreferredSize(new Dimension(900, 130));
        jp.add(jlbAll);
        totul.add(jp);

        int n = sMon.values.length;
        Color[] colors = new Color[n];
        String[] texts = new String[n];
        Color[] max_colors = new Color[n];
        String[] max_texts = new String[n];
        for (int i = 0; i < n; i++) {
            colors[i] = sMon.values[i].getColor();
            texts[i] = sMon.values[i].sName;
            max_colors[i] = sMon.values[i].getMaxColor();
            max_texts[i] = "Max " + sMon.values[i].sName;
        }
        //add graph with history for a rate
        graph = new MultiGraphPanel("Statistics for traffic between client and proxy", texts, colors, max_texts,
                max_colors, "T", "bytes", 200000);
        for (int i = 0; i < n; i++) {
            graph.setVisible(i, false);
            graph.setSingleVisible(i, false);
        }
        totul.add(graph);

        int nSelected = StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN;

        //add radio buttons to choose the desired rate to be shown
        //		JPanel jpRateChoice = new JPanel();
        //		jpRateChoice.setLayout(new BoxLayout(jpRateChoice, BoxLayout.X_AXIS));
        //		ButtonGroup bg = new ButtonGroup();
        //		for ( int i=0; i<n; i++) {
        //			JRadioButton rb = new JRadioButton(sMon.values[i].sShortName);
        ////			rb.setActionCommand(sMon.values[i].sShortName);
        //			final int index = i;
        //			rb.addActionListener(new ActionListener() {
        //				public void actionPerformed(ActionEvent ae) {
        //					//the index choice is selected
        ////					System.out.println("set visible "+sMon.szValues_Short[index]);
        //					graph.setUnit(sMon.values[index].sUnit);
        //					graph.setVisible(index, true);
        //					graph.setVisible(selectedStatistics, false);
        //					selectedStatistics = index;
        ////					System.out.println("unit set, selectedStatistics: "+index);
        //				}
        //			});
        //			if ( nSelected == i ) {
        //				selectedStatistics = i;
        //				rb.setSelected(true);
        //				graph.setUnit(sMon.values[i].sUnit);
        //				graph.setVisible(i, true);
        //			} else {
        //				graph.setVisible(i, false);
        //			}
        //			jpRateChoice.add(rb);
        //			bg.add(rb);
        //		};
        //		totul.add(jpRateChoice);

        //add checkbox buttons to choose the desired rate or rates to be shown
        cbList = new JCheckBox[n];
        JPanel jpRateCheck = new JPanel();
        jpRateCheck.setLayout(new BoxLayout(jpRateCheck, BoxLayout.X_AXIS));
        for (int i = 0; i < n; i++) {
            JCheckBox cb = new JCheckBox(sMon.values[i].sShortName);
            cb.setActionCommand(sMon.values[i].sShortName);
            cb.addActionListener(this);
            if (nSelected == i) {
                cb.setSelected(true);
                cb.setEnabled(false);
                graph.setUnit(sMon.values[i].sUnit);
                graph.setVisible(i, true);
                graph.setSingleVisible(i, true);
            } else {
                graph.setVisible(i, false);
                graph.setSingleVisible(i, false);
            }
            jpRateCheck.add(cb);
            cbList[i] = cb;
        }
        ;
        totul.add(jpRateCheck);

        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(this);
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sMon.monitor.main.frmStatistics = null;
                dispose();
            }
        });
        JPanel jp2 = new JPanel();
        jp2.setLayout(new FlowLayout(FlowLayout.CENTER));
        jp2.add(close);
        totul.add(jp2);

        this.getContentPane().add(totul);

        setSize(new Dimension(600, 500));
        locateOnScreen(this);

        //put some info in the window

        //		this.pack();
        //		new Timer().schedule(new MyTimerTask(), 100, StatisticsMonitor.TIME_UNIT);
    }

    //	class MyTimerTask extends TimerTask {
    //		public void run() {
    //		}
    //
    //	}

    /** runs newIn/OutValue once at 2*TIME_UNIT intervals */
    //	private boolean bTest=false;

    @Override
    public void actionPerformed(ActionEvent e) {
        int n = sMon.values.length;
        int nSelected = -1;
        for (int i = 0; i < n; i++) {
            if (e.getActionCommand().equals(sMon.values[i].sShortName)) {
                nSelected = i;
                break;
            }
            ;
        }
        ;
        if (nSelected == -1) {
            return;
        }
        JCheckBox cb = cbList[nSelected];
        //		System.out.println("pressed on: "+nSelected+" to "+(cb.isSelected()?"select":"unselect")+" it");
        //cb.setSelected(true);
        if (cb.isSelected()) {
            graph.setUnit(sMon.values[nSelected].sUnit);
            graph.setVisible(nSelected, true);
            graph.setSingleVisible(nSelected, true);
        } else {
            graph.setVisible(nSelected, false);
            graph.setSingleVisible(nSelected, false);
        }
        int nSelectedPair = -1;
        JCheckBox cbPair;
        switch (nSelected) {
        case StatisticsMonitor.VALUE_FARMS:
            //the only option available, so no deselect
            //			cb.setSelected(true);
            cb.setEnabled(false);
            //farms selected, so deselect all other
            for (int i = 0; i < n; i++) {
                if (i != nSelected) {
                    cb = cbList[i];
                    if (!cb.isEnabled()) {
                        cb.setEnabled(true);
                    }
                    if (cb.isSelected()) {
                        cb.setSelected(false);
                        graph.setVisible(i, false);
                        graph.setSingleVisible(i, false);
                    }
                    ;
                }
            }
            break;
        case StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN:
            nSelectedPair = StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT;
        case StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT:
            if (nSelectedPair == -1) {
                nSelectedPair = StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN;
            }
        case StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN:
            if (nSelectedPair == -1) {
                nSelectedPair = StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT;
            }
        case StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT:
            if (nSelectedPair == -1) {
                nSelectedPair = StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN;
            }
            //check to see if byte out is selected, and if so, enable both for
            //possible deselection
            cbPair = cbList[nSelectedPair];
            if (cb.isSelected()) {
                if (cbPair.isSelected()) {
                    cbPair.setEnabled(true);
                } else {//only one selected, so don't allow deselection
                    cb.setEnabled(false);
                    //rate selected, so deselect all other
                    for (int i = 0; i < n; i++) {
                        if ((i != nSelected) && (i != nSelectedPair)) {
                            cb = cbList[i];
                            if (!cb.isEnabled()) {
                                cb.setEnabled(true);
                            }
                            if (cb.isSelected()) {
                                cb.setSelected(false);
                                graph.setVisible(i, false);
                                graph.setSingleVisible(i, false);
                            }
                            ;
                        }
                    }
                }
            } else {
                //this one is deselected so disable the pair for not to
                //allow deselection
                cbPair.setEnabled(false);
            }
            break;
        }
    }

    /* (non-Javadoc)
     * @see java.awt.Component#setVisible(boolean)
     */
    @Override
    public void setVisible(boolean b) {
        // TODO Auto-generated method stub
        super.setVisible(b);
    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowActivated(java.awt.event.WindowEvent)
     */
    @Override
    public void windowActivated(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowClosed(java.awt.event.WindowEvent)
     */
    @Override
    public void windowClosed(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowClosing(java.awt.event.WindowEvent)
     */
    @Override
    public void windowClosing(WindowEvent e) {
        // TODO Auto-generated method stub
        sMon.monitor.main.frmStatistics = null;
        //		System.out.println("<mluc> client transfer statistics window closing");
    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowDeactivated(java.awt.event.WindowEvent)
     */
    @Override
    public void windowDeactivated(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowDeiconified(java.awt.event.WindowEvent)
     */
    @Override
    public void windowDeiconified(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowIconified(java.awt.event.WindowEvent)
     */
    @Override
    public void windowIconified(WindowEvent e) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see java.awt.event.WindowListener#windowOpened(java.awt.event.WindowEvent)
     */
    @Override
    public void windowOpened(WindowEvent e) {
        // TODO Auto-generated method stub

    }

}
