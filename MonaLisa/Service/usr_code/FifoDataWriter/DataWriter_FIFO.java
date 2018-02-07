import java.io.*;
import java.util.*;
import java.lang.Object;
import java.text.DateFormat;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;


public  class DataWriter_FIFO implements lia.Monitor.monitor.DataReceiver  {

  private String FIFOFileName = "firsttry";
  private int count = 0; 
  private long FIFOModTime = 0;
  private StringBuffer resultBuffer;
  private FIFO_Writer dwFIFO;  

  public DataWriter_FIFO() throws IOException {

      dwFIFO = new FIFO_Writer();
  }
  
  public void addResult ( eResult r ) {
  }
  
  public void addResult ( Result r ) {

    System.out.println ("Count: " + count + "-------------------------------");
    System.out.println ( "WR ===" + r );
    String outputstring = r.toString();
    File outputFile=null;    
     
    count++;

    outputstring += '\n'; 
 
    //outputFile = new File(FIFOFileName);
   
    //if(outputFile.exists())   
    //  {               
         dwFIFO.addResult(outputstring);
     // }  // if(outputFile.exists())  
  }  //public void addResult

  public void updateConfig(lia.Monitor.monitor.MFarm f) {
  
  }

}

