package lia.Monitor.monitor; 

import lia.Monitor.DataCache.Cache;

public interface MonitorFilter extends java.io.Serializable { 
public String getName();
public void initdb (dbStore datastore, MFarm farm);
public void initCache(Cache cache);
public void addClient ( MonitorClient client );
public void removeClient ( MonitorClient client );
public void addNewResult( Object r );
public boolean isAlive() ;
public void finishIt();
public void confChanged();
}
