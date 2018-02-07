package lia.Monitor.JiniClient.VRVS3D;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSeparatorUI;

public class JScrollMenu extends JPopupMenu implements PopupMenuListener, MouseListener, MouseMotionListener {

    private static final long serialVersionUID = 1;

    private JPanel panelMenus = new JPanel();

    private JScrollPane scroll = null;

    public static final Icon EMPTY_IMAGE_ICON = new ImageIcon("menu_spacer.gif");

    private final List<Component> components;

    private static final Hashtable<JScrollMenu, Boolean> showMenu = new Hashtable<JScrollMenu, Boolean>();

    public JScrollMenu() {
        super();
        this.setLayout(new BorderLayout());
        components = new Vector<Component>();
        panelMenus.setLayout(new GridLayout(0, 1));
        panelMenus.setBackground(UIManager.getColor("MenuItem.background"));
        // panelMenus.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        init();
        addPopupMenuListener(this);
        addMouseListener(this);
        panelMenus.addMouseListener(this);
        scroll.addMouseListener(this);
        addMouseMotionListener(this);
        panelMenus.addMouseMotionListener(this);
        scroll.addMouseMotionListener(this);
   
        showMenu.put(this, Boolean.FALSE);

        // addFocusListener(this);
        // panelMenus.addFocusListener(this);
        // scroll.addFocusListener(this);
    }

    private void init() {
        super.removeAll();
        scroll = new JScrollPane();
        scroll.setViewportView(panelMenus);
        scroll.setBorder(null);
        scroll.setMinimumSize(new Dimension(240, 40));
        scroll.setMaximumSize(new Dimension(scroll.getMaximumSize().width, this.getToolkit().getScreenSize().height - 200));
        super.add(scroll, BorderLayout.CENTER);
        // super.add(scroll);
    }

    public void show(Component invoker, int x, int y) {
        init();
        // this.pack();
        panelMenus.validate();
        int maxsize = scroll.getMaximumSize().height;
        int realsize = panelMenus.getPreferredSize().height;

        int sizescroll = 0;

        if (maxsize < realsize) {
            sizescroll = scroll.getVerticalScrollBar().getPreferredSize().width;
        }
        this.pack();
        scroll.setPreferredSize(new Dimension(scroll.getPreferredSize().width + sizescroll + 20, scroll.getPreferredSize().height));
        setSize(new Dimension(scroll.getPreferredSize().width + sizescroll + 20, scroll.getPreferredSize().height + 10));
        setPreferredSize(new Dimension(scroll.getPreferredSize().width + sizescroll + 20, scroll.getPreferredSize().height + 10));

//    	scroll.setPreferredSize(new Dimension((int)scroll.getPreferredSize().getWidth(), 22 * components.size()));
//    	setPreferredSize(new Dimension((int)scroll.getPreferredSize().getWidth(), 22 * components.size()));
//    	setSize(new Dimension((int)scroll.getPreferredSize().getWidth(), 22 * components.size()));

        this.pack();
        this.setInvoker(invoker);
        if (sizescroll != 0) {
            // Set popup size only if scrollbar is visible
            this.setPopupSize(new Dimension(scroll.getPreferredSize().width + 20, scroll.getMaximumSize().height - 20));
        }
        // this.setMaximumSize(scroll.getMaximumSize());
        Point invokerOrigin = invoker.getLocationOnScreen();
        this.setLocation((int) invokerOrigin.getX() + x, (int) invokerOrigin.getY() + y);
        this.setVisible(true);

        showMenu.put(this, Boolean.TRUE);

        // requestDefaultFocus();
        // requestFocus();
        // requestFocusInWindow();
    }

    public void add(AbstractButton menuItem) {
        // menuItem.setMargin(new Insets(0, 20, 0 , 0));
        if (menuItem == null) {
            return;
        }
        synchronized (components) {
            panelMenus.add(menuItem);
            components.add(menuItem);
        }

        menuItem.addMouseListener(this);
        menuItem.addMouseMotionListener(this);

        pack();
    }

    public void pack() {
        
    	
//    	scroll.setSize(10, 10);
    	
        if (this.panelMenus != null) {
            panelMenus.validate();
        }
        
        if(this.scroll != null) {
        	this.scroll.validate();
        }
        validate();
        super.pack();
    }
    
    public List<Component> getComponentList() {
        return Collections.unmodifiableList(components);
    }

    public void add(JPanel p) {
        synchronized (components) {
            panelMenus.add(p);
            components.add(p);
        }

        p.addMouseListener(this);
        p.addMouseMotionListener(this);

        pack();
    }

    // overrides... from Container
    public void remove(Component comp) {
        if (components == null || comp == null)
            return;
        if (!components.contains(comp))
            return;
        components.remove(comp);
        panelMenus.remove(comp);
        comp.removeMouseListener(this);
        comp.removeMouseMotionListener(this);
        pack();
    }

    public void remove(int pos) {
        synchronized (components) {
            if (components.size() <= pos)
                return;
            final Component c = components.get(pos);
            components.remove(pos);
            panelMenus.remove(pos);
            c.removeMouseListener(this);
            c.removeMouseMotionListener(this);
        }
        pack();
    }

    public JMenuItem add(JMenuItem menuItem) {
        add((AbstractButton) menuItem);
        return menuItem;
    }

    public void addSeparator() {
        XSeperator x = new XSeperator();
        panelMenus.add(x);
        components.add(x);
        x.addMouseListener(this);
        x.addMouseMotionListener(this);
        pack();
    }

    public void removeAll() {
        Component c[] = null;
        synchronized (components) {
            c = panelMenus.getComponents();
        }
        if (c != null && c.length != 0) {
            for (int i = 0; i < c.length; i++) {
                if (c[i] instanceof JMenuItem) {
                    JMenuItem ji = (JMenuItem) c[i];
                    ActionListener al[] = ji.getActionListeners();
                    if (al != null && al.length != 0)
                        for (int j = 0; j < al.length; j++)
                            ji.removeActionListener(al[j]);
                }
            }
        }
        synchronized (components) {
            panelMenus.removeAll();
            for (int i = 0; i < components.size(); i++) {
                final Component cc = components.get(i);
                cc.removeMouseListener(this);
                cc.removeMouseMotionListener(this);
            }
            components.clear();
        }
        validate();
        pack();
    }

    public Component[] getComponents() {
        synchronized (components) {
            return panelMenus.getComponents();
        }
    }

    private static class XSeperator extends JSeparator {

        /**
         * 
         */
        private static final long serialVersionUID = 2455262445058654253L;

        XSeperator() {
            ComponentUI ui = XBasicSeparatorUI.createUI(this);
            XSeperator.this.setUI(ui);
        }

        private static class XBasicSeparatorUI extends BasicSeparatorUI {

            public static ComponentUI createUI(JComponent c) {
                return new XBasicSeparatorUI();
            }

            public void paint(Graphics g, JComponent c) {
                Dimension s = c.getSize();

                if (((JSeparator) c).getOrientation() == JSeparator.VERTICAL) {
                    g.setColor(c.getForeground());
                    g.drawLine(0, 0, 0, s.height);

                    g.setColor(c.getBackground());
                    g.drawLine(1, 0, 1, s.height);
                } else // HORIZONTAL
                {
                    g.setColor(c.getForeground());
                    g.drawLine(0, 7, s.width, 7);

                    g.setColor(c.getBackground());
                    g.drawLine(0, 8, s.width, 8);
                }
            }
        }
    }

    public void hidemenu() {

        final JPopupMenu _menu = this;

        showMenu.put(this, Boolean.FALSE);

        new Timer().schedule(new TimerTask() {

            public void run() {
                if (showMenu.get(_menu))
                    return;
                if (_menu.isVisible()) {
                    _menu.setVisible(false);
                }
            }
        }, 200);

    }

    public void menuSelectionChanged(boolean b) {
    }

    public void popupMenuCanceled(PopupMenuEvent arg0) {
    }

    public void popupMenuWillBecomeInvisible(PopupMenuEvent arg0) {
        // try {
        // throw new Exception();
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // System.out.println("popupMenuWillBecomeInvisible");
    }

    public void popupMenuWillBecomeVisible(PopupMenuEvent arg0) {
        // System.out.println("popupMenuWillBecomeVisible");
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
        // System.out.println("entered");
        showMenu.put(this, Boolean.TRUE);
    }

    public void mouseExited(MouseEvent e) {
        // System.out.println("exited");
    	
    	Rectangle2D r = new Rectangle2D.Double();
    	r.setRect(scroll.getLocationOnScreen().getX(), scroll.getLocationOnScreen().getY(), scroll.getWidth(), scroll.getHeight());
    	
    	if (r.contains(e.getXOnScreen(), e.getYOnScreen()))
    		return;
   
        hidemenu();
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
        // System.out.println("moved");
        showMenu.put(this, Boolean.TRUE);
    }

}
