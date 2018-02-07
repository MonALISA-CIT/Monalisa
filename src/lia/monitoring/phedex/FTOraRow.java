package lia.monitoring.phedex;
//package cms;

import java.util.Date;
import java.util.HashMap;

public class FTOraRow {
    
    public String node;
    public long time;
    /**
     * column name - value
     */
    public HashMap values; 
    
    public FTOraRow() {
        values = new HashMap();
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("FTOraRow: ----> [ ");
        sb.append((node != null)?node:"null ");
        sb.append(", ");
        sb.append(new Date(time).toString());
        sb.append(", ");
        if (values ==null) {
            sb.append("null ]");
        } else {
            sb.append("\n{ ");
            sb.append(values.toString());
            sb.append(" } ]\n");
        }
        
        return sb.toString();
    }
}
