package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.Font;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Font3D;
import javax.media.j3d.FontExtrusion;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Text3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

@SuppressWarnings("restriction")
public class Tooltip3D extends BranchGroup {

	private TransformGroup transf;
	private String text = "";
	private double baseScale = 0.018;

	public Tooltip3D(Transform3D transfIni) {
		setCapability(ALLOW_DETACH);
		Transform3D t3d = new Transform3D();
		TransformGroup transfLocation = new TransformGroup(transfIni);

		transf = new TransformGroup();
		transf.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		transf.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);

		transfLocation.addChild(transf);
		addChild(transfLocation);
	}

	public void setLabel(String text) {
		if (!this.text.equals(text)) {
			this.text = text;
			build();
		}
	}

	public void setScale(double scale) {
		scale *= baseScale;
		Transform3D t = new Transform3D();
		transf.getTransform(t);
		t.setScale(scale);
		transf.setTransform(t);
	}

	private int splitText(String text) {
		int p = text.length() / 2;
		if (text.charAt(p) == ' ')
			return p;
		for (int i = 1; i < p; i++) {
			if (text.charAt(p - i) == ' ')
				return p - i;
			if (text.charAt(p + i) == ' ')
				return p + i;
		}
		return text.length();
	}

	private void build() {
		transf.removeAllChildren();
		Font3D f3d = new Font3D(new Font("System", Font.PLAIN | Font.CENTER_BASELINE, 2), new FontExtrusion());
		// int pos = splitText(text);
		// String text1 = text.substring(0, pos).replaceAll("]", " ]");
		// String text2 = text.substring(pos+1).replaceAll("]", " ]");
		// Text3D txt1 = new Text3D(f3d, text1, new Point3f(0, 3.5f, 1.5f),
		// Text3D.ALIGN_CENTER, Text3D.PATH_RIGHT);
		String text2 = text;
		Text3D txt2 = new Text3D(f3d, text2, new Point3f(0, 2.5f, 2f), Text3D.ALIGN_CENTER, Text3D.PATH_RIGHT);
		// Shape3D sh1 = new Shape3D();
		Shape3D sh2 = new Shape3D();

		Appearance app = new Appearance();
		Color3f white = new Color3f(1f, 1f, 1f);
		Color3f orange = new Color3f(1f, 1f, 1f);
		Material mm = new Material(white, orange, white, orange, 100);
		mm.setLightingEnable(true);
		app.setMaterial(mm);

		// sh1.setGeometry(txt1);
		// sh1.setAppearance(app);
		// transf.addChild(sh1);

		sh2.setGeometry(txt2);
		sh2.setAppearance(app);
		transf.addChild(sh2);

		setScale(1.0);
	}
}
