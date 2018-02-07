import java.io.*;
import java.util.*;
import java.lang.Object;


public class FIFO_Writer implements Runnable  {

  private String FIFOFileName = "firsttry";
  Vector buffer = new Vector();
  Vector buffer1;
  int lost_values=0; 

  public FIFO_Writer()  {

  (new Thread(this)).start();

}

public void run() {

   while ( true ) {
    try {(Thread.currentThread()).sleep( 1000); } catch (Exception e) {}
     System.out.println ( "running..." );
    if ( buffer.size() > 0 ) tryToWrite();
   }
}
public void addResult ( Object a) {

   if ( buffer.size () >= 10 ) {  //delete values ... 
    buffer.remove(0);
    lost_values++;
   }
   buffer.add( a );
}

void tryToWrite() {

   File myFile = new File ( FIFOFileName); 
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

   System.out.println ( " Start writing " + buffer1.size() + " values    Total Lost =" +lost_values );
   while ( buffer1.size() > 0 ) {
     Object aa = buffer1.remove(0);
     String output_str = (String) aa ;
     System.out.println ( output_str);
          
         //write out the result   
          try {
                  out.write(output_str.getBytes());
                  out.flush();
                  //out.close();
                  System.out.println ("-------------------- \n");
          }
          catch (Exception e)
          {
                System.out.println ("IO error to write");
          }
   } 
 
   //close the stream
   try {
         out.close();
   }
   catch (Exception e)
       {
         System.out.println ("IO error to write");
   }  

 } 


  static public void main ( String [] args ) {

        int count =0;
        FIFO_Writer dwFIFO = new FIFO_Writer();
      
        while (count<500)
        {
            dwFIFO.addResult( new Integer(count));
            count++;
            try{Thread.currentThread().sleep(500); }catch(Exception e) {}
        }
   }

}

