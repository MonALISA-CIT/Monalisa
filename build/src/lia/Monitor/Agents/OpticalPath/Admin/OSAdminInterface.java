package lia.Monitor.Agents.OpticalPath.Admin;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface OSAdminInterface extends Remote {

    /**
     * 
     * @param portName
     * @param newSignalType - the new signal for portName
     * @return A self-parsing String ( with the OK/ERROR code )
     */
    public String changePortState(String portName, String newSignalType) throws RemoteException;

    /**
     * 
     * @param sPort - Source Port
     * @param dPort - Destination Port
     * @param connParams - can be null ... used for future devel ( e.g. specify whether the conn is using WBAND, OBAND etc)
     * @param fullDuplex - wheter to make the connection full-duplex, or not
     * @return A self-parsing String ( with the OK/ERROR code )
     */
    public String connectPorts(String sPort, String dPort, String connParams, boolean fullDuplex) throws RemoteException;
    
    /**
     * 
     * @param sPort - Source Port
     * @param dPort - Destination Port
     * @param connParams - can be null ... used for future devel ( e.g. specify whether the conn is using WBAND, OBAND etc)
     * @param fullDuplex - wheter to make the connection full-duplex, or not
     * @return A self-parsing String ( with the OK/ERROR code )
     */
    public String disconnectPorts(String sPort, String dPort, String connParams, boolean fullDuplex) throws RemoteException;
    
	/**
	 * 
	 * @param eqptID The id of the eqpt
	 * @param ip The new ip
	 * @param mask The net mask
	 * @param gw The gw address
	 * @return
	 * @throws RemoteException
	 */
	public String changeNPPort(String eqptID, String ip, String mask, String gw) throws RemoteException;
	
	/**
	 * 
	 * @param routerID The router ID
	 * @param areaID OPSF Area
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
    public String changeOSPF(String routerID, String areaID) throws RemoteException;
	
	/**
	 * 
	 * @param msgRetryInvl Message retry interval attempts of the same RSVP message
	 * @param ntfRetryInvl Notification retry interval
	 * @param grInvl Gracefull interval to refresh the states to the neighbors
	 * @param grcInvl Gracefull recovery interval
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String changeRSVP(String msgRetryInvl, String ntfRetryInvl, String grInvl, String grcInvl) throws RemoteException;
	
	/**
	 * 
	 * @param name The name of the control channel
	 * @param remoteIP The IP address of the remote end
	 * @param remoteRid Remote peer ID
	 * @param port The associated NP port 
	 * @param adj Name of the associated adjacency
	 * @param helloIInvl The time between the hello packets
	 * @param helloInvlMin The minimum time between two hello packets
	 * @param helloInvlMax The maximum time between two hello packets
	 * @param deadInvl The time before a neighbor interface is declared down
	 * @param deadInvlMin Minimum time for deadInvl
	 * @param deadInvlMax Maximum time for deadInvl
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String addCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl, 
			String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax) throws RemoteException;
	
	/**
	 * 
	 * @param name The name of the control channel
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String delCtrlCh(String name) throws RemoteException;
	
	/**
	 * 
	 * @param name The name of the control channel
	 * @param remoteIP The IP address of the remote end
	 * @param remoteRid Remote peer ID
	 * @param port The associated NP port 
	 * @param adj Name of the associated adjacency
	 * @param helloIInvl The time between the hello packets
	 * @param helloInvlMin The minimum time between two hello packets
	 * @param helloInvlMax The maximum time between two hello packets
	 * @param deadInvl The time before a neighbor interface is declared down
	 * @param deadInvlMin Minimum time for deadInvl
	 * @param deadInvlMax Maximum time for deadInvl
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String changeCtrlCh(String name, String remoteIP, String remoteRid, String port, String adj, String helloInvl, 
			String helloInvlMin, String helloInvlMax, String deadInvl, String deadInvlMin, String deadInvlMax) throws RemoteException;

	/**
	 * 
	 * @param name The name of the adjancency
	 * @param ctrlCh The name of the associated control channels (list separated by &)
	 * @param remoteRid Peer router IP address
	 * @param ospfArea The OSPF Area
	 * @param metric The administrative cost
	 * @param ospfAdj OSPF Adjancency flag (Y for enabled)
	 * @param adjType The type of the adjancency
	 * @param rsvpRRFlag The refresh reduction flag
	 * @param rsvpGRFlag The gracefull reduction flag
	 * @param ntfProc Processing notifications
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String addAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj, String adjType, String rsvpRRFlag, 
			String rsvpGRFlag, String ntfProc) throws RemoteException;
	
	/**
	 * 
	 * @param name The name of the adjancency
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String deleteAdj(String name) throws RemoteException;
    
	/**
	 * 
	 * @param name The name of the adjancency
	 * @param ctrlCh The name of the associated control channels (list separated by &)
	 * @param remoteRid Peer router IP address
	 * @param ospfArea The OSPF Area
	 * @param metric The administrative cost
	 * @param ospfAdj OSPF Adjancency flag (Y for enabled)
	 * @param adjType The type of the adjancency
	 * @param rsvpRRFlag The refresh reduction flag
	 * @param rsvpGRFlag The gracefull reduction flag
	 * @param ntfProc Processing notifications
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String changeAdj(String name, String ctrlCh, String remoteRid, String ospfArea, String metric, String ospfAdj, String adjType, String rsvpRRFlag, 
			String rsvpGRFlag, String ntfProc) throws RemoteException;

	/**
	 * 
	 * @param name The name of the link
	 * @param localIP The local ip address
	 * @param remoteIP The remote ip address
	 * @param adj The associated adj name
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String addLink(String name, String localIP, String remoteIP, String adj) throws RemoteException;

	/**
	 * 
	 * @param name The name of the TE Link
	 * @return A self-parsing String ( with the OK/ERROR code )
	 * @throws RemoteException
	 */
	public String delLink(String name) throws RemoteException;
	
	public String changeLink(String name, String localIP, String remoteIP, String linkType, String adj, String wdmAdj, 
			String remoteIf, String wdmRemoteIf, String lmpVerify, String fltDetect, String metric, String port) throws RemoteException;
	
    /**
     * 
     * @param src
     * @param dest
     * @param isFDX
     * @return
     * @throws RemoteException
     */
    public String makeMLPathConn(String src, String dest, boolean isFDX) throws RemoteException;

    
    public String deleteMLPathConn(String olID) throws RemoteException;
    
}
