package lia.monitoring.lemon.conf;

import java.io.Serializable;

public class LemonMetricFields implements Serializable {
    
    /**
     * 
     */
    private static final long serialVersionUID = -9202482874392893713L;
    public static final short NOSUCHTYPE	=	-1;
    public static final short INTEGER		=	0;
    public static final short FLOAT			=	1;
    public static final short CHAR			=	2;
    
    
    public final String metricClass;
    public String[] fieldNames;
    public short[] fieldTypes;
    
    public LemonMetricFields(String metricClass) {
        this.metricClass = metricClass;
    }
    
    public void addField(int fieldIndex, String fieldName, String sFieldType) {
        addField(fieldIndex, fieldName, getShortType(sFieldType));
    }
        
    public void addField(int fieldIndex, String fieldName, short fieldType) {
        if(fieldNames == null){
            fieldNames = new String[fieldIndex];
            fieldTypes = new short[fieldIndex];
        }
        
        if (fieldNames.length >= fieldIndex) {
            fieldNames[fieldIndex - 1 ] = fieldName;
            fieldTypes[fieldIndex - 1 ] = fieldType;
            return;
        }
        
        String _fieldNames[] = new String[fieldIndex];
        System.arraycopy(fieldNames,0, _fieldNames, 0, fieldNames.length);
        short _fieldTypes[] = new short[fieldIndex];
        System.arraycopy(fieldTypes,0, _fieldTypes, 0, fieldTypes.length);
        
        _fieldNames[fieldIndex - 1 ] = fieldName;
        _fieldTypes[fieldIndex - 1 ] = fieldType;
        
        fieldNames = _fieldNames;
        fieldTypes = _fieldTypes;
    }
    
    public String toString(){
        String retV = "MetricClass: " + ((metricClass==null)?"null":metricClass) + "\nFields: ";
        if (fieldNames == null || fieldNames.length ==0) {
            return retV + " null";
        }
        
        for( int i = 0; i < fieldNames.length; i++) {
            retV += "\n [ " + i + " ] = " + fieldNames[i] + " Type: " + getStringType(fieldTypes[i]);
        }
        return retV;
    }
    
    private String getStringType(short type) {
        switch(type) {
	    	case INTEGER: return "INTEGER";
	    	case CHAR: return "CHAR";
	    	case FLOAT: return "FLOAT";
	    	default: return " No such type [ " + type + " ] "; 
        }
    }

    private short getShortType(String sType) {
        if (sType.compareToIgnoreCase("INTEGER") == 0) return INTEGER;
        if (sType.compareToIgnoreCase("CHAR") == 0) return CHAR;
        if (sType.compareToIgnoreCase("FLOAT") == 0) return FLOAT;
        return NOSUCHTYPE;
    }

    public boolean equals(Object o){
        
        if(o instanceof LemonMetricFields) {
            LemonMetricFields lmf = (LemonMetricFields)o;
            if(this.metricClass != null && lmf.metricClass != null ) {
                if(this.metricClass.equals(lmf.metricClass)) {
                    if(fieldNames != null && lmf.fieldNames != null && fieldNames.length == lmf.fieldNames.length ) {
                        if(fieldTypes != null && lmf.fieldTypes != null && fieldTypes.length == lmf.fieldTypes.length) {
                            if(fieldNames.length == fieldTypes.length) {
                                for(int i = 0; i < fieldNames.length; i++) {
                                    if( !fieldNames[i].equals(lmf.fieldNames[i]) || fieldTypes[i] != lmf.fieldTypes[i]) {
                                        return false;
                                    }
                                }//end for
                                return true;
                            }
                        }
                    } else {
                        if(fieldNames == null && lmf.fieldNames == null){
                            return true;
                        }
                    }
                }
            } else {
                if ( this.metricClass == null && lmf.metricClass == null ) {
                    return true;
                }
            }
        } 
        return false;
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return metricClass.hashCode();
    }
}
