/*
 * $Id: ParsedCienaTl1Alarm.java 6865 2010-10-10 10:03:16Z ramiro $
 * 
 */
package lia.util.ciena;

/**
 *
 * Holds all the fields of a CD/CI alarm, including the entire line
 * 
 * "aid,aidType:aisnc,condType,[serviceEffect],date,time,[location],[direction]:[desc],[aidDetection]"
 * 
 * @author ramiro
 */
public class ParsedCienaTl1Alarm {

    public final String aid;
    public final String aidType;
    public final String aisnc;
    public final String condType;
    public final String serviceEffect;
    public final String dateTL1;
    public final String timeTL1;
    public final long date;
    public final String location;
    public final String direction;
    public final String desc;
    public final String aidDetection;
    public final String TL1Line;
    
    public ParsedCienaTl1Alarm(String aid,
            String aidType,
            String aisnc,
            String condType,
            String serviceEffect,
            String dateTL1,
            String timeTL1,
            long date,
            String location,
            String direction,
            String desc,
            String aidDetection,
            String TL1Line) {
        
        this.aid = aid;
        this.aidType = aidType;
        this.aisnc = aisnc;
        this.condType = condType;
        this.serviceEffect = serviceEffect;
        this.dateTL1 = dateTL1;
        this.timeTL1 = timeTL1;
        this.date = date;
        this.location = location;
        this.direction = direction;
        this.desc = desc;
        this.aidDetection = aidDetection;
        this.TL1Line = TL1Line;
    }

    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        
        if(o instanceof ParsedCienaTl1Alarm) {
            final ParsedCienaTl1Alarm al = (ParsedCienaTl1Alarm)o;
            return al.TL1Line.equals(TL1Line);
        }
        
        return false;
    }
    
    public int hashCode() {
        return TL1Line.hashCode();
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nAlmAllTL1Response: ").append(" aid: ").append(aid).append("; aidType: ").append(aidType);
        sb.append("; aisnc: ").append(aisnc).append("; condType: ").append(condType);
        sb.append("; serviceEffect: ").append(serviceEffect);
        sb.append("; dateTL1: ").append(dateTL1).append("; timeTL1: ").append(timeTL1);
        sb.append("; location: ").append(location).append("; direction: ").append(direction);
        sb.append("; desc: ").append(desc).append("; aidDetection: ").append(aidDetection);
        sb.append("\n TL1Line: ").append(TL1Line).append("\n");
        return sb.toString();
    }
}
