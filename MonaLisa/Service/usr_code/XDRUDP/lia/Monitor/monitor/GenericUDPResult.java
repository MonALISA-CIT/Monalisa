package lia.Monitor.monitor;

import java.util.Date;
import java.util.Hashtable;

public class GenericUDPResult {

    public long rtime;

    public String clusterName;

    public String nodeName;

    public Hashtable paramValues;
    
    //this was added to keep track of params order ...
    public String paramNames[];
    
    public GenericUDPResult() {
        paramValues = new Hashtable();
    }
    
    public void addParam(String name, Object value) {
        if (name == null || value == null) return;
        if (name.trim().length() == 0) return;
        if (paramNames == null) {
            synchronized(paramValues) {
                paramNames = new String[1];
                paramNames[0] = name;
                paramValues.put(name, value);
            }
            return;
        }
        synchronized(paramValues) {
            String[] paramNamesTemp = new String[paramNames.length+1];
            System.arraycopy(paramNames, 0, paramNamesTemp, 0, paramNames.length);
            paramNamesTemp[paramNames.length] = name;
            paramValues.put(name, value);
            paramNames = paramNamesTemp;
        }
    }
    
    //Just for Debugging 
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(" [ " + new Date(rtime) + " ] ");
        sb.append(clusterName + "\t");
        sb.append(nodeName + "\t");
        if (paramValues != null && paramValues.size() > 0) {
            for( int i = 0; i< paramNames.length; i++) {
                String paramName = paramNames[i];
                Object value = paramValues.get(paramName);
                sb.append(paramName + " = " + value + "\t");
            }
        } else {
            sb.append(" No PARAMS!");
        }
        return sb.toString();
    }
}