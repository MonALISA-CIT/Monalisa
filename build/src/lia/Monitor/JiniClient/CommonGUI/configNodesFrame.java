/*
 * Created on Oct 14, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.jini.core.lookup.ServiceID;

/**
 * configuration window that permits to select nodes to be hidden in gmap and globe panels
 *
 * Oct 14, 2004 - 4:33:03 PM
 */
public class configNodesFrame extends JFrame implements ActionListener {

    /**
     * 
     */
    private static final long serialVersionUID = -6741983520797492908L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(configNodesFrame.class.getName());

    static private class NodeInList {
        public ServiceID sid;
        public String name;

        public NodeInList(ServiceID s, String n) {
            sid = s;
            name = n;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    //lists of elements that contain farm's name and sid
    JList visibleNodes;
    JList hiddenNodes;
    //reference to snodes through monitor
    SerMonitorBase monitor;

    public configNodesFrame(SerMonitorBase monitor) {
        super("Configure visible nodes");
        this.monitor = monitor;
        DefaultListModel defListModel1 = new DefaultListModel();
        DefaultListModel defListModel2 = new DefaultListModel();
        try {
            for (final Map.Entry<ServiceID, rcNode> entry : monitor.snodes.entrySet()) {
                final ServiceID key = entry.getKey();
                final rcNode elem = entry.getValue();
                if (elem.bHiddenOnMap) {
                    defListModel2.addElement(new NodeInList(key, elem.UnitName));
                } else {
                    defListModel1.addElement(new NodeInList(key, elem.UnitName));
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception while obtaining nodes");
        }
        //this.add(new FlowLayout(FlowLayout.CENTER));
        visibleNodes = new JList(defListModel1);
        hiddenNodes = new JList(defListModel2);
        JPanel totul = new JPanel();
        totul.setLayout(new BoxLayout(totul, BoxLayout.X_AXIS));
        //        totul.setPreferredSize(new Dimension(400, 250));
        JPanel pLeftList = new JPanel();
        pLeftList.setLayout(new BoxLayout(pLeftList, BoxLayout.Y_AXIS));
        pLeftList.add(Box.createVerticalStrut(10));
        pLeftList.add(new JLabel("Visible nodes:"));
        JPanel p1 = new JPanel();
        p1.setLayout(new BorderLayout());
        p1.add(visibleNodes, BorderLayout.CENTER);
        JPanel p11 = new JPanel();
        p11.setLayout(new BorderLayout());
        p11.add(new JScrollPane(p1), BorderLayout.CENTER);
        p11.setPreferredSize(new Dimension(200, 0));
        pLeftList.add(Box.createVerticalStrut(5));
        pLeftList.add(p11);
        pLeftList.add(Box.createVerticalStrut(5));
        pLeftList.add(Box.createGlue());
        totul.add(Box.createHorizontalStrut(5));
        totul.add(pLeftList);
        totul.add(Box.createHorizontalStrut(5));
        //        visibleNodes.setSize(new Dimension(100, 200));
        JPanel pButtons = new JPanel();
        pButtons.setLayout(new BoxLayout(pButtons, BoxLayout.Y_AXIS));
        JButton bAdd = new JButton(">>");
        bAdd.addActionListener(this);
        bAdd.setActionCommand("hide");
        JButton bRemove = new JButton("<<");
        bRemove.addActionListener(this);
        bRemove.setActionCommand("show");
        pButtons.add(bAdd);
        //bAdd.setPreferredSize(new Dimension(20,10));
        pButtons.add(bRemove);
        //bRemove.setPreferredSize(new Dimension(20,10));
        totul.add(pButtons);
        totul.add(Box.createHorizontalStrut(5));
        JPanel pRightList = new JPanel();
        pRightList.setLayout(new BoxLayout(pRightList, BoxLayout.Y_AXIS));
        pRightList.add(Box.createVerticalStrut(10));
        pRightList.add(new JLabel("Hidden nodes:"));
        JPanel p2 = new JPanel();
        p2.setLayout(new BorderLayout());
        p2.add(hiddenNodes, BorderLayout.CENTER);
        JPanel p21 = new JPanel();
        p21.setLayout(new BorderLayout());
        p21.add(new JScrollPane(p2), BorderLayout.CENTER);
        p21.setPreferredSize(new Dimension(200, 0));
        pRightList.add(Box.createVerticalStrut(5));
        pRightList.add(p21);
        pRightList.add(Box.createVerticalStrut(5));
        //pRightList.add(Box.createGlue());
        totul.add(pRightList);
        totul.add(Box.createHorizontalStrut(5));

        //        JButton apply = new JButton("Apply");
        //        apply.addActionListener(this);
        //        apply.setActionCommand( "set");
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        JPanel p3 = new JPanel();
        p3.setLayout(new FlowLayout());
        //p3.add(apply);
        //p3.add(Box.createHorizontalStrut(10));
        p3.add(close);
        JPanel np = new JPanel();
        np.setLayout(new BoxLayout(np, BoxLayout.Y_AXIS));
        np.add(totul);
        np.add(p3);

        this.getContentPane().add(np);

        //totul.add(hiddenNodes);
        //hiddenNodes.setPreferredSize(new Dimension(100, 200));
        setSize(new Dimension(400, 250));
        //        this.pack();
    }

    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().compareTo("hide") == 0) {
            //System.out.println("hide selected nodes");
            Object[] selValues = visibleNodes.getSelectedValues();
            if (selValues.length == 0) {
                return;
            }
            NodeInList nil;
            for (int i = selValues.length - 1; i >= 0; i--) {
                //nil = (NodeInList)listElem1.remove(selIndexes[i]);
                nil = (NodeInList) selValues[i];
                //listElem2.add(nil);
                ((DefaultListModel) visibleNodes.getModel()).removeElement(nil);
                ((DefaultListModel) hiddenNodes.getModel()).addElement(nil);
                try {
                    //System.out.println("sid="+nil.sid);
                    monitor.snodes.get(nil.sid).bHiddenOnMap = true;
                    //get visibility option on wmap and globe panel from properties, the default option
                    Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                    prefs.put("CommonGUI.rcNode." + nil + ".bHiddenOnMap", "1");
                } catch (Exception ex) {
                    logger.log(Level.INFO, "Could not hide node <<" + nil + ">>");
                    ex.printStackTrace();
                }
            }
            visibleNodes.repaint();
            hiddenNodes.repaint();
        } else if (e.getActionCommand().compareTo("show") == 0) {
            //            System.out.println("show selected nodes");
            Object[] selValues = hiddenNodes.getSelectedValues();
            if (selValues.length == 0) {
                return;
            }
            NodeInList nil;
            for (int i = selValues.length - 1; i >= 0; i--) {
                nil = (NodeInList) selValues[i];
                ((DefaultListModel) hiddenNodes.getModel()).removeElement(nil);
                ((DefaultListModel) visibleNodes.getModel()).addElement(nil);
                try {
                    //System.out.println("sid="+nil.sid);
                    monitor.snodes.get(nil.sid).bHiddenOnMap = false;
                    Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                    prefs.put("CommonGUI.rcNode." + nil + ".bHiddenOnMap", "0");
                } catch (Exception ex) {
                    logger.log(Level.INFO, "Could not show node <<" + nil + ">>");
                }
            }
            visibleNodes.repaint();
            hiddenNodes.repaint();
        }
    }

}
