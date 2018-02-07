import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.GenericUDPResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.StringFactory;
import lia.util.Utils;
import lia.util.ntp.NTPDate;


public class XDRUDP extends GenericUDP {

    /** Logger used by this class */
    private static final transient Logger logger = Logger.getLogger("lia.Monitor.modules.monXDRUDP");
    
    static public String ModuleName = "XDRUDP";
    
    /** The time when the first datagram is received */ 
    long startTime = -1;
    
    /** The total number of datagrams received so far */
    int nDatagrams = 0;
    
    /** ApMon senders - used to identify packet loss */
    private Hashtable apMonSenders;
    
    /** 
     * If this is enabled, ApMon senders will be monitored. 
     * This is set by defining the following line in ml.properties:
     * "lia.Monitor.modules.monXDRUDP.MONITOR_SENDERS = true"
     */
    private boolean monitorApMonSenders = false;

    /**
     * If no data is received from a sender for a longer period of time, that
     * sender is removed from the apMonSenders hash. This expiry interval is
     * by default 900 sec (15 min) and is set in ml.properties with:
     * "lia.Monitor.modules.monXDRUDP.SENDER_EXPIRE_TIME = 900"
     */    
    private long senderExpireTime = 900 * 1000;
    
    /** 
     * This class identifies an ApMon sender and allows monitoring 
     * the packet loss based on the sequence number of the received packets.
     */
    class ApMonSender {
    	int seqNr;					// last heard sequence number
    	long lastHeard;				// when I heard last from this
    	String ipAddress;
    	int instanceID;				// sender instance ID on that machine
    	
    	ApMonSender(String ipAddress, int instanceID, int seqNr){
    		this.seqNr = seqNr;
    		this.ipAddress = ipAddress;
    		this.instanceID = instanceID;
    		lastHeard = NTPDate.currentTimeMillis();
    		logger.log(Level.INFO, "Registering "+toString());
    	}
    	
    	void updateSeqNr(int seqNr){
    		lastHeard = NTPDate.currentTimeMillis();
    		int diff = seqNr - this.seqNr;
    		if(diff < 0) // overflow ?
    			diff += 2000000000; // it wraps at 2 mld.
    		diff--;
    		if(diff != 0){
    			logger.log(Level.WARNING, "Lost "+diff+" packets ["+ (this.seqNr+1)+" to "+(seqNr-1)+"] from IP: "
    					+ipAddress+", instance: "+instanceID);
    		}
    		this.seqNr = seqNr;
    	}
    	
    	boolean isExpired(long now){
    		if(now - lastHeard > senderExpireTime)
    			return true;
    		else
    			return false;
    	}
    	
    	public String toString(){
    		return "ApMonSender "+ipAddress+", instance: "+instanceID
    			+", seqNr: "+seqNr+", lastHeard: "+(new Date(lastHeard));
    	}
    }
    
    public XDRUDP () { 
        super(ModuleName);
        
        info.name = ModuleName;
		OsName = "linux";
        isRepetitive = true;
		gPort = 8884;	// default ApMon port; can be changed through given parameters (see monGenericUDP)
		apMonSenders = new Hashtable();
    }

    public void notifyData(int len, byte[] data, InetAddress source) {
        
        XDRInputStream xdrIS = null;
        
        GenericUDPResult gur = null;
        
        /* for performance measurements */
        if (startTime == -1)
        	startTime = System.currentTimeMillis();
        
        try {
            if(logger.isLoggable(Level.FINEST))
               	logger.log(Level.FINEST, "Datagram dump for len="+len+":\n"+Utils.hexDump(data, len));
            
            xdrIS = new XDRInputStream(new ByteArrayInputStream(data));
            gur = new GenericUDPResult();
            
            gur.rtime = NTPDate.currentTimeMillis();
            
            //since ML 1.2.18
            //verify the password ... if sent
            boolean canProcess = (accessConf == null ?  true : ! accessConf.isPasswordDefined());
            String header = xdrIS.readString().trim();
            String version = null;
            byte majorVersion = -1;
            byte minorVersion = -1;
            byte maintenanceVersion = -1;
            
            xdrIS.pad();
            String c = header;

            int vi = header.indexOf("v:");
            int pi = header.indexOf("p:");
            
            if ( vi != -1 &&  pi != -1){
                version = header.substring(2, pi);//first is v:
                
                //the version should be something like major[[.minor][.release]]|[-][_]lang
                String[] splittedVersion = version.split("(_|-)");
                if(splittedVersion != null && splittedVersion.length > 0 
                        && splittedVersion[0] != null && splittedVersion[0].length()>0) {
                    String realVersions[] = splittedVersion[0].split("\\.");
                    try {
                        majorVersion = Integer.valueOf(realVersions[0]).byteValue();
                        minorVersion = Integer.valueOf(realVersions[1]).byteValue();
                        maintenanceVersion = Integer.valueOf(realVersions[2]).byteValue();
                    }catch(Throwable t){
                        
                    }
                }
                if(logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Full Version = " + version + " => [" + majorVersion +"." + minorVersion + "." + maintenanceVersion + "]");
                }
                
                //for now we ignore the version ... but in future versions maybe we'll need it
                String password = header.substring(pi+"p:".length()).trim();
                if (accessConf != null) {
                    canProcess = accessConf.checkPassword(password);
                }
                if (! canProcess) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "No Password matching...ignoring it"); 
                    }
                }
            }

            if (canProcess) {
            	if((majorVersion > 2) || ((majorVersion == 2) && (minorVersion >= 2))){
                	// if ApMon version is at least 2.2.0, we will read the two new fields that identify the ApMon Sender
            		int instanceID = xdrIS.readInt();
            		int seqNr = xdrIS.readInt();
            		
            		if(monitorApMonSenders){
            			Long srcID = buildApMonSenderUID(source, instanceID);
            			ApMonSender apmSender = (ApMonSender) apMonSenders.get(srcID);
            			if(apmSender == null){
            				apmSender = new ApMonSender(source.getHostAddress(), instanceID, seqNr);
            				apMonSenders.put(srcID, apmSender);
            			}else{
            				apmSender.updateSeqNr(seqNr);
            			}
            		}
            	}
            	
            	if(majorVersion != -1){
            		// if the packet defines a version, then we have to read the cluster name
            		// this is kept to be compatible with the first versions of ApMon which were
            		// not sending the version/password information and the cluster name was the
            		// first string in the packet.
                    c = xdrIS.readString();
                    xdrIS.pad();
            	}
            	
                String n = StringFactory.get(xdrIS.readString());
                xdrIS.pad();
                
                gur.clusterName =  StringFactory.get(c);
                gur.nodeName = n;
		
				if (bAppendIPToNodeName){
				    gur.nodeName += "_"+source.getHostAddress();
				    gur.addParam("NodeName", source.getHostAddress());
				}
                
                int nParams = xdrIS.readInt();
                xdrIS.pad();
                
                boolean error = false;
                for (int i = 0; !error && i < nParams; i++) {
                    String paramName = StringFactory.get(xdrIS.readString());
                    xdrIS.pad();
                    
                    if ( paramName == null || paramName.length() == 0 ) {
                        break;
                    }
                    
                    int paramType = xdrIS.readInt();
                    xdrIS.pad();
                    
                    switch (paramType) {
                    case XDRMLMappings.XDR_STRING: {
                        String value = StringFactory.get(xdrIS.readString());
                        xdrIS.pad();
                        gur.addParam(paramName, value);
                        break;
                    }
                    case XDRMLMappings.XDR_INT32: {
                        int value = xdrIS.readInt();
                        xdrIS.pad();
                        gur.addParam(paramName, new Double(value));
                        break;
                    }
                    case XDRMLMappings.XDR_INT64: {
                        long value = xdrIS.readLong();
                        xdrIS.pad();
                        gur.addParam(paramName, new Double(value));
                        break;
                    }
                    case XDRMLMappings.XDR_REAL32: {
                        double value = xdrIS.readFloat();
                        xdrIS.pad();
                        gur.addParam(paramName, new Double(value));
                        break;
                    }
                    case XDRMLMappings.XDR_REAL64: {
                        double value = xdrIS.readDouble();
                        xdrIS.pad();
                        gur.addParam(paramName, new Double(value));
                        break;
                    }
                    default: {
                        error = true;
                    }
                    }//switch
                }//for 
                
                if(! error 
						&& len > xdrIS.getBytesRead() 
						&& (majorVersion >= 2 || (majorVersion == 1 && minorVersion >= 6))) {
                	// Setting result time is supported since version 1.2.27
                	try {
	                	long time = xdrIS.readInt();
                        time *= 1000;
	                    xdrIS.pad();
	                    if(time > 0){
                            if(logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, " [ Generic UDP ] received timed result: "+time+" / " + new Date(time));
                            }
	                    	gur.rtime = time;
	                    }else{
	                    	logger.log(Level.FINE, " [ Generic UDP ] invalid time: " + time);
	                    }
	                }catch(IOException ex){
	                	logger.log(Level.FINE, " [ Generic UDP ] error while reading time for the result.");
	                }
                }

                if (!error) {
                    if ( logger.isLoggable(Level.FINER) ) {
                        logger.log(Level.FINER, " [ Generic UDP ] adding: " + gur);
                    }
                    genResults.add(gur);
                }
            }
        } catch ( Throwable t ){
            if ( logger.isLoggable(Level.FINE) ) {
                logger.log(Level.FINE, " [ Generic UDP ] Exception while decoding UDP datagram at byte "+(xdrIS != null ? xdrIS.getBytesRead() : -1)+" of "+len+":\n"
                		+Utils.hexDump(data, len), t);
                
            }
            gur = null;
        } finally {
            if (xdrIS != null) {
                try {
                    xdrIS.close();
                }catch(Throwable t){
                    
                }
                xdrIS = null;
            }
        }
        
        nDatagrams++;
    }
    
    public Object  doProcess() throws Exception  {
        Object o = getResults();
        if ( o == null ) return null;
        
        checkApMonSenders();
        
        if ( o instanceof Vector ) {
            Vector v = (Vector)o;
            if (v.size() == 0) return null;
            
            Vector retV = new Vector();
            
            for (int i = 0; i<v.size(); i++){
                GenericUDPResult gur = (GenericUDPResult)v.elementAt(i);
                
                if (gur.nodeName.startsWith("#acc_")){
                    if(logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "XDR : Accounting info");
                    }
                    
                    try{
                        AccountingResult ar = new AccountingResult();
                        
                        ar.sGroup = gur.clusterName;
                        ar.sUser  = gur.nodeName.substring(5);
                        ar.sJobID = gur.paramValues.get("jobid").toString();
                        
                        ar.lStartTime = ((Number) gur.paramValues.get("start")).longValue() * 1000L;
                        ar.lEndTime   = ((Number) gur.paramValues.get("stop")).longValue() * 1000L;
                        
                        if (ar.sGroup!=null && ar.sUser!=null && ar.sJobID!=null && ar.lStartTime>0 && ar.lEndTime>0){
                            ar.addParam("cpu_MHz"   , (Number) gur.paramValues.get("cpu_MHz"));
                            ar.addParam("utime"     , (Number) gur.paramValues.get("utime"));
                            ar.addParam("stime"     , (Number) gur.paramValues.get("stime"));
                            ar.addParam("virtualmem", (Number) gur.paramValues.get("virtualmem"));
                            ar.addParam("rss"       , (Number) gur.paramValues.get("rss"));
                        }
                        
                        if(logger.isLoggable(Level.FINEST)) {
                            logger.log(Level.FINEST, "XDR : Returning : "+ar);
                        }

                        retV.add(ar);
                    }
                    catch (Throwable t){
                        logger.log(Level.WARNING, "XDR : Throwable : ", t);
                    }
                } else {
                    if(logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "XDR : normal Result");
                    }
                    
                    Result r = new Result();
                    r.FarmName = Node.getFarmName();
                    r.ClusterName = gur.clusterName;
                    r.time = gur.rtime;
                    r.NodeName = gur.nodeName;
                    r.Module = TaskName;
                    
                    eResult er = new eResult();
                    er.FarmName = Node.getFarmName();
                    er.ClusterName = gur.clusterName;
                    er.time = gur.rtime;
                    er.NodeName = gur.nodeName;
                    er.Module = TaskName;
		    
                    if ( gur.paramValues != null && gur.paramValues.size() > 0) {
                        for ( int j = 0; j< gur.paramNames.length; j++){
                            String key = gur.paramNames[j];
                            Object value = gur.paramValues.get(key);
                            if (value != null) {
                                if ( value instanceof Double) {
                                    r.addSet(key, ((Double)value).doubleValue());
                                } else {
                                    er.addSet(key, value);
                                }
                            }
                        }
                    }
                    if ( r.param != null && r.param.length > 0 ){
                        retV.add(r);
                    }
                    if(er.param != null && er.param.length >0) {
                        retV.add(er);
                    }
                }
            }
            
            return retV;
        }
        return null; 
    }
    
    public void update(Observable o, Object arg) {
        reloadCfg();
        
        /* for performance measurements */     
        if (startTime > 0) {
        	long diffTime = (System.currentTimeMillis() - startTime) / 1000;
        	logger.log(Level.INFO, " [ XDRUDP ] : Datagrams received "
        			+ nDatagrams + ". Total runtime (s): " + diffTime + 
        			". Datagrams per sec (average): " + ((double)nDatagrams) / diffTime);
        }
    }
    
    /** 
     * Creates a ApMon-source ID that (hopefully) identifies uniquely an ApMon sender.
     * Its result is used as a key in the apMonSources hash.  
     */
	private Long buildApMonSenderUID(InetAddress ipAddress, int instanceID){
		byte [] ipBytes = ipAddress.getAddress();
		long uid  =  ((ipBytes[0] & 0xffL) << 0)
					|((ipBytes[1] & 0xffL) << 8)
					|((ipBytes[2] & 0xffL) << 16)
					|((ipBytes[3] & 0xffL) << 24)
					|((instanceID & 0xffffffffL) << 32);
		return new Long(uid);
	}
	
	/**
	 * Update the variables related to the monitoring of ApMon senders;
	 * go through the list of ApMon senders and remove the expired ones.
	 */
	private void checkApMonSenders(){
        monitorApMonSenders = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.modules.monXDRUDP.MONITOR_SENDERS", "false")).booleanValue();
        senderExpireTime = Long.valueOf(AppConfig.getProperty("lia.Monitor.modules.monXDRUDP.SENDER_EXPIRE_TIME", "900")).longValue() * 1000;
        if(monitorApMonSenders){
        	// check expired senders;
        	long now = NTPDate.currentTimeMillis();
        	int activeSenders = 0;
        	for(Iterator asit = apMonSenders.values().iterator(); asit.hasNext(); ){
        		ApMonSender as = (ApMonSender) asit.next();
        		if(as.isExpired(now)){
        			logger.log(Level.INFO, "Removing expired "+as.toString());
        			asit.remove();
        		}else{
        			activeSenders++;
        		}
        	}
        	if(logger.isLoggable(Level.FINE))
        		logger.log(Level.FINE, "Monitoring "+activeSenders+" active ApMon senders.");
        }else{
        	apMonSenders.clear();
        }
	}
    
    static public void main ( String [] args ) {
        String host = "localhost" ; //args[0] ;
        XDRUDP aa = new XDRUDP();
        String ad = null ;
        try {
            ad = InetAddress.getByName( host ).getHostAddress();
        } catch ( Exception e ) {
            System.out.println ( " Can not get ip for node " + e );
            System.exit(-1);
        }
        
        MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), null);
	
	aa.bAppendIPToNodeName = true;
        
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
