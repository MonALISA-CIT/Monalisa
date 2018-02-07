package lia.util;

import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.config.ConfigurationFile;
import net.jini.config.ConfigurationProvider;

public class JiniConfigProvider {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(JiniConfigProvider.class.getName());

    /** Return an Exporter Configuration suitable for NON-secure LUSs */
    public static Configuration getBasicExportConfig() {
        StringBuilder config = new StringBuilder();
        String[] options = new String[] { "-" };

        config.append("import java.net.NetworkInterface;\n");
        config.append("net.jini.discovery.LookupDiscovery {\n");
        config.append("multicastInterfaces = new NetworkInterface[]{};\n");
        config.append("}//net.jini.discovery.LookupDiscovery\n");

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Config content:\n\n" + config.toString());
        }

        StringReader reader = new StringReader(config.toString());

        try {
            return new ConfigurationFile(reader, options);
        } catch (ConfigurationException ce) {
            logger.log(Level.SEVERE, "Cannot get config object");
        }
        return null;
    }

    /** Return an Exporter default Configuration suitable for SECURE LUSs */
    public static Configuration getSecureExportConfig() {
        StringBuilder config = new StringBuilder();
        String[] options = new String[] { "-" };

        config.append("import net.jini.constraint.BasicMethodConstraints;\n");
        config.append("import net.jini.core.constraint.ClientAuthentication;\n");
        config.append("import net.jini.core.constraint.InvocationConstraint;\n");
        config.append("import net.jini.core.constraint.InvocationConstraints;\n");
        config.append("import net.jini.core.constraint.ServerAuthentication;\n");
        config.append("import net.jini.discovery.LookupDiscovery;\n");
        config.append("import net.jini.jeri.*;\n");
        config.append("import net.jini.jeri.ssl.*;\n");
        config.append("import net.jini.security.*;\n");
        config.append("import java.security.Permission;\n");
        config.append("import java.net.NetworkInterface;\n");

        config.append("net.jini.discovery.LookupDiscovery {\n");
        config.append("private serviceLookupConstraints = new BasicMethodConstraints(new InvocationConstraints(new InvocationConstraint[]{/*ClientAuthentication.YES*/}, new InvocationConstraint[]{ClientAuthentication.YES,ServerAuthentication.YES}));\n");
        config.append("static registrarPreparer = new BasicProxyPreparer( false, /*do not verify proxy from LUS*/ serviceLookupConstraints,  new Permission[] {  });\n");
        config.append(" }//end net.jini.discovery.LookupDiscovery\n");

        config.append("net.jini.lookup.JoinManager {\n");
        config.append("static registrarPreparer    = net.jini.discovery.LookupDiscovery.registrarPreparer;\n");
        config.append("static registrationPreparer = net.jini.discovery.LookupDiscovery.registrarPreparer;\n");
        config.append("static serviceLeasePreparer = net.jini.discovery.LookupDiscovery.registrarPreparer;\n");
        config.append("multicastInterfaces = new NetworkInterface[]{};\n");
        config.append("}//end net.jini.lookup.JoinManager\n");

        logger.log(Level.INFO, "Config content:\n" + config.toString());

        StringReader reader = new StringReader(config.toString());

        try {
            return new ConfigurationFile(reader, options);
        } catch (ConfigurationException ce) {
            logger.log(Level.SEVERE, "Cannot get config object");
        }
        return null;
    }

    /** Return an Exporter Configuration based on defined properties */
    public static Configuration getUserDefinedConfig() {
        Configuration cfgLUSs = null;

        String cfgFile = AppConfig.getProperty("lia.Monitor.JiniConfig");
        try {
            if (cfgFile != null) {
                String[] configArgs = new String[] { cfgFile };
                cfgLUSs = ConfigurationProvider.getInstance(configArgs);
            }
        } catch (ConfigurationException e) {
            logger.log(Level.WARNING, "Failed loading config from file " + cfgFile);
        }
        if (cfgLUSs == null) {
            try {
                if (SecureContextExecutor.getInstance().bUseSecureLUSs) {
                    cfgLUSs = JiniConfigProvider.getSecureExportConfig();
                } else {
                    cfgLUSs = JiniConfigProvider.getBasicExportConfig();
                }
            } catch (Exception ex) {
                // anyway, if the error comes from SecureContextExecutor, it will not get here,
                // as the getInstance method would have already been called before
                logger.log(Level.SEVERE, "Failed getting Basic or Secure Export Config", ex);
            }
        }
        return cfgLUSs;
    }
}
