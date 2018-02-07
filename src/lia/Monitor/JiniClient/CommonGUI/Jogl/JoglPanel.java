/*
 * Created on 28.05.2004 18:19:38
 * Filename: JoglPanel.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URL;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.media.opengl.awt.GLCanvas;
//import javax.media.opengl.GLCanvas;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.NetResourceClassLoader;
import lia.Monitor.monitor.AppConfig;
import net.jini.core.lookup.ServiceID;

/**
 * @author Luc
 *
 * JoglPanel
 */
public abstract class JoglPanel extends JPanel implements graphical {

    public static Globals globals = null;
    public static DataGlobals dglobals = null;

    public JPanel toolbarsPanel;
    public JPanel toolbarsPanel2;
    public JPanel toolbar;
    public JToolBar statusbar;
    public JLabel status;
    public JSlider sliderSlider;
    public JSlider scaleSlider;
    public JSlider timeSlider;

    public DataRenderer renderer;

    public JButton proj_button;
    public JComponent grpNR = null;//group of nodes renderers

    public SerMonitorBase monitor;

    /** default dimension for sliders (and preffered one) */
    protected Dimension dimSliderDefault = new Dimension(130, 24/*32*/);

    /** default font for toolbars */
    protected Font fontDefault;

    /** Initialize statusbar */
    protected void buildStatusbar() {

        statusbar = new JToolBar(SwingConstants.HORIZONTAL);
        statusbar.setFloatable(false);
        statusbar.setLayout(new BorderLayout());
        status = new JLabel(" ");
        status.setHorizontalAlignment(JLabel.CENTER);
        statusbar.add(status, BorderLayout.CENTER);

        //add(statusbar);
    }

    public JoglPanel() {
        fontDefault = new Font("Arial", Font.BOLD, 10);
    }

    /** Initialize canvas */
    protected void buildCanvas() {

        globals.mainPanel = this;

        //set default property so that drop down menu get over canvas
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        final GLCanvas canvas = new GLCanvas();//GLDrawableFactory.getFactory().createGLCanvas(new GLCapabilities());
        globals.canvas = canvas;

        //test oct save app
        //      TestOct renderer = new TestOct();
        //      canvas.addGLEventListener( renderer);

        renderer = new DataRenderer();
        canvas.addGLEventListener(renderer);
        canvas.addMouseListener(renderer.uil);
        canvas.addMouseMotionListener(renderer.uil);
        canvas.addMouseWheelListener(renderer.uil);
        canvas.addKeyListener(renderer.uil);

        //this.add(canvas);

        TextureLoadThread tlt = new TextureLoadThread();
        tlt.start();

        final Timer animationTimer = new java.util.Timer();
        animationTimer.schedule/*AtFixedRate*/(new IdleTask(), 0, 10);

        /*		frame.addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e)
         {
         animationTimer.cancel();
         System.out.println("App ended. Hopefully no bug detected. Please try again.");
         System.exit(0);
         }
         });
         frame.setVisible(true);
         */
        BackgroundWorker.schedule(new TimerTask() {
            public void run() {
                Thread.currentThread().setName(" ( ML ) - JOGL - JoglPanel repaint Timer Thread");
                if (!globals.canvas.isVisible()) {
                    return;
                }
                globals.canvas.repaint();
            }

        }, 1000, 4000);

    }

    public ImageIcon loadImage(String resource) {

        ImageIcon ico = null;
        ClassLoader myClassLoader = getClass().getClassLoader();
        try {
            URL resLoc = myClassLoader.getResource(resource);
            ico = new ImageIcon(resLoc);
        } catch (Exception e) {
            System.out.println("Failed to get image ..." + resource);
        }
        return ico;

    } //loadImage

    protected void buildToolbars() {
        buildToolbar();
        //      buildSecondToolbar();
        buildAnotherSecondToolbar();
    }

    /** Initialize toolbar */
    protected void buildToolbar() {
        toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setFont(fontDefault);
        //        toolbar.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        //        toolbar.setFloatable(false);
        //      toolbar.setBorder(BorderFactory.createRaisedBevelBorder());

        //add change nodes renderer combo
        //      comboNodesRenderer = new JComboBox();
        //      comboNodesRenderer.setActionCommand("changeNodesRenderer");
        //      comboNodesRenderer.addActionListener(renderer.uil);
        //      comboNodesRenderer.setSize(30,20);

        //      toolbar.add(proj_button);

        //      toolbarsPanel.add(proj_button);

        toolbarsPanel.add(toolbar);

    }

    /*    protected void buildSecondToolbar() {
            JToolBar toolbar;
            toolbar = new JToolBar(SwingConstants.HORIZONTAL);
            toolbar.setFloatable(false);
            toolbar.setBorder(BorderFactory.createEmptyBorder());
    //        toolbar.setBorder(BorderFactory.createRaisedBevelBorder());

            JButton reset_button = new JButton("Reset");
            reset_button.setActionCommand("reset");
            reset_button.addActionListener(renderer.uil);

            JToggleButton select_button = new JToggleButton("Select");
            select_button.setActionCommand("select");
            select_button.addActionListener(renderer.uil);
            select_button.setSelected(true);

            JToggleButton rotate_button = new JToggleButton("Rotate");
            rotate_button.setActionCommand("rotate");
            rotate_button.addActionListener(renderer.uil);

            JToggleButton translate_button = new JToggleButton("Translate");
            translate_button.setActionCommand("translate");
            translate_button.addActionListener(renderer.uil);

            JToggleButton zoom_button = new JToggleButton("Zoom");
            zoom_button.setActionCommand("zoom");
            zoom_button.addActionListener(renderer.uil);

            JLabel behaviorsLabel = new JLabel("Left mouse button: ");

            ButtonGroup mouseBehaviors = new ButtonGroup();
            mouseBehaviors.add(select_button);
            mouseBehaviors.add(rotate_button);
            mouseBehaviors.add(zoom_button);
            mouseBehaviors.add(translate_button);

            JLabel scaleLabel = new JLabel("Scale nodes");
            scaleSlider = new JSlider(0, 100, 50);
            scaleSlider.setMajorTickSpacing(50);
            scaleSlider.setMinorTickSpacing(10);
            scaleSlider.setPaintTicks(true);
            scaleSlider.addChangeListener(renderer.uil);
            scaleSlider.setMaximumSize(new Dimension(80, 32));
            scaleLabel.setLabelFor(scaleSlider);

            JLabel timeLabel = new JLabel("Speed");
            timeSlider = new JSlider(0, 100, 0);
            timeSlider.setMinorTickSpacing(20);
            timeSlider.setPaintTicks(true);
            timeSlider.addChangeListener(renderer.uil);
            timeSlider.setMaximumSize(new Dimension(80, 32));
            timeLabel.setLabelFor(timeSlider);

            toolbar.add(new JToolBar.Separator());
            toolbar.add(reset_button);
            toolbar.add(new JToolBar.Separator());
            toolbar.add(behaviorsLabel);
            toolbar.add(select_button);
            toolbar.add(rotate_button);
            toolbar.add(translate_button);
            toolbar.add(zoom_button);
            toolbar.add(new JToolBar.Separator());
            toolbar.add(scaleLabel);
            toolbar.add(scaleSlider);
            toolbar.add(timeLabel);
            toolbar.add(timeSlider);
            toolbar.add(Box.createHorizontalGlue());

            toolbarsPanel2.add(toolbar);
        }*/

    protected void buildAnotherSecondToolbar() {
        JPanel toolbar;
        //        toolbar = new JPanel();
        //        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
        //        toolbar.setFont(fontDefault);
        //        toolbar.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        //        toolbar.setBorder(BorderFactory.createRaisedBevelBorder());
        toolbar = toolbarsPanel2;

        JButton reset_button = new JButton("Reset");
        reset_button.setFont(fontDefault);
        reset_button.setActionCommand("reset");
        reset_button.addActionListener(renderer.uil);

        JLabel sliderLabel = new JLabel("Slider");
        sliderLabel.setFont(fontDefault);
        sliderSlider = new JSlider(-20, 20, 0);
        sliderSlider.setFont(fontDefault);
        sliderSlider.setMajorTickSpacing(10);
        sliderSlider.setMinorTickSpacing(2);
        sliderSlider.setPaintTicks(true);
        sliderSlider.addChangeListener(renderer.uil);
        sliderSlider.setPreferredSize(dimSliderDefault);
        sliderSlider.setMaximumSize(dimSliderDefault);
        sliderLabel.setLabelFor(sliderSlider);

        JRadioButton zoom_button = new JRadioButton("Zoom");
        zoom_button.setFont(fontDefault);
        zoom_button.setActionCommand("zoom_radio");
        zoom_button.addActionListener(renderer.uil);
        zoom_button.setSelected(true);

        JRadioButton rotate_button = new JRadioButton("Rotate");
        rotate_button.setFont(fontDefault);
        rotate_button.setActionCommand("rotate_radio");
        rotate_button.addActionListener(renderer.uil);

        JLabel behaviorsLabel = new JLabel("Action: ");
        behaviorsLabel.setFont(fontDefault);

        ButtonGroup mouseBehaviors = new ButtonGroup();
        mouseBehaviors.add(rotate_button);
        mouseBehaviors.add(zoom_button);

        JLabel scaleLabel = new JLabel("Scale nodes");
        scaleLabel.setFont(fontDefault);
        scaleSlider = new JSlider(0, 100, 50);
        scaleSlider.setFont(fontDefault);
        scaleSlider.setMajorTickSpacing(50);
        scaleSlider.setMinorTickSpacing(10);
        scaleSlider.setPaintTicks(true);
        scaleSlider.addChangeListener(renderer.uil);
        scaleSlider.setPreferredSize(dimSliderDefault);
        scaleSlider.setMaximumSize(dimSliderDefault);
        scaleLabel.setLabelFor(scaleSlider);

        JLabel timeLabel = new JLabel("Speed");
        timeLabel.setFont(fontDefault);
        timeSlider = new JSlider(0, 100, 0);
        timeSlider.setFont(fontDefault);
        timeSlider.setMinorTickSpacing(20);
        timeSlider.setPaintTicks(true);
        timeSlider.addChangeListener(renderer.uil);
        timeSlider.setPreferredSize(dimSliderDefault);
        timeSlider.setMaximumSize(dimSliderDefault);
        timeLabel.setLabelFor(timeSlider);

        toolbar.add(new JToolBar.Separator());
        toolbar.add(reset_button);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(behaviorsLabel);
        toolbar.add(zoom_button);
        toolbar.add(rotate_button);
        toolbar.add(sliderSlider);
        toolbar.add(new JToolBar.Separator());
        toolbar.add(scaleLabel);
        toolbar.add(scaleSlider);
        toolbar.add(timeLabel);
        toolbar.add(timeSlider);
        toolbar.add(Box.createHorizontalGlue());

        //        toolbarsPanel2.add(toolbar);
    }

    private void setProp(JComponent c, Dimension d, Font f) {
        c.setFont(f);
        c.setPreferredSize(d);
        c.setMaximumSize(d);
        c.setMinimumSize(d);
    }

    public void checkToolbar() {
        //System.out.println("number of renderers: "+renderer.getNodesRenderersSize());
        if (renderer.getNodesRenderersSize() >= 1) {
            ButtonGroup bg = new ButtonGroup();
            grpNR = new JPanel();

            //            Font f = new Font("Arial", Font.PLAIN, 10);
            Dimension dpan = new Dimension(100, 30);
            Dimension drb = new Dimension(100, 14);

            ((JPanel) grpNR).setLayout(new GridLayout(0, 1));
            setProp(grpNR, dpan, fontDefault);

            for (int i = 0; i < renderer.getNodesRenderersSize(); i++) {
                renderer.addNodesRendererAction(i, bg, (JPanel) grpNR, drb, fontDefault);
            }

            //          comboNodesRenderer = new JCheckBox();
            //          comboNodesRenderer.setActionCommand("changeNodesRenderer");
            //          comboNodesRenderer.addActionListener(renderer.uil);
            //          comboNodesRenderer.setSize(30,20);

            toolbar.add(grpNR);
            //          toolbar.add(comboNodesRenderer);
        }
        ;
    }

    protected abstract void buildOptPan();

    public void updateNode(rcNode node) {
        //nothing?
    }

    public void gupdate() {
        //nothing?
        //System.out.println("gupdate");
        globals.canvas.repaint();
    }

    public void setNodes(Map<ServiceID, rcNode> snodes, Vector<rcNode> vnodes) {
        dglobals.vnodes = vnodes;
        dglobals.snodes = snodes;
    }

    public void setSerMonitor(SerMonitorBase ms) {
        this.monitor = ms;

        /*
         * not very well... creating here the netresourceclassloader..
         *
         */
        globals.myMapsClassLoader = new NetResourceClassLoader(JoglPanel.globals.mainPanel.monitor.mainClientClass);
        //set progress bar for class loader
        globals.myMapsClassLoader.setProgressBar(monitor.main.jpbJarDownProgress, monitor.main.jJarDownBar);
        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            Texture.nInitialLevel = Integer.parseInt(AppConfig.getProperty("jogl.Texture.nForceInitialLevel",
                    prefs.get("Texture.StartUpLevel", AppConfig.getProperty("jogl.Texture.nInitialLevel", "0"))));
            Texture.nMaximumLevel = Integer.parseInt(prefs.get("Texture.MaximumLevel", "-1"));
            if ((Texture.nMaximumLevel != -1) && (Texture.nMaximumLevel < Texture.nInitialLevel)) {
                Texture.nMaximumLevel = Texture.nInitialLevel;
            }
            //            System.out.println("maximum allowed level of detail is: "+Texture.nMaximumLevel);
            Texture.textureDataSpace.setMaxTextureCacheSize(Float.parseFloat(prefs.get(
                    "TextureDataSpace.maxTextureCacheSize", "10")));
        } catch (Exception ex) {
            //????
            System.out.println("[JoglPanel] Could not save texture start up level preference, error: "
                    + ex.getMessage());
        }
    }

    public void setMaxFlowData(rcNode n, Vector v) {
        //nothing?
    }

    public void new_global_param(String name) {
        //nothing?
    }

    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);
        if (aFlag == true) {
            //System.out.println("show and set focus on globe");
            globals.canvas.requestFocus();
            globals.canvas.setVisible(true);
        } else {
            globals.canvas.setVisible(false);
            //System.out.println("hide globe");
        }
    }

    /**
     * @author mluc
     * @since Aug 16, 2006
     */
    public void init() {
        globals = new Globals();
        dglobals = new DataGlobals();

        setLayout(new BorderLayout()); //new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(800, 400));

        toolbarsPanel = new JPanel();
        toolbarsPanel.setLayout(new BoxLayout(toolbarsPanel, BoxLayout.X_AXIS));
        //        toolbarsPanel.setBorder(BorderFactory.createLineBorder(Color.GREEN));

        toolbarsPanel2 = new JPanel();
        toolbarsPanel2.setLayout(new BoxLayout(toolbarsPanel2, BoxLayout.X_AXIS));
        //        toolbarsPanel2.setBorder(BorderFactory.createLineBorder(Color.GREEN));
        //        toolbarsPanel2.setBorder(BorderFactory.createEmptyBorder());

        //check to see if class loader is ok
        //      if ( this.getClass().getClassLoader() != Thread.currentThread().getContextClassLoader() ) {
        //      System.out.println("Invalid class loader: class loader is different than thread's class loader\nChanging thread's class loader!");
        //      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        //      };
        buildCanvas();
        buildToolbars();
        buildOptPan();
        buildStatusbar();

        JPanel panelTool = new JPanel();
        panelTool.setLayout(new BoxLayout(panelTool, BoxLayout.Y_AXIS));
        panelTool.add(toolbarsPanel);
        panelTool.add(toolbarsPanel2);
        //        panelTool.setBorder(BorderFactory.createLineBorder(Color.BLUE));

        JPanel panelTools = new JPanel();
        panelTools.setLayout(new BoxLayout(panelTools, BoxLayout.X_AXIS));
        //        panelTools.setBorder(BorderFactory.createLineBorder(Color.RED));

        //load images
        dglobals.iconSphereProj = loadImage("lia/images/joglpanel/plane2globe_med.jpg");
        dglobals.iconPlaneProj = loadImage("lia/images/joglpanel/globe2plane_med.jpg");
        //add change projection button
        proj_button = new JButton(null, globals.bMapTransition2Sphere ? dglobals.iconSphereProj
                : dglobals.iconPlaneProj);
        proj_button.setActionCommand("changeProjection");
        proj_button.addActionListener(renderer.uil);
        proj_button.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));//BorderFactory.createLineBorder(Color.black));

        panelTools.add(proj_button);
        panelTools.add(panelTool);

        add(panelTools, BorderLayout.NORTH);
        add(globals.canvas, BorderLayout.CENTER);
        add(statusbar, BorderLayout.SOUTH);

        //there is no renderer for this base class
    }

}