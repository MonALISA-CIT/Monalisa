package lia.Monitor.JiniClient.Store;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.ConnMessageMux;
import lia.Monitor.tcpClient.MLSerClient;
import lia.web.utils.Formatare;
import net.jini.core.lookup.ServiceID;

/**
 * 
 * Same as lia.Monitor.tcpClient but without rcFrame
 */
public class JtClient extends MLSerClient {

    /** The Logger */
    private static final Logger log = Logger.getLogger(JtClient.class.getName());

    private final Hashtable<Integer, ClientHolder> htClientHolders;

    private static class ClientHolder {
        /** */
        final Integer key;

        /** */
        final LocalDataFarmClient ldc;

        /** */
        final monPredicate pred;

        /**
         * Copy constructor
         * 
         * @param _key
         * @param _ldc
         * @param _pred
         */
        public ClientHolder(final Integer _key, final LocalDataFarmClient _ldc, final monPredicate _pred) {
            key = _key;
            ldc = _ldc;
            pred = _pred;
        }
    }

    /**
     * Create a new thread for each service that is discovered
     * 
     * @param sid
     * @param name
     * @param _address
     * @param _msgMux 
     * @throws Exception
     */
    public JtClient(ServiceID sid, String name, InetAddress _address, ConnMessageMux _msgMux) throws Exception {

        super(name, _address, _address.getCanonicalHostName(), _msgMux, sid);
        htClientHolders = new Hashtable<Integer, ClientHolder>();
    }

    /** Comparator to check if the same predicate was already registered */
    public static final Comparator<monPredicate> predicatesComparator = new Comparator<monPredicate>() {
        @Override
        public int compare(final monPredicate p1, final monPredicate p2) {
            if (p1 == null) {
                return p2 == null ? 0 : -1;
            }

            if (p2 == null) {
                return 1;
            }

            // farm name
            if (p1.Farm == null) {
                if (p2.Farm != null) {
                    return -1;
                }
            } else if (p2.Farm == null) {
                return 1;
            } else {
                final int c = p1.Farm.compareTo(p2.Farm);

                if (c != 0) {
                    return c;
                }
            }

            // cluster name
            if (p1.Cluster == null) {
                if (p2.Cluster != null) {
                    return -1;
                }
            } else if (p2.Cluster == null) {
                return 1;
            } else {
                final int c = p1.Cluster.compareTo(p2.Cluster);

                if (c != 0) {
                    return c;
                }
            }

            // node name
            if (p1.Node == null) {
                if (p2.Node != null) {
                    return -1;
                }
            } else if (p2.Node == null) {
                return 1;
            } else {
                final int c = p1.Node.compareTo(p2.Node);

                if (c != 0) {
                    return c;
                }
            }

            // parameters
            if (p1.parameters == null) {
                return p2.parameters == null ? 0 : -1;
            }

            if (p2.parameters == null) {
                return 1;
            }

            if (p1.parameters.length != p2.parameters.length) {
                return p1.parameters.length - p2.parameters.length;
            }

            for (int i = 0; i < p1.parameters.length; i++) {
                final int c = p1.parameters[i].compareTo(p2.parameters[i]);

                if (c != 0) {
                    return c;
                }
            }

            return 0;
        }
    };

    @Override
    public void addLocalClient(final LocalDataFarmClient client, final monPredicate pred) {
        super.addLocalClient(client, pred);

        final Integer ikey = Integer.valueOf(pred.id);
        htClientHolders.put(ikey, new ClientHolder(ikey, client, pred));
    }

    /**
     * Unregister a predicate
     * 
     * @param pred
     */
    public void unregister(final monPredicate pred) {
        final ArrayList<ClientHolder> clients = new ArrayList<ClientHolder>(htClientHolders.values());

        for (final ClientHolder ch : clients) {
            if (predicatesComparator.compare(pred, ch.pred) == 0) {
                unregister(ch.key);
            }
        }
    }

    /**
     * @param key
     */
    void unregister(final Object key) {
        final ClientHolder ch = htClientHolders.get(key);

        if (ch != null) {
            ch.pred.id = ch.key.intValue();
            deleteLocalClient(ch.ldc, ch.pred);
            htClientHolders.remove(key);
        }
    }

    private static final boolean configStoreEnabled = AppConfig.getb(
            "lia.Monitor.JiniClient.Store.JtClient.configStoreEnabled", false);

    /**
     * New configuration was received
     * 
     * @param nfarm
     */
    @Override
    public void newConfig(final MFarm nfarm) {
        if (!configStoreEnabled) {
            return;
        }

        farm = nfarm;

        if (farm != null) {
            TransparentStoreFactory.getStore().updateConfig(farm);
            Main.getInstance().updateConfig(farm);
        } else {
            log.log(Level.WARNING, "Got newConfig with MFarm == null ");
        }
    }

    /**
     * Cache with the last version for each service. This is used to avoid unnecessary updates 
     * to the abping_aliases table.
     */
    static final Hashtable<String, String> htVersions = new Hashtable<String, String>(100);

    @Override
    public void postSetMLVersion(final String version) {
        if (version == null) {
            log.log(Level.FINER, "new version is null for '" + FarmName + "', exiting quickly");

            return;
        }

        final String sOldVersion = htVersions.get(FarmName);

        log.log(Level.FINEST, "old version for '" + FarmName + "' is '" + sOldVersion + "', new version is '" + version
                + "'");

        if ((sOldVersion == null) || !sOldVersion.equals(version)) {
            log.log(Level.FINER, "saving new version '" + version + "' for '" + FarmName + "' in memory ht");

            htVersions.put(FarmName, version);

            if (!TransparentStoreFactory.isMemoryStoreOnly() && (version.indexOf("-") > 0)) {
                final String sVersion = version.substring(0, version.indexOf("-"));

                log.log(Level.FINER, "updating database entry for '" + FarmName + "' with value '" + sVersion + "'");

                final DB db = new DB();
                db.query("UPDATE abping_aliases SET version='" + Formatare.mySQLEscape(sVersion) + "' WHERE name='"
                        + Formatare.mySQLEscape(FarmName) + "';");
            }
        }
    }

    /**
     * Get the last received version number for the given farm name
     * 
     * @param sFarmName
     * @return last received version, or null if we don't have this info
     */
    public static String getMLVersion(final String sFarmName) {
        String sRet = htVersions.get(sFarmName);

        if (sRet == null) {
            // info not in memory, falling back to reading last value from the database
            final DB db = new DB();
            
            db.setReadOnly(true);
            
            db.query("SELECT version FROM abping_aliases WHERE name='" + Formatare.mySQLEscape(sFarmName) + "';");

            if (db.moveNext()) {
                sRet = db.gets(1);

                // update the memory cache
                htVersions.put(sFarmName, sRet);
            }
        }

        if ((sRet != null) && (sRet.indexOf("-") > 0)) {
            sRet = sRet.substring(0, sRet.indexOf("-"));
        }

        return sRet;
    }
}
