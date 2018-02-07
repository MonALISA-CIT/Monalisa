package lia.monitoring.lemon.conf;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class MLLemonConf implements Serializable {
    
    private HashMap idPrefML;
    private HashMap paramRemapML;
    private LemonConf lemonConf;
    
    /**
     * 
     * @param idPrefML
     * @param lemonConf
     */
    public MLLemonConf(Map idPrefML, Map paramRemapML, LemonConf lemonConf) {
        
        //Just copy them as they are now. Do not make any presumtions that
        //they'll not be modified in the future
        if (idPrefML != null) {
            this.idPrefML = new HashMap(idPrefML.size());
            this.idPrefML.putAll(idPrefML);
        } else {
            this.idPrefML = null;
        }
        
        if (paramRemapML != null) {
            this.paramRemapML = new HashMap(paramRemapML.size());
            this.paramRemapML = new HashMap(paramRemapML);
        } else {
            this.paramRemapML = null;
        }

        if (lemonConf != null) {
            this.lemonConf = new LemonConf(lemonConf.hostsToClusterMap, lemonConf.metricsIDToNameMap, lemonConf.metricsNameToFieldsMap);
        } else {
            this.lemonConf = null;
        }
    }
    
    public HashMap getIDPrefML() {
        return idPrefML;
    }
    
    public HashMap getParamRemapML() {
        return paramRemapML;
    }

    public LemonConf getLemonConf() {
        return lemonConf;
    }
    
    /**
     * 
     * @param id
     * @return
     */
    public String getIDPrefix(String sID) {
        if(idPrefML == null) return null;
        return (String)idPrefML.get(sID);
    }
    
    public String getTranslatedParam(String lemonParam) {
        if (paramRemapML == null) return null;
        return (String)paramRemapML.get(lemonParam);
    }
    
    public LemonMetricFields getMetricFields(Integer id) {
        
        if (lemonConf == null || lemonConf.metricsIDToNameMap == null ) {
            return null;   
        }
        Object key = lemonConf.metricsIDToNameMap.get(id);
        
        if (key == null || lemonConf.metricsNameToFieldsMap == null ) {
            return null;
        }
        return (LemonMetricFields)(lemonConf.metricsNameToFieldsMap.get(key));
    }
    
    public String getCluster(String node) {
        if (lemonConf == null || lemonConf.hostsToClusterMap == null || node == null) return null;
        return (String)(lemonConf.hostsToClusterMap.get(node));
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
        sb.append("idPrefML:");
        appendHashMap(idPrefML, sb);
        sb.append("paramRemapML:");
        appendHashMap(paramRemapML, sb);
        sb.append("LemonConf: \n[ " + ((lemonConf==null)?"null":lemonConf.toString())+" ]\n\nlemonConf ");
        return sb.toString();
    }    
}
