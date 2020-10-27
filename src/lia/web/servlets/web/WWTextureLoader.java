package lia.web.servlets.web;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;

/**
 * Image loader helper class
 */
public final class WWTextureLoader {
	private static final Logger logger = Logger.getLogger(WWTextureLoader.class.getName());

	private static Hashtable<String, BufferedImage> textures = new Hashtable<>();

	/**
	 * Load an image from an URL
	 * 
	 * @param url image URL
	 * @return the image contents, or null if there was a problem loading the image (see the log file for details in this case)
	 */
	public static BufferedImage loadImage(final URL url) {
		BufferedImage res = null;

		try {
			final ImageIcon icon = new ImageIcon(url);

			if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
				throw new Exception("failed : status = " + icon.getImageLoadStatus());
			}
			final Component obs = new Component() {
				private static final long serialVersionUID = 1L;
			};

			final int width = icon.getIconWidth();
			final int height = icon.getIconHeight();

			res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D big = res.createGraphics();
			icon.paintIcon(obs, big, 0, 0);
			big.dispose();
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Failed loading image from " + url.toString(), e);
		}

		return res;
	}

	/**
	 * Get the texture represented by the given file name. This function will cache loaded textures
	 * so that when the method is called with the same parameter it will return the already loaded image.
	 * 
	 * @param fileName
	 * @return the image, or null if there is a problem loading the texture
	 */
	synchronized public static BufferedImage getTexture(final String fileName) {
		BufferedImage texture = textures.get(fileName);

		if (texture == null) {
			ClassLoader myClassLoader = WWTextureLoader.class.getClassLoader();
			try {
				URL imageURL;
				if (fileName.indexOf("://") >= 0) {
					imageURL = new URL(fileName);
				}
				else {
					imageURL = myClassLoader.getResource(fileName);
				}
				if (imageURL == null) {
					System.out.println("imageURL is NULL. fileName=" + fileName);
				}
				else {
					texture = loadImage(imageURL);

					if (texture != null) {
						textures.put(fileName, texture);
						logger.log(Level.INFO, "Image " + fileName + " loaded...");
					}
					else {
						logger.log(Level.WARNING, "Image " + fileName + " failed to load!");
					}
				}
			}
			catch (Exception ex) {
				logger.log(Level.WARNING, "Failed loading image from " + fileName, ex);
			}
		}

		return texture;
	}
}
