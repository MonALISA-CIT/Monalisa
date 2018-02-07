package lia.util.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.MLProcess;

/**
 * Gathers basic net interfaces information
 * 
 * @author adim
 */
public class LocalHost {
    private static final Logger logger = Logger.getLogger(LocalHost.class.getName());

    static public List<String> getPublicIPs4() {
        Enumeration<NetworkInterface> ifs = null;
        try {
            ifs = NetworkInterface.getNetworkInterfaces();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ LocalHost ] Unable to get the network interfaces. Cause: ", t);
        }

        if ((ifs == null) || !ifs.hasMoreElements()) {
            return Collections.EMPTY_LIST;
        }

        final List<String> ips4 = new ArrayList<String>();
        while (ifs.hasMoreElements()) {
            final NetworkInterface iface = ifs.nextElement();

            Enumeration<InetAddress> iad = iface.getInetAddresses();
            while (iad.hasMoreElements()) {
                final InetAddress localIP = iad.nextElement();
                if (!localIP.isSiteLocalAddress() && !localIP.isLoopbackAddress()) {
                    // found an IPv4 address and proxyAddress not set yet
                    if (localIP instanceof java.net.Inet4Address) {
                        ips4.add(localIP.getHostAddress());
                    }
                }
            }
        }
        return ips4;
    }

    static public String getPublicInterfacesIPs4() {
        Enumeration<NetworkInterface> ifs = null;
        try {
            ifs = NetworkInterface.getNetworkInterfaces();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ LocalHost ] Unable to get the network interfaces. Cause: ", t);
            return "";
        }

        if ((ifs == null) || !ifs.hasMoreElements()) {
            return "";
        }

        final List<String> ips4 = new ArrayList<String>();
        while (ifs.hasMoreElements()) {
            final NetworkInterface iface = ifs.nextElement();
            String iName = iface.getDisplayName();
            Enumeration<InetAddress> iad = iface.getInetAddresses();
            String iFaceInfo = null;
            String iFaceSpeed = null;
            while (iad.hasMoreElements()) {
                InetAddress localIP = iad.nextElement();
                if (!localIP.isSiteLocalAddress() && !localIP.isLoopbackAddress()) {
                    // found an IPv4 address and proxyAddress not set yet
                    if (localIP instanceof java.net.Inet4Address) {
                        iFaceInfo = localIP.getHostAddress() + "|" + iName;
                        iFaceSpeed = getIfSpeed(iName);
                        if (iFaceSpeed != null) {
                            iFaceInfo += "|" + iFaceSpeed;
                        }
                        ips4.add(iFaceInfo);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (final String ipInt : ips4) {
            sb.append(ipInt).append(",");
        }
        return sb.toString();
    }

    static public List<String> getPublicIPs6() {
        Enumeration<NetworkInterface> ifs = null;
        try {
            ifs = NetworkInterface.getNetworkInterfaces();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ LocalHost ] Unable to get the network interfaces. Cause: ", t);
        }

        if ((ifs == null) || !ifs.hasMoreElements()) {
            return Collections.EMPTY_LIST;
        }

        List<String> ips6 = new ArrayList<String>();
        while (ifs.hasMoreElements()) {
            NetworkInterface iface = ifs.nextElement();
            Enumeration<InetAddress> iad = iface.getInetAddresses();
            while (iad.hasMoreElements()) {
                InetAddress localIP = iad.nextElement();
                if (!localIP.isSiteLocalAddress() && !localIP.isLinkLocalAddress() && !localIP.isLoopbackAddress()) {
                    if (localIP instanceof java.net.Inet6Address) {
                        ips6.add(localIP.getHostAddress());
                    }
                }
            }
        }
        return ips6;
    }

    static public String getPublicIP4() {

        Enumeration<NetworkInterface> ifs = null;
        try {
            ifs = NetworkInterface.getNetworkInterfaces();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ LocalHost ] Unable to get the network interfaces. Cause: ", t);
            return null;
        }

        if ((ifs == null) || !ifs.hasMoreElements()) {
            return null;
        }

        while (ifs.hasMoreElements()) {
            NetworkInterface iface = ifs.nextElement();
            Enumeration<InetAddress> iad = iface.getInetAddresses();
            while (iad.hasMoreElements()) {
                InetAddress localIP = iad.nextElement();
                if (!localIP.isSiteLocalAddress() && !localIP.isLoopbackAddress()) {
                    // found an IPv4 address
                    if (localIP instanceof java.net.Inet4Address) {
                        return localIP.getHostAddress();
                    }
                }
            }
        }
        return null;
    }

    private static final String[] SYS_EXTENDED_PATH = new String[] { "PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin" };

    static String getIfSpeed(String ifname) {
        try {
            final Process process = MLProcess.exec("ethtool " + ifname + " |grep Speed|cut -d: -f2", SYS_EXTENDED_PATH,
                    -1);
            BufferedReader buff = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String sLine = null;
            if (buff != null) {
                // read the first line
                sLine = buff.readLine();
                return sLine.trim();
            }
        } catch (Exception e) {
        }
        return null;
    }

    static public String getPublicIP6() {

        Enumeration<NetworkInterface> ifs = null;
        try {
            ifs = NetworkInterface.getNetworkInterfaces();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " [ LocalHost ] Unable to get the network interfaces. Cause: ", t);
            return null;
        }

        if ((ifs == null) || !ifs.hasMoreElements()) {
            return null;
        }

        while (ifs.hasMoreElements()) {
            NetworkInterface iface = ifs.nextElement();
            Enumeration<InetAddress> iad = iface.getInetAddresses();
            while (iad.hasMoreElements()) {
                InetAddress localIP = iad.nextElement();
                if (!localIP.isSiteLocalAddress() && !localIP.isLinkLocalAddress() && !localIP.isLoopbackAddress()) {
                    if (localIP instanceof java.net.Inet6Address) {
                        return localIP.getHostAddress();
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        System.out.println(getPublicIP4());
        System.out.println(getPublicIP6());
        System.out.println(getPublicIPs4());
        System.out.println(getPublicIPs6());
        System.out.println(getPublicInterfacesIPs4());
        System.out.println(getIfSpeed("eth1"));
        System.out.println("DONE");
    }

}
