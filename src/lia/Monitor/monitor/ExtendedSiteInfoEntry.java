/*
 * $Id: ExtendedSiteInfoEntry.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.monitor;

/** 
 * Extended Info...This classes like SiteInfoEntry and ExtendedSiteInfoEntry
 * should all be in MonaLisaEntry...but there's a problem with the LUS.
 * 
 * When it will be fixed ( if...) all thew code will go in MonaLisaEntry
 * 
 */ 
public class ExtendedSiteInfoEntry extends net.jini.entry.AbstractEntry {
    
    private static final long serialVersionUID = -4662432585559441284L;
    
    /** Contact Person Name */
    public String localContactName = "N/A";
    /** Contact Person Email */
    public String localContactEMail = "N/A";
    /** JVM Version */
    public String JVM_VERSION = "N/A";
    /** LIBC Version -> /lib/libc.so.6 */
    public String LIBC_VERSION = "N/A";
}
