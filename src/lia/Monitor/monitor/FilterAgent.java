package lia.Monitor.monitor; 


public interface FilterAgent extends java.io.Serializable { 
public void init ( dbStore datastore )  ;
public void go() ;
public void setClient ( MonitorClient client );
public void addNewResult( Result r );
public boolean isAlive() ;
public void finishIt();
}
