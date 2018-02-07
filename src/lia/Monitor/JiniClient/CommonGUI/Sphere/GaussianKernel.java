/*
 * @(#)GaussianKernel.java
 *
 * Copyright (c) 1999 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license to use,
 * modify and redistribute this software in source and binary code form,
 * provided that i) this copyright notice and license appear on all copies of
 * the software; and ii) Licensee does not utilize the software in a manner
 * which is disparaging to Sun.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE
 * LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
 * OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS
 * LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF
 * OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 *
 * This software is not designed or intended for use in on-line control of
 * aircraft, air traffic, aircraft navigation or aircraft communications; or in
 * the design, construction, operation or maintenance of any nuclear
 * facility. Licensee represents and warrants that it will not use or
 * redistribute the Software for such purposes.
 */

package lia.Monitor.JiniClient.CommonGUI.Sphere;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.net.MalformedURLException;

import javax.swing.JFrame;


/**
 * Extends the Kernel class by offering a new constructor which
 * takes the Kernel size and automatically generates a gaussian
 * matrix.
 *
 * @author         Vincent Hardy
 * @version        1.0, 09/29/1998
 * @see            com.sun.glf.goodies.ElevationMap
 */
public class GaussianKernel extends Kernel{
  /**
   * This kernel's radius
   */
  private int radius;

  /**
   * @param radius number of pixels, on each size, which are taken
   *        into account by the Kernel.
   */
  public GaussianKernel(int radius){
    super(2*radius+1, 2*radius+1, getGaussianKernel(radius));
    this.radius = radius;
  }

  /**
   * @returns kernel for a Gaussian blur convolve
   */
  public static float[] getGaussianKernel(int radius) {
    float kernel[] = new float[ (radius * 2 + 1)*(radius * 2 + 1)];
    double sum = 0.;
    int w = 2*radius+1;

    double deviation = radius/3.; // This guarantees non zero values in the kernel
    double devSqr2 = 2*Math.pow(deviation, 2);
    double piDevSqr2 = Math.PI*devSqr2;

    for(int i=0; i<w; i++){
      for(int j=0; j<w; j++){
	kernel[i*w+j] 
	  = (float)(Math.pow(Math.E, 
			     -((j-radius)*(j-radius) + (i-radius)*(i-radius))/devSqr2)
		    /
		    piDevSqr2);
	sum += kernel[i*w+j];			    
      }
    }

    // Make elements sum to 1
    for(int i=0; i<w; i++){
      for(int j=0; j<w; j++){
	kernel[i*w+j] /= sum;
      }
    }

    return(kernel);
  } 

  public Kernel[] separateKernel(){
    float kernel[] = new float[radius * 2 + 1];
    double sum = 0.;
    int w = 2*radius+1;

    double deviation = radius/3.; // This guarantees non zero values in the kernel
    double devSqr2 = 2*Math.pow(deviation, 2);
    double piDevSqr2 = Math.PI*devSqr2;
    double piDevSqrt = Math.sqrt(piDevSqr2);

    for(int i=0; i<w; i++){
      kernel[i] 
	= (float)(Math.pow(Math.E, 
			   -((i-radius)*(i-radius))/devSqr2)
		  /
		  piDevSqrt);
      sum += kernel[i];			    
    }

    // Make elements sum to 1
    for(int i=0; i<w; i++){
      kernel[i] /= sum;
    }
  
    // Build two kernels
    Kernel hk = new Kernel(w, 1, kernel);
    Kernel vk = new Kernel(1, w, kernel);

    return new Kernel[]{hk, vk};
  }

  static final String USAGE = "java com.sun.glf.goodies.GaussianKernel <source image>";

  /**
   * Unit testing
 * @throws MalformedURLException 
   */
  public static void main(String args[]) throws MalformedURLException{
    /*
    for(int i=0; i<10; i++){
      traceKernel(getGaussianKernel(i+1));
    }*/

    if(args.length<1){
      System.out.println(USAGE);
      System.exit(0);
    }

    BufferedImage src = Toolbox.loadImage(args[0], BufferedImage.TYPE_INT_ARGB_PRE);
    
    int width = 25;
    if(args.length>1)
      width = Integer.parseInt(args[1]);

    System.out.println("Using radius : " + width);

    GaussianKernel gk = new GaussianKernel(width);
    
    //
    // Convolve with separated kernels
    //
    Kernel ka[] = gk.separateKernel();

    ConvolveOp hBlur = new ConvolveOp(ka[0]);
    ConvolveOp vBlur = new ConvolveOp(ka[1]);
    CompositeOp compositeBlur = new CompositeOp(hBlur, vBlur);

    //
    // Display result
    //
    Dimension size = new Dimension(src.getWidth(), src.getHeight()*3);
    LayerComposition cmp = new LayerComposition(size);
    ImageLayer hBlurLayer = new ImageLayer(cmp, src, Position.TOP);
    ImageLayer vBlurLayer = new ImageLayer(cmp, src, Position.CENTER);
    ImageLayer compositeBlurLayer = new ImageLayer(cmp, src, Position.BOTTOM);

    Dimension blurMargins = new Dimension(width*2, width*2);
    hBlurLayer.setImageFilter(hBlur, blurMargins);
    vBlurLayer.setImageFilter(vBlur, blurMargins);
    compositeBlurLayer.setImageFilter(compositeBlur, blurMargins);

    cmp.setLayers(new Layer[]{ hBlurLayer, vBlurLayer, compositeBlurLayer});

    
    CompositionComponent comp = new CompositionComponent(cmp);
    JFrame frame = new JFrame();
    frame.getContentPane().add(comp);
    frame.pack();
    frame.setVisible(true);
  }

  public static void mainDirect(String args[]) throws MalformedURLException{
    /*
    for(int i=0; i<10; i++){
      traceKernel(getGaussianKernel(i+1));
    }*/

    if(args.length<1){
      System.out.println(USAGE);
      System.exit(0);
    }

    BufferedImage src = Toolbox.loadImage(args[0], BufferedImage.TYPE_INT_ARGB_PRE);
    
    int width = 25;
    if(args.length>1)
      width = Integer.parseInt(args[1]);

    System.out.println("Using radius : " + width);

    GaussianKernel gk = new GaussianKernel(width);
    
    //
    // First, convolve with gk
    //
    ConvolveOp blur = new ConvolveOp(gk);
    long start = System.currentTimeMillis();
    BufferedImage simpleBlur = blur.filter(src, null);
    System.out.println("Square convolution took: " + (System.currentTimeMillis() - start));
    

    //
    // Now, convolve with separated kernels
    //
    Kernel ka[] = gk.separateKernel();

    blur = new ConvolveOp(ka[0]);
    start = System.currentTimeMillis();
    BufferedImage hBlur = blur.filter(src, null);
    System.out.println("Horizontal convolution took: " + (System.currentTimeMillis() - start));

    blur = new ConvolveOp(ka[1]);
    start = System.currentTimeMillis();
    BufferedImage vBlur = blur.filter(hBlur, null);
    System.out.println("Vertical convolution took: " + (System.currentTimeMillis() - start));
    
    //
    // Display result
    //
    Dimension size = new Dimension(src.getWidth()*2, src.getHeight()*2);
    LayerComposition cmp = new LayerComposition(size);
    cmp.setLayers(new Layer[]{ new ImageLayer(cmp, src, Position.BOTTOM_LEFT),
				 new ImageLayer(cmp, simpleBlur, Position.TOP_RIGHT) ,
				 new ImageLayer(cmp, hBlur, Position.BOTTOM_RIGHT),
				 new ImageLayer(cmp, vBlur, Position.TOP_LEFT) });

    
    CompositionComponent comp = new CompositionComponent(cmp);
    JFrame frame = new JFrame();
    frame.getContentPane().add(comp);
    frame.pack();
    frame.setVisible(true);
  }

  public static void traceKernel(float k[]){
    int w = (int)Math.sqrt(k.length);
    for(int i=0; i<w; i++){
      for(int j=0; j<w; j++){
	int v = Math.round(k[i*w+j]*10000);
	trace(v);
	System.out.print("  ");
      }
      System.out.println();
    }

    System.out.println();
  }

  public static void trace1DKernel(float k[]){
    for(int i=0; i<k.length; i++){
      int v = Math.round(k[i]*10000);
      trace(v);
      System.out.print("  ");
    }

    System.out.println();
  }

  public static void trace(int i){
    if(i<10)
      System.out.print("0000");
    else if(i<100)
      System.out.print("000");
    else if(i<1000)
      System.out.print("00");
    else if(i<10000)
      System.out.print("0");
    System.out.print(i);
  }
}
