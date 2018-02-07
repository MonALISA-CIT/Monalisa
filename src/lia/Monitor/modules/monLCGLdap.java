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

import lia.Monitor.monitor.LcgLdapInfo;
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
 * Module that connects to the ldap database of the lcg (ldap://lcg-bdii.gridpp.ac.uk:2170) and retrieves the available information from it
 * @author cipsm
 *
 */
public class monLCGLdap extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -201039927774671460L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monLCGLdap.class.getName());

    /** The name of the module */
    static public String moduleName = "monLCGLdap";

    static public String[] ResTypes = new String[] { "Info", "Status" };

    static public String OsName = "linux";

    //last time this module was called
    private long lastCall;

    protected static final String clusterName = "EGEE_GridMap";

    /** Parameters for the ldap database to which to connect */

    protected String host = "lcg-bdii.gridpp.ac.uk";
    protected String port = "2170";
    protected String rootdn = "o=grid";

    //do not suspend this module
    protected boolean canSuspend = false;

    protected final Hashtable mdsVONames = new Hashtable();

    final Pattern p = Pattern.compile("tier\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    final Pattern mdsp = Pattern.compile("mds-vo-name\\s*=\\s*([\\S&&[^,]]+)", Pattern.CASE_INSENSITIVE);

    final LinkedList lastNodes = new LinkedList();

    public monLCGLdap() {
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
            logger.log(Level.FINEST, "LCGLdap: doProcess called");
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
        mdsVONames.clear();

        try {
            // connect to ldap...
            DirContext ctx = connect(host, port, rootdn);

            getGlueSite(ctx);
            getGlueHost(ctx);

            // in the end always have to disconnect the connection
            disconnect(ctx);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got exception " + t, t);
        }

        if (mdsVONames.size() == 0) {
            // got no results...
            logger.warning("Got no results when accessing ldap " + host + ":" + port + "/" + rootdn);
            return null;
        }

        // the returned vector of eResults...
        final Vector v = new Vector();
        for (Enumeration en = mdsVONames.keys(); en.hasMoreElements();) {
            String mds = (String) en.nextElement();
            LcgLdapInfo info = (LcgLdapInfo) mdsVONames.get(mds);
            if (info == null) {
                continue;
            }

            if (!lastNodes.contains(mds)) {
                lastNodes.addLast(mds);
            }

            //			if (logger.isLoggable(Level.FINER))
            //				logger.log(Level.FINER, info.toString());

            eResult er = new eResult();
            er.time = ls;
            er.ClusterName = getClusterName();
            er.NodeName = mds;
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
            r.NodeName = mds;
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
            if (!mdsVONames.containsKey(m)) {
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
        for (Enumeration en = mdsVONames.keys(); en.hasMoreElements();) {
            lastNodes.addLast(en.nextElement());
        }
        mdsVONames.clear();
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

    private final String getMdsVoName(String name) {

        final Matcher m = mdsp.matcher(name);
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
            results = ctx.search("", "objectClass=GlueSite", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }

                Attributes attributes = searchResult.getAttributes();
                Attribute at;
                // get the name of the site
                // set it as the name used for mdsVONames
                info.name = mds;
                //				Attribute at = attributes.get("GlueSiteName");
                //				if (at != null) {
                //					info.name = at.get().toString();
                //				}  
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
                    info.webURL = getAttributes(at);
                    //a small trick to show the template site made by Rosie on CERN-PROD site
                    //					if ( info.name.equals("CERN-PROD") )
                    //						info.webURL = "http://rosy.web.cern.ch/rosy/GridCast/pageCERN.html";
                }
                // get the description of the site
                at = attributes.get("GlueSiteDescription");
                if (at != null) {
                    info.siteDescription = getAttributes(at);
                }
                at = attributes.get("GlueSiteUserSupportContact");
                if (at != null) {
                    info.siteSupportContact = getAttributes(at);
                }
                at = attributes.get("GlueSiteSysAdminContact");
                if (at != null) {
                    info.sysAdminContact = getAttributes(at);
                }
                at = attributes.get("GlueSiteSecurityContact");
                if (at != null) {
                    info.siteSecurityContact = getAttributes(at);
                }
                at = attributes.get("GlueSiteLocation");
                if (at != null) {
                    info.siteLocation = getAttributes(at);
                }
                at = attributes.get("GlueSiteSponsor");
                if (at != null) {
                    info.siteSponsor = getAttributes(at);
                }
                at = attributes.get("GlueSiteOtherInfo");
                if (at != null) {
                    info.siteOtherInfo = getAttributes(at);
                    if (info.siteOtherInfo != null) {
                        Matcher m = p.matcher(info.siteOtherInfo);
                        if (m.find()) {
                            try {
                                info.tierType = Integer.parseInt(m.group(1));
                            } catch (Exception e) {
                                info.tierType = -1;
                            }
                        } else {
                            info.tierType = -1;
                        }
                    } else {
                        info.tierType = -1;
                    }
                } else {
                    info.tierType = -1;
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

        // based on tiers and the distances between hosts establish a connection pattern...
        for (Enumeration en = mdsVONames.keys(); en.hasMoreElements();) {
            LcgLdapInfo info = (LcgLdapInfo) mdsVONames.get(en.nextElement());
            if ((info == null) || (info.tierType <= 0)) {
                continue;
            }
            // a tier 1, 2 or 3... find to whom to connect the tier....
            int tier = info.tierType;
            double min = Double.MAX_VALUE;
            for (Enumeration en1 = mdsVONames.keys(); en1.hasMoreElements();) {
                LcgLdapInfo in = (LcgLdapInfo) mdsVONames.get(en1.nextElement());
                if ((in == null) || (in.tierType < 0)) {
                    continue;
                }
                /*				if (in.tierType == tier - 1 
                						|| tier>3 && in.tierType==2 ) { // possible match
                */
                if ((in.tierType == 0) && (tier == 1)) {//match only tier 0 -> tier 1 connections
                    double dist = distance(info.latitude, info.longitude, in.latitude, in.longitude);
                    if (dist < min) {
                        min = dist;
                        info.connectedTo = in.name;
                    }
                }
            }
        }
    }

    /**
     * Searches for the attributes related tyo the host runtime env in the ldap database
     * @param ctx
     */
    public void getGlueHost(final DirContext ctx) {

        NamingEnumeration results = null;
        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostApplicationSoftware", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostApplicationSoftwareRunTimeEnvironment");
                if (at != null) {
                    info.applicationSoftwareRunTimeEnvironment = getAttributes(at);
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostArchitecture", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostArchitecturePlatformType");
                if (at != null) {
                    info.architecturePlatformType = getAttributes(at);
                }
                at = attributes.get("GlueHostArchitectureSMPSize");
                if (at != null) {
                    info.architectureSMPSize = getAttributes(at);
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostLocalFileSystem", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostLocalFileSystemName");
                if (at != null) {
                    info.localFileSystemName = getAttributes(at);
                }
                at = attributes.get("GlueHostLocalFileSystemRoot");
                if (at != null) {
                    info.localFileSystemRoot = getAttributes(at);
                }
                at = attributes.get("GlueHostLocalFileSystemSize");
                if (at != null) {
                    try {
                        info.localFileSystemSize = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.localFileSystemSize = 0L;
                    }
                }
                at = attributes.get("GlueHostLocalFileSystemAvailableSpace");
                if (at != null) {
                    try {
                        info.localFileSystemAvailableSpace = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.localFileSystemAvailableSpace = 0L;
                    }
                }
                at = attributes.get("GlueHostLocalFileSystemReadOnly");
                if (at != null) {
                    try {
                        info.localFileSystemReadOnly = Boolean.valueOf(at.get().toString()).booleanValue();
                    } catch (Exception e) {
                        e.printStackTrace();
                        info.localFileSystemReadOnly = false;
                    }
                }
                at = attributes.get("GlueHostLocalFileSystemType");
                if (at != null) {
                    info.localFileSystemType = getAttributes(at);
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostMainMemory", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostMainMemoryRAMSize");
                if (at != null) {
                    try {
                        info.mainMemoryRAMSize = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.mainMemoryRAMSize = 0L;
                    }
                }
                at = attributes.get("GlueHostMainMemoryRAMAvailable");
                if (at != null) {
                    try {
                        info.mainMemoryRAMAvailable = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.mainMemoryRAMAvailable = 0L;
                    }
                }
                at = attributes.get("GlueHostMainMemoryVirtualSize");
                if (at != null) {
                    try {
                        info.mainMemoryVirtualSize = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.mainMemoryVirtualSize = 0L;
                    }
                }
                at = attributes.get("GlueHostMainMemoryVirtualAvailable");
                if (at != null) {
                    try {
                        info.mainMemoryVirtualAvailable = Long.parseLong(at.get().toString());
                    } catch (Exception e) {
                        info.mainMemoryVirtualAvailable = 0L;
                    }
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostNetworkAdapter", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostNetworkAdapterOutboundIP");
                if (at != null) {
                    info.networkAdapterOutboundIP = getAttributes(at);
                }
                at = attributes.get("GlueHostNetworkAdapterInboundIP");
                if (at != null) {
                    info.networkAdapterInboundIP = getAttributes(at);
                }
                at = attributes.get("GlueHostNetworkAdapterName");
                if (at != null) {
                    info.networkAdapterName = getAttributes(at);
                }
                at = attributes.get("GlueHostNetworkAdapterIPAddress");
                if (at != null) {
                    info.networkAdapterIPAddress = getAttributes(at);
                }
                at = attributes.get("GlueHostNetworkAdapterMTU");
                if (at != null) {
                    info.networkAdapterMTU = getAttributes(at);
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostOperatingSystem", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostOperatingSystemName");
                if (at != null) {
                    info.operatingSystemName = getAttributes(at);
                }
                at = attributes.get("GlueHostOperatingSystemRelease");
                if (at != null) {
                    info.operatingSystemRelease = getAttributes(at);
                }
                at = attributes.get("GlueHostOperatingSystemVersion");
                if (at != null) {
                    info.operatingSystemVersion = getAttributes(at);
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostProcessorLoad", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostProcessorLoadLast1Min");
                if (at != null) {
                    try {
                        info.processorLoadLast1Min = Double.parseDouble(at.get().toString());
                    } catch (Exception e) {
                        info.processorLoadLast1Min = 0D;
                    }
                }
                at = attributes.get("GlueHostProcessorLoadLast5Min");
                if (at != null) {
                    try {
                        info.processorLoadLast5Min = Double.parseDouble(at.get().toString());
                    } catch (Exception e) {
                        info.processorLoadLast5Min = 0D;
                    }
                }
                at = attributes.get("GlueHostProcessorLoadLast15Min");
                if (at != null) {
                    try {
                        info.processorLoadLast15Min = Double.parseDouble(at.get().toString());
                    } catch (Exception e) {
                        info.processorLoadLast15Min = 0D;
                    }
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

        try {
            // initialize the controls
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // initialize the search
            results = ctx.search("", "objectClass=GlueHostProcessor", controls);

            // traverse the results...
            while (results.hasMore()) {
                SearchResult searchResult = (SearchResult) results.next();
                final String mds = getMdsVoName(searchResult.getName());
                if (mds == null) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.info("For " + searchResult.getName() + " got unknown mds_vo_name");
                    }
                    continue;
                }
                LcgLdapInfo info = null;
                if (mdsVONames.containsKey(mds)) {
                    info = (LcgLdapInfo) mdsVONames.get(mds);
                } else {
                    info = new LcgLdapInfo();
                    mdsVONames.put(mds, info);
                }
                Attributes attributes = searchResult.getAttributes();
                // get the name of the site
                Attribute at = attributes.get("GlueHostProcessorVendor");
                if (at != null) {
                    info.processorVendor = getAttributes(at);
                }
                at = attributes.get("GlueHostProcessorModel");
                if (at != null) {
                    info.processorModel = getAttributes(at);
                }
                at = attributes.get("GlueHostProcessorVersion");
                if (at != null) {
                    info.processorVersion = getAttributes(at);
                }
                at = attributes.get("GlueHostProcessorClockSpeed");
                if (at != null) {
                    info.processorClockSpeed = getAttributes(at);
                }
                at = attributes.get("GlueHostProcessorInstructionSet");
                if (at != null) {
                    info.processorInstructionSet = getAttributes(at);
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
    }

    private final String getAttributes(final Attribute at) {
        try {
            final StringBuilder buf = new StringBuilder();
            NamingEnumeration en = at.getAll();
            while (en.hasMoreElements()) {
                if (buf.length() != 0) {
                    buf.append(",");
                }
                buf.append(en.nextElement().toString());
            }
            return buf.toString();
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

        monLCGLdap ldap = new monLCGLdap();
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

