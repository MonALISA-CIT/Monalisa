/*
 * Created on Aug 23, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.uslhcnet.rrd.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

/**
 * 
 * @author ramiro
 */
public class RRATemplates {

    private final List<RRAConfig> rraTemplates;
    
    public RRATemplates(Properties p) {
        
        final String templatesCount = p.getProperty("rra.templates");
        if(templatesCount == null) {
            throw new IllegalArgumentException("unable to load rra.templates property");
        }
        
        int count = 0;
        try {
            count = Integer.valueOf(templatesCount);
        }catch(Throwable t) {
            throw new IllegalArgumentException("Unable to load number of templates from rra.templates property. Cause:", t);
        }
        
        if(count <= 0) {
            throw new IllegalArgumentException("Illegal value for rra.templates property. It should be a positive integer: " + templatesCount);
        }
        
        List<RRAConfig> rraTemplatesTmp = new ArrayList<RRAConfig>(count);
        
        for(int i=1; i<=count; i++) {
            final String rraName = getPropChecked("rra." + i + ".name", p);
            final String rraXFactor = getPropChecked("rra." + i + ".xFactor", p);
            final String rraSteps = getPropChecked("rra." + i + ".steps", p);
            final String rraRows = getPropChecked("rra." + i + ".rows", p);
            final String rraCF = getPropChecked("rra." + i + ".CF", p);
            EnumSet<ConsolidationFunction> consolidationFunctions = EnumSet.noneOf(ConsolidationFunction.class);
            try {
                final String[] cfSplit = rraCF.split("(\\s)*,(\\s)*");
                if(cfSplit == null || cfSplit.length == 0) {
                    throw new IllegalArgumentException("rra." + i + ".CF does not specify any CF: " + rraCF);
                }
                for(final String cfProp: cfSplit) {
                    consolidationFunctions.add(ConsolidationFunction.valueOf(cfProp));
                }
            }catch(Throwable t) {
                throw new IllegalArgumentException("Unable to parse rra." + i + ".CF. Cause: ", t);
            }
            
            if(consolidationFunctions.isEmpty()) {
                throw new IllegalArgumentException("Unable to parse consolidation functions. Set is empty after parsing for: " + "rra." + i + ".CF");
            }
            
            rraTemplatesTmp.add(new RRAConfig(i, rraName, rraXFactor, rraSteps, rraRows, consolidationFunctions));
        }
        
        this.rraTemplates = Collections.unmodifiableList(rraTemplatesTmp);
    }
    
    private final String getPropChecked(final String propertyName, Properties p) {
        final String propValue = p.getProperty(propertyName);
        if(propValue == null) {
            throw new IllegalArgumentException(propertyName + " not defined");
        }
        final String propTrimmed = propValue.trim();
        if(propTrimmed.length() <= 0) {
            throw new IllegalArgumentException(propertyName +" illegal value=" + propValue + "; trimmed=" + propTrimmed);
        }
        
        return propTrimmed;
    }

    public RRAConfig getRRAConfig(int configNo) {
        return rraTemplates.get(configNo - 1);
    }
    
    public int templatesCount() {
        return rraTemplates.size();
    }
    
    public List<RRAConfig> templates() {
        return rraTemplates;
    }
    
    @Override
    public String toString() {
        return "RRATemplates [rraTemplates=" + rraTemplates + "]";
    }
    
}
