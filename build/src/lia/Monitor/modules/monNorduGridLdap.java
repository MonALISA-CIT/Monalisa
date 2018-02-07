package lia.Monitor.modules;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

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

/**
 * Module that connects to the ldap database of the NorduGrid (ldap://gin-bdii.cern.ch:2170) and retrieves the available information from it
 * <br>
 * Query to be run: ldapsearch -x -h gin-bdii.cern.ch -p 2170 -b o=grid GlueSiteOtherInfo=Middleware=NDGF
 * @author mluc
 *
 */
public class monNorduGridLdap extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = 1033175251007788308L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monNorduGridLdap.class.getName());

    /** The name of the module */
    static public String moduleName = "monNorduGridLdap";

    static public String[] ResTypes = new String[] { "Info", "Status" };

    static public String OsName = "linux";

    //last time this module was called
    private long lastCall;

    protected static final String clusterName = "NDGF_GridMap";

    /** Parameters for the ldap database to which to connect */

    protected String host = "gin-bdii.cern.ch";
    protected String port = "2170";
    protected String rootdn = "o=grid";

    //do not suspend this module
    protected boolean canSuspend = false;

    protected final Hashtable sites = new Hashtable();

    final Pattern p = Pattern.compile("tier\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** pattern to detect site unique ID */
    final Pattern patSiteID = Pattern.compile("GlueSiteUniqueID=([\\S&&[^,]]+)", Pattern.CASE_INSENSITIVE);

    final LinkedList lastNodes = new LinkedList();

    public monNorduGridLdap() {
        super(moduleName);
        isRepetitive = true;
        info.name = moduleName;
        info.ResTypes = ResTypes;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        logger.log(Level.FINEST,
                moduleName + ": farmName=" + Node.getFarmName() + " clusterName= " + Node.getClusterName()
                        + " nodeName=" + Node.getName());

        String[] args = arg.split("(\\s)*;(\\s)*");
        if (args != null) {
            for (String arg2 : args) {
                String argTemp = arg2.trim();
                if (argTemp.startsWith("LdapHost")) {
                    host = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, moduleName + ": host = " + host);
                } else if (argTemp.startsWith("LdapPort")) {
                    port = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, moduleName + ": port = " + port);
                } else if (argTemp.startsWith("LdapRootDn")) {
                    rootdn = argTemp.split("(\\s)*=(\\s)*")[1].trim();
                    logger.log(Level.INFO, moduleName + ": root_dn = " + rootdn);
                }
            }
        }
        lastCall = NTPDate.currentTimeMillis();
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "NDGFLdap: doProcess called");
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
            // connect to ldap...
            DirContext ctx = connect(host, port, rootdn);

            getGlueSite(ctx);

            // in the end always have to disconnect the connection
            disconnect(ctx);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception " + t, t);
        }

        if (sites.size() == 0) {
            // got no results...
            logger.warning("Got no results when accessing ldap " + host + ":" + port + "/" + rootdn);
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

            //			if (logger.isLoggable(Level.FINER))
            //				logger.log(Level.FINER, info.toString());

            eResult er = new eResult();
            er.time = ls;
            er.ClusterName = getClusterName();
            er.NodeName = info.name;
            er.FarmName = getFarmName();
            er.Module = moduleName;
            try {
                byte[] buff = Utils.writeObject(info);
                er.addSet("Info", buff);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot serialize nordugrid_ldap", t);
            }
            v.add(er);

            Result r = new Result();
            r.time = ls;
            r.ClusterName = getClusterName();
            r.NodeName = info.name;
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

    /**
     * connect to server
     *
     * @param host
     * Description of Parameter
     * @param username
     * Description of Parameter
     * @param password
     * Description of Parameter
     * @exception NamingException
     * Description of Exception
     */
    public DirContext connect(String host, String port, String rootdn) throws NamingException {

        logger.info("LCGLdap: Connecting to ldap://" + host + ":" + port + "/" + rootdn);
        DirContext dirContext;
        final Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port + "/" + rootdn);
        if (logger.isLoggable(Level.FINEST)) {
            logger.info("prov_url= " + env.get(Context.PROVIDER_URL));
        }
        dirContext = new InitialDirContext(env);
        return dirContext;
    }

    /**
     * disconnect from the server
     */
    public void disconnect(DirContext dirContext) {
        if (dirContext == null) {
            logger.info("Cannot disconnect null context");
            return;
        }
        try {
            dirContext.close();
        } catch (NamingException e) {
            logger.log(Level.WARNING, "Ldap client disconnect - ", e);
        }
    }

    private final String getSiteID(String name) {

        final Matcher m = patSiteID.matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public void getGlueSite(final DirContext ctx) {

        NamingEnumeration results = null;
        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "(&(GlueSiteOtherInfo=Middleware=NDGF)(objectClass=GlueSite))", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                //				System.out.println("result: "+searchResult.getName()+" attributes: "+searchResult.getAttributes());
                final String siteID = getSiteID(searchResult.getName());
                if (siteID == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown site ID");
                    }
                    continue;
                }
                if (siteID.equals("-1")) {
                    continue; //a strange site... should ignore it?
                }
                GridSiteInfo info = null;
                if (sites.containsKey(siteID)) {
                    info = (GridSiteInfo) sites.get(siteID);
                } else {
                    info = new GridSiteInfo();
                    sites.put(siteID, info);
                }

                Attributes attributes = searchResult.getAttributes();
                Attribute at;
                // get the unique name of the site
                at = attributes.get("GlueSiteUniqueID");
                if (at != null) {
                    info.name = at.get().toString();
                }
                // get the name of the site
                at = attributes.get("GlueSiteName");
                if (at != null) {
                    info.niceName = at.get().toString();
                }
                // get the latitude
                at = attributes.get("GlueSiteLatitude");
                if (at != null) {
                    try {
                        info.latitude = Double.parseDouble(at.get().toString());
                    } catch (Exception e) {
                        info.latitude = 0D;
                    }
                }
                // get the longitude
                at = attributes.get("GlueSiteLongitude");
                if (at != null) {
                    try {
                        info.longitude = Double.parseDouble(at.get().toString());
                    } catch (Exception e) {
                        info.longitude = 0D;
                    }
                }
                // get the web site
                at = attributes.get("GlueSiteWeb");
                if (at != null) {
                    info.webURL = getBestAttribute(at);
                }
                // get the description of the site
                at = attributes.get("GlueSiteDescription");
                if (at != null) {
                    info.siteDescription = getBestAttribute(at);
                }
                at = attributes.get("GlueSiteUserSupportContact");
                if (at != null) {
                    info.siteSupportContact = getBestAttribute(at);
                }
                at = attributes.get("GlueSiteLocation");
                if (at != null) {
                    info.siteLocation = getBestAttribute(at);
                }
                at = attributes.get("GlueSiteSponsor");
                if (at != null) {
                    info.siteSponsor = getBestAttribute(at);
                }
            }
        } catch (NameNotFoundException e) {
            // The base context was not found.
            // Just clean up and exit.
        } catch (NamingException e) {
            throw new RuntimeException(e);
        } finally {
            if (results != null) {
                try {
                    results.close();
                } catch (Exception e) {
                    // Never mind this.
                }
            }
        }

        //TODO: establish a connection pattern
        // based on tiers and the distances between hosts establish a connection pattern...
        /*		for (Enumeration en = sites.keys(); en.hasMoreElements(); ) {
        			GridSiteInfo info = (GridSiteInfo)sites.get(en.nextElement());
        			if (info == null ) continue;
        			int tier = info.tierType;
        			double min = Double.MAX_VALUE;
        			for (Enumeration en1 = sites.keys(); en1.hasMoreElements(); ) {
        				LcgLdapInfo in = (LcgLdapInfo)sites.get(en1.nextElement());
        				if (in == null || in.tierType < 0) continue;
        				if (in.tierType == tier - 1) { // possible match
        					double dist = distance(info.latitude, info.longitude, in.latitude, in.longitude);
        					if (dist < min) {
        						min = dist;
        						info.connectedTo = in.name;
        					}
        				}
        			}
        		}
        */
    }

    /**
     * should select the best value from the set of values, or concatenate all of them...
     * for the moment select the last one
     * TODO: improve the selection of the best value for an attribute
     *
     * @author mluc
     * @since Oct 20, 2006
     * @param at
     * @return a string value of the attribute (that may be the sum of all available values for the parameter)
     */
    private final String getBestAttribute(final Attribute at) {
        try {
            return at.get(at.size() - 1).toString();
        } catch (Exception e) {
        }
        return null;
    }

    private final double DegToRad(double deg) {
        return ((deg / 180.0) * Math.PI);
    }

    final double EarthRadius = 6378137.0D;

    private final double distance(double lat1, double lon1, double lat2, double lon2) {
        /* This algorithm is called Sinnott's Formula */
        double dlon = DegToRad(lon2) - DegToRad(lon1);
        double dlat = DegToRad(lat2) - DegToRad(lat1);
        double a = Math.pow(Math.sin(dlat / 2), 2.0)
                + (Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2.0));
        double c = 2 * Math.asin(Math.sqrt(a));
        double dist = EarthRadius * c;
        lon1 = DegToRad(lon1);
        lon2 = DegToRad(lon2);
        lat1 = DegToRad(lat1);
        lat2 = DegToRad(lat2);
        dist = EarthRadius
                * (Math.acos((Math.sin(lat1) * Math.sin(lat2))
                        + (Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2))));
        return dist;
    }

    public static void main(String args[]) {

        monNorduGridLdap ldap = new monNorduGridLdap();
        //		String s = "GlueClusterUniqueID=grid.uibk.ac.at,Mds-Vo-name=HEPHY-UIBK";
        //		String s = "GlueSubClusterUniqueID=grid.uibk.ac.at,GlueClusterUniqueID=grid.uibk.ac.at,Mds-Vo-name=HEPHY-UIBK,mds-vo-name=local";

        //		Matcher m = ldap.mdsp.matcher(s);
        //		if (m.find())
        //			System.out.println(m.group(1));
        //		else
        //			System.out.println("none");
        //		
        //		if (true)
        //			return;

        ldap.init(new MNode("test", "127.0.0.1", new MCluster("CMap", null), null), "");
        try {
            for (int k = 0; k < 2; k++) {
                ldap.doProcess();
                System.out.println("-------- sleeeping ----------");
                Thread.sleep(5000);
                System.out.println("-------- doProcess-ing --------- k=" + k);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" failed to process !!!");
        }
    }

} // end of class monLCGLdap

