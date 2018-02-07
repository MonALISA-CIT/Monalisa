
import java.io.*;
import java.util.*;

public class VoAccounts {
static public String OsName     = "linux";
static public String ModuleName = "VoAccounts";

static public String  monalisaHome = null;
static public String  path = "/Service/usr_code/VoModules/";
//static public String  path = "/bin/";
static public String  userVoMap = "grid3-user-vo-map.txt";

static public  Hashtable unixAccts   = new Hashtable();
static public  Hashtable voAccts     = new Hashtable();
static private Hashtable voMixedCase = new Hashtable();

static private Hashtable voTotals = new Hashtable();

//==========================
public VoAccounts () { 
    // -------------------------------------
    // Determine the MonaALisa_HOME 
    // -------------------------------------
    System.out.println(ModuleName+"Instantiating VO/User map");
//    monalisaHome = System.getProperty("MonaLisa_HOME");
//    if ( monalisaHome == null ) {
//      System.out.println(ModuleName+"ERROR: MonaLisa_HOME environmental variable not set.");
//    }
//    monalisaHome = "/home/weigand/MonALISA/MonaLisa.v098";
    // --------------------------------------------
    // Load the VO to USER account mapping tables
    // --------------------------------------------
//    loadUnixTable();
    loadDefaultUnixTable();
    loadVoTable(); 
    loadVoMixedCase();
    initializeTotalsTable();
}

//======================================
public Enumeration  VoList() {
  Enumeration vl = voMixedCase.elements();
  return vl; 
}

//======================================
public void initializeTotalsTable() {
  voTotals = new Hashtable();
}

//========================================
public void updateTotals(String unixAcct, String metric) {
  updateTotals(unixAcct, metric, (String) "1" ); 
}

//========================================
public void updateTotals(String unixAcct, String metric, String value ) {
  String vo              = null;

  // -------------------------------------
  // Verify that the user belongs to a VO
  // -------------------------------------
  vo = getVo( unixAcct);
  if ( vo == null ) {
    return; // this is not a user we are interested in
  }
  updateVoTotals( vo, metric, value);
}

//========================================
public void updateVoTotals( String vo, String metric, String value) {
  Hashtable metricsTable = new Hashtable();
  Double oldvalue       =  new Double(0);
  Double newvalue       =  new Double(value);

  // ---------------------------------------------------------------
  // Create a new entry if the VO does not exist in the totals table
  // ---------------------------------------------------------------
  metricsTable = (Hashtable) getMetricsTable(vo); 
  if (metricsTable == null ) { 
    metricsTable = new Hashtable();
    metricsTable.put(metric, newvalue); 
    voTotals.put(vo, metricsTable);
    return;  // new entry made
  }
  // --------------------------
  // Check if the metric exists
  // --------------------------
  if (metricsTable.containsKey( (String) metric)) { 
    oldvalue = (Double) metricsTable.get(metric);
  }
  else {
    metricsTable.put(metric, newvalue);
    return;
  }
  // --------------------------
  // Add to the value 
  // --------------------------
  newvalue = new Double( oldvalue.doubleValue() +  newvalue.doubleValue());
  metricsTable.put(metric,newvalue);
  voTotals.put(vo,(Hashtable) metricsTable);
}

//======================================
public Hashtable getMetricsTable(String vo) {
  /* Returns a metrics hashtable for a specified VO */
  Hashtable metricsTable = null;
  if ( voTotals.containsKey( (String) vo)) { 
    metricsTable = (Hashtable) voTotals.get(vo);
  }
  return metricsTable;
}

//======================================
public Double getMetric(String vo, String metric) {
  /* Returns a specific metric for a specified VO */

  Hashtable metricsTable = new Hashtable();
  Double value = new Double(0);

  if ( voTotals.containsKey( (String) vo)) { 
    metricsTable = (Hashtable) voTotals.get(vo);
  }
  else { return value; }
  if ( metricsTable.containsKey( (String) metric)) { 
    value = (Double) metricsTable.get((String) metric);
  }
  else { return value; }

  return value;
}


//======================================
private void loadDefaultUnixTable() {
  /* This loads a default internal hard-coded set of values rather than
     retrieving them from a file.
  */
  unixAccts.put("uscms","uscms01");
  unixAccts.put("atlas","usatlas1");
  unixAccts.put("ligo","lsc01");
  unixAccts.put("ivdgl","ivdgl");
  unixAccts.put("sdss","sdss");
  unixAccts.put("btev","btev");
}

//======================================
private void loadVoMixedCase() {
  voMixedCase.put("uscms","USCMS");
  voMixedCase.put("atlas","ATLAS");
  voMixedCase.put("ligo","LIGO");
  voMixedCase.put("ivdgl","iVDgL");
  voMixedCase.put("sdss","SDSS");
  voMixedCase.put("btev","BTeV");

}
//======================================
public String getVo ( String unix ) {
  String voLower = null;
  String vo      = null;
  voLower = (String) voAccts.get(unix.toLowerCase());
  if ( voLower != null ) {
    vo = (String) voMixedCase.get(voLower);
  }
  return vo;
}

//==================================================
public String getUnixAccount ( String vo ) {
  String unix = null;
  unix = (String) unixAccts.get(vo);
  return unix;
}

//==========================
public void  printUnixTable () { 
  System.out.println ( ModuleName+": Printing vo / unix map table");
  for (Enumeration e = unixAccts.keys(); e.hasMoreElements();) {
    String voAcct   = (String) e.nextElement();
    String unixList = (String) unixAccts.get(voAcct);
    System.out.println ( ModuleName+": "+voAcct+"  "+unixList);
  }
}

//==========================
public void  printVoTable () { 
  System.out.println ( ModuleName+": Printing unix / vo  map table");
  for (Enumeration e = voAccts.keys(); e.hasMoreElements();) {
    String unix = (String) e.nextElement();
    String vo   = (String) voAccts.get(unix);
    System.out.println ( ModuleName+": "+unix+"  "+vo);
  }
}

//==========================
private void loadVoTable() {
  /*
     This takes the unixAccts table and creates a voAccts table.
  */
  for (Enumeration e = unixAccts.keys(); e.hasMoreElements();) {
    String voAcct   = (String) e.nextElement();
    String unixList = (String) unixAccts.get(voAcct);
//    for ( int i = 0; i < (int) unixList.size(); i++) {
//    voAccts.put(unixList.get(i),voAcct);
//    }
    voAccts.put(unixList,voAcct);
  }
}

//==========================
private void loadUnixTable() {
  /*
     The configuration file containing the mappings of Unix user name
     to VO name is a whitespace delimited file in the format....
        unix_user vo_name
     Comments are on a line starting with an asterick (*).
     Empty lines are ignored. 

     This method loads the file into a hash table for use in other methods.
     
     IMPORTANT:  This method loads the unix map table as a vector as there
                 can be more than 1 unix account per VO.  None of the other
                 code supports this at this time.
  */
  String filename     = null;
  String record       = null;
  String comment      = null;
  String commentValue = "#";
  String unix         = null;
  String vo           = null;
  //String[] config = new String[];
  try {
    filename = monalisaHome+"/"+path+"/"+userVoMap; 
    System.out.println ( ModuleName+": Loading vo-to-unix map table using file ("+filename+")");
    //---------------------------
    // Start processing the file
    //---------------------------
    FileReader fr     = new FileReader( filename ); 
    BufferedReader br = new BufferedReader(fr);   
    while ( (record = br.readLine()) != null) {
      if (record.length() == 0 ) { // check for empty line
         continue;
      }
      comment = record.substring( 0, 1);
      if (comment.compareTo(commentValue) == 0 ) { // check for comment
         continue;
      }
      //------------------
      // Build hash table
      //------------------
      StringTokenizer tz = new StringTokenizer (record) ;
      int ni = tz.countTokens();
      if ( ni > 1 )  {
        unix = tz.nextToken().trim();
        vo   = tz.nextToken().trim();
        Vector unixList = new Vector();
        if ( unixAccts.containsKey(vo) ) {
          unixList = (Vector) unixAccts.get(vo);
          
        }
        unixList.addElement(unix);
        unixAccts.put(vo,(Vector) unixList);
      }
    }
    br.close();
  } catch ( Exception e ) { 
      System.out.println ( ModuleName+" Error: reading mapping file - "+e);
  }
}

// --------------------------------------------------
static public void main ( String [] args ) {
  String vo = null;
  String unix = null;
  String unixAccounts = null;
  String metric = null;
  String value  = null;
  Double newvalue = new Double(0);

  try {
    VoAccounts map = new VoAccounts();
    map.printUnixTable();
    map.printVoTable();
    //------------------
    vo = "uscms";
    unixAccounts = map.getUnixAccount(vo);
    System.out.println ( "VO: "+vo+"  Unix account: "+unixAccounts);
    //------------------
    unix = "uscms01";
    vo = map.getVo(unix);
    System.out.println ( "Unix: "+unix+"  VO: "+vo);
    //------------------
    unix = "uscms02";
    vo = map.getVo(unix);
    System.out.println ( "Unix: "+unix+"  VO: "+vo);

    //------------------
    unix = "uscms01";
    metric = "Running Jobs";
    value  = "1.03";
    map.updateTotals(unix, metric, value );
    newvalue = map.getMetric((String) "USCMS", metric);
    System.out.println ( " Value: " + newvalue.doubleValue() );
    //------------------
    unix = "uscms01";
    metric = "Running Jobs";
    value  = "100.03";
    vo = "USCMS";
    map.updateTotals(unix, metric, value );
    newvalue = map.getMetric(vo, metric);
    System.out.println ( " VO:"+vo+" Metric:"+metric+" Value: " + newvalue.doubleValue() );
    //------------------
    vo = "USCMS";
    metric = "nothing";
    newvalue = map.getMetric(vo, metric);
    System.out.println ( " VO:"+vo+" Metric: "+metric+" Value:"+ newvalue.doubleValue() );
    //------------------
    vo = "nothing";
    newvalue = map.getMetric(vo, metric);
    System.out.println ( " VO:"+vo+ " Metric:"+metric+" Value: " + newvalue.doubleValue() );
    //------------------
    unix = "uscms01";
    metric = "Running Jobs";
    value  = "100.03";
    map.initializeTotalsTable();
    newvalue = map.getMetric((String) "USCMS", metric);
    System.out.println ( " VO:"+vo+" Value: " + newvalue.doubleValue() );
    
//    acct =a.getUnixAccountFromFile( vo );
//    System.out.println ( "From file       - VO: "+vo+"  Unix account: "+acct);
  } catch ( Exception e ) {
    System.out.println ( " Unexpected error: " + e );
    System.exit(-1);
  }
 System.exit(-0);
}
    
 
}


