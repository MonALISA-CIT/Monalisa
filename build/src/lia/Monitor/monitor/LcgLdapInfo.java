package lia.Monitor.monitor;

import java.io.Serializable;

/**
 * Result for the lcg ldap database...
 * @author cipsm
 *
 */
public class LcgLdapInfo implements Serializable {

    private static final long serialVersionUID = 3906366017574810418L;

    /** The name of the site */
    public String name = "N/A";
    
    /** Coordonates... */
    public double latitude = 0D, longitude = 0D;
    
    /** The correspondent web site */
    public String webURL = "N/A";

    /** The description of the site */
    public String siteDescription = null;
    
    /** The site support contact */
    public String siteSupportContact = null;
    
    /** Sys admin contact for the site */
    public String sysAdminContact = null;
    
    /** Site security contact */
    public String siteSecurityContact = null;
    
    /** The location of the site */
    public String siteLocation = null;
    
    /** Who is sponsoring the site */
    public String siteSponsor = null;
    
    public String siteOtherInfo = null;
    
    public int tierType = -1; // Tier 1, Tier 2 or even Tier 0, -1 means unknown type of tier
    
    public String connectedTo = null; // to what other site is this one connected
    
    // Attributes related to the host....
    
    public String applicationSoftwareRunTimeEnvironment = null;
    public String architecturePlatformType = null;
    public String architectureSMPSize = null;
    public String localFileSystemName = null;
    public String localFileSystemRoot = null;
    public long localFileSystemSize = 0L;
    public long localFileSystemAvailableSpace = 0L;
    public boolean localFileSystemReadOnly = false;
    public String localFileSystemType = null;
    public long mainMemoryRAMSize = 0L;
    public long mainMemoryRAMAvailable = 0L;
    public long mainMemoryVirtualSize = 0L;
    public long mainMemoryVirtualAvailable = 0L;
    public String networkAdapterOutboundIP = null;
    public String networkAdapterInboundIP = null;
    public String networkAdapterName = null;
    public String networkAdapterIPAddress = null;
    public String networkAdapterMTU = null;
    public String operatingSystemName = null;
    public String operatingSystemRelease = null;
    public String operatingSystemVersion = null;
    public double processorLoadLast1Min = 0D;
    public double processorLoadLast5Min = 0D;
    public double processorLoadLast15Min = 0D;
    public String processorVendor = null;
    public String processorModel = null;
    public String processorVersion = null;
    public String processorClockSpeed = null;
    public String processorInstructionSet = null;
    
    public String toString() {
    	StringBuilder buf = new StringBuilder();
    	buf.append("LcgLdapInfo name(").append(name).append(") lat(").append(latitude).append(") long(").append(longitude).append(") web(").append(webURL).append(")").append("\n");
    	buf.append(" siteDescription(").append(siteDescription).append(") siteSupportContact(").append(siteSupportContact).append(") sysAdmin(").append(sysAdminContact).append("\n");
    	buf.append(") siteSecurity(").append(siteSecurityContact).append(") siteLocation(").append(siteLocation).append(") sponsor(").append(siteSponsor).append("\n");
    	buf.append(") applicationSoftwareRunTime(").append(applicationSoftwareRunTimeEnvironment).append(")").append("\n");
    	buf.append(" architecturePlatformType(").append(architecturePlatformType).append(")").append("\n");
    	buf.append(" architectureSMPSize(").append(architectureSMPSize).append(")").append("\n");
    	buf.append(" localFileSystemName(").append(localFileSystemName).append(")").append("\n");
    	buf.append(" localFileSystemRoot(").append(localFileSystemRoot).append(")").append("\n");
    	buf.append(" localFileSystemSize(").append(localFileSystemSize).append(")").append("\n");
    	buf.append(" localFileSystemAvailableSpace(").append(localFileSystemAvailableSpace).append(")").append("\n");
    	buf.append(" localFileSystemReadOnly(").append(localFileSystemReadOnly).append(")").append("\n");
    	buf.append(" localFileSystemType(").append(localFileSystemType).append(")").append("\n");
    	buf.append(" mainMemoryRAMSize(").append(mainMemoryRAMSize).append(")").append("\n");
    	buf.append(" mainMemoryRAMAvailable(").append(mainMemoryRAMAvailable).append(")").append("\n");
    	buf.append(" mainMemoryVirtualSize(").append(mainMemoryVirtualSize).append(")").append("\n");
    	buf.append(" mainMemoryVirtualAvailable(").append(mainMemoryVirtualAvailable).append(")").append("\n");
    	buf.append(" networkAdapterOutboundIP(").append(networkAdapterOutboundIP).append(")").append("\n");
    	buf.append(" networkAdapterInboundIP(").append(networkAdapterInboundIP).append(")").append("\n");
    	buf.append(" networkAdapterName(").append(networkAdapterName).append(")").append("\n");
    	buf.append(" networkAdapterIPAddress(").append(networkAdapterIPAddress).append(")").append("\n");
    	buf.append(" networkAdapterMTU(").append(networkAdapterMTU).append(")").append("\n");
    	buf.append(" operatingSystemName(").append(operatingSystemName).append(")").append("\n");
    	buf.append(" operatingSystemRelease(").append(operatingSystemRelease).append(")").append("\n");
    	buf.append(" operatingSystemVersion(").append(operatingSystemVersion).append(")").append("\n");
    	buf.append(" processorLoadLast1Min(").append(processorLoadLast1Min).append(")").append("\n");
    	buf.append(" processorLoadLast5Min(").append(processorLoadLast5Min).append(")").append("\n");
    	buf.append(" processorLoadLast15Min(").append(processorLoadLast15Min).append(")").append("\n");
    	return buf.toString();
    }
    
} // end of class LcgLdapInfo


