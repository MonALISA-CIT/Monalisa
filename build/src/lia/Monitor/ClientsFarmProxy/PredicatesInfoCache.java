/*
 * $Id: PredicatesInfoCache.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import net.jini.core.lookup.ServiceID;

/**
 * 
 * @author mickyt
 *
 */
public class PredicatesInfoCache {

	public final String key;
	public final ServiceID farmID;
	public final ConcurrentSkipListMap<Integer, ConcurrentSkipListSet<Integer>> msgs;
	public Object result;
	public final boolean isHistory ;
	
	public PredicatesInfoCache (String key, ServiceID farmID, boolean isHistory) {
		this.key = key;
		this.farmID = farmID;
		this.isHistory = isHistory ;
		msgs = new ConcurrentSkipListMap<Integer, ConcurrentSkipListSet<Integer>>();
	} // PredicatesInfoCache
	
} // PredicatesInfoCache
