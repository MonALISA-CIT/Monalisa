package lia.monitoring.cms;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.DateFileWatchdog;

public class New_CMS_FT extends cmdExec implements MonitoringModule, Observer {


    Vector rez;
    Hashtable lastValues;
    
    static public String ModuleName = "New_CMS_FT";
    
    long lastTime = 0;
    OraCMS_FT ocf;
    DateFileWatchdog dfw = null;
    File confFile = null;
    String[] param_names;
    
    public New_CMS_FT() {
        super("New_CMS_FT");
        lastValues = new Hashtable();
        System.out.println("Start the Interface to CMS_File ");
        isRepetitive = true;
        ocf = new OraCMS_FT();
        ocf.addObserver(this);
        rez = new Vector();
    }

    public MonModuleInfo init(MNode Node, String arg1) {
        System.out.println(" INIT New_CMS_FT  MODULE " + arg1);
        this.Node = Node;
        
        try {
            if (arg1 != null) {
                String tokens[] = arg1.split("(\\s)*,(\\s)*");
                
                if (tokens != null && tokens.length > 0 ) {
                    for(int i = 0; i < tokens.length; i++) {
                        if(tokens[i] != null) tokens[i] = tokens[i].trim();
                        if(tokens[i].indexOf("ConfFile") != -1 && tokens[i].indexOf("=") != -1) {
                            String[] ft = tokens[i].split("(\\s)*=(\\s)*");
                            if(ft != null && ft.length == 2) {
                                confFile = new File(ft[1]);
                                loadConf();
                                dfw = DateFileWatchdog.getInstance(confFile, 5 * 1000);
                                dfw.addObserver(this);
                                new Thread(ocf, "( ML ) OraCMS_FT Thread").start();
                            }
                        }
                    }
                }
            }
        }catch(Throwable t) {
            t.printStackTrace();
        }
        return info;
    }

    public String[] ResTypes() {
        return param_names;
    }

    public String getOsName() {
        return "linux";
    }

    public Object doProcess() throws Exception {
        Vector retV = null;
        synchronized(rez) {
            if(rez.size() > 0) {
                retV =  new Vector(rez);
                rez.clear();
            } 
        }
        return retV;
    }
    
    public void loadConf() {
        try {
            
            Properties p = new Properties();
	    FileInputStream fis = new FileInputStream(confFile);
            p.load(fis);
	    fis.close();
	    
            String jdbcUrl = p.getProperty("jdbcUrl");
            String userName = p.getProperty("username");
            String passwd = p.getProperty("passwd");
            String tableName = p.getProperty("tablename");
            String sitesS = p.getProperty("sites");
            String paramsS = p.getProperty("params");
            
            if(jdbcUrl != null) jdbcUrl = jdbcUrl.trim();
            if(userName != null) userName = userName.trim();
            if(passwd != null) passwd = passwd.trim();
            if(tableName != null) tableName = tableName.trim();

            long timeToSleep = Long.valueOf(p.getProperty("sleepTime", "60").trim()).longValue()*1000;
            String[] sites = null;
            
            if (sitesS != null) {
                sites = sitesS.split("(\\s)*,(\\s)*");
            } 
            
            if(paramsS != null) {
                param_names = paramsS.split("(\\s)*,(\\s)*");
            }
            
            ocf.setConf(jdbcUrl,userName,passwd,tableName, sites, param_names, timeToSleep);
            
        }catch(Throwable t) {
            t.printStackTrace();
        }
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    
    public void updateResults(Vector dbRows) {
        if (dbRows == null || dbRows.size() == 0) return;
        Vector newRez = new Vector(dbRows.size());
        
        for(int i = 0; i < dbRows.size(); i++) {
            Result r = translateFTOraRow((FTOraRow)dbRows.elementAt(i));
            if (r != null) {
                newRez.add(r);
            }
        }
        
        lastTime  = ((FTOraRow)dbRows.elementAt(0)).time;

        if (newRez.size() > 0) {
            rez.addAll(newRez);
        }
    }
    
    public void update(Observable o, Object arg) {
        if(o != null) {//should not normally happen ... but
            if( ocf != null && o.equals(ocf) ) {
                updateResults(ocf.getData());
            } else if( dfw != null && o.equals(dfw)){
                loadConf();
            }
        }
    }

    private Result translateFTOraRow(FTOraRow row) {
        
       if (row == null) return null;
       if(row.values == null || row.values.size() == 0) return null;
       
       Result r = new Result();
       try {
           
           r.ClusterName = Node.getClusterName();
           r.FarmName = Node.getFarmName();
           r.time = row.time;
           r.NodeName = row.node;
           
           r.param = new double[param_names.length*2];
           r.param_name = new String[param_names.length*2];
           
           for (int i = 0; i < param_names.length; i++) {
               Double D = (Double)row.values.get(param_names[i]);
               if(D != null) {
                   r.param_name[2*i] = param_names[i];
                   r.param_name[2*i + 1] = "R-" + param_names[i];
                   String key = r.NodeName + "&^%" + param_names[i];
                   Double lastV = (Double)lastValues.get(key);
                   if (lastV != null) {
                       long diffTime = r.time - lastTime;
                       double diffDouble = D.doubleValue() - lastV.doubleValue(); 
                       double diff = diffDouble/diffTime;
                       if (diff >= 0) {
                           r.param[2*i+1] = diff*1000;
                       } else {
                           System.out.println("\n\n!!!!!!! Negative diff [ " + key +" ] : LastValue =" + lastV + " New Value" + D +" diffValue = " + diffDouble + "    lastTime" + lastTime + " New Time " + r.time +" diffTime = " + diffTime + " \n\n\n");
                       }
                   }
                   lastValues.put(key, D);
                   r.param[2*i] = D.doubleValue();
               }
           }
           
       }catch(Exception e){
           e.printStackTrace();
           return null;
       }
       return r;
    }

    static public void main(String[] args) {

        String host = "localhost";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        New_CMS_FT aa = new New_CMS_FT();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), "ConfFile=/data/MonALISA/MonaLisa/Service/usr_code/NEW_CMS_FILES/conf/ModuleConf.properties");

        for (;;) {
            try {
                Object bb = aa.doProcess();
                System.out.println(" End Do process");
                if (bb != null && bb instanceof Vector) {
                    System.out.println(" MAIN ===================");

                    System.out.println(" Received a Vector having " + ((Vector) bb).size() + " results");
                    Vector cc = (Vector) bb;
                    for (int i = 0; i < cc.size(); i++) {
                        Result r = (Result) cc.elementAt(i);
                        System.out.println("===>" + r);
                    }
                }
            } catch (Exception e) {
                System.out.println(" failed to process ");
            }
            try {
                Thread.sleep(30 * 1000);//obly for testing
            } catch (Exception eion) {
                ;
            }
        }

    }



}

