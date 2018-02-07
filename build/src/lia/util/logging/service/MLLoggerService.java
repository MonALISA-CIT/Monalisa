package lia.util.logging.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.util.logging.comm.MLLogMsg;

public class MLLoggerService implements AppConfigChangeListener {
    
    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLLoggerService.class.getName());
    
    private static MLLoggerService _thisInstance;
    
    //it will be faster ....
    //ususally will notify messages ... it will not be changed frequently
    private PublisherThread[] publishers;
    private Object lock;
    private ArrayList msgs;
    
    private MLLoggerService() {
        lock = new Object();
        publishers = new PublisherThread[0];
        reloadPublishers();
        AppConfig.addNotifier(this);
        msgs = new ArrayList();
    }
    
    private int indexOfPublisher(String publisherName) {
        synchronized(lock) {
            for(int i=0; i<publishers.length; i++) {
                if(publisherName.equals(publishers[i].getPublisherName())) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private void addPublisher(PublisherThread pt) {
        synchronized(lock) {
            PublisherThread[] newPublishers = new PublisherThread[publishers.length + 1];
            System.arraycopy(publishers, 0, newPublishers, 0, publishers.length);
            newPublishers[publishers.length] = pt;
            publishers = newPublishers;
        }
    }
    
    private void reloadPublishers() {
        synchronized(lock) {
            String[] currentConfigPublishers = AppConfig.getVectorProperty("lia.util.logging.service.publishers");
            if(currentConfigPublishers == null) {
                currentConfigPublishers = new String[0];
                logger.log(Level.INFO, " [ MLLoggerService ] No publishers defined");
            }
            
            //needed for binary search
            Arrays.sort(currentConfigPublishers);
            
            String[][] classPaths = new String[currentConfigPublishers.length][];
            for(int i=0; i<currentConfigPublishers.length; i++) {
                classPaths[i] = AppConfig.getProperty(currentConfigPublishers[i] + ".classpath").split("(\\s)*:(\\s)*");
            }
            
            //check for new publishers
            for(int i=0; i<currentConfigPublishers.length; i++) {
                int idx = indexOfPublisher(currentConfigPublishers[i]);
                if(idx >= 0) {
                    //check to see if the classpath for this publisher has been modified
                    PublisherThread pt = publishers[idx];
                    if(!Arrays.equals(classPaths[i], pt.getClassPath())) {
                        pt.setClassPath(classPaths[i]);
                    }
                } else {
                    try {
                        PublisherThread pt = new PublisherThread(currentConfigPublishers[i], classPaths[i]);
                        if(pt != null) {
                            addPublisher(pt);
                            logger.log(Level.INFO, " [ MLLoggerService ] Added publisher [ " + pt.getPublisherName() + " ] thread for class-path" + Arrays.toString(classPaths[i]));                        
                        }
                    }catch(Throwable t) {
                        logger.log(Level.WARNING, " [ MLLoggerService ] Got exception starting Publisher Thread for: " + Arrays.toString(classPaths[i]), t);
                    }
                }
            }//for

            ArrayList publishersToRemove = new ArrayList();
            //stop old publishers ...
            for(int i = 0; i <publishers.length; i++ ) {
                if(Arrays.binarySearch(currentConfigPublishers, publishers[i].getPublisherName()) < 0) {
                    publishersToRemove.add(publishers[i].getName());
                }
            }
            
            for(Iterator it = publishersToRemove.iterator(); it.hasNext(); ) {
                String pName = (String)it.next();
                logger.log(Level.INFO, " [ MLLoggerService ] Publisher :- " + pName + " removed from config file ! ... will stop it");
                removePublisher(pName);
            }
        }//end sync()
    }
    
    public void removePublisher(PublisherThread pt) {
        removePublisher(pt.getPublisherName());
    }
    
    public void removePublisher(String publisherName) {
        if(publisherName == null) return;
        synchronized(lock) {
            int idx = indexOfPublisher(publisherName);
            if(idx >= 0) {
                PublisherThread pt = publishers[idx];
                publishers[idx] = null;
                
                if(pt != null) {
                    pt.stopIt();
                }
                
                PublisherThread[] newPublishers = new PublisherThread[publishers.length - 1];
                if(newPublishers.length == 0) {
                    publishers = newPublishers;
                    return;
                }
                
                if(idx == 0) {
                    System.arraycopy(publishers, 1, newPublishers, 0, newPublishers.length);
                } else if(idx == newPublishers.length) {
                    System.arraycopy(publishers, 0, newPublishers, 0, newPublishers.length);
                } else {
                    System.arraycopy(publishers, 0, newPublishers, 0, idx);
                    System.arraycopy(publishers, idx+1, newPublishers, idx, ( newPublishers.length - idx ));
                }
                
                publishers = newPublishers;
            }
        }//sync
    }
    
    public static synchronized MLLoggerService getInstance() {
        if(_thisInstance == null) {
            _thisInstance = new MLLoggerService();
        }
        
        return _thisInstance;
    }
    
    public void notifyRemoteLogMsg(MLLogMsg mlm) {
        synchronized(msgs) {
            msgs.add(mlm);
            msgs.notify();
        }
    }

    private void notifyPublishers(MLLogMsg[] messages) {
        if (messages == null) return;
        try {
            synchronized(lock) {
                for(int pi = 0; pi < publishers.length; pi++ ) {
                    PublisherThread pt = publishers[pi];
                    for(int i=0; i<messages.length; i++) {
                        MLLogMsg mlm = messages[i];
                        if(mlm == null || mlm.lrs == null) continue;
                        try {
                            pt.publish(mlm);
                        }catch(Throwable t1){
                            logger.log(Level.WARNING, " Got ex notif publisher " + pt + " for MLLogMessage " + mlm);
                        }
                    }
                }
            }
        } catch(Throwable t) {
            logger.log(Level.WARNING, " [ MLLoggerService ] Got gen exc notifying publishers", t );
        }
    }
    
    public void mainLoop() {
        for(;;) {
            MLLogMsg[] messages = null;
            synchronized(msgs) {
                while(msgs.size() == 0) {
                    try {
                        msgs.wait();
                    }catch(Throwable t){}
                }//while
                messages = (MLLogMsg[])msgs.toArray(new MLLogMsg[msgs.size()]);
                msgs.clear();
            }
            notifyPublishers(messages);
        }
    }
    
    public void notifyAppConfigChanged() {
        reloadPublishers();
    }
    
    public static final void main(String[] args) {
        
        logger.log(Level.INFO, "\n\n\n *************** Trying to start MLLoggerService ... ******** \n\n");
        if(MLLoggerService.getInstance() != null) {
            logger.log(Level.INFO, "MLLoggerService STARTED!");
        } else {
            logger.log(Level.WARNING, " cannot start MLLoggerService ... will exit");
            LogManager.getLogManager().reset();//force to close all logging handlers
            System.exit(1);
        }
        
        logger.log(Level.INFO, "Trying to start MLLoggerServer ...");
        if(MLLoggerServer.getInstance() != null) {
            logger.log(Level.INFO, "MLLoggerServer STARTED!");
        } else {
            logger.log(Level.WARNING, " Cannot start MLLoggerServer ... will exit");
            LogManager.getLogManager().reset();//force to close all logging handlers
            System.exit(1);
        }
        
        logger.log(Level.INFO, "Trying to start MLLoggerJiniService ...");
        if(MLLoggerJiniService.getInstance() != null) {
            logger.log(Level.INFO, "MLLoggerJiniService STARTED!");
        } else {
            logger.log(Level.WARNING, " Cannot start MLLoggerJiniService ... will exit");
            LogManager.getLogManager().reset();//force to close all logging handlers
            System.exit(1);
        }
        
        logger.log(Level.INFO, " [ MLLoggerService ] will start mainLoop()" );
        _thisInstance.mainLoop();
    }



}
