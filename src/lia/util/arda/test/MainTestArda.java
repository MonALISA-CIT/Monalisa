package lia.util.arda.test;

import java.util.Date;
import java.util.Random;
import java.util.Vector;

import lia.util.ntp.NTPDate;

public class MainTestArda {
    
    public static final int NO_OF_JOBS = 30;
    public static final int NO_OF_CHILDJOBS = 3;
    
    private static Vector startArdaJobs() {
        Vector jobs = new Vector();
        Random r = new Random();
        for(int i=0; i<NO_OF_JOBS; i++){
            ArdaJob job = new ArdaJob("Job_"+i);
            try {
                long sTime = r.nextInt(1000) + 1000;
                System.out.println(new Date() + " Main thread sleeping for: "+sTime+" ms" );
                Thread.sleep(sTime);
            }catch(Throwable t1){
                t1.printStackTrace();
            }
            jobs.add(job);
        }
        
        for(int i=0; i<NO_OF_JOBS; i++){
            ArdaJob job = (ArdaJob)jobs.elementAt(i);
            job.start();
        }
        return jobs;
    }
    
    private static Vector startChildArdaJobs() {
        Vector jobs = new Vector();
        Random r = new Random();
        for(int i=0; i<NO_OF_CHILDJOBS; i++){
            ArdaChildJob job = new ArdaChildJob("MJOB"+i, null, 0, null, NTPDate.currentTimeMillis());
            try {
//                long sTime = r.nextInt(1000) + 5 * 60 * 1000;
                long sTime = r.nextInt(1000) + 60 * 1000;
                System.out.println(new Date() + " Main thread sleeping for: "+sTime+" ms" );
                Thread.sleep(sTime);
            }catch(Throwable t1){
                t1.printStackTrace();
            }
            jobs.add(job);
            job.start();
        }
        
        return jobs;
    }
    
    public static void main(String[] args) {
        
        startChildArdaJobs();
        Vector ardaJobs = startArdaJobs();
        
        while(true) {
            try {
                Thread.sleep(10*1000);
            }catch(Throwable t){
                t.printStackTrace();
            }
            boolean isAlive = false;
            for (int i = 0; i < ardaJobs.size(); i++) {
                ArdaJob aj = (ArdaJob)ardaJobs.elementAt(i);
                if(aj.isStillRunning()) {
                    isAlive = true;
                    break;   
                }
            }
            
            if (isAlive) continue;
            
            try {
                Thread.sleep(10 * 60 * 1000);
            }catch(Throwable t){
                t.printStackTrace();
            }
            
            ardaJobs = startArdaJobs();
        }

    }
}
