/**
 * 
 */
package lia.web.utils;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * @author costing
 * @since Nov 4, 2007
 */
public final class ColorFactory implements AppConfigChangeListener{

	private static final class LRUCache extends LinkedHashMap<Integer, Color> {

		private static final long	serialVersionUID	= -4199373198698500849L;
		private static final float  FACTOR = 0.75f;

		private int	iCacheSize;

		/**
		 * @param cacheSize1
		 */
		public LRUCache(final int cacheSize1) {
			super((int) Math.ceil(cacheSize1 / FACTOR) + 1, FACTOR, true);

			this.iCacheSize = cacheSize1;
		}

		@Override
		protected boolean removeEldestEntry(final Map.Entry<Integer, Color> eldest) {
			return size() > iCacheSize;
		}
		
		/**
		 * @param iNewSize
		 */
		public void setCacheSize(final int iNewSize){
			if (iNewSize < size()){
				synchronized(this){
					clear();
				}
			}
			
			iCacheSize = iNewSize;
		}
		
		/**
		 * @return the max number of entries allowed in the cache
		 */
		public int getLimit(){
			return iCacheSize;
		}
	}
	
	private static final int DEFAULT_CACHE_SIZE = 5000;
	
	private static final LRUCache colorCache = new LRUCache(DEFAULT_CACHE_SIZE);

	static{
		reloadConfig();
		
		AppConfig.addNotifier(new ColorFactory());
	}
	
	private static long lHit = 0;
	private static long lMiss = 0;
	
	/**
	 * @param r
	 * @param g
	 * @param b
	 * @return the color, from cache or not
	 */
	public static Color getColor(final int r, final int g, final int b){
		return getColor(r, g, b, 255);
	}
	
	/**
	 * @param r
	 * @param g
	 * @param b
	 * @param a
	 * @return the color, from cache or not
	 */
	public static Color getColor(final int r, final int g, final int b, final int a){
		 final int value = ((a & 0xFF) << 24) |
         			 ((r & 0xFF) << 16) |
         			 ((g & 0xFF) << 8)  |
         			 ((b & 0xFF) << 0);
		 
		 return getColor(value, a!=255);
	}
	
	/**
	 * @param rgb
	 * @return the color, from cache or not
	 */
	public static Color getColor(final int rgb){
		return getColor(rgb, false);
	}
	
	/**
	 * @param rgb
	 * @param hasAlpha
	 * @return the color, from cache or not
	 */
	public static Color getColor(final int rgb, final boolean hasAlpha){
		 final Integer iKey = Integer.valueOf(rgb);
		 
		 synchronized (colorCache) {
			Color c = colorCache.get(iKey);

			if (c == null) {
				c = new Color(rgb, hasAlpha);

				colorCache.put(iKey, c);

				lMiss++;
			}
			else {
				lHit++;
			}
			
			return c;
		}
	}
	
	/**
	 * Get the number of cached Color objects
	 * 
	 * @return size of the cache
	 */
	public static int size(){
		return colorCache.size(); 
	}
	
	/**
	 * Get the number of hits in the cache
	 * 
	 * @return hits count
	 */
	public static long getHitCount(){
		return lHit;
	}
	
	/**
	 * Get the number of misses in the cache
	 * 
	 * @return miss count
	 */
	public static long getMissCount(){
		return lMiss;
	}

	private static void reloadConfig(){
		colorCache.setCacheSize(AppConfig.geti("lia.web.utils.ColorFactory.CACHE_SIZE", colorCache.getLimit()));
	}
	
	@Override
	public void notifyAppConfigChanged() {
		reloadConfig();
	}
	
}
