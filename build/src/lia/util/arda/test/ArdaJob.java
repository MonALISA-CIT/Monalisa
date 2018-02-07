package lia.util.arda.test;

import java.util.Date;
import java.util.Random;
import java.util.Vector;

import lia.util.ApMon.ApMon;
import lia.util.ntp.NTPDate;

public class ArdaJob extends Thread {
    
//    private static final double JOBSTATES[] = new double[]{0, 0.25, .5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.25, 2.5, 2.75, 3, 3.25, 3.5, 3.75, 4}; 
    private static final double JOBSTATES[] = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}; 
    
    private static final String APMON_DestAddrs[] = new String[]{"localhost"};
    private static final int APMON_DestPorts[] = new int[]{7884};
    
    private static Vector apMonDestAddrs;
    private static Vector apMonDestPorts;
    boolean firstTime;
    
    private boolean hasToRun;
    private short currentIndex;
    private Object syncCurrentIndex;
    ApMon apm;
    
    Random sleepGen;
    
    static {
        try {
            apMonDestAddrs = new Vector(APMON_DestAddrs.length);
            apMonDestPorts = new Vector(APMON_DestPorts.length);
            
            for (int i = 0; i < APMON_DestAddrs.length; i++) {
                apMonDestAddrs.add(APMON_DestAddrs[i]);
                apMonDestPorts.add(Integer.valueOf(APMON_DestPorts[i]));
            }
        }catch(Throwable t) {
            t.printStackTrace();
            apMonDestAddrs = null;
            apMonDestAddrs = null;
        }
    }
    
    public ArdaJob(String name) {
        super(name);
        currentIndex = 0;
        syncCurrentIndex = new Object();
        firstTime = true;
        try {
            apm = new ApMon(apMonDestAddrs, apMonDestPorts);

        }catch(Throwable t){
            t.printStackTrace();
            apm = null;
        }
        
        if (apm == null) {
            System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + ": [" + getName()+"] Cannot use apm == null");
        }
        sleepGen = new Random(NTPDate.currentTimeMillis());
        hasToRun = true;
    }
    
    private void sendState() {
        try {
            System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + " ["+getName()+"] Sending param ... [ " + "Arda_Sim_Test, " + getName() + ", State, " + getCurrentState()+" ]");
            apm.sendParameter("Arda_Jobs", getName(), "State", getCurrentState());
        }catch(Throwable t) {
            System.out.println(new Date()  + " / " + new Date(NTPDate.currentTimeMillis()) + " [" +getName() +"] Got exception sending state ");
            t.printStackTrace();
        }
    }
    
    public void run() {
        while(hasToRun) {
            try {
                try {
                    long jobTime = getJobTime(2*1000, 40*1000);
                    
                    long now = NTPDate.currentTimeMillis();
                    long finishTime = now + jobTime;
                    System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + " [" + getName() + "] newState ... finishTime [ " + new Date(finishTime) + " ] ");
                    int iSends = 0;
                    while(now < finishTime) {
                        try {
                            long sTime = 10*1000;
                            
                            if (iSends < 1) {
                                iSends++;
                                sendState();
                            }
                            
                            if (now + sTime > finishTime) {
                                sTime = finishTime - now + 1;
                            }
                            Thread.sleep(sTime);
                        }catch(Throwable t){
                            t.printStackTrace();
                        }
                        now = NTPDate.currentTimeMillis();
                    }
                }catch(Throwable t){
                    System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + ": ["+getName()+"] got exception while sleeping");
                    t.printStackTrace();
                }
                nextState();
                try {
                    Thread.sleep(10);
                }catch(Throwable t){}
                
            }catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    public boolean isStillRunning() {
        return hasToRun;
    }
    
    public double getCurrentState() {
        synchronized(syncCurrentIndex){
            return JOBSTATES[currentIndex%JOBSTATES.length];
        }
    }
    
    public void nextState() {
        synchronized(syncCurrentIndex) {
            currentIndex++;
            if (currentIndex%JOBSTATES.length == 0) {
                hasToRun = false;
            }
        }
    }
    
    private long getJobTime(long min, long max) {
        return sleepGen.nextInt((int)(max - min)) + min;
    }
    
    public void stopIT() {
        hasToRun = false;
    }
}
