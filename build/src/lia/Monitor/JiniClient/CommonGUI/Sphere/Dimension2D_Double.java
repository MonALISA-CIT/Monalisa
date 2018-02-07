/*
 * @(#)Dimension2D_Double.java
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

import java.awt.geom.Dimension2D;

/**
 * A Double precision implementation of the Dimension2D interface.
 *
 * @author        Vincent Hardy
 * @version       1.0, 03/31/1999
 */
public class Dimension2D_Double extends Dimension2D{
  double width, height;
  public Dimension2D_Double() {
  }

  public Dimension2D_Double(double width, double height) {
    this.width = width;
    this.height = height;
  }

  /**
   * Returns the width of this <code>Dimension</code> in double 
   * precision.
   * @return the width of this <code>Dimension</code>.
   */
  public double getWidth(){
    return width;
  }

  /**
   * Returns the height of this <code>Dimension</code> in double 
   * precision.
   * @return the height of this <code>Dimension</code>.
   */
  public double getHeight(){
    return height;
  }

  /**
   * Sets the size of this <code>Dimension</code> object to the 
   * specified width and height.
   * This method is included for completeness, to parallel the
   * {@link java.awt.Component#getSize getSize} method of 
   * {@link java.awt.Component}.
   * @param width  the new width for the <code>Dimension</code>
   * object
   * @param height  the new height for the <code>Dimension</code> 
   * object
   */
  public void setSize(double width, double height){
    this.width = width;
    this.height = height;
  }
}
