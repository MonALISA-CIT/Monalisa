/*
 * Created on Apr 8, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package lia.web.servlets.map2d;

/**
 * @author alexc
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.Sphere.SphereTexture;
import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.ServletExtension;

public class WWmap1 {
	Map2D map2D;
	public int width;				//  size of the final image
	public int height;				// 
	public Vector nodes;			// contains WNodes
	public Vector links;			// contains WLinks
	public Vector new_links;		// contains new WLinks that comply to some very inteligent algorithms :D
	public Vector special;
	public Vector farmrouters; //contains the routers that are also farms
	public Vector routers; //contains the simple routers
	public Vector biggernodes; //contains the nodes that have a bigger radius size
	
	public Color bgColor = Color.WHITE; // background color
	public Color routerColor = ColorFactory.getColor(0, 170, 249); // default color 4 routers 
	public Color defNodeColor = Color.red; // default color 4 nodes
	public Color nodataColor = Color.ORANGE;
	public Color zerovalueColor = Color.WHITE;
	
	
	public Hashtable colorScales;
	public Hashtable valScales;
	
	protected String textureFilename;	// full name of the texture
	protected BufferedImage texture;	// the texture
	protected BufferedImage image;	// the final image
	protected Graphics graphics;		// the graphics context for image
	protected DecimalFormat formatter;// format drawn numbers
	protected Rectangle tRect;		// visual rectangle - coords from texture
	protected Rectangle iRect;		// destination rectangle - coords from image
	protected Properties prop;
	
	public boolean showBump;
	public boolean showShadow;
	public Image shadow;
	public Image bump;
	public ConvolveOp blurOp;
	final public Color shadowColor = ColorFactory.getColor(0, 0, 0, 76);
	
	
	/**
	 * Build the Web World Map
	 * @param width	- width of the image
	 * @param height - height of the image
	 * @param nodes - vector of WNode s
	 * @param links - vector of WLink s
	 * @param resolution - one of "1024x512", "2048x1024", "4096x2048", "8192x4096"
	 */
	public WWmap1( BufferedImage img, int width, int height, Vector nodes, Vector links, Vector special, String resolution, Properties prop, Vector vFarmRouters, Vector vRouters, Vector vBiggerNodes, boolean showBump, boolean showShadow){
		this.width = width;
		this.height = height;
		this.nodes = nodes;
		this.links = links;
		this.farmrouters = vFarmRouters;
		this.routers = vRouters;
		this.biggernodes = vBiggerNodes;
		this.showShadow = showShadow;
		this.showBump = showBump;
		
		this.new_links = new Vector();
		this.special = special;
		this.textureFilename = "lia/images/earth_texture"+resolution+".jpg";
		this.prop = prop;

		//texture = WWTextureLoader.getTexture(textureFilename);
		
		image = img;
		graphics = image.getGraphics();
		Font font = new Font("Arial", Font.PLAIN, 11);	//Font.PLAIN
		graphics.setFont(font);
		tRect = new Rectangle();
		iRect = new Rectangle();
		
		nodataColor = getColor(prop, "color.nodata", Color.ORANGE);
		zerovalueColor = getColor(prop, "color.zerovalue", Color.WHITE);
		
		
		colorScales = new Hashtable();
		
		colorScales.put("Node_min", getColor(prop, "default.color.min", Color.CYAN));
		colorScales.put("Node_max", getColor(prop, "default.color.max", Color.BLUE));
		
		colorScales.put("Delay_min", getColor(prop, "Delay.color.min", ColorFactory.getColor(0, 255, 100)));
		colorScales.put("Delay_max", getColor(prop, "Delay.color.max", ColorFactory.getColor(255, 255, 0)));

		colorScales.put("Bandwidth_min", getColor(prop, "Bandwidth.color.min", ColorFactory.getColor(255, 255, 0)));
		colorScales.put("Bandwidth_max", getColor(prop, "Bandwidth.color.max", ColorFactory.getColor(0, 255, 100)));

		valScales = new Hashtable();
	}
	
	private static final Color getColor(Properties prop, String sParam, Color cDefault){
	    try{
		StringTokenizer st = new StringTokenizer(ServletExtension.pgets(prop, sParam));
		
		return ColorFactory.getColor(
		    Integer.parseInt(st.nextToken()),
		    Integer.parseInt(st.nextToken()),
		    Integer.parseInt(st.nextToken())
		);
	    }
	    catch (Exception e){
		return cDefault;
	    }
	}
	
	/**
	 * Render the whole scene
	 * @param longCenter - center of the image
	 * @param latCenter - in longitude and latitude coords
	 * @param zoom - for zoom==1 draw the original picture. 
	 * @return - the final image, with texture, nodes and links
	 */
	public BufferedImage getImage(double longCenter, double latCenter, double zoom){
		//graphics.setColor(Color.BLACK);
		//graphics.fillRect(0, 0, width, height);
		
		//drawTexture(longCenter, latCenter, zoom);
		
		drawNodes(0);
		drawSLinks();
		drawLinks();
		drawNodes(1);
		
		try{
		   drawLegend();
		}
		catch(Throwable t){
		    t.printStackTrace();
		}
	
		return image;
	}
	
	
	private void drawLegend(){
	    Graphics2D g = (Graphics2D) graphics;
	
	    if (ServletExtension.pgetb(prop, "Legend.display", false)){
		
		int w = ServletExtension.pgeti(prop, "Legend.position.width", 300);
		int h = ServletExtension.pgeti(prop, "Legend.position.height", 30);
		
		int x = map2D.globals.DISPLAY_W - w - 5; 
		//genimage.pgeti(prop, "Legend.position.x", 500);
		int y = map2D.globals.DISPLAY_H - h - 5;
		//genimage.pgeti(prop, "Legend.position.y", 370);
		
		
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		g.setPaint(ColorFactory.getColor(200, 200, 200));
		g.fill(new RoundRectangle2D.Double(x, y, w, h, 5, 5));
		
		int x2 = ServletExtension.pgeti(prop, "Legend.gradient.x", 150);
		int y2 = ServletExtension.pgeti(prop, "Legend.gradient.y", 8);
		int w2 = ServletExtension.pgeti(prop, "Legend.gradient.width", 80);
		int h2 = ServletExtension.pgeti(prop, "Legend.gradient.height", 14);
		
		String sParameter = ServletExtension.pgets(prop, "Legend.parameter.name", "Delay");
		String sAlias     = ServletExtension.pgets(prop, "Legend.parameter.alias", sParameter);
		
		String sSuffix    = Formatare.replace(ServletExtension.pgets(prop, "Legend.suffix", ""), "_", " ");
		
		Color cMin = (Color) colorScales.get(sParameter+"_min");
		Color cMax = (Color) colorScales.get(sParameter+"_max");
		
		GradientPaint gpaint = new GradientPaint(x+x2, y+y2, cMin, x+x2+w2, y+y2, cMax);
		
		g.setPaint(gpaint);
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.fill(new Rectangle2D.Double(x+x2, y+y2, w2, h2));
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		double dMin = ((Double) valScales.get(sParameter+"_min")).doubleValue();
		double dMax = ((Double) valScales.get(sParameter+"_max")).doubleValue();
		
		if (dMin>dMax){
		    dMin=0;
		    dMax=0;
		}
	    		
		g.setPaint(Color.BLACK);
		
		int xl = ServletExtension.pgeti(prop, "Legend.label.x", 7);
		int yl = ServletExtension.pgeti(prop, "Legend.label.y", 19);
		
		String sSeparator = Formatare.replace(ServletExtension.pgets(prop, "Legend.separator", "_"), "_", " ");
		
		g.drawString(sAlias+sSeparator+DoubleFormat.point(dMin)+sSuffix, x+xl, y+yl);
		g.drawString(DoubleFormat.point(dMax)+sSuffix, x+x2+w2+5, y+yl);
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF );
	    }
	}

	private void adjustLinkMinMax(String what){
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for(int i=0; i<links.size(); i++){
			Hashtable data = ((WLink1)links.get(i)).data;
            Object obj = data.get(what);
            Double val=null;
            if ( obj instanceof Double )
    			val = (Double) obj;
            else if ( obj instanceof Double[] )
                val = getMedValue((Double[]) obj);
            if(val != null){
                double v = val.doubleValue();
                min = (v < min ? v : min);
                max = (v > max ? v : max);
            }
		}
		valScales.put(what+"_min", Double.valueOf(min));
		valScales.put(what+"_max", Double.valueOf(max));
	}
	
	private void drawLinks(){
		adjustLinkMinMax("Bandwidth");
		adjustLinkMinMax("Delay");
		for(int i=0; i<links.size(); i++){
			WLink1 link = (WLink1) links.get(i);
			
			WNode1 nSource = link.src;
			WNode1 nDest = link.dest;
			
			
			
			int max_screen_width = (int)(map2D.globals.DISPLAY_W*Globals.MAP_WIDTH/map2D.globals.width2D);
			int x1, x2;
			x1 = nSource.x;
			x2 = nDest.x;
            //boolean bNode1Visible = NodeUtilities.nodeVisible(map2D,(float)nSource.LONG,(float)nSource.LAT,map2D.globals.x2D,map2D.globals.width2D);
            //boolean bNode2Visible = NodeUtilities.nodeVisible(map2D,(float)nDest.LONG,(float)nDest.LAT,map2D.globals.x2D,map2D.globals.width2D);
			
			boolean bNode1Visible = 0<nSource.x && nSource.x<map2D.globals.DISPLAY_W && 0<nSource.y && nSource.y<map2D.globals.DISPLAY_H;
			boolean bNode2Visible = 0<nDest.x && nDest.x<map2D.globals.DISPLAY_W && 0<nDest.y && nDest.y<map2D.globals.DISPLAY_H;
			
			
			//System.out.println("S: "+nSource.name+" x: "+nSource.x+" visible: "+bNode1Visible+" D:"+nDest.name+" x:"+nDest.x+" visible: "+bNode2Visible);
			
			
			if (  bNode1Visible && !bNode2Visible ) {
				if ( Math.abs(nSource.x - nDest.x) > Math.abs( nSource.x - nDest.x_alt) )
					x2 = nDest.x_alt;
			} else if ( !bNode1Visible && bNode2Visible ) {
                if ( Math.abs(nDest.x - nSource.x) > Math.abs( nDest.x - nSource.x_alt) )
                    x1 = nSource.x_alt;
			}
			
            WNode1 nAuxSource = new WNode1(nSource);
			nAuxSource.x = x1;
            WNode1 nAuxDest = new WNode1(nDest);
			nAuxDest.x = x2;

			if(Math.abs(x1 - x2)>max_screen_width/2 && ( bNode1Visible || bNode2Visible ) ){

				
				//System.out.println("nAuxSource.x: "+x1+"nAuxDest.x: "+x2);
				WNode1 nAuxLeft = new WNode1("left","-10","-10",null);
				WNode1 nAuxRight = new WNode1("right","-10","-10",null);
				
				nAuxLeft.x = 0;
				//nAuxLeft.y = map2D.globals.DISPLAY_H/2;
				
				nAuxRight.x = map2D.globals.DISPLAY_W;
				//nAuxRight.y = map2D.globals.DISPLAY_H/2;
				
				WLink1 wl1;
				WLink1 wl2;
				
				int y;
				
				
				if(nAuxSource.x < nAuxDest.x){
					y = ((nAuxRight.x-nAuxDest.x)*nAuxSource.y+(nAuxSource.x-nAuxLeft.x)*nAuxDest.y)/(nAuxSource.x+nAuxRight.x-nAuxLeft.x-nAuxDest.x);
					nAuxRight.y = y;
					nAuxLeft.y = y;
					nAuxLeft.name=nDest.name;
                    nAuxLeft.realname=nDest.realname;
					nAuxRight.name=nSource.name;
                    nAuxRight.realname=nSource.realname;
					wl1 = new WLink1(nAuxSource,nAuxLeft,link.data);
					wl2 = new WLink1(nAuxRight,nAuxDest,link.data);
				}
				else{
					y = ((nAuxRight.x-nAuxSource.x)*nAuxDest.y+(nAuxDest.x-nAuxLeft.x)*nAuxSource.y)/(nAuxDest.x+nAuxRight.x-nAuxLeft.x-nAuxSource.x);
					nAuxRight.y = y;
					nAuxLeft.y = y;
					nAuxLeft.name=nSource.name;
                    nAuxLeft.realname=nSource.realname;
					nAuxRight.name=nDest.name;
                    nAuxRight.realname=nDest.realname;
					wl1 = new WLink1(nAuxSource,nAuxRight,link.data);
					wl2 = new WLink1(nAuxLeft,nAuxDest,link.data);
				
				}
				
				
				new_links.add(wl1);
				new_links.add(wl2);
				paintLink(wl1);
				paintLink(wl2);
			}
			else if ( bNode1Visible || bNode2Visible ) {
				WLink1 AuxLink = new WLink1(nAuxSource, nAuxDest, link.data);
				//System.out.println("nAuxSource.x: "+nAuxSource.x+" nAuxDest.x: "+nAuxDest.x);
				new_links.add(AuxLink);
				paintLink( AuxLink);
			}
			
			
			
		}
	}
	
	private void drawSLinks(){
	    try{
	    for (int i=0; i<special.size()-2; i+=4){
	    	paintSLink( (WNode1) special.get(i), (WNode1) special.get(i+1), (String) special.get(i+2), (Color) special.get(i+3) );
	    }
	    }catch (Exception e){
		System.err.println("Exception : "+e+"("+e.getMessage()+")");
		e.printStackTrace();
	    }
	}
	
	public static final String formatLabel(String sFormat, WLink1 link, Object b, Object d){
	    String sLabel = Formatare.replace(sFormat, "$N1", link.src.realname);
	    sLabel = Formatare.replace(sLabel, "$A1", link.src.name);
	    sLabel = Formatare.replace(sLabel, "$N2", link.dest.realname);
	    sLabel = Formatare.replace(sLabel, "$A2", link.dest.name);
        if ( b!=null && b instanceof Double[] ) {
            Double[] vDouble = (Double[])b;
            Double bv;
            for ( int i=0; i<vDouble.length; i++) {
                bv = vDouble[i];
                sLabel = Formatare.replace(sLabel, "$B"+(i+1), bv!=null ? DoubleFormat.point(bv.doubleValue()) : "--");
            }
        }
        if ( d!=null && d instanceof Double[] ) {
            Double[] vDouble = (Double[])d;
            Double dv;
            for ( int i=0; i<vDouble.length; i++) {
                dv = vDouble[i];
                sLabel = Formatare.replace(sLabel, "$D"+(i+1), dv!=null ? DoubleFormat.point(dv.doubleValue()) : "--");
            }
        }
        Double value=null;
        if ( b!=null )
            if ( b instanceof Double )
                value = (Double)b;
            else
                value = getMedValue((Double[])b);
        else
            value = null;
        sLabel = Formatare.replace(sLabel, "$B", value==null ? "" : DoubleFormat.point(value.doubleValue()));
        
        if (sLabel.indexOf("$S")>=0){
        	String sM = value!=null ? DoubleFormat.size(value.doubleValue(), "M") : "";
        	String sK = value!=null ? DoubleFormat.size(value.doubleValue(), "K") : "";
        	String sB = value!=null ? DoubleFormat.size(value.doubleValue(), "B") : "";
        	
        	if (sM.toLowerCase().endsWith("b"))
        		sM = sM.substring(0, sM.length()-1);
        	
        	if (sK.toLowerCase().endsWith("b"))
        		sK = sK.substring(0, sK.length()-1);
        	
        	if (sB.toLowerCase().endsWith("b"))
        		sB = sB.substring(0, sB.length()-1);

        	sLabel = Formatare.replace(sLabel, "$SMB", sM);
        	sLabel = Formatare.replace(sLabel, "$SKB", sK);
        	sLabel = Formatare.replace(sLabel, "$SBB", sB);
        }
        
        if ( d!=null )
            if ( d instanceof Double )
                value = (Double)d;
            else
                value = getMedValue((Double[])d);
        else
            value = null;
        sLabel = Formatare.replace(sLabel, "$D", value==null ? "" : DoubleFormat.point(value.doubleValue()));
	    
	    sLabel = Formatare.replace(sLabel, "()", "");
	    sLabel = Formatare.replace(sLabel, "[]", "");
	    sLabel = Formatare.replace(sLabel, "{}", "");
	    
	    sLabel = sLabel.trim();
	    
	    return sLabel;
	}
    
    private static Double getMedValue( Double [] vDouble )
    {
        if ( vDouble==null || vDouble.length==0 )
            return null;
        double sum=0;
        int nTotal=0;
        for ( int i=0; i<vDouble.length; i++ )
            if ( vDouble[i]!=null ) {
                sum += vDouble[i].doubleValue();
                nTotal++;
            }
       if ( nTotal == 0 )
           return null;
       return Double.valueOf(sum/nTotal);
    }
	
	private void paintLink(WLink1 link){
		//System.out.println("[paintLink]");
		String sBW  = ServletExtension.pgets(prop, "Bandwidth.parameter", "Bandwidth");
		String sDel = ServletExtension.pgets(prop, "Delay.parameter", "Delay");
		int colorNr=0;
		
        Object obj;
        obj = link.data.get(sBW);
        Double bw = null;
        if ( obj instanceof Double )
            bw = (Double) obj;
        else if ( obj instanceof Double[] && ((Double[])obj).length>0 ){
            //bw = getMedValue((Double[])obj);
            Double bw_value=null;
            Double[] bw_vec=(Double[])obj;
            if(ServletExtension.pgetb(prop,"cmap",false)){
            	for(int j=0; j<((Double[])obj).length;j++){
            		bw_value=bw_vec[j];
            		if(bw_value!=null && bw_value.doubleValue()>0){
            			colorNr=j;
            			break;
            		}	
            	}
            	bw=bw_value;
            }
            else
            	bw = getMedValue((Double[])obj);
        }
        obj = link.data.get(sDel);
        Double del = null;
        if ( obj instanceof Double )
            del = (Double) obj;
        else if ( obj instanceof Double[] && ((Double[])obj).length>0 )
            del = getMedValue((Double[])obj);
		
		String sLabelFormat = ServletExtension.pgets(prop, "Label.format", "$B ($D)");
		
		sLabelFormat = ServletExtension.pgets(prop, link.src.realname+"_"+link.dest.realname+".label.format", sLabelFormat);
		
		Color color = Color.YELLOW;
		float stroke = 1;
		String label = "";
		int dd = 6;			// guess what is this! :-)
		if(ServletExtension.pgetb(prop,"opticalpaths",false)){
			//System.out.println("in Optical....");
				if(bw != null){
					//System.out.println("bw not null....");
					if(bw.doubleValue() < ServletExtension.pgeti(prop, "default.opticallimit", -20)){
						//System.out.println("bw<limit.");
						color =  getColor(prop, "color.light_off", Color.RED);
						stroke = ServletExtension.pgeti(prop,"Stroke.optical",2);
					}
					else{
						//System.out.println("bw > limit");
						color =  getColor(prop, "color.light_on", Color.YELLOW);
						stroke = ServletExtension.pgeti(prop,"Stroke.optical",2);
					}
				}
				else{
					//System.out.println("bw null....");
					stroke=0;
				}
		}
		else{
				//System.out.println("not Optical....");
				if(bw != null && del != null){
						color = getScaledColor(del.doubleValue(), sDel);
						stroke = getScaledStroke(bw.doubleValue(), sBW);
				}else if(bw != null){
					// delay is null => paint in yellow
					color = getColor(prop, "Delay.color.unknown", Color.ORANGE);
					stroke = getScaledStroke(bw.doubleValue(), sBW);
				}else if(del != null){
					// no bandwidth available
					color = getScaledColor(del.doubleValue(), sDel);
					stroke = getScaledStroke(0, sBW);	// minimum bandwidth stroke
				}
		
		}
		label = formatLabel(sLabelFormat, link, bw, del);	
		
		if(ServletExtension.pgetb(prop,"cmap",false))
			color=getColor(prop, "color"+colorNr, Color.YELLOW);
		
		plotWLink(link, color, stroke, label, dd);
	}
	
	private void paintSLink(WNode1 src, WNode1 dest, String label, Color c){
		Color color = c!=null ? c : ColorFactory.getColor(255, 46, 189);
		float stroke = 2;
	
		int klong1 = src.x;//(int) tex2imgX(long2texX(src.LONG));
		int klong2 = dest.x;//(int) tex2imgX(long2texX(dest.LONG));
		
		int klat1 = src.y;//(int) tex2imgY(lat2texY(src.LAT));
		int klat2 = dest.y;//(int) tex2imgY(lat2texY(dest.LAT));
				
		Graphics2D g = (Graphics2D) graphics;
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(color);
		
		Stroke oldStroke = g.getStroke();
		g.setStroke( new BasicStroke(stroke));
		g.drawLine(klong1, klat1, klong2, klat2);
		g.setStroke(oldStroke);

		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		
		// let's put some label on this one
		
		int dx = ( klong1 - klong2 ); int dy = klat1 - klat2;
		float l = (float) Math.sqrt ( dx*dx+dy*dy );
		float dir_x  = dx /l ;
		float dir_y  = dy /l;
		
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

		if ( dir_x >= 0 && dir_y < 0 )  { xl = xl + off ; yl = yl +hl ; }
		if ( dir_x <= 0 && dir_y > 0 ) { xl = xl - wl -off ; yl = yl- off ;}
		if ( dir_x > 0 && dir_y >= 0 ) { xl = xl -wl -off ; yl = yl + hl ; }
		if ( dir_x < 0 && dir_y < 0 )   { xl = xl +off  ; yl = yl - off ; }
		
		g.setColor(color.darker().darker().darker());
		for (int i=-1; i<=1; i++)
		    for (int j=-1; j<=1; j++)
			g.drawString(label, xl+i, yl+j);
			
		g.setColor(color);
		g.drawString(label, xl, yl);

	}
	
	private void plotWLink(WLink1 link, Color color, float stroke, String label, int dd){
		int klong1 = link.src.x;//(int) tex2imgX(long2texX(link.src.LONG));
		int klong2 = link.dest.x;//(int) tex2imgX(long2texX(link.dest.LONG));
		
		int klat1 = link.src.y;//(int) tex2imgY(lat2texY(link.src.LAT));
		int klat2 = link.dest.y;//(int) tex2imgY(lat2texY(link.dest.LAT));
				
		// if it's shorter to get from one point to another going on the other
		// side of the globe...
		/*
		if (Math.abs(link.src.LONG - link.dest.LONG) > 180) {
			// we have to go round the world
			int medLat = (klat1 + klat2) / 2;
			int edge1 = (int) tex2imgX(long2texX(-180));
			int edge2 = (int) tex2imgX(long2texX(180));

			if (link.src.LONG > 0) {
				int tmp = edge1;
				edge1 = edge2;
				edge2 = tmp;
			}
			plotWLinkWorker(klat1, klong1, medLat, edge1, color, stroke, label, dd);
			plotWLinkWorker(medLat, edge2, klat2, klong2, color, stroke, label, dd);
		}else{
		*/
		Color darker = color.darker().darker();
		plotWLinkWorker(klat1+1, klong1+1, klat2+1, klong2+1, darker, stroke, label, dd, false, null);
		plotWLinkWorker(klat1+1, klong1-1, klat2+1, klong2-1, darker, stroke, label, dd, false, null);
		
		plotWLinkWorker(klat1, klong1, klat2, klong2, color, stroke, label, dd, true, link);
		
		/*	
		}
		*/
	}
	
	private void plotWLinkWorker(int klat2, int klong2, int klat1, int klong1, 
			Color color, float stroke, String label, int dd, boolean bPlotText, WLink1 link){
		/*
		 * dd : 
		 * - variable used to compute the displacement for directional link
		 * from the undirected direction that is an central line,
		 * - makes the line to be drawn above the undirected line
		 * - generates some variables to retain the new coordonates 
		 */
		int dx = ( klong1 - klong2 ); int dy = klat1 - klat2;
		float l = (float) Math.sqrt ( dx*dx+dy*dy );
		float dir_x  = dx /l ;
		float dir_y  = dy /l;

		int x1p = klong1 - (int) (dd * dir_y);
		int x2p = klong2 - (int) (dd * dir_y);
		int y1p = klat1 + (int) (dd * dir_x);
		int y2p = klat2 + (int) (dd * dir_x);
		
		if (link!=null){
		    float w = 1 + stroke/2;
		
		    int x11p = x1p - (int) (w * dir_y);
		    int x12p = x1p + (int) (w * dir_y);
		
	    	int x21p = x2p - (int) (w * dir_y);
		    int x22p = x2p + (int) (w * dir_y);
		
		    int y11p = y1p + (int) (w * dir_x);
		    int y12p = y1p - (int) (w * dir_x);
		
		    int y21p = y2p + (int) (w * dir_x);
		    int y22p = y2p - (int) (w * dir_x);
		    
		    link.vMap = new Vector();
		    link.vMap.add(Integer.valueOf(x11p));
		    link.vMap.add(Integer.valueOf(y11p));
		    link.vMap.add(Integer.valueOf(x21p));
		    link.vMap.add(Integer.valueOf(y21p));
		    link.vMap.add(Integer.valueOf(x22p));
		    link.vMap.add(Integer.valueOf(y22p));
		    link.vMap.add(Integer.valueOf(x12p));
		    link.vMap.add(Integer.valueOf(y12p));
		}

		//compute the coordonates for arrow that indicates the direction of the link
		int xv1 = (x1p + x2p) / 2;
		int yv1 = (y1p + y2p) / 2;
		int xv = (xv1 + x2p) / 2;
		int yv = (yv1 + y2p) / 2;

		Graphics g = graphics;

		((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		
		g.setColor(color);
		/*
		g.fillArc(klong1 - dd / 2, klat1 - dd / 2, dd, dd, 0, 360);
		g.fillArc(klong2 - dd / 2, klat2 - dd / 2, dd, dd, 0, 360);
		*/
		
		Stroke oldStroke = ((Graphics2D)g).getStroke();
		((Graphics2D)g).setStroke( new BasicStroke(stroke));
		g.drawLine(x1p, y1p, x2p, y2p);
		((Graphics2D)g).setStroke(oldStroke);
		
		float arrow = 2;
		float aa = (dd + stroke/2) / (float) 2.0;
		
		int[] axv =
			{
				(int) (xv - aa * dir_x + arrow  * aa * dir_y),
				(int) (xv - aa * dir_x - arrow  * aa * dir_y),
				(int) (xv              + arrow * aa * dir_x),
				(int) (xv - aa * dir_x + arrow  * aa * dir_y)};
		int[] ayv =
			{
				(int) (yv - aa * dir_y - arrow * aa * dir_x),
				(int) (yv - aa * dir_y + arrow * aa * dir_x),
				(int) (yv              + arrow * aa * dir_y),
				(int) (yv - aa * dir_y - arrow * aa * dir_x)};
		Polygon p = new Polygon(axv, ayv, 4);
		g.fillPolygon(p);

		((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			
		FontMetrics fm = g.getFontMetrics();
		int wl = fm.stringWidth(label) + 1;
		int hl = fm.getHeight() + 1;

		int off = 2;
		int xl = xv;
		int yl = yv ;

		if ( dir_x >= 0 && dir_y < 0 )  { xl = xl + off ; yl = yl +hl ; }
		if ( dir_x <= 0 && dir_y > 0 ) { xl = xl - wl -off ; yl = yl- off ;}
		if ( dir_x > 0 && dir_y >= 0 ) { xl = xl -wl -off ; yl = yl + hl ; }
		if ( dir_x < 0 && dir_y < 0 )   { xl = xl +off  ; yl = yl - off ; }

		/*
		if (bPlotText){
		    g.setColor(color.darker().darker().darker());
		    for (int i=-1; i<=1; i++)
			for (int j=-1; j<=1; j++)
			    g.drawString(label, xl+i, yl+j);
			
		    g.setColor(color);
		    g.drawString(label, xl, yl);
		}
		*/
	}
	
	private void drawNodes(int drawNodes){
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for(int i=0; i<nodes.size(); i++){
			List data = ((WNode1)nodes.get(i)).data;
			if(data != null && data.size() == 1){
				double val = ((Double) data.get(0)).doubleValue();
				min = (val < min ? val : min);
				max = (val > max ? val : max);
			}
		}
		valScales.put("Node_min", Double.valueOf(min));
		valScales.put("Node_max", Double.valueOf(max));
		
		Vector firstNodes = new Vector();
		for(int i=0; i<nodes.size(); i++)
			paintNode((WNode1) nodes.get(i), map2D.globals.x2D, map2D.globals.width2D,0, firstNodes,drawNodes);
		if(map2D.globals.x2D+map2D.globals.width2D>Globals.MAP_WIDTH/2){
			//System.out.println("Second picture...");
			float offset=(Globals.MAP_WIDTH/2-map2D.globals.x2D)*map2D.globals.DISPLAY_W/map2D.globals.width2D;
			for(int i=0; i<nodes.size(); i++)
				paintNode((WNode1) nodes.get(i), -16, map2D.globals.width2D,offset, firstNodes,drawNodes);
		}
	}
	
	private float getScaledStroke(double val, String what){
		double vmin = ((Double) valScales.get(what+"_min")).doubleValue();
		double vmax = ((Double) valScales.get(what+"_max")).doubleValue();
		float minS = (float) ServletExtension.pgetd(prop, "Stroke.min", 2D);
		float maxS = (float) ServletExtension.pgetd(prop, "Stroke.max", 4D);
		
		double delta = Math.abs(vmax - vmin);
		if (Math.abs(delta) < 1E-5 || val<vmin)
		    return minS;
		   
		if (val>vmax)
		    return maxS;
		   
		return (float) ((val - vmin)* (maxS - minS) / delta) + minS;
	}
	
	private Color getScaledColor(double val, String what){
		double vmin = ((Double) valScales.get(what+"_min")).doubleValue();
		double vmax = ((Double) valScales.get(what+"_max")).doubleValue();
		Color cmin = (Color) colorScales.get(what+"_min");
		Color cmax = (Color) colorScales.get(what+"_max");
		//System.out.println("vmax="+vmax+" vmin="+vmin);
		//System.out.println("cmin="+cmin+" cmax="+cmax);
		
		double delta = Math.abs(vmax - vmin);
		if (Math.abs(delta) < 1E-5)
		   return cmin;
		int R,G,B;
		R = (int) ((val - vmin) * (cmax.getRed() - cmin.getRed()) / delta)
				+ cmin.getRed();
		G = (int) ((val - vmin) * (cmax.getGreen() - cmin.getGreen()) / delta)
					+ cmin.getGreen();
		B = (int) ((val - vmin) * (cmax.getBlue() - cmin.getBlue()) / delta)
					+ cmin.getBlue();
		
		if (R<0) R = 0; if (R>255) R=255; 
		if (G<0) G = 0; if (G>255) G=255; 
		if (B<0) B = 0; if (B>255) B=255;
		
		return ColorFactory.getColor(R, G, B);
	}
	
	public static Image toImage(BufferedImage bufferedImage) {
        return Toolkit.getDefaultToolkit().createImage(bufferedImage.getSource());
    }
	
	private BufferedImage createShadowMask(BufferedImage image) {
		
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
		
	private ConvolveOp getBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1 / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
    }
	
	private void createShadow(int w, int h) {
		//BufferedImage bshadow = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w+14, h+14, Transparency.TRANSLUCENT);
		BufferedImage bshadow = new BufferedImage(w+10,h+10,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bshadow.createGraphics();
		g.setColor(Color.cyan);
		g.fillOval(3,3, w, h);
		if (blurOp == null)
			blurOp = getBlurOp(7);
		blurOp.filter(createShadowMask(bshadow), bshadow);
		shadow = toImage(bshadow);
	}
	
	private void createBump(int w, int h) {
		try{
		//BufferedImage bbump = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
		BufferedImage bbump = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bbump.createGraphics();
		Rectangle rect = new Rectangle(0, 0, w, h);
		//System.out.println("[createBump] before setTexture");
		SphereTexture.setTexture(g, rect);
		//System.out.println("[createBump] after setTexture");
		g.fillOval(0, 0, w, h);
		bump = toImage(bbump);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
		
	private void paintNode(WNode1 n, float x2d, float w2d, float offset, Vector firstNodes, int drawNodes){
		
		float []screen_coord = null;
		screen_coord = NodeUtilities.transform2Screen((float)n.LONG, (float)n.LAT, screen_coord);
		
		/*
		if( !NodeUtilities.nodeVisible(map2D, (float)n.LONG, (float)n.LAT, x2d, w2d) ){
			if ( !firstNodes.contains(n) ) {
				n.x = (int)((screen_coord[0]-x2d)*map2D.globals.DISPLAY_W/w2d+offset);
				n.y = (int)((map2D.globals.y2D-screen_coord[1])*map2D.globals.DISPLAY_H/map2D.globals.height2D);
				firstNodes.add(n);
			} else {
				n.x_alt = (int)((screen_coord[0]-x2d)*map2D.globals.DISPLAY_W/w2d+offset);
				n.y_alt = (int)((map2D.globals.y2D-screen_coord[1])*map2D.globals.DISPLAY_H/map2D.globals.height2D);
			}
			return;
		}
		*/
		if( !NodeUtilities.nodeVisible(map2D, (float)n.LONG, (float)n.LAT, x2d, w2d) ){
			if(offset==0) {
				n.x = (int)((screen_coord[0]-x2d)*map2D.globals.DISPLAY_W/w2d+offset);
				n.y = (int)((map2D.globals.y2D-screen_coord[1])*map2D.globals.DISPLAY_H/map2D.globals.height2D);
				int max_screen_width = (int)(map2D.globals.DISPLAY_W*Globals.MAP_WIDTH/map2D.globals.width2D);
				n.x_alt = n.x+max_screen_width;
				n.y_alt = n.y;
			} else if ( !firstNodes.contains(n) ) {
				n.x_alt = (int)((screen_coord[0]-x2d)*map2D.globals.DISPLAY_W/w2d+offset);
				n.y_alt = (int)((map2D.globals.y2D-screen_coord[1])*map2D.globals.DISPLAY_H/map2D.globals.height2D);
			}
			return;
		}
		
		
		if(offset==0)
			firstNodes.add(n);
		else if ( firstNodes.contains(n) )
			return;
		
		n.x = (int)((screen_coord[0]-x2d)*map2D.globals.DISPLAY_W/w2d+offset);
		n.y = (int)((map2D.globals.y2D-screen_coord[1])*map2D.globals.DISPLAY_H/map2D.globals.height2D);
	
		
		if(drawNodes==1){
		//System.out.println("[paintNode]   n.name=" +n.name+" n x= "+n.x+" n.y= "+n.y);
		if(map2D.globals.width2D<Globals.MAP_WIDTH/4){
			n.r=n.r-1+Globals.MAP_WIDTH/(Globals.MAP_WIDTH/4 + 1);
			n.fontsize=n.fontsize+2;
		}
		else
			n.r=n.r-3+Globals.MAP_WIDTH/((int)map2D.globals.width2D+1);
		
		if(biggernodes.contains(n.name)){
			n.r += 2;
			n.fontsize += 2;
		}
		
		int R = n.r;
		int x = n.x;
		int y = n.y;
		
		if(showShadow)
			createShadow(R*2, R*2);
		if(showBump & R>=0)
			createBump(R*2, R*2);
				
		Graphics2D g = (Graphics2D) graphics;
		g.setFont(new Font("Arial", Font.PLAIN, n.fontsize));
		
		int labelx = x+n.xLabelOffset;
		int labely = y+n.yLabelOffset;
		
		g.setColor(ColorFactory.getColor(100, 100, 100));
		//g.setColor(Color.BLACK);
		
		if(showShadow)
			g.drawImage(shadow,x-R+2,y-R+2,null);
		
		
		if(map2D.globals.width2D<Globals.MAP_WIDTH/2){
			for (int i=-1; i<=1; i++)
				for (int j=-1; j<=1; j++)
						g.drawString(n.name, labelx+i, labely+j);
		
			g.setColor(Color.WHITE);
		
			g.drawString(n.name, labelx, labely);
		}
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		if(n.data == null || routers.contains(n.name)){
			// paint as router
			Stroke oldStroke = g.getStroke();
			g.setStroke( new BasicStroke(2f));

			g.setColor(ColorFactory.getColor(100, 100, 100));
			g.drawOval(x-R-1, y-R-1, 2*R+2, 2*R+2);
			g.setColor(routerColor);
			g.fillOval(x-R, y-R, 2*R, 2*R);
			if( farmrouters.contains(n.name) ){
				if( (n.data==null || n.data.size() == 0) && n.alternate==null)
					g.setColor(Color.RED);
				else
					g.setColor(Color.YELLOW);
			}
			else
				g.setColor(Color.WHITE);
			g.drawOval(x-R, y-R, 2*R, 2*R);
			
			g.setStroke(oldStroke);
			
			g.setColor(Color.WHITE);
			g.drawLine(x-1, y-1, x-R+3, y-R+3);
			g.drawLine(x-1, y+1, x-R+3, y+R-3);
			g.drawLine(x+1, y-1, x+R-3, y-R+3);
			g.drawLine(x+1, y+1, x+R-3, y+R-3);
		}else if(n.data.size() == 0){
			// paint in default color
			g.setColor(n.alternate==null ? defNodeColor : nodataColor);
			g.fillOval(x-R, y-R, 2*R, 2*R);
			//g.setColor(n.alternate==null ? Color.RED : Color.ORANGE);
			g.setColor(Color.BLACK);
			g.drawOval(x-R, y-R, 2*R, 2*R);
		}else if(n.data.size() == 1){
			// paint as color scaled value
			double val = ((Double) n.data.get(0)).doubleValue();
			if(val==0){
				if(getColor(prop, "color.zerovalue", null)==null)
					g.setColor(getScaledColor(val, "Node"));
				else
					g.setColor(zerovalueColor);
			}
			else
				g.setColor(getScaledColor(val, "Node"));	
			g.fillOval(x-R, y-R, 2*R, 2*R);
			//g.setColor(Color.RED);
			g.setColor(Color.BLACK);
			g.drawOval(x-R, y-R, 2*R, 2*R);
		}else{
		    try{
			// TODO: paint as pie
			double dsum = 0;
			
			for (int i=0; i<n.data.size(); i++){
			    dsum += ((Double) n.data.get(i)).doubleValue();
			}
			
			if (dsum<=1E-10){
				if(getColor(prop, "color.zerovalue", null)==null)
					g.setColor(Color.WHITE);
				else
					g.setColor(zerovalueColor);
			    
			    g.fillOval(x-R, y-R, 2*R, 2*R);
			}
			else{
			    int uo = 0;
			
			    for (int i=0; i<n.data.size(); i++){
				double val = ((Double) n.data.get(i)).doubleValue();
			    
				int u = (int) ((val/dsum)*360D);
			    
				u = uo+u>360 ? 360-uo : u;
			    
				//Color c = getColor(prop, sName+".color", (Color)default_paint_sequence[colorIndex % default_paint_sequence.length]);
				
				g.setColor(n.colors[i]);
				g.fillArc(x-R, y-R, 2*R, 2*R, uo, u);
			    
				uo += u;
			    }
			}

			g.setColor(Color.BLACK);
			g.drawOval(x-R, y-R, 2*R, 2*R);
		    }
		    catch (Exception e){
		        System.err.println("Caught : "+e+" : "+e.getMessage());
		        e.printStackTrace();
		    }
			
		}
		
		if(showBump)
			g.drawImage(bump, x-R+2, y-R+2, null);
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		}

	}
}
