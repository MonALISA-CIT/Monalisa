package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.net.URL;


public class SplashWindow extends Window {
	String splashImageFile = "lia/images/ml_loading_logo.jpg";
	Font font = new Font(null, Font.BOLD, 14);
	Image splashImage;
	Image offScreen;
	Graphics offGraphics;
	public Frame container;
	int imgWidth, imgHeight;
	String client = "";
	String version = "16.11.05-b201611240806";
	String date = "201611240806";
	String status = "";
	int percent = 0;
	
	protected SplashWindow(Frame owner){
		super(owner);
		container = owner;
		try {
			ClassLoader myClassLoader = getClass().getClassLoader();
			URL url = myClassLoader.getResource(splashImageFile);
			splashImage = Toolkit.getDefaultToolkit().createImage(url);
			// Load the image
			MediaTracker mt = new MediaTracker(this);
			mt.addImage(splashImage,0);
			try {
				mt.waitForID(0);
			} catch(InterruptedException ie) {}
		}catch(Exception ex){
			ex.printStackTrace();
		}
		
		// Center the window on the screen.
		imgWidth = splashImage.getWidth(this);
		imgHeight = splashImage.getHeight(this);  
		setSize(imgWidth, imgHeight);
		Dimension screenDim =
			Toolkit.getDefaultToolkit().getScreenSize();
		setLocation((screenDim.width - imgWidth) / 2,
					(screenDim.height - imgHeight) / 2);
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
			offGraphics.drawString(client, 10, 223);
			offGraphics.drawString("Version: " + version, 10, 243);
			offGraphics.drawString(status, 187, 223);
			int barWidth = 485;
			int solidGreen = 192;
			int varianceDelta = 100;
			int green = 0;
			for(int i=0; i< barWidth; i++){
				int poz = (barWidth * percent) / 100;
				if(i<=poz)
					green = solidGreen;
				else if(i > poz+varianceDelta)
					green = 0;
				else
					green = (int) Math.round(solidGreen * (1.0 - (i-poz+0.0)/varianceDelta));
				offGraphics.setColor(new Color(0, green, 0));
				offGraphics.drawLine(187+i, 230, 187+i, 245);
			}
			g.drawImage(offScreen, 0, 0, this);
		}
	}
	
	public void setStatus(String status, int percent){
		this.status = status;
		this.percent = percent;
		repaint();
	}
	
	public static SplashWindow splash(String client){
		Frame f = new Frame();
		SplashWindow w = new SplashWindow(f);
		w.client = client;
		// Show the window.
		String sDontFront = System.getProperty("lia.Monitor.JiniClient.Splash.dontFront");
		if ( sDontFront==null || !sDontFront.equals("true") )
			w.toFront();
		String sHide = System.getProperty("lia.Monitor.JiniClient.Splash.hide");
		if ( sHide==null || !sHide.equals("true") )
			w.setVisible(true);
		return w;
	}
	
	public void finishIt(){
		container.setVisible(false);
		if ( offGraphics!=null )
			offGraphics.dispose();
		container.dispose();
		container = null;
		offGraphics = null;
	}
	
	public static void main(String [] args){
		SplashWindow spw = SplashWindow.splash("VRVS Client");
		try{
			spw.setStatus("", 0);
			Thread.sleep(1000);
			spw.setStatus("Starting...", 20);
			Thread.sleep(1000);
			spw.setStatus("Please Wait While Loading ...", 60);
			Thread.sleep(1000);
			spw.setStatus("Almost done...", 80);
			Thread.sleep(1000);
			spw.setStatus("", 100);
			Thread.sleep(1000);
		}catch(Exception ex){
		}
		spw.finishIt();
	}
}
