package lia.monitoring.lemon.conf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DateFileWatchdog;
import lia.util.ntp.NTPDate;

public class MLLemonConfProvider extends Observable implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(MLLemonConfProvider.class.getName());

    File confFile;
    File remapFile;

    DateFileWatchdog confDFW;
    DateFileWatchdog remapDFW;
    LemonConfProvider lcp;
    MLLemonConf conf;
    private final Object syncConf;
    String PersistentStoreFileName;

    public MLLemonConfProvider(File confFile) throws Exception {
        syncConf = new Object();

        this.confFile = confFile;
        try {
            confDFW = DateFileWatchdog.getInstance(confFile, 5 * 1000);
        } catch (Throwable t) {
            throw new Exception(t);
        }

        if (confDFW != null) {
            confDFW.addObserver(this);
        } else {
            throw new Exception("DateFileWatchdog for confFile is null!");
        }

        try {
            lcp = LemonConfProvider.getInstance(confFile);
            lcp.addObserver(this);
        } catch (Throwable t) {
            throw new Exception(t);
        }

        if (lcp != null) {
            lcp.addObserver(this);
        } else {
            throw new Exception("LemonConfProvider is null!");
        }

        loadConf();
        loadML2Mappings();
        notifyChangedConf();
    }

    public MLLemonConf getConf() {
        synchronized (syncConf) {
            return conf;
        }
    }

    private void writeConf2File() {
        long sTime = NTPDate.currentTimeMillis();
        synchronized (syncConf) {
            if ((conf != null) && (conf.getLemonConf() != null)) {
                ObjectOutputStream oos = null;
                try {
                    oos = new ObjectOutputStream(new FileOutputStream(PersistentStoreFileName));
                    oos.writeObject(conf.getLemonConf());
                    oos.flush();

                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINEST, "Wrote to File: \n\n" + conf.getLemonConf().toString() + "\n\n");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " MLLemonConfProvider :- got exc", t);
                }

                try {
                    oos.close();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " MLLemonConfProvider :- got exc", t);
                }
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Write conf took [ " + (NTPDate.currentTimeMillis() - sTime) + " ] ms");
        }
    }

    private void readConfFromFile() {
        long sTime = NTPDate.currentTimeMillis();
        synchronized (syncConf) {
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(new FileInputStream(PersistentStoreFileName));
                LemonConf lcf = (LemonConf) ois.readObject();
                if (conf == null) {
                    conf = new MLLemonConf(null, null, lcf);
                } else {
                    conf = new MLLemonConf(conf.getIDPrefML(), conf.getParamRemapML(), lcf);
                }

            } catch (Throwable t) {
                logger.log(Level.WARNING, "MLLemonConfProvider :- got exc", t);
                conf = null;
            }

            try {
                ois.close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "MLLemonConfProvider :- got exc", t);
            }
        }
        notifyChangedConf();
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "\n\nreadConfFromFile: EntireConf: " + ((conf == null) ? "null" : conf.toString())
                    + "\n\n");
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Read conf took [ " + (NTPDate.currentTimeMillis() - sTime) + " ] ms");
        }
    }

    public void loadConf() {
        Properties p = new Properties();
        try {
            FileInputStream fis = new FileInputStream(confFile);
            p.load(fis);
            fis.close();

            String prop = p.getProperty("PersistentConfFile", null);
            if (prop == null) {
                if (PersistentStoreFileName == null) {
                    logger.log(Level.WARNING, "Got null for PersistentConfFile ... using default");
                    PersistentStoreFileName = "/pool/monalisa/MonaLisa/Service/usr_code/Lemon/conf/PersistentMLLemonConf";
                } else {
                    logger.log(Level.WARNING, "Got null for PersistentConfFile ... using older one ["
                            + PersistentStoreFileName + "]");
                }
            } else {
                String newPersistentStoreFileName = prop.trim();
                if (newPersistentStoreFileName.equals(PersistentStoreFileName)) {
                    logger.log(Level.INFO, "SAME PersistentConfFile ... using default");
                } else {
                    logger.log(Level.INFO, "NEW PersistentConfFile ... using default");
                    PersistentStoreFileName = newPersistentStoreFileName;
                }
            }

            logger.log(Level.INFO, "\n\nMLLemonConfProvider: Using as PersistentStoreFileName "
                    + PersistentStoreFileName + "\n\n");

            String sRemapFile = p.getProperty("MLLemonReMappingFile", null);
            if (sRemapFile == null) {
                if (remapFile == null) {
                    logger.log(Level.WARNING,
                            "\n\nMLLemonConfProvider: MLLemonReMappingFile is NULL!!! I will not import any data into ML\n\n");
                } else {
                    logger.log(Level.INFO, "\n\nMLLemonConfProvider: MLLemonReMappingFile ... using the older one ["
                            + remapFile.getAbsolutePath() + "]\n\n");
                }
            } else {
                sRemapFile = sRemapFile.trim();
                File newRemapFile = new File(sRemapFile);
                if (remapFile != null) {
                    if (remapFile.compareTo(newRemapFile) != 0) {
                        if (remapDFW != null) {
                            remapDFW.deleteObserver(this);
                            remapDFW.stopIt();
                        }

                        remapFile = newRemapFile;
                        remapDFW = DateFileWatchdog.getInstance(remapFile, 5 * 1000);
                        remapDFW.addObserver(this);
                    } else {
                        logger.log(Level.INFO, "\n\nMLLemonConfProvider: Using SAME MLLemonReMappingFile \n\n");
                    }
                } else {
                    remapFile = newRemapFile;
                    remapDFW = DateFileWatchdog.getInstance(remapFile, 5 * 1000);
                    remapDFW.addObserver(this);
                }
                logger.log(Level.INFO, "\n\nMLLemonConfProvider: Using MLLemonReMappingFile " + sRemapFile + "["
                        + remapDFW + "]\n\n");
            }

        } catch (Throwable t) {
            logger.log(Level.WARNING, " MLLemonConfProvider :- got exc", t);
        }
    }

    public void loadML2Mappings() {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Start (re)loading ML2LemonMappings ...  using [" + confFile.getAbsolutePath()
                    + "] ");
        }

        boolean offsetStarted = false;
        boolean remapStarted = false;

        try {
            BufferedReader br = new BufferedReader(new FileReader(remapFile));
            HashMap newRemapMap = new HashMap();
            HashMap newOffsetMap = new HashMap();

            for (String line = br.readLine(); line != null; line = br.readLine()) {
                try {
                    line = line.trim();

                    if ((line.length() == 0)
                            || (line.startsWith("#") && !line.startsWith("#Offset") && !line.startsWith("#ParamRemap"))) {
                        continue; //ignore comment lines
                    }
                    if (line.startsWith("#Offset")) {
                        offsetStarted = true;
                        remapStarted = false;
                        continue;
                    }

                    if (line.startsWith("#ParamRemap")) {
                        offsetStarted = false;
                        remapStarted = true;
                        continue;
                    }

                    String[] tokens = line.split("(\\s)+");
                    if ((tokens != null) && (tokens.length >= 1) && (tokens[0] != null)) {
                        String value = "";
                        if (tokens.length > 1) {
                            value = tokens[1].trim();
                        }
                        if (offsetStarted && !remapStarted) {
                            newOffsetMap.put(tokens[0], value);
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER,
                                        "Added to Offset Map ( " + tokens[0] + ", " + newOffsetMap.get(tokens[0])
                                                + " ) ");
                            }
                        } else if (!offsetStarted && remapStarted) {
                            newRemapMap.put(tokens[0], value);
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER,
                                        "Added to Offset Map ( " + tokens[0] + ", " + newOffsetMap.get(tokens[0])
                                                + " ) ");
                            }
                        } else {
                            if (logger.isLoggable(Level.FINER)) {
                                logger.log(Level.FINER, "MLLemonConfProvider:loadML2Mappings: ignoring line " + line);
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " MLLemonConfProvider :- got exc", t);
                }
            }
            synchronized (syncConf) {
                conf = new MLLemonConf(newOffsetMap, newRemapMap, (lcp == null) ? null : lcp.getCurrentLemonConf());
            }

            br.close();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "MLLemonConfProvider :- got exc", t);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                    "Finished (re)loading ML2LemonMappings ...  "
                            + (((conf == null) || (conf.getIDPrefML() == null)) ? "null" : ""
                                    + conf.getIDPrefML().size()));
        }

    }

    private void notifyChangedConf() {
        setChanged();
        notifyObservers();
        try {
            if ((conf != null) && (conf.getIDPrefML() != null) && (conf.getLemonConf() != null)) {
                LemonConf lcf = conf.getLemonConf();
                if ((lcf != null) && (lcf.hostsToClusterMap != null) && (lcf.metricsIDToNameMap != null)
                        && (lcf.metricsNameToFieldsMap != null)) {
                    writeConf2File();
                } else {
                    readConfFromFile();
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "MLLemonConfProvider :- got exc", t);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        synchronized (syncConf) {
            if (o != null) {
                if ((confDFW != null) && o.equals(confDFW)) {
                    loadConf();
                } else if ((lcp != null) && o.equals(lcp)) {
                    if (conf != null) {
                        conf = new MLLemonConf(conf.getIDPrefML(), conf.getParamRemapML(), lcp.getCurrentLemonConf());
                    } else {
                        conf = new MLLemonConf(null, null, lcp.getCurrentLemonConf());
                    }
                } else if ((remapDFW != null) && o.equals(remapDFW)) {
                    loadML2Mappings();
                }
            }
        }
        notifyChangedConf();
    }
}
