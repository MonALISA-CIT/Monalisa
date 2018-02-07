/*
 * @(#)FillRenderer.java
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
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Dimension2D;

/**
 * This renderer fills a <code>Shape</code> with a <code>Paint</code>. It's <code>
 * paint</code> method is very simple: <p><pre><code>
 * public void paint(Graphics2D g, Shape shape){
 *    g.setPaint(filling);
 *    g.fill(shape);
 * }</pre></code>
 *
 * @author       Vincent Hardy
 * @version      1.0, 02/17/1998
 * @see          jsc.layers.Renderer
 */
public class FillRenderer implements Renderer {
  /**
   * @see #render
   */
  private Paint filling;

  /**
   * margins are none, because the renderer only
   * fills in the shape.
   */
  private Dimension margins = new Dimension(0,0);

  /**
   * @param filling the paint to be used for the filling operation
   * @exception IllegalArgumentException if filling is null
   */
  public FillRenderer(Paint filling){
    if(filling == null)
      throw new IllegalArgumentException();
    
    this.filling = filling;
  }

  /**
   * This renderer adds no margins
   */
  public Dimension2D getMargins(){
    return (Dimension)margins.clone();
  }

  /**
   * Paints the shape by filling it with its filling
   * 
   * @see #filling
   */
  public void render(Graphics2D g, Shape shape){
    g.setPaint(filling);
    g.fill(shape);
  }

}

