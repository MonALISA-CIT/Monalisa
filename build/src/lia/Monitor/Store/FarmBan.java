package lia.Monitor.Store;

import java.util.Hashtable;

import lia.Monitor.JiniClient.CommonJini.JiniClient;
import lia.Monitor.Store.Fast.DB;
import lia.web.utils.Formatare;

/**
 * @author costing
 * @since a long time
 */
public class FarmBan {

    private static Hashtable<String, String> htBannedFarms;
    private static Hashtable<String, String> htBannedIPs;
    private static JiniClient main = null;

    static {
		htBannedFarms = new Hashtable<String, String>();
		htBannedIPs = new Hashtable<String, String>();

		if (!TransparentStoreFactory.isMemoryStoreOnly()) {
			final DB db = new DB();
			db.syncUpdateQuery("CREATE TABLE ban_farm (name varchar(255) primary key);", true);
			db.syncUpdateQuery("CREATE TABLE ban_ip (ip varchar(255) primary key);", true);

			db.setReadOnly(true);
			
			db.query("SELECT name FROM ban_farm;");
			while (db.moveNext())
				htBannedFarms.put(db.gets(1), "");

			db.query("SELECT ip FROM ban_ip;");
			while (db.moveNext())
				htBannedIPs.put(db.gets(1), "");
		}
	}

    /**
     * Ban a service by its name
     * 
     * @param sFarmName
     * @see #banIP(String)
     */
    public static void banFarm(final String sFarmName) {
		htBannedFarms.put(sFarmName, "");

		if (main != null)
			main.reloadNode(sFarmName);

		if (TransparentStoreFactory.isMemoryStoreOnly())
			return;

		DB db = new DB();
		db.syncUpdateQuery("INSERT INTO ban_farm (name) VALUES ('" + Formatare.mySQLEscape(sFarmName) + "');", true);
	}

    /**
     * Ban a service by its IP address
     * 
     * @param sIP
     * @see #banFarm(String)
     */
    public static void banIP(final String sIP) {
		htBannedIPs.put(sIP, "");

		if (main != null)
			main.reloadIP(sIP);

		if (TransparentStoreFactory.isMemoryStoreOnly())
			return;

		DB db = new DB();
		db.syncUpdateQuery("INSERT INTO ban_ip (ip) VALUES ('" + Formatare.mySQLEscape(sIP) + "');", true);
	}

    /**
     * Remove the ban for one name (you should consider calling {@link #unbanIP(String)} too 
     * if necessary).
     * 
     * @param sFarmName
     * @see #unbanIP(String)
     */
    public static void unbanFarm(final String sFarmName) {
		htBannedFarms.remove(sFarmName);

		if (main != null)
			main.reloadNode(sFarmName);

		if (TransparentStoreFactory.isMemoryStoreOnly())
			return;

		DB db = new DB();

		if (db.query("DELETE FROM ban_farm WHERE name='" + Formatare.mySQLEscape(sFarmName) + "';", true, true))
			db.maintenance("VACUUM ban_farm;");
	}

    /**
     * Remove the ban for one IP (you should consider calling {@link #unbanFarm(String)} too 
     * if necessary).
     * 
     * @param sIP
     * @see #unbanFarm(String)
     */
    public static void unbanIP(final String sIP) {
		htBannedIPs.remove(sIP);

		if (main != null)
			main.reloadIP(sIP);

		if (TransparentStoreFactory.isMemoryStoreOnly())
			return;

		DB db = new DB();
		if (db.query("DELETE FROM ban_ip WHERE ip='" + Formatare.mySQLEscape(sIP) + "';", true, true))
			db.maintenance("VACUUM ban_ip;");
	}
    
    /**
     * Check if one service name is banned
     * 
     * @param sFarmName
     * @return true if the service name is banned
     */
    public static boolean isFarmBanned(final String sFarmName) {
		if (sFarmName == null)
			return false;

		return htBannedFarms.get(sFarmName) != null;
	}
    
    /**
     * Check if one IP address is banned
     * 
     * @param sIP
     * @return true if the IP address is banned
     */
    public static boolean isIPBanned(final String sIP) {
		if (sIP == null)
			return false;

		return htBannedIPs.get(sIP) != null;
	}
    
    /**
     * Called by the repository. This is to let the engine know what to call when an operation is done,
     * so that ban/unban operations are immediately applied.  
     * 
     * @param _main
     */
    public static void setJiniClient(final JiniClient _main) {
		main = _main;
	}

}
