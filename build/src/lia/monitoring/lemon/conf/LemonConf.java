package lia.monitoring.lemon.conf;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class LemonConf implements Serializable{
    
    public HashMap hostsToClusterMap;
    public HashMap metricsIDToNameMap;
    public HashMap metricsNameToFieldsMap;

    public LemonConf(Map newHostsToClusterMap, Map newMetricsIDToNameMap, Map newMetricsNameToFieldsMap) {
        if (newHostsToClusterMap != null) {
            hostsToClusterMap = new HashMap(newHostsToClusterMap.size());
            hostsToClusterMap.putAll(newHostsToClusterMap);
        }
        
        if (newMetricsIDToNameMap != null) {
            metricsIDToNameMap = new HashMap(newMetricsIDToNameMap.size());
            metricsIDToNameMap.putAll(newMetricsIDToNameMap);
        }
        
        if (newMetricsNameToFieldsMap != null){
            metricsNameToFieldsMap = new HashMap(newMetricsNameToFieldsMap.size());
            metricsNameToFieldsMap.putAll(newMetricsNameToFieldsMap);
        }
    }
    
    private void appendHashMap(HashMap hash, StringBuilder sb){
        sb.append("\n[");
        if(hash != null) {
            sb.append(hash.toString());
        } else {
            sb.append("null]");
        }
        sb.append("\n");
        
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("hostsToClusterMap:");
        appendHashMap(hostsToClusterMap, sb);
        sb.append("metricsIDToNameMap:");
        appendHashMap(metricsIDToNameMap, sb);
        sb.append("metricsNameToFieldsMap:");
        appendHashMap(metricsNameToFieldsMap, sb);
        return sb.toString();
    }
}
