//package lia.Monitor.modules;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.snmpMon;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Vector;
import org.opennms.protocols.snmp.SnmpVarBind;

// Improvements:
// - Instead of relying on the right data being at a particular Oid,
//   walk the entire extTable looking for an extension named 'getdiskio'.

public class snmp_DiskIO extends snmpMon implements MonitoringModule {

  static public String ModuleName = "snmp_DiskIO";
  static public String OsName = "linux";
  static public String[] ResTypes = { "DiskReadRate", "DiskWriteRate" };
  static public String getdiskioOid = ".1.3.6.1.4.1.2021.8.128.101";

  public long prevBytesRead;
  public long prevBytesWrit;
  public long timePrevRun;
  public double diskReadRate;
  public double diskWriteRate;

  public snmp_DiskIO() {
    super(getdiskioOid, ModuleName);
    info.ResTypes = ResTypes;
    info.name = ModuleName;
    prevBytesRead = 0;
    prevBytesWrit = 0;
    diskReadRate = 0;
    diskWriteRate = 0;
  }

  public String getOsName() {
    return OsName;
  }

  public String[] ResTypes() {
    return ResTypes;
  }

	// If successful, diskReadRate and diskWriteRate will be updated
	// with the current disk IO rates.
  boolean processScriptOutput(String value) {
		// The string that JoeSNMP returns contains a lot of garbage. The actual
		// SNMP return string is at the end of the line, in brackets.
		String output = value.substring(value.lastIndexOf('[')+1, value.lastIndexOf(']'));

    // Make sure the output looks the way it's supposed to
    if(!output.matches("\\d+ \\d+")) {
	if ( Node != null) 
 	     	System.out.println("The output of the getdiskio script isn't right" +Node.getName());
	else
		System.out.println("The output of the getdiskio script isn't right!! Node nulll!!!" );
      return false;
    }

    // Okay, the data is good, now we have to process it
    String[] fields = output.split(" ");
    long bytesRead = Long.parseLong(fields[0]);
    long bytesWrit = Long.parseLong(fields[1]);

    // If the operating system's IO counters rollover, we can just skip
    // this run and start over on the next one.
    long diffRead = bytesRead - prevBytesRead;
    long diffWrit = bytesWrit - prevBytesWrit;

    System.out.println("1) BytesRead = " + bytesRead + " || Prev BytesRead = " + prevBytesRead + " DIFF = " + diffRead );
    System.out.println("1) BytesWrite = " + bytesWrit + " || Prev BytesWrit = " + prevBytesWrit + " DIFF = " + diffWrit);
    
    
    if ( diffRead < 0 ) {
     diffRead = ( (long)(Long.MAX_VALUE) - prevBytesRead ) + bytesRead;
    }

    if ( diffWrit < 0 ) {
     diffWrit = ( (long)(Long.MAX_VALUE) - prevBytesWrit ) + bytesWrit;
    }

    System.out.println("2) BytesRead = " + bytesRead + " || Prev BytesRead = " + prevBytesRead + " DIFF = " + diffRead );
    System.out.println("2) BytesWrite = " + bytesWrit + " || Prev BytesWrit = " + prevBytesWrit + " DIFF = " + diffWrit);

    if( diffRead < 0 || diffWrit < 0 ) {
      System.out.println("Return FALSE!!!!!");
      return false;
    }

    // If this is the first time the script has been run, we can't
    // very well calculate the _rate_ of disk IO.
    if(prevBytesRead == 0 && prevBytesWrit == 0) {
      prevBytesRead = bytesRead;
      prevBytesWrit = bytesWrit;
      timePrevRun = System.currentTimeMillis();
      System.out.println("Return FALSE!!!!!");
      return false;
    }

    // Otherwise, we can just do a d(bytes read)/d(time) to get the IO rates
    long timeThisRun = System.currentTimeMillis();
//    double M = 1024.0;
    double M = 1.0;
	double dt = (double)(timeThisRun - timePrevRun);
    diskReadRate = (double) (diffRead)/M/(dt*2);
    diskWriteRate = (double) (diffWrit)/M/(dt*2);

    // And update the prev variables for the next run
    prevBytesRead = bytesRead;
    prevBytesWrit = bytesWrit;

    return true;
  }


  public Object doProcess() {

    String snmp_return = null;

    Vector vblist = results();
    Enumeration e = vblist.elements();
    while(e.hasMoreElements()) {
      SnmpVarBind vb = (SnmpVarBind) e.nextElement();
      if(vb.getName().toString().startsWith(getdiskioOid))
        snmp_return = vb.getValue().toString();
    }

    // If we weren't able to get data, return null
    if(snmp_return == null || !processScriptOutput(snmp_return)) {
	if( Node != null )
	      System.out.println("Couldn't get SNMP disk IO data for " + Node.getName() );
	else
	      System.out.println("Couldn't get SNMP disk IO data !!! Node == null!!!");
      return null;
    }

    // Otherwise, fill out a Result
    Result r = new Result();
    r.ClusterName = getClusterName();
    r.FarmName = getFarmName();
    r.Module = ModuleName;
    r.NodeName = Node.getName();
    r.param = new double[] { diskReadRate, diskWriteRate };
    r.param_name = ResTypes;
    r.time = System.currentTimeMillis();
    
    timePrevRun = r.time;

    return r;
  }

  static public void main(String[] args) {
    String host;
    String addr = null;
    if(args.length < 1)
      host = "localhost";
    else
      host = args[0];

    try {
      addr = InetAddress.getByName(host).getHostAddress();
    }
    catch(Exception e) {
      System.out.println("Could not get IP address for " + host + ": " + e);
      System.exit(-1);
    }

    snmp_DiskIO dio = new snmp_DiskIO();
    dio.init(new MNode(host, addr, null, null), null);

    try {
      while(true) {
        Result r = (Result) dio.doProcess();
        System.out.println(r);
        Thread.sleep(1000);
      }
    }
    catch(Exception e) {
      System.out.println("snmp_DiskIO.doProcess() choked: " + e);
      System.exit(-1);
    }

  }

}
