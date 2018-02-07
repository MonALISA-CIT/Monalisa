package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.Border;

public class TestBorderFill extends JPanel {

    private final static int MW = 8;

    private final static int MW2 = 2 * MW;

    private final static int T = 0; /* Top */

    private final static int L = 1; /* Left */

    private final static int R = 2; /* Right */

    private final static int B = 3; /* Bottom */

    private final static int TL = 4; /* Top Left */

    private final static int TR = 5; /* Top Right */

    private final static int BL = 6; /* Bottom Left */

    private final static int BR = 7; /* Bottom Right */

    private void toFront(ActionEvent e) {
        getParent().add(this, 0);
        getParent().repaint();
    }

    private void toBack(ActionEvent e) {
        getParent().add(this);
        getParent().repaint();
    }

    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    protected void paintComponent(Graphics g) {
        Insets insets = getInsets();
        int w = getWidth() - (insets.left + insets.right);
        int h = getHeight() - (insets.top + insets.bottom);
        g.setColor(getBackground());
        g.fillRect(insets.left, insets.top, w, h);
    }

    public TestBorderFill(Border border, Color background) {
        super(null);
        setBorder(border);
        setBackground(background);
        setOpaque(false);
        //setDoubleBuffered(true);

    }

    public static void main(String[] args) throws Exception {
        JFrame f = new JFrame("Border Fill");

        URL file0 = TestBorderFill.class.getResource("border0.png");
        Border border0 = new BorderFill(ImageIO.read(file0), new Rectangle[] {
        /* 0 */new Rectangle(16, 0, 5, 10),
        /* 1 */new Rectangle(249, 0, 10, 10),
        /* 2 */new Rectangle(249, 16, 10, 5),
        /* 3 */new Rectangle(249, 101, 10, 10),
        /* 4 */new Rectangle(16, 101, 5, 10),
        /* 5 */new Rectangle(0, 101, 10, 10),
        /* 6 */new Rectangle(0, 16, 10, 5),
        /* 7 */new Rectangle(0, 0, 10, 10)}, new boolean[] { true, true, true, true});
        Color background0 = new Color(0xFFF7D8);

        /*
         * URL file1 = TestBorderFill.class.getResource("border1.png"); Border
         * border1 = new BorderFill( ImageIO.read(file1), new Rectangle[] { new
         * Rectangle(23, 0, 16, 15), new Rectangle(109, 0, 22, 15), new
         * Rectangle(121, 15, 10, 16), new Rectangle(108, 82, 23, 14), new
         * Rectangle(24, 82, 16, 14), new Rectangle(0, 82, 23, 14), new
         * Rectangle(0, 15, 9, 16), new Rectangle(0, 0, 23, 15) }, new
         * boolean[]{true, true, true, true} ); Color background1 = Color.blue;
         */
        TestBorderFill p = new TestBorderFill(border0, background0);
        p.setPreferredSize(new Dimension(640, 480));

        f.getContentPane().add(p, BorderLayout.CENTER);
        WindowListener l = new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        };
        f.addWindowListener(l);
        f.pack();
        f.setVisible(true);
    }
}
