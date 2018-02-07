package lia.Monitor.Agents.SchedAgents;

import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.AgentsEngine;
import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.AppConfig;
import net.jini.core.lookup.ServiceItem;

public class SchedAgentLoader {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(SchedAgentLoader.class.getName());

    public static void loadAgents(AgentsEngine agentsEngine, String serviceID, Cache cache) {

        ServiceItem si;
        String farmHome = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);

        if ((farmHome == null) || (farmHome.length() == 0)) {
            logger.log(Level.WARNING, "SchedAgentLoader could not determine FARM_HOME");
            return;
        }

        String fileName = farmHome + System.getProperty("file.separator") + "agent.properties";
        System.out.println("### confFile: " + fileName);

        Properties schedProperties = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileName);
            schedProperties.load(fis);
        } catch (Exception e) {
            logger.log(Level.WARNING, "SchedAgentLoader could not find the "
                    + "agent.properties file. No schedule agent will be loaded.");
            return;
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Throwable ignore) {
            }
            ;
        }

        /* load the scheduling agents */
        Enumeration keys = schedProperties.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            if (key.endsWith("_classname")) {
                String agentName = key.replaceAll("_classname", "");
                String className = (String) schedProperties.get(key);

                String groupName = (String) schedProperties.get(agentName + "_group");

                if (className.equals("lia.Monitor.Agents.SchedAgents.UserAgent")) {
                    UserAgent userAg = new UserAgent(agentName, groupName, serviceID);
                    agentsEngine.addAgent(userAg);
                    userAg.initCache(cache);
                    try {
                        cache.addFilter(userAg);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }

                if (className.equals("lia.Monitor.Agents.SchedAgents.ResourceAgent")) {
                    ResourceAgent resAg = new ResourceAgent(agentName, groupName, serviceID);
                    agentsEngine.addAgent(resAg);
                    resAg.initCache(cache);
                    try {
                        cache.addFilter(resAg);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
    }
}
