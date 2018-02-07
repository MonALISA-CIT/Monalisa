/*
 * @(#)GridBagConstants.java
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

import java.awt.GridBagConstraints;

/**
 * Convenience interface, very much in the Swing spirit. Contains the
 * set of constant values for the GridBagLayout. This makes it
 * possible for a class to implement this interface and easily reuse
 * the constants without having to prefix the class name. Therefore,
 * this is just meant as a code writing convenience.
 * 
 * @author               Vincent Hardy
 * @version              1.0, 04/09/1998
 * @see                  com.sun.glf.util.GridBagPanel
 */
public
interface GridBagConstants{
   /**
     * Specify that this component is the 
     * last component in its column or row. 
     * @since   JDK1.0
     */
  public static final int REMAINDER = GridBagConstraints.REMAINDER;

   /**
     * Do not resize the component. 
     * @since   JDK1.0
     */
  public static final int NONE = GridBagConstraints.NONE;

   /**
     * Resize the component both horizontally and vertically. 
     * @since   JDK1.0
     */
  public static final int BOTH = GridBagConstraints.BOTH;

   /**
     * Resize the component horizontally but not vertically. 
     * @since   JDK1.0
     */
  public static final int HORIZONTAL = GridBagConstraints.HORIZONTAL;

   /**
     * Resize the component vertically but not horizontally. 
     * @since   JDK1.0
     */
  public static final int VERTICAL = GridBagConstraints.VERTICAL;

   /**
    * Put the component in the center of its display area.
    * @since    JDK1.0
    */
  public static final int CENTER = GridBagConstraints.CENTER;

   /**
     * Put the component at the top of its display area,
     * centered horizontally. 
     * @since   JDK1.0
     */
  public static final int NORTH = GridBagConstraints.NORTH;

    /**
     * Put the component at the top-right corner of its display area. 
     * @since   JDK1.0
     */
  public static final int NORTHEAST = GridBagConstraints.NORTHEAST;

    /**
     * Put the component on the left side of its display area, 
     * centered vertically.
     * @since    JDK1.0
     */
  public static final int EAST = GridBagConstraints.EAST;

    /**
     * Put the component at the bottom-right corner of its display area. 
     * @since   JDK1.0
     */
  public static final int SOUTHEAST = GridBagConstraints.SOUTHEAST;

    /**
     * Put the component at the bottom of its display area, centered 
     * horizontally. 
     * @since   JDK1.0
     */
  public static final int SOUTH = GridBagConstraints.SOUTH;

   /**
     * Put the component at the bottom-left corner of its display area. 
     * @since   JDK1.0
     */
  public static final int SOUTHWEST = GridBagConstraints.SOUTHWEST;

    /**
     * Put the component on the left side of its display area, 
     * centered vertically.
     * @since    JDK1.0
     */
  public static final int WEST = GridBagConstraints.WEST;

   /**
     * Put the component at the top-left corner of its display area. 
     * @since   JDK1.0
     */
  public static final int NORTHWEST = GridBagConstraints.NORTHWEST;
}
