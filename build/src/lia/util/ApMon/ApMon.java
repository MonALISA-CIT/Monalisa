/**
 * \file ApMon.java
 * This file contains the implementations of the methods from the ApMon class.
 */

/*
 * ApMon - Application Monitoring Tool
 * Version: 1.4
 *
 * Copyright (C) 2004 California Institute of Technology
 *
 * Permission is hereby granted, free of charge, to use, copy and modify 
 * this software and its documentation (the "Software") for any
 * purpose, provided that existing copyright notices are retained in 
 * all copies and that this notice is included verbatim in any distributions
 * or substantial portions of the Software. 
 * This software is a part of the MonALISA framework (http://monalisa.cacr.caltech.edu).
 * Users of the Software are asked to feed back problems, benefits,
 * and/or suggestions about the software to the MonALISA Development Team
 * (developers@monalisa.cern.ch). Support for this software - fixing of bugs,
 * incorporation of new features - is done on a best effort basis. All bug
 * fixes and enhancements will be made available under the same terms and
 * conditions as the original software,

 * IN NO EVENT SHALL THE AUTHORS OR DISTRIBUTORS BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
 * OF THE USE OF THIS SOFTWARE, ITS DOCUMENTATION, OR ANY DERIVATIVES THEREOF,
 * EVEN IF THE AUTHORS HAVE BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 * THE AUTHORS AND DISTRIBUTORS SPECIFICALLY DISCLAIM ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. THIS SOFTWARE IS
 * PROVIDED ON AN "AS IS" BASIS, AND THE AUTHORS AND DISTRIBUTORS HAVE NO
 * OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS.
 */

package lia.util.ApMon;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;


/**
 * Data structure used for sending monitoring data to a MonaLisa module. 
 * The data is packed in UDP datagrams, in XDR format.    
 * A datagram has the following structure:
 * - header which contains the ApMon version and the password for the MonALISA
 * host and has the following syntax: v:<ApMon_version>p:<password> 
 * - cluster name (string) 
 * - node name (string)
 * - number of parameters (int)
 * - for each parameter: name (string), value type (int), value
 * <BR>
 * There are two ways to send parameters:
 * 1) a single parameter in a packet (with the function sendParameter() which
 * has several overloaded variants
 * 2) multiple parameters in a packet (with the function sendParameters())
 */
public class ApMon {
    
    public static final String APMON_VERSION = "1.3";
    public static boolean VERBOSE = AppConfig.getb("ApMon.verbose", false); /**< Enables the printing of debugging messages */   
    public static int MAX_DGRAM_SIZE = 8192;  /**< Maximum UDP datagram size. */
    
    public static final int XDR_STRING  = 0;  /**< Used to code the string data type */

    public static final int XDR_INT32 =  2;  /**< Used to code the 4 bytes integer data type */

    public static final int XDR_REAL32 =  4;  /**< Used to code the 4 bytes real data type */
    public static final int XDR_REAL64 =  5;  /**< Used to code the 8 bytes real data type */
    public static int DEFAULT_PORT = 8884; /**< The default port on which MonALISA listens */

    /** Constant that indicates this object was initialized from a file. */
    static final int FILE_INIT = 1;
     /** Constant that indicates this object was initialized from a list. */
    static final int LIST_INIT = 2;
     /** Constant that indicates this object was initialized directly. */
    static final int DIRECT_INIT = 3;

    /** If this flag is true, the configuration file / URLs are periodically
     * rechecked for changes. */
    private boolean confCheck = false;
    /** The initialization source (can be a file or a list). */
    Object initSource = null;
    /* The initialization type (from file / list / directly). */
    int initType;

     /** The configuration file and the URLs are checked for changes at 
      * this numer of seconds (if the network connections are good). */
    private long recheckInterval = 300;

    /** If the configuraion URLs cannot be reloaded, the interval until 
     * the next attempt will be increased. This is the actual value
     * of the interval that is used by ApMon.
     */
    private long crtRecheckInterval = 300;
    
    private String clusterName; /**< The name of the monitored cluster. */
    private String nodeName; /**< The name of the monitored node. */ 

    private Vector destAddresses; /**< The IP addresses where the results will be sent.*/
    private Vector destPorts; /**< The ports where the destination hosts listen. */
    private Vector destPasswds; /**< The Passwdords used for the destination hosts. */

    private byte []buf; /**< The buffer which holds the message data (encoded in XDR). */
    private int dgramSize; /**< The size of the data inside the datagram */

    /** Hashtable which holds the  initialization resources (Files, URLs) 
     * that must be periodically checked for changes, and their latest 
     * modification times.
     */
    Hashtable confResources; 
   
    private ByteArrayOutputStream baos;
    
    private DatagramSocket dgramSocket;

    private ConfReloader confReloader;

    private boolean reloaderStarted = false;

    /**
     * Initializes an ApMon object from a configuration file.
     * @param filename The name of the file which contains the addresses and
     * the ports of the destination hosts (see README for details about
     * the structure of this file).
     * @throws ApMonException, SocketException, IOException
     */
    public ApMon(String filename) throws ApMonException, SocketException, IOException {

    	initType = FILE_INIT;
	initSource = filename;
	initialize(filename, true);
    }

    /**
     * Initializes an ApMon object from a configuration file.
     * @param filename The name of the file which contains the addresses and
     * the ports of the destination hosts (see README for details about
     * the structure of this file).
     * @param firstTime If it is true, all the initializations will be done (the
     * object is being constructed now). Else, only some structures will be reinitialized.
     * @throws ApMonException, SocketException, IOException
     */

    void initialize(String filename, boolean firstTime) 
	throws ApMonException, SocketException, IOException {
    	Vector destAddresses = new Vector();
    	Vector destPorts = new Vector();
    	Vector destPasswds = new Vector();

	Hashtable confRes = new Hashtable();
	try {
	    loadFile(filename, destAddresses, destPorts, destPasswds, confRes);
	} catch (Exception e) {
	    if (firstTime) {
		if (e instanceof IOException)
		    throw (IOException)e;
		if (e instanceof ApMonException)
		    throw (ApMonException)e;
	    }
	    else {
		//System.err.println("Configuration not reloaded successfully, keeping the previous one");
		return;
	    }
	}

	synchronized(this) {
	    arrayInit(destAddresses, destPorts, destPasswds, firstTime);
	    this.confResources = confRes;
	}
    }

    /**
     * Initializes an ApMon object from a list with URLs.
     * @param initSource The list with URLs.
     * the ports of the destination hosts (see README for details about
     * the structure of this file).
     * @throws ApMonException, SocketException, IOException
     */
    public ApMon(Vector destList) throws ApMonException, SocketException, IOException {
	initType = LIST_INIT;
	initSource = destList;
	initialize(destList, true);
    }
    
    /**
     * Initializes an ApMon object from a list with URLs.
     * @param initSource The list with URLs.
     * @param firstTime If it is true, all the initializations will be done 
     * (the object is being constructed now). Else, only some structures will 
     * be reinitialized.
     * @throws ApMonException, SocketException, IOException
     */

    void initialize(Vector destList, boolean firstTime) 
	throws ApMonException, SocketException, IOException {	
	int i;
	Vector destAddresses = new Vector();
	Vector destPorts = new Vector();
	Vector destPasswds = new Vector();

    	String dest;

    	Hashtable confRes = new Hashtable();
    	if (VERBOSE)
    		System.out.println("Initializing destination addresses & ports:");
	try {
		for (i = 0; i < destList.size(); i++) {
			dest = (String)destList.get(i);
			if (dest.startsWith("http")) { // get the list from a remote location
			    loadURL(dest, destAddresses, destPorts, 
				    destPasswds, confRes);
			} else { // the destination address & port are given directly
			    addToDestinations(dest, destAddresses, destPorts, destPasswds);
			}
		}

	} catch (Exception e) {
	    if (firstTime) {
		if (e instanceof IOException)
		    throw (IOException)e;
		if (e instanceof ApMonException)
		    throw (ApMonException)e;
		if (e instanceof SocketException)
		    throw (SocketException)e;
	    } else {
		//System.err.println("Configuration not reloaded successfully, keeping the previous one");
		return;
	    }
	}

	synchronized(this) {
	    arrayInit(destAddresses, destPorts, destPasswds, firstTime);
	    this.confResources = confRes;
	}
    }
    
    /**
     * Initializes an ApMon data structure, using arrays instead of a file.
     * @param nDestinations The number of destination hosts where the results will
     * be sent.
     * @param destAddresses Array that contains the hostnames or IP addresses 
     * of the destination hosts.
     * @param destPorts The ports where the MonaLisa modules listen on the 
     * destination hosts.
     * @throws ApMonException, SocketException, IOException
     *
     */
    public ApMon(Vector destAddresses, Vector destPorts) 
	throws ApMonException, SocketException, IOException {
	this.initType = DIRECT_INIT;
	arrayInit(destAddresses, destPorts, null);
    }

    /**
     * Initializes an ApMon data structure, using arrays instead of a file.
     * @param nDestinations The number of destination hosts where the results will
     * be sent.
     * @param destAddresses Array that contains the hostnames or IP addresses 
     * of the destination hosts.
     * @param destPorts The ports where the MonaLisa modules listen on the 
     * destination hosts.
     * @param destPasswds The passwords for the destination hosts.
     * @throws ApMonException, SocketException, IOException
     *
     */
    public ApMon(Vector destAddresses, Vector destPorts, Vector destPasswds) 
	throws ApMonException, SocketException, IOException {
	this.initType = DIRECT_INIT;
	arrayInit(destAddresses, destPorts, destPasswds);
    }
    

    /**
     * Parses a configuration file which contains addresses, ports and 
     * passwords for the destination hosts and puts the results in the
     * vectors given as parameters.
     * @param filename The name of the configuration file. 
     * @param destAddresses Will contain the destination addresses.
     * @param destPorts Will contain the ports from the destination hosts. 
     * @param destPasswds Will contain the passwords for the destination hosts.
     * @param confRes Will contain the configuration resources (file, URLs).
     * @throws IOException, ApMonException
     */
    private void loadFile(String filename, Vector destAddresses, Vector 
			  destPorts, Vector destPasswds, Hashtable confRes) 
    throws IOException, ApMonException {
	String line, tmp;
	BufferedReader in = new BufferedReader(new FileReader(filename));
	
	confRes.put(new File(filename), 
			  Long.valueOf(NTPDate.currentTimeMillis()));
	
    	/* initializations for the destination addresses */
    	if (VERBOSE)
	    System.out.println("Loading file " + filename + "...");

    	/* parse the input file */
    	while((line = in.readLine()) != null) {
	    tmp = line.trim();
	    // skip empty lines & comment lines
	    if (tmp.length() == 0 || tmp.startsWith("#")) 
		continue;	
	    
	    if (tmp.startsWith("http")) { // get the list from a remote location
    		loadURL(tmp, destAddresses, destPorts, destPasswds, confRes);
	
	    } else { // the destination address & port are given directly
		addToDestinations(tmp, destAddresses, destPorts, destPasswds);
	    }
    	}
	
	in.close();
    }


    /**
     * Parses a web page which contains addresses, ports and 
     * passwords for the destination hosts and puts the results in the
     * vectors given as parameters.
     * @param destAddresses Will contain the destination addresses.
     * @param destPorts Will contain the ports from the destination hosts. 
     * @param destPasswds Will contain the passwords for the destination hosts.
     * @param confRes Will contain the configuration resources (file, URLs).
     */
    private void loadURL(String url, Vector destAddresses, Vector destPorts, 
			Vector destPasswds, Hashtable confRes) 
    throws IOException, ApMonException {
	URL destURL = null;
	try {
	    destURL = new URL(url);
	} catch (MalformedURLException e) {
	    throw new ApMonException(e.getMessage());
	}
	
	URLConnection urlConn = destURL.openConnection();
	long lmt = urlConn.getLastModified();
	confRes.put(new URL(url), Long.valueOf(lmt));

	if (VERBOSE)
	    System.out.println("Loading from URL " + url + "...");
	BufferedReader br = new BufferedReader(
		new InputStreamReader(destURL.openStream()));

	String destLine;
	while ((destLine = br.readLine()) != null) {
	    String tmp2 = destLine.trim();
	    // skip empty lines & comment lines
	    if (tmp2.length() == 0 || tmp2.startsWith("#")) 
		continue;
					
	    if (tmp2.startsWith("http"))
		loadURL(tmp2, destAddresses, destPorts, destPasswds, confRes);
	    else
		addToDestinations(tmp2, destAddresses, destPorts, 
				  destPasswds);
	}
	br.close();
    }

    /**
     * Parses a line from a (local or remote) configuration file and adds the address
     * and the port to the vectors that are given as parameters.
     * @param line The line to be parsed.
     * @param destAddresses Contains destination addresses.
     * @param destPorts Contains the ports from the destination hosts. 
     * @param destPasswds Contains the passwords for the destination hosts.
     */
    private void addToDestinations(String line, Vector destAddresses, 
		         Vector destPorts, Vector destPasswds) {
	String addr;
	int port;
	
	String tokens[] = line.split("(\\s)+");
	String passwd = "";
	
	if (tokens == null)
	    return; // skip blank lines

	line = tokens[0].trim();
	if (tokens.length > 1)  // a password was provided
	    passwd = tokens[1].trim();

	
	/* the address and the port are separated with ":" */
	StringTokenizer st = new StringTokenizer(line, ":");
	addr = st.nextToken();
	if (st.hasMoreTokens())
	    port = Integer.parseInt(st.nextToken());
	else 
	    port = DEFAULT_PORT;
	
	destAddresses.add(addr);
	destPorts.add(Integer.valueOf(port));
	if (passwd != null)
	    destPasswds.add(passwd);
    }

    /**
     * Internal method used to initialize an ApMon data structure.
     * @param nDestinations The number of destination hosts where the results will
     * be sent.
     * @param destAddresses Array that contains the hostnames or IP addresses 
     * of the destination hosts.
     * @param destPorts The ports where the MonaLisa modules listen on the 
     * destination hosts.
     * @param destPasswds The passwords for the destination hosts.
     * @throws ApMonException, SocketException, IOException
     */
    private void arrayInit(Vector destAddresses, Vector destPorts, Vector destPasswds) 
	throws ApMonException, SocketException, IOException {
	arrayInit(destAddresses, destPorts, destPasswds, true);
    }


     /**
     * Internal method used to initialize an ApMon data structure.
     * @param nDestinations The number of destination hosts where the results will
     * be sent.
     * @param destAddresses Array that contains the hostnames or IP addresses 
     * of the destination hosts.
     * @param destPorts The ports where the MonaLisa modules listen on the 
     * destination hosts.
     * @param destPasswds The passwords for the destination hosts.
     * @param firstTime If it is true, all the initializations will be done
     * (the object is being constructed now). Else, only some of the data
     * structures will be reinitialized.
     * @throws ApMonException, SocketException, IOException
     */
    private void arrayInit(Vector destAddresses, Vector destPorts, 
			   Vector destPasswds, boolean firstTime) 
	throws ApMonException, SocketException, IOException {
    	int i;
    	int optval, ret;

    	if (destAddresses.size() == 0 || destPorts.size() == 0)
    		throw new ApMonException("No destination hosts specified");
    	
	this.destPorts = new Vector();
	this.destAddresses = new Vector();
	this.destPasswds = new Vector();
	
	for (i = 0; i < destAddresses.size();  i++) {
	    InetAddress inetAddr = InetAddress.getByName((String)destAddresses.get(i));
	    String ipAddr = inetAddr.getHostAddress();
		
	    /* add the new destination only if it doesn't already
	       exist in this.destAddresses */
	    if (!this.destAddresses.contains(ipAddr)) {
		this.destAddresses.add(ipAddr);
		this.destPorts.add(destPorts.get(i));
		if (destPasswds != null) {
		    this.destPasswds.add(destPasswds.get(i));
		}
		if (VERBOSE)
		    System.out.println(ipAddr + " - " + destPorts.get(i));
	    }
	}
    

	/* these should be done only the first time the function is called */
	if (firstTime) {
	
	    dgramSocket = new DatagramSocket();
	
	    System.setProperty("sun.net.client.defaultConnectTimeout", 
			   "5000");
	    System.setProperty("sun.net.client.defaultReadTimeout", 
			   "5000");
	
	    try {
		baos = new ByteArrayOutputStream();
	    } catch(Throwable t){
		t.printStackTrace();
		throw new ApMonException("Got General Exception while encoding:" + t);
	    }
	}
    }
    
    /**
     * Sends several parameters and thier values to the MonALISA module. 
     * @param clusterName The name of the cluster that is monitored.
     * @param nodeName The name of the node from the cluster from which the 
     * value was taken.
     * @param paramNames Vector with the names of the parameters.
     * @param valueTypes Vector with the value types of the parameters. The
     * values can be XDR_INT32 (integer), XDR_REAL32 (float), XDR_REAL64 
     *(double) or XDR_STRING (null-terminated string).
     * @param paramValues Vector with the values of the parameters.
     * @throws ApMonException, UnknownHostException, SocketException
     */
    public void sendParameters(String clusterName, String nodeName,
	       int nParams, Vector paramNames, Vector valueTypes, 
			Vector paramValues) throws ApMonException, UnknownHostException,
			SocketException, IOException {
	int i;
	
	if (clusterName != null) { // don't keep the cached values for cluster name
	    // and node name
	    this.clusterName = new String(clusterName);

	    if (nodeName != null)  /* the user provided a name */
		this.nodeName = new String(nodeName);
	    else { /* set the node name to the node's IP */
		try {
		    InetAddress inetAddr = InetAddress.getLocalHost();
		    this.nodeName = inetAddr.getHostAddress();
		} catch (UnknownHostException e) {
		    this.nodeName = "unknown_node";
		}
		
	    } // else
	    
	} // if
     


	/* try to encode the parameters */
	encodeParams(nParams, paramNames, valueTypes, paramValues);
  

	synchronized (this) {
	    /* for each destination */
	    for (i = 0; i < destAddresses.size(); i++) {
		InetAddress destAddr = InetAddress.getByName((String)destAddresses.get(i));
		int port = ((Integer)destPorts.get(i)).intValue();
		
		String header = "v:"+APMON_VERSION+"_jp:";
		String passwd = "";
		if (destPasswds != null && destPasswds.size() == destAddresses.size()) {
		    passwd = (String)destPasswds.get(i);
		}
		header += passwd;
	    
		byte[] newBuff = null;
		try {
		    XDROutputStream xdrOS = new XDROutputStream(baos);
		    
		    xdrOS.writeString(header);
		    xdrOS.pad();
		    
		    xdrOS.flush();
		    byte[] tmpbuf = baos.toByteArray();
		    baos.reset();
    	    
		    newBuff = new byte[tmpbuf.length + buf.length]; 
		    System.arraycopy(tmpbuf, 0, newBuff, 0, tmpbuf.length);
		    System.arraycopy(buf, 0, newBuff, tmpbuf.length, buf.length);
    	    
		}catch(Throwable t) {
		    System.err.println("Cannot add header...."+t);
		    newBuff = buf;
		}
		
		if (newBuff == null || newBuff.length == 0) {
		    System.err.println("Cannot send null or 0 length buffer!!");
	        continue;
		}
		
		dgramSize = newBuff.length;
		DatagramPacket dp = new DatagramPacket(newBuff, dgramSize, destAddr,
						   port);
		try {
		    dgramSocket.send(dp);
		} catch (IOException e) {
		    System.err.println("Error sending parameters to " + 
				       destAddresses.get(i));
		    dgramSocket.close();
		    dgramSocket = new DatagramSocket();
		}
	
		if (VERBOSE)
		    System.out.println("[" + new Date() + "] Datagram with size " + dgramSize +
				       " sent to " +  destAddresses.get(i));
	    }
	} // synchronized


    }

    /**
     * Sends a parameter and its value to the MonALISA module. 
     * @param clusterName The name of the cluster that is monitored. If it is
     * NULL, we keep the same cluster and node name as in the previous datagram.
     * @param nodeName The name of the node from the cluster from which the 
     * value was taken.
     * @param paramName The name of the parameter.
     * @param valueType The value type of the parameter. Can be one of the 
     * constants XDR_INT32 (integer),  XDR_REAL64 (double),
     * XDR_STRING.
     * @param paramValue The value of the parameter.
     * @throws ApMonException, UnknownHostException, SocketException
     */
    public void sendParameter(String clusterName, String nodeName,
			String paramName, int valueType, Object paramValue) 
 	throws ApMonException, UnknownHostException, SocketException, IOException {
    	Vector paramNames = new Vector();
    	paramNames.add(paramName);
    	Vector valueTypes = new Vector();
    	valueTypes.add(Integer.valueOf(valueType));
    	Vector paramValues = new Vector();
    	paramValues.add(paramValue);

    	sendParameters(clusterName, nodeName, 1, paramNames, 
			      valueTypes, paramValues);
    	}

    /**
     * Sends an integer parameter and its value to the MonALISA module. 
     * @param clusterName The name of the cluster that is monitored. If it is
     * NULL, we keep the same cluster and node name as in the previous datagram.
     * @param nodeName The name of the node from the cluster from which the 
     * value was taken.
     * @param paramName The name of the parameter.
     * @param paramValue The value of the parameter.
     * @throws ApMonException, UnknownHostException, SocketException 
	  */
    public void sendParameter(String clusterName, String nodeName,
		String paramName, int paramValue) 
	throws ApMonException, UnknownHostException, SocketException, IOException {
    	sendParameter(clusterName, nodeName, paramName, XDR_INT32, 
		    Integer.valueOf(paramValue));
    }

    /**
     * Sends a parameter of type double and its value to the MonALISA module. 
     * @param clusterName The name of the cluster that is monitored. If it is
     * NULL,we keep the same cluster and node name as in the previous datagram.
     * @param nodeName The name of the node from the cluster from which the 
     * value was taken.
     * @param paramName The name of the parameter.
     * @param paramValue The value of the parameter.
     * @throws ApMonException, UnknownHostException, SocketException
     */
    public void sendParameter(String clusterName, String nodeName,
		String paramName, double paramValue)
	throws ApMonException, UnknownHostException, SocketException, IOException {
    	
    	sendParameter(clusterName, nodeName, paramName, XDR_REAL64, 
		    Double.valueOf(paramValue));
    }

    /**
     * Sends a parameter of type string and its value to the MonALISA module. 
     * @param clusterName The name of the cluster that is monitored. If it is
     * NULL, we keep the same cluster and node name as in the previous datagram.
     * @param nodeName The name of the node from the cluster from which the 
     * value was taken.
     * @param paramName The name of the parameter.
     * @param paramValue The value of the parameter.
     * @throws ApMonException, UnknownHostException, SocketException
     */
    public void sendParameter(String clusterName, String nodeName,
		String paramName, String paramValue) 
	throws ApMonException, UnknownHostException, SocketException, IOException {
    	
    	sendParameter(clusterName, nodeName, paramName, XDR_STRING, 
		    paramValue);
    }

    /**
     * Checks that the size of the stream does not exceed the maximum size
     * of an UDP diagram.
     */
    private void ensureSize(ByteArrayOutputStream baos) throws ApMonException {
        if (baos == null) throw new ApMonException("Null ByteArrayOutputStream");
		if (baos.size() > MAX_DGRAM_SIZE)
	    	throw new ApMonException("Maximum datagram size exceeded");
    }
    
	/**
	   * Encodes in the XDR format the data from a ApMon structure. Must be 
	   * called before sending the data over the newtork.
	   *  @throws ApMonException
	   */
    private void encodeParams(int nParams, Vector paramNames, 
            Vector valueTypes, Vector paramValues) 
    throws ApMonException {
        int i, valType;
        try {

	    XDROutputStream xdrOS = new XDROutputStream(baos);
            
            /* encode the cluster name, the node name and the number of parameters */
            ensureSize(baos);
            xdrOS.writeString(clusterName);
	    xdrOS.pad();
            xdrOS.writeString(nodeName);
	    xdrOS.pad();
            xdrOS.writeInt(nParams);
	    xdrOS.pad();

            /* encode the parameters */
            for (i = 0; i < nParams; i++) {
                
                /* parameter name */
                xdrOS.writeString((String)paramNames.get(i));
                
                /* parameter value type */
                valType = ((Integer)valueTypes.get(i)).intValue();
                xdrOS.writeInt(valType);
                
                /* parameter value */
                switch (valType) {
                case XDR_STRING:
                    xdrOS.writeString((String)paramValues.get(i));
                    break;
                    //INT16 is not supported
                case XDR_INT32:
                    int ival = ((Integer)paramValues.get(i)).intValue();
                    xdrOS.writeInt(ival);
                    break;
                    // REAL32 is not supported
                case XDR_REAL64:
                    //newOffset = offset + XDRUtils.REAL64_LEN;
                    double dval = ((Double)paramValues.get(i)).doubleValue();
                    xdrOS.writeDouble(dval);
                    break;
                default:
                    throw new ApMonException("Unknown type for XDR encoding");
                }
		
		xdrOS.pad();
            }//end for()

	    ensureSize(baos);
	    xdrOS.flush();
            buf = baos.toByteArray();
	    baos.reset();
	    if (VERBOSE)
		System.out.println("Setting buff [ " + buf.length + " ] ");
        } catch(Throwable t){
	    t.printStackTrace();
            throw new ApMonException("Got General Exception while encoding:" + t);
        }
        
    }

   
    /**
     * Returns the value of the confCheck flag. If it is true, the 
     * configuration file and/or the URLs are periodically checked for
     * modifications.
     */
    public boolean getConfCheck() {
	return confCheck;
    }

     /**
     * Sets the value of the confCheck flag. If it is true, the 
     * configuration file and/or the URLs are periodically checked for
     * modifications. By default it is false.
     * The user cannot call this function directly. The function 
     * setRecheckInterval() should be used instead.
     */
    synchronized void setConfCheck(boolean val) {
	confCheck = val;

	if (val == true && !reloaderStarted) {
	    reloaderStarted = true;
	    confReloader = new ConfReloader(this);
	    confReloader.setDaemon(true);
	    confReloader.start();
	}

	if (val == false && reloaderStarted) {
	    confReloader.stopIt();
	    reloaderStarted = false;
	}
    }

    /**
     * Returns the requested value of the time interval (in ms) between 
     * two recheck operations for the configuration files.
     */
    public long getRecheckInterval() {
	return recheckInterval;
    }

    /**
     * Returns the actual value of the time interval (in seconds) between two 
     * recheck operations for the configuration files.
     */
    long getCrtRecheckInterval() {
	return crtRecheckInterval;
    }

    /**
     * Sets the value of the time interval (in seconds) between two recheck 
     * operations for the configuration files. The default value is 5min.
     * If the value is negative, the configuration rechecking is turned off.
     */
    public void setRecheckInterval(long val) {
	recheckInterval = val;
	crtRecheckInterval = val;
	if (val > 0)
	    setConfCheck(true);
	else
	    setConfCheck(false);
    }

    void setCrtRecheckInterval(long val) {
	crtRecheckInterval = val;
    }

    /**
     * Closes the UDP socket used for sending the parameters. Must be called
     * when the ApMon object is no longer in use.
     */
    public void stopIt() {
    	dgramSocket.close();
    }
}

