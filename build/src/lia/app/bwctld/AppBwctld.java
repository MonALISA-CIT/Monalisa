package lia.app.bwctld;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import lia.Monitor.monitor.AppConfig;
import lia.app.AppInt;
import lia.app.AppUtils;

public class AppBwctld implements AppInt {

	String sFile = null;
	String sLimits = "";
	Properties prop = new Properties();

	public static final String sConfigOptions =
		"########### Required parameters : ############\n"
			+ "#iperfcmd iperflocation to be passed to bwctld\n"
			+ "##############################################\n\n";

	String sParameters = "";

	String MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME", "../..");
	String conf_home = MonaLisa_home + "/Control/conf/";

	public String getName() {
		return "lia.app.AppBwctld";
	} // getName

	public boolean start() {
		String vsCommand[];

		vsCommand =
			new String[] {
				"/bin/bash",
				"-c",
				MonaLisa_home
					+ "/Control/bin/bwctld -c "
					+ MonaLisa_home
					+ "/Control/conf/"
					+ " &>/dev/null </dev/null &" };

		//System.out.println(AppUtils.getOutput(vsCommand));

		return true;
	} // start

	public boolean stop() {
		AppUtils.getOutput(new String[] { "killall", "bwctld" });
		return true;
	} // stop

	public boolean restart() {
		stop();
		return start();
	} // restart

	public int status() {
		//return AppUtils.APP_STATUS_RUNNING;
		String sRunning =
			AppUtils.getOutput(
				new String[] {
					"/bin/sh",
					"-c",
					"pstree -u `id -u -n`| grep bwctld" });

		if (sRunning == null || sRunning.indexOf("bwctld") < 0)
			return AppUtils.APP_STATUS_STOPPED;
		else
			return AppUtils.APP_STATUS_RUNNING;
	} // status

	// TODO -asta tre' sa fie modificata
	public String info() {
		// xml with the version & stuff
		StringBuilder sb = new StringBuilder();
		sb.append("<config app=\"Bwctld\">\n");
		getConfFile(sb, MonaLisa_home + "/Control/conf/" + sFile);
		getLimitFile(sb);
		getKeyFile(sb);
		sb.append("</config>\n");

		return sb.toString();
	} // info

	private void getKeyFile(StringBuilder sb) {
		if (sb == null)
			return;

		sb.append("<file name=\"bwctld.keys\">\n");

		String keyPath = MonaLisa_home + "/Control/conf/bwctld.keys";
		String fileContent = "";
		File key = new File(MonaLisa_home + "/Control/conf/bwctld.keys");
		if (key.exists()) {

		} else {
			try {
				key.createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			} // try - catch
		} // if - else

		try {
			BufferedReader br = new BufferedReader(new FileReader(key));
			String line = br.readLine();
			int nr = 1;
			while (line != null) {
				StringTokenizer st = new StringTokenizer(line, " \t");
				String keyn = "";
				String value = "";

				if (st.hasMoreElements()) {
					keyn = st.nextToken();

				} // if

				if (st.hasMoreElements()) {
					value = st.nextToken();

				}

				sb.append(
					"<key name=\""
						+ keyn
						+ "\""
						+ " value=\""
						+ AppUtils.enc(value)
						+ "\" line=\""
						+ nr
						+ "\" read=\"true\" write=\"false\"/>");
				line = br.readLine();
				nr++;
			} // while
		} catch (Exception e) {
			e.printStackTrace();
		} // try -catch

		sb.append("</file>\n");

	} // getKeyFile

	private void getLimitFile(StringBuilder sb) {
		if (sb == null)
			return;

		sb.append("<file name=\"bwctld.limits\">\n");

		String limitPath = MonaLisa_home + "/Control/conf/bwctld.limits";
		String fileContent = "";

		File limit = new File(MonaLisa_home + "/Control/conf/bwctld.limits");
		if (limit.exists()) {

		} else {
			try {
				limit.createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			} // try - catch
		} // if - else

		try {
			BufferedReader br = new BufferedReader(new FileReader(limit));
			String line = br.readLine();
			int nr = 1;
			while (line != null) {

				fileContent = fileContent + line + "\n";
				line = br.readLine();

			} // while

			br.close();
		} catch (Exception e) {
			e.printStackTrace();
			fileContent = "";
		}

		sb.append(
			"<key name=\"limits\" value=\""
				+ AppUtils.enc(fileContent)
				+ "\" line=\"1\" read=\"true\" write=\"true\"/>");

		sb.append("</file>\n");
	} // getLimitFile

	private void saveLimits(String limits) {
		sLimits = limits;
	} // saveLimits

	private static final void getConfFile(StringBuilder sb, String sFile) {

		sb.append(
			"<file name=\""
				+ sFile.substring(sFile.lastIndexOf("/") + 1)
				+ "\">\n");

		try {
			BufferedReader br = new BufferedReader(new FileReader(sFile));

			String s = null;
			int line = 0;

			while ((s = br.readLine()) != null) {
				s = s.trim();
				line++;

				if (s.length() > 0 && !s.startsWith("#")) {
					String sKey = "";
					String sValue = "";
					if (s.indexOf(" ") > 0) {
						sKey =
							s
								.substring(0, s.indexOf(" "))
								.replace('\t', ' ')
								.trim();
						sValue = s.substring(s.indexOf(" ") + 1).trim();
						if (sValue == null)
							sValue = "";
					} else {
						sKey = s;
						sValue = "";
					}

					sb.append(
						"<key name=\""
							+ AppUtils.enc(sKey)
							+ "\" value=\""
							+ AppUtils.enc(sValue)
							+ "\" line=\""
							+ line
							+ "\" read=\"true\" write=\"true\"/>\n");
				}
			}

		} catch (Exception e) {
			System.err.println("error : " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
		} // try - catch

		sb.append("</file>\n");
	} // getConfFile

	public String exec(String sCmd) {

		String command = "";
		String cmd = "";
		StringTokenizer st = new StringTokenizer(sCmd, " ");
		if (st.hasMoreElements()) {
			cmd = st.nextToken();
		} else {
			return "Not a valid command";
		}

		if (cmd.equals("aespasswd")) {
			try {
				File key = new File(conf_home + "bwctld.keys");
				if (!key.exists()) {
					key.createNewFile();
				}

				command = cmd + " -f " + conf_home + "bwctld.keys ";
				while (st.hasMoreElements()) {
					command = command + st.nextToken() + " ";
				}
				return AppUtils.getOutput(
					new String[] { "/bin/bash", "-c", command });
			} catch (Exception e) {
				return "aespasswd exception .... ";
			}
		} //if

		
		if (cmd.equals("bwctl")) {
			command = command + MonaLisa_home + "/Control/bin/bwctl ";
			String lastElement="";
			
			boolean i = false;
			boolean d = false;
			boolean p = false;
			String cs="";
			
			while (st.hasMoreElements()) {
				if (lastElement.equals("-c") || lastElement.equals ("-s")) {
					cs=lastElement;
				}else {
					command = command + lastElement + " ";
				}
				lastElement = st.nextToken();
				if (lastElement.equals("-I")) {
					i=true;
				} // if
				if (lastElement.equals ("-d")) {
					d=true;
				} // if
				if (lastElement.equals("-p")) {
					p = true ;
				} // if
				
			} // while

			if (i==true) {
				if (d==false && p==false) {
					// create data dir if not exists .... 
					File dataDir = new File (MonaLisa_home+"/Control/datas");
					if (!dataDir.exists())
						dataDir.mkdir();
					command=command+" -d "+MonaLisa_home+"/Control/datas "+" -p ";
				} else if (d==false || p==false){
					return "bwctl: command sintax error";
				}
			} // set interactive
			
			command = command+" "+cs+" "+lastElement;

			return AppUtils.getOutput(
				new String[] { "/bin/bash", "-c", command });
		}

		return "Not a valid command";
	} // exec

	public boolean update(String sUpdate) {
		return update(new String[] { sUpdate });
	} // update

	private boolean updateLimitFile(
		String sFile,
		int iLine,
		String sName,
		String sCMD,
		String sNewValue) {

		if (sCMD.equals("rename")
			|| sCMD.equals("insert")
			|| sCMD.equals("delete")
			|| sCMD.equals("insertsection")) {
			System.out.println(
				"bwctld.limits: command " + sCMD + " is ingnored");
			return true;
			// this commands have no sense for /proc file system module
		}

		if (!sCMD.equals("update")) {
			System.out.println("bwctld.limit : command is not 'update'");
			return false; // the only valid command
		}

		if (sNewValue == null)
			sNewValue = "";
		AppUtils.saveFile(
			MonaLisa_home + "/Control/conf/bwctld.limits",
			sNewValue);
		sLimits = sNewValue;

		return true;
	} // updateLimitFile

	private static final boolean updateConfFile(
		String sFile,
		int iLine,
		String sName,
		String sCMD,
		String sNewValue) {

		Vector v = AppUtils.getLines(AppUtils.getFileContents(sFile));

		if (v == null)
			return false;

		iLine--;

		if (iLine < 0 || iLine >= v.size())
			return false;

		String s = (String) v.elementAt(iLine);
		s = s.trim();

		if (s.length() > 0 && !s.startsWith("#")) {
			String sKey = "";
			String sValue = "";
			if (s.indexOf(" ") > 0) {
				sKey = s.substring(0, s.indexOf(" ")).replace('\t', ' ').trim();
				sValue = s.substring(s.indexOf(" ") + 1).trim();
				if (sValue == null)
					sValue = "";
			} else {
				sKey = s.replace('\t', ' ').trim();
				sValue = "";
			}

			if (sKey.equals(sName)) {
				// ok, what's to be done ?

				if (sCMD.equals("delete")) {
					v.remove(iLine);
				}

				if (sCMD.equals("update")) {
					v.set(iLine, sKey + " " + sNewValue);
				}

				if (sCMD.equals("rename")) {
					v.set(iLine, sNewValue + " " + sValue);
				}

				if (sCMD.equals("insert")) {
					v.add(
						iLine + 1,
						sNewValue + (sNewValue.indexOf(" ") > 0 ? "" : " "));
				}

				if (sCMD.equals("insertsection")) {
					//this command has no effect
				}

				return AppUtils.saveFile(sFile, v);
			} else {
				return false;
			}
		}

		return false;
	} // updateConfFile

	public boolean update(String vs[]) {

		try {
			for (int o = 0; o < vs.length; o++) {
				String sUpdate = vs[o];

				StringTokenizer st = new StringTokenizer(sUpdate, " ");
				int i;

				String sFile = AppUtils.dec(st.nextToken());
				int iLine = 0;
				try {
					iLine = Integer.parseInt(st.nextToken());
				} catch (Exception e) {
					e.printStackTrace();
					iLine = 0;
				}
				String sName = AppUtils.dec(st.nextToken());
				String sCMD = AppUtils.dec(st.nextToken());

				String sValue = null;

				while (st.hasMoreTokens()) {
					sValue =
						(sValue == null ? "" : sValue + " ")
							+ AppUtils.dec(st.nextToken());
				}

				// make sure we have correct parameters
				if (!sCMD.equals("update")
					&& !sCMD.equals("rename")
					&& !sCMD.equals("insert")
					&& !sCMD.equals("insertsection")
					&& !sCMD.equals("delete")) {
					System.err.println("unknown command : " + sCMD);
					return false;
				}

				if (sFile.equals("bwctld.conf"))
					return updateConfFile(
						MonaLisa_home + "/Control/conf/" + sFile,
						iLine,
						sName,
						sCMD,
						sValue);

				if (sFile.equals("bwctld.limits"))
					return updateLimitFile(
						MonaLisa_home + "/Control/conf/" + sFile,
						iLine,
						sName,
						sCMD,
						sValue);

			}
			return true;
		} catch (Exception e) {
			System.out.println(
				"bwctld : exception : " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
			return false;
		} // try - catch
	} // update

	public String getConfiguration() {

		StringBuilder sb = new StringBuilder();

		sb.append(sConfigOptions);

		Enumeration e = prop.propertyNames();

		while (e.hasMoreElements()) {
			String s = (String) e.nextElement();

			sb.append(s + " " + prop.getProperty(s) + "\n");
		}

		return sb.toString();
	} // getConfiguration

	public boolean updateConfiguration(String s) {
		return AppUtils.updateConfig(sFile, s) && init(sFile);
	} // updateConfiguration

	public boolean bFirstRun = true;

	public boolean init(String sPropFile) {

		while (MonaLisa_home.endsWith("/"))
			MonaLisa_home =
				MonaLisa_home.substring(0, MonaLisa_home.length() - 1);

		sFile = sPropFile;
		AppUtils.getConfig(prop, sFile);

		if (prop.getProperty("iperfcmd") != null
			&& prop.getProperty("iperfcmd").length() > 0) {
			sParameters = prop.getProperty("iperfcmd");
		} else {
			sParameters = "";
		}

		if (bFirstRun) {
			extractFiles();
			bFirstRun = false;
		}

		return true;
	} // init

	public String getConfigFile() {
		return sFile;
	}

	private void extractFiles() {
		try {
			try {
				(new File(MonaLisa_home + "/Control/bin")).mkdirs();
			} catch (Exception e) {
			}

			// hope this never changes :)
			JarFile jf = new JarFile(MonaLisa_home + "/Control/lib/bwctld.jar");
			Enumeration e = jf.entries();
			byte[] buff = new byte[1024];

			while (e.hasMoreElements()) {
				JarEntry je = (JarEntry) e.nextElement();
				String s = je.toString();

				if (s.endsWith("bwctld")) {
					try {
						(new File(MonaLisa_home + "/Control/bin/bwctld"))
							.delete();
					} catch (Exception ee) {
					}

					BufferedInputStream bis =
						new BufferedInputStream(jf.getInputStream(je));
					FileOutputStream fos =
						new FileOutputStream(
							MonaLisa_home + "/Control/bin/bwctld");

					try {
						for (;;) {
							int bNO = bis.read(buff);
							if (bNO == -1)
								break;
							fos.write(buff, 0, bNO);
						}
					} catch (Throwable t) {
					}

					bis.close();
					fos.close();

					AppUtils.getOutput(
						new String[] {
							"/usr/bin/chmod",
							"a+x",
							MonaLisa_home + "/Control/bin/bwctld" });

					break;
				}
			}
		} catch (Exception e) {
			System.err.println(
				"bwctld: cannot extract: " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
		}
	}

} // class
