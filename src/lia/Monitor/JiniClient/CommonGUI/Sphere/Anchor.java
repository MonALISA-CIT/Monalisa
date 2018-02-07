/*
 * @(#)Anchor.java
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

import java.awt.geom.AffineTransform;
import java.beans.PropertyEditorManager;
import java.io.Serializable;

/**
 * Defines where an object can be anchored relative to another one: centered, left,
 * top-left, etc... <p>
 * <code>Anchors</code> can be used wherever relative placement is relevant. See 
 * <code>{@link com.sun.glf.ShapeLayer ShapeLayer}</code> for a sample usage. <p>
 * The class defines several final variables, and the API uses the ones
 * of type <code>Anchor</code> as API parameters. The <code>int</code> and <code>String
 * </code> types are used as convenience. Strings are used for the <code>toString</code>
 * implementation and the <code>toInt</code> member allows switching on an <code>Anchor
 * </code> value: <p> <blockcode><pre>
 * Anchor anchor = ....;
 * switch(anchor.toInt()){
 *   case Anchot.ANCHOR_TOP_LEFT:
 *   ....
 *   break;
 *   case Anchor.ANCHOR_TOP:
 *   ....
 * </pre></blockcode>
 *
 * @author        Vincent Hardy
 * @version       1.0, 10/17/1998
 * @see           com.sun.glf.ShapeLayer
 */
public final class Anchor implements Serializable{
  /*
   * Text block anchor types.
   */
  public static final int ANCHOR_TOP_LEFT = 0;
  public static final int ANCHOR_TOP = 1;
  public static final int ANCHOR_TOP_RIGHT = 2;
  public static final int ANCHOR_RIGHT = 3;
  public static final int ANCHOR_BOTTOM_RIGHT = 4;
  public static final int ANCHOR_BOTTOM = 5;
  public static final int ANCHOR_BOTTOM_LEFT = 6;
  public static final int ANCHOR_LEFT = 7;
  public static final int ANCHOR_CENTER = 8;

  /*
   * Strings used to get a more readable String when
   * an Anchor is displayed in a property editor.
   */
  public static final String TOP_LEFT_STR = "Top Left";
  public static final String TOP_STR = "Top";
  public static final String TOP_RIGHT_STR = "Top Right";
  public static final String RIGHT_STR = "Right";
  public static final String BOTTOM_RIGHT_STR = "Bottom Right";
  public static final String BOTTOM_STR = "Bottom";
  public static final String BOTTOM_LEFT_STR = "Bottom Left";
  public static final String LEFT_STR = "Left";
  public static final String CENTER_STR = "Center";

  /*
   * Anchor values
   */
  public static final Anchor TOP_LEFT = new Anchor(ANCHOR_TOP_LEFT, TOP_LEFT_STR);
  public static final Anchor TOP = new Anchor(ANCHOR_TOP, TOP_STR);
  public static final Anchor TOP_RIGHT = new Anchor(ANCHOR_TOP_RIGHT, TOP_RIGHT_STR);
  public static final Anchor RIGHT = new Anchor(ANCHOR_RIGHT, RIGHT_STR);
  public static final Anchor BOTTOM_RIGHT = new Anchor(ANCHOR_BOTTOM_RIGHT, BOTTOM_RIGHT_STR);
  public static final Anchor BOTTOM = new Anchor(ANCHOR_BOTTOM, BOTTOM_STR);
  public static final Anchor BOTTOM_LEFT = new Anchor(ANCHOR_BOTTOM_LEFT, BOTTOM_LEFT_STR);
  public static final Anchor LEFT = new Anchor(ANCHOR_LEFT, LEFT_STR);
  public static final Anchor CENTER = new Anchor(ANCHOR_CENTER, CENTER_STR);
  
  /**
   * All values
   */
  public static final Anchor[] enumValues = { TOP_LEFT,
					      TOP,
					      TOP_RIGHT,
					      RIGHT,
					      BOTTOM_RIGHT,
					      BOTTOM,
					      BOTTOM_LEFT,
					      LEFT,
					      CENTER };

  private String desc;
  private int val;

  /** 
   * Constructor is private so that no other instances than
   * the one in the enumeration can be created.
   * @see #readResolve
   */
  private Anchor(int val, String desc){
    this.desc = desc;
    this.val = val;
  }

  /**
   * @return description
   */
  public String toString(){
    return desc;
  }

  /**
   * Convenience for enumeration switching
   * @return value.
   */
  public int toInt(){
    return val;
  }

  /**
   * Convenience member. The position adjustment values have different meanings depending
   * on the Anchor value:<p>
   * + TOP_LEFT : hAdjust is the left margin, vAdjust is the top margin
   * + TOP : hAdjust is ignored, vAdjust is the top margin
   * + TOP_RIGHT : hAdjust is the right margin, vAdjust is the top margin
   * + RIGHT : hAdjust is the right margin, vAdjust is ignored
   * + BOTTOM_RIGHT : hAdjust is the right margin, vAdjust is the bottom margin
   * + BOTTOM : hAdjust is ignored, vAdjust is the bottom margin
   * + BOTTOM_LEFT : hAdjust is the left margin, vAdjust is the bottom margin
   * + LEFT : hAdjust is the left margin, vAdjust is ignored
   * + CENTER : hAdjust is ignored, vAdjust is ignored
   * A way to picture how the adjustment is done is to remember that, given positive values
   * for hAdjust and vAdjust, the resulting translation will move the position inward, i.e. towards
   * the center.
   */
  public AffineTransform getAdjustmentTranslation(float hAdjust, float vAdjust){
    AffineTransform t = new AffineTransform();

    switch(val){
    case Anchor.ANCHOR_TOP_LEFT:
      t.setToTranslation(hAdjust, vAdjust);
      break;
    case Anchor.ANCHOR_TOP:
      t.setToTranslation(0, vAdjust);
      break;
    case Anchor.ANCHOR_TOP_RIGHT:
      t.setToTranslation(-hAdjust, vAdjust);
      break;
    case Anchor.ANCHOR_RIGHT:
      t.setToTranslation(-hAdjust, 0);
      break;
    case Anchor.ANCHOR_BOTTOM_RIGHT:
      t.setToTranslation(-hAdjust, -vAdjust);
      break;
    case Anchor.ANCHOR_BOTTOM:
      t.setToTranslation(0, -vAdjust);
      break;
    case Anchor.ANCHOR_BOTTOM_LEFT:
      t.setToTranslation(hAdjust, -vAdjust);
      break;
    case Anchor.ANCHOR_LEFT:
      t.setToTranslation(hAdjust, 0);
      break;
    case Anchor.ANCHOR_CENTER:
      t.setToTranslation(0, 0);
      break;
    default:
      throw new IllegalArgumentException("Invalid anchor value: " + val);
    }

    return t;
  }

  /**
   * This is called by the serialization code before it returns an unserialized
   * object. To provide for unicity of instances, the instance that was read
   * is replaced by its static equivalent. See the serialiazation specification
   * for further details on this method's logic.
   */
  public Object readResolve() {
    switch(val){
      case ANCHOR_TOP_LEFT:
	return TOP_LEFT;
      case ANCHOR_TOP:
	return TOP;
      case ANCHOR_TOP_RIGHT:
	return TOP_RIGHT;
      case ANCHOR_RIGHT:
	return RIGHT;
      case ANCHOR_BOTTOM_RIGHT:
	return BOTTOM_RIGHT;
      case ANCHOR_BOTTOM:
	return BOTTOM;
      case ANCHOR_BOTTOM_LEFT:
	return BOTTOM_LEFT;
      case ANCHOR_LEFT:
	return LEFT;
      case ANCHOR_CENTER:
	return CENTER;
      default:
	throw new Error("Unknown Anchor value");
    }
  }

  /**
   * Property editor for the <code>Anchor</code> type. 
   * @see com.sun.glf.util.EnumPropertyEditorSupport
   */
  static public class AnchorPropertyEditor extends EnumPropertyEditorSupport{
    /**
   * This method is intended for use when generating Java code to set
   * the value of the property.  It returns a fragment of Java code
   * that can be used to initialize a variable with the current property
   * value.
   */
    public String getJavaInitializationString() {
      Anchor val = (Anchor)getValue();
      if(val==null)
	return "null";

      switch(val.toInt()){
      case ANCHOR_TOP_LEFT:
	return "Anchor.TOP_LEFT";
      case ANCHOR_TOP:
	return "Anchor.TOP";
      case ANCHOR_TOP_RIGHT:
	return "Anchor.TOP_RIGHT";
      case ANCHOR_RIGHT:
	return "Anchor.RIGHT";
      case ANCHOR_BOTTOM_RIGHT:
	return "Anchor.BOTTOM_RIGHT";
      case ANCHOR_BOTTOM:
	return "Anchor.BOTTOM";
      case ANCHOR_BOTTOM_LEFT:
	return "Anchor.BOTTOM_LEFT";
      case ANCHOR_LEFT:
	return "Anchor.LEFT";
      case ANCHOR_CENTER:
	return "Anchor.CENTER";
      default:
	throw new Error("Unknown Anchor value");
      }
    }

    public AnchorPropertyEditor(){
      super(enumValues);
    }
  }

  /**
   * Static initializer registers the editor with the property editor 
   * manager
   */
  static {
    PropertyEditorManager.registerEditor(Anchor.class, AnchorPropertyEditor.class);
  }
}
