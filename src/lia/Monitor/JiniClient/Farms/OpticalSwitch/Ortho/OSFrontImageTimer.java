package lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho;

import java.awt.Component;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.JiniClient.Farms.OpticalSwitch.OpticalSwitchGraphPan;

public class OSFrontImageTimer implements Runnable {

	protected Component comp = null;
	protected Object lock = null;
	protected Hashtable blinkMapping = null;
	protected boolean runThread = false;
	
	public OSFrontImageTimer(Component comp, Object lock, Hashtable blinkMapping) {
		
		this.comp = comp;
		this.lock = lock;
		this.blinkMapping = blinkMapping;
	}
	
	public void stop() {
		
		runThread = false;
	}
	
	public synchronized void start() {
		
		runThread = true;
		notifyAll();
	}
	
	public boolean getRunning() {
		
		return runThread;
	}
	
	public void run() {
		
		while (true) {
			
			synchronized (this) {
				while (!runThread) {
					try {
						wait();
					} catch (Exception e) { }
				}
			}
			
			if (blinkMapping != null) {
				synchronized (lock) {
					Vector v = new Vector();
					for (Enumeration en = blinkMapping.keys(); en.hasMoreElements(); ) {
						Object key = en.nextElement();
						if (key == null) continue;
						if (!v.contains(key)) {
							boolean blink = ((Boolean)blinkMapping.get(key)).booleanValue();
							blink = !blink;
							blinkMapping.put(key, new Boolean(blink));
							v.add(key);
						}
					}
				}
				if (comp != null)
					comp.repaint();
				if (OpticalSwitchGraphPan.OSConToolTip.panelIn != null) {
					OpticalSwitchGraphPan.OSConToolTip.panelIn.setNextStep();
					OpticalSwitchGraphPan.OSConToolTip.panelIn.repaint();
				}
				if (OpticalSwitchGraphPan.OSConToolTip.panelOut != null) {
					OpticalSwitchGraphPan.OSConToolTip.panelOut.setNextStep();
					OpticalSwitchGraphPan.OSConToolTip.panelOut.repaint();
				}
			}
			try {
				Thread.sleep(1000);
			} catch (Exception e) { }
		}
	}
	
}
