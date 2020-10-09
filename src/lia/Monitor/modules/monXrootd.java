package lia.Monitor.modules;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Utils;
import lazyj.cache.ExpirationCache;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

/**
 * @author ML
 */
public class monXrootd extends monGenericUDP {
	private static final long serialVersionUID = 1L;

	/** Logger used by this class */
	private static final Logger logger = Logger.getLogger(monXrootd.class.getName());

	/**
	 * if not receiving data from one xrd server, after how much time we should remove it
	 */
	private long XRDSERVER_EXPIRE = 2 * 60 * 60 * 1000; // 2 hours by default

	/**
	 * if not receiving data from one DictInfo entry, after how much time we should remove it
	 */
	private long DICTINFO_EXPIRE = 1 * 60 * 60 * 1000; // 1 hour by default

	/**
	 * Module name
	 */
	static final public String MODULE_NAME = "monXrootd";

	/**
	 * 
	 */
	static final int XROOTD_MON_OPEN = 0x80;

	/**
	 * 
	 */
	static final int XROOTD_MON_APPID = 0xa0;
	/**
	 * 
	 */
	static final int XROOTD_MON_CLOSE = 0xc0;
	/**
	 * 
	 */
	static final int XROOTD_MON_DISC = 0xd0;
	/**
	 * 
	 */
	static final int XROOTD_MON_WINDOW = 0xe0;
	/**
	 * 
	 */
	static final int XROOTD_MON_RWREQ = 0x80; // and-ed with type => 0

	/**
	 * xrootd servers that send data to this module
	 */
	final Hashtable<String, XrdServerInfo> xrdServers = new Hashtable<String, XrdServerInfo>();

	/**
	 * parameter names
	 */
	static final String[] transfResTypes = new String[] { "transf_rd_mbytes", "transf_wr_mbytes", "transf_client_ip", "transf_speed" };

	/**
	 * @author catac
	 */
	static final class XrdServerInfo {
		/**
		 * server's hostname; also key in the xrdServers hashtable
		 */
		final String host;

		/**
		 * when I received info about this object
		 */
		long lastUpdateTime;

		// summarized values
		/**
		 * total MBytes read since last report
		 */
		double rMBytes;

		/**
		 * total MBytes written since last report
		 */
		double wMBytes;

		/**
		 * no of files that were read since last report
		 */
		double rFiles;

		/**
		 * no of files that were written since last report
		 */
		double wFiles;

		/**
		 * number of currently open files
		 */
		double nrOpenFiles;

		/**
		 * number of currently connected clients
		 */
		double nrClients;

		/**
		 * the hash with connected users and opened files on this server
		 */
		final Hashtable<String, DictInfo> dictMap;

		/**
		 * 
		 */
		final Map<String, DictInfo> dictUserMap;

		/**
		 * transfer results that will be reported on doProcess
		 */
		final Hashtable<String, Result> transfResults;

		/**
		 * @param hostName
		 */
		public XrdServerInfo(final String hostName) {
			this.host = hostName;
			this.dictMap = new Hashtable<String, DictInfo>();
			this.dictUserMap = new Hashtable<String, DictInfo>();
			this.transfResults = new Hashtable<String, Result>();
		}
	}

	/**
	 * dictid mapping to a user/path combination
	 */
	static final int DI_TYPE_PATH = 1;

	/**
	 * dictid mapping to a session user/information combination
	 */
	static final int DI_TYPE_APPINFO = 2;

	/**
	 * dictid mapping to the user login name
	 */
	static final int DI_TYPE_LOGIN = 3;

	/**
	 * @author catac
	 */
	static final class DictInfo {
		/**
		 * when I received info about this object
		 */
		long lastUpdateTime;

		/**
		 * one of the DI_TYPE_... constants
		 */
		int type;

		/**
		 * the key (dictID/stod)
		 */
		String key;

		/**
		 * the user for this information
		 */
		String user;

		/**
		 * user's IP, stored as a double value
		 */
		double userIP;

		/**
		 * information string
		 */
		String info;

		@Override
		public String toString() {
			return "DI[" + this.key + "]=" + this.user + "/" + this.info;
		}
	}

	/**
	 * 
	 */
	public monXrootd() {
		super(MODULE_NAME);

		// read and written MB rates are per second
		this.resTypes = new String[] { "srv_conn_clients", "srv_open_files", "srv_rd_mbytes", "srv_wr_mbytes", "srv_rd_files", "srv_wr_files" };
		this.gPort = 9930; // default port for Xrootd monitoring UDPs
	}

	@Override
	public MonModuleInfo init(final MNode node, final String arg) {
		this.Node = node;
		init_args(arg);
		this.info = new MonModuleInfo();

		logger.log(Level.INFO, "Initializing with: ListenPort=" + this.gPort + " XrdServerExpire=" + (this.XRDSERVER_EXPIRE / 1000) + " DictInfoExpire=" + (this.DICTINFO_EXPIRE / 1000));

		try {
			this.udpLS = new GenericUDPListener(this.gPort, this, null);
		}
		catch (final Throwable tt) {
			logger.log(Level.WARNING, " Cannot create UDPListener !", tt);
		}

		this.isRepetitive = true;

		this.info.ResTypes = this.resTypes;
		this.info.name = MODULE_NAME;
		OsName = "linux";

		return this.info;
	}

	@Override
	void init_args(final String args) {

		String list = args;

		if ((list == null) || (list.length() == 0)) {
			return;
		}
		if (list.startsWith("\"")) {
			list = list.substring(1);
		}
		if (list.endsWith("\"") && (list.length() > 0)) {
			list = list.substring(0, list.length() - 1);
		}
		final String params[] = list.split("(\\s)*,(\\s)*");
		if ((params == null) || (params.length == 0)) {
			return;
		}

		for (final String param : params) {
			int itmp = param.indexOf("ListenPort");
			if (itmp != -1) {
				final String tmp = param.substring(itmp + "ListenPort".length()).trim();
				final int iq = tmp.indexOf("=");
				final String port = tmp.substring(iq + 1).trim();
				try {
					this.gPort = Integer.valueOf(port).intValue();
				}
				catch (final Throwable tt) {
					// ignore
				}
				continue;
			}
			itmp = param.indexOf("XrdServerExpire");
			if (itmp != -1) {
				final String tmp = param.substring(itmp + "XrdServerExpire".length()).trim();
				final int iq = tmp.indexOf("=");
				final String timeout = tmp.substring(iq + 1).trim();
				try {
					this.XRDSERVER_EXPIRE = Long.valueOf(timeout).longValue() * 1000; // it's
																					// in
																					// seconds
				}
				catch (final Throwable tt) {
					// ignore
				}
				continue;
			}
			itmp = param.indexOf("DictInfoExpire");
			if (itmp != -1) {
				final String tmp = param.substring(itmp + "DictInfoExpire".length()).trim();
				final int iq = tmp.indexOf("=");
				final String timeout = tmp.substring(iq + 1).trim();
				try {
					this.DICTINFO_EXPIRE = Long.valueOf(timeout).longValue() * 1000; // it's
																				// in
																				// seconds
				}
				catch (final Throwable tt) {
					// ignore
				}
				continue;
			}
		}
	}

	/**
	 * Helper class that provides functions to read from a data buffer bytes, chars, int16s, int32s, uint32, string
	 * until a separator, string of given length.
	 * 
	 * Note that it expects that the received data is in network byte order, so for int16, int32 and uint32 it will do a
	 * ntoh(..).
	 */
	static final class ByteReader {
		private final int len; // length of the data buffer
		private int pos; // current position in buffer
		private final byte[] data; // data buffer

		/**
		 * initialized with the buffer and its length
		 * 
		 * @param length
		 * @param bytes
		 */
		public ByteReader(final int length, final byte[] bytes) {
			this.len = length;
			this.data = bytes;
			this.pos = 0;
		}

		/**
		 * has more bytes to read
		 * 
		 * @return true if the buffer has more content to parse
		 */
		public boolean hasMore() {
			return this.pos < this.len;
		}

		/**
		 * read the next byte
		 * 
		 * @return the read byte
		 */
		public byte readByte() {
			return this.data[this.pos++];
		}

		/**
		 * read an unsigned byte
		 * 
		 * @return the read byte
		 */
		public int readUByte() {
			final int ff = 0xff;
			final byte b = readByte();
			return ff & b;
		}

		/**
		 * read the next char (1 byte)
		 * 
		 * @return the read character
		 */
		public char readChar() {
			return (char) this.data[this.pos++];
		}

		/**
		 * read the next int16
		 * 
		 * @return the read small int value
		 */
		public int readInt16() {
			final int b1 = readUByte();
			final int b2 = readUByte();
			return (b1 << 8) | b2;
		}

		/**
		 * read the next int32
		 * 
		 * @return the read int value
		 */
		public int readInt32() {
			final int w1 = readInt16();
			final int w2 = readInt16();
			return (w1 << 16) | w2;
		}

		/**
		 * read the next unsigned int32
		 * 
		 * @return the read int value
		 */
		public long readUInt32() {
			final long u1 = readInt16();
			final long u2 = readInt16();
			return (u1 << 16) | u2;
		}

		/**
		 * read the next int64
		 * 
		 * @return the read long value
		 */
		public long readInt64() {
			final long l1 = readUInt32();
			final long l2 = readUInt32();
			return (l1 << 32) | l2;
		}

		/** push back one byte from the buffer */
		public void pushBack() {
			if (this.pos > 0) {
				this.pos--;
			}
		}

		/**
		 * read string until reaching the given separator, or end of buffer. The output doesn't include the separator
		 * 
		 * @param sep
		 * @return the read string
		 */
		public String readStringToSep(final char sep) {
			final StringBuilder sb = new StringBuilder();
			while (hasMore()) {
				final char c = readChar();
				if (c == sep) {
					break;
				}
				sb.append(c);
			}
			return sb.toString();
		}

		/**
		 * read string of given length, or until end of buffer.
		 * 
		 * @param charNo
		 * @return the string value
		 */
		public String readStringLen(final int charNo) {
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; (i < charNo) && hasMore(); i++) {
				sb.append(readChar());
			}
			return sb.toString();
		}

		/**
		 * skip the next given number of chars from the buffer
		 * 
		 * @param charNo
		 */
		public void skipChars(final int charNo) {
			for (int i = 0; (i < charNo) && hasMore(); i++) {
				readChar();
			}
		}
	}

	/**
	 * add the given data to the global data structures; data is reported in MB
	 * 
	 * @param rRshift
	 * @param wRshift
	 * @param rTot
	 * @param wTot
	 * @param xsi
	 * @param key
	 */
	final void addRWsums(final int rRshift, final int wRshift, final long rTot, final long wTot, final XrdServerInfo xsi, final String key) {
		final double read = (rTot << rRshift) / 1024.0 / 1024.0;
		final double written = (wTot << wRshift) / 1024.0 / 1024.0;
		xsi.rMBytes += read;
		xsi.wMBytes += written;
		if (read > 0) {
			xsi.rFiles++;
		}
		if (written > 0) {
			xsi.wFiles++;
		}
		// we have to build a result for this transfer
		final Result r = new Result(this.Node.getFarmName(), this.Node.getClusterName(), xsi.host, MODULE_NAME, transfResTypes);
		r.time = NTPDate.currentTimeMillis();
		r.param[0] = read;
		r.param[1] = written;
		final DictInfo di = xsi.dictMap.get(key);
		if (di != null) {
			r.param[2] = di.userIP;
			r.param[3] = 0; // speed. filled at disconnect time
			xsi.transfResults.put(di.user, r);
		}
		else {
			// else we ignore it
			// TODO: change this sometime...
			logger.log(Level.FINE, "Ignoring Result since din't find the key[" + key + "] in the dictMap\n" + r);
		}
	}

	/**
	 * @param xsi
	 * @param di
	 */
	static final void touchXSIandDI(final XrdServerInfo xsi, final DictInfo di) {
		final long now = NTPDate.currentTimeMillis();

		if (xsi != null) {
			xsi.lastUpdateTime = now;
		}

		if (di != null) {
			di.lastUpdateTime = now;
		}
	}

	/**
	 * update the lastUpdateTime for the given key and the user of that key
	 * 
	 * @param key
	 * @param xsi
	 */
	static final void touchUserForKey(final String key, final XrdServerInfo xsi) {
		// update file and user's DictInfo time
		final long now = NTPDate.currentTimeMillis();
		xsi.lastUpdateTime = now;

		final DictInfo di = xsi.dictMap.get(key);
		if (di != null) {
			di.lastUpdateTime = now;

			/**
			 * findDIforUser would return at most the same DictInfo, so searching for it just to update the same field
			 * seems redundant di = findDIforUser(di.user, xsi); if(di != null) di.lastUpdateTime = now;
			 */
		}
	}

	/**
	 * convert the character code to a readable string
	 * 
	 * @param c
	 * @return the human readable form
	 */
	static final String codeToText(final char c) {
		switch (c) {
		case 'd':
			return "dictid mapping to a user/path combination";
		case 'i':
			return "dictid mapping to a session user/information combination";
		case 't':
			return "a file or I/O request trace";
		case 'u':
			return "dictid mapping to the user login name";
		default:
			return "UNKNOWN CODE!";
		}
	}

	/**
	 * convert the character code to the corresponding int constant
	 * 
	 * @param c
	 * @return the respective code
	 */
	static final int codeToType(final char c) {
		switch (c) {
		case 'd':
			return DI_TYPE_PATH;
		case 'i':
			return DI_TYPE_APPINFO;
		case 'u':
			return DI_TYPE_LOGIN;
		default:
			return 0;
		}
	}

	/**
	 * search the DictInfo for the given user; if not found, it returns null
	 * 
	 * @param user
	 * @param xsi
	 * @return the info
	 */
	static final DictInfo findDIforUser(final String user, final XrdServerInfo xsi) {
		final DictInfo di = xsi.dictUserMap.get(user);

		if ((di != null) && (di.type != DI_TYPE_LOGIN)) {
			return null;
		}

		return di;
	}

	private static final ExpirationCache<String, Double> hostToIPv4address = new ExpirationCache<String, Double>();

	/**
	 * convert the user string (such as aliprod.20565:13@lxb1628) to an IP (of lxb1628) stored as a double
	 * 
	 * @param user
	 * @return IPv4 address of the hostname from the client info encoded in a double value
	 */
	static final double getUserIP(final String user) {
		if (user == null) {
			return 0;
		}

		final int pa = user.indexOf('@');
		if (pa == -1) {
			return 0;
		}

		final String host = user.substring(pa + 1);

		final Double cached = hostToIPv4address.get(host);

		if (cached != null)
			return cached.doubleValue();

		try {
			final InetAddress[] ia = InetAddress.getAllByName(host);

			if (ia != null && ia.length > 0) {
				for (final InetAddress element : ia) {
					if (element instanceof Inet4Address) {
						final byte[] addr = element.getAddress();
						final double ret = ((0xffL & addr[3]) << 24) | ((0xffL & addr[2]) << 16) | ((0xffL & addr[1]) << 8) | (0xffL & addr[0]);

						hostToIPv4address.put(host, Double.valueOf(ret), 1000 * 60 * 30);

						return ret;
					}
				}
			}
		}
		catch (final UnknownHostException ex) {
			if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "Unknown host address for " + user);
			}
		}

		hostToIPv4address.put(host, Double.valueOf(0), 1000 * 60 * 5);

		return 0;
	}

	/**
	 * read a map message
	 * 
	 * @param br
	 * @param stod
	 * @param type
	 * @param xsi
	 */
	static final void readMapMessage(final ByteReader br, final long stod, final int type, final XrdServerInfo xsi) {
		final long dictid = br.readUInt32();
		String infoString = br.readStringLen(10000);
		final int idx = infoString.indexOf('\n');
		final String user = (idx != -1 ? infoString.substring(0, idx) : infoString);
		infoString = (idx != -1 ? infoString.substring(idx + 1) : null);
		// info = info.replace('\n', ' ');
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "Reading Map Message: dictID=" + dictid + " user=" + user + " info=" + infoString);
		}
		final DictInfo di = new DictInfo();
		di.user = StringFactory.get(user);
		di.userIP = getUserIP(user);
		di.info = infoString;
		di.key = "" + dictid + "/" + stod;
		di.type = type;
		xsi.dictMap.put(di.key, di);

		if ((di.user != null) && (di.type == DI_TYPE_LOGIN)) {
			xsi.dictUserMap.put(di.user, di);
		}

		touchXSIandDI(xsi, di);

		// touchUserForKey(di.key, xsi);
	}

	/**
	 * read trace messages
	 * 
	 * @param br
	 * @param stod
	 * @param xsi
	 */
	final void readTraceMessage(final ByteReader br, final long stod, final XrdServerInfo xsi) {
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "Reading Trace Message");
		}

		while (br.hasMore()) {
			final int type = br.readUByte();
			String key;
			DictInfo di;
			switch (type) {
			case XROOTD_MON_APPID: // Application provided marker
				br.skipChars(3); // skip 3 bytes;
				final String appid = br.readStringLen(12);
				for (int i = appid.length(); i < 12; i++) {
					br.readByte(); // ignore the rest; make sure that we read
									// the full record
				}
				// System.out.println("AppID = " + appid);
				// TODO: don't know how to handle and what to do with it; ignore
				// for now...
				break;
			case XROOTD_MON_CLOSE: // File has been closed
				final int rRshift = br.readUByte();
				final int wRshift = br.readUByte();
				br.skipChars(1);
				final long rTot = br.readUInt32();
				final long wTot = br.readUInt32();
				long dictid = br.readUInt32();
				key = "" + dictid + "/" + stod;
				di = xsi.dictMap.get(key);
				if (logger.isLoggable(Level.FINER)) {
					logger.log(Level.FINER, "CLOSE: rShift=" + rRshift + " wShift=" + wRshift + " rTot=" + rTot + " wTot=" + wTot + " dictID=" + dictid + " ref=" + di);
				}
				addRWsums(rRshift, wRshift, rTot, wTot, xsi, key);

				touchXSIandDI(xsi, di);

				// touchUserForKey(key, xsi);
				di = xsi.dictMap.remove(key); // removed key

				if ((di != null) && (di.user != null) && (di.type == DI_TYPE_LOGIN)) {
					xsi.dictUserMap.remove(di.user);
				}

				break;
			case XROOTD_MON_DISC: // Client has disconnected
				br.skipChars(7);
				final long seconds = br.readUInt32();
				dictid = br.readUInt32();
				key = "" + dictid + "/" + stod;
				di = xsi.dictMap.get(key);
				if (logger.isLoggable(Level.FINER)) {
					logger.log(Level.FINER, "DISCONNECT: after " + seconds + " sec dictID=" + dictid + " ref=" + di);
				}
				// set the time for the transfer done by this user.
				// the idea is that with xcp, there will be one transfer / user
				// connection
				// so we can use the connection time as the transfer time...
				// TODO: find a better way to do this
				if ((di != null) && (seconds > 0)) {
					// di refers to the user
					// find all results belonging to this user
					for (final Enumeration<String> entk = xsi.transfResults.keys(); entk.hasMoreElements();) {
						final String user = entk.nextElement();
						final DictInfo udi = findDIforUser(user, xsi);
						if ((udi != null) && udi.equals(di)) {
							final Result r = xsi.transfResults.get(user);
							r.param[3] = (r.param[0] + r.param[1]) / seconds; // in
																				// MB/s
						}
					}
				}
				di = xsi.dictMap.remove(key); // removed key

				if ((di != null) && (di.user != null) && (di.type == DI_TYPE_LOGIN)) {
					xsi.dictUserMap.remove(di.user);
				}

				break;
			case XROOTD_MON_OPEN: // File has been opened
				br.skipChars(11);
				dictid = br.readUInt32();
				key = "" + dictid + "/" + stod;
				di = xsi.dictMap.get(key);
				if (logger.isLoggable(Level.FINER)) {
					logger.log(Level.FINER, "OPEN: dictID=" + dictid + " ref=" + di);
				}

				touchXSIandDI(xsi, di);

				// touchUserForKey(key, xsi);
				break;
			case XROOTD_MON_WINDOW: // Window timing mark
				br.skipChars(7);
				final long lastWend = br.readUInt32();
				final long thisWstart = br.readUInt32();
				if (logger.isLoggable(Level.FINER)) {
					logger.log(Level.FINER, "WINDOW: lastEND: " + new Date(lastWend * 1000) + " thisSTART: " + new Date(thisWstart * 1000));
				}
				// TODO: does it worth using this information?
				break;
			default:
				if ((type & XROOTD_MON_RWREQ) == 0) {
					// Read or write request
					br.pushBack(); // push back the type byte
					final long offset = br.readInt64();
					int rwSize = br.readInt32(); // SIGNED!
					String rwType;
					if (rwSize >= 0) {
						rwType = "READ";
					}
					else {
						rwType = "WRITE";
						rwSize *= -1;
					}
					dictid = br.readUInt32();
					if (logger.isLoggable(Level.FINEST)) {
						logger.log(Level.FINEST, rwType + ": offset=" + offset + " length=" + rwSize + " dictID=" + dictid + " ref=" + xsi.dictMap.get("" + dictid + "/" + stod));
						// TODO: find a better way to use this data
						// because for now, it's unusable, as it is
					}
				}
				else {
					logger.log(Level.INFO, "UNKNOWN Trace message!");
					br.skipChars(7);
				}
			}
		}
	}

	private static final ExpirationCache<String, String> sourceToHostAddress = new ExpirationCache<String, String>();

	@Override
	public void notifyData(final int len, final byte[] data, final InetAddress source) {
		final String sourceAddress = source.getHostAddress();

		String serverHost = sourceToHostAddress.get(sourceAddress);

		if (serverHost == null) {
			serverHost = Utils.getHostName(sourceAddress);

			if (serverHost != null) {
				sourceToHostAddress.put(sourceAddress, serverHost, 1000 * 60 * 60);
			}
			else {
				serverHost = sourceAddress;
				sourceToHostAddress.put(sourceAddress, serverHost, 1000 * 60 * 5);
			}
		}
		
		if (logger.isLoggable(Level.FINER)) {
			logger.log(Level.FINER, "Received packet of size " + len + " from " + serverHost + " [" + source.getHostAddress() + "] at " + new Date());
		}

		final ByteReader br = new ByteReader(len, data);
		final char code = br.readChar();
		final int pseq = br.readUByte();
		final int plen = br.readInt16();
		final long stod = br.readUInt32();
		if (logger.isLoggable(Level.FINEST)) {
			logger.log(Level.FINEST, "Reading Header:" + " code=" + code + " -> Follows " + codeToText(code) + " pseq=" + pseq + " plen=" + plen + " stod=" + stod + "=" + new Date(1000 * stod));
		}
		// try{
		// String fName = "/tmp/monXrootd."+pseq;
		// FileOutputStream file = new FileOutputStream(fName);
		// file.write(data, 0, len);
		// file.close();
		// System.out.println("Wrote binary packet in "+fName+"\n");
		// }catch(IOException ex){
		// ex.printStackTrace();
		// }

		XrdServerInfo xsi = this.xrdServers.get(serverHost);
		if (xsi == null) {
			xsi = new XrdServerInfo(serverHost);
			this.xrdServers.put(serverHost, xsi);
		}
		synchronized (xsi) {
			if (code == 't') {
				readTraceMessage(br, stod, xsi);
			}
			else {
				readMapMessage(br, stod, codeToType(code), xsi);
			}
		}
	}

	/** build a result vector with the current data */
	@Override
	public Object doProcess() throws Exception {
		final Vector<Result> vrez = new Vector<Result>();

		final long now = NTPDate.currentTimeMillis();
		// long timeInterval = (now - lastDoProcessTime) / 1000;

		for (final Enumeration<String> ksen = this.xrdServers.keys(); ksen.hasMoreElements();) {
			final String host = ksen.nextElement();
			final XrdServerInfo xsi = this.xrdServers.get(host);
			synchronized (xsi) {
				// add all transfer results
				vrez.addAll(xsi.transfResults.values());
				xsi.transfResults.clear();
				// summarize the server's
				xsi.nrClients = 0;
				xsi.nrOpenFiles = 0;
				if ((now - xsi.lastUpdateTime) > this.XRDSERVER_EXPIRE) {
					this.xrdServers.remove(host);
					// remove this host from config
					if (logger.isLoggable(Level.FINE)) {
						logger.log(Level.FINE, "Info about Xrootd server [" + host + "] expired! Removing it..");
					}
					final Result r = new Result(this.Node.getFarmName(), this.Node.getClusterName(), host, MODULE_NAME, new String[] {});
					r.time = now;
					vrez.add(r);
				}
				else {
					for (final Enumeration<String> kdien = xsi.dictMap.keys(); kdien.hasMoreElements();) {
						final String kdi = kdien.nextElement();
						final DictInfo di = xsi.dictMap.get(kdi);
						if ((now - di.lastUpdateTime) > this.DICTINFO_EXPIRE) {
							xsi.dictMap.remove(kdi);

							if ((di.user != null) && (di.type == DI_TYPE_LOGIN)) {
								xsi.dictUserMap.remove(di.user);
							}
						}
						else {
							if (di.type == DI_TYPE_LOGIN) {
								xsi.nrClients++;
							}
							if (di.type == DI_TYPE_PATH) {
								xsi.nrOpenFiles++;
							}
						}
					}
					final Result r = new Result(this.Node.getFarmName(), this.Node.getClusterName(), host, MODULE_NAME, this.resTypes);
					r.time = now;
					r.param[0] = xsi.nrClients;
					r.param[1] = xsi.nrOpenFiles;
					r.param[2] = xsi.rMBytes; // / timeInterval
					r.param[3] = xsi.wMBytes; // / timeInterval
					r.param[4] = xsi.rFiles; // / timeInterval
					r.param[5] = xsi.wFiles; // / timeInterval
					vrez.add(r);
				}
				xsi.rMBytes = xsi.wMBytes = 0;
				xsi.rFiles = xsi.wFiles = 0;
			}
		}

		return vrez;
	}

	/**
	 * Module debug method
	 * 
	 * @param args
	 */
	public static void main(final String[] args) {
		final String host = "localhost"; // args[0] ;

		final monXrootd aa = new monXrootd();
		String ad = null;
		try {
			ad = InetAddress.getByName(host).getHostAddress();
		}
		catch (final Exception e) {
			System.out.println(" Can not get ip for node " + e);
			System.exit(-1);
		}
		// Logger.getLogger("lia.Monitor.modules.monXrootd").setLevel(Level.FINEST);
		// System.out.println("lev="+Logger.getLogger("lia.Monitor.modules.monXrootd").getLevel());

		aa.init(new MNode(host, ad, null, null), "\"ListenPort=9932,XrdServerExpire=300,DictInfoExpire=150\"");

		for (;;) {
			try {
				final Object bb = aa.doProcess();
				try {
					Thread.sleep(1 * 1000);
				}
				catch (final Exception e1) {
					// ignore
				}

				if ((bb != null) && (bb instanceof Vector)) {
					final Vector<?> res = (Vector<?>) bb;
					if (res.size() > 0) {
						System.out.println("Got a Vector with " + res.size() + " results");
						for (int i = 0; i < res.size(); i++) {
							System.out.println(" { " + i + " } >>> " + res.elementAt(i));
						}
					}
				}
			}
			catch (final Exception e) {
				// ignore
			}
		}
	}
}
