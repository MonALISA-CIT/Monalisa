/*
 * Created on Jan 12, 2010
 */
package lia.Monitor.ciena.triggers.repository;

import java.io.File;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.Store.Filter;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.monPredicate;
import lia.util.DateFileWatchdog;
import lia.util.Utils;
import lia.web.utils.Formatare;

/**
 * 
 * Simple trigger to "fix" a messy VCG BUG in Ciena CD/CI; the workaround is to lock/unlock the VCG.
 * 
 * The cause for this is not yet (2010) clear ... after more than 2 years. I don't like the "workaround" but ... such is life.
 * 
 * @author ramiro
 * 
 */
public class CienaVCGTrigger implements Filter, Observer {

    private static final Logger logger = Logger.getLogger(CienaVCGTrigger.class.getName());

    private static final String DEFAULT_SETTINGS_SECTION_NAME = "default";

    private final File confFile;
    private final ConcurrentNavigableMap<String, CienaVCGConfigEntry> configMap;

    public CienaVCGTrigger() throws Exception {
        configMap = new ConcurrentSkipListMap<String, CienaVCGConfigEntry>();
        final String propName = CienaVCGTrigger.class.getName() + ".configFile";
        final String confFileName = AppConfig.getProperty(propName);
        if (confFileName == null) {
            throw new InstantiationException(
                    "The config file for CienaVCGTrigger is not defined. Check that the property: " + propName
                            + " is defined");
        }

        File f = new File(confFileName);
        if (!f.exists() || !f.canRead()) {
            throw new InstantiationException("Unable to read CienaVCGTrigger config file: " + confFileName);
        }

        this.confFile = f;

        reloadConfig();

        DateFileWatchdog.getInstance(f, 5 * 1000).addObserver(this);
    }

    private void reloadConfig() throws Exception {

        final IniEditor iniConfig = new IniEditor();
        iniConfig.load(confFile);

        //we're fine; go get a coffee
        String definedEmails = iniConfig.get(DEFAULT_SETTINGS_SECTION_NAME, "emailAddresses");
        if ((definedEmails == null) || (definedEmails.trim().length() == 0)) {
            definedEmails = "ramiro@cern.ch";
        }

        final List<String> sectionNames = iniConfig.sectionNames();
        for (final String sectionName : sectionNames) {
            if (sectionName.equals(DEFAULT_SETTINGS_SECTION_NAME)) {
                continue;
            }
            String sectionDefinedEmail = iniConfig.get(sectionName, "emailAddresses");
            if ((sectionDefinedEmail == null) || (sectionDefinedEmail.trim().length() == 0)) {
                sectionDefinedEmail = definedEmails;
            }

            final String[] emailAddresses = Utils.getSplittedListFields(sectionDefinedEmail);

            CienaVCGConfigEntry.Builder builder = new CienaVCGConfigEntry.Builder(sectionName, emailAddresses);

            builder.mlPingPredicate(getPredicate(iniConfig, sectionName, "MLPingPredicate"))
                    .mlPingThreshold(getDouble(iniConfig, sectionName, "MLPingThreshold", .2))
                    .provBWPredicate(getPredicate(iniConfig, sectionName, "ProvisionedBWPredicate"))
                    .operBWPredicate(getPredicate(iniConfig, sectionName, "OperationalBWPredicate"))
                    .bwThreshold(getDouble(iniConfig, sectionName, "BWThreshold", .2))
                    .traffInPredicate(getPredicate(iniConfig, sectionName, "trafficInPredicate"))
                    .traffOutPredicate(getPredicate(iniConfig, sectionName, "trafficOutPredicate"))
                    .traffThreshold(getDouble(iniConfig, sectionName, "trafficThreshold", .002));

        }
    }

    private static final double getDouble(final IniEditor iniConfig, final String sectionName, final String property,
            double defaultVal) {
        double retV = defaultVal;
        String doubleProp = null;
        try {
            doubleProp = iniConfig.get(sectionName, property);
            retV = Double.valueOf(doubleProp);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Exception parsing double val for sectionName: " + sectionName
                    + "; propertyName: " + property + "; propVal: " + doubleProp + ". Will return default val: "
                    + defaultVal, t);
            retV = defaultVal;
        }

        return retV;
    }

    private static final monPredicate getPredicate(final IniEditor iniConfig, final String sectionName,
            final String property) {
        final String monPredicateS = iniConfig.get(sectionName, property);
        return (monPredicateS == null) ? null : Formatare.toPred(monPredicateS);
    }

    /**
     * Data from services
     */
    @Override
    public Object filterData(Object data) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Config file was reloaded
     */
    @Override
    public void update(Observable o, Object arg) {
        try {
            reloadConfig();
        } catch (Throwable t) {

        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub

    }
}
