package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.StringTokenizer;

import lia.Monitor.JiniClient.CommonGUI.ProcReader;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

public class monClientStats extends SchJob implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -2913098507447338700L;

    private MonModuleInfo mmi = null;

    private MNode mn = null;

    private long lLastCall = 0;

    private static final long lClientStarted = NTPDate.currentTimeMillis();
    ProcReader reader;
    private SerMonitorBase smb;
    private String clientType; // F for Farms or V for VRVS

    public MonModuleInfo init(MNode node, String args) {
        mn = node;
        reader = new ProcReader();
        
        // add client type in node's name
        mn.name = clientType+" "+mn.getName();
        
        mmi = new MonModuleInfo();
        mmi.setName("ClientStatsModule");
        mmi.setState(0);

        lLastCall = NTPDate.currentTimeMillis();
        mmi.lastMeasurement = lLastCall;

        return mmi;
    }

    public String[] ResTypes() {
        return mmi.getResType();
    }

    public String getOsName() {
        return System.getProperty("os.name");
    }

    public Object doProcess() throws Exception {
        long ls = NTPDate.currentTimeMillis();

        if (ls <= lLastCall)
            return null;

        Result er = new Result();
        er.FarmName = getFarmName();
        er.ClusterName = getClusterName();
        er.NodeName = mn.getName();
        er.Module = mmi.getName();
        er.time = ls;

        er.addSet("FreeMemory", Runtime.getRuntime().freeMemory());
        er.addSet("TotalMemory", Runtime.getRuntime().totalMemory());

        ConnMessageMux tmCli = smb.getTmClient();
        if(tmCli != null){
            er.addSet("RecMsg", tmCli.getMessageCount());
            er.addSet("Proxy",  tmCli.getProxyLongIP());
        }
        er.addSet("NodesCount", smb.getNodesCount());
        long clientLongIP = getLongClientIP();
        if(clientLongIP != -1)
            er.addSet("ClientIP", clientLongIP);
        try {
            er.addSet("Uptime", (NTPDate.currentTimeMillis() - lClientStarted) / 1000);
        } catch (Throwable t) {
        }

        // these are from cip's ReadProc class
        reader.update();
        er.addSet("Load1", Double.parseDouble(reader.getLoad1()));
        er.addSet("Load5", Double.parseDouble(reader.getLoad5()));
        er.addSet("Load15", Double.parseDouble(reader.getLoad15()));
        er.addSet("CPU_usr", Double.parseDouble(reader.getCPUUsr()));
        er.addSet("CPU_sys", Double.parseDouble(reader.getCPUSys()));
        er.addSet("CPU_idle", Double.parseDouble(reader.getCPUIdle()));
        er.addSet("CPU_nice", Double.parseDouble(reader.getCPUNice()));
        //System.out.println("Page_in = "+reader.getPagesIn());
        //er.addSet("Page_in", Double.parseDouble(reader.getPagesIn()));
        //er.addSet("Page_out", Double.parseDouble(reader.getPagesOut()));
        String [] netif = reader.getNetInterfaces();
        for(int i=0; i<netif.length; i++){
            er.addSet(netif[i]+"_IN", Double.parseDouble(reader.getNetIn(netif[i])));
            er.addSet(netif[i]+"_OUT", Double.parseDouble(reader.getNetOut(netif[i])));            
        }
        er.addSet("SysTotalMem", Double.parseDouble(reader.getMemTotal()));
        er.addSet("SysFreeMem", Double.parseDouble(reader.getMemFree()));
        er.addSet("NoProcesses", Double.parseDouble(reader.getNoProcesses()));
        
        lLastCall = ls;

        return er;
    }

    /** convert a string IP to it's long representation */
    long strIPtoLong(String ip){
        long longIP = 0;
        StringTokenizer stk = new StringTokenizer(ip, ".");
        while(stk.hasMoreTokens()){
            long b = Long.parseLong(stk.nextToken());
            longIP = (longIP << 8) + b;
        }
        return longIP;
    }
    
    /** use the topology service discovered in JiniClient to find this client's IP */
    long getLongClientIP(){
        long clientIP = -1;
        try{
	        URL url = new URL(smb.topoSerURL+"/FindIP?127.0.0.1");
	    	HttpURLConnection huc = (HttpURLConnection)url.openConnection();
	    	huc.setRequestMethod("GET");
	    	huc.setUseCaches(false);
	    	huc.connect();
	    	InputStream is = huc.getInputStream();
	    	int code = huc.getResponseCode();
	    	if (code == HttpURLConnection.HTTP_OK) {
	    		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    		while (reader.ready()) {
	    			String line = reader.readLine().trim();
	    			if(line.startsWith("publicIP:")){
	    			    clientIP = strIPtoLong(line.substring(10).trim());
	    			}
	    		}
	    	}
	    	huc.disconnect();
	    	is.close();
        }catch(Exception ex){
            System.out.println("Failed getting client's Public IP");
        }
        return clientIP;
    }
    
    /** set the SerMonitorBase to acces different status information */
    public void setSMB(SerMonitorBase smb){
        this.smb = smb;
        if(smb.mainClientClass.getName().indexOf("Farms") >= 0)
            clientType = "F";
        if(smb.mainClientClass.getName().indexOf("VRVS") >= 0)
            clientType = "V";
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

}