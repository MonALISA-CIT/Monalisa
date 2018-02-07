/*
 * @(#)Position.java
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

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * The Position class encapsulates the information needed to place a graphic
 * object relative to a bounding rectangle. Positions are used by <code>ShapeLayer</code> 
 * and <code>ImageLayer</code> to let the programmer define where a <code>Shape</code> or image should 
 * be drawn relative to the parent <code>LayerComposition</code>'s bounding rectangle.
 * <p>
 * The following attributes define a Position:
 * + Its Anchor: Top-Left, Top, Bottom, Center, etc...<br>
 * + Its vertical and horizontal adjustment values. Those have meanind which 
 *   depend on the Anchor value (see below for more on this).<br>
 * + A transform relative to the center of the graphic object to render.<br>
 * <p>
 * The Anchor defines how an object's bounding rectangle and the bounding rectangle where it is 
 * positioned match:
 * + Anchor.TOP_LEFT: the upper left corners match<br>
 * + Anchor.TOP: the top edge centers matches<br>
 * + Anchor.TOP_RIGHT: the upper right corners match<br>
 * + Anchor.RIGHT: the right edge centers matche<br>
 * + Anchor.BOTTOM_RIGHT: the bottom right corners match.<br>
 * + Anchor.BOTTOM: the bottom edge middle match.<br>
 * + Anchor.BOTTOM_LEFT: the bottom left corners match<br>
 * + Anchor.LEFT: the left edge center match.<br>
 * + Anchor.CENTER: the center match.<br>
 * <p>
 * The horizontal and vertical adjustment parameters define variations abound the anchor.
 * The semantic of these adjustments varies depending on the Anchor:<br>
 * + Anchor.TOP_LEFT: left and top margins<br>
 * + Anchor.TOP: Horizontal adjustment ignored.top margin <br>
 * + Anchor.TOP_RIGHT: right and top margins.
 * + Anchor.RIGHT: Right margin. Vertical adjustment ignored<br>
 * + Anchor.BOTTOM_RIGHT: right and bottom margins<br>
 * + Anchor.BOTTOM: horizontal adjustment ignored. Bottom margin<br>
 * + Anchor.BOTTOM_LEFT: left and bottom margins<br>
 * + Anchor.LEFT: left margin. Vertical adjustment ignored<br>
 * + Anchor.CENTER: horizontal and vertical adjustments ignored<br>
 * <p> 
 * The transform encapsulated in a Position object is relative to the graphic object's center. 
 * <p>
 * Users of the Position class can get the AffineTransformation that will correctly
 * position a graphic object inside a bounding rectangle by calling the 
 * {@link #getTransform #getTransform} member.
 *
 * @author          Vincent Hardy
 * @version         1.0, 03/10/1999
 * @see             com.sun.glf.Anchor
 * @see             com.sun.glf.ShapeLayer
 * @see             com.sun.glf.ImageLayer
 */
public class Position{
  /**
   * Predefined Positions.
   */
  public static final Position TOP_LEFT = new Position(Anchor.TOP_LEFT);
  public static final Position TOP = new Position(Anchor.TOP);
  public static final Position TOP_RIGHT = new Position(Anchor.TOP_RIGHT);
  public static final Position RIGHT = new Position(Anchor.RIGHT);
  public static final Position BOTTOM_RIGHT = new Position(Anchor.BOTTOM_RIGHT);
  public static final Position BOTTOM = new Position(Anchor.BOTTOM);
  public static final Position BOTTOM_LEFT = new Position(Anchor.BOTTOM_LEFT);
  public static final Position LEFT = new Position(Anchor.LEFT);
  public static final Position CENTER = new Position(Anchor.CENTER);

  /**
   * Position relative to bounding rectangle
   */
  private Anchor anchor = Anchor.CENTER;

  /**
   * Adjustment about the Anchor. Semantic depends on Anchor value. 
   */
  private float hAdjust, vAdjust;

  /**
   * Transform about the graphic object's center.
   */
  private AffineTransform transform = new AffineTransform();

  /**
   * Controls in which order Shapes should be transformed/positioned
   */
  private boolean placeBeforeTransform = true;

  /**
   * Default constructor. Default parameter vales position the graphic object at the 
   * center of the bounding rectangle.
   */
  public Position(){
    this(Anchor.CENTER);
  }

  /**
   * @param anchor Defines relative placement in bounding rectangle. 
   */
  public Position(Anchor anchor){
    this(anchor, 0, 0);
  }

  /**
   * @param anchor defines relative placement in bounding rectangle. 
   * @param hAdjust horizontal position adjustment.
   * @param vAdjust vertical position adjustment
   */
  public Position(Anchor anchor, float hAdjust, float vAdjust){
    this(anchor, hAdjust, vAdjust, null);
  }

  /**
   * @param anchor defines relative placement in bounding rectangle. 
   * @param hAdjust horizontal position adjustment.
   * @param vAdjust vertical position adjustment
   * @param transform AffineTransform relative to the positioned object's center.
   */
  public Position(Anchor anchor, float hAdjust, float vAdjust, AffineTransform transform){
    this(anchor, hAdjust, vAdjust, transform, true);
  }

  /**
   * @param anchor defines relative placement in bounding rectangle. 
   * @param hAdjust horizontal position adjustment.
   * @param vAdjust vertical position adjustment
   * @param transform AffineTransform relative to the positioned object's center.
   * @param placeThenTransform if true, then Shapes are positioned before they
   *        are placed. Otherwise, Shapes are placed after they are transformed. This
   *        only makes a difference when the Shape bounds is affected by the transform.
   */
  public Position(Anchor anchor, 
		  float hAdjust, float vAdjust, 
		  AffineTransform transform,
		  boolean placeThenTransform){
    if(anchor==null)
      anchor = Anchor.CENTER;
      
    this.anchor = anchor;
    this.hAdjust = hAdjust;
    this.vAdjust = vAdjust;
    if(transform!=null)
      this.transform = (AffineTransform)transform.clone();
    this.placeBeforeTransform = placeThenTransform;
  }

  /**
   * @param shape object that should be positioned in rectangle
   * @param rect rectangle where graphic object should be placed.
   * @return transformed shape that has the specified position in user space.
   */
  public Shape createTransformedShape(Shape shape, Rectangle2D rect){
    AffineTransform t = getTransform(shape, rect);
    return t.createTransformedShape(shape);
  }

  /**
   * @param shape shape that is positioned in rect
   * @param rect rectangle where graphic object should be placed.
   */
  public AffineTransform getTransform(Shape shape, Rectangle2D rect){
    AffineTransform t = null;
    Rectangle2D bounds = shape.getBounds2D();

    if(placeBeforeTransform){
      t = getAnchorPlacementTransform(bounds, rect);
      t.translate(bounds.getX() + bounds.getWidth()/2.0, bounds.getY() + bounds.getHeight()/2.0);
      t.concatenate(transform);
      t.translate(-bounds.getX() -bounds.getWidth()/2.0, -bounds.getY() -bounds.getHeight()/2.0);
    }
    else{
      t = new AffineTransform();
      t.translate(bounds.getX() + bounds.getWidth()/2.0, bounds.getY() + bounds.getHeight()/2.0);
      t.concatenate(transform);
      t.translate(-bounds.getX() -bounds.getWidth()/2.0, -bounds.getY() -bounds.getHeight()/2.0);

      // Get new Shape bounds
      bounds = t.createTransformedShape(shape).getBounds();
      AffineTransform tmp = getAnchorPlacementTransform(bounds, rect);
      t.preConcatenate(tmp);
    }
    return t;
  }

  private AffineTransform getAnchorPlacementTransform(Rectangle2D bounds, Rectangle2D rect){
    double w = rect.getWidth();
    double h = rect.getHeight();
    
    AffineTransform t = new AffineTransform();
    
    // Move the the appropriate Anchor position, taking adjustment
    // values into account.
    switch(anchor.toInt()){
    case Anchor.ANCHOR_TOP_LEFT:
      t.setToTranslation(hAdjust, vAdjust);
      break;
    case Anchor.ANCHOR_TOP:
      t.setToTranslation(-bounds.getWidth()/2.0+w/2.0, vAdjust);
      break;
    case Anchor.ANCHOR_TOP_RIGHT:
      t.setToTranslation(w-bounds.getWidth()-hAdjust, vAdjust);
      break;
    case Anchor.ANCHOR_RIGHT:
      t.setToTranslation(w-bounds.getWidth()-hAdjust, -bounds.getHeight()/2.0 + h/2.0);
      break;
    case Anchor.ANCHOR_BOTTOM_RIGHT:
      t.setToTranslation(w-bounds.getWidth()-hAdjust, h - bounds.getHeight() - vAdjust);
      break;
    case Anchor.ANCHOR_BOTTOM:
      t.setToTranslation(-bounds.getWidth()/2.0+w/2.0, h - bounds.getHeight() - vAdjust);
      break;
    case Anchor.ANCHOR_BOTTOM_LEFT:
      t.setToTranslation(hAdjust, h - bounds.getHeight() - vAdjust);
      break;
    case Anchor.ANCHOR_LEFT:
      t.setToTranslation(hAdjust, -bounds.getHeight()/2.0 + h/2.0);
      break;
    case Anchor.ANCHOR_CENTER:
      t.setToTranslation(-bounds.getWidth()/2.0+w/2.0, -bounds.getHeight()/2.0 + h/2.0);
      break;
    default:
      throw new IllegalArgumentException("Invalid anchor value: " + anchor);
    }

    // Move to the upper left corner of the bounding rect
    t.translate(rect.getX() - bounds.getX(), rect.getY() - bounds.getY());

    return t;
  }
}
