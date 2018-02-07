package lia.Monitor.JiniClient.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class VOStorageFilter extends GenericAgregatorFilter {

    private static final HashMap INTERESTING_DATA = new HashMap();
    static{
	INTERESTING_DATA.put("osgVoStorage", new ArrayList());
    }
    
    private static final long FLUSH_TIME = 6*60*60*1000;	// 6 hours
    
    private static final String TOTALS="_TOTALS_";
    
	public Map getRegexClusters() {
		return null;
	}
    
    public Map getInterestingData(){
	return INTERESTING_DATA;
    }
    
    public String getTotalsName(){
	return TOTALS;
    }
    
    public long getRunInterval(){
	return FLUSH_TIME;
    }
    
}
