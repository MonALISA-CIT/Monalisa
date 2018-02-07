/*
 * @(#)Layer.java
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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;

/**
 * Layer is the interface expected by a <code>{@link com.sun.glf.LayerComposition 
 * LayerComposition}</code> to perform atomic operations of a complex drawing.
 * <p>
 * A <code>Layer</code> has a set of important "memento" attributes which control
 * how it is rendered in its composition:<br>
 * 1. A Composition rule. This typically controls the 
 *    layer transparency.<br>
 * 2. A clipping shape. This controls which part of the layer
 *    is drawn on the composition. <br>
 * 3. A mask. This is equivalent to an advanced clipping, and
 *    allows sophisticated composition of layers. <br>
 * 4. A rendering hint. This controls the quality which should
 *    be used when rendering the layer. <br>
 * 5. A transform.. This controls the mapping between the user space in which the
 *    <code>Layer</code> drawing happen, and the device space where the final
 *    ouput is rendered.
 * 6. A filter. This defines a filtering operation applied to the Layer. An
 *    additional filterMargin can be defined, which expands the size of the offscreen
 *    buffer used for the filtering operation (e.g. can be used for convolutions).
 * <p>
 * All of these attributes are optional, meaning that they
 * can all be null. Note that these attributes are 'mementos'
 * and are not meant to be used by the <code>Layer</code> implementations. 
 *
 * @author    Vincent Hardy
 * @version   1.0, 10/8/98
 * @see       com.sun.glf.LayerComposition
 */
public
abstract class Layer {
  /**
   * A LayerFilter can be either a BufferedImageOp or a RasterOp.
   * @see com.sun.glf.LayerComposition
   */
  static public class LayerFilter{
    private BufferedImageOp bFilter;
    private RasterOp rFilter;
    private Dimension filterMargins;
    
    /**
     * @param filter BufferedImageOp used to filter this Layer
     */
    public LayerFilter(BufferedImageOp filter){
      this.bFilter = filter;
      this.filterMargins = new Dimension(0, 0);
    }
    
    /**
     * @param filter RasterOp used to filter this Layer
     */
    public LayerFilter(RasterOp filter){
      this.rFilter = filter;
      this.filterMargins = new Dimension(0, 0);
    }

    /**
     * @param filter BufferedImageOp used to filter this Layer
     * @param filterMargins extra margins used to expance the offscreen buffer
     *        used in the filtering process.
     */
    public LayerFilter(BufferedImageOp filter, Dimension filterMargins){
      this.bFilter = filter;
      this.filterMargins = (Dimension)filterMargins.clone();
    }
    
    /**
     * @param filter RasterOp used to filter this Layer
     * @param filterMargins extra margins used to expance the offscreen buffer
     *        used in the filtering process.
     */
    public LayerFilter(RasterOp filter, Dimension filterMargins){
      this.rFilter = filter;
      this.filterMargins = (Dimension)filterMargins.clone();
    }
    
    /**
     * @return true if this a Raster filter
     */
    public boolean isRasterFilter(){
      return (rFilter != null);
    }

    public Dimension getFilterMargins(){
      return (Dimension)filterMargins.clone();
    }

    public BufferedImageOp getImageFilter(){
      return bFilter;
    }

    public RasterOp getRasterFilter(){
      return rFilter;
    }
  }

  /**
   * ColorModel used for masks. The Layer masks are input as gray scale images.
   * The mask data is used to build another BufferedImage that uses an IndexColorModel.
   * The IndexColorModel uses a lookup table that associates a fully transparent alpha
   * to index 0 and a fully opaque alpha to index 255. In the gray scale input image, 
   * black has a value of zero and white a value of 255. Therefore, this techniques provides
   * a way to convert the gray scale mask into an RGB image with alpha corresponding to the
   * mask intensities.
   */
  private static ColorModel maskColorModel;

  static {
    byte cmap[] = new byte[256];
    for(int i=0; i<cmap.length; i++) cmap[i] = (byte)i;
    maskColorModel = new IndexColorModel(8, 256, cmap, cmap, cmap, cmap);    
  }

  /**
   * Parent composition
   */
  protected LayerComposition parent;

  /**
   * Composition rule to be used when this layer is 
   * painted on top of another one.
   */
  protected Composite composite;

  /**
   * Clipping area
   */
  protected Shape clippingArea;

  /**
   * Layer Mask. The layer should paint only
   * where the mask is opaque
   * @see com.sun.glf.LayerComposition#paint
   */
  protected BufferedImage layerMask;

  /**
   * Bounding rectangle, in device space
   */
  protected Rectangle layerMaskRect;

  /**
   * RenderingHints. Controls the quality to use
   * when rendering this layer.
   */
  protected RenderingHints hints;

  /**
   * The transform to be applied before
   * rendering the layer.
   * @see LayerComposition#paint
   */
  protected AffineTransform transform;

  /**
   * LayerFilter applied to Layer
   *
   * @see #paint
   */
  protected LayerFilter filter;

  /**
   * @return the filter associated with this layer
   */
  public LayerFilter getFilter(){
    return filter;
  }

  /**
   * @param filter BufferedImageOp used to filter this Layer
   */
  public void setImageFilter(BufferedImageOp filter){
    setImageFilter(filter, new Dimension(0, 0));
  }

  /**
   * @param filter RasterOp used to filter this Layer
   */
  public void setRasterFilter(RasterOp filter){
    setRasterFilter(filter, new Dimension(0, 0));
  }

  /**
   * @param filter BufferedImageOp used to filter this Layer
   * @param filterMargins extra margins used to expance the offscreen buffer
   *        used in the filtering process.
   */
  public void setImageFilter(BufferedImageOp filter, Dimension filterMargins){
    this.filter = new LayerFilter(filter, (Dimension)filterMargins.clone());
  }

  /**
   * @param filter RasterOp used to filter this Layer
   * @param filterMargins extra margins used to expance the offscreen buffer
   *        used in the filtering process.
   */
  public void setRasterFilter(RasterOp filter, Dimension filterMargins){
    this.filter = new LayerFilter(filter, (Dimension)filterMargins.clone());
  }

  /**
   * Note that this does clone the transform.
   *
   * @param transform transform to apply before rendering this layer.
   */
  public void setTransform(AffineTransform transform){
    if(transform!=null)
      this.transform = (AffineTransform)transform.clone();
    else
      this.transform = null;
  }

  /**
   * @return transform applied before rendering this layer
   */
  public AffineTransform getTransform(){
    return transform;
  }

  /**
   * Sets the composition to be used when painting 
   * this layer.
   * 
   * @see LayerComposition#paint
   */
  public void setComposite(Composite composite){
    this.composite = composite;
  }

  /**
   * Returns the composition rule to be used when painting
   * this layer.
   *
   * @see LayerComposition#paint
   */
  public Composite getComposite(){
    return this.composite;
  }

  /**
   * Sets the clipping area to be used when rendering this layer
   *
   * @see #clippingArea
   */
  public void setClip(Shape clippingArea){
    this.clippingArea = clippingArea;
  }

  /**
   * Gets the clipping area for this layer
   *
   * @see #clippingArea
   */
  public Shape getClip(){
    return clippingArea;
  }

  /**
   * Convenience method. Creates a mask from a Shape.
   * @param shape shape the mask will be made of the shape's inside.
   */
  public void setLayerMask(Shape shape){
    setLayerMask(shape, false);
  }

  /**
   * Convenience method. Creates a mask from a Shape. This can be useful
   * as a way to have antialiasing performed along the path of a clip.
   *
   * @param shape form used as a base for the mask. Device coordinates.
   * @param invert if true, then the mask is what lies outside the shape.
   *               if false, the mask is made of the shape inside.
   */
  public void setLayerMask(Shape shape, boolean invert){
    BufferedImage mask = null;
    Rectangle bounds = null;
    if(!invert){
      bounds = shape.getBounds();
      mask = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g = mask.createGraphics();
      g.setPaint(Color.white);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.translate(-bounds.x, -bounds.y);
      g.setComposite(AlphaComposite.SrcOver);
      g.fill(shape);
      g.dispose();
    }
    else{
      bounds = parent.getBounds();
      mask = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_GRAY);
      Graphics2D g = mask.createGraphics();
      g.setPaint(Color.white);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fillRect(0, 0, bounds.width, bounds.height);
      g.setPaint(Color.black);
      g.fill(shape);
      g.dispose();
    }
      

    setLayerMask(mask, bounds);

  }

  /**
   * Sets the mask for this layer. The input BufferedImage should be a gray scale 
   * image (i.e. of type BufferedImage.TYPE_BYTE_GRAY) or have the Layer.maskColorModel
   * ColorModel. White pixels are considered fully opaque and black pixels are considered
   * fully transparent. Gray values have an opacity linearly proportional to their 
   * brightness.
   *
   * @see #layerMask
   */
  public void setLayerMask(BufferedImage layerMask){
    setLayerMask(layerMask, new Rectangle(0, 0, layerMask.getWidth(), layerMask.getHeight()));
  }

  /**
   * Sets the mask for this layer. The input BufferedImage should be a gray scale 
   * image (i.e. of type BufferedImage.TYPE_BYTE_GRAY) or have the Layer.maskColorModel
   * ColorModel. White pixels are considered fully opaque and black pixels are considered
   * fully transparent. Gray values have an opacity linearly proportional to their 
   * brightness.
   */
  public void setLayerMask(BufferedImage layerMask, Rectangle layerMaskRect){
    if(layerMaskRect == null)
      throw new IllegalArgumentException();

    if(layerMask.getColorModel()!=maskColorModel){
      if(layerMask.getType()!=BufferedImage.TYPE_BYTE_GRAY){
	BufferedImage tmpMask = new BufferedImage(layerMask.getWidth(),
						  layerMask.getHeight(),
						  BufferedImage.TYPE_BYTE_GRAY);
	ColorConvertOp toGray = new ColorConvertOp(null);
	layerMask = toGray.filter(layerMask, tmpMask);
      }
	
      // At this point, we know that layerMask is TYPE_BYTE_GRAY. Use the gray 
      // scale image to create a corresponding ARGB image, with the alpha channel
      // corresponding to the gray intensities. 
      WritableRaster raster = layerMask.getRaster();
      layerMask = new BufferedImage(maskColorModel, raster, false, null);
    }

    this.layerMask = layerMask;
    this.layerMaskRect = layerMaskRect;
  }

  /**
   * Gets the mask for this layer
   *
   * @see #layerMask
   */
  public BufferedImage getLayerMask(){
    return layerMask;
  }

  /**
   * @return rectangle, in device space, where the mask applies
   */
  public Rectangle getLayerMaskRect(){
    return layerMaskRect;
  }

  /**
   * Sets the rendering hints for this layer
   *
   * @see #hints
   */
  public void setRenderingHints(RenderingHints hints){
    this.hints = hints;
  }

  /**
   * Adds a rendering hint to this layer
   *
   * @param key rendering hint key
   * @param value rendering hint value
   * @see java.awt.RenderingHints
   */
  public void setRenderingHint(RenderingHints.Key key, Object value){
    if(hints==null)
      hints = new RenderingHints(key, value);
    else
      hints.put(key, value);
  }

  /**
   * Gets the rendering hints for this layer
   *
   * @see #hints
   */
  public RenderingHints getRenderingHints(){
    return hints;
  }
    
  /**
   * Requests this <code>Layer</code> to paint into the 
   * graphics.<br>
   * <code>Layer</code> implementations should not modify the 
   * composite, clip and rendering hints attributes.
   * 
   * @see com.sun.glf.LayerComposition#paint
   */
  public abstract void paint(Graphics2D g);

  /**
   * Requests the bounding box, in user space, of the area rendered
   * by this Layer.
   *
   * @see com.sun.glf.LayerComposition#paint
   */
  public abstract Rectangle2D getBounds();

  /**
   * Constructor. 
   * @param parent the composition this layer belongs to. Not null.
   */
  protected Layer(LayerComposition parent){
    if(parent == null)
      throw new IllegalArgumentException("null parent composition");

    this.parent = parent;
  }

}


