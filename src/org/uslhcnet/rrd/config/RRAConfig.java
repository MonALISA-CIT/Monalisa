/*
 * Created on Aug 19, 2010 
 */
package org.uslhcnet.rrd.config;

import java.util.EnumSet;

/**
 * @author ramiro
 */
public class RRAConfig {

    private final int rraIndex;
    
    private final String rraName;

    private final String XFactor;

    private final String steps;

    private final String rows;

    private final EnumSet<ConsolidationFunction> cfSet;

    /**
     * @param rraName
     * @param xFactor
     * @param steps
     * @param rows
     * @param cfSet
     */
    public RRAConfig(int rraIndex, String rraName, String xFactor, String steps, String rows, EnumSet<ConsolidationFunction> cfSet) {
        if (rraName == null) {
            throw new NullPointerException("rraName cannot be null");
        }
        this.rraIndex = rraIndex;
        this.rraName = rraName;
        XFactor = xFactor;
        this.steps = steps;
        this.rows = rows;
        this.cfSet = cfSet;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((rraName == null) ? 0 : rraName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return rraName.equals(((RRAConfig) obj).rraName);
    }

    
    public String getRraName() {
        return rraName;
    }

    
    public String getXFactor() {
        return XFactor;
    }

    
    public String getSteps() {
        return steps;
    }

    
    public String getRows() {
        return rows;
    }

    
    public EnumSet<ConsolidationFunction> getCfSet() {
        return cfSet;
    }

    @Override
    public String toString() {
        return "RRAConfig [rraIndex=" + rraIndex +", rraName=" + rraName + ", XFactor=" + XFactor + ", steps=" + steps + ", rows=" + rows + ", cfSet=" + cfSet + "]";
    }

}
