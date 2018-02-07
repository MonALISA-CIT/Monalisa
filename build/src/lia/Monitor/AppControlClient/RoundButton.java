 
package lia.Monitor.AppControlClient ;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Action;
import javax.swing.JButton;

public class RoundButton extends JButton {

  final Color buttonColor = new Color (177,211,253);//251,192,104);
  final Color buttonArmedColor = new Color (163, 195, 234);//204,156,85);
  

   public RoundButton (Action action) {
   	super(action);

   	Dimension size = new Dimension (70,140);
   	setMinimumSize(size);
   	
   	size = getPreferredSize();

   	setPreferredSize(size);
   	
   	setContentAreaFilled(false);
   	
   }
 
  
  public RoundButton(String label) {
    super(label);

    Dimension size = getPreferredSize();

    setPreferredSize(size);
    setContentAreaFilled(false);
  }

// Paint the round background and label.
  protected void paintComponent(Graphics g) {
    if (getModel().isArmed()) {
// You might want to make the highlight color 
   // a property of the RoundButton class.
      g.setColor(buttonArmedColor);
    } else {
      g.setColor(buttonColor);
    }
    g.fillRoundRect(0, 0, getSize().width-1, 
      getSize().height-1,20,20);

// This call will paint the label and the 
   // focus rectangle.
    super.paintComponent(g);
  }

// Paint the border of the button using a simple stroke.
  protected void paintBorder(Graphics g) {
    g.setColor(getForeground());
    g.drawRoundRect(0, 0, getSize().width-1, 
      getSize().height-1,20,20);
  }

// Hit detection.
  Shape shape;
  public boolean contains(int x, int y) {
// If the button has changed size, 
   // make a new shape object.
    if (shape == null || 
      !shape.getBounds().equals(getBounds())) {
      shape = new RoundRectangle2D.Float(0, 0, 
        getWidth(), getHeight(),20,20);
    }
    return shape.contains(x, y);
  }

}
