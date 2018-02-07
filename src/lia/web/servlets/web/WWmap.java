package lia.web.servlets.web;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.ServletExtension;

/**
 *
 */
public class WWmap {
	
	/**
	 * 
	 */
	static final class SomeComponent extends Component {
		private static final long	serialVersionUID	= 1L;
	}

	/** size of the final image */
	public int				width; 

	/** size of the final image */
	public int				height; 

	/** the nodes */
	public Vector<WNode>			nodes;										// contains WNodes

	/** the links */
	public Vector<WLink>			links;										// contains WLinks

	/** other things */
	public Vector<Object>			special;

	/** background color */ 
	public Color			bgColor			= Color.WHITE; 

	/** default color 4 routers */
	public Color			routerColor		= ColorFactory.getColor(0, 170, 249); 

	/** default color 4 nodes */
	public Color			defNodeColor	= Color.red; 

	/**
	 * color scales
	 */
	public Hashtable<String, Color>		colorScales;

	/**
	 * Value scales
	 */
	public Hashtable<String, Double>		valScales;

	private String			textureFilename;							// full name of the texture

	private BufferedImage	texture;									// the texture

	private BufferedImage	image;										// the final image

	private Graphics		graphics;									// the graphics context for
																		// image

	private Rectangle		tRect;										// visual rectangle - coords
																		// from texture

	private Rectangle		iRect;										// destination rectangle -
																		// coords from image

	private Properties		prop;

	/**
	 * Build the Web World Map
	 * 
	 * @param iWidth -
	 *            width of the image
	 * @param iHeight -
	 *            height of the image
	 * @param vNodes -
	 *            vector of WNode s
	 * @param vLinks -
	 *            vector of WLink s
	 * @param vSpecial 
	 * @param resolution -
	 *            one of "1024x512", "2048x1024", "4096x2048", "8192x4096"
	 * @param properties 
	 */
	public WWmap(int iWidth, int iHeight, Vector<WNode> vNodes, Vector<WLink> vLinks, Vector<Object> vSpecial, String resolution, Properties properties) {
		width = iWidth;
		height = iHeight;
		nodes = vNodes;
		links = vLinks;
		special = vSpecial;
		textureFilename = "lia/images/earth_texture" + resolution + ".jpg";
		prop = properties;

		textureFilename = properties.getProperty("texture", textureFilename);
		
		texture = WWTextureLoader.getTexture(textureFilename);
		image = new BufferedImage(iWidth, iHeight, BufferedImage.TYPE_INT_RGB);
		graphics = image.getGraphics();
		Font font = new Font("Arial", Font.BOLD, 11); // Font.PLAIN
		graphics.setFont(font);
		tRect = new Rectangle();
		iRect = new Rectangle();
		colorScales = new Hashtable<String, Color>();

		colorScales.put("Node_min", getColor(properties, "default.color.min", Color.CYAN));
		colorScales.put("Node_max", getColor(properties, "default.color.max", Color.BLUE));

		colorScales.put("Delay_min", getColor(properties, "Delay.color.min", ColorFactory.getColor(0, 255, 100)));
		colorScales.put("Delay_max", getColor(properties, "Delay.color.max", ColorFactory.getColor(255, 255, 0)));

		colorScales.put("Bandwidth_min", getColor(properties, "Bandwidth.color.min", ColorFactory.getColor(255, 255, 0)));
		colorScales.put("Bandwidth_max", getColor(properties, "Bandwidth.color.max", ColorFactory.getColor(0, 255, 100)));

		valScales = new Hashtable<String, Double>();
	}

	private static final Color getColor(Properties prop, String sParam, Color cDefault) {
		try {
			StringTokenizer st = new StringTokenizer(ServletExtension.pgets(prop, sParam));

			return ColorFactory.getColor(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
		} catch (Exception e) {
			return cDefault;
		}
	}

	/**
	 * Render the whole scene
	 * 
	 * @param longCenter -
	 *            center of the image
	 * @param latCenter -
	 *            in longitude and latitude coords
	 * @param zoom -
	 *            for zoom==1 draw the original picture.
	 * @return - the final image, with texture, nodes and links
	 */
	public BufferedImage getImage(double longCenter, double latCenter, double zoom) {
		graphics.setColor(bgColor);
		graphics.fillRect(0, 0, width, height);

		drawTexture(longCenter, latCenter, zoom);
		drawSLinks();
		drawLinks();
		drawNodes();
		try {
			drawLegend();
		} catch (Throwable t) {
			t.printStackTrace();
		}

		return image;
	}

	private void drawLegend() {
		Graphics2D g = (Graphics2D) graphics;

		if (ServletExtension.pgetb(prop, "Legend.display", false)) {
			int x = ServletExtension.pgeti(prop, "Legend.position.x", 500);
			int y = ServletExtension.pgeti(prop, "Legend.position.y", 370);
			int w = ServletExtension.pgeti(prop, "Legend.position.width", 300);
			int h = ServletExtension.pgeti(prop, "Legend.position.height", 30);

			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g.setPaint(ColorFactory.getColor(200, 200, 200));
			g.fill(new RoundRectangle2D.Double(x, y, w, h, 5, 5));

			int x2 = ServletExtension.pgeti(prop, "Legend.gradient.x", 150);
			int y2 = ServletExtension.pgeti(prop, "Legend.gradient.y", 8);
			int w2 = ServletExtension.pgeti(prop, "Legend.gradient.width", 80);
			int h2 = ServletExtension.pgeti(prop, "Legend.gradient.height", 14);

			String sParameter = ServletExtension.pgets(prop, "Legend.parameter.name", "Delay");
			String sAlias = ServletExtension.pgets(prop, "Legend.parameter.alias", sParameter);

			String sSuffix = Formatare.replace(ServletExtension.pgets(prop, "Legend.suffix", ""), "_", " ");

			Color cMin = colorScales.get(sParameter + "_min");
			Color cMax = colorScales.get(sParameter + "_max");

			GradientPaint gpaint = new GradientPaint(x + x2, y + y2, cMin, x + x2 + w2, y + y2, cMax);

			g.setPaint(gpaint);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.fill(new Rectangle2D.Double(x + x2, y + y2, w2, h2));
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			double dMin = valScales.get(sParameter + "_min").doubleValue();
			double dMax = valScales.get(sParameter + "_max").doubleValue();

			if (dMin > dMax) {
				dMin = 0;
				dMax = 0;
			}

			g.setPaint(Color.BLACK);

			int xl = ServletExtension.pgeti(prop, "Legend.label.x", 7);
			int yl = ServletExtension.pgeti(prop, "Legend.label.y", 19);

			String sSeparator = Formatare.replace(ServletExtension.pgets(prop, "Legend.separator", "_"), "_", " ");

			g.drawString(sAlias + sSeparator + DoubleFormat.point(dMin) + sSuffix, x + xl, y + yl);
			g.drawString(DoubleFormat.point(dMax) + sSuffix, x + x2 + w2 + 5, y + yl);

			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		}
	}

	/**
	 * convert a longitude to a X coordinate from source texture
	 * 
	 * @param l -
	 *            the longitude
	 * @return - the x coord on unresized texture
	 */
	private double long2texX(final double lLong) {
		final double l = lLong * Math.PI / 180.0;
		return (l + Math.PI) / Math.PI / 2 * texture.getWidth();
	}

	/**
	 * convert a latitude to a Y coordinate from source texture
	 * 
	 * @param l -
	 *            the latitude
	 * @return - the y coord on unresized texture
	 */
	private double lat2texY(final double lLat) {
		final double l = lLat * Math.PI / 180.0;
		return (Math.PI / 2 - l) / Math.PI * texture.getHeight();
	}

	/**
	 * convert a X coordinate from source texture to dest image using tRect and iRect
	 * 
	 * @param x -
	 *            the cord on tex
	 * @return - the coord on image
	 */
	private double tex2imgX(double x) {
		double xt = x - tRect.x;
		double ratio = (double) iRect.width / (double) tRect.width;
		double xi = iRect.x + ratio * xt;
		return xi;
	}

	/**
	 * convert a Y coordinate from source texture to dest image using tRect and iRect
	 * 
	 * @param y -
	 *            the cord on tex
	 * @return - the coord on image
	 */
	private double tex2imgY(double y) {
		double yt = y - tRect.y;
		double ratio = (double) iRect.height / (double) tRect.height;
		double yi = iRect.y + ratio * yt;
		return yi;
	}

	private void drawTexture(double longC, double latC, double zoom) {
		double xC = long2texX(longC);
		double yC = lat2texY(latC);
		double w = width / zoom;
		double h = height / zoom;
		// this is the desired size
		tRect.setBounds((int) (xC - w / 2), (int) (yC - h / 2), (int) w, (int) h);
		iRect.setBounds(0, 0, width, height);
		// if needed, adjust this to fit in texture

		if (tRect.x < 0)
			tRect.x = 0;
		if (tRect.y < 0)
			tRect.y = 0;
		int dx = tRect.x + tRect.width - texture.getWidth();
		if (dx > 0)
			tRect.x -= dx;
		int dy = tRect.y + tRect.height - texture.getHeight();
		if (dy > 0)
			tRect.y -= dy;

		// bogus object needed for drawImage
		Component obs = new SomeComponent();
		graphics.drawImage(texture, iRect.x, iRect.y, iRect.x + iRect.width, iRect.y + iRect.height, tRect.x, tRect.y, tRect.x + tRect.width, tRect.y + tRect.height, obs);
	}

	private void adjustLinkMinMax(String what) {
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for (int i = 0; i < links.size(); i++) {
			Hashtable<String, Double> data = links.get(i).data;
			Double val = data.get(what);
			if (val != null) {
				double v = val.doubleValue();
				min = (v < min ? v : min);
				max = (v > max ? v : max);
			}
		}
		valScales.put(what + "_min", Double.valueOf(min));
		valScales.put(what + "_max", Double.valueOf(max));
	}

	private void drawLinks() {
		adjustLinkMinMax("Bandwidth");
		adjustLinkMinMax("Delay");
		for (int i = 0; i < links.size(); i++) {
			WLink link = links.get(i);
			paintLink(link);
		}
	}

	private void drawSLinks() {
		try {
			for (int i = 0; i < special.size() - 2; i += 3) {
				paintSLink((WNode) special.get(i), (WNode) special.get(i + 1), (String) special.get(i + 2));
			}
		} catch (Exception e) {
			System.err.println("Exception : " + e + "(" + e.getMessage() + ")");
			e.printStackTrace();
		}
	}

	/**
	 * @param sFormat
	 * @param link
	 * @param b
	 * @param d
	 * @return formatted label
	 */
	public static final String formatLabel(String sFormat, WLink link, Double b, Double d) {
		String sLabel = Formatare.replace(sFormat, "$N1", link.src.realname);
		sLabel = Formatare.replace(sLabel, "$A1", link.src.name);
		sLabel = Formatare.replace(sLabel, "$N2", link.dest.realname);
		sLabel = Formatare.replace(sLabel, "$A2", link.dest.name);
		sLabel = Formatare.replace(sLabel, "$B", b != null ? DoubleFormat.point(b.doubleValue()) : "");
		sLabel = Formatare.replace(sLabel, "$D", d != null ? DoubleFormat.point(d.doubleValue()) : "");

		sLabel = Formatare.replace(sLabel, "()", "");
		sLabel = Formatare.replace(sLabel, "[]", "");
		sLabel = Formatare.replace(sLabel, "{}", "");

		sLabel = sLabel.trim();

		return sLabel;
	}

	private void paintLink(WLink link) {
		String sBW = ServletExtension.pgets(prop, "Bandwidth.parameter", "Bandwidth");
		String sDel = ServletExtension.pgets(prop, "Delay.parameter", "Delay");

		Double bw = link.data.get(sBW);
		Double del = link.data.get(sDel);

		String sLabelFormat = ServletExtension.pgets(prop, "Label.format", "$B ($D)");

		sLabelFormat = ServletExtension.pgets(prop, link.src.realname + "_" + link.dest.realname + ".label.format", sLabelFormat);

		Color color = Color.YELLOW;
		float stroke = 1;
		String label = "";
		int dd = 6; // guess what is this! :-)
		if (bw != null && del != null) {
			color = getScaledColor(del.doubleValue(), sDel);
			stroke = getScaledStroke(bw.doubleValue(), sBW);
		} else if (bw != null) {
			// delay is null => paint in yellow
			color = getColor(prop, "Delay.color.unknown", Color.ORANGE);
			stroke = getScaledStroke(bw.doubleValue(), sBW);
		} else if (del != null) {
			// no bandwidth available
			color = getScaledColor(del.doubleValue(), sDel);
			stroke = getScaledStroke(0, sBW); // minimum bandwidth stroke
		}
		label = formatLabel(sLabelFormat, link, bw, del);

		plotWLink(link, color, stroke, label, dd);
	}

	private void paintSLink(WNode src, WNode dest, String label) {
		Color color = ColorFactory.getColor(255, 46, 189);
		float stroke = 2;

		int klong1 = (int) tex2imgX(long2texX(src.LONG));
		int klong2 = (int) tex2imgX(long2texX(dest.LONG));

		int klat1 = (int) tex2imgY(lat2texY(src.LAT));
		int klat2 = (int) tex2imgY(lat2texY(dest.LAT));

		Graphics2D g = (Graphics2D) graphics;

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(color);

		Stroke oldStroke = g.getStroke();
		g.setStroke(new BasicStroke(stroke));
		g.drawLine(klong1, klat1, klong2, klat2);
		g.setStroke(oldStroke);

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// let's put some label on this one

		int dx = (klong1 - klong2);
		int dy = klat1 - klat2;
		float l = (float) Math.sqrt(dx * dx + dy * dy);
		float dir_x = dx / l;
		float dir_y = dy / l;

		int dd = 6;

		int x1p = klong1 - (int) (dd * dir_y);
		int x2p = klong2 - (int) (dd * dir_y);
		int y1p = klat1 + (int) (dd * dir_x);
		int y2p = klat2 + (int) (dd * dir_x);

		int xv1 = (x1p + x2p) / 2;
		int yv1 = (y1p + y2p) / 2;

		int xl = xv1;
		int yl = yv1;

		FontMetrics fm = g.getFontMetrics();
		int wl = fm.stringWidth(label) + 1;
		int hl = fm.getHeight() + 1;

		int off = 2;

		if (dir_x >= 0 && dir_y < 0) {
			xl = xl + off;
			yl = yl + hl;
		}
		if (dir_x <= 0 && dir_y > 0) {
			xl = xl - wl - off;
			yl = yl - off;
		}
		if (dir_x > 0 && dir_y >= 0) {
			xl = xl - wl - off;
			yl = yl + hl;
		}
		if (dir_x < 0 && dir_y < 0) {
			xl = xl + off;
			yl = yl - off;
		}

		g.setColor(color.darker().darker().darker());
		for (int i = -1; i <= 1; i++)
			for (int j = -1; j <= 1; j++)
				g.drawString(label, xl + i, yl + j);

		g.setColor(color);
		g.drawString(label, xl, yl);

	}

	private void plotWLink(WLink link, Color color, float stroke, String label, int dd) {
		int klong1 = (int) tex2imgX(long2texX(link.src.LONG));
		int klong2 = (int) tex2imgX(long2texX(link.dest.LONG));

		int klat1 = (int) tex2imgY(lat2texY(link.src.LAT));
		int klat2 = (int) tex2imgY(lat2texY(link.dest.LAT));

		// if it's shorter to get from one point to another going on the other
		// side of the globe...
		/*
		 * if (Math.abs(link.src.LONG - link.dest.LONG) > 180) { // we have to go round the world
		 * int medLat = (klat1 + klat2) / 2; int edge1 = (int) tex2imgX(long2texX(-180)); int edge2 =
		 * (int) tex2imgX(long2texX(180)); if (link.src.LONG > 0) { int tmp = edge1; edge1 = edge2;
		 * edge2 = tmp; } plotWLinkWorker(klat1, klong1, medLat, edge1, color, stroke, label, dd);
		 * plotWLinkWorker(medLat, edge2, klat2, klong2, color, stroke, label, dd); }else{
		 */
		Color darker = color.darker().darker();
		plotWLinkWorker(klat1 + 1, klong1 + 1, klat2 + 1, klong2 + 1, darker, stroke, label, dd, false, null);
		plotWLinkWorker(klat1 + 1, klong1 - 1, klat2 + 1, klong2 - 1, darker, stroke, label, dd, false, null);

		plotWLinkWorker(klat1, klong1, klat2, klong2, color, stroke, label, dd, true, link);

		/*
		 * }
		 */
	}

	private void plotWLinkWorker(int klat2, int klong2, int klat1, int klong1, Color color, float stroke, String label, int dd, boolean bPlotText, WLink link) {
		/*
		 * dd : - variable used to compute the displacement for directional link from the undirected
		 * direction that is an central line, - makes the line to be drawn above the undirected line -
		 * generates some variables to retain the new coordonates
		 */
		int dx = (klong1 - klong2);
		int dy = klat1 - klat2;
		float l = (float) Math.sqrt(dx * dx + dy * dy);
		float dir_x = dx / l;
		float dir_y = dy / l;

		int x1p = klong1 - (int) (dd * dir_y);
		int x2p = klong2 - (int) (dd * dir_y);
		int y1p = klat1 + (int) (dd * dir_x);
		int y2p = klat2 + (int) (dd * dir_x);

		if (link != null) {
			float w = 1 + stroke / 2;

			int x11p = x1p - (int) (w * dir_y);
			int x12p = x1p + (int) (w * dir_y);

			int x21p = x2p - (int) (w * dir_y);
			int x22p = x2p + (int) (w * dir_y);

			int y11p = y1p + (int) (w * dir_x);
			int y12p = y1p - (int) (w * dir_x);

			int y21p = y2p + (int) (w * dir_x);
			int y22p = y2p - (int) (w * dir_x);

			link.vMap = new Vector<Integer>();
			link.vMap.add(Integer.valueOf(x11p));
			link.vMap.add(Integer.valueOf(y11p));
			link.vMap.add(Integer.valueOf(x21p));
			link.vMap.add(Integer.valueOf(y21p));
			link.vMap.add(Integer.valueOf(x22p));
			link.vMap.add(Integer.valueOf(y22p));
			link.vMap.add(Integer.valueOf(x12p));
			link.vMap.add(Integer.valueOf(y12p));
		}

		// compute the coordonates for arrow that indicates the direction of the link
		int xv1 = (x1p + x2p) / 2;
		int yv1 = (y1p + y2p) / 2;
		int xv = (xv1 + x2p) / 2;
		int yv = (yv1 + y2p) / 2;

		Graphics g = graphics;

		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(color);
		g.fillArc(klong1 - dd / 2, klat1 - dd / 2, dd, dd, 0, 360);
		g.fillArc(klong2 - dd / 2, klat2 - dd / 2, dd, dd, 0, 360);

		Stroke oldStroke = ((Graphics2D) g).getStroke();
		((Graphics2D) g).setStroke(new BasicStroke(stroke));
		g.drawLine(x1p, y1p, x2p, y2p);
		((Graphics2D) g).setStroke(oldStroke);

		float arrow = 2;
		float aa = (dd + stroke / 2) / 2.0f;

		int[] axv = { (int) (xv - aa * dir_x + arrow * aa * dir_y), (int) (xv - aa * dir_x - arrow * aa * dir_y), (int) (xv + arrow * aa * dir_x), (int) (xv - aa * dir_x + arrow * aa * dir_y) };
		int[] ayv = { (int) (yv - aa * dir_y - arrow * aa * dir_x), (int) (yv - aa * dir_y + arrow * aa * dir_x), (int) (yv + arrow * aa * dir_y), (int) (yv - aa * dir_y - arrow * aa * dir_x) };
		Polygon p = new Polygon(axv, ayv, 4);
		g.fillPolygon(p);

		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		FontMetrics fm = g.getFontMetrics();
		int wl = fm.stringWidth(label) + 1;
		int hl = fm.getHeight() + 1;

		int off = 2;
		int xl = xv;
		int yl = yv;

		if (dir_x >= 0 && dir_y < 0) {
			xl = xl + off;
			yl = yl + hl;
		}
		if (dir_x <= 0 && dir_y > 0) {
			xl = xl - wl - off;
			yl = yl - off;
		}
		if (dir_x > 0 && dir_y >= 0) {
			xl = xl - wl - off;
			yl = yl + hl;
		}
		if (dir_x < 0 && dir_y < 0) {
			xl = xl + off;
			yl = yl - off;
		}

		if (bPlotText) {
			g.setColor(color.darker().darker().darker());
			for (int i = -1; i <= 1; i++)
				for (int j = -1; j <= 1; j++)
					g.drawString(label, xl + i, yl + j);

			g.setColor(color);
			g.drawString(label, xl, yl);
		}
	}

	private void drawNodes() {
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for (int i = 0; i < nodes.size(); i++) {
			List<Double> data = nodes.get(i).data;
			if (data != null && data.size() == 1) {
				double val = data.get(0).doubleValue();
				min = (val < min ? val : min);
				max = (val > max ? val : max);
			}
		}
		valScales.put("Node_min", Double.valueOf(min));
		valScales.put("Node_max", Double.valueOf(max));
		for (int i = 0; i < nodes.size(); i++)
			paintNode(nodes.get(i));
	}

	private float getScaledStroke(double val, String what) {
		double vmin = valScales.get(what + "_min").doubleValue();
		double vmax = valScales.get(what + "_max").doubleValue();
		float minS = (float) ServletExtension.pgetd(prop, "Stroke.min", 2D);
		float maxS = (float) ServletExtension.pgetd(prop, "Stroke.max", 4D);

		double delta = Math.abs(vmax - vmin);
		if (Math.abs(delta) < 1E-5 || val < vmin)
			return minS;

		if (val > vmax)
			return maxS;

		return (float) ((val - vmin) * (maxS - minS) / delta) + minS;
	}

	private Color getScaledColor(double val, String what) {
		double vmin = valScales.get(what + "_min").doubleValue();
		double vmax = valScales.get(what + "_max").doubleValue();
		Color cmin = colorScales.get(what + "_min");
		Color cmax = colorScales.get(what + "_max");
		// System.out.println("vmax="+vmax+" vmin="+vmin);
		// System.out.println("cmin="+cmin+" cmax="+cmax);

		double delta = Math.abs(vmax - vmin);
		if (Math.abs(delta) < 1E-5)
			return cmin;
		int R, G, B;
		R = (int) ((val - vmin) * (cmax.getRed() - cmin.getRed()) / delta) + cmin.getRed();
		G = (int) ((val - vmin) * (cmax.getGreen() - cmin.getGreen()) / delta) + cmin.getGreen();
		B = (int) ((val - vmin) * (cmax.getBlue() - cmin.getBlue()) / delta) + cmin.getBlue();

		if (R < 0)
			R = 0;
		if (R > 255)
			R = 255;
		if (G < 0)
			G = 0;
		if (G > 255)
			G = 255;
		if (B < 0)
			B = 0;
		if (B > 255)
			B = 255;

		return ColorFactory.getColor(R, G, B);
	}

	private void paintNode(WNode n) {
		n.x = (int) tex2imgX(long2texX(n.LONG));
		n.y = (int) tex2imgY(lat2texY(n.LAT));
		int R = n.r;
		int x = n.x;
		int y = n.y;
		Graphics2D g = (Graphics2D) graphics;
		g.setFont(new Font("Arial", Font.BOLD, n.fontsize));

		int labelx = x + n.xLabelOffset;
		int labely = y + n.yLabelOffset;

		g.setColor(ColorFactory.getColor(100, 100, 100));
		for (int i = -1; i <= 1; i++)
			for (int j = -1; j <= 1; j++)
				g.drawString(n.name, labelx + i, labely + j);

		g.setColor(Color.WHITE);
		g.drawString(n.name, labelx, labely);

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (n.data == null) {
			// paint as router
			Stroke oldStroke = g.getStroke();
			g.setStroke(new BasicStroke(2f));

			g.setColor(ColorFactory.getColor(100, 100, 100));
			g.drawOval(x - R - 1, y - R - 1, 2 * R + 2, 2 * R + 2);
			g.setColor(routerColor);
			g.fillOval(x - R, y - R, 2 * R, 2 * R);
			g.setColor(Color.WHITE);
			g.drawOval(x - R, y - R, 2 * R, 2 * R);

			g.setStroke(oldStroke);
			g.drawLine(x - 1, y - 1, x - R + 3, y - R + 3);
			g.drawLine(x - 1, y + 1, x - R + 3, y + R - 3);
			g.drawLine(x + 1, y - 1, x + R - 3, y - R + 3);
			g.drawLine(x + 1, y + 1, x + R - 3, y + R - 3);
		} else if (n.data.size() == 0) {
			// paint in default color
			g.setColor(n.alternate == null ? defNodeColor : Color.ORANGE);
			g.fillOval(x - R, y - R, 2 * R, 2 * R);
			g.setColor(n.alternate == null ? Color.RED : Color.ORANGE);
			g.drawOval(x - R, y - R, 2 * R, 2 * R);
		} else if (n.data.size() == 1) {
			// paint as color scaled value
			double val = n.data.get(0).doubleValue();
			g.setColor(getScaledColor(val, "Node"));
			g.fillOval(x - R, y - R, 2 * R, 2 * R);
			g.setColor(Color.RED);
			g.drawOval(x - R, y - R, 2 * R, 2 * R);
		} else {
			try {
				// TODO: paint as pie
				double dsum = 0;

				for (int i = 0; i < n.data.size(); i++) {
					dsum += n.data.get(i).doubleValue();
				}

				if (dsum <= 1E-10) {
					g.setColor(n.colors[0]);
					g.fillOval(x - R, y - R, 2 * R, 2 * R);
				} else {
					int uo = 0;

					for (int i = 0; i < n.data.size(); i++) {
						double val = n.data.get(i).doubleValue();

						int u = (int) ((val / dsum) * 360D);

						u = uo + u > 360 ? 360 - uo : u;

						g.setColor(n.colors[i]);
						g.fillArc(x - R, y - R, 2 * R, 2 * R, uo, u);

						uo += u;
					}
				}

				g.setColor(Color.BLACK);
				g.drawOval(x - R, y - R, 2 * R, 2 * R);
			} catch (Exception e) {
				System.err.println("Caught : " + e + " : " + e.getMessage());
				e.printStackTrace();
			}
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	}

}
