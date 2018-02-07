package lia.monitoring.phedex;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Vector;

import lia.Monitor.monitor.Result;

public class MLPhedexTable {
    
    private long last_update;
    private String tableName;
    private String MLClusterName;
    private String[] parameters;
    private String[] keys;
    private String keySeparator;
    private String[] allParams;
    private String key;
    private double[] mFactors; 
    private boolean bRate;
    private HashMap lastValues;
    private String sqlConstraint;
    
    public MLPhedexTable(String tableName, String MLClusterName, 
            String[] parameters, String[] sFactors, String[] keys,
            String keySeparator, String sqlConstraint,
            boolean bRate) throws Exception
        {
        
        if(keys == null || keys.length == 0) {
            throw new Exception("At least one key must be defined");
        }

        if(parameters == null || parameters.length == 0) {
            throw new Exception("At least one parameter must be defined");
        }
        
        if(tableName == null || tableName.length() == 0) {
            throw new Exception("Incorect tableName");
        }
        
        if(MLClusterName == null || MLClusterName.length() == 0) {
            throw new Exception("Incorect MLClusterName");
        }

        this.sqlConstraint = sqlConstraint;
        this.bRate = bRate;
        this.last_update = 0;
        this.tableName = tableName;
        this.MLClusterName = MLClusterName;
        this.keys = keys;
        this.keySeparator = keySeparator;
        this.parameters = parameters;
        lastValues = null;
        
        key = "";
        
        for(int i=0; i<keys.length; i++) {
            key += keys[i];
            if(i < keys.length - 1) {
                if(keySeparator != null) {
                    key += keySeparator;
                }
            } else {
                break;
            }
        }
        lastValues = new HashMap();
        allParams = new String[parameters.length + keys.length + 1];
        System.arraycopy(parameters, 0, allParams, 0, parameters.length);
        System.arraycopy(keys, 0, allParams, parameters.length, keys.length);
        allParams[allParams.length - 1] = "TIMESTAMP";
        Vector vFact = new Vector();
        if(sFactors != null && sFactors.length > 0) {
            for(int i=0; i<sFactors.length; i++) {
                Double value = Double.valueOf(1);
                try {
                    value = Double.valueOf(sFactors[i]);
                }catch(Throwable t){
                    value = Double.valueOf(1);;
                }
                vFact.add(value);
            }
        }
        
        mFactors = new double[parameters.length];
        
        for(int i=0; i< parameters.length; i++) {
            if(i < vFact.size()) {
                mFactors[i] = ((Double)vFact.elementAt(i)).doubleValue();
            } else {
                mFactors[i] = 1;
            }
            
            System.out.println("Setting mFactor = " + mFactors[i] + " for " + parameters[i]);
        }
    }
    
    private boolean checkForNewResults(Connection conn) {
        try {
            HashMap hms[] = OraDBHelper.getValues(conn, tableName, new String[]{"MAX(TIMESTAMP)"});
            if(hms == null || hms.length ==0 ) return false;
            
            long cLastValue = ((BigDecimal)hms[0].get("MAX(TIMESTAMP)")).longValue();
            if(cLastValue != last_update) {
                last_update = cLastValue;
                return true;
            }
        }catch(Throwable t) {
            t.printStackTrace();
        }
        return false;
    }
    
    public Vector getData(Connection conn) {
        Vector retv = new Vector();
        
        if(checkForNewResults(conn)) {
            try {
                HashMap[] hms = OraDBHelper.getValues(conn, tableName, allParams, sqlConstraint);
                if(hms != null && hms.length>0) {
                    for(int i=0; i<hms.length; i++) {
                        HashMap hm = hms[i];
                        Result r = new Result();
                        r.ClusterName = MLClusterName;
//                        r.time = ((BigDecimal)hm.get("TIMESTAMP")).longValue()*1000;
                        r.time = last_update * 1000;
                        r.NodeName = "";
                        boolean keysOK = true;
                        for(int j=0; j<keys.length; j++) {
                            if(hm.get(keys[j]) == null || hm.get(keys[j]).toString().trim().length() == 0) {
                                keysOK = false;
                                break;
                            }
                            r.NodeName += hm.get(keys[j]);
                            if(j < keys.length - 1) {
                                r.NodeName += keySeparator;
                            }
                        }
                        
                        if(!keysOK) continue;
                        
                        for(int j=0; j<parameters.length; j++) {
                            double cVal = ((BigDecimal)hm.get(parameters[j])).doubleValue()*mFactors[j];
                            r.addSet(parameters[j], cVal);
                            if(bRate) {
                                HashMap lhm = (HashMap)lastValues.get(r.NodeName);
                                if(lhm != null){
                                    long lTime = ((BigDecimal)lhm.get("TIMESTAMP")).longValue()*1000;
                                    long dTime = r.time - lTime;
                                    if(dTime != 0) {
                                        double lVal = ((BigDecimal)lhm.get(parameters[j])).doubleValue()*mFactors[j];
                                        double rate = ((cVal - lVal)/dTime)*1000;//do it per second
                                        r.addSet("R-"+parameters[j], rate);
                                    } else {
                                        r.addSet("R-"+parameters[j], 0);
                                    }
                                }else{
                                    r.addSet("R-"+parameters[j], 0);
                                }
                            }
                        }
                        
                        lastValues.put(r.NodeName, hm);
                        retv.add(r);
                    }
                }
            }catch(Throwable t) {
                t.printStackTrace();
            }
        }
        
        return retv;
    }
    
    public String getKey() {
        return key;
    }
    
}
