package lia.Monitor.monitor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Class that shows the legend dialog for oslink colors.
 */
public class OSLinkLegend extends JDialog {

	public static final Font textFont = new Font("Arial", Font.BOLD, 18);
	public static final Font f = new Font("Arial", Font.PLAIN, 14);
	
	static OSLinkLegend legend;
	
	OSLinkLegend(JFrame owner) {
		
		super(owner, "Legend", true);
		setResizable(false);
		LegendPanel p = new LegendPanel();
		this.setLocation(owner.getLocation());
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(p, BorderLayout.CENTER);
		setSize(500, 320);
	}
	
	public static void show(JFrame owner) {
		if (legend == null) legend = new OSLinkLegend(owner);
		legend.setLocation((int)(owner.getLocation().getX()+owner.getWidth()/2-legend.getWidth()/2), 
				(int)(owner.getLocation().getY()+owner.getHeight()/2-legend.getHeight()/2));
		legend.setVisible(true);
	}

	class LegendPanel extends JPanel {
		
		public LegendPanel() {
			super();
			setBackground(Color.white);
		}
		
		private void drawPortRect(Graphics2D g2, Color c, int x, int y, int w) {
			
			g2.setColor(c);
			g2.fillRect(x+1, y+1, w+8, w+8);
			g2.setColor(Color.lightGray);
			g2.drawRect(x, y, w+10, w+10);
			g2.drawRect(x+1, y+1, w+8, w+8);
			g2.drawRect(x+2, y+2, w+6, w+6);
		}
		
		private void drawLinkRect(Graphics2D g2, Color c, int x, int y, int w) {
			g2.setColor(Color.lightGray);
			g2.fillRect(x, y+w-1, w+10+2, w+2);
			g2.setColor(c);
			if (c.equals(OSLink.OSLINK_COLOR_DISCONNECTED) || c.equals(OSLink.OSLINK_COLOR_FREE_2) || c.equals(OSLink.OSLINK_COLOR_FAIL)) {
				BasicStroke b = (BasicStroke)g2.getStroke();
				BasicStroke stroke = new BasicStroke(2,
						b.getEndCap(),
						b.getLineJoin(),
						b.getMiterLimit(),
						new float[] {2.0f, 4.0f}, // Dash pattern
						b.getDashPhase());
				g2.setStroke(stroke);
				g2.drawLine(x+3, y+3*w/2, x+w+9, y+3*w/2);
				g2.setStroke(b);
			} else {
				BasicStroke b = (BasicStroke)g2.getStroke();
				BasicStroke stroke = new BasicStroke(2);
				g2.setStroke(stroke);
				g2.drawLine(x+3, y+3*w/2, x+w+9, y+3*w/2);
				g2.setStroke(b);
			}
		}

		
		public void paintComponent(Graphics g) {
			
			super.paintComponent(g);
			
			Graphics2D g2 = (Graphics2D)g;
			// draw the in title
			g2.setFont(textFont);
			FontMetrics fm = g2.getFontMetrics();
			int titleW = fm.stringWidth("LEGEND");
			g2.drawString("LEGEND", (getWidth()-titleW)/2, 20);
			g2.setFont(f);
			fm = g2.getFontMetrics();
			int maxLen = fm.stringWidth("1");
			int w = fm.stringWidth("2");
			if (maxLen < w) maxLen = w;
			int startRect = 20;
			
			// draw the ports...
			int startY = 20 + fm.getHeight();
			drawPortRect(g2, OSLink.OSLINK_COLOR_CONNECTED, startRect, startY, w);
			int middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("CROSS_CONN (No light)", startRect + 22 + maxLen, middleY);
			fm = g2.getFontMetrics();
			
			startY += maxLen + 20;
			drawPortRect(g2, OSLink.OSLINK_COLOR_TRANSFERING_ML, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("ML_CONN (Light present ... connection made by ml_path)", startRect + 22 + maxLen, middleY);

			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_TRANSFERING_ML, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("ML_CONN (Light present ... connection made by ml_path)", startRect + 22 + maxLen, middleY);

			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_FREE_2, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("FREE (Optical Fiber present, No Light is present)", startRect + 22 + maxLen, middleY);

			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_DISCONNECTED, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("DISCONNECTED (No Optical Fiber present, No Light is present)", startRect + 22 + maxLen, middleY);

			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_FAIL, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("CONN_FAIL (Connected, No Light)", startRect + 22 + maxLen, middleY);

			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_IDLE, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("IDLE_LIGHT (Connected, Light, No ID)", startRect + 22 + maxLen, middleY);
			
			startY += maxLen + 20;
			drawLinkRect(g2, OSLink.OSLINK_COLOR_TRANSFERING, startRect, startY, w);
			middleY = startY + fm.getHeight();
			g2.setFont(f);
			g2.setColor(Color.black);
			g2.drawString("OTHER_CONN (Connected, Light, Cross-Connect)", startRect + 22 + maxLen, middleY);
		}
	}

	public static void main(String args[]) {
		
		JFrame f = new JFrame("test");
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		f.setSize(200, 200);
		f.setLocation(new Point(200, 200));
		f.setVisible(true);
		OSLinkLegend.show(f);
	}
}
