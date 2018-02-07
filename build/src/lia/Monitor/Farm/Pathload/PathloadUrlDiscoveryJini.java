/**
 * 
 */
package lia.Monitor.Farm.Pathload;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.GenericMLEntry;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;

/**
 * @author heri
 *
 */
public class PathloadUrlDiscoveryJini {

    /**
     * My Logger Component
     */
    private static final Logger logger = Logger.getLogger(PathloadUrlDiscoveryJini.class.getName());

    private String myUrl;
    private String myCommuniytName;
    private String mySerial;
    private String myServiceID;

    public PathloadUrlDiscoveryJini(String myCommunityName, String myUrl, String mySerial) {
        this.myUrl = myUrl;
        this.myCommuniytName = myCommunityName;
        this.mySerial = mySerial;
    }

    /**
     * @return Returns the myServiceID.
     */
    public String getMyServiceID() {
        return myServiceID;
    }

    /**
     * @param myServiceID The myServiceID to set.
     */
    public void setMyServiceID(String myServiceID) {
        this.myServiceID = myServiceID;
    }

    /**
     * @return Returns the mySerial.
     */
    public String getMySerial() {
        return mySerial;
    }

    /**
     * @param mySerial The mySerial to set.
     */
    public void setMySerial(String mySerial) {
        this.mySerial = mySerial;
    }

    /**
     * @return Returns the myCommuniytName.
     */
    public String getMyCommuniytName() {
        return myCommuniytName;
    }

    /**
     * @param myCommuniytName The myCommuniytName to set.
     */
    public void setMyCommuniytName(String myCommuniytName) {
        this.myCommuniytName = myCommuniytName;
    }

    /**
     * @return Returns the myUrl.
     */
    public String getMyUrl() {
        return myUrl;
    }

    /**
     * @param myUrl The myUrl to set.
     */
    public void setMyUrl(String myUrl) {
        this.myUrl = myUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PathloadUrlDiscoveryJini)) {
            return false;
        }

        PathloadUrlDiscoveryJini pud = (PathloadUrlDiscoveryJini) obj;
        if ((myCommuniytName == null) || (myServiceID == null) || (myUrl == null) || (mySerial == null)) {
            return false;
        }

        return this.myCommuniytName.equals(pud.getMyCommuniytName()) && this.myServiceID.equals(pud.getMyServiceID())
                && this.myUrl.equals(pud.getMyUrl()) && this.mySerial.equals(pud.getMySerial());
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return "[CommunityName: " + myCommuniytName + "] [URL: " + myUrl + "] [SID: " + myServiceID + "] [Serial: "
                + mySerial + "]";
    }

    /**
     * 
     * @param peerGroupName
     * @return
     */
    public static Vector getUrls(String peerGroupName) {
        Vector vResult = new Vector();
        if (peerGroupName == null) {
            logger.log(Level.INFO, "Community Name is null. Please assign a community name "
                    + "before issuing a query.");
            return null;
        }

        MLLUSHelper mllus = MLLUSHelper.getInstance();
        ServiceItem[] sis = mllus.getPathloadServices();
        if ((sis != null) && (sis.length != 0)) {
            for (ServiceItem s : sis) {
                Entry[] entries = s.attributeSets;
                if (((entries != null) && (entries.length > 0))
                        && ((entries[0] != null) && (entries[0] instanceof GenericMLEntry))) {
                    GenericMLEntry glme = (GenericMLEntry) entries[0];
                    Hashtable hash = glme.hash;

                    String community = null;
                    String url = null;
                    String serial = null;
                    if ((hash != null) && (hash.containsKey("COMMUNITY"))) {
                        community = (String) hash.get("COMMUNITY");
                    }
                    if ((hash != null) && (hash.containsKey("URL"))) {
                        url = (String) hash.get("URL");
                    }
                    if ((hash != null) && (hash.containsKey("SERIAL"))) {
                        serial = (String) hash.get("SERIAL");
                    }

                    if ((community != null) && (url != null) && (serial != null)) {
                        if (peerGroupName.equals(community)) {
                            PathloadUrlDiscoveryJini pud = new PathloadUrlDiscoveryJini(community, url, serial);
                            pud.setMyServiceID(s.serviceID.toString());
                            vResult.add(pud);
                            logger.log(Level.INFO, "[PathloadUrlDiscoveryJini] " + pud.toString()
                                    + " added to UrlCache.");
                        }
                    }
                }
            }
        }

        return vResult;
    }
}
