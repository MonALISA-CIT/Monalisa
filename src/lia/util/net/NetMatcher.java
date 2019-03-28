/***********************************************************************
 * Copyright (c) 2000-2004 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/

/**
 * Modified by Catac to support matching with a domain name. 
 * 
 * A given initialization string (in NetMatcher constructor) is considered 
 * to be a domain name if it starts with a ".", like ".cern.ch".
 */

package lia.util.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import lazyj.Utils;

/**
 */
public class NetMatcher {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(NetMatcher.class.getName());

    private ArrayList<InetNetwork> networks;

    /**
     * @param nets
     */
    public void initInetNetworks(final Collection<String> nets) {
        networks = new ArrayList<InetNetwork>(nets.size());

        addInetNetworks(nets);

        networks.trimToSize();
    }

    /**
     * @param nets
     */
    public void initInetNetworks(final String[] nets) {
        networks = new ArrayList<InetNetwork>(nets.length);

        for (final String netName : nets) {
            final InetNetwork net = InetNetwork.getFromString(netName);

            if (!networks.contains(net)) {
                networks.add(net);
            }
        }

        networks.trimToSize();
    }

    /** Add more InetNetworks to the current NetMatcher 
     * @param nets */
    public void addInetNetworks(final Collection<String> nets) {
        for (final String netName : nets) {
            final InetNetwork net = InetNetwork.getFromString(netName);

            if (!networks.contains(net)) {
                networks.add(net);
            }
        }
    }

    /**
     * @param hostIP
     * @return true if matches
     */
    public boolean matchInetNetwork(final String hostIP) {
        InetAddress ip = null;

        try {
            ip = InetAddress.getByName(hostIP);
        } catch (final java.net.UnknownHostException uhe) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Cannot resolve address for: " + hostIP + " Attempting to match it as string.");
            }
        }

        for (final InetNetwork network : networks) {
            if (ip != null) {
                if (network.contains(ip)) {
                    return true;
                }
            } else {
                try {
                    if (network.contains(hostIP)) {
                        return true;
                    }
                } catch (final UnknownHostException ex) {
                    // ignore. we have already complained before.
                }
            }
        }

        return false;
    }

    /**
     * @param ip
     * @return true if matches
     */
    public boolean matchInetNetwork(final InetAddress ip) {
        for (final InetNetwork network : networks) {
            if (network.contains(ip)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 
     */
    public NetMatcher() {
        // nothing to be done in the constructor
    }

    /**
     * @param nets
     */
    public NetMatcher(final String[] nets) {
        initInetNetworks(nets);
    }

    /**
     * @param nets
     */
    public NetMatcher(final Collection<String> nets) {
        initInetNetworks(nets);
    }

    @Override
    public String toString() {
        return networks.toString();
    }

    /**
     * @param args
     */
    public static final void main(String[] args) {
        String[] nets = { "137.138.*", "192.91.0.0/18", "134.158.108.110" };
        String[] addresses = { "137.138.42.56", "192.91.244.18", "cclcgalice02", "134.158.108.110" };

        NetMatcher nm = new NetMatcher(nets);
        System.out.println(" NetMatcher is: " + nm);
        for (String addresse : addresses) {
            if (nm.matchInetNetwork(addresse)) {
                System.out.println(" Matched IP  -> " + addresse);
            } else {
                System.out.println(" Missed IP  -> " + addresse);

                if (nm.containsName(addresse)) {
                    System.out.println("But matched the host hostname -> " + addresse);
                }
            }
        }
    }

    /**
     * @param shortHostName
     * @return true if the short hostname if found
     */
    public final boolean containsName(final String shortHostName) {
        for (final InetNetwork net : networks) {
            if (net.containsName(shortHostName)) {
                return true;
            }
        }

        return false;
    }
}

/**
 * @author costing
 *
 */
class InetNetwork {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(InetNetwork.class.getName());

    /*
     * Implements network masking, and is compatible with RFC 1518 and
     * RFC 1519, which describe CIDR: Classless Inter-Domain Routing.
     */

    private InetAddress network;
    private InetAddress netmask;

    /* Or, it holds the full qualified domain name, or just the ."domain name" */
    private String fqdn;

    private String reverseName;

    /**
     * @param ip
     * @param netmask
     */
    public InetNetwork(InetAddress ip, InetAddress netmask) {
        network = maskIP(ip, netmask);
        this.netmask = netmask;
    }

    /**
     * @param fqdn
     */
    public InetNetwork(String fqdn) {
        this.fqdn = this.reverseName = fqdn.toLowerCase();
    }

    /**
     * @param name
     * @return true if contains
     * @throws UnknownHostException
     */
    public boolean contains(final String name) throws java.net.UnknownHostException {
        if ((name == null) || (name.length() == 0)) {
            return false;
        }

        if (fqdn == null) {
            InetAddress ip = InetAddress.getByName(name);
            return contains(ip);
        }

        // fqdn is either a full hostname, or a domain name, starting with "."
        if (fqdn.startsWith(".")) {
            return name.toLowerCase().endsWith(fqdn);
        }

        return name.equalsIgnoreCase(fqdn);
    }

    /**
     * @param hostname
     * @return true if the hostname matches the fqdn fully or even if it is just the hostname part
     */
    public boolean containsName(final String hostname) {
        if ((reverseName == null) || (hostname == null) || (reverseName.length() == 0) || (hostname.length() == 0)) {
            return false;
        }

        if (reverseName.equalsIgnoreCase(hostname)) {
            return true;
        }

        final int idx = hostname.indexOf(".");

        if ((idx < 0) && reverseName.startsWith(hostname.toLowerCase() + ".")) {
            return true;
        } else if ((idx == (hostname.length() - 1)) && reverseName.startsWith(hostname.toLowerCase())) {
            return true;
        }

        return false;
    }

    /**
     * @param ip
     * @return true if contains
     */
    public boolean contains(final InetAddress ip) {
        return fqdn == null ? network.equals(maskIP(ip, netmask)) : Utils.getHostName(ip.getHostAddress()).endsWith(fqdn);
    }

    @Override
    public String toString() {
        return fqdn == null ? network.getHostAddress() + "/" + netmask.getHostAddress() : fqdn;
    }

    @Override
    public int hashCode() {
        return fqdn == null ? maskIP(network, netmask).hashCode() : fqdn.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj != null)
                && (obj instanceof InetNetwork)
                && ((fqdn == null) && (((InetNetwork) obj).fqdn == null) ? ((((InetNetwork) obj).network
                        .equals(network)) && (((InetNetwork) obj).netmask.equals(netmask))) : ((fqdn != null)
                        && (((InetNetwork) obj).fqdn != null) ? ((InetNetwork) obj).fqdn.equals(fqdn) : false));
    }

    /**
     * @param stringForm
     * @return the network class from a string representation
     */
    public static InetNetwork getFromString(final String stringForm) {
        String netspec = stringForm;

        if (netspec.startsWith(".")) {
            return new InetNetwork(netspec);
        }
        if (netspec.endsWith("*")) {
            netspec = normalizeFromAsterisk(netspec);
        } else {
            int iSlash = netspec.indexOf('/');
            if (iSlash == -1) {
                netspec += "/255.255.255.255";
            } else if (netspec.indexOf('.', iSlash) == -1) {
                netspec = normalizeFromCIDR(netspec);
            }
        }

        final int iSlash = netspec.indexOf('/');
        final String fqdnOrIP = netspec.substring(0, iSlash);

        try {
            final InetAddress addr = InetAddress.getByName(fqdnOrIP);
            final InetAddress mask = InetAddress.getByName(netspec.substring(iSlash + 1));

            final InetNetwork net = new InetNetwork(addr, mask);

            try {
                net.reverseName = Utils.getHostName(fqdnOrIP);
            } catch (Throwable t) {
                // ignore
            }

            return net;
        } catch (java.net.UnknownHostException ex) {
            logger.warning("Cannot resolve address: " + fqdnOrIP + " Will try to match it as it is.");
            // This means that it's a private hostname, that cannot be resolved publicly
            // Will keep it as it is, and perform string-only matching on it
            return new InetNetwork(fqdnOrIP);
        }
    }

    /**
     * @param ip
     * @param mask
     * @return the masked network address
     */
    public static InetAddress maskIP(final byte[] ip, final byte[] mask) {
        try {
            return getByAddress(new byte[] { (byte) (mask[0] & ip[0]), (byte) (mask[1] & ip[1]),
                    (byte) (mask[2] & ip[2]), (byte) (mask[3] & ip[3]) });
        } catch (Exception _) {
            // ignore
        }

        return null;
    }

    /**
     * @param ip
     * @param mask
     * @return the masked network address
     */
    public static InetAddress maskIP(final InetAddress ip, final InetAddress mask) {
        return maskIP(ip.getAddress(), mask.getAddress());
    }

    /*
     * This converts from an uncommon "wildcard" CIDR format
     * to "address + mask" format:
     * 
     *   *               =>  000.000.000.0/000.000.000.0
     *   xxx.*           =>  xxx.000.000.0/255.000.000.0
     *   xxx.xxx.*       =>  xxx.xxx.000.0/255.255.000.0
     *   xxx.xxx.xxx.*   =>  xxx.xxx.xxx.0/255.255.255.0
     */
    static private String normalizeFromAsterisk(final String netspec) {
        String[] masks = { "0.0.0.0/0.0.0.0", "0.0.0/255.0.0.0", "0.0/255.255.0.0", "0/255.255.255.0" };
        char[] srcb = netspec.toCharArray();
        int octets = 0;
        for (int i = 1; i < netspec.length(); i++) {
            if (srcb[i] == '.') {
                octets++;
            }
        }
        return (octets == 0) ? masks[0] : netspec.substring(0, netspec.length() - 1).concat(masks[octets]);
    }

    /*
     * RFC 1518, 1519 - Classless Inter-Domain Routing (CIDR)
     * This converts from "prefix + prefix-length" format to
     * "address + mask" format, e.g. from xxx.xxx.xxx.xxx/yy
     * to xxx.xxx.xxx.xxx/yyy.yyy.yyy.yyy.
     */
    static private String normalizeFromCIDR(final String netspec) {
        final int bits = 32 - Integer.parseInt(netspec.substring(netspec.indexOf('/') + 1));
        final int mask = (bits == 32) ? 0 : 0xFFFFFFFF - ((1 << bits) - 1);

        return netspec.substring(0, netspec.indexOf('/') + 1) + Integer.toString((mask >> 24) & 0xFF, 10) + "."
                + Integer.toString((mask >> 16) & 0xFF, 10) + "." + Integer.toString((mask >> 8) & 0xFF, 10) + "."
                + Integer.toString((mask >> 0) & 0xFF, 10);
    }

    private static java.lang.reflect.Method getByAddress = null;

    static {
        try {
            Class<?> inetAddressClass = Class.forName("java.net.InetAddress");
            Class<?>[] parameterTypes = { byte[].class };
            getByAddress = inetAddressClass.getMethod("getByAddress", parameterTypes);
        } catch (Exception e) {
            getByAddress = null;
        }
    }

    private static InetAddress getByAddress(byte[] ip) throws java.net.UnknownHostException {
        InetAddress addr = null;
        if (getByAddress != null) {
            try {
                addr = (InetAddress) getByAddress.invoke(null, new Object[] { ip });
            } catch (IllegalAccessException e) {
                // ignore
            } catch (java.lang.reflect.InvocationTargetException e) {
                // ignore
            }
        }

        if (addr == null) {
            addr = InetAddress.getByName(Integer.toString(ip[0] & 0xFF, 10) + "." + Integer.toString(ip[1] & 0xFF, 10)
                    + "." + Integer.toString(ip[2] & 0xFF, 10) + "." + Integer.toString(ip[3] & 0xFF, 10));
        }
        return addr;
    }
}
