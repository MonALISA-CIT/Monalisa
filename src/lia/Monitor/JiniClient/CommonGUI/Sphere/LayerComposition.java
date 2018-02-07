/*
 * @(#)LayerComposition.java
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
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.color.ColorSpace;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ByteLookupTable;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.Kernel;
import java.awt.image.LookupOp;
import java.awt.image.LookupTable;
import java.awt.image.RasterOp;
import java.awt.image.WritableRaster;
import java.util.Vector;

import javax.swing.JFrame;

/**
 * Allows the composition of <code>Layers</code> into one single Composition.
 * Each layer is responsible for producing one of the different 'steps'
 * to the final output. A <code>LayerComposition</code> piles layers, one on top 
 * of the other.
 * <p>
 * Memo rendering attributes (<code>Composite</code>, clip, mask, transform, and 
 * <code>RenderingHints</code> can be set in <code>Layer</code> objects. 
 * Those memos are used by the <code>LayerComposition</code> to prepare the 
 * graphic context (i.e. the Graphics2D rendering attributes) before invoking
 * the <code>Layer</code>'s <code>paint</code> method.
 * <p>
 * <code>Layers</code> are painted in the order they appear in the constructor's 
 * <code>layers</code> array, i.e. from index 0 to layers.length-1.
 * <br>
 * It is possible to set <code>RenderingHints</code> for the whole 
 * <code>LayerComposition</code>. This is meant for situations where the same 
 * rendering quality is desired for all the layers in the <code>Composition</code>. 
 * That way, the hints can be set once instead of once for each <code>Layer</code>.
 * <br>
 * Layers need temporary offscreen buffers during their paint operations. For large images,
 * the process of allocating and garbage collecting big offscreen buffers becomes a real
 * concern. To address this issue, the <code>LayerComposition</code> provides support for the
 * creation of temporary BufferedImage it 'leases' to its <code>Layers</code>. See
 * the {@link #leaseBuffer leaseBuffer} and the {@link #releaseBuffer releaseBuffer}
 * members.
 * <br>
 * The following code segment illustrates how to build a <code>LayerComposition</code> and
 * its stack of <code>Layers</code>: <p><code><pre>
 * 
 * Dimension dim = new Dimension(200, 200);
 * LayerComposition cmp = new LayerComposition(cmpSize);
 * cmp.setBackgroundPaint(Color.white);
 * TextLayer textLayer = new TextLayer(cmp, "Hello Layers", 
 *                                     new Font("serif", Font.PLAIN, 80),
 *                                     new FillRenderer(Color.black));
 * cmp.setLayers(new Layer[] { textLayer });
 * </pre></code>
 * 
 * @author    Vincent Hardy
 * @version   1.0, 01/30/98
 * @see       jsc.awt.layers.Layer
 */
public class LayerComposition implements Composition{
  /**
   * Layers responsible for the different drawing steps.
   * There are as many drawing steps as there are layers.
   * Layers are drawn in the order they appear in the array
   * The same layer may appear several times in this array.
   */
  Layer layers[];

  /**
   * Overall <code>RenderingHints</code>. Note that individual <code>Layers</code>
   * may override the <code>RenderingHints</code>.
   */
  private RenderingHints renderingHints;

  /**
   * Composition's size
   */
  private Dimension dim;

  /**
   * Constant transform
   */
  private AffineTransform IDENTITY = new AffineTransform();

  /**
   * Background paint (optional)
   */
  private Paint backgroundPaint;

  /**
   * Vector of leased BufferedImages
   * @see #leaseBuffer
   * @see #releaseBuffer
   */
  private Vector leasedBuffers = new Vector();

  /**
   * List of objects intersted in paint progress
   */
  private Vector paintProgressListeners = new Vector();

  /**
   * Constructor
   *
   * @param dim this composition's size
   * @exception IllegalArgumentException if input arguments are
   *            null.
   */
  public LayerComposition(Dimension dim){
    if(dim==null)
      throw new IllegalArgumentException();

    this.dim = dim;
  }

  /**
   * For use by derived classes only
   */
  protected LayerComposition(){
  }

  /**
   * @return this Composition's bounds, in device space
   */
  public Rectangle getBounds(){
    return new Rectangle(0, 0, dim.width, dim.height);
  }

  /**
   * @return the backgroundPaint this composition uses. May be null.
   */
  public Paint getBackgroundPaint(){
    return backgroundPaint;
  }

  /**
   * @param listener object interested in paint progress updates
   */
  public void addPaintProgressListener(PaintProgressListener l){
    paintProgressListeners.addElement(l);
  }

  /**
   * @param listener object no longer intersted in paint progress updates
   */
  public void removePaintProgressListener(PaintProgressListener l){
    paintProgressListeners.removeElement(l);
  }

  /**
   * If defined, the background paint will be used to fill the entire
   * composition before painting the layers
   */
  public void setBackgroundPaint(Paint backgroundPaint){
    this.backgroundPaint = backgroundPaint;
  }

  /**
   * @return number of layers in the composition
   */
  public int getLayerCount(){
    return layers!=null?layers.length:0;
  }

  /**
   * @return layer at given index
   */
  public Layer getLayer(int index){
    return layers[index];
  }

  /**
   * @param renderingHints hints which apply to all the layers in the composition.
   *        Individual layer hints do override this global setting.
   */
  public void setRenderingHints(RenderingHints renderingHints){
    this.renderingHints = renderingHints;
  }

  /**
   * @return copy of the rendering hints used by this composition
   */
  public RenderingHints getRenderingHints(){
    RenderingHints rh = null;
    if(renderingHints!=null)
      rh = (RenderingHints)renderingHints.clone();
    return rh;
  }

  /**
   * Adds a rendering hint to this Composition
   *
   * @param key rendering hint key
   * @param value rendering hint value
   * @see java.awt.RenderingHints
   */
  public void setRenderingHint(RenderingHints.Key key, Object value){
    if(renderingHints==null)
      renderingHints = new RenderingHints(key, value);
    else
      renderingHints.put(key, value);
  }

  /**
   * Set the composition's layers
   * @param layers the new set of layers for this composition
   * @exception IllegalArgumentException if layers or one of its elements
   *            is null
   */
  public void setLayers(Layer layers[]){
    if(layers==null)
      throw new IllegalArgumentException();
    for(int  i=0; i<layers.length; i++)
      if(layers[i]==null)
        throw new IllegalArgumentException("Layer at index " + i + " is null");

    this.layers = layers;
  }

  /**
   * @return this composition's dimension
   */
  public Dimension getSize(){
    return dim;
  }

  /**
   * Sets this composition's size
   */
  public void setSize(Dimension dim){
    if(dim==null)
      throw new IllegalArgumentException();
    this.dim=(Dimension)dim.clone();;
  }

  /**
   * Each layer is, in turn, asked to paint,
   * using the layer's associated attributes:
   * composite, clip, mask, transform and rendering hints.
   */
  public void paint(Graphics2D g){
    // Notify listeners that paint is starting.
    notifyPaintStart();

    //
    // Initialize empty set of buffers for lease
    // by the children layers. Buffers will get created
    // as requested.
    //
    leasedBuffers = new Vector();

    try{
      //
      // Get default rendering attributes
      //
      RenderingHints defaultHints = g.getRenderingHints(); 
      if(renderingHints!=null)
	defaultHints.putAll(renderingHints);

      Composite defaultComposite = g.getComposite();
      Shape defaultClip = g.getClip();
      AffineTransform defaultTransform = g.getTransform();

      //
      // First, fill background with background color if
      // one has been defined
      //
      if(backgroundPaint != null){
	g.setPaint(backgroundPaint);
	g.fillRect(0, 0, dim.width, dim.height);
      }

      //
      // Now, render each layer in turn, using
      // layer specific attributes where needed.
      //
      int nLayers = layers.length;
      Composite composite = null;
      RenderingHints hints = null;
      Shape clip = null;
      AffineTransform transform = null;

      BufferedImage offScreen = null; // Used for rendering layers which have a mask or filter
      Graphics2D offg = null; // working graphics

      for(int i=0; i<nLayers; i++){
	Layer layer = layers[i];

	// Notify listeners that paint is starting.
	notifyLayerPaintStart(layer);

	// Reset transform and clip
	g.setTransform(defaultTransform);
	g.setClip(defaultClip);

	// Get layer memento attributes
	composite = layer.getComposite();
	hints = layer.getRenderingHints();
	clip = layer.getClip();
	transform = layer.getTransform();

	if(composite==null) composite = defaultComposite;
	if(clip==null) clip = defaultClip;
	if(hints==null) hints = defaultHints;
	if(transform==null) transform = IDENTITY;

	//
	// If there is no layer mask or filter, simply paint the layer.
	// Otherwise, we need to first paint the layer offscreen and then
	// apply filter and mask.
	//
	BufferedImage mask = layer.getLayerMask();
	Layer.LayerFilter filter = layer.getFilter();
	if(mask==null && filter==null){
	  g.clip(clip);             
	  g.transform(transform);
	  g.setRenderingHints(hints);
	  g.setComposite(composite);
	  layer.paint(g);
	}
	else{ // Mask and/or filter on
	  // Process offscreen buffer size and position.
	  // The 'active' area is defined by the intersection of the 
	  // composition area, the layer mask area and the rendered area.
	  // Keep processing only if the intersection is not empty
	  Rectangle cmpRect = getBounds();                     // Device space
	  Rectangle maskRect = layer.getLayerMaskRect();       // Device space
	  Rectangle2D renderRect2D = layer.getBounds();        // User space
	  
	  Rectangle renderRect = transform.createTransformedShape(renderRect2D).getBounds(); // Now in device space

	  renderRect = renderRect.intersection(cmpRect);
	  if(maskRect!=null)
	    renderRect = renderRect.intersection(maskRect);

	  if(renderRect.width>0 && renderRect.height>0){
	    // Adjust size and position to accomodate for filter margin
	    if(filter!=null){
	      Dimension filterMargins = filter.getFilterMargins();
	      renderRect.x -= filterMargins.width;
	      renderRect.y -= filterMargins.height;
	      renderRect.width += filterMargins.width*2;
	      renderRect.height += filterMargins.height*2;
	    }

	    // Create offscreen buffer
	    if(offScreen==null                                 // No offscreen buffer created
	       || offScreen.getWidth()<renderRect.width        // Offscreen not wide enough
	       || offScreen.getHeight()<renderRect.height      // Offscreen not high enough
	       || !isOffscreenCompatible(offScreen, layer)){   // Offscreen not compatible with layer
	      offScreen = createLayerCompatibleImage(layer, renderRect.width, renderRect.height);
	    }

	    BufferedImage working = offScreen.getSubimage(0, 0, renderRect.width, renderRect.height);
	    offg = working.createGraphics();

	    // First, clear working in case it has already been used
	    offg.setComposite(AlphaComposite.Clear);
	    offg.fillRect(0, 0, renderRect.width, renderRect.height);
	    offg.setComposite(AlphaComposite.SrcOver);

	    // Set offscreen transform to prepare offscreen rendering
	    offg.translate(-renderRect.x, -renderRect.y);
	    
	    // Draw Layer offscreen
	    offg.transform(transform);

	    offg.setRenderingHints(hints);
	    layer.paint(offg);

	    // Use filter if one is defined
	    if(filter!=null){
	      if(filter.isRasterFilter()){
		RasterOp op = filter.getRasterFilter();
		WritableRaster workingRaster = working.getRaster();
		workingRaster = workingRaster.createWritableTranslatedChild(renderRect.x, renderRect.y);
		WritableRaster filteredRaster = workingRaster.createCompatibleWritableRaster(working.getWidth(), working.getHeight());
		op.filter(workingRaster, filteredRaster);
		working = new BufferedImage(working.getColorModel(),
					     filteredRaster, working.isAlphaPremultiplied(), null);
	      }
	      else{
		BufferedImageOp op = filter.getImageFilter();
		working = op.filter(working, null);
	      }
	      
	      offg = working.createGraphics();
	    }
	    else
	      offg.setTransform(IDENTITY);

	    // Set translation so that coordinate spaces match
	    offg.translate(-renderRect.x, -renderRect.y);
	    
	    // Now, draw image in working, masking out what is not part of the mask
	    if(mask!=null){
	      offg.setComposite(AlphaComposite.DstIn);
	      offg.drawImage(mask, maskRect.x, maskRect.y, maskRect.width, maskRect.height, null);

	      // offg.setPaint(Color.white);
	      // offg.drawRect(maskRect.x, maskRect.y, maskRect.width, maskRect.height);

	      // Trim part of filtered outside the mask. This may happen because we might have
	      // extended the renderRect for filter margins.
	      Rectangle trimedRect = renderRect.intersection(maskRect);
	      working = working.getSubimage((int)Math.max(0, (maskRect.x - renderRect.x)),
					    (int)Math.max(0, (maskRect.y - renderRect.y)),
					    trimedRect.width, trimedRect.height);
	      renderRect = trimedRect;
	    }

	    // Finally, paint working on graphics
	    g.clip(clip);
	    g.setComposite(composite);
	    g.drawImage(working, renderRect.x, renderRect.y, null);
	  }
	  else
	    System.out.println("Layer has not active area");
	}
      }
    }finally{
      // Make sure the temporary buffers are candidates for garbage collection
      // in any event.
      leasedBuffers = null;
      
      // Notify listeners that paint is finished
      notifyPaintFinished();
    }
  }

  /**
   * @return true if offScreen can be used for input layer
   * @see #paint
   */
  private boolean isOffscreenCompatible(BufferedImage offScreen, Layer layer){
    boolean compatible = true;
    if(!(layer instanceof ImageLayer))
      compatible = (offScreen.getType() == BufferedImage.TYPE_INT_ARGB);
    else{
      Image srcImage = ((ImageLayer)layer).getImage();
      if(!(srcImage instanceof BufferedImage))
	compatible = (offScreen.getType() == BufferedImage.TYPE_INT_ARGB);
      else{
	BufferedImage srcBufImage = (BufferedImage)srcImage;
	compatible = srcBufImage.getColorModel().equals(offScreen.getColorModel());
      }
    }

    return compatible;
  }

  /**
   * Creates a BufferedImage compatible with the input Layer. This returns an 
   * ARGB image for all Layers, except ImageLayer. For ImageLayer, it the Image is
   * a BufferedImage, then a same type image is created. Otherwise, an ARGB is created
   * as well.
   * 
   * @see #paint
   */
  private BufferedImage createLayerCompatibleImage(Layer layer, int width, int height){
    ColorModel cm = new DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
					 32,
					 0x00ff0000,	// Red
					 0x0000ff00,	// Green
					 0x000000ff,	// Blue
					 0xff000000,	// Alpha
					 true,
					 DataBuffer.TYPE_INT
					 );

    BufferedImage image = null;
    if(!(layer instanceof ImageLayer))
      image = new BufferedImage(cm, cm.createCompatibleWritableRaster(width, height), true, null);
      // image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    else{
      Image srcImage = ((ImageLayer)layer).getImage();
      if(!(srcImage instanceof BufferedImage))
	image = new BufferedImage(cm, cm.createCompatibleWritableRaster(width, height), true, null);
        // image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      else{
	BufferedImage srcBufImage = (BufferedImage)srcImage;
	image = new BufferedImage(srcBufImage.getColorModel(),
				  srcBufImage.getRaster().createCompatibleWritableRaster(width, height),
				  srcBufImage.isAlphaPremultiplied(),
				  null);
      }
    }
    
    return image;
  }

  /**
   * Notifies listeners that paint is about to start
   */
  private void notifyPaintStart(){
    int n = paintProgressListeners.size();
    for(int i=0; i<n; i++){
      PaintProgressListener l = (PaintProgressListener)paintProgressListeners.elementAt(i);
      l.paintStarted(this, layers.length);
    }
  }

  /**
   * Notifies listeners that a new layer is about to be painted
   */
  private void notifyLayerPaintStart(Layer layer){
    String message = layer.getClass().getName();
    int n = paintProgressListeners.size();
    for(int i=0; i<n; i++){
      PaintProgressListener l = (PaintProgressListener)paintProgressListeners.elementAt(i);
      l.paintStepStarted(this, message);
    }
  }

  /**
   * Notifies listeners that paint is completed
   */
  private void notifyPaintFinished(){
    int n = paintProgressListeners.size();
    for(int i=0; i<n; i++){
      PaintProgressListener l = (PaintProgressListener)paintProgressListeners.elementAt(i);
      l.paintFinished(this);
    }
  }

  

  /**
   * Returns a temporary buffer for lease by the children layers. Note that, during a paint
   * operation, this tries to reuse buffers in the leasedBuffers vector. Outside a  paint operation,
   * this simply creates a BufferedImage.
   * 
   * @param w requested image width
   * @param h requested image height
   * @param type requested image type (e.g. BufferedImage.TYPE_INT_RGB).
   * @see java.awt.image.BufferedImage
   * @see #releaseBuffer
   */
  public BufferedImage leaseBuffer(int w, int h, int type){
    //
    // Check if there is an available buffer in the leasedBuffers
    //
    BufferedImage buffer = null;
    if(leasedBuffers!=null){
      // A fitting buffer has at lease the requested size and
      // the same image type
      int nBuffers = leasedBuffers.size();
      for(int i=0; i<nBuffers; i++){
	BufferedImage buf = (BufferedImage)leasedBuffers.elementAt(i);
	if(buf.getType()==type && buf.getWidth()>=w && buf.getHeight()>h){
	  buffer = buf.getSubimage(0, 0, w, h);
	  leasedBuffers.remove(buf);
	  break;
	}
      }

      if(buffer==null)
	buffer = new BufferedImage(w, h, type);
    }
    else
      buffer = new BufferedImage(w, h, type);

    return buffer;
  }

  /**
   * 'Composition friendly' Layers should release the temporary buffers they use in case
   * they might be reused by other layers.
   * During a paint operation, the composition stores release buffers into its leasedBuffers
   * vector so that it is reusable by other layers.
   *
   * @param buffer buffer a layer makes available to other layers.
   */
  public void releaseBuffer(BufferedImage buffer){
    if(leasedBuffers!=null){
      leasedBuffers.addElement(buffer);
      if(leasedBuffers.size()>2) 
	// Keep no more than two buffers for lease.
	// This is done in case layers use very different buffer types,
	// to avoid accumulating buffers that are not reused.
	leasedBuffers.remove(0);
    }
  }

  /**
   * Unit testing. Tests layer compositions.
   */
  public static void main(String args[]){
    //
    // Create a single layer implementation. Used for unit testing
    //
    class SimpleLayer extends Layer{
      Color paint = new Color(174, 174, 232);

      public SimpleLayer(LayerComposition parent){
	super(parent);
      }

      public void paint(Graphics2D g){
	int w = parent.getSize().width;
	int h = parent.getSize().height;
	g.setPaint(paint);
	g.fillRect(0, 0, w, h);
	g.setPaint(Color.black);
	g.drawLine(0, 0, w, h);
	g.drawLine(0, h, w, 0);
      }

      public Rectangle2D getBounds(){
	return new Rectangle(0, 0, parent.getSize().width, parent.getSize().height);
      }
    }

    //
    // Create frame to display compositions
    //
    final JFrame frame = new JFrame("LayerComposition unit testing");
    frame.getContentPane().setBackground(Color.white);
    frame.getContentPane().setLayout(new GridLayout(0, 2));
    CompositionComponent comp = null;

    //
    // Create layer composition with different attributes
    //
    Dimension dim = new Dimension(120, 120);

    // Plain
    LayerComposition cmp = new LayerComposition(dim);
    cmp.setLayers(new Layer[]{ new SimpleLayer(cmp) });
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, no attributes");
    frame.getContentPane().add(comp);

    // With rendering hints
    final RenderingHints antialias = new RenderingHints(RenderingHints.KEY_ANTIALIASING, 
							RenderingHints.VALUE_ANTIALIAS_ON);
    cmp = new LayerComposition(dim);
    cmp.setLayers(new Layer[]{ new SimpleLayer(cmp){ {this.setRenderingHints(antialias);} }});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, Antialias on hint.");
    frame.getContentPane().add(comp);

    // With composite
    final Composite alpha = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f);
    cmp = new LayerComposition(dim);
    cmp.setLayers(new Layer[]{ new SimpleLayer(cmp){ {setComposite(alpha);}}});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, Composite (AlphaComposite.5 SrcOver).");
    frame.getContentPane().add(comp);

    // With clip
    final Shape clip = new Ellipse2D.Double(0, 0, dim.width, dim.height);
    cmp = new LayerComposition(dim);
    cmp.setLayers(new Layer[]{ new SimpleLayer(cmp){ { setClip(clip);} } });
    cmp.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, clip set to Ellipse Shape. Composition hint set.");
    frame.getContentPane().add(comp);

    // With transform
    cmp = new LayerComposition(dim);
    Layer layer =  new SimpleLayer(cmp);
    layer.setTransform(AffineTransform.getRotateInstance(Math.PI/4, dim.width/2, dim.height/2));
    cmp.setLayers(new Layer[]{layer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, transform set to rotation");
    frame.getContentPane().add(comp);

    byte lut[] = new byte[256];
    for(int i=0; i<lut.length; i++)
      lut[i] = (byte)(255-i);
    LookupTable t = new ByteLookupTable(0, lut);
    BufferedImageOp filter = new LookupOp(t, null);

    // With filter
    Rectangle rect = new Rectangle(-10000, -10000, dim.width/2, dim.height/3);
    Kernel k = new GaussianKernel(10);
    ConvolveOp blur = new ConvolveOp(k);
    cmp = new LayerComposition(dim);
    layer = new SimpleLayer(cmp);
    layer.setImageFilter(blur, new Dimension(20, 20));
    cmp.setLayers(new Layer[]{layer});
    cmp.setBackgroundPaint(Color.black);
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. Filter set to blur, with filter margins");
    frame.getContentPane().add(comp);

    // Again with filter
    cmp = new LayerComposition(dim);
    layer = new ShapeLayer(cmp, rect, new FillRenderer(Color.orange), new Position(Anchor.RIGHT, 20, 20));
    layer.setRasterFilter(blur, new Dimension(20, 20));
    cmp.setLayers(new Layer[]{layer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. Filter set to blur, using ShapeLayer");
    frame.getContentPane().add(comp);

    // For comparison
    cmp = new LayerComposition(dim);
    layer = new ShapeLayer(cmp, rect, new FillRenderer(Color.orange), new Position(Anchor.RIGHT, 20, 20));
    cmp.setLayers(new Layer[]{layer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. For comparison with blurred version");
    frame.getContentPane().add(comp);

    // With filter and mask
    cmp = new LayerComposition(dim);
    rect = new Rectangle(-10000, -10000, dim.width/2, dim.height/3);
    layer = new ShapeLayer(cmp, rect, new FillRenderer(Color.black), new Position(Anchor.RIGHT, 20, 20));
    rect = ((ShapeLayer)layer).createTransformedShape().getBounds();
    layer.setLayerMask(new Ellipse2D.Float(rect.x, rect.y, rect.width, rect.height));
    layer.setRasterFilter(blur, new Dimension(20, 20));
    cmp.setLayers(new Layer[]{layer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. Filter set to blur, using ShapeLayer");
    frame.getContentPane().add(comp);

    // With filter and clip
    cmp = new LayerComposition(dim);
    rect = new Rectangle(-10000, -10000, dim.width/2, dim.height/3);
    layer = new ShapeLayer(cmp, rect, new FillRenderer(Color.black), new Position(Anchor.RIGHT, 20, 20));
    rect = ((ShapeLayer)layer).createTransformedShape().getBounds();
    layer.setClip(new Ellipse2D.Float(rect.x, rect.y, rect.width, rect.height));
    layer.setRasterFilter(blur, new Dimension(20, 20));
    cmp.setLayers(new Layer[]{layer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. Filter set to blur, using ShapeLayer. Clip set");
    frame.getContentPane().add(comp);

    // With mask and filter
    final BufferedImage mask = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D gm = mask.createGraphics();
    gm.setPaint(Color.white);
    gm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    gm.fill(new Ellipse2D.Float(0, 0, dim.width, dim.height));
    cmp = new LayerComposition(dim);
    Layer maskedLayer = new SimpleLayer(cmp);
    maskedLayer.setLayerMask(mask);
    maskedLayer.setImageFilter(filter);
    cmp.setLayers(new Layer[]{maskedLayer});
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. One single layer, mask set to Ellipse2D Shape");
    frame.getContentPane().add(comp);

    // Multiple layers
    cmp = new LayerComposition(dim);
    cmp.setLayers(new Layer[] { 
      new SimpleLayer(cmp) { { setComposite(alpha); } },
      new SimpleLayer(cmp){ {setClip(clip); }},
    });    
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("LayerComposition. Multiple layers, a transparent one in backround and a clipped one in foreground");
    frame.getContentPane().add(comp);
    
    frame.addWindowListener(new WindowAdapter(){
      public void windowClosing(WindowEvent evt){
	frame.setVisible(false);
	frame.dispose();
	System.exit(0);
      }
    });

    frame.pack();
    frame.setVisible(true);
    
  }
}
