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
import javax.vecmath.Vector3d;

@SuppressWarnings("restriction")
public class MoonSpinBehavior extends Behavior {
	public static double DISTANCE_FROM_EARTH = 10;	// should be 60
	public static double MAX_TILT = Math.PI / 30;	// should be PI/36
	
	TransformGroup moonSpin;
	private double speed = 0;
	private double extraTilt = 0;
	private double extraRevol = 0;
	public long count = -1;
	
	// Update the Earth's spin every minute
	public WakeupCriterion criterion = new WakeupOnElapsedTime(30);

	public MoonSpinBehavior(TransformGroup moonSpin){
		this.moonSpin = moonSpin;
	}
	
	public void initialize() {
		updateMoonSpin();
		wakeupOn(criterion);

	}

	public void reset(){
		extraRevol = 0;  // set everything to current, real time
		extraTilt = 0;
		count = -1;		   // make sure it resets _now_
		updateMoonSpin();
	}

	public void setSpeed(int val) {
		speed = val;
	}

	public void processStimulus(Enumeration arg0) {
		updateMoonSpin();
		wakeupOn(criterion);
	}

	void updateMoonSpin(){
		count++;
		if(speed == 0 && count % 100 != 0){
			return;
		}

		GregorianCalendar calendar =
			new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.setTime(new Date());

		double revolPeriod = calendar.getActualMaximum(Calendar.DAY_OF_MONTH) * 24 * 60;
		double crtRevolTime = 60 * 24 * calendar.get(Calendar.DAY_OF_MONTH) +
							  60 * calendar.get(Calendar.HOUR_OF_DAY) +
							  calendar.get(Calendar.MINUTE);

		double tiltPeriod = calendar.getActualMaximum(Calendar.DAY_OF_YEAR) * 24 * 60;
		double crtTiltTime = 24 * 60 * calendar.get(Calendar.DAY_OF_YEAR) +
							 60 * calendar.get(Calendar.HOUR_OF_DAY) +
							 calendar.get(Calendar.MINUTE);

		extraRevol += speed / 3.294;
		extraTilt += speed / 2; 
		
		crtRevolTime = (crtRevolTime + extraRevol) % revolPeriod;
		crtTiltTime = (crtTiltTime + extraTilt) % tiltPeriod;
		
		double rotY = crtRevolTime / revolPeriod * 2 * Math.PI;
		
		double val = crtTiltTime / tiltPeriod;
		// compute Z axis rotation
		double rotZ = 0;
		if(val < 0.5){ 
			rotZ = -MAX_TILT + val*2*2*MAX_TILT;
		}else{
			rotZ = 	MAX_TILT - (val - 0.5)*2*2*MAX_TILT;				
		}
		// compute X axis rotation
		double rotX = 0;
		val += 0.25; 	// adjust the phase forwarding it with 1/4.
		if(val >= 1.0) val -= 1.0;
		if(val < 0.5){ 
			rotX = -MAX_TILT + val*2*2*MAX_TILT;
		}else{
			rotX = 	MAX_TILT - (val - 0.5)*2*2*MAX_TILT;				
		}
	
		Vector3d pos = new Vector3d(DISTANCE_FROM_EARTH, 0.0, 0.0);
		Transform3D transform = new Transform3D();
		transform.rotY(rotY);
		transform.transform(pos);		
		transform.rotZ(rotZ);
		transform.transform(pos);
		transform.rotX(rotX);
		transform.transform(pos);		
		//transform.set(pos);

		// prepare final transformation (including moon self-axis rotation)
		Transform3D tempTransf = new Transform3D();
		transform.rotX(rotX);
		tempTransf.rotY(rotY - Math.PI/2);
		transform.mul(tempTransf);
		tempTransf.rotZ(rotZ);
		transform.mul(tempTransf);
		transform.setTranslation(pos);

		moonSpin.setTransform(transform);
	}
}
