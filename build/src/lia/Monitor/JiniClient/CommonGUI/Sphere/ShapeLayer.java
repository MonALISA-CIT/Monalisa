/*
 * @(#)ShapeLayer.java
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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Dimension2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import javax.swing.JFrame;

/**
 * Renders a <code>Shape</code> with a <code>{@ling com.sun.glf.Renderer 
 * Renderer}</code>.<br>
 * This class provides convenience methods to place a <code>Shape</code>
 * relative to the <code>Composition</code>'s bounding rectangle through
 * it's <code>Position</code> based constructor. <br>
 *
 * @author    Vincent Hardy
 * @version   1.0, 10/09/1998
 * @see       com.sun.glf.Layer
 * @see       com.sun.glf.Position
 */
public class ShapeLayer extends Layer {
  /**
   * Used to render the Shape
   */
  protected Renderer renderer;

  /**
   * The Shape to be filled
   */
  protected Shape shape;

  /**
   * Constructor
   * 
   * @param parent the parent composition
   * @param renderer used to render the shape. 
   * @param shape the shape to be rendered
   * @exception IllegalArgumentException if shape or renderer is null
   */
  public ShapeLayer(LayerComposition parent, Shape shape, Renderer renderer){
    this(parent, shape, renderer, null);
  }

  /**
   * Instead of specifying the transform which should be applied before drawing 
   * the input shape, this constructor takes the anchor defining where in the 
   * composition the shape should appear.
   * 
   * @param parent the parent composition
   * @param renderer used to render the shape. 
   * @param shape the shape to be rendered
   * @param position defines the shape's relative position inside the Composition
   * @exception IllegalArgumentException if shape or renderer is null
   */
  public ShapeLayer(LayerComposition parent, Shape shape, Renderer renderer, Position position){
    super(parent);

    if(shape==null||renderer==null)
      throw new IllegalArgumentException();

    this.renderer = renderer;
    this.shape = shape;
    if(position!=null)
      setTransform(position.getTransform(shape, parent.getBounds()));
  }
  
  /**
   * Returns a copy of the Shape drawn by this Layer. 
   *
   * @return the Shape used by this layer.
   */
  public Shape getShape(){
    return new GeneralPath(shape);
  }

  /**
   * Returns this Layer's Shape, after transformation by this layer's transform
   */
  public Shape createTransformedShape(){
    if(transform==null)
      return new GeneralPath(shape);
    else
      return transform.createTransformedShape(shape);
  }

  /**
   * Parent's abstract method implementation
   *
   * @see Layer#paint
   */
  public void paint(Graphics2D g){
    // Delegate rendering to renderer
    renderer.render(g, shape);
  }

  /**
   * Requests the bounding box, in user space, of the area rendered
   * by this Layer
   *
   * @see com.sun.glf.LayerComposition#paint
   */
  public Rectangle2D getBounds(){
    Rectangle2D bounds = shape.getBounds2D();
    Dimension2D margins = renderer.getMargins();
    bounds.setRect(bounds.getX() - margins.getWidth(),
		   bounds.getY() - margins.getHeight(),
		   bounds.getWidth() + 2*margins.getWidth(),
		   bounds.getHeight() + 2*margins.getHeight());
    return bounds;
  }

  /*
   * Unit testing
   */
  public static void main(String args[]){
    // Get Shape from a Glyph object
    Glyph glyph = new Glyph(new Font("serif", Font.BOLD, 80), '@');
    Shape shape = glyph.getShape(); 

    // Center Shape
    Rectangle bounds = shape.getBounds();
    Dimension dim = new Dimension(bounds.width + 10, bounds.height + 10);
    Rectangle cmpRect = new Rectangle(0, 0, dim.width, dim.height);
    shape = Position.CENTER.createTransformedShape(shape, cmpRect);

    // Use high quality rendering
    final RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
						 RenderingHints.VALUE_ANTIALIAS_ON);

    //
    // Create a frame and display the different layer compositions
    //
    final JFrame frame = new JFrame("ShapeLayer unit testing");
    frame.getContentPane().setBackground(Color.white);
    frame.getContentPane().setLayout(new GridLayout(0, 1));

    //
    // Create different compositions with ShapeLayers
    //
    CompositionComponent comp = null;

    // Fill Only
    LayerComposition cmp = new LayerComposition(dim);
    cmp.setRenderingHints(rh);
    Renderer fillRenderer = new FillRenderer(new Color(128, 0, 0));
    ShapeLayer layer = new ShapeLayer(cmp, shape, fillRenderer);
    cmp.setLayers( new Layer[] {layer} );
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("ShapeLayer with FillRenderer");
    frame.getContentPane().add(comp);

    // Stroke Only
    cmp = new LayerComposition(dim);
    cmp.setRenderingHints(rh);
    Renderer strokeRenderer = new StrokeRenderer(Color.black, 4);
    layer = new ShapeLayer(cmp, shape, strokeRenderer);
    cmp.setLayers( new Layer[] {layer} );
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("ShapeLayer with StrokeRenderer (BasicStroke)");
    frame.getContentPane().add(comp);

    // Stroke and Fill
    cmp = new LayerComposition(dim);
    cmp.setRenderingHints(rh);
    Renderer compositeRenderer = new CompositeRenderer( new Renderer[] { fillRenderer, strokeRenderer } );
    layer = new ShapeLayer(cmp, shape, compositeRenderer);
    cmp.setLayers( new Layer[] {layer} );
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("ShapeLayer with CompositeRenderer (fill and stroke)");
    frame.getContentPane().add(comp);

    frame.pack();
    frame.addWindowListener(new WindowAdapter(){
      public void windowClosing(WindowEvent evt){
	frame.setVisible(false);
	frame.dispose();
	System.exit(0);
      }
    });

    frame.setVisible(true);
  }

}

