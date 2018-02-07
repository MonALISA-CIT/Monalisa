/*
 * Created on May 27, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.Monitor.JiniClient.CommonGUI.Jogl.Globals;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Texture;
import lia.Monitor.JiniClient.CommonGUI.Jogl.TextureDataSpace;
import lia.Monitor.JiniClient.CommonGUI.Jogl.util.ChangeRootTexture;

/**
 * sets default start-up resolution for jogl panel 
 *
 * May 27, 2005 - 11:26:15 AM
 */
public class ChangeResFrame extends JFrame implements ActionListener 
{
    private static final long serialVersionUID = 1L;

    //reference to snodes through monitor
    private final SerMonitorBase monitor;
    
    private JTextField jWebPaths;
    private JTextField jCacheLocation;
    private JLabel labelSize;
    private JSlider jMapsMemoryCacheSize;
    
    public ChangeResFrame(SerMonitorBase mon) {
        super("3D Map images optios");//Configure base resolution");
        this.monitor=mon;
        Font f = new Font("Default", Font.PLAIN, 12);
        this.getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER,0,0));
        Dimension spacing = new Dimension(5,0);
        getContentPane().add(Box.createRigidArea(spacing));
        this.setResizable(false);
        //set preferred dimensions for window
        setSize(new Dimension(200,200));
        //get screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        //set location relative to screen
        setLocation(
                (screenSize.width - getWidth()) / 2,
                (screenSize.height - getHeight()) / 2
        );
        JPanel panel = new JPanel();
        JPanel p1;
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label;
        labelSize = new JLabel("Memory Cache for Maps: "+Texture.textureDataSpace.getMaxTextureCacheSize()+"MB");
        labelSize.setFont(f);
        labelSize.setAlignmentX(LEFT_ALIGNMENT);
        labelSize.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        p1 = new JPanel();
        p1.setOpaque(false);
        p1.setLayout(new BorderLayout());
        p1.add(labelSize, BorderLayout.WEST);
        panel.add(p1);
        JPanel panelNoTextures = new JPanel();
        panelNoTextures.setLayout(new BoxLayout(panelNoTextures, BoxLayout.X_AXIS));
        label = new JLabel(""+TextureDataSpace.getMinCacheSize()+"MB");
        label.setFont(f);
        panelNoTextures.add(label);
        jMapsMemoryCacheSize = new JSlider(0,100);
    	jMapsMemoryCacheSize.setValue((int)(100*(Texture.textureDataSpace.getMaxTextureCacheSize()-TextureDataSpace.getMinCacheSize())/(400-TextureDataSpace.getMinCacheSize())));
    	jMapsMemoryCacheSize.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				int val = ((JSlider)e.getSource()).getValue();
				float fVal;
				fVal = TextureDataSpace.getMinCacheSize();
				fVal += (val*(400-TextureDataSpace.getMinCacheSize())/100f);
				val = (int)(fVal*10f);
				if ( val%10 == 0 )
					fVal = (int)(val/10);
				else
					fVal = val/10f;
		        labelSize.setText("Memory Cache for Maps: "+fVal+"MB ("+TextureDataSpace.getNoCachedTextures(fVal)+" maps)");
			}
    	});
        panelNoTextures.add(jMapsMemoryCacheSize);
        label = new JLabel("400MB");
        label.setFont(f);
        panelNoTextures.add(label);
        panel.add(panelNoTextures);
        
        jWebPaths = new JTextField(JoglPanel.globals.myMapsClassLoader.getExtraImagesLocations());
        jWebPaths.setPreferredSize(new Dimension(150, 20));
        jWebPaths.setFont(f);
        jCacheLocation = new JTextField(JoglPanel.globals.myMapsClassLoader.getCacheDir());
        jCacheLocation.setPreferredSize( new Dimension(150, 20));
        jCacheLocation.setFont(f);
        label = new JLabel("Set web paths for maps:");
        label.setFont(f);
        p1 = new JPanel();
        p1.setOpaque(false);
        p1.setLayout(new BorderLayout());
        p1.add(label, BorderLayout.WEST);
        panel.add(p1);
        panel.add(jWebPaths);
        label = new JLabel("Set local cache directory path:");
        label.setFont(f);
        p1 = new JPanel();
        p1.setOpaque(false);
        p1.setLayout(new BorderLayout());
        p1.add(label, BorderLayout.WEST);
        panel.add(p1);
        JPanel cachePanel = new JPanel();
        cachePanel.setLayout(new BoxLayout(cachePanel, BoxLayout.X_AXIS));
        cachePanel.add(jCacheLocation);
        JButton butClearCache = new JButton("Clear cache");
        butClearCache.setPreferredSize(new Dimension(80,20));
        Insets m = butClearCache.getMargin();
        m.left=2;
        m.right=2;
        butClearCache.setMargin(m);
        butClearCache.setFont(f);
        butClearCache.addActionListener(this);
        butClearCache.setActionCommand("clear cache");
        cachePanel.add(butClearCache);
        panel.add(cachePanel);
        JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout());

        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
        label = new JLabel("Change resolution");
        label.setFont(f);
        panel3.add(label);
//      In initialization code:
        //Create the radio buttons.
        JRadioButton res[] = new JRadioButton[ChangeRootTexture.sResolutions.length];
        for ( int i=0; i<ChangeRootTexture.sResolutions.length; i++) {
            res[i] = new JRadioButton(ChangeRootTexture.sResolutions[i]);
            res[i].setFont(f);
        };
        //res0.setSelected(true);

        //Group the radio buttons.
        ButtonGroup group = new ButtonGroup();
        //Register a listener for the radio buttons.
        for (int i=0; i<res.length; i++) {
            res[i].setActionCommand("res"+i);
            group.add(res[i]);
            res[i].addActionListener(this);
            panel3.add(res[i]);
        }        

        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            int selected = Integer.parseInt(prefs.get( "Texture.StartUpLevel", "0"));
            if ( selected >=0 && selected<ChangeRootTexture.sResolutions.length )
                res[selected].setSelected(true);
            else
                res[0].setSelected(true);
        } catch (Exception ex ) {
            //????
        }

        JPanel panel4 = new JPanel();
        panel4.setLayout(new BoxLayout(panel4, BoxLayout.Y_AXIS));
        label = new JLabel("Set max resolution");
        label.setFont(f);
        panel4.add(label);
//      In initialization code:
        //Create the radio buttons.
        JRadioButton resX[] = new JRadioButton[ChangeRootTexture.sResolutions.length-Texture.nInitialLevel];
        for ( int i=0; i<ChangeRootTexture.sResolutions.length-Texture.nInitialLevel; i++) {
            resX[i] = new JRadioButton(ChangeRootTexture.sResolutions[i+Texture.nInitialLevel]);
            resX[i].setFont(f);
        };
        //res0.setSelected(true);

        //Group the radio buttons.
        ButtonGroup groupX = new ButtonGroup();
        //Register a listener for the radio buttons.
        for (int i=0; i<resX.length; i++) {
            resX[i].setActionCommand("maxres"+(i+Texture.nInitialLevel));
            groupX.add(resX[i]);
            resX[i].addActionListener(this);
            panel4.add(resX[i]);
        }
        JRadioButton rbUnlimited = new JRadioButton("unlimited");
        rbUnlimited.setFont(f);
        rbUnlimited.setActionCommand("maxres-1");
        groupX.add(rbUnlimited);
        rbUnlimited.addActionListener(this);
        panel4.add(rbUnlimited);

        try {
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            int selected = Integer.parseInt(prefs.get( "Texture.MaximumLevel", "-1"));
            //System.out.println("selected max level: "+selected+" initial level: "+Texture.nInitialLevel);
            if ( selected-Texture.nInitialLevel >= 0 && selected<ChangeRootTexture.sResolutions.length )
                resX[selected-Texture.nInitialLevel].setSelected(true);
            else
                rbUnlimited.setSelected(true);
        } catch (Exception ex ) {
            //????
        }
        
        panel2.add(panel3, BorderLayout.WEST);
        panel2.add(panel4, BorderLayout.EAST);
        panel.add(panel2);
        
        JButton close = new JButton("Save&Close");
        close.setFont(f);
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setAndDispose();
            }
        });
        JPanel panel5 = new JPanel();
        panel5.setAlignmentX(CENTER_ALIGNMENT);
        panel5.add(close);
        panel.add(panel5);
        
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().add(panel);
        getContentPane().add(Box.createRigidArea(spacing));
        this.pack();
        
    }
    
    private void setAndDispose()
    {
        //set new texture level
        try {
            //first set paths
            Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
            String newWebPath = jWebPaths.getText();
            if ( !newWebPath.equals(JoglPanel.globals.myMapsClassLoader.getExtraImagesLocations()) ) {
                JoglPanel.globals.myMapsClassLoader.setExtraImagesLocations(newWebPath);
                prefs.put( "Texture.WebPaths", newWebPath);
            }
            String newCachePath = jCacheLocation.getText();
            //if ( !newCachePath.equals(JoglPanel.globals.myMapsClassLoader.getCacheDir()) ) {
                JoglPanel.globals.myMapsClassLoader.setCacheDir(newCachePath);
                prefs.put( "Texture.CacheDirectory", newCachePath);
            //};
            int selected = Integer.parseInt(prefs.get( "Texture.StartUpLevel", "0"));
            if ( selected >=0 && selected<ChangeRootTexture.sResolutions.length && selected!=Texture.nInitialLevel) {
            	ChangeRootTexture.init(selected, ChangeRootTexture.CRT_KEY_MODE_LOAD, JoglPanel.globals.mainPanel.monitor.main.jpbTextLoadProgress, JoglPanel.globals.mainPanel.monitor.main.jTextLoadBar);
//                ChangeRootTexture crtMonitor = new ChangeRootTexture(selected);
//                crtMonitor.setProgressBar(monitor.main.jpbTextLoadProgress, monitor.main.jTextLoadBar);
//                if ( !crtMonitor.init() )
//                    System.out.println("Resolution changing in progress.... Please wait");
            }
            int max_selected = Integer.parseInt(prefs.get( "Texture.MaximumLevel", "-1"));
            if ( max_selected!=-1 && max_selected < selected ) {
                max_selected = selected;
                prefs.put("Texture.MaximumLevel", ""+max_selected);
            };
            Texture.nMaximumLevel = max_selected;
            //set maximum number of cached textures
			int val = jMapsMemoryCacheSize.getValue();
			float fVal;
			fVal = TextureDataSpace.getMinCacheSize();
			fVal += (val*(400-TextureDataSpace.getMinCacheSize())*.01f);
			val = (int)(fVal*10f);
			if ( val%10 == 0 )
				fVal = (int)(val/10f);
			else
				fVal = val/10f;
            prefs.put("TextureDataSpace.maxTextureCacheSize", ""+fVal);
            Texture.textureDataSpace.setMaxTextureCacheSize(fVal);
        } catch (Exception ex ) {
            //????
        }
        monitor.main.crFrame = null;
        dispose();
    }
    
    public void actionPerformed(ActionEvent e) {
        if ( "clear cache".equals(e.getActionCommand()) ) {
            //clear cache directory
            Globals.emptyDirectory(new File(JoglPanel.globals.myMapsClassLoader.getCacheDir()+Texture.pathSeparator+"cached_maps"));
        } else if ( e.getActionCommand().startsWith("res") ) {//change resolution buttons
            try {
                Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                int old_selected = Integer.parseInt(prefs.get( "Texture.StartUpLevel", "0"));
                int selected = Integer.parseInt(e.getActionCommand().substring(3));
                if ( selected >=0 && selected <ChangeRootTexture.sResolutions.length && selected!=old_selected ) {
                    prefs.put("Texture.StartUpLevel", ""+selected);
                };
            } catch (Exception ex ) {
                //????
            }
        } else if ( e.getActionCommand().startsWith("maxres") ) {//change resolution buttons
            try {
                Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                int old_selected = Integer.parseInt(prefs.get( "Texture.MaximumLevel", "-1"));
                int selected = Integer.parseInt(e.getActionCommand().substring(6));
                if ( selected!=old_selected )
                if ( selected >= Texture.nInitialLevel && selected <ChangeRootTexture.sResolutions.length ) {
                    prefs.put("Texture.MaximumLevel", ""+selected);
                } else
                    prefs.put("Texture.MaximumLevel", "-1");
            } catch (Exception ex ) {
                //????
            }
        };
    }
}
