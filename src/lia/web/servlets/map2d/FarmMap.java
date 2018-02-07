package lia.web.servlets.map2d;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.ServletContext;

import lia.Monitor.Store.Cache;
import lia.Monitor.Store.DataSplitter;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.util.StringFactory;
import lia.web.servlets.web.Utils;
import lia.web.utils.CacheServlet;
import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Formatare;
import lia.web.utils.Page;

import org.jfree.chart.ChartUtilities;

/*
 * Created on Apr 4, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author alexc
 */
public class FarmMap extends CacheServlet {

	private static final long serialVersionUID = -3866477050236889201L;
	public Globals globals = new Globals();
	public float width2D = 0;
	public float height2D = 0;
	public float x2D = 0;
	public float y2D = 0;

	public int DISPLAY_W = 800;
	public int DISPLAY_H = 400;

	String sResDir = "";
	String sConfDir = "";
	static String sClassesDir = "";
	DB db = new DB();
	Page p;

	protected long getCacheTimeout() {
		return 0; // disable caching for maps
	}

	public final void doInit() {
		ServletContext sc = getServletContext();

		sResDir = sc.getRealPath("/");
		if (!sResDir.endsWith("/"))
			sResDir += "/";

		sConfDir = sResDir + "WEB-INF/conf/";
		sClassesDir = sResDir + "WEB-INF/classes/";
		sResDir += "WEB-INF/res/";
		response.setContentType("text/html");
		p = new Page(osOut, sResDir + "map2d/map2d.res");
	}

	private static final String getTooltip(Properties prop, String sTitle, String sBody, HashMap hmKeys) {
		if (sBody == null)
			return "";

		if (pgetb(prop, "overlib_tooltips", true)) {
			if (sTitle == null && sBody.indexOf(":") > 0) {
				sTitle = sBody.substring(0, sBody.indexOf(":"));
				sBody = sBody.substring(sBody.indexOf(":") + 1).trim();
			}

			if (sTitle == null)
				sTitle = "";

			String sExtra = pgets(prop, "overlib_tooltips.settings", "");

			String sOverlibAction = pgets(prop, "overlib_tooltips.definition", "onmouseover=\"return overlib(':BODY:', CAPTION, ':TITLE:' :EXTRA:);\" onmouseout=\"return nd();\"");

			sOverlibAction = Formatare.replace(sOverlibAction, ":BODY:", sBody);
			sOverlibAction = Formatare.replace(sOverlibAction, ":TITLE:", sTitle);
			sOverlibAction = Formatare.replace(sOverlibAction, ":EXTRA:", sExtra);

			if (hmKeys != null && hmKeys.size() > 0) {
				try {
					Iterator it = hmKeys.entrySet().iterator();

					while (it.hasNext()) {
						Map.Entry me = (Map.Entry) it.next();
						String sKey = me.getKey().toString();
						String sValue = me.getValue().toString();

						sOverlibAction = Formatare.replace(sOverlibAction, sKey, sValue);
					}
				} catch (Exception e) {
				}
			}

			return sOverlibAction;
		}
		else {
			String s = "\"" + (sTitle != null ? escHtml(sTitle) + ": " : "") + escHtml(sBody) + "\"";

			return "title=" + s + " alt=" + s;
		}
	}

	private HashMap hmActiveRequests = new HashMap();
	private long lLastActiveRequestsClear = System.currentTimeMillis();

	public void execGet() {
		final String sIP = StringFactory.get(request.getRemoteAddr());

		synchronized (hmActiveRequests) {
			if (System.currentTimeMillis() - lLastActiveRequestsClear > 1000 * 60 * 30) {
				// once every 1/2 an hour clear the hash to avoid problems when some threads are killed because they ran for too long
				lLastActiveRequestsClear = System.currentTimeMillis();
				hmActiveRequests.clear();
			}

			final Integer i = (Integer) hmActiveRequests.get(sIP);

			if (i != null && i.intValue() > 1) { // queue 2 max requests from the same IP, reject the others
				System.err.println("FarmMap: Block request from " + sIP);
				return;
			}

			hmActiveRequests.put(sIP, i == null ? Integer.valueOf(1) : Integer.valueOf(i.intValue() + 1));
		}

		try {
			synchronized (sIP) {
				actualexecGet();
			}
		} catch (Throwable t) {
			System.err.println("FarmMap: exception executing: " + t + " (" + t.getMessage() + ")");
			t.printStackTrace();
		}

		synchronized (hmActiveRequests) {
			Integer i = (Integer) hmActiveRequests.get(sIP);

			if (i != null) {
				if (i.intValue() > 1)
					hmActiveRequests.put(sIP, Integer.valueOf(i.intValue() - 1));
				else
					hmActiveRequests.remove(sIP);
			}
		}
	}

	private void actualexecGet() {
		final long lStart = System.currentTimeMillis();

		try {

			// get servlet get parameters
			float w = 32;
			float h = 16;
			float x = -16;
			float y = 8;
			float rot_angle = 0f;
			int W = 800;
			int d3d = 0;
			int pred_type = 1;
			int show_links = -1;
			int show_speciallinks = -1;

			int show_shadow = -1;
			int show_lights = -1;

			float a3d = 75;// rotation angle
			// float dx3d=.1f;//fraction from initial width for dx1+dx2
			try {
				W = Integer.parseInt(request.getParameter("_W"));
			} catch (Exception ex) {
				W = 800;
			}
			;
			try {
				w = Float.parseFloat(request.getParameter("w"));
			} catch (Exception ex) {
				w = 32;
			}
			;
			h = w * .5f;
			try {
				x = Float.parseFloat(request.getParameter("x"));
			} catch (Exception ex) {
				x = -16;
			}
			;
			try {
				y = Float.parseFloat(request.getParameter("y"));
			} catch (Exception ex) {
				y = 8f;
			}
			;
			try {
				d3d = Integer.parseInt(request.getParameter("d3d"));
			} catch (Exception ex) {
				d3d = 0;
			}
			;
			try {
				a3d = Integer.parseInt(request.getParameter("a3d"));
			} catch (Exception ex) {
				a3d = 75;
			}
			;
			// try {
			// dx3d=Integer.parseInt(request.getParameter("dx3d"));
			// } catch (Exception ex) { dx3d=0.1f; };
			try {
				pred_type = Integer.parseInt(request.getParameter("p"));
			} catch (Exception ex) {
				pred_type = 1;
			}
			;
			try {
				show_links = Integer.parseInt(request.getParameter("ckShowLinks"));
			} catch (Exception ex) {
				show_links = -1;
			}
			;
			try {
				show_speciallinks = Integer.parseInt(request.getParameter("ckShowSpecialLinks"));
			} catch (Exception ex) {
				show_speciallinks = -1;
			}
			;
			try {
				show_shadow = Integer.parseInt(request.getParameter("ShowShadow"));
			} catch (Exception ex) {
				show_shadow = -1;
			}
			;
			try {
				show_lights = Integer.parseInt(request.getParameter("ShowLights"));
			} catch (Exception ex) {
				show_lights = -1;
			}
			;

			globals.DISPLAY_W = W;
			globals.DISPLAY_H = W / 2;
			if (d3d > 0) {
				// if the image is 3D, then compute some extra lateral images
				float extra;
				// int dx2=(int)(2*DISPLAY_H*ctg/3);//(DISPLAY_W*dx3d);
				float ctg = 1f / (float) Math.tan(a3d * Math.PI / 180);
				float fdx2 = 2f * DISPLAY_H * ctg / 3f;// (DISPLAY_W*dx3d);
				extra = fdx2 / DISPLAY_W * w;
				x -= extra;
				w += 2 * extra;
			}
			// limit y
			float y_max = Globals.MAP_HEIGHT * .5f;
			float y_min = -Globals.MAP_HEIGHT * .5f + h;
			if (y > y_max)
				y = y_max;
			if (y < y_min)
				y = y_min;
			// set x inside [-MAP_WIDTH;MAP_WIDTH] limits

			while (x >= Globals.MAP_WIDTH * .5f)
				x -= Globals.MAP_WIDTH;
			while (x < -Globals.MAP_WIDTH * .5f)
				x += Globals.MAP_WIDTH;

			globals.width2D = w;
			globals.height2D = h;
			globals.x2D = x;
			globals.y2D = y;
			width2D = w;
			height2D = h;
			x2D = x;
			y2D = y;

			logTiming("servlet parameter show_shadow =" + show_shadow);
			globals.show_shadow = show_shadow;
			globals.show_lights = show_lights;

			logTiming("[FarmMap] dimmensions: x= " + x + " y= " + y + " w= " + w + " h= " + h);

			// Page p = new Page(sResDir + "genimage/genimage.res");
			Properties prop = Utils.getProperties(sConfDir, gets("page"));

			setLogTiming(prop);

			// p.modify("page", gets("page"));

			/*
			 * 
			 * Build the nodes information
			 * 
			 */
			Vector vAuto = toVector(prop, "auto", null);
			Vector vFarmRouters = toVector(prop, "farmrouters", null);
			Vector vRouters = toVector(prop, "noderouters", null);
			Vector vBiggerNodes = toVector(prop, "biggernodes", null);
			Vector vNotVisible = toVector(prop, "notvisible", null);

			boolean dataFromDB = pgetb(prop, "dataFromDB", false);
			boolean showBump = pgetb(prop, "3dfarms", true);
			boolean showShadow = pgetb(prop, "shadow", true);

			Vector vData = new Vector();
			Vector vLong = new Vector();
			Vector vLat = new Vector();
			Vector vAliases = new Vector();

			// System.err.println("auto: "+vAuto.elementAt(0));
			if (Integer.parseInt((String) vAuto.elementAt(0)) == 1) {
				Vector vDataFile = toVector(prop, "nodes", null);
				Vector vLongFile = toVector(prop, "long", null);
				Vector vLatFile = toVector(prop, "lat", null);
				Vector vAliasesFile = toVector(prop, "aliases", null);

				db.query("select * from abping_aliases");
				while (db.moveNext()) {
					if (vDataFile.contains(db.gets(2))) {
						vData.add(db.gets(2));
						vLong.add(vLongFile.elementAt(vDataFile.indexOf(db.gets(2))));
						vLat.add(vLatFile.elementAt(vDataFile.indexOf(db.gets(2))));
						vAliases.add(vAliasesFile.elementAt(vDataFile.indexOf(db.gets(2))));
					}
					else {
						if (vNotVisible == null) {
							vData.add(db.gets(2));
							vLong.add(db.gets(5));
							vLat.add(db.gets(4));
							vAliases.add(db.gets(2));
						}
						else {
							if (!(vNotVisible.contains(db.gets(2)))) {
								vData.add(db.gets(2));
								vLong.add(db.gets(5));
								vLat.add(db.gets(4));
								vAliases.add(db.gets(2));
							}
						}

					}
				}
			}
			else {
				vData = toVector(prop, "nodes", null);
				vLong = toVector(prop, "long", null);
				vLat = toVector(prop, "lat", null);
				vAliases = toVector(prop, "aliases", null);
			}

			// System.out.println("[FarmMap] LAT[0]= "+vLat.elementAt(0)+"LONG[0]= "+vLong.elementAt(0));

			Vector vNodes = new Vector();
			HashMap hmNodes = new HashMap();

			int iDefaultXLabelOffset = pgeti(prop, "default.xlabeloffset", -32);
			int iDefaultYLabelOffset = pgeti(prop, "default.ylabeloffset", -12);
			int iDefaultRadius = pgeti(prop, "default.radius", 8);
			int iDefaultFontSize = pgeti(prop, "default.fontsize", 14);

			Color vDefaultColorSequence[] = new Color[2];
			vDefaultColorSequence[0] = Color.GREEN;
			vDefaultColorSequence[1] = Color.YELLOW;

			Vector vColors = toVector(prop, "default.colors" + pred_type, null);

			if (vColors.size() > 0) {
				vDefaultColorSequence = new Color[vColors.size()];
				for (int i = 0; i < vColors.size(); i++) {
					StringTokenizer st = new StringTokenizer((String) vColors.get(i));
					Color c = ColorFactory.getColor(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
					vDefaultColorSequence[i] = c;
				}
			}

			String sDefaultAlternate = pgets(prop, "default.alternate_data");
			int vo_nr = pgeti(prop, "vo_nr", 13);

			for (int i = 0; i < vData.size(); i++) {
				StringTokenizer st = new StringTokenizer((String) vData.get(i));

				String sName = st.nextToken();

				List lData = null;
				int max_vo = 0;

				if (pgetb(prop, "routers", false)) {
					lData = null;
				}
				else {
					lData = new LinkedList();
				}

				if (!st.hasMoreTokens()) {
					st = new StringTokenizer(pgets(prop, sName + ".preds"), "&");
				}

				if (!st.hasMoreTokens()) {
					st = new StringTokenizer(pgets(prop, "default.preds" + pred_type), "&");
				}

				while (st.hasMoreTokens()) {
					try {
						String sPredicate = st.nextToken();
						sPredicate = replace(sPredicate, "$NAME", sName);

						monPredicate pred = toPred(sPredicate);
						/*
						 * Result r = Cache.getLastValue(pred);
						 * 
						 * if (r!=null && r.param!=null && r.param.length>0)
						 * lData.add(Double.valueOf(r.param[0]));
						 */
						double res = 0;
						Result r;
						Vector v = Cache.getLastValues(pred);
						for (int k = 0; k < v.size(); k++) {
							r = (Result) v.get(k);
							if (r.param != null) {
								res += r.param[0];
								if (pred_type == 1)
									break;
							}

						}

						if (sPredicate.indexOf("VO_JOBS") > 0 && sPredicate.indexOf("VO_JOBS/*/") < 0) {
							if (v == null || v.size() == 0) {
								lData.add(Double.valueOf(0));

								max_vo++;
								if (max_vo == vo_nr)
									lData.clear();

							}
						}

						if (v != null && v.size() > 0)
							lData.add(Double.valueOf(res));
						else { // bring data from DB
							if (dataFromDB == true) {
								String newPredicate = sPredicate.replaceAll("-1/-1/", ""); // predicate without "-1/-1/"
								db.query("select mi_lastvalue from monitor_ids where mi_key='" + newPredicate + "'");
								if (db.gets(1) != null)
									lData.add(Double.valueOf(db.gets(1)));

							}

						}
					} catch (Exception e) {
						System.err.println("Cannot add node's status : " + e + "(" + e.getMessage() + ")");
					}
				}

				String sLat = vLat.size() > i ? (String) vLat.get(i) : null;
				String sLong = vLong.size() > i ? (String) vLong.get(i) : null;

				String sAlias = sName;

				try {
					sAlias = ((String) vAliases.get(i)).trim();
				} catch (Exception e) {
					sAlias = sName;
				}

				WNode1 wn = new WNode1(sAlias, sLong, sLat, lData);
				wn.realname = sName;

				wn.xLabelOffset = pgeti(prop, sName + ".xlabeloffset", iDefaultXLabelOffset);
				wn.yLabelOffset = pgeti(prop, sName + ".ylabeloffset", iDefaultYLabelOffset);
				wn.r = pgeti(prop, sName + ".radius", iDefaultRadius);
				wn.fontsize = pgeti(prop, sName + ".fontsize", iDefaultFontSize);
				wn.colors = vDefaultColorSequence;

				String sAlternate = pgets(prop, sName + ".alternate_data", sDefaultAlternate);

				if (sAlternate.length() > 0) {
					sAlternate = replace(sAlternate, "$NAME", sName);

					monPredicate pred = toPred(sAlternate);
					Object o = Cache.getLastValue(pred);
					if (o != null && (o instanceof Result)) {
						wn.alternate = new LinkedList();
						wn.alternate.add((Result) o);
					}
				}

				vNodes.add(wn);
				hmNodes.put(sName, wn);
			}

			if (pgetb(prop, "scallednodes.enabled", false)) {
				double dmin = -1;
				double dmax = -1;

				for (int i = 0; i < vNodes.size(); i++) {
					WNode1 wn = (WNode1) vNodes.get(i);

					double dVal = 0;

					if (wn.data != null && wn.data.size() > 0) {
						for (int j = 0; j < wn.data.size(); j++)
							dVal += ((Double) wn.data.get(j)).doubleValue();

						dmin = dmin < 0 || dmin > dVal ? dVal : dmin;
						dmax = dmax < 0 || dmax < dVal ? dVal : dmax;
					}
				}

				final double rmin = pgetd(prop, "scallednodes.min", 1D);
				final double rmax = pgetd(prop, "scallednodes.max", (double) iDefaultRadius);

				final double dDownSubstract = pgetd(prop, "scallednodes.substract_if_down", 2);
				final double dDownThreshold = pgetd(prop, "scallednodes.substract_if_down.threshold", 3);

				if (dmax > 0 && Math.abs(dmax - dmin) < 1E-20) {
					for (int i = 0; i < vNodes.size(); i++) {
						WNode1 wn = (WNode1) vNodes.get(i);

						double dVal = 0;

						if (wn.data != null && wn.data.size() > 0) {
							for (int j = 0; j < wn.data.size(); j++)
								dVal += ((Double) wn.data.get(j)).doubleValue();

							wn.r = (int) (((dVal - dmin) / (dmax - dmin)) * (rmax - rmin) + rmin);
						}
						else {
							wn.r = (int) (rmin > dDownThreshold ? rmin - dDownSubstract : rmin);
						}
					}
				}
			}

			/*
			 * 
			 * Build the links information
			 * 
			 */
			logTiming("[FarmMap] Build the links information");

			if (show_links != 0)
				vData = toVector(prop, "links", null);
			else
				vData = new Vector();
			Vector vLinks = new Vector();

			// boolean bIgnoreZero = pgetb(prop, "ignorezero", false);

			for (int i = 0; i < vData.size(); i++) {
				final StringTokenizer st = new StringTokenizer((String) vData.get(i), ":");

				final String sSrc = st.nextToken();
				final String sDest = st.nextToken();

				if (hmNodes.get(sSrc) == null || hmNodes.get(sDest) == null)
					continue;

				final Hashtable htData = new Hashtable();

				while (st.hasMoreTokens()) {
					final String sName = st.nextToken();
					final String sPreds = st.nextToken();
					final StringTokenizer stPreds = new StringTokenizer(sPreds, "&");

					final int nPreds = stPreds.countTokens();

					if (nPreds > 1) {
						Double[] vDouble = new Double[nPreds];

						boolean bHasData = false;

						for (int j = 0; j < nPreds; j++) {
							monPredicate pred = toPred(stPreds.nextToken());

							vDouble[j] = getLinkValue(pred, prop);
							bHasData = bHasData || vDouble[j] != null;
						}

						if (bHasData)
							htData.put(sName, vDouble);

					}
					else {
						monPredicate pred = toPred(stPreds.nextToken());
						Double d = getLinkValue(pred, prop);

						if (d != null)
							htData.put(sName, d);
					}
				}

				if (htData.size() > 0) {
					WLink1 wl = new WLink1((WNode1) hmNodes.get(sSrc), (WNode1) hmNodes.get(sDest), htData);

					vLinks.add(wl);
				}
			}

			/*
			 * Special links
			 */

			logTiming("[FarmMap] Special links");

			if (show_speciallinks != 0)
				vData = toVector(prop, "speciallink", null);
			else
				vData = new Vector();

			// vData = toVector(prop, "speciallink", null);
			Vector vSpecial = new Vector();

			for (int i = 0; i < vData.size(); i++) {

				StringTokenizer st = new StringTokenizer((String) vData.get(i));

				String sSrc = st.nextToken();
				String sDest = st.nextToken();

				vSpecial.add((WNode1) hmNodes.get(sSrc));
				vSpecial.add((WNode1) hmNodes.get(sDest));

				if (st.hasMoreTokens()) {
					String sLabel = st.nextToken();

					sLabel = Formatare.replace(sLabel, "_", " ").trim();

					vSpecial.add(sLabel);
				}
				else {
					vSpecial.add("");
				}

				if (st.hasMoreTokens()) {
					String sColor = st.nextToken();

					vSpecial.add(getColor(sColor, null));
				}
				else {
					vSpecial.add(null);
				}
			}

			// build the image
			logTiming("[FarmMap] Building map image... ");

			String sResolution = pgets(prop, "resolution", "1024x512");

			if (sResolution.indexOf("x") <= 0)
				sResolution = "1024x512";

			Map2D map2D = new Map2D(globals);
			BufferedImage img = new BufferedImage(globals.DISPLAY_W, globals.DISPLAY_H, BufferedImage.TYPE_INT_RGB);
			WWmap1 map = new WWmap1(img, globals.DISPLAY_W, globals.DISPLAY_H, vNodes, vLinks, vSpecial, sResolution, prop, vFarmRouters, vRouters, vBiggerNodes, showBump, showShadow);
			map.map2D = map2D;

			int xCenter = pgeti(prop, "xcenter", 0);
			int yCenter = pgeti(prop, "ycenter", 0);
			double zoom = pgetd(prop, "zoom", 1D);
			map2D.drawGeoMap(img, w, h, x, y, rot_angle, W, d3d, a3d);
			map.getImage(xCenter, yCenter, zoom);

			boolean bJPEG = pgets(prop, "image.format", "jpeg").toLowerCase().startsWith("jp");
			double fJPEGcompression = pgetd(prop, "image.jpeg.compression", 0.9D);

			// build the map
			logTiming("[FarmMap] Do nodes tooltips... ");

			StringBuilder sbMap = new StringBuilder();

			String sDefaultHREF = pgets(prop, "default.href", pgets(prop, "href_default"));

			try {
				for (int i = 0; i < vNodes.size(); i++) {
					WNode1 node = (WNode1) vNodes.get(i);

					String sHREF = pgets(prop, node.realname + ".href", pgets(prop, "href_" + node.realname, sDefaultHREF));
					if (sHREF.length() > 0) {
						String sTooltipType = "0";

						if (node.data == null)
							sTooltipType = "router";
						else
							sTooltipType = "" + node.data.size();

						if (node.data != null && node.data.size() == 0 && node.alternate != null)
							sTooltipType = "0_alternate_data";

						String sDefaultToolTip = pgets(prop, "default.tooltip" + pred_type + "." + sTooltipType);

						String sTooltip = pgets(prop, node.realname + ".tooltip." + sTooltipType, sDefaultToolTip);

						sTooltip = replace(sTooltip, "$NAME", node.realname);
						sTooltip = replace(sTooltip, "$ALIAS", node.name);

						double total = 0d;
						Integer vo_nr_Int = Integer.valueOf(vo_nr);
						// System.out.println("d: "+d);
						for (int j = 0; node.data != null && j < node.data.size(); j++) {
							double d = ((Double) node.data.get(j)).doubleValue();
							/*
							 * if(pred_type==1 ) {
							 * total = d;
							 * break;
							 * }
							 */
							total += d;

							// System.out.println("d: "+d);
							// sTooltip = replace(sTooltip, "$"+j, DoubleFormat.point(d));

							if (pred_type == 3 && sTooltipType.equals(vo_nr_Int.toString())) {
								if (d < 1E-10) {
									int index1 = sTooltip.indexOf("$" + j);
									String str1 = sTooltip.substring(0, index1 - 1);
									String str2 = str1.substring(0, str1.lastIndexOf('*') - 1);
									String str3 = sTooltip.substring(index1 + 3, sTooltip.length());
									sTooltip = str2.concat(str3);
									// System.out.println(" str1: "+str1+" str2: "+str2+" str3: "+str3+" stooltip: "+sTooltip);
								}
								else
								// if(j<10 && sTooltip.charAt(sTooltip.indexOf("$"+j)+1)==' ' || j>=10)
								{
									// sTooltip = replace(sTooltip, "$"+j, DoubleFormat.point(d));
									String str4 = sTooltip.substring(0, sTooltip.indexOf("$" + j) - 1);
									str4 = str4.concat(" " + DoubleFormat.point(d) + " ");
									// System.out.println(" str4: "+str4+" stooltip: "+sTooltip);
									if (sTooltip.indexOf("$" + j) + 4 < sTooltip.length()) {
										String str5 = sTooltip.substring(sTooltip.indexOf("$" + j) + 4, sTooltip.length());
										sTooltip = str4.concat(str5);

									}
									else
										sTooltip = str4;
								}

							}
							else
							// if(j<10 && sTooltip.charAt(sTooltip.indexOf("$"+j)+1)==' ' || j>=10)
							{
								// sTooltip = replace(sTooltip, "$"+j, DoubleFormat.point(d));
								if (sTooltip.indexOf("$" + j) > 0) {
									String str6 = sTooltip.substring(0, sTooltip.indexOf("$" + j) - 1);
									str6 = str6.concat(" " + DoubleFormat.point(d) + " ");
									if (sTooltip.indexOf("$" + j) + 4 < sTooltip.length()) {
										String str7 = sTooltip.substring(sTooltip.indexOf("$" + j) + 4, sTooltip.length());
										sTooltip = str6.concat(str7);
									}
									else
										sTooltip = str6;
								}
							}

						}

						if (pred_type == 3 && sTooltipType.equals(vo_nr_Int.toString())) {
							// System.out.println(sTooltip+" total:"+total);
							if (sTooltip.lastIndexOf('*') == -1 || total == 0.0) {
								sTooltip = replace(sTooltip, "Running Jobs", "No Running Jobs");
							}
						}

						sTooltip = replace(sTooltip, "*", "");
						sTooltip = replace(sTooltip, "$TOTAL", DoubleFormat.point(total));

						sTooltip = escHtml(sTooltip);
						// System.out.println(sTooltip);
						sHREF = replace(sHREF, "$NAME", node.realname);

						HashMap hmKeys = new HashMap();
						hmKeys.put(":IDREF:", "node_" + node.realname);

						sbMap.append("<area id=\"node_" + node.realname + "\" shape=\"circle\" href=\"" + sHREF + "\" target=\"_parent\" coords=\"" + node.x + "," + node.y + "," + node.r + "\" "
								+ getTooltip(prop, null, sTooltip, hmKeys) + "/>");
					}
				}
				String sDefaultLinkHref = pgets(prop, "default.link.href");

				logTiming("[FarmMap] Do links tooltips... ");

				for (int i = 0; i < /* vLinks */map.new_links.size(); i++) {
					WLink1 link = (WLink1) /* vLinks */map.new_links.get(i);

					if (link.vMap != null) {

						String sHREF = pgets(prop, "href_" + link.src.realname + "_" + link.dest.realname, pgets(prop, link.src.realname + "_" + link.dest.realname + ".href", sDefaultLinkHref));

						if (sHREF.length() <= 0)
							continue;

						sHREF = WWmap1.formatLabel(sHREF, link, null, null);

						// Iterator it = link.data.keySet().iterator();

						String sTooltipFormat = pgets(prop, link.src.realname + "_" + link.dest.realname + ".tooltip", pgets(prop, "default.link.tooltip", "$A1-$A2 Bandwidth:$B Delay:$D"));

						String sTooltip = WWmap1.formatLabel(sTooltipFormat, link, link.data.get("Bandwidth"), link.data.get("Delay"));

						HashMap hmKeys = new HashMap();

						hmKeys.put(":IDREF:", "link_" + link.src.realname + "_" + link.dest.realname);

						sbMap.append("<area id=\"link_" + link.src.realname + "_" + link.dest.realname + "\" shape=\"poly\" " + getTooltip(prop, null, sTooltip, hmKeys) + " href=\"" + sHREF
								+ "\" target=_parent coords=\"");

						for (int j = 0; j < link.vMap.size(); j++) {
							if (j > 0)
								sbMap.append(',');
							sbMap.append(link.vMap.get(j).toString());
						}

						sbMap.append("\" />");
					}
				}
			} catch (Exception e) {
				System.err.println("FarmMap : exception : " + e + "(" + e.getMessage() + ")");
				e.printStackTrace();
			}

			p.modify("map", sbMap.toString());

			try {
				logTiming("Writing image in temp directory... ");
				File tempFile = File.createTempFile("wmap-", bJPEG ? ".jpg" : ".png", new File(System.getProperty("java.io.tmpdir")));
				OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));

				if (bJPEG)
					ChartUtilities.writeBufferedImageAsJPEG(out, (float) fJPEGcompression, img);
				else
					ChartUtilities.writeBufferedImageAsPNG(out, img);

				out.flush();
				out.close();

				lia.web.servlets.web.display.registerImageForDeletion(tempFile.getName(), getCacheTimeout());

				logTiming("tempFile: " + tempFile.getName());
				p.modify("image", tempFile.getName());
			} catch (Exception e) {
				System.err.println("Exception creating the image : " + e + "(" + e.getMessage() + ")");
				e.printStackTrace();
			}

			p.write();

			/*
			 * if ( img!=null ) {
			 * //ServletOutputStream out = response.getOutputStream();
			 * ImageIO.write(img, "png", osOut);
			 * //ChartUtilities.writeBufferedImageAsPNG(out, img);
			 * response.setContentType("image/png");
			 * } else {
			 * //PrintWriter pw = response.getWriter();
			 * pwOut.println("img is null");
			 * }
			 */
		} catch (Throwable ex) {
			try {
				// PrintWriter pw = response.getWriter();
				ex.printStackTrace(pwOut);
			} catch (Exception ex2) {
				ex2.printStackTrace();
			}
			;
		}

		System.err.println("farmmap, ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

		Utils.logRequest("farmmap", (int) (System.currentTimeMillis() - lStart), request, false, System.currentTimeMillis() - lStart);

		bAuthOK = true;
	}

	private Double getLinkValue(final monPredicate pred, final Properties prop) {
		if (pred.tmin < -1000) {
			boolean bAverage = pgetb(prop, "links.history.average", true);
			boolean bIntegrate = pgetb(prop, "links.history.integrate", false);

			try {
				final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();

				final DataSplitter ds = store.getDataSplitter(new monPredicate[] { pred }, -1);

				final Vector v = ds.get(pred);

				if (v == null || v.size() == 0)
					return null;

				if (bAverage) {
					double d = 0;
					double dCount = 0;

					for (int i = 0; i < v.size(); i++) {
						Object o = v.get(i);

						if (o != null && (o instanceof Result)) {
							Result r = (Result) o;

							if (r.param != null && r.param.length > 0) {
								d += r.param[0];
								dCount++;
							}
						}
					}

					if (dCount > 0.5)
						return Double.valueOf(d / dCount);
				}

				if (bIntegrate) {
					Utils.integrateSeries(v, prop, true, -pred.tmin, -pred.tmax);

					if (v.size() > 0)
						return Double.valueOf(((Result) v.lastElement()).param[0]);
				}
			} catch (Throwable t) {
				System.err.println("FarmMap : caught " + t + " (" + t.getMessage() + ") in getLinkValue()");
			}
		}
		else {
			Object o = Cache.getLastValue(pred);
			if (o != null && (o instanceof Result) && ((Result) o).param != null && ((Result) o).param.length > 0)
				return Double.valueOf(((Result) o).param[0]);
		}

		return null;
	}

}
