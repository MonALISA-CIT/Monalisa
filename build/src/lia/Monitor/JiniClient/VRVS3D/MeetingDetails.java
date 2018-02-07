/*
 * Created on Sep 14, 2010
 */
package lia.Monitor.JiniClient.VRVS3D;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JCheckBoxMenuItem;


public class MeetingDetails implements Comparable<MeetingDetails>{
    public static class IDColor {
        public final String ID;
        public final Color color;
        IDColor(final String ID, final Color color) {
            this.ID = ID;
            this.color = color;
        }
    }
    final IDColor idColor;
    final String name;
    volatile boolean status;
    final JCheckBoxMenuItem menuItem;
    
    private final AtomicReference<String> community = new AtomicReference<String>(null);
    
    /**
     * @param iD
     * @param name
     * @param c
     * @param status
     */
    public MeetingDetails(String ID, String name, Color color, boolean status, JCheckBoxMenuItem menuItem) {
        this.idColor = new IDColor(ID, color);
        this.name = name;
        this.status = status;
        this.menuItem = menuItem;
    }

    public boolean setCommunity(String community) {
        return this.community.compareAndSet(null, community);
    }
    
    public String getCommunity() {
        final String ret = community.get();
        return (ret == null)?"other":ret; 
    }

    public int compareTo(MeetingDetails o) {
        return this.name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return "MeetingDetails [ID=" + idColor.ID + ", name=" + name + ", status=" + status + ", community=" + community.get() + "]";
    }
    
}
