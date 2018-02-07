package lia.Monitor.JiniClient.CommonGUI.Mmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.Map;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.GlobeTextureLoader;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.pie;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.ILink;
import net.jini.core.lookup.ServiceID;

public class MapCanvasBase extends JPanel implements graphical{

    public int LINK_TYPE_UNDIRECTED = 1;
    public int LINK_TYPE_DIRECTED = 2;
    public int LINK_TYPE_SHOW_VALUE = 4;
    
//	private BufferedImage srcBI;
//	private BufferedImage srcNightBI;
	private BufferedImage destBI;

    protected volatile Map<ServiceID, rcNode> nodes;
    protected volatile Vector<rcNode> vnodes;
	protected SerMonitorBase monitor;
	
	protected NumberFormat nf;

    JScrollBar sbH;		// scroll bars
    JScrollBar sbV;

    ImgPanel imagPan;	// the image is displayed here to avoid flicker

	// zoom issues
	private Rectangle2D.Double viewArea = new Rectangle2D.Double();
	private Rectangle iniSelArea = new Rectangle();
	private Rectangle crtSelArea = new Rectangle();
	private Point iniMousePos = new Point();
	private Point crtMousePos = new Point();

	// zoom constants
	private int ZOOM_STABLE = 1;
	private int ZOOM_DRAWING_RECTANGLE = 2;
	private int ZOOM_MOVING = 4;
	private int zoomStatus = ZOOM_STABLE;
	
    private int oldHVal, oldVVal;	// scroll bar values before zooming
	private boolean adjustingScrollBars = false;
	
	//protected Timer timer;

public MapCanvasBase() {
//  	srcBI = GlobeTextureLoader.getBufferedImage();
//  	srcNightBI = GlobeTextureLoader.getNightBufferedImage();
//	destBI = new BufferedImage(srcBI.getWidth(), srcBI.getHeight(), srcBI.getType());
	destBI = GlobeTextureLoader.getFinalBufferedImage(); 
	
    nf = NumberFormat.getInstance();
    nf.setMaximumFractionDigits(1);
    nf.setMinimumFractionDigits(1);

	imagPan = new ImgPanel();
	imagPan.setToolTipText("My Tool tip");
	
    imagPan.addMouseListener(imagPan);
	imagPan.addMouseMotionListener(imagPan);
	imagPan.addMouseWheelListener(imagPan);
	imagPan.addComponentListener(imagPan);

	sbH = new JScrollBar(JScrollBar.HORIZONTAL);		// add scrollbars
	sbV = new JScrollBar(JScrollBar.VERTICAL);
	sbH.addAdjustmentListener(imagPan);
	sbV.addAdjustmentListener(imagPan);

	setLayout(new BorderLayout());
	add(sbH, BorderLayout.SOUTH);
	add(sbV, BorderLayout.EAST);
	add(imagPan, BorderLayout.CENTER);

	viewArea.setFrame(0, 0, destBI.getWidth(), destBI.getHeight());
	imagPan.adjustViewArea();
	
	// start a timer to update the shadow every minute
	TimerTask ttask2 = new TimerTask() {
		public void run() {
			repaint();
		}
	};
	BackgroundWorker.schedule(ttask2, 4*1000, 4*1000);
}


public void updateNode(rcNode node) {
	// empty
}

public void gupdate() {
	// empty
}

public void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
	this.nodes = nodes;
	this.vnodes = vnodes;
}

public void setSerMonitor(SerMonitorBase ms) {
	this.monitor = ms;
}

public void setMaxFlowData(rcNode n, Vector v) {
	// empty
}

public void new_global_param(String name) {
	// empty
}

/**
 * converts a longiture coordinate to a X position on the source image
 * @param klong in radians
 */
protected double rad2imgX(double klong){
	return (klong - (-Math.PI)) / Math.PI / 2 * destBI.getWidth();
}

/**
 * converts a latitude coordinate to a Y position on the source image
 * @param klat in radians
 */
protected double rad2imgY(double klat){
	return (Math.PI/2 - klat) / Math.PI * destBI.getHeight();
}

/**
 * converts a X on the source image to a X on the imagPan panel
 * @param ix x coordinate
 */
protected double img2panX(double ix){
	double fx = imagPan.getWidth() / viewArea.width;
	return (ix - viewArea.x) * fx;
}

/**
 * converts a Y on the source image to a Y on the imagPan panel
 * @param iy y coordinate
 */
protected double img2panY(double iy){
	double fy = imagPan.getHeight() / viewArea.height;
	return (iy - viewArea.y) * fy;
}

/**
 * convert a longitude coordinate to a x position on the WMap panel
 * @param longitude - the longitude
 * @return - the x position
 */
public int long2panX(String longitude){
    double xlong = failsafeParseDouble(longitude, -111.15);
    xlong = xlong * Math.PI /180.0;

	return (int) img2panX(rad2imgX(xlong));
}

/**
 * convert a latitude coordinate to a y position on the WMap panel
 * @param latitude - the latitude
 * @return - the y position
 */
public int lat2panY(String latitude){
    double ylat = failsafeParseDouble(latitude, -21.22);
    ylat = ylat * Math.PI /180.0;

	return (int) img2panY(rad2imgY(ylat));
}

/**
 * prepare to plot a node. This calls plotCN to effectively plot
 * the node at certains coordinates
 * @param n the node
 * @param g the graphic context
 */
protected void plotCN ( rcNode n,  Graphics g ) {

    if ( n.bHiddenOnMap==true )
        return;
    
	int klong = long2panX(n.LONG);
	int klat = lat2panY(n.LAT);
	
	//System.out.println(n.UnitName+" xlong="+xlong+" klong="+klong+" xlat="+xlat+" klat="+klat);

	Point pos = (Point) n.haux.get("MapPos"); // this is for tooltips
    if ( pos == null ) {
        pos = new Point() ;
    }
    pos.x = klong;
    pos.y = klat ;
    n.haux.put("MapPos", pos) ;

    plotCNWorker(n, klong, klat, g);
}

/**
 * effectively plot the node. This may be redefined to plot
 * the node in a certain way. By default it will plot a piechart
 * with the Load of the corresponding farm
 * @param n the farm
 * @param x x coordinate
 * @param y y cordinate
 * @param g graphics context
 */
protected void plotCNWorker(rcNode n, int x, int y, Graphics g){
	int R = 6;
	if (n.errorCount > 0) {
		g.setColor(Color.RED);
		g.fillArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
		g.setColor(Color.WHITE);
		g.fillArc(x - 1, y - 1, 3, 3, 0, 360);
	} else {
		pie px = (pie) n.haux.get("LoadPie");
		if ((px == null) || (px.len == 0)) {
			g.setColor(Color.yellow);
			g.fillArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
		} else {
			int u1 = 0;
			for (int i = 0; i < px.len; i++) {
				g.setColor(px.cpie[i]);
				int u2 = (int) (px.rpie[i] * 360);
				g.fillArc(x - R, y - R, 2 * R, 2 * R, u1, u2);
				u1 += u2;
			}
		}
	}
	g.setColor(Color.red);
	g.drawArc(x - R, y - R, 2 * R, 2 * R, 0, 360);
}

/**
 * this should be called to draw a link
 * @param link the link to be plot
 * @param g the graphics context
 * @param linkType one of LINK_TYPE_DIRECTED or LINE_TYPE_UNDIRECTED
 * @param color the color used to draw the line
 */
protected void plotILink(ILink link, Graphics g, int linkType, Color color) {
	useILink( link, g, linkType, color, 0, null);
}

/**
 * extension of plotILink to draw a link, or only to do the computation and check with a pair of 
 * coordonates (x,y) 
 * @param link
 * @param g
 * @param linkType
 * @param color
 * @param use
 * @param CheckPos
 * @return for use!=0 returns true if check pos is in arrow polygon
 */
protected boolean useILink(ILink link, Graphics g, int linkType, Color color, int use, int[] CheckPos)
{
/*	if ( use == 0 ) ;//plot
	else ; //tooltip
*/
	double xlat1 = link.fromLAT * 0.017453293;
	double xlat2 = link.toLAT * 0.017453293;
	double xlong1 = link.fromLONG * 0.017453293;
	double xlong2 = link.toLONG * 0.017453293;

	int klong1 = (int) img2panX(rad2imgX(xlong1));
	int klong2 = (int) img2panX(rad2imgX(xlong2));

	int klat1 = (int) img2panY(rad2imgY(xlat1));
	int klat2 = (int) img2panY(rad2imgY(xlat2));

	boolean bMouseOnArrow = false;
	
	// if it's shorter to get from one point to another going on the other
	// side of the globe...
	if (Math.abs(xlong1 - xlong2) > Math.PI) {
		// we have to go round the world
		int medLat = (klat1 + klat2) / 2;
		int edge1 = (int) img2panX(rad2imgX(-Math.PI));
		int edge2 = (int) img2panX(rad2imgX(Math.PI));

		if (xlong1 > 0) {
			int tmp = edge1;
			edge1 = edge2;
			edge2 = tmp;
		}
/*		plotILinkWorker(klat1, klong1, medLat, edge1, link, g, linkType, color);
		plotILinkWorker(medLat, edge2, klat2, klong2, link, g, linkType, color);
*/
		bMouseOnArrow = useILinkWorker(klat1, klong1, medLat, edge1, link, g, linkType, color, use, CheckPos);
		bMouseOnArrow |= useILinkWorker(medLat, edge2, klat2, klong2, link, g, linkType, color, use, CheckPos);
	} else {
		bMouseOnArrow = useILinkWorker(klat1, klong1, klat2, klong2, link, g, linkType, color, use, CheckPos);
		//plotILinkWorker(klat1, klong1, klat2, klong2, link, g, linkType, color);
	};
	return bMouseOnArrow;
	
}

/**
 * used to effectively plot the ILink; this is called by default by plotILink which
 * should be used instead.
 * @param klat2  y2 coordinate
 * @param klong2 x1 coordinate
 * @param klat1 y1 coordinate
 * @param klong1 x1 coordinate
 * @param link the ILink
 * @param g the graphic context
 * @param linkType one of LINK_TYPE_DIRECTED or LINE_TYPE_UNDIRECTED
 * @param color the color used to draw the line
 */
protected void plotILinkWorker(int klat2, int klong2, int klat1, int klong1, 
							ILink link, Graphics g, int linkType, Color color){
	useILinkWorker( klat2, klong2, klat1, klong1, link, g, linkType, color, 0, null);
}

/**
 * allows to be used for two purposes: draw and check if coordonates in area
 * @param klat2
 * @param klong2
 * @param klat1
 * @param klong1
 * @param link
 * @param g
 * @param linkType
 * @param color
 * @param use specifies the usage of the function: draw or just computation
 * @param CheckPos the position (x,y) to check
 */
protected boolean useILinkWorker(int klat2, int klong2, int klat1, int klong1, 
		ILink link, Graphics g, int linkType, Color color, int use, int []CheckPos)
{
	/*
	 * dd : 
	 * - variable used to compute the displacement for directional link
	 * from the undirected direction that is an central line,
	 * - makes the line to be drawn above the undirected line
	 * - generates some variables to retain the new coordonates 
	 */
	int dd = 4 ;
	int dx = ( klong1 - klong2 ); int dy = klat1 - klat2;
	float l = (float) Math.sqrt ( dx*dx+dy*dy );
	float dir_x  = dx /l ;
	float dir_y  = dy /l;

	int x1p = klong1 - (int) (dd * dir_y);
	int x2p = klong2 - (int) (dd * dir_y);
	int y1p = klat1 + (int) (dd * dir_x);
	int y2p = klat2 + (int) (dd * dir_x);

	//compute the coordonates for arrow that indicates the direction of the link
	int xv1 = (x1p + x2p) / 2;
	int yv1 = (y1p + y2p) / 2;
	int xv = (xv1 + x2p) / 2;
	int yv = (yv1 + y2p) / 2;

	Color shaddowColor=Color.BLACK;
	
	if ( use == 0 ) {
		((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		/** create shadow color*/
		int red, green, blue;
		red = color.getRed()-170;if ( red <  0 ) red = 0;
		green = color.getGreen()-170;if ( green <  0 ) green = 0;
		blue = color.getBlue()-170;if ( blue <  0 ) blue = 0;
		shaddowColor = new Color(red,green,blue);
		
		if ((linkType & LINK_TYPE_UNDIRECTED) != 0 ) {
			/** show shadow */
			g.setColor( shaddowColor);
			g.drawLine(klong2+1, klat2+1, klong1+1, klat1+1);
	
			g.setColor(color);
			g.drawLine(klong2, klat2, klong1, klat1);
		}
	};
	if ((linkType & LINK_TYPE_DIRECTED) != 0) {
		if ( use == 0 ) {
			g.setColor(color);
	
			g.fillArc(klong1 - dd / 2, klat1 - dd / 2, dd, dd, 0, 360);
			g.fillArc(klong2 - dd / 2, klat2 - dd / 2, dd, dd, 0, 360);
	
			/** show shadow */
			g.setColor( shaddowColor);
			g.drawLine(x1p+1, y1p+1, x2p+1, y2p+1);

			g.setColor(color);
			g.drawLine(x1p, y1p, x2p, y2p);
		};
		float aa = (float) (dd) / (float) 2.0;
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
		int[] ayv_shadow =
		{
			(int) (yv - aa * dir_y - 2 * aa * dir_x)+1,
			(int) (yv - aa * dir_y + 2 * aa * dir_x)+1,
			(int) (yv + 2 * aa * dir_y)+1,
			(int) (yv - aa * dir_y - 2 * aa * dir_x)+1};
		Polygon p = new Polygon(axv, ayv, 4);
		Polygon p_shadow = new Polygon(axv, ayv_shadow, 4);
		Rectangle r = p.getBounds();
		if ( use == 0 ) {
			/** draw shadow */
			g.setColor( shaddowColor);
			g.fillPolygon(p_shadow);

			g.setColor(color);
			g.fillPolygon(p);
		} else
			return r.contains( CheckPos[0], CheckPos[1]);
	}
	if ( use == 0 ) {
		((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		
		if((linkType & LINK_TYPE_SHOW_VALUE) != 0){
			double rate = ((Double) (link.data)).doubleValue();
			double per = rate *100 / link.speed ;
			String lbl = "" + nf.format(rate) + "("+nf.format(per)+"%)";
			FontMetrics fm = g.getFontMetrics();
			int wl = fm.stringWidth(lbl) + 1;
			int hl = fm.getHeight() + 1;
	
			int off = 2;
			int xl = xv;
			int yl = yv ;
	
			if ( dir_x >= 0 && dir_y < 0 )  { xl = xl + off ; yl = yl +hl ; }
			if ( dir_x <= 0 && dir_y > 0 ) { xl = xl - wl -off ; yl = yl- off ;}
			if ( dir_x > 0 && dir_y >= 0 ) { xl = xl -wl -off ; yl = yl + hl ; }
			if ( dir_x < 0 && dir_y < 0 )   { xl = xl +off  ; yl = yl - off ; }

			/** draw shadow */
			g.setColor( shaddowColor);
			g.drawString(lbl, xl+1, yl+1);

			g.setColor( color);
			g.drawString(lbl, xl, yl);
		}
	};
	return false;
}

/**
 * convert a string to a double value. If there would be an exception
 * return the failsafe value
 * @param value initial value
 * @param failsafe failsafe value
 * @return final value
 */
public double failsafeParseDouble(String value, double failsafe){
	try {
		return Double.parseDouble(value);
	} catch ( Throwable t  ){  
		return failsafe;
	}
}

/**
 * this should be called from plotOverImage to iterate
 * over the vector of nodes and plot them
 * @param g graphics context
 */
protected void plotNodes ( Graphics g ) {
	if  ( vnodes == null ) return;
	for (int i=0; i<vnodes.size(); i++) {
		rcNode n = ( rcNode ) vnodes.get(i);
		if(n != null)
			plotCN(n, g);
    }
}

/**
 * This should be redefined and here should be called all methods
 * that paint something on the map
 * @param g graphics context
 */
protected void plotOverImage(Graphics g){
	plotNodes(g);
}

/**
 * this should be redefined to return a tooltip text for a 
 * position on the map
 * @param event mouse event -> mouse position on the map
 * @return the string containing the tooltip text
 */
protected String getMapToolTipText(MouseEvent event){
	return null;
}

/**
 * inner class here to have access to parent's fields and at the same 
 * time to be positioned automatically by the environment near scroll bars. 
 * See constructor for details 
 */
class ImgPanel extends JPanel implements MouseListener, 
										 MouseMotionListener, 
										 MouseWheelListener,
										 ComponentListener,
										 AdjustmentListener {
	
	void adjustViewArea(){
		// adjust viewArea rectangle so that it has the same aspect ratio as the window
		// and is inside the srcBI image
		//System.out.println("1: vax="+viewArea.x+" vay="+viewArea.y+" vaW="+viewArea.width+" vaH="+viewArea.height);
		double vx = viewArea.width;
		double vy = viewArea.height;
		double panx = getWidth();
		double pany = getHeight();
		double sx = destBI.getWidth();
		double sy = destBI.getHeight();
		
		//System.out.println("vx="+vx+" vy="+vy+" px="+panx+" py="+pany+" sx="+sx+" sy="+sy);
		
		double fx = panx / vx;
		double fy = pany / vy;
		double f = Math.min(fx, fy);

		vx = panx / f; vy = pany / f;
		//System.out.println("fx="+fx+" fy="+fy+" f="+f+" vx="+vx+" vy="+vy);
				
		double gx = sx / vx;
		double gy = sy / vy;
		double g = Math.min(gx, gy);
		
		//System.out.println("gx="+gx+" gy="+gy+" g="+g);
		if(g < 1){
			gx *= 1 / g;
			gy *= 1 / g;
			vx = sx / gx; vy = sy / gy;
		}
		
		//System.out.println("gx="+gx+" gy="+gy+" vx="+vx+" vy="+vy);
		if(Double.isNaN(vx) || Double.isNaN(vy))
			return;
		double cx = viewArea.getCenterX();
		double cy = viewArea.getCenterY();
		
		//System.out.println("cx="+cx+" cy="+cy);
		viewArea.setFrame(cx - vx/2, cy - vy/2, vx, vy);
		//System.out.println("2: vax="+viewArea.x+" vay="+viewArea.y+" vaW="+viewArea.width+" vaH="+viewArea.height);
		
		if(viewArea.x < 0) viewArea.x = 0.0;
		if(viewArea.y < 0) viewArea.y = 0.0;
		if(viewArea.x + viewArea.width > destBI.getWidth() )
			viewArea.x = destBI.getWidth() - viewArea.width;
		if(viewArea.y + viewArea.height > destBI.getHeight() )
			viewArea.y = destBI.getHeight() - viewArea.height;
		//System.out.println("3: vax="+viewArea.x+" vay="+viewArea.y+" vaW="+viewArea.width+" vaH="+viewArea.height);
		// adjust scroll bars
		adjustingScrollBars = true;
		sbH.setValues((int)Math.round(viewArea.x), (int)Math.round(viewArea.width), 0, destBI.getWidth());
		sbV.setValues((int)Math.round(viewArea.y), (int)Math.round(viewArea.height), 0, destBI.getHeight());
		adjustingScrollBars = false;
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		 int x = e.getX();
		 int y = e.getY();

		 if(SwingUtilities.isLeftMouseButton(e)){
		 	 //System.out.println("left");
			 for ( final rcNode n : nodes.values() ) {
				  if ( !n.bHiddenOnMap ) {//if node should not be visible on map, don't check it
				      Point pos = (Point) n.haux.get("MapPos");
				      if ( pos != null ) {
				          if ( (Math.abs(x - pos.x) < 6) && 
				                  (Math.abs(y - pos.y) < 6) ) {
				              n.client.setVisible ( !n.client.isVisible() );
				          }
				      }
				  }
			 }
		 }else if(SwingUtilities.isRightMouseButton(e)){
			 //System.out.println("right");
			 iniSelArea.setBounds(0, 0, 0, 0);
			 if(zoomStatus == ZOOM_STABLE){
				 iniMousePos.setLocation(x, y);
				 zoomStatus = ZOOM_DRAWING_RECTANGLE;
			 }
		 }else if(SwingUtilities.isMiddleMouseButton(e)){
			 //System.out.println("middle");
			 if(zoomStatus == ZOOM_STABLE){
				 iniMousePos.setLocation(x, y);
				 zoomStatus = ZOOM_MOVING;
			 }
		 }
		 e.consume();
	 }

	 public void mouseDragged(MouseEvent e) {
		 crtMousePos.setLocation(e.getX(), e.getY());
		 if( zoomStatus == ZOOM_DRAWING_RECTANGLE ){
		 	 //System.out.println("drawing rectangle");
			 crtSelArea.setSize(0, 0);
			 crtSelArea.setLocation(iniMousePos);
			 crtSelArea.add(crtMousePos);
			 drawZoomRect(crtSelArea);
			 e.consume();
		 }else if( zoomStatus == ZOOM_MOVING ){
		 	 //System.out.println("moving");
			 int dx = crtMousePos.x - iniMousePos.x;
			 int dy = crtMousePos.y - iniMousePos.y;
			 double fx = getWidth() / viewArea.width;
			 double fy = getHeight() / viewArea.height;
			 viewArea.x += -dx/fx;
			 viewArea.y += -dy/fy;
			 adjustViewArea();
			 iniMousePos.setLocation(crtMousePos);
			 e.consume();
			 //System.out.println("mouseDragged->repaint");
			 repaint();
		 }
	 }

	  public void mouseReleased(MouseEvent e) {
		 if(SwingUtilities.isRightMouseButton(e) && zoomStatus == ZOOM_DRAWING_RECTANGLE){
			 if( crtSelArea.height >= 10 && crtSelArea.width >= 10 ){
				 // if the area is big enough, set new area
				 //System.out.println("zooming to new area");
				 double fx = getWidth() / viewArea.width;
				 double fy = getHeight() / viewArea.height;
				 crtSelArea.x = (int) (viewArea.x + crtSelArea.x / fx);
				 crtSelArea.y = (int) (viewArea.y + crtSelArea.y / fy);
				 crtSelArea.width = (int)(crtSelArea.width / fx);
				 crtSelArea.height = (int)(crtSelArea.height / fy);
				 if((crtSelArea.height >= 5.0 && crtSelArea.width >= 5.0))
				 	viewArea.setFrame(crtSelArea.x, crtSelArea.y, crtSelArea.width, crtSelArea.height); 
			 }else{
			 	// reset zoom
			 	//System.out.println("Resetting zoom");
			 	viewArea.setFrame(0, 0, destBI.getWidth(), destBI.getHeight());
			 }
			 adjustViewArea();
			 zoomStatus = ZOOM_STABLE;
			 crtSelArea.setBounds(0, 0, 0, 0);
			 iniSelArea.setBounds(0, 0, 0, 0);
			 //System.out.println("mouseReleased->repaint");
			 repaint();
		 }else if(SwingUtilities.isMiddleMouseButton(e)){
		 	zoomStatus = ZOOM_STABLE;
		 	//System.out.println("finished moving");
		 }
		 e.consume();
	 }


	 public void mouseEntered(MouseEvent e) {
	 }

	 public void mouseExited(MouseEvent e) {
	 }


	 public void mouseMoved(MouseEvent e) {
	 }

	 public void mouseWheelMoved(MouseWheelEvent e) {
		int amount = e.getWheelRotation();
		int x = e.getX();
		int y = e.getY();
		if(amount < 0 && (viewArea.height < 5.0 || viewArea.width < 5.0))
			return;
		//System.out.println("Wheel amount: "+amount);
		double dx = amount * viewArea.width / 10.0;
		double dy = amount * viewArea.height / 10.0;
		
		viewArea.x -= dx / 2 + (amount < 0 ? dx * (x - getWidth()/2) / getWidth() : 0); 
		viewArea.width += dx;
		viewArea.y -= dy / 2 + (amount < 0 ? dx * (y - getHeight()/2) / getHeight() : 0); 
		viewArea.height += dy;
		adjustViewArea();
		//System.out.println("wheelMoved->repaint");
		repaint();
	 }

	 void drawZoomRect( Rectangle nArea ){
		 synchronized(crtSelArea){
			 Graphics graphics = getGraphics();
			 graphics.setColor ( Color.BLACK);
			 graphics.setXORMode(Color.WHITE);
			 // delete old rectangle
			 graphics.drawRect(iniSelArea.x, iniSelArea.y, iniSelArea.width, iniSelArea.height);
			 // draw new rectangle
			 graphics.drawRect(nArea.x, nArea.y, nArea.width, nArea.height);
			 iniSelArea.setBounds(nArea);
			 graphics.setPaintMode();
		 }
	 }

	 void refreshZoomRect(Graphics graphics){
		 if(zoomStatus == ZOOM_DRAWING_RECTANGLE){
			 graphics.setColor ( Color.BLACK);
			 graphics.setXORMode(Color.WHITE);
			 graphics.drawRect(crtSelArea.x, crtSelArea.y, crtSelArea.width, crtSelArea.height);
			 graphics.setPaintMode();
		 }
	 }

	//	Watch for scroll bar adjustments
	public void adjustmentValueChanged(AdjustmentEvent event) {
		// The event came from our scrollers handle it.
		Object src = event.getSource();
		if (!adjustingScrollBars && (src == sbH || src == sbV)) {
			viewArea.x = sbH.getValue();
			viewArea.y = sbV.getValue();
			adjustViewArea();
			//System.out.println("adjustmentValueChanged->repaint");
			repaint();
		}
	}				


    public synchronized void paint(Graphics g)
    {
		synchronized(crtSelArea){		// to avoid overwritting zoom rectangle
			//System.out.println("WMap: painting...");
			//long t1 = NTPDate.currentTimeMillis();
			g.drawImage(destBI, 
				0, 0, getWidth(), getHeight(), 
				(int)viewArea.x, (int)viewArea.y, (int)(viewArea.x+viewArea.width), (int)(viewArea.y+viewArea.height), 
				this);
			plotOverImage(g);
			refreshZoomRect( g );
			//long t2 = NTPDate.currentTimeMillis();
			//System.out.println("WMap: ... painted in "+ (t2-t1));
		}
    }

	public String getToolTipText(MouseEvent event){
		return getMapToolTipText(event);
	}

	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
	}

	public void componentResized(ComponentEvent e) {
		adjustViewArea();
	}

	public void componentShown(ComponentEvent e) {
		adjustViewArea();
	}

// end of inner class
}

// end of main class
}

