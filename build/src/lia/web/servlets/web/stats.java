package lia.web.servlets.web;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Format;
import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.Store.Cache;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.IDGenerator;
import lia.Monitor.Store.Fast.Writer;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.util.JEPHelper;
import lia.web.utils.CacheServlet;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.Page;
import lia.web.utils.ServletExtension;

/**
 * This servlet builds nice, colored tables
 * 
 * @author costing
 * @since forever
 */
@SuppressWarnings("nls")
public final class stats extends CacheServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * Logging facility
	 */
	private static final Logger logger = Logger.getLogger(Rest.class.getCanonicalName());

	private transient Page pMaster = null;

	private transient ByteArrayOutputStream baos;

	private static transient volatile TransparentStoreFast store = null;

	/**
	 * The default is to cache the tables for 45 seconds
	 * 
	 * @return 45 seconds (default)
	 */
	@Override
	public long getCacheTimeout() {
		if (lazyj.Utils.stringToBool(gets("dump_csv"), false)) {
			return 0;
		}

		final String range = request.getHeader("Range");

		if ((range != null) && (range.length() > 0)) {
			return 0;
		}

		if (geti("cache_timeout", -1) >= 0) {
			return geti("cache_timeout"); //$NON-NLS-1$
		}

		return 45;
	}

	/**
	 * Create the master page
	 */
	@Override
	public void doInit() {
		baos = new ByteArrayOutputStream();

		pMaster = new Page(baos, sResDir + "masterpage/masterpage.res"); //$NON-NLS-1$
	}

	private String sTitle = "";

	/**
	 * Execution entry point
	 */
	@Override
	public void execGet() {
		response.setContentType("text/html");

		sTitle = "";

		final long lStart = System.currentTimeMillis();

		if (store == null) {
			try {
				store = (TransparentStoreFast) TransparentStoreFactory.getStore();
			} catch (Exception e) {
				System.out.println("Error building store : " + e + " (" + e.getMessage() + ")");
				bAuthOK = true;
				return;
			}
		}

		Page p = new Page(sResDir + "stats/stats.res");

		Properties prop = Utils.getProperties(sConfDir, gets("page"));

		setLogTiming(prop);

		p.modify("page", gets("page"));

		// URL parameters override everything
		Enumeration<?> eParams = request.getParameterNames();
		while (eParams.hasMoreElements()) {
			final String sParameter = (String) eParams.nextElement();

			if (sParameter.equals("page")) {
				continue;
			}

			prop.setProperty(sParameter, gets(sParameter));
		}

		String sCSV = null;

		try {
			sCSV = buildStatistics(p, prop);
		} catch (Exception e) {
			System.err.println("Exception : " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
		}

		if (sCSV != null) {
			response.setContentType("text/csv");

			try {
				writeResponse(sCSV, request, response, osOut);
			} catch (final IOException ioe) {
				logger.log(Level.WARNING, "Exception writing the response back to the client " + request.getRemoteAddr(), ioe);
			}

			System.err.println("stats_dump_csv (" + gets("page") + "), ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

			Utils.logRequest("stats_dump_csv", (int) (System.currentTimeMillis() - lStart), request, false, System.currentTimeMillis() - lStart);

			baos = null;

			return;
		}

		if (pgeti(prop, "options", 0) > 0) {
			Page pOptions = new Page(sResDir + "stats/options.res");

			display.showOptions(sResDir, prop, pOptions, this);

			p.modify("options", pOptions);
		}

		pMaster.append(p);

		display.initMasterPage(pMaster, prop, sResDir);
		pMaster.modify("bookmark", getBookmarkURL());

		pMaster.modify("title", sTitle);

		pMaster.write();

		int written = -1;
		try {
			written = writeResponse(baos.toByteArray(), request, response, osOut);
		} catch (final IOException ioe) {
			logger.log(Level.WARNING, "Exception writing the response back to the client " + request.getRemoteAddr(), ioe);
		}

		System.err.println("stats (" + gets("page") + "), ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

		Utils.logRequest("stats", written, request, false, System.currentTimeMillis() - lStart);

		bAuthOK = true;

		baos = null;
	}

	// ------------------------------------------------------------------------------------------------------------------------------------

	private static final String vsTotals[] = { "total", "avg", "stddev", "min", "max" };

	private static final String vsTotalsDescr[] = { "Total", "Average", "Std dev", "Min", "Max" };

	private final String buildStatistics(Page p, Properties prop) throws Exception {
		return buildStatistics(p, prop, sResDir, pgetb(prop, "dump_csv", false));
	}

	private static final double NO_DATA = -99999999999999999d;

	/**
	 * CSV-escaping
	 * 
	 * @param s
	 *            string to escape
	 * @return a string escaped for CSV
	 */
	public static final String escCSV(final String s) {
		if ((s.indexOf(' ') >= 0) || (s.indexOf(',') >= 0) || (s.indexOf('|') >= 0)) {
			return "\"" + Format.replace(s, "|", "\\|") + "\"";
		}

		return s;
	}

	private final String buildStatistics(final Page p, final Properties prop, final String resDir, final boolean bCSV) throws Exception {
		final int iPages = pgeti(prop, "pages", 0);

		final String sPageTitle = pgets(prop, "pagetitle");

		p.modify("title", sPageTitle);

		sTitle += (sTitle.length() > 0 ? ", " : "") + sPageTitle;

		final Page p1 = new Page(resDir + "stats/1.res");
		final Page p2 = new Page(resDir + "stats/2.res");
		final Page p3 = new Page(resDir + "stats/3.res");
		final Page p4 = new Page(resDir + "stats/4.res");
		final Page p5 = new Page(resDir + "stats/5.res");

		final Page p2_1 = new Page(resDir + "stats/2_1.res");
		final Page p3_1 = new Page(resDir + "stats/3_1.res");
		final Page p4_1 = new Page(resDir + "stats/4_1.res");
		final Page p4_2 = new Page(resDir + "stats/4_2.res");
		final Page p5_1 = new Page(resDir + "stats/5_1.res");

		final Page pFilter = new Page(resDir + "stats/filter.res");

		long lMinTime = pgetl(prop, "stats.mintime", 60) * 60000L;
		long lMaxTime = pgetl(prop, "stats.maxtime", 0) * 60000L;

		if (lMaxTime > lMinTime) {
			long lTemp = lMaxTime;
			lMaxTime = lMinTime;
			lMinTime = lTemp;
		}

		final StringBuilder csv = new StringBuilder();

		final long lCompactInterval = Utils.getCompactInterval(prop, lMinTime, lMaxTime);

		if (logTiming()) {
			logTiming("history parameters are: lMinTime=" + lMinTime + ", lMaxTime=" + lMaxTime + ", lCompactInteravl=" + lCompactInterval);
		}

		final Vector<monPredicate> vPreds = getIDs(prop, lMinTime, lMaxTime); // only history-related predicates

		DataSplitter ds = null;

		if (vPreds.size() > 0) {
			if (logTiming()) {
				logTiming("geting data for " + vPreds.size() + " predicates, compact interval=" + lCompactInterval);
			}

			ds = store.getDataSplitter(vPreds, lCompactInterval);

			if (logTiming()) {
				logTiming("data gathering complete, data structure is : " + ds.toString());
			}
		}
		else {
			logTiming("no history to fetch from DB");
		}

		if (ds == null) {
			ds = new DataSplitter(new Vector<TimestampedResult>(1), lMinTime, lMaxTime);
		}

		final HashMap<Integer, HashMap<String, String>> hmStrings = new HashMap<Integer, HashMap<String, String>>();

		final String sDefaultMessageFormat = pgets(prop, "default_message_format", "<a onmouseover=\"return overlib(':FULL_MESSAGE:');\" onmouseout=\"return nd();\">:CUT_MESSAGE:</a>");

		final String sAlternativeRowOdd = pgets(prop, "row_color_odd", "F5F5F5");
		final String sAlternativeRowEven = pgets(prop, "row_color_even", "FFFFFF");

		final Map<String, TimestampedResult> cacheSnapshot = Cache.getSnapshot();

		logTiming("cache snapshot size: " + cacheSnapshot.size());

		for (int i = 0; i < iPages; i++) {
			logTiming("processing sheet " + i);

			final Vector<String> v1 = toVector(prop, "pivot" + i + "_1", "pivot_1");
			final Vector<String> v2 = toVector(prop, "pivot" + i + "_2", "pivot_2");
			final Vector<String> v3 = toVector(prop, "pivot" + i + "_3", "pivot_3", pgetb(prop, "option" + i + ".process_queries", false));
			final Vector<String> descr = toVector(prop, "descr" + i, "descr");
			final Vector<String> funcDef = toVector(prop, "func" + i, "func");
			final Vector<String> vd2 = toVector(prop, "pivotdescr" + i + "_2", "pivotdescr_2");
			final Vector<FunctionDecode> func = new Vector<FunctionDecode>(funcDef.size());

			// have a fallback in case there is no title for the whole page
			if ((sTitle.length() == 0) && (v1.size() > 0)) {
				sTitle = v1.get(0);
			}

			final boolean bNumbers = pgetb(prop, "option" + i + ".numbers", false);

			final String sDefaultPageMessageFormat = pgets(prop, "option" + i + ".default_message_format", sDefaultMessageFormat);

			if ((v3.size() != descr.size()) || (v3.size() != funcDef.size())) {
				System.err.println("Size differes : " + v3.size() + "/" + descr.size() + "/" + funcDef.size());
				System.err.println("pivot 3 : " + v3.toString());
				System.err.println("descr   : " + descr.toString());
				System.err.println("func    : " + funcDef.toString());
				return null;
			}

			final Vector<String> groups = toVector(prop, "groups" + i, "groups");
			final Vector<String> groups2 = toVector(prop, "groups" + i + "_2", "groups_2");
			final Vector<String> minmax = toVector(prop, "minmax" + i, "minmax");

			final Vector<Vector<String>> vTotals = new Vector<Vector<String>>();

			for (String vsTotal : vsTotals) {
				final Vector<String> vTemp = toVector(prop, vsTotal + i, vsTotal);
				vTotals.add(vTemp);
			}

			final int descrSize = descr.size();
			final int funcSize = funcDef.size();
			final int groupsSize = groups.size();
			final int groups2Size = groups2.size();

			logTiming("loading function decoders");

			for (int j = 0; j < funcSize; j++) {
				final String s = funcDef.get(j);

				final FunctionDecode fd = new FunctionDecode(s);
				func.add(fd);
			}

			final Vector<Vector<Vector<EvalResult>>> VR = new Vector<Vector<Vector<EvalResult>>>();

			final double vdMax[] = new double[v3.size()];
			final double vdMin[] = new double[v3.size()];

			for (int j = 0; j < v3.size(); j++) {
				vdMax[j] = NO_DATA;
				vdMin[j] = NO_DATA;
			}

			logTiming("decoding min max");

			final MinMaxRec vMinMax[] = decodeMinMax(minmax, v3.size(), descr, prop);

			int offered = 0;

			final LinkedBlockingQueue<AsyncRowEval> results = new LinkedBlockingQueue<AsyncRowEval>();

			for (int j = 0; j < v1.size(); j++) {
				final String s1 = v1.get(j);

				final Vector<Vector<EvalResult>> VR1 = new Vector<Vector<EvalResult>>();
				VR.add(VR1);

				for (int k = 0; k < v2.size(); k++) {
					final String s2 = v2.get(k);

					final Vector<EvalResult> VR2 = new Vector<EvalResult>();
					VR1.add(VR2);

					final AsyncRowEval are = new AsyncRowEval(prop, lMinTime, lMaxTime, ds, hmStrings, i, v3, func, v3.size(), vdMax, vdMin, s1, s2, VR2, results, cacheSnapshot);

					if (rowProcessingQueue.offer(are)) {
						offered++;
					}
					else {
						System.err.println("stats: cannot queue a row execution because the queue is full " + rowProcessingQueue.size());
						are.run();
					}
				}
			}

			logTiming("stats: I have scheduled " + offered + " rows, waiting for them to finish");

			for (int off = 0; off < offered; off++) {
				try {
					results.take();
				} catch (InterruptedException ie) {
					System.err.println("stats: interrupted while waiting for row " + off + " / " + offered + " to be processed");
				}
			}

			logTiming("stats: Async evaluation of rows is over, now filtering");

			final List<ConditionVerifier> conditions = new ArrayList<ConditionVerifier>(v3.size());

			ConditionVerifier condNames = null;

			boolean bAnyCond = false;

			final boolean bFilterEnabled = pgetb(prop, "filtering_" + i + ".enabled", pgetb(prop, "filtering.enabled", true));

			if (bFilterEnabled) {
				final String sNames = pgets(prop, "filter_" + i);

				if (sNames.length() > 0) {
					condNames = new ConditionVerifier(sNames);
				}

				for (int cond = 0; cond < v3.size(); cond++) {
					final String s = pgets(prop, "filter_" + i + "_" + cond);

					if (s.length() > 0) {
						conditions.add(new ConditionVerifier(s));
						bAnyCond = true;
					}
					else {
						conditions.add(null);
					}
				}
			}

			final Set<String> hideRows = new HashSet<String>();

			if (bAnyCond) {
				for (int iVR = 0; iVR < VR.size(); iVR++) {
					final Vector<Vector<EvalResult>> VR1 = VR.get(iVR);

					for (int iVR1 = 0; iVR1 < VR1.size(); iVR1++) {
						final Vector<EvalResult> VR2 = VR1.get(iVR1);

						boolean ok = true;

						for (int colToTest = 0; colToTest < v3.size(); colToTest++) {
							final ConditionVerifier cv = conditions.get(colToTest);

							if (cv == null) {
								continue;
							}

							final EvalResult er = VR2.get(colToTest);

							if (!er.bData) {
								ok = cv.matches(null);
							}
							else {
								if ((er.iType == EvalResult.TYPE_NUMBER) || (er.iType == EvalResult.TYPE_BOOL) || (er.iType == EvalResult.TYPE_STATUS)) {
									if (!cv.matches(er.dRez)) {
										ok = false;
									}
								}
								else
									if (er.iType == EvalResult.TYPE_STRING) {
										if (!cv.matches(er.sRez)) {
											ok = false;
										}
									}
							}

							logTiming("Filter : " + iVR + " / " + iVR1 + " / " + colToTest + " = " + er + " : " + ok);

							if (!ok) {
								break;
							}
						}

						if (!ok) {
							hideRows.add(iVR + "_" + iVR1);
						}
					}
				}
			}

			logTiming("stats: Filtering done");

			for (int j = 0; j < v3.size(); j++) {
				if (vdMin[j] == NO_DATA) {
					vdMin[j] = 0;
				}

				if (vdMax[j] == NO_DATA) {
					vdMax[j] = 0;
				}

				final double dConfigMin = pgetd(prop, "absmin" + i + "_" + j, -Double.MAX_VALUE);
				final double dConfigMax = pgetd(prop, "absmax" + i + "_" + j, -Double.MAX_VALUE);

				if (Math.abs(dConfigMin + Double.MAX_VALUE) > 1E-10) {
					vdMin[j] = dConfigMin;
				}

				if (Math.abs(dConfigMax + Double.MAX_VALUE) > 1E-10) {
					vdMax[j] = dConfigMax;
				}
			}

			if (pgetb(prop, "option" + i + ".overall_minmax", false)) {
				double dOverallMin = NO_DATA;
				double dOverallMax = NO_DATA;

				for (int j = 0; j < v3.size(); j++) {
					if ((dOverallMin == NO_DATA) || (vdMin[j] < dOverallMin)) {
						dOverallMin = vdMin[j];
					}

					if ((dOverallMax == NO_DATA) || (vdMax[j] > dOverallMax)) {
						dOverallMax = vdMax[j];
					}
				}

				if (dOverallMin >= 0) {
					for (int j = 0; j < v3.size(); j++) {
						vdMin[j] = dOverallMin;
						vdMax[j] = dOverallMax;
					}
				}
			}

			// set the min/max values for each column color manager
			for (int l = 0; l < v3.size(); l++) {
				final MinMaxRec mmr = vMinMax[l];

				if (mmr == null) {
					continue;
				}

				if (mmr instanceof MinMaxSimple) {
					final MinMaxSimple mms = (MinMaxSimple) mmr;

					mms.setMin(vdMin[l]);
					mms.setMax(vdMax[l]);
				}

				if (mmr instanceof MinMaxComplex) {
					final MinMaxComplex mmc = (MinMaxComplex) mmr;

					mmc.setMin(vdMin[l]);
					mmc.setMax(vdMax[l]);
				}
			}

			logTiming("preparing html");

			for (int j = 0; j < v1.size(); j++) {
				logTiming("table " + j);

				final String s1 = v1.get(j);
				p1.modify("title", s1);
				p1.modify("page", gets("page"));
				p1.modify("tableno", "" + j);
				fillPageFromProperties(p1, prop);

				p1.append("<thead>");

				final String st = pgets(prop, "title" + i, pgets(prop, "title"));
				p2.modify("title", st);

				// have a fallback in case there is no title for the whole page
				if (sTitle.length() == 0) {
					sTitle = st;
				}

				if (bFilterEnabled) {
					if (pgetb(prop, "filter_" + i + ".enabled", true)) {
						final Page pFilterHead = new Page(resDir + "stats/filter_head.res");
						pFilterHead.modify("name", "filter_" + i);
						pFilterHead.modify("value", pgets(prop, "filter_" + i));
						p4.modify("value", pFilterHead);
					}

					for (int k = 0; k < descrSize; k++) {
						FunctionDecode fd = null;

						try {
							fd = func.get(k);
						} catch (Exception e) {
							// ignore
						}

						if ((fd != null) && fd.bHide) {
							continue;
						}

						if (pgetb(prop, "filter_" + i + "_" + k + ".enabled", true)) {
							pFilter.modify("value", pgets(prop, "filter_" + i + "_" + k));
							pFilter.modify("name", "filter_" + i + "_" + k);

							p4.append(pFilter);
						}
						else {
							p4.append(p4_2);
						}
					}

					p1.append(p4);
				}

				if (groupsSize > 0) {
					int last = 0;
					for (int k = 0; k < groupsSize; k++) {
						final String s = groups.get(k);

						final StringTokenizer stok = new StringTokenizer(s, " ");

						final int iCol = Integer.parseInt(stok.nextToken());
						final int iSpan = Integer.parseInt(stok.nextToken());

						final StringBuilder sb = new StringBuilder();
						while (stok.hasMoreTokens()) {
							if (sb.length() > 0) {
								sb.append(" ");
							}

							sb.append(stok.nextToken());
						}

						for (; last < iCol; last++) {
							p4.append(p4_2);
						}

						p4_1.modify("span", iSpan);
						p4_1.modify("title", sb.toString());
						p4.append(p4_1);

						last += iSpan;
					}
					p1.append(p4);
				}

				if (groups2Size > 0) {
					int last = 0;
					for (int k = 0; k < groups2Size; k++) {
						final String s = groups2.get(k);

						final StringTokenizer stok = new StringTokenizer(s, " ");

						final int iCol = Integer.parseInt(stok.nextToken());
						final int iSpan = Integer.parseInt(stok.nextToken());

						final StringBuilder sb = new StringBuilder();
						while (stok.hasMoreTokens()) {
							if (sb.length() > 0) {
								sb.append(" ");
							}

							sb.append(stok.nextToken());
						}

						for (; last < iCol; last++) {
							p4.append(p4_2);
						}

						p4_1.modify("span", iSpan);
						p4_1.modify("title", sb.toString());
						p4.append(p4_1);

						last += iSpan;
					}
					p1.append(p4);
				}

				if (bCSV) {
					csv.append('#').append(escCSV(st));
				}

				for (int k = 0; k < descrSize; k++) {
					FunctionDecode fd = null;

					try {
						fd = func.get(k);
					} catch (Exception e) {
						// ignore
					}

					p2_1.modify("value", descr.get(k));

					if (bCSV) {
						csv.append('|').append(escCSV(descr.get(k)));
					}

					if ((fd == null) || (fd.bHide == false)) {
						p2.append(p2_1);
					}
					else {
						p2_1.toString();
					}
				}

				if (bCSV) {
					csv.append("\r\n");
				}

				p1.append(p2);

				p1.append("</thead><tbody>");

				final boolean bAllData = pgetb(prop, "option" + i + ".all_data", true);

				final Vector<Vector<EvalResult>> VR1 = VR.get(j);

				int iColor = 0;

				logTiming("  headers done");

				final StringBuilder sbNames = new StringBuilder();
				final StringBuilder sbRealNames = new StringBuilder();

				final String sNamesSeparator = pgets(prop, "option" + i + ".names_separator", ",_").replace('_', ' ');

				for (int k = 0; k < v2.size(); k++) {
					if (hideRows.contains(j + "_" + k)) {
						continue;
					}

					final String s2 = v2.get(k);

					String sPrint = s2;

					if (vd2.size() > k) {
						sPrint = vd2.get(k);
					}

					sPrint = display.getDescr(prop, s2, sPrint);

					if (condNames != null) {
						if (!condNames.matches(sPrint) && !condNames.matches(s2)) {
							hideRows.add(j + "_" + k);
							continue;
						}
					}

					String sAlternate = pgets(prop, "option" + i + ".default_tooltip");

					sAlternate = pgets(prop, s2 + ".tooltip", sAlternate);
					sAlternate = pgets(prop, s2 + "_" + i + ".tooltip", sAlternate);

					sAlternate = replace(sAlternate, "$NAME", sPrint);
					sAlternate = replace(sAlternate, "$REALNAME", s2);
					sAlternate = replace(sAlternate, "$NO", "" + (iColor + 1));

					final StringBuilder csvLine = new StringBuilder();

					if (bCSV) {
						csvLine.append(sPrint);
					}

					if (bNumbers) {
						sPrint = (iColor + 1) + ". " + sPrint;

						p3.modify("sortkey", iColor);
					}
					else {
						p3.modify("sortkey", sPrint);
					}

					p3.modify("series", sPrint);
					p3.modify("realname", s2);

					if (sbNames.length() > 0) {
						sbNames.append(sNamesSeparator);
						sbRealNames.append(sNamesSeparator);
					}

					sbNames.append(sPrint);
					sbRealNames.append(s2);

					if (sAlternate.length() > 0) {
						p3.comment("com_alt", true);
						p3.modify("alternate", sAlternate);
					}
					else {
						p3.comment("com_alt", false);
					}

					p3.comment("par", (iColor % 2) == 0);
					p3.comment("impar", (iColor % 2) != 0);

					final Vector<EvalResult> VR2 = VR1.get(k);

					boolean bData = false;

					for (int l = 0; l < v3.size(); l++) {
						final EvalResult er = VR2.get(l);

						bData = bData || er.bData;

						final FunctionDecode fd = func.get(l);

						if (fd == null) {
							continue;
						}

						if (bCSV) {
							csvLine.append('|');
						}

						if ((fd.url != null) && (fd.url.length() > 0)) {
							p3_1.comment("com_url", true);

							String url = fd.url;

							url = Format.replace(url, "$REALNAME", s2);
							url = Format.replace(url, "$NAME", sPrint);
							url = Format.replace(url, "$COLUMN", "" + l);
							url = Format.replace(url, "$NO", "" + (iColor + 1));

							p3_1.modify("url", url);
						}
						else {
							p3_1.comment("com_url", false);
						}

						if (er.bData) {
							if (er.iType == EvalResult.TYPE_STRING) {
								if (bCSV) {
									csvLine.append(escCSV(er.sRez));
								}

								if ((fd.sVersion != null) && (fd.sVersion.length() > 0)) {
									final long version = getVersion(er.sRez, fd.sVersion, fd.iVersionCount);

									p3_1.modify("sorttable_realvalue", version);
									p3_1.modify("sorttable_value", version);
								}
								else {
									p3_1.modify("sorttable_realvalue", er.sRez);
									p3_1.modify("sorttable_value", er.sRez);
								}
							}
							else {
								if (bCSV) {
									csvLine.append(Utils.showDouble(er.dRez));
								}

								p3_1.modify("sorttable_realvalue", Utils.showDouble(er.dRealValue));
								p3_1.modify("sorttable_value", Utils.showDouble(er.dRez));
							}
						}

						if ((er.iType == EvalResult.TYPE_BOOL) && er.bData) {
							p3_1.modify("value", "");
							String sColor = "FF0000";
							if (er.sRez.equals("ok")) {
								sColor = "33FF77";
							}
							if (er.sRez.equals("warn")) {
								sColor = "FFE539";
							}
							if (er.sRez.equals("alternate")) {
								sColor = "E5E5E5";
							}

							p3_1.modify("color", sColor);
							p3.append(p3_1);
							continue;
						}

						if ((er.iType == EvalResult.TYPE_STATUS) && er.bData) {
							String sMessage = er.sRez;

							String sReasonCut = Formatare.stripHTML(sMessage);
							if ((fd.iReasonCut > 0) && (sReasonCut.length() > (fd.iReasonCut + 3))) {
								sReasonCut = sReasonCut.substring(0, fd.iReasonCut) + "...";
							}

							String sColor;
							boolean bCheckMessage;

							if (er.dRez > 0.5) {
								// value = 1 => ok
								sColor = "33FF77";

								bCheckMessage = fd.bForceMessage;
							}
							else {
								// value = 0 => not ok
								if (fd.bWarning && (er.dRealValue > 1.5)) {
									sColor = "FFE539";
								}
								else {
									sColor = "FF6666";
								}

								bCheckMessage = true;
							}

							if (bCheckMessage && (sMessage.length() == 0) && !fd.b0Point1 && (er.dRealValue > 0.5)) {
								sReasonCut = "" + DoubleFormat.point(er.dRealValue);

								sMessage = "Status code: " + sReasonCut;
							}

							if (sMessage.length() > 0) {
								String sMessageFormat = pgets(prop, "option" + i + "_" + l + ".message_format", sDefaultPageMessageFormat);

								if (sMessageFormat.indexOf(":FULL_MESSAGE:") >= 0) {
									sMessageFormat = replace(sMessageFormat, ":FULL_MESSAGE:", Formatare.jsEscape(sMessage));
								}

								if (sMessageFormat.indexOf(":CUT_MESSAGE:") >= 0) {
									sMessageFormat = replace(sMessageFormat, ":CUT_MESSAGE:", Formatare.tagProcess(sReasonCut));
								}

								p3_1.modify("value", sMessageFormat);
							}
							else {
								p3_1.modify("value", "");
							}

							p3_1.modify("color", sColor);
							p3.append(p3_1);
							continue;
						}

						if ((er.iType == EvalResult.TYPE_STRING) && er.bData) {
							if (vMinMax[l] != null) {
								MinMaxRec mmr = vMinMax[l];

								if (mmr instanceof MinMaxSimple) {
									mmr = ((MinMaxSimple) mmr).getStringVersion();
								}

								if (mmr instanceof MinMaxStrings) {
									MinMaxStrings mms = (MinMaxStrings) mmr;

									mms.setFD(fd);
									mms.setStrings(hmStrings.get(Integer.valueOf(l)));
								}

								p3_1.modify("color", mmr.getColor(er.sRez));
							}
						}

						String sValue = er.sRez;
						String sReasonCut = sValue;

						if (fd.bResolveIP) {
							sReasonCut = er.sResolvedIP;

							if (sReasonCut == null) {
								sReasonCut = getHostName(er.sRez);
							}
						}

						final boolean bShouldCut = (fd.iReasonCut > 0) && (sReasonCut.length() > (fd.iReasonCut + 3));

						if (!sValue.equals(sReasonCut) || bShouldCut) {
							if (bShouldCut) {
								sReasonCut = sReasonCut.substring(0, fd.iReasonCut) + "...";
							}

							String sMessageFormat = pgets(prop, "option" + i + "_" + l + ".message_format", sDefaultPageMessageFormat);

							if (sMessageFormat.indexOf(":FULL_MESSAGE:") >= 0) {
								sMessageFormat = replace(sMessageFormat, ":FULL_MESSAGE:", Formatare.jsEscape(sValue));
							}

							if (sMessageFormat.indexOf(":CUT_MESSAGE:") >= 0) {
								sMessageFormat = replace(sMessageFormat, ":CUT_MESSAGE:", Formatare.tagProcess(sReasonCut));
							}

							sValue = sMessageFormat;
						}

						p3_1.modify("value", sValue);

						if (fd.bRedIfZero && (er.dRez == 0) && er.bData) {
							p3_1.modify("color", "FF0000");
						}

						if (fd.bRedIfNoData && !er.bData) {
							p3_1.modify("color", "FF0000");
						}

						final MinMaxRec mmr = vMinMax[l];

						if (mmr != null) {
							p3_1.modify("color", mmr.getColor(Double.valueOf(er.dRez)));
						}
						else {
							if ((iColor % 2) == 0) {
								p3_1.modify("color", sAlternativeRowEven);
							}
							else {
								p3_1.modify("color", sAlternativeRowOdd);
							}
						}

						if (!fd.bHide) {
							p3.append(p3_1);
						}
						else {
							p3_1.toString();
						}
					}

					if (bData || bAllData) {
						p1.append(p3); // write the line
						if (bCSV) {
							csv.append(csvLine).append("\r\n");
						}

						iColor++;
					}
					else {
						p3.toString(); // clean the changes
					}
				}

				p1.append("</tbody><tfoot>");

				logTiming("  body done");

				for (int m = 0; m < vTotals.size(); m++) {
					Vector<String> vTemp = vTotals.get(m);

					if (vTemp.size() > 0) {
						for (int k = 0; k < v3.size(); k++) {
							FunctionDecode fd = func.get(k);

							if (vTemp.contains("" + k)) {

								double dTotal = 0;
								double dMin = NO_DATA;
								double dMax = NO_DATA;
								int iCount = 0;

								for (int l = 0; l < v2.size(); l++) {
									if (hideRows.contains(j + "_" + l)) {
										continue;
									}

									Vector<EvalResult> VR2 = VR1.get(l);
									EvalResult er = VR2.get(k);

									if ((er.dRez >= 0) || fd.bAllowNegatives) {
										dTotal += er.dRez;
										iCount++;
										dMin = ((dMin == NO_DATA) || (dMin > er.dRez)) ? er.dRez : dMin;
										dMax = ((dMax == NO_DATA) || (dMax < er.dRez)) ? er.dRez : dMax;
									}
								}

								switch (m) {
								case 1: // average
									if (iCount > 0) {
										dTotal = dTotal / iCount;
									}
									else {
										dTotal = NO_DATA;
									}
									break;
								case 2: // stddev
									if (iCount > 1) {
										dTotal = dTotal / iCount;
									}
									else {
										dTotal = NO_DATA;
										break;
									}

									double dSum = 0;

									for (int l = 0; l < v2.size(); l++) {
										Vector<EvalResult> VR2 = VR1.get(l);
										EvalResult er = VR2.get(k);
										if (er.dRez >= 0) {
											double dPatrat = (er.dRez - dTotal) * (er.dRez - dTotal);
											dSum += dPatrat;
										}
									}
									dSum /= (iCount - 1);
									dTotal = Math.sqrt(dSum);
									break;
								case 3: // min
									dTotal = dMin;
									break;
								case 4: // max
									dTotal = dMax;
									break;
								default: // 0=total, or anything else
									break;
								}

								if ((dTotal >= 0) || fd.bAllowNegatives) {
									p5_1.modify("value", convertDouble(fd, dTotal));
								}
								else {
									p5_1.modify("value", "-");
								}
							}
							else { // this cell does not contain this function
								p5_1.modify("value", "");
							}

							if (!fd.bHide) {
								p5.append(p5_1);
							}
							else {
								p5_1.toString();
							}
						}

						p5.modify("title", vsTotalsDescr[m]);

						if (pgetb(prop, "option" + i + ".use_real_names", pgetb(prop, "option.use_real_names", true))) {
							p5.modify("names", sbRealNames.toString());
						}
						else {
							p5.modify("names", sbNames.toString());
						}

						p1.append(p5);
					}
				}

				p1.append("</tfoot>");

				logTiming("  footer done");

				p.append(p1);

				logTiming("  and appended");
			}
		}
		logTiming("done");

		return bCSV ? csv.toString() : null;
	}

	private static final class AsyncRowEval implements Runnable {
		final Properties prop;
		final long lMinTime;
		final long lMaxTime;
		final DataSplitter ds;
		final HashMap<Integer, HashMap<String, String>> hmStrings;
		final int i;
		final Vector<String> v3;
		final Vector<FunctionDecode> func;
		final int v3Size;
		final double[] vdMax;
		final double[] vdMin;
		final String s1;
		final String s2;
		final Vector<EvalResult> VR2;
		final Map<String, TimestampedResult> cacheSnapshot;

		final LinkedBlockingQueue<AsyncRowEval> results;

		public AsyncRowEval(final Properties _prop, final long _lMinTime, final long _lMaxTime, final DataSplitter _ds, final HashMap<Integer, HashMap<String, String>> _hmStrings, final int _i,
				final Vector<String> _v3, final Vector<FunctionDecode> _func, final int _v3Size, final double[] _vdMax, final double[] _vdMin, final String _s1, final String _s2,
				final Vector<EvalResult> _VR2, final LinkedBlockingQueue<AsyncRowEval> _results, final Map<String, TimestampedResult> _cacheSnapshot) {

			prop = _prop;
			lMinTime = _lMinTime;
			lMaxTime = _lMaxTime;
			ds = _ds;
			hmStrings = _hmStrings;
			i = _i;
			v3 = _v3;
			func = _func;
			v3Size = _v3Size;
			vdMax = _vdMax;
			vdMin = _vdMin;
			s1 = _s1;
			s2 = _s2;
			VR2 = _VR2;
			results = _results;
			cacheSnapshot = _cacheSnapshot;
		}

		/**
		 * What to do when it's done
		 */
		public void done() {
			results.offer(this);
		}

		@Override
		public void run() {
			final Set<Integer> unsolved = new TreeSet<Integer>();
			final Set<Integer> solved = new HashSet<Integer>();

			for (int l = 0; l < v3Size; l++) {
				unsolved.add(Integer.valueOf(l));

				VR2.add(null);
			}

			int iUnsolvedSize = unsolved.size();

			while (iUnsolvedSize > 0) {
				final Iterator<Integer> itUnsolved = unsolved.iterator();

				while (itUnsolved.hasNext()) {
					final int l = (itUnsolved.next()).intValue();

					final FunctionDecode fd = func.get(l);

					// wait until the column(s) this one depends upon are evaluated
					if ((fd.sDependsOn.size() > 0) && !solved.containsAll(fd.sDependsOn)) {
						continue;
					}

					// evaluate the column, all the dependencies are met
					String sv3 = v3.get(l);

					sv3 = replace(sv3, "$1", s1);
					sv3 = replace(sv3, "$2", s2);
					sv3 = parseOption(prop, "pivot" + i + "_3", sv3, sv3, true, true);

					if (fd.sAlternate != null) {
						String sPred = fd.sAlternate;

						sPred = replace(sPred, "$1", s1);
						sPred = replace(sPred, "$2", s2);
						sPred = parseOption(prop, "alternate" + i, sPred, sPred, true, true);

						fd.mpAlternate = toPred(sPred);
					}

					if (fd.vInclude != null) {
						fd.vmpInclude = new Vector<monPredicate>();

						for (int ign = 0; ign < fd.vInclude.size(); ign++) {
							String sPred = fd.vInclude.get(ign);

							sPred = replace(sPred, "$1", s1);
							sPred = replace(sPred, "$2", s2);
							sPred = parseOption(prop, "include" + i, sPred, sPred, true);

							fd.vmpInclude.add(toPred(sPred));
						}
					}
					else {
						fd.vmpInclude = null;
					}

					if (fd.vExclude != null) {
						fd.vmpExclude = new Vector<monPredicate>();

						for (int ign = 0; ign < fd.vExclude.size(); ign++) {
							String sPred = fd.vExclude.get(ign);

							sPred = replace(sPred, "$1", s1);
							sPred = replace(sPred, "$2", s2);
							sPred = parseOption(prop, "ignore" + i, sPred, sPred, true);

							fd.vmpExclude.add(toPred(sPred));
						}
					}
					else {
						fd.vmpExclude = null;
					}

					HashMap<String, String> hmSortStrings;

					synchronized (hmStrings) {
						hmSortStrings = hmStrings.get(Integer.valueOf(l));

						if (hmSortStrings == null) {
							hmSortStrings = new HashMap<String, String>();
							hmStrings.put(Integer.valueOf(l), hmSortStrings);
						}
					}

					fd.iPageNo = i;
					fd.iSeriesNo = l;
					fd.sSeriesName = s2;

					final EvalResult er = eval(fd, ds, toPred(sv3), lMinTime, lMaxTime, hmSortStrings, prop, sv3, VR2, cacheSnapshot);

					if (fd.bResolveIP) {
						er.sResolvedIP = getHostName(er.sRez);
					}

					VR2.set(l, er);

					if (er.bData) {
						synchronized (vdMax) {
							if ((vdMax[l] == NO_DATA) || (vdMax[l] < er.dRez)) {
								vdMax[l] = er.dRez;
							}
						}

						synchronized (vdMin) {
							if ((vdMin[l] == NO_DATA) || (vdMin[l] > er.dRez)) {
								vdMin[l] = er.dRez;
							}
						}
					}

					// mark the column as solved
					itUnsolved.remove();
					solved.add(Integer.valueOf(l));
				}

				if (unsolved.size() == iUnsolvedSize) {
					// nothing more could be evaluated
					break;
				}

				iUnsolvedSize = unsolved.size();
			}

			for (int l = 0; l < v3Size; l++) {
				if (VR2.get(l) == null) {
					final EvalResult er = new EvalResult(NO_DATA, "", false);

					VR2.set(l, er);
				}
			}
		}
	}

	/**
	 * Queue of table rows to process asynchronously
	 */
	static final LinkedBlockingQueue<AsyncRowEval> rowProcessingQueue = new LinkedBlockingQueue<AsyncRowEval>();

	/**
	 * Keep track of the executors
	 */
	private static final Vector<AsyncRowExecutor> asyncExecutors = new Vector<AsyncRowExecutor>();

	/**
	 * Takes one row and evaluates its cells
	 * 
	 * @author costing
	 * @since Jul 16, 2009
	 */
	private static final class AsyncRowExecutor extends Thread {

		/**
		 * Default constructor
		 * 
		 * @param i
		 *            executor ID
		 */
		public AsyncRowExecutor(final int i) {
			super("(ML) stats.AsyncRowExecutor (" + i + ")");
			setDaemon(true);
		}

		private boolean shouldStop = false;

		@Override
		public void run() {
			while (!shouldStop) {
				try {
					final AsyncRowEval are = rowProcessingQueue.poll(60, TimeUnit.SECONDS);

					if (are != null) {
						try {
							are.run();
						} catch (Throwable t) {
							System.err.println("stats.AsyncRowExecutor : evaluation exception : " + t + " (" + t.getMessage() + ")");
							t.printStackTrace();
						}

						try {
							are.done();
						} catch (Throwable t) {
							// ignore
						}
					}
				} catch (InterruptedException ie) {
					// ignore
				}
			}
		}

		/**
		 * Signal thread stopping
		 */
		public void signalStop() {
			shouldStop = true;
		}
	}

	static {
		int cpus = Runtime.getRuntime().availableProcessors();

		if (cpus <= 1) {
			cpus = 2;
		}

		int executors = AppConfig.geti("lia.web.servlets.web.stats.executors", cpus);

		if (executors <= 0) {
			executors = 1;
		}

		System.err.println("lia.web.servlets.web.stats.executors = " + executors);

		for (int i = 0; i < executors; i++) {
			final AsyncRowExecutor executor = new AsyncRowExecutor(i);

			executor.start();

			asyncExecutors.add(executor);
		}
	}

	/**
	 * Stop the row executors.
	 */
	public static final void stopExecutors() {
		for (int i = 0; i < asyncExecutors.size(); i++) {
			AsyncRowExecutor are = asyncExecutors.get(i);

			are.signalStop();
		}

		asyncExecutors.clear();
	}

	private static final class EvalResult {

		/**
		 * The result is a number
		 */
		public static final int TYPE_NUMBER = 0;

		/**
		 * The result is a boolean field
		 */
		public static final int TYPE_BOOL = 1;

		/**
		 * The result is a string
		 */
		public static final int TYPE_STRING = 2;

		/**
		 * The result is a status field (a more complicated boolean)
		 */
		public static final int TYPE_STATUS = 3;

		/**
		 * The numberic value
		 */
		public double dRez;

		/**
		 * The string to display
		 */
		public String sRez;

		/**
		 * Whether or not this object really contains some data
		 */
		public boolean bData;

		/**
		 * What is the type of this field
		 */
		public int iType = 0;

		/**
		 * The real number value
		 */
		public double dRealValue = 0;

		/**
		 * Resolved IP address, if necessary
		 */
		public String sResolvedIP = null;

		/**
		 * Simple constructor
		 * 
		 * @param d
		 *            value
		 * @param s
		 *            display
		 * @param b
		 *            whether or not we have some value here
		 */
		public EvalResult(double d, String s, boolean b) {
			dRez = d;
			sRez = s;
			bData = b;
		}

		/**
		 * Debugging method
		 * 
		 * @return fields dump
		 */
		@Override
		public String toString() {
			return dRez + " / '" + sRez + "' / " + bData + " / " + iType;
		}

	}

	private static final String convertDouble(final FunctionDecode fd, final double dValueParam) {
		String sRez = "";

		double dValue = dValueParam;

		if (((dValue < 0) && !fd.bAllowNegatives) || (fd.bAllowNegatives && (Math.abs(dValue - NO_DATA) < 1E-10))) {
			sRez = "-";
		}
		else
			if ((Math.abs(dValue) < 1E-10) && fd.bIgnoreZero) {
				sRez = "-";
			}
			else {
				if (fd.bRound) {
					if (!fd.b0Point1) {
						dValue = Math.round(dValue);
					}
					else {
						if (dValue >= 1) {
							dValue = Math.round(dValue);
						}
						else
							if ((dValue > 0) && (dValue < 0.1)) {
								dValue = 0.1;
							}
							else {
								dValue = Math.round(dValue * 10) / 10d;
							}
					}
				}

				if (fd.bNoSize) {
					if (fd.bDot) {
						sRez = ServletExtension.showDottedInt((long) dValue);
					}
					else
						if (fd.bDDot) {
							sRez = ServletExtension.showDottedDouble(dValue, fd.iDDotDigits);
						}
						else {
							sRez = DoubleFormat.point(dValue);
						}
				}
				else {
					if (dValue > 0) {
						sRez = DoubleFormat.size(dValue, fd.sSize, fd.b1000);

						if ((fd.sPlus.length() == 0) && !sRez.endsWith("B")) {
							sRez += "B";
						}
					}
					else {
						sRez = "0";
					}
				}

				if (fd.bTime) {
					sRez = toTime(dValue);
				}

				if (fd.bTimestamp) {
					sRez = toTimestamp(dValue);
				}

				if (fd.bIP) {
					sRez = toIP(dValue);
				}

				if (((dValue > 0) || ((dValue == 0) && fd.bAlwaysShowUnit)) && (fd.sPlus.length() > 0)) {
					if (sRez.toLowerCase().endsWith("b") && fd.sPlus.toLowerCase().startsWith("b")) {
						sRez += fd.sPlus.substring(1);
					}
					else {
						sRez += fd.sPlus;
					}
				}
			}

		return sRez;
	}

	private static String toTimestamp(final double value) {
		if ((value < 0) || (value > Long.MAX_VALUE)) {
			return "-";
		}

		long lTimestamp = (long) value;

		if (lTimestamp < 2000000000) {
			lTimestamp *= 1000;
		}

		final Date d = new Date(lTimestamp);

		return showDottedDate(d) + " " + showTime(d);
	}

	private static final String toIP(double d) {
		long l = (long) d;

		String sRez = "." + (l % 256);
		l /= 256;
		sRez = "." + (l % 256) + sRez;
		l /= 256;
		sRez = "." + (l % 256) + sRez;
		l /= 256;
		sRez = l + sRez;

		return l == 0 ? "" : sRez;
	}

	private static class FunctionDecode implements Serializable {
		private static final long serialVersionUID = -1912618670392162787L;

		/**
		 * Flag used to hide a column, this is good to have a column used only for arithmetics
		 */
		boolean bHide = false;

		/**
		 * Function to use to get the data from the storage
		 */
		String sFunc = "";

		/**
		 * Extra string to append to the output
		 */
		String sPlus = "";

		/**
		 * Base unit for the size values
		 */
		String sSize = "";

		/**
		 * Function parameter
		 */
		String sParams = "";

		/**
		 * Options
		 */
		String sOpts = "";

		/**
		 * Predicate to put some alternate value in case the one requested does not exist
		 */
		String sAlternate = null;

		/**
		 * Include some other values
		 */
		Vector<String> vInclude = null;

		/**
		 * Exclude some values
		 */
		Vector<String> vExclude = null;

		/**
		 * Predicate to put some alternate value in case the one requested does not exist
		 */
		monPredicate mpAlternate = null;

		/**
		 * Include some other predicates
		 */
		Vector<monPredicate> vmpInclude = null;

		/**
		 * Exclude some predicates
		 */
		Vector<monPredicate> vmpExclude = null;

		/**
		 * Divide by 8 (bits -> bytes transformation)
		 */
		boolean bDiv8 = false;

		/**
		 * Round the values
		 */
		boolean bRound = false;

		/**
		 * If the value is exactly 0 then consider it as missing data
		 */
		boolean bIgnoreZero = false;

		/**
		 * Do not show the size for this data
		 */
		boolean bNoSize = false;

		/**
		 * Boolean field
		 */
		boolean bBoolean = false;

		/**
		 * Boolean field, type 2
		 */
		boolean bBoolean2 = false;

		/**
		 * Boolean field, type 3
		 */
		boolean bBoolean3 = false;

		/**
		 * Red if <=0 or missing, Green if >=1
		 */
		boolean bOnOff = false;

		/**
		 * Whether or not to count the fields containing zero values
		 */
		boolean bCountZero = false;

		/**
		 * Sort the values by the length of the strings (used when coloring the output)
		 */
		boolean bSortLen = false;

		/**
		 * Display a red box if the field is zero
		 */
		boolean bRedIfZero = false;

		/**
		 * Display a red box if the data is missing for this field
		 */
		boolean bRedIfNoData = false;

		/**
		 * Display the number as an integer, with groups of 3 digits separated by ','
		 */
		boolean bDot = false;

		/**
		 * Display the number with groups of 3 digits separated by ',' + the decimal part
		 */
		boolean bDDot = false;

		/**
		 * Number of decimal digits to display when ddot is enabled
		 */
		int iDDotDigits = 3;

		/**
		 * If this is a time interval, in seconds, display it accordingly
		 */
		boolean bTime = false;

		/**
		 * Show a timestamp
		 */
		boolean bTimestamp = false;

		/**
		 * If this value is an IP address, display it accordingly
		 */
		boolean bIP = false;

		/**
		 * If you want to force a division by 1000 instead of 1024
		 */
		boolean b1000 = false;

		/**
		 * Decode some value stored in the database in an internal format
		 */
		boolean bDecode = false;

		/**
		 * Put the average in case there are several values
		 */
		boolean bMultiAvg = true;

		/**
		 * Put the first value in case the data query returned several ones
		 */
		boolean bMultiFirst = false;

		/**
		 * special flag to hide small values
		 */
		boolean b0Point1 = false;

		/**
		 * If the selected string should be converted to double and considered a number
		 */
		boolean bDouble = false;

		/**
		 * If the values are missing from the memory, fall back and query the database for the last known value
		 */
		boolean bFallbackToDB = false;

		/**
		 * If the string is a software version, use special mechanism to color the output
		 */
		String sVersion = null;

		/**
		 * How many of the version fields are numeric ?
		 */
		int iVersionCount = 3;

		/**
		 * For an IP address, try to resolve it into a host name
		 */
		boolean bResolveIP = false;

		/**
		 * If this string is an email address, display it in a special way
		 */
		boolean bEmail = false;

		/**
		 * Status field
		 */
		boolean bStatus = false;

		/**
		 * Warning field
		 */
		boolean bWarning = false;

		/**
		 * Use an arbitrary factor on all numbers
		 */
		boolean bFactor = false;

		/**
		 * Factor to apply to all the number, if bFactor==true
		 */
		double dFactor = 0;

		/**
		 * For long strings, display only this many characters
		 */
		int iReasonCut = -1;

		/**
		 * Series name
		 */
		String sSeriesName = null;

		/**
		 * How many series are there
		 */
		int iSeriesNo = 0;

		/**
		 * Which table
		 */
		int iPageNo = 0;

		/**
		 * Field arithmetics
		 */
		boolean bSustractFrom = false;

		/**
		 * Field arithmetics
		 */
		double dSubstractFrom = 0;

		/**
		 * Show unit for zero values too
		 */
		boolean bAlwaysShowUnit = false;

		/**
		 * ?
		 */
		boolean bForceMessage = false;

		/**
		 * Allow negative numbers
		 */
		boolean bAllowNegatives = false;

		/**
		 * Set of columns this depends on
		 */
		HashSet<Integer> sDependsOn = new HashSet<Integer>();

		/**
		 * Url to go to when clicking on the cell
		 */
		String url = null;

		/**
		 * Parse constructor
		 * 
		 * @param sFunction
		 *            configuration option for a field
		 */
		public FunctionDecode(final String sFunction) {
			sFunc = sFunction.trim();

			if (sFunc.lastIndexOf(" ") >= 0) {
				sOpts = sFunc.substring(sFunc.lastIndexOf(" ")).trim();
				sFunc = sFunc.substring(0, sFunc.lastIndexOf(" ")).trim();
			}

			if ((sFunc.indexOf("(") >= 0) && (sFunc.indexOf(")") > sFunc.indexOf("("))) {
				sParams = sFunc.substring(sFunc.indexOf("(") + 1, sFunc.lastIndexOf(")"));
				sFunc = sFunc.substring(0, sFunc.indexOf("("));
			}

			sFunc = sFunc.trim().toLowerCase();

			if ((sFunc.equals("totalcol") || sFunc.equals("divcol")) && (sParams != null) && (sParams.length() > 0)) {
				final StringTokenizer st = new StringTokenizer(sParams, ";");

				while (st.hasMoreTokens()) {
					try {
						final int iColIdx = Integer.parseInt(st.nextToken());
						sDependsOn.add(Integer.valueOf(Math.abs(iColIdx)));
					} catch (NumberFormatException nfe) {
						// ignore
					}
				}
			}

			if (sFunc.equals("eval") && (sParams != null) && (sParams.length() > 0)) {
				final char[] p = sParams.toCharArray();

				for (int i = 0; i < (p.length - 1); i++) {
					if (p[i] == '#') {
						boolean bOk = false;
						int iCol = 0;
						i++;
						while ((i < p.length) && (p[i] >= '0') && (p[i] <= '9')) {
							iCol = (iCol * 10) + (p[i] - '0');
							bOk = true;
							i++;
						}

						if (bOk) {
							sDependsOn.add(Integer.valueOf(iCol));
						}
					}
				}
			}

			final StringTokenizer st = new StringTokenizer(sOpts, ";");

			while (st.hasMoreTokens()) {
				final String sNormal = st.nextToken();
				final String s = sNormal.toLowerCase();

				if (s.equals("b") || s.equals("k") || s.equals("m") || s.equals("g") || s.equals("t") || s.equals("p")) {
					sSize = s.toUpperCase();
				}
				else
					if (s.equals("rnd")) {
						bRound = true;
					}
					else
						if (s.equals("iz")) {
							bIgnoreZero = true;
						}
						else
							if (s.equals("ns")) {
								bNoSize = true;
							}
							else
								if (s.equals("8")) {
									bDiv8 = true;
								}
								else
									if (s.equals("bool")) {
										bBoolean = true;
									}
									else
										if (s.equals("bool2")) {
											bBoolean2 = true;
										}
										else
											if (s.equals("bool3")) {
												bBoolean3 = true;
											}
											else
												if (s.equals("onoff")) {
													bOnOff = true;
												}
												else
													if (s.equals("count0")) {
														bCountZero = true;
													}
													else
														if (s.equals("sortlen")) {
															bSortLen = true;
														}
														else
															if (s.equals("redifzero")) {
																bRedIfZero = true;
															}
															else
																if (s.equals("redifnodata")) {
																	bRedIfNoData = true;
																}
																else
																	if (s.equals("dot")) {
																		bDot = true;
																	}
																	else
																		if (s.startsWith("ddot")) {
																			bDDot = true;

																			if (s.length() > 4) {
																				try {
																					iDDotDigits = Integer.parseInt(s.substring(4));

																					if (iDDotDigits < 0) {
																						iDDotDigits = 0;
																					}
																				} catch (NumberFormatException nfe) {
																					// ignore
																				}
																			}
																		}
																		else
																			if (s.equals("time")) {
																				bTime = true;
																			}
																			else
																				if (s.equals("timestamp")) {
																					bTimestamp = true;
																				}
																				else
																					if (s.equals("ip")) {
																						bIP = true;
																					}
																					else
																						if (s.equals("1000")) {
																							b1000 = true;
																						}
																						else
																							if (s.equals("decode")) {
																								bDecode = true;
																							}
																							else
																								if (s.equals("multi_avg")) {
																									bMultiAvg = true;
																									bMultiFirst = false;
																								}
																								else
																									if (s.equals("multi_sum")) {
																										bMultiAvg = false;
																										bMultiFirst = false;
																									}
																									else
																										if (s.equals("multi_first")) {
																											bMultiFirst = true;
																											bMultiAvg = false;
																										}
																										else
																											if (s.equals("double")) {
																												bDouble = true;
																											}
																											else
																												if (s.equals("fallbacktodb")) {
																													bFallbackToDB = true;
																												}
																												else
																													if (s.equals("zeropointone")) {
																														b0Point1 = true;
																													}
																													else
																														if (s.equals("allow_negatives")) {
																															bAllowNegatives = true;
																														}
																														else
																															if (s.startsWith("alternate=")) {
																																sAlternate = sNormal.substring("alternate=".length());

																																// System.err.println("FD : Alternate predicate is : '"+sAlternate+"'");
																															}
																															else
																																if (s.startsWith("include=")) {
																																	if (vInclude == null) {
																																		vInclude = new Vector<String>();
																																	}

																																	vInclude.add(sNormal.substring("include=".length()));
																																}
																																else
																																	if (s.startsWith("exclude=")) {
																																		if (vExclude == null) {
																																			vExclude = new Vector<String>();
																																		}

																																		vExclude.add(sNormal.substring("exclude=".length()));
																																	}
																																	else
																																		if (s.startsWith("version=")) {
																																			sVersion = sNormal.substring("version=".length());
																																		}
																																		else
																																			if (s.startsWith("version_count=")) {
																																				try {
																																					iVersionCount = Integer.parseInt(sNormal
																																							.substring("version_count=".length()));
																																				} catch (Exception e) {
																																					iVersionCount = 3; // default if wrongly specified
																																				}
																																			}
																																			else
																																				if (s.startsWith("reason_cut=")) {
																																					try {
																																						iReasonCut = Integer.parseInt(sNormal
																																								.substring("reason_cut=".length()));
																																					} catch (Exception e) {
																																						iReasonCut = 4; // default if wrongly specified
																																					}
																																				}
																																				else
																																					if (s.startsWith("resolveip")) {
																																						bResolveIP = true;
																																					}
																																					else
																																						if (s.equals("email")) {
																																							bEmail = true;
																																						}
																																						else
																																							if (s.equals("status")) {
																																								bStatus = true;
																																								if (iReasonCut < 0) {
																																									iReasonCut = 4;
																																								}
																																							}
																																							else
																																								if (s.startsWith("factor=")) {
																																									try {
																																										dFactor = Double.parseDouble(
																																												sNormal.substring(
																																														"factor="
																																																.length()));
																																										bFactor = Math.abs(
																																												dFactor - 1) > 1E-10;
																																									} catch (Exception e) {
																																										// ignore
																																									}
																																								}
																																								else
																																									if (s.startsWith("divide=")) {
																																										try {
																																											dFactor = 1d
																																													/ Double.parseDouble(
																																															sNormal.substring(
																																																	"divide="
																																																			.length()));
																																											bFactor = Math.abs(dFactor
																																													- 1) > 1E-10;
																																										} catch (Exception e) {
																																											// ignore
																																										}
																																									}
																																									else
																																										if (s.startsWith(
																																												"substract_from=")) {
																																											try {
																																												dSubstractFrom = Double
																																														.parseDouble(
																																																sNormal.substring(
																																																		"substract_from="
																																																				.length()));
																																												bSustractFrom = true;
																																											} catch (Exception e) {
																																												bSustractFrom = false;
																																											}
																																										}
																																										else
																																											if (s.equals("warning")) {
																																												bWarning = true;
																																											}
																																											else
																																												if (s.equals("hide")) {
																																													bHide = true;
																																												}
																																												else
																																													if (s.equals(
																																															"always_show_unit")) {
																																														bAlwaysShowUnit = true;
																																													}
																																													else
																																														if (s.equals(
																																																"force_message")) {
																																															bForceMessage = true;
																																														}
																																														else
																																															if (s.startsWith(
																																																	"url=")) {
																																																url = sNormal
																																																		.substring(
																																																				4);
																																															}
																																															else {
																																																sPlus += sNormal;
																																															}
			}

			sPlus = sPlus.replace('_', ' ');
		}
	}

	private static final String[] passParameters = new String[] { "compact.min_interval", "history.integrate.timebase", "default.measurement_interval", "totalperminute", "datainbits", "areachart",
			"compact.displaypoints.areachart", "compact.displaypoints", "compact.disable" };

	private static HashMap<String, String> setProperties(final Properties prop, final int iSeriesNo) {
		final HashMap<String, String> hmOld = new HashMap<String, String>();

		// System.err.println("setProperties("+iSeriesNo+")");

		for (final String sParam : passParameters) {
			final String sVal = prop.getProperty(sParam);

			if (sVal != null) {
				hmOld.put(sParam, sVal);
			}

			final String sValNew = prop.getProperty(iSeriesNo + "." + sParam);

			// System.err.println(sParam+": old="+sVal+", new="+sValNew);

			if (sValNew != null) {
				prop.setProperty(sParam, sValNew);
			}
		}

		// System.err.println("Returning: "+hmOld);

		return hmOld;
	}

	private static void restoreProperties(final Properties prop, final HashMap<String, String> hmOld) {
		// System.err.println("Restoring: "+hmOld);

		for (final String sParam : passParameters) {
			prop.remove(sParam);

			final String sValOld = hmOld.get(sParam);

			if (sValOld != null) {
				prop.setProperty(sParam, sValOld);
			}
		}
	}

	private static final AtomicLong evalSequence = new AtomicLong();

	/**
	 * Evaluate one cell
	 * 
	 * @param fd
	 * @param ds
	 * @param pred
	 * @param lMinTime
	 * @param lMaxTime
	 * @param hmSortStrings
	 * @param prop
	 * @param sv3
	 * @param vPrevValues
	 * @param cacheSnapshot
	 * @return cell value
	 */
	static final EvalResult eval(final FunctionDecode fd, final DataSplitter ds, final monPredicate pred, final long lMinTime, final long lMaxTime, final HashMap<String, String> hmSortStrings,
			final Properties prop, final String sv3, final Vector<EvalResult> vPrevValues, final Map<String, TimestampedResult> cacheSnapshot) {
		double dValue = 0;

		if (fd.sFunc.equals("string")) {
			final String sKey = "temporary_property_" + evalSequence.incrementAndGet();

			prop.setProperty(sKey, sv3);

			String sVal = pgets(prop, sKey, null);

			prop.remove(sKey);

			if (fd.bDecode && (sVal != null)) {
				Object o = Writer.deserializeFromString(sVal);
				if (o != null) {
					sVal = o.toString();
				}
				else {
					sVal = "";
				}
			}

			if (fd.bEmail) {
				sVal = "<a href=\"mailto:" + Formatare.extractMailAddress(sVal) + "\">" + Formatare.extractMailName(sVal) + "</a>";
			}

			if (fd.bDouble) {
				// convert to double then let the rest of the formatting to take place
				try {
					dValue = Double.parseDouble(sVal);
				} catch (Exception e) {
					dValue = NO_DATA;
				}
			}
			else {
				boolean bData = sVal != null;

				if ((sVal != null) && (sVal.length() == 0) && fd.bIgnoreZero) {
					bData = false;
				}

				EvalResult er = new EvalResult(sVal != null ? 1 : 0, sVal, bData);
				er.iType = EvalResult.TYPE_STRING;

				synchronized (hmSortStrings) {
					hmSortStrings.put(sVal, "");
				}

				return er;
			}
		}

		if (fd.sFunc.equals("totalcol")) {
			dValue = 0;

			boolean bData = false;

			Vector<String> vsCols = new Vector<String>();

			if (fd.sParams != null) {
				final StringTokenizer st = new StringTokenizer(fd.sParams, ";");

				while (st.hasMoreTokens()) {
					final String s = st.nextToken();

					if (s.length() > 0) {
						vsCols.add(s);
					}
				}
			}

			for (int i = 0; i < vPrevValues.size(); i++) {
				if ((vsCols.size() == 0) || vsCols.contains("" + i) || vsCols.contains("-" + i)) {
					final EvalResult erTemp = vPrevValues.get(i);

					if (erTemp.bData && (erTemp.iType == EvalResult.TYPE_NUMBER)) {
						if (vsCols.contains("-" + i)) {
							dValue -= erTemp.dRez;
						}
						else {
							dValue += erTemp.dRez;
						}

						bData = true;
					}
				}
			}

			if (!bData) {
				dValue = NO_DATA;
			}
		}

		if (fd.sFunc.equals("divcol")) {
			double dVal1;
			double dVal2;

			if (fd.sParams != null) {
				final StringTokenizer st = new StringTokenizer(fd.sParams, ";");

				try {
					final String s1 = st.nextToken();
					final String s2 = st.nextToken();

					EvalResult erTemp = vPrevValues.get(Integer.parseInt(s1));

					if (erTemp.bData && (erTemp.iType == EvalResult.TYPE_NUMBER)) {
						dVal1 = erTemp.dRez;
					}
					else {
						dVal1 = 0;
					}

					erTemp = vPrevValues.get(Integer.parseInt(s2));

					if (erTemp.bData && (erTemp.iType == EvalResult.TYPE_NUMBER)) {
						dVal2 = erTemp.dRez;
					}
					else {
						dVal2 = 0;
					}

					if (dVal2 > 1E-10) {
						dValue = dVal1 / dVal2;
					}
					else {
						dValue = NO_DATA;
					}
				} catch (Exception e) {
					// System.err.println("Exception doing divcol : "+e + "("+e.getMessage()+")");
					dValue = NO_DATA;
				}
			}
		}

		if (fd.sFunc.equals("eval")) {
			dValue = evalJEP(fd.sParams, vPrevValues);
		}

		if (fd.sFunc.equals("last")) {
			dValue = evalLast(pred, fd, ds, cacheSnapshot);
		}

		if (fd.sFunc.equals("avg")) {
			final Vector<TimestampedResult> v = ds.get(pred);
			dValue = evalAvg(Utils.toResultVector(v), fd.sParams);
		}

		if (fd.sFunc.equals("sum")) {
			final Vector<TimestampedResult> v = ds.get(pred);
			dValue = evalSum(Utils.toResultVector(v), fd.sParams);
		}

		if (fd.sFunc.equals("int")) {
			final Vector<TimestampedResult> v = ds.get(pred);

			final Vector<Result> v2;

			synchronized (prop) {
				final HashMap<String, String> hmOld = setProperties(prop, fd.iSeriesNo);

				v2 = Utils.integrateSeries(Utils.toResultVector(v), prop, false, lMinTime, lMaxTime);

				restoreProperties(prop, hmOld);
			}

			if ((v2 != null) && (v2.size() > 0)) {
				dValue = v2.lastElement().param[0];
			}
			else {
				dValue = NO_DATA;
			}
		}

		if (fd.bDiv8) {
			dValue /= 8;
		}

		if (dValue >= 0) {
			if (fd.bFactor) {
				dValue *= fd.dFactor;
			}

			if (fd.bSustractFrom) {
				dValue = fd.dSubstractFrom - dValue;
			}
		}

		String sRez = "";

		if (((dValue < 0) && !fd.bAllowNegatives) || (fd.bAllowNegatives && (Math.abs(dValue - NO_DATA) < 1E-10))) {
			sRez = "-";
			dValue = NO_DATA;
		}
		else
			if ((Math.abs(dValue) < 1E-10) && fd.bIgnoreZero) {
				dValue = NO_DATA;
				sRez = "-";
			}
			else {
				if (fd.bRound) {
					if (!fd.b0Point1) {
						dValue = Math.round(dValue);
					}
					else {
						if (dValue >= 1) {
							dValue = Math.round(dValue);
						}
						else
							if ((dValue > 0) && (dValue < 0.1)) {
								dValue = 0.1;
							}
							else {
								dValue = Math.round(dValue * 10) / 10d;
							}
					}
				}

				if (fd.bNoSize) {
					if (fd.bDot) {
						sRez = ServletExtension.showDottedInt((long) dValue);
					}
					else
						if (fd.bDDot) {
							sRez = ServletExtension.showDottedDouble(dValue, fd.iDDotDigits);
						}
						else {
							sRez = DoubleFormat.point(dValue);
						}
				}
				else {
					if (dValue > 0) {
						sRez = DoubleFormat.size(dValue, fd.sSize, fd.b1000);

						if ((fd.sPlus.length() == 0) && !sRez.endsWith("B")) {
							sRez += "B";
						}
					}
					else {
						sRez = "0";
					}
				}

				if (((dValue > 0) || ((dValue == 0) && fd.bAlwaysShowUnit)) && (fd.sPlus.length() > 0)) {
					if (sRez.toLowerCase().endsWith("b") && fd.sPlus.toLowerCase().startsWith("b")) {
						sRez += fd.sPlus.substring(1);
					}
					else {
						sRez += fd.sPlus;
					}
				}
			}

		final double dRealValue = dValue;

		if (fd.bBoolean) {
			if (dValue < 0) {
				if (dValue > -1.5) {
					sRez = "err";
				}
				else {
					sRez = "alternate";
				}

				dValue = 0;
			}
			else {
				dValue = 1;
				sRez = "ok";
			}
		}

		if (fd.bBoolean2) {
			if (dValue < 0) {
				if (dValue > -1.5) {
					sRez = "err";
				}
				else {
					sRez = "alternate";
				}

				dValue = 0;
			}
			else
				if (dValue == 0) {
					if (fd.bCountZero) {
						dValue = 1; // in special cases we need to count the 0-producing nodes as active
					}
					// nodes ...
					sRez = "warn";
				}
				else {
					dValue = 1; // count only the nodes with some data and where the value>0
					sRez = "ok";
				}
		}

		// System.err.println("sRez = "+sRez);

		if (fd.bTime) {
			sRez = toTime(dValue);
		}

		if (fd.bTimestamp) {
			sRez = toTimestamp(dValue);
		}

		if (fd.bIP) {
			sRez = toIP(dValue);
		}

		if (fd.bStatus && (dValue > -1E-20)) {
			sRez = "";

			final boolean bSetMessage = (dValue > 0.1) || fd.bForceMessage;

			if (dValue > 0.1) {
				dValue = 0;
			}
			else {
				dValue = 1;
			}

			if (bSetMessage) {
				String sParam = null;

				if ((pred.parameters != null) && (pred.parameters.length > 0)) {
					sParam = pred.parameters[0];
				}

				if ((sParam == null) || sParam.equals("*") || sParam.equals("%") || sParam.equals("Status")) {
					sParam = "Message";
				}
				else
					if (sParam.indexOf("Status") >= 0) {
						sParam = Formatare.replace(sParam, "Status", "Message");
					}
					else {
						sParam = sParam + "Message";
					}

				final monPredicate pTemp = new monPredicate(pred.Farm, pred.Cluster, pred.Node, -1, -1, new String[] { sParam }, null);

				final TimestampedResult o = Cache.getObjectFromHash(cacheSnapshot, pTemp, null, false);

				if ((o != null) && (o instanceof eResult) && (((eResult) o).param.length > 0)) {
					sRez = ((eResult) o).param[0].toString();
				}
			}
		}

		if (fd.bBoolean3) {
			if (dValue < 0) {
				sRez = pgets(prop, fd.iPageNo + "_" + fd.iSeriesNo + ".alternative_down", "", false);

				sRez = Formatare.replace(sRez, "$2", fd.sSeriesName);

				final String sKey = "temporary_property_" + evalSequence.incrementAndGet();

				prop.setProperty(sKey, sRez);

				sRez = pgets(prop, sKey, null);

				prop.remove(sKey);

				dValue = 0;
			}
			else {
				sRez = "";
				dValue = 1;
			}
		}

		if (fd.bOnOff) {
			if ((dValue < 0) || (Math.abs(dValue) < 1E-10)) {
				sRez = "err";

				if (fd.bCountZero) {
					dValue = 1;
				}
				else {
					dValue = 0;
				}
			}
			else {
				sRez = "ok";
				dValue = 1;
			}
		}

		EvalResult er = new EvalResult(dValue, sRez, !sRez.equals("-") || (dValue > 1E-10) || ((dValue >= 0) && fd.bIgnoreZero));
		er.iType = (fd.bBoolean || fd.bBoolean2 || fd.bOnOff) ? EvalResult.TYPE_BOOL : EvalResult.TYPE_NUMBER;

		if (fd.bStatus || fd.bBoolean3) {
			er.iType = EvalResult.TYPE_STATUS;
			er.bData = dValue > -1E-20;
		}

		er.dRealValue = dRealValue;

		return er;
	}

	/**
	 * @param sParams
	 * @param vPrevValues
	 * @return
	 */
	private static double evalJEP(final String sParams, final Vector<EvalResult> vPrevValues) {
		String sExpr = sParams;

		final char[] p = sParams.toCharArray();

		final ArrayList<Integer> alColumns = new ArrayList<Integer>();

		for (int i = 0; i < (p.length - 1); i++) {
			if (p[i] == '#') {
				boolean bOk = false;
				int iCol = 0;
				i++;
				while ((i < p.length) && (p[i] >= '0') && (p[i] <= '9')) {
					iCol = (iCol * 10) + (p[i] - '0');
					bOk = true;
					i++;
				}

				if (bOk) {
					alColumns.add(Integer.valueOf(iCol));
				}
			}
		}

		Collections.sort(alColumns);

		// apply them in reverse order to avoid prefixed strings problems

		for (int i = alColumns.size() - 1; i >= 0; i--) {
			final int iCol = alColumns.get(i).intValue();

			try {
				final EvalResult er = vPrevValues.get(iCol);

				if ((er == null) || !er.bData) {
					return NO_DATA;
				}

				sExpr = Format.replace(sExpr, "#" + iCol, "" + er.dRez);
			} catch (Exception e) {
				// System.err.println(e+" ("+e.getMessage());
				// e.printStackTrace();
				// error parsing, too bad
				return NO_DATA;
			}
		}

		try {
			return Double.parseDouble(JEPHelper.evaluateExpression(sExpr));
		} catch (Exception e) {
			// ignore, fatal
			// System.err.println(e+" ("+e.getMessage()+")");
			// e.printStackTrace();
		}

		return NO_DATA;
	}

	/**
	 * Show a time interval
	 * 
	 * @param dValue
	 * @return time interval in human format
	 */
	public static final String toTime(final double dValue) {
		String sRez = null;

		if (dValue < 0) {
			sRez = "-";
		}
		else {
			long l = (long) dValue;

			l /= 60;
			long m = l % 60;
			l /= 60;
			long h = l % 24;
			l /= 24;
			long d = l;

			if (d > 0) {
				sRez = d + "d " + h + ":" + showZero(m);
			}
			else
				if (h > 0) {
					sRez = h + ":" + showZero(m);
				}
				else {
					sRez = m + "min";
				}
		}

		return sRez;
	}

	private static final String showZero(long i) {
		return (((i < 10) && (i >= 0)) ? "0" : "") + i;
	}

	private static List<TimestampedResult> filterByTime(final List<TimestampedResult> c, final monPredicate pred) {
		return pred.tmin != 0 ? Cache.filterByTime(c, pred) : c;
	}

	/**
	 * Convert a string into a time interval
	 * 
	 * @param sTimeParam
	 *            nice time specification (5m, 1h ...)
	 * @return the interval, in millis
	 */
	public static long toInterval(final String sTimeParam) {
		if ((sTimeParam == null) || (sTimeParam.length() == 0)) {
			return 0;
		}

		double dInterval = 0;

		String sTime = sTimeParam.toLowerCase();

		String sUnit = "";

		// System.err.println("--- toInterval("+sTime+")");

		for (int i = sTime.length() - 1; i >= 0; i--) {
			char c = sTime.charAt(i);

			if ((c >= 'a') && (c <= 'z')) {
				sUnit = c + sUnit;
				sTime = sTime.substring(0, sTime.length() - 1);
			}
		}

		sUnit = sUnit.trim();
		sTime = sTime.trim();

		// System.err.println("--- toInterval("+sTime+", "+sUnit+")");

		try {
			dInterval = Double.parseDouble(sTime);

			if (sUnit.equals("s")) {
				dInterval *= 1000d;
			}
			else
				if (sUnit.equals("m")) {
					dInterval *= 60 * 1000d;
				}
				else
					if (sUnit.equals("h")) {
						dInterval *= 60 * 60 * 1000d;
					}
					else
						if (sUnit.equals("D")) {
							dInterval *= 24 * 60 * 60 * 1000d;
						}
						else
							if (sUnit.equals("M")) {
								dInterval *= 30 * 24 * 60 * 60 * 1000d;
							}
							else
								if (sUnit.equals("Y")) {
									dInterval *= 365 * 24 * 60 * 60 * 1000d;
								}

			// System.err.println("--- toInterval returning "+dInterval);

			return (long) dInterval;
		} catch (Exception e) {
			// System.err.println("--- toInterval exception : "+e+"("+e.getMessage()+")");
			return 0;
		}
	}

	private static final double evalLast(final monPredicate pred, final FunctionDecode fd, final DataSplitter ds, final Map<String, TimestampedResult> cacheSnapshot) {
		pred.tmin = -1 * toInterval(fd.sParams);
		pred.tmax = -1;

		final List<TimestampedResult> v = filterByTime(Cache.getObjectsFromHash(cacheSnapshot, pred, null, false), pred);

		// include all extra values
		if (fd.vmpInclude != null) {
			for (int i = 0; i < fd.vmpInclude.size(); i++) {
				v.addAll(filterByTime(Cache.getObjectsFromHash(cacheSnapshot, fd.vmpInclude.get(i), null, false), pred));
			}
		}

		if ((v.size() == 0) && fd.bFallbackToDB) {
			// if no value was found in memory try the database for the main predicate
			Vector<TimestampedResult> vTemp = ds.get(pred);

			if ((vTemp != null) && (vTemp.size() > 0)) {
				final ArrayList<TimestampedResult> al = new ArrayList<TimestampedResult>(1);
				al.add(vTemp.lastElement());
				v.addAll(filterByTime(al, pred));
			}

			// and its alternatives
			if (fd.vmpInclude != null) {
				for (int i = 0; i < fd.vmpInclude.size(); i++) {
					vTemp = ds.get(fd.vmpInclude.get(i));

					if ((vTemp != null) && (vTemp.size() > 0)) {
						final ArrayList<TimestampedResult> al = new ArrayList<TimestampedResult>(1);
						al.add(vTemp.lastElement());
						v.addAll(filterByTime(al, pred));
					}
				}
			}
		}

		// exclude objects that are not Result instances
		Iterator<?> it = v.iterator();
		while (it.hasNext()) {
			final Object o = it.next();

			if ((o == null) || !(o instanceof Result)) {
				it.remove();
			}
		}

		// exclude explicitly specified values
		if (fd.vmpExclude != null) {
			for (int i = 0; i < fd.vmpExclude.size(); i++) {
				final monPredicate mpExclude = fd.vmpExclude.get(i);

				it = v.iterator();

				while (it.hasNext()) {
					final Result r = (Result) it.next();

					if (DataSelect.matchResult(r, mpExclude) != null) {
						it.remove();
					}
				}
			}
		}

		// remove duplicate values

		if (v.size() > 1) {
			it = v.iterator();
			final Set<String> ids = new HashSet<String>(v.size());

			while (it.hasNext()) {
				final String sKey = IDGenerator.generateKey(it.next(), 0);

				if ((sKey == null) || ids.contains(sKey)) {
					it.remove();
				}
				else {
					ids.add(sKey);
				}
			}
		}

		if (v.size() == 0) {
			if (fd.mpAlternate == null) {
				return NO_DATA;
			}

			if (filterByTime(Cache.getObjectsFromHash(cacheSnapshot, fd.mpAlternate, null, false), pred).size() == 0) {
				// System.err.println("No alternate data, returning -1");
				return NO_DATA;
			}

			// System.err.println("Some alternate data, returning -2");
			return -2;
		}

		double dVal = 0;

		int iCount = 0;

		for (int i = 0; i < v.size(); i++) {
			final Object o = v.get(i);

			if ((o != null) && (o instanceof Result)) {
				dVal += ((Result) o).param[0];
				iCount++;

				if (fd.bMultiFirst) {
					break;
				}
			}
		}

		if (fd.bMultiAvg && (iCount > 0)) {
			dVal /= iCount;
		}

		return dVal;
	}

	private static final double evalAvg(final Vector<Result> v, final String sOpts) {
		if ((v == null) || (v.size() <= 0)) {
			return NO_DATA;
		}

		double dSum = 0;
		long lCnt = 0;

		for (int i = v.size() - 1; i >= 0; i--) {
			try {
				dSum += v.get(i).param[0];
				lCnt++;
			} catch (Exception e) {
				System.err.println("EXCEPTION : " + e + " (" + e.getMessage() + ") at element " + i + "/" + v.size() + ": " + v.get(i));
				continue;
			}
		}

		if (lCnt > 0) {
			return dSum / lCnt;
		}

		return NO_DATA;
	}

	private static final double evalSum(final Vector<Result> v, final String sOpts) {
		if ((v == null) || (v.size() <= 0)) {
			return NO_DATA;
		}

		double dSum = 0;
		for (int i = v.size() - 1; i >= 0; i--) {
			try {
				dSum += v.get(i).param[0];
			} catch (Exception e) {
				System.err.println("EXCEPTION : " + e + " (" + e.getMessage() + ") at element " + i + "/" + v.size() + ": " + v.get(i));
				continue;
			}
		}

		return dSum;
	}

	/**
	 * How much time to allow this servlet to produce the output ?
	 * 
	 * @return answer : 10 minutes
	 */
	@Override
	protected int getMaxRunTime() {
		return 600;
	}

	private static interface MinMaxRec {

		/**
		 * Get the color for a given object
		 * 
		 * @param o
		 * @return the color in hex string format
		 */
		public String getColor(Object o);

	}

	private static final class MinMaxStrings implements MinMaxRec, Comparator<String>, Serializable {
		private static final long serialVersionUID = 1L;

		private int iColorsMin[];

		private int iColorsMax[];

		private final String sColorNoData;

		private final String sColorSingleData;

		private FunctionDecode fd = null;

		private HashMap<String, String> hmStrings = null;

		/**
		 * @param sMin
		 * @param sMax
		 * @param sSingleData
		 * @param sNoData
		 */
		public MinMaxStrings(final String sMin, final String sMax, final String sSingleData, final String sNoData) {
			iColorsMin = getRGB(sMin);

			if (iColorsMin == null) {
				iColorsMin = new int[] { 255, 255, 255 };
			}

			iColorsMax = getRGB(sMax);

			if (iColorsMax == null) {
				iColorsMax = new int[] { 128, 128, 128 };
			}

			sColorSingleData = sSingleData != null ? sSingleData : Utils.toHex(iColorsMin);
			sColorNoData = sNoData != null ? sNoData : "FFFFFF";
		}

		/**
		 * @param decode
		 */
		public void setFD(final FunctionDecode decode) {
			fd = decode;
		}

		/**
		 * @param strings
		 */
		public void setStrings(final HashMap<String, String> strings) {
			hmStrings = strings;
		}

		@Override
		public String getColor(final Object o) {
			if ((o == null) || !(o instanceof String)) {
				return sColorNoData;
			}

			final String s = (String) o;

			if ((hmStrings == null) || (hmStrings.size() == 0) || (s.length() == 0)) {
				// return toHex(iColorsMin[0])+toHex(iColorsMin[1])+toHex(iColorsMin[2]);
				return sColorNoData;
			}

			if (hmStrings.size() == 1) {
				return sColorSingleData;
			}

			Vector<String> v = new Vector<String>();
			v.addAll(hmStrings.keySet());

			Collections.sort(v, this);

			Vector<String> vClase = new Vector<String>();

			String so = v.get(0);

			vClase.add(so);

			for (int j = 1; j < v.size(); j++) {
				String st = v.get(j);

				if (compare(so, st) != 0) {
					vClase.add(st);
					so = st;
				}
			}

			for (int j = 0; j < vClase.size(); j++) {
				String sv = vClase.get(j);

				if (compare(sv, s) == 0) {
					StringBuilder sbRez = new StringBuilder(6);

					for (int i = 0; i < 3; i++) {
						int iColor;

						if (iColorsMax[i] == iColorsMin[i]) {
							iColor = iColorsMax[i];
						}
						else {
							iColor = (int) ((j / ((double) vClase.size() - 1)) * (iColorsMax[i] - iColorsMin[i])) + iColorsMin[i];
						}

						sbRez.append(Utils.toHex(iColor));
					}

					return sbRez.toString();
				}
			}

			// System.err.println("Not found, returning default");

			return Utils.toHex(iColorsMin);
		}

		@Override
		public int compare(final String s1, final String s2) {
			if ((fd != null) && (fd.sVersion != null) && (fd.sVersion.length() > 0)) {
				final long lV1 = getVersion(s1, fd.sVersion, fd.iVersionCount);
				final long lV2 = getVersion(s2, fd.sVersion, fd.iVersionCount);

				if (lV1 > lV2) {
					return 1;
				}
				if (lV1 < lV2) {
					return -1;
				}
				return 0;
			}

			if ((fd == null) || (fd.bSortLen == false) || (s1.length() == s2.length())) {
				return s1.compareTo(s2);
			}

			if (s1.length() > s2.length()) {
				return 1;
			}

			return -1;
		}

	}

	private static final class MinMaxSimple implements MinMaxRec {
		private int iColorsMin[];

		private int iColorsMax[];

		/**
		 * Color to use when there is no data to display
		 */
		public String sColorNoData;

		/**
		 * Color to use when there is a single unique value
		 */
		public String sColorSingleData;

		/**
		 * Constructor
		 * 
		 * @param sMin
		 *            color for the smallest value
		 * @param sMax
		 *            color for the largest value
		 * @param sSingleData
		 * @param sNoData
		 */
		public MinMaxSimple(final String sMin, final String sMax, final String sSingleData, final String sNoData) {
			iColorsMin = getRGB(sMin);

			if (iColorsMin == null) {
				iColorsMin = new int[] { 255, 255, 255 };
			}

			iColorsMax = getRGB(sMax);

			if (iColorsMax == null) {
				iColorsMax = new int[] { 128, 128, 128 };
			}

			sColorSingleData = sSingleData != null ? sSingleData : Utils.toHex(iColorsMin);
			sColorNoData = sNoData != null ? sNoData : "FFFFFF";
		}

		/**
		 * Migration function
		 * 
		 * @return the Strings colorer
		 */
		public MinMaxStrings getStringVersion() {
			return new MinMaxStrings(Utils.toHex(iColorsMin), Utils.toHex(iColorsMax), sColorSingleData, sColorNoData);
		}

		private double dMinSet = 0;
		private double dMaxSet = 0;

		@Override
		public final String getColor(final Object o) {
			if ((o == null) || !(o instanceof Number)) {
				// System.err.println("Returning color for no data ("+sColorNoData+") because parameter is not a number");

				return sColorNoData;
			}

			double dValParam = ((Number) o).doubleValue();

			if (dValParam == NO_DATA) {
				// System.err.println("Returning color for no data because value is NO_DATA ("+sColorNoData+")");

				return sColorNoData; // white for unknown values
			}

			if (dMaxSet <= dMinSet) {
				// System.err.println("Returning single value ("+sColorSingleData+") because "+dMaxSet+"<="+dMinSet);

				return sColorSingleData;
			}

			if (dValParam <= dMinSet) {
				// System.err.println("Returning min value ("+Utils.toHex(iColorsMax)+") because "+dValParam+"<="+dMinSet);

				return Utils.toHex(iColorsMin);
			}

			if (dValParam >= dMaxSet) {
				// System.err.println("Returning max value ("+Utils.toHex(iColorsMax)+") because "+dValParam+">="+dMaxSet);

				return Utils.toHex(iColorsMax);
			}

			double dMax = dMaxSet;
			double dMin = dMinSet;
			double dVal = dValParam;

			while ((dMax - dMin) > 5) {
				dMax /= 2;
				dMin /= 2;
				dVal /= 2;
			}

			StringBuilder sbRez = new StringBuilder(6);

			for (int i = 0; i < 3; i++) {
				int iColor;

				if (iColorsMax[i] == iColorsMin[i]) {
					iColor = iColorsMax[i];
				}
				else {
					iColor = (int) (((dVal - dMin) / (dMax - dMin)) * (iColorsMax[i] - iColorsMin[i])) + iColorsMin[i];
				}

				sbRez.append(Utils.toHex(iColor));
			}

			return sbRez.toString();
		}

		/**
		 * @param max
		 */
		public void setMax(double max) {
			dMaxSet = max;
		}

		/**
		 * @param min
		 */
		public void setMin(double min) {
			dMinSet = min;
		}
	}

	/**
	 * Parse the version string and return the numeric value
	 * 
	 * @param s
	 *            version string
	 * @param sTokenSeparator
	 *            version tokens
	 * @param iVersionCount
	 *            how many numeric fields are in the version
	 * @return some long value
	 */
	public static final long getVersion(final String s, final String sTokenSeparator, final int iVersionCount) {
		if ((s == null) || (s.length() <= 0)) {
			return (long) NO_DATA;
		}

		final StringTokenizer st = new StringTokenizer(s, sTokenSeparator);

		long lResult = 0;

		for (int i = 0; i < iVersionCount; i++) {
			lResult = lResult * 10000;

			if (st.hasMoreTokens()) {
				try {
					String stok = st.nextToken();

					for (int j = 0; j < stok.length(); j++) {
						final char c = stok.charAt(j);

						if ((c < '0') || (c > '9')) {
							stok = stok.substring(0, j);
							break;
						}
					}

					lResult += Integer.parseInt(stok);
				} catch (Exception e) {
					// ignore
				}
			}
		}

		// System.err.println("Version ('"+s+"', "+iVersionCount+") = "+lResult);

		return lResult;
	}

	private static final MinMaxRec[] decodeMinMax(final Vector<String> minmax, final int iSize, final Vector<String> descr, final Properties prop) {
		final MinMaxRec mmr[] = new MinMaxRec[iSize];

		for (int j = 0; j < iSize; j++) {
			mmr[j] = null;
		}

		final String sDefaultColorMin = pgets(prop, "default.color.min", pgets(prop, "default.color", ""));
		final String sDefaultColorMax = pgets(prop, "default.color.max", pgets(prop, "default.color", ""));

		for (int j = 0; j < minmax.size(); j++) {
			final String s = minmax.get(j);

			final StringTokenizer st = new StringTokenizer(s, " ");

			int iCol;

			try {
				iCol = Integer.parseInt(st.nextToken());
			} catch (Exception e) {
				iCol = -1;
			}

			if (iCol >= mmr.length) {
				continue;
			}

			String sMin = (st.hasMoreTokens() ? st.nextToken() : null);
			String sMax = (st.hasMoreTokens() ? st.nextToken() : null);

			if ((sMin != null) && sMin.startsWith("auto")) {
				String sKey = descr.get(iCol) + ".color";

				String sTemp = pgets(prop, sKey + ".min", sDefaultColorMin.length() > 0 ? sDefaultColorMin : pgets(prop, sKey));

				if (sTemp.length() > 0) {
					sMin = sTemp;
				}
				else {
					if (sMin.length() > 4) {
						sMin = sMin.substring(4);
					}
					else {
						continue;
					}
				}
			}

			if ((sMax != null) && sMax.startsWith("auto")) {
				String sKey = descr.get(iCol) + ".color";

				String sTemp = pgets(prop, sKey + ".max", sDefaultColorMax.length() > 0 ? sDefaultColorMax : pgets(prop, sKey));

				if (sTemp.length() > 0) {
					sMax = sTemp;
				}
				else {
					if (sMax.length() > 4) {
						sMax = sMax.substring(4);
					}
					else {
						continue;
					}
				}
			}

			String sColorNoData = st.hasMoreTokens() ? st.nextToken() : null;
			String sColorSingleData = st.hasMoreTokens() ? st.nextToken() : null;

			MinMaxRec mmrTemp;

			if ((sMin != null) && sMin.equals("stringsdef")) {
				mmrTemp = new MinMaxStaticStringColors(prop);

				// System.err.println("Column "+iCol+": creating stringsdef with: "+prop);

			}
			else
				if ((sMin != null) && sMin.startsWith("complexgradient")) {
					// the syntax is :
					// N complexgradient(percent|abs) (valuemin^color^valuemax^color)*
					// System.err.println("Column "+iCol+": creating complex with: "+sMin+", "+sMax+", "+sColorNoData+", "+sColorSingleData);

					mmrTemp = new MinMaxComplex(sMin, sMax, sColorNoData, sColorSingleData);
				}
				else {
					// System.err.println("Column "+iCol+": creating simple with: "+sMin+", "+sMax+", "+sColorNoData+", "+sColorSingleData);

					mmrTemp = new MinMaxSimple(sMin, sMax, sColorNoData, sColorSingleData);
				}

			if (iCol >= 0) {
				mmr[iCol] = mmrTemp;
			}
			else {
				for (int i = 0; i < iSize; i++) {
					if (mmr[i] == null) {
						mmr[i] = mmrTemp;
					}
				}
			}
		}

		return mmr;
	}

	private static final class MinMaxComplex implements MinMaxRec {

		private boolean bPercent = false;

		private String sColorSingleData;

		private String sColorNoData;

		private final ArrayList<Entry> lEntries = new ArrayList<Entry>();

		private double dAbsMin;
		private double dAbsMax;

		/**
		 * @param sType
		 * @param sDef
		 * @param sNoData
		 * @param sSingleData
		 */
		public MinMaxComplex(final String sType, final String sDef, final String sNoData, final String sSingleData) {
			if ((sType == null) || (sDef == null)) {
				return;
			}

			bPercent = sType.indexOf("percent") >= 0;

			sColorSingleData = sSingleData != null ? sSingleData : "FFFFFF";
			sColorNoData = sNoData != null ? sNoData : "FFFFFF";

			StringTokenizer st = new StringTokenizer(sDef, "^|");

			while (st.hasMoreTokens()) {
				try {
					double min = Double.parseDouble(st.nextToken());
					String sColorMin = st.nextToken();
					double max = Double.parseDouble(st.nextToken());
					String sColorMax = st.nextToken();

					Entry e = new Entry(min, sColorMin, max, sColorMax);

					lEntries.add(e);
				} catch (Throwable e) {
					// ignore
				}
			}
		}

		private static class Entry {
			/**
			 * min value for the interval
			 */
			public double min;
			/**
			 * max value for the interval
			 */
			public double max;

			/**
			 * simple color generator
			 */
			MinMaxSimple mms;

			/**
			 * @param dmin
			 * @param smin
			 * @param dmax
			 * @param smax
			 */
			public Entry(final double dmin, final String smin, final double dmax, final String smax) {
				min = dmin;
				max = dmax;

				mms = new MinMaxSimple(smin, smax, null, null);
				mms.setMin(min);
				mms.setMax(max);
			}
		}

		/**
		 * @param min
		 */
		public void setMin(final double min) {
			dAbsMin = min;
		}

		/**
		 * @param max
		 */
		public void setMax(final double max) {
			dAbsMax = max;
		}

		@Override
		public String getColor(final Object o) {
			if ((o == null) || !(o instanceof Number)) {
				return sColorNoData;
			}

			double d = ((Number) o).doubleValue();

			if (bPercent) {
				if (dAbsMax <= dAbsMin) {
					return sColorSingleData;
				}

				d = ((d - dAbsMin) * 100) / (dAbsMax - dAbsMin);
			}

			for (int i = 0; i < lEntries.size(); i++) {
				Entry e = lEntries.get(i);

				if ((d <= e.max) || (i == (lEntries.size() - 1))) {
					return e.mms.getColor(Double.valueOf(d));
				}
			}

			return sColorNoData;
		}

	}

	private static Vector<monPredicate> getIDs(final Properties prop, final long lMinTime, final long lMaxTime) {
		final int iPages = pgeti(prop, "pages", 0);

		final Vector<monPredicate> vPreds = new Vector<monPredicate>();

		for (int i = 0; i < iPages; i++) {
			final Vector<String> v1 = toVector(prop, "pivot" + i + "_1", "pivot_1");
			final Vector<String> v2 = toVector(prop, "pivot" + i + "_2", "pivot_2");
			final Vector<String> v3 = toVector(prop, "pivot" + i + "_3", "pivot_3", pgetb(prop, "option" + i + ".process_queries", false));
			final Vector<String> func = toVector(prop, "func" + i, "func");

			if (v3.size() != func.size()) {
				return vPreds;
			}

			final int v1Size = v1.size();
			final int v2Size = v2.size();
			final int v3Size = v3.size();

			for (int j = 0; j < v1Size; j++) {
				final String s1 = v1.get(j);

				for (int k = 0; k < v2Size; k++) {
					final String s2 = v2.get(k);

					for (int l = 0; l < v3Size; l++) {
						String sFunc = func.get(l);
						sFunc = sFunc.trim().toLowerCase();

						boolean bQueryDB = !sFunc.startsWith("string") && !sFunc.startsWith("last") && !sFunc.startsWith("total") && !sFunc.startsWith("div");

						if (sFunc.startsWith("last") && (sFunc.indexOf("fallbacktodb") >= 0)) {
							bQueryDB = true;
						}

						if (bQueryDB) {
							String sv3 = v3.get(l);
							sv3 = replace(sv3, "$1", s1);
							sv3 = replace(sv3, "$2", s2);
							sv3 = parseOption(prop, "pivot" + i + "_3", sv3, sv3, true, true);

							final monPredicate p = toPred(sv3);

							if (p != null) {
								p.tmin = -lMinTime;
								p.tmax = -lMaxTime;

								vPreds.add(p);
							}
							else {
								System.err.println("Pred is null for '" + sv3 + "'");
							}
						}
					}
				}
			}
		}

		return vPreds;
	}

	private static final class MinMaxStaticStringColors implements MinMaxRec {
		private final Properties prop;

		/**
		 * @param properties
		 */
		public MinMaxStaticStringColors(final Properties properties) {
			prop = properties;
		}

		@Override
		public String getColor(final Object o) {
			if ((o == null) || !(o instanceof String)) {
				return "FFFFFF";
			}

			final String s = ((String) o).trim();

			if (s.length() == 0) {
				return "FFFFFF";
			}

			int colorIndex = s.hashCode();

			if (colorIndex < 0) {
				colorIndex *= -1;
			}

			final Color c = ServletExtension.getColor(prop, s + ".color", (Color) Utils.DEFAULT_PAINT_SEQUENCE[colorIndex % Utils.DEFAULT_PAINT_SEQUENCE.length]);

			return Utils.toHex(c);
		}

	}

	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		MinMaxSimple mmr = new MinMaxSimple("77FF77", "FF5555", null, null);

		mmr.setMin(0);
		mmr.setMax(500);

		System.err.println(mmr.getColor(Double.valueOf(850)));

		MinMaxComplex mmc = new MinMaxComplex("complexgradientabs", "0^FF0000^40^FFAAAA|40^FFAAAA^50^FFFFAA|50^FFFFAA^60^AAFFAA|60^AAFFAA^100^00FF00", "111111", "222222");

		for (double d = 0; d <= 100; d += 5) {
			System.err.println(d + " - " + mmc.getColor(Double.valueOf(d)));
		}

		MinMaxStaticStringColors strings = new MinMaxStaticStringColors(new Properties());

		System.err.println("INFN = " + strings.getColor("INFN"));
		System.err.println("<void> = " + strings.getColor(""));
		System.err.println("space = " + strings.getColor(" "));
		System.err.println("dash = " + strings.getColor("-"));
		System.err.println("double = " + strings.getColor(Double.valueOf(0)));
	}

}
