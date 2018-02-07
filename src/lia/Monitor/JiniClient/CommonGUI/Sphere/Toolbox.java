/*
 * @(#)Toolbox.java
 * Copyright (c) 1999 Sun Microsystems, Inc. All Rights Reserved.
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license to use,
 * modify and redistribute this software in source and binary code form,
 * provided that i) this copyright notice and license appear on all copies of
 * the software; and ii) Licensee does not utilize the software in a manner
 * which is disparaging to Sun.
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
 * This software is not designed or intended for use in on-line control of
 * aircraft, air traffic, aircraft navigation or aircraft communications; or in
 * the design, construction, operation or maintenance of any nuclear
 * facility. Licensee represents and warrants that it will not use or
 * redistribute the Software for such purposes.
 */

package lia.Monitor.JiniClient.CommonGUI.Sphere;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

public class Toolbox {

    /*
     * Image loading utility.
     */
    protected final static Component component = new Component() {

        /**
         * 
         */
        private static final long serialVersionUID = -5760394757242185285L;
    };

    protected final static MediaTracker tracker = new MediaTracker(component);

    /**
     * Sets the Swing defaults
     */
    public static void swingDefaultsInit() {
        // Set default font
        Font font = new Font("Dialog", Font.PLAIN, 10);
        FontUIResource fontRes = new FontUIResource(font);
        UIManager.put("CheckBox.font", fontRes);
        UIManager.put("PopupMenu.font", fontRes);
        UIManager.put("TextPane.font", fontRes);
        UIManager.put("MenuItem.font", fontRes);
        UIManager.put("ComboBox.font", fontRes);
        UIManager.put("Button.font", fontRes);
        UIManager.put("Tree.font", fontRes);
        UIManager.put("ScrollPane.font", fontRes);
        UIManager.put("TabbedPane.font", fontRes);
        UIManager.put("EditorPane.font", fontRes);
        UIManager.put("TitledBorder.font", fontRes);
        UIManager.put("Menu.font", fontRes);
        UIManager.put("TextArea.font", fontRes);
        UIManager.put("OptionPane.font", fontRes);
        UIManager.put("DesktopIcon.font", fontRes);
        UIManager.put("MenuBar.font", fontRes);
        UIManager.put("ToolBar.font", fontRes);
        UIManager.put("RadioButton.font", fontRes);
        UIManager.put("ToggleButton.font", fontRes);
        UIManager.put("ToolTip.font", fontRes);
        UIManager.put("ProgressBar.font", fontRes);
        UIManager.put("TableHeader.font", fontRes);
        UIManager.put("Panel.font", fontRes);
        UIManager.put("List.font", fontRes);
        UIManager.put("ColorChooser.font", fontRes);
        UIManager.put("PasswordField.font", fontRes);
        UIManager.put("TextField.font", fontRes);
        UIManager.put("Table.font", fontRes);
        UIManager.put("Label.font", fontRes);
        UIManager.put("InternalFrameTitlePane.font", fontRes);
    }

    /**
     * loadImage loads the image located at path.
     * 
     * @param path
     *            location of image file in local file system.
     * @return loaded image at path or url
     * @throws MalformedURLException
     * @see #java
     */
    public static synchronized Image loadImage(String path) throws MalformedURLException {
        File file = new File(path);
        Image image = null;
        URL url = file.toURI().toURL();
        image = loadImage(url);
        return image;
    }

    /**
     * loadImage loads the image located at URL.
     * 
     * @param url
     *            URL where the image file is located.
     * @return loaded image at path or url
     * @see #java
     */
    public static synchronized Image loadImage(URL url) {
        Image image = null;
        image = Toolkit.getDefaultToolkit().getImage(url);

        if (image != null) {
            tracker.addImage(image, 0);
            try {
                tracker.waitForAll();
            } catch (InterruptedException e) {
                tracker.removeImage(image);
                image = null;
            } finally {
                if (image != null)
                    tracker.removeImage(image);

                if (tracker.isErrorAny())
                    image = null;

                if (image != null) {
                    if (image.getWidth(null) < 0 || image.getHeight(null) < 0)
                        image = null;
                }
            }
        }

        return image;
    }

    /**
     * loadImage loads an image from a given file into a BufferedImage.
     * The image is returned in the format defined by the imageType parameter.
     * Note that this is special cased for JPEG images where loading is performed
     * outside the standard media tracker, for efficiency reasons.
     * 
     * @param file
     *            File where the image file is located.
     * @param imageType
     *            one of the image type defined in the BufferedImage class.
     * @return loaded image at path or url
     * @throws MalformedURLException
     * @see java.awt.image.BufferedImage
     */
    public static synchronized BufferedImage loadImage(File file, int imageType) throws MalformedURLException {
        BufferedImage image = null;
        URL url = file.toURI().toURL();
        image = loadImage(url, imageType);

        return image;
    }

    /**
     * loadImage loads an image from a given path into a BufferedImage.
     * The image is returned in the format defined by the imageType parameter.
     * Note that this is special cased for JPEG images where loading is performed
     * outside the standard media tracker, for efficiency reasons.
     * 
     * @param path
     *            Name of file where the image file is located.
     * @param imageType
     *            one of the image type defined in the BufferedImage class.
     * @return loaded image at path or url
     * @throws MalformedURLException
     * @see java.awt.image.BufferedImage
     */
    public static synchronized BufferedImage loadImage(String path, int imageType) throws MalformedURLException {
        File file = new File(path);
        BufferedImage image = null;
        URL url = file.toURI().toURL();
        image = loadImage(url, imageType);

        return image;
    }

    /**
     * loadImage loads an image from a given URL into a BufferedImage.
     * The image is returned in the format defined by the imageType parameter.
     * Note that this is special cased for JPEG images where loading is performed
     * outside the standard media tracker, for efficiency reasons.
     * 
     * @param url
     *            URL where the image file is located.
     * @param imageType
     *            one of the image type defined in the BufferedImage class.
     * @return loaded image at path or url
     * @see java.awt.image.BufferedImage
     */
    public static synchronized BufferedImage loadImage(URL url, int imageType) {
        BufferedImage image = null; // return value

        // Special handling for JPEG images to avoid extra processing if possible.
        if (url == null || !url.toString().toLowerCase().endsWith(".jpg")) {
            Image tmpImage = loadImage(url);
            if (tmpImage != null) {
                image = new BufferedImage(tmpImage.getWidth(null), tmpImage.getHeight(null), imageType);
                Graphics2D g = image.createGraphics();
                g.drawImage(tmpImage, 0, 0, null);
                g.dispose();
            }
        } else {
            BufferedImage tmpImage = loadJPEGImage(url);
            if (tmpImage != null) {
                if (tmpImage.getType() != imageType) {
                    // System.out.println("Incompatible JPEG image type: creating new buffer image");
                    image = new BufferedImage(tmpImage.getWidth(null), tmpImage.getHeight(null), imageType);
                    Graphics2D g = image.createGraphics();
                    g.drawImage(tmpImage, 0, 0, null);
                    g.dispose();
                } else
                    image = tmpImage;
            }
        }
        return image;
    }

    /**
     * loads a JPEG image from a given location.
     * 
     * @param url
     *            URL where the image file is located.
     * @return loaded image at path or url
     */
    public static synchronized BufferedImage loadJPEGImage(URL url) {
        BufferedImage image = null;

        if (url != null) {
            InputStream in = null;
            BufferedInputStream bis = null;
            URLConnection connection = null;
            try {
                connection = url.openConnection();
                connection.setConnectTimeout(20 * 1000);
                connection.setReadTimeout(20 * 1000);
                connection.connect();
                in = connection.getInputStream();
                bis = new BufferedInputStream(in);
                image = ImageIO.read(bis);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (in != null)
                        in.close();
                } catch (Throwable ioe) {
                    // ignore
                }
            }

            if (image != null) {
                // System.out.println("Image type : " + image.getType());
                if (image.getWidth() <= 0 || image.getHeight() <= 0)
                    image = null;
            }
        }

        return image;
    }

    /**
     * @param shapes
     *            array of Shapes which should be concatenated into a single one
     * @return Concatenated Shape. Note that this uses a GeneralPath and not an Area,
     *         which may result in unexpected form when the input Shapes overlap.
     */
    public static Shape concat(Shape shapes[]) {
        GeneralPath shape = new GeneralPath();
        int n = shapes != null ? shapes.length : 0;
        for (int i = 0; i < n; i++)
            shape.append(shapes[i], false); // Do not connect shapes

        return shape;
    }

    /**
     * @param color
     *            color used as a base to create a transparent version. This is usefull for
     *            alpha based gradients, for example.
     */
    public static Color makeTransparent(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 1);
    }

    /**
     * Work around: Loads all fonts. Otherwise (as of 1.2.2), constructors such as:
     * new Font("Curlz MT", Font.PLAIN, 80);
     * will use the default font, not the requested one.
     */
    public static void initFonts() {
        if (!fontsLoaded) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            System.out.println("Initializing fonts .... please wait");
            // Font allFonts[] = env.getAllFonts();
            // System.out.println("Done loading " + allFonts.length + " fonts");

            String fontNames[] = env.getAvailableFontFamilyNames();
            System.out.println("Done initializing " + fontNames.length + " fonts");
            fontsLoaded = true; // Make sure we do not do this twice
        }
    }

    private static boolean fontsLoaded;

    public static final String USAGE = "java com.sun.glf.util.Toolbox <imageFile>";

    /**
     * Unit testing for image loading benchmarking.
     */
    /*
     * public static void main(String args[]){
     * if(args.length<1){
     * System.out.println(USAGE);
     * System.exit(0);
     * }
     * //
     * // Load with default method
     * //
     * TimeProbe probe = new TimeProbe();
     * BufferedImage image = loadImage(args[0], BufferedImage.TYPE_INT_RGB);
     * System.out.println("Image size : " + image.getWidth() + " by " + image.getHeight());
     * System.out.println(probe.traceTime("loadImage took : "));
     * }
     */
}
