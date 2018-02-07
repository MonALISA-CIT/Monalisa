package lia.Monitor.JiniClient.CommonGUI.Sphere;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;

import javax.swing.JComponent;
import javax.swing.JFrame;

public class Sphere extends JComponent {
    public void paint(Graphics _g){
        Graphics2D g = (Graphics2D)_g;
        Dimension size = getSize();
        int sphereSize = size.width <= size.height? size.width :
size.height;
//        Color highlightColor = new Color(255, 255, 50);
//        Color midtoneColor = new Color(200, 160, 40);
//        Color shadowColor = new Color(128, 30, 10);
//        float highlightInterval = 2, midtoneInterval = 2;
//        float shadowInterval = 2, blackInterval = 1;
//
        Ellipse2D sphere = new Ellipse2D.Float(0, 0, sphereSize,
sphereSize);
//        Rectangle gradientRect = new Rectangle(-sphereSize/2,
//-sphereSize/2,
//                                               3*sphereSize/2,
//3*sphereSize/2);
//
//        Color gradientColors[] = {Color.white, highlightColor,
//                                  midtoneColor, shadowColor,
//Color.black};
//        float gradientIntervals[] = {highlightInterval, midtoneInterval,
//                                     shadowInterval, blackInterval};
//        RadialGradientPaintExt sphereFilling
//            = new RadialGradientPaintExt(gradientRect,
//                                         gradientColors,
//                                         gradientIntervals);
//
//        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                            RenderingHints.VALUE_ANTIALIAS_ON);
//        g.setPaint(sphereFilling);
		Rectangle rect = new Rectangle(size);
		g.setColor(Color.yellow);
		g.fillOval(0, 0, (int)size.getWidth(),(int)size.getHeight());
		SphereTexture.setTexture(g, rect);
		g.fillOval(0, 0, (int)size.getWidth(),(int)size.getHeight());
//        g.fill(sphere);
    }

    public static void main(String args[]){
        JFrame frame = new JFrame();
        Sphere sphere = new Sphere();
        sphere.setPreferredSize(new Dimension(20, 20));
        frame.getContentPane().add(sphere);
        frame.getContentPane().setBackground(Color.white);

        frame.addWindowListener(new WindowAdapter(){
                public void windowClosing(WindowEvent evt){
                    System.exit(0);
                }
            });

        frame.pack();
        frame.setVisible(true);
    }
}