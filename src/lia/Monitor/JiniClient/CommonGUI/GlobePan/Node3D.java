package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.TriangleArray;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;

import lia.Monitor.JiniClient.CommonGUI.rcNode;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;

// this is a wrapper for the InnerNode3D, former Node3D created
// to support adding & removing ony one node at a time, to avoid
// the necessity of rebuilding the whole scene for each operation
// of this kind.
@SuppressWarnings("restriction")
public class Node3D extends BranchGroup {

	InnerNode3D realNode;
	double currentScale = 1.0;

	public Node3D(rcNode n) {
		realNode = new InnerNode3D(n);
		setCapability(ALLOW_DETACH);
		setCapability(ALLOW_CHILDREN_EXTEND);
		setCapability(ALLOW_CHILDREN_READ);
		setCapability(ALLOW_CHILDREN_WRITE);
		addChild(realNode);
		compile();
	}

	public void setScale(double scale) {
		realNode.setScale(scale);
		currentScale = scale;
	}

	public void setColor(Color3f color) {
		realNode.setColor(color);
	}

	public void setTooltipText(String text) {
		realNode.tooltip.setLabel(text);
		realNode.tooltip.setScale(currentScale);
	}

	public void showTooltip() {
		if (!realNode.tooltip.isLive()) {
			addChild(realNode.tooltip);
			// System.out.println("Showing tooltip for "+n.UnitName);
		}
	}

	public void hideTooltip() {
		if (realNode.tooltip.isLive()) {
			realNode.tooltip.detach();
			removeChild(realNode.tooltip);
			// System.out.println("Hiding tooltip for "+n.UnitName);
		}
	}

	// ///////// real Node3D /////////////

	private class InnerNode3D extends TransformGroup {
		rcNode n;
		Tooltip3D tooltip;
		float zoom = 1.0f;
		Shape3D shape;

		public InnerNode3D(rcNode n) {
			this.n = n;

			WorldCoordinates loc = new WorldCoordinates(n.LAT, n.LONG);
			setTransform(loc.toTransform());
			setCapability(ALLOW_TRANSFORM_READ);
			setCapability(ALLOW_TRANSFORM_WRITE);

			buildShape();
			tooltip = new Tooltip3D(loc.toTransform());
		}

		public void setScale(double scale) {
			Transform3D t = new Transform3D();
			getTransform(t);
			t.setScale(scale);
			setTransform(t);
		}

		public void setColor(Color3f color) {
			if (color == null)
				return;

			Material mat = shape.getAppearance().getMaterial();
			mat.setDiffuseColor(0.8f * color.x, 0.8f * color.y, 0.8f * color.z);
			mat.setAmbientColor(0.6f * color.x, 0.6f * color.y, 0.6f * color.z);
		}

		double NODE_WIDTH = 0.06;

		void buildShape() {
			Material shapeMat = new Material();
			shapeMat.setCapability(Material.ALLOW_COMPONENT_WRITE);
			shapeMat.setCapability(Material.ALLOW_COMPONENT_READ);
			shapeMat.setSpecularColor(0, 0, 0);
			shapeMat.setDiffuseColor(0.6f, 0, 0);
			shapeMat.setAmbientColor(0.6f, 0, 0);

			Appearance shapeAppear = new Appearance();
			shapeAppear.setCapability(Appearance.ALLOW_MATERIAL_READ);
			shapeAppear.setCapability(Appearance.ALLOW_MATERIAL_WRITE);
			shapeAppear.setMaterial(shapeMat);

			buildSpehreShape(shapeAppear);
			// buildPyramidShape(shapeAppear);
		}

		void buildSpehreShape(Appearance appear) {
			Sphere sphere = new Sphere((float) NODE_WIDTH / 2, Primitive.GENERATE_NORMALS, 8, appear);
			shape = sphere.getShape();
			shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
			shape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
			shape.setUserData(n);
			addChild(sphere);
		}

		void buildPyramidShape(Appearance appear) {
			double w = NODE_WIDTH;
			Point3d tip = new Point3d(0, w, 0);
			Point3d left = new Point3d(-w / 2, 0, 0);
			Point3d right = new Point3d(w / 2, 0, 0);
			Point3d up = new Point3d(0, 0, -w / 2);
			Point3d down = new Point3d(0, 0, w / 2);

			Point3d[] triangles = new Point3d[] { tip, down, right, tip, right, up, tip, up, left, tip, left, down };
			TriangleArray pyramidGeom = new TriangleArray(12, GeometryArray.COORDINATES | GeometryArray.NORMALS);
			pyramidGeom.setCoordinates(0, triangles);

			shape = new Shape3D(pyramidGeom, appear);
			shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
			shape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
			shape.setUserData(n);
			addChild(shape);
		}
	}
}
