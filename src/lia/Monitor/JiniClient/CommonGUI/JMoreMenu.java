package lia.Monitor.JiniClient.CommonGUI;

/**
 * taken from JFreeChart.JMoreMenu
 */
import java.awt.Component;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

public class JMoreMenu extends JMenu {
    
    JMoreMenu moreMenu = null;
    
    protected int currentPoz = 0;
    
    protected final int maxPoz = 25;
    
    public JMoreMenu(String menuString) {
        super(menuString);
        currentPoz = 0;
        moreMenu = null;
    }
    
    public JMenuItem add( JMenuItem menuItem) {
        return (JMenuItem)this.add( (Component)menuItem);
    }
    
    /**
     * Appends a menu item to the end of this menu. 
     * Returns the menu item added.
     *
     * @param menuItem the <code>JMenuitem</code> to be added
     * @return the <code>JMenuItem</code> added
     */
    public Component add( Component menuItem) {
        
        if(moreMenu == null && currentPoz >= maxPoz){
//          System.out.println("creating more... at "+menuItem.getText());
            moreMenu = new JMoreMenu("More ...");
            super.add(moreMenu);
        }
        
        if(moreMenu != null){
//          System.out.println("passing to next");
            return moreMenu.add(menuItem);
        }
        
        currentPoz++;
//      System.out.println("creating here "+menuItem.getText());
        return super.add(menuItem);
    }

    /** 
     * Returns the <code>JMenuItem</code> at the specified position.
     * If the component at <code>pos</code> is not a menu item,
     * <code>null</code> is returned.
     * This method is included for AWT compatibility.
     *
     * @param pos    an integer specifying the position
     * @exception   IllegalArgumentException if the value of 
     *                       <code>pos</code> < 0
     * @return  the menu item at the specified position; or <code>null</code>
     *      if the item as the specified position is not a menu item
     */
    public JMenuItem getItem(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException("index less than zero.");
        }
        if ( pos<maxPoz ) {
            Component c = getMenuComponent(pos);
            if (c instanceof JMenuItem) {
                JMenuItem mi = (JMenuItem) c;
                return mi;
            }
            return null;
        }
        if(moreMenu != null){
            return moreMenu.getItem( pos-maxPoz);
        }
        return null;
    }
    

    /**
     * Returns the number of items on the menu, including separators.
     * This method is included for AWT compatibility.
     *
     * @return an integer equal to the number of items on the menu
     * @see #getMenuComponentCount
     */
    public int getItemCount() {
        return currentPoz+(moreMenu!=null?moreMenu.getItemCount():0);
    }
    
    /**
     * Appends a menu item at the specified index. 
     * Returns the menu item added.
     *
     * @param menuItem the <code>JMenuitem</code> to be added
     * @return the <code>JMenuItem</code> added
     */
    public Component add(Component menuItem, int index) {
        if(moreMenu == null && currentPoz >= maxPoz){
//          System.out.println("creating more... at "+menuItem.getText());
            moreMenu = new JMoreMenu("More ...");
            super.add(moreMenu);
        }
        Component moreMenuItem = null;
        if(moreMenu != null){
            int new_index;
            if ( index<maxPoz ) {
                //element should be inserted in this menu, so last element has to 
                //be moved to next menu
                moreMenuItem = getMenuComponent(maxPoz-1);
                super.remove(moreMenuItem);
                currentPoz--;
                new_index = 0;
            } else {
                new_index = index-maxPoz;
                moreMenuItem = menuItem;
                index = -1;
            }
//          System.out.println("passing to next");
            moreMenuItem = moreMenu.add( moreMenuItem, new_index);
        }
        if ( index>=0 ) {
            currentPoz++;
            return super.add( menuItem, index);
        };
//      System.out.println("creating here "+menuItem.getText());
        return moreMenuItem;
    }
    
    public void removeAll(){
//      System.out.println("removingAll..");
        currentPoz = 0;
        moreMenu = null;
        super.removeAll();
    }
    
    public int removeLast(JMenuItem item){
        if(moreMenu != null){
            int left = moreMenu.removeLast(item);
            if(left == 0){
                super.remove(moreMenu);
                moreMenu = null;
//              currentPoz--; // ??!?
            }
        }else{
            currentPoz--;
            super.remove(item);
        }
        return currentPoz;
    }
    
    public void remove(JMenuItem item){
        
        boolean found = false;
        for(int i=0; i<super.getMenuComponentCount(); i++){
        	if ( super.getMenuComponent(i) instanceof JMenuItem ) {
	            JMenuItem aItem = (JMenuItem) super.getMenuComponent(i);
	            if(aItem.equals(item)){
	                found = true;
	                break;
	            }
        	};
        }
        if(found){
            super.remove(item);
            if(moreMenu != null){
                item = (JMenuItem) moreMenu.getMenuComponent(0);
                moreMenu.remove(item);
                super.remove(moreMenu);
                super.add(item);
                if(moreMenu.getMenuComponentCount() > 0)
                    super.add(moreMenu);
                else
                    moreMenu = null;
            }else{
                currentPoz--;
            }
        }else
            if(moreMenu != null)
                moreMenu.remove(item);
        
//      Component[] compoments = getComponents();
//      removeAll();
//      for (int i=0; i<compoments.length; i++)
//      if (!compoments[i].equals(item))
//      add(compoments[i]);
    }
    
}