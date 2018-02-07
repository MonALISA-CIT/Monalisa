package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JComponent;
import javax.swing.JProgressBar;

import lia.Monitor.JiniClient.CommonGUI.BackgroundWorker;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Texture;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/*
 * Created on Mar 13, 2005 11:49:53 PM
 * Filename: ExtraClassLoader.java
 */

/**
 * @author Luc
 *         NetResourceClassLoader<br>
 *         - loads a resource if in java.class.path or sun.boot.class.path, searched by application (system) classloader
 *         (that
 *         loads all application classes) and by bootstrap classloader (main native classloader that loads all java core
 *         classes). <br>
 *         - searches in all downloaded jars for the resource<br>
 *         - if still not found, it starts downloading the neccessary jar, if it exists.<br>
 *         DONE: use System.getProperty("<Separator>") instead of "/" <br>
 *         Should provide system property "extra_images_location" for path to downloadable jars<br>
 *         and system property "DownloadRate" for rate of download: bytes per second for jars, defaults to 1024<br>
 *         also, for development client, the property "user.home" shoul be provided to specify path to cached jars
 */
public class NetResourceClassLoader/* extends ClassLoader */{

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(NetResourceClassLoader.class.getName());

    public static Level logLevel = Level.FINEST;// for debug user Level.INFO

    private String sCacheDir;

    private static final String pathSeparator = System.getProperty("file.separator");

    private String[] sExtraImagesLocations = new String[0];// =
                                                           // AppConfig.getProperty("jogl.netresourceclassloader.extra_images_location","http://").split(",");
                                                           // //web location of downloadable jars with images

    /**
     * array with valid repository lists, that means that each list has at least
     * one element, one path, one map<br>
     */
    private RepositoryList[] repLists = new RepositoryList[0];

    private JProgressBar jpbDownloadBar = null; // progress bar to show statu of download

    private JComponent jpDownloadPanel = null; // panel that includes progress bar and label

    private boolean bIsCacheDir = false;// if cache dir is available or not
    // private String sJarsLMTimeFile = "jars_last_modification.txt";//name of file that contains last modification
    // times for jars

    private final String sMapsListFile = "list_jars_content.txt";// name of file that lists on server the available maps

    public boolean bDownloadNewFiles = true;// if this is false, then no new files will be downloaded, and no old files
                                            // will be deleted

    /**
     * stores last access time for cached files, in the form of local jar name - milis when it was accessed (smaller
     * value means older file)
     */
    // private Hashtable hMapsLastAccessTime = null;

    private final boolean bStoreInJar = (AppConfig.getProperty("jogl.netresourceclassloader.store_in_jar", "true")
            .equals("false") ? false : true);

    private static boolean bDebug = AppConfig.getb("lia.Monitor.debug", false);

    private boolean bProgressOccupied = false;

    private final Object objSyncProgressAccess = new Object();

    /** if a rep list is available, the download list will be used instead */
    private Vector[] dwlLists = new Vector[0];

    private final Object dwlListsSyncObj = new Object();

    /**
     * sets debug level for this class
     * 
     * @param bIsDebug
     *            set debug mode if true, else do not show debug messages
     */
    public static void setDebugLevel(boolean bIsDebug) {
        bDebug = bIsDebug;
        if (bDebug) {
            logLevel = Level.INFO;
        } else {
            logLevel = Level.FINEST;
        }
    }

    /**
     * parses a string and gives the long value as interpreted in bytes from k, m, g...
     * 
     * @param sSize
     * @return
     */
    public static int getSize(String sSize) {
        int lSize = 0;
        char car;
        int i = 0;
        if ((sSize != null) && (sSize.length() > 0)) {
            do {
                car = sSize.charAt(i);
                if ((car >= '0') && (car <= '9')) {
                    lSize = ((lSize * 10) + car) - '0';
                } else {
                    break;
                }
                i++;
            } while ((car >= '0') && (car <= '9') && (i < sSize.length()));
            if (i < sSize.length()) {
                // caracter read that is not digit, so is measure unit
                switch (car) {
                case 'g':
                case 'G':
                    lSize *= 1024;
                case 'm':
                case 'M':
                    lSize *= 1024;
                case 'k':
                case 'K':
                    lSize *= 1024;
                }
            }
            ;
        }
        ;
        return lSize;
    }

    public static long MAX_JAR_SIZE = 50 * 1024 * 1024;// maximal downloadable jar size is 5 megi
    // get maximal size from properties
    static {
        String sMaxJarSize = AppConfig.getProperty("jogl.netresourceclassloader.MaxJarSize", "5m");
        MAX_JAR_SIZE = getSize(sMaxJarSize);
        if (MAX_JAR_SIZE <= (300 * 1024)) {
            MAX_JAR_SIZE = 300 * 1024;
        }
    }

    /*
     * private static int nTopRate=0;//top download rate
     * //parse top download rate
     * static {
     * String sDownloadRate=AppConfig.getProperty("jogl.netresourceclassloader.DownloadRate","10k");
     * nTopRate = getSize( sDownloadRate);
     * if ( nTopRate<=1024 )
     * nTopRate = 1024;
     * }
     */
    private static int nBufferSize = 0;// buffer size
    static {
        String sBufferSize = AppConfig.getProperty("jogl.netresourceclassloader.BufferSize", "32k");
        nBufferSize = getSize(sBufferSize);
        if (nBufferSize <= 1024) {
            nBufferSize = 1024;
        }
    }

    private static int nStartCheckThread = 5 * 60 * 1000;// at 5 minutes after the client is started

    private static int nRepeatCheckThread = 60 * 60 * 1000;// at each hour check and update jars
    static {
        String sTime = AppConfig.getProperty("jogl.netresourceclassloader.StartCheckMilis", "300000");
        try {
            nStartCheckThread = Integer.parseInt(sTime);
        } catch (NumberFormatException nfex) {
            nStartCheckThread = 5 * 60 * 1000;
        }
        if (nStartCheckThread <= 0) {
            nStartCheckThread = 5 * 60 * 1000;
        }
        sTime = AppConfig.getProperty("jogl.netresourceclassloader.RepeatCheckMilis", "3600000");
        try {
            nRepeatCheckThread = Integer.parseInt(sTime);
        } catch (NumberFormatException nfex) {
            nRepeatCheckThread = 60 * 60 * 1000;
        }
        if (nRepeatCheckThread <= 0) {
            nRepeatCheckThread = 60 * 60 * 1000;
        }
    }

    /**
     * initalizes a new class loader<br>
     * verifies the download path and checks if cache directory is accessible.<br>
     */
    public NetResourceClassLoader(Class mainClientClass) {
        // call parent classloader initializer
        // super(NetResourceClassLoader.class.getClassLoader());
        if (mainClientClass != null) {
            Preferences prefs = Preferences.userNodeForPackage(mainClientClass);
            setCacheDir(prefs.get("Texture.CacheDirectory",
                    AppConfig.getProperty(/* "user.home" */"java.io.tmpdir", ".")));
            String cur_ver = prefs.get("Texture.WebPathsVersion", "0");
            String next_ver = AppConfig.getProperty("jogl.maps_webpaths_version", "0");
            int nc, nn;
            try {
                nc = Integer.parseInt(cur_ver);
                nn = Integer.parseInt(next_ver);
            } catch (Exception ex) {
                nc = 0;
                nn = 0;
            }
            if (nc < nn) {
                prefs.put("Texture.WebPaths",
                        AppConfig.getProperty("jogl.netresourceclassloader.extra_images_location", null));
                prefs.put("Texture.WebPathsVersion", next_ver);
            }
            setExtraImagesLocations(prefs.get("Texture.WebPaths",
                    AppConfig.getProperty("jogl.netresourceclassloader.extra_images_location", null)));
        } else {
            setCacheDir(AppConfig.getProperty(/* "user.home" */"java.io.tmpdir", "."));
            setExtraImagesLocations(AppConfig.getProperty("jogl.netresourceclassloader.extra_images_location", null));
        }
        // create repositories array
        // if ( sExtraImagesLocations.length > 0 ) {
        // repLists = new RepositoryList[sExtraImagesLocations.length];
        // for ( int i=0; i<repLists.length; i++ )
        // repLists[i]=null;
        // }
        // set the timer to check for new jars and to delete old cached jars
        // based on access time
        // set to start 5 minutes after the GUI interface is started
        // and it will run once at each hour
        BackgroundWorker.schedule(new TimerTask() {

            @Override
            public void run() {
                Thread.currentThread().setName(
                        "JoGL - NetResourceClassLoader - check new jars and delete old cached ones");
                checkNewJarsAndClearCache();
            }
        }, nStartCheckThread, nRepeatCheckThread);
    }

    /**
     * the first param is an identifier of the location where the file is taken
     * from and also where it will be saved
     * so that there could be several instances of sFile file on disk, from
     * different locations
     * 
     * @param sWebPath
     * @param sFile
     * @return bufferedreader for file located in cache, a copy of the one on the web
     */
    public BufferedReader readCachedFile(String sWebPath, String sFileName) {
        BufferedReader br = null;
        if (!sWebPath.endsWith("/")) {
            sWebPath += "/";
        }
        // site directory name from where from jar is downloaded
        String sSiteName = getSiteName(sWebPath);
        try {
            // System.out.println("try to create file "+sCacheDir+sSiteName+pathSeparator+sFileName);
            File fLMTime = new File(sCacheDir + sSiteName + pathSeparator + sFileName);
            // set the smallest date, that is 00:00:00 GMT, January 1, 1970 plus one milisecond
            long localLastModified = fLMTime.lastModified();
            URL urlDown;
            InputStream isDown;
            // get jars list file
            urlDown = new URL(sWebPath + sFileName);
            URLConnection connection = urlDown.openConnection();
            // compare web file's lm time with local file
            // Obs: if local file does not yet exists, it's lm time will be 0,
            // as returned by File.lastModified()
            long webLastModified = connection.getLastModified();
            if (bDebug) {
                System.out.println("file last modification times: local is " + localLastModified + " web: "
                        + webLastModified);
            }
            if (localLastModified < webLastModified) {
                // update list only if web list is newer than local one
                // set web jar's last modification time to know when to update it
                fLMTime.setLastModified(webLastModified);
                // write from web to local file
                FileOutputStream fOS = new FileOutputStream(fLMTime);
                isDown = connection.getInputStream();
                byte buff[] = new byte[nBufferSize];
                int nRead = -1;
                do {
                    nRead = isDown.read(buff);
                    if (nRead > 0) {
                        fOS.write(buff, 0, nRead);
                    }
                } while (nRead > 0);
                fOS.flush();
                fOS.close();
                isDown.close();
            }
            ;
            // open read buffer to file
            br = new BufferedReader(new InputStreamReader(new FileInputStream(fLMTime)));
        } catch (Exception ex) {
            // don't report?
            if (bDebug) {
                logger.log(logLevel, "Could not access " + sWebPath + sFileName);
            }
        }
        return br;
    }

    private class MapTreeNode extends ArrayList {

        private static final long serialVersionUID = 1L;

        /**
         * node value: directory, if intermediary node,
         * or map file, if terminal node
         */
        private final String sValue;

        public static final int S_NONE = 0;

        public static final int S_AVAILABLE = 1;

        public static final int S_ISDOWNLOADING = 2;

        private int state;

        /** state of map file: is downloading or none */
        public MapTreeNode(String val) {
            super();
            sValue = val;
            state = S_NONE;
        }

        @Override
        public String toString() {
            return sValue;
        }

        /**
         * the path does not start with "/"<br>
         * each "/" is interpreted as a new path, so a new TreeNode is created<br>
         * if there are no more "/" then, this is a terminal node<br>
         * first directory in path is compared with the child nodes, not with
         * this node's value<br>
         * NOTE: for add, the path separator is '/', but for get is the os
         * separator
         * 
         * @param sPath
         */
        public void addMap(String sPath) {
            if (sPath == null) {
                return;
            }
            String sDir;
            String sRest = "";
            int index = sPath.indexOf('/');
            if (index != -1) {
                // the first directory becomes the value of this node
                sDir = sPath.substring(0, index);
                sRest = sPath.substring(index + 1);
            } else {
                sDir = sPath;
            }
            ;
            MapTreeNode child = null;
            // and for rest of path, we find or create a new node
            // add call addMap for the child
            for (int i = 0; i < size(); i++) {
                child = ((MapTreeNode) get(i));
                if (child.getValue().equals(sDir)) {
                    break;
                }
            }
            if (child == null) {
                child = new MapTreeNode(sDir);
                add(child);
            }
            if (sRest.length() > 0) {
                child.addMap(sRest);
            } else {
                // terminal node, so set available for download flag
                child.setState(S_AVAILABLE);
            }
        }

        /**
         * searches a specified map with it's relative path in this repository<br>
         * should return the same object if or if not there is an '/' at the end
         * 
         * @param sPath
         *            relative path to map file
         * @return the tree node object if is found, null otherwise
         */
        public MapTreeNode getMap(String sPath) {
            /*
             * a integer value if the map is available or not:<br>
             * 0 - means map is not available<br>
             * 1 - means map is available<br>
             * 2 - means map is available, and is at maximum level.
             */
            String sDir;
            String sRest = "";
            int index = sPath.indexOf(Texture.pathSeparator);
            if (index != -1) {
                // the first directory becomes the value of this node
                sDir = sPath.substring(0, index);
                sRest = sPath.substring(index + 1);
            } else {
                sDir = sPath;
            }
            ;
            MapTreeNode child = null;
            // and for rest of path, we find or create a new node
            // add call addMap for the child
            for (int i = 0; i < size(); i++) {
                child = ((MapTreeNode) get(i));
                if (child.getValue().equals(sDir)) {
                    break;
                }
            }
            if (child == null) {
                return null;
            }
            if (sRest.length() == 0) {
                return child;
            }
            return child.getMap(sRest);
        }

        public String getValue() {
            return sValue;
        }

        public int getState() {
            return state;
        }

        public boolean checkState(int stateParam) {
            if ((stateParam == 0) && (state == 0)) {
                return true;
            }
            return (stateParam & state) > 0;
        }

        public void setState(int stateParam) {
            state |= stateParam;
        }

        public void clearState(int stateParam) {
            if ((state & stateParam) > 0) {
                state ^= stateParam;
            }
        }
    }

    /**
     * constructs a tree with available maps at the specified location<br>
     * if tree is empty, fall back to traditional loading, meaning try
     * and download a map.<br>
     * The mechanism of specifying the state of `downloading` for a map
     * will still be available.
     * 
     * @author mluc
     */
    private class RepositoryList {

        private String sLocation;

        public RepositoryList(String sLocation) {
            this.sLocation = sLocation;
            if (!this.sLocation.endsWith("/")) {
                this.sLocation = this.sLocation + '/';
            }
        }

        @Override
        public String toString() {
            return sLocation;
        }

        /**
         * checks and then sets downloading state for all first level children
         * of this root path<br>
         * This does not have to be sync with getAvailableList because that
         * function is run at construction time, and probably only then.<br>
         * if there is no parent that means that the parent file is not
         * in available file list or the tree is corrupt, because for any
         * child node, a parent node is created
         * this means that the path is not compatible with the available path
         * list so it should not start downloading it
         * because it will not find it.
         * 
         * @param rootPath
         *            path to the element who's first level children are going
         *            to be downloaded
         * @return 1 if the download cand be started or 0 if download
         *         is already in progress for one of the children, so it should wait for
         *         a while, or -1 if parent is not available so it should give up, stop
         *         the download and never try to download it
         */
        public synchronized int startDownload(String rootPath) {
            MapTreeNode localRoot = root.getMap(rootPath);
            if (localRoot == null) {
                return -1;
            }
            for (int i = 0; i < localRoot.size(); i++) {
                if (((MapTreeNode) localRoot.get(i)).checkState(MapTreeNode.S_ISDOWNLOADING)) {
                    return 0;
                }
            }
            // everything ok, none of the files are to be downloaded, so set the
            // is_downloading flag for them
            for (int i = 0; i < localRoot.size(); i++) {
                ((MapTreeNode) localRoot.get(i)).setState(MapTreeNode.S_ISDOWNLOADING);
            }
            return 1;
        }

        /**
         * unsets is_downloading state from all child nodes of this parent node
         * 
         * @param rootPath
         *            parent node who's children are to be unset
         */
        public synchronized void endDownload(String rootPath) {
            MapTreeNode localRoot = root.getMap(rootPath);
            // nothing to be unset
            if (localRoot == null) {
                return;
            }
            for (int i = 0; i < localRoot.size(); i++) {
                ((MapTreeNode) localRoot.get(i)).clearState(MapTreeNode.S_ISDOWNLOADING);
            }
        }

        /**
         * checks in a sync mode with the others that a node from list is
         * downloading or not
         * 
         * @param node
         * @return
         */
        public synchronized boolean checkIsDonwloading(MapTreeNode node) {
            return node.checkState(MapTreeNode.S_ISDOWNLOADING);
        }

        /**
         * root of tree of available maps, organized as a directory tree, with
         * files as leaves
         */
        private final MapTreeNode root = new MapTreeNode("");

        /**
         * checks if map is available at location and if it is at maximum level
         * 
         * @param sPath
         * @return node that corresponds to path
         */
        public MapTreeNode getMap(String sPath) {
            return root.getMap(sPath);
        }

        /**
         * connects to the specified location and downloads the file named<br>
         * <i>list_jars_content.txt</i><br>
         * and then constructs a tree of available maps, not jars.<br>
         * trims extension ".oct"
         * 
         * @return true or false if succeded in constructing the tree
         */
        private boolean getAvailableList() {
            try {
                if (bDebug) {
                    System.out.println("check list of maps for location " + sLocation);
                }
                // read the cached file
                BufferedReader br = null;
                br = readCachedFile(sLocation, sMapsListFile);
                if (br != null) {
                    String sLine;
                    while ((sLine = br.readLine()) != null) {
                        sLine = sLine.trim();
                        if (sLine.endsWith(Texture.sMapExt)) {
                            sLine = sLine.substring(0, sLine.length() - Texture.sMapExt.length());
                        }
                        if (sLine.length() > 0) {
                            root.addMap(sLine);
                        }
                    }
                    br.close();
                    if (root.size() > 0) {
                        return true;
                    }
                }
            } catch (Exception ex) {
                // some exception occured
                logger.log(Level.WARNING, "some exception while updating list of available jars");
                ex.printStackTrace();
            }
            return false;
        }
    }

    /**
     * removes from file path the file name and preceding /<br>
     * and adds ".jar" to remaining path<br>
     * works for levels 1 or greater<br>
     * with a little trick works also for 0 level, that will contain 8x4 textures
     * instead of only 2x2<br>
     * update on 25 Aug 2005<br>
     * Because of identification of resources change, the web path and local path are now
     * different in the path separator, so it returns an array of two strings: the relative local
     * path and the relative web path to jar
     * 
     * @param file_name
     * @return jar name in the form of 2 dimension array with local path and web path
     *         or null if wrong file path format
     */
    private static String[] getJarFromFileName(String file_name) {
        // this is done by removing file name and adding jar extension to "name"
        int nAfterPos;// , nBeforePos;
        nAfterPos = file_name.lastIndexOf(pathSeparator);
        if ((nAfterPos == -1) || (nAfterPos == 0)) {
            return null;
        }
        // nBeforePos = file_name.lastIndexOf('/', nAfterPos-1);
        // compute the name removing / and file name
        // return file_name.substring(nBeforePos+1, nAfterPos)+".jar";
        String sRootPath = file_name.substring(0, nAfterPos);
        String sLocalPath = file_name.substring(0, nAfterPos + 1) + "octs.jar";
        String sWebPath = sLocalPath.substring(0);// file_name.substring( 0, nAfterPos+1)+"octs.jar";
        if (!pathSeparator.equals("/")) {
            sWebPath = sWebPath.replace(pathSeparator.charAt(0), '/');
        }
        return new String[] { sLocalPath, sWebPath, sRootPath };
    }

    public static final Integer FIND_RESOURCE_MALFORMED_NAME = Integer.valueOf(-1);

    public static final Integer FIND_RESOURCE_JAR_UNAVAILABLE = Integer.valueOf(-2);

    public static final Integer FIND_RESOURCE_FILE_UNAVAILABLE = Integer.valueOf(-3);

    public static final Integer FIND_RESOURCE_EMPTY_JAR = Integer.valueOf(-4);

    public static final Integer FIND_RESOURCE_NO_CACHE_DIRECTORY = Integer.valueOf(-5);

    public static final Integer FIND_RESOURCE_FILE_ISDOWNLOADING = Integer.valueOf(-6);

    public static final Integer FIND_RESOURCE_MAX_LEVEL = Integer.valueOf(1);

    public static final Integer FIND_RESOURCE_OK = Integer.valueOf(0);

    /**
     * searches in local cache directory for the jar who's name is determined by file's path<br>
     * <br>
     * Synchronize this functions' calls because of using the jar extraction.
     * 
     * @param name
     *            relative path to resource, including complete file name
     * @return Object[] that can be an InputStream if file is found, or an error type<br>
     *         If file is found in jar, the inputstream is return,<br>
     *         else an error code is returned:<br>
     *         -1 for malformed file name,<br>
     *         -2 for unavailable jar file in cache directory,<br>
     *         -3 for unavailable file in existing jar.<br>
     *         The array contains: input stream or null, operation code, operation message
     */
    private synchronized Object[] findResourceAsStream(String name, int nLocationIndex) {
        // check if cache directory exists
        if (!bIsCacheDir) {
            return new Object[] { null, FIND_RESOURCE_NO_CACHE_DIRECTORY,
                    new String("no available cache directory, giving up") };// no way!!! this can't be, this function has been called with a wrong parameter
        }
        boolean bMaxLevel = false;
        // check to see if file is available in repository list
        if (repLists[nLocationIndex] != null) {
            MapTreeNode nRet = repLists[nLocationIndex].getMap(name);
            if (bDebug) {
                System.out.println("get map " + name + " nRet=" + nRet);
            }
            if ((nRet == null) || !nRet.checkState(MapTreeNode.S_AVAILABLE)) {
                // file not available in list, so don't search it locally or on the web
                if (bDebug) {
                    System.out.println("FIND_RESOURCE_FILE_UNAVAILABLE for map " + name);
                }
                return new Object[] { null, FIND_RESOURCE_FILE_UNAVAILABLE,
                        new String("file is unavailable in repository, giving up") };
            }
            ;
            // check to see if this node isn't already downloading
            if (repLists[nLocationIndex].checkIsDonwloading(nRet)) {
                if (bDebug) {
                    System.out.println("FIND_RESOURCE_FILE_ISDOWNLOADING for map " + name);
                }
                return new Object[] { null, FIND_RESOURCE_FILE_ISDOWNLOADING,
                        new String("file is being downloaded, please have patience") };
            }
            // if this node has no children, then it means that is maximal level
            if (nRet.size() == 0) {
                bMaxLevel = true;
            }
        }
        // identify jar that contains the file
        // this is done by removing file name and adding jar extension to "name"
        String[] sJarFile;
        // compute the name removing "/" and file name
        sJarFile = getJarFromFileName(name);
        if (repLists[nLocationIndex] == null) {
            // check the stored file going to be downloaded from list if repository
            // list is not available
            // TODO: construct that list and check in a synchronized matter
            // if the file is already being downloaded
            // use sJarFile
            synchronized (dwlListsSyncObj) {
                Vector dwlL = dwlLists[nLocationIndex];
                for (int i = 0; i < dwlL.size(); i++) {
                    if (((String) dwlL.get(i)).equals(sJarFile[2])) {
                        return new Object[] { null, FIND_RESOURCE_FILE_ISDOWNLOADING,
                                new String("file is being downloaded, please have patience") };
                    }
                }
            }
        }
        String sBaseName = name;
        int nPos = sBaseName.lastIndexOf(pathSeparator);
        if (nPos != -1) {
            sBaseName = sBaseName.substring(nPos + 1);
        }
        // add extension to map file name
        sBaseName += Texture.sMapExt;
        if (sJarFile == null) {
            return new Object[] { null, FIND_RESOURCE_MALFORMED_NAME, new String("wrong file name format, giving up") };// no way!!! this can't be, this function has been called with a wrong parameter
        }
        boolean bJarFileFound = false;
        boolean bZeroFileError = false;
        InputStream is = null;
        try {
            String sSiteName = getSiteName(sExtraImagesLocations[nLocationIndex]);
            // open a file to check 0 length jars
            File f = new File(sCacheDir + sSiteName + pathSeparator + sJarFile[0]);
            // 0 length jar
            if (f.exists() && (f.length() == 0L)) {
                bZeroFileError = true;
            }
            // obtain stream from jar
            JarFile jf = new JarFile(f, false);
            bJarFileFound = true;
            // if ( bDebug )
            // System.out.println("get entry for "+sBaseName+".maxlevel --> "+(jf.getEntry(sBaseName+".maxlevel")!=null?"YES":"NO"));
            if (bStoreInJar) {
                if (jf.getEntry(sBaseName) != null) {
                    is = jf.getInputStream(jf.getEntry(sBaseName));
                    if (!bMaxLevel && (jf.getEntry(sBaseName + ".maxlevel") != null)) {
                        bMaxLevel = true;
                    }
                }
            } else {
                // obtain stream from file
                // System.out.println("trying to read: "+sCacheDir+sSiteName+pathSeparator+name);
                File fOct = new File(sCacheDir + sSiteName + pathSeparator + name + Texture.sMapExt);
                if (fOct.exists() && (fOct.length() > 0L)) {
                    is = new FileInputStream(fOct);
                    File fMaxLevel = new File(sCacheDir + sSiteName + pathSeparator + name + Texture.sMapExt
                            + ".maxlevel");
                    if (!bMaxLevel && fMaxLevel.exists()) {
                        bMaxLevel = true;
                    }
                }
            }
        } catch (Exception ex) {
            // no file found
            if (bDebug) {
                if (!bJarFileFound) {
                    System.out.println("jar file not found in cache.");
                } else {
                    System.out.println("searched entry not found in cached jar.");
                    // ex.printStackTrace();
                }
            }
        }
        if (is != null) {
            if (bMaxLevel) {
                if (bDebug) {
                    System.out.println("got entry for " + sBaseName + " at max level");
                }
                return new Object[] { is, FIND_RESOURCE_MAX_LEVEL,
                        new String("jar is valid, resource found, and at maximal level") };
            } else {
                return new Object[] { is, FIND_RESOURCE_OK, "jar is valid, resource found" };
            }
        }
        ;
        if (bZeroFileError) {
            return new Object[] { null, FIND_RESOURCE_JAR_UNAVAILABLE,
                    new String("jar is empty, error on downloading, retry download") };
        }
        // System.out.println(" not found");
        // treat error cases
        if (!bJarFileFound) {
            // no jar file found
            return new Object[] { null, FIND_RESOURCE_JAR_UNAVAILABLE,
                    new String("jar file not found in cache, download suggested") };
        } else {
            // no file in jar??
            return new Object[] { null, FIND_RESOURCE_FILE_UNAVAILABLE, new String("file not found in jar, giving up") };
        }
    }

    /**
     * find site name from location
     */
    private static String getSiteName(String sExtraImagesLocation) {
        String sRet = sExtraImagesLocation;
        int nIndex;
        // remove prefix: http:// or file:// or whatever
        if ((nIndex = sRet.indexOf("://")) != -1) {
            sRet = sRet.substring(nIndex + 3);
        }
        // for the rest of the path, transform path separator ( "/" or "\" ) in "_"
        sRet = sRet.replaceAll("/|\\\\", "_");
        sRet = sRet.replaceAll(":", "");
        if (sRet.endsWith("_")) {
            sRet = sRet.substring(0, sRet.length() - 1);
        }
        return sRet;
    }

    private void endDownload(int nIndex, String rootPath) {
        if (repLists[nIndex] != null) {
            repLists[nIndex].endDownload(rootPath);
        } else {
            // check the stored file going to be downloaded from list if repository
            // list is not available
            // remove the entry from list
            // use sJarFile
            synchronized (dwlListsSyncObj) {
                Vector dwlL = dwlLists[nIndex];
                for (int i = 0; i < dwlL.size(); i++) {
                    if (((String) dwlL.get(i)).equals(rootPath)) {
                        dwlL.remove(i);
                        break;
                    }
                    ;
                }
            }
        }
    }

    /**
     * tries to download jar containing searched resource.<br>
     * sets for the cached jar the last modification time of the one on the web<br>
     * to be able to update it when a new one is available.
     * 
     * @param name
     *            name of resource
     * @return success if jar is downloaded and now resource is available to load
     */
    private boolean tryDownloadJarForResource(String name, int nIndex) {
        // check if download directory exists
        if (!bIsCacheDir) {
            return false;
        }
        ;
        // get jar name
        String[] sJarFile;
        sJarFile = getJarFromFileName(name);
        if (sJarFile == null) {
            return false;
        }
        // check to see if another thread didn't already started the download
        // System.out.println("index is "+nIndex+" repository is "+repLists[nIndex]);
        if (repLists[nIndex] != null) {
            int retVal = repLists[nIndex].startDownload(sJarFile[2]);
            if (bDebug) {
                System.out.println("start downloading file " + sJarFile[0] + " result: " + retVal + " from repository "
                        + repLists[nIndex]);
            }
            // if root path is unavailable, then the find test will also fail
            // so it should never return -1
            if (retVal == -1) {
                return false;
            }
            // if already someone is downloading the requested file, that means
            // that a child node of this root path has is_downloading state set,
            // then it should wait for a while before returning, and then attempting
            // again, but the waiting is done at a higher level
            // the return value is true because the file is being downloaded
            // so it might be found by find or, at least will return is_downloading
            // state
            if (retVal == 0) {
                return true;
            }
            ;
        } else {
            // store file going to be downloaded in a list if repository list
            // is not available
            // TODO: construct that list and check in a synchronized matter
            // if the file is already being downloaded
            // use sJarFile
            synchronized (dwlListsSyncObj) {
                Vector dwlL = dwlLists[nIndex];
                for (int i = 0; i < dwlL.size(); i++) {
                    if (((String) dwlL.get(i)).equals(sJarFile[2])) {
                        return true;
                    }
                }
                // jar not downloading so set it for download
                dwlL.add(sJarFile[2]);
            }
        }
        if (bDebug) {
            System.out.println("jar name is local: " + sJarFile[0] + " and web: " + sExtraImagesLocations[nIndex]
                    + sJarFile[1]);
        }
        String sRelWebPath;
        // here I suppose thtat the name has an extension fo exactly 4 characters, mainly ".jar"
        if (sJarFile[1].length() > 4) {
            sRelWebPath = sJarFile[1].substring(0, sJarFile[1].length() - 4);
        } else {
            sRelWebPath = sJarFile[1];
        }
        boolean bUseProgressBar = false;
        boolean bHiddenBefore = false;
        // start downloading the jar
        try {
            // first create the store file in cache so that, if jar can't be found,
            // there'll be a dummy file so that I would not have to look for it
            // on the net. It is usefull for long delay connections.
            // create directories for storing the jar
            URL urlDown;
            InputStream isDown;
            long lTotalSize = 0;
            // get jar
            urlDown = new URL(sExtraImagesLocations[nIndex] + sJarFile[1]);
            URLConnection connection = urlDown.openConnection();
            // set web jar's last modification time to know when to update it
            // fJar.setLastModified(connection.getLastModified());
            lTotalSize = connection.getContentLength();// file size as reported by web server
            if (lTotalSize > MAX_JAR_SIZE) {
                // if ( repLists[nIndex]!=null )
                // repLists[nIndex].endDownload(sJarFile[2]);
                // else {
                // //check the stored file going to be downloaded from list if repository
                // //list is not available
                // // remove the entry from list
                // //use sJarFile
                // synchronized ( dwlListsSyncObj ) {
                // Vector dwlL = dwlLists[nIndex];
                // for ( int i=0; i<dwlL.size(); i++) {
                // if ( ((String)dwlL.get(i)).equals(sJarFile[2]) ) {
                // dwlL.remove(i);
                // break;
                // };
                // }
                // }
                // }
                endDownload(nIndex, sJarFile[2]);
                return false;// dimension reported by web server too big
            }

            // server did not provide the length, so try to find it from jarsize file
            /*
             * if ( lTotalSize==-1 ) {
             * //get jar size
             * URL urlDown2;
             * InputStream isDown2;
             * urlDown2 = new URL(sExtraImagesLocation+sJarFile[1]+"size");//extend jar name by adding "size" to
             * extension
             * if ( bDebug )
             * System.out.println("jar size file is: "+sExtraImagesLocation+sJarFile[1]+"size");
             * isDown2 = urlDown2.openStream();
             * if ( bDebug )
             * System.out.println("jar size file stream is opened");
             * int nCount=10;//dimension of jar has maximum 10 characters
             * int car;
             * while( nCount>0 && (car=isDown2.read())!=-1 ) {
             * //System.out.println("read car: "+(char)car);
             * if ( car>='0' && car<='9' )
             * lTotalSize = lTotalSize*10+((char)car-'0');
             * nCount--;
             * };
             * isDown2.close();
             * if ( bDebug )
             * System.out.println("jar size file stream is closed, size is "+lTotalSize);
             * if ( nCount<0 )
             * return false;
             * };
             */
            if ((lTotalSize <= 0) || (lTotalSize > MAX_JAR_SIZE)) {// invalid file size for jar
                endDownload(nIndex, sJarFile[2]);
                return false;
            }
            isDown = connection.getInputStream();
            // isDown = urlDown.openStream();
            if (bDebug) {
                System.out.println("jar file stream is opened");
            }

            // site directory name from where from jar is downloaded
            String sSiteName = getSiteName(sExtraImagesLocations[nIndex]);
            // if an connection for download is available, try to create the output directory and file
            if (!doJarDirectories(sCacheDir, sSiteName + pathSeparator + sJarFile[0])) {
                if (bDebug) {
                    System.out.println("could not create directories to jar file: " + sSiteName + pathSeparator
                            + sJarFile[0]);
                }
                endDownload(nIndex, sJarFile[2]);
                return false;
            }
            String sTempJarFullPath, sJarFullPath;
            sTempJarFullPath = sCacheDir + sSiteName + pathSeparator + sJarFile[0] + ".temp";
            sJarFullPath = sCacheDir + sSiteName + pathSeparator + sJarFile[0];
            File fJar = new File(sTempJarFullPath);
            // set the smallest date, that is 00:00:00 GMT, January 1, 1970 plus one milisecond
            fJar.setLastModified(1);
            if (bDebug) {
                System.out.println("output file created in cache: " + sCacheDir + sSiteName + pathSeparator
                        + sJarFile[0]);
            }

            long lReadSoFar = 0;
            byte buff[] = new byte[nBufferSize];
            int nRead = -1;
            long /* curRate, */startTime, fullStartTime, fullEndTime;
            startTime = NTPDate.currentTimeMillis();
            fullStartTime = startTime;
            // curRate = 0;
            FileOutputStream fOS = new FileOutputStream(fJar);
            if (fOS == null) {
                if (bDebug) {
                    System.out.println("could not open output stream to: " + sCacheDir + sSiteName + pathSeparator
                            + sJarFile[0]);
                }
                endDownload(nIndex, sJarFile[2]);
                return false;
            }
            // show action on file
            synchronized (objSyncProgressAccess) {
                if (!bProgressOccupied) {
                    bProgressOccupied = true;
                    bUseProgressBar = true;
                }
                ;
            }
            // rehide panel, if hidden from start
            if (bUseProgressBar && (jpDownloadPanel != null)) {
                if (jpDownloadPanel.isVisible() == false) {
                    bHiddenBefore = true;
                    jpDownloadPanel.setVisible(true);
                }
            }
            if (bUseProgressBar && (jpbDownloadBar != null)) {
                jpbDownloadBar.setStringPainted(true);
                jpbDownloadBar.setString("Downloading " + sRelWebPath);
                jpbDownloadBar.setMinimum(0);
                jpbDownloadBar.setMaximum(100);
                jpbDownloadBar.setValue(0);
            }
            ;

            do {
                // if ( bDebug )
                // System.out.println("prepare to read "+nBufferSize+" bytes");
                nRead = isDown.read(buff);
                // if ( bDebug )
                // System.out.println("read "+nRead+" bytes");
                if (nRead > 0) {
                    lReadSoFar += nRead;
                    // if ( bDebug )
                    // System.out.println("read so far: "+lReadSoFar);
                    if (bUseProgressBar && (jpbDownloadBar != null)) {
                        jpbDownloadBar.setValue((int) ((100 * lReadSoFar) / lTotalSize));
                    }
                    fOS.write(buff, 0, nRead);
                    // update current rate
                    // curRate += nRead;
                    // if ( curRate >= nTopRate ) {
                    // curRate -=nTopRate;
                    // endTime = NTPDate.currentTimeMillis();
                    // if downloaded faster than the set tranfer rate, then sleep a little bit
                    // if ( endTime-startTime<1000)
                    // try {
                    // Thread.sleep(endTime-startTime);
                    // } catch(Exception ex) {
                    // //do nothing
                    // }
                    // }
                }
            } while (nRead > 0);
            fullEndTime = NTPDate.currentTimeMillis();
            fOS.flush();
            fOS.close();
            isDown.close();
            // set web jar's last modification time to know when to update it
            fJar.setLastModified(connection.getLastModified());
            if (bDebug) {
                System.out.println("jar file stream is closed, total read " + lReadSoFar);
            }
            if (bUseProgressBar && (jpDownloadPanel != null)) {
                if (bHiddenBefore) {
                    jpDownloadPanel.setVisible(false);
                }
            }
            if (bUseProgressBar && (jpbDownloadBar != null)) {
                jpbDownloadBar.setStringPainted(false);
                jpbDownloadBar.setString("");
                jpbDownloadBar.setValue(0);
            }
            ;
            // free access to progress bar
            if (bUseProgressBar) {
                synchronized (objSyncProgressAccess) {
                    if (bProgressOccupied) {// should always be true
                        bProgressOccupied = false;
                        bUseProgressBar = false;
                    }
                    ;
                }
            }
            // download ended, so check the file integrity by checking size
            if (lReadSoFar != lTotalSize) {
                logger.log(Level.WARNING, "Invalid reported size (" + lTotalSize + "b) with downloaded size ("
                        + lReadSoFar + "b) for jar " + sJarFile[1]);
                // if jar no good, delete it
                // File fJar = new File(sCacheDir+sJarFile);
                try {
                    if (fJar.isFile()) {
                        if (!fJar.delete()) {
                            logger.log(Level.WARNING, "Invalid downloaded jar file " + sJarFile[0]
                                    + " could not be deleted.");
                        }
                    }
                } catch (SecurityException sex) {
                    logger.log(Level.WARNING, "Could not delete temporary downloaded jar file " + sJarFile[0]
                            + ", no delete right.");
                }
                endDownload(nIndex, sJarFile[2]);
                return false;
            } else {
                // file complete, so move from temporary name to real name
                boolean bFileOk = false;
                try {
                    File fRealJar = new File(sJarFullPath);
                    if (fJar.isFile()) {
                        if (!fJar.renameTo(fRealJar)) {
                            logger.log(Level.WARNING, "Could not rename temporary downloaded jar file " + sJarFile[0]
                                    + ".");
                        } else {
                            bFileOk = true;
                        }
                    } else {
                        logger.log(Level.WARNING, "No temporary downloaded jar file " + sJarFile[0] + " is available.");
                    }
                } catch (SecurityException sex) {
                    logger.log(Level.WARNING, "Could not rename temporary downloaded jar file " + sJarFile[0]
                            + ", no write right.");
                }
                if (!bFileOk) {
                    endDownload(nIndex, sJarFile[2]);
                    return false;// could not rename temporary downloaded file, so return false
                }
            }
            logger.log(logLevel, "jar file " + sJarFile[1] + " with size " + lTotalSize + "b downloaded in "
                    + (fullEndTime - fullStartTime) + "ms");
            if (bDebug) {
                System.out.println("jar file " + sJarFile[1] + " with size " + lTotalSize + "b downloaded in "
                        + (fullEndTime - fullStartTime) + "ms");
            }
            // time to extract files if so requested
            try {
                if (!bStoreInJar) {
                    // files should be put directly on hard
                    JarFile jf = new JarFile(fJar);
                    InputStream isOctFile;
                    FileOutputStream osOctFile;
                    // remove trailling "octs.jar"
                    String pathToJar = sCacheDir + sSiteName + pathSeparator
                            + sJarFile[0].substring(0, sJarFile[0].length() - 8);
                    for (Enumeration en = jf.entries(); en.hasMoreElements();) {
                        JarEntry jarEntry = (JarEntry) en.nextElement();
                        if (!jarEntry.isDirectory() && !jarEntry.getName().startsWith("META-INF")) {
                            isOctFile = jf.getInputStream(jarEntry);
                            osOctFile = new FileOutputStream(pathToJar + jarEntry.getName());
                            do {
                                nRead = isOctFile.read(buff);
                                if (nRead > 0) {
                                    osOctFile.write(buff, 0, nRead);
                                }
                            } while (nRead > 0);
                            osOctFile.close();
                            isOctFile.close();
                            if (bDebug) {
                                System.out.println("File " + jarEntry.getName() + " successfully extracted from jar "
                                        + sJarFile[0] + " to " + pathToJar);
                            }
                        }
                        ;
                    }
                }
            } catch (Exception ex) {
                if (bDebug) {
                    System.out.println("Exception while unpacking jar: " + sJarFile[0]);
                    ex.printStackTrace();
                }
                ;
            }
            endDownload(nIndex, sJarFile[2]);
            return true;
        } catch (Exception ex) {
            // file could not be opened
            if (bUseProgressBar && (jpDownloadPanel != null)) {
                if (bHiddenBefore) {
                    jpDownloadPanel.setVisible(false);
                }
            }
            if (bUseProgressBar && (jpbDownloadBar != null)) {
                jpbDownloadBar.setStringPainted(false);
                jpbDownloadBar.setString("");
                jpbDownloadBar.setValue(0);
            }
            ;
            // free access to progress bar
            if (bUseProgressBar) {
                synchronized (objSyncProgressAccess) {
                    if (bProgressOccupied) {// should always be true
                        bProgressOccupied = false;
                        bUseProgressBar = false;
                    }
                    ;
                }
            }
            // ignore error
            // logger.log(Level.WARNING, "Could not download: "+sJarFile);
            if (bDebug) {
                System.out.println("Could not download: " + sExtraImagesLocations[nIndex] + sJarFile[1]);
                // ex.printStackTrace();
            }
            ;
        }
        endDownload(nIndex, sJarFile[2]);
        return false;
    }

    /**
     * assuming that jarFile is a relative path to a file, construct the directories to the path
     * 
     * @param cacheDir
     *            absolute local path to file
     * @param jarFile
     *            relative local path to file, including file name
     * @return returns true or false if it succeded in creating the directories
     */
    private boolean doJarDirectories(String cacheDir, String jarFile) {
        int nStartPos = 0;
        int nEndPos = -1;
        nEndPos = jarFile.indexOf(pathSeparator, nStartPos);
        try {
            while (nEndPos != -1) {
                File f = new File(cacheDir + jarFile.substring(0, nEndPos));
                if (bDebug) {
                    System.out.println("creating directory: " + cacheDir + jarFile.substring(0, nEndPos));
                }
                if (!f.isDirectory()) {
                    f.mkdir();
                }
                nStartPos = nEndPos + 1;
                nEndPos = jarFile.indexOf(pathSeparator, nStartPos);
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    /**
     * tries to load the resource specified by name<br>
     * using first the parent class loader,<br>
     * and then, if not found, it assumes that an oct file is supposed to load
     * so it tries to find it in cache directory by searching for jar file
     * containing it. If such a jar is not found, it tries to download the jar
     * from web, form a location specified at startup. Although the jar file
     * may not be available, a 0 length file is created so that next time when
     * the jar will be searched, the web site should not be used as the file is
     * not available. If jar is successfully downloaded, a new try to load it
     * is done.<br>
     * Another thread in a timer is responsable for updating local jars by
     * deleting the ones that have a date older than the one on the site.<br>
     */
    public Object[] getResourceAsStream(String name) {
        InputStream is = null;
        Integer errorCode = null;
        String errorMsg = null;
        try {
            if (bDebug) {
                System.out.println("get resource: " + name + Texture.sMapExt);
            }
            // very specialized class loader, only for octs
            // is = super.getResourceAsStream(name);
            if ((name.length() > 0) && name.startsWith("*")) {// resource should be loaded using normal classloader
                is = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(name.substring(1) + Texture.sMapExt);
            } else {
                // search in cache, or download it
                if (bDebug) {
                    System.out.println("find resource: " + name + Texture.sMapExt);
                }
                // search in local list of jars
                Object[] obJarInfos;
                for (int i = 0; i < sExtraImagesLocations.length; i++) {
                    obJarInfos = findResourceAsStream(name, i);
                    if ((obJarInfos != null) && (obJarInfos.length == 3)) {
                        is = (InputStream) obJarInfos[0];
                        errorCode = (Integer) obJarInfos[1];
                        errorMsg = (String) obJarInfos[2];
                        if (bDebug) {
                            System.out.println("find resource at " + sExtraImagesLocations[i] + " : " + errorMsg);
                        }
                        if (is != null) {
                            break;
                        }
                        // return new Object[] { is, errorCode };
                        if ((errorCode == FIND_RESOURCE_JAR_UNAVAILABLE) && bDownloadNewFiles) {// download this file only
                                                                                                // if new files are
                                                                                                // allowed to be
                                                                                                // downloaded
                            // jar not found in cache dir, so download it
                            // first, check the directory limit size to see if is ok
                            // and eventually make some space
                            // --> done in another thread
                            // if available
                            // from this point, a thread would be good?
                            // try to download jar containing resource, and then reconstruct the
                            // cache and load the resource if available
                            // download from all sources, but use the first one
                            if (tryDownloadJarForResource(name, i)) {
                                if (bDebug) {
                                    System.out.println("jar for resource " + name + " downloaded from "
                                            + sExtraImagesLocations[i]);
                                }
                                obJarInfos = findResourceAsStream(name, i);
                                if ((obJarInfos != null) && (obJarInfos.length == 3)) {
                                    is = (InputStream) obJarInfos[0];
                                    errorCode = (Integer) obJarInfos[1];
                                    errorMsg = (String) obJarInfos[2];
                                    if (bDebug && (errorMsg != null)) {
                                        System.out.println("message: " + errorMsg);
                                    }
                                    break;
                                } else {
                                    System.out.println("error in info from findResource for " + name + " from "
                                            + sExtraImagesLocations[i]);
                                }
                            }
                            ;
                        }
                        ;
                    }
                }
                ;
                // check to see if the file was found in cache, and, if so, save its access time
                // if ( is!=null ) {
                // String[] sJarFile;
                // //compute the name removing "/" and file name
                // sJarFile = getJarFromFileName(name);
                // if ( sJarFile!=null )
                // hMapsLastAccessTime.put(sJarFile[0], Long.valueOf(NTPDate.currentTimeMillis()));
                // }
            }
            ;
        } catch (Exception ex) {
            // some exception occured, ignore it
            if (bDebug) {
                ex.printStackTrace();
            }
        }
        // }
        return new Object[] { is, errorCode };
    }

    // public /*InputStream*/Object[] getResourceAsStream_old(String name)
    // {
    // InputStream is=null;
    // Integer errorCode=null;
    // String errorMsg=null;
    // try {
    // if ( bDebug )
    // System.out.println("get resource: "+name);
    // //very specialized class loader, only for octs
    // //is = super.getResourceAsStream(name);
    // if ( name.length()>0 && name.startsWith("*") ) {//resource should be loaded using normal classloader
    // is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name.substring(1));
    // } else {
    // //search in cache, or download it
    // if ( bDebug )
    // System.out.println("find resource: "+name);
    // //search in local list of jars
    // Object[] obJarInfos;
    // for ( int i=0; i<sExtraImagesLocations.length; i++ ) {
    // obJarInfos = findResourceAsStream( name, i);
    // if ( obJarInfos!=null && obJarInfos.length==3 ) {
    // is = (InputStream)obJarInfos[0];
    // errorCode = (Integer)obJarInfos[1];
    // errorMsg = (String)obJarInfos[2];
    // if ( bDebug )
    // System.out.println("find resource at "+sExtraImagesLocations[i]+" : "+errorMsg);
    // if ( is!=null )
    // break;
    // //return new Object[] { is, errorCode };
    // if ( errorCode==FIND_RESOURCE_JAR_UNAVAILABLE
    // && bDownloadNewFiles ) {//download this file only if new files are allowed to be downloaded
    // //jar not found in cache dir, so download it
    // //first, check the directory limit size to see if is ok
    // //and eventually make some space
    // //--> done in another thread
    // //if available
    // //from this point, a thread would be good?
    // //try to download jar containing resource, and then reconstruct the
    // //cache and load the resource if available
    // //download from all sources, but use the first one
    // if ( tryDownloadJarForResource( name, i) ) {
    // if ( bDebug )
    // System.out.println("jar for resource "+name+" downloaded from "+sExtraImagesLocations[i]);
    // obJarInfos = findResourceAsStream( name, i);
    // if ( obJarInfos!=null && obJarInfos.length==3 ) {
    // is = (InputStream)obJarInfos[0];
    // errorCode = (Integer)obJarInfos[1];
    // errorMsg = (String)obJarInfos[2];
    // if ( bDebug && errorMsg!=null )
    // System.out.println("message: "+errorMsg);
    // break;
    // } else
    // System.out.println("error in info from findResource for "+name+" from "+sExtraImagesLocations[i]);
    // };
    // };
    // }
    // };
    // //check to see if the file was found in cache, and, if so, save its access time
    // // if ( is!=null ) {
    // // String[] sJarFile;
    // // //compute the name removing "/" and file name
    // // sJarFile = getJarFromFileName(name);
    // // if ( sJarFile!=null )
    // // hMapsLastAccessTime.put(sJarFile[0], Long.valueOf(NTPDate.currentTimeMillis()));
    // // }
    // };
    // } catch (Exception ex) {
    // //some exception occured, ignore it
    // if ( bDebug )
    // ex.printStackTrace();
    // }
    // // }
    // return new Object[] {is, errorCode};
    // }

    /**
     * checkes for new jars by verifying an index file's last modification time
     * and then it's content<br>
     * file that contains the list of jars and their last modification time has
     * the format:<br>
     * _jar_file_name_ _space_ _last_modification_time_in_seconds_since_`00:00:00 1970-01-01 UTC'<br>
     * removes old unused jars from cache based on access date
     */
    private void checkNewJarsAndClearCache() {
        // check first if cache directory exists
        if (!bIsCacheDir) {
            return;
        }
        if (!bDownloadNewFiles) {
            return;
        }

        /*
         * try {
         * //site directory name from where from jar is downloaded
         * String sSiteName=getSiteName(sExtraImagesLocations[i]);
         * File fLMTime = new File(sCacheDir+sSiteName+pathSeparator+sJarsLMTimeFile);
         * //set the smallest date, that is 00:00:00 GMT, January 1, 1970 plus one milisecond
         * long localLastModified=fLMTime.lastModified();
         * URL urlDown;
         * InputStream isDown;
         * //get jars list file
         * urlDown = new URL(sExtraImagesLocations[i]+sSiteName+"/"+sJarsLMTimeFile);
         * URLConnection connection = urlDown.openConnection();
         * //compare web file's lm time with local file
         * //Obs: if local file does not yet exists, it's lm time will be 0,
         * //as returned by File.lastModified()
         * long webLastModified = connection.getLastModified();
         * if ( bDebug )
         * System.out.println("jars file lmtimes: local is "+localLastModified+" web: "+webLastModified);
         * if ( localLastModified<webLastModified ) {
         * //update list only if web list is newer than local one
         * //set web jar's last modification time to know when to update it
         * fLMTime.setLastModified(webLastModified);
         * //write from web to local file
         * FileOutputStream fOS = new FileOutputStream(fLMTime);
         * isDown = connection.getInputStream();
         * byte buff[] = new byte[nBufferSize];
         * int nRead = -1;
         * do {
         * nRead = isDown.read(buff);
         * if ( nRead > 0 ) {
         * fOS.write(buff, 0, nRead);
         * }
         * } while ( nRead > 0 );
         * fOS.flush();
         * fOS.close();
         * isDown.close();
         * //read the file and check every jar's lmt
         * BufferedReader br = null;
         * br = new BufferedReader( new InputStreamReader(new FileInputStream(fLMTime)));
         * if ( br != null ) {
         * String sLine;
         * // String sFileName, sDate;
         * // int i=0, len, nCount;
         * // char car;
         * // ArrayList arJarContent = null;
         * String sJarFileName, sJarLMTime;
         * int nSpace;
         * // long lJarLMTime;
         * File fJar;
         * while ( (sLine=br.readLine())!=null ) {
         * //parse a line from jars file and get jar's name and last modification time
         * nSpace = sLine.indexOf(' ');
         * if ( nSpace!=-1 ) {
         * sJarFileName = sLine.substring(0, nSpace);
         * sJarLMTime = sLine.substring(nSpace+1);
         * if ( bDebug )
         * System.out.println("read line containing "+sJarFileName+" with lmt: "+sJarLMTime);
         * //check each file that is in cache
         * //if has the time less than this one
         * //and, if so, delete it so that it can be re-downloaded
         * try {
         * fJar = new File(sCacheDir+sSiteName+pathSeparator+sJarFileName);
         * if ( bDebug )
         * System.out.println("lmtimes: local is "+(fJar.lastModified()/1000)+" web: "+Long.parseLong(sJarLMTime));
         * if ( fJar.isFile() && fJar.lastModified()/1000<Long.parseLong(sJarLMTime) )
         * fJar.delete();
         * } catch ( Exception ex ) {
         * //some exception treating file sJarFileName
         * logger.log(Level.WARNING,"some exception during checking lmtime for "+sJarFileName);
         * ex.printStackTrace();
         * }
         * }
         * }
         * br.close();
         * }
         * }
         * } catch ( Exception ex ) {
         * //some exception occured
         * logger.log(Level.WARNING,"some exception while updating list of available jars");
         * ex.printStackTrace();
         * }
         */
        // check cache size
        /**
         * the cache size will be computed only from number of files point of view.
         * If 683 jar files occupy 425MB, then the average size of a file is 637KB.
         * If the user wants to make available only about 50 MB, then the number of files
         * allowed to reside in cache is about 80. So, delete all files older than the 80th file.
         * If we are to consider the multiple locations... this is more difficult.
         */
        // try {
        // //list all files
        // TreeSet fileSet = new TreeSet(new FileDateComparator());
        // // Add files to fileSet
        //
        // Iterator fileIt = fileSet.iterator();
        // while (fileIt.hasNext()) {
        // // do stuff to the files or something!
        // }
        // File fCacheDir = new File(sCacheDir);
        // fCacheDir.listFiles()
        // //compute sizes
        // //delete some files with the oldest access time
        // //so that cache directory has correct size
        // } catch ( Exception ex ) {
        // //some exception occured
        // logger.log(Level.WARNING,"some exception while checking cache directory's size");
        // ex.printStackTrace();
        // }
    }

    class FileAccessTimeComparator implements Comparator {

        @Override
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof File) || !(o2 instanceof File)) {
                throw new IllegalArgumentException();
            }
            File f1 = (File) o1;
            File f2 = (File) o2;
            if (f1.lastModified() < f2.lastModified()) {
                return 1;
            } else if (f1.lastModified() > f2.lastModified()) {
                return -1;
            }
            return 0;
        }
    }

    /**
     * sets usable GUI interface to show download statistics
     * 
     * @param jpb
     * @param jlb
     */
    public void setProgressBar(JProgressBar jpb, JComponent jmp) {
        jpbDownloadBar = jpb;
        jpDownloadPanel = jmp;
    }

    public void clearProgressBar() {
        jpbDownloadBar = null;
        jpDownloadPanel = null;
    }

    /**
     * returns path to cached_maps/ directory
     * 
     * @return
     */
    public String getCacheDir() {
        return sCacheDir.substring(0, sCacheDir.length() - 12);
    }

    public void setCacheDir(String cacheDir) {
        sCacheDir = cacheDir;
        if (!sCacheDir.endsWith(pathSeparator)) {
            sCacheDir += pathSeparator;
        }
        sCacheDir += "cached_maps" + pathSeparator;
        // print a path
        // System.out.println("System.getProperty(\"deployment.user.cachedir\")="+System.getProperty("deployment.user.cachedir"));
        // add an extra slash to directory if missing
        File fCacheDir = new File(sCacheDir);
        if (fCacheDir.exists() && !fCacheDir.isDirectory()) {
            sCacheDir = ".";
        } else if (!fCacheDir.exists()) {
            try {
                fCacheDir.mkdir();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Could not create cache directory at: " + sCacheDir);
            }
        }
        // check if download directory exists
        fCacheDir = new File(sCacheDir);
        if (!fCacheDir.isDirectory()) {
            logger.log(Level.WARNING, "No cache directory available at: " + sCacheDir);
            bIsCacheDir = false;
        } else {
            bIsCacheDir = true;
        }
        logger.log(Level.INFO, "jars cache directory is " + sCacheDir);
    }

    public String getExtraImagesLocations() {
        if (sExtraImagesLocations == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String sExtraImagesLocation : sExtraImagesLocations) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(sExtraImagesLocation);
        }
        return sb.toString();
    }

    /**
     * sets the extra images locations <br>
     * made of web paths separated with commas
     * 
     * @param extraImagesLocations
     *            - comma separated list of web paths
     */
    public void setExtraImagesLocations(String extraImagesLocations) {
        if ((extraImagesLocations == null) || (extraImagesLocations.length() == 0)) {
            return;
        }
        sExtraImagesLocations = extraImagesLocations.split(",");
        for (int i = 0; i < sExtraImagesLocations.length; i++) {
            if (!sExtraImagesLocations[i].endsWith("/")) {
                sExtraImagesLocations[i] += "/";
            }
        }
        ;
        // create repositories array
        if (sExtraImagesLocations.length > 0) {
            dwlLists = new Vector[sExtraImagesLocations.length];
            RepositoryList[] auxRL;
            auxRL = new RepositoryList[sExtraImagesLocations.length];
            for (int i = 0; i < sExtraImagesLocations.length; i++) {
                auxRL[i] = null;
                dwlLists[i] = new Vector();
            }
            ;
            // update list of available jars
            for (int i = 0; i < sExtraImagesLocations.length; i++) {
                if (bDebug) {
                    System.out.println("check list of jars");
                }

                RepositoryList repList = new RepositoryList(sExtraImagesLocations[i]);
                if (repList.getAvailableList()) {
                    auxRL[i] = repList;
                }
            }
            ;
            // TODO: should take all download jobs from old repository lists
            // if they are in the new array.
            repLists = auxRL;
        } else {
            dwlLists = new Vector[0];
            repLists = new RepositoryList[0];
        }

    }
}
