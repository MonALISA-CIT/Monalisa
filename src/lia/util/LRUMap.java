/**
 * 
 */
package lia.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Map with restricted size and LRU behavior (when the size is reached the oldest unused
 * entry is deleted to make some space for the new entry).<br>
 * <br>
 * The implementation is based on LinkedHashMap, thus it is not thread safe. Remember
 * {@link java.util.Collections#synchronizedMap}. 
 * 
 * @author costing
 * @param <K> key type
 * @param <T> value type
 * @since Oct 26, 2007 (1.0.2)
 */
public class LRUMap<K, T> extends LinkedHashMap<K, T>{
	private static final long	serialVersionUID	= 2470194500885712833L;

	private final int			iCacheSize;

	/**
	 * @param cacheSize How many entries can be in this map at maximum. A negative value means "unlimited" size.
	 */
	public LRUMap(final int cacheSize) {
		super(16, 0.75f, true);
		
		this.iCacheSize = cacheSize;
	}

	@Override
	protected boolean removeEldestEntry(final Map.Entry<K, T> eldest) {
		return this.iCacheSize>=0 && size() > this.iCacheSize;
	}
}