package lia.util.topology;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;
import lia.util.ntp.NTPDate;

public final class IpClassifier extends Thread {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(IpClassifier.class.getName());

    private final static String rootPath = AppConfig.getProperty("lia.util.topology.path", "/home/catac/temp/topo");

    // we use this ping executable to determine the distance (TTL) to an IP
    private final static String pingPath = rootPath + "/bin/ping";
    // we use ally to check if 2 IPs are on the same device (this must be suid)
    private final static String allyPath = rootPath + "/bin/ally";
    // 
    private final static String dicPath = rootPath + "/dictionary";
    private final static String ipClassPath = rootPath + "/ipClasses";
    private final static String staticIPidPath = rootPath + "/staticIPids";

    private final static long ipClassRefreshDelta = 2 * 24 * 60 * 60 * 1000;
    private long lastIpClassRefreshTime = 0;

    /** the single instance of this class */
    private static final IpClassifier ipClassifier = new IpClassifier();

    /** new and unclassified IPs */
    private final Vector newIPs;

    /** classes of IPs, by TTL */
    private final Hashtable ipClasses;

    /** dictionary with all sets of IPs on the same machine */
    private final Vector ipIDic;

    // DELETE:
    Vector oldIpIDic;
    Hashtable modifications;

    /** cannot construct this. use getInstance instead */
    private IpClassifier() {
        newIPs = new Vector();
        ipClasses = new Hashtable();
        ipIDic = new Vector();
        oldIpIDic = new Vector();
        modifications = new Hashtable();
        loadIPclasses();
        loadDictionary();
        loadStaticIPids();
        updateModifications(new HashSet(oldIpIDic));
        start();
    }

    /** get a reference to the instance */
    public static IpClassifier getInstance() {
        return ipClassifier;
    }

    /** add an IP if it is not already in ipClasses
     * this is called from the AddTrace servlet */
    public void addIP(String ip) {
        boolean found = false;
        for (Iterator clsIt = ipClasses.values().iterator(); clsIt.hasNext();) {
            Vector cls = (Vector) clsIt.next();
            if (cls.contains(ip)) {
                found = true;
                break;
            }
        }
        if (!found && !newIPs.contains(ip)) {
            newIPs.add(ip);
        }
    }

    /** get a line with IPs in the same group with the given ip */
    private String getIPids(String ip) {
        Vector ips = searchOldIpIDic(ip);
        if (ips == null) {
            return ip + "\n";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ips.size(); i++) {
                ip = (String) ips.get(i);
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(ip);
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /** generate a response for the client, based on the query:
     * if time (first item in ipList) is = 0, return a line for each
     * of the other IPs with ips on the same machine.
     * if time > 0, build an answer by concatenating all modifications
     * for which modif_time > time
     */
    public String getIPidsResponse(String ipList) {
        StringTokenizer stk = new StringTokenizer(ipList, " ");
        StringBuilder resp = new StringBuilder();
        Long time = Long.valueOf(stk.nextToken());

        if (time.longValue() == 0) {
            while (stk.hasMoreTokens()) {
                String ip = stk.nextToken();
                resp.append(getIPids(ip));
            }
        } else {
            Vector keys = new Vector(modifications.keySet());
            Collections.sort(keys);
            for (Enumeration enm = keys.elements(); enm.hasMoreElements();) {
                Long mTime = (Long) enm.nextElement();
                if (time.compareTo(mTime) < 0) {
                    resp.append(modifications.get(mTime));
                }
            }
        }
        // find the last modification time
        time = Long.valueOf(0);
        for (Enumeration enm = modifications.keys(); enm.hasMoreElements();) {
            Long mTime = (Long) enm.nextElement();
            if (time.compareTo(mTime) < 0) {
                time = mTime;
            }
        }
        return time.toString() + "\n" + resp.toString();
    }

    /** put in modificatins hash a pair with current time as key and
     * a string with all modified IPs 
     */
    private void updateModifications(HashSet modifiedGroups) {
        Long time = Long.valueOf(NTPDate.currentTimeMillis());
        StringBuilder txt = new StringBuilder();
        for (Iterator lit = modifiedGroups.iterator(); lit.hasNext();) {
            Vector ips = (Vector) lit.next();
            for (int i = 0; i < ips.size(); i++) {
                String ip = (String) ips.get(i);
                if (i > 0) {
                    txt.append(" ");
                }
                txt.append(ip);
            }
            txt.append("\n");
        }
        modifications.put(time, txt);
    }

    /** run a process */
    static BufferedReader runCommand(String cmd) {
        Process pro = null;
        InputStream out = null;
        try {
            pro = MLProcess.exec(cmd);
            out = pro.getInputStream();
            InputStreamReader in = new InputStreamReader(out);
            StringBuilder answerBuff = new StringBuilder(1024);
            int c = in.read();
            int nb = 0;
            while (c > -1) {
                nb++;
                //filter non-printable and non-ASCII
                if (((c >= 32) && (c < 127)) || (c == '\t') || (c == '\r') || (c == '\n')) {
                    answerBuff.append((char) c);
                }
                c = in.read();
            }
            if (pro != null) {
                pro.destroy();
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable t) {
            }
            pro = null;
            out = null;

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "For cmd " + cmd + " filtered result [" + nb + "]:\n." + answerBuff.toString()
                        + ".");
            }

            return new BufferedReader(new StringReader(answerBuff.toString()));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "While executing '" + cmd + "' got exception:", t);
        }
        if (pro != null) {
            pro.destroy();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (Throwable t) {
        }
        pro = null;
        out = null;
        return null;
    }

    /** classify this IP according to its TTL (R) distance */
    private void classifyIP(String ip) {
        String cmd = pingPath + " " + ip + " 56 1 | grep ttl= | cut -d' ' -f 6 ; echo 0";
        //String cmd = "ping -c 1 " + ip + " | grep ttl | cut -d\" \" -f 6 | cut -d= -f 2 ; echo 0";
        try {
            String ttl = runCommand(cmd).readLine();
            if (ttl != null) {
                Vector cls = (Vector) ipClasses.get(ttl);
                if (cls == null) {
                    cls = new Vector();
                    ipClasses.put(ttl, cls);
                }
                if (!cls.contains(ip)) {
                    cls.add(ip);
                }
            } else {
                logger.log(Level.WARNING, "Got invalid TTL for cmd=" + cmd);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While classifying IP: " + ip + " got exception:", ex);
        }
    }

    /** check if two IPs are on the same machine */
    private boolean ipId(String ip1, String ip2) {
        String cmd = allyPath + " " + ip1 + " " + ip2 + " | grep ipid | cut -d' ' -f 3 ; echo !";
        try {
            String ipid = runCommand(cmd).readLine();
            return (ipid.length() > 0) && (ipid.charAt(0) != '!');
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While doing IpID for " + ip1 + " " + ip2 + " got:", ex);
        }
        return false;
    }

    /** search the given IP in the dictionary and return
     * the class that contains it. If if doesn't exist, return null 
     */
    private Vector searchIpIDic(String ip) {
        synchronized (ipIDic) {
            for (Iterator lit = ipIDic.iterator(); lit.hasNext();) {
                Vector entry = (Vector) lit.next();
                if (entry.contains(ip)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** search the given IP in the OLD dictionary and return
     * the class that contains it. If if doesn't exist, return null 
     */
    private Vector searchOldIpIDic(String ip) {
        for (Iterator lit = oldIpIDic.iterator(); lit.hasNext();) {
            Vector entry = (Vector) lit.next();
            if (entry.contains(ip)) {
                return entry;
            }
        }
        return null;
    }

    /** analyze all IPs in this class by probing with ipId(.. , ..) 
     * almost each pair. Insert into the dictionary the pairs that
     * are on the same router
     */
    private void analyzeIPs(Vector ipClass) {
        //		HashSet modifiedGroups = new HashSet();
        for (int i = 0; i < ipClass.size(); i++) {
            String ip1 = (String) ipClass.get(i);
            Vector cls1 = searchIpIDic(ip1);
            // try to exploit the transitivity relation
            // this will skip probing ip1 with the rest of ips if
            // ip1 is in the same entry with some previous ip 
            if (cls1 != null) {
                boolean found = false;
                for (int k = 0; k < i; k++) {
                    String ip0 = (String) ipClass.get(k);
                    if (cls1 == searchIpIDic(ip0)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    continue;
                }
            }
            // probe ip1 with all following IPs in this class
            // adjusting the ipIDic accordingly
            for (int j = i + 1; j < ipClass.size(); j++) {
                String ip2 = (String) ipClass.get(j);
                cls1 = searchIpIDic(ip1);
                Vector cls2 = searchIpIDic(ip2);
                if ((cls1 != null) && (cls2 != null)) {
                    // they are already in different groups;
                    // don't bother anymore.
                    continue;
                }
                // one of them is not yet analyzed; do it now
                //System.out.print("Probing "+ip1+" : "+ip2+" -> ");
                boolean ipid = ipId(ip1, ip2);
                //System.out.println(ipid);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
                if (!ipid) {
                    continue;
                }
                // they are on the same machine
                if ((cls1 != null) && (cls2 == null)) {
                    cls1.add(ip2);
                    //					modifiedGroups.add(cls1);
                } else if ((cls1 == null) && (cls2 != null)) {
                    cls2.add(ip1);
                    //					modifiedGroups.add(cls2);
                } else {
                    cls1 = new Vector();
                    cls1.add(ip1);
                    cls1.add(ip2);
                    ipIDic.add(cls1);
                    //					modifiedGroups.add(cls1);
                }
            }
            // if ip1 is still ungrouped, create a group just for it
            if (searchIpIDic(ip1) == null) {
                cls1 = new Vector();
                cls1.add(ip1);
                ipIDic.add(cls1);
                //				modifiedGroups.add(cls1);
            }
        }
        //		return modifiedGroups;
    }

    /** save hash as key : list of elements in corresponding vector, 
     * sepparated by spaces */
    private void saveHash(Hashtable hash, String fileName) {
        System.out.println("start:" + fileName);
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(fileName));
            for (Enumeration enh = hash.keys(); enh.hasMoreElements();) {
                String key = (String) enh.nextElement();
                Vector ipClass = (Vector) hash.get(key);
                System.out.print(key + " :");
                for (int i = 0; i < ipClass.size(); i++) {
                    System.out.print(" " + ipClass.get(i));
                    pw.println(ipClass.get(i));
                }
                System.out.println();
            }
            pw.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While saving hash to " + fileName + " got: ", ex);
        }
        System.out.println("end:" + fileName + " @ " + new Date());
    }

    /** save the ipClasses to a file */
    private void saveIPclasses() {
        saveHash(ipClasses, ipClassPath);
    }

    /** load saved IPs */
    private void loadIPclasses() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(ipClassPath));
            String line = null;
            while ((line = br.readLine()) != null) {
                addIP(line.trim());
            }
            br.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While loading IP classes, got:", ex);
        }
    }

    /** load the dictionary from file in oldIpIDic
     * to be able to answer quickly to the clients while ipIDic is  
     * rebuild in background from the loaded IP classes
     */
    private void loadDictionary() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(dicPath));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                StringTokenizer stk = new StringTokenizer(line);
                Vector group = new Vector();
                while (stk.hasMoreTokens()) {
                    String ip = stk.nextToken();
                    group.add(ip);
                }
                oldIpIDic.add(group);
            }
            br.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While loading dictionary got:", ex);
        }
    }

    /** load static definitions for IPID data, returning modifications */
    private void loadStaticIPids() {
        //		HashSet modif = new HashSet();
        try {
            BufferedReader br = new BufferedReader(new FileReader(staticIPidPath));
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                StringTokenizer stk = new StringTokenizer(line);
                //				HashSet groupOld = new HashSet();
                HashSet group = new HashSet();
                while (stk.hasMoreTokens()) {
                    String ip = stk.nextToken();
                    // for oldIpIdic
                    Vector initialGroup = searchOldIpIDic(ip);
                    //					if(initialGroup != null){
                    //						groupOld.addAll(initialGroup);
                    //						oldIpIDic.remove(initialGroup);
                    //					}else
                    //						groupOld.add(ip);

                    // for ipIDic
                    initialGroup = searchIpIDic(ip);
                    if (initialGroup != null) {
                        group.addAll(initialGroup);
                        ipIDic.remove(initialGroup);
                    } else {
                        group.add(ip);
                    }
                }
                // create a big group with all IPs
                //				oldIpIDic.add(new Vector(groupOld));
                ipIDic.add(new Vector(group));
            }
            br.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While loading static IPids, got:", ex);
        }
        //		return modif;
    }

    /** save the list of vectors, each vector on a line in fileName; 
     * elements are sepparated by spaces */
    private void saveList(List list, String fileName) {
        System.out.println("start:" + fileName);
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(fileName));
            for (Iterator lit = list.iterator(); lit.hasNext();) {
                Vector group = (Vector) lit.next();
                // save data to file
                for (int i = 0; i < group.size(); i++) {
                    System.out.print(group.get(i) + " ");
                    pw.print(group.get(i) + " ");
                }
                System.out.println();
                pw.println();
            }
            pw.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "While saving list to " + fileName + " got: ", ex);
        }
        System.out.println("end:" + fileName + " @ " + new Date());
    }

    /** save the dictionary to a file and also update oldIpIDic */
    private void saveDictionary() {
        synchronized (oldIpIDic) {
            saveList(ipIDic, dicPath);
            oldIpIDic.clear();
            for (Iterator lit = ipIDic.iterator(); lit.hasNext();) {
                Vector group = (Vector) lit.next();
                Vector cloneGroup = new Vector(group);
                oldIpIDic.add(cloneGroup);
            }
        }
    }

    private final static int CONTAINS_NONE = 1;
    private final static int CONTAINS_SOME = 2;
    private final static int CONTAINS_ALL = 3;

    /** how many elements from b contains a */
    private int howManyContains(Vector a, Vector b) {
        int h = 0, sb = b.size();
        for (int i = 0; i < sb; i++) {
            h += a.contains(b.get(i)) ? 1 : 0;
        }
        if (h == 0) {
            return CONTAINS_NONE;
        }
        if (h == b.size()) {
            return CONTAINS_ALL;
        }
        return CONTAINS_SOME;
    }

    /** make a diff between ipIDic and oldIpIDic; return changed groups */
    private HashSet diffDics() {
        HashSet modif = new HashSet();
        for (Iterator git = ipIDic.iterator(); git.hasNext();) {
            Vector group = (Vector) git.next();
            boolean newGroup = true;
            for (Iterator ogit = oldIpIDic.iterator(); ogit.hasNext();) {
                Vector oldGroup = (Vector) ogit.next();

                int oldInNew = howManyContains(group, oldGroup);
                int newInOld = howManyContains(oldGroup, group);

                if (((oldInNew == CONTAINS_NONE) && (newInOld == CONTAINS_NONE))) {
                    continue;
                }
                if ((oldInNew == CONTAINS_ALL) && (newInOld == CONTAINS_ALL)) {
                    newGroup = false;
                    break;
                }
                modif.add(group);
                break;
            }
            if (newGroup) {
                modif.add(group);
            }
        }
        return modif;
    }

    class IPAnalyzer extends Thread {
        Vector ipList = null;

        IPAnalyzer(Vector ipList) {
            this.ipList = ipList;
            start();
        }

        @Override
        public void run() {
            analyzeIPs(ipList);
        }
    }

    /** check newly received IPs */
    @Override
    public void run() {
        while (true) {
            try {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                }
                long now = NTPDate.currentTimeMillis();
                boolean fullRefresh = false;
                //				System.out.println("now="+now+" lrt="+lastIpClassRefreshTime);
                if ((now - lastIpClassRefreshTime) > ipClassRefreshDelta) {
                    lastIpClassRefreshTime = now;
                    ipClasses.clear();
                    ipIDic.clear();
                    System.out.println("Refreshing IP Classes... " + new Date());
                    loadIPclasses();
                    fullRefresh = true;
                }
                // classify newly added IPs
                //System.out.println("starting classifying @ "+ new Date());
                boolean saveIPs = false;
                while (newIPs.size() > 0) {
                    String ip = (String) newIPs.remove(0);
                    classifyIP(ip);
                    saveIPs = true;
                }
                if (saveIPs) {
                    saveIPclasses();
                }
                //System.out.println("finished classifying @ "+ new Date());
                // analyze IPs in the same class
                Vector analyzers = new Vector();
                for (Enumeration enIpCls = ipClasses.elements(); enIpCls.hasMoreElements();) {
                    Vector ips = (Vector) enIpCls.nextElement();
                    analyzers.add(new IPAnalyzer(ips));
                }
                for (int i = 0; i < analyzers.size(); i++) {
                    ((Thread) analyzers.get(i)).join();
                }
                analyzers.clear();

                loadStaticIPids();
                if (fullRefresh) {
                    modifications.clear();
                    updateModifications(new HashSet(oldIpIDic));
                }
                HashSet changedGroups = diffDics();
                if (changedGroups.size() > 0) {
                    updateModifications(changedGroups);
                    saveDictionary();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "While checking newly received IPs, got:", t);
            }
        }
    }

    /** 
     * This class  
     * @author catac
     */
    class IPGroup {

    }
}
