/*
 * @(#)monIPAddresses	1.0	01/06/2006
 *
 * Copyright 2005 California Institute of Technology
 */

package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.eResult;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

/**
 * The <code>monIPAddresses</code> module exports all local IP addresses, in the form:<br>
 * <br>
 * ip[N]_(v4|v6)_(public|private|link|multicast)_[interface name]<br>
 * <br>
 * All public addresses are listed before all private/link/multicast addresses.<br>
 * <br>
 * Another parameter that is exported is <i>ip_visible</i>. This address is determined by
 * parsing the output of monalisa.cern.ch/ip.php
 * If this address is one of the local addresses then this address will be moved on the first
 * position in the list. In this case another parameter, <i>default_interface</i> will be
 * published, with the name of the network interface that has the visible address
 *
 * @author Costin Grigoras <costing@cs.pub.ro>
 * @version 1.0 01/06/2006
 * @since   MonALISA 1.4.6 / MLRepository 1.2.54
 */
public class monIPAddresses extends SchJob implements MonitoringModule {
    private static final long serialVersionUID = 1908364258237134523L;

    private static final Logger logger = Logger.getLogger(monIPAddresses.class.getName());

    private MonModuleInfo mmi = null;

    private MNode mn = null;

    private long lLastCall = 0;

    private String sMyVisibleIPv4 = null;
    private String sMyVisibleIPv6 = null;

    private long lLastVisibleIPCheck = 0;

    private static final long VISIBLE_IP_CHECK_INTERVAL = 1000 * 60 * 60 * 2;
    
    @Override
    public MonModuleInfo init(MNode node, String args) {
        mn = node;

        mmi = new MonModuleInfo();
        mmi.setName("monIPAddresses");
        mmi.setState(0);

        lLastCall = NTPDate.currentTimeMillis();
        mmi.lastMeasurement = lLastCall;
        lLastVisibleIPCheck = lLastCall;

        return mmi;
    }

    // MonitoringModule

    @Override
    public String[] ResTypes() {
        return mmi.getResType();
    }

    @Override
    public String getOsName() {
        return "Linux";
    }

    @Override
    public Object doProcess() throws Exception {
        lLastCall = NTPDate.currentTimeMillis();

        final eResult er = new eResult();
        er.FarmName = getFarmName();
        er.ClusterName = getClusterName();
        er.NodeName = mn.getName();
        er.Module = mmi.getName();
        er.time = lLastCall;

        final LinkedList<InetAddress> lIPs = new LinkedList<InetAddress>();
        final LinkedList<String> lNames = new LinkedList<String>();
        
        final String testSite = AppConfig.getProperty("lia.Monitor.modules.monIPAddresses.testSite", "monalisa.cern.ch");

        if ((sMyVisibleIPv4 == null && sMyVisibleIPv6 == null) || ((lLastCall - lLastVisibleIPCheck) > VISIBLE_IP_CHECK_INTERVAL)) {
        	sMyVisibleIPv4 = null;
        	sMyVisibleIPv6 = null;
            
            final InetAddress[] mladdresses = InetAddress.getAllByName(testSite); 

            for (final InetAddress addr: mladdresses){
            	if ((sMyVisibleIPv4==null && (addr instanceof Inet4Address)) || (sMyVisibleIPv6==null && (addr instanceof Inet6Address))){
            		final Socket s = new Socket();
            		
		            try {
		                s.connect(new InetSocketAddress(addr, 80), 5000);
		                s.setSoTimeout(5000);
		
		                final PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
		                final BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
		
		                pw.print("GET /ip.php HTTP/1.0\r\n");
		                pw.print("Host: "+testSite+"\r\n");
		                pw.print("User-Agent: MonALISA (http://monalisa.caltech.edu/)\r\n");
		                pw.print("\r\n");
		                pw.flush();
		
		                String sIP = br.readLine();
		                
		                while ( (sIP = br.readLine()) != null && sIP.length()>0 ){
		                	// nothing
		                }
		                
		                sIP = br.readLine();
		                
		                br.close();
		                pw.close();
		
		                if (sIP != null) {
		                	InetAddress checkAddr = InetAddress.getByName(sIP);
		                	
		                	if (checkAddr!=null){
		                		if (checkAddr instanceof Inet4Address)
		                			sMyVisibleIPv4 = sIP;
		                		else
		                			sMyVisibleIPv6 = sIP;
		                	}
		                	
		                    lLastVisibleIPCheck = lLastCall;
		                } else {
		                    // parse error, try again in at least one hour
		                    lLastVisibleIPCheck = (lLastCall - VISIBLE_IP_CHECK_INTERVAL) + (1000 * 60 * 60);
		                }
		            } catch (final Throwable t) {
		            	// ignore
		            	logger.log(Level.INFO, "Exception connecting to "+addr, t);
		            } finally {
		                try {
		                    s.close();
		                } catch (Exception e) {
		                    // ignore socket close exception
		                }
		            }
            	}
            }
            
            if (sMyVisibleIPv4 == null && sMyVisibleIPv6 == null){
                // connectivity problems, try again in at least 10 minutes
                lLastVisibleIPCheck = (lLastCall - VISIBLE_IP_CHECK_INTERVAL) + (1000 * 60 * 10);

                logger.log(Level.FINE, "Could not determine the visible IP address");
            }
        }

        try {
            final Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();

            InetAddress ip;
            NetworkInterface ni;
            Enumeration<InetAddress> ipAddresses;

            while (netInterfaces.hasMoreElements()) {
                ni = netInterfaces.nextElement();

                ipAddresses = ni.getInetAddresses();

                while (ipAddresses.hasMoreElements()) {
                    ip = ipAddresses.nextElement();

                    if (!ip.isLoopbackAddress()) {
                        if (ip.isSiteLocalAddress() || ip.isLinkLocalAddress() || ip.isMulticastAddress()) {
                            lIPs.addLast(ip);
                            lNames.addLast(ni.getName());
                        } else {
                            lIPs.addFirst(ip);
                            lNames.addFirst(ni.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not enumerate the local IP addresses", e);

            return null;
        }

        String sDefaultInterface = null;

        // try to put first the visible IP, if any
        if ((sMyVisibleIPv4 != null || sMyVisibleIPv6 != null) && (lIPs.size() > 1)) {
            for (int i = 0; i < lIPs.size(); i++) {
                String sIP = lIPs.get(i).toString();

                // remove the hostname part
                if (sIP.indexOf('/') >= 0) {
                    sIP = sIP.substring(sIP.lastIndexOf('/') + 1);
                }
                
                // remove the zone
                if (sIP.indexOf('%') >= 0) {
                    sIP = sIP.substring(0, sIP.indexOf('%'));
                }

                if (sIP.equals(sMyVisibleIPv4) || sIP.equals(sMyVisibleIPv6)) {
                    sDefaultInterface = lNames.get(i);

                    if (i > 0) { // move the address and the interface name on the first position in the list
                        final InetAddress iaTemp = lIPs.remove(i);
                        lIPs.addFirst(iaTemp);

                        final String sTemp = lNames.remove(i);
                        lNames.addFirst(sTemp);
                    }
                }
            }
        }

        for (int i = 0; i < lIPs.size(); i++) {
            final InetAddress ip = lIPs.get(i);

            String sIP = ip.toString();

            // remove the hostname part
            if (sIP.indexOf('/') >= 0) {
                sIP = sIP.substring(sIP.lastIndexOf('/') + 1);
            }
            
            if (sIP.indexOf('%') >= 0){
            	sIP = sIP.substring(0, sIP.indexOf('%'));
            }

            String sName = "ip" + i;

            if (ip instanceof java.net.Inet4Address) {
                sName += "_v4";
            } else if (ip instanceof java.net.Inet6Address) {
                sName += "_v6";
            } else {
                continue; // ignore other types of addresses, if any ...
            }

            String sAddrType;

            if (ip.isSiteLocalAddress()) {
                sAddrType = "private";
            } else if (ip.isLinkLocalAddress()) {
                sAddrType = "link";
            } else if (ip.isMulticastAddress()) {
                sAddrType = "multicast";
            } else {
                sAddrType = "public";
            }

            sName += "_" + sAddrType + "_" + lNames.get(i);

            er.addSet(sName, sIP);

            if (ip instanceof java.net.Inet4Address) {
                er.addSet(lNames.get(i) + "_IPv4", sIP);
                er.addSet(lNames.get(i) + "_IPv4_type", sAddrType);
            } else {
                er.addSet(lNames.get(i) + "_IPv6", sIP);
                er.addSet(lNames.get(i) + "_IPv6_type", sAddrType);
            }
        }

        if (sMyVisibleIPv4 != null) {
            er.addSet("ip_visible", sMyVisibleIPv4);
        }
        
        if (sMyVisibleIPv6 != null) {
            er.addSet("ip_visible_v6", sMyVisibleIPv6);
        }

        if (sDefaultInterface != null) {
            er.addSet("default_interface", sDefaultInterface);
        }

        final Vector<Object> vReturn = new Vector<Object>(1);
        vReturn.add(er);

        return vReturn;
    }

    @Override
    public MNode getNode() {
        return mn;
    }

    @Override
    public String getClusterName() {
        return mn.getClusterName();
    }

    @Override
    public String getFarmName() {
        return mn.getFarmName();
    }

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return mmi.getName();
    }

    @Override
    public MonModuleInfo getInfo() {
        return mmi;
    }

    /**
     * Test case for the module
     * @param args
     * @throws Exception 
     */
    public static void main(String args[]) throws Exception {
        String host = "localhost"; // args[0] ;
        monIPAddresses aa = new monIPAddresses();
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        aa.init(new MNode(host, ad, null, null), null);

        try {
            Object cb = aa.doProcess();

            System.out.println(cb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread.sleep(1000 * 60 * 2);

        try {
            Object cb = aa.doProcess();

            System.out.println("\n ------ \n");
            System.out.println(cb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
