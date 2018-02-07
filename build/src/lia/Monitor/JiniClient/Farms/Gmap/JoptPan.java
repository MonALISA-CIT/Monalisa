package lia.Monitor.JiniClient.Farms.Gmap;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;


/**
 * Used to display the chooser and color range for the links and bubbles
 */
public class JoptPan extends JPanel implements ActionListener {

	public JPanel parent;
	public JColorScale csPing;
	public JCheckBox kbShowPing;
	public JCheckBox kbMakeNice;
	public JCheckBox kbShadow;
	public GraphPan graphPan;
	public GmapPan pan; 
	
	// constructor
	public JoptPan(JPanel parent, GmapPan pan){
		this.parent = parent;
		this.pan = pan;
		
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		csPing = new JColorScale();
		kbShowPing = new JCheckBox("RTTime", true);//ABPing 
		kbMakeNice = new JCheckBox("Nicer", false);
		kbShadow = new JCheckBox("Shadow", false);
		Font f = new Font("Arial", Font.BOLD, 10);
		Dimension dpan= new Dimension(200, 35);

		kbShowPing.setFont(f);
		kbMakeNice.setFont(f);
		kbShadow.setFont(f);
		JPanel lPanel1 = new JPanel();
		lPanel1.setLayout(new BoxLayout(lPanel1, BoxLayout.Y_AXIS));
		JPanel lPanel2 = new JPanel();
		lPanel2.setLayout(new BoxLayout(lPanel2, BoxLayout.X_AXIS));
		lPanel2.add(kbShadow);
		lPanel2.add(kbShowPing);
		lPanel2.add(kbMakeNice);
		lPanel1.add(lPanel2);
		lPanel1.add(csPing);
		setProp(lPanel1, dpan, f);
		add(lPanel1);

		add(Box.createHorizontalStrut(25));

		dpan= new Dimension(120, 30);

		kbShowPing.addActionListener(this);
		kbMakeNice.addActionListener(this);
		kbShadow.addActionListener(this);
	}

	static public void setProp(JComponent c, Dimension d, Font f){
		c.setFont(f);
		c.setPreferredSize(d);
		c.setMaximumSize(d);
		c.setMinimumSize(d);
	}
	
	void setGraphPan(GraphPan gp){
		graphPan = gp;
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == kbShowPing /* || e.getSource() == kbShowWAN */){
			graphPan.setColors4ABPing();
			graphPan.repaint();
		}else if(e.getSource() == kbMakeNice){
			graphPan.setColors4ABPing();
			graphPan.repaint();
			if (pan == null || pan.monitor == null || pan.monitor.main == null)
				return;
			//OSGPanel panel = (OSGPanel)(pan.monitor.main.getGraphical("GridGraph"));
			//if (panel == null) return;
			//panel.setShowSphere(kbMakeNice.isSelected());
		} else if (e.getSource().equals(kbShadow)) {
			graphPan.repaint();
			if (pan == null || pan.monitor == null || pan.monitor.main == null)
				return;
			//OSGPanel panel = (OSGPanel)(pan.monitor.main.getGraphical("GridGraph"));
			//if (panel == null) return;
			//panel.setShowShadow(kbShadow.isSelected());
		}
        repaint();
	}

}
