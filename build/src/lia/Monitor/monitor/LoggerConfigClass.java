package lia.Monitor.monitor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.LogManager;

import lia.util.logging.MLLoggerConfig;

/**
 * 
 * Helper Class for configuring 
 * 
 * @author ramiro
 */
public class LoggerConfigClass implements AppConfigChangeListener {

    public LoggerConfigClass() {
        try {
            reloadConf();
            AppConfig.addLoggerNotifier(this);
        } catch (Throwable t) {
            System.err.println("Unable to reload the config. Cause:");
            t.printStackTrace();
        }
    }

    private void reloadConf() {
        Properties props = new Properties();
        try {
            props.putAll(AppConfig.getPropertiesConfigApp());
        } catch (Throwable t) {
            t.printStackTrace();
            setDefaultProps(props);
        }
        if (props.size() == 0) {
            setDefaultProps(props);
        }
        try {
            String MonaLisa_HOME = System.getProperty("MonaLisa_HOME", null);
            //Just for ML
            if (MonaLisa_HOME != null) {
                boolean useDefaulLoggers = Boolean.valueOf(
                        props.getProperty("lia.Monitor.monitor.LoggerConfigClass.useDefaulLoggers", "false"))
                        .booleanValue();
                try {
                    if (!useDefaulLoggers) {
                        synchronized (props) {
                            String handlers = props.getProperty("handlers");
                            if ((handlers != null) && handlers.equals("java.util.logging.FileHandler")) {
                                props.setProperty("handlers", "lia.util.logging.MLFileHandler");
                                int len = "java.util.logging.FileHandler".length();
                                String prefix = "lia.util.logging.MLFileHandler";
                                HashMap hm = new HashMap();
                                for (Object element : props.entrySet()) {
                                    Map.Entry entry = (Map.Entry) element;
                                    String key = (String) entry.getKey();
                                    String val = (String) entry.getValue();
                                    if (key.indexOf("java.util.logging.FileHandler") != -1) {
                                        hm.put(prefix + key.substring(len), val);
                                    }
                                }
                                props.putAll(hm);
                            }
                        }
                    }
                } catch (Throwable t1) {
                    t1.printStackTrace();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            props.store(baos, "ML Loggging Properties");
        } catch (Throwable t) {
            System.err.println("Cannot store default props");
            t.printStackTrace();
        }
        try {
            baos.flush();
        } catch (Exception ex) {
            //kind of impossible
        }
        byte[] buff = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(buff);
        try {
            LogManager.getLogManager().readConfiguration(bais);
        } catch (Throwable t) {
            System.err.println("Cannot load logging properties into LogManager");
            t.printStackTrace();
        }

        MLLoggerConfig.getInstance().notifyLocalProps(props);
    }

    private void setDefaultProps(Properties props) {
        System.err.println("Setting default props for logging");
        props.setProperty("handlers", "java.util.logging.ConsoleHandler");
        props.setProperty("java.util.logging.ConsoleHandler.level", "FINEST");
        props.setProperty(".level", "INFO");
        props.setProperty("lia.level", "CONFIG");
        props.setProperty("java.util.logging.ConsoleHandler.formatter", "java.util.logging.SimpleFormatter");
    }

    @Override
    public void notifyAppConfigChanged() {
        reloadConf();
    }

}
