package lia.util;

import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.ShutdownReceiver;

/**
 * The ShutdownManager is a cleanup Manager Class.
 * When the MonAlisa service shuts down and the ShutdownManager 
 * is active (at least one module has been registered to it),
 * the shutdown manager will close the module cleanly
 * by calling its shutdown method.
 *  
 *  Each registered module must implement the ShutdownReceiver
 *  interface.
 *  
 * @author heri
 * @since ML.1.4.10
 *
 */
public class ShutdownManager extends Thread {

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(ShutdownManager.class.getName());

    /**
     * The singleton instance class
     */
    private static ShutdownManager miniMe = new ShutdownManager();

    private final Vector registeredModules;
    private final AtomicBoolean isShuttingDown;

    /**
     * Default Constructor. 
     *
     */
    private ShutdownManager() {
        super("(ML) ShutdownManager");

        isShuttingDown = new AtomicBoolean(false);
        registeredModules = new Vector();

        try {
            Runtime.getRuntime().addShutdownHook(this);
        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE,
                    "IllegalArgumentException caught. Unable to register ShutdownManager" + e.getMessage());
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "IllegalStateException caught. Unable to register ShutdownManager."
                    + " Maybe the JVM is already shutting down. " + e.getMessage());
        } catch (SecurityException e) {
            logger.log(Level.SEVERE, "SecurityException caught. Unable to register ShutdownManager." + e.getMessage());
        }
    }

    /**
     * Class Factory method, it returns the singleton instance of 
     * the ShutdownManager
     * 
     * @return	The ShutdownManager	
     */
    public static ShutdownManager getInstance() {
        return miniMe;
    }

    /**
     * This method is used to register a module to the ShutdownManager
     * The module must implement the shutdownReceiver interface.
     * 
     * @param module	The module to shut down when the ML Service is
     * 					exiting
     */
    public void addModule(ShutdownReceiver module) {
        if (module == null) {
            logger.log(Level.INFO, "ShutdownManager: the module is NULL and will not" + "be registered.");
            return;
        }

        if (!isShuttingDown.get()) {
            if (!registeredModules.contains(module)) {
                registeredModules.add(module);
                logger.log(Level.INFO, "ShutdownManager: [ " + module.getClass() + " ] registered.");
            } else {
                logger.log(Level.INFO, "ShutdownManager: Module is already registered.");
            }
        } else {
            logger.log(Level.INFO, "ShutdownManager is shutting down. No modules may " + "be registered at this time.");
        }
    }

    /**
     * This method is used to de-register a module to the ShutdownManager
     * The module must implement the shutdownReceiver interface.
     * 
     * @param module The module to shut down when the ML Service is
     * 				 exiting
     */
    public void removeModule(ShutdownReceiver module) {
        if (module == null) {
            logger.log(Level.INFO, "ShutdownManager: the module is NULL and will not" + "be deregistered.");
            return;
        }

        if (!isShuttingDown.get()) {
            if (registeredModules.remove(module)) {
                logger.log(Level.INFO, "ShutdownManager: Module successfully deregistered.");
            } else {
                logger.log(Level.INFO, "ShutdownManager: Module is not registered.");
            }
        } else {
            logger.log(Level.INFO, "ShutdownManager is shutting down. No modules may "
                    + "be deregistered at this time.");
        }
    }

    public void shutdownNow() {
        try {

            if (isShuttingDown.compareAndSet(false, true)) {

                logger.log(Level.INFO, "ShudownManager: Cleaning up!");
                System.out.println(new Date() + " ShudownManager: Cleaning up!");

                for (Iterator it = registeredModules.iterator(); it.hasNext();) {
                    try {
                        ShutdownReceiver module = (ShutdownReceiver) it.next();
                        if (module != null) {
                            module.Shutdown();
                        }
                        it.remove();
                    } catch (Throwable t) {
                        //ignore exception in ShutdownReceiver's code 
                    }
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * For all registered modules call the Shutdown method for a clean
     * exit.
     */
    @Override
    public void run() {
        shutdownNow();
    }
}
