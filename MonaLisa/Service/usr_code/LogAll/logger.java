import java.io.*;
import java.util.*;
import java.lang.Object;
import java.text.*;
import lia.Monitor.monitor.*;
import java.text.NumberFormat;



public class logger implements Runnable, lia.Monitor.monitor.DataReceiver  {

  Vector buffer = new Vector();
  Vector buffer1;
  int lost_values=0; 
  Calendar calen;

  String year;
  String month;
  String day;
  String cur_date ;

//  String Path="fullLog";
   String Path="/raid3/iosif/";

  SimpleDateFormat dateform = new SimpleDateFormat( " HH:mm:ss" );
  NumberFormat nf ; 


public logger( )  {
    calen = Calendar.getInstance();
    nf = NumberFormat.getInstance();
    nf.setMaximumFractionDigits(3);
    nf.setMinimumFractionDigits(1);
    nf.setGroupingUsed(false);

    (new Thread(this)).start();
}

public void cale () {
//  calen = Calendar.getInstance();
  calen.setTime( new Date());
  year = ""+calen.get (Calendar.YEAR) ;
  int mx = (calen.get(Calendar.MONTH )+1);
  if ( mx <=9 )
   month = "0"+ mx;
  else
   month = ""+mx;
  int id = calen.get(Calendar.DAY_OF_MONTH);

  if ( id <=9 )
    day = "0"+ id;
  else
    day = ""+id;

  int hh = (calen.get(Calendar.HOUR_OF_DAY ));
  cur_date =year+"_"+month+"_"+day ;
//  cur_date =year+"_"+month+"_"+day+"*"+hh ;


  
}


public void run() {
   while ( true ) {
    cale(); 
    try {(Thread.currentThread()).sleep( 60000); } catch (Exception e) {}
     //System.out.println ( "running..." );
    if ( buffer.size() > 10 ) tryToWrite();
   }
}
synchronized public void addResult ( Result a) {
   buffer.add( a );
}

synchronized public void addResult ( eResult a) {

}

synchronized public void addResult ( ExtResult a) {

}

public void updateConfig(lia.Monitor.monitor.MFarm f) {
}

void tryToWrite() {
   File myFile = new File ( Path +cur_date) ; 
   FileOutputStream out = null; 

   try { 
         System.out.println ( " try to create out stream " );
         out = new FileOutputStream(myFile, true);
   } catch ( Exception e ) {
        System.out.println ( " Failed to create the io streams " );
        return;
   }
   synchronized ( buffer) {
     buffer1 = buffer ;
     buffer = new Vector();
   }

   System.out.println ( " Start writing " + buffer1.size() + " values "   );
   while ( buffer1.size() > 0 ) {
     Object aa = buffer1.remove(0);
     String output_str =  rformat ( aa ) ; 
         if (output_str != null ) { 
          try {
                  out.write(output_str.getBytes());
          }
          catch (Exception e)
          {
                System.out.println ("IO error to write");
          }
        }
   } 
 
   //close the stream
   try {
         out.flush();
         out.close();
   }
   catch (Exception e)
       {
         System.out.println ("IO error to write");
   }  

   out = null;
   myFile = null;

 } 

String rformat ( Object o ) {
 if ( o instanceof  Result ) { 
  return rformat ( (Result ) o ) ;
 }
 
 if ( o instanceof String ) {
   return (String) o + " \n" ;
 }

 return o.toString() + "\n";

}
 
String rformat ( Result r ) {
  String ans =  "";
  if ( r.ClusterName.equals("MonaLisa") ) return null;
//  if ( r.ClusterName.equals("Internet")) return null;
  long time = r.time;
  Date da = new Date ( time) ;
  String date = dateform.format(da);
  for ( int i=0; i < r.param_name.length ;i++ ) {
    //ans += date + "  " + r.ClusterName + "  "+ r.NodeName+ "  " + r.param_name[i] + " "+ nf.format(r.param[i]) + "\n"; 
     ans += date + "  " + r.NodeName+ "  " + r.param_name[i] + " "+ nf.format(r.param[i]) + "\n";

  }

 return ans;
}

   static public void main ( String [] args ) {

        int count =0;
        logger  l = new logger();
      
        while (count<5000)
        {
           // l.addResult( new Integer(count));
            count++;
            try{Thread.currentThread().sleep(100); }catch(Exception e) {}
        }
   }

}

