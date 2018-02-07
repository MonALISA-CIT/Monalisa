/**
 * 
 */
package lia.util.dns;

import java.io.IOException;
import java.net.InetAddress;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Zone;

/**
 * Simple example for building a zone with weighted round-robin response
 * 
 * @author costing
 * @since Jun 22, 2007
 */
public class DNSServerTest {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		final DNSServer dns = DNSServer.getInstance();
		
		// host name for which we can answer
		// host name for which we can answer
		final Name name = Name.fromConstantString("alien-proxy.cern.ch.");
		final Name myName = Name.fromConstantString("pcalice295.cern.ch.");
		final Name adminName = Name.fromConstantString("grigoras.cern.ch.");
		
		// add some basic records
		final Record[] zoneRecords = new Record[2];
		
		zoneRecords[0] = new SOARecord(name, DClass.IN, 1, myName, adminName, System.currentTimeMillis()/1000, 5, 5, 5, 5);
		zoneRecords[1] = new NSRecord(name, DClass.IN, 5, myName);
		
		final Zone zone = new Zone(name, zoneRecords);

		// create some weighted records
		final WeightedRRSet rr = new WeightedRRSet();
		
		final Record r1 = new ARecord(name, DClass.IN, 5, InetAddress.getByName("1.2.3.4")); 
		final Record r2 = new ARecord(name, DClass.IN, 5, InetAddress.getByName("2.3.4.5"));
		final Record r3 = new ARecord(name, DClass.IN, 5, InetAddress.getByName("3.4.5.6"));
		
		rr.addRR(r1, 1d);
		rr.addRR(r2, 2d);
		rr.addRR(r3, 3d);
		
		// put the weighted round-robin structure in the zone
		zone.addRRset(rr);
	
		// make the DNS server aware of this zone
		dns.addZone(name, zone);
	}
	
}
