package lia.web.servlets.web;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.Store.Cache;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.ColorFactory;
import lia.web.utils.DoubleFormat;
import lia.web.utils.Page;
import lia.web.utils.ThreadedPage;

import org.jfree.chart.ChartUtilities;

/**
 * Old style map
 * 
 * @author costing, catac
 * @since forever
 */
public class genimage extends ThreadedPage {
	private static final long serialVersionUID = 1L;

	private transient Page pMaster = null;

	@Override
	public final void doInit() {
		pMaster = new Page(osOut, sResDir + "masterpage/masterpage.res");
	}

	@Override
	public void execGet() {
		final long lStart = System.currentTimeMillis();

		try {
			Page p = new Page(sResDir + "genimage/genimage.res");
			Properties prop = Utils.getProperties(sConfDir, gets("page"));

			// override the configuration options with the url parameters
			Enumeration<?> eParams = request.getParameterNames();
			while (eParams.hasMoreElements()) {
				String sParameter = (String) eParams.nextElement();
				if (!sParameter.equals("page"))
					prop.setProperty(sParameter, gets(sParameter));
			}

			p.modify("page", gets("page"));

			/*
			 * 
			 * Build the nodes information
			 * 
			 */

			Vector<String> vData = toVector(prop, "nodes", null);
			Vector<String> vLong = toVector(prop, "long", null);
			Vector<String> vLat = toVector(prop, "lat", null);

			Vector<String> vAliases = toVector(prop, "aliases", null);

			Vector<WNode> vNodes = new Vector<>();
			HashMap<String, WNode> hmNodes = new HashMap<>();

			int iDefaultXLabelOffset = pgeti(prop, "default.xlabeloffset", -32);
			int iDefaultYLabelOffset = pgeti(prop, "default.ylabeloffset", -12);
			int iDefaultRadius = pgeti(prop, "default.radius", 8);
			int iDefaultFontSize = pgeti(prop, "default.fontsize", 14);

			Color vDefaultColorSequence[] = new Color[2];
			vDefaultColorSequence[0] = Color.GREEN;
			vDefaultColorSequence[1] = Color.YELLOW;

			Vector<String> vColors = toVector(prop, "default.colors", null);

			if (vColors.size() > 0) {
				vDefaultColorSequence = new Color[vColors.size()];
				for (int i = 0; i < vColors.size(); i++) {
					StringTokenizer st = new StringTokenizer(vColors.get(i));
					Color c = ColorFactory.getColor(Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()), Integer.parseInt(st.nextToken()));
					vDefaultColorSequence[i] = c;
				}
			}

			String sDefaultAlternate = pgets(prop, "default.alternate_data");

			for (int i = 0; i < vData.size(); i++) {
				StringTokenizer st = new StringTokenizer(vData.get(i));

				String sName = st.nextToken();

				List<Double> lData = null;

				if (!pgetb(prop, "routers", false)) {
					lData = new LinkedList<>();
				}

				if (!st.hasMoreTokens()) {
					st = new StringTokenizer(pgets(prop, sName + ".preds"));
				}

				if (!st.hasMoreTokens()) {
					st = new StringTokenizer(pgets(prop, "default.preds"));
				}

				while (st.hasMoreTokens()) {
					try {
						String sPredicate = st.nextToken();

						sPredicate = replace(sPredicate, "$NAME", sName);

						monPredicate pred = toPred(sPredicate);

						Object o = Cache.getLastValue(pred);

						if (o != null && (o instanceof Result) && lData != null)
							lData.add(Double.valueOf(((Result) o).param[0]));
					}
					catch (Exception e) {
						System.err.println("Cannot add node's status : " + e + "(" + e.getMessage() + ")");
					}
				}

				String sLat = vLat.size() > i ? (String) vLat.get(i) : null;
				String sLong = vLong.size() > i ? (String) vLong.get(i) : null;

				String sAlias = sName;

				try {
					sAlias = vAliases.get(i).trim();
				}
				catch (@SuppressWarnings("unused") Exception e) {
					sAlias = sName;
				}

				WNode wn = new WNode(sAlias, sLong, sLat, lData);
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
						wn.alternate = new LinkedList<>();
						wn.alternate.add(o);
					}
				}

				vNodes.add(wn);
				hmNodes.put(sName, wn);
			}

			if (pgetb(prop, "scallednodes.enabled", false)) {
				double dmin = -1;
				double dmax = -1;

				for (int i = 0; i < vNodes.size(); i++) {
					WNode wn = vNodes.get(i);

					double dVal = 0;

					if (wn.data != null && wn.data.size() > 0) {
						for (int j = 0; j < wn.data.size(); j++)
							dVal += wn.data.get(j).doubleValue();

						dmin = dmin < 0 || dmin > dVal ? dVal : dmin;
						dmax = dmax < 0 || dmax < dVal ? dVal : dmax;
					}
				}

				double rmin = pgetd(prop, "scallednodes.min", 1D);
				double rmax = pgetd(prop, "scallednodes.max", iDefaultRadius);

				if (dmax > 0 && Math.abs(dmax - dmin) < 1E-20) {
					for (int i = 0; i < vNodes.size(); i++) {
						WNode wn = vNodes.get(i);

						double dVal = 0;

						if (wn.data != null && wn.data.size() > 0) {
							for (int j = 0; j < wn.data.size(); j++)
								dVal += wn.data.get(j).doubleValue();

							wn.r = (int) (((dVal - dmin) / (dmax - dmin)) * (rmax - rmin) + rmin);
						}
						else {
							wn.r = (int) (rmin > 3 ? rmin - 2 : rmin);
						}
					}
				}
			}

			/*
			 * 
			 * Build the links information
			 * 
			 */

			vData = toVector(prop, "links", null);
			Vector<WLink> vLinks = new Vector<>();

			for (int i = 0; i < vData.size(); i++) {
				StringTokenizer st = new StringTokenizer(vData.get(i));

				String sSrc = st.nextToken();
				String sDest = st.nextToken();

				if (hmNodes.get(sSrc) == null || hmNodes.get(sDest) == null)
					continue;

				Hashtable<String, Double> htData = new Hashtable<>();

				while (st.hasMoreTokens()) {
					String sName = st.nextToken();
					monPredicate pred = toPred(st.nextToken());

					Object o = Cache.getLastValue(pred);
					if (o != null && (o instanceof Result))
						htData.put(sName, Double.valueOf(((Result) o).param[0]));
				}

				if (htData.size() > 0) {
					WLink wl = new WLink(hmNodes.get(sSrc), hmNodes.get(sDest), htData);

					vLinks.add(wl);
				}
			}

			/*
			 * 
			 * Special links
			 * 
			 */

			vData = toVector(prop, "speciallink", null);
			Vector<Object> vSpecial = new Vector<>();

			for (int i = 0; i < vData.size(); i++) {

				StringTokenizer st = new StringTokenizer(vData.get(i));

				String sSrc = st.nextToken();
				String sDest = st.nextToken();

				vSpecial.add(hmNodes.get(sSrc));
				vSpecial.add(hmNodes.get(sDest));

				vSpecial.add(st.hasMoreTokens() ? st.nextToken() : "");
			}

			/*
			 * 
			 * Build the image
			 * 
			 */

			int width = pgeti(prop, "width", 800);
			int height = pgeti(prop, "height", 400);
			String sResolution = pgets(prop, "resolution", "1024x512");

			if (sResolution.indexOf("x") <= 0)
				sResolution = "1024x512";

			WWmap map = new WWmap(width, height, vNodes, vLinks, vSpecial, sResolution, prop);

			int xCenter = pgeti(prop, "xcenter", 0);
			int yCenter = pgeti(prop, "ycenter", 0);
			double zoom = pgetd(prop, "zoom", 1D);

			BufferedImage image = map.getImage(xCenter, yCenter, zoom);

			boolean bJPEG = pgets(prop, "image.format", "jpeg").toLowerCase().startsWith("jp");
			double fJPEGcompression = pgetd(prop, "image.jpeg.compression", 0.9D);

			File tempFile = File.createTempFile("wmap-", bJPEG ? ".jpg" : ".png", new File(System.getProperty("java.io.tmpdir")));

			try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
				if (bJPEG)
					ChartUtilities.writeBufferedImageAsJPEG(out, (float) fJPEGcompression, image);
				else
					ChartUtilities.writeBufferedImageAsPNG(out, image);

				p.modify("image", tempFile.getName());

				lia.web.servlets.web.display.registerImageForDeletion(tempFile.getName(), 0);
			}
			catch (Throwable e) {
				System.err.println("Exception creating the image : " + e + "(" + e.getMessage() + ")");
				e.printStackTrace();
			}

			p.modify("title", pgets(prop, "title"));
			p.modify("description", pgets(prop, "description"));

			StringBuilder sbMap = new StringBuilder();

			String sDefaultHREF = pgets(prop, "default.href", pgets(prop, "href_default"));

			try {
				for (int i = 0; i < vNodes.size(); i++) {
					WNode node = vNodes.get(i);

					String sHREF = pgets(prop, node.realname + ".href", pgets(prop, "href_" + node.realname, sDefaultHREF));
					if (sHREF.length() > 0) {
						String sTooltipType = "0";

						if (node.data == null)
							sTooltipType = "router";
						else
							sTooltipType = "" + node.data.size();

						if (node.data != null && node.data.size() == 0 && node.alternate != null)
							sTooltipType = "0_alternate_data";

						String sDefaultToolTip = pgets(prop, "default.tooltip." + sTooltipType);

						String sTooltip = pgets(prop, node.realname + ".tooltip." + sTooltipType, sDefaultToolTip);

						sTooltip = replace(sTooltip, "$NAME", node.realname);
						sTooltip = replace(sTooltip, "$ALIAS", node.name);

						double total = 0d;

						for (int j = 0; node.data != null && j < node.data.size(); j++) {
							double d = node.data.get(j).doubleValue();
							total += d;
							sTooltip = replace(sTooltip, "$" + j, DoubleFormat.point(d));
						}

						sTooltip = replace(sTooltip, "$TOTAL", DoubleFormat.point(total));

						sTooltip = escHtml(sTooltip);

						sHREF = replace(sHREF, "$NAME", node.realname);

						sbMap.append("<area shape=\"circle\" href=\"" + sHREF + "\" coords=\"" + node.x + "," + node.y + "," + node.r + "\" " + getTooltip(prop, null, sTooltip) + "/>");
					}
				}

				String sDefaultLinkHref = pgets(prop, "default.link.href");

				for (int i = 0; i < vLinks.size(); i++) {
					WLink link = vLinks.get(i);

					if (link.vMap != null) {

						String sHREF = pgets(
								prop,
								"href_" + link.src.realname + "_" + link.dest.realname,
								pgets(
										prop,
										link.src.realname + "_" + link.dest.realname + ".href",
										sDefaultLinkHref));

						if (sHREF.length() <= 0)
							continue;

						sHREF = WWmap.formatLabel(sHREF, link, null, null);

						String sTooltipFormat = pgets(
								prop,
								link.src.realname + "_" + link.dest.realname + ".tooltip",
								pgets(prop, "default.link.tooltip", "$A1-$A2 Bandwidth:$B Delay:$D"));

						String sTooltip = WWmap.formatLabel(sTooltipFormat, link, link.data.get("Bandwidth"), link.data.get("Delay"));

						sbMap.append("<area shape=\"poly\" " + getTooltip(prop, null, sTooltip) + " href=\"" + sHREF + "\" coords=\"");

						for (int j = 0; j < link.vMap.size(); j++) {
							sbMap.append(link.vMap.get(j).toString() + ",");
						}

						sbMap.append("\" />");
					}
				}
			}
			catch (Throwable e) {
				System.err.println("exception : " + e + "(" + e.getMessage() + ")");
				e.printStackTrace();
			}

			p.modify("map", sbMap.toString());

			pMaster.append(p);

			display.initMasterPage(pMaster, prop, sResDir);
			pMaster.modify("bookmark", getBookmarkURL());
			pMaster.modify("title", "Services map");

			pMaster.write();
		}
		catch (Throwable t) {
			System.err.println(t.toString());
			t.printStackTrace();
		}

		System.err.println("map (" + gets("page") + "), ip: " + getHostName() + ", took " + (System.currentTimeMillis() - lStart) + "ms to complete");

		Utils.logRequest("genimage", (int) (System.currentTimeMillis() - lStart), request, false, System.currentTimeMillis() - lStart);

		bAuthOK = true;
	}

	private static final String getTooltip(final Properties prop, final String sTitle, final String sBody) {
		if (sBody == null)
			return "";

		String title = sTitle;
		String body = sBody;

		if (pgetb(prop, "overlib_tooltips", true)) {
			if (title == null && body.indexOf(":") > 0) {
				title = body.substring(0, body.indexOf(":"));
				body = body.substring(body.indexOf(":") + 1).trim();
			}

			return "onmouseover=\"return overlib('" + body + "'" + (title != null ? ", CAPTION, '" + title + "'" : "") + ");\" onmouseout=\"return nd();\"";
		}
		String s = "\"" + (title != null ? escHtml(title) + ": " : "") + escHtml(body) + "\"";

		return "title=" + s + " alt=" + s;
	}

}
