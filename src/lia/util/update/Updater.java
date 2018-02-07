/*
 * Created on May 7, 2003
 */
package lia.util.update;

import lia.util.update.old.OldUpdater;
import lia.util.update.old.UpdaterConfig;

public class Updater {

    public static final String UPDATER_VERSION = "@updaterVersion@";
    public static final String UPDATER_DATE = "@updaterDate@";
    
    public static final void main(String[] args) throws Exception {
        try {
            final boolean bURLUpdater = UpdaterConfig.checkJavaVersion("1.6+");

            final boolean useOldUpdater = UpdaterConfig.getb("lia.util.update.USE_OLD_UPDATER", false);
            
            System.out.println("\nML Updater version: " + UPDATER_VERSION + "-" + UPDATER_DATE);

            if(bURLUpdater && !useOldUpdater) {
                AppRemoteURLUpdater.main(args);
                System.exit(0);
            }
            
            //too bad - cannot update to the latest and greatest
            final boolean oldUpdater = UpdaterConfig.checkJavaVersion("1.4+");
            if (oldUpdater || useOldUpdater) {
                OldUpdater.updateML(args);
                System.exit(0);
            } else {
                System.err.println(" [ Updater ] Unable to determine the updater protocol to use. ML needs at least Java 1.4+ and is highly recommended to use at least Java6! Java Version: " + UpdaterConfig.getJavaVersion());
            }
        } catch (Throwable genexc) {
            System.out.println("Unable to update. Please check the update.log in MonaLisa_HOME/Service/myFarm directory");
            System.err.println("Unable to update. Cause: ");
            genexc.printStackTrace();
            System.exit(1);
        } finally {
            System.out.flush();
            System.err.flush();
        }
    }
}
