package lia.web.servlets.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
	private static final long	serialVersionUID	= -2190075295184456255L;

	private static HashMap<String, String>	hmNames = new HashMap<String, String>();

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
		
		db.syncUpdateQuery("ALTER TABLE colors ADD COLUMN shape char(1) DEFAULT '"+sDefaultShape+"';", true);
		
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
				} catch (Throwable t) {
					System.err.println(t);
					t.printStackTrace();
				}
			}
		}.start();
	}

	private String	sZoneDir	= "";

	private String	sResDir		= "";

	private String	sConfDir	= "";

	/**
	 * Init function
	 * 
	 * @param req
	 * @param res
	 */
	public final void doInit(HttpServletRequest req, HttpServletResponse res) {
		ServletContext sc = getServletContext();

		sZoneDir = sc.getRealPath("/");
		if (!sZoneDir.endsWith("/"))
			sZoneDir += "/";

		sConfDir = sZoneDir + "WEB-INF/conf/";

		sResDir = sZoneDir + "WEB-INF/res/";

		response = res;
		request = req;
	}

	//********************** doGet *******************************************
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
		doInit(req, res);

		String l_queryType = gets("function");

		if (l_queryType.equals("ABPingWriteConfigurationToFile"))
			DB_ABPingFarmConfFile_Create();
		else if (l_queryType.equals("ABPingFarmsDiscover")) {
			DB_ABPingFarmsDiscover();
			ABPingAsMatrix(req, res);
		} else if (l_queryType.equals("ABPingFarmsList"))
			DB_ABPingFarmsList(req, res);
		else if (l_queryType.equals("ABPingFarmView"))
			DB_ABPingFarmView(req, res, req.getParameter("farmName"));
		else if (l_queryType.equals("ColorSitesList"))
			SH_ColorSitesList(req, res);
		else if (l_queryType.equals("ColorSitesListNew"))
			showColors();
		else if (l_queryType.equals("ColorUpdate"))
			DB_ColorPaletteSelect(req, res);
		else
			ABPingAsMatrix(req, res);

		// ResourceBundle rb = ResourceBundle.getBundle("LocalStrings",req.getLocale());
		// res.setContentType("text/html");
		// String title = rb.getString("helloworld.title");
	}

	/**
	 * 
	 */
	private void showColors() throws IOException {
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
			
			if (j % 4 == 3){
				pSet.append(pSetLine);
				bDirty = false;
			}
			else{
				bDirty = true;
			}
		}
		
		if (bDirty)
			pSet.append(pSetLine);

		p.append(pSet);
	}

	private static final String	vsColors[]		= { "FFFFFF", "CCCCCC", "CCCCFF", "AAAACC", "FF0000" };

	private static final String	vsSiteColors[]	= { "000000", "0000CC" };

	/**
	 * Build the matrix of hosts
	 * 
	 * @param req
	 * @param res
	 * @throws IOException
	 */
	public void ABPingAsMatrix(HttpServletRequest req, HttpServletResponse res) throws IOException {
		Page p = new Page(res.getOutputStream(), sResDir + "abping/abping.res");
		Page ph = new Page(sResDir + "abping/header.res");
		Page pl = new Page(sResDir + "abping/line.res");
		Page pe = new Page(sResDir + "abping/element.res");
		Page pn = new Page(sResDir + "abping/null.res");

		updateNames();

		HashMap<String, String> hmAliases = new HashMap<String, String>();
		DB db = new DB();
		db.query("SELECT ip,name FROM abping_aliases UNION SELECT ip,name FROM abping_aliases_extra;");
		while (db.moveNext())
			hmAliases.put(db.gets(1), db.gets(2));

		db.query("SELECT distinct mfarmdest FROM abping ORDER BY mfarmdest ASC;");

		Vector<String> vDest = new Vector<String>();

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
		HashMap<String, String> hmConnected = new HashMap<String, String>();
		while (db.moveNext())
			hmConnected.put(db.gets("mfarmsource") + "@" + db.gets("mfarmdest"), "");

		db.query("SELECT distinct mfarmsource FROM abping ORDER BY mfarmsource ASC;");

		int iLine = 0;

		while (db.moveNext()) {
			String src = db.gets("mfarmsource");
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

				String dst = vDest.get(i);
				String dsta = dst;
				if (hmAliases.get(dsta) != null)
					dsta = hmAliases.get(dsta);

				boolean connected = hmConnected.get(src + "@" + dst) != null;

				pe.modify("connected", connected ? "1" : "0");
				pe.modify("source", src);
				pe.modify("dest", dst);
				pe.modify("source_alias", srca);
				pe.modify("dest_alias", dsta);

				if (!connected) {
					pe.modify("color", vsColors[iLine % 2 + (i % 2) * 2]);
				} else {
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

	//*********************** doPost ***************************
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
		doInit(req, res);

		String ip = gets("ip");
		String un = gets("fqn");

		if (ip.length() > 0 && un.length() > 0) {
			DB db = new DB();

			db.syncUpdateQuery("DELETE FROM abping_aliases_extra WHERE ip='" + ip + "';");
			db.syncUpdateQuery("DELETE FROM abping_aliases_extra WHERE name='" + un + "';");
			db.syncUpdateQuery("INSERT INTO abping_aliases_extra (ip, name) VALUES ('" + ip + "', '" + un + "');");

			db.setReadOnly(true);
			
			if (db.query("SELECT mfarmsource FROM abping WHERE mfarmsource='" + ip + "';", true) && !db.moveNext()) {
				DB db2 = new DB();

				db.query("SELECT distinct mfarmsource FROM abping;");

				if (db.moveNext()) {
					do {
						db2.query("INSERT INTO abping VALUES ('" + ip + "', '" + db.gets(1) + "', 0);", true);
						db2.query("INSERT INTO abping VALUES ('" + db.gets(1) + "', '" + ip + "', 0);", true);
					} while (db.moveNext());

					db2.query("DELETE FROM abping WHERE mfarmsource=mfarmdest;");
				} else {
					db2.query("INSERT INTO abping VALUES ('" + ip + "', '" + ip + "', 0);");
				}
			}

			redirect("abping?function=Matrix");
			return;
		}

		String buttonLabel = gets("button");
		if (buttonLabel.compareTo("Update") == 0)
			DB_ABPingFarmUpdate(req, res);
		if (buttonLabel.compareTo("DoIt!") == 0)
			DB_ColorPaletteUpdate(req, res);

		DB db = new DB();
		db.query("UPDATE abping SET is_connected=0 WHERE is_connected IS NULL OR is_connected!=0;");

		if (buttonLabel.length() <= 0) {
			String vs[] = request.getParameterValues("bife");

			for (int i = 0; vs != null && i < vs.length; i++) {
				String s = vs[i];

				String src = s.substring(0, s.indexOf("@")).trim();
				String dst = s.substring(s.indexOf("@") + 1).trim();

				insert(src, dst);

				if (geti("simetric") != 0)
					insert(dst, src);
			}

			DB_ABPingFarmConfFile_Create();

			redirect("abping?function=Matrix");
		}
	}

	private static void insert(String src, String dst) {
		DB db = new DB();
		db.query("SELECT mfarmsource FROM abping WHERE mfarmsource='" + src + "' AND mfarmdest='" + dst + "';");

		if (db.moveNext())
			db.query("UPDATE abping SET is_connected=1 WHERE trim(mfarmsource)='" + src + "' AND trim(mfarmdest)='" + dst + "';");
		else
			db.query("INSERT INTO abping VALUES ('" + src + "','" + dst + "',1);");
	}

	
	private void DB_ABPingFarmsDiscover(){
		Vector<String> v = DB_ABPingFarmsSoaped();

		DB db = new DB();

		for (int i = 0; i < v.size(); i++)
			for (int j = 0; j < v.size(); j++)
				if (i != j) {
					db.query("SELECT * FROM abping WHERE mfarmsource='" + v.get(i) + "' AND mfarmdest='" + v.get(j) + "';");
					if (!db.moveNext())
						db.query("INSERT INTO abping VALUES('" + v.get(i) + "','" + v.get(j) + "',0);");
				}
	}

	private void DB_ABPingFarmsList(HttpServletRequest req, HttpServletResponse res) throws IOException {
		PrintWriter out = res.getWriter();
		DB db = new DB("SELECT distinct mfarmsource FROM abping ORDER by mfarmsource;");

		lib_drawHtml.drawHeader(out, res, "ABPing: Farms List (hostname)");
		out.println("<b>AVAILABLE FARMS:</B><br><br>");

		while (db.moveNext())
			out.println("<a href=abping?function=ABPingFarmView&farmName=" + db.gets(1) + ">" + db.gets(1) + "</a><br>");

		out.println("<br>&nbsp;<br><a href=abping?function=ABPingFarmsDiscover>Refresh this list (might take a while if the database is big)</a><br>");
		lib_drawHtml.closeHtml(out);
		out.close();
	}

	private void DB_ABPingFarmView(HttpServletRequest req, HttpServletResponse res, String p_farmName) throws IOException {
		PrintWriter out = res.getWriter();
		DB db = new DB("SELECT mfarmdest, is_connected from abping WHERE mfarmsource = '" + p_farmName + "' ORDER by mfarmdest;");

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
		out.close();
	}

	private void DB_ABPingFarmUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
		PrintWriter out = res.getWriter();
		String ls_mfarmsrc = req.getParameter("mfarmsrc");
		String ls_mfarmdst = "";

		DB db = new DB();

		db.query("UPDATE abping SET is_connected=0 WHERE mfarmsource = '" + ls_mfarmsrc + "'");

		lib_drawHtml.drawHeader(out, res, ls_mfarmsrc + " ABPing connections");
		out.println("ABPing enabled from node <b>" + Format.escHtml(ls_mfarmsrc) + "</b> to nodes ");
		Enumeration<?> enumeration = req.getParameterNames();
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
		out.close();

		DB_ABPingFarmConfFile_Create();
	}

	/**
	 * 
	 */
	static void updateNames() {
		DB db = new DB();

		db.query("SELECT DISTINCT mfarmsource FROM abping;");
		while (db.moveNext()) {
			boolean bFound;

			final String src = db.gets(1);

			synchronized (hmNames) {
				bFound = hmNames.get(src) != null && !hmNames.get(src).equals(src);
			}

			if (!bFound) {
				try {
					InetAddress ia = InetAddress.getByName(src);
					String sName = lazyj.Utils.getHostName(ia.getHostAddress());

					synchronized (hmNames) {
						if (sName != null && sName.length() > 0)
							hmNames.put(src, sName);
						else
							hmNames.put(src, src);
					}
				} catch (Throwable t) {
					synchronized (hmNames) {
						hmNames.put(src, src);
					}
				}
			}
		}
	}

	private void DB_ABPingFarmConfFile_Create(){
		updateNames();

		String ls_mfarmsrc;

		PrintStream cout = null;
		
		try {
			cout = new PrintStream(new FileOutputStream(sZoneDir + "ABPingFarmConfig"));

			DB db = new DB();

			StringTokenizer st = DB_ABPingFarmsSrcList();
			while (st.hasMoreTokens()) {
				ls_mfarmsrc = st.nextToken();

				StringBuilder sb = new StringBuilder();
				db.query("SELECT mfarmdest from abping WHERE mfarmsource = '" + ls_mfarmsrc + "' AND is_connected = 1 ORDER BY mfarmdest;");
				while (db.moveNext())
					sb.append(" " + hmNames.get(db.gets(1)));

				String s = sb.toString();

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
		} catch (IOException e) {
			System.err.println("Cannot save abping configuration file because: "+e+" ("+e.getMessage()+")");
			e.printStackTrace();
		}
		finally {
			if (cout!=null)
				cout.close();
		}
	}

	private StringTokenizer DB_ABPingFarmsSrcList(){
		StringBuilder ls_farms_list = new StringBuilder();

		DB db = new DB("SELECT DISTINCT mfarmsource from abping ORDER BY mfarmsource");
		while (db.moveNext())
			ls_farms_list.append(db.gets(1)).append(' ');

		return (new StringTokenizer(ls_farms_list.toString().trim()));
	}

	private Vector<String> DB_ABPingFarmsSoaped() {
		DB db = new DB("SELECT DISTINCT mfarmsource FROM abping;");

		Vector<String> v = new Vector<String>();

		while (db.moveNext()) {
			v.add(db.gets(1));
		}

		return v;
	}

	// COLORS MANAGEMENT *****************************************************
	private static String HTMLFileLoad(String _filename) // get the content of the specified HTML file
	{
		char[] chars;
		int count;
		
		BufferedReader reader = null;
		
		try {
			File file = new File(_filename);
			chars = new char[(int) file.length()];
			
			reader = new BufferedReader(new FileReader(file));
			count = reader.read(chars);
		} 
		catch (FileNotFoundException e) {
			return "";
		} 
		catch (IOException e) {
			return "";
		}
		finally{
			if (reader!=null)
				try{
					reader.close();
				}
				catch (IOException ioe){
					// ignore
				}
		}

		return new String(chars, 0, count);
	}

	private void SH_ColorSitesList(HttpServletRequest req, HttpServletResponse res) throws IOException {
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

	private void showList(final Vector<String> v, final PrintWriter out, final String sTitle) {
		final DB db = new DB();
		int i = 0;
		String bgcolor = null;
		String ls_siteName;

		out.println("<br><b>" + sTitle + ":</B><br><br><table width=600 border=0 cellpadding=0>");

		for (int j = 0; j < v.size(); j++) {
			ls_siteName = v.get(j);

			db.query("SELECT R,G,B,shape from colors WHERE sitename = '" + ls_siteName + "'");

			if (db.moveNext())
				bgcolor = "#" + toHex(db.geti(1)) + toHex(db.geti(2)) + toHex(db.geti(3));
			else
				bgcolor = "";

			i++;
			if (i % 4 == 1)
				out.print("<tr>");

			out.print("<td width=15 bgcolor=" + bgcolor + ">&nbsp;</td>");
			out.println("<td><a href=\"javascript:showSite('" + ls_siteName + "', '" + bgcolor + "', '"+db.gets(4)+"')\">" + ls_siteName + "</a></td>");

			if (i % 4 == 0)
				out.print("</tr>");
		}
		if (i % 4 != 1)
			out.print("</tr>");

		out.println("</table>");
	}

	private void DB_ColorPaletteSelect(HttpServletRequest req, HttpServletResponse res) throws IOException {
		PrintWriter out = res.getWriter();

		res.setContentType("text/html");
		out.println("<html><head><title>Color Update</title>");
		out.print(HTMLFileLoad(sResDir + "ColorsPicker1.htm"));
		out.print(" value=" + Format.escHtml(req.getParameter("siteName")));
		out.println(HTMLFileLoad(sResDir + "ColorsPicker2.htm"));
		out.close();
	}

	private void DB_ColorPaletteUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
		//PrintWriter out = res.getWriter();
		String ls_colorhexval = req.getParameter("hexval");
		String ls_sitename = req.getParameter("sitename");
		String sShape = req.getParameter("shape");
	
		if (ls_sitename.length() <= 0) {
			SH_ColorSitesList(req, res);
			return;
		}

		if (ls_colorhexval.length() <= 0){
			DB db = new DB();
			db.query("DELETE FROM colors WHERE sitename = '"+ls_sitename+"';");
			
			SH_ColorSitesList(req, res);
			return;
		}
		
		String R = String.valueOf(hex2dec(ls_colorhexval.substring(1, 3)));
		String G = String.valueOf(hex2dec(ls_colorhexval.substring(3, 5)));
		String B = String.valueOf(hex2dec(ls_colorhexval.substring(5, 7)));
		
		if (sShape==null || sShape.length()!=1)
			sShape = sDefaultShape;

		DB db = new DB("SELECT * from colors WHERE sitename = '" + ls_sitename + "'");
		if (!db.moveNext())
			db.query("INSERT INTO colors VALUES('" + ls_sitename + "'," + R + "," + G + "," + B + ", '"+sShape+"')");
		else
			db.query("UPDATE colors SET R=" + R + ", G=" + G + ", B=" + B + ", shape='"+sShape+"' WHERE sitename = '" + ls_sitename + "'");

		// Rewrite the colors.properties file
		FileOutputStream writeFile = new FileOutputStream(sConfDir + "colors.properties");
		PrintStream cout = new PrintStream(writeFile);

		cout.println("# This file was automatically generated by the color configuration servlet.");
		cout.println("# Please do not edit this file by hand, any changes will be lost when you will use the configuration tool again.");
		cout.println();

		db.query("SELECT sitename,R,G,B,shape from colors ORDER by sitename;");
		int i = 0;
		while (db.moveNext()) {
			i++;
			
			String sSite = db.gets(1);
			sSite = Formatare.replaceChars(sSite, new char[]{' ', '\t', ':', '='}, new String[]{"\\ ", "\\\t", "\\:", "\\="});
			
			cout.println(sSite + ".color=" + db.gets(2) + " " + db.gets(3) + " " + db.gets(4));
			
			sShape = db.gets(5);
			
			if (sShape.length()!=1)
				sShape = sDefaultShape;
			
			cout.println(sSite + ".shape=" +sShape);
		}
		
		cout.flush();
		cout.close();

		// clear the cache so that changes will be seen immediately
		CacheServlet.clearCache();
		
		if (req.getParameter("newstyle")!=null)
			showColors();
		else
			SH_ColorSitesList(req, res);
	}

	private static int hex2dec(String str) {
		int value = 0;
		for (int i = 0; i < str.length(); i++)
			value = value * 16 + hexValue(str.charAt(i));

		return value;
	}

	private static int hexValue(char ch) {
		if (ch >= '0' && ch <= '9')
			return (ch - '0');
		if (ch >= 'a' && ch <= 'f')
			return (ch - 'a') + 10;
		if (ch >= 'A' && ch <= 'F')
			return (ch - 'A') + 10;

		return -1;
	}

	private static String toHex(int i) {
		String sRez = "";

		sRez += i / 16 < 10 ? (char) ('0' + (i / 16)) : (char) ('A' + (i / 16) - 10);
		sRez += i % 16 < 10 ? (char) ('0' + (i % 16)) : (char) ('A' + (i % 16) - 10);

		return sRez;
	}

}
