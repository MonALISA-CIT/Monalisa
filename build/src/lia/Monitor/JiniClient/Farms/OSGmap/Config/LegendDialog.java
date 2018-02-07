package lia.Monitor.JiniClient.Farms.OSGmap.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class LegendDialog extends JDialog {

	public static final Font textFont = new Font("Arial", Font.PLAIN, 14);

	private static final String inactiveText = "Inactive port, either unlicensed or not installed.";
	private static final String unconnectedLightText = "Unconnected port with light.";
	private static final String unconnectedInNoLightText = "Unconnected input port with no light";
	private static final String unconnectedOutNoLightText = "Unconnected output port with no light";
	private static final String connectedText = "Connected port (port 1 is connected to port 2).";
	private static final String connectedNoLightText = "Connected port (no input light).";
	private static final String faultText = "Fault condition, either bad port or failed connection.";
	public static final String[] otherText = new String[] { "Each port is represented by a pair of boxes. The top box", 
		"contains the port's number. The bottom box contains the ", "number of the connecting port. If the port is unconnected,", 
		"then the bottom box is empty." };
	
	public LegendDialog(JFrame owner) {
		
		super(owner, "Legend", true);
		setResizable(false);
		LegendPanel p = new LegendPanel();
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(p, BorderLayout.CENTER);
		setSize(450, 400);
	}
	
	class LegendPanel extends JPanel {
		
		public LegendPanel() {
			super();
			setBackground(Color.white);
		}
		
		private Color getColor(int state) {
			
			switch (state) {
			case PortsPanel.inactive: return PortsPanel.inactiveColor;
			case PortsPanel.unconnected_light: return PortsPanel.unconnectedLight;
			case PortsPanel.unconnected_innolight : return PortsPanel.unconnectedInNoLight;
			case PortsPanel.unconnected_outnolight : return PortsPanel.unconnectedOutNoLight;
			case PortsPanel.connected: return PortsPanel.connectedColor;
			case PortsPanel.connected_nolight: return PortsPanel.connectedNoLightColor;
			case PortsPanel.fault: return PortsPanel.faultColor;
			}
			return PortsPanel.faultColor;
		}
		
		private void drawPortRect(Graphics2D g2, String portName, int state, int x, int y, int w) {
			
			Color rectColor = getColor(state);
			g2.setColor(rectColor);
			g2.fillRect(x+1, y+1, w+8, w+8);
			g2.setColor(Color.lightGray);
			g2.drawRect(x, y, w+10, w+10);
			g2.drawRect(x+1, y+1, w+8, w+8);
			g2.drawRect(x+2, y+2, w+6, w+6);
			g2.setFont(PortsPanel.portFont);
			if (rectColor.equals(Color.white))
				g2.setColor(Color.black);
			else
				g2.setColor(Color.white);
			g2.drawString(portName, x+5, y+9+w/2);
		}

		
		public void paintComponent(Graphics g) {
			
			super.paintComponent(g);
			
			Graphics2D g2 = (Graphics2D)g;
			// draw the in title
			g2.setFont(PortsPanel.titleFont);
			FontMetrics fm = g2.getFontMetrics();
			int titleW = fm.stringWidth("LEGEND");
			g2.drawString("LEGEND", (getWidth()-titleW)/2, 20);
			g2.setFont(PortsPanel.portFont);
			fm = g2.getFontMetrics();
			int maxLen = fm.stringWidth("1");
			int w = fm.stringWidth("2");
			if (maxLen < w) maxLen = w;
			int startRect = 20;
			
			// draw the ports...
			int startY = 20 + fm.getHeight();
			drawPortRect(g2, "1", PortsPanel.inactive, startRect, startY, w);
			int newStartY = startY + maxLen + 12;
			drawPortRect(g2, "", PortsPanel.inactive, startRect, newStartY, w);
			int middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(inactiveText, startRect + 22 + maxLen, middleY);
			fm = g2.getFontMetrics();
			
			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.unconnected_light, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "", PortsPanel.unconnected_light, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(unconnectedLightText, startRect + 22 + maxLen, middleY);

			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.unconnected_innolight, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "", PortsPanel.unconnected_innolight, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(unconnectedInNoLightText, startRect + 22 + maxLen, middleY);

			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.unconnected_outnolight, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "", PortsPanel.unconnected_outnolight, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(unconnectedOutNoLightText, startRect + 22 + maxLen, middleY);

			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.connected, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "2", PortsPanel.connected, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(connectedText, startRect + 22 + maxLen, middleY);

			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.connected_nolight, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "2", PortsPanel.connected_nolight, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(connectedNoLightText, startRect + 22 + maxLen, middleY);
			
			startY = newStartY + maxLen + 20;
			drawPortRect(g2, "1", PortsPanel.fault, startRect, startY, w);
			newStartY = startY + maxLen + 12;
			drawPortRect(g2, "2", PortsPanel.fault, startRect, newStartY, w);
			middleY = (startY + newStartY) / 2 + fm.getHeight();
			g2.setFont(textFont);
			g2.setColor(Color.black);
			g2.drawString(faultText, startRect + 22 + maxLen, middleY);
			
			startY = newStartY + maxLen + 20 + fm.getHeight();
			for (int i=0; i<otherText.length; i++) {
				g2.drawString(otherText[i], startRect, startY);
				startY += 3 + fm.getHeight();
			}
		}
	}
}
