/*
 * @(#)RadialGradientPaintExt.java
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
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * This class provides a way to fill a shape with a radial color gradient
 * pattern.
 * Given an Ellipse a set of colors and delta values, the paint will render 
 * a color starting at colors[0] at the center of the Ellipse to colors[n] at 
 * its boundary and progressively to the remaining colors, based on the interval
 * values. Those values should be positive. The interval vector is normalized to
 * represent ratios of distance between the focal points and the ellipse outer
 * bounds. The color along any line between the focal points and the ellipse
 * will progressively take all the color from colors[0] to the last colors at
 * intervals proportionals to the normalized values of the input interval vector.
 *
 * All pixels lying outside the Ellipse boundary have the last value in the 
 * input <code>colors</code> array.
 *
 * @author Vincent Hardy
 * @version 1.2, 04.06.1999, Now operates in user space.
 * @version 1.1, 03.20.1999, Modified construction logic.
 * @version 1.0, 09.11.1998
 */
public class RadialGradientPaintExt implements Paint {
  /** The Ellipse bounds */
  Rectangle2D.Float gradientBounds;

  /** Transparency */
  int transparency;

  /** The Ellipse controlling colors */
  Color colors[];

  /** Color intervals */
  float I[];

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
   * @return gradient bounds
   */
  public Rectangle2D getBounds(){
    return (Rectangle2D)gradientBounds.clone();
  }

  /**
   * @param gradientBounds the bounds of the outer Ellipse defining the gradient limit.
   * @param colors set of colors to use for the gradient. The first
   *        color is used at the center.
   * @param I set of interval values for the radial gradients. All values should
   *        be positive.
   *
   * @exception IllegalArgumentException if colors is less than 2 in size
   *            or if colors.length != I.length+1
   */
  public RadialGradientPaintExt(Rectangle2D bounds, Color colors[], 
				float I[]){
    // Check input arguments
    if(bounds==null)
      throw new IllegalArgumentException();
    if(colors==null)
      throw new IllegalArgumentException();
    if(I==null)
      throw new IllegalArgumentException();
    if(colors.length<2)
      throw new IllegalArgumentException();
    if(colors.length != I.length+1)
      throw new IllegalArgumentException();

    this.gradientBounds = new Rectangle2D.Float();
    gradientBounds.setRect((float)bounds.getX(), (float)bounds.getY(), 
			   (float)bounds.getWidth(), (float)bounds.getHeight());

    // Copy colors array
    this.colors = new Color[colors.length];
    System.arraycopy(colors, 0, this.colors, 0, colors.length);

    // Copy intervals array and normalize.
    float temp[] = new float[I.length];
    System.arraycopy(I, 0, temp, 0, I.length);
    I = temp;

    // Normalize interval
    float sum = 0;
    for(int i=0; i<I.length; i++){
      if(I[i]<=0)
	throw new IllegalArgumentException("Cannot use negative or null interval: " + I[i]);
      sum += I[i];
    }

    for(int i=0; i<I.length; i++)
      I[i] /= sum;

    this.I = I;

    // Process transparency
    boolean opaque = true;
    for(int i=0; i<this.colors.length; i++){
      opaque = opaque && (this.colors[i].getAlpha()==0xff);
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
      return (new RadialGradientPaintExtContext(gradientBounds, colors, I, transform));
    }catch(NoninvertibleTransformException e){
      throw new IllegalArgumentException("transform should be invertible");
    }
  }

  /**
   * Return the transparency mode for this GradientPaint.
   * @see Transparency
   */
  public int getTransparency() {
    return transparency;
  }

  /**
   * Unit testing
   */
  public static void main(String args[]){
    //
    // Create a frame and display LayerCompositions containing
    // ShapeLayers rendered with FillRenderer using RadialGradientPaint
    //
    JFrame frame = new JFrame("RadialGradientPaint unit testing");
    frame.getContentPane().setLayout(new GridLayout(0, 2));
    frame.getContentPane().setBackground(Color.white);
    Dimension dim = new Dimension(200, 100);

    //
    // First, create paints and renderers
    //
    Color colors[] = { Color.white, Color.yellow, new Color(128, 50, 50) };
    float I[] = {1, 1};

    Shape ellipse = new Ellipse2D.Double(0, 0, 150, 100);
    Shape disc = new Ellipse2D.Double(0, 0, 100, 100);
    Paint ellipsePaint = new RadialGradientPaintExt(ellipse.getBounds2D(),
						    colors, I);
    Paint discPaint = new RadialGradientPaintExt(disc.getBounds2D(), colors, I);
    Renderer ellipseFill = new FillRenderer(ellipsePaint);
    Renderer discFill = new FillRenderer(discPaint);

    Shape rect = new Rectangle(20, 20, 160, 60);

    //
    // Create LayerCompositions using different transforms
    //

    // No transforms
    AffineTransform transform = null;
    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "No transform"));
    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "No transform"));

    // Translation
    transform = AffineTransform.getTranslateInstance(40, 40);
    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Translation"));
    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Translation"));

    // Scale
    transform = AffineTransform.getScaleInstance(2, 2);
    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Scale"));
    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Scale"));

    // Rotation
    transform = AffineTransform.getRotateInstance(Math.PI/4, 100, 50);
    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Rotation"));
    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Rotation"));

    // Shear
    transform = AffineTransform.getShearInstance(.5f, 0f);
    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Shear"));
    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Shear"));

    frame.pack();
    frame.setVisible(true);

  }

  private static JComponent makeNewComponent(Dimension dim, Shape shape, Renderer renderer, AffineTransform transform, String toolTip){
    LayerComposition cmp = new LayerComposition(dim);
    Rectangle rect = new Rectangle(-1, -1, dim.width, dim.height);
    ShapeLayer layer = new ShapeLayer(cmp, shape, renderer);
    layer.setTransform(transform);
    ShapeLayer boundsLayer = new ShapeLayer(cmp, rect, new StrokeRenderer(Color.black, 1));
    cmp.setLayers(new Layer[]{layer, boundsLayer});
    cmp.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    CompositionComponent comp = new CompositionComponent(cmp);
    comp.setToolTipText(toolTip);
    return comp;
  }
}

