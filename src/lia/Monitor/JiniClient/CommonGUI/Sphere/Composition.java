/*
 * @(#)Composition.java
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

/**
 * Interface expected from objects able to perform rendering
 * on a drawing area. For example, this interface is used
 * by a <code>{@link com.sun.glf.CompositionComponent 
 * CompositionComponent}</code> to perform its rendering.
 * <br>
 * Note that <code>Compositions</code> can be used in contexts other than 
 * on-screen rendering such as off-screen image creation, in which case the 
 * <code>Composition</code> is used by 'clients' other than <code>CompositionComponent.
 * </code><br>
 * An example of <code>Composition</code> is the <code>LayerComposition</code>
 * which piles up <code>Layers</code> to produce a drawing.
 * <br>
 * Because there are situations where it is important to monitor the progress 
 * of a paint operation (e.g. to provide feedback to a user), the interface 
 * provides a way for interested parties to declare their interest in updates 
 * about the paint process. However, the 'quality' of the information feedback 
 * is left to the <code>Composition</code> implementations (i.e. how often, if 
 * ever, a <code>PaintProgressListener</code> is called is left to the implementation 
 * to decide).
 * <br>
 * @author         Vincent Hardy
 * @author         1.0, 10/08/98
 * @see            com.sun.glf.LayerComposition
 * @see            com.sun.glf.util.CompositionComponent
 * @see            com.sun.glf.PaintProgressListener
 */
public interface Composition{
  /** 
   * @return this composition's dimension 
   */
  public Dimension getSize();

  /**
   * @param listener object interested in paint progress updates
   */
  public void addPaintProgressListener(PaintProgressListener l);

  /**
   * @param listener object no longer intersted in paint progress updates
   */
  public void removePaintProgressListener(PaintProgressListener l);

  /**
   * Requests the composition to perform painting using graphics
   *
   * @param g the graphics into which drawing should happen.
   */
  public void paint(Graphics2D g);
}

