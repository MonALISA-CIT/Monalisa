/*
 * @(#)Glyph.java
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

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.Serializable;

/**
 * Encapsulate a Shape created from a character, using a 
 * Font object. This is commonly referred to as a glyph, which
 * explains the class name.
 * <p>
 * A Glyph object is non mutable.
 * 
 * @author                  Vincent Hardy
 * @version                 1.0, 11/02/1998
 */
public class Glyph implements Serializable{
  /**
   * Used by all classes for Shape creation
   */
  private static FontRenderContext frc = new FontRenderContext(null, true, true);

  /**
   * Associated font, i.e. the font used to create the Glyph's Shape
   */
  private Font font;

  /**
   * Character whose visual representation is provided by the Glyph's Shape
   */
  private char c;

  /**
   * Default constructor
   */
  public Glyph(Font font, char c){
    if(font==null)
      throw new IllegalArgumentException();
    
    this.font = font;
    this.c = c;
  }

  /**
   * @return this Glyph's shape
   */
  public Shape getShape(){
    GlyphVector v = font.createGlyphVector(frc, new char[]{c});
    return v.getOutline();
  }

  /**
   * @return this Glyph's font
   */
  public Font getFont(){ return font; }

  /**
   * @return this Glyph's character 
   */
  public char getChar(){ return c; }
}
