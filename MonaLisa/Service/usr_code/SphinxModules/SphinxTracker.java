/*
 * @(#)SphinxTracker.java        1.0 10/25/2003
 *
 *
 * Change History:
 *
 * Action		Date			Owner					Comments
 * ==============================================================================================
 * Create		Oct 25 2003	Laukik Chitnis			Created (reference:$MonALISA/Service/usr_code/LSFModules/)
 *
 *
 */



import lia.Monitor.monitor.*;

import java.io.*;
import java.util.*;
import java.net.InetAddress;
    

/**
 * SphinxTracker Class, This class is used for getting the unfinished job information on the MonALISA GUI
 *
 * @author 	Laukik Chitnis
 * @version 	1.0 25 Oct 2003
 * @See     $MonALISA/Service/<farm-name>/<conf-file-name>
 */
    
public class SphinxTracker extends cmdExec implements  MonitoringModule
{   
	     
	/** array of parameter names*/     
	static String[] tmetric={"Unfinished"}; /* I am tracking only one parameter right now */
	     
	/** shell command */
	String cmd ;

	/** command line arguments*/
	String args;
	    
	/** constructor */
	public SphinxTracker () 
	{   
	    super( "SphinxTracker");
	    info.ResTypes = tmetric;
	    System.out.println ( "Start the Interface to  SphinxTracker Unfinished jobs Monitoring module" );
	    isRepetitive = true;
	}

	/** initialization method 
	  * 
	  * @return MonModuleInfo
	  */
	public  MonModuleInfo init( MNode Node , String args )
	{
	    this.Node = Node;
	    info.ResTypes = tmetric;
	    
	    /* I am not using args here 
	     * But I need to pass some parameter to my command line script like the path to a directory
	     * I believe this can be done by passing that path as args here, right?
	     * This is the same args string that is passed from the user_arguments string in the .conf file, right?
	     * *SphinxJOBS{SphinxTracker,ufgrid.phys.ufl.edu,user_arguments}%60 
	     */

	    //this.args = args;
	    this.args = "";

	    cmd = "echo 5" ;    /* using a dummy command instead of the real script I am using*/
							/* Like my original script, this will output just one number */
	    System.out.println ( " CMD   = " + cmd ) ; 
	    System.out.println ( " ARGS  = " + args );

	    return info;
	}   
	    
	/** This method return the object containing the values of monitored parameters
	  *
	  * @return Object
	  *
	  */
	public Object   doProcess() throws Exception
	{
	    BufferedReader buff1 = procOutput ( cmd );
	    
	    if ( buff1  == null ) 
	    {   
			System.out.println ( " Failed  to get the  SphinxTracker output " );
			if ( pro != null ) pro.destroy();
			throw new Exception ( " SphinxTracker load  output  is null for " + Node.name);
	    }   
	    
	    return Parse(buff1);
	}


	//public Vector   Parse (  BufferedReader buff )  throws Exception  {

	/** This method return the Result object containing the values of monitored parameters
	  *
	  * @return Result
	  * @param buff BufferedReader, the input stream containing the result of running the command
	  *
	  */
	public Result Parse (  BufferedReader buff )  throws Exception
	{

	    Result rr = null ;
	    rr = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), null, tmetric );
	    rr.time = (new Date()).getTime();
	    String line;

	    try
	    {
			line = buff.readLine();
			System.out.println ("Got this in Parse : " + line );
			rr.param[0] = Integer.parseInt(line.trim());
			buff.close();
			System.out.println ( " Close Buffer " );
			if ( pro != null ) pro.destroy();
	    }
	    catch ( Exception e )
	    {
			if ( pro != null ) pro.destroy();
			System.out.println ( "Exception in Parsing SphinxTracker output : "  + e );
			e.printStackTrace();
			throw e;
	    }

	    return rr;

	    /* Should Parse return a Result or a Result wrapped in a Vector? */
	}

	/** This method is used for getting the info
	  *
	  * @return MonModuleInfo
	  *
	  */
	public MonModuleInfo getInfo()
	{
	    return info;
	}


	/** This method is used for getting the ResTypes
	  *
	  * @return String[]
	  *
	  */
	public String[] ResTypes ()
	{
	    return tmetric;
	}


	/** This method is used for getting the OS name
	  *
	  * @return String
	  *
	  */
	public String getOsName()
	{
	    return "linux";
	}


	/** This main method is used for testing purposes
	  *
	  * @return void
	  *
	  */
	public static void main ( String [] args )
	{

	    String host = "localhost" ;
	    SphinxTracker aa = new SphinxTracker ();
	    String ad = null ;

	    try
	    {
			ad = InetAddress.getByName( host ).getHostAddress();
	    }
	    catch ( Exception e )
	    {
			System.out.println ( " Can not get ip for node " + e );
			System.exit(-1);
	    }

	    MonModuleInfo info = aa.init( new MNode (host ,ad,  null, null), args[0]);

	    try
	    {
			Object bb = aa.doProcess();
			if ( bb instanceof Result )
			{
				System.out.println ( " R ->" + (Result)bb ) ;
			}
	    }
	    catch ( Exception e )
	    {
			System.out.println ( " failed to process " );
	    }



	}


}



                            