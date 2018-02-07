
package lia.Monitor.AppControlClient ;


import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.border.Border;


public class BorderFill extends Fill implements Border
{
    private final Fill[] fills;
    private final Dimension[] sizes;
    private BufferedImage buffer = null;

    public BorderFill(Fill[] fills, Dimension[] sizes) {
	// assert that both arrays are length 8
	this.fills = (Fill[])(fills.clone());
	this.sizes = (Dimension[])(sizes.clone());
    }

    public BorderFill(BufferedImage image, Rectangle[] rectangles, boolean isTile[]) {
	// assert that rectangles is length 8
	// assert that isTile is length 4
	sizes = new Dimension[8];
	Fill fills[] = new Fill[8];
	for(int i = 0; i < fills.length; i++) {
	    Rectangle r = rectangles[i];
	    BufferedImage sample = image.getSubimage(r.x, r.y, r.width, r.height);
            ImageFill fill = new ImageFill(sample);
	    if ((i % 2 == 0) &&  isTile[i / 2]) {
		fills[i] = new TiledFill(fill, r.width, r.height);
	    }
	    else {
		fills[i] = fill;
	    }
	    sizes[i] = new Dimension(r.width, r.height);
	}
        this.fills = (Fill[])(fills.clone());
    }

    public BorderFill(BufferedImage image, Rectangle[] rectangles) {
        this(image, rectangles, new boolean[]{true, true, true, true});
    }


    public Fill[] getFills() {
	return fills;
    }

    public void setFills(Fill[] fills) {
	// assert fills.length == 8
	System.arraycopy(fills, 0, this.fills, 0, 8);
    }

    public Dimension[] getSizes() {
	return sizes;
    }

    public void setSizes(Dimension[] sizes) {
	// assert sizes.length == 8
	System.arraycopy(sizes, 0, this.sizes, 0, 8);
    }

    /**
     * <pre>
     *	 x,y   xt1                  xt2
     *     +----+--------------------+-------+
     *     | 7  |        0           |   1   |
     *     |    |                    |       |
     *     | xl +--------------------+       |
     * yl1 +-+--+                    | xr    |
     *     | |                       +-+-----+	yr1
     *     |6|                         |  2  |
     *     | |                         |     |
     * yl2 +---+                    +--+-----+	yr2
     *     | 5 |                    |        |
     *     |   +-------- 4 ---------+yb   3  |
     *     +---+--------------------+--------+
     *         xb1                  xb2     x+w,y+h
     * </pre>
     */
    public void paintFill(Component c, Graphics g, Rectangle r)
    {
	int x = r.x, y = r.y, w = r.width, h = r.height;
	int xt1 = x + sizes[7].width;
	int xt2 = (x + w) - sizes[1].width;
	int xb1 = x + sizes[5].width;
	int xb2 = (x + w) - sizes[3].width;
	int xl = x + sizes[6].width;
	int xr = (x + w) - sizes[2].width;
	int yl1 = y + sizes[7].height;
	int yl2 = (y + h) - sizes[5].height;
	int yr1 = y + sizes[1].height;
	int yr2 = (y + h) - sizes[3].height;
	int yb = (y + h) - sizes[4].height;

	fills[0].paintFill(c, g, xt1, y, xt2 - xt1, sizes[0].height);
	fills[1].paintFill(c, g, xt2, y, sizes[1].width, sizes[1].height);
	fills[2].paintFill(c, g, xr, yr1, sizes[2].width, yr2 - yr1);
	fills[3].paintFill(c, g, xb2, yr2, (x + w) - xb2, sizes[3].height);
	fills[4].paintFill(c, g, xb1, yb, xb2 - xb1, sizes[4].height);
	fills[5].paintFill(c, g, x, yl2, sizes[5].width, (y + h) - yl2);
	fills[6].paintFill(c, g, x, yl1, sizes[6].width, yl2 - yl1);
	fills[7].paintFill(c, g, x, y, sizes[7].width, yl1 - y);
    }


    public Insets getBorderInsets(Component c) {
	return new Insets(
  	    sizes[0].height,  /* top */
            sizes[6].width,   /* left */
            sizes[4].height,  /* bottom */
	    sizes[2].width);  /* right */
    }

    public boolean isBorderOpaque() {
	return true;
    }
    
    private int oldx = -1, oldy = -1, oldw = -1, oldh = -1;
    
    public void redoImage(Component c, int x, int y, int w, int h) {
    	
    	buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    	paintFill(c, buffer.getGraphics(), new Rectangle(x, y, w, h));
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
	Graphics2D g2d = (Graphics2D)g;
	Composite oldComposite = g2d.getComposite();
	g2d.setComposite(AlphaComposite.SrcOver);
	if (oldx != x || oldy != y || oldw != w || oldh != h) {
		redoImage(c, x, y, w, h);
		oldx = x;
		oldy = y;
		oldw = w;
		oldh = h;
	}
	g2d.drawImage(buffer, x, y, null);
//	paintFill(c, g, new Rectangle(x, y, w, h));
	g2d.setComposite(oldComposite);
    }
}
