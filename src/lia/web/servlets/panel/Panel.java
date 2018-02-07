package lia.web.servlets.panel; 

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.swing.ImageIcon;

import lia.Monitor.Store.Cache;
import lia.Monitor.monitor.Result;
import lia.web.servlets.web.Utils;
import lia.web.servlets.web.genimage;
import lia.web.utils.CacheServlet;
import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.Page;

import org.jfree.chart.ChartUtilities;


public class Panel extends CacheServlet{
	
	String sResDir = "";
    String sConfDir = "";
    public static String sClassesDir="";
    
    public int WIDTH = 800;
    public int HEIGHT = 600;
    
    public Hashtable colorScales;
	public Hashtable valScales;
	
	public Double[] linksdata = new Double[100];
	
	transient Page pMaster;
	Page p;
	Properties prop;
	
	
    
    
    protected long getCacheTimeout(){
    	return 0;	
    }
        
    public final void doInit() {
            ServletContext sc = getServletContext();
            prop = Utils.getProperties(sConfDir, gets("page"));
            
            sResDir = sc.getRealPath("/");
            if (!sResDir.endsWith("/"))
                sResDir += "/";
            
            sConfDir = sResDir + "WEB-INF/conf/";
            sClassesDir = sResDir + "WEB-INF/classes/";
            sResDir += "WEB-INF/res/";
            response.setContentType("text/html");
            pMaster = new Page(osOut, sResDir + "masterpage/masterpage.res");
            p = new Page(osOut, sResDir + "panel/panel.res");
    }	
    
    public ImageIcon loadIcon(String resource) {
        ImageIcon ico = null;
        //ClassLoader myClassLoader = getClass().getClassLoader();
        try {
            //URL resLoc = myClassLoader.getResource(resource);
            //System.out.println(resource+" "+resLoc);
            ico = new ImageIcon(resource);
        } catch (Exception ex) {
                ex.printStackTrace();
        }

        return ico;
    }

    public Object[][] links = new Object[][] {
            //global links
            new Object[] {"Router1_2_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/CHI-GVA_IN"),Integer.valueOf(254), Integer.valueOf(266), ColorFactory.getColor(123,45,67)},
            new Object[] {"Router1_2_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/CHI-GVA_OUT"),Integer.valueOf(362), Integer.valueOf(126), ColorFactory.getColor(78,56,234)},
            new Object[] {"Router1_3_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/NY-GVA_IN"),Integer.valueOf(549), Integer.valueOf(272), ColorFactory.getColor(255,45,67)},
            new Object[] {"Router1_3_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/NY-GVA_OUT"),Integer.valueOf(450), Integer.valueOf(129), ColorFactory.getColor(255,255,0)},
            new Object[] {"Router2_3_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/CHI-NY_OUT"),Integer.valueOf(514), Integer.valueOf(305), ColorFactory.getColor(123,145,255)},
            new Object[] {"Router2_3_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/CHI-NY_IN"),Integer.valueOf(305), Integer.valueOf(305), ColorFactory.getColor(78,255,234)},
            //router 2 links
            new Object[] {"Router2_Cisco_NLR_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Cisco_NLR_OUT"),Integer.valueOf(114), Integer.valueOf(275), ColorFactory.getColor(100,20,30)},
            new Object[] {"Router2_Cisco_NLR_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Cisco_NLR_IN"),Integer.valueOf(192), Integer.valueOf(294), ColorFactory.getColor(200,30,40)},
            new Object[] {"Router2_ESNet_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/ESNet_OUT"),Integer.valueOf(124), Integer.valueOf(338), ColorFactory.getColor(130,40,50)},
            new Object[] {"Router2_ESNet_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/ESNet_IN"),Integer.valueOf(192), Integer.valueOf(323), ColorFactory.getColor(140,50,60)},
            new Object[] {"Router2_FNAL_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/FNAL_OUT"),Integer.valueOf(114), Integer.valueOf(402), ColorFactory.getColor(150,60,70)},
            new Object[] {"Router2_FNAL_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/FNAL_IN"),Integer.valueOf(204), Integer.valueOf(345), ColorFactory.getColor(160,70,80)},
            new Object[] {"Router2_MiLR_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/MiLR_OUT"),Integer.valueOf(125), Integer.valueOf(460), ColorFactory.getColor(170,80,90)},
            new Object[] {"Router2_MiLR_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/MiLR_IN"),Integer.valueOf(225), Integer.valueOf(355), ColorFactory.getColor(180,90,100)},
            new Object[] {"Router2_Starlight_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Starlight_OUT"),Integer.valueOf(220), Integer.valueOf(450), ColorFactory.getColor(90,100,110)},
            new Object[] {"Router2_Starlight_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Starlight_IN"),Integer.valueOf(245), Integer.valueOf(355), ColorFactory.getColor(100,110,120)},
            new Object[] {"Router2_Ultralight_CHI_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Ultralight_CHI_OUT"),Integer.valueOf(295), Integer.valueOf(480), ColorFactory.getColor(110,120,130)},
            new Object[] {"Router2_Ultralight_CHI_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/Ultralight_CHI_IN"),Integer.valueOf(270), Integer.valueOf(350), ColorFactory.getColor(120,130,140)},
            new Object[] {"Router2_UWisconsin_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/UWisconsin_OUT"),Integer.valueOf(370), Integer.valueOf(445), ColorFactory.getColor(130,140,150)}
,			new Object[] {"Router2_UWisconsin_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/UWisconsin_IN"),Integer.valueOf(290), Integer.valueOf(345), ColorFactory.getColor(140,150,160)},
            new Object[] {"Router2_USNet_OUT", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/USNet_OUT"),Integer.valueOf(375), Integer.valueOf(375), ColorFactory.getColor(150,160,170)},
            new Object[] {"Router2_USNet_IN", new String("CERN-US_WAN/WAN/192.65.196.252/-1/-1/USNet_IN"),Integer.valueOf(300), Integer.valueOf(325), ColorFactory.getColor(160,170,180)},
//          router 3 links
            new Object[] {"Router3_MANLAN_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/MANLAN_OUT"),Integer.valueOf(535), Integer.valueOf(395), ColorFactory.getColor(205,255,250)},
            new Object[] {"Router3_MANLAN_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/MANLAN_IN"),Integer.valueOf(555), Integer.valueOf(345), ColorFactory.getColor(230,230,50)},
            new Object[] {"Router3_ColumbiaNevis_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/ColumbiaNevis_OUT"),Integer.valueOf(580), Integer.valueOf(450), ColorFactory.getColor(200,200,50)},
            new Object[] {"Router3_ColumbiaNevis_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/ColumbiaNevis_IN"),Integer.valueOf(580), Integer.valueOf(355), ColorFactory.getColor(160,160,50)},
            new Object[] {"Router3_Ultralight_NY_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/Ultralight_NY_OUT"),Integer.valueOf(660), Integer.valueOf(485), ColorFactory.getColor(100,100,50)},
            new Object[] {"Router3_Ultralight_NY_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/Ultralight_NY_IN"),Integer.valueOf(605), Integer.valueOf(355), ColorFactory.getColor(170,255,170)},
            new Object[] {"Router3_Cisco_7606_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/Cisco_7606_OUT"),Integer.valueOf(690), Integer.valueOf(410), ColorFactory.getColor(170,45,170)},
            new Object[] {"Router3_Cisco_7606_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/Cisco_7606_IN"),Integer.valueOf(625), Integer.valueOf(340), ColorFactory.getColor(78,220,220)},
            new Object[] {"Router3_UBuffalo_OUT", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/UBuffalo_OUT"),Integer.valueOf(688), Integer.valueOf(318), ColorFactory.getColor(123,145,137)},
            new Object[] {"Router3_UBuffalo_IN", new String("CERN-US_WAN/WAN/192.65.196.251/-1/-1/UBuffalo_IN"),Integer.valueOf(630), Integer.valueOf(310), ColorFactory.getColor(143,200,200)},
    };

    public void updateLinksData(){
    	
    	for ( int i=0; i<links.length; i++) {
    		Object o = Cache.getLastValue(toPred(links[i][1].toString()));
    		if (o!=null && (o instanceof Result) && ((Result)o).param!=null && ((Result)o).param.length>0)
    			linksdata[i]=Double.valueOf(((Result)o).param[0]);
    	}
    	
    }
    
    
    /**
     * for each link specified in {@link links} array, it replaces the color
     * found in image with the one specified
     * @author mluc
     * @since May 14, 2006
     * @param g
     */
    private void drawLinks(BufferedImage bi, Graphics2D g) {
            int x, y;
            int newColor;
            int oldColor;
            for ( int i=0; i<links.length; i++) {
                    x = ((Integer)links[i][2]).intValue();
                    y = ((Integer)links[i][3]).intValue();
                    //newColor = ((Color)links[i][4]).getRGB();
                    newColor = getScaledColor(linksdata[i].doubleValue(),"Bandwidth").getRGB();
                    oldColor = bi.getRGB(x, y);
                    drawColor( links[i][0].toString(), bi, g, oldColor, newColor, x, y);
            }
    }

    /**
     * changes all adjacent pixels to the start one from oldColor to newColor.
     * For that, it uses a queue in which the pixels to be checked are put in,
     * starting with StartPos. While there are pixels in queue, the algorithm
     * continues. A pixel is added to queue if it has the same color as the
     * one that is doing the checking. IT IS an iterative algorithm.
     * @author mluc
     * @since May 14, 2006
     * @param g
     * @param oldColor
     * @param newColor
     * @param xStartPos
     * @param yStartPos
     */
    private void drawColor(String linkName, BufferedImage bi, Graphics2D g, int oldColor, int newColor, int xStartPos, int yStartPos) {
            LinkedList l = new LinkedList();
            l.add(new int[] {xStartPos,yStartPos});
            int next[], aux[];
            int width = bi.getWidth();
            int height = bi.getHeight();
            int x, y;
            int counter=0;
            //color for border
            Color c = ColorFactory.getColor(newColor);
            int darkColor = c.darker().getRGB();
            //the array removed from queue, use it again
            boolean bNextUsed=false;
            int cNeig[]=new int[4];
            boolean bDarker = false;
            while ( l.size()>0 ) {
                next = (int[])l.removeFirst();
                counter++;
                bNextUsed=false;
                x=next[0];
                y=next[1];
//              System.out.println("processing ["+x+","+y+"]");
                cNeig[0] = bi.getRGB(x-1, y);
                cNeig[1] = bi.getRGB(x+1, y);
                cNeig[2] = bi.getRGB(x, y-1);
                cNeig[3] = bi.getRGB(x, y+1);
                //this check is not needed because all that are in list have oldColor
//              if ( bi.getRGB(xStartPos, yStartPos) == oldColor ) {
                //check to see if this is a marginal point
                //if at least one color of the four is not new/oldColor, draw darker
                bDarker = false;
                for ( int i=0; i<4; i++)
                        if ( cNeig[i]!= newColor && cNeig[i]!=oldColor && cNeig[i]!=darkColor ) {
                                bDarker=true;
                                break;
                        }
                if ( bDarker )
                        bi.setRGB(x, y, darkColor);
                else
                        bi.setRGB(x, y, newColor);
//              System.out.println("oldcolor "+oldColor+", newcolor "+bi.getRGB(x, y));
                if ( x>0 && cNeig[0] == oldColor ) {
                        aux = !bNextUsed?next:new int[2];
                        aux[0]=x-1;aux[1]=y;
                        if ( !contains(l, aux) ) {
                                bNextUsed=true;
                                l.add(aux);
                        } else if ( bNextUsed ) {
                                bNextUsed=false;
                                next = aux;
                        }
                }
                if ( x<width-1 && cNeig[1] == oldColor ) {
                    aux = !bNextUsed?next:new int[2];
                    aux[0]=x+1;aux[1]=y;
                    if ( !contains(l, aux) ) {
                            bNextUsed=true;
                            l.add(aux);
                    } else if ( bNextUsed ) {
                            bNextUsed=false;
                            next = aux;
                    }
            }
            if ( y>0 && cNeig[2] == oldColor ) {
                    aux = !bNextUsed?next:new int[2];
                    aux[0]=x;aux[1]=y-1;
                    if ( !contains(l, aux) ) {
                            bNextUsed=true;
                            l.add(aux);
                    } else if ( bNextUsed ) {
                            bNextUsed=false;
                            next = aux;
                    }
            }
            if ( y<height-1 && cNeig[3] == oldColor ) {
                    aux = !bNextUsed?next:new int[2];
                    aux[0]=x;aux[1]=y+1;
                    if ( !contains(l, aux) ) {
                            bNextUsed=true;
                            l.add(aux);
                    } else if ( bNextUsed ) {
                            bNextUsed=false;
                            next = aux;
                    }
            }
    }
    System.out.println("For "+linkName+" "+counter+" pixels were changed.");
    }

    /**
     * checks to see if the specified element is already in the list
     * by verifying the two ints
     * @author mluc
     * @since May 14, 2006
     * @param l
     * @param elem
     * @return
     */
    private boolean contains(LinkedList l, int[] elem) {
            for ( ListIterator li=l.listIterator(); li.hasNext(); ) {
                    int[] e = (int[])li.next();
                    if ( e[0]==elem[0] && e[1]==elem[1] )
                            return true;
            }
            return false;
    }
    
    private void drawLegend(Graphics2D g){
	    //Graphics2D g = (Graphics2D) graphics;
	
	    if (genimage.pgetb(prop, "Legend.display", false)){
		
		int w = genimage.pgeti(prop, "Legend.position.width", 300);
		int h = genimage.pgeti(prop, "Legend.position.height", 30);
		
		int x = genimage.pgeti(prop, "Legend.position.x", 500);
		int y = genimage.pgeti(prop, "Legend.position.y", 370);
		
		
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		g.setPaint(ColorFactory.getColor(200, 200, 200));
		g.draw(new Rectangle2D.Double(x, y, w, h));
		
		int x2 = genimage.pgeti(prop, "Legend.gradient.x", 150);
		int y2 = genimage.pgeti(prop, "Legend.gradient.y", 8);
		int w2 = genimage.pgeti(prop, "Legend.gradient.width", 80);
		int h2 = genimage.pgeti(prop, "Legend.gradient.height", 14);
		
		String sParameter = genimage.pgets(prop, "Legend.parameter.name", "Delay");
		String sAlias     = genimage.pgets(prop, "Legend.parameter.alias", sParameter);
		
		String sSuffix    = Formatare.replace(genimage.pgets(prop, "Legend.suffix", ""), "_", " ");
		
		Color cMin = (Color) colorScales.get(sParameter+"_min");
		Color cMax = (Color) colorScales.get(sParameter+"_max");
		
		GradientPaint gpaint = new GradientPaint(x+x2, y+y2, cMin, x+x2+w2, y+y2, cMax);
		
		g.setPaint(gpaint);
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.fill(new Rectangle2D.Double(x+x2, y+y2, w2, h2));
		//g.draw3DRect(x+x2,y+y2,w2,h2,true);
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		double dMin = ((Double) valScales.get(sParameter+"_min")).doubleValue();
		double dMax = ((Double) valScales.get(sParameter+"_max")).doubleValue();
		
		if (dMin>dMax){
		    dMin=0;
		    dMax=0;
		}
	    		
		g.setPaint(Color.BLACK);
		
		int xl = genimage.pgeti(prop, "Legend.label.x", 7);
		int yl = genimage.pgeti(prop, "Legend.label.y", 19);
		
		String sSeparator = Formatare.replace(genimage.pgets(prop, "Legend.separator", "_"), "_", " ");
		
		g.drawString(sAlias+sSeparator+DoubleFormat.point(dMin)+sSuffix, x+xl, y+yl);
		g.drawString(DoubleFormat.point(dMax)+sSuffix, x+x2+w2+5, y+yl);
		
		g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF );
	    }
	}
    
    
    
    private void adjustLinkMinMax(String what){
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for(int i=0; i<links.length; i++){
			//Hashtable data = ((WLink1)links.get(i)).data;
            Object obj = linksdata[i];
            Double val=null;
            if ( obj instanceof Double )
    			val = (Double) obj;
            if(val != null){
                double v = val.doubleValue();
                min = (v < min ? v : min);
                max = (v > max ? v : max);
            }
		}
		valScales.put(what+"_min", Double.valueOf(min));
		valScales.put(what+"_max", Double.valueOf(max));
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
	
    
    
    
    public void execGet(){
    
    try{
    	   	
    	
    	
    	colorScales = new Hashtable();
    	colorScales.put("Bandwidth_min", getColor(prop, "Bandwidth.color.min", ColorFactory.getColor(255, 255, 0)));
		colorScales.put("Bandwidth_max", getColor(prop, "Bandwidth.color.max", ColorFactory.getColor(0, 255, 100)));

		valScales = new Hashtable();
    	
		updateLinksData();
		adjustLinkMinMax("Bandwidth");
		
    	ImageIcon icon = loadIcon(sClassesDir+"img/all_traffic.png");
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        BufferedImage bi = new BufferedImage(w, h,BufferedImage.TYPE_INT_RGB);
        Graphics2D imageGraphics = bi.createGraphics();
        imageGraphics.drawImage(icon.getImage(), 0, 0, null);
        drawLinks(bi, imageGraphics);
        drawLegend(imageGraphics);

        //ServletOutputStream out = response.getOutputStream();
        //response.setContentType("image/png");	
        //ImageIO.write(bi, "png", out);
        
        
  
        File tempFile = File.createTempFile("panel-", ".png", new File(System.getProperty("java.io.tmpdir")));
        OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
        ChartUtilities.writeBufferedImageAsPNG(out, bi);

        out.flush();
        out.close();

        lia.web.servlets.web.display.registerImageForDeletion(tempFile.getName(), getCacheTimeout());
        
        p.modify("image",tempFile.getName());
        
        
        pMaster.append(p);

		//initMasterPage(pMaster, prop, sResDir);

		pMaster.write();
        
        //p.write();        
    	
    } catch ( Throwable ex) {
        
            ex.printStackTrace();
            }
    }
    
    
    
    
    
    
    

}
