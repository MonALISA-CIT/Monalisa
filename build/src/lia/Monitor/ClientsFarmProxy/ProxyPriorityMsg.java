/*
 * Created on Nov 6, 2005
 */ 
package lia.Monitor.ClientsFarmProxy;

import java.util.concurrent.atomic.AtomicLong;


class ProxyPriorityMsg implements Comparable<ProxyPriorityMsg> {

    //====== Priorities ========
    //default priority will be the lowest
    public static final int DEFAULT_PRIORITY = Integer.MAX_VALUE;
    
    //====== Services messages ========
    /**
     * Agent Message
     */
    public static final int ML_AGENT_MESSAGE = -1000;
    public static final int ML_PROXY_MESSAGE = Integer.MIN_VALUE;
    
    //====== Client messages ========
    
    //====== End Priorities ========
    
    private final int priority;
    private final Object msg;
    
    private static final AtomicLong SEQ = new AtomicLong(Long.MIN_VALUE);
    
    private long seq;
    
    ProxyPriorityMsg(Object msg, int priority) {
        if(msg == null) throw new NullPointerException(" The message cannot be null !");
        this.msg = msg;
        this.priority = priority;
        this.seq = SEQ.getAndIncrement();
    }
    
    public Object getMessage() {
        return msg;
    }
    
    public int compareTo(ProxyPriorityMsg ppm) {
        if(ppm == this) return 0;

        if(priority < ppm.priority) return -1;
        if(priority > ppm.priority) return 1;
        
        //same time?
        if(this.seq < ppm.seq) return -1;
        
        return 1;
    }
    
}
