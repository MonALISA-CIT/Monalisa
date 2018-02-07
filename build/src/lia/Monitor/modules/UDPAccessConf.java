/*
 * $Id: UDPAccessConf.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DateFileWatchdog;
import lia.util.net.NetMatcher;

/**
 * 
 * @author ramiro
 */
public class UDPAccessConf implements Observer {

    private static final Logger logger = Logger.getLogger(UDPAccessConf.class.getName());

    private final static class IPAclEntry {

        private final NetMatcher ipMatch;
        private final boolean policy;

        IPAclEntry(final NetMatcher ipMatch, final boolean policy) {
            if (ipMatch == null) {
                throw new NullPointerException(" [ UDPAccessConf ] IPAclEntry: The NetMatcher param cannot be null");
            }
            this.ipMatch = ipMatch;
            this.policy = policy;
        }

        @Override
        public String toString() {
            return " IPAclEntry :- NetMatcher: " + ipMatch + " policy: " + policy;
        }
    }

    private final static class RegExAclEntry {

        private final Pattern pattern;
        private final boolean policy;

        RegExAclEntry(final Pattern pattern, final boolean policy) {
            if (pattern == null) {
                throw new NullPointerException(" [ UDPAccessConf ] RegExAclEntry: The Pattern cannot be null");
            }

            this.pattern = pattern;
            this.policy = policy;
        }

        @Override
        public String toString() {
            return " RegExAcl :- pattern: " + pattern + " policy: " + policy;
        }
    }

    /**
     * the conf file which this object encapsulates
     */
    private final File accessConfFile;
    /**
     * guards read access to this object
     */
    private final Lock confReadLock;
    /**
     * guards write access to this object
     */
    private final Lock confWriteLock;
    /**
     * holds a list of IPAclEntry in the same order as defined in the config file
     */
    private final List ipListAcl = new ArrayList();
    /**
     * default action if none of the IPs defined in the list match ...
     */
    private boolean defaultIPAclPolicy = true;
    private final List clustersListAcl = new ArrayList();
    private boolean defaultClustersPolicy = true;
    private final List nodesListAcl = new ArrayList();
    private boolean defaultNodesPolicy = true;
    private String password;
    private boolean isPasswdDefined = false;

    public UDPAccessConf(final File accessConfFile) throws Exception {
        if (accessConfFile == null) {
            throw new NullPointerException("UDPAccessConf null accessConfFile ....");
        }

        final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        confReadLock = rwLock.readLock();
        confWriteLock = rwLock.writeLock();

        this.accessConfFile = accessConfFile;

        if (!accessConfFile.exists()) {
            throw new IOException("[ UDPAccessConf ] The AccessConfFile [ " + accessConfFile + " ] does not exist!");
        }

        if (!accessConfFile.isFile()) {
            throw new IOException("[ UDPAccessConf ] The AccessConfFile [ " + accessConfFile + " ] is not a File!");
        }

        if (!accessConfFile.canRead()) {
            throw new IOException("[ UDPAccessConf ] The AccessConfFile [ " + accessConfFile + " ] cannot be read!");
        }

        try {
            DateFileWatchdog dfw = DateFileWatchdog.getInstance(accessConfFile, 5 * 1000);
            dfw.addObserver(this);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ UDPAccessConf ] Cannot instantiate DateFileWatchdog for " + accessConfFile
                    + ". The file cannot be monitored for changes ....", t);
        }

        reloadCfg();
    }

    public synchronized String getPassword() {
        confReadLock.lock();
        try {
            return password;
        } finally {
            confReadLock.unlock();
        }
    }

    public void setPassword(final String password) {
        confWriteLock.lock();
        try {
            this.password = password;
            if ((this.password == null) || (this.password.length() == 0)) {
                isPasswdDefined = false;
            } else {
                isPasswdDefined = true;
            }
        } finally {
            confWriteLock.unlock();
        }
    }

    public boolean checkPassword(final String passToCheck) {
        confReadLock.lock();
        try {

            if (!isPasswdDefined) {
                return true;
            }
            if ((passToCheck == null) || (passToCheck.length() == 0)) {
                return false;
            }

            return password.equals(passToCheck);
        } finally {
            confReadLock.unlock();
        }
    }

    public boolean isPasswordDefined() {
        confReadLock.lock();
        try {
            return isPasswdDefined;
        } finally {
            confReadLock.unlock();
        }
    }

    public boolean checkResult(final Result r) {
        return (checkClusterName(r.ClusterName) && checkNodeName(r.NodeName));
    }

    public boolean checkResult(final eResult er) {
        return (checkClusterName(er.ClusterName) && checkNodeName(er.NodeName));
    }

    public boolean checkClusterName(final String clusterName) {
        if (clusterName == null) {
            throw new NullPointerException("clusterName cannot be null");
        }

        confReadLock.lock();

        try {
            return checkRegExParam(clusterName, clustersListAcl, defaultClustersPolicy);
        } finally {
            confReadLock.unlock();
        }
    }

    public boolean checkNodeName(final String nodeName) {
        if (nodeName == null) {
            throw new NullPointerException("nodeName cannot be null");
        }

        confReadLock.lock();

        try {
            return checkRegExParam(nodeName, nodesListAcl, defaultNodesPolicy);
        } finally {
            confReadLock.unlock();
        }
    }

    private static final boolean checkRegExParam(final String param, final List regExAclList,
            final boolean defaultPolicy) {

        if ((regExAclList == null) || (regExAclList.size() == 0)) {
            return defaultPolicy;
        }

        for (Iterator it = regExAclList.iterator(); it.hasNext();) {
            RegExAclEntry entry = (RegExAclEntry) it.next();

            if (entry.pattern.matcher(param).matches()) {
                return entry.policy;
            }
        }

        return defaultPolicy;
    }

    public boolean checkIP(final InetAddress ip) {
        confReadLock.lock();
        try {

            //nothing defined
            if (ipListAcl.size() == 0) {
                return true;
            }

            for (final Iterator ipIt = ipListAcl.iterator(); ipIt.hasNext();) {
                IPAclEntry entry = (IPAclEntry) ipIt.next();
                if (entry.ipMatch.matchInetNetwork(ip)) {
                    return entry.policy;
                }
            }

        } finally {
            confReadLock.unlock();
        }

        return defaultIPAclPolicy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("\n\nPassword: ").append(password);
        int i = 0;
        for (Iterator ipIt = ipListAcl.iterator(); ipIt.hasNext();) {
            IPAclEntry entry = (IPAclEntry) ipIt.next();
            sb.append("\n[").append(i).append("] = ").append(entry.ipMatch.toString()).append(" [")
                    .append(entry.policy).append("]");
            i++;
        }
        sb.append("\n\n");
        return sb.toString();
    }

    protected void reloadCfg() {
        logger.log(Level.INFO, " [ UDPAccessConf ] : (RE)Loading conf ... [ " + accessConfFile + " ] ");

        final long sTime = System.currentTimeMillis();

        BufferedReader br = null;
        FileReader fr = null;

        String newPasswd = null;

        final ArrayList newIPAcl = new ArrayList();
        boolean newDefaultIPPolicy = true;
        final ArrayList newClustersAcl = new ArrayList();
        boolean newDefaultClustersPolicy = true;
        final ArrayList newNodesAcl = new ArrayList();
        boolean newDefaultNodesPolicy = true;

        try {
            fr = new FileReader(accessConfFile);
            br = new BufferedReader(fr);
            String line = null;
            while ((line = br.readLine()) != null) {
                try {
                    line = line.trim();

                    ///////////////
                    //ignore comments ( line that starts with # )
                    //////////////
                    if ((line.length() == 0) || line.startsWith("#")) {
                        continue;
                    }

                    ///////////////
                    //check for password
                    //////////////
                    if (line.toLowerCase().indexOf("password") != -1) {
                        String[] tokens = line.split("(\\s)*=(\\s)*");
                        if ((tokens == null) || (tokens.length != 2)) {
                            logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Ignoring line: " + line);
                            continue;
                        }
                        if ((tokens[1] == null) || (tokens[1].trim().length() == 0)) {
                            continue;
                        }
                        newPasswd = tokens[1].trim();
                        continue;
                    }

                    /////////////////
                    // default IP policy
                    /////////////////
                    if (line.indexOf("default_ip_policy") >= 0) {
                        newDefaultIPPolicy = parseKeyBooleanValue(line, "default_ip_policy", true);
                        continue;
                    }

                    /////////////////
                    // default cluster policy
                    /////////////////
                    if (line.toLowerCase().indexOf("default_clusters_policy") != -1) {
                        newDefaultClustersPolicy = parseKeyBooleanValue(line, "default_clusters_policy", true);
                        continue;
                    }

                    /////////////////
                    // default nodes policy
                    /////////////////
                    if (line.toLowerCase().indexOf("default_nodes_policy") != -1) {
                        newDefaultNodesPolicy = parseKeyBooleanValue(line, "default_nodes_policy", true);
                        continue;
                    }

                    ////////////////////////////
                    //cluster/node line regex
                    ////////////////////////////
                    final boolean allowCN = line.toLowerCase().startsWith("allow");
                    final boolean denyCN = (allowCN) ? false : line.toLowerCase().startsWith("deny");

                    if (allowCN || denyCN) {

                        int i = line.toLowerCase().indexOf("clusternames");

                        if (i >= 0) {
                            line = line.substring(i);
                            parseClusterNodeAclLine(line, newClustersAcl, allowCN);
                            continue;
                        }

                        i = line.toLowerCase().indexOf("nodenames");
                        if (i >= 0) {
                            parseClusterNodeAclLine(line, newNodesAcl, allowCN);
                            continue;
                        }

                        continue;
                    }

                    ///////////////////////
                    //ip line
                    ///////////////////////
                    String[] tokens = line.split("(\\s)+");
                    if ((tokens == null) || (tokens.length != 2)) {
                        logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Ignoring line: " + line);
                        continue;
                    }

                    if ((tokens[0] == null) || (tokens[1] == null) || (tokens[0].trim().length() == 0)
                            || (tokens[1].trim().length() == 0)) {
                        logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Cannot process line [ " + line
                                + " ]. Ignoring...");
                        continue;
                    }

                    boolean policy = false;
                    try {
                        String sPolicy = tokens[1].trim();
                        if (sPolicy.toLowerCase().indexOf("allow") != -1) {
                            policy = true;
                        } else if (sPolicy.toLowerCase().indexOf("deny") != -1) {
                            policy = false;
                        } else {
                            logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Cannot process line [ " + line
                                    + " ]. Ignoring...Please specify a policy [ allow | deny ]");
                            continue;
                        }
                    } catch (Throwable t) {
                        logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Cannot process line [ " + line
                                + " ]. Ignoring...", t);
                        continue;
                    }

                    newIPAcl.add(new IPAclEntry(new NetMatcher(new String[] { tokens[0].trim() }), policy));

                } catch (Throwable t1) {
                    logger.log(Level.WARNING, " [ UDPAccessConf ]  Got Exeption while parsing entry [ " + line
                            + " ] ... ignoring it");
                }
            }//while

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception while (Re)loading config");
        } finally {
            try {
                fr.close();
                fr = null;
            } catch (Throwable ignore) {
            }

            try {
                br.close();
                br = null;
            } catch (Throwable ignore) {
            }
        }

        confWriteLock.lock();
        try {

            //////
            // new Passwd
            //////////
            setPassword(newPasswd);

            ///////////
            // new IP acl
            //////////
            ipListAcl.clear();
            ipListAcl.addAll(newIPAcl);
            defaultIPAclPolicy = newDefaultIPPolicy;

            ////////////
            //new Cluster params
            ///////////
            clustersListAcl.clear();
            clustersListAcl.addAll(newClustersAcl);
            defaultClustersPolicy = newDefaultClustersPolicy;

            ////////////////
            //new nodes params
            ///////////////
            nodesListAcl.clear();
            nodesListAcl.addAll(newNodesAcl);
            defaultNodesPolicy = newDefaultNodesPolicy;

        } finally {
            confWriteLock.unlock();
        }

        if (logger.isLoggable(Level.FINEST)) {
            confReadLock.lock();
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("\n\n [ UDPAccessConf ] reload conf: ");
                sb.append("\n Password: ").append(password).append(" isPasswdEnabled: ").append(isPasswdDefined);
                sb.append("\n IpListAcl: ").append(ipListAcl).append(" defaultIPPolicy: ").append(defaultIPAclPolicy);
                sb.append("\n ClustersAcl: ").append(clustersListAcl).append(" defaultClustersPolicy: ")
                        .append(defaultClustersPolicy);
                sb.append("\n NodesAcl: ").append(nodesListAcl).append(" defaultNodesPolicy: ")
                        .append(defaultNodesPolicy).append("\n\n");
                logger.log(Level.FINEST, sb.toString());
            } finally {
                confReadLock.unlock();
            }
        }

        logger.log(Level.INFO,
                " [ UDPAccessConf ] : Finished (RE)Loading conf. It took: " + (System.currentTimeMillis() - sTime)
                        + " ms");
    }

    @Override
    public void update(Observable o, Object arg) {
        reloadCfg();
    }

    private static final boolean parseKeyBooleanValue(final String line, final String key, final boolean defaultValue) {
        boolean ret = defaultValue;
        try {
            if (line.toLowerCase().indexOf("default_ip_policy") != -1) {
                String[] tokens = line.split("(\\s)*=(\\s)*");
                if ((tokens == null) || (tokens.length != 2)) {
                    logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Ignoring line: " + line);
                } else if ((tokens[1] == null) || (tokens[1].trim().length() == 0)) {
                    logger.log(Level.WARNING, " [ UDPAccessConf ] reloadCfg: Ignoring line: " + line);
                } else {
                    char c = tokens[1].charAt(0);

                    if ((c == '1') || (c == 't') || (c == 'a') || (c == 'A') || (c == 'T')) {
                        ret = true;
                    } else {
                        ret = defaultValue;
                    }
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ UDPAccessConf ] [ parseKeyBooleanValue ]: Ignoring line  [" + line
                    + "] got exception: ", t);
        } finally {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.WARNING, " [ UDPAccessConf ] [ parseKeyBooleanValue ]: for line [ " + line
                        + " ] returning: " + ret);
            }
        }
        return ret;
    }

    private static final void parseClusterNodeAclLine(final String line, final List aclList, final boolean policy) {
        try {
            int eqIdx = line.indexOf("=");
            if (eqIdx >= 0) {
                String tmpline = line.substring(eqIdx + 1);
                String[] tkI = line.split("(\\s)*,(\\s)*");
                final int len = tkI.length;
                for (int i = 0; i < len; i++) {
                    aclList.add(new RegExAclEntry(Pattern.compile(tkI[i]), policy));
                }

            } else {
                logger.log(Level.WARNING, " [ UDPAccessConf ] [ parseClusterNodeAclLine ]: Ignoring line: " + line);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ UDPAccessConf ] [ parseClusterNodeAclLine ]: Ignoring line  [" + line
                    + "]    got exception: ", t);
        }
    }
}
