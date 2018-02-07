/*
 * Created on Mar 25, 2010
 */
package lia.net.topology.agents.conf;

import java.io.Serializable;

import lia.net.topology.Port.PortType;

/**
 * 
 * @author ramiro
 */
public final class AFOXRawPort implements Comparable<AFOXRawPort>, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -1067006622113832128L;
    
//    private static final TreeSet<AFOXRawPort> cache = new TreeSet<AFOXRawPort>();
    public final int portRow;
    public final int portColumn;
    public final PortType portType;
    
    private AFOXRawPort(int portRow, int portColumn, PortType portType) {
        this.portRow = portRow;
        this.portColumn = portColumn;
        this.portType = portType;
    }

    /**
     * in the form (row, column):PortType
     * @param port
     * @return
     */
    public static AFOXRawPort valueOf(String portSpecification) {
        
        final String ttp = portSpecification.trim();
        final String[] rawColPType = ttp.split(":");

        
        final String tp = rawColPType[0].trim().substring(1, rawColPType[0].trim().length() - 1);
        final String[] tpTKNS = tp.split("(\\s)*,(\\s)*");
        PortType ptype = PortType.INPUT_OUTPUT_PORT; 

        int row = -1;
        int col = -1;
        if(tpTKNS.length == 1) {
            //output port
            int outputPortNumber  = Integer.parseInt(tpTKNS[0].trim());
            row = outputPortNumber / 13 + 1;
            col = (outputPortNumber - (row-1) * 12) % 13;
            ptype = PortType.OUTPUT_PORT;
            if(rawColPType.length >= 2) {
                ptype = PortType.valueOf(rawColPType[1]);
            }
        } else if(tpTKNS.length == 2) {
            //input port
            row = Integer.parseInt(tpTKNS[0].trim());
            col = Integer.parseInt(tpTKNS[1].trim());
            ptype = PortType.INPUT_PORT;
            if(rawColPType.length >= 2) {
                ptype = PortType.valueOf(rawColPType[1]);
            }
        } else {
            throw new IllegalArgumentException(" Unable to decode AFOX port from " + tp);
        }
        
        if(ptype != PortType.INPUT_OUTPUT_PORT && row > 0 && col > 0) {
            return newInstance(row, col, ptype);
        }
        
        throw new IllegalArgumentException(" Unable to decode AFOX port from " + tp);

    }
    
    public static AFOXRawPort newInstance(int portRow, int portColumn, PortType portType) {
        return checkAndGet(new AFOXRawPort(portRow, portColumn, portType));
    }
    
    private static final AFOXRawPort checkAndGet(AFOXRawPort ap) {
//        final AFOXRawPort other = cache.co(ap);
//        if(other != null && other.equals(ap)) {
//            return other;
//        }
//        cache.add(ap);
        return ap;
    }
    
    @Override
    public boolean equals(Object o) {
        if(o == null) {
            return false;
        }
        if(o instanceof AFOXRawPort) {
            AFOXRawPort other = (AFOXRawPort)o;
            return (this.portRow == other.portRow && this.portColumn == other.portColumn && this.portType == other.portType); 
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.portRow + this.portColumn + portType.hashCode();
    }
    /**
     * 
     */
    public int compareTo(AFOXRawPort other) {
        final int diffPortType = this.portType.ordinal() - other.portType.ordinal();
        return (diffPortType < 0)? -1: (diffPortType > 0)? 1: 
            (this.portRow < other.portRow)? -1 : (this.portRow > other.portRow)? 1 :
                (this.portColumn < other.portColumn)? -1 : (this.portColumn > other.portColumn)? 1 :0
                ; 
    }

    @Override
    public String toString() {
        return getClass().getName() + "[(" + portRow  + "," + portColumn + "):" + portType + "]";
    }

    
}