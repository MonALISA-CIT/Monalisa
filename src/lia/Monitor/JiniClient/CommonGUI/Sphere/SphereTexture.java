package lia.Monitor.JiniClient.CommonGUI.Sphere;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Utility class used to set a sphere texture
 */
public class SphereTexture {

	static final Color whiteColor = new Color(255, 255, 255, 80);
    static final Color highlightColor = new Color(255, 255, 50, 80);
    static final Color midtoneColor = new Color(200, 160, 40, 80);
	static final Color shadowColor = new Color(128, 30, 10, 80);
	static final Color blackColor = new Color(83, 19, 7, 80);
    static final float highlightInterval = 1, midtoneInterval = 2;
    static final float shadowInterval = 2, blackInterval = 2;
    static final Color gradientColors[] = {whiteColor, highlightColor, midtoneColor, shadowColor, blackColor};
    static final float gradientIntervals[] = {highlightInterval, midtoneInterval, shadowInterval, blackInterval};
	static final Color gradientRedColors[] = {whiteColor, new Color(247, 128, 0, 80), new Color(239, 28, 0, 80), new Color(184, 21, 0, 80), new Color(146, 3, 3, 80) };
	static final Color gradientYellowColors[] = { whiteColor, highlightColor, new Color(203, 203, 40, 80), new Color(173, 173, 34, 80), new Color(160, 160, 31, 80) };
	
	public static void setTexture(Graphics g, Rectangle size) {
		
        Rectangle gradientRect = new Rectangle(size.x-size.width/2, size.y-size.height/2, 3*size.width/2, 3*size.height/2);
		RadialGradientPaintExt sphereFilling = new RadialGradientPaintExt(gradientRect, gradientColors, gradientIntervals);
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
		((Graphics2D)g).setPaint(sphereFilling);
	}
	
	public static void setRedTexture(Graphics g, Rectangle size) {
		
        Rectangle gradientRect = new Rectangle(size.x-size.width/2, size.y-size.height/2, 3*size.width/2, 3*size.height/2);
        RadialGradientPaintExt sphereFilling = new RadialGradientPaintExt(gradientRect, gradientRedColors, gradientIntervals);
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D)g).setPaint(sphereFilling);
	}

	public static void setYellowTexture(Graphics g, Rectangle size) {
		
        Rectangle gradientRect = new Rectangle(size.x-size.width/2, size.y-size.height/2, 3*size.width/2, 3*size.height/2);
        RadialGradientPaintExt sphereFilling = new RadialGradientPaintExt(gradientRect, gradientYellowColors, gradientIntervals);
		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D)g).setPaint(sphereFilling);
	}
}
