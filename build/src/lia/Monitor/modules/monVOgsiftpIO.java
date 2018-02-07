package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.Date;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;

public class monVOgsiftpIO extends monVoModules implements MonitoringModule {

    /**
       * 
       */
    private static final long serialVersionUID = -5052555702664328495L;

    //JGW  static protected String MyModuleName = "monVOgsiftpIOold";
    protected static String MyModuleName = "monVOgsiftpIO";
    protected static String[] MyResTypes = { "ftpInput", "ftpOutput" };

    protected static String emailNotifyProperty = "lia.Monitor.notifyVOFTP";

    protected String monalisaHome = null;
    protected String vdtLocation = null;
    protected String globusLocation = null;

    protected static final String GRIDFTP_LOG = "/var/gridftp.log";
    protected String ftplog = null; // GLOBUS_LOCATION + GRIDFTP_LOG;

    //==============================================================
    public monVOgsiftpIO() {
        super(MyModuleName, MyResTypes, emailNotifyProperty);
        canSuspend = false;
        String methodName = "constructor";
        addToMsg(methodName, "Constructor of " + ModuleName + " at " + currDate);
        addToMsg(methodName, "Info content: name " + info.name + " id " + info.id + " type " + info.type + " state "
                + info.state + " err " + info.error_count + ".");
        isRepetitive = true;
        info.ResTypes = ResTypes();
        logit(sb.toString());
        sb = new StringBuilder();
    } // end method 

    //==============================================================
    // configuration file entry: *Node{monVOgsiftpIOold, localhost, <arguments>}%30
    // <arguments> is a comme separated list (no quoted)
    // ftplog=/path-to-ftplog
    // globus_location=/globus-location
    @Override
    public MonModuleInfo init(MNode inNode, String args) {
        String methodName = "init";
        String[] argList = null;
        Node = inNode;
        clusterModuleName = Node.getClusterName() + "-" + ModuleName;

        addToMsg(methodName, "Instantiating instance for Cluster (node in cf) " + clusterModuleName + " at " + currDate);
        addToMsg(methodName, "arguments: " + ((args == null) ? "NO ARGUMENTS" : args));
        addToMsg(methodName, "Node Info: name " + (Node.name == null ? "null" : Node.name) + " short_name "
                + (Node.name_short == null ? "null" : Node.name_short) + " cl.name "
                + (Node.cluster == null ? "null" : Node.cluster.name) + " fa.name "
                + (Node.farm == null ? "null" : Node.farm.name) + ".");

        // -----------------------
        // Check the argument list
        // -----------------------
        if (args != null) {
            //check if file location or globus_location are passed
            argList = args.split(","); //requires java 1.4
            for (int j = 0; j < argList.length; j++) {
                argList[j] = argList[j].trim();
                logit("Argument " + j + ":" + argList[j] + ".");
                if (argList[j].startsWith("ftplog=")) {
                    ftplog = argList[j].substring("ftplog=".length()).trim();
                    addToMsg(methodName, "overrridden ftplog(" + ftplog + ")");
                    continue;
                }
                if (argList[j].startsWith("mapfile=")) {
                    mapfile = argList[j].substring("mapfile=".length()).trim();
                    addToMsg(methodName, "overrridden mapfile(" + mapfile + ")");
                    continue;
                }
                if (argList[j].startsWith("debug")) {
                    debugmode = true;
                    addToMsg(methodName, "overrridden debug(" + debugmode + ")");
                    continue;
                }

                if (argList[j].toLowerCase().indexOf("cansuspend") != -1) {
                    boolean cSusp = false;
                    try {
                        cSusp = Boolean.valueOf(argList[j].split("(\\s)*=(\\s)*")[1].trim()).booleanValue();
                    } catch (Throwable t) {
                        cSusp = false;
                    }
                    canSuspend = cSusp;
                    continue;
                }
            } // end for 
            /*
              if (argList[j].equals("par_alt")) {
                ResTypes = new String[] {"ftpInput22w","ftpOutput22w"};
                info.ResTypes = ResTypes;
              }
            */
        } // end if args

        info.ResTypes = ResTypes();
        return info;
    } // end method

    //=================================================
    protected void setEnvironment() throws Exception {
        String methodName = "setEnvironment";
        try {
            // -- Establish map table ---
            //What happen if the map table file is partialy good ?!?! Only a few accounts!

            //save latest known state
            tmpVoAccts.clear();
            tmpVoMixedCase.clear();
            tmpVoAccts.putAll(voAccts);
            tmpVoMixedCase.putAll(voMixedCase);

            cleanupEnv();
            try {
                initializeEnv();
            } catch (Exception e1) {
                throw e1;
            }

            computeVoAcctsDiff();

            // -- Determine the MonaALisa_HOME ---
            monalisaHome = getEnvValue("MonaLisa_HOME");
            if (monalisaHome == null) {
                throw new Exception("MonaLisa_HOME environmental variable not set.");
            } // end if monalisaHome
            addToMsg(methodName, "MonaLisa_HOME=" + monalisaHome);

            // -- Determine the VDT_LOCATION ---
            vdtLocation = getEnvValue("VDT_LOCATION");
            if (vdtLocation == null) {
                vdtLocation = monalisaHome + "/..";
            }
            addToMsg(methodName, "VDT_LOCATION=" + vdtLocation);

            // -- Determine the GLOBUS_LOCATION ---
            if (ftplog == null) {
                globusLocation = getEnvValue("GLOBUS_LOCATION");
                if (globusLocation == null) {
                    throw new Exception("GLOBUS_LOCATION environmental variable not set.");
                } // end if globusLocation
                addToMsg(methodName, "GLOBUS_LOCATION=" + globusLocation);
            } // end if null

            // -- set the ftplog values ---
            if (ftplog == null) {
                ftplog = globusLocation + GRIDFTP_LOG;
            }
            addToMsg(methodName, "ftplog...." + ftplog);

            // --- check to see if GRIDFTP_LOG exists and readable ---
            File probe = new File(ftplog);
            if (!probe.isFile()) {
                throw new Exception("Gridftp log (" + ftplog + ") not found.");
            } // end if
            if (!probe.canRead()) {
                throw new Exception("Gridftp log (" + ftplog + ") is not readable.");
            } // end if 
            addToMsg(methodName,
                    "Gridftp log (" + ftplog + ")\n  last modified " + (new Date(probe.lastModified())).toString()
                            + " - size(" + probe.length() + " Bytes)");

        } catch (Exception e) {
            throw new Exception("setEnvironment() - " + e.getMessage() + " " + e);
        } // end try/catch

        environmentSet = true;
    } // end method

    //============================================================
    @Override
    public Object doProcess() throws Exception {
        String methodName = "doProcess";
        Vector v = null;

        try {
            //-- set the status to good --
            statusGood = true;

            //-- set the environment (only once we hope) --
            if (!environmentSet) {
                setEnvironment();
            } // if environmentSet

            // -- record the start time --
            setStartTime();

            // -- Initialize the VO totals table ---
            initializeTotalsTable();

            // -- collect the ftp log data --
            //      BufferedReader br = procOutput ( "cat "+ftplog );
            //      if ( br  == null ) {
            //   throw new Exception ("Command line process failed unexpectedly: cat "+ftplog);
            //} // end br
            //parseFtpStat(br);

            // -- collect the ftp log data --
            File flog = new File(ftplog);
            if (!flog.exists()) {
                throw new Exception("FTPlog file [ " + ftplog + " ] does NOT exist!");
            }

            if (!flog.isFile()) {
                throw new Exception("FTPlog file [ " + ftplog + " ] is NOT a FILE!");
            }

            if (!flog.canRead()) {
                throw new Exception("FTPlog file [ " + ftplog + " ] has NO Read Access!");
            }

            BufferedReader br = new BufferedReader(new FileReader(flog));

            try {
                parseFtpStat(br);
            } catch (Throwable t) {
                if (br != null) {
                    br.close();
                }
                throw t;
            }
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Throwable t) {//this is somehow bad ...
            }

            // -- record the finish -----
            setFinishTime();

            // -- create the results for update to ML-----
            double factor = 1024.0; // results in KBytes
            v = createResults(factor);

            sendStatusEmail();

            if (getShouldNotifyConfig()) {
                logit(" [ monVOgsiftpIO ] - Notified Config changed");
                setShouldNotifyConfig(false);
            }

        } catch (Throwable e) {
            statusGood = false;
            sendExceptionEmail(methodName + " FATAL ERROR - " + e.getMessage());
            throw new Exception(e);
        } // end try/catch

        return v;
    } // end method

    //==== parseFtpStat =======================================
    protected void parseFtpStat(BufferedReader buff) throws Exception {
        String methodName = "parseFtpStat";
        String COMMENT_LINE_START = "#";
        String[] tz = new String[] {};
        String record = null;
        String user = new String();
        String value = new String();
        String type = new String();
        int linecnt = 0;
        int maxlinecnt = 7;

        try {
            while ((record = buff.readLine()) != null) {
                linecnt++;
                if (record.length() == 0) {
                    continue;
                } // empty line
                if (record.startsWith(COMMENT_LINE_START)) {
                    continue;
                } // comment

                //---- process the line -----
                tz = new String[] {};
                tz = record.split("\\s+");
                if (linecnt < 5) {
                    debug(record);
                    debug("Length: " + tz.length);
                } // end if
                if (tz.length == 16) {
                    user = tz[5].split("=")[1].trim();
                    value = tz[9].split("=")[1].trim();
                    type = tz[14].split("=")[1].trim();

                    if (linecnt < 10) {
                        debug("User: " + user + "  Value: " + value + "  type: " + type);
                    } //end if

                    if (type.equals("STOR")) {
                        updateTotals(user, "ftpInput", value);
                    } // end if STOR

                    if (type.equals("RETR")) {
                        updateTotals(user, "ftpOutput", value);
                    } // end if RETR

                } // end if ni
            } // end while loop
            debug(linecnt + " lines read from gridftp.log file");
        } catch (Exception e) {
            throw new Exception(methodName + "() Exception: " + e);
        } // end try/catch

        /*
          int vid = monVODescriptor.getVOD().getVOid(tok[0].trim());
          if (vid>=0)
            if (res[vid][0]>0) {
              logit("Irreguler ftp stat file, twice the same VO, skipping line.");
            } else {
               user  = tok[0].trim();
               value = tok[1].trim();
               res[vid][0] = Double.valueOf(tok[1]).doubleValue();
               res[vid][1] = Double.valueOf(tok[2]).doubleValue();
        */

    } // end method

    //============================================================
    /*
    // --------------------------------------------------
    public class BasicLogging {
      public static void main(String[] args) {
          // Get a logger; the logger is automatically created if
          // it doesn't already exist
          Logger logger = Logger.getLogger("com.mycompany.BasicLogging");
        
          // Log a few message at different severity levels
          logger.severe("my severe message");
          logger.warning("my warning message");
          logger.info("my info message");
          logger.config("my config message");
          logger.fine("my fine message");
          logger.finer("my finer message");
          logger.finest("my finest message");
       }
    } 
    */

    //============================================================

    // --------------------------------------------------
    static public void main(String[] args) {
        String host = args[0];
        monVOgsiftpIO aa = null;
        String ad = null;

        try {
            System.out.println("...instantiating VOgsiftpIO");
            aa = new monVOgsiftpIO();
        } catch (Exception e) {
            System.out.println(" Cannot instantiate VOgsiftpIO:" + e);
            System.exit(-1);
        } // end try/catch

        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Cannot get ip for node " + e);
            System.exit(-1);
        } // end try/catch

        System.out.println("...running init method ");
        String mapFile = "./testdata/grid3-user-vo-map.txt";
        String arg = "test,mapfile=" + mapFile + ",ftplog=./testdata/gridftp.log";
        MonModuleInfo info = aa.init(new MNode(args[0], ad, null, null), arg);
        int sec = 2; // number of seconds to sleep before processing again
        for (int i = 0; i < 8; i++) {
            try {
                System.out.println("...sleeping " + sec + " seconds");
                Thread.sleep(sec * 1000);
                System.out.println("...running doProcess method ");
                Object bb = aa.doProcess();
                // -- after the 5th time, touch the map file and sleep --
                // -- to test the re-reading of the map file         --
                if (i == 4) {
                    System.out.println("...touching map file: " + mapFile);
                    Runtime.getRuntime().exec("touch " + mapFile);
                    System.out.println("...sleeping " + sec + " seconds");
                    Thread.sleep(25000); // 25 secs
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e);
            } // end try/catch

            System.out.println("VOgsiftpIO Testing Complete");
        } // end for
        System.exit(0);
    }// end main
}// end class

