package lia.web.servlets.map2d;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.ImageIcon;

//import lia.Monitor.JiniClient.CommonGUI.Jogl.util.FMath;

//import lia.Monitor.JiniClient.CommonGUI.Jogl.util.FMath;
//import lia.Monitor.JiniClient.CommonGUI.Jogl.util.Sun;
//import lia.Monitor.monitor.AppConfig;

/*
 * Created on May 13, 2004
 *
 */
/**
 * @author mluc
 *
 * contains functions to set shadow on an image
 */
public class Shadow 
{
	private static Calendar calendar;

    public static final double PI = 3.1415926535897931D;
    public static final double PId2 = 1.5707963267948966D;
    public static final double PI2 = 6.2831853071795862D;
    
    public static final BufferedImage biEarthNight ;
    public static final int nNightWidth;
    public static final int nNightHeight;
    
    public static final boolean bHideLights;
	
	public static BufferedImage loadBuffImage(String path)
	{
		BufferedImage bi=null;
		
		BufferedInputStream bis = null;
		
		try {
			File fImage = new File(path);
			long file_length = fImage.length();
			byte data[] = new byte[(int)file_length];
			bis = new BufferedInputStream(new FileInputStream(fImage));
			int r = bis.read(data);				
			bis.close();
			bis = null;
			
			if (r!=data.length){
				System.err.println("r!=len : "+r+" ... "+data.length);
			
				return null;
			}
			
			ImageIcon icon = new ImageIcon(data);
			
//			
//			if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
//				throw new Exception("failed");
//			}
			Component obs = new Component() { private static final long serialVersionUID = 1L; };
			int width = icon.getIconWidth();
			int height = icon.getIconHeight();
			bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D big = bi.createGraphics();
			icon.paintIcon(obs, big, 0, 0);
			big.dispose();
		}catch(Throwable e){
			bi = null;
			System.out.println("\nFailed loading image from "+path);
			e.printStackTrace();
		}
		finally{
			if (bis!=null){
				try{
					bis.close();
				}
				catch (IOException ioe){
				}
			}
		}
		return bi;
	}

//	
	static {
		calendar  = Calendar.getInstance();
		TimeZone tz = TimeZone.getTimeZone("GMT");
		calendar.setTimeZone(tz);
		biEarthNight = loadBuffImage(FarmMap.sClassesDir+"lia/images/earth_night1024x512.jpg");
		if ( biEarthNight!=null ) {
	        nNightWidth = biEarthNight.getWidth();
	        nNightHeight = biEarthNight.getHeight();
	        bHideLights = false;
		} else {
			System.out.println("No night earth image with lights.");
			bHideLights = true;
			nNightHeight = nNightWidth = -1;
		}
	}
	
	public static void setBrightness( BufferedImage bi, int width, int height, float intensity)
	{
	    if ( intensity < 0 )
	        intensity = 0;
	    float value, int2;
	    int color;
		int red, green, blue;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				color = bi.getRGB(x, y);
				red = ((color>>16)&0xff);
				green = ((color>>8)&0xff);
				blue = (color&0xff);
			    int2 = 1f - ( (red&0xff) + (green&0xff) + (blue&0xff) )/500f;
			    if ( int2 < 0 ) int2 = 0f;
			    /** @author cipsm */
			    int max = red;
			    int g = green;
			    int b = blue;
			    max = (max<g)?g:max;
			    max = (max<b)?b:max;
			    float factor = (1+int2*(intensity-1));
			    int d = (int)(max*factor);
			    if (d>255)
			        factor = 255.0f/max;
			    //color = (int)((float)color*(1+value*(1-intensity)));
			    value = (float)((red&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    red = (int)value;
			    value = (float)((green&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    green = (int)value;
			    value = (float)((blue&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    blue = (int)value;
				color = 0xff000000 + (red<<16)+(green<<8)+blue;
				bi.setRGB( x, y, color);
			}
		};
	}
	
	public static void setShadow( BufferedImage bi, int width, int height, int startX, int startY, int full_width, int full_height, int show_lights){
//		double declination;
//		double rightAscension;
//		double GST;
		
		
		calendar.setTime( new Date());
		double t1 = (double) calendar.get(Calendar.HOUR_OF_DAY) ;
		double t2  = (double) calendar.get(Calendar.MINUTE) /60D;
		double t3 = (double ) calendar.get(Calendar.SECOND) /3600D;

		double t = ( t1+t2+t3 ) /24.0D; 
		double CURRENTTIME = t;
		int CURRENTDAYNUMBER = calendar.get(Calendar.DAY_OF_YEAR); 
//		double CURRENTDATE = (double)CURRENTDAYNUMBER + CURRENTTIME;

		//setBrightness( bi, width, height, 1.7f);

		setShadow(Sun.getDeclination(CURRENTDAYNUMBER),
				  Sun.getRightAscension(CURRENTDAYNUMBER),
				  Sun.getGST(CURRENTDAYNUMBER, CURRENTTIME), bi, width, height, startX, startY, full_width, full_height, show_lights);
	}
	
	private static void setShadow(double declination, double rightAscension, double GST, BufferedImage bi, int width, int height, int startX, int startY, int full_width, int full_height, int show_lights) {

		boolean bUseLights=(!bHideLights)&& (show_lights == 1);
		int imgWidth = width;
		int imgHeight = height;

		GST *= PI2;
		if (GST > PI)
			GST = - (PI2 - GST);
		double diff = GST - rightAscension;
		double sinDec = Math.sin(declination);
		double cosDec = Math.cos(declination);
		
		int nrImgPixelsX = full_width/nNightWidth;
		int nrImgPixelsY = full_height/nNightHeight;

		//System.out.println("nrImgPixelsX="+nrImgPixelsX+" nrImgPixelsY="+nrImgPixelsY);
		//System.out.println("startX="+startX+", startY="+startY+", width="+width+", height="+height+", full_width="+full_width+", full_height="+full_height);

		int color;
		for (int y = 0; y < imgHeight; y++) {
			double sinLat = Math.sin( - ((PI / (double) (full_height - 1)) * (double) (y+startY) - PId2));
			double cosLat =	Math.cos( - ((PI / (double) (full_height - 1)) * (double) (y+startY) - PId2));
			//srcBI.getRGB(0, y, imgWidth, 1, buffImgLine, 0, imgWidth);
			for (int x = 0; x < imgWidth; x++) {
				double temp = diff + (PI2 / (double) (full_width - 1)) * (double) (x+startX) - PI;
				if (temp < 0.0D)
					temp = -temp;
				if (temp > PI)
					temp = PI2 - temp;
				if (temp < 0.0D)
					temp = -temp;
				double cosHourAngle = FMath.Cos(temp);
				double altitude = FMath.ArcSin(sinLat * sinDec + cosLat * cosDec * cosHourAngle);

				color = bi.getRGB(x, y);
				int red, green, blue;
				red = color&0xff0000;
				green = color&0xff00;
				blue = color&0xff;
				int dayTemp = color;//((data[x*3+y*imgWidth*3] & 0xFF)<<16)+((data[x*3+y*imgWidth*3+1] & 0xFF)<<8)+(data[x*3+y*imgWidth*3+2] & 0xFF);//buffImgLine[x];
				// compute shadowed color
                int nightTemp;
                if ( !bUseLights ) {
                    nightTemp = ((((dayTemp & 0xFF0000)>>16) / 2 ) << 16) |
								((((dayTemp & 0x00FF00)>> 8) / 2 ) <<  8) |
								((((dayTemp & 0x0000FF)>> 0) / 2 ) <<  0) 
                                /*| (dayTemp & 0xFF000000)*/;
                } else {
    				int nightX = (startX+x)/nrImgPixelsX;
    				int nightY = (startY+y)/nrImgPixelsY;
					nightTemp = biEarthNight.getRGB( nightX, nightY);
    //				int red = ((nightTemp & 0xFF0000) >> 16);
    //				int green = ((nightTemp & 0xFF00) >> 8);
    //				int blue = ((nightTemp & 0xFF));
    //				nightTemp = (((int)((float)((nightTemp & 0xFF0000) >> 16)*fRapW*fRapH)) & 0xFF)<<16
    //									+ (((int)((float)((nightTemp & 0xFF00) >> 8)*fRapW*fRapH)) & 0xFF)<<8
    //									+ ((int)((float)((nightTemp & 0xFF))*fRapW*fRapH));
    				
    				int x_start = nightX*nrImgPixelsX;
    				int y_start = nightY*nrImgPixelsY;
    				float fIntensity =.6f;
    				int deplX = x_start+nrImgPixelsX/2-startX-x;
    				int deplY = y_start+nrImgPixelsY/2-(startY+y);
    				float depl = deplX*deplX+ deplY*deplY+1;
    				if ( depl > 1 )
    				    fIntensity /= depl;

    				    
    				nightTemp = (int) ( ((int)((dayTemp & 0xFF0000) >> 16) * .4 + ((nightTemp & 0xFF0000)>>16) * 0.5/**fRapW*fRapH*/*fIntensity)) << 16 |
    									(int) ( ((int)((dayTemp & 0xFF00)>>8) * .4 + ((nightTemp & 0xFF00)>>8)* 0.5/**fRapW*fRapH*/*fIntensity)) << 8 |
    									(int) ( ((int)((dayTemp & 0xFF)) * .4 + (nightTemp & 0xFF) * 0.5/**fRapW*fRapH*/*fIntensity) );
                };
				if (altitude < -0.065449846949787352D) {
					red = ((nightTemp&0xFF0000));
					green = ((nightTemp&0x00FF00));
					blue = (nightTemp&0xFF);
				} else if (altitude < 0.065449846949787352D) {
					int day = dayTemp & 0xff0000;
					int night = nightTemp & 0xff0000;
					red = 0xff0000 & (int) ((altitude * (double) (day - night)) 
							/ 0.1308996938995747D + (double) (day + night) / 2D);
					day = dayTemp & 0xff00;
					night = nightTemp & 0xff00;
					green =	0xff00 & (int) ((altitude * (double) (day - night))
								/ 0.1308996938995747D + (double) (day + night) / 2D);
					day = dayTemp & 0xff;
					night = nightTemp & 0xff;
					blue = 0xff	& (int) ((altitude * (double) (day - night))
								/ 0.1308996938995747D + (double) (day + night) / 2D);
					//buffImgLine[x] = 0xff000000 | red | green | blue;
				}
				color = 0xff000000 | red | green | blue;
				bi.setRGB( x, y, color);
			}
			//destBI.setRGB(0, y, imgWidth, 1, buffImgLine, 0, imgWidth);
		}
	}
	
}
