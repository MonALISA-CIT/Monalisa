/*
 * @(#)CompositeRenderer.java
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

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Dimension2D;

/**
 * Renders by sequentially rendering it with each
 * of its component renderers.
 * This is useful to fill a <code>Shape</code> and stroke it: 
 * <p><blockcode><pre>
 *      FillRenderer fill = new FillRenderer(Color.yellow);
 *      StrokeRenderer outline = new StrokeRenderer(Color.red);
 *      CompositeRenderer renderer = new CompositeRenderer( new Renderer[]{fill, outline} );
 *      ShapeLayer layer = new ShapeLayer(composition,
 *                                        shapeToBeRendered,
 *                                        renderer);
 * <p></blockcode></pre>
 * With this code, <code>shapeToBeRendered</code> will be first filled,
 * then stroked. <p>
 * 
 * @author       Vincent Hardy
 * @version      1.0, 10/09/1998
 * @see          com.sun.glf.Renderer
 * @see          com.sun.glf.ShapeLayer
 */
public 
class CompositeRenderer implements Renderer {
  /**
   * Set of component renderers which are sequentially 
   * called for rendering.
   * @see #render
   */
  private Renderer components[];

  /**
   * Rendering margins
   * Processed from the rendering components margins
   * @see #CompositeRenderer
   */
  private Dimension2D margins;

  /**
   * @param first first renderer to apply
   * @param second second renderer to apply
   *
   * @exception IllegalArgumentException one of the arguments is null
   */
  public CompositeRenderer(Renderer first, Renderer second){
    this(new Renderer[]{first, second});
  }

  /**
   * @param components the list of renderers to be used sequentially
   *        in each drawing operation.
   *
   * @exception IllegalArgumentException if components or one of its
   *            elements is null
   */
  public CompositeRenderer(Renderer components[]){
    if(components==null)
      throw new IllegalArgumentException();

    synchronized(components){
      this.components = new Renderer[components.length];
      System.arraycopy(components, 0, this.components, 0, components.length);
    }

    for(int i=0; i<this.components.length; i++)
      if(this.components[i]==null)
        throw new IllegalArgumentException();

    margins = new Dimension2D_Double();
    for(int i=0; i<components.length; i++){
      margins.setSize(Math.max(margins.getWidth(), components[i].getMargins().getWidth()),
		      Math.max(margins.getHeight(), components[i].getMargins().getHeight()));
    }
                               
  }

  /**
   * @return this renderer's margins
   */
  public Dimension2D getMargins(){
    return (Dimension2D)margins.clone();
  }

  /**
   * Renders the shape by sequentially rendering it with each 
   * of this composite's component renderers. The component renderer
   * are invoked in the order they were passed to the composite at
   * construction time, i.e. for the first element to the last in the
   * input array.
   */
  public void render(Graphics2D g, Shape shape){
    for(int i=0; i<components.length; i++){
      components[i].render(g, shape);
    }
  }
}

