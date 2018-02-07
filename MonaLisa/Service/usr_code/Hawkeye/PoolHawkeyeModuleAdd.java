// $Header: /home/cvs/cvs/MSRC/MonaLisa/Service/usr_code/Hawkeye/PoolHawkeyeModuleAdd.java,v 1.1.1.1 2003-10-20 12:28:26 catac Exp $

/*--------------------------------------------------------------------
 *
 * Condor Java ClassAd library
 * Copyright (C) 1990-2001, CONDOR Team, Computer Sciences Department,
 * University of Wisconsin-Madison, WI, and Marvin Solomon
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of version 2.1 of the GNU Lesser General
 * Public License as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *--------------------------------------------------------------------*/
//package condor.MonaLisa;
import lia.Monitor.monitor.*;
import java.io.*;
import java.util.*;
import java.net.InetAddress;
import condor.classad.CollectorFetch;
/** 
 *
 * This is a MonaLisa module which retrieves Hawkeye-generated information about a monitored node
 * by querying the Condor collector associated with this node. 
 *
 *
 * @author <a href="mailto:ckireyev@cs.wisc.edu">Carey Kireyev</a>
 */
public class PoolHawkeyeModuleAdd extends cmdExec implements MonitoringModule  {

    public static final String ModuleName="PoolHawkeyeModuleAdd";

    private static Settings settings = null;
    
    private CollectorFetch collectorFetch = null;
    
    private long lastQueryTime;
    private boolean isNodeOK = true; 
    private boolean debug = false; 
        
    public PoolHawkeyeModuleAdd () {
        super(  ModuleName);
        
        System.out.println ("****PHMA:constructor called");
        
        debug = ("true".equals(System.getProperty ("lia.Monitor.debug")));

        // Not sure what this means...
        isRepetitive = true;

        // Read the names of the parameters we're going to return
        // from a file, if it hasn't been done before
        if (settings == null) {
            try {
                settings = new Settings();
            }
            catch (Exception e) {
                if (debug) { e.printStackTrace(); }
                throw new RuntimeException ("Error initializing PoolHawkeyeModuleAdd "+e.toString());
            }
            
        }
        info.ResTypes = settings.getResultParameters();
    }

    public String[] ResTypes () {
        debug ("settings.getResultParameters() = "+ settings.getResultParameters());
        return settings.getResultParameters();
    }

    public String getOsName() {
        return settings.getOSName();
    }

    public Object doProcess() {
        Properties classAd = null;
        
        System.out.println ("*** PHMA:doProcess called ");
        
        long resultTimestamp;
        long thisQueryTime = System.currentTimeMillis();

        // Fetch new results
        List newResults = null;
        try {
            newResults = collectorFetch.fetch();
        }
        catch (java.io.IOException e) {
            throw new RuntimeException ("Error conneting to the collector: "+e.toString());
        }

        Result result  = new Result ( Node.getFarmName(),
            Node.getClusterName(),
            Node.getName(),
            ModuleName, 
            settings.getResultParameters() );

        addResults (newResults, result);
        
        lastQueryTime = thisQueryTime;
        Vector victor = new Vector();
        victor.add (result);
        return victor;
    }

protected Result addResults ( List listNodeResults, Result result) {
    for (Iterator iterNodeResults=listNodeResults.iterator(); iterNodeResults.hasNext(); ) {
        Map nodeResult=(Map)iterNodeResults.next();
        
        Map mapHE2MLParamNames = settings.getHE2MLParamMap();
        for (Iterator iter = mapHE2MLParamNames.keySet().iterator(); iter.hasNext(); ) {
            String keyToXlate = iter.next().toString();
            String value = (String)nodeResult.get(keyToXlate);
            if (value != null) {
                // old: ("HE param name" -> string value)
                nodeResult.remove(keyToXlate);
                //new: ("ML param name" -> string value)
                nodeResult.put (mapHE2MLParamNames.get(keyToXlate), value);
            }
        }
        
        for (int iParams=0; iParams<result.param_name.length; iParams++) {
            result.param[iParams] = 0;
            
            String key = result.param_name[iParams];
            String value = (String)nodeResult.get(key);
            
            if (value == null) {
                debug ("Could not get value for key "+key+" for node");
                continue;
            }
            
            double nodeResultValue=0.0;
            
            // Convert value to double
            try {
                Double doubleVal = new Double(value);
                nodeResultValue=doubleVal.doubleValue();
            } 
            catch (java.lang.NumberFormatException nfe) {
                debug ("Value for parameter "+key+" is non-numeric: "+value);
                continue;
            }
            
            result.param[iParams] += nodeResultValue;
        }
    }
    
    return result;
}

public MonModuleInfo init( MNode Node , String arg) {
    // Get host and port of the collector from parameter
    
    System.out.println ("\n***PHMA:init1 called " + Node.getName()+ " ****");
    
    String host = null;
    int port = 0;

    try {
        int idx = arg.indexOf (":");
        host = arg.substring(0, idx);
        port = Integer.parseInt(arg.substring(idx+1));        
    }
    catch (Exception e){
        throw new RuntimeException ("Invalid parameter for node "+Node.getName()+". Expecting <collector_host>:<port>, found "+arg);
    }

    // Instantiate a collector interface
    try {    
        collectorFetch = new CollectorFetch (host,port);
    }
    catch (java.io.IOException e) {
        throw new RuntimeException ("Error connecting to the collector "+e.toString());
    }

    return super.init (Node, arg);
}

public MonModuleInfo getInfo(){
    return info;
}

static public void main ( String [] args ) {
    System.out.println ("*** main() *** ");
    String host = args[0] ;
    if (host.indexOf(':') > -1)
        host=host.substring(0, host.indexOf(':'));
    PoolHawkeyeModuleAdd aa = new PoolHawkeyeModuleAdd();

    String ad = null ;
    try {
        ad = InetAddress.getByName( host ).getHostAddress();
    } catch ( Exception e ) {
        System.out.println ( " Can not get ip for node " + e );
        System.exit(-1);
    }

    MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), args[0]);
    for (int i=0; i< 100; i++) {
        Object bb = aa.doProcess();

        Vector vv = ( Vector) bb;
        System.out.println ( " VV size = " + vv.size() );
         for ( int j=0; j < vv.size(); j ++ ) {
           Result rr = ( Result) vv.elementAt(j);
           System.out.println ( " RR=" +rr );
         }

        try {
            Thread.sleep(4000);
        }
        catch (Exception e) {}
    }
    
}

private void debug(String str) {
    String enabled = System.getProperty ("lia.Monitor.debug");
    if ("true".equals(enabled)) {
        System.out.println ("Hawkeye [Node="+Node.getName()+", time = "+System.currentTimeMillis()+"] : "+str);
    }
}

static class Settings {
    private Properties props = null;
    
    private int defaultStartDPort, cacheTimeoutInterval;
    private Map mapHE2MLParamNames = new Properties();
    private String [] params;
    private String OSName;
    
    public Settings() throws Exception {
        props = new Properties();
        props.load(new FileInputStream(System.getProperty ("condor.MonaLisa.PoolHawkeyeModuleAdd.config_file")));
        
        //defaultStartDPort = Integer.parseInt(props.getProperty("DEFAULT_STARTD_PORT"));
        //cacheTimeoutInterval = Integer.parseInt(props.getProperty("CACHE_TIMEOUT_INTERVAL"));
        OSName = props.getProperty ("OS_NAME");
        
        // Load parameter mapping
        String HE2MLParamsMapFile = props.getProperty("PARAMS_MAP_FILE");
        if (HE2MLParamsMapFile != null) {
            FileInputStream inputStream = 
                new FileInputStream (HE2MLParamsMapFile);
            ((Properties)mapHE2MLParamNames).load(inputStream);
            inputStream.close();
        }

        // Create a list of parameters we're monitoring
        String paramNames = props.getProperty ("MONITOR_PARAMS");
        StringTokenizer tok = new StringTokenizer(paramNames, ",");
        Vector paramsVector = new Vector();
        while (tok.hasMoreTokens())
            paramsVector.add (tok.nextToken());
        params = new String[paramsVector.size()];
        paramsVector.copyInto (params);
    }
    
    /*public int getDefaultStartDPort() {
        return this.defaultStartDPort;
    }
    
    public int getCacheTimeoutInterval() {
        return this.cacheTimeoutInterval;
    }*/

    public String[] getResultParameters() {
        return params;
    }
    
    public Map getHE2MLParamMap() {
        return this.mapHE2MLParamNames;
    }
    
    public String getOSName() {
        return OSName;
    }
}

}
