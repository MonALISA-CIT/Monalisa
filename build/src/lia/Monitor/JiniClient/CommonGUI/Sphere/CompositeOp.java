/*
 * @(#)CompositeOp.java
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
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.LookupOp;
import java.awt.image.LookupTable;
import java.net.MalformedURLException;

import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * A CompositeOp allows chaining of filters. By combining several 
 * BufferedImageOp in a CompositeOp, it is possible to create a 
 * filter pipeline.
 *
 * @author             Vincent Hardy
 * @version            1.0, 09/30/1998
 */
public class CompositeOp implements BufferedImageOp {
  /** Filter chain */
  BufferedImageOp filters[];

  /**
   * @param firstFilter first filter in the chain
   * @param secondFilter second filter in the filter chain
   */
  public CompositeOp(BufferedImageOp firstFilter, BufferedImageOp secondFilter){
    this(new BufferedImageOp[]{firstFilter, secondFilter});
  }

  /**
   * @param filters set of filters which should be combined into a single
   *        chain.
   */
  public CompositeOp(BufferedImageOp filters[]){
    if(filters==null || filters.length<1)
      throw new IllegalArgumentException();

    synchronized(filters){
      this.filters = new BufferedImageOp[filters.length];
      System.arraycopy(filters, 0, this.filters, 0, filters.length);
    }

    for(int i=0; i<this.filters.length; i++)
      if(this.filters[i]==null)
	throw new IllegalArgumentException();
  }

  /**
   * Performs a single-input/single-output operation on a BufferedImage.
   * If the color models for the two images do not match, a color
   * conversion into the destination color model will be performed.
   * If the destination image is null,
   * a BufferedImage with an appropriate ColorModel will be created.
   * The IllegalArgumentException may be thrown if the source and/or
   * destination image is incompatible with the types of images allowed
   * by the class implementing this filter.
   */
  public BufferedImage filter(BufferedImage src, BufferedImage dest){
    for(int i=0; i<filters.length-1; i++){
      src = filters[i].filter(src, null);
    }
    return filters[filters.length-1].filter(src, dest);
  }
  
  /**
   * Returns the bounding box of the filtered destination image.
   * This uses the getPoint2D method of each filter to process
   * the bounds of the resulting image. Note that this may fail
   * for filters which would transform pixels in a non-linear way.
   * <p> 
   * The IllegalArgumentException may be thrown if the source
   * image is incompatible with the types of images allowed
   * by the class implementing the first filter in the chain.
   */
  public Rectangle2D getBounds2D (BufferedImage src){
    Rectangle2D bounds2D = filters[0].getBounds2D(src);
    double xMin=0, yMin=0, xMax=0, yMax=0;
    for(int i=1; i<filters.length; i++){
      // Use getPoint2D to get the transformed bounds
      Point2D tl = new Point2D.Double(bounds2D.getX(), bounds2D.getY());
      Point2D tr = new Point2D.Double(bounds2D.getX()+bounds2D.getWidth(), bounds2D.getY());
      Point2D br = new Point2D.Double(bounds2D.getX()+bounds2D.getWidth(), bounds2D.getY() + bounds2D.getHeight());
      Point2D bl = new Point2D.Double(bounds2D.getX(), bounds2D.getY() + bounds2D.getHeight());
      tl = filters[i].getPoint2D(tl, null);
      tr = filters[i].getPoint2D(tr, null);
      br = filters[i].getPoint2D(br, null);
      bl = filters[i].getPoint2D(bl, null);

      Point2D cur = tl;
      Point2D pts[] = {tr, br, bl};
      xMin = cur.getX();
      yMin = cur.getY();
      xMax = xMin;
      yMax = yMin;
      for(int j=0; j<3; j++){
	xMin = Math.min(xMin, pts[j].getX());
	yMin = Math.min(yMin, pts[j].getY());
	xMax = Math.max(xMax, pts[j].getX());
	yMax = Math.max(yMax, pts[j].getY());
      }

      bounds2D = new Rectangle2D.Double(xMin, yMin, xMax-xMin, yMax-yMin);
    }
    return bounds2D;
  }
  
  /**
   * 
   * @param src       Source image for the filter operation.
   * @param destCM    ColorModel of the destination.  If null, the
   *                  ColorModel of the source will be used.
   */
  public BufferedImage createCompatibleDestImage (BufferedImage src,
						  ColorModel destCM){
    return filters[filters.length-1].createCompatibleDestImage(src, destCM);
  }
  
  /**
   * Returns the location of the destination point given a
   * point in the source image.  If dstPt is non-null, it
   * will be used to hold the return value.
   */
  public Point2D getPoint2D (Point2D srcPt, Point2D dstPt){
    for(int i=0; i<filters.length; i++)
      dstPt = filters[i].getPoint2D(srcPt, dstPt);
    return dstPt;
  }
  
  /**
   * Returns the rendering hints for this BufferedImageOp.  Returns
   * null if no hints have been set.
   */
  public RenderingHints getRenderingHints(){
    return filters[filters.length-1].getRenderingHints();
  }

  //
  // Unit testing
  //
  
  static final String USAGE = "java com.sun.glf.goodies.CompositeOp <imageFile>";
  
  public static void main(String args[]) throws MalformedURLException{
    // Check command line
    if(args.length<1 ){
      System.out.println(USAGE);
      System.exit(0);
    }

    final String fileName = args[0];
    JFrame frame = new JFrame("CompositeOp unit testing");
    frame.getContentPane().setLayout(new GridLayout(0, 1));

    BufferedImage image = Toolbox.loadImage(fileName, BufferedImage.TYPE_INT_RGB);
    BufferedImage filtered = new BufferedImage(image.getWidth(null),
					       image.getHeight(null),
					       BufferedImage.TYPE_INT_RGB);
    int w = filtered.getWidth();
    int h = filtered.getHeight();
    Graphics2D g = filtered.createGraphics();
    g.drawImage(image, 0, 0, null);
    Dimension dim = new Dimension(w, h);
    
    //
    // Prepare BufferedImageOp chain
    //
    BufferedImageOp txformFilter = new AffineTransformOp( AffineTransform.getShearInstance(0.5, 0), null);
    BufferedImageOp blurFilter = new ConvolveOp(new GaussianKernel(5));
    byte lookup[] = new byte[256];
    for(int i=0; i<256; i++)
      lookup[i] = (byte)(255-i);
    LookupTable lookupTable = new ByteLookupTable(0, lookup);
    BufferedImageOp lookupFilter = new LookupOp(lookupTable, null);
    BufferedImageOp compositeFilter = new CompositeOp(new BufferedImageOp[]{ lookupFilter, blurFilter, txformFilter });
    
    //
    // Filter images
    //
    BufferedImage lookupImage = lookupFilter.filter(filtered, null);
    BufferedImage txformedImage = txformFilter.filter(filtered, null);
    BufferedImage bluredImage = blurFilter.filter(filtered, null);
    BufferedImage compositedImage = compositeFilter.filter(filtered, null);

    
    frame.getContentPane().add(makeNewComponent(filtered, "Original Image"));
    frame.getContentPane().add(makeNewComponent(lookupImage, "LookupOp"));
    frame.getContentPane().add(makeNewComponent(txformedImage, "AffineTransformOp"));
    frame.getContentPane().add(makeNewComponent(bluredImage, "ConvolveOp"));
    frame.getContentPane().add(makeNewComponent(compositedImage, "CompositeOp"));
	      
    frame.pack();
    frame.setVisible(true);
  }

  private static JComponent makeNewComponent(Image image, String toolTip){
    Dimension dim = new Dimension(image.getWidth(null),
				  image.getHeight(null));
    LayerComposition cmp = new LayerComposition(dim);
    ImageLayer layer = new ImageLayer(cmp, image, null);
    cmp.setLayers(new Layer[]{layer});

    CompositionComponent comp = new CompositionComponent(cmp);
    comp.setToolTipText(toolTip);
    return comp;
  }
}
