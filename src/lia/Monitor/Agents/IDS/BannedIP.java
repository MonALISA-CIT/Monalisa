/*
 * Created on Jan 13, 2005
 *
 */
package lia.Monitor.Agents.IDS;

import java.util.Date;

import lia.util.ntp.NTPDate;

/**
 * @author adim
 * Jan 13, 2005
 * 
 */
public class BannedIP {

private String ip;
private long time;
private long interval;

protected final static int MAX_PERIOD = 24*60*60*1000; //24h

/**
 * @param time
 * @param ip
 */
public BannedIP(String ip, long time, long interval) {        
    this.time = time;
    this.ip = ip;
    this.interval = interval;
}
    
/**
 * @return Returns the ip.
 */
public String getIp() {
    return this.ip;
}
/**
 * @param ip The ip to set.
 */
public void setIp(String ip) {
    this.ip = ip;
}
/**
 * @return Returns the banned time.
 */
public long getTime() {
    return this.time;
}

public long getInterval(){
    return this.interval;
}
/**
 * @param time The time to set.
 */
public void setTime(long time) {
    this.time = time;
}

public synchronized void update(long time,long period) {
    this.time = time;
    this.interval=(period>MAX_PERIOD?MAX_PERIOD:period);
}

public synchronized boolean isExpired() {
    return (this.time + this.interval < NTPDate.currentTimeMillis());
}
public String toString() {
    return "[IP:"+this.ip+"/Time:"+(new Date(this.time)).toString()+"],for:"+(this.interval/1000)+" sec.";
}
}
