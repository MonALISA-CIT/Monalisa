package lia.web.servlets.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lazyj.Format;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.DB;
import lia.web.utils.CacheServlet;
import lia.web.utils.Formatare;
import lia.web.utils.Page;
import lia.web.utils.ServletExtension;

/**
 * @author costing
 * @since forever
 */
public class ABPing extends ServletExtension {
	private static final long serialVersionUID = -2190075295184456255L;

	private static HashMap<String, String> hmNames = new HashMap<>();

	/**
	 * default series shape
	 */
	public static final String sDefaultShape = "o";

	private static boolean bDatabaseInitialized = false;

	/**
	 * Create tables, indexes and so on.
	 */
	public static synchronized final void initDBStructure() {
		if (TransparentStoreFactory.isMemoryStoreOnly() || bDatabaseInitialized)
			return;

		final DB db = new DB();
		db.syncUpdateQuery("CREATE TABLE abping (mfarmsource varchar(100), mfarmdest varchar(100), is_connected int default 0);", true);
		db.syncUpdateQuery("CREATE TABLE colors (sitename varchar(100), R int default 0, G int default 0, B int default 0);", true);
		db.syncUpdateQuery("CREATE TABLE abping_aliases (ip varchar(1000), name varchar(100), version varchar(100));", true);
		db.syncUpdateQuery("CREATE UNIQUE INDEX abping_src_dest_uidx ON abping(mfarmsource, mfarmdest);", true);
		db.syncUpdateQuery("CREATE UNIQUE INDEX abping_aliases_ip_name_uidx ON abping_aliases(ip, name);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN version varchar(100);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN geo_lat varchar(50);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN geo_long varchar(50);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN java_ver varchar(50);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN libc_ver varchar(50);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN autoupdate int;", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN contact_email varchar(250);", true);
		db.syncUpdateQuery("ALTER TABLE abping_aliases ADD COLUMN contact_name varchar(250);", true);

		db.syncUpdateQuery("CREATE TABLE abping_aliases_extra (ip varchar(1000), name varchar(100));", true);

		db.syncUpdateQuery("ALTER TABLE colors ADD COLUMN shape char(1) DEFAULT '" + sDefaultShape + "';", true);

		db.syncUpdateQuery("CREATE TABLE hidden_sites (name text primary key);", true);

		bDatabaseInitialized = true;
	}

	static {
		new Thread() {
			@Override
			public void run() {
				try {
					initDBStructure();

					updateNames();
				}
				catch (final Throwable t) {
					System.err.println(t);
					t.printStackTrace();
				}
			}
		}.start();
	}

	private String sZoneDir = "";

	private String sResDir = "";

	private String sConfDir = "";

	/**
	 * Init function
	 *
	 * @param req
	 * @param res
	 */
	public final void doInit(final HttpServletRequest req, final HttpServletResponse res) {
		final ServletContext sc = getServletContext();

		sZoneDir = sc.getRealPath("/");
		if (!sZoneDir.endsWith("/"))
			sZoneDir += "/";

		sConfDir = sZoneDir + "WEB-INF/conf/";

		sResDir = sZoneDir + "WEB-INF/res/";

		response = res;
		request = req;
	}

	// ********************** doGet *******************************************
	@Override
	public void doGet(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		doInit(req, res);

		final String l_queryType = gets("function");

		if (l_queryType.equals("ABPingWriteConfigurationToFile"))
			DB_ABPingFarmConfFile_Create();
		else
			if (l_queryType.equals("ABPingFarmsDiscover")) {
				DB_ABPingFarmsDiscover();
				ABPingAsMatrix(res);
			}
			else
				if (l_queryType.equals("ABPingFarmsList"))
					DB_ABPingFarmsList(res);
				else
					if (l_queryType.equals("ABPingFarmView"))
						DB_ABPingFarmView(res, req.getParameter("farmName"));
					else
						if (l_queryType.equals("ColorSitesList"))
							SH_ColorSitesList(req, res);
						else
							if (l_queryType.equals("ColorSitesListNew"))
								showColors();
							else
								if (l_queryType.equals("ColorUpdate"))
									DB_ColorPaletteSelect(req, res);
								else
									ABPingAsMatrix(res);

		// ResourceBundle rb = ResourceBundle.getBundle("LocalStrings",req.getLocale());
		// res.setContentType("text/html");
		// String title = rb.getString("helloworld.title");
	}

	/**
	 *
	 */
	private void showColors() throws IOException {
		@SuppressWarnings("resource")
		final Page pMaster = new Page(response.getOutputStream(), sResDir + "masterpage/masterpage_admin.res");
		final Page p = new Page(sResDir + "colors/colors.res");

		final Properties prop = Utils.getProperties(sConfDir, "global");

		showListNew(toVector(prop, "Farms", null, true, false), p, "MonALISA Services");

		final int iOtherColorSets = pgeti(prop, "colors.sets", 0);

		for (int i = 0; i < iOtherColorSets; i++)
			showListNew(toVector(prop, "colors.set_" + i + ".list", null, true, false), p, pgets(prop, "colors.set_" + i + ".title"));

		pMaster.append(p);
		pMaster.modify("class_farm", "_active");
		pMaster.write();
	}

	private void showListNew(final Vector<String> v, final Page p, final String sTitle) {
		final Page pSet = new Page(sResDir + "colors/set.res");
		final Page pSetLine = new Page(sResDir + "colors/setline.res");
		final Page pSetElement = new Page(sResDir + "colors/setelement.res");

		pSet.modify("name", sTitle);

		final DB db = new DB();
		String bgcolor = null;
		String ls_siteName;

		boolean bDirty = false;

		for (int j = 0; j < v.size(); j++) {
			ls_siteName = v.get(j);

			db.query("SELECT R,G,B,shape from colors WHERE sitename = '" + ls_siteName + "'");

			if (db.moveNext())
				bgcolor = "#" + toHex(db.geti(1)) + toHex(db.geti(2)) + toHex(db.geti(3));
			else
				bgcolor = "";

			pSetElement.modify("color", bgcolor);
			pSetElement.modify("name", ls_siteName);
			pSetElement.modify("shape", db.gets(4));

			pSetLine.append(pSetElement);

			if (j % 4 == 3) {
				pSet.append(pSetLine);
				bDirty = false;
			}
			else {
				bDirty = true;
			}
		}

		if (bDirty)
			pSet.append(pSetLine);

		p.append(pSet);
	}

	private static final String vsColors[] = { "FFFFFF", "CCCCCC", "CCCCFF", "AAAACC", "FF0000" };

	private static final String vsSiteColors[] = { "000000", "0000CC" };

	/**
	 * Build the matrix of hosts
	 *
	 * @param res
	 * @throws IOException
	 */
	public void ABPingAsMatrix(final HttpServletResponse res) throws IOException {
		@SuppressWarnings("resource")
		final Page p = new Page(res.getOutputStream(), sResDir + "abping/abping.res");
		final Page ph = new Page(sResDir + "abping/header.res");
		final Page pl = new Page(sResDir + "abping/line.res");
		final Page pe = new Page(sResDir + "abping/element.res");
		final Page pn = new Page(sResDir + "abping/null.res");

		updateNames();

		final HashMap<String, String> hmAliases = new HashMap<>();
		final DB db = new DB();
		db.query("SELECT ip,name FROM abping_aliases UNION SELECT ip,name FROM abping_aliases_extra;");
		while (db.moveNext())
			hmAliases.put(db.gets(1), db.gets(2));

		db.query("SELECT distinct mfarmdest FROM abping ORDER BY mfarmdest ASC;");

		final Vector<String> vDest = new Vector<>();

		while (db.moveNext()) {
			final String s1 = db.gets(1);
			String s = s1;

			if (hmAliases.get(s) != null)
				s = hmAliases.get(s);

			if (s.length() > 10)
				s = s.substring(0, 10);

			for (int i = 0; i < s.length(); i++)
				ph.append("dest", s.charAt(i) + "<br>");

			ph.modify("color", vsColors[(vDest.size() % 2) * 2]);
			p.append("header", ph);
			vDest.add(s1);
		}

		db.query("SELECT mfarmsource,mfarmdest FROM abping WHERE is_connected=1;");
		final HashMap<String, String> hmConnected = new HashMap<>();
		while (db.moveNext())
			hmConnected.put(db.gets("mfarmsource") + "@" + db.gets("mfarmdest"), "");

		db.query("SELECT distinct mfarmsource FROM abping ORDER BY mfarmsource ASC;");

		int iLine = 0;

		while (db.moveNext()) {
			final String src = db.gets("mfarmsource");
			String srca = src;
			if (hmAliases.get(srca) != null)
				srca = hmAliases.get(srca);

			String name = hmNames.get(src);
			if (name == null)
				name = src;

			pl.modify("source", src);
			pl.modify("source_alias", srca);
			pl.modify("name", name);
			pl.modify("color", vsColors[iLine % 2]);

			boolean bHasPings = false;

			for (int i = 0; i < vDest.size(); i++) {
				if (iLine == i) {
					pn.modify("color", vsColors[iLine % 2 + (i % 2) * 2]);
					pl.append(pn);
					continue;
				}

				final String dst = vDest.get(i);
				String dsta = dst;
				if (hmAliases.get(dsta) != null)
					dsta = hmAliases.get(dsta);

				final boolean connected = hmConnected.get(src + "@" + dst) != null;

				pe.modify("connected", connected ? "1" : "0");
				pe.modify("source", src);
				pe.modify("dest", dst);
				pe.modify("source_alias", srca);
				pe.modify("dest_alias", dsta);

				if (!connected) {
					pe.modify("color", vsColors[iLine % 2 + (i % 2) * 2]);
				}
				else {
					pe.modify("color", vsColors[4]);
					bHasPings = true;
				}

				pl.append(pe);
			}

			pl.modify("sitecolor", vsSiteColors[bHasPings ? 0 : 1]);

			iLine++;

			p.append(pl);
		}

		p.write();
	}

	// *********************** doPost ***************************
	@Override
	public void doPost(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		doInit(req, res);

		final String ip = gets("ip");
		final String un = gets("fqn");

		if (ip.length() > 0 && un.length() > 0) {
			final DB db = new DB();

			db.syncUpdateQuery("DELETE FROM abping_aliases_extra WHERE ip='" + ip + "';");
			db.syncUpdateQuery("DELETE FROM abping_aliases_extra WHERE name='" + un + "';");
			db.syncUpdateQuery("INSERT INTO abping_aliases_extra (ip, name) VALUES ('" + ip + "', '" + un + "');");

			db.setReadOnly(true);

			if (db.query("SELECT mfarmsource FROM abping WHERE mfarmsource='" + ip + "';", true) && !db.moveNext()) {
				final DB db2 = new DB();

				db.query("SELECT distinct mfarmsource FROM abping;");

				if (db.moveNext()) {
					do {
						db2.query("INSERT INTO abping VALUES ('" + ip + "', '" + db.gets(1) + "', 0);", true);
						db2.query("INSERT INTO abping VALUES ('" + db.gets(1) + "', '" + ip + "', 0);", true);
					} while (db.moveNext());

					db2.query("DELETE FROM abping WHERE mfarmsource=mfarmdest;");
				}
				else {
					db2.query("INSERT INTO abping VALUES ('" + ip + "', '" + ip + "', 0);");
				}
			}

			redirect("abping?function=Matrix");
			return;
		}

		final String buttonLabel = gets("button");
		if (buttonLabel.compareTo("Update") == 0)
			DB_ABPingFarmUpdate(req, res);
		if (buttonLabel.compareTo("DoIt!") == 0)
			DB_ColorPaletteUpdate(req, res);

		final DB db = new DB();
		db.query("UPDATE abping SET is_connected=0 WHERE is_connected IS NULL OR is_connected!=0;");

		if (buttonLabel.length() <= 0) {
			final String vs[] = request.getParameterValues("bife");

			for (int i = 0; vs != null && i < vs.length; i++) {
				final String s = vs[i];

				final String src = s.substring(0, s.indexOf("@")).trim();
				final String dst = s.substring(s.indexOf("@") + 1).trim();

				insert(src, dst);

				if (geti("simetric") != 0)
					insert(dst, src);
			}

			DB_ABPingFarmConfFile_Create();

			redirect("abping?function=Matrix");
		}
	}

	private static void insert(final String src, final String dst) {
		final DB db = new DB();
		db.query("SELECT mfarmsource FROM abping WHERE mfarmsource='" + src + "' AND mfarmdest='" + dst + "';");

		if (db.moveNext())
			db.query("UPDATE abping SET is_connected=1 WHERE trim(mfarmsource)='" + src + "' AND trim(mfarmdest)='" + dst + "';");
		else
			db.query("INSERT INTO abping VALUES ('" + src + "','" + dst + "',1);");
	}

	private static void DB_ABPingFarmsDiscover() {
		final Vector<String> v = DB_ABPingFarmsSoaped();

		final DB db = new DB();

		for (int i = 0; i < v.size(); i++)
			for (int j = 0; j < v.size(); j++)
				if (i != j) {
					db.query("SELECT * FROM abping WHERE mfarmsource='" + v.get(i) + "' AND mfarmdest='" + v.get(j) + "';");
					if (!db.moveNext())
						db.query("INSERT INTO abping VALUES('" + v.get(i) + "','" + v.get(j) + "',0);");
				}
	}

	private static void DB_ABPingFarmsList(final HttpServletResponse res) throws IOException {
		@SuppressWarnings("resource")
		final PrintWriter out = res.getWriter();
		final DB db = new DB("SELECT distinct mfarmsource FROM abping ORDER by mfarmsource;");

		lib_drawHtml.drawHeader(out, res, "ABPing: Farms List (hostname)");
		out.println("<b>AVAILABLE FARMS:</B><br><br>");

		while (db.moveNext())
			out.println("<a href=abping?function=ABPingFarmView&farmName=" + db.gets(1) + ">" + db.gets(1) + "</a><br>");

		out.println("<br>&nbsp;<br><a href=abping?function=ABPingFarmsDiscover>Refresh this list (might take a while if the database is big)</a><br>");
		lib_drawHtml.closeHtml(out);
	}

	private static void DB_ABPingFarmView(final HttpServletResponse res, final String p_farmName) throws IOException {
		@SuppressWarnings("resource")
		final PrintWriter out = res.getWriter();
		final DB db = new DB("SELECT mfarmdest, is_connected from abping WHERE mfarmsource = '" + p_farmName + "' ORDER by mfarmdest;");

		lib_drawHtml.drawHeader(out, res, "ABPing: Farm Connections");
		out.println("<a href=abping?function=ABPingFarmsList>Back to available farms list</a><br><br>");
		out.println("<b>Check the farms you want to ABPing from " + p_farmName + ":</B><br><br>");
		out.println("<form name=form1 method=POST action='abping'>");

		while (db.moveNext()) {
			out.print("<input type=checkbox name=" + db.gets(1));
			if (db.geti(2) != 0)
				out.print(" checked");

			out.println(">");
			out.println("<font size='2'>" + db.gets(1) + "</font><br>");
		}

		out.println("<input name=mfarmsrc type=hidden value=" + p_farmName + ">");
		lib_drawHtml.drawObjects(out, "button", new StringTokenizer("Update|end", "|"));
		out.println("</form>");
		lib_drawHtml.closeHtml(out);
	}

	private void DB_ABPingFarmUpdate(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		@SuppressWarnings("resource")
		final PrintWriter out = res.getWriter();
		final String ls_mfarmsrc = req.getParameter("mfarmsrc");
		String ls_mfarmdst = "";

		final DB db = new DB();

		db.query("UPDATE abping SET is_connected=0 WHERE mfarmsource = '" + ls_mfarmsrc + "'");

		lib_drawHtml.drawHeader(out, res, ls_mfarmsrc + " ABPing connections");
		out.println("ABPing enabled from node <b>" + Format.escHtml(ls_mfarmsrc) + "</b> to nodes ");
		final Enumeration<?> enumeration = req.getParameterNames();
		if (enumeration != null) {
			while (enumeration.hasMoreElements()) {
				ls_mfarmdst = (String) enumeration.nextElement();
				if (ls_mfarmdst.compareTo("button") != 0 && ls_mfarmdst.compareTo("mfarmsrc") != 0)
					out.print("<b>" + ls_mfarmdst + "</b>,");
				db.query("UPDATE abping SET is_connected=1 WHERE mfarmsource = '" + Format.encode(ls_mfarmsrc) + "' AND mfarmdest = '" + Format.encode(ls_mfarmdst) + "'");
			}
		}

		out.println("<br><br><a href=abping?function=ABPingFarmView&farmName=" + Format.escHtml(ls_mfarmsrc) + ">Back to " + Format.escHtml(ls_mfarmsrc) + " connections</a>");
		out.println("</form>");
		lib_drawHtml.closeHtml(out);

		DB_ABPingFarmConfFile_Create();
	}

	/**
	 *
	 */
	static void updateNames() {
		final DB db = new DB();

		db.query("SELECT DISTINCT mfarmsource FROM abping;");
		while (db.moveNext()) {
			boolean bFound;

			final String src = db.gets(1);

			synchronized (hmNames) {
				bFound = hmNames.get(src) != null && !hmNames.get(src).equals(src);
			}

			if (!bFound) {
				try {
					final InetAddress ia = InetAddress.getByName(src);
					final String sName = lazyj.Utils.getHostName(ia.getHostAddress());

					synchronized (hmNames) {
						if (sName != null && sName.length() > 0)
							hmNames.put(src, sName);
						else
							hmNames.put(src, src);
					}
				}
				catch (@SuppressWarnings("unused") final Throwable t) {
					synchronized (hmNames) {
						hmNames.put(src, src);
					}
				}
			}
		}
	}

	private void DB_ABPingFarmConfFile_Create() {
		updateNames();

		String ls_mfarmsrc;

		try (PrintStream cout = new PrintStream(new FileOutputStream(sZoneDir + "ABPingFarmConfig"))) {
			final DB db = new DB();

			final StringTokenizer st = DB_ABPingFarmsSrcList();
			while (st.hasMoreTokens()) {
				ls_mfarmsrc = st.nextToken();

				final StringBuilder sb = new StringBuilder();
				db.query("SELECT mfarmdest from abping WHERE mfarmsource = '" + ls_mfarmsrc + "' AND is_connected = 1 ORDER BY mfarmdest;");
				while (db.moveNext())
					sb.append(" " + hmNames.get(db.gets(1)));

				final String s = sb.toString();

				cout.println(hmNames.get(ls_mfarmsrc) + s);
				cout.println(ls_mfarmsrc + s);

				cout.println("");
			}

			cout.println("# double values");
			cout.println("OVERALL_COEF 0");
			cout.println("RTT_COEF 0.5");
			cout.println("PKT_LOSS_COEF 500");
			cout.println("JITTER_COEF 10");
			cout.println("# We keep last RTT_SAMPLES rtts (integer value)");
			cout.println("RTT_SAMPLES 6");
			cout.println("# The history of Lost Packages is PKT_LOSS_MEM long (integer value)");
			cout.println("PKT_LOSS_MEM 10");
			cout.println("# The size of the packet sent over the net (must be bigger than 3 bytes)");
			cout.println("PACKET_SIZE 450");
			cout.println("# Time between pings (milliseconds). Should be big enough to alow reasonable time for");
			cout.println("# packets to return to sender and not consider them lost");
			cout.println("PING_INTERVAL 4000");

			cout.flush();
		}
		catch (final IOException e) {
			System.err.println("Cannot save abping configuration file because: " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
		}
	}

	private static StringTokenizer DB_ABPingFarmsSrcList() {
		final StringBuilder ls_farms_list = new StringBuilder();

		final DB db = new DB("SELECT DISTINCT mfarmsource from abping ORDER BY mfarmsource");
		while (db.moveNext())
			ls_farms_list.append(db.gets(1)).append(' ');

		return (new StringTokenizer(ls_farms_list.toString().trim()));
	}

	private static Vector<String> DB_ABPingFarmsSoaped() {
		final DB db = new DB("SELECT DISTINCT mfarmsource FROM abping;");

		final Vector<String> v = new Vector<>();

		while (db.moveNext()) {
			v.add(db.gets(1));
		}

		return v;
	}

	// COLORS MANAGEMENT *****************************************************
	private static String HTMLFileLoad(final String _filename) // get the content of the specified HTML file
	{
		int count;

		final File file = new File(_filename);

		final char[] chars = new char[(int) file.length()];

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			count = reader.read(chars);
		}
		catch (@SuppressWarnings("unused") final IOException e) {
			return "";
		}

		return new String(chars, 0, count);
	}

	private void SH_ColorSitesList(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		@SuppressWarnings("resource")
		final PrintWriter out = res.getWriter();

		final Properties prop = Utils.getProperties(sConfDir, "global");

		lib_drawHtml.drawHeader(out, res, "Site colors management");

		out.print("<CENTER>" + HTMLFileLoad(sResDir + "ColorsPicker1.htm"));
		if (req.getParameter("siteName") != null)
			out.print(" value=\"" + Format.escHtml(req.getParameter("siteName")) + "\"");
		out.println(HTMLFileLoad(sResDir + "ColorsPicker2.htm"));

		showList(toVector(prop, "Farms", null, true, false), out, "AVAILABLE SITES");

		final int iOtherColorSets = pgeti(prop, "colors.sets", 0);

		for (int i = 0; i < iOtherColorSets; i++)
			showList(toVector(prop, "colors.set_" + i + ".list", null, true, false), out, pgets(prop, "colors.set_" + i + ".title"));

		out.println("</td></CENTER>");

		lib_drawHtml.closeHtml(out);
		out.close();
	}

	private static void showList(final Vector<String> v, final PrintWriter out, final String sTitle) {
		final DB db = new DB();
		int i = 0;
		String bgcolor = null;
		String ls_siteName;

		out.println("<br><b>" + sTitle + ":</B><br><br><table width=600 border=0 cellpadding=0>");

		for (final String element : v) {
			ls_siteName = element;

			db.query("SELECT R,G,B,shape from colors WHERE sitename = '" + ls_siteName + "'");

			if (db.moveNext())
				bgcolor = "#" + toHex(db.geti(1)) + toHex(db.geti(2)) + toHex(db.geti(3));
			else
				bgcolor = "";

			i++;
			if (i % 4 == 1)
				out.print("<tr>");

			out.print("<td width=15 bgcolor=" + bgcolor + ">&nbsp;</td>");
			out.println("<td><a href=\"javascript:showSite('" + ls_siteName + "', '" + bgcolor + "', '" + db.gets(4) + "')\">" + ls_siteName + "</a></td>");

			if (i % 4 == 0)
				out.print("</tr>");
		}
		if (i % 4 != 1)
			out.print("</tr>");

		out.println("</table>");
	}

	private void DB_ColorPaletteSelect(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		@SuppressWarnings("resource")
		final PrintWriter out = res.getWriter();

		res.setContentType("text/html");
		out.println("<html><head><title>Color Update</title>");
		out.print(HTMLFileLoad(sResDir + "ColorsPicker1.htm"));
		out.print(" value=" + Format.escHtml(req.getParameter("siteName")));
		out.println(HTMLFileLoad(sResDir + "ColorsPicker2.htm"));
		out.close();
	}

	private void DB_ColorPaletteUpdate(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
		// PrintWriter out = res.getWriter();
		final String ls_colorhexval = req.getParameter("hexval");
		final String ls_sitename = req.getParameter("sitename");
		String sShape = req.getParameter("shape");

		if (ls_sitename.length() <= 0) {
			SH_ColorSitesList(req, res);
			return;
		}

		if (ls_colorhexval.length() <= 0) {
			final DB db = new DB();
			db.query("DELETE FROM colors WHERE sitename = '" + ls_sitename + "';");

			SH_ColorSitesList(req, res);
			return;
		}

		final String R = String.valueOf(hex2dec(ls_colorhexval.substring(1, 3)));
		final String G = String.valueOf(hex2dec(ls_colorhexval.substring(3, 5)));
		final String B = String.valueOf(hex2dec(ls_colorhexval.substring(5, 7)));

		if (sShape == null || sShape.length() != 1)
			sShape = sDefaultShape;

		final DB db = new DB("SELECT * from colors WHERE sitename = '" + ls_sitename + "'");
		if (!db.moveNext())
			db.query("INSERT INTO colors VALUES('" + ls_sitename + "'," + R + "," + G + "," + B + ", '" + sShape + "')");
		else
			db.query("UPDATE colors SET R=" + R + ", G=" + G + ", B=" + B + ", shape='" + sShape + "' WHERE sitename = '" + ls_sitename + "'");

		// Rewrite the colors.properties file

		try (PrintStream cout = new PrintStream(new FileOutputStream(sConfDir + "colors.properties"))) {
			cout.println("# This file was automatically generated by the color configuration servlet.");
			cout.println("# Please do not edit this file by hand, any changes will be lost when you will use the configuration tool again.");
			cout.println();

			db.query("SELECT sitename,R,G,B,shape from colors ORDER by sitename;");
			while (db.moveNext()) {
				String sSite = db.gets(1);
				sSite = Formatare.replaceChars(sSite, new char[] { ' ', '\t', ':', '=' }, new String[] { "\\ ", "\\\t", "\\:", "\\=" });

				cout.println(sSite + ".color=" + db.gets(2) + " " + db.gets(3) + " " + db.gets(4));

				sShape = db.gets(5);

				if (sShape.length() != 1)
					sShape = sDefaultShape;

				cout.println(sSite + ".shape=" + sShape);
			}
		}

		// clear the cache so that changes will be seen immediately
		CacheServlet.clearCache();

		if (req.getParameter("newstyle") != null)
			showColors();
		else
			SH_ColorSitesList(req, res);
	}

	private static int hex2dec(final String str) {
		int value = 0;
		for (int i = 0; i < str.length(); i++)
			value = value * 16 + hexValue(str.charAt(i));

		return value;
	}

	private static int hexValue(final char ch) {
		if (ch >= '0' && ch <= '9')
			return (ch - '0');
		if (ch >= 'a' && ch <= 'f')
			return (ch - 'a') + 10;
		if (ch >= 'A' && ch <= 'F')
			return (ch - 'A') + 10;

		return -1;
	}

	private static String toHex(final int i) {
		String sRez = "";

		sRez += i / 16 < 10 ? (char) ('0' + (i / 16)) : (char) ('A' + (i / 16) - 10);
		sRez += i % 16 < 10 ? (char) ('0' + (i % 16)) : (char) ('A' + (i % 16) - 10);

		return sRez;
	}

}
