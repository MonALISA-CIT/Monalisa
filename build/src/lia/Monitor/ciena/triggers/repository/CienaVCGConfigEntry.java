/*
 * Created on Jan 14, 2010
 */
package lia.Monitor.ciena.triggers.repository;

import java.util.Arrays;
import java.util.Collection;

import lia.Monitor.monitor.monPredicate;
import lia.util.timestamp.TimestampableStateValue;

/**
 * 
 * 
 * @author ramiro
 * 
 */
class CienaVCGConfigEntry implements Comparable<CienaVCGConfigEntry>, StateProvider<State, CienaVCGMonitoringValue>{
    
    public static final class Builder {
        private final String entryName;
        private final String[] emailAddresses;
        
        private monPredicate mlPingPredicate;
        //default 20%
        private double mlPingThreshold = .2;
        private monPredicate provBWPredicate;
        private monPredicate operBWPredicate;
        //default 20%
        private double bwThreshold = .2;
        private monPredicate traffInPredicate;
        private monPredicate traffOutPredicate;
        //in Mbps
        private double traffThreshold = .005;

        public Builder(final String entryName, final String[] emailAddresses) {
            if(entryName == null) {
                throw new NullPointerException("Null key name");
            }
            
            final String trimmedEntryName = entryName.trim();
            if(trimmedEntryName.length() == 0) {
                throw new IllegalArgumentException("The entry name is blank");
            }
            
            if(emailAddresses == null) {
                throw new NullPointerException("Email addresses cannot be null");
            }
            
            if(emailAddresses.length == 0) {
                throw new IllegalArgumentException("Email addresses have no valid email address");
            }

            this.emailAddresses = Arrays.copyOf(emailAddresses, emailAddresses.length);
            
            if(this.emailAddresses.length == 0) {
                throw new IllegalArgumentException("Email addresses have no valid email address");
            }
            
            this.entryName = trimmedEntryName;
        }
        
        public Builder mlPingPredicate(monPredicate mlPingPredicate) {
            this.mlPingPredicate = mlPingPredicate;
            return this;
        }

        public Builder mlPingThreshold(double mlPingThreshold) {
            this.mlPingThreshold = mlPingThreshold;
            return this;
        }

        public Builder provBWPredicate(monPredicate provBWPredicate) {
            this.provBWPredicate = provBWPredicate;
            return this;
        }

        public Builder operBWPredicate(monPredicate operBWPredicate) {
            this.operBWPredicate = operBWPredicate;
            return this;
        }

        public Builder bwThreshold(double bwThreshold) {
            this.bwThreshold = bwThreshold;
            return this;
        }
        
        public Builder traffInPredicate(monPredicate traffInPredicate) {
            this.traffInPredicate = traffInPredicate;
            return this;
        }
        
        public Builder traffOutPredicate(monPredicate traffOutPredicate) {
            this.traffOutPredicate = traffOutPredicate;
            return this;
        }
        
        public Builder traffThreshold(double traffThreshold) {
            this.traffThreshold = traffThreshold;
            return this;
        }

        
        public final CienaVCGConfigEntry build() {
            return new CienaVCGConfigEntry(this);
        }
        
    }
    
    private final String entryName;
    private final String[] emailAddresses;
    private final monPredicate mlPingPredicate;
    private final boolean checkMLPing;
    private final double mlPingThreshold;
    private final monPredicate provBWPredicate;
    private final monPredicate operBWPredicate;
    private final double bwThreshold;
    private final boolean checkBW;
    private final monPredicate traffInPredicate;
    private final monPredicate traffOutPredicate;
    private final double traffThreshold;
    private final boolean checkTraffic;

    private CienaVCGConfigEntry(final Builder builder) {
        this.entryName = builder.entryName;
        this.emailAddresses = Arrays.copyOf(builder.emailAddresses, builder.emailAddresses.length);
        
        this.mlPingPredicate = builder.mlPingPredicate;
        this.mlPingThreshold = builder.mlPingThreshold;
        this.checkMLPing = (this.mlPingPredicate != null);
        
        this.provBWPredicate = builder.provBWPredicate;
        this.operBWPredicate = builder.operBWPredicate;
        this.bwThreshold = builder.bwThreshold;
        this.checkBW = (this.provBWPredicate != null && this.operBWPredicate != null);
        
        this.traffInPredicate = builder.traffInPredicate;
        this.traffOutPredicate = builder.traffOutPredicate;
        this.traffThreshold = builder.traffThreshold;
        this.checkTraffic = (this.traffInPredicate != null && this.traffOutPredicate != null);
    }
    
    public int compareTo(CienaVCGConfigEntry o) {
        return this.entryName.compareTo(o.entryName);
    }
    
    public boolean equals(Object otherEntry) {
        return this.entryName.equals(((CienaVCGConfigEntry)otherEntry).entryName);
    }
    
    public int hashCode() {
        return 16 + entryName.hashCode();
    }
    
    public monPredicate mlPingPredicate() {
        return mlPingPredicate;
    }
    
    public double mlPingThreshold() {
        return mlPingThreshold;
    }

    public boolean checkMLPing() {
        return checkMLPing;
    }
    
    public monPredicate provBWPredicate() {
        return provBWPredicate;
    }
    
    public monPredicate operBWPredicate() {
        return operBWPredicate;
    }
    
    public double bwThreshold() {
        return bwThreshold;
    }
    
    public boolean checkBW() {
        return checkBW;
    }
    
    public monPredicate traffInPredicate() {
        return traffInPredicate;
    }
    
    public monPredicate traddOutPredicate() {
        return traffOutPredicate;
    }
    
    public double traffThreshold() {
        return traffThreshold;
    }
    
    public boolean checkTraffic() {
        return checkTraffic;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CienaVCGConfigEntry=").append(entryName);
        sb.append("; emailAddresses=").append(Arrays.toString(emailAddresses));
        sb.append("; mlPingPredicate=").append(mlPingPredicate);
        sb.append("; mlPingThreshold=").append(mlPingThreshold);
        sb.append("; provBWPredicate=").append(provBWPredicate);
        sb.append("; operBWPredicate=").append(operBWPredicate);
        sb.append("; bwThreshold=").append(bwThreshold);
        sb.append("; traffInPredicate=").append(traffInPredicate);
        sb.append("; traffOutPredicate=").append(traffOutPredicate);
        sb.append("; traffThreshold=").append(traffThreshold);
        return sb.toString();
    }

    public State newState(State currentState, CienaVCGMonitoringValue currentValue, Collection<TimestampableStateValue<State, CienaVCGMonitoringValue>> lastValues) {
        // TODO Auto-generated method stub
        return null;
    }

    public int samplingSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int flipFlopTransitions() {
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean isFlipFlopTransition(State startState, State endState) {
        // TODO Auto-generated method stub
        return false;
    }
}