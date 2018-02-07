package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.monPredicate;
import lia.util.MLProcess;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

public class monRRD extends SchJob implements MonitoringModule, dbStore {

    private MonModuleInfo mmi = null;

    private MNode mn = null;

    private String sRRDPath = null;

    //  private String sRRDFile = null;
    //  private Hashtable sRRDDictionary = null;
    private Properties p = null;

    private static final double INET_FACTOR = 1000D * 1000D / 8D;

    //  private URL remoteRRDURL = null;
    //  private File localRRDFile = null;
    private Hashtable hosts = null;

    private static long REMOTERRD_FETCH_DELAY = 2 * 60 * 1000;//2min

    //inner class for holding the info specific to a host
    private class RRDFileInfo {

        public File localRRDFile;

        public File localRRDTmpFile;

        public URL remoteRRDFile;

        public Hashtable paramMap;

        public Object sync;

        public long lastTimeMeasure;

        String rrdparams[];
    }

    //inner class that holds the info for a host
    private class RRDHostInfo {

        public String hostname;

        public Vector rrdFiles;
    }

    private class RemoteRRDFetcher extends Thread {

        private RRDFileInfo[] rfis = null;

        private boolean active = true;

        /**
         * @param v -
         *            Vector of RRDFileInfo
         */
        public RemoteRRDFetcher(Vector v) {
            super(" (ML) RemoteRRDFetcher ");
            if (v == null || v.size() == 0) {
                active = false;
                return;
            }

            try {
                rfis = (RRDFileInfo[]) v.toArray(new RRDFileInfo[v.size()]);
            } catch (Throwable t) {
                active = false;
            }

            if (active) {
                getAllFiles();
            }
        }

        private void getRemoteFile(URL remoteURL, File toLocalFile) throws Exception {
            byte buf[] = new byte[1024];
            InputStream urlIS = remoteURL.openStream();
            FileOutputStream fos = new FileOutputStream(toLocalFile);

            for (;;) {
                int nRead = urlIS.read(buf);
                if (nRead == 0 || nRead == -1) break;
                fos.write(buf, 0, nRead);
            }
            fos.flush();
            urlIS.close();
            fos.close();
        }

        public void getAllFiles() {

            long sTime = NTPDate.currentTimeMillis();

            for (int i = 0; i < rfis.length; i++) {
                try {
                    RRDFileInfo rfi = rfis[i];
                    if (rfi.remoteRRDFile == null || rfi.localRRDFile == null || rfi.localRRDTmpFile == null) {
                        continue;
                    }
                    boolean success = true;

                    try {
                        getRemoteFile(rfi.remoteRRDFile, rfi.localRRDTmpFile);
                    } catch (Throwable t) {
                        success = false;
                    }

                    if (success) {
                        try {
                            synchronized (rfi.sync) {
                                rfi.localRRDTmpFile.renameTo(rfi.localRRDFile);
                            }
                        } catch (Throwable t) {
                            System.out.println(" Error moving " + rfi.localRRDTmpFile.getPath() + " to " + rfi.localRRDFile.getPath());
                        }
                    }
                } catch (Throwable t) {
                }
            }

            System.out.println("monRRD Getting All Remote Files [ " + rfis.length + " ] took [ " + (NTPDate.currentTimeMillis() - sTime) + " ] ms ");
        }

        public void run() {
            if (rfis == null || rfis.length == 0) {
                active = false;
            }
            while (active) {
                try {
                    try {
                        Thread.sleep(REMOTERRD_FETCH_DELAY);
                    } catch (Throwable t) {
                    }
                    try {
                        getAllFiles();
                    } catch (Throwable ee) {
                    }
                } catch (Throwable t) {

                }
            }
        }
    }

    public MonModuleInfo init(MNode node, String args) {
        mn = node;
        hosts = new Hashtable();
        mmi = new MonModuleInfo();
        mmi.setName("monRRDmodule");
        mmi.setState(0);
        String sError = null;
        try {
            p = new Properties();
            p.load(new FileInputStream(args));

            sRRDPath = p.getProperty("rrdpath");
            int hostno = Integer.valueOf(p.getProperty("rrdhosts", "0")).intValue();
            REMOTERRD_FETCH_DELAY = Long.valueOf(p.getProperty("RRD_FETCH_DELAY", "2")).longValue() * 60 * 1000;

            for (int hi = 0; hi < hostno; hi++) {
                String host = p.getProperty("rrdhost" + hi, null);

                if (host == null || host.length() == 0) continue;
                RRDHostInfo rhi = new RRDHostInfo();
                rhi.hostname = host;
                rhi.rrdFiles = new Vector();

                //try to get the RRDs for this host
                int rrdNo = Integer.valueOf(p.getProperty("rrdfiles_" + hi, "0")).intValue();
                for (int ri = 0; ri < rrdNo; ri++) {
                    //the local rrdfile
                    String localrrd = p.getProperty("rrdfile_" + hi + "_" + ri, null);
                    if (localrrd == null || localrrd.length() == 0) continue;
                    File lrf = new File(localrrd);
                    if (lrf.isDirectory() || lrf.getParent() == null) {
                        System.out.println(" Error for host " + rhi.hostname + ". The rrdfile [ " + localrrd + " ] must exist and have RW access");
                        continue;
                    }

                    RRDFileInfo rfi = new RRDFileInfo();
                    rfi.sync = new Object();
                    rfi.localRRDFile = lrf;
                    rfi.localRRDTmpFile = new File(localrrd + ".TMP");
                    rfi.lastTimeMeasure = NTPDate.currentTimeMillis() - 20 * 60 * 1000;
                    //remoterrd URL
                    String remoterrd = p.getProperty("remoterrdfile_" + hi + "_" + ri, null);
                    URL remoterrdURL = null;
                    try {
                        remoterrdURL = new URL(remoterrd);
                    } catch (Throwable t) {
                        remoterrdURL = null;
                    }

                    rfi.remoteRRDFile = remoterrdURL;

                    //get param Mappings
                    int rrdpNo = Integer.valueOf(p.getProperty("rrdparams_" + hi + "_" + ri, "0")).intValue();
                    if (rrdpNo > 0) {
                        Hashtable htp = new Hashtable();
                        for (int rpi = 0; rpi < rrdpNo; rpi++) {
                            String rrdParam = p.getProperty("rrdparam_" + hi + "_" + ri + "_" + rpi, null);
                            String mlrrdParam = p.getProperty("mlrrdparam_" + hi + "_" + ri + "_" + rpi, null);
                            if (rrdParam == null || rrdParam.length() == 0 || mlrrdParam == null || mlrrdParam.length() == 0) {
                                continue;
                            }
                            htp.put(rrdParam, mlrrdParam);
                        }
                        rfi.paramMap = htp;
                    }
                    rhi.rrdFiles.add(rfi);
                }

                hosts.put(host, rhi);
            }

            Vector allFiles = new Vector();
            for (Enumeration rfis = hosts.elements(); rfis.hasMoreElements();) {
                allFiles.addAll(((RRDHostInfo) rfis.nextElement()).rrdFiles);
            }

            new RemoteRRDFetcher(allFiles).start();

            File f = new File(sRRDPath + "/rrdtool");
            if (!f.isFile()) { throw new IOException("rrdtool cannot be found in the '" + sRRDPath + "' folder"); }

        }

        catch (Exception e) {
            sError = e.getMessage();
        }

        mmi.lastMeasurement = NTPDate.currentTimeMillis() - 20 * 60 * 1000;

        return mmi;
    }

    private Vector getRRDOutput(String sCommand, String sParams, String sRRDFile) throws Exception {
        Vector v = new Vector();

        v.addElement(sRRDPath + "/rrdtool");
        v.addElement(sCommand);
        v.addElement(sRRDFile);

        if (sParams != null && sParams.length() > 0) {
            StringTokenizer st = new StringTokenizer(sParams, " ");
            while (st.hasMoreTokens()) {
                v.addElement(st.nextToken());
            }
        }

        String vs[] = new String[v.size()];
        for (int i = 0; i < v.size(); i++)
            vs[i] = (String) v.elementAt(i);

        Vector vr = getOutput(vs);

        if (vr != null && vr.size() > 0) {
            String sl = (String) vr.elementAt(0);

            if (sl.startsWith("RRDtool ") || sl.startsWith("ERROR: ")) { throw new IOException("Error while executing : " + v.toString()); }
        } else {
            throw new IOException("null output from : " + v.toString());
        }

        return vr;
    }

    private Vector getOutput(String vs[]) throws Exception {
//        Runtime rt = Runtime.getRuntime();

        Process pr = MLProcess.exec(vs, false);

        InputStream is = pr.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        Vector v = new Vector();

        String s = null;
        while (br != null && (s = br.readLine()) != null) {
            v.addElement(s);
        }

        pr.waitFor();
        try {
            pr.destroy();
        }catch(Throwable t){
            
        }
        
        return v;
    }

    // MonitoringModule

    public String[] ResTypes() {
        return mmi.getResType();
    }

    public String getOsName() {
        return "Linux";
    }

    public Object doProcess() throws Exception {
        if (mmi.getState() != 0) { throw new IOException("cannot read from rrd"); }

        Vector vr = new Vector();

        //let's rock
        for (Enumeration henum = hosts.keys(); henum.hasMoreElements();) {//for
                                                                          // all
                                                                          // hosts
            String hostname = (String) henum.nextElement();
            RRDHostInfo rhi = (RRDHostInfo) hosts.get(hostname);
            if (rhi == null) continue;

            for (int rrdi = 0; rrdi < rhi.rrdFiles.size(); rrdi++) {//for all
                                                                    // RRDs
                RRDFileInfo rfi = (RRDFileInfo) rhi.rrdFiles.elementAt(rrdi);
                Vector v = null;
                try {
                    synchronized(rfi.sync) {
                        v = getRRDOutput("fetch", "AVERAGE -s " + (rfi.lastTimeMeasure / 1000), rfi.localRRDFile.getAbsolutePath());
                    }
                } catch (Throwable t1) {
                    v = null;
                }
                String[] vs = null;
                if (v != null && v.size() > 0) {
                    for (int i = 0; i < v.size(); i++) {
                        String s = (String) v.elementAt(i);
                        if (i == 0) {
                            if (rfi.rrdparams == null) {
                                s = s.trim();
                                StringTokenizer st = new StringTokenizer(s, " \t");
                                vs = new String[st.countTokens()];

                                for (int vstmpi = 0; st.hasMoreTokens(); vstmpi++) {
                                    vs[vstmpi] = st.nextToken();
                                }
                                rfi.rrdparams = vs;
                            } else {
                                vs = rfi.rrdparams;
                            }
                            continue;
                        }

                        if (s.matches("^[1-9][0-9]{9,10}: .*$")) {
                            StringTokenizer st = new StringTokenizer(s, " :");

                            String sTime = st.nextToken();

                            long l = Long.parseLong(sTime) * 1000;

                            if (l <= rfi.lastTimeMeasure) {
                                continue;
                            }

                            Hashtable sRRDDictionary = rfi.paramMap;
                            if (sRRDDictionary == null) continue;

                            ExtResult er = new ExtResult();
                            er.FarmName = getFarmName();
                            er.ClusterName = getClusterName();
                            er.NodeName = hostname;
                            er.Module = mmi.getName();
                            er.time = l;

                            for (int j = 0; j < vs.length; j++) {
                                String sValue = st.nextToken();

                                if (!sValue.equals("nan")) {
                                    double d = Double.parseDouble(sValue);
                                    if (l > rfi.lastTimeMeasure) {
                                        rfi.lastTimeMeasure = l;
                                    }

                                    String param = vs[j];

                                    if (sRRDDictionary.containsKey(vs[j])) {
                                        param = (String) sRRDDictionary.get(vs[j]);
                                    } else {
                                        String mapParam = p.getProperty(vs[j], null);
                                        if (mapParam != null) {
                                            sRRDDictionary.put(vs[j], mapParam);
                                            param = mapParam;
                                        }
                                    }

                                    if (param.indexOf("_IN") != -1 || param.indexOf("_OUT") != -1) {
                                        er.addSet(param, d / INET_FACTOR);
                                    } else {
                                        er.addSet(param, d);
                                    }
                                }
                            }

                            if (er.param_name == null || er.param_name.length <= 0) {
                                continue;
                            }
                            vr.addElement(er);
                        }
                    }
                }
            }
        }

//        System.out.println(" doProcess returning " + vr.size());
//        for (int i = 0; i < vr.size(); i++) {
//            System.out.println(vr.elementAt(i));
//        }
        return vr;
    }

    public MNode getNode() {
        return mn;
    }

    public String getClusterName() {
        return mn.getClusterName();
    }

    public String getFarmName() {
        return mn.getFarmName();
    }

    public boolean isRepetitive() {
        return true;
    }

    public String getTaskName() {
        return mmi.getName();
    }

    public MonModuleInfo getInfo() {
        return mmi;
    }

    // dbStore interface implementation

    public java.util.Vector select(monPredicate pred) {
        try {
            long lStart = pred.tmin;
            long lEnd = pred.tmax;
            if (lStart <= 0) {
                lStart = NTPDate.currentTimeMillis() + lStart;
                lEnd = NTPDate.currentTimeMillis() + lEnd;

                if (lEnd < lStart) lEnd = lStart;
            }

            Vector vr = new Vector();
            
            //let's rock
            //for all hosts
            for (Enumeration henum = hosts.keys(); henum.hasMoreElements();) {
                String hostname = (String) henum.nextElement();
                RRDHostInfo rhi = (RRDHostInfo) hosts.get(hostname);
                if (rhi == null) continue;

                //for all RRDs for a given host
                for (int rrdi = 0; rrdi < rhi.rrdFiles.size(); rrdi++) {
                    RRDFileInfo rfi = (RRDFileInfo) rhi.rrdFiles.elementAt(rrdi);
                    Hashtable sRRDDictionary = rfi.paramMap;
                    String[] vs = rfi.rrdparams;
                    if (vs == null) continue;

                    boolean vb[] = new boolean[vs.length];
//                    boolean bExit = true;

                    // first check that at least one result type matches the
                    // given predicate, if not quit
                    for (int i = 0; i < vs.length; i++) {
                        Result r = new Result();
                        r.FarmName = getFarmName();
                        r.ClusterName = getClusterName();
                        r.NodeName = hostname;
                        r.Module = mmi.getName();
                        r.time = lStart;
                        if (sRRDDictionary.containsKey(vs[i])) {
                            vs[i] = (String) sRRDDictionary.get(vs[i]);
                        }
                        r.addSet(vs[i], (double) 1.0);

                        if (DataSelect.matchResult(r, pred) != null) {
                            vb[i] = true;
//                            bExit = false;
                        } else {
                            vb[i] = false;
                        }
                    }

//                    if (bExit) { 
//                        // the given predicate does not match any function we have,
//                        // so just try next one
//                        continue; 
//                    }

                    Vector v = null;
                    synchronized (rfi.sync) {
                        v = getRRDOutput("fetch", "AVERAGE -s " + (lStart / 1000) + " -e " + (lEnd / 1000), rfi.localRRDFile.getAbsolutePath());
                    }

                    for (int i = 0; v != null && i < v.size(); i++) {
                        String s = (String) v.elementAt(i);

                        if (s.matches("^[1-9][0-9]{9,10}: .*$")) {
                            StringTokenizer st = new StringTokenizer(s, " :");

                            String sTime = st.nextToken();

                            long l = Long.parseLong(sTime) * 1000;

                            if (l < lStart) {
                                continue;
                            }

                            if (l > lEnd) {
                                break;
                            }

                            for (int j = 0; j < vs.length; j++) {
                                String sValue = st.nextToken();

                                if (vb[j] == true && !sValue.toLowerCase().equals("nan")) {
                                    double d = Double.parseDouble(sValue);

                                    Result r = new Result();
                                    r.FarmName = getFarmName();
                                    r.ClusterName = getClusterName();
                                    r.NodeName = hostname;
                                    r.Module = mmi.getName();
                                    r.time = l;
                                    if (vs[j].indexOf("_IN") != -1 || vs[j].indexOf("_OUT") != -1) {
                                        r.addSet(vs[j], d / INET_FACTOR);
                                    } else {
                                        r.addSet(vs[j], d);
                                    }

                                    if (DataSelect.matchResult(r, pred) != null) {
                                        vr.addElement(r);
                                    }
                                }
                            }
                        }
                    }
                }//for all RRDs for a given host
            }//for all hosts
            return vr;
        } catch (Exception e) {
            return null;
        }
    }

    public static final void main(String[] args) {
        String host = "bigmac.fnal.gov";
        String ad = null;

        monRRD aa = new monRRD();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), "/home/ramiro/WORK/MSRC/MonaLisa/Service/usr_code/RRD/rrd.properties");
        try {
            for (;;) {
                Object bb = aa.doProcess();

                if (bb instanceof Vector) {
                    Vector v = (Vector) bb;
                    if (v != null) {
                        System.out.println(" [ " + new Date() + "]  Received a Vector having " + v.size() + " results");
                        for (int i = 0; i < v.size(); i++) {
                            System.out.println(v.elementAt(i));
                        }

                    }
                }
                try {
                    Thread.sleep(20 * 1000);
                } catch (Exception e1) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(" failed to process ");
        }

    }

}