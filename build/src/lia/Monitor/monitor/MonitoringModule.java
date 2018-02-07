package lia.Monitor.monitor;


public interface MonitoringModule extends lia.util.DynamicThreadPoll.SchJobInt {

// public  MonModuleInfo init( MNode node);
 public  MonModuleInfo init( MNode node, String args ) ;
 public String[] ResTypes() ;
 public String   getOsName();
 public Object   doProcess() throws Exception ;

 public MNode getNode();
 
 public String getClusterName();
 public String getFarmName();

 public boolean isRepetitive() ;

 public String getTaskName();
 public MonModuleInfo getInfo();
 
}


