package lia.web.utils.gmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.Mmap.JColorScale;
import lia.Monitor.JiniClient.CommonGUI.Sphere.SphereTexture;
import lia.Monitor.monitor.Gresult;
import lia.web.utils.ColorFactory;

/**
 * A class that does in servelts what GraphPanel does for ML Client.
 */
public class GMap {
	
	static Font nodesFont = new Font("Arial", Font.PLAIN, 10);
	private static DecimalFormat lblFormat = new DecimalFormat("###,###.#");
	static Color clHiPing = ColorFactory.getColor(255, 100, 100);
	final static Color shadowColor = ColorFactory.getColor(0, 0, 0, 76);
	
	/** possible layout values */
	public static final int RANDOM_LAYOUT = 0;
	/**
	 * grid layout
	 */
	public static final int GRID_LAYOUT = 1;
	/**
	 * Geo layout
	 */
	public static final int GEOGRAPHICAL_LAYOUT = 2;
	/**
	 * Radial layout
	 */
	public static final int RADIAL_LAYOUT = 3;

	/**
	 * Returns an image identical to the one painted in the GrapPan.paintComponent
	 * @param w The width of the resulted image
	 * @param h The height of the resulted image
	 * @param makeNice If true the image will have rendering hits set on
	 * @param bgColor The background color (if null set default to white)
	 * @param nodeFont 
	 * @param links Hash with pairs (farmName -> Hash (toFarm -> Double[] {quality, lostPackages }))
	 * @param showOnlyConnectedNodes 
	 * @param withShadow 
	 * @param withBump 
	 * @param layoutType The type of layout to draw.... see the constants defined above
	 * @param cpuUSR Hash with pairs (node -> GResult)
	 * @param cpuSYS Hash with pairs (node -> GResult)
	 * @param cpuNICE Hash with pairs (node -> GResult)
	 * @param geoPos If layoutType is geographical then using this hash you have to supply for each node LAT and LONG, as in
	 * 				(name -> String[] { LAT, LONG } )... otherwise ignore
	 * @param rootNode If layoutType is radial you must specify the root node's name... otherwise ignore
	 * @return A new image [image0], color scale [image1]
	 */
	public static BufferedImage[] drawGMapForCPU(int w, int h, boolean makeNice, Color bgColor, Font nodeFont, Hashtable<String, Hashtable<String, Object[]>> links, 
			boolean showOnlyConnectedNodes, boolean withShadow, boolean withBump, int layoutType, 
			Hashtable<String, Gresult> cpuUSR, Hashtable<String, Gresult> cpuSYS, Hashtable<String, Gresult> cpuNICE, Hashtable<String, Object[]> geoPos, String rootNode) {
		
		BufferedImage image[] = new BufferedImage[2];
		image[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image[1] = new BufferedImage(120, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image[0].createGraphics(); 
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, makeNice?RenderingHints.VALUE_ANTIALIAS_ON :RenderingHints.VALUE_ANTIALIAS_OFF);
		
		if (bgColor != null)g2.setColor(bgColor); 
		else g2.setColor(Color.WHITE);
		if (nodeFont != null) g2.setFont(nodeFont);
		else g2.setFont(nodesFont);
		g2.fillRect(0, 0, w, h);
		
		GraphTopology topology = constructTopology(w, h, links);
		
		// apply the layout
		GraphLayoutAlgorithm layout = null;
		switch (layoutType) {
		case RANDOM_LAYOUT: {
			break;
		}
		case GRID_LAYOUT: {
			layout = new GridLayout();
			break;
		}
		case GEOGRAPHICAL_LAYOUT: {
			layout = new GeographicLayout();
			break;
		}
		case RADIAL_LAYOUT: {
			Node root = null;
			if (rootNode != null) {
				for (Iterator<Node> it1 = topology.gnodes.iterator(); it1.hasNext(); ) {
					Node n = it1.next();
					if (n.name.equals(rootNode)) { root = n; break; }
				}
			}  
			if (root == null) root = topology.gnodes.getFirst();
			layout = new RadialLayout(root);
			break;
		}
		default:
			break;
		}
		
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			if (geoPos != null && geoPos.containsKey(n.name)) {
				String[] pos = (String[])geoPos.get(n.name);
				n.LAT = pos[0];
				n.LONG = pos[1];
			} else {
				n.LAT = "0.0";
				n.LONG = "0.0";
			}
		}
		
		if (layout != null) {
			layout.layout(topology);
		}
		
		// rescale if needed the points
		Rectangle range = new Rectangle();
		Rectangle actualRange = new Rectangle();
		int wunit = 0;
		int hunit = 0;
		
		FontMetrics fm = g2.getFontMetrics();
		boolean update = false;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    Node n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
	        if ( w2 > wunit ) {
	        	wunit = w2;
				update = true;
	        }
	        if ( h2 > hunit ) {
				update = true;
	        	hunit = h2;
	        }
		}
		
		range.setBounds(0, 0, w, h);
		range.grow(-wunit, -hunit);
		
		boolean firstNode = true;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			
			if(layoutType == GRID_LAYOUT || layoutType == GEOGRAPHICAL_LAYOUT){
				n.x = range.x + range.width * (1.0 + n.x)/2.0;
				n.y = range.y + range.height * (1.0 + n.y)/2.0;
			}
			
			if(firstNode){
				firstNode = false;
				actualRange.setBounds((int)n.x, (int)n.y, 1, 1);
			}else
				actualRange.add(n.x, n.y);
		}
		if (!firstNode) {
			double zx = actualRange.getWidth() / range.getWidth();
			double zy = actualRange.getHeight() / range.getHeight();
			
			if(zx > 1) 
				actualRange.width = (int) (actualRange.width / zx);
			if(zy > 1) 
				actualRange.height /= (int) (actualRange.height / zy);
			
			double dx = 0;
			double dy = 0;
			
			if(actualRange.x < range.x) dx = range.x - actualRange.x;
			if(actualRange.getMaxX() > range.getMaxX()) dx = range.getMaxX() - actualRange.getMaxX();
			if(actualRange.y < range.y) dy = range.y - actualRange.y;
			if(actualRange.getMaxY() > range.getMaxY()) dy = range.getMaxY() - actualRange.getMaxY();
			
			for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
				Node n = it.next();
				if(zx > 1) 
					n.x = n.x / zx;
				if(zy > 1) 
					n.y = n.y / zy;
				if(n.fixed)
					continue;
				n.x = n.x + dx;
				n.y = n.y + dy;
			}
		}
		
		// set colors for abping
		JColorScale csPing = new JColorScale();
		csPing.setSize(120, 30);
		double minPerformance = Double.MAX_VALUE;
		double maxPerformance = Double.MIN_VALUE;
		double perf, lp;
		Node n;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext(); ) {
			Node ns = e.next();
			if ( ns.connQuality.size() > 0 )  {
				for ( Enumeration<Node> e1 = ns.connQuality.keys(); e1.hasMoreElements(); ) {
					n = e1.nextElement();
					perf = ns.connPerformance(n);
					lp = ns.connLP(n);
					if(lp == 1.0)
						continue;
					if(perf < minPerformance) minPerformance = perf;
					if(perf > maxPerformance) maxPerformance = perf;
				}
			}
		}
		if(minPerformance >= maxPerformance){
			csPing.setColors(Color.GREEN, Color.GREEN);  // red(min) --> yellow(max)
			if(minPerformance == Double.MAX_VALUE){
				csPing.setValues(0, 0);
			}
		}else{
			csPing.setColors(Color.GREEN, clHiPing);  // red(min) --> yellow(max)
			csPing.setValues(minPerformance, maxPerformance);	
		}
		
		csPing.printAll(image[1].createGraphics());
		
		int i = 0;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();  i++) {
			n = it.next();
			for (Enumeration<Node> e1 = n.connQuality.keys(); e1.hasMoreElements();) {
				Node n1 = e1.nextElement();
				double val = n.connPerformance(n1);
				String lbl = lblFormat.format(val);
				Color cc;
				if(n.connLP(n1) == 1.0){
					cc = Color.RED;			// error link
					lbl = "?";
				}else
					cc = csPing.getColor(val);
				drawConn(g2, n, n1, lbl, cc);
			}
		}
		
		// paint the nodes
		Image shadow = null;
		Image bump = null;
		
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
			int x = (int)n.x;
			int y = (int)n.y;
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
			
			if (update) {
				if (withShadow) shadow = createShadow(wunit*2, hunit*2);
				if (withBump) bump = createBump(wunit*2, hunit*2);
			}
			
	        if(n.limits == null)
	        	n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
	        else
	        	n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
	        
			if (withShadow) {
				g2.drawImage(shadow,  n.limits.x, n.limits.y, null);
			}
			
			if (!cpuUSR.containsKey(n.name) || !cpuSYS.containsKey(n.name) || !cpuNICE.containsKey(n.name)){ 
				g2.setColor(Color.red);
				g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
				if (withBump) {
					g2.drawImage(bump, n.limits.x, n.limits.y, null);
				}
			} else {
				Gresult usr = cpuUSR.get(n.name);
				Gresult sys = cpuSYS.get(n.name);
				Gresult nice = cpuNICE.get(n.name);
				pie px = makeCPUpie(usr, sys, nice);
				if ((px == null) || (px.len == 0)) {
					g2.setColor(Color.yellow);
					g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				} else {
					int u1 = 0;
					for (int ii = 0; ii < px.len; ii++) {
						g2.setColor(px.cpie[ii]);
						int u2 = (int) (px.rpie[ii] * 360);
						g2.fillArc(n.limits.x, n.limits.y, 
									n.limits.width, n.limits.height,
									u1, u2);
						u1 += u2;
					}
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				}
			}

			g2.setColor(Color.black);
			g2.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
			g2.drawString(nodeName, n.limits.x + 7 + wunit - w2, 
						 n.limits.y + 5 + hunit - h2 + fm.getAscent());
		}
		
		return image;
	} 
	
	/**
	 * Returns an image identical to the one painted in the GrapPan.paintComponent
	 * @param w The width of the resulted image
	 * @param h The height of the resulted image
	 * @param makeNice If true the image will have rendering hits set on
	 * @param bgColor The background color (if null set default to white)
	 * @param nodeFont 
	 * @param links Hash with pairs (farmName -> Hash (toFarm -> Double[] {quality, lostPackages }))
	 * @param showOnlyConnectedNodes 
	 * @param withShadow 
	 * @param withBump 
	 * @param layoutType The type of layout to draw.... see the constants defined above
	 * @param diskFREE Hash with pairs (node -> GResult)
	 * @param diskUSED Hash with pairs (node -> GResult)
	 * @param geoPos If layoutType is geographical then using this hash you have to supply for each node LAT and LONG, as in
	 * 				(name -> String[] { LAT, LONG } )... otherwise ignore
	 * @param rootNode If layoutType is radial you must specify the root node's name... otherwise ignore
	 * @return A new image [image0], color scale [image1]
	 */
	public static BufferedImage[] drawGMapForDisk(int w, int h, boolean makeNice, Color bgColor, Font nodeFont, Hashtable<String, Hashtable<String, Object[]>> links, 
			boolean showOnlyConnectedNodes, boolean withShadow, boolean withBump, int layoutType, 
			Hashtable<String, Gresult> diskFREE, Hashtable<String, Gresult> diskUSED, Hashtable<String, Object[]> geoPos, String rootNode) {
		
		BufferedImage image[] = new BufferedImage[2];
		image[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image[1] = new BufferedImage(120, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image[0].createGraphics(); 
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, makeNice?RenderingHints.VALUE_ANTIALIAS_ON :RenderingHints.VALUE_ANTIALIAS_OFF);
		
		if (bgColor != null)g2.setColor(bgColor); 
		else g2.setColor(Color.WHITE);
		if (nodeFont != null) g2.setFont(nodeFont);
		else g2.setFont(nodesFont);
		g2.fillRect(0, 0, w, h);
		
		GraphTopology topology = constructTopology(w, h, links);
		
		// apply the layout
		GraphLayoutAlgorithm layout = null;
		switch (layoutType) {
		case RANDOM_LAYOUT: {
			break;
		}
		case GRID_LAYOUT: {
			layout = new GridLayout();
			break;
		}
		case GEOGRAPHICAL_LAYOUT: {
			layout = new GeographicLayout();
			break;
		}
		case RADIAL_LAYOUT: {
			Node root = null;
			if (rootNode != null) {
				for (Iterator<Node> it1 = topology.gnodes.iterator(); it1.hasNext(); ) {
					Node n = it1.next();
					if (n.name.equals(rootNode)) { root = n; break; }
				}
			}  
			if (root == null) root = topology.gnodes.getFirst();
			layout = new RadialLayout(root);
			break;
		}
		default:
			break;
		}
		
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			if (geoPos != null && geoPos.containsKey(n.name)) {
				String[] pos = (String[])geoPos.get(n.name);
				n.LAT = pos[0];
				n.LONG = pos[1];
			} else {
				n.LAT = "0.0";
				n.LONG = "0.0";
			}
		}
		
		if (layout != null) {
			layout.layout(topology);
		}
		
		// rescale if needed the points
		Rectangle range = new Rectangle();
		Rectangle actualRange = new Rectangle();
		int wunit = 0;
		int hunit = 0;
		
		FontMetrics fm = g2.getFontMetrics();
		boolean update = false;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    Node n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
	        if ( w2 > wunit ) {
	        	wunit = w2;
				update = true;
	        }
	        if ( h2 > hunit ) {
				update = true;
	        	hunit = h2;
	        }
		}
		
		range.setBounds(0, 0, w, h);
		range.grow(-wunit, -hunit);
		
		boolean firstNode = true;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			
			if(layoutType == GRID_LAYOUT || layoutType == GEOGRAPHICAL_LAYOUT){
				n.x = range.x + range.width * (1.0 + n.x)/2.0;
				n.y = range.y + range.height * (1.0 + n.y)/2.0;
			}
			
			if(firstNode){
				firstNode = false;
				actualRange.setBounds((int)n.x, (int)n.y, 1, 1);
			}else
				actualRange.add(n.x, n.y);
		}
		if (!firstNode) {
			double zx = actualRange.getWidth() / range.getWidth();
			double zy = actualRange.getHeight() / range.getHeight();
			
			if(zx > 1) 
				actualRange.width = (int) (actualRange.width / zx);
			if(zy > 1) 
				actualRange.height /= (int) (actualRange.height / zy);
			
			double dx = 0;
			double dy = 0;
			
			if(actualRange.x < range.x) dx = range.x - actualRange.x;
			if(actualRange.getMaxX() > range.getMaxX()) dx = range.getMaxX() - actualRange.getMaxX();
			if(actualRange.y < range.y) dy = range.y - actualRange.y;
			if(actualRange.getMaxY() > range.getMaxY()) dy = range.getMaxY() - actualRange.getMaxY();
			
			for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
				Node n = it.next();
				if(zx > 1) 
					n.x = n.x / zx;
				if(zy > 1) 
					n.y = n.y / zy;
				if(n.fixed)
					continue;
				n.x = n.x + dx;
				n.y = n.y + dy;
			}
		}
		
		// set colors for abping
		JColorScale csPing = new JColorScale();
		csPing.setSize(120, 30);
		double minPerformance = Double.MAX_VALUE;
		double maxPerformance = Double.MIN_VALUE;
		double perf, lp;
		Node n;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext(); ) {
			Node ns = e.next();
			if ( ns.connQuality.size() > 0 )  {
				for ( Enumeration<Node> e1 = ns.connQuality.keys(); e1.hasMoreElements(); ) {
					n = e1.nextElement();
					perf = ns.connPerformance(n);
					lp = ns.connLP(n);
					if(lp == 1.0)
						continue;
					if(perf < minPerformance) minPerformance = perf;
					if(perf > maxPerformance) maxPerformance = perf;
				}
			}
		}
		if(minPerformance >= maxPerformance){
			csPing.setColors(Color.GREEN, Color.GREEN);  // red(min) --> yellow(max)
			if(minPerformance == Double.MAX_VALUE){
				csPing.setValues(0, 0);
			}
		}else{
			csPing.setColors(Color.GREEN, clHiPing);  // red(min) --> yellow(max)
			csPing.setValues(minPerformance, maxPerformance);	
		}
		
		csPing.printAll(image[1].createGraphics());
		
		int i = 0;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();  i++) {
			n =  it.next();
			for (Enumeration<Node> e1 = n.connQuality.keys(); e1.hasMoreElements();) {
				Node n1 = e1.nextElement();
				double val = n.connPerformance(n1);
				String lbl = lblFormat.format(val);
				Color cc;
				if(n.connLP(n1) == 1.0){
					cc = Color.RED;			// error link
					lbl = "?";
				}else
					cc = csPing.getColor(val);
				drawConn(g2, n, n1, lbl, cc);
			}
		}
		
		// paint the nodes
		Image shadow = null;
		Image bump = null;
		
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
			int x = (int)n.x;
			int y = (int)n.y;
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
			
			if (update) {
				if (withShadow) shadow = createShadow(wunit*2, hunit*2);
				if (withBump) bump = createBump(wunit*2, hunit*2);
			}
			
	        if(n.limits == null)
	        	n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
	        else
	        	n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
	        
			if (withShadow) {
				g2.drawImage(shadow,  n.limits.x, n.limits.y, null);
			}
			
			if (!diskFREE.containsKey(n.name) || !diskUSED.containsKey(n.name)){ 
				g2.setColor(Color.red);
				g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
				if (withBump) {
					g2.drawImage(bump, n.limits.x, n.limits.y, null);
				}
			} else {
				Gresult fd = diskFREE.get(n.name);
				Gresult ud = diskUSED.get(n.name);
				pie px = makeDiskpie(fd, ud);
				if ((px == null) || (px.len == 0)) {
					g2.setColor(Color.yellow);
					g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				} else {
					int u1 = 0;
					for (int ii = 0; ii < px.len; ii++) {
						g2.setColor(px.cpie[ii]);
						int u2 = (int) (px.rpie[ii] * 360);
						g2.fillArc(n.limits.x, n.limits.y, 
									n.limits.width, n.limits.height,
									u1, u2);
						u1 += u2;
					}
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				}
			}

			g2.setColor(Color.black);
			g2.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
			g2.drawString(nodeName, n.limits.x + 7 + wunit - w2, 
						 n.limits.y + 5 + hunit - h2 + fm.getAscent());
		}
		
		return image;
	} 
	
	/**
	 * Returns an image identical to the one painted in the GrapPan.paintComponent
	 * @param w The width of the resulted image
	 * @param h The height of the resulted image
	 * @param makeNice If true the image will have rendering hits set on
	 * @param bgColor The background color (if null set default to white)
	 * @param nodeFont 
	 * @param links Hash with pairs (farmName -> Hash (toFarm -> Double[] {quality, lostPackages }))
	 * @param showOnlyConnectedNodes 
	 * @param withShadow 
	 * @param withBump 
	 * @param layoutType The type of layout to draw.... see the constants defined above
	 * @param inn Hash with pairs (node -> GResult)
	 * @param outn Hash with pairs (node -> GResult)
	 * @param geoPos If layoutType is geographical then using this hash you have to supply for each node LAT and LONG, as in
	 * 				(name -> String[] { LAT, LONG } )... otherwise ignore
	 * @param rootNode If layoutType is radial you must specify the root node's name... otherwise ignore
	 * @return A new image [image0], color scale [image1]
	 */
	public static BufferedImage[] drawGMapForIO(int w, int h, boolean makeNice, Color bgColor, Font nodeFont, Hashtable<String, Hashtable<String, Object[]>> links, 
			boolean showOnlyConnectedNodes, boolean withShadow, boolean withBump, int layoutType, 
			Hashtable<String, Gresult> inn, Hashtable<String, Gresult> outn, Hashtable<String, Object[]> geoPos, String rootNode) {
		
		BufferedImage image[] = new BufferedImage[2];
		image[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image[1] = new BufferedImage(120, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image[0].createGraphics(); 
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, makeNice?RenderingHints.VALUE_ANTIALIAS_ON :RenderingHints.VALUE_ANTIALIAS_OFF);
		
		if (bgColor != null)g2.setColor(bgColor); 
		else g2.setColor(Color.WHITE);
		if (nodeFont != null) g2.setFont(nodeFont);
		else g2.setFont(nodesFont);
		g2.fillRect(0, 0, w, h);
		
		GraphTopology topology = constructTopology(w, h, links);
		
		// apply the layout
		GraphLayoutAlgorithm layout = null;
		switch (layoutType) {
		case RANDOM_LAYOUT: {
			break;
		}
		case GRID_LAYOUT: {
			layout = new GridLayout();
			break;
		}
		case GEOGRAPHICAL_LAYOUT: {
			layout = new GeographicLayout();
			break;
		}
		case RADIAL_LAYOUT: {
			Node root = null;
			if (rootNode != null) {
				for (Iterator<Node> it1 = topology.gnodes.iterator(); it1.hasNext(); ) {
					Node n = it1.next();
					if (n.name.equals(rootNode)) { root = n; break; }
				}
			}  
			if (root == null) root = topology.gnodes.getFirst();
			layout = new RadialLayout(root);
			break;
		}
		default:
			break;
		}
		
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			if (geoPos != null && geoPos.containsKey(n.name)) {
				String[] pos = (String[])geoPos.get(n.name);
				n.LAT = pos[0];
				n.LONG = pos[1];
			} else {
				n.LAT = "0.0";
				n.LONG = "0.0";
			}
		}
		
		if (layout != null) {
			layout.layout(topology);
		}
		
		// rescale if needed the points
		Rectangle range = new Rectangle();
		Rectangle actualRange = new Rectangle();
		int wunit = 0;
		int hunit = 0;
		
		FontMetrics fm = g2.getFontMetrics();
		boolean update = false;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    Node n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
	        if ( w2 > wunit ) {
	        	wunit = w2;
				update = true;
	        }
	        if ( h2 > hunit ) {
				update = true;
	        	hunit = h2;
	        }
		}
		
		range.setBounds(0, 0, w, h);
		range.grow(-wunit, -hunit);
		
		boolean firstNode = true;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			
			if(layoutType == GRID_LAYOUT || layoutType == GEOGRAPHICAL_LAYOUT){
				n.x = range.x + range.width * (1.0 + n.x)/2.0;
				n.y = range.y + range.height * (1.0 + n.y)/2.0;
			}
			
			if(firstNode){
				firstNode = false;
				actualRange.setBounds((int)n.x, (int)n.y, 1, 1);
			}else
				actualRange.add(n.x, n.y);
		}
		if (!firstNode) {
			double zx = actualRange.getWidth() / range.getWidth();
			double zy = actualRange.getHeight() / range.getHeight();
			
			if(zx > 1) 
				actualRange.width = (int) (actualRange.width / zx);
			if(zy > 1) 
				actualRange.height /= (int) (actualRange.height / zy);
			
			double dx = 0;
			double dy = 0;
			
			if(actualRange.x < range.x) dx = range.x - actualRange.x;
			if(actualRange.getMaxX() > range.getMaxX()) dx = range.getMaxX() - actualRange.getMaxX();
			if(actualRange.y < range.y) dy = range.y - actualRange.y;
			if(actualRange.getMaxY() > range.getMaxY()) dy = range.getMaxY() - actualRange.getMaxY();
			
			for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
				Node n = it.next();
				if(zx > 1) 
					n.x = n.x / zx;
				if(zy > 1) 
					n.y = n.y / zy;
				if(n.fixed)
					continue;
				n.x = n.x + dx;
				n.y = n.y + dy;
			}
		}
		
		// set colors for abping
		JColorScale csPing = new JColorScale();
		csPing.setSize(120, 30);
		double minPerformance = Double.MAX_VALUE;
		double maxPerformance = Double.MIN_VALUE;
		double perf, lp;
		Node n;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext(); ) {
			Node ns = e.next();
			if ( ns.connQuality.size() > 0 )  {
				for ( Enumeration<Node> e1 = ns.connQuality.keys(); e1.hasMoreElements(); ) {
					n = e1.nextElement();
					perf = ns.connPerformance(n);
					lp = ns.connLP(n);
					if(lp == 1.0)
						continue;
					if(perf < minPerformance) minPerformance = perf;
					if(perf > maxPerformance) maxPerformance = perf;
				}
			}
		}
		if(minPerformance >= maxPerformance){
			csPing.setColors(Color.GREEN, Color.GREEN);  // red(min) --> yellow(max)
			if(minPerformance == Double.MAX_VALUE){
				csPing.setValues(0, 0);
			}
		}else{
			csPing.setColors(Color.GREEN, clHiPing);  // red(min) --> yellow(max)
			csPing.setValues(minPerformance, maxPerformance);	
		}
		
		csPing.printAll(image[1].createGraphics());
		
		int i = 0;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();  i++) {
			n = it.next();
			for (Enumeration<Node> e1 = n.connQuality.keys(); e1.hasMoreElements();) {
				Node n1 =  e1.nextElement();
				double val = n.connPerformance(n1);
				String lbl = lblFormat.format(val);
				Color cc;
				if(n.connLP(n1) == 1.0){
					cc = Color.RED;			// error link
					lbl = "?";
				}else
					cc = csPing.getColor(val);
				drawConn(g2, n, n1, lbl, cc);
			}
		}
		
		// paint the nodes
		Image shadow = null;
		Image bump = null;
		
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
			int x = (int)n.x;
			int y = (int)n.y;
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
			
			if (update) {
				if (withShadow) shadow = createShadow(wunit*2, hunit*2);
				if (withBump) bump = createBump(wunit*2, hunit*2);
			}
			
	        if(n.limits == null)
	        	n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
	        else
	        	n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
	        
			if (withShadow) {
				g2.drawImage(shadow,  n.limits.x, n.limits.y, null);
			}
			
			if (!inn.containsKey(n.name) || !outn.containsKey(n.name)){ 
				g2.setColor(Color.red);
				g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
				if (withBump) {
					g2.drawImage(bump, n.limits.x, n.limits.y, null);
				}
			} else {
				Gresult in = inn.get(n.name);
				Gresult out = outn.get(n.name);
				pie px = makeIOpie(in, out);
				if ((px == null) || (px.len == 0)) {
					g2.setColor(Color.yellow);
					g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				} else {
					int u1 = 0;
					for (int ii = 0; ii < px.len; ii++) {
						g2.setColor(px.cpie[ii]);
						int u2 = (int) (px.rpie[ii] * 360);
						g2.fillArc(n.limits.x, n.limits.y, 
									n.limits.width, n.limits.height,
									u1, u2);
						u1 += u2;
					}
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				}
			}

			g2.setColor(Color.black);
			g2.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
			g2.drawString(nodeName, n.limits.x + 7 + wunit - w2, 
						 n.limits.y + 5 + hunit - h2 + fm.getAscent());
		}
		
		return image;
	} 
	
	/**
	 * Returns an image identical to the one painted in the GrapPan.paintComponent
	 * @param w The width of the resulted image
	 * @param h The height of the resulted image
	 * @param makeNice If true the image will have rendering hits set on
	 * @param bgColor The background color (if null set default to white)
	 * @param nodeFont 
	 * @param links Hash with pairs (farmName -> Hash (toFarm -> Double[] {quality, lostPackages }))
	 * @param showOnlyConnectedNodes 
	 * @param withShadow 
	 * @param withBump 
	 * @param layoutType The type of layout to draw.... see the constants defined above
	 * @param load5 Hash with pairs (node -> GResult)
	 * @param geoPos If layoutType is geographical then using this hash you have to supply for each node LAT and LONG, as in
	 * 				(name -> String[] { LAT, LONG } )... otherwise ignore
	 * @param rootNode If layoutType is radial you must specify the root node's name... otherwise ignore
	 * @return A new image [image0], color scale [image1]
	 */
	public static BufferedImage[] drawGMapForLoad(int w, int h, boolean makeNice, Color bgColor, Font nodeFont, Hashtable<String, Hashtable<String, Object[]>> links, 
			boolean showOnlyConnectedNodes, boolean withShadow, boolean withBump, int layoutType, 
			Hashtable<String, Gresult> load5, Hashtable<String, Object[]> geoPos, String rootNode) {
		
		BufferedImage image[] = new BufferedImage[2];
		image[0] = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		image[1] = new BufferedImage(120, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image[0].createGraphics(); 
		
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, makeNice?RenderingHints.VALUE_ANTIALIAS_ON :RenderingHints.VALUE_ANTIALIAS_OFF);
		
		if (bgColor != null)g2.setColor(bgColor); 
		else g2.setColor(Color.WHITE);
		if (nodeFont != null) g2.setFont(nodeFont);
		else g2.setFont(nodesFont);
		g2.fillRect(0, 0, w, h);
		
		GraphTopology topology = constructTopology(w, h, links);
		
		// apply the layout
		GraphLayoutAlgorithm layout = null;
		switch (layoutType) {
		case RANDOM_LAYOUT: {
			break;
		}
		case GRID_LAYOUT: {
			layout = new GridLayout();
			break;
		}
	case GEOGRAPHICAL_LAYOUT: {
			layout = new GeographicLayout();
			break;
		}
		case RADIAL_LAYOUT: {
			Node root = null;
			if (rootNode != null) {
				for (Iterator<Node> it1 = topology.gnodes.iterator(); it1.hasNext(); ) {
					Node n = it1.next();
					if (n.name.equals(rootNode)) { root = n; break; }
				}
			}  
			if (root == null) root = topology.gnodes.getFirst();
			layout = new RadialLayout(root);
			break;
		}
		default:
			break;
		}
		
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			if (geoPos != null && geoPos.containsKey(n.name)) {
				String[] pos = (String[])geoPos.get(n.name);
				n.LAT = pos[0];
				n.LONG = pos[1];
			} else {
				n.LAT = "0.0";
				n.LONG = "0.0";
			}
		}
		
		if (layout != null) {
			layout.layout(topology);
		}
		
		// rescale if needed the points
		Rectangle range = new Rectangle();
		Rectangle actualRange = new Rectangle();
		int wunit = 0;
		int hunit = 0;
		
		FontMetrics fm = g2.getFontMetrics();
		boolean update = false;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    Node n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
	        if ( w2 > wunit ) {
	        	wunit = w2;
				update = true;
	        }
	        if ( h2 > hunit ) {
				update = true;
	        	hunit = h2;
	        }
		}
		
		range.setBounds(0, 0, w, h);
		range.grow(-wunit, -hunit);
		
		boolean firstNode = true;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
			Node n = it.next();
			
			if(layoutType == GRID_LAYOUT || layoutType == GEOGRAPHICAL_LAYOUT){
				n.x = range.x + range.width * (1.0 + n.x)/2.0;
				n.y = range.y + range.height * (1.0 + n.y)/2.0;
			}
			
			if(firstNode){
				firstNode = false;
				actualRange.setBounds((int)n.x, (int)n.y, 1, 1);
			}else
				actualRange.add(n.x, n.y);
		}
		if (!firstNode) {
			double zx = actualRange.getWidth() / range.getWidth();
			double zy = actualRange.getHeight() / range.getHeight();
			
			if(zx > 1) 
				actualRange.width = (int) (actualRange.width / zx);
			if(zy > 1) 
				actualRange.height /= (int) (actualRange.height / zy);
			
			double dx = 0;
			double dy = 0;
			
			if(actualRange.x < range.x) dx = range.x - actualRange.x;
			if(actualRange.getMaxX() > range.getMaxX()) dx = range.getMaxX() - actualRange.getMaxX();
			if(actualRange.y < range.y) dy = range.y - actualRange.y;
			if(actualRange.getMaxY() > range.getMaxY()) dy = range.getMaxY() - actualRange.getMaxY();
			
			for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();) {
				Node n = it.next();
				if(zx > 1) 
					n.x = n.x / zx;
				if(zy > 1) 
					n.y = n.y / zy;
				if(n.fixed)
					continue;
				n.x = n.x + dx;
				n.y = n.y + dy;
			}
		}
		
		// set colors for abping
		JColorScale csPing = new JColorScale();
		csPing.setSize(120, 30);
		double minPerformance = Double.MAX_VALUE;
		double maxPerformance = Double.MIN_VALUE;
		double perf, lp;
		Node n;
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext(); ) {
			Node ns = e.next();
			if ( ns.connQuality.size() > 0 )  {
				for ( Enumeration<Node> e1 = ns.connQuality.keys(); e1.hasMoreElements(); ) {
					n = e1.nextElement();
					perf = ns.connPerformance(n);
					lp = ns.connLP(n);
					if(lp == 1.0)
						continue;
					if(perf < minPerformance) minPerformance = perf;
					if(perf > maxPerformance) maxPerformance = perf;
				}
			}
		}
		if(minPerformance >= maxPerformance){
			csPing.setColors(Color.GREEN, Color.GREEN);  // red(min) --> yellow(max)
			if(minPerformance == Double.MAX_VALUE){
				csPing.setValues(0, 0);
			}
		}else{
			csPing.setColors(Color.GREEN, clHiPing);  // red(min) --> yellow(max)
			csPing.setValues(minPerformance, maxPerformance);	
		}
		
		csPing.printAll(image[1].createGraphics());
		
		int i = 0;
		for (Iterator<Node> it = topology.gnodes.iterator(); it.hasNext();  i++) {
			n = it.next();
			for (Enumeration<Node> e1 = n.connQuality.keys(); e1.hasMoreElements();) {
				Node n1 = e1.nextElement();
				double val = n.connPerformance(n1);
				String lbl = lblFormat.format(val);
				Color cc;
				if(n.connLP(n1) == 1.0){
					cc = Color.RED;			// error link
					lbl = "?";
				}else
					cc = csPing.getColor(val);
				drawConn(g2, n, n1, lbl, cc);
			}
		}
		
		// paint the nodes
		Image shadow = null;
		Image bump = null;
		
		for (Iterator<Node> e = topology.gnodes.iterator(); e.hasNext();) {
		    n = e.next();
		    if(showOnlyConnectedNodes && n.connQuality.size() == 0) continue;
			
			int x = (int)n.x;
			int y = (int)n.y;
	        String nodeName = n.name;
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
	        int h2 = (fm.getHeight() + 10) / 2;
			
			if (update) {
				if (withShadow) shadow = createShadow(wunit*2, hunit*2);
				if (withBump) bump = createBump(wunit*2, hunit*2);
			}
			
	        if(n.limits == null)
	        	n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
	        else
	        	n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
	        
			if (withShadow) {
				g2.drawImage(shadow,  n.limits.x, n.limits.y, null);
			}
			
			if (load5 == null || !load5.containsKey(n.name)){ 
				g2.setColor(Color.red);
				g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
				if (withBump) {
					g2.drawImage(bump, n.limits.x, n.limits.y, null);
				}
			} else {
				Gresult ldx = load5.get(n.name);
				pie px = makeLoadpie(ldx);
				if ((px == null) || (px.len == 0)) {
					g2.setColor(Color.yellow);
					g2.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				} else {
					int u1 = 0;
					for (int ii = 0; ii < px.len; ii++) {
						g2.setColor(px.cpie[ii]);
						int u2 = (int) (px.rpie[ii] * 360);
						g2.fillArc(n.limits.x, n.limits.y, 
									n.limits.width, n.limits.height,
									u1, u2);
						u1 += u2;
					}
					if (withBump) {
						g2.drawImage(bump, n.limits.x, n.limits.y, null);
					}
				}
			}

			g2.setColor(Color.black);
			g2.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
			g2.drawString(nodeName, n.limits.x + 7 + wunit - w2, 
						 n.limits.y + 5 + hunit - h2 + fm.getAscent());
		}
		
		return image;
	} 
	
	private static GraphTopology constructTopology(int w, int h, Hashtable<String, Hashtable<String, Object[]>> links) {
		
		Hashtable<String, Node> set = new Hashtable<String, Node>();
		
		GraphTopology topo = new GraphTopology();
		for (Enumeration<String> en = links.keys(); en.hasMoreElements(); ) {
			String farmName = en.nextElement();
			Hashtable<String, Object[]> v = links.get(farmName);
			if (v == null) {
				if (!set.containsKey(farmName)) {
					Node n = new Node(w, h, farmName);
					set.put(farmName, n);
					topo.add(n);
				}
				continue;
			}
			Node n = null;
			if (!set.containsKey(farmName)) {
				n = new Node(w, h, farmName);
				set.put(farmName, n);
				topo.add(n);
			} else {
				n = set.get(farmName);
			}
			for (Enumeration<String> en1 = v.keys(); en1.hasMoreElements(); ) {
				String toFarm = en1.nextElement();
				Object[] obj = v.get(toFarm);
				if (obj == null) continue;
				Double quality = (Double)obj[0];
				Double losts = (Double)obj[1];
				Node to = null;
				if (set.containsKey(toFarm)) to = set.get(toFarm);
				else { to = new Node(w, h, toFarm); set.put(toFarm, to); topo.add(to);  }
				n.addLink(to, quality, losts);
			}
		}
		
		return topo;
	}
	
	/**
	 * @param gg
	 * @param n
	 * @param n1
	 * @param lbl
	 * @param col
	 */
	public static void drawConn(Graphics gg, Node n, Node n1, String lbl, Color col) {
		drawConn( gg, n, n1, lbl, col, 0);
	}
	
	/**
	 * nOrder has values:
	 * 0 - means that default value for dd is used: 6
	 * 1-.. - means that a value of 4*nOrder is used for dd
	 * Also, position of arrow is computed based on nOrder: if 0, is centered, with a dimension of 6,
	 * if 1 or more, is shifted up or down a little bit 
	 * @param gg
	 * @param n
	 * @param n1
	 * @param lbl
	 * @param col
	 * @param nOrder 
	 */
	public static void drawConn(Graphics gg, Node n, Node n1, String lbl, Color col, int nOrder) {

	    final int DD = 6;
	    int dd;
	    if ( nOrder == 0 )
	        dd = DD;
	    else
	        dd = 4*nOrder;

		gg.setColor(col);

		int dx = (int)(n1.x - n.x);
		int dy = (int)(n1.y - n.y);
		float l = (float) Math.sqrt(dx * dx + dy * dy);
		float dir_x = dx / l;
		float dir_y = dy / l;
		int dd1 = dd;

		int x1p = (int)(n.x - (int) (dd1 * dir_y));
		int x2p = (int)(n1.x - (int) (dd1 * dir_y));
		int y1p = (int)(n.y + (int) (dd1 * dir_x));
		int y2p = (int)(n1.y + (int) (dd1 * dir_x));

		gg.drawLine(x1p, y1p, x2p, y2p);

		int xv = (int)((x1p + x2p + (x1p-x2p)*(float)nOrder/20.0f) / 2.0f);
		int yv = (int)((y1p + y2p + (y1p-y2p)*(float)nOrder/20.0f) / 2.0f);

		float aa;//(float) (dd) / (float) 2.0;
		if ( nOrder == 0 )
		    aa = dd / 2.0f;
		else
		    aa = 2.0f;

		int[] axv =
			{
				(int) (xv - aa * dir_x + 2 * aa * dir_y),
				(int) (xv - aa * dir_x - 2 * aa * dir_y),
				(int) (xv + 2 * aa * dir_x),
				(int) (xv - aa * dir_x + 2 * aa * dir_y)};

		int[] ayv =
			{
				(int) (yv - aa * dir_y - 2 * aa * dir_x),
				(int) (yv - aa * dir_y + 2 * aa * dir_x),
				(int) (yv + 2 * aa * dir_y),
				(int) (yv - aa * dir_y - 2 * aa * dir_x)};

		gg.fillPolygon(axv, ayv, 4);

		if ( lbl != null && lbl.length()!=0 ) {
		    //int ddl = 6;
		    // String lbl = "" + (int) perf ;
		    FontMetrics fm = gg.getFontMetrics();
		    int wl = fm.stringWidth(lbl) + 1;
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
		    
		    gg.drawString(lbl, xl, yl);
		}
		gg.setColor(Color.black);
	}
	
	private static Image createShadow(int w, int h) {
		
		BufferedImage bshadow = new BufferedImage(w+14, h+14, Transparency.TRANSLUCENT);
        Graphics2D g = bshadow.createGraphics();
		g.setColor(Color.cyan);
		g.fillOval(6, 6, w, h);
		ConvolveOp blurOp = getBlurOp(7);
		blurOp.filter(createShadowMask(bshadow), bshadow);
		return toImage(bshadow);
	}

	/**
	 * @param bufferedImage
	 * @return image
	 */
	public static Image toImage(BufferedImage bufferedImage) {
        return Toolkit.getDefaultToolkit().createImage(bufferedImage.getSource());
    }

	private static ConvolveOp getBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1 / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
    }

	private static BufferedImage createShadowMask(BufferedImage image) {
		
		  BufferedImage mask = new BufferedImage(image.getWidth(),
		                                         image.getHeight(),
		                                         BufferedImage.TYPE_INT_ARGB);
		  for (int x = 0; x < image.getWidth(); x++) {
		    for (int y = 0; y < image.getHeight(); y++) {
		      int argb = image.getRGB(x, y);
		      argb = (int) ((argb >> 24 & 0xFF) * 0.5) << 24 |
		             shadowColor.getRGB() & 0x00FFFFFF;
		      mask.setRGB(x, y, argb);
		    }
		  }
		  return mask;
	}

	private static Image createBump(int w, int h) {
		
		BufferedImage bbump = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        Graphics2D g = bbump.createGraphics();
		Rectangle rect = new Rectangle(0, 0, w, h);
		SphereTexture.setTexture(g, rect);
		g.fillOval(0, 0, w, h);
		return toImage(bbump);
	}

	/**
	 * @param usr
	 * @param sys
	 * @param nice
	 * @return pie
	 */
	public static pie makeCPUpie(Gresult usr, Gresult sys, Gresult nice) {

		pie p1 = new pie(4);
		p1.cpie[0] = Color.pink;
		p1.cpie[1] = Color.blue;
		p1.cpie[2] = Color.green;
		p1.cpie[3] = Color.red;
		
		if ((usr == null) || (nice == null)) {
			p1.len = 0;
		} else {
			p1.len = 4;
			if (usr.Nodes == usr.TotalNodes) // nodes in error 
				p1.rpie[3] = 0;
			else
				p1.rpie[3] =
					(double) (usr.TotalNodes - usr.Nodes)
					/ (double) usr.TotalNodes;
			
			p1.rpie[0] =
				(usr.sum + nice.sum) * 0.01 / usr.TotalNodes;
			
			if (sys != null)
				p1.rpie[1] = sys.sum * 0.01 / usr.TotalNodes;
			else
				p1.rpie[1] = 0.0;
			
			p1.rpie[2] = 1.0 - p1.rpie[0] - p1.rpie[1] - p1.rpie[3];
		}
		return p1;
	}
	
	/**
	 * @param fd
	 * @param ud
	 * @return pie
	 */
	public static pie makeDiskpie(Gresult fd, Gresult ud) {

		pie p1 = new pie(2);
		p1.cpie[0] = Color.pink;
		p1.cpie[1] = Color.green;
		if ((fd == null) || (ud == null)) {
			p1.len = 0;
		} else {
			p1.len = 2;
			double sum = fd.sum + ud.sum;
			p1.rpie[0] = ud.sum / sum;
			p1.rpie[1] = fd.sum / sum;
		}
		return p1;
	}
	
	/**
	 * @param inn
	 * @param outn
	 * @return pie
	 */
	public static pie makeIOpie(Gresult inn, Gresult outn) {
		
		pie p1 = new pie(2);
		p1.cpie[0] = Color.pink;
		p1.cpie[1] = Color.blue;
		if ((inn == null) || (outn == null)) {
			p1.len = 0;
		} else {
			p1.len = 2;
			double sum = inn.mean + outn.mean;
			p1.rpie[0] = inn.mean / sum;
			p1.rpie[1] = outn.mean / sum;
		}
		
		return p1;
	}
	
	/**
	 * @param ldx
	 * @return pie
	 */
	public static pie makeLoadpie(Gresult ldx) {
		
		pie p1 = new pie(3);
		p1.cpie[0] = Color.pink;
		p1.cpie[1] = Color.blue;
		p1.cpie[2] = Color.green;
		if ((ldx == null) || (ldx.hist == null)) {
			p1.len = 0;
		} else {
			p1.len = 3;
			p1.rpie[0] = ldx.hist[4] / (double) ldx.Nodes;
			p1.rpie[1] = (ldx.hist[3] + ldx.hist[2]) / (double) ldx.Nodes;
			p1.rpie[2] = (ldx.hist[0] + ldx.hist[1]) / (double) ldx.Nodes;
		}
		return p1;
	}
	
	/** Tester 
	 * @param args */
	public static void main(String args[]) {
		
		// prepare a frame to show the resulted image
		JFrame f = new JFrame("Tester");
		JPanel p = new JPanel();
		JLabel l = new JLabel();
		f.getContentPane().setLayout(new BorderLayout());
		f.getContentPane().add(p, BorderLayout.CENTER);
		p.setLayout(new BorderLayout());
		p.add(l, BorderLayout.CENTER);
		f.setSize(500, 500);
		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		 /* @param links Hash with pairs (farmName -> Hash (toFarm -> Object[] {quality, lostPackages })) */
		Hashtable<String, Hashtable<String, Object[]>> links = new Hashtable<String, Hashtable<String, Object[]>>();
		Hashtable<String, Object[]> l1 = new Hashtable<String, Object[]>();
		l1.put("test2", new Double[] { Double.valueOf(1.0), Double.valueOf(0.0) });
		Hashtable<String, Object[]> l2 = new Hashtable<String, Object[]>();
		l2.put("test1", new Double[] { Double.valueOf(1.0), Double.valueOf(0.0) });
		links.put("test1", l1);
		links.put("test2", l2);
		 /* @param cpuUSR Hash with pairs (node -> GResult)
		 * @param cpuSYS Hash with pairs (node -> GResult)
		 * @param cpuNICE Hash with pairs (node -> GResult) */
		Hashtable<String, Gresult> usr = new Hashtable<String, Gresult>();
		Hashtable<String, Gresult> sys = new Hashtable<String, Gresult>();
		Hashtable<String, Gresult> nice = new Hashtable<String, Gresult>();
		Gresult res = new Gresult();
		res.TotalNodes = 2;
		res.Nodes = 1;
		res.sum = 10;
		usr.put("test1", res);
		usr.put("test2", res);
		sys.put("test1", res);
		sys.put("test2", res);
		nice.put("test1", res);
		nice.put("test2", res);
		 /* @param geoPos If layoutType is geographical then using this hash you have to supply for each node LAT and LONG, as in
		 * 				(name -> String[] { LAT, LONG } )... otherwise ignore */
		Hashtable<String, Object[]> geoPos = new Hashtable<String, Object[]>();
		geoPos.put("test1", new String[] { "10.2", "-50.0" });
		
		// prepare the image
		BufferedImage im[] = drawGMapForCPU(500, 500, true, null, null, links, false, true, true, RANDOM_LAYOUT, usr, sys, nice, geoPos, "test1");
		l.setIcon(new ImageIcon(im[0].getScaledInstance(500, 500, 1)));
		
		f.setVisible(true);
	}
	
} // end of class GMap


