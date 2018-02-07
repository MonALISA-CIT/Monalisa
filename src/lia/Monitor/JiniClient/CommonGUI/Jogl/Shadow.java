package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.image.BufferedImage;
import java.util.Calendar;
import java.util.TimeZone;

import lia.Monitor.JiniClient.CommonGUI.Jogl.util.FMath;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.Sun;
import lia.Monitor.monitor.AppConfig;

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
    
    public static BufferedImage biEarthNight = null;
    public static int nNightWidth = 0;
    public static int nNightHeight = 0;
    
    public static boolean bHideLights = false;
    public static boolean bShowNight = true;
    
    private static Object setShadow_sync = new Object(); 
	
	static {
		calendar  = Calendar.getInstance();
		TimeZone tz = TimeZone.getTimeZone("GMT");
		calendar.setTimeZone(tz);
		biEarthNight = Globals.loadBuffImage("lia/images/earth_night1024x512.jpg");
        if ( biEarthNight !=null ) {
            nNightWidth = biEarthNight.getWidth();
            nNightHeight = biEarthNight.getHeight();
        };
        String val = AppConfig.getProperty("jogl.hide_lights", "false");
        if ( val!=null && ( val.equals("1") || val.toLowerCase().equals("true") ) ) {
            bHideLights = true;
        };
	}
	
	public static void setBrightness( byte[] data, int width, int height, float intensity)
	{
	    if ( intensity < 0 )
	        intensity = 0;
	    int length = width*height*3;
	    if ( data.length < length )
	        return;
	    float value, int2;
//	    int color;
	    int index;
//        float min_factor=255, max_factor=0, avg_factor=0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
			    index = 3*x+3*y*width;
			    int2 = 1f - ((int)(data[index]&0xff)+(int)(data[index+1]&0xff)+(int)(data[index+2]&0xff))/500f;
			    if ( int2 < 0 ) int2 = 0f;
			    int max = (int)(data[index]&0xff);
			    int g = (int)(data[index+1]&0xff);
			    int b = (int)(data[index+2]&0xff);
			    max = (max<g)?g:max;
			    max = (max<b)?b:max;
                if ( max==0 ) max=1;
			    float factor = (1+int2*(intensity-1));
			    int d = (int)(max*factor);
			    if (d>255)
			        factor = 255.0f/max;
//                if ( min_factor>factor )
//                    min_factor=factor;
//                if ( max_factor<factor )
//                    max_factor=factor;
//                avg_factor += factor;
			    //color = (int)((float)color*(1+value*(1-intensity)));
//                float factor=1.5f;
			    value = (float)((int)(data[index]&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    data[index] = (byte)value;
			    value = (float)((int)(data[index+1]&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    data[index+1] = (byte)value;
			    value = (float)((int)(data[index+2]&0xff)*factor);
			    if ( value > 255 )
			        value = 255;
			    data[index+2] = (byte)value;
			}
		};
//        avg_factor /= (width*height);
//        System.out.println("Average factor: "+avg_factor+"Minimum factor: "+min_factor+" Maximum factor: "+max_factor);
	}
    
    public static void setBrightness( BufferedImage bi, float intensity)
    {
        if ( intensity < 0 )
            intensity = 0;
        int width = bi.getWidth();
        int height = bi.getHeight();
        float value, int2;
        int color;
        int red, green, blue;
        float min_factor=255, max_factor=0, avg_factor=0, sum_factor=0;
        int count =0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                color = bi.getRGB(x, y);
                red = ((color>>16)&0xff);
                green = ((color>>8)&0xff);
                blue = (color&0xff);
                int2 = 1f - ( red + green + blue )/500f;
                if ( int2 < 0 ) int2 = 0f;
                int max = 1;
                max = (max<red)?red:max;
                max = (max<green)?green:max;
                max = (max<blue)?blue:max;
                float factor = (1+int2*(intensity-1));
                int d = (int)(max*factor);
                if (d>255)
                    factor = 255.0f/max;
                if ( min_factor > factor )
                    min_factor = factor;
                if ( max_factor < factor )
                    max_factor = factor;
                sum_factor += factor;
                count ++;
                //color = (int)((float)color*(1+value*(1-intensity)));
                value = red*factor;
                if ( value > 255 )
                    value = 255;
                red = (int)value;
                value = green*factor;
                if ( value > 255 )
                    value = 255;
                green = (int)value;
                value = blue*factor;
                if ( value > 255 )
                    value = 255;
                blue = (int)value;
                color = 0xff000000 + (red<<16)+(green<<8)+blue;
                bi.setRGB( x, y, color);
            }
        };
        avg_factor = sum_factor/width/height;
        //System.out.println(" Average factor: "+avg_factor+" Minimum factor: "+min_factor+" Maximum factor: "+max_factor);
    }
	
	public static void setShadow( byte[] data, int width, int height, int startX, int startY, int full_width, int full_height, int flags){
//		double declination;
//		double rightAscension;
//		double GST;
		
		
		calendar.setTime( JoglPanel.globals.currentTime/*new Date()*/);
		double t1 = (double) calendar.get(Calendar.HOUR_OF_DAY) ;
		double t2  = (double) calendar.get(Calendar.MINUTE) /60D;
		double t3 = (double ) calendar.get(Calendar.SECOND) /3600D;

		double t = ( t1+t2+t3 ) /24.0D; 
		double CURRENTTIME = t;
		int CURRENTDAYNUMBER = calendar.get(Calendar.DAY_OF_YEAR); 
//		double CURRENTDATE = (double)CURRENTDAYNUMBER + CURRENTTIME;

		synchronized (setShadow_sync) {
			//setBrightness( data, width, height, 1.7f);

			setShadow(Sun.getDeclination(CURRENTDAYNUMBER),
					  Sun.getRightAscension(CURRENTDAYNUMBER),
					  Sun.getGST(CURRENTDAYNUMBER, CURRENTTIME), data, width, height, startX, startY, full_width, full_height, !Texture.checkFlagIsSet( flags, Texture.S_SPECIAL_NO_LIGHTS));
		}
	}
	
	public static void setShadow( BufferedImage bi, float brightness)
	{
	    int width = bi.getWidth();
	    int height = bi.getHeight();
//		double declination;
//		double rightAscension;
//		double GST;
		
		
		calendar.setTime( JoglPanel.globals.currentTime/*new Date()*/);
		double t1 = (double) calendar.get(Calendar.HOUR_OF_DAY) ;
		double t2  = (double) calendar.get(Calendar.MINUTE) /60D;
		double t3 = (double ) calendar.get(Calendar.SECOND) /3600D;

		double t = ( t1+t2+t3 ) /24.0D; 
		double CURRENTTIME = t;
		int CURRENTDAYNUMBER = calendar.get(Calendar.DAY_OF_YEAR); 
//		double CURRENTDATE = (double)CURRENTDAYNUMBER + CURRENTTIME;
		
		byte data[]=new byte[width*3];
		int data_aux[] = new int[width];
		for ( int j=0; j<height; j++ ) {
		    bi.getRGB( 0, j, width, 1, data_aux, 0, width);
		    for( int i=0; i<width; i++) {
		        data[i*3] = (byte)((data_aux[i]>>16)&0xff);
		        data[i*3+1] = (byte)((data_aux[i]>>8)&0xff);
		        data[i*3+2] = (byte)((data_aux[i])&0xff);
		    }

			setBrightness( data, width, height, brightness);

			setShadow(Sun.getDeclination(CURRENTDAYNUMBER),
					  Sun.getRightAscension(CURRENTDAYNUMBER),
					  Sun.getGST(CURRENTDAYNUMBER, CURRENTTIME), data, width, 1, 0, j, width, height, false);

		    for( int i=0; i<width; i++) {
		        data_aux[i] = (((int)(data[3*i]&0xff))<<16) | (((int)(data[3*i+1]&0xff))<<8) | ((int)(data[3*i+1]&0xff));
		    }
			bi.setRGB(0, j, width, 1, data_aux, 0, width);
		}
	}

//    private static void setShadow(double declination, double rightAscension, double GST, byte[] data, int width, int height, int startX, int startY, int full_width, int full_height) {
//        setShadow( declination, rightAscension, GST, data, width, height, startX, startY, full_width, full_height, false);
//    }
	
	private static void setShadow(double declination, double rightAscension, double GST, byte[] data, int width, int height, int startX, int startY, int full_width, int full_height, boolean bUseLights) {

		int imgWidth = width;
		int imgHeight = height;

		GST *= PI2;
		if (GST > PI)
			GST = - (PI2 - GST);
		double diff = GST - rightAscension;
		double sinDec = Math.sin(declination);
		double cosDec = Math.cos(declination);
		
//		float fRapW = (float)nNightWidth/(float)full_width;
//		float fRapH = (float)nNightHeight/(float)full_height;

		//System.out.println("nrImgPixelsX="+nrImgPixelsX+" nrImgPixelsY="+nrImgPixelsY);
		//System.out.println("startX="+startX+", startY="+startY+", width="+width+", height="+height+", full_width="+full_width+", full_height="+full_height);

		for (int y = 0; y < imgHeight; y++) {
			double sinLat = Math.sin( - ((PI / (double) (full_height - 1)) * (double) (imgHeight-y+startY) - PId2));
			double cosLat =	Math.cos( - ((PI / (double) (full_height - 1)) * (double) (imgHeight-y+startY) - PId2));
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

				int dayTemp = ((data[x*3+y*imgWidth*3] & 0xFF)<<16)+((data[x*3+y*imgWidth*3+1] & 0xFF)<<8)+(data[x*3+y*imgWidth*3+2] & 0xFF);//buffImgLine[x];
				// compute shadowed color
                int nightTemp = dayTemp;
                if ( !bUseLights ) {
                    nightTemp = ((((dayTemp & 0xFF0000)>>16) / 2 ) << 16) |
								((((dayTemp & 0x00FF00)>> 8) / 2 ) <<  8) |
								((((dayTemp & 0x0000FF)>> 0) / 2 ) <<  0) 
                                /*| (dayTemp & 0xFF000000)*/;
                } else if ( biEarthNight!=null ) {
                    int nrImgPixelsX = full_width/nNightWidth;
                    int nrImgPixelsY = full_height/nNightHeight;

                    int nightX = (startX+x)/nrImgPixelsX;
    				int nightY = (startY+imgHeight-1-y)/nrImgPixelsY;
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
    				int deplY = y_start+nrImgPixelsY/2-(startY+imgHeight-1-y);
    				float depl = deplX*deplX+ deplY*deplY+1;
    				if ( depl > 1 )
    				    fIntensity /= depl;
    				    
    				nightTemp = (int) ( ((int)((dayTemp & 0xFF0000) >> 16) * .4 + ((nightTemp & 0xFF0000)>>16) * 0.5/**fRapW*fRapH*/*fIntensity)) << 16 |
    									(int) ( ((int)((dayTemp & 0xFF00)>>8) * .4 + ((nightTemp & 0xFF00)>>8)* 0.5/**fRapW*fRapH*/*fIntensity)) << 8 |
    									(int) ( ((int)((dayTemp & 0xFF)) * .4 + (nightTemp & 0xFF) * 0.5/**fRapW*fRapH*/*fIntensity) );
                };
				if (altitude < -0.065449846949787352D) {
					data[x*3+y*imgWidth*3] = (byte)((nightTemp&0xFF0000)>>16);
					data[x*3+y*imgWidth*3+1] = (byte)((nightTemp&0x00FF00)>>8);
					data[x*3+y*imgWidth*3+2] = (byte)(nightTemp&0xFF);
				} else if (altitude < 0.065449846949787352D) {
					int day = dayTemp & 0xff0000;
					int night = nightTemp & 0xff0000;
					int red = 0xff0000 & (int) ((altitude * (double) (day - night)) 
							/ 0.1308996938995747D + (double) (day + night) / 2D);
					day = dayTemp & 0xff00;
					night = nightTemp & 0xff00;
					int green =	0xff00 & (int) ((altitude * (double) (day - night))
								/ 0.1308996938995747D + (double) (day + night) / 2D);
					day = dayTemp & 0xff;
					night = nightTemp & 0xff;
					int blue = 0xff	& (int) ((altitude * (double) (day - night))
								/ 0.1308996938995747D + (double) (day + night) / 2D);
					data[x*3+y*imgWidth*3] = (byte)(red>>16);
					data[x*3+y*imgWidth*3+1] = (byte)(green>>8);
					data[x*3+y*imgWidth*3+2] = (byte)blue;
					//buffImgLine[x] = 0xff000000 | red | green | blue;
				}
			}
			//destBI.setRGB(0, y, imgWidth, 1, buffImgLine, 0, imgWidth);
		}
	}
	
}
