/*
 * Created on Sep 13, 2005 by Lucian Musat
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.monitor;

import java.util.ArrayList;
import java.util.Map;

/**
 * NetFlow link reported by a node, that connects two points
 * Sep 13, 2005 - 2:03:29 PM
 */
public class NFLink {
    public Map<String, String> from;
    public Map<String, String> to;
    public String fromIP;
    public String toIP;
    public String name;
    public volatile Object data;
    public double fromLAT;
    public double toLAT;
    public double fromLONG;
    public double toLONG;
    public volatile double speed;
    public volatile long time;

    public NFLink(String name) {
        this.name = name;
    }

    /**
     *      ILINK=sidFrom->sidTo from [longFrom,latFrom] to [longTo,latTo]
     */
    @Override
    public String toString() {
        return " NFLink=" + name + " from [" + fromLONG + "," + fromLAT + "] to [" + toLONG + "," + toLAT + "]";
    }

    /**
     * compares two ilinks based on name field.<br>
     * the strings that are compared should look like this:
     *      sidFrom->sidTo 
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NFLink)) {
            return false;
        }
        NFLink link = (NFLink) obj;
        return link.name.equals(name) && link.toIP.equals(toIP) && link.fromIP.equals(fromIP)
                && (Math.abs(link.fromLAT - fromLAT) < 0.0001) && (Math.abs(link.toLAT - toLAT) < 0.0001)
                && (Math.abs(link.fromLONG - fromLONG) < 0.0001) && (Math.abs(link.toLONG - toLONG) < 0.0001);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Sep 13, 2005 3:29:21 PM - mluc<br>
     * converts from ILink to NFLink copying main/general informations
     * taken from iNetGeo config file: iNetGeoConfig<br>
     * @param src source link, should be ILink
     * @return new NFLink node
     */
    public static NFLink convert(ILink src) {
        if (src == null) {
            return null;
        }
        NFLink link = new NFLink(src.name);
        link.from = src.from;
        link.to = src.to;
        link.fromIP = src.fromIP;
        link.toIP = src.toIP;
        link.fromLAT = src.fromLAT;
        link.fromLONG = src.fromLONG;
        link.toLAT = src.toLAT;
        link.toLONG = src.toLONG;
        link.speed = src.speed;
        link.data = src.data;
        link.time = src.time;
        return link;
    }

    /**
     * converts from a list of links with same name but different ips
     * to two lists of links with suffixes _IN and _OUT, one of them 
     * having the opposite sources and destinations of the other
     * @author mluc
     * @since Nov 15, 2006
     * @param src
     */
    public static ArrayList<NFLink> convert(ArrayList<ILink> src) {
        if (src == null) {
            return null;
        }
        ArrayList<NFLink> dst = new ArrayList<NFLink>();
        for (int i = 0; i < src.size(); i++) {
            ILink srcLink = src.get(i);
            //link IN is from "TO" to "FROM"
            NFLink linkIN = new NFLink(srcLink.name + "_IN");
            linkIN.from = srcLink.to;
            linkIN.fromIP = srcLink.toIP;
            linkIN.fromLAT = srcLink.toLAT;
            linkIN.fromLONG = srcLink.toLONG;
            linkIN.to = srcLink.from;
            linkIN.toIP = srcLink.fromIP;
            linkIN.toLAT = srcLink.fromLAT;
            linkIN.toLONG = srcLink.fromLONG;
            linkIN.speed = srcLink.speed;
            linkIN.data = srcLink.data;
            linkIN.time = srcLink.time;
            dst.add(linkIN);

            //link OUT = FROM -> TO
            NFLink linkOUT = new NFLink(srcLink.name + "_OUT");
            linkOUT.from = srcLink.from;
            linkOUT.to = srcLink.to;
            linkOUT.fromIP = srcLink.fromIP;
            linkOUT.toIP = srcLink.toIP;
            linkOUT.fromLAT = srcLink.fromLAT;
            linkOUT.fromLONG = srcLink.fromLONG;
            linkOUT.toLAT = srcLink.toLAT;
            linkOUT.toLONG = srcLink.toLONG;
            linkOUT.speed = srcLink.speed;
            linkOUT.data = srcLink.data;
            linkOUT.time = srcLink.time;
            dst.add(linkOUT);
        }
        return dst;
    }

    public static ArrayList<NFLink> convert(ArrayList<ILink> src, String customName, boolean onlyOut) {
        if (src == null) {
            return null;
        }
        ArrayList<NFLink> dst = new ArrayList<NFLink>();
        for (int i = 0; i < src.size(); i++) {
            ILink srcLink = src.get(i);
            if ((customName != null) && (customName.indexOf("NetLink") >= 0)) {
                //link OUT = FROM -> TO
                NFLink linkOUT = new NFLink(customName);
                linkOUT.from = srcLink.from;
                linkOUT.to = srcLink.to;
                linkOUT.fromIP = srcLink.fromIP;
                linkOUT.toIP = srcLink.toIP;
                linkOUT.fromLAT = srcLink.fromLAT;
                linkOUT.fromLONG = srcLink.fromLONG;
                linkOUT.toLAT = srcLink.toLAT;
                linkOUT.toLONG = srcLink.toLONG;
                linkOUT.speed = srcLink.speed;
                linkOUT.data = srcLink.data;
                linkOUT.time = srcLink.time;
                dst.add(linkOUT);
                continue;
            }
            if (!onlyOut) {
                //link IN is from "TO" to "FROM"
                NFLink linkIN = new NFLink(srcLink.name + "_IN");
                linkIN.from = srcLink.to;
                linkIN.fromIP = srcLink.toIP;
                linkIN.fromLAT = srcLink.toLAT;
                linkIN.fromLONG = srcLink.toLONG;
                linkIN.to = srcLink.from;
                linkIN.toIP = srcLink.fromIP;
                linkIN.toLAT = srcLink.fromLAT;
                linkIN.toLONG = srcLink.fromLONG;
                linkIN.speed = srcLink.speed;
                linkIN.data = srcLink.data;
                linkIN.time = srcLink.time;
                dst.add(linkIN);
            }
            //link OUT = FROM -> TO
            NFLink linkOUT = new NFLink(((customName != null) ? customName : srcLink.name) + "_OUT");
            linkOUT.from = srcLink.from;
            linkOUT.to = srcLink.to;
            linkOUT.fromIP = srcLink.fromIP;
            linkOUT.toIP = srcLink.toIP;
            linkOUT.fromLAT = srcLink.fromLAT;
            linkOUT.fromLONG = srcLink.fromLONG;
            linkOUT.toLAT = srcLink.toLAT;
            linkOUT.toLONG = srcLink.toLONG;
            linkOUT.speed = srcLink.speed;
            linkOUT.data = srcLink.data;
            linkOUT.time = srcLink.time;
            dst.add(linkOUT);
        }
        return dst;
    }
}
