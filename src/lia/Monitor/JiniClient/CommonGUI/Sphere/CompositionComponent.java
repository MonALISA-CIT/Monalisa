/*
 * @(#)CompositionComponent.java
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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

/**
 * A component which displays the drawing made by a Composition
 * object.
 *
 * @author    Vincent Hardy
 * @version   1.0, 10/08/98
 * @see       com.sun.glf.Composition
 */

public
class CompositionComponent extends JComponent {
  /**
   * Offscreen buffer used for double buffering.
   */
  private BufferedImage offscreen;

  /**
   * Controls whether or not the Composition contained
   * in this component should be scaled to fit the component's
   * size or not. By default, the composition is not scaled
   * @see #setDoScale
   */
  private boolean doScale = false;

  /**
   * rendering hint to use when doScale is true. Can
   * be null. If not null, should be one of : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
   * RenderingHints.VALUE_INTERPOLATION_BILINEAR, RenderingHints.VALUE_INTERPOLATION_BICUBIC.
   *
   * @see #doScale
   */
  private Object scaleHint;

  /**
   * Preferred size, based on the size of the contained component.
   * @see #getPreferredSize
   */
  private Dimension dim;

  /**
   * Composition object to which the painting is
   * delegated.
   */
  private Composition composition;

  /**
   * @return copy of the offscreen buffer used by the component
   */
  public BufferedImage getOffscreen(){
    BufferedImage copy = new BufferedImage(offscreen.getWidth(), 
					   offscreen.getHeight(),
					   BufferedImage.TYPE_INT_RGB);
    Graphics2D g = copy.createGraphics();
    g.drawImage(offscreen, 0, 0, null);
    g.dispose();
    return copy;
  }

  /**
   * Constructor
   * 
   * @param composition the composition this composent should paint.
   * @param doScale controls whether the composition should be scaled
   *        to fit the component's size.
   * @param scaleHint rendering hint to use when doScale is true. Can
   *        be null. If not null, should be one of the values for
   *        java.awt.RenderingHints.KEY_INTERPOLATION, i.e.
   *        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR or
   *        RenderingHints.VALUE_INTERPOLATION_BILINEAR or
   *        RenderingHints.VALUE_INTERPOLATION_BICUBIC
   *
   * @exception IllegalArgumentException thrown if composition is null
   */
  public CompositionComponent(Composition composition, boolean doScale,
			      Object scaleHint){
    setComposition(composition);
    this.doScale = doScale;
    this.scaleHint = scaleHint;
    if(scaleHint!=null){
      if(!(scaleHint==RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
	 &&
	 !(scaleHint==RenderingHints.VALUE_INTERPOLATION_BILINEAR)
	 &&
	 !(scaleHint==RenderingHints.VALUE_INTERPOLATION_BICUBIC))
	throw new IllegalArgumentException("Invalid scaleHint");
    }
  }

  /**
   * Constructor
   * 
   * @param composition the composition this composent should paint.
   * @exception IllegalArgumentException thrown if composition is null
   */
  public CompositionComponent(Composition composition){
    setComposition(composition);
  }

  /**
   * Sets the Composition object this component should display.
   * This will cause the component to repaint itself. Note that,
   * depending on the type of composition, this may be a lengthy 
   * operation.
   * 
   * @param composition composition this component should display
   * @exception IllegalArgumentException if composition is null
   */
  public synchronized void setComposition(Composition composition){
    if(composition==null)
      throw new IllegalArgumentException();

    this.dim = composition.getSize();
    this.composition = composition;
    this.offscreen = null;
    setPreferredSize(dim);
    repaint();
  }

  /**
   * @see java.awt.Component#update
   */
  public void update(Graphics g){
    paint(g);
  }

  /**
   * Paints this component. 
   * An offscreen buffer is created on the first paint
   * request and the composition is requested only once to
   * paint into the buffer.
   *
   * @see java.awt.Component#paint
   */
  public void paint(Graphics _g){
    Graphics2D g = (Graphics2D)_g;

    if(offscreen == null)
      prepareOffscreen();

    // At this point, a double buffer exists with the Composition drawn on
    // it. If no scaling has been requested, simply paint the offscreen buffer.
    // Otherwise, process the scale to fit into the window and set the graphics
    // transform before painting the image.
    Dimension size = getSize();
    Dimension paintDim = (Dimension)dim.clone();
    if(doScale){
      // Scale drawing to fit in display area.
      float scaleX = size.width/(float)dim.width;
      float scaleY = size.height/(float)dim.height;
      float scale = (float)Math.min(scaleX, scaleY);
      paintDim.width = (int)(paintDim.width*scale);
      paintDim.height = (int)(paintDim.height*scale);
    }

    if(scaleHint!=null)
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scaleHint);

    // Center is display area
    int x = (size.width - paintDim.width)/2;
    int y = (size.height - paintDim.height)/2;
    g.drawImage(offscreen, x, y, paintDim.width, paintDim.height, null);

  }

  /**
   * Can be used to request the component to prepare its offscreen buffer
   * @see #paint
   */
  public void prepareOffscreen(){
    if(offscreen == null){
      // Offscreen buffer is not ready yet.
      // A new offscreen buffer is created. It will be used in subsequent calls.
      offscreen = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D offg = offscreen.createGraphics();
      composition.paint(offg);
      composition = null; // To free up any resource the Composition may hold
    }
  }

  /**
   * Unit testing. Using a simple Composition implementation, tests the different 
   * usage of the class.
   */
  public static void main(String args[]){
    // 
    // Simple Composition implementation
    //
    Composition square = new Composition(){
      Color paint = new Color(174, 174, 232);

      public Dimension getSize(){
	return new Dimension(40, 40);
      }

      public void addPaintProgressListener(PaintProgressListener l){}

      public void removePaintProgressListener(PaintProgressListener l){}

      public void paint(Graphics2D g){
	g.setPaint(paint);
	g.fillRect(0, 0, 40, 40);
	g.setPaint(Color.black);
	g.drawLine(0, 0, 40, 40);
	g.drawLine(0, 40, 40, 0);
      }
    };

    //
    // Create CompositionComponents, one for each possible
    // constructors.
    //
    CompositionComponent noScale = new CompositionComponent(square);
    noScale.setToolTipText("Square Shape, no scale");

    CompositionComponent scaleNN = new CompositionComponent(square, true, 
							    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    scaleNN.setToolTipText("Square Shape, scale, Nearest Neighbor interpolation");
    
    CompositionComponent scaleBL = new CompositionComponent(square, true, 
							    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    scaleBL.setToolTipText("Square Shape, scale, Bilinear interpolation");

    CompositionComponent scaleBC = new CompositionComponent(square, true, 
							    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    scaleBC.setToolTipText("Square Shape, scale, Bicubic interpolation");

    CompositionComponent scaleNull = new CompositionComponent(square, true, null);
    scaleNull.setToolTipText("Square Shape, scale, null interpolation (unspecified)");
    //
    // Put all the test components in a frame and display
    //
    final Frame frame = new Frame("CompositionComponent Unit testing");
    frame.setLayout(new GridLayout(0, 1));
    frame.add(noScale);
    frame.add(scaleNN);
    frame.add(scaleBL);
    frame.add(scaleBC);
    frame.add(scaleNull);
    
    frame.addWindowListener(new WindowAdapter(){
      public void windowClosing(WindowEvent evt){
	frame.setVisible(false);
	frame.dispose();
	System.exit(0);
      }
    });

    frame.pack();
    frame.show();
    
  }
}

