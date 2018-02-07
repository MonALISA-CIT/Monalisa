/*
 * Created on Aug 23, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.uslhcnet.rrd.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DSTemplates {
    
    private final List<DSConfig> dsTemplates;
    
    public DSTemplates(Properties p) {
        final String templatesCount = p.getProperty("ds.templates");
        if(templatesCount == null) {
            throw new IllegalArgumentException("unable to load ds.templates property");
        }
        
        int count = 0;
        try {
            count = Integer.valueOf(templatesCount);
        }catch(Throwable t) {
            throw new IllegalArgumentException("Unable to load number of templates from ds.templates property. Cause:", t);
        }
        
        if(count <= 0) {
            throw new IllegalArgumentException("Illegal value for ds.templates property. It should be a positive integer: " + templatesCount);
        }
        
        List<DSConfig> dsTemplatesTmp = new ArrayList<DSConfig>(count);
        
        for(int i=1; i<=count; i++) {
            final String dsName = getPropChecked("ds." + i + ".name", p);
            final String dsTypeProp = getPropChecked("ds." + i + ".type", p);
            if(dsTypeProp == null) {
                throw new IllegalArgumentException("Unable to parse DataSource type. ds." + i + ".type not defined?");
            }
            
            DSType dsType = null;
            try {
                dsType = DSType.valueOf(dsTypeProp);
            }catch(Throwable t) {
                throw new IllegalArgumentException("Unable to parse DataSource type: " + dsTypeProp +". Cause:", t);
            }
            
            if(dsType == null) {
                throw new IllegalArgumentException("Unable to parse DataSource type: " + dsTypeProp);
            }
            
            final String dsMin = getPropChecked("ds." + i + ".min", p);
            final String dsMax = getPropChecked("ds." + i + ".max", p);
            final String dsHeartbeat = getPropChecked("ds." + i + ".heartbeat", p);
            
            dsTemplatesTmp.add(new DSConfig(i, dsType, dsName, dsHeartbeat, dsMin, dsMax));
        }
        this.dsTemplates = Collections.unmodifiableList(dsTemplatesTmp);
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

    public DSConfig getDSConfig(int configNo) {
        return dsTemplates.get(configNo - 1);
    }
    
    public int templatesCount() {
        return dsTemplates.size();
    }
    
    public List<DSConfig> templates() {
        return dsTemplates;
    }

    @Override
    public String toString() {
        return "DSTemplates [dsTemplates=" + dsTemplates + "]";
    }

}
