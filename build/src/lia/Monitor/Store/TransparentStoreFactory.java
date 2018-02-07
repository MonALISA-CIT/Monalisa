package lia.Monitor.Store;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.EPGUtils.EmbeddedPGUtils;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.monPredicate;
import lia.util.MLProcess;
import lia.util.ntp.NTPDate;

/**
 * @author
 */
public class TransparentStoreFactory {

    private static TransparentStoreInt _transparentStore = null;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TransparentStoreFactory.class.getName());

    private static String MonaLisa_home = null;

    private static String FARM_home = null;

    private static boolean bMemoryStore = false;

    private static String storeType = "external";

    private static final long dbStartTimeout;

    static {
        long sTimeout;
        try {
            sTimeout = AppConfig.getl("lia.Monitor.Store.TransparentStoreFactory.dbStartTimeout", 20 * 60 * 1000);
        } catch (Throwable t) {
            sTimeout = 20 * 60 * 1000;
        }
        dbStartTimeout = sTimeout;
    }

    /**
     * Get the memory-only flag
     * 
     * @return true if data should stay only in memory, without any database support
     */
    public static boolean isMemoryStoreOnly() {
        return bMemoryStore;
    }

    /**
     * Set the memory-only flag
     * 
     * @param b
     */
    static void setMemoryStoreOnly(final boolean b) {
        bMemoryStore = b;
    }

    /**
     * UnJar and starts MySQL.
     * 
     * @return true - if MySql started successfully false - if not
     */
    private static synchronized boolean veryUglyHack() {

        MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME", MonaLisa_home);
        FARM_home = AppConfig.getProperty("lia.Monitor.Farm.HOME", FARM_home);

        if (MonaLisa_home == null || FARM_home == null) {
            return false;
        }

        try {

            JarFile jf = new JarFile(MonaLisa_home + "/Service/lib/emysqldb.jar");
            Enumeration<JarEntry> e = jf.entries();
            byte[] buff = new byte[1024];
            String cmdToInstall = null;

            Vector<String> fileNames = new Vector<String>();

            while (e.hasMoreElements()) {

                JarEntry je = e.nextElement();
                String s = je.toString();

                if (s.endsWith(".tgz") || s.endsWith(".sh")) {

                    if (s.endsWith(".sh")) {
                        cmdToInstall = FARM_home + "/" + s;
                    }
                    BufferedInputStream bis = null;
                    FileOutputStream fos = null;

                    try {
                        fos = new FileOutputStream(FARM_home + "/" + s);
                        bis = new BufferedInputStream(jf.getInputStream(je));

                        fileNames.add(FARM_home + "/" + s);

                        for (;;) {
                            int bNO = bis.read(buff);
                            if (bNO == -1) {
                                break;
                            }
                            fos.write(buff, 0, bNO);
                        }

                        fos.flush();
                    } catch (IOException ioe) {
                        continue;
                    } finally {
                        if (bis != null) {
                            try {
                                bis.close();
                            } catch (Exception e2) {
                                // ignore
                            }
                        }

                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (Exception e2) {
                                // ignore
                            }
                        }
                    }
                }
            }// while ( e.hasMoreElements() )

            if (cmdToInstall == null) {
                return false;
            }

            Process pro = MLProcess.exec(new String[] {
                    "/bin/sh", cmdToInstall
            }, 10 * 60 * 1000);// 10 mins
            try {
                pro.waitFor();
            } catch (Throwable t) {
                // ignore
            }

            for (int i = 0; i < fileNames.size(); i++) {
                String fn = fileNames.elementAt(i);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Deleting file..." + fn);
                }
                (new File(fn)).delete();
            }

            // setting params for emysqldb
            driverString = "com.mysql.jdbc.Driver";
            System.setProperty("lia.Monitor.jdbcDriverString", driverString);
            System.setProperty("lia.Monitor.ServerName", "127.0.0.1");
            System.setProperty("lia.Monitor.DatabaseName", "mon_data");
            System.setProperty("lia.Monitor.UserName", "mon_user");
            System.setProperty("lia.Monitor.Pass", "mon_pass");

            BufferedReader br = null;
            String portS = null;
            try {
                br = new BufferedReader(new FileReader(FARM_home + "/emysqldb/mysql.port"));
                portS = br.readLine();
            } catch (IOException ioe) {
                // ignore
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e2) {
                        // ignore
                    }
                }
            }

            if (portS == null) {
                return false;
            }

            System.setProperty("lia.Monitor.DatabasePort", portS.trim());

            DB db = new DB();
            
            db.setReadOnly(true);

            for (int i = 0; i < 20; i++) {
                if (db.query("SELECT 1015 AS omiecinshpe;", true) && db.moveNext() && db.geti("omiecinshpe") == 1015) {
                    storeType = "emysqldb";
                    return true;
                }
                try {
                    Thread.sleep(3000);
                } catch (Exception e2) {
                    // ignore
                }
            }

            logger.log(Level.WARNING, " Embedded mysql did not respond to test queries ... keeping only in-memory history");
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Cannot instantiate Embedded mysql", t);
        }

        return false;
    }

    /**
     * UnJar and starts PostgreSQL.
     * 
     * @return true - if PostgreSQL was started successfully false - if not
     */
	private static synchronized boolean startEmbeddedPG() {

        MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME", MonaLisa_home);
        FARM_home = AppConfig.getProperty("lia.Monitor.Farm.HOME", FARM_home);

        String sPG_PATH = AppConfig.getGlobalEnvProperty("PGSQL_PATH", null);

        // relative to this path we get the jar file containing the database
        if (MonaLisa_home == null) {
            return false;
        }

        // if this variable is not set fallback to the FARM_home folder, if possible
        if (sPG_PATH == null || sPG_PATH.length() == 0) {
            if (FARM_home == null || FARM_home.length() == 0) {
                return false;
            }

            sPG_PATH = FARM_home;
        }

        logger.log(Level.FINE, "Database location will be : '" + sPG_PATH + "' because '" + AppConfig.getGlobalEnvProperty("PGSQL_PATH", null) + "' and '" + FARM_home + "'");

        try {
            String sJarFile = "epgsqldb.jar";

            try {

                final String osName = System.getProperty("os.name");
                final String osArch = System.getProperty("os.arch");

                if (osName.equals("SunOS") && osArch.equals("sparc")) {
                    sJarFile = "epgsqldb-solaris_sparc32.jar";
                } else if (osName.equals("Linux") && osArch.equals("amd64")) {
                    sJarFile = "epgsqldb-linux_amd64.jar";
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Exception trying to determine the underlying OS", e);
            }

            JarFile jf = new JarFile(MonaLisa_home + "/Service/lib/" + sJarFile);
            Enumeration<JarEntry> e = jf.entries();
            byte[] buff = new byte[1024];
            String cmdToInstall = null;

            Vector<String> fileNames = new Vector<String>();

            while (e.hasMoreElements()) {

                JarEntry je = e.nextElement();
                String s = je.toString();

                if (s.endsWith(".tgz") || s.endsWith(".tar.gz") || s.endsWith(".sh")) {
                    if (s.endsWith(".sh")) {
                        cmdToInstall = sPG_PATH + "/" + s;
                    }

                    BufferedInputStream bis = null;
                    FileOutputStream fos = null;

                    try {
                        bis = new BufferedInputStream(jf.getInputStream(je));
                        fos = new FileOutputStream(sPG_PATH + "/" + s);
                        fileNames.add(sPG_PATH + "/" + s);

                        for (;;) {
                            int bNO = bis.read(buff);
                            if (bNO == -1) {
                                break;
                            }
                            fos.write(buff, 0, bNO);
                        }

                        fos.flush();
                    } catch (IOException ioe) {
                        continue;
                    } finally {
                        if (bis != null) {
                            try {
                                bis.close();
                            } catch (IOException ioe2) {
                                // ignore
                            }
                        }

                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (IOException ioe2) {
                                // ignore
                            }
                        }
                    }
                }
            }// while ( e.hasMoreElements() )

            long sTime = System.currentTimeMillis();
            if (cmdToInstall == null) {
                return false;
            }
            logger.log(Level.INFO, " [ TransparentStoreFactory ] Trying to start embedded PG ... Please wait." + "\nIt can take a while (e.g. if import from older DB is needed)" + "\nThe Timeout to start the DB is " + (dbStartTimeout / (1000 * 60)) + " min(s).");
            Process pro = MLProcess.exec(new String[] {
                    "/bin/sh", cmdToInstall
            }, dbStartTimeout);// 10 mins
            try {
                pro.waitFor();
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Got exc waiting for proc to finish", t);
                }
            }

            for (int i = 0; i < fileNames.size(); i++) {
                String fn = fileNames.elementAt(i);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Deleting file..." + fn);
                }
                (new File(fn)).delete();
            }

            // setting params for epgsqldb
            driverString = "org.postgresql.Driver";
            System.setProperty("lia.Monitor.jdbcDriverString", driverString);
            System.setProperty("lia.Monitor.ServerName", "127.0.0.1");
            System.setProperty("lia.Monitor.DatabaseName", "mon_data");
            System.setProperty("lia.Monitor.UserName", "mon_user");
            System.setProperty("lia.Monitor.Pass", "mon_pass");

            BufferedReader br = null;
            String portS = null;

            try {
                br = new BufferedReader(new FileReader(sPG_PATH + "/pgsql/pgsql.port"));
                portS = br.readLine();
            } catch (IOException ioe) {
                // ignore
            } finally {
                if (br != null) {
                    try {
                        br.close();
                        br = null;
                    } catch (IOException ioe2) {
                        // ignore
                    }
                }
            }

            if (portS == null) {
                return false;
            }

            System.setProperty("lia.Monitor.DatabasePort", portS.trim());

            DB db = new DB();

            long sleepTime = dbStartTimeout - (System.currentTimeMillis() - sTime);
            if (sleepTime <= 0) {
                sleepTime = 5 * 1000;
            }

            db.setReadOnly(true);
            
            if (db.query("SELECT 1015 AS omiecinshpe;", false) && db.moveNext() && db.geti("omiecinshpe") == 1015) {
                storeType = "epgsqldb";
                logger.log(Level.INFO, " [ TransparentStoreFactory ] Emebdded PG Started successfully ... ");
                return true;
            }

            logger.log(Level.WARNING, " [ TransparentStoreFactory ] Embedded pgsql did not respond to test queries ... keeping only in-memory history");

        } catch (Throwable t) {
            logger.log(Level.WARNING, " Cannot instantiate Embedded pgsql", t);
        }
        return false;
    }

    private static String driverString = AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver").trim();

    /**
     * Get the database driver string
     * 
     * @return driver string
     */
    public static synchronized String getDriverString() {
        return driverString;
    }

    /**
     * Textual short description of the store type
     * 
     * @return text version of the store type
     */
    public static synchronized String getStoreType() {
        return storeType;
    }

    private static final void startMemoryStore() {
        System.setProperty("lia.Monitor.Store.TransparentStoreFast.web_writes", "0");
        logger.log(Level.INFO, "Starting with memory-only store");
        bMemoryStore = true;
        storeType = "memory-only";
        _transparentStore = new TransparentStoreFast();
    }

    /**
     * Get the store instance
     * 
     * @return an instance of TransparentStoreInt
     */
    public static synchronized TransparentStoreInt getStore() {
        if (_transparentStore == null) {
            // logger.log(Level.INFO, "Store is null");

            if (AppConfig.getb("lia.Monitor.memory_store_only", false) || AppConfig.geti("lia.Monitor.Store.TransparentStoreFast.web_writes", -1) == 0) {
                startMemoryStore();
            }

            if (_transparentStore == null) {
                if (Boolean.valueOf(AppConfig.getProperty("lia.Monitor.use_epgsqldb", "false")).booleanValue()) {
                    // if (startEmbeddedPG()) {
                    if (EmbeddedPGUtils.upgradeAndStartEmbeddedPG()) {
                        storeType = "epgsqldb";
                        _transparentStore = new TransparentStoreFast();
                    } else {
                        startMemoryStore();
                    }
                } else if (Boolean.valueOf(AppConfig.getProperty("lia.Monitor.use_emysqldb", "false")).booleanValue()) {

                    if (veryUglyHack()) {

                        String msg = "\n\nemysqldb will no longer be supported in future versions of MonALISA\nbecause of license issues. You can use epgsqldb instead.";
                        msg += "\nTo enable epgsqldb please set lia.Monitor.use_epgsqldb = true in your ml.properties file.";
                        msg += "\nThank you for your understanding.\n\n";
                        logger.log(Level.INFO, msg);

                        _transparentStore = new TransparentStoreFast();

                    } else {
                        startMemoryStore();
                    }
                }
            }

            if (_transparentStore == null) {
                _transparentStore = new TransparentStoreFast();
                storeType = "external";
            }

            logger.log(Level.FINEST, "Store created : " + _transparentStore);
            // logger.log(Level.INFO, "Loader : "+_transparentStore.getClass().getClassLoader());
        }

        return _transparentStore;
    }

    /**
     * Normalizez the times in a predicate, making tmin and tmax &lt;0 for general processing. This function <b>returns
     * a <u>copy</u></b> of the original predicate.
     * 
     * @param p
     *            the predicate to normalize
     * @return a copy of the original predicate with tmin and tmax <0
     */
    public static final monPredicate normalizePredicate(final monPredicate p) {
        final monPredicate pt = new monPredicate(p.Farm, p.Cluster, p.Node, p.tmin, p.tmax, p.parameters, p.constraints);

        pt.bLastVals = p.bLastVals;

        final long now = DataSplitter.bFreeze ? DataSplitter.lFreezeTime : NTPDate.currentTimeMillis();

        if (pt.tmax >= now) {
            pt.tmax = now - 1;
        }

        if (pt.tmin >= now) {
            pt.tmin = now - 1;
        }

        if (pt.tmax > 0) {
            pt.tmax = pt.tmax - now;
        } else if (pt.tmax == 0) {
            pt.tmax = -1;
        }

        if (pt.tmin > 0) {
            pt.tmin = pt.tmin - now;
        } else if (pt.tmin == 0) {
            pt.tmin = -1;
        }

        if (pt.tmax < pt.tmin) {
            long t = pt.tmax;
            pt.tmax = pt.tmin;
            pt.tmin = t;
        }

        return pt;
    }
}
