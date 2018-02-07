package lia.Monitor.monitor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A Grid Site Info class that should provide all required and desired informations about a site in a grid system
 * @author mluc
 *
 */
public class GridSiteInfo implements Serializable {

    private static final long serialVersionUID = 3906366017574810418L;

    /** The real name of the site corresponding to a hostname usually (unique) */
    public String name = "N/A";
    
    /** The user friendly name for the site */
    public String niceName = "N/A";
    
    /** Coordonates... */
    public double latitude = 0D, longitude = 0D;
    
    /** The correspondent web site */
    public String webURL = "N/A";

    /** The description of the site */
    public String siteDescription = null;
    
    /** The site support contact, the most relevant, no need for admin or security */
    public String siteSupportContact = null;
    
    /** The location of the site */
    public String siteLocation = null;
    
    /** Who is sponsoring the site */
    public String siteSponsor = null;
    
    /** hierarchic ordering of nodes */
    public int tierType = -1; // Tier 1, Tier 2 or even Tier 0, -1 means unknown type of tier
    
    /** name of the site this one is connected to */
    public String connectedTo = null; // to what other site is this one connected
    
    /** list of additional attributes */
    public HashMap mapOtherAttributes = null;
    
    public String toString() {
    	StringBuilder buf = new StringBuilder();
    	buf.append("Grid Site info: name(").append(name).append(") lat(").append(latitude).append(") long(").append(longitude).append(") web(").append(webURL).append(")").append("\n");
    	buf.append(" siteDescription(").append(siteDescription).append(") siteSupportContact(").append(siteSupportContact).append(")\n");
    	buf.append("siteLocation(").append(siteLocation).append(") sponsor(").append(siteSponsor).append(")\n");
    	if ( tierType!=-1 )
    		buf.append("tier type: ").append(tierType).append("\n");
    	if ( connectedTo!=null )
    		buf.append("connected to: ").append(connectedTo).append("\n");
    	if ( mapOtherAttributes!=null ) {
    		buf.append("other attributes:");
    		for (Iterator iter = mapOtherAttributes.entrySet().iterator(); iter.hasNext();)
    		{ 
    		    Map.Entry entry = (Map.Entry)iter.next();
    		    String key = (String)entry.getKey();
    		    String value = (String)entry.getValue();
    		    buf.append(" ").append(key).append("(").append(value).append(")");
    		}
    	}
    	return buf.toString();
    }
    
} // end of class 


