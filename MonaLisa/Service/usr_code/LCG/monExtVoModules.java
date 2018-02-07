/**
 * This class extends monVoModules in order to provide support for different
 * Grid distributions. Currently it works with OSG and LCG 2.4.
 */
//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.Observable;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.util.DateFileWatchdog;

public abstract class monExtVoModules extends VoModules {
	/** The name of the Grid distribution (OSG, LCG 2.4). */
	protected String gridDistribution = "OSG";
	
	/** The name of the user-VO map file in LCG 2.4. */
	protected static String LCG24_MAP_FILE = "users.conf";
	/** The name of the site info file in LCG 2.4. */
	protected static String LCG24_INFO_FILE = "site-info.def";
	
	/** The path to the LCG site info file (i.e., site-info.def). */
	protected String lcgInfoFile = null;
	/**	Wathcdog used to notify if the site info file has changed. */
    protected DateFileWatchdog infoFileWatchdog = null;
    
	protected monExtVoModules (String ModName, String[] inResTypes,
			String inNotifyProperty) { 
        super(ModName, inResTypes, inNotifyProperty);
    } 
	
	/**
	 * Returns User-VO account mapping file name (full path).
	 * Returns null, if not set.
    */
	protected String getMapFile() throws Exception {
        
		if (gridDistribution.equals("OSG"))
			return getOsgMapFile();
		
		if (gridDistribution.startsWith("LCG"))
			return getLcg24MapFile();
		
		return null;
	}
	
	/**
	 * Returns User-VO account mapping file name (full path) in an OSG.
	 *  Grid distribution. Returns null, if not set.
    */	
	protected String getOsgMapFile() throws Exception {
		/*
         First, check VDT_LOCATION env variable 
         Then, sets it to MonaLisa_HOME + /..
         */
        String methodName = "getOsgMapFile";
        String mapfile = null;
        debug("Searching for OSG User-VO map file.");
        String mappath = getEnvValue("VDT_LOCATION");
        if (mappath == null) {
            addToMsg(methodName,"VDT_LOCATION variable not set...checking MonaLisa_HOME.");
            mappath = getEnvValue("MonaLisa_HOME");
            if (mappath != null) {
                mappath = mappath + "/.."; 
            } else {
                addToMsg(methodName,"MonaLisa_HOME variable not set.");
                throw new Exception("Unable to determine "+MAP_FILE+" location. Terminating.");
            } // end if/else
        } // end if 
        // set the file name
        mapfile = mappath + MAP_FILE;
        addToMsg(methodName,"OSG User-VO map file used:"+mapfile);
        return mapfile;
    } 
    
	/**
	 * Returns User-VO account mapping file name (full path) in a LCG Grid.
	 * distribution. Returns null, if not set.
    */
	protected String getLcg24MapFile() throws Exception {
		/*
         First, check LCG_LOCATION env variable; if it is not set, 
         consider that LCG is installed in /opt/lcg
         */
        String methodName = "getLcg24MapFile";
        String mapfile = null;
        debug("Searching for LCG  User-VO map file.");
        String mappath = getEnvValue("LCG_LOCATION");
        if (mappath == null) {
            addToMsg(methodName,"LCG_LOCATION variable not set... using /opt/lcg.");
            mappath = "/opt/lcg";
        }
        mappath = mappath + "/yaim/examples/";
        mapfile = mappath + LCG24_MAP_FILE;
        addToMsg(methodName,"LCG User-VO map file used:"+mapfile);
        return mapfile;
    } 
	
	/**
	 * Returns User-VO account mapping file name (full path) in a LCG Grid.
	 * distribution. Returns null, if not set.
    */
	protected String getLcg24InfoFile() throws Exception {
		/*
         First, check LCG_LOCATION env variable; if it is not set, 
         consider that LCG is installed in /opt/lcg
         */
        String methodName = "getLcg24MapFile";
        String infofile = null;
        debug("Searching for LCG  User-VO map file.");
        String infopath = getEnvValue("LCG_LOCATION");
        if (infopath == null) {
            addToMsg(methodName,"LCG_LOCATION variable not set... using /opt/lcg.");
            infopath = "/opt/lcg";
        }
        infopath = infopath + "/yaim/examples/";
        infofile = infopath + LCG24_INFO_FILE;
        addToMsg(methodName,"LCG  Site info file used:" + infofile);
        return infofile;
    } 
	
	/**
	 * Constructs the User-VO mapping tables, based on the map file specific to
	 * the Grid distribution.
	 */
	protected void loadUserVoMapTable() throws Exception {
		if (gridDistribution.equals("OSG")) {
			loadOsgUserVoMapTable();
			return;
		}
		
		if (gridDistribution.startsWith("LCG"))
			loadLcg24UserVoMapTable();
			return;
	}
	
	protected void loadOsgUserVoMapTable() throws Exception {
        /*
         The configuration file containing the mappings of Unix user name
         to VO name is a whitespace delimited file in the format....
         unix_user vo_name
         Comments are on a line starting with an asterick (*).
         Empty lines are ignored. 
         
         This method loads the file into a hash table for use in other methods.
         */
        String methodName = "loadOsgUserVoMapTable";
        
        // -- clean the hashtables used in the mappings ---
        cleanupEnv();
        
        // ----------------------------------------------
        String record       = null;
        // ----------------------------------------------
        String unix         = null;
        String vo           = null;
        String[] voiList    = null;
        
        boolean voiFound    = false; // indicates if #voi map file reocrd was found
        boolean vocFound    = false; // indicates if #VOc map file reocrd was found
        
        int voiCnt = 0; // number of voi VOs in map file
        int vocCnt = 0; // number of VOc VOs in map file
        
        //String[] config = new String[];
        
        try {
            // -- get the User-VO map file name ---------
            if ( mapfile == null ) {
                debug("..getting mapfile");
                mapfile = getMapFile();
            } // end if
            checkConfigFile(mapfile, mapFileWatchdog);
            
            //---------------------------
            // Start processing the file
            //---------------------------
            addToMsg(methodName,"User-VO map table("+mapfile+")");
            FileReader fr     = new FileReader( mapfile );
            BufferedReader br = new BufferedReader(fr);
            Vector ignoredAccounts = new Vector();
            while ( (record = br.readLine()) != null) {
                debug("record: "+record);
                StringTokenizer tz = new StringTokenizer (record);
                if (tz.countTokens() == 0 ) { // check for empty line
                    continue;
                }
                debug("processing: "+record);
                addToMsg(methodName,"processing: "+record);
                //-----------------------------------------------------------------
                // Get the first work in the line and use it to see the record type
                //-----------------------------------------------------------------
                String token1 = tz.nextToken().trim();
                
                // --- process #voi record ----
                if (VOI.equals(token1 + " ")) { // lower case VO names
                    // --- verify that there are not more than 1 #voi record 
                    if ( voiFound ) {
                        br.close();
                        throw new Exception("Multiple #voi records found in map file.");
                    }
                    voiFound = true;
                    voiCnt = tz.countTokens();
                    voiList = new String[voiCnt];
                    for ( int i=0; i<voiCnt ; i++) {
                        vo = tz.nextToken().trim();
                        voiList[i] = vo;
                    } //end for
                    continue; // read next record
                } //end if
                
                // --- process #VOc record ----
                if (VOC.equals(token1 + " ")) { // mixed case VO names
                    if ( vocFound ) {
                        br.close();
                        throw new Exception("Multiple #VOc records found in map file.");
                    }
                    vocFound = true;
                    // --- verify that the #voi record was found first --------
                    if ( ! voiFound ) {
                        br.close();
                        throw new Exception("The #voi record must precede the #VOc record.");
                    }
                    // --- verify there is a 1 for 1 map of voi to voc VOs ----
                    vocCnt = tz.countTokens();
                    if ( voiCnt != vocCnt ) {
                        br.close();
                        throw new Exception("#voi("+voiCnt+") and #VOc("+vocCnt+") entries do not match.");
                    }
                    // -- build the lower to mixed case mappings -------------
                    debug("VOc: "+vocCnt+" VOs");
                    for ( int i=0; i<vocCnt; i++) {
                        vo = tz.nextToken().trim();
                        debug("lower("+voiList[i]+") upper("+vo+")");
                        voMixedCase.put(voiList[i],vo);
                    } //end for
                    continue; // read next record
                } //end if
                
                // --- process comment record ----
                if (COMMENT.equals(record.substring(0,1))) { // check for comment
                    continue; // read next record
                } //end if
                
                //---------------------------------------------------------------
                // Process the unix to lower case mapping records
                // (anything that falls through to here is considered a mapping)
                //---------------------------------------------------------------
                // --- verify that the #voi and $VOc records was found first --------
                if ( ! vocFound ) {
                    br.close();
                    throw new Exception("The #voi and #VOc records must precede the unix to VO mapping records.");
                }
                // ----------------------------------------------------
                // Since we already stripped off the first token, there should only
                // be 1 left.  Otherwise, it is probably a bad record and we will
                // ignore it.
                // ----------------------------------------------------
                int ni = tz.countTokens();
                if ( ni != 1 )  {
                    continue; // presumably just a bad line in the file 
                }
                // --------------------------------------------------------
                // This should be a mapping line of user to lower case VO
                // --------------------------------------------------------
                if ( ni == 1 )  {
                    unix = token1;
                    vo   = tz.nextToken().trim();
                    if ( voAccts.containsKey(unix) ) {
                        //br.close();
                        ignoredAccounts.add(unix);
                        logerr("Multiple mappings for unix account ("+unix+") ... this account will be ignored");
                        addToMsg(methodName,"Multiple mappings for unix account ("+unix+") ... this account will be ignored");
                        //throw new Exception("Multiple mappings for unix account ("+unix+")");
                    } //end if
                    voAccts.put(unix,vo);
                } else {
                    //br.close();
                    logerr("Unable to determine mapping from this entry("+record+")");
                    addToMsg(methodName,"Unable to determine mapping from this entry("+record+")");
                    //throw new Exception("Unable to determine mapping from this entry("+record+")");
                } //end if
            }
            
            if(ignoredAccounts.size() > 0 ) {
                logerr("Ignored unix accounts: " + ignoredAccounts.toString());
                addToMsg(methodName,"Ignored unix accounts: " + ignoredAccounts.toString());
                for(int iai = 0; iai < ignoredAccounts.size(); iai++) {
                    voAccts.remove(ignoredAccounts.elementAt(iai));
                }
            }
            
            br.close();
        } catch ( Exception e ) {
            throw new Exception("ERROR in mapping file: "+e.getMessage());
        }
    } // end method
    
	
	protected void loadLcg24UserVoMapTable() throws Exception {
		/*
		 The configuration file containing the mappings of Unix user name
		 to VO name has lines with the following format:
		 uid:unix_user:gid:group:vo_name:sgm_flag:
		 Empty lines are ignored. 
		 
		 This method loads the file into a hash table for use in other methods.
		 */
		String methodName = "loadLcg24UserVoMapTable";
		
		// -- clean the hashtables used in the mappings ---
		cleanupEnv();
		
		// ----------------------------------------------
		String record       = null;
		// ----------------------------------------------
		String unix         = null;
		String vo           = null;
		
		try {
			// -- get the site info file name ---------
			if (lcgInfoFile == null ) {
				debug("..getting LCG site info file");
				lcgInfoFile = getLcg24InfoFile();
			} // end if			
			checkConfigFile(lcgInfoFile, mapFileWatchdog);
			addToMsg(methodName, "Site info def file(" + lcgInfoFile + ")");
			
			// -- process the site info file => get the list of VOs ------
			FileReader fr = new FileReader(lcgInfoFile);
			BufferedReader br = new BufferedReader(fr);
			while ((record = br.readLine()) != null) {
				String line = record.trim();
				if (line.startsWith("#"))
					continue;
				if (line.startsWith("VOS=")) {
					line = line.replaceFirst("VOS=", "").replaceAll("\"", "");
					StringBuffer voMsg = new StringBuffer("VO list from the site info file: ");
					String[] volist = line.split("(\\s)+");
					for (int i = 0; i < volist.length; i++) {
					    voMsg.append(" " + volist[i]);
					    String voiLower = volist[i].toLowerCase();
					    if (voMixedCase.get(voiLower) == null)
						voMixedCase.put(voiLower, volist[i]);
					}
					addToMsg(methodName, new String(voMsg));
				}
			}
			fr.close(); 
			br.close();
			
			// -- get the User-VO map file name ---------
			if ( mapfile == null ) {
				debug("..getting mapfile");
				mapfile = getMapFile();
			} // end if
			checkConfigFile(mapfile, mapFileWatchdog);
			
			//---------------------------
			// Start processing the file
			//---------------------------
			addToMsg(methodName,"User-VO map table("+mapfile+")");
			fr = new FileReader( mapfile );
			br = new BufferedReader(fr);
			Vector ignoredAccounts = new Vector();
			while ((record = br.readLine()) != null) {
				debug("record: "+record);
				String[] fields = record.split(":");
				if (fields.length < 5 ) { // skip empty or incorrect lines
					if (record.length() > 0)
						logerr("Incorrect line in mapfile: " + record);
					continue;
				}
				//debug("processing: "+record);
				addToMsg(methodName,"processing: "+record);
				unix = fields[1];
				vo = fields[4];
				
				/*
				String voLower = vo.toLowerCase();
				if (voMixedCase.get(voLower) == null)
					voMixedCase.put(voLower, vo);
				*/
				
				String voLower = vo.toLowerCase();
				if (voMixedCase.get(voLower) == null) {
					logerr("VO " + vo + " not listed in the site info file. Account " +
							unix + " ignored.");
				} else {
					if ( voAccts.containsKey(unix) ) {
						//br.close();
						ignoredAccounts.add(unix);
						logerr("Multiple mappings for unix account ("+unix+") ... this account will be ignored");
						addToMsg(methodName,"Multiple mappings for unix account ("+unix+") ... this account will be ignored");
						//throw new Exception("Multiple mappings for unix account ("+unix+")");
					} //end if
					voAccts.put(unix,voLower);
				}
			}
			
			if(ignoredAccounts.size() > 0 ) {
				logerr("Ignored unix accounts: " + ignoredAccounts.toString());
				addToMsg(methodName,"Ignored unix accounts: " + ignoredAccounts.toString());
				for(int iai = 0; iai < ignoredAccounts.size(); iai++) {
					voAccts.remove(ignoredAccounts.elementAt(iai));
				}
			}
			
			br.close();
		} catch ( Exception e ) {
			throw new Exception("ERROR in mapping file: "+e.getMessage());
		}
	} // end method	

	
	protected void checkConfigFile(String filename, DateFileWatchdog watchdog)
		throws Exception {
		String methodName = "checkFileWatchdog";
		
		debug("checking existence of config file");
		//--verify the map file exists and is readable ----
		File probe = new File(filename);
		if (!probe.isFile()) {
			throw new Exception("Config file( " + filename + ") not found.");
		} // end if
		if ( !probe.canRead() ) {
			logerr("Config file (" + filename + ") is not readable.");
			throw new Exception("Config file (" + filename + ") is not readable.");
		} // end if			
		logit("Config file stats:\n (" + filename + ")\n  last modified " + 
				(new Date(probe.lastModified())).toString() + " - size(" + 
				probe.length() + " Bytes)");
		addToMsg(methodName, "Config file stats:\n (" + filename + ")\n  last modified "
				+ (new Date(probe.lastModified())).toString() + 
				" - size(" + probe.length() +" Bytes)");
		
		if(watchdog != null) { //just extra check ...
			if(!watchdog.getFile().equals(probe)) {//has changed or has been deleted ?!?
				watchdog.stopIt();
				watchdog = null;
			}
		} 
		
		if(watchdog == null) {
			watchdog = new DateFileWatchdog(probe, MAP_FILE_WATCHDOG_CHECK_RATE);
			watchdog.addObserver(this);
			logit("DateFileWatchdog for (" + watchdog.getFile() + ") has been started");
			addToMsg(methodName, "DateFileWatchdog for (" + watchdog.getFile() +
					") has been started");
			environmentSet = false;				
		}
	}
	

    public void update(Observable o, Object arg) {
        if(o != null && mapFileWatchdog != null && o.equals(mapFileWatchdog)) {
            logit("\n\n===> User vo Map has changed !! ... The env will be reloaded\n\n");
            environmentSet = false;
            return;
        }
        
        if(o != null && infoFileWatchdog != null && o.equals(infoFileWatchdog)) {
            logit("\n\n===> Site info file has changed !! ... The env will be reloaded\n\n");
            environmentSet = false;
        }
    }
}
