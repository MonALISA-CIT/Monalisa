package lia.Monitor.AppControlClient;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

public class ImageFill extends Fill {

    private final static int IMAGE_CACHE_SIZE = 8;

    private BufferedImage image;

    private BufferedImage[] imageCache = new BufferedImage[IMAGE_CACHE_SIZE];

    private int imageCacheIndex = 0;

    public ImageFill(BufferedImage image) {
        this.image = image;
    }

    public ImageFill() {
        this.image = null;
    }

    public BufferedImage getImage() {
        return image;
    }

    /**
     * Set the image that the <code>paintFill</code> method draws.
     * 
     * @param image
     *            the new value of the <code>image</code> property
     * @see #getImage
     * @see #paintFill
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        for (int i = 0; i < imageCache.length; i++) {
            imageCache[i] = null;
        }
    }

    /**
     * Returns the actual width of the <code>BufferedImage</code> rendered by
     * the <code>paintFill</code> method. If the image property hasn't been
     * set, -1 is returned.
     * 
     * @return the value of <code>getImage().getWidth()</code> or -1 if
     *         getImage() returns null
     * @see #getHeight
     * @see #setImage
     */
    public int getWidth() {
        BufferedImage image = getImage();
        return (image == null) ? -1 : image.getWidth();
    }

    /**
     * Returns the actual height of the <code>BufferedImage</code> rendered
     * by the <code>paintFill</code> method. If the image property hasn't
     * been set, -1 is returned.
     * 
     * @return the value of <code>getImage().getHeight()</code> or -1 if
     *         getImage() returns null
     * @see #getWidth
     * @see #setImage
     */
    public int getHeight() {
        BufferedImage image = getImage();
        return (image == null) ? -1 : image.getHeight();
    }

    /**
     * Create a copy of image scaled to width,height w,h and add it to the null
     * element of the imageCache array. If the imageCache array is full, then
     * we replace the "least recently used element", at imageCacheIndex.
     */
    private BufferedImage createScaledImage(Component c, int w, int h) {
        GraphicsConfiguration gc = c.getGraphicsConfiguration();
        BufferedImage newImage = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);

        boolean cacheOverflow = true;
        for (int i = 0; i < imageCache.length; i++) {
            Image image = imageCache[i];
            if (image == null) {
                imageCache[i] = newImage;
                cacheOverflow = false;
                break;
            }
        }
        if (cacheOverflow) {
            imageCache[imageCacheIndex] = newImage;
            imageCacheIndex = (imageCacheIndex + 1) % imageCache.length;
        }

        Graphics g = newImage.getGraphics();
        int width = image.getWidth();
        int height = image.getHeight();
        g.drawImage(image, 0, 0, w, h, 0, 0, width, height, null);
        g.dispose();

        return newImage;
    }

    /**
     * Returns either the image itself or a cached scaled copy.
     */
    private BufferedImage getFillImage(Component c, int w, int h) {
        if ((w == getWidth()) && (h == getHeight())) { return image; }
        for (int i = 0; i < imageCache.length; i++) {
            BufferedImage cimage = imageCache[i];
            if (cimage == null) {
                break;
            }
            if ((cimage.getWidth(c) == w) && (cimage.getHeight(c) == h)) { return cimage; }
        }
        return createScaledImage(c, w, h);
    }

    /**
     * Draw the image at <i>r.x,r.y </i>, scaled to <i>r.width </i> and
     * <i>r.height </i>.
     */
    public void paintFill(Component c, Graphics g, Rectangle r) {
        if ((r.width > 0) && (r.height > 0)) {
            BufferedImage fillImage = getFillImage(c, r.width, r.height);
            g.drawImage(fillImage, r.x, r.y, c);

        }
    }
}
