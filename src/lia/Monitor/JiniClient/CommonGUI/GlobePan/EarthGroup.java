package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.image.BufferedImage;
import java.util.TimerTask;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Texture;
import javax.media.j3d.Texture2D;
import javax.media.j3d.TextureAttributes;
import javax.vecmath.Color3f;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.monitor.AppConfig;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;

@SuppressWarnings("restriction")
public class EarthGroup extends BranchGroup {

	Texture image = null;
	int divisions = 64;
	Sphere earth;
	BufferedImage buffTexture;

	private String textureScaleFilter = AppConfig.getProperty("lia.Monitor.globeTexture.scaleFilter", "nice"); // or
																												// "fast"

	public EarthGroup() {
		this(null);
	}

	public EarthGroup(BufferedImage bfImage) {
		buffTexture = bfImage;
		ImageComponent2D icloc = new ImageComponent2D(ImageComponent2D.FORMAT_RGB4, bfImage);
		icloc.setCapability(ImageComponent2D.ALLOW_IMAGE_READ);
		icloc.setCapability(ImageComponent2D.ALLOW_IMAGE_WRITE);
		image = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, bfImage.getWidth(), bfImage.getHeight());
		image.setImage(0, icloc);
		int scaleFilter = Texture.FASTEST;
		if (textureScaleFilter.equals("nice"))
			scaleFilter = Texture.NICEST;
		image.setMagFilter(scaleFilter); // FASTEST
		image.setMinFilter(scaleFilter); // FASTEST
		setCapability(ALLOW_DETACH);
		build();

		refreshTexture();

		TimerTask ttask = new TimerTask() {
			@Override
			public void run() {
				try {
					refreshTexture();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		};
		BackgroundWorker.schedule(ttask, 10 * 60 * 1000, 10 * 60 * 1000);
	}

	private void refreshTexture() {
		// System.out.println("starting texture refresh @ "+ new Date());
		Appearance appear = earth.getAppearance();
		Texture2D texture = (Texture2D) appear.getTexture();
		ImageComponent2D icloc = (ImageComponent2D) texture.getImage(0);
		icloc.set(buffTexture);
		// System.out.println("texture refreshed");
	}

	void build() {
		Appearance appear = new Appearance();
		appear.setCapability(Appearance.ALLOW_TEXTURE_READ);
		// If an image was given, display the Earth with a texture.
		if (image != null) {
			/*
			 * // Set up the texture map TextureLoader tex = new
			 * TextureLoader(texImage, this); app.setTexture(tex.getTexture());
			 * 
			 * // Set up the material properties app.setMaterial(new
			 * Material(white, black, white, black, 1.0f)); TextureAttributes
			 * texAttr = new TextureAttributes();
			 * texAttr.setTextureMode(TextureAttributes.MODULATE);
			 * app.setTextureAttributes(texAttr);
			 * 
			 * break;
			 */
			image.setCapability(Texture.ALLOW_IMAGE_READ);
			image.setCapability(Texture.ALLOW_IMAGE_WRITE);
			// set texture map
			appear.setTexture(image);

			// set material properties
			Color3f white = new Color3f(1f, 1f, 1f);
			Color3f black = new Color3f(0, 0, 0);
			appear.setMaterial(new Material(white, black, white, black, 1.0f));

			TextureAttributes texAttr = new TextureAttributes();
			texAttr.setTextureMode(TextureAttributes.MODULATE);
			appear.setTextureAttributes(texAttr);

			/*
			 * Material mat = new Material(); mat.setAmbientColor();
			 * mat.setSpecularColor(); appear.setMaterial(mat);
			 * 
			 * TextureAttributes attr = new TextureAttributes();
			 * attr.setTextureMode(TextureAttributes.MODULATE);
			 * appear.setTextureAttributes(attr);
			 * 
			 * Texture2D texture = new Texture2D(Texture.BASE_LEVEL,
			 * Texture.RGBA, image.getWidth(), image.getHeight());
			 * texture.setImage(0, image);
			 * texture.setMagFilter(Texture.BASE_LEVEL_LINEAR);
			 * texture.setMinFilter(Texture.BASE_LEVEL_LINEAR);
			 * texture.setEnable(true); appear.setTexture(texture);
			 */
		}
		// Otherwise just display a solid blue sphere.
		else {
			Material mat = new Material();
			mat.setAmbientColor(new Color3f(0, 0.2f, 0.3f));
			mat.setDiffuseColor(new Color3f(0, 0.3f, 0.4f));
			mat.setSpecularColor(new Color3f(0, 0, 0));
			appear.setMaterial(mat);
		}

		earth = new Sphere(1, Primitive.GENERATE_NORMALS | Primitive.GENERATE_TEXTURE_COORDS, divisions, appear);
		earth.getShape().setCapability(Shape3D.ALLOW_APPEARANCE_READ);
		addChild(earth);
		compile();
	}

}
