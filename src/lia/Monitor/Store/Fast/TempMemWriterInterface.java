package lia.Monitor.Store.Fast;

import java.util.ArrayList;

/**
 * Interface describing a memory buffer
 */
public interface TempMemWriterInterface {

    /**
     * @return number of values in the memory buffer
     */
    public int getSize();
    
    /**
     * @return the time interval (milliseconds) of the data
     */
    public long getTotalTime();
    
    /**
     * @return how many values is estimated to keep in memory in the current situation
     */
    public int getLimit();
    
    /**
     * @return absolute maximum number of values, function of JVM memory parameters
     */
    public int getHardLimit();
    
    /**
     * @return statistics
     */
    public long getServedRequests();
    
    /**
     * @return the most recent objects from each series
     */
    public ArrayList<Object> getLatestValues();

}