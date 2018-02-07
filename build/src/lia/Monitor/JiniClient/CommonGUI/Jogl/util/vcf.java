package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Vector;



public class vcf {
    //one vcf's properties
    public HashMap hmData = new HashMap();
    //list of vcf-s
    public static Vector vcfList= new Vector();
    public static void readVcfList(String vcfFile) 
    {
        vcfList.clear();
        ClassLoader myClassLoader = vcf.class.getClassLoader();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(myClassLoader.getResource(vcfFile).openStream()));
            String line;
            while ( (line=br.readLine()) != null ){
                if (line.equals("BEGIN:VCARD")){
                    //                System.err.println("START");
                    vcf v = new vcf();
                    vcfList.add(v);
                    StringBuilder sb = new StringBuilder();
                    while ( (line=br.readLine()) != null ){
                        if (line.equals("END:VCARD")){
                            if (sb.length()>0)
                                process(v, sb.toString());
                            break;
                        }
                        if (line.startsWith(" "))
                            sb.append('\n').append(line);
                        else{
                            if (sb.length()>0)
                                process(v, sb.toString());
                            sb = new StringBuilder(line);
                        }
                    }
//                    System.out.println("vcf info: Name="+(String)v.hmData.get("FN")
//                            +" Email="+(String)v.hmData.get("EMAIL")
//                            +" Image="+v.hmData.get("PHOTO"));
                };
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    private static void process(vcf v, String line)
    {
        if (line.length()<=0) {
            //            System.err.println("void line");
            return;
        };
        
        try {
            int nSeparatorPos = line.indexOf(":");
            String sKey = line.substring(0, nSeparatorPos).trim();
            String sContent = line.substring(nSeparatorPos+1).trim();
            
            if (sKey.equals("EMAIL") || sKey.equals("FN"))
                v.hmData.put(sKey, sContent);
            
            if (sKey.startsWith("PHOTO")){
                java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read( new ByteArrayInputStream(Base64Decoder.decodeToBytes(sContent)) );
                
                if (bi!=null) {
                    //System.err.println("Unknown image type");
                    //            else{
                    //                System.err.println("PHOTO OK");
                    //scale to be power of 2
                    int w = bi.getWidth();
                    int h= bi.getHeight();
//                    System.out.println("initial w="+w+" h="+h);
                    float whRaport = (float)w/(float)h;
                    int scaled_w=2;
                    while ( scaled_w*2 < w )
                        scaled_w*=2;
                    //scaled_w < w
                    //check to see which is closer of w: scaled_w or scaled_w*2;
                    if ( w-scaled_w < 2*scaled_w-w )
                        w = scaled_w;
                    else
                        w = 2*scaled_w;
                    //same for height
                    int scaled_h=2;
                    while ( scaled_h*2 < h )
                        scaled_h*=2;
                    //scaled_w < w
                    //check to see which is closer of w: scaled_w or scaled_w*2;
                    if ( h-scaled_h < 2*scaled_h-h )
                        h = scaled_h;
                    else
                        h = 2*scaled_h;
                    v.hmData.put("PHOTO", resizeAWT( bi, w, h));
                    v.hmData.put("whRaport", new Float(whRaport));
                }
            }
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public static BufferedImage resizeAWT(BufferedImage src, int newW, int newH) {
//        int w = src.getWidth();
//        int h = src.getHeight();
//      int newW = (int)(Math.floor(factorX * w));
//      int newH = (int)(Math.floor(factorY * h));
        Image temp = src.getScaledInstance(newW, newH, Image.SCALE_DEFAULT);
        BufferedImage tgt = createBlankImage(src, newW, newH);
        Graphics2D g = tgt.createGraphics();
        g.drawImage(temp, 0, 0, null);
        g.dispose();
        return tgt;
    }
    
    //Uses image to create another image of the same "type", but of a factored size
    public static BufferedImage createBlankImage(BufferedImage src, int w, int h) {
        int type = src.getType();
        if (type != BufferedImage.TYPE_CUSTOM)
            return new BufferedImage(w, h, type);
        else {
            ColorModel cm = src.getColorModel();
            WritableRaster raster = src.getRaster().createCompatibleWritableRaster(w, h);
            boolean isRasterPremultiplied = src.isAlphaPremultiplied();
            return new BufferedImage(cm, raster, isRasterPremultiplied, null);
        }
    }    
}
