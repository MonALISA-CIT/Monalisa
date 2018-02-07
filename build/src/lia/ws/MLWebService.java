/**
 * MLWebService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package lia.ws;

/**
 * 
 * @author mickyt
 *
 */
public interface MLWebService extends java.rmi.Remote {
    public lia.ws.Result[] getValues(java.lang.String in0, java.lang.String in1, java.lang.String in2, java.lang.String in3, long in4, long in5) throws java.rmi.RemoteException;
    public lia.ws.WSConf[] getConfiguration(long in0, long in1) throws java.rmi.RemoteException;
    public lia.ws.WSConf[] getLatestConfiguration(java.lang.String in0) throws java.rmi.RemoteException;
    public lia.ws.Result[] getLastValues() throws java.rmi.RemoteException;
    public lia.ws.Result[] getFilteredLastValues(java.lang.String in0, java.lang.String in1, java.lang.String in2, java.lang.String in3) throws java.rmi.RemoteException;
    public java.util.HashMap networkMeasurementSet (java.util.HashMap request) throws java.rmi.RemoteException;
} // interface MLWebService
