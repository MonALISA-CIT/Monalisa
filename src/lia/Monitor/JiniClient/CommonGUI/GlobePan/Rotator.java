package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.event.MouseEvent;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

@SuppressWarnings("restriction")
public class Rotator extends Manipulator {

	/** Angular velocity about the axis cutting through the equator. */
	public final static double DEFAULT_TILT_SPEED = 0.007;
	double tiltSpeed = DEFAULT_TILT_SPEED;

	/** Angular velocity about the Earth's axis. */
	public final static double DEFAULT_SPIN_SPEED = 0.007;
	double spinSpeed = DEFAULT_SPIN_SPEED;

	/** TransformGroup to be manipulated. */
	TransformGroup transformGroup;

	/** Which button this rotator responds to. */
	int button = 3;
	int buttonMask = MouseEvent.BUTTON3_MASK;

	int x_last;
	int y_last;

	public Rotator(TransformGroup transformGroup) {
		this.transformGroup = transformGroup;
	}

	public void setSensitivity(double sensitivity) {
		tiltSpeed = sensitivity * DEFAULT_TILT_SPEED;
		spinSpeed = sensitivity * DEFAULT_SPIN_SPEED;
	}

	/**
	 * Set the button that this manipulator responds to. If b is negative, the
	 * button will be set to anything _but_ |b|.
	 */
	public void setButton(int b) {
		if (b > 0)
			button = b;
		else if (button == -b)
			if (b == -3)
				button = 0;
			else
				button = 3;

		if (button == 1)
			buttonMask = MouseEvent.BUTTON1_MASK;
		else if (button == 2)
			buttonMask = MouseEvent.BUTTON2_MASK;
		else if (button == 3)
			buttonMask = MouseEvent.BUTTON3_MASK;
		else
			buttonMask = 0;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		// Only handle the event if the proper button is down
		if ((e.getModifiers() & buttonMask) == 0)
			return;

		int dx = e.getX() - x_last;
		int dy = e.getY() - y_last;

		Transform3D transform = new Transform3D();
		transformGroup.getTransform(transform);

		if (dx != 0) {
			Transform3D spin = new Transform3D();
			spin.rotY(dx * spinSpeed);
			transform.mul(spin);
		}

		if (dy != 0) {
			Matrix3d rot = new Matrix3d();
			transform.get(rot);
			rot.invert();
			Vector3d X = new Vector3d(1, 0, 0);
			rot.transform(X);
			Transform3D tilt = new Transform3D();
			tilt.set(new AxisAngle4d(X, dy * tiltSpeed));
			transform.mul(tilt);
		}

		transformGroup.setTransform(transform);

		x_last += dx;
		y_last += dy;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == button) {
			x_last = e.getX();
			y_last = e.getY();
		}
	}

}
