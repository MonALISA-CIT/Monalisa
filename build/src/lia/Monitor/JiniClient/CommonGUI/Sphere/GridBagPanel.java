/*
 * @(#)GridBagPanel.java
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

import java.awt.AWTError;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * Utility class designed to make it easier to use the 
 * GridBagLayout layout manager.
 * 
 * @author              Vincent Hardy
 * @version             1.0, 04/09/1998
 * @see                 j2d.ui.GridBagConstants
 */

public 
class GridBagPanel extends JPanel implements GridBagConstants{
  /**
   * leftMarginDefault declaration
   */
  int leftMarginDefault=5;
  
  /**
   * topMarginDefault declaration
   */
  int topMarginDefault=5;
  
  /**
   * Gets the leftMarginDefault property
   *
   * @return the leftMarginDefault property value
   */
  public int getLeftMarginDefault(){
    return leftMarginDefault;
  }
  
  /**
   * Sets the leftMarginDefault property
   *
   * @param leftMarginDefault the new value
   */
  public void setLeftMarginDefault(int leftMarginDefault){
    this.leftMarginDefault = leftMarginDefault;
  }
  
  /**
   * Gets the topMarginDefault property
   *
   * @return the topMarginDefault property value
   */
  public int getTopMarginDefault(){
    return topMarginDefault;
  }
  
  /**
   * Sets the topMarginDefault property
   *
   * @param topMarginDefault the new value
   */
  public void setTopMarginDefault(int topMarginDefault){
    this.topMarginDefault = topMarginDefault;
  }
  
  /**
   * Sets the layout manager to GridBagLayout
   */
  public GridBagPanel(){
    super(new GridBagLayout());
  }

  /**
   * @exception AwtError as a GridBagPanel can only use a GridBagLayout
   */
  public void setLayout(LayoutManager layout){
    if(!(layout instanceof GridBagLayout))
      throw new AWTError("Should not set layout in a GridBagPanel");
    else
      super.setLayout(layout);
  }

  /**
   * This version uses default margins. It assumes components are added in
   * cells of positive coordinates. Top margin for components added at the top
   * is 0. Left margins for components added on the left is 0. Otherwise, top
   * and left margins default to the values of the defaultLeftMargin and defaultTopMargin
   * properties. Bottom and right margins are always set to 0.
   *
   * @param cmp Component to add to the panel
   * @param gridx x position of the cell into which component should be added
   * @param gridy y position of the cell into which component should be added
   * @param gridwidth width, in cells, of the space occupied by the component in the grid
   * @param gridheight height, in cells, of the space occupied by the component in the grid
   * @param anchor placement of the component in its allocated space: WEST, NORTH, SOUTH, NORTHWEST, ...
   * @param fill out should the component be resized within its space? NONE, BOTH, HORIZONTAL, VERTICAL.
   * @param weightx what amount of extra horizontal space, if any, should be given to this component?
   * @param weighty what amount of extra vertical space, if any, should be given to this component?
   */
  public void add(Component cmp, int gridx, int gridy,
		  int gridwidth, int gridheight, int anchor, int fill, 
		  double weightx, double weighty){
    Insets insets = new Insets(0,0,0,0);
    if(gridx!=0) insets.left = leftMarginDefault;
    if(gridy!=0) insets.top = topMarginDefault;
    add(this, cmp, gridx, gridy, gridwidth, gridheight, anchor, fill,
	  weightx, weighty, insets);
  }

  /**
   * @param cmp Component to add to the panel
   * @param gridx x position of the cell into which component should be added
   * @param gridy y position of the cell into which component should be added
   * @param gridwidth width, in cells, of the space occupied by the component in the grid
   * @param gridheight height, in cells, of the space occupied by the component in the grid
   * @param anchor placement of the component in its allocated space: WEST, NORTH, SOUTH, NORTHWEST, ...
   * @param fill out should the component be resized within its space? NONE, BOTH, HORIZONTAL, VERTICAL.
   * @param weightx what amount of extra horizontal space, if any, should be given to this component?
   * @param weighty what amount of extra vertical space, if any, should be given to this component?
   * @param insets margins to add around component within its allocated space.
   */
  public void add(Component cmp, int gridx, int gridy,
		  int gridwidth, int gridheight, int anchor, int fill, 
		  double weightx, double weighty, Insets insets){
    add(this, cmp, gridx, gridy, gridwidth, gridheight, anchor, fill,
	weightx, weighty, insets);
  }

  /**
   * @param cnt Container to which component is added
   * @param cmp Component to add to the panel
   * @param gridx x position of the cell into which component should be added
   * @param gridy y position of the cell into which component should be added
   * @param gridwidth width, in cells, of the space occupied by the component in the grid
   * @param gridheight height, in cells, of the space occupied by the component in the grid
   * @param anchor placement of the component in its allocated space: WEST, NORTH, SOUTH, NORTHWEST, ...
   * @param fill out should the component be resized within its space? NONE, BOTH, HORIZONTAL, VERTICAL.
   * @param weightx what amount of extra horizontal space, if any, should be given to this component?
   * @param weighty what amount of extra vertical space, if any, should be given to this component?
   * @param insets margins to add around component within its allocated space.
   */
  public static void add(Container cnt, Component cmp, int gridx, int gridy,
		  int gridwidth, int gridheight, int anchor, int fill, 
		  double weightx, double weighty, Insets insets){
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = gridx;
    constraints.gridy = gridy;
    constraints.gridwidth = gridwidth;
    constraints.gridheight = gridheight;
    constraints.anchor = anchor;
    constraints.fill = fill;
    constraints.weightx = weightx;
    constraints.weighty = weighty;
    constraints.insets = insets;
    cnt.add(cmp, constraints);
  }

}
