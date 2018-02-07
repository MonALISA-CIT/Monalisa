package lia.Monitor.JiniClient.CommonGUI.Topology;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JToolTip;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.JMultiLineToolTip;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.ForceDirectedLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayeredTreeLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.LayoutChangedListener;
import lia.Monitor.JiniClient.CommonGUI.Gmap.RadialLayoutAlgorithm;
import lia.Monitor.JiniClient.CommonGUI.Gmap.SpringLayoutAlgorithm;

public class GTopoArea  extends JPanel implements  MouseListener, 
									MouseMotionListener,
									LayoutChangedListener {
	GNetTopoPan gntPan;
	SerMonitorBase monitor;
	AliasCache aliasCache;
	Hashtable traces;
	Vector routers;
	Vector ases;
	Vector nets;
	Vector farms;
	
	int wunit = 0;
	int hunit = 0;
	rcNode pickX;	// the currently selected rcNode
	rcNode pickX2;	// the rcNode beneath mouse cursor
	boolean hotLinksShown = false;
	boolean valuesShown = false;
	double hotLinksValue = 0;
	double minPathValue = 0;
	
	int showing = GNetTopoPan.SHOW_AS;
	String showingStr = "as";
	Vector origShowingNodes = null;		// points to a vector from NetTopologyAnalyzer
	Vector crtShowingNodes = new Vector(); // current shown nodes
//	HashSet tmpShowingNodes = new HashSet(); // temp nodes, used in refreshLinks()
	Vector crtShowingLinks = new Vector(); // current shown links
//	HashSet tmpShowingLinks = new HashSet(); // temp links, used in refreshLinks()
	
	Color rtrColor = new Color(0, 170, 249);
	Color linkColor = Color.GREEN; //new Color(10, 20, 250);
	Color hotLinkColor = Color.BLUE; // draw links between selected nodes
	Color minTreeLinkColor = Color.ORANGE; // draw links starting from selected node
	Color netColor = new Color(240, 150, 0);
	Color asColor = new Color(60, 145, 0);
	
	static Font textFont = new Font("Arial", Font.PLAIN, 10);
	DecimalFormat formatter = new DecimalFormat("###,###.#");
	// layout & stuff
	GraphLayoutAlgorithm layout = null;
	GraphTopology vGraph = null;
	private Object syncGraphObj = new Object();
	String currentLayout = "none";
	
	Object syncRefresh = new Object();
	static int DO_COMPACT = 1;
	static int DO_LAYOUT = 2;
	static int DO_PAINT = 4;
	static int DO_DIJKSTRA = 8;
	static int DO_ALL = 0xff;
	
	Dijkstra dijkstra = new Dijkstra();
	boolean showDijkstra = false; // if minimum path should be shown
	boolean joinAliases = false; // if we should draw a single router for all its interfaces
	
	boolean compacted = false;	// if the graph should be compacted
	Rectangle range = new Rectangle();
	BasicStroke thickLine = new BasicStroke(2);
	
	Image imgRouter = null;
	Image imgNet = null;
	Image imgAS = null;
	
	int postedRefreshMsgs = 0;
	int lastPostedFlags = 0;
	
	public GTopoArea(GNetTopoPan gntPan){
		this.gntPan = gntPan;
		setBackground(Color.WHITE);
		addMouseListener(this);
		addMouseMotionListener(this);
		setToolTipText("My Tool tip");
		// prepare & enable the timerTask that paints the panel
		TimerTask ttask = createRefreshTask();
		BackgroundWorker.schedule(ttask, 4*1000, 10*1000);
	}
	
	public void setSerMonitor(SerMonitorBase smb){
		this.monitor = smb;
		traces = monitor.netTopology.traces;
		routers = monitor.netTopology.routers;
		ases = monitor.netTopology.ases;
		nets = monitor.netTopology.nets;
		farms = monitor.netTopology.farms;
		origShowingNodes = ases;
		aliasCache = monitor.netTopology.aliasCache;
	}

	/** Timer task that will make sure that the links are refreshed */
	private TimerTask createRefreshTask(){
		return new TimerTask() {
			public void run() {
		        Thread.currentThread().setName(" ( ML ) - Topology - GTopoArea refresh links Timer Thread");
				try{
					refreshLinks(DO_ALL);
				}catch(Throwable t){
					t.printStackTrace();
				}
			}
		};
	}
	
	private void paintFarmNode(Graphics g, rcNode n) {
		String nodeName = n.shortName;
		int x = n.x;
		int y = n.y;
		
		if(nodeName == null)
			nodeName = "NULL!!";
		FontMetrics fm = g.getFontMetrics();
		int w2 = (fm.stringWidth(nodeName) + 15) / 2;
		int h2 = (fm.getHeight() + 10) / 2;
		if ( w2 > wunit ) 
			wunit = w2;
		if ( h2 > hunit ) 
			hunit = h2;
		
		if(n.limits == null)
			n.limits = new Rectangle(x-wunit, y-hunit, wunit*2, hunit*2 );
		else
			n.limits.setBounds(x-wunit, y-hunit, wunit*2, hunit*2);
		
		Color c = null;
		if(n.errorCount>0)
			c = Color.RED;
		else{
			if(n.haux.get("ErrorKey") != null && n.haux.get("ErrorKey").equals("1") ){
//				System.out.println(n.UnitName+"-ErrorKey>"+n.haux.get("ErrorKey"));
				c = Color.PINK;
			}
			if ( n.haux.get("lostConn") != null && n.haux.get("lostConn").equals("1") ){
//				System.out.println(n.UnitName+"-lostConn>"+n.haux.get("lostConn"));
				c = Color.RED;
			}
			Color cc = Color.YELLOW;
			if(cc != null)
				c = cc;
			if(c == null)
				c = Color.PINK;
//			if(n.UnitName.equals("WWW.VRVS.ORG"))
//			System.out.println(n.UnitName+"->"+c);
		}
		g.setColor(c);
		g.fillOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
		Color invC = Color.WHITE;
		if(n.fixed || n == pickX)
			invC = new Color(255-c.getRed(), 255-c.getGreen(), 255-c.getBlue());		
		if(n.fixed)
			g.setColor(invC);
		else
			g.setColor(Color.BLACK);
		g.drawString(nodeName, n.limits.x + 7 + wunit - w2, n.limits.y + 5 + hunit - h2 + fm.getAscent());
		
		if(n == pickX){
			g.setColor(Color.RED);
		    Stroke oldStroke = ((Graphics2D)g).getStroke();
		    ((Graphics2D)g).setStroke(thickLine);
			g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
		    ((Graphics2D)g).setStroke(oldStroke);
		}else{
			g.setColor(Color.BLACK);
			g.drawOval(n.limits.x, n.limits.y, n.limits.width, n.limits.height);
		}
	}
	
	private void paintRouter(Graphics gDev, rcNode r){
	    int R = 8;
		int x = r.x;
		int y = r.y;
		if(r.limits == null){
			r.limits = new Rectangle(x-R, y-R, 2*R, 2*R);
		}else{
			r.limits.setBounds(x-R, y-R, 2*R, 2*R);
		}
	    if (imgRouter == null) {
			GraphicsConfiguration gc = getGraphicsConfiguration();
		    imgRouter = gc.createCompatibleImage((int) r.limits.getWidth()+1, (int) r.limits.getHeight()+1, Transparency.BITMASK);
		    Graphics2D g = (Graphics2D)imgRouter.getGraphics();
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, (int) r.limits.getWidth(), (int) r.limits.getHeight());
			x = R; y = R;
		    
			g.setColor(Color.WHITE);
			g.drawOval(x-R+1, y-R+1, 2*R-2, 2*R-2);
			g.drawOval(x-R+2, y-R+2, 2*R-4, 2*R-4);
			g.setColor(Color.BLACK);
			g.drawOval(x-R, y-R, 2*R, 2*R);

			R -= 2;
			g.setColor(rtrColor);
			g.fillOval(x-R, y-R, 2*R, 2*R);
			g.setColor(Color.WHITE);
			g.drawLine(x-R+3, y-R+3, x+R-3, y+R-3);
			g.drawLine(x+R-3, y-R+3, x-R+3, y+R-3);
		    R += 2;
		    x = r.x; y = r.y;
	    }
	    gDev.drawImage(imgRouter, r.x-R, r.y-R, null);
	    if(r.errorCount < 0 || r == pickX){
	        gDev.setColor(r == pickX ? Color.RED : Color.BLACK);
	        Stroke oldStroke = ((Graphics2D)gDev).getStroke();
	        ((Graphics2D)gDev).setStroke(thickLine);
	        gDev.drawOval(x-R-1, y-R-1, 2*R+2, 2*R+2);
	        ((Graphics2D)gDev).setStroke(oldStroke);
	    }
//	    if(r == pickX){
//	        int RR = 100;
//	        gDev.drawOval(r.x-RR, r.y-RR, 2*RR, 2*RR);
//	    }
	}
	
	private void paintNet(Graphics g, rcNode n){
		int R = 8;
		int x = n.x;
		int y = n.y;
		if(n.limits == null){
			n.limits = new Rectangle(x-R, y-R, 2*R, 2*R);
		}else{
			n.limits.setBounds(x-R, y-R, 2*R, 2*R);
		}
	    if (imgNet == null) {
			GraphicsConfiguration gc = getGraphicsConfiguration();
		    imgNet = gc.createCompatibleImage((int) n.limits.getWidth()+1, (int) n.limits.getHeight()+1, Transparency.BITMASK);
		    Graphics2D gImg = (Graphics2D)imgNet.getGraphics();
            gImg.setColor(new Color(0, 0, 0, 0));
            gImg.fillRect(0, 0, (int) n.limits.getWidth(), (int) n.limits.getHeight());
			x = R; y = R;
			gImg.setColor(Color.BLACK);
			gImg.drawOval(x-R, y-R, 2*R, 2*R);
			R -= 2;
			gImg.setColor(netColor);
			gImg.fillOval(x-R, y-R, 2*R, 2*R);
			gImg.setColor(Color.WHITE);
			gImg.drawLine(x-3, y-3, x+3, y-3);
			gImg.drawLine(x-3, y+3, x+3, y+3);
			R += 2;
		    x = n.x; y = n.y;
	    }
	    g.drawImage(imgNet, n.x-R, n.y-R, null);
	    if(n.errorCount < 0 || n == pickX){
	        g.setColor(n == pickX ? Color.RED : Color.BLACK);
	        Stroke oldStroke = ((Graphics2D)g).getStroke();
	        ((Graphics2D)g).setStroke(thickLine);
	        g.drawOval(x-R-1, y-R-1, 2*R+2, 2*R+2);
	        ((Graphics2D)g).setStroke(oldStroke);
	    }
	}
	
	private void paintAS(Graphics g, rcNode a){
		int R = 8;
		int x = a.x;
		int y = a.y;
		if(a.limits == null){
			a.limits = new Rectangle(x-R, y-R, 2*R, 2*R);
		}else{
			a.limits.setBounds(x-R, y-R, 2*R, 2*R);
		}
	    if (imgAS == null) {
			GraphicsConfiguration gc = getGraphicsConfiguration();
		    imgAS = gc.createCompatibleImage((int) a.limits.getWidth()+1, (int) a.limits.getHeight()+1, Transparency.BITMASK);
		    Graphics2D gImg = (Graphics2D)imgAS.getGraphics();
            gImg.setColor(new Color(0, 0, 0, 0));
            gImg.fillRect(0, 0, (int) a.limits.getWidth(), (int) a.limits.getHeight());
			x = R; y = R;
			gImg.setColor(Color.BLACK);
			gImg.drawOval(x-R, y-R, 2*R, 2*R);
			R -= 2;
			gImg.setColor(asColor);
			gImg.fillOval(x-R, y-R, 2*R, 2*R);
			gImg.setColor(Color.WHITE);
			gImg.drawOval(x-3, y-3, 6, 6);
			gImg.drawLine(x, y, x, y+1);
			R += 2;
		    x = a.x; y = a.y;
	    }
	    g.drawImage(imgAS, a.x-R, a.y-R, null);
	    if(a.errorCount < 0 || a == pickX){
	        g.setColor(a == pickX ? Color.RED : Color.BLACK);
	        Stroke oldStroke = ((Graphics2D)g).getStroke();
	        ((Graphics2D)g).setStroke(thickLine);
	        g.drawOval(x-R-1, y-R-1, 2*R+2, 2*R+2);
	        ((Graphics2D)g).setStroke(oldStroke);
	    }
	}
	
	private void paintDirectLink(Graphics gg, rcNode n, rcNode n1, Color color){
		gg.setColor(color);
		gg.drawLine(n.x, n.y, n1.x, n1.y);
	}
	
	private void paintLink(Graphics gg, rcNode n , rcNode n1, String lbl, Color color) {
		
		int dd = 6 ;
		
		gg.setColor(color);
		
		int dx = n1.x - n.x; 
		int dy = n1.y - n.y;
		float l = (float) Math.sqrt ( dx*dx+dy*dy );
			//Math.abs(dx) + Math.abs(dy); 
		float dir_x  = dx / l ;
		float dir_y  = dy / l;
		int dd1 = dd ;
		
		int x1p = n.x - (int ) ( dd1 * dir_y ) ;
		int x2p = n1.x - (int ) ( dd1 * dir_y );
		int y1p = n.y + (int)  ( dd1 * dir_x );
		int y2p = n1.y + (int ) ( dd1 *dir_x );
		
		gg.drawLine( x1p,y1p,x2p,y2p ) ;
		
		int xv = (x1p+x2p )/2;
		int yv = (y1p +y2p )/2;
		
		float aa= (float)( dd )/ (float) 3.0;
		
		int[] axv={(int) (xv -aa*dir_x + 2*aa*dir_y), (int) (xv-aa*dir_x - 2*aa*dir_y), (int)(xv+2*aa*dir_x) ,(int)(xv-aa*dir_x+ 2*aa*dir_y) };
		
		int[] ayv={(int)(yv -aa*dir_y - 2*aa*dir_x),(int)(yv-aa*dir_y+2*aa*dir_x), (int) (yv +2*aa * dir_y ), (int) (yv -aa*dir_y - 2*aa*dir_x) };
		
		gg.fillPolygon(axv, ayv, 4 ) ;
		
		
		//int ddl = 6;
		// String lbl = "" + (int) perf ;
		if(valuesShown){
			FontMetrics fm = gg.getFontMetrics();
			int wl = fm.stringWidth(lbl) + 1;
			int hl = fm.getHeight() + 1;
			
			int off = 2;
			
			int xl = xv;
			int yl = yv ;
			
			if ( dir_x >= 0 && dir_y < 0 )  { xl = xl + off ; yl = yl +hl ; }
			if ( dir_x <= 0 && dir_y > 0 ) { xl = xl - wl -off ; yl = yl- off ;}
			
			if ( dir_x > 0 && dir_y >= 0 ) { xl = xl -wl -off ; yl = yl + hl ; }
			if ( dir_x < 0 && dir_y < 0 )   { xl = xl +off  ; yl = yl - off ; }
			
			gg.drawString(lbl, xl, yl);
		}
	}
	
//	boolean isLinkDefined(rcNode n1, rcNode n2){
//		for(int i=0; i<showingLinks.size(); i++){
//			EntityLink link = (EntityLink) showingLinks.get(i);
//			if(link.n1 == n1 && link.n2 == n2)
//				return true;
//		}
//		return false;
//	}
//
//	EntityLink getDefinedLink(rcNode n1, rcNode n2){
//		for(int i=0; i<showingLinks.size(); i++){
//			EntityLink link = (EntityLink) showingLinks.get(i);
//			if(link.n1 == n1 && link.n2 == n2)
//				return link;
//		}
//		return null;
//	}

	void updateFilterCombobox(){
		Vector names = new Vector();
		for(int i=0; i<nets.size(); i++){
			rcNode n = (rcNode) nets.get(i);
			if(! names.contains(n.UnitName)){
				names.add(n.UnitName);
			}
		}
		for(int i=0; i<ases.size(); i++){
			rcNode n = (rcNode) ases.get(i);
			if(! names.contains("AS"+n.UnitName)){
				names.add("AS"+n.UnitName);
			}
		}
		Collections.sort(names);
		names.add(0, "All");
		for(int i=0; i<names.size(); i++){
			Object nn = names.get(i);
			int lenCb = gntPan.cbFilter.getItemCount(); 
			if(lenCb > i){
				Object n = gntPan.cbFilter.getItemAt(i);
				if(! n.equals(nn)){
					gntPan.cbFilter.removeItemAt(i);
					gntPan.cbFilter.insertItemAt(nn, i);
				}
			}else
				gntPan.cbFilter.insertItemAt(nn, lenCb);	
		}
		int len;
		while((len = gntPan.cbFilter.getItemCount()) > names.size())
			gntPan.cbFilter.removeItemAt(len-1);
	}
	
	boolean notFilteredBy(String filterName, rcNode node){
		if(filterName.startsWith("AS") && Character.isDigit(filterName.charAt(2))){
			return filterName.substring(2).equals(node.haux.get("as"));
		}
		return filterName.equals(node.haux.get("net"));
	}
	
	void refreshLinks(int how){
		//System.out.println("refresh started");
		//long t1 = NTPDate.currentTimeMillis();
		//System.out.println("refreshLinks");
		HashSet tmpShowingLinks = new HashSet();
		HashSet tmpShowingNodes = new HashSet();
		boolean hotLinks = false;
		hotLinksValue = 0; 
		boolean filtered = false;
		String filterName = null;
		rcNode tmpNode = null;
		
		updateFilterCombobox();
		filtered = gntPan.cbFilter.getSelectedIndex() != 0;
		if(filtered)
			filterName = (String) gntPan.cbFilter.getSelectedItem();

		//for(Iterator nsit=traces.keySet().iterator(); nsit.hasNext(); ){
		for(Enumeration nsen = traces.keys(); nsen.hasMoreElements(); ){
			//String ipns = (String) nsit.next();
		    String ipns =(String) nsen.nextElement();
			// ipns = source IP
			rcNode srcFarm = NetTopologyAnalyzer.findRCnodeByIP(farms, ipns);
			if(srcFarm == null || srcFarm.selected){
				//System.out.println("SKIP1 "+srcFarm.UnitName);
				continue;	// don't bother to visit all peers
			}
			if(showingStr.equals("router") && joinAliases)
				tmpNode = aliasCache.getLeaderNode(srcFarm);
			else
				tmpNode = srcFarm;
			if(! filtered || notFilteredBy(filterName, tmpNode))
				tmpShowingNodes.add(tmpNode);
			Hashtable tracePeers = (Hashtable) traces.get(ipns);

			//for(Iterator nwit=tracePeers.keySet().iterator(); nwit.hasNext(); ){
			for(Enumeration nwen = tracePeers.keys(); nwen.hasMoreElements(); ){
				//String ipnw = (String) nwit.next();
			    String ipnw = (String) nwen.nextElement();
				// ipnw = destination IP
				rcNode destFarm = NetTopologyAnalyzer.findRCnodeByIP(farms, ipnw);
				if(destFarm == null || destFarm.selected){
					continue;	// don't bother to visit all intermediate nodes
				}
				
				if(showingStr.equals("router") && joinAliases)
					tmpNode = aliasCache.getLeaderNode(destFarm);
				else
					tmpNode = destFarm;
				if(! filtered || notFilteredBy(filterName, tmpNode))
					tmpShowingNodes.add(tmpNode);

				Hashtable traceValues = (Hashtable) tracePeers.get(ipnw);
				
				// get the current entity trace
				Vector crtTrace = (Vector) traceValues.get(showingStr+"-trace");
//				System.out.println("CRTTRACE = "+crtTrace+");
				if(crtTrace == null){
					//System.out.println("GTopoArea: TRACE NULL for "+showingStr+" :: "+ipns+" -> "+ipnw);
					continue;
				}
				boolean hotTrace = (pickX != null && pickX2 != null && 
						((pickX.IPaddress.equals(ipns) && (pickX2.IPaddress.equals(ipnw)))));
				hotLinks |= hotTrace;
				for(int i=0; i<crtTrace.size(); i++){
					EntityLink pathLink = (EntityLink) crtTrace.get(i);
					rcNode n1 = pathLink.n1;
					rcNode n2 = pathLink.n2;
					
					if(showingStr.equals("router") && joinAliases){
						n1 = aliasCache.getLeaderNode(n1);
						n2 = aliasCache.getLeaderNode(n2);
						if(n1 != pathLink.n1 || n2 != pathLink.n2){
							// search if we don't have already added a link between new n1 and n2
							boolean found = false;
							for(Iterator lit = tmpShowingLinks.iterator(); lit.hasNext(); ){
								EntityLink link = (EntityLink) lit.next();
								if(link.n1 == n1 && link.n2 == n2){
									found = true;
									pathLink = link;
									break;
								}
							}
							if(! found){
								pathLink = new EntityLink(pathLink);
								pathLink.n1 = n1;
								pathLink.n2 = n2;
							}
						}
					}
					boolean n1In = false;
					boolean n2In = false;
					if(! filtered || notFilteredBy(filterName, n1)){
						n1In = true;
					}
					if(! filtered || notFilteredBy(filterName, n2)){
						n2In = true;
					}

					if(n1In || n2In){
						n1.errorCount = n1In ? 0 : -1;
						n2.errorCount = n2In ? 0 : -1;
						tmpShowingNodes.add(n1);
						tmpShowingNodes.add(n2);
						
//							if(srcFarm.UnitName.equals("test-infn") &&
//									destFarm.UnitName.equals("test-wn1-ro")){
//								System.out.println(pathLink);
//							}							
						// if we added at least one end of the link...
						if(tmpShowingLinks.add(pathLink)){
							// the link was added now
							pathLink.color = linkColor;
							pathLink.hotLink = false;
						}
						if(hotTrace){
							pathLink.color = hotLinkColor;
							pathLink.hotLink = true;
							hotLinksValue += pathLink.delay;
						}
					}
				}
			}
		}
		hotLinksShown = hotLinks;
		
		// compact the graph if necessary, using tmpShowing{Nodes,Links}
		if((how & DO_COMPACT) != 0)
			compactGraphIfNeeded(tmpShowingNodes, tmpShowingLinks);
		
		setLinksColor(tmpShowingLinks);
		
		if(showDijkstra && hotLinksShown //pickX != null && pickX.shortName != null 
				&& ((how & DO_DIJKSTRA) != 0)){
			//Vector treeLinks = dijkstra.computeTree(pickX, tmpShowingLinks);
			minPathValue = 0;
			Vector treeLinks = dijkstra.computePath(pickX, pickX2, tmpShowingLinks);
			for(Iterator lit = treeLinks.iterator(); lit.hasNext(); ){
				EntityLink link = (EntityLink) lit.next();
				link.inDijkstra = true;
				minPathValue += link.delay;
			}
		}
		
		synchronized(syncRefresh){
			crtShowingNodes.clear();
			crtShowingNodes.addAll(tmpShowingNodes);
			
			crtShowingLinks.clear();
			crtShowingLinks.addAll(tmpShowingLinks);
		}			
		// set the layout
		if((how & DO_LAYOUT) != 0 && (currentLayout.equals("Elastic") || currentLayout.equals("Spring")))
			setLayout(currentLayout);
		if((how & DO_PAINT) != 0)
			repaint();
	
		synchronized(syncRefresh){
			if(postedRefreshMsgs > 0)
				postedRefreshMsgs--;
		}
		//long t2 = NTPDate.currentTimeMillis();
		//System.out.println("topoAreaRefresh in "+(t2-t1)+" ms");
//		System.out.println("refresh finished");
	}

	/** 
	 * Make sure that the timerTask runs as soon as possible once again
	 * the refreshLinks method. It will not add this task multiple times
	 * into the timerTask queue.
	 */
	public void postRefreshLinksMsg(int flags){
		synchronized (syncRefresh) {
			if((lastPostedFlags != flags) || (postedRefreshMsgs < 2)){
				synchronized (syncRefresh) {
					lastPostedFlags = flags;
					postedRefreshMsgs++;
				}
				TimerTask ttask = createRefreshTask();
				// execute the task as soon as possible
				BackgroundWorker.schedule(ttask, 0);
			}
		}
	}
	
	/** set the color of the drawn links based on their value */
	private void setLinksColor(HashSet tmpShowingLinks){
		// compute min/max values of the links for the colorScale
		double minDelay = Double.MAX_VALUE;
		double maxDelay = Double.MIN_VALUE;
		for(Iterator elit = tmpShowingLinks.iterator(); elit.hasNext(); ){
			EntityLink link = (EntityLink) elit.next();
			minDelay = Math.min(minDelay, link.delay);
			maxDelay = Math.max(maxDelay, link.delay);
			link.inDijkstra = false;
		}
		if(minDelay > maxDelay){
			minDelay = maxDelay = 0;
			gntPan.colorScale.setColors(Color.GREEN, Color.GREEN);
		}else
			gntPan.colorScale.setColors(Color.GREEN, Color.RED);
		gntPan.colorScale.setValues(minDelay, maxDelay);
		for(Iterator elit = tmpShowingLinks.iterator(); elit.hasNext(); ){
			EntityLink link = (EntityLink) elit.next();
			if(! link.hotLink)
				link.color = gntPan.colorScale.getColor(link.delay);
		}				
	}
	
	private void paintAllLinks(Graphics g){
		for(int i=0; i<crtShowingLinks.size(); i++){
			EntityLink link = (EntityLink) crtShowingLinks.get(i);
			paintLink(g, link.n1, link.n2, formatter.format(link.delay), link.color);
			if(link.inDijkstra){
				paintDirectLink(g, link.n1, link.n2, minTreeLinkColor);
			}
		}
	}
	
	private void paintAllNodes(Graphics g){
		// first paint the smaller entities
		for(int i=0; i<crtShowingNodes.size(); i++){
			rcNode n = (rcNode) crtShowingNodes.get(i);
			if(n.shortName != null){
				// it's a farm
				paintFarmNode(g, n);
			}else
				switch(showing){
					case GNetTopoPan.SHOW_ROUTERS:
						paintRouter(g, n); break;
					case GNetTopoPan.SHOW_NETS:
						paintNet(g, n); break;
					case GNetTopoPan.SHOW_AS:
						paintAS(g, n); break;
				}
		}
	}
	
	public void paintComponent(Graphics g){
		super.paintComponent(g);
		Dimension d = getSize();
		//g.setColor(Color.WHITE);
		//g.fillRect(0, 0, d.width, d.height);
		g.setFont(textFont);
		range.setBounds(0, 0, d.width, d.height);
		range.grow(-hunit, -wunit);
		//long t1 = NTPDate.currentTimeMillis();
		synchronized (syncRefresh) {
			paintAllLinks(g);
			//long t2 = NTPDate.currentTimeMillis();
			paintAllNodes(g);
		}
		//long t3 = NTPDate.currentTimeMillis();
//		System.out.println("painted links in "+(t2-t1)+"ms; nodes in "+(t3-t2)+"ms");
	}
	
	public void mouseClicked(MouseEvent e) {
		if(pickX != null && gntPan.cbLayout.getSelectedIndex() > 0){
			setLayout((String) gntPan.cbLayout.getSelectedItem());
		}
	}
	
	public void mouseEntered(MouseEvent e) {
		// empty
	}
	
	public void mouseExited(MouseEvent e) {
		// empty
	}
	
	public void mousePressed(MouseEvent e) {
		//addMouseMotionListener(this);
		int x = e.getX();
		int y = e.getY();
		
		pickX = null;
		rcNode pick = null;
		// first check if user clicked a farm node
		for(int i=0; i<farms.size(); i++){
			rcNode n = (rcNode) farms.get(i);
			if((n.limits != null) && (n.limits.contains(x, y))){
				pick = n;
				pick.fixed = true;
				break;
			}			
		}
		int but = e.getButton();
		if (but != MouseEvent.BUTTON1) {
			if(pick == null){
				// if not, test the currently selected entity
				synchronized (syncRefresh) {
					for(int i=0; i<crtShowingNodes.size(); i++){
						rcNode n = (rcNode) crtShowingNodes.get(i);
						if((n.limits != null) && (n.limits.contains(x, y))){
							pick = n;
							pick.fixed = true;
							break;
						}
					}
				}
			}
			pickX = pick;
			//TODO: rethink this
			//System.out.println("doing refreshLinks");
			postRefreshLinksMsg(DO_COMPACT | DO_DIJKSTRA | DO_PAINT);
			//System.out.println("edn refreshLinks");
			//repaint();
		}else{
			// button 1
			if(pick != null){
			    pick = gntPan.monitor.getTracepathNodeByIP(pick.IPaddress);
//				for(Enumeration enf = gntPan.monitor.netTopology.nodes.elements(); enf.hasMoreElements(); ){
//					rcNode aNode = (rcNode) enf.nextElement();
//					if(aNode.IPaddress.equals(pick.IPaddress)){
//						pick = aNode;
//						break;
//					}
//				}
				if(pick.client != null)
				    pick.client.setVisible(! pick.client.isVisible());
			}
		}
		e.consume();
	}
	
	public void mouseReleased(MouseEvent e) {
		//removeMouseMotionListener(this);
		if(pickX != null){
			pickX.fixed = false;
		}
		e.consume();	
	}
	
	public void mouseDragged(MouseEvent e) {
		if(pickX != null && e.getButton() != MouseEvent.BUTTON1){
			int minx = 1 + pickX.limits.width / 2;
			int miny = 1 + pickX.limits.height / 2;
			int maxx = getWidth() - minx;
			int maxy = getHeight() - miny;
			int x = e.getX();
			int y = e.getY();
			
			if(x < minx) x = minx;
			if(y < miny) y = miny;
			if(x > maxx) x = maxx;
			if(y > maxy) y = maxy;
			
			pickX.x = x;
			pickX.y = y;
			
			if(vGraph != null){
				GraphNode gn = vGraph.getGN(pickX);
				if(gn != null){
					gn.pos.setLocation(x, y);
					// notify the thread that the position has changed
					if(layout != null && layout instanceof SpringLayoutAlgorithm){
					    ((SpringLayoutAlgorithm)layout).notifyRunnerThread();
					}
				}
			}
			repaint();
		}
		e.consume();	
	}
	
	public void mouseMoved(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		String text = getTextAt(x, y, "name");
		if(text == null)
			gntPan.lblCrtOn.setText("");
		else
			gntPan.lblCrtOn.setText(text);
		if(pickX != null && pickX2 != null && pickX != pickX2
				&& farms.contains(pickX) && farms.contains(pickX2)){
			if(! hotLinksShown){
				postRefreshLinksMsg(DO_COMPACT | DO_DIJKSTRA | DO_PAINT);
//				System.out.println(pickX.UnitName+" -> "+pickX2.UnitName);
			}
		}else if(hotLinksShown){
//			System.out.println("exit hot area");
			postRefreshLinksMsg(DO_COMPACT | DO_DIJKSTRA | DO_PAINT);
		}	
	}
	
	public void setShow(int toShow){
		showing = toShow;
		switch(toShow){
			case GNetTopoPan.SHOW_ROUTERS:
				showingStr = "router";
				origShowingNodes = routers;
				break;
			case GNetTopoPan.SHOW_NETS:
				showingStr = "net";
				origShowingNodes = nets;
				break;
			case GNetTopoPan.SHOW_AS:
				showingStr = "as";
				origShowingNodes = ases;
				break;
		}
		postRefreshLinksMsg(DO_ALL);
	}
	
	public void setLayout(String type){
		if(layout != null && !type.equals(currentLayout))
			layout.finish();
//		System.out.println("########### new="+type+" crt="+currentLayout);
		if(type.equals("Elastic")){
			//System.out.println("########### new="+type+" crt="+currentLayout);
			if(currentLayout.equals("Elastic")){
				synchronized(syncGraphObj){
					//vGraph = GraphTopology.constructGraphFromTopology(traces, showingStr);
					vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
					((ForceDirectedLayoutAlgorithm)layout).updateGT(vGraph);
				}
			}else{
				synchronized(syncGraphObj){
					unfixNodes();
					//vGraph = GraphTopology.constructGraphFromTopology(traces, showingStr);
					vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
					layout = new ForceDirectedLayoutAlgorithm(vGraph, this);
					((ForceDirectedLayoutAlgorithm)layout).setLinksSizeRange(30, 100);
					((ForceDirectedLayoutAlgorithm)layout).setRespF(gntPan.respfSlider.getValue());
					((ForceDirectedLayoutAlgorithm)layout).setStiffness(((double)gntPan.stiffSlider.getValue()) / 100.0);
					layout.layOut();
				}
			}
			currentLayout = type;
		}else if(type.equals("Radial")){
			synchronized(syncGraphObj){
				unfixNodes();
				vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
				if(pickX == null || vGraph.nodesMap.get(pickX) == null)
				    pickX = vGraph.findARoot();
				vGraph.pruneToTree(pickX);
				layout = new RadialLayoutAlgorithm(vGraph, pickX);
				computeNewLayout();
			}
		}else if(type.equals("Layered")){
			synchronized(syncGraphObj){
				unfixNodes();
				vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
				if(pickX == null || vGraph.nodesMap.get(pickX) == null)
				    pickX = vGraph.findARoot();
				vGraph.pruneToTree(pickX);
				layout = new LayeredTreeLayoutAlgorithm(vGraph, pickX);
				computeNewLayout();
			}
		}else if(type.equals("Spring")){
			//System.out.println("########### new="+type+" crt="+currentLayout);
			if(currentLayout.equals("Spring")){
				synchronized(syncGraphObj){
					//vGraph = GraphTopology.constructGraphFromTopology(traces, showingStr);
					vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
					((SpringLayoutAlgorithm)layout).updateGT(vGraph);
				}
			}else{
				synchronized(syncGraphObj){
					unfixNodes();
					//vGraph = GraphTopology.constructGraphFromTopology(traces, showingStr);
					vGraph = GraphTopology.constructGraphFromEntityLinks(crtShowingLinks);
					layout = new SpringLayoutAlgorithm(vGraph, this);
					((SpringLayoutAlgorithm)layout).setRespRange(gntPan.respfSlider.getValue());
					((SpringLayoutAlgorithm)layout).setStiffness(gntPan.stiffSlider.getValue());
					layout.layOut();
				}
			}
			currentLayout = type;
		}
		currentLayout = type;
	}

	public void computeNewLayout(){
		//synchronized(syncRescale){
		range.setBounds(0, 0, getWidth(), getHeight());
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
		currentLayout = "none";
		//rescaleIfNeeded();
		//}
		repaint();
	}

	void unfixNodes() {
		for(int i=0; i<crtShowingNodes.size(); i++){
			rcNode n = (rcNode) crtShowingNodes.get(i);
			n.fixed = false;
		}
	}
	
	public int setElasticLayout(){
	    int totalMovement = 0;
		for(Iterator it=vGraph.gnodes.iterator(); it.hasNext();){
			GraphNode gn = (GraphNode) it.next();
			if(gn == null || gn.rcnode == null || gn.rcnode.limits == null)
			    continue;
			int nx = gn.rcnode.x;
			int ny = gn.rcnode.y;
			gn.rcnode.x = (int) Math.round(gn.pos.x);
			gn.rcnode.y = (int) Math.round(gn.pos.y);
			range.setBounds(0, 0, getWidth(), getHeight());
			range.grow(-gn.rcnode.limits.width/2-1, -gn.rcnode.limits.height/2-1);
			if(gn.rcnode.x < range.x)
			    gn.pos.x = gn.rcnode.x = range.x;
			if(gn.rcnode.y < range.y)
			    gn.pos.y = gn.rcnode.y = range.y;
			if(gn.rcnode.x > range.getMaxX())
			    gn.pos.x = gn.rcnode.x = (int) range.getMaxX();
			if(gn.rcnode.y > range.getMaxY())
			    gn.pos.y = gn.rcnode.y = (int) range.getMaxY();
			totalMovement += Math.abs(gn.rcnode.x - nx) + Math.abs(gn.rcnode.y - ny);
		}
		repaint();
		return totalMovement;
	}
	
	
	public double getLinkValue(rcNode n1, rcNode n2) {
		return vGraph.getLinkValue(n1, n2);
	}
	
	public JToolTip createToolTip()
	{
		return new JMultiLineToolTip();
	}
	
	public void setCompact(boolean compact){
		this.compacted = compact;
		postRefreshLinksMsg(DO_ALL);
	}
	
	private void compactGraphIfNeeded(HashSet tmpShowingNodes, HashSet tmpShowingLinks){
		if(! compacted)
			return;
		//System.out.println("Compacting ...");
		Vector myLinks = new Vector(tmpShowingLinks);
		boolean changeMade = true;
		while(changeMade){
			changeMade = false;
//			System.out.println("compacting...");
			for(int i=0; i<myLinks.size(); i++){
				EntityLink li = (EntityLink) myLinks.get(i);
				int c1 = 1; // nr de linkuri legate de li.n1
				int c2 = 1; // --//-- li.n2
				int k = -1;
				EntityLink nextLI = null;
				for(int j=0; j<myLinks.size(); j++){
					if(i == j)
						continue;
					EntityLink lj = (EntityLink) myLinks.get(j);
					if(li.n1 == lj.n2 || li.n1 == lj.n1) // lj -> li
						c1++;
					if(li.n2 == lj.n1 || li.n2 == lj.n2){ // li -> lj
						c2++;
						if(li.n2 == lj.n1){
							nextLI = lj;
							k = j;
						}
					}
				}
				if(c1 == 2 && c2 == 2 && nextLI != null){
					// remove li and li.n2 from the graph
					//System.out.println("removing.."+li.n1.UnitName+" -> "+li.n2.UnitName);
					if(li.n2.shortName != null)
						continue; // n2 is a farm; cannot remove this link
					tmpShowingNodes.remove(li.n2);
					
					// prepare a replacement for li->nextLI
					EntityLink liReplace = new EntityLink(nextLI);
					if(nextLI.n1 == li.n2)
						liReplace.n1 = li.n1;
					else
						liReplace.n2 = li.n1;
					liReplace.delay = li.delay + nextLI.delay;
					liReplace.color = li.color;
					liReplace.hotLink = li.hotLink;
					myLinks.set(i, liReplace);
					myLinks.remove(k);
					if(k<i){
						changeMade = true;
						//System.out.println("must restart i="+i+" k="+k);
					}
					//break;
				}
			}
		}
		tmpShowingLinks.clear();
		tmpShowingLinks.addAll(myLinks);
	}
	
	public String getToolTipText(MouseEvent mevent){
		int x = mevent.getX();
		int y = mevent.getY();
		if(hotLinksShown){
			return getTextAt(x, y, "hotLinkData");
		}
		return getTextAt(x, y, "descr");
	}
	
	private String getTextAtHelper(Vector from, int x, int y, String what){
		pickX2 = null;
		for(int i=0; i<from.size(); i++){
			rcNode n = (rcNode) from.get(i);
			if(n.selected)
				continue;
			if((n.limits != null) && (n.limits.contains(x, y))){
				pickX2 = n;
				if(what.equals("descr")){
					String descr = (String) n.haux.get("descr");
					String net = (String) n.haux.get("net");
					String as = (String) n.haux.get("as");
					String city = (String) n.haux.get("city");
					String country = (String) n.haux.get("country");
					StringBuilder t = new StringBuilder();
					if(descr != null){
						if(t.length() > 0) t.append("\n");
						t.append("Descr: "+descr);
					}
					if(net != null){
						if(t.length() > 0) t.append("\n");
						t.append("NET: "+net);
					}
					if(as != null){
						if(t.length() > 0) t.append("\n");
						t.append("AS: "+as);
					}
					if(city != null){
						if(t.length() > 0) t.append("\n");
						t.append("City: "+city);
					}
					if(country != null){
						if(t.length() > 0) t.append("\n");
						t.append("Country: "+country);
					}
					if(t.length() > 0)
						return t.toString();
					else
						return null;
				}else if(what.equals("name")){
					if(n.IPaddress.length() > 2)
						return n.UnitName+" @ "+n.IPaddress;
					else
						return n.UnitName;
				}else if(what.equals("hotLinkData")){
					String tip = "Total Delay: "+formatter.format(hotLinksValue);
					if(showDijkstra)
						tip += "\nMin Delay: "+formatter.format(minPathValue);
					return tip;
				}
			}
		}
		return null;		
	}
	
	private String getTextAt(int x, int y, String what){
		//String t = getTextAtHelper(farms, x, y, what);
		//if(t == null)
		synchronized (syncRefresh) {
			return getTextAtHelper(crtShowingNodes, x, y, what);
		}
		//return t;
	}
}
