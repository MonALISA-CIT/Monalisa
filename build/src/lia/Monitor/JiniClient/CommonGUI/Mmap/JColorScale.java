package lia.Monitor.JiniClient.CommonGUI.Mmap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * 
 * @author catac
 *
 * displays a color scale from a min value w/ a min color to a max 
 * value w/ a max color
 */
public class JColorScale extends JPanel {

    /**
     * 
     */
    private static final long serialVersionUID = -7234065968198195474L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(JColorScale.class.getName());

    public double minValue;
    public double maxValue;
    public double minLimitValue;//the minimal limit for minValue
    public double maxLimitValue;//the maximal limit for maxValue
    public Color minColor;
    public Color maxColor;

    public DecimalFormat formatter;
    private final JLabel minLabel;
    private final JLabel maxLabel;
    private final JScalePanel scalePanel;
    private boolean noText = false;
    public String units = "";

    public JColorScale() {
        minValue = 0.0;
        maxValue = 100.0;
        minLimitValue = 0.0;
        maxLimitValue = 100.0;
        minColor = Color.CYAN;
        maxColor = Color.BLUE;
        formatter = new DecimalFormat("###,###.#");
        minLabel = new JLabel(" " + formatter.format(minValue) + " ");
        maxLabel = new JLabel(" " + formatter.format(maxValue));
        scalePanel = new JScalePanel();

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(maxLabel, BoxLayout.X_AXIS);
        add(scalePanel, BoxLayout.X_AXIS);
        add(minLabel, BoxLayout.X_AXIS);
    }

    public void setLimitValues(double minLimit, double maxLimit) {
        minLimitValue = minLimit;
        maxLimitValue = maxLimit;
    }

    public void setValues(double min, double max) {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " setValues : [ min " + min + ", max " + max + " ]");
        }
        minValue = min;
        maxValue = max;
        if (noText) {
            minLabel.setText("");
            maxLabel.setText("");
        } else {
            minLabel.setText(" " + formatter.format(minValue) + " ");
            maxLabel.setText(" " + formatter.format(maxValue) + " " + units);
        }
        invalidate();
        repaint();
    }

    public void setColors(Color min, Color max) {
        minColor = min;
        maxColor = max;
        invalidate();
        repaint();
    }

    public void setLabelFormat(String f, String units) {
        if (f.equals("")) {
            noText = true;
        } else {
            noText = false;
        }
        formatter.applyPattern(f);
        this.units = units;
        setValues(minValue, maxValue);
    }

    public void setFont1(Font f) {
        minLabel.setFont(f);
        maxLabel.setFont(f);
    }

    public Color getColor(double val) {
        if (val == -1) {
            return null;
        }

        double delta = Math.abs(maxValue - minValue);
        if (Math.abs(delta) < 1E-5) {
            return minColor;
        }
        int R, G, B;
        if (maxValue > minValue) {
            R = (int) (((val - minValue) * (maxColor.getRed() - minColor.getRed())) / delta) + minColor.getRed();
            G = (int) (((val - minValue) * (maxColor.getGreen() - minColor.getGreen())) / delta) + minColor.getGreen();
            B = (int) (((val - minValue) * (maxColor.getBlue() - minColor.getBlue())) / delta) + minColor.getBlue();
        } else {
            R = (int) (((val - maxValue) * (minColor.getRed() - maxColor.getRed())) / delta) + maxColor.getRed();
            G = (int) (((val - maxValue) * (minColor.getGreen() - maxColor.getGreen())) / delta) + maxColor.getGreen();
            B = (int) (((val - maxValue) * (minColor.getBlue() - maxColor.getBlue())) / delta) + maxColor.getBlue();
        }

        if (R < 0) {
            R = 0;
        }
        if (R > 255) {
            R = 255;
        }
        if (G < 0) {
            G = 0;
        }
        if (G > 255) {
            G = 255;
        }
        if (B < 0) {
            B = 0;
        }
        if (B > 255) {
            B = 255;
        }

        return new Color(R, G, B);
    }

    // displays only the color scale, from min to max color form outer class
    class JScalePanel extends JPanel {

        @Override
        public void paint(Graphics gr) {
            int dx = getWidth();
            int dy = getHeight() - 4;
            float incR = (maxColor.getRed() - minColor.getRed()) / (float) dx;
            float incG = (maxColor.getGreen() - minColor.getGreen()) / (float) dx;
            float incB = (maxColor.getBlue() - minColor.getBlue()) / (float) dx;

            float r = minColor.getRed();
            float g = minColor.getGreen();
            float b = minColor.getBlue();
            for (int x = 0; x < dx; x++, r += incR, g += incG, b += incB) {
                gr.setColor(new Color((int) r, (int) g, (int) b));
                gr.drawLine(x, 2, x, dy);
            }
        }
    }

}
