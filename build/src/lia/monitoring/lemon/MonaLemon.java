/*
 * $Id: MonaLemon.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.monitoring.lemon;

import java.net.InetAddress;
import java.util.Vector;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;

/**
 * 
 * Bridge between CERN's Lemon monitoring and ML
 * 
 * @author ramiro
 * 
 */
public class MonaLemon extends LemonUDPDecoder implements MonitoringModule  {
   
    /**
     * 
     */
    private static final long serialVersionUID = -6974350096466930746L;

    static public String ModuleName="MonaLemon";
    
    static public String TaskName ="MonaLemon";
    
    static public String[]  ResTypes = null;
    static public String OsName = "linux";
    
    public MonaLemon() { 
        super(ModuleName);
        
        info.name = ModuleName ;
        isRepetitive = true;
        
        ResTypes = info.ResTypes;
    }
    
    
    public String[] ResTypes () {
        return info.ResTypes;  
    }
    public String getOsName() { return OsName; }
    
    public Object  doProcess() throws Exception  {
        Object o = getResults();
        if ( o == null ) return null;
        
        if ( o instanceof Vector ) {
            Vector v = (Vector)o;
            if (v.size() == 0) return null;
            
            Vector retV = new Vector();
            for (int i = 0; i<v.size(); i++){
                Result r =(Result) v.elementAt(i);
                r.Module = TaskName;
                if ( r.param != null && r.param.length > 0 ){
                    retV.add(r);
                }
            }
            
            return retV;
        }
        return null; 
    }
    
    
    public MonModuleInfo getInfo(){
        return info;
    }
    
    
    public static final void main ( String [] args ) {
        String host = "localhost" ; //args[0] ;
        MonaLemon aa = new MonaLemon();
        String ad = null ;
        
        if(args == null || args.length == 0 || args[0] == null || args[0].length() == 0) {
            System.out.println("No args specified  ... will exit");
            System.out.println("Args should be like this: \"ListenPort=12509,MLLemonConfFile=/path/to/MonaLisa/Service/usr_code/Lemon/conf/MLLemonConfFile\"");
            System.exit(1);
        }
        
        try {
            ad = InetAddress.getByName( host ).getHostAddress();
        } catch ( Exception e ) {
            System.out.println ( " Can not get ip for node " + e );
            System.exit(-1);
        }
        
        aa.init( new MNode (host ,ad,  null, null), args[0]);
        
        for(;;) {
            try { 
                Object bb = aa.doProcess();
                try {
                    Thread.sleep(1 * 1000);
                } catch ( Exception e1 ){}
                
                if ( bb != null && bb instanceof Vector ){
                    Vector res = (Vector)bb;
                    if ( res.size() > 0 ) {
                        System.out.println("Got a Vector with " + res.size() +" results");
                        for ( int i = 0; i < res.size(); i++) {
                            System.out.println(" { "+ i + " } >>> " + res.elementAt(i));
                        }
                    }
                }
            } catch ( Exception e ) { ; }
            
        }
        
        
    }
    
  
}
