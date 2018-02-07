package lia.Monitor.JiniClient.Farms;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;

import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import lia.Monitor.monitor.AppConfig;


/**
 * Used to display the chooser and color range for the links and bubbles
 */
public class JoptPan extends JPanel implements ActionListener {

	public JPanel parent;
	public JColorScale csPing;
	public JCheckBox kbShowPing;
	public JColorScale csWAN;
	public JCheckBox kbShowWAN;
	public JCheckBox kbShowOS;//optical switch, NOT operating system :D
    public JCheckBox kbShowNF;//NetFlow checkbox
    public JColorScale csNetFlow;//color scale for net flow
	public JCheckBox kbAnimateWAN;

	// combo box support
	public JComboBox gparam;
	public DefaultComboBoxModel combo;

	 
	// constructor
	public JoptPan(JPanel parent){
		this.parent = parent;

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        Font f = new Font("Arial", Font.BOLD, 10);
        Dimension dpan= new Dimension(200, 30);
        Dimension dkb = new Dimension(120, 14);
        Dimension dkb2 = new Dimension(80, 14);

        csPing = new JColorScale();
		kbShowPing = new JCheckBox("ABPing RTTime", false);
		kbShowOS = new JCheckBox("OS Links", false);

		kbShowPing.setFont(f);
		kbShowOS.setFont(f);
//		setProp(kbShowPing, dkb, f);
	//	setProp(kbShowOS, dkb2, f);
		JPanel lPanel1 = new JPanel();
		lPanel1.setLayout(new BorderLayout());
		lPanel1.add(kbShowPing, BorderLayout.WEST);
		lPanel1.add(kbShowOS, BorderLayout.EAST);
		lPanel1.add(csPing, BorderLayout.SOUTH);
		setProp(lPanel1, dpan, f);
		add(lPanel1);

		add(Box.createHorizontalStrut(10));

		//dpan= new Dimension(200, 30);
		dkb = new Dimension(100, 14);
		dkb2 = new Dimension(100, 14);

		csWAN = new JColorScale();
		kbShowWAN = new JCheckBox("WAN Links", true);
		kbAnimateWAN = new JCheckBox("Animated", false);

		kbShowWAN.setFont(f);
		kbAnimateWAN.setFont(f);
		//setProp(kbShowWAN, dkb, f);
		//setProp(kbAnimateWAN, dkb2, f);
		setProp(csWAN, dkb, f);
		JPanel lPanel2 = new JPanel();
		lPanel2.setLayout(new BorderLayout());
		lPanel2.add(kbShowWAN, BorderLayout.WEST);
		lPanel2.add(kbAnimateWAN, BorderLayout.EAST);
		lPanel2.add(csWAN, BorderLayout.SOUTH);
		setProp(lPanel2, dpan, f);
		add(lPanel2);

		add(Box.createHorizontalStrut(10));

        //dpan= new Dimension(200, 30);
        dkb = new Dimension(140, 14);
        //add netflow info
        csNetFlow = new JColorScale();
        final FarmsSerMonitor.GlobeLinksType glt = FarmsSerMonitor.getGlobeLinksTypeFromEnv();
        String attrName = "";
        switch (glt) {
            case FDT: {
                attrName = "FDT traffic";
                break;
            }
            case NETFLOW: {
                attrName = "Net Flow";
                break;
            }
            case OPENFLOW: {
                attrName = "OpenFlow links";
                break;
            }
            case UNDEFINED: {
                // ;) - meaning NoLinks :D
                attrName = "FDT Links";
                break;
            }
            default:
                break;
        }

        kbShowNF = new JCheckBox(attrName, true);
        //setProp(kbShowNF, dkb, f);
		kbShowNF.setFont(f);
        setProp(csNetFlow, dkb, f);
        JPanel lPanel3 = new JPanel();
        lPanel3.setLayout(new BorderLayout());
        lPanel3.add(Box.createHorizontalStrut(40), BorderLayout.EAST);
        lPanel3.add(kbShowNF, BorderLayout.WEST);
        lPanel3.add(csNetFlow, BorderLayout.SOUTH);
        setProp(lPanel3, dpan, f);
        add(lPanel3);

        add(Box.createHorizontalStrut(10));
        
//		// set menu weight to avoid drawing menus under 3D scenes
		JPopupMenu.setDefaultLightWeightPopupEnabled( false );
		combo = new DefaultComboBoxModel();
		gparam = new JComboBox(combo);
		setProp(gparam, dpan, f);
		ComboBoxRenderer renderer = new ComboBoxRenderer();
		gparam.setRenderer(renderer);
//		for(int k=0; k<MenuAccept.length; k++)
//			gparam.addItem(MenuAccept[k]);
		add(gparam);

		kbShowPing.addActionListener(this);
		kbShowOS.addActionListener(this);
		kbShowWAN.addActionListener(this);
		kbAnimateWAN.addActionListener(this);
		
		add(Box.createHorizontalGlue());
	}

	private void setProp(JComponent c, Dimension d, Font f){
		c.setFont(f);
		c.setPreferredSize(d);
		c.setMaximumSize(d);
//		c.setMinimumSize(d);
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == kbShowPing || e.getSource() == kbShowWAN){
			parent.repaint();
		}
        repaint();
	}

	class ComboBoxRenderer extends JLabel implements ListCellRenderer {
		public ComboBoxRenderer() {
			setOpaque(true);
			setHorizontalAlignment(CENTER);
			setVerticalAlignment(CENTER);
		}
		public Component getListCellRendererComponent(
			JList list,
			Object value,
			int index,
			boolean isSelected,
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


}
