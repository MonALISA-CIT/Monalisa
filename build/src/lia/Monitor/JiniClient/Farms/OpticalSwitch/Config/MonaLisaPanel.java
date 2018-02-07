package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class MonaLisaPanel extends JPanel {

	private static ImageIcon ml1;
	private static ImageIcon ml2;
	private static final Object lock = new Object();
	
	protected Rectangle eyeRect;
	
	public MonaLisaPanel() {
		
		super();
		initImages();
		setBackground(Color.white);
	}
	
	private void initImages() {
	
		synchronized (lock) {
			if (ml1 == null) {
				URL url = this.getClass().getResource("/lia/images/ml_1.gif");
				try {
					ml1 = new ImageIcon(url);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (ml2 == null) {
				URL url = this.getClass().getResource("/lia/images/ml_optical_switch.png");
				try {
					ml2 = new ImageIcon(url);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	protected Rectangle getEyeRect() {
		synchronized (lock) {
			return eyeRect;
		}
	}
	
	public void paintComponent(Graphics g) {
		
		super.paintComponent(g);
		
		g.drawImage(ml2.getImage(), 10, 10, null);
		g.drawImage(ml1.getImage(), getWidth() - 10 - ml1.getIconWidth(), 10, null);
		synchronized (lock) {
			final Point p = getLocationOnScreen();
			eyeRect = new Rectangle(getWidth() - 10 - ml1.getIconWidth() + p.x, 10 + p.y, ml1.getIconWidth(), ml1.getIconHeight());
		}
	}
}
