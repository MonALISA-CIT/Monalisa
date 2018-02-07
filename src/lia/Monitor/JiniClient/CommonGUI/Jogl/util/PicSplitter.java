/*
 * Created on May 30, 2005 by Lucian Musat
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.ImageIcon;

import lia.Monitor.JiniClient.CommonGUI.Jogl.Shadow;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Texture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.TextureLoadJobResult;
import lia.Monitor.monitor.AppConfig;

/**
 * generates a script or directly oct-s from an image file
 * for whom two points are specified along with their geographical coordinates<br>
 * coordinates in a picture start from left-top corner and, x is positive along left to right direction<br>
 * and y is positive along top to bottom direction.<br>
 * In geographical coordinates, the starting point is the center of the map and longitude goes
 * positive from left to right, while latitude goes positive from bottom to top.<br>
 * <b>The doWork function:</b><br>
 * <ol>
 * <li><b>Zero step</b><br>
 * read inputs.
 * </li>
 * <li><b>First step</b><br>
 * compute geographical width and height the picture would have<br>
 * compute geographical starting and ending coordinates for this picture
 * </li>
 * <li><b>Second step</b><br>
 * Computation of right level the picture should be considered for and
 * correct its dimensions if the case (it does not fit exactly the requirements).
 * </li>
 * <li><b>Third step</b><br>
 * generating pictures
 * </li>
 * </ol>
 * <br>
 * Uses the following <b>properties</b> set before the application is run with "-D" parameter:<br>
 * <ol>
 * <li><b>verbose</b><br>
 * If <i>true</i>, print verbose output about actions that are taken.
 * </li>
 * <li><b>input_file</b><br>
 * Specifies the input file where from parameters will be read, each on a line, instead of asking from standard input.
 * </li>
 * <li><b>noWebFile</b><br>
 * If <i>true</i>, the application will not try to look for a background image on the web, using the underlying
 * mechanism provided by NetResourceClassLoader class.
 * </li>
 * <li><b>nrXtiles0</b> and <b>nrYtiles0</b><br>
 * The only values that really matter and change the size of the octs.<br>
 * The number of textures on x and y for first image that has a resolution of 1024x512.
 * </li>
 * </ol>
 * May 30, 2005 - 1:01:14 PM
 */
public class PicSplitter {
    private static final boolean bVerbose = ("true".equals(System.getProperty("verbose")));

    public static int[] resolutionX = { 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288 };
    public static int[] resolutionY = { 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144 };
    public static int[] texturesX = { 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };
    public static int[] texturesY = { 1, 2, 2, 2, 2, 2, 2, 2, 2, 2 };
    public static final String pathSeparator = System.getProperty("file.separator");
    private String sPicFilePath = "";
    private String sPicFileExt = "";
    private String sInputFile = System.getProperty("input_file");
    static {
        texturesX[0] = AppConfig.geti("nrXtiles0", 2);
        texturesY[0] = AppConfig.geti("nrYtiles0", 1);
        OCT_WIDTH = resolutionX[0] / texturesX[0];
        OCT_HEIGHT = resolutionY[0] / texturesY[0];
    };
    private static final int OCT_WIDTH;
    private static final int OCT_HEIGHT;

    private final boolean bNoSearchOnWeb = System.getProperty("noWebFile", "false").equals("true");

    private NetResourceClassLoader myMapsClassLoader;

    /**
     * reads an int from input
     */
    public static int readInt(InputStream is) {
        int val = -1;//next char read [0..255] or -1 for end of stream
        int ret = 0;//return value
        char car;//character value -> unicode codification, 2 bytes, unsigned
        do {
            try {
                val = is.read();
                //System.out.println("read: "+val);
            } catch (IOException e) {
                e.printStackTrace();
                val = -1;
            }
            ;
            if (val != -1) {
                car = (char) val;
                if (car == '\n') {
                    val = -1;
                } else if (((car >= '0') && (car <= '9')) || (car == '-')) {
                    ret = ((ret * 10) + car) - '0';
                }
            }
        } while (val != -1);
        if (is != System.in) {
            if (bVerbose) {
                System.out.println("**read from file: ** " + ret + " **");
            }
        }
        return ret;
    }

    public static float readFloat(InputStream is) {
        float ret = 0;
        int val = -1;
        StringBuilder sNumar = new StringBuilder();
        char car;//character value -> unicode codification, 2 bytes, unsigned
        do {
            try {
                val = is.read();
                //System.out.println("read: "+val);
            } catch (IOException e) {
                e.printStackTrace();
                val = -1;
            }
            ;
            if (val != -1) {
                car = (char) val;
                if (car == '\n') {
                    val = -1;
                } else if (((car >= '0') && (car <= '9')) || (car == '-') || (car == '.')) {
                    sNumar.append(car);
                }
            }
        } while (val != -1);
        try {
            ret = Float.parseFloat(sNumar.toString());
        } catch (NumberFormatException nfex) {
            ret = 0;
            nfex.printStackTrace();
        }
        if (is != System.in) {
            if (bVerbose) {
                System.out.println("**read from file: ** " + ret + " **");
            }
        }
        return ret;
    }

    /**
     * reads an string from input
     */
    public static String readString(InputStream is) {
        int val = -1;//next char read [0..255] or -1 for end of stream
        StringBuilder ret = new StringBuilder();//return value
        char car;//character value -> unicode codification, 2 bytes, unsigned
        do {
            try {
                val = is.read();
                //System.out.println("read: "+val);
            } catch (IOException e) {
                e.printStackTrace();
                val = -1;
            }
            ;
            if (val != -1) {
                car = (char) val;
                if (car == '\n') {
                    val = -1;
                } else {
                    ret.append(car);
                }
            }
        } while (val != -1);
        if (is != System.in) {
            if (bVerbose) {
                System.out.println("**read from file: ** " + ret + " **");
            }
        }
        return ret.toString();
    }

    /**
     * puts the command into a file for latter batch run<br>
     * or runs it now.<br>
     * For the moment only outputs the command.
     * @param command
     */
    public void runCommand(String command) {
        if (bVerbose) {
            System.out.println("Command to be run: " + command);
        }
        try {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
        } catch (IOException e) {
            if (bVerbose) {
                System.out.println("Exception executing command");
            }
            if (bVerbose) {
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            if (bVerbose) {
                e.printStackTrace();
            }
        }
    }

    /**
     * puts the command into a file for latter batch run<br>
     * or runs it now.<br>
     * For the moment only outputs the command.
     * @param command
     */
    public void runCommand(String command, boolean bShowMessage) {
        if (bShowMessage) {
            if (bVerbose) {
                System.out.println("Command to be run: " + command);
            }
        }
        try {
            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();
        } catch (IOException e) {
            if (bShowMessage) {
                if (bVerbose) {
                    System.out.println("Exception executing command");
                }
            }
            if (bShowMessage) {
                if (bVerbose) {
                    e.printStackTrace();
                }
            }
        } catch (InterruptedException e) {
            if (bShowMessage) {
                if (bVerbose) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int doWork() {
        //first create the maps loader
        myMapsClassLoader = new NetResourceClassLoader(null);
        NetResourceClassLoader.setDebugLevel(true);
        if ((sInputFile == null) || (sInputFile.length() == 0)) {
            sInputFile = "input.txt";
        }
        //get input stream
        InputStream fis = null;
        try {
            fis = new FileInputStream(new File(sInputFile));
        } catch (FileNotFoundException e) {
            fis = System.in;
        }
        //read inputs
        int x1, x2, y1, y2;
        float long1, long2, lat1, lat2;
        float longS, latS, longE, latE;
        float wd, hd;//width and height of picture in geographical dimensions
        String sPicFullPath;
        if (bVerbose) {
            System.out.print("Path to image: ");
        }
        sPicFullPath = readString(fis);//"/home/mluc/public_html/monalisa/temp/earth_night1024x512.jpg";//
        String sPicFile = sPicFullPath;
        //get file path
        int nPos1 = sPicFullPath.lastIndexOf(sFileSeparator);
        if (nPos1 != -1) {
            sPicFilePath = sPicFullPath.substring(0, nPos1 + 1);
        } else {
            System.exit(0);
        }
        int nPos2 = sPicFullPath.lastIndexOf(".");
        if (nPos2 != -1) {
            sPicFileExt = sPicFullPath.substring(nPos2);//includes point
        } else {
            System.exit(0);
        }
        if ((nPos1 != -1) && (nPos2 != -1) && (nPos1 < nPos2)) {
            sPicFile = sPicFullPath.substring(nPos1 + 1, nPos2);
        } else {
            System.exit(0);
        }
        if (bVerbose) {
            System.out.println("picture directory is " + sPicFilePath);
        }
        int w, h;//image dimensions determined by one way or the other...
        //read w, h
        if (bVerbose) {
            System.out.print("Image width=");
        }
        w = readInt(fis);//1006;//
        if (bVerbose) {
            System.out.print("Image height=");
        }
        h = readInt(fis);//503;//
        //read the two points
        if (bVerbose) {
            System.out.print("First point x=");
        }
        x1 = readInt(fis);//0;//
        if (bVerbose) {
            System.out.print("First point y=");
        }
        y1 = readInt(fis);//0;//
        if (bVerbose) {
            System.out.print("First point longitude=");
        }
        long1 = readFloat(fis);//0;//
        if (bVerbose) {
            System.out.print("First point latitude=");
        }
        lat1 = readFloat(fis);//90;//
        if (bVerbose) {
            System.out.print("Second point x=");
        }
        x2 = readInt(fis);//1006;//
        if (bVerbose) {
            System.out.print("Second point y=");
        }
        y2 = readInt(fis);//503;//
        if (bVerbose) {
            System.out.print("Second point longitude=");
        }
        long2 = readFloat(fis);//360;//
        if (bVerbose) {
            System.out.print("Second point latitude=");
        }
        lat2 = readFloat(fis);//-90;//
        if (bVerbose) {
            System.out.println("Image dimensions: w=" + w + " h=" + h);
        }
        if (bVerbose) {
            System.out.println("First point: x=" + x1 + " y=" + y1 + " long=" + long1 + " lat=" + lat1);
        }
        if (bVerbose) {
            System.out.println("Second point: x=" + x2 + " y=" + y2 + " long=" + long2 + " lat=" + lat2);
        }
        //First step
        //compute geographical width and height the picture would have
        wd = (w * (long1 - long2)) / (x1 - x2);
        hd = (h * (lat1 - lat2)) / (y2 - y1);
        if (bVerbose) {
            System.out.println("geographical dimensions: wd=" + wd + " hd=" + hd);
        }
        //compute geographical starting and ending coordinates for this picture
        longS = long1 - ((x1 * wd) / w);
        latS = lat1 + ((y1 * hd) / h);
        longE = longS + wd;
        latE = latS - hd;
        if (bVerbose) {
            System.out.println("geographical positions: longS=" + longS + " latS=" + latS + " longE=" + longE
                    + " latE=" + latE);
        }
        //Second step: Computation of right level the picture should be considered for and
        //correct its dimensions if the case (it does not fit exactly the requirements).
        float rpg;//pixels per grades
        /**
         * initial division, meaning that level 0 shows on 1024 pixels the entire earth map,
         * covering 360 degrees in longitude and 180 in latitude
         */
        float start_rpg = 1024 / 360f;
        if (bVerbose) {
            System.out.println("start_rpg=1024/360f=" + start_rpg
                    + " (level 0 covers entire earth map with 1024 pixels)");
        }
        rpg = w / wd;
        if (bVerbose) {
            System.out.println("rpg=" + w + "/" + wd + "=" + rpg);
        }
        if (rpg < start_rpg) {
            if (bVerbose) {
                System.out.println("Picture has a resolution too small. Nothing will be done!");
            }
            System.exit(0);
        }
        /**
         * suspected level is full width divided by minimal width (1024) compared to a power of 2
         * because the report of width gives the multiplier of this map from initial one compared to a power of 2
         * The computation is done with rpg because the full width is not available
         * ony the division to full longitude width
         */
        if (bVerbose) {
            System.out.println("suspected level: log2(rpg/start_rpg)=" + (Math.log(rpg / start_rpg) / Math.log(2)));
        }
        /**
         * picture's level: level==0 means that picture integrates nicelly on a 1024x512 map<br>
         * level==1 is for a 2048x1024 map<br>
         * etc...
         */
        int level = 0;
        level = (int) Math.floor(Math.log(rpg / start_rpg) / Math.log(2));
        int power2OfLevel = (int) Math.pow(2, level);
        if (bVerbose) {
            System.out
            .println("This image corresponds, given the geographical coordinates, to the level (starting at 0): "
                    + level);
        }
        /**
         * compute starting positions for this picture, top-left corner
         * to be able to find the correct list of indexes to each texture that will compose this image
         * choose floor for xs and ceil for xe to overlap in the worst case with the next, but not miss a pixel
         * the same for ys
         */
        int xs = (int) Math.floor(long2x(longS, resolutionX[level]));
        int ys = (int) Math.floor(lat2y(latS, resolutionY[level]));
        if (bVerbose) {
            System.out.println("start position in big picture: xs=" + xs + " ys=" + ys);
        }
        //now ys is between yp*hp and yp*hp+hp
        int ye = (int) Math.ceil(lat2y(latE, resolutionY[level]));//ys+h;//
        int xe = (int) Math.ceil(long2x(longE, resolutionX[level]));//xs+w;//
        if (bVerbose) {
            System.out.println("end position in big picture: xe=" + xe + " ye=" + ye);
        }
        int new_w, new_h;
        new_w = xe - xs;
        new_h = ye - ys;
        //                                                  if ( rpg>start_rpg*power2OfLevel ) {
        //resize image
        //                                                  System.out.println("new_w="+w+"*"+cur_rpg+"/"+rpg+"="+(int)(w*cur_rpg/rpg));
        if (bVerbose) {
            System.out.println("new_w=" + new_w + " ~~ " + w + "*" + (power2OfLevel * start_rpg) + "/" + rpg + "="
                    + (int) ((w * power2OfLevel * start_rpg) / rpg));
        }
        //                                                  int new_w = (int)(w*power2OfLevel*start_rpg/rpg);
        //                                                  int new_h = (int)(h*power2OfLevel*start_rpg/rpg);
        if (bVerbose) {
            System.out.println("new_h=" + new_h);
        }
        //do actual resize operation
        boolean bFileResized = false;
        if ((new_w != w) || (new_h != h)) {
            //run command
            runCommand("convert -quality 100 -resize " + new_w + "x" + new_h + " " + sPicFilePath + sPicFile
                    + sPicFileExt + " " + sPicFilePath + sPicFile + "resized" + sPicFileExt);
            sPicFile += "resized";
            bFileResized = true;
            w = new_w;
            h = new_h;
        }
        //                                                  }
        //Third step: generating pictures
        //find wp and hp: dimensions for one piece of picture
        int wp, hp;
        wp = OCT_WIDTH;
        hp = OCT_HEIGHT;
        //                                                  wp = resolutionX[level];
        //                                                  hp = resolutionY[level];
        //                                                  for ( int i=0; i<=level; i++) {
        //                                                  wp /= texturesX[i];
        //                                                  hp /= texturesY[i];
        //                                                  }
        if (bVerbose) {
            System.out.println("dimensions for one piece of picture: wp=" + wp + " hp=" + hp);
        }
        String sVect[];
        String sPathName, sFileName, sBaseFileName;
        int xp = 0, yp = 0;
        //find starting yp that includes a piece of the big picture
        while (((yp + 1) * hp) <= ys) {
            yp++;
        }
        String sFNSuffixVert = "", sFNSuffixHoriz = "";//file name suffix for pieces that are splitted horizontally or vertically or both
        int wp_split = wp, hp_split = hp;//width and height for splitted files
        int x_split, y_split;//x and y starting position for cropping from big picture for each piece
        int xp_start = 0;//start position for xp index
        //find starting xp
        while (((xp_start + 1) * wp) <= xs) {
            xp_start++;
        }
        while ((yp * hp) < ye) {
            hp_split = hp;//for each piece, the start height is hp
            y_split = yp * hp;
            sFNSuffixVert = "";
            //check to see if this piece is full or not, and append a suffix that describes it
            //the suffix will tell the distance on y and x relative to top left corner
            //if piece starts at left top corner, then no suffix will be appended,
            //and the piece will cover only width and height from the entire oct
            if (y_split < ys) {//if piece covers only lower part of picture
                hp_split = (y_split + hp) - ys;
                //put suffix
                //sFNSuffixVert +="_bottom";
                sFNSuffixVert = "_" + (ys - y_split);
                y_split = ys;
            } else if ((y_split + hp) > ye) {//if piece covers the upper part of picture
                hp_split = ye - y_split;
                //no need to specify a suffix, the picture is automattically put in the top part
                //                sFNSuffixVert +="_top";
            }
            ;
            xp = xp_start;
            //now xs is between  xp*wp and (xp+1)*wp
            while ((xp * wp) < xe) {
                wp_split = wp;//for each piece, the start width is wp
                x_split = xp * wp;
                sFNSuffixHoriz = "";
                //check to see if this piece is full or not, and append a suffix that describes it
                if (x_split < xs) {//if piece covers only right part of picture
                    wp_split = (x_split + wp) - xs;
                    //put suffix indicating the distance relative to left margin of this piece
                    sFNSuffixHoriz = "_" + (xs - x_split);
                    //sFNSuffixHoriz +="_right";
                    x_split = xs;
                } else if ((x_split + wp) > xe) {//if piece covers the left part of picture
                    wp_split = xe - x_split;
                    //no need to put a suffix, it will start from left
                    //sFNSuffixHoriz ="_left";
                }
                //create the slice
                sVect = getSlicePath(xp, yp, level);
                //System.out.println("get slice path for xp="+xp+" yp="+yp+" and level="+level+" is: "+sVect[0]);
                sPathName = sVect[1];
                sBaseFileName = "map" + (level + 1) + sVect[0];
                String sFNSuffix = "";
                if ((sFNSuffixVert.length() > 0) || (sFNSuffixHoriz.length() > 0)) {
                    sFNSuffix = (sFNSuffixVert.length() > 0 ? sFNSuffixVert : "_0")
                            + (sFNSuffixHoriz.length() > 0 ? sFNSuffixHoriz : "_0");
                }
                sFileName = sBaseFileName + sFNSuffix + sPicFileExt;
                runCommand("mkdir -p " + sPathName, false);
                //xs is absolute position, x_split-xs gives the position in available picture starting at 0
                runCommand("convert -quality 100 -crop " + wp_split + "x" + hp_split + "+" + (x_split - xs) + "+"
                        + (y_split - ys) + " " + sPicFilePath + sPicFile + sPicFileExt + " " + sPathName + sFileName,
                        false);
                if (bVerbose) {
                    System.out.println("crop " + wp_split + "x" + hp_split + "+" + (x_split - xs) + "+"
                            + (y_split - ys));
                }
                //load image and convert it to oct
                convertImg2Oct(sPathName, sBaseFileName, sPicFileExt/*, sBaseFileName+".oct"*/);
                xp++;
            }
            yp++;
        }
        ;
        if (bFileResized) {
            File fResizedFile = new File(sPicFilePath + sPicFile + sPicFileExt);
            try {
                fResizedFile.delete();
            } catch (Exception ex) {
                if (bVerbose) {
                    System.out.println("resized file could not be deleted.\nFile: " + sPicFilePath + sPicFile
                            + sPicFileExt + "\nException message follows.");
                    ex.printStackTrace();
                }
                ;
            }
        }

        return 1;
    }

    //private final String[] parts = new String[] {"_top_left", "_top", "_top_right", "_right", "_bottom_right", "_bottom", "_bottom_left", "_left"};
    /** array to contain image data for an oct */
    private final byte data[] = new byte[3 * OCT_WIDTH * OCT_HEIGHT];

    /**
     * first load the full image, if available, and then overlap on it pieces
     * @param pathName
     * @param baseFileName
     * @param picFileExt
     * @param octName
     */
    private void convertImg2Oct(String pathName, final String baseFileName, final String picFileExt/*, String octName*/) {
        //      System.out.println("oct W="+OCT_WIDTH+" H="+OCT_HEIGHT);
        String octName = baseFileName + ".oct";
        //flush oct byte data
        //or better, initialize it to the oct that exists
        //the first one that is found available
        /*
         * for that, first generate the name of file
         * and then, try to load it by using Texture.loadTextureFromFile
         * that downloads it from an known repository location.
         * If the file is not available, try to get parent file, and so on,
         * until one is found available, or no file found
         */
        boolean bOctFound = false;
        try {
            FileInputStream fis = new FileInputStream(pathName + octName);
            if (fis.read(data) == data.length) {
                bOctFound = true;
                System.out.println("oct found locally: " + pathName + octName);
            }
            ;

        } catch (FileNotFoundException e2) {
            // oct not found, go on, ignore it
            //background will be black
        } catch (IOException e) {
        }
        if (!bOctFound && !bNoSearchOnWeb) {
            try {
                System.out.println("Search oct on web");
                //if local was not yet created, try to get it from web, the first that matches
                //for that, split base file name to find desired level, and x,y coordinates for each succesive level
                String[] info = baseFileName.split("_");
                String[] infolevel;
                //get level found in first string
                int level = Integer.valueOf(info[0].substring(3)).intValue();
                //make an array for values (y,x)
                int coords[][] = new int[level][2];
                for (int i = 0; i < level; i++) {
                    infolevel = info[i + 1].split("\\.");
                    coords[i][0] = Integer.valueOf(infolevel[0]).intValue();
                    coords[i][1] = Integer.valueOf(infolevel[1]).intValue();
                }
                ;
                //level for whom the oct should be loaded
                int cur_level = level;
                StringBuilder sWebName = null;
                int x_startOct, y_startOct, widthOct, heightOct;
                x_startOct = 0;
                y_startOct = 0;
                widthOct = OCT_WIDTH;
                heightOct = OCT_HEIGHT;
                byte dataNew[] = null;
                int step = 1;
                do {
                    if (cur_level <= 0) {
                        break;
                    }
                    //construct the path to oct, as it is understood by load texture from file
                    //till current level
                    sWebName = new StringBuilder();
                    for (int i = 0; i < cur_level; i++) {
                        if (sWebName.length() > 0) {
                            sWebName.append(pathSeparator);
                        }
                        sWebName.append(info[i + 1]);
                    }
                    ;
                    sWebName.append(".oct");
                    TextureLoadJobResult result;
                    result = new TextureLoadJobResult();
                    result.level = cur_level - 1;
                    result.path = sWebName.toString();
                    Texture.loadTextureFromFile(result, myMapsClassLoader);
                    dataNew = result.data;
                    //dataNew is data or null
                    if (dataNew == null) {//requested file not found, so go to parent
                        //but first compute offsets
                        System.out.println("file " + sWebName.toString() + " not found, go to parent.");
                        widthOct /= 2;
                        heightOct /= 2;
                        step *= 2;
                        x_startOct += (coords[cur_level - 1][1] - 1) * widthOct;
                        y_startOct += (coords[cur_level - 1][0] - 1) * heightOct;
                        cur_level--;
                    }
                } while (dataNew == null);
                if (dataNew != null) {
                    bOctFound = true;
                    //first copy from dataNew to data
                    System.arraycopy(dataNew, 0, data, 0, 3 * OCT_WIDTH * OCT_HEIGHT);
                    //I've found a level so prepare the data using the computed offsets
                    if (cur_level <= level) {
                        if (sWebName != null) {
                            System.out.println("found map: " + sWebName.toString());
                        }
                        //if cur_level==level then nothing to be done, data is ok
                        //else zoom the area to full OCT_WIDTHxOCT_HEIGHT
                        //strech the area defined by (x_startOct, y_startOct) as (left,top) and width and height to
                        //full (0,0) and OCT_WIDTHxOCT_HEIGHT
                        byte aux_data[] = new byte[3 * OCT_WIDTH * OCT_HEIGHT];
                        System.arraycopy(data, 0, aux_data, 0, 3 * OCT_WIDTH * OCT_HEIGHT);
                        byte value1, value2, value3;
                        int index, index2;
                        for (int j = 0; j < heightOct; j++) {
                            for (int i = 0; i < widthOct; i++) {
                                index = x_startOct + i + (((OCT_HEIGHT - y_startOct - heightOct) + j) * OCT_WIDTH);
                                value1 = aux_data[3 * index];
                                value2 = aux_data[(3 * index) + 1];
                                value3 = aux_data[(3 * index) + 2];
                                for (int ky = 0; ky < step; ky++) {
                                    for (int kx = 0; kx < step; kx++) {
                                        index2 = (i * step) + kx + (((j * step) + ky) * OCT_WIDTH);
                                        data[3 * index2] = value1;
                                        data[(3 * index2) + 1] = value2;
                                        data[(3 * index2) + 2] = value3;
                                    }
                                }
                            }
                        }

                    }
                }
            } catch (Exception ex) {
                //some exception occured, nothing unusual, probably wrong file name
                System.out.println("Exception occurred in finding the startUp pic for file " + baseFileName + ":");
                ex.printStackTrace();
            }
        }
        //for diverse motives, we don't have any initial data, so clear the buffer
        //error on reading, reset data
        //we haven't read the entire oct, error, reset data
        if (!bOctFound) {
            for (int i = 0; i < data.length; i++) {
                data[i] = 0;
            }
        }
        int y_start, y_end, x_start, x_end;
        //if there are no parts, load image and make it oct
        boolean bPartExists = false;
        if (!bPartExists) {
            BufferedImage bi = loadBuffImage(pathName + baseFileName + picFileExt);
            if (bi != null) {
                bPartExists = true;
                int color1 = bi.getRGB(0, 0);
                //set brightness
                Shadow.setBrightness(bi, 1.7f);
                int color2 = bi.getRGB(0, 0);
                //System.out.println("brightness applied to "+baseFileName+" "+(color1!=color2?"yes":"no")+" from color "+Integer.toHexString(color1)+" to color "+Integer.toHexString(color2));
                //System.out.println("Could not load image "+/*pathName+*/baseFileName+picFileExt);
                int width = bi.getWidth();
                int height = bi.getHeight();
                int pixels[] = new int[width * height];
                bi.getRGB(0, 0, width, height, pixels, 0, width);
                y_start = height - 1;
                if (y_start > (OCT_HEIGHT - 1)) {
                    y_start = OCT_HEIGHT - 1;
                }
                y_end = 0;
                if (y_end < 0) {
                    y_end = 0;
                }
                x_start = 0;
                x_end = width;
                if (x_end > OCT_WIDTH) {
                    x_end = OCT_WIDTH;
                }
                for (int y = y_start - y_end, pointer = (((OCT_HEIGHT - 1 - y_start) * OCT_WIDTH) + x_start) * 3; y >= 0; y--, pointer += ((x_start + OCT_WIDTH) - x_end) * 3) {
                    for (int x = 0; x < (x_end - x_start); x++, pointer += 3) {
                        if (((pixels[(y * width) + x] >> 24) & 0xFF) == 0xFF) {
                            data[pointer + 0] = (byte) ((pixels[(y * width) + x] >> 16) & 0xFF);
                            data[pointer + 1] = (byte) ((pixels[(y * width) + x] >> 8) & 0xFF);
                            data[pointer + 2] = (byte) (pixels[(y * width) + x] & 0xFF);
                        } else {
                            int alphaValue = ((pixels[(y * width) + x] >> 24) & 0xFF);
                            data[pointer + 0] = (byte) (((data[pointer + 0] * (255 - alphaValue)) + (alphaValue * ((pixels[(y * width)
                                                                                                                           + x] >> 16) & 0xFF))) / 255);
                            data[pointer + 1] = (byte) (((data[pointer + 1] * (255 - alphaValue)) + (alphaValue * ((pixels[(y * width)
                                                                                                                           + x] >> 8) & 0xFF))) / 255);
                            data[pointer + 2] = (byte) (((data[pointer + 2] * (255 - alphaValue)) + (alphaValue * (pixels[(y * width)
                                                                                                                          + x] & 0xFF))) / 255);
                        }
                    }
                }
            }
        }
        //for each part of the image, top-left, top, top-right, right, bottom-right, bottom, bottom-left, let
        //load the image, if it exists, and put it into the oct.
        /**deprecated*/
        //replaced with: for each file in specified directory that starts with baseFileName and has the desired extension
        //check for the suffix of base name and interpret it as _y_x
        File curDir = new File(pathName);
        File[] fileList = curDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                if (pathname.isFile() && pathname.getName().startsWith(baseFileName)
                        && pathname.getName().endsWith(picFileExt)) {
                    return true;
                }
                return false;
            }
        });
        for (int i = 0; i < fileList.length; i++) {
            BufferedImage bi = loadBuffImage(fileList[i].getAbsolutePath());
            if (bi != null) {
                bPartExists = true;

                int color1 = bi.getRGB(0, 0);
                //set brightness
                Shadow.setBrightness(bi, 1.7f);
                int color2 = bi.getRGB(0, 0);
                //System.out.println("brightness applied to "+baseFileName+" "+(color1!=color2?"yes":"no")+" from color "+Integer.toHexString(color1)+" to color "+Integer.toHexString(color2));

                //create the slice
                //get the pixels array from picture
                //transform in 3 byte array
                //save it to file under prefix+".oct"
                int width = bi.getWidth();
                int height = bi.getHeight();
                int pixels[] = new int[width * height];
                bi.getRGB(0, 0, width, height, pixels, 0, width);

                //get part's position in slice from basename suffix
                String sSuffix = fileList[i].getName();
                //remove basename
                sSuffix = sSuffix.substring(baseFileName.length());
                //remove extension
                sSuffix = sSuffix.substring(0, sSuffix.length() - picFileExt.length());
                //identify x and y
                int yOffset = 0;
                int xOffset = 0;
                if (sSuffix.length() > 0) {
                    //skip first _
                    int index = 1;
                    //read y offset
                    while ((index < sSuffix.length()) && (sSuffix.charAt(index) >= '0')
                            && (sSuffix.charAt(index) <= '9')) {
                        yOffset = ((yOffset * 10) + sSuffix.charAt(index)) - '0';
                        index++;
                    }
                    ;
                    //skip the next _
                    index++;
                    //read x offset
                    while ((index < sSuffix.length()) && (sSuffix.charAt(index) >= '0')
                            && (sSuffix.charAt(index) <= '9')) {
                        xOffset = ((xOffset * 10) + sSuffix.charAt(index)) - '0';
                        index++;
                    }
                    ;
                }
                //skip lower lines if not bottom and let and right columns if the case
                y_start = (yOffset + height) - 1;
                if (y_start > (OCT_HEIGHT - 1)) {
                    y_start = OCT_HEIGHT - 1;
                }
                y_end = yOffset;
                if (y_end > (OCT_HEIGHT - 1)) {
                    y_end = OCT_HEIGHT - 1;
                }
                x_start = xOffset;
                if (x_start > (OCT_WIDTH - 1)) {
                    x_start = OCT_WIDTH - 1;
                }
                x_end = (xOffset + width) - 1;
                if (x_end > (OCT_WIDTH - 1)) {
                    x_end = OCT_WIDTH - 1;
                }
                for (int y = y_start - y_end, pointer = (((OCT_HEIGHT - 1 - y_start) * OCT_WIDTH) + x_start) * 3; y >= 0; y--, pointer += ((x_start + OCT_WIDTH) - x_end) * 3) {
                    for (int x = 0; x < (x_end - x_start); x++, pointer += 3) {
                        if (((pixels[(y * width) + x] >> 24) & 0xFF) == 0xFF) {
                            data[pointer + 0] = (byte) ((pixels[(y * width) + x] >> 16) & 0xFF);
                            data[pointer + 1] = (byte) ((pixels[(y * width) + x] >> 8) & 0xFF);
                            data[pointer + 2] = (byte) (pixels[(y * width) + x] & 0xFF);
                        } else {
                            int alphaValue = ((pixels[(y * width) + x] >> 24) & 0xFF);
                            data[pointer + 0] = (byte) (((data[pointer + 0] * (255 - alphaValue)) + (alphaValue * ((pixels[(y * width)
                                                                                                                           + x] >> 16) & 0xFF))) / 255);
                            data[pointer + 1] = (byte) (((data[pointer + 1] * (255 - alphaValue)) + (alphaValue * ((pixels[(y * width)
                                                                                                                           + x] >> 8) & 0xFF))) / 255);
                            data[pointer + 2] = (byte) (((data[pointer + 2] * (255 - alphaValue)) + (alphaValue * (pixels[(y * width)
                                                                                                                          + x] & 0xFF))) / 255);
                        }
                    }
                }
            }
        }
        /** only if there is something to be saved, save it */
        if (bPartExists) {
            try {
                if (bVerbose) {
                    System.out.print("Saving file " + /*pathName+*/octName + " ... ");
                }
                FileOutputStream fos = new FileOutputStream(pathName + octName);
                fos.write(data);
                fos.close();
                if (bVerbose) {
                    System.out.println("OK");
                }
            } catch (FileNotFoundException e) {
                if (bVerbose) {
                    System.out.println("Error");
                }
                if (bVerbose) {
                    e.printStackTrace();
                }
            } catch (IOException e1) {
                if (bVerbose) {
                    System.out.println("Error");
                }
                if (bVerbose) {
                    e1.printStackTrace();
                }
            }
        }
        ;
    }

    /**
     * loads a image and returns a reference to the loaded object
     * @param imageFileName
     * @return loaded image or null
     */
    public static BufferedImage loadBuffImage(String imageFileName) {
        BufferedImage res = null;
        try {
            if (imageFileName == null) {
                return null;
            }
            ImageIcon icon = new ImageIcon(imageFileName);//imageFileName);//imageURL);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                throw new Exception("failed");
            }
            Component obs = new Component() {
                private static final long serialVersionUID = -8930221173662062083L;
            };
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D big = res.createGraphics();
            icon.paintIcon(obs, big, 0, 0);
            big.dispose();
        } catch (Exception e) {
            res = null;
            //System.out.println("\nFailed loading image from "+imageFileName);
            //e.printStackTrace();
        }
        return res;
    }

    private static final String sFileSeparator = System.getProperty("file.separator");

    /**
     * the x-th and y-th texture on x and y if all textures are at level resolution
     * @param xp
     * @param yp
     * @param level
     * @return vector with 2 string values: the base name and path;<br>
     * full name is obtained like this: vect[1]+"map"+vect[0]+".oct"
     */
    private String[] getSlicePath(int xp, int yp, int level) {
        String sBase, sPath;
        if (level == 0) {
            sBase = "_" + (yp + 1) + "." + (xp + 1);
            sPath = sPicFilePath + "lia" + sFileSeparator + "images" + sFileSeparator + "map0" + sFileSeparator;
        } else {
            /*            int nx = getNX(level-1);
             int ny = getNY(level-1);
             int xl = xp% nx;
             int yl = yp% ny;
             xp/=nx;
             yp/=ny;
             */
            int xl = xp % texturesX[level];
            int yl = yp % texturesY[level];
            xp /= texturesX[level];
            yp /= texturesY[level];
            String[] sVect = getSlicePath(xp, yp, level - 1);
            String sParentBase = sVect[0];
            String sParentPath = sVect[1];
            sBase = sParentBase + "_" + (yl + 1) + "." + (xl + 1);
            sPath = sParentPath + "map" + level + sParentBase + sFileSeparator;
        }
        ;
        return new String[] { sBase, sPath };
    }

    /** computes position on x axis in full image given the longitude */
    private float long2x(float LONG, int total_width) {
        return (((LONG + 180f) * total_width) / 360f);
    }

    /** computes position on y axis in full image given the latitude */
    private float lat2y(float LAT, int total_height) {
        return (((-LAT + 90) * total_height) / 180f);
    }

    /** computes longitude given the position on x axis and the full image width */
    //  private float x2long(int x, int total_width) { return x*360f/total_width; }
    /** computes latitude given the position on y axis and the full image height */
    //  private float y2lat(int y, int total_height) { return (total_height*.5f-y)*180f/total_height; }
    /**
     * computes the total number of textures on x for the given level,<br>
     * supposing they all are at same resolution
     * @param level starts at 0
     * @return number
     */
    int getNX(int level) {
        int nx = 1;
        for (int i = 0; i <= level; i++) {
            nx *= texturesX[i];
        }
        return nx;
    }

    /**
     * computes the total number of textures on y for the given level,<br>
     * supposing they all are at same resolution
     * @param level starts at 0
     * @return number
     */
    int getNY(int level) {
        int ny = 1;
        for (int i = 0; i <= level; i++) {
            ny *= texturesY[i];
        }
        return ny;
    }

    public static void main(String[] args) {
        PicSplitter ps = new PicSplitter();
        int ret = ps.doWork();
        System.out.println("Work done! Bug report: mluc@cs.pub.ro");
        System.exit(ret);
    }

}
