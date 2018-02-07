/* Created on Mar 15, 2004 */
package lia.Monitor.JiniClient.CommonGUI;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.IPUtils;
import lia.util.Utils;

/**
 * Changed a lot on 04/May/2013 to support IPv6. 
 * Added a lot of stuff from GUAVA
 * 
 * @author mluc
 * @author ramiro
 *         TODO: make it IPv6-friendly
 */
public final class IpAddrCache {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(IpAddrCache.class.getName());

    public static final int hostIgnoreTime = AppConfig.geti("lia.Monitor.IpAddrCache.hostIgnoreTime", 15); // in minutes

    /** hash with hostName -> ipAddress */
    private static final Map<String, String> addrIPCache = new ConcurrentHashMap<String, String>();

    /** hash with ipAddress -> hostName */
    private static final Map<String, String> hostCache = new ConcurrentHashMap<String, String>();

    /** hash with ipAddress -> InetAddress */
    private static final Map<String, InetAddress> inetAddrCache = new ConcurrentHashMap<String, InetAddress>();

    /** reference to the ipResolver thread */
    private final static IPResolver ipResolver;

    static {
        ipResolver = new IPResolver();
        ipResolver.start();
    }

    /** put in addrIPCache and hostCache, but NOT in inetAddrCache the given pair */
    public static void putIPandHostInCache(String IPaddr, String hostName) {
        hostName = hostName.toLowerCase();
        hostCache.put(IPaddr, hostName);
        addrIPCache.put(hostName, IPaddr);
    }

    /** put into ALL the caches data based on the given InetAddress */
    private static void putInetAddress(InetAddress ina, String hostOrIP) {
        final String host = (isInetAddress(hostOrIP) ? ina.getCanonicalHostName() : hostOrIP);
        putIPandHostInCache(ina.getHostAddress(), host);
        inetAddrCache.put(ina.getHostAddress(), ina);
    }

    /**
     * creates a InetAddress given a host name or an IP address
     * Provides the InetAddress object with possible alot of waiting!!! That's important!
     */
    public static InetAddress getInetAddressHelper(String hostOrIP) {
        hostOrIP = hostOrIP.toLowerCase();
        String ip = null;
        InetAddress ia = null;
        if (isInetAddress(hostOrIP)) {
            ip = hostOrIP; // it's already an IP
        } else {
            ip = addrIPCache.get(hostOrIP); // try to convert a known hostName to the corresponding IP
        }
        if (ip != null) {
            ia = inetAddrCache.get(ip); // get the IP's inet Address
        } else {
            ip = hostOrIP; // in fact it's an unresolved hostName... will resolve it
        }
        if (ia != null) {
            return ia;
        }
        try {
            final long lStartResolving = Utils.nanoNow();
            ia = InetAddress.getByName(ip);
            if (logger.isLoggable(Level.FINEST)) {
                final long dt = Utils.nanoNow() - lStartResolving;
                logger.finest(ip + " resolved to " + ia.getHostName() + " " + ia.getHostAddress() + " in "
                        + TimeUnit.NANOSECONDS.toMillis(dt) + " millis");
            }
            // System.out.println("result = "+ia);
            putInetAddress(ia, hostOrIP);
        } catch (UnknownHostException e) {
            logger.log(Level.FINE, "Error resolving IP address " + ip + "\n", e);
            ia = null;
        }
        return ia;
    }

    /**
     * finds the host name corresponding to the given hostName or IPaddress
     * If mayFail is true and hostName isn't found in addrIPCache then
     * it is added to the list of IPs that are going to be resolved by IPResolver thread,
     * and on a subsequent call to this function the hostname might have been resolved.
     * If mayFail is false and hostName isn't found in addrIPCache then it is resolved immediately
     */
    public static String getHostName(String hostOrIP, boolean mayFail) {
        hostOrIP = hostOrIP.toLowerCase();
        if (!isInetAddress(hostOrIP)) {
            return hostOrIP;
        }
        // hostOrIP is a IP
        String name = hostCache.get(hostOrIP);
        if (name == null) {
            if (mayFail) {
                ipResolver.addToResolve(hostOrIP);
                return null;
            }
            InetAddress ina = getInetAddressHelper(hostOrIP);
            if (ina != null) {
                name = hostCache.get(hostOrIP);
            }
            if (name == null) {
                logger.log(Level.FINE, "Name NULL after resolving");
            }
        }
        return name;
    }

    /**
     * Gets the ip for a given hostname.
     * If hostname is the same as the resulting ip, try
     * to find the hostname with the complementary function.
     * If mayFail is true and hostName isn't found in addrIPCache then
     * it is added to the list of IPs that are going to be resolved by IPResolver thread,
     * and on a subsequent call to this function the hostname might have been resolved.
     * If mayFail is false and hostName isn't found in addrIPCache then it is resolved immediately
     */
    public static String getIPaddr(String hostOrIP, boolean mayFail) {
        // first test if hostName is in fact an IP and return it like that
        hostOrIP = hostOrIP.toLowerCase();
        if (isInetAddress(hostOrIP)) {
            return hostOrIP;
        }
        // hostOrIP is a hostName
        String ip = addrIPCache.get(hostOrIP);
        // this hostName was not found; try resolving it
        if (ip == null) {
            if (mayFail) {
                ipResolver.addToResolve(hostOrIP);
                return null;
            }
            InetAddress ina = getInetAddressHelper(hostOrIP);
            if (ina != null) {
                ip = addrIPCache.get(hostOrIP);
                if (ip == null) {
                    logger.log(Level.FINE, "IP NULL after resolving " + hostOrIP);
                    putIPandHostInCache("##NO_IP_FOUND##", hostOrIP);
                }
            }
        }
        return ip;
    }

    /** return true if hostOrIP is IP and not hostname */

    //
    // Replaced with isInetAddress() - from GUAVA!!!
    //

    //    private static boolean isIPaddress(String hostOrIP) {
    //        StringTokenizer stk = new StringTokenizer(hostOrIP, ".");
    //        if (stk.countTokens() != 4)
    //            return false;
    //        try {
    //            while (stk.hasMoreTokens()) {
    //                String group = stk.nextToken();
    //                int gval = Integer.parseInt(group);
    //                if (gval < 0 || gval > 255)
    //                    return false;
    //            }
    //        } catch (NumberFormatException ex) {
    //            return false;
    //        }
    //        return true;
    //    }

    /**
     * Returns {@code true} if the supplied string is a valid IP string
     * literal, {@code false} otherwise.
     *
     * @param ipString {@code String} to evaluated as an IP string literal
     * @return {@code true} if the argument is a valid IP string literal
     */
    public static boolean isInetAddress(String ipString) {
        return IPUtils.ipStringToBytes(ipString) != null;
    }

    /** get a byte [] address for a hostName or an IP address */
    private static byte[] getByteAddr(String hostOrIP) {
        if (!isInetAddress(hostOrIP)) {
            hostOrIP = getIPaddr(hostOrIP, false);
        }
        if (hostOrIP == null) {
            return null;
        }

        return IPUtils.ipStringToBytes(hostOrIP);
    }

    /**
     * try providing an InetAddress without using DNS for this... unless it's impossible
     * Provides the InetAddress object without wait!!! That's important!
     */
    public static InetAddress getInetAddress(String hostOrIP) {

        String ip = getIPaddr(hostOrIP, false);
        byte[] addr = getByteAddr(ip);
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** this thread will resolve in background all IPs and will put restuls in outer class */
    private static class IPResolver extends Thread {

        // filled by outer class with hostnames or IPs to be resolved
        private final BlockingQueue<String> toResolve = new LinkedBlockingQueue<String>();

        // hostOrIp -> timeOfFailure
        private final Map<String, Long> failed = new ConcurrentHashMap<String, Long>();

        public IPResolver() {
            setName("(ML) IPResolver");
        }

        /** add another host or ip to resolve */
        public void addToResolve(String hostOrIP) {
            final Long time = failed.get(hostOrIP);
            if (time != null) {
                final long nanoNow = Utils.nanoNow();
                if (TimeUnit.NANOSECONDS.toMinutes(nanoNow - time.longValue()) > hostIgnoreTime) {
                    failed.remove(hostOrIP);
                } else {
                    return;
                }
            }
            if (!toResolve.contains(hostOrIP)) {
                // logger.log(Level.INFO, "Going to resolve later "+hostOrIP);
                toResolve.add(hostOrIP);
            }
        }

        /**
         * get the next host or IP to resolve
         * 
         * @throws InterruptedException
         */
        private String getNextToResolve() throws InterruptedException {
            return toResolve.take();
        }

        @Override
        public void run() {
            while (true) {
                String hostOrIP = null;
                try {
                    // iterate through things to resolve
                    while ((hostOrIP = getNextToResolve()) != null) {
                        InetAddress ina = getInetAddressHelper(hostOrIP);
                        if (ina == null) {
                            logger.log(Level.FINE, "FAILED resolving " + hostOrIP + "; will retry after "
                                    + hostIgnoreTime + " min.");
                            failed.put(hostOrIP, Long.valueOf(Utils.nanoNow()));
                        }
                        // remove the analyzed element
                        toResolve.remove(0); // 0 instead of hostOrIP
                    }
                } catch (Throwable t) {
                    logger.log(Level.INFO, "Error while resolving IP", t);
                    if (hostOrIP != null) {
                        failed.put(hostOrIP, Long.valueOf(Utils.nanoNow()));
                    }
                    toResolve.remove(0); // 0 instead of hostOrIP
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

}
