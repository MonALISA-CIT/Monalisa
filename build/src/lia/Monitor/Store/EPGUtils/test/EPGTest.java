/*
 * $Id: EPGTest.java 6946 2010-11-12 16:54:44Z ramiro $
 *
 * Created on Oct 13, 2010
 *
 */
package lia.Monitor.Store.EPGUtils.test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.EPGUtils.EmbeddedPGUtils;
import lia.Monitor.monitor.AppConfig;
import lia.util.process.ExternalProcess;
import lia.util.process.ProcessNotifier;


/**
 *
 * @author ramiro
 *
 */
public class EPGTest {
    static final Logger logger = Logger.getLogger(EPGTest.class.getName());

  private static final class LoggingProcessNotifier implements ProcessNotifier {
    
            final Level loggingLevel;
            final String cmdPrefix;
            
            LoggingProcessNotifier(Level loggingLevel, final String cmdPrefix) {
                this.cmdPrefix = (cmdPrefix == null)?"":cmdPrefix;
                this.loggingLevel = loggingLevel;
            }
            
            /* (non-Javadoc)
             * @see lia.util.process.ProcessNotifier#notifyStdOut(lia.util.process.ExternalProcess, java.util.List)
             */
            @Override
            public void notifyStdOut(ExternalProcess p, List<String> line) {
                if(logger.isLoggable(loggingLevel)) {
                    logger.log(loggingLevel, cmdPrefix + " lineStdOut:- " + line);
                }
            }

            /* (non-Javadoc)
             * @see lia.util.process.ProcessNotifier#notifyStdErr(lia.util.process.ExternalProcess, java.util.List)
             */
            @Override
            public void notifyStdErr(ExternalProcess p, List<String> line) {
                if(logger.isLoggable(loggingLevel)) {
                    logger.log(loggingLevel, cmdPrefix + " lineStdErr:- " + line);
                }
            }

            /* (non-Javadoc)
             * @see lia.util.process.ProcessNotifier#notifyProcessFinished(lia.util.process.ExternalProcess)
             */
            @Override
            public void notifyProcessFinished(ExternalProcess p) {
                if(logger.isLoggable(loggingLevel)) {
                    logger.log(loggingLevel, cmdPrefix + " :- proc finished ");
                }
            }
            
        }
    
    public static final void main(String[] args) {
        System.setProperty("MonaLisa_HOME", "/home/ramiro/WORK/EPGSQL/PG_UPGRADE_TESTS/MonaLisa");
        System.setProperty("lia.Monitor.Farm.HOME", "/home/ramiro/WORK/EPGSQL/PG_UPGRADE_TESTS/MonaLisa/Service/myFarm");
        
        final String MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME");
        // relative to this path we get the jar file containing the database
        if (MonaLisa_home == null) {
            throw new IllegalArgumentException("Unable to determine MonaLisa_HOME");
        }
        String sPG_PATH = AppConfig.getGlobalEnvProperty("PGSQL_PATH", null);
        final String FARM_home = AppConfig.getProperty("lia.Monitor.Farm.HOME");

        // if this variable is not set fallback to the FARM_home folder, if possible
        if (sPG_PATH == null || sPG_PATH.length() == 0) {
            if (FARM_home == null || FARM_home.length() == 0) {
                throw new IllegalArgumentException("Unable to determine lia.Monitor.Farm.HOME");
            }

            sPG_PATH = FARM_home;
        }

        sPG_PATH = sPG_PATH.trim();

        logger.log(Level.INFO, "Embedded PGdb location will be : " + sPG_PATH);
        
        EmbeddedPGUtils.upgradeAndStartEmbeddedPG();
    }

}
