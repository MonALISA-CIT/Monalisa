/**
 * 
 */
package lia.util.dns;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import lia.Monitor.monitor.AppConfig;

import org.xbill.DNS.Address;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DNAMERecord;
import org.xbill.DNS.ExtendedFlags;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.NameTooLongException;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.TSIGRecord;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferException;

/**
 * Copyright (c) 1999-2004 Brian Wellington (bwelling@xbill.org)
 * 
 * @author Brian Wellington &lt;bwelling@xbill.org&gt;
 * @author costing
 * @since Jun 22, 2007
 */
public class DNSServer {
	private static final int	FLAG_DNSSECOK	= 1;
	private static final int	FLAG_SIGONLY	= 2;
	private Map					caches;
	private Map					znames;
	private Map					TSIGs;

	private static String addrport(InetAddress addr, int port) {
		return addr.getHostAddress() + "#" + port;
	}

	/**
	 * ML-dedicated initialization
	 * 
	 * @param prop
	 */
	private DNSServer(){
		caches = new HashMap();
		znames = new HashMap();
		TSIGs = new HashMap();
		
		final List ports = new ArrayList();
		final List addresses = new ArrayList();
		
		final String[] vsPorts = AppConfig.getVectorProperty("lia.util.dns.DNSServer.ports");
		
		for (int i=0; vsPorts!=null && i<vsPorts.length; i++)
			try{
				ports.add(Integer.valueOf(vsPorts[i]));
			}
			catch (Exception e){
				// ignore
			}
		
		final String[] vsAddresses = AppConfig.getVectorProperty("lia.util.dns.DNSServer.bind");
		
		for (int i=0; vsAddresses!=null && i<vsAddresses.length; i++)
			try{
				addresses.add(Address.getByAddress(vsAddresses[i]));
			}
			catch (Exception e){
				// ignore
			}

		bindAddrPorts(ports, addresses);
	}
	
	/**
	 * Constructor based on a configuration file
	 * 
	 * @param conffile
	 * @throws IOException
	 * @throws ZoneTransferException
	 */
	private DNSServer(String conffile) throws IOException, ZoneTransferException {
		FileInputStream fs;
		List ports = new ArrayList();
		List addresses = new ArrayList();
		try {
			fs = new FileInputStream(conffile);
		} catch (Exception e) {
			System.out.println("Cannot open " + conffile);
			return;
		}

		try {
			caches = new HashMap();
			znames = new HashMap();
			TSIGs = new HashMap();

			InputStreamReader isr = new InputStreamReader(fs);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			while ((line = br.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				if (!st.hasMoreTokens())
					continue;
				String keyword = st.nextToken();
				if (!st.hasMoreTokens()) {
					System.out.println("Invalid line: " + line);
					continue;
				}
				if (keyword.charAt(0) == '#')
					continue;
				if (keyword.equals("primary"))
					addPrimaryZone(st.nextToken(), st.nextToken());
				else if (keyword.equals("secondary"))
					addSecondaryZone(st.nextToken(), st.nextToken());
				else if (keyword.equals("cache")) {
					Cache cache = new Cache(st.nextToken());
					caches.put(Integer.valueOf(DClass.IN), cache);
				} else if (keyword.equals("key")) {
					String s1 = st.nextToken();
					String s2 = st.nextToken();
					if (st.hasMoreTokens())
						addTSIG(s1, s2, st.nextToken());
					else
						addTSIG("hmac-md5", s1, s2);
				} else if (keyword.equals("port")) {
					ports.add(Integer.valueOf(st.nextToken()));
				} else if (keyword.equals("address")) {
					String addr = st.nextToken();
					addresses.add(Address.getByAddress(addr));
				} else {
					System.out.println("unknown keyword: " + keyword);
				}

			}

			bindAddrPorts(ports, addresses);

			System.out.println("jnamed: running");
		} finally {
			fs.close();
		}
	}

	private void bindAddrPorts(List ports, List addresses){
		if (ports.size() == 0)
			ports.add(Integer.valueOf(53));

		if (addresses.size() == 0){
			try{
				addresses.add(Address.getByAddress("0.0.0.0"));
			}
			catch (Exception e){
				// ignore
			}
		}
			
		Iterator iaddr = addresses.iterator();
		while (iaddr.hasNext()) {
			InetAddress addr = (InetAddress) iaddr.next();
			Iterator iport = ports.iterator();
			while (iport.hasNext()) {
				int port = ((Integer) iport.next()).intValue();
				addUDP(addr, port);
				addTCP(addr, port);
				System.out.println("DNSServer: listening on " + addrport(addr, port));
			}
		}		
	}
	
	/**
	 * Add a primary zone
	 * 
	 * @param zname
	 * @param zonefile
	 * @throws IOException
	 */
	public void addPrimaryZone(String zname, String zonefile) throws IOException {
		Name origin = null;
		if (zname != null)
			origin = Name.fromString(zname, Name.root);
		Zone newzone = new Zone(origin, zonefile);
		
		addZone(newzone.getOrigin(), newzone);
	}

	/**
	 * Add a secondary zone
	 * 
	 * @param zone
	 * @param remote
	 * @throws IOException
	 * @throws ZoneTransferException
	 */
	public void addSecondaryZone(String zone, String remote) throws IOException, ZoneTransferException {
		Name zname = Name.fromString(zone, Name.root);
		Zone newzone = new Zone(zname, DClass.IN, remote);
		
		addZone(zname, newzone);
	}

	/**
	 * Add some zone
	 * 
	 * @param name
	 * @param zone
	 */
	public void addZone(final Name name, final Zone zone){
		znames.put(name, zone);
	}
	
	/**
	 * @param algstr
	 * @param namestr
	 * @param key
	 * @throws IOException
	 */
	public void addTSIG(String algstr, String namestr, String key) throws IOException {
		Name name = Name.fromString(namestr, Name.root);
		TSIGs.put(name, new TSIG(algstr, namestr, key));
	}

	/**
	 * @param dclass
	 * @return the cache
	 */
	public Cache getCache(int dclass) {
		Cache c = (Cache) caches.get(Integer.valueOf(dclass));
		if (c == null) {
			c = new Cache(dclass);
			caches.put(Integer.valueOf(dclass), c);
		}
		return c;
	}

	/**
	 * @param name
	 * @return the best zone
	 */
	public Zone findBestZone(Name name) {
		Zone foundzone = null;
		foundzone = (Zone) znames.get(name);
		if (foundzone != null)
			return foundzone;
		int labels = name.labels();
		for (int i = 1; i < labels; i++) {
			Name tname = new Name(name, i);
			foundzone = (Zone) znames.get(tname);
			if (foundzone != null)
				return foundzone;
		}
		return null;
	}

	/**
	 * @param name
	 * @param type
	 * @param dclass
	 * @param glue
	 * @return the exact match, if any
	 */
	public RRset findExactMatch(Name name, int type, int dclass, boolean glue) {
		Zone zone = findBestZone(name);
		if (zone != null)
			return zone.findExactMatch(name, type);
		else {
			RRset[] rrsets;
			Cache cache = getCache(dclass);
			if (glue)
				rrsets = cache.findAnyRecords(name, type);
			else
				rrsets = cache.findRecords(name, type);
			if (rrsets == null)
				return null;
			else
				return rrsets[0]; /* not quite right */
		}
	}

	private void addRRset(Name name, Message response, RRset rrset, int section, int flags) {
		for (int s = 1; s <= section; s++)
			if (response.findRRset(name, rrset.getType(), s))
				return;
		if ((flags & FLAG_SIGONLY) == 0) {
			Iterator it = rrset.rrs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
		if ((flags & (FLAG_SIGONLY | FLAG_DNSSECOK)) != 0) {
			Iterator it = rrset.sigs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
	}

	private final void addSOA(Message response, Zone zone) {
		response.addRecord(zone.getSOA(), Section.AUTHORITY);
	}

	private final void addNS(Message response, Zone zone, int flags) {
		RRset nsRecords = zone.getNS();
		addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);
	}

	private final void addCacheNS(Message response, Cache cache, Name name) {
		SetResponse sr = cache.lookupRecords(name, Type.NS, Credibility.HINT);
		if (!sr.isDelegation())
			return;
		RRset nsRecords = sr.getNS();
		Iterator it = nsRecords.rrs();
		while (it.hasNext()) {
			Record r = (Record) it.next();
			response.addRecord(r, Section.AUTHORITY);
		}
	}

	private void addGlue(Message response, Name name, int flags) {
		RRset a = findExactMatch(name, Type.A, DClass.IN, true);
		if (a == null)
			return;
		addRRset(name, response, a, Section.ADDITIONAL, flags);
	}

	private void addAdditional2(Message response, int section, int flags) {
		Record[] records = response.getSectionArray(section);
		for (int i = 0; i < records.length; i++) {
			Record r = records[i];
			Name glueName = r.getAdditionalName();
			if (glueName != null)
				addGlue(response, glueName, flags);
		}
	}

	private final void addAdditional(Message response, int flags) {
		addAdditional2(response, Section.ANSWER, flags);
		addAdditional2(response, Section.AUTHORITY, flags);
	}

	private byte addAnswer(Message response, Name name, int type, int dclass, int iterations, int flags) {
		SetResponse sr;
		byte rcode = Rcode.NOERROR;

		if (iterations > 6)
			return Rcode.NOERROR;

		if (type == Type.SIG || type == Type.RRSIG) {
			type = Type.ANY;
			flags |= FLAG_SIGONLY;
		}

		Zone zone = findBestZone(name);
		if (zone != null)
			sr = zone.findRecords(name, type);
		else {
			Cache cache = getCache(dclass);
			sr = cache.lookupRecords(name, type, Credibility.NORMAL);
		}

		if (sr.isUnknown()) {
			addCacheNS(response, getCache(dclass), name);
		}
		if (sr.isNXDOMAIN()) {
			response.getHeader().setRcode(Rcode.NXDOMAIN);
			if (zone != null) {
				addSOA(response, zone);
				if (iterations == 0)
					response.getHeader().setFlag(Flags.AA);
			}
			rcode = Rcode.NXDOMAIN;
		} else if (sr.isNXRRSET()) {
			if (zone != null) {
				addSOA(response, zone);
				if (iterations == 0)
					response.getHeader().setFlag(Flags.AA);
			}
		} else if (sr.isDelegation()) {
			RRset nsRecords = sr.getNS();
			addRRset(nsRecords.getName(), response, nsRecords, Section.AUTHORITY, flags);
		} else if (sr.isCNAME()) {
			CNAMERecord cname = sr.getCNAME();
			RRset rrset = new RRset(cname);
			addRRset(name, response, rrset, Section.ANSWER, flags);
			if (zone != null && iterations == 0)
				response.getHeader().setFlag(Flags.AA);
			rcode = addAnswer(response, cname.getTarget(), type, dclass, iterations + 1, flags);
		} else if (sr.isDNAME()) {
			DNAMERecord dname = sr.getDNAME();
			RRset rrset = new RRset(dname);
			addRRset(name, response, rrset, Section.ANSWER, flags);
			Name newname;
			try {
				newname = name.fromDNAME(dname);
			} catch (NameTooLongException e) {
				return Rcode.YXDOMAIN;
			}
			rrset = new RRset(new CNAMERecord(name, dclass, 0, newname));
			addRRset(name, response, rrset, Section.ANSWER, flags);
			if (zone != null && iterations == 0)
				response.getHeader().setFlag(Flags.AA);
			rcode = addAnswer(response, newname, type, dclass, iterations + 1, flags);
		} else if (sr.isSuccessful()) {
			RRset[] rrsets = sr.answers();
			for (int i = 0; i < rrsets.length; i++)
				addRRset(name, response, rrsets[i], Section.ANSWER, flags);
			if (zone != null) {
				addNS(response, zone, flags);
				if (iterations == 0)
					response.getHeader().setFlag(Flags.AA);
			} else
				addCacheNS(response, getCache(dclass), name);
		}
		return rcode;
	}

	private byte[] doAXFR(Name name, Message query, TSIG tsig, TSIGRecord qtsig, Socket s) {
		Zone zone = (Zone) znames.get(name);
		boolean first = true;
		if (zone == null)
			return errorMessage(query, Rcode.REFUSED);
		Iterator it = zone.AXFR();
		try {
			DataOutputStream dataOut;
			dataOut = new DataOutputStream(s.getOutputStream());
			int id = query.getHeader().getID();
			while (it.hasNext()) {
				RRset rrset = (RRset) it.next();
				Message response = new Message(id);
				Header header = response.getHeader();
				header.setFlag(Flags.QR);
				header.setFlag(Flags.AA);
				addRRset(rrset.getName(), response, rrset, Section.ANSWER, FLAG_DNSSECOK);
				if (tsig != null) {
					tsig.applyStream(response, qtsig, first);
					qtsig = response.getTSIG();
				}
				first = false;
				byte[] out = response.toWire();
				dataOut.writeShort(out.length);
				dataOut.write(out);
			}
		} catch (IOException ex) {
			System.out.println("AXFR failed");
		}
		try {
			s.close();
		} catch (IOException ex) {
			// ignore
		}
		return null;
	}

	/*
	 * Note: a null return value means that the caller doesn't need to do anything. Currently this
	 * only happens if this is an AXFR request over TCP.
	 */
	private byte[] generateReply(Message query, byte[] in, int length, Socket s) throws IOException {
		Header header;
		boolean badversion;
		int maxLength;
		boolean sigonly;
		SetResponse sr;
		int flags = 0;

		header = query.getHeader();
		if (header.getFlag(Flags.QR))
			return null;
		if (header.getRcode() != Rcode.NOERROR)
			return errorMessage(query, Rcode.FORMERR);
		if (header.getOpcode() != Opcode.QUERY)
			return errorMessage(query, Rcode.NOTIMP);

		Record queryRecord = query.getQuestion();

		TSIGRecord queryTSIG = query.getTSIG();
		TSIG tsig = null;
		if (queryTSIG != null) {
			tsig = (TSIG) TSIGs.get(queryTSIG.getName());
			if (tsig == null || tsig.verify(query, in, length, null) != Rcode.NOERROR)
				return formerrMessage(in);
		}

		OPTRecord queryOPT = query.getOPT();
		if (queryOPT != null && queryOPT.getVersion() > 0)
			badversion = true;

		if (s != null)
			maxLength = 65535;
		else if (queryOPT != null)
			maxLength = Math.max(queryOPT.getPayloadSize(), 512);
		else
			maxLength = 512;

		if (queryOPT != null && (queryOPT.getFlags() & ExtendedFlags.DO) != 0)
			flags = FLAG_DNSSECOK;

		Message response = new Message(query.getHeader().getID());
		response.getHeader().setFlag(Flags.QR);
		if (query.getHeader().getFlag(Flags.RD))
			response.getHeader().setFlag(Flags.RD);
		response.addRecord(queryRecord, Section.QUESTION);

		Name name = queryRecord.getName();
		int type = queryRecord.getType();
		int dclass = queryRecord.getDClass();
		if (type == Type.AXFR && s != null)
			return doAXFR(name, query, tsig, queryTSIG, s);
		if (!Type.isRR(type) && type != Type.ANY)
			return errorMessage(query, Rcode.NOTIMP);

		byte rcode = addAnswer(response, name, type, dclass, 0, flags);
		if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN)
			return errorMessage(query, rcode);

		addAdditional(response, flags);

		if (queryOPT != null) {
			int optflags = (flags == FLAG_DNSSECOK) ? ExtendedFlags.DO : 0;
			OPTRecord opt = new OPTRecord((short) 4096, rcode, (byte) 0, optflags);
			response.addRecord(opt, Section.ADDITIONAL);
		}

		response.setTSIG(tsig, Rcode.NOERROR, queryTSIG);
		return response.toWire(maxLength);
	}

	private byte[] buildErrorMessage(Header header, int rcode, Record question) {
		Message response = new Message();
		response.setHeader(header);
		for (int i = 0; i < 4; i++)
			response.removeAllRecords(i);
		if (rcode == Rcode.SERVFAIL)
			response.addRecord(question, Section.QUESTION);
		header.setRcode(rcode);
		return response.toWire();
	}

	/**
	 * @param in
	 * @return error message
	 */
	public byte[] formerrMessage(byte[] in) {
		Header header;
		try {
			header = new Header(in);
		} catch (IOException e) {
			return null;
		}
		return buildErrorMessage(header, Rcode.FORMERR, null);
	}

	/**
	 * @param query
	 * @param rcode
	 * @return error message
	 */
	public byte[] errorMessage(Message query, int rcode) {
		return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
	}

	/**
	 * @param s
	 */
	public void TCPclient(Socket s) {
		try {
			int inLength;
			DataInputStream dataIn;
			DataOutputStream dataOut;
			byte[] in;

			InputStream is = s.getInputStream();
			dataIn = new DataInputStream(is);
			inLength = dataIn.readUnsignedShort();
			in = new byte[inLength];
			dataIn.readFully(in);

			Message query;
			byte[] response = null;
			try {
				query = new Message(in);
				response = generateReply(query, in, in.length, s);
				if (response == null)
					return;
			} catch (IOException e) {
				response = formerrMessage(in);
			}
			dataOut = new DataOutputStream(s.getOutputStream());
			dataOut.writeShort(response.length);
			dataOut.write(response);
		} catch (IOException e) {
			System.out.println("TCPclient(" + addrport(s.getLocalAddress(), s.getLocalPort()) + "): " + e);
		} finally {
			try {
				s.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	/**
	 * @param addr
	 * @param port
	 */
	public void serveTCP(InetAddress addr, int port) {
		try {
			ServerSocket sock = new ServerSocket(port, 128, addr);
			while (true) {
				final Socket s = sock.accept();
				Thread t;
				t = new Thread(new Runnable() {
					public void run() {
						TCPclient(s);
					}
				});
				t.start();
			}
		} catch (IOException e) {
			System.out.println("serveTCP(" + addrport(addr, port) + "): " + e);
		}
	}

	/**
	 * @param addr
	 * @param port
	 */
	public void serveUDP(InetAddress addr, int port) {
		try {
			DatagramSocket sock = new DatagramSocket(port, addr);
			final short udpLength = 512;
			byte[] in = new byte[udpLength];
			DatagramPacket indp = new DatagramPacket(in, in.length);
			DatagramPacket outdp = null;
			while (true) {
				indp.setLength(in.length);
				try {
					sock.receive(indp);
				} catch (InterruptedIOException e) {
					continue;
				}
				Message query;
				byte[] response = null;
				try {
					query = new Message(in);
					response = generateReply(query, in, indp.getLength(), null);
					if (response == null)
						continue;
				} catch (IOException e) {
					response = formerrMessage(in);
				}
				if (outdp == null)
					outdp = new DatagramPacket(response, response.length, indp.getAddress(), indp.getPort());
				else {
					outdp.setData(response);
					outdp.setLength(response.length);
					outdp.setAddress(indp.getAddress());
					outdp.setPort(indp.getPort());
				}
				sock.send(outdp);
			}
		} catch (IOException e) {
			System.out.println("serveUDP(" + addrport(addr, port) + "): " + e);
		}
	}

	/**
	 * @param addr
	 * @param port
	 */
	public void addTCP(final InetAddress addr, final int port) {
		Thread t;
		t = new Thread(new Runnable() {
			public void run() {
				serveTCP(addr, port);
			}
		});
		t.start();
	}

	/**
	 * @param addr
	 * @param port
	 */
	public void addUDP(final InetAddress addr, final int port) {
		Thread t;
		t = new Thread(new Runnable() {
			public void run() {
				serveUDP(addr, port);
			}
		});
		t.start();
	}

	private static DNSServer instance = null;
	
	/**
	 * @return the only instance of DNSServer
	 */
	public static synchronized DNSServer getInstance(){
		if (instance==null){
			try{
				instance = new DNSServer();
			}
			catch (Exception e){
				// ignore
			}
		}
		
		return instance;
	}
	
	/**
	 * test method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length > 1) {
			System.out.println("usage: jnamed [conf]");
			System.exit(0);
		}

		try {
			String conf;
			if (args.length == 1)
				conf = args[0];
			else
				conf = "jnamed.conf";
			
			new DNSServer(conf);
		} catch (IOException e) {
			System.out.println(e);
		} catch (ZoneTransferException e) {
			System.out.println(e);
		}
	}

}
