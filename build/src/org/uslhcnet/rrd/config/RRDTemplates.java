/*
 * Created on Aug 23, 2010
 */
package org.uslhcnet.rrd.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * @author ramiro
 */
public class RRDTemplates {

    private final List<RRDConfig> rrdTemplates;

    RRDTemplates(Properties p, RRATemplates rraTemplates, DSTemplates dsTemplates) {
        final String templatesCount = p.getProperty("rrd.templates");
        if (templatesCount == null) {
            throw new IllegalArgumentException("unable to load rra.templates property");
        }

        int count = 0;
        try {
            count = Integer.valueOf(templatesCount);
        } catch (Throwable t) {
            throw new IllegalArgumentException("Unable to load number of templates from rra.templates property. Cause:", t);
        }

        if (count <= 0) {
            throw new IllegalArgumentException("Illegal value for rra.templates property. It should be a positive integer: " + templatesCount);
        }

        List<RRDConfig> rrdTemplatesTmp = new ArrayList<RRDConfig>(count);

        for (int i = 1; i <= count; i++) {
            final String rrdName = getPropChecked("rrd." + i + ".name", p);
            final String rrdStep = getPropChecked("rrd." + i + ".step", p);
            final String rrdDSProp = getPropChecked("rrd." + i + ".ds", p);
            final String rrdRRAProp = getPropChecked("rrd." + i + ".rra", p);

            if (rrdDSProp == null) {
                throw new IllegalArgumentException("Null property for rrd." + i + ".ds");
            }

            if (rrdRRAProp == null) {
                throw new IllegalArgumentException("Null property for rrd." + i + ".rra");
            }

            final String[] rrdDSTkns = rrdDSProp.trim().split("(\\s)*,(\\s)*");
            final String[] rrdRRATkns = rrdRRAProp.trim().split("(\\s)*,(\\s)*");

            if (rrdDSTkns == null || rrdDSTkns.length == 0) {
                throw new IllegalArgumentException("No valid rrd." + i + ".ds defined. length == 0");
            }

            if (rrdRRATkns == null || rrdRRATkns.length == 0) {
                throw new IllegalArgumentException("No valid rrd." + i + ".rra defined. length == 0");
            }

            final List<RRAConfig> rraConfigs = new LinkedList<RRAConfig>();
            for (final String rraIdx : rrdRRATkns) {
                try {
                    final int idx = Integer.valueOf(rraIdx);
                    rraConfigs.add(rraTemplates.getRRAConfig(idx));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("Unable to get rraTemplate for index: " + rraIdx + ". Cause: ", t);
                }
            }

            if (rraConfigs.size() == 0) {
                throw new IllegalArgumentException("No valid rras defined for RRD. rraConfig size is 0");
            }

            final List<DSConfig> dsConfigs = new LinkedList<DSConfig>();
            for (final String dsIdx : rrdDSTkns) {
                try {
                    final int idx = Integer.valueOf(dsIdx);
                    dsConfigs.add(dsTemplates.getDSConfig(idx));
                } catch (Throwable t) {
                    throw new IllegalArgumentException("Unable to get rraTemplate for index: " + dsIdx + ". Cause: ", t);
                }
            }

            if (dsConfigs.size() == 0) {
                throw new IllegalArgumentException("No valid DS defined for RRD. dsConfig size is 0");
            }

            rrdTemplatesTmp.add(new RRDConfig(i, rrdName, rrdStep, dsConfigs, rraConfigs));
        }

        this.rrdTemplates = Collections.unmodifiableList(rrdTemplatesTmp);
    }

    private final String getPropChecked(final String propertyName, Properties p) {
        final String propValue = p.getProperty(propertyName);
        if (propValue == null) {
            throw new IllegalArgumentException(propertyName + " not defined");
        }
        final String propTrimmed = propValue.trim();
        if (propTrimmed.length() <= 0) {
            throw new IllegalArgumentException(propertyName + " illegal value=" + propValue + "; trimmed=" + propTrimmed);
        }

        return propTrimmed;
    }

    public RRDConfig getRRDConfig(int configNo) {
        return rrdTemplates.get(configNo - 1);
    }

    public int templatesCount() {
        return rrdTemplates.size();
    }

    public List<RRDConfig> templates() {
        return rrdTemplates;
    }

    @Override
    public String toString() {
        return "RRDTemplates [rrdTemplates=" + rrdTemplates + "]";
    }

}
