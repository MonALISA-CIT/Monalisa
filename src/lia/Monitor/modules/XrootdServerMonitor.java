package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.security.authz.Format;

public class XrootdServerMonitor extends AbstractSchJobMonitoring implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(XrootdServerMonitor.class.getName());

    String sXrootdInstallationPath = null;

    /**
     * Generated serial ID
     */
    private static final long serialVersionUID = 6409603627742754224L;

    /**
     * Get the set of directories used by an xrootd installation
     * 
     * @param installationPath the base of xrootd installation
     * @return
     */
    public static final Set<String> getDirectories(final String installationPath) {
        final Set<String> ret = new HashSet<String>();

        final Map<String, String> variables = new HashMap<String, String>();

        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(installationPath + "etc/xrootd/server/xrootd.cf"));

            String sLine;

            while ((sLine = br.readLine()) != null) {
                sLine = sLine.trim();

                if (sLine.startsWith("#") || (sLine.length() == 0)) {
                    continue;
                }

                final StringTokenizer st = new StringTokenizer(sLine);

                final String sDir = st.nextToken();

                if (sDir.equals("set")) {
                    // set osscachepath = /atlas/xrdcache

                    String sKey = st.nextToken();

                    boolean bEqualsFound = false;

                    if (sKey.endsWith("=")) {
                        sKey = sKey.substring(0, sKey.length() - 1).trim();
                        bEqualsFound = true;
                    }

                    String sValue = st.nextToken("\n").trim();

                    if (!bEqualsFound && sValue.startsWith("=")) {
                        sValue = sValue.substring(1).trim();
                    }

                    variables.put(sKey, sValue);
                    continue;
                }

                if (sDir.equals("oss.cache") || sDir.equals("oss.localroot")) {
                    // oss.cache public /data04/
                    // oss.cache ATLASMCDISK $(osscachepath)/atlasmcdisk xa
                    // oss.localroot /data01/data

                    if (sDir.equals("oss.cache")) {
                        st.nextToken();
                    }

                    final String sPath = st.nextToken();

                    String sParsedPath = followLinks(parsePath(sPath, variables));

                    if ((sParsedPath != null) && (sParsedPath.length() > 0)) {
                        if (!sParsedPath.endsWith("/")) {
                            sParsedPath += "/";
                        }

                        ret.add(sParsedPath);
                    }
                }
            }
        } catch (IOException ioe) {
            // ignore
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        return ret;
    }

    /**
     * Replace all occurrences of $(variable) with the corresponding value in the map
     * 
     * @param sPath path definition
     * @param variables variable->value mapping
     * @return parsed path
     */
    public static String parsePath(final String sPath, final Map<String, String> variables) {
        if ((sPath == null) || (sPath.length() == 0) || (variables == null) || (variables.size() == 0)
                || (sPath.indexOf("$(") < 0)) {
            return sPath;
        }

        String sRet = sPath;

        for (final Map.Entry<String, String> me : variables.entrySet()) {
            sRet = Format.replace(sPath, "$(" + me.getKey() + ")", me.getValue());
        }

        return sRet;
    }

    /**
     * Find the base mount point of a given path
     * 
     * @param path path to check
     * @param mountPoints set of mount points
     * @return the longest mount point that is prefix for the path
     */
    public static final String getMountPoint(final String path, final TreeSet<String> mountPoints) {
        String sPoint = null;

        for (final String sMountPoint : mountPoints) {
            if (path.startsWith(sMountPoint)) {
                sPoint = sMountPoint;
            }
        }

        return sPoint;
    }

    /**
     * Get the device from which the given path is mounted
     * 
     * @param path path to lookup
     * @param mountPoints sorted map of mount point->device
     * @return the device, if found
     */
    public static final String getDevice(final String path, final TreeMap<String, String> mountPoints) {
        String sDevice = null;

        for (Map.Entry<String, String> me : mountPoints.entrySet()) {
            final String sMountPoint = me.getKey();

            if (path.startsWith(sMountPoint)) {
                sDevice = me.getValue();
            }
        }

        return sDevice;
    }

    /**
     * Check the entire path for links and resolve them recurrently until only real directories
     * or files are present.
     * 
     * @param path initial path
     * @return absolute path with no links
     */
    public static final String followLinks(final String path) {
        File f = new File(path);

        try {
            while (!f.getCanonicalFile().equals(f.getAbsoluteFile())) {
                f = f.getCanonicalFile();
            }

            File fParent = f.getCanonicalFile().getParentFile();

            if (fParent != null) {
                f = new File(followLinks(fParent.getCanonicalPath()), f.getName());
            }

            return f.getCanonicalPath();
        } catch (IOException ioe) {
            // ignore
        }

        return path;
    }

    /**
     * Parse the system configuration (either /proc/self/mounts or `mount`) to produce the map
     * of mount point->device.
     * 
     * @return
     */
    public static final TreeMap<String, String> getSystemMountPoints() {
        final File f = new File("/proc/self/mounts");

        if (f.exists() && f.isFile() && f.canRead()) {
            return getSystemMountPoints(f);
        }

        final TreeMap<String, String> ret = new TreeMap<String, String>();

        final BufferedReader br = new BufferedReader(new StringReader(Utils.getOutput("mount")));

        String sLine;

        final boolean bIsSolaris = System.getProperty("os.name").startsWith("Sun");

        try {
            while ((sLine = br.readLine()) != null) {
                // Linux    : /dev/sdb on /data01 type xfs (rw,noatime,logbsize=256k,logbufs=8,inode64,swalloc)
                // Mac OS X : /dev/disk1s2    732238672 147314620 584668052    21%    /
                // Solaris  :  /export/home on rpool/export/home read/write/setuid/devices/nonbmand/exec/xattr/atime/dev=2d90007 on Thu Jun  3 15:41:28 2010
                //             (the order is reversed! bipbip)
                final StringTokenizer st = new StringTokenizer(sLine);

                final String sDevice;
                final String sMountPoint;

                if (!bIsSolaris) {
                    // on linux we might have something like:
                    // 		/dev/disk/by-uuid/1efd152e-aad0-4f92-b262-eb8051b7d71a / xfs rw,noatime,attr2,noquota 0 0
                    // so we have to follow the links to figure out the actual device ...
                    sDevice = followLinks(st.nextToken());
                    st.nextToken();
                    sMountPoint = st.nextToken();
                } else {
                    sMountPoint = st.nextToken();
                    st.nextToken();
                    sDevice = st.nextToken();
                }

                ret.put(sMountPoint.endsWith("/") ? sMountPoint : sMountPoint + '/', sDevice);
            }
        } catch (IOException ioe) {
            // ignore, can't be
        }

        return ret;
    }

    /**
     * In case of Linux we can simply parse the /proc/self/mounts
     * 
     * @param f pointer to the file to parse
     * @return
     */
    private static final TreeMap<String, String> getSystemMountPoints(final File f) {
        final TreeMap<String, String> ret = new TreeMap<String, String>();

        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(f));

            String sLine;

            while ((sLine = br.readLine()) != null) {
                // /dev/disk/by-uuid/1efd152e-aad0-4f92-b262-eb8051b7d71a / xfs rw,noatime,attr2,noquota 0 0
                final StringTokenizer st = new StringTokenizer(sLine);

                final String sDevice = followLinks(st.nextToken());
                final String sMountPoint = st.nextToken();

                ret.put(sMountPoint.endsWith("/") ? sMountPoint : sMountPoint + '/', sDevice);
            }
        } catch (IOException ioe) {
            // ignore
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        return ret;
    }

    @Override
    public Object doProcess() throws Exception {
        if (sXrootdInstallationPath == null) {
            throw new Exception("Xrootd installation cannot be found");
        }

        final Set<String> directories = getDirectories(sXrootdInstallationPath);

        if ((directories == null) || (directories.size() == 0)) {
            throw new Exception("No directories found in the configuration file `" + sXrootdInstallationPath + "`");
        }

        final TreeMap<String, String> systemMountPoints = getSystemMountPoints();

        if ((systemMountPoints == null) || (systemMountPoints.size() == 0)) {
            throw new Exception("Could not determine the mount point from /proc/self/mounts");
        }

        final Set<String> mountPoints = new TreeSet<String>();

        final Set<String> devices = new TreeSet<String>();

        final TreeSet<String> systemMounted = new TreeSet<String>(systemMountPoints.keySet());

        final Collection<Object> ret = new Vector<Object>();

        final Map<String, eResult> erMapPerDirectory = new HashMap<String, eResult>();
        final Map<String, eResult> erMapPerMountPoint = new HashMap<String, eResult>();
        final Map<String, eResult> erMapPerDevice = new HashMap<String, eResult>();

        for (final String dir : directories) {
            final eResult erDir = neweResult();

            erDir.ClusterName = "directory";
            erDir.NodeName = transformPaths(dir);

            erMapPerDirectory.put(dir, erDir);

            final String sMountPoint = getMountPoint(dir, systemMounted);

            if ((sMountPoint != null) && (sMountPoint.length() > 0)) {
                mountPoints.add(sMountPoint);

                erDir.addSet("mount_point", sMountPoint);

                eResult erMountPoint = erMapPerMountPoint.get(sMountPoint);

                if (erMountPoint == null) {
                    erMountPoint = neweResult();
                    erMountPoint.ClusterName = "mount_points";
                    erMountPoint.NodeName = transformPaths(sMountPoint);

                    erMapPerMountPoint.put(sMountPoint, erMountPoint);
                }

                final String sDevice = getDevice(dir, systemMountPoints);

                if ((sDevice != null) && (sDevice.length() > 0)) {
                    devices.add(sDevice);

                    erDir.addSet("device", sDevice);

                    if (erMountPoint.getIndex("device") < 0) {
                        erMountPoint.addSet("device", sDevice);
                    }

                    eResult erDevice = erMapPerDevice.get(sDevice);

                    if (erDevice == null) {
                        erDevice = neweResult();
                        erDevice.ClusterName = "devices";
                        erDevice.NodeName = transformPaths(sDevice);
                        erDevice.addSet("dummy", "1");

                        erMapPerDevice.put(sDevice, erDevice);
                    }
                } else {
                    erDir.addSet("device", "n/a");
                }
            } else {
                erDir.addSet("mount_point", "n/a");
                erDir.addSet("device", "n/a");
            }

            ret.add(erDir);
        }

        if (mountPoints.size() == 0) {
            throw new Exception("Could not determine any mount point for the defined directories : " + directories);
        }

        if (devices.size() == 0) {
            throw new Exception("Could not find out the devices for these mount points: " + mountPoints);
        }

        final Collection<eResult> all = new ArrayList<eResult>(ret.size());
        all.addAll(erMapPerDevice.values());
        all.addAll(erMapPerDirectory.values());
        all.addAll(erMapPerMountPoint.values());

        long lTotalSize = 0;
        long lTotalUsed = 0;
        long lTotalAvailable = 0;

        try {
            final BufferedReader br = new BufferedReader(new StringReader(Utils.getOutput("df -Pk")));
            br.readLine(); // Filesystem         1024-blocks      Used Available Capacity Mounted on

            String sLine;

            while ((sLine = br.readLine()) != null) {
                // /dev/sda1            151009492  81032776  69976716      54% /
                final StringTokenizer st = new StringTokenizer(sLine);

                final String sDevice = st.nextToken();
                final long lSize = Long.parseLong(st.nextToken());
                final long lUsed = Long.parseLong(st.nextToken());
                final long lAvailable = Long.parseLong(st.nextToken());
                st.nextToken();
                final String sMountPoint = st.nextToken();

                eResult erDevice = erMapPerDevice.get(sDevice);

                if (erDevice != null) {
                    Result r = newResult();
                    r.ClusterName = erDevice.ClusterName;
                    r.NodeName = erDevice.NodeName;
                    r.addSet("size_KB", lSize);
                    r.addSet("used_KB", lUsed);
                    r.addSet("available_KB", lAvailable);

                    lTotalSize += lSize;
                    lTotalUsed += lUsed;
                    lTotalAvailable += lAvailable;

                    erDevice.addSet("mount_point", sMountPoint);

                    ret.add(r);

                    for (final eResult er : all) {
                        final int idx = er.getIndex("device");

                        if (idx < 0) {
                            continue;
                        }

                        if (er.param_name[idx].equals("device") && er.param[idx].equals(sDevice)) {
                            r = newResult();
                            r.ClusterName = er.ClusterName;
                            r.NodeName = er.NodeName;
                            r.addSet("size_KB", lSize);
                            r.addSet("used_KB", lUsed);
                            r.addSet("available_KB", lAvailable);

                            ret.add(r);
                        }
                    }
                }
            }

            if (lTotalSize > 0) {
                final Result r = newResult();

                r.ClusterName = "devices";
                r.NodeName = "_TOTALS_";
                r.addSet("size_KB", lTotalSize);
                r.addSet("used_KB", lTotalUsed);
                r.addSet("available_KB", lTotalAvailable);

                ret.add(r);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        ret.addAll(erMapPerDevice.values());
        ret.addAll(erMapPerMountPoint.values());

        // now let's figure out the name of the cluster

        // first try to figure it out from the environment, in case the system.cf was source-ed
        String sClusterName = System.getenv("SE_NAME");

        if ((sClusterName == null) || (sClusterName.indexOf("::") < 0)) {
            sClusterName = Utils.getOutput("source " + sXrootdInstallationPath
                    + "etc/xrootd/system.cnf && echo \\${SE_NAME} 2>&1");
        }

        if ((sClusterName != null) && (sClusterName.indexOf("::") > 0)) {
            sClusterName = sClusterName.replaceAll("^\\s+|\\s+$", "");

            final Result r = newResult();

            r.ClusterName = "alien_cluster";
            r.NodeName = sClusterName;
            r.addSet("size_KB", lTotalSize);
            r.addSet("used_KB", lTotalUsed);
            r.addSet("available_KB", lTotalAvailable);

            ret.add(r);
        }

        return ret;
    }

    public static final String transformPaths(final String path) {
        return Format.replace(path, "/", "!");
    }

    private eResult neweResult() {
        final eResult ret = new eResult();

        ret.FarmName = getNode().name;
        ret.time = NTPDate.currentTimeMillis();

        return ret;
    }

    private Result newResult() {
        final Result ret = new Result();

        ret.FarmName = getNode().name;
        ret.time = NTPDate.currentTimeMillis();

        return ret;
    }

    @Override
    protected MonModuleInfo initArgs(final String args) {
        final Map<String, String> options = Utils.parseOptions(args);

        if (options == null) {
            logger.log(Level.WARNING, "Please provide the InstallationDir option to the module");

            sXrootdInstallationPath = null;
        } else {
            String sPath = options.get("InstallationDir");

            if (sPath == null) {
                logger.log(Level.WARNING, "Please provide the InstallationDir option to the module");
            } else {
                if (!sPath.endsWith("/")) {
                    sPath += "/";
                }

                final File f = new File(sPath + "etc/xrootd/system.cnf");

                if (f.exists() && f.isFile() && f.canRead()) {
                    logger.log(Level.INFO, "Xrootd installation path '" + sPath + "' seems correct.");

                    sXrootdInstallationPath = sPath;
                } else {
                    logger.log(Level.WARNING, "Xrootd installation path '" + sPath + "' doesn't seem correct.");
                }
            }
        }

        return new MonModuleInfo();
    }

    @Override
    public void update(final Observable arg0, final Object arg1) {
        // nothing yet
    }

    /**
     * Debug method
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        XrootdServerMonitor m = new XrootdServerMonitor();
        m.init(new MNode("pcalice52", "1.2.3.4", null, null), null);
        m.initArgs("InstallationDir=/home/costing/temp/x/x2");

        try {
            Collection<?> c = (Collection<?>) m.doProcess();

            for (Object o : c) {
                System.err.println(o);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
