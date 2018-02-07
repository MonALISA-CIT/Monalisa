/*
 * @(#)GradientPaintExt.java
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
import java.awt.Dimension;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

import javax.swing.JComponent;

/**
 * This class in another linear gradient implementation, like the standard 
 * GradientPaint class. However, this GradientPaintExt class defines a 
 * multiple color gradient.
 *
 * @author       Vincent Hardy
 * @version      1.0, 09.16.1998
 * @see          java.awt.GradientPaint
 * @see          com.sun.glf.goodies.RadialGradientPaint
 * @see          com.sun.glf.goodies.RadialGradientPaintExt
 * @see          java.awt.Paint
 */
public class GradientPaintExt implements Paint {
  /** Gradient start and end points */
  private Point2D.Float start, end;

  /** Normalized interval values */
  private float I[];

  /** Gradient colors */
  private Color colors[];

  /** Transparency */
  private int transparency;

  /** True if gradient should cycle between the colors. */
  private boolean cyclic;

  /**
   * @return colors used by this gradient
   */
  public Color[] getColors(){
    Color colors[] = new Color[this.colors.length];
    System.arraycopy(this.colors, 0, colors, 0, this.colors.length);
    return colors;
  }

  /**
   * @return intervals used by this gradient
   */
  public float[] getIntervals(){
    float I[] = new float[this.I.length];
    System.arraycopy(this.I, 0, I, 0, this.I.length);
    return I;
  }

  /**
   * @return true if gradient is cyclic
   */
  public boolean isCyclic(){
    return cyclic;
  }

  /**
   * @return start point for the gradient
   */
  public Point2D getStart(){
    return (Point2D)start.clone();
  }

  /**
   * @return end point for the gradient
   */
  public Point2D getEnd(){
    return (Point2D)end.clone();
  }

  /**
   * @param x x coordinate of the gradient axis start point
   * @param y y coordinate of the gradient axis start point
   * @param dx x coordinate of the gradient axis end point
   * @param dy y coordinate of the gradient axis end point
   * @param I array of intervals on the gradient axis
   * @param colors array of colors on the gradient axis
   *
   * @exception IllegalArgumentException if start and end points are the same or if
   *            I.length != colors.length -1 or colors is less than two in size.
   */
  public GradientPaintExt(float x, float y, float dx, float dy, float I[], Color colors[]){
    this(x, y, dx, dy, I, colors, false);
  }

  /**
   * @param x x coordinate of the gradient axis start point
   * @param y y coordinate of the gradient axis start point
   * @param dx x coordinate of the gradient axis end point
   * @param dy y coordinate of the gradient axis end point
   * @param I array of intervals on the gradient axis
   * @param colors array of colors on the gradient axis
   * @param cyclic true if the gradient pattern should cycle repeatedly between the colors.
   *
   * @exception IllegalArgumentException if start and end points are the same or if
   *            I.length != colors.length -1 or colors is less than two in size.
   */
  public GradientPaintExt(float x, float y, float dx, float dy, float I[], Color colors[], boolean cyclic){
    this(new Point2D.Float(x, y), new Point2D.Float(dx, dy), I, colors, cyclic);
  }

  /**
   * @param start coordinates of the gradient axis start point
   * @param end coordinates of the gradient axis end point
   * @param I array of intervals on the gradient axis
   * @param colors array of colors on the gradient axis
   *
   * @exception IllegalArgumentException if start and end points are the same points or if
   *            I.length != colors.length -1 or colors is less than two in size.
   */
  public GradientPaintExt(Point2D start, Point2D end, float I[], Color colors[]){
    this(start, end, I, colors, false);
  }

  /**
   * @param start coordinates of the gradient axis start point
   * @param end coordinates of the gradient axis end point
   * @param I array of intervals on the gradient axis
   * @param colors array of colors on the gradient axis
   * @param cyclic true if the gradient pattern should cycle repeatedly between the colors.
   *
   * @exception IllegalArgumentException if start and end points are the same points or if
   *            I.length != colors.length -1 or colors is less than two in size.
   */
  public GradientPaintExt(Point2D start, Point2D end, float I[], Color colors[], boolean cyclic){
    //
    // Check input parameters
    //
    if( (start== null || end==null)
	||
	start.equals(end)
	||
	(I==null || colors==null)
	||
	(I.length != colors.length-1)
	||
	(colors.length<2) ){
      throw new IllegalArgumentException();
    }

    this.cyclic = cyclic;

    this.start = new Point2D.Float((float)start.getX(), (float)start.getY());
    this.end = new Point2D.Float((float)end.getX(), (float)end.getY());

    float temp[] = new float[I.length];
    System.arraycopy(I, 0, temp, 0, I.length);
    I = temp;

    //
    // Normalize interval and check values are positive.
    //
    float sum = 0;
    for(int i=0; i<I.length; i++){
      if(I[i]<=0)
	throw new IllegalArgumentException("Cannot use negative or null interval: " + I[i]);
      sum += I[i];
    }

    for(int i=0; i<I.length; i++)
      I[i] /= sum;

    this.I = I;
    
    this.colors = new Color[colors.length];
    System.arraycopy(colors, 0, this.colors, 0, colors.length);   

    // Process transparency
    boolean opaque = true;
    for(int i=0; i<colors.length; i++){
      opaque = opaque && (colors[i].getAlpha()==0xff);
    }

    if(opaque) transparency = OPAQUE;
    else transparency = TRANSLUCENT;
  }

  
  /**
   * Creates and returns a context used to generate the color pattern.
   */
  public PaintContext createContext(ColorModel cm,
				    Rectangle deviceBounds,
				    Rectangle2D userBounds,
				    AffineTransform transform,
				    RenderingHints hints) {
    try{
      return new GradientPaintExtContext(start, end, I, colors, transform, cyclic);
    }catch(NoninvertibleTransformException e){
      e.printStackTrace();
      throw new IllegalArgumentException();
    }
  }

  /**
   * Return the transparency mode for this GradientPaint.
   * @see Transparency
   */
  public int getTransparency() {
    return transparency;
  }

  private static JComponent makeNewComponent(Dimension dim, Shape shape, Renderer renderer, AffineTransform transform, String toolTip){
    LayerComposition cmp = new LayerComposition(dim);
    Rectangle rect = new Rectangle(-1, -1, dim.width, dim.height);
    ShapeLayer layer = new ShapeLayer(cmp, shape, renderer);
    layer.setTransform(transform);
    ShapeLayer boundsLayer = new ShapeLayer(cmp, rect, new StrokeRenderer(Color.black, 1));
    cmp.setLayers(new Layer[]{layer, boundsLayer});
    CompositionComponent comp = new CompositionComponent(cmp);
    comp.setToolTipText(toolTip);
    return comp;
  }
}


