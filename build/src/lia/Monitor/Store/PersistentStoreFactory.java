package lia.Monitor.Store;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * @author 
 *
 */
public class PersistentStoreFactory {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(PersistentStoreFactory.class.getName());

    private static AbstractPersistentStore _persistentStore = null;

    /**
     * An application specific ClassLoader - it will be used for dynamic loading(at Runtime)
     * for the drivers, or classes stored at specified URL locations.
     * 
     * It is an <code>java.net.URLClassLoader</code> that loads specifed classes found at
     * URLs specified by -Dlia.Monitor.ExternalURLs
     * 
     * Examples:
     * -Dlia.Monitor.StoreURLs=file:///some_local_location/pgjdbc2.jar,http://webserver/store.jar
     * 
     * @see java.net.URLClassLoader
     */
    static URLClassLoader _loader = null;

    //-Dlia.Monitor.StoreClass

    /**
     * This method is used to get the <code>AbstractPersistentStore</code>, based on a specific configuration. The instance is unique!
     * 
     * @see lia.Monitor.Store.AbstractPersistentStore
     * @return The store!
     * @throws lia.Monitor.Store.StoreException
     */
    public static AbstractPersistentStore getPersistentStore() throws StoreException {
        return getPersistentStore(AppConfig.getProperty("lia.Monitor.StoreClass", null));
    }

    /**
     * @param storeClassName
     * @return ?
     * @throws StoreException
     */
    public static AbstractPersistentStore getPersistentStore(String storeClassName) throws StoreException {
        return getPersistentStore(storeClassName, getURLs());
    }

    /**
     * @param storeClassName
     * @param URLs
     * @return ?
     * @throws StoreException
     */
    public static AbstractPersistentStore getPersistentStore(String storeClassName, String[] URLs)
            throws StoreException {
        return getPersistentStore(storeClassName, getURLs(URLs));
    }

    /**
     * @param storeClassName
     * @param URLList
     * @return ?
     * @throws StoreException
     */
    public static AbstractPersistentStore getPersistentStore(final String storeClassName, final URL[] URLList)
            throws StoreException {
        if (_loader == null) {
            try {
                if (URLList != null) {
                    AccessController.doPrivileged(new PrivilegedAction<Object>() {
                        @Override
                        public Object run() {
                            _loader = new URLClassLoader(URLList, AbstractPersistentStore.class.getClassLoader());
                            return null;
                        }
                    });
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "FAILED to initialize ClassLoader", t);
            }
        }

        /*
         * 
         * THIS IS RAD!!!! CHAGE AS SOON AS YOU HAVE TIME!!!!!!!
         * 
         */
        if (_persistentStore == null) {
            try {
                _persistentStore = new PersistentStoreFast(null);
            } catch (Throwable t) {
                t.printStackTrace();
                logger.log(Level.FINER, " Got Exception ", t);
            }
            if (_persistentStore != null) {
                return _persistentStore;
            }
            if ((storeClassName != null)
                    && (storeClassName.equals("lia.Monitor.Store.Sql.Web.MysqlStore") || storeClassName
                            .equals("lia.Monitor.Store.Sql.MysqlStore"))) {
                try {
                    final String[] connIDBParams = { "monitor0", "monitor1", "monitor2", "monitor3", "monitor4", };
                    _persistentStore = new lia.Monitor.Store.Sql.MysqlStore(connIDBParams);

                    return _persistentStore;
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot instantiate MySqlStore", t);
                }
            }

            //            if ( storeClassName != null &&
            //                  storeClassName.equals("lia.Monitor.Store.Sql.EMcKoiStore")
            //                ){
            //                    try {
            //                        final String[] connIDBParams = {
            //                            "monitor0", 
            //                            "monitor1", 
            //                            "monitor2", 
            //                            "monitor3", 
            //                            "monitor4",
            //                        };
            //                       _persistentStore = (AbstractPersistentStore) new EMcKoiStore(connIDBParams);
            //                       return _persistentStore;
            //                    } catch ( Throwable t ) {
            //                        throw new StoreException(t);
            //                    }
            //            }

            if ((storeClassName != null) && (_loader != null)) {
                try {
                    _persistentStore = (AbstractPersistentStore) (_loader.loadClass(storeClassName).newInstance());
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "FAILED to initialize user defined _persistentStore ", t);
                }
            }

            //cannot initialize external store use the DEFAULT -- mcKoi
            logger.log(Level.INFO, "Trying to use the default embedded Sql store");
            if (_persistentStore == null) {
                try {
                    //						   _persistentStore = (AbstractPersistentStore) new EMcKoiStore();
                    return _persistentStore;
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, " [ FAILURE ] -----> FAILED TO INITIALIZE embedded store", t);
                    throw new StoreException(t);
                    //no storing ... we should probably exit for now!?!?
                }
            }
        }

        return _persistentStore;
    }

    /**
     * 
     * Getting URL String from environment
     * -Dlia.Monitor.StoreURLs
     **/
    private static URL[] getURLs() {
        String[] strURLs = null;

        String strURL = AppConfig.getProperty("lia.Monitor.StoreURLs", null);

        if ((strURL != null) && (strURL.length() != 0)) {
            StringTokenizer st = new StringTokenizer(strURL, ",");

            strURLs = new String[st.countTokens()];

            int i = 0;
            while (st.hasMoreTokens()) {
                strURLs[i++] = st.nextToken();
            }
        }

        return getURLs(strURLs);
    }

    private static URL[] getURLs(String[] strURLs) {
        if ((strURLs == null) || (strURLs.length == 0)) {
            return null;
        }

        URL[] _returnURLs = new URL[strURLs.length];
        for (int i = 0; i < strURLs.length; i++) {
            try {
                _returnURLs[i++] = new URL(strURLs[i]);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "URL: " + _returnURLs[i - 1].toString() + " added to URLs");
                }
            } catch (MalformedURLException ex) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "GOT A BAD URL..." + strURLs[i] + "...SKIPPING IT!!", ex);
                }
            }
        }

        return _returnURLs;
    }

}//class PersistentStoreFactory
