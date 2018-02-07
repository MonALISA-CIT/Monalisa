import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.util.DateFileWatchdog;
import lia.util.DynamicThreadPoll.SchJob;

public abstract class GenericUDP extends SchJob implements MonitoringModule, GenericUDPNotifier, Observer {
    
    /** Logger name */
    private static final transient String COMPONENT = "lia.Monitor.modules.monGenericUDP";
    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger(COMPONENT);
    
    public MNode Node;
    public String TaskName;
    
    public MonModuleInfo info;
    
    static public String ModuleName = "GenericUDP";
    
    static public String OsName = "*";
    
    String[] resTypes = null;
    
    public boolean isRepetitive = false;
    
    InetAddress gAddress = null;
    
    int gPort = 8889;
    
    int maxMsgRate = 50; // maximum rate of messages accepted from a sender
    
    GenericUDPListener udpLS = null;
    File accessConfFile = null;
    UDPAccessConf accessConf = null;
    private Object locker = new Object();
    
    long last_measured = -1;
    
    boolean debug = false;
    Vector genResults;
    
    protected boolean bAppendIPToNodeName = false;
    
    public GenericUDP(String TaskName) {
        isRepetitive = true;
        this.TaskName = TaskName;
        info = new MonModuleInfo ();
        info.name = TaskName;
    }
    
    public MonModuleInfo init(MNode Node, String arg) {
        this.Node = Node;
        init_args(arg);
        info = new MonModuleInfo();
        
        genResults = new Vector();

        try {
            udpLS = new GenericUDPListener(gPort, this, null);
        } catch (Throwable tt) {
            logger.log(Level.WARNING, " Cannot create UDPListener !", tt);
        }
        udpLS.setMaxMsgRate(maxMsgRate);
        
        if (accessConfFile != null) {
            try {
                DateFileWatchdog dfw = new DateFileWatchdog(accessConfFile, 5 * 1000);
                dfw.addObserver(this);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot instantiate DateFileWatchdog for " + accessConfFile, t);
            }
            reloadCfg();
        }
        
        isRepetitive = true;
        
        info.ResTypes = resTypes;
        info.name = ModuleName;

        return info;
    }
    
    void init_args(String list) {
        if (list == null || list.length() == 0) return;
        String params[] = list.split("(\\s)*,(\\s)*");
        if (params == null || params.length == 0) return;
        
        for (int i=0; i<params.length; i++) {
            int itmp = params[i].indexOf("ListenPort");
            if (itmp != -1) {
                String tmp = params[i].substring(itmp+"ListenPort".length()).trim();
                int iq = tmp.indexOf("=");
                String port = tmp.substring(iq+1).trim();
                try {
                    gPort = new Integer(port).intValue();
                } catch(Throwable tt){
                    //gPort = 8889; // catac: the default port is set already (in constructor).
                }
            }
	    
		    itmp = params[i].indexOf("AppendIPToNodeName");
		    if (itmp != -1){
				String tmp = params[i].substring(itmp+"AppendIPToNodeName".length()).trim();
				int iq = tmp.indexOf("=");
				
				String val = "";
				
				if (iq>0)
				    val = tmp.substring(iq+1).trim().toLowerCase();
			
				// if the parameter exists the default value is true unless an explicit value of false is specified
				if (val.startsWith("f") || val.startsWith("0"))
				    bAppendIPToNodeName = false;
				else
				    bAppendIPToNodeName = true;
		    }
            
		    itmp = params[i].indexOf("MaxMsgRate");
            if (itmp != -1) {
                String tmp = params[i].substring(itmp+"MaxMsgRate".length()).trim();
                int iq = tmp.indexOf("=");
                String rate = tmp.substring(iq+1).trim();
                try {
                    maxMsgRate = new Integer(rate).intValue();
                } catch(Throwable tt){
                	// already defined
                }
                
            }
		    
            itmp = params[i].indexOf("AccessConfFile");
            if (itmp != -1) {
                String tmp = params[i].substring(itmp+"AccessConfFile".length()).trim();
                int iq = tmp.indexOf("=");
                String sCFile = tmp.substring(iq+1).trim();
                if (sCFile != null && sCFile.length() > 0) {
                    try {
                        accessConfFile = new File(sCFile);
                    } catch(Throwable tt) {
                        logger.log(Level.WARNING, "[ GenericUDP ] Got exception while initializing AccessConFile", tt);
                        accessConfFile = null;
                    }
                } else {
                    logger.log(Level.WARNING, "[ GenericUDP ] Please make sure that you have defined a valid file name after AccessConfFile = [ " + sCFile +" ] ");
                    accessConfFile = null;
                    return;
                }
                
                if (accessConfFile != null) {
                    if (!accessConfFile.exists()) {
                        logger.log(Level.WARNING, "[ GenericUDP ] The AccessConfFile that you have defined [ " + sCFile + " ] does not exist!");
                        accessConfFile = null;
                        return;
                    }
                    if (!accessConfFile.isFile()) {
                        logger.log(Level.WARNING, "[ GenericUDP ] The AccessConfFile that you have defined [ " + sCFile + " ] is not a File!");
                        accessConfFile = null;
                        return;
                    }
                    if (!accessConfFile.canRead()) {
                        logger.log(Level.WARNING, "[ GenericUDP ] The AccessConfFile that you have defined [ " + sCFile + " ] cannot be read!");
                        accessConfFile = null;
                        return;
                    }
                } else {//should not get here
                    logger.log(Level.WARNING, "[ GenericUDP ] SHOULD NOT GET HERE! AccessConfFile = [ " + sCFile +" ] ");
                }
            }
        }
    }
    
    public String[] ResTypes() {
        return resTypes;
    }
    
    public String getOsName() {
        return OsName;
    }
    
    public MNode getNode() {
        return Node;
    }
    
    public String getClusterName() {
        return Node.getClusterName();
    }
    
    public String getFarmName() {
        return Node.getFarmName();
    }
    
    public String getTaskName() {
        return ModuleName;
    }
    
    public boolean isRepetitive() {
        return isRepetitive;
    }
    
    public MonModuleInfo getInfo() {
        return info;
    }
    
    abstract public void notifyData(int len, byte[] data, InetAddress source);
    
    public Object getResults() {
        if (genResults == null || genResults.size() == 0)
            return null;
        Vector rV = new Vector();
        synchronized (genResults){
            rV.addAll(genResults);
            genResults.clear();
        }
        return rV;
    }
    
    protected void reloadCfg(){
        logger.log(Level.INFO, "[ GenericUDP ] : (RE)Loading conf ... [ " + accessConfFile + " ] ");
        BufferedReader br = null;
        FileReader fr = null;
        try {
            fr = new FileReader(accessConfFile);
            br = new BufferedReader(fr);
            String line = null;
            UDPAccessConf tmpAccessConf = new UDPAccessConf();
            while ((line = br.readLine()) != null) {
                try {
                    line = line.trim();

                    //ignore comments ( line that starts with # )
                    if (line.length() == 0 || line.startsWith("#")) continue;
                    
                    //password line
                    if (line.toLowerCase().indexOf("password") != -1) {
                        String[] tokens = line.split("(\\s)*=(\\s)*");
                        if (tokens == null || tokens.length != 2) {
                            logger.log(Level.WARNING, " GenericUDP reloadCfg: Ignoring line: " + line);
                            continue;
                        }
                        if (tokens[1] == null || tokens[1].trim().length() == 0) continue;
                        tmpAccessConf.setPassword(tokens[1].trim());
                        continue;
                    }
                    
                    //ip line
                    String[] tokens = line.split("(\\s)+");
                    if (tokens == null || tokens.length != 2) {
                        logger.log(Level.WARNING, "GenericUDP reloadCfg: Ignoring line: " + line);
                        continue;
                    }
                    
                    if (tokens[0] == null || tokens[1] == null || tokens[0].trim().length() == 0 || tokens[1].trim().length() == 0) {
                        logger.log(Level.WARNING, "GenericUDP reloadCfg: Cannot process line [ " + line +" ]. Ignoring...");
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
                            logger.log(Level.WARNING, "GenericUDP reloadCfg: Cannot process line [ " + line +" ]. Ignoring...Please specify a policy [ allow | deny ]");
                            continue;
                        } 
                    }catch(Throwable t){
                        logger.log(Level.WARNING, "GenericUDP reloadCfg: Cannot process line [ " + line +" ]. Ignoring...",t);
                        continue;
                    }
                    
                    tmpAccessConf.addIP(tokens[0].trim(), policy);
                } catch (Throwable t1) {
                    logger.log(Level.WARNING, "Got Exeption while parsing entry [ " + line + " ] ... ignoring it");
                }
            }//while
            
            synchronized(locker) {
                this.accessConf = tmpAccessConf;
            }
            udpLS.setAccessConf(this.accessConf);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got Exception while (Re)loading config");
        } finally {
            try {
                fr.close();
                br.close();
                fr = null;
                br = null;
            } catch (Throwable t1) {

            }
        }
        if(logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " UDPAccessConf: " + accessConf.toString());
        }
        //System.gc();
        logger.log(Level.INFO, " [ GenericUDP ] : Finished (RE)Loading conf");
    }

    
    public void update(Observable o, Object arg) {
        reloadCfg();
    }
    
}