/*
 * Created on Jun 1, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.swing.ImageIcon;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

/**
 * @author mluc
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DataGlobals {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(DataGlobals.class.getName());

    //------------------------- data view information --------------------------//
    public Map<ServiceID, rcNode> snodes; // map of rcNode.UnitName -> rcNode
    public Vector<rcNode> vnodes; // vector of rcNode

    //-----------------------end data view information -------------------------//

    /**
     * convert a string to a double value. If there would be an exception
     * return the failsafe value
     * @param value initial value
     * @param failsafe failsafe value
     * @return final value
     */
    public static float failsafeParseFloat(String value, float failsafe) {
        try {
            return Float.parseFloat(value);
        } catch (Throwable t) {
            return failsafe;
        }
    }

    /**
     * sets a correct and usable value for latittude with respect to a geographical map<br>
     * value is in interval -90 to 90 when returned
     * @param latitude
     * @return new corrected value
     */
    public static float failsafeParseLAT(float latitude) {
        //correct latitude if outside the interval
        if (latitude > 90f) {
            latitude = 90f;
        } else if (latitude < -90f) {
            latitude = -90f;
        }
        return latitude;
    }

    public static float failsafeParseLAT(String value, float failsafe) {
        float latitude = failsafeParseFloat(value, failsafe);
        //correct latitude if outside the interval
        return failsafeParseLAT(latitude);
    }

    /**
     * idem as above, only that for longitude, so value must be in interval -180 to 180
     * @param longitude
     * @return
     */
    public static float failsafeParseLONG(float longitude) {
        //correct longitude if outside the interval
        if (longitude > 180f) {
            longitude = 180f;
        } else if (longitude < -180f) {
            longitude = -180f;
        }
        return longitude;
    }

    public static float failsafeParseLONG(String value, float failsafe) {
        float longitude = failsafeParseFloat(value, failsafe);
        //correct latitude if outside the interval
        return failsafeParseLONG(longitude);
    }

    /**
     * image icons used for options panel inside main jogl panel
     */
    ImageIcon iconPlaneProj = null;
    ImageIcon iconSphereProj = null;

    public static int makeTexture(GL gl, BufferedImage bi) {
        int texture_id;
        ByteBuffer dest = getImageBytes(bi);
        int width = bi.getWidth();
        int height = bi.getHeight();
        int[] rou_text = new int[1];
        gl.glGenTextures(1, rou_text, 0);
        texture_id = rou_text[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, 3, width, height, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, dest);
        return texture_id;
    }

    public static ByteBuffer getImageBytes(BufferedImage bi) {
        ByteBuffer dest = null;
        int width = bi.getWidth();
        int height = bi.getHeight();
        byte[] data = new byte[width * height * 3];
        int[] pixels = new int[width * height];
        pixels = bi.getRGB(0, 0, width, height, pixels, 0, width);
        for (int y = height - 1, pointer = 0; y >= 0; y--) {
            for (int x = 0; x < width; x++, pointer += 3) {
                data[pointer + 0] = (byte) ((pixels[(y * width) + x] >> 16) & 0xFF);
                data[pointer + 1] = (byte) ((pixels[(y * width) + x] >> 8) & 0xFF);
                data[pointer + 2] = (byte) (pixels[(y * width) + x] & 0xFF);
            }
        }
        dest = ByteBuffer.allocateDirect(data.length);
        dest.order(ByteOrder.nativeOrder());
        dest.put(data, 0, data.length);
        dest.rewind(); // <- NEW STUFF NEEDED BY JSR231
        return dest;
    }

    public static int makeTextureA(GL gl, BufferedImage bi) {
        int texture_id;
        IntBuffer dest = getImageBytesA(bi);
        int width = bi.getWidth();
        int height = bi.getHeight();
        int[] rou_text = new int[1];
        gl.glGenTextures(1, rou_text, 0);
        texture_id = rou_text[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, width, height, 0, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE, dest);
        return texture_id;
    }

    public static IntBuffer getImageBytesA(BufferedImage bi) {
        IntBuffer dest = null;
        //        int width = bi.getWidth();
        //        int height = bi.getHeight();
        //        byte []data = new byte[width*height*3];
        //        int []pixels = new int[width*height];
        //        pixels = bi.getRGB( 0, 0, width, height, pixels, 0, width);
        //        for(int y = height -1, pointer = 0; y>=0; y--)
        //            for(int x = 0; x<width; x++,pointer+=3){
        //                data[pointer+0] = (byte)((pixels[y*width + x] >> 16) & 0xFF);
        //                data[pointer+1] = (byte)((pixels[y*width + x] >>  8) & 0xFF);
        //                data[pointer+2] = (byte) (pixels[y*width + x]        & 0xFF);
        //            }
        int[] data = ((DataBufferInt) (bi.getRaster().getDataBuffer())).getData();
        dest = IntBuffer.wrap(data);
        //	    dest.order(Order.nativeOrder());
        //	    dest.put(data, 0, data.length);
        dest.rewind(); // <- NEW STUFF NEEDED BY JSR231
        return dest;
    }

    public static int getBubbleID(GL2 gl, String imgPath, float[] outherColor) {
        int bubbleID = 0;
        int bubble_texture_id = 0;

        int nFileName;
        nFileName = imgPath.lastIndexOf('/');
        String sFile;
        if (nFileName != -1) {
            sFile = imgPath.substring(nFileName + 1);
        } else {
            sFile = imgPath;
        }

        //load star fundal
        long startTime = NTPDate.currentTimeMillis();
        BufferedImage bi = Globals.loadBuffImage(imgPath);//"lia/images/ml_router.png");
        if (bi == null) {
            logger.log(Level.WARNING, "Loading texture for " + sFile + " -> error!");
            return -1;
        } else {
            bubble_texture_id = makeTexture(gl, bi);
        }
        ;
        long endTime = NTPDate.currentTimeMillis();
        logger.log(Level.FINEST, "Loading texture for " + sFile + " has texture id: " + bubble_texture_id
                + " -> done in " + (endTime - startTime) + " ms.");

        int nrPoints = 32;
        float[][] pts = new float[nrPoints][2];
        float faDelta = (2 * (float) Math.PI) / nrPoints, faAlfa;
        faAlfa = 0.0f;
        //        float fX, fY;
        for (int slice = 0; slice < nrPoints; slice++) {
            pts[slice][0] = (float) Math.cos(faAlfa);
            pts[slice][1] = (float) Math.sin(faAlfa);
            faAlfa += faDelta;
        }
        bubbleID = gl.glGenLists(1);
        //System.out.println("routerID="+ routerID);
        gl.glNewList(bubbleID, GL2.GL_COMPILE);
        {
            //draw first up face
            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, bubble_texture_id);
            gl.glColor3f(1.0f, 1.0f, 1.0f);
            gl.glBegin(GL.GL_TRIANGLE_FAN);
            gl.glTexCoord2f(0.5f, 0.5f);
            gl.glVertex3f(0, 0, 0.5f);
            for (int slice = 0; slice < nrPoints; slice++) {
                gl.glTexCoord2f((pts[slice][0] * .5f) + .5f, (pts[slice][1] * .5f) + .5f);
                gl.glVertex3f(pts[slice][0], pts[slice][1], 0.5f);
            }
            gl.glTexCoord2f((pts[0][0] * .5f) + .5f, (pts[0][1] * .5f) + .5f);
            gl.glVertex3f(pts[0][0], pts[0][1], 0.5f);
            gl.glEnd();
            gl.glDisable(GL.GL_TEXTURE_2D);

            //set color
            gl.glColor3f(outherColor[0], outherColor[1], outherColor[2]);

            //draw bottom face
            gl.glBegin(GL.GL_TRIANGLE_FAN);
            for (int slice = 0; slice < nrPoints; slice++) {
                gl.glVertex3f(pts[slice][0], pts[slice][1], 0f);
            }
            gl.glVertex3f(pts[0][0], pts[0][1], 0f);
            gl.glEnd();

            //draw laterals
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            for (int slice = 0; slice < nrPoints; slice++) {
                gl.glVertex3f(pts[slice][0], pts[slice][1], 0.5f);
                gl.glVertex3f(pts[slice][0], pts[slice][1], 0f);
            }
            gl.glVertex3f(pts[0][0], pts[0][1], 0.5f);
            gl.glVertex3f(pts[0][0], pts[0][1], 0f);
            gl.glEnd();

        }
        gl.glEndList();
        return bubbleID;
    }

    public static int getCubeID(GL2 gl, String imgPath, String sObject) {
        int ID = 0;
        int texture_id = 0;

        //load star fundal
        long startTime = NTPDate.currentTimeMillis();
        BufferedImage bi = Globals.loadBuffImage(imgPath);//"lia/images/ml_router.png");
        if (bi == null) {
            logger.log(Level.WARNING, "Loading " + sObject + " texture -> error! -> ");
            return -1;
        } else {
            texture_id = makeTexture(gl, bi);
        }
        ;
        long endTime = NTPDate.currentTimeMillis();
        logger.log(Level.FINEST, "Loading " + sObject + " texture has id: " + texture_id + " -> done in "
                + (endTime - startTime) + " ms.");

        ID = gl.glGenLists(1);
        //System.out.println("routerID="+ routerID);
        gl.glNewList(ID, GL2.GL_COMPILE);
        {
            //draw first up face
            gl.glEnable(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, texture_id);
            gl.glColor3f(1.0f, 1.0f, 1.0f);

            float dim = .5f;

            gl.glBegin(GL2.GL_QUADS);
            // Front Face
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(-dim, -dim, dim); // Bottom Left Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(dim, -dim, dim); // Bottom Right Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(dim, dim, dim); // Top Right Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(-dim, dim, dim); // Top Left Of The Texture and Quad
            // Back Face
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(-dim, -dim, -dim); // Bottom Right Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(-dim, dim, -dim); // Top Right Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(dim, dim, -dim); // Top Left Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(dim, -dim, -dim); // Bottom Left Of The Texture and Quad
            // Top Face
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(-dim, dim, -dim); // Top Left Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(-dim, dim, dim); // Bottom Left Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(dim, dim, dim); // Bottom Right Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(dim, dim, -dim); // Top Right Of The Texture and Quad
            // Bottom Face
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(-dim, -dim, -dim); // Top Right Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(dim, -dim, -dim); // Top Left Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(dim, -dim, dim); // Bottom Left Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(-dim, -dim, dim); // Bottom Right Of The Texture and Quad
            // Right face
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(dim, -dim, -dim); // Bottom Right Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(dim, dim, -dim); // Top Right Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(dim, dim, dim); // Top Left Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(dim, -dim, dim); // Bottom Left Of The Texture and Quad
            // Left Face
            gl.glTexCoord2f(0.0f, 0.0f);
            gl.glVertex3f(-dim, -dim, -dim); // Bottom Left Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 0.0f);
            gl.glVertex3f(-dim, -dim, dim); // Bottom Right Of The Texture and Quad
            gl.glTexCoord2f(1.0f, 1.0f);
            gl.glVertex3f(-dim, dim, dim); // Top Right Of The Texture and Quad
            gl.glTexCoord2f(0.0f, 1.0f);
            gl.glVertex3f(-dim, dim, -dim); // Top Left Of The Texture and Quad
            gl.glEnd();
            gl.glDisable(GL.GL_TEXTURE_2D);

        }
        gl.glEndList();
        return ID;
    }

    /**
     * start_multiplier = 0 means bytes<br>
     * = 1 means kilobytes, etc
     * @param d
     * @param start_multiplier
     * @return
     */
    public static String formatDoubleByteMul(double d, int start_multiplier) {
        int val = (int) (d * 10);
        String[] multipliers = new String[] { "bps", "Kbps", "Mbps", "Gbps", "Tbps", "Pbps" };
        int current_multiplier = start_multiplier;
        String retVal = "";
        while (current_multiplier < (multipliers.length - 1)) {
            if (val < 10000) {
                break;
            }
            val /= 1000;
            current_multiplier++;
        }
        ;
        retVal = "" + ((double) val / 10) + " " + multipliers[current_multiplier];
        return retVal;
    }

    /**
     * start_multiplier = 0 means bytes<br>
     * = 1 means kilobytes, etc
     * @param d
     * @param start_multiplier
     * @return
     */
    public static String formatDoubleByteMul(double d, int start_multiplier, boolean bIsBits) {
        int val = (int) (d * 10);
        String[] multipliers;
        if (bIsBits) {
            multipliers = new String[] { "bps", "Kbps", "Mbps", "Gbps", "Tbps", "Pbps" };
        } else {
            multipliers = new String[] { "B/s", "KB/s", "MB/s", "GB/s", "TB/s", "PB/s" };
        }
        int current_multiplier = start_multiplier;
        String retVal = "";
        while (current_multiplier < (multipliers.length - 1)) {
            if (bIsBits) {
                if (val < 10000) {
                    break;
                }
                val /= 1000;
            } else {
                if (val < 10240) {
                    break;
                }
                val /= 1024;
            }
            current_multiplier++;
        }
        ;
        retVal = "" + ((double) val / 10) + " " + multipliers[current_multiplier];
        return retVal;
    }

    /**
     * load a texture into memory and generates an opengl texture, returning its integer id
     * @param gl
     * @param imgPath
     * @return id of opengl texture
     */
    public static int loadTexture(GL gl, String imgPath) {
        int texture_id = 0;

        long startTime = NTPDate.currentTimeMillis();
        BufferedImage bi = Globals.loadBuffImage(imgPath);
        if (bi == null) {
            logger.log(Level.WARNING, "Error on loading texture " + imgPath + " -> image object is null");
            return -1;
        } else {
            texture_id = makeTexture(gl, bi);
        }
        ;
        long endTime = NTPDate.currentTimeMillis();
        logger.log(Level.FINEST, "Loading texture " + imgPath + " with gl texture id " + texture_id + " -> done in "
                + (endTime - startTime) + " ms.");

        return texture_id;
    }

    public static int loadTexture(GL gl, BufferedImage bi, String textureName) {
        int texture_id = 0;

        long startTime = NTPDate.currentTimeMillis();
        if (bi == null) {
            logger.log(Level.WARNING, "Error on loading texture " + textureName + " -> image object is null");
            return -1;
        } else {
            texture_id = makeTexture(gl, bi);
        }
        ;
        long endTime = NTPDate.currentTimeMillis();
        logger.log(Level.FINEST, "Loading texture " + textureName + " with gl texture id " + texture_id
                + " -> done in " + (endTime - startTime) + " ms.");

        return texture_id;
    }

    public static int loadTextureAlpha(GL gl, BufferedImage bi, String textureName) {
        int texture_id = 0;

        long startTime = NTPDate.currentTimeMillis();
        if (bi == null) {
            logger.log(Level.WARNING, "Error on loading texture " + textureName + " -> image object is null");
            return -1;
        } else {
            texture_id = makeTextureA(gl, bi);
        }
        ;
        long endTime = NTPDate.currentTimeMillis();
        logger.log(Level.FINEST, "Loading alpha texture " + textureName + " with gl texture id " + texture_id
                + " -> done in " + (endTime - startTime) + " ms.");

        return texture_id;
    }
}
