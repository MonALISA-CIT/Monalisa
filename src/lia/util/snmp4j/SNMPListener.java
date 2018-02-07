package lia.util.snmp4j;

import org.snmp4j.PDU;
import org.snmp4j.smi.Address;

/**
 * Interface that should be implemented by classes that need to be inform of TRAP and INFORM 
 * SNMP messages... use in conjuction with SNMPFactory.run command
 */
public interface SNMPListener {

	/** Whenever a new SNMP message comes this method will be called */
	public void newPDU(Address peerAddress, PDU pdu);
	
} // end of class SNMPListener

