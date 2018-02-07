package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.ILink;
import lia.Monitor.monitor.Result;
import lia.util.geo.iNetGeoManager;
import lia.util.ntp.NTPDate;


public class StaticGMap {
	int width, height;		// the size of the image
	BufferedImage image;	// the image
	Graphics graphics;		// corresponding graphics context
	Hashtable<String, rcNode> nodes;		// key = farm name; value = rcNode
	int wunit, hunit;		// maximum width & height of the node bubbles
	rcNode startNode;		// compute dijkstra & others starting from this node 
	boolean antialiased;	// draw antialiased lines 
	String nodesShow;		/** @see drawGmap */
	String linksShow;		/** @see drawGmap */
	Hashtable nodesShowColors;	/** @see drawGmap */
	Hashtable linksShowColors;	/** @see drawGmap */
	double minValue, maxValue;	// min and max values for a thing that is going to be drawn 
	Color minColor, maxColor;	// min and max color for a thing that is going to be drawn
	Hashtable addrIPCache;		// key = hostName; value = ipAddress
	iNetGeoManager iNetGeo;		// helper class to get geographic coords for WAN Links
	DecimalFormat numberFormatter;	// used to format how a float number is drawn 
	GraphLayoutAlgorithm layout;	// layout to use
	GraphTopology vGraph;			// the visual graph, used in layout
	
	public StaticGMap(int width, int height, Hashtable nodes){
		this.width = width;
		this.height = height;
		this.nodes = nodes;
		image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		graphics = image.getGraphics();
		Font nodesFont = new Font("Arial", Font.PLAIN, 10);
		graphics.setFont(nodesFont);
		numberFormatter = new DecimalFormat("###,###.#");
		//TODO: initialization will fail if "lia.util.geo.iNetGeoConfig" property
		//isn't set.
		iNetGeo = new iNetGeoManager();
	}

	/**
	 * @param properties - contains the following keys: 
	 * * "nodesShow" -> one of "CPUPie", "DiskPie", "IOPie", "LoadPie" for farms
	 *      or "Audio", "Video", "VirtualRooms", "Load", "Traffic" for vrvs
	 * * "linksShow" -> one of: "abping", [NOT YET: "wan", "dijkstra"] for farms 
	 *      or "abping", "peers", [NOT YET: "mst"] for vrvs
	 * * "layout" -> can be: "random", "grid", "layered", "radial";
	 * 	    "layered" and "radial" can be used ONLY with linksShow="peers" for vrvs!
	 * * "startNodeName" -> the name of the node to start from; required only for
	 *      "layered" and "radial" layouts  
	 * * "antialiasing" -> one of: "true" or "false" - how to draw the scene
	 * @param nodesShowColors - hashtable containing colors to draw the nodes for 
	 *    vrvs. If nodesShow="Audio", nodesShowColors should contain two values:
	 *    "Audio_min" -> Color(min) and "Audio_max" -> Color(max), and so on. 
	 * @param linksShowColors - hashtable containing colors to draw the links 
	 *    f linksShow="abing", linksShowColors should contain two values:
	 *    "abping_min" -> Color(min) and "abping_max" -> Color(max), and so on. 
	 * @return - the buffered image containing the scene drawn as directed
	 */
	public BufferedImage drawGmap(Hashtable properties, 
				Hashtable nodesShowColors, Hashtable linksShowColors){
		if(properties == null || nodesShowColors == null || linksShowColors == null)
			return null;
		// get properties
		String startNodeName = (String) properties.get("startNodeName");
		String layoutType = (String) properties.get("layout");
		String antialiasing = (String) properties.get("antialiasing");
		startNode = (startNodeName == null ? null : (rcNode) nodes.get(startNodeName));
		antialiased = Boolean.valueOf(antialiasing).booleanValue();
		this.nodesShow = (String) properties.get("nodesShow");
		this.linksShow = (String) properties.get("linksShow");
		this.nodesShowColors = nodesShowColors;
		this.linksShowColors = linksShowColors;

		if(nodesShow == null || linksShow == null)
			return null;
		
		if(layoutType == null)
			layoutType = "random";
		
		// prepare
		((Graphics2D)graphics).setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				antialiased ? RenderingHints.VALUE_ANTIALIAS_ON :
							  RenderingHints.VALUE_ANTIALIAS_OFF);
		// work
		setLayoutType(layoutType);
		paintLinks();
		paintNodes();
		return image;
	}

	/** returns the graphics corresponding to the image */
	public Graphics getGraphics(){
		return graphics;
	}

	/** used to compute a layout for the nodes */
	private void setLayoutType(String type){
		if(type.equals("layered") && nodesShow.equals("peers")){
			vGraph = GraphTopology.constructTreeFromPeers(nodes);
			boolean contains = false;
			for (Enumeration en = nodes.elements(); en.hasMoreElements(); ) {
				if (startNode.equals(en.nextElement())) { contains = true; break; }
			}
			if((startNode == null) || (!contains))
				startNode = vGraph.findARoot();
			layout = new LayeredTreeLayoutAlgorithm(vGraph, startNode);
			computeNewLayout();
		}else if(type.equals("radial") && nodesShow.equals("peers")){
			vGraph = GraphTopology.constructTreeFromPeers(nodes);
			boolean contains = false;
			for (Enumeration en = nodes.elements(); en.hasMoreElements(); ) {
				if (startNode.equals(en.nextElement())) { contains = true; break; }
			}
			if((startNode == null) || (!contains))
				startNode = vGraph.findARoot();
			layout = new RadialLayoutAlgorithm(vGraph, startNode);
			computeNewLayout();
		}else if(type.equals("random")){
			vGraph = GraphTopology.constructUnconnectedGraph(nodes);
			layout = new RandomLayoutAlgorithm(vGraph);
			computeNewLayout();			
		}else if(type.equals("grid")){
			vGraph = GraphTopology.constructGraphInet4VRVS(nodes);
			layout = new GridLayoutAlgorithm(vGraph);
			computeNewLayout();
		}
	}

	/** used internally to compute the new positions for each node */
	private void computeNewLayout(){
		Rectangle range = new Rectangle(0, 0, width, height);
		range.grow(-wunit, -hunit);
		
		layout.layOut();
		for(Iterator it=vGraph.gnodes.iterator(); it.hasNext();){
			GraphNode gn = (GraphNode) it.next();
			if(! gn.rcnode.fixed){
				gn.rcnode.x = range.x + (int) (range.width * (1.0 + gn.pos.x)/2.0);
				gn.rcnode.y = range.y + (int) (range.height * (1.0 + gn.pos.y)/2.0);
			}
		}
		vGraph = null;
		layout = null;
	}
	
	/** compute all about nodes and paint them */
	private void paintNodes(){
		computeNodesAttributes();
		makeCPUpie();
		makeDiskpie();
		makeIOpie();
		makeLoadpie();
		for(Enumeration en = nodes.elements(); en.hasMoreElements(); ){
			rcNode n = (rcNode) en.nextElement();
			paintNode(n);
		}
		//TODO: use the min/max values and colors to draw a colorscale
	}
	
	/** used internally to compute values for each node, before drawing */
	private void computeNodesAttributes(){
		FontMetrics fm = graphics.getFontMetrics();
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		// if we have a bubble, compute min/max values
		
		for(Enumeration en = nodes.elements(); en.hasMoreElements(); ){
			rcNode n = (rcNode) en.nextElement();
			String nodeName = n.shortName;
			// compute 
			int w2 = (fm.stringWidth(nodeName) + 15) / 2;
			int h2 = (fm.getHeight() + 10) / 2;
			if ( w2 > wunit ) 
				wunit = w2;
			if ( h2 > hunit ) 
				hunit = h2;
			if(nodesShow.indexOf("Pie") < 0){
				try {
					double crt = Double.parseDouble((String)n.haux.get(nodesShow));
					min = (min < crt) ? min : crt;
					max = (max > crt) ? max : crt;
				}catch(NumberFormatException ex){
					// ignore
				}
			}
		}
		if(nodesShow.indexOf("Pie") < 0){
			if (min > max) {
				min = max = 0.0;
			}
			if (min < max){
				setColorSet(min, max, (Color)nodesShowColors.get(nodesShow+"_min"),
					(Color)nodesShowColors.get(nodesShow+"_max"));
			}else{
				setColorSet(min, min, (Color)nodesShowColors.get(nodesShow+"_min"),
					(Color)nodesShowColors.get(nodesShow+"_min"));
			}
		}
	}

	/** used internally to paint a node on a graphics context 
	 * @param g - the graphics context
	 * @param n - the node to be painted
	 * @param hauxKeys - what to draw for the node - contains a list of keys 
	 *                  from the n.haux hashtable
	 */
	private void paintNode(rcNode n) {
		int x = n.x;
		int y = n.y;
		String nodeName = n.shortName;
		
		if(n.limits == null)
			n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
		else
			n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
		
		Color c = null;
		if(n.errorCount>0)
			c = Color.RED;
		else{
			// ErrorKey is for vrvs
			if(n.haux.get("ErrorKey") != null && n.haux.get("ErrorKey").equals("1") ){
//				System.out.println(n.UnitName+"-ErrorKey>"+n.haux.get("ErrorKey"));
				c = Color.PINK;
			}
			if ( n.haux.get("lostConn") != null && n.haux.get("lostConn").equals("1") ){
//				System.out.println(n.UnitName+"-lostConn>"+n.haux.get("lostConn"));
				c = Color.RED;
			}
			if(c==null && nodesShow.indexOf("Pie") >= 0){
				// we'll have a pie
				pie px = (pie) n.haux.get(nodesShow);
				int u1 = 0;
				for (int i = 0; i < px.len; i++) {
					graphics.setColor(px.cpie[i]);
					int u2 = (int) (px.rpie[i] * 360);
					graphics.fillArc(n.limits.x, n.limits.y, 
							n.limits.width, n.limits.height,
							u1, u2);
					u1 += u2;
				}
			}else if(c == null){
				// we'll have a coloured bubble
				try{
					double val = Double.parseDouble((String)n.haux.get(nodesShow));
					Color cc = getColor(val);
					c = cc;
					graphics.setColor(c);
					graphics.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
				}catch(NumberFormatException ex){
					// ignore
				}
			}
			if(c == null){
				c = Color.PINK;
				graphics.setColor(c);
				graphics.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
			}
		}
		Color invC = Color.WHITE;
		if(n.fixed || n == startNode)
			invC = new Color(255-c.getRed(), 255-c.getGreen(), 255-c.getBlue());		
		if(n.fixed)
			graphics.setColor(invC);
		else
			graphics.setColor(Color.BLACK);
		graphics.drawString(nodeName, n.limits.x + 7 + wunit - wunit, 
				n.limits.y + 5 + hunit - hunit + graphics.getFontMetrics().getAscent());
		if(n.equals(startNode))
			graphics.setColor(invC);
		graphics.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
	}

	/** compute all about links and paint them */
	private void paintLinks(){
		computeLinksAttributes();
		if(linksShow.equals("abping")){
			for(Enumeration en=nodes.elements(); en.hasMoreElements(); ){
				rcNode n = (rcNode) en.nextElement();
				for(Enumeration el=n.wconn.elements(); el.hasMoreElements(); ){
					ILink link = (ILink) el.nextElement();
					rcNode n1 = nodes.get(link.name);
					double val = (link.inetQuality == null ? -1 : link.inetQuality[0]);
					if(val == -1)
						continue;
					String lbl = numberFormatter.format(val);
					Color col = getColor(val);
					paintDirectedLink(n, n1, lbl, col);
				}
			}
		}else if(linksShow.equals("peers")){
			for(Enumeration en=nodes.elements(); en.hasMoreElements(); ){
				rcNode n = (rcNode) en.nextElement();
				for(Enumeration el=n.wconn.elements(); el.hasMoreElements(); ){
					ILink link = (ILink) el.nextElement();
					rcNode n1 = nodes.get(link.name);
					double val = (link.peersQuality == null ? -1 : link.peersQuality[0]);
					if(val == -1)
						continue;
					String lbl = numberFormatter.format(val);
					Color col = getColor(val);
					paintDirectedLink(n, n1, lbl, col);
				}
			}
		} 
		//linksShow.equals("wan") || linksShow.equals("dijkstra") || mst
		
		//TODO: use the min/max values and colors to draw a colorscale
	}
	
	/** used internally to compute values for each node, before drawing */
	private void computeLinksAttributes(){
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		
		if(linksShow.equals("abping")){
			for(Enumeration en=nodes.elements(); en.hasMoreElements(); ){
				rcNode n = (rcNode) en.nextElement();
				for(Enumeration el=n.wconn.elements(); el.hasMoreElements(); ){
					ILink link = (ILink) el.nextElement();
//					rcNode n1 = (rcNode) nodes.get(link.name);
					double crt = (link.inetQuality == null ? -1 : link.inetQuality[0]);
					if(crt == -1)
						continue;
					min = (min < crt) ? min : crt;
					max = (max > crt) ? max : crt;
				}
			}
		}else if(linksShow.equals("peers")){
			for(Enumeration en=nodes.elements(); en.hasMoreElements(); ){
				rcNode n = (rcNode) en.nextElement();
				for(Enumeration el=n.wconn.elements(); el.hasMoreElements(); ){
					ILink link = (ILink) el.nextElement();
//					rcNode n1 = (rcNode) nodes.get(link.name);
					double crt = (link.peersQuality == null ? -1 : link.peersQuality[0]);
					if(crt == -1)
						continue;
					min = (min < crt) ? min : crt;
					max = (max > crt) ? max : crt;
				}
			}
		}
		if (min > max) {
			min = max = 0.0;
		}
		if (min < max){
			setColorSet(min, max, (Color)linksShowColors.get(linksShow+"_min"),
				(Color)linksShowColors.get(nodesShow+"_max"));
		}else{
			setColorSet(min, min, (Color)linksShowColors.get(linksShow+"_min"),
				(Color)linksShowColors.get(nodesShow+"_min"));
		}
	}
	
//	/** used internally to paint an undirected link between two nodes */
//	private void paintUndirectedLink(rcNode n, rcNode n1, Color col){
//		graphics.setColor(col);
//		graphics.drawLine ( n.x,n.y,n1.x,n1.y);
//	}
	
	/** used internally to paint a directed link between two nodes */
	private void paintDirectedLink(rcNode n, rcNode n1, String lbl, Color col) {
		graphics.setColor(col);
		int dd = 6;
		int dx = (n1.x - n.x);
		int dy = n1.y - n.y;
		float l = (float) Math.sqrt(dx * dx + dy * dy);
		float dir_x = dx / l;
		float dir_y = dy / l;
		int dd1 = dd;

		int x1p = n.x - (int) (dd1 * dir_y);
		int x2p = n1.x - (int) (dd1 * dir_y);
		int y1p = n.y + (int) (dd1 * dir_x);
		int y2p = n1.y + (int) (dd1 * dir_x);

		graphics.drawLine(x1p, y1p, x2p, y2p);

		int xv = (x1p + x2p) / 2;
		int yv = (y1p + y2p) / 2;

		float aa = dd / 2.0f;

		int[] axv={	(int) (xv - aa * dir_x + 2 * aa * dir_y),
							(int) (xv - aa * dir_x - 2 * aa * dir_y),
					(int) (xv + 2 * aa * dir_x),
							(int) (xv - aa * dir_x + 2 * aa * dir_y)};
		int[] ayv={	(int) (yv - aa * dir_y - 2 * aa * dir_x),
							(int) (yv - aa * dir_y + 2 * aa * dir_x),
					(int) (yv + 2 * aa * dir_y),
							(int) (yv - aa * dir_y - 2 * aa * dir_x)};

		graphics.fillPolygon(axv, ayv, 4);
//		int ddl = 6;
		FontMetrics fm = graphics.getFontMetrics();
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

		graphics.drawString(lbl, xl, yl);
		graphics.setColor(Color.black);
	}
	
	/** set the bounding values and colors - used before plotting all nodes
	 * and all links
	 */
	private void setColorSet(double minValue, double maxValue, 
							 Color minColor, Color maxColor){
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.minColor = minColor;
		this.maxColor = maxColor;
	}
	
	/** 
	 * compute a color between minColor and maxColor for the given 
	 * value between minValue and maxValue
	 */
	private Color getColor(double val) {
		
		double delta = Math.abs(maxValue - minValue);
		if (Math.abs(delta) < 1E-5)
			return minColor;
		int R,G,B;
		if (maxValue > minValue ) {
			R = (int) ((val - minValue) * (maxColor.getRed() - minColor.getRed()) / delta)
			+ minColor.getRed();
			G = (int) ((val - minValue) * (maxColor.getGreen() - minColor.getGreen()) / delta)
			+ minColor.getGreen();
			B = (int) ((val - minValue) * (maxColor.getBlue() - minColor.getBlue()) / delta)
			+ minColor.getBlue();
		} else {
			R = (int) ((val - maxValue) * (minColor.getRed() - maxColor.getRed()) / delta)
			+ maxColor.getRed();
			G = (int) ((val - maxValue) * (minColor.getGreen() - maxColor.getGreen()) / delta)
			+ maxColor.getGreen();
			B = (int) ((val - maxValue) * (minColor.getBlue() - maxColor.getBlue()) / delta)
			+ maxColor.getBlue();
		}
		
		if (R<0) R = 0; if (R>255) R=255; if (G<0) G=0; if (G>255) G=255; if(B<0) B=0; if(B>255) B=255;

		return new Color(R, G, B);
	}
	
	/** use this to add a new rcNode */
	public void addNode(String nodeName, String ipAddress, String LONG, String LAT){
		rcNode n = new rcNode();
		n.wconn = new Hashtable();
		n.fixed = false;
		n.selected = false;
		n.errorCount = 0;
		n.x = (int) ( width * Math.random() );
		n.y = (int) ( height * Math.random() );
		n.UnitName = nodeName;
		n.setShortName();
		n.IPaddress = ipAddress;
		n.LONG = LONG;
		n.LAT = LAT;
		nodes.put(nodeName, n);
	}

	/** use this to add a result. This will invoke addReflectorInfo and
	 * addLinkInfo with appropriate parameters 
	 */ 
	public void addResult(Result r){
		rcNode ns = nodes.get(r.FarmName);
		if(ns == null)
			return;
		if(r.ClusterName.equals("Reflector"))
			addReflectorInfo(ns, r);
		else if(r.ClusterName.equals("WAN"))
			addWANInfo(ns, r);
		else if(r.ClusterName.equals("Peers") || r.ClusterName.equals("Internet")
					|| r.ClusterName.equals("ABPing")){
			rcNode nw = getNodeByHostName(r.NodeName);
			addLinkInfo(ns, nw, r);
		}
	}
	
	/** use this to add some info for a vrvs reflector */
	public void addReflectorInfo(rcNode ns, Result r){
		
		if (r.param_name == null || r.param == null) return;
		if (r.ClusterName !=null && r.ClusterName.equals("Reflector")) {
			for (int i = 0; i < r.param_name.length; i++)
				if (r.param_name[i].equals("Video"))
					ns.haux.put("Video", Double.valueOf(r.param[i]));
				else if (r.param_name[i].equals("Audio"))
					ns.haux.put("Audio", Double.valueOf(r.param[i]));
				else if (r.param_name[i].equals("VirtualRooms"))
					ns.haux.put("VirtualRooms", Double.valueOf(r.param[i]));
				else if (r.param_name[i].indexOf("_IN") != -1 )
					ns.haux.put(r.param_name[i], Double.valueOf(r.param[i]));
				else if (r.param_name[i].indexOf("_OUT") != -1 )
					ns.haux.put(r.param_name[i], Double.valueOf(r.param[i]));
				else if (r.param_name[i].equals("Load5"))
					ns.haux.put("Load", Double.valueOf(r.param[i]));
		}
	}
	
	/** use this to add some global information for a farm */
	public void addGlobalFarmInfo(Gresult gr) {
		rcNode ns = nodes.get(gr.FarmName);
		if (ns == null)
			return;
		ns.time = NTPDate.currentTimeMillis();
		ns.global_param.put(gr.Module, gr);
	}
	
	void addWANInfo(rcNode ns, Result r) {
		
		if (r.param_name == null || r.param == null) return;
		for (int i = 0; i < r.param_name.length; i++) {
			ILink il = (ILink) ns.wconn.get(r.param_name[i]);
			if(il == null){
				il = iNetGeo.getLink(r.param_name[i]);
				if (il != null) {
					//ILink l1 = fixGeo.resolveILink(l);
					il.data = Double.valueOf(r.param[i]);
					il.time = r.time;
					ns.wconn.put(r.param_name[i], il);
				}
			}else{
				il.data = Double.valueOf(r.param[i]);
				il.time = r.time;
			}
		}
	}
	
	/** use this to add some link information between two rcNodes */
	public void addLinkInfo(rcNode ns, rcNode nw, Result r) {
	    Object objLink = ns.wconn.get(nw.UnitName);
	    if ( !(objLink instanceof ILink) || r.param_name == null || r.param == null)
	        return;
		ILink il = (ILink) objLink;
		// do not add peer links that connect down reflectors
		String ek = (String) ns.haux.get("ErrorKey");
		if(ek != null && ek.equals("1") && r.param_name[0].equals("Qual2h"))
			return;
		ek = (String) nw.haux.get("ErrorKey");
		if(ek != null && ek.equals("1") && r.param_name[0].equals("Qual2h"))
			return;
		// do not add links that connect farms going to be down
		ek = (String) ns.haux.get("lostConn");
		if(ek != null && ek.equals("1"))
			return;
		ek = (String) nw.haux.get("lostConn");
		if(ek != null && ek.equals("1"))
			return;

		if (il == null) {
			il = new ILink(nw.UnitName);
			il.fromLAT = failsafeParseDouble(ns.LAT, -21.22D);
			il.fromLONG = failsafeParseDouble(ns.LONG, -111.15D); 
			il.toLAT = failsafeParseDouble(nw.LAT, -21.22D);
			il.toLONG = failsafeParseDouble(nw.LONG, -111.15D);
			il.speed = 100;
			il.time = r.time;
		}
		il.time = r.time;
		if (r.param_name[0].equals("Qual2h")) { // kinda' ugly!
			il.peersQuality = r.param; // anyway, param would be garbage otherwise
			il.timePeers = r.time;
			//System.out.println("Qual2h  "+ns.UnitName+" -> "+nw.UnitName+" = "+r.param[0]); 
		} else if (r.param_name[0].equals("RTime")) {
			il.inetQuality = r.param;
			il.timeInet = r.time; //System.out.println("IPQuality "+ns.UnitName+" -> "+nw.UnitName+" = "+r.param[0]); 
		}else
			il.data = Double.valueOf(r.param[0]);
		if(r.time - il.timePeers > 2 * 60 * 1000)
			il.peersQuality = null;
		if(r.time - il.timeInet > 2 * 60 * 1000)
			il.inetQuality = null;
		ns.wconn.put(nw.UnitName, il);
	}
	
	/** get a rcNode for a farmName - if one exists */
	public rcNode getNodeByName(String farmName){
		return nodes.get(farmName);
	}
	
	/** get a rcNode for an IP Address - if one exists */
	public rcNode getNodeByIPAddress(String ipAddress){
		for(Enumeration e = nodes.elements(); e.hasMoreElements();) {
			rcNode n = (rcNode) e.nextElement();
			if(n.IPaddress.equals(ipAddress))
				return n;
		}
		return null;
	}
	
	/** get a rcNode for a FQDN hostname - if one exists */
	public rcNode getNodeByHostName(String hostName){
		String ipaddr = getHostIPaddr(hostName);
		if(ipaddr == null || ipaddr.equals("#Error#Getting#IP#"))
			return null;
		return getNodeByIPAddress(ipaddr);
	}
	
	/** get the IP address for a FQDN hostname ASAP */
	private String getHostIPaddr(String hostName){
		try{
			String a = (String) addrIPCache.get(hostName);
			if(a == null) {
				a = InetAddress.getByName(hostName).getHostAddress();
				addrIPCache.put(hostName, a);
			}
			return a;
		}catch(UnknownHostException ex){ 
			String errorIP = "#Error#Getting#IP#";
			addrIPCache.put(hostName, errorIP);
			return errorIP;
		}
	}
	
	/** convert a string to a double value; if it fails, return the failsafe value */
	private double failsafeParseDouble(String value, double failsafe){
		try {
			return Double.parseDouble(value);
		} catch ( Throwable t  ){  
			return failsafe;
		}
	}
	
	/** create a load pie from global results */
	private void makeLoadpie() {

		for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
			rcNode n = (rcNode) e.nextElement();
			if (n == null)
				continue;

			pie p1 = (pie) n.haux.get("LoadPie");
			if (p1 == null) {
				p1 = new pie(3);
				p1.cpie[0] = Color.pink;
				p1.cpie[1] = Color.blue;
				p1.cpie[2] = Color.green;
				n.haux.put("LoadPie", p1);
			}
			synchronized(p1){				
				Gresult ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load5" ));
			/*	if (ldx == null ) {					
					 ldx  = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
					 if(ldx!=null && ldx.ClusterName.indexOf("PBS")==-1 &&  ldx.ClusterName.indexOf("Condor")==-1 ) 
						 ldx=null;					 					 
				}			
		     */				
				if ((ldx == null) || (ldx.hist == null)) {
					p1.len = 0;
				} else {
					p1.len = 3;
					p1.rpie[0] = ldx.hist[4] / (double) ldx.Nodes;
					p1.rpie[1] = (ldx.hist[3] + ldx.hist[2]) / (double) ldx.Nodes;
					p1.rpie[2] = (ldx.hist[0] + ldx.hist[1]) / (double) ldx.Nodes;
				}
			}
		}
	}

	/** create a IO pie from global results */
	private void makeIOpie() {

		for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
			rcNode n = (rcNode) e.nextElement();
			if (n == null)
				continue;

			pie p1 = (pie) n.haux.get("IOPie");
			if (p1 == null) {
				p1 = new pie(2);
				p1.cpie[0] = Color.pink;
				p1.cpie[1] = Color.blue;
				n.haux.put("IOPie", p1);
			}
			synchronized(p1){
				Gresult inn = n.global_param.get("TotalIO_Rate_IN");
				Gresult outn = n.global_param.get("TotalIO_Rate_OUT");
				if ((inn == null) || (outn == null)) {
					p1.len = 0;
				} else {
					p1.len = 2;
					double sum = inn.mean + outn.mean;
					p1.rpie[0] = inn.mean / sum;
					p1.rpie[1] = outn.mean / sum;
				}
			}
		}

	}

	/** create a disk pie from global results */
	private void makeDiskpie() {

		for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
			rcNode n = (rcNode) e.nextElement();
			if (n == null)
				continue;

			pie p1 = (pie) n.haux.get("DiskPie");
			if (p1 == null) {
				p1 = new pie(2);
				p1.cpie[0] = Color.pink;
				p1.cpie[1] = Color.green;
				n.haux.put("DiskPie", p1);
			}
			synchronized(p1){
				Gresult fd = n.global_param.get("FreeDsk");
				Gresult ud = n.global_param.get("UsedDsk");
				if ((fd == null) || (ud == null)) {
					p1.len = 0;
				} else {
					p1.len = 2;
					double sum = fd.sum + ud.sum;
					p1.rpie[0] = ud.sum / sum;
					p1.rpie[1] = fd.sum / sum;
				}
			}
		}

	}
	
	/** create a cpu pie from global results */
	private void makeCPUpie() {
		for (Enumeration e = nodes.elements(); e.hasMoreElements();) {
			rcNode n = (rcNode) e.nextElement();
			if (n == null)
				continue;

			pie p1 = (pie) n.haux.get("CPUPie");
			if (p1 == null) {
				p1 = new pie(4);
				p1.cpie[0] = Color.pink;
				p1.cpie[1] = Color.blue;
				p1.cpie[2] = Color.green;
				p1.cpie[3] = Color.red;
				n.haux.put("CPUPie", p1);
			}
			synchronized(p1){
				Gresult usr = n.global_param.get("CPU_usr");
				Gresult sys = n.global_param.get("CPU_sys");
				Gresult nice = n.global_param.get("CPU_nice");
				
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
			}
		}
	}
	
}
