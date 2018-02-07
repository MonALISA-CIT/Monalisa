package lia.searchdaemon.comm;

import java.net.Socket;
import java.util.Vector;

import lia.Monitor.monitor.tcpConn;
import lia.Monitor.monitor.tcpConnNotifier;

public class XDRAgentComm extends XDRAbstractComm implements tcpConnNotifier {

	private final tcpConn objectConn ;
	
	private Vector<SearchMsg> msgs ;
	private Object msgsSync = new Object ();
	
	
	private XDRAgentComm (String myName, Socket s,  XDRMessageNotifier notifier) throws Exception {
		super (myName, null, null, notifier);
		msgs = new Vector<SearchMsg>();
		objectConn = tcpConn.newConnection(this, s);
	} // XDRAgentComm
	
	public static final XDRAgentComm newInstance(String myName, Socket s,  XDRMessageNotifier notifier) throws Exception {
	    final XDRAgentComm retVal = new XDRAgentComm(myName, s, notifier);
	    if(retVal != null && retVal.objectConn != null) {
	        retVal.objectConn.startCommunication();
	    } else {
	        throw new NullPointerException("Null connection");
	    }
	   
	    return retVal;
	}
	
	public XDRMessage read() {
		
		Object readMsg = null ;
		XDRMessage rez ;
		
		synchronized (msgsSync) {
			while (msgs.size() == 0) {
				try {
					msgsSync.wait ();
				} catch (Throwable t){}	
			} // while
		} // synchronized
		
		synchronized (msgs) {
			readMsg = msgs.get(0);
			msgs.remove(0);
		} // synchronized
		
		rez = new XDRMessage ();
		// TODO - fill the ressage with the SearchMsg fields
		
		return rez;
	} // read

	
	public void close() {
		objectConn.close_connection();
	} // close

	
	public void write(XDRMessage msg) {

		// transform in an SearchMsg
		// write on the tcp
		// objectConn.sendMsg(searchMsg)
		
		SearchMsg wr = new SearchMsg();
		
		// TODO - fill the fields from the wr object with the ones from xdr message mgs
		
		objectConn.sendMsg (wr);
		
	} // write


	public void notifyMessage(Object o) {
		if (!(o instanceof SearchMsg)) {
			return;
		} // if
		
		synchronized (msgsSync) {
			msgs.add((SearchMsg)o);
			msgsSync.notify();
		} // synchronized	
		
		
	} // notifyMessage

	public void notifyConnectionClosed() {
		notifier.notifyXDRCommClosed(this);
	} // notifyConnectionClosed
	
	

} // XDRAgentComm
