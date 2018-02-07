/*
 * Created on Aug 23, 2010
 */
package org.uslhcnet.rrd.config;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 * @author ramiro
 */
public class RRDConfig {

    private final int index;
    private final String name;
    private final String step;
    
    private final List<DSConfig> dataSources;
    private final List<RRAConfig> rraConfig;

    public RRDConfig(int index, String name, String step, List<DSConfig> dataSources, List<RRAConfig> rraConfig) {

        this.index = index;
        
        if(name == null) {
            throw new NullPointerException("Null RRDConfig name");
        }
        this.name = name;

        if(step == null) {
            throw new NullPointerException("Null RRDConfig step");
        }
        this.step = step;
        
        if(dataSources == null) {
            throw new NullPointerException("Null DS!");
        }
        
        if(dataSources.size() == 0) {
            throw new IllegalArgumentException("Data sources cannot be empty");
        }
        this.dataSources = Collections.unmodifiableList(new LinkedList<DSConfig>(dataSources));
    
        if(rraConfig == null) {
            throw new NullPointerException("Null rraConfig");
        }
        
        if(rraConfig.size() == 0) {
            throw new IllegalArgumentException("rraConfig cannot be empty");
        }
        this.rraConfig = new LinkedList<RRAConfig>(rraConfig);
    }

    public String getRRDCreateCommand(final String rrdFileName) {
        //build the rrd command
        StringBuilder sb = new StringBuilder(" create ");
        sb.append(rrdFileName);
        for(final DSConfig ds: dataSources) {
            sb.append(" DS:").append(ds.name());
            sb.append(':').append(ds.type());
            sb.append(':').append(ds.heartBeat());
            sb.append(':').append(ds.minVal());
            sb.append(':').append(ds.maxVal());
        }
        
        for(RRAConfig rra: rraConfig) {
            final StringBuilder sbPost = new StringBuilder();
            sbPost.append(':').append(rra.getXFactor());
            sbPost.append(':').append(rra.getSteps());
            sbPost.append(':').append(rra.getRows());
            final String postStr = sbPost.toString();
            
            for(ConsolidationFunction cf: rra.getCfSet())
                sb.append(" RRA:").append(cf).append(postStr);
        }
        
        return sb.toString();
    }

    public List<DSConfig> getDataSources() {
        return dataSources;
    }
    
    @Override
    public String toString() {
        return "RRDConfig [index=" + index + ", name=" + name + ", step=" + step + ", dataSources=" + dataSources + ", rraConfig=" + rraConfig + "]";
    }

}
