package lia.Monitor.Farm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;

public class EdMFarm {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(EdMFarm.class.getName());

    final MFarm farm;

    final Map<String, ModuleParams> mpHash;

    final FarmMonitor farmMonitor;

    public EdMFarm(FarmMonitor farmMonitor, MFarm farm, Map<String, ModuleParams> mpHash) {
        this.farm = farm;
        this.mpHash = mpHash;
        this.farmMonitor = farmMonitor;
    }

    public EdMFarm(MFarm farm, Hashtable<String, ModuleParams> mpHash) {
        this(null, farm, mpHash);
    }

    public void addEntry(String ecluster, String fnode, String module, long repeat, String param) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ EdMFarm ] [ addEntry ] ecluster=" + ecluster + ", fnode=" + fnode
                    + ", module=" + module + ", repeat=" + repeat + ", param=" + param);
        }
        String cluster;
        if (ecluster == null) {
            return;
        }
        int i1 = ecluster.indexOf("{");
        if (i1 != -1) {
            int i2 = ecluster.indexOf("}");
            String par = ecluster.substring(i1 + 1, i2);
            int i3 = ecluster.lastIndexOf("%");
            String eTime = null;
            if ((i3 != -1) && (i3 > i2) && (i3 < (ecluster.length() - 1))) {
                eTime = ecluster.substring(i3 + 1);
            }
            StringTokenizer tz = new StringTokenizer(par, ",");
            String exMod = null;
            if (tz.hasMoreTokens()) {
                exMod = (tz.nextToken()).trim();
            }
            String exNode = null;
            if (tz.hasMoreTokens()) {
                exNode = (tz.nextToken()).trim();
            }
            String exPar = null;
            if (tz.hasMoreTokens()) {
                exPar = (tz.nextToken()).trim();
            }
            while (tz.hasMoreTokens()) {
                exPar += "," + tz.nextToken();
            }
            cluster = ecluster.substring(0, i1).trim();
            MCluster cl = farm.getCluster(cluster);
            if (cl == null) {
                cl = new MCluster(cluster, farm);
                farm.getClusters().add(cl);
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " add external cluster" + cluster + " Module+" + cl.externalModule + " Node ="
                        + cl.externalNode + " agrs=" + cl.externalParam);
            }
            cl.externalModule = exMod;
            cl.externalParam = exPar;
            cl.externalNode = exNode;
            if (eTime != null) {
                cl.externalParam += ("%^&" + eTime);
            }
            return;
        }
        cluster = ecluster;
        MCluster cl = farm.getCluster(cluster);
        if (cl == null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ EdMFarm ] [ addEntry ] adding new cluster: " + cl);
            }
            cl = new MCluster(cluster, farm);
            farm.getClusters().add(cl);
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ EdMFarm ] [ addEntry ] cluster: " + cl + " already in the list");
            }
        }
        if ((fnode == null) && (module == null)) {
            return;
        }
        StringTokenizer tz = new StringTokenizer(fnode, " ");
        String node = tz.nextToken();
        MNode mn = cl.getNode(node);
        if (mn == null) {
            mn = new MNode(node, cl, farm);
            cl.getNodes().add(mn);
            if (tz.hasMoreTokens()) {
                mn.name_short = (tz.nextToken()).trim();
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ EdMFarm ] [ addEntry ] added new node: " + node + " in the cluster: " + cl
                        + " already in the list");
            }
        } else {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " [ EdMFarm ] [ addEntry ] node: " + node + " already in the cluster: " + cl
                        + " already in the list");
            }
        }
        if (module != null) {
            if (!mn.moduleList.contains(module)) {
                mn.moduleList.add(module);
            }
            mpHash.put(mn.getKey(module), new ModuleParams(module, Long.valueOf(repeat).longValue(), param)); // Long.valueOf( repeat) ); //
            // put in moduleInfo the
            // intial repetition time !
        }
    }

    public void removeNode(String cluster, MNode n) {
        MCluster cl = farm.getCluster(cluster);
        if (cl != null) {
            cl.removeNode(n);
        }
    }

    public void removeCluster(String cluster) {
        MCluster cl = farm.getCluster(cluster);
        if (cl != null) {
            farm.removeCluster(cl);
        }
    }

    public MNode[] getOrCreate(String cluster, String node) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINE, " [ EdMFarm ] [ getOrCreate ] cluster=" + cluster + ", node=" + node);
        }
        if (cluster == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "ERROR cluster name can not be null ");
            }
            return null;
        }
        if ((cluster.equals("*")) && ((node == null) || (node.equals("*")))) {
            Object[] obj = (farm.getNodes()).toArray();
            MNode[] nArr = new MNode[obj.length];
            for (int i = 0; i < nArr.length; i++) {
                nArr[i] = (MNode) obj[i];
            }
            return nArr;
            // return (MNode[]) (farm.getNodes() ).toArray() ;
        }
        addEntry(cluster, null, null, 0L, null);
        MCluster cl = farm.getCluster(cluster);
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " added entry  " + cluster + "  " + node);
        }
        if (node == null) {
            return null;
        }
        if (node.equals("*")) {
            Object[] obj = (cl.getNodes()).toArray();
            MNode[] nArr = new MNode[obj.length];
            for (int i = 0; i < nArr.length; i++) {
                nArr[i] = (MNode) obj[i];
            }
            return nArr;
        }
        MNode mn = cl.getNode(node);
        if (mn == null) {
            mn = new MNode(node, cl, farm);
            cl.getNodes().add(mn);
        }
        MNode[] res = new MNode[1];
        res[0] = mn;
        return res;
    }

    public void addModule(MNode n, String func, String[] param) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                    " [ EdMFarm ] [ addModule ] MNode=" + n + ", func=" + func + ", param[]=" + Arrays.toString(param));
        }
        if ((n == null) || (func == null)) {
            return;
        }
        if (!n.moduleList.contains(func)) {
            n.moduleList.add(func);
        }
        if (param == null) {
            return;
        }
        for (int i = 0; i < param.length; i++) {
            if (!n.getParameterList().contains(param[i])) {
                n.getParameterList().add(param[i]);
            }
        }
    }

    public void removeModule(MNode n, String func, String[] param) {
        if ((n == null) || (func == null)) {
            return;
        }
        if (n.moduleList.contains(func)) {
            n.removeModule(func);
        }
        if (param == null) {
            return;
        }
        for (String element : param) {
            if (n.getParameterList().contains(element)) {
                n.getParameterList().remove(element);
            }
        }
    }

    public void readOldFile(String ConfFile) throws Exception {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "[ EdMFarm ] Start to process config file " + ConfFile);
        }
        BufferedReader in = null;
        if (ConfFile == null) {
            ConfFile = AppConfig.getProperty("FarmMonitor.conf");
        }

        try {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "Try " + ConfFile);
            }
            in = new BufferedReader(new InputStreamReader(new java.net.URL(ConfFile).openStream()));
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ EdMFarm ] No conf file !" + ConfFile, t);
            }
            throw new Exception("[ EdMFarm ] unable to init stream to read the confFile: " + ConfFile, t);
        }
        String line;
        int nline = 0;
        String clusterName = "ProcNode"; // default Cluster name ! in case is not defined
        String nodeName = null;
        String moduleName = null;
        try {
            // set timeout for params and nodes from monABPing module
            farmMonitor.modulesTimeoutConfig.put("monABPing", new HashMap());
            HashMap hptp = (HashMap) farmMonitor.modulesTimeoutConfig.get("monABPing");
            hptp.put("ParamTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes
            hptp.put("NodeTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes
            hptp.put("ClusterTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes

            // set timeout for results with "monUNKNOWN" module name
            farmMonitor.modulesTimeoutConfig.put(FarmMonitor.MON_UNKOWN_NAME, new HashMap());
            hptp = (HashMap) farmMonitor.modulesTimeoutConfig.get("monUNKNOWN");
            hptp.put("ParamTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes
            hptp.put("NodeTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes
            hptp.put("ClusterTimeout", Long.valueOf(5 * 60 * 1000)); // 5 minutes

            farmMonitor.modulesTimeoutConfig.put("OlimpsFLFilter", new HashMap());
            hptp = (HashMap) farmMonitor.modulesTimeoutConfig.get("OlimpsFLFilter");
            hptp.put("ParamTimeout", Long.valueOf(20 * 1000)); // 5 minutes
            hptp.put("NodeTimeout", Long.valueOf(20 * 1000)); // 1 minute
            hptp.put("ClusterTimeout", Long.valueOf(20 * 1000)); // 1 minutes

            while ((line = in.readLine()) != null) {
                nline++;
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, " [ EdMFarm ] read line: " + line + " count: " + nline);
                }
                final String cline = line.trim();
                if (cline.length() <= 1) {
                    continue;
                }
                if (cline.startsWith("#")) {
                    continue;
                }
                if (cline.startsWith("^")) {
                    moduleName = (cline.substring(1)).trim();
                    if ((moduleName == null) || (moduleName.length() == 0)) {
                        continue;
                    }
                    int sI = moduleName.indexOf("{");
                    int eI = moduleName.indexOf("}");
                    String nModuleName = null;
                    String param = null;
                    String repTime = "" + (30 * 1000);
                    if ((sI != -1) && (eI != -1)) {
                        nModuleName = moduleName.substring(0, sI);
                        param = moduleName.substring(sI + 1, eI);
                        moduleName = moduleName.substring(eI + 1);
                    }
                    int rtI = moduleName.indexOf("%");
                    if (rtI != -1) {
                        repTime = moduleName.substring(rtI + 1).trim();
                        moduleName = moduleName.substring(0, rtI);
                        try {
                            repTime = "" + (Integer.valueOf(repTime).longValue() * 1000);
                        } catch (Throwable t) {
                            repTime = "" + (30 * 1000);
                        }
                    }
                    if ((nModuleName != null) && (nModuleName.length() > 0)) {
                        moduleName = nModuleName;
                    }

                    // parse param for timeout params
                    if ((param != null) && (param.length() > 0)) {
                        String nparam = "";
                        StringTokenizer st = new StringTokenizer(param, ",");
                        HashMap hpt = (HashMap) farmMonitor.modulesTimeoutConfig.get(moduleName);
                        if (hpt == null) {
                            farmMonitor.modulesTimeoutConfig.put(moduleName, new HashMap());
                            hpt = (HashMap) farmMonitor.modulesTimeoutConfig.get(moduleName);
                        }

                        while (st.hasMoreTokens()) {
                            String token = st.nextToken().trim();
                            if (token.indexOf("ParamTimeout") != -1) {
                                fillNewTimeoutParam(hpt, "ParamTimeout", token);
                                continue;
                            } else if (token.indexOf("NodeTimeout") != -1) {
                                fillNewTimeoutParam(hpt, "NodeTimeout", token);
                                continue;
                            } else if (token.indexOf("ClusterTimeout") != -1) {
                                fillNewTimeoutParam(hpt, "ClusterTimeout", token);
                                continue;
                            } else {
                                nparam += token + ",";
                            }
                        } //while
                        if (nparam.endsWith(",")) {
                            param = nparam.substring(0, nparam.length() - 1);
                        } else {
                            param = nparam;
                        }
                    } //if - parse param for timeout params
                    farmMonitor.addExtMod(moduleName, param, repTime);
                    continue;
                } else if (cline.startsWith("*")) {
                    clusterName = (cline.substring(1)).trim();
                    addEntry(clusterName, null, null, 0, null);
                    continue;
                } else if (cline.startsWith(">")) {
                    nodeName = (cline.substring(1)).trim();
                } else {
                    long repet = 30000;
                    String fun = line.trim();
                    int i1 = fun.indexOf("%");
                    if (i1 != -1) {
                        moduleName = (fun.substring(0, i1)).trim();
                        String rep = (fun.substring(i1 + 1)).trim();
                        repet = 1000 * (Integer.valueOf(rep)).longValue();
                    } else {
                        moduleName = fun.trim();
                    }
                    String param = null;
                    i1 = moduleName.indexOf("{");
                    if (i1 != -1) {
                        int i2 = moduleName.indexOf("}");
                        param = moduleName.substring(i1 + 1, i2);
                        moduleName = moduleName.substring(0, i1).trim();
                    }
                    addEntry(clusterName, nodeName, moduleName, repet, param);
                }
            }
        } catch (IOException ioex) {
            logger.log(Level.SEVERE, "[ EdMFarm ] IOException Reading the conf file " + ConfFile, ioex);
            throw new Exception("[ EdMFarm ] IOException Reading the conf file ", ioex);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ EdMFarm ] General Exception Reading the conf file ", t);
            throw new Exception("[ EdMFarm ] General Exception Reading the conf file ", t);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private void fillNewTimeoutParam(HashMap hpt, String paramName, String token) {
        int ieq = token.indexOf("=");
        long timeout = -1;
        if ((ieq != -1) && (ieq != (token.length() - 1))) {
            try {
                timeout = Long.valueOf(token.substring(ieq + 1).trim()).longValue() * 1000;
            } catch (Throwable t) {
                timeout = -1;
            }
        }
        if (timeout != -1) {
            hpt.put(paramName, Long.valueOf(timeout));
        }
    }
}