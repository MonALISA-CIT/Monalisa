//package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

public class monIDS extends cmdExec implements MonitoringModule {

    /** Logger Name */
    private static final String COMPONENT = "lia.Monitor.modules.monIDS";

    /** The Logger */
    private static final Logger logger = Logger.getLogger(COMPONENT);

    static public String ModuleName = "monIDS";

    static public String ClusterName = "Attacks";

    static public String[] ResTypes = null;

    static public String OsName = "linux";

    private String cmd;

    //last time this module was called
    private long lastCall;

    public monIDS() {
        super(ModuleName);
        //info is initialized at this point
        ResTypes = info.ResTypes;
        info.name = ModuleName;
        lastCall = System.currentTimeMillis();
        info.lastMeasurement = lastCall;
        isRepetitive = true;

        ClassLoader cl = this.getClass().getClassLoader();
        URL url = cl.getResource("logtail");
        if (url == null) {
            logger.log(Level.SEVERE, "logtail could not be found ...");
            cmd = null;
            info.addErrorCount();
            info.setState(1); // error
            info.setErrorDesc("logtail could not be found ...");

        } else {
            cmd = url.getPath() + " /var/log/snort/alert .alert.offset";
            info.setState(0);            
        }

    }

    public String[] ResTypes() {
        return info.ResTypes;
    }

    public String getOsName() {
        return OsName;
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    public Object doProcess() throws Exception {

        long ls = System.currentTimeMillis();
        if (ls <= lastCall)
            return null;

        Object o = getLastAlerts();
        if (o == null || !(o instanceof Hashtable))
            return null;
        Hashtable allAlerts = (Hashtable) o;
        if (allAlerts.size() == 0)
            return null;

        Vector retV = new Vector();
        //Iterate over the values in the map
        Iterator it = allAlerts.keySet().iterator();
        while (it.hasNext()) {
            // Get key
            String priority = (String) it.next();
            o = allAlerts.get(priority);
            if (o == null || !(o instanceof Hashtable))
                return null;

            Hashtable alerts = (Hashtable) o;
            if (alerts.size() == 0)
                continue;

            Result er = new Result();
            er.FarmName = getFarmName();
            er.ClusterName = ClusterName;//getClusterName();
            er.NodeName = priority;
            er.Module = ModuleName;
            er.time = ls;

            //    	Iterate over the values in the map
            Iterator iter = alerts.keySet().iterator();
            while (iter.hasNext()) {
                //          Get key
                String srcIP = (String) iter.next();
                int count = ((Integer) alerts.get(srcIP)).intValue();
                er.addSet(srcIP, count);
            }
            if (er.param != null && er.param.length > 0) {
                retV.add(er);

            }
        }

        lastCall = ls;

        logger.log(Level.INFO, retV.toString());
        if (retV != null && retV.size() > 0) {
            return retV;
        }
        return null;
    }

    private Hashtable getLastAlerts() throws Exception {
        //String cmd1 = "logtail /var/log/snort/alert ~/.alert.offset";
        BufferedReader ob = procOutput(cmd);
        if (ob == null) {
            logger.log(Level.INFO, " logtail output is null ");
            throw new Exception(" logtail output is null ");
        }
        //return alerts hashtable        
        Hashtable pr = parseLogtailOutput(ob);
        cleanup();
        return pr;
    }

    /**scan the snort alert file for attacks with priority @param priority
     * 
     * @param ob
     * @param priority
     * @return the srcIP->count map
     * @throws Exception
     */
    private Hashtable parseLogtailOutput(BufferedReader ob) throws Exception {
        Hashtable pr = new Hashtable();

        pr.put("HighPriority", new Hashtable());
        pr.put("MediumPriority", new Hashtable());
        pr.put("LowPriority", new Hashtable());
        pr.put("UnknownPriority", new Hashtable());

        while (true) {
            String line;
            try {
                line = ob.readLine();
            } catch (IOException e1) {
                break;
            }
            if (line == null)
                break;

            int priority = Integer.MAX_VALUE;
            String srcIP = null;

            Pattern pattern = Pattern.compile("\\s+([\\d\\.]+)\\:?(\\d+)?\\s[\\-\\>]+\\s([\\d\\.]+)\\:?(\\d+)?");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                srcIP = matcher.group(1);
            }

            pattern = null;
            matcher = null;
            pattern = Pattern.compile("\\s+\\[Priority\\:\\s+(\\d)]\\s+");
            matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    priority = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                }
            }

            String level;
            switch (priority) {
            case 1:
                level = "HighPriority";
                break;
            case 2:
                level = "MediumPriority";
                break;
            case 3:
                level = "LowPriority";
                break;
            default:
                level = "UnknownPriority";
                break;
            }

            Hashtable prH = (Hashtable) pr.get(level);
            if (prH.containsKey(srcIP)) {
                int count = ((Integer) prH.get(srcIP)).intValue() + 1;
                prH.put(srcIP, new Integer(count));
            } else {
                prH.put(srcIP, new Integer(1));
            }

        }//while
        return pr;
    }

    static public void main(String[] args) {
        String host = "localhost"; //args[0] ;
        monIDS aa = new monIDS();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null, null);

        try {
            Object cb = aa.doProcess();
            //System.out.println((Vector) cb);           
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("DONE.");
        System.exit(0);
    }

}

