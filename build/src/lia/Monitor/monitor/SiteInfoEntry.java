/*
 * $Id: SiteInfoEntry.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.monitor;

/** Stores TCP/IP Info like IPAddress and ports for a site */
public class SiteInfoEntry extends net.jini.entry.AbstractEntry {
    
    private static final long serialVersionUID = -697164529999417997L;

    /** Standard Client Port */
    public Integer ML_PORT = null;

    /** Used if the RMIRegistry is bind on other port than 1099 */
    public Integer REGISTRY_PORT = null;
    
    /** WebServices Port - if started */
    public Integer WS_PORT = null;
    
    /** WebServer CodeBase Port - if started */
    public Integer WEB_CODEBASE_PORT = null;
    
    /** IPAddress of the site */
    public String IPAddress = null;
     
    /** Short Name for the site */
    public String UnitName = null;

}
