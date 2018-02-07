/*
 * $Id: BadClient.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.ClientsFarmProxy;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 * @author mickyt, ramiro
 */
public class BadClient {

    /**
     * in nanos
     */
	public final AtomicLong lastUpdate;
	public final AtomicInteger nrConns;
	public final InetAddress address;
	
	public BadClient (final long lastUpdate, final InetAddress ia) {
		this.lastUpdate = new AtomicLong(lastUpdate);
		this.nrConns = new AtomicInteger(1);
		this.address = ia;
	}
	
	public String toString() {
	    return "BadClient : " + address + " conns: " + nrConns.get();
	}
} // class BadClient

