package lia.web.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lazyj.RequestWrapper;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;
import lia.web.servlets.web.Utils;

/**
 * @author costing
 *
 */
public abstract class CacheServlet extends ThreadedPage {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	static Map<String, CachingStructure> cache;

	/**
	 * 
	 */
	static Map<String, Long> underConstruction;

	/**
	 * 
	 */
	static Object cacheLock;

	/**
	 * 
	 */
	static Thread tSupervisor;

	/**
	 * 
	 */
	static int iServerPort = 0;

	/**
	 * 
	 */
	static LRUCache lruCache = new LRUCache(20); // keep rebuilding the last 20 requests

	static {
		cache = new HashMap<String, CachingStructure>();
		underConstruction = new HashMap<String, Long>();
		cacheLock = new Object();
		tSupervisor = new CacheSupervisor();
		tSupervisor.start();
	}

	private boolean bShouldClose = false;

	@Override
	protected boolean shouldClose() {
		return bShouldClose;
	}

	/**
	 * @author costing
	 *
	 */
	public static final class CacheStatistics {
		/**
		 * hits
		 */
		public final long lCacheHits;

		/**
		 * total count
		 */
		public final long lCacheCount;

		/**
		 * total key length
		 */
		public final long lTotalKeyLength;

		/**
		 * total cache size
		 */
		public final long lTotalCacheSize;

		/**
		 * 
		 */
		public CacheStatistics() {
			lCacheHits = lCacheHitCount;

			long lTKL = 0;
			long lTCS = 0;

			synchronized (cacheLock) {
				lCacheCount = cache.size();

				final Iterator<CachingStructure> it = cache.values().iterator();

				while (it.hasNext()) {
					final CachingStructure cs = it.next();

					lTKL += cs.sKey.length();
					lTCS += cs.content.length;
				}
			}

			lTotalKeyLength = lTKL;
			lTotalCacheSize = lTCS;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();

			sb.append("hits: ").append(lCacheHits);

			if (getRequestCount() > 0) {
				sb.append(" (").append(DoubleFormat.point((lCacheHits * 100d) / getRequestCount())).append("%)");
			}

			sb.append(", current cache size: ").append(lCacheCount).append(", occupied memory: ").append(DoubleFormat.size(lTotalKeyLength + lTotalCacheSize, "B"));

			return sb.toString();
		}
	}

	private static final class LRUCache extends LinkedHashMap<String, Long> {
		/**
		 * Eclipse suggestion
		 */
		private static final long serialVersionUID = -9221730219685670189L;

		private static final float FACTOR = 0.75f;

		private final int iCacheSize;

		public LRUCache(final int cacheSize) {
			super((int) Math.ceil(cacheSize / FACTOR) + 1, FACTOR, true);

			iCacheSize = cacheSize;
		}

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, Long> eldest) {
			return size() > iCacheSize;
		}
	}

	/**
	 * cache hits
	 */
	static volatile long lCacheHitCount = 0;

	/**
	 * @return statistics
	 */
	public static final CacheStatistics getCacheStatistics() {
		return new CacheStatistics();
	}

	/**
	 * accesses comparator
	 */
	static final Comparator<CachingStructure> accessesComparator = new Comparator<CachingStructure>() {
		@Override
		public int compare(final CachingStructure cs1, final CachingStructure cs2) {
			return (cs2.accesses - cs1.accesses);
		}
	};

	private static class CacheSupervisor extends Thread {
		public CacheSupervisor() {
			super("(ML) Web Page Cache Supervisor");
		}

		@Override
		public void run() {
			while (true) {
				try {
					sleep(2 * 1000); // every 2 seconds clean/refresh the cache
				} catch (Exception e) {
					// ignore
				}

				final long now = NTPDate.currentTimeMillis();

				final List<String> lRemove = new ArrayList<String>();
				List<CachingStructure> lRefresh = new ArrayList<CachingStructure>();

				CachingStructure cs;

				long lActiveSize = 0;

				try {
					synchronized (cacheLock) { // remove expired pages from cache, otherwise the cache might grow indefinitely
						final Iterator<CachingStructure> it = cache.values().iterator();
						while (it.hasNext()) {
							cs = it.next();

							if (cs.content == null || cs.expires < now)
								lRemove.add(cs.sKey);
							else
								lActiveSize += cs.sKey.length() + cs.content.length;
						}

						final Iterator<String> it2 = lRemove.iterator();
						while (it2.hasNext()) {
							final String sKey = it2.next();

							cs = cache.remove(sKey);

							if (cs.accesses >= 0)
								lRefresh.add(cs);
						}

						if (lActiveSize > 2 * 1024 * 1024 || cache.size() > 40) {
							final ArrayList<CachingStructure> l = new ArrayList<CachingStructure>(cache.values());
							Collections.sort(l);

							// estimate the number of pages to keep in the cache
							int iDesiredSize = (int) ((2 * 1024L * 1024 * cache.size()) / lActiveSize);

							if (iDesiredSize > 40)
								iDesiredSize = 40;

							if (iDesiredSize > cache.size())
								iDesiredSize = cache.size();

							for (int i = cache.size() - iDesiredSize; i >= 0; i--) {
								cs = l.get(i);

								cache.remove(cs.sKey);

								if (cs.accesses >= 0)
									lRefresh.add(cs);
							}
						}
					}

					if (AppConfig.getb("lia.web.cache_refresh_disabled", true))
						lRefresh.clear();

					if (lRefresh.size() > 3) { // no more than 3 refreshes at one step
						Collections.sort(lRefresh, accessesComparator);

						// refresh only the 3 most accessed ones
						List<CachingStructure> lTemp = lRefresh;
						lRefresh = new ArrayList<CachingStructure>(3);

						for (int i = 0; i < 3; i++)
							lRefresh.add(lTemp.get(i));
					}

					for (int i = 0; i < lRefresh.size(); i++) {
						cs = lRefresh.get(i);
						cs.refresh();
					}

					synchronized (underConstruction) {
						final Iterator<Map.Entry<String, Long>> it = underConstruction.entrySet().iterator();

						Map.Entry<String, Long> me;
						Long l;

						while (it.hasNext()) {
							me = it.next();
							l = me.getValue();

							if (now - l.longValue() > 60000)
								it.remove();
						}
					}

				} catch (Throwable t) {
					System.err.println("Exception in cache management : " + t + " (" + t.getMessage() + ")");
					t.printStackTrace();

					clearCache();
				}
			}
		}
	}

	/**
	 * @return extra string to add to the caching key (if the response doesn't depend only on the URL parameters)
	 */
	protected String getCacheKeyModifier() {
		return "";
	}

	private static final CachingStructure get(final String sKey) {
		synchronized (cacheLock) {
			final CachingStructure cs = cache.get(sKey);

			if (cs != null) {
				if (cs.expires < NTPDate.currentTimeMillis()) {
					// if a request came for a not-yet-removed key then this content will be regenerated anyway
					// don't force a content regeneration, it's redundant
					cache.remove(sKey);
				}
				else {
					return cs;
				}
			}
		}

		return null;
	}

	/**
	 * @param sKey
	 * @param cs
	 */
	static final void put(final String sKey, final CachingStructure cs) {

		synchronized (cacheLock) {
			cache.put(sKey, cs);
		}

	}

	/**
	 * @return whether or not to cache POST responses
	 */
	protected boolean allowPOSTCaching() {
		return false;
	}

	@Override
	public void masterInit() {
		bShouldClose = false;

		if (iServerPort == 0)
			iServerPort = request.getServerPort();

		final long lTimeout = getCacheTimeout();

		if (AppConfig.getb("lia.web.cache_disabled", false) == false && (bGet || allowPOSTCaching()) && (lTimeout > 0) && gets("dont_cache").length() == 0) {
			String sKey = request.getRequestURI();

			if (request.getQueryString() != null) {
				String sReq = request.getQueryString();

				// remove this from the key, otherwise it's useless
				int idx = sReq.indexOf("cache_refresh_request=true");

				if (idx >= 0) {
					int idx2 = sReq.indexOf("&", idx);

					if (idx > 0 && sReq.charAt(idx - 1) == '&')
						idx--;

					sReq = sReq.substring(0, idx) + (idx2 > 0 ? sReq.substring(idx2 + 1) : "");
				}

				if (sReq.length() > 0)
					sKey += "?" + sReq;
			}

			if (bGet == false) {
				sKey += "\tPOST";
				final Enumeration<?> e = request.getParameterNames();

				final List<String> l = new LinkedList<String>();
				while (e != null && e.hasMoreElements()) {
					l.add((String) e.nextElement());
				}

				Collections.sort(l);

				final Iterator<String> it = l.iterator();

				String sParam;
				String[] vsValues;
				List<String> l2;
				Iterator<String> it2;

				while (it.hasNext()) {
					sParam = it.next();

					if (sParam != null && (sParam.equals("submit_plot") || sParam.equals("cache_refresh_request") || sParam.startsWith("interval_date_") || sParam.startsWith("interval_hour_")))
						continue;

					vsValues = request.getParameterValues(sParam);

					l2 = new LinkedList<String>();

					for (int i = 0; vsValues != null && i < vsValues.length; i++)
						l2.add(vsValues[i]);

					Collections.sort(l2);

					it2 = l2.iterator();

					sParam = encode(sParam);

					while (it2.hasNext())
						sKey += sParam + "=" + encode(it2.next()) + "&";
				}
			}

			sKey += "\tCACHE" + getCacheKeyModifier();

			// System.err.println("Timeout = "+lTimeout+" for "+sKey);

			CachingStructure cs = get(sKey);

			synchronized (lruCache) {
				if (gets("cache_refresh_request").length() <= 0)
					lruCache.put(sKey, Long.valueOf(NTPDate.currentTimeMillis()));
			}

			logTiming("\n** sKey = " + sKey + "\n** timeout = " + lTimeout + "\n** in cache: " + (cs != null));

			if (cs == null) {
				// check if it is under construction

				int iWait = 200; // 50*200 ~= 10 seconds max wait for a page to be generated
				do {
					synchronized (underConstruction) {
						if (underConstruction.get(sKey) == null) {
							// put myself in the list
							underConstruction.put(sKey, Long.valueOf(NTPDate.currentTimeMillis()));

							break;
						}
					}

					try {
						Thread.sleep(50);
					} catch (InterruptedException ie) {
						break;
					}
				} while ((--iWait > 0) && ((cs = get(sKey)) == null));
			}

			logTiming("After under construction check : " + (cs != null));

			if (cs != null) { // it's ok, i can write the cache content to the output
				response.setContentType("text/html; charset=UTF-8"); // hmmm ... this should be rewriten ...........

				RequestWrapper.setCacheTimeout(response, (int) ((cs.expires - NTPDate.currentTimeMillis()) / 1000));

				response.setHeader("Content-Language", "en");

				int iLength = 0;

				try {
					final byte[] b = cs.bZip ? decompress(cs.content) : cs.content;

					response.setContentLength(b.length);

					osOut.write(b);

					iLength = b.length;
				} catch (Exception e) {
					System.err.println("CacheServlet: exception writing the cached content: " + e + " (" + e.getMessage() + ")");
					e.printStackTrace();
				}

				try {
					osOut.flush();
				} catch (Exception e) {
					// ignore
				}

				try {
					pwOut.flush();
				} catch (Exception e) {
					// ignore
				}

				try {
					pwOut.close();
				} catch (Exception e) {
					// ignore
				}

				try {
					osOut.close();
				} catch (Exception e) {
					// ignore
				}

				bShouldExecute = false;
				bAuthOK = true;

				cs.accesses++;

				lCacheHitCount++;

				logTiming("response from cache for ip: " + getHostName());

				Utils.logRequest("cache", iLength, request, false, 0);
			}
			else { // we must generate the page
				logTiming("Generating the page");

				osOut = new StringBuilderOutputStream(sKey);
				pwOut = new PrintWriter(osOut);

				bShouldClose = true;
			}
		}
		else { // this request cannot be cached
			logTiming("Not caching request to " + request.getServletPath() + " because:\n" + "  lia.web.cache_disabled: " + (AppConfig.getProperty("lia.web.cache_disabled", null) == null) + "\n"
					+ "  bGet: " + bGet + "\n" + "  allowPOSTCaching(): " + allowPOSTCaching() + "\n" + "  lTimeout: " + lTimeout);
		}

		super.masterInit();
	}

	/**
	 * override this method to set the caching time (in seconds)
	 * 
	 * @return disabled
	 */
	protected long getCacheTimeout() {
		return 0; // default cache policy : disabled
	}

	/**
	 * @author costing
	 *
	 */
	static final class CachingStructure implements Comparable<CachingStructure> {
		/**
		 * 
		 */
		public long expires = 0;

		/**
		 * 
		 */
		public long lifetime = 0;

		/**
		 * 
		 */
		public String sKey = null;

		/**
		 * 
		 */
		public int accesses = 0;

		/**
		 * 
		 */
		public byte[] content = null;

		/**
		 * 
		 */
		public boolean bZip = false;

		@Override
		public int compareTo(CachingStructure cs) {
			return (int) (expires - cs.expires);
		}

		/**
		 * 
		 */
		public void refresh() {
			final StringTokenizer st = new StringTokenizer(sKey, "\t");

			String sAddr = st.nextToken();

			String POST = null;
			String EXTRA = null;

			try {
				while (st.hasMoreTokens()) {
					String s = st.nextToken();
					if (s.startsWith("POST"))
						POST = s.substring(4);

					if (s.startsWith("CACHE"))
						EXTRA = s.substring(5);
				}

				Socket s = new Socket("127.0.0.1", iServerPort);

				PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
				BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));

				StringBuilder sbReq = new StringBuilder();

				if (POST != null) {
					POST += "cache_refresh_request=true";

					sbReq.append("POST " + sAddr + " HTTP/1.0\r\n");
					sbReq.append("Content-Length: " + POST.length() + "\r\n");
					sbReq.append("Content-Type: application/x-www-form-urlencoded\r\n");
					if (EXTRA != null && EXTRA.length() > 0)
						sbReq.append("MLSetCookies: " + EXTRA + "\r\n");
					sbReq.append("\r\n");
					sbReq.append(POST);
				}
				else {
					if (sAddr.indexOf("?") < 0)
						sAddr += "?";
					else
						if (!sAddr.endsWith("?") && !sAddr.endsWith("&"))
							sAddr += "&";

					sAddr += "cache_refresh_request=true";

					sbReq.append("GET " + sAddr + " HTTP/1.0\r\n");
					if (EXTRA != null && EXTRA.length() > 0)
						sbReq.append("MLSetCookies: " + EXTRA + "\r\n");

					sbReq.append("\r\n");
				}

				pw.print(sbReq.toString());
				pw.flush();

				while (br.readLine() != null) {
					// read the full response, discard the actual text, we don't care what the response is
				}

				pw.close();
				br.close();
			} catch (Throwable t) {
				System.err.println("Cache: cannot refresh because: " + t + " (" + t.getMessage() + ")");
				t.printStackTrace();
			}
		}
	}

	/**
	 * @return content
	 */
	public static final String getCacheContent() {
		final StringBuilder sb = new StringBuilder();

		sb.append("<table border=1 cellspacing=0 cellpadding=0><tr>" + "<th>No</th>" + "<th>Key</th>" + "<th>Length</th>" + "<th>Expires (sec)</th>" + "<th>Lifetime</th>" + "<th>Accesses</th>"
				+ "<th>Zip</th></tr>");

		final long lNow = NTPDate.currentTimeMillis();

		long lTotalKeyLength = 0;
		long lTotalCacheSize = 0;

		synchronized (cacheLock) {
			Iterator<String> it = cache.keySet().iterator();

			int i = 0;

			while (it.hasNext()) {
				String sKey = it.next();

				CachingStructure cs = cache.get(sKey);

				sb.append("<tr><td align=right>").append(++i).append("</td><td align=left nowrap>").append(sKey).append("</td><td align=right>").append(cs.content.length)
						.append("</td><td align=right>").append((cs.expires - lNow) / 1000d).append("</td><td align=right>").append(cs.lifetime / 1000d).append("</td><td align=right>")
						.append(cs.accesses).append("</td><td align=center>").append(cs.bZip).append("</td></tr>");

				lTotalKeyLength += sKey.length();
				lTotalCacheSize += cs.content.length;
			}
		}

		sb.append("<tr><td align=left><b>TOTAL</b></td><td align=right><b>").append(lTotalKeyLength).append("</b></td><td align=right><b>").append(lTotalCacheSize)
				.append("</b></td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>");

		sb.append("</table>");

		sb.append("<br><br>Last active requests:<br><table border=1 cellspacing=0 cellpadding=0>");

		synchronized (lruCache) {
			final Iterator<Map.Entry<String, Long>> it = lruCache.entrySet().iterator();

			int i = 0;

			while (it.hasNext()) {
				Map.Entry<String, Long> me = it.next();

				String sKey = me.getKey();
				Long lTimeout = me.getValue();
				sb.append("<tr><td align=right>").append(++i).append("</td><td nowrap>").append(sKey).append("</td><td>").append((new Date(lTimeout.longValue())).toString()).append("</td></tr>");
			}
		}

		sb.append("</table>");

		return sb.toString();
	}

	/**
	 * @return list of keys
	 */
	static final String getCacheKeysList() {
		synchronized (cacheLock) {
			Iterator<String> it = cache.keySet().iterator();

			StringBuilder sb = new StringBuilder();

			while (it.hasNext())
				sb.append(it.next()).append("\n");

			return sb.toString();
		}
	}

	/**
	 * 
	 */
	static transient int iMaxSize = 16 * 1024;

	private final class StringBuilderOutputStream extends OutputStream {

		private final OutputStream origos;

		private final String sKey;

		private final ByteArrayOutputStream baos;

		public StringBuilderOutputStream(final String _sKey) {
			origos = osOut;
			sKey = _sKey;
			baos = new ByteArrayOutputStream(iMaxSize); // max page size for this repository
		}

		@Override
		public void write(final int b) throws IOException {
			baos.write(b);
		}

		@Override
		public void close() throws IOException {
			try {
				pwOut.flush();
			} catch (Exception e) {
				// ignore
			}

			logTiming("Page generation complete, saving the cache");

			final byte[] vbContent = baos.toByteArray();

			if (vbContent.length > iMaxSize && vbContent.length < 128 * 1024)
				iMaxSize = vbContent.length; // don't allocate too much memory for any page

			if (!wasRedirect()) {
				final CachingStructure cs = new CachingStructure();

				final long lCacheTimeout = getCacheTimeout() * 1000;

				cs.lifetime = lCacheTimeout;
				cs.expires = NTPDate.currentTimeMillis() + lCacheTimeout;
				cs.content = vbContent;
				cs.sKey = sKey;

				synchronized (lruCache) {
					// a mechanism to eliminate seldom requested pages

					Long l = lruCache.get(sKey);

					if (gets("cache_refresh_request").length() > 0 && (l == null || (NTPDate.currentTimeMillis() - l.longValue() > 1000 * 60 * 60))) {
						cs.accesses--;
					}
				}

				if (cs.content.length > 20000) {
					try {
						cs.content = compress(cs.content);
						cs.bZip = true;
					} catch (Exception e) {
						System.err.println("CacheServlet: exception compressing: " + e + " (" + e.getMessage() + ")");
						e.printStackTrace();
					}
				}

				RequestWrapper.setCacheTimeout(response, (int) (lCacheTimeout / 1000));

				response.setContentLength(vbContent.length);

				put(sKey, cs);

				logTiming("Cache contents after put: \n" + getCacheKeysList());
			}

			synchronized (underConstruction) {
				underConstruction.remove(sKey);
			}

			logTiming("Writing content to the client");

			origos.write(vbContent);
			origos.flush();
			origos.close();

			logTiming("All finished");
		}

	}

	/**
	 * clear cache
	 */
	public static final void clearCache() {
		synchronized (cacheLock) {
			cache.clear();
		}

		synchronized (underConstruction) {
			underConstruction.clear();
		}

		synchronized (lruCache) {
			lruCache.clear();
		}
	}

	/**
	 * @param orig
	 * @return gzip
	 * @throws IOException
	 */
	public static final byte[] compress(byte[] orig) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();

		final GZIPOutputStream gos = new GZIPOutputStream(baos);

		gos.write(orig, 0, orig.length);

		gos.finish();

		gos.flush();
		baos.flush();

		return baos.toByteArray();
	}

	/**
	 * @param orig
	 * @return gnuzip
	 * @throws IOException
	 */
	public static final byte[] decompress(byte[] orig) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();

		final ByteArrayInputStream bais = new ByteArrayInputStream(orig);

		final GZIPInputStream gis = new GZIPInputStream(bais);

		final byte[] buff = new byte[1024];

		int r;

		while ((r = gis.read(buff, 0, buff.length)) >= 0) {
			baos.write(buff, 0, r);
		}

		baos.flush();

		return baos.toByteArray();
	}

}
