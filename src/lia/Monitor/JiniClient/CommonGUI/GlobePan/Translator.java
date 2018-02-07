package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.event.MouseEvent;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Vector3d;

@SuppressWarnings("restriction")
public class Translator extends Manipulator {

	public static double MINIMUM_DISTANCE = 1.1;

	public static double DEFAULT_TRANSLATE_SPEED = 0.005;
	double translateSpeed = DEFAULT_TRANSLATE_SPEED;

	TransformGroup transformGroup;

	int button = 2;
	int buttonMask = MouseEvent.BUTTON2_MASK;

	int x_last;
	int y_last;

	public Translator(TransformGroup transformGroup) {
		this.transformGroup = transformGroup;
	}

	public void setSensitivity(double sensitivity) {
		translateSpeed = sensitivity * DEFAULT_TRANSLATE_SPEED;
	}

	public void setButton(int b) {
		if (b > 0)
			button = b;
		else if (button == -b)
			if (b == -2)
				button = 0;
			else
				button = 2;

		if (button == 1)
			buttonMask = MouseEvent.BUTTON1_MASK;
		else if (button == 2)
			buttonMask = MouseEvent.BUTTON2_MASK;
		else if (button == 3)
			buttonMask = MouseEvent.BUTTON3_MASK;
		else
			buttonMask = 0;
	}

	/**
	 * Calculates the translation-specific zoom factor.
	 * 
	 * @param w
	 *            canvas width
	 * @param h
	 *            canvas height
	 */
	/*
	 * public void calculateZoomFactor(int w, int h) { Transform3D transform =
	 * new Transform3D(); transformGroup.getTransform(transform);
	 * 
	 * Vector3d translation = new Vector3d(); transform.get(translation); double
	 * r = translation.length();
	 * 
	 * // FIXME: make this a calculation and not a hack zoom_factor = 0.6 *
	 * Math.sqrt(r/4.0) * r/4.0 * 400.0/w; }
	 */

	@Override
	public void mouseDragged(MouseEvent e) {
		if ((e.getModifiers() & buttonMask) == 0)
			return;

		int dx = e.getX() - x_last;
		int dy = e.getY() - y_last;

		Transform3D transform = new Transform3D();
		transformGroup.getTransform(transform);

		Vector3d translation = new Vector3d();
		transform.get(translation);

		translation.add(new Vector3d(dx * translateSpeed, -dy * translateSpeed, 0));

		// This will prevent the user from tranlating _into_ the globe.
		if (translation.length() < MINIMUM_DISTANCE) {
			translation.normalize();
			translation.scale(MINIMUM_DISTANCE);
		}

		transform.setTranslation(translation);
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
