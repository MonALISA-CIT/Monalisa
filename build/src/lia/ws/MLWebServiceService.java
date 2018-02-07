/**
 * MLWebServiceService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package lia.ws;

public interface MLWebServiceService extends javax.xml.rpc.Service {
    public java.lang.String getMLWebServiceAddress();

    public lia.ws.MLWebService getMLWebService() throws javax.xml.rpc.ServiceException;

    public lia.ws.MLWebService getMLWebService(java.net.URL portAddress) throws javax.xml.rpc.ServiceException;
}
