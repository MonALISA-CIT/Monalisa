/*
 * Created on Mar 26, 2010
 */
package lia.net.topology.agents.conf;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lia.util.DateFileWatchdog;

/**
 * 
 * @author ramiro
 */
public abstract class RawConfig<T> implements Observer, RawConfigInterface<T> {

    protected final List<RawConfigNotifier<T>> notifiers = new CopyOnWriteArrayList<RawConfigNotifier<T>>();

    protected final File configFile;

    public RawConfig(File configFile) throws IOException {

        if (configFile == null) {
            throw new NullPointerException("Null config file");
        }

        if (!configFile.exists()) {
            throw new IOException("Config file: " + configFile + " does not exist");
        }

        if (!configFile.isFile()) {
            throw new IOException("Config file: " + configFile + " is not a file");
        }

        if (!configFile.canRead()) {
            throw new IOException("Config file: " + configFile + " cannot be read");
        }

        this.configFile = configFile;

        try {
            DateFileWatchdog.getInstance(this.configFile, 5 * 1000).addObserver(this);
        } catch (Throwable t) {
            throw new IOException("Unable to monitor conf file [ " + this.configFile + " ] for changes. Cause " + t.getCause());
        }
    }

    public RawConfig(String configFileName) throws IOException {
        this(new File(configFileName));
    }

    public void addNotifier(RawConfigNotifier<T> notifier) {
        notifiers.add(notifier);
    }

    public void removeNotifier(RawConfigNotifier<T> notifier) {
        notifiers.remove(notifier);
    }

    public void newConfig(RawConfigInterface<T> oldConfig, RawConfigInterface<T> newConfig) {
        for (RawConfigNotifier<T> notifier : notifiers) {
            notifier.notifyConfig(oldConfig, newConfig);
        }
    }

    public abstract void configFileChanged();

    public abstract String hostName();

    public abstract List<T> hostPorts();

    public abstract ConcurrentMap<T, OutgoingLink> outgoingLinks();

    /**
     * @param o
     * @param arg
     */
    public void update(Observable o, Object arg) {
        configFileChanged();
    }

    public File getConfigFile() {
        return configFile;
    }
}
