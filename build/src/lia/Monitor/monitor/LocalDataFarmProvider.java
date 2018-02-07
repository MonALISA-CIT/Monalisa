package lia.Monitor.monitor; 

/**
 * same as LocalDataProvider, except it uses LocalDataFarmClient
 * instead of LocalDataClient 
 */

public interface LocalDataFarmProvider 
{
  public void addLocalClient ( LocalDataFarmClient c , monPredicate p ) ; 
  public void  addLocalClient ( LocalDataFarmClient c ,  String func ) ;
  public void deleteLocalClient (LocalDataFarmClient c ) ;
}
