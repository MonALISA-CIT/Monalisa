/**
 * 
 */
package lia.util.Pathload.server.servlet;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import lia.util.Pathload.server.manager.GarbageManager;

/**
 * At application startup and shutdown we must insure that a 
 *		System.out.println("Do this shutdown ... [Garbage Manager]"); peer cache is up and running and that a cleanup thread,
 * Garbage Manager is running.
 * @author heri
 *
 */
public class PathloadContextListener implements ServletContextListener {

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(PathloadContextListener.class.getName());

    private GarbageManager gm;

    /** 
     * @see javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
     */
    @Override
    public void contextInitialized(ServletContextEvent arg0) {

        logger.log(Level.INFO, "[PathloadConfig] Application starting up ...");

        /*
        Properties properties = new Properties();
        try {
        	String filename = ResourceLoader.getResourceAsFileString(
        			ResourceLoader.class, 
        			"../../../../../properties/PathloadConfig.properties");
            properties.load(new FileInputStream(filename));
            
            String sMaxAgingTime = (String) properties.getProperty(
               		"lia.util.Pathload.server.PeerCache.maxAgingTime",
               		"" + PeerCache.MAX_AGING_TIME);	
            String sMaxDeadPeerCount = (String) properties.getProperty(
               		"lia.util.Pathload.server.PeerCache.maxMaxDeadPeerCount",
               		"" + PeerCache.MAX_DEAD_PEER_COUNT);
            String sMinWaitingTime = (String) properties.getProperty(
               		"lia.util.Pathload.server.PeerCache.minWaitingTime",
               		"" + PeerCache.MIN_WAITING_TIME);
            String sMaxTokenAgingTime = (String) properties.getProperty(
               		"lia.util.Pathload.server.Token.maxTokenAgingTime",
               		"" + Token.MAX_TOKEN_AGING_TIME);
            String sMinPathloadVersion = (String) properties.getProperty(
               		"lia.util.Pathload.server.Pathload.minVersion",
               		"" + PathloadVersionFilter.MINIMUM_VERSION);
            
            SetupManager sm = new SetupManager();
            sm.setMaxTokenAgingTime(Long.parseLong(sMaxAgingTime));
            sm.setMaxDeadPeerCount(Integer.parseInt(sMaxDeadPeerCount));
            sm.setMinWaitingTime(Long.parseLong(sMinWaitingTime));
            sm.setMaxTokenAgingTime(Long.parseLong(sMaxTokenAgingTime));
            sm.setPathloadMinVersion(Integer.parseInt(sMinPathloadVersion));

        } catch (ResourceMissingException e) {
        	logger.log(Level.WARNING, "PathloadConfig.properties not found in WEB-INF/properties.");
        } catch (IOException e) {
        	logger.log(Level.WARNING, "PathloadConfig.properties not found in WEB-INF/properties.");
        }	        
            
            
        */

        gm = new GarbageManager();
        gm.startup();
    }

    /** 
     * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
     */
    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        gm.shutdown();
        logger.log(Level.INFO, "[PathloadConfig] Application has shut down ...");
    }

}
