import java.io.BufferedReader;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.net.URL;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;

public class DiskIO extends cmdExec implements MonitoringModule {

    static final String[] tmetric = { "IORead", "IOWrite"};
    Hashtable devices;
    String host;
    long lTime = 0;
    long cTime = 0;
    String cmd = null;

    public DiskIO() {
        super("DiskIO");
        info.ResTypes = tmetric;
        isRepetitive = true;
        devices = new Hashtable();
    }

    public MonModuleInfo init(MNode Node, String arg1) {
        System.out.println(" INIT monDiskIO Module " + arg1);
        this.Node = Node;
        info.ResTypes = tmetric;

        ClassLoader cl = this.getClass().getClassLoader();
        URL url = cl.getResource("iostat");
        cmd=url.getPath();
        
        if (arg1 != null) {
            StringTokenizer tz = new StringTokenizer(arg1, ",");
            while ( tz.hasMoreTokens() ){
                String nextToken = tz.nextToken();
                if (nextToken != null && nextToken.length() > 0 && nextToken.indexOf("CMD") != -1) {
                    cmd = nextToken.substring(nextToken.indexOf("CMD")+"CMD".length()).trim();
		    cmd = cmd.substring(cmd.indexOf("=")+1).trim();
                }
            }
        }
        
        System.out.println(" Using CMD [ " + cmd + " ]");

        return info;
    }

    public Object doProcess() throws Exception {

        BufferedReader buff1 = procOutput(cmd);

        if (buff1 == null) {
            if (pro != null) pro.destroy();
        } else {
             cTime = System.currentTimeMillis();
             return Parse(buff1);
        }
        return null;
    }
    
    
    //      Device: tps Blk_read/s Blk_wrtn/s Blk_read Blk_wrtn
    //      hda 2.49 41.54 43.50 5693550 5961602
    public Vector Parse(BufferedReader buff) throws Exception {
        
        //first ignore the lines until 'Device:' is reached 
        String lin = buff.readLine();
        try {
            while (lin != null && lin.indexOf("Device:") == -1) {
                lin = buff.readLine();
            }
        } catch ( Exception t ) {
            System.out.println("Exception in DiskIO Ex=");
            t.printStackTrace();
            buff.close();
            if (pro != null) pro.destroy();
            throw t;
        }
        
        //should not happen ... but never knows (broken buffer!?) 
        if (lin == null || lin.length() == 0) {
            throw new Exception("DiskIO: 'Device:' delimiter not found. Broken buffer?");
        }
        
        Vector results = new Vector();
        //let's parse the devices
        try {
            lin = buff.readLine();
            if (lin == null || lin.length() ==0) {
                throw new Exception("DiskIO: No devices detected after 'Device:'?");
            }
            
            long totalDiffRead = 0;
            long totalDiffWrite = 0;

            double tmp = 1024D*(double)(cTime - lTime);
            Vector currentDevices = new Vector();
            
            for (;;) {
                if (lin == null || lin.length() == 0) break;
//                System.out.println("Lin = "+lin);
                StringTokenizer st = new StringTokenizer(lin);
                
                //Device Name
                String deviceName = null;
                long bRead = -1; 
                long bWrite = -1;
                
                //hda
                if (st.hasMoreTokens()) {
                    deviceName = st.nextToken();
                }
                
                //should not happen
                if (deviceName == null || deviceName.length() ==0) break;

                //tps
                if (st.hasMoreTokens()) {
                    st.nextToken();
                }
                
                //Blk_read/s
                if (st.hasMoreTokens()) {
                    st.nextToken();
                }

                //Blk_wrtn/s
                if (st.hasMoreTokens()) {
                    st.nextToken();
                }

                //Blk_read
                if (st.hasMoreTokens()) {
                    try {
                        bRead = Long.parseLong(st.nextToken());
                    } catch (Exception e){
                        bRead = -1;
                    }
                }
                
                //Blk_wrtn
                if (st.hasMoreTokens()) {
                    try {
                        bWrite = Long.parseLong(st.nextToken());
                    } catch (Exception e){
                        bWrite = -1;
                    }
                }
                
                //should not happen...but
                if (deviceName == null || deviceName.length() == 0 || bRead == -1 || bWrite == -1) {
                    lin = buff.readLine();
                    continue;
                }
                
                long[] oldValues = (long[])devices.get(deviceName);
                currentDevices.add(deviceName);
                if (oldValues == null) {
                    devices.put(deviceName, new long[tmetric.length]);
                    oldValues = (long[])devices.get(deviceName);
                    oldValues[0] = bRead;
                    oldValues[1] = bWrite;
                    lin = buff.readLine();
                    continue;
                }
                
                oldValues = (long[])devices.get(deviceName);
                long diffRead = diffUnsignedInt(bRead, oldValues[0]);
                long diffWrite = diffUnsignedInt(bWrite, oldValues[1]);
                
                totalDiffRead += diffRead;
                totalDiffWrite += diffWrite;
                
                oldValues[0] = bRead;
                oldValues[1] = bWrite;
                if (lTime != 0) {
                    Result r = new Result(Node.getFarmName(), Node.getClusterName(), deviceName, TaskName, tmetric);
                    r.param[r.getIndex(tmetric[0])] = ((double)diffRead/tmp)*512D;
                    r.param[r.getIndex(tmetric[1])] = ((double)diffWrite/tmp)*512D;
                    r.time = cTime;
                    results.add(r);
                } 
                //Loop through devices
                lin = buff.readLine();
            }
            buff.close();
            if (pro != null) pro.destroy();
            
            //perform cleanup() if there are devices that are no longer reported by `iostat`
            for (Enumeration dlist = devices.keys(); dlist.hasMoreElements(); ) {
                Object dev = dlist.nextElement();
                if (!currentDevices.contains(dev)) {
                    System.out.println(new Date() + " DiskIO: Removing a device ... [ " + dev + " ]");
                    devices.remove(dev);
                }
            }
            
            if (lTime != 0) {
                Result r = new Result(Node.getFarmName(), Node.getClusterName(), "Total", TaskName, tmetric);
                r.param[r.getIndex(tmetric[0])] = ((double)totalDiffRead/tmp)*512D;
                r.param[r.getIndex(tmetric[1])] = ((double)totalDiffWrite/tmp)*512D;
                r.time = cTime;
                results.add(r);
            } 

        } catch (Exception e) {
            System.out.println("Exeption in DiskIO Ex=");
            e.printStackTrace();
            buff.close();
            if (pro != null) pro.destroy();
            throw e;
        }

        //System.out.println ( rr );
        
        lTime = cTime;
        return results;

    }
    
    private long diffUnsignedInt(long newValue, long oldValue) {
        if (newValue >= oldValue) return (newValue - oldValue);
        return (2L*(long)Integer.MAX_VALUE)-oldValue+newValue;
    }
    public MonModuleInfo getInfo() {
        return info;
    }

    public String[] ResTypes() {
        return tmetric;
    }

    public String getOsName() {
        return "linux";
    }

    static public void main(String[] args) {

        String host = "bigmac.fnal.gov";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        DiskIO aa = new DiskIO();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            for (;;) {
                Object bb = aa.doProcess();

                if (bb instanceof Vector) {
                    Vector v = (Vector) bb;
                    if (v != null) {
                        System.out.println(" Received a Vector having " + v.size() + " results");
                        for (int i = 0; i < v.size(); i++) {
                            System.out.println(v.elementAt(i));
                        }

                    }
                }
                try { Thread.sleep(30*1000); }catch(Exception e1){}
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }

    }

}
