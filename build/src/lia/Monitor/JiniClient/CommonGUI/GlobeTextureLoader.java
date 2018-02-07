package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;

import lia.Monitor.JiniClient.CommonGUI.Mmap.FMath;
import lia.Monitor.JiniClient.CommonGUI.Mmap.Sun;
import lia.Monitor.monitor.AppConfig;

public final class GlobeTextureLoader {

    private static final Logger logger = Logger.getLogger(GlobeTextureLoader.class.getName());

    public static final double PI = 3.1415926535897931D;
    public static final double PId2 = 1.5707963267948966D;
    public static final double PI2 = 6.2831853071795862D;

    // logger

    // default texture filename, found in Client jar
    private static String defaultTextureFilename = "lia/images/earth_texture1024x512.jpg";

    // final texture filename
    private static String textureFilename;

    // buffered image
    private static BufferedImage buffImg;

    // night image
    private static BufferedImage buffNightImg;

    // day + night current image
    private static BufferedImage buffFinalImg;

    // current time
    Calendar calendar;
    private final int[] buffImgLine;
    private final int[] buffNightImgLine; // see setShadow

    // the one and only instance of this class
    private static GlobeTextureLoader texLoader = new GlobeTextureLoader();

    private GlobeTextureLoader() {
        textureFilename = AppConfig.getProperty("lia.Monitor.globeTexture", defaultTextureFilename);
        buffImg = loadSomeImage(textureFilename, true);
        textureFilename = textureFilename.replaceAll("earth_texture", "earth_night");
        buffNightImg = loadSomeImage(textureFilename, false);
        buffFinalImg = new BufferedImage(buffImg.getWidth(), buffImg.getHeight(), buffImg.getType());

        buffImgLine = new int[buffImg.getWidth()];
        buffNightImgLine = new int[buffImg.getWidth()];

        calendar = Calendar.getInstance();
        TimeZone tz = TimeZone.getTimeZone("GMT");
        calendar.setTimeZone(tz);

        setShadow();

        /*		try {
        			System.out.println("\n\nGlobeTextureLoader called\n\n");
        			throw new Exception();
        		} catch( Exception ex ) {
        		    ex.printStackTrace();
        		}
        */
        // build in background the final shadowed image
        TimerTask ttask = new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName(" ( ML ) - GlobeTextureLoader - setShadow Timer Thread");
                try {
                    setShadow();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        };
        BackgroundWorker.schedule(ttask, 5 * 60 * 1000, 5 * 60 * 1000);
    }

    private BufferedImage loadSomeImage(String fileName, boolean tryDefault) {

        if (fileName == null) {
            return null;
        }
        BufferedImage bi = null;

        ClassLoader myClassLoader = getClass().getClassLoader();
        try {
            URL imageURL;
            if (fileName.indexOf("://") >= 0) {
                imageURL = new URL(fileName);
            } else {
                imageURL = myClassLoader.getResource(fileName);
            }
            logger.log(Level.FINE, "Loading globe texture from " + fileName);
            bi = (fileName.endsWith(".raw") ? loadRAWImage(imageURL) : loadImage(imageURL));
            if (bi == null) {
                throw new Exception();
            }
        } catch (Exception ex) {
            if (tryDefault) {
                logger.log(Level.FINE, "Loading globe texture... " + fileName.substring(fileName.lastIndexOf("/")));
                URL imageURL = myClassLoader.getResource(defaultTextureFilename);
                bi = (defaultTextureFilename.endsWith(".raw") ? loadRAWImage(imageURL) : loadImage(imageURL));
            }
            if (bi == null) {
                logger.log(Level.WARNING,
                        "Failed loading any globe texture." + fileName.substring(fileName.lastIndexOf("/")));
            }
        }
        return bi;
    }

    private BufferedImage loadRAWImage(URL url) {
        BufferedImage res = null;
        try {
            //			System.out.println("Opening file: "+url.toString());
            InputStream is = url.openStream();
            int width = read3Bytes(is);
            int height = read3Bytes(is);
            //			System.out.println("Found image w="+width+" h="+height);
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            byte[] px3BytesColors = new byte[3 * width];
            int[] pxIntColors = new int[width];
            int base = 0;
            for (int i = 0; i < height; i++) {
                is.read(px3BytesColors);
                for (int j = 0; j < width; j++) {
                    base = 3 * j;
                    pxIntColors[j] = (0xff << 24) | ((0xff & px3BytesColors[base]) << 16)
                            | ((0xff & px3BytesColors[base + 1]) << 8) | (0xff & px3BytesColors[base + 2]);
                }
                res.setRGB(0, i, width, 1, pxIntColors, 0, width);
            }
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
            res = null;
        }
        return res;
    }

    private int read3Bytes(InputStream is) throws IOException {
        byte[] pxColor = new byte[3];
        is.read(pxColor);
        return (pxColor[0] << 16) | (pxColor[1] << 8) | (pxColor[2]);
    }

    public static BufferedImage loadImage(URL url) {
        BufferedImage res = null;
        try {
            ImageIcon icon = new ImageIcon(url);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed loading image from " + url.toString(), e);
        }
        return res;
    }

    public static final BufferedImage getBufferedImage() {
        return buffImg;
    }

    public static final BufferedImage getNightBufferedImage() {
        return buffNightImg;
    }

    void setShadow() {
        double declination;
        double rightAscension;
        double GST;

        calendar.setTime(new Date());
        double t1 = calendar.get(Calendar.HOUR_OF_DAY);
        double t2 = calendar.get(Calendar.MINUTE) / 60D;
        double t3 = calendar.get(Calendar.SECOND) / 3600D;

        double t = (t1 + t2 + t3) / 24.0D;
        double CURRENTTIME = t;
        int CURRENTDAYNUMBER = calendar.get(Calendar.DAY_OF_YEAR);
        double CURRENTDATE = CURRENTDAYNUMBER + CURRENTTIME;

        setShadow(Sun.getDeclination(CURRENTDAYNUMBER), Sun.getRightAscension(CURRENTDAYNUMBER),
                Sun.getGST(CURRENTDAYNUMBER, CURRENTTIME));
    }

    private void setShadow(double declination, double rightAscension, double GST) {

        int imgWidth = buffImg.getWidth();
        int imgHeight = buffImg.getHeight();

        GST *= PI2;
        if (GST > PI) {
            GST = -(PI2 - GST);
        }
        double diff = GST - rightAscension;
        double sinDec = Math.sin(declination);
        double cosDec = Math.cos(declination);

        for (int y = 0; y < imgHeight; y++) {
            double sinLat = Math.sin(-(((PI / (imgHeight - 1)) * y) - PId2));
            double cosLat = Math.cos(-(((PI / (imgHeight - 1)) * y) - PId2));
            buffImg.getRGB(0, y, imgWidth, 1, buffImgLine, 0, imgWidth);
            if (buffNightImg != null) {
                buffNightImg.getRGB(0, y, imgWidth, 1, buffNightImgLine, 0, imgWidth);
            } else {
                for (int i = 0; i < imgWidth; i++) {
                    buffNightImgLine[i] = 0;
                }
            }
            for (int x = 0; x < imgWidth; x++) {
                double temp = (diff + ((PI2 / (imgWidth - 1)) * x)) - PI;
                if (temp < 0.0D) {
                    temp = -temp;
                }
                if (temp > PI) {
                    temp = PI2 - temp;
                }
                if (temp < 0.0D) {
                    temp = -temp;
                }
                double cosHourAngle = FMath.Cos(temp);
                double altitude = FMath.ArcSin((sinLat * sinDec) + (cosLat * cosDec * cosHourAngle));

                int dayTemp = buffImgLine[x];
                // compute shadowed color
                //				int nightTemp = ((((dayTemp & 0xFF0000)>>16) / 2 ) << 16) |
                //								((((dayTemp & 0x00FF00)>> 8) / 2 ) <<  8) |
                //								((((dayTemp & 0x0000FF)>> 0) / 2 ) <<  0) |
                //								(dayTemp & 0xFF000000);
                int nightTemp = buffNightImgLine[x];
                nightTemp = ((int) ((((dayTemp & 0xFF0000) >> 16) * .4) + (((nightTemp & 0xFF0000) >> 16) * 0.5)) << 16)
                        | ((int) ((((dayTemp & 0xFF00) >> 8) * .4) + (((nightTemp & 0xFF00) >> 8) * 0.5)) << 8)
                        | (int) ((((dayTemp & 0xFF)) * .4) + ((nightTemp & 0xFF) * 0.5));

                if (altitude < -0.065449846949787352D) {
                    buffImgLine[x] = nightTemp;
                } else if (altitude < 0.065449846949787352D) {
                    int day = dayTemp & 0xff0000;
                    int night = nightTemp & 0xff0000;
                    int red = 0xff0000 & (int) (((altitude * (day - night)) / 0.1308996938995747D) + ((day + night) / 2D));
                    day = dayTemp & 0xff00;
                    night = nightTemp & 0xff00;
                    int green = 0xff00 & (int) (((altitude * (day - night)) / 0.1308996938995747D) + ((day + night) / 2D));
                    day = dayTemp & 0xff;
                    night = nightTemp & 0xff;
                    int blue = 0xff & (int) (((altitude * (day - night)) / 0.1308996938995747D) + ((day + night) / 2D));
                    buffImgLine[x] = 0xff000000 | red | green | blue;
                }
            }
            buffFinalImg.setRGB(0, y, imgWidth, 1, buffImgLine, 0, imgWidth);
        }
    }

    public static final BufferedImage getFinalBufferedImage() {
        return buffFinalImg;
    }

}
