/*
 * @(#)StrokeRenderer.java
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

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;

/**
 * Strokes a <code>Shape</code>'s outline.
 *
 * @author       Vincent Hardy
 * @version      1.0, 10/09/1998
 * @see          com.sun.glf.Renderer
 */
public 
class StrokeRenderer implements Renderer {
  /**
   * Used to process the renderer's margins
   */
  private static final Rectangle rect = new Rectangle(0, 0, 1, 1);

  /**
   * Paint used for drawing the outline
   */
  private Paint strokePaint;

  /**
   * Margin around the rendered shapes
   */
  private Dimension2D margins;

  /**
   * Stroke used for the outline
   */
  private Stroke stroke;

  /**
   * @param strokePaint the paint to be used for drawing the outline
   * @param strokeWidth width of the BasicStroke which will be used.
   *
   * @exception IllegalArgumentException if strokePaint is null
   */
  public StrokeRenderer(Paint strokePaint, float strokeWidth){
    this(strokePaint, new BasicStroke(strokeWidth));
  }
  /**
   * @param strokePaint the paint to be used for drawing the outline
   * @param stroke the stroke used for the outline. 
   * 
   * @exception IllegalArgumentException if strokePaint is null
   */
  public StrokeRenderer(Paint strokePaint, Stroke stroke){
    if(strokePaint == null || stroke == null)
      throw new IllegalArgumentException();
    
    this.strokePaint = strokePaint;
    this.stroke = stroke;

    // Margins are processed 'experimentally', using the stroke.
    Shape strokedRect = stroke.createStrokedShape(rect);
    Rectangle2D rect = strokedRect.getBounds2D();
    margins = new Dimension2D_Double(rect.getWidth() - this.rect.width, rect.getHeight() - this.rect.height);

    if(margins.getWidth()<0) margins.setSize(0, margins.getHeight());
    if(margins.getHeight()<0) margins.setSize(margins.getWidth(), 0);
  }

  /**
   * This renderer add some margin due to the stroke it uses
   */
  public Dimension2D getMargins(){
    return (Dimension2D)margins.clone();
  }

  /**
   * Renders the Shape by stroking its outline.
   */
  public void render(Graphics2D g, Shape shape){
    g.setStroke(stroke);
    g.setPaint(strokePaint);
    g.draw(shape);
  }

}

