
package lia.Monitor.AppControlClient ;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

public class Fill 
{
    public void paintFill(Component c, Graphics g, Rectangle r) {
	// assert c, g, not null
	g.setColor(c.getBackground());
	g.fillRect(r.x, r.y, r.width, r.height);
    }

    public void paintFill(Container c, Graphics g) {
	// assert c, g, not null
	Insets insets = c.getInsets();
	int x = insets.left;
	int y = insets.top;
	int w = c.getWidth() - (insets.left + insets.right);
	int h = c.getHeight() - (insets.top + insets.bottom);
	paintFill(c, g, new Rectangle(x, y, w, h));
    }

    public void paintFill(Component c, Graphics g, int x, int y, int w, int h) {
	paintFill(c, g, new Rectangle(x, y, w, h));
    }
}


