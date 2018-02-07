/*
 * $Id: ABPingEntry.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.monitor;

import net.jini.entry.AbstractEntry;

public class ABPingEntry extends AbstractEntry {
    private static final long serialVersionUID = -2424897367747181367L;

    /** UDP Port to which I am bound*/
    public Integer PORT;
    
    /** My IP Address */
    public String IPAddress;
    
    /** My Hostname */
    public String HostName;

    /** My FQDN Hostname */
    public String FullHostName;
}
