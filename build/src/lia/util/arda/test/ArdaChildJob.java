package lia.util.arda.test;

import java.util.Date;
import java.util.Random;
import java.util.Vector;

import lia.util.ApMon.ApMon;
import lia.util.ntp.NTPDate;

public class ArdaChildJob extends Thread {
    
//    private static final double JOBSTATES[] = new double[]{0, 0.25, .5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.25, 2.5, 2.75, 3, 3.25, 3.5, 3.75, 4}; 
    private static final double JOBSTATES[] = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}; 
    private static final int CHILDREN_NO = 10; 
    
    private static final String APMON_DestAddrs[] = new String[]{"localhost"};
    private static final int APMON_DestPorts[] = new int[]{7884};
    
    private static Vector apMonDestAddrs;
    private static Vector apMonDestPorts;
    boolean firstTime;
    
    private boolean hasToRun;
    private int currentIndex;
    private Object syncCurrentIndex;
    private boolean shouldReport;
    
    ApMon apm;
    int childIndex;
    Random sleepGen;
    ArdaChildJob parent;
    Vector children;
    private String clusterName;

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
    
    public ArdaChildJob(String name, ArdaChildJob parent, double startState, String clusterName, long randomSeed) {
        super(name);
        shouldReport = true;
        if (parent == null) {
            this.clusterName = "Arda_MJobs";
        } else {
            this.clusterName = clusterName;
        }
        
        children = new Vector();
        childIndex = 0;
        this.parent = parent;
        currentIndex = 0;
        for (short i=0; i < JOBSTATES.length; i++) {
            if (JOBSTATES[i] >= startState-0.025 && JOBSTATES[i] <= startState+0.025) {
                currentIndex = i;
                break;
            }
        }
        
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
        sleepGen = new Random(randomSeed);
        hasToRun = true;
    }
    
    private void sendState() {
        try {
            if (!shouldReport) return;
            System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + " ["+getName()+"] Sending param ... [ " + clusterName + ", " + getName() + ", State, " + getCurrentState()+" ]");
            apm.sendParameter(clusterName, getName(), "State", getCurrentState());
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
                    System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + "[" + getName() + "] newState ... finishTime [ " + new Date(finishTime) + " ] ");
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
    
    
    public double getCurrentState() {
        synchronized(syncCurrentIndex){
            return JOBSTATES[currentIndex%JOBSTATES.length];
        }
    }
    
    public void nextState() {
        synchronized(syncCurrentIndex) {
            currentIndex++;
            if (this.parent == null ) {
                if ( currentIndex == 5 ) {
                    if (children.size() == 0) {
                        sendState();
                        for (int i =0;i < CHILDREN_NO; i++) {
                            ArdaChildJob child =new ArdaChildJob( "Child_" + getName() + "_" + childIndex, this, JOBSTATES[currentIndex], clusterName, getJobTime(1000, 200*1000));
                            childIndex++;
                            System.out.println(new Date() + " / " + new Date(NTPDate.currentTimeMillis()) + " ["+getName()+"] Adding a child Child_"+childIndex);
                            children.add(child);
                            child.start();
                        }
                        shouldReport = false;
                    } else {
                        for (int i =0;i < CHILDREN_NO; i++) {
                            ArdaChildJob child = (ArdaChildJob)children.elementAt(i);
                            if (child.hasToRun) return;
                        }
                        
                        //all the childrent stopped
                        shouldReport = true;
                        children.clear();
                        currentIndex = 0;
                        childIndex = 0;
                        return;
                    }
                }
            }
            
            if (currentIndex%JOBSTATES.length == 0) {
                currentIndex = 0;
                if (parent == null) {
                    try {
                        Thread.sleep(getJobTime(15*60*1000, 16*60*1000));
                    }catch(Throwable t){
                        
                    }
                }
            }
            if (this.parent != null && currentIndex == 0) hasToRun = false;
        }
    }
    
    private long getJobTime(long min, long max) {
        return sleepGen.nextInt((int)(max - min)) + min;
    }
    
    public void stopIT() {
        hasToRun = false;
    }
}
