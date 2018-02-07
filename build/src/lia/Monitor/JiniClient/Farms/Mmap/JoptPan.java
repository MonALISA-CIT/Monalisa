package lia.Monitor.JiniClient.Farms.Mmap;

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


/**
 * Used to display the chooser and color range for the links and bubbles
 */
public class JoptPan extends JPanel implements ActionListener {

	public JPanel parent;
	public JColorScale csPing;
	public JCheckBox kbShowPing;
	public JColorScale csWAN;
	public JCheckBox kbShowWAN;
	public JCheckBox kbShowWANVal;

	// combo box support
	public JComboBox gparam;
	public DefaultComboBoxModel combo;

	// constructor
	public JoptPan(JPanel parent){
		this.parent = parent;

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		csPing = new JColorScale();
		kbShowPing = new JCheckBox("ABPing RTTime", false);

		Font f = new Font("Arial", Font.BOLD, 10);
		Dimension dpan= new Dimension(200, 30);
		Dimension dkb = new Dimension(200, 14);

		setProp(kbShowPing, dkb, f);
		JPanel lPanel1 = new JPanel();
		lPanel1.setLayout(new BorderLayout());
		lPanel1.add(kbShowPing, BorderLayout.CENTER);
		lPanel1.add(csPing, BorderLayout.SOUTH);
		setProp(lPanel1, dpan, f);
		add(lPanel1);

		add(Box.createHorizontalStrut(25));

		dpan= new Dimension(200, 30);
		dkb = new Dimension(130, 14);
		Dimension dkb_val = new Dimension( 70, 14);//values dkb

		csWAN = new JColorScale();
		kbShowWAN = new JCheckBox("Show WAN Links", true);
		//create a checkbox to show/hide values for WAN links
		kbShowWANVal = new JCheckBox("Values", false);

		setProp(kbShowWAN, dkb, f);
		setProp(kbShowWANVal, dkb_val, f);
		setProp(csWAN, dkb, f);
		JPanel lPanel2 = new JPanel();
		lPanel2.setLayout(new BorderLayout());
		lPanel2.add(kbShowWAN, BorderLayout.WEST);
		lPanel2.add(kbShowWANVal, BorderLayout.EAST);
		lPanel2.add(csWAN, BorderLayout.SOUTH);
		setProp(lPanel2, dpan, f);
		add(lPanel2);

		add(Box.createHorizontalStrut(25));

		// set menu weight to avoid drawing menus under 3D scenes
		JPopupMenu.setDefaultLightWeightPopupEnabled( false );
		combo = new DefaultComboBoxModel();
		gparam = new JComboBox(combo);
		ComboBoxRenderer renderer = new ComboBoxRenderer();
		gparam.setRenderer(renderer);
		add(gparam);

		kbShowPing.addActionListener(this);
		kbShowWAN.addActionListener(this);
		kbShowWANVal.addActionListener(this);
	}

	private void setProp(JComponent c, Dimension d, Font f){
		c.setFont(f);
		c.setPreferredSize(d);
		c.setMaximumSize(d);
		c.setMinimumSize(d);
	}

	public void actionPerformed(ActionEvent e) {
		if( e.getSource() == kbShowPing || e.getSource() == kbShowWAN || e.getSource() == kbShowWANVal ){
			parent.repaint();
		}
        repaint();
	}

	static class ComboBoxRenderer extends JLabel implements ListCellRenderer {
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
