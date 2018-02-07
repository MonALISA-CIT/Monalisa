/*
 * @(#)RadialGradientPaint.java
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

package lia.Monitor.JiniClient.Store.utils;

import java.awt.Color;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

//import com.sun.glf.*;
//import com.sun.glf.util.*;
//import com.sun.glf.util.*;

/**
 * This class provides a way to fill a shape with a radial color gradient
 * pattern.
 * Given an Ellipse and 2 Colors, color1 and color2, the paint will render 
 * a color starting at color1 at the center of the Ellipse to color2 at 
 * its boundary. All pixels lying outside the Ellipse boundary have the
 * color2 value.
 *
 * @author Vincent Hardy
 * @version 1.0, 09.11.1998
 */
public class RadialGradientPaint implements Paint {
  /* The Ellipse controlling colors */
  Color color1, color2;

  /** The Ellipse bounds */
  Rectangle2D.Float gradientBounds;

  /** Transparency */
  int transparency;

  /**
   * @return center gradient color
   */
  public Color getCenterColor(){
    return color1;
  }

  /**
   * @return boundary color
   */
  public Color getBoundaryColor(){
    return color2;
  }

  /**
   * @return gradient bounds
   */
  public Rectangle2D getBounds(){
    return (Rectangle2D)gradientBounds.clone();
  }

  /**
   * @param bounds the bounds of the Ellipse defining the gradient. User space.
   * @param color1 Color at the ellipse focal points.
   * @param color2 Color at the ellipse boundary and beyond.
   */
  public RadialGradientPaint(Rectangle2D bounds, Color color1, Color color2) {
    this.color1 = color1;
    this.color2 = color2;
    this.gradientBounds = new Rectangle2D.Float();
    gradientBounds.setRect((float)bounds.getX(), (float)bounds.getY(), 
			   (float)bounds.getWidth(), (float)bounds.getHeight());

    int a1 = color1.getAlpha();
    int a2 = color2.getAlpha();
    transparency = (((a1 & a2) == 0xff) ? OPAQUE : TRANSLUCENT);
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
      return (new RadialGradientPaintContext(gradientBounds, color1, color2, transform));
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
//  public static void main(String args[]){
//    //
//    // Create a frame and display LayerCompositions containing
//    // ShapeLayers rendered with FillRenderer using RadialGradientPaint
//    //
//    JFrame frame = new JFrame("RadialGradientPaint unit testing");
//    frame.getContentPane().setLayout(new GridLayout(0, 2));
//    frame.getContentPane().setBackground(Color.white);
//    Shape rect = new Rectangle(20, 20, 160, 60);
//    Dimension dim = new Dimension(200, 100);
//
//    //
//    // First, create paints and renderers
//    //
//    Color testColor = new Color(20, 40, 20);
//    Paint ellipsePaint = new RadialGradientPaint(new Rectangle(40, 20, 70, 60), Color.white, testColor);
//    Paint discPaint = new RadialGradientPaint(new Rectangle(40, 20, 60, 60), Color.white, testColor);
//    Renderer ellipseFill = new FillRenderer(ellipsePaint);
//    Renderer discFill = new FillRenderer(discPaint);
//
//    //
//    // Create LayerCompositions using different transforms
//    //
//
//    // No transforms
//    AffineTransform transform = null;
//    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "No transform"));
//    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "No transform"));
//
//    // Translation
//    transform = AffineTransform.getTranslateInstance(40, 40);
//    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Translation"));
//    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Translation"));
//
//    // Scale
//    transform = AffineTransform.getScaleInstance(2, 2);
//    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Scale"));
//    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Scale"));
//
//    // Rotation
//    transform = AffineTransform.getRotateInstance(Math.PI/4, 100, 50);
//    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Rotation"));
//    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Rotation"));
//
//    // Shear
//    transform = AffineTransform.getShearInstance(.5f, 0f);
//    frame.getContentPane().add(makeNewComponent(dim, rect, ellipseFill, transform, "Shear"));
//    frame.getContentPane().add(makeNewComponent(dim, rect, discFill, transform, "Shear"));
//
//    frame.pack();
//    frame.setVisible(true);
//  }

//  private static JComponent makeNewComponent(Dimension dim, Shape shape, Renderer renderer, AffineTransform transform, String toolTip){
//    LayerComposition cmp = new LayerComposition(dim);
//    Rectangle rect = new Rectangle(-1, -1, dim.width, dim.height);
//    ShapeLayer layer = new ShapeLayer(cmp, shape, renderer);
//    layer.setTransform(transform);
//    ShapeLayer boundsLayer = new ShapeLayer(cmp, rect, new StrokeRenderer(Color.black, 1));
//    cmp.setLayers(new Layer[]{layer, boundsLayer});
//    CompositionComponent comp = new CompositionComponent(cmp);
//    comp.setToolTipText(toolTip);
//    return comp;
//  }
}
