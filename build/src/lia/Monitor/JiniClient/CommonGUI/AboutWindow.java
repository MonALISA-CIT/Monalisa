package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class AboutWindow extends SplashWindow 
						implements MouseListener {
	
	protected AboutWindow(Frame owner) {
		
		super(owner);
		addMouseListener(this);
	}
	
	public void update(Graphics g){
		paint(g);
	}
	
	public void paint(Graphics g) {
		
		if(offScreen == null){
			offScreen = createImage(imgWidth, imgHeight);
			offGraphics = offScreen.getGraphics();
		}
		if(offGraphics != null){
			offGraphics.clearRect(0, 0, imgWidth, imgHeight);
			offGraphics.drawImage(splashImage, 0, 0, this);
			offGraphics.setColor(Color.BLACK);
			offGraphics.drawRect(0, 0, imgWidth-1, imgHeight-1);
			((Graphics2D)offGraphics).setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
				    RenderingHints.VALUE_ANTIALIAS_ON);
			offGraphics.setFont(font);
			offGraphics.drawString(client, 10, 225);
			offGraphics.drawString("Version "+version, 10, 245);
			offGraphics.drawString("http://monalisa.caltech.edu", 388, 225);
			offGraphics.drawString(date, 388, 245);
		}
		g.drawImage(offScreen, 0, 0, this);
	}
	
	public static AboutWindow display(String client){
		Frame f = new Frame();
		AboutWindow w = new AboutWindow(f);
		w.client = client;
		// Show the window.
		w.toFront();
		w.setVisible(true);
		return w;
	}

	public void mouseClicked(MouseEvent e) {
		finishIt();
	}

	public void mouseEntered(MouseEvent e) {
	}
	
	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}
	
	public static void main(String [] args){
		/*AboutWindow aw = */AboutWindow.display("VRVS Client");
	}
}
