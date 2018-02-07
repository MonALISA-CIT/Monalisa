import java.io.*;
import java.util.*;

public class VODescriptor {

		private static final String MAP_PATH = "/monitoring/grid3-user-vo-map.txt";

		private static String VDT_LOCATION = getVdtLocation();

		/* Classes to get path and environment */
		public static final HashMap getDefaultParameters() {
				HashMap res = new HashMap();
				String str = getGlobalEnvProperty("GLOBUS_LOCATION");
				if (str!=null)
						res.put("GLOBUS_LOCATION", str);
				str = getGlobalEnvProperty("VDT_LOCATION");
				if (str!=null)
						res.put("VDT_LOCATION", str);
				return res;
		}

		public static final void setParameters(String[] pars, HashMap map) {
				for (int i=0; i<pars.length; i++) {
						if (pars[i].startsWith("GLOBUS_LOCATION"))
								//map.put("GLOBUS_LOCATION", pars[i].substring(16));
								map.put("GLOBUS_LOCATION", pars[i].substring("GLOBUS_LOCATION=".length()));
						if (pars[i].startsWith("VDT_LOCATION"))
								map.put("VDT_LOCATION", pars[i].substring("VDT_LOCATION=".length()));
				}
		}

		public static final String getGlobusLocation(HashMap map) {
				String res = (String) map.get("GLOBUS_LOCATION");
				if (res==null)
						res = getVdtLocation(map)+"/globus";
				return res;
		}
		public static final String getVdtLocation(HashMap map) {
				String res = (String) map.get("VDT_LOCATION");
				if (res==null)
						res = getVdtLocation();
				return res;
		}
		public static final String getVdtLocation() {
				//try to get the system variable?
				//function to read environment variables?
				String res = System.getProperty("MonaLisa_HOME");
				if (res==null) {
						//other euristics?
						res = ".";
				} else 
						res += "/..";
				return res;
		}


		private static Properties globalEnv = null;
		public final static String getGlobalEnvProperty(String key) {
				if ( globalEnv == null ) {
						try {
								globalEnv = new Properties();
								BufferedReader br = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("env").getInputStream()));
								for(;;){
										String line = br.readLine();
										if ( line == null ) break;
										if ( line.indexOf("=") != -1 ) {
												String[] splitLine = line.split("=");
												if ( splitLine != null && splitLine.length == 2)
														globalEnv.put(splitLine[0], splitLine[1]);
										}
								}
								if ( br!= null) br.close();
						} catch ( Throwable t ) {
								globalEnv = null;
						}
				}
				
				if ( globalEnv == null ) return null;

				return globalEnv.getProperty(key, null);
		}


		//in the future allow more VOD with a string-id/file-id
		private static VODescriptor one = null;

		public static VODescriptor getVOD() {
				if (one==null) {
						one = new VODescriptor(VDT_LOCATION+MAP_PATH);
				}
				return one;
		}

		//each user belongs to 1 VO
		//each VO may have more users
		private String mapfile = null;
		private HashMap mapVOid = null;
		private HashMap mapVOCNid = null;
		private HashMap mapUSRtoVO = null;
		private String[] voList = null;
		private String[] voListCN = null;
		private String[][] voUSRList = null;

		public VODescriptor(String file) {
				mapfile = file;
				//if file exist, load it, else
				loadDefaultMaps();
		}

		public String[] getVOList() { return voList; }

		public String[] getVOListCN() { return voListCN; }

		public int getVOid(String voname) { 
				Object ob = mapVOid.get(voname); 
				if (ob==null)
						return -1;
				return ((Integer) ob).intValue(); 
		}

		public String getVOName2CN(String voname) { 
				Object ob = mapVOid.get(voname); 
				if (ob==null)
						return null;
				return voListCN[((Integer) ob).intValue()]; 
		}

		public String[] getVOUsers(String voname) { 
				Object ob = mapVOid.get(voname); 
				if (ob==null)
						return null;
				return voUSRList[((Integer) ob).intValue()]; 
		}

		public String[] getVOUsers(int voi) { return voUSRList[voi]; }

		public String getUserVO(String user) { 
				Object ob = mapUSRtoVO.get(user);
				if (ob==null)
						return null;
				return voList[((Integer) ob).intValue()]; 
		}
		public int getUserVOid(String user) { 
				Object ob = mapUSRtoVO.get(user); 
				if (ob==null)
						return -1;
				return ((Integer) ob).intValue(); 
		}

		private void loadMaps(File file) {
		}

		private void loadDefaultMaps() {
		/* This loads a default internal hard-coded set of values rather than
			 retrieving them from a file.
		*/
				voList = new String[] {"uscms",
															 "usatlas",
															 "sdss",
															 "ligo",
															 "ivdgl",
															 "btev",
				};
				voListCN = new String[] {"CMS",
																 "ATLAS",
																 "SDSS",
																 "LIGO",
																 "iVDgL",
																 "BTeV",
				};

			  voUSRList = new String[][] {{"uscms01", "uscms02"},
																		{"usatlas1"},
																		{"lsc01"},
																		{"ivdgl"},
																		{"sdss"},
																		{"btev"},
				};

				//VO-id maps
				mapVOid = new HashMap();
				for (int i=0; i<voList.length; i++) {
						mapVOid.put(voList[i], new Integer(i));
				}
				mapVOCNid = new HashMap();
				for (int i=0; i<voListCN.length; i++) {
						mapVOCNid.put(voListCN[i], new Integer(i));
				}

				//users-VOid map
				mapUSRtoVO = new HashMap();
			  mapUSRtoVO.put("uscms01", mapVOid.get("uscms"));
			  mapUSRtoVO.put("uscms02", mapVOid.get("uscms"));
				mapUSRtoVO.put("usatlas1", mapVOid.get("atlas"));
				mapUSRtoVO.put("lsc01", mapVOid.get("ligo"));
				mapUSRtoVO.put("ivdgl", mapVOid.get("ivdgl"));
				mapUSRtoVO.put("sdss", mapVOid.get("sdss"));
				mapUSRtoVO.put("btev", mapVOid.get("btev"));


		}
}
