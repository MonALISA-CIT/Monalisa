/*
 * Created on Aug 20, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.uslhcnet.rrd.config;

/**
 * 
 * @author ramiro
 */
public class DSConfig {
    
    private final int dsIndex;
    private final String dsName;
    private final DSType dsType;
    
    private final String heartBeat;
    private final String minVal;
    private final String maxVal;

    
    /**
     * @param dsType
     * @param dsName
     * @param heartBeat
     * @param minVal
     * @param maxVal
     */
    public DSConfig(int dsIndex, DSType dsType, String dsName, String heartBeat, String minVal, String maxVal) {
        this.dsType = dsType;
        this.dsIndex = dsIndex;
        this.dsName = dsName;
        this.heartBeat = heartBeat;
        this.minVal = minVal;
        this.maxVal = maxVal;
    }


    
    public String name() {
        return dsName;
    }


    
    public DSType type() {
        return dsType;
    }


    
    public String heartBeat() {
        return heartBeat;
    }


    
    public String minVal() {
        return minVal;
    }


    
    public String maxVal() {
        return maxVal;
    }

    public int getIndex() {
        return dsIndex;
    }

    @Override
    public String toString() {
        return "DSConfig [dsIndex=" + dsIndex + ", dsName=" + dsName + ", dsType=" + dsType + ", heartBeat=" + heartBeat + ", minVal=" + minVal + ", maxVal=" + maxVal + "]";
    }


}
