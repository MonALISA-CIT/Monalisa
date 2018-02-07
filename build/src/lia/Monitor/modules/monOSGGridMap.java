package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.GridSiteInfo;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.Monitor.monitor.eResult;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

public class monOSGGridMap extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -8461740223624886295L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monOSGGridMap.class.getName());

    /** The name of the module */
    static public String moduleName = "monOSGGridMap";

    static public String[] ResTypes = new String[] { "Info", "Status" };

    static public String OsName = "linux";

    //last time this module was called
    private long lastCall;

    //do not suspend this module
    protected boolean canSuspend = false;

    protected static final String clusterName = "OSG_GridMap";

    protected String host = "http://vors.grid.iu.edu/OSG_map_info.txt";

    protected String hostInfo = "http://vors.grid.iu.edu/cgi-bin/tindex.cgi?res=";

    protected final Hashtable sites = new Hashtable();

    final LinkedList lastNodes = new LinkedList();

    public monOSGGridMap() {
        super(moduleName);
        isRepetitive = true;
        info.name = moduleName;
        info.ResTypes = ResTypes;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.FINEST,
                "monOSGGridMap: farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                        + " nodeName=" + Node.getName());

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                String argTemp = arg2.trim();
                if (argTemp.startsWith("OSGHostInfo")) {
                    hostInfo = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, "monOSGGridMap: hostInfo = " + hostInfo);
                } else if (argTemp.startsWith("OSGHost")) {
                    host = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, "monOSGGridMap: host = " + host);
                }
            }
        }
        lastCall = NTPDate.currentTimeMillis();
        return info;
    }

    @Override
    public String[] ResTypes() {
        return info.ResTypes;
    }

    @Override
    public String getOsName() {
        return OsName;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "OSGGridMap: doProcess called");
        }
        // can't run this module, init failed
        if (info.getState() != 0) {
            throw new Exception("There was some exception during init ...");
        }

        long ls = NTPDate.currentTimeMillis();
        if (ls <= lastCall) {
            return null;
        }

        lastCall = ls;
        sites.clear();

        try {
            checkSite();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception " + t, t);
        }

        for (Enumeration en = sites.elements(); en.hasMoreElements();) {
            System.out.println("site: " + en.nextElement());
        }

        if (sites.size() == 0) {
            // got no results...
            logger.warning("Got no results when accessing " + host);
            return null;
        }

        // the returned vector of eResults...
        final Vector v = new Vector();
        for (Enumeration en = sites.keys(); en.hasMoreElements();) {
            String siteID = (String) en.nextElement();
            GridSiteInfo info = (GridSiteInfo) sites.get(siteID);
            if (info == null) {
                continue;
            }

            if (!lastNodes.contains(siteID)) {
                lastNodes.addLast(siteID);
            }

            eResult er = new eResult();
            er.time = ls;
            er.ClusterName = getClusterName();
            er.NodeName = siteID;
            er.FarmName = getFarmName();
            er.Module = moduleName;
            try {
                byte[] buff = Utils.writeObject(info);
                er.addSet("Info", buff);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize lcg_ldap", t);
            }
            v.add(er);

            Result r = new Result();
            r.time = ls;
            r.ClusterName = getClusterName();
            r.NodeName = siteID;
            r.FarmName = getFarmName();
            r.Module = moduleName;
            if ((info.name == null) || (info.name.length() == 0) || info.name.equals("N/A")) {
                r.addSet("Status", 0D);
            } else if ((info.webURL == null) || (info.webURL.length() == 0) || info.webURL.equals("N/A")) {
                r.addSet("Status", 0D);
            } else {
                r.addSet("Status", 1D);
            }
            v.add(r);
        }

        // also check for nodes to be removed...
        for (Iterator it = lastNodes.iterator(); it.hasNext();) {
            String m = (String) it.next();
            if (!sites.containsKey(m)) {
                eResult er = new eResult();
                er.time = ls;
                er.ClusterName = getClusterName();
                er.NodeName = m;
                er.FarmName = getFarmName();
                er.Module = moduleName;
                er.param = null;
                er.param_name = null;
                v.add(er);
            }
        }
        lastNodes.clear();
        for (Enumeration en = sites.keys(); en.hasMoreElements();) {
            lastNodes.addLast(en.nextElement());
        }
        sites.clear();
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " Returning [ " + ((v == null) ? 0 : v.size()) + " ] results");
        }
        return v;
    }

    private void checkSite() {

        final HashMap order = new HashMap();

        try {
            final URL url = new URL(host);
            final URLConnection conn = url.openConnection();
            final BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String line = in.readLine();
            if (line == null) {
                return;
            }
            if (line.startsWith("#")) {
                line = line.substring(1);
            }
            String split[] = line.split(",");
            if (split != null) {
                for (int i = 0; i < split.length; i++) {
                    order.put(Integer.valueOf(i), split[i].trim());
                }
            }

            while ((line = in.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                split = line.split(",");
                if (split != null) {
                    String name = null;
                    for (int i = 0; i < split.length; i++) {
                        final String key = (String) order.get(Integer.valueOf(i));
                        if (key == null) {
                            continue;
                        }
                        if (key.equals("name")) {
                            name = split[i].trim();
                        }
                    }
                    if (name == null) {
                        continue; // advance to the next line..
                    }

                    GridSiteInfo info = null;
                    if (sites.containsKey(name)) {
                        info = (GridSiteInfo) sites.get(name);
                    } else {
                        info = new GridSiteInfo();
                        sites.put(name, info);
                    }
                    info.name = name;
                    info.niceName = name;
                    for (int i = 0; i < split.length; i++) {
                        final String key = (String) order.get(Integer.valueOf(i));
                        if (key == null) {
                            continue;
                        }
                        if (key.equals("host")) {
                            String ss = split[i].trim();
                            if (ss.indexOf('.') >= 0) {
                                ss = ss.substring(ss.indexOf('.') + 1);
                            }
                            ss = "www." + ss;
                            info.webURL = ss;
                            continue;
                        }
                        if (key.equals("longitude")) {
                            String ss = split[i].trim();
                            try {
                                info.longitude = Double.parseDouble(ss);
                            } catch (Exception e) {
                            }
                            continue;
                        }
                        if (key.equals("latitude")) {
                            String ss = split[i].trim();
                            try {
                                info.latitude = Double.parseDouble(ss);
                            } catch (Exception e) {
                            }
                            continue;
                        }
                    }
                }
            }

            in.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {

        monOSGGridMap osg = new monOSGGridMap();

        osg.init(new MNode("test", "127.0.0.1", new MCluster("CMap", null), null), "");
        try {
            for (int k = 0; k < 2; k++) {
                osg.doProcess();
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" failed to process !!!");
        }
    }

} // end of class monOSGGridMap

