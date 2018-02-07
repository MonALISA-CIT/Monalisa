/*
 * Created on Aug 24, 2010
 */
package lia.util.rrd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lia.Monitor.monitor.monPredicate;

import org.uslhcnet.rrd.config.DSConfig;
import org.uslhcnet.rrd.config.RRDConfig;

/**
 * 
 * @author ramiro
 */
public class MLRRDConfigEntry {
    
    final List<monPredicate> predicatesList;
    final Map<String, DSConfig> dsMap;
    final RRDConfig rrdConfig;
    
    public MLRRDConfigEntry(RRDConfig rrdConfig, List<monPredicate> predicatesList, Map<String, DSConfig> dsMap) {
        this.predicatesList = Collections.unmodifiableList(new ArrayList<monPredicate>(predicatesList));
        this.dsMap = Collections.unmodifiableMap(new HashMap<String, DSConfig>(dsMap));
        this.rrdConfig = rrdConfig;
    }

    
    public List<monPredicate> getPredicatesList() {
        return predicatesList;
    }

    public Map<String, DSConfig> dsMap() {
        return dsMap;
    }


    
    public RRDConfig getRrdConfig() {
        return rrdConfig;
    }


    @Override
    public String toString() {
        return "MLRRDConfigEntry [predicatesList=" + predicatesList + ", dsMap=" + dsMap + ", rrdConfig=" + rrdConfig + "]";
    }

}
