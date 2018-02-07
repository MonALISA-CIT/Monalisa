/*
 * @(#)ImageLayer.java
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
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.net.MalformedURLException;

import javax.swing.JFrame;

/**
 * This implementation of the abstract <code>{@link com.sun.glf.Layer Layer}</code> 
 * class renders an <code>Image</code>. The optional <code>Position</code> construction
 * argument is used to define where the Image is painted in the composition. 
 * By default, the image is rendered in the center of the composition.
 *
 * @author         Vincent Hardy
 * @version        1.0, 10/08/1998
 * @see            com.sun.glf.Layer
 * @see            com.sun.glf.Position
 */
public class ImageLayer extends Layer{
  /**
   * rendered image
   */
  private Image image;

  /**
   * Requests the bounding box, in user space, of the area rendered
   * by this Layer
   *
   * @see com.sun.glf.LayerComposition#paint
   */
  public Rectangle2D getBounds(){
    return new Rectangle(0, 0, 
			 image.getWidth(null), 
			 image.getHeight(null));
  }

  /**
   * paint : just paints the image after applying transform.
   */
  public void paint(Graphics2D g){
    g.drawImage(image, 0, 0, null);
  }

  /**
   * @param parent the parent composition
   * @param image the image this layer should paint
   * 
   * @exception IllegalArgumentException if image is null 
   */
  public ImageLayer(LayerComposition parent, Image image){
    this(parent, image, null);
  }

  /**
   * @param parent the parent composition
   * @param image the image this layer should paint.
   * @param position relative position this image should take in 
   *        the composition. 
   */
  public ImageLayer(LayerComposition parent, Image image, Position position){
    super(parent);

    if(image==null)
      throw new IllegalArgumentException("Cannot paint null image");

    this.image = image;

    if(position!=null){
      Rectangle imageBounds = new Rectangle(image.getWidth(null), image.getHeight(null));
      setTransform(position.getTransform(imageBounds, parent.getBounds()));
    }
  }

  /**
   * @return image rendered by this layer
   */
  public Image getImage(){
    return image;
  }

  /*
   * Unit testing. Uses input image as the base for testing ImageLayer. The image is scaled to be
   * 10% the width of the input width parameter.
   */
  static final String USAGE = "java com.sun.glf.ImageLayer <imageFile or URL> <width> <height>";
  public static void main(String args[]) throws MalformedURLException{
    if(args.length<3){
      System.out.println(USAGE);
      System.exit(0);
    }

    //
    // Extract command line parameter
    //
    String imageName = args[0];
    int width = Integer.parseInt(args[1]);
    int height = Integer.parseInt(args[2]);

    // Load image and process composition size
    BufferedImage image = Toolbox.loadImage(imageName, BufferedImage.TYPE_INT_RGB);
    Dimension dim = new Dimension(width, height);

    // Rescale image so that it is 10% of composition width
    float scale = 0.10f*width/(float)image.getWidth();
    RenderingHints rh = new RenderingHints(RenderingHints.KEY_INTERPOLATION,
					   RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    AffineTransformOp scaleOp = new AffineTransformOp(AffineTransform.getScaleInstance(scale, scale), rh);
    image = scaleOp.filter(image, null);

    //
    // Create a frame and display the different layer compositions
    //
    final JFrame frame = new JFrame("ImageLayer unit testing");
    frame.getContentPane().setLayout(new GridLayout(0, 4));
    frame.getContentPane().setBackground(Color.white);
    CompositionComponent comp = null;
    Color backgroundPaint = Color.black; // new Color(145, 172, 165);

    //
    // First, create a LayerComposition with a single ImageLayer, using the 
    // simplest constructor
    //
    LayerComposition cmp = new LayerComposition(dim);
    ImageLayer layer = new ImageLayer(cmp, image);
    cmp.setLayers(new Layer[] { layer });
    cmp.setBackgroundPaint(backgroundPaint);
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("Simple ImageLayer");
    frame.getContentPane().add(comp);
    
    // 
    // Second, show the different Anchor usage
    //
    int nAnchors = Anchor.enumValues.length;
    Layer layers[] = new Layer[nAnchors];
    ConvolveOp blur = new ConvolveOp(new GaussianKernel(4));
    for(int i=0; i<nAnchors; i++){
      layers[i] = new ImageLayer(cmp, image, new Position(Anchor.enumValues[i]));
      layers[i].setImageFilter(blur, new Dimension(4, 4));
    }
    cmp = new LayerComposition(dim);
    cmp.setLayers(layers);
    cmp.setBackgroundPaint(backgroundPaint);
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("ImageLayers using different anchor values");
    frame.getContentPane().add(comp);

    //
    // Third, show the different Anchor usage, with adjustment values
    //
    float vAdjust = 30;
    float hAdjust = 50;
    layers = new ImageLayer[nAnchors];
    for(int i=0; i<nAnchors; i++){
      layers[i] = new ImageLayer(cmp, image, new Position(Anchor.enumValues[i], hAdjust, vAdjust));
    }
    cmp = new LayerComposition(dim);
    cmp.setLayers(layers);
    cmp.setBackgroundPaint(backgroundPaint);
    comp = new CompositionComponent(cmp);
    comp.setToolTipText("ImageLayers using different anchor values and adjustment");
    frame.getContentPane().add(comp);

    // 
    // Finally, show different Anchor usage, with adjustment values and transform
    //
    AffineTransform transforms[] = { AffineTransform.getTranslateInstance(20, 20),
				     AffineTransform.getScaleInstance(2, 2),
				     AffineTransform.getScaleInstance(1, -1),
				     AffineTransform.getRotateInstance(Math.PI/4),
				     AffineTransform.getShearInstance(-.2, -.2) };
    for(int i=0; i<transforms.length; i++){
      layers = new ImageLayer[nAnchors];
      for(int j=0; j<nAnchors; j++){
	layers[j] = new ImageLayer(cmp, image, 
				   new Position(Anchor.enumValues[j], hAdjust, vAdjust, transforms[i]));
      }
      cmp = new LayerComposition(dim);
      cmp.setLayers(layers);
      cmp.setBackgroundPaint(backgroundPaint);
      comp = new CompositionComponent(cmp);
      comp.setToolTipText("ImageLayers using different anchor values, adjustments and adjustment transform");
      frame.getContentPane().add(comp);
    }

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
