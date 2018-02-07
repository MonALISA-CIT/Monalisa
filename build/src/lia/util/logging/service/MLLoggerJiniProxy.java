package lia.util.logging.service;

import java.io.Serializable;
import java.util.Date;

import lia.Monitor.monitor.MLLoggerSI;

public class MLLoggerJiniProxy implements Serializable, MLLoggerSI {
   
    /**
     * 
     */
    private static final long serialVersionUID = 8017941284662352876L;
    
    public Long rTime;
    public Date rDate;
}