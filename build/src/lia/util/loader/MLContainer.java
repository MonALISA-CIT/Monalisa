package lia.util.loader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DateFileWatchdog;

/**
 * Wrapper class over a ClassLoader. Accepts a list of JarFiles which it monitors
 * for changes. Whenever a change is detected reload is set to true()
 * @author ramiro
 */
public class MLContainer extends Observable implements Observer {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLContainer.class.getName());

    /**
     * The watchdogs used by this class loader
     */
    private DateFileWatchdog[] dfws;
    
    /**
     * Class loader used by this container
     */
    private URLClassLoader classLoader;
    
    /**
     * The manifests for the jar files used by this container
     */
    private Manifest[] manifests;
    private AtomicBoolean notified;
    
    public MLContainer(String[] paths, final long checkDelay) throws Exception {
        
        long dt = checkDelay;
        notified = new AtomicBoolean(false);
        if(dt < 5 * 1000) {
            dt = 5 * 1000;
        }
        
        if(paths == null || paths.length == 0) {
            cleanup();
            throw new Exception("[ MLContainer ] Paths[] cannot be null or empty.");
        }
        
        dfws = new DateFileWatchdog[paths.length];
        URL[] urls = new URL[dfws.length];
        manifests = new Manifest[paths.length];
        
        for(int i=0; i<paths.length; i++) {
            if(paths[i] != null) {
                if(paths[i].endsWith(".jar")) {
                    File f = new File(paths[i]);
                    if(f.exists() && f.canRead()) {
                        JarFile jf = new JarFile(f);
                        manifests[i] = jf.getManifest();
                        urls[i] = new URL("file:" + paths[i]);
                        DateFileWatchdog dfw = DateFileWatchdog.getInstance(f, checkDelay);
                        dfw.addObserver(this);
                        dfws[i] = dfw;
                    } else {
                        cleanup();
                        throw new Exception("[ MLContainer ] The file ( " + paths[i] + " ) do not exist or cannot be read");
                    }
                } else {
                    cleanup();
                    throw new Exception("[ MLContainer ] The file ( " + paths[i] + " ) must be a jar file!");
                }
            } else {
                cleanup();
                throw new Exception("[ MLContainer ] Paths cannot be null.");
            }
        }
        if(logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "\n\n [ MLContainer ] URLs: " + Arrays.toString(urls) + "\n\n");
        }
        classLoader = new URLClassLoader(urls);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
    
    public Manifest[] getManifests() {
        return manifests;
    }
    
    public void cleanup() {
        if(notified.compareAndSet(false, true)) {
            try {
                //remove all file watchdogs
                if(dfws != null) {
                    for(int i=0; i<dfws.length; i++) {
                        if(dfws[i] != null) {
                            dfws[i].stopIt();
                        }
                        dfws[i] = null;
                        manifests[i] = null;
                    }
                }

            } catch(Throwable t) {
                logger.log(Level.WARNING, " [ MLContainer ] Got exception in cleanup()", t);
            } finally {

                //cleanup
                classLoader = null;
                dfws = null;
                manifests = null;
            }
        }//if()
    }
    
    public void update(Observable o, Object arg) {
        try {
           if(o instanceof DateFileWatchdog) {
               DateFileWatchdog dfw = (DateFileWatchdog)o;
               logger.log(Level.INFO, " [ MLContainer ] :- File " + dfw.getFile() + " has changed. Will stop all watchdogs.");
               cleanup();
               setChanged();
               notifyObservers(dfw.getFile().getAbsolutePath());
           } else {//should not get here
               logger.log(Level.WARNING, "\n\n [ MLContainer ]  *WARNING* Got a notif from a non DateFileWatcher \n\n");
           }
        } catch(Throwable t) {
            logger.log(Level.WARNING, "[ MLContainer ] Got exception notifying modif of a file ", t);
        }
    }

}
