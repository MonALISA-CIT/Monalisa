/*
 * @(#)GradientPaintExtContext.java
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

import java.awt.Color;
import java.awt.PaintContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;

/**
 * 
 * @author       Vincent Hardy
 * @version      1.1, 04/05/1999 Improved algorithm
 *               1.0, 09/16/1998
 * @see          java.awt.GradientPaintContext
 * @see          java.awt.Paint
 * @see          java.awt.GradientPaint
 * @see          com.sun.glf.goodies.GradientPaintExt
 * @see          com.sun.glf.goodies.RadialGradientPaint
 * @see          com.sun.glf.goodies.MultiRadialGradientPaint
 * @see          com.sun.glf.goodies.SpotPaint
 */
class GradientPaintExtContext implements PaintContext {
  /**
   * The following constants are used to process the gradient value from 
   * a device space coordinate, (X, Y):
   * g(X, Y) = dgdX*X + dgdY*Y + gc
   */
  float dgdX, dgdY, gc;

  /**
   * gradients contains the interpolated color values for each interval
   */
  int gradient[];

  /** Normalized interval values */
  float I[];

  /** Color model used if gradient colors are all opaque */
  static ColorModel xrgbmodel = new DirectColorModel(24, 0x00ff0000, 0x0000ff00, 0x000000ff);

  /** Raster is reused whenever possible */
  Raster saved;

  /** 
   * PaintContext's ColorModel ARGB if colors are not all opaque.
   * RGB otherwise.
   */
  ColorModel model;

  /** cycle through the gradient pattern or not */
  boolean cyclic;

  static int c;

  /**
   * @param start gradient start point, in user space
   * @param end gradient end point, in user space
   * @param I gradient ratios. Values sum to 1
   * @param colors the gradient colors
   * @param transform user space to device space transform.
   */
  public GradientPaintExtContext(Point2D.Float start, 
				 Point2D.Float end, 
				 float I[],
				 Color colors[], 
				 AffineTransform t,
				 boolean cyclic) throws NoninvertibleTransformException
  {
    this.cyclic = cyclic;
    if(cyclic){
      end = (Point2D.Float)end.clone();

      // First, create 'real' end point.
      end.x += (end.x - start.x);
      end.y += (end.y - start.y);

      // Create inverted color array
      Color tmpColors[] = new Color[colors.length*2-1];
      System.arraycopy(colors, 0, tmpColors, 0, colors.length);
      for(int i=0; i<colors.length-1; i++)
	tmpColors[colors.length+i] = colors[colors.length-i-2];

      // Create inverted interval array. Keep interval array normalized
      float  tmpI[] = new float[I.length*2];
      for(int i=0; i<I.length; i++)
	tmpI[i] = I[i]/2;
      for(int i=0; i<I.length; i++) 
	tmpI[I.length+i] = I[I.length-i-1]/2;

      I = tmpI;
      colors = tmpColors;
    }

    // The inverse transform is needed to compute the gradient value
    // in device space.
    AffineTransform tInv = t.createInverse();
    
    // Order gradient controls in increasing x-axis order, in device space
    Point2D.Float startDS = new Point2D.Float();
    Point2D.Float endDS = new Point2D.Float();
    t.transform(start, startDS);
    t.transform(end, endDS);
    if(startDS.x > endDS.x){
      Point2D.Float tmp = start;
      start = end; end = tmp;

      // Invert intervals and colors
      float tmpI[] = new float[I.length];
      Color tmpColors[] = new Color[colors.length];
      for(int i=0; i<tmpI.length; i++) tmpI[i] = I[tmpI.length-i-1];
      for(int i=0; i<tmpColors.length; i++) tmpColors[i] = colors[tmpColors.length-i-1];

      I = tmpI;
      colors = tmpColors;
    }
    
    this.I = I;

    // Process gradient delta for unit variations of x and y axis, in device space
    double m[] = new double[6];
    tInv.getMatrix(m);
    float a00 = (float)m[0];
    float a10 = (float)m[1];
    float a01 = (float)m[2];
    float a11 = (float)m[3];
    float a02 = (float)m[4];
    float a12 = (float)m[5];

    float dx = end.x - start.x;
    float dy = end.y - start.y;
    float dSq = dx*dx + dy*dy;

    dgdX = a00*dx/dSq + a10*dy/dSq;
    dgdY = a01*dx/dSq + a11*dy/dSq;
    gc = (a02-start.x)*dx/dSq + (a12-start.y)*dy/dSq;

    //
    // Process interpolated color values
    // The smallest interval determines the size of the
    // interpolation array.
    //

    // Find smallest interval
    int n = I.length;
    float Imin = 1;
    for(int i=0; i<n; i++)
      Imin = Imin>I[i]?I[i]:Imin;

    // Now, allocate gradients array based on minimal interval size
    int transparencyTest = 0xff000000;
    int gradients[][] = new int[n][];
    int gradientsTot = 1;
    int rgb1=0, rgb2=0;
    for(int i=0; i<n; i++){
      int nGradients = (int)((I[i]/Imin)*255f);
      gradientsTot += nGradients;
      gradients[i] = new int[nGradients];
      float stepSize = 1/(float)nGradients;

      rgb1 = colors[i].getRGB();
      rgb2 = colors[i+1].getRGB();

      int a1 = (rgb1 >> 24) & 0xff;
      int r1 = (rgb1 >> 16) & 0xff;
      int g1 = (rgb1 >>  8) & 0xff;
      int b1 = (rgb1      ) & 0xff;
      int da = ((rgb2 >> 24) & 0xff) - a1;
      int dr = ((rgb2 >> 16) & 0xff) - r1;
      int dg = ((rgb2 >>  8) & 0xff) - g1;
      int db = ((rgb2      ) & 0xff) - b1;
      
      transparencyTest &= rgb1;
      transparencyTest &= rgb2;
      
      for (int j = 0; j < nGradients; j++) {
	int rgb =
	  (((int) (a1 + j* da * stepSize)) << 24) |
	  (((int) (r1 + j* dr * stepSize)) << 16) |
	  (((int) (g1 + j* dg * stepSize)) <<  8) |
	  (((int) (b1 + j* db * stepSize))      );
	
	gradients[i][j] = rgb;
      }
    }

    // Put all gradients in a single array
    gradient = new int[gradientsTot];
    int curOffset = 0;
    for(int i=0; i<n; i++){
      System.arraycopy(gradients[i], 0, gradient, curOffset, gradients[i].length);
      curOffset += gradients[i].length;
    }
    gradient[gradient.length-1] = colors[colors.length-1].getRGB();

    // Scale gradient calculation so that it varies between 0 and gradientMaxIndex
    int gradientMaxIndex = gradient.length-1; 
    dgdX *= gradientMaxIndex;
    dgdY *= gradientMaxIndex;
    gc *= gradientMaxIndex;

    // Use the most 'economical' model.
    if((transparencyTest >>> 24) == 0xff) 
      model = xrgbmodel;
    else 
      model = ColorModel.getRGBdefault();
  
  }

  /**
   * Release the resources allocated for the operation.
   */
  public void dispose() {
    saved = null;
  }
  
  /**
   * Return the ColorModel of the output.
   */
  public ColorModel getColorModel() {
    return model;
  }
  
  /**
   * Return a Raster containing the colors generated for the graphics
   * operation.
   * @param x,y,w,h The area in device space for which colors are
   * generated.
   */
  public Raster getRaster(int x, int y, int w, int h) {
    //
    // If working raster is big enough, reuse it. Otherwise,
    // build a large enough new one.
    //
    Raster raster = saved;
    if (raster == null || raster.getWidth() < w || raster.getHeight() < h) {
      raster = getColorModel().createCompatibleWritableRaster(w, h);
      saved = raster;
    }
    
    //
    // Access raster internal int array. Because we use a DirectColorModel,
    // we know the DataBuffer is of type DataBufferInt and the SampleModel
    // is SinglePixelPackedSampleModel.
    // Adjust for initial offset in DataBuffer and also for the scanline stride.
    //
    DataBufferInt rasterDB = (DataBufferInt)raster.getDataBuffer();
    int pixels[] = rasterDB.getBankData()[0];
    int off = rasterDB.getOffset();
    int scanlineStride = ((SinglePixelPackedSampleModel)raster.getSampleModel()).getScanlineStride();
    int adjust = scanlineStride - w;

    if(!cyclic)
      fillRaster(pixels, off, adjust, w, h, x, y);
    else
      fillCyclicRaster(pixels, off, adjust, w, h, x, y);

    return raster;
  }
  
  void fillCyclicRaster(int[] pixels, int off, int adjust, int w, int h,
			float X, float Y) {
    //
    // Process each row in turn
    //
    int n = I.length;                // Number of intervals
    float gs = 0, g = 0;             // Start and current values for row gradients
    int rgb=0;                       // Working rgb value
    float ci=0;                      // Color index in gradients array.
    int k=0;                         // Used to indentifying starting interval
    int rowLimit = off + w;          // Used to end iteration on rows
    float gradientMaxIndex = gradient.length-1;

    for(int i=0; i<h; i++){
      gs = dgdX*X + dgdY*(Y+i) + gc;
      gs %= gradientMaxIndex;
      if(gs<0) gs = gradientMaxIndex+gs;

      g = gs;
      while(off < rowLimit){
	while(g<=gradientMaxIndex && off < rowLimit){
	  pixels[off++] = gradient[(int)g];
	  g += dgdX;
	}
	g %= gradientMaxIndex;
      }

      off += adjust;
      rowLimit = off + w;
    }
  }

  void fillRaster(int[] pixels, int off, int adjust, int w, int h,
		      float X, float Y) {
    //
    // Process each row in turn
    //
    int n = I.length;                // Number of intervals
    float gs = 0, ge = 0, g = 0;     // Start, end and current values for row gradients
    int rgb=0;                       // Working rgb value
    float ci=0;                      // Color index in gradients array.
    int k=0;                         // Used to indentifying starting interval
    int rowLimit = off + w;          // Used to end iteration on rows
    float gradientMaxIndex = gradient.length-1;

    for(int i=0; i<h; i++){
      gs = dgdX*X + dgdY*(Y+i) + gc;
      ge = gs + dgdX*w;

      if(gs>=gradientMaxIndex){ 
	// All row is to the right of the last interval
	rgb = gradient[(int)gradientMaxIndex];
	while(off<rowLimit) pixels[off++] = rgb;
      }
      else if(ge<=0){
	rgb = gradient[0];
	while(off<rowLimit) pixels[off++] = rgb;
      }
      else{
	g = gs;

	// Left of first interval : pad with first color to the left.
	rgb = gradient[0];
	while(g<=0 && off<rowLimit){
	  pixels[off++] = rgb;
	  g += dgdX;
	}

	while(g<=gradientMaxIndex && off<rowLimit){
	  // Process starting index in color array
	  pixels[off++] = gradient[(int)g];
	  g += dgdX;
	}
  
	// Give last color to remaining pixels : pad with last color to the right
	rgb = gradient[(int)gradientMaxIndex];
	while(off<rowLimit){
	  pixels[off++] = rgb;
	}

      }
      off += adjust;
      rowLimit = off + w;
    }
  }

}
