package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.media.j3d.Behavior;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.WakeupCriterion;
import javax.media.j3d.WakeupOnElapsedTime;

@SuppressWarnings("restriction")
public class EarthSpinBehavior extends Behavior implements WakeUpperInt {

	public TransformGroup spin;
	private double speed = 0;
	public double extraRotation = 0;
	
	// Update the Earth's spin every minute
	public WakeupCriterion criterion = new WakeupOnElapsedTime(10000);

	private WakeUpper wkupper = new WakeUpper(this, 15);

	public EarthSpinBehavior(TransformGroup spin) {
		this.spin = spin;
	}

	public void initialize() {
		updateEarthSpin();
		wakeupOn(criterion);
	}

	synchronized public void wakeUp() {
		// We only registered for one wakeup criterion, so we don't have
		// to check what criteria actually is.
		updateEarthSpin();
	}

	public void processStimulus(Enumeration en){
		wakeUp();
		wakeupOn(criterion);	
	}

	public void updateEarthSpin() {
		GregorianCalendar calendar =
			new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.setTime(new Date());

		// The default orientation of spin (i.e., when spin is the identity)
		// puts the Sun at noontime GMT. So to calculate how far we
		// have to rotate the Earth, we need to know how far it is until/past
		// noontime GMT. (There are 1440 minutes, approximately, in a day,
		// so noon is 720 of those minutes.)
		double minutesPastNoonGMT =
			 60 * calendar.get(Calendar.HOUR_OF_DAY)
				+ calendar.get(Calendar.MINUTE)
				- 720;
		
		//System.out.println("Spinnning...");

		extraRotation += speed / 7.0; 

		minutesPastNoonGMT += extraRotation;
		if(minutesPastNoonGMT > 1440)		// adjust if necesary 
			minutesPastNoonGMT -= 1440.0;
		
		Transform3D transform = new Transform3D();
		transform.rotY(minutesPastNoonGMT / 1440.0 * 2 * Math.PI);
		spin.setTransform(transform);
	}

	public void reset(){
		extraRotation = 0;  // set everything to current, real time
		updateEarthSpin();
		wkupper.pause();
	}

	public void setSpeed(int val) {
		speed = val;
		if(val == 0){
			wkupper.pause();
		}else{
			wkupper.unpause();
		}
	}
}

interface WakeUpperInt {
	public void wakeUp();
}

class WakeUpper extends Thread {
	private long period;
	private WakeUpperInt up;
	private Object syncObj = new Object();
	private boolean paused;
	private boolean done;
		
	WakeUpper(WakeUpperInt up, long period){
		super("WakeUpper");
		this.up = up;
		this.period = period;
		paused = true;
		done = false;
		start();
	}
	
	public void setPeriod(long period){
		this.period = period;
	}
	
	public void pause(){
		paused = true;
	}
	
	public void unpause(){
		paused = false;
		synchronized(syncObj){	
			syncObj.notify();
		}
	}
	
	public boolean isPaused(){
		return paused;
	}
		
	public void run(){
		while(!done){
			if(paused){
				try {
					synchronized(syncObj){
						syncObj.wait();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			up.wakeUp();
			try {
					sleep(period);			// take a break
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
