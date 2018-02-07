package lia.web.servlets.map2d;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;

import lia.web.servlets.web.Utils;
import lia.web.utils.ThreadedPage;

/*
 * Created on Mar 14, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

/**
 * @author alexc && mluc
 *
 *         To change the template for this generated type comment go to
 *         Window - Preferences - Java - Code Generation - Code and Comments
 */
public class Map2D extends ThreadedPage {
	private static final long serialVersionUID = 1L;

	Globals globals;

	public Map2D(Globals _globals) {
		globals = _globals;
		globals.map2d = this;
	}

	/*
	 * Create a new Globals on each request instead of one per servlet instance
	 */
	public void doInit() {
		globals = new Globals();
		globals.map2d = this;
	}

	public void execGet() {
		final long lStart = System.currentTimeMillis();

		globals.map2d = this;
		try {
			// get servlet get parameters
			float w = getf("w", 32);
			float h = w / 2;
			float x = getf("x", -16);
			float y = getf("y", 8);
			float rot_angle = 0f;
			int W = geti("_W", 800);
			int d3d = geti("d3d", 0);
			float a3d = getf("a3d", 75f);// rotation angle

			globals.DISPLAY_W = W;
			globals.DISPLAY_H = W / 2;
			if (d3d > 0) {
				// if the image is 3D, then compute some extra lateral images
				float extra;
				// int dx2=(int)(2*Globals.DISPLAY_H*ctg/3);//(Globals.DISPLAY_W*dx3d);
				float ctg = 1f / (float) Math.tan(a3d * Math.PI / 180);
				float fdx2 = 2f * globals.DISPLAY_H * ctg / 3f;// (Globals.DISPLAY_W*dx3d);
				extra = fdx2 / globals.DISPLAY_W * w;
				x -= extra;
				w += 2 * extra;
			}
			// limit y
			float y_max = Globals.MAP_HEIGHT * .5f;
			float y_min = -Globals.MAP_HEIGHT * .5f + h;
			if (y > y_max)
				y = y_max;
			if (y < y_min)
				y = y_min;
			// set x inside [-MAP_WIDTH;MAP_WIDTH] limits
			while (x >= Globals.MAP_WIDTH * .5f)
				x -= Globals.MAP_WIDTH;
			while (x < -Globals.MAP_WIDTH * .5f)
				x += Globals.MAP_WIDTH;
			globals.width2D = w;
			globals.height2D = h;
			globals.x2D = x;
			globals.y2D = y;

			BufferedImage img = new BufferedImage(globals.DISPLAY_W, globals.DISPLAY_H, BufferedImage.TYPE_INT_RGB);
			drawGeoMap(img, w, h, x, y, rot_angle, W, d3d, a3d);
			ServletOutputStream out = response.getOutputStream();
			ImageIO.write(img, "jpg", out);
			// ChartUtilities.writeBufferedImageAsPNG(out, img);
			response.setContentType("image/jpeg");

		} catch (Throwable ex) {
			try {
				PrintWriter pw = response.getWriter();
				ex.printStackTrace(pw);
			} catch (Exception ex2) {
				ex2.printStackTrace();
			}
			;
		}

		globals = null;

		System.err.println("map2d, ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

		Utils.logRequest("map2d", (int) (System.currentTimeMillis() - lStart), request, false, System.currentTimeMillis() - lStart);
	}

	public void drawGeoMap(BufferedImage img, float w, float h, float x, float y, float rot_angle, int W, int d3d, float a3d) {
		Graphics2D g = (Graphics2D) img.getGraphics();
		// byte []data =null;

		// g.drawOval(10,10,6,9);

		Texture.constructInitialTree(this);
		globals.computeGrid();

		// detect correct LOD
		globals.root.zoomChanged();
		// load textures from files
		Texture.loadNewTextures(this);
		// set textures for this LOD
		Texture.setTreeTextures();
		Texture.unsetTreeTextures();
		// draw visible textures
		globals.root.drawTree(g);

		int step = 1;
		while (globals.x2D + globals.width2D > Globals.MAP_WIDTH * .5f * step) {
			// image is split in 2, so draw second part
			// but first reposition x, and correct width
			// x becomes -MAP_WIDTH
			// and w becomes old_w - (MAP_WIDTH-old_x)
			// Globals.width2D += Globals.x2D-Globals.MAP_WIDTH;
			globals.x2D -= Globals.MAP_WIDTH * step;

			// Globals.globals.computeGrid();
			// detect correct LOD
			globals.root.zoomChanged();
			// load textures from files
			Texture.loadNewTextures(this);
			// set textures for this LOD
			Texture.setTreeTextures();
			Texture.unsetTreeTextures();
			// draw visible textures
			globals.root.drawTree(g);
			// revert to initial x
			globals.x2D += Globals.MAP_WIDTH * step;
			step++;
		}

		if (d3d > 0) {
			BufferedImage img2;
			img2 = new BufferedImage(globals.DISPLAY_W, globals.DISPLAY_H, BufferedImage.TYPE_INT_RGB);

			double ctg = 1 / Math.tan(a3d * Math.PI / 180);
			int dx2 = 0;// (int)(2*Globals.DISPLAY_H*ctg/3);//(Globals.DISPLAY_W*dx3d);
			int x1, x2;
			int xo;
			int aux;
			// double ctg=Math.tan(a3d*Math.PI/180);
			for (int yo = 0; yo < globals.DISPLAY_H; yo++) {
				aux = (int) (yo * ctg);
				x1 = dx2 - aux;
				x2 = aux + globals.DISPLAY_W - dx2;
				for (int xp = (x1 >= 0 ? x1 : 0); xp < (x2 < globals.DISPLAY_W ? x2 : globals.DISPLAY_W); xp++) {
					xo = (xp + aux - dx2) * globals.DISPLAY_W / (globals.DISPLAY_W + 2 * aux - 2 * dx2);
					if (x2 < globals.DISPLAY_W || x1 > 0)
						img2.setRGB(xp, yo, 45 + (45 << 8) + (145 << 16));
					else
						img2.setRGB(xp, yo, img.getRGB(xo, yo));
				}
			}
			g.drawImage(img2, 0, 0, globals.DISPLAY_W, globals.DISPLAY_H, null);
		}
	}

}
